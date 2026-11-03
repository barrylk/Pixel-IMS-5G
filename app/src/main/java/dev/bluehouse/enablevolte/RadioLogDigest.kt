package dev.bluehouse.enablevolte

/**
 * Pure helpers for the Field Test radio log capture.
 *
 * Kept free of Android APIs so the redaction rules, the EN-DC event extractor and the
 * UE-capability log decoder are covered by JVM tests. The root service does the file
 * access; everything that decides what leaves the device lives here.
 */
object RadioLogDigest {
    /**
     * Subscriber identifiers in full radio logs: IMSI/IMEI (15 digits), ICCID (19-20),
     * MSISDNs. Ten or more contiguous digits, or a `+`-prefixed number. Deliberately not
     * the looser rule used for short snippets, which also erases logcat PID/TID columns.
     */
    private val LONG_NUMBER = Regex("(?<![\\w.])(?:\\+\\d[\\d -]{8,}\\d|\\d{10,})(?![\\w.])")
    private val SENSITIVE_FIELD = Regex(
        "(?i)\\b(m?iccid|m?imsi|imei|imeisv|meid|msisdn|phoneNumber|line1Number|subscriberId|" +
            "suci|supi|guti|tmsi)\\s*[=:]\\s*\\[?[^,\\s}\\]]+\\]?",
    )
    private val IPV4 = Regex("(?<![\\d.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\d.])")

    /** Four or more groups, or `::` compression; never `09:12:05` timestamps. */
    private val IPV6 = Regex(
        "(?i)(?<![0-9a-f:])(?:(?:[0-9a-f]{1,4}:){4,7}[0-9a-f]{1,4}|" +
            "(?:[0-9a-f]{1,4}:){1,6}:(?:[0-9a-f]{1,4}(?::[0-9a-f]{1,4}){0,5})?)(?![0-9a-f:])",
    )

    private val ENDC_EVENT = Regex("(?i)ENDCCapaInd|EndcCapability|endc[ _]?(?:capa|avail|disable|restrict)|scg[ _]?fail")

    fun redact(text: String): String =
        IPV6.replace(
            IPV4.replace(
                SENSITIVE_FIELD.replace(LONG_NUMBER.replace(text, "[redacted-id]")) { "${it.groupValues[1]}=[redacted]" },
                "[redacted-ip]",
            ),
            "[redacted-ip]",
        )

    /** Text if the first block has no NUL bytes and is mostly printable. */
    fun isProbablyText(head: ByteArray): Boolean {
        if (head.isEmpty()) return true
        if (head.any { it == 0.toByte() }) return false
        val printable = head.count { b ->
            val c = b.toInt() and 0xFF
            c == 9 || c == 10 || c == 13 || c in 32..126 || c >= 0x80
        }
        return printable * 100 / head.size >= 95
    }

    /** RIL lines that record why EN-DC capability changed, newest last. */
    fun endcEvents(text: String, limit: Int = 40): List<String> =
        text.lineSequence()
            .filter { ENDC_EVENT.containsMatchIn(it) }
            .map { it.trim().take(300) }
            .toList()
            .takeLast(limit)

    data class UecapSummary(
        val combos: Int,
        val endc: Int,
        val lteBands: List<Int>,
        val nrBands: List<Int>,
        val samples: List<String>,
    ) {
        fun describe(): String =
            if (combos == 0) {
                "no band combinations"
            } else {
                "$combos combinations, $endc EN-DC; LTE ${lteBands.joinToString(",") { "B$it" }}; " +
                    "NR ${nrBands.joinToString(",") { "n$it" }}; e.g. ${samples.joinToString("  ")}"
            }
    }

    /**
     * The modem's `uecap_log_file-*` records use the Pixel UE-capability schema: field 3
     * holds combo groups, each group's field 2 a combination, each combination's field 1 a
     * component whose field 1 is the band. NR bands are stored offset by 10000.
     */
    fun decodeUecapLog(bytes: ByteArray, sampleLimit: Int = 8): UecapSummary? = runCatching {
        val combos = mutableListOf<List<Int>>()
        for (group in lengthDelimited(bytes, 3)) {
            for (combo in lengthDelimited(group, 2)) {
                val bands = lengthDelimited(combo, 1).mapNotNull { firstVarint(it, 1)?.toInt() }
                if (bands.isNotEmpty()) combos += bands
            }
        }
        val endc = combos.filter { c -> c.any { it >= NR_OFFSET } && c.any { it < NR_OFFSET } }
        UecapSummary(
            combos = combos.size,
            endc = endc.size,
            lteBands = combos.flatten().filter { it < NR_OFFSET }.distinct().sorted(),
            nrBands = combos.flatten().filter { it >= NR_OFFSET }.map { it - NR_OFFSET }.distinct().sorted(),
            samples = endc.take(sampleLimit).map { c ->
                c.joinToString("+") { if (it >= NR_OFFSET) "n${it - NR_OFFSET}" else "B$it" }
            },
        )
    }.getOrNull()

    private const val NR_OFFSET = 10_000

    private class Reader(val b: ByteArray) {
        var i = 0

        fun varint(): Long {
            var v = 0L
            var shift = 0
            while (true) {
                require(i < b.size && shift < 64) { "truncated varint" }
                val x = b[i++].toInt() and 0xFF
                v = v or ((x and 0x7F).toLong() shl shift)
                if (x < 0x80) return v
                shift += 7
            }
        }
    }

    /** Walks one protobuf message, calling [visit] with field number, wire type and value. */
    private fun walk(bytes: ByteArray, visit: (field: Int, wire: Int, varint: Long, data: ByteArray?) -> Unit) {
        val r = Reader(bytes)
        while (r.i < bytes.size) {
            val tag = r.varint()
            val field = (tag ushr 3).toInt()
            val wire = (tag and 7).toInt()
            require(field > 0) { "zero field" }
            when (wire) {
                0 -> visit(field, wire, r.varint(), null)
                2 -> {
                    val n = r.varint().toInt()
                    require(n >= 0 && r.i + n <= bytes.size) { "truncated field" }
                    visit(field, wire, 0, bytes.copyOfRange(r.i, r.i + n))
                    r.i += n
                }
                1 -> r.i += 8
                5 -> r.i += 4
                else -> throw IllegalArgumentException("wire type $wire")
            }
            require(r.i <= bytes.size) { "truncated" }
        }
    }

    private fun lengthDelimited(bytes: ByteArray, field: Int): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        walk(bytes) { f, w, _, data -> if (f == field && w == 2 && data != null) out += data }
        return out
    }

    private fun firstVarint(bytes: ByteArray, field: Int): Long? {
        var found: Long? = null
        walk(bytes) { f, w, v, _ -> if (found == null && f == field && w == 0) found = v }
        return found
    }
}

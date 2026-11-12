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

    /**
     * Real EN-DC capability signals only. The 2.2.8 pattern also matched `endc[ _]?avail`,
     * which hits every registration dump that merely prints `isEnDcAvailable = …`.
     */
    private val ENDC_EVENT = Regex("ENDCCapaInd|OnEndcCapabilityReceived|EndcCapability(?:Ind|Changed)|(?i:scg[ _-]?fail)")
    private val LOG_TIME = Regex("^(\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3})")
    private val NR_FLAGS = Regex("isNrAvailable = (\\w+).*?isEnDcAvailable = (\\w+)")
    private val SLOT_TAG = Regex("\\b(?:SST|NRM-C|DIC)-(\\d)\\b")
    private val CHANNEL = Regex("mChannelNumber=(\\d+)|earfcn: (\\d+)|mEarfcn=(\\d+)")
    private val NTC_EVENT = Regex("NetworkTypeController: \\[(\\d)] (Transition .*|Entering \\w+State)")
    private val DISPLAY_CHANGE = Regex("DIC-(\\d)\\s*: TelephonyDisplayInfo changed from .* to TelephonyDisplayInfo \\{network=(\\w+), overrideNetwork=(\\w+)")

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

    /**
     * A 5G timeline from the radio log buffer, newest last: changes in NR-advertised and
     * EN-DC-available per SIM slot (with the serving channel), NetworkTypeController
     * transitions, status-bar 5G changes, and any explicit EN-DC capability or SCG failure
     * lines. Repeated states are dropped, so the result reads as what changed and when.
     */
    fun nrTimeline(text: String, limit: Int = 60): List<String> {
        val out = mutableListOf<String?>()
        val lastFlags = mutableMapOf<String, String>()
        val lastChannel = mutableMapOf<String, String>()
        // The newest NR-flag entry per slot, so a burst of reports can be collapsed into it.
        data class Pending(val index: Int, val timeMs: Long, val stateBefore: String?)
        val pending = mutableMapOf<String, Pending>()
        for (line in text.lineSequence()) {
            val time = LOG_TIME.find(line)?.groupValues?.get(1) ?: continue
            if (ENDC_EVENT.containsMatchIn(line)) {
                out += line.trim().take(240)
                continue
            }
            NTC_EVENT.find(line)?.let { m ->
                out += "$time  SIM slot ${m.groupValues[1]}: ${m.groupValues[2].trim().take(120)}"
            }
            DISPLAY_CHANGE.find(line)?.let { m ->
                out += "$time  SIM slot ${m.groupValues[1]}: status bar ${m.groupValues[2]} / ${m.groupValues[3]}"
            }
            val flags = NR_FLAGS.find(line)
            // Untagged sources repeat a slot's state without saying which slot; skip them.
            val slot = SLOT_TAG.find(line)?.groupValues?.get(1)
            if (flags == null || slot == null || !line.contains("domain=PS")) continue
            CHANNEL.find(line)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() && it != INVALID_CHANNEL }
                ?.let { lastChannel[slot] = it }
            val state = "NR advertised=${flags.groupValues[1]}, EN-DC available=${flags.groupValues[2]}"
            if (lastFlags[slot] == state) continue
            val entry = "$time  SIM slot $slot: $state${lastChannel[slot]?.let { " (channel $it)" }.orEmpty()}"
            val now = timeMs(time)
            val burst = pending[slot]?.takeIf { now - it.timeMs in 0..BURST_MS }
            if (burst != null) {
                // Several layers report one change within moments: keep only where it settled.
                if (state == burst.stateBefore) {
                    out[burst.index] = null
                    pending.remove(slot)
                } else {
                    out[burst.index] = entry
                }
            } else {
                pending[slot] = Pending(out.size, now, lastFlags[slot])
                out += entry
            }
            lastFlags[slot] = state
        }
        return out.filterNotNull().takeLast(limit)
    }

    /** Milliseconds from a logcat `MM-dd HH:mm:ss.SSS` stamp, for ordering within a capture. */
    private fun timeMs(stamp: String): Long {
        val (date, clock) = stamp.split(' ')
        val (month, day) = date.split('-').map { it.toLong() }
        val (h, m, rest) = clock.split(':')
        val seconds = rest.toDouble()
        return ((((month * 31 + day) * 24 + h.toLong()) * 60 + m.toLong()) * 60_000) + (seconds * 1000).toLong()
    }

    private const val BURST_MS = 1_500L
    private const val INVALID_CHANNEL = "65535"

    /** Where a capability log was written relative to the last boot. */
    fun bootRelation(writtenAtMs: Long, bootAtMs: Long): String = when {
        bootAtMs <= 0 -> "boot time unknown"
        writtenAtMs < bootAtMs - BOOT_WINDOW_MS -> "before last boot"
        writtenAtMs <= bootAtMs + BOOT_WINDOW_MS -> "at last boot"
        else -> "since last boot"
    }

    private const val BOOT_WINDOW_MS = 15 * 60 * 1000L

    /** Shannon modem diagnostic (DM) logging, as the vendor properties report it. */
    enum class ModemLoggingState { UNSUPPORTED, OFF, STARTING, RUNNING, STOPPING }

    /**
     * Reads the state from the requested switch ([persist], `persist.vendor.sys.modem.logging.enable`),
     * the live switch ([live], `vendor.sys.modem.logging.enable`) and the logger's own [status]
     * (`vendor.sys.modem.logging.status`). Only "running" when the logger itself says so; a
     * request the vendor init has not acted on stays "starting", never assumed to work.
     */
    fun modemLoggingState(persist: String, live: String, status: String, location: String): ModemLoggingState {
        fun on(v: String) = v.trim().equals("true", ignoreCase = true) || v.trim() == "1"
        if (location.isBlank() && persist.isBlank() && live.isBlank()) return ModemLoggingState.UNSUPPORTED
        val running = on(status)
        return when {
            on(persist) && running -> ModemLoggingState.RUNNING
            on(persist) -> ModemLoggingState.STARTING
            running || on(live) -> ModemLoggingState.STOPPING
            else -> ModemLoggingState.OFF
        }
    }

    data class UecapSummary(
        val combos: Int,
        val endc: Int,
        val lteBands: List<Int>,
        val nrBands: List<Int>,
        val samples: List<String>,
    ) {
        fun describe(): String =
            if (combos == 0) {
                "feature/bandwidth table only, no band combinations"
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

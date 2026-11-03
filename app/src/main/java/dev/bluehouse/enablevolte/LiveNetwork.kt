package dev.bluehouse.enablevolte

/** A current physical carrier, retaining the logical phone/slot it came from. */
data class LivePhysicalChannel(
    val phoneId: Int, val rat: String, val status: Int, val band: Int?, val pci: Int?,
    val channel: Int?, val bandwidthKhz: Int?, val uplinkChannel: Int?, val uplinkBandwidthKhz: Int?,
    val downlinkFrequencyKhz: Int?, val uplinkFrequencyKhz: Int?,
)

/** Deliberately excludes the registry history and all subscriber identifiers. */
internal fun livePhysicalRegistry(raw: String): String = raw.lineSequence().filter {
    it.trim().startsWith("Phone Id=") || it.trim().startsWith("mPhysicalChannelConfigs=")
}.joinToString("\n")

fun parseLivePhysicalChannels(raw: String, phoneId: Int): List<LivePhysicalChannel> {
    var currentPhone = -1
    return buildList {
        raw.lineSequence().forEach { line ->
            val text = line.trim()
            if (text.startsWith("Phone Id=")) currentPhone = text.substringAfter('=').trim().toIntOrNull() ?: -1
            if (currentPhone != phoneId || !text.startsWith("mPhysicalChannelConfigs=")) return@forEach
            Regex("\\{mConnectionStatus=[^}]+\\}").findAll(text).forEach { match ->
                fun field(name: String): String = Regex("\\b$name=([^,}]+)").find(match.value)?.groupValues?.get(1)?.trim().orEmpty()
                fun number(name: String): Int? = field(name).toIntOrNull()?.takeIf { it >= 0 && it != Int.MAX_VALUE }
                val rat = when (field("mNetworkType").uppercase()) { "LTE", "13", "LTE_CA", "19" -> "LTE"; "NR", "20" -> "NR"; else -> return@forEach }
                val status = when (field("mConnectionStatus")) {
                    "PrimaryServing", "PRIMARY_SERVING", "1" -> 1
                    "SecondaryServing", "SECONDARY_SERVING", "2" -> 2
                    else -> 0
                }
                if (status == 0) return@forEach
                add(LivePhysicalChannel(phoneId, rat, status, number("mBand")?.takeIf { it > 0 },
                    number("mPhysicalCellId"), number("mDownlinkChannelNumber"), number("mCellBandwidthDownlinkKhz")?.takeIf { it > 0 },
                    number("mUplinkChannelNumber")?.takeIf { it > 0 }, number("mCellBandwidthUplinkKhz")?.takeIf { it > 0 },
                    number("mDownlinkFrequency")?.takeIf { it > 0 }, number("mUplinkFrequency")?.takeIf { it > 0 }))
            }
        }
    }.distinctBy { listOf(it.rat, it.channel, it.pci, it.band, it.status) }
}

fun liveCarrierName(name: String, numeric: String, fallback: String): String {
    val clean = name.trim()
    if (clean.isNotBlank() && clean.any(Char::isLetter) && !clean.contains('=') && !clean.contains('{')) return clean
    return when (numeric.filter(Char::isDigit)) {
        "41301" -> "SLT-MOBITEL"
        "41302" -> "Dialog"
        "41305" -> "Airtel Lanka"
        "41308" -> "Hutch"
        else -> fallback.takeIf { it.isNotBlank() && !it.contains('{') } ?: "Mobile network"
    }
}

fun radioReading(value: Int?, unit: String = ""): String =
    if (value == null || value == Int.MAX_VALUE || value == Int.MIN_VALUE) "Not reported"
    else (value.toString().replace('-', '−') + if (unit.isBlank()) "" else " $unit")

data class LiveCarrier(
    val role: String, val rat: String, val band: String,
    val cell: SubscriptionModer.CellSnapshot?, val physical: LivePhysicalChannel?,
) {
    val channel: Int? get() = physical?.channel ?: cell?.channel?.takeIf { it >= 0 && it != Int.MAX_VALUE }
    val pci: Int? get() = physical?.pci ?: cell?.pci?.takeIf { it >= 0 && it != Int.MAX_VALUE }
    val bandwidthKhz: Int? get() = physical?.bandwidthKhz ?: cell?.bandwidthKhz
    val key: String get() = "$rat:$channel:$pci:$band"
}

data class LiveConnection(
    val title: String, val explanation: String, val fiveGStatus: String,
    val nrConnected: Boolean, val caActive: Boolean, val carriers: List<LiveCarrier>,
    val neighbours: List<SubscriptionModer.CellSnapshot>, val carrierNote: String,
) {
    val caBandwidth: LiveCaBandwidth get() {
        val lte = carriers.filter { it.rat == "LTE" && (it.physical != null || it.cell != null) }
        return LiveCaBandwidth(lte.map { it.bandwidthKhz?.takeIf { width -> width > 0 && width != Int.MAX_VALUE } },
            if (lte.any { it.physical != null }) "Physical channel report" else "Serving-cell report")
    }
    val bandCombination: String get() = carriers.filter { it.rat == "LTE" && it.band != "Not reported" }.joinToString(" + ") { it.band }
    val serving: SubscriptionModer.CellSnapshot? get() = carriers.firstOrNull { it.rat == "NR" && it.cell != null }?.cell
        ?: carriers.firstOrNull { it.rat == "LTE" && it.cell != null }?.cell
    val anchor: SubscriptionModer.CellSnapshot? get() = carriers.firstOrNull { it.role == "4G anchor" }?.cell
}

/** Identified components in this sample only; ServiceState's retained summary is separate. */
data class LiveCaBandwidth(val componentWidthsKhz: List<Int?>, val source: String) {
    val componentCount: Int get() = componentWidthsKhz.size
    val totalKhz: Int? get() = if (componentWidthsKhz.isNotEmpty() && componentWidthsKhz.all { it != null })
        componentWidthsKhz.filterNotNull().sum() else null
}

fun resolveLiveConnection(
    cells: List<SubscriptionModer.CellSnapshot>, plmn: String, dataRat: String, nrState: Int,
    usingCa: Boolean, nrAvailable: Boolean?, endcAvailable: Boolean?,
    physical: List<LivePhysicalChannel> = emptyList(),
    cellsScopedToSubscription: Boolean = false,
): LiveConnection {
    val digits = plmn.filter(Char::isDigit)
    // Never use a different operator's registered cell as selected-SIM evidence.
    val hasOwnPrimary = cells.any { it.operator.filter(Char::isDigit) == digits && (it.registered || it.connectionStatus == 1) }
    val own = cells.filter {
        digits.isNotBlank() && (it.operator.filter(Char::isDigit) == digits ||
            (cellsScopedToSubscription && hasOwnPrimary && it.operator.filter(Char::isDigit).isBlank() && it.connectionStatus == 2))
    }
    val registered = own.filter { it.registered || it.connectionStatus in 1..2 }
    val nr = dataRat == "NR" || (dataRat in setOf("LTE", "LTE_CA") && nrState == 3) ||
        (dataRat !in setOf("LTE", "LTE_CA", "NR") &&
            (registered.any { it.type == "NR" } || physical.any { it.rat == "NR" }))
    val activePhysical = physical.filter { it.status in 1..2 && (it.rat != "NR" || nr) && (it.rat != "LTE" || dataRat != "NR") }
    val nsa = nr && dataRat != "NR"
    val result = mutableListOf<LiveCarrier>()
    fun role(rat: String, primary: Boolean): String = when {
        rat == "NR" -> "5G carrier"
        primary && nsa -> "4G anchor"
        primary -> "Main 4G carrier"
        else -> "Extra 4G carrier"
    }
    activePhysical.sortedWith(compareBy<LivePhysicalChannel> { it.rat == "NR" }.thenBy { it.status }).forEach { p ->
        // Secondary CellInfo frequently omits MCC/MNC. A current, slot-scoped
        // physical channel plus an exact channel/PCI match can identify it.
        // Never attach another operator's metrics or an ambiguous match.
        val candidates = cells.filter { c ->
            val operator = c.operator.filter(Char::isDigit)
            c.type == p.rat && (operator == digits || operator.isBlank()) &&
                p.channel != null && p.pci != null && c.channel == p.channel && c.pci == p.pci
        }
        val c = candidates.firstOrNull { it.operator.filter(Char::isDigit) == digits }
            ?: candidates.singleOrNull()
        val band = p.band?.let { if (p.rat == "NR") "n$it" else "B$it" } ?: c?.band ?: "Not reported"
        result += LiveCarrier(role(p.rat, p.status == 1), p.rat, band, c, p)
    }
    registered.sortedWith(compareBy<SubscriptionModer.CellSnapshot> { it.type == "NR" }.thenBy { it.connectionStatus != 1 }).forEach { c ->
        if (c.type == "NR" && !nr) return@forEach
        if (c.type == "LTE" && dataRat == "NR") return@forEach
        // A physical report is the current component list for this RAT. Do not
        // append older secondary CellInfo entries when that list shrinks.
        if (activePhysical.any { it.rat == c.type }) return@forEach
        if (result.none { it.rat == c.type && it.channel == c.channel && it.pci == c.pci }) {
            val primary = c.connectionStatus == 1 || (c.connectionStatus != 2 && result.none { it.rat == c.type })
            result += LiveCarrier(role(c.type, primary), c.type, c.band.takeIf { it != "—" && it.isNotBlank() } ?: "Not reported", c, null)
        }
    }
    if (nr && result.none { it.rat == "NR" }) result += LiveCarrier("5G carrier", "NR", "Not reported", null, null)
    if (nsa && result.none { it.rat == "LTE" }) result.add(0, LiveCarrier("4G anchor", "LTE", "Not reported", null, null))
    val lteCount = result.count { it.rat == "LTE" }
    val ca = dataRat != "NR" && (usingCa || dataRat == "LTE_CA" || lteCount > 1)
    val title = when {
        nr && nsa -> "5G NSA connected"
        nr -> "5G SA connected"
        ca && lteCount > 1 -> "4G+ connected"
        ca -> "4G+ reported"
        dataRat in setOf("LTE", "LTE_CA") -> "4G connected"
        dataRat == "Unknown" -> "Waiting for network"
        else -> "$dataRat connection"
    }
    val connectionExplanation = when {
        nsa -> "5G works alongside a 4G anchor. Both radio links support your connection."
        nr -> "Connected to standalone 5G. A 4G anchor is not required."
        ca && lteCount > 1 -> "Multiple 4G carriers work together for more capacity."
        ca -> "Android reports carrier aggregation; current component details are incomplete."
        dataRat == "LTE" -> "Your mobile connection is using 4G LTE."
        else -> "Waiting for the phone to report its current radio connection."
    }
    val explanation = connectionExplanation + when {
        nr -> ""
        nrState == 1 -> " Android reports that 5G is restricted on this connection."
        nrAvailable == true && endcAvailable == false ->
            " The network advertises 5G, but Android is not reporting an EN-DC link for 5G alongside LTE. Your current data connection remains 4G."
        else -> ""
    }
    val note = when {
        usingCa && lteCount < 2 -> "Carrier aggregation is active, but Android is not reporting the extra carrier details."
        activePhysical.isNotEmpty() -> "Carrier roles include current physical channels for this SIM slot."
        result.isNotEmpty() -> "Carrier roles come from this SIM's serving-cell reports. Hidden extra carriers cannot be listed."
        else -> "Android has not reported a serving cell for this SIM. Nearby cells are shown separately."
    }
    return LiveConnection(title, explanation, when {
        nr -> "5G connected"
        nrState == 1 -> "5G restricted by the network"
        nrAvailable == true && endcAvailable == false -> "5G advertised · LTE only"
        nrAvailable == true || endcAvailable == true -> "5G available · not connected"
        else -> "5G connection not reported"
    }, nr, ca, result, cells.filter { c -> result.none { it.cell === c } }, note)
}

package dev.bluehouse.enablevolte

import java.util.Locale
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Derived radio values for the Network page. Each is computed only from values the modem
 * did report, and is always labelled as calculated where it is shown.
 */
object RadioMath {
    /** One LTE timing-advance step: 16 Ts, about 78 m of one-way distance. */
    private const val LTE_TA_METRES = 78.12

    /** NR timing advance in microseconds covers the round trip: 150 m per microsecond. */
    private const val NR_TA_METRES_PER_US = 150.0

    /** Resource blocks per standard LTE channel width. */
    private fun lteResourceBlocks(bandwidthKhz: Int): Int? =
        when (bandwidthKhz) {
            1400 -> 6
            3000 -> 15
            5000 -> 25
            10000 -> 50
            15000 -> 75
            20000 -> 100
            else -> null
        }

    /**
     * LTE RSSI from RSRQ = N × RSRP / RSSI, where N is the number of resource blocks:
     * RSSI = RSRP + 10·log10(N) − RSRQ. Used only when the modem omits RSSI itself.
     */
    fun estimateLteRssi(
        rsrp: Int?,
        rsrq: Int?,
        bandwidthKhz: Int?,
    ): Int? {
        if (rsrp == null || rsrq == null || bandwidthKhz == null) return null
        if (rsrp !in -156..-31 || rsrq !in -43..20) return null
        val blocks = lteResourceBlocks(bandwidthKhz) ?: return null
        return (rsrp + 10 * log10(blocks.toDouble()) - rsrq).roundToInt()
    }

    /**
     * Approximate distance to the serving cell from timing advance. Zero or out-of-range
     * values mean "not measured" (idle, or a neighbour cell) and give null.
     */
    fun distanceMetres(
        type: String,
        timingAdvance: Int?,
    ): Int? {
        val ta = timingAdvance ?: return null
        return when (type) {
            "LTE" -> ta.takeIf { it in 1..1282 }?.let { (it * LTE_TA_METRES).roundToInt() }
            "NR" -> ta.takeIf { it in 1..2000 }?.let { (it * NR_TA_METRES_PER_US).roundToInt() }
            else -> null
        }
    }

    fun formatDistance(metres: Int): String =
        if (metres < 1000) {
            "$metres m"
        } else {
            String.format(Locale.US, "%.1f km", metres / 1000.0)
        }
}

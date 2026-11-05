package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioMathTest {
    @Test
    fun rssiIsCalculatedFromRsrpRsrqAndBandwidth() {
        // Pixel 10 Pro XL on B3 20 MHz: RSRP −110, RSRQ −17 → about −73 dBm (NetMonster showed −71).
        assertEquals(-73, RadioMath.estimateLteRssi(-110, -17, 20000))
        assertEquals(-80, RadioMath.estimateLteRssi(-104, -10, 5000))
    }

    @Test
    fun rssiIsNotInventedFromMissingOrOddValues() {
        assertNull(RadioMath.estimateLteRssi(null, -17, 20000))
        assertNull(RadioMath.estimateLteRssi(-110, null, 20000))
        assertNull(RadioMath.estimateLteRssi(-110, -17, null))
        assertNull(RadioMath.estimateLteRssi(-110, -17, 12345))
        assertNull(RadioMath.estimateLteRssi(Int.MAX_VALUE, -17, 20000))
    }

    @Test
    fun distanceFollowsTimingAdvance() {
        // 34 LTE steps ≈ 2.66 km (NetMonster: 1.6 mi).
        assertEquals(2656, RadioMath.distanceMetres("LTE", 34))
        assertEquals("2.7 km", RadioMath.formatDistance(2656))
        assertEquals(1500, RadioMath.distanceMetres("NR", 10))
        assertEquals("78 m", RadioMath.formatDistance(RadioMath.distanceMetres("LTE", 1)!!))
    }

    @Test
    fun unmeasuredTimingAdvanceGivesNoDistance() {
        assertNull(RadioMath.distanceMetres("LTE", 0))
        assertNull(RadioMath.distanceMetres("LTE", null))
        assertNull(RadioMath.distanceMetres("LTE", 5000))
        assertNull(RadioMath.distanceMetres("GSM", 3))
    }
}

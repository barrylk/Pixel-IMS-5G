package dev.bluehouse.enablevolte

import android.telephony.CarrierConfigManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigControlsTest {
    @Test
    fun `every control reads back a key it writes`() {
        // The read-back is meaningless if it watches a key the write never touches.
        ConfigControls.ALL.forEach { control ->
            assertTrue("${control.id} reads a key it does not write", control.readKey in control.onValues)
        }
    }

    @Test
    fun `control ids are unique`() {
        val ids = ConfigControls.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `turning a control off writes the negation of every key`() {
        val on = ConfigControls.CROSS_SIM.valuesFor(true)
        val off = ConfigControls.CROSS_SIM.valuesFor(false)

        assertEquals(on.keys, off.keys)
        on.forEach { (key, value) -> assertEquals(!value, off.getValue(key)) }
    }

    @Test
    fun `vonr carries its visibility key`() {
        val values = ConfigControls.VONR.valuesFor(true)

        assertEquals(true, values[CarrierConfigManager.KEY_VONR_ENABLED_BOOL])
        assertEquals(true, values[CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL])
    }

    @Test
    fun `four g plus hides the setting only when it is off`() {
        // The hide key runs opposite to the other two, which is the pairing the
        // old page expressed as a hand-written if/else per branch.
        val on = ConfigControls.FOUR_G_PLUS.valuesFor(true)
        val off = ConfigControls.FOUR_G_PLUS.valuesFor(false)

        assertEquals(true, on[CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL])
        assertEquals(true, on[CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL])
        assertEquals(false, on[CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL])
        assertEquals(true, off[CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL])
    }

    @Test
    fun `raw value is read against the position the key takes when on`() {
        assertTrue(ConfigControls.VOLTE.isOn(true))
        assertFalse(ConfigControls.VOLTE.isOn(false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a control cannot read back a key it never writes`() {
        BooleanControl(
            id = "broken",
            readKey = CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL,
            onValues = mapOf(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL to true),
        )
    }
}

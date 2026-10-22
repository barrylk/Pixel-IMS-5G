package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionalOverlayLabelsTest {
    @Test
    fun preservesLiveLabelsOnEveryOverlayAncestorAndDatabase() {
        val copied = mutableListOf<Pair<String, String>>()
        applyRegionalOverlayLabels("/data/adb/modules/example.new") { source, target ->
            copied += source to target
            true
        }
        assertEquals(
            listOf(
                "/system" to "/data/adb/modules/example.new/system",
                "/vendor" to "/data/adb/modules/example.new/system/vendor",
                "/vendor/firmware" to "/data/adb/modules/example.new/system/vendor/firmware",
                "/vendor/firmware/carrierconfig" to "/data/adb/modules/example.new/system/vendor/firmware/carrierconfig",
                "/vendor/firmware/carrierconfig/cfg.db" to "/data/adb/modules/example.new/system/vendor/firmware/carrierconfig/cfg.db",
            ),
            copied,
        )
    }

    @Test(expected = IllegalStateException::class)
    fun refusesToStageAnOverlayWithAnUnlabelledAncestor() {
        applyRegionalOverlayLabels("/data/adb/modules/example.new") { source, _ ->
            source != "/vendor/firmware"
        }
    }
}

package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalModemPatchStatusTest {
    @Test
    fun parsesRootServiceStatus() {
        val status = RegionalModemPatchStatus.fromJson(
            """
            {
              "supported": true,
              "magiskAvailable": true,
              "sourceAvailable": true,
              "installed": true,
              "active": true,
              "mountingReady": true,
              "removalPending": false,
              "rebootRequired": true,
              "device": "cheetah",
              "sourceSha256": "stock",
              "patchedSha256": "patched",
              "message": "Reboot required"
            }
            """.trimIndent(),
        )

        assertTrue(status.supported)
        assertTrue(status.installed)
        assertTrue(status.active)
        assertTrue(status.mountingReady)
        assertTrue(status.rebootRequired)
        assertEquals("cheetah", status.device)
        assertEquals("patched", status.patchedSha256)
    }
}

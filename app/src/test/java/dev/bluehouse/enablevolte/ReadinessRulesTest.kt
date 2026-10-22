package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessRulesTest {
    private fun patch(
        supported: Boolean = true,
        magisk: Boolean = true,
        source: Boolean = true,
        installed: Boolean = false,
        active: Boolean = false,
        mountingReady: Boolean = true,
        removalPending: Boolean = false,
        rebootRequired: Boolean = false,
    ) = RegionalModemPatchStatus(
        supported = supported,
        magiskAvailable = magisk,
        sourceAvailable = source,
        installed = installed,
        active = active,
        mountingReady = mountingReady,
        removalPending = removalPending,
        rebootRequired = rebootRequired,
        device = "cheetah",
        sourceSha256 = "",
        patchedSha256 = "",
        message = "",
    )

    @Test
    fun rootModeDoesNotFallBackToShizuku() {
        assertFalse(ReadinessRules.hasAccess(rootMode = true, rootReady = false, shizukuGranted = true))
        assertTrue(ReadinessRules.hasAccess(rootMode = true, rootReady = true, shizukuGranted = false))
        assertTrue(ReadinessRules.hasAccess(rootMode = false, rootReady = false, shizukuGranted = true))
        assertFalse(ReadinessRules.hasAccess(rootMode = false, rootReady = true, shizukuGranted = false))
    }

    @Test
    fun patchStatusTreatsPendingStatesAsUnfinished() {
        assertEquals(CheckStatus.UNKNOWN, ReadinessRules.patchStatus(null))
        assertEquals(CheckStatus.WARN, ReadinessRules.patchStatus(patch(supported = false)))
        assertEquals(CheckStatus.WARN, ReadinessRules.patchStatus(patch(installed = true, rebootRequired = true)))
        assertEquals(CheckStatus.WARN, ReadinessRules.patchStatus(patch(installed = true, removalPending = true)))
        assertEquals(CheckStatus.WARN, ReadinessRules.patchStatus(patch(installed = true, active = false)))
        assertEquals(CheckStatus.PASS, ReadinessRules.patchStatus(patch(installed = true, active = true)))
        assertEquals(
            CheckStatus.WARN,
            ReadinessRules.patchStatus(patch(installed = true, active = false, mountingReady = false)),
        )
        assertEquals(CheckStatus.FAIL, ReadinessRules.patchStatus(patch()))
    }

    @Test
    fun bootMountFailureBlocksReadinessBeforeDiagnosingTheRadio() {
        assertEquals(
            CheckStatus.FAIL,
            ReadinessRules.patchStatus(patch(installed = true).copy(bootMountStatus = "read-only bind remount failed")),
        )
    }

    @Test
    fun installNeedsEveryPrerequisite() {
        assertTrue(ReadinessRules.canInstallPatch(patch()))
        assertFalse(ReadinessRules.canInstallPatch(patch(source = false)))
        assertFalse(ReadinessRules.canInstallPatch(patch(magisk = false)))
        assertFalse(ReadinessRules.canInstallPatch(patch(supported = false)))
        assertFalse(ReadinessRules.canInstallPatch(patch(installed = true)))
        assertFalse(ReadinessRules.canInstallPatch(patch(removalPending = true)))
    }

    @Test
    fun revalidateOnlyBeforeTheOverlayIsLive() {
        assertTrue(ReadinessRules.canRevalidatePatch(patch(installed = true, rebootRequired = true)))
        assertFalse(ReadinessRules.canRevalidatePatch(patch(installed = true)))
        assertFalse(ReadinessRules.canRevalidatePatch(patch(installed = true, rebootRequired = true, removalPending = true)))
        assertFalse(ReadinessRules.canRevalidatePatch(patch(installed = true, rebootRequired = true, source = false)))
    }

    @Test
    fun inactiveKernelSuOverlayCanMigrateWithoutAnotherRebootMarker() {
        assertTrue(ReadinessRules.canRevalidatePatch(patch(installed = true).copy(rootManager = "KernelSU")))
        assertFalse(
            ReadinessRules.canRevalidatePatch(patch(installed = true, active = true).copy(rootManager = "KernelSU")),
        )
    }

    @Test
    fun removeOnlyWhenInstalledAndNotAlreadyPending() {
        assertTrue(ReadinessRules.canRemovePatch(patch(installed = true)))
        assertFalse(ReadinessRules.canRemovePatch(patch(installed = true, removalPending = true)))
        assertFalse(ReadinessRules.canRemovePatch(patch()))
    }

    @Test
    fun nsaCountsAsConnected() {
        assertTrue(ReadinessRules.is5gConnected("LTE", ReadinessRules.NR_STATE_CONNECTED, registeredNrCell = false))
        assertTrue(ReadinessRules.is5gConnected("NR", 0, registeredNrCell = false))
        assertTrue(ReadinessRules.is5gConnected("LTE", 0, registeredNrCell = true))
        assertFalse(ReadinessRules.is5gConnected("LTE", 2, registeredNrCell = false))
    }
}

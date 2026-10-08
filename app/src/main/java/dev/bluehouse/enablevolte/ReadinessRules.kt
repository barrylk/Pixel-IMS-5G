package dev.bluehouse.enablevolte

/**
 * The pure decisions behind the Setup checks, kept apart from the binder calls
 * that feed them so they can be unit tested on the JVM.
 */
object ReadinessRules {
    /**
     * Whether the chosen privilege path is actually usable.
     *
     * Root mode routes every binder call through the root service, so a
     * Shizuku grant cannot stand in for a root service that is not connected.
     */
    fun hasAccess(rootMode: Boolean, rootReady: Boolean, shizukuGranted: Boolean): Boolean =
        if (rootMode) rootReady else shizukuGranted

    fun patchStatus(patch: RegionalModemPatchStatus?): CheckStatus = when {
        patch == null -> CheckStatus.UNKNOWN
        !patch.supported -> CheckStatus.WARN
        // Installed but not yet loaded by the modem, or on its way out: neither is done.
        patch.removalPending || patch.rebootRequired -> CheckStatus.WARN
        patch.installed -> CheckStatus.PASS
        else -> CheckStatus.FAIL
    }

    fun canInstallPatch(patch: RegionalModemPatchStatus): Boolean =
        patch.supported && !patch.installed && patch.magiskAvailable && patch.sourceAvailable && !patch.removalPending

    /**
     * Re-staging is only safe before the reboot that mounts the overlay: once it
     * is live, the "stock" database the installer reads is the patched copy.
     */
    fun canRevalidatePatch(patch: RegionalModemPatchStatus): Boolean =
        patch.supported &&
            patch.installed &&
            patch.rebootRequired &&
            !patch.removalPending &&
            patch.magiskAvailable &&
            patch.sourceAvailable

    fun canRemovePatch(patch: RegionalModemPatchStatus): Boolean = patch.installed && !patch.removalPending

    /**
     * On NSA the NR leg is a secondary cell and is often not reported as a
     * registered cell, so the data RAT and NR state count as evidence too.
     */
    fun is5gConnected(dataRat: String, nrState: Int, registeredNrCell: Boolean): Boolean =
        registeredNrCell || dataRat == "NR" || (dataRat == "LTE" && nrState == NR_STATE_CONNECTED)

    /** `NetworkRegistrationInfo.NR_STATE_CONNECTED`. */
    const val NR_STATE_CONNECTED = 3
}

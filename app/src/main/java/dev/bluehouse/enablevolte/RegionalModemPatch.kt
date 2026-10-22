package dev.bluehouse.enablevolte

import org.json.JSONObject

data class RegionalModemPatchStatus(
    val supported: Boolean,
    val magiskAvailable: Boolean,
    val sourceAvailable: Boolean,
    val installed: Boolean,
    /** True only when the live vendor database matches the staged patched database. */
    val active: Boolean,
    /** True when this module has its own boot mount, or an overlay mounter is available. */
    val mountingReady: Boolean,
    val removalPending: Boolean,
    val rebootRequired: Boolean,
    val device: String,
    val sourceSha256: String,
    val patchedSha256: String,
    val message: String,
    /** Magisk, KernelSU or APatch; empty when none was found or the service predates 2.2. */
    val rootManager: String = "",
    val mountMethod: String = "",
    val bootMountStatus: String = "",
) {
    companion object {
        fun fromJson(value: String): RegionalModemPatchStatus {
            val json = JSONObject(value)
            return RegionalModemPatchStatus(
                supported = json.optBoolean("supported"),
                magiskAvailable = json.optBoolean("magiskAvailable"),
                sourceAvailable = json.optBoolean("sourceAvailable"),
                installed = json.optBoolean("installed"),
                active = json.optBoolean("active"),
                mountingReady = json.optBoolean("mountingReady", true),
                removalPending = json.optBoolean("removalPending"),
                rebootRequired = json.optBoolean("rebootRequired"),
                device = json.optString("device"),
                sourceSha256 = json.optString("sourceSha256"),
                patchedSha256 = json.optString("patchedSha256"),
                message = json.optString("message"),
                rootManager = json.optString("rootManager"),
                mountMethod = json.optString("mountMethod"),
                bootMountStatus = json.optString("bootMountStatus"),
            )
        }

        fun unavailable(message: String) = RegionalModemPatchStatus(
            supported = false,
            magiskAvailable = false,
            sourceAvailable = false,
            installed = false,
            active = false,
            mountingReady = false,
            removalPending = false,
            rebootRequired = false,
            device = "",
            sourceSha256 = "",
            patchedSha256 = "",
            message = message,
        )
    }
}

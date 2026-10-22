package dev.bluehouse.enablevolte

internal val regionalOverlayLabelPaths = listOf(
    "/system" to "system",
    "/vendor" to "system/vendor",
    "/vendor/firmware" to "system/vendor/firmware",
    "/vendor/firmware/carrierconfig" to "system/vendor/firmware/carrierconfig",
    "/vendor/firmware/carrierconfig/cfg.db" to "system/vendor/firmware/carrierconfig/cfg.db",
)

/** Directory metadata is visible through OverlayFS, not only the replacement file's label. */
internal fun applyRegionalOverlayLabels(
    modulePath: String,
    copyLabel: (reference: String, stagedPath: String) -> Boolean,
) {
    regionalOverlayLabelPaths.forEach { (reference, relative) ->
        check(copyLabel(reference, "$modulePath/$relative")) {
            "Unable to preserve the live SELinux label for $reference; the module was not activated."
        }
    }
}

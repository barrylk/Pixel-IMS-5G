package dev.bluehouse.enablevolte

import android.os.Build.VERSION_CODES
import android.telephony.CarrierConfigManager

/**
 * A SIM config toggle described as data rather than as a branch in the UI.
 *
 * Several of these are not a single CarrierConfig key. VoNR has to carry its
 * visibility key, cross-SIM has to carry the opportunistic-data key, and 4G+
 * writes three keys of which one is inverted. Expressing that here keeps the
 * eighteen near-identical if/else blocks out of the config page, and lets the
 * pairings be unit tested without a device.
 */
data class BooleanControl(
    val id: String,
    /**
     * The key read back after a write to decide whether it actually landed.
     * Must appear in [onValues] so the raw value can be read as on or off.
     */
    val readKey: String,
    /** Every key written when the control is switched on. Off writes the negation. */
    val onValues: Map<String, Boolean>,
    /** Whether IMS has to re-register before the change takes effect. */
    val restartIms: Boolean = false,
    val minSdk: Int = VERSION_CODES.BASE,
) {
    init {
        require(readKey in onValues) { "readKey $readKey of control $id is not among the keys it writes" }
    }

    /** The keys and values to write to put this control in the requested position. */
    fun valuesFor(on: Boolean): Map<String, Boolean> = if (on) onValues else onValues.mapValues { !it.value }

    /**
     * Reads the raw value of [readKey] as "this control is on".
     *
     * 4G+ is the case that matters: it is on when its hide key is false, so a
     * raw true does not always mean on.
     */
    fun isOn(raw: Boolean): Boolean = raw == onValues.getValue(readKey)
}

/**
 * Every boolean control on the SIM config page.
 *
 * The app's minSdk is 31, so [VERSION_CODES.Q] and [VERSION_CODES.R] gates that
 * the page used to carry were always true and are not repeated here. Only
 * Tiramisu and Upside Down Cake can actually be missing at runtime.
 */
object ConfigControls {
    val VOLTE = BooleanControl(
        id = "volte",
        readKey = CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL to true),
        restartIms = true,
    )

    val VONR = BooleanControl(
        id = "vonr",
        readKey = CarrierConfigManager.KEY_VONR_ENABLED_BOOL,
        onValues = mapOf(
            CarrierConfigManager.KEY_VONR_ENABLED_BOOL to true,
            CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL to true,
        ),
        restartIms = true,
        minSdk = VERSION_CODES.UPSIDE_DOWN_CAKE,
    )

    val CROSS_SIM = BooleanControl(
        id = "crossSim",
        readKey = CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL,
        onValues = mapOf(
            CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL to true,
            CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL to true,
        ),
        restartIms = true,
        minSdk = VERSION_CODES.TIRAMISU,
    )

    val VOWIFI = BooleanControl(
        id = "voWifi",
        readKey = CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL to true),
        restartIms = true,
    )

    val VOWIFI_ROAMING = BooleanControl(
        id = "voWifiRoaming",
        readKey = CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL to true),
        restartIms = true,
    )

    val SS_OVER_UT = BooleanControl(
        id = "ssOverUt",
        readKey = CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL to true),
        restartIms = true,
    )

    val SS_OVER_CDMA = BooleanControl(
        id = "ssOverCdma",
        readKey = CarrierConfigManager.KEY_SUPPORT_SS_OVER_CDMA_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_SUPPORT_SS_OVER_CDMA_BOOL to true),
        restartIms = true,
    )

    val VIDEO_CALLING = BooleanControl(
        id = "videoCalling",
        readKey = CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL to true),
        restartIms = true,
    )

    // Turning 4G+ on means making the setting editable, defaulting it on, and
    // un-hiding it — so the hide key moves the opposite way from the other two.
    val FOUR_G_PLUS = BooleanControl(
        id = "fourGPlus",
        readKey = CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL,
        onValues = mapOf(
            CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL to true,
            CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL to true,
            CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL to false,
        ),
    )

    val ALLOW_ADDING_APNS = BooleanControl(
        id = "allowAddingApns",
        readKey = CarrierConfigManager.KEY_ALLOW_ADDING_APNS_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_ALLOW_ADDING_APNS_BOOL to true),
    )

    val SHOW_VOWIFI_MODE = BooleanControl(
        id = "showVoWifiMode",
        readKey = CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL to true),
        restartIms = true,
    )

    val SHOW_VOWIFI_ROAMING_MODE = BooleanControl(
        id = "showVoWifiRoamingMode",
        readKey = CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL to true),
        restartIms = true,
    )

    val WFC_WIFI_ONLY = BooleanControl(
        id = "wfcWifiOnly",
        readKey = CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL to true),
        restartIms = true,
    )

    val SHOW_VOWIFI_ICON = BooleanControl(
        id = "showVoWifiIcon",
        readKey = CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL to true),
    )

    val ALWAYS_SHOW_DATA_ICON = BooleanControl(
        id = "alwaysShowDataIcon",
        readKey = CarrierConfigManager.KEY_ALWAYS_SHOW_DATA_RAT_ICON_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_ALWAYS_SHOW_DATA_RAT_ICON_BOOL to true),
    )

    val SHOW_4G_FOR_LTE = BooleanControl(
        id = "show4GForLte",
        readKey = CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL to true),
    )

    val HIDE_ENHANCED_DATA_ICON = BooleanControl(
        id = "hideEnhancedDataIcon",
        readKey = CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL to true),
    )

    val SHOW_IMS_IN_SIM_INFO = BooleanControl(
        id = "showImsInSimInfo",
        readKey = CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL,
        onValues = mapOf(CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL to true),
    )

    /** Read once at load time, so the page can fetch every value in one pass. */
    val ALL = listOf(
        VOLTE,
        VONR,
        CROSS_SIM,
        VOWIFI,
        VOWIFI_ROAMING,
        SS_OVER_UT,
        SS_OVER_CDMA,
        VIDEO_CALLING,
        FOUR_G_PLUS,
        ALLOW_ADDING_APNS,
        SHOW_VOWIFI_MODE,
        SHOW_VOWIFI_ROAMING_MODE,
        WFC_WIFI_ONLY,
        SHOW_VOWIFI_ICON,
        ALWAYS_SHOW_DATA_ICON,
        SHOW_4G_FOR_LTE,
        HIDE_ENHANCED_DATA_ICON,
        SHOW_IMS_IN_SIM_INFO,
    )
}

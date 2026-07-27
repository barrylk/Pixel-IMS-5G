package dev.bluehouse.enablevolte

import android.annotation.SuppressLint
import android.app.IActivityManager
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.IInterface
import android.os.IBinder
import android.os.Parcel
import android.os.PersistableBundle
import android.os.IPowerManager
import android.os.ServiceManager
import android.telephony.CarrierConfigManager
import android.telephony.AccessNetworkConstants
import android.telephony.AccessNetworkUtils
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfo
import android.telephony.CellSignalStrengthNr
import android.telephony.ICellInfoCallback
import android.telephony.NetworkRegistrationInfo
import android.telephony.RadioAccessSpecifier
import android.telephony.ServiceState
import android.telephony.SubscriptionInfo
import android.telephony.TelephonyManager
import android.telephony.TelephonyFrameworkInitializer
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.internal.telephony.ICarrierConfigLoader
import com.android.internal.telephony.IBooleanConsumer
import com.android.internal.telephony.IPhoneSubInfo
import com.android.internal.telephony.ISub
import com.android.internal.telephony.ITelephony
import rikka.shizuku.SystemServiceHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object InterfaceCache {
    val cache = HashMap<String, IInterface>()
}

open class Moder {
    @Suppress("ktlint:standard:property-naming")
    val KEY_IMS_USER_AGENT = "ims.ims_user_agent_string"

    protected inline fun <reified T : IInterface> loadCachedInterface(interfaceLoader: () -> T): T {
        InterfaceCache.cache[T::class.java.name]?.let {
            return it as T
        } ?: run {
            val i = interfaceLoader()
            InterfaceCache.cache[T::class.java.name] = i
            return i
        }
    }

    protected val carrierConfigLoader: ICarrierConfigLoader
        get() =
            ICarrierConfigLoader.Stub.asInterface(
                PrivilegeManager.wrapService(
                    Context.CARRIER_CONFIG_SERVICE,
                    try {
                        TelephonyFrameworkInitializer
                            .getTelephonyServiceManager()
                            .carrierConfigServiceRegisterer
                            .get()
                    } catch (e: NoClassDefFoundError) {
                        ServiceManager.getService(Context.CARRIER_CONFIG_SERVICE)
                    }!!,
                ),
            )

    protected val telephony: ITelephony
        get() =
            ITelephony.Stub.asInterface(
                PrivilegeManager.wrapService(
                    Context.TELEPHONY_SERVICE,
                    try {
                        TelephonyFrameworkInitializer
                            .getTelephonyServiceManager()
                            .telephonyServiceRegisterer
                            .get()
                    } catch (e: NoClassDefFoundError) {
                        ServiceManager.getService(Context.TELEPHONY_SERVICE)
                    }!!,
                ),
            )

    protected val phoneSubInfo: IPhoneSubInfo
        get() =
            IPhoneSubInfo.Stub.asInterface(
                PrivilegeManager.wrapService(
                    "iphonesubinfo",
                    try {
                        TelephonyFrameworkInitializer
                            .getTelephonyServiceManager()
                            .phoneSubServiceRegisterer
                            .get()
                    } catch (e: NoClassDefFoundError) {
                        ServiceManager.getService("iphonesubinfo")
                    }!!,
                ),
            )

    protected val sub: ISub
        get() =
            ISub.Stub.asInterface(
                PrivilegeManager.wrapService(
                    "isub",
                    try {
                        TelephonyFrameworkInitializer
                            .getTelephonyServiceManager()
                            .subscriptionServiceRegisterer
                            .get()
                    } catch (e: NoClassDefFoundError) {
                        ServiceManager.getService("isub")
                    }!!,
                ),
            )
}

class CarrierModer(
    private val context: Context,
) : Moder() {
    fun getActiveSubscriptionInfoForSimSlotIndex(index: Int): SubscriptionInfo? {
        val sub = this.loadCachedInterface { sub }
        return try {
            sub.getActiveSubscriptionInfoForSimSlotIndex(index, null, null)
        } catch (e: NoSuchMethodError) {
            val getActiveSubscriptionInfoForSimSlotIndexMethod =
                sub.javaClass.getMethod(
                    "getActiveSubscriptionInfoForSimSlotIndex",
                    Int::class.javaPrimitiveType,
                    String::class.java,
                )
            (getActiveSubscriptionInfoForSimSlotIndexMethod.invoke(sub, index, null) as? SubscriptionInfo)
        }
    }

    val subscriptions: List<SubscriptionInfo>
        get() {
            val sub = this.loadCachedInterface { sub }
            try {
                return sub.getActiveSubscriptionInfoList(null, null, true) ?: emptyList()
            } catch (e: NoSuchMethodError) {
            }
            return try {
                val getActiveSubscriptionInfoListMethod =
                    sub.javaClass.getMethod(
                        "getActiveSubscriptionInfoList",
                        String::class.java,
                        String::class.java,
                    )
                (getActiveSubscriptionInfoListMethod.invoke(sub, null, null) as? List<SubscriptionInfo>) ?: emptyList()
            } catch (e: NoSuchMethodException) {
                val getActiveSubscriptionInfoListMethod =
                    sub.javaClass.getMethod(
                        "getActiveSubscriptionInfoList",
                        String::class.java,
                    )
                (getActiveSubscriptionInfoListMethod.invoke(sub, null) as? List<SubscriptionInfo>) ?: emptyList()
            }
        }

    val defaultSubId: Int
        get() {
            val sub = this.loadCachedInterface { sub }
            return sub.defaultSubId
        }

    val deviceSupportsIMS: Boolean
        get() {
            val res = Resources.getSystem()
            val volteConfigId = res.getIdentifier("config_device_volte_available", "bool", "android")
            return res.getBoolean(volteConfigId)
        }

    fun restoreAllManagedSettingsAndReboot() {
        subscriptions.forEach { SubscriptionModer(context, it.subscriptionId).restoreGoogleDefaults() }
        context.getSharedPreferences("pixel_ims_5g_network_modes", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("github_updater", Context.MODE_PRIVATE).edit().clear().apply()
        val power = IPowerManager.Stub.asInterface(
            PrivilegeManager.wrapService(
                Context.POWER_SERVICE,
                ServiceManager.getService(Context.POWER_SERVICE),
            ),
        )
        power.reboot(false, "Pixel IMS 5G restore", false)
    }
}

class SubscriptionModer(
    private val context: Context,
    val subscriptionId: Int,
) : Moder() {
    @Suppress("ktlint:standard:property-naming")
    private val TAG = "CarrierModer"

    companion object {
        private const val NETWORK_PREFS = "pixel_ims_5g_network_modes"
        private const val ORIGINAL_MASK_PREFIX = "original_user_mask_"
        private const val ORIGINAL_CARRIER_MASK_PREFIX = "original_carrier_mask_"
        private const val ORIGINAL_NR_AVAIL_PREFIX = "original_nr_availability_"
        private const val PROFILE_MODE_PREFIX = "radio_profile_mode_"
        private const val LAST_ACTION_PREFIX = "last_action_"
        private const val LAST_USER_MASK_PREFIX = "last_user_mask_"
        private const val LAST_CARRIER_MASK_PREFIX = "last_carrier_mask_"
        private const val LAST_LTE_BANDS_PREFIX = "last_lte_bands_"
        private const val LAST_NR_BANDS_PREFIX = "last_nr_bands_"
        private const val LAST_NR_AVAIL_PREFIX = "last_nr_availability_"
        private const val LAST_PROFILE_MODE_PREFIX = "last_radio_profile_mode_"
        private const val LAST_CA_PREFIX = "last_tensor_ca_"
        private const val CACHED_LTE_BANDS_PREFIX = "cached_lte_bands_"
        private const val CACHED_NR_BANDS_PREFIX = "cached_nr_bands_"
        private const val BAND_CACHE_VALID_PREFIX = "band_cache_valid_"
        private const val EASY_MODE_PREFIX = "easy_mode_"
        private const val ORIGINAL_CA_PREFIX = "original_tensor_ca_"
        private const val ROOT_FORCE_ACTIVE_PREFIX = "root_force_active_"
        private const val ROOT_FORCE_MASK_PREFIX = "root_force_mask_"
        private const val ROOT_FORCE_NR_AVAIL_PREFIX = "root_force_nr_availability_"
        private const val ROOT_FORCE_LTE_BANDS_PREFIX = "root_force_lte_bands_"
        private const val ROOT_FORCE_NR_BANDS_PREFIX = "root_force_nr_bands_"
        private const val ROOT_FORCE_PROPERTY_PREFIX = "root_force_property_"
        private const val OEM_RIL_SERVICE = "telephony.oem.oemrilhook"
        private const val OEM_RIL_DESCRIPTOR = "com.samsung.slsi.telephony.oem.oemrilhook.IOemRilHook"
        private const val OEM_RIL_GET_RADIO_NODE = 1
        private const val OEM_RIL_SET_RADIO_NODE_INT = 2
        private const val TENSOR_LTE_CA_ENABLEMENT_NODE = 12300
        private val ROOT_FORCE_REASONS = listOf(
            0 to "User",
            1 to "Power",
            2 to "Carrier",
            3 to "2G control",
            4 to "Test",
        )
        private val ROOT_FORCE_PROPERTIES = listOf(
            "persist.dbg.ims_volte_enable" to "1",
            "persist.dbg.volte_avail_ovr" to "1",
            "persist.dbg.wfc_avail_ovr" to "1",
            "persist.dbg.vt_avail_ovr" to "1",
        )
    }

    enum class ImsIssue {
        REGISTERED,
        NO_CELLULAR_SERVICE,
        VOLTE_DISABLED_BY_CONFIG,
        LTE_NR_NOT_ALLOWED,
        CARRIER_PROVISIONING_OR_NETWORK,
        STATUS_UNAVAILABLE,
    }

    data class ImsDiagnosis(
        val registered: Boolean,
        val issue: ImsIssue,
    )

    data class EasyModeResult(
        val applied: Boolean,
        val caEnabled: Boolean?,
    )

    data class NetworkGate(
        val reason: Int,
        val label: String,
        val mask: Long?,
        val lteAllowed: Boolean,
        val nrAllowed: Boolean,
    )

    enum class RootForceVerdict {
        NR_CONNECTED,
        NSA_AVAILABLE,
        LOCAL_POLICY_BLOCKED,
        WAITING_FOR_NR_CELL,
        MODEM_OR_NETWORK_REJECTED,
        UNKNOWN,
    }

    data class RootForceReport(
        val active: Boolean,
        val gates: List<NetworkGate>,
        val carrierNsa: Boolean,
        val carrierSa: Boolean,
        val nrAvailable: Boolean?,
        val endcAvailable: Boolean?,
        val dataRat: String,
        val verdict: RootForceVerdict,
    )

    data class RootForceResult(
        val applied: Boolean,
        val failedGates: List<String>,
        val report: RootForceReport,
    )

    data class ShizukuRegionalResult(
        val applied: Boolean,
        val failedGates: List<String>,
        val limitations: List<String>,
        val report: RootForceReport,
    )

    val easyModeEnabled: Boolean
        get() = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
            .getBoolean(EASY_MODE_PREFIX + subscriptionId, false)

    fun setEasyMode(enabled: Boolean): EasyModeResult {
        val prefs = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
        if (!enabled) {
            prefs.edit().putBoolean(EASY_MODE_PREFIX + subscriptionId, false).apply()
            return EasyModeResult(true, getTensorLteCaEnabled())
        }
        saveChangeSnapshot("Enabled VoLTE + LTE CA Easy Mode")
        if (!prefs.contains(ORIGINAL_CA_PREFIX + subscriptionId)) {
            prefs.edit().putInt(
                ORIGINAL_CA_PREFIX + subscriptionId,
                when (getTensorLteCaEnabled()) { true -> 1; false -> 0; null -> -1 },
            ).apply()
        }
        setBandSelectionInternal(intArrayOf(), intArrayOf())
        setRadioMode(1, recordChange = false)
        publishBundle {
            it.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
            it.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true)
            it.putBoolean(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, true)
            it.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false)
            it.putBoolean(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, false)
            it.putBoolean(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL, false)
            it.putBoolean(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL, true)
        }
        val caRequested = if (getTensorLteCaEnabled() == true) true else setTensorLteCaEnabled(true)
        restartIMSRegistration()
        Thread.sleep(750)
        val caEnabled = getTensorLteCaEnabled()
        val applied = isVoLteConfigEnabled && (caEnabled != false || caRequested != false)
        prefs.edit().putBoolean(EASY_MODE_PREFIX + subscriptionId, applied).apply()
        return EasyModeResult(applied, caEnabled)
    }

    val radioModeIndex: Int
        get() {
            return context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
                .getInt(PROFILE_MODE_PREFIX + subscriptionId, 0)
        }

    fun setRadioMode(index: Int, recordChange: Boolean = true): Boolean {
        val phone = this.loadCachedInterface { telephony }
        val userReason = TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
        val carrierReason = TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER
        val currentUser = phone.getAllowedNetworkTypesForReason(subscriptionId, userReason)
        val currentCarrier = phone.getAllowedNetworkTypesForReason(subscriptionId, carrierReason)
        if (recordChange) saveChangeSnapshot("Radio profile changed")
        val prefs = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
        val originalKey = ORIGINAL_MASK_PREFIX + subscriptionId
        val originalCarrierKey = ORIGINAL_CARRIER_MASK_PREFIX + subscriptionId

        if (index != 0 && !prefs.contains(originalKey)) {
            prefs.edit()
                .putLong(originalKey, currentUser)
                .putLong(originalCarrierKey, currentCarrier)
                .apply()
        }

        val requestedUser = when (index) {
            0 -> prefs.getLong(originalKey, currentUser)
            1 -> currentUser or
                TelephonyManager.NETWORK_TYPE_BITMASK_NR or
                TelephonyManager.NETWORK_TYPE_BITMASK_LTE
            2 -> TelephonyManager.NETWORK_TYPE_BITMASK_NR
            else -> throw IllegalArgumentException("Unknown radio mode index: $index")
        }
        val requestedCarrier = when (index) {
            0 -> prefs.getLong(originalCarrierKey, currentCarrier)
            else -> currentCarrier or
                TelephonyManager.NETWORK_TYPE_BITMASK_NR or
                TelephonyManager.NETWORK_TYPE_BITMASK_LTE
        }

        val carrierChanged = setAllowedNetworkTypesForReason(phone, carrierReason, requestedCarrier)
        val userChanged = setAllowedNetworkTypesForReason(phone, userReason, requestedUser)
        if (carrierChanged && userChanged) {
            prefs.edit().putInt(PROFILE_MODE_PREFIX + subscriptionId, index).apply()
        }
        if (carrierChanged && userChanged && index == 0) {
            prefs.edit().remove(originalKey).remove(originalCarrierKey).apply()
        }
        return carrierChanged && userChanged
    }

    private fun encode(values: IntArray): String = values.joinToString(",")

    private fun decode(value: String?): IntArray = value.orEmpty()
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .distinct()
        .sorted()
        .toIntArray()

    private fun saveChangeSnapshot(action: String) {
        val phone = this.loadCachedInterface { telephony }
        val bands = try { getBandSelection() } catch (_: Exception) { BandSelection(intArrayOf(), intArrayOf()) }
        val prefs = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(LAST_ACTION_PREFIX + subscriptionId, action)
            .putLong(
                LAST_USER_MASK_PREFIX + subscriptionId,
                phone.getAllowedNetworkTypesForReason(subscriptionId, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER),
            )
            .putLong(
                LAST_CARRIER_MASK_PREFIX + subscriptionId,
                phone.getAllowedNetworkTypesForReason(subscriptionId, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER),
            )
            .putString(LAST_LTE_BANDS_PREFIX + subscriptionId, encode(bands.lteBands))
            .putString(LAST_NR_BANDS_PREFIX + subscriptionId, encode(bands.nrBands))
            .putString(
                LAST_NR_AVAIL_PREFIX + subscriptionId,
                encode(getIntArrayValue(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY)),
            )
            .putInt(LAST_PROFILE_MODE_PREFIX + subscriptionId, prefs.getInt(PROFILE_MODE_PREFIX + subscriptionId, 0))
            .putInt(
                LAST_CA_PREFIX + subscriptionId,
                when (getTensorLteCaEnabled()) { true -> 1; false -> 0; null -> -1 },
            )
            .apply()
    }

    val lastChangeDescription: String?
        get() = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
            .getString(LAST_ACTION_PREFIX + subscriptionId, null)

    fun hasCellularService(): Boolean {
        return try {
            val state = this.loadCachedInterface { telephony }
                .getServiceStateForSlot(simSlotIndex, false, false, "com.android.shell", null)
            state?.state == ServiceState.STATE_IN_SERVICE
        } catch (_: Exception) {
            false
        }
    }

    fun undoLastChange(restoreBands: Boolean = true): Boolean {
        val prefs = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(LAST_ACTION_PREFIX + subscriptionId)) return false
        val phone = this.loadCachedInterface { telephony }
        var restored = true
        val user = prefs.getLong(LAST_USER_MASK_PREFIX + subscriptionId, -1L)
        val carrier = prefs.getLong(LAST_CARRIER_MASK_PREFIX + subscriptionId, -1L)
        if (user >= 0) {
            restored = runCatching {
                setAllowedNetworkTypesForReason(phone, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, user)
            }.getOrDefault(false) && restored
        }
        if (carrier >= 0) {
            restored = runCatching {
                setAllowedNetworkTypesForReason(phone, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER, carrier)
            }.getOrDefault(false) && restored
        }
        restored = runCatching {
            updateCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                decode(prefs.getString(LAST_NR_AVAIL_PREFIX + subscriptionId, "")),
            )
        }.isSuccess && restored
        // Some Pixel 6 radio builds reject system-selection-channel reads and resets. Continue
        // restoring all other gates and report the band limitation instead of abandoning rollback.
        if (restoreBands) {
            restored = runCatching {
                setBandSelectionInternal(
                    decode(prefs.getString(LAST_LTE_BANDS_PREFIX + subscriptionId, "")),
                    decode(prefs.getString(LAST_NR_BANDS_PREFIX + subscriptionId, "")),
                )
            }.getOrDefault(false) && restored
        }
        prefs.edit().putInt(
            PROFILE_MODE_PREFIX + subscriptionId,
            prefs.getInt(LAST_PROFILE_MODE_PREFIX + subscriptionId, 0),
        ).apply()
        prefs.getInt(LAST_CA_PREFIX + subscriptionId, -1).takeIf { it >= 0 }?.let {
            val caRestored = runCatching { setTensorLteCaEnabled(it == 1) }.getOrNull()
            if (caRestored == false) restored = false
        }
        clearLastChange(prefs)
        runCatching { restartIMSRegistration() }
        return restored
    }

    private fun clearLastChange(prefs: android.content.SharedPreferences = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)) {
        prefs.edit()
            .remove(LAST_ACTION_PREFIX + subscriptionId)
            .remove(LAST_USER_MASK_PREFIX + subscriptionId)
            .remove(LAST_CARRIER_MASK_PREFIX + subscriptionId)
            .remove(LAST_LTE_BANDS_PREFIX + subscriptionId)
            .remove(LAST_NR_BANDS_PREFIX + subscriptionId)
            .remove(LAST_NR_AVAIL_PREFIX + subscriptionId)
            .remove(LAST_PROFILE_MODE_PREFIX + subscriptionId)
            .remove(LAST_CA_PREFIX + subscriptionId)
            .apply()
    }

    private fun setAllowedNetworkTypesForReason(
        phone: ITelephony,
        reason: Int,
        networkTypes: Long,
    ): Boolean {
        return try {
            phone.setAllowedNetworkTypesForReason(subscriptionId, reason, networkTypes)
        } catch (e: NoSuchMethodError) {
            // Android 17 adds the calling package to this hidden Binder method.
            val method = phone.javaClass.getMethod(
                "setAllowedNetworkTypesForReason",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                String::class.java,
            )
            method.invoke(phone, subscriptionId, reason, networkTypes, "com.android.shell") as Boolean
        }
    }

    data class BandSelection(
        val lteBands: IntArray,
        val nrBands: IntArray,
        val modemReadbackAvailable: Boolean = true,
        val knownSelection: Boolean = true,
    )

    data class RadioDiagnostics(
        val lteBands: IntArray,
        val nrBands: IntArray,
        val servingLteBands: IntArray,
        val servingNrBands: IntArray,
        val dataRat: String,
        val displayTechnology: String,
        val usingCarrierAggregation: Boolean,
        val nrState: Int,
        val nrAvailable: Boolean?,
        val endcAvailable: Boolean?,
        val dcNrRestricted: Boolean?,
        val imsRegistered: Boolean,
        val imsTransport: String,
        val serviceState: String,
        val operatorName: String,
        val operatorNumeric: String,
        val roaming: Boolean?,
        val channelNumber: Int?,
        val duplexMode: Int?,
        val registrationRejectCause: Int?,
        val registeredPlmn: String?,
        val registrationServices: List<Int>,
        val networkSearching: Boolean?,
        val nonTerrestrialNetwork: Boolean?,
        val registrationSummary: String,
        val serviceStateSummary: String,
        val cells: List<CellSnapshot>,
    )

    data class CellSnapshot(
        val type: String,
        val registered: Boolean,
        val band: String,
        val channel: Int,
        val pci: Int,
        val tac: Int,
        val cellId: String,
        val dbm: Int,
        val level: Int,
        val rsrp: Int?,
        val rsrq: Int?,
        val sinr: Int?,
        val rssi: Int?,
        val cqi: Int?,
        val timingAdvance: Int?,
        val bandwidthKhz: Int?,
        val frequencyKhz: Long?,
        val allBands: String,
        val csiRsrp: Int?,
        val csiRsrq: Int?,
        val csiSinr: Int?,
        val csiCqiTable: Int?,
        val csiCqiReport: String?,
        val connectionStatus: Int,
        val operator: String,
    )

    @SuppressLint("MissingPermission")
    fun getRadioDiagnostics(): RadioDiagnostics {
        val phone = this.loadCachedInterface { telephony }
        var refreshedCells: List<CellInfo>? = null
        val refreshLatch = CountDownLatch(1)
        val callback = object : ICellInfoCallback.Stub() {
            override fun onCellInfo(cellInfo: MutableList<CellInfo>?) {
                refreshedCells = cellInfo?.toList()
                refreshLatch.countDown()
            }

            override fun onError(errorCode: Int, exceptionName: String?, message: String?) {
                Log.w(TAG, "Cell scan refresh failed: $errorCode $exceptionName $message")
                refreshLatch.countDown()
            }
        }
        val cells = try {
            phone.requestCellInfoUpdate(subscriptionId, callback, "com.android.shell", null)
            refreshLatch.await(4, TimeUnit.SECONDS)
            refreshedCells ?: phone.getAllCellInfo("com.android.shell", null) ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read visible cells", e)
            try {
                phone.getAllCellInfo("com.android.shell", null) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        val lteCells = cells.filterIsInstance<CellInfoLte>()
        val nrCells = cells.filterIsInstance<CellInfoNr>()
        val state = try {
            phone.getServiceStateForSlot(simSlotIndex, false, false, "com.android.shell", null)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read service state", e)
            null
        }
        val registration = state?.getNetworkRegistrationInfo(
            NetworkRegistrationInfo.DOMAIN_PS,
            AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
        )
        val dataInfo = registration?.dataSpecificInfo
        val servingIdentity = registration?.cellIdentity
        val cellSnapshots = cells.mapNotNull { cell ->
            when (cell) {
                is CellInfoLte -> {
                    val identity = cell.cellIdentity
                    val signal = cell.cellSignalStrength
                    val band = identity.bands.firstOrNull()
                        ?: AccessNetworkUtils.getOperatingBandForEarfcn(identity.earfcn)
                    CellSnapshot(
                        type = "LTE",
                        registered = cell.isRegistered,
                        band = if (band == AccessNetworkUtils.INVALID_BAND) "—" else "B$band",
                        channel = identity.earfcn,
                        pci = identity.pci,
                        tac = identity.tac,
                        cellId = identity.ci.toString(),
                        dbm = signal.dbm,
                        level = signal.level,
                        rsrp = signal.rsrp.validSignalValue(),
                        rsrq = signal.rsrq.validSignalValue(),
                        sinr = signal.rssnr.validSignalValue(),
                        rssi = signal.rssi.validSignalValue(),
                        cqi = signal.cqi.validSignalValue(),
                        timingAdvance = signal.timingAdvance.validSignalValue(),
                        bandwidthKhz = identity.bandwidth.takeIf { it > 0 && it != Int.MAX_VALUE },
                        frequencyKhz = null,
                        allBands = identity.bands.joinToString(prefix = "[", postfix = "]") { "B$it" },
                        csiRsrp = null,
                        csiRsrq = null,
                        csiSinr = null,
                        csiCqiTable = null,
                        csiCqiReport = null,
                        connectionStatus = cell.cellConnectionStatus,
                        operator = listOfNotNull(identity.mccString, identity.mncString).joinToString("-"),
                    )
                }
                is CellInfoNr -> {
                    val identity = cell.cellIdentity as CellIdentityNr
                    val signal = cell.cellSignalStrength as CellSignalStrengthNr
                    val band = identity.bands.firstOrNull()
                        ?: AccessNetworkUtils.getOperatingBandForNrarfcn(identity.nrarfcn)
                    CellSnapshot(
                        type = "NR",
                        registered = cell.isRegistered,
                        band = if (band == AccessNetworkUtils.INVALID_BAND) "—" else "n$band",
                        channel = identity.nrarfcn,
                        pci = identity.pci,
                        tac = identity.tac,
                        cellId = identity.nci.toString(),
                        dbm = signal.dbm,
                        level = signal.level,
                        rsrp = signal.ssRsrp.validSignalValue(),
                        rsrq = signal.ssRsrq.validSignalValue(),
                        sinr = signal.ssSinr.validSignalValue(),
                        rssi = null,
                        cqi = null,
                        timingAdvance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            signal.timingAdvanceMicros.validSignalValue()
                        } else {
                            null
                        },
                        bandwidthKhz = null,
                        frequencyKhz = nrArfcnToFrequencyKhz(identity.nrarfcn),
                        allBands = identity.bands.joinToString(prefix = "[", postfix = "]") { "n$it" },
                        csiRsrp = signal.csiRsrp.validSignalValue(),
                        csiRsrq = signal.csiRsrq.validSignalValue(),
                        csiSinr = signal.csiSinr.validSignalValue(),
                        csiCqiTable = signal.csiCqiTableIndex.validSignalValue(),
                        csiCqiReport = signal.csiCqiReport
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString(prefix = "[", postfix = "]"),
                        connectionStatus = cell.cellConnectionStatus,
                        operator = listOfNotNull(identity.mccString, identity.mncString).joinToString("-"),
                    )
                }
                else -> null
            }
        }.sortedWith(compareByDescending<CellSnapshot> { it.registered }.thenByDescending { it.dbm })
        val imsRegistered = runCatching { phone.isImsRegistered(subscriptionId) }.getOrDefault(false)
        val imsTechnology = runCatching {
            val method = phone.javaClass.methods.first {
                it.name == "getImsRegTechnologyForMmTel" && it.parameterCount == 1
            }
            method.invoke(phone, subscriptionId) as Int
        }.getOrDefault(-1)
        val nrState = runCatching {
            state?.javaClass?.getMethod("getNrState")?.invoke(state) as Int
        }.getOrDefault(0)
        val usingCarrierAggregation = runCatching {
            state?.javaClass?.getMethod("isUsingCarrierAggregation")?.invoke(state) as Boolean
        }.getOrDefault(false)
        val dataRat = registration?.accessNetworkTechnology?.let(TelephonyManager::getNetworkTypeName) ?: "Unknown"
        val displayTechnology = when {
            dataRat == "NR" -> "5G SA"
            dataRat == "LTE" && nrState == 3 -> "5G NSA"
            dataRat == "LTE" && usingCarrierAggregation -> "LTE+"
            else -> dataRat
        }
        return RadioDiagnostics(
            lteBands = lteCells.flatMap {
                val identity = it.cellIdentity
                identity.bands.toList().ifEmpty {
                    AccessNetworkUtils.getOperatingBandForEarfcn(identity.earfcn)
                        .takeIf { band -> band != AccessNetworkUtils.INVALID_BAND }
                        ?.let(::listOf) ?: emptyList()
                }
            }.distinct().sorted().toIntArray(),
            nrBands = nrCells.flatMap {
                val identity = it.cellIdentity as CellIdentityNr
                identity.bands.toList().ifEmpty {
                    AccessNetworkUtils.getOperatingBandForNrarfcn(identity.nrarfcn)
                        .takeIf { band -> band != AccessNetworkUtils.INVALID_BAND }
                        ?.let(::listOf) ?: emptyList()
                }
            }.distinct().sorted().toIntArray(),
            servingLteBands = (servingIdentity as? CellIdentityLte)?.bands?.sortedArray() ?: intArrayOf(),
            servingNrBands = (servingIdentity as? CellIdentityNr)?.bands?.sortedArray() ?: intArrayOf(),
            dataRat = dataRat,
            displayTechnology = displayTechnology,
            usingCarrierAggregation = usingCarrierAggregation,
            nrState = nrState,
            nrAvailable = dataInfo?.isNrAvailable,
            endcAvailable = dataInfo?.isEnDcAvailable,
            dcNrRestricted = dataInfo?.isDcNrRestricted,
            imsRegistered = imsRegistered,
            imsTransport = when (imsTechnology) {
                0 -> "LTE"
                1 -> "IWLAN"
                2 -> "Cross-SIM"
                3 -> "NR"
                else -> if (imsRegistered) "Registered (transport hidden)" else "Not registered"
            },
            serviceState = when (state?.state) {
                ServiceState.STATE_IN_SERVICE -> "In service"
                ServiceState.STATE_EMERGENCY_ONLY -> "Emergency only"
                ServiceState.STATE_OUT_OF_SERVICE -> "Out of service"
                ServiceState.STATE_POWER_OFF -> "Radio off"
                else -> "Unknown"
            },
            operatorName = runCatching { state?.operatorAlphaLong.orEmpty() }.getOrDefault(""),
            operatorNumeric = runCatching { state?.operatorNumeric.orEmpty() }.getOrDefault(""),
            roaming = state?.roaming,
            channelNumber = state?.channelNumber?.takeIf { it >= 0 },
            duplexMode = state?.duplexMode?.takeIf { it >= 0 },
            registrationRejectCause = if (Build.VERSION.SDK_INT >= 35) registration?.rejectCause else null,
            registeredPlmn = registration?.registeredPlmn,
            registrationServices = registration?.availableServices.orEmpty(),
            networkSearching = if (Build.VERSION.SDK_INT >= 34) registration?.isNetworkSearching else null,
            nonTerrestrialNetwork =
                if (Build.VERSION.SDK_INT >= 35) registration?.isNonTerrestrialNetwork else null,
            registrationSummary = registration?.toString().orEmpty(),
            serviceStateSummary = state?.toString().orEmpty(),
            cells = cellSnapshots,
        )
    }

    private fun Int.validSignalValue(): Int? =
        takeUnless { it == Int.MAX_VALUE || it == Int.MIN_VALUE || it == 99 || it == 2147483647 }

    /**
     * 3GPP TS 38.104 global NR-ARFCN raster. This is the reference frequency Android's
     * NRARFCN represents; the configured carrier bandwidth still determines occupied spectrum.
     */
    private fun nrArfcnToFrequencyKhz(nrarfcn: Int): Long? = when (nrarfcn) {
        in 0..599_999 -> nrarfcn * 5L
        in 600_000..2_016_666 -> 3_000_000L + (nrarfcn - 600_000L) * 15L
        in 2_016_667..3_279_165 -> 24_250_080L + (nrarfcn - 2_016_667L) * 60L
        else -> null
    }

    fun getRootForceReport(radio: RadioDiagnostics? = null): RootForceReport {
        val phone = this.loadCachedInterface { telephony }
        val gates = ROOT_FORCE_REASONS.map { (reason, label) ->
            val mask = runCatching {
                phone.getAllowedNetworkTypesForReason(subscriptionId, reason)
            }.getOrNull()
            NetworkGate(
                reason = reason,
                label = label,
                mask = mask,
                lteAllowed = mask?.let { it and TelephonyManager.NETWORK_TYPE_BITMASK_LTE != 0L } == true,
                nrAllowed = mask?.let { it and TelephonyManager.NETWORK_TYPE_BITMASK_NR != 0L } == true,
            )
        }
        val nrAvailability = getIntArrayValue(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY)
        val currentRadio = radio ?: getRadioDiagnostics()
        val localPolicyOpen = gates.filter { it.mask != null }.all { it.lteAllowed && it.nrAllowed } &&
            nrAvailability.contains(CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA) &&
            nrAvailability.contains(CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA)
        val verdict = when {
            currentRadio.dataRat == "NR" || currentRadio.servingNrBands.isNotEmpty() ->
                RootForceVerdict.NR_CONNECTED
            !localPolicyOpen -> RootForceVerdict.LOCAL_POLICY_BLOCKED
            currentRadio.endcAvailable == true -> RootForceVerdict.NSA_AVAILABLE
            currentRadio.endcAvailable == false && currentRadio.nrBands.isEmpty() ->
                RootForceVerdict.WAITING_FOR_NR_CELL
            currentRadio.nrAvailable == true -> RootForceVerdict.MODEM_OR_NETWORK_REJECTED
            else -> RootForceVerdict.UNKNOWN
        }
        return RootForceReport(
            active = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
                .getBoolean(ROOT_FORCE_ACTIVE_PREFIX + subscriptionId, false),
            gates = gates,
            carrierNsa = nrAvailability.contains(CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA),
            carrierSa = nrAvailability.contains(CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA),
            nrAvailable = currentRadio.nrAvailable,
            endcAvailable = currentRadio.endcAvailable,
            dataRat = currentRadio.dataRat,
            verdict = verdict,
        )
    }

    /**
     * Opens every Android-side NR gate exposed by TelephonyManager and CarrierConfig. This cannot
     * make a cell broadcast NR/EN-DC, add unsupported RF bands, or bypass network authentication.
     */
    fun applyRootForce(): RootForceResult {
        check(PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) {
            "Root Force requires the UID 0 backend"
        }
        val phone = this.loadCachedInterface { telephony }
        val prefs = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
        val firstApply = !prefs.getBoolean(ROOT_FORCE_ACTIVE_PREFIX + subscriptionId, false)
        val editor = prefs.edit()
        if (firstApply) {
            ROOT_FORCE_REASONS.forEach { (reason, _) ->
                runCatching { phone.getAllowedNetworkTypesForReason(subscriptionId, reason) }
                    .onSuccess { editor.putLong("$ROOT_FORCE_MASK_PREFIX${subscriptionId}_$reason", it) }
            }
            val bands = runCatching { getBandSelection() }.getOrNull()
            editor
                .putString(
                    ROOT_FORCE_NR_AVAIL_PREFIX + subscriptionId,
                    encode(getIntArrayValue(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY)),
                )
                .putString(ROOT_FORCE_LTE_BANDS_PREFIX + subscriptionId, encode(bands?.lteBands ?: intArrayOf()))
                .putString(ROOT_FORCE_NR_BANDS_PREFIX + subscriptionId, encode(bands?.nrBands ?: intArrayOf()))
            (ROOT_FORCE_PROPERTIES + ("persist.radio.is_vonr_enabled_$simSlotIndex" to "true")).forEach { (name, _) ->
                editor.putString(
                    "$ROOT_FORCE_PROPERTY_PREFIX${subscriptionId}_$name",
                    PrivilegeManager.getRootSystemProperty(name).orEmpty(),
                )
            }
            editor.apply()
        }

        val failedGates = mutableListOf<String>()
        val requiredTypes =
            TelephonyManager.NETWORK_TYPE_BITMASK_LTE or TelephonyManager.NETWORK_TYPE_BITMASK_NR
        ROOT_FORCE_REASONS.forEach { (reason, label) ->
            val current = runCatching {
                phone.getAllowedNetworkTypesForReason(subscriptionId, reason)
            }.getOrNull() ?: return@forEach
            val accepted = runCatching {
                setAllowedNetworkTypesForReason(phone, reason, current or requiredTypes)
            }.getOrDefault(false)
            if (!accepted) failedGates += label
        }
        runCatching { setBandSelectionInternal(intArrayOf(), intArrayOf()) }
            .onFailure { failedGates += "Automatic bands" }
        (ROOT_FORCE_PROPERTIES + ("persist.radio.is_vonr_enabled_$simSlotIndex" to "true")).forEach { (name, value) ->
            if (!PrivilegeManager.setRootSystemProperty(name, value)) failedGates += name
        }
        // resetIms can reload carrier state on recent Android builds, so restart first and publish
        // the force override afterwards.
        runCatching { restartIMSRegistration() }.onFailure { failedGates += "IMS restart" }
        runCatching {
            publishBundle {
                it.putIntArray(
                    CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                    intArrayOf(
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA,
                    ),
                )
                it.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false)
                it.putBoolean(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, false)
                it.putBoolean(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL, false)
            }
        }.onFailure { failedGates += "Carrier configuration" }
        Thread.sleep(1_500)
        // CarrierConfig changes may cause Phone to recompute the carrier reason. Reassert every
        // readable reason after the broadcast, then verify the actual intersection below.
        ROOT_FORCE_REASONS.forEach { (reason, label) ->
            val current = runCatching {
                phone.getAllowedNetworkTypesForReason(subscriptionId, reason)
            }.getOrNull() ?: return@forEach
            val accepted = runCatching {
                setAllowedNetworkTypesForReason(phone, reason, current or requiredTypes)
            }.getOrDefault(false)
            if (!accepted) failedGates += label
        }
        prefs.edit()
            .putBoolean(ROOT_FORCE_ACTIVE_PREFIX + subscriptionId, true)
            .putInt(PROFILE_MODE_PREFIX + subscriptionId, 0)
            .apply()
        Thread.sleep(750)
        val report = getRootForceReport()
        val readableGatesOpen = report.gates.filter { it.mask != null }.all { it.lteAllowed && it.nrAllowed }
        return RootForceResult(
            applied = failedGates.isEmpty() && readableGatesOpen && report.carrierNsa && report.carrierSa,
            failedGates = failedGates.distinct(),
            report = report,
        )
    }

    fun restoreRootForce(): Boolean {
        if (PrivilegeManager.activeMode != PrivilegeMode.ROOT || !PrivilegeManager.isRootReady()) return false
        val prefs = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ROOT_FORCE_ACTIVE_PREFIX + subscriptionId, false)) return true
        val phone = this.loadCachedInterface { telephony }
        var restored = true
        ROOT_FORCE_REASONS.forEach { (reason, _) ->
            val key = "$ROOT_FORCE_MASK_PREFIX${subscriptionId}_$reason"
            if (prefs.contains(key)) {
                restored = runCatching {
                    setAllowedNetworkTypesForReason(phone, reason, prefs.getLong(key, 0L))
                }.getOrDefault(false) && restored
            }
        }
        restored = runCatching {
            updateCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                decode(prefs.getString(ROOT_FORCE_NR_AVAIL_PREFIX + subscriptionId, "")),
            )
            setBandSelectionInternal(
                decode(prefs.getString(ROOT_FORCE_LTE_BANDS_PREFIX + subscriptionId, "")),
                decode(prefs.getString(ROOT_FORCE_NR_BANDS_PREFIX + subscriptionId, "")),
            )
        }.isSuccess && restored
        (ROOT_FORCE_PROPERTIES + ("persist.radio.is_vonr_enabled_$simSlotIndex" to "true")).forEach { (name, _) ->
            val key = "$ROOT_FORCE_PROPERTY_PREFIX${subscriptionId}_$name"
            if (prefs.contains(key)) {
                restored = PrivilegeManager.setRootSystemProperty(name, prefs.getString(key, "").orEmpty()) && restored
            }
        }
        val editor = prefs.edit().remove(ROOT_FORCE_ACTIVE_PREFIX + subscriptionId)
        ROOT_FORCE_REASONS.forEach { (reason, _) ->
            editor.remove("$ROOT_FORCE_MASK_PREFIX${subscriptionId}_$reason")
        }
        editor
            .remove(ROOT_FORCE_NR_AVAIL_PREFIX + subscriptionId)
            .remove(ROOT_FORCE_LTE_BANDS_PREFIX + subscriptionId)
            .remove(ROOT_FORCE_NR_BANDS_PREFIX + subscriptionId)
        (ROOT_FORCE_PROPERTIES + ("persist.radio.is_vonr_enabled_$simSlotIndex" to "true")).forEach { (name, _) ->
            editor.remove("$ROOT_FORCE_PROPERTY_PREFIX${subscriptionId}_$name")
        }
        editor.apply()
        runCatching { restartIMSRegistration() }
        return restored
    }

    fun requestNsaOnly(): Boolean {
        saveChangeSnapshot("Forced NSA (LTE + NR)")
        rememberOriginalNrAvailability()
        updateCarrierConfig(
            CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
            intArrayOf(CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA),
        )
        return setRadioMode(1, recordChange = false)
    }

    fun requestSaOnly(): Boolean {
        saveChangeSnapshot("Forced SA (NR only)")
        rememberOriginalNrAvailability()
        updateCarrierConfig(
            CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
            intArrayOf(CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA),
        )
        return setRadioMode(2, recordChange = false)
    }

    fun requestAutomaticRadio(): Boolean {
        saveChangeSnapshot("Restored automatic radio selection")
        val prefs = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
        val key = ORIGINAL_NR_AVAIL_PREFIX + subscriptionId
        if (prefs.contains(key)) {
            updateCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                decode(prefs.getString(key, "")),
            )
            prefs.edit().remove(key).apply()
        }
        return setRadioMode(0, recordChange = false)
    }

    private fun rememberOriginalNrAvailability() {
        val prefs = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
        val key = ORIGINAL_NR_AVAIL_PREFIX + subscriptionId
        if (!prefs.contains(key)) {
            prefs.edit().putString(
                key,
                encode(getIntArrayValue(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY)),
            ).apply()
        }
    }

    fun getBandSelection(): BandSelection {
        return try {
            readBandSelectionFromModem().also(::cacheBandSelection)
        } catch (error: IllegalStateException) {
            // Pixel 6 radio implementations can support the setter while omitting the HAL 1.6
            // getter. Android CTS explicitly treats this getter failure as optional.
            Log.i(TAG, "Band readback is unavailable; using the last callback-confirmed selection", error)
            getCachedBandSelection()
        }
    }

    private fun readBandSelectionFromModem(): BandSelection {
        val specifiers = this.loadCachedInterface { telephony }
            .getSystemSelectionChannels(subscriptionId)
            ?: emptyList()
        val lte = specifiers.firstOrNull {
            it.radioAccessNetwork == AccessNetworkConstants.AccessNetworkType.EUTRAN
        }?.bands ?: intArrayOf()
        val nr = specifiers.firstOrNull {
            it.radioAccessNetwork == AccessNetworkConstants.AccessNetworkType.NGRAN
        }?.bands ?: intArrayOf()
        return BandSelection(lte.sortedArray(), nr.sortedArray())
    }

    private fun cacheBandSelection(selection: BandSelection) {
        context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CACHED_LTE_BANDS_PREFIX + subscriptionId, encode(selection.lteBands))
            .putString(CACHED_NR_BANDS_PREFIX + subscriptionId, encode(selection.nrBands))
            .putBoolean(BAND_CACHE_VALID_PREFIX + subscriptionId, true)
            .apply()
    }

    private fun getCachedBandSelection(): BandSelection {
        val prefs = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
        val known = prefs.getBoolean(BAND_CACHE_VALID_PREFIX + subscriptionId, false)
        return BandSelection(
            lteBands = decode(prefs.getString(CACHED_LTE_BANDS_PREFIX + subscriptionId, "")),
            nrBands = decode(prefs.getString(CACHED_NR_BANDS_PREFIX + subscriptionId, "")),
            modemReadbackAvailable = false,
            knownSelection = known,
        )
    }

    fun setBandSelection(
        lteBands: IntArray,
        nrBands: IntArray,
    ): Boolean {
        saveChangeSnapshot(
            if (lteBands.isEmpty() && nrBands.isEmpty()) "Restored automatic band selection"
            else "Restricted LTE/NR bands",
        )
        return setBandSelectionInternal(lteBands, nrBands)
    }

    private fun setBandSelectionInternal(
        lteBands: IntArray,
        nrBands: IntArray,
    ): Boolean {
        val normalizedLte = lteBands.distinct().sorted().toIntArray()
        val normalizedNr = nrBands.distinct().sorted().toIntArray()
        val specifiers = mutableListOf<RadioAccessSpecifier>()
        if (normalizedLte.isNotEmpty()) {
            specifiers.add(
                RadioAccessSpecifier(
                    AccessNetworkConstants.AccessNetworkType.EUTRAN,
                    normalizedLte,
                    intArrayOf(),
                ),
            )
        }
        if (normalizedNr.isNotEmpty()) {
            specifiers.add(
                RadioAccessSpecifier(
                    AccessNetworkConstants.AccessNetworkType.NGRAN,
                    normalizedNr,
                    intArrayOf(),
                ),
            )
        }
        val result = AtomicReference<Boolean?>(null)
        val latch = CountDownLatch(1)
        val callback = object : IBooleanConsumer.Stub() {
            override fun accept(accepted: Boolean) {
                result.set(accepted)
                latch.countDown()
            }
        }
        this.loadCachedInterface { telephony }
            .setSystemSelectionChannels(specifiers, subscriptionId, callback)
        if (!latch.await(3, TimeUnit.SECONDS) || result.get() != true) return false

        val acceptedSelection = BandSelection(normalizedLte, normalizedNr)
        cacheBandSelection(acceptedSelection)
        val readback = try {
            readBandSelectionFromModem()
        } catch (_: IllegalStateException) {
            return true
        }
        return readback.lteBands.contentEquals(normalizedLte) &&
            readback.nrBands.contentEquals(normalizedNr)
    }

    fun diagnoseIms(): ImsDiagnosis {
        return try {
            if (isIMSRegistered) return ImsDiagnosis(true, ImsIssue.REGISTERED)
            if (!hasCellularService()) return ImsDiagnosis(false, ImsIssue.NO_CELLULAR_SERVICE)
            if (!isVoLteConfigEnabled) return ImsDiagnosis(false, ImsIssue.VOLTE_DISABLED_BY_CONFIG)
            val mask = this.loadCachedInterface { telephony }.getAllowedNetworkTypesForReason(
                subscriptionId,
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
            )
            if (mask and (TelephonyManager.NETWORK_TYPE_BITMASK_LTE or TelephonyManager.NETWORK_TYPE_BITMASK_NR) == 0L) {
                ImsDiagnosis(false, ImsIssue.LTE_NR_NOT_ALLOWED)
            } else {
                ImsDiagnosis(false, ImsIssue.CARRIER_PROVISIONING_OR_NETWORK)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to diagnose IMS", e)
            ImsDiagnosis(false, ImsIssue.STATUS_UNAVAILABLE)
        }
    }

    fun restoreGoogleDefaults(): Boolean {
        return try {
            setBandSelectionInternal(intArrayOf(), intArrayOf())
            setRadioMode(0, recordChange = false)
            clearCarrierConfig()
            val prefs = context.getSharedPreferences(NETWORK_PREFS, Context.MODE_PRIVATE)
            prefs.getInt(ORIGINAL_CA_PREFIX + subscriptionId, -1).takeIf { it >= 0 }?.let {
                setTensorLteCaEnabled(it == 1)
            }
            prefs.edit()
                .remove(ORIGINAL_NR_AVAIL_PREFIX + subscriptionId)
                .remove(EASY_MODE_PREFIX + subscriptionId)
                .remove(ORIGINAL_CA_PREFIX + subscriptionId)
                .apply()
            clearLastChange()
            Thread.sleep(500)
            restartIMSRegistration()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to restore Google defaults", e)
            false
        }
    }

    /**
     * Applies conservative, globally useful IMS visibility flags while leaving radio mode and
     * band selection automatic. This can expose carrier-supported features but cannot provision
     * an IMS account or bypass the carrier's subscriber/device authorization.
     */
    fun applyCompatibilityProfile(advertiseNr: Boolean): Boolean =
        runCatching {
            publishBundle {
                it.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false)
                it.putBoolean(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, false)
                it.putBoolean(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL, false)
                if (advertiseNr) {
                    it.putIntArray(
                        CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                        intArrayOf(
                            CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                            CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA,
                        ),
                    )
                }
            }
            setBandSelectionInternal(intArrayOf(), intArrayOf())
            setRadioMode(0, recordChange = false)
            restartIMSRegistration()
            true
        }.onFailure { Log.e(TAG, "Unable to apply compatibility profile", it) }.getOrDefault(false)

    /**
     * Applies the strongest reversible regional 5G profile available to the Shizuku shell UID.
     * This deliberately does not claim to replace Tensor cfg.db: Android 17 SELinux and verified
     * vendor partitions keep that operation root-only.
     */
    fun applyShizukuRegionalCompatibility(): ShizukuRegionalResult {
        check(PrivilegeManager.activeMode == PrivilegeMode.SHIZUKU) {
            "The Shizuku regional profile requires Shizuku mode"
        }
        saveChangeSnapshot("Applied Shizuku regional 5G profile")
        val failed = mutableListOf<String>()
        val limitations = mutableListOf<String>()
        val phone = this.loadCachedInterface { telephony }
        val requiredTypes =
            TelephonyManager.NETWORK_TYPE_BITMASK_LTE or TelephonyManager.NETWORK_TYPE_BITMASK_NR

        // Pixel 6 radio implementations can reject the system-selection-channel API even
        // though all Android-side NR and IMS gates below remain configurable. Band reset is
        // therefore best-effort and must not make the regional profile report total failure.
        val automaticBandsRestored = runCatching {
            setBandSelectionInternal(intArrayOf(), intArrayOf())
        }.onFailure {
            Log.i(TAG, "Automatic band reset is unavailable; preserving the current selection", it)
        }.getOrDefault(false)
        if (!automaticBandsRestored) {
            limitations += "Automatic bands unchanged (modem API unavailable)"
        }
        if (!runCatching { setRadioMode(1, recordChange = false) }.getOrDefault(false)) {
            failed += "LTE + NR allowed-network policy"
        }
        val endcControlAvailable = runCatching {
            phone.isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE,
            )
        }.getOrNull()
        if (endcControlAvailable == true) {
            val endcResult = runCatching {
                phone.setNrDualConnectivityState(
                    subscriptionId,
                    TelephonyManager.NR_DUAL_CONNECTIVITY_ENABLE,
                )
            }.getOrNull()
            if (endcResult != TelephonyManager.ENABLE_NR_DUAL_CONNECTIVITY_SUCCESS) {
                failed += "EN-DC control (result ${endcResult ?: "unreadable"})"
            } else {
                Thread.sleep(500)
                if (!runCatching { phone.isNrDualConnectivityEnabled(subscriptionId) }.getOrDefault(false)) {
                    failed += "EN-DC enablement did not persist"
                }
            }
        } else if (endcControlAvailable == false) {
            Log.i(TAG, "The modem HAL does not expose configurable NR dual connectivity")
        } else {
            failed += "EN-DC control status"
        }
        runCatching { restartIMSRegistration() }.onFailure { failed += "IMS restart" }
        runCatching {
            publishBundle {
                it.putIntArray(
                    CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                    intArrayOf(
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA,
                    ),
                )
                it.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false)
                it.putBoolean(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, false)
                it.putBoolean(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL, false)
                it.putBoolean(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL, true)
                it.putBoolean(CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL, true)
            }
        }.onFailure { failed += "Carrier configuration" }

        Thread.sleep(1_250)
        listOf(
            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER to "User network policy",
            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER to "Carrier network policy",
        ).forEach { (reason, label) ->
            val current = runCatching { phone.getAllowedNetworkTypesForReason(subscriptionId, reason) }
                .getOrNull() ?: return@forEach
            val accepted = runCatching {
                setAllowedNetworkTypesForReason(phone, reason, current or requiredTypes)
            }.getOrDefault(false)
            if (!accepted) failed += label
        }
        Thread.sleep(500)
        val report = getRootForceReport()
        val relevantGates = report.gates.filter {
            it.reason == TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER ||
                it.reason == TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER
        }
        val gatesOpen = relevantGates.isNotEmpty() && relevantGates.all { it.lteAllowed && it.nrAllowed }
        return ShizukuRegionalResult(
            applied = failed.isEmpty() && gatesOpen && report.carrierNsa,
            failedGates = failed.distinct(),
            limitations = limitations.distinct(),
            report = report,
        )
    }

    fun getNrDualConnectivityEnabled(): Boolean? =
        runCatching {
            this.loadCachedInterface { telephony }.isNrDualConnectivityEnabled(subscriptionId)
        }.getOrNull()

    /** Returns null on devices without Samsung SLSI's OEM radio service. */
    fun getTensorLteCaEnabled(): Boolean? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            val service = SystemServiceHelper.getSystemService(OEM_RIL_SERVICE) ?: return null
            val binder: IBinder = PrivilegeManager.wrapService(OEM_RIL_SERVICE, service)
            data.writeInterfaceToken(OEM_RIL_DESCRIPTOR)
            data.writeInt(TENSOR_LTE_CA_ENABLEMENT_NODE)
            data.writeInt(simSlotIndex)
            if (!binder.transact(OEM_RIL_GET_RADIO_NODE, data, reply, 0)) return null
            reply.readException()
            when (reply.readString()?.trim()) {
                "1" -> true
                "0" -> false
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read Tensor LTE CA status", e)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /** Requests the Tensor modem CA node and verifies the value through the matching getter. */
    fun setTensorLteCaEnabled(enabled: Boolean): Boolean? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            val service = SystemServiceHelper.getSystemService(OEM_RIL_SERVICE) ?: return null
            val binder: IBinder = PrivilegeManager.wrapService(OEM_RIL_SERVICE, service)
            data.writeInterfaceToken(OEM_RIL_DESCRIPTOR)
            data.writeInt(TENSOR_LTE_CA_ENABLEMENT_NODE)
            data.writeInt(if (enabled) 1 else 0)
            data.writeInt(simSlotIndex)
            if (!binder.transact(OEM_RIL_SET_RADIO_NODE_INT, data, reply, 0)) return false
            reply.readException()
            val accepted = reply.readBoolean()
            Thread.sleep(300)
            val verified = getTensorLteCaEnabled()
            when {
                verified == enabled -> true
                !accepted -> false
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to set Tensor LTE CA status", e)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun overrideConfigDirectly(bundle: Bundle?) {
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }
        if (bundle != null) {
            val args = toPersistableBundle(bundle)
            try {
                iCclInstance.overrideConfig(subscriptionId, args, true)
            } catch (e: SecurityException) {
                if (e.message?.contains("persistent=true") == true) {
                    iCclInstance.overrideConfig(subscriptionId, args, false)
                } else {
                    throw e
                }
            }
        } else {
            try {
                iCclInstance.overrideConfig(subscriptionId, null, true)
            } catch (e: SecurityException) {
                if (e.message?.contains("persistent=true") == true) {
                    iCclInstance.overrideConfig(subscriptionId, null, false)
                } else {
                    throw e
                }
            }
        }
    }

    private fun overrideConfigUsingBroker(bundle: Bundle?) {
        if (PrivilegeManager.activeMode == PrivilegeMode.ROOT) {
            overrideConfigDirectly(bundle)
            return
        }
        val am =
            IActivityManager.Stub.asInterface(
                PrivilegeManager.wrapService(
                    Context.ACTIVITY_SERVICE,
                    SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE),
                ),
            )

        val arg =
            bundle ?: run {
                val empty = Bundle()
                empty.putBoolean("moder_clear", true)
                empty
            }
        arg.putInt("moder_subId", subscriptionId)

        am.startInstrumentation(
            ComponentName(context, Class.forName("dev.bluehouse.enablevolte.BrokerInstrumentation")),
            null,
            8,
            arg,
            null,
            UiAutomationConnection(),
            0,
            null,
        )
    }

    private fun overrideConfig(bundle: Bundle?) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val securityPatchDate = sdf.parse(Build.VERSION.SECURITY_PATCH)
        if (securityPatchDate == null) {
            this.overrideConfigDirectly(bundle)
        } else {
            cal.time = securityPatchDate
            if (cal.get(Calendar.YEAR) > 2025 || (cal.get(Calendar.YEAR) == 2025 && cal.get(Calendar.MONTH) >= 9)) {
                this.overrideConfigUsingBroker(bundle)
            } else {
                this.overrideConfigDirectly(bundle)
            }
        }
    }

    private fun publishBundle(fn: (Bundle) -> Unit) {
        val overrideBundle = Bundle()
        fn(overrideBundle)
        this.overrideConfig(overrideBundle)
    }

    fun updateCarrierConfig(
        key: String,
        value: Boolean,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putBoolean(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: String,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putString(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: Int,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putInt(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: Long,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putLong(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: IntArray,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putIntArray(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: BooleanArray,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putBooleanArray(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: Array<String>,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putStringArray(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: LongArray,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putLongArray(key, value) }
    }

    fun clearCarrierConfig() {
        this.overrideConfig(null)
    }

    fun restartIMSRegistration() {
        val telephony = this.loadCachedInterface { telephony }
        val sub = this.loadCachedInterface { sub }
        telephony.resetIms(sub.getSlotIndex(this.subscriptionId))
    }

    fun getStringValue(key: String): String? {
        Log.d(TAG, "Resolving string value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return ""
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = this.getConfigForSubId(iCclInstance, subscriptionId)
        return config?.getString(key)
    }

    fun getBooleanValue(key: String): Boolean {
        Log.d(TAG, "Resolving boolean value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return false
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = this.getConfigForSubId(iCclInstance, subscriptionId)
        return config?.getBoolean(key) ?: false
    }

    fun getIntValue(key: String): Int {
        Log.d(TAG, "Resolving integer value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return -1
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = this.getConfigForSubId(iCclInstance, subscriptionId)
        return config?.getInt(key) ?: -1
    }

    fun getLongValue(key: String): Long {
        Log.d(TAG, "Resolving long value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return -1
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = this.getConfigForSubId(iCclInstance, subscriptionId)
        return config?.getLong(key) ?: -1L
    }

    fun getBooleanArrayValue(key: String): BooleanArray {
        Log.d(TAG, "Resolving boolean array value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return booleanArrayOf()
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = this.getConfigForSubId(iCclInstance, subscriptionId)
        return config?.getBooleanArray(key) ?: BooleanArray(0)
    }

    fun getIntArrayValue(key: String): IntArray {
        Log.d(TAG, "Resolving integer value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return intArrayOf()
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = this.getConfigForSubId(iCclInstance, subscriptionId)
        return config?.getIntArray(key) ?: IntArray(0)
    }

    fun getStringArrayValue(key: String): Array<String> {
        Log.d(TAG, "Resolving string array value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return arrayOf()
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = this.getConfigForSubId(iCclInstance, subscriptionId)
        return config?.getStringArray(key) ?: emptyArray()
    }

    fun getValue(key: String): Any? {
        Log.d(TAG, "Resolving value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return null
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = this.getConfigForSubId(iCclInstance, subscriptionId)
        return config?.get(key)
    }

    fun getConfigForSubId(
        iCclInstance: ICarrierConfigLoader,
        subscriptionId: Int,
    ): PersistableBundle? {
        try {
            return iCclInstance.getConfigForSubIdWithFeature(subscriptionId, iCclInstance.defaultCarrierServicePackageName, "")
        } catch (e: NoSuchMethodError) {
        }
        return try {
            iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        } catch (e: NoSuchMethodError) {
            val getConfigForSubIdMethod =
                iCclInstance.javaClass.getMethod(
                    "getConfigForSubId",
                    Int::class.javaPrimitiveType,
                )
            (getConfigForSubIdMethod.invoke(iCclInstance, subscriptionId) as? PersistableBundle)
        }
    }

    val simSlotIndex: Int
        get() = this.loadCachedInterface { sub }.getSlotIndex(subscriptionId)

    val isVoLteConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL)

    val isVoNrConfigEnabled: Boolean
        @RequiresApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
        get() =
            this.getBooleanValue(CarrierConfigManager.KEY_VONR_ENABLED_BOOL) &&
                this.getBooleanValue(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL)

    val isCrossSIMConfigEnabled: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL) &&
                    this.getBooleanValue(CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL)
            } else {
                false
            }
        }

    val isVoWifiConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL)

    val isVoWifiWhileRoamingEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL)

    val showIMSinSIMInfo: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL)

    val allowAddingAPNs: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_ALLOW_ADDING_APNS_BOOL)

    val showVoWifiMode: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL)

    val showVoWifiRoamingMode: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL)

    val wfcSpnFormatIndex: Int
        get() = this.getIntValue(CarrierConfigManager.KEY_WFC_SPN_FORMAT_IDX_INT)

    val carrierName: String?
        get() = this.loadCachedInterface { telephony }.getSubscriptionCarrierName(this.subscriptionId)

    val showVoWifiIcon: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL)

    val alwaysDataRATIcon: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_ALWAYS_SHOW_DATA_RAT_ICON_BOOL)

    val supportWfcWifiOnly: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL)

    val isVtConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL)

    val ssOverUtEnabled: Boolean
        get() =
            if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
                this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL)
            } else {
                false
            }

    val ssOverCDMAEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SUPPORT_SS_OVER_CDMA_BOOL)

    val isShow4GForLteEnabled: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL)

    val isHideEnhancedDataIconEnabled: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL)

    val is4GPlusEnabled: Boolean
        get() =
            if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
                this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL) &&
                    this.getBooleanValue(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL) &&
                    !this.getBooleanValue(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL)
            } else {
                this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL) &&
                    !this.getBooleanValue(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL)
            }

    val isNRConfigEnabled: Boolean
        get() =
            if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
                this
                    .getIntArrayValue(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY)
                    .contentEquals(intArrayOf(1, 2))
            } else {
                false
            }

    val nrAvailabilityIndex: Int
        get() {
            val values = this.getIntArrayValue(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY)
            val nsa = values.contains(CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA)
            val sa = values.contains(CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA)
            return when {
                nsa && sa -> 3
                nsa -> 1
                sa -> 2
                else -> 0
            }
        }

    val userAgentConfig: String
        get() = this.getStringValue(KEY_IMS_USER_AGENT) ?: ""

    val isIMSRegistered: Boolean
        get() {
            val telephony = this.loadCachedInterface { telephony }
            return telephony.isImsRegistered(this.subscriptionId)
        }
}

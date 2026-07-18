package dev.bluehouse.enablevolte

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
import android.os.ServiceManager
import android.telephony.CarrierConfigManager
import android.telephony.AccessNetworkConstants
import android.telephony.AccessNetworkUtils
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfo
import android.telephony.ICellInfoCallback
import android.telephony.NetworkRegistrationInfo
import android.telephony.RadioAccessSpecifier
import android.telephony.SubscriptionInfo
import android.telephony.TelephonyManager
import android.telephony.TelephonyFrameworkInitializer
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.internal.telephony.ICarrierConfigLoader
import com.android.internal.telephony.IPhoneSubInfo
import com.android.internal.telephony.ISub
import com.android.internal.telephony.ITelephony
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
                ShizukuBinderWrapper(
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
                ShizukuBinderWrapper(
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
                ShizukuBinderWrapper(
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
                ShizukuBinderWrapper(
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
        private const val OEM_RIL_SERVICE = "telephony.oem.oemrilhook"
        private const val OEM_RIL_DESCRIPTOR = "com.samsung.slsi.telephony.oem.oemrilhook.IOemRilHook"
        private const val OEM_RIL_GET_RADIO_NODE = 1
        private const val TENSOR_LTE_CA_ENABLEMENT_NODE = 12300
    }

    val radioModeIndex: Int
        get() {
            val mask = this.loadCachedInterface { telephony }.getAllowedNetworkTypesForReason(
                subscriptionId,
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
            )
            return when {
                mask == TelephonyManager.NETWORK_TYPE_BITMASK_NR -> 2
                mask and TelephonyManager.NETWORK_TYPE_BITMASK_NR != 0L -> 1
                else -> 0
            }
        }

    fun setRadioMode(index: Int): Boolean {
        val phone = this.loadCachedInterface { telephony }
        val userReason = TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
        val carrierReason = TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER
        val currentUser = phone.getAllowedNetworkTypesForReason(subscriptionId, userReason)
        val currentCarrier = phone.getAllowedNetworkTypesForReason(subscriptionId, carrierReason)
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
        if (carrierChanged && userChanged && index == 0) {
            prefs.edit().remove(originalKey).remove(originalCarrierKey).apply()
        }
        return carrierChanged && userChanged
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
    )

    data class RadioDiagnostics(
        val lteBands: IntArray,
        val nrBands: IntArray,
        val servingLteBands: IntArray,
        val servingNrBands: IntArray,
        val dataRat: String,
        val nrAvailable: Boolean?,
        val endcAvailable: Boolean?,
    )

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
            dataRat = registration?.accessNetworkTechnology?.let(TelephonyManager::getNetworkTypeName) ?: "Unknown",
            nrAvailable = dataInfo?.isNrAvailable,
            endcAvailable = dataInfo?.isEnDcAvailable,
        )
    }

    fun requestNsaOnly(): Boolean {
        updateCarrierConfig(
            CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
            intArrayOf(CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA),
        )
        return setRadioMode(1)
    }

    fun requestSaOnly(): Boolean {
        updateCarrierConfig(
            CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
            intArrayOf(CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA),
        )
        return setRadioMode(2)
    }

    fun getBandSelection(): BandSelection {
        val specifiers = this.loadCachedInterface { telephony }.getSystemSelectionChannels(subscriptionId)
        val lte = specifiers.firstOrNull {
            it.radioAccessNetwork == AccessNetworkConstants.AccessNetworkType.EUTRAN
        }?.bands ?: intArrayOf()
        val nr = specifiers.firstOrNull {
            it.radioAccessNetwork == AccessNetworkConstants.AccessNetworkType.NGRAN
        }?.bands ?: intArrayOf()
        return BandSelection(lte.sortedArray(), nr.sortedArray())
    }

    fun setBandSelection(
        lteBands: IntArray,
        nrBands: IntArray,
    ): Boolean {
        val specifiers = mutableListOf<RadioAccessSpecifier>()
        if (lteBands.isNotEmpty()) {
            specifiers.add(
                RadioAccessSpecifier(
                    AccessNetworkConstants.AccessNetworkType.EUTRAN,
                    lteBands.distinct().sorted().toIntArray(),
                    intArrayOf(),
                ),
            )
        }
        if (nrBands.isNotEmpty()) {
            specifiers.add(
                RadioAccessSpecifier(
                    AccessNetworkConstants.AccessNetworkType.NGRAN,
                    nrBands.distinct().sorted().toIntArray(),
                    intArrayOf(),
                ),
            )
        }
        this.loadCachedInterface { telephony }.setSystemSelectionChannels(specifiers, subscriptionId, null)
        Thread.sleep(750)
        val applied = getBandSelection()
        return applied.lteBands.contentEquals(lteBands.distinct().sorted().toIntArray()) &&
            applied.nrBands.contentEquals(nrBands.distinct().sorted().toIntArray())
    }

    /** Returns null on devices without Samsung SLSI's OEM radio service. */
    fun getTensorLteCaEnabled(): Boolean? {
        val service = SystemServiceHelper.getSystemService(OEM_RIL_SERVICE) ?: return null
        val binder: IBinder = ShizukuBinderWrapper(service)
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
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

    private fun overrideConfigDirectly(bundle: Bundle?) {
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }
        if (bundle != null) {
            val args = toPersistableBundle(bundle)
            iCclInstance.overrideConfig(subscriptionId, args, true)
        } else {
            iCclInstance.overrideConfig(subscriptionId, null, true)
        }
    }

    private fun overrideConfigUsingBroker(bundle: Bundle?) {
        val am =
            IActivityManager.Stub.asInterface(
                ShizukuBinderWrapper(
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

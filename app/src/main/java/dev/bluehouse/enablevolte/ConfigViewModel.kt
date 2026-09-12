package dev.bluehouse.enablevolte

import android.app.Application
import android.os.Build.VERSION
import android.telephony.CarrierConfigManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the SIM config page draws. */
data class ConfigUiState(
    val loading: Boolean = true,
    val configurable: Boolean = false,
    val booleans: Map<String, ControlState<Boolean>> = emptyMap(),
    val nrAvailability: ControlState<Int> = ControlState(),
    val radioMode: ControlState<Int> = ControlState(),
    val wfcSpnFormat: ControlState<Int> = ControlState(),
    val userAgent: ControlState<String> = ControlState(),
    val rootVoWifi: RootVoWifiStatus? = null,
    val rootVoWifiBusy: Boolean = false,
    /** Read once here; resolving it is a binder call and must not happen during composition. */
    val simSlotIndex: Int = 0,
)

/**
 * Owns the state of one SIM's config page.
 *
 * The page used to keep all of this in `remember`, which meant a rotation or a
 * theme change threw it away mid-write, and every toggle fired its binder call
 * straight from the click handler on the main thread. Holding it here survives
 * configuration changes, and every privileged call goes through
 * [applyAndConfirm], so a control only moves once the device agrees it moved.
 */
class ConfigViewModel(
    application: Application,
    private val subId: Int,
) : AndroidViewModel(application) {
    private val moder = SubscriptionModer(application, subId)
    private val carrierModer = CarrierModer(application)

    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state.asStateFlow()

    val carrierName: String? get() = runCatching { moder.carrierName }.getOrNull()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val privileged = readQuietly {
                (PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) ||
                    checkShizukuPermission(0) == ShizukuStatus.GRANTED
            } ?: false

            if (!privileged || subId < 0 || readQuietly { carrierModer.deviceSupportsIMS } != true) {
                _state.value = _state.value.copy(loading = false, configurable = false)
                return@launch
            }

            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    // One binder round trip for every boolean on the page.
                    val snapshot = moder.readConfigSnapshot()
                    val booleans = ConfigControls.ALL
                        .filter { VERSION.SDK_INT >= it.minSdk }
                        .associate { control ->
                            val raw = snapshot?.getBoolean(control.readKey) ?: false
                            control.id to ControlState(control.isOn(raw), ControlPhase.IDLE)
                        }
                    ConfigUiState(
                        loading = false,
                        configurable = true,
                        booleans = booleans,
                        nrAvailability = ControlState(moder.nrAvailabilityIndex),
                        radioMode = ControlState(moder.radioModeIndex),
                        wfcSpnFormat = ControlState(snapshot?.getInt(CarrierConfigManager.KEY_WFC_SPN_FORMAT_IDX_INT) ?: 0),
                        userAgent = ControlState(snapshot?.getString(moder.KEY_IMS_USER_AGENT) ?: ""),
                        simSlotIndex = moder.simSlotIndex,
                    )
                }.getOrElse { ConfigUiState(loading = false, configurable = false) }
            }
            _state.value = loaded

            if (PrivilegeManager.activeMode == PrivilegeMode.ROOT) {
                refreshRootVoWifi()
            }
        }
    }

    fun toggle(
        control: BooleanControl,
        requested: Boolean,
    ) {
        if (VERSION.SDK_INT < control.minSdk) return
        updateBoolean(control.id) { it.applying() }
        viewModelScope.launch {
            val outcome = applyAndConfirm(
                requested = requested,
                write = {
                    control.valuesFor(requested).forEach { (key, value) -> moder.updateCarrierConfig(key, value) }
                    if (requested && control.restartIms) {
                        moder.restartIMSRegistration()
                    }
                },
                readBack = { control.isOn(moder.getBooleanValue(control.readKey)) },
            )
            updateBoolean(control.id) { it.reduce(outcome) }
        }
    }

    fun setNrAvailability(requested: Int) {
        val values = when (requested) {
            1 -> intArrayOf(CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA)
            2 -> intArrayOf(CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA)
            3 -> intArrayOf(
                CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA,
            )
            else -> intArrayOf()
        }
        _state.value = _state.value.copy(nrAvailability = _state.value.nrAvailability.applying())
        viewModelScope.launch {
            val outcome = applyAndConfirm(
                requested = requested,
                write = { moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY, values) },
                readBack = { moder.nrAvailabilityIndex },
            )
            _state.value = _state.value.copy(nrAvailability = _state.value.nrAvailability.reduce(outcome))
        }
    }

    fun setRadioMode(requested: Int) {
        _state.value = _state.value.copy(radioMode = _state.value.radioMode.applying())
        viewModelScope.launch {
            val outcome = applyAndConfirm(
                requested = requested,
                // setRadioMode reports its own refusal, which is not visible in the read-back.
                write = { check(moder.setRadioMode(requested)) { "The radio rejected this profile." } },
                readBack = { moder.radioModeIndex },
            )
            _state.value = _state.value.copy(radioMode = _state.value.radioMode.reduce(outcome))
        }
    }

    fun setWfcSpnFormat(requested: Int) {
        _state.value = _state.value.copy(wfcSpnFormat = _state.value.wfcSpnFormat.applying())
        viewModelScope.launch {
            val outcome = applyAndConfirm(
                requested = requested,
                write = { moder.updateCarrierConfig(CarrierConfigManager.KEY_WFC_SPN_FORMAT_IDX_INT, requested) },
                readBack = { moder.wfcSpnFormatIndex },
            )
            _state.value = _state.value.copy(wfcSpnFormat = _state.value.wfcSpnFormat.reduce(outcome))
        }
    }

    fun setUserAgent(requested: String) {
        _state.value = _state.value.copy(userAgent = _state.value.userAgent.applying())
        viewModelScope.launch {
            val outcome = applyAndConfirm(
                requested = requested,
                write = { moder.updateCarrierConfig(moder.KEY_IMS_USER_AGENT, requested) },
                readBack = { moder.userAgentConfig },
            )
            _state.value = _state.value.copy(userAgent = _state.value.userAgent.reduce(outcome))
        }
    }

    fun resetAll() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { moder.clearCarrierConfig() } }
            load()
        }
    }

    fun restartIms() {
        viewModelScope.launch { withContext(Dispatchers.IO) { runCatching { moder.restartIMSRegistration() } } }
    }

    fun refreshRootVoWifi() = runRootVoWifi { PrivilegeManager.getRootVoWifiStatus(subId) }

    fun applyRootVoWifiRepair() = runRootVoWifi { moder.applyRootVoWifiRepair() }

    fun restoreRootVoWifiRepair() = runRootVoWifi { moder.restoreRootVoWifiRepair() }

    private fun runRootVoWifi(block: () -> RootVoWifiStatus) {
        _state.value = _state.value.copy(rootVoWifiBusy = true)
        viewModelScope.launch {
            val status = readQuietly(block)
            _state.value = _state.value.copy(rootVoWifi = status ?: _state.value.rootVoWifi, rootVoWifiBusy = false)
        }
    }

    private fun updateBoolean(
        id: String,
        transform: (ControlState<Boolean>) -> ControlState<Boolean>,
    ) {
        val current = _state.value
        val existing = current.booleans[id] ?: ControlState()
        _state.value = current.copy(booleans = current.booleans + (id to transform(existing)))
    }

    companion object {
        fun factory(
            application: Application,
            subId: Int,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ConfigViewModel(application, subId) as T
            }
    }
}

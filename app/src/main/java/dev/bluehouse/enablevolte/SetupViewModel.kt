package dev.bluehouse.enablevolte

import android.app.Application
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.telephony.CarrierConfigManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CheckStatus { PASS, FAIL, WARN, UNKNOWN }

/** What the user can do about a check that did not pass. */
enum class FixAction {
    NONE,
    ENABLE_VOLTE,
    ENABLE_VONR,
    SET_NR_ARCHITECTURE,
    OPEN_BANDS,
    INSTALL_MODEM_PATCH,
    RESTART_IMS,
    OPEN_EXPERT,
}

/**
 * One thing that has to be true for 5G to work, and what to do when it is not.
 *
 * The app has always been able to test each of these. What it never did was
 * put them in order and say which one is the reason.
 */
data class ReadinessCheck(
    val id: String,
    val title: String,
    val detail: String,
    val status: CheckStatus,
    val fix: FixAction = FixAction.NONE,
    val fixLabel: String? = null,
    val blocking: Boolean = true,
)

data class SetupState(
    val loading: Boolean = true,
    val busyFix: FixAction? = null,
    val checks: List<ReadinessCheck> = emptyList(),
    val carrier: String = "",
    val note: String? = null,
) {
    val blocking = checks.filter { it.blocking }
    val passed = blocking.count { it.status == CheckStatus.PASS }
    val total = blocking.size
    val ready = total > 0 && passed == total

    /** The first thing standing in the way, which is the only one worth leading with. */
    val firstProblem: ReadinessCheck? = checks.firstOrNull { it.blocking && it.status == CheckStatus.FAIL }
}

/**
 * Works out why 5G is not connecting on this SIM.
 *
 * The checks run in dependency order — there is no point reporting that the
 * modem refused EN-DC if VoLTE was never switched on — and each one that fails
 * carries the specific action that addresses it.
 */
class SetupViewModel(
    application: Application,
    private val subId: Int,
) : AndroidViewModel(application) {
    private val moder = SubscriptionModer(application, subId)
    private val carrierModer = CarrierModer(application)

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(SetupState())
    val state: kotlinx.coroutines.flow.StateFlow<SetupState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.checks.isEmpty())
            val result = withContext(Dispatchers.IO) { runChecks() }
            _state.value = _state.value.copy(loading = false, checks = result.first, carrier = result.second, busyFix = null)
        }
    }

    fun applyFix(action: FixAction) {
        if (action == FixAction.NONE) return
        _state.value = _state.value.copy(busyFix = action)
        viewModelScope.launch {
            val note = withContext(Dispatchers.IO) {
                runCatching {
                    when (action) {
                        FixAction.ENABLE_VOLTE -> {
                            moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
                            moder.restartIMSRegistration()
                            "VoLTE switched on. IMS is re-registering."
                        }
                        FixAction.ENABLE_VONR -> {
                            moder.updateCarrierConfig(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, true)
                            moder.updateCarrierConfig(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, true)
                            moder.restartIMSRegistration()
                            "VoNR switched on."
                        }
                        FixAction.SET_NR_ARCHITECTURE -> {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                                intArrayOf(
                                    CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                                    CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA,
                                ),
                            )
                            "NR set to NSA and SA."
                        }
                        FixAction.INSTALL_MODEM_PATCH -> {
                            val status = PrivilegeManager.installRegionalModemPatch()
                            if (status.rebootRequired) {
                                "Patch installed. Reboot for the modem to pick it up."
                            } else {
                                status.message.ifBlank { "Patch step finished." }
                            }
                        }
                        FixAction.RESTART_IMS -> {
                            moder.restartIMSRegistration()
                            "IMS registration restarted."
                        }
                        else -> null
                    }
                }.getOrElse { "That did not apply: ${it.message ?: "the device refused it"}" }
            }
            _state.value = _state.value.copy(note = note)
            refresh()
        }
    }

    fun dismissNote() {
        _state.value = _state.value.copy(note = null)
    }

    private fun runChecks(): Pair<List<ReadinessCheck>, String> {
        val out = mutableListOf<ReadinessCheck>()
        val carrier = runCatching { moder.carrierName }.getOrNull().orEmpty()
        val rootMode = PrivilegeManager.activeMode == PrivilegeMode.ROOT

        val privileged = runCatching {
            (rootMode && PrivilegeManager.isRootReady()) || checkShizukuPermission(0) == ShizukuStatus.GRANTED
        }.getOrDefault(false)

        out += ReadinessCheck(
            id = "privilege",
            title = if (rootMode) "Root access" else "Shizuku access",
            detail = if (privileged) {
                if (rootMode) "Root service connected" else "Shizuku permission granted"
            } else {
                "Not connected. Nothing below can be read or changed without it."
            },
            status = if (privileged) CheckStatus.PASS else CheckStatus.FAIL,
        )
        if (!privileged) return out to carrier

        val supportsIms = runCatching { carrierModer.deviceSupportsIMS }.getOrDefault(false)
        out += ReadinessCheck(
            id = "ims",
            title = "Device supports IMS",
            detail = if (supportsIms) "Reported by the platform" else "This device does not report IMS support",
            status = if (supportsIms) CheckStatus.PASS else CheckStatus.FAIL,
        )

        val volte = runCatching { moder.isVoLteConfigEnabled }.getOrDefault(false)
        out += ReadinessCheck(
            id = "volte",
            title = "VoLTE enabled",
            detail = if (volte) "Carrier config allows VoLTE" else "5G attaches over an LTE anchor, so VoLTE has to be on first",
            status = if (volte) CheckStatus.PASS else CheckStatus.FAIL,
            fix = if (volte) FixAction.NONE else FixAction.ENABLE_VOLTE,
            fixLabel = if (volte) null else "Enable VoLTE",
        )

        val nrIndex = runCatching { moder.nrAvailabilityIndex }.getOrDefault(0)
        val nrLabel = when (nrIndex) {
            1 -> "NSA"
            2 -> "SA"
            3 -> "NSA and SA"
            else -> "off"
        }
        out += ReadinessCheck(
            id = "nr_mode",
            title = "NR architecture",
            detail = if (nrIndex == 0) "NR is switched off in carrier config" else "Set to $nrLabel",
            status = if (nrIndex == 0) CheckStatus.FAIL else CheckStatus.PASS,
            fix = if (nrIndex == 0) FixAction.SET_NR_ARCHITECTURE else FixAction.NONE,
            fixLabel = if (nrIndex == 0) "Set NSA + SA" else null,
        )

        val bands = runCatching { moder.getBandSelection() }.getOrNull()
        val nrLocked = bands?.nrBands?.isNotEmpty() == true
        out += ReadinessCheck(
            id = "bands",
            title = "Band selection",
            detail = when {
                bands == null -> "Could not read the current selection"
                !nrLocked -> "No NR lock — the modem may use any band it is given"
                else -> "Locked to ${bands.nrBands.joinToString(", ") { "n$it" }}"
            },
            status = when {
                bands == null -> CheckStatus.UNKNOWN
                else -> CheckStatus.PASS
            },
            fix = FixAction.OPEN_BANDS,
            fixLabel = "Choose bands",
            blocking = false,
        )

        if (VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val vonr = runCatching { moder.isVoNrConfigEnabled }.getOrDefault(false)
            out += ReadinessCheck(
                id = "vonr",
                title = "VoNR",
                detail = if (vonr) "Voice over 5G is on" else "Optional — only matters for voice on standalone 5G",
                status = if (vonr) CheckStatus.PASS else CheckStatus.WARN,
                fix = if (vonr) FixAction.NONE else FixAction.ENABLE_VONR,
                fixLabel = if (vonr) null else "Enable VoNR",
                blocking = false,
            )
        }

        if (rootMode) {
            val patch = runCatching { PrivilegeManager.getRegionalModemPatchStatus() }.getOrNull()
            out += ReadinessCheck(
                id = "patch",
                title = "Modem region patch",
                detail = when {
                    patch == null -> "Could not read the patch state"
                    !patch.supported -> "Not applicable to this device"
                    patch.removalPending -> "Removal is pending — reboot to finish"
                    patch.installed && patch.rebootRequired -> "Installed. Reboot for the modem to load it"
                    patch.installed -> "Installed"
                    !patch.magiskAvailable -> "Magisk was not found, so the systemless patch cannot be installed"
                    else -> "Not installed. This is the step that makes the modem accept EN-DC"
                },
                status = when {
                    patch == null -> CheckStatus.UNKNOWN
                    !patch.supported -> CheckStatus.WARN
                    patch.installed -> CheckStatus.PASS
                    else -> CheckStatus.FAIL
                },
                fix = if (patch != null && patch.supported && !patch.installed && patch.magiskAvailable) {
                    FixAction.INSTALL_MODEM_PATCH
                } else {
                    FixAction.NONE
                },
                fixLabel = if (patch != null && patch.supported && !patch.installed && patch.magiskAvailable) "Apply patch" else null,
            )
        } else {
            out += ReadinessCheck(
                id = "patch",
                title = "Modem region patch",
                detail = "Root only. Shizuku cannot reach the modem configuration, so 5G itself needs root.",
                status = CheckStatus.WARN,
                blocking = false,
            )
        }

        // The outcome, not a setting: is there actually an NR leg up right now.
        val radio = runCatching { moder.getRadioDiagnostics() }.getOrNull()
        val nrCell = radio?.cells?.firstOrNull { it.registered && it.type.contains("NR", ignoreCase = true) }
        out += ReadinessCheck(
            id = "endc",
            title = "5G connected",
            detail = when {
                radio == null -> "Could not read the radio"
                nrCell != null -> "Attached on ${nrCell.band} · ${nrCell.channel} · ${nrCell.rsrp ?: nrCell.dbm} dBm"
                else -> "No NR cell registered. If everything above passes, the modem is refusing EN-DC."
            },
            status = when {
                radio == null -> CheckStatus.UNKNOWN
                nrCell != null -> CheckStatus.PASS
                else -> CheckStatus.FAIL
            },
        )

        val ims = runCatching { moder.diagnoseIms() }.getOrNull()
        out += ReadinessCheck(
            id = "ims_reg",
            title = "IMS registered",
            detail = when {
                ims == null -> "Status unavailable"
                ims.registered -> "Calls will use VoLTE"
                else -> imsIssueText(ims.issue)
            },
            status = when {
                ims == null -> CheckStatus.UNKNOWN
                ims.registered -> CheckStatus.PASS
                else -> CheckStatus.WARN
            },
            fix = if (ims?.registered == false) FixAction.RESTART_IMS else FixAction.NONE,
            fixLabel = if (ims?.registered == false) "Restart IMS" else null,
            blocking = false,
        )

        return out to carrier
    }

    private fun imsIssueText(issue: SubscriptionModer.ImsIssue): String = when (issue) {
        SubscriptionModer.ImsIssue.NO_CELLULAR_SERVICE -> "No cellular service to register against"
        SubscriptionModer.ImsIssue.VOLTE_DISABLED_BY_CONFIG -> "VoLTE is disabled in carrier config"
        SubscriptionModer.ImsIssue.LTE_NR_NOT_ALLOWED -> "The radio is not allowed on LTE or NR"
        SubscriptionModer.ImsIssue.CARRIER_PROVISIONING_OR_NETWORK -> "The carrier has not provisioned this line, or the network refused"
        SubscriptionModer.ImsIssue.STATUS_UNAVAILABLE -> "The platform did not report a status"
        SubscriptionModer.ImsIssue.REGISTERED -> "Registered"
    }

    companion object {
        fun factory(
            application: Application,
            subId: Int,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SetupViewModel(application, subId) as T
            }
    }
}

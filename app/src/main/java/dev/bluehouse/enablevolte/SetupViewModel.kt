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
import kotlinx.coroutines.Job
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
    val checkedAt: Long = 0,
    val refreshing: Boolean = false,
) {
    val blocking = checks.filter { it.blocking }
    val passed = blocking.count { it.status == CheckStatus.PASS }
    val total = blocking.size
    val ready = total > 0 && passed == total

    /** The first thing standing in the way, which is the only one worth leading with. */
    val firstProblem: ReadinessCheck? = checks.firstOrNull { it.blocking && it.status == CheckStatus.FAIL }
}

/**
 * Reads setup settings and observed connection state for this SIM.
 *
 * Settings and connection evidence are separate: a missing NR link does not
 * establish that the modem or network rejected it. Fix actions address only
 * the setting each check actually observed.
 */
class SetupViewModel(
    application: Application,
    private val subId: Int,
) : AndroidViewModel(application) {
    private val moder = SubscriptionModer(application, subId)
    private val carrierModer = CarrierModer(application)

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(SetupState())
    val state: kotlinx.coroutines.flow.StateFlow<SetupState> = _state

    private var checkJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        // A newer refresh supersedes an older one rather than racing it to the state.
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.checks.isEmpty(), refreshing = true)
            val result = withContext(Dispatchers.IO) { runChecks() }
            _state.value = _state.value.copy(loading = false, checks = result.first, carrier = result.second,
                checkedAt = System.currentTimeMillis(), busyFix = null, refreshing = false)
        }
    }

    fun applyFix(action: FixAction) {
        if (action == FixAction.NONE || _state.value.busyFix != null) return
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
                        // The modem patch is never applied from here: it goes through
                        // RegionalPatchViewModel and its confirmation dialog.
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
            ReadinessRules.hasAccess(
                rootMode = rootMode,
                rootReady = rootMode && PrivilegeManager.isRootReady(),
                // Only asked in Shizuku mode: the check can raise a permission prompt.
                shizukuGranted = !rootMode && checkShizukuPermission(0) == ShizukuStatus.GRANTED,
            )
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
            detail = if (volte) "Carrier configuration allows LTE calling. IMS registration is checked separately." else "Carrier configuration disables LTE calling.",
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
            title = "5G mode",
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
                title = "VoNR setting",
                detail = if (vonr) "Carrier configuration allows voice over 5G; registration is checked separately." else "Optional — only matters for voice on standalone 5G",
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
                    patch.installed && patch.active -> "Patched carrier database mounted"
                    patch.installed -> patch.message.ifBlank { "Installed but inactive" }
                    !patch.magiskAvailable -> "No Magisk, KernelSU or APatch was found, so the systemless patch cannot be installed"
                    !patch.sourceAvailable -> "This firmware's modem database could not be read, so there is nothing to patch"
                    else -> "Not installed. This optional compatibility patch changes the modem carrier mapping; it does not guarantee 5G attachment."
                },
                status = ReadinessRules.patchStatus(patch),
                fix = if (patch != null && ReadinessRules.canInstallPatch(patch)) FixAction.INSTALL_MODEM_PATCH else FixAction.NONE,
                fixLabel = if (patch != null && ReadinessRules.canInstallPatch(patch)) "Apply patch" else null,
            )
        } else {
            out += ReadinessCheck(
                id = "patch",
                title = "Modem region patch",
                detail = "Installing this compatibility patch requires root. Shizuku can read and adjust supported Android settings.",
                status = CheckStatus.WARN,
                blocking = false,
            )
        }

        return out to carrier
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

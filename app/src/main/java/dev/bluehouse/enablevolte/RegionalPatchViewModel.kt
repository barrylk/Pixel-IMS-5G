package dev.bluehouse.enablevolte

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RegionalPatchUiState(
    val status: RegionalModemPatchStatus? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    /** The action waiting on the user's confirmation, if any. */
    val confirming: RegionalPatchAction? = null,
    /** What the last install or removal reported, kept until dismissed so a failure is never silent. */
    val outcome: String? = null,
)

enum class RegionalPatchAction { INSTALL, REMOVE }

/**
 * Owns device-wide patch work across navigation, rotation and appearance changes.
 *
 * The work used to live in a composable's coroutine scope with a saveable busy
 * flag, so rotating mid-install cancelled the coroutine but restored `busy =
 * true`, and the buttons stayed disabled until the page was left.
 */
class RegionalPatchViewModel : ViewModel() {
    private val _state = MutableStateFlow(RegionalPatchUiState())
    val state = _state.asStateFlow()

    private var work: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (work?.isActive == true) return
        work = viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.status == null)
            val status = withContext(Dispatchers.IO) { PrivilegeManager.getRegionalModemPatchStatus() }
            _state.value = _state.value.copy(status = status, loading = false)
        }
    }

    fun requestConfirmation(action: RegionalPatchAction) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(confirming = action)
    }

    fun dismissOutcome() {
        _state.value = _state.value.copy(outcome = null)
    }

    fun cancelConfirmation() {
        _state.value = _state.value.copy(confirming = null)
    }

    fun confirm() {
        val action = _state.value.confirming ?: return
        _state.value = _state.value.copy(confirming = null)
        apply(action)
    }

    private fun apply(action: RegionalPatchAction) {
        if (work?.isActive == true) return
        _state.value = _state.value.copy(busy = true)
        work = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                // Recheck both access and compatibility after the confirmation dialog.
                val current = PrivilegeManager.getRegionalModemPatchStatus()
                when {
                    action == RegionalPatchAction.INSTALL &&
                        (ReadinessRules.canInstallPatch(current) || ReadinessRules.canRevalidatePatch(current)) ->
                        PrivilegeManager.installRegionalModemPatch()
                    action == RegionalPatchAction.REMOVE && ReadinessRules.canRemovePatch(current) ->
                        PrivilegeManager.scheduleRegionalModemPatchRemoval()
                    else -> current
                }
            }
            _state.value = RegionalPatchUiState(status = result, loading = false, outcome = result.message.ifBlank { null })
        }
    }
}

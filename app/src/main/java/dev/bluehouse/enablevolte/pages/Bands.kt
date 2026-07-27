package dev.bluehouse.enablevolte.pages

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.PrivilegeManager
import dev.bluehouse.enablevolte.PrivilegeMode
import dev.bluehouse.enablevolte.RegionalModemPatchStatus
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.components.ClickablePropertyView
import dev.bluehouse.enablevolte.components.GlassSurface
import dev.bluehouse.enablevolte.components.HeaderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TENSOR_LTE_BANDS = intArrayOf(
    1, 2, 3, 4, 5, 7, 8, 12, 13, 14, 17, 18, 19, 20, 21, 25, 26, 28, 29, 30,
    32, 34, 38, 39, 40, 41, 42, 46, 48, 66, 71,
)
private val TENSOR_NR_BANDS = intArrayOf(
    1, 2, 3, 5, 7, 8, 12, 14, 20, 25, 26, 28, 29, 30, 38, 40, 41, 48, 66,
    70, 71, 75, 76, 77, 78, 79, 257, 258, 260, 261,
)

private const val BANDS_TAG = "Bands"

private fun IntArray.bandText(): String = joinToString(", ")

private fun bandFailureSummary(
    operation: String,
    error: Throwable,
): String {
    Log.w(BANDS_TAG, "$operation is unavailable", error)
    val detail = error.message
        ?.lineSequence()
        ?.firstOrNull()
        ?.take(120)
        ?.takeIf { it.isNotBlank() }
    return buildString {
        append(operation)
        append(": ")
        append(error.javaClass.simpleName.ifBlank { "Error" })
        if (detail != null) {
            append(" — ")
            append(detail)
        }
    }
}

@Composable
private fun RadioProfileChoice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.42f),
        onClick = if (enabled) onClick else null,
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            RadioButton(selected = selected, enabled = enabled, onClick = if (enabled) onClick else null)
            Text(label, modifier = Modifier.padding(top = 12.dp), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandPicker(
    prefix: String,
    catalogue: IntArray,
    selected: Set<Int>,
    detected: Set<Int>,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    val choices = (catalogue.toSet() + selected + detected).sorted()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { band ->
            val hasSignal = band in detected
            FilterChip(
                selected = band in selected,
                enabled = enabled,
                onClick = { onToggle(band) },
                label = { Text("$prefix$band") },
                leadingIcon = if (band in selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = if (hasSignal) Color(0xFF198754) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = if (hasSignal) Color.White else MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = if (hasSignal) Color(0xFF198754) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    selectedLabelColor = if (hasSignal) Color.White else MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = if (hasSignal) Color(0xFF198754).copy(alpha = 0.38f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.38f),
                    disabledLabelColor = if (hasSignal) Color.White.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                ),
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun Bands(
    subId: Int,
    navController: NavController,
) {
    val context = LocalContext.current
    val moder = remember(subId) { SubscriptionModer(context, subId) }
    val scope = rememberCoroutineScope()
    var selectedLte by remember { mutableStateOf(emptySet<Int>()) }
    var selectedNr by remember { mutableStateOf(emptySet<Int>()) }
    var detectedLte by remember { mutableStateOf(emptySet<Int>()) }
    var detectedNr by remember { mutableStateOf(emptySet<Int>()) }
    var currentSelection by rememberSaveable { mutableStateOf(context.getString(R.string.band_automatic)) }
    var radioMode by rememberSaveable { mutableStateOf(0) }
    var easyMode by rememberSaveable { mutableStateOf(false) }
    var easyModeBusy by rememberSaveable { mutableStateOf(false) }
    var caStatus by rememberSaveable { mutableStateOf(context.getString(R.string.checking)) }
    var servingBands by rememberSaveable { mutableStateOf(context.getString(R.string.checking)) }
    var visibleBands by rememberSaveable { mutableStateOf(context.getString(R.string.checking)) }
    var nrAttachStatus by rememberSaveable { mutableStateOf(context.getString(R.string.checking)) }
    var noServiceChange by rememberSaveable { mutableStateOf<String?>(null) }
    var rootForceReport by remember { mutableStateOf<SubscriptionModer.RootForceReport?>(null) }
    var rootForceBusy by rememberSaveable { mutableStateOf(false) }
    var shizukuRegionalBusy by rememberSaveable { mutableStateOf(false) }
    var shizukuRegionalResult by remember { mutableStateOf<SubscriptionModer.ShizukuRegionalResult?>(null) }
    var regionalPatch by remember { mutableStateOf<RegionalModemPatchStatus?>(null) }
    var regionalPatchBusy by rememberSaveable { mutableStateOf(false) }
    var regionalConfirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var bandLoadError by rememberSaveable { mutableStateOf<String?>(null) }

    fun loadSelection() {
        scope.launch {
            var loadStage = "Current band restriction"
            try {
                bandLoadError = null
                val selection = withContext(Dispatchers.IO) { moder.getBandSelection() }
                selectedLte = selection.lteBands.toSet()
                selectedNr = selection.nrBands.toSet()
                loadStage = "Radio profile"
                radioMode = withContext(Dispatchers.IO) { moder.radioModeIndex }
                loadStage = "Easy mode"
                easyMode = withContext(Dispatchers.IO) { moder.easyModeEnabled }
                val selectionText = if (selection.lteBands.isEmpty() && selection.nrBands.isEmpty()) {
                    context.getString(R.string.band_automatic)
                } else {
                    "LTE: ${selection.lteBands.bandText().ifEmpty { "-" }} | NR: ${selection.nrBands.bandText().ifEmpty { "-" }}"
                }
                currentSelection = when {
                    selection.modemReadbackAvailable -> selectionText
                    selection.knownSelection -> context.getString(R.string.band_cached_selection, selectionText)
                    else -> context.getString(R.string.band_readback_not_supported)
                }
                loadStage = "Tensor LTE CA"
                caStatus = when (withContext(Dispatchers.IO) { moder.getTensorLteCaEnabled() }) {
                    true -> context.getString(R.string.lte_ca_enabled)
                    false -> context.getString(R.string.lte_ca_disabled)
                    null -> context.getString(R.string.lte_ca_unavailable)
                }
                loadStage = "Radio diagnostics"
                val radio = withContext(Dispatchers.IO) { moder.getRadioDiagnostics() }
                detectedLte = radio.lteBands.toSet()
                detectedNr = radio.nrBands.toSet()
                servingBands = buildString {
                    append(radio.dataRat)
                    if (radio.servingLteBands.isNotEmpty()) append(" • LTE B${radio.servingLteBands.bandText()}")
                    if (radio.servingNrBands.isNotEmpty()) append(" • NR n${radio.servingNrBands.bandText()}")
                }
                visibleBands = context.getString(
                    R.string.visible_bands_value,
                    radio.lteBands.bandText().ifEmpty { context.getString(R.string.none_reported) },
                    radio.nrBands.bandText().ifEmpty { context.getString(R.string.none_reported) },
                )
                nrAttachStatus = when {
                    radio.dataRat == "NR" -> context.getString(R.string.sa_connected)
                    radio.endcAvailable == true -> context.getString(R.string.nsa_attach_available)
                    radio.nrAvailable == true -> context.getString(R.string.nr_advertised_no_endc)
                    radio.nrAvailable == false -> context.getString(R.string.nr_not_advertised)
                    else -> context.getString(R.string.status_unknown)
                }
                loadStage = "Root force report"
                rootForceReport = if (
                    PrivilegeManager.activeMode == PrivilegeMode.ROOT &&
                    PrivilegeManager.isRootReady()
                ) {
                    withContext(Dispatchers.IO) { moder.getRootForceReport(radio) }
                } else {
                    null
                }
                loadStage = "Regional modem patch"
                regionalPatch = if (
                    PrivilegeManager.activeMode == PrivilegeMode.ROOT &&
                    PrivilegeManager.isRootReady()
                ) {
                    withContext(Dispatchers.IO) { PrivilegeManager.getRegionalModemPatchStatus() }
                } else {
                    RegionalModemPatchStatus.unavailable(context.getString(R.string.regional_patch_root_only))
                }
                loadStage = "Service state"
                noServiceChange = withContext(Dispatchers.IO) {
                    if (!moder.hasCellularService()) moder.lastChangeDescription else null
                }
            } catch (error: Exception) {
                bandLoadError = bandFailureSummary(loadStage, error)
                currentSelection = context.getString(R.string.band_read_unavailable)
                caStatus = context.getString(R.string.lte_ca_unavailable)
                servingBands = context.getString(R.string.status_unknown)
                visibleBands = context.getString(R.string.status_unknown)
                nrAttachStatus = context.getString(R.string.status_unknown)
            }
        }
    }

    fun setProfile(index: Int) {
        scope.launch {
            val accepted = try {
                withContext(Dispatchers.IO) {
                    when (index) {
                        0 -> moder.requestAutomaticRadio()
                        1 -> moder.requestNsaOnly()
                        else -> moder.requestSaOnly()
                    }
                }
            } catch (error: Exception) {
                bandLoadError = bandFailureSummary("Radio profile change", error)
                false
            }
            Toast.makeText(context, if (accepted) R.string.nr_mode_requested else R.string.nr_mode_request_failed, Toast.LENGTH_LONG).show()
            loadSelection()
        }
    }

    fun applySelection(lte: IntArray, nr: IntArray) {
        scope.launch {
            val retained = try {
                withContext(Dispatchers.IO) { moder.setBandSelection(lte, nr) }
            } catch (error: Exception) {
                bandLoadError = bandFailureSummary("Band selection change", error)
                false
            }
            Toast.makeText(context, if (retained) R.string.band_applied else R.string.band_rejected, Toast.LENGTH_LONG).show()
            loadSelection()
        }
    }

    fun toggleEasyMode(enabled: Boolean) {
        if (easyModeBusy) return
        scope.launch {
            easyModeBusy = true
            val result = try {
                withContext(Dispatchers.IO) { moder.setEasyMode(enabled) }
            } catch (error: Exception) {
                bandLoadError = bandFailureSummary("Easy mode change", error)
                null
            }
            easyMode = enabled && result?.applied == true
            val message = when {
                !enabled -> R.string.easy_mode_advanced_unlocked
                result?.applied == true && result.caEnabled == true -> R.string.easy_mode_enabled
                result?.applied == true -> R.string.easy_mode_enabled_ca_unavailable
                else -> R.string.easy_mode_failed
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            easyModeBusy = false
            loadSelection()
        }
    }

    fun applyRootForce() {
        if (rootForceBusy) return
        scope.launch {
            rootForceBusy = true
            val result = withContext(Dispatchers.IO) { moder.applyRootForce() }
            val message = if (result.applied) {
                context.getString(R.string.root_force_applied)
            } else {
                context.getString(
                    R.string.root_force_partial,
                    result.failedGates.joinToString().ifEmpty { context.getString(R.string.status_unknown) },
                )
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            rootForceBusy = false
            loadSelection()
        }
    }

    fun restoreRootForce() {
        if (rootForceBusy) return
        scope.launch {
            rootForceBusy = true
            val restored = withContext(Dispatchers.IO) { moder.restoreRootForce() }
            Toast.makeText(
                context,
                if (restored) R.string.root_force_restored else R.string.root_force_restore_failed,
                Toast.LENGTH_LONG,
            ).show()
            rootForceBusy = false
            loadSelection()
        }
    }

    fun applyShizukuRegionalProfile() {
        if (shizukuRegionalBusy) return
        scope.launch {
            shizukuRegionalBusy = true
            shizukuRegionalResult = withContext(Dispatchers.IO) {
                runCatching { moder.applyShizukuRegionalCompatibility() }.getOrNull()
            }
            val result = shizukuRegionalResult
            val message = when {
                result == null -> context.getString(R.string.profile_failed)
                result.applied && result.limitations.isNotEmpty() ->
                    context.getString(R.string.shizuku_regional_applied_limited)
                result.applied -> context.getString(R.string.shizuku_regional_applied)
                else -> context.getString(
                    R.string.shizuku_regional_partial,
                    result.failedGates.joinToString().ifEmpty { context.getString(R.string.status_unknown) },
                )
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            shizukuRegionalBusy = false
            loadSelection()
        }
    }

    fun runRegionalPatchAction(action: String) {
        if (regionalPatchBusy) return
        scope.launch {
            regionalPatchBusy = true
            regionalPatch = withContext(Dispatchers.IO) {
                when (action) {
                    "install" -> PrivilegeManager.installRegionalModemPatch()
                    else -> PrivilegeManager.scheduleRegionalModemPatchRemoval()
                }
            }
            regionalPatchBusy = false
        }
    }

    LaunchedEffect(subId) { loadSelection() }

    regionalConfirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { regionalConfirmation = null },
            title = {
                Text(
                    stringResource(
                        if (action == "install") {
                            R.string.regional_patch_confirm_title
                        } else {
                            R.string.regional_patch_remove_confirm_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (action == "install") {
                            R.string.regional_patch_confirm_message
                        } else {
                            R.string.regional_patch_remove_confirm_message
                        },
                    ),
                )
            },
            confirmButton = {
                Button(onClick = {
                    regionalConfirmation = null
                    runRegionalPatchAction(action)
                }) {
                    Text(
                        stringResource(
                            if (action == "install") R.string.install_patch else R.string.schedule_removal,
                        ),
                    )
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { regionalConfirmation = null }) {
                    Text(stringResource(R.string.dismiss))
                }
            },
        )
    }

    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { navController.navigate("config/$subId") },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.sim_config))
            }
            Button(onClick = {}, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.bands))
            }
        }
        bandLoadError?.let { details ->
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.band_backend_limited_title),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.band_backend_limited_message))
                    Text(details, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { loadSelection() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        noServiceChange?.let { change ->
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.signal_lost_title), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.signal_lost_after_change, change))
                    Button(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { moder.undoLastChange() }
                            loadSelection()
                        }
                    }) { Text(stringResource(R.string.undo_last_change)) }
                }
            }
        }

        HeaderText(text = stringResource(R.string.easy_mode))
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            onClick = { toggleEasyMode(!easyMode) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.easy_volte_ca), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.easy_mode_description))
                }
                Switch(checked = easyMode, enabled = !easyModeBusy, onCheckedChange = ::toggleEasyMode)
            }
        }
        if (easyMode) Text(stringResource(R.string.easy_mode_locked_notice), color = MaterialTheme.colorScheme.primary)

        HeaderText(text = stringResource(R.string.radio_profile))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { RadioProfileChoice(stringResource(R.string.auto), radioMode == 0, !easyMode) { setProfile(0) } }
            Column(Modifier.weight(1f)) { RadioProfileChoice(stringResource(R.string.force_nsa), radioMode == 1, !easyMode) { setProfile(1) } }
            Column(Modifier.weight(1f)) { RadioProfileChoice(stringResource(R.string.force_sa), radioMode == 2, !easyMode) { setProfile(2) } }
        }

        ClickablePropertyView(label = stringResource(R.string.tensor_lte_ca), value = caStatus)
        ClickablePropertyView(label = stringResource(R.string.serving_radio), value = servingBands)
        ClickablePropertyView(label = stringResource(R.string.detected_bands), value = visibleBands)
        ClickablePropertyView(label = stringResource(R.string.nr_attach_status), value = nrAttachStatus)
        Text(text = stringResource(R.string.nr_force_limit))

        rootForceReport?.let { report ->
            HeaderText(text = stringResource(R.string.root_force_lab))
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(
                            if (report.active) R.string.root_force_active else R.string.root_force_inactive,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (report.active) Color(0xFF198754) else MaterialTheme.colorScheme.onSurface,
                    )
                    report.gates.forEach { gate ->
                        val value = when {
                            gate.mask == null -> stringResource(R.string.gate_unreadable)
                            gate.lteAllowed && gate.nrAllowed -> stringResource(R.string.gate_open)
                            else -> stringResource(R.string.gate_blocked)
                        }
                        ClickablePropertyView(label = gate.label, value = value)
                    }
                    ClickablePropertyView(
                        label = stringResource(R.string.carrier_nr_modes),
                        value = buildString {
                            if (report.carrierNsa) append("NSA")
                            if (report.carrierNsa && report.carrierSa) append(" + ")
                            if (report.carrierSa) append("SA")
                            if (!report.carrierNsa && !report.carrierSa) append(context.getString(R.string.gate_blocked))
                        },
                    )
                    Text(
                        text = stringResource(
                            when (report.verdict) {
                                SubscriptionModer.RootForceVerdict.NR_CONNECTED -> R.string.root_verdict_connected
                                SubscriptionModer.RootForceVerdict.NSA_AVAILABLE -> R.string.root_verdict_nsa_available
                                SubscriptionModer.RootForceVerdict.LOCAL_POLICY_BLOCKED -> R.string.root_verdict_local_block
                                SubscriptionModer.RootForceVerdict.WAITING_FOR_NR_CELL -> R.string.root_verdict_no_nr_cell
                                SubscriptionModer.RootForceVerdict.MODEM_OR_NETWORK_REJECTED -> R.string.root_verdict_rejected
                                SubscriptionModer.RootForceVerdict.UNKNOWN -> R.string.root_verdict_unknown
                            },
                        ),
                    )
                    Button(
                        enabled = !rootForceBusy,
                        onClick = ::applyRootForce,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.force_all_local_5g_gates))
                    }
                    if (report.active) {
                        OutlinedButton(
                            enabled = !rootForceBusy,
                            onClick = ::restoreRootForce,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.restore_root_force_snapshot))
                        }
                    }
                    Text(stringResource(R.string.root_force_warning))
                }
            }
        }

        if (PrivilegeManager.activeMode == PrivilegeMode.SHIZUKU) {
            HeaderText(text = stringResource(R.string.shizuku_regional_profile))
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.shizuku_regional_description))
                    shizukuRegionalResult?.let { result ->
                        ClickablePropertyView(
                            label = stringResource(R.string.carrier_nr_modes),
                            value = buildString {
                                if (result.report.carrierNsa) append("NSA")
                                if (result.report.carrierNsa && result.report.carrierSa) append(" + ")
                                if (result.report.carrierSa) append("SA")
                                if (!result.report.carrierNsa && !result.report.carrierSa) {
                                    append(context.getString(R.string.gate_blocked))
                                }
                            },
                        )
                        result.report.gates
                            .filter {
                                it.reason == android.telephony.TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER ||
                                    it.reason == android.telephony.TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER
                            }
                            .forEach { gate ->
                                ClickablePropertyView(
                                    label = gate.label,
                                    value = if (gate.lteAllowed && gate.nrAllowed) {
                                        stringResource(R.string.gate_open)
                                    } else {
                                        stringResource(R.string.gate_blocked)
                                    },
                                )
                            }
                        if (result.limitations.isNotEmpty()) {
                            ClickablePropertyView(
                                label = stringResource(R.string.modem_limitations),
                                value = result.limitations.joinToString(),
                            )
                        }
                    }
                    Button(
                        enabled = !shizukuRegionalBusy,
                        onClick = ::applyShizukuRegionalProfile,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (shizukuRegionalBusy) R.string.applying_profile else R.string.shizuku_regional_apply,
                            ),
                        )
                    }
                    Text(
                        stringResource(R.string.shizuku_modem_patch_limit),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (PrivilegeManager.activeMode == PrivilegeMode.ROOT) {
            HeaderText(text = stringResource(R.string.regional_modem_patch))
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.regional_patch_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                regionalPatch?.let { patch ->
                    ClickablePropertyView(
                        label = stringResource(R.string.patch_device),
                        value = patch.device.ifBlank { stringResource(R.string.status_unknown) },
                    )
                    ClickablePropertyView(
                        label = stringResource(R.string.patch_compatibility),
                        value = stringResource(
                            if (patch.supported && patch.magiskAvailable && patch.sourceAvailable) {
                                R.string.patch_compatible
                            } else {
                                R.string.patch_unavailable
                            },
                        ),
                    )
                    ClickablePropertyView(
                        label = stringResource(R.string.patch_status),
                        value = stringResource(
                            when {
                                patch.removalPending -> R.string.patch_removal_pending
                                patch.installed -> R.string.patch_installed
                                else -> R.string.patch_not_installed
                            },
                        ),
                    )
                    Text(patch.message)
                    if (patch.sourceSha256.isNotBlank()) {
                        ClickablePropertyView(
                            label = stringResource(R.string.stock_database_hash),
                            value = patch.sourceSha256.take(16) + "…",
                        )
                    }
                    if (patch.patchedSha256.isNotBlank()) {
                        ClickablePropertyView(
                            label = stringResource(R.string.patched_database_hash),
                            value = patch.patchedSha256.take(16) + "…",
                        )
                    }
                    if (!patch.installed) {
                        Button(
                            enabled = !regionalPatchBusy &&
                                patch.supported &&
                                patch.magiskAvailable &&
                                patch.sourceAvailable,
                            onClick = { regionalConfirmation = "install" },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if (regionalPatchBusy) R.string.applying_profile else R.string.install_patch,
                                ),
                            )
                        }
                    } else {
                        if (patch.rebootRequired && !patch.removalPending) {
                            Button(
                                enabled = !regionalPatchBusy &&
                                    patch.supported &&
                                    patch.magiskAvailable &&
                                    patch.sourceAvailable,
                                onClick = { regionalConfirmation = "install" },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.reinstall_patch))
                            }
                        }
                        if (!patch.removalPending) {
                            OutlinedButton(
                                enabled = !regionalPatchBusy,
                                onClick = { regionalConfirmation = "remove" },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.remove_patch))
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.regional_patch_warning),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            }
        }

        HeaderText(text = stringResource(R.string.band_selection))
        RadioProfileChoice(
            label = stringResource(R.string.automatic_band_selection),
            selected = selectedLte.isEmpty() && selectedNr.isEmpty(),
            enabled = !easyMode,
            onClick = { applySelection(intArrayOf(), intArrayOf()) },
        )
        ClickablePropertyView(label = stringResource(R.string.current_bands), value = currentSelection)
        Text(stringResource(R.string.band_color_legend))

        HeaderText(text = stringResource(R.string.lte_bands))
        BandPicker("B", TENSOR_LTE_BANDS, selectedLte, detectedLte, !easyMode) { band ->
            selectedLte = if (band in selectedLte) selectedLte - band else selectedLte + band
        }
        Text(stringResource(R.string.selected_bands, selectedLte.sorted().joinToString(", ").ifEmpty { context.getString(R.string.none_reported) }))

        HeaderText(text = stringResource(R.string.nr_bands))
        BandPicker("n", TENSOR_NR_BANDS, selectedNr, detectedNr, !easyMode) { band ->
            selectedNr = if (band in selectedNr) selectedNr - band else selectedNr + band
        }
        Text(stringResource(R.string.selected_bands, selectedNr.sorted().joinToString(", ").ifEmpty { context.getString(R.string.none_reported) }))

        Text(text = stringResource(R.string.band_warning))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = !easyMode,
                onClick = { applySelection(selectedLte.sorted().toIntArray(), selectedNr.sorted().toIntArray()) },
            ) {
                Text(stringResource(R.string.apply_bands))
            }
            OutlinedButton(onClick = { loadSelection() }) { Text(stringResource(R.string.refresh)) }
        }
    }
}

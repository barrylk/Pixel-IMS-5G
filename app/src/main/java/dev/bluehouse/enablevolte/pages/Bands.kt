package dev.bluehouse.enablevolte.pages

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
import dev.bluehouse.enablevolte.R
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

private fun IntArray.bandText(): String = joinToString(", ")

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
fun Bands(subId: Int) {
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

    fun loadSelection() {
        scope.launch {
            val selection = withContext(Dispatchers.IO) { moder.getBandSelection() }
            selectedLte = selection.lteBands.toSet()
            selectedNr = selection.nrBands.toSet()
            radioMode = withContext(Dispatchers.IO) { moder.radioModeIndex }
            easyMode = withContext(Dispatchers.IO) { moder.easyModeEnabled }
            currentSelection = if (selection.lteBands.isEmpty() && selection.nrBands.isEmpty()) {
                context.getString(R.string.band_automatic)
            } else {
                "LTE: ${selection.lteBands.bandText().ifEmpty { "-" }} | NR: ${selection.nrBands.bandText().ifEmpty { "-" }}"
            }
            caStatus = when (withContext(Dispatchers.IO) { moder.getTensorLteCaEnabled() }) {
                true -> context.getString(R.string.lte_ca_enabled)
                false -> context.getString(R.string.lte_ca_disabled)
                null -> context.getString(R.string.lte_ca_unavailable)
            }
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
            noServiceChange = withContext(Dispatchers.IO) {
                if (!moder.hasCellularService()) moder.lastChangeDescription else null
            }
        }
    }

    fun setProfile(index: Int) {
        scope.launch {
            val accepted = withContext(Dispatchers.IO) {
                when (index) {
                    0 -> moder.requestAutomaticRadio()
                    1 -> moder.requestNsaOnly()
                    else -> moder.requestSaOnly()
                }
            }
            Toast.makeText(context, if (accepted) R.string.nr_mode_requested else R.string.nr_mode_request_failed, Toast.LENGTH_LONG).show()
            loadSelection()
        }
    }

    fun applySelection(lte: IntArray, nr: IntArray) {
        scope.launch {
            val retained = withContext(Dispatchers.IO) { moder.setBandSelection(lte, nr) }
            Toast.makeText(context, if (retained) R.string.band_applied else R.string.band_rejected, Toast.LENGTH_LONG).show()
            loadSelection()
        }
    }

    fun toggleEasyMode(enabled: Boolean) {
        if (easyModeBusy) return
        scope.launch {
            easyModeBusy = true
            val result = withContext(Dispatchers.IO) { moder.setEasyMode(enabled) }
            easyMode = enabled && result.applied
            val message = when {
                !enabled -> R.string.easy_mode_advanced_unlocked
                result.applied && result.caEnabled == true -> R.string.easy_mode_enabled
                result.applied -> R.string.easy_mode_enabled_ca_unavailable
                else -> R.string.easy_mode_failed
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            easyModeBusy = false
            loadSelection()
        }
    }

    LaunchedEffect(subId) { loadSelection() }

    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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

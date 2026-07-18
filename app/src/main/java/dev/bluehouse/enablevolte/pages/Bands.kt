package dev.bluehouse.enablevolte.pages

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.components.ClickablePropertyView
import dev.bluehouse.enablevolte.components.HeaderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun parseBands(value: String): IntArray? {
    if (value.isBlank()) return intArrayOf()
    return try {
        value.split(",")
            .map { it.trim().toInt() }
            .also { bands -> require(bands.all { it > 0 }) }
            .distinct()
            .sorted()
            .toIntArray()
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun IntArray.bandText(): String = joinToString(", ")

@Suppress("ktlint:standard:function-naming")
@Composable
fun Bands(subId: Int) {
    val context = LocalContext.current
    val moder = SubscriptionModer(context, subId)
    val scope = rememberCoroutineScope()
    var lteText by rememberSaveable { mutableStateOf("") }
    var nrText by rememberSaveable { mutableStateOf("") }
    var currentSelection by rememberSaveable { mutableStateOf(context.getString(R.string.band_automatic)) }
    var caStatus by rememberSaveable { mutableStateOf(context.getString(R.string.checking)) }
    var servingBands by rememberSaveable { mutableStateOf(context.getString(R.string.checking)) }
    var visibleBands by rememberSaveable { mutableStateOf(context.getString(R.string.checking)) }
    var nrAttachStatus by rememberSaveable { mutableStateOf(context.getString(R.string.checking)) }

    fun loadSelection() {
        scope.launch {
            val selection = withContext(Dispatchers.IO) { moder.getBandSelection() }
            lteText = selection.lteBands.bandText()
            nrText = selection.nrBands.bandText()
            currentSelection =
                if (selection.lteBands.isEmpty() && selection.nrBands.isEmpty()) {
                    context.getString(R.string.band_automatic)
                } else {
                    "LTE: ${selection.lteBands.bandText().ifEmpty { "—" }} | NR: ${selection.nrBands.bandText().ifEmpty { "—" }}"
                }
            caStatus = when (withContext(Dispatchers.IO) { moder.getTensorLteCaEnabled() }) {
                true -> context.getString(R.string.lte_ca_enabled)
                false -> context.getString(R.string.lte_ca_disabled)
                null -> context.getString(R.string.lte_ca_unavailable)
            }
            val radio = withContext(Dispatchers.IO) { moder.getRadioDiagnostics() }
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
        }
    }

    fun requestNrMode(nsa: Boolean) {
        scope.launch {
            val accepted = withContext(Dispatchers.IO) {
                if (nsa) moder.requestNsaOnly() else moder.requestSaOnly()
            }
            Toast.makeText(
                context,
                if (accepted) R.string.nr_mode_requested else R.string.nr_mode_request_failed,
                Toast.LENGTH_LONG,
            ).show()
            loadSelection()
        }
    }

    fun applySelection(lte: IntArray, nr: IntArray) {
        scope.launch {
            val retained = withContext(Dispatchers.IO) { moder.setBandSelection(lte, nr) }
            Toast.makeText(
                context,
                if (retained) R.string.band_applied else R.string.band_rejected,
                Toast.LENGTH_LONG,
            ).show()
            loadSelection()
        }
    }

    LaunchedEffect(subId) { loadSelection() }

    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderText(text = stringResource(R.string.band_selection))
        ClickablePropertyView(label = stringResource(R.string.current_bands), value = currentSelection)
        ClickablePropertyView(label = stringResource(R.string.tensor_lte_ca), value = caStatus)
        ClickablePropertyView(label = stringResource(R.string.serving_radio), value = servingBands)
        ClickablePropertyView(label = stringResource(R.string.detected_bands), value = visibleBands)
        ClickablePropertyView(label = stringResource(R.string.nr_attach_status), value = nrAttachStatus)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { requestNrMode(true) }) {
                Text(stringResource(R.string.request_nsa))
            }
            OutlinedButton(onClick = { requestNrMode(false) }) {
                Text(stringResource(R.string.request_sa_only))
            }
        }
        Text(text = stringResource(R.string.nr_force_limit))
        Text(text = stringResource(R.string.band_warning))
        OutlinedTextField(
            value = lteText,
            onValueChange = { lteText = it },
            label = { Text(stringResource(R.string.lte_bands)) },
            placeholder = { Text(stringResource(R.string.band_list_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = nrText,
            onValueChange = { nrText = it },
            label = { Text(stringResource(R.string.nr_bands)) },
            placeholder = { Text(stringResource(R.string.band_list_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        ClickablePropertyView(
            label = stringResource(R.string.lte_ca_example),
            value = stringResource(R.string.lte_ca_example_value),
        ) {
            lteText = "1, 3, 7, 8, 20, 28, 38, 40, 41"
            nrText = ""
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val lte = parseBands(lteText)
                val nr = parseBands(nrText)
                if (lte == null || nr == null) {
                    Toast.makeText(context, R.string.band_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    applySelection(lte, nr)
                }
            }) {
                Text(stringResource(R.string.apply_bands))
            }
            OutlinedButton(onClick = {
                lteText = ""
                nrText = ""
                applySelection(intArrayOf(), intArrayOf())
            }) {
                Text(stringResource(R.string.reset_bands))
            }
        }
    }
}

package dev.bluehouse.enablevolte.pages

import android.telephony.SubscriptionInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.components.BottomBarSpacer
import dev.bluehouse.enablevolte.components.GlassIconButton
import dev.bluehouse.enablevolte.components.NoteBox
import dev.bluehouse.enablevolte.components.PageTitle
import dev.bluehouse.enablevolte.components.SimPicker
import dev.bluehouse.enablevolte.components.StatusTone
import dev.bluehouse.enablevolte.uniqueName

/**
 * The Controls tab: a SIM picker at the top and that SIM's carrier settings inline,
 * grouped as Calling, Network, Indicators and Advanced. The per-SIM page
 * (`config/{subId}`) still exists for links from Home.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun ControlsHub(
    subscriptions: List<SubscriptionInfo>,
    navController: NavController,
) {
    var selectedId by rememberSaveable { mutableIntStateOf(-1) }
    val selected = subscriptions.firstOrNull { it.subscriptionId == selectedId } ?: subscriptions.firstOrNull()
    val title = stringResource(R.string.controls)

    if (selected == null) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageTitle(title)
            NoteBox(stringResource(R.string.controls_no_sim), StatusTone.DANGER)
            NoteBox(stringResource(R.string.controls_safety_note), StatusTone.WARNING)
            BottomBarSpacer()
        }
        return
    }

    Config(navController, selected.subscriptionId) { onRefresh ->
        PageTitle(title) {
            GlassIconButton(Icons.Filled.Refresh, "Refresh", onRefresh)
        }
        SimPicker(
            items = subscriptions.map { it.subscriptionId to it.uniqueName },
            selectedId = selected.subscriptionId,
            onSelect = { selectedId = it },
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
    }
}

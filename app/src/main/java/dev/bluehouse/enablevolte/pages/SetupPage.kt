package dev.bluehouse.enablevolte.pages

import android.app.Application
import android.telephony.SubscriptionInfo
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.*
import dev.bluehouse.enablevolte.components.RegionalPatchCard

/** Opening page: the same reading surfaces and glass controls as the live monitor. */
@Composable
fun SetupPage(subscriptions: List<SubscriptionInfo>, navController: NavController, rootGranted: Boolean) {
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableIntStateOf(-1) }
    val subscription = subscriptions.firstOrNull { it.subscriptionId == selectedId } ?: subscriptions.firstOrNull()
    val patchVm: RegionalPatchViewModel? = if (rootGranted) viewModel(key = "regional-patch") else null
    val patchUi = patchVm?.state?.collectAsState()?.value
    val setupVm: SetupViewModel? = subscription?.let {
        viewModel(key = "setup-${it.subscriptionId}",
            factory = SetupViewModel.factory(context.applicationContext as Application, it.subscriptionId))
    }
    val ui = setupVm?.state?.collectAsState()?.value ?: SetupState(loading = false)
    var seenPatch by remember(subscription?.subscriptionId) { mutableStateOf<RegionalModemPatchStatus?>(null) }
    LaunchedEffect(patchUi?.status) {
        val current = patchUi?.status ?: return@LaunchedEffect
        if (seenPatch != null && seenPatch != current) setupVm?.refresh()
        seenPatch = current
    }
    HomeDashboard(
        sims = subscriptions.map { HomeSim(it.subscriptionId, it.displayName?.toString()?.takeIf { it.isNotBlank() }
            ?: liveCarrierName(it.carrierName.toString(), "${it.mccString}${it.mncString}", "Mobile network"), it.simSlotIndex + 1) },
        selectedId = subscription?.subscriptionId,
        ui = ui,
        accessMode = if (PrivilegeManager.activeMode == PrivilegeMode.ROOT) "Root" else "Shizuku",
        onSelectSim = { selectedId = it },
        onRefresh = { setupVm?.refresh(); patchVm?.refresh() },
        onNavigate = { navController.navigate(it) },
        onDismissNote = { setupVm?.dismissNote() },
        onFix = { check ->
            when (check.fix) {
                FixAction.OPEN_BANDS -> navController.navigate("bands/${subscription?.subscriptionId}")
                FixAction.OPEN_EXPERT -> navController.navigate("config/${subscription?.subscriptionId}")
                FixAction.INSTALL_MODEM_PATCH -> patchVm?.requestConfirmation(RegionalPatchAction.INSTALL)
                else -> setupVm?.applyFix(check.fix)
            }
        },
        patchContent = patchVm?.let { vm ->
            { RegionalPatchCard(vm, compactWhenActive = false, liquidStyle = true, detailsAlwaysVisible = true) }
        },
    )
}

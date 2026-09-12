package dev.bluehouse.enablevolte.pages

import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.BooleanControl
import dev.bluehouse.enablevolte.ConfigControls
import dev.bluehouse.enablevolte.ConfigUiState
import dev.bluehouse.enablevolte.ConfigViewModel
import dev.bluehouse.enablevolte.PrivilegeManager
import dev.bluehouse.enablevolte.PrivilegeMode
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.components.BooleanPropertyView
import dev.bluehouse.enablevolte.components.ClickablePropertyView
import dev.bluehouse.enablevolte.components.Panel
import dev.bluehouse.enablevolte.components.HeaderText
import dev.bluehouse.enablevolte.components.InfiniteLoadingDialog
import dev.bluehouse.enablevolte.components.PanelGroup
import dev.bluehouse.enablevolte.components.RowDivider
import dev.bluehouse.enablevolte.components.RadioSelectPropertyView
import dev.bluehouse.enablevolte.components.UserAgentPropertyView

/**
 * A run of related rows, drawn as one panel with hairlines between them.
 *
 * Rows are passed as a list rather than as a content block so that controls the
 * running Android version cannot support can be dropped before composing —
 * otherwise the dividers would not know which row is first.
 */
@Composable
private fun SectionPanel(
    title: String,
    rows: List<@Composable () -> Unit>,
) {
    if (rows.isEmpty()) return
    HeaderText(text = title)
    PanelGroup {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                RowDivider()
            }
            row()
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun Config(
    navController: NavController,
    subId: Int,
) {
    val context = LocalContext.current
    val viewModel: ConfigViewModel =
        viewModel(
            key = "config-$subId",
            factory = ConfigViewModel.factory(context.applicationContext as Application, subId),
        )
    val ui by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val carrierName = viewModel.carrierName

    if (ui.loading) {
        InfiniteLoadingDialog()
        return
    }

    // Builds one toggle row, or nothing when this Android version has no such
    // setting, so a section can drop it before the dividers are placed.
    val toggleRow: (String, BooleanControl) -> (@Composable () -> Unit)? = { label, control ->
        val state = ui.booleans[control.id]
        if (state == null) {
            null
        } else {
            {
                BooleanPropertyView(
                    label = label,
                    toggled = state.value ?: false,
                    minSdk = control.minSdk,
                    busy = state.isBusy,
                    error = if (state.hasFailed) state.message else null,
                ) { requested -> viewModel.toggle(control, requested) }
            }
        }
    }

    val nrLabels = arrayOf(
        stringResource(R.string.nr_off),
        stringResource(R.string.nr_nsa),
        stringResource(R.string.nr_sa),
        stringResource(R.string.nr_nsa_sa),
    )
    val radioLabels = arrayOf(
        stringResource(R.string.radio_default),
        stringResource(R.string.radio_5g_preferred),
        stringResource(R.string.radio_nr_only),
    )
    val spnLabels = arrayOf(
        "%s".format(carrierName),
        "%s Wi-Fi Calling".format(carrierName),
        "WLAN Call",
        "%s WLAN Call".format(carrierName),
        "%s Wi-Fi".format(carrierName),
        "WiFi Calling | %s".format(carrierName),
        "%s VoWifi".format(carrierName),
        "Wi-Fi Calling",
        "Wi-Fi",
        "WiFi Calling",
        "VoWifi",
        "%s WiFi Calling".format(carrierName),
        "WiFi Call",
    )

    val nrLabel = stringResource(R.string.nr_architecture)
    val radioModeLabel = stringResource(R.string.radio_mode)
    val radioWarning = stringResource(R.string.radio_warning)
    val spnLabel = stringResource(R.string.wi_fi_calling_carrier_name_format)
    val userAgentLabel = stringResource(R.string.user_agent)

    val networkRows = listOfNotNull<@Composable () -> Unit>(
        {
            RadioSelectPropertyView(
                label = nrLabel,
                values = nrLabels,
                selectedIndex = ui.nrAvailability.value ?: 0,
                busy = ui.nrAvailability.isBusy,
                error = if (ui.nrAvailability.hasFailed) ui.nrAvailability.message else null,
            ) { viewModel.setNrAvailability(it) }
        },
        {
            RadioSelectPropertyView(
                label = radioModeLabel,
                values = radioLabels,
                selectedIndex = ui.radioMode.value ?: 0,
                busy = ui.radioMode.isBusy,
                error = if (ui.radioMode.hasFailed) ui.radioMode.message else null,
            ) { viewModel.setRadioMode(it) }
        },
        { ClickablePropertyView(label = radioWarning, value = "") },
        toggleRow(stringResource(R.string.enable_enhanced_4g_lte_plus), ConfigControls.FOUR_G_PLUS),
        toggleRow(stringResource(R.string.allow_adding_apns), ConfigControls.ALLOW_ADDING_APNS),
    )

    val callingRows = listOfNotNull<@Composable () -> Unit>(
        toggleRow(stringResource(R.string.enable_volte), ConfigControls.VOLTE),
        toggleRow(stringResource(R.string.enable_vonr), ConfigControls.VONR),
        toggleRow(stringResource(R.string.enable_crosssim), ConfigControls.CROSS_SIM),
        toggleRow(stringResource(R.string.enable_vowifi), ConfigControls.VOWIFI),
        toggleRow(stringResource(R.string.enable_vowifi_while_roamed), ConfigControls.VOWIFI_ROAMING),
        toggleRow(stringResource(R.string.enable_video_calling_vt), ConfigControls.VIDEO_CALLING),
        toggleRow(stringResource(R.string.enable_ss_over_ut), ConfigControls.SS_OVER_UT),
        toggleRow(stringResource(R.string.enable_ss_over_cdma), ConfigControls.SS_OVER_CDMA),
        toggleRow(stringResource(R.string.show_wifi_only_for_vowifi), ConfigControls.WFC_WIFI_ONLY),
        toggleRow(stringResource(R.string.show_vowifi_preference_in_settings), ConfigControls.SHOW_VOWIFI_MODE),
        toggleRow(
            stringResource(R.string.show_vowifi_roaming_preference_in_settings),
            ConfigControls.SHOW_VOWIFI_ROAMING_MODE,
        ),
        {
            UserAgentPropertyView(label = userAgentLabel, value = ui.userAgent.value) { viewModel.setUserAgent(it) }
        },
        {
            RadioSelectPropertyView(
                label = spnLabel,
                values = spnLabels,
                selectedIndex = ui.wfcSpnFormat.value ?: 0,
                busy = ui.wfcSpnFormat.isBusy,
                error = if (ui.wfcSpnFormat.hasFailed) ui.wfcSpnFormat.message else null,
            ) { viewModel.setWfcSpnFormat(it) }
        },
    )

    val indicatorRows = listOfNotNull<@Composable () -> Unit>(
        toggleRow(stringResource(R.string.show_vowifi_icon), ConfigControls.SHOW_VOWIFI_ICON),
        toggleRow(stringResource(R.string.always_show_data_icon), ConfigControls.ALWAYS_SHOW_DATA_ICON),
        toggleRow(stringResource(R.string.show_4g_for_lte_data_icon), ConfigControls.SHOW_4G_FOR_LTE),
        toggleRow(stringResource(R.string.hide_enhanced_data_icon), ConfigControls.HIDE_ENHANCED_DATA_ICON),
        toggleRow(stringResource(R.string.show_ims_status_in_sim_status), ConfigControls.SHOW_IMS_IN_SIM_INFO),
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(scrollState)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = {}, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.sim_config))
            }
            OutlinedButton(onClick = { navController.navigate("bands/$subId") }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.bands))
            }
        }
        Text(
            text = stringResource(
                if (PrivilegeManager.activeMode == PrivilegeMode.ROOT) {
                    R.string.root_sim_config_persistence
                } else {
                    R.string.shizuku_sim_config_persistence
                },
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp),
        )

        SectionPanel(title = stringResource(R.string.config_section_network), rows = networkRows)
        SectionPanel(title = stringResource(R.string.config_section_calling), rows = callingRows)

        if (PrivilegeManager.activeMode == PrivilegeMode.ROOT) {
            HeaderText(text = stringResource(R.string.root_vowifi_repair_title))
            RootVoWifiPanel(ui = ui, viewModel = viewModel)
        }

        SectionPanel(title = stringResource(R.string.config_section_indicators), rows = indicatorRows)

        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            val statusBarManager: StatusBarManager = context.getSystemService(StatusBarManager::class.java)
            val simSlotIndex = ui.simSlotIndex
            val statusTileLabel = stringResource(R.string.add_status_tile)
            val toggleTileLabel = stringResource(R.string.add_toggle_tile)

            SectionPanel(
                title = stringResource(R.string.qstile),
                rows = listOf(
                    {
                        ClickablePropertyView(label = statusTileLabel, value = "") {
                            statusBarManager.requestAddTileService(
                                ComponentName(
                                    context,
                                    // TODO: what happens if someone tries to use this feature from a triple(or even dual)-SIM phone?
                                    Class.forName("dev.bluehouse.enablevolte.SIM${simSlotIndex + 1}IMSStatusQSTileService"),
                                ),
                                context.getString(R.string.qs_status_tile_title, (simSlotIndex + 1).toString()),
                                Icon.createWithResource(context, R.drawable.ic_launcher_foreground),
                                {},
                                {},
                            )
                        }
                    },
                    {
                        ClickablePropertyView(label = toggleTileLabel, value = "") {
                            statusBarManager.requestAddTileService(
                                ComponentName(
                                    context,
                                    Class.forName("dev.bluehouse.enablevolte.SIM${simSlotIndex + 1}VoLTEConfigToggleQSTileService"),
                                ),
                                context.getString(R.string.qs_toggle_tile_title, (simSlotIndex + 1).toString()),
                                Icon.createWithResource(context, R.drawable.ic_launcher_foreground),
                                {},
                                {},
                            )
                        }
                    },
                ),
            )
        }

        val resetLabel = stringResource(R.string.reset_all_settings)
        val resetValue = stringResource(R.string.reverts_to_carrier_default)
        val expertLabel = stringResource(R.string.expert_mode)
        val dumpLabel = stringResource(R.string.dump_config)
        val restartLabel = stringResource(R.string.restart_ims_registration)

        SectionPanel(
            title = stringResource(R.string.miscellaneous),
            rows = listOf(
                { ClickablePropertyView(label = resetLabel, value = resetValue) { viewModel.resetAll() } },
                { ClickablePropertyView(label = expertLabel, value = "") { navController.navigate("config/$subId/edit") } },
                { ClickablePropertyView(label = dumpLabel, value = "") { navController.navigate("config/$subId/dump") } },
                { ClickablePropertyView(label = restartLabel, value = "") { viewModel.restartIms() } },
            ),
        )

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun RootVoWifiPanel(
    ui: ConfigUiState,
    viewModel: ConfigViewModel,
) {
    val status = ui.rootVoWifi
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = if (status?.isVoWifiActive == true) {
                    stringResource(R.string.root_vowifi_active)
                } else {
                    stringResource(R.string.root_vowifi_not_active)
                },
                color = if (status?.isVoWifiActive == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (status == null) {
                Text(stringResource(R.string.root_vowifi_reading))
            } else {
                Text(
                    stringResource(
                        R.string.root_vowifi_status,
                        if (status.settingEnabled) "Enabled" else "Disabled",
                        status.modeLabel,
                        status.registrationLabel,
                        status.transportLabel,
                        if (status.wifiState == 3) "On" else "Off",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (status.message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        status.message,
                        color = if (status.operationSucceeded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (status.failureReason.isNotBlank() && !status.isVoWifiActive) {
                    Spacer(Modifier.height(8.dp))
                    Text(status.failureReason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.root_vowifi_limit),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            if (ui.rootVoWifiBusy) {
                CircularProgressIndicator()
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.applyRootVoWifiRepair() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.root_vowifi_apply))
                    }
                    OutlinedButton(onClick = { viewModel.refreshRootVoWifi() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.refresh))
                    }
                }
                if (status?.snapshotAvailable == true) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.restoreRootVoWifiRepair() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.root_vowifi_restore))
                    }
                }
            }
        }
    }
}

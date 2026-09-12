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
import androidx.compose.ui.unit.Dp
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
import dev.bluehouse.enablevolte.components.GlassSurface
import dev.bluehouse.enablevolte.components.HeaderText
import dev.bluehouse.enablevolte.components.InfiniteLoadingDialog
import dev.bluehouse.enablevolte.components.RadioSelectPropertyView
import dev.bluehouse.enablevolte.components.UserAgentPropertyView

/**
 * One toggle, wired to the control state the view model holds for it.
 *
 * Controls the running Android version cannot support are not loaded, so an
 * absent entry means "not applicable here" and the row is simply not drawn.
 */
@Composable
private fun BooleanControlView(
    label: String,
    control: BooleanControl,
    ui: ConfigUiState,
    onToggle: (BooleanControl, Boolean) -> Unit,
) {
    val state = ui.booleans[control.id] ?: return
    BooleanPropertyView(
        label = label,
        toggled = state.value ?: false,
        minSdk = control.minSdk,
        busy = state.isBusy,
        error = if (state.hasFailed) state.message else null,
    ) { requested -> onToggle(control, requested) }
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
    val onToggle: (BooleanControl, Boolean) -> Unit = { control, requested -> viewModel.toggle(control, requested) }

    if (ui.loading) {
        InfiniteLoadingDialog()
        return
    }

    Column(modifier = Modifier.padding(Dp(16f)).verticalScroll(scrollState)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.sim_config))
            }
            OutlinedButton(
                onClick = { navController.navigate("bands/$subId") },
                modifier = Modifier.weight(1f),
            ) {
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
            color = if (PrivilegeManager.activeMode == PrivilegeMode.ROOT) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp),
        )

        HeaderText(text = stringResource(R.string.config_section_network))
        RadioSelectPropertyView(
            label = stringResource(R.string.nr_architecture),
            values = arrayOf(
                stringResource(R.string.nr_off),
                stringResource(R.string.nr_nsa),
                stringResource(R.string.nr_sa),
                stringResource(R.string.nr_nsa_sa),
            ),
            selectedIndex = ui.nrAvailability.value ?: 0,
            busy = ui.nrAvailability.isBusy,
            error = if (ui.nrAvailability.hasFailed) ui.nrAvailability.message else null,
        ) { viewModel.setNrAvailability(it) }

        RadioSelectPropertyView(
            label = stringResource(R.string.radio_mode),
            values = arrayOf(
                stringResource(R.string.radio_default),
                stringResource(R.string.radio_5g_preferred),
                stringResource(R.string.radio_nr_only),
            ),
            selectedIndex = ui.radioMode.value ?: 0,
            busy = ui.radioMode.isBusy,
            error = if (ui.radioMode.hasFailed) ui.radioMode.message else null,
        ) { viewModel.setRadioMode(it) }

        ClickablePropertyView(label = stringResource(R.string.radio_warning), value = "")
        BooleanControlView(stringResource(R.string.enable_enhanced_4g_lte_plus), ConfigControls.FOUR_G_PLUS, ui, onToggle)
        BooleanControlView(stringResource(R.string.allow_adding_apns), ConfigControls.ALLOW_ADDING_APNS, ui, onToggle)

        HeaderText(text = stringResource(R.string.config_section_calling))
        BooleanControlView(stringResource(R.string.enable_volte), ConfigControls.VOLTE, ui, onToggle)
        BooleanControlView(stringResource(R.string.enable_vonr), ConfigControls.VONR, ui, onToggle)
        BooleanControlView(stringResource(R.string.enable_crosssim), ConfigControls.CROSS_SIM, ui, onToggle)
        BooleanControlView(stringResource(R.string.enable_vowifi), ConfigControls.VOWIFI, ui, onToggle)
        BooleanControlView(stringResource(R.string.enable_vowifi_while_roamed), ConfigControls.VOWIFI_ROAMING, ui, onToggle)
        BooleanControlView(stringResource(R.string.enable_video_calling_vt), ConfigControls.VIDEO_CALLING, ui, onToggle)
        BooleanControlView(stringResource(R.string.enable_ss_over_ut), ConfigControls.SS_OVER_UT, ui, onToggle)
        BooleanControlView(stringResource(R.string.enable_ss_over_cdma), ConfigControls.SS_OVER_CDMA, ui, onToggle)
        BooleanControlView(stringResource(R.string.show_wifi_only_for_vowifi), ConfigControls.WFC_WIFI_ONLY, ui, onToggle)
        BooleanControlView(stringResource(R.string.show_vowifi_preference_in_settings), ConfigControls.SHOW_VOWIFI_MODE, ui, onToggle)
        BooleanControlView(
            stringResource(R.string.show_vowifi_roaming_preference_in_settings),
            ConfigControls.SHOW_VOWIFI_ROAMING_MODE,
            ui,
            onToggle,
        )
        UserAgentPropertyView(label = stringResource(R.string.user_agent), value = ui.userAgent.value) {
            viewModel.setUserAgent(it)
        }
        RadioSelectPropertyView(
            label = stringResource(R.string.wi_fi_calling_carrier_name_format),
            values = arrayOf(
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
            ),
            selectedIndex = ui.wfcSpnFormat.value ?: 0,
            busy = ui.wfcSpnFormat.isBusy,
            error = if (ui.wfcSpnFormat.hasFailed) ui.wfcSpnFormat.message else null,
        ) { viewModel.setWfcSpnFormat(it) }

        if (PrivilegeManager.activeMode == PrivilegeMode.ROOT) {
            HeaderText(text = stringResource(R.string.root_vowifi_repair_title))
            RootVoWifiPanel(ui = ui, viewModel = viewModel)
        }

        HeaderText(text = stringResource(R.string.config_section_indicators))
        BooleanControlView(stringResource(R.string.show_vowifi_icon), ConfigControls.SHOW_VOWIFI_ICON, ui, onToggle)
        BooleanControlView(stringResource(R.string.always_show_data_icon), ConfigControls.ALWAYS_SHOW_DATA_ICON, ui, onToggle)
        BooleanControlView(stringResource(R.string.show_4g_for_lte_data_icon), ConfigControls.SHOW_4G_FOR_LTE, ui, onToggle)
        BooleanControlView(stringResource(R.string.hide_enhanced_data_icon), ConfigControls.HIDE_ENHANCED_DATA_ICON, ui, onToggle)
        BooleanControlView(stringResource(R.string.show_ims_status_in_sim_status), ConfigControls.SHOW_IMS_IN_SIM_INFO, ui, onToggle)

        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            val statusBarManager: StatusBarManager = context.getSystemService(StatusBarManager::class.java)
            val simSlotIndex = ui.simSlotIndex

            HeaderText(text = stringResource(R.string.qstile))
            ClickablePropertyView(label = stringResource(R.string.add_status_tile), value = "") {
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
            ClickablePropertyView(label = stringResource(R.string.add_toggle_tile), value = "") {
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
        }

        HeaderText(text = stringResource(R.string.miscellaneous))
        ClickablePropertyView(
            label = stringResource(R.string.reset_all_settings),
            value = stringResource(R.string.reverts_to_carrier_default),
        ) { viewModel.resetAll() }
        ClickablePropertyView(label = stringResource(R.string.expert_mode), value = "") {
            navController.navigate("config/$subId/edit")
        }
        ClickablePropertyView(label = stringResource(R.string.dump_config), value = "") {
            navController.navigate("config/$subId/dump")
        }
        ClickablePropertyView(label = stringResource(R.string.restart_ims_registration), value = "") {
            viewModel.restartIms()
        }
    }
}

@Composable
private fun RootVoWifiPanel(
    ui: ConfigUiState,
    viewModel: ConfigViewModel,
) {
    val status = ui.rootVoWifi
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
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

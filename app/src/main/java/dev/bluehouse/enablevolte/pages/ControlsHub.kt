package dev.bluehouse.enablevolte.pages

import android.telephony.SubscriptionInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.components.Panel
import dev.bluehouse.enablevolte.components.PremiumActionRow
import dev.bluehouse.enablevolte.components.PremiumPageIntro
import dev.bluehouse.enablevolte.components.PremiumSectionLabel
import dev.bluehouse.enablevolte.uniqueName

@Suppress("ktlint:standard:function-naming")
@Composable
fun ControlsHub(
    subscriptions: List<SubscriptionInfo>,
    navController: NavController,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PremiumPageIntro(
            eyebrow = stringResource(R.string.premium_controls_eyebrow),
            title = stringResource(R.string.premium_controls_title),
            description = stringResource(R.string.premium_controls_description),
        )
        if (subscriptions.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.controls_no_sim),
                    Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        subscriptions.forEach { subscription ->
            PremiumSectionLabel(subscription.uniqueName)
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PremiumActionRow(
                        title = stringResource(R.string.open_ims_5g_controls),
                        subtitle = stringResource(R.string.ims_controls_summary),
                        icon = Icons.Filled.Settings,
                        onClick = { navController.navigate("config/${subscription.subscriptionId}") },
                    )
                    PremiumActionRow(
                        title = stringResource(R.string.open_band_lte_controls),
                        subtitle = stringResource(R.string.band_controls_summary),
                        icon = Icons.Filled.SignalCellularAlt,
                        onClick = { navController.navigate("bands/${subscription.subscriptionId}") },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.controls_safety_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

package dev.bluehouse.enablevolte.pages

import android.telephony.SubscriptionInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.components.GlassSurface
import dev.bluehouse.enablevolte.components.HeaderText
import dev.bluehouse.enablevolte.uniqueName

@Suppress("ktlint:standard:function-naming")
@Composable
fun ControlsHub(
    subscriptions: List<SubscriptionInfo>,
    navController: NavController,
) {
    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderText(stringResource(R.string.control_center))
        Text(stringResource(R.string.control_center_description))
        if (subscriptions.isEmpty()) {
            GlassSurface(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.controls_no_sim), Modifier.padding(16.dp))
            }
        }
        subscriptions.forEach { subscription ->
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(subscription.uniqueName, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.ims_controls_summary))
                    Button(
                        onClick = { navController.navigate("config/${subscription.subscriptionId}") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.open_ims_5g_controls))
                    }
                    Text(stringResource(R.string.band_controls_summary))
                    OutlinedButton(
                        onClick = { navController.navigate("bands/${subscription.subscriptionId}") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.open_band_lte_controls))
                    }
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

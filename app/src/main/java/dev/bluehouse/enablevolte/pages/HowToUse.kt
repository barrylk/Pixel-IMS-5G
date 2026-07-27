package dev.bluehouse.enablevolte.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.components.GlassSurface
import dev.bluehouse.enablevolte.components.HeaderText

@Suppress("ktlint:standard:function-naming")
@Composable
fun HowToUse() {
    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderText(stringResource(R.string.how_to_use))
        HowToCard(R.string.how_to_home_title, R.string.how_to_home_body)
        HowToCard(R.string.how_to_controls_title, R.string.how_to_controls_body)
        HowToCard(R.string.how_to_monitor_title, R.string.how_to_monitor_body)
        HowToCard(R.string.how_to_field_title, R.string.how_to_field_body)
        HowToCard(R.string.how_to_recovery_title, R.string.how_to_recovery_body)
        Text(
            stringResource(R.string.how_to_limitations),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun HowToCard(title: Int, body: Int) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body))
        }
    }
}

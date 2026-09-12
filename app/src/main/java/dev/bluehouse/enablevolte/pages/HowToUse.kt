package dev.bluehouse.enablevolte.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.components.Panel
import dev.bluehouse.enablevolte.components.HeaderText
import dev.bluehouse.enablevolte.components.PremiumPageIntro
import dev.bluehouse.enablevolte.ui.theme.Signal
import dev.bluehouse.enablevolte.ui.theme.Fair
import dev.bluehouse.enablevolte.ui.theme.Good

@Suppress("ktlint:standard:function-naming")
@Composable
fun HowToUse() {
    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PremiumPageIntro(
            eyebrow = stringResource(R.string.how_to_5g_eyebrow),
            title = stringResource(R.string.how_to_5g_title),
            description = stringResource(R.string.how_to_5g_description),
        )
        FiveGGuideCard(
            badge = stringResource(R.string.how_to_root_badge),
            title = stringResource(R.string.how_to_root_5g_title),
            steps = stringResource(R.string.how_to_root_5g_steps),
            note = stringResource(R.string.how_to_root_5g_note),
            accent = Good,
            icon = Icons.Filled.AdminPanelSettings,
        )
        FiveGGuideCard(
            badge = stringResource(R.string.how_to_shizuku_badge),
            title = stringResource(R.string.how_to_shizuku_5g_title),
            steps = stringResource(R.string.how_to_shizuku_5g_steps),
            note = stringResource(R.string.how_to_shizuku_5g_note),
            accent = Signal,
            noteColor = Fair,
            icon = Icons.Filled.PhoneAndroid,
        )
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
private fun FiveGGuideCard(
    badge: String,
    title: String,
    steps: String,
    note: String,
    accent: Color,
    icon: ImageVector,
    noteColor: Color = Fair,
) {
    Panel(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.14f),
                contentColor = accent,
            ) {
                Text(
                    text = badge,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.16f),
                    contentColor = accent,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(23.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                )
            }
            Text(
                text = steps,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = noteColor.copy(alpha = 0.11f),
                contentColor = noteColor,
            ) {
                Text(
                    text = note,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun HowToCard(title: Int, body: Int) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body))
        }
    }
}

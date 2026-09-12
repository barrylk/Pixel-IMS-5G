package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.ChangelogItem
import dev.bluehouse.enablevolte.ChangelogTone
import dev.bluehouse.enablevolte.InstalledChangelog
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.ui.theme.SignalAmber
import dev.bluehouse.enablevolte.ui.theme.SignalGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewDialog(
    changelog: InstalledChangelog,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Panel(Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .heightIn(max = 680.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.whats_new_eyebrow),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.whats_new_version, changelog.version),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = stringResource(R.string.app_signature),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                changelog.items.forEach { item -> ChangelogCard(item) }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.explore_update))
                }
            }
        }
    }
}

@Composable
private fun ChangelogCard(item: ChangelogItem) {
    val (color, icon) = changelogStyle(item.tone)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = color.copy(alpha = 0.11f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.16f),
                contentColor = color,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(9.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
                if (item.detail.isNotBlank()) {
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun changelogStyle(tone: ChangelogTone): Pair<Color, ImageVector> =
    when (tone) {
        ChangelogTone.FEATURE -> MaterialTheme.colorScheme.primary to Icons.Filled.AutoAwesome
        ChangelogTone.IMPROVEMENT -> MaterialTheme.colorScheme.tertiary to Icons.Filled.Build
        ChangelogTone.FIX -> SignalGreen to Icons.Filled.CheckCircle
        ChangelogTone.IMPORTANT -> SignalAmber to Icons.Filled.Info
    }

package dev.bluehouse.enablevolte.components

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.R

/**
 * A switch for one privileged setting.
 *
 * [busy] and [error] exist because these writes are not instant and can be
 * refused: the switch is held while the change is in flight, and the subtitle
 * carries the reason when the device declines it, instead of the control
 * silently showing a position the modem never accepted.
 */
@Composable
fun BooleanPropertyView(
    label: String,
    toggled: Boolean?,
    enabled: Boolean = true,
    trueLabel: String = stringResource(R.string.yes),
    falseLabel: String = stringResource(R.string.no),
    minSdk: Int = Build.VERSION.SDK_INT,
    busy: Boolean = false,
    error: String? = null,
    onClick: ((Boolean) -> Unit)? = null,
) {
    val localEnabled = enabled && Build.VERSION.SDK_INT >= minSdk && !busy

    if (toggled == null) {
        CompactPropertySurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(text = stringResource(R.string.unknown), style = MaterialTheme.typography.bodySmall, color = statusToneColor(StatusTone.WARNING))
            }
        }
        return
    }
    if (onClick != null) {
        CompactPropertySurface(modifier = Modifier.fillMaxWidth(), onClick = { if (localEnabled) onClick(!toggled) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                Column(modifier = Modifier.weight(1F)) {
                    Text(text = label, style = MaterialTheme.typography.titleMedium)
                    SubtitleText(toggled = toggled, trueLabel = trueLabel, falseLabel = falseLabel, busy = busy, error = error)
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Switch(checked = toggled, enabled = localEnabled, onCheckedChange = onClick)
                }
            }
        }
    } else {
        CompactPropertySurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                SubtitleText(toggled = toggled, trueLabel = trueLabel, falseLabel = falseLabel, busy = busy, error = error)
            }
        }
    }
}

@Composable
private fun SubtitleText(
    toggled: Boolean,
    trueLabel: String,
    falseLabel: String,
    busy: Boolean,
    error: String?,
) {
    when {
        busy ->
            Text(
                text = stringResource(R.string.control_applying),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        error != null ->
            Text(text = error, style = MaterialTheme.typography.bodySmall, color = statusToneColor(StatusTone.DANGER))
        else ->
            Text(
                text = if (toggled) trueLabel else falseLabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (toggled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Preview
@Composable
fun BooleanPropertyViewPreview() {
    var toggled by remember { mutableStateOf(false) }
    BooleanPropertyView(label = "Lorem Ipsum", toggled = toggled) { toggled = !toggled }
}

@Preview
@Composable
fun LowSDKBooleanPropertyViewPreview() {
    var toggled by remember { mutableStateOf(false) }
    BooleanPropertyView(label = "Lorem Ipsum", toggled = toggled, minSdk = 999) { toggled = !toggled }
}

@Preview
@Composable
fun FailedBooleanPropertyViewPreview() {
    BooleanPropertyView(label = "Lorem Ipsum", toggled = false, error = "The device did not accept this change.") {}
}

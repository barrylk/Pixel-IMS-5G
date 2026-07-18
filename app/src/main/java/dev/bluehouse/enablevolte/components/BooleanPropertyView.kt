package dev.bluehouse.enablevolte.components

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.sp
import dev.bluehouse.enablevolte.R

@Composable
fun BooleanPropertyView(
    label: String,
    toggled: Boolean?,
    enabled: Boolean = true,
    trueLabel: String = stringResource(R.string.yes),
    falseLabel: String = stringResource(R.string.no),
    minSdk: Int = Build.VERSION.SDK_INT,
    onClick: ((Boolean) -> Unit)? = null,
) {
    val localEnabled = enabled && Build.VERSION.SDK_INT >= minSdk

    if (toggled == null) {
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(text = stringResource(R.string.unknown), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    if (onClick != null) {
        GlassSurface(modifier = Modifier.fillMaxWidth(), onClick = { if (localEnabled) onClick(!toggled) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Column(modifier = Modifier.weight(1F)) {
                    Text(text = label, style = MaterialTheme.typography.titleMedium)
                    Text(text = if (toggled) trueLabel else falseLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = toggled, enabled = localEnabled, onCheckedChange = onClick)
            }
        }
    } else {
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(text = if (toggled) trueLabel else falseLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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

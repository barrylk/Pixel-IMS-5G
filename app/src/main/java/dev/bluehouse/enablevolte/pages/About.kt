package dev.bluehouse.enablevolte.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.BuildConfig
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.ReleaseInfo
import dev.bluehouse.enablevolte.UpdateManager
import dev.bluehouse.enablevolte.components.ClickablePropertyView
import dev.bluehouse.enablevolte.components.HeaderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GITHUB_PROFILE = "https://github.com/barrylk"
private const val FACEBOOK_PROFILE = "https://www.facebook.com/nirmalafromslk/"
private const val UPSTREAM_PROJECT = "https://github.com/kyujin-cho/pixel-volte-patch"

@Composable
fun About() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf(context.getString(R.string.update_not_checked)) }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }

    fun checkUpdates() {
        checking = true
        scope.launch {
            try {
                val latest = withContext(Dispatchers.IO) { UpdateManager.latestRelease() }
                release = latest
                status = if (UpdateManager.isNewer(latest.version)) {
                    context.getString(R.string.update_available, latest.version)
                } else {
                    context.getString(R.string.up_to_date, BuildConfig.VERSION_NAME)
                }
            } catch (e: Exception) {
                status = context.getString(R.string.update_check_failed, e.message ?: "Unknown error")
            } finally {
                checking = false
            }
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderText(stringResource(R.string.developer))
        ClickablePropertyView(
            label = stringResource(R.string.developed_by),
            value = "Nadeeja Nirmala",
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = { UpdateManager.open(context, GITHUB_PROFILE) }, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_github), contentDescription = null)
                Text(" GitHub")
            }
            FilledTonalButton(onClick = { UpdateManager.open(context, FACEBOOK_PROFILE) }, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_facebook), contentDescription = null)
                Text(" Facebook")
            }
        }
        OutlinedButton(onClick = { UpdateManager.open(context, UpdateManager.ISSUES_URL) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Settings, contentDescription = null)
            Text(" ${stringResource(R.string.report_bug)}")
        }

        HeaderText(stringResource(R.string.updates))
        ClickablePropertyView(label = stringResource(R.string.installed_version), value = BuildConfig.VERSION_NAME)
        ClickablePropertyView(label = stringResource(R.string.update_status), value = status)
        Button(onClick = { checkUpdates() }, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text(if (checking) " ${stringResource(R.string.checking)}" else " ${stringResource(R.string.check_updates)}")
        }
        release?.takeIf { UpdateManager.isNewer(it.version) && it.apkUrl != null }?.let { available ->
            Button(
                onClick = {
                    UpdateManager.download(context, available)
                    status = context.getString(R.string.update_downloading, available.version)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Text(" ${stringResource(R.string.download_install)}")
            }
        }

        HeaderText(stringResource(R.string.credits))
        ClickablePropertyView(
            label = "Pixel IMS / pixel-volte-patch",
            value = stringResource(R.string.upstream_credit),
            onClick = { UpdateManager.open(context, UPSTREAM_PROJECT) },
        )
        ClickablePropertyView(
            label = stringResource(R.string.license),
            value = "GNU General Public License v3.0",
            onClick = { UpdateManager.open(context, "https://www.gnu.org/licenses/gpl-3.0.html") },
        )
        Text(stringResource(R.string.unofficial_notice), modifier = Modifier.padding(8.dp, 0.dp, 8.dp, 32.dp))
    }
}

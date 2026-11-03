package dev.bluehouse.enablevolte.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.bluehouse.enablevolte.*
import dev.bluehouse.enablevolte.components.AppBackdrop
import dev.bluehouse.enablevolte.ui.theme.EnableVoLTETheme
import dev.bluehouse.enablevolte.ui.theme.ThemeMode

/** Design fixtures only. These previews never access a device or a privileged service. */
private fun homeFixture(): SetupState = SetupState(
    loading = false, carrier = "Dialog", checkedAt = 1792745256000,
    checks = listOf(
        ReadinessCheck("privilege", "Root access", "Root service connected", CheckStatus.PASS),
        ReadinessCheck("ims", "Device supports IMS", "Reported by the platform", CheckStatus.PASS),
        ReadinessCheck("volte", "VoLTE enabled", "Carrier configuration allows LTE calling. IMS registration is checked separately.", CheckStatus.PASS),
        ReadinessCheck("nr_mode", "5G mode", "NSA and SA are enabled in carrier configuration.", CheckStatus.PASS),
        ReadinessCheck("bands", "Band selection", "LTE and NR use automatic band selection.", CheckStatus.PASS, FixAction.OPEN_BANDS, "Choose bands", false),
        ReadinessCheck("patch", "Modem region patch", "Patched carrier database mounted", CheckStatus.PASS),
        ReadinessCheck("endc", "5G connected", "5G is advertised as available, but no active 5G link is confirmed. The reason needs further diagnosis.", CheckStatus.FAIL),
        ReadinessCheck("ims_reg", "IMS registered", "IMS is registered over LTE.", CheckStatus.PASS, blocking = false),
    ),
)

@Preview(name = "Home dark · sample data", widthDp = 412, heightDp = 915, showBackground = true)
@Composable private fun HomeDarkPreview() = HomeDesignPreview(ThemeMode.DARK, homeFixture())

@Preview(name = "Home light · sample data", widthDp = 412, heightDp = 915, showBackground = true)
@Composable private fun HomeLightPreview() = HomeDesignPreview(ThemeMode.LIGHT, homeFixture())

@Preview(name = "Home checking", widthDp = 360, heightDp = 800, showBackground = true)
@Composable private fun HomeCheckingPreview() = HomeDesignPreview(ThemeMode.DARK, SetupState())

@Preview(name = "Home no SIM", widthDp = 360, heightDp = 800, showBackground = true)
@Composable private fun HomeNoSimPreview() = HomeDesignPreview(ThemeMode.LIGHT, SetupState(loading = false), noSim = true)

@Preview(name = "Home large text", widthDp = 360, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Composable private fun HomeLargeTextPreview() = HomeDesignPreview(ThemeMode.DARK, homeFixture())

@Composable private fun HomeDesignPreview(theme: ThemeMode, state: SetupState, noSim: Boolean = false) {
    EnableVoLTETheme(themeMode = theme) {
        AppBackdrop {
            Box(Modifier.fillMaxSize()) {
                HomeDashboard(
                    sims = if (noSim) emptyList() else listOf(HomeSim(1, "Dialog", 1), HomeSim(2, "SLTMobitel", 2)),
                    selectedId = if (noSim) null else 1, ui = state, accessMode = "Root",
                    onSelectSim = {}, onRefresh = {}, onNavigate = {}, onDismissNote = {}, onFix = {},
                )
            }
        }
    }
}

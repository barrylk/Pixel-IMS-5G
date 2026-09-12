package dev.bluehouse.enablevolte

enum class ChangelogTone {
    FEATURE,
    IMPROVEMENT,
    FIX,
    IMPORTANT,
}

data class ChangelogItem(
    val title: String,
    val detail: String,
    val tone: ChangelogTone,
)

data class InstalledChangelog(
    val version: String,
    val items: List<ChangelogItem>,
)

object ReleaseChangelogCatalog {
    fun forVersion(version: String): InstalledChangelog? =
        when (version.removePrefix("v")) {
            "1.0.8" ->
                InstalledChangelog(
                    version = "1.0.8",
                    items =
                        listOf(
                            ChangelogItem(
                                title = "Switches tell the truth",
                                detail = "Every SIM Config switch now waits for the device to confirm the change and reads the value back. A setting the modem refuses reports why instead of quietly showing itself as on.",
                                tone = ChangelogTone.FIX,
                            ),
                            ChangelogItem(
                                title = "No more freezing on toggle",
                                detail = "Applying a setting no longer runs on the interface thread, so the app stays responsive and cannot hang while a root or Shizuku write is in progress.",
                                tone = ChangelogTone.FIX,
                            ),
                            ChangelogItem(
                                title = "Settings survive rotation",
                                detail = "Rotating the phone or switching between light and dark no longer discards the SIM Config page or interrupts a change that is still being applied.",
                                tone = ChangelogTone.FIX,
                            ),
                            ChangelogItem(
                                title = "Tidier SIM Config page",
                                detail = "Controls are grouped into Network, Calling, and Status bar sections instead of one long list, and each one shows when it is applying.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                            ChangelogItem(
                                title = "Faster page load",
                                detail = "The page reads the carrier configuration once rather than around thirty times, and no longer scans every configuration field it never used.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                            ChangelogItem(
                                title = "Please re-check your settings",
                                detail = "Every SIM Config control was rewritten in this release. Open SIM Config once after updating and confirm your VoLTE, VoNR, and band choices still read correctly.",
                                tone = ChangelogTone.IMPORTANT,
                            ),
                        ),
                )
            "1.0.7" ->
                InstalledChangelog(
                    version = "1.0.7",
                    items =
                        listOf(
                            ChangelogItem(
                                title = "Much smaller download",
                                detail = "Code shrinking is now enabled and fourteen unused libraries have been removed, cutting the release package from about 54 MB to roughly 3 MB.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                            ChangelogItem(
                                title = "No feature changes",
                                detail = "This release is internal housekeeping only. Root and Shizuku behaviour, band selection, the modem patch, and field test are all unchanged.",
                                tone = ChangelogTone.IMPORTANT,
                            ),
                            ChangelogItem(
                                title = "Automated build checks",
                                detail = "Every change is now compiled and unit tested automatically before release, so build regressions are caught earlier.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                        ),
                )
            "1.0.6" ->
                InstalledChangelog(
                    version = "1.0.6",
                    items =
                        listOf(
                            ChangelogItem(
                                title = "Small bug fixes",
                                detail = "Root SIM Config choices now stay together instead of later VoNR, VoWiFi, or enhanced-data-icon changes replacing earlier choices.",
                                tone = ChangelogTone.FIX,
                            ),
                            ChangelogItem(
                                title = "Persistent Root profiles",
                                detail = "The app saves one complete profile per SIM and restores it after reboot, user unlock, app update, or Root service reconnect.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                            ChangelogItem(
                                title = "Safe reset behavior",
                                detail = "Restore Google defaults also removes the saved Root profile. Shizuku controls remain session-only and may reset after reboot.",
                                tone = ChangelogTone.IMPORTANT,
                            ),
                        ),
                )
            "1.0.5" ->
                InstalledChangelog(
                    version = "1.0.5",
                    items =
                        listOf(
                            ChangelogItem(
                                title = "VoLTE Fix",
                                detail = "Root mode can now show a live boxed VoLTE badge in Android’s right-side status area while IMS is registered over LTE or NR. The icon disappears when cellular IMS disconnects.",
                                tone = ChangelogTone.FIX,
                            ),
                            ChangelogItem(
                                title = "Live VoWiFi indicator",
                                detail = "A separate right-side VoWiFi icon appears only when IMS is genuinely registered through IWLAN. These indicators do not create an ongoing notification.",
                                tone = ChangelogTone.FEATURE,
                            ),
                            ChangelogItem(
                                title = "Root VoWiFi repair",
                                detail = "A reversible per-SIM repair opens the Android WFC gates, selects Wi-Fi preferred, restarts IMS, and reports carrier ePDG, profile, DNS, or authentication blockers honestly.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                            ChangelogItem(
                                title = "Deeper IMS reports",
                                detail = "Field reports now include the effective VoWiFi setting, IMS registration transport, IWLAN state, Wi-Fi state, and sanitized recent failure evidence.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                            ChangelogItem(
                                title = "Small update",
                                detail = "This release focuses on VoLTE and VoWiFi visibility and diagnostics. It does not change the regional 5G modem patch or bypass carrier IMS provisioning.",
                                tone = ChangelogTone.IMPORTANT,
                            ),
                        ),
                )
            "1.0.4r" ->
                InstalledChangelog(
                    version = "1.0.4 rev",
                    items =
                        listOf(
                            ChangelogItem(
                                title = "How to enable 5G",
                                detail = "The in-app guide now starts with dedicated, step-by-step Root and Shizuku 5G setup paths using the exact controls users need.",
                                tone = ChangelogTone.FEATURE,
                            ),
                            ChangelogItem(
                                title = "Root setup made explicit",
                                detail = "The guide covers Root Force, the Magisk regional modem compatibility patch, the required reboot, NSA-first testing, and firmware-update recovery.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                            ChangelogItem(
                                title = "Clear Shizuku workflow",
                                detail = "Shizuku users get a separate Automatic-bands, Force NSA, Easy Mode, regional-profile, Monitor, and Field Test sequence.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                            ChangelogItem(
                                title = "Regional limit remains honest",
                                detail = "Shizuku can open Android-side gates but cannot replace Tensor cfg.db. A modem-policy rejection can still require the Root/Magisk patch.",
                                tone = ChangelogTone.IMPORTANT,
                            ),
                        ),
                )
            "1.0.4" ->
                InstalledChangelog(
                    version = "1.0.4",
                    items =
                        listOf(
                            ChangelogItem(
                                title = "Beautiful post-update changelogs",
                                detail = "OTA updates now open a versioned liquid-glass What’s New screen. It preserves GitHub release notes across Android’s installer and can be reopened from About.",
                                tone = ChangelogTone.FEATURE,
                            ),
                            ChangelogItem(
                                title = "Premium interface redesign",
                                detail = "A calmer hierarchy, semantic status colors, refined typography, compact expert controls, glass surfaces and restrained motion now scale cleanly across Pixel displays.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                            ChangelogItem(
                                title = "Perfectly aligned navigation",
                                detail = "The bottom bar is now custom-built with equal cells, fixed centered icon slots and stable label baselines. The app header also carries the by Nirmala signature.",
                                tone = ChangelogTone.FIX,
                            ),
                            ChangelogItem(
                                title = "Safer mobile-only diagnostics",
                                detail = "Monitor and Field Test temporarily disable Wi-Fi, fail closed if isolation cannot be confirmed, and restore the previous Wi-Fi state when the session ends.",
                                tone = ChangelogTone.IMPORTANT,
                            ),
                            ChangelogItem(
                                title = "Deeper field reports",
                                detail = "Reports now identify the OS build, operational PLMN, evidence source, NR and EN-DC flag semantics, IMS/callback coverage, readable policy gates and sanitized root modem deltas.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                            ChangelogItem(
                                title = "Honest Shizuku guidance",
                                detail = "Every Pixel using Shizuku receives the regional 5G limitation notice on stock Pixel OS and supported alternative Pixel operating systems.",
                                tone = ChangelogTone.IMPORTANT,
                            ),
                        ),
                )
            else -> null
        }

    fun fromReleaseNotes(
        version: String,
        notes: String,
    ): InstalledChangelog? {
        val items =
            notes
                .lineSequence()
                .map(String::trim)
                .filter { it.startsWith("- ") || it.startsWith("* ") }
                .map { it.drop(2).trim() }
                .filter(String::isNotBlank)
                .map { line ->
                    val titleMatch = Regex("^\\*\\*(.+?)\\*\\*[:—-]?\\s*(.*)$").find(line)
                    val title = titleMatch?.groupValues?.get(1)?.trim()?.trimEnd(':', '—', '-')
                        ?: line.substringBefore(":").trim().take(72)
                    val detail = titleMatch?.groupValues?.get(2)?.trim().orEmpty()
                        .ifBlank {
                            line.substringAfter(":", missingDelimiterValue = line).trim()
                        }
                    ChangelogItem(
                        title = title,
                        detail = detail.takeIf { it != title }.orEmpty(),
                        tone = toneFor("$title $detail"),
                    )
                }
                .take(10)
                .toList()
        return items.takeIf(List<*>::isNotEmpty)?.let {
            InstalledChangelog(version.removePrefix("v"), it)
        }
    }

    private fun toneFor(value: String): ChangelogTone {
        val normalized = value.lowercase()
        return when {
            listOf("warning", "important", "root", "shizuku", "safety").any(normalized::contains) ->
                ChangelogTone.IMPORTANT
            listOf("fix", "crash", "restore", "correct").any(normalized::contains) ->
                ChangelogTone.FIX
            listOf("improve", "redesign", "refine", "report").any(normalized::contains) ->
                ChangelogTone.IMPROVEMENT
            else -> ChangelogTone.FEATURE
        }
    }
}

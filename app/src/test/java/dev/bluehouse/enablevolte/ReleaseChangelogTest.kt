package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseChangelogTest {
    @Test
    fun radioLogReleaseHasBundledChangelog() {
        val changelog = ReleaseChangelogCatalog.forVersion("v2.2.8")

        assertEquals("2.2.8", changelog?.version)
        assertTrue(changelog.orEmptyItems().any { it.title.contains("Radio log capture") })
        assertTrue(changelog.orEmptyItems().any { it.tone == ChangelogTone.IMPORTANT && it.title.contains("Pixel 10 Pro XL") })
    }

    @Test
    fun liquidReleaseHasBundledChangelogAndStatesThePixel10Limit() {
        val changelog = ReleaseChangelogCatalog.forVersion("v2.2.7")

        assertEquals("2.2.7", changelog?.version)
        assertTrue(changelog.orEmptyItems().any { it.title.contains("Liquid") })
        assertTrue(changelog.orEmptyItems().any { it.tone == ChangelogTone.IMPORTANT && it.title.contains("Pixel 10 Pro XL") })
    }

    @Test
    fun combinedReleaseIncludesRootAndDiagnosticFixes() {
        val changelog = ReleaseChangelogCatalog.forVersion("v2.2.6")

        assertEquals("2.2.6", changelog?.version)
        assertTrue(changelog.orEmptyItems().any { it.title.contains("KernelSU") })
        assertTrue(changelog.orEmptyItems().any { it.title.contains("Magisk") })
        assertTrue(changelog.orEmptyItems().any { it.title.contains("callback") })
        assertTrue(changelog.orEmptyItems().any { it.title.contains("evidence") })
    }

    @Test
    fun finalKernelSuBuildHasBundledChangelog() {
        val changelog = ReleaseChangelogCatalog.forVersion("2.2.5")

        assertEquals("2.2.5", changelog?.version)
        assertTrue(changelog?.items?.any { it.title.contains("KernelSU") } == true)
    }

    @Test
    fun currentReleaseHasStructuredHighlightedItems() {
        val changelog = ReleaseChangelogCatalog.forVersion("v1.0.6")

        assertNotNull(changelog)
        assertEquals("1.0.6", changelog?.version)
        assertTrue(changelog.orEmptyItems().any { it.title == "Small bug fixes" })
    }

    @Test
    fun githubMarkdownBulletsBecomeCards() {
        val changelog =
            ReleaseChangelogCatalog.fromReleaseNotes(
                "1.1.0",
                """
                ## Pixel IMS 5G 1.1.0
                - **New monitor:** Adds a live modem timeline.
                - **Crash fix:** Corrects Pixel 6 band selection.
                """.trimIndent(),
            )

        assertEquals(2, changelog?.items?.size)
        assertEquals("New monitor", changelog?.items?.first()?.title)
        assertEquals(ChangelogTone.FIX, changelog?.items?.last()?.tone)
    }

    private fun InstalledChangelog?.orEmptyItems(): List<ChangelogItem> = this?.items.orEmpty()
}

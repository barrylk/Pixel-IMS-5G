package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldTestReportTest {
    @Test
    fun failedRegistrationDoesNotClaimSuccess() {
        val summary = FieldTestReporter.callbackTimelineSummary(false, emptyList())
        assertTrue(summary.contains("registration failed", ignoreCase = true))
        assertFalse(summary.contains("Registration succeeded"))
        assertTrue(summary.contains("polling"))
    }

    @Test
    fun successfulRegistrationWithoutEventsIsDistinguishedFromFailure() {
        val summary = FieldTestReporter.callbackTimelineSummary(true, emptyList())
        assertTrue(summary.contains("Registration succeeded"))
        assertFalse(summary.contains("registration failed", ignoreCase = true))
    }

    @Test
    fun capturedEventsArePreserved() {
        assertEquals("+10ms LTE\n+20ms NR", FieldTestReporter.callbackTimelineSummary(true, listOf("+10ms LTE", "+20ms NR")))
    }

    @Test
    fun airtelReportPhysicalMismatchDoesNotInventCellInfoConflict() {
        val warnings = FieldTestReporter.evidenceWarnings(false, false, true)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("device-global"))
        assertFalse(warnings.single().contains("conflicting or cross-SIM CellInfo"))
    }

    @Test
    fun dialogReportCellDisagreementsRetainTheirWarning() {
        val warnings = FieldTestReporter.evidenceWarnings(false, true, false)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("conflicting or cross-SIM CellInfo"))
    }

    @Test
    fun absentCellsAreIncompleteEvidenceRatherThanProvenConflicts() {
        val warnings = FieldTestReporter.evidenceWarnings(true, false, false)
        assertTrue(warnings.single().contains("did not expose"))
        assertFalse(warnings.single().contains("conflicting"))
    }

    @Test
    fun independentWarningsRemainSeparateAndCleanEvidenceHasNoWarnings() {
        assertEquals(3, FieldTestReporter.evidenceWarnings(true, true, true).size)
        assertTrue(FieldTestReporter.evidenceWarnings(false, false, false).isEmpty())
    }
}

package dev.bluehouse.enablevolte

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlStateTest {
    @Test
    fun confirmsWhenTheDeviceAgrees() =
        runBlocking {
            var written: Boolean? = null
            val outcome =
                applyAndConfirm(
                    requested = true,
                    write = { written = true },
                    readBack = { written },
                )

            assertTrue(outcome is WriteOutcome.Confirmed)
            assertEquals(true, (outcome as WriteOutcome.Confirmed).value)
        }

    @Test
    fun rejectsWhenTheDeviceReportsSomethingElse() =
        runBlocking {
            val outcome =
                applyAndConfirm(
                    requested = true,
                    write = { },
                    readBack = { false },
                )

            assertTrue(outcome is WriteOutcome.Rejected)
            assertEquals(false, (outcome as WriteOutcome.Rejected).actual)
        }

    @Test
    fun failsWhenTheWriteThrows() =
        runBlocking {
            val outcome =
                applyAndConfirm<Boolean>(
                    requested = true,
                    write = { throw IllegalStateException("no root") },
                    readBack = { true },
                )

            assertTrue(outcome is WriteOutcome.Failed)
            assertEquals("no root", (outcome as WriteOutcome.Failed).reason)
        }

    @Test
    fun aRejectedWriteDoesNotMoveTheControlToTheRequestedValue() {
        val before = ControlState(value = false, phase = ControlPhase.IDLE)
        val after = before.reduce(WriteOutcome.Rejected(actual = false, reason = "refused"))

        assertEquals(false, after.value)
        assertEquals(ControlPhase.FAILED, after.phase)
        assertTrue(after.hasFailed)
    }

    @Test
    fun aFailedWriteKeepsTheLastKnownValue() {
        val before = ControlState(value = true, phase = ControlPhase.CONFIRMED)
        val after = before.reduce(WriteOutcome.Failed("service unavailable"))

        assertEquals(true, after.value)
        assertEquals("service unavailable", after.message)
    }

    @Test
    fun readQuietlySwallowsFailures() =
        runBlocking {
            assertNull(readQuietly<Int> { throw IllegalStateException("gone") })
            assertEquals(7, readQuietly { 7 })
        }
}

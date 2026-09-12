package dev.bluehouse.enablevolte

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lifecycle of a single privileged control.
 *
 * Controls in this app write to CarrierConfig through a root or Shizuku binder.
 * Those writes take time and can be refused by the modem, so a control is not
 * simply on or off: it is idle, mid-flight, confirmed against a read-back, or
 * failed.
 */
enum class ControlPhase {
    IDLE,
    APPLYING,
    CONFIRMED,
    FAILED,
}

/**
 * The value a control is showing, together with how much that value can be
 * trusted. [value] is always the last value actually read back from the
 * device, never the value the user requested.
 */
data class ControlState<T>(
    val value: T? = null,
    val phase: ControlPhase = ControlPhase.IDLE,
    val message: String? = null,
) {
    val isBusy: Boolean get() = phase == ControlPhase.APPLYING

    val hasFailed: Boolean get() = phase == ControlPhase.FAILED

    fun applying(): ControlState<T> = copy(phase = ControlPhase.APPLYING, message = null)
}

/**
 * Result of a privileged write, describing what the device reports afterwards
 * rather than what was asked for.
 */
sealed interface WriteOutcome<out T> {
    data class Confirmed<T>(val value: T) : WriteOutcome<T>

    data class Rejected<T>(val actual: T?, val reason: String) : WriteOutcome<T>

    data class Failed(val reason: String) : WriteOutcome<Nothing>
}

/**
 * Runs a privileged write off the main thread and then reads the value back to
 * find out whether it actually took effect.
 *
 * Binder calls to the privileged service block, so calling one from a Compose
 * click handler janks the UI and risks an ANR. Every call site should go
 * through here instead.
 *
 * The read-back is what makes a control honest. Writing a CarrierConfig key
 * does not guarantee the modem accepted it, so the caller is told the value
 * the device reports, which may differ from the value requested.
 */
suspend fun <T> applyAndConfirm(
    requested: T,
    write: () -> Unit,
    readBack: () -> T?,
): WriteOutcome<T> = withContext(Dispatchers.IO) {
    try {
        write()
        val actual = readBack()
        if (actual == requested) {
            WriteOutcome.Confirmed(requested)
        } else {
            WriteOutcome.Rejected(actual, "The device did not accept this change.")
        }
    } catch (e: Exception) {
        WriteOutcome.Failed(e.message ?: "This change could not be applied.")
    }
}

/**
 * Reads a value off the main thread, returning null rather than throwing when
 * the privileged service is unavailable.
 */
suspend fun <T> readQuietly(read: () -> T?): T? = withContext(Dispatchers.IO) {
    try {
        read()
    } catch (e: Exception) {
        null
    }
}

/** Folds a [WriteOutcome] back into the state a control should now display. */
fun <T> ControlState<T>.reduce(outcome: WriteOutcome<T>): ControlState<T> =
    when (outcome) {
        is WriteOutcome.Confirmed ->
            ControlState(outcome.value, ControlPhase.CONFIRMED)
        is WriteOutcome.Rejected ->
            ControlState(outcome.actual ?: value, ControlPhase.FAILED, outcome.reason)
        is WriteOutcome.Failed ->
            ControlState(value, ControlPhase.FAILED, outcome.reason)
    }

package dev.bluehouse.enablevolte

/** A shell resolves su on-device, avoiding Java's direct executable lookup on KernelSU. */
internal fun rootShellCommand(): Array<String> = arrayOf("/system/bin/sh", "-c", "exec su")

/**
 * Coalesces callers while a single root request is in flight. Root managers expose one
 * process-wide `su` prompt, so starting another root service request only creates races and can
 * leave libsu's pending state stuck when the first startup has not completed yet.
 */
internal class RootRequestQueue<T> {
    private val callbacks = mutableListOf<(T) -> Unit>()

    @Synchronized
    fun enqueue(callback: (T) -> Unit): Boolean {
        val shouldStart = callbacks.isEmpty()
        callbacks += callback
        return shouldStart
    }

    @Synchronized
    fun complete(value: T): List<(T) -> Unit> {
        val completed = callbacks.toList()
        callbacks.clear()
        return completed
    }
}

/** Run a forwarded Binder transaction as the service process and always restore its caller. */
internal inline fun <T> withClearedBinderIdentity(
    clearIdentity: () -> Long,
    restoreIdentity: (Long) -> Unit,
    block: () -> T,
): T {
    val token = clearIdentity()
    return try {
        block()
    } finally {
        restoreIdentity(token)
    }
}

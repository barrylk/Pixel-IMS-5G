package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BinderIdentityScopeTest {
    @Test
    fun `restores the caller identity after a successful forwarded call`() {
        val events = mutableListOf<String>()

        val result = withClearedBinderIdentity(
            clearIdentity = {
                events += "clear"
                42L
            },
            restoreIdentity = { events += "restore:$it" },
        ) {
            events += "call"
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf("clear", "call", "restore:42"), events)
    }

    @Test
    fun `restores the caller identity when the forwarded call throws`() {
        val events = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            withClearedBinderIdentity(
                clearIdentity = {
                    events += "clear"
                    7L
                },
                restoreIdentity = { events += "restore:$it" },
            ) {
                events += "call"
                error("binder failure")
            }
        }

        assertEquals(listOf("clear", "call", "restore:7"), events)
    }
}

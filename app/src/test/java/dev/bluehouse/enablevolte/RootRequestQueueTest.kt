package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootRequestQueueTest {
    @Test
    fun `a second request joins the in-flight root request`() {
        val queue = RootRequestQueue<String>()
        val results = mutableListOf<String>()

        assertTrue(queue.enqueue { results += "first:$it" })
        assertFalse(queue.enqueue { results += "second:$it" })

        queue.complete("connected").forEach { it("connected") }

        assertEquals(listOf("first:connected", "second:connected"), results)
        assertTrue(queue.enqueue { results += "third:$it" })
    }
}

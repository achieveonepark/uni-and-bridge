package com.achieve.unitybridge

import org.junit.Assert.*
import org.junit.Test

class BridgeSessionTest {
    @Test fun waitsForReadinessAndPreservesOrder() {
        val session = BridgeSession("first")
        assertTrue(session.offer("initial data").isEmpty())
        assertTrue(session.offer("queued update").isEmpty())
        assertEquals(listOf("initial data", "queued update"), session.markReady())
        assertTrue(session.markReady().isEmpty())
        assertEquals(listOf("live update"), session.offer("live update"))
    }

    @Test fun closingDiscardsPendingMessagesAndCannotReopen() {
        val session = BridgeSession("old")
        session.offer("stale update")
        session.close()
        assertTrue(session.markReady().isEmpty())
        assertThrows(IllegalStateException::class.java) { session.offer("late update") }
        val next = BridgeSession("new")
        next.offer("new initial data")
        assertEquals(listOf("new initial data"), next.markReady())
    }

    @Test fun boundsTheQueueUntilUnityIsReady() {
        val session = BridgeSession("slow")
        repeat(128) { session.offer("$it") }
        assertThrows(IllegalStateException::class.java) { session.offer("overflow") }
        assertEquals(128, session.markReady().size)
        assertEquals(listOf("after drain"), session.offer("after drain"))
    }
}

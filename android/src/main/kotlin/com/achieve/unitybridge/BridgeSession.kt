package com.achieve.unitybridge

/** Kept independent of Android so readiness, ordering and disposal can be verified on the JVM. */
internal class BridgeSession(val id: String) {
    private val pending = ArrayDeque<String>()
    private var ready = false
    private var closed = false

    fun offer(message: String): List<String> {
        check(!closed) { "The Unity session is closed." }
        if (ready) return listOf(message)
        check(pending.size < 128) { "Too many messages queued before Unity became ready (maximum 128)." }
        pending.addLast(message)
        return emptyList()
    }

    fun markReady(): List<String> {
        if (closed || ready) return emptyList()
        ready = true
        return pending.toList().also { pending.clear() }
    }

    fun close() {
        closed = true
        pending.clear()
    }
}

package com.achieve.unitybridge

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.UUID

data class UnityMessage(val name: String, val payload: JSONObject)
data class UnityResult(val cancelled: Boolean, val payload: JSONObject?)

/** One active Unity session per process. Public application calls run on the Android main thread. */
object UnityBridge {
    const val ACTIVITY_CLASS = "com.achieve.unitybridge.BridgeUnityActivity"
    private const val RECEIVER = "__AndroidUnityBridge"
    private val main = Handler(Looper.getMainLooper())

    interface Listener {
        fun onEvent(message: UnityMessage) {}
        fun onClosed(result: UnityResult) {}
        fun onError(error: Exception) {}
    }

    private class Session(
        val queue: BridgeSession,
        val listener: Listener,
        val context: Context,
        val returnIntent: Intent,
        val timeout: Runnable,
    )

    @Volatile private var session: Session? = null
    val isOpen: Boolean get() = session != null

    fun open(
        host: Activity,
        initialData: JSONObject = JSONObject(),
        readyTimeoutMillis: Long = 30_000,
        listener: Listener = object : Listener {},
    ) {
        requireMainThread()
        check(session == null) { "A Unity session is already open." }
        require(readyTimeoutMillis > 0) { "readyTimeoutMillis must be positive." }
        check(!host.isFinishing && !host.isDestroyed) { "The host Activity is no longer active." }

        // Fail before changing session state if the companion package was not exported.
        Class.forName(ACTIVITY_CLASS)
        val queue = BridgeSession(UUID.randomUUID().toString())
        queue.offer(encode(queue.id, "init", "", initialData))
        val timeout = Runnable {
            if (session?.queue === queue) {
                fail(IllegalStateException("Unity did not become ready within $readyTimeoutMillis ms."))
            }
        }
        val current = Session(
            queue, listener, host.applicationContext,
            Intent(host, host.javaClass).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK), timeout,
        )
        session = current
        main.postDelayed(timeout, readyTimeoutMillis)
        try {
            host.startActivity(Intent().setClassName(host, ACTIVITY_CLASS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: Exception) {
            clear(current)
            throw error
        }
    }

    fun send(name: String, payload: JSONObject = JSONObject()) {
        requireMainThread()
        require(name.isNotBlank()) { "Message name must not be blank." }
        val current = checkNotNull(session) { "No Unity session is open." }
        deliver(current, current.queue.offer(encode(current.queue.id, "message", name, payload)))
    }

    /** Cancels the session and brings the host forward; Unity stays loaded and pauses. */
    fun close() {
        requireMainThread()
        finish(null, cancelled = true)
    }

    // Called by the Unity receiver via JNI, or by the companion Activity via reflection.
    @JvmStatic fun getSessionId(): String = session?.queue?.id ?: ""

    @JvmStatic fun markReady(sessionId: String) {
        main.post {
            val current = session ?: return@post
            if (current.queue.id != sessionId) return@post
            main.removeCallbacks(current.timeout)
            deliver(current, current.queue.markReady())
        }
    }

    @JvmStatic fun receiveFromUnity(json: String) {
        main.post {
            val current = session ?: return@post
            val kind: String
            val name: String
            val payload: JSONObject
            try {
                val envelope = JSONObject(json)
                // Late messages from a previous invocation must not affect the new session.
                if (envelope.optString("sessionId") != current.queue.id) return@post
                require(envelope.getInt("version") == 1) { "Unsupported bridge protocol version." }
                payload = JSONObject(envelope.getString("payloadJson"))
                kind = envelope.getString("kind")
                require(kind == "event" || kind == "complete") { "Unsupported Unity message kind." }
                name = envelope.getString("name")
                require(kind != "event" || name.isNotBlank()) { "Event name must not be blank." }
            } catch (error: Exception) {
                current.listener.onError(error)
                return@post
            }
            when (kind) {
                "event" -> current.listener.onEvent(UnityMessage(name, payload))
                "complete" -> finish(payload, cancelled = false)
            }
        }
    }

    @JvmStatic fun cancelFromUnity(sessionId: String) {
        main.post {
            if (session?.queue?.id == sessionId) finish(null, cancelled = true)
        }
    }

    @JvmStatic fun cancelFromActivity() = cancelFromUnity(getSessionId())

    private fun encode(id: String, kind: String, name: String, payload: JSONObject): String =
        JSONObject().put("version", 1).put("sessionId", id).put("kind", kind)
            .put("name", name).put("payloadJson", payload.toString()).toString()

    private fun deliver(current: Session, messages: List<String>) {
        try {
            for (message in messages) {
                if (session !== current) return
                val player = Class.forName("com.unity3d.player.UnityPlayer")
                player.getMethod("UnitySendMessage", String::class.java, String::class.java, String::class.java)
                    .invoke(null, RECEIVER, "OnNativeMessage", message)
            }
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun clear(current: Session) {
        main.removeCallbacks(current.timeout)
        current.queue.close()
        session = null
    }

    private fun finish(payload: JSONObject?, cancelled: Boolean) {
        val current = session ?: return
        clear(current)
        try {
            Class.forName("com.unity3d.player.UnityPlayer")
                .getMethod("UnitySendMessage", String::class.java, String::class.java, String::class.java)
                .invoke(null, RECEIVER, "OnNativeClosed", current.queue.id)
        } catch (error: Exception) {
            // Returning to Android must still work if Unity failed during startup.
            android.util.Log.w("UnityBridge", "Could not notify Unity of session closure.", error)
        }
        try {
            current.context.startActivity(current.returnIntent)
        } catch (error: Exception) {
            current.listener.onError(error)
        }
        current.listener.onClosed(UnityResult(cancelled, payload))
    }

    private fun fail(error: Exception) {
        val current = session ?: return
        finish(null, cancelled = true)
        current.listener.onError(error)
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Call UnityBridge from the Android main thread." }
    }
}

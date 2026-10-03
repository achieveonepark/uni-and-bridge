package com.achieve.unitybridge.sample

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.achieve.unitybridge.UnityBridge
import com.achieve.unitybridge.UnityMessage
import com.achieve.unitybridge.UnityResult
import org.json.JSONObject

class MainActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val status = TextView(this).apply { text = "Open Unity to pass user data." }
        val open = Button(this).apply {
            text = "Open Unity"
            setOnClickListener {
                if (UnityBridge.isOpen) return@setOnClickListener
                try {
                    UnityBridge.open(
                        this@MainActivity,
                        JSONObject().put("userName", "Somiri").put("level", 7),
                        listener = object : UnityBridge.Listener {
                            override fun onEvent(message: UnityMessage) {
                                status.text = "Event: ${message.name} ${message.payload}"
                                if (message.name == "sceneLoaded") {
                                    UnityBridge.send("greeting", JSONObject().put("text", "Hello from Android"))
                                }
                            }
                            override fun onClosed(result: UnityResult) {
                                status.text = if (result.cancelled) "Cancelled" else "Result: ${result.payload}"
                            }
                            override fun onError(error: Exception) { status.text = error.message }
                        },
                    )
                } catch (error: Exception) { status.text = error.message }
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
            addView(status)
            addView(open)
        })
    }
}

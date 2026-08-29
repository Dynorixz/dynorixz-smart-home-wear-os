package com.dynorixz.smarthome.companion

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.google.android.gms.wearable.Wearable

class AuthRelayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        relayCallback()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        relayCallback()
    }

    private fun relayCallback() {
        val callback = intent?.data?.toString() ?: return finish()
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Toast.makeText(this, "Часы не подключены", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }
                val sends = nodes.map { node ->
                    Wearable.getMessageClient(this)
                        .sendMessage(node.id, "/oauth/callback", callback.encodeToByteArray())
                }
                sends.last().addOnCompleteListener {
                    Toast.makeText(this, "Вход продолжен на часах", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Не удалось связаться с часами", Toast.LENGTH_LONG).show()
                finish()
            }
    }
}


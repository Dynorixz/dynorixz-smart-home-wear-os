package com.dynorixz.smarthome.auth

import android.annotation.SuppressLint
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class OAuthRelayListenerService : WearableListenerService() {
    @SuppressLint("WearRecents")
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/oauth/callback") return
        val uri = runCatching { event.data.decodeToString().toUri() }.getOrNull() ?: return
        startActivity(Intent(this, OAuthCallbackActivity::class.java).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }
}

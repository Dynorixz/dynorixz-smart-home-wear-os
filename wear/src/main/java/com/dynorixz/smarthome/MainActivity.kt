package com.dynorixz.smarthome

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dynorixz.smarthome.data.local.SecureTokenStore
import com.dynorixz.smarthome.data.local.TokenState
import com.dynorixz.smarthome.ui.DynorixzApp
import com.dynorixz.smarthome.ui.LoadingScreen
import com.dynorixz.smarthome.ui.auth.AuthScreen
import com.dynorixz.smarthome.ui.auth.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var tokenStore: SecureTokenStore
    private var latestIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestIntent = intent
        setContent {
            val tokenState by tokenStore.tokenState.collectAsStateWithLifecycle()
            when (tokenState) {
                TokenState.Loading -> LoadingScreen()
                TokenState.SignedOut -> AuthScreen(hiltViewModel<AuthViewModel>())
                is TokenState.Authenticated -> DynorixzApp(
                    initialDeepLink = latestIntent?.data,
                    initialDestination = latestIntent?.getStringExtra(EXTRA_DESTINATION),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent = intent
        recreate()
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
        const val DESTINATION_TILE_SETTINGS = "tile_settings"
    }
}

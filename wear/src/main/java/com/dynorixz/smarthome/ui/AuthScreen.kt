package com.dynorixz.smarthome.ui.auth

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.dynorixz.smarthome.ui.DynorixzTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val remoteHelper = RemoteActivityHelper(context, ContextCompat.getMainExecutor(context))

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            if (effect is AuthEffect.Open) {
                val intent = Intent(Intent.ACTION_VIEW, effect.uri).addCategory(Intent.CATEGORY_BROWSABLE)
                if (effect.onPhone) {
                    val future = remoteHelper.startRemoteActivity(intent)
                    future.addListener({
                        runCatching { future.get() }.onFailure { context.startActivity(intent) }
                    }, ContextCompat.getMainExecutor(context))
                } else {
                    context.startActivity(intent)
                }
            }
        }
    }

    DynorixzTheme {
        Box(Modifier.fillMaxSize()) {
            TimeText()
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 36.dp),
            ) {
                item {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                item { Text("Устройства", style = MaterialTheme.typography.titleLarge) }
                if (state.error != null) item { Text(state.error!!) }
                item {
                    Button(
                        onClick = { viewModel.login(onPhone = true) },
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Войти") },
                        secondaryLabel = { Text("через Яндекс на телефоне") },
                    )
                }
                item {
                    Text(
                        text = "Войти на часах",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(enabled = !state.loading) {
                            viewModel.login(onPhone = false)
                        },
                    )
                }
                if (state.loading) item { CircularProgressIndicator() }
            }
        }
    }
}

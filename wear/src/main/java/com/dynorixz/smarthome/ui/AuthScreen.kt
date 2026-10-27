package com.dynorixz.smarthome.ui.auth

import android.content.Intent
import android.content.ActivityNotFoundException
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.timeTextCurvedText
import com.dynorixz.smarthome.ui.DynorixzTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    LaunchedEffect(state.error) {
        if (state.error != null) listState.scrollToItem(2)
    }

    DisposableEffect(view, state.loading) {
        val wasKeepingScreenOn = view.keepScreenOn
        view.keepScreenOn = wasKeepingScreenOn || state.loading
        onDispose { view.keepScreenOn = wasKeepingScreenOn }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            if (effect is AuthEffect.Open) {
                val intent = Intent(Intent.ACTION_VIEW, effect.uri).addCategory(Intent.CATEGORY_BROWSABLE)
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    viewModel.browserUnavailable()
                }
            }
        }
    }

    DynorixzTheme {
        Box(Modifier.fillMaxSize()) {
            val clockStyle = TimeTextDefaults.timeTextStyle(color = MaterialTheme.colorScheme.onBackground)
            TimeText { time -> timeTextCurvedText(time, style = clockStyle) }
            ScalingLazyColumn(
                state = listState,
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
                item { Text("Устройства", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge) }
                if (state.error != null) item { Text(state.error!!, color = MaterialTheme.colorScheme.error) }
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
                if (state.loading) {
                    item { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                    item { Text("Продолжите вход в браузере телефона", color = MaterialTheme.colorScheme.onBackground) }
                    item {
                        Text(
                            "Отменить",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.cancelLogin() },
                        )
                    }
                }
            }
        }
    }
}

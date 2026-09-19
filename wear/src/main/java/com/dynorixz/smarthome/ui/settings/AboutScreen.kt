package com.dynorixz.smarthome.ui.settings

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.dynorixz.smarthome.BuildConfig

private const val GITHUB_URL = "https://github.com/Dynorixz"
private const val TELEGRAM_URL = "https://t.me/dynorixz"
private const val DONATE_URL = "https://pay.cloudtips.ru/p/10b062fc"

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val remoteHelper = remember(context) { RemoteActivityHelper(context, executor) }
    var status by remember { mutableStateOf<String?>(null) }

    fun openOnPhone(url: String) {
        status = "Открываем на телефоне…"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val future = remoteHelper.startRemoteActivity(intent)
        future.addListener({
            status = runCatching { future.get() }
                .fold(
                    onSuccess = { "Открыто на телефоне" },
                    onFailure = { "Не удалось открыть · проверьте подключение телефона" },
                )
        }, executor)
    }

    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 36.dp, bottom = 28.dp),
        ) {
            item { Text("О приложении", style = MaterialTheme.typography.titleMedium) }
            item {
                Text(
                    "Dynorixz Smart Home\nверсия ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Button(
                    onClick = { openOnPhone(GITHUB_URL) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("GitHub") },
                    secondaryLabel = { Text("@Dynorixz · на телефоне") },
                )
            }
            item {
                Button(
                    onClick = { openOnPhone(TELEGRAM_URL) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Telegram") },
                    secondaryLabel = { Text("@dynorixz · на телефоне") },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                )
            }
            item {
                Button(
                    onClick = { openOnPhone(DONATE_URL) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Донат") },
                    secondaryLabel = { Text("Поддержать проект · на телефоне") },
                )
            }
            item {
                AnimatedVisibility(
                    visible = status != null,
                    enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
                ) {
                    Text(
                        status.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Text(
                    "Независимый клиент для Яндекс Умного дома",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        TimeText()
    }
}

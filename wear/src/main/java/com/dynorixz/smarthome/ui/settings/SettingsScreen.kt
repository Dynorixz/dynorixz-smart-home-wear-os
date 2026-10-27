package com.dynorixz.smarthome.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.HomeStylePreference
import com.dynorixz.smarthome.data.local.ThemePreference
import com.dynorixz.smarthome.domain.SmartHome

@Composable
fun SettingsScreen(nav: NavHostController, viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 34.dp, bottom = 28.dp),
        ) {
            item { Text("Настройки", style = MaterialTheme.typography.titleMedium) }
            item { Text("Интерфейс", style = MaterialTheme.typography.titleSmall) }
            item {
                SettingButton("Цвета системы", if (state.settings.dynamicColor) "Включено" else "Фирменные") {
                    viewModel.setDynamicColor(!state.settings.dynamicColor)
                }
            }
            item {
                SettingButton("Тема", state.settings.theme.displayName()) {
                    val next = ThemePreference.entries[(state.settings.theme.ordinal + 1) % ThemePreference.entries.size]
                    viewModel.setTheme(next)
                }
            }
            item {
                SettingButton("Главный экран", state.settings.homeStyle.displayName()) {
                    val next = HomeStylePreference.entries[
                        (state.settings.homeStyle.ordinal + 1) % HomeStylePreference.entries.size
                    ]
                    viewModel.setHomeStyle(next)
                }
            }
            item {
                SettingButton("Показывать комнаты", if (state.settings.showRooms) "Да" else "Нет") {
                    viewModel.setShowRooms(!state.settings.showRooms)
                }
            }
            item {
                SettingButton("Показывать сценарии", if (state.settings.showScenarios) "Да" else "Нет") {
                    viewModel.setShowScenarios(!state.settings.showScenarios)
                }
            }
            item {
                SettingButton("Обновление", "Раз в ${state.settings.syncIntervalMinutes} мин") {
                    val next = when (state.settings.syncIntervalMinutes) { 5 -> 15; 15 -> 30; 30 -> 60; else -> 5 }
                    viewModel.setSyncInterval(next)
                }
            }
            item { SettingButton("Плитки часов", "Умный дом · Свет · Датчики") { nav.navigate("tile_settings") } }
            if (state.favorites.isNotEmpty()) {
                item { Text("Порядок избранного", style = MaterialTheme.typography.titleSmall) }
                itemsIndexed(state.favorites, key = { _, ref -> "order:${ref.kind}:${ref.id}" }) { index, ref ->
                    SettingButton("≡ ${ref.name(state.home)}", "Нажмите, чтобы поднять") {
                        if (index > 0) {
                            val reordered = state.favorites.toMutableList()
                            val item = reordered.removeAt(index)
                            reordered.add(index - 1, item)
                            viewModel.reorderFavorites(reordered)
                        }
                    }
                }
                item { Text("Действие по умолчанию", style = MaterialTheme.typography.titleSmall) }
                itemsIndexed(state.favorites, key = { _, ref -> "comp:${ref.kind}:${ref.id}" }) { _, ref ->
                    val selected = ref == state.complicationFavorite
                    SettingButton(ref.name(state.home), if (selected) "Выбрано" else "Выбрать") {
                        viewModel.setComplicationFavorite(if (selected) null else ref)
                    }
                }
            }
            item { Text("Ярлыки на циферблате: удерживайте циферблат → Изменить → выберите кружок → «Действие дома». Прибор или сценарий выбирается отдельно для каждого кружка.", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall) }
            item { Text("Плитки «Умный дом», «Свет» и «Датчики» добавляются в боковую ленту часов. Содержимое выбирается в разделе «Плитки часов».", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall) }
            item {
                Button(
                    onClick = viewModel::logout,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Выйти из Yandex ID") },
                )
            }
            item {
                Button(
                    onClick = { nav.navigate("about") },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("О приложении") },
                    secondaryLabel = { Text("Версия и контакты") },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                )
            }
        }
        TimeText()
    }
}

@Composable
fun TileSettingsScreen(viewModel: SettingsViewModel, initialKind: com.dynorixz.smarthome.data.local.HomeTileKind = com.dynorixz.smarthome.data.local.HomeTileKind.HOME) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TileConfigurationScreen(state, initialKind, viewModel::setTileSelection)
}

@Composable
private fun SettingButton(title: String, secondary: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title) },
        secondaryLabel = { Text(secondary) },
        colors = ButtonDefaults.filledTonalButtonColors(),
    )
}

private fun ThemePreference.displayName(): String = when (this) {
    ThemePreference.SYSTEM -> "Системная"
    ThemePreference.LIGHT -> "Светлая"
    ThemePreference.DARK -> "Тёмная"
}

private fun HomeStylePreference.displayName(): String = when (this) {
    HomeStylePreference.LIST -> "Список"
    HomeStylePreference.DASHBOARD -> "Панель"
    HomeStylePreference.GALLERY -> "Галерея · фото устройств"
}

private fun FavoriteRef.name(home: SmartHome?): String = when (kind) {
    FavoriteKind.DEVICE -> home?.devices?.firstOrNull { it.id == id }?.name
    FavoriteKind.GROUP -> home?.groups?.firstOrNull { it.id == id }?.name
    FavoriteKind.SCENARIO -> home?.scenarios?.firstOrNull { it.id == id }?.name
} ?: id

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
                    val next = when (state.settings.homeStyle) {
                        HomeStylePreference.LIST -> HomeStylePreference.DASHBOARD
                        HomeStylePreference.DASHBOARD -> HomeStylePreference.LIST
                    }
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
                item { Text("Плитка «Избранное» · до 3", style = MaterialTheme.typography.titleSmall) }
                itemsIndexed(state.favorites, key = { _, ref -> "tile:${ref.kind}:${ref.id}" }) { _, ref ->
                    val selected = ref in state.tileFavorites
                    SettingButton(ref.name(state.home), if (selected) "На плитке" else "Добавить") {
                        val next = if (selected) state.tileFavorites - ref else (state.tileFavorites + ref).take(3)
                        viewModel.setTileFavorites(next)
                    }
                }
                item { Text("Виджет быстрого действия", style = MaterialTheme.typography.titleSmall) }
                itemsIndexed(state.favorites, key = { _, ref -> "comp:${ref.kind}:${ref.id}" }) { _, ref ->
                    val selected = ref == state.complicationFavorite
                    SettingButton(ref.name(state.home), if (selected) "Выбрано" else "Выбрать") {
                        viewModel.setComplicationFavorite(if (selected) null else ref)
                    }
                }
            }
            item { Text("Плитка «Свет» и виджет «Статус дома» добавляются через настройку часов", style = MaterialTheme.typography.bodySmall) }
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
fun TileSettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected = state.tileFavorites
    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 34.dp, bottom = 28.dp),
        ) {
            item { Text("Плитка «Умный дом»", style = MaterialTheme.typography.titleMedium) }
            item { Text("Выбрано ${selected.size} из 3", style = MaterialTheme.typography.bodySmall) }

            val home = state.home
            if (home == null) {
                item { Text("Загрузите устройства в приложении") }
            } else {
                if (home.devices.isNotEmpty()) {
                    item { Text("Устройства", style = MaterialTheme.typography.titleSmall) }
                    itemsIndexed(home.devices, key = { _, device -> "tile-device:${device.id}" }) { _, device ->
                        TileChoiceButton(
                            title = device.name,
                            ref = FavoriteRef(FavoriteKind.DEVICE, device.id),
                            selected = selected,
                            onChange = viewModel::setTileFavorites,
                        )
                    }
                }
                if (home.groups.isNotEmpty()) {
                    item { Text("Группы", style = MaterialTheme.typography.titleSmall) }
                    itemsIndexed(home.groups, key = { _, group -> "tile-group:${group.id}" }) { _, group ->
                        TileChoiceButton(
                            title = group.name,
                            ref = FavoriteRef(FavoriteKind.GROUP, group.id),
                            selected = selected,
                            onChange = viewModel::setTileFavorites,
                        )
                    }
                }
                if (home.scenarios.isNotEmpty()) {
                    item { Text("Сценарии", style = MaterialTheme.typography.titleSmall) }
                    itemsIndexed(home.scenarios, key = { _, scenario -> "tile-scenario:${scenario.id}" }) { _, scenario ->
                        TileChoiceButton(
                            title = scenario.name,
                            ref = FavoriteRef(FavoriteKind.SCENARIO, scenario.id),
                            selected = selected,
                            onChange = viewModel::setTileFavorites,
                        )
                    }
                }
            }
        }
        TimeText()
    }
}

@Composable
private fun TileChoiceButton(
    title: String,
    ref: FavoriteRef,
    selected: List<FavoriteRef>,
    onChange: (List<FavoriteRef>) -> Unit,
) {
    val isSelected = ref in selected
    val limitReached = selected.size >= 3
    Button(
        onClick = {
            when {
                isSelected -> onChange(selected - ref)
                !limitReached -> onChange(selected + ref)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = isSelected || !limitReached,
        label = { Text(title) },
        secondaryLabel = {
            Text(
                when {
                    isSelected -> "Добавлено · нажмите, чтобы убрать"
                    limitReached -> "Лимит: 3 действия"
                    else -> "Добавить"
                },
            )
        },
        colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
    )
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
}

private fun FavoriteRef.name(home: SmartHome?): String = when (kind) {
    FavoriteKind.DEVICE -> home?.devices?.firstOrNull { it.id == id }?.name
    FavoriteKind.GROUP -> home?.groups?.firstOrNull { it.id == id }?.name
    FavoriteKind.SCENARIO -> home?.scenarios?.firstOrNull { it.id == id }?.name
} ?: id

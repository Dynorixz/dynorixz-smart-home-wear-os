package com.dynorixz.smarthome.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.*
import com.dynorixz.smarthome.data.local.*
import com.dynorixz.smarthome.ui.device.DevicePage
import com.dynorixz.smarthome.ui.home.galleryStatus

@Composable
internal fun TileConfigurationScreen(state: SettingsUiState, initialKind: HomeTileKind, onChange: (HomeTileKind, List<FavoriteRef>) -> Unit) {
    var kindName by rememberSaveable(initialKind) { mutableStateOf(initialKind.name) }
    val kind = HomeTileKind.valueOf(kindName)
    val stored = when (kind) { HomeTileKind.HOME -> state.tileFavorites; HomeTileKind.LIGHTS -> state.tileLights; HomeTileKind.SENSORS -> state.tileSensors }
    var selected by remember(kind) { mutableStateOf(stored) }
    LaunchedEffect(stored) { selected = stored }
    val choices = buildList {
        state.home?.devices.orEmpty().filter { when (kind) {
            HomeTileKind.HOME -> true
            HomeTileKind.LIGHTS -> "light" in it.type.removePrefix("devices.types.")
            HomeTileKind.SENSORS -> it.properties.isNotEmpty() || "sensor" in it.type
        } }.forEach { add(TileChoice(FavoriteRef(FavoriteKind.DEVICE, it.id), it.name, it.galleryStatus())) }
        if (kind == HomeTileKind.HOME) {
            state.home?.groups.orEmpty().forEach { add(TileChoice(FavoriteRef(FavoriteKind.GROUP, it.id), it.name, "Группа")) }
            state.home?.scenarios.orEmpty().forEach { add(TileChoice(FavoriteRef(FavoriteKind.SCENARIO, it.id), it.name, "Сценарий")) }
        }
    }
    DevicePage(showScrollIndicator = true) {
        item { Text("Плитки часов", fontSize = 16.sp) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                HomeTileKind.entries.forEach { option ->
                    val current = option == kind
                    Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { kindName = option.name }.semantics { this.selected = current }.padding(4.dp), contentAlignment = Alignment.Center) {
                        Text(when (option) { HomeTileKind.HOME -> "Дом"; HomeTileKind.LIGHTS -> "Свет"; HomeTileKind.SENSORS -> "Датчики" },
                            fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
        item { Text("Выбрано ${selected.size} из ${kind.limit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text(if (kind == HomeTileKind.HOME) "Первое действие — большая карточка. Приборы, группы и сценарии — в порядке выбора."
            else "Без выбора — автоматический подбор из избранного и остальных устройств.",
            Modifier.padding(horizontal = 8.dp), fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.home == null) item { Text("Сначала загрузите дом в приложении", fontSize = 12.sp, textAlign = TextAlign.Center) }
        if (state.home != null && choices.isEmpty()) item { Text("Подходящих устройств пока нет", fontSize = 12.sp, textAlign = TextAlign.Center) }
        items(choices, key = { "${kind.name}:${it.ref.kind}:${it.ref.id}" }) { choice ->
            val index = selected.indexOf(choice.ref)
            val checked = index >= 0
            Button(onClick = {
                val next = if (choice.ref in selected) selected - choice.ref else (selected + choice.ref).distinct().take(kind.limit)
                selected = next
                onChange(kind, next)
            }, enabled = checked || selected.size < kind.limit, modifier = Modifier.fillMaxWidth().semantics { this.selected = checked },
                label = { Text(if (checked) "${index + 1}. ${choice.name}" else choice.name) },
                secondaryLabel = { Text(if (checked) "На плитке · убрать" else choice.description) },
                colors = if (checked) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors())
        }
    }
}

private data class TileChoice(val ref: FavoriteRef, val name: String, val description: String)

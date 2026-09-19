@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dynorixz.smarthome.ui

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.HomeStylePreference
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.CapabilityActionFactory
import com.dynorixz.smarthome.domain.Device
import com.dynorixz.smarthome.domain.DeviceGroup
import com.dynorixz.smarthome.domain.DeviceProperty
import com.dynorixz.smarthome.domain.Scenario
import com.dynorixz.smarthome.domain.SmartHome
import com.dynorixz.smarthome.ui.device.DeviceUiState
import com.dynorixz.smarthome.ui.device.DeviceViewModel
import com.dynorixz.smarthome.ui.device.LightDeviceScreen
import com.dynorixz.smarthome.ui.home.HomeUiState
import com.dynorixz.smarthome.ui.home.HomeViewModel
import com.dynorixz.smarthome.ui.settings.SettingsScreen
import com.dynorixz.smarthome.ui.settings.SettingsViewModel
import com.dynorixz.smarthome.ui.settings.TileSettingsScreen
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

private object Route {
    const val HOME = "home"
    const val ROOMS = "rooms"
    const val SCENARIOS = "scenarios"
    const val DEVICES = "devices"
    const val GROUPS = "groups"
    const val SETTINGS = "settings"
    const val TILE_SETTINGS = "tile-settings"
    const val ABOUT = "about"
    const val ROOM = "room/{roomId}"
    const val DEVICE = "device/{deviceId}"
    const val GROUP = "group/{groupId}"
    fun room(id: String) = "room/${Uri.encode(id)}"
    fun device(id: String) = "device/${Uri.encode(id)}"
    fun group(id: String) = "group/${Uri.encode(id)}"
}

@Composable
fun LoadingScreen() {
    DynorixzTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
fun DynorixzApp(
    initialDeepLink: Uri?,
    initialDestination: String? = null,
) {
    val nav = rememberNavController()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialDeepLink, initialDestination) {
        if (initialDestination == com.dynorixz.smarthome.MainActivity.DESTINATION_TILE_SETTINGS) {
            nav.navigate(Route.TILE_SETTINGS)
            return@LaunchedEffect
        }
        val uri = initialDeepLink ?: return@LaunchedEffect
        if (uri.host == "open") {
            val parts = uri.pathSegments
            when (parts.firstOrNull()) {
                "device" -> parts.getOrNull(1)?.let { nav.navigate(Route.device(it)) }
                "room" -> parts.getOrNull(1)?.let { nav.navigate(Route.room(it)) }
                "group" -> parts.getOrNull(1)?.let { nav.navigate(Route.group(it)) }
                "scenarios", "scenario" -> nav.navigate(Route.SCENARIOS)
                "tile" -> nav.navigate(Route.TILE_SETTINGS)
                "settings" -> nav.navigate(Route.SETTINGS)
            }
        }
    }
    DynorixzTheme(dynamicColor = settings.settings.dynamicColor, theme = settings.settings.theme) {
        NavHost(
            navController = nav,
            startDestination = Route.HOME,
            enterTransition = {
                fadeIn(tween(durationMillis = 180, delayMillis = 35)) +
                    slideInHorizontally(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        initialOffsetX = { it / 6 },
                    )
            },
            exitTransition = {
                fadeOut(tween(110)) +
                    slideOutHorizontally(
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        targetOffsetX = { -it / 12 },
                    )
            },
            popEnterTransition = {
                fadeIn(tween(durationMillis = 180, delayMillis = 25)) +
                    slideInHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        initialOffsetX = { -it / 8 },
                    )
            },
            popExitTransition = {
                fadeOut(tween(110)) +
                    slideOutHorizontally(
                        animationSpec = tween(190, easing = FastOutSlowInEasing),
                        targetOffsetX = { it / 7 },
                    )
            },
        ) {
            composable(Route.HOME) { HomeScreen(nav, hiltViewModel()) }
            composable(Route.ROOMS) { RoomsScreen(nav, hiltViewModel()) }
            composable(Route.SCENARIOS) { ScenariosScreen(nav, hiltViewModel()) }
            composable(Route.DEVICES) { DevicesScreen(nav, hiltViewModel()) }
            composable(Route.GROUPS) { GroupsScreen(nav, hiltViewModel()) }
            composable(Route.SETTINGS) { SettingsScreen(nav, settingsViewModel) }
            composable(Route.TILE_SETTINGS) { TileSettingsScreen(settingsViewModel) }
            composable(Route.ABOUT) { com.dynorixz.smarthome.ui.settings.AboutScreen() }
            composable(Route.ROOM, arguments = listOf(navArgument("roomId") { type = NavType.StringType })) {
                RoomScreen(nav, it.arguments?.getString("roomId").orEmpty(), hiltViewModel())
            }
            composable(Route.DEVICE, arguments = listOf(navArgument("deviceId") { type = NavType.StringType })) {
                DeviceScreen(nav, hiltViewModel())
            }
            composable(Route.GROUP, arguments = listOf(navArgument("groupId") { type = NavType.StringType })) {
                GroupScreen(nav, it.arguments?.getString("groupId").orEmpty(), hiltViewModel())
            }
        }
    }
}

@Composable
private fun WearList(
    modifier: Modifier = Modifier,
    state: ScalingLazyListState? = null,
    content: androidx.wear.compose.foundation.lazy.ScalingLazyListScope.() -> Unit,
) {
    val listState = state ?: rememberScalingLazyListState()
    Box(modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 34.dp, bottom = 28.dp),
            content = content,
        )
        TimeText()
    }
}

@Composable
private fun Header(title: String, subtitle: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NavButton(text: String, secondary: String? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text) },
        secondaryLabel = secondary?.let { { Text(it) } },
        colors = ButtonDefaults.filledTonalButtonColors(),
    )
}

@Composable
private fun HomeScreen(nav: NavHostController, viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val refreshThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startAutoRefresh()
                Lifecycle.Event.ON_RESUME -> viewModel.refresh()
                Lifecycle.Event.ON_STOP -> viewModel.stopAutoRefresh()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.stopAutoRefresh()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val pullToRefresh = Modifier.pointerInput(listState, state.refreshing) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val startedAtTop = listState.centerItemIndex == 0
            var deltaX = 0f
            var deltaY = 0f
            var pointer = down
            do {
                val event = awaitPointerEvent(pass = PointerEventPass.Final)
                pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                deltaX += pointer.position.x - pointer.previousPosition.x
                deltaY += pointer.position.y - pointer.previousPosition.y
            } while (pointer.pressed)
            if (
                startedAtTop &&
                !state.refreshing &&
                deltaY > refreshThreshold &&
                deltaY > abs(deltaX) * 1.35f
            ) {
                viewModel.refresh()
            }
        }
    }
    if (state.settings.homeStyle == HomeStylePreference.DASHBOARD) {
        DashboardHomeScreen(
            nav = nav,
            viewModel = viewModel,
            state = state,
            listState = listState,
            modifier = pullToRefresh,
        )
        return
    }
    WearList(modifier = pullToRefresh, state = listState) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Устройства", style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = { nav.navigate(Route.SETTINGS) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Настройки", modifier = Modifier.size(19.dp))
                }
            }
        }
        if (state.loading && state.home == null) item { CircularProgressIndicator(modifier = Modifier.size(22.dp)) }
        if (state.offline) item { StatusText("Нет сети · сохранённые данные") }
        state.error?.let { item { StatusText(it) } }
        state.success?.let { item { StatusText("✓ $it") } }
        if (state.home == null && !state.loading && state.error == null) item { StatusText("Нет устройств") }
        state.home?.let { home ->
            if (state.settings.showRooms && home.rooms.isNotEmpty()) {
                item {
                    NavButton(
                        text = "Комнаты",
                        secondary = "${home.rooms.size} · открыть список",
                    ) { nav.navigate(Route.ROOMS) }
                }
            }
            if (state.settings.showScenarios && home.scenarios.isNotEmpty()) {
                item {
                    NavButton(
                        text = "Сценарии",
                        secondary = "${home.scenarios.size} · быстрый запуск",
                    ) { nav.navigate(Route.SCENARIOS) }
                }
            }
            val favoriteIds = state.favorites
                .filter { it.kind == FavoriteKind.DEVICE }
                .map(FavoriteRef::id)
                .toSet()
            val devices = home.devices.sortedWith(
                compareByDescending<Device> { it.id in favoriteIds }
                    .thenBy { it.name.lowercase() },
            )
            if (devices.isEmpty()) item { StatusText("Нет устройств") }
            items(devices, key = { it.id }) { device ->
                DeviceButton(
                    device = device,
                    nav = nav,
                    viewModel = viewModel,
                    isFavorite = device.id in favoriteIds,
                    quickActionsEnabled = !state.offline && !state.refreshing,
                )
            }
        }
    }
}

@Composable
private fun DashboardHomeScreen(
    nav: NavHostController,
    viewModel: HomeViewModel,
    state: HomeUiState,
    listState: ScalingLazyListState,
    modifier: Modifier,
) {
    WearList(modifier = modifier, state = listState) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Мой дом", style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = { nav.navigate(Route.SETTINGS) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Настройки", modifier = Modifier.size(19.dp))
                }
            }
        }
        if (state.loading && state.home == null) {
            item { CircularProgressIndicator(modifier = Modifier.size(22.dp)) }
        }
        state.home?.let { home ->
            val enabledCount = home.devices.count(Device::isOn)
            val reachableCount = home.devices.count { it.reachable }
            item {
                DashboardStatusCard(
                    deviceCount = home.devices.size,
                    enabledCount = enabledCount,
                    reachableCount = reachableCount,
                    offline = state.offline,
                    onClick = { nav.navigate(Route.DEVICES) },
                )
            }

            val showRooms = state.settings.showRooms && home.rooms.isNotEmpty()
            val showScenarios = state.settings.showScenarios && home.scenarios.isNotEmpty()
            if (showRooms && showScenarios) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DashboardMenuButton(
                            title = "Комнаты",
                            value = home.rooms.size.toString(),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.weight(1f),
                        ) { nav.navigate(Route.ROOMS) }
                        DashboardMenuButton(
                            title = "Сценарии",
                            value = home.scenarios.size.toString(),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.weight(1f),
                        ) { nav.navigate(Route.SCENARIOS) }
                    }
                }
            } else {
                if (showRooms) {
                    item {
                        DashboardMenuButton(
                            title = "Комнаты",
                            value = "${home.rooms.size} разделов",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ) { nav.navigate(Route.ROOMS) }
                    }
                }
                if (showScenarios) {
                    item {
                        DashboardMenuButton(
                            title = "Сценарии",
                            value = "${home.scenarios.size} доступно",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ) { nav.navigate(Route.SCENARIOS) }
                    }
                }
            }
            item {
                DashboardMenuButton(
                    title = "Все устройства",
                    value = "${home.devices.size} · включено $enabledCount",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    compact = true,
                ) { nav.navigate(Route.DEVICES) }
            }

            val quickActions = state.favorites.mapNotNull { it.resolve(home) }.take(3)
            if (quickActions.isNotEmpty()) {
                item { Header("Быстрые действия") }
                items(quickActions, key = ResolvedFavorite::key) { item ->
                    FavoriteButton(item, nav, viewModel)
                }
            }
        }
        if (state.home == null && !state.loading) item { StatusText(state.error ?: "Нет устройств") }
        if (state.offline && state.home != null) item { StatusText("Нет сети · показаны сохранённые данные") }
        state.error?.takeIf { state.home != null }?.let { item { StatusText(it) } }
        state.success?.let { item { StatusText("✓ $it") } }
    }
}

@Composable
private fun DashboardStatusCard(
    deviceCount: Int,
    enabledCount: Int,
    reachableCount: Int,
    offline: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(colors.primaryContainer, colors.secondaryContainer),
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    if (offline) "Дом офлайн" else "Дом в порядке",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
                Text(
                    "$reachableCount из $deviceCount на связи",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(colors.surfaceContainer.copy(alpha = 0.72f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(enabledCount.toString(), style = MaterialTheme.typography.titleLarge)
                    Text("вкл", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DashboardMenuButton(
    title: String,
    value: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 68.dp else 96.dp)
            .clip(RoundedCornerShape(if (compact) 28.dp else 34.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text(value, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun GroupsScreen(nav: NavHostController, viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearList {
        item { Header("Группы") }
        val groups = state.home?.groups.orEmpty()
        if (groups.isEmpty() && state.home != null) item { StatusText("В Яндекс Умном доме нет групп") }
        items(groups, key = { it.id }) { group ->
            Button(
                onClick = { nav.navigate(Route.group(group.id)) },
                onLongClick = { viewModel.toggleFavorite(FavoriteRef(FavoriteKind.GROUP, group.id)) },
                onLongClickLabel = "Добавить в избранное",
                modifier = Modifier.fillMaxWidth(),
                label = { Text(group.name) },
                secondaryLabel = { Text("${group.deviceIds.size} устройств") },
                colors = ButtonDefaults.filledTonalButtonColors(),
            )
        }
    }
}

@Composable
private fun GroupScreen(nav: NavHostController, groupId: String, viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val group = state.home?.groups?.firstOrNull { it.id == groupId }
    LaunchedEffect(groupId) { viewModel.refreshGroup(groupId) }
    WearList {
        item { Header(group?.name ?: "Группа", group?.let { "${it.deviceIds.size} устройств" }) }
        state.error?.let { item { StatusText(it) } }
        state.success?.let { item { StatusText("✓ $it") } }
        if (group == null && state.home != null) item { StatusText("Группа не найдена") }
        group?.let { current ->
            items(current.capabilities.filterNot { it is Capability.Unknown }, key = { "${it.type}:${it.instance}" }) { capability ->
                CapabilityControl(capability, state.refreshing) { action, _ ->
                    viewModel.executeGroup(groupId, action)
                }
            }
            if (current.capabilities.none { it !is Capability.Unknown }) {
                item { StatusText("Публичный API не предоставляет доступных команд для этой группы") }
            }
        }
    }
}

@Composable
private fun RoomsScreen(nav: NavHostController, viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearList {
        item { Header("Комнаты") }
        state.home?.let { home ->
            if (home.rooms.isEmpty()) item { StatusText("В Яндекс Умном доме нет комнат") }
            items(home.rooms, key = { it.id }) { room ->
                val devices = home.devices.filter { it.id in room.deviceIds }
                val on = devices.count(Device::isOn)
                NavButton(room.name, "${devices.size} устройств · включено $on") { nav.navigate(Route.room(room.id)) }
            }
        } ?: item { CircularProgressIndicator() }
    }
}

@Composable
private fun RoomScreen(nav: NavHostController, roomId: String, viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val home = state.home
    val room = home?.rooms?.firstOrNull { it.id == roomId }
    val devices = home?.devices.orEmpty().filter { it.id in room?.deviceIds.orEmpty() }
    WearList {
        item { Header(room?.name ?: "Комната", "${devices.size} устройств") }
        if (devices.isEmpty()) item { StatusText("Нет устройств") }
        items(devices, key = { it.id }) { DeviceButton(it, nav, viewModel) }
    }
}

@Composable
private fun ScenariosScreen(nav: NavHostController, viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearList {
        item { Header("Сценарии") }
        state.success?.let { item { StatusText("✓ $it") } }
        state.error?.let { item { StatusText(it) } }
        val scenarios = state.home?.scenarios.orEmpty()
        if (scenarios.isEmpty() && state.home != null) item { StatusText("В Яндекс Умном доме нет сценариев") }
        items(scenarios, key = { it.id }) { scenario ->
            ScenarioActionButton(
                scenario = scenario,
                enabled = !state.offline && !state.refreshing,
                onExecute = { viewModel.executeScenario(scenario.id) },
                onFavorite = { viewModel.toggleFavorite(FavoriteRef(FavoriteKind.SCENARIO, scenario.id)) },
            )
        }
    }
}

@Composable
internal fun ScenarioActionButton(
    scenario: Scenario,
    enabled: Boolean,
    onExecute: () -> Unit,
    onFavorite: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onExecute()
        },
        onLongClick = onFavorite,
        onLongClickLabel = "Добавить в избранное",
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(scenario.name) },
        secondaryLabel = { Text("Запустить") },
    )
}

private enum class DeviceFilter { ALL, ON, OFF, UNAVAILABLE }

@Composable
private fun DevicesScreen(nav: NavHostController, viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var roomFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    var typeFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    val filter = DeviceFilter.entries[filterIndex]
    val home = state.home
    val roomFilters = listOf<Pair<String?, String>>(null to "все") + home?.rooms.orEmpty().map { it.id to it.name }
    val typeFilters = listOf("Все") + home?.devices.orEmpty().map(Device::category).distinct().sorted()
    val selectedRoom = roomFilters[roomFilterIndex % roomFilters.size]
    val selectedType = typeFilters[typeFilterIndex % typeFilters.size]
    val devices = home?.devices.orEmpty().filter { device ->
        device.name.contains(query, ignoreCase = true) &&
            (selectedRoom.first == null || device.roomId == selectedRoom.first) &&
            (selectedType == "Все" || device.category() == selectedType) && when (filter) {
            DeviceFilter.ALL -> true
            DeviceFilter.ON -> device.isOn()
            DeviceFilter.OFF -> !device.isOn()
            DeviceFilter.UNAVAILABLE -> !device.reachable
        }
    }
    WearList {
        item { Header("Все устройства", "${devices.size}") }
        item {
            BasicTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { contentDescription = "Поиск устройств" },
                decorationBox = { inner ->
                    if (query.isBlank()) Text("Найти устройство")
                    inner()
                },
            )
        }
        item {
            NavButton("Фильтр: ${filter.label()}") { filterIndex = (filterIndex + 1) % DeviceFilter.entries.size }
        }
        item {
            NavButton("Комната: ${selectedRoom.second}") {
                roomFilterIndex = (roomFilterIndex + 1) % roomFilters.size
            }
        }
        item {
            NavButton("Тип: $selectedType") {
                typeFilterIndex = (typeFilterIndex + 1) % typeFilters.size
            }
        }
        if (devices.isEmpty()) item { StatusText("Ничего не найдено") }
        items(devices, key = { it.id }) { DeviceButton(it, nav, viewModel) }
    }
}

@Composable
private fun DeviceButton(
    device: Device,
    nav: NavHostController,
    viewModel: HomeViewModel,
    isFavorite: Boolean = false,
    quickActionsEnabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val onOff = device.capabilities.filterIsInstance<Capability.OnOff>().firstOrNull()
    val canSwipe = onOff != null && device.reachable && quickActionsEnabled
    var dragOffset by remember(device.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(device.id) { mutableStateOf(false) }
    var swipeTurnsOn by remember(device.id) { mutableStateOf(!device.isOn()) }
    val swipeThreshold = with(density) { 44.dp.toPx() }
    val maxOffset = with(density) { 68.dp.toPx() }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (isDragging) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "device swipe offset",
    )
    val swipeProgress = (abs(animatedOffset) / maxOffset).coerceIn(0f, 1f)
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
    val indicatorColor = when {
        !device.reachable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        device.isOn() -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    val actionContainer = if (swipeTurnsOn) primaryContainer else errorContainer
    val actionContent = if (swipeTurnsOn) onPrimaryContainer else onErrorContainer
    val actionAlignment = if (swipeTurnsOn) Alignment.CenterStart else Alignment.CenterEnd
    val actionLabel = if (swipeTurnsOn) "Вкл" else "Выкл"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .pointerInput(device.id, canSwipe, device.isOn()) {
                if (!canSwipe) return@pointerInput
                var thresholdSignaled = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        swipeTurnsOn = !device.isOn()
                        dragOffset = 0f
                        isDragging = true
                        thresholdSignaled = false
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = 0f
                    },
                    onDragEnd = {
                        val commit = abs(dragOffset) >= swipeThreshold
                        if (commit) {
                            viewModel.executeDevice(
                                device.id,
                                CapabilityActionFactory.boolean(onOff, swipeTurnsOn),
                            )
                        }
                        isDragging = false
                        dragOffset = 0f
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val minimum = if (swipeTurnsOn) 0f else -maxOffset
                    val maximum = if (swipeTurnsOn) maxOffset else 0f
                    dragOffset = (dragOffset + dragAmount).coerceIn(minimum, maximum)
                    if (abs(dragOffset) >= swipeThreshold && !thresholdSignaled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        thresholdSignaled = true
                    } else if (abs(dragOffset) < swipeThreshold * 0.72f) {
                        thresholdSignaled = false
                    }
                }
            },
    ) {
        if (canSwipe) {
            Box(
                modifier = Modifier.matchParentSize().background(actionContainer),
                contentAlignment = actionAlignment,
            ) {
                Text(
                    actionLabel,
                    color = actionContent,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .graphicsLayer {
                            alpha = swipeProgress
                            scaleX = 0.9f + 0.1f * swipeProgress
                            scaleY = 0.9f + 0.1f * swipeProgress
                        },
                )
            }
        }
        Button(
            onClick = { nav.navigate(Route.device(device.id)) },
            onLongClick = { viewModel.toggleFavorite(FavoriteRef(FavoriteKind.DEVICE, device.id)) },
            onLongClickLabel = if (isFavorite) "Убрать из избранного" else "Добавить в избранное",
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = animatedOffset },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(indicatorColor, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(device.name)
                }
            },
            secondaryLabel = {
                Text(buildString {
                    append(device.summary())
                    if (isFavorite) append("  ·  ★")
                })
            },
            colors = ButtonDefaults.filledTonalButtonColors(),
        )
    }
}

private sealed interface ResolvedFavorite {
    val key: String
    data class DeviceItem(val device: Device) : ResolvedFavorite { override val key = "d:${device.id}" }
    data class GroupItem(val group: DeviceGroup) : ResolvedFavorite { override val key = "g:${group.id}" }
    data class ScenarioItem(val scenario: Scenario) : ResolvedFavorite { override val key = "s:${scenario.id}" }
}

private fun FavoriteRef.resolve(home: SmartHome): ResolvedFavorite? = when (kind) {
    FavoriteKind.DEVICE -> home.devices.firstOrNull { it.id == id }?.let(ResolvedFavorite::DeviceItem)
    FavoriteKind.GROUP -> home.groups.firstOrNull { it.id == id }?.let(ResolvedFavorite::GroupItem)
    FavoriteKind.SCENARIO -> home.scenarios.firstOrNull { it.id == id }?.let(ResolvedFavorite::ScenarioItem)
}

@Composable
private fun FavoriteButton(item: ResolvedFavorite, nav: NavHostController, viewModel: HomeViewModel) {
    when (item) {
        is ResolvedFavorite.DeviceItem -> {
            val onOff = item.device.capabilities.filterIsInstance<Capability.OnOff>().firstOrNull()
            Button(
                onClick = {
                    if (onOff == null) nav.navigate(Route.device(item.device.id))
                    else viewModel.executeDevice(item.device.id, CapabilityActionFactory.boolean(onOff, !item.device.isOn()))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(item.device.name) },
                secondaryLabel = { Text(if (onOff == null) "Открыть" else if (item.device.isOn()) "Выключить" else "Включить") },
            )
        }
        is ResolvedFavorite.GroupItem -> {
            val onOff = item.group.capabilities.filterIsInstance<Capability.OnOff>().firstOrNull()
            val isOn = onOff?.value?.jsonPrimitive?.booleanOrNull == true
            Button(
                onClick = {
                    if (onOff != null) {
                        viewModel.executeGroup(item.group.id, CapabilityActionFactory.boolean(onOff, !isOn))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = onOff != null,
                label = { Text(item.group.name) },
                secondaryLabel = { Text(if (isOn) "Выключить группу" else "Включить группу") },
            )
        }
        is ResolvedFavorite.ScenarioItem -> Button(
            onClick = { viewModel.executeScenario(item.scenario.id) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(item.scenario.name) },
            secondaryLabel = { Text("Запустить") },
        )
    }
}

@Composable
private fun DeviceScreen(nav: NavHostController, viewModel: DeviceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        DeviceUiState.Loading -> LoadingScreen()
        is DeviceUiState.Error -> WearList {
            item { Header("Устройство") }
            item { StatusText(current.message) }
            item { NavButton("Повторить") { viewModel.refresh() } }
        }
        is DeviceUiState.Content -> {
            val device = current.device
            val isLight = "light" in device.type.removePrefix("devices.types.")
            val hasLightControl = device.capabilities.any {
                (it is Capability.Range && it.instance == "brightness") || it is Capability.ColorSetting
            }
            if (isLight && hasLightControl) {
                LightDeviceScreen(
                    device = device,
                    pendingInstance = current.pendingInstance,
                    error = current.error,
                    execute = viewModel::execute,
                )
            } else {
                WearList {
                    item { Header(device.name, device.type.removePrefix("devices.types.").replace('_', ' ')) }
                    if (!device.reachable) item { StatusText("Устройство недоступно") }
                    current.error?.let { item { StatusText(it) } }
                    items(device.capabilities.filterNot { it is Capability.Unknown }, key = { "${it.type}:${it.instance}" }) { capability ->
                        CapabilityControl(capability, current.pendingInstance == capability.instance, viewModel::execute)
                    }
                    if (device.properties.isNotEmpty()) item { Text("Показания", style = MaterialTheme.typography.titleSmall) }
                    items(device.properties.filterNot { it is DeviceProperty.Unknown }, key = { "${it.type}:${it.instance}" }) {
                        PropertyRow(it)
                    }
                    if (device.capabilities.none { it !is Capability.Unknown } && device.properties.none { it !is DeviceProperty.Unknown }) {
                        item { StatusText("Публичный API не предоставляет доступных элементов управления") }
                    }
                    item { NavButton("Обновить состояние") { viewModel.refresh() } }
                }
            }
        }
    }
}

@Composable
internal fun CapabilityControl(
    capability: Capability,
    pending: Boolean,
    execute: (com.dynorixz.smarthome.domain.CapabilityAction, Long) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    when (capability) {
        is Capability.OnOff -> {
            val enabled = capability.value?.jsonPrimitive?.booleanOrNull == true
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    execute(CapabilityActionFactory.boolean(capability, !enabled), 0)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !pending,
                label = { Text(if (enabled) "Включено" else "Выключено") },
                secondaryLabel = { Text("Питание") },
                colors = if (enabled) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
            )
        }
        is Capability.Range -> {
            val value = capability.value?.jsonPrimitive?.doubleOrNull ?: capability.range.min
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(capability.instance.displayName(), style = MaterialTheme.typography.titleSmall)
                WearValueSlider(
                    value = value,
                    min = capability.range.min,
                    max = capability.range.max,
                    step = capability.range.precision,
                    unit = capability.unit.displayUnit(),
                    enabled = !pending && capability.randomAccess,
                    onValueChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        execute(CapabilityActionFactory.range(capability, it), 350)
                    },
                )
            }
        }
        is Capability.Mode -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(capability.instance.displayName(), style = MaterialTheme.typography.titleSmall)
            capability.modes.forEach { mode ->
                val selected = capability.value?.jsonPrimitive?.content == mode
                Button(
                    onClick = { execute(CapabilityActionFactory.mode(capability, mode), 0) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    enabled = !pending,
                    label = { Text(mode.displayName()) },
                    colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                )
            }
        }
        is Capability.Toggle -> {
            val enabled = capability.value?.jsonPrimitive?.booleanOrNull == true
            Button(
                onClick = { execute(CapabilityActionFactory.boolean(capability, !enabled), 0) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !pending,
                label = { Text(capability.instance.displayName()) },
                secondaryLabel = { Text(if (enabled) "Включено" else "Выключено") },
                colors = if (enabled) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
            )
        }
        is Capability.ColorSetting -> ColorControls(capability, pending, execute)
        is Capability.Unknown -> Unit
    }
}

@Composable
private fun ColorControls(
    capability: Capability.ColorSetting,
    pending: Boolean,
    execute: (com.dynorixz.smarthome.domain.CapabilityAction, Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Цвет", style = MaterialTheme.typography.titleSmall)
        if (capability.colorModel in setOf("rgb", "hsv")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                COLOR_PRESETS.forEach { preset ->
                    val action = if (capability.colorModel == "rgb") {
                        CapabilityActionFactory.color(capability, "rgb", JsonPrimitive(preset.rgb and 0xFFFFFF))
                    } else {
                        CapabilityActionFactory.color(
                            capability,
                            "hsv",
                            JsonObject(mapOf(
                                "h" to JsonPrimitive(preset.hue),
                                "s" to JsonPrimitive(100),
                                "v" to JsonPrimitive(100),
                            )),
                        )
                    }
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(Color(preset.rgb), CircleShape)
                            .clickable(enabled = !pending) { execute(action, 0) }
                            .semantics { contentDescription = preset.name },
                    )
                }
            }
        }
        capability.temperatureRange?.let { range ->
            WearValueSlider(
                value = if (capability.instance == "temperature_k") capability.value?.jsonPrimitive?.doubleOrNull ?: range.min else range.min,
                min = range.min,
                max = range.max,
                step = range.precision,
                unit = "K",
                enabled = !pending,
                onValueChange = {
                    execute(CapabilityActionFactory.color(capability, "temperature_k", JsonPrimitive(it.toInt())), 350)
                },
            )
        }
        capability.scenes.forEach { scene ->
            Button(
                onClick = { execute(CapabilityActionFactory.color(capability, "scene", JsonPrimitive(scene)), 0) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                enabled = !pending,
                label = { Text(scene.displayName()) },
                colors = ButtonDefaults.filledTonalButtonColors(),
            )
        }
    }
}

@Composable
private fun WearValueSlider(
    value: Double,
    min: Double,
    max: Double,
    step: Double,
    unit: String,
    enabled: Boolean,
    onValueChange: (Double) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainer
    val span = (max - min).takeIf { it > 0 } ?: 1.0
    val fraction = ((value - min) / span).coerceIn(0.0, 1.0)
    fun updateValue(raw: Double) {
        val snapped = min + kotlin.math.round((raw - min) / step) * step
        onValueChange(snapped.coerceIn(min, max))
    }
    fun updateFromFraction(newFraction: Float) {
        val raw = min + (max - min) * newFraction.coerceIn(0f, 1f)
        updateValue(raw)
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${value.pretty()} $unit", style = MaterialTheme.typography.titleMedium)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .focusRequester(focusRequester)
                .focusable(enabled)
                .onRotaryScrollEvent {
                    if (enabled) onValueChange((value + if (it.verticalScrollPixels > 0) step else -step).coerceIn(min, max))
                    enabled
                }
                .pointerInput(enabled, min, max) {
                    if (enabled) {
                        detectTapGestures { updateFromFraction(it.x / size.width) }
                    }
                }
                .pointerInput(enabled, min, max) {
                    if (enabled) {
                        detectDragGestures { change, _ -> updateFromFraction(change.position.x / size.width) }
                    }
                }
                .semantics {
                    contentDescription = "Значение ${value.pretty()} $unit"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = value.toFloat(),
                        range = min.toFloat()..max.toFloat(),
                        steps = ((max - min) / step).toInt().minus(1).coerceAtLeast(0),
                    )
                    setProgress { requested ->
                        if (!enabled) false else {
                            updateValue(requested.toDouble())
                            true
                        }
                    }
                },
        ) {
            val y = size.height / 2
            drawLine(track, start = androidx.compose.ui.geometry.Offset(8f, y), end = androidx.compose.ui.geometry.Offset(size.width - 8f, y), strokeWidth = 12f, cap = StrokeCap.Round)
            drawLine(primary, start = androidx.compose.ui.geometry.Offset(8f, y), end = androidx.compose.ui.geometry.Offset(8f + (size.width - 16f) * fraction.toFloat(), y), strokeWidth = 12f, cap = StrokeCap.Round)
            drawCircle(primary, radius = 11f, center = androidx.compose.ui.geometry.Offset(8f + (size.width - 16f) * fraction.toFloat(), y))
        }
    }
    LaunchedEffect(enabled) { if (enabled) focusRequester.requestFocus() }
}

@Composable
private fun PropertyRow(property: DeviceProperty) {
    val text = when (property) {
        is DeviceProperty.FloatValue -> "${property.value?.pretty() ?: "—"} ${property.unit.displayUnit()}"
        is DeviceProperty.Event -> property.value?.displayName() ?: "—"
        is DeviceProperty.Unknown -> return
    }
    Button(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(property.instance.displayName()) },
        secondaryLabel = { Text(text) },
        colors = ButtonDefaults.filledTonalButtonColors(),
    )
}

@Composable
private fun StatusText(text: String) {
    Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
}

@Composable
internal fun OfflineNotice() {
    StatusText("Нет подключения · показываем последнее известное состояние")
}

private data class ColorPreset(val name: String, val rgb: Int, val hue: Int)
private val COLOR_PRESETS = listOf(
    ColorPreset("Тёплый", 0xFFFFB36B.toInt(), 30),
    ColorPreset("Красный", 0xFFFF6B6B.toInt(), 0),
    ColorPreset("Зелёный", 0xFF6DD58C.toInt(), 130),
    ColorPreset("Синий", 0xFF6BA7FF.toInt(), 215),
)

private fun Device.isOn(): Boolean = capabilities.filterIsInstance<Capability.OnOff>()
    .firstOrNull()?.value?.jsonPrimitive?.booleanOrNull == true

private fun Device.summary(): String = when {
    !reachable -> "Недоступно"
    capabilities.any { it is Capability.OnOff } -> if (isOn()) "Включено" else "Выключено"
    properties.isNotEmpty() -> properties.first().instance.displayName()
    else -> type.removePrefix("devices.types.").replace('_', ' ')
}

private fun Device.category(): String {
    val raw = type.removePrefix("devices.types.")
    return when {
        "light" in raw -> "Свет"
        raw in setOf("thermostat", "thermostat.ac", "humidifier", "fan", "heater") -> "Климат"
        "socket" in raw || "switch" in raw -> "Розетки"
        "vacuum" in raw -> "Уборка"
        raw in setOf("tv", "media_device", "smart_speaker", "smart_speaker.yandex.station") -> "Техника"
        else -> raw.replace('_', ' ').replaceFirstChar(Char::uppercase)
    }
}

private fun DeviceFilter.label(): String = when (this) {
    DeviceFilter.ALL -> "все"
    DeviceFilter.ON -> "включено"
    DeviceFilter.OFF -> "выключено"
    DeviceFilter.UNAVAILABLE -> "недоступно"
}

private fun String?.displayUnit(): String = when (this) {
    "unit.percent" -> "%"
    "unit.temperature.celsius" -> "°C"
    "unit.temperature.kelvin" -> "K"
    "unit.pressure.mmhg" -> "мм рт. ст."
    "unit.pressure.pascal" -> "Па"
    "unit.ppm" -> "ppm"
    "unit.watt" -> "Вт"
    "unit.kilowatt_hour" -> "кВт⋅ч"
    "unit.volt" -> "В"
    "unit.ampere" -> "А"
    null -> ""
    else -> removePrefix("unit.").replace('_', ' ')
}

private fun String.displayName(): String = when (this) {
    "brightness" -> "Яркость"
    "temperature", "temperature_k" -> "Температура"
    "volume" -> "Громкость"
    "channel" -> "Канал"
    "humidity" -> "Влажность"
    "open" -> "Открытие"
    "heat" -> "Нагрев"
    "fan_speed" -> "Скорость вентилятора"
    "mute" -> "Без звука"
    "oscillation", "swing" -> "Поворот"
    "battery_level" -> "Батарея"
    "pressure" -> "Давление"
    "co2_level" -> "CO₂"
    "water_level" -> "Уровень воды"
    "illumination" -> "Освещённость"
    "motion" -> "Движение"
    "smoke" -> "Дым"
    "water_leak" -> "Протечка"
    "low" -> "Низкий"
    "medium", "normal" -> "Средний"
    "high" -> "Высокий"
    "auto" -> "Авто"
    "cool" -> "Охлаждение"
    "heat_mode" -> "Обогрев"
    "fan_only" -> "Вентиляция"
    "dry" -> "Осушение"
    else -> replace('_', ' ').replaceFirstChar(Char::uppercase)
}
private fun Double.pretty(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)

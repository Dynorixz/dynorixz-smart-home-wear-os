package com.dynorixz.smarthome.ui.settings

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.dynorixz.smarthome.complication.SmartHomeComplicationService
import com.dynorixz.smarthome.data.local.AppSettings
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.HomeStylePreference
import com.dynorixz.smarthome.data.local.ThemePreference
import com.dynorixz.smarthome.data.local.UserPreferences
import com.dynorixz.smarthome.domain.AuthRepository
import com.dynorixz.smarthome.domain.SmartHome
import com.dynorixz.smarthome.domain.SmartHomeRepository
import com.dynorixz.smarthome.tile.SmartHomeTileService
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val favorites: List<FavoriteRef> = emptyList(),
    val tileFavorites: List<FavoriteRef> = emptyList(),
    val complicationFavorite: FavoriteRef? = null,
    val home: SmartHome? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: UserPreferences,
    private val authRepository: AuthRepository,
    smartHomeRepository: SmartHomeRepository,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        preferences.settings,
        preferences.favorites,
        preferences.tileFavorites,
        preferences.complicationFavorite,
        smartHomeRepository.observeHome(),
    ) { settings, favorites, tile, complication, home ->
        SettingsUiState(settings, favorites, tile, complication, home)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDynamicColor(value: Boolean) = update { copy(dynamicColor = value) }
    fun setTheme(value: ThemePreference) = update { copy(theme = value) }
    fun setHomeStyle(value: HomeStylePreference) = update { copy(homeStyle = value) }
    fun setShowRooms(value: Boolean) = update { copy(showRooms = value) }
    fun setShowScenarios(value: Boolean) = update { copy(showScenarios = value) }
    fun setSyncInterval(value: Int) = update { copy(syncIntervalMinutes = value.coerceIn(1, 60)) }
    fun setTileFavorites(value: List<FavoriteRef>) {
        viewModelScope.launch {
            preferences.setTileFavorites(value)
            TileService.getUpdater(context).requestUpdate(SmartHomeTileService::class.java)
        }
    }

    fun setComplicationFavorite(value: FavoriteRef?) {
        viewModelScope.launch {
            preferences.setComplicationFavorite(value)
            ComplicationDataSourceUpdateRequester.create(
                context,
                ComponentName(context, SmartHomeComplicationService::class.java),
            ).requestUpdateAll()
        }
    }
    fun reorderFavorites(value: List<FavoriteRef>) { viewModelScope.launch { preferences.reorderFavorites(value) } }
    fun logout() { viewModelScope.launch { authRepository.logout() } }

    private fun update(transform: AppSettings.() -> AppSettings) {
        viewModelScope.launch { preferences.updateSettings(state.value.settings.transform()) }
    }
}

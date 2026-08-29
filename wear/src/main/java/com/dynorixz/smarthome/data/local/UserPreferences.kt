package com.dynorixz.smarthome.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferences by preferencesDataStore("user_preferences")

enum class FavoriteKind { DEVICE, GROUP, SCENARIO }
data class FavoriteRef(val kind: FavoriteKind, val id: String)
enum class ThemePreference { SYSTEM, LIGHT, DARK }
enum class HomeStylePreference { LIST, DASHBOARD }

data class AppSettings(
    val dynamicColor: Boolean = true,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val homeStyle: HomeStylePreference = HomeStylePreference.LIST,
    val showRooms: Boolean = true,
    val showScenarios: Boolean = true,
    val syncIntervalMinutes: Int = 5,
)

@Singleton
class UserPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {
    val favorites: Flow<List<FavoriteRef>> = context.userPreferences.data.map { prefs ->
        val order = prefs[FAVORITE_ORDER].orEmpty().split(',').filter(String::isNotBlank)
        val values = prefs[FAVORITES].orEmpty().mapNotNull(::decodeFavorite).associateBy(::encodeFavorite)
        order.mapNotNull(values::get) + values.filterKeys { it !in order }.values
    }

    val tileFavorites: Flow<List<FavoriteRef>> = context.userPreferences.data.map { prefs ->
        prefs[TILE_FAVORITES].orEmpty().mapNotNull(::decodeFavorite).sortedBy(::encodeFavorite)
    }

    val complicationFavorite: Flow<FavoriteRef?> = context.userPreferences.data.map { prefs ->
        prefs[COMPLICATION_FAVORITE]?.let(::decodeFavorite)
    }

    val settings: Flow<AppSettings> = context.userPreferences.data.map { prefs ->
        AppSettings(
            dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
            theme = prefs[THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.SYSTEM,
            homeStyle = prefs[HOME_STYLE]?.let { runCatching { HomeStylePreference.valueOf(it) }.getOrNull() }
                ?: HomeStylePreference.LIST,
            showRooms = prefs[SHOW_ROOMS] ?: true,
            showScenarios = prefs[SHOW_SCENARIOS] ?: true,
            syncIntervalMinutes = (prefs[SYNC_INTERVAL] ?: 5).coerceIn(1, 60),
        )
    }

    suspend fun toggleFavorite(ref: FavoriteRef) = context.userPreferences.edit { prefs ->
        val encoded = encodeFavorite(ref)
        val current = prefs[FAVORITES].orEmpty().toMutableSet()
        if (!current.add(encoded)) current.remove(encoded)
        prefs[FAVORITES] = current
        val order = prefs[FAVORITE_ORDER].orEmpty().split(',').filter(String::isNotBlank).toMutableList()
        if (encoded in current && encoded !in order) order += encoded else if (encoded !in current) order -= encoded
        prefs[FAVORITE_ORDER] = order.joinToString(",")
    }

    suspend fun reorderFavorites(refs: List<FavoriteRef>) = context.userPreferences.edit {
        it[FAVORITE_ORDER] = refs.joinToString(",", transform = ::encodeFavorite)
    }

    suspend fun setTileFavorites(refs: List<FavoriteRef>) = context.userPreferences.edit {
        it[TILE_FAVORITES] = refs.take(3).mapTo(mutableSetOf(), ::encodeFavorite)
    }

    suspend fun setComplicationFavorite(ref: FavoriteRef?) = context.userPreferences.edit {
        if (ref == null) it.remove(COMPLICATION_FAVORITE) else it[COMPLICATION_FAVORITE] = encodeFavorite(ref)
    }

    suspend fun updateSettings(settings: AppSettings) = context.userPreferences.edit {
        it[DYNAMIC_COLOR] = settings.dynamicColor
        it[THEME] = settings.theme.name
        it[HOME_STYLE] = settings.homeStyle.name
        it[SHOW_ROOMS] = settings.showRooms
        it[SHOW_SCENARIOS] = settings.showScenarios
        it[SYNC_INTERVAL] = settings.syncIntervalMinutes
    }

    suspend fun getOrCreateDeviceId(): String {
        var result = ""
        context.userPreferences.edit { prefs ->
            result = prefs[DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[DEVICE_ID] = it }
        }
        return result
    }

    companion object {
        fun encodeFavorite(ref: FavoriteRef): String = "${ref.kind.name}|${ref.id}"
        fun decodeFavorite(value: String): FavoriteRef? {
            val parts = value.split('|', limit = 2)
            if (parts.size != 2 || parts[1].isBlank()) return null
            return runCatching { FavoriteRef(FavoriteKind.valueOf(parts[0]), parts[1]) }.getOrNull()
        }

        private val FAVORITES = stringSetPreferencesKey("favorites")
        private val FAVORITE_ORDER = stringPreferencesKey("favorite_order")
        private val TILE_FAVORITES = stringSetPreferencesKey("tile_favorites")
        private val COMPLICATION_FAVORITE = stringPreferencesKey("complication_favorite")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val THEME = stringPreferencesKey("theme")
        private val HOME_STYLE = stringPreferencesKey("home_style")
        private val SHOW_ROOMS = booleanPreferencesKey("show_rooms")
        private val SHOW_SCENARIOS = booleanPreferencesKey("show_scenarios")
        private val SYNC_INTERVAL = intPreferencesKey("sync_interval")
        private val DEVICE_ID = stringPreferencesKey("oauth_device_id")
    }
}

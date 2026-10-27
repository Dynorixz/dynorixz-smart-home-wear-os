package com.dynorixz.smarthome.complication

import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.UserPreferences

enum class ShortcutAction(val label: String) {
    TOGGLE("Переключить"), ON("Включить"), OFF("Выключить"),
    OPEN("Открыть управление"), SCENARIO("Запустить сценарий"),
}

data class ComplicationChoice(val ref: FavoriteRef, val action: ShortcutAction) {
    fun encode(): String = "${action.name}|${UserPreferences.encodeFavorite(ref)}"

    companion object {
        fun default(ref: FavoriteRef) = ComplicationChoice(
            ref, if (ref.kind == FavoriteKind.SCENARIO) ShortcutAction.SCENARIO else ShortcutAction.TOGGLE,
        )

        fun decode(value: String): ComplicationChoice? {
            val parts = value.split('|', limit = 2)
            if (parts.size != 2) return null
            val action = runCatching { ShortcutAction.valueOf(parts[0]) }.getOrNull() ?: return null
            val ref = UserPreferences.decodeFavorite(parts[1]) ?: return null
            if ((ref.kind == FavoriteKind.SCENARIO) != (action == ShortcutAction.SCENARIO)) return null
            return ComplicationChoice(ref, action)
        }
    }
}

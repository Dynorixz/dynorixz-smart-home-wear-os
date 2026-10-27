package com.dynorixz.smarthome.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.dynorixz.smarthome.R
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.UserPreferences
import com.dynorixz.smarthome.di.wearSurfaceDependencies
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.SmartHome
import com.dynorixz.smarthome.domain.SmartHomeRepository
import com.dynorixz.smarthome.tile.TileActionActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

class SmartHomeComplicationService : SuspendingComplicationDataSourceService() {
    private val dependencies by lazy(LazyThreadSafetyMode.NONE) { wearSurfaceDependencies() }
    private val preferences: UserPreferences get() = dependencies.preferences()
    private val repository: SmartHomeRepository get() = dependencies.repository()

    override fun getPreviewData(type: ComplicationType): ComplicationData? = buildData(
        type,
        ComplicationPresentation("Вкл", "Лампа · включена", "Лампа: переключить", 72f, "Лампа", R.drawable.ic_shortcut_light),
        null, 0,
    )

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val snapshot = runCatching {
            withTimeoutOrNull(1_500) {
                preferences.complicationChoice(request.complicationInstanceId).first() to repository.observeHome().first()
            }
        }.getOrNull()
        val choice = snapshot?.first
        val home = snapshot?.second
        val presentation = choice?.ref?.presentation(home)?.let {
            it.copy(description = "${it.description}. ${choice.action.label}")
        } ?: ComplicationPresentation("Выбор", "Выбрать действие", "Настроить ярлык дома")
        return buildData(request.complicationType, presentation, choice, request.complicationInstanceId)
    }

    internal fun buildData(
        type: ComplicationType,
        presentation: ComplicationPresentation,
        choice: ComplicationChoice?,
        instanceId: Int,
    ): ComplicationData {
        val description = PlainComplicationText.Builder(presentation.description).build()
        val tapAction = PendingIntent.getActivity(
            this,
            instanceId,
            if (choice == null) Intent(this, ComplicationConfigActivity::class.java).apply {
                data = android.net.Uri.parse("dynorixzsmarthome://configure/$instanceId")
                putExtra(EXTRA_CONFIG_COMPLICATION_ID, instanceId)
            }
            else Intent(this, TileActionActivity::class.java).apply {
                data = android.net.Uri.parse("dynorixzsmarthome://complication/$instanceId")
                putExtra(TileActionActivity.EXTRA_KIND, choice.ref.kind.name)
                putExtra(TileActionActivity.EXTRA_ID, choice.ref.id)
                putExtra(TileActionActivity.EXTRA_SOURCE, TileActionActivity.SOURCE_COMPLICATION)
                putExtra(TileActionActivity.EXTRA_COMPLICATION_ID, instanceId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(this, presentation.icon)
        val image = MonochromaticImage.Builder(icon).build()
        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                PlainComplicationText.Builder(presentation.longText).build(), description,
            ).setMonochromaticImage(image).setTapAction(tapAction).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                presentation.rangeValue ?: 0f, 0f, 100f, description,
            )
                .setText(PlainComplicationText.Builder(presentation.shortText).build())
                .setMonochromaticImage(image)
                .setTapAction(tapAction)
                .build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                image, description,
            ).setTapAction(tapAction).build()
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                SmallImage.Builder(icon, SmallImageType.ICON).build(), description,
            ).setTapAction(tapAction).build()
            else -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(presentation.shortText.take(7)).build(), description,
            ).setTitle(PlainComplicationText.Builder(presentation.title.take(7)).build())
                .setMonochromaticImage(image).setTapAction(tapAction).build()
        }
    }
}

internal data class ComplicationPresentation(
    val shortText: String,
    val longText: String,
    val description: String,
    val rangeValue: Float? = null,
    val title: String = "Дом",
    val icon: Int = R.drawable.ic_shortcut_power,
)

private fun FavoriteRef.presentation(home: SmartHome?): ComplicationPresentation = when (kind) {
    FavoriteKind.DEVICE -> home?.devices?.firstOrNull { it.id == id }?.let { device ->
        val on = device.capabilities.filterIsInstance<Capability.OnOff>()
            .firstOrNull()?.value?.jsonPrimitive?.booleanOrNull
        val brightness = device.capabilities.filterIsInstance<Capability.Range>()
            .firstOrNull { it.instance == "brightness" }
            ?.value?.jsonPrimitive?.doubleOrNull?.toFloat()
        val state = when {
            !device.reachable -> "Офлайн"
            on == true -> "Вкл"
            on == false -> "Выкл"
            else -> device.name.take(7)
        }
        ComplicationPresentation(
            shortText = state,
            longText = "${device.name} · $state",
            description = "${device.name}: $state",
            rangeValue = if (on == false || !device.reachable) 0f else brightness ?: if (on == true) 100f else 0f,
            title = device.name,
            icon = if ("light" in device.type) R.drawable.ic_shortcut_light else R.drawable.ic_shortcut_power,
        )
    }
    FavoriteKind.GROUP -> home?.groups?.firstOrNull { it.id == id }?.let { group ->
        val on = group.capabilities.filterIsInstance<Capability.OnOff>()
            .firstOrNull()?.value?.jsonPrimitive?.booleanOrNull == true
        ComplicationPresentation(
            if (on) "Вкл" else "Выкл",
            "${group.name} · ${if (on) "Вкл" else "Выкл"}",
            group.name,
            rangeValue = if (on) 100f else 0f,
            title = group.name,
        )
    }
    FavoriteKind.SCENARIO -> home?.scenarios?.firstOrNull { it.id == id }?.let {
        ComplicationPresentation("Пуск", it.name, "Запустить ${it.name}", title = it.name, icon = R.drawable.ic_shortcut_scenario)
    }
} ?: ComplicationPresentation("Дом", name(home), "Умный дом")

private fun FavoriteRef.name(home: SmartHome?): String = when (kind) {
    FavoriteKind.DEVICE -> home?.devices?.firstOrNull { it.id == id }?.name
    FavoriteKind.GROUP -> home?.groups?.firstOrNull { it.id == id }?.name
    FavoriteKind.SCENARIO -> home?.scenarios?.firstOrNull { it.id == id }?.name
} ?: "Умный дом"

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
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.dynorixz.smarthome.MainActivity
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
        ComplicationPresentation("Вкл", "Лампа · включена", "Умный дом", 72f),
        null,
    )

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val snapshot = runCatching {
            withTimeoutOrNull(1_500) {
                preferences.complicationFavorite.first() to repository.observeHome().first()
            }
        }.getOrNull()
        val ref = snapshot?.first
        val home = snapshot?.second
        val presentation = ref?.presentation(home)
            ?: ComplicationPresentation("Дом", "Умный дом", "Открыть умный дом")
        return buildData(request.complicationType, presentation, ref)
    }

    private fun buildData(
        type: ComplicationType,
        presentation: ComplicationPresentation,
        ref: FavoriteRef?,
    ): ComplicationData {
        val description = PlainComplicationText.Builder(presentation.description).build()
        val tapAction = PendingIntent.getActivity(
            this,
            ref?.hashCode() ?: 0,
            if (ref == null) Intent(this, MainActivity::class.java)
            else Intent(this, TileActionActivity::class.java).apply {
                putExtra(TileActionActivity.EXTRA_KIND, ref.kind.name)
                putExtra(TileActionActivity.EXTRA_ID, ref.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val image = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_home)).build()
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
            else -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(presentation.shortText.take(7)).build(), description,
            ).setMonochromaticImage(image).setTapAction(tapAction).build()
        }
    }
}

private data class ComplicationPresentation(
    val shortText: String,
    val longText: String,
    val description: String,
    val rangeValue: Float? = null,
)

private fun FavoriteRef.presentation(home: SmartHome?): ComplicationPresentation = when (kind) {
    FavoriteKind.DEVICE -> home?.devices?.firstOrNull { it.id == id }?.let { device ->
        val on = device.capabilities.filterIsInstance<Capability.OnOff>()
            .firstOrNull()?.value?.jsonPrimitive?.booleanOrNull
        val brightness = device.capabilities.filterIsInstance<Capability.Range>()
            .firstOrNull { it.instance == "brightness" }
            ?.value?.jsonPrimitive?.doubleOrNull?.toFloat()
        val state = when {
            !device.reachable -> "Нет"
            on == true -> "Вкл"
            on == false -> "Выкл"
            else -> device.name.take(7)
        }
        ComplicationPresentation(
            shortText = brightness?.let { "${it.toInt()}%" } ?: state,
            longText = "${device.name} · $state",
            description = "${device.name}: $state",
            rangeValue = brightness,
        )
    }
    FavoriteKind.GROUP -> home?.groups?.firstOrNull { it.id == id }?.let { group ->
        val on = group.capabilities.filterIsInstance<Capability.OnOff>()
            .firstOrNull()?.value?.jsonPrimitive?.booleanOrNull == true
        ComplicationPresentation(
            if (on) "Вкл" else "Выкл",
            "${group.name} · ${if (on) "Вкл" else "Выкл"}",
            group.name,
        )
    }
    FavoriteKind.SCENARIO -> home?.scenarios?.firstOrNull { it.id == id }?.let {
        ComplicationPresentation("Пуск", it.name, "Запустить ${it.name}")
    }
} ?: ComplicationPresentation("Дом", name(home), "Умный дом")

private fun FavoriteRef.name(home: SmartHome?): String = when (kind) {
    FavoriteKind.DEVICE -> home?.devices?.firstOrNull { it.id == id }?.name
    FavoriteKind.GROUP -> home?.groups?.firstOrNull { it.id == id }?.name
    FavoriteKind.SCENARIO -> home?.scenarios?.firstOrNull { it.id == id }?.name
} ?: "Умный дом"

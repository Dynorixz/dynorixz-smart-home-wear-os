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
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.SmartHomeRepository
import com.dynorixz.smarthome.di.wearSurfaceDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

class HomeStatusComplicationService : SuspendingComplicationDataSourceService() {
    private val repository: SmartHomeRepository by lazy(LazyThreadSafetyMode.NONE) {
        wearSurfaceDependencies().repository()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData = buildData(type, 3, 8)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val devices = runCatching {
            withTimeoutOrNull(1_500) { repository.observeHome().first() }
        }.getOrNull()?.devices.orEmpty()
        val controllable = devices.filter { device -> device.capabilities.any { it is Capability.OnOff } }
        val enabled = controllable.count { device ->
            device.capabilities.filterIsInstance<Capability.OnOff>()
                .firstOrNull()?.value?.jsonPrimitive?.booleanOrNull == true
        }
        return buildData(request.complicationType, enabled, controllable.size)
    }

    private fun buildData(type: ComplicationType, enabled: Int, total: Int): ComplicationData {
        val short = if (total == 0) "—" else "$enabled вкл"
        val long = if (total == 0) "Нет устройств" else "$enabled из $total включено"
        val description = PlainComplicationText.Builder("Умный дом: $long").build()
        val tapAction = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val image = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_home)).build()
        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                PlainComplicationText.Builder(long).build(), description,
            ).setMonochromaticImage(image).setTapAction(tapAction).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                enabled.toFloat(), 0f, total.coerceAtLeast(1).toFloat(), description,
            )
                .setText(PlainComplicationText.Builder(short).build())
                .setMonochromaticImage(image)
                .setTapAction(tapAction)
                .build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                image, description,
            ).setTapAction(tapAction).build()
            else -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(short).build(), description,
            ).setMonochromaticImage(image).setTapAction(tapAction).build()
        }
    }
}

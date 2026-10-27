package com.dynorixz.smarthome.tile

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders.*
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.*
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ModifiersBuilders.*
import androidx.wear.protolayout.ResourceBuilders.*
import androidx.wear.protolayout.layout.basicImage
import androidx.wear.protolayout.material3.MaterialScope
import com.dynorixz.smarthome.MainActivity
import com.dynorixz.smarthome.R
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.HomeTileKind

private const val BACKGROUND = 0xFF090B0C.toInt()
private const val SURFACE = 0xFF181F22.toInt()
private const val MINT = 0xFF8BCDBB.toInt()
private const val FOREGROUND = 0xFFE3E7E5.toInt()
private const val MUTED = 0xFFBEC9C5.toInt()
private const val AMBER = 0xFFFFCF88.toInt()
private const val ALERT = 0xFFFFA79E.toInt()

internal fun MaterialScope.homeTileLayout(model: HomeTileModel): LayoutElement {
    val width = deviceConfiguration.screenWidthDp.toFloat().coerceAtLeast(160f)
    val content = Column.Builder().setWidth(expand()).setHeight(expand()).setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
        .setModifiers(Modifiers.Builder().setPadding(padding(12f, 20f, 12f, 22f)).build())
        .addContent(Row.Builder().setWidth(dp(width * .72f)).setHeight(dp(44f)).setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(Column.Builder().setWidth(expand()).setHeight(wrap()).setHorizontalAlignment(HORIZONTAL_ALIGN_START)
                .addContent(label(model.title, 13f, MINT, bold = true))
                .addContent(vSpace(2f)).addContent(label(if (model.hasHome) "Снимок · ${model.freshness}" else "Войдите в Home", 9f, MUTED)).build())
            .addContent(Box.Builder().setWidth(dp(44f)).setHeight(dp(44f))
                .setModifiers(cardModifiers(SURFACE, MINT, tileSettings(model.kind), "Настроить плитку ${model.title}", 22f))
                .addContent(art(R.drawable.ic_tile_settings, 20f)).build()).build())
        .addContent(vSpace(4f))
    val body: LayoutElement = if (model.items.isEmpty()) emptyCard(model) else if (model.kind == HomeTileKind.SENSORS) {
        Column.Builder().setWidth(expand()).setHeight(expand()).apply {
            model.items.forEachIndexed { index, item ->
                if (index > 0) addContent(vSpace(6f))
                addContent(sensorCard(item, weight(1f), large = model.items.size == 1))
            }
        }.build()
    } else if (model.items.size < 3) {
        Column.Builder().setWidth(expand()).setHeight(expand()).apply {
            model.items.forEachIndexed { index, item ->
                if (index > 0) addContent(vSpace(6f))
                addContent(actionCard(item, weight(1f), mini = false, large = model.items.size == 1))
            }
        }.build()
    } else {
        Column.Builder().setWidth(expand()).setHeight(expand())
            .addContent(actionCard(model.items[0], weight(1f), mini = false))
            .addContent(vSpace(6f))
            .addContent(Row.Builder().setWidth(dp(width - 48f)).setHeight(weight(1f))
                .addContent(actionCard(model.items[1], expand(), mini = true))
                .addContent(hSpace(6f)).addContent(actionCard(model.items[2], expand(), mini = true)).build()).build()
    }
    content.addContent(body)
    return Box.Builder().setWidth(expand()).setHeight(expand())
        .setModifiers(Modifiers.Builder().setBackground(Background.Builder().setColor(argb(BACKGROUND)).build()).build())
        .addContent(content.build()).build()
}

private fun MaterialScope.actionCard(item: HomeTileItem, height: ContainerDimension, mini: Boolean, large: Boolean = false): LayoutElement {
    val accent = if (item.alert) ALERT else if (item.tap == TileTap.SCENARIO) AMBER else MINT
    val imageSize = if (mini) 16f else if (large) 48f else 30f
    val inner: LayoutElement = if (mini) {
        Column.Builder().setWidth(expand()).setHeight(wrap()).setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(art(item.image, imageSize)).addContent(vSpace(2f))
            .addContent(label(item.title, 10f, FOREGROUND, center = true, bold = true))
            .addContent(label(item.status, 9f, accent, center = true)).build()
    } else {
        Row.Builder().setWidth(expand()).setHeight(expand()).setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(art(item.image, imageSize)).addContent(hSpace(7f))
            .addContent(Column.Builder().setWidth(expand()).setHeight(wrap()).setHorizontalAlignment(HORIZONTAL_ALIGN_START)
                .addContent(label(item.title, if (large) 15f else 12f, FOREGROUND, bold = true, lines = if (large) 2 else 1))
                .addContent(vSpace(2f)).addContent(label(item.status, if (large) 12f else 10f, accent)).build())
            .addContent(hSpace(3f)).addContent(label(when (item.tap) { TileTap.OPEN -> "›"; TileTap.SCENARIO -> "▶"; TileTap.TOGGLE -> if (item.active) "●" else "○" }, 16f, accent)).build()
    }
    return Box.Builder().setWidth(if (mini) weight(1f) else expand()).setHeight(height)
        .setModifiers(cardModifiers(if (item.active) 0xFF17372F.toInt() else SURFACE, accent, itemClick(item), item.description, if (mini) 18f else 22f,
            horizontal = if (mini) 4f else 12f, vertical = if (mini) 3f else 6f))
        .addContent(inner).build()
}

private fun MaterialScope.sensorCard(item: HomeTileItem, height: ContainerDimension, large: Boolean): LayoutElement {
    val accent = if (item.alert) ALERT else AMBER
    val textSize = if (large) 28f else 22f
    val text = item.reading ?: "Нет данных"
    val content = Column.Builder().setWidth(expand()).setHeight(wrap()).setHorizontalAlignment(HORIZONTAL_ALIGN_START)
        .addContent(label(item.title, 10f, MUTED, lines = 1)).addContent(vSpace(if (large) 5f else 2f))
        .addContent(label(text, if (text.length > 12) 16f else textSize, accent, bold = true))
    if (large && item.secondary != null) content.addContent(vSpace(5f)).addContent(label(item.secondary, 11f, MUTED))
    return Box.Builder().setWidth(expand()).setHeight(height)
        .setModifiers(cardModifiers(SURFACE, accent, itemClick(item), item.description, 23f, 14f, 6f))
        .addContent(content.build()).build()
}

private fun MaterialScope.emptyCard(model: HomeTileModel): LayoutElement = Box.Builder().setWidth(expand()).setHeight(expand())
    .setModifiers(cardModifiers(SURFACE, MINT, tileSettings(model.kind), "Открыть настройки плитки", 24f, 18f, 10f))
    .addContent(Column.Builder().setWidth(expand()).setHeight(wrap()).setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
        .addContent(label(if (model.hasHome) "＋" else "⌂", 28f, MINT, center = true)).addContent(vSpace(6f))
        .addContent(label(if (!model.hasHome) "Войдите в Home" else when (model.kind) {
            HomeTileKind.HOME -> "Выберите действия"; HomeTileKind.LIGHTS -> "Добавьте лампы"; HomeTileKind.SENSORS -> "Выберите датчики"
        }, 13f, FOREGROUND, center = true, lines = 2)).build()).build()

private fun MaterialScope.art(drawable: Int, size: Float): LayoutElement = protoLayoutScope.basicImage(
    resource = ImageResource.Builder().setAndroidResourceByResId(AndroidImageResourceByResId.Builder().setResourceId(drawable).build()).build(),
    width = dp(size), height = dp(size), contentScaleMode = CONTENT_SCALE_MODE_FIT)

private fun MaterialScope.tileSettings(kind: HomeTileKind): Clickable = clickableFor("settings:${kind.name}", launchAction(ComponentName(context, MainActivity::class.java),
    mapOf(MainActivity.EXTRA_DESTINATION to stringExtra(MainActivity.DESTINATION_TILE_SETTINGS), MainActivity.EXTRA_TILE_KIND to stringExtra(kind.name))))

private fun MaterialScope.itemClick(item: HomeTileItem): Clickable {
    val ref = item.ref
    val action = if (item.tap == TileTap.OPEN) {
        val path = if (ref.kind == FavoriteKind.GROUP) "group" else "device"
        launchAction(ComponentName(context, MainActivity::class.java), mapOf(MainActivity.EXTRA_TILE_URI to stringExtra("dynorixzsmarthome://open/$path/${android.net.Uri.encode(ref.id)}")))
    } else launchAction(ComponentName(context, TileActionActivity::class.java), mapOf(
        TileActionActivity.EXTRA_KIND to stringExtra(ref.kind.name), TileActionActivity.EXTRA_ID to stringExtra(ref.id),
        TileActionActivity.EXTRA_SOURCE to stringExtra(if (item.source == HomeTileKind.LIGHTS) TileActionActivity.SOURCE_LIGHTS else TileActionActivity.SOURCE_TILE)))
    return clickableFor("${ref.kind}:${ref.id}:${item.tap}", action)
}

private fun clickableFor(id: String, action: Action): Clickable = Clickable.Builder().setId(id).setOnClick(action).build()
private fun label(value: String, size: Float, color: Int, center: Boolean = false, bold: Boolean = false, lines: Int = 1): LayoutElement = Text.Builder()
    .setText(value).setMaxLines(lines).setOverflow(TEXT_OVERFLOW_ELLIPSIZE)
    .setMultilineAlignment(if (center) TEXT_ALIGN_CENTER else TEXT_ALIGN_START)
    .setFontStyle(FontStyle.Builder().setSize(sp(size)).setColor(argb(color)).setWeight(if (bold) FONT_WEIGHT_BOLD else FONT_WEIGHT_NORMAL).build()).build()
private fun padding(left: Float, top: Float, right: Float, bottom: Float) = Padding.Builder().setStart(dp(left)).setEnd(dp(right)).setTop(dp(top)).setBottom(dp(bottom)).build()
private fun cardModifiers(background: Int, accent: Int, click: Clickable, description: String, radius: Float, horizontal: Float = 0f, vertical: Float = 0f): Modifiers = Modifiers.Builder()
    .setBackground(Background.Builder().setColor(argb(background)).setCorner(Corner.Builder().setRadius(dp(radius)).build()).build())
    .setBorder(Border.Builder().setWidth(dp(.7f)).setColor(argb((accent and 0xFFFFFF) or 0x55000000)).build())
    .setPadding(padding(horizontal, vertical, horizontal, vertical)).setClickable(click)
    .setSemantics(Semantics.Builder().setContentDescription(description).build()).build()
private fun vSpace(size: Float): LayoutElement = Spacer.Builder().setHeight(dp(size)).build()
private fun hSpace(size: Float): LayoutElement = Spacer.Builder().setWidth(dp(size)).build()

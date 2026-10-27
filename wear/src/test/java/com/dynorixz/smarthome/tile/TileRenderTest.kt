package com.dynorixz.smarthome.tile

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.wear.protolayout.DeviceParametersBuilders.*
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.protolayout.material3.materialScopeWithResources
import androidx.wear.tiles.renderer.TileRenderer
import com.dynorixz.smarthome.MainActivity
import com.dynorixz.smarthome.data.local.*
import com.dynorixz.smarthome.domain.DeviceProperty
import com.dynorixz.smarthome.ui.device.sensorFixture
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w192dp-h192dp-round-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TileRenderTest {
    @Test fun renderHome() { render("tile-home", homeModel()) }
    @Test fun renderLights() { render("tile-lights", tileModel(HomeTileKind.LIGHTS, tileHome(), now = 1_000_000)) }
    @Test fun renderClimate() { render("tile-sensors", tileModel(HomeTileKind.SENSORS, tileHome(), now = 1_000_000)) }
    @Test fun renderTwoSensors() {
        val home = tileHome().let { it.copy(devices = it.devices + sensorFixture("Окно", listOf(DeviceProperty.Event("open", "closed", emptyList(), true, true, null))).copy(id = "window")) }
        render("tile-two-sensors", tileModel(HomeTileKind.SENSORS, home, now = 1_000_000))
    }
    @Test fun renderEmptyHome() { render("tile-empty", tileModel(HomeTileKind.HOME, tileHome(), now = 1_000_000)) }
    @Test fun renderSignedOut() { render("tile-sign-in", tileModel(HomeTileKind.HOME, null)) }
    @Test fun renderCachedState() { render("tile-cached", tileModel(HomeTileKind.LIGHTS, tileHome().copy(isStale = true))) }
    @Test @Config(qualifiers = "w227dp-h227dp-round-xhdpi") fun renderLargeWatchAndFont() { render("tile-large-font", homeModel(), size = 227, fontScale = 1.3f) }
    @Test fun aLightTapCarriesTheCorrectDeviceAndSource() {
        val model = tileModel(HomeTileKind.LIGHTS, tileHome(), now = 1_000_000)
        val rendered = render("tile-light-action", model)
        val target = find(rendered.parent, model.items.first().description) ?: error("Light target not rendered")
        assertTrue(target.performClick())
        val intent = shadowOf(rendered.activity).nextStartedActivity
        assertEquals(TileActionActivity::class.java.name, intent.component?.className)
        assertEquals("light", intent.getStringExtra(TileActionActivity.EXTRA_ID))
        assertEquals(TileActionActivity.SOURCE_LIGHTS, intent.getStringExtra(TileActionActivity.EXTRA_SOURCE))
    }
    @Test fun sensorTapOpensDetailsAndCannotTogglePower() {
        val model = tileModel(HomeTileKind.SENSORS, tileHome(), now = 1_000_000)
        val rendered = render("tile-sensor-action", model)
        assertTrue(find(rendered.parent, model.items.first().description)!!.performClick())
        val intent = shadowOf(rendered.activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals("dynorixzsmarthome://open/device/sensor", intent.getStringExtra(MainActivity.EXTRA_TILE_URI))
    }
    @Test fun settingsTapOpensTheMatchingTileConfiguration() {
        val model = tileModel(HomeTileKind.SENSORS, tileHome(), now = 1_000_000)
        val rendered = render("tile-config-action", model)
        assertTrue(find(rendered.parent, "Настроить плитку Датчики")!!.performClick())
        val intent = shadowOf(rendered.activity).nextStartedActivity
        assertEquals(HomeTileKind.SENSORS.name, intent.getStringExtra(MainActivity.EXTRA_TILE_KIND))
        assertEquals(MainActivity.DESTINATION_TILE_SETTINGS, intent.getStringExtra(MainActivity.EXTRA_DESTINATION))
    }
    @Test fun threeFavoriteTargetsRemainLargeEnoughOnTheSmallRoundDisplay() {
        val model = homeModel()
        val rendered = render("tile-targets", model)
        model.items.forEach {
            val target = find(rendered.parent, it.description) ?: error("Favorite target missing")
            assertTrue(target.width >= 88, "Target width: ${target.width}")
            assertTrue(target.height >= 88, "Target height: ${target.height}")
        }
        val settings = find(rendered.parent, "Настроить плитку Умный дом")!!
        assertTrue(settings.width >= 88 && settings.height >= 88)
    }

    private fun homeModel() = tileModel(HomeTileKind.HOME, tileHome(), listOf(FavoriteRef(FavoriteKind.DEVICE, "light"),
        FavoriteRef(FavoriteKind.SCENARIO, "night"), FavoriteRef(FavoriteKind.DEVICE, "lamp2")), now = 1_000_000)
    private data class Rendered(val activity: Activity, val parent: FrameLayout)
    private fun render(name: String, model: HomeTileModel, size: Int = 192, fontScale: Float = 1f): Rendered {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val config = activity.resources.configuration.apply { this.fontScale = fontScale }
        @Suppress("DEPRECATION") activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
        val pixels = size * 2
        val parent = FrameLayout(activity)
        activity.setContentView(parent)
        val scope = ProtoLayoutScope()
        val parameters = DeviceParameters.Builder().setScreenWidthDp(size).setScreenHeightDp(size).setScreenDensity(2f)
            .setScreenShape(SCREEN_SHAPE_ROUND).setFontScale(fontScale).build()
        val root = materialScopeWithResources(activity, scope, parameters, allowDynamicTheme = false) { homeTileLayout(model) }
        val resources = scope.collectResources()
        if (model.items.any { it.reading == null }) assertTrue(resources.idToImageMapping.isNotEmpty())
        val renderer = TileRenderer.Builder(activity, Executor { it.run() }, {}).build()
        val future = renderer.inflateAsync(Layout.Builder().setRoot(root).build(), resources, parent)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(future.get(5, TimeUnit.SECONDS))
        parent.measure(View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, pixels, pixels)
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.clipPath(Path().apply { addCircle(pixels / 2f, pixels / 2f, pixels / 2f, Path.Direction.CW) })
        parent.draw(canvas)
        val file = File("build/tile-screenshots/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Rendered(activity, parent)
    }
    private fun find(view: View, description: String): View? {
        if (view.contentDescription?.toString() == description && view.isClickable) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index), description)?.let { return it }
        return null
    }
}

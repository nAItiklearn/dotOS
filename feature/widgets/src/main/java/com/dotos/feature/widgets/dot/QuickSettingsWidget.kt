package com.dotos.feature.widgets.dot

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.dotSpring
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetShape
import com.dotos.feature.widgets.api.WidgetSize
import com.dotos.feature.widgets.api.WidgetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Round quick-settings tiles. ON tiles are filled white with a dark glyph. Only the flashlight can be
 * toggled without a permission; the rest open the matching system panel. State comes from content
 * observers and a torch callback that exist only while the widget is composed (none in previews).
 */
object QuickSettingsWidget : DotWidgetProvider {
    override val kind = "quick_settings"
    override val title = "Quick Settings"
    override val description = "Round toggles for the flashlight, Wi-Fi, Bluetooth and more."
    override val sizes = listOf(WidgetSize(4, 1), WidgetSize(4, 2), WidgetSize(2, 2))

    override fun shape(size: WidgetSize): WidgetShape = if (size.spanY <= 1) WidgetShape.PILL else WidgetShape.ROUNDED

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val host = LocalWidgetHost.current
        val preview = instance.isPreview
        val live = !preview

        val torch = rememberTorch(live)
        val wifiOn by rememberObservedFlag(live, Settings.Global.getUriFor("wifi_on")) {
            Settings.Global.getInt(it.contentResolver, "wifi_on", 0) == 1
        }
        val bluetoothOn by rememberObservedFlag(live, Settings.Global.getUriFor("bluetooth_on")) {
            Settings.Global.getInt(it.contentResolver, "bluetooth_on", 0) == 1
        }
        val dndOn by rememberObservedFlag(live, Settings.Global.getUriFor("zen_mode")) {
            val filter = it.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
            filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
        val rotateOn by rememberObservedFlag(live, Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)) {
            Settings.System.getInt(it.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        }

        val pill = instance.spanY <= 1
        val wide = instance.spanX >= 4
        val tiles = when {
            pill -> QsKind.entries.take(5)
            wide -> QsKind.entries
            else -> QsKind.entries.take(6)
        }
        val columns = when {
            pill -> 5
            wide -> 4
            else -> 2
        }
        val rows = ceil(tiles.size / columns.toFloat()).toInt()

        BoxWithConstraints(modifier.fillMaxSize()) {
            val w = maxWidth.value
            val h = maxHeight.value
            val padX = if (pill) h * 0.22f else 16f
            val padY = if (pill) 0f else 16f
            val gap = 12f
            val rawDiameter = if (pill) {
                min(h * 0.56f, (w - 2f * padX - (columns - 1) * 8f) / columns)
            } else {
                min((w - 2f * padX - (columns - 1) * gap) / columns, (h - 2f * padY - (rows - 1) * gap) / rows)
            }
            val diameter = rawDiameter.coerceAtLeast(24f)

            @Composable
            fun Tile(kind: QsKind) {
                val on = when (kind) {
                    QsKind.TORCH -> torch.on
                    QsKind.WIFI -> wifiOn
                    QsKind.BLUETOOTH -> bluetoothOn
                    QsKind.DND -> dndOn
                    QsKind.ROTATE -> rotateOn
                    QsKind.HOTSPOT, QsKind.QR -> false
                }
                QsTile(
                    kind = kind,
                    on = on,
                    available = kind != QsKind.TORCH || torch.available,
                    interactive = live,
                    style = style,
                    diameter = diameter.dp,
                ) {
                    when (kind) {
                        QsKind.TORCH -> torch.toggle()
                        else -> host.baLaunch(kind.settingsIntent())
                    }
                }
            }

            if (pill) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = padX.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tiles.forEach { Tile(it) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(gap.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    tiles.chunked(columns).forEach { rowTiles ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap.dp, Alignment.CenterHorizontally)) {
                            rowTiles.forEach { Tile(it) }
                        }
                    }
                }
            }
        }
    }
}

private enum class QsKind(val label: String) {
    TORCH("Flashlight"),
    WIFI("Wi-Fi"),
    BLUETOOTH("Bluetooth"),
    DND("Do not disturb"),
    ROTATE("Auto-rotate"),
    HOTSPOT("Hotspot"),
    QR("QR scanner"),
}

/** The system screen a tile opens (the torch is toggled in place instead). */
private fun QsKind.settingsIntent(): Intent = when (this) {
    QsKind.TORCH -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    QsKind.WIFI -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    QsKind.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    QsKind.DND -> Intent("android.settings.ZEN_MODE_SETTINGS")
    QsKind.ROTATE -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
    QsKind.HOTSPOT -> Intent("android.settings.TETHER_SETTINGS")
    QsKind.QR -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
}

// ---------------------------------------------------------------------------------------------
// State sources
// ---------------------------------------------------------------------------------------------

@Immutable
private class TorchControl(val on: Boolean, val available: Boolean, val toggle: () -> Unit)

private fun findTorchCamera(manager: CameraManager): String? = runCatching {
    manager.cameraIdList.firstOrNull { id ->
        val characteristics = manager.getCameraCharacteristics(id)
        characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }
}.getOrNull()

/** Flashlight state through a TorchCallback registered only while composed (and not in previews). */
@Composable
private fun rememberTorch(live: Boolean): TorchControl {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager }
    val cameraId by produceState<String?>(null, manager) {
        value = if (manager == null) null else withContext(Dispatchers.Default) { findTorchCamera(manager) }
    }
    var on by remember { mutableStateOf(false) }
    DisposableEffect(manager, cameraId, live) {
        val id = cameraId
        if (!live || manager == null || id == null) return@DisposableEffect onDispose { }
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) on = enabled
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == id) on = false
            }
        }
        manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterTorchCallback(callback) }
    }
    val id = cameraId
    val isOn = on
    return TorchControl(on = isOn, available = id != null) {
        if (manager != null && id != null) runCatching { manager.setTorchMode(id, !isOn) }
    }
}

/**
 * A boolean read from system settings: initial read, re-read when the launcher resumes (coming back
 * from a settings panel) and on every change of [uri] while composed.
 */
@Composable
private fun rememberObservedFlag(live: Boolean, uri: Uri, read: (Context) -> Boolean): State<Boolean> {
    val context = LocalContext.current
    val state = remember(context) { mutableStateOf(runCatching { read(context) }.getOrDefault(false)) }
    val resumed by baRememberResumed()
    LaunchedEffect(resumed) {
        if (resumed) state.value = runCatching { read(context) }.getOrDefault(state.value)
    }
    DisposableEffect(context, live, uri) {
        if (!live) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value = runCatching { read(context) }.getOrDefault(state.value)
            }
        }
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return state
}

// ---------------------------------------------------------------------------------------------
// Tile
// ---------------------------------------------------------------------------------------------

@Composable
private fun QsTile(
    kind: QsKind,
    on: Boolean,
    available: Boolean,
    interactive: Boolean,
    style: WidgetStyle,
    diameter: Dp,
    onClick: () -> Unit,
) {
    val fill = animateColorAsState(if (on) style.content else style.track, dotSpring(), label = "qsFill")
    val ink = animateColorAsState(if (on) style.background.copy(alpha = 1f) else style.content, dotSpring(), label = "qsInk")
    val glyph = remember(kind) { qsGlyph(kind) }
    val dim = if (available) 1f else 0.4f
    Canvas(
        Modifier
            .size(diameter)
            .semantics { contentDescription = kind.label + if (on) ", on" else ", off" }
            .baTap(enabled = interactive && available, onClick = onClick),
    ) {
        drawCircle(color = fill.value, alpha = dim)
        val box = size.minDimension * 0.5f
        val unit = box / 24f
        val left = (size.width - box) / 2f
        val top = (size.height - box) / 2f
        withTransform({
            translate(left, top)
            scale(unit, unit, Offset.Zero)
        }) {
            drawPath(glyph.path, ink.value, alpha = dim, style = QS_STROKE)
            for (i in glyph.dots.indices) drawCircle(ink.value, 1.5f, glyph.dots[i], alpha = dim)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Glyphs: 2 unit monoline strokes in a 24 x 24 box, drawn scaled to the tile
// ---------------------------------------------------------------------------------------------

private val QS_STROKE = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private class QsGlyph(val path: Path, val dots: List<Offset> = emptyList())

private fun Path.qsArc(cx: Float, cy: Float, radius: Float, start: Float, sweep: Float) {
    arcTo(Rect(Offset(cx, cy), radius), start, sweep, true)
}

/** Two short strokes that form an arrow head at [tip], pointing along ([dx], [dy]). */
private fun Path.qsArrowHead(tip: Offset, dx: Float, dy: Float, length: Float) {
    for (sign in intArrayOf(-1, 1)) {
        val a = sign * 0.62f
        val bx = -(dx * cos(a) - dy * sin(a))
        val by = -(dx * sin(a) + dy * cos(a))
        moveTo(tip.x, tip.y)
        lineTo(tip.x + bx * length, tip.y + by * length)
    }
}

private fun qsGlyph(kind: QsKind): QsGlyph = when (kind) {
    QsKind.TORCH -> QsGlyph(
        Path().apply {
            moveTo(7f, 3f)
            lineTo(17f, 3f)
            lineTo(15f, 10f)
            lineTo(9f, 10f)
            close()
            moveTo(9f, 10f)
            lineTo(9f, 21f)
            lineTo(15f, 21f)
            lineTo(15f, 10f)
            moveTo(12f, 13.5f)
            lineTo(12f, 16.5f)
        },
    )
    QsKind.WIFI -> QsGlyph(
        Path().apply {
            qsArc(12f, 18f, 12.5f, -135f, 90f)
            qsArc(12f, 18f, 8.5f, -135f, 90f)
            qsArc(12f, 18f, 4.5f, -135f, 90f)
        },
        dots = listOf(Offset(12f, 18f)),
    )
    QsKind.BLUETOOTH -> QsGlyph(
        Path().apply {
            moveTo(7f, 7f)
            lineTo(17f, 17f)
            lineTo(12f, 22f)
            lineTo(12f, 2f)
            lineTo(17f, 7f)
            lineTo(7f, 17f)
        },
    )
    QsKind.DND -> QsGlyph(
        Path().apply {
            addOval(Rect(Offset(12f, 12f), 9f))
            moveTo(7.5f, 12f)
            lineTo(16.5f, 12f)
        },
    )
    QsKind.ROTATE -> QsGlyph(
        Path().apply {
            qsArc(12f, 12f, 8.5f, 20f, 270f)
            // Clockwise arc ending at 290 degrees; its tangent there is (-sin, cos).
            val end = 290f * (Math.PI / 180.0).toFloat()
            val tip = Offset(12f + 8.5f * cos(end), 12f + 8.5f * sin(end))
            qsArrowHead(tip, -sin(end), cos(end), 4f)
            addRoundRect(RoundRect(Rect(9.6f, 8.6f, 14.4f, 15.4f), CornerRadius(1f)))
        },
    )
    QsKind.HOTSPOT -> QsGlyph(
        Path().apply {
            qsArc(12f, 12f, 5f, -50f, 100f)
            qsArc(12f, 12f, 9f, -50f, 100f)
            qsArc(12f, 12f, 5f, 130f, 100f)
            qsArc(12f, 12f, 9f, 130f, 100f)
        },
        dots = listOf(Offset(12f, 12f)),
    )
    QsKind.QR -> QsGlyph(
        Path().apply {
            moveTo(3f, 8f)
            lineTo(3f, 3f)
            lineTo(8f, 3f)
            moveTo(16f, 3f)
            lineTo(21f, 3f)
            lineTo(21f, 8f)
            moveTo(21f, 16f)
            lineTo(21f, 21f)
            lineTo(16f, 21f)
            moveTo(8f, 21f)
            lineTo(3f, 21f)
            lineTo(3f, 16f)
            moveTo(7f, 12f)
            lineTo(17f, 12f)
        },
    )
}

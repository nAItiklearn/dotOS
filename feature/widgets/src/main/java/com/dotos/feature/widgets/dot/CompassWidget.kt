package com.dotos.feature.widgets.dot

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotFonts
import com.dotos.core.designsystem.LocalReduceMotion
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetShape
import com.dotos.feature.widgets.api.WidgetSize
import com.dotos.feature.widgets.api.drawDotMatrix
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Compass: a dial that turns against the phone so the dot-built arrow always points north. The
 * sensors are registered only while the widget is composed and the launcher is RESUMED, at
 * SENSOR_DELAY_UI. Previews and phones without a magnetometer show a static pose.
 */
object CompassWidget : DotWidgetProvider {
    override val kind = "compass"
    override val title = "Compass"
    override val description = "A dot arrow that always points north."
    override val sizes = listOf(WidgetSize(1, 1), WidgetSize(2, 2))

    override fun shape(size: WidgetSize): WidgetShape = WidgetShape.CIRCLE

    /** Pose of the picker preview, in degrees. */
    private const val PREVIEW_HEADING = 24f

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val preview = instance.isPreview
        val big = instance.spanX >= 2
        val heading = rememberCompassHeading(live = !preview, initial = if (preview) PREVIEW_HEADING else 0f)
        val measurer = rememberTextMeasurer()
        Box(
            modifier
                .fillMaxSize()
                .semantics { contentDescription = "Compass" }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val d = min(w, h)
                    val cx = w / 2f
                    val cy = h / 2f
                    val r = d / 2f - max(6.dp.toPx(), d * 0.06f)
                    val q = r * 0.115f
                    val pivot = Offset(cx, cy)
                    val arrowTopLeft = Offset(cx - 2.5f * q, cy - 3.5f * q)

                    val tickCount = if (big) 36 else 12
                    val quarter = tickCount / 4
                    val tickStep = 360f / tickCount
                    val tickRadius = r * 0.86f
                    val majorR = r * 0.032f
                    val minorR = r * 0.02f

                    val letterPx = if (big) r * 0.17f else r * 0.2f
                    val letterRadius = if (big) r * 0.66f else r * 0.62f
                    val nT = baText(measurer, "N", letterPx, DotFonts.Grotesk, FontWeight.Bold)
                    val eT = if (big) baText(measurer, "E", letterPx, DotFonts.Grotesk, FontWeight.Medium) else null
                    val sT = if (big) baText(measurer, "S", letterPx, DotFonts.Grotesk, FontWeight.Medium) else null
                    val wT = if (big) baText(measurer, "W", letterPx, DotFonts.Grotesk, FontWeight.Medium) else null

                    // Fixed marker at the top: the direction the phone is facing.
                    val lubber = Path().apply {
                        moveTo(cx - r * 0.05f, cy - r)
                        lineTo(cx + r * 0.05f, cy - r)
                        lineTo(cx, cy - r * 0.935f)
                        close()
                    }

                    onDrawBehind {
                        val deg = heading.value
                        drawPath(lubber, style.content)

                        for (i in 0 until tickCount) {
                            val a = (i * tickStep - deg) * DEG_RAD
                            val major = i % quarter == 0
                            drawCircle(
                                color = if (major) style.content else style.muted,
                                radius = if (major) majorR else minorR,
                                center = Offset(cx + sin(a) * tickRadius, cy - cos(a) * tickRadius),
                            )
                        }

                        val an = -deg * DEG_RAD
                        baDrawText(nT, style.accent, cx + sin(an) * letterRadius, cy - cos(an) * letterRadius)
                        if (eT != null && sT != null && wT != null) {
                            val ae = (90f - deg) * DEG_RAD
                            baDrawText(eT, style.muted, cx + sin(ae) * letterRadius, cy - cos(ae) * letterRadius)
                            val a180 = (180f - deg) * DEG_RAD
                            baDrawText(sT, style.muted, cx + sin(a180) * letterRadius, cy - cos(a180) * letterRadius)
                            val aw = (270f - deg) * DEG_RAD
                            baDrawText(wT, style.muted, cx + sin(aw) * letterRadius, cy - cos(aw) * letterRadius)
                        }

                        rotate(degrees = -deg, pivot = pivot) {
                            drawDotMatrix(
                                rows = ARROW_ROWS,
                                on = style.content,
                                topLeft = arrowTopLeft,
                                pitch = q,
                                radiusFraction = 0.4f,
                            )
                        }
                    }
                },
        )
    }
}

private val DEG_RAD: Float = (PI / 180.0).toFloat()

/** 5x7 up arrow; rotated around the dial centre. */
private val ARROW_ROWS = listOf(
    "..#..",
    ".###.",
    "#.#.#",
    "..#..",
    "..#..",
    "..#..",
    "..#..",
)

/**
 * Smoothed heading in degrees, as an unwrapped angle (it may leave 0..360 so that springs always take
 * the short way round). Sensors run only when [live] and the launcher is resumed.
 */
@Composable
private fun rememberCompassHeading(live: Boolean, initial: Float): Animatable<Float, AnimationVector1D> {
    val context = LocalContext.current
    val resumed by baRememberResumed()
    val reduceMotion = LocalReduceMotion.current
    val heading = remember { Animatable(initial) }
    val target = remember { mutableFloatStateOf(initial) }

    DisposableEffect(context, live, resumed) {
        val manager = if (live && resumed) context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager else null
        if (manager == null) return@DisposableEffect onDispose { }
        val listener = CompassHeadingListener { degrees -> target.floatValue = degrees }
        val registered = listener.register(manager)
        onDispose { if (registered) manager.unregisterListener(listener) }
    }

    LaunchedEffect(live, reduceMotion) {
        if (!live) return@LaunchedEffect
        var current = heading.value
        snapshotFlow { target.floatValue }.collectLatest { degrees ->
            // Shortest signed angular distance from the last target, so 359 -> 1 never spins the long way.
            val delta = (((degrees - current) % 360f) + 540f) % 360f - 180f
            current += delta
            if (reduceMotion) {
                heading.snapTo(current)
            } else {
                heading.animateTo(current, spring(dampingRatio = 0.85f, stiffness = 140f))
            }
        }
    }
    return heading
}

/** Turns rotation-vector (or accelerometer + magnetometer) events into a low-pass filtered azimuth. */
private class CompassHeadingListener(private val onHeading: (Float) -> Unit) : SensorEventListener {
    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)
    private val gravity = FloatArray(3)
    private val magnet = FloatArray(3)
    private var haveGravity = false
    private var haveMagnet = false
    private var sinAverage = 0f
    private var cosAverage = 1f
    private var seeded = false
    private var lastSent = Float.NaN

    /** Registers the best sensors available; false when the phone has no usable compass. */
    fun register(manager: SensorManager): Boolean {
        val vector = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (vector != null) return manager.registerListener(this, vector, SensorManager.SENSOR_DELAY_UI)
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (accelerometer == null || magnetometer == null) return false
        val a = manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        val m = manager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        return a || m
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                // Some devices report 5 values, which getRotationMatrixFromVector rejects.
                val values = if (event.values.size > 4) event.values.copyOf(4) else event.values
                SensorManager.getRotationMatrixFromVector(rotation, values)
                publish()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                smooth(event.values, gravity, haveGravity, 0.12f)
                haveGravity = true
                publishFromRaw()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                smooth(event.values, magnet, haveMagnet, 0.18f)
                haveMagnet = true
                publishFromRaw()
            }
        }
    }

    private fun smooth(source: FloatArray, into: FloatArray, seeded: Boolean, alpha: Float) {
        for (i in 0 until 3) into[i] = if (seeded) into[i] + alpha * (source[i] - into[i]) else source[i]
    }

    private fun publishFromRaw() {
        if (haveGravity && haveMagnet && SensorManager.getRotationMatrix(rotation, null, gravity, magnet)) publish()
    }

    private fun publish() {
        // Held upright, the useful heading is where the camera points rather than where the top edge points.
        val matrix = if (abs(rotation[8]) < 0.6f) {
            SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
            remapped
        } else {
            rotation
        }
        SensorManager.getOrientation(matrix, orientation)
        val azimuth = orientation[0]
        if (!seeded) {
            sinAverage = sin(azimuth)
            cosAverage = cos(azimuth)
            seeded = true
        } else {
            sinAverage += 0.3f * (sin(azimuth) - sinAverage)
            cosAverage += 0.3f * (cos(azimuth) - cosAverage)
        }
        var degrees = Math.toDegrees(atan2(sinAverage, cosAverage).toDouble()).toFloat()
        if (degrees < 0f) degrees += 360f
        if (lastSent.isNaN() || abs(((degrees - lastSent + 540f) % 360f) - 180f) > 0.4f) {
            lastSent = degrees
            onHeading(degrees)
        }
    }
}

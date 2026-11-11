package com.dotos.feature.widgets.dot

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dotos.core.designsystem.DotFonts
import com.dotos.core.designsystem.LocalReduceMotion
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetShape
import com.dotos.feature.widgets.api.WidgetSize
import com.dotos.feature.widgets.api.WidgetStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Phone battery: a 2x1 pill of 12 rounded segments or a 2x2 dot ring with a big Doto percentage.
 * Driven by the sticky ACTION_BATTERY_CHANGED broadcast (no polling). Below 20 % the lit dots turn
 * red; while charging a bolt shows and the next dot breathes (only while the launcher is resumed).
 */
object BatteryWidget : DotWidgetProvider {
    override val kind = "battery"
    override val title = "Battery"
    override val description = "Phone battery as a dot bar or a dot ring."
    override val sizes = listOf(WidgetSize(2, 1), WidgetSize(2, 2))

    override fun shape(size: WidgetSize): WidgetShape = if (size.spanY <= 1) WidgetShape.PILL else WidgetShape.ROUNDED

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val host = LocalWidgetHost.current
        val preview = instance.isPreview
        val reading by rememberBatteryReading(live = !preview)
        val reduceMotion = LocalReduceMotion.current
        val resumed by baRememberResumed()
        val pulsing = !preview && reading.charging && reading.percent < 100 && resumed && !reduceMotion
        val pulse = remember { Animatable(0f) }
        LaunchedEffect(pulsing) {
            if (!pulsing) {
                pulse.snapTo(0f)
                return@LaunchedEffect
            }
            while (true) {
                pulse.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                pulse.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
            }
        }

        val description = "Battery ${reading.percent} percent" + if (reading.pluggedIn) ", charging" else ""
        val root = modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .baTap(enabled = !preview) { host.baLaunch(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }

        if (instance.spanY <= 1) {
            BatteryPill(reading, pulse, pulsing, style, root)
        } else {
            BatteryRing(reading, pulse, pulsing, style, root)
        }
    }
}

@Immutable
private class BatteryReading(val percent: Int, val pluggedIn: Boolean, val charging: Boolean)

private val BatteryFallback = BatteryReading(percent = 100, pluggedIn = false, charging = false)

private fun readBattery(intent: Intent?): BatteryReading? {
    if (intent == null) return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    return BatteryReading(
        percent = (level * 100f / scale).roundToInt().coerceIn(0, 100),
        pluggedIn = plugged != 0,
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING,
    )
}

/** Sticky read for the first frame, then a receiver that lives only while the widget is composed. */
@SuppressLint("UnspecifiedRegisterReceiverFlag") // null receiver: only reads the protected sticky broadcast
@Composable
private fun rememberBatteryReading(live: Boolean): State<BatteryReading> {
    val context = LocalContext.current
    val filter = remember { IntentFilter(Intent.ACTION_BATTERY_CHANGED) }
    val state = remember(context) {
        mutableStateOf(readBattery(context.registerReceiver(null, filter)) ?: BatteryFallback)
    }
    DisposableEffect(context, live) {
        if (!live) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                readBattery(intent)?.let { state.value = it }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

private const val BATTERY_SEGMENTS = 12
private const val BATTERY_RING_DOTS = 36

/** Number of lit units out of [total] for [percent]: any charge shows at least one. */
private fun litCount(percent: Int, total: Int): Int =
    if (percent <= 0) 0 else max(1, (percent * total + 50) / 100).coerceAtMost(total)

@Composable
private fun BatteryPill(
    reading: BatteryReading,
    pulse: Animatable<Float, AnimationVector1D>,
    pulsing: Boolean,
    style: WidgetStyle,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val percentText = "${reading.percent}%"
    val lit = litCount(reading.percent, BATTERY_SEGMENTS)
    val litColor = if (reading.percent < 20) style.accent else style.content
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val u = min(w / BA_REF_2X2_W_DP.dp.toPx(), h / 110.dp.toPx())
            val tall = h >= 76.dp.toPx()
            val padX = max(14.dp.toPx(), h * 0.2f)
            val innerW = (w - 2f * padX).coerceAtLeast(1f)

            if (tall) {
                val pctT = baText(measurer, percentText, h * 0.2f, DotFonts.Grotesk, FontWeight.Medium, innerW * 0.5f)
                val labelT = baLabel(measurer, BaStrings.BATTERY, h * 0.095f, innerW * 0.4f)
                val barH = (h * 0.24f).coerceIn(12.dp.toPx(), 30.dp.toPx())
                val gap = 3.dp.toPx() * u
                val segW = ((innerW - (BATTERY_SEGMENTS - 1) * gap) / BATTERY_SEGMENTS).coerceAtLeast(1f)
                val rad = CornerRadius(min(segW, barH) / 2f)
                val rowCy = h * 0.36f
                val barTop = h * 0.68f - barH / 2f
                val boltH = pctT.px * 1.05f
                val boltX = padX + pctT.width + 8.dp.toPx() * u + boltH * 0.3f
                return@drawWithCache onDrawBehind {
                    baDrawText(pctT, style.content, padX, rowCy, hAlign = 0f)
                    if (reading.pluggedIn) baDrawBolt(boltX, rowCy, boltH, style.content)
                    baDrawText(labelT, style.muted, w - padX, rowCy, hAlign = 1f)
                    for (i in 0 until BATTERY_SEGMENTS) {
                        val x = padX + i * (segW + gap)
                        drawRoundRect(
                            color = if (i < lit) litColor else style.track,
                            topLeft = Offset(x, barTop),
                            size = Size(segW, barH),
                            cornerRadius = rad,
                        )
                    }
                    if (pulsing && lit < BATTERY_SEGMENTS) {
                        drawRoundRect(
                            color = litColor.copy(alpha = 0.15f + 0.85f * pulse.value),
                            topLeft = Offset(padX + lit * (segW + gap), barTop),
                            size = Size(segW, barH),
                            cornerRadius = rad,
                        )
                    }
                }
            }

            // Short pill: one row with the bar on the left and the percentage on the right.
            val pctT = baText(measurer, percentText, h * 0.3f, DotFonts.Grotesk, FontWeight.Medium, innerW * 0.34f)
            val boltH = pctT.px * 1.05f
            val boltSpace = if (reading.pluggedIn) boltH * 0.6f + 6.dp.toPx() else 0f
            val barW = (innerW - pctT.width - 12.dp.toPx() - boltSpace).coerceAtLeast(1f)
            val barH = h * 0.36f
            val gap = 2.dp.toPx()
            val segW = ((barW - (BATTERY_SEGMENTS - 1) * gap) / BATTERY_SEGMENTS).coerceAtLeast(1f)
            val rad = CornerRadius(min(segW, barH) / 2f)
            val barTop = (h - barH) / 2f
            onDrawBehind {
                for (i in 0 until BATTERY_SEGMENTS) {
                    drawRoundRect(
                        color = if (i < lit) litColor else style.track,
                        topLeft = Offset(padX + i * (segW + gap), barTop),
                        size = Size(segW, barH),
                        cornerRadius = rad,
                    )
                }
                if (pulsing && lit < BATTERY_SEGMENTS) {
                    drawRoundRect(
                        color = litColor.copy(alpha = 0.15f + 0.85f * pulse.value),
                        topLeft = Offset(padX + lit * (segW + gap), barTop),
                        size = Size(segW, barH),
                        cornerRadius = rad,
                    )
                }
                if (reading.pluggedIn) baDrawBolt(padX + barW + 6.dp.toPx() + boltH * 0.3f, h / 2f, boltH, style.content)
                baDrawText(pctT, style.content, w - padX, h / 2f, hAlign = 1f)
            }
        },
    )
}

@Composable
private fun BatteryRing(
    reading: BatteryReading,
    pulse: Animatable<Float, AnimationVector1D>,
    pulsing: Boolean,
    style: WidgetStyle,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val lit = litCount(reading.percent, BATTERY_RING_DOTS)
    val litColor = if (reading.percent < 20) style.accent else style.content
    val label = if (reading.pluggedIn) BaStrings.CHARGING else BaStrings.BATTERY
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val u = min(w / BA_REF_2X2_W_DP.dp.toPx(), h / BA_REF_2X2_H_DP.dp.toPx())
            val d = min(w, h)
            val cx = w / 2f
            val cy = h / 2f
            val pad = 14.dp.toPx() * u
            val dotR = d * 0.021f
            val ringR = d / 2f - pad - dotR
            val innerR = ringR - dotR - 8.dp.toPx() * u

            val ringX = FloatArray(BATTERY_RING_DOTS)
            val ringY = FloatArray(BATTERY_RING_DOTS)
            val step = (2.0 * PI / BATTERY_RING_DOTS).toFloat()
            val start = (-PI / 2.0).toFloat()
            for (i in 0 until BATTERY_RING_DOTS) {
                val a = start + i * step
                ringX[i] = cx + cos(a) * ringR
                ringY[i] = cy + sin(a) * ringR
            }

            val numberPx = innerR * 1.03f
            val numberT = baDoto(measurer, reading.percent.toString(), numberPx, maxWidth = innerR * 1.5f)
            val labelT = baLabel(measurer, label, max(d * 0.056f, 7.dp.toPx()), innerR * 1.5f)
            val numberH = numberT.px * 0.7f
            val gap = numberT.px * 0.22f
            val labelH = labelT.px * 0.7f
            val stackH = numberH + gap + labelH
            val top = cy - stackH / 2f
            val numberCy = top + numberH / 2f
            val labelCy = top + numberH + gap + labelH / 2f
            val boltH = labelT.px * 1.5f
            val boltW = boltH * 0.5f
            val boltGap = 4.dp.toPx() * u
            val groupW = if (reading.pluggedIn) boltW + boltGap + labelT.width else labelT.width
            val groupLeft = cx - groupW / 2f
            onDrawBehind {
                for (i in 0 until BATTERY_RING_DOTS) {
                    drawCircle(if (i < lit) litColor else style.track, dotR, Offset(ringX[i], ringY[i]))
                }
                if (pulsing && lit < BATTERY_RING_DOTS) {
                    drawCircle(litColor.copy(alpha = 0.15f + 0.85f * pulse.value), dotR, Offset(ringX[lit], ringY[lit]))
                }
                baDrawText(numberT, style.content, cx, numberCy, shiftEm = BA_DOTO_INK_SHIFT_EM)
                if (reading.pluggedIn) {
                    baDrawBolt(groupLeft + boltW / 2f, labelCy, boltH, style.content)
                    baDrawText(labelT, style.muted, groupLeft + boltW + boltGap, labelCy, hAlign = 0f)
                } else {
                    baDrawText(labelT, style.muted, groupLeft, labelCy, hAlign = 0f)
                }
            }
        },
    )
}

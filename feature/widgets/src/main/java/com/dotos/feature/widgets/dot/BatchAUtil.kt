package com.dotos.feature.widgets.dot

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dotos.core.designsystem.DotFonts
import com.dotos.core.designsystem.component.pressScale
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.feature.widgets.api.WidgetHostServices

/*
 * Shared helpers for widget batch A. Everything is prefixed "ba" / "Ba" on purpose so it can never
 * collide with the helpers of other batches that live in the same package.
 */

/** Doto (the dot-matrix face) is a 5x7 grid: cap height 0.7 em, dot pitch 0.1 em, advance 0.6 em. */
internal const val BA_DOTO_CAP_EM = 0.7f

/** Doto ink is centred 0.05 em left of its layout box (one blank column on the right). */
internal const val BA_DOTO_INK_SHIFT_EM = 0.05f

/** Dot radius / pitch at Doto weight 700. */
internal const val BA_DOTO_DOT_RADIUS_PITCH = 0.375f

/** Reference size of a 2x2 cell block (92 x 110 dp cells); layouts scale from this. */
internal const val BA_REF_2X2_W_DP = 184f
internal const val BA_REF_2X2_H_DP = 220f

/** User-visible strings of batch A in one place (the module has no resources yet). */
internal object BaStrings {
    const val SEARCH = "Search"
    const val TAP_ADD_PHOTO = "Tap to add photo"
    const val TAP_SHOW_EVENTS = "Tap to show events"
    const val NO_EVENTS = "No events"
    const val NEXT = "Next"
    const val TODAY = "Today"
    const val TOMORROW = "Tomorrow"
    const val YESTERDAY = "Yesterday"
    const val BATTERY = "Battery"
    const val CHARGING = "Charging"
}

/** A measured single-line text together with the pixel font size it was measured at. */
internal class BaText(val layout: TextLayoutResult, val px: Float) {
    val width: Float get() = layout.size.width.toFloat()
}

private fun Density.baMeasure(
    measurer: TextMeasurer,
    text: String,
    px: Float,
    family: FontFamily,
    weight: FontWeight,
    letterSpacingEm: Float,
    maxWidth: Float,
    ellipsize: Boolean,
): TextLayoutResult = measurer.measure(
    text = text,
    style = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (px / density).dp.toSp(),
        letterSpacing = letterSpacingEm.em,
    ),
    overflow = if (ellipsize) TextOverflow.Ellipsis else TextOverflow.Clip,
    softWrap = false,
    maxLines = 1,
    constraints = if (ellipsize && maxWidth.isFinite()) {
        Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(0))
    } else {
        Constraints()
    },
)

/**
 * Measures [text] at [px] and, when it is wider than [maxWidth], shrinks the font so it fits.
 * Text drawn with it can therefore never clip or overflow, whatever the system font scale is.
 */
internal fun Density.baText(
    measurer: TextMeasurer,
    text: String,
    px: Float,
    family: FontFamily,
    weight: FontWeight,
    maxWidth: Float = Float.POSITIVE_INFINITY,
    letterSpacingEm: Float = 0f,
): BaText {
    var size = px.coerceAtLeast(1f)
    var layout = baMeasure(measurer, text, size, family, weight, letterSpacingEm, maxWidth, false)
    val w = layout.size.width
    if (maxWidth.isFinite() && w > maxWidth && w > 0) {
        size = (size * maxWidth / w * 0.98f).coerceAtLeast(3f)
        layout = baMeasure(measurer, text, size, family, weight, letterSpacingEm, maxWidth, false)
    }
    return BaText(layout, size)
}

/** Like [baText] but keeps the size and ends the text with an ellipsis when it is too wide. */
internal fun Density.baTextEllipsized(
    measurer: TextMeasurer,
    text: String,
    px: Float,
    family: FontFamily,
    weight: FontWeight,
    maxWidth: Float,
    letterSpacingEm: Float = 0f,
): BaText {
    val size = px.coerceAtLeast(1f)
    return BaText(baMeasure(measurer, text, size, family, weight, letterSpacingEm, maxWidth, true), size)
}

/** Small-caps section label: Space Grotesk Medium, wide tracking, upper case. */
internal fun Density.baLabel(
    measurer: TextMeasurer,
    text: String,
    px: Float,
    maxWidth: Float = Float.POSITIVE_INFINITY,
    spacingEm: Float = 0.16f,
): BaText = baText(measurer, text.uppercase(), px, DotFonts.Grotesk, FontWeight.Medium, maxWidth, spacingEm)

/** Dot-matrix text (Doto, bold = round 0.75 pitch dots). */
internal fun Density.baDoto(
    measurer: TextMeasurer,
    text: String,
    px: Float,
    maxWidth: Float = Float.POSITIVE_INFINITY,
): BaText = baText(measurer, text, px, DotFonts.Doto, FontWeight.Bold, maxWidth)

/**
 * Draws [t] so that the middle of its capital height sits on [centerY] and its box is aligned to
 * [anchorX] ([hAlign]: 0 = left edge, 0.5 = centred, 1 = right edge). [shiftEm] nudges the text.
 */
internal fun DrawScope.baDrawText(
    t: BaText,
    color: Color,
    anchorX: Float,
    centerY: Float,
    hAlign: Float = 0.5f,
    capEm: Float = BA_DOTO_CAP_EM,
    shiftEm: Float = 0f,
) {
    val x = anchorX - t.width * hAlign + shiftEm * t.px
    val y = centerY - t.layout.firstBaseline + capEm * t.px * 0.5f
    drawText(textLayoutResult = t.layout, color = color, topLeft = Offset(x, y))
}

/**
 * Press-scale + tap + light haptic. Pass `enabled = false` for picker previews (no side effects).
 */
@Composable
internal fun Modifier.baTap(enabled: Boolean = true, role: Role? = Role.Button, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberDotHaptics()
    if (!enabled) return this
    return this
        .pressScale(source)
        .clickable(
            interactionSource = source,
            indication = null,
            role = role,
            onClick = {
                haptics.tick()
                onClick()
            },
        )
}

/** True while the host lifecycle is RESUMED; sensors / animations hang off this so they sleep offscreen. */
@Composable
internal fun baRememberResumed(): State<Boolean> {
    val owner = LocalLifecycleOwner.current
    val state = remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.value = true
                Lifecycle.Event.ON_PAUSE -> state.value = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

/** Starts an activity through the host; a missing handler must never crash the launcher. */
internal fun WidgetHostServices.baLaunch(intent: Intent) {
    runCatching { startActivity(intent) }
}

internal fun baTwoDigits(n: Int): String = if (n in 0..9) "0$n" else n.toString()

/** Unit lightning bolt centred on the origin, 1 unit tall. */
private val BoltPath = Path().apply {
    moveTo(0.12f, -0.5f)
    lineTo(-0.30f, 0.06f)
    lineTo(-0.04f, 0.06f)
    lineTo(-0.12f, 0.5f)
    lineTo(0.30f, -0.08f)
    lineTo(0.04f, -0.08f)
    close()
}

/** Filled lightning bolt centred at ([cx], [cy]), [height] px tall. */
internal fun DrawScope.baDrawBolt(cx: Float, cy: Float, height: Float, color: Color) {
    withTransform({
        translate(cx, cy)
        scale(height, height, Offset.Zero)
    }) {
        drawPath(BoltPath, color)
    }
}

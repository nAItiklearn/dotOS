package com.dotos.feature.home.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetFace
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetShape
import com.dotos.feature.widgets.api.WidgetSize
import com.dotos.feature.widgets.api.WidgetStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** User-visible text. Kept in one place so it can move to string resources in a single step. */
internal object WidgetPickerStrings {
    const val SEARCH_PLACEHOLDER = "Search widgets"
    const val CLEAR_SEARCH = "Clear search"
    const val SECTION_DOTOS = "DotOS"
    const val NO_RESULTS = "No widgets found"
    const val ADD_TO_HOME = "Add to home"
    const val CLOSE = "Close"
    const val WIDGET_UNAVAILABLE = "Widget unavailable"
}

/** Assumed home cell used to turn a widget's span into the dp size it will really have on the grid. */
internal const val PREVIEW_CELL_WIDTH_DP = 92f
internal const val PREVIEW_CELL_HEIGHT_DP = 110f

/** Corner radius of a ROUNDED widget surface in the picker. */
private val RoundedSurfaceRadius = 18.dp

/** Black preview plates, as in the reference (#0A0A0A) rather than the translucent home plate. */
private val PreviewStyle = WidgetStyle(background = Color(0xFF0A0A0A))
private val PreviewRim = Color(0x29FFFFFF)

internal fun facesOf(provider: DotWidgetProvider): List<WidgetFace> =
    provider.faces.ifEmpty { listOf(WidgetFace("default", provider.title)) }

/** Real dp size of [provider] at [size] on a home grid with the assumed cell size. */
internal fun realSizeOf(provider: DotWidgetProvider, size: WidgetSize): DpSize {
    val w = size.spanX * PREVIEW_CELL_WIDTH_DP
    val h = size.spanY * PREVIEW_CELL_HEIGHT_DP
    return if (provider.shape(size) == WidgetShape.CIRCLE) {
        val side = min(w, h)
        DpSize(side.dp, side.dp)
    } else {
        DpSize(w.dp, h.dp)
    }
}

private fun surfaceShape(shape: WidgetShape): Shape = when (shape) {
    WidgetShape.ROUNDED -> RoundedCornerShape(RoundedSurfaceRadius)
    WidgetShape.PILL -> CircleShape
    WidgetShape.CIRCLE -> CircleShape
}

/**
 * A live DotOS widget inside its launcher surface (shape, black fill, optional rim), exactly as the home
 * screen would draw it. [modifier] must give it a definite size. Previews use itemId = -1, so providers
 * keep them side-effect free.
 */
@Composable
internal fun DotWidgetSurface(
    provider: DotWidgetProvider,
    faceId: String,
    size: WidgetSize,
    modifier: Modifier = Modifier,
    rim: Boolean = false,
) {
    val shape = remember(provider, size) { surfaceShape(provider.shape(size)) }
    val instance = remember(provider, faceId, size) {
        WidgetInstance(
            itemId = -1L,
            kind = provider.kind,
            face = faceId,
            spanX = size.spanX,
            spanY = size.spanY,
        )
    }
    CompositionLocalProvider(LocalWidgetStyle provides PreviewStyle) {
        Box(
            modifier
                .clip(shape)
                .background(PreviewStyle.background)
                .then(if (rim) Modifier.border(1.dp, PreviewRim, shape) else Modifier),
        ) {
            provider.Content(instance, Modifier.fillMaxSize())
        }
    }
}

// Where a card preview sits and how big it may get, as fractions of the card (see the reference
// screenshots: the preview starts ~28% in from the left / ~30% down and bleeds off the bottom-right).
private const val BLEED_FIT_WIDTH = 1.0f
private const val BLEED_FIT_HEIGHT = 0.95f
private const val BLEED_ORIGIN_X = 0.28f
private const val BLEED_ORIGIN_Y = 0.30f
private const val BLEED_MAX_SCALE = 1.5f

/**
 * Lays out [content] at its real size ([realWidth] x [realHeight]) and draws it scaled down (or up a
 * little) so it fills the card's lower right and bleeds past the card edge. The caller clips the card.
 * Needs a definite size from its parent.
 */
@Composable
internal fun BleedPreview(
    realWidth: Dp,
    realHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val rw = max(1, realWidth.roundToPx())
        val rh = max(1, realHeight.roundToPx())
        val cw = if (constraints.hasBoundedWidth) constraints.maxWidth else rw
        val ch = if (constraints.hasBoundedHeight) constraints.maxHeight else rh
        val scale = min(cw * BLEED_FIT_WIDTH / rw, ch * BLEED_FIT_HEIGHT / rh).coerceAtMost(BLEED_MAX_SCALE)
        val placeable = measurables.firstOrNull()?.measure(Constraints.fixed(rw, rh))
        layout(cw, ch) {
            placeable?.placeRelativeWithLayer(
                x = (cw * BLEED_ORIGIN_X).roundToInt(),
                y = (ch * BLEED_ORIGIN_Y).roundToInt(),
            ) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/**
 * Lays out [content] at its real size and draws it centred in the available box, scaled to fit
 * (never above [maxScale]). Needs a definite size from its parent.
 */
@Composable
internal fun FitPreview(
    realWidth: Dp,
    realHeight: Dp,
    modifier: Modifier = Modifier,
    maxScale: Float = 1.25f,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val rw = max(1, realWidth.roundToPx())
        val rh = max(1, realHeight.roundToPx())
        val cw = if (constraints.hasBoundedWidth) constraints.maxWidth else rw
        val ch = if (constraints.hasBoundedHeight) constraints.maxHeight else rh
        val scale = min(cw.toFloat() / rw, ch.toFloat() / rh).coerceAtMost(maxScale)
        val placeable = measurables.firstOrNull()?.measure(Constraints.fixed(rw, rh))
        layout(cw, ch) {
            placeable?.placeRelativeWithLayer(
                x = ((cw - rw * scale) / 2f).roundToInt(),
                y = ((ch - rh * scale) / 2f).roundToInt(),
            ) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/** Small 3x3 dot glyph used as the (original) stand-in for a widget's app icon on a card. */
@Composable
internal fun WidgetGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val pitch = size.minDimension / 3f
        val radius = pitch * 0.32f
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                drawCircle(color, radius, Offset(pitch * (col + 0.5f), pitch * (row + 0.5f)))
            }
        }
    }
}

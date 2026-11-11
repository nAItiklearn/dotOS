package com.dotos.feature.widgets.api

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Wall-clock ticker that wakes exactly on the next [periodMillis] boundary (e.g. each minute),
 * instead of polling. Use for clocks: it fires at :00 so the display never lags.
 */
@Composable
fun rememberTicker(periodMillis: Long = 60_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMillis) {
        while (true) {
            val t = System.currentTimeMillis()
            delay(periodMillis - (t % periodMillis) + 5)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * Draws a dot-matrix bitmap: [rows] strings where any non-space char is a lit dot.
 * Lit dots use [on]; when [off] is not transparent, unlit dots are drawn too (LED panel look).
 */
fun DrawScope.drawDotMatrix(
    rows: List<String>,
    on: Color,
    off: Color = Color.Transparent,
    topLeft: Offset = Offset.Zero,
    pitch: Float,
    radiusFraction: Float = 0.38f,
) {
    val r = pitch * radiusFraction
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch ->
            val lit = ch != ' ' && ch != '.'
            if (lit || off.alpha > 0f) {
                drawCircle(if (lit) on else off, r, Offset(topLeft.x + x * pitch + pitch / 2f, topLeft.y + y * pitch + pitch / 2f))
            }
        }
    }
}

/** Box that fills the widget area; the usual root for widget [DotWidgetProvider.Content]. */
@Composable
fun WidgetBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) = Box(modifier, content = content)

/** Square dot grid progress (e.g. "life in dots"): fills [filled] of [total] dots, row-major. */
fun DrawScope.drawDotGrid(
    total: Int,
    filled: Int,
    columns: Int,
    on: Color,
    off: Color,
    highlight: Color? = null,
) {
    if (total <= 0 || columns <= 0) return
    val rows = (total + columns - 1) / columns
    val pitch = min(size.width / columns, size.height / rows)
    val ox = (size.width - pitch * columns) / 2f
    val oy = (size.height - pitch * rows) / 2f
    val r = pitch * 0.34f
    for (i in 0 until total) {
        val cx = ox + (i % columns) * pitch + pitch / 2f
        val cy = oy + (i / columns) * pitch + pitch / 2f
        val color = when {
            i < filled -> on
            i == filled && highlight != null -> highlight
            else -> off
        }
        drawCircle(color, r, Offset(cx, cy))
    }
}

/** Convenience wrapper so widgets can use a Canvas that fills the box. */
@Composable
fun FillCanvas(modifier: Modifier = Modifier, onDraw: DrawScope.() -> Unit) = Canvas(modifier, onDraw = onDraw)

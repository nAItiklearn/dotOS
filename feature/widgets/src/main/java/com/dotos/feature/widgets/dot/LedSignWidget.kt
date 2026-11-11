package com.dotos.feature.widgets.dot

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dotos.core.designsystem.LocalReduceMotion
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetShape
import com.dotos.feature.widgets.api.WidgetSize
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val LED_ROWS = 7
private const val LED_MAX_TEXT = 160

/** Own 5x7 dot-matrix font ('X' = lit). Glyph width = longest row; one blank column separates glyphs. */
private object LedFont {
    private fun g(vararg rows: String): List<String> = rows.toList()

    private val glyphs: Map<Char, List<String>> = mapOf(
        'A' to g(".XXX.", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"),
        'B' to g("XXXX.", "X...X", "X...X", "XXXX.", "X...X", "X...X", "XXXX."),
        'C' to g(".XXX.", "X...X", "X....", "X....", "X....", "X...X", ".XXX."),
        'D' to g("XXXX.", "X...X", "X...X", "X...X", "X...X", "X...X", "XXXX."),
        'E' to g("XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "XXXXX"),
        'F' to g("XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "X...."),
        'G' to g(".XXX.", "X...X", "X....", "X.XXX", "X...X", "X...X", ".XXXX"),
        'H' to g("X...X", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"),
        'I' to g(".XXX.", "..X..", "..X..", "..X..", "..X..", "..X..", ".XXX."),
        'J' to g("..XXX", "...X.", "...X.", "...X.", "...X.", "X..X.", ".XX.."),
        'K' to g("X...X", "X..X.", "X.X..", "XX...", "X.X..", "X..X.", "X...X"),
        'L' to g("X....", "X....", "X....", "X....", "X....", "X....", "XXXXX"),
        'M' to g("X...X", "XX.XX", "X.X.X", "X.X.X", "X...X", "X...X", "X...X"),
        'N' to g("X...X", "X...X", "XX..X", "X.X.X", "X..XX", "X...X", "X...X"),
        'O' to g(".XXX.", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."),
        'P' to g("XXXX.", "X...X", "X...X", "XXXX.", "X....", "X....", "X...."),
        'Q' to g(".XXX.", "X...X", "X...X", "X...X", "X.X.X", "X..X.", ".XX.X"),
        'R' to g("XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"),
        'S' to g(".XXXX", "X....", "X....", ".XXX.", "....X", "....X", "XXXX."),
        'T' to g("XXXXX", "..X..", "..X..", "..X..", "..X..", "..X..", "..X.."),
        'U' to g("X...X", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."),
        'V' to g("X...X", "X...X", "X...X", "X...X", "X...X", ".X.X.", "..X.."),
        'W' to g("X...X", "X...X", "X...X", "X.X.X", "X.X.X", "XX.XX", "X...X"),
        'X' to g("X...X", "X...X", ".X.X.", "..X..", ".X.X.", "X...X", "X...X"),
        'Y' to g("X...X", "X...X", ".X.X.", "..X..", "..X..", "..X..", "..X.."),
        'Z' to g("XXXXX", "....X", "...X.", "..X..", ".X...", "X....", "XXXXX"),
        '0' to g(".XXX.", "X...X", "X..XX", "X.X.X", "XX..X", "X...X", ".XXX."),
        '1' to g("..X..", ".XX..", "..X..", "..X..", "..X..", "..X..", ".XXX."),
        '2' to g(".XXX.", "X...X", "....X", "...X.", "..X..", ".X...", "XXXXX"),
        '3' to g("XXXXX", "...X.", "..X..", "...X.", "....X", "X...X", ".XXX."),
        '4' to g("...X.", "..XX.", ".X.X.", "X..X.", "XXXXX", "...X.", "...X."),
        '5' to g("XXXXX", "X....", "XXXX.", "....X", "....X", "X...X", ".XXX."),
        '6' to g("..XX.", ".X...", "X....", "XXXX.", "X...X", "X...X", ".XXX."),
        '7' to g("XXXXX", "....X", "...X.", "..X..", ".X...", ".X...", ".X..."),
        '8' to g(".XXX.", "X...X", "X...X", ".XXX.", "X...X", "X...X", ".XXX."),
        '9' to g(".XXX.", "X...X", "X...X", ".XXXX", "....X", "...X.", ".XX.."),
        ' ' to g("...", "...", "...", "...", "...", "...", "..."),
        '.' to g(".", ".", ".", ".", ".", ".", "X"),
        ',' to g("..", "..", "..", "..", ".X", ".X", "X."),
        '!' to g("X", "X", "X", "X", "X", ".", "X"),
        '?' to g(".XXX.", "X...X", "....X", "...X.", "..X..", ".....", "..X.."),
        ':' to g(".", ".", "X", ".", "X", ".", "."),
        '-' to g("....", "....", "....", "XXXX", "....", "....", "...."),
        '\'' to g("X", "X", ".", ".", ".", ".", "."),
        '/' to g("....X", "....X", "...X.", "..X..", ".X...", "X....", "X...."),
        '+' to g(".....", "..X..", "..X..", "XXXXX", "..X..", "..X..", "....."),
    )

    private val blank = glyphs.getValue(' ')

    private fun glyphFor(c: Char): List<String> = glyphs[c] ?: blank

    /** One Int per column; bit r (0 = top) is set when row r is lit. */
    fun render(text: String): IntArray {
        if (text.isEmpty()) return IntArray(0)
        val list = text.map { glyphFor(it) }
        var total = list.size - 1
        for (glyph in list) total += glyph.maxOf { it.length }
        val columns = IntArray(total.coerceAtLeast(0))
        var x = 0
        for (glyph in list) {
            val width = glyph.maxOf { it.length }
            for (c in 0 until width) {
                var bits = 0
                for (r in 0 until LED_ROWS) {
                    if (glyph.getOrNull(r)?.getOrNull(c) == 'X') bits = bits or (1 shl r)
                }
                if (x + c < columns.size) columns[x + c] = bits
            }
            x += width + 1
        }
        return columns
    }
}

private class LedLayout(
    val pitch: Float,
    val dot: Float,
    val left: Float,
    val top: Float,
    val gridW: Float,
    val gridH: Float,
    /** Static text offset from the grid's left edge. */
    val textX: Float,
    /** Text offset from the grid's top edge (text is vertically centred in the panel). */
    val textTop: Float,
    val textW: Float,
    val scrolling: Boolean,
    /** Distance the marquee travels before it restarts. */
    val cycle: Float,
    /** x,y pairs of every dot of the panel (unlit background), absolute. */
    val grid: FloatArray,
    /** x,y pairs of the lit dots, relative to the text origin. */
    val lit: FloatArray,
)

private class LedPaints(dot: Float, unlitArgb: Int, litArgb: Int, glowArgb: Int) {
    val unlit: Paint = pointPaint(dot, unlitArgb)
    val glow: Paint = pointPaint(dot * 1.9f, glowArgb)
    val lit: Paint = pointPaint(dot, litArgb)

    private fun pointPaint(width: Float, argb: Int): Paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = width
        color = argb
    }
}

@Suppress("LongParameterList")
private fun buildLedLayout(
    bits: IntArray,
    wPx: Float,
    hPx: Float,
    padX: Float,
    padY: Float,
    minStaticPitch: Float,
    scrollPitchCap: Float,
    minPitch: Float,
    reduceMotion: Boolean,
): LedLayout {
    val availW = (wPx - 2f * padX).coerceAtLeast(1f)
    val availH = (hPx - 2f * padY).coerceAtLeast(1f)
    val textCols = bits.size
    val maxPitchForHeight = availH / LED_ROWS
    val pitchToFit = if (textCols > 0) availW / textCols else maxPitchForHeight
    val fits = textCols == 0 || pitchToFit >= minStaticPitch
    val scrolling = !fits && !reduceMotion
    val wanted = if (fits) min(pitchToFit, maxPitchForHeight) else min(maxPitchForHeight, scrollPitchCap)
    val pitch = max(wanted, minPitch)

    val gridCols = max(1, floor(availW / pitch).toInt())
    val gridRows = max(1, floor(availH / pitch).toInt())
    val gridW = gridCols * pitch
    val gridH = gridRows * pitch
    val left = (wPx - gridW) / 2f
    val top = (hPx - gridH) / 2f

    val rowOffset = max(0, (gridRows - LED_ROWS) / 2)
    val colOffset = if (textCols <= gridCols) (gridCols - textCols) / 2 else 0

    val grid = FloatArray(gridCols * gridRows * 2)
    var k = 0
    for (r in 0 until gridRows) {
        for (c in 0 until gridCols) {
            grid[k++] = left + (c + 0.5f) * pitch
            grid[k++] = top + (r + 0.5f) * pitch
        }
    }

    var litCount = 0
    for (column in bits) litCount += column.countOneBits()
    val lit = FloatArray(litCount * 2)
    var j = 0
    for (c in bits.indices) {
        for (r in 0 until LED_ROWS) {
            if (((bits[c] shr r) and 1) == 1) {
                lit[j++] = (c + 0.5f) * pitch
                lit[j++] = (r + 0.5f) * pitch
            }
        }
    }

    val textW = textCols * pitch
    return LedLayout(
        pitch = pitch,
        dot = pitch * 0.62f,
        left = left,
        top = top,
        gridW = gridW,
        gridH = gridH,
        textX = colOffset * pitch,
        textTop = rowOffset * pitch,
        textW = textW,
        scrolling = scrolling,
        cycle = gridW + textW + pitch * 3f,
        grid = grid,
        lit = lit,
    )
}

/** Columns per second: "slow" | "normal" | "fast" or a number. */
private fun parseLedSpeed(raw: String?): Float = when (raw?.lowercase()) {
    null, "", "normal" -> 10f
    "slow" -> 6f
    "fast" -> 16f
    else -> raw?.toFloatOrNull()?.coerceIn(2f, 40f) ?: 10f
}

object LedSignWidget : DotWidgetProvider {
    override val kind: String = "led_sign"
    override val title: String = "LED Sign"
    override val description: String = "Scrolling or static text in big dot-LED letters. Set the text in the widget options."
    override val sizes: List<WidgetSize> = listOf(WidgetSize(4, 1), WidgetSize(4, 2))

    override fun shape(size: WidgetSize): WidgetShape = if (size.spanY <= 1) WidgetShape.PILL else WidgetShape.ROUNDED

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val reduceMotion = LocalReduceMotion.current
        val resumed = rememberIsResumed()
        val preview = instance.isPreview
        val pill = instance.spanY <= 1

        val text = remember(instance.config) {
            (instance.config["text"] ?: "HELLO").replace('\n', ' ').trim().take(LED_MAX_TEXT).uppercase()
        }
        val speed = remember(instance.config) { parseLedSpeed(instance.config["speed"]) }
        val bits = remember(text) { LedFont.render(text) }
        val litColor = if (instance.config["color"] == "white") style.content else style.accent
        val unlitColor = style.content.copy(alpha = 0.09f)

        BoxWithConstraints(modifier.semantics { contentDescription = "LED sign: $text" }) {
            val density = LocalDensity.current.density
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val layout = remember(bits, wPx, hPx, density, reduceMotion, pill) {
                buildLedLayout(
                    bits = bits,
                    wPx = wPx,
                    hPx = hPx,
                    padX = if (pill) min(hPx * 0.38f, 44f * density) else 16f * density,
                    padY = if (pill) 14f * density else 16f * density,
                    minStaticPitch = 6f * density,
                    scrollPitchCap = 12f * density,
                    minPitch = 4f * density,
                    reduceMotion = reduceMotion,
                )
            }
            val paints = remember(layout.dot, litColor, unlitColor) {
                LedPaints(layout.dot, unlitColor.toArgb(), litColor.toArgb(), litColor.copy(alpha = 0.18f).toArgb())
            }

            var scroll by remember(layout) { mutableFloatStateOf(0f) }
            val pixelsPerSecond = speed * layout.pitch
            // The only frame loop: marquee text, visible launcher, no preview.
            LaunchedEffect(layout, resumed, pixelsPerSecond) {
                if (!layout.scrolling || !resumed || preview) return@LaunchedEffect
                var last = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now ->
                        val dt = (now - last) / 1_000_000_000f
                        last = now
                        scroll = (scroll + pixelsPerSecond * dt) % layout.cycle
                    }
                }
            }

            Canvas(Modifier.fillMaxSize()) {
                val canvas = drawContext.canvas.nativeCanvas
                canvas.drawPoints(layout.grid, paints.unlit)
                if (layout.lit.isNotEmpty()) {
                    clipRect(layout.left, layout.top, layout.left + layout.gridW, layout.top + layout.gridH) {
                        val x = if (layout.scrolling && !preview) layout.gridW - scroll else layout.textX
                        translate(layout.left + x, layout.top + layout.textTop) {
                            canvas.drawPoints(layout.lit, paints.glow)
                            canvas.drawPoints(layout.lit, paints.lit)
                        }
                    }
                }
            }
        }
    }
}

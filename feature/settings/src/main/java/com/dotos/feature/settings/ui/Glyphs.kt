package com.dotos.feature.settings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A tiny 5x5 dot-matrix glyph. '#' = lit dot. Drawn on one Canvas. */
@Composable
internal fun DotMatrixGlyph(
    pattern: List<String>,
    modifier: Modifier = Modifier,
    color: Color = DotTheme.colors.onSurface,
    glyphSize: Dp = 28.dp,
) {
    Canvas(modifier.size(glyphSize)) {
        val cols = pattern.maxOf { it.length }
        val step = size.width / cols
        val radius = step * 0.32f
        pattern.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch == '#') drawCircle(color, radius, Offset((x + 0.5f) * step, (y + 0.5f) * step))
            }
        }
    }
}

/** Dot-matrix glyph per top-level settings section. */
internal object SectionGlyphs {
    val home = listOf("..#..", ".###.", "#####", "##.##", "##.##")
    val drawer = listOf("##.##", "##.##", ".....", "##.##", "##.##")
    val icons = listOf(".###.", "#...#", "#.#.#", "#...#", ".###.")
    val wallpaper = listOf("....#", "..#..", ".###.", "#####", "#####")
    val widgets = listOf("###..", "###..", "...##", "...##", ".....")
    val folders = listOf("##...", "#####", "#...#", "#...#", "#####")
    val gestures = listOf("..#..", ".###.", "#.#.#", "..#..", "..#..")
    val theme = listOf(".###.", "##..#", "##..#", "##..#", ".###.")
    val badges = listOf("..#..", ".###.", ".###.", "#####", "..#..")
    val privacy = listOf(".###.", "#...#", "#####", "##.##", "#####")
    val backup = listOf("..#..", "..#..", "#.#.#", ".###.", "#####")
    val performance = listOf("..###", ".##..", ".###.", "..##.", ".##..")
    val about = listOf("..#..", ".....", "..#..", "..#..", "..#..")
}

@Composable
internal fun MagnifierIcon(modifier: Modifier = Modifier, color: Color = DotTheme.colors.onSurface) {
    Canvas(modifier) {
        val d = size.minDimension
        val sw = d * 0.12f
        drawCircle(color, radius = d * 0.32f, center = Offset(d * 0.42f, d * 0.42f), style = Stroke(width = sw))
        drawLine(color, Offset(d * 0.66f, d * 0.66f), Offset(d * 0.92f, d * 0.92f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
internal fun CloseIcon(modifier: Modifier = Modifier, color: Color = DotTheme.colors.onSurfaceMuted) {
    Canvas(modifier.size(14.dp)) {
        val sw = 1.6.dp.toPx()
        drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
internal fun ChevronIcon(modifier: Modifier = Modifier, color: Color = DotTheme.colors.onSurfaceMuted) {
    Canvas(modifier.size(width = 8.dp, height = 14.dp)) {
        val sw = 1.8.dp.toPx()
        drawLine(color, Offset(0f, 0f), Offset(size.width, size.height / 2f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width, size.height / 2f), Offset(0f, size.height), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** Radio indicator: ring, with an inner dot when selected. */
@Composable
internal fun RadioDot(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = DotTheme.colors
    Canvas(modifier.size(22.dp)) {
        val r = size.minDimension / 2f
        drawCircle(
            if (selected) colors.onSurface else colors.hairline,
            radius = r - 1.dp.toPx(),
            style = Stroke(width = 1.5.dp.toPx()),
        )
        if (selected) drawCircle(colors.onSurface, radius = r * 0.5f)
    }
}

/** Split-pie "Colours" glyph. */
@Composable
internal fun ColoursIcon(modifier: Modifier = Modifier) {
    val colors = DotTheme.colors
    Canvas(modifier.size(26.dp)) {
        val d = size.minDimension
        val box = Size(d, d)
        drawArc(colors.onSurface, 180f, 90f, true, Offset.Zero, box)
        drawArc(colors.onSurfaceMuted, 270f, 90f, true, Offset.Zero, box)
        drawArc(colors.surfaceRaised, 90f, 90f, true, Offset.Zero, box)
        drawArc(colors.onSurface, 0f, 90f, true, Offset.Zero, box)
    }
}

/** Donut "Icon Pack" glyph. */
@Composable
internal fun IconPackIcon(modifier: Modifier = Modifier) {
    val colors = DotTheme.colors
    Canvas(modifier.size(26.dp)) {
        val r = size.minDimension / 2f
        drawCircle(colors.onSurfaceMuted, radius = r * 0.78f, style = Stroke(width = r * 0.42f))
        drawCircle(colors.onSurface, radius = r * 0.24f)
    }
}

/** "#" Layout glyph. */
@Composable
internal fun LayoutIcon(modifier: Modifier = Modifier) {
    val colors = DotTheme.colors
    Canvas(modifier.size(24.dp)) {
        val sw = 2.2.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(colors.onSurface, Offset(w * 0.36f, h * 0.08f), Offset(w * 0.30f, h * 0.92f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(colors.onSurface, Offset(w * 0.70f, h * 0.08f), Offset(w * 0.64f, h * 0.92f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(colors.onSurface, Offset(w * 0.08f, h * 0.36f), Offset(w * 0.92f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(colors.onSurface, Offset(w * 0.08f, h * 0.68f), Offset(w * 0.92f, h * 0.68f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** Three wavy lines: the Atmosphere glyph. */
@Composable
internal fun WavesIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(26.dp)) {
        val sw = 2.dp.toPx()
        val steps = 24
        for (k in 0..2) {
            val y0 = size.height * (0.24f + 0.26f * k)
            val path = Path()
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = size.width * t
                val y = y0 + sin(t * 2f * PI.toFloat() * 1.5f) * size.height * 0.07f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Dot-hexagon flower: the Glass glyph. */
@Composable
internal fun GlassIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(26.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.32f
        val dot = size.minDimension * 0.1f
        drawCircle(color, dot, c)
        for (i in 0 until 6) {
            val a = (i * 60f - 90f) * PI.toFloat() / 180f
            drawCircle(color, dot, Offset(c.x + cos(a) * r, c.y + sin(a) * r))
        }
    }
}

/** Ring with three dots: "More wallpapers". */
@Composable
internal fun MoreRingIcon(modifier: Modifier = Modifier, color: Color = DotTheme.colors.onSurface) {
    Canvas(modifier.size(22.dp)) {
        val r = size.minDimension / 2f
        drawCircle(color, radius = r - 1.dp.toPx(), style = Stroke(width = 1.6.dp.toPx()))
        val dot = r * 0.11f
        for (i in -1..1) drawCircle(color, dot, Offset(r + i * r * 0.36f, r))
    }
}

/** Plus sign for "Add / Choose". */
@Composable
internal fun PlusIcon(modifier: Modifier = Modifier, color: Color = DotTheme.colors.onSurface) {
    Canvas(modifier.size(22.dp)) {
        val sw = 1.8.dp.toPx()
        drawLine(color, Offset(size.width / 2f, 2.dp.toPx()), Offset(size.width / 2f, size.height - 2.dp.toPx()), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(2.dp.toPx(), size.height / 2f), Offset(size.width - 2.dp.toPx(), size.height / 2f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** Sliders glyph used for "Customise". */
@Composable
internal fun TuneIcon(modifier: Modifier = Modifier, color: Color = DotTheme.colors.onSurface) {
    Canvas(modifier.size(24.dp)) {
        val sw = 1.8.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.1f, h * 0.3f), Offset(w * 0.9f, h * 0.3f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.1f, h * 0.7f), Offset(w * 0.9f, h * 0.7f), strokeWidth = sw, cap = StrokeCap.Round)
        drawCircle(color, radius = h * 0.12f, center = Offset(w * 0.65f, h * 0.3f))
        drawCircle(color, radius = h * 0.12f, center = Offset(w * 0.35f, h * 0.7f))
    }
}

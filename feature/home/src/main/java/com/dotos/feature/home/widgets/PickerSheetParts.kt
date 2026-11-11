package com.dotos.feature.home.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotPalette

/**
 * The picker and face sheets are dark in every theme (the reference is a near-black sheet over a dimmed
 * home), so they use fixed colours instead of the light/dark aware theme colours.
 */
internal object SheetColors {
    val Background = DotPalette.Grey950
    val Card = DotPalette.Grey850
    val Field = Color(0xFF242424)
    val Ink = DotPalette.White
    val Muted = DotPalette.Grey500
    val Section = Color(0xFFB0B0B0)
    val Handle = DotPalette.Grey600
    val Scrim = Color(0x99000000)
    val ButtonFill = DotPalette.Paper
    val ButtonInk = DotPalette.Black
}

internal val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

/** 36 x 4 dp grey pill. */
@Composable
internal fun SheetDragHandle() {
    Box(
        Modifier
            .padding(vertical = 10.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(SheetColors.Handle),
    )
}

@Composable
internal fun SearchGlyph(modifier: Modifier = Modifier, color: Color = SheetColors.Muted) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = s * 0.11f
        val radius = s * 0.33f
        val center = Offset(s * 0.42f, s * 0.42f)
        drawCircle(color, radius, center, style = Stroke(width = stroke))
        drawLine(
            color,
            Offset(center.x + radius * 0.72f, center.y + radius * 0.72f),
            Offset(s * 0.9f, s * 0.9f),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
internal fun CloseGlyph(modifier: Modifier = Modifier, color: Color = SheetColors.Ink) {
    Canvas(modifier) {
        val s = size.minDimension
        val inset = s * 0.28f
        val stroke = s * 0.09f
        drawLine(color, Offset(inset, inset), Offset(s - inset, s - inset), stroke, StrokeCap.Round)
        drawLine(color, Offset(s - inset, inset), Offset(inset, s - inset), stroke, StrokeCap.Round)
    }
}

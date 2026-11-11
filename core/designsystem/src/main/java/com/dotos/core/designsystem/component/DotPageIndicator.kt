package com.dotos.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Dot page indicator. Reads [position] (page + fraction) only in the draw phase,
 * so scrolling never recomposes it.
 */
@Composable
fun DotPageIndicator(
    pageCount: Int,
    position: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
    dotSize: Dp = 5.dp,
    spacing: Dp = 7.dp,
) {
    if (pageCount <= 1) return
    Canvas(
        modifier
            .width(dotSize * pageCount + spacing * (pageCount - 1))
            .height(dotSize),
    ) {
        val r = dotSize.toPx() / 2f
        val step = dotSize.toPx() + spacing.toPx()
        val p = position()
        for (i in 0 until pageCount) {
            val distance = abs(p - i).coerceIn(0f, 1f)
            val alpha = 1f - 0.65f * distance
            drawCircle(color.copy(alpha = color.alpha * alpha), radius = r, center = Offset(r + i * step, r))
        }
    }
}

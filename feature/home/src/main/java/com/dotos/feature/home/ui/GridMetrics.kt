package com.dotos.feature.home.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.floor
import kotlin.math.min

/**
 * Geometry of one home page, in px. Cells are as wide as the grid allows; content uses a square
 * "pitch" so 2x2 widgets stay square and 1x1 plates are ~77 % of the pitch (Nothing proportions:
 * 72 dp plate in a 93 dp pitch on a 4-column phone).
 */
@Immutable
data class GridMetrics(
    val cols: Int,
    val rows: Int,
    val width: Float,
    val height: Float,
) {
    val cellW: Float = width / cols
    val cellH: Float = height / rows
    val pitch: Float = min(cellW, cellH)
    val gutter: Float = pitch * 0.226f
    val plate: Float = pitch - gutter

    /** Content rect for an item at (x, y) spanning (sx, sy), centred inside its cells. */
    fun itemRect(x: Int, y: Int, sx: Int, sy: Int): Rect {
        val w = sx * pitch - gutter
        val h = sy * pitch - gutter
        val cx = (x + sx / 2f) * cellW
        val cy = (y + sy / 2f) * cellH
        return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /** Cell under a local point (clamped). */
    fun cellAt(p: Offset): Pair<Int, Int> =
        floor(p.x / cellW).toInt().coerceIn(0, cols - 1) to floor(p.y / cellH).toInt().coerceIn(0, rows - 1)
}

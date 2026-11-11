package com.dotos.feature.widgets.dot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotFonts
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetShape
import com.dotos.feature.widgets.api.WidgetSize
import kotlin.math.max

/** A 4x1 search pill: magnifier on the left, "Search" in the middle. A tap opens the launcher search. */
object SearchWidget : DotWidgetProvider {
    override val kind = "search"
    override val title = "Search"
    override val description = "A search pill that opens the launcher search."
    override val sizes = listOf(WidgetSize(4, 1))

    override fun shape(size: WidgetSize): WidgetShape = WidgetShape.PILL

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val host = LocalWidgetHost.current
        val measurer = rememberTextMeasurer()
        Box(
            modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) { contentDescription = BaStrings.SEARCH }
                .baTap(enabled = !instance.isPreview) { host.openSearch() }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val cy = h / 2f
                    val glyph = (h * 0.2f).coerceIn(14.dp.toPx(), 22.dp.toPx())
                    val glyphX = max(h * 0.46f, 18.dp.toPx() + glyph / 2f)
                    val textMax = (w - 2f * (glyphX + glyph)).coerceAtLeast(1f)
                    val label = baText(measurer, BaStrings.SEARCH, glyph * 0.85f, DotFonts.Grotesk, FontWeight.Medium, textMax)

                    val ringR = glyph * 0.34f
                    val ringCenter = Offset(glyphX - glyph * 0.1f, cy - glyph * 0.1f)
                    val strokeWidth = max(2.dp.toPx(), glyph * 0.11f)
                    val ringStroke = Stroke(width = strokeWidth)
                    val handleFrom = Offset(ringCenter.x + ringR * 0.7071f, ringCenter.y + ringR * 0.7071f)
                    val handleTo = Offset(glyphX + glyph * 0.44f, cy + glyph * 0.44f)
                    onDrawBehind {
                        drawCircle(style.content, ringR, ringCenter, style = ringStroke)
                        drawLine(style.content, handleFrom, handleTo, strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        baDrawText(label, style.content, w / 2f, cy, capEm = 0.6f)
                    }
                },
        )
    }
}

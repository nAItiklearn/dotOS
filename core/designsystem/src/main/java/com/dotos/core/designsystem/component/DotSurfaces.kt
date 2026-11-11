package com.dotos.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.sin

/** Widget/card container: large radius, near-black fill, hairline border on dark. */
@Composable
fun DotCard(
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    color: Color = DotTheme.colors.surface,
    border: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(color, shape)
            .then(if (border && DotTheme.colors.isDark) Modifier.border(1.dp, DotTheme.colors.hairline, shape) else Modifier),
        content = content,
    )
}

/** Round action with a label underneath (e.g. "Colours", "Icon Pack", "Layout"). */
@Composable
fun DotRoundAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 64.dp,
    icon: @Composable BoxScope.() -> Unit,
) {
    val colors = DotTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .pressScale(interaction)
                .clip(CircleShape)
                .background(if (selected) colors.onSurface else colors.surface)
                .then(if (selected) Modifier else Modifier.border(1.dp, colors.hairline, CircleShape))
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
            content = icon,
        )
        Text(
            label,
            style = DotTheme.type.bodySmall,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Row of round options, e.g. grid columns "4 5". */
@Composable
fun <T> DotChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DotTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.onSurface else Color.Transparent)
                    .border(1.dp, if (isSelected) colors.onSurface else colors.hairline, CircleShape)
                    .clickable(role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = DotTheme.type.body,
                    color = if (isSelected) colors.background else colors.onSurface,
                )
            }
        }
    }
}

/** Two-segment pill selector ("Wallpaper colours | Basic colours"). */
@Composable
fun DotSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
) {
    val colors = DotTheme.colors
    Row(
        modifier
            .clip(shape)
            .background(colors.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, text ->
            val sel = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(shape)
                    .background(if (sel) colors.onSurface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = DotTheme.type.bodySmall, color = if (sel) colors.background else colors.onSurface)
            }
        }
    }
}

@Composable
fun DotDivider(modifier: Modifier = Modifier, color: Color = DotTheme.colors.hairline) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
        )
    }
}

/** Three staggered pulsing dots. One Canvas, never one composable per dot. */
@Composable
fun DotLoading(modifier: Modifier = Modifier, color: Color = DotTheme.colors.onSurface) {
    val reduce = LocalReduceMotion.current
    var t by remember { mutableFloatStateOf(0f) }
    if (!reduce) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { t = (it - start) / 1_000_000_000f }
        }
    }
    Canvas(modifier.size(width = 36.dp, height = 8.dp)) {
        val r = size.height / 2f
        val step = (size.width - 2 * r) / 2f
        for (i in 0..2) {
            val phase = sin((t * 2f * PI.toFloat()) - i * 0.9f) * 0.5f + 0.5f
            drawCircle(color.copy(alpha = 0.3f + 0.7f * if (reduce) 1f else phase), radius = r, center = Offset(r + i * step, r))
        }
    }
}

package com.dotos.feature.settings.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.DotSegmented
import com.dotos.core.designsystem.component.pressScale
import com.dotos.core.designsystem.rememberDotHaptics
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Slider interaction state. While dragging, the slider shows its own value (instant, no jitter even
 * though the real value round-trips through DataStore); shortly after release it hands back to the
 * stored value.
 */
@Stable
internal class DragState(initial: Float) {
    var local by mutableFloatStateOf(initial)
    var dragging by mutableStateOf(false)
    var released by mutableStateOf(false)

    fun shown(upstream: Float): Float = if (dragging) local else upstream
}

@Composable
internal fun rememberDragState(value: Float): DragState {
    val state = remember { DragState(value) }
    LaunchedEffect(state.released) {
        if (state.released) {
            delay(300)
            state.dragging = false
            state.released = false
        }
    }
    return state
}

@Composable
internal fun DotSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    drag: DragState = rememberDragState(value),
) {
    val colors = DotTheme.colors
    Slider(
        value = drag.shown(value).coerceIn(valueRange),
        onValueChange = { v ->
            drag.local = v
            drag.dragging = true
            drag.released = false
            onValueChange(v)
        },
        onValueChangeFinished = { drag.released = true },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = colors.onSurface,
            activeTrackColor = colors.onSurface,
            activeTickColor = Color.Transparent,
            inactiveTrackColor = colors.surfaceRaised,
            inactiveTickColor = Color.Transparent,
            disabledThumbColor = colors.onSurfaceMuted,
            disabledActiveTrackColor = colors.onSurfaceMuted,
            disabledInactiveTrackColor = colors.surfaceRaised,
        ),
    )
}

/** Title + live value on one line, optional description, slider below. */
@Composable
internal fun SliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    description: String? = null,
    enabled: Boolean = true,
) {
    val colors = DotTheme.colors
    val drag = rememberDragState(value)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = DotTheme.type.title,
                color = if (enabled) colors.onSurface else colors.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            Text(valueText(drag.shown(value)), style = DotTheme.type.mono, color = colors.onSurfaceMuted)
        }
        if (description != null) {
            Text(description, style = DotTheme.type.bodySmall, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 2.dp))
        }
        DotSlider(value, onValueChange, valueRange, steps = steps, enabled = enabled, drag = drag)
    }
}

@Composable
internal fun IntSliderRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    suffix: String = "",
    description: String? = null,
    enabled: Boolean = true,
) {
    SliderRow(
        title = title,
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        valueText = { "${it.roundToInt()}$suffix" },
        modifier = modifier,
        steps = (range.last - range.first - 1).coerceAtLeast(0),
        description = description,
        enabled = enabled,
    )
}

internal fun percentText(v: Float): String = "${(v * 100f).roundToInt()}%"

internal fun decimalText(v: Float, digits: Int = 2): String = String.format(Locale.US, "%.${digits}f", v)

/** Full-width segmented control for an enum / list of options. */
@Composable
internal fun <T> OptionSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberDotHaptics()
    DotSegmented(
        options = options.map(label),
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        onSelect = { index ->
            haptics.tick()
            onSelect(options[index])
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
    )
}

/** Round digit selector ("4 | 5") in the dot display font. Selected = filled with inverted digit. */
@Composable
internal fun DigitSelector(
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DotTheme.colors
    val haptics = rememberDotHaptics()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(48.dp)
                    .pressScale(interaction)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.onSurface else colors.surface)
                    .then(if (isSelected) Modifier else Modifier.border(1.dp, colors.hairline, CircleShape))
                    .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton) {
                        haptics.tick()
                        onSelect(option)
                    }
                    .semantics { contentDescription = "$option columns" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.toString(),
                    style = DotTheme.type.dotTitle,
                    color = if (isSelected) colors.background else colors.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit, size: Dp = 34.dp, description: String = "Colour") {
    val colors = DotTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(size)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.onSurface else colors.hairline, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
    )
}

internal val CuratedSwatches: List<Color> = listOf(
    Color(0xFF000000), Color(0xFF1A1A1A), Color(0xFF5C5C5C), Color(0xFF8A8A8A),
    Color(0xFFC8C0B6), Color(0xFFE7E2DA), Color(0xFFF2F2F2), Color(0xFFFFFFFF),
    Color(0xFFD71921), Color(0xFFE85630), Color(0xFFFFB020), Color(0xFFFDF9AF),
    Color(0xFF3DDC84), Color(0xFF4C8DFF), Color(0xFF6B4EFF), Color(0xFFE54B9A),
)

/**
 * Simple palette picker: 16 curated swatches plus hue / saturation / brightness sliders.
 * Keep it in a `key(slot)` block so switching slots re-initialises it.
 */
@Composable
internal fun ColorPickerPanel(color: Color, onColorChange: (Color) -> Unit, modifier: Modifier = Modifier) {
    val start = remember { FloatArray(3).also { AColor.colorToHSV(color.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var bri by remember { mutableFloatStateOf(start[2]) }

    fun emit() {
        onColorChange(Color(AColor.HSVToColor(floatArrayOf(hue, sat, bri))))
    }

    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CuratedSwatches.chunked(8).forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowColors.forEach { c ->
                        ColorDot(
                            color = c,
                            selected = c.toArgb() == color.toArgb(),
                            onClick = {
                                val hsv = FloatArray(3)
                                AColor.colorToHSV(c.toArgb(), hsv)
                                hue = hsv[0]
                                sat = hsv[1]
                                bri = hsv[2]
                                onColorChange(c)
                            },
                        )
                    }
                }
            }
        }
        SliderRow(
            title = "Hue",
            value = hue,
            onValueChange = {
                hue = it
                emit()
            },
            valueRange = 0f..360f,
            valueText = { "${it.roundToInt()}°" },
        )
        SliderRow(
            title = "Saturation",
            value = sat,
            onValueChange = {
                sat = it
                emit()
            },
            valueRange = 0f..1f,
            valueText = ::percentText,
        )
        SliderRow(
            title = "Brightness",
            value = bri,
            onValueChange = {
                bri = it
                emit()
            },
            valueRange = 0f..1f,
            valueText = ::percentText,
        )
    }
}

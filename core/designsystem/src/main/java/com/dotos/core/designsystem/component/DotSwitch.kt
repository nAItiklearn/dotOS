package com.dotos.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.dotSpring
import com.dotos.core.designsystem.rememberDotHaptics

/** Pill track with a single dot thumb. */
@Composable
fun DotSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = DotTheme.colors
    val haptics = rememberDotHaptics()
    val fraction by animateFloatAsState(if (checked) 1f else 0f, dotSpring(), label = "switch")
    val track by animateColorAsState(if (checked) colors.onSurface else colors.surfaceRaised, label = "track")
    val thumb by animateColorAsState(if (checked) colors.background else colors.onSurfaceMuted, label = "thumb")
    val interaction = remember { MutableInteractionSource() }
    Canvas(
        modifier
            .size(width = 46.dp, height = 26.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
                onValueChange = {
                    haptics.toggle()
                    onCheckedChange(it)
                },
            ),
    ) {
        val h = size.height
        drawRoundRect(track.copy(alpha = if (enabled) 1f else 0.4f), cornerRadius = CornerRadius(h / 2f))
        val r = h * 0.30f
        val pad = h / 2f
        val x = pad + (size.width - 2 * pad) * fraction
        drawCircle(thumb, radius = r, center = Offset(x, h / 2f))
    }
}

package com.dotos.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.dotos.core.designsystem.DotMotion
import com.dotos.core.designsystem.LocalReduceMotion

/** Scales to 0.94 on press (120ms), springs back on release. Transform only: never relayouts. */
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = DotMotion.PRESS_SCALE): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource, reduce) {
        if (reduce) return@LaunchedEffect
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.animateTo(pressedScale, DotMotion.press())
                is PressInteraction.Release, is PressInteraction.Cancel -> scale.animateTo(1f, DotMotion.spring())
            }
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

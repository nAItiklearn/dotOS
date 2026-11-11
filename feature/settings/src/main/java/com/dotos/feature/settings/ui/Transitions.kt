package com.dotos.feature.settings.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotMotion

/** 24 dp in pixels: the horizontal slide distance of every screen transition. */
@Composable
internal fun rememberSlidePx(): Int {
    val density = LocalDensity.current
    return remember(density) { with(density) { 24.dp.roundToPx() } }
}

/**
 * Fade + 24 dp horizontal slide on the DotOS spring (0.8 / 380). [forward] = push, otherwise pop.
 * With reduce-motion everything collapses to an instant swap.
 */
internal fun screenTransition(forward: Boolean, slidePx: Int, reduce: Boolean): ContentTransform {
    val fade: FiniteAnimationSpec<Float> = if (reduce) snap() else DotMotion.spring()
    val slide: FiniteAnimationSpec<IntOffset> = if (reduce) snap() else DotMotion.spring()
    val dx = if (reduce) 0 else slidePx
    val enterFrom = if (forward) dx else -dx
    val exitTo = if (forward) -dx else dx
    return (fadeIn(fade) + slideInHorizontally(slide) { enterFrom }) togetherWith
        (fadeOut(fade) + slideOutHorizontally(slide) { exitTo })
}

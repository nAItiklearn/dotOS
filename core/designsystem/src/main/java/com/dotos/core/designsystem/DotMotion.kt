package com.dotos.core.designsystem

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Motion tokens. Springs, not tweens. */
object DotMotion {
    const val DAMPING = 0.8f
    const val STIFFNESS = 380f
    const val PRESS_SCALE = 0.94f
    const val PRESS_MILLIS = 120

    fun <T> spring(): FiniteAnimationSpec<T> = spring(dampingRatio = DAMPING, stiffness = STIFFNESS)
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 200f)
    fun <T> press(): FiniteAnimationSpec<T> = tween(PRESS_MILLIS)
}

/** True when the user asked for no motion (launcher toggle OR system "Remove animations"). */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberSystemAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Decorative animation spec: collapses to snap() when reduce-motion is on. */
@Composable
@ReadOnlyComposable
fun <T> dotSpring(): AnimationSpec<T> = if (LocalReduceMotion.current) snap() else DotMotion.spring()

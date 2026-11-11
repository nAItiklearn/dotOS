package com.dotos.core.designsystem

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

val LocalHapticsEnabled = compositionLocalOf { true }

@Stable
class DotHaptics(private val view: View, private val enabled: Boolean) {
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)
    fun pick() = perform(HapticFeedbackConstants.LONG_PRESS)
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun reject() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun toggle() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK)

    private fun perform(constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberDotHaptics(): DotHaptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { DotHaptics(view, enabled) }
}

package com.dotos.feature.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Drives the drawer reveal (0 = closed, 1 = open) from home swipes and drawer scroll. */
@Stable
class DrawerController(private val scope: CoroutineScope) {
    val progress = Animatable(0f)

    /** Distance in px that maps to a full open (set by the host from the screen height). */
    var travelPx: Float = 1600f

    /** Set when the drawer should focus its search field on open. */
    var focusSearchRequest: Int = 0
        private set

    val isOpen: Boolean get() = progress.targetValue > 0.5f

    fun dragBy(dyPx: Float) {
        val next = (progress.value - dyPx / travelPx).coerceIn(0f, 1f)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(next) }
    }

    /** Settles after a drag. Negative velocity = upward fling. */
    fun settle(velocityY: Float) {
        val target = when {
            velocityY < -900f -> 1f
            velocityY > 900f -> 0f
            else -> if (progress.value > 0.4f) 1f else 0f
        }
        animateTo(target, velocityY)
    }

    fun open(focusSearch: Boolean = false) {
        if (focusSearch) focusSearchRequest++
        animateTo(1f, 0f)
    }

    fun close() = animateTo(0f, 0f)

    fun snapClosed() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(0f) }
    }

    private fun animateTo(target: Float, velocityY: Float) {
        scope.launch {
            progress.animateTo(
                target,
                spring(dampingRatio = 0.86f, stiffness = 420f),
                initialVelocity = -velocityY / travelPx,
            )
        }
    }
}

package com.dotos.core.designsystem.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * One gesture detector for launcher items: tap, long-press, then drag-after-long-press.
 *
 * Why custom: combining `clickable` with a long-press detector can fire a tap on release after a
 * long-press, and can fight the pager. Here a tap only fires if the finger lifts before the
 * long-press timeout without moving past touch slop; any movement before the timeout cancels
 * without consuming, so the pager / vertical swipes take over naturally.
 *
 * @param onPress called with true on finger down and false when the gesture resolves (for press scale).
 * @param onLongPress local position of the long-press.
 * @param onDrag per-move delta after the long-press (events are consumed so nothing else scrolls).
 * @param onDragEnd finger lifted after a long-press (whether or not it moved).
 */
fun Modifier.launcherGestures(
    key: Any?,
    enabled: Boolean = true,
    onPress: (Boolean) -> Unit = {},
    onTap: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
): Modifier = if (!enabled) this else pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        onPress(true)
        val slop = viewConfiguration.touchSlop
        // 0 = released (tap), 1 = cancelled, null = long-press timeout reached.
        val outcome: Int? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var result = 1
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    result = 1
                    break
                }
                if (change.changedToUp()) {
                    result = if (change.isConsumed) 1 else 0
                    break
                }
                if (change.isConsumed || (change.position - down.position).getDistance() > slop) {
                    result = 1
                    break
                }
            }
            result
        }
        when (outcome) {
            0 -> {
                onPress(false)
                onTap()
            }
            1 -> onPress(false)
            else -> {
                onLongPress(down.position)
                var cancelled = false
                val completed = try {
                    drag(down.id) { change ->
                        val delta = change.positionChange()
                        change.consume()
                        if (delta != Offset.Zero) onDrag(delta)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    cancelled = true
                    onPress(false)
                    onDragCancel()
                    throw e
                }
                onPress(false)
                if (!cancelled) {
                    if (completed) onDragEnd() else onDragCancel()
                }
            }
        }
    }
}

package com.dotos.feature.widgets.dot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetFace
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetSize
import kotlinx.coroutines.delay

private const val MINUTE_MS = 60_000L
private const val MAX_TIMER_MS = 180L * MINUTE_MS
private const val DEFAULT_TIMER_MINUTES = 5

/**
 * Persisted as "running,startEpoch,accumMs,durationMs,finished" so a running timer survives scrolling,
 * process death and reboots of the launcher: elapsed time is always derived from wall-clock epochs.
 */
private data class TimerData(
    val running: Boolean,
    val startEpoch: Long,
    val accumMs: Long,
    val durationMs: Long,
    val finished: Boolean,
) {
    fun elapsedAt(now: Long): Long = (accumMs + (if (running) now - startEpoch else 0L)).coerceAtLeast(0L)

    fun encode(): String = "${if (running) 1 else 0},$startEpoch,$accumMs,$durationMs,${if (finished) 1 else 0}"

    companion object {
        fun idle(durationMs: Long): TimerData =
            TimerData(running = false, startEpoch = 0L, accumMs = 0L, durationMs = durationMs, finished = false)

        fun parse(raw: String, defaultDurationMs: Long): TimerData {
            val p = raw.split(',')
            if (p.size < 5) return idle(defaultDurationMs)
            val start = p[1].toLongOrNull()
            val accum = p[2].toLongOrNull()
            val duration = p[3].toLongOrNull()
            if (start == null || accum == null || duration == null || duration <= 0L) return idle(defaultDurationMs)
            return TimerData(p[0] == "1", start, accum, duration, p[4] == "1")
        }
    }
}

object TimerWidget : DotWidgetProvider {
    override val kind: String = "timer"
    override val title: String = "Timer"
    override val description: String = "Countdown timer or stopwatch with dot-matrix digits. Tap to start or pause."
    override val sizes: List<WidgetSize> = listOf(WidgetSize(2, 2))
    override val faces: List<WidgetFace> = listOf(
        WidgetFace("timer", "Timer", "Countdown with a +1 minute button"),
        WidgetFace("stopwatch", "Stopwatch", "Counts up from zero"),
    )

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val host = LocalWidgetHost.current
        val context = LocalContext.current
        val app = remember(context) { context.applicationContext }
        val haptics = rememberDotHaptics()
        val resumed = rememberIsResumed()
        val preview = instance.isPreview
        val stopwatch = instance.face == "stopwatch"

        val defaultMs = remember(instance.config) {
            (instance.config["minutes"]?.toIntOrNull()?.coerceIn(1, 180) ?: DEFAULT_TIMER_MINUTES) * MINUTE_MS
        }
        val key = "${instance.stateKey}:${if (stopwatch) "stopwatch" else "timer"}"
        val raw by rememberWidgetState(key)
        var data by remember(key) { mutableStateOf<TimerData?>(null) }
        // The persisted copy is read once; afterwards the local copy is the source of truth.
        LaunchedEffect(raw, key) {
            val persisted = raw
            if (data == null && persisted != null) data = TimerData.parse(persisted, defaultMs)
        }
        val current = data ?: TimerData.idle(defaultMs)
        var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

        fun commit(next: TimerData) {
            data = next
            nowTick = System.currentTimeMillis()
            host.save(key, next.encode())
        }

        // 1 s display ticker and finish detection: only while running and visible.
        LaunchedEffect(current.running, resumed, stopwatch, key) {
            if (!current.running || !resumed || preview) return@LaunchedEffect
            while (true) {
                val now = System.currentTimeMillis()
                nowTick = now
                val live = data ?: return@LaunchedEffect
                val elapsed = live.elapsedAt(now)
                if (!stopwatch && elapsed >= live.durationMs) {
                    val trueFinish = live.startEpoch + (live.durationMs - live.accumMs)
                    val fresh = now - trueFinish < 15_000L
                    val done = live.copy(running = false, accumMs = live.durationMs, finished = true)
                    data = done
                    host.save(key, done.encode())
                    if (fresh) {
                        vibrateDone(app)
                        haptics.confirm()
                    }
                    return@LaunchedEffect
                }
                delay(1000L - elapsed % 1000L + 8L)
            }
        }

        val finishedUi = !stopwatch && current.finished
        val elapsedMs = current.elapsedAt(if (current.running) nowTick else 0L)
        val seconds = when {
            finishedUi -> 0L
            stopwatch -> elapsedMs / 1000L
            else -> (current.durationMs - elapsedMs).coerceAtLeast(0L).let { (it + 999L) / 1000L }
        }
        val digits = formatClock(seconds)
        val template = if (seconds >= 3600L) "0:00:00" else "00:00"
        val blink = rememberPulse(active = finishedUi && resumed, periodMillis = 650, minAlpha = 0.05f)

        val a11y = when {
            finishedUi -> "Timer finished. Tap to dismiss"
            current.running -> "${if (stopwatch) "Stopwatch" else "Timer"} $digits, running. Tap to pause"
            else -> "${if (stopwatch) "Stopwatch" else "Timer"} $digits. Tap to start"
        }

        val onTap: () -> Unit = tap@{
            if (preview) return@tap
            val now = System.currentTimeMillis()
            val cur = data ?: TimerData.idle(defaultMs)
            if (finishedUi) {
                commit(cur.copy(running = false, startEpoch = 0L, accumMs = 0L, finished = false))
            } else if (cur.running) {
                commit(cur.copy(running = false, accumMs = cur.elapsedAt(now)))
            } else {
                val base = if (!stopwatch && cur.accumMs >= cur.durationMs) 0L else cur.accumMs
                commit(cur.copy(running = true, startEpoch = now, accumMs = base, finished = false))
            }
            haptics.confirm()
        }
        val onReset: () -> Unit = reset@{
            if (preview) return@reset
            val cur = data ?: TimerData.idle(defaultMs)
            if (cur.running || cur.accumMs > 0L || cur.finished) {
                commit(cur.copy(running = false, startEpoch = 0L, accumMs = 0L, finished = false))
            } else if (!stopwatch) {
                // Second reset on an idle timer goes back to the configured length.
                commit(cur.copy(durationMs = defaultMs))
            }
            haptics.tick()
        }
        val onPlusMinute: () -> Unit = plus@{
            if (preview) return@plus
            val cur = data ?: TimerData.idle(defaultMs)
            commit(cur.copy(durationMs = (cur.durationMs + MINUTE_MS).coerceAtMost(MAX_TIMER_MS), finished = false))
            haptics.tick()
        }

        Column(
            modifier
                .semantics(mergeDescendants = false) { contentDescription = a11y }
                .pressClickable(enabled = !preview, pressedScale = 0.97f, onClick = onTap)
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (finishedUi) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .graphicsLayer { alpha = blink.value }
                            .background(style.accent, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                FitText(
                    text = if (finishedUi) "DONE" else if (stopwatch) "STOPWATCH" else "TIMER",
                    color = if (finishedUi) style.content else style.muted,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    role = TextRole.LABEL,
                    maxSize = 11f,
                    alignment = Alignment.CenterStart,
                )
                if (!finishedUi) {
                    DotGlyph(
                        rows = if (current.running) Glyphs.PAUSE else Glyphs.PLAY,
                        color = if (current.running) style.content else style.muted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            FitText(
                text = digits,
                color = style.content,
                modifier = Modifier.fillMaxWidth().weight(1f),
                maxSize = 56f,
                sizeTemplate = template,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillButton(label = "RESET", onClick = onReset)
                if (!stopwatch) PillButton(label = "+1M", onClick = onPlusMinute)
            }
        }
    }
}

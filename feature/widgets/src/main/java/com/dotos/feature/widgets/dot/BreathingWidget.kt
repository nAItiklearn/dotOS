package com.dotos.feature.widgets.dot

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.LocalReduceMotion
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetFace
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetSize
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val MIN_SCALE = 0.3f
private const val IDLE_SCALE = 0.62f
private const val STATIC_SCALE = 0.7f
private const val RINGS = 5

private class BreathPhase(val label: String, val seconds: Int, val from: Float, val to: Float)

private class BreathPattern(val id: String, val name: String, val phases: List<BreathPhase>) {
    private val totalMs: Long = phases.sumOf { it.seconds } * 1000L

    fun indexAt(ms: Long): Int {
        var t = ms.coerceAtLeast(0L) % totalMs
        for (i in phases.indices) {
            val d = phases[i].seconds * 1000L
            if (t < d) return i
            t -= d
        }
        return phases.lastIndex
    }

    /** Dot-circle scale 0..1 at [ms] into the session: eased inhale/exhale, steady holds. */
    fun scaleAt(ms: Long): Float {
        var t = ms.coerceAtLeast(0L) % totalMs
        for (p in phases) {
            val d = p.seconds * 1000L
            if (t < d) {
                val eased = 0.5f - 0.5f * cos(PI.toFloat() * (t.toFloat() / d))
                return p.from + (p.to - p.from) * eased
            }
            t -= d
        }
        return phases.last().to
    }

    fun secondsLeftAt(ms: Long): Int {
        var t = ms.coerceAtLeast(0L) % totalMs
        for (p in phases) {
            val d = p.seconds * 1000L
            if (t < d) return ceil((d - t) / 1000f).toInt().coerceAtLeast(1)
            t -= d
        }
        return 1
    }
}

private val BreathPatterns = listOf(
    BreathPattern(
        "478",
        "4-7-8",
        listOf(
            BreathPhase("Inhale", 4, MIN_SCALE, 1f),
            BreathPhase("Hold", 7, 1f, 1f),
            BreathPhase("Exhale", 8, 1f, MIN_SCALE),
        ),
    ),
    BreathPattern(
        "box",
        "Box",
        listOf(
            BreathPhase("Inhale", 4, MIN_SCALE, 1f),
            BreathPhase("Hold", 4, 1f, 1f),
            BreathPhase("Exhale", 4, 1f, MIN_SCALE),
            BreathPhase("Hold", 4, MIN_SCALE, MIN_SCALE),
        ),
    ),
    BreathPattern(
        "calm",
        "Calm",
        listOf(
            BreathPhase("Inhale", 4, MIN_SCALE, 1f),
            BreathPhase("Exhale", 6, 1f, MIN_SCALE),
        ),
    ),
)

/** Unit-circle dot lattice: a centre dot plus rings of 6, 12, 18... dots. Precomputed once. */
private class BreathDisc {
    val count: Int = 1 + 3 * RINGS * (RINGS + 1)
    val x = FloatArray(count)
    val y = FloatArray(count)
    val ring = IntArray(count)

    init {
        var i = 1
        for (k in 1..RINGS) {
            val n = 6 * k
            val offset = if (k % 2 == 0) PI.toFloat() / n else 0f
            for (j in 0 until n) {
                val a = (2.0 * PI * j / n).toFloat() + offset
                x[i] = cos(a) * k / RINGS
                y[i] = sin(a) * k / RINGS
                ring[i] = k
                i++
            }
        }
    }
}

private val Disc = BreathDisc()

object BreathingWidget : DotWidgetProvider {
    override val kind: String = "breathing"
    override val title: String = "Breathing Break"
    override val description: String = "A guided breathing circle: inhale, hold, exhale. Tap to start or stop."
    override val sizes: List<WidgetSize> = listOf(WidgetSize(2, 2), WidgetSize(4, 2))
    override val faces: List<WidgetFace> = listOf(
        WidgetFace("478", "4-7-8", "Inhale 4, hold 7, exhale 8"),
        WidgetFace("box", "Box", "Inhale, hold, exhale, hold: 4 seconds each"),
        WidgetFace("calm", "Calm", "Inhale 4, exhale 6"),
    )

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val pattern = remember(instance.face) { BreathPatterns.firstOrNull { it.id == instance.face } ?: BreathPatterns[0] }
        val reduce = LocalReduceMotion.current
        val resumed = rememberIsResumed()
        val haptics = rememberDotHaptics()
        val preview = instance.isPreview

        var running by remember { mutableStateOf(false) }
        var elapsed by remember { mutableLongStateOf(0L) }

        // A face change or leaving the foreground ends the session.
        LaunchedEffect(pattern, resumed) {
            running = false
            elapsed = 0L
        }

        // The only animation loop: runs while a session is active and the launcher is visible.
        LaunchedEffect(running, resumed, reduce, pattern) {
            if (!running || !resumed || preview) return@LaunchedEffect
            if (reduce) {
                val start = SystemClock.uptimeMillis() - elapsed
                while (true) {
                    elapsed = SystemClock.uptimeMillis() - start
                    delay(200L)
                }
            } else {
                val startNs = withFrameNanos { it } - elapsed * 1_000_000L
                while (true) {
                    withFrameNanos { now -> elapsed = (now - startNs) / 1_000_000L }
                }
            }
        }

        // Recomposition only when the phase or the whole-second countdown changes, never per frame.
        val phaseIndex by remember(pattern) { derivedStateOf { pattern.indexAt(elapsed) } }
        val secondsLeft by remember(pattern) { derivedStateOf { pattern.secondsLeftAt(elapsed) } }

        LaunchedEffect(phaseIndex, running) {
            if (running) haptics.tick()
        }

        val phaseLabel = if (running) pattern.phases[phaseIndex].label else "Breathe"
        val subLabel = if (running) "$secondsLeft SEC" else "TAP TO START"
        val a11y = if (running) {
            "Breathing exercise ${pattern.name}. $phaseLabel. Tap to stop"
        } else {
            "Breathing exercise ${pattern.name}. Tap to start"
        }

        var root = modifier.semantics(mergeDescendants = true) { contentDescription = a11y }
        if (!preview) {
            root = root.pressClickable(pressedScale = 0.97f) {
                running = !running
                if (!running) elapsed = 0L
                haptics.toggle()
            }
        }

        val circle: @Composable (Modifier) -> Unit = { m ->
            Box(m, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val scale = when {
                        !running -> IDLE_SCALE
                        reduce -> STATIC_SCALE
                        else -> pattern.scaleAt(elapsed)
                    }
                    drawBreathDisc(scale, style.content, style.accent, running)
                }
                if (running && reduce) {
                    FitText(
                        text = secondsLeft.toString(),
                        color = style.content,
                        modifier = Modifier.fillMaxSize(0.32f),
                        maxSize = 40f,
                        sizeTemplate = "0",
                    )
                }
            }
        }

        if (instance.spanX >= 4) {
            Row(
                root.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                circle(Modifier.fillMaxHeight().aspectRatio(1f))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    FitText(
                        text = "BREATHE  ${pattern.name.uppercase()}",
                        color = style.muted,
                        modifier = Modifier.fillMaxWidth().height(16.dp),
                        role = TextRole.LABEL,
                        maxSize = 11f,
                        alignment = Alignment.CenterStart,
                    )
                    FitText(
                        text = phaseLabel,
                        color = style.content,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        maxSize = 40f,
                        sizeTemplate = "Exhale",
                        alignment = Alignment.CenterStart,
                    )
                    FitText(
                        text = subLabel,
                        color = style.muted,
                        modifier = Modifier.fillMaxWidth().height(16.dp),
                        role = TextRole.LABEL,
                        maxSize = 11f,
                        alignment = Alignment.CenterStart,
                    )
                }
            }
        } else {
            Column(root.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FitText(
                        text = "BREATHE",
                        color = style.muted,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        role = TextRole.LABEL,
                        maxSize = 11f,
                        alignment = Alignment.CenterStart,
                    )
                    FitText(
                        text = pattern.name.uppercase(),
                        color = style.content,
                        modifier = Modifier.fillMaxHeight(),
                        role = TextRole.LABEL,
                        maxSize = 11f,
                        alignment = Alignment.CenterEnd,
                    )
                }
                circle(Modifier.weight(1f).fillMaxWidth())
                FitText(
                    text = phaseLabel,
                    color = style.content,
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    maxSize = 26f,
                    sizeTemplate = "Exhale",
                )
                Spacer(Modifier.height(2.dp))
                FitText(
                    text = subLabel,
                    color = style.muted,
                    modifier = Modifier.fillMaxWidth().height(14.dp),
                    role = TextRole.LABEL,
                    maxSize = 10f,
                )
            }
        }
    }
}

private fun DrawScope.drawBreathDisc(scale: Float, color: Color, accent: Color, running: Boolean) {
    val radius = min(size.width, size.height) / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val reach = radius * 0.94f * scale
    val dotRadius = max(reach / RINGS * 0.36f, 1.2.dp.toPx())
    for (i in 0 until Disc.count) {
        val dotColor = if (i == 0 && running) accent else color.copy(alpha = 1f - 0.5f * Disc.ring[i] / RINGS)
        drawCircle(dotColor, dotRadius, Offset(cx + Disc.x[i] * reach, cy + Disc.y[i] * reach))
    }
}

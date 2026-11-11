package com.dotos.feature.widgets.dot

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetShape
import com.dotos.feature.widgets.api.WidgetSize
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Process-wide latest reading of the cumulative step counter (steps since boot). */
internal object StepSource {
    val counter = MutableStateFlow<Long?>(null)
}

private data class StepBaseline(val date: String, val value: Long) {
    fun encode(): String = "$date|$value"
}

private fun parseStepBaseline(raw: String?): StepBaseline? {
    if (raw.isNullOrEmpty()) return null
    val parts = raw.split('|')
    if (parts.size != 2) return null
    val value = parts[1].toLongOrNull() ?: return null
    return StepBaseline(parts[0], value)
}

private enum class StepUi { READY, NEEDS_PERMISSION, NO_SENSOR }

private const val STEP_PERMISSION = Manifest.permission.ACTIVITY_RECOGNITION
private const val DEFAULT_GOAL = 8000
private const val PREVIEW_STEPS = 4170
private const val RING_DOTS = 20

object PedometerWidget : DotWidgetProvider {
    override val kind: String = "pedometer"
    override val title: String = "Pedometer"
    override val description: String = "Steps today from your phone's step counter. Stays on device."
    override val sizes: List<WidgetSize> = listOf(WidgetSize(2, 1), WidgetSize(2, 2))
    override val permissions: List<String> = listOf(STEP_PERMISSION)

    override fun shape(size: WidgetSize): WidgetShape = if (size.spanY <= 1) WidgetShape.PILL else WidgetShape.ROUNDED

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val host = LocalWidgetHost.current
        val context = LocalContext.current
        val app = remember(context) { context.applicationContext }
        val preview = instance.isPreview
        val resumed = rememberIsResumed()
        val goal = remember(instance.config) {
            instance.config["goal"]?.toIntOrNull()?.coerceIn(500, 100_000) ?: DEFAULT_GOAL
        }

        val sensorManager = remember(app) { app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
        val sensor = remember(sensorManager) { sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
        var granted by remember { mutableStateOf(preview || host.hasPermission(STEP_PERMISSION)) }
        LaunchedEffect(resumed, preview) {
            if (resumed && !preview) granted = granted || host.hasPermission(STEP_PERMISSION)
        }

        // Listen only while composed, foregrounded, permitted. The latest value is cached process-wide.
        DisposableEffect(sensorManager, sensor, resumed, granted, preview) {
            if (preview || !resumed || !granted || sensorManager == null || sensor == null) {
                onDispose { }
            } else {
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        if (event.values.isNotEmpty()) StepSource.counter.value = event.values[0].toLong()
                    }

                    override fun onAccuracyChanged(changed: Sensor?, accuracy: Int) = Unit
                }
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                onDispose { sensorManager.unregisterListener(listener) }
            }
        }

        // Daily baseline: the counter value at the first reading of the day, persisted through the host.
        val baseKey = "${instance.stateKey}:steps_base"
        val raw by rememberWidgetState(baseKey)
        val counter by StepSource.counter.collectAsState()
        var localBase by remember { mutableStateOf<StepBaseline?>(null) }
        val today = LocalDate.now().toString()

        LaunchedEffect(raw, counter, today, preview) {
            if (preview) return@LaunchedEffect
            val persisted = raw ?: return@LaunchedEffect
            val current = counter ?: return@LaunchedEffect
            val base = localBase ?: parseStepBaseline(persisted)
            // New day, first run, or the counter restarted after a reboot (counter < baseline).
            if (base == null || base.date != today || current < base.value) {
                val fresh = StepBaseline(today, current)
                localBase = fresh
                host.save(baseKey, fresh.encode())
            }
        }

        val steps: Int? = if (preview) {
            PREVIEW_STEPS
        } else {
            val persisted = raw
            val current = counter
            if (persisted == null || current == null) {
                null
            } else {
                val base = localBase ?: parseStepBaseline(persisted)
                if (base == null || base.date != today || current < base.value) 0 else (current - base.value).toInt()
            }
        }

        val ui = when {
            preview -> StepUi.READY
            sensor == null -> StepUi.NO_SENSOR
            !granted -> StepUi.NEEDS_PERMISSION
            else -> StepUi.READY
        }
        val shown = steps ?: 0
        val percent = (shown * 100L / goal).toInt().coerceAtMost(999)
        val a11y = when (ui) {
            StepUi.READY -> "Pedometer, ${groupThousands(shown)} steps today, $percent percent of goal"
            StepUi.NEEDS_PERMISSION -> "Pedometer. Tap to enable step counting"
            StepUi.NO_SENSOR -> "Pedometer. No step sensor on this device"
        }

        var root = modifier.semantics(mergeDescendants = true) { contentDescription = a11y }
        if (ui == StepUi.NEEDS_PERMISSION) {
            root = root.pressClickable(pressedScale = 0.97f) {
                host.requestPermission(STEP_PERMISSION) { ok -> runOnMain { granted = ok } }
            }
        }

        if (instance.spanY <= 1) {
            PedometerPill(root, ui, shown, percent)
        } else {
            PedometerTile(root, ui, shown, percent, goal)
        }
    }
}

@Composable
private fun PedometerPill(modifier: Modifier, ui: StepUi, steps: Int, percent: Int) {
    val style = LocalWidgetStyle.current
    BoxWithConstraints(modifier) {
        val padH = (maxHeight * 0.2f).coerceIn(12.dp, 22.dp)
        val labelH = (maxHeight * 0.2f).coerceIn(12.dp, 18.dp)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = padH, vertical = padH * 0.6f),
            verticalArrangement = Arrangement.Center,
        ) {
            when (ui) {
                StepUi.READY -> {
                    FitText(
                        text = groupThousands(steps),
                        color = style.content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        maxSize = 46f,
                        sizeTemplate = "0,000",
                        alignment = Alignment.CenterStart,
                    )
                    Row(Modifier.fillMaxWidth().height(labelH), verticalAlignment = Alignment.CenterVertically) {
                        FitText(
                            text = "TOTAL TODAY",
                            color = style.muted,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            role = TextRole.LABEL,
                            maxSize = 11f,
                            alignment = Alignment.CenterStart,
                        )
                        FitText(
                            text = "$percent%",
                            color = style.content,
                            modifier = Modifier.fillMaxHeight(),
                            role = TextRole.LABEL,
                            maxSize = 11f,
                            sizeTemplate = "000%",
                            alignment = Alignment.CenterEnd,
                        )
                    }
                }
                StepUi.NEEDS_PERMISSION -> MessageLines("Tap to enable", "STEP COUNTER", Modifier.fillMaxWidth().weight(1f), labelH)
                StepUi.NO_SENSOR -> MessageLines("No step sensor", "PEDOMETER", Modifier.fillMaxWidth().weight(1f), labelH)
            }
        }
    }
}

@Composable
private fun MessageLines(message: String, label: String, modifier: Modifier, labelHeight: androidx.compose.ui.unit.Dp) {
    val style = LocalWidgetStyle.current
    Column(modifier, verticalArrangement = Arrangement.Center) {
        FitText(
            text = message,
            color = style.content,
            modifier = Modifier.fillMaxWidth().weight(1f),
            role = TextRole.TITLE,
            maxSize = 22f,
            alignment = Alignment.CenterStart,
        )
        FitText(
            text = label,
            color = style.muted,
            modifier = Modifier.fillMaxWidth().height(labelHeight),
            role = TextRole.LABEL,
            maxSize = 11f,
            alignment = Alignment.CenterStart,
        )
    }
}

@Composable
private fun PedometerTile(modifier: Modifier, ui: StepUi, steps: Int, percent: Int, goal: Int) {
    val style = LocalWidgetStyle.current
    Column(modifier.padding(14.dp)) {
        Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FitText(
                text = "TOTAL TODAY",
                color = style.muted,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                role = TextRole.LABEL,
                maxSize = 11f,
                alignment = Alignment.CenterStart,
            )
            if (ui == StepUi.READY) {
                FitText(
                    text = "$percent%",
                    color = style.content,
                    modifier = Modifier.fillMaxHeight(),
                    role = TextRole.LABEL,
                    maxSize = 11f,
                    sizeTemplate = "000%",
                    alignment = Alignment.CenterEnd,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val d = minOf(maxWidth, maxHeight)
            Box(Modifier.size(d), contentAlignment = Alignment.Center) {
                val filled = if (ui == StepUi.READY) (steps.toLong() * RING_DOTS / goal).toInt().coerceIn(0, RING_DOTS) else 0
                Canvas(Modifier.fillMaxSize()) {
                    drawGoalRing(filled, RING_DOTS, style.content, style.track, style.accent)
                }
                val inner = Modifier.size(width = d * 0.62f, height = d * 0.42f)
                when (ui) {
                    StepUi.READY -> Column(
                        inner,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        FitText(
                            text = groupThousands(steps),
                            color = style.content,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            maxSize = 40f,
                            sizeTemplate = "0,000",
                        )
                        FitText(
                            text = "GOAL ${groupThousands(goal)}",
                            color = style.muted,
                            modifier = Modifier.fillMaxWidth().height(14.dp),
                            role = TextRole.LABEL,
                            maxSize = 10f,
                        )
                    }
                    StepUi.NEEDS_PERMISSION -> MessageLines("Tap to enable", "STEP COUNTER", inner, 14.dp)
                    StepUi.NO_SENSOR -> MessageLines("No step sensor", "PEDOMETER", inner, 14.dp)
                }
            }
        }
    }
}

/** [total] dots on a circle starting at 12 o'clock; the next dot to fill is the accent. */
private fun DrawScope.drawGoalRing(filled: Int, total: Int, on: Color, off: Color, head: Color) {
    val d = min(size.width, size.height)
    val dotRadius = d * 0.045f
    val ringRadius = d / 2f - dotRadius * 1.6f
    val cx = size.width / 2f
    val cy = size.height / 2f
    for (i in 0 until total) {
        val angle = (-PI / 2.0 + 2.0 * PI * i / total).toFloat()
        val color = when {
            i < filled -> on
            i == filled -> head
            else -> off
        }
        drawCircle(color, dotRadius, Offset(cx + cos(angle) * ringRadius, cy + sin(angle) * ringRadius))
    }
}

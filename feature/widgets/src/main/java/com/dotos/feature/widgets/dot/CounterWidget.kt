package com.dotos.feature.widgets.dot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetSize
import com.dotos.feature.widgets.api.drawDotGrid
import java.time.LocalDate
import kotlin.math.min

private const val COUNTER_DEFAULT_GOAL = 8
private const val COUNTER_MAX = 99
private const val COUNTER_PREVIEW = 5

private class DayCount(val date: String, val n: Int) {
    fun encode(): String = "$date|$n"
}

private fun parseDayCount(raw: String?): DayCount? {
    if (raw.isNullOrEmpty()) return null
    val parts = raw.split('|')
    if (parts.size != 2) return null
    val n = parts[1].toIntOrNull() ?: return null
    return DayCount(parts[0], n.coerceIn(0, COUNTER_MAX))
}

/** Column count that makes the dots as large as possible inside a [w] x [h] area. */
private fun bestColumns(goal: Int, w: Float, h: Float): Int {
    var best = 1
    var bestPitch = 0f
    for (cols in 1..goal) {
        val rows = (goal + cols - 1) / cols
        val pitch = min(w / cols, h / rows)
        if (pitch > bestPitch + 0.01f) {
            bestPitch = pitch
            best = cols
        }
    }
    return best
}

object CounterWidget : DotWidgetProvider {
    override val kind: String = "counter"
    override val title: String = "Counter"
    override val description: String = "Habit dots: tap to count something today. Resets every day."
    override val sizes: List<WidgetSize> = listOf(WidgetSize(2, 2))

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val host = LocalWidgetHost.current
        val haptics = rememberDotHaptics()
        val preview = instance.isPreview

        val label = remember(instance.config) { (instance.config["label"] ?: "Water").trim().take(18).ifEmpty { "Count" } }
        val goal = remember(instance.config) { instance.config["goal"]?.toIntOrNull()?.coerceIn(1, 24) ?: COUNTER_DEFAULT_GOAL }

        val key = "${instance.stateKey}:count"
        val raw by rememberWidgetState(key)
        var local by remember(key) { mutableStateOf<DayCount?>(null) }
        val today = LocalDate.now().toString()
        val stored = local ?: parseDayCount(raw)
        val count = when {
            preview -> COUNTER_PREVIEW
            stored != null && stored.date == today -> stored.n
            else -> 0
        }

        fun change(delta: Int) {
            if (preview) return
            val next = (count + delta).coerceIn(0, COUNTER_MAX)
            if (next == count) {
                if (delta < 0) haptics.reject()
                return
            }
            val record = DayCount(today, next)
            local = record
            host.save(key, record.encode())
            if (delta > 0 && next == goal) haptics.confirm() else haptics.tick()
        }

        val onFilled = style.background.copy(alpha = 1f)
        val a11y = "$label counter. $count of $goal today. Tap to add one"

        Column(
            modifier
                .semantics { contentDescription = a11y }
                .pressClickable(enabled = !preview, pressedScale = 0.97f) { change(1) }
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                FitText(
                    text = label.uppercase(),
                    color = style.muted,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    role = TextRole.LABEL,
                    maxSize = 11f,
                    alignment = Alignment.CenterStart,
                )
                FitText(
                    text = "$count/$goal",
                    color = style.content,
                    modifier = Modifier.fillMaxHeight(),
                    maxSize = 22f,
                    sizeTemplate = "00/00",
                    alignment = Alignment.CenterEnd,
                )
            }
            Spacer(Modifier.height(6.dp))
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val columns = remember(goal, constraints.maxWidth, constraints.maxHeight) {
                    bestColumns(goal, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                }
                Canvas(Modifier.fillMaxSize()) {
                    // The next dot to fill carries the single accent.
                    drawDotGrid(
                        total = goal,
                        filled = min(count, goal),
                        columns = columns,
                        on = style.content,
                        off = style.track,
                        highlight = style.accent,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(onClick = { change(-1) }, enabled = !preview && count > 0, label = "Remove one") {
                    DotGlyph(Glyphs.MINUS, style.content, Modifier.size(16.dp))
                }
                RoundButton(onClick = { change(1) }, enabled = !preview, filled = true, label = "Add one") {
                    DotGlyph(Glyphs.PLUS, onFilled, Modifier.size(16.dp))
                }
            }
        }
    }
}

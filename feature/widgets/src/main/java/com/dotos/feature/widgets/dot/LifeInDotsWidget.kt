package com.dotos.feature.widgets.dot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetFace
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetSize
import com.dotos.feature.widgets.api.drawDotGrid
import com.dotos.feature.widgets.api.rememberTicker
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.min

/**
 * "Life in dots": how much of the year, month, week or day has passed, as a grid of dots. Filled dots
 * are time gone by, the current dot is red, the rest are dim. Updates once a minute.
 */
object LifeInDotsWidget : DotWidgetProvider {
    const val FACE_YEAR = "year"
    const val FACE_MONTH = "month"
    const val FACE_DAY = "day"
    const val FACE_WEEK = "week"

    override val kind = "life_dots"
    override val title = "Life in Dots"
    override val description = "The year, month, week or day as a grid of dots."
    override val sizes = listOf(WidgetSize(4, 2), WidgetSize(4, 4))
    override val faces = listOf(
        WidgetFace(FACE_YEAR, "Year", "One dot for every day of the year"),
        WidgetFace(FACE_MONTH, "Month", "One dot for every day of the month"),
        WidgetFace(FACE_DAY, "Day", "One dot for every hour of today"),
        WidgetFace(FACE_WEEK, "Week", "One dot for every day of the week"),
    )

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val now = rememberTicker(60_000L)
        val faceId = faces.firstOrNull { it.id == instance.face }?.id ?: FACE_YEAR
        val tall = instance.spanY >= 4
        val life = remember(now, faceId) { lifeData(faceId, now) }
        val measurer = rememberTextMeasurer()

        Column(
            modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) { contentDescription = "${life.label}, ${life.percent} percent" }
                .padding(16.dp),
        ) {
            // Header: label on the left, percentage on the right.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val px = 12.dp.toPx()
                        val percentT = baLabel(measurer, "${life.percent}%", px, w * 0.3f)
                        val labelT = baLabel(measurer, life.label, px, w - percentT.width - 12.dp.toPx())
                        onDrawBehind {
                            baDrawText(labelT, style.content, 0f, h / 2f, hAlign = 0f)
                            baDrawText(percentT, style.muted, w, h / 2f, hAlign = 1f)
                        }
                    },
            )
            Spacer(Modifier.height(10.dp))
            // Dots.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        if (faceId == FACE_WEEK) {
                            val pitch = w / 7f
                            val r = min(pitch * 0.32f, 22.dp.toPx())
                            val letterPx = min(11.dp.toPx(), pitch * 0.3f)
                            val letters = Array(7) { baLabel(measurer, life.weekLetters[it], letterPx) }
                            val gap = 10.dp.toPx()
                            val groupH = 2f * r + gap + letterPx * 0.7f
                            val dotCy = (h - groupH) / 2f + r
                            val letterCy = dotCy + r + gap + letterPx * 0.35f
                            return@drawWithCache onDrawBehind {
                                for (i in 0 until 7) {
                                    val color = when {
                                        i < life.filled -> style.content
                                        i == life.filled -> style.accent
                                        else -> style.track
                                    }
                                    val cx = pitch * (i + 0.5f)
                                    drawCircle(color, r, Offset(cx, dotCy))
                                    baDrawText(letters[i], if (i == life.filled) style.content else style.muted, cx, letterCy)
                                }
                            }
                        }
                        val columns = bestGridColumns(life.total, w, h)
                        onDrawBehind {
                            drawDotGrid(
                                total = life.total,
                                filled = life.filled,
                                columns = columns,
                                on = style.content,
                                off = style.track,
                                highlight = style.accent,
                            )
                        }
                    },
            )
            if (tall) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .drawWithCache {
                            val h = size.height
                            val footerT = baLabel(measurer, life.remaining, 11.dp.toPx(), size.width)
                            onDrawBehind { baDrawText(footerT, style.muted, 0f, h / 2f, hAlign = 0f) }
                        },
                )
            }
        }
    }
}

@Immutable
private class LifeData(
    val label: String,
    /** Number of dots. */
    val total: Int,
    /** Dots already gone by; the dot at this index is "now". */
    val filled: Int,
    val percent: Int,
    val remaining: String,
    val weekLetters: List<String>,
)

private fun lifeData(face: String, nowMs: Long): LifeData {
    val dt = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
    val locale = Locale.getDefault()
    val secondOfDay = dt.toLocalTime().toSecondOfDay()
    val dayFraction = secondOfDay / 86_400f
    val firstDow = WeekFields.of(locale).firstDayOfWeek
    val letters = List(7) { firstDow.plus(it.toLong()).getDisplayName(JTextStyle.NARROW, locale).uppercase() }

    fun percentOf(fraction: Float): Int = (fraction * 100f).toInt().coerceIn(0, 100)

    return when (face) {
        LifeInDotsWidget.FACE_MONTH -> {
            val total = dt.toLocalDate().lengthOfMonth()
            val passed = dt.dayOfMonth - 1
            LifeData(
                label = dt.month.getDisplayName(JTextStyle.FULL, locale),
                total = total,
                filled = passed,
                percent = percentOf((passed + dayFraction) / total),
                remaining = "${total - dt.dayOfMonth} days left",
                weekLetters = letters,
            )
        }
        LifeInDotsWidget.FACE_DAY -> {
            val minutesLeft = 1440 - (dt.hour * 60 + dt.minute)
            LifeData(
                label = BaStrings.TODAY,
                total = 24,
                filled = dt.hour,
                percent = percentOf(dayFraction),
                remaining = "${minutesLeft / 60}h ${minutesLeft % 60}m left",
                weekLetters = letters,
            )
        }
        LifeInDotsWidget.FACE_WEEK -> {
            val index = (dt.dayOfWeek.value - firstDow.value + 7) % 7
            LifeData(
                label = "Week ${dt.get(WeekFields.ISO.weekOfWeekBasedYear())}",
                total = 7,
                filled = index,
                percent = percentOf((index + dayFraction) / 7f),
                remaining = "${6 - index} days left",
                weekLetters = letters,
            )
        }
        else -> {
            val total = dt.toLocalDate().lengthOfYear()
            val passed = dt.dayOfYear - 1
            LifeData(
                label = dt.year.toString(),
                total = total,
                filled = passed,
                percent = percentOf((passed + dayFraction) / total),
                remaining = "${total - dt.dayOfYear} days left",
                weekLetters = letters,
            )
        }
    }
}

/** Column count that gives the largest dot pitch for [total] dots in a [w] x [h] area. */
private fun bestGridColumns(total: Int, w: Float, h: Float): Int {
    var best = 1
    var bestPitch = 0f
    for (columns in 1..total.coerceAtLeast(1)) {
        val rows = (total + columns - 1) / columns
        val pitch = min(w / columns, h / rows)
        if (pitch > bestPitch + 0.001f) {
            bestPitch = pitch
            best = columns
        }
    }
    return best
}

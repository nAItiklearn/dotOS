package com.dotos.feature.widgets.dot

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotFonts
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetSize
import com.dotos.feature.widgets.api.WidgetStyle
import com.dotos.feature.widgets.api.rememberTicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Date tile (2x2) and dot month calendar with the next event (4x2). Calendar access is optional:
 * without READ_CALENDAR the 4x2 shows the grid and a "Tap to show events" hint that asks for it.
 */
object DateWidget : DotWidgetProvider {
    override val kind = "date"
    override val title = "Date & Calendar"
    override val description = "Today at a glance with a dot month calendar and your next event."
    override val sizes = listOf(WidgetSize(2, 2), WidgetSize(4, 2))

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val context = LocalContext.current
        val host = LocalWidgetHost.current
        val now = rememberTicker(60_000L)
        val preview = instance.isPreview
        val wide = instance.spanX >= 4
        val today = remember(now) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate() }

        var granted by remember { mutableStateOf(!preview && host.hasPermission(Manifest.permission.READ_CALENDAR)) }
        var events by remember { mutableStateOf<List<DateWidgetEvent>>(emptyList()) }
        val refreshBucket = now / REFRESH_MILLIS
        LaunchedEffect(wide, granted, preview, refreshBucket) {
            events = if (wide && granted && !preview) queryEvents(context, System.currentTimeMillis()) else emptyList()
        }

        val zone = remember(now) { ZoneId.systemDefault() }
        val next = if (preview) PREVIEW_EVENT else events.firstOrNull { it.isUpcoming(now, today, zone) }
        val whenText = next?.let { eventWhen(context, it, now, today, zone) }

        val dateDescription = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        val description = if (next != null && whenText != null) "$dateDescription. Next event $whenText ${next.title}" else dateDescription
        val root = modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .baTap(enabled = !preview) {
                if (wide && !granted) {
                    host.requestPermission(Manifest.permission.READ_CALENDAR) { ok -> granted = ok }
                } else {
                    host.baLaunch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                }
            }

        if (wide) {
            val mode = when {
                preview || next != null -> EventMode.EVENT
                granted -> EventMode.EMPTY
                else -> EventMode.HINT
            }
            DateCalendar(today, style, mode, whenText, next?.title, root)
        } else {
            DateTile(today, style, root)
        }
    }

    private const val REFRESH_MILLIS = 15L * 60_000L
    private val PREVIEW_EVENT = DateWidgetEvent(
        title = "Design review",
        begin = Long.MAX_VALUE / 4,
        end = Long.MAX_VALUE / 4 + 1,
        allDay = false,
    )
}

private enum class EventMode { EVENT, EMPTY, HINT }

private data class DateWidgetEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean) {
    /** All-day instances are stored as UTC midnights, so they are compared by calendar day instead. */
    fun isUpcoming(nowMs: Long, today: LocalDate, zone: ZoneId): Boolean {
        if (!allDay) return end > nowMs
        val lastDay = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
        return !lastDay.isBefore(today)
    }
}

/** "NOW", "ALL DAY", "09:30" or "THU 09:30" for the event line. */
private fun eventWhen(context: Context, e: DateWidgetEvent, nowMs: Long, today: LocalDate, zone: ZoneId): String {
    if (e.begin == Long.MAX_VALUE / 4) return "14:30" // picker preview sample
    val startDay = if (e.allDay) {
        Instant.ofEpochMilli(e.begin).atZone(ZoneOffset.UTC).toLocalDate()
    } else {
        Instant.ofEpochMilli(e.begin).atZone(zone).toLocalDate()
    }
    val isToday = !startDay.isAfter(today)
    val prefix = if (isToday) "" else startDay.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()) + " "
    return when {
        e.allDay -> prefix + "ALL DAY"
        e.begin <= nowMs -> "NOW"
        else -> prefix + DateFormat.getTimeFormat(context).format(Date(e.begin))
    }.uppercase()
}

/** Instances for the next 7 days, ordered by start. Runs on the IO dispatcher; returns empty on any failure. */
@SuppressLint("MissingPermission") // only called after the host reported READ_CALENDAR as granted
private suspend fun queryEvents(context: Context, nowMs: Long): List<DateWidgetEvent> = withContext(Dispatchers.IO) {
    runCatching {
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, nowMs)
        ContentUris.appendId(builder, nowMs + 7L * 24L * 3_600_000L)
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val out = ArrayList<DateWidgetEvent>()
        context.contentResolver.query(
            builder.build(),
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && out.size < 12) {
                out += DateWidgetEvent(
                    title = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "(No title)",
                    begin = cursor.getLong(1),
                    end = cursor.getLong(2),
                    allDay = cursor.getInt(3) != 0,
                )
            }
        }
        out.toList()
    }.getOrDefault(emptyList())
}

// ---------------------------------------------------------------------------------------------
// 2x2 tile
// ---------------------------------------------------------------------------------------------

@Composable
private fun DateTile(today: LocalDate, style: WidgetStyle, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val weekday = today.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault())
    val day = today.dayOfMonth.toString()
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val u = min(w / BA_REF_2X2_W_DP.dp.toPx(), h / BA_REF_2X2_H_DP.dp.toPx())
            val pad = 16.dp.toPx() * u
            val dotR = 4.5f.dp.toPx() * u
            val weekdayT = baText(measurer, weekday, 15.dp.toPx() * u, DotFonts.Grotesk, FontWeight.Medium, w * 0.5f)
            val topZone = pad + 22.dp.toPx() * u
            val numberPx = min((w - 2f * pad) / 1.3f, (h - topZone - pad) / 0.7f * 0.92f)
            val numberT = baText(
                measurer, day, numberPx, DotFonts.Grotesk, FontWeight.Bold,
                maxWidth = w - 2f * pad, letterSpacingEm = -0.02f,
            )
            val numberCy = topZone + (h - pad - topZone) / 2f
            onDrawBehind {
                drawCircle(style.accent, dotR, Offset(pad + dotR, pad + dotR))
                baDrawText(weekdayT, style.accent, w - pad, pad + dotR, hAlign = 1f, capEm = 0.6f)
                baDrawText(numberT, style.content, w / 2f, numberCy)
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// 4x2 dot calendar
// ---------------------------------------------------------------------------------------------

@Composable
private fun DateCalendar(
    today: LocalDate,
    style: WidgetStyle,
    mode: EventMode,
    whenText: String?,
    eventTitle: String?,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val locale = Locale.getDefault()
    val monthStart = today.withDayOfMonth(1)
    val firstDow = WeekFields.of(locale).firstDayOfWeek
    val offset = (monthStart.dayOfWeek.value - firstDow.value + 7) % 7
    val daysInMonth = today.lengthOfMonth()
    val rows = (offset + daysInMonth + 6) / 7
    val initials = remember(firstDow, locale) {
        List(7) { firstDow.plus(it.toLong()).getDisplayName(JTextStyle.NARROW, locale).uppercase() }
    }
    val monthName = today.month.getDisplayName(JTextStyle.FULL, locale)
    val weekdayName = today.dayOfWeek.getDisplayName(JTextStyle.FULL, locale)
    val dayNumber = today.dayOfMonth
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val u = min(w / 368.dp.toPx(), h / BA_REF_2X2_H_DP.dp.toPx())
            val pad = 16.dp.toPx() * u
            val labelPx = 11.dp.toPx() * u
            val headerCy = pad + labelPx * 0.35f
            val lettersCy = headerCy + 20.dp.toPx() * u
            val gridTop = lettersCy + 8.dp.toPx() * u
            val leftW = min(w * 0.47f, 7f * 28.dp.toPx() * u)
            val pitch = min(leftW / 7f, (h - pad - gridTop) / rows)

            val monthT = baLabel(measurer, monthName, labelPx, leftW)
            val letterPx = min(10.dp.toPx() * u, pitch * 0.42f)
            val letters = Array(7) { baText(measurer, initials[it], letterPx, DotFonts.Grotesk, FontWeight.Medium) }

            val x0 = pad + 7f * pitch + 20.dp.toPx() * u
            val xr = w - pad
            val rw = (xr - x0).coerceAtLeast(0f)
            val weekdayT = baLabel(measurer, weekdayName, labelPx, rw)
            val numberPx = min(rw / 1.3f, 66.dp.toPx() * u)
            val numberT = baText(measurer, dayNumber.toString(), numberPx, DotFonts.Grotesk, FontWeight.Bold, rw, -0.02f)
            val numberCy = headerCy + labelPx * 0.35f + 10.dp.toPx() * u + 0.35f * numberPx
            val dividerY = numberCy + 0.35f * numberPx + 14.dp.toPx() * u
            val dotGap = 9.dp.toPx() * u
            val dotStroke = 2.5f.dp.toPx() * u
            val line1Cy = dividerY + 15.dp.toPx() * u
            val line2Cy = line1Cy + 21.dp.toPx() * u
            val line3Cy = line2Cy + 23.dp.toPx() * u

            val nextT = baLabel(measurer, BaStrings.NEXT, labelPx, rw)
            val whenT = if (mode == EventMode.EVENT && whenText != null) {
                baText(measurer, whenText, 13.dp.toPx() * u, DotFonts.Mono, FontWeight.Normal, rw)
            } else {
                null
            }
            val titleT = if (mode == EventMode.EVENT && eventTitle != null) {
                baTextEllipsized(measurer, eventTitle, 15.dp.toPx() * u, DotFonts.Grotesk, FontWeight.Medium, rw)
            } else {
                null
            }
            val emptyT = if (mode == EventMode.EMPTY) baLabel(measurer, BaStrings.NO_EVENTS, labelPx, rw) else null
            val hintT = if (mode == EventMode.HINT) {
                baText(measurer, BaStrings.TAP_SHOW_EVENTS, 13.dp.toPx() * u, DotFonts.Grotesk, FontWeight.Normal, rw)
            } else {
                null
            }

            val pastColor = style.muted.copy(alpha = 0.55f)
            val dividerColor = style.muted.copy(alpha = 0.5f)
            val dashes = PathEffect.dashPathEffect(floatArrayOf(0.01f, dotGap), 0f)

            onDrawBehind {
                baDrawText(monthT, style.content, pad, headerCy, hAlign = 0f)
                for (c in 0 until 7) {
                    baDrawText(letters[c], style.muted, pad + pitch * (c + 0.5f), lettersCy)
                }
                for (day in 1..daysInMonth) {
                    val cell = offset + day - 1
                    val cx = pad + pitch * ((cell % 7) + 0.5f)
                    val cy = gridTop + pitch * ((cell / 7) + 0.5f)
                    when {
                        day == dayNumber -> drawCircle(style.accent, pitch * 0.34f, Offset(cx, cy))
                        day < dayNumber -> drawCircle(pastColor, pitch * 0.2f, Offset(cx, cy))
                        else -> drawCircle(style.content, pitch * 0.26f, Offset(cx, cy))
                    }
                }

                baDrawText(weekdayT, style.muted, x0, headerCy, hAlign = 0f)
                baDrawText(numberT, style.content, x0, numberCy, hAlign = 0f)
                drawLine(
                    color = dividerColor,
                    start = Offset(x0, dividerY),
                    end = Offset(xr, dividerY),
                    strokeWidth = dotStroke,
                    cap = StrokeCap.Round,
                    pathEffect = dashes,
                )
                when (mode) {
                    EventMode.EVENT -> {
                        baDrawText(nextT, style.muted, x0, line1Cy, hAlign = 0f)
                        if (whenT != null) baDrawText(whenT, style.content, x0, line2Cy, hAlign = 0f)
                        if (titleT != null) baDrawText(titleT, style.content, x0, line3Cy, hAlign = 0f)
                    }
                    EventMode.EMPTY -> {
                        if (emptyT != null) baDrawText(emptyT, style.muted, x0, line1Cy, hAlign = 0f)
                    }
                    EventMode.HINT -> {
                        if (hintT != null) baDrawText(hintT, style.muted, x0, line1Cy, hAlign = 0f)
                    }
                }
            }
        },
    )
}

package com.dotos.feature.widgets.dot

import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotFonts
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetFace
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetShape
import com.dotos.feature.widgets.api.WidgetSize
import com.dotos.feature.widgets.api.WidgetStyle
import com.dotos.feature.widgets.api.rememberTicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Clock widget. Faces: analog, digital dots, world, binary (BCD) and micro. Wakes once per minute
 * (see [rememberTicker]); there is no per-frame work at all. The 1x1 and 2x2 sizes are circles.
 */
object ClockWidget : DotWidgetProvider {
    const val FACE_ANALOG = "analog"
    const val FACE_DIGITAL = "digital_dots"
    const val FACE_WORLD = "world"
    const val FACE_BINARY = "binary"
    const val FACE_MICRO = "micro"

    /** Config key for the world clock: an IANA zone id such as "Europe/London". */
    const val CONFIG_ZONE = "zone"

    override val kind = "clock"
    override val title = "Clock"
    override val description = "Analog, dot-matrix, world and binary clocks."
    override val sizes = listOf(WidgetSize(1, 1), WidgetSize(2, 2), WidgetSize(4, 2))
    override val faces = listOf(
        WidgetFace(FACE_ANALOG, "Analog", "Thick white hands on a dark disc"),
        WidgetFace(FACE_DIGITAL, "Digital", "Hours over minutes in big dot-matrix digits"),
        WidgetFace(FACE_WORLD, "World clock", "The time in another city"),
        WidgetFace(FACE_BINARY, "Binary", "Binary coded decimal dot columns"),
        WidgetFace(FACE_MICRO, "Micro", "Big time with date and micrographics"),
    )

    override fun shape(size: WidgetSize): WidgetShape =
        if (size.spanX == size.spanY) WidgetShape.CIRCLE else WidgetShape.ROUNDED

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val context = LocalContext.current
        val host = LocalWidgetHost.current
        val now = rememberTicker(60_000L)
        val is24 = remember(now) { DateFormat.is24HourFormat(context) }
        val faceId = faces.firstOrNull { it.id == instance.face }?.id ?: FACE_ANALOG
        val zoneKey = instance.config[CONFIG_ZONE]
        val zone = remember(zoneKey, faceId, now) {
            if (faceId == FACE_WORLD) clockZone(zoneKey) else ZoneId.systemDefault()
        }
        val time = remember(now, zone) { Instant.ofEpochMilli(now).atZone(zone) }
        val deviceDate = remember(now) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate() }

        val readable = "${if (is24) baTwoDigits(time.hour) else time.hour12().toString()}:${baTwoDigits(time.minute)}"
        val description = if (faceId == FACE_WORLD) "${clockCityLabel(time.zone)} $readable" else "Clock $readable"
        val root = modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .baTap(enabled = !instance.isPreview) { host.baLaunch(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }

        when (faceId) {
            FACE_DIGITAL -> DigitalClockFace(time, is24, style, root)
            FACE_WORLD -> WorldClockFace(time, deviceDate, is24, style, root)
            FACE_BINARY -> BinaryClockFace(time, is24, style, root)
            FACE_MICRO -> MicroClockFace(time, is24, style, root)
            else -> AnalogClockFace(time, is24, style, root)
        }
    }
}

private val DEG_TO_RAD: Float = (PI / 180.0).toFloat()

private fun ZonedDateTime.hour12(): Int {
    val h = hour % 12
    return if (h == 0) 12 else h
}

private fun ZonedDateTime.displayHour(is24: Boolean): Int = if (is24) hour else hour12()

private fun ZonedDateTime.ampm(is24: Boolean): String = if (is24) "" else if (hour < 12) "AM" else "PM"

private fun ZonedDateTime.weekdayShort(): String =
    dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()).uppercase()

private fun ZonedDateTime.monthShort(): String =
    month.getDisplayName(JTextStyle.SHORT, Locale.getDefault()).uppercase()

private fun clockZone(id: String?): ZoneId =
    if (id.isNullOrBlank()) ZoneId.systemDefault() else runCatching { ZoneId.of(id) }.getOrElse { ZoneId.systemDefault() }

/** "Europe/London" -> "LONDON", "America/New_York" -> "NEW YORK", "UTC" -> "UTC". */
private fun clockCityLabel(zone: ZoneId): String {
    val id = zone.id
    return id.substringAfterLast('/', id).replace('_', ' ').uppercase()
}

/** 24-hour (or 12-hour) number of the hours, always two digits for the dot faces. */
private fun ZonedDateTime.hourText(is24: Boolean): String = baTwoDigits(displayHour(is24))

/** Time as two Doto rows: hours over minutes, [p] = dot pitch in px. Block is 11p x 16p. */
private fun DrawScope.drawStackedDotTime(hh: BaText, mm: BaText, cx: Float, cy: Float, p: Float, color: Color) {
    baDrawText(hh, color, cx, cy - 4.5f * p, shiftEm = BA_DOTO_INK_SHIFT_EM)
    baDrawText(mm, color, cx, cy + 4.5f * p, shiftEm = BA_DOTO_INK_SHIFT_EM)
}

/** Time as "HH:MM" in Doto with hand-placed colon dots. Block is 26p x 7p. */
private fun DrawScope.drawInlineDotTime(
    hh: BaText,
    mm: BaText,
    cx: Float,
    cy: Float,
    p: Float,
    color: Color,
    colon: Color,
) {
    baDrawText(hh, color, cx - 7.5f * p, cy, shiftEm = BA_DOTO_INK_SHIFT_EM)
    baDrawText(mm, color, cx + 7.5f * p, cy, shiftEm = BA_DOTO_INK_SHIFT_EM)
    val r = BA_DOTO_DOT_RADIUS_PITCH * p
    drawCircle(colon, r, Offset(cx, cy - 1.5f * p))
    drawCircle(colon, r, Offset(cx, cy + 1.5f * p))
}

/**
 * Circle layout shared by world and micro faces: a small caps line, the dot time and an optional
 * muted line underneath. Very small circles (1x1) stack the digits and drop the bottom line.
 */
private fun CacheDrawScope.circleLabeledTime(
    measurer: TextMeasurer,
    hh: String,
    mm: String,
    top: String,
    bottom: String?,
    style: WidgetStyle,
): DrawResult {
    val w = size.width
    val h = size.height
    val d = min(w, h)
    val cx = w / 2f
    val cy = h / 2f
    val pad = max(6.dp.toPx(), d * 0.065f)
    val rAvail = d / 2f - pad
    if (d < 130.dp.toPx()) {
        val p = rAvail / 12.5f
        val hhT = baDoto(measurer, hh, 10f * p)
        val mmT = baDoto(measurer, mm, 10f * p)
        val topT = baLabel(measurer, top, max(6.dp.toPx(), d * 0.075f), d * 0.62f)
        return onDrawBehind {
            baDrawText(topT, style.content, cx, cy - 8.6f * p)
            baDrawText(hhT, style.content, cx, cy - 2.9f * p, shiftEm = BA_DOTO_INK_SHIFT_EM)
            baDrawText(mmT, style.content, cx, cy + 6.1f * p, shiftEm = BA_DOTO_INK_SHIFT_EM)
        }
    }
    val p = d * 0.030f
    val labelPx = d * 0.066f
    val hhT = baDoto(measurer, hh, 10f * p)
    val mmT = baDoto(measurer, mm, 10f * p)
    val topT = baLabel(measurer, top, labelPx, d * 0.66f)
    val bottomT = bottom?.let { baLabel(measurer, it, labelPx * 0.92f, d * 0.66f) }
    return onDrawBehind {
        baDrawText(topT, style.content, cx, cy - 7f * p)
        drawInlineDotTime(hhT, mmT, cx, cy, p, style.content, style.accent)
        if (bottomT != null) baDrawText(bottomT, style.muted, cx, cy + 6.5f * p)
    }
}

// ---------------------------------------------------------------------------------------------
// Analog
// ---------------------------------------------------------------------------------------------

@Composable
private fun AnalogClockFace(time: ZonedDateTime, is24: Boolean, style: WidgetStyle, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val weekday = time.weekdayShort()
    val dateText = "${time.dayOfMonth} ${time.monthShort()}"
    val hourStr = if (is24) baTwoDigits(time.hour) else time.hour12().toString()
    val digital = "$hourStr:${baTwoDigits(time.minute)}"
    val ampm = time.ampm(is24)
    val hourAngle = ((time.hour % 12) + time.minute / 60f) * 30f * DEG_TO_RAD
    val minuteAngle = time.minute * 6f * DEG_TO_RAD
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val d = min(w, h)
            val cx = w / 2f
            val cy = h / 2f
            val wide = w > h * 1.4f
            val r = d / 2f - max(7.dp.toPx(), d * 0.07f)
            val u = min(w / BA_REF_2X2_W_DP.dp.toPx(), h / BA_REF_2X2_H_DP.dp.toPx())

            val hourLen = r * 0.46f
            val minuteLen = r * 0.76f
            val hourStroke = r * 0.17f
            val minuteStroke = max(r * 0.075f, 2.dp.toPx())
            val hourEnd = Offset(cx + sin(hourAngle) * hourLen, cy - cos(hourAngle) * hourLen)
            val minuteEnd = Offset(cx + sin(minuteAngle) * minuteLen, cy - cos(minuteAngle) * minuteLen)
            val markR = r * 0.84f
            val pivotColor = style.background.copy(alpha = 1f)
            val disc = style.track.copy(alpha = 0.5f)

            // Side complications on the wide size: date on the left, digital readout on the right.
            val sidePad = 14.dp.toPx()
            val sideW = (cx - r - 12.dp.toPx() - sidePad).coerceAtLeast(0f)
            val labelPx = 11.dp.toPx() * u
            val dayT = if (wide) baLabel(measurer, weekday, labelPx, sideW) else null
            val dateT = if (wide) baLabel(measurer, dateText, labelPx, sideW) else null
            val timeT = if (wide) baText(measurer, digital, 15.dp.toPx() * u, DotFonts.Mono, FontWeight.Normal, sideW) else null
            val ampmT = if (wide && ampm.isNotEmpty()) baLabel(measurer, ampm, labelPx, sideW) else null
            val lineGap = 10.dp.toPx() * u

            onDrawBehind {
                if (wide) drawCircle(disc, r, Offset(cx, cy))
                // 3 / 6 / 9 o'clock tick dots, the single red dot sits at 12.
                val tickR = r * 0.028f
                drawCircle(style.muted, tickR, Offset(cx + markR, cy))
                drawCircle(style.muted, tickR, Offset(cx - markR, cy))
                drawCircle(style.muted, tickR, Offset(cx, cy + markR))
                drawCircle(style.accent, r * 0.055f, Offset(cx, cy - markR))

                drawLine(style.content, Offset(cx, cy), minuteEnd, strokeWidth = minuteStroke, cap = StrokeCap.Round)
                drawLine(style.content, Offset(cx, cy), hourEnd, strokeWidth = hourStroke, cap = StrokeCap.Round)
                drawCircle(pivotColor, r * 0.045f, Offset(cx, cy))

                if (wide) {
                    val lx = sidePad
                    if (dayT != null) baDrawText(dayT, style.content, lx, cy - lineGap, hAlign = 0f)
                    if (dateT != null) baDrawText(dateT, style.muted, lx, cy + lineGap, hAlign = 0f)
                    val rx = w - sidePad
                    val timeY = if (ampmT != null) cy - lineGap else cy
                    if (timeT != null) baDrawText(timeT, style.content, rx, timeY, hAlign = 1f)
                    if (ampmT != null) baDrawText(ampmT, style.muted, rx, cy + lineGap, hAlign = 1f)
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Digital dots
// ---------------------------------------------------------------------------------------------

@Composable
private fun DigitalClockFace(time: ZonedDateTime, is24: Boolean, style: WidgetStyle, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val hh = time.hourText(is24)
    val mm = baTwoDigits(time.minute)
    val ampm = time.ampm(is24)
    val dateLine = "${time.weekdayShort()} ${time.dayOfMonth} ${time.monthShort()}" + if (ampm.isEmpty()) "" else "  $ampm"
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val pad = max(6.dp.toPx(), min(w, h) * 0.065f)
            val wide = w > h * 1.4f
            if (wide) {
                val u = min(w / 368.dp.toPx(), h / BA_REF_2X2_H_DP.dp.toPx())
                val labelPx = 11.dp.toPx() * u
                val gap = 12.dp.toPx() * u
                val reserve = labelPx * 0.7f + gap
                val p = min((w - 2f * pad) / 26f, (h - 2f * pad - reserve) / 7f)
                val hhT = baDoto(measurer, hh, 10f * p)
                val mmT = baDoto(measurer, mm, 10f * p)
                val label = baLabel(measurer, dateLine, labelPx, w - 2f * pad)
                val top = (h - (7f * p + reserve)) / 2f
                val timeCy = top + 3.5f * p
                val labelCy = top + 7f * p + gap + labelPx * 0.35f
                return@drawWithCache onDrawBehind {
                    drawInlineDotTime(hhT, mmT, w / 2f, timeCy, p, style.content, style.accent)
                    baDrawText(label, style.muted, w / 2f, labelCy)
                }
            }
            val cx = w / 2f
            val cy = h / 2f
            val rAvail = min(w, h) / 2f - pad
            val p = min(rAvail / 9.7f, min((w - 2f * pad) / 11f, (h - 2f * pad) / 16f))
            val hhT = baDoto(measurer, hh, 10f * p)
            val mmT = baDoto(measurer, mm, 10f * p)
            onDrawBehind { drawStackedDotTime(hhT, mmT, cx, cy, p, style.content) }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// World clock
// ---------------------------------------------------------------------------------------------

@Composable
private fun WorldClockFace(
    time: ZonedDateTime,
    deviceDate: LocalDate,
    is24: Boolean,
    style: WidgetStyle,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val city = clockCityLabel(time.zone)
    val hh = time.hourText(is24)
    val mm = baTwoDigits(time.minute)
    val delta = ChronoUnit.DAYS.between(deviceDate, time.toLocalDate()).toInt()
    val relative = when (delta) {
        0 -> BaStrings.TODAY
        1 -> BaStrings.TOMORROW
        -1 -> BaStrings.YESTERDAY
        else -> time.weekdayShort()
    }
    val ampm = time.ampm(is24)
    val sub = if (ampm.isEmpty()) relative else "$relative  $ampm"
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val wide = w > h * 1.4f
            if (!wide) return@drawWithCache circleLabeledTime(measurer, hh, mm, city, sub, style)

            val pad = 16.dp.toPx()
            val u = min(w / 368.dp.toPx(), h / BA_REF_2X2_H_DP.dp.toPx())
            val labelPx = 12.dp.toPx() * u
            val topCy = pad + labelPx * 0.35f
            val areaTop = pad + labelPx * 0.7f + 8.dp.toPx() * u
            val areaBottom = h - pad
            val p = min((w - 2f * pad) / 26f, (areaBottom - areaTop) / 7f)
            val timeCy = (areaTop + areaBottom) / 2f
            val cityT = baLabel(measurer, city, labelPx, (w - 2f * pad) * 0.6f)
            val subT = baLabel(measurer, sub, labelPx * 0.9f, (w - 2f * pad) * 0.4f)
            val hhT = baDoto(measurer, hh, 10f * p)
            val mmT = baDoto(measurer, mm, 10f * p)
            onDrawBehind {
                baDrawText(cityT, style.content, pad, topCy, hAlign = 0f)
                baDrawText(subT, style.muted, w - pad, topCy, hAlign = 1f)
                drawInlineDotTime(hhT, mmT, w / 2f, timeCy, p, style.content, style.accent)
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Binary coded decimal
// ---------------------------------------------------------------------------------------------

@Composable
private fun BinaryClockFace(time: ZonedDateTime, is24: Boolean, style: WidgetStyle, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val hour = time.displayHour(is24)
    val minute = time.minute
    val digits = remember(hour, minute) { intArrayOf(hour / 10, hour % 10, minute / 10, minute % 10) }
    // Number of bit rows each column uses (counted from the bottom): hour tens 0..2, minute tens 0..5.
    val bitsPerColumn = remember { intArrayOf(2, 4, 3, 4) }
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val d = min(w, h)
            val cx = w / 2f
            val cy = h / 2f
            val wide = w > h * 1.4f
            val pad = max(6.dp.toPx(), d * 0.065f)
            val labelsOn = d >= 120.dp.toPx()
            val heightUnits = if (labelsOn) 4.19f else 3.64f
            val q: Float
            val kx: Float
            if (wide) {
                q = (h - 2f * pad) / heightUnits
                kx = min(1.6f, ((w - 2f * pad - 0.64f * q) / 3.8f) / q)
            } else {
                val widthUnits = 4.44f
                val halfDiagonal = kotlin.math.sqrt((widthUnits / 2f) * (widthUnits / 2f) + (heightUnits / 2f) * (heightUnits / 2f))
                q = min((d / 2f - pad) / halfDiagonal, min((w - 2f * pad) / widthUnits, (h - 2f * pad) / heightUnits))
                kx = 1f
            }
            val colStep = q * kx
            val gap = 0.8f * colStep
            val spanX = 3f * colStep + gap
            val startX = cx - spanX / 2f
            val colX = floatArrayOf(startX, startX + colStep, startX + 2f * colStep + gap, startX + 3f * colStep + gap)
            val gridTop = cy - heightUnits * q / 2f + 0.32f * q
            val dotR = 0.32f * q
            val labelPx = 0.42f * q
            val labels = if (labelsOn) {
                Array(4) { baText(measurer, digits[it].toString(), labelPx, DotFonts.Mono, FontWeight.Normal) }
            } else {
                null
            }
            val lastRowY = gridTop + 3f * q
            onDrawBehind {
                for (col in 0 until 4) {
                    val rows = bitsPerColumn[col]
                    for (row in (4 - rows) until 4) {
                        val weight = 1 shl (3 - row)
                        val lit = (digits[col] and weight) != 0
                        drawCircle(
                            color = if (lit) style.content else style.track,
                            radius = dotR,
                            center = Offset(colX[col], gridTop + row * q),
                        )
                    }
                    if (labels != null) baDrawText(labels[col], style.muted, colX[col], lastRowY + 0.72f * q, capEm = 0.7f)
                }
                // The single red dot sits where a colon would be.
                drawCircle(style.accent, 0.16f * q, Offset((colX[1] + colX[2]) / 2f, gridTop + 1.5f * q))
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Micro
// ---------------------------------------------------------------------------------------------

@Composable
private fun MicroClockFace(time: ZonedDateTime, is24: Boolean, style: WidgetStyle, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val hh = time.hourText(is24)
    val mm = baTwoDigits(time.minute)
    val ampm = time.ampm(is24)
    val dateLine = "${time.weekdayShort()} ${time.dayOfMonth} ${time.monthShort()}"
    val weekNumber = time.get(WeekFields.ISO.weekOfWeekBasedYear())
    val micro = "W$weekNumber  D${time.dayOfYear}"
    val microWithMeridiem = if (ampm.isEmpty()) micro else "$micro  $ampm"
    val hour = time.hour
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val wide = w > h * 1.4f
            if (!wide) return@drawWithCache circleLabeledTime(measurer, hh, mm, dateLine, micro, style)

            val pad = 16.dp.toPx()
            val u = min(w / 368.dp.toPx(), h / BA_REF_2X2_H_DP.dp.toPx())
            val labelPx = 11.dp.toPx() * u
            val topCy = pad + labelPx * 0.35f
            val pitch = (w - 2f * pad) / 24f
            val stripR = min(pitch * 0.28f, 5.dp.toPx())
            val stripCy = h - pad - stripR
            val areaTop = topCy + labelPx * 0.35f + 10.dp.toPx() * u
            val areaBottom = stripCy - stripR - 10.dp.toPx() * u
            val p = min((w - 2f * pad) / 26f, (areaBottom - areaTop) / 7f)
            val timeCy = (areaTop + areaBottom) / 2f
            val dateT = baLabel(measurer, dateLine, labelPx, (w - 2f * pad) * 0.55f)
            val microT = baText(
                measurer, microWithMeridiem, labelPx * 1.05f, DotFonts.Mono, FontWeight.Normal,
                maxWidth = (w - 2f * pad) * 0.42f,
            )
            val hhT = baDoto(measurer, hh, 10f * p)
            val mmT = baDoto(measurer, mm, 10f * p)
            onDrawBehind {
                baDrawText(dateT, style.content, pad, topCy, hAlign = 0f)
                baDrawText(microT, style.muted, w - pad, topCy, hAlign = 1f)
                // Hour dots: past hours white, the current hour red, the rest dim.
                drawInlineDotTime(hhT, mmT, w / 2f, timeCy, p, style.content, style.content)
                for (i in 0 until 24) {
                    val color = when {
                        i < hour -> style.content
                        i == hour -> style.accent
                        else -> style.track
                    }
                    drawCircle(color, stripR, Offset(pad + pitch * (i + 0.5f), stripCy))
                }
            }
        },
    )
}

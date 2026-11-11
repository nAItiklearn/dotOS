package com.dotos.feature.widgets.dot

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.ContextCompat
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetHostServices
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetShape
import com.dotos.feature.widgets.api.WidgetSize
import com.dotos.feature.widgets.api.rememberTicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@Immutable
internal data class AwayData(
    /** Epoch millis of the last unlock (ACTION_USER_PRESENT), 0 = unknown. */
    val lastUnlock: Long = 0L,
    /** Epoch millis of the screen-off that started the current/last break, 0 = unknown. */
    val lastOff: Long = 0L,
    /** ISO date -> longest break (ms) that ended on that day. */
    val days: Map<String, Long> = emptyMap(),
    val loaded: Boolean = false,
)

/**
 * Process-level screen-usage tracker. Registers one dynamic receiver for SCREEN_OFF / SCREEN_ON /
 * USER_PRESENT the first time a widget is composed and never unregisters (the launcher process is
 * long-lived). Summary values are persisted through the host state API under the shared "away:" keys.
 * Everything stays on the device.
 *
 * A "break" is the time from the screen turning off (after an unlocked session) to the next unlock.
 */
internal object AwayTracker {
    private const val KEY_UNLOCK = "away:last_unlock"
    private const val KEY_OFF = "away:last_off"
    private const val KEY_DAYS = "away:days"
    private const val KEEP_DAYS = 8L

    private val started = AtomicBoolean(false)
    private var hostRef: WeakReference<WidgetHostServices>? = null
    private var appContext: Context? = null
    private var dirty = false
    private val mutableData = MutableStateFlow(AwayData())
    val data: StateFlow<AwayData> = mutableData.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff(now)
                Intent.ACTION_USER_PRESENT -> onUnlock(now)
                // Devices without a lock screen never send USER_PRESENT: screen on == unlocked.
                Intent.ACTION_SCREEN_ON -> if (!keyguardLocked(context)) onUnlock(now)
            }
        }
    }

    /** Idempotent. Call from a LaunchedEffect (never for previews). */
    fun ensureStarted(context: Context, host: WidgetHostServices) {
        hostRef = WeakReference(host)
        if (started.compareAndSet(false, true)) {
            val app = context.applicationContext
            appContext = app
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            BatchScope.launch { load(host) }
        } else if (dirty) {
            persist()
        }
    }

    private fun onScreenOff(now: Long) {
        mutableData.update { d -> if (d.lastOff <= d.lastUnlock) d.copy(lastOff = now) else d }
        persist()
    }

    private fun onUnlock(now: Long) {
        mutableData.update { d -> applyUnlock(d, now) }
        persist()
    }

    private fun applyUnlock(d: AwayData, now: Long): AwayData {
        val breakMs = if (d.lastOff > d.lastUnlock) now - d.lastOff else 0L
        if (breakMs <= 0L) return d.copy(lastUnlock = now)
        val today = LocalDate.now().toString()
        val longest = max(d.days[today] ?: 0L, breakMs)
        return d.copy(lastUnlock = now, days = prune(d.days + (today to longest)))
    }

    private fun prune(days: Map<String, Long>): Map<String, Long> {
        val cutoff = LocalDate.now().minusDays(KEEP_DAYS).toString()
        return days.filterKeys { it >= cutoff }
    }

    private suspend fun load(host: WidgetHostServices) {
        val unlock = readState(host, KEY_UNLOCK)?.toLongOrNull() ?: 0L
        val off = readState(host, KEY_OFF)?.toLongOrNull() ?: 0L
        val stored = parseDays(readState(host, KEY_DAYS))
        val now = System.currentTimeMillis()
        val app = appContext
        val missedUnlock = app != null && !keyguardLocked(app)
        mutableData.update { cur ->
            var d = cur.copy(
                lastUnlock = max(cur.lastUnlock, unlock),
                lastOff = max(cur.lastOff, off),
                days = prune(mergeDays(stored, cur.days)),
                loaded = true,
            )
            if (d.lastUnlock == 0L) {
                // First run: the user is looking at the launcher right now.
                d = d.copy(lastUnlock = now)
            } else if (d.lastOff > d.lastUnlock && missedUnlock) {
                // The process was away during an unlock; count it as unlocked now.
                d = applyUnlock(d, now)
            }
            d
        }
        persist()
    }

    private fun persist() {
        val host = hostRef?.get()
        if (host == null) {
            dirty = true
            return
        }
        dirty = false
        val d = mutableData.value
        if (!d.loaded) return
        BatchScope.launch {
            host.putState(KEY_UNLOCK, d.lastUnlock.toString())
            host.putState(KEY_OFF, d.lastOff.toString())
            host.putState(KEY_DAYS, d.days.entries.joinToString(";") { "${it.key}=${it.value}" })
        }
    }

    private suspend fun readState(host: WidgetHostServices, key: String): String? =
        withTimeoutOrNull(2_000L) { host.state(key).firstOrNull() }

    private fun parseDays(raw: String?): Map<String, Long> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val out = HashMap<String, Long>()
        for (part in raw.split(';')) {
            val kv = part.split('=')
            val value = if (kv.size == 2) kv[1].toLongOrNull() else null
            if (value != null) out[kv[0]] = value
        }
        return out
    }

    private fun mergeDays(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> {
        val out = HashMap<String, Long>(a)
        for ((k, v) in b) out[k] = max(out[k] ?: 0L, v)
        return out
    }

    private fun keyguardLocked(context: Context): Boolean {
        val manager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return manager.isKeyguardLocked
    }
}

@Immutable
private class AwayModel(
    val sinceMs: Long,
    val longestTodayMs: Long,
    /** Longest break per day, oldest first; the last entry is today. */
    val week: LongArray,
    val initials: String,
)

object AwayTimeWidget : DotWidgetProvider {
    override val kind: String = "away_time"
    override val title: String = "Away Time"
    override val description: String =
        "Time since you last unlocked your phone, your longest break today and a 7 day trend. Tracked on device only."
    override val sizes: List<WidgetSize> = listOf(WidgetSize(2, 1), WidgetSize(2, 2), WidgetSize(4, 2))

    override fun shape(size: WidgetSize): WidgetShape = if (size.spanY <= 1) WidgetShape.PILL else WidgetShape.ROUNDED

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val host = LocalWidgetHost.current
        val context = LocalContext.current
        val preview = instance.isPreview
        LaunchedEffect(preview) {
            if (!preview) AwayTracker.ensureStarted(context, host)
        }
        val data by AwayTracker.data.collectAsState()
        val now = rememberTicker(60_000L)

        val model = remember(data, now, preview) {
            if (preview) {
                AwayModel(
                    sinceMs = 3L * HOUR + 22L * MINUTE,
                    longestTodayMs = 2L * HOUR + 5L * MINUTE,
                    week = longArrayOf(3L * HOUR, 5L * HOUR, 90L * MINUTE, 7L * HOUR, 2L * HOUR, 4L * HOUR, 2L * HOUR + 5L * MINUTE),
                    initials = "MTWTFSS",
                )
            } else {
                buildModel(data, now)
            }
        }

        val a11y = "Away time. ${spoken(model.sinceMs)} since last unlock. Longest break today ${spoken(model.longestTodayMs)}."
        val root = modifier.semantics(mergeDescendants = true) { contentDescription = a11y }
        when {
            instance.spanY <= 1 -> AwayPill(root, model)
            instance.spanX >= 4 -> AwayWide(root, model)
            else -> AwayTile(root, model)
        }
    }
}

private const val MINUTE = 60_000L
private const val HOUR = 3_600_000L

private fun buildModel(data: AwayData, now: Long): AwayModel {
    val today = LocalDate.now()
    val week = LongArray(7)
    val letters = StringBuilder(7)
    for (i in 0 until 7) {
        val date = today.minusDays((6 - i).toLong())
        week[i] = data.days[date.toString()] ?: 0L
        letters.append("MTWTFSS"[date.dayOfWeek.ordinal])
    }
    val since = if (data.lastUnlock > 0L) (now - data.lastUnlock).coerceAtLeast(0L) else 0L
    return AwayModel(since, week[6], week, letters.toString())
}

private fun spoken(ms: Long): String {
    val totalMin = (ms / MINUTE).coerceAtLeast(0L)
    return "${totalMin / 60} hours ${totalMin % 60} minutes"
}

private val SmallLetter = SpanStyle(fontSize = 0.55.em)

/** "3H22M" with small H and M, or "22M" under an hour. */
private fun durationText(ms: Long): AnnotatedString {
    val totalMin = (ms / MINUTE).coerceAtLeast(0L)
    val h = totalMin / 60L
    val m = (totalMin % 60L).toInt()
    return buildAnnotatedString {
        if (h > 0L) {
            append(h.toString())
            withStyle(SmallLetter) { append("H") }
            append(pad2(m))
            withStyle(SmallLetter) { append("M") }
        } else {
            append(m.toString())
            withStyle(SmallLetter) { append("M") }
        }
    }
}

/** Fixed-width stand-in so the size does not jitter as digits change. */
private fun durationTemplate(ms: Long): AnnotatedString {
    val template = when {
        ms >= 10L * HOUR -> 10L * HOUR + 11L * MINUTE
        ms >= HOUR -> HOUR + 11L * MINUTE
        else -> 11L * MINUTE
    }
    return durationText(template)
}

@Composable
private fun AwayPill(modifier: Modifier, model: AwayModel) {
    val style = LocalWidgetStyle.current
    BoxWithConstraints(modifier) {
        val padH = (maxHeight * 0.2f).coerceIn(12.dp, 22.dp)
        val labelH = (maxHeight * 0.2f).coerceIn(12.dp, 18.dp)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = padH, vertical = padH * 0.5f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FitText(
                text = durationText(model.sinceMs),
                color = style.content,
                modifier = Modifier.fillMaxWidth().weight(1f),
                maxSize = 44f,
                sizeTemplate = durationTemplate(model.sinceMs),
            )
            FitText(
                text = "SINCE UNLOCK",
                color = style.muted,
                modifier = Modifier.fillMaxWidth().height(labelH),
                role = TextRole.LABEL,
                maxSize = 10f,
            )
        }
    }
}

@Composable
private fun AwayHeader(model: AwayModel, modifier: Modifier = Modifier) {
    val style = LocalWidgetStyle.current
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(style.accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            FitText(
                text = "SINCE UNLOCK",
                color = style.muted,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                role = TextRole.LABEL,
                maxSize = 11f,
                alignment = Alignment.CenterStart,
            )
        }
        FitText(
            text = durationText(model.sinceMs),
            color = style.content,
            modifier = Modifier.fillMaxWidth().weight(1f),
            maxSize = 52f,
            sizeTemplate = durationTemplate(model.sinceMs),
            alignment = Alignment.CenterStart,
        )
        Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
            FitText(
                text = "LONGEST BREAK",
                color = style.muted,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                role = TextRole.LABEL,
                maxSize = 10f,
                alignment = Alignment.CenterStart,
            )
            FitText(
                text = durationText(model.longestTodayMs),
                color = style.content,
                modifier = Modifier.fillMaxHeight(),
                maxSize = 18f,
                sizeTemplate = durationTemplate(model.longestTodayMs),
                alignment = Alignment.CenterEnd,
            )
        }
    }
}

@Composable
private fun WeekBars(model: AwayModel, modifier: Modifier = Modifier) {
    val style = LocalWidgetStyle.current
    Column(modifier) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            drawWeekDots(model.week, WEEK_ROWS, style.content, style.muted, style.track)
        }
        Row(Modifier.fillMaxWidth().height(12.dp)) {
            for (i in 0 until 7) {
                FitText(
                    text = model.initials.getOrElse(i) { ' ' }.toString(),
                    color = if (i == 6) style.content else style.muted,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    role = TextRole.LABEL,
                    maxSize = 9f,
                    sizeTemplate = "M",
                )
            }
        }
    }
}

@Composable
private fun AwayTile(modifier: Modifier, model: AwayModel) {
    Column(modifier.padding(14.dp)) {
        AwayHeader(model, Modifier.weight(1.25f).fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        WeekBars(model, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun AwayWide(modifier: Modifier, model: AwayModel) {
    Row(modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        AwayHeader(model, Modifier.weight(1f).fillMaxHeight())
        WeekBars(model, Modifier.weight(1f).fillMaxHeight())
    }
}

private const val WEEK_ROWS = 6

/** One column of dots per day, filled from the bottom; today is bright, earlier days muted. */
private fun DrawScope.drawWeekDots(week: LongArray, rows: Int, today: Color, past: Color, off: Color) {
    val cols = week.size
    var peak = 2L * HOUR
    for (v in week) peak = max(peak, v)
    val px = size.width / cols
    val py = size.height / rows
    val radius = min(px, py) * 0.34f
    for (c in 0 until cols) {
        val lit = if (week[c] <= 0L) 0 else ceil(week[c].toFloat() / peak * rows).toInt().coerceIn(1, rows)
        val on = if (c == cols - 1) today else past
        for (r in 0 until rows) {
            val fromBottom = rows - 1 - r
            drawCircle(if (fromBottom < lit) on else off, radius, Offset(px * c + px / 2f, py * r + py / 2f))
        }
    }
}

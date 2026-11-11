package com.dotos.feature.widgets.dot

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetHostServices
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetShape
import com.dotos.feature.widgets.api.WidgetSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

@Immutable
internal data class RecorderState(
    val recording: Boolean = false,
    /** [SystemClock.elapsedRealtime] when the current recording started. */
    val startedAt: Long = 0L,
    /** True when the last attempt failed (no mic, busy, storage). Cleared by the next start. */
    val failed: Boolean = false,
)

/**
 * Process-level owner of the one MediaRecorder. Lives outside any composition so scrolling pages or
 * recomposing never stops a recording. All calls happen on the main thread.
 */
@SuppressLint("MissingPermission") // RECORD_AUDIO is requested through the widget host before start() is called.
internal object RecorderEngine {
    private val mutableState = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = mutableState.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var outFile: File? = null

    fun start(context: Context): Boolean {
        if (recorder != null) return true
        val app = context.applicationContext
        val dir = File(app.filesDir, "recordings")
        if (!dir.exists() && !dir.mkdirs()) {
            mutableState.value = RecorderState(failed = true)
            return false
        }
        val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
        val rec = createRecorder(app)
        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(SAMPLE_RATE)
            rec.setAudioEncodingBitRate(BIT_RATE)
            rec.setAudioChannels(1)
            rec.setOutputFile(file.absolutePath)
            rec.setOnErrorListener { _, _, _ -> stop() }
            rec.prepare()
            rec.start()
        } catch (e: IOException) {
            return fail(rec, file)
        } catch (e: RuntimeException) {
            // IllegalStateException (mic busy / not allowed) and friends.
            return fail(rec, file)
        }
        recorder = rec
        outFile = file
        mutableState.value = RecorderState(recording = true, startedAt = SystemClock.elapsedRealtime())
        return true
    }

    fun stop() {
        val rec = recorder
        val file = outFile
        recorder = null
        outFile = null
        if (rec == null) {
            mutableState.value = RecorderState()
            return
        }
        var ok = true
        try {
            rec.stop()
        } catch (e: RuntimeException) {
            // Thrown when stopped right after start (no valid audio data): drop the empty file.
            ok = false
        }
        release(rec)
        if (!ok) file?.delete()
        mutableState.value = RecorderState(failed = !ok)
    }

    /** Loudness since the previous call, 0..1 (perceptually lifted). Call at ~15 Hz while visible. */
    fun sampleLevel(): Float {
        val rec = recorder ?: return 0f
        val amplitude = try {
            rec.maxAmplitude
        } catch (e: RuntimeException) {
            0
        }
        val normalized = (amplitude / 32767f).coerceIn(0f, 1f)
        return sqrt(normalized)
    }

    private fun fail(rec: MediaRecorder, file: File): Boolean {
        release(rec)
        file.delete()
        mutableState.value = RecorderState(failed = true)
        return false
    }

    private fun release(rec: MediaRecorder) {
        try {
            rec.reset()
        } catch (e: RuntimeException) {
            // Already unusable; release below still frees the native object.
        }
        try {
            rec.release()
        } catch (e: RuntimeException) {
            // Nothing else to do.
        }
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    private const val SAMPLE_RATE = 44_100
    private const val BIT_RATE = 128_000
}

/** Stops a running recording, or starts one once RECORD_AUDIO is granted (asked through the host). */
internal fun toggleRecording(host: WidgetHostServices, context: Context) {
    if (RecorderEngine.state.value.recording) {
        RecorderEngine.stop()
        return
    }
    val permission = Manifest.permission.RECORD_AUDIO
    if (host.hasPermission(permission)) {
        RecorderEngine.start(context)
    } else {
        host.requestPermission(permission) { granted ->
            if (granted) runOnMain { RecorderEngine.start(context) }
        }
    }
}

object RecorderWidget : DotWidgetProvider {
    override val kind: String = "recorder"
    override val title: String = "Recorder"
    override val description: String = "Tap to record a voice memo. Stored on this device only."
    override val sizes: List<WidgetSize> = listOf(WidgetSize(2, 1), WidgetSize(2, 2))
    override val permissions: List<String> = listOf(Manifest.permission.RECORD_AUDIO)

    override fun shape(size: WidgetSize): WidgetShape = if (size.spanY <= 1) WidgetShape.PILL else WidgetShape.ROUNDED

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val host = LocalWidgetHost.current
        val context = LocalContext.current
        val app = remember(context) { context.applicationContext }
        val engine by RecorderEngine.state.collectAsState()
        val resumed = rememberIsResumed()
        val preview = instance.isPreview
        val recording = engine.recording && !preview
        val startedAt = engine.startedAt

        // 1 s clock aligned to the recording start, only while recording and visible.
        var nowTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(recording, resumed, startedAt) {
            if (!recording || !resumed) return@LaunchedEffect
            while (true) {
                val now = SystemClock.elapsedRealtime()
                nowTick = now
                delay(1000L - (now - startedAt) % 1000L + 5L)
            }
        }
        val seconds = if (recording) ((nowTick - startedAt) / 1000L).coerceAtLeast(0L) else 0L

        // Waveform history (index 0 = newest), sampled ~15 Hz only while recording and visible.
        var levels by remember { mutableStateOf(IDLE_LEVELS) }
        LaunchedEffect(recording, resumed) {
            if (!recording || !resumed) {
                levels = IDLE_LEVELS
                return@LaunchedEffect
            }
            var history = FloatArray(HISTORY) { 0.1f }
            levels = history
            while (true) {
                val next = FloatArray(HISTORY)
                System.arraycopy(history, 0, next, 1, HISTORY - 1)
                next[0] = (history[0] * 0.35f + RecorderEngine.sampleLevel() * 0.65f)
                history = next
                levels = next
                delay(SAMPLE_MILLIS)
            }
        }

        val pulse = rememberPulse(active = recording && resumed, periodMillis = 900, minAlpha = 0.3f)
        val onTap: () -> Unit = {
            if (!preview) toggleRecording(host, app)
        }
        val a11y = if (recording) "Recorder, recording. Tap to stop" else "Recorder. Tap to record"

        val tapped = modifier
            .semantics(mergeDescendants = true) { contentDescription = a11y }
            .pressClickable(pressedScale = 0.97f, onClick = onTap)

        if (instance.spanY <= 1) {
            RecorderPill(tapped, recording, seconds, pulse)
        } else {
            RecorderTile(tapped, recording, seconds, pulse) { levels }
        }
    }
}

@Composable
private fun RecorderPill(modifier: Modifier, recording: Boolean, seconds: Long, pulse: State<Float>) {
    val style = LocalWidgetStyle.current
    BoxWithConstraints(modifier) {
        val dotSize = (maxHeight * 0.34f).coerceIn(10.dp, 28.dp)
        val pad = (maxHeight * 0.26f).coerceIn(10.dp, 26.dp)
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = pad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(pad * 0.6f),
        ) {
            Box(
                Modifier
                    .size(dotSize)
                    .graphicsLayer { alpha = pulse.value }
                    .background(style.accent, CircleShape),
            )
            RecLabel(recording, seconds, Modifier.weight(1f).fillMaxHeight(0.6f), maxSize = 44f)
        }
    }
}

@Composable
private fun RecorderTile(
    modifier: Modifier,
    recording: Boolean,
    seconds: Long,
    pulse: State<Float>,
    levels: () -> FloatArray,
) {
    val style = LocalWidgetStyle.current
    Column(
        modifier.padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            drawWaveCircle(levels(), style.content)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.height(26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .graphicsLayer { alpha = pulse.value }
                    .background(style.accent, CircleShape),
            )
            RecLabel(recording, seconds, Modifier.fillMaxHeight(), maxSize = 24f)
        }
    }
}

@Composable
private fun RecLabel(recording: Boolean, seconds: Long, modifier: Modifier, maxSize: Float) {
    val style = LocalWidgetStyle.current
    if (recording) {
        FitText(
            text = formatClock(seconds),
            color = style.content,
            modifier = modifier,
            maxSize = maxSize,
            sizeTemplate = if (seconds >= 3600L) "0:00:00" else "00:00",
            alignment = Alignment.CenterStart,
        )
    } else {
        FitText(
            text = "REC",
            color = style.content,
            modifier = modifier,
            maxSize = maxSize,
            alignment = Alignment.CenterStart,
        )
    }
}

/** A disc of dot columns; column height follows the (mirrored) level history, clipped to the circle. */
private fun DrawScope.drawWaveCircle(levels: FloatArray, color: Color) {
    val d = min(size.width, size.height)
    val r = d / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val pitch = d / COLUMNS
    val dotRadius = pitch * 0.3f
    val mid = COLUMNS / 2
    for (i in 0 until COLUMNS) {
        val x = cx - r + pitch * (i + 0.5f)
        val dx = x - cx
        val chord = sqrt((r * r - dx * dx).coerceAtLeast(0f))
        val level = levels[abs(i - mid).coerceAtMost(levels.size - 1)]
        val half = (chord - dotRadius).coerceAtLeast(0f) * (0.12f + 0.88f * level.coerceIn(0f, 1f))
        val k = (half / pitch).toInt()
        for (j in -k..k) {
            drawCircle(color, dotRadius, Offset(x, cy + j * pitch))
        }
    }
}

private const val COLUMNS = 15
private const val HISTORY = 8
private const val SAMPLE_MILLIS = 66L
private val IDLE_LEVELS = FloatArray(HISTORY) { 1f }

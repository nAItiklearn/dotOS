package com.dotos.feature.widgets.dot

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.LocalReduceMotion
import com.dotos.core.designsystem.component.pressScale
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetHostServices
import com.dotos.feature.widgets.api.drawDotMatrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Process-level helpers shared by the batch B widgets.
// ---------------------------------------------------------------------------------------------

/**
 * Scope for fire-and-forget persistence that must outlive a composition (e.g. a note committed
 * while the page scrolls away). Runs on the main thread like a LaunchedEffect would.
 */
internal val BatchScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

internal fun runOnMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
}

/** Persist [value] under [key] without tying the write to a composition. */
internal fun WidgetHostServices.save(key: String, value: String) {
    BatchScope.launch { putState(key, value) }
}

/**
 * Observes a host-persisted string. `null` means "not loaded yet" (or no host); an unset key is
 * reported as the empty string, so callers can tell loading apart from empty.
 */
@Composable
internal fun rememberWidgetState(key: String): State<String?> {
    val host = LocalWidgetHost.current
    val flow = remember(host, key) { host.state(key).map { it ?: "" } }
    return flow.collectAsState<String, String?>(initial = null)
}

/** True while the host lifecycle is RESUMED (launcher in the foreground). */
@Composable
internal fun rememberIsResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

/** Alpha that pulses between 1 and [minAlpha] while [active]; constant 1 otherwise (and for reduce motion). */
@Composable
internal fun rememberPulse(active: Boolean, periodMillis: Int = 1000, minAlpha: Float = 0.25f): State<Float> {
    val still = remember { mutableFloatStateOf(1f) }
    if (!active || LocalReduceMotion.current) return still
    val transition = rememberInfiniteTransition(label = "pulse")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = minAlpha,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
}

// ---------------------------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------------------------

internal fun pad2(n: Int): String = if (n in 0..9) "0$n" else n.toString()

/** 65 -> "01:05", 3725 -> "1:02:05". Locale independent so the dot font always has the glyphs. */
internal fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = (s / 3600L).toInt()
    val m = ((s % 3600L) / 60L).toInt()
    val sec = (s % 60L).toInt()
    return if (h > 0) "$h:${pad2(m)}:${pad2(sec)}" else "${pad2(m)}:${pad2(sec)}"
}

/** 4170 -> "4,170". */
internal fun groupThousands(n: Int): String {
    val digits = n.coerceAtLeast(0).toString()
    if (digits.length <= 3) return digits
    val sb = StringBuilder(digits.length + digits.length / 3)
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.toString()
}

// ---------------------------------------------------------------------------------------------
// Vibration (timer done)
// ---------------------------------------------------------------------------------------------

internal fun vibrateDone(context: Context) {
    try {
        val vibrator: Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) vibratorApi31(context) else legacyVibrator(context)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 140L, 100L, 140L, 100L, 280L), -1))
    } catch (e: SecurityException) {
        // Vibration is a nicety; never let it break the widget.
    } catch (e: RuntimeException) {
        // Same as above (e.g. no vibrator service on this device).
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun vibratorApi31(context: Context): Vibrator? = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

@Suppress("DEPRECATION")
private fun legacyVibrator(context: Context): Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

// ---------------------------------------------------------------------------------------------
// Typography: text that always fits its box
// ---------------------------------------------------------------------------------------------

internal enum class TextRole { DISPLAY, TITLE, BODY, LABEL, MONO }

/**
 * Line box height as a fraction of the font size. Doto's capitals and digits are 0.7 em tall with no
 * descenders, so a tighter box makes the dots fill the space we measured for them (nothing is clipped:
 * overflow is Visible).
 */
private fun lineFactor(role: TextRole): Float = if (role == TextRole.DISPLAY) 0.85f else 1.0f

private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

@Composable
private fun roleStyle(role: TextRole): TextStyle {
    val type = DotTheme.type
    return when (role) {
        TextRole.DISPLAY -> type.display
        TextRole.TITLE -> type.title
        TextRole.BODY -> type.body
        TextRole.LABEL -> type.sectionLabel
        TextRole.MONO -> type.mono
    }
}

private fun fitFontSize(
    measurer: TextMeasurer,
    base: TextStyle,
    template: AnnotatedString,
    maxW: Float,
    maxH: Float,
    maxSize: Float,
    letterSpacing: TextUnit,
    lineFactor: Float,
): Float {
    val ref = 100f
    val probe = base.copy(
        fontSize = ref.sp,
        lineHeight = (ref * lineFactor).sp,
        letterSpacing = if (letterSpacing.isSpecified) letterSpacing else base.letterSpacing,
        lineHeightStyle = TrimBoth,
    )
    val result = measurer.measure(template, style = probe, softWrap = false, maxLines = 1)
    val w = result.size.width.coerceAtLeast(1).toFloat()
    val h = result.size.height.coerceAtLeast(1).toFloat()
    val scale = minOf(maxSize / ref, maxW / w, maxH / h)
    return (scale * ref * 0.97f).coerceAtLeast(5f)
}

/**
 * Single-line text that scales (up to [maxSize] sp) so [sizeTemplate] fits the available box, at any
 * font scale. Pass a fixed-width template for changing values ("00:00") so the size never jitters.
 */
@Composable
internal fun FitText(
    text: AnnotatedString,
    color: Color,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.DISPLAY,
    maxSize: Float = 64f,
    sizeTemplate: AnnotatedString = text,
    alignment: Alignment = Alignment.Center,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    val measurer = rememberTextMeasurer()
    val base = roleStyle(role)
    val factor = lineFactor(role)
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.MAX_VALUE
        val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else Float.MAX_VALUE
        val fontSize = remember(measurer, base, sizeTemplate, maxW, maxH, maxSize, letterSpacing, factor) {
            fitFontSize(measurer, base, sizeTemplate, maxW, maxH, maxSize, letterSpacing, factor)
        }
        Text(
            text = text,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            style = base.copy(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * factor).sp,
                letterSpacing = if (letterSpacing.isSpecified) letterSpacing else base.letterSpacing,
                lineHeightStyle = TrimBoth,
            ),
        )
    }
}

@Composable
internal fun FitText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.DISPLAY,
    maxSize: Float = 64f,
    sizeTemplate: String = text,
    alignment: Alignment = Alignment.Center,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    FitText(
        text = if (role == TextRole.DISPLAY) colonSafe(text) else AnnotatedString(text),
        color = color,
        modifier = modifier,
        role = role,
        maxSize = maxSize,
        sizeTemplate = if (role == TextRole.DISPLAY) colonSafe(sizeTemplate) else AnnotatedString(sizeTemplate),
        alignment = alignment,
        letterSpacing = letterSpacing,
    )
}

// ---------------------------------------------------------------------------------------------
// Interaction
// ---------------------------------------------------------------------------------------------

/** Click with the launcher's press-scale feel and no ripple (the widget surface is clipped by the host). */
internal fun Modifier.pressClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    pressedScale: Float = 0.94f,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    Modifier
        .pressScale(interaction, pressedScale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
}

/** Round button with a 48 dp minimum touch target. */
@Composable
internal fun RoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    size: Dp = 48.dp,
    label: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LocalWidgetStyle.current
    Box(
        modifier
            .size(size.coerceAtLeast(48.dp))
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .semantics { if (label != null) contentDescription = label }
            .pressClickable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(if (filled) style.content else Color.Transparent)
            .border(1.dp, if (filled) style.content else style.muted.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Small capsule text button (visible height 34 dp, 48 dp touch target). */
@Composable
internal fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val style = LocalWidgetStyle.current
    Box(
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .pressClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(34.dp)
                .clip(CircleShape)
                .background(if (filled) style.content else Color.Transparent)
                .border(1.dp, if (filled) style.content else style.muted.copy(alpha = 0.55f), CircleShape)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                maxLines = 1,
                color = if (filled) style.background.copy(alpha = 1f) else style.content,
                style = DotTheme.type.sectionLabel,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dot glyphs (own artwork, 'X' = lit)
// ---------------------------------------------------------------------------------------------

internal object Glyphs {
    val PLAY = listOf(
        ".X.....",
        ".XXX...",
        ".XXXXX.",
        ".XXXXXX",
        ".XXXXX.",
        ".XXX...",
        ".X.....",
    )
    val PAUSE = listOf(
        ".XX.XX.",
        ".XX.XX.",
        ".XX.XX.",
        ".XX.XX.",
        ".XX.XX.",
        ".XX.XX.",
        ".XX.XX.",
    )
    val NEXT = listOf(
        "X.....X",
        "XX....X",
        "XXX...X",
        "XXXX..X",
        "XXX...X",
        "XX....X",
        "X.....X",
    )
    val PREV = listOf(
        "X.....X",
        "X....XX",
        "X...XXX",
        "X..XXXX",
        "X...XXX",
        "X....XX",
        "X.....X",
    )
    val PLUS = listOf(
        "...X...",
        "...X...",
        "...X...",
        "XXXXXXX",
        "...X...",
        "...X...",
        "...X...",
    )
    val MINUS = listOf(
        ".......",
        ".......",
        ".......",
        "XXXXXXX",
        ".......",
        ".......",
        ".......",
    )
    val MIC = listOf(
        "..XXX..",
        "..XXX..",
        "X.XXX.X",
        "X.XXX.X",
        "X.XXX.X",
        ".XXXXX.",
        "...X...",
        "..XXX..",
    )
    val NOTE = listOf(
        "...XXXXXX",
        "...XXXXXX",
        "...X....X",
        "...X....X",
        "...X....X",
        "...X....X",
        ".XXX..XXX",
        "XXXX.XXXX",
        ".XX...XX.",
    )
    val CHECK = listOf(
        "......X",
        ".....XX",
        "X...XX.",
        "XX.XX..",
        ".XXX...",
        "..X....",
    )
}

/** Draws [rows] scaled to fit the box, centred. */
@Composable
internal fun DotGlyph(
    rows: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    offColor: Color = Color.Transparent,
) {
    val cols = remember(rows) { rows.maxOf { it.length } }
    Canvas(modifier) {
        val pitch = min(size.width / cols, size.height / rows.size)
        val left = (size.width - pitch * cols) / 2f
        val top = (size.height - pitch * rows.size) / 2f
        drawDotMatrix(rows, color, offColor, Offset(left, top), pitch, 0.42f)
    }
}

/**
 * Doto's colon renders as two stacked glyph fragments at large sizes. Draw ':' with the bold mono
 * face instead, which sits on the digit centre line and keeps the dot-matrix look.
 */
private fun colonSafe(text: String): AnnotatedString {
    if (':' !in text) return AnnotatedString(text)
    return androidx.compose.ui.text.buildAnnotatedString {
        for (c in text) {
            if (c == ':') {
                pushStyle(
                    androidx.compose.ui.text.SpanStyle(
                        fontFamily = com.dotos.core.designsystem.DotFonts.Mono,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    ),
                )
                append(c)
                pop()
            } else {
                append(c)
            }
        }
    }
}

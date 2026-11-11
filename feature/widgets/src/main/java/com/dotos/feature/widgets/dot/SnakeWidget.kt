package com.dotos.feature.widgets.dot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetSize
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private const val SNAKE_N = 14
private const val SNAKE_TICK_MS = 143L
private const val GLYPH_SIZE = 6

private enum class SnakeStatus { IDLE, PLAYING, PAUSED, OVER }

private enum class SnakeStep { MOVED, ATE, DIED }

/** Plain game model on an n x n torus (walls wrap). Mutated only from the main thread. */
private class SnakeGame(private val n: Int) {
    private val cap = n * n
    private val ring = IntArray(cap)
    private val occupied = BooleanArray(cap)
    private var tail = 0
    private var length = 0
    private var dx = 1
    private var dy = 0
    private var nextDx = 1
    private var nextDy = 0
    private var food = -1

    var score = 0
        private set

    init {
        reset()
    }

    fun reset() {
        occupied.fill(false)
        tail = 0
        length = 0
        score = 0
        dx = 1
        dy = 0
        nextDx = 1
        nextDy = 0
        val row = n / 2
        for (i in 0 until 3) push(row * n + 3 + i)
        food = randomFreeCell()
    }

    /** Queue a turn; reversing straight into yourself is ignored. */
    fun turn(tx: Int, ty: Int) {
        if (tx == -dx && ty == -dy) return
        nextDx = tx
        nextDy = ty
    }

    fun step(): SnakeStep {
        dx = nextDx
        dy = nextDy
        val head = ring[(tail + length - 1) % cap]
        val cell = ((head / n + dy + n) % n) * n + (head % n + dx + n) % n
        val eating = cell == food
        val tailCell = ring[tail]
        if (occupied[cell] && !(cell == tailCell && !eating)) return SnakeStep.DIED
        if (!eating) popTail()
        push(cell)
        if (!eating) return SnakeStep.MOVED
        score++
        if (length >= cap) {
            food = -1
            return SnakeStep.DIED
        }
        food = randomFreeCell()
        return SnakeStep.ATE
    }

    /** 0 empty, 1 body, 2 head, 3 food. */
    fun frame(): ByteArray {
        val out = ByteArray(cap)
        for (i in 0 until length) out[ring[(tail + i) % cap]] = 1
        if (length > 0) out[ring[(tail + length - 1) % cap]] = 2
        if (food >= 0) out[food] = 3
        return out
    }

    private fun push(cell: Int) {
        ring[(tail + length) % cap] = cell
        length++
        occupied[cell] = true
    }

    private fun popTail() {
        occupied[ring[tail]] = false
        tail = (tail + 1) % cap
        length--
    }

    private fun randomFreeCell(): Int {
        val free = cap - length
        if (free <= 0) return -1
        var pick = Random.nextInt(free)
        for (i in 0 until cap) {
            if (!occupied[i]) {
                if (pick == 0) return i
                pick--
            }
        }
        return -1
    }
}

private val GlyphPlay = listOf("X.....", "XXX...", "XXXXX.", "XXXXX.", "XXX...", "X.....")
private val GlyphPause = listOf("XX..XX", "XX..XX", "XX..XX", "XX..XX", "XX..XX", "XX..XX")
private val GlyphOver = listOf("X....X", ".X..X.", "..XX..", "..XX..", ".X..X.", "X....X")

object SnakeWidget : DotWidgetProvider {
    override val kind: String = "snake"
    override val title: String = "Snake"
    override val description: String = "Dot-matrix Snake. Tap to play or pause, swipe the widget to steer."
    override val sizes: List<WidgetSize> = listOf(WidgetSize(2, 2))

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val host = LocalWidgetHost.current
        val haptics = rememberDotHaptics()
        val resumed = rememberIsResumed()
        val preview = instance.isPreview
        val thresholdPx = with(LocalDensity.current) { 14.dp.toPx() }

        val game = remember { SnakeGame(SNAKE_N) }
        var status by remember { mutableStateOf(SnakeStatus.IDLE) }
        var frame by remember { mutableStateOf(game.frame()) }
        var score by remember { mutableIntStateOf(0) }
        val bestKey = "${instance.stateKey}:snake_best"
        val persistedBest by rememberWidgetState(bestKey)
        var bestLocal by remember { mutableIntStateOf(0) }
        val best = max(bestLocal, persistedBest?.toIntOrNull() ?: 0)

        // Game tick (~7 Hz): only while playing and the launcher is visible.
        LaunchedEffect(status, resumed) {
            if (status != SnakeStatus.PLAYING) return@LaunchedEffect
            if (!resumed) {
                status = SnakeStatus.PAUSED
                return@LaunchedEffect
            }
            while (true) {
                delay(SNAKE_TICK_MS)
                when (game.step()) {
                    SnakeStep.ATE -> {
                        score = game.score
                        haptics.tick()
                    }
                    SnakeStep.DIED -> {
                        frame = game.frame()
                        score = game.score
                        status = SnakeStatus.OVER
                        haptics.reject()
                        val previousBest = max(bestLocal, persistedBest?.toIntOrNull() ?: 0)
                        if (game.score > previousBest) {
                            bestLocal = game.score
                            host.save(bestKey, game.score.toString())
                        }
                        return@LaunchedEffect
                    }
                    SnakeStep.MOVED -> Unit
                }
                frame = game.frame()
            }
        }

        val onTap: () -> Unit = {
            if (!preview) {
                when (status) {
                    SnakeStatus.PLAYING -> status = SnakeStatus.PAUSED
                    SnakeStatus.PAUSED -> status = SnakeStatus.PLAYING
                    SnakeStatus.IDLE, SnakeStatus.OVER -> {
                        game.reset()
                        frame = game.frame()
                        score = 0
                        status = SnakeStatus.PLAYING
                    }
                }
                haptics.confirm()
            }
        }
        val currentTap = rememberUpdatedState(onTap)
        val playing = status == SnakeStatus.PLAYING

        val a11y = when (status) {
            SnakeStatus.IDLE -> "Snake. Tap to play"
            SnakeStatus.PLAYING -> "Snake, playing. Score $score. Swipe to steer, tap to pause"
            SnakeStatus.PAUSED -> "Snake, paused. Score $score. Tap to resume"
            SnakeStatus.OVER -> "Snake, game over. Score $score. Tap to play again"
        }

        // The drag detector exists only while playing, so page swipes still work otherwise. Consuming the
        // changes keeps the launcher pager from moving mid-game.
        val root = modifier
            .semantics(mergeDescendants = true) { contentDescription = a11y }
            .pointerInput(preview) {
                if (!preview) detectTapGestures(onTap = { currentTap.value() })
            }
            .pointerInput(playing) {
                if (!playing) return@pointerInput
                var ax = 0f
                var ay = 0f
                detectDragGestures(
                    onDragStart = {
                        ax = 0f
                        ay = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        ax += amount.x
                        ay += amount.y
                        if (abs(ax) > thresholdPx || abs(ay) > thresholdPx) {
                            if (abs(ax) > abs(ay)) {
                                game.turn(if (ax > 0f) 1 else -1, 0)
                            } else {
                                game.turn(0, if (ay > 0f) 1 else -1)
                            }
                            ax = 0f
                            ay = 0f
                        }
                    },
                )
            }

        Column(root.padding(14.dp)) {
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                FitText(
                    text = score.toString(),
                    color = style.content,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    maxSize = 26f,
                    sizeTemplate = "00",
                    alignment = Alignment.CenterStart,
                )
                FitText(
                    text = "BEST $best",
                    color = style.muted,
                    modifier = Modifier.fillMaxHeight(),
                    role = TextRole.LABEL,
                    maxSize = 10f,
                    alignment = Alignment.CenterEnd,
                )
            }
            Spacer(Modifier.height(6.dp))
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val overlay = when (status) {
                    SnakeStatus.PLAYING -> null
                    SnakeStatus.PAUSED -> GlyphPause
                    SnakeStatus.OVER -> GlyphOver
                    SnakeStatus.IDLE -> GlyphPlay
                }
                drawSnakeBoard(frame, SNAKE_N, style.content, style.track, style.accent, overlay)
            }
        }
    }
}

/** One dot per cell. When [overlay] is set the board is dimmed and a 6x6 dot glyph sits in the middle. */
private fun DrawScope.drawSnakeBoard(
    frame: ByteArray,
    n: Int,
    content: Color,
    track: Color,
    accent: Color,
    overlay: List<String>?,
) {
    val pitch = min(size.width, size.height) / n
    val ox = (size.width - pitch * n) / 2f
    val oy = (size.height - pitch * n) / 2f
    val dim = if (overlay == null) 1f else 0.3f
    val start = (n - GLYPH_SIZE) / 2
    val bodyColor = content.copy(alpha = dim)
    val foodColor = accent.copy(alpha = dim)
    for (i in 0 until n * n) {
        val col = i % n
        val row = i / n
        val center = Offset(ox + (col + 0.5f) * pitch, oy + (row + 0.5f) * pitch)
        if (overlay != null && col in start until start + GLYPH_SIZE && row in start until start + GLYPH_SIZE) {
            val lit = overlay[row - start][col - start] == 'X'
            drawCircle(if (lit) content else track, if (lit) pitch * 0.42f else pitch * 0.13f, center)
            continue
        }
        when (frame[i].toInt()) {
            1 -> drawCircle(bodyColor, pitch * 0.34f, center)
            2 -> drawCircle(bodyColor, pitch * 0.44f, center)
            3 -> drawCircle(foodColor, pitch * 0.36f, center)
            else -> drawCircle(track, pitch * 0.13f, center)
        }
    }
}

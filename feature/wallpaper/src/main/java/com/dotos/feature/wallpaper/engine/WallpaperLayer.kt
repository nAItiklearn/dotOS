package com.dotos.feature.wallpaper.engine

import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dotos.core.data.settings.AnimationSpeed
import com.dotos.core.data.settings.WallpaperConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws the configured wallpaper full-bleed.
 *
 * Performance: time lives in a float state read only in the draw lambda, so animating never
 * recomposes anything. Frames run only while the host lifecycle is RESUMED and are capped at [maxFps].
 * The shader path is wrapped so a compile/bind error silently falls back to Canvas.
 *
 * @param scroll page scroll 0..1 (read in draw).
 * @param blurPx extra blur (drawer), API 31+ only (read in layer block).
 * @param dim 0..1 black overlay (read in draw).
 */
@Composable
fun WallpaperLayer(
    config: WallpaperConfig,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    maxFps: Int = 60,
    scroll: () -> Float = { 0f },
    blurPx: () -> Float = { 0f },
    dim: () -> Float = { 0f },
    staticTime: Float = 18f,
    allowGlass: Boolean = true,
) {
    val resolved = remember(config) { config.resolve() }
    val wallpaper = resolved.wallpaper
    val density = LocalDensity.current.density
    val frame = remember(wallpaper) { WallpaperFrame() }
    var time by remember { mutableFloatStateOf(staticTime) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Shader is created once per wallpaper. Any failure → null → Canvas fallback.
    var shaderBroken by remember(wallpaper) { mutableStateOf(false) }
    val shader: RuntimeShader? = remember(wallpaper) {
        if (Build.VERSION.SDK_INT >= 33) {
            wallpaper.shaderSource?.let { src -> runCatching { RuntimeShader(src) }.getOrNull() }
        } else {
            null
        }
    }
    val brush = remember(shader) { if (Build.VERSION.SDK_INT >= 33 && shader != null) shaderBrush(shader) else null }

    val speed = config.animation.multiplier
    val powerSave = rememberPowerSaveMode()
    val battery = rememberBattery()
    val shouldAnimate = animate && wallpaper.animated && config.animation != AnimationSpeed.OFF &&
        !(config.pauseInBatterySaver && powerSave) && !(config.animateOnlyWhileCharging && !battery.second)

    val lifecycleOwner = LocalLifecycleOwner.current
    if (shouldAnimate) {
        LaunchedEffect(lifecycleOwner, speed, maxFps) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val minFrameNanos = 1_000_000_000L / max(1, maxFps) - 2_000_000L
                var last = 0L
                var lastFrame = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (last == 0L) last = now
                        if (now - lastFrame >= minFrameNanos) {
                            val dt = (now - last) / 1_000_000_000f
                            time += dt.coerceAtMost(0.1f) * speed
                            lastFrame = now
                        }
                        last = now
                    }
                }
            }
        }
    }

    // User photo wallpaper.
    val imagePath = config.imagePath
    val image: ImageBitmap? = if (config.id == "image" && imagePath != null) {
        val path: String = imagePath
        produceState<ImageBitmap?>(null, path, size) {
            if (size.width == 0) return@produceState
            value = withContext(Dispatchers.IO) { decodeSampled(path, size.width, size.height) }
        }.value
    } else {
        null
    }

    val glass: RenderEffect? = remember(config.glass, config.glassStripes, size, allowGlass) {
        if (allowGlass && config.glass && Build.VERSION.SDK_INT >= 33 && size.width > 0) {
            GlassEffect.create(size.width.toFloat(), size.height.toFloat(), config.glassStripes)
        } else {
            null
        }
    }

    Box(modifier.fillMaxSize().onSizeChanged { size = it }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= 31) renderEffect = combinedEffect(blurPx(), glass)
                    clip = true
                },
        ) {
            val t = time // state read in draw → redraw only
            if (image != null) {
                drawPhoto(image)
            } else {
                frame.update(this, resolved, t, scroll(), density, battery)
                val sh = shader
                val br = brush
                var drawn = false
                if (sh != null && br != null && !shaderBroken && Build.VERSION.SDK_INT >= 33) {
                    drawn = runCatching {
                        wallpaper.bindUniforms(sh, frame)
                        drawRect(br)
                    }.isSuccess
                    if (!drawn) shaderBroken = true
                }
                if (!drawn) with(wallpaper) { drawFallback(frame) }
            }
            val d = dim()
            if (d > 0.001f) drawRect(Color.Black.copy(alpha = d.coerceIn(0f, 1f)))
        }
    }
}

@RequiresApi(33)
private fun shaderBrush(shader: RuntimeShader): ShaderBrush = ShaderBrush(shader)

@RequiresApi(31)
private fun combinedEffect(blur: Float, glass: RenderEffect?): androidx.compose.ui.graphics.RenderEffect? {
    val effect: RenderEffect? = when {
        blur > 0.5f && glass != null -> RenderEffect.createChainEffect(
            RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP),
            glass,
        )
        blur > 0.5f -> RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
        else -> glass
    }
    return effect?.asComposeRenderEffect()
}

/** Home reports touches here so interactive wallpapers (Pulse) can react. Lock-free, main thread. */
object WallpaperTouch {
    @Volatile internal var x = -1f
    @Volatile internal var y = -1f
    @Volatile internal var at = 0L

    fun report(px: Float, py: Float) {
        x = px
        y = py
        at = System.nanoTime()
    }
}

private fun WallpaperFrame.update(
    scope: DrawScope,
    resolved: ResolvedWallpaper,
    t: Float,
    scroll: Float,
    density: Float,
    battery: Pair<Float, Boolean>,
) {
    time = t
    width = scope.size.width
    height = scope.size.height
    this.scroll = scroll
    this.density = density
    this.battery = battery.first
    this.charging = battery.second
    touchX = WallpaperTouch.x
    touchY = WallpaperTouch.y
    touchAge = if (WallpaperTouch.at == 0L) 1000f else ((System.nanoTime() - WallpaperTouch.at) / 1_000_000_000f)
    colorMap = resolved.colors
    paramMap = resolved.params
    val now = System.currentTimeMillis()
    if (now - hourStamp > 60_000L) {
        hourStamp = now
        val cal = Calendar.getInstance()
        hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
    }
    layoutKey = 31 * (31 * width.roundToInt() + height.roundToInt()) + resolved.params.hashCode() * 17 + resolved.colors.hashCode()
}

private fun DrawScope.drawPhoto(image: ImageBitmap) {
    val scale = max(size.width / image.width, size.height / image.height)
    val w = (image.width * scale).roundToInt()
    val h = (image.height * scale).roundToInt()
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
        dstSize = IntSize(w, h),
    )
}

private fun decodeSampled(path: String, reqW: Int, reqH: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

@Composable
private fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val pm = remember(context) { context.getSystemService(PowerManager::class.java) }
    var saver by remember { mutableStateOf(pm?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: Intent) {
                saver = pm?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return saver
}

/** (level 0..1, charging). Sticky broadcast: no polling. */
@Composable
fun rememberBattery(): Pair<Float, Boolean> {
    val context = LocalContext.current
    var state by remember { mutableStateOf(1f to false) }
    DisposableEffect(context) {
        fun parse(i: Intent?) {
            if (i == null) return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (level >= 0 && scale > 0) state = (level.toFloat() / scale) to charging
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: Intent) = parse(i)
        }
        parse(
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            ),
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

/** Same per-frame update for the live wallpaper service (no Compose state involved). */
fun WallpaperFrame.updateForService(scope: DrawScope, resolved: ResolvedWallpaper, t: Float, scroll: Float, density: Float) =
    update(scope, resolved, t, scroll, density, 1f to false)

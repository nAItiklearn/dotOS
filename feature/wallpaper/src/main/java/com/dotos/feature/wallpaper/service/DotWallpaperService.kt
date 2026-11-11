package com.dotos.feature.wallpaper.service

import android.graphics.RuntimeShader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.dotos.core.data.settings.AnimationSpeed
import com.dotos.core.data.settings.WallpaperConfig
import com.dotos.feature.wallpaper.engine.ResolvedWallpaper
import com.dotos.feature.wallpaper.engine.WallpaperFrame
import com.dotos.feature.wallpaper.engine.resolve
import com.dotos.feature.wallpaper.engine.updateForService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * System-wide live wallpaper running the same engine as the in-launcher layer. Draws with a hardware
 * canvas on the Choreographer, at most 30 fps (wallpapers are calm), and only while visible.
 */
class DotWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = DotEngine()

    private inner class DotEngine : Engine(), Choreographer.FrameCallback {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var job: Job? = null
        private var config: WallpaperConfig = WallpaperConfig()
        private var resolved: ResolvedWallpaper = config.resolve()
        private val frame = WallpaperFrame()
        private var shader: RuntimeShader? = null
        private var brush: ShaderBrush? = null
        private var visible = false
        private var time = 18f
        private var lastNanos = 0L
        private var xOffset = 0f
        private val drawScope = CanvasDrawScope()
        private val handler = Handler(Looper.getMainLooper())

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            val source = applicationContext as? WallpaperConfigSource
            job = scope.launch {
                source?.wallpaperConfig?.collect { c ->
                    config = c
                    resolved = c.resolve()
                    buildShader()
                    drawFrame()
                }
            }
            buildShader()
        }

        private fun buildShader() {
            shader = null
            brush = null
            if (Build.VERSION.SDK_INT >= 33) createShader()
        }

        @RequiresApi(33)
        private fun createShader() {
            val src = resolved.wallpaper.shaderSource ?: return
            val s = runCatching { RuntimeShader(src) }.getOrNull() ?: return
            shader = s
            brush = ShaderBrush(s)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            lastNanos = 0L
            if (isVisible) Choreographer.getInstance().postFrameCallback(this) else Choreographer.getInstance().removeFrameCallback(this)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPx: Int, yPx: Int) {
            this.xOffset = xOffset
            if (!animating()) drawFrame()
        }

        private fun animating() = resolved.wallpaper.animated && config.animation != AnimationSpeed.OFF

        override fun doFrame(frameTimeNanos: Long) {
            if (!visible) return
            if (lastNanos != 0L) {
                val dt = ((frameTimeNanos - lastNanos) / 1e9f).coerceAtMost(0.1f)
                time += dt * config.animation.multiplier
            }
            lastNanos = frameTimeNanos
            drawFrame()
            if (animating()) {
                // ~30 fps cap.
                handler.postDelayed({ if (visible) Choreographer.getInstance().postFrameCallback(this) }, 28L)
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            val surface = holder.surface ?: return
            if (!surface.isValid) return
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                val w = canvas.width.toFloat()
                val h = canvas.height.toFloat()
                val density = Density(resources.displayMetrics.density)
                drawScope.draw(density, LayoutDirection.Ltr, Canvas(canvas), Size(w, h)) {
                    frame.updateForService(this, resolved, time, if (config.scrollParallax) xOffset else 0f, density.density)
                    val sh = shader
                    val br = brush
                    var drawn = false
                    if (sh != null && br != null && Build.VERSION.SDK_INT >= 33) {
                        drawn = runCatching {
                            resolved.wallpaper.bindUniforms(sh, frame)
                            drawRect(br)
                        }.isSuccess
                        if (!drawn) {
                            shader = null
                            brush = null
                        }
                    }
                    if (!drawn) with(resolved.wallpaper) { drawFallback(frame) }
                }
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        override fun onDestroy() {
            Choreographer.getInstance().removeFrameCallback(this)
            handler.removeCallbacksAndMessages(null)
            scope.cancel()
            super.onDestroy()
        }
    }
}

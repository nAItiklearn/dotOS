package com.dotos.feature.icons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotos.core.data.settings.IconStyle
import com.dotos.core.data.settings.PlateShape
import com.dotos.core.launcher.AppInfo
import com.dotos.feature.icons.engine.IconSpec
import com.dotos.feature.icons.engine.LocalIconEngine
import com.dotos.feature.icons.engine.LocalIconSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Renders an app icon in the user's current icon style (plate + glyph), [size] = plate diameter.
 * Loading is async and cached; a soft placeholder plate shows until the glyph is ready, then the
 * glyph fades in over 120 ms. Cached icons show in the first frame with no fade.
 */
@Composable
fun AppIcon(app: AppInfo, modifier: Modifier = Modifier, size: Dp = 56.dp, spec: IconSpec = LocalIconSpec.current) {
    val engine = LocalIconEngine.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val initial = remember(app, spec, px) { engine?.cached(app, spec, px) }
    var bitmap by remember(app, spec, px) { mutableStateOf(initial) }
    val alpha = remember(app, spec, px) { Animatable(if (initial != null) 1f else 0f) }
    LaunchedEffect(app, spec, px) {
        if (bitmap == null && engine != null) {
            bitmap = engine.render(app, spec, px)
            alpha.animateTo(1f, tween(120))
        }
    }
    val drawPlate = spec.style != IconStyle.ORIGINAL && spec.plateShape != PlateShape.NONE
    Box(
        modifier
            .size(size)
            .then(if (drawPlate) Modifier.drawBehind { drawPlate(spec.plateShape, spec.plateColor) } else Modifier),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value },
            )
        }
    }
}

/** Plate drawing shared with folders and previews. */
fun DrawScope.drawPlate(shape: PlateShape, color: Color) {
    when (shape) {
        PlateShape.NONE -> Unit
        PlateShape.CIRCLE -> drawCircle(color)
        PlateShape.ROUNDED -> drawRoundRect(color, cornerRadius = CornerRadius(size.minDimension * 0.30f))
        PlateShape.SQUIRCLE -> drawPath(squircle(size.width, size.height), color)
    }
}

private val squircleCache = HashMap<Long, Path>()

private fun squircle(w: Float, h: Float): Path {
    val key = (w.toBits().toLong() shl 32) or h.toBits().toLong()
    return squircleCache.getOrPut(key) {
        val n = 4.6f
        val p = Path()
        val steps = 72
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * 2f * PI.toFloat()
            val c = cos(t)
            val s = sin(t)
            val x = w / 2f + (w / 2f) * sign(c) * abs(c).pow(2f / n)
            val y = h / 2f + (h / 2f) * sign(s) * abs(s).pow(2f / n)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        p
    }
}

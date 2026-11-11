package com.dotos.feature.widgets.dot

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotFonts
import com.dotos.core.designsystem.LocalReduceMotion
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.LocalWidgetHost
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetFace
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetSize
import com.dotos.feature.widgets.api.WidgetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * A user-picked photo, centre-cropped to the widget (the launcher clips the corners). The image is
 * decoded off the main thread at roughly the widget's pixel size; the picked URI is stored with
 * the host under "<stateKey>:photo". Faces: colour or mono (greyscale).
 */
object PhotoWidget : DotWidgetProvider {
    const val FACE_COLOR = "color"
    const val FACE_MONO = "mono"

    override val kind = "photo"
    override val title = "Photo"
    override val description = "Shows a photo of your choice."
    override val sizes = listOf(WidgetSize(2, 2), WidgetSize(4, 2), WidgetSize(4, 4))
    override val faces = listOf(
        WidgetFace(FACE_COLOR, "Colour", "The photo as it is"),
        WidgetFace(FACE_MONO, "Mono", "The photo in black and white"),
    )

    @Composable
    override fun Content(instance: WidgetInstance, modifier: Modifier) {
        val style = LocalWidgetStyle.current
        val context = LocalContext.current
        val host = LocalWidgetHost.current
        val scope = rememberCoroutineScope()
        val reduceMotion = LocalReduceMotion.current
        val preview = instance.isPreview
        val key = instance.stateKey + ":photo"
        val flow: Flow<String?> = remember(host, key, preview) { if (preview) emptyFlow() else host.state(key) }
        val saved by flow.collectAsState(initial = null)
        val mono = instance.face == FACE_MONO
        val filter = remember(mono) {
            if (mono) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
        }
        val fade = remember { Animatable(0f) }

        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .baTap(enabled = !preview) {
                    host.pickImage { uri ->
                        if (uri != null) scope.launch { host.putState(key, uri.toString()) }
                    }
                },
        ) {
            val targetW = constraints.maxWidth
            val targetH = constraints.maxHeight
            val bitmap by produceState<ImageBitmap?>(null, saved, targetW, targetH) {
                val uri = saved
                value = if (uri.isNullOrBlank() || targetW <= 0 || targetH <= 0) {
                    null
                } else {
                    withContext(Dispatchers.IO) { decodePhoto(context, Uri.parse(uri), targetW, targetH) }
                }
            }
            val image = bitmap
            LaunchedEffect(image != null, reduceMotion) {
                if (image == null) return@LaunchedEffect
                if (reduceMotion) fade.snapTo(1f) else fade.animateTo(1f, tween(220))
            }
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "Photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fade.value },
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    colorFilter = filter,
                )
            } else {
                PhotoEmptyState(style, Modifier.fillMaxSize())
            }
        }
    }
}

/** Decodes [uri] near [targetW] x [targetH] (never smaller, so the centre crop stays sharp). */
private fun decodePhoto(context: Context, uri: Uri, targetW: Int, targetH: Int): ImageBitmap? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val sample = max(1, min(info.size.width / max(targetW, 1), info.size.height / max(targetH, 1)))
        decoder.setTargetSampleSize(sample)
    }.asImageBitmap()
}.getOrNull()

private val PHOTO_STROKE = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Picture glyph in a 24 x 24 box: frame and mountains (the sun is a filled dot drawn separately). */
private val PHOTO_GLYPH = Path().apply {
    addRoundRect(RoundRect(Rect(3f, 4f, 21f, 20f), CornerRadius(2.5f)))
    moveTo(3f, 17f)
    lineTo(9f, 12f)
    lineTo(14f, 16.5f)
    lineTo(17f, 14f)
    lineTo(21f, 17.5f)
}

@Composable
private fun PhotoEmptyState(style: WidgetStyle, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Box(
        modifier
            .semantics { contentDescription = BaStrings.TAP_ADD_PHOTO }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val u = min(w / BA_REF_2X2_W_DP.dp.toPx(), h / BA_REF_2X2_H_DP.dp.toPx()).coerceIn(0.7f, 1.3f)
                val inset = 10.dp.toPx()
                val corner = (style.radius.toPx() - inset).coerceAtLeast(6.dp.toPx())
                val dotGap = 11.dp.toPx() / style.dotDensity.coerceAtLeast(0.25f)
                val outline = Stroke(
                    width = 3.5f.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, dotGap), 0f),
                )
                val outlineColor = style.muted.copy(alpha = 0.6f)

                val label = baText(
                    measurer, BaStrings.TAP_ADD_PHOTO, 13.dp.toPx() * u, DotFonts.Grotesk, FontWeight.Normal,
                    maxWidth = (w - 2f * (inset + 14.dp.toPx())).coerceAtLeast(1f),
                )
                val glyph = 30.dp.toPx() * u
                val gap = 10.dp.toPx() * u
                val groupH = glyph + gap + label.px * 0.7f
                val glyphTop = (h - groupH) / 2f
                val glyphLeft = (w - glyph) / 2f
                val labelCy = glyphTop + glyph + gap + label.px * 0.35f
                val unit = glyph / 24f
                onDrawBehind {
                    drawRoundRect(
                        color = outlineColor,
                        topLeft = Offset(inset, inset),
                        size = Size(w - 2f * inset, h - 2f * inset),
                        cornerRadius = CornerRadius(corner),
                        style = outline,
                    )
                    withTransform({
                        translate(glyphLeft, glyphTop)
                        scale(unit, unit, Offset.Zero)
                    }) {
                        drawPath(PHOTO_GLYPH, style.muted, style = PHOTO_STROKE)
                        drawCircle(style.muted, 1.6f, Offset(9f, 9.5f))
                    }
                    baDrawText(label, style.muted, w / 2f, labelCy, capEm = 0.6f)
                }
            },
    )
}

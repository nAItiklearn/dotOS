package com.dotos.feature.home.widgets

import android.appwidget.AppWidgetHostView
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dotos.core.designsystem.DotTheme
import kotlin.math.roundToInt

/**
 * Renders a bound third-party widget, clipped to [cornerRadius]. The widget is told its real size in dp
 * (and re-told whenever the composable is resized) so responsive widgets pick the right layout.
 *
 * The widget's own pixels are untouched: DotOS can only clip and place them. If the provider is gone or
 * the host view cannot be created, a quiet "Widget unavailable" placeholder is shown instead.
 */
@Composable
fun AppWidgetHostItem(
    controller: AppWidgetController,
    appWidgetId: Int,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(cornerRadius)

    val hostView: AppWidgetHostView? = remember(controller, appWidgetId) {
        val info = controller.info(appWidgetId)
        if (info == null) {
            null
        } else {
            runCatching {
                controller.host.createView(context.applicationContext, appWidgetId, info).also {
                    it.setPadding(0, 0, 0, 0)
                }
            }.getOrNull()
        }
    }

    var widthDp by remember { mutableFloatStateOf(0f) }
    var heightDp by remember { mutableFloatStateOf(0f) }
    // Last size reported to the provider, so unchanged sizes never cost a binder call.
    val lastPushed = remember(hostView) { IntArray(2) { -1 } }

    Box(
        modifier
            .clip(shape)
            .onSizeChanged { size ->
                widthDp = size.width / density
                heightDp = size.height / density
            },
    ) {
        if (hostView == null) {
            WidgetUnavailable()
        } else {
            val view: AppWidgetHostView = hostView
            AndroidView(
                factory = { view },
                modifier = Modifier.fillMaxSize(),
                update = { hostViewToUpdate ->
                    // Reading the size states here makes the update block re-run on resize without
                    // recomposing the item.
                    val w = widthDp.roundToInt()
                    val h = heightDp.roundToInt()
                    if (w > 0 && h > 0 && (lastPushed[0] != w || lastPushed[1] != h)) {
                        lastPushed[0] = w
                        lastPushed[1] = h
                        hostViewToUpdate.pushSize(w.toFloat(), h.toFloat())
                    }
                },
            )
        }
    }
}

@Composable
private fun WidgetUnavailable() {
    val colors = DotTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.plate),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = WidgetPickerStrings.WIDGET_UNAVAILABLE,
            style = DotTheme.type.bodySmall,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/** Tells the provider how much room it really has. Sizes are dp, as the platform expects. */
@Suppress("DEPRECATION")
private fun AppWidgetHostView.pushSize(widthDp: Float, heightDp: Float) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
        } else {
            val w = widthDp.roundToInt()
            val h = heightDp.roundToInt()
            updateAppWidgetSize(null, w, h, w, h)
        }
    }
}

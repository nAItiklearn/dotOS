package com.dotos.feature.drawer.library

import android.graphics.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.pressScale
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.core.launcher.AppInfo
import com.dotos.feature.icons.AppIcon
import kotlin.math.roundToInt

/** Library surfaces sit on top of the live wallpaper, so they are translucent plates in both themes. */
internal object LibraryColors {
    val Plate = Color(0xE61B1B1B)
    val Hairline = Color(0x1FFFFFFF)
    val Scrim = Color(0xB3000000)
}

/**
 * User-visible strings. The drawer module has no resources of its own yet; keeping them in one place
 * makes the later move to `strings.xml` mechanical.
 */
internal object LibraryText {
    const val SEARCH_PLACEHOLDER = "App Library"
    const val CLEAR_SEARCH = "Clear search"
    const val NO_RESULTS = "No apps found"
    const val CLOSE = "Close"
    const val OPEN_PREFIX = "Open "
}

internal object LibraryDims {
    val PageHorizontal = 20.dp
    val GridGap = 16.dp
    val TileRadius = 28.dp
    val TilePadding = 14.dp
    val TileGap = 10.dp
    val MiniGap = 4.dp
    val SearchHeight = 52.dp
    val DefaultBottomPadding = 96.dp
    val TileShape = RoundedCornerShape(TileRadius)
}

/** Tracks an icon's window bounds without triggering recomposition; read only when the icon is tapped. */
internal class BoundsHolder {
    var coordinates: LayoutCoordinates? = null

    fun toRect(): Rect? {
        val c = coordinates ?: return null
        if (!c.isAttached) return null
        val b = c.boundsInWindow()
        return Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
    }
}

/**
 * A tappable app: icon (plus optional label). Tap launches with the icon's window bounds for the launch
 * animation; long-press picks it up with a haptic tick. [labelColor] != null shows the label under the icon.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppTarget(
    app: AppInfo,
    iconSize: Dp,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color? = null,
) {
    val bounds = remember { BoundsHolder() }
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberDotHaptics()
    val semanticsModifier = if (labelColor == null) {
        Modifier.semantics { contentDescription = app.label }
    } else {
        Modifier
    }
    Column(
        modifier
            .pressScale(interaction)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClick = {
                    haptics.pick()
                    onLongPress(app)
                },
                onClick = { onLaunch(app, bounds.toRect()) },
            )
            .then(semanticsModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        AppIcon(
            app = app,
            modifier = Modifier.onGloballyPositioned { bounds.coordinates = it },
            size = iconSize,
        )
        if (labelColor != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                style = DotTheme.type.iconLabel,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

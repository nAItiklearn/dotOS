package com.dotos.feature.home.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotos.core.data.settings.BadgeStyle
import com.dotos.core.designsystem.DotTheme
import com.dotos.feature.home.layout.HomeItem
import com.dotos.feature.icons.AppIcon
import com.dotos.feature.icons.drawPlate
import com.dotos.feature.icons.engine.LocalIconSpec
import com.dotos.feature.widgets.DotWidgets
import com.dotos.feature.widgets.api.LocalWidgetStyle
import com.dotos.feature.widgets.api.WidgetInstance
import com.dotos.feature.widgets.api.WidgetShape
import com.dotos.feature.widgets.api.WidgetSize

/** App icon with optional label and notification badge. [plate] = plate diameter. */
@Composable
internal fun AppTile(
    item: HomeItem,
    plate: Dp,
    showLabel: Boolean,
    labelColor: Color,
    badgeCount: Int,
    badgeStyle: BadgeStyle,
    pressed: Boolean,
    modifier: Modifier = Modifier,
) {
    val app = item.app ?: return
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            AppIcon(app, size = plate)
            Badge(badgeCount, badgeStyle, plate, Modifier.align(Alignment.TopEnd))
        }
        if (showLabel) {
            Text(
                item.entity.title ?: app.label,
                style = DotTheme.type.iconLabel,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun Badge(count: Int, style: BadgeStyle, plate: Dp, modifier: Modifier = Modifier) {
    if (count <= 0 || style == BadgeStyle.OFF) return
    val colors = DotTheme.colors
    if (style == BadgeStyle.DOT) {
        // Small near-white dot at 45°, like the reference.
        Box(
            modifier
                .offset(x = -plate * 0.06f, y = plate * 0.06f)
                .size(plate * 0.14f)
                .clip(CircleShape)
                .background(Color(0xFFF2F2F2))
                .border(1.5.dp, Color(0xFF111111), CircleShape),
        )
    } else {
        Box(
            modifier
                .offset(x = plate * 0.04f, y = -plate * 0.02f)
                .clip(RoundedCornerShape(50))
                .background(colors.accent)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(if (count > 99) "99+" else count.toString(), style = DotTheme.type.iconLabel, color = Color.White)
        }
    }
}

/** Folder preview: a plate holding a 2x2 cluster of mini icons. */
@Composable
internal fun FolderTile(
    item: HomeItem,
    plate: Dp,
    showLabel: Boolean,
    labelColor: Color,
    pressed: Boolean,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val spec = LocalIconSpec.current
    val scale by animateFloatAsState(if (pressed) 0.94f else if (highlighted) 1.08f else 1f, label = "folder")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(plate)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .drawBehind { drawPlate(spec.plateShape, Color(0xFF2D2D2D)) },
            contentAlignment = Alignment.Center,
        ) {
            val mini = plate * 0.36f
            val kids = item.children.take(4)
            Column(Modifier.size(mini * 2 + plate * 0.04f)) {
                for (row in 0 until 2) {
                    androidx.compose.foundation.layout.Row(Modifier.weight(1f)) {
                        for (col in 0 until 2) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                kids.getOrNull(row * 2 + col)?.app?.let { AppIcon(it, size = mini) }
                            }
                        }
                    }
                }
            }
        }
        if (showLabel) {
            Text(
                item.entity.title ?: "Folder",
                style = DotTheme.type.iconLabel,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Built-in widget on its surface. The launcher owns the surface (fill, radius, hairline) so every
 * widget looks consistent; the provider draws only content.
 */
@Composable
internal fun DotWidgetTile(item: HomeItem, safeMode: Boolean, modifier: Modifier = Modifier) {
    val provider = DotWidgets[item.entity.widgetKind]
    val style = LocalWidgetStyle.current
    val size = WidgetSize(item.entity.spanX, item.entity.spanY)
    val shapeKind = provider?.shape(size) ?: WidgetShape.ROUNDED
    BoxWithConstraints(modifier) {
        val shape: Shape = when (shapeKind) {
            WidgetShape.CIRCLE -> CircleShape
            WidgetShape.PILL -> RoundedCornerShape(50)
            WidgetShape.ROUNDED -> RoundedCornerShape(minOf(maxWidth, maxHeight) * 0.13f)
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(style.background)
                .border(1.dp, Color(0x14FFFFFF), shape),
        ) {
            val config = item.config
            val instance = remember(item.entity) {
                WidgetInstance(
                    itemId = item.id,
                    kind = item.entity.widgetKind.orEmpty(),
                    face = config["face"] ?: provider?.faces?.firstOrNull()?.id ?: "default",
                    spanX = item.entity.spanX,
                    spanY = item.entity.spanY,
                    config = config,
                )
            }
            if (provider == null || safeMode) {
                Text(
                    if (safeMode) "Safe mode" else "Widget unavailable",
                    style = DotTheme.type.bodySmall,
                    color = style.muted,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                CompositionLocalProvider(LocalWidgetStyle provides style) {
                    provider.Content(instance, Modifier.fillMaxSize())
                }
            }
        }
    }
}

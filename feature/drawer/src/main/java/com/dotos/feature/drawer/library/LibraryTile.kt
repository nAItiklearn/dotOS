package com.dotos.feature.drawer.library

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.pressScale
import com.dotos.core.launcher.AppInfo
import com.dotos.feature.icons.AppIcon

/** With exactly this many apps all four quadrants are large icons; with more, the 4th becomes a mini-cluster. */
private const val FULL_TILE_APPS = 4

/** Quadrants 0..2 always hold a large icon (when there are enough apps). */
private const val LARGE_SLOTS = 3

/**
 * Square category tile (dark plate, 28 dp radius, hairline) with the iOS App Library 2x2 layout:
 * three large icons plus either the 4th app (exactly 4 apps) or a 2x2 mini-cluster of the next four.
 * Tapping the plate or the cluster opens the category; tapping a large icon launches it.
 * The title sits under the tile in muted small caps.
 */
@Composable
internal fun CategoryTile(
    group: LibraryGroup,
    contentColor: Color,
    onOpen: () -> Unit,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pressScale(interaction)
                .clip(LibraryDims.TileShape)
                .background(LibraryColors.Plate, LibraryDims.TileShape)
                .border(1.dp, LibraryColors.Hairline, LibraryDims.TileShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = LibraryText.OPEN_PREFIX + group.title,
                    onClick = onOpen,
                )
                .semantics { contentDescription = group.title },
        ) {
            val cell = (maxWidth - LibraryDims.TilePadding * 2 - LibraryDims.TileGap) / 2
            Column(
                Modifier.padding(LibraryDims.TilePadding),
                verticalArrangement = Arrangement.spacedBy(LibraryDims.TileGap),
            ) {
                for (row in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(LibraryDims.TileGap)) {
                        for (col in 0..1) {
                            TileSlot(
                                apps = group.apps,
                                index = row * 2 + col,
                                cell = cell,
                                onLaunch = onLaunch,
                                onLongPress = onLongPress,
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = group.title.uppercase(),
            style = DotTheme.type.sectionLabel,
            color = contentColor.copy(alpha = 0.62f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
    }
}

@Composable
private fun TileSlot(
    apps: List<AppInfo>,
    index: Int,
    cell: Dp,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    val count = apps.size
    when {
        index < LARGE_SLOTS && index < count -> {
            val app = apps[index]
            key(app.key) { AppTarget(app, cell, onLaunch, onLongPress) }
        }
        index == LARGE_SLOTS && count == FULL_TILE_APPS -> {
            val app = apps[index]
            key(app.key) { AppTarget(app, cell, onLaunch, onLongPress) }
        }
        index == LARGE_SLOTS && count > FULL_TILE_APPS -> MiniCluster(apps, LARGE_SLOTS, cell)
        else -> Spacer(Modifier.size(cell))
    }
}

/** 2x2 cluster of small icons for apps `[start, start + 4)`; not individually tappable (the tile opens the category). */
@Composable
private fun MiniCluster(apps: List<AppInfo>, start: Int, cell: Dp) {
    val mini = (cell - LibraryDims.MiniGap) / 2
    Column(Modifier.size(cell), verticalArrangement = Arrangement.spacedBy(LibraryDims.MiniGap)) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(LibraryDims.MiniGap)) {
                for (col in 0..1) {
                    val app = apps.getOrNull(start + row * 2 + col)
                    if (app != null) {
                        key(app.key) { AppIcon(app, Modifier, mini) }
                    } else {
                        Spacer(Modifier.size(mini))
                    }
                }
            }
        }
    }
}

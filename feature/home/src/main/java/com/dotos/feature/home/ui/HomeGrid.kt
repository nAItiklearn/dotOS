package com.dotos.feature.home.ui

import android.graphics.Rect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dotos.core.data.db.ItemType
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.designsystem.component.launcherGestures
import com.dotos.feature.home.layout.HomeItem
import com.dotos.feature.home.widgets.AppWidgetController
import com.dotos.feature.home.widgets.AppWidgetHostItem
import kotlin.math.roundToInt

/** Callbacks shared by grid, dock and folder items. */
interface ItemCallbacks {
    fun tap(item: HomeItem, bounds: Rect?)
    fun longPress(item: HomeItem, source: DragSource, bounds: Rect, pointer: Offset, w: Float, h: Float)
    fun drag(delta: Offset)
    fun dragEnd()
    fun dragCancel()
}

internal fun LayoutCoordinates.rootRect(): Rect? {
    if (!isAttached) return null
    val b = boundsInRoot()
    return Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
}

/** One home page: absolutely positioned items on the grid. */
@Composable
internal fun HomeGridPage(
    items: List<HomeItem>,
    metrics: GridMetrics,
    settings: LauncherSettings,
    labelColor: Color,
    badges: Map<String, Int>,
    drag: HomeDragState,
    safeMode: Boolean,
    appWidgets: AppWidgetController,
    callbacks: ItemCallbacks,
) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        for (item in items) {
            key(item.id) {
                val e = item.entity
                val r = metrics.itemRect(e.cellX, e.cellY, e.spanX, e.spanY)
                val hidden = drag.session?.item?.id == item.id
                val isSingle = e.spanX == 1 && e.spanY == 1 &&
                    (e.type == ItemType.APP || e.type == ItemType.FOLDER || e.type == ItemType.SHORTCUT)
                val w = with(density) { r.width.toDp() }
                val h = with(density) { r.height.toDp() }
                Box(
                    Modifier
                        .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                        .size(w, h)
                        .graphicsLayer { alpha = if (hidden) 0f else 1f },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSingle) {
                        val plate = if (settings.showLabels) w * 0.84f else w
                        ItemCell(item, DragSource.HOME, plate, settings, labelColor, badges, drag, callbacks)
                    } else {
                        WidgetCell(item, metrics, safeMode, appWidgets, callbacks)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ItemCell(
    item: HomeItem,
    source: DragSource,
    plate: Dp,
    settings: LauncherSettings,
    labelColor: Color,
    badges: Map<String, Int>,
    drag: HomeDragState,
    callbacks: ItemCallbacks,
    showLabel: Boolean = settings.showLabels,
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val folderHighlight = drag.session != null && item.isFolder
    Box(
        Modifier
            .onGloballyPositioned { coords = it }
            .semantics { contentDescription = item.app?.label ?: item.entity.title ?: "Folder" }
            .launcherGestures(
                key = item.id,
                onPress = { pressed = it },
                onTap = { callbacks.tap(item, coords?.rootRect()) },
                onLongPress = { local ->
                    val c = coords
                    val rect = c?.rootRect()
                    if (c != null && rect != null) {
                        val px = with(density) { plate.toPx() }
                        callbacks.longPress(item, source, rect, c.localToRoot(local), px, px)
                    }
                },
                onDrag = callbacks::drag,
                onDragEnd = callbacks::dragEnd,
                onDragCancel = callbacks::dragCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (item.isFolder) {
            FolderTile(item, plate, showLabel, labelColor, pressed, folderHighlight)
        } else {
            val count = item.app?.packageName?.let { badges[it] } ?: 0
            AppTile(item, plate, showLabel, labelColor, count, settings.badgeStyle, pressed)
        }
    }
}

@Composable
private fun WidgetCell(item: HomeItem, metrics: GridMetrics, safeMode: Boolean, appWidgets: AppWidgetController, callbacks: ItemCallbacks) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords = it }
            .launcherGestures(
                key = item.id,
                onTap = {},
                onLongPress = { local ->
                    val c = coords
                    val rect = c?.rootRect()
                    if (c != null && rect != null) {
                        callbacks.longPress(item, DragSource.HOME, rect, c.localToRoot(local), c.size.width.toFloat(), c.size.height.toFloat())
                    }
                },
                onDrag = callbacks::drag,
                onDragEnd = callbacks::dragEnd,
                onDragCancel = callbacks::dragCancel,
            ),
    ) {
        if (item.entity.type == ItemType.APP_WIDGET) {
            AppWidgetHostItem(appWidgets, item.entity.appWidgetId, cornerRadius = with(density) { (metrics.pitch * 0.2f).toDp() })
        } else {
            DotWidgetTile(item, safeMode, Modifier.fillMaxSize())
        }
    }
}

/** The dock: evenly spaced slots, same plate size as the grid. No backing (like the reference). */
@Composable
internal fun DockRow(
    items: List<HomeItem>,
    slots: Int,
    plate: Float?,
    settings: LauncherSettings,
    badges: Map<String, Int>,
    drag: HomeDragState,
    callbacks: ItemCallbacks,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
    // Same plate as the grid, unless 5-6 slots need it smaller to keep breathing room.
    val slotW = maxWidth / slots
    val gridPlate: Dp = plate?.let { with(density) { it.toDp() } } ?: 64.dp
    val plateDp: Dp = minOf(gridPlate, slotW * 0.84f)
    Row(
        Modifier.fillMaxWidth().height(plateDp + 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until slots) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val item = items.getOrNull(i)
                if (item != null) {
                    key(item.id) {
                        val hidden = drag.session?.item?.id == item.id
                        Box(Modifier.graphicsLayer { alpha = if (hidden) 0f else 1f }) {
                            ItemCell(item, DragSource.DOCK, plateDp, settings, Color.White, badges, drag, callbacks, showLabel = false)
                        }
                    }
                } else if (drag.session != null) {
                    Box(Modifier.size(plateDp).graphicsLayer { alpha = 0.35f }.drawDashedCircle())
                }
            }
        }
    }
}
}

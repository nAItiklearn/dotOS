package com.dotos.feature.home.ui

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dotos.core.data.db.ItemType
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.DotRoundAction
import com.dotos.core.launcher.AppInfo
import com.dotos.core.launcher.AppShortcut
import com.dotos.core.launcher.AppsRepository
import com.dotos.feature.home.layout.HomeItem
import com.dotos.feature.home.widgets.AppWidgetController
import com.dotos.feature.icons.AppIcon
import com.dotos.feature.widgets.DotWidgets
import com.dotos.feature.widgets.api.WidgetSize
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal fun Modifier.drawDashedCircle(): Modifier = drawBehind {
    drawCircle(
        Color.White,
        radius = size.minDimension / 2f - 1.dp.toPx(),
        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))),
    )
}

/** Remove / App info / Uninstall targets shown at the top while dragging. */
@Composable
internal fun DropTargetsBar(drag: HomeDragState) {
    val s = drag.session
    val visible = s != null && (s.item != null || s.app != null)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val zones = buildList {
                if (s?.item != null) add(DropZone.REMOVE to "Remove")
                if (s?.app != null) add(DropZone.INFO to "App info")
                if (s?.app != null) add(DropZone.UNINSTALL to "Uninstall")
            }
            zones.forEach { (zone, label) ->
                val active = drag.hoverZone == zone
                val danger = zone == DropZone.UNINSTALL
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (active) (if (danger) Color(0xFFD71921) else Color.White) else Color(0xE61A1A1A))
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = DotTheme.type.bodySmall, color = if (active && !danger) Color.Black else Color.White)
                }
            }
        }
    }
}

/** The item following the finger during drag. */
@Composable
internal fun DragGhost(drag: HomeDragState, settings: LauncherSettings, safeMode: Boolean, appWidgets: AppWidgetController) {
    val s = drag.session ?: return
    val density = LocalDensity.current
    val w = with(density) { s.width.toDp() }
    val h = with(density) { s.height.toDp() }
    Box(
        Modifier
            .offset {
                val p = drag.ghostTopLeft()
                IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
            .size(w, h)
            .graphicsLayer {
                scaleX = 1.06f
                scaleY = 1.06f
                alpha = 0.96f
            },
        contentAlignment = Alignment.Center,
    ) {
        val item = s.item
        val app = s.app
        when {
            item != null && item.isFolder -> FolderTile(item, w, false, Color.White, pressed = false, highlighted = false)
            item != null && item.entity.type == ItemType.DOT_WIDGET -> DotWidgetTile(item, safeMode, Modifier.fillMaxSize())
            item != null && item.entity.type == ItemType.APP_WIDGET -> Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color(0xE61A1A1A)),
            )
            app != null -> AppIcon(app, size = w)
        }
    }
}

/** Long-press menu: shortcuts + actions, positioned above (or below) the anchor. */
@Composable
internal fun ItemMenu(
    drag: HomeDragState,
    apps: AppsRepository,
    onLaunchShortcut: () -> Unit,
    onRemove: (HomeItem) -> Unit,
    onHide: (AppInfo) -> Unit,
    onAddToHome: (AppInfo) -> Unit,
    onCustomize: (AppInfo) -> Unit,
    onResize: (HomeItem, Int, Int) -> Unit,
    onLaunch: (AppInfo, Rect?) -> Unit,
) {
    val target = drag.menu ?: return
    if (drag.session != null) return
    val colors = DotTheme.colors
    val density = LocalDensity.current
    val app = target.app
    val item = target.item
    val fromDrawer = target.source == DragSource.DRAWER || target.source == DragSource.LIBRARY
    val shortcuts by produceState<List<AppShortcut>>(emptyList(), app?.key) {
        value = if (app != null) apps.shortcuts.forApp(app) else emptyList()
    }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { drag.menu = null },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val menuW = 248.dp
            val maxWpx = with(density) { maxWidth.toPx() }
            val maxHpx = with(density) { maxHeight.toPx() }
            val margin = with(density) { 12.dp.toPx() }
            val gap = with(density) { 10.dp.toPx() }
            val mw = with(density) { menuW.toPx() }
            val anchor = target.anchor
            val x = (anchor.center.x - mw / 2f).coerceIn(margin, (maxWpx - mw - margin).coerceAtLeast(margin))
            val above = anchor.top > maxHpx * 0.45f
            var menuH by remember { mutableIntStateOf(0) }
            val y = (if (above) anchor.top - menuH - gap else anchor.bottom + gap).coerceIn(0f, (maxHpx - menuH).coerceAtLeast(0f))
            Column(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .width(menuW)
                    .onSizeChanged { menuH = it.height }
                    .graphicsLayer { alpha = if (menuH == 0) 0f else 1f }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xF51A1A1A))
                    .border(1.dp, colors.hairline, RoundedCornerShape(24.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(vertical = 8.dp),
            ) {
                shortcuts.forEach { sc ->
                    MenuRow(sc.shortLabel) {
                        apps.shortcuts.start(sc)
                        drag.menu = null
                        onLaunchShortcut()
                    }
                }
                if (shortcuts.isNotEmpty()) Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
                if (item != null && item.entity.type == ItemType.DOT_WIDGET) {
                    val current = WidgetSize(item.entity.spanX, item.entity.spanY)
                    DotWidgets[item.entity.widgetKind]?.sizes?.filter { it != current }?.forEach { size ->
                        MenuRow("Resize to $size") {
                            onResize(item, size.spanX, size.spanY)
                            drag.menu = null
                        }
                    }
                }
                if (app != null && fromDrawer) MenuRow("Add to home") { onAddToHome(app); drag.menu = null }
                if (app != null) {
                    MenuRow("App info") { apps.openAppInfo(app); drag.menu = null }
                    MenuRow("Change icon") { onCustomize(app); drag.menu = null }
                }
                if (item != null) MenuRow("Remove") { onRemove(item); drag.menu = null }
                if (app != null && fromDrawer) MenuRow("Hide") { onHide(app); drag.menu = null }
                if (app != null && !apps.isSystemApp(app)) {
                    MenuRow("Uninstall", danger = true) { apps.uninstall(app); drag.menu = null }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = DotTheme.type.body,
        color = if (danger) Color(0xFFD71921) else DotTheme.colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
    )
}

/** Folder popup: rounded card with editable title and a 4-column grid. */
@Composable
internal fun FolderOverlay(
    folder: HomeItem?,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    callbacks: ItemCallbacks,
    badges: Map<String, Int>,
    settings: LauncherSettings,
) {
    AnimatedVisibility(visible = folder != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val f = folder
            if (f != null) {
                var title by remember(f.id) { mutableStateOf(f.entity.title ?: "Folder") }
                LaunchedEffect(title) {
                    delay(400)
                    if (title.isNotBlank() && title.trim() != f.entity.title) onRename(title.trim())
                }
                val localDrag = remember { HomeDragState() }
                Column(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFA141414))
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(32.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(20.dp),
                ) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it.take(32) },
                        singleLine = true,
                        textStyle = DotTheme.type.dotTitle.copy(color = Color.White, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(Color(0xFFD71921)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    )
                    val cols = 4
                    f.children.chunked(cols).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            row.forEach { child ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    ItemCell(child, DragSource.FOLDER, 56.dp, settings, Color.White, badges, localDrag, callbacks, showLabel = true)
                                }
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** Long-press on empty home: quick actions sheet. */
@Composable
internal fun EditSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onWallpaper: () -> Unit,
    onWidgets: () -> Unit,
    onIcons: () -> Unit,
    onLayout: () -> Unit,
    onSettings: () -> Unit,
    onAddPage: () -> Unit,
    canDeletePage: Boolean,
    onDeletePage: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .padding(12.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFA101010))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(32.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(vertical = 24.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    DotRoundAction("Wallpaper", onWallpaper, size = 56.dp) { ActionGlyph(0) }
                    DotRoundAction("Widgets", onWidgets, size = 56.dp) { ActionGlyph(1) }
                    DotRoundAction("Icons", onIcons, size = 56.dp) { ActionGlyph(2) }
                    DotRoundAction("Layout", onLayout, size = 56.dp) { ActionGlyph(3) }
                    DotRoundAction("Settings", onSettings, size = 56.dp) { ActionGlyph(4) }
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Add page", onAddPage)
                    if (canDeletePage) PillButton("Delete page", onDeletePage)
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) { Text(label, style = DotTheme.type.bodySmall, color = Color.White) }
}

/** Minimal original glyphs for the edit actions (wallpaper, widgets, icons, layout, settings). */
@Composable
private fun ActionGlyph(kind: Int) {
    val c = DotTheme.colors.onSurface
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        val st = Stroke(s * 0.09f)
        when (kind) {
            0 -> {
                drawRoundRect(c, topLeft = Offset(s * 0.12f, s * 0.12f), size = Size(s * 0.76f, s * 0.76f), cornerRadius = CornerRadius(s * 0.16f), style = st)
                drawCircle(c, s * 0.09f, Offset(s * 0.62f, s * 0.38f))
            }
            1 -> {
                val g = s * 0.36f
                for (i in 0..1) for (j in 0..1) {
                    drawRoundRect(
                        c,
                        topLeft = Offset(s * 0.1f + i * (g + s * 0.08f), s * 0.1f + j * (g + s * 0.08f)),
                        size = Size(g, g),
                        cornerRadius = CornerRadius(s * 0.1f),
                        style = st,
                    )
                }
            }
            2 -> for (i in 0..2) for (j in 0..2) drawCircle(c, s * 0.08f, Offset(s * (0.2f + i * 0.3f), s * (0.2f + j * 0.3f)))
            3 -> {
                drawLine(c, Offset(s * 0.36f, s * 0.12f), Offset(s * 0.36f, s * 0.88f), st.width)
                drawLine(c, Offset(s * 0.64f, s * 0.12f), Offset(s * 0.64f, s * 0.88f), st.width)
                drawLine(c, Offset(s * 0.12f, s * 0.36f), Offset(s * 0.88f, s * 0.36f), st.width)
                drawLine(c, Offset(s * 0.12f, s * 0.64f), Offset(s * 0.88f, s * 0.64f), st.width)
            }
            else -> {
                drawCircle(c, s * 0.34f, Offset(s / 2, s / 2), style = st)
                drawCircle(c, s * 0.1f, Offset(s / 2, s / 2))
            }
        }
    }
}

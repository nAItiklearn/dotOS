package com.dotos.feature.home

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotos.core.data.db.Container
import com.dotos.core.data.db.ItemType
import com.dotos.core.data.settings.GestureAction
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.DotPageIndicator
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.core.launcher.AppInfo
import com.dotos.core.launcher.ProfileKind
import com.dotos.feature.drawer.AppDrawer
import com.dotos.feature.drawer.DrawerActions
import com.dotos.feature.drawer.DrawerConfig
import com.dotos.feature.drawer.DrawerController
import com.dotos.feature.drawer.library.AppLibraryPage
import com.dotos.feature.home.layout.HomeItem
import com.dotos.feature.home.layout.HomeLayout
import com.dotos.feature.home.layout.buildLayout
import com.dotos.feature.home.ui.DragSession
import com.dotos.feature.home.ui.DragSource
import com.dotos.feature.home.ui.DropZone
import com.dotos.feature.home.ui.EditSheet
import com.dotos.feature.home.ui.FolderOverlay
import com.dotos.feature.home.ui.GridMetrics
import com.dotos.feature.home.ui.HomeDragState
import com.dotos.feature.home.ui.HomeGridPage
import com.dotos.feature.home.ui.HomeWidgetServices
import com.dotos.feature.home.ui.ItemMenu
import com.dotos.feature.home.ui.MenuTarget
import com.dotos.feature.home.ui.SystemActions
import com.dotos.feature.home.ui.DockRow
import com.dotos.feature.home.ui.DragGhost
import com.dotos.feature.home.ui.DropTargetsBar
import com.dotos.feature.home.widgets.AppWidgetController
import com.dotos.feature.home.widgets.WidgetPickerSheet
import com.dotos.feature.home.widgets.rememberAppWidgetBinder
import com.dotos.feature.home.widgets.spanFor
import com.dotos.feature.icons.engine.LocalIconEngine
import com.dotos.feature.icons.engine.LocalIconSpec
import com.dotos.feature.icons.engine.toIconSpec
import com.dotos.feature.wallpaper.engine.WallpaperLayer
import com.dotos.feature.wallpaper.engine.resolve
import com.dotos.feature.widgets.DotWidgets
import com.dotos.feature.widgets.api.LocalWidgetHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The home surface: live wallpaper, paged grid (+ App Library as the last page), dock, search pill,
 * drawer overlay, folders, menus, drag & drop and the edit sheet.
 */
@Composable
fun HomeScreen(deps: HomeDeps, settings: LauncherSettings, appWidgets: AppWidgetController) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberDotHaptics()

    // ------------------------------------------------------------------ data
    val allApps by deps.apps.apps.collectAsStateWithLifecycle()
    val quiet by deps.apps.quietProfiles.collectAsStateWithLifecycle()
    val rawItems by deps.layout.items.collectAsStateWithLifecycle()
    val hidden by deps.database.appMeta().observeHidden().collectAsStateWithLifecycle(initialValue = emptyList())
    val overrides by deps.database.appMeta().observeOverrides().collectAsStateWithLifecycle(initialValue = emptyList())
    val usage by deps.database.appMeta().observeUsage().collectAsStateWithLifecycle(initialValue = emptyList())
    val badges by deps.badges.collectAsStateWithLifecycle()

    val apps = allApps.orEmpty()
    val hiddenKeys = remember(hidden) { hidden.map { it.componentKey }.toHashSet() }
    val labelOverrides = remember(overrides) { overrides.filter { it.label != null }.associate { it.componentKey to it.label!! } }
    LaunchedEffect(overrides) {
        deps.icons.setOverrides(overrides.filter { it.icon != null }.associate { it.componentKey to it.icon!! })
    }
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val visibleApps = remember(apps, hiddenKeys) { apps.filter { it.key !in hiddenKeys } }
    val layout: HomeLayout = remember(rawItems, appsByKey, settings.pageCount) {
        rawItems?.let { buildLayout(it, appsByKey, settings.pageCount) } ?: HomeLayout.Empty
    }
    val recent = remember(usage, appsByKey) {
        usage.sortedByDescending { it.lastLaunched }.take(8).mapNotNull { appsByKey[it.componentKey] }
    }

    // First run seed + reflow on grid changes.
    LaunchedEffect(rawItems == null, allApps == null) {
        val items = rawItems
        val list = allApps
        if (items != null && items.isEmpty() && list != null && list.isNotEmpty()) {
            deps.layout.seedIfEmpty(list, deps.apps.mainUserSerial, settings.gridColumns, DotWidgets.all.map { it.kind }.toSet())
        }
    }
    LaunchedEffect(settings.gridColumns, settings.gridRows) {
        if (rawItems != null) {
            val pages = deps.layout.reflow(settings.gridColumns, settings.gridRows, settings.pageCount)
            if (pages > settings.pageCount) deps.settings.update { it.copy(pageCount = pages) }
        }
    }

    // ------------------------------------------------------------------ state
    val drawer = remember { DrawerController(scope) }
    val drag = remember { HomeDragState() }
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerPage by remember { mutableStateOf(0) }
    val homePages = layout.pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = settings.defaultPage.coerceIn(0, homePages - 1)) { homePages + 1 }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    var metrics by remember { mutableStateOf<GridMetrics?>(null) }
    var dockBounds by remember { mutableStateOf(ComposeRect.Zero) }
    val dropAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    val resolvedWallpaper = remember(settings.wallpaper) { settings.wallpaper.resolve() }
    val lightWallpaper = !settings.useSystemWallpaper && resolvedWallpaper.isLight
    val contentColor = if (lightWallpaper) Color(0xFF111111) else Color.White

    // Status bar icons follow the wallpaper; drawer is dark, so light icons there.
    val drawerOpenState by remember { derivedStateOf { drawer.progress.value > 0.5f } }
    LaunchedEffect(lightWallpaper, drawerOpenState) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightWallpaper && !drawerOpenState
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = lightWallpaper && !drawerOpenState
    }
    // System wallpaper mode: let the window show it; engine mode: our layer covers it (no double draw).
    LaunchedEffect(settings.useSystemWallpaper) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        if (settings.useSystemWallpaper) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        }
    }

    // ------------------------------------------------------------------ actions
    fun launch(app: AppInfo, bounds: Rect?) {
        val opts: Bundle? = bounds?.let {
            ActivityOptions.makeClipRevealAnimation(view, it.left, it.top, it.width(), it.height()).toBundle()
        }
        deps.apps.launch(app, bounds, opts)
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            deps.database.appMeta().ensureUsage(app.key)
            deps.database.appMeta().bumpUsage(app.key, now)
        }
    }

    fun closeAll(): Boolean {
        var any = false
        if (drag.menu != null) { drag.menu = null; any = true }
        if (openFolderId != null) { openFolderId = null; any = true }
        if (pickerOpen) { pickerOpen = false; any = true }
        if (editMode) { editMode = false; any = true }
        if (drawer.progress.targetValue > 0f) { drawer.close(); any = true }
        return any
    }

    fun perform(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.OPEN_DRAWER -> drawer.open()
            GestureAction.OPEN_SEARCH -> drawer.open(focusSearch = true)
            GestureAction.NOTIFICATIONS -> SystemActions.expandNotifications(context)
            GestureAction.QUICK_SETTINGS -> SystemActions.expandQuickSettings(context)
            GestureAction.LOCK_SCREEN -> if (!deps.lockScreen()) {
                Toast.makeText(context, "Enable DotOS in Accessibility to lock with a gesture", Toast.LENGTH_LONG).show()
            }
            GestureAction.EDIT_MODE -> if (!settings.lockLayout) editMode = true
            GestureAction.DEFAULT_PAGE -> scope.launch { pagerState.animateScrollToPage(settings.defaultPage.coerceIn(0, homePages - 1)) }
            GestureAction.LAUNCHER_SETTINGS -> deps.openSettings(null)
        }
    }

    LaunchedEffect(deps) {
        deps.homePresses.collect {
            if (!closeAll()) perform(settings.gestures.homePress)
        }
    }
    BackHandler(enabled = drag.menu != null || openFolderId != null || editMode || pickerOpen || drawerOpenState) { closeAll() }

    // ------------------------------------------------------------------ activity results
    var pendingPermission by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermission?.invoke(granted)
        pendingPermission = null
    }
    var pendingPhoto by remember { mutableStateOf<((Uri?) -> Unit)?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val cb = pendingPhoto
        pendingPhoto = null
        if (uri == null || cb == null) {
            cb?.invoke(null)
        } else {
            scope.launch {
                val local = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(context.filesDir, "widget_photos").apply { mkdirs() }
                        val out = File(dir, "p_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                        Uri.fromFile(out)
                    }.getOrNull()
                }
                cb(local)
            }
        }
    }
    val widgetServices = remember(deps) {
        HomeWidgetServices(
            context = context.applicationContext,
            stateDao = deps.database.widgetState(),
            layout = deps.layout,
            launchPermission = { perm, cb ->
                pendingPermission = cb
                permissionLauncher.launch(perm)
            },
            launchPhotoPicker = { cb ->
                pendingPhoto = cb
                photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onOpenSearch = { drawer.open(focusSearch = true) },
            onOpenOptions = { pickerOpen = true },
            notificationAccess = { deps.hasNotificationAccess() },
            onRequestNotificationAccess = { deps.requestNotificationAccess() },
            notificationListener = deps.notificationListener,
        )
    }

    fun placeAppWidget(appWidgetId: Int, spanX: Int, spanY: Int) {
        val m = metrics ?: return
        val page = pickerPage.coerceIn(0, homePages - 1)
        val all = rawItems.orEmpty()
        val grid = deps.layout.occupancy(all, page, m.cols, m.rows)
        val pos = deps.layout.nearestFree(grid, spanX, spanY)
        scope.launch {
            if (pos != null) {
                deps.layout.addAppWidget(appWidgetId, page, pos.first, pos.second, spanX, spanY)
            } else {
                deps.settings.update { it.copy(pageCount = homePages + 1) }
                deps.layout.addAppWidget(appWidgetId, homePages, 0, 0, spanX, spanY)
            }
        }
    }
    val binder = rememberAppWidgetBinder(
        controller = appWidgets,
        onBound = { id, info ->
            val m = metrics
            val pitchDp = m?.let { with(density) { it.cellW.toDp().value } } ?: 92f
            val cellHDp = m?.let { with(density) { it.cellH.toDp().value } } ?: 110f
            val span = spanFor(info, pitchDp, cellHDp, settings.gridColumns)
            placeAppWidget(id, span.spanX, span.spanY.coerceAtMost(settings.gridRows))
        },
        onFailed = { Toast.makeText(context, "Widget could not be added", Toast.LENGTH_SHORT).show() },
    )

    fun addDotWidget(kind: String, face: String, spanX: Int, spanY: Int) {
        val m = metrics ?: return
        val page = pickerPage.coerceIn(0, homePages - 1)
        val grid = deps.layout.occupancy(rawItems.orEmpty(), page, m.cols, m.rows)
        val sx = spanX.coerceAtMost(m.cols)
        val sy = spanY.coerceAtMost(m.rows)
        val pos = deps.layout.nearestFree(grid, sx, sy)
        scope.launch {
            if (pos != null) {
                deps.layout.addDotWidget(kind, face, page, pos.first, pos.second, sx, sy)
            } else {
                deps.settings.update { it.copy(pageCount = homePages + 1) }
                deps.layout.addDotWidget(kind, face, homePages, 0, 0, sx, sy)
            }
            haptics.confirm()
        }
    }

    // ------------------------------------------------------------------ drag & drop
    val travelSlop = with(density) { 10.dp.toPx() }

    fun beginLongPress(session: DragSession, anchor: Rect, pointer: Offset, menu: MenuTarget) {
        haptics.pick()
        drag.pending = session
        drag.pendingTravel = 0f
        drag.pointer = pointer
        drag.menu = if (settings.lockLayout && menu.source != DragSource.DRAWER) menu else menu
    }

    fun onDragDelta(delta: Offset) {
        drag.pointer += delta
        val pending = drag.pending
        if (drag.session == null && pending != null) {
            drag.pendingTravel += delta.getDistance()
            val canDrag = !settings.lockLayout || pending.source == DragSource.DRAWER || pending.source == DragSource.LIBRARY
            if (drag.pendingTravel > travelSlop && canDrag) {
                drag.menu = null
                drag.session = pending
                drag.pending = null
                if (pending.source == DragSource.DRAWER) drawer.snapClosed()
                if (pending.source == DragSource.FOLDER) openFolderId = null
            }
        }
        val s = drag.session ?: return
        // Drop zones live in the top 96 dp while dragging.
        val zoneH = with(density) { 96.dp.toPx() }
        drag.hoverZone = if (drag.pointer.y < zoneH + with(density) { 24.dp.toPx() }) {
            val w = view.width.toFloat().coerceAtLeast(1f)
            val canUninstall = s.app != null
            when {
                s.item == null -> DropZone.NONE
                drag.pointer.x < w / 3f -> DropZone.REMOVE
                drag.pointer.x < 2f * w / 3f -> if (s.app != null) DropZone.INFO else DropZone.REMOVE
                else -> if (canUninstall) DropZone.UNINSTALL else DropZone.REMOVE
            }
        } else {
            DropZone.NONE
        }
    }

    fun commitDrop() {
        val s = drag.session
        if (s == null) {
            drag.pending = null
            return
        }
        val m = metrics
        val pointer = drag.pointer
        val zone = drag.hoverZone
        val item = s.item
        val app = s.app
        val page = pagerState.currentPage
        scope.launch {
            when {
                zone == DropZone.REMOVE && item != null -> {
                    if (item.entity.type == ItemType.APP_WIDGET) appWidgets.delete(item.entity.appWidgetId)
                    deps.layout.remove(item.id)
                    haptics.confirm()
                }
                zone == DropZone.INFO && app != null -> deps.apps.openAppInfo(app)
                zone == DropZone.UNINSTALL && app != null -> deps.apps.uninstall(app)
                dockBounds.contains(pointer) && settings.dockEnabled && s.spanX == 1 && s.spanY == 1 -> {
                    val slots = settings.dockCount.coerceAtLeast(1)
                    val slotW = dockBounds.width / slots
                    val idx = ((pointer.x - dockBounds.left) / slotW).toInt().coerceIn(0, slots - 1)
                    val occupant = layout.dock.getOrNull(idx)
                    when {
                        occupant != null && occupant.id != item?.id && occupant.isApp && (item?.isApp == true || item == null) && app != null &&
                            layout.dock.size >= slots -> {
                            val draggedId = item?.id ?: deps.layout.addApp(app, Container.HOME, page, 0, 0)
                            deps.layout.createFolder(occupant.id, draggedId, "Folder")
                        }
                        occupant != null && occupant.isFolder && app != null && occupant.id != item?.id -> {
                            val draggedId = item?.id ?: deps.layout.addApp(app, Container.HOME, page, 0, 0)
                            deps.layout.addToFolder(occupant.id, draggedId)
                        }
                        item != null -> deps.layout.moveToDock(item.id, idx, slots)
                        app != null -> deps.layout.addAppToDock(app, idx, slots)
                    }
                    haptics.tick()
                }
                m != null && page < homePages -> {
                    val local = pointer - gridOrigin
                    val topLeft = drag.ghostTopLeft() - gridOrigin + Offset(s.width / 2f, s.height / 2f)
                    val anchor = if (s.spanX == 1 && s.spanY == 1) local else topLeft
                    val (cx, cy) = m.cellAt(anchor)
                    val tx = (cx - (s.spanX - 1) / 2).coerceIn(0, m.cols - s.spanX)
                    val ty = (cy - (s.spanY - 1) / 2).coerceIn(0, m.rows - s.spanY)
                    val pageItems = layout.pages.getOrNull(page).orEmpty()
                    val target = pageItems.firstOrNull { h ->
                        h.id != item?.id && cx >= h.entity.cellX && cx < h.entity.cellX + h.entity.spanX &&
                            cy >= h.entity.cellY && cy < h.entity.cellY + h.entity.spanY
                    }
                    val draggingApp = s.spanX == 1 && s.spanY == 1 && app != null
                    when {
                        target != null && draggingApp && target.isApp -> {
                            val draggedId = item?.id ?: deps.layout.addApp(app!!, Container.HOME, page, 0, 0)
                            deps.layout.createFolder(target.id, draggedId, "Folder")
                            haptics.confirm()
                        }
                        target != null && draggingApp && target.isFolder -> {
                            val draggedId = item?.id ?: deps.layout.addApp(app!!, Container.HOME, page, 0, 0)
                            deps.layout.addToFolder(target.id, draggedId)
                            haptics.confirm()
                        }
                        else -> {
                            val grid = deps.layout.occupancy(rawItems.orEmpty(), page, m.cols, m.rows, excludeId = item?.id ?: -1)
                            val pos = if (deps.layout.fits(grid, tx, ty, s.spanX, s.spanY)) tx to ty else deps.layout.nearestFree(grid, s.spanX, s.spanY, tx, ty)
                            if (pos == null) {
                                haptics.reject()
                            } else {
                                // Glide the ghost into its cell before committing, for a clean landing.
                                val r = m.itemRect(pos.first, pos.second, s.spanX, s.spanY)
                                val landing = gridOrigin + Offset(r.left + (r.width - s.width) / 2f, r.top + (r.height - s.height) / 2f) + s.grab
                                dropAnim.snapTo(pointer)
                                dropAnim.animateTo(landing, spring(dampingRatio = 0.9f, stiffness = 900f)) { drag.pointer = value }
                                if (item != null) {
                                    deps.layout.moveOnHome(item.id, page, pos.first, pos.second)
                                } else if (app != null) {
                                    deps.layout.addApp(app, Container.HOME, page, pos.first, pos.second)
                                }
                                haptics.tick()
                            }
                        }
                    }
                }
            }
            drag.clear()
        }
    }

    fun cancelDrag() {
        if (drag.session != null) commitDrop() else drag.pending = null
    }

    // Auto-scroll pages when the dragged item rests near an edge.
    LaunchedEffect(drag.isDragging) {
        if (!drag.isDragging) return@LaunchedEffect
        val edge = with(density) { 28.dp.toPx() }
        while (drag.isDragging) {
            delay(80)
            val x = drag.pointer.x
            val w = view.width.toFloat()
            val dir = when {
                x < edge -> -1
                x > w - edge -> 1
                else -> 0
            }
            if (dir != 0) {
                delay(450)
                val still = (dir < 0 && drag.pointer.x < edge) || (dir > 0 && drag.pointer.x > w - edge)
                if (still && drag.isDragging) {
                    val next = (pagerState.currentPage + dir).coerceIn(0, homePages - 1)
                    if (next != pagerState.currentPage) {
                        haptics.tick()
                        pagerState.animateScrollToPage(next)
                    } else if (dir > 0 && next == homePages - 1 && pagerState.currentPage == homePages - 1) {
                        // Dragging past the last page creates a new one.
                        deps.settings.update { it.copy(pageCount = homePages + 1) }
                        delay(120)
                        pagerState.animateScrollToPage(homePages)
                    }
                }
            }
        }
    }

    val itemCallbacks = remember(settings.lockLayout) {
        object : com.dotos.feature.home.ui.ItemCallbacks {
            override fun tap(item: HomeItem, bounds: Rect?) {
                when {
                    item.isFolder -> openFolderId = item.id
                    item.app != null -> launch(item.app, bounds)
                }
            }

            override fun longPress(item: HomeItem, source: DragSource, bounds: Rect, pointer: Offset, w: Float, h: Float) {
                val grab = pointer - Offset(bounds.left.toFloat(), bounds.top.toFloat())
                val session = DragSession(item, item.app, source, w, h, grab, item.entity.spanX, item.entity.spanY)
                beginLongPress(session, bounds, pointer, MenuTarget(item, item.app, source, bounds.toComposeRect()))
            }

            override fun drag(delta: Offset) = onDragDelta(delta)
            override fun dragEnd() = commitDrop()
            override fun dragCancel() = cancelDrag()
        }
    }

    val drawerActions = remember(deps) {
        object : DrawerActions {
            override fun launch(app: AppInfo, bounds: Rect?) = launch(app, bounds)

            override fun longPress(app: AppInfo, bounds: Rect, pointerInRoot: Offset) {
                val m = metrics
                val size = m?.plate ?: bounds.width().toFloat()
                val grab = Offset(size / 2f, size / 2f)
                val session = DragSession(null, app, DragSource.DRAWER, size, size, grab)
                beginLongPress(session, bounds, pointerInRoot, MenuTarget(null, app, DragSource.DRAWER, bounds.toComposeRect()))
            }

            override fun drag(delta: Offset) = onDragDelta(delta)
            override fun dragEnd() = commitDrop()
            override fun dragCancel() = cancelDrag()

            override fun unlockPrivateSpace() {
                deps.apps.privateProfile()?.let { deps.apps.setProfileQuiet(it, false) }
            }

            override fun lockPrivateSpace() {
                deps.apps.privateProfile()?.let { deps.apps.setProfileQuiet(it, true) }
            }
        }
    }

    // ------------------------------------------------------------------ UI
    val iconSpec = remember(settings) { settings.toIconSpec() }
    val drawerApps = remember(visibleApps, labelOverrides) { visibleApps }
    val privateUser = remember(apps, quiet) { deps.apps.privateProfile() }
    val privateLocked: Boolean? = privateUser?.let { deps.apps.serialOf(it) in quiet }
    val screenHeight = LocalConfiguration.current.screenHeightDp
    drawer.travelPx = with(density) { (screenHeight * 0.55f).dp.toPx() }
    val blurPx = with(density) { (if (settings.disableBlur) 0f else settings.drawerBlur).dp.toPx() }

    // Prewarm icons for the first frame (dock + current page).
    LaunchedEffect(layout, iconSpec, metrics) {
        val m = metrics ?: return@LaunchedEffect
        val px = m.plate.roundToInt()
        val firstFrame = (layout.dock + layout.pages.getOrNull(pagerState.currentPage).orEmpty()).flatMap { h ->
            listOfNotNull(h.app) + h.children.mapNotNull { it.app }
        }
        deps.icons.prewarm(firstFrame, iconSpec, px)
    }

    CompositionLocalProvider(
        LocalIconEngine provides deps.icons,
        LocalIconSpec provides iconSpec,
        LocalWidgetHost provides widgetServices,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!settings.useSystemWallpaper) {
                WallpaperLayer(
                    config = settings.wallpaper,
                    scroll = {
                        if (!settings.wallpaper.scrollParallax) 0f else {
                            val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            (pos / homePages.coerceAtLeast(1)).coerceIn(0f, 1f)
                        }
                    },
                    blurPx = { blurPx * drawer.progress.value + settings.homeBlur },
                    dim = { settings.homeDim + (settings.drawerDim - settings.homeDim) * drawer.progress.value },
                )
            } else {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = settings.drawerDim * drawer.progress.value }.background(Color.Black))
            }

            // ---------------- home content
            val dragUp = remember { mutableStateOf(0f) }
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = drawer.progress.value
                        alpha = 1f - p
                        val s = 1f - 0.04f * p
                        scaleX = s
                        scaleY = s
                    }
                    .pointerInput(settings.gestures) {
                        // Vertical swipes anywhere on home: up = drawer, down = shade.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            com.dotos.feature.wallpaper.engine.WallpaperTouch.report(down.position.x, down.position.y)
                            val tracker = VelocityTracker()
                            var total = 0f
                            var decided = false
                            var vertical = false
                            val slop = viewConfiguration.touchSlop
                            var dragOffset = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (drag.isDragging || drag.pending != null) break
                                val d = change.positionChange()
                                dragOffset += d
                                if (!decided) {
                                    if (change.isConsumed) break
                                    if (abs(dragOffset.y) > slop && abs(dragOffset.y) > abs(dragOffset.x) * 1.2f) {
                                        decided = true
                                        vertical = true
                                    } else if (abs(dragOffset.x) > slop) {
                                        break
                                    }
                                }
                                if (vertical) {
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    total += d.y
                                    if (settings.gestures.swipeUp == GestureAction.OPEN_DRAWER && (total < 0 || drawer.progress.value > 0f)) {
                                        drawer.dragBy(d.y)
                                    }
                                    change.consume()
                                }
                            }
                            if (vertical) {
                                val vy = tracker.calculateVelocity().y
                                dragUp.value = total
                                if (drawer.progress.value > 0f || (total < 0 && settings.gestures.swipeUp == GestureAction.OPEN_DRAWER)) {
                                    drawer.settle(vy)
                                } else if (total < -slop * 6) {
                                    perform(settings.gestures.swipeUp)
                                } else if (total > slop * 6) {
                                    perform(settings.gestures.swipeDown)
                                }
                            }
                        }
                    },
            ) {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    key = { it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    if (page < homePages) {
                        BoxWithConstraints(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .onGloballyPositioned { c ->
                                    if (page == pagerState.currentPage) {
                                        val b = c.boundsInRoot()
                                        gridOrigin = Offset(b.left, b.top)
                                    }
                                }
                                .pointerInput(settings.gestures.doubleTap, settings.gestures.longPressEmpty) {
                                    detectTapGestures(
                                        onDoubleTap = if (settings.gestures.doubleTap != GestureAction.NONE) {
                                            { perform(settings.gestures.doubleTap) }
                                        } else {
                                            null
                                        },
                                        onLongPress = {
                                            haptics.pick()
                                            pickerPage = page
                                            perform(settings.gestures.longPressEmpty)
                                        },
                                    )
                                },
                        ) {
                            val w = constraints.maxWidth.toFloat()
                            val h = constraints.maxHeight.toFloat()
                            val m = remember(w, h, settings.gridColumns, settings.gridRows) {
                                GridMetrics(settings.gridColumns, settings.gridRows, w, h)
                            }
                            if (page == pagerState.currentPage) LaunchedEffect(m) { metrics = m }
                            HomeGridPage(
                                items = layout.pages.getOrNull(page).orEmpty(),
                                metrics = m,
                                settings = settings,
                                labelColor = contentColor,
                                badges = badges,
                                drag = drag,
                                safeMode = deps.safeMode,
                                appWidgets = appWidgets,
                                callbacks = itemCallbacks,
                            )
                        }
                    } else {
                        AppLibraryPage(
                            apps = visibleApps.filter { it.profile != ProfileKind.PRIVATE },
                            recentlyUsed = recent,
                            onLaunch = { app, bounds -> launch(app, bounds) },
                            onAppLongPress = { app ->
                                drag.menu = MenuTarget(null, app, DragSource.LIBRARY, ComposeRect(Offset(view.width / 2f, view.height / 2f), androidx.compose.ui.geometry.Size(1f, 1f)))
                            },
                            contentColor = contentColor,
                        )
                    }
                }
                if (settings.pageIndicator) {
                    Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
                        DotPageIndicator(
                            pageCount = homePages + 1,
                            position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                            color = contentColor,
                        )
                    }
                }
                if (settings.dockEnabled) {
                    DockRow(
                        items = layout.dock,
                        slots = settings.dockCount,
                        plate = metrics?.plate,
                        settings = settings,
                        badges = badges,
                        drag = drag,
                        callbacks = itemCallbacks,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .onGloballyPositioned { dockBounds = it.boundsInRoot() },
                    )
                }
                if (settings.searchBar) {
                    SearchPill(
                        dark = true,
                        onClick = { drawer.open(focusSearch = true) },
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 12.dp),
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }

            // ---------------- drawer
            val drawerVisible by remember { derivedStateOf { drawer.progress.value > 0.001f || drag.pending?.source == DragSource.DRAWER || drag.session?.source == DragSource.DRAWER } }
            if (drawerVisible) {
                AppDrawer(
                    controller = drawer,
                    apps = drawerApps,
                    privateLocked = privateLocked,
                    config = DrawerConfig(
                        columns = settings.drawerColumns,
                        labels = settings.drawerLabels,
                        autoKeyboard = settings.autoKeyboard,
                        fastScroller = settings.fastScroller,
                        iconSize = 56.dp,
                        badges = badges,
                        showBadges = settings.badgeStyle != com.dotos.core.data.settings.BadgeStyle.OFF,
                    ),
                    actions = drawerActions,
                )
            }

            // ---------------- overlays
            val folder = openFolderId?.let { id -> (layout.pages.flatten() + layout.dock).firstOrNull { it.id == id } }
            FolderOverlay(
                folder = folder,
                onDismiss = { openFolderId = null },
                onRename = { title -> folder?.let { f -> scope.launch { deps.layout.renameFolder(f.id, title) } } },
                callbacks = itemCallbacks,
                badges = badges,
                settings = settings,
            )
            DropTargetsBar(drag = drag)
            DragGhost(drag = drag, settings = settings, safeMode = deps.safeMode, appWidgets = appWidgets)
            ItemMenu(
                drag = drag,
                apps = deps.apps,
                onLaunchShortcut = { drag.menu = null },
                onRemove = { item ->
                    if (item.entity.type == ItemType.APP_WIDGET) appWidgets.delete(item.entity.appWidgetId)
                    scope.launch { deps.layout.remove(item.id) }
                },
                onHide = { app ->
                    scope.launch(Dispatchers.IO) { deps.database.appMeta().hide(com.dotos.core.data.db.HiddenAppEntity(app.key)) }
                },
                onAddToHome = { app ->
                    val m = metrics
                    if (m != null) {
                        val page = pagerState.currentPage.coerceIn(0, homePages - 1)
                        val grid = deps.layout.occupancy(rawItems.orEmpty(), page, m.cols, m.rows)
                        deps.layout.nearestFree(grid, 1, 1)?.let { pos ->
                            scope.launch { deps.layout.addApp(app, Container.HOME, page, pos.first, pos.second) }
                        }
                    }
                },
                onCustomize = { deps.openSettings("icons") },
                onResize = { item, sx, sy -> scope.launch { deps.layout.resize(item.id, sx, sy) } },
                onLaunch = { app, rect -> launch(app, rect) },
            )
            EditSheet(
                visible = editMode,
                onDismiss = { editMode = false },
                onWallpaper = { editMode = false; deps.openSettings("customizer") },
                onWidgets = { editMode = false; pickerOpen = true },
                onIcons = { editMode = false; deps.openSettings("icons") },
                onLayout = { editMode = false; deps.openSettings("layout") },
                onSettings = { editMode = false; deps.openSettings(null) },
                onAddPage = {
                    scope.launch {
                        deps.settings.update { it.copy(pageCount = homePages + 1) }
                        delay(100)
                        pagerState.animateScrollToPage(homePages)
                    }
                },
                canDeletePage = homePages > 1 && pagerState.currentPage < homePages &&
                    layout.pages.getOrNull(pagerState.currentPage).isNullOrEmpty(),
                onDeletePage = {
                    val p = pagerState.currentPage
                    scope.launch {
                        deps.layout.deletePage(p)
                        deps.settings.update { it.copy(pageCount = (homePages - 1).coerceAtLeast(1)) }
                    }
                },
            )
            WidgetPickerSheet(
                visible = pickerOpen,
                onDismiss = { pickerOpen = false },
                onPickDotWidget = { kind, face, size ->
                    pickerOpen = false
                    addDotWidget(kind, face, size.spanX, size.spanY)
                },
                onPickAppWidget = { info ->
                    pickerOpen = false
                    binder(info)
                },
                controller = appWidgets,
            )
        }
    }
}

private fun Rect.toComposeRect() = ComposeRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

/** The bottom "Search" pill (48 dp stadium), like the reference home. */
@Composable
internal fun SearchPill(dark: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill = if (dark) Color(0xF20A0A0A) else Color(0xF2F2F2F2)
    val fg = if (dark) Color.White else Color.Black
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(fill)
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val s = size.minDimension
            val stroke = s * 0.12f
            drawCircle(fg, radius = s * 0.33f, center = Offset(s * 0.42f, s * 0.42f), style = Stroke(stroke))
            drawLine(fg, Offset(s * 0.67f, s * 0.67f), Offset(s * 0.92f, s * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text("Search", style = DotTheme.type.bodySmall, color = fg)
        }
        Spacer(Modifier.width(16.dp))
    }
}

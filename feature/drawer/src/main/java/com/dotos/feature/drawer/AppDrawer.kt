package com.dotos.feature.drawer

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.launcherGestures
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.core.launcher.AppInfo
import com.dotos.core.launcher.AppSearch
import com.dotos.core.launcher.ProfileKind
import com.dotos.feature.icons.AppIcon
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Callbacks from the drawer to the home host (menus, drag to home, launching). */
interface DrawerActions {
    fun launch(app: AppInfo, bounds: Rect?)
    /** Long-press: show the app menu anchored at [bounds] (root coordinates). */
    fun longPress(app: AppInfo, bounds: Rect, pointerInRoot: Offset)
    fun drag(delta: Offset)
    fun dragEnd()
    fun dragCancel()
    fun unlockPrivateSpace()
    fun lockPrivateSpace()
}

@androidx.compose.runtime.Immutable
data class DrawerConfig(
    val columns: Int = 4,
    val labels: Boolean = true,
    val autoKeyboard: Boolean = false,
    val fastScroller: Boolean = true,
    val iconSize: Dp = 56.dp,
    val badges: Map<String, Int> = emptyMap(),
    val showBadges: Boolean = true,
    /** Single-column list instead of a grid. */
    val list: Boolean = false,
)

/**
 * The app drawer overlay: search pill, A–Z grid with an edge fast-scroller, work-profile tab and a
 * Private Space section. Visibility is driven by [controller]: fades in with a 24 dp lift.
 */
@Composable
fun AppDrawer(
    controller: DrawerController,
    apps: List<AppInfo>,
    privateLocked: Boolean?,
    config: DrawerConfig,
    actions: DrawerActions,
    modifier: Modifier = Modifier,
) {
    val colors = DotTheme.colors
    val type = DotTheme.type
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    val hasWork = remember(apps) { apps.any { it.profile == ProfileKind.WORK } }
    val personal = remember(apps) { apps.filter { it.profile == ProfileKind.MAIN || it.profile == ProfileKind.OTHER } }
    val work = remember(apps) { apps.filter { it.profile == ProfileKind.WORK } }
    val privateApps = remember(apps) { apps.filter { it.profile == ProfileKind.PRIVATE } }
    val base = if (tab == 1 && hasWork) work else personal
    val results by remember(base, privateApps) {
        derivedStateOf { if (query.isBlank()) base else AppSearch.search(base + privateApps, query) { it.label } }
    }
    val calc by remember { derivedStateOf { Calculator.evaluate(query) } }

    // Close keyboard and clear search when the drawer closes.
    LaunchedEffect(controller) {
        snapshotFlow { controller.progress.value < 0.01f }.collect { closed ->
            if (closed) {
                query = ""
                focus.clearFocus()
                keyboard?.hide()
                gridState.scrollToItem(0)
            }
        }
    }
    LaunchedEffect(controller.focusSearchRequest, config.autoKeyboard) {
        snapshotFlow { controller.progress.value > 0.95f }.collect { open ->
            if (open && (config.autoKeyboard || controller.focusSearchRequest > 0)) runCatching { focusRequester.requestFocus() }
        }
    }

    val nested = remember(controller) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0 && controller.progress.value < 1f) {
                    controller.dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    focus.clearFocus()
                    keyboard?.hide()
                    controller.dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (controller.progress.value < 0.999f) {
                    controller.settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val lift = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = controller.progress.value
                alpha = p
                translationY = (1f - p) * lift
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            // Search pill
            Row(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surface.copy(alpha = 0.92f))
                    .border(1.dp, colors.hairline, RoundedCornerShape(50))
                    .pointerInput(Unit) {
                        // Pull down on the search pill closes the drawer too.
                        detectVerticalDragGestures(
                            onVerticalDrag = { c, dy ->
                                c.consume()
                                controller.dragBy(dy)
                            },
                            onDragEnd = { controller.settle(0f) },
                        )
                    }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchGlyph(colors.onSurfaceMuted)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search apps", style = type.body, color = colors.onSurfaceMuted)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = type.body.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let { actions.launch(it, null) } }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
                if (query.isNotEmpty()) {
                    Text(
                        "Clear",
                        style = type.bodySmall,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.clickable { query = "" }.padding(8.dp),
                    )
                }
            }
            if (hasWork && query.isBlank()) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Personal", "Work").forEachIndexed { i, label ->
                        val sel = tab == i
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) colors.onSurface else Color.Transparent)
                                .border(1.dp, if (sel) colors.onSurface else colors.hairline, RoundedCornerShape(50))
                                .clickable { tab = i }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text(label, style = type.bodySmall, color = if (sel) colors.background else colors.onSurface) }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (config.list) 1 else config.columns),
                    state = gridState,
                    contentPadding = PaddingValues(start = 12.dp, end = if (config.fastScroller) 28.dp else 12.dp, top = 8.dp, bottom = 32.dp),
                    modifier = Modifier.fillMaxSize().nestedScroll(nested),
                ) {
                    val calcValue = calc
                    if (calcValue != null) {
                        item(key = "calc", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "= $calcValue",
                                style = type.display,
                                color = colors.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                            )
                        }
                    }
                    items(results, key = { it.key }) { app ->
                        DrawerCell(app, config, actions)
                    }
                    if (query.isBlank() && privateLocked != null && tab == 0) {
                        item(key = "private", span = { GridItemSpan(maxLineSpan) }) {
                            PrivateSpaceRow(privateLocked, privateApps, config, actions)
                        }
                    }
                    if (results.isEmpty() && calcValue == null) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "No apps found",
                                style = type.body,
                                color = colors.onSurfaceMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                            )
                        }
                    }
                }
                if (config.fastScroller && query.isBlank()) {
                    FastScroller(
                        apps = base,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 24.dp),
                        onLetter = { index -> scope.launch { gridState.scrollToItem(index) } },
                    )
                }
            }
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun DrawerCell(app: AppInfo, config: DrawerConfig, actions: DrawerActions) {
    val colors = DotTheme.colors
    val haptics = rememberDotHaptics()
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")
    fun bounds(): Rect? = coords?.takeIf { it.isAttached }?.boundsInRoot()?.let {
        Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
    }
    val gestures = Modifier
        .onGloballyPositioned { coords = it }
        .semantics { contentDescription = app.label }
        .launcherGestures(
            key = app.key,
            onPress = { pressed = it },
            onTap = { actions.launch(app, bounds()) },
            onLongPress = { local ->
                haptics.pick()
                val c = coords
                val b = bounds()
                if (c != null && b != null) actions.longPress(app, b, c.localToRoot(local))
            },
            onDrag = actions::drag,
            onDragEnd = actions::dragEnd,
            onDragCancel = actions::dragCancel,
        )
    if (config.list) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).then(gestures),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { AppIcon(app, size = 44.dp) }
            Text(
                app.label,
                style = DotTheme.type.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp).weight(1f),
            )
        }
        return
    }
    Column(
        Modifier
            .padding(vertical = 10.dp)
            .onGloballyPositioned { coords = it }
            .semantics { contentDescription = app.label }
            .launcherGestures(
                key = app.key,
                onPress = { pressed = it },
                onTap = { actions.launch(app, bounds()) },
                onLongPress = { local ->
                    haptics.pick()
                    val c = coords
                    val b = bounds()
                    if (c != null && b != null) actions.longPress(app, b, c.localToRoot(local))
                },
                onDrag = actions::drag,
                onDragEnd = actions::dragEnd,
                onDragCancel = actions::dragCancel,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            AppIcon(app, size = config.iconSize)
            val count = config.badges[app.packageName] ?: 0
            if (config.showBadges && count > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 4.dp)
                        .size(config.iconSize * 0.16f)
                        .clip(CircleShape)
                        .background(colors.onSurface),
                )
            }
        }
        if (config.labels) {
            Text(
                app.label,
                style = DotTheme.type.iconLabel,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun PrivateSpaceRow(locked: Boolean, apps: List<AppInfo>, config: DrawerConfig, actions: DrawerActions) {
    val colors = DotTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Row(
            Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.hairline, RoundedCornerShape(24.dp))
                .clickable { if (locked) actions.unlockPrivateSpace() else actions.lockPrivateSpace() }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Private space", style = DotTheme.type.title, color = colors.onSurface)
                Text(if (locked) "Locked" else "${apps.size} apps", style = DotTheme.type.bodySmall, color = colors.onSurfaceMuted)
            }
            Text(if (locked) "Unlock" else "Lock", style = DotTheme.type.bodySmall, color = colors.accent)
        }
        if (!locked && apps.isNotEmpty()) {
            val rows = apps.chunked(config.columns)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app -> Box(Modifier.weight(1f)) { DrawerCell(app, config, actions) } }
                    repeat(config.columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FastScroller(apps: List<AppInfo>, modifier: Modifier, onLetter: (Int) -> Unit) {
    val colors = DotTheme.colors
    val haptics = rememberDotHaptics()
    val letters = remember(apps) {
        val map = LinkedHashMap<Char, Int>()
        apps.forEachIndexed { i, a ->
            val c = AppSearch.fold(a.label).firstOrNull()?.uppercaseChar()?.let { if (it.isLetter()) it else '#' } ?: '#'
            if (c !in map) map[c] = i
        }
        map
    }
    if (letters.size < 2) return
    val keys = letters.keys.toList()
    var active by remember { mutableStateOf<Char?>(null) }
    var height by remember { mutableIntStateOf(1) }
    Box(modifier.width(28.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { height = it.size.height.coerceAtLeast(1) }
                .pointerInput(keys) {
                    fun pick(y: Float) {
                        val idx = ((y / height) * keys.size).toInt().coerceIn(0, keys.size - 1)
                        val c = keys[idx]
                        if (c != active) {
                            active = c
                            haptics.tick()
                            onLetter(letters[c] ?: 0)
                        }
                    }
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull() ?: continue
                            if (ch.pressed) {
                                pick(ch.position.y)
                                ch.consume()
                            } else {
                                active = null
                            }
                        }
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            keys.forEach { c ->
                Text(
                    c.toString(),
                    style = DotTheme.type.sectionLabel,
                    color = if (c == active) colors.onSurface else colors.onSurfaceMuted,
                )
            }
        }
        AnimatedVisibility(
            visible = active != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).offset(x = (-72).dp),
        ) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(colors.surface).border(1.dp, colors.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text((active ?: ' ').toString(), style = DotTheme.type.display, color = colors.onSurface)
            }
        }
    }
}

@Composable
internal fun SearchGlyph(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.11f
        drawCircle(color, radius = s * 0.34f, center = Offset(s * 0.42f, s * 0.42f), style = Stroke(stroke))
        drawLine(color, Offset(s * 0.68f, s * 0.68f), Offset(s * 0.92f, s * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

package com.dotos.feature.drawer.library

import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotMotion
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.LocalReduceMotion
import com.dotos.core.designsystem.component.DotLoading
import com.dotos.core.launcher.AppInfo

private const val OVERLAY_SCALE = 0.92f
private const val OVERLAY_MAX_HEIGHT_FRACTION = 0.74f
private const val PANEL_COLUMNS = 4
private val PanelIconSize = 56.dp

/**
 * iOS-style App Library: the right-most home page, drawn over the live wallpaper.
 *
 * Top: a 52 dp search pill (typing swaps the tiles for one flat result list ranked by [com.dotos.core.launcher.AppSearch]).
 * Below: two columns of square category tiles ("Suggestions", "Recently added", then real categories by size,
 * "Other" last). Tapping a tile (or its mini-cluster) opens an in-page overlay with every app of the category.
 *
 * [onLaunch] receives the tapped icon's window bounds for launch animations (null when launched from the
 * keyboard action). [bottomPadding] keeps content clear of the dock / search bar owned by the host.
 */
@Composable
fun AppLibraryPage(
    apps: List<AppInfo>,
    recentlyUsed: List<AppInfo>,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    bottomPadding: Dp = LibraryDims.DefaultBottomPadding,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var openGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchFocused by remember { mutableStateOf(false) }

    val groups = remember(apps, recentlyUsed) {
        buildLibraryGroups(apps, recentlyUsed, System.currentTimeMillis())
    }
    val alphabetical = remember(apps) { apps.sortedWith(AppOrder) }
    val results by remember(alphabetical) { derivedStateOf { searchApps(alphabetical, query) } }
    val searching = query.isNotBlank()
    val openGroup = groups.firstOrNull { it.id == openGroupId }
    val gridState = rememberLazyGridState()

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Closes the keyboard on the first user scroll instead of on every scroll frame.
    val dismissKeyboardOnScroll = remember(focusManager, keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (searchFocused && available.y != 0f) {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }

    // Launching resets transient UI so the page is clean when the user comes back.
    val launchApp: (AppInfo, Rect?) -> Unit = remember(onLaunch, focusManager) {
        { app, bounds ->
            onLaunch(app, bounds)
            openGroupId = null
            if (query.isNotEmpty()) {
                query = ""
                focusManager.clearFocus()
            }
        }
    }

    BackHandler(enabled = openGroup != null || query.isNotEmpty()) {
        if (openGroup != null) {
            openGroupId = null
        } else {
            query = ""
            focusManager.clearFocus()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .nestedScroll(dismissKeyboardOnScroll),
        ) {
            LibrarySearchPill(
                query = query,
                onQueryChange = { query = it },
                onSubmit = {
                    val first = results.firstOrNull()
                    if (first != null) launchApp(first, null) else keyboard?.hide()
                },
                onFocusChange = { searchFocused = it },
                contentColor = contentColor,
                modifier = Modifier.padding(horizontal = LibraryDims.PageHorizontal, vertical = 12.dp),
            )
            when {
                searching -> SearchResults(
                    query = query,
                    results = results,
                    contentColor = contentColor,
                    bottomPadding = bottomPadding,
                    onLaunch = launchApp,
                    onLongPress = onAppLongPress,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = LibraryDims.PageHorizontal),
                )
                groups.isEmpty() -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    DotLoading(color = contentColor)
                }
                else -> CategoryGrid(
                    groups = groups,
                    state = gridState,
                    contentColor = contentColor,
                    bottomPadding = bottomPadding,
                    onOpen = {
                        focusManager.clearFocus()
                        openGroupId = it.id
                    },
                    onLaunch = launchApp,
                    onLongPress = onAppLongPress,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        CategoryOverlay(
            group = openGroup,
            contentColor = contentColor,
            onDismiss = { openGroupId = null },
            onLaunch = launchApp,
            onLongPress = onAppLongPress,
        )
    }
}

@Composable
private fun CategoryGrid(
    groups: List<LibraryGroup>,
    state: LazyGridState,
    contentColor: Color,
    bottomPadding: Dp,
    onOpen: (LibraryGroup) -> Unit,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = LibraryDims.PageHorizontal,
            end = LibraryDims.PageHorizontal,
            top = 4.dp,
            bottom = bottomPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(LibraryDims.GridGap),
        verticalArrangement = Arrangement.spacedBy(LibraryDims.GridGap),
    ) {
        items(groups, key = { it.id }, contentType = { "tile" }) { group ->
            CategoryTile(
                group = group,
                contentColor = contentColor,
                onOpen = { onOpen(group) },
                onLaunch = onLaunch,
                onLongPress = onLongPress,
            )
        }
    }
}

/** Keeps the last non-null group alive so the panel's content survives its own exit animation. */
private class LastGroup {
    var value: LibraryGroup? = null
}

/**
 * Category pop-up: dark scrim + panel that fades and scales from 0.92 on the launcher spring
 * (damping 0.8, stiffness 380). With reduce-motion on, both snap.
 */
@Composable
private fun CategoryOverlay(
    group: LibraryGroup?,
    contentColor: Color,
    onDismiss: () -> Unit,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val fadeSpec: FiniteAnimationSpec<Float> = if (reduceMotion) snap() else DotMotion.spring()
    val last = remember { LastGroup() }
    if (group != null) last.value = group
    val visible = group != null

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelMaxHeight = maxHeight * OVERLAY_MAX_HEIGHT_FRACTION
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(fadeSpec),
            exit = fadeOut(fadeSpec),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(LibraryColors.Scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = LibraryText.CLOSE,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.Center),
            enter = if (reduceMotion) {
                fadeIn(fadeSpec)
            } else {
                fadeIn(fadeSpec) + scaleIn(fadeSpec, initialScale = OVERLAY_SCALE)
            },
            exit = if (reduceMotion) {
                fadeOut(fadeSpec)
            } else {
                fadeOut(fadeSpec) + scaleOut(fadeSpec, targetScale = OVERLAY_SCALE)
            },
        ) {
            last.value?.let { shown ->
                CategoryPanel(
                    group = shown,
                    contentColor = contentColor,
                    maxHeight = panelMaxHeight,
                    onLaunch = onLaunch,
                    onLongPress = onLongPress,
                )
            }
        }
    }
}

@Composable
private fun CategoryPanel(
    group: LibraryGroup,
    contentColor: Color,
    maxHeight: Dp,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = DotTheme.type
    val shape = LibraryDims.TileShape
    Column(
        modifier
            .padding(horizontal = LibraryDims.PageHorizontal)
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(shape)
            .background(LibraryColors.Plate, shape)
            .border(1.dp, LibraryColors.Hairline, shape)
            // Swallow taps on the panel body so they never fall through to the dismiss scrim underneath.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(DotTheme.colors.accent, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = group.title,
                style = type.dotTitle,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = group.apps.size.toString(),
                style = type.mono,
                color = contentColor.copy(alpha = 0.55f),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(PANEL_COLUMNS),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(group.apps, key = { it.key }, contentType = { "app" }) { app ->
                AppTarget(
                    app = app,
                    iconSize = PanelIconSize,
                    onLaunch = onLaunch,
                    onLongPress = onLongPress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    labelColor = contentColor,
                )
            }
        }
    }
}

package com.dotos.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotos.core.data.settings.DockStyle
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.data.settings.WallpaperConfig
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.pressScale
import com.dotos.core.launcher.AppInfo
import com.dotos.feature.icons.AppIcon
import com.dotos.feature.icons.drawPlate
import com.dotos.feature.icons.engine.LocalIconSpec
import com.dotos.feature.settings.SettingsDeps
import com.dotos.feature.wallpaper.engine.DotWallpaper
import com.dotos.feature.wallpaper.engine.WallpaperLayer
import com.dotos.feature.wallpaper.engine.resolve

/** One home icon inside a preview. Falls back to a bare plate while the app list loads. */
@Composable
internal fun PreviewIcon(app: AppInfo?, size: Dp, showLabel: Boolean) {
    val spec = LocalIconSpec.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (app != null) {
            AppIcon(app, size = size)
        } else {
            Box(
                Modifier
                    .size(size)
                    .drawBehind { drawPlate(spec.plateShape, spec.plateColor) },
            )
        }
        if (showLabel) {
            Box(
                Modifier
                    .padding(top = size * 0.16f)
                    .size(width = size * 0.72f, height = 2.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.6f)),
            )
        }
    }
}

@Composable
private fun SearchPill(w: Dp) {
    val fontSp = with(LocalDensity.current) { (w * 0.05f).toSp() }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = w * 0.08f)
            .height(w * 0.115f)
            .clip(CircleShape)
            .background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MagnifierIcon(Modifier.size(w * 0.04f), Color.White)
            Spacer(Modifier.width(w * 0.02f))
            Text(
                "Search",
                style = DotTheme.type.bodySmall.copy(fontSize = fontSp, lineHeight = fontSp * 1.2f),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun PreviewOverlay(w: Dp, s: LauncherSettings, apps: List<AppInfo>, extraIcons: Int) {
    val dockN = s.dockCount.coerceIn(4, 6)
    val dockIcon = minOf(w * 0.15f, w * 0.74f / dockN)
    val gridCols = s.gridColumns.coerceIn(4, 5)
    val gridIcon = minOf(w * 0.15f, w * 0.74f / gridCols)
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = w * 0.2f, bottom = w * 0.08f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (extraIcons > 0) {
            val cellW = w * 0.84f / gridCols
            (0 until extraIcons).chunked(gridCols).forEach { rowIndexes ->
                Row(Modifier.padding(bottom = w * 0.05f)) {
                    rowIndexes.forEach { i ->
                        Box(Modifier.width(cellW), contentAlignment = Alignment.Center) {
                            PreviewIcon(apps.getOrNull(dockN + i), gridIcon, s.showLabels)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (s.dockEnabled) {
            val dockShape = RoundedCornerShape(dockIcon * 0.55f)
            val backing = when (s.dockStyle) {
                DockStyle.NONE -> Modifier
                DockStyle.BLUR -> Modifier.background(Color.White.copy(alpha = 0.14f), dockShape)
                DockStyle.SOLID -> Modifier.background(Color.Black.copy(alpha = 0.6f), dockShape)
            }
            val inner = if (s.dockStyle == DockStyle.NONE) 0.dp else w * 0.02f
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = w * 0.06f)
                    .then(backing)
                    .padding(horizontal = w * 0.02f, vertical = inner),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                repeat(dockN) { i -> PreviewIcon(apps.getOrNull(i), dockIcon, s.showLabels) }
            }
        }
        if (s.searchBar) {
            Spacer(Modifier.height(w * 0.07f))
            SearchPill(w)
        }
    }
}

/**
 * Phone-shaped live preview of the home screen: the real wallpaper layer plus a mini dock built from
 * real AppIcons (same icon spec as the launcher) and the search pill. [extraIcons] adds a few grid
 * icons above the dock (Icon Pack screen).
 */
@Composable
internal fun HomePreview(
    deps: SettingsDeps,
    s: LauncherSettings,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    maxFps: Int = 24,
    extraIcons: Int = 0,
    selected: Boolean = false,
) {
    val apps by deps.apps.apps.collectAsStateWithLifecycle()
    PhoneMock(modifier, selected = selected) {
        WallpaperLayer(config = s.wallpaper, animate = animate, maxFps = maxFps, dim = { s.homeDim })
        BoxWithConstraints(Modifier.fillMaxSize()) {
            PreviewOverlay(maxWidth, s, apps.orEmpty(), extraIcons)
        }
    }
}

/** Preview of the bare wallpaper (Atmosphere / Glass visible, no icons) with a dot clock. */
@Composable
internal fun WallpaperOnlyPreview(
    s: LauncherSettings,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    maxFps: Int = 24,
) {
    val ink = remember(s.wallpaper) { if (s.wallpaper.resolve().isLight) Color(0xFF111111) else Color.White }
    PhoneMock(modifier) {
        WallpaperLayer(config = s.wallpaper, animate = animate, maxFps = maxFps, dim = { s.homeDim })
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fontSp = with(LocalDensity.current) { (maxWidth * 0.2f).toSp() }
            Text(
                com.dotos.core.designsystem.dotTime("09:41"),
                style = DotTheme.type.display.copy(fontSize = fontSp, lineHeight = fontSp * 1.1f),
                color = ink,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = maxWidth * 0.28f),
            )
        }
    }
}

/** Default config used for a wallpaper thumbnail (first preset, no tweaks). */
internal fun thumbConfig(wp: DotWallpaper): WallpaperConfig =
    WallpaperConfig(id = wp.id, presetId = wp.presets.first().id)

/** Rounded frame that shows a 2 dp white ring (3 dp gap) when [selected]. */
@Composable
internal fun SelectableFrame(
    selected: Boolean,
    radius: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = DotTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction)
            .border(2.dp, if (selected) colors.onSurface else Color.Transparent, RoundedCornerShape(radius))
            .padding(5.dp)
            .clip(RoundedCornerShape(radius - 5.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                if (description != null) contentDescription = description
            },
        content = content,
    )
}

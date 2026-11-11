package com.dotos.feature.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.data.settings.WallpaperConfig
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.DotCard
import com.dotos.core.designsystem.component.DotDivider
import com.dotos.core.designsystem.component.DotPageIndicator
import com.dotos.core.designsystem.component.DotRoundAction
import com.dotos.core.designsystem.component.pressScale
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.feature.settings.nav.Routes
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.ColoursIcon
import com.dotos.feature.settings.ui.GlassIcon
import com.dotos.feature.settings.ui.HomePreview
import com.dotos.feature.settings.ui.IMAGE_WALLPAPER_ID
import com.dotos.feature.settings.ui.IconPackIcon
import com.dotos.feature.settings.ui.LayoutIcon
import com.dotos.feature.settings.ui.MoreRingIcon
import com.dotos.feature.settings.ui.NavRow
import com.dotos.feature.settings.ui.ScreenPadding
import com.dotos.feature.settings.ui.SelectableFrame
import com.dotos.feature.settings.ui.SettingsScaffold
import com.dotos.feature.settings.ui.WallpaperOnlyPreview
import com.dotos.feature.settings.ui.WavesIcon
import com.dotos.feature.settings.ui.panelColor
import com.dotos.feature.settings.ui.thumbConfig
import com.dotos.feature.settings.ui.withWallpaper
import com.dotos.feature.wallpaper.engine.WallpaperLayer
import com.dotos.feature.wallpaper.engine.WallpaperRegistry
import kotlin.math.absoluteValue

/** Home screen customiser (reference: preview cards, Colours / Icon Pack / Layout, wallpaper strip). */
@Composable
internal fun HomeCustomizerScreen(env: SettingsEnv, s: LauncherSettings) {
    SettingsScaffold(title = "Home screen", onBack = env.back) {
        PreviewPager(env, s)
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DotRoundAction(label = "Colours", onClick = { env.navigate(Routes.COLOURS) }, size = 56.dp) { ColoursIcon() }
            DotRoundAction(label = "Icon Pack", onClick = { env.navigate(Routes.ICONS) }, size = 56.dp) { IconPackIcon() }
            DotRoundAction(label = "Layout", onClick = { env.navigate(Routes.LAYOUT) }, size = 56.dp) { LayoutIcon() }
        }
        Spacer(Modifier.height(24.dp))
        WallpaperPanel(env, s)
        Spacer(Modifier.height(8.dp))
        NavRow(
            title = "Customise wallpaper",
            subtitle = "Presets, colours, animation, blur and more",
            onClick = { env.navigate(Routes.WALLPAPER_EDITOR) },
        )
    }
}

@Composable
private fun PreviewPager(env: SettingsEnv, s: LauncherSettings) {
    val colors = DotTheme.colors
    val pagerState = rememberPagerState(pageCount = { 2 })
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardW = 164.dp
        val sidePadding = (maxWidth - cardW) / 2
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = sidePadding),
            pageSize = PageSize.Fixed(cardW),
            pageSpacing = 16.dp,
        ) { page ->
            val active = pagerState.currentPage == page
            val tap = remember { MutableInteractionSource() }
            Column(
                Modifier.graphicsLayer {
                    val off = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    val scale = 1f - 0.08f * off
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.45f * off
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (page == 0) {
                    HomePreview(
                        deps = env.deps,
                        s = s,
                        modifier = Modifier
                            .width(cardW)
                            .clickable(interactionSource = tap, indication = null, role = Role.Button) { env.navigate(Routes.LAYOUT) },
                        animate = active,
                        maxFps = 24,
                    )
                } else {
                    WallpaperOnlyPreview(
                        s = s,
                        modifier = Modifier
                            .width(cardW)
                            .clickable(interactionSource = tap, indication = null, role = Role.Button) { env.navigate(Routes.WALLPAPER_EDITOR) },
                        animate = active,
                        maxFps = 24,
                    )
                }
                Text(
                    if (page == 0) "Home screen" else "Wallpaper",
                    style = DotTheme.type.bodySmall,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        DotPageIndicator(
            pageCount = 2,
            position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            color = colors.onSurface,
        )
    }
}

@Composable
private fun WallpaperPanel(env: SettingsEnv, s: LauncherSettings) {
    DotCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        radius = 28.dp,
        color = panelColor(),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            WallpaperStrip(env, s)
            DotDivider(Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
            EffectToggles(env, s)
            MoreWallpapersRow(env)
        }
    }
}

@Composable
private fun WallpaperStrip(env: SettingsEnv, s: LauncherSettings) {
    val wallpapers = WallpaperRegistry.all
    LazyRow(
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(wallpapers, key = { it.id }) { wp ->
            val selected = s.wallpaper.id == wp.id
            val thumb = remember(wp.id) { thumbConfig(wp) }
            SelectableFrame(
                selected = selected,
                radius = 18.dp,
                onClick = {
                    if (selected) env.navigate(Routes.WALLPAPER_EDITOR) else env.updateWallpaper { it.withWallpaper(wp) }
                },
                modifier = Modifier.size(width = 92.dp, height = 156.dp),
                description = wp.name,
            ) {
                WallpaperLayer(
                    config = if (selected) s.wallpaper else thumb,
                    animate = selected,
                    maxFps = 30,
                    allowGlass = selected,
                )
            }
        }
        if (s.wallpaper.imagePath != null) {
            item(key = "photo") {
                val selected = s.wallpaper.id == IMAGE_WALLPAPER_ID
                SelectableFrame(
                    selected = selected,
                    radius = 18.dp,
                    onClick = {
                        if (selected) env.navigate(Routes.WALLPAPER_EDITOR) else env.updateWallpaper { it.copy(id = IMAGE_WALLPAPER_ID) }
                    },
                    modifier = Modifier.size(width = 92.dp, height = 156.dp),
                    description = "Your photo",
                ) {
                    WallpaperLayer(
                        config = WallpaperConfig(id = IMAGE_WALLPAPER_ID, imagePath = s.wallpaper.imagePath),
                        animate = false,
                        allowGlass = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectToggles(env: SettingsEnv, s: LauncherSettings) {
    val atmosphereOn = s.wallpaper.id == "atmosphere"
    val glassOn = s.wallpaper.glass
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        EffectToggle(
            label = "Atmosphere",
            on = atmosphereOn,
            onClick = {
                if (atmosphereOn) {
                    val other = WallpaperRegistry.all.firstOrNull { it.id != "atmosphere" }
                    if (other != null) env.updateWallpaper { it.withWallpaper(other) }
                } else {
                    val atmosphere = WallpaperRegistry["atmosphere"]
                    env.updateWallpaper { it.withWallpaper(atmosphere) }
                }
            },
            icon = { tint -> WavesIcon(tint) },
        )
        EffectToggle(
            label = "Glass",
            on = glassOn,
            onClick = { env.updateWallpaper { it.copy(glass = !it.glass) } },
            icon = { tint -> GlassIcon(tint) },
        )
    }
    Caption(
        "Atmosphere and Glass apply to the home screen wallpaper. The system lock screen is not changed.",
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
private fun EffectToggle(label: String, on: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val colors = DotTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberDotHaptics()
    Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(60.dp)
                .pressScale(interaction)
                .clip(CircleShape)
                .background(if (on) colors.onSurface else Color.Transparent)
                .then(if (on) Modifier else Modifier.border(1.dp, colors.hairline, CircleShape))
                .clickable(interactionSource = interaction, indication = null, role = Role.Switch) {
                    haptics.toggle()
                    onClick()
                }
                .semantics { stateDescription = if (on) "On" else "Off" },
            contentAlignment = Alignment.Center,
        ) {
            icon(if (on) colors.background else colors.onSurface)
        }
        Text(
            label,
            style = DotTheme.type.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            if (on) "On" else "Off",
            style = DotTheme.type.iconLabel,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MoreWallpapersRow(env: SettingsEnv) {
    val colors = DotTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) colors.surfaceRaised.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { env.navigate(Routes.WALLPAPERS) },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoreRingIcon()
        Spacer(Modifier.width(10.dp))
        Text("More wallpapers", style = DotTheme.type.body.copy(fontWeight = FontWeight.Medium), color = colors.onSurface)
    }
}

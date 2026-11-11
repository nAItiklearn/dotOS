package com.dotos.feature.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.data.settings.WallpaperConfig
import com.dotos.core.designsystem.DotTheme
import com.dotos.feature.settings.nav.Routes
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.IMAGE_WALLPAPER_ID
import com.dotos.feature.settings.ui.PlusIcon
import com.dotos.feature.settings.ui.SelectableFrame
import com.dotos.feature.settings.ui.SettingsScaffold
import com.dotos.feature.settings.ui.TuneIcon
import com.dotos.feature.settings.ui.GallerySectionOrder
import com.dotos.feature.settings.ui.bottomInsetPadding
import com.dotos.feature.settings.ui.gallerySection
import com.dotos.feature.settings.ui.rememberPhotoPicker
import com.dotos.feature.settings.ui.thumbConfig
import com.dotos.feature.settings.ui.withWallpaper
import com.dotos.feature.wallpaper.engine.WallpaperLayer
import com.dotos.feature.wallpaper.engine.WallpaperRegistry

/** Gallery: 3-column grid of tall live previews, grouped by section. Tapping one applies it and opens the editor. */
@Composable
internal fun WallpaperGalleryScreen(env: SettingsEnv, s: LauncherSettings) {
    val pickPhoto = rememberPhotoPicker(env)
    val groups = remember { WallpaperRegistry.all.groupBy { gallerySection(it.id) } }
    val bottom = bottomInsetPadding().calculateBottomPadding()
    SettingsScaffold(title = "Wallpaper gallery", onBack = env.back, scroll = false) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "h_studio", span = { GridItemSpan(maxLineSpan) }) { GalleryHeader("Studio") }
            item(key = "customise") {
                GalleryCard(label = "Customise", selected = false, onClick = { env.navigate(Routes.WALLPAPER_EDITOR) }) {
                    WallpaperLayer(config = s.wallpaper, animate = false, allowGlass = true)
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) { TuneIcon(color = Color.White) }
                }
            }
            if (s.wallpaper.imagePath != null) {
                item(key = "photo") {
                    val selected = s.wallpaper.id == IMAGE_WALLPAPER_ID
                    GalleryCard(
                        label = "Your photo",
                        selected = selected,
                        onClick = {
                            if (!selected) env.updateWallpaper { it.copy(id = IMAGE_WALLPAPER_ID) }
                            env.navigate(Routes.WALLPAPER_EDITOR)
                        },
                    ) {
                        WallpaperLayer(
                            config = WallpaperConfig(id = IMAGE_WALLPAPER_ID, imagePath = s.wallpaper.imagePath),
                            animate = false,
                            allowGlass = false,
                        )
                    }
                }
            }
            item(key = "choose_photo") {
                GalleryCard(label = "Choose photo", selected = false, onClick = pickPhoto) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(DotTheme.colors.surface),
                        contentAlignment = Alignment.Center,
                    ) { PlusIcon() }
                }
            }
            for (section in GallerySectionOrder) {
                val list = groups[section] ?: continue
                item(key = "h_$section", span = { GridItemSpan(maxLineSpan) }) { GalleryHeader(section) }
                items(list, key = { it.id }) { wp ->
                    val selected = s.wallpaper.id == wp.id
                    val thumb = remember(wp.id) { thumbConfig(wp) }
                    GalleryCard(
                        label = wp.name,
                        selected = selected,
                        onClick = {
                            if (!selected) env.updateWallpaper { it.withWallpaper(wp) }
                            env.navigate(Routes.WALLPAPER_EDITOR)
                        },
                    ) {
                        WallpaperLayer(config = if (selected) s.wallpaper else thumb, animate = false, allowGlass = selected)
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryHeader(text: String) {
    Text(
        text.uppercase(),
        style = DotTheme.type.sectionLabel,
        color = DotTheme.colors.onSurfaceMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun GalleryCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = DotTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SelectableFrame(
            selected = selected,
            radius = 20.dp,
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 19.5f),
            description = label,
            content = content,
        )
        Text(
            label,
            style = DotTheme.type.iconLabel,
            color = if (selected) colors.onSurface else colors.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

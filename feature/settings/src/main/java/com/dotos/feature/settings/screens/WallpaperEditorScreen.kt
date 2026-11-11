package com.dotos.feature.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotos.core.data.db.WallpaperPresetEntity
import com.dotos.core.data.settings.AnimationSpeed
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.data.settings.SettingsCodec
import com.dotos.core.data.settings.WallpaperConfig
import com.dotos.core.designsystem.DotTheme
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.CloseIcon
import com.dotos.feature.settings.ui.ColorPickerPanel
import com.dotos.feature.settings.ui.HomePreview
import com.dotos.feature.settings.ui.IMAGE_WALLPAPER_ID
import com.dotos.feature.settings.ui.IntSliderRow
import com.dotos.feature.settings.ui.NameDialog
import com.dotos.feature.settings.ui.NavRow
import com.dotos.feature.settings.ui.OptionSegmented
import com.dotos.feature.settings.ui.PillButton
import com.dotos.feature.settings.ui.RoundOption
import com.dotos.feature.settings.ui.ScreenPadding
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsRow
import com.dotos.feature.settings.ui.SettingsScaffold
import com.dotos.feature.settings.ui.SliderRow
import com.dotos.feature.settings.ui.ToggleRow
import com.dotos.feature.settings.ui.decimalText
import com.dotos.feature.settings.ui.percentText
import com.dotos.feature.settings.ui.randomized
import com.dotos.feature.settings.ui.rememberPhotoPicker
import com.dotos.feature.settings.ui.toArgbLong
import com.dotos.feature.settings.ui.toneOnly
import com.dotos.feature.settings.ui.withPreset
import com.dotos.feature.wallpaper.engine.WallpaperPreset
import com.dotos.feature.wallpaper.engine.resolve
import kotlin.math.roundToInt

/** Wallpaper customiser (CLAUDE.md 5.2): live preview, presets, sliders, colours, animation, effects, saved presets. */
@Composable
internal fun WallpaperEditorScreen(env: SettingsEnv, s: LauncherSettings) {
    val cfg = s.wallpaper
    val isPhoto = cfg.id == IMAGE_WALLPAPER_ID
    val resolved = remember(cfg) { cfg.resolve() }
    val wp = resolved.wallpaper
    var editingSlot by rememberSaveable { mutableStateOf<String?>(null) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    val presetDao = remember(env.deps) { env.deps.database.wallpaperPresets() }
    val saved by remember(presetDao) { presetDao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val pickPhoto = rememberPhotoPicker(env)

    SettingsScaffold(title = if (isPhoto) "Your photo" else wp.name, onBack = env.back) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            HomePreview(deps = env.deps, s = s, modifier = Modifier.width(184.dp), animate = true, maxFps = 30)
        }

        if (!isPhoto) {
            SectionHeader("Presets")
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(wp.presets, key = { it.id }) { preset ->
                    PresetChip(
                        preset = preset,
                        selected = cfg.presetId == preset.id,
                        onClick = {
                            editingSlot = null
                            env.updateWallpaper { it.withPreset(preset) }
                        },
                    )
                }
            }

            SectionHeader("Look")
            wp.params.forEach { p ->
                key(p.id) {
                    val value = resolved.params[p.id] ?: p.default
                    val steps = if (p.step > 0f) (((p.max - p.min) / p.step).roundToInt() - 1).coerceAtLeast(0) else 0
                    SliderRow(
                        title = p.label,
                        value = value,
                        onValueChange = { v ->
                            val snapped = if (p.step > 0f) p.min + ((v - p.min) / p.step).roundToInt() * p.step else v
                            env.updateWallpaper { it.copy(params = it.params + (p.id to snapped)) }
                        },
                        valueRange = p.min..p.max,
                        valueText = { decimalText(it, 2) },
                        steps = steps,
                    )
                }
            }

            SectionHeader("Colours")
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(wp.colorSlots, key = { it.id }) { slot ->
                    RoundOption(
                        label = slot.label,
                        selected = editingSlot == slot.id,
                        onClick = { editingSlot = if (editingSlot == slot.id) null else slot.id },
                        size = 52.dp,
                        fill = resolved.colors[slot.id] ?: Color.Gray,
                    )
                }
            }
            val slotId = editingSlot
            if (slotId != null && wp.colorSlots.any { it.id == slotId }) {
                key(slotId, cfg.presetId) {
                    ColorPickerPanel(
                        color = resolved.colors[slotId] ?: Color.Gray,
                        onColorChange = { c -> env.updateWallpaper { it.copy(colors = it.colors + (slotId to c.toArgbLong())) } },
                    )
                }
            }
            if (cfg.colors.keys.any { it != "tone" }) {
                Row(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)) {
                    PillButton(
                        text = "Reset colours",
                        onClick = {
                            editingSlot = null
                            env.updateWallpaper { it.copy(colors = it.toneOnly()) }
                        },
                        filled = false,
                    )
                }
            }

            if (wp.animated) {
                SectionHeader("Animation")
                OptionSegmented(
                    options = AnimationSpeed.entries,
                    selected = cfg.animation,
                    label = {
                        when (it) {
                            AnimationSpeed.OFF -> "Off"
                            AnimationSpeed.SLOW -> "Slow"
                            AnimationSpeed.NORMAL -> "Normal"
                            AnimationSpeed.FAST -> "Fast"
                        }
                    },
                    onSelect = { v -> env.updateWallpaper { it.copy(animation = v) } },
                )
                ToggleRow(
                    title = "Only while charging",
                    description = "Animate the wallpaper only when the phone is plugged in",
                    checked = cfg.animateOnlyWhileCharging,
                    onCheckedChange = { v -> env.updateWallpaper { it.copy(animateOnlyWhileCharging = v) } },
                )
                ToggleRow(
                    title = "Pause in battery saver",
                    description = "Freeze the wallpaper when battery saver is on",
                    checked = cfg.pauseInBatterySaver,
                    onCheckedChange = { v -> env.updateWallpaper { it.copy(pauseInBatterySaver = v) } },
                )
            }
        } else {
            NavRow(title = "Replace photo", subtitle = "Pick another picture with the system Photo Picker", onClick = pickPhoto)
        }

        SectionHeader("Depth")
        SliderRow(
            title = "Parallax",
            value = cfg.parallax,
            onValueChange = { v -> env.updateWallpaper { it.copy(parallax = v) } },
            valueRange = 0f..1f,
            valueText = ::percentText,
            description = "How far the wallpaper moves when you tilt the phone",
        )
        ToggleRow(
            title = "Scroll parallax",
            description = "Shift the wallpaper as you swipe between pages",
            checked = cfg.scrollParallax,
            onCheckedChange = { v -> env.updateWallpaper { it.copy(scrollParallax = v) } },
        )

        SectionHeader("Glass")
        ToggleRow(
            title = "Glass",
            description = "Ribbed-glass finish on top of the wallpaper",
            checked = cfg.glass,
            onCheckedChange = { v -> env.updateWallpaper { it.copy(glass = v) } },
        )
        if (cfg.glass) {
            IntSliderRow(
                title = "Flutes",
                value = cfg.glassStripes.roundToInt(),
                onValueChange = { v -> env.updateWallpaper { it.copy(glassStripes = v.toFloat()) } },
                range = 4..80,
                description = "Number of glass ribs across the screen",
            )
        }

        SectionHeader("Blur and dim")
        SliderRow(
            title = "Home blur",
            value = s.homeBlur,
            onValueChange = { v -> env.update { it.copy(homeBlur = v) } },
            valueRange = 0f..48f,
            valueText = { "${it.roundToInt()} dp" },
            enabled = !s.disableBlur,
        )
        SliderRow(
            title = "Home dim",
            value = s.homeDim,
            onValueChange = { v -> env.update { it.copy(homeDim = v) } },
            valueRange = 0f..0.9f,
            valueText = ::percentText,
        )
        SliderRow(
            title = "Drawer blur",
            value = s.drawerBlur,
            onValueChange = { v -> env.update { it.copy(drawerBlur = v) } },
            valueRange = 0f..48f,
            valueText = { "${it.roundToInt()} dp" },
            enabled = !s.disableBlur,
        )
        SliderRow(
            title = "Drawer dim",
            value = s.drawerDim,
            onValueChange = { v -> env.update { it.copy(drawerDim = v) } },
            valueRange = 0f..0.9f,
            valueText = ::percentText,
        )
        if (s.disableBlur) Caption("Blur is switched off in Performance settings.")

        if (!isPhoto) {
            SectionHeader("Make it yours")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillButton(
                    text = "Randomise",
                    onClick = {
                        editingSlot = null
                        env.updateWallpaper { randomized(it) }
                    },
                    modifier = Modifier.weight(1f),
                    filled = false,
                )
                PillButton(
                    text = "Save preset",
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.weight(1f),
                )
            }

            if (saved.isNotEmpty()) {
                SectionHeader("My presets")
                saved.forEach { entity ->
                    key(entity.id) {
                        SavedPresetRow(
                            entity = entity,
                            onApply = {
                                val loaded = decodeConfig(entity.configJson)
                                if (loaded != null) env.updateWallpaper { loaded }
                            },
                            onDelete = { env.launchIO { presetDao.delete(entity) } },
                        )
                    }
                }
            }
        }

        SectionHeader("System-wide")
        NavRow(
            title = "Live wallpaper",
            subtitle = "Use the DotOS wallpaper on the whole system, not just this launcher",
            onClick = { env.deps.openLiveWallpaperPicker() },
        )
        Caption("The lock screen can only show a static picture; third-party launchers cannot restyle it.")
    }

    if (showSaveDialog) {
        NameDialog(
            title = "Save preset",
            initial = "My preset ${saved.size + 1}",
            confirmLabel = "Save",
            onConfirm = { name ->
                showSaveDialog = false
                val json = SettingsCodec.json.encodeToString(WallpaperConfig.serializer(), cfg)
                env.launchIO {
                    presetDao.insert(WallpaperPresetEntity(name = name, configJson = json, createdAt = System.currentTimeMillis()))
                }
            },
            onDismiss = { showSaveDialog = false },
        )
    }
}

private fun decodeConfig(json: String): WallpaperConfig? =
    try {
        SettingsCodec.json.decodeFromString(WallpaperConfig.serializer(), json)
    } catch (e: Exception) {
        null
    }

@Composable
private fun PresetChip(preset: WallpaperPreset, selected: Boolean, onClick: () -> Unit) {
    val colors = DotTheme.colors
    val dots = remember(preset) { preset.colors.values.take(3).toList() }
    Row(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (selected) colors.onSurface else colors.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, colors.hairline, CircleShape))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            dots.forEach { c ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(c.copy(alpha = 1f))
                        .border(1.dp, if (selected) colors.background.copy(alpha = 0.4f) else colors.hairline, CircleShape),
                )
            }
        }
        Text(
            preset.name,
            style = DotTheme.type.bodySmall,
            color = if (selected) colors.background else colors.onSurface,
        )
    }
}

@Composable
private fun SavedPresetRow(entity: WallpaperPresetEntity, onApply: () -> Unit, onDelete: () -> Unit) {
    SettingsRow(
        title = entity.name,
        onClick = onApply,
        trailing = {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDelete)
                    .semantics { contentDescription = "Delete preset ${entity.name}" },
                contentAlignment = Alignment.Center,
            ) { CloseIcon() }
        },
    )
}

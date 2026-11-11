package com.dotos.feature.settings.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dotos.core.data.settings.DotShape
import com.dotos.core.data.settings.GlyphTint
import com.dotos.core.data.settings.IconStyle
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.data.settings.PlateColor
import com.dotos.core.data.settings.PlateShape
import com.dotos.core.designsystem.DotTheme
import com.dotos.feature.icons.drawPlate
import com.dotos.feature.icons.engine.IconPackInfo
import com.dotos.feature.icons.engine.color
import com.dotos.feature.settings.nav.Routes
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.HomePreview
import com.dotos.feature.settings.ui.IconPackIcon
import com.dotos.feature.settings.ui.IntSliderRow
import com.dotos.feature.settings.ui.NavRow
import com.dotos.feature.settings.ui.OptionSegmented
import com.dotos.feature.settings.ui.RoundOption
import com.dotos.feature.settings.ui.ScreenPadding
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsScaffold
import com.dotos.feature.settings.ui.SliderRow
import com.dotos.feature.settings.ui.percentText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private fun IconStyle.label(): String = when (this) {
    IconStyle.LINE -> "Line"
    IconStyle.DOT -> "Dot"
    IconStyle.ORIGINAL -> "Original"
    IconStyle.THEMED -> "Themed"
}

private fun PlateShape.label(): String = when (this) {
    PlateShape.NONE -> "None"
    PlateShape.CIRCLE -> "Circle"
    PlateShape.SQUIRCLE -> "Squircle"
    PlateShape.ROUNDED -> "Rounded"
}

private fun PlateColor.label(): String = when (this) {
    PlateColor.DARK -> "Dark"
    PlateColor.BLACK -> "Black"
    PlateColor.WHITE -> "White"
    PlateColor.ACCENT -> "Accent"
    PlateColor.TRANSPARENT -> "Clear"
}

private fun GlyphTint.label(): String = when (this) {
    GlyphTint.WHITE -> "White"
    GlyphTint.BLACK -> "Black"
    GlyphTint.ACCENT -> "Accent"
}

private fun DotShape.label(): String = when (this) {
    DotShape.ROUND -> "Round"
    DotShape.SQUARE -> "Square"
    DotShape.RING -> "Ring"
}

private fun isDefaultIconLook(s: LauncherSettings): Boolean {
    val d = LauncherSettings()
    return s.iconStyle == d.iconStyle && s.plateShape == d.plateShape && s.plateColor == d.plateColor &&
        s.glyphTint == d.glyphTint && s.iconScale == d.iconScale && s.dotDensity == d.dotDensity &&
        s.dotShape == d.dotShape && s.iconPackPackage == null
}

/** Icon Pack (reference: preview, one row of round options, then detailed styling). */
@Composable
internal fun IconPackScreen(env: SettingsEnv, s: LauncherSettings) {
    val packs by produceState(emptyList<IconPackInfo>()) {
        value = withContext(Dispatchers.IO) {
            try {
                env.deps.icons.installedIconPacks()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    val isDefault = isDefaultIconLook(s)

    SettingsScaffold(title = "Icon Pack", onBack = env.back) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            HomePreview(deps = env.deps, s = s, modifier = Modifier.width(184.dp), extraIcons = 6)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "default") {
                RoundOption(
                    label = "Default",
                    selected = isDefault,
                    onClick = {
                        val d = LauncherSettings()
                        env.update {
                            it.copy(
                                iconStyle = d.iconStyle,
                                plateShape = d.plateShape,
                                plateColor = d.plateColor,
                                glyphTint = d.glyphTint,
                                iconScale = d.iconScale,
                                dotDensity = d.dotDensity,
                                dotShape = d.dotShape,
                                iconPackPackage = null,
                            )
                        }
                    },
                ) { IconPackIcon() }
            }
            items(IconStyle.entries, key = { "style_" + it.name }) { style ->
                RoundOption(
                    label = style.label(),
                    selected = !isDefault && s.iconPackPackage == null && s.iconStyle == style,
                    onClick = { env.update { it.copy(iconStyle = style, iconPackPackage = null) } },
                ) { StyleSample(style) }
            }
            items(packs, key = { "pack_" + it.packageName }) { pack ->
                RoundOption(
                    label = pack.label,
                    selected = s.iconPackPackage == pack.packageName,
                    onClick = { env.update { it.copy(iconPackPackage = pack.packageName, iconStyle = IconStyle.ORIGINAL) } },
                ) { PackIcon(pack.packageName) }
            }
        }
        if (packs.isEmpty()) {
            Caption("Install an ADW / Nova compatible icon pack and it will show up here.", Modifier.padding(top = 8.dp))
        }

        SectionHeader("Plate shape")
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(PlateShape.entries, key = { it.name }) { shape ->
                RoundOption(
                    label = shape.label(),
                    selected = s.plateShape == shape,
                    onClick = { env.update { it.copy(plateShape = shape) } },
                ) { ShapeSample(shape) }
            }
        }

        SectionHeader("Plate colour")
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(PlateColor.entries, key = { it.name }) { plate ->
                val fill = when (plate) {
                    PlateColor.DARK -> Color(0xFF1A1A1A)
                    PlateColor.BLACK -> Color(0xFF000000)
                    PlateColor.WHITE -> Color(0xFFF2F2F2)
                    PlateColor.ACCENT -> s.accent.color()
                    PlateColor.TRANSPARENT -> Color.Transparent
                }
                RoundOption(
                    label = plate.label(),
                    selected = s.plateColor == plate,
                    onClick = { env.update { it.copy(plateColor = plate) } },
                    fill = fill,
                ) {
                    if (plate == PlateColor.TRANSPARENT) DashedRing()
                }
            }
        }

        SectionHeader("Glyph colour")
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(GlyphTint.entries, key = { it.name }) { tint ->
                val fill = when (tint) {
                    GlyphTint.WHITE -> Color.White
                    GlyphTint.BLACK -> Color.Black
                    GlyphTint.ACCENT -> s.accent.color()
                }
                RoundOption(
                    label = tint.label(),
                    selected = s.glyphTint == tint,
                    onClick = { env.update { it.copy(glyphTint = tint) } },
                    fill = fill,
                )
            }
        }

        SectionHeader("Size")
        SliderRow(
            title = "Icon size",
            value = s.iconScale,
            onValueChange = { v -> env.update { it.copy(iconScale = (v * 20f).roundToInt() / 20f) } },
            valueRange = 0.7f..1.3f,
            valueText = ::percentText,
            steps = 11,
            description = "Scale the symbol inside each plate",
        )

        if (s.iconStyle == IconStyle.DOT) {
            SectionHeader("Dot style")
            IntSliderRow(
                title = "Dot density",
                value = s.dotDensity,
                onValueChange = { v -> env.update { it.copy(dotDensity = v) } },
                range = 12..24,
                description = "More dots make a sharper dot-matrix icon",
            )
            OptionSegmented(
                options = DotShape.entries,
                selected = s.dotShape,
                label = { it.label() },
                onSelect = { shape -> env.update { it.copy(dotShape = shape) } },
            )
        }

        SectionHeader("Per app")
        NavRow(
            title = "Per-app icons",
            subtitle = "Choose a different icon for a single app",
            onClick = { env.navigate(Routes.ICON_OVERRIDES) },
        )
    }
}

@Composable
internal fun StyleSample(style: IconStyle) {
    val colors = DotTheme.colors
    Canvas(Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        when (style) {
            IconStyle.LINE -> {
                drawCircle(colors.onSurface, radius = w * 0.34f, style = Stroke(width = 2.dp.toPx()))
                drawCircle(colors.onSurface, radius = w * 0.08f)
            }
            IconStyle.DOT -> {
                for (x in 0..2) {
                    for (y in 0..2) {
                        drawCircle(colors.onSurface, radius = w * 0.07f, center = Offset(w * (0.22f + 0.28f * x), h * (0.22f + 0.28f * y)))
                    }
                }
            }
            IconStyle.ORIGINAL -> {
                val palette = listOf(Color(0xFFD71921), Color(0xFF3DDC84), Color(0xFF4C8DFF), Color(0xFFFFB020))
                for (i in 0..3) {
                    drawCircle(palette[i], radius = w * 0.17f, center = Offset(w * (0.3f + 0.4f * (i % 2)), h * (0.3f + 0.4f * (i / 2))))
                }
            }
            IconStyle.THEMED -> {
                drawCircle(colors.onSurfaceMuted, radius = w * 0.36f)
                drawCircle(colors.background, radius = w * 0.12f)
            }
        }
    }
}

@Composable
private fun ShapeSample(shape: PlateShape) {
    val colors = DotTheme.colors
    if (shape == PlateShape.NONE) {
        DashedRing()
    } else {
        Canvas(Modifier.size(28.dp)) { drawPlate(shape, colors.onSurface) }
    }
}

@Composable
private fun DashedRing() {
    val colors = DotTheme.colors
    Canvas(Modifier.size(28.dp)) {
        drawCircle(
            colors.onSurfaceMuted,
            radius = size.minDimension / 2f - 1.dp.toPx(),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
        )
    }
}

/** The installed pack's own launcher icon. */
@Composable
private fun PackIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize().padding(10.dp))
    }
}

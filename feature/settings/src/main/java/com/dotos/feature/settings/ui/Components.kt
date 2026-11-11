package com.dotos.feature.settings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dotos.core.designsystem.DotPalette
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.DotCard
import com.dotos.core.designsystem.component.DotSwitch
import com.dotos.core.designsystem.component.pressScale
import com.dotos.core.designsystem.rememberDotHaptics

internal val ScreenPadding = 20.dp

/** Bottom padding that keeps list content clear of the navigation bar / keyboard. */
@Composable
internal fun bottomInsetPadding(extra: Dp = 24.dp): PaddingValues {
    val bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    return PaddingValues(bottom = bottom + extra)
}

/** Fill of rounded panels (wallpaper strip, grouped cards): slightly lifted from pure black. */
@Composable
internal fun panelColor(): Color = if (DotTheme.colors.isDark) Color(0xFF111111) else DotTheme.colors.surface

/**
 * Standard screen frame: back arrow + centred title, edge-to-edge scrolling body that respects
 * system bars. With [scroll] = false the body is a plain column (for screens hosting a Lazy list).
 */
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DotTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
    ) {
        SettingsTopBar(title = title, onBack = onBack, trailing = trailing)
        if (scroll) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
                Spacer(Modifier.height(32.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun SettingsTopBar(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val colors = DotTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        BackButton(onBack, Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
        Text(
            title,
            style = DotTheme.type.title.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 64.dp),
        )
        if (trailing != null) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
            ) { trailing() }
        }
    }
}

@Composable
internal fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DotTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(48.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val sw = 2.dp.toPx()
            val w = size.width
            val h = size.height
            val c = colors.onSurface
            drawLine(c, Offset(w * 0.92f, h / 2f), Offset(w * 0.08f, h / 2f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(w * 0.08f, h / 2f), Offset(w * 0.46f, h * 0.12f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(w * 0.08f, h / 2f), Offset(w * 0.46f, h * 0.88f), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

/** Generic settings row: min 56 dp, 20 dp sides, subtle raised background while pressed. */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
) {
    val colors = DotTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = if (pressed && onClick != null) colors.surfaceRaised.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(90),
        label = "rowBg",
    )
    Row(
        modifier
            .fillMaxWidth()
            .background(bg)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = ScreenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = DotTheme.type.title,
                color = if (titleColor == Color.Unspecified) colors.onSurface else titleColor,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = DotTheme.type.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Title + one-line description + dot switch. The whole row toggles. */
@Composable
internal fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = rememberDotHaptics()
    val click: (() -> Unit)? = if (enabled) {
        {
            haptics.toggle()
            onCheckedChange(!checked)
        }
    } else {
        null
    }
    SettingsRow(
        title = title,
        subtitle = description,
        modifier = modifier,
        onClick = click,
        trailing = { DotSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
    )
}

/** Row that shows its current value on the right and a chevron; opens another screen. */
@Composable
internal fun NavRow(title: String, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier, value: String? = null) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    Text(value, style = DotTheme.type.bodySmall, color = DotTheme.colors.onSurfaceMuted)
                    Spacer(Modifier.width(10.dp))
                }
                ChevronIcon()
            }
        },
    )
}

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = DotTheme.type.sectionLabel,
        color = DotTheme.colors.onSurfaceMuted,
        modifier = modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 28.dp, bottom = 8.dp),
    )
}

@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = DotTheme.type.bodySmall,
        color = DotTheme.colors.onSurfaceMuted,
        modifier = modifier.padding(horizontal = ScreenPadding, vertical = 6.dp),
    )
}

/** Pill button. [filled] = solid, otherwise outlined. [danger] uses signal red. */
@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val colors = DotTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val container = when {
        !filled -> Color.Transparent
        danger -> DotPalette.SignalRed
        else -> colors.onSurface
    }
    val content = when {
        !filled -> if (danger) DotPalette.SignalRed else colors.onSurface
        danger -> Color.White
        else -> colors.background
    }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 96.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(container)
            .then(
                if (filled) {
                    Modifier
                } else {
                    Modifier.border(1.dp, if (danger) DotPalette.SignalRed else colors.hairline, CircleShape)
                },
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = DotTheme.type.body.copy(fontWeight = FontWeight.Medium),
            color = content.copy(alpha = if (enabled) 1f else 0.4f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Round option with an optional label underneath. Selected = 2 dp ring with a 3 dp gap
 * (used for icon packs, colour swatches, wallpaper choices).
 */
@Composable
internal fun RoundOption(
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    fill: Color = DotTheme.colors.surface,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = DotTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.width(size + 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .pressScale(interaction)
                .border(2.dp, if (selected) colors.onSurface else Color.Transparent, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(fill)
                .then(if (selected) Modifier else Modifier.border(1.dp, colors.hairline, CircleShape))
                .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
                .semantics {
                    this.selected = selected
                    if (label != null) contentDescription = label
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
        if (label != null) {
            Text(
                label,
                style = DotTheme.type.iconLabel,
                color = if (selected) colors.onSurface else colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Portrait phone-shaped card (9 : 19.5) with rounded corners. Content is clipped to it. */
@Composable
internal fun PhoneMock(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = DotTheme.colors
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .aspectRatio(9f / 19.5f)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.onSurface else colors.hairline, shape)
            .clip(shape)
            .background(Color.Black),
        content = content,
    )
}

@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = DotTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(colors.surfaceRaised)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MagnifierIcon(Modifier.size(18.dp), colors.onSurfaceMuted)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = DotTheme.type.body, color = colors.onSurfaceMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = DotTheme.type.body.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onValueChange("") }
                    .semantics { contentDescription = "Clear" },
                contentAlignment = Alignment.Center,
            ) { CloseIcon() }
        }
    }
}

/** Radio-style list of options with an optional description under each title. */
@Composable
internal fun <T> ChoiceRows(
    options: List<T>,
    selected: T,
    title: (T) -> String,
    description: (T) -> String?,
    onSelect: (T) -> Unit,
) {
    val haptics = rememberDotHaptics()
    options.forEach { option ->
        val isSelected = option == selected
        SettingsRow(
            title = title(option),
            subtitle = description(option),
            onClick = {
                haptics.tick()
                onSelect(option)
            },
            trailing = { RadioDot(isSelected) },
        )
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
) {
    val colors = DotTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        DotCard(radius = 28.dp, color = colors.surface) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = DotTheme.type.title, color = colors.onSurface)
                Text(
                    message,
                    style = DotTheme.type.body,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), danger = danger)
                    PillButton("Cancel", onDismiss, Modifier.fillMaxWidth(), filled = false)
                }
            }
        }
    }
}

/** Small dialog with one text field (used to name a saved wallpaper preset). */
@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DotTheme.colors
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        DotCard(radius = 28.dp, color = colors.surface) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = DotTheme.type.title, color = colors.onSurface)
                Box(
                    Modifier
                        .padding(top = 16.dp, bottom = 20.dp)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceRaised)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { if (it.length <= 32) text = it },
                        singleLine = true,
                        textStyle = DotTheme.type.body.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(confirmLabel, { onConfirm(text.trim()) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
                    PillButton("Cancel", onDismiss, Modifier.fillMaxWidth(), filled = false)
                }
            }
        }
    }
}

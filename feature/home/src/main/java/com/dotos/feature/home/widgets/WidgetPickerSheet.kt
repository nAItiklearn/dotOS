package com.dotos.feature.home.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Process
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.DotLoading
import com.dotos.core.designsystem.component.pressScale
import com.dotos.feature.widgets.DotWidgets
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.WidgetSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PICKER_HEIGHT_FRACTION = 0.9f

/** Columns assumed when labelling third-party widget sizes (the smallest home grid). */
private const val LABEL_MAX_COLUMNS = 4

private val CardRadius = 24.dp
private val AppIconSize = 24.dp

/**
 * Bottom sheet listing DotOS widgets first (live previews, tap for the face / size carousel), then
 * third-party widgets grouped by app. Renders nothing while [visible] is false.
 *
 * Picking a widget invokes the matching callback and then slides the sheet away, calling [onDismiss]
 * once it is gone. [onDismiss] is also called for swipe-down, scrim tap and back.
 */
@Composable
fun WidgetPickerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPickDotWidget: (kind: String, face: String, size: WidgetSize) -> Unit,
    onPickAppWidget: (AppWidgetProviderInfo) -> Unit,
    controller: AppWidgetController,
) {
    if (!visible) return
    PickerSheet(
        onDismiss = onDismiss,
        onPickDotWidget = onPickDotWidget,
        onPickAppWidget = onPickAppWidget,
        controller = controller,
    )
}

@Composable
private fun PickerSheet(
    onDismiss: () -> Unit,
    onPickDotWidget: (kind: String, face: String, size: WidgetSize) -> Unit,
    onPickAppWidget: (AppWidgetProviderInfo) -> Unit,
    controller: AppWidgetController,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    var query by rememberSaveable { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf<String?>(null) }

    val dotProviders = remember { DotWidgets.all.filter { it.sizes.isNotEmpty() } }
    val loaded by produceState<List<AppWidgetEntry>?>(null, controller) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadEntries(context, controller) }.getOrDefault(emptyList())
        }
    }
    val items = remember(query, dotProviders, loaded) { buildItems(query, dotProviders, loaded) }

    val closeAll: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    LaunchedEffect(query) { gridState.scrollToItem(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = SheetColors.Background,
        scrimColor = SheetColors.Scrim,
        dragHandle = { SheetDragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(PICKER_HEIGHT_FRACTION),
        ) {
            SearchPill(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = items,
                    key = { it.key },
                    span = { item -> if (item.fullWidth) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                    contentType = { it::class },
                ) { item ->
                    when (item) {
                        is PickerItem.Header -> SectionHeader(item.title, item.iconInfo, controller)
                        is PickerItem.Dot -> DotWidgetCard(item.provider) {
                            focusManager.clearFocus()
                            selectedKind = item.provider.kind
                        }
                        is PickerItem.App -> AppWidgetCard(item.entry, controller) {
                            onPickAppWidget(item.entry.info)
                            closeAll()
                        }
                        is PickerItem.Message -> Text(
                            text = item.text,
                            style = DotTheme.type.body,
                            color = SheetColors.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                        )
                        PickerItem.Loading -> Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            DotLoading(color = SheetColors.Muted)
                        }
                    }
                }
                item(key = "bottom-inset", span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(
                        Modifier
                            .navigationBarsPadding()
                            .height(16.dp),
                    )
                }
            }
        }
    }

    val selected = selectedKind?.let { kind -> dotProviders.firstOrNull { it.kind == kind } }
    if (selected != null) {
        WidgetFaceSheet(
            provider = selected,
            onAdd = { face, size ->
                onPickDotWidget(selected.kind, face.id, size)
                closeAll()
            },
            onClose = { selectedKind = null },
        )
    }
}

// ---------------------------------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------------------------------

/** A third-party widget provider with everything the picker needs, resolved off the main thread. */
@Immutable
private class AppWidgetEntry(
    val info: AppWidgetProviderInfo,
    val key: String,
    val groupKey: String,
    val appLabel: String,
    val widgetLabel: String,
    val span: WidgetSize,
)

private sealed class PickerItem(val key: String, val fullWidth: Boolean) {
    class Header(key: String, val title: String, val iconInfo: AppWidgetProviderInfo?) : PickerItem(key, true)
    class Dot(val provider: DotWidgetProvider) : PickerItem("dot:" + provider.kind, false)
    class App(val entry: AppWidgetEntry) : PickerItem("app:" + entry.key, false)
    class Message(key: String, val text: String) : PickerItem(key, true)
    object Loading : PickerItem("loading", true)
}

/** Blocking: resolves labels and sizes for every installed provider. Run on a background dispatcher. */
private fun loadEntries(context: Context, controller: AppWidgetController): List<AppWidgetEntry> {
    val pm = context.packageManager
    val ownPackage = context.packageName
    val myProfile = Process.myUserHandle()
    val result = ArrayList<AppWidgetEntry>()
    for (info in controller.providers()) {
        // Our own Glance widgets are offered natively in the DotOS section.
        if (info.provider.packageName == ownPackage) continue
        if ((info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER) != 0) continue
        val entry = runCatching {
            var appLabel = controller.appLabel(info)
            if (info.profile != myProfile) {
                appLabel = pm.getUserBadgedLabel(appLabel, info.profile).toString()
            }
            AppWidgetEntry(
                info = info,
                key = info.provider.flattenToShortString() + "|" + info.profile,
                groupKey = info.provider.packageName + "|" + info.profile,
                appLabel = appLabel,
                widgetLabel = controller.widgetLabel(info),
                span = spanFor(info, PREVIEW_CELL_WIDTH_DP, PREVIEW_CELL_HEIGHT_DP, LABEL_MAX_COLUMNS),
            )
        }.getOrNull()
        if (entry != null) result.add(entry)
    }
    return result
}

private fun buildItems(
    query: String,
    dots: List<DotWidgetProvider>,
    loaded: List<AppWidgetEntry>?,
): List<PickerItem> {
    val q = query.trim()
    val out = ArrayList<PickerItem>()

    val shownDots = if (q.isEmpty()) dots else dots.filter { it.title.contains(q, ignoreCase = true) }
    if (shownDots.isNotEmpty()) {
        out.add(PickerItem.Header("header:dotos", WidgetPickerStrings.SECTION_DOTOS, null))
        shownDots.mapTo(out) { PickerItem.Dot(it) }
    }

    if (loaded == null) {
        out.add(PickerItem.Loading)
        return out
    }
    val matching = if (q.isEmpty()) {
        loaded
    } else {
        loaded.filter { it.widgetLabel.contains(q, ignoreCase = true) || it.appLabel.contains(q, ignoreCase = true) }
    }
    for ((groupKey, entries) in matching.groupBy { it.groupKey }) {
        val first = entries.first()
        out.add(PickerItem.Header("group:$groupKey", first.appLabel, first.info))
        entries.mapTo(out) { PickerItem.App(it) }
    }
    if (out.isEmpty()) out.add(PickerItem.Message("empty", WidgetPickerStrings.NO_RESULTS))
    return out
}

// ---------------------------------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------------------------------

@Composable
private fun SearchPill(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val textStyle = DotTheme.type.bodySmall.copy(fontSize = 14.sp, color = SheetColors.Ink)
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(SheetColors.Field)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            textStyle = textStyle,
            singleLine = true,
            cursorBrush = SolidColor(SheetColors.Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = WidgetPickerStrings.SEARCH_PLACEHOLDER,
                            style = textStyle,
                            color = SheetColors.Muted,
                            textAlign = if (focused) TextAlign.Start else TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = WidgetPickerStrings.CLEAR_SEARCH, role = Role.Button) {
                        onQueryChange("")
                    },
                contentAlignment = Alignment.Center,
            ) {
                CloseGlyph(Modifier.size(20.dp), SheetColors.Muted)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// Grid items
// ---------------------------------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String, iconInfo: AppWidgetProviderInfo?, controller: AppWidgetController) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconInfo != null) {
            AppGlyph(iconInfo, controller)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = title,
            style = DotTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = SheetColors.Section,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** App icon from PackageManager, decoded off the main thread and cached on the controller. */
@Composable
private fun AppGlyph(info: AppWidgetProviderInfo, controller: AppWidgetController) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { AppIconSize.roundToPx() }
    val key = "icon:" + info.provider.packageName + "|" + info.profile + "|" + sizePx
    val bitmap by produceState<ImageBitmap?>(controller.imageCache[key], key) {
        value = controller.imageCache[key] ?: withContext(Dispatchers.IO) {
            loadAppIcon(context, info, sizePx)?.also { controller.imageCache.put(key, it) }
        }
    }
    val icon = bitmap
    val modifier = Modifier
        .size(AppIconSize)
        .clip(RoundedCornerShape(6.dp))
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = modifier)
    } else {
        Spacer(modifier)
    }
}

/**
 * Rounded card with the preview in its lower right (clipped by the card) and the title underneath.
 * The whole item scales on press and is one accessibility button.
 */
@Composable
private fun WidgetCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(CardRadius))
                .background(SheetColors.Card)
                // The preview is decoration; the title below carries the meaning.
                .clearAndSetSemantics {},
            content = preview,
        )
        Text(
            text = title,
            style = DotTheme.type.bodySmall,
            color = SheetColors.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = DotTheme.type.iconLabel,
                color = SheetColors.Muted,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DotWidgetCard(provider: DotWidgetProvider, onClick: () -> Unit) {
    val face = remember(provider) { facesOf(provider).first() }
    val size = remember(provider) { provider.sizes.first() }
    val real = remember(provider, size) { realSizeOf(provider, size) }
    WidgetCard(title = provider.title, subtitle = null, onClick = onClick) {
        BleedPreview(realWidth = real.width, realHeight = real.height, modifier = Modifier.fillMaxSize()) {
            DotWidgetSurface(provider = provider, faceId = face.id, size = size, modifier = Modifier.fillMaxSize())
        }
        WidgetGlyph(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(14.dp),
            color = SheetColors.Ink.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun AppWidgetCard(entry: AppWidgetEntry, controller: AppWidgetController, onClick: () -> Unit) {
    val context = LocalContext.current
    val densityDpi = LocalConfiguration.current.densityDpi
    val key = "preview:" + entry.key + "|" + densityDpi
    val bitmap by produceState<ImageBitmap?>(controller.imageCache[key], key) {
        value = controller.imageCache[key] ?: withContext(Dispatchers.IO) {
            loadWidgetPreview(context, entry.info, densityDpi)?.also { controller.imageCache.put(key, it) }
        }
    }
    WidgetCard(title = entry.widgetLabel, subtitle = entry.span.toString(), onClick = onClick) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
            )
        } else {
            WidgetGlyph(
                Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
                color = SheetColors.Muted.copy(alpha = 0.5f),
            )
        }
    }
}

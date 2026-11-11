package com.dotos.feature.drawer.library

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.core.launcher.AppInfo
import com.dotos.feature.icons.AppIcon

private val SearchResultIconSize = 44.dp

/** 52 dp pill: canvas magnifier, placeholder, inline clear button. Plain-text, no autocorrect. */
@Composable
internal fun LibrarySearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val type = DotTheme.type
    val shape = RoundedCornerShape(LibraryDims.SearchHeight / 2)
    val muted = contentColor.copy(alpha = 0.55f)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = type.body.copy(color = contentColor),
        cursorBrush = SolidColor(contentColor),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = modifier
            .fillMaxWidth()
            .height(LibraryDims.SearchHeight)
            .clip(shape)
            .background(LibraryColors.Plate, shape)
            .border(1.dp, LibraryColors.Hairline, shape)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .semantics { contentDescription = LibraryText.SEARCH_PLACEHOLDER },
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchGlyph(color = muted)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = LibraryText.SEARCH_PLACEHOLDER,
                            style = type.body,
                            color = muted,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    ClearButton(color = muted, onClick = { onQueryChange("") })
                }
            }
        },
    )
}

/** Magnifier drawn on a Canvas: ring plus a round-capped handle at 45 degrees. */
@Composable
private fun SearchGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val radius = size.minDimension * 0.30f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = stroke))
        val start = center + Offset(radius * 0.7071f, radius * 0.7071f)
        val end = Offset(size.width * 0.9f, size.height * 0.9f)
        drawLine(color = color, start = start, end = end, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun ClearButton(color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = LibraryText.CLEAR_SEARCH, onClick = onClick)
            .semantics { contentDescription = LibraryText.CLEAR_SEARCH },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = 2.dp.toPx()
            drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/**
 * Flat result list for an active query, inside one dark plate. The bottom inset is the larger of the dock
 * padding and the keyboard, so the last row is never hidden by either.
 */
@Composable
internal fun SearchResults(
    query: String,
    results: List<AppInfo>,
    contentColor: Color,
    bottomPadding: Dp,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(top = 56.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                text = LibraryText.NO_RESULTS,
                style = DotTheme.type.body,
                color = contentColor.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
        }
    } else {
        ResultList(query, results, contentColor, bottomPadding, onLaunch, onLongPress, modifier)
    }
}

@Composable
private fun ResultList(
    query: String,
    results: List<AppInfo>,
    contentColor: Color,
    bottomPadding: Dp,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    modifier: Modifier,
) {
    val ime = WindowInsets.ime
    val bottomInsets = remember(ime, bottomPadding) { ime.union(WindowInsets(bottom = bottomPadding)) }
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    val shape = LibraryDims.TileShape

    Box(modifier.windowInsetsPadding(bottomInsets)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(LibraryColors.Plate, shape)
                .border(1.dp, LibraryColors.Hairline, shape),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(results, key = { it.key }, contentType = { "result" }) { app ->
                SearchResultRow(app, contentColor, onLaunch, onLongPress)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(
    app: AppInfo,
    contentColor: Color,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    val bounds = remember { BoundsHolder() }
    val haptics = rememberDotHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .combinedClickable(
                role = Role.Button,
                onLongClick = {
                    haptics.pick()
                    onLongPress(app)
                },
                onClick = { onLaunch(app, bounds.toRect()) },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, Modifier.onGloballyPositioned { bounds.coordinates = it }, SearchResultIconSize)
        Spacer(Modifier.width(16.dp))
        Text(
            text = app.label,
            style = DotTheme.type.body,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

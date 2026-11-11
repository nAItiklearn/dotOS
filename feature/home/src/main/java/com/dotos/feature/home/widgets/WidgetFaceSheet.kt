package com.dotos.feature.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.designsystem.component.DotPageIndicator
import com.dotos.core.designsystem.component.pressScale
import com.dotos.core.designsystem.rememberDotHaptics
import com.dotos.feature.widgets.api.DotWidgetProvider
import com.dotos.feature.widgets.api.WidgetFace
import com.dotos.feature.widgets.api.WidgetSize
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private val PagerHeight = 200.dp
private val PageWidth = 200.dp
private val PageGap = 12.dp
private const val NEIGHBOUR_SCALE = 0.8f
private const val NEIGHBOUR_ALPHA = 0.55f
private const val MAX_INDICATOR_DOTS = 20

/** One page of the carousel: a face rendered at one size. */
@Immutable
private data class FaceOption(val face: WidgetFace, val size: WidgetSize)

/**
 * Face / size carousel for one DotOS widget: a snap carousel of live previews (every face at every size),
 * with the centred option described underneath and an "Add to home" button.
 *
 * [onAdd] is called with the centred option. [onClose] is called once the sheet has closed itself (close
 * button, swipe down, scrim tap, or after adding); it does not mean "nothing was added".
 */
@Composable
internal fun WidgetFaceSheet(
    provider: DotWidgetProvider,
    onAdd: (face: WidgetFace, size: WidgetSize) -> Unit,
    onClose: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberDotHaptics()

    val options = remember(provider) {
        facesOf(provider).flatMap { face -> provider.sizes.map { size -> FaceOption(face, size) } }
    }
    val pagerState = rememberPagerState(pageCount = { options.size })

    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onClose()
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { haptics.tick() }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = SheetColors.Background,
        scrimColor = SheetColors.Scrim,
        dragHandle = { SheetDragHandle() },
    ) {
        // Scrollable so the carousel still reaches the button in landscape / short windows.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            FaceSheetTitle(title = provider.title, onClose = close)

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(PagerHeight),
            ) {
                // Centre the current page; the neighbours peek in from both sides.
                val sidePadding = if (maxWidth > PageWidth) (maxWidth - PageWidth) / 2 else 0.dp
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    pageSize = PageSize.Fixed(PageWidth),
                    pageSpacing = PageGap,
                    key = { index -> "$index:${options[index].face.id}:${options[index].size}" },
                ) { page ->
                    FacePage(
                        provider = provider,
                        option = options[page],
                        page = page,
                        pagerState = pagerState,
                        onClick = {
                            if (page != pagerState.currentPage) {
                                scope.launch { pagerState.animateScrollToPage(page) }
                            }
                        },
                    )
                }
            }

            if (options.size in 2..MAX_INDICATOR_DOTS) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DotPageIndicator(
                        pageCount = options.size,
                        position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                        color = SheetColors.Ink,
                    )
                }
            }

            FaceInfo(options = options, pagerState = pagerState, modifier = Modifier.padding(top = 12.dp))

            AddToHomeButton(
                onClick = {
                    val option = options[pagerState.currentPage.coerceIn(0, options.lastIndex)]
                    haptics.confirm()
                    onAdd(option.face, option.size)
                    close()
                },
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )

            Spacer(
                Modifier
                    .navigationBarsPadding()
                    .height(24.dp),
            )
        }
    }
}

@Composable
private fun FaceSheetTitle(title: String, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = title,
            style = DotTheme.type.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            color = SheetColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 48.dp),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = WidgetPickerStrings.CLOSE, role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            CloseGlyph(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun FacePage(
    provider: DotWidgetProvider,
    option: FaceOption,
    page: Int,
    pagerState: PagerState,
    onClick: () -> Unit,
) {
    val real = remember(provider, option) { realSizeOf(provider, option.size) }
    val interaction = remember { MutableInteractionSource() }
    val label = "${option.face.name}, ${option.size}"
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Read in the layer block: scrolling only invalidates the layer, never recomposes the
                // live preview.
                val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    .absoluteValue
                    .coerceIn(0f, 1f)
                val s = lerp(1f, NEIGHBOUR_SCALE, distance)
                scaleX = s
                scaleY = s
                alpha = lerp(1f, NEIGHBOUR_ALPHA, distance)
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        FitPreview(
            realWidth = real.width,
            realHeight = real.height,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
        ) {
            DotWidgetSurface(
                provider = provider,
                faceId = option.face.id,
                size = option.size,
                modifier = Modifier.fillMaxSize(),
                rim = true,
            )
        }
    }
}

/** Name, size and description of the centred option. Only this scope recomposes when the page changes. */
@Composable
private fun FaceInfo(options: List<FaceOption>, pagerState: PagerState, modifier: Modifier = Modifier) {
    val option = options[pagerState.currentPage.coerceIn(0, options.lastIndex)]
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = option.face.name,
            style = DotTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = SheetColors.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = option.size.toString(),
            style = DotTheme.type.iconLabel,
            color = SheetColors.Muted,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (option.face.description.isNotBlank()) {
            Text(
                text = option.face.description,
                style = DotTheme.type.iconLabel,
                color = SheetColors.Section,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AddToHomeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(SheetColors.ButtonFill)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = WidgetPickerStrings.ADD_TO_HOME,
            style = DotTheme.type.body.copy(fontWeight = FontWeight.Medium),
            color = SheetColors.ButtonInk,
        )
    }
}

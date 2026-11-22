package com.elvan.gallery.ui

import com.composables.icons.materialsymbols.roundedfilled.Play_arrow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.gallery.data.Media
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.elvan.navil.Dimens
import com.elvan.navil.LocalElvanTopSpacerHeight
import com.elvan.navil.LocalPortraitPageBottom
import com.elvan.navil.rememberShellColors
import kotlinx.coroutines.launch

/**
 * A grid page inside ElvanShell: its first row is the shell's top spacer (so the large title folds over it exactly
 * as over Udukkai's lists) and its end clears the floating bars.
 */
@Composable
fun ElvanGrid(
    state: LazyGridState,
    columns: Int,
    modifier: Modifier = Modifier,
    gap: Dp = 2.dp,
    sidePadding: Dp = 2.dp,
    /** The date shown beside the fast-scroll handle for a grid item's key, if it has one. */
    dateLabel: ((Any) -> String?)? = null,
    /** Years marked down the fast-scroll track: where each starts (0 to 1) and its name. */
    yearMarks: List<Pair<Float, String>> = emptyList(),
    content: LazyGridScope.() -> Unit
) {
    val top = LocalElvanTopSpacerHeight.current + Dimens.SectionSpacing
    val bottom = LocalPortraitPageBottom.current ?: 24.dp
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = sidePadding, end = sidePadding, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            item(key = "shell_top_spacer", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(top - gap)) }
            content()
        }
        GridFastScroller(state, Modifier.align(Alignment.CenterEnd), dateLabel, yearMarks)
        // Samsung: while the grid scrolls, the date at the top floats in a grey pill under the buttons
        if (dateLabel != null) {
            val scrolling = state.isScrollInProgress && state.firstVisibleItemIndex > 3
            val chipAlpha by animateFloatAsState(if (scrolling) 1f else 0f, tween(if (scrolling) 120 else 600), label = "dateChip")
            val label = if (chipAlpha > 0f) state.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { dateLabel(it.key) } else null
            if (label != null) Text(
                label, fontSize = 14.sp, color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 84.dp)
                    .graphicsLayer { alpha = chipAlpha }
                    .clip(RoundedCornerShape(50)).background(Color(0xCC3A3A3C)).padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

/** Thin draggable fast-scroll thumb shared by every gallery grid. */
@Composable
private fun GridFastScroller(state: LazyGridState, modifier: Modifier = Modifier, dateLabel: ((Any) -> String?)? = null, yearMarks: List<Pair<Float, String>> = emptyList()) {
    val colors = rememberShellColors()
    val scope = rememberCoroutineScope()
    val layout by remember { derivedStateOf { state.layoutInfo } }
    val total = layout.totalItemsCount
    val visible = layout.visibleItemsInfo.size
    if (total <= visible || visible == 0) return
    val maxFirst = (total - visible).coerceAtLeast(1)
    val fraction = (state.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
    var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (state.isScrollInProgress || dragging) 1f else 0f,
        animationSpec = tween(if (state.isScrollInProgress || dragging) 80 else 450),
        label = "gridFastScrollerAlpha"
    )
    val thumb = 46.dp
    // Samsung: while the handle is held it turns blue, the date it has reached rides beside it, and the years are marked down the side
    val blue = Color(0xFF3E7BFA)
    val navbarSpace = (com.elvan.navil.LocalPortraitPageBottom.current ?: 24.dp) + 8.dp
    BoxWithConstraints(modifier.fillMaxHeight().width(220.dp).statusBarsPadding().padding(top = 72.dp, bottom = navbarSpace)) {
    Box(
        Modifier.align(Alignment.TopEnd).fillMaxHeight().width(18.dp)
            .pointerInput(total, visible) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, _ ->
                    change.consume()
                    val f = (change.position.y / size.height).coerceIn(0f, 1f)
                    scope.launch { state.scrollToItem((f * maxFirst).toInt()) }
                }
            }
    )
        // Marks sit between the top buttons and the navbar, and never crowd one another
        val top = 0.dp
        val span = maxHeight - 24.dp
        var last = (-100).dp
        if (dragging) yearMarks.filter { it.second != "1970" }.forEach { (at, year) ->
            val y = top + span * at
            if (y - last < 26.dp) return@forEach
            last = y
            Text(
                year, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = colors.textSecondary,
                modifier = Modifier.align(Alignment.TopEnd).offset(y = y).padding(end = 16.dp)
                    .clip(RoundedCornerShape(10.dp)).background(colors.surface.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd)
                .graphicsLayer { this.alpha = alpha }
                .offset(y = (maxHeight - thumb) * fraction)
                .padding(end = 3.dp)
                .width(4.dp).height(thumb)
                .clip(RoundedCornerShape(8.dp))
                .background(if (dragging) blue else colors.textSecondary.copy(alpha = 0.7f))
        )
        val label = if (dragging && dateLabel != null) layout.visibleItemsInfo.firstNotNullOfOrNull { dateLabel(it.key) } else null
        if (label != null) Text(
            label, fontSize = 14.sp, color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).offset(y = (maxHeight - thumb) * fraction + thumb / 2 - 20.dp).padding(end = 22.dp)
                .clip(RoundedCornerShape(50)).background(blue).padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

/** The pictures of one list, four to a row; a tap opens the viewer at that picture. */
fun LazyGridScope.mediaItems(
    media: List<Media>,
    selected: Set<String> = emptySet(),
    selecting: Boolean = false,
    onClick: (Int) -> Unit,
    onLongClick: (Int) -> Unit = {}
) {
    itemsIndexed(media, key = { _, m -> m.uri }) { index, m ->
        MediaThumb(m, selecting, m.uri in selected, { onClick(index) }, { onLongClick(index) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
/** [thumbPx]: how big a thumbnail to decode; dense grids ask for small ones so hundreds of tiles stay cheap to draw. */
fun MediaThumb(media: Media, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier = Modifier, showDuration: Boolean = true, square: Boolean = true, thumbPx: Int = 320) {
    val colors = rememberShellColors()
    val context = LocalContext.current
    Box(
        modifier.then(if (square) Modifier.aspectRatio(1f) else Modifier.fillMaxHeight())
            .clip(RoundedCornerShape(0.dp)).background(colors.surface)
            .onPlaced { HeroBounds.coords[media.uri] = it }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = remember(media.uri, thumbPx) { ImageRequest.Builder(context).data(thumbModel(media.uri)).size(thumbPx).build() },
            contentDescription = media.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (media.isVideo && showDuration) androidx.compose.foundation.layout.Row(
            Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shade = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
            androidx.compose.material3.Icon(
                com.composables.icons.materialsymbols.MaterialSymbols.RoundedFilled.Play_arrow, null,
                tint = Color.White, modifier = Modifier.size(15.dp)
            )
            Text(
                formatDuration(media.duration ?: 0L), color = Color.White, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                style = androidx.compose.material3.LocalTextStyle.current.copy(shadow = shade)
            )
        }
        if (selecting) {
            if (selected) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            com.elvan.navil.ElvanCheckMark(
                selected = selected, selectedTint = colors.link, idleTint = Color.White, size = 22.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
            )
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000).toInt()
    val minutes = seconds / 60
    return if (minutes >= 60) "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds % 60)
    else "%d:%02d".format(minutes, seconds % 60)
}

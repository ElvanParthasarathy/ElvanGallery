package com.elvan.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
    content: LazyGridScope.() -> Unit
) {
    val top = LocalElvanTopSpacerHeight.current + Dimens.SectionSpacing
    val bottom = LocalPortraitPageBottom.current ?: 24.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, bottom = bottom),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        item(key = "shell_top_spacer", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(top - gap)) }
        content()
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
fun MediaThumb(media: Media, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier = Modifier, showDuration: Boolean = true) {
    val colors = rememberShellColors()
    val context = LocalContext.current
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(colors.surface)
            .onGloballyPositioned { HeroBounds.rects[media.uri] = it.boundsInWindow() }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(thumbModel(media.uri)).size(320).build(),
            contentDescription = media.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (media.isVideo && showDuration) Text(
            formatDuration(media.duration ?: 0L),
            color = Color.White, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
        )
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

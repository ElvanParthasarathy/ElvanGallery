package com.elvan.gallery.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import com.elvan.navil.frostedGlass
import com.composables.icons.materialsymbols.rounded.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.gallery.data.Media
import com.elvan.navil.ElvanCheckMark
import com.elvan.navil.rememberHaptics
import com.elvan.navil.rememberShellColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Pictures picked with a long press: the uris, and whether the page is selecting at all. */
class MediaSelection {
    var active by mutableStateOf(false)
    var selected by mutableStateOf(emptySet<String>())
    fun clear() { active = false; selected = emptySet() }
    fun toggle(uri: String) { active = true; selected = if (uri in selected) selected - uri else selected + uri }
}

/** One day of pictures under its heading ("Today", "Yesterday", "Sat, 21 Nov", "21 Nov 2024"). */
class DayGroup(val key: String, val label: String, val media: List<Media>)

fun groupByDay(media: List<Media>): List<DayGroup> {
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    val year = cal.get(Calendar.YEAR)
    val sameYear = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH)
    val otherYear = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
    return media.groupBy {
        cal.timeInMillis = (it.dateAdded ?: 0L) * 1000
        cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }.map { (day, items) ->
        cal.timeInMillis = (items.first().dateAdded ?: 0L) * 1000
        val label = when {
            day == today -> "Today"
            day == today - 1 || (cal.get(Calendar.DAY_OF_YEAR) == cal.getActualMaximum(Calendar.DAY_OF_YEAR) && day / 1000 == year - 1 && today % 1000 == 1) -> "Yesterday"
            cal.get(Calendar.YEAR) == year -> sameYear.format(Date(cal.timeInMillis))
            else -> otherYear.format(Date(cal.timeInMillis))
        }
        DayGroup("day_$day", label, items)
    }
}

/** Grid sizes a pinch steps through: fewest columns first. */
private val columnLevels = listOf(2, 3, 4, 6, 8)

fun savedColumns(context: Context) =
    context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE).getInt("pictures_columns", 4).takeIf { it in columnLevels } ?: 4

/**
 * A grid of pictures under date headings: pinch to change how many to a row, hold a picture and
 * slide to select a run of them, tick a heading to select its whole day.
 */
@Composable
fun DatedMediaGrid(
    media: List<Media>,
    grid: LazyGridState,
    selection: MediaSelection,
    header: String? = null,
    onOpen: (List<Media>, Int) -> Unit
) {
    val context = LocalContext.current
    val colors = rememberShellColors()
    var columns by remember { mutableStateOf(savedColumns(context)) }
    val days = remember(media) { groupByDay(media) }
    val order = remember(media) { media.map { it.uri } }

    ElvanGrid(
        grid, columns = columns,
        modifier = Modifier
            .pinchColumns(columns) { next ->
                columns = next
                context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE).edit().putInt("pictures_columns", next).apply()
            }
            .gridDragSelect(grid, order, selection)
    ) {
        if (header != null) item(key = "page_header", span = { GridItemSpan(maxLineSpan) }) { CountLine(header) }
        days.forEach { day ->
            item(key = day.key, span = { GridItemSpan(maxLineSpan) }, contentType = "day") {
                DayHeading(day, selection, colors)
            }
            items(day.media, key = { it.uri }, contentType = { "media" }) { m ->
                MediaThumb(
                    m, selection.active, m.uri in selection.selected, showDuration = columns < 6,
                    onClick = { if (selection.active) selection.toggle(m.uri) else onOpen(media, order.indexOf(m.uri)) },
                    onLongClick = null,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun DayHeading(day: DayGroup, selection: MediaSelection, colors: com.elvan.navil.ShellColors) {
    val all = day.media.all { it.uri in selection.selected }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(if (selection.active) Modifier.clickable {
                val uris = day.media.map { it.uri }
                selection.selected = if (all) selection.selected - uris.toSet() else selection.selected + uris
            } else Modifier)
            .padding(start = 10.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selection.active) ElvanCheckMark(all, colors.link, colors.textTertiary, 20.dp, Modifier.padding(end = 10.dp))
        Text(day.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
    }
}

/** Two fingers spread for bigger pictures (fewer to a row), pinch for more; one finger is left to the scroll. */
private fun Modifier.pinchColumns(columns: Int, onChange: (Int) -> Unit): Modifier = pointerInput(columns) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoom = 1f
        var changed = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } > 1) {
                zoom *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                val at = columnLevels.indexOf(columns)
                if (!changed && zoom > 1.25f && at > 0) { onChange(columnLevels[at - 1]); changed = true }
                if (!changed && zoom < 0.8f && at < columnLevels.lastIndex) { onChange(columnLevels[at + 1]); changed = true }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * Hold a picture and, without lifting, slide: every picture between the first and the finger is selected; sliding back
 * gives the ones it leaves their earlier state. Near the top or bottom the grid scrolls by itself. The touch is watched
 * before the tiles see it, so a hold never opens a picture.
 */
@Composable
private fun Modifier.gridDragSelect(grid: LazyGridState, order: List<String>, selection: MediaSelection): Modifier {
    val currentOrder by rememberUpdatedState(order)
    val haptics = rememberHaptics()
    return pointerInput(grid) {
        val edge = 96.dp.toPx()
        coroutineScope {
            val scope = this
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val movedOrLifted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                        if (!c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                    }
                    @Suppress("UNREACHABLE_CODE") false
                }
                if (movedOrLifted == true) return@awaitEachGesture

                fun uriAt(y: Float, x: Float): String? {
                    val info = grid.layoutInfo
                    val gy = y + info.viewportStartOffset
                    return info.visibleItemsInfo.firstOrNull {
                        gy >= it.offset.y && gy < it.offset.y + it.size.height && x >= it.offset.x && x < it.offset.x + it.size.width
                    }?.key as? String
                }
                val startUri = uriAt(down.position.y, down.position.x)?.takeIf { it in currentOrder } ?: return@awaitEachGesture
                val base = selection.selected
                val startIndex = currentOrder.indexOf(startUri)
                selection.active = true
                selection.selected = base + startUri
                haptics.tick()
                var fingerY = down.position.y
                var fingerX = down.position.x
                var lastEnd = startIndex
                val scroller = scope.launch {
                    while (true) {
                        withFrameNanos { }
                        val speed = when {
                            fingerY < edge -> -(edge - fingerY) / edge * 28f
                            fingerY > size.height - edge -> (fingerY - (size.height - edge)) / edge * 28f
                            else -> 0f
                        }
                        if (speed != 0f) grid.scrollBy(speed)
                    }
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        event.changes.forEach { it.consume() }
                        if (!c.pressed) break
                        fingerY = c.position.y
                        fingerX = c.position.x
                        val end = uriAt(fingerY, fingerX)?.let { currentOrder.indexOf(it) }?.takeIf { it >= 0 } ?: continue
                        if (end != lastEnd) {
                            lastEnd = end
                            haptics.tick()
                            val range = currentOrder.subList(minOf(startIndex, end), maxOf(startIndex, end) + 1).toSet()
                            selection.selected = base + range
                        }
                    }
                } finally {
                    scroller.cancel()
                }
            }
        }
    }
}

/** The floating actions while pictures are selected: share, favourite, delete (to the phone's trash). */
@Composable
fun PictureSelectionBar(selection: MediaSelection, library: GalleryLibrary) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val trash = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) { selection.clear(); library.reload() }
    }
    val uris = selection.selected.toList()
    val actions = if (uris.isEmpty()) emptyList() else listOf(
        com.elvan.navil.ElvanFloatingAction("Share", com.elvan.navil.MaterialSymbols.Rounded.Share, {
            context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris.map { android.net.Uri.parse(it) }))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share files"))
        }),
        com.elvan.navil.ElvanFloatingAction("Favourite", com.composables.icons.materialsymbols.MaterialSymbols.Rounded.Favorite, {
            Favourites.add(context, uris)
            android.widget.Toast.makeText(context, "Added to Favourites", android.widget.Toast.LENGTH_SHORT).show()
            selection.clear()
        }),
        com.elvan.navil.ElvanFloatingAction("Delete", com.elvan.navil.MaterialSymbols.Rounded.Delete, {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) trash.launch(
                androidx.activity.result.IntentSenderRequest.Builder(
                    android.provider.MediaStore.createTrashRequest(context.contentResolver, uris.map { android.net.Uri.parse(it) }, true).intentSender
                ).build()
            ) else confirm = true
        }, isDestructive = true)
    )
    com.elvan.navil.ElvanFloatingActionBar(actions)
    if (confirm) ElvanDialog("Delete ${uris.size} item(s)?", "They are deleted from this device.", "Delete", destructive = true, onDismiss = { confirm = false }) {
        confirm = false
        uris.forEach { try { context.contentResolver.delete(android.net.Uri.parse(it), null, null) } catch (_: Exception) {} }
        selection.clear(); library.reload()
    }
}

/** The round "back to top" button: shows once the grid is well down, and takes it to the top with the title open. */
@Composable
fun ScrollToTopButton(grid: LazyGridState) {
    val colors = rememberShellColors()
    val controller = com.elvan.navil.LocalElvanShellController.current
    val show = grid.firstVisibleItemIndex > 40
    val bottom = (com.elvan.navil.LocalPortraitPageBottom.current ?: 24.dp) + 4.dp
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        androidx.compose.animation.AnimatedVisibility(
            show,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.7f),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.7f),
            modifier = Modifier.padding(bottom = bottom)
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(44.dp).frostedGlass(androidx.compose.foundation.shape.CircleShape, colors)
                    .clip(androidx.compose.foundation.shape.CircleShape).clickable { controller.expandHeader() },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(com.composables.icons.materialsymbols.MaterialSymbols.Rounded.Vertical_align_top, "Top", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

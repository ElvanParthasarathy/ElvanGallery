package com.elvan.gallery.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
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
    val sameYear = SimpleDateFormat("d MMM", Locale.ENGLISH) // Samsung: "20 Nov", no weekday
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
// Pictures (Samsung): days in photo-shaped rows, days in 4-across squares, months at 9 across, years at 20
private val columnLevels = listOf(3, 4, 9, 20)
private val albumColumnLevels = listOf(3, 4, 7, 12)

fun savedColumns(context: Context) =
    context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE).getInt("pictures_density", 4).takeIf { it in columnLevels } ?: 4

/** Groups by a date pattern ("MMM yyyy" for months, "yyyy" for years), newest first as the media already is. */
private fun groupByPattern(media: List<Media>, pattern: String): List<DayGroup> {
    val f = SimpleDateFormat(pattern, Locale.ENGLISH)
    return media.groupBy { f.format(Date((it.dateAdded ?: 0L) * 1000)) }.map { (label, items) -> DayGroup("$pattern:$label", label, items) }
}

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
    /** An album: an even square grid (4 across to start) with no date headings. */
    plain: Boolean = false,
    /** Drawn full width above everything else (an album's cover). */
    cover: (@Composable () -> Unit)? = null,
    onOpen: (List<Media>, Int) -> Unit
) {
    val context = LocalContext.current
    val colors = rememberShellColors()
    val order = remember(media) { media.map { it.uri } }
    val prefs = remember(context) { context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE) }
    val columnsKey = if (plain) "album_columns" else "pictures_density"
    var columns by remember { mutableIntStateOf(if (plain) prefs.getInt(columnsKey, 4).takeIf { it in albumColumnLevels } ?: 4 else savedColumns(context)) }
    // Days keep their own proportions in rows; months and years are even squares under their own headings
    val squares = plain || columns >= 4
    val days = remember(media, columns) {
        when {
            plain -> emptyList()
            columns == 20 -> groupByPattern(media, "yyyy")
            columns == 9 -> groupByPattern(media, "MMM yyyy")
            else -> groupByDay(media)
        }
    }
    val pinch = remember { PinchZoom() }
    val morph = remember { GridMorph(squareSlots) }
    // For the fast-scroll bubble: each picture's (and each day row's) date, and where each year begins
    val dateOf = remember(media, days) {
        val map = HashMap<Any, Media>()
        media.forEach { map[it.uri] = it }
        days.forEach { d -> justifiedRows(d.media, columns).forEachIndexed { i, row -> map["${d.key}_row_$i"] = row.first() } }
        map
    }
    val years = remember(media) {
        val f = SimpleDateFormat("yyyy", Locale.ENGLISH)
        if (media.isEmpty()) emptyList() else media.withIndex().groupBy { f.format(Date((it.value.dateAdded ?: 0L) * 1000)) }
            .map { (y, items) -> items.first().index.toFloat() / media.size to y }
    }

    ElvanGrid(
        grid, columns = if (squares) columns else 1,
        dateLabel = { key -> dateOf[key]?.let { bubbleDate(it) } }, yearMarks = years, gap = if (squares) 1.dp else 0.5.dp, sidePadding = 0.dp,
        modifier = if (plain) Modifier.pinchMorph(morph, grid, columns, albumColumnLevels, 1 + (if (cover != null) 1 else 0) + (if (header != null) 1 else 0)) {
            columns = it
            prefs.edit().putInt(columnsKey, it).apply()
        } else Modifier.pinchLevels(pinch, columns, columnLevels) {
            columns = it
            prefs.edit().putInt(columnsKey, it).apply()
        }.pinchScaled(pinch)
    ) {
        if (cover != null) item(key = "page_cover", span = { GridItemSpan(maxLineSpan) }) { cover() }
        if (header != null) item(key = "page_header", span = { GridItemSpan(maxLineSpan) }) { CountLine(header) }
        if (plain) {
            items(media.size, key = { media[it].uri }, contentType = { "media" }) { i ->
                val m = media[i]
                MediaThumb(
                    m, selection.active, m.uri in selection.selected,
                    onClick = { if (selection.active) selection.toggle(m.uri) else onOpen(media, i) },
                    onLongClick = { selection.toggle(m.uri) },
                    modifier = Modifier.morphTile(morph, i, columns)
                )
            }
            return@ElvanGrid
        }
        if (squares) {
            days.forEach { group ->
                item(key = group.key, span = { GridItemSpan(maxLineSpan) }, contentType = "day") { DayHeading(group, selection, colors) }
                items(group.media.size, key = { group.media[it].uri }, contentType = { "media" }) { i ->
                    val m = group.media[i]
                    MediaThumb(
                        m, selection.active, m.uri in selection.selected, showDuration = columns < 9,
                        onClick = { if (selection.active) selection.toggle(m.uri) else onOpen(media, order.indexOf(m.uri)) },
                        onLongClick = { selection.toggle(m.uri) }
                    )
                }
            }
            return@ElvanGrid
        }
        days.forEach { day ->
            item(key = day.key, span = { GridItemSpan(maxLineSpan) }, contentType = "day") {
                DayHeading(day, selection, colors)
            }
            val rows = justifiedRows(day.media, columns)
            rows.forEachIndexed { rowIndex, rowMedia ->
                item(key = "${day.key}_row_$rowIndex", contentType = "media_row") {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val ratios = rowMedia.map { it.displayRatio() }
                        // A day's short last row keeps the usual height and sits to the left, as Samsung's does
                        val usual = columns * 0.72f
                        val short = rowIndex == rows.lastIndex && rowMedia.size < columns && ratios.sum() < usual * 0.8f
                        val rowHeight = maxWidth / (if (short) usual else ratios.sum().coerceAtLeast(0.5f))
                        Row(
                            Modifier.fillMaxWidth().height(rowHeight),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.5.dp)
                        ) {
                            rowMedia.forEachIndexed { index, m ->
                                MediaThumb(
                                    m, selection.active, m.uri in selection.selected, showDuration = true,
                                    onClick = { if (selection.active) selection.toggle(m.uri) else onOpen(media, order.indexOf(m.uri)) },
                                    onLongClick = { selection.toggle(m.uri) },
                                    square = false,
                                    modifier = if (short) Modifier.width(rowHeight * ratios[index]) else Modifier.weight(ratios[index])
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Media.displayRatio(): Float =
    if (width > 0 && height > 0) (width.toFloat() / height.toFloat()).coerceIn(0.42f, 2.4f) else 1f

/** Samsung Gallery keeps original thumbnail proportions and closes a row once it fills the screen. */
private fun justifiedRows(media: List<Media>, targetColumns: Int): List<List<Media>> {
    val rows = mutableListOf<List<Media>>()
    var current = mutableListOf<Media>()
    var ratio = 0f
    media.forEach { item ->
        current += item
        ratio += item.displayRatio()
        val targetRatio = targetColumns * 0.72f
        if ((current.size >= 2 && ratio >= targetRatio) || current.size == targetColumns) {
            rows += current
            current = mutableListOf()
            ratio = 0f
        }
    }
    if (current.isNotEmpty()) rows += current
    return rows
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

private fun bubbleDate(m: Media): String {
    val cal = Calendar.getInstance()
    val now = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    val year = cal.get(Calendar.YEAR)
    cal.timeInMillis = (m.dateAdded ?: 0L) * 1000
    val day = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    return when {
        day == now -> "Today"
        cal.get(Calendar.YEAR) == year -> SimpleDateFormat("d MMM", Locale.ENGLISH).format(cal.time)
        else -> SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(cal.time)
    }
}

private val squareSlots = GridSlots { index, columns, width, _ ->
    val gap = 1.dp.toPx()
    val w = (width - (columns - 1) * gap) / columns
    val x = (index % columns) * (w + gap)
    val y = (index / columns) * (w + gap)
    androidx.compose.ui.geometry.Rect(x, y, x + w, y + w)
}

/** How far the fingers have pinched, and where: the grid is drawn scaled by this so it follows them. */
class PinchZoom {
    val scale = androidx.compose.animation.core.Animatable(1f)
    var origin by androidx.compose.runtime.mutableStateOf(androidx.compose.ui.graphics.TransformOrigin.Center)
}

fun Modifier.pinchScaled(pinch: PinchZoom): Modifier = graphicsLayer {
    scaleX = pinch.scale.value; scaleY = pinch.scale.value; transformOrigin = pinch.origin
}

/**
 * Two fingers: the grid grows or shrinks under them as they move. On lifting, a spread or pinch past about a fifth steps
 * to the next level; the new grid starts at the size the fingers left it and eases into place, so the change reads as
 * one continuous zoom. One finger is left to the scroll.
 */
fun Modifier.pinchLevels(pinch: PinchZoom, columns: Int, levels: List<Int>, onChange: (Int) -> Unit): Modifier = pointerInput(columns, levels) {
    val settle = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.9f, stiffness = 380f)
    coroutineScope {
        val scope = this
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zoom = 1f
            var pinched = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) {
                    pinched = true
                    zoom = (zoom * event.calculateZoom()).coerceIn(0.5f, 2f)
                    val c = event.calculateCentroid(useCurrent = true)
                    if (c.isSpecified && size.width > 0) pinch.origin = androidx.compose.ui.graphics.TransformOrigin(c.x / size.width, c.y / size.height)
                    event.changes.forEach { it.consume() }
                    val z = zoom
                    scope.launch { pinch.scale.snapTo(z.coerceIn(0.7f, 1.4f)) }
                }
            } while (event.changes.any { it.pressed })
            if (!pinched) return@awaitEachGesture
            val at = levels.indexOf(columns)
            val next = when {
                zoom > 1.2f && at > 0 -> levels[at - 1]
                zoom < 0.83f && at in 0 until levels.lastIndex -> levels[at + 1]
                else -> null
            }
            val shown = zoom.coerceIn(0.7f, 1.4f)
            scope.launch {
                if (next != null) {
                    onChange(next)
                    // A list (1) is drawn about as dense as 4 across
                    fun across(c: Int) = if (c == 1) 4f else c.toFloat()
                    pinch.scale.snapTo((shown * across(next) / across(columns)).coerceIn(0.4f, 2.5f))
                }
                pinch.scale.animateTo(1f, settle)
            }
        }
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
        com.elvan.navil.ElvanFloatingAction("Delete", com.composables.icons.materialsymbols.MaterialSymbols.Rounded.Delete, {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) trash.launch(
                androidx.activity.result.IntentSenderRequest.Builder(
                    android.provider.MediaStore.createTrashRequest(context.contentResolver, uris.map { android.net.Uri.parse(it) }, true).intentSender
                ).build()
            ) else confirm = true
        })
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

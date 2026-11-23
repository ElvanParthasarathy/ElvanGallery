package com.elvan.gallery.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.isSpecified
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onPlaced
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.zIndex
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
private val columnLevels = listOf(3, 4, 9)
private val albumColumnLevels = listOf(3, 4, 7)

fun savedColumns(context: Context) =
    context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE).getInt("pictures_density", 4).takeIf { it in columnLevels } ?: 4

/** Groups by a date pattern ("MMM yyyy" for months, "yyyy" for years), newest first as the media already is. */
private fun groupByPattern(media: List<Media>, pattern: String): List<DayGroup> {
    val f = SimpleDateFormat(pattern, Locale.ENGLISH)
    return media.groupBy { f.format(Date((it.dateAdded ?: 0L) * 1000)) }.map { (label, items) -> DayGroup("$pattern:$label", label, items) }
}

/** Heights the layout model needs that only the screen knows: a date heading, the album cover, the count line. */
private class Measured {
    var heading by androidx.compose.runtime.mutableFloatStateOf(0f)
    var cover by androidx.compose.runtime.mutableFloatStateOf(0f)
    var header by androidx.compose.runtime.mutableFloatStateOf(0f)
}

private fun thumbPxFor(level: Int) = when (level) { 9 -> 128; 7 -> 160; 4 -> 256; else -> 320 }

/**
 * A grid of pictures under date headings: pinch to change how many to a row, hold a picture and
 * slide to select a run of them, tick a heading to select its whole day.
 *
 * A pinch moves every photo from where it is to where it will be at the next level, with the photo under the
 * fingers held still; the next level is drawn faintly behind, so the space the photos leave is never empty, and when
 * the fingers lift the page lands exactly where the photos did.
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
    val density = androidx.compose.ui.platform.LocalDensity.current
    val prefs = remember(context) { context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE) }
    val columnsKey = if (plain) "album_columns" else "pictures_density"
    val levels = if (plain) albumColumnLevels else columnLevels
    var columns by remember { mutableIntStateOf(if (plain) prefs.getInt(columnsKey, 4).takeIf { it in albumColumnLevels } ?: 4 else savedColumns(context)) }
    val topSpacer = com.elvan.navil.LocalElvanTopSpacerHeight.current + com.elvan.navil.Dimens.SectionSpacing
    val measured = remember { Measured() }
    val gridCoords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val mediaIndex = remember(media) { HashMap<String, Int>(media.size * 2).also { m -> media.forEachIndexed { i, it -> m[it.uri] = i } } }

    // Groups and photo rows for each level, worked out once and kept
    val groupCache = remember(media) { java.util.concurrent.ConcurrentHashMap<Int, List<DayGroup>>() }
    fun groupsFor(level: Int): List<DayGroup> = when {
        plain -> listOf(DayGroup("all", "", media))
        else -> groupCache.getOrPut(when (level) { 20 -> 20; 9 -> 9; else -> 0 }) {
            when (level) { 20 -> groupByPattern(media, "yyyy"); 9 -> groupByPattern(media, "MMM yyyy"); else -> groupByDay(media) }
        }
    }
    val rowCache = remember(media) { java.util.concurrent.ConcurrentHashMap<String, List<List<Media>>>() }
    fun rowsOf(g: DayGroup) = rowCache.getOrPut(g.key) { justifiedRows(g.media, 3) }
    val rowByKey = remember(media) { HashMap<String, List<Media>>() }

    // The exact layout of each level, as the grid will lay it out
    val planCache = remember(media, plain) { java.util.concurrent.ConcurrentHashMap<String, GridPlan>() }
    fun plan(level: Int, width: Float): GridPlan {
        val prefix = listOfNotNull(if (cover != null) measured.cover else null, if (header != null) measured.header else null)
        val heading = if (measured.heading > 0f) measured.heading else with(density) { 42.dp.toPx() }
        val key = "$level:$width:$heading:$prefix"
        return planCache.getOrPut(key) {
            with(density) { buildPlan(media, mediaIndex, level, plain, groupsFor(level), ::rowsOf, width, topSpacer, prefix, heading) }
        }
    }
    val animScope = androidx.compose.runtime.rememberCoroutineScope()
    // Ready the levels either side before any pinch, off the main thread (each is a pass over every photo)
    var gridWidth by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(media, columns, gridWidth, measured.heading, measured.cover, measured.header) {
        if (gridWidth <= 0f || (!plain && measured.heading <= 0f)) return@LaunchedEffect
        val at = levels.indexOf(columns)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            listOfNotNull(levels.getOrNull(at - 1), levels.getOrNull(at + 1), columns).forEach { plan(it, gridWidth) }
        }
    }
    val morph = remember(media, plain) { GridMorph({ index, level, width, _ -> plan(level, width).rect.getOrNull(index)?.takeIf { it.width > 0f } ?: androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 1f) }, animScope) }

    // Samsung never reveals an undecoded tile during a density change. Keep a generous run around the viewport in
    // Coil before the fingers go down, including the extra photos a denser level will bring on screen. Requests use
    // SystemThumbKeyer's size-independent key, so PinchCanvas and the newly settled LazyGrid receive the same bitmap.
    val thumbLoader = remember(context) { coil3.SingletonImageLoader.get(context) }
    LaunchedEffect(media, grid, columns) {
        androidx.compose.runtime.snapshotFlow {
            grid.layoutInfo.visibleItemsInfo.flatMap { item ->
                val key = item.key as? String ?: return@flatMap emptyList()
                when {
                    key in mediaIndex -> listOfNotNull(mediaIndex[key])
                    else -> rowByKey[key].orEmpty().mapNotNull { mediaIndex[it.uri] }
                }
            }
        }.map { it.sorted() }.distinctUntilChanged().collectLatest { visible ->
            if (visible.isEmpty()) return@collectLatest
            // A denser level shows ~200 photos around the fingers: ready those too, at that level's smaller size
            val denser = levels.getOrNull(levels.indexOf(columns) + 1)
            val reach = if (denser != null) 200 else 96
            val from = (visible.first() - reach).coerceAtLeast(0)
            val to = (visible.last() + reach).coerceAtMost(media.lastIndex)
            // Nearest first, so what the pinch reveals first is ready first
            val order = (from..to).sortedBy { i -> if (i < visible.first()) visible.first() - i else if (i > visible.last()) i - visible.last() else 0 }
            for (i in order) {
                val uri = media[i].uri
                if (thumbLoader.memoryCache?.get(thumbPlaceholder(uri)) == null) {
                    val onScreen = i in visible.first()..visible.last()
                    thumbLoader.enqueue(
                        coil3.request.ImageRequest.Builder(context)
                            .data(thumbModel(uri)).size(thumbPxFor(if (onScreen || denser == null) columns else denser))
                            .memoryCacheKey(thumbPlaceholder(uri)).build()
                    )
                }
            }
        }
    }

    // As a pinch begins: the photo under the fingers (or the nearest), where on it they are, and where the page is
    morph.begin = begin@{ f ->
        val gc = gridCoords[0]?.takeIf { it.isAttached } ?: return@begin null
        val onScreen = grid.layoutInfo.visibleItemsInfo.flatMap { item ->
            val k = item.key as? String ?: return@flatMap emptyList()
            if (k in mediaIndex) listOf(k) else rowByKey[k]?.map { it.uri } ?: emptyList()
        }
        var best: Pair<String, androidx.compose.ui.geometry.Rect>? = null
        var bestDist = Float.MAX_VALUE
        onScreen.forEach { uri ->
            val c = HeroBounds.get(uri) ?: return@forEach
            val r = gc.localBoundingBoxOf(c, clipBounds = false)
            val dx = maxOf(r.left - f.x, 0f, f.x - r.right)
            val dy = maxOf(r.top - f.y, 0f, f.y - r.bottom)
            val d = dx * dx + dy * dy
            if (d < bestDist) { bestDist = d; best = uri to r }
        }
        val (uri, r) = best ?: return@begin null
        val a = mediaIndex[uri] ?: return@begin null
        val width = morph.width
        val fy = ((f.y - r.top) / r.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val atTop = grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0
        morph.anchorFy = fy
        morph.sNow = plan(columns, width).rect[a].top - r.top
        morph.scrollFor = { level ->
            // At the very top the page stays at the top (the header stays open); otherwise the photo stays under the fingers
            if (atTop) 0f else plan(level, width).rect[a].let { it.top + fy * it.height - f.y }.coerceAtLeast(0f)
        }
        a
    }

    // For the fast-scroll bubble: each picture's (and each day row's) date, and where each year begins
    val days = groupsFor(columns)
    val dateOf = remember(media, columns) {
        val map = HashMap<Any, Media>()
        media.forEach { map[it.uri] = it }
        if (!plain && columns == 3) days.forEach { d -> rowsOf(d).forEachIndexed { i, row -> map["${d.key}_row_$i"] = row.first() } }
        map
    }
    val years = remember(media) {
        val f = SimpleDateFormat("yyyy", Locale.ENGLISH)
        if (media.isEmpty()) emptyList() else media.withIndex().groupBy { f.format(Date((it.value.dateAdded ?: 0L) * 1000)) }
            .map { (y, items) -> items.first().index.toFloat() / media.size to y }
    }

    /** The items of one level; [live] is the real grid, otherwise the faint copy of the next level drawn during a pinch. */
    fun androidx.compose.foundation.lazy.grid.LazyGridScope.levelContent(level: Int, live: Boolean) {
        val tp = thumbPxFor(level)
        val squares = plain || level >= 4
        if (cover != null) item(key = "page_cover", span = { GridItemSpan(maxLineSpan) }) {
            Box(if (live) Modifier.onSizeChanged { measured.cover = it.height.toFloat() } else Modifier) { cover() }
        }
        if (header != null) item(key = "page_header", span = { GridItemSpan(maxLineSpan) }) {
            Box(if (live) Modifier.onSizeChanged { measured.header = it.height.toFloat() } else Modifier) { CountLine(header) }
        }
        fun tile(m: Media, i: Int, mod: Modifier, square: Boolean) = @Composable {
            MediaThumb(
                m, selection.active, m.uri in selection.selected, showDuration = level < 9,
                onClick = { if (!live) Unit else if (selection.active) selection.toggle(m.uri) else onOpen(media, i) },
                onLongClick = if (live) ({ selection.toggle(m.uri) }) else null,
                square = square,
                modifier = mod,
                thumbPx = tp, trackHero = live
            )
        }
        groupsFor(level).forEach { group ->
            if (!plain) item(key = group.key, span = { GridItemSpan(maxLineSpan) }, contentType = "day") {
                Box(
                    if (live) Modifier.onSizeChanged { measured.heading = it.height.toFloat() } else Modifier
                ) { DayHeading(group, selection, colors) }
            }
            if (squares) {
                items(group.media.size, key = { group.media[it].uri }, contentType = { "media" }) { k ->
                    val m = group.media[k]
                    tile(m, mediaIndex[m.uri] ?: 0, Modifier, true)()
                }
            } else {
                val rows = rowsOf(group)
                rows.forEachIndexed { rowIndex, rowMedia ->
                    val rowKey = "${group.key}_row_$rowIndex"
                    if (live) rowByKey[rowKey] = rowMedia
                    item(key = rowKey, contentType = "media_row") {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val ratios = rowMedia.map { it.displayRatio() }
                            // A day's short last row keeps the usual height and sits to the left, as Samsung's does
                            val usual = level * 0.72f
                            val short = rowIndex == rows.lastIndex && rowMedia.size < level && ratios.sum() < usual * 0.8f
                            val rowHeight = maxWidth / (if (short) usual else ratios.sum().coerceAtLeast(0.5f))
                            Row(
                                Modifier.fillMaxWidth().height(rowHeight),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.5.dp)
                            ) {
                                rowMedia.forEachIndexed { index, m ->
                                    tile(m, mediaIndex[m.uri] ?: 0, if (short) Modifier.width(rowHeight * ratios[index]) else Modifier.weight(ratios[index]), false)()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val gapFor = { level: Int -> if (plain || level >= 4) 1.dp else 0.5.dp }
    Box(Modifier.fillMaxSize()) {
    QuietWhilePinching(morph) { ElvanGrid(
        grid, columns = if (plain || columns >= 4) columns else 1,
        dateLabel = { key -> dateOf[key]?.let { bubbleDate(it) } }, yearMarks = years, gap = gapFor(columns), sidePadding = 0.dp,
        modifier = Modifier.onPlaced { gridCoords[0] = it; gridWidth = it.size.width.toFloat() }
            // Hidden (still taking the touch) while PinchCanvas draws the pinch; shown again the frame the new level is in place
            .graphicsLayer { alpha = morph.target.let { t -> if (t != null && t != columns && morph.scrollFor != null) 0f else 1f } }
            .pinchMorph(morph, grid, columns, levels, 1 + (if (cover != null) 1 else 0) + (if (header != null) 1 else 0)) { next ->
                // The new level starts exactly where the photos were carried to, in the same frame
                morph.sThen[next]?.let { s -> plan(next, morph.width).itemAt(s).let { (i, o) -> grid.requestScrollToItem(i, o) } }
                columns = next
                prefs.edit().putInt(columnsKey, next).apply()
            },
    ) { levelContent(columns, live = true) } }
    PinchCanvas(morph, columns, { plan(it, morph.width) }, media, colors.surface, colors.background, colors.textPrimary, plain)
    }
}

/**
 * The whole pinch as one drawing, over the (hidden) grid: every photo on screen now or at the next level is painted
 * from Coil's memory cache part way between its place now and its place then, and the date headings of the two levels
 * cross-fade. One display list instead of hundreds of moving tiles, so it keeps up on mid-range phones.
 * Photos not in memory yet are grey and load in the background.
 */
@Composable
private fun PinchCanvas(
    morph: GridMorph, columns: Int, plan: (Int) -> GridPlan, media: List<Media>,
    surface: Color, background: Color, textColor: Color, plain: Boolean
) {
    val target = morph.target
    val sThen = target?.let { morph.sThen[it] }
    if (target == null || target == columns || sThen == null || morph.scrollFor == null || morph.width <= 0f) return
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val loader = coil3.SingletonImageLoader.get(context)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    val now = remember(columns, morph.width) { plan(columns) }
    val then = remember(target, morph.width) { plan(target) }
    val sNow = morph.sNow
    var loaded by remember { mutableIntStateOf(0) }
    var height by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val bitmaps = remember { HashMap<String, androidx.compose.ui.graphics.ImageBitmap>() }
    val labels = remember { HashMap<String, androidx.compose.ui.text.TextLayoutResult>() }
    fun cached(uri: String): androidx.compose.ui.graphics.ImageBitmap? = bitmaps[uri]
        ?: (loader.memoryCache?.get(thumbPlaceholder(uri))?.image as? coil3.BitmapImage)?.bitmap?.asImageBitmap()?.also { bitmaps[uri] = it }
    // The photos the next level shows that are not in memory: load them, each arrival redraws
    LaunchedEffect(target, height) {
        if (height <= 0f) return@LaunchedEffect
        var i = then.firstVisible(sThen)
        while (i < media.size && then.rect[i].top <= sThen + height) {
            val uri = media[i].uri
            if (cached(uri) == null) loader.enqueue(
                coil3.request.ImageRequest.Builder(context).data(thumbModel(uri)).size(thumbPxFor(target))
                    .memoryCacheKey(thumbPlaceholder(uri))
                    .listener(onSuccess = { _, _ -> loaded++ }).build()
            )
            i++
        }
    }
    val padX = with(density) { 10.dp.toPx() }
    val padY = with(density) { 14.dp.toPx() }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize().onSizeChanged { height = it.height.toFloat() }) {
        loaded
        val p = morph.progress.value
        val h = size.height
        drawRect(background)
        // Photos on screen at either level (both ranges hold the photo under the fingers, so together they are one run)
        fun lastVisible(pl: GridPlan, s: Float): Int {
            var i = pl.firstVisible(s)
            while (i < media.size && pl.rect[i].top <= s + h) i++
            return i
        }
        val from = minOf(now.firstVisible(sNow), then.firstVisible(sThen))
        val to = maxOf(lastVisible(now, sNow), lastVisible(then, sThen))
        // Every photo owns its transition in both directions. Zooming out must not scale the page as one film:
        // each thumbnail shrinks and travels from its current rectangle to its own rectangle in the denser level.
        for (i in from until to) {
            val a = now.rect[i]
            val b = then.rect[i]
            // A photo that was off screen does not travel in from far away: it fades in where it will be,
            // filling the space the shrinking photos leave
            val wasOff = a.top - sNow > h || a.bottom - sNow < 0f
            val k = if (wasOff) 1f else p
            val fade = if (wasOff) (p * 2.5f).coerceAtMost(1f) else 1f
            val left = a.left + (b.left - a.left) * k
            val top = (a.top - sNow) + ((b.top - sThen) - (a.top - sNow)) * k
            val w = a.width + (b.width - a.width) * k
            val ht = a.height + (b.height - a.height) * k
            if (top > h || top + ht < 0f || w < 1f || ht < 1f || fade <= 0.01f) continue
            val img = cached(media[i].uri)
            // A photo still loading: grey only if it was already on screen; ones arriving from off screen just wait
            if (img == null) { if (!wasOff) drawRect(surface, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(w, ht), alpha = fade); continue }
            val dw = w.toInt().coerceAtLeast(1)
            val dh = ht.toInt().coerceAtLeast(1)
            // Centre crop, as the tiles draw
            val scale = maxOf(dw / img.width.toFloat(), dh / img.height.toFloat())
            val sw = (dw / scale).toInt().coerceIn(1, img.width)
            val sh = (dh / scale).toInt().coerceIn(1, img.height)
            drawImage(img, androidx.compose.ui.unit.IntOffset((img.width - sw) / 2, (img.height - sh) / 2), androidx.compose.ui.unit.IntSize(sw, sh),
                androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()), androidx.compose.ui.unit.IntSize(dw, dh), alpha = fade)
        }
        // Date headings cross-fade at the two layouts' real positions; the page itself never scales or jumps.
        if (!plain) for ((pl, s, alpha) in listOf(Triple(now, sNow, 1f - p), Triple(then, sThen, p))) {
            if (alpha <= 0.01f) continue
            for (k in pl.headTop.indices) {
                val y = pl.headTop[k] - s
                if (y > h || y < -padY * 3) continue
                val label = pl.headLabel[k]
                val layout = labels.getOrPut(label) { measurer.measure(label, style) }
                drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(padX, y + padY), alpha = alpha)
            }
        }
    }
}

/** One level of a photo grid, laid out as the lazy grid lays it out: every item's top and every photo's rectangle, in content pixels. */
class GridPlan(
    private val tops: FloatArray, val rect: Array<androidx.compose.ui.geometry.Rect>,
    /** Each date heading's top and text, top to bottom. */
    val headTop: FloatArray = FloatArray(0), val headLabel: List<String> = emptyList()
) {
    /** The first photo (in order, which is top to bottom) whose bottom is below [scroll]. */
    fun firstVisible(scroll: Float): Int {
        var lo = 0
        var hi = rect.size
        while (lo < hi) { val mid = (lo + hi) / 2; if (rect[mid].bottom < scroll) lo = mid + 1 else hi = mid }
        return lo
    }

    /** The item to scroll to, and how far into it, so the content [scroll] pixels down is at the top of the screen. */
    fun itemAt(scroll: Float): Pair<Int, Int> {
        var lo = 0
        var hi = tops.size - 1
        while (lo < hi) { val mid = (lo + hi + 1) / 2; if (tops[mid] <= scroll) lo = mid else hi = mid - 1 }
        return lo to (scroll - tops[lo]).toInt().coerceAtLeast(0)
    }
}

/**
 * Lays a level out the way LazyVerticalGrid does: the shell's spacer, any cover and count line, then each group's
 * heading and its photos (squares split the width as GridCells.Fixed does; rows are the photo-shaped rows), lines
 * [gap] apart, sizes rounded to whole pixels as the grid rounds them.
 */
internal fun androidx.compose.ui.unit.Density.buildPlan(
    media: List<Media>, mediaIndex: Map<String, Int>, level: Int, plain: Boolean, groups: List<DayGroup>,
    rowsOf: (DayGroup) -> List<List<Media>>, width: Float, topSpacer: androidx.compose.ui.unit.Dp, prefix: List<Float>, heading: Float
): GridPlan {
    val gapDp = if (plain || level >= 4) 1.dp else 0.5.dp
    val gap = gapDp.roundToPx().toFloat()
    val tops = ArrayList<Float>(media.size + groups.size + 4)
    val headTops = ArrayList<Float>(groups.size)
    val headLabels = ArrayList<String>(groups.size)
    val rects = Array(media.size) { androidx.compose.ui.geometry.Rect.Zero }
    var y = 0f
    tops += 0f
    y += (topSpacer - gapDp).roundToPx().coerceAtLeast(0) + gap
    prefix.forEach { h -> tops += y; y += h + gap }
    val squares = plain || level >= 4
    val total = (width - gap * (level - 1)).toInt()
    val slot = total / level
    val rem = total % level
    val xs = FloatArray(level)
    val ws = IntArray(level)
    run { var x = 0f; for (c in 0 until level) { ws[c] = slot + if (c < rem) 1 else 0; xs[c] = x; x += ws[c] + gap } }
    val lineH = slot + if (rem > 0) 1 else 0
    val rowGap = 0.5.dp.roundToPx().toFloat()
    groups.forEach { g ->
        if (!plain) { tops += y; headTops += y; headLabels += g.label; y += heading + gap }
        if (squares) {
            g.media.forEachIndexed { k, m ->
                val c = k % level
                if (c == 0 && k > 0) y += lineH + gap
                tops += y
                mediaIndex[m.uri]?.let { rects[it] = androidx.compose.ui.geometry.Rect(xs[c], y, xs[c] + ws[c], y + ws[c]) }
            }
            if (g.media.isNotEmpty()) y += lineH + gap
        } else {
            val rows = rowsOf(g)
            rows.forEachIndexed { r, row ->
                tops += y
                val ratios = row.map { it.displayRatio() }
                val sum = ratios.sum()
                val usual = level * 0.72f
                val short = r == rows.lastIndex && row.size < level && sum < usual * 0.8f
                val h = kotlin.math.round(width / (if (short) usual else sum.coerceAtLeast(0.5f)))
                val free = width - (row.size - 1) * rowGap
                var x = 0f
                row.forEachIndexed { k, m ->
                    val w = if (short) h * ratios[k] else free * ratios[k] / sum
                    mediaIndex[m.uri]?.let { rects[it] = androidx.compose.ui.geometry.Rect(x, y, x + w, y + h) }
                    x += w + rowGap
                }
                y += h + gap
            }
        }
    }
    return GridPlan(tops.toFloatArray(), rects, headTops.toFloatArray(), headLabels)
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

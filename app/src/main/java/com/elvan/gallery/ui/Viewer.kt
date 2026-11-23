package com.elvan.gallery.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.elvan.gallery.data.Media
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.composables.icons.materialsymbols.roundedfilled.*
import com.elvan.navil.ElvanFloatingAction
import com.elvan.navil.ElvanFloatingActionBar
import com.elvan.navil.ElvanPopupMenu
import com.elvan.navil.ElvanPopupMenuItem
import com.elvan.navil.ElvanMenuState
import com.elvan.navil.MaterialSymbols
import com.elvan.navil.frostedGlass
import com.elvan.navil.rememberShellColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where each tile was last drawn on screen, so a photo can grow out of its tile and shrink back into it. */
object HeroBounds {
    // Weak: a tile that has left the screen is not kept alive by the map
    private val coords = HashMap<String, java.lang.ref.WeakReference<androidx.compose.ui.layout.LayoutCoordinates>>()
    fun put(key: String, c: androidx.compose.ui.layout.LayoutCoordinates) {
        coords[key] = java.lang.ref.WeakReference(c)
        if (coords.size > 2000) coords.values.removeAll { it.get()?.isAttached != true }
    }
    fun get(key: String): androidx.compose.ui.layout.LayoutCoordinates? = coords[key]?.get()?.takeIf { it.isAttached }
    fun rect(key: String): androidx.compose.ui.geometry.Rect? = get(key)?.boundsInWindow()
}

/**
 * A full-screen pager of pictures that moves like a real gallery: the photo grows out of the tile that was
 * touched and shrinks back into it, follows the finger down and closes, swipe up shows its details, pinch or double-tap
 * zooms. The chrome is Elvan Navil's: a round frosted back button and the floating action bar.
 */
@Composable
fun ZoomViewer(
    models: List<Any>,
    start: Int,
    title: (Int) -> String,
    onBack: () -> Unit,
    actions: @Composable (page: Int, openDetails: () -> Unit) -> List<ElvanFloatingAction>,
    isVideo: (Int) -> Boolean = { false },
    onPlay: (Int) -> Unit = {},
    filmstrip: Boolean = false,
    closing: Boolean = false,
    onClosed: () -> Unit = {},
    heroKey: (Int) -> String? = { null },
    details: (@Composable (Int) -> Unit)? = null,
    topActions: (@Composable (Int, () -> Unit) -> Unit)? = null,
    showTitle: Boolean = true
) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (models.size - 1).coerceAtLeast(0))) { models.size }
    var chrome by remember { mutableStateOf(true) }
    val zoomed = remember { mutableStateMapOf<Int, Boolean>() }
    val colors = rememberShellColors()
    val scope = rememberCoroutineScope()
    val open = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val info = remember { Animatable(0f) }
    var lastDy by remember { mutableFloatStateOf(0f) }
    val heroSpring = spring<Float>(dampingRatio = 0.88f, stiffness = 420f)
    // Samsung Gallery (measured on a Galaxy A53): the photo grows from its tile in ~300 ms and shrinks back in ~300 ms
    val openTween = tween<Float>(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    val closeTween = tween<Float>(300, easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f))
    var opened by remember { mutableStateOf(false) }
    // Pinching the photo smaller (Samsung): it follows the fingers, shrinking and turning, the page shows through;
    // let go small enough and it flies back into its tile, untwisting as it lands
    val pinchS = remember { Animatable(1f) }
    val pinchR = remember { Animatable(0f) }
    val pinchPan = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val pageShow = remember { Animatable(1f) }
    LaunchedEffect(Unit) { open.animateTo(1f, openTween); opened = true }
    LaunchedEffect(closing) {
        if (closing) {
            coroutineScope {
                launch { info.animateTo(0f, heroSpring) }
                launch { dragY.animateTo(0f, closeTween) }
                launch { pinchS.animateTo(1f, closeTween) }
                launch { pinchR.animateTo(0f, closeTween) }
                launch { pinchPan.animateTo(Offset.Zero, tween(300, easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f))) }
                open.animateTo(0f, closeTween)
            }
            onClosed()
        }
    }
    BackHandler(enabled = info.targetValue > 0f && !closing) { scope.launch { info.animateTo(0f, heroSpring) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // Samsung: the details take the lower half; the picture slides up to sit above them
        val infoH = h * 0.52f
        val closeDistance = h * 0.12f
        val screen = androidx.compose.ui.geometry.Rect(0f, 0f, w, h)
        val hero = models.getOrNull(pager.currentPage)?.let { heroKey(pager.currentPage) }?.let { HeroBounds.rect(it) }
            ?.takeIf { it.width > 0f && it.overlaps(screen) }
        // Opening: black at once; closing and pulling down: the page behind fades in
        val shown = (if (!opened && !closing) 1f else open.value) * (1f - (dragY.value / (h * 0.6f))).coerceIn(0f, 1f) * pageShow.value

        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = shown))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    dragY.value > closeDistance -> onBack()
                                    dragY.value > 0f -> dragY.animateTo(0f, heroSpring)
                                    // The panel settles the way the finger was last going
                                    else -> info.animateTo(if (lastDy < 0f || (lastDy == 0f && info.value > 0.5f)) 1f else 0f, heroSpring)
                                }
                            }
                        },
                        onDragCancel = { scope.launch { dragY.animateTo(0f); info.animateTo(if (info.value > 0.5f) 1f else 0f) } }
                    ) { change, dy ->
                        if (zoomed[pager.currentPage] == true || closing) return@detectVerticalDragGestures
                        change.consume()
                        lastDy = dy
                        scope.launch {
                            // Up opens the details; down first closes them, then pulls the photo away
                            if (details != null && (info.value > 0f || (dy < 0f && dragY.value == 0f))) info.snapTo((info.value - dy / infoH).coerceIn(0f, 1f))
                            else dragY.snapTo((dragY.value + dy).coerceAtLeast(0f))
                        }
                    }
                }
        ) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = zoomed[pager.currentPage] != true && info.value == 0f && !closing,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val p = open.value
                    val startScale = hero?.let { it.width / w } ?: 0.85f
                    val pull = 1f - (dragY.value / h) * 0.6f
                    val s = (startScale + (1f - startScale) * p) * pull * pinchS.value
                    scaleX = s; scaleY = s
                    rotationZ = pinchR.value
                    val cx = hero?.center?.x ?: (w / 2)
                    val cy = hero?.center?.y ?: (h / 2)
                    translationX = (cx - w / 2) * (1f - p) + pinchPan.value.x
                    translationY = (cy - h / 2) * (1f - p) + dragY.value - info.value * infoH * 0.55f + pinchPan.value.y
                    alpha = if (hero == null) p else 1f
                },
                key = { it }
            ) { page ->
                if (isVideo(page)) VideoPage(models[page].toString(), isCurrent = pager.currentPage == page && opened, chrome = chrome && opened && !closing) { chrome = !chrome }
                else ZoomableImage(models[page], onTap = {
                    if (info.value > 0f) scope.launch { info.animateTo(0f, heroSpring) } else chrome = !chrome
                }, onZoomChange = { zoomed[page] = it },
                    onPinchOut = { z, r, pan ->
                        if (!closing) scope.launch {
                            val ns = (pinchS.value * z).coerceIn(0.2f, 1f)
                            pinchS.snapTo(ns)
                            pinchR.snapTo(pinchR.value + r)
                            pinchPan.snapTo(pinchPan.value + pan)
                            // The page behind is fully there by the time the photo is ~70% of its size
                            pageShow.snapTo(((ns - 0.7f) / 0.28f).coerceIn(0f, 1f))
                        }
                    },
                    onPinchOutEnd = {
                        if (!closing) {
                            if (pinchS.value < 0.88f) onBack()
                            else scope.launch {
                                launch { pinchR.animateTo(0f, heroSpring) }
                                launch { pinchPan.animateTo(Offset.Zero, spring(dampingRatio = 0.88f, stiffness = 420f)) }
                                launch { pageShow.animateTo(1f, heroSpring) }
                                pinchS.animateTo(1f, heroSpring)
                            }
                        }
                    })
            }

            // The bars appear once the photo has landed and go at once on close
            val chromeAlpha = if (chrome && dragY.value == 0f && pinchS.value >= 0.999f && opened && !closing) 1f else 0f
            Row(
                Modifier.align(Alignment.TopStart).graphicsLayer { alpha = chromeAlpha * (1f - info.value) }.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).frostedGlass(CircleShape, colors).background(Color(0xB8252527), CircleShape)
                        .clip(CircleShape).clickable(enabled = chromeAlpha > 0f, onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { Icon(MaterialSymbols.Rounded.ArrowBack, "Back", tint = colors.textPrimary, modifier = Modifier.size(22.dp)) }
                if (showTitle) Text(
                    if (models.isNotEmpty()) title(pager.currentPage) else "", color = Color.White, fontSize = 15.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp).weight(1f)
                ) else Box(Modifier.weight(1f))
                if (topActions != null && models.isNotEmpty()) topActions(pager.currentPage) {
                    scope.launch { info.animateTo(1f, heroSpring) }
                }
            }

            if (details != null && models.isNotEmpty() && info.value > 0f) Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(with(LocalDensity.current) { infoH.toDp() })
                    .graphicsLayer { translationY = (1f - info.value) * infoH }
                    .background(Color.Black)
            ) { details(pager.currentPage) }

            Column(
                Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = chromeAlpha * (1f - info.value) }.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // A video page shows its own strip of frames instead (its seek bar)
                // Shown for videos too, as Samsung does
                if (filmstrip && models.size > 1) Filmstrip(models, pager)
                if (models.isNotEmpty() && chromeAlpha > 0f) ElvanFloatingActionBar(
                    actions = actions(pager.currentPage) { scope.launch { info.animateTo(1f, heroSpring) } },
                    showLabels = false,
                    barHeight = 48.dp,
                    containerColor = Color(0xB8252527)
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    model: Any, onTap: () -> Unit, onZoomChange: (Boolean) -> Unit,
    onPinchOut: (zoom: Float, rotation: Float, pan: Offset) -> Unit = { _, _, _ -> },
    onPinchOutEnd: () -> Unit = {}
) {
    val scale = remember { Animatable(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(scale.value > 1f) { onZoomChange(scale.value > 1f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        fun clamp(o: Offset, s: Float) = Offset(o.x.coerceIn(-w * (s - 1) / 2, w * (s - 1) / 2), o.y.coerceIn(-h * (s - 1) / 2, h * (s - 1) / 2))
        val context = LocalContext.current
        val zoomModifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { p ->
                        scope.launch {
                            val from = offset
                            val target = if (scale.value > 1f) 1f else 2.5f
                            val to = if (target == 1f) Offset.Zero else clamp((Offset(w / 2, h / 2) - p) * 1.5f, 2.5f)
                            val start = scale.value
                            scale.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 500f)) {
                                val t = ((value - start) / (target - start)).coerceIn(0f, 1f)
                                offset = from + (to - from) * t
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                // Two fingers zoom; once zoomed one finger pans. A single finger at 1x is left to the pager.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var out = false
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        // At full size, two fingers closing take the photo out (pinch to close), turning with them
                        if (fingers > 1 && (out || (scale.value <= 1.001f && event.calculateZoom() < 1f))) {
                            out = true
                            onPinchOut(event.calculateZoom(), event.calculateRotation(), event.calculatePan())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (fingers > 1 || scale.value > 1f) {
                            val s = (scale.value * event.calculateZoom()).coerceIn(1f, 5f)
                            scope.launch { scale.snapTo(s) }
                            offset = clamp(offset + event.calculatePan(), s)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (out) onPinchOutEnd()
                    else if (scale.value <= 1.05f) scope.launch { scale.animateTo(1f); offset = Offset.Zero }
                }
            }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; translationX = offset.x; translationY = offset.y }
        Box(zoomModifier) {
            // The tile picture is already in memory: it shows at once, and the full photo draws over it when ready
            AsyncImage(ImageRequest.Builder(context).data(thumbModel(model as? String)).size(320).apply { (model as? String)?.let { placeholderMemoryCacheKey(thumbPlaceholder(it)) } }.build(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            AsyncImage(ImageRequest.Builder(context).data(model).build(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun VideoPoster(model: Any, onTap: () -> Unit, onPlay: () -> Unit) {
    val colors = rememberShellColors()
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }, contentAlignment = Alignment.Center) {
        AsyncImage(ImageRequest.Builder(LocalContext.current).data(model).build(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Box(Modifier.size(72.dp).frostedGlass(CircleShape, colors).clip(CircleShape).clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
            Icon(Sym.RoundedFilled.Play_arrow, "Play", tint = colors.textPrimary, modifier = Modifier.size(36.dp))
        }
    }
}

/** Small thumbnails under the picture; the current one sits in the middle and is outlined. */
/**
 * Samsung Gallery's strip (measured on a Galaxy A53): 27 x 36 dp thumbnails 4 dp apart with small round corners; the one
 * in the middle grows to 33 x 44 dp and its neighbours step aside. It follows the photo as it is swiped, and dragging it
 * shows the photo in the middle at once (no slide); it settles on a thumbnail.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Filmstrip(models: List<Any>, pager: androidx.compose.foundation.pager.PagerState) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = pager.currentPage)
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var userDriven by remember { mutableStateOf(false) }
    val itemW = 27.dp
    val gap = 4.dp
    val pitchPx = with(density) { (itemW + gap).toPx() }
    LaunchedEffect(dragged) { if (dragged) userDriven = true }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val side = (maxWidth - itemW) / 2
        // The photo moves the strip, unless the strip is being moved by hand
        LaunchedEffect(pager) {
            androidx.compose.runtime.snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { at ->
                if (userDriven) return@collect
                val i = kotlin.math.floor(at).toInt().coerceAtLeast(0)
                list.scrollToItem(i, ((at - i) * pitchPx).toInt())
            }
        }
        // The strip moved by hand shows the photo in its middle straight away
        LaunchedEffect(list) {
            androidx.compose.runtime.snapshotFlow { Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.isScrollInProgress) }.collect { (i, off, moving) ->
                if (!userDriven) return@collect
                val target = (i + kotlin.math.round(off / pitchPx).toInt()).coerceIn(0, models.size - 1)
                if (target != pager.currentPage) pager.scrollToPage(target)
                if (!moving) userDriven = false
            }
        }
        LazyRow(
            state = list,
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(gap),
            flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(list),
            modifier = Modifier.height(44.dp)
        ) {
            itemsIndexed(models) { i, m ->
                Box(
                    Modifier.size(itemW, 44.dp).graphicsLayer {
                        // How close this thumbnail is to the middle: 1 there, 0 one step away or more
                        val at = list.firstVisibleItemIndex + list.firstVisibleItemScrollOffset / pitchPx
                        val d = i - at
                        val near = (1f - kotlin.math.abs(d)).coerceIn(0f, 1f)
                        val grow = 33f / 27f
                        scaleX = 1f + (grow - 1f) * near
                        scaleY = 1f + (grow - 1f) * near
                        // Everything either side steps aside by the extra width the middle one takes
                        translationX = (grow - 1f) * itemW.toPx() / 2f * d.coerceIn(-1f, 1f)
                    }
                        .clickable { userDriven = false; scope.launch { pager.scrollToPage(i) } }
                ) {
                    Box(Modifier.align(Alignment.Center).size(itemW, 36.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1F1F1F))) {
                        Thumb(m.toString(), Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}


/** The pictures viewer: favourite, edit, details, share, delete, rename, wallpaper, print. */
@Composable
fun MediaViewer(media: List<Media>, start: Int, closing: Boolean, onBack: () -> Unit, onClosed: () -> Unit, onDeleted: () -> Unit, onEdit: (Media) -> Unit = {}) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current
    val colors = rememberShellColors()
    var items by remember(media) { mutableStateOf(media) }
    var dialog by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    val filmstrip = remember { defaultPrefs(context).getBoolean("ENABLE_FILMSTRIP", true) }
    BackHandler(enabled = !closing, onBack = onBack)

    fun removeAt(pos: Int) {
        items = items.toMutableList().also { it.removeAt(pos) }
        onDeleted()
        if (items.isEmpty()) onBack()
    }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    val systemDelete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val pos = pendingDelete
        pendingDelete = null
        if (result.resultCode == Activity.RESULT_OK && pos != null) removeAt(pos)
    }

    ZoomViewer(
        models = items.map { it.uri },
        start = start,
        title = { items[it].name },
        onBack = onBack,
        closing = closing,
        onClosed = onClosed,
        heroKey = { items.getOrNull(it)?.uri },
        showTitle = false,
        details = { page -> MediaDetails(items[page]) },
        filmstrip = filmstrip,
        isVideo = { items[it].isVideo },
        onPlay = { pos ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(items[pos].uri.toUri(), "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (e: Exception) { Toast.makeText(context, "No video player found", Toast.LENGTH_SHORT).show() }
        },
        topActions = { page, openDetails ->
            val uri = items[page].uri.toUri()
            Box {
                Row(
                    Modifier.height(44.dp).frostedGlass(CircleShape, colors)
                        .background(Color(0xB8252527), CircleShape).clip(CircleShape),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable { onEdit(items[page]) }, contentAlignment = Alignment.Center) {
                        Icon(Sym.Rounded.Rotate_right, "Rotate", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = openDetails), contentAlignment = Alignment.Center) {
                        Icon(Sym.Rounded.Visibility, "View details", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable { moreOpen = true }, contentAlignment = Alignment.Center) {
                        Icon(MaterialSymbols.Rounded.MoreVert, "More", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                ElvanPopupMenu(
                    expanded = moreOpen, colors = colors,
                    onDismissRequest = { ElvanMenuState.isMenuOpen = false; moreOpen = false },
                    items = listOf(
                        ElvanPopupMenuItem("Rename", MaterialSymbols.Rounded.Edit, { moreOpen = false; dialog = "rename" to page }),
                        ElvanPopupMenuItem("Open in another app", MaterialSymbols.Rounded.OpenInNew, {
                            moreOpen = false
                            try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_EDIT).setDataAndType(uri, if (items[page].isVideo) "video/*" else "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null)) }
                            catch (e: Exception) { Toast.makeText(context, "No editor found!", Toast.LENGTH_SHORT).show() }
                        }),
                        ElvanPopupMenuItem("Copy to clipboard", Sym.Rounded.Content_copy, {
                            moreOpen = false
                            context.getSystemService(android.content.ClipboardManager::class.java)
                                ?.setPrimaryClip(android.content.ClipData.newUri(context.contentResolver, items[page].name, uri))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }),
                        ElvanPopupMenuItem("Set as wallpaper", Sym.Rounded.Wallpaper, {
                            moreOpen = false
                            try {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_ATTACH_DATA).apply {
                                    addCategory(Intent.CATEGORY_DEFAULT); setDataAndType(uri, "image/*"); putExtra("mimeType", "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Set as wallpaper"))
                            } catch (e: Exception) { Toast.makeText(context, "Unable to set wallpaper", Toast.LENGTH_SHORT).show() }
                        }),
                        ElvanPopupMenuItem("Move to private safe", Sym.Rounded.Lock, {
                            moreOpen = false
                            // A copy goes into the safe; the original then goes to the phone's trash with Android's own confirmation
                            scope.launch {
                                val kept = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.elvan.gallery.data.PrivateVault(context).add(uri) != null }
                                if (!kept) Toast.makeText(context, "Could not move to private safe", Toast.LENGTH_SHORT).show()
                                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    pendingDelete = page
                                    systemDelete.launch(IntentSenderRequest.Builder(MediaStore.createTrashRequest(context.contentResolver, listOf(uri), true).intentSender).build())
                                } else Toast.makeText(context, "Copied to private safe", Toast.LENGTH_SHORT).show()
                            }
                        }),
                        ElvanPopupMenuItem("Print", MaterialSymbols.Rounded.Print, { moreOpen = false; printImage(context, uri) })
                    )
                )
            }
        },
        actions = { page, openDetails ->
            val item = items[page]
            val uri = item.uri.toUri()
            val favourite = item.uri in Favourites.all(context)
            listOf(
                ElvanFloatingAction("Favourite", if (favourite) Sym.RoundedFilled.Favorite else Sym.Rounded.Favorite,
                    { Favourites.toggle(context, item.uri) }, customColor = if (favourite) Color(0xFFFF5A5F) else null),
                // The built-in editor (photo: crop, rotate, filters, adjust; video: trim, mute)
                ElvanFloatingAction("Edit", MaterialSymbols.Rounded.Edit, { onEdit(item) }),
                ElvanFloatingAction("Details", Sym.Rounded.Info, openDetails),
                ElvanFloatingAction("Share", MaterialSymbols.Rounded.Share, {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = if (item.isVideo) "video/*" else "image/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share Image"))
                }),
                ElvanFloatingAction("Delete", Sym.Rounded.Delete, {
                    // Android 11+: the photo goes to the phone's trash (restorable), with Android's own confirmation
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pendingDelete = page
                        systemDelete.launch(IntentSenderRequest.Builder(MediaStore.createTrashRequest(context.contentResolver, listOf(uri), true).intentSender).build())
                    } else dialog = "delete" to page
                })
            )
        }
    )

    when (val d = dialog) {
        null -> {}
        else -> when (d.first) {
            "delete" -> ElvanDialog(
                title = "Delete this photo?",
                text = "This photo will be deleted from this device.\nAlso can not be accessed or restored from gallery.",
                confirm = "Delete", destructive = true, onDismiss = { dialog = null }
            ) {
                dialog = null
                val pos = d.second
                val uri = items[pos].uri.toUri()
                try {
                    if (context.contentResolver.delete(uri, null, null) > 0) removeAt(pos)
                    else Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    // Another app's picture: Android asks the user itself
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pendingDelete = pos
                        systemDelete.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender).build())
                    } else Toast.makeText(context, "Allow file access permission to delete", Toast.LENGTH_LONG).show()
                }
            }
            "rename" -> {
                val name = items[d.second].name
                val dot = name.lastIndexOf('.')
                val ext = if (dot > 0) name.substring(dot + 1) else ""
                NameDialog("Rename", if (dot > 0) name.substring(0, dot) else name, "Rename", { null }, { dialog = null }) { newName ->
                    dialog = null
                    val pos = d.second
                    val final = if (ext.isNotEmpty()) "$newName.$ext" else newName
                    try {
                        val rows = context.contentResolver.update(items[pos].uri.toUri(), ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, final) }, null, null)
                        if (rows > 0) {
                            items = items.toMutableList().also { it[pos] = it[pos].copy(name = final) }
                            Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                            onDeleted()
                        } else Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                    } catch (e: SecurityException) {
                        Toast.makeText(context, "Allow file access permission to rename", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Rename error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}


private fun printImage(context: Context, uri: Uri) {
    try {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        if (bitmap == null) { Toast.makeText(context, "Unable to load image", Toast.LENGTH_SHORT).show(); return }
        androidx.print.PrintHelper(context).apply { scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT }.printBitmap("Gallery image", bitmap)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to print image", Toast.LENGTH_SHORT).show()
    }
}

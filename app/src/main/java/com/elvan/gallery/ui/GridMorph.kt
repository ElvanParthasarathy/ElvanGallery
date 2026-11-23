package com.elvan.gallery.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable

/**
 * Where tile [index] sits in a grid of [columns] across a [width]-pixel page, measured from the first tile's top.
 * A grid page supplies this so a pinch can move every tile from its slot now to its slot at the next level.
 */
fun interface GridSlots {
    fun Density.slot(index: Int, columns: Int, width: Float, target: Boolean): Rect
}

/**
 * Samsung's pinch: as the fingers move, every tile shrinks or grows and slides toward its place in the next
 * layout, in step with the fingers; let go past about a third of the way and it finishes, otherwise it springs back.
 */
class GridMorph(val slots: GridSlots, val scope: kotlinx.coroutines.CoroutineScope) {
    val progress = Animatable(0f)
    var target by mutableStateOf<Int?>(null)
    /** The first tile on screen when the pinch began: it stays put and the rest move around it. */
    var anchor by mutableIntStateOf(0)
    var width by mutableFloatStateOf(0f)
    /** Just after a level change: tiles land where they are instead of animating there a second time. */
    var settling by mutableStateOf(false)
    /** Where the fingers are, in the grid's pixels. */
    var focus = androidx.compose.ui.geometry.Offset.Zero
    /** How far down the anchor tile the fingers are (0 top, 1 bottom): that point stays under the fingers. */
    var anchorFy = 0f
    /**
     * Called as a pinch begins with where the fingers are: the page picks the tile under them (setting [anchorFy],
     * [sNow] and [scrollFor]) and returns its index; null leaves the first tile on screen as the anchor.
     */
    var begin: ((androidx.compose.ui.geometry.Offset) -> Int?)? = null
    /** How far the page is scrolled now, in the page's own layout model. */
    var sNow = 0f
    /** How far the page will be scrolled at a level, so the anchor stays under the fingers (null: page has no model). */
    var scrollFor: ((Int) -> Float)? = null
    /** Where the grid sits in the window as the pinch begins (the shell moves it as the header folds). */
    var gridTop = 0f
    /** Set by the page as a level changes: true once the grid shows the new level where it should be. */
    var landed: (() -> Boolean)? = null
    /** [scrollFor] worked out once per level for this pinch. */
    val sThen = HashMap<Int, Float>()
    /** Counts pinches: only the latest one may tidy up after itself. */
    var gen = 0
    /** Fingers on the grid now, and whether a pinch's ending is still playing: with neither, a pinch must be at rest. */
    var fingers by mutableIntStateOf(0)
    var finishing by mutableStateOf(false)
    /** Names on tiles fade back in once a new level has landed. */
    val labels = Animatable(1f)
    /**
     * How much of the names on tiles to show (Samsung): they fade out over the first 40% of a pinch instead of
     * shrinking with their tiles, stay away while the tiles move, and fade back in over ~300 ms once the level lands.
     */
    /** Back to rest: no pinch under way, tiles in their slots, names shown. */
    suspend fun tidy() {
        target = null
        settling = false
        finishing = false
        progress.snapTo(0f)
        labels.snapTo(1f)
    }

    fun labelAlpha(): Float = if (target != null) labels.value * (1f - progress.value * 2.5f).coerceIn(0f, 1f) else labels.value
}

fun Modifier.pinchMorph(morph: GridMorph, grid: LazyGridState, columns: Int, levels: List<Int>, firstTile: Int, onChange: (Int) -> Unit): Modifier =
    onSizeChanged { morph.width = it.width.toFloat() }
    // Counts the fingers down (never restarted, never consumes), for the watch in [QuietWhilePinching]
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) morph.fingers = awaitPointerEvent(PointerEventPass.Initial).changes.count { it.pressed }
        }
    }
    .pointerInput(columns, levels, firstTile) {
        val finish = spring<Float>(dampingRatio = 0.95f, stiffness = 520f)
        run {
            val scope = morph.scope
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var zoom = 1f
                var pinched = false
                var ended = false
                // A pinch cut off half way (the previous pinch landing changes the level, which restarts this handler
                // while fingers are still down) puts the tiles back, so the grid is never left frozen between levels
                try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) {
                        if (!pinched) {
                            morph.gen++
                            // Midpoint of every finger down now (calculateCentroid skips a finger that has only just landed)
                            val down = event.changes.filter { it.pressed }
                            morph.focus = down.fold(androidx.compose.ui.geometry.Offset.Zero) { acc, ch -> acc + ch.position } / down.size.toFloat()
                            morph.sThen.clear(); morph.scrollFor = null; morph.anchorFy = 0f
                            morph.anchor = morph.begin?.invoke(morph.focus) ?: (grid.firstVisibleItemIndex - firstTile).coerceAtLeast(0)
                        }
                        pinched = true
                        zoom = (zoom * event.calculateZoom()).coerceIn(0.35f, 2.5f)
                        event.changes.forEach { it.consume() }
                        val at = levels.indexOf(columns)
                        val next = when {
                            zoom < 1f && at in 0 until levels.lastIndex -> levels[at + 1]
                            zoom > 1f && at > 0 -> levels[at - 1]
                            else -> null
                        }
                        // Where the page will sit at that level is settled before anything is drawn toward it
                        val plan = morph.scrollFor
                        if (next != null && plan != null && next !in morph.sThen) morph.sThen[next] = plan(next)
                        morph.target = next
                        val p = kotlin.math.abs(kotlin.math.ln(zoom)) / kotlin.math.ln(if (zoom < 1f) 2.6f else 1.7f)
                        scope.launch { morph.progress.snapTo(if (morph.target == null) 0f else p.coerceIn(0f, 1f)) }
                    }
                    else if (pinched) event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
                ended = true
                } finally {
                    val g = morph.gen
                    if (pinched && !ended) scope.launch { if (morph.gen == g) morph.tidy() }
                }
                if (!pinched) return@awaitEachGesture
                val next = morph.target
                val g = morph.gen
                morph.finishing = true
                scope.launch {
                  // Whatever cuts this short (another pinch's frame, the page going), the grid is left at rest
                  try {
                    if (next != null && morph.progress.value > 0.3f) {
                        morph.progress.animateTo(1f, finish)
                        morph.settling = true
                        onChange(next)
                        // Keep drawing the finished pinch until the grid is laid out at the new level and scrolled to
                        // where the photos landed (two frames at least, at most ten)
                        var frames = 0
                        do { androidx.compose.runtime.withFrameNanos { }; frames++ } while (frames < 10 && (frames < 2 || morph.landed?.invoke() == false))
                        morph.landed = null
                        morph.labels.snapTo(0f)
                        morph.target = null
                        morph.progress.snapTo(0f)
                        launch { morph.labels.animateTo(1f, androidx.compose.animation.core.tween(300)) }
                        delay(350)
                        morph.settling = false
                    } else {
                        morph.progress.animateTo(0f, finish)
                        morph.target = null
                    }
                  } finally {
                    if (morph.gen == g) {
                        if (morph.target != null) withContext(NonCancellable) { morph.tidy() }
                        morph.finishing = false
                    }
                  }
                }
            }
        }
    }

/** A cover's corner for its width (Samsung's, measured on a Galaxy A53): 4 dp plus about a tenth of the width. */
fun coverCorner(widthPx: Float, density: androidx.compose.ui.unit.Density): Float = with(density) { 4.dp.toPx() } + 0.103f * widthPx

/** Rounded corners that follow the card's size, as [coverCorner]. */
val CoverShape = object : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val r = coverCorner(size.width, density)
        return androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, r, r))
    }
}

/**
 * The part of tile [index] (in its own pixels, [size] being its laid-out size) that a pinch shows right now: the
 * in-between shape, centred; null when no pinch moves it. The picture is drawn cropped to exactly this, from the
 * original photo, so each moment of the pinch shows what a card of that shape shows, and the last one what the landed
 * card shows.
 */
fun GridMorph.cropRegion(index: Int, columns: Int, density: Density, size: androidx.compose.ui.geometry.Size): androidx.compose.ui.geometry.Rect? {
    val to = target ?: return null
    if (to == columns) return null
    val p = progress.value
    if (p == 0f || width <= 0f) return null
    val now = with(slots) { density.slot(index, columns, width, false) }
    val then = with(slots) { density.slot(index, to, width, true) }
    val boxW = now.width + (then.width - now.width) * p
    val boxH = now.height + (then.height - now.height) * p
    val u = maxOf(boxW / now.width, boxH / now.height)
    val cw = boxW / u
    val ch = boxH / u
    return androidx.compose.ui.geometry.Rect((size.width - cw) / 2f, (size.height - ch) / 2f, (size.width + cw) / 2f, (size.height + ch) / 2f)
}

/** How much a pinch is scaling tile [index] right now (1 when no pinch moves it). */
fun GridMorph.tileScale(index: Int, columns: Int, density: Density): Float {
    val to = target ?: return 1f
    if (to == columns) return 1f
    val p = progress.value
    if (p == 0f || width <= 0f) return 1f
    val now = with(slots) { density.slot(index, columns, width, false) }
    val then = with(slots) { density.slot(index, to, width, true) }
    val boxW = now.width + (then.width - now.width) * p
    val boxH = now.height + (then.height - now.height) * p
    return maxOf(boxW / now.width, boxH / now.height)
}

/**
 * Draws tile [index] part way between its slot now and its slot at the pinch's next level. The picture is scaled
 * evenly and clipped to the in-between shape (Samsung's): a tile that changes shape on the way (a 5:6 cover becoming a
 * square) shows more or less of its picture instead of stretching, and its corners grow and shrink with it.
 */
fun Modifier.morphTile(morph: GridMorph, index: Int, columns: Int): Modifier = graphicsLayer {
    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Auto
    val to = morph.target ?: return@graphicsLayer
    if (to == columns) return@graphicsLayer
    val p = morph.progress.value
    val w = morph.width
    if (p == 0f || w <= 0f) return@graphicsLayer
    with(morph.slots) {
        val now = slot(index, columns, w, false)
        val nowAnchor = slot(morph.anchor, columns, w, false)
        val then = slot(index, to, w, true)
        val thenAnchor = slot(morph.anchor, to, w, true)
        val sThen = morph.sThen[to]
        // Where the tile's top-left goes, relative to where it is laid out now
        val dx = (then.left - now.left) * p
        val dy = if (morph.scrollFor != null && sThen != null) {
            // Both levels on screen exactly: where the tile is now, and where the page will show it
            ((then.top - sThen) - (now.top - morph.sNow)) * p
        } else {
            val fy = morph.anchorFy
            ((then.top - (thenAnchor.top + fy * thenAnchor.height)) - (now.top - (nowAnchor.top + fy * nowAnchor.height))) * p
        }
        // The in-between size, and one even scale that covers it
        val boxW = now.width + (then.width - now.width) * p
        val boxH = now.height + (then.height - now.height) * p
        val u = maxOf(boxW / now.width, boxH / now.height)
        // The part of the tile (in its own pixels) that stays in view: centred, the in-between shape
        val cw = boxW / u
        val ch = boxH / u
        val cx = (size.width - cw) / 2f
        val cy = (size.height - ch) / 2f
        scaleX = u; scaleY = u
        translationX = dx - u * cx
        translationY = dy - u * cy
        // The corner follows the in-between size on screen, as Samsung's cards' do
        val r = coverCorner(boxW, this@graphicsLayer) / u
        shape = object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
                androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(cx, cy, cx + cw, cy + ch, r, r))
        }
        clip = true
        // While it moves, the card is drawn once into its own texture and only moved and scaled after that: the names'
        // blurred shadow and the shade are not drawn again every frame
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }
    transformOrigin = TransformOrigin(0f, 0f)
}

/**
 * While a pinch moves the tiles, the grid is left out of the accessibility tree: with an accessibility service on,
 * every frame would otherwise re-describe hundreds of moving tiles. Readers get the settled grid once it lands.
 * Only this wrapper recomposes when the pinch starts and ends; [content] is skipped.
 */
@androidx.compose.runtime.Composable
fun QuietWhilePinching(morph: GridMorph, content: @androidx.compose.runtime.Composable () -> Unit) {
    // The last guard: a pinch left part way with no finger down and no ending playing (however it happened) is put
    // back at rest, so the grid can never stay frozen between two levels
    androidx.compose.runtime.LaunchedEffect(morph) {
        androidx.compose.runtime.snapshotFlow { morph.target != null && morph.fingers == 0 && !morph.finishing }.collectLatest { stuck ->
            if (stuck) { delay(400); morph.tidy() }
        }
    }
    androidx.compose.foundation.layout.Box(
        if (morph.target != null) Modifier.clearAndSetSemantics { } else Modifier
    ) { content() }
}

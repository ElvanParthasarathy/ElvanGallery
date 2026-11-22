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
    /** [scrollFor] worked out once per level for this pinch. */
    val sThen = HashMap<Int, Float>()
}

fun Modifier.pinchMorph(morph: GridMorph, grid: LazyGridState, columns: Int, levels: List<Int>, firstTile: Int, onChange: (Int) -> Unit): Modifier =
    onSizeChanged { morph.width = it.width.toFloat() }.pointerInput(columns, levels, firstTile) {
        val finish = spring<Float>(dampingRatio = 0.95f, stiffness = 520f)
        run {
            val scope = morph.scope
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var zoom = 1f
                var pinched = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) {
                        if (!pinched) {
                            // Midpoint of every finger down now (calculateCentroid skips a finger that has only just landed)
                            val down = event.changes.filter { it.pressed }
                            morph.focus = down.fold(androidx.compose.ui.geometry.Offset.Zero) { acc, ch -> acc + ch.position } / down.size.toFloat()
                            morph.sThen.clear(); morph.scrollFor = null; morph.anchorFy = 0f
                            morph.anchor = morph.begin?.invoke(morph.focus) ?: (grid.firstVisibleItemIndex - firstTile).coerceAtLeast(0)
                        }
                        pinched = true
                        zoom = (zoom * event.calculateZoom()).coerceIn(0.4f, 2.5f)
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
                        val p = kotlin.math.abs(kotlin.math.ln(zoom)) / kotlin.math.ln(1.7f)
                        scope.launch { morph.progress.snapTo(if (morph.target == null) 0f else p.coerceIn(0f, 1f)) }
                    }
                    else if (pinched) event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
                if (!pinched) return@awaitEachGesture
                val next = morph.target
                scope.launch {
                    if (next != null && morph.progress.value > 0.3f) {
                        morph.progress.animateTo(1f, finish)
                        morph.settling = true
                        onChange(next)
                        // Two frames: the grid is re-laid at the new level, where the morph is already at rest
                        androidx.compose.runtime.withFrameNanos { }
                        androidx.compose.runtime.withFrameNanos { }
                        morph.target = null
                        morph.progress.snapTo(0f)
                        delay(350)
                        morph.settling = false
                    } else {
                        morph.progress.animateTo(0f, finish)
                        morph.target = null
                    }
                }
            }
        }
    }

/** Draws tile [index] part way between its slot now and its slot at the pinch's next level. */
fun Modifier.morphTile(morph: GridMorph, index: Int, columns: Int): Modifier = graphicsLayer {
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
        translationX = (then.left - now.left) * p
        val sThen = morph.sThen[to]
        translationY = if (morph.scrollFor != null && sThen != null) {
            // Both levels on screen exactly: where the tile is now, and where the page will show it
            ((then.top - sThen) - (now.top - morph.sNow)) * p
        } else {
            val fy = morph.anchorFy
            ((then.top - (thenAnchor.top + fy * thenAnchor.height)) - (now.top - (nowAnchor.top + fy * nowAnchor.height))) * p
        }
        scaleX = 1f + (then.width / now.width - 1f) * p
        scaleY = 1f + (then.height / now.height - 1f) * p
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
    androidx.compose.foundation.layout.Box(
        if (morph.target != null) Modifier.clearAndSetSemantics { } else Modifier
    ) { content() }
}

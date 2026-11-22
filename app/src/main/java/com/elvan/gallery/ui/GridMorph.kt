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
class GridMorph(val slots: GridSlots) {
    val progress = Animatable(0f)
    var target by mutableStateOf<Int?>(null)
    /** The first tile on screen when the pinch began: it stays put and the rest move around it. */
    var anchor by mutableIntStateOf(0)
    var width by mutableFloatStateOf(0f)
    /** Just after a level change: tiles land where they are instead of animating there a second time. */
    var settling by mutableStateOf(false)
}

fun Modifier.pinchMorph(morph: GridMorph, grid: LazyGridState, columns: Int, levels: List<Int>, firstTile: Int, onChange: (Int) -> Unit): Modifier =
    onSizeChanged { morph.width = it.width.toFloat() }.pointerInput(columns, levels, firstTile) {
        val finish = spring<Float>(dampingRatio = 0.95f, stiffness = 520f)
        coroutineScope {
            val scope = this
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var zoom = 1f
                var pinched = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) {
                        if (!pinched) morph.anchor = (grid.firstVisibleItemIndex - firstTile).coerceAtLeast(0)
                        pinched = true
                        zoom = (zoom * event.calculateZoom()).coerceIn(0.4f, 2.5f)
                        event.changes.forEach { it.consume() }
                        val at = levels.indexOf(columns)
                        morph.target = when {
                            zoom < 1f && at in 0 until levels.lastIndex -> levels[at + 1]
                            zoom > 1f && at > 0 -> levels[at - 1]
                            else -> null
                        }
                        val p = if (zoom < 1f) (1f - zoom) / 0.45f else (zoom - 1f) / 0.8f
                        scope.launch { morph.progress.snapTo(if (morph.target == null) 0f else p.coerceIn(0f, 1f)) }
                    }
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
        translationY = ((then.top - thenAnchor.top) - (now.top - nowAnchor.top)) * p
        scaleX = 1f + (then.width / now.width - 1f) * p
        scaleY = 1f + (then.height / now.height - 1f) * p
    }
    transformOrigin = TransformOrigin(0f, 0f)
}

package com.elvan.gallery.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import kotlinx.coroutines.launch

/**
 * Draws a photo's grid thumbnail, centre-cropped, straight from Coil: no composition of its own, so a grid can build
 * hundreds of tiles at once (a pinch's new level) without a pause. Whatever copy is in memory shows at once; a sharper
 * one is fetched when that copy is smaller than [px]. A photo that was not in memory fades in over 150 ms instead of
 * popping onto the empty tile.
 */
fun Modifier.thumbnail(uri: String, px: Int): Modifier = this then ThumbElement(uri, px)

private data class ThumbElement(val uri: String, val px: Int) : ModifierNodeElement<ThumbNode>() {
    override fun create() = ThumbNode(uri, px)
    override fun update(node: ThumbNode) = node.set(uri, px)
}

private class ThumbNode(private var uri: String, private var px: Int) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var image: ImageBitmap? = null
    private var job: Disposable? = null
    private val fade = androidx.compose.animation.core.Animatable(1f)

    override fun onAttach() = load()
    override fun onDetach() { job?.dispose(); job = null; image = null }

    fun set(uri: String, px: Int) {
        if (uri == this.uri && px == this.px) return
        val samePhoto = uri == this.uri
        this.uri = uri; this.px = px
        job?.dispose(); job = null
        if (!samePhoto) image = null
        if (isAttached) load()
    }

    private fun load() {
        val context = currentValueOf(LocalContext)
        val loader = SingletonImageLoader.get(context)
        val cached = (loader.memoryCache?.get(thumbPlaceholder(uri))?.image as? BitmapImage)?.bitmap
        if (cached != null) {
            image = cached.asImageBitmap()
            if (maxOf(cached.width, cached.height) >= px * 0.9f) return
        }
        val want = uri
        job = loader.enqueue(
            ImageRequest.Builder(context).data(thumbModel(uri)).size(px).memoryCacheKey(thumbPlaceholder(uri))
                .target(onSuccess = { result ->
                    if (want == uri && isAttached) {
                        val first = image == null
                        (result as? BitmapImage)?.bitmap?.let { image = it.asImageBitmap() }
                        if (first) coroutineScope.launch { fade.snapTo(0f); fade.animateTo(1f, androidx.compose.animation.core.tween(150)) }
                        invalidateDraw()
                    }
                })
                .build()
        )
    }

    override fun ContentDrawScope.draw() {
        image?.let { img ->
            val dw = size.width.toInt().coerceAtLeast(1)
            val dh = size.height.toInt().coerceAtLeast(1)
            val scale = maxOf(dw / img.width.toFloat(), dh / img.height.toFloat())
            val sw = (dw / scale).toInt().coerceIn(1, img.width)
            val sh = (dh / scale).toInt().coerceIn(1, img.height)
            drawImage(img, IntOffset((img.width - sw) / 2, (img.height - sh) / 2), IntSize(sw, sh), IntOffset.Zero, IntSize(dw, dh), alpha = fade.value)
        }
        drawContent()
    }
}

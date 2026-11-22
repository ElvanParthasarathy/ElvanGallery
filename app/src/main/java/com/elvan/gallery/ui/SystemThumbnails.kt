package com.elvan.gallery.ui

import android.net.Uri
import android.os.Build
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.Dimension

/** A grid thumbnail from the phone's own thumbnail cache: fast, and right for videos too. */
data class SystemThumb(val uri: String)

/** The model a tile loads: the phone's thumbnail where Android has the API (10+), else the file itself. */
fun thumbModel(uri: String?): Any? = if (uri != null && Build.VERSION.SDK_INT >= 29 && uri.startsWith("content://")) SystemThumb(uri) else uri

class SystemThumbFetcher(private val data: SystemThumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val px = (options.size.width as? Dimension.Pixels)?.px?.coerceAtLeast(48) ?: 320
        val loaded = options.context.contentResolver.loadThumbnail(Uri.parse(data.uri), android.util.Size(px, px), null)
        // Moved to the GPU once here: a plain bitmap is copied to the GPU again whenever the GPU's cache drops it,
        // which happens constantly with hundreds of thumbnails moving in a pinch
        val bitmap = loaded.copy(android.graphics.Bitmap.Config.HARDWARE, false)?.also { loaded.recycle() } ?: loaded
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<SystemThumb> {
        override fun create(data: SystemThumb, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (Build.VERSION.SDK_INT >= 29) SystemThumbFetcher(data, options) else null
    }
}

/**
 * Lets Coil keep these in its memory cache (no key means no caching). One key per photo, not per size: Coil reuses a
 * bigger cached copy for a smaller tile, and [thumbPlaceholder] shows a smaller one while a bigger loads, so a tile
 * never goes blank when a pinch changes its size.
 */
class SystemThumbKeyer : Keyer<SystemThumb> {
    override fun key(data: SystemThumb, options: Options) = thumbKey(data.uri)
}

fun thumbKey(uri: String) = "thumb:$uri"

/** Whatever copy of this photo is already in memory, to show until the requested one is ready. */
fun thumbPlaceholder(uri: String) = coil3.memory.MemoryCache.Key(thumbKey(uri))

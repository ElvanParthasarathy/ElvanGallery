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
        val bitmap = options.context.contentResolver.loadThumbnail(Uri.parse(data.uri), android.util.Size(px, px), null)
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<SystemThumb> {
        override fun create(data: SystemThumb, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (Build.VERSION.SDK_INT >= 29) SystemThumbFetcher(data, options) else null
    }
}

/** Lets Coil keep these in its memory cache (no key means no caching). */
class SystemThumbKeyer : Keyer<SystemThumb> {
    override fun key(data: SystemThumb, options: Options) = "thumb:${data.uri}:${options.size}"
}

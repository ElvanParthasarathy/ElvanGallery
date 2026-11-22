package com.elvan.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.elvan.gallery.ui.SystemThumbFetcher
import com.elvan.gallery.ui.SystemThumbKeyer

/** Tiles come from the phone's thumbnail cache; full videos frames from Coil's video decoder. */
class GalleryApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).components {
            add(SystemThumbFetcher.Factory())
            add(SystemThumbKeyer())
            add(VideoFrameDecoder.Factory())
        }.build()
}

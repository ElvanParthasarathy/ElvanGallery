package com.elvan.gallery.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.elvan.gallery.data.AlbumSettings
import com.elvan.gallery.data.Media
import com.elvan.gallery.data.MediaQuery
import com.elvan.gallery.data.RECENT
import com.elvan.gallery.data.VIDEOS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the screens show, read in one MediaStore query off the main thread. */
class GalleryLibrary(private val context: Context, private val scope: CoroutineScope) {
    /** Every picture and video, newest first. */
    var all by mutableStateOf(emptyList<Media>()); private set
    var albums by mutableStateOf<AlbumsModel?>(null); private set
    var loaded by mutableStateOf(false); private set
    var showSortSheet by mutableStateOf(false)
    val settings = AlbumSettings(context)

    fun reload() {
        scope.launch {
            val media = withContext(Dispatchers.IO) { MediaQuery.all(context) }
            all = media
            loaded = true
            albums?.refresh(media) ?: run {
                albums = AlbumsModel(context, media, settings, defaultPrefs(context).getBoolean("SHOW_ESSENTIAL_ALBUMS", false))
            }
        }
    }

    /** One album's pictures, newest first. */
    fun album(name: String): List<Media> = when (name) {
        RECENT -> all
        VIDEOS -> all.filter { it.isVideo }
        else -> all.filter { it.album == name }
    }
}

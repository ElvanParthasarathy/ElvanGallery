package com.elvan.gallery.ui

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.elvan.gallery.data.Media
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.elvan.navil.ElvanFloatingAction
import com.elvan.navil.ElvanFloatingActionBar
import com.elvan.navil.ElvanShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Menu shortcuts, each a page of pictures: Videos, Favourites, Recent. */
enum class MediaKind(val title: String) { Videos("Videos"), Favourites("Favourites"), Recent("Recent") }

fun GalleryLibrary.mediaOf(kind: MediaKind, context: Context): List<Media> = when (kind) {
    MediaKind.Videos -> album(com.elvan.gallery.data.VIDEOS)
    MediaKind.Recent -> all.take(500)
    MediaKind.Favourites -> {
        val favourites = Favourites.all(context)
        all.filter { it.uri in favourites }
    }
}

/** One of the shortcut pages: dated grid, long-press selection and its actions, opening into the viewer. */
@Composable
fun MediaListPage(kind: MediaKind, library: GalleryLibrary, nav: Navigator) {
    val context = LocalContext.current
    val media = remember(kind, library.all, Favourites.all(context)) { library.mediaOf(kind, context) }
    MediaGridPage(kind.title, media, library, nav, if (kind == MediaKind.Favourites) "No favourites yet. Tap the heart on a picture." else "Nothing here")
}

/** A page of pictures (a shortcut or a story): dated grid, long-press selection and its actions, opening into the viewer. */
@Composable
fun MediaGridPage(title: String, media: List<Media>, library: GalleryLibrary, nav: Navigator, emptyText: String, subtitle: String? = null) {
    val grid = rememberLazyGridState()
    val selection = remember { MediaSelection() }
    BackHandler(enabled = selection.active) { selection.clear() }
    ElvanShell(
        gridState = grid, title = title, showNavbar = false, onBack = nav::back,
        isSelectionMode = selection.active, selectedCount = selection.selected.size,
        isAllSelected = selection.selected.size == media.size,
        onSelectAll = { selection.selected = if (selection.selected.size == media.size) emptySet() else media.map { it.uri }.toSet() },
        onCancelSelection = selection::clear,
        hasFloatingBar = selection.active,
        floatingBar = { if (selection.active) PictureSelectionBar(selection, library) }
    ) {
        if (media.isEmpty()) EmptyPage(emptyText)
        else DatedMediaGrid(media, grid, selection, header = subtitle) { list, i -> nav.open(Page.Viewer(list, i)) }
    }
}

@Composable
fun EmptyPage(text: String) {
    val colors = com.elvan.navil.rememberShellColors()
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.material3.Text(text, color = colors.textSecondary)
    }
}

/** What Android keeps in its trash (pictures and videos deleted from here or any app), newest first. Android 11+. */
private fun readTrash(context: Context): List<Media> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
    val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val args = Bundle().apply {
        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (1, 3)")
        putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
    }
    val out = mutableListOf<Media>()
    context.contentResolver.query(
        files,
        arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DURATION),
        args, null
    )?.use { c ->
        while (c.moveToNext()) {
            val video = c.getInt(2) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            out += Media(
                uri = ContentUris.withAppendedId(base, c.getLong(0)).toString(),
                name = c.getString(1) ?: "", isVideo = video, album = "Trash",
                dateAdded = c.getLong(3), duration = if (video) c.getLong(4) else null
            )
        }
    }
    return out
}

/** The Trash: restore or delete for good; Android asks before either (its own dialog). */
@Composable
fun TrashPage(library: GalleryLibrary, nav: Navigator) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var media by remember { mutableStateOf(emptyList<Media>()) }
    LaunchedEffect(version) { media = withContext(Dispatchers.IO) { readTrash(context) } }
    val grid = rememberLazyGridState()
    val selection = remember { MediaSelection() }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) { selection.clear(); version++; library.reload() }
    }
    fun ask(sender: android.app.PendingIntent) = request.launch(IntentSenderRequest.Builder(sender.intentSender).build())
    BackHandler(enabled = selection.active) { selection.clear() }
    ElvanShell(
        gridState = grid, title = "Trash", showNavbar = false, onBack = nav::back,
        isSelectionMode = selection.active, selectedCount = selection.selected.size,
        isAllSelected = media.isNotEmpty() && selection.selected.size == media.size,
        onSelectAll = { selection.selected = if (selection.selected.size == media.size) emptySet() else media.map { it.uri }.toSet() },
        onCancelSelection = selection::clear,
        hasFloatingBar = selection.active,
        floatingBar = {
            val uris = selection.selected.map(Uri::parse)
            if (selection.active && uris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ElvanFloatingActionBar(listOf(
                ElvanFloatingAction("Restore", Sym.Rounded.Restore_from_trash, { ask(MediaStore.createTrashRequest(context.contentResolver, uris, false)) }),
                ElvanFloatingAction("Delete", com.elvan.navil.MaterialSymbols.Rounded.DeleteForever, { ask(MediaStore.createDeleteRequest(context.contentResolver, uris)) }, isDestructive = true)
            ))
        }
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) EmptyPage("Trash needs Android 11 or newer")
        else if (media.isEmpty()) EmptyPage("Trash is empty")
        // A tap in the trash selects (there is nothing to open: the files are hidden until restored)
        else DatedMediaGrid(media, grid, selection) { list, i -> selection.toggle(list[i].uri) }
    }
}

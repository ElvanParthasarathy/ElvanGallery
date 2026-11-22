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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.elvan.navil.frostedGlass
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.elvan.gallery.data.Media
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.elvan.navil.ElvanFloatingAction
import com.elvan.navil.ElvanFloatingActionBar
import com.elvan.navil.ElvanShell
import com.elvan.navil.ElvanMenuState
import com.elvan.navil.ElvanPopupMenu
import com.elvan.navil.ElvanPopupMenuItem
import com.elvan.navil.ElvanTopBarIconButton
import com.elvan.navil.MaterialSymbols
import dev.chrisbanes.haze.hazeSource
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
fun MediaGridPage(title: String, media: List<Media>, library: GalleryLibrary, nav: Navigator, emptyText: String, subtitle: String? = null, plain: Boolean = false) {
    val grid = rememberLazyGridState()
    val selection = remember { MediaSelection() }
    val colors = com.elvan.navil.rememberShellColors()
    var menuOpen by remember { mutableStateOf(false) }
    var oldestFirst by remember { mutableStateOf(false) }
    val visibleMedia = if (oldestFirst) media.asReversed() else media
    BackHandler(enabled = selection.active) { selection.clear() }
    fun menuItems(close: () -> Unit) = listOf(
        ElvanPopupMenuItem("Select", MaterialSymbols.Rounded.CheckCircle, { close(); selection.active = true }),
        ElvanPopupMenuItem(if (oldestFirst) "Newest first" else "Oldest first", MaterialSymbols.Rounded.Sort, { close(); oldestFirst = !oldestFirst })
    )
    // An album opens on its cover; the usual header comes back only while selecting
    if (plain && !selection.active && visibleMedia.isNotEmpty()) {
        AlbumCoverPage(title, subtitle, visibleMedia, grid, selection, nav, ::menuItems)
        return
    }
    ElvanShell(
        gridState = grid, title = title, showNavbar = false, onBack = nav::back,
        isSelectionMode = selection.active, selectedCount = selection.selected.size,
        isAllSelected = selection.selected.size == media.size,
        onSelectAll = { selection.selected = if (selection.selected.size == media.size) emptySet() else media.map { it.uri }.toSet() },
        onCancelSelection = selection::clear,
        hasActions = !selection.active,
        actions = {
            ElvanTopBarIconButton(onClick = { menuOpen = true }) {
                Icon(MaterialSymbols.Rounded.MoreVert, "More options", tint = colors.textPrimary, modifier = androidx.compose.ui.Modifier.size(22.dp))
            }
            fun close() { ElvanMenuState.isMenuOpen = false; menuOpen = false }
            ElvanPopupMenu(
                expanded = menuOpen, onDismissRequest = ::close, colors = colors,
                items = menuItems(::close)
            )
        },
        hasFloatingBar = selection.active,
        floatingBar = { if (selection.active) PictureSelectionBar(selection, library) }
    ) {
        if (visibleMedia.isEmpty()) EmptyPage(emptyText)
        else DatedMediaGrid(visibleMedia, grid, selection, header = subtitle, plain = plain) { list, i -> nav.open(Page.Viewer(list, i)) }
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

/**
 * An album as it first opens: its newest picture fills the top under the status bar with the name and counts over it,
 * the back and menu buttons float in frosted pills, and the square grid runs on beneath.
 */
@Composable
private fun AlbumCoverPage(
    title: String, subtitle: String?, media: List<Media>, grid: androidx.compose.foundation.lazy.grid.LazyGridState,
    selection: MediaSelection, nav: Navigator, menuItems: (() -> Unit) -> List<ElvanPopupMenuItem>
) {
    val colors = com.elvan.navil.rememberShellColors()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    val pill = androidx.compose.foundation.shape.CircleShape
    // The pills blur the cover and photos sliding under them, as the shell's bars do
    val haze = dev.chrisbanes.haze.rememberHazeState()
    androidx.compose.runtime.CompositionLocalProvider(com.elvan.navil.LocalHazeState provides haze) {
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
        // No shell here: the grid starts at the very top, so the cover sits under the status bar
        androidx.compose.runtime.CompositionLocalProvider(com.elvan.navil.LocalElvanTopSpacerHeight provides 1.dp - com.elvan.navil.Dimens.SectionSpacing) {
            DatedMediaGrid(media, grid, selection, plain = true, cover = {
                Box(Modifier.fillMaxWidth().height(286.dp)) {
                    coil3.compose.AsyncImage(
                        model = coil3.request.ImageRequest.Builder(LocalContext.current).data(media.first().let { if (it.isVideo) thumbModel(it.uri) else it.uri }).size(1080).build(),
                        contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f))))
                    Column(Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val shade = androidx.compose.material3.LocalTextStyle.current.copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.35f), blurRadius = 8f))
                        Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = shade)
                        if (subtitle != null) Text(subtitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, style = shade, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }) { list, i -> nav.open(Page.Viewer(list, i)) }
        }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).frostedGlass(pill, colors).clip(pill).clickable(onClick = nav::back), contentAlignment = Alignment.Center) {
                Icon(Sym.Rounded.Arrow_back_ios_new, "Navigate up", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(48.dp).frostedGlass(pill, colors).clip(pill).clickable { menuOpen = true }, contentAlignment = Alignment.Center) {
                Icon(MaterialSymbols.Rounded.MoreVert, "More options", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                fun close() { ElvanMenuState.isMenuOpen = false; menuOpen = false }
                ElvanPopupMenu(expanded = menuOpen, onDismissRequest = ::close, colors = colors, items = menuItems(::close))
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            grid.firstVisibleItemIndex > 40,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.7f),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.7f),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)
        ) {
            Box(Modifier.size(48.dp).frostedGlass(pill, colors).clip(pill).clickable { scope.launch { grid.animateScrollToItem(0) } }, contentAlignment = Alignment.Center) {
                Icon(Sym.Rounded.Vertical_align_top, "Top", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
    }
}

package com.elvan.gallery.ui

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.composables.icons.materialsymbols.roundedfilled.*
import com.elvan.gallery.data.Album
import com.elvan.gallery.data.AlbumEntry
import com.elvan.gallery.data.AlbumGroup
import com.elvan.gallery.data.AlbumOrder
import com.elvan.gallery.data.Media
import com.elvan.navil.ElvanCheckMark
import com.elvan.navil.ElvanFloatingAction
import com.elvan.navil.ElvanFloatingActionBar
import com.elvan.navil.ElvanMenuState
import com.elvan.navil.ElvanPopupMenu
import com.elvan.navil.ElvanPopupMenuItem
import com.elvan.navil.ElvanShell
import com.elvan.navil.ElvanTopBarIconButton
import com.elvan.navil.MaterialSymbols
import com.elvan.navil.ShellColors
import com.elvan.navil.rememberShellColors
import kotlinx.coroutines.launch

/** The dialog an album action opens; one at a time. */
private sealed interface AlbumDialog {
    data class Rename(val entry: AlbumEntry) : AlbumDialog
    data object NewGroup : AlbumDialog
    data object Delete : AlbumDialog
}

private val albumDialog = mutableStateOf<AlbumDialog?>(null)

@Composable
fun AlbumsTab(library: GalleryLibrary, grid: LazyGridState, nav: Navigator) {
    val model = library.albums ?: return
    val drag = remember { AlbumDrag() }
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("gallery_prefs", android.content.Context.MODE_PRIVATE) }
    // 1 is the list (thumbnail, name and count in rows); pinching in goes 2 -> 3 -> 4 -> list
    var columns by remember { androidx.compose.runtime.mutableIntStateOf(prefs.getInt("albums_columns", 3).takeIf { it in albumLevels } ?: 3) }
    val animScope = androidx.compose.runtime.rememberCoroutineScope()
    val morph = remember { GridMorph(albumSlots, animScope) }
    // The shell's spacer and the "All albums" heading come before the first album
    val firstTile = if (model.choosingEssential) 1 else 2
    QuietWhilePinching(morph) {
    ElvanGrid(grid, columns = columns, gap = if (columns == 1) 0.dp else 10.dp, sidePadding = 10.dp,
        modifier = Modifier.pinchMorph(morph, grid, columns, albumLevels, firstTile) { columns = it; prefs.edit().putInt("albums_columns", it).apply() }
            .dragToArrange(grid, model, drag)) {
        if (!model.choosingEssential) item(key = "albums_header", span = { GridItemSpan(maxLineSpan) }) {
            AlbumsHeading(model.onlyEssential) { nav.open(Page.AllAlbums) }
        }
        albumTiles(model, model.visible, nav, grid, drag, asList = columns == 1, morph = morph, columns = columns)
    }
    }
    AlbumDialogs(model, library)
}

@Composable
private fun AlbumsHeading(essential: Boolean, onViewAll: () -> Unit) {
    val colors = rememberShellColors()
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (essential) "Essential albums" else "All albums", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
        if (essential) Text("View all", color = colors.link, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onViewAll).padding(8.dp))
    }
}

// Samsung: 2 across, 3 across, then the list
private val albumLevels = listOf(2, 3, 1)

/** Album tiles: 10 dp in from the sides and apart, 5:6 covers; the list is 76 dp rows with a 64 dp cover 6 dp down. */
private val albumSlots = GridSlots { index, columns, width, target ->
    val side = 10.dp.toPx()
    if (columns == 1) {
        val row = 76.dp.toPx()
        Rect(side, index * row + 6.dp.toPx(), side + 64.dp.toPx(), index * row + 70.dp.toPx())
    } else {
        val gap = 10.dp.toPx()
        val w = (width - 2 * side - (columns - 1) * gap) / columns
        val h = w * 1.2f
        val x = side + (index % columns) * (w + gap)
        val y = (index / columns) * (h + gap)
        Rect(x, y, x + w, y + h)
    }
}

fun LazyGridScope.albumTiles(model: AlbumsModel, entries: List<AlbumEntry>, nav: Navigator, grid: LazyGridState? = null, drag: AlbumDrag? = null, asList: Boolean = false, morph: GridMorph? = null, columns: Int = 3) {
    itemsIndexed(entries, key = { _, it -> it.key }) { index, entry ->
        val carried = drag?.key == entry.key
        AlbumTile(
            model, entry, nav,
            if (carried && grid != null) Modifier.zIndex(2f).graphicsLayer {
                val info = grid.layoutInfo
                info.visibleItemsInfo.firstOrNull { it.key == entry.key }?.let {
                    translationX = drag!!.finger.x - drag.grab.x - it.offset.x
                    translationY = drag.finger.y - drag.grab.y - (it.offset.y - info.viewportStartOffset)
                }
                scaleX = 1.06f; scaleY = 1.06f
            // Tiles glide to new places only while albums are being rearranged; otherwise they move with the page and header
            } else (if (drag?.key != null && morph?.settling != true) Modifier.animateItem() else Modifier)
                // In the list only the small cover moves (see AlbumTile); a whole row is never stretched into a tile
                .then(if (morph != null && !asList) Modifier.morphTile(morph, index, columns) else Modifier),
            asList, morph, index, columns
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumTile(model: AlbumsModel, entry: AlbumEntry, nav: Navigator, modifier: Modifier = Modifier, asList: Boolean = false, morph: GridMorph? = null, index: Int = 0, columns: Int = 3) {
    val colors = rememberShellColors()
    val on = entry.key in model.selected
    val dimmed = model.pickingGroup && entry is Album
    model.titles
    val onClick = {
        when {
            model.pickingGroup && entry is AlbumGroup -> model.moveSelectedInto(entry)
            model.selecting -> model.toggle(entry.key)
            entry is Album -> nav.open(Page.Album(entry.name))
            entry is AlbumGroup -> nav.open(Page.Group(entry.name))
        }
    }
    val onLongClick = if (model.canRearrange) null else ({ model.toggle(entry.key) })
    // During a pinch the cover is cropped from the photo to the card's shape at that moment (see GridMorph.cropRegion)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val crop: ((androidx.compose.ui.geometry.Size) -> androidx.compose.ui.geometry.Rect?)? =
        remember(morph, index, columns, density) { morph?.let { m -> { s -> m.cropRegion(index, columns, density, s) } } }
    if (asList) {
        // List level: a rounded thumbnail with the name and count beside it
        Row(
            // The row's rounded clip (for the press ripple) is dropped while a pinch grows its cover past the row
            modifier.alpha(if (dimmed) 0.4f else 1f).fillMaxWidth().then(if (morph?.target == null) Modifier.clip(RoundedCornerShape(12.dp)) else Modifier)
                .combinedClickable(enabled = !dimmed, onClick = onClick, onLongClick = onLongClick).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box((if (morph != null) Modifier.zIndex(1f).morphTile(morph, index, columns) else Modifier).size(64.dp).clip(CoverShape).background(colors.surface)) {
                when (entry) {
                    is Album -> Thumb(entry.media.firstOrNull()?.uri, Modifier.fillMaxSize(), crop)
                    is AlbumGroup -> GroupCover(entry, colors)
                }
                if (model.selecting && !model.pickingGroup) ElvanCheckMark(on, colors.link, Color.White, 20.dp, Modifier.align(Alignment.TopStart).padding(4.dp))
            }
            Column(Modifier.padding(start = 14.dp).weight(1f).graphicsLayer { alpha = morph?.labelAlpha() ?: 1f }) {
                Text(model.title(entry), fontSize = 16.sp, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (entry is AlbumGroup) "${entry.albums.size} albums" else "${entry.count}", fontSize = 13.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        return
    }
    // A 5:6 rounded cover with the name and count laid over its foot, on a soft shade
    Box(
        modifier.alpha(if (dimmed) 0.4f else 1f).fillMaxWidth().aspectRatio(5f / 6f).clip(CoverShape).background(colors.surface).combinedClickable(
            enabled = !dimmed,
            onClick = {
                when {
                    model.pickingGroup && entry is AlbumGroup -> model.moveSelectedInto(entry)
                    model.selecting -> model.toggle(entry.key)
                    entry is Album -> nav.open(Page.Album(entry.name))
                    entry is AlbumGroup -> nav.open(Page.Group(entry.name))
                }
            },
            // With Custom order the grid itself handles a hold (select, or carry the album)
            onLongClick = if (model.canRearrange) null else ({ model.toggle(entry.key) })
        )
    ) {
        when (entry) {
            is Album -> Thumb(entry.media.firstOrNull()?.uri, Modifier.fillMaxSize(), crop)
            is AlbumGroup -> GroupCover(entry, colors)
        }
        // The name and its shade fade during a pinch rather than shrink with the cover
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.45f).align(Alignment.BottomCenter).graphicsLayer { alpha = morph?.labelAlpha() ?: 1f }
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
        // The names keep their real text size while a pinch scales the cover (Samsung's never grow or shrink)
        Column(Modifier.align(Alignment.BottomCenter).graphicsLayer {
            // During a pinch the names keep their own texture, so redrawing the picture each frame does not redraw them
            compositingStrategy = if (morph?.target != null) androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                else androidx.compose.ui.graphics.CompositingStrategy.Auto
            alpha = morph?.labelAlpha() ?: 1f
            val u = morph?.tileScale(index, columns, this) ?: 1f
            scaleX = 1f / u; scaleY = 1f / u
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        }.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.title(entry), fontSize = 16.sp, color = Color.White,
                    style = LocalTextStyle.current.copy(shadow = Shadow(Color.Black.copy(alpha = 0.4f), blurRadius = 6f)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                val essential = when (entry) { is Album -> entry.name in model.essential; is AlbumGroup -> entry.albums.any { it.name in model.essential } }
                if (essential) Icon(Sym.RoundedFilled.Star, null, tint = Color(0xFFF4B94F), modifier = Modifier.padding(start = 3.dp).size(14.dp))
            }
            Text(if (entry is AlbumGroup) "${entry.albums.size} albums" else "${entry.count}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 2.dp))
        }
        if (model.selecting && !model.pickingGroup) {
            if (on) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            ElvanCheckMark(on, colors.link, Color.White, 22.dp, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
    }
}

@Composable
private fun GroupCover(group: AlbumGroup, colors: ShellColors) {
    val covers = group.albums.mapNotNull { it.media.firstOrNull()?.uri }.take(4)
    Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0..1) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (col in 0..1) Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(10.dp)).background(colors.iconBg)) {
                covers.getOrNull(row * 2 + col)?.let { Thumb(it, Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
fun Thumb(uri: String?, modifier: Modifier, region: ((androidx.compose.ui.geometry.Size) -> androidx.compose.ui.geometry.Rect?)? = null) {
    // The grid's own drawing: the same copy in memory as the Pictures tab shows at once, a missing one fades in
    Box(if (uri != null) modifier.thumbnail(uri, 360, region) else modifier)
}

/** The album being carried to a new place, where the finger is, and where on the tile it was taken. */
class AlbumDrag {
    var key by mutableStateOf<String?>(null)
    var finger by mutableStateOf(Offset.Zero)
    var grab = Offset.Zero
}

/**
 * Arranging albums (Custom order): hold an album to select it; slide without lifting to carry it to a new place while the
 * others make room. Watched before the tiles see the touch, so a hold never also opens the album.
 */
private fun Modifier.dragToArrange(grid: LazyGridState, model: AlbumsModel, drag: AlbumDrag): Modifier = pointerInput(grid, model) {
    val edge = 96.dp.toPx()
    kotlinx.coroutines.coroutineScope {
        val scope = this
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!model.canRearrange) return@awaitEachGesture
            val moved = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                    if (!c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                }
                @Suppress("UNREACHABLE_CODE") false
            }
            if (moved == true) return@awaitEachGesture
            fun tileAt(p: Offset) = grid.layoutInfo.let { info ->
                info.visibleItemsInfo.firstOrNull {
                    val top = it.offset.y - info.viewportStartOffset
                    p.x >= it.offset.x && p.x < it.offset.x + it.size.width && p.y >= top && p.y < top + it.size.height
                }
            }
            fun isAlbum(k: Any?) = (k as? String)?.let { it.startsWith("ALBUM::") || it.startsWith("GROUP::") } == true
            val held = tileAt(down.position)?.takeIf { isAlbum(it.key) } ?: return@awaitEachGesture
            val key = held.key as String
            model.toggle(key)
            var carrying = false
            var finger = down.position
            val scroller = scope.launch {
                while (true) {
                    androidx.compose.runtime.withFrameNanos { }
                    if (!carrying) continue
                    val speed = when {
                        finger.y < edge -> -(edge - finger.y) / edge * 24f
                        finger.y > size.height - edge -> (finger.y - (size.height - edge)) / edge * 24f
                        else -> 0f
                    }
                    if (speed != 0f) grid.scrollBy(speed)
                }
            }
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val c = event.changes.firstOrNull { it.id == down.id } ?: break
                    event.changes.forEach { it.consume() }
                    if (!c.pressed) break
                    finger = c.position
                    if (!carrying && (finger - down.position).getDistance() > viewConfiguration.touchSlop * 2) {
                        carrying = true
                        model.stopSelecting()
                        drag.grab = down.position - Offset(held.offset.x.toFloat(), (held.offset.y - grid.layoutInfo.viewportStartOffset).toFloat())
                        drag.key = key
                    }
                    if (carrying) {
                        drag.finger = finger
                        tileAt(finger)?.key?.takeIf { it != key && isAlbum(it) }?.let { model.drag(key, it as String) }
                    }
                }
            } finally {
                scroller.cancel()
                if (carrying) { drag.key = null; model.endDrag() }
            }
        }
    }
}

/** The floating actions while albums are selected. */
@Composable
fun AlbumSelectionBar(model: AlbumsModel) {
    val count = model.selectedCount
    val actions = when {
        model.choosingEssential -> listOf(ElvanFloatingAction("Done", MaterialSymbols.Rounded.Check, { model.saveEssential() }))
        model.pickingGroup || count == 0 -> emptyList()
        else -> buildList {
            val sole = model.soleGroup
            add(ElvanFloatingAction(if (sole != null) "Ungroup" else "Group", if (sole != null) Sym.Rounded.Ungroup else Sym.Rounded.Workspaces, {
                if (sole != null) model.ungroup(sole) else albumDialog.value = AlbumDialog.NewGroup
            }))
            add(ElvanFloatingAction("Move", Sym.Rounded.Drive_file_move, { model.startPickingGroup() }))
            add(ElvanFloatingAction("Share", MaterialSymbols.Rounded.Share, { model.shareSelected() }))
            if (count == 1) add(ElvanFloatingAction("Rename", MaterialSymbols.Rounded.Edit, {
                model.visible.firstOrNull { it.key in model.selected }?.let { albumDialog.value = AlbumDialog.Rename(it) }
            }))
            if (model.onlyEssential) add(ElvanFloatingAction("Remove", Sym.Rounded.Star, { model.dropFromEssential() }))
            if (model.albumsToDelete > 0) add(ElvanFloatingAction("Delete", MaterialSymbols.Rounded.Delete, { albumDialog.value = AlbumDialog.Delete }, isDestructive = true))
        }
    }
    ElvanFloatingActionBar(actions)
}

@Composable
fun AlbumDialogs(model: AlbumsModel, library: GalleryLibrary) {
    val context = LocalContext.current
    val trash = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) { model.stopSelecting(); library.reload() }
    }
    val dialog = albumDialog.value ?: return
    val close = { albumDialog.value = null }
    when (dialog) {
        is AlbumDialog.Rename -> NameDialog(
            if (dialog.entry is AlbumGroup) "Rename group" else "Rename album", model.title(dialog.entry), "Rename",
            { if (dialog.entry is AlbumGroup && model.groupNameTaken(it, dialog.entry.name)) "A group has this name already" else null }, close
        ) { model.rename(dialog.entry, it); close() }
        AlbumDialog.NewGroup -> NameDialog("New group", model.suggestedGroupName(), "Create",
            { if (model.groupNameTaken(it, null)) "A group has this name already" else null }, close) { model.groupSelected(it); close() }
        AlbumDialog.Delete -> ElvanDialog(
            title = "Delete ${model.albumsToDelete} album(s)?",
            text = "Their ${model.filesToDelete.size} pictures and videos move to the phone's trash.",
            confirm = "Delete", destructive = true, onDismiss = close
        ) {
            close()
            val uris = model.filesToDelete.map { it.toUri() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) trash.launch(IntentSenderRequest.Builder(MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender).build())
            else {
                val gone = uris.count { u -> try { context.contentResolver.delete(u, null, null) > 0 } catch (_: Exception) { false } }
                Toast.makeText(context, "$gone deleted", Toast.LENGTH_SHORT).show()
                model.stopSelecting(); library.reload()
            }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, error: (String) -> String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val colors = rememberShellColors()
    var text by remember { mutableStateOf(initial) }
    val problem = error(text.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.floatingBg,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = colors.textPrimary) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, singleLine = true, isError = problem != null, shape = RoundedCornerShape(16.dp))
                if (problem != null) Text(problem, color = colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank() && problem == null) { Text(confirm, color = colors.link) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) } }
    )
}

@Composable
fun ElvanDialog(title: String, text: String, confirm: String, destructive: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = rememberShellColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.floatingBg,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = colors.textPrimary) },
        text = { Text(text, color = colors.textSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = if (destructive) colors.danger else colors.link) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) } }
    )
}

@Composable
fun SortDialog(current: AlbumOrder, onDismiss: () -> Unit, onPick: (AlbumOrder) -> Unit) {
    val colors = rememberShellColors()
    var choice by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = colors.floatingBg, shape = RoundedCornerShape(28.dp),
        title = { Text("Sort albums", color = colors.textPrimary) },
        text = {
            Column {
                AlbumOrder.entries.forEach { o ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { choice = o }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(choice == o, { choice = o }, colors = RadioButtonDefaults.colors(selectedColor = colors.link))
                        Text(o.label, color = colors.textPrimary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(choice); onDismiss() }) { Text("OK", color = colors.link) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) } }
    )
}

/** One album's pictures, newest first. */
@Composable
fun AlbumPage(name: String, library: GalleryLibrary, nav: Navigator) {
    val media = remember(name, library.all) { library.album(name) }
    val title = library.albums?.let { m -> m.titles; library.settings.names[name] } ?: name
    MediaGridPage(title, media, library, nav, "Nothing here", subtitle = mediaCountText(media), plain = true)
}

/** The albums of one group. */
@Composable
fun GroupPage(name: String, library: GalleryLibrary, nav: Navigator) {
    val model = library.albums ?: return
    val albums = model.entries.filterIsInstance<AlbumGroup>().firstOrNull { it.name == name }?.albums.orEmpty()
    val grid = rememberLazyGridState()
    ElvanShell(gridState = grid, title = name, showNavbar = false, onBack = nav::back) {
        ElvanGrid(grid, columns = 3, gap = 12.dp, sidePadding = 14.dp) {
            item(key = "count", span = { GridItemSpan(maxLineSpan) }) { CountLine("${albums.size} album${if (albums.size != 1) "s" else ""}") }
            albumTiles(model, albums, nav)
        }
    }
}

/** Every album, whatever the essential-albums setting says, with its own selection and sort. */
@Composable
fun AllAlbumsPage(library: GalleryLibrary, nav: Navigator) {
    val context = LocalContext.current
    val colors = rememberShellColors()
    val model = remember(library.all) { AlbumsModel(context, library.all, library.settings, onlyEssential = false) }
    val grid = rememberLazyGridState()
    var menuOpen by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    BackHandler(enabled = model.selecting) { model.stopSelecting() }
    ElvanShell(
        gridState = grid, title = "All albums", showNavbar = false, onBack = nav::back,
        hasActions = !model.selecting, hasFloatingBar = model.selecting,
        isSelectionMode = model.selecting, selectedCount = model.selectedCount, isAllSelected = model.allSelected,
        onSelectAll = model::toggleAll, onCancelSelection = model::stopSelecting,
        actions = {
            ElvanTopBarIconButton(onClick = { menuOpen = true }) {
                Icon(MaterialSymbols.Rounded.MoreVert, "More", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
            }
            fun close() { ElvanMenuState.isMenuOpen = false; menuOpen = false }
            ElvanPopupMenu(
                expanded = menuOpen, onDismissRequest = ::close, colors = colors,
                items = listOf(
                    ElvanPopupMenuItem("Select", MaterialSymbols.Rounded.CheckCircle, { close(); model.startSelecting() }),
                    ElvanPopupMenuItem("Sort", MaterialSymbols.Rounded.Sort, { close(); sorting = true })
                )
            )
        },
        floatingBar = { if (model.selecting) AlbumSelectionBar(model) }
    ) {
        val count = model.entries.size
        ElvanGrid(grid, columns = 3, gap = 12.dp, sidePadding = 14.dp, modifier = Modifier.dragToArrange(grid, model, remember { AlbumDrag() })) {
            item(key = "count", span = { GridItemSpan(maxLineSpan) }) { CountLine("$count album${if (count != 1) "s" else ""}") }
            albumTiles(model, model.visible, nav)
        }
    }
    AlbumDialogs(model, library)
    if (sorting) SortDialog(model.order, { sorting = false }) { model.changeOrder(it); library.albums?.changeOrder(it) }
}

@Composable
fun CountLine(text: String) {
    val colors = rememberShellColors()
    Text(text, fontSize = 13.sp, color = colors.textSecondary, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
}

fun mediaCountText(media: List<Media>): String {
    val videos = media.count { it.isVideo }
    val images = media.size - videos
    return listOfNotNull(
        if (images > 0) "$images picture${if (images > 1) "s" else ""}" else null,
        if (videos > 0) "$videos video${if (videos > 1) "s" else ""}" else null
    ).joinToString(" · ")
}

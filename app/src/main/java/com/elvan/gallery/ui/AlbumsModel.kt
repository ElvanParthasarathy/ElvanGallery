package com.elvan.gallery.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.elvan.gallery.data.Album
import com.elvan.gallery.data.AlbumEntry
import com.elvan.gallery.data.AlbumGroup
import com.elvan.gallery.data.AlbumOrder
import com.elvan.gallery.data.AlbumSettings
import com.elvan.gallery.data.AlbumShelf
import com.elvan.gallery.data.Media
import com.elvan.gallery.data.RECENT
import com.elvan.gallery.data.VIDEOS

/**
 * The Albums page: what is shown, what is selected, and every change the user can make (group, ungroup, move into a
 * group, rename, essential albums, share, delete, drag into a new order).
 */
class AlbumsModel(private val context: Context, private var media: List<Media>, val settings: AlbumSettings, onlyEssential: Boolean) {
    var order by mutableStateOf(settings.sort); private set
    var entries by mutableStateOf(emptyList<AlbumEntry>()); private set
    var visible by mutableStateOf(emptyList<AlbumEntry>()); private set
    var essential by mutableStateOf(settings.essential); private set
    var selected by mutableStateOf(emptySet<String>()); private set
    var selecting by mutableStateOf(false); private set
    var choosingEssential by mutableStateOf(false); private set
    var pickingGroup by mutableStateOf(false); private set
    var onlyEssential by mutableStateOf(onlyEssential); private set
    /** Changes whenever a title changes, so tiles read the new one. */
    var titles by mutableIntStateOf(0); private set
    private var draggedOrder: List<String>? = null

    init { rebuild() }

    fun title(entry: AlbumEntry): String = settings.titleOf(entry)
    fun media(entry: AlbumEntry): List<Media> = when (entry) {
        is Album -> entry.media
        is AlbumGroup -> entry.albums.flatMap { it.media }
    }

    val selectedCount get() = visible.count { it.key in selected }
    val allSelected get() = visible.isNotEmpty() && selectedCount == visible.size
    val canRearrange get() = order == AlbumOrder.Custom && !selecting
    private val selectedAlbums get() = entries.filterIsInstance<Album>().filter { it.key in selected && it.name != RECENT && it.name != VIDEOS }
    private val selectedGroups get() = entries.filterIsInstance<AlbumGroup>().filter { it.key in selected }
    /** The one group selected on its own: the group button takes it apart. */
    val soleGroup get() = selectedGroups.singleOrNull()?.takeIf { selected.size == 1 }

    private fun rebuild() {
        entries = AlbumShelf.build(media, settings, order)
        val dragged = draggedOrder
        val shown = if (!onlyEssential || choosingEssential) entries else entries.filter { e ->
            when (e) { is Album -> e.name in essential; is AlbumGroup -> e.albums.any { it.name in essential } }
        }
        visible = if (dragged == null) shown else shown.sortedBy { dragged.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }

    fun refresh(newMedia: List<Media>) { media = newMedia; rebuild() }
    fun changeOrder(next: AlbumOrder) { order = next; settings.sort = next; rebuild() }
    fun showOnlyEssential(on: Boolean) { onlyEssential = on; rebuild() }

    // ── Selection ──
    fun startSelecting() { if (visible.isNotEmpty()) selecting = true }
    fun stopSelecting() { selecting = false; choosingEssential = false; pickingGroup = false; selected = emptySet(); rebuild() }
    fun toggle(key: String) { if (pickingGroup) return; selecting = true; selected = if (key in selected) selected - key else selected + key }
    fun toggleAll() { selecting = true; selected = if (allSelected) emptySet() else visible.map { it.key }.toSet() }

    // ── Essential albums ──
    fun chooseEssential() {
        choosingEssential = true; selecting = true; rebuild()
        selected = entries.filter { e -> when (e) { is Album -> e.name in essential; is AlbumGroup -> e.albums.all { it.name in essential } } }.map { it.key }.toSet()
    }
    fun saveEssential() {
        val chosen = entries.filter { it.key in selected }.flatMap { e -> when (e) { is Album -> listOf(e.name); is AlbumGroup -> e.albums.map { it.name } } }.toSet()
        essential = chosen; settings.essential = chosen; stopSelecting()
    }
    fun dropFromEssential() {
        val gone = entries.filter { it.key in selected }.flatMap { e -> when (e) { is Album -> listOf(e.name); is AlbumGroup -> e.albums.map { it.name } } }.toSet()
        essential = essential - gone; settings.essential = essential; stopSelecting()
    }

    // ── Groups ──
    fun suggestedGroupName(): String = generateSequence(1) { it + 1 }.map { "Group $it" }.first { n -> entries.none { it is AlbumGroup && it.name.equals(n, true) } }
    fun groupNameTaken(name: String, except: String?) = name.isNotBlank() && !name.equals(except, true) && entries.any { it is AlbumGroup && it.name.equals(name, true) }
    fun groupSelected(name: String) { settings.groups = settings.groups + selectedAlbums.associate { it.name to name }; stopSelecting() }
    fun ungroup(group: AlbumGroup) { settings.groups = settings.groups.filterValues { it != group.name }; stopSelecting() }
    fun startPickingGroup() { pickingGroup = true; Toast.makeText(context, "Choose a group to move the albums into", Toast.LENGTH_SHORT).show() }
    fun moveSelectedInto(group: AlbumGroup) { groupSelected(group.name) }

    // ── Names ──
    fun rename(entry: AlbumEntry, name: String) {
        when (entry) {
            is Album -> settings.names = settings.names + (entry.name to name)
            is AlbumGroup -> settings.groups = settings.groups.mapValues { if (it.value == entry.name) name else it.value }
        }
        titles++; stopSelecting()
    }

    // ── Share and delete ──
    fun shareSelected() {
        val uris = entries.filter { it.key in selected }.flatMap { media(it) }.map { it.uri.toUri() }.distinct()
        if (uris.isEmpty()) { Toast.makeText(context, "Nothing to share", Toast.LENGTH_SHORT).show(); return }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        stopSelecting()
    }
    /** The pictures and videos the selected albums hold (Recent and Videos are views of everything, so they never count). */
    val filesToDelete get() = selectedAlbums.flatMap { it.media }.map { it.uri }
    val albumsToDelete get() = selectedAlbums.size

    // ── Dragging into a new order (Custom order) ──
    fun drag(key: String, over: String) {
        val keys = (draggedOrder ?: visible.map { it.key }).toMutableList()
        val from = keys.indexOf(key); val to = keys.indexOf(over)
        if (from < 0 || to < 0 || from == to) return
        keys.add(to, keys.removeAt(from))
        draggedOrder = keys; rebuild()
    }
    fun endDrag() {
        draggedOrder?.let { settings.order = it + settings.order.filter { k -> k !in it } }
        draggedOrder = null; rebuild()
    }
}

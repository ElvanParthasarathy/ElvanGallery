package com.elvan.gallery.data

import android.content.Context
import org.json.JSONObject

/** An entry on the Albums page: a folder of pictures, or a group the user made of several folders. */
sealed interface AlbumEntry {
    val name: String
    val key: String
    val count: Int
}

data class Album(override val name: String, val media: List<Media>) : AlbumEntry {
    override val key get() = "ALBUM::$name"
    override val count get() = media.size
}

data class AlbumGroup(override val name: String, val albums: List<Album>) : AlbumEntry {
    override val key get() = "GROUP::$name"
    override val count get() = albums.sumOf { it.media.size }
}

enum class AlbumOrder(val label: String) {
    Custom("Custom order"), NameAZ("Name (A-Z)"), NameZA("Name (Z-A)"), MostItems("Most items"), FewestItems("Fewest items")
}

/** The two albums every phone gets: everything, and every video. */
const val RECENT = "Recent"
const val VIDEOS = "Videos"

/** Builds the Albums page from the pictures, the user's groups and the chosen order. */
object AlbumShelf {
    fun build(media: List<Media>, settings: AlbumSettings, order: AlbumOrder): List<AlbumEntry> {
        val folders = media.groupBy { it.album }.map { (name, items) -> Album(name, items) }
        val grouped = folders.filter { settings.groupOf(it.name) != null }.groupBy { settings.groupOf(it.name)!! }
            .map { (group, albums) -> AlbumGroup(group, albums.sortedBy { it.name.lowercase() }) }
        val loose = folders.filter { settings.groupOf(it.name) == null }
        val pinned = listOf(Album(RECENT, media), Album(VIDEOS, media.filter { it.isVideo })).filter { it.count > 0 }
        val rest: List<AlbumEntry> = loose + grouped
        val sorted = when (order) {
            AlbumOrder.NameAZ -> rest.sortedBy { it.name.lowercase() }
            AlbumOrder.NameZA -> rest.sortedByDescending { it.name.lowercase() }
            AlbumOrder.MostItems -> rest.sortedByDescending { it.count }
            AlbumOrder.FewestItems -> rest.sortedBy { it.count }
            // Custom: the order the user dragged them into; new albums after, newest pictures first
            AlbumOrder.Custom -> {
                val rank = settings.order.withIndex().associate { (i, k) -> k to i }
                rest.sortedWith(compareBy<AlbumEntry> { rank[it.key] ?: Int.MAX_VALUE }.thenByDescending { newest(it) })
            }
        }
        return pinned + sorted
    }

    private fun newest(entry: AlbumEntry): Long = when (entry) {
        is Album -> entry.media.firstOrNull()?.dateAdded ?: 0L
        is AlbumGroup -> entry.albums.maxOfOrNull { it.media.firstOrNull()?.dateAdded ?: 0L } ?: 0L
    }
}

/** What the user chose about albums: groups, essential albums, own names, dragged order, sort. */
class AlbumSettings(context: Context) {
    private val prefs = context.getSharedPreferences("elvan_albums", Context.MODE_PRIVATE)

    private fun readMap(key: String): Map<String, String> = prefs.getString(key, null)?.let { s ->
        val o = JSONObject(s); o.keys().asSequence().associateWith { o.getString(it) }
    } ?: emptyMap()
    private fun writeMap(key: String, map: Map<String, String>) = prefs.edit().putString(key, JSONObject(map).toString()).apply()

    var groups: Map<String, String>
        get() = readMap("groups")
        set(value) = writeMap("groups", value)
    var names: Map<String, String>
        get() = readMap("names")
        set(value) = writeMap("names", value)
    var essential: Set<String>
        get() = prefs.getStringSet("essential", emptySet())!!.toSet()
        set(value) = prefs.edit().putStringSet("essential", value).apply()
    var order: List<String>
        get() = prefs.getString("order", "")!!.split('\u0001').filter { it.isNotEmpty() }
        set(value) = prefs.edit().putString("order", value.joinToString("\u0001")).apply()
    var sort: AlbumOrder
        get() = AlbumOrder.entries.getOrElse(prefs.getInt("sort", 0)) { AlbumOrder.Custom }
        set(value) = prefs.edit().putInt("sort", value.ordinal).apply()

    fun groupOf(album: String) = groups[album]
    fun titleOf(entry: AlbumEntry) = if (entry is Album) names[entry.name] ?: entry.name else entry.name
}

/** Saves the user's album groups to a small JSON file and reads them back. */
object GroupBackup {
    fun export(settings: AlbumSettings): String? {
        val byGroup = settings.groups.entries.groupBy({ it.value }, { it.key })
        if (byGroup.isEmpty()) return null
        return JSONObject().put("elvanGalleryGroups", 1).put("groups", JSONObject(byGroup.mapValues { org.json.JSONArray(it.value) })).toString(2)
    }

    /** The number of groups read, or null when the file is not a group backup. */
    fun import(settings: AlbumSettings, json: String): Int? = try {
        val root = JSONObject(json)
        if (!root.has("groups")) null else {
            val groups = root.getJSONObject("groups")
            val next = settings.groups.toMutableMap()
            groups.keys().forEach { g -> val albums = groups.getJSONArray(g); for (i in 0 until albums.length()) next[albums.getString(i)] = g }
            settings.groups = next
            groups.length()
        }
    } catch (e: Exception) { null }

    fun fileName() = "elvan-gallery-groups-${java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())}.json"
}

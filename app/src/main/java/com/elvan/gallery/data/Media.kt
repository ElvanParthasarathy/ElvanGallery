package com.elvan.gallery.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/** One picture or video on the phone. [dateAdded] is in seconds, [duration] in milliseconds (videos). */
data class Media(
    val uri: String,
    val name: String,
    val isVideo: Boolean,
    val album: String,
    val dateAdded: Long,
    val duration: Long?,
    val width: Int = 0,
    val height: Int = 0
)

/** Reads every picture and video the phone knows about, newest first, in one MediaStore query. */
object MediaQuery {
    private const val DURATION = "duration" // MediaColumns.DURATION is API 29+; the column itself is older
    private const val TAKEN = "datetaken" // milliseconds; Samsung Gallery dates and orders by this

    /** [limit]: only the newest that many, so the first screen can show while the rest are read. */
    fun all(context: Context, limit: Int? = null): List<Media> {
        val files = MediaStore.Files.getContentUri("external")
        val columns = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            DURATION,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            TAKEN
        )
        val kinds = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
        val out = ArrayList<Media>()
        val sort = "COALESCE($TAKEN, ${MediaStore.Files.FileColumns.DATE_ADDED} * 1000) DESC"
        val cursor = if (limit != null && android.os.Build.VERSION.SDK_INT >= 30) context.contentResolver.query(files, columns, android.os.Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, kinds)
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
        }, null) else context.contentResolver.query(files, columns, kinds, null, sort)
        cursor?.use { c ->
            val id = c.getColumnIndexOrThrow(columns[0])
            val name = c.getColumnIndexOrThrow(columns[1])
            val type = c.getColumnIndexOrThrow(columns[2])
            val bucket = c.getColumnIndexOrThrow(columns[3])
            val added = c.getColumnIndexOrThrow(columns[4])
            val length = c.getColumnIndex(DURATION)
            val width = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val height = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val taken = c.getColumnIndex(TAKEN)
            while (c.moveToNext()) {
                val video = c.getInt(type) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                out += Media(
                    uri = ContentUris.withAppendedId(base, c.getLong(id)).toString(),
                    name = c.getString(name) ?: "",
                    isVideo = video,
                    album = c.getString(bucket) ?: "Other",
                    dateAdded = if (taken >= 0 && c.getLong(taken) > 0) c.getLong(taken) / 1000 else c.getLong(added),
                    duration = if (video && length >= 0) c.getLong(length) else null,
                    width = if (width >= 0) c.getInt(width) else 0,
                    height = if (height >= 0) c.getInt(height) else 0
                )
            }
        }
        return out
    }
}

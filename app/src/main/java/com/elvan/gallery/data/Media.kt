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
    val duration: Long?
)

/** Reads every picture and video the phone knows about, newest first, in one MediaStore query. */
object MediaQuery {
    private const val DURATION = "duration" // MediaColumns.DURATION is API 29+; the column itself is older

    fun all(context: Context): List<Media> {
        val files = MediaStore.Files.getContentUri("external")
        val columns = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            DURATION
        )
        val kinds = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
        val out = ArrayList<Media>()
        context.contentResolver.query(files, columns, kinds, null, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { c ->
            val id = c.getColumnIndexOrThrow(columns[0])
            val name = c.getColumnIndexOrThrow(columns[1])
            val type = c.getColumnIndexOrThrow(columns[2])
            val bucket = c.getColumnIndexOrThrow(columns[3])
            val added = c.getColumnIndexOrThrow(columns[4])
            val length = c.getColumnIndex(DURATION)
            while (c.moveToNext()) {
                val video = c.getInt(type) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                out += Media(
                    uri = ContentUris.withAppendedId(base, c.getLong(id)).toString(),
                    name = c.getString(name) ?: "",
                    isVideo = video,
                    album = c.getString(bucket) ?: "Other",
                    dateAdded = c.getLong(added),
                    duration = if (video && length >= 0) c.getLong(length) else null
                )
            }
        }
        return out
    }
}

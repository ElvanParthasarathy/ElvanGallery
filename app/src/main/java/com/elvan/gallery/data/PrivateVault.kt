package com.elvan.gallery.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * The private safe's storage: pictures are copied byte for byte into a folder only this app can read, and listed
 * from there (no separate database to fall out of step with the files).
 */
class PrivateVault(private val context: Context) {
    private val dir = File(context.filesDir, "vault").apply { mkdirs() }

    fun list(): List<File> = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** Copies the picture in; returns the stored file, or null when it could not be read. */
    fun add(source: Uri): File? = try {
        val ext = context.contentResolver.getType(source)?.substringAfter('/')?.takeIf { it.length in 2..5 } ?: "jpg"
        val file = File(dir, "safe_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(source)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        file
    } catch (e: Exception) { null }

    fun delete(file: File) = file.delete()

    fun share(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/*"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Puts a copy back into the gallery, in Pictures/Restored. */
    fun restore(file: File): Boolean = try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/${file.extension.ifEmpty { "jpeg" }}")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Restored")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        true
    } catch (e: Exception) { false }
}

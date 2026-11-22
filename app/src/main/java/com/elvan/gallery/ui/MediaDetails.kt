package com.elvan.gallery.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.elvan.gallery.data.Media
import com.elvan.navil.rememberShellColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Favourite pictures, kept by the app; a snapshot so hearts and the Favourites page follow at once. */
object Favourites {
    private var cache by mutableStateOf<Set<String>?>(null)
    private fun prefs(context: Context) = context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE)
    fun all(context: Context): Set<String> = cache ?: (prefs(context).getStringSet("favourites", emptySet()) ?: emptySet()).also { cache = it }
    fun toggle(context: Context, uri: String) = set(context, if (uri in all(context)) all(context) - uri else all(context) + uri)
    fun add(context: Context, uris: Collection<String>) = set(context, all(context) + uris)
    private fun set(context: Context, value: Set<String>) { cache = value; prefs(context).edit().putStringSet("favourites", value).apply() }
}

private data class Details(
    val date: String, val name: String, val path: String, val device: String?, val sizeLine: String, val cameraLine: String?
)

/** What a swipe up shows: when, which file and where, which camera, size and exposure. */
@Composable
fun MediaDetails(media: Media) {
    val context = LocalContext.current
    val colors = rememberShellColors()
    val details by produceState<Details?>(null, media.uri) { value = withContext(Dispatchers.IO) { readDetails(context, media) } }
    val d = details ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(d.date, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        Text(d.name, fontSize = 14.sp, color = colors.textSecondary)
        Text(d.path, fontSize = 13.sp, color = colors.textTertiary)
        if (d.device != null) Text(d.device, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, modifier = Modifier.padding(top = 14.dp))
        Row { Text(d.sizeLine, fontSize = 13.sp, color = colors.textSecondary) }
        if (d.cameraLine != null) Text(d.cameraLine, fontSize = 13.sp, color = colors.textSecondary)
    }
}

private fun readDetails(context: Context, media: Media): Details {
    val uri: Uri = media.uri.toUri()
    val resolver = context.contentResolver
    var path: String? = null
    var taken = 0L
    var size = 0L
    resolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getColumnIndex(MediaStore.MediaColumns.DATA).takeIf { it >= 0 }?.let { path = c.getString(it) }
            c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN).takeIf { it >= 0 }?.let { taken = c.getLong(it) }
            c.getColumnIndex(MediaStore.MediaColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
        }
    }
    if (taken <= 0) taken = (media.dateAdded ?: 0L) * 1000
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    var exif: ExifInterface? = null
    if (!media.isVideo) try {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        resolver.openInputStream(uri)?.use { exif = ExifInterface(it) }
    } catch (_: Exception) {}
    val e = exif
    val model = listOfNotNull(e?.getAttribute(ExifInterface.TAG_MAKE), e?.getAttribute(ExifInterface.TAG_MODEL)).distinct().joinToString(" ").ifBlank { null }
    val mp = if (bounds.outWidth > 0) "${"%.0f".format(bounds.outWidth * bounds.outHeight / 1_000_000.0)}MP" else null
    val sizeLine = listOfNotNull(
        "%.2f MB".format(size / (1024.0 * 1024.0)),
        if (bounds.outWidth > 0) "${bounds.outWidth}x${bounds.outHeight}" else null,
        mp,
        media.duration?.let { formatDuration(it) }
    ).joinToString("   |   ")
    val camera = e?.let {
        listOfNotNull(
            it.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { v -> "ISO $v" },
            it.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { v -> v > 0 }?.let { v -> "${"%.0f".format(v)}mm" },
            it.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { v -> v > 0 }?.let { v -> "F$v" },
            it.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { v -> v > 0 }?.let { v -> if (v < 1) "1/${"%.0f".format(1 / v)} s" else "$v s" }
        ).joinToString("   |   ").ifBlank { null }
    }
    return Details(
        date = if (taken > 0) SimpleDateFormat("EEE, d MMMM yyyy · h:mm a", Locale.getDefault()).format(Date(taken)) else "Unknown date",
        name = media.name,
        path = path?.substringBeforeLast("/") ?: "",
        device = model,
        sizeLine = sizeLine,
        cameraLine = camera
    )
}

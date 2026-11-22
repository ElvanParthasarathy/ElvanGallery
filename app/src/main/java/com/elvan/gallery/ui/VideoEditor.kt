package com.elvan.gallery.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.SpannableString
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SingleColorLut
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.composables.icons.materialsymbols.roundedfilled.*
import com.elvan.navil.MaterialSymbols
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val VYellow = Color(0xFFFFD90D)
private val VPanel = Color(0xFF1A1A1A)
private val VRound = Color(0xFF202023)
private val VDisabled = Color(0xFF7D7D7F)
private val speeds = listOf(0.25f, 0.5f, 1f, 1.5f, 2f)

/** One video edit (undo / redo keep a list of these). Trim is in fractions of the clip. */
data class VideoEdit(
    val start: Float = 0f,
    val end: Float = 1f,
    val rotation: Int = 0,
    val flip: Boolean = false,
    val filter: Int = 0,
    val tones: Map<Tone, Float> = emptyMap(),
    val text: String = "",
    val speed: Float = 1f,
    val mute: Boolean = false
) {
    val color get() = EditState(filter = filter, tones = tones)
}

private enum class VideoTool(val label: String, val icon: ImageVector) {
    Trim("Trim", Sym.Rounded.Content_cut),
    Transform("Transform", Sym.Rounded.Crop_rotate),
    Filters("Filters", Sym.Rounded.Filter_vintage),
    Tone("Tone", Sym.Rounded.Tune),
    Decorations("Decorations", Sym.Rounded.Add_reaction),
    Audio("Audio", Sym.Rounded.Music_note)
}

/** A 17³ colour cube from the edit's colour matrix: the same filters and tone as photos, for every video frame. */
@OptIn(UnstableApi::class)
private fun colorLut(m: FloatArray): SingleColorLut {
    val n = 17
    val cube = Array(n) { r -> Array(n) { g -> IntArray(n) { b ->
        val rf = r * 255f / (n - 1); val gf = g * 255f / (n - 1); val bf = b * 255f / (n - 1)
        fun ch(row: Int) = (m[row * 5] * rf + m[row * 5 + 1] * gf + m[row * 5 + 2] * bf + m[row * 5 + 4]).roundToInt().coerceIn(0, 255)
        (0xFF shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
    } } }
    return SingleColorLut.createFromCube(cube)
}

@OptIn(UnstableApi::class)
private fun videoEffects(e: VideoEdit, withSpeed: Boolean): List<Effect> = buildList {
    if (e.rotation != 0 || e.flip) add(ScaleAndRotateTransformation.Builder().setRotationDegrees(-e.rotation.toFloat()).setScale(if (e.flip) -1f else 1f, 1f).build())
    if (e.color != EditState()) add(colorLut(e.color.matrix()))
    if (e.text.isNotBlank()) add(OverlayEffect(ImmutableList.of<TextureOverlay>(TextOverlay.createStaticTextOverlay(SpannableString(e.text)))))
    if (withSpeed && e.speed != 1f) add(SpeedChangeEffect(e.speed))
}

/**
 * The video editor, laid out as: undo/redo, Revert, Save along the top; the video with its play pill
 * and speed button; the tool's panel (the trim strip of frames with white grips); six tool icons along the bottom.
 * Saves a new video in Movies/Edited; the original stays.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoEditor(uri: String, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val undo = remember { mutableStateListOf<VideoEdit>() }
    val redo = remember { mutableStateListOf<VideoEdit>() }
    var edit by remember { mutableStateOf(VideoEdit()) }
    var tool by remember { mutableStateOf(VideoTool.Trim) }
    var exporting by remember { mutableStateOf<Float?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var askText by remember { mutableStateOf(false) }
    val changed = edit != VideoEdit()
    fun commit(next: VideoEdit) { if (next != edit) { undo.add(edit); redo.clear(); edit = next } }

    val player = remember(uri) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare() } }
    DisposableEffect(player) { onDispose { player.release() } }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (true) {
            if (duration <= 0) duration = player.duration.coerceAtLeast(0L)
            position = player.currentPosition
            if (duration > 0 && position >= (edit.end * duration).toLong()) { player.pause(); player.seekTo((edit.start * duration).toLong()) }
            playing = player.isPlaying
            delay(50)
        }
    }
    // The preview shows the edit as it will be saved (speed through the player, the rest as video effects)
    LaunchedEffect(edit.rotation, edit.flip, edit.filter, edit.tones, edit.text) { player.setVideoEffects(videoEffects(edit, withSpeed = false)) }
    LaunchedEffect(edit.speed, edit.mute) { player.setPlaybackSpeed(edit.speed); player.volume = if (edit.mute) 0f else 1f }

    fun export() {
        val out = File(context.cacheDir, "edit_${System.currentTimeMillis()}.mp4")
        val item = MediaItem.Builder().setUri(uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs((edit.start * duration).toLong()).setEndPositionMs((edit.end * duration).toLong()).build()
        ).build()
        val edited = EditedMediaItem.Builder(item)
            .setRemoveAudio(edit.mute || edit.speed != 1f) // speed changes keep the picture only
            .setEffects(Effects(ImmutableList.of(), videoEffects(edit, withSpeed = true)))
            .build()
        val transformer = Transformer.Builder(context).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                val ok = publishVideo(context, out)
                out.delete()
                exporting = null
                Toast.makeText(context, if (ok) "Saved to Movies/Edited" else "Could not save", Toast.LENGTH_SHORT).show()
                if (ok) { onSaved(); onClose() }
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                out.delete()
                exporting = null
                Toast.makeText(context, "Could not save: ${exportException.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        }).build()
        player.pause()
        exporting = 0f
        transformer.start(edited, out.absolutePath)
    }
    BackHandler { if (exporting == null) { if (changed) leaving = true else onClose() } }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(68.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VBarIcon(Sym.Rounded.Undo, "Undo", undo.isNotEmpty()) { redo.add(edit); edit = undo.removeAt(undo.lastIndex) }
            Spacer(Modifier.width(8.dp))
            VBarIcon(Sym.Rounded.Redo, "Redo", redo.isNotEmpty()) { undo.add(edit); edit = redo.removeAt(redo.lastIndex) }
            Spacer(Modifier.weight(1f))
            VBarText("Revert", changed) { commit(VideoEdit()) }
            VBarText(if (exporting != null) "Saving…" else "Save", changed && exporting == null && duration > 0) { export() }
            VBarIcon(MaterialSymbols.Rounded.MoreVert, "More", true) { if (changed) leaving = true else onClose() }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; this.player = player } },
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)
            )
            // The play pill and the round speed button under the video
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.height(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { if (player.isPlaying) player.pause() else player.play() }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (playing) Sym.RoundedFilled.Pause else Sym.RoundedFilled.Play_arrow, if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("  ${formatDuration(position)} / ${formatDuration(duration)}", color = Color.White, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { commit(edit.copy(speed = speeds[(speeds.indexOf(edit.speed) + 1) % speeds.size])) },
                    contentAlignment = Alignment.Center
                ) {
                    if (edit.speed == 1f) Icon(Sym.Rounded.Speed, "Speed", tint = Color.White, modifier = Modifier.size(18.dp))
                    else Text("${edit.speed}x".replace(".0x", "x"), color = VYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            exporting?.let {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Text("Saving video…", color = Color.White, fontSize = 15.sp)
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            when (tool) {
                VideoTool.Trim -> TrimStrip(uri, duration, edit, onSeek = { player.seekTo((it * duration).toLong()) }, onCommit = ::commit)
                VideoTool.Transform -> Row(
                    Modifier.clip(CircleShape).background(Color(0xFF0A0E12)).border(1.dp, Color(0xFF2C2F33), CircleShape).padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VCapsuleIcon(Sym.Rounded.Flip, "Flip") { commit(edit.copy(flip = !edit.flip)) }
                    VCapsuleIcon(Sym.Rounded.Rotate_left, "Rotate") { commit(edit.copy(rotation = (edit.rotation + 270) % 360)) }
                }
                VideoTool.Filters -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    itemsIndexed(photoFilters) { i, f ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(VRound)
                                    .then(if (edit.filter == i) Modifier.border(3.dp, Color.White, RoundedCornerShape(16.dp)) else Modifier)
                                    .clickable { commit(edit.copy(filter = i)) },
                                contentAlignment = Alignment.Center
                            ) { FilterSwatch(f.matrix) }
                            Text(f.name, color = if (edit.filter == i) Color.White else Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                VideoTool.Tone -> VideoTone(edit, ::commit)
                VideoTool.Decorations -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (edit.text.isBlank()) "Add text" else "Edit text", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(Color.White).clickable { askText = true }.padding(horizontal = 22.dp, vertical = 12.dp)
                    )
                    if (edit.text.isNotBlank()) Text("Remove text", color = VDisabled, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp).clickable { commit(edit.copy(text = "")) })
                }
                VideoTool.Audio -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(false to "Original sound", true to "No sound").forEach { (mute, label) ->
                        Row(
                            Modifier.clip(CircleShape).background(if (edit.mute == mute) Color.White else VPanel).clickable { commit(edit.copy(mute = mute)) }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (mute) Sym.Rounded.Volume_off else Sym.Rounded.Volume_up, null, tint = if (edit.mute == mute) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                            Text("  $label", color = if (edit.mute == mute) Color.Black else Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            VideoTool.entries.forEach { t ->
                Box(Modifier.padding(horizontal = 1.dp).size(45.dp).clip(CircleShape).clickable { tool = t }, contentAlignment = Alignment.Center) {
                    Icon(t.icon, t.label, tint = if (t == tool) VYellow else Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }

    if (askText) {
        var text by remember { mutableStateOf(edit.text) }
        AlertDialog(
            onDismissRequest = { askText = false }, containerColor = Color(0xFF252527), shape = RoundedCornerShape(26.dp),
            text = { OutlinedTextField(text, { text = it }, shape = RoundedCornerShape(16.dp)) },
            confirmButton = { TextButton(onClick = { askText = false; commit(edit.copy(text = text.trim())) }) { Text("Done", color = Color.White) } },
            dismissButton = { TextButton(onClick = { askText = false }) { Text("Cancel", color = VDisabled) } }
        )
    }
    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false }, containerColor = Color(0xFF252527), shape = RoundedCornerShape(26.dp),
        text = { Text("Save or discard your changes?", color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { leaving = false }) { Text("Cancel", color = Color.White) }
                TextButton(onClick = { leaving = false; onClose() }) { Text("Discard", color = Color.White) }
                TextButton(onClick = { leaving = false; export() }) { Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
    )
}

@Composable
private fun VBarIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (enabled) Color.White else VDisabled, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun VBarText(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (enabled) Color.White else VDisabled, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun VCapsuleIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** A filter's look on a fixed colour swatch (video frames are not decoded for every filter tile). */
private val swatch: Bitmap by lazy {
    Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { b ->
        android.graphics.Canvas(b).drawPaint(android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 64f, 64f, intArrayOf(0xFFE8A87C.toInt(), 0xFF41B3A3.toInt(), 0xFF5D5C61.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        })
    }
}

@Composable
private fun FilterSwatch(matrix: FloatArray) {
    Image(
        remember { swatch.asImageBitmap() }, null, contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(matrix)), modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun VideoTone(edit: VideoEdit, onCommit: (VideoEdit) -> Unit) {
    var active by remember { mutableStateOf(Tone.Brightness) }
    val tones = Tone.entries.filter { it != Tone.Vignette }
    var value by remember(active, edit) { mutableFloatStateOf(edit.tones[active] ?: 0f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (value != 0f) "${active.label}  ${value.roundToInt()}" else active.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            itemsIndexed(tones) { _, t ->
                Box(Modifier.size(48.dp).clip(CircleShape).background(if (t == active) Color(0xFFFAFAFA) else VRound).clickable { active = t }, contentAlignment = Alignment.Center) {
                    Icon(t.icon, t.label, tint = if (t == active) Color.Black else Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        // The same ruler as the photo editor, committed when the finger lifts
        Box(Modifier.fillMaxWidth().height(30.dp).systemGestureExclusion().padding(horizontal = 28.dp).pointerInput(active, edit) {
            detectDragGestures(onDragEnd = { onCommit(edit.copy(tones = edit.tones + (active to value))) }) { change, d ->
                change.consume(); value = (value + d.x / (size.width / 2f) * 100f).coerceIn(-100f, 100f)
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val step = 8.dp.toPx(); val mid = size.width / 2; val shift = -value / 100f * size.width / 2
                var x = mid + shift % step - step * 60
                while (x < size.width + step) {
                    if (x >= 0) drawLine(Color(0xFF6C6C6D), Offset(x, size.height * 0.35f), Offset(x, size.height * 0.65f), 1.5.dp.toPx())
                    x += step
                }
                drawLine(VYellow, Offset(mid, 0f), Offset(mid, size.height), 2.5.dp.toPx())
            }
        }
    }
}

/** The trim strip: the clip's frames in a row, white rounded grips at both ends, the outside dimmed, a playhead. */
@Composable
private fun TrimStrip(uri: String, duration: Long, edit: VideoEdit, onSeek: (Float) -> Unit, onCommit: (VideoEdit) -> Unit) {
    val context = LocalContext.current
    val frames by produceState<List<Bitmap>>(emptyList(), uri) {
        value = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, Uri.parse(uri))
                val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                (0 until 10).mapNotNull { i ->
                    val at = ms * 1000 * (i + 0.5) / 10
                    if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(at.toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 160)
                    else r.getFrameAtTime(at.toLong())
                }
            } catch (e: Exception) { emptyList() } finally { r.release() }
        }
    }
    var start by remember(edit) { mutableFloatStateOf(edit.start) }
    var end by remember(edit) { mutableFloatStateOf(edit.end) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(formatDuration((start * duration).toLong()), color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(((end - start) * duration).toLong()), color = VYellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(formatDuration((end * duration).toLong()), color = Color.White, fontSize = 12.sp)
        }
        // The grips sit at the screen edges: keep the system back swipe off them
        BoxWithConstraints(Modifier.fillMaxWidth().height(47.dp).systemGestureExclusion()) {
            val w = constraints.maxWidth.toFloat()
            Row(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
                frames.forEach { f ->
                    Image(f.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
            Canvas(Modifier.fillMaxSize().pointerInput(edit) {
                var grip = 0
                detectDragGestures(
                    onDragStart = { p -> grip = if (kotlin.math.abs(p.x - start * w) < kotlin.math.abs(p.x - end * w)) 0 else 1 },
                    onDragEnd = { onCommit(edit.copy(start = start, end = end)) },
                    onDrag = { change, d ->
                        change.consume()
                        if (grip == 0) { start = (start + d.x / w).coerceIn(0f, end - 0.03f); onSeek(start) }
                        else { end = (end + d.x / w).coerceIn(start + 0.03f, 1f); onSeek(end) }
                    }
                )
            }) {
                val sx = start * size.width; val ex = end * size.width
                drawRect(Color.Black.copy(alpha = 0.65f), Offset.Zero, Size(sx, size.height))
                drawRect(Color.Black.copy(alpha = 0.65f), Offset(ex, 0f), Size(size.width - ex, size.height))
                drawRect(Color.White, Offset(sx, 0f), Size(ex - sx, size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                val gw = 14.dp.toPx()
                drawRoundRect(Color.White, Offset(sx - gw / 2, -2.dp.toPx()), Size(gw, size.height + 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                drawRoundRect(Color.White, Offset(ex - gw / 2, -2.dp.toPx()), Size(gw, size.height + 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                listOf(sx, ex).forEach { x -> drawLine(Color(0xFF8E8E93), Offset(x, size.height * 0.35f), Offset(x, size.height * 0.65f), 2.dp.toPx()) }
            }
        }
    }
}

/** Moves the exported file into Movies/Edited through MediaStore. */
private fun publishVideo(context: Context, file: File): Boolean = try {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "Edited_${System.currentTimeMillis()}.mp4")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Edited")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
    context.contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
    if (Build.VERSION.SDK_INT >= 29) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    true
} catch (e: Exception) { e.printStackTrace(); false }

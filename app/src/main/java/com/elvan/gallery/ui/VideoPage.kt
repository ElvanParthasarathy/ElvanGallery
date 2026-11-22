package com.elvan.gallery.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.composables.icons.materialsymbols.roundedfilled.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A video in the viewer, as it should play: it starts by itself when its page is open; a capsule with
 * play/pause and the time sits over the picture with a capture button on the left and sound on the right; below, a strip
 * of the video's own frames is the seek bar (drag it), with a white playhead.
 */
@Composable
fun VideoPage(uri: String, isCurrent: Boolean, chrome: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }, contentAlignment = Alignment.Center) {
        if (!isCurrent) {
            coil3.compose.AsyncImage(
                coil3.request.ImageRequest.Builder(context).data(thumbModel(uri)).size(640).build(), null,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
            )
            return@Box
        }
        val player = remember(uri) {
            ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = true }
        }
        DisposableEffect(player) { onDispose { player.release() } }
        var playing by remember { mutableStateOf(true) }
        var muted by remember { mutableStateOf(false) }
        var position by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(0L) }
        DisposableEffect(player) {
            val listener = object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying } }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
        LaunchedEffect(player) {
            while (true) { position = player.currentPosition; duration = player.duration.coerceAtLeast(0L); delay(50) }
        }
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; this.player = player } },
            modifier = Modifier.fillMaxSize()
        )
        if (chrome) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 84.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundControl(Sym.Rounded.Screenshot_frame, "Capture") {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            val r = MediaMetadataRetriever()
                            try {
                                r.setDataSource(context, Uri.parse(uri))
                                r.getFrameAtTime(player.currentPosition * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { saveEdited(context, it, "Captures", "Capture") } ?: false
                            } catch (e: Exception) { false } finally { r.release() }
                        }
                        Toast.makeText(context, if (ok) "Frame saved to Pictures/Captures" else "Could not capture", Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.height(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                            if (playing) player.pause() else player.play()
                        }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (playing) Sym.RoundedFilled.Pause else Sym.RoundedFilled.Play_arrow, if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("  ${formatClock(position)}", color = Color.White, fontSize = 13.sp)
                    Text(" / ${formatClock(duration)}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                RoundControl(if (muted) Sym.Rounded.Volume_off else Sym.Rounded.Volume_up, if (muted) "Sound off" else "Sound on") {
                    muted = !muted; player.volume = if (muted) 0f else 1f
                }
            }
            FrameScrubber(uri, if (duration > 0) position.toFloat() / duration else 0f) { player.seekTo((it * duration).toLong()) }
        }
    }
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

/** The "00:27" clock (two-digit minutes). */
private fun formatClock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/** The video's frames in a row; the white playhead marks where it is; drag anywhere on it to seek. */
@Composable
private fun FrameScrubber(uri: String, progress: Float, onSeek: (Float) -> Unit) {
    val context = LocalContext.current
    val frames by produceState<List<Bitmap>>(emptyList(), uri) {
        value = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, Uri.parse(uri))
                val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                (0 until 9).mapNotNull { i ->
                    val at = (ms * 1000 * (i + 0.5) / 9).toLong()
                    if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 120, 120) else r.getFrameAtTime(at)
                }
            } catch (e: Exception) { emptyList() } finally { r.release() }
        }
    }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: progress
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).height(44.dp).systemGestureExclusion()) {
        Row(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))) {
            frames.forEach { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight()) }
        }
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { p -> dragging = (p.x / size.width).coerceIn(0f, 1f); onSeek(dragging!!) },
                onDragEnd = { dragging = null },
                onDragCancel = { dragging = null },
                onDrag = { change, _ -> change.consume(); dragging = (change.position.x / size.width).coerceIn(0f, 1f); onSeek(dragging!!) }
            )
        }.pointerInput(Unit) { detectTapGestures { p -> onSeek((p.x / size.width).coerceIn(0f, 1f)) } }) {
            val x = shown * size.width
            drawLine(Color.White, Offset(x, -4.dp.toPx()), Offset(x, size.height + 4.dp.toPx()), 3.dp.toPx())
        }
    }
}

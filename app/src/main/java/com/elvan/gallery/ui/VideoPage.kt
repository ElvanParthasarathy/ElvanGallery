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
        // Samsung's video controls: play/pause beside a full-width seek line with the times under it; capture and sound below
        if (chrome) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 132.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.Top) {
                RoundControl(if (playing) Sym.RoundedFilled.Pause else Sym.RoundedFilled.Play_arrow, if (playing) "Pause" else "Play") {
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                    if (playing) player.pause() else player.play()
                }
                Column(Modifier.weight(1f).padding(start = 10.dp, end = 10.dp)) {
                    SeekLine(if (duration > 0) position.toFloat() / duration else 0f) { player.seekTo((it * duration).toLong()) }
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatClock(position), color = Color.White, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Text(formatClock(duration), color = Color.White, fontSize = 13.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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
                RoundControl(if (muted) Sym.Rounded.Volume_off else Sym.Rounded.Volume_up, if (muted) "Sound off" else "Sound on") {
                    muted = !muted; player.volume = if (muted) 0f else 1f
                }
            }
        }
    }
}

/** A thin white line with a round thumb; tap or drag anywhere on it to seek. */
@Composable
private fun SeekLine(progress: Float, onSeek: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: progress
    Canvas(Modifier.fillMaxWidth().height(36.dp).systemGestureExclusion().pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { p -> dragging = (p.x / size.width).coerceIn(0f, 1f); onSeek(dragging!!) },
            onDragEnd = { dragging = null },
            onDragCancel = { dragging = null },
            onDrag = { change, _ -> change.consume(); dragging = (change.position.x / size.width).coerceIn(0f, 1f); onSeek(dragging!!) }
        )
    }.pointerInput(Unit) { detectTapGestures { p -> onSeek((p.x / size.width).coerceIn(0f, 1f)) } }) {
        val y = size.height / 2
        val x = shown * size.width
        drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
        drawLine(Color.White, Offset(0f, y), Offset(x, y), 2.dp.toPx())
        drawCircle(Color.White, 7.dp.toPx(), Offset(x, y))
    }
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Color(0x99303033)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

/** The "00:27" clock (two-digit minutes). */
private fun formatClock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}


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

/** The playing video's time, shared between its page (which plays it) and the filmstrip (which shows and seeks it). */
class VideoClock {
    var uri by mutableStateOf<String?>(null)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var seek: (Long) -> Unit = {}
}

/**
 * A video in the viewer, as Samsung Gallery shows it (measured on a Galaxy A53): it starts by itself; a 30 dp capsule
 * with play/pause and "00:02 / 00:16" sits centred above the filmstrip, a round capture button on the left and sound on
 * the right. The filmstrip below is the seek bar: the video's frames with a white playhead.
 */
@Composable
fun VideoPage(uri: String, isCurrent: Boolean, chrome: Boolean, clock: VideoClock, onTap: () -> Unit) {
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
        DisposableEffect(player) {
            clock.uri = uri
            clock.seek = { player.seekTo(it) }
            onDispose { if (clock.uri == uri) { clock.uri = null; clock.seek = {} }; player.release() }
        }
        var playing by remember { mutableStateOf(true) }
        var muted by remember { mutableStateOf(false) }
        DisposableEffect(player) {
            val listener = object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying } }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
        LaunchedEffect(player) {
            while (true) {
                androidx.compose.runtime.withFrameMillis { }
                clock.position = player.currentPosition
                clock.duration = player.duration.coerceAtLeast(0L)
            }
        }
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; this.player = player } },
            modifier = Modifier.fillMaxSize()
        )
        // The capsule's foot sits 136 dp above the bottom, 18 dp over the filmstrip, as Samsung's
        if (chrome) Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 136.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundControl(Sym.Rounded.Screenshot_frame_2, "Capture") {
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
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.height(30.dp).clip(CircleShape).background(Color(0x66000000))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                            if (playing) player.pause() else player.play()
                        }.padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (playing) Sym.RoundedFilled.Pause else Sym.RoundedFilled.Play_arrow, if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("  ${formatClock(clock.position)}", color = Color.White, fontSize = 13.sp)
                    Text(" / ${formatClock(clock.duration)}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
            }
            RoundControl(if (muted) Sym.Rounded.Volume_off else Sym.Rounded.Volume_up, if (muted) "Sound off" else "Sound on") {
                muted = !muted; player.volume = if (muted) 0f else 1f
            }
        }
    }
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Color(0x66000000)).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

/** The "00:27" clock (two-digit minutes). */
fun formatClock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/** A video's frames across [count] slots, for the filmstrip's wide video tile. */
@Composable
fun videoFrames(uri: String, count: Int): List<Bitmap> {
    val context = LocalContext.current
    val frames by produceState<List<Bitmap>>(emptyList(), uri) {
        value = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, Uri.parse(uri))
                val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                (0 until count).mapNotNull { i ->
                    val at = (ms * 1000 * (i + 0.5) / count).toLong()
                    if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 200, 200) else r.getFrameAtTime(at)
                }
            } catch (e: Exception) { emptyList() } finally { r.release() }
        }
    }
    return frames
}

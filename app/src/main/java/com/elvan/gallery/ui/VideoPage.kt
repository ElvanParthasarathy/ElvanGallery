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
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.graphicsLayer
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
fun VideoPage(uri: String, isCurrent: Boolean, chrome: Boolean, clock: VideoClock, onZoomChange: (Boolean) -> Unit = {}, onTap: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Samsung: while playing, the controls row hides itself after 3 s; a tap brings it back (rather than hiding all)
    var autoHidden by remember(uri) { mutableStateOf(false) }
    var touched by remember(uri) { androidx.compose.runtime.mutableLongStateOf(0L) }
    // Zoom (Samsung): a double tap zooms in about the tap and back out; two fingers zoom; once zoomed one finger pans
    val zoom = remember(uri) { androidx.compose.animation.core.Animatable(1f) }
    var pan by remember(uri) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(zoom.value > 1.01f) { onZoomChange(zoom.value > 1.01f) }
    Box(Modifier.fillMaxSize().pointerInput(uri) {
        detectTapGestures(
            onTap = { if (autoHidden && chrome) { autoHidden = false; touched = System.currentTimeMillis() } else onTap() },
            onDoubleTap = { p ->
                scope.launch {
                    val target = if (zoom.value > 1.01f) 1f else 2.5f
                    val from = pan
                    val to = if (target == 1f) Offset.Zero else (Offset(size.width / 2f, size.height / 2f) - p) * (target - 1f)
                    val start = zoom.value
                    zoom.animateTo(target, androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 500f)) {
                        val t = ((value - start) / (target - start)).coerceIn(0f, 1f)
                        pan = from + (to - from) * t
                    }
                }
            },
            // A hold does nothing (it must not also count as a tap)
            onLongPress = {}
        )
    }.pointerInput(uri) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val fingers = event.changes.count { it.pressed }
                if (fingers > 1 || zoom.value > 1.01f) {
                    val z = (zoom.value * event.calculateZoom()).coerceIn(1f, 5f)
                    scope.launch { zoom.snapTo(z) }
                    val lim = Offset(size.width * (z - 1f) / 2f, size.height * (z - 1f) / 2f)
                    val p = pan + event.calculatePan()
                    pan = Offset(p.x.coerceIn(-lim.x, lim.x), p.y.coerceIn(-lim.y, lim.y))
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (zoom.value < 1.05f) scope.launch { zoom.animateTo(1f); pan = Offset.Zero }
        }
    }, contentAlignment = Alignment.Center) {
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
        // Until the first frame is drawn the video's own picture shows (Samsung never shows a black page)
        var firstFrame by remember(uri) { mutableStateOf(false) }
        LaunchedEffect(chrome) { autoHidden = false }
        LaunchedEffect(playing, chrome, touched) { autoHidden = false; if (playing && chrome) { delay(3000); autoHidden = true } }
        // Sound on or off is remembered from one video to the next, as Samsung does
        val prefs = remember { defaultPrefs(context) }
        var muted by remember { mutableStateOf(prefs.getBoolean("VIDEO_MUTED", false)) }
        LaunchedEffect(player, muted) { player.volume = if (muted) 0f else 1f }
        var captured by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(captured) { if (captured != null) { delay(2500); captured = null } }
        var videoRatio by remember { mutableFloatStateOf(0f) }
        val texture = remember { arrayOfNulls<android.view.TextureView>(1) }
        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onRenderedFirstFrame() { firstFrame = true }
                override fun onVideoSizeChanged(v: androidx.media3.common.VideoSize) {
                    if (v.width > 0 && v.height > 0) videoRatio = v.width * v.pixelWidthHeightRatio / v.height
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
        LaunchedEffect(player) {
            while (true) {
                androidx.compose.runtime.withFrameMillis { }
                // Only the video on show drives the shared clock
                if (clock.uri == uri) {
                    clock.position = player.currentPosition
                    clock.duration = player.duration.coerceAtLeast(0L)
                }
            }
        }
        // A texture (not a surface) so the picture can be zoomed and moved like a photo
        if (videoRatio > 0f) AndroidView(
            factory = { android.view.TextureView(it).also { tv -> texture[0] = tv; player.setVideoTextureView(tv) } },
            modifier = Modifier.aspectRatio(videoRatio).graphicsLayer {
                scaleX = zoom.value; scaleY = zoom.value; translationX = pan.x; translationY = pan.y
            }
        ) else AndroidView(
            factory = { android.view.TextureView(it).also { tv -> texture[0] = tv; player.setVideoTextureView(tv) } },
            modifier = Modifier.size(1.dp)
        )
        if (!firstFrame) coil3.compose.AsyncImage(
            coil3.request.ImageRequest.Builder(context).data(thumbModel(uri)).size(640).placeholderMemoryCacheKey(thumbPlaceholder(uri)).build(), null,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
        )
        // Samsung: after a capture, a small picture of the frame shows above the capture button for a moment
        captured?.let { b ->
            androidx.compose.foundation.Image(
                b.asImageBitmap(), "Captured frame", contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 176.dp)
                    .size(30.dp * (b.width.toFloat() / b.height).coerceIn(0.5f, 2f), 30.dp).clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color.White, RoundedCornerShape(4.dp))
            )
        }
        // The capsule's foot sits 136 dp above the bottom, 18 dp over the filmstrip, as Samsung's
        androidx.compose.animation.AnimatedVisibility(
            chrome && !autoHidden, modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()
        ) { Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 136.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundControl(Sym.Rounded.Screenshot_frame_2, "Capture") {
                scope.launch {
                    // The frame on screen, taken from the video's texture; the file's decoder as a fallback
                    val shown = texture[0]?.takeIf { it.isAvailable }?.bitmap
                    val at = player.currentPosition * 1000 // the player is read on the main thread only
                    val ok = withContext(Dispatchers.IO) {
                        val b = shown ?: run {
                            val r = MediaMetadataRetriever()
                            try {
                                r.setDataSource(context, Uri.parse(uri))
                                r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST) ?: r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            } catch (e: Exception) { null } finally { r.release() }
                        }
                        b != null && saveEdited(context, b, "Captures", "Capture").also { saved -> if (saved) captured = b }
                    }
                    if (!ok) Toast.makeText(context, "Could not capture", Toast.LENGTH_SHORT).show()
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
                    Text(" / ${formatClock(clock.duration)}", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
            RoundControl(if (muted) Sym.Rounded.Volume_off else Sym.Rounded.Volume_up, if (muted) "Sound off" else "Sound on") {
                muted = !muted
                prefs.edit().putBoolean("VIDEO_MUTED", muted).apply()
                // Turning sound on shows the phone's volume slider, as Samsung's does
                if (!muted) (context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager)
                    .adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_SAME, android.media.AudioManager.FLAG_SHOW_UI)
            }
        } }
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

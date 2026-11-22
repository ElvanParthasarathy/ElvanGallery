package com.elvan.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.elvan.gallery.data.Media
import kotlinx.coroutines.delay

/** Start slideshow: the pictures one after another, full screen, each for three seconds with a soft fade; a tap or Back ends it. */
@Composable
fun SlideshowPage(media: List<Media>, onClose: () -> Unit) {
    val pictures = remember(media) { media.filter { !it.isVideo } }
    var at by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose { bars?.show(WindowInsetsCompat.Type.systemBars()); view.keepScreenOn = false }
    }
    LaunchedEffect(pictures) {
        if (pictures.isEmpty()) { onClose(); return@LaunchedEffect }
        while (true) { delay(3000); at = (at + 1) % pictures.size }
    }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(MutableInteractionSource(), null, onClick = onClose)) {
        val context = LocalContext.current
        Crossfade(pictures.getOrNull(at), animationSpec = tween(700), label = "slideshow") { m ->
            if (m != null) AsyncImage(
                model = ImageRequest.Builder(context).data(m.uri).size(1600).build(),
                contentDescription = m.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
            )
        }
    }
}

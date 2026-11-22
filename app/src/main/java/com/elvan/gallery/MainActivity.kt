package com.elvan.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.elvan.gallery.ui.GalleryApp
import com.elvan.gallery.ui.GalleryLibrary
import com.elvan.gallery.ui.GalleryTheme

/** Elvan Gallery is one Compose screen in Elvan Navil UI; pages open over the tabs. */
class MainActivity : ComponentActivity() {
    private lateinit var library: GalleryLibrary

    private val mediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private val askForMedia = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) library.reload()
        else Toast.makeText(this, "Elvan Gallery needs access to your pictures and videos", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        library = GalleryLibrary(applicationContext, lifecycleScope)
        if (mediaPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) library.reload()
        else askForMedia.launch(mediaPermissions)
        setContent { GalleryTheme { GalleryApp(library) } }
    }
}

package com.elvan.gallery.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.elvan.gallery.data.PrivateVault
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.elvan.navil.ElvanFloatingAction
import com.elvan.navil.ElvanFloatingActionBar
import com.elvan.navil.ElvanShell
import com.elvan.navil.MaterialSymbols
import com.elvan.navil.rememberShellColors
import java.io.File

/** Screenshots and the recents preview stay blank while a private page is open. */
@Composable
fun SecureWindow() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** The private safe: the phone's lock screen first, then the saved pictures. */
@Composable
fun PrivateSafePage(nav: Navigator) {
    val context = LocalContext.current
    val colors = rememberShellColors()
    val vault = remember { PrivateVault(context) }
    var unlocked by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf(emptyList<File>()) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    val grid = rememberLazyGridState()
    SecureWindow()

    val auth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Authentication Successful", Toast.LENGTH_SHORT).show()
            unlocked = true
        } else {
            Toast.makeText(context, "Authentication Failed", Toast.LENGTH_SHORT).show()
            nav.back()
        }
    }
    LaunchedEffect(Unit) {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isKeyguardSecure) {
            @Suppress("DEPRECATION")
            auth.launch(keyguard.createConfirmDeviceCredentialIntent("Authentication Required", "Please authenticate to access private safe"))
        } else {
            Toast.makeText(context, "No lock screen security set up", Toast.LENGTH_LONG).show()
            nav.back()
        }
    }
    LaunchedEffect(unlocked) { if (unlocked) images = vault.list() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && addToSafe(context, uri, vault)) images = vault.list()
    }

    ElvanShell(
        gridState = grid, title = "Private Safe", showNavbar = false, onBack = nav::back, hasFloatingBar = unlocked,
        floatingBar = {
            if (unlocked) ElvanFloatingActionBar(listOf(ElvanFloatingAction("Add", MaterialSymbols.Rounded.Add, {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })))
        }
    ) {
        if (unlocked) ElvanGrid(grid, columns = 4) {
            itemsIndexed(images, key = { _, f -> f.path }) { i, file ->
                Thumb(file.path, Modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(colors.surface).clickable { viewing = i })
            }
        }
    }

    viewing?.let { start ->
        BackHandler { viewing = null }
        ZoomViewer(
            models = images,
            start = start,
            title = { images[it].name },
            onBack = { viewing = null },
            actions = { page, _ ->
                val file = images[page]
                listOf(
                    ElvanFloatingAction("Share", MaterialSymbols.Rounded.Share, { vault.share(file) }),
                    ElvanFloatingAction("Save", Sym.Rounded.Download, {
                        Toast.makeText(context, if (vault.restore(file)) "Saved to Pictures/Restored" else "Could not save", Toast.LENGTH_SHORT).show()
                    }),
                    ElvanFloatingAction("Delete", MaterialSymbols.Rounded.Delete, {
                        vault.delete(file)
                        images = vault.list()
                        if (images.isEmpty()) viewing = null
                    }, isDestructive = true)
                )
            }
        )
    }
}

/** Copies the picked picture into the safe; removes the original when the setting asks. */
private fun addToSafe(context: Context, uri: Uri, vault: PrivateVault): Boolean {
    if (vault.add(uri) == null) { Toast.makeText(context, "Could not read the picture", Toast.LENGTH_SHORT).show(); return false }
    if (defaultPrefs(context).getBoolean("DELETE_AFTER_SAVE", false)) {
        try { context.contentResolver.delete(uri, null, null) } catch (e: Exception) { e.printStackTrace() }
    }
    return true
}

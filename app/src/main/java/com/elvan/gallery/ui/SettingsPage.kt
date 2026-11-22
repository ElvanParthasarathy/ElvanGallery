package com.elvan.gallery.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.elvan.gallery.data.GroupBackup
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.elvan.navil.ElvanLazyColumn
import com.elvan.navil.ElvanSettingsRow
import com.elvan.navil.ElvanSettingsSection
import com.elvan.navil.ElvanSettingsSwitch
import com.elvan.navil.ElvanShell


/** Settings, in Elvan Navil rows. */
@Composable
fun SettingsPage(library: GalleryLibrary, nav: Navigator) {
    val context = LocalContext.current
    val prefs = remember { defaultPrefs(context) }
    val list = rememberLazyListState()
    var filmstrip by remember { mutableStateOf(prefs.getBoolean("ENABLE_FILMSTRIP", true)) }
    var essential by remember { mutableStateOf(prefs.getBoolean("SHOW_ESSENTIAL_ALBUMS", false)) }
    var deleteAfterSave by remember { mutableStateOf(prefs.getBoolean("DELETE_AFTER_SAVE", false)) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val exportSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val data = pendingExport
        pendingExport = null
        if (uri != null && data != null) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
            Toast.makeText(context, "Groups exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = try { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } catch (e: Exception) { null }
        val count = json?.let { GroupBackup.import(library.settings, it) }
        Toast.makeText(context, if (count == null) "This is not a group backup" else "$count group(s) restored", Toast.LENGTH_SHORT).show()
        if (count != null) library.reload()
    }

    ElvanShell(scrollState = list, title = "Gallery settings", showNavbar = false, onBack = nav::back, collapsedOnly = true) {
        ElvanLazyColumn(state = list) {
            item(key = "shell_top_spacer") { Spacer(Modifier.height(1.dp)) }
            item(key = "ui") {
                ElvanSettingsSection(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), title = "UI Changes") {
                    ElvanSettingsRow(
                        title = "Filmstrip", icon = Sym.Rounded.View_carousel,
                        description = "Enable filmstrip in image viewer for easy access of images.",
                        onClick = { filmstrip = !filmstrip; prefs.edit().putBoolean("ENABLE_FILMSTRIP", filmstrip).apply() },
                        customTrailing = { ElvanSettingsSwitch(filmstrip, { filmstrip = it; prefs.edit().putBoolean("ENABLE_FILMSTRIP", it).apply() }) }
                    )
                    ElvanSettingsRow(
                        title = "Show Essential Albums", icon = Sym.Rounded.Star,
                        description = "Show only the albums you select on the Albums Tab instead of showing them all.",
                        onClick = { essential = !essential; prefs.edit().putBoolean("SHOW_ESSENTIAL_ALBUMS", essential).apply(); library.albums?.showOnlyEssential(essential) },
                        customTrailing = {
                            ElvanSettingsSwitch(essential, {
                                essential = it; prefs.edit().putBoolean("SHOW_ESSENTIAL_ALBUMS", it).apply(); library.albums?.showOnlyEssential(it)
                            })
                        }
                    )
                }
            }
            item(key = "groups") {
                ElvanSettingsSection(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), title = "Groups") {
                    ElvanSettingsRow(title = "Import", icon = Sym.Rounded.Download, description = "Restore your album groups from a backup file",
                        onClick = { importPicker.launch(arrayOf("application/json", "text/plain", "*/*")) })
                    ElvanSettingsRow(title = "Export", icon = Sym.Rounded.Upload, description = "Save your album groups to backup and restore later",
                        onClick = { GroupBackup.export(library.settings)?.let { pendingExport = it; exportSaver.launch(GroupBackup.fileName()) } ?: Toast.makeText(context, "No groups yet", Toast.LENGTH_SHORT).show() })
                }
            }
            item(key = "safe") {
                ElvanSettingsSection(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), title = "Private safe") {
                    ElvanSettingsRow(title = "Private Safe", icon = Sym.Rounded.Lock, onClick = { dialog = "safe" })
                    ElvanSettingsRow(
                        title = "Delete", icon = Sym.Rounded.Delete,
                        description = "Delete the original image after saving it into private safe.",
                        onClick = { deleteAfterSave = !deleteAfterSave; prefs.edit().putBoolean("DELETE_AFTER_SAVE", deleteAfterSave).apply() },
                        customTrailing = { ElvanSettingsSwitch(deleteAfterSave, { deleteAfterSave = it; prefs.edit().putBoolean("DELETE_AFTER_SAVE", it).apply() }) }
                    )
                }
            }
            item(key = "privacy") {
                ElvanSettingsSection(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), title = "Privacy") {
                    ElvanSettingsRow(title = "Permissions", icon = Sym.Rounded.Shield, description = "Photos and videos access for this app",
                        onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) })
                }
            }
            item(key = "about") {
                ElvanSettingsSection(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), title = "About") {
                    ElvanSettingsRow(title = "About Elvan Navil Gallery", icon = Sym.Rounded.Info, description = "Version ${appVersion(context)} · Elvan Navil UI",
                        onClick = { dialog = "about" })
                }
            }
        }
    }

    when (dialog) {
        "safe" -> ElvanDialog(
            title = "About Private Safe",
            text = "Private Safe keeps pictures inside this app, behind your phone's lock screen. " +
                "You can delete the original picture after saving it in the safe. " +
                "Do not clear this app's data, or the pictures in the safe are lost.",
            confirm = "OK", onDismiss = { dialog = null }
        ) { dialog = null }
        "about" -> ElvanDialog(
            title = "Elvan Navil Gallery",
            text = "Version ${appVersion(context)}\nDesigned and built by Elvan Parthasarathy with Elvan Navil UI.",
            confirm = "OK", onDismiss = { dialog = null }
        ) { dialog = null }
    }
}

private fun appVersion(context: android.content.Context): String =
    try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }

package com.elvan.gallery.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.elvan.navil.LocalAppFontFamily
import com.elvan.navil.MaterialSymbols
import com.elvan.navil.frostedGlass
import com.elvan.navil.rememberShellColors

/** Searches typed and sent, newest first; kept on the phone only. */
private object RecentSearches {
    private const val KEY = "search_recent"
    fun load(context: Context): List<String> =
        prefs(context).getString(KEY, "")!!.split('\n').filter { it.isNotBlank() }
    fun add(context: Context, q: String) = save(context, (listOf(q) + load(context).filterNot { it.equals(q, true) }).take(10))
    fun remove(context: Context, q: String) = save(context, load(context) - q)
    fun clear(context: Context) = save(context, emptyList())
    private fun save(context: Context, list: List<String>) = prefs(context).edit().putString(KEY, list.joinToString("\n")).apply()
    private fun prefs(context: Context) = context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE)
}

/**
 * Search as its own page: suggestions and recent searches on top, results in a grid, and the field floating at the
 * bottom just above the keyboard, which opens straight away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchPage(library: GalleryLibrary, nav: Navigator) {
    val colors = rememberShellColors()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var recent by remember { mutableStateOf(RecentSearches.load(context)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val all = library.all
    val q = query.trim()
    val results = remember(all, q) {
        if (q.isEmpty()) emptyList()
        else if (q.equals("videos", true) || q.equals("video", true)) all.filter { it.isVideo }
        else all.filter { it.name.contains(q, true) || it.album.contains(q, true) }
    }
    // Suggestions: the albums with the most pictures
    val suggestions = remember(all) { all.groupingBy { it.album }.eachCount().entries.sortedByDescending { it.value }.take(4).map { it.key } }
    fun send(text: String) {
        query = text
        if (text.isNotBlank()) { RecentSearches.add(context, text.trim()); recent = RecentSearches.load(context) }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyVerticalGrid(
            GridCells.Fixed(4), state = rememberLazyGridState(),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            item(key = "back", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp)) {
                    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = nav::back), contentAlignment = Alignment.Center) {
                        Icon(Sym.Rounded.Arrow_back_ios_new, "Navigate up", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (q.isEmpty()) {
                if (suggestions.isNotEmpty()) item(key = "suggest", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
                        Text("You might be looking for", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.forEach { s ->
                                Text(s, fontSize = 15.sp, color = colors.textPrimary, modifier = Modifier.clip(CircleShape).background(colors.iconBg)
                                    .clickable { send(s) }.padding(horizontal = 16.dp, vertical = 10.dp))
                            }
                        }
                    }
                }
                if (recent.isNotEmpty()) item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 24.dp)) {
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent searches", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, modifier = Modifier.weight(1f))
                            Text("Clear all", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.link,
                                modifier = Modifier.clip(CircleShape).clickable { RecentSearches.clear(context); recent = emptyList() }.padding(8.dp))
                        }
                        Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(colors.surface).padding(vertical = 6.dp)) {
                            recent.forEach { r ->
                                Row(Modifier.fillMaxWidth().clickable { send(r) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val cover = all.firstOrNull { it.album.contains(r, true) || it.name.contains(r, true) }
                                    Box(Modifier.size(44.dp).clip(CircleShape).background(colors.iconBg)) { if (cover != null) Thumb(cover.uri, Modifier.fillMaxSize()) }
                                    Text(r, fontSize = 16.sp, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 16.dp))
                                    Icon(MaterialSymbols.Rounded.Close, "Delete", tint = colors.textSecondary,
                                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { RecentSearches.remove(context, r); recent = RecentSearches.load(context) }.padding(6.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                item(key = "count", span = { GridItemSpan(maxLineSpan) }) { CountLine(if (results.isEmpty()) "No results" else "${results.size} results") }
                mediaItems(results, onClick = { nav.open(Page.Viewer(results, it)) })
            }
        }
        // The field floats over the bottom, riding up with the keyboard
        Row(
            Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                .fillMaxWidth().frostedGlass(CircleShape, colors).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("What are you looking for?", color = colors.textTertiary, fontSize = 16.sp)
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp, fontFamily = LocalAppFontFamily.current),
                    cursorBrush = SolidColor(colors.link),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { send(query) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            if (query.isNotEmpty()) Icon(MaterialSymbols.Rounded.Close, "Clear", tint = colors.textSecondary,
                modifier = Modifier.padding(start = 8.dp).size(22.dp).clip(CircleShape).clickable { query = "" })
        }
    }
}

package com.elvan.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvan.gallery.data.Media
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.elvan.navil.Dimens
import com.elvan.navil.ElvanTintedIcon
import com.elvan.navil.LocalAppFontFamily
import com.elvan.navil.LocalPortraitPageBottom
import com.elvan.navil.MaterialSymbols
import com.elvan.navil.frostedGlass
import com.elvan.navil.rememberShellColors

/**
 * The Menu: a card that rises over the page from the navbar (not a page of its own), with the shortcut
 * pages as round chips and the rest as pills. Elvan Navil's frosted surface and tinted icons.
 */
@Composable
fun MenuCard(visible: Boolean, onDismiss: () -> Unit, onPick: (Page?) -> Unit) {
    val colors = rememberShellColors()
    val bottom = (LocalPortraitPageBottom.current ?: 24.dp)
    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(MutableInteractionSource(), null, onClick = onDismiss))
    }
    // Samsung's Menu: the page dims, the navbar steps aside and a card of eight round buttons rises in its place
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = 8.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(200)) + scaleIn(tween(260), initialScale = 0.9f, transformOrigin = TransformOrigin(0.9f, 1f)),
            exit = fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.92f, transformOrigin = TransformOrigin(0.9f, 1f))
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(if (colors.isDark) Color(0xFF252528) else colors.surface)
                    .clickable(MutableInteractionSource(), null) {}.padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth()) {
                    MenuChip("Videos", Sym.Rounded.Play_circle, Modifier.weight(1f)) { onPick(Page.Shortcut(MediaKind.Videos)) }
                    MenuChip("Favourites", Sym.Rounded.Favorite, Modifier.weight(1f)) { onPick(Page.Shortcut(MediaKind.Favourites)) }
                    MenuChip("Recent", Sym.Rounded.Schedule, Modifier.weight(1f)) { onPick(Page.Shortcut(MediaKind.Recent)) }
                    MenuChip("All albums", Sym.Rounded.Photo_library, Modifier.weight(1f)) { onPick(Page.AllAlbums) }
                }
                Row(Modifier.fillMaxWidth()) {
                    MenuChip("Private safe", Sym.Rounded.Lock, Modifier.weight(1f)) { onPick(Page.PrivateSafe) }
                    if (android.os.Build.VERSION.SDK_INT >= 30) MenuChip("Recycle bin", Sym.Rounded.Delete, Modifier.weight(1f)) { onPick(Page.Trash) }
                    MenuChip("Settings", Sym.Rounded.Settings, Modifier.weight(1f)) { onPick(Page.Settings) }
                    MenuChip("Quotes", Sym.Rounded.Format_quote, Modifier.weight(1f)) { onPick(null) }
                }
            }
        }
    }
}

@Composable
private fun MenuChip(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = rememberShellColors()
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(if (colors.isDark) Color(0xFF47474A) else colors.iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 12.sp, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Grey tiles that breathe while the library loads, so the page never starts black. */
@Composable
fun SkeletonGrid(grid: LazyGridState, columns: Int = 4) {
    val colors = rememberShellColors()
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        0.45f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "skeletonAlpha"
    )
    ElvanGrid(grid, columns = columns, gap = if (columns == 3) 12.dp else 2.dp, sidePadding = if (columns == 3) 14.dp else 2.dp) {
        item(key = "skeleton_heading", span = { GridItemSpan(maxLineSpan) }) {
            // Grid items are stretched to the row: the bar sits inside a full-width box to keep its own size
            Box(Modifier.fillMaxWidth()) { Box(Modifier.padding(start = 10.dp, top = 14.dp, bottom = 8.dp).size(110.dp, 16.dp).clip(CircleShape).background(colors.iconBg.copy(alpha = pulse))) }
        }
        items(List(columns * 8) { it }, key = { "skeleton_$it" }) {
            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(if (columns == 3) 16.dp else 4.dp)).background(colors.surface.copy(alpha = pulse)))
        }
    }
}

/** A story: a day with enough pictures to tell one (made automatically), newest first. */
class Story(val key: String, val title: String, val media: List<Media>)

fun buildStories(pictures: List<Media>): List<Story> =
    groupByDay(pictures).filter { it.media.size >= 6 }.map { Story(it.key, it.label, it.media) }

/** The Stories tab: big cards of the days worth remembering; a card opens that day's pictures. */
@Composable
fun StoriesTab(library: GalleryLibrary, grid: LazyGridState, nav: Navigator) {
    val colors = rememberShellColors()
    val stories = remember(library.all) { buildStories(library.all) }
    if (!library.loaded) { SkeletonGrid(grid, columns = 3); return }
    ElvanGrid(grid, columns = 2, gap = 12.dp, sidePadding = 14.dp) {
        if (stories.isEmpty()) item(key = "no_stories", span = { GridItemSpan(maxLineSpan) }) {
            Text("Stories appear here for days with lots of pictures.", color = colors.textSecondary, modifier = Modifier.padding(24.dp))
        }
        if (stories.isNotEmpty()) item(key = "stories_heading", span = { GridItemSpan(maxLineSpan) }) {
            // Samsung's section heading: bold name with a chevron at the far end
            Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Stories", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Icon(Sym.Rounded.Chevron_right, null, tint = colors.textPrimary, modifier = Modifier.size(24.dp))
            }
        }
        // The newest story is the big card across the top; the rest pair up beneath it
        stories.firstOrNull()?.let { story ->
            item(key = story.key, span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1.41f).clip(RoundedCornerShape(26.dp)).background(colors.surface)
                        .clickable { nav.open(Page.Story(story.title, story.media)) }
                ) {
                    val first = story.media.first()
                    coil3.compose.AsyncImage(
                        model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(if (first.isVideo) thumbModel(first.uri) else first.uri).size(1080).build(),
                        contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                        Text(story.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${story.media.size} items", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        items(stories.drop(1), key = { it.key }) { story ->
            Box(
                Modifier.aspectRatio(0.87f).clip(RoundedCornerShape(22.dp)).background(colors.surface)
                    .clickable { nav.open(Page.Story(story.title, story.media)) }
            ) {
                Thumb(story.media.first().uri, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(story.title, color = Color.White, fontSize = 15.sp)
                    Text("${story.media.size} items", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

private val searchChips = listOf("Videos", "Camera", "Screenshots", "Download", "WhatsApp Images", "Pictures")

/** The Search tab: a search field, quick categories, and every picture whose name or album matches. */
@Composable
fun SearchTab(library: GalleryLibrary, grid: LazyGridState, nav: Navigator) {
    val colors = rememberShellColors()
    var query by rememberSaveable { mutableStateOf("") }
    var chip by rememberSaveable { mutableStateOf<String?>(null) }
    val all = library.all
    val results = remember(all, query, chip) {
        val q = query.trim()
        all.filter { m ->
            (chip == null || (if (chip == "Videos") m.isVideo else m.album.equals(chip, true))) &&
                (q.isEmpty() || m.name.contains(q, true) || m.album.contains(q, true))
        }
    }
    val searching = query.isNotBlank() || chip != null
    ElvanGrid(grid, columns = 4) {
        item(key = "search_field", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(CircleShape).background(colors.surface).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(MaterialSymbols.Rounded.Search, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    if (query.isEmpty()) Text("Search pictures and albums", color = colors.textTertiary, fontSize = 15.sp)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp, fontFamily = LocalAppFontFamily.current),
                        cursorBrush = SolidColor(colors.link), modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) Icon(MaterialSymbols.Rounded.Close, "Clear", tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable { query = "" })
            }
        }
        item(key = "search_chips", span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(Modifier.padding(top = 10.dp, bottom = 6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchChips) { name ->
                    val on = chip == name
                    Text(
                        name, fontSize = 13.sp, color = if (on) colors.background else colors.textPrimary,
                        modifier = Modifier.clip(CircleShape).background(if (on) colors.textPrimary else colors.iconBg)
                            .clickable { chip = if (on) null else name }.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
        if (searching) {
            item(key = "search_count", span = { GridItemSpan(maxLineSpan) }) { CountLine("${results.size} results") }
            mediaItems(results, onClick = { nav.open(Page.Viewer(results, it)) })
        } else item(key = "search_hint", span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(Dimens.SectionSpacing))
        }
    }
}

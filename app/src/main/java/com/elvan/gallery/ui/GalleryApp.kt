package com.elvan.gallery.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.elvan.gallery.data.Media
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.composables.icons.materialsymbols.roundedfilled.*
import com.elvan.navil.ElvanMenuState
import com.elvan.navil.ElvanNavBar
import com.elvan.navil.ElvanPopupMenu
import com.elvan.navil.ElvanPopupMenuItem
import com.elvan.navil.ElvanSearchBar
import com.elvan.navil.ElvanShell
import com.elvan.navil.ElvanTopBarIconButton
import com.elvan.navil.LocalElvanShellController
import com.elvan.navil.MaterialSymbols
import com.elvan.navil.SideRailItem
import com.elvan.navil.rememberShellColors

fun defaultPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

enum class GalleryTab(val title: String, val icon: ImageVector, val activeIcon: ImageVector) {
    Pictures("Pictures", Sym.Rounded.Photo, Sym.RoundedFilled.Photo),
    Albums("Albums", Sym.Rounded.Photo_library, Sym.RoundedFilled.Photo_library),
    Stories("Stories", Sym.Rounded.Auto_stories, Sym.RoundedFilled.Auto_stories),
    Search("Search", Sym.Rounded.Search, Sym.RoundedFilled.Search)
}

/** The pages above the tabs; the newest is on top and Back removes it. */
sealed interface Page {
    data class Viewer(val media: List<Media>, val start: Int) : Page
    data class Album(val folder: String) : Page
    data class Group(val name: String) : Page
    data object AllAlbums : Page
    data object PrivateSafe : Page
    data object Settings : Page
    data class Shortcut(val kind: MediaKind) : Page
    data object Trash : Page
    data class Story(val title: String, val media: List<Media>) : Page
    data class Editor(val uri: String, val video: Boolean) : Page
}

class PageEntry(val page: Page, val id: Long) {
    /** Set by Back: the page plays its closing animation, then leaves the stack. */
    var closing by mutableStateOf(false)
}

class Navigator {
    val stack = mutableStateListOf<PageEntry>()
    private var nextId = 0L
    fun open(page: Page) { stack.add(PageEntry(page, nextId++)) }
    fun back() { stack.lastOrNull { !it.closing }?.closing = true }
    fun remove(entry: PageEntry) { stack.remove(entry) }
    val top: PageEntry? get() = stack.lastOrNull { !it.closing }
}

@Composable
fun GalleryApp(library: GalleryLibrary) {
    val nav = remember { Navigator() }
    Box {
        MainTabs(library, nav)
        // Pages draw over the tabs (the tabs keep their scroll and header state underneath)
        nav.stack.forEach { entry ->
            key(entry.id) {
                if (entry.page is Page.Viewer) PageHost(entry, library, nav)
                else {
                    // Page motion: the new page slides in from the side over a fading scrim, and back out
                    val visible = remember { MutableTransitionState(false) }
                    visible.targetState = !entry.closing
                    if (entry.closing && visible.isIdle && !visible.currentState) LaunchedEffect(Unit) { nav.remove(entry) }
                    AnimatedVisibility(
                        visibleState = visible,
                        enter = slideInHorizontally(tween(380, easing = PageEasing)) { it / 3 } + fadeIn(tween(260)),
                        exit = slideOutHorizontally(tween(320, easing = PageEasing)) { it / 3 } + fadeOut(tween(240))
                    ) { PageHost(entry, library, nav) }
                }
            }
        }
    }
    BackHandler(enabled = nav.top != null) { nav.back() }
}

private val PageEasing = CubicBezierEasing(0.22f, 0.25f, 0f, 1f)

@Composable
private fun PageHost(entry: PageEntry, library: GalleryLibrary, nav: Navigator) {
    when (val page = entry.page) {
        is Page.Viewer -> MediaViewer(page.media, page.start, entry.closing, onBack = nav::back, onClosed = { nav.remove(entry) }, onDeleted = library::reload,
            onEdit = { nav.open(Page.Editor(it.uri, it.isVideo)) })
        is Page.Editor -> if (page.video) VideoEditor(page.uri, onClose = nav::back, onSaved = library::reload)
            else PhotoEditor(page.uri, onClose = nav::back, onSaved = library::reload)
        is Page.Album -> AlbumPage(page.folder, library, nav)
        is Page.Group -> GroupPage(page.name, library, nav)
        Page.AllAlbums -> AllAlbumsPage(library, nav)
        Page.PrivateSafe -> PrivateSafePage(nav)
        Page.Settings -> SettingsPage(library, nav)
        is Page.Shortcut -> MediaListPage(page.kind, library, nav)
        Page.Trash -> TrashPage(library, nav)
        is Page.Story -> MediaGridPage(page.title, page.media, library, nav, "Nothing here")
    }
}

@Composable
private fun MainTabs(library: GalleryLibrary, nav: Navigator) {
    val context = LocalContext.current
    val colors = rememberShellColors()
    val prefs = remember { context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE) }
    var tab by remember { mutableStateOf(GalleryTab.entries.firstOrNull { it.name == prefs.getString("last_tab", null) } ?: GalleryTab.Pictures) }
    val gridStates = remember { GalleryTab.entries.associateWith { LazyGridState() } }
    var menuOpen by remember { mutableStateOf(false) }
    var menuCard by remember { mutableStateOf(false) }
    var quotes by remember { mutableStateOf(false) }
    val albums = library.albums
    val pictureSelection = remember { MediaSelection() }

    val selectingAlbums = tab == GalleryTab.Albums && albums != null && albums.selecting
    val selectingPictures = tab == GalleryTab.Pictures && pictureSelection.active
    val selecting = selectingAlbums || selectingPictures
    val pictures = library.all
    BackHandler(enabled = selecting || menuCard) {
        when {
            menuCard -> menuCard = false
            selectingPictures -> pictureSelection.clear()
            else -> albums?.stopSelecting()
        }
    }
    // The tab's own ⋮ items (Settings and the shortcuts live in the Menu card)
    val tabMenu = buildList {
        fun close() { ElvanMenuState.isMenuOpen = false; menuOpen = false }
        when (tab) {
            GalleryTab.Pictures -> if (pictures.isNotEmpty()) add(ElvanPopupMenuItem("Select", MaterialSymbols.Rounded.CheckCircle, { close(); pictureSelection.active = true }))
            GalleryTab.Albums -> if (albums != null) {
                add(ElvanPopupMenuItem("Select", MaterialSymbols.Rounded.CheckCircle, { close(); albums.startSelecting() }))
                if (albums.onlyEssential) add(ElvanPopupMenuItem("Essential albums", Sym.Rounded.Star, { close(); albums.chooseEssential() }))
                else add(ElvanPopupMenuItem("Sort", MaterialSymbols.Rounded.Sort, { close(); library.showSortSheet = true }))
                add(ElvanPopupMenuItem("All albums", Sym.Rounded.Photo_library, { close(); nav.open(Page.AllAlbums) }))
            }
            else -> {}
        }
    }

    ElvanShell(
        gridState = gridStates.getValue(tab),
        title = tab.title,
        showNavbar = true,
        hasActions = !selecting && tabMenu.isNotEmpty(),
        isSelectionMode = selecting,
        selectedCount = if (selectingPictures) pictureSelection.selected.size else albums?.selectedCount ?: 0,
        isAllSelected = if (selectingPictures) pictureSelection.selected.size == pictures.size else albums?.allSelected == true,
        onSelectAll = {
            if (selectingPictures) pictureSelection.selected = if (pictureSelection.selected.size == pictures.size) emptySet() else pictures.map { it.uri }.toSet()
            else albums?.toggleAll()
        },
        onCancelSelection = { if (selectingPictures) pictureSelection.clear() else albums?.stopSelecting() },
        actions = {
            ElvanTopBarIconButton(onClick = { menuOpen = true }) {
                Icon(MaterialSymbols.Rounded.MoreVert, "More", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
            }
            ElvanPopupMenu(
                expanded = menuOpen,
                onDismissRequest = { ElvanMenuState.isMenuOpen = false; menuOpen = false },
                colors = colors,
                items = tabMenu
            )
        },
        navbar = {
            val controller = LocalElvanShellController.current
            if (selectingPictures) PictureSelectionBar(pictureSelection, library)
            else if (selectingAlbums) AlbumSelectionBar(albums!!)
            else Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // The five buttons: Pictures, Albums, Stories, Search, and Menu (a card, not a page)
                ElvanNavBar(
                    items = GalleryTab.entries.map { SideRailItem(it.icon, it.activeIcon, it.title) } +
                        SideRailItem(Sym.Rounded.Menu, Sym.RoundedFilled.Menu, "Menu"),
                    selectedIndex = if (menuCard) GalleryTab.entries.size else tab.ordinal,
                    onSelected = { i, _ ->
                        val picked = GalleryTab.entries.getOrNull(i)
                        when {
                            picked == null -> menuCard = !menuCard
                            picked == tab -> { menuCard = false; controller.toggleHeader() }
                            else -> {
                                menuCard = false
                                albums?.stopSelecting()
                                pictureSelection.clear()
                                tab = picked
                                prefs.edit().putString("last_tab", picked.name).apply()
                            }
                        }
                    }
                )
            }
        },
        overlay = {
            ScrollToTopButton(gridStates.getValue(tab))
            MenuCard(visible = menuCard, onDismiss = { menuCard = false }) { page ->
                menuCard = false
                when (page) {
                    null -> quotes = true
                    else -> nav.open(page)
                }
            }
        }
    ) {
        val grid = gridStates.getValue(tab)
        when (tab) {
            GalleryTab.Pictures ->
                if (!library.loaded) SkeletonGrid(grid)
                else DatedMediaGrid(pictures, grid, pictureSelection) { list, i -> nav.open(Page.Viewer(list, i)) }
            GalleryTab.Albums -> if (albums == null) SkeletonGrid(grid, columns = 3) else AlbumsTab(library, grid, nav)
            GalleryTab.Stories -> StoriesTab(library, grid, nav)
            GalleryTab.Search -> SearchTab(library, grid, nav)
        }
    }
    if (library.showSortSheet) albums?.let { a -> SortDialog(a.order, { library.showSortSheet = false }) { a.changeOrder(it) } }
    if (quotes) QuoteColorDialog(onDismiss = { quotes = false })
}

fun List<Media>.filterByName(query: String) =
    if (query.isBlank()) this else filter { it.name.contains(query.trim(), ignoreCase = true) }

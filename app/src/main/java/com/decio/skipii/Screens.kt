package com.decio.skipii

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ── Home / Subscriptions / Library ─────────────────────────────────────────────
// Signed in, these show the account's real feeds. Signed out, they explain what signing
// in brings rather than showing sample content.

@Composable
fun HomeScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val signedIn = Account.signedIn
    LaunchedEffect(signedIn) { if (signedIn) lib.home.ensureLoaded() }
    CollapsingHeaderLayout(
        resetKey = scrollToTop to lib.home.generation,
        header = { ScreenHeader(title = "onevoidtube", brandMark = true) { AccountAction(onAccount) } },
    ) { top ->
        if (signedIn) {
            FeedList(lib.home, contentPadding.withTop(top), onOpen, scrollToTop = scrollToTop, indicatorTop = top)
        } else {
            LazyColumn(contentPadding = contentPadding.withTop(top)) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Subscriptions,
                        title = "Your recommendations, minus the ads",
                        body = "Sign in with Google to see your own YouTube home feed, or just search.",
                        action = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PillButton("Sign in with Google", onSignIn)
                                Spacer(Modifier.height(12.dp))
                                TextLink("Search instead", onSearch)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionsScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
    onOpenChannels: () -> Unit = {},
) {
    val openChannel = LocalChannelOpener.current
    val signedIn = Account.signedIn
    LaunchedEffect(signedIn) {
        if (signedIn) {
            lib.subscriptions.ensureLoaded()
            if (lib.channels.isEmpty()) lib.loadChannels()
        }
    }
    CollapsingHeaderLayout(
        resetKey = scrollToTop to lib.subscriptions.generation,
        header = { ScreenHeader(title = "Subscriptions") { AccountAction(onAccount) } },
    ) { top ->
        if (signedIn) {
            FeedList(lib.subscriptions, contentPadding.withTop(top), onOpen, header = {
                // Always present (empty until loaded) so its arrival never shifts the list.
                item(key = "channels") {
                    if (lib.channels.isNotEmpty()) ChannelRail(
                        lib.channels,
                        onOpenChannel = { ch -> openChannel("https://www.youtube.com/channel/${ch.id}") },
                        onSeeAll = onOpenChannels,
                    )
                }
            }, scrollToTop = scrollToTop, indicatorTop = top)
        } else {
            LazyColumn(contentPadding = contentPadding.withTop(top)) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Subscriptions,
                        title = "Your subscriptions",
                        body = "Sign in with Google to see new videos from the channels you follow.",
                        action = { PillButton("Sign in with Google", onSignIn) },
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onOpenPlaylist: (PlaylistRef) -> Unit,
    onPlaylistMenu: (PlaylistRef) -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
    onMessage: (String) -> Unit = {},
    onSeeAllDownloads: () -> Unit = {},
    onSeeAllHistory: () -> Unit = {},
) {
    val signedIn = Account.signedIn
    val scope = rememberCoroutineScope()
    var selectingPlaylists by remember { mutableStateOf(false) }
    var selectedPlaylists by remember { mutableStateOf<Set<String>>(emptySet()) }
    var working by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ownIds = lib.playlists.mapTo(HashSet()) { it.id }
    LaunchedEffect(signedIn) {
        if (signedIn) {
            lib.history.ensureLoaded()
            if (lib.playlists.isEmpty()) lib.loadPlaylists()
        }
    }
    fun exitSelection() {
        selectingPlaylists = false
        selectedPlaylists = emptySet()
    }
    LaunchedEffect(lib.playlists) {
        selectedPlaylists = selectedPlaylists.intersect(ownIds)
        if (selectingPlaylists && ownIds.isEmpty()) selectingPlaylists = false
    }
    CollapsingHeaderLayout(
        resetKey = scrollToTop to lib.history.generation,
        header = {
            Column {
                ScreenHeader(
                    title = "Library",
                    actions = {
                        if (signedIn && lib.playlists.isNotEmpty() && !selectingPlaylists) {
                            CircleAction(
                                androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.solar_check),
                                "Select playlists",
                            ) { selectingPlaylists = true }
                        }
                        AccountAction(onAccount)
                    },
                )
                if (selectingPlaylists) {
                    BulkSelectionBar(
                        count = selectedPlaylists.size,
                        allSelected = ownIds.isNotEmpty() && selectedPlaylists.size == ownIds.size,
                        working = working,
                        onClose = ::exitSelection,
                        onAll = {
                            selectedPlaylists = if (selectedPlaylists.size == ownIds.size) emptySet() else ownIds
                        },
                    ) {
                        BulkAction(
                            ImageVector.vectorResource(R.drawable.solar_trash),
                            "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            enabled = selectedPlaylists.isNotEmpty() && !working,
                        ) { confirmDelete = true }
                    }
                }
            }
        },
    ) { top ->
        PullToRefresh(
            enabled = signedIn,
            refreshing = lib.history.refreshing,
            onRefresh = { lib.history.refresh(); lib.loadPlaylists() },
            topPadding = top,
        ) {
        LazyColumn(state = rememberScrollToTop(scrollToTop), contentPadding = contentPadding.withTop(top)) {
            // Downloads stay available signed out and offline: they live in the app itself.
            if (DownloadStore.entries.isNotEmpty()) {
                item(key = "downloadsTitle") { SectionHeaderWithSeeAll("Downloads", onSeeAllDownloads) }
                item(key = "downloads") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Three is the preview; the fourth tile opens the full downloads page.
                        items(DownloadStore.entries.take(3), key = { "d-" + it.video.id }) { entry ->
                            DownloadCard(entry) { onOpen(entry.video) }
                        }
                        item(key = "downloads-all") {
                            SeeAllCard("All downloads", onSeeAllDownloads)
                        }
                    }
                }
            }
            if (!signedIn) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.VideoLibrary,
                        title = "Your library",
                        body = "Sign in with Google for your history, Watch later, liked videos and playlists.",
                        action = { PillButton("Sign in with Google", onSignIn) },
                    )
                }
                return@LazyColumn
            }
            item(key = "historyTitle") { SectionHeaderWithSeeAll("History", onSeeAllHistory) }
            item(key = "history") {
                val history = lib.history.videos
                when {
                    history.isNotEmpty() -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(history.take(3), key = { "h-" + it.id }) { video -> HistoryCard(video) { onOpen(video) } }
                        item(key = "history-all") { SeeAllCard("Full history", onSeeAllHistory) }
                    }
                    lib.history.loading -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        repeat(3) { VideoRowSkeleton() }
                    }
                    else -> Text(
                        lib.history.error ?: "Nothing watched yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            item(key = "playlistsTitle") { SectionTitle("Playlists") }
            val pinned = listOf(
                PlaylistRef("WL", "Watch later", null, null),
                PlaylistRef("LL", "Liked videos", null, null),
            )
            items((pinned + lib.playlists).chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { pl ->
                        val selectable = selectingPlaylists && pl.id in ownIds
                        PlaylistTile(
                            pl,
                            Modifier.weight(1f),
                            onClick = {
                                if (selectable) {
                                    selectedPlaylists = if (pl.id in selectedPlaylists) selectedPlaylists - pl.id
                                    else selectedPlaylists + pl.id
                                } else onOpenPlaylist(pl)
                            },
                            selected = if (selectingPlaylists && pl.id in ownIds) pl.id in selectedPlaylists else null,
                            // Watch later and Liked are YouTube's own: nothing to rename or delete.
                            onLongClick = if (pl.id == "WL" || pl.id == "LL" || selectingPlaylists) null else { { onPlaylistMenu(pl) } },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        }
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!working) confirmDelete = false },
            title = { Text("Delete ${selectedPlaylists.size} playlist${if (selectedPlaylists.size == 1) "" else "s"}?") },
            text = { Text("This removes them from your YouTube account and can't be undone.") },
            confirmButton = {
                Text(
                    if (working) "Deleting…" else "Delete",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.pressable(enabled = !working) {
                        val ids = selectedPlaylists.toList()
                        if (ids.isEmpty()) return@pressable
                        confirmDelete = false
                        working = true
                        scope.launch {
                            var failed = 0
                            ids.forEach { id -> runCatching { InnerTube.deletePlaylist(id) }.onFailure { failed++ } }
                            working = false
                            lib.loadPlaylists()
                            exitSelection()
                            onMessage(
                                if (failed == 0) "Deleted ${ids.size} playlist${if (ids.size == 1) "" else "s"}"
                                else "Deleted ${ids.size - failed}; $failed failed",
                            )
                        }
                    }.padding(12.dp),
                )
            },
            dismissButton = {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.pressable(enabled = !working) { confirmDelete = false }.padding(12.dp),
                )
            },
        )
    }
}

/** A section title with the primary "See more" link that opens its dedicated page. */
@Composable
private fun SectionHeaderWithSeeAll(title: String, onSeeAll: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontFamily = RoundedHeavy,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 21.sp,
            letterSpacing = (-0.6).sp,
        )
        Text(
            "See more",
            color = c.primary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            modifier = Modifier.pressable(pressedScale = 0.94f, onClick = onSeeAll).padding(6.dp),
        )
    }
}

/** The rail's extra tile: the same footprint as a video card, opening the full list. */
@Composable
private fun SeeAllCard(label: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.width(168.dp).pressable(pressedScale = 0.97f, onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ImageVector.vectorResource(R.drawable.solar_playlist),
                null,
                tint = c.onPrimaryContainer,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            color = c.primary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HistoryCard(video: VideoItem, onClick: () -> Unit) {
    val menu = LocalVideoMenu.current
    Column(Modifier.width(168.dp).prefetchOnPress(video.id).pressable(pressedScale = 0.97f, onLongClick = { menu(video) }, onClick = onClick)) {
        Box {
            Thumbnail(video.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 20)
            DurationPill(video.durationSec, Modifier.align(Alignment.BottomEnd).padding(6.dp), small = true)
        }
        Spacer(Modifier.height(7.dp))
        Text(video.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun PlaylistTile(
    playlist: PlaylistRef,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean? = null,
) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    val (icon, tone) = when (playlist.id) {
        "WL" -> ImageVector.vectorResource(R.drawable.solar_bookmark) to c.primaryContainer
        "LL" -> ImageVector.vectorResource(R.drawable.solar_heart) to c.tertiaryContainer
        else -> ImageVector.vectorResource(R.drawable.solar_playlist) to c.secondaryContainer
    }
    Box(
        modifier = modifier
            .height(184.dp)
            .pressable(pressedScale = 0.97f, onLongClick = onLongClick, onClick = onClick)
            .clip(shape)
            .then(if (selected == true) Modifier.border(2.dp, c.primary, shape) else Modifier)
            .background(c.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (playlist.thumbnail != null) {
                Thumbnail(playlist.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 12)
            } else {
                Box(Modifier.size(52.dp).clip(CookieShape).background(tone), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = contentColorFor(tone))
                }
            }
            Column {
                Text(playlist.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                playlist.subtitle?.let { Text(it, color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
        if (selected != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(28.dp)
                    .clip(CircleShape)
                    .background(if (selected) c.primary else c.surface.copy(alpha = 0.85f))
                    .border(1.5.dp, if (selected) c.primary else c.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(ImageVector.vectorResource(R.drawable.solar_check), "Selected", tint = c.onPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A playlist's videos (Watch later, Liked, or the user's own), with bulk selection. */
@Composable
fun PlaylistScreen(
    playlist: PlaylistRef,
    feed: FeedState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onMessage: (String) -> Unit = {},
    onSetStart: () -> Unit = {},
    libraries: List<PlaylistRef> = emptyList(),
) {
    LaunchedEffect(playlist.id) { feed.ensureLoaded() }
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var selecting by remember(playlist.id) { mutableStateOf(false) }
    var selected by remember(playlist.id) { mutableStateOf<Set<String>>(emptySet()) }
    var working by remember(playlist.id) { mutableStateOf(false) }
    var moveOpen by remember(playlist.id) { mutableStateOf(false) }
    var downloadOpen by remember(playlist.id) { mutableStateOf(false) }
    val exclude: (VideoItem) -> Boolean = { LibraryLive.isRemoved(playlist.id, it.id) }
    val visible = feed.videos.filterNot {
        exclude(it) || ContentFilter.hideVideo(it) || HiddenVideos.isHidden(it.id)
    }
    val visibleIds = visible.mapTo(HashSet()) { it.id }
    LaunchedEffect(visibleIds) {
        selected = selected.intersect(visibleIds)
        if (selecting && visibleIds.isEmpty()) selecting = false
    }
    fun exitSelection() {
        selecting = false
        selected = emptySet()
    }
    suspend fun removeFromSource(video: VideoItem) = runCatching {
        if (playlist.id == "LL") InnerTube.setLike(video.id, LikeStatus.None)
        else InnerTube.setInPlaylist(playlist.id, video.id, false)
    }

    fun removeSelected() {
        val chosen = visible.filter { it.id in selected }
        if (chosen.isEmpty() || working) return
        working = true
        scope.launch {
            var failed = 0
            chosen.forEach { video ->
                removeFromSource(video)
                    .onSuccess { LibraryLive.removed(playlist.id, video.id) }
                    .onFailure { failed++ }
            }
            working = false
            feed.refresh()
            exitSelection()
            onMessage(
                if (failed == 0) "Removed ${chosen.size} video${if (chosen.size == 1) "" else "s"}"
                else "Removed ${chosen.size - failed}; $failed failed",
            )
        }
    }

    fun moveSelected(target: PlaylistRef) {
        val chosen = visible.filter { it.id in selected }
        if (chosen.isEmpty() || working) return
        working = true
        scope.launch {
            var failed = 0
            chosen.forEach { video ->
                runCatching { InnerTube.setInPlaylist(target.id, video.id, true) }
                    .onSuccess {
                        removeFromSource(video)
                            .onSuccess { LibraryLive.removed(playlist.id, video.id) }
                            .onFailure { failed++ }
                    }
                    .onFailure { failed++ }
            }
            working = false
            feed.refresh()
            exitSelection()
            onMessage(
                if (failed == 0) "Moved ${chosen.size} to ${target.title}"
                else "Moved ${chosen.size - failed}; $failed failed",
            )
        }
    }

    fun downloadSelected(height: Int) {
        val chosen = visible.filter { it.id in selected }
        if (chosen.isEmpty() || working) return
        chosen.forEach { DownloadStore.download(it, height) }
        downloadOpen = false
        exitSelection()
        onMessage("Downloading ${chosen.size} video${if (chosen.size == 1) "" else "s"}")
    }

    Box(Modifier.fillMaxSize().background(c.surface)) {
    CollapsingHeaderLayout(
        resetKey = feed.generation,
        header = {
            Column {
                if (selecting) {
                    Spacer(Modifier.statusBarsPadding().height(4.dp))
                    BulkSelectionBar(
                        count = selected.size,
                        allSelected = visibleIds.isNotEmpty() && selected.size == visibleIds.size,
                        working = working,
                        onClose = ::exitSelection,
                        onAll = { selected = if (selected.size == visibleIds.size) emptySet() else visibleIds },
                    ) {
                        BulkAction(
                            ImageVector.vectorResource(R.drawable.solar_trash),
                            "Remove",
                            tint = c.error,
                            enabled = selected.isNotEmpty() && !working,
                            onClick = ::removeSelected,
                        )
                        BulkAction(
                            ImageVector.vectorResource(R.drawable.solar_playlist),
                            "Move",
                            enabled = selected.isNotEmpty() && !working,
                        ) { moveOpen = true }
                        BulkAction(
                            ImageVector.vectorResource(R.drawable.solar_download),
                            "Download",
                            enabled = selected.isNotEmpty() && !working,
                        ) {
                            if (DownloadSettings.askEachTime) downloadOpen = true
                            else downloadSelected(DownloadSettings.qualityHeight)
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            playlist.title,
                            modifier = Modifier.weight(1f),
                            fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.8).sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        CircleAction(ImageVector.vectorResource(R.drawable.solar_home), "Set as start screen", onSetStart)
                        if (visible.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            CircleAction(
                                ImageVector.vectorResource(R.drawable.solar_check),
                                "Select videos",
                            ) { selecting = true }
                        }
                    }
                }
            }
        },
    ) { top ->
        if (visible.isEmpty() && !feed.loading && feed.error == null && !feed.hasMore) {
            Box(Modifier.padding(top = top)) { EmptyState(ImageVector.vectorResource(R.drawable.solar_playlist), "Nothing here yet", "Videos you add to this playlist will show up here.") }
        } else {
            FeedList(
                feed, contentPadding.withTop(top), onOpen,
                indicatorTop = top,
                exclude = exclude,
                selectionMode = selecting,
                selectedIds = selected,
                onToggleSelect = { video ->
                    selected = if (video.id in selected) selected - video.id else selected + video.id
                },
            )
        }
    }
    }
    if (moveOpen) {
        MoveVideosSheet(
            libraries = libraries,
            sourceId = playlist.id,
            count = selected.size,
            onDismiss = { moveOpen = false },
            onPick = { target -> moveOpen = false; moveSelected(target) },
        )
    }
    if (downloadOpen) {
        BulkDownloadSheet(
            count = selected.size,
            onDismiss = { downloadOpen = false },
            onPick = ::downloadSelected,
        )
    }
}


@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        modifier = Modifier.pressable(onClick = onClick).padding(8.dp),
    )
}

// ── Search ─────────────────────────────────────────────────────────────────────

/** Lives above the tab switcher so results survive switching tabs. */
@Stable
class SearchState(private val scope: CoroutineScope) {
    var query by mutableStateOf("")
    var submitted by mutableStateOf("")
        private set
    var results by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var generation by mutableStateOf(0)
        private set
    private var job: Job? = null

    /** The query bar changed. Emptying it keeps the previous results on screen. */
    fun onQueryChange(value: String) {
        query = value
    }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) { clearResults(); return }
        submitted = q
        loading = true
        error = null
        job?.cancel()
        job = scope.launch {
            runCatching { YouTube.search(q) }
                .onSuccess { results = it; generation++ }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    /**
     * The Clear button: blank the field, drop the results and show the idle search page.
     * A search already in flight is cancelled so its late answer cannot reappear.
     */
    fun clear() {
        query = ""
        clearResults()
    }

    private fun clearResults() {
        job?.cancel()
        submitted = ""
        results = emptyList()
        loading = false
        error = null
        generation++
    }
}

@Composable
fun SearchScreen(state: SearchState, contentPadding: PaddingValues, onOpen: (VideoItem) -> Unit, scrollToTop: Int = 0) {
    val c = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.submitted) {
        if (state.submitted.isBlank()) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    // Search and Enter both dismiss the keyboard — every field, every time. A hide issued
    // while the key event is still being dispatched can be undone by the IME, so the
    // window-level hide is posted to run after it.
    fun submit() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        view.postDelayed({
            view.context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
        }, 120)
        state.submit()
    }
    // The search bar rides with the header: both slide away together while scrolling results.
    CollapsingHeaderLayout(
        resetKey = scrollToTop to state.generation,
        header = { Column {
        ScreenHeader(title = "Search")
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(60.dp)
                .clip(CircleShape)
                .background(c.surfaceContainerHigh)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.solar_magnifer), null, tint = c.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (state.query.isEmpty()) {
                    Text("Search YouTube", color = c.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = { state.onQueryChange(it) },
                    singleLine = true,
                    textStyle = TextStyle(color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }, onDone = { submit() }),
                    // Hardware Enter doesn't go through the IME action; treat it the same.
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onPreviewKeyEvent { e ->
                        if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                            if (e.type == KeyEventType.KeyDown) submit()
                            true
                        } else false
                    },
                )
            }
            if (state.query.isNotEmpty()) {
                Box(
                    modifier = Modifier.size(44.dp).pressable { state.clear() },
                    contentAlignment = Alignment.Center,
                ) { Icon(androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.solar_close), "Clear", tint = c.onSurfaceVariant) }
            }
        }
        // A settled answer from a previous search stays on screen while the next one loads.
        // Only the thin bar under the field moves; the results never blink away.
        if (state.loading) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                color = c.primary,
                trackColor = c.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        } },
    ) { top ->
        Box(Modifier.fillMaxSize()) {
        when {
            // Empty field: the idle page is just the mark, no ads copy, no history.
            state.submitted.isEmpty() -> SearchIdle(Modifier.padding(top = top))
            state.results.isEmpty() && state.loading -> LazyColumn(contentPadding = contentPadding.withTop(top)) {
                items(4) { VideoCardSkeleton() }
            }
            state.results.isEmpty() && state.error != null -> EmptyState(
                icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.solar_offline),
                title = "Search didn't load",
                body = state.error.orEmpty(),
                action = { PillButton("Try again", { state.submit() }) },
            )
            state.results.isEmpty() -> EmptyState(
                icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.solar_history),
                title = "Nothing found",
                body = "No videos matched “${state.submitted}”.",
            )
            else -> {
                val list = rememberScrollToTop(scrollToTop)
                LaunchedEffect(state.generation) { list.scrollToItem(0) }
                LazyColumn(state = list, contentPadding = contentPadding.withTop(top)) {
                items(state.results.filterNot { ContentFilter.hideVideo(it) || HiddenVideos.isHidden(it.id) }, key = { it.id }) { video ->
                    VideoCard(video, onClick = { onOpen(video) })
                }
            }
            }
        }
        }
    }
}

/** The untouched Search tab: only the mark, centred, exactly as empty searches should read. */
@Composable
private fun SearchIdle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.solar_explore),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            modifier = Modifier.size(104.dp),
        )
    }
}

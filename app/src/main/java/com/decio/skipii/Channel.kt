package com.decio.skipii

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

/** Opens a channel page from anywhere (player channel row, card avatars). Provided by the shell. */
val LocalChannelOpener = staticCompositionLocalOf<(String) -> Unit> { {} }

enum class ChannelTab(val label: String) {
    Videos("Videos"),
    Live("Live"),
    Playlists("Playlists"),
}

enum class ChannelSort(val label: String) {
    Newest("Newest"),
    Popular("Popular"),
    Oldest("Oldest"),
}

@Stable
class ChannelState(val url: String, private val scope: CoroutineScope) {
    var details by mutableStateOf<ChannelDetails?>(null)
        private set
    var tab by mutableStateOf(ChannelTab.Videos)
        private set
    var sort by mutableStateOf(ChannelSort.Newest)
        private set

    // Videos
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var next: String? = null
    val hasMore get() = next != null
    private var sortTokens: Map<String, String> = emptyMap()

    // Live
    var liveVideos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var liveLoading by mutableStateOf(false)
        private set
    var liveError by mutableStateOf<String?>(null)
        private set
    private var liveNext: String? = null
    val liveHasMore get() = liveNext != null
    var liveLoaded by mutableStateOf(false)
        private set

    // Playlists
    var playlists by mutableStateOf<List<PlaylistRef>>(emptyList())
        private set
    var playlistsLoading by mutableStateOf(false)
        private set
    var playlistsError by mutableStateOf<String?>(null)
        private set
    private var playlistsNext: String? = null
    val playlistsHasMore get() = playlistsNext != null
    var playlistsLoaded by mutableStateOf(false)
        private set

    var subscribed by mutableStateOf<Boolean?>(null)
        private set

    init { load() }

    fun load() {
        loading = true
        error = null
        scope.launch {
            runCatching { YouTube.channel(url) }
                .onSuccess { d ->
                    details = d
                    if (Account.signedIn) launch { subscribed = runCatching { InnerTube.channelSubscribed(d.id) }.getOrNull() }
                    loadVideosFor(d, null)
                    // Preload live + playlists so empty tabs can be hidden.
                    launch {
                        liveLoaded = true
                        liveLoading = true
                        runCatching { YouTube.channelLive(d, null) }
                            .onSuccess { liveVideos = it.videos; liveNext = it.next }
                        liveLoading = false
                    }
                    launch {
                        playlistsLoaded = true
                        playlistsLoading = true
                        runCatching { YouTube.channelPlaylists(d, null) }
                            .onSuccess { playlists = it.playlists; playlistsNext = it.next }
                        playlistsLoading = false
                    }
                }
                .onFailure { error = YouTube.friendlyError(it); loading = false }
        }
    }

    private fun loadVideosFor(d: ChannelDetails, sortToken: String?) {
        loading = true
        scope.launch {
            runCatching { YouTube.channelVideos(d, null, sortToken) }
                .onSuccess { res ->
                    videos = res.page.videos
                    next = res.page.next
                    if (res.sortTokens.isNotEmpty()) sortTokens = res.sortTokens
                }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun selectTab(newTab: ChannelTab) {
        if (tab == newTab) return
        tab = newTab
        val d = details ?: return
        when (newTab) {
            ChannelTab.Videos -> Unit
            ChannelTab.Live -> if (!liveLoaded) {
                liveLoaded = true
                liveLoading = true
                liveError = null
                scope.launch {
                    runCatching { YouTube.channelLive(d, null) }
                        .onSuccess { liveVideos = it.videos; liveNext = it.next }
                        .onFailure { liveError = YouTube.friendlyError(it) }
                    liveLoading = false
                }
            }
            ChannelTab.Playlists -> if (!playlistsLoaded) {
                playlistsLoaded = true
                playlistsLoading = true
                playlistsError = null
                scope.launch {
                    runCatching { YouTube.channelPlaylists(d, null) }
                        .onSuccess { playlists = it.playlists; playlistsNext = it.next }
                        .onFailure { playlistsError = YouTube.friendlyError(it) }
                    playlistsLoading = false
                }
            }
        }
    }

    fun selectSort(newSort: ChannelSort) {
        if (sort == newSort) return
        sort = newSort
        val d = details ?: return
        val token = when (newSort) {
            ChannelSort.Newest -> sortTokens.entries.firstOrNull { it.key.contains("latest", true) || it.key.contains("newest", true) }?.value
            ChannelSort.Popular -> sortTokens.entries.firstOrNull { it.key.contains("popular", true) }?.value
            ChannelSort.Oldest -> sortTokens.entries.firstOrNull { it.key.contains("oldest", true) }?.value
        }
        loadVideosFor(d, token)
    }

    fun loadMore() {
        val d = details ?: return
        when (tab) {
            ChannelTab.Videos -> {
                val page = next ?: return
                if (loading) return
                loading = true
                scope.launch {
                    runCatching { YouTube.channelVideos(d, page) }
                        .onSuccess { res ->
                            val seen = videos.mapTo(HashSet()) { it.id }
                            videos = videos + res.page.videos.filter { seen.add(it.id) }
                            next = res.page.next
                        }
                    loading = false
                }
            }
            ChannelTab.Live -> {
                val page = liveNext ?: return
                if (liveLoading) return
                liveLoading = true
                scope.launch {
                    runCatching { YouTube.channelLive(d, page) }
                        .onSuccess { res ->
                            val seen = liveVideos.mapTo(HashSet()) { it.id }
                            liveVideos = liveVideos + res.videos.filter { seen.add(it.id) }
                            liveNext = res.next
                        }
                    liveLoading = false
                }
            }
            ChannelTab.Playlists -> {
                val page = playlistsNext ?: return
                if (playlistsLoading) return
                playlistsLoading = true
                scope.launch {
                    runCatching { YouTube.channelPlaylists(d, page) }
                        .onSuccess { res ->
                            val seen = playlists.mapTo(HashSet()) { it.id }
                            playlists = playlists + res.playlists.filter { seen.add(it.id) }
                            playlistsNext = res.next
                        }
                    playlistsLoading = false
                }
            }
        }
    }

    fun toggleSubscribe(onError: (String) -> Unit) {
        val id = details?.id ?: return
        val before = subscribed ?: return
        subscribed = !before
        scope.launch {
            runCatching { InnerTube.setSubscribed(id, !before) }
                .onFailure { subscribed = before; onError("Couldn't update your subscription.") }
        }
    }
}

@Composable
fun ChannelScreen(
    state: ChannelState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onOpenPlaylist: (PlaylistRef) -> Unit = {},
    onMessage: (String) -> Unit,
    onSetStart: () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    // Only offer sections that actually have something: no empty Live/Playlists chips.
    val visibleTabs = buildList {
        add(ChannelTab.Videos)
        if (state.liveVideos.isNotEmpty()) add(ChannelTab.Live)
        if (state.playlists.isNotEmpty()) add(ChannelTab.Playlists)
    }
    LaunchedEffect(visibleTabs) {
        if (state.tab !in visibleTabs) state.selectTab(ChannelTab.Videos)
    }
    var descriptionOpen by remember(state.url) { mutableStateOf(false) }
    val nearEnd by remember {
        derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
    }
    LaunchedEffect(nearEnd, state.videos.size, state.liveVideos.size, state.playlists.size) {
        if (nearEnd) state.loadMore()
    }
    Box(Modifier.fillMaxSize().background(c.surface)) {
    CollapsingHeaderLayout(
        header = {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Text(
                state.details?.name.orEmpty(),
                fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.6).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).pointerInput(Unit) {
                    detectTapGestures(onLongPress = { clipboard.setText(AnnotatedString(state.details?.name.orEmpty())) })
                },
            )
            Spacer(Modifier.width(6.dp))
            CircleAction(
                ImageVector.vectorResource(R.drawable.solar_home),
                "Set as start screen",
                onSetStart,
            )
        }
        },
    ) { top ->
        val d = state.details
        when {
            d == null && state.error != null -> Box(Modifier.padding(top = top)) { EmptyState(
                Icons.Rounded.CloudOff, "Couldn't load this channel", state.error.orEmpty(),
                action = { PillButton("Try again", state::load) },
            ) }
            d == null -> Column(Modifier.padding(top = top + 20.dp)) {
                VideoCardSkeleton()
                VideoCardSkeleton()
            }
            else -> LazyColumn(state = list, contentPadding = contentPadding.withTop(top)) {
                item(key = "header") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        if (d.banner != null) {
                            Thumbnail(d.banner, Modifier.fillMaxWidth().aspectRatio(5.6f), radius = 22)
                            Spacer(Modifier.height(16.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelAvatar(d.avatar, d.name, size = 76)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (d.subscribers > 0) Text("${formatCount(d.subscribers)} subscribers", color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        state.subscribed?.let { subscribed ->
                            Spacer(Modifier.height(14.dp))
                            ChannelSubscribeButton(subscribed) { state.toggleSubscribe(onMessage) }
                        }
                        if (d.description.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Column(Modifier.pressable(pressedScale = 0.99f) { descriptionOpen = !descriptionOpen }) {
                                Text(
                                    d.description,
                                    color = c.onSurfaceVariant, fontSize = 13.5.sp, lineHeight = 19.sp,
                                    maxLines = if (descriptionOpen) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // Only offer the toggle when the text is actually clipped.
                                Text(
                                    if (descriptionOpen) "less" else "more",
                                    color = c.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                item(key = "tabs") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        visibleTabs.forEach { tab ->
                            val active = state.tab == tab
                            Box(
                                Modifier
                                    .pressable { state.selectTab(tab) }
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (active) c.primary else c.surfaceContainerHigh)
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    tab.label,
                                    color = if (active) c.onPrimary else c.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                )
                            }
                        }
                    }
                }
                if (state.tab == ChannelTab.Videos) {
                    item(key = "sortRow") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ChannelSort.entries.forEach { sort ->
                                val active = state.sort == sort
                                Box(
                                    Modifier
                                        .pressable(enabled = !state.loading) { state.selectSort(sort) }
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) c.secondaryContainer else c.surfaceContainer)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        sort.label,
                                        color = if (active) c.onSecondaryContainer else c.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                    if (state.loading && state.videos.isEmpty()) {
                        items(3) { VideoCardSkeleton() }
                    } else {
                        items(state.videos.filterNot { ContentFilter.hideVideo(it) || HiddenVideos.isHidden(it.id) }, key = { it.id }) { video -> VideoCard(video, onClick = { onOpen(video) }) }
                        if (state.loading && state.hasMore) item { VideoCardSkeleton() }
                    }
                } else if (state.tab == ChannelTab.Live) {
                    if (state.liveLoading && state.liveVideos.isEmpty()) {
                        items(3) { VideoCardSkeleton() }
                    } else if (state.liveVideos.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.Rounded.LiveTv,
                                "No live streams",
                                "This channel has no active or scheduled live streams.",
                            )
                        }
                    } else {
                        items(state.liveVideos.filterNot { ContentFilter.hideVideo(it) }, key = { it.id }) { video -> VideoCard(video, onClick = { onOpen(video) }) }
                        if (state.liveLoading && state.liveHasMore) item { VideoCardSkeleton() }
                    }
                } else if (state.tab == ChannelTab.Playlists) {
                    if (state.playlistsLoading && state.playlists.isEmpty()) {
                        items(3) { VideoRowSkeleton(Modifier.padding(horizontal = 16.dp)) }
                    } else if (state.playlists.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.Rounded.VideoLibrary,
                                "No playlists",
                                "This channel has no public playlists.",
                            )
                        }
                    } else {
                        items(state.playlists.chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { pl ->
                                    PlaylistTile(
                                        pl,
                                        Modifier.weight(1f),
                                        onClick = { onOpenPlaylist(pl) },
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        if (state.playlistsLoading && state.playlistsHasMore) item { VideoRowSkeleton(Modifier.padding(horizontal = 16.dp)) }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ChannelSubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .squishOn(subscribed)
            .fillMaxWidth()
            .height(46.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(if (subscribed) c.secondaryContainer else c.onSurface),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (subscribed) {
            FlourishIcon(Icons.Rounded.NotificationsActive, null, active = true, kind = Flourish.Ring, tint = c.onSecondaryContainer, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(if (subscribed) "Subscribed" else "Subscribe", color = if (subscribed) c.onSecondaryContainer else c.surface, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

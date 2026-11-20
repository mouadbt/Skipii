package com.decio.skipii

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlin.math.pow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.decio.skipii.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // Incremented whenever a download notification is tapped. The composable watches it so
    // the request also works when the activity is already open (launchMode="singleTask").
    private var downloadsRequest by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The media notification (and its lock-screen controls) needs this on Android 13+.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        consumeDownloadsRequest(intent)
        setContent { SkipiiTheme { SkipiiApp(downloadsRequest) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDownloadsRequest(intent)
    }

    private fun consumeDownloadsRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(DownloadNotifications.EXTRA_OPEN_DOWNLOADS, false) != true) return
        // Never let a configuration change repeat the navigation.
        intent.removeExtra(DownloadNotifications.EXTRA_OPEN_DOWNLOADS)
        downloadsRequest++
    }
}

enum class Tab(val label: String, @androidx.annotation.DrawableRes val iconRes: Int) {
    Home("Home", R.drawable.solar_home),
    Subscriptions("Subscriptions", R.drawable.solar_clapperboardplay),
    Search("Search", R.drawable.solar_magnifer),
    Library("Library", R.drawable.solar_videolibrary),
}

// Castii's floating stack: a 56 dp nav pill capped at 328 dp, the mini-player 8 dp above
// it capped at 368 dp, both centered.
// 62 dp (Derek, 2026-10-05): between Castii's 56 and Ledgii's 78. Tabs keep 50 dp targets.
private val NavHeight = 62.dp
private val NavWidth = 328.dp
// The mini-player is the same width as the nav pill and sits flush on top of it: one merged
// floating dock. Its 31 dp radius matches the nav pill's rounded ends.
private val MiniWidth = NavWidth
private val MiniGap = 0.dp
private const val SELECTED_TAB_WEIGHT = 2.2f

@Composable
fun SkipiiApp(downloadsRequest: Int = 0) {
    val c = MaterialTheme.colorScheme
    val navColor = NavBarSettings.color(c.surfaceContainerHighest, c.primary, c.background)
    val player = rememberPlayerState()
    val scope = rememberCoroutineScope()
    val search = remember { SearchState(scope) }
    val lib = remember { LibraryData(scope) }
    var signingIn by remember { mutableStateOf(false) }
    var accountSheet by remember { mutableStateOf(false) }
    // The user's chosen launch point can be a tab, a channel or a playlist.
    val startupChannel = remember { NavigationSettings.startupChannelUrl() }
    val startupPlaylist = remember { NavigationSettings.startupPlaylist() }
    var openPlaylist by remember { mutableStateOf(startupPlaylist) }
    var openDownloads by remember { mutableStateOf(false) }
    var openHistory by remember { mutableStateOf(false) }
    var openChannels by remember { mutableStateOf(false) }
    var openChannel by remember { mutableStateOf(startupChannel) }
    // Kept after closing so the page can slide out with its content.
    var channelState by remember { mutableStateOf(startupChannel?.let { ChannelState(it, scope) }) }
    val playlistFeed = remember(openPlaylist?.id) { openPlaylist?.let { lib.playlistFeed(it.id) } }
    var tab by rememberSaveable { mutableStateOf(NavigationSettings.startupTab()) }
    val visibleTabs = Tab.entries.filterNot { it in NavigationSettings.hiddenTabs }.ifEmpty { listOf(Tab.Home) }
    val fallbackTab = visibleTabs.firstOrNull() ?: Tab.Home
    LaunchedEffect(visibleTabs, tab) { if (tab !in visibleTabs) tab = fallbackTab }
    // Bumped when the already-selected tab is tapped; that tab's list scrolls to the top.
    val scrollToTop = remember { mutableStateMapOf<Tab, Int>() }
    val reduced = rememberReducedMotion()
    val morph = remember { PlayerMorph(scope) { expanded -> player.expanded = expanded } }

    // A tapped download notification lands on the Downloads page, not just the app.
    LaunchedEffect(downloadsRequest) {
        if (downloadsRequest <= 0) return@LaunchedEffect
        player.expanded = false
        accountSheet = false
        signingIn = false
        openPlaylist = null
        openChannels = false
        openChannel = null
        openHistory = false
        openDownloads = true
    }

    // The screen stays on while a video plays (full player, mini-player or fullscreen);
    // paused, the normal timeout applies again. Only affects the visible window.
    val rootView = LocalView.current
    DisposableEffect(player.isPlaying) {
        rootView.keepScreenOn = player.isPlaying
        onDispose { rootView.keepScreenOn = false }
    }

    // Opening the player drops any text focus (the search box behind it), so the keyboard
    // can't come back over the video.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(player.expanded) {
        if (player.expanded) { focusManager.clearFocus(force = true); keyboard?.hide() }
    }

    // Off screen (app in background, screen locked): audio only.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> player.setBackground(true)
                androidx.lifecycle.Lifecycle.Event.ON_START -> player.setBackground(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // As in Castii: at least 8 dp off the bottom, and extra clearance above a three-button bar.
    val stackBottom = navBottom.coerceAtLeast(8.dp) +
        if (WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding() > 0.dp) 10.dp else 0.dp
    var optionsFor by remember { mutableStateOf<VideoItem?>(null) }
    var playlistOptions by remember { mutableStateOf<PlaylistRef?>(null) }
    val hasMini = player.current != null
    // Lists scroll fully clear of the floating nav (and mini-player when present).
    val contentPadding = PaddingValues(
        bottom = stackBottom + NavHeight + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 16.dp,
    )

    // Fullscreen is whenever the open player is shown in landscape (rotation or button).
    val activity = LocalContext.current as Activity
    val orientation = rememberOrientationController(activity)
    val gestures = rememberScreenGestures(activity)
    val playerOpen = player.current != null && player.expanded
    // Audio only has no picture to go fullscreen with: the open player stays portrait.
    orientation.Bind(playerOpen && !AudioOnly.enabled)
    val pip = remember(activity) { PipController(activity as ComponentActivity) }
    // Closing the picture-in-picture window pauses (expanding it just comes back).
    pip.Bind(onClosed = { player.controller?.pause() })
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // The PiP window can be any shape: hold whatever was showing when it opened.
    val fullscreenBeforePip = remember { mutableStateOf(false) }
    // Upright fullscreen ("fullscreen vertical"): the phone stays portrait while the watch
    // page turns fullscreen — its details were swiped down (see UprightFullscreen).
    val uprightFullscreen = playerOpen && player.uprightFullscreen && !landscape
    val showFullscreen = if (pip.active) fullscreenBeforePip.value else playerOpen && landscape
    SideEffect { if (!pip.active) fullscreenBeforePip.value = showFullscreen }
    LaunchedEffect(showFullscreen) { player.fullscreen = showFullscreen }
    SystemBarsHidden(showFullscreen || uprightFullscreen)
    // Leaving fullscreen from the portrait side too: the flag falls with the bars.
    fun exitFullscreen() {
        player.uprightFullscreen = false
        orientation.exitFullscreen()
    }
    fun toggleFullscreen() = if (showFullscreen || uprightFullscreen) exitFullscreen() else orientation.enterFullscreen()
    // The window's brightness follows the system again once the player is gone.
    LaunchedEffect(hasMini) { if (!hasMini) gestures.resetBrightness() }

    BackHandler(enabled = player.expanded) { player.expanded = false }
    BackHandler(enabled = !player.expanded && tab != fallbackTab && openPlaylist == null && !openChannels) { tab = fallbackTab }
    BackHandler(enabled = !player.expanded && openPlaylist != null) { openPlaylist = null }
    BackHandler(enabled = !player.expanded && openDownloads) { openDownloads = false }
    BackHandler(enabled = !player.expanded && openHistory) { openHistory = false }
    BackHandler(enabled = signingIn) { signingIn = false }
    BackHandler(enabled = !player.expanded && openChannels && openChannel == null) { openChannels = false }
    // A playlist opened from a channel sits on top of it; Back closes the playlist first.
    BackHandler(enabled = !player.expanded && openChannel != null && openPlaylist == null) { openChannel = null }
    // Registered next-to-last: in upright fullscreen Back returns to the watch page…
    BackHandler(enabled = uprightFullscreen) { exitFullscreen() }
    // …and in landscape fullscreen to the portrait player first (registered last, so it wins).
    BackHandler(enabled = showFullscreen) { exitFullscreen() }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    // Taps, back and opening a video set `expanded`; this turns that into the morph.
    // Gesture releases settle themselves and are left alone (see PlayerMorph.animateTo).
    LaunchedEffect(player.expanded, player.current?.id) {
        if (player.current == null) morph.progress.snapTo(0f) else morph.animateTo(player.expanded, reduced)
    }
    val showPlayer by remember {
        // Held until the morph has completely finished, so the video changes hands once.
        derivedStateOf { player.current != null && (player.expanded || morph.dragging || morph.progress.isRunning || morph.progress.value > 0f) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.surface)) {
        // Both ends of the morph, from the same constants the mini-player and player use.
        val stageAspect = player.stageAspect
        val geometry = remember(constraints.maxWidth, constraints.maxHeight, stackBottom, statusTop, density, stageAspect) {
            with(density) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val miniBottom = h - (stackBottom + NavHeight + MiniGap).toPx()
                val miniW = minOf(w - 32.dp.toPx(), MiniWidth.toPx())
                val miniLeft = (w - miniW) / 2f
                val miniCard = Rect(miniLeft, miniBottom - MiniHeight.toPx(), miniLeft + miniW, miniBottom)
                val thumb = Rect(
                    Offset(miniCard.left + MiniThumbStart.toPx(), miniCard.top + (MiniHeight - MiniThumbHeight).toPx() / 2f),
                    Size(MiniThumbWidth.toPx(), MiniThumbHeight.toPx()),
                )
                val side = StageSideGap.toPx()
                val stageTop = (statusTop + StageTopGap).toPx()
                val stage = Rect(side, stageTop, w - side, stageTop + (w - 2 * side) / stageAspect)
                MorphGeometry(
                    screen = Size(w, h),
                    miniCard = miniCard,
                    miniThumb = thumb,
                    stage = stage,
                    miniRadius = MiniRadius.toPx(),
                    thumbRadius = MiniThumbRadius.toPx(),
                    stageRadius = StageRadius.toPx(),
                )
            }
        }
        SideEffect { morph.geometry = geometry }

        // One video surface that moves between player, fullscreen and mini-player (never two
        // at once), so the picture is never rebuilt and never blinks back to the thumbnail.
        val sharedSurface = remember(player) {
            androidx.compose.runtime.movableContentOf { m: Modifier -> VideoSurface(player.controller, m) }
        }
        CompositionLocalProvider(
            // In picture-in-picture only the PiP view below shows the video.
            LocalVideoSurface provides if (pip.active) NoVideoSurface else sharedSurface,
            LocalPictureInPicture provides pip,
            LocalVideoMenu provides { optionsFor = it },
            LocalChannelOpener provides { url ->
                // From the player: minimize it so the channel page is visible.
                player.expanded = false
                if (channelState?.url != url) channelState = ChannelState(url, scope)
                openChannel = url
            },
        ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                if (reduced) fadeIn(snap()) togetherWith fadeOut(snap())
                else (fadeIn(tween(Motion.PageEnterMs, easing = Motion.Emphasized)) +
                    slideInVertically(tween(Motion.PageRiseMs, easing = Motion.Emphasized)) { (it / 35).coerceIn(8, 24) }) togetherWith
                    fadeOut(tween(Motion.PageExitMs))
            },
            label = "tabs",
        ) { t ->
            when (t) {
                Tab.Home -> HomeScreen(
                    lib, contentPadding, onOpen = player::open, onSearch = { tab = Tab.Search },
                    onSignIn = { signingIn = true }, onAccount = { accountSheet = true },
                    scrollToTop = scrollToTop[Tab.Home] ?: 0,
                )
                Tab.Subscriptions -> SubscriptionsScreen(
                    lib, contentPadding, onOpen = player::open,
                    onSignIn = { signingIn = true }, onAccount = { accountSheet = true },
                    scrollToTop = scrollToTop[Tab.Subscriptions] ?: 0,
                    onOpenChannels = { openChannels = true },
                )
                Tab.Search -> SearchScreen(search, contentPadding, onOpen = player::open, scrollToTop = scrollToTop[Tab.Search] ?: 0)
                Tab.Library -> LibraryScreen(
                    lib, contentPadding, onOpen = player::open, onOpenPlaylist = { openPlaylist = it },
                    onPlaylistMenu = { playlistOptions = it },
                    onSignIn = { signingIn = true }, onAccount = { accountSheet = true },
                    scrollToTop = scrollToTop[Tab.Library] ?: 0,
                    onMessage = { player.actionMessage = it },
                    onSeeAllDownloads = { openDownloads = true },
                    onSeeAllHistory = { openHistory = true },
                )
            }
        }

        // A playlist opens as a page over the tabs (slides in from the right).
        AnimatedVisibility(
            visible = openPlaylist != null,
            // Above the channel page it may have been opened from.
            modifier = Modifier.zIndex(2f),
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            val pl = openPlaylist
            val feed = playlistFeed
            if (pl != null && feed != null) {
                PlaylistScreen(
                    pl, feed, contentPadding,
                    onBack = { openPlaylist = null },
                    onOpen = player::open,
                    onMessage = { player.actionMessage = it },
                    onSetStart = {
                        NavigationSettings.setPlaylist(pl)
                        player.actionMessage = "Start screen set to ${pl.title}"
                    },
                    libraries = lib.playlists,
                )
            }
        }

        // Downloads and History open as their own pages from the Library previews.
        AnimatedVisibility(
            visible = openDownloads,
            modifier = Modifier.zIndex(3f),
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            DownloadsScreen(
                contentPadding = contentPadding,
                onBack = { openDownloads = false },
                onOpen = player::open,
            )
        }
        AnimatedVisibility(
            visible = openHistory,
            modifier = Modifier.zIndex(3f),
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            HistoryScreen(
                lib = lib,
                contentPadding = contentPadding,
                onBack = { openHistory = false },
                onOpen = player::open,
            )
        }

        // The full subscriptions list behind the channel rail's See all.
        AnimatedVisibility(
            visible = openChannels,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            ChannelsScreen(
                lib, contentPadding,
                onBack = { openChannels = false },
                onOpenChannel = { ch ->
                    val url = "https://www.youtube.com/channel/${ch.id}"
                    if (channelState?.url != url) channelState = ChannelState(url, scope)
                    openChannel = url
                },
                onMessage = { player.actionMessage = it },
            )
        }

        // A channel opens the same way, over everything below the mini-player.
        AnimatedVisibility(
            visible = openChannel != null,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            channelState?.let { ch ->
                ChannelScreen(
                    ch,
                    contentPadding,
                    onBack = { openChannel = null },
                    onOpen = player::open,
                    onOpenPlaylist = { openPlaylist = it },
                    onMessage = { player.actionMessage = it },
                    onSetStart = {
                        NavigationSettings.setChannel(ch.url, ch.details?.name)
                        player.actionMessage = "Start screen set to ${ch.details?.name ?: "this channel"}"
                    },
                )
            }
        }

        // Ledgii's bottom vignette: the page color fades in over the last 130 dp (plus the
        // system bar), so content dissolves behind the mini-player and tab bar instead of
        // running hard into them. Drawn only — it never intercepts touches.
        val pageColor = c.surface
        val vignette = remember(pageColor) { vignetteBrush(pageColor, opaqueAtTop = false) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .zIndex(3.4f)
                .fillMaxWidth()
                .height(navBottom + 130.dp)
                .drawBehind { drawRect(vignette) },
        )
        // Swiping home from here never scrolls the page (the tab bar above still wins its taps).
        BottomGestureGuard(Modifier.align(Alignment.BottomCenter).zIndex(3.4f))

        if (hasMini) {
            // Swipe up expands (the morph); swipe down follows the finger and, past a
            // threshold or with a flick, slides the mini-player away and stops playback.
            val dismiss = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(player.current?.id) { dismiss.snapTo(0f) }
            val dismissDistance = with(density) { 56.dp.toPx() }
            MiniPlayer(
                player,
                onExpand = { player.expanded = true },
                showVideo = !showPlayer && !showFullscreen,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(3.5f)
                    .padding(start = 16.dp, end = 16.dp, bottom = stackBottom + NavHeight + MiniGap)
                    .widthIn(max = MiniWidth)
                    // Fades as the video grows out of it; slides and fades as it's dismissed.
                    .graphicsLayer {
                        translationY = dismiss.value
                        // Hidden for the whole morph: the shrinking page draws the mini-player
                        // itself (it stays touchable underneath, so taps still work mid-collapse).
                        alpha = (if (showPlayer) 0f else 1f) * (1f - (dismiss.value / (dismissDistance * 1.6f)).coerceIn(0f, 1f))
                    }
                    .draggable(
                        state = rememberDraggableState { delta ->
                            if (morph.progress.value <= 0f && (delta > 0f || dismiss.value > 0f)) {
                                scope.launch { dismiss.snapTo((dismiss.value + delta).coerceAtLeast(0f)) }
                            } else {
                                morph.dragBy(delta)
                            }
                        },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (dismiss.value > 0f) {
                                if (dismiss.value > dismissDistance || velocity > 800f) {
                                    scope.launch {
                                        if (!reduced) dismiss.animateTo(dismissDistance * 2.2f, tween(200, easing = Motion.Emphasized))
                                        player.close()
                                    }
                                } else {
                                    scope.launch { dismiss.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 600f)) }
                                }
                            } else {
                                morph.settle(velocity, reduced)
                            }
                        },
                    ),
            )
        }

        val p = morph.progress.value
        val g = morph.geometry
        // During initial lift-off (p in 0..0.18), the dock separates smoothly with a water-drop meniscus
        val mergedFraction = if (hasMini && !showFullscreen) (1f - ramp(p, 0f, 0.18f)) else 0f

        // Liquid water-drop bridge connecting the rising mini-card and the nav dock
        if (hasMini && !showFullscreen && p in 0.001f..0.18f && g != null) {
            val t = (p / 0.18f).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxSize()) {
                val full = Rect(Offset.Zero, size)
                val cardBottom = lerpF(g.miniCard.bottom, full.bottom, ramp(p, 0.3f))
                val navTop = g.miniCard.bottom
                val gap = navTop - cardBottom
                if (gap > 0f) {
                    val w = g.miniCard.width
                    val centerX = (g.miniCard.left + g.miniCard.right) / 2f
                    val topW = w * (1f - t * 0.35f)
                    val botW = w * (1f - t * 0.35f)
                    val waistW = w * (1f - t).pow(1.8f)
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(centerX - topW / 2f, cardBottom)
                        cubicTo(
                            centerX - waistW / 2f, cardBottom + gap * 0.35f,
                            centerX - waistW / 2f, navTop - gap * 0.35f,
                            centerX - botW / 2f, navTop,
                        )
                        lineTo(centerX + botW / 2f, navTop)
                        cubicTo(
                            centerX + waistW / 2f, navTop - gap * 0.35f,
                            centerX + waistW / 2f, cardBottom + gap * 0.35f,
                            centerX + topW / 2f, cardBottom,
                        )
                        close()
                    }
                    drawPath(path, color = navColor)
                }
            }
        }

        NavPill(
            selected = tab,
            tabs = visibleTabs,
            color = navColor,
            mergedFraction = mergedFraction,
            onSelect = { selected ->
                when {
                    // Re-tapping the current tab backs out of the page on top, else scrolls up.
                    selected == tab && openDownloads -> openDownloads = false
                    selected == tab && openHistory -> openHistory = false
                    selected == tab && openPlaylist != null -> openPlaylist = null
                    selected == tab && openChannel != null -> openChannel = null
                    selected == tab && openChannels -> openChannels = false
                    selected == tab -> scrollToTop[selected] = (scrollToTop[selected] ?: 0) + 1
                    else -> {
                        openDownloads = false
                        openHistory = false
                        openPlaylist = null
                        openChannel = null
                        openChannels = false
                        tab = selected
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(3.5f).padding(start = 16.dp, end = 16.dp, bottom = stackBottom),
        )

        // The player morphs out of (and back into) the mini-player; see PlayerMorph.
        // Above the Downloads/History/Playlist pages: opening a video from one of them must
        // show the full player, not play behind the page in the mini-player.
        if (showFullscreen || showPlayer) {
            Box(Modifier.fillMaxSize().zIndex(4f)) {
                if (showFullscreen) {
                    FullscreenPlayer(player, gestures, onExit = { exitFullscreen() })
                } else {
                    PlayerScreen(
                        player, morph, gestures,
                        onOpen = player::open,
                        onCollapse = { player.expanded = false },
                        onFullscreen = { toggleFullscreen() },
                    )
                }
            }
        }

        if (accountSheet) {
            AccountSheet(
                onSignIn = { signingIn = true },
                onSignedOut = { lib.reset(); player.refreshAccountState() },
                onDismiss = { accountSheet = false },
            )
        }
        if (signingIn) {
            Box(Modifier.fillMaxSize().zIndex(5f)) {
                SignInScreen(
                    onDone = { signingIn = false; lib.reset(); player.refreshAccountState() },
                    onCancel = { signingIn = false },
                )
            }
        }

        when (player.sheet) {
            PlayerSheet.Quality -> QualitySheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Chapters -> ChaptersSheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Comments -> Unit // a panel inside the player (PlayerScreen)
            PlayerSheet.Queue -> QueueSheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Library -> LibrarySheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Speed -> SpeedSheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Download -> DownloadSheet(player, onDismiss = { player.sheet = null })
            null -> Unit
        }
        optionsFor?.let { video -> VideoOptionsSheet(video, player, onDismiss = { optionsFor = null }) }
        playlistOptions?.let { playlist ->
            PlaylistOptionsSheet(
                playlist, player,
                onChanged = { lib.loadPlaylists() },
                onDismiss = { playlistOptions = null },
            )
        }
        // Confirmations ("Added to queue") when the full player isn't showing its own.
        if (!showPlayer) {
            QueueMessage(
                player,
                Modifier.align(Alignment.BottomCenter).zIndex(3.6f)
                    .padding(bottom = stackBottom + NavHeight + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 12.dp),
            )
        }
        // Picture-in-picture: the window is just the video, over everything (kept composed
        // underneath, so expanding returns to exactly where things were).
        if (pip.active) {
            Box(Modifier.fillMaxSize().zIndex(6f).background(Color.Black)) {
                sharedSurface(Modifier.fillMaxSize().aspectFit(player.videoAspect))
            }
        }
        }
    }
}

@Composable
private fun QueueMessage(player: PlayerState, modifier: Modifier) {
    val message = player.actionMessage ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2_000)
        player.actionMessage = null
    }
    val c = MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(50)).background(c.inverseSurface).padding(horizontal = 18.dp, vertical = 11.dp)) {
        Text(message, color = c.inverseOnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/**
 * Castii's floating nav pill: tonal surfaceVariant with a wide, very soft shadow. The
 * active tab grows (weight 2.2) and reveals its label on a primaryContainer pill; the
 * others show their icon only. No ripple, no outline.
 */
@Composable
private fun NavPill(selected: Tab, tabs: List<Tab>, color: Color, mergedFraction: Float, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val topRadius = with(LocalDensity.current) { lerpF((NavHeight / 2).toPx(), 0f, mergedFraction.coerceIn(0f, 1f)).toDp() }
    val shape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = NavHeight / 2,
        bottomEnd = NavHeight / 2,
    )
    Surface(
        modifier = modifier
            .widthIn(max = NavWidth)
            .height(NavHeight)
            .fillMaxWidth()
            .shadow(20.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.12f)),
        shape = shape,
        color = color,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { t ->
                val active = t == selected
                val spec = if (reduced) snap<Float>() else tween(260, easing = Motion.Emphasized)
                val labelColor by animateColorAsState(
                    if (active) c.onPrimary else c.onSurfaceVariant,
                    if (reduced) snap() else tween(220, easing = Motion.Emphasized), label = "tabContent",
                )
                // Fade the pill by alpha of the same color (never toward Color.Transparent).
                val pillColor by animateColorAsState(
                    // Full primary (not the pale container tone) so the selected tab stands
                    // out clearly against the bar in light and dark (Derek, 2026-10-05).
                    if (active) c.primary else c.primary.copy(alpha = 0f),
                    if (reduced) snap() else tween(220, easing = Motion.Emphasized), label = "tabPill",
                )
                val weight by animateFloatAsState(if (active) SELECTED_TAB_WEIGHT else 1f, spec, label = "tabWeight")
                val interaction = remember(t) { MutableInteractionSource() }
                Surface(
                    modifier = Modifier
                        .weight(weight.coerceAtLeast(0.75f))
                        .clickable(interactionSource = interaction, indication = null) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onSelect(t)
                        },
                    shape = RoundedCornerShape(25.dp),
                    color = pillColor,
                    contentColor = labelColor,
                ) {
                    Row(Modifier.height(50.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(t.iconRes), contentDescription = t.label, modifier = Modifier.size(22.dp))
                        AnimatedVisibility(
                            visible = active,
                            enter = if (reduced) fadeIn(snap()) else fadeIn(tween(160, easing = Motion.Emphasized)) + expandHorizontally(tween(200, easing = Motion.Emphasized)),
                            exit = if (reduced) fadeOut(snap()) else fadeOut(tween(90)) + shrinkHorizontally(tween(160, easing = Motion.Emphasized)),
                        ) {
                            Text(t.label, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

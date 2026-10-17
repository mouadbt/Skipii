package com.decio.skipii

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW

// ── Geometry shared with the morph (MainActivity builds MorphGeometry from these) ──

// The video runs edge to edge as a plain rectangle, right under the status bar (Derek).
internal val StageTopGap = 0.dp
internal val StageSideGap = 0.dp
internal val StageRadius = 0.dp
internal val MiniHeight = 70.dp
internal val MiniRadius = 24.dp
internal val MiniThumbStart = 7.dp
internal val MiniThumbWidth = 92.dp
internal val MiniThumbHeight = 52.dp
internal val MiniThumbRadius = 17.dp
/**
 * The portrait video takes the video's own shape (Derek, 2026-10-10: no black bars), from
 * 4:5 for tall videos to 2.4:1 for wide ones; anything beyond fits inside with bars.
 */
internal const val StageMinAspect = 0.8f
internal const val StageMaxAspect = 2.4f
/** Audio only: the stage is a short strip holding just the controls (~164 dp on a Pixel). */
internal const val AudioStageAspect = 2.5f
/** Morph progress after which the details and tint start fading in (tuning dial). */
private const val DetailsFadeStart = 0.55f

// ── Full player (portrait) ─────────────────────────────────────────────────────

@Composable
fun PlayerScreen(
    state: PlayerState,
    morph: PlayerMorph,
    gestures: ScreenGestures,
    onOpen: (VideoItem) -> Unit,
    onCollapse: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    // Collapsing (or collapsed): the player gives up the screen immediately, so the
    // mini-player underneath answers taps and swipes even while the animation finishes.
    val interactive = state.expanded || morph.dragging
    // Page ↔ upright fullscreen ("fullscreen vertical"): pulling the details down.
    val full = remember(item.id) { UprightFullscreen(scope, morph, open = state.uprightFullscreen) { state.uprightFullscreen = it } }
    // Mid-transition the controls stay out of it: they would ride the travel and snap
    // from the portrait style to the fullscreen one when the swap lands.
    val fullBusy = full.dragging || full.progress.isRunning
    val showDetails by remember { derivedStateOf { state.expanded || morph.dragging || morph.progress.value > DetailsFadeStart } }
    val details = state.details
    // The bars hide in fullscreen; the page itself must not shift when they do (the upright
    // transition draws the video's travel itself), so the inset is taken once, while opening.
    val statusTopNow = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val top = remember { statusTopNow }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var descriptionOpen by remember(item.id) { mutableStateOf(false) }
    // The video's own shape, eased in as the real one arrives: the stage lays itself out from
    // it, and so does the room the details leave for it.
    val aspect by animateFloatAsState(
        state.stageAspect,
        if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 380f),
        label = "stageAspect",
    )
    // How far the video's top edge must travel to sit in the middle of the screen, and how
    // far a pull must go to commit — both driven into the transition for its math.
    val config = LocalConfiguration.current
    val stageHeight = (config.screenWidthDp.dp - StageSideGap * 2) / aspect
    val travel = ((config.screenHeightDp.dp - stageHeight) / 2 - (top + StageTopGap)).coerceAtLeast(0.dp)
    // Landed in upright fullscreen: the stage owns the whole screen (its controls reach the
    // edges), so it has to sit over the details. On the way in and out it slides under them,
    // so they stay visible while they drop away.
    val uprightLanded by remember { derivedStateOf { state.uprightFullscreen && full.progress.value >= 1f } }
    val density = LocalDensity.current
    SideEffect {
        full.travel = with(density) { travel.toPx() }
        full.pull = with(density) { FullscreenPull.toPx() }
    }
    LaunchedEffect(state.uprightFullscreen, item.id) { full.animateTo(state.uprightFullscreen, reduced) }

    val pageColor = c.surface
    val miniColor = c.surfaceContainerHighest
    Box(
        Modifier
            .fillMaxSize()
            // The morph's moving frame: the mini-player card growing to the full screen.
            .graphicsLayer {
                val g = morph.geometry ?: return@graphicsLayer
                val p = morph.progress.value
                // The page itself becomes the mini-player card. Its bottom edge holds at the
                // card's bottom until well into the open (and from there on the way down), so
                // it never sweeps over the tab bar.
                val full = Rect(Offset.Zero, size)
                val frame = Rect(
                    lerpF(g.miniCard.left, full.left, p), lerpF(g.miniCard.top, full.top, p),
                    lerpF(g.miniCard.right, full.right, p), lerpF(g.miniCard.bottom, full.bottom, ramp(p, 0.3f)),
                )
                shape = RoundRectClip(frame, lerpF(g.miniRadius, 0f, p.coerceIn(0f, 1f)))
                clip = true
            }
            .drawBehind {
                val p = morph.progress.value
                // Opaque throughout: at the small end it IS the mini-player card (same color).
                // Upright fullscreen fades the page to black behind the centred video, and the
                // exit's pull takes it past 1 (the pull gives way, but this must still clamp).
                drawRect(lerpColor(lerpColor(miniColor, pageColor, ramp(p, 0f, 0.6f)), Color.Black, ramp(full.progress.value, 0f, 1f)))
            }
            // Open: a hit target over the whole screen, so taps never reach the tabs beneath.
            // Collapsing: no hit target at all, so touches fall through to the mini-player.
            .then(if (interactive) Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } } else Modifier)
            // Upright fullscreen's gestures: the sides set the brightness and volume, the
            // middle drags the transition back to the page.
            .then(
                if (state.uprightFullscreen) Modifier.screenGestures(
                    gestures,
                    onExitDrag = { delta -> full.dragBack(delta) },
                    onExitDrop = { velocity -> full.settle(-velocity, reduced) },
                ) else Modifier,
            ),
    ) {
        // Soft tint behind the video, fading into the page (and away in fullscreen). Behind the
        // video too: the stage is a sibling (-1) now, not a child of the list below it.
        Box(
            Modifier.fillMaxWidth().height(520.dp)
                .zIndex(-2f)
                .graphicsLayer { alpha = ramp(morph.progress.value, DetailsFadeStart) * (1f - ramp(full.progress.value, 0f, 0.6f)) }
                .background(Brush.verticalGradient(listOf(c.primaryContainer.copy(alpha = 0.75f), c.surface))),
        )
        ActionMessage(state, Modifier.align(Alignment.BottomCenter).zIndex(2f).padding(bottom = bottom + 24.dp))
        Column(Modifier.fillMaxSize()) {
            // The video's room, kept whether or not the stage is in it: upright fullscreen hands
            // the stage the whole screen, and the details must not move when it does.
            Spacer(Modifier.height(top + StageTopGap + stageHeight))
            // While collapsing the details are invisible anyway; leaving them out keeps them
            // from catching touches meant for the mini-player.
            if (!showDetails) Spacer(Modifier.weight(1f)) else
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(full.nestedScroll)
                    // Fullscreen has dropped these rows off the page; eating their touches on
                    // the way in (Initial) keeps taps and drags from landing on rows that are
                    // no longer there. The root's own gestures see the event first.
                    .then(
                        if (state.uprightFullscreen) Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        } else Modifier,
                    )
                    .graphicsLayer {
                        // The details ride along under the video's bottom edge (so they never
                        // overlap it mid-morph), fade in for the last stretch, and drop away
                        // with the video as the page turns fullscreen.
                        val p = morph.progress.value
                        val f = full.progress.value
                        alpha = ramp(p, DetailsFadeStart) * (1f - ramp(f, 0f, 0.5f))
                        val g = morph.geometry
                        translationY = (if (g != null) lerpF(g.miniThumb.bottom - g.stage.bottom, 0f, p) else 0f) + full.travel * f
                    },
                contentPadding = PaddingValues(bottom = bottom + 32.dp),
            ) {
                item(key = "info") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            item.title,
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            lineHeight = 26.sp,
                            letterSpacing = (-0.6).sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        val stats = listOfNotNull(
                            item.views.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
                            item.uploaded?.ifBlank { null },
                            details?.likes?.takeIf { it > 0 }?.let { "${formatCount(it)} likes" },
                        ).joinToString(" · ")
                        val hasDescription = !details?.description.isNullOrBlank()
                        Row(
                            modifier = if (hasDescription) Modifier.pressable(pressedScale = 0.98f) { descriptionOpen = !descriptionOpen } else Modifier,
                        ) {
                            Text(stats, color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (hasDescription) {
                                Text(
                                    if (descriptionOpen) "  less" else "  …more",
                                    color = c.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                        if (descriptionOpen && details != null) {
                            Text(
                                details.description,
                                modifier = Modifier.padding(top = 10.dp),
                                color = c.onSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val openChannel = LocalChannelOpener.current
                            val channelUrl = details?.item?.channelUrl ?: item.channelUrl
                            // Avatar + name open the channel (the Subscribe button beside them doesn't).
                            Row(
                                Modifier.weight(1f).pressable(enabled = channelUrl != null, pressedScale = 0.97f) { channelUrl?.let(openChannel) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                            ChannelAvatar(details?.item?.avatar ?: item.avatar, item.channel, size = 46)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.channel, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                details?.subscribers?.takeIf { it > 0 }?.let {
                                    Text("${formatCount(it)} subscribers", color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            }
                            state.accountState?.subscribed?.let { subscribed ->
                                SubscribeButton(subscribed, onClick = state::toggleSubscribe)
                            }
                        }
                        PlayerActions(state)
                        SpeedPanel(state)
                        QueueCard(state, onOpen = { state.sheet = PlayerSheet.Queue })
                        CommentsCard(state.comments, onOpen = { state.sheet = PlayerSheet.Comments })
                    }
                }
                val related = details?.related.orEmpty()
                if (related.isNotEmpty()) {
                    item(key = "moreTitle") { SectionTitle("More videos") }
                    items(related, key = { "rel-" + it.id }) { video ->
                        Box(Modifier.padding(horizontal = 20.dp)) { VideoRow(video, onClick = { onOpen(video) }) }
                    }
                } else if (state.loading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                            CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
        // The pinned video: over the page it is the video's own box under the status bar; in
        // upright fullscreen it takes the whole screen, so its controls reach the screen's
        // edges and a tap anywhere on the black answers. It stays under everything on the way
        // in and out ([uprightLanded]) so the details remain visible as they drop away.
        VideoStage(
            state = state,
            morph = morph,
            full = full,
            placement = if (state.uprightFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier.align(Alignment.TopStart).padding(top = top + StageTopGap)
                    .fillMaxWidth().aspectRatio(aspect)
            }.zIndex(if (uprightLanded) 1f else -1f),
            onCollapse = onCollapse,
            onFullscreen = onFullscreen,
            interactive = interactive && !fullBusy,
        )
        // Swiping home from the bottom edge never scrolls the details (open player only: while
        // collapsing, touches must still fall through to the mini-player). Fullscreen leaves it
        // out: its scrubber sits down there now and has to answer.
        if (interactive && !state.uprightFullscreen) BottomGestureGuard(Modifier.align(Alignment.BottomCenter))
        // The mini-player's title and buttons travel with the shrinking video and fade in as
        // it lands, so the page turns into the mini-player rather than flying to one.
        MorphingMiniDetails(state, morph)
        // The brightness/volume pill for fullscreen's side swipes (invisible until one starts).
        ScreenGestureHud(gestures)

        // Comments open as a panel whose top sits just under the video.
        if (state.sheet == PlayerSheet.Comments) {
            val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
            val videoBottom = top + StageTopGap + (screenW - StageSideGap * 2) / state.stageAspect
            CommentsPanel(state.comments, partialTop = videoBottom + 10.dp, onClose = { state.sheet = null })
        }
    }
}

/**
 * The pinned video in portrait: the morph's container-transform layer around [VideoContent].
 * Swiping it down minimizes (the morph). Swiping it up while open goes fullscreen, as in the
 * YouTube app (Derek, 2026-10-10): the video swells under the finger, and past
 * [FullscreenPull] or with a flick it turns to landscape; short of that it eases back.
 * While upright fullscreen holds it ([UprightFullscreen]) the stage takes the whole screen so
 * its controls reach the screen's edges, with the video resting in the middle of the black and
 * the side swipes around it running the brightness and volume instead.
 */
@Composable
private fun VideoStage(state: PlayerState, morph: PlayerMorph, full: UprightFullscreen, placement: Modifier, onCollapse: () -> Unit, onFullscreen: () -> Unit, interactive: Boolean) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val upright = state.uprightFullscreen
    val pullUp = remember { Animatable(0f) }
    val pullDistance = with(LocalDensity.current) { FullscreenPull.toPx() }
    // Each gesture's first move decides: up on the open player pulls toward fullscreen,
    // anything else drives the morph.
    var decided by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }
    val stage = placement
        .padding(horizontal = StageSideGap)
        .graphicsLayer {
            // Rubber band: up to 6% bigger, ever more slowly, growing down from the top edge.
            val k = 1f + 0.06f * (1f - exp(-pullUp.value / (pullDistance * 1.5f)))
            scaleX = k
            scaleY = k
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
        // Container transform: at progress 0 the video's centred 16:9 part sits exactly
        // on the mini-player's thumbnail (scale, position and corner radius); at 1 the
        // whole stage is home.
        .graphicsLayer {
            val g = morph.geometry
            val p = morph.progress.value
            // Upright fullscreen: the stage holds the whole screen and stays put; the picture
            // inside it carries the travel, from under the status bar to the middle.
            val travel = if (upright) 0f else full.travel * full.progress.value
            if (g != null) {
                val f = g.videoFrame(size, p)
                scaleX = f.scale
                scaleY = f.scale
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = f.offset.x
                translationY = f.offset.y + travel
                // Radius in layer space: divided by the scale so the small version
                // shows the thumbnail's 17 dp corners, not a shrunken 28 dp.
                shape = RoundRectClip(f.window, lerpF(g.thumbRadius / f.startScale, g.stageRadius, p.coerceIn(0f, 1f)))
            } else {
                translationY = travel
                shape = RoundedCornerShape(StageRadius)
            }
            clip = true
            // Only while it floats mid-morph; landed, it is a flat edge-to-edge rectangle.
            shadowElevation = 14.dp.toPx() * (1f - p).coerceIn(0f, 1f)
            ambientShadowColor = Color.Black.copy(alpha = 0.25f)
            spotShadowColor = Color.Black.copy(alpha = 0.35f)
        }
        .draggable(
            state = rememberDraggableState { delta ->
                if (!decided) {
                    decided = true
                    pulling = delta < 0f && state.expanded && !state.fullscreen && !AudioOnly.enabled && morph.progress.value >= 0.999f
                }
                if (pulling) scope.launch(start = CoroutineStart.UNDISPATCHED) { pullUp.snapTo((pullUp.value - delta).coerceAtLeast(0f)) }
                else morph.dragBy(delta)
            },
            orientation = Orientation.Vertical,
            // Upright fullscreen drags on the root instead; and while the transition runs
            // (entering or leaving) the stage must not start the morph under the finger.
            enabled = !state.uprightFullscreen && !full.progress.isRunning,
            onDragStopped = { velocity ->
                val wasPulling = pulling
                decided = false
                pulling = false
                if (wasPulling) {
                    if (pullUp.value > pullDistance || velocity < -PlayerMorph.FLING_PX_PER_S) onFullscreen()
                    if (reduced) pullUp.snapTo(0f) else pullUp.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                } else {
                    morph.settle(velocity, reduced)
                }
            },
        )
    // Controls appear only once the video has nearly landed, so they never ride the morph.
    val controlsAlpha = { ramp(morph.progress.value, 0.7f) }
    if (AudioOnly.enabled) {
        AudioOnlyStage(
            state = state,
            interactive = interactive,
            onInactiveTap = { state.expanded = true },
            progress = { morph.progress.value },
            controlsAlpha = controlsAlpha,
            modifier = stage,
        )
    } else {
        VideoContent(
            state = state,
            // Fullscreen-style controls while upright fullscreen holds the stage.
            fullscreen = upright,
            // Their edges are the screen's, not the video's: the picture keeps the video's own
            // box, centred, and rides the travel, while the controls span everything.
            upright = upright,
            uprightShift = { full.travel * (full.progress.value - 1f) },
            interactive = interactive,
            // A tap on the shrinking video brings the player straight back.
            onInactiveTap = { state.expanded = true },
            onCollapse = onCollapse,
            onToggleFullscreen = onFullscreen,
            controlsAlpha = controlsAlpha,
            modifier = stage,
        )
    }
}

/** How far a swipe up on the open video travels before it goes fullscreen on release. */
private val FullscreenPull = 64.dp

/**
 * Page ↔ upright fullscreen ("fullscreen vertical", Derek 2026-10-17): pulling the details
 * down turns the watch page into fullscreen without turning the phone — the video slides to
 * the middle of a black stage while the details drop away; a swipe down the video's middle
 * (or Back, or the exit button) comes back. That swipe rides the finger the whole way: down
 * pulls the video below the middle (the pull gives way the further it goes) and the release
 * takes it up to the page, up unwinds it under the finger. The morph owns mini ↔ page, this
 * owns page ↔ fullscreen, and it borrows the morph's whole feel: the same fling threshold,
 * pull and springs, the same handover of the details list's leftover drag.
 */
@Stable
class UprightFullscreen(
    private val scope: CoroutineScope,
    private val morph: PlayerMorph,
    open: Boolean = false,
    private val onSettled: (open: Boolean) -> Unit,
) {
    /** 0 = the watch page, 1 = fullscreen. Same too-small threshold as the morph. */
    val progress = Animatable(if (open) 1f else 0f, visibilityThreshold = 0.0005f)
    var dragging by mutableStateOf(false)
        private set
    private var startedOpen = false

    /** How far the video's top edge must travel to the middle (px), and the commit pull. */
    var travel by mutableFloatStateOf(1f)
    var pull by mutableFloatStateOf(1f)

    /** Applies a vertical finger delta (down = positive = toward fullscreen). Returns px used. */
    fun dragBy(deltaY: Float): Float = drag(deltaY, over = false)

    /**
     * The exit gesture's drag: the same, except a downward pull carries the video on past the
     * middle under the finger, giving way as it goes, and the release takes it up to the page.
     */
    fun dragBack(deltaY: Float): Float = drag(deltaY, over = true)

    private fun drag(deltaY: Float, over: Boolean): Float {
        if (!dragging) {
            dragging = true
            startedOpen = progress.value >= 0.5f
        }
        val before = progress.value
        val after = if (over && deltaY > 0f) {
            // Past fullscreen the pull gives way, so the video is never dragged off the screen.
            val below = (before - 1f).coerceAtLeast(0f) * travel
            1f + (below + deltaY * exp(-below / (pull * 2f))) / travel
        } else {
            (before + deltaY / travel).coerceIn(0f, 1f)
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(after) }
        return (after - before) * travel
    }

    /** Finger lifted: finish toward fullscreen or the page by distance or flick. */
    fun settle(velocityY: Float, reducedMotion: Boolean) {
        if (!dragging) return
        dragging = false
        val p = progress.value
        val open = when {
            // Leaving: a flick either way commits — a downward pull rides the finger now, so
            // the pull's distance, not its direction, is what decides — and a short drag
            // either way settles back into fullscreen.
            startedOpen -> velocityY in -PlayerMorph.FLING_PX_PER_S..PlayerMorph.FLING_PX_PER_S &&
                abs(1f - p) * travel < pull
            velocityY > PlayerMorph.FLING_PX_PER_S -> true
            velocityY < -PlayerMorph.FLING_PX_PER_S -> false
            // Entering commits past the pull.
            else -> p * travel > pull
        }
        val target = if (open) 1f else 0f
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (reducedMotion) progress.snapTo(target)
            // Leaving never overshoots, and only a flick toward the page carries into it: the
            // pull's own downward momentum would dip the video before lifting it to the top.
            else progress.animateTo(
                target,
                if (open) settleSpec else closeSpec,
                initialVelocity = (if (open) velocityY else velocityY.coerceAtMost(0f)) / travel,
            )
        }
        onSettled(open)
    }

    /** Non-gesture changes: Back, the exit button, a rotation landing back in portrait. */
    suspend fun animateTo(open: Boolean, reducedMotion: Boolean) {
        // A forced change during a drag (Back while the finger is down) abandons the drag,
        // so it can never leave the transition stuck mid-way under `dragging`.
        dragging = false
        val target = if (open) 1f else 0f
        // Already heading there (e.g. a gesture's settle spring): don't restart it.
        if (progress.targetValue == target && (progress.isRunning || progress.value == target)) return
        when {
            reducedMotion -> progress.snapTo(target)
            open -> progress.animateTo(target, tween(Motion.MorphMs, easing = Motion.Morph))
            else -> progress.animateTo(target, tween(Motion.CollapseMs, easing = Motion.Emphasized))
        }
    }

    /**
     * The details list's leftover downward pull (it can't scroll any further) drives this
     * instead of minimizing the player. Mid-pull reversals retreat it before the list scrolls
     * again, and only a landed page can start: mid-open, the pull still belongs to the morph.
     */
    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (dragging && source == NestedScrollSource.UserInput && available.y < 0f) {
                return Offset(0f, dragBy(available.y))
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y > 0f &&
                morph.progress.value >= 0.999f && !AudioOnly.enabled
            ) {
                return Offset(0f, dragBy(available.y))
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!dragging) return Velocity.Zero
            settle(available.y, reducedMotion = false)
            return available
        }
    }

    private companion object {
        /** Entering takes the morph's soft overshoot; leaving eases in flat. */
        val settleSpec = spring(dampingRatio = 0.86f, stiffness = 550f, visibilityThreshold = 0.0005f)
        val closeSpec = spring(dampingRatio = 1f, stiffness = 550f, visibilityThreshold = 0.0005f)
    }
}

// ── Account actions (signed in) ────────────────────────────────────────────────

@Composable
private fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    // Expressive state change: the pill rounds into a softer tonal shape once subscribed,
    // squishes as it does, and the bell rings.
    val radius by animateDpAsState(if (subscribed) 16.dp else 22.dp, if (reduced) tween(0) else spring(dampingRatio = 0.6f, stiffness = 500f), label = "subShape")
    Row(
        modifier = Modifier
            .squishOn(subscribed)
            .height(44.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(radius))
            .background(if (subscribed) c.secondaryContainer else c.onSurface)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(horizontal = if (subscribed) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (subscribed) {
            FlourishIcon(Icons.Rounded.NotificationsActive, null, active = subscribed, kind = Flourish.Ring, tint = c.onSecondaryContainer, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (subscribed) "Subscribed" else "Subscribe",
            color = if (subscribed) c.onSecondaryContainer else c.surface,
            fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
        )
    }
}

/**
 * The action row: like | dislike and Save when signed in, then speed and audio only (always).
 * It runs edge to edge inside so a long like count scrolls it sideways instead of squeezing
 * the buttons, and the like sparks still have room to fly.
 */
@Composable
private fun PlayerActions(state: PlayerState) {
    Row(
        Modifier
            .padding(top = 14.dp)
            .bleed(20.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.accountState != null) AccountActions(state)
        SpeedButton(state)
        AudioOnlyButton(state)
    }
}

/** Lays the content out [x] wider on each side than the parent's padding allows. */
private fun Modifier.bleed(x: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val extra = (x * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Connected like | dislike group and Save (Watch later), all synced to the account; placed in [PlayerActions]' row. */
@Composable
private fun AccountActions(state: PlayerState) {
    val like = state.accountState?.like ?: LikeStatus.None
    var lastLikes by remember { mutableStateOf(state.displayLikes) }
    val likes = state.displayLikes
    val rising = likes >= lastLikes
    SideEffect { lastLikes = likes }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ActionPill(
            icon = if (like == LikeStatus.Like) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
            label = likes.takeIf { it > 0 }?.let { formatCount(it) },
            active = like == LikeStatus.Like,
            flourish = Flourish.Pop,
            labelRollsUp = rising,
            shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            description = "Like",
        ) { state.setLike(LikeStatus.Like) }
        ActionPill(
            icon = if (like == LikeStatus.Dislike) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
            label = null,
            active = like == LikeStatus.Dislike,
            flourish = Flourish.Droop,
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 22.dp, bottomEnd = 22.dp),
            description = "Dislike",
        ) { state.setLike(LikeStatus.Dislike) }
    }
    ActionPill(
        icon = if (state.inWatchLater) Icons.Rounded.WatchLater else Icons.Outlined.WatchLater,
        label = if (state.inWatchLater) "Saved" else "Save",
        active = state.inWatchLater,
        flourish = Flourish.Drop,
        shape = RoundedCornerShape(22.dp),
        description = "Save to Watch later",
    ) { state.toggleWatchLater() }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    active: Boolean,
    flourish: Flourish,
    shape: androidx.compose.ui.graphics.Shape,
    description: String,
    labelRollsUp: Boolean = true,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val bg by androidx.compose.animation.animateColorAsState(if (active) c.primary else c.surfaceContainerHigh, tween(220), label = "pillBg")
    val tint by androidx.compose.animation.animateColorAsState(if (active) c.onPrimary else c.onSurface, tween(220), label = "pillInk")
    Row(
        modifier = Modifier
            .squishOn(active)
            .height(44.dp)
            .pressable(onClick = onClick)
            // background(shape), not clip: the like sparks may fly past the pill's edge.
            .background(bg, shape)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlourishIcon(icon, description, active = active, kind = flourish, tint = tint, size = 20.dp, sparkColor = c.primary)
        if (label != null) {
            Spacer(Modifier.width(7.dp))
            RollingText(label, tint, up = labelRollsUp)
        }
    }
}

/** Brief confirmation or failure note for account actions; clears itself. */
@Composable
private fun ActionMessage(state: PlayerState, modifier: Modifier) {
    val message = state.actionMessage ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2_500)
        state.actionMessage = null
    }
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(CircleShape)
            .background(c.inverseSurface)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) { Text(message, color = c.inverseOnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

// ── Mini player ────────────────────────────────────────────────────────────────

@Composable
fun MiniPlayer(state: PlayerState, onExpand: () -> Unit, modifier: Modifier = Modifier, showVideo: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    val shape = RoundedCornerShape(MiniRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniHeight)
            .pressable(pressedScale = 0.98f, onClick = onExpand)
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .background(c.surfaceContainerHighest)
    ) {
        Row(Modifier.fillMaxSize().padding(start = MiniThumbStart, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // The live video, not a still. While the player is open or morphing, this slot
            // stays EMPTY: the shrinking video is on its way here, and a thumbnail already
            // sitting in the slot read as a second copy it was flying towards.
            Box(Modifier.size(width = MiniThumbWidth, height = MiniThumbHeight).clip(RoundedCornerShape(MiniThumbRadius))) {
                if (showVideo) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                    Thumbnail(item.thumbnail, Modifier.fillMaxSize(), radius = 0)
                    // The player's stage, centre-cropped to the slot (as the morph's small end
                    // shows it), with the video fitted inside: never stretched.
                    // Audio only: there is no picture, the thumbnail stands in.
                    if (!AudioOnly.enabled) {
                        Box(Modifier.fillMaxSize().aspectFit(state.stageAspect, cover = true)) {
                            LocalVideoSurface.current(Modifier.fillMaxSize().aspectFit(state.videoAspect))
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            MiniDetails(state, interactive = true)
        }
    }
}

/**
 * Everything right of the mini-player's video: title, channel, play/pause, close, and the
 * progress bar. Shared by the real mini-player and the morph's look-alike ([interactive]
 * false: drawn only, touches go to the real one underneath).
 */
@Composable
private fun RowScope.MiniDetails(state: PlayerState, interactive: Boolean) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    Box(Modifier.weight(1f).fillMaxHeight()) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.channel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            MorphPlayButton(
                playing = state.isPlaying,
                buffering = state.isBuffering,
                size = 46.dp,
                container = c.primary,
                content = c.onPrimary,
                enabled = interactive,
                onClick = state::togglePlay,
            )
            Box(Modifier.size(40.dp).pressable(enabled = interactive, onClick = state::close), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Close", tint = c.onSurfaceVariant)
            }
        }
        val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(
            Modifier.align(Alignment.BottomStart).padding(end = 96.dp, bottom = 7.dp)
                .fillMaxWidth().height(4.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.18f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(c.primary))
        }
    }
}

/**
 * The mini-player's details, drawn inside the morphing page: always just right of the
 * shrinking video and centred on it, fading in over the last 30% of the collapse (out over
 * the first 30% of an open). At progress 0 they sit exactly where the real mini-player's
 * are, so the handoff is invisible.
 */
@Composable
private fun MorphingMiniDetails(state: PlayerState, morph: PlayerMorph) {
    val g = morph.geometry ?: return
    Row(
        Modifier
            // Laid out per frame (this small row only, no recomposition): it spans from the
            // video's right edge to the page's right edge, so the buttons hug the page while
            // it narrows, and it sits vertically centred on the video.
            .layout { measurable, _ ->
                val p = morph.progress.value
                val gap = 12.dp.toPx()
                val f = g.videoFrame(g.stage.size, p)
                val start = g.stage.left + f.offset.x + f.scale * f.window.right + gap
                val videoCenterY = g.stage.top + f.offset.y + f.scale * f.window.center.y
                val frameRight = lerpF(g.miniCard.right, g.screen.width, p)
                val width = (frameRight - 8.dp.toPx() - start).coerceAtLeast(0f).toInt()
                val height = MiniHeight.roundToPx()
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
                layout(width, height) {
                    placeable.placeRelative(start.toInt(), (videoCenterY - height / 2f).toInt())
                }
            }
            .graphicsLayer { alpha = 1f - ramp(morph.progress.value, 0f, 0.3f) },
        verticalAlignment = Alignment.CenterVertically,
    ) { MiniDetails(state, interactive = false) }
}

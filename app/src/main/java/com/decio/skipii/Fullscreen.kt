package com.decio.skipii

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Decides the activity's orientation, YouTube-style:
 *  - Only the open player may rotate; tabs and the mini-player stay portrait.
 *  - With the player open, it follows the phone (and the system rotation lock), but never
 *    upside down: USER, not FULL_USER, which allowed reverse portrait (Derek, 2026-10-10).
 *  - The fullscreen button forces landscape even while the phone is upright, and lets go
 *    once the phone is physically turned, so turning it back later exits naturally. The
 *    exit button does the mirror image.
 */
@Stable
class OrientationController(private val activity: Activity) {
    private enum class Forced { Landscape, Portrait }

    private var forced by mutableStateOf<Forced?>(null)
    private var playerOpen = false

    private val listener = object : OrientationEventListener(activity) {
        override fun onOrientationChanged(degrees: Int) {
            if (degrees == ORIENTATION_UNKNOWN) return
            val physicallyLandscape = degrees in 60..120 || degrees in 240..300
            val physicallyPortrait = degrees <= 30 || degrees >= 330
            when {
                // Only release a forced landscape when auto-rotate is on; with the rotation
                // lock on, releasing would snap straight back to portrait.
                forced == Forced.Landscape && physicallyLandscape && autoRotateOn() -> forced = null
                forced == Forced.Portrait && physicallyPortrait -> forced = null
            }
        }
    }

    private fun autoRotateOn() =
        Settings.System.getInt(activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    fun enterFullscreen() { forced = Forced.Landscape }
    fun exitFullscreen() { forced = Forced.Portrait }

    fun setPlayerOpen(open: Boolean) {
        playerOpen = open
        if (!open) forced = null
        apply()
    }

    fun apply() {
        activity.requestedOrientation = when {
            !playerOpen -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            forced == Forced.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            forced == Forced.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_USER
        }
    }

    @Composable
    fun Bind(playerOpen: Boolean) {
        DisposableEffect(Unit) {
            if (listener.canDetectOrientation()) listener.enable()
            onDispose { listener.disable() }
        }
        LaunchedEffect(playerOpen, forced) { setPlayerOpen(playerOpen) }
    }
}

@Composable
fun rememberOrientationController(activity: Activity) = remember(activity) { OrientationController(activity) }

/** Hides the status and navigation bars while [hidden]; a swipe from the edge peeks them. */
@Composable
fun SystemBarsHidden(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(hidden) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/**
 * Picture-in-picture (Derek, 2026-10-10): the button beside the quality pill shrinks the app
 * into a floating window that shows only the video; its play/pause come from the media
 * session. Expanding the window brings the player back; closing it pauses, as YouTube does.
 */
@Stable
class PipController(private val activity: ComponentActivity) {
    var active by mutableStateOf(activity.isInPictureInPictureMode)
        private set
    val supported = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    /** The video's bounds in the window, so the floating window grows out of it. */
    var videoBounds: Rect? = null

    /** False when the system refused (PiP switched off for the app in Settings). */
    fun enter(videoAspect: Float): Boolean {
        if (!supported) return false
        // Android accepts 1:2.39 to 2.39:1; anything outside it would throw.
        val ratio = videoAspect.coerceIn(0.42f, 2.38f)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
            .apply {
                videoBounds?.let { b -> setSourceRectHint(android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())) }
            }
            .build()
        return activity.enterPictureInPictureMode(params)
    }

    @Composable
    fun Bind(onClosed: () -> Unit) {
        DisposableEffect(activity) {
            val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
                active = info.isInPictureInPictureMode
                // Closing the window stops the activity; expanding it doesn't.
                if (!info.isInPictureInPictureMode && !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onClosed()
            }
            activity.addOnPictureInPictureModeChangedListener(listener)
            onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
        }
    }
}

/** The app's [PipController]; null where there's none (previews). */
val LocalPictureInPicture = staticCompositionLocalOf<PipController?> { null }

// ── Screen gestures (brightness / volume) ──────────────────────────────────────

/** What a fullscreen swipe is adjusting while its indicator shows. */
enum class ScreenGestureKind { Brightness, Volume }

/**
 * The fullscreen player's screen gestures (Derek, 2026-10-17): a vertical swipe over the left
 * 40 % of the screen sets the window's brightness, over the right 40 % the media volume — up
 * for more, down for less, as the muscle memory from other players expects. Only the open
 * activity's window is ever touched (the system setting is never written), and the brightness
 * goes back to following the system when the player closes. The middle 20 % is left to the
 * player's own swipe-down, and taps are never eaten.
 */
@Stable
class ScreenGestures(private val activity: Activity, private val scope: CoroutineScope) {
    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** What the last swipe adjusted; null once its indicator has faded. */
    var kind by mutableStateOf<ScreenGestureKind?>(null)
        private set
    /** The value as a 0..1 fraction: the brightness, or the volume against the stream's max. */
    var value by mutableFloatStateOf(0f)
        private set
    private var hideJob: Job? = null

    /** Drags the brightness by [fraction] of the screen (up is brighter). */
    fun brightnessBy(fraction: Float) {
        val window = activity.window
        val current = window.attributes.screenBrightness.takeIf { it >= 0f } ?: systemBrightness()
        val next = (current + fraction).coerceIn(0.02f, 1f)
        window.attributes = window.attributes.apply { screenBrightness = next }
        show(ScreenGestureKind.Brightness, next)
    }

    /** Where the volume sits while a swipe runs — an index alone is too coarse to drag. */
    private var volumeAt: Float? = null

    /** Drags the volume by [fraction] of the music stream's range (up is louder). */
    fun volumeBy(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        // The stream has only ~15 steps: one event moves a tenth of a step, so rounding each
        // event on its own would never budge it. Keep the fractional position across the whole
        // swipe (as the brightness does in the window) and land on the nearest index.
        val at = (volumeAt ?: audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) + fraction * max
        val next = at.coerceIn(0f, max.toFloat())
        volumeAt = next
        val index = next.roundToInt()
        if (index != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        }
        show(ScreenGestureKind.Volume, index.toFloat() / max)
    }

    /** The finger lifted: the indicator stays a moment, then fades (HudDismissMs). */
    fun release() {
        volumeAt = null
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(HudDismissMs)
            kind = null
        }
    }

    /** Back on the system's brightness (the player closed). */
    fun resetBrightness() {
        val window = activity.window
        if (window.attributes.screenBrightness >= 0f) {
            window.attributes = window.attributes.apply { screenBrightness = -1f }
        }
    }

    private fun show(kind: ScreenGestureKind, value: Float) {
        hideJob?.cancel()
        this.kind = kind
        this.value = value
    }

    /** What the system's slider is at now, for the first swipe to pick up from. */
    private fun systemBrightness(): Float = runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f)

    private companion object {
        const val HudDismissMs = 900L
    }
}

@Composable
fun rememberScreenGestures(activity: Activity): ScreenGestures {
    val scope = rememberCoroutineScope()
    return remember(activity) { ScreenGestures(activity, scope) }
}

/**
 * The fullscreen gesture layer. Vertical swipes over the left 40 % of the screen run
 * [gestures]' brightness, over the right 40 % its volume; the middle 20 % falls through to
 * [onExitDrag] / [onExitDrop] — the fullscreen player's own swipe-down. Reads on the Initial
 * pass so the player's taps, skip buttons, scrubber and double-tap seek never fight it, and
 * consumes nothing until a vertical drag passes the touch slop: taps land exactly as before.
 */
fun Modifier.screenGestures(
    gestures: ScreenGestures,
    onExitDrag: ((Float) -> Unit)? = null,
    onExitDrop: ((Float) -> Unit)? = null,
): Modifier = composed {
    val drag by rememberUpdatedState(onExitDrag)
    val drop by rememberUpdatedState(onExitDrop)
    pointerInput(gestures) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val left = down.position.x < size.width * 0.4f
            val right = down.position.x > size.width * 0.6f
            if (!left && !right && drag == null) return@awaitEachGesture
            val tracker = if (!left && !right) VelocityTracker() else null
            tracker?.addPosition(down.uptimeMillis, down.position)
            var dragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                tracker?.addPosition(change.uptimeMillis, change.position)
                // Read the delta before consuming: a consumed change reports no movement.
                val step = change.positionChange().y
                if (!dragging) {
                    // A vertical drag takes the gesture; taps and horizontal scrubber drags
                    // are the content's.
                    val dy = change.position.y - down.position.y
                    if (abs(dy) <= slop || abs(dy) <= abs(change.position.x - down.position.x)) continue
                    dragging = true
                }
                change.consume()
                when {
                    left -> gestures.brightnessBy(-step / size.height)
                    right -> gestures.volumeBy(-step / size.height)
                    else -> drag?.invoke(step)
                }
            }
            if (dragging) {
                gestures.release()
                if (!left && !right) drop?.invoke(tracker?.calculateVelocity()?.y ?: 0f)
            }
        }
    }
}

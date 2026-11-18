# Skipii — working notes

Native Android YouTube **player** (not a YouTube app): Kotlin, Jetpack Compose, Media3,
NewPipeExtractor, sideloaded, one user, no ads. Design: Material 3 Expressive; approved mockup in
`docs/design/mockup.html`.

**This repo is a personal fork.** It ships as `com.skipii.onevoidtube`, label `onevoidtube`,
dark icon with a cyan badge, and installs **alongside** Derek's stock `com.skipii` build
(`Skipii-*.apk` in the repo root). Both APKs use the AndroidDebug key, so only the application ID
keeps them apart: never set it back to `com.skipii` unless the build should replace the stock app.
Side-by-side installs share nothing — data, sign-in, settings, notification permission and media
session are all per app.

## Product decisions (binding)
- Pure player: tabs Home / Subscriptions / Search / Library. No upload/creating, no notifications tab.
- Google sign-in: WebView on Google's own page, cookies AES-GCM encrypted via Keystore. **Full sync**:
  read home/subs/history/playlists, write likes, subscriptions, Watch later, libraries, watch
  history. Keep the account-suspension warning. Signed out, everything works except account feeds.
- Never add an ad SDK. SponsorBlock per category — Skip / Ask / Show / Off — defaults: sponsor
  Skip; self-promo + reminder Ask; intro/outro/preview Show; music/filler Off. Settings and the
  CC BY-NC-SA credit live in the account sheet. No commercial use.
- Player chrome overlays the video. Chapters live on the video/scrubber, never as a card under it.
  The related list is "More videos", never "Up next". No minimize button (swipe down / Back).
- Queue is always `[current, ...queue]`; long-press a video → Play next / Add to queue.
- No watch-time stats in Library. No fake/placeholder data — empty states are designed states.
- Theme = YouTube first (`SEED_YOUTUBE`), launcher icon = the in-app BrandMark; themes are
  Ledgii's (copy palettes verbatim, fill unset roles with `completeLedgiiScheme`).
- Background playback = audio only (video track off on ON_STOP, back on ON_START); the
  audio-only state is remembered (`wantBackground`) and re-applied on every controller connect.
- Comments: read-only list from web `next` pages + composer; posting/replies/likes and library
  writes are **not verified** with a real account.

## UI rules
1. No ripples anywhere (`LocalRippleConfiguration provides null`); press feedback is `pressable`
   (0.94 scale + spring).
2. Never animate a color to `Color.Transparent`; animate alpha.
3. In a Box inside a Column, fade with `animateFloatAsState` + `graphicsLayer`, not `AnimatedVisibility`.
4. Enter/Done/Search dismisses the keyboard on every field (post the hide ~120 ms late).
5. Skips fade audio — never hard-cut.
6. No 1 dp outlines/borders; tone separates surfaces.
7. Re-tapping the selected tab scrolls to top; fresh content starts at the top; never key a list's
   trailing loader item.
8. `pressable(enabled = false)` attaches no click handler, so taps fall through.
9. Text color comes from `LocalContentColor` (set in `SkipiiTheme`), never Compose's default black.
10. Root-cause fixes only; a side effect wrapped in `runCatching` needs a fallback/state change.
11. Judge motion on the release build, never on debug.

## Build & verify
```bash
export JAVA_HOME=/opt/android-studio/jbr   # macOS: /Applications/Android Studio.app/Contents/jbr/Contents/Home
# local.properties (gitignored): sdk.dir=/home/mouad/Android/Sdk
./gradlew assembleDebug          # must pass before presenting any change
./gradlew testDebugUnitTest      # InnerTube / CommentFeed parser tests (real captured fixtures)
./gradlew assembleRelease        # shipping truth: R8, signed with the debug key
```
- Verify playback on the **release** build: `adb logcat -s Skipii:I | grep "media source"` must say
  "video itag … + audio itag …", never "combined fallback"; check the quality sheet's "Playing …" line.
- Pull-to-refresh replaces refresh buttons; lists use collapsing headers (`Header.kt`).
- Emulators: `emulator-5554` is shared with other sessions; use `emulator -avd skipii_qa -port 5560
  -no-window` for uninterrupted checks. Turn off `stay_on_while_plugged_in` before timeout tests.
- No real device events: `adb shell input tap` takes ~0.3 s (too slow for double taps). Use
  `adb root` and write raw evdev `input_event` structs (slot/tracking id/x/y scaled 0..32767,
  BTN_TOUCH, SYN) in ONE file, then `cat` it to `/dev/input/event1`. They arrive as STYLUS.
- Screenshot: `adb exec-out screencap -p`. Playback state: `adb shell dumpsys media_session`.

## Traps already hit (do not relearn)
- **R8 vs NewPipeExtractor**: shrinking it emptied audio streams in release only (silent 360p
  fallback labelled 1080p). The extractor stays whole — see `proguard-rules.pro`.
- **Streams**: the extractor's ANDROID request is SABR-only (no URLs); every playable URL comes
  from its VISIONOS request. `PlayerFallback` answers an ANDROID bot check with a VISIONOS answer
  minus URLs. Never put VISIONOS URLs in the ANDROID slot (403 on the first deep seek);
  ANDROID_VR/IOS links 403 past the first chunk (PO tokens).
- **HTTP 403 recovery** lives in the service (`StreamRecoverer`), not the UI, so it works with no
  screen. Mobile data changes the phone's address mid-video, so it may repeat: recover again once
  ≥30 s more has played, then give up.
- **One TextureView / SurfaceTexture per process** (`SharedVideoTexture`, `LocalVideoSurface`):
  moving a TextureView detaches it and destroys its SurfaceTexture (frozen picture). The destroy
  callback returns false, re-attached views get the texture back, and the player's surface is
  never cleared. The mini-player's video slot is EMPTY while the player is open/morphing.
- Springs on the morph's `progress` need `visibilityThreshold = 0.0005`, or they snap visibly.
  The shrinking player has **no pointer input** unless `interactive = expanded || dragging`.
- JSON walking: `walk` only visits values of keys — objects that are array elements are skipped.
  Use `forEachObject` (playlist add options, comment entity mutations, watch-page params).
- Browse feeds drop members-only videos and movies/TV (`InnerTube.isMembersOnly` / `isMovie`);
  BADGE_COMMERCE is a movie signal except "Fundraiser".
- `AvSync` (API 33+) measures drawn frames (median of 61) and shifts the ExoPlayer renderer; don't
  replace it with a guessed offset. Audio/Bluetooth latency stays ExoPlayer's job.
- Speed is per video unless "Remember for every video"; the service's `SpeedKeeper` applies it to
  each new video id (a quality switch on the same id keeps it). SponsorBlock skipping runs in the
  service: 300 ms fade out → seek → 450 ms fade in.
- Watch history + watch-time pings are sent by the service's `WatchTimeReporter` (10/20/30 s then
  every 40 s, plus pause/seek/move-on) — without them every video looks abandoned at 0 s.
- Parser tests run against real captured responses in `src/test/resources`; re-capture fixtures
  when YouTube changes a layout rather than guessing at shapes.

## Code map (`app/src/main/java/com/decio/skipii/`)
- `YouTube.kt` — models, details/search via NewPipeExtractor, `StreamCache`, `StreamSelection`.
- `PlaybackService.kt` — MediaSessionService + ExoPlayer, DASH manifest generation, recovery, SponsorBlock.
- `PlayerState.kt` — UI-side MediaController state, queue, library edits.
- `PlayerMorph.kt` — mini ↔ player transform; one `progress` read only in graphics/draw lambdas.
- `PlayerUi.kt` — portrait watch screen, details, actions, mini-player, morph geometry.
- `VideoView.kt` — `VideoContent`, `FullscreenPlayer`, quality sheet, scrubber, seek burst, HUD.
- `Fullscreen.kt` — `OrientationController`, system bars, `PipController`, screen gestures.
- `Comments.kt` / `CommentFeed.kt` / `CommentPosting.kt` — comments UI, web parsing, writes.
- `Account.kt` — session, SAPISIDHASH auth, `InnerTube` browse/write APIs; `SignIn.kt` WebView sign-in.
- `SponsorBlock.kt`, `PlaybackModes.kt` (speed/audio-only), `SpeedPanel.kt`, `Sheets.kt`, `LibrarySheet.kt`.
- `Feeds.kt` — `FeedState` paging, `LibraryData`; `Screens.kt` — Home/Subscriptions/Search/Library.
- `MainActivity.kt` — shell, tabs, player/fullscreen, overlays; `UiKit.kt` — shared kit.
- `Theme.kt`, `Motion.kt`, `Playful.kt`, `Header.kt`, `Channel.kt`, `Resume.kt`, `SecureStore.kt`,
  `Net.kt`, `AvSync.kt`.

## Roadmap
✅ Playback, mini-player, morph, fullscreen/PiP, comments, sign-in + full sync, queue, SponsorBlock,
speed/audio-only, libraries, upright fullscreen + gestures, side-by-side packaging.

### Requested changes (phased)
Phase 1 (easy UI/consistency): search keep results on empty + autofocus; channel tab empty hiding;
playlist thumbnails full width; long-press copy channel name; comments/button skeletons; sort loading
guard + least-views; search icon center (pending).
Phase 2 (medium features): not-interested action + API; transcript; content word filters; nav-bar
background setting; vertical fullscreen gesture range; micro-transitions; reorder player controls UI.
Phase 3 (larger features): chapters navigation; audio-only data optimization; profile settings restructure
+ Material 3 expressive; offline downloads; captions.

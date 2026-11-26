package com.decio.skipii

import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.rounded.Forum
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import java.lang.ref.WeakReference
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SlowMotionVideo
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Below Android 13 the picked language is applied per window (AppLanguage). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+ rebuilds the window after a change in Settings › Apps › Skipii › Language too.
        AppLanguage.refresh()
        // One window at a time (one player, one video texture): a newer one closes the one before.
        // That's a bubble opened while the app is open, or the app opened from Home while it sits
        // in a bubble (the bubble then goes away). Its task goes too, so Recents keeps no stale card;
        // PlaybackService.onTaskRemoved sees this window still open and plays on.
        current?.get()?.takeIf { it !== this && !it.isFinishing }?.finishAndRemoveTask()
        current = WeakReference(this)
        enableEdgeToEdge()
        // The media notification (and its lock-screen controls) needs this on Android 13+.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        (NewVideos.takeVideo(intent) ?: linkedVideo(intent))?.let { NewVideos.opening = it }
        LiveControls.takeOpen(intent)
        setContent { SkipiiTheme { SkipiiApp() } }
    }

    /** A new-video notification tapped while this window is open (singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        (NewVideos.takeVideo(intent) ?: linkedVideo(intent))?.let { NewVideos.opening = it }
        LiveControls.takeOpen(intent)
    }

    override fun onStart() {
        super.onStart()
        AppUpdate.checkIfDue()
    }

    override fun onResume() {
        super.onResume()
        AppUpdate.onWindowResumed(this)
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        private var current: WeakReference<MainActivity>? = null

        /** The task of the app's open window; null when there's none (closed, or on its way out). */
        fun openTaskId(): Int? = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }?.taskId
    }
}

enum class Tab(@StringRes val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home(R.string.app_tab_home, Icons.Outlined.Home, Icons.Rounded.Home),
    Subscriptions(R.string.app_tab_subscriptions, Icons.Outlined.Subscriptions, Icons.Rounded.Subscriptions),
    Shorts(R.string.app_tab_shorts, Icons.Outlined.SlowMotionVideo, Icons.Rounded.SlowMotionVideo),
    Posts(R.string.app_tab_posts, Icons.Outlined.Forum, Icons.Rounded.Forum),
    Search(R.string.app_tab_search, Icons.Rounded.Search, Icons.Rounded.Search),
    Library(R.string.app_tab_library, Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary);

    /** The label in the app's current language (resolved on each read, never kept). */
    val label: String get() = str(labelRes)
}

// The mini-player floats 8 dp above the nav pill (NavBar.kt), capped at 368 dp, centered.
private val MiniWidth = 368.dp
private val MiniGap = 8.dp
/** The floating mini-player keeps this far from the screen's sides. */
private val MiniEdge = 12.dp

@Composable
fun SkipiiApp() {
    val c = MaterialTheme.colorScheme
    val player = rememberPlayerState()
    val scope = rememberCoroutineScope()
    val search = remember { SearchState(scope) }
    val appContext = LocalContext.current.applicationContext
    val lib = remember { LibraryData(scope, appContext) }
    LaunchedEffect(Account.signedIn) { if (Account.signedIn) lib.warmUp() }
    var signingIn by remember { mutableStateOf(false) }
    // Settings (Settings.kt): a page over the app, from the header avatar. Saveable: picking a language rebuilds the window.
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<PlaylistRef?>(null) }
    var openChannel by rememberSaveable { mutableStateOf<String?>(null) }
    // Kept after closing so the page can slide out with its content.
    var channelState by remember { mutableStateOf(openChannel?.let { ChannelState(it, scope) }) }
    val playlistFeed = remember(openPlaylist?.id) { openPlaylist?.let { lib.playlistFeed(it.id) } }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    // Shorts: its own feed and players; ‹ (and Back) return to the tab it was opened from.
    // One per process (SkipiiApplication), so its bank keeps filling with no window open.
    val shorts = (LocalContext.current.applicationContext as SkipiiApplication).shorts
    // A window rebuilt for a new screen (a fold the manifest's configChanges don't cover) carries on
    // with the same short: leave() would move past it.
    val host = LocalContext.current as Activity
    // Big screens (Adaptive.kt): the rail, grids and the two-pane player once the window is wide.
    val windowLayout = rememberWindowLayout(host)
    val wide = windowLayout.wide
    // A TV (Tv.kt): the remote drives everything, and the open player is always TvPlayer, across the screen.
    val tv = windowLayout.tv
    DisposableEffect(shorts) { onDispose { if (!host.isChangingConfigurations) shorts.leave() } }
    var shortsFrom by rememberSaveable { mutableStateOf(Tab.Home) }
    // A short tapped on a channel page plays in Shorts among that channel's shorts; ‹ / Back return to
    // that page. [shortsListOnTab]: it was opened from Shorts itself (the channel page over a short).
    var shortsBackTo by rememberSaveable { mutableStateOf<String?>(null) }
    var shortsListOnTab by rememberSaveable { mutableStateOf(false) }
    // A playlist opened from a channel page goes over it (else the channel goes over the playlist).
    var playlistOverChannel by remember { mutableStateOf(false) }
    // A comment notification's tap: the video's comments on their own, with that comment marked.
    var commentTarget by remember { mutableStateOf<CommentTarget?>(null) }
    // The live activity's tap (LiveActivity.kt): the player opens on what's playing, once the controller has it back.
    LaunchedEffect(LiveControls.openPlayer, player.current) {
        if (!LiveControls.openPlayer) return@LaunchedEffect
        val video = player.current ?: return@LaunchedEffect
        LiveControls.openPlayer = false
        settingsOpen = false
        if (tab == Tab.Shorts) tab = shortsFrom
        player.open(video)
    }
    // Bumped when the already-selected tab is tapped; that tab's list scrolls to the top.
    val scrollToTop = remember { mutableStateMapOf<Tab, Int>() }
    val reduced = rememberReducedMotion()
    val morph = remember { PlayerMorph(scope) { expanded -> player.expanded = expanded } }
    // Folding or unfolding never sends the open video back to the mini-player: if the window is
    // rebuilt (the service plays on), the player opens again at once, without the morph, as soon
    // as the controller has the video back.
    var savedExpanded by rememberSaveable { mutableStateOf(false) }
    var reopening by remember { mutableStateOf(savedExpanded) }
    LaunchedEffect(player.controller) {
        if (player.controller == null || !reopening) return@LaunchedEffect
        if (player.current != null) { morph.progress.snapTo(1f); player.expanded = true }
        reopening = false
    }
    LaunchedEffect(player.expanded) { if (!reopening) savedExpanded = player.expanded }

    // The screen stays on while a video plays (full player, mini-player or fullscreen);
    // paused, the normal timeout applies again. Only affects the visible window.
    val rootView = LocalView.current
    DisposableEffect(player.isPlaying, shorts.playing) {
        rootView.keepScreenOn = player.isPlaying || shorts.playing
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
                // Not for a window being rebuilt (fold / unfold): the video track would go off and back on.
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> if (!host.isChangingConfigurations) {
                    player.setBackground(true); shorts.setForeground(false); LiveControls.setOnScreen(false)
                    // Background play off: the video stops with the app (audio only keeps playing either way).
                    if (!BackgroundPlay.enabled && !AudioOnly.enabled) player.pausePlayback()
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> { player.setBackground(false); shorts.setForeground(true); LiveControls.setOnScreen(true) }
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
    // On Shorts the app's video waits, paused, out of sight (no mini-player).
    val hasMini = player.current != null && tab != Tab.Shorts
    // Lists scroll fully clear of the floating nav (and mini-player when present). A wide window
    // has the rail instead of the nav; only the mini-player card (bottom right) needs clearing.
    val contentPadding = if (wide) PaddingValues(bottom = stackBottom + 0.dp + 16.dp)
    else PaddingValues(
        bottom = stackBottom + NavHeight + 0.dp + 16.dp,
    )
    // On a wide window the pages start right of the rail (none on Shorts: a short takes the whole height).
    val railStart = WindowInsets.safeDrawing.only(WindowInsetsSides.Start).asPaddingValues().calculateStartPadding(LayoutDirection.Ltr)
    val showRail = wide && tab != Tab.Shorts
    val pagesStart = if (showRail) RailWidth + railStart else 0.dp

    // Fullscreen is whenever the open player is shown in landscape (rotation or button).
    val activity = LocalContext.current as Activity
    val orientation = rememberOrientationController(activity as ComponentActivity)
    val playerOpen = player.current != null && player.expanded
    // In split screen this is the pane's shape, not the phone's. On a near-square screen it's how the
    // screen is turned: its window's shape says nothing there. A Z Flip's cover screen (720 × 748) never
    // turns, but its window came out wider than tall, so the open player was always fullscreen and the
    // exit button (asking for portrait) couldn't undo it (Z Flip 6 user, 2026-11-04).
    val config = LocalConfiguration.current
    val windowLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val landscape = remember(windowLandscape, config.screenWidthDp, config.screenHeightDp, config.densityDpi, orientation.multiWindow) {
        if (!orientation.multiWindow && activity.onSquareScreen()) activity.turnedSideways() else windowLandscape
    }
    // Audio only has no picture to go fullscreen with: the open player stays portrait.
    orientation.Bind(playerOpen && !AudioOnly.enabled, landscape)
    val pip = remember(activity) { PipController(activity) }
    // Closing the picture-in-picture window pauses (expanding it just comes back).
    pip.Bind(onClosed = { player.controller?.pause() })
    // Leaving the app mid-video floats it (Settings › Playback, off unless turned on; #42).
    pip.AutoEnter(AutoPip.on && player.isPlaying && player.current != null && !AudioOnly.enabled, player.videoAspect)
    // The PiP window can be any shape: hold whatever was showing when it opened.
    val fullscreenBeforePip = remember { mutableStateOf(false) }
    // Tabletop (an open foldable stood half open, Tabletop.kt): the open player is the video on the top
    // half and the black deck on the bottom half, in place of the page and of fullscreen.
    val tabletop = windowLayout.tabletopY != null && playerOpen && !pip.active
    val showFullscreen = !tabletop && !tv && if (pip.active) fullscreenBeforePip.value else orientation.showsFullscreen(playerOpen, landscape)
    SideEffect { if (!pip.active) fullscreenBeforePip.value = showFullscreen }
    LaunchedEffect(showFullscreen) { player.fullscreen = showFullscreen }
    SystemBarsHidden(showFullscreen || tabletop || (tv && playerOpen))
    // A vertical video's fullscreen stays upright, and every video's on a near-square phone (Fullscreen.kt).
    fun toggleFullscreen() = if (showFullscreen) orientation.exitFullscreen() else orientation.enterFullscreen(vertical = player.videoAspect < 1f)

    fun showChannel(url: String) {
        // From the player: minimize it so the channel page is visible.
        player.expanded = false
        if (channelState?.url != url) channelState = ChannelState(url, scope)
        playlistOverChannel = false
        openChannel = url
    }

    /** A channel's short tapped: Shorts plays the channel's list from it, the page waits for ‹ / Back. */
    fun openShorts(list: ShortsList) {
        shortsBackTo = openChannel
        shortsListOnTab = tab == Tab.Shorts
        openChannel = null
        shorts.openList(list)
        if (tab != Tab.Shorts) { shortsFrom = tab; tab = Tab.Shorts }
    }

    /**
     * A video tapped anywhere (history, a playlist, a link, a notification): a Short opens in Shorts, as the tab
     * shows it, and carries on into more of them; anything else opens in the player. Only what could be a Short
     * (they run up to three minutes) is asked about, and the answer is kept.
     */
    fun openVideo(video: VideoItem) {
        if (video.durationSec > 180) { player.open(video); return }
        scope.launch {
            if (ShortCheck.isShort(video.id)) {
                // The player makes room; Shorts pauses it on the way in and carries on when it leaves.
                player.expanded = false
                openShorts(ShortsList(listOf(video), 0, InnerTube.reelSequenceParams(video.id)) { token -> InnerTube.shorts(token) })
            } else {
                player.open(video)
            }
        }
    }

    // A new-video notification's tap: that video opens in the player, or in Shorts if it is one (leaving Shorts, as a channel page's video does).
    LaunchedEffect(NewVideos.opening) {
        val video = NewVideos.opening ?: return@LaunchedEffect
        NewVideos.opening = null
        settingsOpen = false
        if (tab == Tab.Shorts) tab = shortsFrom
        openVideo(video)
    }

    /** ‹ / Back on Shorts: to the tab it was opened from, or back to the channel page a short came from. */
    fun leaveShorts() {
        val back = shortsBackTo
        shortsBackTo = null
        when {
            back == null -> tab = shortsFrom
            // From another tab: leave() puts the feed back; the channel page shows over that tab again.
            !shortsListOnTab -> { tab = shortsFrom; showChannel(back) }
            // From the channel page over a short: the page covers the feed's short again (paused first).
            else -> { showChannel(back); shorts.cover(true); shorts.closeList() }
        }
    }

    // Shorts on a TV: the short and its glass bar's Like, so the remote can go between them.
    val shortFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val shortsBarFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var tvPlayerWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(tv && playerOpen) {
        if (tv && playerOpen) tvPlayerWasOpen = true
        // Closing it puts the remote back on the card that opened the video (if it's still there).
        else if (tvPlayerWasOpen) { tvPlayerWasOpen = false; TvFocusMemory.opener?.focusWhenReady() }
    }
    BackHandler(enabled = player.expanded) { player.expanded = false }
    BackHandler(enabled = !player.expanded && tab != Tab.Home && openPlaylist == null) { if (tab == Tab.Shorts) leaveShorts() else tab = Tab.Home }
    BackHandler(enabled = !player.expanded && openPlaylist != null) { openPlaylist = null }
    BackHandler(enabled = signingIn) { signingIn = false }
    BackHandler(enabled = !player.expanded && openChannel != null && !(playlistOverChannel && openPlaylist != null)) { openChannel = null }
    BackHandler(enabled = tab == Tab.Shorts && shorts.commentsOpen) { shorts.commentsOpen = false }
    // Registered last so it wins: in fullscreen, Back returns to the portrait player first.
    BackHandler(enabled = showFullscreen) { orientation.exitFullscreen() }

    // The open player's bar (PlayerBar.kt): its tools card / quality list close on Back, on a
    // tap anywhere else, and whenever the player stops being the open portrait page.
    val playerBar = remember { PlayerBarState(scope) }
    BackHandler(enabled = playerBar.scrimActive && playerOpen && !showFullscreen) { playerBar.closeAll() }
    // Kept open by default (Settings › Playback): the card opens with the player and stays; otherwise it folds away.
    LaunchedEffect(playerOpen, showFullscreen, player.sheet, BarPrefs.expandedByDefault) {
        val showing = playerOpen && !showFullscreen && player.sheet == null
        playerBar.allowExpanded = showing
        if (!showing) playerBar.closeAll(keepExpanded = false)
        else if (BarPrefs.expandedByDefault) playerBar.openCard()
        else if (playerBar.card.targetValue > 0f) playerBar.closeAll(keepExpanded = false)
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    // 1 while Settings is open: the tab bar and mini-player sink out of sight, as Moodii's bar does under a page.
    val settingsAway by animateFloatAsState(if (settingsOpen) 1f else 0f, if (reduced) snap() else MoodiiSpatial(), label = "settingsAway")

    // Shorts (Shorts.kt) play on their own players: the app's video pauses on the way in and
    // carries on, from the same spot in the mini-player, on the way out (Derek, 2026-10-28).
    // One progress turns the tab bar into the glass bar: 620 ms in, 460 ms out, both moving at once.
    val shortsMorph = remember { androidx.compose.animation.core.Animatable(if (tab == Tab.Shorts) 1f else 0f) }
    var resumeMain by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tab) {
        if (tab == Tab.Shorts) {
            resumeMain = resumeMain || player.isPlaying
            player.controller?.pause()
            player.setVideoAside(true)
            shorts.enter()
        } else if (shorts.active) {
            shortsBackTo = null
            shorts.leave()
            player.setVideoAside(false)
            if (resumeMain) player.controller?.play()
            resumeMain = false
        }
    }
    // Shorts turned off (account sheet) while it was the tab: back to where it was opened from.
    LaunchedEffect(PostsSetting.enabled) { if (!PostsSetting.enabled && tab == Tab.Posts) tab = Tab.Home }
    LaunchedEffect(ShortsSetting.enabled) { if (!ShortsSetting.enabled && tab == Tab.Shorts) tab = shortsFrom }
    LaunchedEffect(tab == Tab.Shorts) {
        if (tab == Tab.Shorts) shortsMorph.animateTo(1f, if (reduced) snap() else tween(ShortsEnterMs, easing = androidx.compose.animation.core.LinearEasing))
        else shortsMorph.animateTo(0f, if (reduced) snap() else tween(ShortsExitMs, easing = ShortsExitEasing))
    }
    val shortsShown by remember { derivedStateOf { shortsMorph.value > 0f } }
    SideEffect {
        StatusBarOverVideo.shorts = tab == Tab.Shorts && openChannel == null
        shorts.onMessage = { player.actionMessage = it }
        shorts.mainBusy = { player.isBuffering }
    }

    // Taps, back and opening a video set `expanded`; this turns that into the morph.
    // Gesture releases settle themselves and are left alone (see PlayerMorph.animateTo).
    LaunchedEffect(player.expanded, player.current?.id) {
        when {
            player.current == null -> morph.progress.snapTo(0f)
            // No morph on a TV: the player opens across the screen and closes to the corner card at once.
            tv -> morph.progress.snapTo(if (player.expanded) 1f else 0f)
            else -> morph.animateTo(player.expanded, reduced)
        }
    }
    val showPlayer by remember {
        // Held until the morph has completely finished, so the video changes hands once.
        derivedStateOf { player.current != null && (player.expanded || morph.dragging || morph.progress.isRunning || morph.progress.value > 0f) }
    }

    // A tab tapped (the pill or the rail): a re-tap backs out of a playlist / channel, else scrolls to the top.
    fun selectTab(selected: Tab) {
        when {
            selected == tab && playlistOverChannel && openPlaylist != null -> openPlaylist = null
            selected == tab && openChannel != null -> openChannel = null
            selected == tab && openPlaylist != null -> openPlaylist = null
            selected == tab -> scrollToTop[selected] = (scrollToTop[selected] ?: 0) + 1
            else -> {
                if (selected == Tab.Shorts) shortsFrom = tab
                openPlaylist = null; openChannel = null; tab = selected
            }
        }
    }

    // A channel opened from Shorts sits over the short, which waits paused.
    SideEffect { shorts.cover(tab == Tab.Shorts && openChannel != null) }
    BackHandler(enabled = tab == Tab.Shorts && shorts.channelMenu) { shorts.channelMenu = false }

    // The floating mini-player's resting place: which side, and how far down (a share of the window's
    // height; negative = the default spot, just above the tab bar). The drag offsets are the live finger.
    var miniRight by rememberSaveable { mutableStateOf(true) }
    var miniTopShare by rememberSaveable { mutableStateOf(-1f) }
    val miniDragX = remember { androidx.compose.animation.core.Animatable(0f) }
    val miniDragY = remember { androidx.compose.animation.core.Animatable(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().background(c.surface)) {
        // The open player's shape on this window (PlayerUi.kt): the phone page, or on a wide window
        // Rail 3 — beside the rail on a tablet; across the whole screen, split at the fold, on an
        // open foldable (option B: More videos on the left page, the rest on the right).
        val endInset = WindowInsets.safeDrawing.only(WindowInsetsSides.End).asPaddingValues().calculateEndPadding(LayoutDirection.Ltr)
        val playerLayout = when {
            !wide -> PlayerLayout.Phone
            windowLayout.foldX != null -> PlayerLayout.Book(with(density) { windowLayout.foldX.toDp() })
            else -> PlayerLayout.Tablet(moreWidth = ((maxWidth - RailWidth - railStart) * 0.38f).coerceIn(300.dp, 460.dp))
        }
        // The player sits right of the rail on a tablet (the rail stays to leave by); elsewhere it takes the screen.
        val playerStart = if (playerLayout is PlayerLayout.Tablet) RailWidth + railStart else 0.dp
        // How wide the video is: the left pane on a tablet, the whole screen otherwise.
        val stageWidth = when (playerLayout) {
            is PlayerLayout.Tablet -> maxWidth - playerStart - playerLayout.moreWidth
            else -> maxWidth
        }
        val stageFloor = maxOf(StageMinAspect, stageWidth / (maxHeight * StageMaxHeightShare))
        SideEffect { player.stageFloor = stageFloor }
        // Both ends of the morph, from the same constants the mini-player and player use.
        val stageAspect = player.stageAspect
        val geometry = remember(constraints.maxWidth, constraints.maxHeight, stackBottom, statusTop, density, stageAspect, wide, playerStart, stageWidth, endInset, miniRight, miniTopShare) {
            with(density) {
                // In the player's own space (it starts [playerStart] in on a tablet).
                val left = playerStart.toPx()
                val w = constraints.maxWidth.toFloat() - left
                val h = constraints.maxHeight.toFloat()
                // The floating mini-player's resting rectangle (one rectangle: the card is just the video).
                val mw = MiniThumbWidth.toPx()
                val mh = MiniThumbHeight.toPx()
                val edge = MiniEdge.toPx()
                val lowestBottom = if (wide) h - stackBottom.toPx() else h - (stackBottom + NavHeight + MiniGap).toPx()
                val rest = miniRest(miniRight, miniTopShare, w, h, mw, mh, edge, edge + (if (wide) endInset.toPx() else 0f), lowestBottom, (statusTop + 72.dp).toPx())
                val miniCard = Rect(rest.x, rest.y, rest.x + mw, rest.y + mh)
                val thumb = miniCard
                val side = StageSideGap.toPx()
                val stageTop = (statusTop + StageTopGap).toPx()
                val sw = stageWidth.toPx()
                val stage = Rect(side, stageTop, sw - side, stageTop + (sw - 2 * side) / stageAspect)
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
            LocalChannelOpener provides { url -> showChannel(url) },
            LocalVideoOpener provides { video -> openVideo(video) },
            LocalCommentsOpener provides { target -> player.expanded = false; settingsOpen = false; commentTarget = target },
            LocalSeek provides { ms -> player.seekTo(ms) },
            LocalPlaybackPosition provides { player.positionMs },
            LocalPlaylistRemove provides { playlist, video, done -> player.removeFromPlaylist(playlist, video, done) },
            LocalPlayAll provides { videos, shuffle -> player.playAll(videos, shuffle) },
            LocalQueueAdd provides { video -> player.addToQueue(video) },
            LocalQueuePosition provides { id -> player.queue.indexOfFirst { it.id == id } + 1 },
            LocalWindowLayout provides windowLayout,
        ) {
        Box(Modifier.padding(start = pagesStart)) {
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
                    lib, contentPadding, onOpen = ::openVideo, onSearch = { tab = Tab.Search },
                    onSignIn = { signingIn = true }, onAccount = { settingsOpen = true },
                    scrollToTop = scrollToTop[Tab.Home] ?: 0,
                    shortsCard = if (wide && ShortsSetting.enabled && shorts.items.isNotEmpty()) {
                        { m -> ShortsHomeCard(shorts, onOpen = { selectTab(Tab.Shorts) }, modifier = m) }
                    } else null,
                )
                Tab.Subscriptions -> SubscriptionsScreen(
                    lib, contentPadding, onOpen = ::openVideo,
                    onSignIn = { signingIn = true }, onAccount = { settingsOpen = true },
                    scrollToTop = scrollToTop[Tab.Subscriptions] ?: 0,
                )
                Tab.Shorts -> ShortsScreen(
                    shorts, { shortsMorph.value }, overlayBottom = stackBottom + GlassHeight + 16.dp,
                    shortFocus = shortFocus, barFocus = if (tv) shortsBarFocus else null,
                )
                Tab.Posts -> PostsScreen(
                    contentPadding, onOpen = ::openVideo,
                    onSignIn = { signingIn = true }, onAccount = { settingsOpen = true },
                    scrollToTop = scrollToTop[Tab.Posts] ?: 0,
                )
                Tab.Search -> SearchScreen(search, contentPadding, onOpen = ::openVideo, scrollToTop = scrollToTop[Tab.Search] ?: 0)
                Tab.Library -> LibraryScreen(
                    lib, contentPadding, onOpen = ::openVideo, onOpenPlaylist = { openPlaylist = it },
                    onSignIn = { signingIn = true }, onAccount = { settingsOpen = true },
                    scrollToTop = scrollToTop[Tab.Library] ?: 0,
                )
            }
        }

        // A playlist opens as a page over the tabs (slides in from the right).
        AnimatedVisibility(
            visible = openPlaylist != null,
            modifier = Modifier.zIndex(if (playlistOverChannel) 1f else 0f),
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            val pl = openPlaylist
            val feed = playlistFeed
            if (pl != null && feed != null) Box(Modifier.tvPage(toRail = true)) {
                PlaylistScreen(pl, feed, contentPadding, onBack = { openPlaylist = null }, onOpen = ::openVideo)
            }
        }

        // A channel opens the same way, over everything below the mini-player.
        AnimatedVisibility(
            visible = openChannel != null,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            channelState?.let { ch -> Box(Modifier.tvPage(toRail = true)) {
                ChannelScreen(
                    ch, contentPadding, onBack = { openChannel = null },
                    // A video from a channel opened in Shorts plays in the app's player: Shorts closes.
                    onOpen = { v -> if (tab == Tab.Shorts) tab = shortsFrom; openVideo(v) },
                    onOpenShorts = ::openShorts,
                    onOpenPlaylist = { playlistOverChannel = true; openPlaylist = it },
                    onMessage = { player.actionMessage = it },
                )
            } }
        }

        // A comment notification opens the video's comments on their own, over everything below the mini-player.
        AnimatedVisibility(
            visible = commentTarget != null,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            commentTarget?.let { t ->
                Box(Modifier.tvPage(toRail = true)) {
                    NotificationCommentsScreen(
                        t,
                        onClose = { commentTarget = null },
                        onOpenVideo = { v -> commentTarget = null; openVideo(v) },
                    )
                }
            }
        }

        }
        // Wide windows: the tabs in a rail down the left (Rail.kt). Under the player, which covers it
        // on an open foldable and stands beside it on a tablet.
        if (showRail) {
            NavRail(
                selected = tab,
                tabs = Tab.entries.filter { (it != Tab.Shorts || ShortsSetting.enabled) && (it != Tab.Posts || PostsSetting.enabled) },
                onSelect = { t -> if (player.expanded) player.expanded = false; selectTab(t) },
                modifier = Modifier.graphicsLayer { alpha = 1f - settingsAway },
            )
        }

        // Ledgii's bottom vignette: the page color fades in over the last 130 dp (plus the
        // system bar), so content dissolves behind the mini-player and tab bar instead of
        // running hard into them. Drawn only — it never intercepts touches.
        val pageColor = c.surface
        val vignette = remember(pageColor) { vignetteBrush(pageColor, opaqueAtTop = false) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navBottom + 130.dp)
                // Not over a short: the video runs to the screen's edge.
                .graphicsLayer { alpha = 1f - shortsMorph.value }
                .drawBehind { drawRect(vignette) },
        )
        // Swiping home from here never scrolls the page (the tab bar above still wins its taps).
        BottomGestureGuard(Modifier.align(Alignment.BottomCenter))

        if (hasMini) {
            // A floating window: drag it anywhere and it settles on the nearer side. Flick it down (or drag
            // it down past the tab bar) to close it; tap it, or flick it up, to open the player.
            val playerStartPx = with(density) { playerStart.toPx() }
            val mw = with(density) { MiniThumbWidth.toPx() }
            val mh = with(density) { MiniThumbHeight.toPx() }
            val edge = with(density) { MiniEdge.toPx() }
            val rootW = constraints.maxWidth.toFloat()
            val w = rootW - playerStartPx
            val h = constraints.maxHeight.toFloat()
            val lowestBottom = with(density) { if (wide) h - stackBottom.toPx() else h - (stackBottom + NavHeight + MiniGap).toPx() }
            val rightEdge = edge + with(density) { if (wide) endInset.toPx() else 0f }
            val topLimit = with(density) { (statusTop + 72.dp).toPx() }
            val rest = miniRest(miniRight, miniTopShare, w, h, mw, mh, edge, rightEdge, lowestBottom, topLimit)
            val restX = rest.x + playerStartPx
            val restY = rest.y
            val lowestY = miniRest(miniRight, -1f, w, h, mw, mh, edge, rightEdge, lowestBottom, topLimit).y
            val tracker = remember { VelocityTracker() }
            LaunchedEffect(player.current?.id) { miniDragX.snapTo(0f); miniDragY.snapTo(0f) }
            // Finger up: the window goes to the nearer side (a flick carries it a little further) and
            // glides there from where it was let go.
            val settle = { x: Float, y: Float, vx: Float ->
                val toRight = x + mw / 2f + vx * 0.12f > playerStartPx + w / 2f
                val nearLowest = y >= lowestY - edge
                val share = if (nearLowest) -1f else (y / h).coerceIn(0f, 1f)
                val next = miniRest(toRight, share, w, h, mw, mh, edge, rightEdge, lowestBottom, topLimit)
                val dx = x - (next.x + playerStartPx)
                val dy = y - next.y
                miniRight = toRight
                miniTopShare = share
                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { miniDragX.snapTo(dx); miniDragY.snapTo(dy) }
                scope.launch { miniDragX.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 400f)) }
                scope.launch { miniDragY.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 400f)) }
            }
            MiniPlayer(
                player,
                onExpand = { player.expanded = true },
                showVideo = !showPlayer && !showFullscreen,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset((restX + miniDragX.value).roundToInt(), (restY + miniDragY.value).roundToInt()) }
                    .graphicsLayer {
                        // Settings open: it sinks out of sight with the tab bar. Hidden for the whole morph:
                        // the shrinking page draws the mini-player itself (it stays touchable underneath).
                        translationY = settingsAway * (size.height + 96.dp.toPx())
                        val past = (restY + miniDragY.value - lowestY) / mh
                        alpha = (if (showPlayer) 0f else 1f) * (1f - past.coerceIn(0f, 1f)) * (1f - settingsAway)
                    }
                    .pointerInput(restX, restY, miniRight, miniTopShare) {
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { total = Offset.Zero; tracker.resetTracking() },
                            onDrag = { change, amount ->
                                change.consume()
                                total += amount
                                tracker.addPosition(change.uptimeMillis, total)
                                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                    val x = (restX + miniDragX.value + amount.x).coerceIn(0f, (rootW - mw).coerceAtLeast(0f))
                                    val y = (restY + miniDragY.value + amount.y).coerceIn(topLimit, h)
                                    miniDragX.snapTo(x - restX)
                                    miniDragY.snapTo(y - restY)
                                }
                            },
                            onDragEnd = {
                                val v = tracker.calculateVelocity()
                                val x = restX + miniDragX.value
                                val y = restY + miniDragY.value
                                val vertical = abs(v.y) > abs(v.x)
                                when {
                                    (vertical && v.y > 1800f) || y > lowestY + mh * 0.45f -> scope.launch {
                                        if (!reduced) miniDragY.animateTo(miniDragY.value + mh * 1.5f, tween(180, easing = Motion.Emphasized))
                                        player.close()
                                    }
                                    vertical && v.y < -1800f -> { settle(x, y, 0f); player.expanded = true }
                                    else -> settle(x, y, v.x)
                                }
                            },
                            onDragCancel = { settle(restX + miniDragX.value, restY + miniDragY.value, 0f) },
                        )
                    },
            )
        }

        // The player morphs out of (and back into) the mini-player; see PlayerMorph.
        if (showPlayer && !showFullscreen && !tabletop && !tv) {
            Box(Modifier.padding(start = playerStart)) {
                PlayerScreen(
                    player, morph,
                    onOpen = ::openVideo,
                    onCollapse = { player.expanded = false },
                    onFullscreen = { toggleFullscreen() },
                    // With the controls on the video there's no bar over the player (it steps down below).
                    bottomClearance = if (PlayerControls.inBar) stackBottom + NavHeight + PlayerBarRaise + 16.dp else stackBottom + 16.dp,
                    layout = playerLayout,
                    statusBleed = playerStart,
                )
            }
        }

        // Over the player: while it's open the nav pill is the player's own bar (NavBar.kt).
        // It steps down out of the way of the comments panel (its composer sits there).
        val barAside by animateFloatAsState(
            if (player.sheet.isPanel && playerOpen) 1f else 0f,
            if (reduced) snap() else tween(280, easing = Motion.Emphasized), label = "barAside",
        )
        // With the controls on the video (PlayerControls) the open player has no bar:
        // the tabs step down as it opens and come back up as it collapses.
        val navAway = { maxOf(settingsAway, if (PlayerControls.inBar) barAside else maxOf(barAside, ramp(morph.progress.value, 0f, 0.5f))) }
        // A tap anywhere outside the open card / list closes it (and goes no further).
        if (playerBar.scrimActive && playerOpen) {
            Box(
                Modifier.fillMaxSize().pointerInput(playerBar) {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        playerBar.closeAll()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
            )
        }
        // A wide window has the rail for its tabs, so there the pill is only ever the open player's bar,
        // centred on the screen as everywhere (Derek, 2026-11-02). It fades in as the player opens.
        if (!tabletop && !tv && (!wide || (PlayerControls.inBar && showPlayer && !showFullscreen))) Box(Modifier.fillMaxSize()) {
        NavPill(
            selected = tab,
            onSelect = ::selectTab,
            player = player,
            morph = morph,
            controlsActive = PlayerControls.inBar && player.current != null && (player.expanded || morph.dragging),
            bar = playerBar,
            onFullscreen = { toggleFullscreen() },
            shorts = { shortsMorph.value },
            tabsEnabled = !shortsShown && !wide,
            // The fade is the bar's own (NavPill puts it on the bar's layer): alpha on this slot-sized
            // layer would crop the player's bar, which stands taller than the slot.
            fade = { (1f - navAway()) * if (wide) ramp(morph.progress.value, 0.55f, 0.95f) else 1f },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = stackBottom)
                .graphicsLayer {
                    // Moved fully off screen, so it can't catch touches meant for the composer.
                    translationY = navAway() * (NavHeight + PlayerBarRaise + stackBottom + 24.dp).toPx()
                },
        )
        }

        // The tab bar's Shorts form: ‹ and the glass pill, out of the tab bar's own spot. It steps
        // down out of the way of the comments panel (its composer sits there), as the player's bar does.
        // Wide windows: the glass bar (and its channel card) stand in the margin beside the short (Shorts.kt).
        val shortsSide = shortsMargin(windowLayout, maxWidth, maxHeight)
        Box(Modifier.align(Alignment.BottomStart).padding(start = shortsSide?.first ?: 0.dp).width(shortsSide?.second ?: maxWidth).fillMaxHeight()) {
            if (shortsShown) {
                val shortsAside by animateFloatAsState(
                    if (shorts.commentsOpen || openChannel != null) 1f else 0f,
                    if (reduced) snap() else tween(280, easing = Motion.Emphasized), label = "shortsAside",
                )
                ShortsBar(
                    shorts,
                    progress = { shortsMorph.value },
                    onBack = ::leaveShorts,
                    focus = if (tv) shortsBarFocus else null,
                    shortFocus = if (tv) shortFocus else null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = stackBottom)
                        .widthIn(max = NavWidth)
                        .fillMaxWidth()
                        .height(NavHeight)
                        .graphicsLayer {
                            translationY = shortsAside * (NavHeight + stackBottom + 24.dp).toPx()
                            alpha = 1f - shortsAside
                        },
                )
            }

            if (shortsShown) ShortsChannelMenu(shorts, bottom = stackBottom + GlassHeight + 10.dp, onChannel = ::showChannel)
        }

        if (showFullscreen) FullscreenPlayer(player, onExit = { orientation.exitFullscreen() })
        if (tv && playerOpen) TvPlayer(player, onClose = { player.expanded = false }, onOpen = player::open, onChannel = ::showChannel)
        windowLayout.tabletopY?.takeIf { tabletop }?.let { fold ->
            with(density) { TabletopPlayer(player, foldTop = fold.start.toDp(), foldBottom = fold.endInclusive.toDp()) }
        }

        // Settings comes forward as Moodii's pages do (Derek, 2026-11-02): a little scale and rise on Moodii's
        // spatial spring while it fades in, so the app behind seems to recede (its tab bar and mini-player sink away).
        AnimatedVisibility(
            visible = settingsOpen,
            enter = if (reduced) fadeIn(snap()) else
                scaleIn(MoodiiSpatial(), initialScale = 0.94f) + slideInVertically(MoodiiSpatial()) { it / 12 } + fadeIn(tween(220)),
            exit = if (reduced) fadeOut(snap()) else
                scaleOut(MoodiiSpatial(), targetScale = 0.96f) + slideOutVertically(MoodiiSpatial()) { it / 16 } + fadeOut(tween(160)),
        ) {
            Box(Modifier.tvPage()) { SettingsScreen(
                player,
                active = settingsOpen,
                onClose = { settingsOpen = false },
                onSignIn = { signingIn = true },
                onSignedOut = { lib.reset(); player.refreshAccountState() },
                // Wide: the categories on the left, the open one on the right (list-detail).
                wide = wide,
            ) }
        }
        if (signingIn) Box(Modifier.tvPage()) {
            SignInScreen(
                onDone = { signingIn = false; lib.reset(); player.refreshAccountState() },
                onCancel = { signingIn = false },
            )
        }

        when (player.sheet) {
            PlayerSheet.Quality -> QualitySheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Speed -> SpeedSheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Comments, PlayerSheet.Transcript -> Unit // a panel inside the player (PlayerScreen)
            PlayerSheet.Captions -> CaptionsSheet(player, onDismiss = { player.sheet = null }, onTranscript = { player.sheet = PlayerSheet.Transcript })
            PlayerSheet.Queue -> QueueSheet(player, onDismiss = { player.sheet = null })
            null -> Unit
        }
        optionsFor?.let { video -> VideoOptionsSheet(video, player, onDismiss = { optionsFor = null }) }
        // No taller than the comments panel opens (Derek): its top rests just under the video.
        player.playlistTarget?.let { video -> SaveToPlaylistSheet(player, video, maxHeight = maxHeight - panelRestTop(player)) }
        // Confirmations ("Added to queue") when the full player isn't showing its own.
        if (!showPlayer) {
            QueueMessage(player, Modifier.align(Alignment.BottomCenter).padding(bottom = stackBottom + NavHeight + 0.dp + 12.dp))
        }
        // Picture-in-picture: the window is just the video, over everything (kept composed
        // underneath, so expanding returns to exactly where things were).
        if (pip.active) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxSize().aspectFit(player.videoAspect)) {
                    sharedSurface(Modifier.fillMaxSize())
                    CaptionLayer(player, bottom = 4.dp)
                }
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

/** Moodii's spatial spring (Motion.spatial there): the default for anything that moves in space. */
private fun <T> MoodiiSpatial() = spring<T>(dampingRatio = 0.86f, stiffness = 650f)


private val VideoIdShape = Regex("[A-Za-z0-9_-]{11}")

/**
 * The video a youtube.com / youtu.be link asked to open (a Short plays as an ordinary video); null
 * for any other intent. The link is cleared from the intent so a rebuilt window doesn't open it twice.
 */
internal fun linkedVideo(intent: Intent?): VideoItem? {
    if (intent?.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val host = uri.host?.lowercase() ?: return null
    val segments = uri.pathSegments
    val raw = when {
        host == "youtu.be" -> segments.firstOrNull()
        host == "youtube.com" || host.endsWith(".youtube.com") -> when (segments.firstOrNull()) {
            "watch" -> uri.getQueryParameter("v")
            "shorts", "live", "embed" -> segments.getOrNull(1)
            else -> null
        }
        else -> null
    }
    val id = raw?.takeIf { VideoIdShape.matches(it) } ?: return null
    intent.data = null
    return VideoItem(
        id = id, title = "", channel = "", channelUrl = null,
        thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg", avatar = null,
        durationSec = 0, views = 0, uploaded = null,
    )
}


/**
 * Where the floating mini-player rests, in the player's own space: on the right or left side, [share] of the
 * window's height down (negative: as low as it goes, just above the tab bar). Never above [top] nor below
 * [lowestBottom].
 */
private fun miniRest(
    right: Boolean, share: Float, w: Float, h: Float, mw: Float, mh: Float,
    edge: Float, rightEdge: Float, lowestBottom: Float, top: Float,
): Offset {
    val x = if (right) w - rightEdge - mw else edge
    val lowest = (lowestBottom - mh).coerceAtLeast(top)
    val y = (if (share < 0f) lowest else share * h).coerceIn(top, lowest)
    return Offset(x, y)
}

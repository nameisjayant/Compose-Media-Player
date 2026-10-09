package com.nameisjayant.composevideos.media.videos.presentation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.nameisjayant.composevideos.R
import com.nameisjayant.composevideos.media.navigation.MediaMotion
import com.nameisjayant.composevideos.media.ui.MediaColors
import com.nameisjayant.composevideos.media.videos.data.Video
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the controls stay up after the last touch while the video plays. */
private const val CONTROLS_TIMEOUT_MS = 3_000L

/** How long the unlock button stays up after a tap on the locked screen. */
private const val UNLOCK_HINT_TIMEOUT_MS = 2_500L

/** How much the app is dimmed while the full player slides over it. */
private const val UNDERLAY_DIM = 0.4f

/** The floating window's width as a share of the screen, capped at [MiniMaxWidth]. */
private const val MINI_WIDTH_FRACTION = 0.5f
private val MiniMaxWidth = 280.dp
private val MiniMargin = 12.dp
private val MiniCornerRadius = 14.dp
private val MiniElevation = 16.dp

/** A flick faster than this finishes the shrink (or grow) whatever the distance dragged. */
private val FlingVelocity = 800.dp

private val SettleSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
private val SnapSpec = spring<Offset>(stiffness = Spring.StiffnessMediumLow)

private val TopScrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))
private val BottomScrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))

/**
 * The video player, floating over the whole app.
 *
 * Portrait: a 16:9 player at the top with the title and description below it.
 * Landscape: only the video, edge to edge, with the system bars hidden.
 * Swiping the video down shrinks it into a floating window above the tab bar that keeps playing
 * while the user browses; drag it to any corner, tap it to grow it back, or close it.
 * Picture-in-picture: only the video, with no controls, while the app is in the background.
 *
 * @param bottomInset space taken by the tab bar, which the floating window sits above.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerOverlay(
    viewModel: VideoPlayerViewModel,
    sheetState: PlayerSheetState,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player = viewModel.player
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isInPip = rememberIsInPictureInPicture()
    val activity = LocalActivity.current
    val playPause = rememberPlayPauseButtonState(player)
    val playbackState by rememberPlaybackState(player)
    val videoAspectRatio by rememberVideoAspectRatio(player)
    val collapse = sheetState.collapse
    val offscreen = sheetState.offscreen
    val miniDrag = sheetState.miniDrag
    val isCollapsing by remember { derivedStateOf { collapse.value > 0f } }
    val isMini by remember { derivedStateOf { collapse.value >= 1f } }
    val videoOnly = isLandscape || isInPip
    val qualities by rememberVideoQualities(player)
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val showSettings = settingsOpen && !isCollapsing && !isInPip
    // Full screen only: shuts out every touch on the player so a stray palm can't pause or seek.
    var lockRequested by rememberSaveable { mutableStateOf(false) }
    val isLocked = lockRequested && isLandscape && !isInPip && !isMini

    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val topInset = WindowInsets.statusBars.getTop(density)
    val geometry = remember(overlaySize, topInset, bottomInset, videoOnly, density) {
        with(density) {
            SheetGeometry(
                width = overlaySize.width.toFloat(),
                height = overlaySize.height.toFloat(),
                topInset = topInset.toFloat(),
                bottomInset = bottomInset.toPx(),
                videoOnly = videoOnly,
                miniWidth = min(overlaySize.width * MINI_WIDTH_FRACTION, MiniMaxWidth.toPx()),
                margin = MiniMargin.toPx(),
            )
        }
    }
    val flingVelocity = with(density) { FlingVelocity.toPx() }

    /** Where the video sits right now; read while laying out, so animating it skips recomposition. */
    fun videoRect(): Rect = when {
        isInPip -> geometry.full
        else -> lerp(geometry.expanded, geometry.mini(sheetState.corner).translate(miniDrag.value), collapse.value)
    }

    suspend fun close() {
        offscreen.animateTo(1f, MediaMotion.exitSpec())
        lockRequested = false
        viewModel.onIntent(VideoPlayerIntent.Close)
    }

    val onClose: () -> Unit = { scope.launch { close() } }
    val onExpand: () -> Unit = { scope.launch { collapse.animateTo(0f, SettleSpec) } }

    // Slide in when a video is opened, or grow back out of the floating window when one is picked
    // while it's shrunk. Skipped after rotation, where the request has already been handled.
    LaunchedEffect(state.openRequest) {
        if (state.openRequest == sheetState.handledRequest) return@LaunchedEffect
        sheetState.handledRequest = state.openRequest
        if (offscreen.value > 0f) {
            collapse.snapTo(0f)
            miniDrag.snapTo(Offset.Zero)
            offscreen.animateTo(0f, MediaMotion.enterSpec())
        } else {
            collapse.animateTo(0f, SettleSpec)
        }
    }

    // Back slides the full player away, following the predictive-back gesture as it goes. The
    // floating window leaves back to the screen underneath.
    PredictiveBackHandler(enabled = !isMini && !isInPip && !isLocked) { progress ->
        try {
            progress.collect { offscreen.snapTo(it.progress) }
            close()
        } catch (e: CancellationException) {
            scope.launch { offscreen.animateTo(0f, MediaMotion.enterSpec()) }
            throw e
        }
    }

    // Registered after the handler above, so Back closes the settings menu first.
    BackHandler(enabled = showSettings) { settingsOpen = false }

    // The menu doesn't follow the player into the floating window or picture-in-picture.
    LaunchedEffect(isCollapsing, isInPip) {
        if (isCollapsing || isInPip) settingsOpen = false
    }

    // The lock is for full screen; leaving it (rotating back) or hitting an error lets go of it,
    // so the user is never stuck behind it.
    LaunchedEffect(isLandscape, state.error) {
        if (!isLandscape || state.error != null) lockRequested = false
    }

    HideSystemBarsEffect(hide = isLandscape && !isInPip && !isMini)

    // Going home (or swiping up) mid-video keeps it playing in a system floating window.
    PictureInPictureEffect(
        autoEnter = !playPause.showPlay && state.error == null,
        isPlaying = !playPause.showPlay,
        videoAspectRatio = videoAspectRatio,
        videoBounds = if (isMini) geometry.mini(sheetState.corner) else geometry.expanded,
        onPlayPause = playPause::onClick,
        onNext = { viewModel.onIntent(VideoPlayerIntent.PlayNext) },
        onPrevious = { viewModel.onIntent(VideoPlayerIntent.PlayPrevious) },
    )

    // Pause when the app goes to the background. Activity recreation (e.g. a theme change) also
    // stops it, but the player lives on the ViewModel, so skip that and let playback carry on.
    // A picture-in-picture window only pauses the activity, so the video keeps playing in it;
    // closing the window stops the activity and pauses the video here.
    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose {
            if (activity?.isChangingConfigurations != true) viewModel.onBackground()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                viewModel.onIntent(VideoPlayerIntent.PlaybackFailed(error.errorCodeName))
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Dragging is tracked on this full-screen layer rather than the video, which moves and
    // shrinks under the finger. Once it's a floating window the layer steps out of the way, so
    // the app underneath takes touches again; only the window itself does.
    // Undispatched, so each step lands before the release animation starts rather than cutting it off.
    val collapseDrag = rememberDraggableState { delta ->
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            collapse.snapTo(collapse.value + delta / geometry.dragTravel)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = it }
            .then(
                // Swipes on the open settings menu shouldn't drag the player down behind it, and a
                // locked screen doesn't move at all.
                if (isMini || isInPip || showSettings || isLocked) {
                    Modifier
                } else {
                    Modifier.draggable(
                        state = collapseDrag,
                        orientation = Orientation.Vertical,
                        onDragStarted = {
                            sheetState.corner = sheetState.corner.toBottom()
                            miniDrag.snapTo(Offset.Zero)
                        },
                        onDragStopped = { velocity ->
                            val target = when {
                                velocity > flingVelocity -> 1f
                                velocity < -flingVelocity -> 0f
                                collapse.value > 0.5f -> 1f
                                else -> 0f
                            }
                            collapse.animateTo(target, SettleSpec, initialVelocity = velocity / geometry.dragTravel)
                        },
                    )
                },
            ),
    ) {
        // The player's backdrop, fading out as it shrinks to reveal the app, plus a dim over the
        // app while the full player slides in or out.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val shown = if (isInPip) 1f else 1f - collapse.value
                    val slide = if (isInPip) 0f else offscreen.value
                    drawRect(Color.Black, alpha = UNDERLAY_DIM * shown * (1f - slide))
                    translate(top = slide * size.height) {
                        drawRect(if (videoOnly) Color.Black else MediaColors.Canvas, alpha = shown)
                        // Black behind the status bar, above the video.
                        drawRect(Color.Black, size = Size(size.width, geometry.expanded.top), alpha = shown)
                    }
                },
        )

        if (!videoOnly && !isMini) {
            state.video?.let { video ->
                VideoDetails(
                    video = video,
                    upNext = state.upNext,
                    autoplay = state.autoplay,
                    onAutoplayChange = { viewModel.onIntent(VideoPlayerIntent.SetAutoplay(it)) },
                    onPlay = { viewModel.onIntent(VideoPlayerIntent.Open(it.id)) },
                    modifier = Modifier
                        .padding(top = with(density) { geometry.expanded.bottom.toDp() })
                        .graphicsLayer {
                            // Gone by half way, drifting down with the video as it leaves.
                            alpha = (1f - 2f * collapse.value).coerceAtLeast(0f)
                            translationY = offscreen.value * geometry.height + collapse.value * geometry.dragTravel
                        },
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { videoRect().topLeft.round() }
                .layout { measurable, _ ->
                    val rect = videoRect()
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            rect.width.roundToInt().coerceAtLeast(0),
                            rect.height.roundToInt().coerceAtLeast(0),
                        ),
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    if (isInPip) return@graphicsLayer
                    if (isMini) {
                        // The floating window fades and shrinks away in place when closed.
                        val scale = 1f - 0.15f * offscreen.value
                        alpha = 1f - offscreen.value
                        scaleX = scale
                        scaleY = scale
                    } else {
                        translationY = offscreen.value * geometry.height
                    }
                    shape = RoundedCornerShape(MiniCornerRadius * collapse.value)
                    clip = true
                    shadowElevation = MiniElevation.toPx() * collapse.value
                }
                .background(Color.Black),
        ) {
            VideoSurface(
                player = player,
                state = state,
                isFullScreen = isLandscape,
                showChrome = !isCollapsing && !isInPip,
                isLocked = isLocked,
                onBack = onClose,
                onOpenSettings = { settingsOpen = true },
                onLockChange = {
                    lockRequested = it
                    settingsOpen = false
                },
                onRetry = { viewModel.onIntent(VideoPlayerIntent.LoadVideo) },
                onPlayNext = { viewModel.onIntent(VideoPlayerIntent.PlayNext) },
                onCancelAutoplay = { viewModel.onIntent(VideoPlayerIntent.CancelAutoplay) },
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedVisibility(
                visible = isMini && !isInPip,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.matchParentSize(),
            ) {
                MiniPlayerControls(
                    showPlay = playPause.showPlay,
                    isEnded = playbackState == Player.STATE_ENDED,
                    playPauseEnabled = playPause.isEnabled,
                    autoplayCountdown = state.autoplayCountdown,
                    onPlayPause = playPause::onClick,
                    onNext = { viewModel.onIntent(VideoPlayerIntent.PlayNext) },
                    onPrevious = { viewModel.onIntent(VideoPlayerIntent.PlayPrevious) },
                    onClose = onClose,
                    onExpand = onExpand,
                    onDrag = { amount ->
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { miniDrag.snapTo(miniDrag.value + amount) }
                    },
                    onDragEnd = {
                        scope.launch {
                            // Snap to whichever corner the window was let go nearest.
                            val current = geometry.mini(sheetState.corner).translate(miniDrag.value)
                            val target = geometry.nearestCorner(current.center)
                            miniDrag.snapTo(current.topLeft - geometry.mini(target).topLeft)
                            sheetState.corner = target
                            miniDrag.animateTo(Offset.Zero, SnapSpec)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        PlaybackSettingsPanel(
            visible = showSettings,
            speed = state.playbackSpeed,
            maxQuality = state.maxQuality,
            qualities = qualities,
            onSpeed = {
                viewModel.onIntent(VideoPlayerIntent.SetPlaybackSpeed(it))
                settingsOpen = false
            },
            onQuality = {
                viewModel.onIntent(VideoPlayerIntent.SetQuality(it))
                settingsOpen = false
            },
            onDismiss = { settingsOpen = false },
        )
    }
}

/** Where the video sits full screen and as a floating window, in the overlay's pixels. */
private class SheetGeometry(
    val width: Float,
    val height: Float,
    topInset: Float,
    private val bottomInset: Float,
    videoOnly: Boolean,
    private val miniWidth: Float,
    private val margin: Float,
) {
    private val miniHeight = miniWidth * 9f / 16f
    private val miniTop = topInset + margin

    val full = Rect(0f, 0f, width, height)

    val expanded: Rect = if (videoOnly) full else Rect(0f, topInset, width, topInset + width * 9f / 16f)

    fun mini(corner: MiniPlayerCorner): Rect {
        val left = if (corner.isLeft) margin else width - margin - miniWidth
        val top = if (corner.isTop) miniTop else height - bottomInset - margin - miniHeight
        return Rect(left, top, left + miniWidth, top + miniHeight)
    }

    /** How far the finger travels to take the full player all the way down to the window. */
    val dragTravel: Float = (mini(MiniPlayerCorner.BottomRight).top - expanded.top).coerceAtLeast(1f)

    fun nearestCorner(point: Offset): MiniPlayerCorner {
        val left = point.x < width / 2
        return if (point.y < height / 2) {
            if (left) MiniPlayerCorner.TopLeft else MiniPlayerCorner.TopRight
        } else {
            if (left) MiniPlayerCorner.BottomLeft else MiniPlayerCorner.BottomRight
        }
    }
}

/**
 * Expand, previous, play/pause, next and close on the floating window. Tapping the expand button,
 * or anywhere else, grows it back to the full player; dragging moves it around.
 */
@Composable
private fun MiniPlayerControls(
    showPlay: Boolean,
    isEnded: Boolean,
    playPauseEnabled: Boolean,
    autoplayCountdown: Int?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() },
                ) { change, amount ->
                    change.consume()
                    currentOnDrag(amount)
                }
            }
            .clickable(onClickLabel = "Expand player", onClick = onExpand)
            .background(Color.Black.copy(alpha = 0.2f)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center),
        ) {
            MiniSkipButton(R.drawable.ic_skip_previous, "Previous video", onPrevious)
            IconButton(
                onClick = onPlayPause,
                enabled = playPauseEnabled,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MediaColors.Glass),
            ) {
                Icon(
                    painter = painterResource(
                        when {
                            isEnded -> R.drawable.ic_replay
                            showPlay -> R.drawable.ic_play
                            else -> R.drawable.ic_pause
                        },
                    ),
                    contentDescription = when {
                        isEnded -> "Replay"
                        showPlay -> "Play"
                        else -> "Pause"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            MiniSkipButton(R.drawable.ic_skip_next, "Next video", onNext)
        }
        // No room for the full up-next card here, so just say when the next video starts.
        AnimatedVisibility(
            visible = autoplayCountdown != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
        ) {
            // Holds the last number while fading out, rather than going blank.
            var shown by remember { mutableIntStateOf(autoplayCountdown ?: 0) }
            if (autoplayCountdown != null) shown = autoplayCountdown
            Text(
                text = "Next in $shown",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MediaColors.Glass)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        IconButton(
            onClick = onExpand,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(MediaColors.Glass),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_open_in_full),
                contentDescription = "Expand player",
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(MediaColors.Glass),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Close player",
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/** A previous/next button on the floating window, a little smaller than play/pause. */
@Composable
private fun MiniSkipButton(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MediaColors.Glass),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The video itself plus its tap-to-show controls, loading spinner and error state. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(
    player: Player,
    state: VideoPlayerState,
    isFullScreen: Boolean,
    showChrome: Boolean,
    isLocked: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onLockChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onPlayNext: () -> Unit,
    onCancelAutoplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val playbackState by rememberPlaybackState(player)
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    // Bumped on every touch on the controls, to restart the auto-hide countdown.
    var interactions by remember { mutableIntStateOf(0) }

    LaunchedEffect(controlsVisible, playPause.showPlay, interactions) {
        // Stay up while paused or ended, so the play button is always reachable.
        if (controlsVisible && !playPause.showPlay) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    KeepScreenOnEffect(keepOn = !playPause.showPlay)

    // While locked, a tap only brings up the unlock button for a moment.
    var unlockHintVisible by remember { mutableStateOf(false) }
    var unlockHintTaps by remember { mutableIntStateOf(0) }
    fun showUnlockHint() {
        unlockHintVisible = true
        unlockHintTaps++
    }
    LaunchedEffect(unlockHintVisible, unlockHintTaps) {
        if (unlockHintVisible) {
            delay(UNLOCK_HINT_TIMEOUT_MS)
            unlockHintVisible = false
        }
    }
    LaunchedEffect(isLocked) {
        if (isLocked) {
            controlsVisible = false
            showUnlockHint()
        } else {
            unlockHintVisible = false
        }
    }
    // Back doesn't leave a locked player either; it points at the unlock button instead.
    BackHandler(enabled = isLocked && showChrome) { showUnlockHint() }

    val haptics = LocalHapticFeedback.current
    val seekFeedback = remember { SeekFeedbackState() }
    val canSeek by rememberUpdatedState(state.error == null && !state.isLoading)
    val toggleControls = { controlsVisible = !controlsVisible }
    fun seek(side: SeekSide) {
        val target = player.currentPosition + side.direction * SEEK_STEP_MS
        val duration = player.duration
        player.seekTo(if (duration == C.TIME_UNSET) target.coerceAtLeast(0) else target.coerceIn(0, duration))
    }

    Box(
        // Picture-in-picture and the floating window handle their own taps (and have no room for
        // these controls).
        modifier = modifier
            .doubleTapToSeek(
                enabled = showChrome && !isLocked,
                seekEnabled = { canSeek },
                isSeeking = seekFeedback::isSeeking,
                onTap = toggleControls,
                onSeek = { side, position ->
                    seek(side)
                    seekFeedback.onSeek(side, position)
                    // Out of the way of the ripple; a tap brings them back.
                    controlsVisible = false
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                },
            )
            .then(
                if (isLocked && showChrome) {
                    Modifier.pointerInput(Unit) { detectTapGestures { showUnlockHint() } }
                } else {
                    Modifier
                },
            )
            .semantics {
                if (!showChrome) return@semantics
                if (isLocked) {
                    onClick(label = "Show unlock button") {
                        showUnlockHint()
                        true
                    }
                    customActions = listOf(CustomAccessibilityAction("Unlock screen") { onLockChange(false); true })
                    return@semantics
                }
                onClick(label = if (controlsVisible) "Hide controls" else "Show controls") {
                    toggleControls()
                    true
                }
                if (canSeek) {
                    customActions = listOf(
                        CustomAccessibilityAction("Rewind 10 seconds") { seek(SeekSide.Back); true },
                        CustomAccessibilityAction("Forward 10 seconds") { seek(SeekSide.Forward); true },
                    )
                }
            },
    ) {
        ContentFrame(
            player = player,
            // TextureView moves with the slide-up/down transition; a SurfaceView lags a frame behind.
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = ContentScale.Fit,
            shutter = { Box(Modifier.fillMaxSize().background(Color.Black)) },
            modifier = Modifier.fillMaxSize(),
        )

        when {
            !showChrome && (state.error != null || state.isLoading) -> Unit

            state.error != null -> PlayerError(
                message = state.error,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )

            state.isLoading -> CircularProgressIndicator(
                color = MediaColors.Accent,
                trackColor = Color.White.copy(alpha = 0.1f),
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
            )

            else -> AnimatedVisibility(
                visible = controlsVisible && showChrome && !isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    PlayerControls(
                        player = player,
                        showPlay = playPause.showPlay,
                        isEnded = playbackState == Player.STATE_ENDED,
                        playPauseEnabled = playPause.isEnabled,
                        onPlayPause = {
                            playPause.onClick()
                            interactions++
                        },
                        onSeek = { interactions++ },
                        isFullScreen = isFullScreen,
                    )
                }
            }
        }

        if (showChrome && !isLocked) SeekFeedback(seekFeedback)

        // Over the replay controls; cancelling it leaves them to replay. A locked screen still
        // counts down, it just can't be tapped.
        val nextVideo = state.upNext.firstOrNull()
        AnimatedVisibility(
            visible = showChrome && !isLocked && state.autoplayCountdown != null && nextVideo != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Holds on to the last values while fading out, once the state has moved on.
            val shownVideo = remember { nextVideo }
            var shownSeconds by remember { mutableIntStateOf(state.autoplayCountdown ?: 0) }
            state.autoplayCountdown?.let { shownSeconds = it }
            if (shownVideo != null) {
                UpNextCountdown(
                    video = shownVideo,
                    secondsLeft = shownSeconds,
                    isFullScreen = isFullScreen,
                    onPlayNow = onPlayNext,
                    onCancel = {
                        onCancelAutoplay()
                        controlsVisible = true
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = showChrome && isLocked && unlockHintVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp),
        ) {
            UnlockButton(onClick = { onLockChange(false) })
        }

        // Back stays reachable even while the controls are hidden in portrait; in full screen it
        // comes and goes with them (with the title), so nothing sits on the video.
        AnimatedVisibility(
            visible = showChrome && !isLocked && (!isFullScreen || controlsVisible || state.error != null),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isFullScreen) Modifier.background(TopScrim) else Modifier)
                    .padding(horizontal = if (isFullScreen) 16.dp else 4.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                if (isFullScreen) {
                    Text(
                        text = state.video?.title.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                // Lock and quality/speed; come and go with the controls, like on YouTube.
                AnimatedVisibility(
                    visible = isFullScreen && controlsVisible && state.error == null && !state.isLoading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(onClick = { onLockChange(true) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock),
                            contentDescription = "Lock screen",
                            tint = Color.White,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = controlsVisible && state.error == null && !state.isLoading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "Quality and playback speed",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/** The pill shown on a locked screen; tapping it gives the controls back. */
@Composable
private fun UnlockButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(MediaColors.Glass)
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(onClickLabel = "Unlock screen", onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock_open),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Screen locked · Tap to unlock",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun BoxScope.PlayerControls(
    player: Player,
    showPlay: Boolean,
    isEnded: Boolean,
    playPauseEnabled: Boolean,
    onPlayPause: () -> Unit,
    onSeek: () -> Unit,
    isFullScreen: Boolean,
) {
    // A soft dim behind the centre button so it reads on bright frames.
    Box(
        Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.25f)),
    )

    IconButton(
        onClick = onPlayPause,
        enabled = playPauseEnabled,
        modifier = Modifier
            .align(Alignment.Center)
            .size(64.dp)
            .clip(CircleShape)
            .background(MediaColors.Glass)
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
    ) {
        Icon(
            painter = painterResource(
                when {
                    isEnded -> R.drawable.ic_replay
                    showPlay -> R.drawable.ic_play
                    else -> R.drawable.ic_pause
                },
            ),
            contentDescription = when {
                isEnded -> "Replay"
                showPlay -> "Play"
                else -> "Pause"
            },
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }

    SeekBar(
        player = player,
        onSeek = onSeek,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(BottomScrim)
            .then(if (isFullScreen) Modifier.padding(horizontal = 24.dp, vertical = 12.dp) else Modifier.padding(horizontal = 12.dp)),
    )
}

@Composable
private fun SeekBar(
    player: Player,
    onSeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    // Non-null while the thumb is being dragged, so playback ticks don't fight the finger.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(200)
        }
    }

    val fraction = dragFraction ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownPosition = dragFraction?.let { (it * duration).toLong() } ?: position

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(
            text = formatTime(shownPosition),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = fraction,
            onValueChange = {
                dragFraction = it
                currentOnSeek()
            },
            onValueChangeFinished = {
                dragFraction?.let { player.seekTo((it * duration).toLong()) }
                position = player.currentPosition
                dragFraction = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = MediaColors.Accent,
                activeTrackColor = MediaColors.Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Text(
            text = formatTime(duration),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PlayerError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 32.dp),
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text("Retry", color = MediaColors.Accent) }
    }
}

/**
 * Shown over the last frame while the next video counts down: its thumbnail dimmed behind, the
 * title, a play button whose ring fills as the seconds run out, and Cancel.
 */
@Composable
private fun UpNextCountdown(
    video: Video,
    secondsLeft: Int,
    isFullScreen: Boolean,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Fills smoothly between the ticks, starting from wherever the countdown already is (e.g. after rotation).
    val ring = remember(video.id) {
        Animatable((AUTOPLAY_COUNTDOWN_SECONDS - secondsLeft).toFloat() / AUTOPLAY_COUNTDOWN_SECONDS)
    }
    LaunchedEffect(ring) {
        ring.animateTo(1f, tween(durationMillis = secondsLeft * 1_000, easing = LinearEasing))
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(video.thumbnailRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
        ) {
            Text(
                text = "Up next in $secondsLeft",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = video.title,
                color = Color.White,
                style = if (isFullScreen) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.channel,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (isFullScreen) 20.dp else 12.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MediaColors.Glass)
                    .clickable(role = Role.Button, onClickLabel = "Play ${video.title} now", onClick = onPlayNow)
                    .drawBehind {
                        val stroke = 3.dp.toPx()
                        val inset = stroke / 2
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(
                            color = Color.White.copy(alpha = 0.2f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(stroke),
                        )
                        drawArc(
                            color = MediaColors.Accent,
                            startAngle = -90f,
                            sweepAngle = 360f * ring.value,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .offset(x = 2.dp)
                        .size(28.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Title, credit and description under the player in portrait, then the queue of what plays next. */
@Composable
private fun VideoDetails(
    video: Video,
    upNext: List<Video>,
    autoplay: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    onPlay: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text(
            text = video.title,
            color = MediaColors.OnCanvas,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = video.meta,
            color = MediaColors.Muted,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MediaColors.SurfaceRaised)
                    .border(1.dp, MediaColors.Accent.copy(alpha = 0.4f), CircleShape),
            ) {
                Text(
                    text = video.channel.take(1),
                    color = MediaColors.Accent,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = video.channel,
                color = MediaColors.OnCanvas,
                style = MaterialTheme.typography.titleSmall,
            )
        }

        Spacer(Modifier.height(20.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MediaColors.Surface)
                .border(1.dp, MediaColors.Hairline, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text(
                text = "DESCRIPTION",
                color = MediaColors.Accent,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = video.description,
                color = MediaColors.OnCanvas,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (upNext.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            UpNextQueue(
                videos = upNext,
                autoplay = autoplay,
                onAutoplayChange = onAutoplayChange,
                onPlay = onPlay,
            )
        }
    }
}

/** "Up next" with the autoplay switch, and the videos still to come in the order they'll play. */
@Composable
private fun UpNextQueue(
    videos: List<Video>,
    autoplay: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    onPlay: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "UP NEXT",
                color = MediaColors.Accent,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .toggleable(value = autoplay, role = Role.Switch, onValueChange = onAutoplayChange)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = "Autoplay",
                    color = MediaColors.Muted,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(8.dp))
                // The whole row toggles, so the switch itself just shows the value.
                Switch(
                    checked = autoplay,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MediaColors.Canvas,
                        checkedTrackColor = MediaColors.Accent,
                        uncheckedThumbColor = MediaColors.Muted,
                        uncheckedTrackColor = MediaColors.SurfaceRaised,
                        uncheckedBorderColor = MediaColors.Hairline,
                    ),
                    modifier = Modifier.scale(0.8f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        videos.forEachIndexed { index, video ->
            UpNextRow(
                video = video,
                isNext = index == 0 && autoplay,
                onClick = { onPlay(video) },
            )
        }
    }
}

private val QueueThumbnailShape = RoundedCornerShape(10.dp)

@Composable
private fun UpNextRow(
    video: Video,
    isNext: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "Play ${video.title}", onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(144.dp)
                .aspectRatio(16f / 9f)
                .clip(QueueThumbnailShape)
                .background(MediaColors.Surface)
                .border(1.dp, MediaColors.Hairline, QueueThumbnailShape),
        ) {
            Image(
                painter = painterResource(video.thumbnailRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = video.duration,
                color = MediaColors.OnCanvas,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (isNext) {
                Text(
                    text = "Plays next",
                    color = MediaColors.Accent,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = video.title,
                color = MediaColors.OnCanvas,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = video.channel,
                color = MediaColors.Muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Full-screen video: hides the status and navigation bars; a swipe from the edge peeks them back. */
@Composable
private fun HideSystemBarsEffect(hide: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view, hide) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (hide) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (hide) controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** The player's [Player.getPlaybackState], kept current as it changes. */
@Composable
private fun rememberPlaybackState(player: Player): State<Int> {
    val state = remember(player) { mutableIntStateOf(player.playbackState) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.intValue = playbackState
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return state
}

/** The video's width / height, kept current as it loads; 16:9 until it's known. */
@Composable
private fun rememberVideoAspectRatio(player: Player): State<Float> {
    val state = remember(player) { mutableFloatStateOf(player.videoSize.aspectRatio()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                state.floatValue = videoSize.aspectRatio()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return state
}

private fun VideoSize.aspectRatio(): Float =
    if (width > 0 && height > 0) width * pixelWidthHeightRatio / height else 16f / 9f

/** Stops the screen dimming and locking mid-video. */
@Composable
private fun KeepScreenOnEffect(keepOn: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

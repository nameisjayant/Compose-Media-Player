package com.nameisjayant.androidpractice.media.videos.presentation

import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.nameisjayant.androidpractice.R
import com.nameisjayant.androidpractice.media.ui.MediaColors
import com.nameisjayant.androidpractice.media.videos.data.Video
import kotlinx.coroutines.delay

/** How long the controls stay up after the last touch while the video plays. */
private const val CONTROLS_TIMEOUT_MS = 3_000L

private val TopScrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))
private val BottomScrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))

/**
 * Portrait: a 16:9 player at the top with the title and description below it.
 * Landscape: only the video, edge to edge, with the system bars hidden.
 * Picture-in-picture: only the video, with no controls, while the app is in the background.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player = viewModel.player
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isInPip = rememberIsInPictureInPicture()
    val activity = LocalActivity.current
    val playPause = rememberPlayPauseButtonState(player)
    val videoAspectRatio by rememberVideoAspectRatio(player)
    var videoBounds by remember { mutableStateOf<Rect?>(null) }

    HideSystemBarsEffect(hide = isLandscape && !isInPip)

    // Going home (or swiping up) mid-video keeps it playing in a floating window.
    PictureInPictureEffect(
        autoEnter = !playPause.showPlay && state.error == null,
        isPlaying = !playPause.showPlay,
        videoAspectRatio = videoAspectRatio,
        videoBounds = videoBounds,
        onPlayPause = playPause::onClick,
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

    val onRetry = { viewModel.onIntent(VideoPlayerIntent.LoadVideo) }
    val videoOnly = isLandscape || isInPip

    // One layout for every mode, so the video surface stays put (no black flash) as the window
    // rotates or shrinks into picture-in-picture; only its size changes.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (videoOnly) Color.Black else MediaColors.Canvas),
    ) {
        VideoSurface(
            player = player,
            state = state,
            isFullScreen = isLandscape,
            isInPip = isInPip,
            onBack = onBack,
            onRetry = onRetry,
            modifier = Modifier
                .then(
                    if (videoOnly) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .background(Color.Black)
                            .statusBarsPadding()
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    },
                )
                .onGloballyPositioned { videoBounds = it.boundsInWindow() },
        )
        if (!videoOnly) state.video?.let { VideoDetails(it) }
    }
}

/** The video itself plus its tap-to-show controls, loading spinner and error state. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(
    player: Player,
    state: VideoPlayerState,
    isFullScreen: Boolean,
    isInPip: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
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

    Box(
        modifier = modifier.clickable(
            // The picture-in-picture window handles its own taps (and has no room for controls).
            enabled = !isInPip,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = if (controlsVisible) "Hide controls" else "Show controls",
        ) { controlsVisible = !controlsVisible },
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
            isInPip -> Unit

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
                visible = controlsVisible,
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

        // Back stays reachable even while the controls are hidden in portrait; in full screen it
        // comes and goes with them (with the title), so nothing sits on the video.
        AnimatedVisibility(
            visible = !isInPip && (!isFullScreen || controlsVisible || state.error != null),
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
                    )
                }
            }
        }
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

/** Title, credit and description under the player in portrait. */
@Composable
private fun VideoDetails(video: Video, modifier: Modifier = Modifier) {
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

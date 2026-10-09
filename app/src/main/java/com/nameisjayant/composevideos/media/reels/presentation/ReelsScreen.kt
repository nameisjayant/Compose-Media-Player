package com.nameisjayant.composevideos.media.reels.presentation

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nameisjayant.composevideos.R
import com.nameisjayant.composevideos.media.reels.data.Reel
import com.nameisjayant.composevideos.media.reels.data.commentCount
import com.nameisjayant.composevideos.media.ui.MediaColors
import com.nameisjayant.composevideos.media.ui.MediaTheme
import kotlinx.coroutines.launch

/** Stateful entry point: wires the ViewModel's state and effects into [ReelsContent]. */
@Composable
fun ReelsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ReelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ReelsEffect.ShowMessage -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel,
                        // Leave time to reach Undo; plain notices go quickly.
                        duration = if (effect.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) effect.action?.let(viewModel::onIntent)
                }
                is ReelsEffect.ShareReel -> {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, effect.text)
                    context.startActivity(Intent.createChooser(send, "Share reel"))
                }
            }
        }
    }

    ReelsContent(
        state = state,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}


@Composable
fun ReelsContent(
    state: ReelsState,
    onIntent: (ReelsIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(
                color = MediaColors.Accent,
                trackColor = Color.White.copy(alpha = 0.1f),
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
            )

            state.error != null -> ReelsMessage(
                title = "Something went quiet",
                message = state.error,
                actionLabel = "Try again",
                onAction = { onIntent(ReelsIntent.LoadReels) },
                modifier = Modifier.align(Alignment.Center),
            )

            state.reels.isEmpty() -> ReelsMessage(
                title = "You're all caught up",
                message = "No more reels in your feed.",
                actionLabel = "Refresh",
                onAction = { onIntent(ReelsIntent.LoadReels) },
                modifier = Modifier.align(Alignment.Center),
            )

            else -> ReelsPager(state = state, onIntent = onIntent, contentPadding = contentPadding)
        }

        // Holding the video clears the chrome so only the reel is on screen, like Instagram.
        AnimatedVisibility(
            visible = state.hold == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReelsHeader(
                position = if (state.reels.isEmpty()) null else state.currentIndex + 1 to state.reels.size,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(16.dp),
                containerColor = MediaColors.SurfaceRaised,
                contentColor = MediaColors.OnCanvas,
                actionColor = MediaColors.Accent,
            )
        }
    }

    val commentsReel = state.reels.firstOrNull { it.id == state.commentsReelId }
    if (commentsReel != null) {
        ReelCommentsSheet(
            reel = commentsReel,
            likedCommentIds = state.likedCommentIds,
            onPost = { text, parentId -> onIntent(ReelsIntent.PostComment(commentsReel.id, text, parentId)) },
            onToggleLike = { onIntent(ReelsIntent.ToggleCommentLike(it)) },
            onDelete = { onIntent(ReelsIntent.DeleteComment(commentsReel.id, it)) },
            onDismiss = { onIntent(ReelsIntent.CloseComments) },
        )
    }

    val optionsReel = state.reels.firstOrNull { it.id == state.optionsReelId }
    if (optionsReel != null) {
        ReelOptionsSheet(
            onNotInterested = { onIntent(ReelsIntent.NotInterested(optionsReel.id)) },
            onReport = { reason -> onIntent(ReelsIntent.Report(optionsReel.id, reason)) },
            onDismiss = { onIntent(ReelsIntent.CloseOptions) },
        )
    }
}

/** Title over a soft top scrim, so it stays legible on bright videos. */
@Composable
private fun ReelsHeader(
    position: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(HeaderScrim)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
    ) {
        Text(
            text = "Reels",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
        )
        Box(
            Modifier
                .padding(start = 6.dp)
                .offset(y = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MediaColors.Accent),
        )
        Spacer(Modifier.weight(1f))
        if (position != null) {
            val (current, total) = position
            Text(
                text = "$current / $total",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Reels kept prepared on each side of the current one. Each holds an ExoPlayer with its own
 * video decoder, and phones only have a handful; local files prepare fast enough that one
 * neighbour per side still makes swipes instant.
 */
private const val PRELOAD_PAGES = 1

/**
 * Reels after the current one kept buffered. The first is the composed neighbour above; the rest
 * are warmed on idle pooled players, so even the page that composes mid-swipe starts instantly.
 */
private const val PRELOAD_AHEAD = 2

/** Fraction of a page a slow drag must cover to move on; lower than the default half, like Instagram. */
private const val SNAP_THRESHOLD = 0.25f

/** Width of each side strip where a hold plays fast instead of pausing, as a fraction of the reel. */
private const val HOLD_EDGE_FRACTION = 0.25f

// Allocated once instead of on every recomposition of the header and each reel.
private val HeaderScrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))
private val InfoScrim = Brush.verticalGradient(
    0f to Color.Transparent,
    0.45f to Color.Black.copy(alpha = 0.45f),
    1f to Color.Black.copy(alpha = 0.85f),
)
/** Same feel as a fling's snap, so an auto-scroll looks like a swipe. */
private val PageSnapSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
private val AvatarRing = Brush.linearGradient(listOf(MediaColors.Accent, MediaColors.AccentDeep))

@Composable
private fun ReelsPager(
    state: ReelsState,
    onIntent: (ReelsIntent) -> Unit,
    contentPadding: PaddingValues,
) {
    val pagerState = rememberPagerState(initialPage = state.currentIndex) { state.reels.size }
    val playerPool = rememberReelPlayerPool()
    val scope = rememberCoroutineScope()

    // Report only fully settled pages, so a reel starts once the swipe finishes (like Instagram).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { onIntent(ReelsIntent.PageSettled(it)) }
    }
    // Removing or restoring a reel shifts the pages; the pager would follow the neighbour by key,
    // so put it back on the page the ViewModel chose (the restored reel after an Undo). Unconditional:
    // this can run before the pager re-anchors, when currentPage still looks right.
    LaunchedEffect(pagerState, state.reels.size) {
        pagerState.scrollToPage(state.currentIndex)
    }
    // Warm the reels ahead only once a swipe settles, keeping player setup off the swipe itself.
    LaunchedEffect(pagerState, playerPool, state.reels) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            playerPool.preload(state.reels.drop(page + 1).take(PRELOAD_AHEAD))
        }
    }

    VerticalPager(
        state = pagerState,
        // Keep the reels around the current one alive and buffered so swiping feels instant.
        beyondViewportPageCount = PRELOAD_PAGES,
        // One page per fling, eased by a critically damped spring that carries the finger's velocity.
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1),
            snapAnimationSpec = PageSnapSpec,
            snapPositionalThreshold = SNAP_THRESHOLD,
        ),
        key = { state.reels[it].id },
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val reel = state.reels[page]
        val isCurrent = page == state.currentIndex
        ReelItem(
            playerPool = playerPool,
            reel = reel,
            isCurrent = isCurrent,
            isPaused = state.isPaused,
            hold = if (isCurrent) state.hold else null,
            isMuted = state.isMuted,
            speed = state.playbackSpeed,
            // With auto-scroll on a reel plays once, unless there's nowhere to go: the last reel,
            // or an open sheet the next reel would slide out from under.
            loop = !state.autoScroll || page == state.reels.lastIndex ||
                state.commentsReelId != null || state.optionsReelId != null,
            autoScroll = state.autoScroll,
            isLiked = reel.id in state.likedReelIds,
            onTogglePlay = { onIntent(ReelsIntent.TogglePlayPause) },
            onHoldStart = { onIntent(ReelsIntent.HoldStarted(it)) },
            onHoldEnd = { onIntent(ReelsIntent.HoldReleased) },
            onToggleLike = { onIntent(ReelsIntent.ToggleLike(reel.id)) },
            onDoubleTapLike = { onIntent(ReelsIntent.DoubleTapLike(reel.id)) },
            onOpenComments = { onIntent(ReelsIntent.OpenComments(reel.id)) },
            onShare = { onIntent(ReelsIntent.Share(reel.id)) },
            onOpenOptions = { onIntent(ReelsIntent.OpenOptions(reel.id)) },
            onToggleMute = { onIntent(ReelsIntent.ToggleMute) },
            onCycleSpeed = { onIntent(ReelsIntent.CycleSpeed) },
            onToggleAutoScroll = { onIntent(ReelsIntent.ToggleAutoScroll) },
            onEnded = {
                if (isCurrent) scope.launch { pagerState.animateScrollToPage(page + 1, animationSpec = PageSnapSpec) }
            },
            onPlaybackError = { reason -> onIntent(ReelsIntent.PlaybackFailed(reel.id, reason)) },
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun ReelItem(
    playerPool: ReelPlayerPool,
    reel: Reel,
    isCurrent: Boolean,
    isPaused: Boolean,
    hold: ReelHold?,
    isMuted: Boolean,
    speed: Float,
    loop: Boolean,
    autoScroll: Boolean,
    isLiked: Boolean,
    onTogglePlay: () -> Unit,
    onHoldStart: (ReelHold) -> Unit,
    onHoldEnd: () -> Unit,
    onToggleLike: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onOpenOptions: () -> Unit,
    onToggleMute: () -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onEnded: () -> Unit,
    onPlaybackError: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    var progress by remember(reel.id) { mutableFloatStateOf(0f) }
    // Dragging the progress bar pauses the reel; releasing it queues a seek for the player.
    var isScrubbing by remember(reel.id) { mutableStateOf(false) }
    var seekTarget by remember(reel.id) { mutableStateOf<Float?>(null) }
    // Where the last double-tap landed; bumping the key replays the heart even on the same spot.
    var burstAt by remember { mutableStateOf(Offset.Zero) }
    var burstKey by remember { mutableIntStateOf(0) }
    // The gesture detector outlives recompositions; read the latest callbacks instead of restarting it.
    val currentOnTap by rememberUpdatedState(onTogglePlay)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTapLike)
    val currentOnHoldStart by rememberUpdatedState(onHoldStart)
    val currentOnHoldEnd by rememberUpdatedState(onHoldEnd)
    val haptics = LocalHapticFeedback.current

    Box(Modifier.fillMaxSize()) {
        ReelPlayer(
            pool = playerPool,
            reel = reel,
            // Holding fast-forward plays even a paused reel, then leaves it paused on release.
            shouldPlay = isCurrent && !isScrubbing && when (hold) {
                ReelHold.Pause -> false
                ReelHold.FastForward -> true
                null -> !isPaused
            },
            isMuted = isMuted,
            speed = if (hold == ReelHold.FastForward) HoldSpeed else speed,
            loop = loop,
            onPlaybackError = onPlaybackError,
            onProgress = { progress = it },
            seekTo = seekTarget,
            onSeeked = { seekTarget = null },
            onEnded = onEnded,
        )

        // Transparent layer above the video: tap toggles play/pause, double-tap likes, and a hold
        // pauses (middle) or plays fast (edges) until the finger lifts.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var holding = false
                    detectTapGestures(
                        onPress = {
                            // Also returns when the pager takes the gesture over for a swipe.
                            tryAwaitRelease()
                            if (holding) {
                                holding = false
                                currentOnHoldEnd()
                            }
                        },
                        onLongPress = {
                            val edge = size.width * HOLD_EDGE_FRACTION
                            val fast = it.x < edge || it.x > size.width - edge
                            holding = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnHoldStart(if (fast) ReelHold.FastForward else ReelHold.Pause)
                        },
                        onTap = { currentOnTap() },
                        onDoubleTap = {
                            burstAt = it
                            burstKey++
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            currentOnDoubleTap()
                        },
                    )
                },
        )

        if (burstKey > 0) HeartBurst(at = burstAt, key = burstKey)

        AnimatedVisibility(
            visible = isCurrent && isPaused && hold == null,
            enter = fadeIn() + scaleIn(initialScale = 1.3f),
            exit = fadeOut() + scaleOut(targetScale = 1.3f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = "Paused",
                    tint = Color.White,
                    // The triangle's visual centre sits left of its box; nudge it right.
                    modifier = Modifier
                        .offset(x = 2.dp)
                        .size(36.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = hold == ReelHold.FastForward,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
        ) {
            FastForwardBadge()
        }

        AnimatedVisibility(
            visible = hold == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            ReelInfo(
                reel = reel,
                isMuted = isMuted,
                speed = speed,
                autoScroll = autoScroll,
                isLiked = isLiked,
                onToggleLike = onToggleLike,
                onOpenComments = onOpenComments,
                onShare = onShare,
                onOpenOptions = onOpenOptions,
                // Read lazily so per-frame progress only redraws the bar, not the whole reel.
                progress = { if (isCurrent) progress else 0f },
                isScrubbing = isScrubbing,
                onScrubStart = { isScrubbing = true },
                onScrubEnd = { fraction ->
                    isScrubbing = false
                    if (fraction != null) {
                        // Jump the bar now; the player only reports again once it resumes.
                        progress = fraction
                        seekTarget = fraction
                    }
                },
                onToggleMute = onToggleMute,
                onCycleSpeed = onCycleSpeed,
                onToggleAutoScroll = onToggleAutoScroll,
                contentPadding = contentPadding,
            )
        }
    }
}

/** Glass pill shown while a hold plays the reel at [HoldSpeed]. */
@Composable
private fun FastForwardBadge() {
    Text(
        text = "${HoldSpeed.toInt()}x speed  ››",
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ReelInfo(
    reel: Reel,
    isMuted: Boolean,
    speed: Float,
    autoScroll: Boolean,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onOpenOptions: () -> Unit,
    progress: () -> Float,
    isScrubbing: Boolean,
    onScrubStart: () -> Unit,
    onScrubEnd: (fraction: Float?) -> Unit,
    onToggleMute: () -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // Scrubbing clears the caption and actions so the preview has the screen, like Instagram.
    val chromeAlpha = animateFloatAsState(if (isScrubbing) 0f else 1f, label = "chromeAlpha")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(InfoScrim)
            // The gradient extends under the floating bar; the text stays above it.
            .padding(contentPadding)
            .padding(top = 72.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .padding(start = 20.dp, end = 16.dp)
                .graphicsLayer { alpha = chromeAlpha.value },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                ChannelRow(reel.channel)
                Text(
                    text = reel.title,
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LikeButton(
                    isLiked = isLiked,
                    count = reel.likeCount + if (isLiked) 1 else 0,
                    onClick = onToggleLike,
                )
                ReelAction(
                    icon = R.drawable.ic_comment,
                    label = formatCount(reel.commentCount),
                    contentDescription = "Comments",
                    onClick = onOpenComments,
                )
                ReelAction(
                    icon = R.drawable.ic_share,
                    label = formatCount(reel.shareCount),
                    contentDescription = "Share",
                    onClick = onShare,
                )
                // Instagram keeps its overflow menu on the rail too, without a count.
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = "More options",
                    tint = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = "More options", onClick = onOpenOptions)
                        .padding(6.dp)
                        .size(24.dp),
                )
                Spacer(Modifier.height(4.dp))
                SpeedButton(speed = speed, onClick = onCycleSpeed)
                GlassIconButton(
                    // Shows the current mode: looping the reel, or moving on to the next.
                    icon = if (autoScroll) R.drawable.ic_auto_scroll else R.drawable.ic_replay,
                    contentDescription = if (autoScroll) "Turn off auto-scroll" else "Turn on auto-scroll",
                    onClick = onToggleAutoScroll,
                    isActive = autoScroll,
                )
                GlassIconButton(
                    icon = if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    onClick = onToggleMute,
                )
            }
        }
        // The bar sits in a taller touch strip; these spacings keep the hairline where it was.
        Spacer(Modifier.height(4.dp))
        ReelProgress(
            videoRes = reel.videoRes,
            progress = progress,
            onScrubStart = onScrubStart,
            onScrubEnd = onScrubEnd,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun ChannelRow(channel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Monogram avatar inside a champagne ring.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .border(
                    width = 1.5.dp,
                    brush = AvatarRing,
                    shape = CircleShape,
                )
                .padding(3.dp)
                .clip(CircleShape)
                .background(MediaColors.SurfaceRaised),
        ) {
            Text(
                text = channel.first().uppercase(),
                color = MediaColors.Accent,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                text = channel,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${channel.replace(" ", "").lowercase()}",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

/** Instagram's like red. */
internal val LikeRed = Color(0xFFFF3040)

/** Icon over its count, no background, like Instagram's right rail. */
@Composable
private fun ReelAction(
    icon: Int,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    iconModifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = contentDescription, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = iconModifier.size(28.dp),
        )
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Heart that pops and turns red when liked. */
@Composable
private fun LikeButton(isLiked: Boolean, count: Int, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(isLiked) {
        // Skip the pop when the page first composes with an existing like.
        if (firstRun) { firstRun = false; return@LaunchedEffect }
        pop.snapTo(0.7f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    val tint by animateColorAsState(if (isLiked) LikeRed else Color.White, label = "likeTint")
    val haptics = LocalHapticFeedback.current
    ReelAction(
        icon = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
        label = formatCount(count),
        contentDescription = if (isLiked) "Unlike" else "Like",
        onClick = {
            haptics.performHapticFeedback(if (isLiked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            onClick()
        },
        tint = tint,
        iconModifier = Modifier.scale(pop.value),
    )
}

private const val BURST_SIZE_DP = 96

/** Big red heart that pops at the double-tap point, then floats up and fades. */
@Composable
private fun HeartBurst(at: Offset, key: Int) {
    val scale = remember(key) { Animatable(0f) }
    val alpha = remember(key) { Animatable(1f) }
    val rise = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        scale.animateTo(1f, keyframes {
            durationMillis = 350
            1.25f at 180
            0.95f at 280
        })
        launch { rise.animateTo(-60f, tween(400)) }
        alpha.animateTo(0f, tween(400))
    }
    val half = with(LocalDensity.current) { (BURST_SIZE_DP / 2).dp.roundToPx() }
    Icon(
        painter = painterResource(R.drawable.ic_heart_filled),
        contentDescription = null,
        tint = LikeRed,
        modifier = Modifier
            .offset { IntOffset(at.x.toInt() - half, at.y.toInt() - half) }
            .size(BURST_SIZE_DP.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                translationY = rise.value
            },
    )
}

/** 950 → "950", 12_400 → "12.4K", 3_100_000 → "3.1M". */
internal fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> trimDecimal(n / 1_000_000f) + "M"
    n >= 10_000 -> (n / 1_000).toString() + "K"
    n >= 1_000 -> trimDecimal(n / 1_000f) + "K"
    else -> n.toString()
}

private fun trimDecimal(v: Float): String =
    ((v * 10).toInt() / 10f).let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }

@Composable
private fun GlassIconButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
) {
    // Active matches a non-default SpeedButton: champagne instead of white glass.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isActive) MediaColors.Accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.14f))
            .border(
                1.dp,
                if (isActive) MediaColors.Accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.18f),
                CircleShape,
            )
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (isActive) MediaColors.Accent else Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Glass pill showing the current speed; each tap steps to the next one in [PlaybackSpeeds]. */
@Composable
private fun SpeedButton(speed: Float, onClick: () -> Unit) {
    val label = (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "x"
    val isDefault = speed == 1f
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isDefault) Color.White.copy(alpha = 0.14f) else MediaColors.Accent.copy(alpha = 0.22f))
            .border(
                1.dp,
                if (isDefault) Color.White.copy(alpha = 0.18f) else MediaColors.Accent.copy(alpha = 0.6f),
                CircleShape,
            )
            .clickable(onClickLabel = "Change playback speed") {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            },
    ) {
        Text(
            text = label,
            color = if (isDefault) Color.White else MediaColors.Accent,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Centred notice with one action: load errors and an emptied feed. */
@Composable
private fun ReelsMessage(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(32.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MediaColors.Accent.copy(alpha = 0.12f))
                .border(1.dp, MediaColors.Accent.copy(alpha = 0.3f), CircleShape),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_reels),
                contentDescription = null,
                tint = MediaColors.Accent,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onAction,
            border = BorderStroke(1.dp, MediaColors.Accent.copy(alpha = 0.6f)),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
        ) {
            Text(actionLabel, color = MediaColors.Accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview
@Composable
private fun ReelsErrorPreview() {
    MediaTheme {
        ReelsContent(
            state = ReelsState(isLoading = false, error = "Couldn't load reels"),
            onIntent = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

package com.nameisjayant.composevideos.media.reels.presentation

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.nameisjayant.composevideos.media.reels.data.Reel

/** Idle players kept warm for reuse; anything beyond this is released. */
private const val MAX_IDLE_PLAYERS = 2

/**
 * Recycles [ExoPlayer]s between pager pages. Building a player (and its decoder) and releasing
 * one both run on the main thread, and the pager does it mid-swipe as pages enter and leave the
 * preload window, which drops frames. Reusing players turns that into a cheap media item swap.
 */
@OptIn(UnstableApi::class)
class ReelPlayerPool(private val context: Context) {
    private val idle = ArrayDeque<ExoPlayer>()
    private val leased = mutableSetOf<ExoPlayer>()

    /** Hands out a player loaded with [reel], preferring an idle one that already has it prepared. */
    fun lease(reel: Reel): PlayerLease {
        val player = idle.firstOrNull { it.currentMediaItem?.mediaId == reel.id }?.also(idle::remove)
            ?: idle.removeFirstOrNull()
            ?: buildPlayer()
        if (player.currentMediaItem?.mediaId != reel.id) {
            player.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(reel.id)
                    .setUri(RawResourceDataSource.buildRawResourceUri(reel.videoRes))
                    .build(),
            )
            player.prepare()
        }
        leased += player
        return PlayerLease(player)
    }

    private fun recycle(player: ExoPlayer) {
        if (!leased.remove(player)) return
        player.playWhenReady = false
        if (idle.size < MAX_IDLE_PLAYERS) idle.addFirst(player) else player.release()
    }

    fun releaseAll() {
        (idle + leased).forEach(ExoPlayer::release)
        idle.clear()
        leased.clear()
    }

    private fun buildPlayer(): ExoPlayer =
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
            .apply {
                // Reels loop forever.
                repeatMode = Player.REPEAT_MODE_ONE
            }

    /** Returns its player to the pool when the page leaves composition. */
    inner class PlayerLease(val player: ExoPlayer) : RememberObserver {
        override fun onRemembered() = Unit
        override fun onForgotten() = recycle(player)
        override fun onAbandoned() = recycle(player)
    }
}

@Composable
fun rememberReelPlayerPool(): ReelPlayerPool {
    val context = LocalContext.current
    val pool = remember(context) { ReelPlayerPool(context) }
    DisposableEffect(pool) { onDispose { pool.releaseAll() } }
    return pool
}

/**
 * Plays one bundled reel on a player leased from [pool]. The pager keeps neighbours composed, so
 * their players are already prepared with the first frame on screen; only the page with
 * [shouldPlay] actually plays.
 */
@OptIn(UnstableApi::class)
@Composable
fun ReelPlayer(
    pool: ReelPlayerPool,
    reel: Reel,
    shouldPlay: Boolean,
    isMuted: Boolean,
    speed: Float,
    onPlaybackError: (reason: String) -> Unit,
    modifier: Modifier = Modifier,
    onProgress: (fraction: Float) -> Unit = {},
) {
    val player = remember(pool, reel.id) { pool.lease(reel) }.player

    // Pause when the app goes to the background, resume when it comes back.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val play = shouldPlay && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val currentOnError by rememberUpdatedState(onPlaybackError)
    val currentOnProgress by rememberUpdatedState(onProgress)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                currentOnError(error.errorCodeName)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, play) {
        player.playWhenReady = play
        // Sample once per frame so the progress bar glides instead of stepping.
        while (play) {
            withFrameNanos { }
            val duration = player.duration
            if (duration > 0) currentOnProgress((player.currentPosition.toFloat() / duration).coerceIn(0f, 1f))
        }
    }
    LaunchedEffect(player, isMuted) {
        player.volume = if (isMuted) 0f else 1f
    }
    LaunchedEffect(player, speed) {
        // Re-applied on every lease, since pooled players keep the last reel's speed.
        player.setPlaybackSpeed(speed)
    }

    ContentFrame(
        player = player,
        // TextureView moves with the pager's swipe; SurfaceView can lag a frame behind it.
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = ContentScale.Crop,
        shutter = { Box(Modifier.fillMaxSize().background(Color.Black)) },
        modifier = modifier.fillMaxSize(),
    )
}

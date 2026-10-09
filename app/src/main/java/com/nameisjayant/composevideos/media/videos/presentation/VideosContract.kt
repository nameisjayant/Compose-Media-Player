package com.nameisjayant.composevideos.media.videos.presentation

import com.nameisjayant.composevideos.media.videos.data.Video

/** Single source of truth for the Videos list. */
data class VideosState(
    val isLoading: Boolean = true,
    val videos: List<Video> = emptyList(),
    val error: String? = null,
)

sealed interface VideosIntent {
    data object LoadVideos : VideosIntent
}

/**
 * Single source of truth for the player, which floats over the whole app; playback itself lives
 * on the ViewModel's player. Whether it's full screen or shrunk to the floating window is UI state.
 */
data class VideoPlayerState(
    val isOpen: Boolean = false,
    val videoId: String? = null,
    /** Bumped on every [VideoPlayerIntent.Open], so the UI expands the player even if it's already open. */
    val openRequest: Int = 0,
    val isLoading: Boolean = false,
    val video: Video? = null,
    val error: String? = null,
    /** Kept from one video to the next, like the quality below. */
    val playbackSpeed: Float = 1f,
    /** The tallest video track to play, e.g. 480; null picks the best one automatically. */
    val maxQuality: Int? = null,
    /** The rest of the list after this video, in play order, wrapping round; the first one plays next. */
    val upNext: List<Video> = emptyList(),
    /** Whether the next video starts by itself when this one ends. Kept like speed and quality. */
    val autoplay: Boolean = true,
    /** Seconds left before the next video starts, while the end-of-video countdown runs; else null. */
    val autoplayCountdown: Int? = null,
)

sealed interface VideoPlayerIntent {
    data class Open(val videoId: String) : VideoPlayerIntent
    data object Close : VideoPlayerIntent
    /** Moves on to the next video in the list, wrapping round, without expanding the player. */
    data object PlayNext : VideoPlayerIntent
    /** Goes back to the previous video in the list, wrapping round, without expanding the player. */
    data object PlayPrevious : VideoPlayerIntent
    data object LoadVideo : VideoPlayerIntent
    data class PlaybackFailed(val reason: String) : VideoPlayerIntent
    data class SetPlaybackSpeed(val speed: Float) : VideoPlayerIntent
    /** [height] null goes back to Auto. */
    data class SetQuality(val height: Int?) : VideoPlayerIntent
    data class SetAutoplay(val enabled: Boolean) : VideoPlayerIntent
    /** Stops the end-of-video countdown, leaving the video on its last frame; autoplay stays on. */
    data object CancelAutoplay : VideoPlayerIntent
}

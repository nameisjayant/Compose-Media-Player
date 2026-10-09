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
)

sealed interface VideoPlayerIntent {
    data class Open(val videoId: String) : VideoPlayerIntent
    data object Close : VideoPlayerIntent
    data object LoadVideo : VideoPlayerIntent
    data class PlaybackFailed(val reason: String) : VideoPlayerIntent
}

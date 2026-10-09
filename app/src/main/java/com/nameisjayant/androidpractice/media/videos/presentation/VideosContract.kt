package com.nameisjayant.androidpractice.media.videos.presentation

import com.nameisjayant.androidpractice.media.videos.data.Video

/** Single source of truth for the Videos list. */
data class VideosState(
    val isLoading: Boolean = true,
    val videos: List<Video> = emptyList(),
    val error: String? = null,
)

sealed interface VideosIntent {
    data object LoadVideos : VideosIntent
}

/** Single source of truth for the player screen; playback itself lives on the ViewModel's player. */
data class VideoPlayerState(
    val isLoading: Boolean = true,
    val video: Video? = null,
    val error: String? = null,
)

sealed interface VideoPlayerIntent {
    data object LoadVideo : VideoPlayerIntent
    data class PlaybackFailed(val reason: String) : VideoPlayerIntent
}

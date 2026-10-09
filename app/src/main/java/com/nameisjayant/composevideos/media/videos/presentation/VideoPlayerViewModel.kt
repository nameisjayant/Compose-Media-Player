package com.nameisjayant.composevideos.media.videos.presentation

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.nameisjayant.composevideos.media.videos.data.SeekPreviewSource
import com.nameisjayant.composevideos.media.videos.data.SeekPreviews
import com.nameisjayant.composevideos.media.videos.data.Video
import com.nameisjayant.composevideos.media.videos.data.VideosRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long the up-next countdown runs at the end of a video before the next one starts. */
internal const val AUTOPLAY_COUNTDOWN_SECONDS = 5

/**
 * Owns the [player] for the whole activity, so the video keeps playing in the floating window
 * while the user moves around the app, and anything that recreates the activity carries on from
 * the same spot instead of rebuffering from the start.
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: VideosRepository,
    private val seekPreviewSource: SeekPreviewSource,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoPlayerState())
    val state: StateFlow<VideoPlayerState> = _state.asStateFlow()

    private val _seekPreviews = MutableStateFlow<SeekPreviews?>(null)

    /**
     * Frames of the open video for the seek bar to show while scrubbing, filling in as they decode;
     * null until the first one has. Kept out of [state] so each new frame doesn't touch the rest.
     */
    val seekPreviews: StateFlow<SeekPreviews?> = _seekPreviews.asStateFlow()

    val player: Player = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        // Pause rather than blast audio from the speaker when headphones are unplugged.
        .setHandleAudioBecomingNoisy(true)
        .build()

    /** Whether to (re)start playback the next time the app is in the foreground. */
    private var resumeOnForeground = false

    /** Whether to restart the end-of-video countdown the next time the app is in the foreground. */
    private var countdownOnForeground = false

    private var loadJob: Job? = null

    private var countdownJob: Job? = null

    private var previewJob: Job? = null

    /** The video [seekPreviews] are (being) decoded from, so reloading the same one keeps them. */
    private var previewVideoId: String? = null

    init {
        // Counts down to the next video when one ends; a replay or seek off the end calls it off.
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) startCountdown() else cancelCountdown()
            }
        })
        savedStateHandle.get<Boolean>(KEY_AUTOPLAY)?.let { autoplay -> _state.update { it.copy(autoplay = autoplay) } }
        // Speed and quality carry on from where they were set, even after process death.
        savedStateHandle.get<Float>(KEY_SPEED)?.let(::setPlaybackSpeed)
        savedStateHandle.get<Int>(KEY_QUALITY)?.let(::setQuality)
        // Back after process death: reopen the video and pick playback up once the app is visible.
        savedStateHandle.get<String>(KEY_VIDEO_ID)?.let {
            open(it)
            resumeOnForeground = true
        }
    }

    fun onIntent(intent: VideoPlayerIntent) {
        when (intent) {
            is VideoPlayerIntent.Open -> {
                open(intent.videoId)
                player.playWhenReady = true
            }
            VideoPlayerIntent.Close -> close()
            VideoPlayerIntent.PlayNext -> skip(1)
            VideoPlayerIntent.PlayPrevious -> skip(-1)
            VideoPlayerIntent.LoadVideo -> loadVideo()
            is VideoPlayerIntent.PlaybackFailed ->
                _state.update { it.copy(error = "This video can't be played (${intent.reason})") }
            is VideoPlayerIntent.SetPlaybackSpeed -> setPlaybackSpeed(intent.speed)
            is VideoPlayerIntent.SetQuality -> setQuality(intent.height)
            is VideoPlayerIntent.SetAutoplay -> setAutoplay(intent.enabled)
            VideoPlayerIntent.CancelAutoplay -> cancelCountdown()
            is VideoPlayerIntent.SeekTo -> seekTo(intent.positionMs)
        }
    }

    fun onForeground() {
        if (resumeOnForeground) player.play()
        resumeOnForeground = false
        // The countdown waits for the user to come back rather than starting a video unseen.
        if (countdownOnForeground && player.playbackState == Player.STATE_ENDED) startCountdown()
        countdownOnForeground = false
    }

    /** Not called on rotation, so playback (or a pause) carries straight across. */
    fun onBackground() {
        resumeOnForeground = player.playWhenReady
        player.pause()
        countdownOnForeground = countdownJob?.isActive == true
        cancelCountdown()
    }

    private fun open(videoId: String) {
        val current = _state.value
        savedStateHandle[KEY_VIDEO_ID] = videoId
        _state.update { it.copy(isOpen = true, videoId = videoId, openRequest = it.openRequest + 1) }
        // Reopening the video that's already in the floating window just expands it.
        if (current.isOpen && current.videoId == videoId) return
        cancelCountdown()
        _state.update { it.copy(video = null) }
        loadVideo()
    }

    /**
     * Swaps in the video [step] places along the list (wrapping round) where the player already is, so the floating window or
     * picture-in-picture stays put. Unlike [open], it leaves [VideoPlayerState.openRequest] alone.
     */
    private fun skip(step: Int) {
        val currentId = _state.value.videoId ?: return
        cancelCountdown()
        viewModelScope.launch {
            val videos = repository.getVideos()
            if (videos.isEmpty()) return@launch
            val next = videos[(videos.indexOfFirst { it.id == currentId } + step).mod(videos.size)]
            // Closed, or another video picked, while the list loaded.
            if (!_state.value.isOpen || _state.value.videoId != currentId) return@launch
            savedStateHandle[KEY_VIDEO_ID] = next.id
            _state.update { it.copy(videoId = next.id, video = null) }
            player.playWhenReady = true
            loadVideo()
        }
    }

    private fun close() {
        loadJob?.cancel()
        cancelCountdown()
        clearSeekPreviews()
        savedStateHandle.remove<String>(KEY_VIDEO_ID)
        player.pause()
        player.stop()
        player.clearMediaItems()
        _state.update {
            VideoPlayerState(
                openRequest = it.openRequest,
                playbackSpeed = it.playbackSpeed,
                maxQuality = it.maxQuality,
                autoplay = it.autoplay,
            )
        }
    }

    /** Leaving the end also calls off the up-next countdown, through the playback state listener. */
    private fun seekTo(positionMs: Long) {
        val state = _state.value
        if (state.video == null || state.isLoading || state.error != null) return
        val duration = player.duration
        player.seekTo(if (duration == C.TIME_UNSET) positionMs.coerceAtLeast(0) else positionMs.coerceIn(0, duration))
        player.play()
    }

    private fun setPlaybackSpeed(speed: Float) {
        savedStateHandle[KEY_SPEED] = speed
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    /**
     * Caps the video at [height] rather than pinning one track, so the choice carries over to the
     * next video, whose tracks are different. Auto lifts the cap and the player picks the best.
     */
    private fun setQuality(height: Int?) {
        savedStateHandle[KEY_QUALITY] = height
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .apply { if (height == null) clearVideoSizeConstraints() else setMaxVideoSize(Int.MAX_VALUE, height) }
            .build()
        _state.update { it.copy(maxQuality = height) }
    }

    private fun setAutoplay(enabled: Boolean) {
        savedStateHandle[KEY_AUTOPLAY] = enabled
        _state.update { it.copy(autoplay = enabled) }
        if (!enabled) cancelCountdown() else if (player.playbackState == Player.STATE_ENDED) startCountdown()
    }

    /** Ticks [VideoPlayerState.autoplayCountdown] down to zero, then plays the first video up next. */
    private fun startCountdown() {
        val state = _state.value
        if (!state.autoplay || state.upNext.isEmpty() || state.error != null || countdownJob?.isActive == true) return
        countdownJob = viewModelScope.launch {
            for (seconds in AUTOPLAY_COUNTDOWN_SECONDS downTo 1) {
                _state.update { it.copy(autoplayCountdown = seconds) }
                delay(1_000)
            }
            _state.update { it.copy(autoplayCountdown = null) }
            countdownJob = null
            skip(1)
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _state.update { it.copy(autoplayCountdown = null) }
    }

    private fun loadVideo() {
        val videoId = _state.value.videoId ?: return
        loadJob?.cancel()
        // Another video's frames would preview the wrong scenes.
        if (previewVideoId != videoId) clearSeekPreviews()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val video = repository.getVideo(videoId)
                if (video == null) {
                    _state.update { it.copy(isLoading = false, error = "This video isn't available") }
                    return@launch
                }
                val videos = repository.getVideos()
                val index = videos.indexOfFirst { it.id == video.id }
                // Starting just after this one and wrapping round, the same order as next.
                val upNext = if (index < 0) emptyList() else (1 until videos.size).map { videos[(index + it) % videos.size] }
                if (player.currentMediaItem?.mediaId != video.id) {
                    player.setMediaItem(
                        MediaItem.Builder()
                            .setMediaId(video.id)
                            .setUri(RawResourceDataSource.buildRawResourceUri(video.videoRes))
                            .build(),
                    )
                }
                // Also recovers from a playback error when retrying.
                player.prepare()
                _state.update { it.copy(isLoading = false, video = video, upNext = upNext) }
                loadSeekPreviews(video)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load this video") }
            }
        }
    }

    private fun loadSeekPreviews(video: Video) {
        if (previewVideoId == video.id) return
        clearSeekPreviews()
        previewVideoId = video.id
        previewJob = viewModelScope.launch {
            try {
                seekPreviewSource.previews(video).collect { _seekPreviews.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Only a nicety: scrubbing still works, just without the pictures.
            }
        }
    }

    private fun clearSeekPreviews() {
        previewJob?.cancel()
        previewJob = null
        previewVideoId = null
        _seekPreviews.value = null
    }

    override fun onCleared() {
        player.release()
    }

    private companion object {
        const val KEY_VIDEO_ID = "videoId"
        const val KEY_SPEED = "playbackSpeed"
        const val KEY_QUALITY = "maxQuality"
        const val KEY_AUTOPLAY = "autoplay"
    }
}

package com.nameisjayant.composevideos.media.reels.presentation

import com.nameisjayant.composevideos.media.reels.data.Reel

/** Single source of truth for the Reels screen. */
data class ReelsState(
    val isLoading: Boolean = true,
    val reels: List<Reel> = emptyList(),
    val currentIndex: Int = 0,
    val isPaused: Boolean = false,
    val isMuted: Boolean = false,
    val playbackSpeed: Float = 1f,
    /** Move on to the next reel when the current one ends, instead of looping it. */
    val autoScroll: Boolean = false,
    val error: String? = null,
    val likedReelIds: Set<String> = emptySet(),
    val likedCommentIds: Set<String> = emptySet(),
    /** Reel whose comments sheet is open, or null when it's closed. */
    val commentsReelId: String? = null,
    /** Reel whose ⋮ options sheet is open, or null when it's closed. */
    val optionsReelId: String? = null,
    /** What a finger held on the video is doing, or null when nothing's held. */
    val hold: ReelHold? = null,
)

/** Long-press on the video: the middle pauses (Instagram), the edges play fast (TikTok). */
enum class ReelHold { Pause, FastForward }

/** Speed while [ReelHold.FastForward] is held. */
const val HoldSpeed = 2f

/** Speeds the speed button steps through, wrapping back to the first. */
val PlaybackSpeeds = listOf(1f, 1.5f, 2f, 0.5f)

/** Reasons offered when reporting a reel, in Instagram's order. */
val ReportReasons = listOf(
    "Spam",
    "Nudity or sexual activity",
    "Hate speech or symbols",
    "Violence or dangerous organisations",
    "Bullying or harassment",
    "False information",
    "Something else",
)

/** Everything the user (or the player) can tell the screen. */
sealed interface ReelsIntent {
    data object LoadReels : ReelsIntent
    data class PageSettled(val index: Int) : ReelsIntent
    data object TogglePlayPause : ReelsIntent
    data object ToggleMute : ReelsIntent
    data object CycleSpeed : ReelsIntent
    data object ToggleAutoScroll : ReelsIntent
    data class HoldStarted(val hold: ReelHold) : ReelsIntent
    data object HoldReleased : ReelsIntent
    data class PlaybackFailed(val reelId: String, val reason: String) : ReelsIntent
    data class ToggleLike(val reelId: String) : ReelsIntent
    /** Double-tap on the video: only ever likes, never unlikes, like Instagram. */
    data class DoubleTapLike(val reelId: String) : ReelsIntent
    data class OpenComments(val reelId: String) : ReelsIntent
    data object CloseComments : ReelsIntent
    /** [parentId] makes it a reply in that comment's thread. */
    data class PostComment(val reelId: String, val text: String, val parentId: String? = null) : ReelsIntent
    data class ToggleCommentLike(val commentId: String) : ReelsIntent
    /** Only the user's own comments can be deleted; a top-level one takes its replies with it. */
    data class DeleteComment(val reelId: String, val commentId: String) : ReelsIntent
    data class OpenOptions(val reelId: String) : ReelsIntent
    data object CloseOptions : ReelsIntent
    /** Drops the reel from the feed, with an undo. */
    data class NotInterested(val reelId: String) : ReelsIntent
    /** Drops the reel from the feed for good. */
    data class Report(val reelId: String, val reason: String) : ReelsIntent
    /** Puts back the reel the last [NotInterested] removed. */
    data object UndoNotInterested : ReelsIntent
    data class Share(val reelId: String) : ReelsIntent
}

/** One-off events that shouldn't survive recomposition or rotation. */
sealed interface ReelsEffect {
    /** [action] is sent back as an intent if the snackbar's [actionLabel] is tapped. */
    data class ShowMessage(
        val message: String,
        val actionLabel: String? = null,
        val action: ReelsIntent? = null,
    ) : ReelsEffect
    data class ShareReel(val text: String) : ReelsEffect
}

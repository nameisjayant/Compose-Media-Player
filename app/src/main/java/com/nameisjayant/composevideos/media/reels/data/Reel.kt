package com.nameisjayant.composevideos.media.reels.data

import androidx.annotation.RawRes

/** A single reel shown in the Reels feed, bundled in the APK as [videoRes] under `res/raw`. */
data class Reel(
    val id: String,
    val title: String,
    val channel: String,
    @param:RawRes val videoRes: Int,
    val likeCount: Int = 0,
    val shareCount: Int = 0,
    val comments: List<ReelComment> = emptyList(),
)

/** Every comment, replies included, as Instagram counts them. */
val Reel.commentCount: Int get() = comments.sumOf { 1 + it.replies.size }

/** Handle the signed-in user posts under; their comments are the ones they can delete. */
const val CurrentUser = "you"

data class ReelComment(
    val id: String,
    val author: String,
    val text: String,
    /** Pre-formatted age, e.g. "2h" or "now". */
    val postedAgo: String,
    /** Likes from everyone else; the user's own like is tracked in the screen state. */
    val likeCount: Int = 0,
    /** Pinned by the reel's creator; shown above the rest. Only top-level comments are pinned. */
    val isPinned: Boolean = false,
    /** One level deep, like Instagram: a reply to a reply joins the same thread. */
    val replies: List<ReelComment> = emptyList(),
) {
    val isMine: Boolean get() = author == CurrentUser
}

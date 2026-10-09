package com.nameisjayant.androidpractice.media.reels.data

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

data class ReelComment(
    val id: String,
    val author: String,
    val text: String,
    /** Pre-formatted age, e.g. "2h" or "now". */
    val postedAgo: String,
)

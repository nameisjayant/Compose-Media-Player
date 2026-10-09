package com.nameisjayant.composevideos.media.videos.data

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes

/**
 * A long-form video in the Videos tab, bundled in the APK as [videoRes] under `res/raw`, with a
 * still frame from it as [thumbnailRes].
 */
data class Video(
    val id: String,
    val title: String,
    val channel: String,
    val description: String,
    /** Pre-formatted length shown on the thumbnail, e.g. "1:00". */
    val duration: String,
    /** Release year and licence, shown under the title. */
    val meta: String,
    @param:RawRes val videoRes: Int,
    @param:DrawableRes val thumbnailRes: Int,
)

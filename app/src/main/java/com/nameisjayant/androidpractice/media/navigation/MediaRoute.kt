package com.nameisjayant.androidpractice.media.navigation

import androidx.annotation.DrawableRes
import com.nameisjayant.androidpractice.R
import kotlinx.serialization.Serializable

sealed interface MediaRoute {
    @Serializable
    data object Reels : MediaRoute

    @Serializable
    data object Videos : MediaRoute
}

enum class MediaTab(
    val route: MediaRoute,
    val label: String,
    @param:DrawableRes val icon: Int,
) {
    Reels(MediaRoute.Reels, "Reels", R.drawable.ic_reels),
    Videos(MediaRoute.Videos, "Videos", R.drawable.ic_videos),
}

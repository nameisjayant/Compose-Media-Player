package com.nameisjayant.androidpractice.media.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nameisjayant.androidpractice.media.reels.presentation.ReelsScreen
import com.nameisjayant.androidpractice.media.videos.VideosScreen

@Composable
fun MediaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    NavHost(
        navController = navController,
        startDestination = MediaRoute.Reels,
        modifier = modifier,
    ) {
        composable<MediaRoute.Reels> { ReelsScreen(contentPadding = contentPadding) }
        composable<MediaRoute.Videos> { VideosScreen(Modifier.padding(contentPadding)) }
    }
}

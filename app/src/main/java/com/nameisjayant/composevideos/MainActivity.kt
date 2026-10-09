package com.nameisjayant.composevideos

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nameisjayant.composevideos.media.navigation.MediaBottomBar
import com.nameisjayant.composevideos.media.navigation.MediaNavHost
import com.nameisjayant.composevideos.media.ui.MediaTheme
import com.nameisjayant.composevideos.media.videos.presentation.VideoPlayerIntent
import com.nameisjayant.composevideos.media.videos.presentation.VideoPlayerOverlay
import com.nameisjayant.composevideos.media.videos.presentation.VideoPlayerViewModel
import com.nameisjayant.composevideos.media.videos.presentation.rememberIsInPictureInPicture
import com.nameisjayant.composevideos.media.videos.presentation.rememberPlayerSheetState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The media shell is always dark, so system bar icons are always light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            MediaTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                // Scoped to the activity, so the video keeps playing in its floating window
                // whichever tab the user moves to.
                val playerViewModel: VideoPlayerViewModel = hiltViewModel()
                val playerState by playerViewModel.state.collectAsStateWithLifecycle()
                val playerSheet = rememberPlayerSheetState()
                val isInPip = rememberIsInPictureInPicture()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        // The full player is full-bleed, so the tab bar tucks away under it and
                        // slides back as the player shrinks into its floating window.
                        MediaBottomBar(
                            navController = navController,
                            currentDestination = currentDestination,
                            modifier = Modifier.graphicsLayer {
                                val hidden = if (isInPip) 1f else playerSheet.tabBarHidden
                                translationY = size.height * hidden
                                alpha = 1f - hidden
                            },
                        )
                    },
                ) { innerPadding ->
                    // The bar floats over the content so it shows through the glass; screens get
                    // only the bottom inset to keep their own UI clear of it (they handle the status bar).
                    val bottomPadding = innerPadding.calculateBottomPadding()
                    Box(Modifier.fillMaxSize()) {
                        MediaNavHost(
                            navController = navController,
                            onVideoClick = { playerViewModel.onIntent(VideoPlayerIntent.Open(it)) },
                            contentPadding = PaddingValues(bottom = bottomPadding),
                        )
                        if (playerState.isOpen) {
                            VideoPlayerOverlay(
                                viewModel = playerViewModel,
                                sheetState = playerSheet,
                                bottomInset = bottomPadding,
                            )
                        }
                    }
                }
            }
        }
    }
}

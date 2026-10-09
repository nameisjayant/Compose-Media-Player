package com.nameisjayant.androidpractice

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nameisjayant.androidpractice.media.navigation.MediaBottomBar
import com.nameisjayant.androidpractice.media.navigation.MediaNavHost
import com.nameisjayant.androidpractice.media.ui.MediaTheme
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

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = { MediaBottomBar(navController, currentDestination) },
                ) { innerPadding ->
                    // The bar floats over the content so it shows through the glass; screens get
                    // only the bottom inset to keep their own UI clear of it (they handle the status bar).
                    MediaNavHost(
                        navController = navController,
                        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                    )
                }
            }
        }
    }
}

package com.nameisjayant.composevideos

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.cast.Cast
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AndroidPracticeApplication : Application() {
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // Before anything builds a CastPlayer or Cast button. Loads in the background; without
        // Google Play services it fails quietly and the videos only play on the phone.
        Cast.getSingletonInstance(this).initialize()
    }
}

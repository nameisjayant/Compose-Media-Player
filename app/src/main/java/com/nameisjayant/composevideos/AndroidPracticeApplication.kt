package com.nameisjayant.composevideos

import android.app.Application
import androidx.media3.cast.Cast
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AndroidPracticeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before anything builds a CastPlayer or Cast button. Loads in the background; without
        // Google Play services it fails quietly and the videos only play on the phone.
        Cast.getSingletonInstance(this).initialize()
    }
}

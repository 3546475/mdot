package com.mdot.app

import android.app.Application
import com.mdot.app.core.util.CrashGuard
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JiabanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
    }
}

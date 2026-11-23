package com.mdot.app

import android.app.Application
import com.mdot.app.core.util.CrashGuard
import com.mdot.app.core.util.StartupTrace
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JiabanApplication : Application() {
    override fun onCreate() {
        // 先装崩溃处理器再 super.onCreate()：@HiltAndroidApp 的注入在 super 里发生，
        // 装晚了那一段崩了就没日志（而上报的冷启动闪退正落在这里的可能性最大）。
        CrashGuard.install(this)
        // 读上次留下的面包屑合成崩溃记录——必须在下面的 mark("application") **之前**，
        // 否则每次冷启动都会把本轮自己写的那行当成「上次异常退出」。
        CrashGuard.captureStartupAbort(this)
        StartupTrace.mark(this, "application")
        super.onCreate()
    }
}

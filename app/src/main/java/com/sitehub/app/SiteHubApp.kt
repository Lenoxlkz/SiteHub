package com.sitehub.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.sitehub.app.core.DownloadCenter
import com.sitehub.app.core.Engine
import com.sitehub.app.core.HomeData
import com.sitehub.app.core.Prefs
import com.sitehub.app.core.Sessions
import com.sitehub.app.util.CrashReporter

/**
 * Application de SiteHub: captura de crashes, ajustes y arranque diferido del
 * motor (después del primer fotograma, para que la app abra al instante).
 */
class SiteHubApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (isCrashProcess()) return
        CrashReporter.install(this)
        Prefs.init(this)
        HomeData.init(this)
        DownloadCenter.init(this)
        Engine.warmUpAfterFirstFrame(this)
    }

    private fun isCrashProcess(): Boolean {
        val name = runCatching {
            java.io.File("/proc/self/cmdline").readText().replace("\u0000", "").trim()
        }.getOrNull() ?: return false
        return name.endsWith(":crash")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> Sessions.closeBackground()
        }
    }
}

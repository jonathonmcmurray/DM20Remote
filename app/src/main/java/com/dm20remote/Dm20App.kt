package com.dm20remote

import android.app.Application
import com.dm20remote.debug.WireLog

/**
 * Installs process-wide services that need a [Application] context. Today that is
 * only the opt-in wire log, which must be ready before the first connection
 * attempt so initial traffic can be captured.
 */
class Dm20App : Application() {
    override fun onCreate() {
        super.onCreate()
        WireLog.install(this)
    }
}

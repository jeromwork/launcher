package com.launcher.app

import android.app.Application
import com.launcher.app.di.appAndroidModule
import com.launcher.core.LauncherCore
import com.launcher.ui.di.androidPlatformModule
import com.launcher.ui.di.coreCommonModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Process entry: starts Koin and the launcher core lifecycle.
 *
 * Per ADR-005 Amendment 2026-05-07a, dependency wiring goes through Koin
 * (KMP-compatible service locator) instead of manual constructor wiring in
 * Application.onCreate.
 */
class LauncherApplication : Application() {

    private val core: LauncherCore by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@LauncherApplication)
            modules(coreCommonModule, androidPlatformModule, appAndroidModule)
        }
        core.start()
    }

    override fun onTerminate() {
        core.stop()
        super.onTerminate()
    }
}

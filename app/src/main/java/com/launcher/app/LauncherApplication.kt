package com.launcher.app

import android.app.Application
import com.launcher.app.preset.DataStorePresetRepository
import com.launcher.core.LauncherCore

/**
 * Process entry: owns [LauncherCore] and starts platform bridge per core foundation plan.
 */
class LauncherApplication : Application() {

    lateinit var core: LauncherCore
        private set

    override fun onCreate() {
        super.onCreate()
        core = LauncherCore(
            context = this,
            moduleDescriptors = AppModuleDescriptors.all,
            presetRepository = DataStorePresetRepository(this),
        )
        core.start()
    }

    override fun onTerminate() {
        core.stop()
        super.onTerminate()
    }
}

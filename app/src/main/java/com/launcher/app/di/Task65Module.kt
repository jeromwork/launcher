package com.launcher.app.di

import com.launcher.adapters.pools.HardcodedPoolSource
import com.launcher.adapters.pools.JsonAssetPoolSource
import com.launcher.adapters.preset.PresetReminderService
import com.launcher.adapters.preset.PresetSelectionService
import com.launcher.adapters.preset.PresetSwitchService
import com.launcher.adapters.profile.PreferencesProfileStore
import com.launcher.api.pools.PoolSource
import com.launcher.api.profile.ProfileStore
import com.launcher.api.switchstrategy.CopyOnActivateStrategy
import com.launcher.api.switchstrategy.ProfileSwitchStrategy
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.handlers.CheckHandler
import kotlin.reflect.KClass
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * TASK-65 Koin wiring.
 *
 * [PoolSource] default = [HardcodedPoolSource]. Override via BuildConfig flag
 * `POOLS_JSON=true` (set with `./gradlew :app:assembleDebug -Ppools.json=true`)
 * to switch to [JsonAssetPoolSource] scaffold. See T632.
 */
val task65Module = module {

    single<PoolSource> {
        val useJson = try {
            // BuildConfig field set from Gradle property; default false.
            val clazz = Class.forName("com.launcher.app.BuildConfig")
            val field = clazz.getDeclaredField("POOLS_JSON")
            field.getBoolean(null)
        } catch (_: Throwable) {
            false
        }
        if (useJson) JsonAssetPoolSource() else HardcodedPoolSource()
    }

    single<ProfileSwitchStrategy> { CopyOnActivateStrategy() }

    single<ProfileStore> { PreferencesProfileStore(androidContext()) }

    single {
        PresetSelectionService(
            configSource = get(),
            profileStore = get(),
            switchStrategy = get(),
        )
    }
    single {
        PresetSwitchService(
            configSource = get(),
            profileStore = get(),
            switchStrategy = get(),
        )
    }
    single {
        @Suppress("UNCHECKED_CAST")
        PresetReminderService(
            checkHandlers = get<Map<KClass<out CheckSpec>, CheckHandler>>(named("checkHandlers")),
        )
    }
}

package com.launcher.app.debug.di

import com.launcher.app.data.envelope.InMemoryAsyncConfigPushQueueImpl
import cryptokit.keys.api.AsyncConfigPushQueue
import cryptokit.keys.api.RemoteStorage
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Spec 018 (F-5b) debug overlay для realBackendDebug.
 *
 * Подменяет `WorkManagerAsyncConfigPushQueue` (production, async через
 * WorkManager) на `InMemoryAsyncConfigPushQueueImpl` (synchronous, in-memory).
 * Это нужно для приёмки save → load round-trip: иначе `ConfigSaver.saveOwn`
 * возвращает Success после стейджинга в WorkManager, а не после реального
 * Firestore write — нельзя сразу сделать loadOwn и ожидать что данные на
 * сервере.
 *
 * Loaded в LauncherApplication.onCreate() через flavor-resolved
 * `debugOverlayModules()` factory (см. companion object).
 */
val f018DebugOverlayModule = module {
    // override = true — переписывает биндинг из f018KeysBackendModule.
    single<AsyncConfigPushQueue>(createdAtStart = false) {
        InMemoryAsyncConfigPushQueueImpl(storage = get<RemoteStorage>())
    }
}

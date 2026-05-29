package com.launcher.app.di

import org.koin.dsl.module

/**
 * Spec 014 Koin wiring per Plan §6 / tasks T004.
 *
 * **Phase 0 — scaffolding**: module is empty. Bindings будут добавляться
 * incrementally по мере того как:
 *  - T031 определит `NamedConfigsLocalStore` port,
 *  - T056 создаст `DataStoreNamedConfigsLocalStore` real adapter,
 *  - T060 завершит wiring (real adapter для release/debug, fake для test).
 *
 * Per CLAUDE.md §6 — DI выбирает fake/real per build. F-014.0 пока only-local,
 * single binding. F-014.1 добавит `RemoteNamedConfigsStore` через отдельный
 * adapter без изменений в этом файле (port stays the same).
 */
val spec014Module = module {
    // TODO(T060): single<NamedConfigsLocalStore> {
    //     DataStoreNamedConfigsLocalStore(
    //         dataStore = get(named("namedConfigsDataStore")),
    //         logger = get(),
    //     )
    // }
}

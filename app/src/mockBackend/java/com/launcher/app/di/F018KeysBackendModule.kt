package com.launcher.app.di

import com.launcher.app.data.recovery.NoOpRecoveryKeyVault
import family.keys.api.RecoveryKeyVault
import org.koin.dsl.module

/**
 * Spec 018 (F-5) backend wiring — **mockBackend flavor** (T046).
 *
 * RecoveryKeyVault → NoOpRecoveryKeyVault (нет Firestore в mockBackend).
 * Это означает: mockBackend builds могут работать в Standard / Senior mode без
 * cloud config, но recovery flow выдаст Unauthorized — ожидаемое поведение
 * для dev/non-GMS builds.
 */
val f018KeysBackendModule = module {
    single<RecoveryKeyVault> { NoOpRecoveryKeyVault() }
}

package com.launcher.app.di

import com.google.firebase.firestore.FirebaseFirestore
import com.launcher.app.data.recovery.FirestoreRecoveryKeyVault
import family.keys.api.RecoveryKeyVault
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Spec 018 (F-5) backend wiring — **realBackend flavor** (T045, T049).
 *
 * RecoveryKeyVault → FirestoreRecoveryKeyVault поверх FirebaseFirestore singleton.
 *
 * Per CLAUDE.md rule 2 (ACL): Firebase SDK импортируется ТОЛЬКО в
 * FirestoreRecoveryKeyVault и здесь (DI binding). Никакой Firestore type не
 * утекает в domain или UI.
 */
val f018KeysBackendModule = module {
    single<RecoveryKeyVault> {
        FirestoreRecoveryKeyVault(
            firestore = FirebaseFirestore.getInstance(),
            json = Json { ignoreUnknownKeys = true }
        )
    }
}

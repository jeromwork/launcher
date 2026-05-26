package com.launcher.app.di

import com.launcher.adapters.crypto.PairingCryptoCoordinator
import com.launcher.api.identity.DeviceIdProvider
import com.launcher.api.identity.IdentityProvider
import com.launcher.api.link.LinkRegistry
import com.launcher.api.pairing.PairingService
import com.launcher.api.push.PushSender
import com.launcher.api.result.Outcome
import com.launcher.api.sync.RemoteSyncBackend
import com.launcher.app.ui.pairing.PairingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Spec 007 pairing-flow Koin wiring (T085 + DI glue).
 *
 * `PairingService` is registered as a process-wide `single` so the FSM
 * observer survives Activity recreate. The `applicationScope` is a
 * supervised CoroutineScope tied to the process lifetime; PairingService
 * launches its `/pairings/{token}` observer onto this scope.
 *
 * Flavor-specific port bindings live in `:core/androidRealBackend` and
 * `:core/androidMockBackend` `backendModule` — this module just composes
 * the orchestrator + ViewModel on top.
 */
val pairingModule = module {

    single(named(APPLICATION_SCOPE_QUALIFIER)) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single {
        PairingService(
            backend = get<RemoteSyncBackend>(),
            identity = get<IdentityProvider>(),
            deviceId = get<DeviceIdProvider>(),
            linkRegistry = get<LinkRegistry>(),
            pushSender = get<PushSender>(),
            clock = { System.currentTimeMillis() },
            scope = get(named(APPLICATION_SCOPE_QUALIFIER)),
            managedDevices = get<com.launcher.api.link.ManagedDevicesRegistry>(),
        )
    }

    single {
        // Spec 011 T061 wiring: PairingViewModel.confirmConsent() → coordinator
        // publishOwnIdentity. Coordinator зарегистрирован в cryptoModule;
        // получаем lambda здесь чтобы не тянуть optional dependency через
        // ViewModel constructor.
        val coordinator: PairingCryptoCoordinator = get()
        PairingViewModel(
            service = get(),
            identity = get<IdentityProvider>(),
            linkRegistry = get<LinkRegistry>(),
            onLinkEstablished = { linkId ->
                when (val r = coordinator.publishOwnIdentity(linkId)) {
                    is Outcome.Failure -> {
                        // Log-only; pairing успешен, крипто-публикация повторится
                        // при следующем lifecycle hook (ensureKeysReady — идемпотентна).
                        android.util.Log.w("Spec011", "publishOwnIdentity failed: ${r.error::class.simpleName}")
                    }
                    is Outcome.Success -> {
                        android.util.Log.i("Spec011", "Own DeviceIdentity published for link $linkId")
                    }
                }
            },
        )
    }
}

const val APPLICATION_SCOPE_QUALIFIER: String = "applicationScope"

package com.launcher.app.ui.pairing

import com.launcher.api.identity.IdentityProvider
import com.launcher.api.link.LinkRegistry
import com.launcher.api.pairing.PairingService
import com.launcher.api.pairing.PairingState
import com.launcher.api.pairing.PairingToken
import com.launcher.api.result.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Spec 007 UI ↔ domain orchestrator for the Managed-side pairing flow
 * (T085). Wraps [PairingService] for Compose consumption:
 *
 *  - [state] mirrors the FSM as a Compose-friendly [StateFlow].
 *  - [events] surfaces one-shot UX signals (errors, copy-to-clipboard hints).
 *  - action methods (`startPairing`, `cancel`, `confirmConsent`, `decline`)
 *    suspend-launch off the internal scope so the UI can `onClick` without
 *    needing to manage its own coroutines.
 *
 * Lives as a **Koin singleton** (not androidx.lifecycle.ViewModel) so its
 * state survives activity recreate by virtue of Application-scope ownership.
 * The lifecycle hook on the consuming Activity calls [bindToScope] / [unbind]
 * to wire a UI-tied scope for fan-out cancellation if needed.
 */
class PairingViewModel(
    private val service: PairingService,
    private val identity: IdentityProvider,
    private val linkRegistry: LinkRegistry,
    // Spec 011 T061 — after consent.allow / claim success, publish own
    // DeviceIdentity to Firestore. Optional с default no-op для существующих
    // unit tests + mockBackend flavor где Firestore нет.
    private val onLinkEstablished: suspend (linkId: String) -> Unit = { /* no-op */ },
) {

    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val state: StateFlow<PairingState> =
        service.state().stateIn(internalScope, SharingStarted.Eagerly, PairingState.Idle)

    private val _events = MutableSharedFlow<UiEvent>(replay = 0, extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    /** True while an admin-side claim is in flight (between deep-link arrival
     *  and the FSM moving to [PairingState.Claimed] or [PairingState.Error]).
     *  UI uses this to show a Loading screen instead of the Idle toggle, so
     *  the user does not see the Managed-side "Включить" entry by mistake. */
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    sealed interface UiEvent {
        data class Error(val message: String) : UiEvent
        data object PairingComplete : UiEvent
        data object PairingDeclined : UiEvent
    }

    /** Called from Settings toggle ON. Anonymously signs in if needed, then
     *  starts the pairing flow (FR-002 + FR-003). */
    fun startPairing() {
        internalScope.launch {
            if (identity.currentIdentity() == null) {
                val signIn = identity.signInAnonymous()
                if (signIn is Outcome.Failure) {
                    _events.tryEmit(UiEvent.Error("Не удалось войти в Firebase: ${signIn.error}"))
                    return@launch
                }
            }
            val result = service.startPairingAsManaged()
            if (result is Outcome.Failure) {
                _events.tryEmit(UiEvent.Error("Не удалось создать QR-токен: ${result.error}"))
            }
        }
    }

    /** Settings toggle OFF before claim — deletes /pairings/{token} cleanly. */
    fun cancel() {
        internalScope.launch {
            service.cancelPairingAsManaged()
        }
    }

    /** Consent → "Разрешить". Writes /state/current bootstrap + FCM subscribe. */
    fun confirmConsent() {
        internalScope.launch {
            when (val result = service.confirmConsentAsManaged()) {
                is Outcome.Failure -> {
                    _events.tryEmit(UiEvent.Error("Не удалось активировать связь: ${result.error}"))
                }
                is Outcome.Success -> {
                    // Spec 011 T061 — после успешного link activate, publish own
                    // DeviceIdentity. Non-blocking-fatal: ошибка только logging,
                    // pairing уже считается успешным (крипто-фундамент догонит
                    // через `PairingCryptoCoordinator.ensureKeysReady()` при
                    // следующей попытке encrypt).
                    runCatching { onLinkEstablished(result.value.linkId) }
                    _events.tryEmit(UiEvent.PairingComplete)
                }
            }
        }
    }

    /** Consent → "Отклонить". Hard-deletes /pairings + /links subtree. */
    fun decline() {
        internalScope.launch {
            val result = service.declineConsentAsManaged()
            if (result is Outcome.Failure) {
                _events.tryEmit(UiEvent.Error("Ошибка при отказе: ${result.error}"))
            } else {
                _events.tryEmit(UiEvent.PairingDeclined)
            }
        }
    }

    /** Admin-side entry: incoming `launcher://pair?token=XXX` deep-link.
     *  Anonymous-signs in if needed, then runs the claim transaction.
     *  Sets [isProcessing] so the UI does not flash the Managed-side Idle
     *  toggle while sign-in + transaction are in flight. */
    fun claimAsAdmin(token: PairingToken) {
        // Set flag synchronously so the first Compose frame after the deep-link
        // already renders ProcessingScreen instead of flashing the Idle toggle.
        _isProcessing.value = true
        internalScope.launch {
            try {
                // If a stale Managed-side pairing is hanging on this device's
                // singleton state, cancel it first — otherwise the router would
                // keep rendering WaitingForClaim with the old token and the
                // admin claim would never visually take over.
                if (state.value is PairingState.WaitingForClaim ||
                    state.value is PairingState.AwaitingConsent
                ) {
                    service.cancelPairingAsManaged()
                }
                if (identity.currentIdentity() == null) {
                    val signIn = identity.signInAnonymous()
                    if (signIn is Outcome.Failure) {
                        _events.tryEmit(UiEvent.Error("Не удалось войти в Firebase: ${signIn.error}"))
                        return@launch
                    }
                }
                val result = service.claimAsAdmin(token)
                if (result is Outcome.Failure) {
                    _events.tryEmit(UiEvent.Error("Не удалось привязать: ${result.error}"))
                } else {
                    _events.tryEmit(UiEvent.PairingComplete)
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** PairedStatus → "Отвязать". Hard-deletes the link (FR-033). Both sides
     *  of the relationship can call this; the Firestore Security Rules let
     *  either the admin or the managed device perform the delete. Resets the
     *  in-memory FSM state so the next Settings → "Открыть" doesn't land on a
     *  stale "Связь установлена" screen. */
    fun unbind() {
        // Reset the FSM **synchronously first** so the UI thread sees Idle
        // immediately — otherwise the next Activity entry can flash the
        // stale Claimed/Connected screen before the revoke coroutine lands.
        service.resetToIdle()
        internalScope.launch {
            linkRegistry.revoke()
        }
    }

    fun dispose() {
        // Mostly a debug hook; production stays alive for the process.
        service.dispose()
    }
}

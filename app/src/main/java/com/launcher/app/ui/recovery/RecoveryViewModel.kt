package com.launcher.app.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import family.keys.api.AuthIdentity
import family.keys.api.Outcome
import family.keys.api.PassphrasePrompter
import family.keys.api.RecoveryError
import family.keys.impl.RecoveryFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel мостящий Compose recovery UI ↔ [RecoveryFlow] (T073, US2).
 *
 * **State machine**: [State.Idle] → [State.SettingUp] | [State.Restoring]
 *  → [State.Done] | [State.Error] | [State.Fallback].
 *
 * **Passphrase bridge**: ViewModel реализует [PassphrasePrompter] через
 * `CompletableDeferred<String>` каналы — `RecoveryFlow.performSetup/Recovery`
 * suspend'ит на `prompter.requestXxxPassphrase()`, ViewModel выставляет
 * UI state'у `AwaitingPassphrase`, UI собирает text → ViewModel завершает
 * deferred → flow продолжается.
 */
class RecoveryViewModel(
    private val recoveryFlow: RecoveryFlow,
    private val identity: AuthIdentity
) : ViewModel(), PassphrasePrompter {

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var pendingSetup: CompletableDeferred<CharArray>? = null
    private var pendingRecovery: CompletableDeferred<CharArray>? = null

    /** Стартует setup flow (после bootstrap'а нового root). */
    fun startSetup(rootKey: family.keys.api.RootKey) {
        viewModelScope.launch {
            _state.value = State.SettingUp(AwaitingPassphraseKind.SETUP)
            when (val r = recoveryFlow.performSetup(identity, rootKey)) {
                is Outcome.Success -> _state.value = State.Done
                is Outcome.Failure -> _state.value = State.Error(r.error)
            }
        }
    }

    /** Стартует recovery flow (Sign-In в новое устройство, Keystore пуст). */
    fun startRecovery() {
        viewModelScope.launch {
            _state.value = State.Restoring(AwaitingPassphraseKind.RECOVERY)
            when (val r = recoveryFlow.performRecovery(identity)) {
                is Outcome.Success -> _state.value = State.Done
                is Outcome.Failure -> {
                    val err = r.error
                    if (err == RecoveryError.TooManyAttempts) {
                        _state.value = State.Fallback(FallbackReason.TOO_MANY_ATTEMPTS)
                    } else if (err == RecoveryError.MalformedVault) {
                        _state.value = State.Fallback(FallbackReason.MALFORMED_VAULT)
                    } else if (err == RecoveryError.NoVaultPresent) {
                        _state.value = State.Fallback(FallbackReason.NO_VAULT)
                    } else {
                        _state.value = State.Error(err)
                    }
                }
            }
        }
    }

    /** UI вызывает после того как user ввёл passphrase в setup screen. */
    fun submitSetupPassphrase(passphrase: CharArray) {
        pendingSetup?.complete(passphrase)
    }

    /** UI вызывает после того как user ввёл passphrase в recovery screen. */
    fun submitRecoveryPassphrase(passphrase: CharArray) {
        pendingRecovery?.complete(passphrase)
    }

    /** UI cancel button. */
    fun cancel() {
        pendingSetup?.complete(CharArray(0))
        pendingRecovery?.complete(CharArray(0))
        _state.value = State.Idle
    }

    /** UI вызывает после Fallback screen → "set up as new device". */
    fun acknowledgeFallback() {
        _state.value = State.Idle
    }

    override suspend fun requestSetupPassphrase(): Outcome<CharArray, RecoveryError> {
        val deferred = CompletableDeferred<CharArray>()
        pendingSetup = deferred
        val pw = deferred.await()
        pendingSetup = null
        return if (pw.isEmpty()) Outcome.Failure(RecoveryError.Cancelled) else Outcome.Success(pw)
    }

    override suspend fun requestRecoveryPassphrase(): Outcome<CharArray, RecoveryError> {
        val deferred = CompletableDeferred<CharArray>()
        pendingRecovery = deferred
        val pw = deferred.await()
        pendingRecovery = null
        return if (pw.isEmpty()) Outcome.Failure(RecoveryError.Cancelled) else Outcome.Success(pw)
    }

    sealed class State {
        object Idle : State()
        data class SettingUp(val awaiting: AwaitingPassphraseKind) : State()
        data class Restoring(val awaiting: AwaitingPassphraseKind) : State()
        object Done : State()
        data class Error(val cause: RecoveryError) : State()
        data class Fallback(val reason: FallbackReason) : State()
    }

    enum class AwaitingPassphraseKind { SETUP, RECOVERY }

    enum class FallbackReason {
        TOO_MANY_ATTEMPTS,
        MALFORMED_VAULT,
        NO_VAULT
    }
}

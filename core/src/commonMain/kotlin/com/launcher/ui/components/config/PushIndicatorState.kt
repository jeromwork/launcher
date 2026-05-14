package com.launcher.ui.components.config

import com.launcher.api.config.ConfigSyncConstants

/**
 * UI state machine для push-индикатора рядом с устройством в списке (spec 008
 * Phase 8 T101, FR-015, SC-001).
 *
 * Lifecycle:
 *  [Idle] — initial OR after «Применено» fades / dismissed.
 *  [InProgress] — push tap fired; spinner visible.
 *  [InProgressNoNetwork] — InProgress > 5s + no network detected; text «Нет
 *    интернета, попробуем позже».
 *  [Sent] — Firestore acknowledged write (FR-015 variant A); checkmark
 *    «Отправлено ✓».
 *  [AppliedOnDevice] — server's /state.appliedConfigUpdatedAt advanced to match
 *    or exceed our pushed snapshot (SC-001b); «Применено на телефоне ✓».
 *  [Failed] — push returned BackendFailure (not Conflict — Conflict goes to
 *    MergeUI path).
 *
 * Visual evolution: Idle → InProgress → InProgressNoNetwork (optionally) →
 * Sent → AppliedOnDevice. Failed is a terminal-error path.
 *
 * Transitions are computed by [PushIndicatorPresenter] (no I/O in this state
 * type). Per ux-quality CHK008: UI text strings are неутральны (no «push» /
 * «сервер»).
 */
sealed interface PushIndicatorState {
    data object Idle : PushIndicatorState

    /** Spinner only — push in flight. */
    data object InProgress : PushIndicatorState

    /** Spinner + «Нет интернета, попробуем позже» (FR-015 5-second threshold). */
    data object InProgressNoNetwork : PushIndicatorState

    /** Firestore write acknowledged. UI: «Отправлено ✓». */
    data object Sent : PushIndicatorState

    /** Managed updated /state. UI: «Применено на телефоне ✓». */
    data object AppliedOnDevice : PushIndicatorState

    /** Non-conflict failure. UI: «Не удалось отправить». */
    data class Failed(val reason: String) : PushIndicatorState
}

/**
 * Pure logic computing state transitions. Used by composable presenter +
 * unit-tested independently of Compose.
 */
object PushIndicatorPresenter {

    /**
     * Time threshold in [PushIndicatorState] transitions:
     *  - if [InProgress] > [PUSH_NO_NETWORK_WARNING_DELAY_MS] AND network is
     *    down per `ConnectivityManager.NetworkCallback` → [InProgressNoNetwork].
     */
    val NO_NETWORK_WARNING_AFTER_MS: Long = ConfigSyncConstants.PUSH_NO_NETWORK_WARNING_DELAY_MS

    /** Triggered on user-pressed «Отправить». */
    fun onPushInitiated(): PushIndicatorState = PushIndicatorState.InProgress

    /** Triggered after [NO_NETWORK_WARNING_AFTER_MS] if still InProgress AND no net. */
    fun onPushSlowWithoutNetwork(current: PushIndicatorState): PushIndicatorState =
        if (current is PushIndicatorState.InProgress) PushIndicatorState.InProgressNoNetwork
        else current

    /** Firestore acknowledged the /config/current write. */
    fun onFirestoreAcked(): PushIndicatorState = PushIndicatorState.Sent

    /** /state.appliedConfigUpdatedAt advanced past our pushed snapshot. */
    fun onStateAppliedOnDevice(): PushIndicatorState = PushIndicatorState.AppliedOnDevice

    /** Push failed (non-conflict; conflict → MergeScreen). */
    fun onPushFailed(reason: String): PushIndicatorState =
        PushIndicatorState.Failed(reason)

    /** User dismissed or the indicator auto-fades. */
    fun reset(): PushIndicatorState = PushIndicatorState.Idle
}

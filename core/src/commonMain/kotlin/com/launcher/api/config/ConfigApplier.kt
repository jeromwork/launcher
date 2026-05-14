package com.launcher.api.config

import com.launcher.api.link.StateApplied
import com.launcher.api.result.Outcome

/**
 * Port for applying a remote ConfigDocument to local Managed state (spec 008
 * §FR-021..023, FR-030..033).
 *
 * Per CLAUDE.md §6 (mock-first): two implementations —
 *  - `FirebaseConfigApplier` (androidMain): reads `/config/current` via
 *    [com.launcher.api.sync.RemoteSyncBackend], writes to local SQLDelight
 *    via [LocalConfigStore], publishes [StateApplied] back to
 *    `/state/current`.
 *  - `FakeConfigApplier` (commonTest): in-memory state machine. Programmable
 *    failure injection для tests of partial-apply (FR-033).
 *
 * **Self-as-writer skip** (FR-023): when this Managed just pushed its own
 * config (the FCM `config.updated` echoes back), avoid double-apply by
 * checking `incoming.lastWriterDeviceId == localDeviceId`.
 */
interface ConfigApplier {

    /**
     * Read `/config/current` for [linkId], apply to local store atomically,
     * publish [StateApplied] back to `/state/current`.
     *
     * Triggered by any of FR-022's four sources (T1 FCM / T2 NetworkCallback
     * / T3 WorkManager / T4 RESUMED). Idempotent per FR-021.
     */
    suspend fun applyFromRemote(linkId: String): Outcome<StateApplied, ConfigSyncError>
}

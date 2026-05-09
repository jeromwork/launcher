package com.launcher.api.capability

import kotlinx.coroutines.flow.Flow

/**
 * Port (interface) exposing per-provider availability snapshots.
 *
 * Implementations:
 *  - real (`AndroidCapabilityRepository`, androidMain): wires
 *    [AndroidCapabilityCollector] → DataStore projection.
 *  - fake (`FakeCapabilityRepository`, commonTest): in-memory `MutableStateFlow`
 *    for domain-level tests and dev/debug builds (FR-048, CLAUDE.md rule 6).
 */
interface CapabilityRepository {
    /** Hot flow of current snapshot. Replays last value on subscribe. */
    fun observe(): Flow<List<Capability>>

    /** Synchronous read of last-known snapshot (debug screens, one-shot consumers). */
    fun snapshot(): List<Capability>
}

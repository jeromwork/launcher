package com.launcher.api.health

import kotlinx.coroutines.flow.Flow

/**
 * Port (interface) exposing per-device health snapshots.
 *
 * Implementations:
 *  - real (`AndroidHealthRepository`, androidMain): wires `AndroidHealthCollector`
 *    (ConnectivityManager + AudioManager + ContentObservers + battery) →
 *    DataStore projection.
 *  - fake (`FakeHealthRepository`, commonTest): in-memory `MutableStateFlow`
 *    for tests (FR-048, CLAUDE.md rule 6).
 */
interface HealthRepository {
    /** Hot flow of current snapshot. Replays last value on subscribe. */
    fun observe(): Flow<Health>

    /** Synchronous read of last-known snapshot (debug screens, one-shot consumers). */
    fun snapshot(): Health
}

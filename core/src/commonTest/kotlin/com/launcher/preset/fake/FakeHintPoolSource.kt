package com.launcher.preset.fake

import com.launcher.preset.model.HintFlowEntry
import com.launcher.preset.port.HintPoolSource

/**
 * T029 — fake HintPoolSource for TASK-126 wizard runtime tests (FR-007).
 *
 * Returns a preconfigured list of [HintFlowEntry] items. Supports the empty-pool
 * scenario — the domain contract for [HintPoolSource.load] states that missing /
 * malformed sources MUST return an empty list rather than throw. This fake makes
 * the empty case explicit via [empty].
 *
 * Rule 6 (mock-first): production `BundledHintPoolSource` (androidMain, T035) reads
 * `assets/hint-pool.json`; commonTest exercises hint resolution without touching
 * the Android asset loader.
 */
class FakeHintPoolSource(
    private val entries: List<HintFlowEntry>,
) : HintPoolSource {

    override suspend fun load(): List<HintFlowEntry> = entries

    companion object {
        /** Empty-pool scenario — validates that missing hints degrade silently. */
        fun empty(): FakeHintPoolSource = FakeHintPoolSource(emptyList())

        /** Convenience varargs factory. */
        fun of(vararg entries: HintFlowEntry): FakeHintPoolSource =
            FakeHintPoolSource(entries.toList())
    }
}

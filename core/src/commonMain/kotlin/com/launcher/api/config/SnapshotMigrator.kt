package com.launcher.api.config

import com.launcher.api.result.Outcome
import com.launcher.wire.WireVersion
import com.launcher.wire.accessFor

/**
 * Reader gate for [ConfigSnapshot] per `docs/architecture/wire-format.md` §3, and the future home
 * of its migration chain (spec 009 FR-043).
 *
 * Plan §11 C-2: NO generic `TransformerRegistry<Version>` infrastructure. When the first schema
 * bump lands, the body gains an explicit `when (schemaVersion) { ... }` chain in this file.
 * A generic registry stays premature until the third transformer (rule 4, "3 examples").
 *
 * Reader policy (FR-043 + §3): a snapshot written by a newer build is read normally; only a
 * raised `minReaderVersion` fails closed with [MigrationError.UnsupportedVersion]. Never throws
 * across the boundary, never silently defaults.
 *
 * TODO(spec-followup TODO-ARCH-015): explicit v1 → v2 → ... chain here when the first breaking
 * change is needed.
 */
object SnapshotMigrator {

    /**
     * Applies the version gate and returns the snapshot ready for use by this build.
     *
     * Before TASK-138 this compared the envelope version numerically, which refused a snapshot
     * from any newer writer. Under §3 that is wrong — history records are append-only and an
     * older reader can display one written by a newer build as long as it does not demand a
     * newer reader.
     */
    fun migrate(
        snapshot: ConfigSnapshot,
        readerLevel: WireVersion = ConfigSnapshot.SCHEMA_VERSION,
    ): Outcome<ConfigSnapshot, MigrationError> =
        try {
            snapshot.accessFor(readerLevel)
            Outcome.Success(snapshot)
        } catch (_: Exception) {
            Outcome.Failure(MigrationError.UnsupportedVersion(snapshot.minReaderVersion))
        }
}

sealed interface MigrationError {
    /** [requiredReaderVersion] is what the document asked for, not what it was written with. */
    data class UnsupportedVersion(val requiredReaderVersion: WireVersion) : MigrationError
}

package com.launcher.api.config

import com.launcher.api.result.Outcome

/**
 * Migrates [ConfigSnapshot] between schemaVersion's (spec 009 FR-043).
 *
 * Plan §11 C-2: NO generic `TransformerRegistry<Version>` infrastructure.
 * When the first schema bump lands, the body becomes an explicit
 * `when (fromVersion) { 1 -> v1ToV2(it); 2 -> ...}` chain in this file.
 * Generic registry is premature until the third transformer (rule 4
 * "3 examples").
 *
 * Reader policy (FR-043): unknown future schemaVersion fails closed —
 * returns [MigrationError.UnsupportedVersion], never throws, never
 * silently defaults.
 *
 * TODO(spec-followup TODO-ARCH-015): explicit v1 → v2 → ... chain here
 * when the first breaking change is needed.
 */
object SnapshotMigrator {

    /**
     * Migrate a snapshot to [toVersion]. Today spec 9 supports only v1
     * so any other [toVersion] is a programmer error; reading a future
     * snapshot (snapshot.snapshotSchemaVersion > 1) fails closed.
     */
    fun migrate(
        snapshot: ConfigSnapshot,
        toVersion: Int = ConfigSnapshot.SUPPORTED_SNAPSHOT_SCHEMA_VERSION,
    ): Outcome<ConfigSnapshot, MigrationError> {
        if (toVersion != ConfigSnapshot.SUPPORTED_SNAPSHOT_SCHEMA_VERSION) {
            return Outcome.Failure(MigrationError.UnsupportedVersion(toVersion))
        }
        return when (snapshot.snapshotSchemaVersion) {
            1 -> Outcome.Success(snapshot)
            else -> Outcome.Failure(
                MigrationError.UnsupportedVersion(snapshot.snapshotSchemaVersion),
            )
        }
    }
}

sealed interface MigrationError {
    data class UnsupportedVersion(val version: Int) : MigrationError
}

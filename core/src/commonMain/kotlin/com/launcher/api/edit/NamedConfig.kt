package com.launcher.api.edit

import family.wire.WireVersion
import family.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

import kotlinx.serialization.Serializable

/**
 * F-014.0 local domain type для **named admin configs** (FR-003).
 *
 * Admin может иметь до **5 named configs** per Google account, каждый со своим
 * `configName`, `description`, `isDefault` флагом, compatibility key
 * (`presetId` + `deviceClass`), и `activeDeviceIds` set.
 *
 * **F-014.0 simplification** — single-device only:
 *  - `activeDeviceIds` contains только `thisDeviceId` (single-device).
 *  - Multi-device sync — F-014.1 (Firestore backup, depends on F-4 Google Sign-In).
 *  - UI hidden behind progressive disclosure (FR-003d State 0/1).
 *
 * Lifecycle (FR-003):
 *  - **ACTIVE**: `activeDeviceIds.size >= 1`, `orphanedAt == null`.
 *  - **ORPHAN**: `activeDeviceIds.isEmpty()`, `orphanedAt != null` (epoch millis).
 *    Auto-delete deferred to TODO-FUTURE-SPEC-008 (own-server prerequisite);
 *    F-014.0 only marks с UI countdown.
 *
 * Wire format JSON contract: [contracts/named-config-local.md](../../../../../specs/014-tile-editing-admin-senior-profiles/contracts/named-config-local.md).
 *
 * Invariants enforced at [NamedConfigsLocalStore] boundary, not in `init {}`:
 *  - Атомарность default flag invariant (FR-003a) требует knowledge of full list,
 *    не одного config.
 *  - Length / regex validation — отдельно через [ConfigNameValidator].
 *
 * TODO(server-roadmap): F-014.1 добавит `RemoteNamedConfigsStore` adapter;
 *   merge local + remote at use site через MergedNamedConfigsRepository.
 */
// @EncodeDefault: this format encodes with `encodeDefaults = false`, and I1 requires the version
// fields on every document.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NamedConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val configName: String,
    val description: String = "",
    val isDefault: Boolean = false,
    val presetId: String,
    val deviceClass: String,
    val activeDeviceIds: Set<String>,
    /** Epoch millis — set when `activeDeviceIds.isEmpty()` becomes true. Used for UI countdown. */
    val orphanedAt: Long? = null,
) : WireVersionHeader {
    /** Derived: lifecycle ACTIVE when at least one device uses this config. */
    val isActive: Boolean get() = activeDeviceIds.isNotEmpty()

    /** Derived: lifecycle ORPHAN when no device uses this config. */
    val isOrphan: Boolean get() = activeDeviceIds.isEmpty() && orphanedAt != null

    companion object {
        /** What this build writes. Was the integer 1 before the conversion — never lowered (I3). */
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

        /** Every field is independent and additive; nothing here needs a newer reader. */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /**
         * Named configs are shared between an admin's devices (F-014.1 adds a remote store), so a
         * document can be written back by a device that did not author it. Raise once a field
         * appears that an older writer would silently drop, unless §6 preservation lands first.
         */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
        const val MAX_CONFIG_NAME_LENGTH: Int = 32
        const val MAX_DESCRIPTION_LENGTH: Int = 200

        /** F-014.0 / FR-003c hard limit per admin. */
        const val MAX_CONFIGS_PER_ADMIN: Int = 5

        /** Default config name used at bootstrap on first launch (T057). */
        const val DEFAULT_CONFIG_NAME: String = "default"
    }
}

package com.launcher.api.link

import family.wire.WireVersion
import family.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Initial snapshot written to `/links/{linkId}/state/current` when the Managed
 * user accepts the consent screen (spec 007 §FR-009, contracts/state-bootstrap.md).
 *
 * **Bootstrap-only** schema for spec 007. The full state schema (flows, slots,
 * applied capabilities) is added in spec 008 as **additive** fields —
 * [SCHEMA_VERSION] stays at `1` until a rename/removal is required.
 *
 *  - [fcmToken]: `null` when GMS is unavailable (C13 stub); FCM push delivery
 *    falls back to the in-app banner.
 *  - [appliedAt]/[updatedAt]: epoch millis; server-set on write.
 */
data class LinkBootstrap(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val appliedAt: Long,
    val presetId: String,
    val fcmToken: String?,
) : WireVersionHeader {
    companion object {
        /** What this build writes. Was the integer 1 before the conversion — never lowered (I3). */
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

        /** Bootstrap fields are additive; an older reader safely ignores what it does not know. */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /** Written once by the managed device at pairing; no second writer merges it. */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }
}

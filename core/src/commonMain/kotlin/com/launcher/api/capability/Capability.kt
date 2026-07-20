package com.launcher.api.capability

import com.launcher.api.action.ProviderId
import family.wire.WireVersion
import family.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Per-provider snapshot of "what this device can do".
 *
 * Wire-format root per [`contracts/capability-wire-format.md`](specs/006-provider-capabilities-and-health/contracts/capability-wire-format.md)
 * v1.0.0. The shape is **public**: persisted in app-private DataStore today,
 * exported to Firestore `/links/{linkId}/capabilities` in spec 007. Any change
 * here is a wire-format change.
 *
 * Versioned per [`docs/architecture/wire-format.md`](docs/architecture/wire-format.md). FR-043's
 * "accept a newer writer with best-effort parse" is exactly §3's model: a raised [schemaVersion]
 * alone changes nothing for the reader, and only [minReaderVersion] can refuse.
 *
 * Invariants:
 *  - [iconId] uses namespace-prefixed convention `<namespace>:<name>` per
 *    [`contracts/icon-id-namespace.md`](specs/006-provider-capabilities-and-health/contracts/icon-id-namespace.md).
 *    Validation NOT enforced in `init` — forward-compat with future namespaces
 *    requires unknown values to parse without exception (resolved at consume
 *    time by [IconStorage]).
 *  - [versionCode] is `Long?` (PackageInfoCompat returns Long); null when
 *    provider is not installed OR when adapter cannot determine it (iOS, etc.).
 */
// @EncodeDefault: this format encodes with `encodeDefaults = false`, and I1 requires the version
// fields on every document.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Capability(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val providerId: ProviderId,
    val displayName: String,
    val iconId: String,
    val iconSha256: String? = null,
    val available: Boolean,
    val versionCode: Long? = null,
) : WireVersionHeader {
    companion object {
        /** What this build writes. Was the integer `1` before the conversion — never lowered (I3). */
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

        /** Nothing in this shape needs a newer reader; raise only on a meaning change (§3). */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /** An older writer cannot destroy meaning here — every field is independent. */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }
}

package family.crypto.ports

import kotlin.jvm.JvmInline

/**
 * Opaque domain value types for the MLS-shaped crypto ports (TASK-123, spec task-123).
 *
 * Every type here is a thin `value class` over `ByteArray`/`String`. Two hard rules from the
 * arch-packs govern this file:
 *  1. **No `@Serializable`, no vendor types** (FR-002; `:core:crypto` carries ZERO serialization
 *     since TASK-146). Wire encoding of these payloads (KeyPackage/Commit bytes, DTO +
 *     `schemaVersion`) is the TASK-124 real-adapter's job, never the domain's.
 *  2. **Opaque bytes** — the domain never inspects or forges MLS internals; it holds handles and
 *     passes them through (rule 1 isolation, ML-leak prevention).
 *
 * `Ciphertext` is deliberately NOT redefined here — it is reused verbatim from
 * [family.crypto.api.values.Ciphertext] (FR-006, no second type).
 *
 * See docs/architecture/crypto-mls.md (port shape = Wire CoreCrypto) and data-model.md.
 */

/**
 * Opaque, client-side group handle. NOT the MLS `group_id` bytes exposed to the server —
 * that identifier stays opaque to the server (rule 13); this is the local handle a consumer
 * uses to address a conversation.
 */
@JvmInline
value class GroupId(val value: String)

/**
 * A member's identity: the Ed25519 signature public key, opaque to the domain.
 *
 * Typed for misuse-resistance (P4) — "pass arbitrary bytes as a member key" is unrepresentable.
 * NOT a duplicate of `:core:pairing`'s `PublicKey`: that type lives in a different layer and a
 * dependency from `:core:crypto` onto `:core:pairing` would be the wrong direction (data-model.md).
 */
@JvmInline
value class IdentityKey(val bytes: ByteArray)

/** Opaque MLS KeyPackage payload (RFC 9420). The domain treats the bytes as opaque. */
@JvmInline
value class KeyPackage(val bytes: ByteArray)

/**
 * Opaque pool-entry id for a published KeyPackage. Server-facing ids are opaque (rule 13);
 * the domain only ever holds the handle, never a server primary key.
 */
@JvmInline
value class KeyPackageId(val value: String)

/**
 * A reusable fallback KeyPackage (RFC 9750 §5.1, "last resort"). First-class here because the
 * client refill / claim semantics (FR-011) must distinguish it from a one-time KeyPackage.
 */
@JvmInline
value class LastResortKey(val bytes: ByteArray)

/** Opaque MLS Commit message (RFC 9420). */
@JvmInline
value class Commit(val bytes: ByteArray)

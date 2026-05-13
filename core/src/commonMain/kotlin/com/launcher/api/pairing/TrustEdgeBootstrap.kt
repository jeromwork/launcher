package com.launcher.api.pairing

/**
 * Result interface for `PairingService.claimAsAdmin(...)` per the
 * **reusable trust primitive** pattern (plan.md §Reusable trust primitive,
 * memory `project_qr_pairing_trust_primitive.md`).
 *
 * In spec 007 the single subtype is [com.launcher.api.link.Link] — admin↔Managed
 * trust edge. Future specs add their own subtypes **without modifying
 * `PairingService`**:
 *
 *  - spec 011 `TrustedContact`  — admin adds родственника as trusted contact
 *  - future `CallTrustEdge`     — incoming call authorisation (jitsi spec)
 *  - future `SubAdminLink`      — multi-admin (внучка)
 *  - backlog `DeviceReplacement` — config-portability
 *
 * Each subtype provides its own stable [edgeId] (Firestore doc ID) and
 * [createdAt] (epoch millis).
 *
 * **Not** declared `sealed`: subtypes intentionally live in their own feature
 * packages (`api.link`, future `api.contacts`, …) and Kotlin restricts sealed
 * subclasses to the same package. Exhaustiveness on `when` is therefore not
 * compiler-enforced; the trade-off is accepted because each consumer of
 * `claimAsAdmin` typically expects exactly one subtype per call-site, not a
 * polymorphic dispatch.
 *
 * TODO(adr): write ADR-007 "QR-pairing as project-wide trust primitive" when
 * the second subtype lands (spec 011 or earlier). See project-backlog
 * TODO-DOC-001.
 */
interface TrustEdgeBootstrap {
    val edgeId: String
    val createdAt: Long
}

---
name: checklist-backend-substitution
description: Verifies that any feature touching the backend is designed substitution-ready — the application can swap the third-party provider (Firebase, Firestore, Cloudflare, etc.) for an own-server implementation later without rewriting domain or UI. Per constitution Project-Specific Direction §7 (Backend Substitution Readiness) and CLAUDE.md rules 1–2 (domain isolation, Anti-Corruption Layer) and rule 8 (server migration tracking). Triggered whenever a spec or design discussion mentions backend, server, Firebase, Firestore, Realtime DB, Cloud Storage, Cloud Functions, Cloudflare, Worker, sync, remote config, remote command, auth, user identity, session, token, persisted user data, or shared storage. Does NOT block — it raises substitution cost and ensures the topic surfaces in the discussion.
---

# Checklist: backend-substitution

Keeps the future "swap third-party backend for our own server" alive as a working constraint in every backend-touching design — without scheduling that migration as a real spec. Anchored in:

- [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md) — Project-Specific Direction §7 (Backend Substitution Readiness).
- [`CLAUDE.md`](../../../CLAUDE.md) — rules 1 (domain isolated), 2 (ACL per external SDK), 5 (wire-format versioning), 8 (server-roadmap tracking).
- [`docs/dev/server-roadmap.md`](../../../docs/dev/server-roadmap.md) — destination ledger for client-side workarounds that should eventually move server-side.

## What this skill is and is not

- **Is**: a cost-of-future-swap audit. Reads the proposed feature, asks "if Firebase / Cloudflare / chosen provider disappeared, what would we rewrite?", flags places where the answer is "more than the adapter module", suggests how to redraw the seams, and ensures `TODO(server-roadmap)` markers land where applicable.
- **Is not**: an approval gate, a migration plan, or a trigger to actually replace the provider. The team is **not** planning the swap on any timeline. The point is that when we eventually do swap, the cost is bounded to data migration + one adapter rewrite, not to a domain/UI rewrite.

If a feature passes every gate below, recommend proceeding as designed. If a gate fails, propose the corrected shape; do not block unless the failure also violates CLAUDE.md rule 1 or 2 directly (those are hard rules).

---

## Adapter boundary

- [ ] CHK001 No provider type (`FirebaseFirestore`, `DocumentReference`, `DocumentSnapshot`, `QuerySnapshot`, `FirebaseUser`, `FirebaseAuth`, `StorageReference`, Cloudflare Worker request/response shapes, etc.) appears in any signature visible to domain code, UI code, or other feature modules.
- [ ] CHK002 Each provider has exactly one wrapper adapter module. Domain references **only the port**.
- [ ] CHK003 "Provider disappears tomorrow" test (CLAUDE.md rule 2): the spec answers, in writing, the number of files that would change. The answer MUST be bounded to one adapter module. If it is not, redraw the seams before implementation.

## Wire format

- [ ] CHK004 What we persist remotely is a domain-owned, schema-versioned data class — not a provider-shaped document. (Firestore `Timestamp`, `FieldValue.serverTimestamp()`, document-reference paths, server-side `arrayUnion()` semantics live in the adapter, not in the persisted domain model.)
- [ ] CHK005 The wire format carries an explicit schema-version field from its first commit (Article VII §3, CLAUDE.md rule 5).
- [ ] CHK006 A roundtrip test (write → read → assert equal) exists for the wire format and runs in CI.

## Identity

- [ ] CHK007 The domain primary key for "user" is a project-owned value (`UserId`, ULID/UUID, etc.) — not the Firebase UID or any provider-issued ID directly.
- [ ] CHK008 Provider-issued identifiers (Firebase UID, OAuth subject, etc.) are stored as **credentials inside the auth adapter** and mapped to `UserId` at the boundary. They are not exposed to the domain.
- [ ] CHK009 If the spec must use a provider UID as the domain ID for cost or simplicity reasons, this is explicitly called out as a one-way door (CLAUDE.md rule 3) with the exit ramp documented.

## Query/command surface

- [ ] CHK010 The domain talks to the backend in domain verbs (e.g., `userRepository.findActiveSessionsForUser(id)`), not in provider verbs (e.g., `firestore.collection("sessions").where("userId", "==", id).whereEqualTo("active", true).get()`).
- [ ] CHK011 No security-rules-shaped or transport-shaped logic ("re-read the doc to validate," "use server timestamp because rules require it") leaks into the calling code. Adapter handles those concerns.

## Server-roadmap surfacing

- [ ] CHK012 If this feature relies on a third-party-specific mechanism that would normally belong on our own server (security rules as business logic, client-side transactions for atomicity, Worker-side rate limiting, etc.), an entry exists or is added to [`docs/dev/server-roadmap.md`](../../../docs/dev/server-roadmap.md) per CLAUDE.md rule 8.
- [ ] CHK013 The corresponding code or spec carries an inline `// TODO(server-roadmap): <operation> should move server-side for <integrity | atomicity | privacy | scale>` marker so the constraint is visible at the point of use.

## Exemptions (intentionally provider-specific)

- [ ] CHK014 The feature does not classify FCM, APNs, SMS, telephony, biometrics, location, or contacts as "substitutable backend." These are platform integrations — wrap them in a port (CLAUDE.md rule 2), but do not over-engineer for swap.
- [ ] CHK015 The feature does not invent a needless cross-provider abstraction for an exempt platform integration (e.g., a "universal push channel" abstracting FCM + a hypothetical own-push) per CLAUDE.md rule 4 (Minimum Viable Architecture).

## Cost-of-swap summary (output deliverable)

- [ ] CHK016 The spec or design note ends with one short paragraph: *"If this provider were replaced by our own server, the work would be: rewrite adapter X, run data migration Y on collection Z, switch DI binding W. Estimated bounded cost: N files."* This paragraph is the skill's primary value — it forces the topic into every backend-touching discussion.

---

## How to apply

1. Identify every backend-touching surface in the spec (read, write, listen, sync, auth, storage).
2. For each surface, walk the gates. CHK001–CHK003 are hard (they restate CLAUDE.md rules 1–2). The rest are advisory: a failure is a discussion item, not a stop.
3. Produce the cost-of-swap paragraph (CHK016). Surface it in the spec's design section or in the `speckit-analyze` punch list.
4. If gates surface a one-way door (CHK009, or any "we can't keep the seam this time"), invoke the `mentor` skill before the decision is committed in code.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-backend-substitution: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/backend-substitution.md`. Scratch buffer permitted, must be deleted before returning. Cost-of-swap paragraph and any `TODO(server-roadmap)` items land as edits to `spec.md` / `plan.md` (or as a note in the discussion thread if no spec yet), not in a checklist file.

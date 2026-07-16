---
name: checklist-zero-knowledge-server
description: Verifies that any new or modified server endpoint respects the zero-knowledge posture per CLAUDE.md rule 13 — server sees only opaque blobs and opaque IDs, no ownership graph, no event-type routing, no business logic on plaintext. Also enforces the server-log accumulator model (rule 13 skill enforcement) — every server touch point MUST leave an entry in docs/dev/server-log.md. Triggered whenever a spec introduces or modifies an HTTP endpoint / Worker route / API handler / any server-side state. Companion to checklist-server-hardening (rule 12 zero-trust) — same trigger, orthogonal concerns.
---

# Skill: checklist-zero-knowledge-server

## When to invoke

- Any spec that introduces a **new** server-facing endpoint (Cloudflare Worker route, future Go microservice handler, HTTP API).
- Any spec that **modifies** an existing endpoint's contract (new field, new method, new stored state).
- Any spec that references `push-worker/`, `workers/`, `POST /`, `GET /`, `endpoint`, `route`, `handler`, `API`, `Worker`, `server-side`, `Firestore`, `Cloudflare KV`, `Durable Object`.

Called automatically by `procedure-assess-spec-complexity`. Manual invocation also valid.

Runs **in parallel with** `checklist-server-hardening` — same triggers, different concerns. Rule 12 (server-hardening) = "how we defend each request". Rule 13 (zero-knowledge, this skill) = "what the server is allowed to know at all".

## Rationale (why zero-knowledge)

Per CLAUDE.md rule 13 — server sees only opaque blobs and opaque identifiers. Even under valid authenticated requests (rule 12 zero-trust satisfied) the server MUST NOT learn business meaning:

- DB dump reveals encrypted blobs + opaque IDs, not content, not ownership, not who-talks-to-whom.
- Hosting provider insider sees the same opaque view — no metadata graph to extract.
- Migration to own-server does not require re-encrypting historic data — the smart-server never had plaintext to begin with.

Client-side encryption without server-side ignorance is insufficient: an ACL graph or event-type field on the server is metadata leak even when content is encrypted.

## Checklist items

Emit red-only summary in chat per ADR-011 §5: `checklist-zero-knowledge-server: N/Total ✓, FAIL: CHK-XXX (short why)`. Chat line = only artefact. Do NOT create `specs/<NNN>/checklists/zero-knowledge-server.md`; scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.

### Principle 1 — Sealed Server Default (Tier 0 unless justified)

- **CHK-101**: Endpoint tier declared explicitly (Tier 0 sealed / Tier 1 minimal directory / Tier 2 server-required logic).
- **CHK-102**: Tier 1 elevation justified — the endpoint is a pubkey/token discovery pattern (X3DH, FCM routing) where async setup is impossible without a directory. No relationships stored beyond `opaque ID → pubkey bytes`.
- **CHK-103**: Tier 2 elevation justified with concrete client-side bypass scenario (Clear App Data / factory reset / root). Refuse pattern 25 fires otherwise.
- **CHK-104**: Authorization = Ed25519 signature verification against a pubkey recorded at namespace/entity creation. NOT an ACL graph, NOT a server-side membership table (refuse pattern 23).

### Principle 2 — Client Coordinates, Server Stores

- **CHK-201**: Endpoint stores opaque ciphertext, not typed records. Server does not deserialize the blob to apply business logic.
- **CHK-202**: No `eventType` / `messageType` / `intent` field read by the server (refuse pattern 22). Push routing uses opaque target token list + collapse key as opaque hash (refuse pattern 26).
- **CHK-203**: Retention model declared — cron-time TTL (client-set header at PUT) OR client LIST+DELETE. No business-rule retention on server («keep last N», «delete when refcount=0») — refuse pattern 24.
- **CHK-204**: Group / membership / share concept (if present) is a **client-coordinated keyring blob inside namespace**. Server does not maintain a members table.
- **CHK-205**: Forward unsharing / member kick (if present) is client re-key + re-encrypt + new blob write. Server does not enforce access revocation.
- **CHK-206**: No server-side cron enforcing business rules over decrypted content. Time-based retention OK, content-based NOT OK.

### Principle 3 — Opaque Identifiers Everywhere

- **CHK-301**: URL path uses opaque UUIDs (`/namespaces/{nsId}/blobs/{key}`), never identity-provider primary keys (`/users/{uid}/...`, `/accounts/{email}/...`) — refuse pattern 21.
- **CHK-302**: Stored identifiers are opaque UUIDs, not derived from Google `sub`, email, phone number, Firebase Auth UID.
- **CHK-303**: No server-side join between identity-links and data tables (no `SELECT ... FROM data JOIN links ON ...`). Mapping `userUid → nsId[]` stays on the client.
- **CHK-304**: No `linkId` / `pairingId` / `groupMembershipId` as a first-class server concept exposing the social graph.

### Wire format compliance

- **CHK-401**: Every blob shape has `schemaVersion` per rule 5 (wire format versioning).
- **CHK-402**: Push payload (if any) is encrypted under a shared key known only to sender + recipients. Size accounted for against transport constraint (FCM 4KB — see server-log.md Q-2).

### Server-log accumulator (rule 13 enforcement)

- **CHK-501**: Every server touch point in the spec has a corresponding entry in `docs/dev/server-log.md` — either Part A (confirmed pattern, referenced by this spec) or Part B (open question raised by this spec). Refuse pattern 27 fires otherwise.
- **CHK-502**: No standalone server-research-task created in the launcher repo to hold open questions from this spec. Open questions go into `server-log.md` Part B under the source feature-task — refuse pattern 28.
- **CHK-503**: Consistency check — new decisions in this spec do not contradict existing `server-log.md` Part A entries. If contradiction detected → add Part C entry, block spec until resolved.

### Non-goals + exit ramps

- **CHK-601**: Explicit non-goals section — what zero-knowledge properties are NOT achieved at MVP + why (e.g. traffic-pattern analysis at Cloudflare level, sealed-at-rest without hardware attestation) + exit ramp per each (server-roadmap.md destination, own-server migration trigger).
- **CHK-602**: Cross-reference to `docs/architecture/server.md` (current-state snapshot) and `docs/dev/server-requirements.md` v2 (sketch).

## Interaction with other checklists

- **checklist-server-hardening** — rule 12 zero-trust. Same trigger, orthogonal concerns. Rule 12 = "how we defend"; rule 13 (this) = "what the server may know". Both must pass.
- **checklist-security** — MASVS-level app security. Zero-knowledge = server-side privacy counterpart.
- **checklist-wire-format** — schema versioning of blob shapes. Zero-knowledge cares about opaqueness to server, wire-format cares about client-parseable evolution.
- **checklist-backend-substitution** — zero-knowledge posture must be portable to own-server migration (rule 8). Own-server does not gain new visibility.

## Server-log update procedure

When running this checklist on a feature spec:

1. **Read** `docs/dev/server-log.md` fully (Part A + Part B + Part C).
2. **Extract** server touch points from the spec (endpoints, stored state, push events).
3. For each touch point:
   - Match against Part A — if the pattern is already confirmed, add a **reference** from this spec (no duplicate Entry).
   - New pattern → propose a new Part A Entry (source = this task, tier, refs).
   - Open question → propose a new Part B Entry (Q-N, context, blocked-by, trigger).
   - Detected contradiction with Part A → propose a new Part C Entry, block spec.
4. **Confirm with owner** before writing to `server-log.md` (owner is source of truth for decisions).
5. **Write** confirmed entries to `server-log.md` in the same commit as the spec change. Update the Journal table at the bottom.

## Refuse pattern

If any CHK-101..502 fails, refuse per CLAUDE.md refuse patterns 21-28:

- Surface which CHK-N failed and why.
- Cite the matching refuse pattern number.
- Propose corrected shape (opaque ID instead of userUid, client-coordinated keyring instead of ACL table, Part B Entry instead of standalone research-task, etc.).
- Do not proceed to implementation phase.

If CHK-503 (Part C contradiction) fires — spec is **blocked** until owner resolves by either updating Part A or reformulating the spec's server touch point.

## Reference

- CLAUDE.md rule 13 — universal principle (server sees only opaque blobs).
- CLAUDE.md refuse patterns 21-28 — specific violations.
- CLAUDE.md rule 12 — companion posture (zero-trust). Runs via `checklist-server-hardening`.
- `docs/dev/server-log.md` — accumulator (Part A / B / C).
- `docs/dev/server-requirements.md` v2 — sketch of endpoint tiers (S0/S1/S2).
- `docs/dev/client-requirements-for-zero-knowledge-server.md` — client-side deltas required to keep server sealed.
- `docs/architecture/server.md` — current-state snapshot (pre-migration to full zero-knowledge).
- TASK-57 — adoption task for rule 13 + this skill.

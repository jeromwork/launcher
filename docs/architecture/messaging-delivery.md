# Messaging delivery — the blind-courier server (`DeliveryServicePort`)

**This file is the single source of truth for the messenger's server side** — the delivery service, the mailbox, the KeyPackage directory, push routing, and the Cloudflare→own-Rust path. If it and any other doc disagree on the messenger server, this file wins — except: the umbrella/cutting is [`messaging.md`](messaging.md), the client pipe is [`messaging-substrate.md`](messaging-substrate.md), the zero-trust/zero-knowledge endpoint baseline is [`server.md`](server.md) (rules 12/13), crypto is [`crypto.md`](crypto.md). Change the model → update this file in the same commit.

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: the messenger server is a **blind courier** behind `DeliveryServicePort`. It sees only an **opaque group id + integer epoch + opaque mailbox/recipient tokens + ciphertext**. Zero crypto ops, zero content, zero identities, zero social graph (rule 13). It does exactly three jobs: **(1) serialize Commits** — one per epoch per group (integer compare, reject dupes); **(2) store-and-forward** sealed blobs into opaque mailboxes until drained; **(3) hand out KeyPackages** one-time so offline devices can be added. Chat messages are **not** ordered by the server — the client sorts (substrate S4).

**Own Rust server is the TARGET; Cloudflare is a rule-8 stopgap.** There is **no permissive turnkey MLS server** — Phoenix (`phnx`/`air`) and Wire (`wire-server`) are **AGPL** (do not adopt the code). So the server is **build, not buy**: assemble permissive Rust frameworks + write the handlers from **Phoenix's published design** (clean-room from docs, never the AGPL repo).

**Build-vs-buy**

| Block | Verdict | Component |
|---|---|---|
| Web framework / runtime / DB | 🟢 import | **axum + tokio + sqlx + PostgreSQL** (MIT/Apache) |
| MLS engine (server-side validation if any) | 🟢 import | openmls (MIT) — but the DS needs almost no crypto |
| Commit serialization (one per epoch) | 🟡 thin glue | integer epoch compare per group |
| Mailbox / store-and-forward | 🟡 thin glue | opaque blob store keyed by opaque token |
| KeyPackage directory (one-time fetch + last-resort) | 🟡 thin glue | atomic fetch-and-decrement — architecture owned by [`crypto-mls.md`](crypto-mls.md) (RFC 9750 + Signal drain-defense) |
| Push routing | 🟡 thin glue | FCM data-only wake-ping (opaque) |
| The DS as a whole | 🔴 own build | assemble the above from **Phoenix design** — no turnkey |

**Stopgap mapping (Cloudflare, while own-Rust is built)**: Durable Object per group = the serialization point + WebSocket + mailbox (one primitive, three jobs, all opaque); Cloudflare Queues = fan-out; KV/R2/D1 = KeyPackage directory; FCM = push. Every one keeps the server blind. `TODO(server-roadmap)` at each site (rule 8).

**Invariants** (D1–D5, see §Invariants). **Status**: designed, not built (messenger = TASK-27; endpoints inherit [`server.md`](server.md) rules 12/13; a new endpoint MUST leave a `server-log.md` entry).

**Routing**: server behaviour / delivery / KeyPackage → stay here. Endpoint hardening baseline (auth/rate-limit/validation) → [`server.md`](server.md). Client contract → [`messaging-substrate.md`](messaging-substrate.md). Crypto/epoch model → [`crypto.md`](crypto.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **D1 — blind courier (rule 13).** Opaque group id + integer epoch + opaque tokens + ciphertext; the server never decrypts, never learns identity, ownership, event type, or relationships. Symptom of violation: any handler that reads a plaintext field, a `userUid`, or an `eventType`.
- **D2 — app-loose, Commit-serialized ordering.** Chat = best-effort relay, client re-sorts; only MLS Commits are serialized one-per-epoch-per-group (RFC 9420 §3.2/§7.4). The serialization is an integer compare on the outside of a sealed blob — not crypto.
- **D3 — opaque identifiers everywhere (rule 13).** URL paths use `/namespaces/{nsId}/…`, never `/users/{uid}/…`. No Google `sub`, email, phone, or Firebase UID as a routing/storage key; the `userUid → nsId` map stays client-side.
- **D4 — client coordinates, server stores (rule 13).** No server-side membership graph, no business-rule retention (client LIST+DELETE; server only cron-TTL), no push routing by event type. The recipient token list is provided explicitly by the client.
- **D5 — own Rust is the target; Cloudflare is a rule-8 stopgap.** Behind `DeliveryServicePort`; migration is an adapter swap, tracked with inline `TODO(server-roadmap)` and in `docs/dev/server-roadmap.md`.

## Industry grounding (copy the design, not the code)

- **RFC 9750** (MLS Architecture) — the DS is an untrusted, ordered relay + the AS binds identity↔key; MLS puts almost no crypto in the server. https://www.rfc-editor.org/rfc/rfc9750.html
- **RFC 9420** §3.2/§7.4 — one accepted Commit per epoch; concurrent Commits must be tie-broken by the DS. https://www.rfc-editor.org/rfc/rfc9420.html
- **Phoenix R&D** (docs.phnx.im) — the most complete MLS-native, metadata-minimizing DS/QS/AS design (identity-blind, graph-blind via per-group pseudonyms + unlinkable queues). SRLabs 2024 audit. **Code is AGPL — reimplement from the docs.** https://docs.phnx.im/spec.html
- **Signal Sealed Sender / X3DH prekey directory** — the metadata-privacy + KeyPackage-directory patterns. https://signal.org/docs/
- Rust stack: axum/tokio/sqlx (MIT/Apache), PostgreSQL (permissive), openmls (MIT).

## Rejected (do not re-litigate)

- ❌ **Matrix homeserver** — must see the membership/social graph to route + resolve room state; breaks rule 13 (D1/D3/D4).
- ❌ **AGPL servers as code** (Phoenix `air`/`infra`, Wire `wire-server`) — network-copyleft breaks the commercial model. Reuse the published design only.
- ❌ **Server-side membership/ACL graph, business-rule retention, event-type routing** — rule 13 refuse patterns 21-26. Client coordinates; server stores opaque blobs + cron-TTL.
- ❌ **`userUid` as a routing/storage key** — opaque `nsId` only (D3).

## Related

- Umbrella + cutting: [`messaging.md`](messaging.md). Client pipe: [`messaging-substrate.md`](messaging-substrate.md). Endpoint baseline (rules 12/13) + `server-log.md`: [`server.md`](server.md). Crypto/epoch: [`crypto.md`](crypto.md). Migration destination: [`../dev/server-roadmap.md`](../dev/server-roadmap.md).
- Owning feature task: TASK-27 (messenger). KeyPackage defense decision: TASK-104. Zero-knowledge posture: TASK-57.

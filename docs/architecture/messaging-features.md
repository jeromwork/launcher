# Messaging features — the message-feature taxonomy (domain)

**This file is the single source of truth for message features** — reactions, replies, edits, deletes, forwards, mentions, receipts, typing, pins — and for group governance (roles, block, kick, mute, invite). If it and any other doc disagree on features/governance, this file wins — except: the umbrella/cutting is [`messaging.md`](messaging.md), the transport pipe is [`messaging-substrate.md`](messaging-substrate.md), crypto is [`crypto.md`](crypto.md), versioning is [`wire-format.md`](wire-format.md). Change the model → update this file in the same commit.

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: a message feature is **NOT a library and NOT a vendor event** — it is **our own domain typed-message that points at another message id, plus a client render rule**. A reaction is `Reaction(targetId, emoji)`; the transport adapter (Matrix now / MLS later) only *marshals* it. **There is no per-feature SDK anywhere** — the reusable asset is a published *taxonomy* (Matrix event relations + IETF MIMI), which we copy clean-room into our domain. This is the concrete home of umbrella **INV-M1** (taxonomy in domain, adapters marshal) — the single cut that prevents rewriting every feature on a transport swap.

**Why no library**: the industry models every one of these as "another message with different properties" — verbatim from the MIMI content draft. So the work per feature is a small domain type + a render rule + adapter marshalling, not an integration.

**The taxonomy (copy the SHAPE from the spec, put the TYPE in our domain)**

| Feature | Our domain type (shape) | Copy shape from |
|---|---|---|
| Reaction (emoji) | `Reaction(targetId, emoji)` | Matrix `m.annotation` (MSC2677) / MIMI `reaction` |
| Reply / quote | `Reply(inReplyTo, body)` | Matrix `m.reference` / MIMI `inReplyTo` |
| Thread | `ThreadRef(rootId)` | Matrix `m.thread` / MIMI `topicId` |
| Edit | `Edit(replaces, body)` | Matrix `m.replace` (MSC2676) |
| Delete / unsend | `Delete(targetId)` | Matrix redaction / MIMI `delete` |
| Forward | `Forward(sourceId, body)` | app-level (new message, copied content) |
| Mention @ | `Mentions(ids)` | Matrix `m.mentions` |
| Read / delivery receipt | `Receipt(uptoId, kind)` | Matrix `m.receipt` / MIMI receipts |
| Typing | `Typing(conversationId)` (ephemeral) | Matrix `m.typing` (define our own) |
| Pin | `Pin(targetId)` | app-level (conversation state) |
| Personal block (user blocks user) | `BlockUser(id)` | app-level: client filter + optional MLS Remove if co-grouped |
| Disappearing / self-destruct (TTL) | `SetExpireTimer(seconds)` + per-msg TTL | WhatsApp (start-on-send)/Signal (start-on-read); server-side = client-set TTL, blind server cron-deletes ([`messaging-delivery.md`](messaging-delivery.md) D-inv, rule 13) |
| View-once media | `viewOnce` flag on a media pointer | WhatsApp view-once; **not server-enforced**; blob in [`gallery.md`](gallery.md), optional single-fetch-delete |
| Contact card | `ContactCard(ref)` | prefer identity-reference (ties to [`crypto-pairing.md`](crypto-pairing.md) TrustEdgeBootstrap) over raw vCard PII |
| Location (static / live) | shapes here; sensor+SOS in [`safety.md`](safety.md) | Matrix MSC3488/3489 — `LocationShare` / `LiveShareStart` / `LocationBeacon` / `LiveShareStop` |
| Sticker / GIF / custom emoji (sent) | media pointer message | the sent item = media blob ([`gallery.md`](gallery.md)); the pack = shareable config artifact (rule 9) |

**Honest limits (do NOT market as security)**: disappearing-messages and view-once are **UX affordances, not guarantees** — a modified client, screenshots, or OS backups defeat them. MLS forward secrecy discards keys so an *expired* message can't be re-derived, but a client that kept a plaintext copy keeps it. Server TTL is courtesy cleanup, not a control.

**Group governance** (roles / block / kick / mute / invite): also **100% our app-logic** — MLS enforces NO access control (RFC 9750 §3.5). The *crypto* eviction is an MLS Remove+Commit (owned by [`crypto.md`](crypto.md)); *who may* evict is our policy. Copy the shape from **Matrix power-levels** + **IETF MIMI room-policy** draft. Device-management group revoke is already contracted in [`crypto-pairing.md`](crypto-pairing.md) (TASK-102) — the messenger group is separate (TASK-27), its governance policy lives here when built.

**Search**: client-side only — the server is blind (rule 13). Index with SQLite FTS5 / Tantivy on device.

**Invariants** (F1–F4, see §Invariants). **Build-vs-buy**: 🟡 everything here — thin app-logic, copy the spec, no lib. **Status**: designed, not built (messenger = TASK-27, m-4).

**Routing**: a feature's shape/render → stay here. How it travels → [`messaging-substrate.md`](messaging-substrate.md). Crypto eviction mechanics → [`crypto.md`](crypto.md). Versioning of a feature's wire shape → [`wire-format.md`](wire-format.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **F1 — feature taxonomy lives in our domain; the adapter only marshals** (umbrella INV-M1). Symptom of violation: a feature type that embeds a Matrix event JSON or an MLS frame, or a feature implemented only inside a vendor adapter. This is the anti-rewrite cut — a transport swap must not touch features.
- **F2 — a feature is a typed message referencing another message id.** Not a mutable column, not server state — an append-only typed message the client interprets (MIMI model: "modeled as another message with different properties"). Edit/delete are *tombstone/replace* messages, not in-place mutation.
- **F3 — group governance is application-layer; MLS enforces none** (RFC 9750 §3.5). We own the policy of who-may-add/remove/mute; MLS owns only the cryptographic add/remove. Symptom of violation: expecting the crypto layer or the server to enforce an admin role.
- **F4 — search is client-side only.** The server sees ciphertext (rule 13), so full-text search indexes on the device. No server-side search, no "encrypted search" lib dependency.

## Industry grounding (copy the design, not the code)

- **IETF MIMI content** (`draft-ietf-mimi-content`) — the model that reactions/replies/edits/deletes/receipts are "another message with different properties." https://datatracker.ietf.org/doc/draft-ietf-mimi-content/
- **Matrix event relations** — MSC2674 (relations), MSC2675 (aggregations), MSC2676 (edits), MSC2677 (reactions), `m.thread`, `m.mentions`, `m.receipt`. https://spec.matrix.org/latest/
- **Group governance** — Matrix power-levels (`m.room.power_levels`) + IETF MIMI room-policy (`draft-ietf-mimi-room-policy`). https://datatracker.ietf.org/doc/draft-ietf-mimi-room-policy/
- Legal: copying a published *taxonomy/design* is clean-room; never lift vendor *code*.

## Rejected (do not re-litigate)

- ❌ **Feature implemented as a vendor event type inside the adapter** — forces rewriting every feature on transport swap (F1/INV-M1).
- ❌ **Server-side governance graph / roles table** — server would learn the social graph (rule 13, refuse pattern 23). Governance is client-coordinated policy over the crypto group.
- ❌ **Server-side or "encrypted search" service** — server is blind (F4/rule 13); search is on-device.
- ❌ **In-place mutation for edit/delete** — breaks the append-only typed-message model (F2) and forward-secrecy assumptions.

## Member directory / display names — encrypted roster, NO server directory (architected from industry standard)

**Grounded in Signal (encrypted profiles + Private Group System) + MLS (RFC 9420 group state)** — synthesised. This resolves the "encrypted co-admin display directory" question: **there is no server-side identity/admin directory.** The problem splits into two encrypted blobs the server stores opaquely and cannot read:

- **Per-user profile (name + avatar)** — encrypted under a per-user **profile key** distributed over the E2E channel (Signal pattern); the server holds an opaque ciphertext profile blob per user and forwards a key it never sees.
- **The roster (who is in the group + roles)** — an **encrypted roster blob inside the group state** (`member → displayName + role`), encrypted to the current members and **re-keyed on every membership change**. In MLS this rides the group's per-epoch secret: add/remove is a Commit → new epoch secret a removed member cannot derive → the re-encrypted roster is unreadable to them (TreeKEM, intrinsic). Sent as an MLS `PrivateMessage`, the server sees only ciphertext.

**Server posture (rule 13)**: stores opaque profile ciphertext + opaque roster/group-state ciphertext + a namespace signing pubkey; authorization = **signature/credential verification, not an ACL graph** (Signal KVAC or MLS membership proof). It never learns names, avatars, roles, or the membership graph. Roles (owner/admin/caregiver) are **app-level, inside the encrypted roster**, never a server field (F3/rule 13 principle 2). Counter-example to avoid: Matrix (roster + room metadata plaintext on the homeserver — a hard retrofit, the cautionary tale for rule 13).

**Build-vs-buy**: 🟢 **openmls** (MIT) provides the re-keying group substrate (epochs, credentials, ratchet tree — see [`crypto-mls.md`](crypto-mls.md)); Signal `zkgroup` is **AGPL** → take the *profile-key encryption pattern* but **reimplement** it on our AEAD, don't link. 🟡 you write the versioned profile-blob + roster-blob wire formats (rule 5), the profile-key distribution policy, and role semantics — all opaque-to-server. Resolves TASK-114. Sources: Signal profiles https://signal.org/blog/signal-profiles-beta/ ; Signal Private Group System https://signal.org/blog/signal-private-group-system/ (ePrint 2019/1416) ; RFC 9420 https://www.rfc-editor.org/rfc/rfc9420.html ; RFC 9750 https://datatracker.ietf.org/doc/html/rfc9750 .

## Related

- Umbrella + cutting: [`messaging.md`](messaging.md). Transport pipe: [`messaging-substrate.md`](messaging-substrate.md). Server: [`messaging-delivery.md`](messaging-delivery.md). Crypto group + eviction mechanics: [`crypto.md`](crypto.md) / [`crypto-pairing.md`](crypto-pairing.md) / [`crypto-mls.md`](crypto-mls.md). Versioning: [`wire-format.md`](wire-format.md).
- Owning feature task: TASK-27 (messenger).

# Messaging substrate — the one stable seam (`MessagingPort`)

**This file is the single source of truth for the messaging *substrate*** — the pipe that carries an opaque encrypted payload to a set of recipients through a blind server, and the contract every faucet (chat, gallery, config-sync) sits on. If it and any other doc disagree on the substrate, this file wins — except: the umbrella/cutting is [`messaging.md`](messaging.md), crypto is [`crypto.md`](crypto.md), versioning is [`wire-format.md`](wire-format.md), server endpoints are [`server.md`](server.md). Change the model → update this file in the same commit (the `messaging` skill is a thin router, never a second copy).

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: the **messenger substrate** — `MessagingPort` — takes an **opaque payload** + an **opaque recipient-token list** and delivers it through a **blind courier server**. It serves the MESSENGER only (chat + calls + feature messages). It is **not** the config-sync mechanism (that is envelope-per-recipient + Firestore, domain [`ecs.md`](ecs.md)), and media/gallery blobs travel by *pointer* via the sibling [`gallery.md`](gallery.md) `MediaPort`, not through here. The payload is sealed by the crypto domain (MLS); the substrate never opens it. This is the stable seam: the transport *behind* the port (Matrix maybe-MVP → MLS target) and the server *behind* the delivery port (Cloudflare stopgap → own Rust) both swap without touching the domain.

**Zone charter**

| Owns | Must NOT own |
|---|---|
| the `MessagingPort` contract; the message envelope shape (opaque body + routing metadata); the ordering model (app loose / Commit serialized); the offline mailbox contract; the "taxonomy in domain" rule | the crypto (MLS/keys — `crypto.md`); the concrete transport/server (adapters); the *meaning* of a feature payload (that is domain feature-logic); versioning (`wire-format.md`) |

**The four contracts**

1. **`MessagingPort`** (domain port, PLANNED): `send(payload: Sealed, to: List<RecipientToken>, kind: Envelope.Kind)`, `observe(): Flow<IncomingEnvelope>`, plus group ops delegated to `GroupPort` (`crypto.md`). Domain types only — no `Room`, no `Event`, no `@user:server`, no MLS frame.
2. **Envelope**: `{ routing: {opaqueGroupId, epoch, kind}, body: Sealed }`. `body` is ciphertext the substrate never opens. `routing` is the *outside of the sealed letter* the courier reads to sort — nothing more.
3. **Ordering**: **application (chat) messages are NOT server-ordered** — the client sorts by an inner monotonic counter/timestamp (loose delivery is fine). **Only Commits (group changes) are serialized** — exactly one per epoch per group; the server accepts by integer-comparing `epoch`, rejects duplicates ("epoch taken, retry as epoch+1"). No decryption, ever.
4. **Offline mailbox**: store-and-forward. The server holds sealed envelopes in an opaque mailbox (`RecipientToken`) until the owner drains it, then deletes. It sees "mailbox #X holds N sealed blobs" — not who, not what.

**Invariants** (S1–S5, see §Invariants): S1 opaque-payload + opaque-recipients only. S2 **feature taxonomy lives in the domain, adapter marshals** (the anti-rewrite cut). S3 blind courier (rule 13). S4 app-loose / Commit-serialized ordering. S5 big blobs via `MediaPort`, pointer via `MessagingPort`.

**Status**: designed, not built (0 code). Owning task TASK-27; SoT task TASK-148. Consumes crypto (`GroupPort`/`CryptoPort`/`KeyPackagePort`) — not re-decided here.

**Routing**: substrate question → stay here. A feature's *meaning* (how a reaction renders) → domain feature-logic (umbrella §build-vs-buy, copy Matrix/MIMI taxonomy). Crypto → [`crypto.md`](crypto.md). Server endpoints → [`server.md`](server.md). Versioning → [`wire-format.md`](wire-format.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **S1 — opaque payload, opaque recipients.** `MessagingPort.send` takes a sealed body + a list of opaque recipient tokens the *client* computes. The substrate never learns plaintext, real identity, or the human relationship behind a token. (Rule 13: "client provides explicit opaque token list.")
- **S2 — feature taxonomy lives in the domain; the adapter only marshals.** The substrate carries an `Envelope.Kind` (an opaque-to-the-server discriminator) and a body; the *domain* owns what kinds exist (`Chat`, `Reaction`, `Reply`, `Edit`, `RoleChange`, …) and how they render. The Matrix adapter translates our kind ↔ a Matrix event; the MLS adapter translates our kind ↔ an MLS app-message. **A feature must never be defined only inside a vendor adapter** — that is the rewrite trap (umbrella INV-M1).
- **S3 — the server is a blind courier (rule 13).** It reads only `routing = {opaqueGroupId, epoch, kind-is-opaque-bytes}` + recipient tokens. Zero crypto ops, zero content, zero social graph. (RFC 9750 assumes an untrusted Delivery Service; Phoenix demonstrates identity- and graph-blind delivery.)
- **S4 — app-loose, Commit-serialized ordering.** Chat messages tolerate out-of-order delivery and are sorted client-side; only MLS Commits require the server to enforce one-per-epoch-per-group (integer compare on the envelope's `epoch`). (RFC 9420 §3.2/§7.4: one accepted Commit advances the epoch; concurrent Commits must be tie-broken by the DS.)
- **S5 — big blobs do not ride the group ratchet.** Media/gallery payloads are content-key-encrypted into a blob store via `MediaPort`; only a small *pointer* (blob id + wrapped content key) travels as a `MessagingPort` message. (Umbrella INV-M2.)

## The cut, drawn out (why this seam survives every swap)

```
UI / feature-logic (domain)         ← stable: knows Conversation/Message/Reaction, never a vendor type
        │  MessagingPort.send(sealed, [tokens], kind)
        ▼
MessagingPort  (the seam)           ← stable contract
        │  implemented by ↓  (volatile, swappable)
   ┌────┴─────────────┐
   │ MatrixAdapter    │  maybe-MVP: our kind ↔ Matrix event; talks to a Matrix homeserver
   │ MlsAdapter       │  target: our kind ↔ MLS app-message; talks to DeliveryServicePort
   └──────────────────┘
        │  opaque envelope
        ▼
DeliveryServicePort → server        ← volatile: Cloudflare stopgap → own Rust; blind courier
```

Swapping Matrix→MLS = write `MlsAdapter`, flip DI. The domain and UI do not change (rules 1, 2, 6). Swapping Cloudflare→own-Rust = a `DeliveryServicePort` adapter change. **The only thing that would force a domain rewrite — a feature defined inside the adapter — is banned by S2.**

## Ordering, in plain terms (the part everyone over-thinks)

- **"Привет, бабушка" (chat)**: server is a dumb relay. If two chats arrive swapped, the *client* re-sorts by an inner counter. The server never orders these and never opens them.
- **"add / remove member, re-key" (Commit / epoch change)**: the server enforces *one Commit per epoch per group* so the group's key state does not fork. It does this by reading an **integer on the outside of the sealed blob** (`epoch`), accepting the first, rejecting the rest ("occupied, retry as epoch+1"). This is bookkeeping on a number — **not** knowledge of crypto, content, or people.

This is why the server stays blind while still keeping the group consistent — see [`messaging.md`](messaging.md) INV-M3 and [`crypto.md`](crypto.md) for the MLS epoch model.

## Data-migration honesty (the exit-ramp cost the facade does NOT erase)

`MessagingPort` makes the transport swap a *code* change. It does **not** make it a *data* change:
- If MVP ships on Matrix, message **history** is Megolm-encrypted on a Matrix server and does not auto-move to an MLS group; **identities** are Matrix MXIDs.
- Migration = either abandon MVP-era history or write a one-time migration. Record the concrete cost in TASK-27 at build time; leave an inline `TODO(exit-ramp)` at the adapter persistence site.

Keep the `our-opaque-id ↔ vendor-id` mapping **only inside the adapter**, never in the domain, so identity coupling cannot leak upward.

## Exit ramps

| Concern | Today | Exit ramp |
|---|---|---|
| Transport (Matrix vs MLS) | facade only, 0 code | second adapter behind `MessagingPort`; data migration cost per TASK-27 |
| Server (Cloudflare vs own Rust) | stopgap design | `DeliveryServicePort` adapter swap; `TODO(server-roadmap)` (rule 8) |
| Ordering guarantees | design | if loose app-ordering proves insufficient, add per-group sequence in the DS (additive) |

## Related

- Umbrella + cutting: [`messaging.md`](messaging.md). Crypto (MLS/keys): [`crypto.md`](crypto.md). Server: [`server.md`](server.md). Versioning: [`wire-format.md`](wire-format.md).
- Feature taxonomy blueprint (copy the design): Matrix event relations + IETF MIMI drafts — see [`messaging.md`](messaging.md) §Copyable blueprints.
- Owning feature task: TASK-27. SoT task: TASK-148.

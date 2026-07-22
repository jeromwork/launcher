# Messaging calls — voice/video (`CallPort`)

**This file is the single source of truth for voice/video calling.** If it and any other doc disagree on calls, this file wins — except: the umbrella/cutting is [`messaging.md`](messaging.md), crypto/MLS is [`crypto.md`](crypto.md), the pipe is [`messaging-substrate.md`](messaging-substrate.md). Change the model → update this file in the same commit.

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: calls are the one place we **adopt a whole component** — a self-hosted **SFU** (Selective Forwarding Unit) — behind `CallPort`. **Jitsi** (Videobridge + Meet, **Apache-2.0**) is the chosen SFU (already the basis of TASK-27). The client uses standard **WebRTC** (libwebrtc / `webrtc-rs`). We do **not** write an SFU. What we *do* build is the thin **E2E media-key glue**: encrypt media frames with **SFrame** (RFC 9605) keyed from the **MLS group** — design copied from **Discord DAVE** / Element Call.

**Honest metadata caveat**: an SFU is a media relay — it inherently sees **call participation + timing** (who is in the call, when, how long), even when frame *content* is E2E-encrypted. Strong metadata-blind group calls are a hard, separate problem — **deferred**, not solved by SFrame. For the family MVP this is acceptable; it is stated here so no one assumes call-metadata is zero-knowledge like the message path.

**Build-vs-buy**

| Block | Verdict | Component |
|---|---|---|
| SFU (server media relay) | 🔴 adopt whole | **Jitsi Videobridge** (Apache-2.0) |
| WebRTC client | 🟢 import | libwebrtc (BSD) / `webrtc-rs` (MIT/Apache) |
| E2E media encryption | 🟡 thin glue | **SFrame** (RFC 9605) + MLS-derived keys, design from Discord DAVE |
| Signalling / call UX | 🟡 our code | our domain, via `CallPort` |

**Invariants** (CL1–CL4, see §Invariants). **Status**: designed, not built (TASK-27, Jitsi-based).

**Routing**: calls / SFU / media-keys → stay here. MLS group the keys derive from → [`crypto.md`](crypto.md). The message pipe (calls are NOT messages) → [`messaging-substrate.md`](messaging-substrate.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **CL1 — adopt a whole SFU, never build one.** Jitsi (Apache-2.0). Building an SFU is out of scope; `CallPort` abstracts it so Jitsi↔LiveKit is an adapter swap.
- **CL2 — E2E media = SFrame (RFC 9605) keyed from the MLS group.** The SFU forwards ciphertext frames it cannot decrypt; keys derive from the group's per-epoch secret (design from Discord DAVE / Element Call).
- **CL3 — call metadata is NOT zero-knowledge (honest caveat).** The SFU sees participation + timing. This is inherent to media relaying; strong metadata privacy for calls is deferred, not claimed.
- **CL4 — calls ride `CallPort`, not the message pipe.** Real-time media is a separate stack; a call is not an MLS application message.

## Industry grounding (copy the design, not the code)

- **Jitsi** — Videobridge + Meet, **Apache-2.0**, self-hostable SFU. https://github.com/jitsi/jitsi-videobridge
- **SFrame — RFC 9605** — per-frame AEAD for E2E media over an SFU. https://www.rfc-editor.org/rfc/rfc9605.html
- **Discord DAVE** — reference design: MLS group key → per-sender ratcheted media keys → frame encryption. https://daveprotocol.com/
- **Element Call** — WebRTC Insertable Streams + SFrame, moving to MLS for group key scaling. https://element.io/blog/secure-video-conferencing-for-matrix/
- WebRTC: libwebrtc (BSD), `webrtc-rs` (MIT/Apache).

## Rejected (do not re-litigate)

- ❌ **Janus (GPLv3)** as SFU — copyleft; commercial license required. Use Jitsi/LiveKit (Apache-2.0).
- ❌ **Building our own SFU / WebRTC stack** — enormous, no benefit; adopt Jitsi.
- ❌ **Claiming zero-knowledge calls** — false; the SFU sees participation (CL3). Do not market call-metadata as blind.
- ❌ **Routing calls through the MLS message pipe** — real-time media needs an SFU, not the group ratchet (CL4).

## Related

- Umbrella + cutting: [`messaging.md`](messaging.md). MLS group (key source): [`crypto.md`](crypto.md). Message pipe: [`messaging-substrate.md`](messaging-substrate.md). Media blobs (not calls): [`gallery.md`](gallery.md).
- Owning feature task: TASK-27 (Elderly-Friendly Messenger, Jitsi-based).

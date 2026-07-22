---
kind: architecture-domain
domain: gallery
audience: [owner, ai-agent]
purpose: Rich-media & gallery domain — the media mechanism (blob + pointer, MediaPort) and the album/sharing product. A SIBLING domain to messaging; both consume this, neither owns the other.
components:
  - id: media-mechanism
    choice: content-key-encrypt a blob into an opaque store + send only a POINTER {blobId, wrappedKey, meta} through the transport; transform (compress + EXIF strip + thumbnail) before encrypt
    port: MediaPort
    status: designed, not built (0 code)
    decision-task: TASK-148
    consumed-by: [gallery-album, messaging chat attachments]
  - id: transform
    choice: on-device compress + EXIF/metadata strip + thumbnail before encryption
    status: decided (TASK-110)
    decision-task: TASK-110
  - id: codecs
    choice: permissive only — Opus (BSD), libvpx VP8/VP9 (BSD), image/Coil (MIT/Apache); NEVER x264/x265 or FFmpeg-with-gpl
    status: designed
  - id: album-product
    choice: photo album + share-to-family; retention client-driven; NOT identity-bound state (shareable per rule 9 where applicable)
    status: designed, not built
    owner-task: TASK-27 (adjacent), future gallery task
  - id: upload-tokens
    choice: signed upload tokens + quotas for the blob store
    status: NOT decided
    owner-task: TASK-111
last-synced: 2026-07-22
---

# Домен: Gallery & rich media — umbrella (`MediaPort`)

**This is the single source of truth for rich media** — photos, voice messages, files/documents, video — and the gallery/album product. Gallery is a **sibling domain to messaging**, not a zone inside it: the messenger's chat attachments and the gallery/album both **consume** the media mechanism here. If it and any other doc disagree on media, this file wins — except: crypto (content keys) is [`crypto.md`](crypto.md), the transport that carries the pointer is [`messaging-substrate.md`](messaging-substrate.md), versioning is [`wire-format.md`](wire-format.md). Change the model → update this file in the same commit.

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: rich media does **NOT** ride the group ratchet. A photo/voice/file/video is **encrypted with its own content key into an opaque blob store**, and only a small **pointer** — `{blobId, wrappedContentKey, meta}` — travels through the transport ([`messaging-substrate.md`](messaging-substrate.md)). Before encryption, media is **transformed on-device**: compress + strip EXIF/metadata + thumbnail (TASK-110 decided). Codecs are permissive; the blob store is blind (rule 13). **This mechanism (`MediaPort`) is shared** by two consumers: the **gallery/album** product and the **messenger's chat attachments** — neither owns the other.

**Why a sibling domain, not a messaging zone**: media is used by chat AND by the standalone gallery; anchoring it inside "messaging" would wrongly imply the album needs the messenger. The mechanism is shared infra; the album is its own product.

**Build-vs-buy**

| Block | Verdict | Component |
|---|---|---|
| Image compress / thumbnail / resize | 🟢 import | Coil (Android, Apache-2.0), `image` crate (MIT/Apache) |
| EXIF strip | 🟢 import | AndroidX `ExifInterface` (Apache), `kamadak-exif` (MIT/Apache) |
| Voice-message codec | 🟢 import | Opus / libopus (BSD) |
| Video codec | 🟢 import | **libvpx VP8/VP9 (BSD)** — ⚠️ avoid x264/x265 (GPL) |
| Blob store (opaque) | 🟡 thin glue | R2 / object store keyed by opaque blobId |
| Pointer message + chunked upload | 🟡 our code | via `MediaPort` + the transport |
| Album product (albums, sharing, retention) | 🟡 our code | domain logic on top |
| View-once media | 🟡 flag + single-fetch | `viewOnce` flag on the pointer; server deletes an opaque blob after first GET (blind); NOT enforceable (screenshots) |
| Stickers / custom emoji (sent item) | 🟢/🟡 | sent = a media blob; taxonomy from Matrix **MSC2545**; WhatsApp/stickers = BSD *sample* (pattern, not a lib) |
| Sticker / emoji PACK | 🟡 shareable config | pack manifest = **rule 9 shareable artifact** (`schemaVersion` + `ConfigSource`, deidentified) — ecs.md-shaped, not media |
| GIF search | ⚠️ opt-in `GifSourcePort` | Giphy (forced "Powered by GIPHY" attribution) / Tenor (Google API ToS); **privacy leak** — query+IP go to a third party, OFF the blind server; never default for care/clinic |

**Invariants** (G1–G5, see §Invariants). **Status**: designed, not built (media transform = TASK-110 decided; upload tokens = TASK-111 open).

**Routing**: media mechanism / transform / blob / pointer / album → stay here. How the pointer travels → [`messaging-substrate.md`](messaging-substrate.md). Content-key crypto → [`crypto.md`](crypto.md). Blob wire-shape versioning → [`wire-format.md`](wire-format.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **G1 — blob + pointer, never through the ratchet.** Media encrypts with a content key into a blob store; only `{blobId, wrappedContentKey, meta}` travels as a message. Symptom of violation: a multi-MB payload inside a group message. (Signal/Matrix/WhatsApp all do this.)
- **G2 — transform before encrypt** (TASK-110): compress + strip EXIF/metadata + thumbnail on-device before encryption. Privacy (EXIF GPS) + cost (size).
- **G3 — permissive codecs only.** Opus (BSD), libvpx VP8/VP9 (BSD), `image`/Coil (MIT/Apache). ❌ x264/x265, FFmpeg-with-gpl (copyleft).
- **G4 — the blob store is blind (rule 13).** Ciphertext keyed by an opaque blobId; never sees plaintext, owner, or which conversation/album a blob belongs to.
- **G5 — the mechanism is shared, the products are distinct.** `MediaPort` serves both chat attachments (messaging) and the gallery/album; the album is NOT part of the messenger and must not depend on chat.
- **G6 — sticker/emoji PACKS are shareable config artifacts (rule 9), not media.** A pack manifest carries `schemaVersion`, loads via a `ConfigSource` adapter, and is deidentified (no identity inside) — ecs.md-shaped. The *sent* sticker is a media blob; the *pack* is a portable artifact from day one.
- **G7 — third-party GIF search is an opt-in port with a privacy leak.** `GifSourcePort` (Giphy/Tenor) sends query+IP to a third party — it does NOT and CANNOT route through the blind server. Off by default for the care/clinic posture; Giphy also forces attribution UI. Treat like an exit-ramp TODO, never a silent default.

## Industry grounding

- Encrypt-blob-send-pointer is universal — Signal, Matrix, WhatsApp encrypt media with a per-blob key in a CDN/blob store and send the key reference.
- Client media transform (compress + EXIF strip + resize) — TASK-110; standard privacy hygiene.
- Codecs: Opus (BSD), libvpx (BSD), AndroidX ExifInterface (Apache), Coil (Apache), Rust `image`/`kamadak-exif` (MIT/Apache). Copyleft to avoid: x264/x265 (GPL), FFmpeg `--enable-gpl`.

## Rejected (do not re-litigate)

- ❌ Big media blobs through the group ratchet — encrypt-and-pointer (G1).
- ❌ GPL codecs (x264/x265, FFmpeg-with-gpl) — use libvpx/libopus (BSD) (G3).
- ❌ Uploading raw media with EXIF — transform first (G2).
- ❌ Server understanding which conversation/album a blob belongs to — blind store (G4/rule 13).
- ❌ Making the gallery/album depend on the messenger — sibling domains, shared mechanism only (G5).

## Related domains

- Transport (carries the pointer): [`messaging-substrate.md`](messaging-substrate.md). Content-key crypto: [`crypto.md`](crypto.md). Versioning: [`wire-format.md`](wire-format.md). Blob-store endpoint: [`messaging-delivery.md`](messaging-delivery.md) / [`server.md`](server.md).
- Sibling: [`messaging.md`](messaging.md) (chat attachments consume `MediaPort`).
- Owner tasks (history, not truth): TASK-110 (media transform), TASK-111 (upload tokens — open), TASK-27 (messenger attachments).

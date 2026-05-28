# Ecosystem Vision — Future Companion Apps

**Date**: 2026-05-28
**Status**: Vision document — not committed to roadmap timeline
**Purpose**: Record long-term product direction for Family Care Ecosystem so design decisions taken now don't preclude it. Updated when launcher MVP is shipping and we have signal on which adjacent products to build next.

---

## Core principle (from CLAUDE.md + decisions 2026-05-28)

The **launcher** is a single-purpose tool — provides preset-based configurable home screen for elderly users, managed remotely by a relative. It is **NOT** a messenger, family album, or social network. Those are separate products that **may** be built later, share the same identity / pairing infrastructure, and integrate with the launcher via share-target / SDK / tap-to-launch.

The launcher's **competitive advantage** is the **preset system** (FlowPreset architecture + Wizard module + customizable layouts) — not encryption, not group management, not photo sync.

---

## Why "Family Group" primitive was removed from launcher (2026-05-28)

Spec 013 originally introduced `GroupRepository`, `MembershipRepository`, `EnvelopeAdapter` with N-recipient encryption, and Cloudflare Worker arbitration. After product review:

- **No use case in launcher itself** requires N-recipient shared content. Multi-admin scenario (3 admins → 1 grandma) is solved by N independent pair edits, merged on grandma's device via spec 008 bidirectional sync.
- **Shared media (family album)** is a separate product, NOT part of launcher.
- **Shared chat (messenger)** is a separate product, NOT part of launcher.
- **CLAUDE.md rule 4** (Minimum Viable Architecture): no current consumer for group primitive → premature.
- **CLAUDE.md rule 3** (one-way doors): adding group later is **additive** over pair-encryption (envelope from N=1 to N=many is additive), exit ramp preserved.

**Therefore spec 013 was archived. The group primitive lives in future companion apps**, not in launcher.

---

## Where Family Group will live in the future

### Future product 1 — Family Messenger

Separate Android app. Allows family members to chat (group chats, direct messages), share location, voice messages.

- **Why group primitive needed here**: group chat = N participants seeing same messages. This IS the use case for group-encryption (envelope with N recipients).
- **Reuse from launcher**:
  - `Pair` primitive (spec 007) for 1-to-1 direct messages.
  - `EncryptedMediaStorage` (spec 011) for media attachments.
  - `AeadCipher` / `AsymmetricCrypto` (spec 011) for content encryption.
- **New for messenger**:
  - `GroupRepository` (originally drafted in spec 013, now will live here).
  - `MembershipRepository`.
  - Envelope encryption with N>1 recipients.
  - Server-side membership arbitration.
  - Multi-tier audit log (Tier 1 plaintext metadata + Tier 2 encrypted payload).

### Future product 2 — Family Album / Content Viewer

Separate Android app. Lets family share photos / videos / care notes with role-based filtering (e.g., medical worker sees CareContent but not FamilyContent).

- **Why group primitive needed here**: shared media for N family members. Role-based access.
- **Reuse from launcher**: same as messenger.
- **Or**: integrate with Google Photos / similar SDK instead of building from scratch. This is a **build-vs-integrate** decision deferred until product validation.

### Future product 3 — Family Flow / activity dashboard

Separate Android app. Shows family activity timeline, upcoming events, health summaries.

- **Why group primitive**: timeline scoped per family group.

---

## Cross-app group sync — open architectural question

User raised 2026-05-28: "If user installs Simple Launcher preset which bundles our messenger + family flow viewer, do they have to register / sign in / create group separately in each app? Can we synchronize groups across our ecosystem apps automatically?"

This is an **architectural one-way door** that affects multiple future products. **Do NOT design it now**. Record the question and constraints for when we have ≥2 ecosystem apps in development.

### Constraints to preserve

- **Pair primitive (spec 007)** must remain identity-independent — pair-keys do not encode "which app uses this pair". This way, all ecosystem apps can reuse the same pair.
- **Identity (Firebase anonymous UID currently; F-4 will add named auth)** must be a **shared identity** across ecosystem apps on the same device. NOT separate identity per app.
- **Group membership store** should not be launcher-internal — if it lives in launcher's Firestore collections, other ecosystem apps can't access it. Possible solutions (deferred):
  - **Centralized identity / group service** (separate Worker endpoint, shared by all ecosystem apps).
  - **First-installed app becomes "group manager"** — others delegate to it via SDK / Intent (the pattern user mentioned).
  - **Each app independently** uses pair (no shared group primitive).

### Open questions to revisit later

1. Is there a single account / identity for all ecosystem apps, or per-app?
2. When user installs preset that bundles multiple apps, can registration / Google Sign-In be done once?
3. How does group membership invitation cross app boundaries? (User invites cousin to family — does it appear in messenger, album, flow all at once?)
4. What is the IPC / SDK story between launcher and messenger when they're separate APKs? (Bound services? Content provider? Share intents?)
5. Permissions model: does messenger need to request its own contacts/storage/etc, or inherit from launcher?
6. Update / version compatibility: launcher v2 + messenger v1 — how do we avoid breakage?

### Decision criteria for picking a path later

- **User signal**: which adjacent product (messenger / album / flow) generates demand first.
- **Cost of duplicate registration UX**: friction users tolerate.
- **Privacy posture**: more cross-app integration = more attack surface.
- **Backend cost**: shared identity infra is cheap; per-app identity is wasteful.

---

## What we explicitly preserve from spec 013

Even though spec 013 itself is archived (DEPRECATED), the following content from it must survive in future messenger spec:

- **Domain model**: `Group` entity, `Membership` entity, `Role` enum (Admin / CoAdmin / Member / Managed / Caregiver), TTL on membership.
- **Envelope encryption (N>1 recipients)**: producer generates per-content CEK, wraps for each recipient via `crypto_box_seal(CEK, pub_i)`. **NO** shared group key (priv_G does not exist) — fundamental architectural invariant.
- **Server arbitration via Cloudflare Worker**: signed membership operations (Ed25519), atomic Firestore transactions, `membership_version` monotonic counter.
- **Audit log Tier 1 + Tier 2** structure (plaintext metadata for multi-admin merge UI; encrypted payload for privacy).
- **One-way doors and exit ramps** documented in spec 013 (see archived spec.md).
- **Edge cases**: last admin cannot leave (`LastAdminCannotLeave`), TTL clock authority (server-side), groupId collision resistance.

When messenger spec is written, copy these sections verbatim — they were thoroughly designed.

**Reference**: archived spec at `specs/013-family-group-foundation/spec.md` (with DEPRECATED header).

---

## What we explicitly DO NOT preserve

- **Pair → Group migration** (FR-019..FR-022 in spec 013). Pair stays pair. Group is **new** in messenger, not a refactor of pair.
- **schemaVersion bumps for config / envelope** caused by group introduction in launcher. Launcher's wire-formats stay as-is.

---

## TL;DR на русском

**Family Group** как примитив **не нужен в лаунчере** — multi-admin сценарий решается через N независимых pair-edit'ов, shared content (фото, чат) уходит в **отдельные приложения экосистемы** (мессенджер, family album, family flow). Spec 013 архивируется как DEPRECATED, всё ценное из неё (доменная модель Group/Membership, envelope encryption на N recipients, server arbitration, audit log Tier 1/2) **сохраняется** в этом vision-документе и переедет в спеку мессенджера, когда придёт его время. **Конкурентное преимущество** лаунчера — система пресетов (Simple Launcher, Workspace, и будущие — для разных типов пользователей), а не шифрование группы. **Открытый вопрос на будущее** — cross-app group sync (если установлен пресет с lauchner + messenger + album, должен ли пользователь регистрироваться один раз или в каждом app отдельно?). Не решаем сейчас — записываем constraints и criteria для решения, когда будет ≥2 ecosystem app'а в разработке.

# Crypto Architecture — Current Status (Start Here)

**Purpose**: короткий "start here" файл для новой сессии AI или нового collaborator'а работающих над крипто-архитектурой проекта. Обновляется вручную после mentor-сессий.

**Last updated**: 2026-07-03.

---

## TL;DR для fresh session

1. **Модель работы**: [CLAUDE.md rule 11](../../CLAUDE.md) — architectural decisions живут как backlog-tasks в статусах `Discussion` → `Draft` → `Done` с immutable `### Decision (English, immutable) 🔒` блоками. Cross-task references — только через `dependencies:`.
2. **Skills**: `mentor` (для Discussion sessions), `backlog-task-format` (формат task'ов), `procedure-decision-drift-check` (walk dependencies).
3. **Пилотная миграция от legacy SoT-файлов запушена**: см. commit `1ed9c41` (bootstrap) + последующие. Ветка `crypto-backlog-migration-pilot`.
4. **Legacy files с deprecation banner'ами**:
   - [`crypto-mentor-overview.md`](crypto-mentor-overview.md) — 1994 строк, incremental migration on touch.
   - [`crypto-open-questions.md`](crypto-open-questions.md) — register закрытых Q-NN; priority queue актуален.
   - [`crypto-topics-handoff.md`](crypto-topics-handoff.md) — historical, may reference retired patterns.
5. **Не создавать новые `docs/dev/*-mentor-overview.md` файлы**. Все architectural decisions — decision-task'и в backlog.

---

## Recently decided (sessions 2026-07-02 → 2026-07-07)

| Task | Title | Status | Что решено |
|---|---|---|---|
| [TASK-100](../../backlog/tasks/task-100%20-%20Decision-History-backup-strategy-for-MVP.md) | History backup strategy for MVP | Done | MVP Signal-style (нет истории после recovery); Phase-3+ WhatsApp-style opt-in backup. |
| [TASK-101](../../backlog/tasks/task-101%20-%20Decision-Peer-confirmation-on-recovery.md) | Peer confirmation on recovery | Draft | Chrome-model auto-add + post-facto notification. Multi-device как first-class. |
| [TASK-102](../../backlog/tasks/task-102%20-%20Decision-Revoke-policy.md) | MLS revoke policy | Draft | Three-tier language (owner/admin/other), MVP flat + admin, identity-level UI, no blacklist. |
| [TASK-103](../../backlog/tasks/task-103%20-%20Decision-Remote-app-lock-for-stolen-device.md) | Remote app lock for stolen device | Draft | Full logout + Keystore wipe = crypto defense (не UX). 5 preset fields. |
| [TASK-104](../../backlog/tasks/task-104%20-%20Decision-KeyPackage-rate-limit.md) | KeyPackage rate limit | Draft | Signal-inspired hybrid: pool cap + claim dedup + last-resort. 4 preset fields в `PresetV2.mls`. |
| [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) | Server-side abuse defense baseline | Draft | Contract stability first-class + zero-trust posture. CLAUDE.md rule 12 + refuse pattern 20 + `checklist-server-hardening` skill. |
| [TASK-106](../../backlog/tasks/task-106%20-%20Decision-Sybil-resistance-and-signup-gate.md) | Sybil resistance / signup gate | Draft | LOCAL-first identity generation; QR pairing = cloud gate. `identity_id = hash(root_public)` (заменяет старый `stableId` naming). |
| [TASK-107](../../backlog/tasks/task-107%20-%20Decision-Abuse-response-mechanism-legal-minimum.md) | Abuse response umbrella | **Paused** | Post-MVP scope: arbitration + open/closed groups + auto-detection. Legal + product ownership needed. Blocks TASK-11, TASK-28. |
| [TASK-108](../../backlog/tasks/task-108%20-%20Decision-Metadata-privacy-what-server-sees.md) | Metadata privacy | Draft | T0 MVP (identity_id + group roster + timing visible). Opaque `OwnerRef`/`BucketKey`/`PushTopic`/`GroupRef` ports → T1 adapter swap ~2-3 недели. T2+ (Signal sealed sender, VOPRF) parked. |
| [TASK-109](../../backlog/tasks/task-109%20-%20Decision-Durable-Objects-concrete-design-security-critical-endpoints.md) | Anti-brute-force / Durable Objects | **Paused** | Own-server phase concrete design. Baseline (TASK-105) уже определила ladder RATE_LIMITER → DO. Осталось: which endpoints classify security-critical + concrete DO schema. |
| [TASK-110](../../backlog/tasks/task-110%20-%20Decision-Client-side-media-transformation.md) | Client-side media transformation | Draft | WhatsApp pattern: compression + EXIF strip + resize on client, потом encrypt. Server видит только encrypted blob. Preset fields via TASK-16. |
| [TASK-111](../../backlog/tasks/task-111%20-%20Decision-Signed-upload-tokens-quotas-abuse-response.md) | Signed upload tokens + quotas | Draft (Deferred) | Cloudflare R2 presigned URL + DO counter per (pseudonym, resource). 100 MB per identity quota. Формально Deferred — реализация в TASK-11/28 vertical. |
| [TASK-112](../../backlog/tasks/task-112%20-%20Decision-Cross-platform-IdentityVault.md) | KeyVault port boundary | Draft (2026-07-07) | `KeyVault` port + `Purpose` enum (4 variants) + sealed `VaultException` + `Ciphertext`/`Mac`/`DerivedKeyBytes` newtypes + inband schemaVersion prefix + AutoCloseable zeroize. Sync API (not suspend), Kotlin idiomatic exceptions (not Outcome). Migration path: existing `RootKey.bytes` → internal, `KeyRegistry` → internal helper, `ConfigCipher2` → refactored to consume `KeyVault`. |
| [TASK-16](../../backlog/tasks/task-16%20-%20Preset-Schema-v2-Wizard-Engine.md) | Wire format evolution discipline | Draft (2026-07-07) | **Reformulated from stale "Preset Schema v2 + Wizard Engine"**. `schemaVersion: String` с Kubernetes-style suffix (`"1-alpha.N"` pre-MVP, `"1"` stable). Two modes / one enforcement point (fitness rule reads suffix). E2E-encrypted formats use Bitwarden first-byte inband; plain-JSON use top-level string field. Skill `checklist-wire-format` extended with bump discipline. Pre-MVP → GA switch = one-time ceremony (git tag + sweep). Preset shape reference short doc в `docs/architecture/INDEX.md`. Applies to all 7 wire formats (Profile / Preset / Recovery blob / Bucket / Ciphertext / QR / Push). |
| [TASK-58](../../backlog/tasks/task-58%20-%20Research-Signal-Sender-Keys-vs-MLS-for-family-group-E2E.md) | MLS library formal choice | **Done — superseded** (2026-07-07) | Closed as superseded by TASK-104. MLS vs Sender Keys decided in early sessions (MLS chose for post-compromise security). openmls vs mls-rs decided in TASK-104 mentor-session. Formal Decision resides in `docs/architecture/crypto.md` frontmatter with 2026-07-07 evidence anchors (Wire production, Discord DAVE March 2026, RCS adoption, SRLabs 7/8 fixed, UniFFI KMP maturity). Implementation ownership → TASK-2 (F-CRYPTO) + TASK-42 (Family group encryption). |
| [TASK-114](../../backlog/tasks/task-114%20-%20Decision-Encrypted-co-admin-display-directory.md) | Encrypted co-admin display directory | Draft (2026-07-07) | UI multi-admin shows display names ("Мама Таня") без metadata leak. Encrypted bucket через TASK-66 registry; server sees only opaque bytes. `AdminDisplayDirectoryPort` в domain. Preset fields displayNameMaxLength / allowEmojiInDisplayName. Depends TASK-102, TASK-108. |

**Read Decision blocks (English) для machine-readable контракта** — downstream tasks должны иметь `dependencies: [TASK-N]` для этих decisions.

---

## Currently in Discussion

*(нет активных Discussion — TASK-112 закрыта Decision Session 2 2026-07-07, статус → Draft)*

---

## Paused (waiting on trigger)

- **[TASK-107](../../backlog/tasks/task-107%20-%20Decision-Abuse-response-mechanism-legal-minimum.md)** — Abuse response umbrella. Post-MVP, требует legal + product perspective. Blocks TASK-11, TASK-28.
- **[TASK-109](../../backlog/tasks/task-109%20-%20Decision-Durable-Objects-concrete-design-security-critical-endpoints.md)** — Anti-brute-force / Durable Objects concrete design. Baseline (TASK-105) уже установила ladder RATE_LIMITER → DO. Ждёт owner input.
- **[TASK-113](../../backlog/tasks/task-113%20-%20Refactor-Outcome-to-sealed-exceptions.md)** — Outcome → sealed exceptions refactor (2026-07-07). Не блокирует TASK-112. Триггер unpause: TASK-58 закрыта + начата implementation TASK-42/TASK-67 (Rust FFI активно используется).

---

## Priority queue — next candidates (revised 2026-07-07)

**High** (recommended immediate next):

1. **TASK-16 implementation** — wire format discipline (CLAUDE.md rule 5 extension + fitness rule + skill extension + preset shape reference). Decision block закрыт 2026-07-07, готова к `/speckit.specify`. ~1 неделя (skill + docs + fitness rule).
2. **TASK-112 implementation** — port `KeyVault` реализовать в `core/keys/`. Decision block закрыт 2026-07-07, готова к `/speckit.specify`. ~1 неделя mechanical work.
3. **TASK-2 implementation** — F-CRYPTO Core crypto module foundation (openmls integration через UniFFI, cargo-ndk build, adapter). Formal MLS library choice confirmed 2026-07-07 в crypto.md frontmatter. ~32-49 часов к emulator smoke.

**Medium** (deferred, physical/platform-dependent):

- **Huawei без GMS push fallback** (Q-13) — HMS Push Kit / MQTT / WebSocket. Physical device dependent (у владельца нет Huawei). Blocks TASK-58 Huawei smoke gates.
- **SOS payload wire format** (surfaced из crypto-mentor-overview Блок 7 supersession note) — конкретный wire format для SOS inline payload ≤ 2.5KB после base64. Отдельный decision-task при работе над SOS feature (не MVP scope).

**Full open-questions register** — [crypto-open-questions.md](crypto-open-questions.md).

---

## Continuation prompts (для новой сессии)

**Если владелец продолжает крипто-работу с fresh session'а**, полезные starting prompts:

- **«Продолжай следующую тему из priority queue»** — AI открывает `crypto-status.md`, берёт top candidate из «High» списка, создаёт новый TASK-N в Discussion, запускает `mentor` skill.
- **«Открой [TASK-N]»** — AI читает task file, определяет статус (Discussion → продолжить mentor session; Draft → готова к /speckit.specify; Done → informational).
- **«Проверь drift»** — AI запускает `procedure-decision-drift-check` skill — walk dependencies, flag downstream tasks с superseded upstream decisions.
- **«Что уже решено?»** — AI читает эту таблицу выше + git log за последнюю неделю.

**Anti-patterns для fresh session'а** (что делать НЕ надо):

- ❌ Читать `crypto-mentor-overview.md` как authoritative SoT — там deprecation banner, legacy content.
- ❌ Считать что priority queue из `crypto-open-questions.md` актуальный только по Q-NN — некоторые Q мигрированы в TASK-100+.
- ❌ Создавать `docs/dev/*-mentor-overview.md` файлы для новых доменов (backend, UX, i18n) — violates rule 11 universality.

---

## Downstream tasks awaiting Decision integration

Эти feature-tasks должны добавить соответствующие `dependencies: [TASK-100+]` при следующем touch'е (per pilot sweep + Session decisions). **Updated 2026-07-03**:

- **TASK-6** (Root Key Hierarchy) → dependencies на TASK-100, TASK-101, TASK-102, TASK-103, **TASK-105** (server baseline).
- **TASK-25** (Multi-app cohabitation) → dependencies на TASK-101, TASK-102, TASK-103.
- **TASK-27** (Messenger Jitsi) → dependencies на TASK-100, **TASK-105** (server baseline для messenger backend).
- **TASK-28** (Full family album) → dependencies на TASK-100, **TASK-105** (server baseline).
- **TASK-32** (Audit log) → dependencies на TASK-100, TASK-102, TASK-103.
- **TASK-40** (Multi-device) → **unparked by TASK-101 Decision** — multi-device теперь first-class. Update crypto-alignment: `parked` → `aligned` при следующем touch.
- **TASK-42** (Family group encryption) → dependencies на TASK-102, TASK-58, **TASK-104** (KeyPackage lifecycle).
- **TASK-46** (Shared admin book) → dependencies на TASK-102.
- **TASK-58** (MLS research) → должен produce financials по MLS choice, потом закрыт. Research из TASK-104 сузила варианты (см. priority queue #3).
- **TASK-67** (Pairing feature) → dependencies на TASK-101, TASK-102, TASK-103, **TASK-104** (KeyPackage), **TASK-105** (server baseline). Первый implementation с полным baseline.
- **TASK-70** (Profile sync) → dependencies на TASK-100, **TASK-105** (server baseline для config sync endpoints).
- **TASK-16** (Preset Schema v2) → **должен интегрировать** новые preset fields из **TASK-103** (`deviceLock` namespace: 5 fields) + **TASK-104** (`mls` namespace: 4 fields). Приоритетно (см. queue #2).
- **TASK-19** (Config sync) → dependencies на **TASK-105** (server baseline для endpoint hardening).

Integration происходит **на touch каждого таск'а**, не bulk update. Rule 11 «migration by touch».

---

## Skills reminder (для новой сессии)

Для крипто work'а обычно используются:

- **`mentor`** — invoke внутри Discussion-задачи для structured session.
- **`backlog-task-format`** — формат task-файла + Discussion + Decision block.
- **`procedure-decision-drift-check`** — walk dependencies, flag downstream drift.

Все остальные skills (speckit-*, checklist-*, etc.) — стандартные workflow'ы, не крипто-специфичные.

---

## Ветка / commit trail

- **Branch**: `crypto-backlog-migration-pilot`.
- **Bootstrap commit**: `1ed9c41` (add Discussion status, decision-task frontmatter, retire alignment-sweep).
- **Session commits 2026-07-02** (chronological): `76e19d1`, `fc06526`, `2e29a3f`, `2af8e5d`, `e9877ed`, `ddf236a`, `ba1b57a`, `7f47d5d` (handoff artifacts).
- **Session commits 2026-07-03**: `c7032ac` (TASK-105 baseline + rule 12 + skill), `3214de6` (TASK-104 KeyPackage decision).
- **PR** (когда готов to merge into main): https://github.com/jeromwork/launcher/pull/new/crypto-backlog-migration-pilot.

---

## Обновление этого файла

Обновляй **вручную** после каждой mentor-сессии или закрытия decision-task'а. Не auto-generated. Раздел «Recently decided» — top N последних (5-10), старее — переносится в footer «Historical decisions» (создать когда список расширится).

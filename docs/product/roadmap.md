# Roadmap — Family Care Ecosystem

**Дата**: 2026-05-28 · **Заменяет**: предыдущий roadmap (на vision'е «launcher для пожилых»). · **Источник vision'a и решений**: [`docs/product/use-cases/`](use-cases/README.md).

> **Update 2026-05-28 evening** — pre-F-1 adjustments applied based on mentor critique walkthrough ([`13-risks-and-critique.md`](use-cases/13-risks-and-critique.md)). Изменения:
> - **F-1**: добавлен multi-admin promotion safeguard (N/2+1).
> - **S-1**: wizard reduced 9 → 5 steps + autohints для остального.
> - **S-4**: app update SOS-deferral.
> - **Cross-cutting**: 4 бесплатных security mitigation, OEM matrix mandatory в spec template, two-tier audit log architecture, soft launch gate.
> - **V-2**: notes — Universal Preset extends на messenger (adult preset + elderly preset).

---

# 📘 Часть I — Как читать этот документ

## Что это за документ

Этот roadmap — **исполнительный план**, который превращает фиксированный vision (Family Care Ecosystem) и 28 закрытых D-вопросов в **конкретные спеки**, готовые к написанию через `/speckit.specify`.

Документ написан в **mentor-стиле**: каждая спека объяснена простым языком, с контекстом, откуда взялись решения. **Цель** — чтобы при старте работы над спекой ты мог:

1. Прочитать раздел этой спеки.
2. Понять, что строим и почему, без re-thinking уже обсуждённого.
3. **Скопипастить готовый prompt** в `/speckit.specify` — спека стартует.

## Структура каждой спеки в этом документе

Для каждой спеки (F-x в Foundation, S-x в MVP Vertical Slices) — **унифицированная структура**:

- **Что строим (mentor explanation)** — простыми словами, в 2-3 параграфа.
- **Зачем именно сейчас** — почему эта спека на этой стадии.
- **Источники и резолюции** — ссылки на use-cases + D-вопросы, которые она закрывает.
- **Scope: что входит** — конкретные components, ports, adapters.
- **Scope: что НЕ входит** — explicit boundaries.
- **Dependencies** — что должно быть готово до этой спеки.
- **Local Test Path** — что обязательно тестируется локально (per CLAUDE.md / D-2 rule).
- **Effort** — rough estimate.
- **Copy-paste prompt для `/speckit.specify`** — готовый текст для запуска спеки.
- **Notes / gotchas** — caveats, которые всплыли в discussion.

## Phase legend

| Phase | Что | Status |
|---|---|---|
| **Phase 0 — Vision** | Discussion 2026-05-27/28, 28 D-вопросов закрыты | ✅ DONE |
| **Phase 1 — Foundation (F-1..F-4)** | Архитектурные refactor'ы, делают систему ready для MVP фич | 🟡 Next |
| **Phase 2 — MVP Vertical Slices (S-1..S-8)** | Каждая = демо-able end-to-end фича | ⏸ После Phase 1 |
| **Phase 3 — Post-MVP v2 (V-1..V-5)** | iOS / Messenger / Album full / TV / Wearables начало | 🔮 |
| **Phase 4 — Long-term (L-x)** | Marketplace, AI implementations, B2B, etc. | 🔮 |

## Status icons

- ✅ DONE
- 🟡 In progress / next
- ⏸ Blocked by dependencies
- 🔮 Future / Long-term
- 🔵 Frozen (намеренно отложено)
- ❌ Explicitly out of scope

---

# 📗 Часть II — Vision Recap (one page)

> Полная версия — [`use-cases/01-vision-and-positioning.md`](use-cases/01-vision-and-positioning.md). Здесь — самое необходимое для контекста.

**Продукт** — **Family Care Ecosystem**, не «launcher для пожилых». Это:
- communication platform,
- remote support infrastructure,
- family coordination system.

Launcher — только interface surface. **Ядро** продукта: забота, связь, удалённая поддержка, безопасное сопровождение пожилого, снижение тревоги семьи.

**Архитектура — Universal Preset**:
- Единое ядро + 2 preset'a в MVP (Simple Launcher для Managed + Admin App для семьи).
- **Family Group** — primary primitive (N admin'ов + M Managed + caregivers).
- **Envelope encryption** для shared content (E2E preserved, сервер не видит контент).
- **Capability Registry + Exposure Adapters** (AI-ready, без provider implementations в MVP).
- **Wizard module** с reusable steps + nested config templates (shareability-ready per CLAUDE.md rule 9).
- **Subscription per admin** (family monthly), implementation детали — отдельная спека post-MVP.

**Главный фильтр фич**:
> Если фича **не усиливает** «Семья поддерживает связь и заботу о пожилом через единое безопасное пространство» — это **suspect feature**, требует обоснования.

**Что explicitly OUT**:
- Monetization billing flows (frozen, base зафиксирован)
- iOS Admin (post-MVP v2)
- Android TV preset (post-MVP)
- Full Family Album с видео/аудио (post-MVP v2)
- Elderly-Friendly Messenger embedded (post-MVP — будет separate app)
- Hardware SOS power-button (post-MVP)
- Dwell-to-activate accessibility (post-MVP)
- Social recovery (accepted edge case — «потерял так потерял»)
- AI provider implementations (architecture готова, конкретные adapter'ы — отдельные спеки when needed)
- Crashlytics / Sentry SDK (не нужны, Android Vitals достаточно)

---

# 📊 Часть III — Phases Overview

```
Phase 0: Vision ✅ DONE
        │
        ▼
Phase 1: Foundation (4 active specs + F-5 production-blocker, ~10-14 weeks)
   F-1 Family Group Foundation        ❌ DEPRECATED 2026-05-28
                                      (moved to ecosystem-vision.md)
   F-2 Capability Registry            ─┐
   F-3 Wizard Module + Localization   ─┼─── могут параллелизоваться
   F-4 AuthProvider + Google Sign-In  ─┘
   F-5 ConfigDocument E2E Encryption  🔴 PRODUCTION BLOCKER
                                      (must complete before release;
                                       does NOT block S-features dev)
        │
        ▼
Phase 2: MVP Vertical Slices (~16-20 weeks parallelized)
   S-1 Simple Launcher Wizard          (needs F-3, F-2)
   S-2 Admin App + Remote Pairing      (needs F-3, F-4)
   S-3 Contact Tiles + Calling         (needs F-2, S-1, S-2)
   S-4 SOS Capability                  (needs F-2, S-1, S-2)
   S-5 Contact Photos                  (needs S-3)
   S-6 Account Deletion                (needs F-4)
   S-7 Caregiver Invite                (needs F-4)
   S-8 Layout Editor + History         (needs S-2; was "Family Group
                                       Editor" — renamed since no group)
        │
        ▼ (PRODUCTION RELEASE — F-5 must be complete)
        │
        ▼
Phase 3: Post-MVP v2 (5 specs)
   V-1 iOS Admin · V-2 Messenger app · V-3 Full Album · V-4 Android TV · V-5 Wearables
        │
        ▼
Phase 4: Long-term (open)
   L-x: Clinic B2B · Marketplace · AI providers · etc.
```

**Critical path для first demo**: F-3 → S-1 → S-3 (~2-3 months sequential).

**Critical path для production release**: above + F-5 (E2E config encryption) + F-4 (AuthProvider). F-5 can be parallelized with S-features in development phase but MUST be merged before release.

**MVP total estimate**: ~6-8 months parallelized.

**Vision shift 2026-05-28**: Family Group primitive removed from launcher (was F-1). Multi-admin scenarios handled by N independent pair-edits merged via spec 008. Shared content (album, messenger) moved to separate ecosystem apps. Group primitive design preserved in [ecosystem-vision.md](future/ecosystem-vision.md) for future messenger/album specs. See research docs in `docs/research/2026-05-28-*.md`.

---

# 🏗️ Часть IV — Phase 1: Foundation (F-1 .. F-4)

> **Цель Phase 1**: подготовить архитектуру для всего, что строится в Phase 2. **Refactoring** existing system без потери функциональности.
>
> **Принцип**: каждая F-спека делает migration внутри. После F-x existing функциональность 002 / 007 / 010 продолжает работать, но через новый, более общий путь.

---

## F-1: Family Group Foundation — ❌ DEPRECATED 2026-05-28

> **Status**: ARCHIVED — moved to future companion apps (messenger, family album).
> **Reason**: Group primitive is not needed in launcher itself. Multi-admin scenarios solved via N independent pair-edits, merged on grandma's device through spec 008 bidirectional sync. Shared content (family album, group chat) belongs in separate ecosystem apps, NOT the launcher.
> **Where it lives now**: [docs/product/future/ecosystem-vision.md](future/ecosystem-vision.md) §Family Messenger / §Family Album. Archived spec at `specs/013-family-group-foundation/spec.md` (with DEPRECATED header).
> **Replacement**: F-5 (ConfigDocument E2E encryption) takes the Foundation slot as the new must-fix-before-production item. F-1 work redirected to expanding pair-based architecture in spec 014 (Contact Sharing UX Refinements).
> **Decision discussion**: research artifacts in `docs/research/2026-05-28-*.md`.

---

### Что строим (mentor explanation)

Сейчас наша система знает только **pair** — пара устройств (admin ↔ Managed), которая доверяет друг другу через спеку 007. Это работает для одного admin'а и одной бабушки, но **vision требует группу**: семья из N родственников, M устройств, плюс caregivers.

Мы строим **Family Group** — новый primary architectural primitive. Group объединяет участников (admin, co-admin, member, managed, caregiver), их устройства, общий trust state. Каждый участник имеет **свой собственный keypair**, никто не делится своим private key. Чтобы content был доступен всем — используется **envelope encryption** (контент шифруется случайным симметричным ключом K, K оборачивается для каждого recipient'a его pub-ключом, server хранит encrypted_content + N маленьких wrappers).

**Сервер** (Cloudflare Worker + Firestore) хранит только membership list + публичные ключи участников. **`priv_G` не существует** — сервер не может прочитать content (E2E сохранён).

После F-1 spec 011 расширяется группой, 007 продолжает существовать как 1-to-1 channel, 008 переписывается на group-level config sync.

### Зачем именно сейчас

Это **самый большой refactor** в MVP. Если делать его позже — каждая S-спека сверху будет привязана к pair-модели, и потом придётся переделывать всё. **Делаем первым**.

### Источники и резолюции

- Vision: [`use-cases/01-vision-and-positioning.md`](use-cases/01-vision-and-positioning.md) §Family Group System.
- Архитектура: [`use-cases/05-pairing-identity-trust.md` §Family Group Model](use-cases/05-pairing-identity-trust.md).
- **Closes D-25** (Family Group data model — refined B+C: pair-keys + envelope encryption + server arbitration).
- Extends спеку 011 (per-pairing crypto).
- Updates 007, 008, 009.

### Scope: что входит

- `GroupRepository` port в `core/domain/`.
- `MembershipRepository` port (many-to-many user↔group).
- `Membership` data class с `role`, `ttl_expiry`, `permissions_override`.
- `Group` data class с `type` (Family / Care / Other), `primary_admin_id`.
- Envelope encryption primitive (через **libsodium**: `crypto_box_seal` + `crypto_secretbox`).
- `EnvelopeAdapter` interface + real implementation + FakeAdapter.
- Server arbitration logic в Cloudflare Worker (extends push-relay из 007):
  - Endpoint для membership operations (add / remove / promote / kick), подписанных admin'ом.
  - Verify подпись против primary_admin_id или CoAdmin.
  - **Multi-admin promotion safeguard** (added 2026-05-28 evening — pre-F-1 adjustment): promotion нового admin'а требует подписи **большинства существующих admin'ов** (N/2+1). Singleton admin (без co-admin) — может promote единолично. Timeout 30 дней если N/2+1 не достигнут → request expires → repropose.
  - **Atomic membership ops via Firestore Transactions** (2nd pass 2026-05-28 evening): membership read + verification + write выполняются в Firestore Transaction для atomicity (защита от race condition «Bob удаляет Carol, Carol параллельно добавляет Dan»). Long-term — переезд на own server с настоящими ACID транзакциями (server-roadmap entry).
  - Update membership list, revoke tokens.
- **Two-tier audit log architecture** (added 2026-05-28 evening — pre-F-1 adjustment):
  - **Tier 1 (public metadata)**: actor pub_id, timestamp, action_type — в plaintext в Firestore. Нужно для multi-admin merge UI.
  - **Tier 2 (private payload)**: полный diff изменения — зашифрован actor's own pub_id (только сам actor может расшифровать). Хранится encrypted blob'ом.
  - Privacy boundary: court subpoena получит Tier 1 metadata, Tier 2 — технически не можем выдать (E2E).
- Migration: existing pair data → group с N=2 members (admin Role + Managed Role).
- Wire format `schemaVersion` bump для config + crypto.
- Roundtrip tests + **property-based crypto tests** (см. Cross-cutting Security Mitigations §VIII):
  - Sign → tamper → verify FAILS.
  - Encrypt different content with same K → ciphertext different (catches nonce reuse).
  - Replay protection: same message twice → server rejects second.

### Scope: что НЕ входит

- ❌ Real AuthProvider implementation (это F-4) — F-1 использует FakeAuthAdapter для тестов.
- ❌ Wizard integration (это F-3).
- ❌ Любые UI changes (это S-1..S-8).
- ❌ Caregiver roles + invitation flow (это S-7).
- ❌ Real-time group messaging crypto (Signal-style) — для shared content envelope достаточен.
- ❌ Social recovery / orphan admin handling (accepted edge case per D-25 evening).

### Dependencies

Должно быть готово **до** F-1:
- Спеки 005, 006, 007, 011 (Phase 0-2) — merged / code-complete.
- Cloudflare Worker push-relay (из 007) — должен работать.

### Local Test Path (D-2 mandatory)

- **FakeGroupRepository + FakeEnvelopeAdapter** unit tests: simulated 2-3 members в group, envelope roundtrip (encrypt for N → каждый decrypts своим priv).
- **Migration test**: load existing pair JSON, transform в group JSON, assert structure valid + readable старыми кодом.
- **Server arbitration integration test** через Cloudflare Worker **Miniflare** (local emulator).
- **Membership operations test**: signed by admin → applies, signed by member → 403, signed by removed user → 403.
- **Concurrent membership ops test**: race condition resolution (server's order-of-arrival wins).

### Effort

**Large** (~3-4 weeks). Самый большой refactor в MVP.

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для F-1: Family Group Foundation.

КОНТЕКСТ:
Продукт переходит от pair-модели (admin↔Managed) к Family Group model (N admin'ов + M Managed + caregivers + roles). Эта спека — фундаментальный refactor, поверх которого строятся все MVP vertical slices.

Архитектурное решение зафиксировано в use-cases/05-pairing-identity-trust.md §Family Group Model (refined B+C: pair-keys + envelope encryption + server arbitration). priv_G НЕ хранится на сервере — E2E preserved.

ЦЕЛЬ:
Реализовать Family Group как primary architectural primitive с envelope encryption для shared content и server arbitration. Pair (из спеки 007) остаётся как 1-to-1 channel. Group — новый primary primitive.

SCOPE ВКЛЮЧАЕТ:
- GroupRepository + MembershipRepository ports в core/domain/.
- Many-to-many user↔group data model.
- Membership entity с role (Admin / CoAdmin / Member / Managed / Caregiver), ttl_expiry, permissions_override.
- Envelope encryption primitive (libsodium crypto_box_seal + crypto_secretbox).
- EnvelopeAdapter interface + real + Fake.
- Server arbitration в Cloudflare Worker (extends push-relay): endpoint для membership operations, signature verification, atomic updates.
- Migration: existing pair data → group с N=2 (admin + Managed).
- Wire format schemaVersion bump для config + crypto envelopes.
- Roundtrip + cross-device tests с первого коммита (per CLAUDE.md rule 5).

SCOPE НЕ ВКЛЮЧАЕТ:
- Real AuthProvider implementation (F-4).
- Wizard integration (F-3).
- UI changes (S-1..S-8).
- Caregiver invite flow (S-7).
- Signal-style group crypto (envelope достаточен).
- Social recovery / orphan admin handling.

EXTENDS / UPDATES:
- Extends спеку 011 (per-pairing crypto).
- Updates спеку 007 (pair остаётся как 1-to-1).
- Updates спеки 008 / 009.

LOCAL TEST PATH (mandatory per D-2):
- FakeGroupRepository + FakeEnvelopeAdapter unit tests.
- Envelope roundtrip: encrypt for N recipients → каждый decrypts.
- Migration test: pair JSON → group JSON valid.
- Server arbitration integration test через Miniflare.
- Membership operations signed verification test.
- Concurrent membership race condition resolution test.

CONSTITUTION GATES:
- Rule 1: domain isolation.
- Rule 2: ACL — все adapters обёрнуты в ports.
- Rule 5: wire-format versioning с schemaVersion.
- Rule 6: mock-first — FakeAdapter обязателен.
- Rule 8: server-side операции отмечены в server-roadmap.

EFFORT: Large (~3-4 weeks).

REFERENCE DOCS:
- use-cases/01-vision-and-positioning.md §Family Group System
- use-cases/05-pairing-identity-trust.md §Family Group Model + §Caregiver Integration
- CLAUDE.md rules 1, 2, 5, 6, 8
- specs/011 — extends
```

### Notes / gotchas

- **Не путать pair и group**. Pair (из 007) — 1-to-1 channel между двумя устройствами. Group — N-membered коллекция. Pair используется для direct admin↔Managed communication; group — для shared content.
- **priv_G не существует**. Это критично. Если в дискуссии кто-то предложит «давайте сделаем shared group key» — refuse и сошлись на use-cases/05 §Family Group Model.
- **Содержимое всегда per-content K** (envelope). Никаких «общих ключей группы».
- **Server — арбитр membership, не keeper of keys**. Сервер видит membership list + публичные ключи. Не видит content.
- **Forward secrecy при removal — inherent limit**. Удалённый member сохраняет доступ к уже скачанным копиям контента. Это записано в Privacy Policy как известный compromise.

---

## F-2: Capability Registry Foundation

### Что строим (mentor explanation)

Сейчас наш product выражает actions через wire format спеки 005 (Action Architecture v2). Это уже частично «intent-based» — действия описываются структурой `providerId + params`, dispatched через `ProviderRegistry`. **Это хорошая основа**, но недостаточная для AI-readiness.

Мы строим **Capability Registry** — единый каталог того, что app умеет делать, с богатой метадатой: human-readable description, voice phrases, parameter schemas, confirmation requirements, authorization scope. Этот каталог — **порт в domain layer**. Domain не знает о существовании AI-агентов.

Снаружи каталога — **Exposure Adapter interface**, через который каталог можно экспонировать конкретному AI-consumer'у: Google Assistant App Actions, MCP server, iOS Shortcuts, etc. **В MVP ни один реальный adapter не реализуется** — только interface + FakeAdapter для тестов.

После F-2 каждая S-спека сверху может **объявлять capability declarations** для своих actions. Через год, когда понадобится первый реальный adapter (например, App Actions), пишется implementation spec без переписывания core.

### Зачем именно сейчас

AI становится default-интерфейсом в 2026. Если архитектура не подготовлена — через 1-2 года придётся переписывать. **AI-ready, not AI-built** — наша архитектурная поза (per D-17 / D-21).

### Источники и резолюции

- [`use-cases/12-ai-integration.md`](use-cases/12-ai-integration.md) — full deep-dive в три слоя AI exposure.
- **Closes D-17** (только AI-ready architecture без provider implementations).
- **Closes D-18, D-19** как DEFERRED (privacy posture / MCP location — решаются при implementation spec).
- **Closes D-20** (создать `checklist-ai-readiness` skill).
- **Closes D-21** (AI affordance как обязательная ось roadmap-обсуждений).

### Scope: что входит

- `core/capability/` модуль.
- `CapabilityRegistry` port (interface).
- `Capability` data class с полями: `intentName`, `humanReadableDescription`, `voicePhrases`, `params`, `idempotent`, `requiresConfirmation`, `auth`.
- `ExposureAdapter` interface (port для будущих adapters).
- `FakeExposureAdapter` для тестов.
- Capability declarations для существующих actions из спек 005 / 006 / 002 / 010:
  - `call_contact` / `message_contact` / `video_call_contact`
  - `open_app` / `navigate_to`
  - `trigger_emergency` (placeholder, full в S-4)
- Wire format с `schemaVersion` для capability declarations.
- Roundtrip tests + cross-device tests.

### Scope: что НЕ входит

- ❌ App Actions adapter implementation (отдельная implementation spec позже).
- ❌ MCP server adapter implementation (отдельная implementation spec позже).
- ❌ Gemini Nano integration (отдельная implementation spec позже).
- ❌ Voice command parsing (это Layer 2 OS-level work).

### Dependencies

Должно быть готово **до** F-2:
- Спеки 005 (Action Architecture v2) — merged.
- Спека 006 (Provider Capabilities) — code-complete.

Может идти **параллельно** с F-1, F-3, F-4.

### Local Test Path (D-2 mandatory)

- **Register fake capabilities** → invoke через FakeExposureAdapter → verify dispatch routing.
- **Capability declarations validation**: каждый existing action имеет capability declaration (fitness function в test).
- **Roundtrip serialization test** wire format с schemaVersion.
- **Cross-device test**: capability serialized на одной версии → deserialized на другой → schema-compatible.

### Effort

**Medium** (~2 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для F-2: Capability Registry Foundation.

КОНТЕКСТ:
Продукт строится по принципу "AI-ready, not AI-built". В MVP не реализуем реальных AI-adapter'ов (App Actions, MCP, Gemini Nano), но строим архитектурный layer, на котором они подключатся как additive add'ы.

Решение зафиксировано в use-cases/12-ai-integration.md (Capability Registry + Exposure Adapters pattern, расширение CLAUDE.md rule 2 ACL).

ЦЕЛЬ:
Создать core/capability/ module с CapabilityRegistry port + ExposureAdapter interface + FakeAdapter + capability declarations для всех existing actions.

SCOPE ВКЛЮЧАЕТ:
- core/capability/ KMP common module.
- CapabilityRegistry port (interface): list(), describe(), invoke().
- Capability data class: intentName, description, voicePhrases, params with types, idempotent flag, requiresConfirmation, auth scope.
- ExposureAdapter interface (port под Layer 2 / Layer 3 / Layer 1 future adapters).
- FakeExposureAdapter для тестов (rule 6 mock-first).
- Capability declarations для existing actions из спек 005, 006, 002, 010 (call_contact, message_contact, open_app, и т.д.).
- Wire format с schemaVersion (per rule 5).

SCOPE НЕ ВКЛЮЧАЕТ:
- App Actions adapter (отдельная implementation spec).
- MCP server adapter (отдельная implementation spec).
- Gemini Nano integration (отдельная implementation spec).
- Voice parsing.

LOCAL TEST PATH (mandatory per D-2):
- Register fake capabilities, invoke через FakeExposureAdapter, verify dispatch.
- Fitness function: каждый existing action имеет capability declaration.
- Roundtrip serialization test wire format.
- Cross-device schema compat test.

ADDITIONAL DELIVERABLES (cross-cutting):
- ADR-008 — AI affordance posture documented ("AI-ready, not AI-built").
- skill `checklist-ai-readiness` создан в .claude/skills/.
- procedure-assess-spec-complexity updated для активации checklist condit.
- spec-template.md получает обязательную секцию "AI Affordance".

CONSTITUTION GATES:
- Rule 1, 2, 5, 6.

EFFORT: Medium (~2 weeks).

REFERENCE DOCS:
- use-cases/12-ai-integration.md (full architecture)
- specs/005 — capability declarations добавляются к existing actions
- CLAUDE.md rules 1, 2, 5, 6
```

### Notes / gotchas

- **Никакой реальной AI-логики**. Этот spec — чистая инфра. Если кто-то хочет «давайте просто добавим Google Assistant сразу» — refuse, направь в отдельную implementation spec позже.
- **Capability declarations — single source of truth**. Любой будущий adapter транслирует из Registry, не из спеки 005 напрямую.
- **Voice phrases — на нескольких языках**. Capability должен поддерживать localized voicePhrases (Map<Locale, List<String>>), потому что App Actions BII matching language-specific.

---

## F-3: Wizard Module + Localization

### Что строим (mentor explanation)

Каждый preset (Simple Launcher, Admin App, в будущем TV, Caregiver) имеет свой **first-run wizard**: серия шагов, которые ведут user'а от установки до полнофункционального приложения. **Wizard — не должен быть зашит в preset**. Это **независимый module**, который любой preset переиспользует.

Мы строим **Wizard Engine** — preset-agnostic state machine. Каждый preset декларирует свой **WizardManifest** (список нужных steps + доступные config templates). Шаги — переиспользуемые между preset'ами (`PermissionStep` одинаковый для Simple Launcher и Admin App).

Внутри wizard'а есть **ConfigTemplate** — nested presets «8 плиток + календарь снизу», «12 плиток без виджетов», и т.д. Templates — **pure data (JSON)**, **shareability-ready** по CLAUDE.md rule 9 (загружаются через `ConfigSource` adapter pattern; `BundledConfigSource` — один из источников, не единственный).

Параллельно — **Localization infrastructure**: string tables на Android resources, system locale detection (без app-level переключателя), и **CI fitness function**, которая failит build если string не переведена на supported languages (RU + 9 distributed).

После F-3 каждый preset может декларировать свой manifest, а wizard «просто работает» поверх него.

### Зачем именно сейчас

Vision требует preset framework (D-22 c). Setup Wizard — то, что **enforce'ит** non-empty top-level screen (D-5). Это **используется в S-1** (Simple Launcher first-run) и **в S-2** (Admin App first-run). Без F-3 эти S-спеки невозможны.

Localization — foundation per ADR-004. Если не сделаем сейчас, потом придётся возвращаться к каждой existing спеке и rebuilding strings.

### Источники и резолюции

- [`use-cases/03-launcher-ui-and-accessibility.md` §Setup Wizard + §Wizard Module](use-cases/03-launcher-ui-and-accessibility.md).
- **Closes D-5** (no top-level empty — wizard enforces config).
- **Closes D-7** (grid presets selected в wizard).
- **Closes D-8** (dwell-to-activate — inline TODO в reusable steps).
- **Closes** localization MVP decision (2026-05-28 — all common languages from day 1).
- **Поглощает FUTURE-SPEC-006** (onboarding-and-tutorials) — TutorialHintStep + hints data становятся частью wizard module.
- **Applies CLAUDE.md rule 9** (shareability-readiness) к ConfigTemplate.

### Scope: что входит

- `core/wizard/` KMP common module.
- `WizardEngine` — state machine, orchestration, persistent checkpoints.
- `WizardStep` interface.
- `WizardManifest` — declarative описание шагов для preset'а.
- Library reusable steps:
  - `PermissionStep` (с deep-links через `Settings.ACTION_*` для конкретного permission).
  - `TextSizeStep` (system fontScale-aware).
  - `ThemeStep` (warm-contrast light / dark).
  - `GridSelectionStep` (2×3, 3×4, 4×5 presets).
  - `PairingStep` (QR scan or invite link).
  - `ConfigTemplatePickerStep` (выбор из bundled templates).
  - `TutorialHintStep` (contextual help).
- `ConfigSource` adapter pattern + `BundledConfigSource` implementation.
- `ConfigTemplate` wire format (JSON с `schemaVersion`).
- `TutorialHintManager` — runtime hints (separate from one-time wizard).
- **Localization infrastructure**:
  - String tables (Android resources, KMP-friendly через moko-resources или аналог).
  - System locale detection (никакого app-level переключателя).
  - **CI fitness function**: failing test если string не переведена на supported languages.
  - Supported languages MVP: RU + EN + ES + ZH + AR + HI + PT + DE + FR + JA.

### Scope: что НЕ входит

- ❌ Конкретные `SimpleLauncherWizardManifest` / `AdminWizardManifest` — это S-1 / S-2.
- ❌ Конкретные ConfigTemplate JSON файлы (это S-1 starter templates).
- ❌ Reverse-engineering existing спеки 010 setup-assistant — F-3 даёт framework, S-1 расширяет 010 в полный wizard.
- ❌ Wizard для caregiver-specific flow (S-7).
- ❌ Translation pipeline (CI / community / professional / AI-assisted) — adapter pattern есть, конкретный provider — позже.

### Dependencies

Может идти **параллельно** с F-1, F-2, F-4. Не зависит ни от чего из Phase 1.

### Local Test Path (D-2 mandatory)

- **WizardEngine state machine test**: simulate manifest, прогнать через все steps, verify state transitions.
- **Reusable step tests**: PermissionStep handles grant/deny, TextSizeStep applies fontScale, и т.д.
- **ConfigTemplate roundtrip test** — JSON serialize → deserialize → assert structure.
- **ConfigSource adapter test**: BundledConfigSource loads from resources, FakeConfigSource for tests.
- **Locale switching test**: system locale → string lookup → fallback to EN if missing.
- **CI fitness function test**: missing translation → build fails.
- **Checkpoint resume test**: interrupt wizard mid-flow → restart → resume from same step.

### Effort

**Large** (~3 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для F-3: Wizard Module + Localization.

КОНТЕКСТ:
Vision требует Universal Preset Architecture (D-22): единое ядро + N preset'ов. Каждый preset должен иметь first-run wizard. Wizard — независимый reusable module, не зашит в preset.

Архитектура подробно в use-cases/03-launcher-ui-and-accessibility.md §Wizard Module.
Дополнительно: CLAUDE.md rule 9 (shareability-readiness) применяется к ConfigTemplate.

ЦЕЛЬ:
Создать core/wizard/ module с WizardEngine + reusable steps + ConfigTemplate format + ConfigSource adapter pattern. Параллельно — Localization infrastructure с CI fitness function.

SCOPE ВКЛЮЧАЕТ:
- core/wizard/ KMP common module.
- WizardEngine: state machine, orchestration, persistent checkpoints, resumable from any step.
- WizardStep interface.
- WizardManifest — preset's declarative description of needed steps + available templates.
- Reusable step library:
  - PermissionStep (с deep-links via Settings.ACTION_*)
  - TextSizeStep
  - ThemeStep (warm-contrast)
  - GridSelectionStep (2×3 / 3×4 / 4×5)
  - PairingStep
  - ConfigTemplatePickerStep
  - TutorialHintStep
- ConfigSource adapter pattern + BundledConfigSource implementation.
- ConfigTemplate wire format (JSON, schemaVersion per rule 5 + 9).
- TutorialHintManager — runtime contextual hints (separate from one-time wizard).
- Localization infrastructure:
  - String tables (KMP-friendly через moko-resources).
  - System locale detection.
  - CI fitness function: failing test if string не переведена на supported languages.
  - Initial supported: RU, EN, ES, ZH, AR, HI, PT, DE, FR, JA.

SCOPE НЕ ВКЛЮЧАЕТ:
- Конкретные preset Manifest'ы (S-1 для Simple Launcher, S-2 для Admin App).
- Конкретные ConfigTemplate JSON (S-1 предоставит starter templates).
- Caregiver wizard flow (S-7).
- Translation pipeline (CI / community / professional — позже).

LOCAL TEST PATH (mandatory per D-2):
- WizardEngine state machine test (fake manifest, traversal).
- Reusable step tests (each step handles success / fail / skip).
- ConfigTemplate roundtrip serialization test.
- ConfigSource adapter test (Bundled + Fake).
- Locale switching test (system → lookup → fallback).
- CI fitness function fails on missing translation.
- Checkpoint resume test (interrupt → restart → continue).

ADDITIONAL DELIVERABLES (cross-cutting):
- skill `checklist-preset-readiness` создан (против unification erosion).
- skill `checklist-shareability` (опционально, rule 9 enforcement).
- FUTURE-SPEC-006 (onboarding-and-tutorials) поглощается — становится частью wizard module.

CONSTITUTION GATES:
- Rule 1, 2, 5, 6, 9.

EFFORT: Large (~3 weeks).

REFERENCE DOCS:
- use-cases/03-launcher-ui-and-accessibility.md §Setup Wizard + §Wizard Module
- ADR-004 (localization global readiness)
- CLAUDE.md rules 1, 2, 5, 6, 9
```

### Notes / gotchas

- **Wizard vs Tutorial — РАЗНЫЕ концепции** в одном module. Wizard = one-time setup, mandatory + skippable steps. Tutorial = ongoing contextual hints, opt-out.
- **ConfigTemplate — pure data, не код**. Если возникнет искушение зашить template в Kotlin code — refuse, направь в JSON + ConfigSource adapter.
- **Templates без identity-bound values**: tile **types**, positions, widget choices. БЕЗ contact identifiers / photo references / UIDs. Это применение rule 9.
- **Локализация — system locale-only**. Никакого app-level переключателя. Если бабушка хочет другой язык — она меняет в Android Settings.
- **CI fitness function — must fail build**. Не warning. Если string не переведена — PR не мерджится. Иначе localization деградирует.

---

## F-4: AuthProvider + Google Sign-In — 🔴 ELEVATED PRIORITY 2026-05-29

> **Priority shift (2026-05-29)**: F-4 поднят как **dependency для F-014.1** (Server backup of named configs). Без stable Google identity невозможно cross-device sync admin'ского self-config. См. specs/014-tile-editing-admin-senior-profiles/spec.md §Phase Dependencies.

### Что строим (mentor explanation)

Сейчас наша система использует **anonymous Firebase Auth** (auto-generated UID без email или identity). Это работает для pair-pairing (007), но имеет фундаментальные проблемы:
- При factory reset бабушки — UID теряется, нет связи с предыдущей identity.
- Невозможен «login с нового устройства same account» — admin теряет всё при смене телефона.
- Anti-abuse слабее (нет stable identity для rate-limit per-uid).
- Subscription billing невозможен (нужен stable identity).

Мы строим **AuthProvider port** + **Google Sign-In adapter для всех устройств**. Каждое устройство (и admin, и Managed) логинится под своим Google-аккаунтом и имеет свой стабильный UID. Это полностью вырезает анонимность из системы и решает проблему Disaster Recovery при потере телефона (бабушка просто логинится на новом устройстве). Связь между ними устанавливается через механизм делегирования (delegations).

Email-bound identity admin'а используется для:
- Subscription billing (D-11 family monthly).
- Account recovery (если admin потерял телефон, новое устройство → Google Sign-In → восстановление через Firebase).
- Multi-device admin (admin на телефоне + планшете — same identity).
- Account deletion email confirmation (S-6).

### Зачем именно сейчас

S-2 (Admin App с first-run + remote pairing) и S-6 (Account deletion) и S-7 (Caregiver invite) — **все зависят** от AuthProvider. F-4 — критический dependency.

### Источники и резолюции

- [`use-cases/05-pairing-identity-trust.md` §Identity](use-cases/05-pairing-identity-trust.md) D-Pair-1.
- **Closes D-Pair-1** (полный отказ от анонимности — все устройства named) — **в MVP** (решение от 2026-05-30).
- Декабрь 2026-05-28 evening: Google OAuth для admin зафиксирован.
- Extends `project-backlog.md` AUTH-001.

### Scope: что входит

- `AuthProvider` port в `core/domain/`.
- `GoogleSignInAuthAdapter`:
  - Firebase OAuth integration (Google Sign-In).
  - Email-bound identity binding.
  - Token refresh / session management.
- **Отказ от AnonymousAuthAdapter**: все устройства используют `GoogleSignInAuthAdapter`.
- `FakeAuthAdapter` для тестов.
- Identity model:
  - `User { id, identity_keys, email?, display_name?, subscription_state }`
  - Email — Required для всех пользователей.
- Session management (token storage, refresh logic, expiry handling).

### Scope: что НЕ входит

- ❌ Phone Auth, Email/Password Auth — только Google Sign-In в MVP (можно добавить позже через adapter pattern).
- ❌ Apple Sign-In — это V-1 iOS территория.
- ❌ Real subscription billing flow (frozen).
- ❌ Account recovery UI flow — частично в S-2 (admin onboarding), полная — S-6 (account deletion с recovery option).

### Dependencies

Должно быть готово **до** F-4:
- F-1 (group model references user identity — user_id из AuthProvider).
- Firebase Auth setup в Firebase Console (admin task, не код).

### Local Test Path (D-2 mandatory)

- **FakeAuthAdapter test**: mock OAuth flow → verify identity binding (email, display name).
- **Delegation binding test**: Managed Google account успешно делегирует права Admin Google account.
- **Token refresh test**: expired token → automatic refresh.
- **Session persistence test**: app restart → session restored (или login required).
- **Named auth integration test**: admin emulator с Google Sign-In + Managed emulator с Google Sign-In → делегирование (pair) работает.

### Effort

**Medium** (~2 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для F-4: AuthProvider + Google Sign-In.

КОНТЕКСТ:
В MVP все пользователи (admin и Managed) — registered Google users (Google Sign-In + email-bound identity). Анонимных устройств нет.

Решение зафиксировано в use-cases/05-pairing-identity-trust.md §Identity (D-Pair-1).

ЦЕЛЬ:
Создать AuthProvider port + Google Sign-In adapter (для всех) + Fake adapter (tests).

SCOPE ВКЛЮЧАЕТ:
- AuthProvider port в core/domain/.
- GoogleSignInAuthAdapter: Firebase OAuth + email-bound + token refresh.
- Отказ от AnonymousAuthAdapter, переход на delegation pattern.
- FakeAuthAdapter (rule 6 mock-first).
- User identity model: { id, identity_keys, email?, display_name?, subscription_state }.
- Session management (storage, refresh, expiry).

SCOPE НЕ ВКЛЮЧАЕТ:
- Phone Auth / Email-Password Auth (можно добавить позже через adapter).
- Apple Sign-In (V-1 iOS).
- Subscription billing (frozen).
- Account recovery UI (S-2 / S-6).

DEPENDENCIES:
- F-1 готов (group model references user identity).
- Firebase Auth project configured (admin task).

LOCAL TEST PATH (mandatory per D-2):
- FakeAuthAdapter mock OAuth flow.
- AnonymousAuthAdapter pair-based identity test.
- Token refresh test.
- Session persistence test (app restart).
- Hybrid model integration test (admin emulator + Managed emulator с разными adapters).

CONSTITUTION GATES:
- Rule 1, 2, 6.

EFFORT: Medium (~2 weeks).

REFERENCE DOCS:
- use-cases/05-pairing-identity-trust.md §Identity (D-Pair-1)
- project-backlog.md AUTH-001
- CLAUDE.md rules 1, 2, 6
```

### Notes / gotchas

- **Отказ от анонимности (решение 2026-05-30)**. И Admin, и Managed используют Google Sign-In. Первичный вход на устройстве Managed выполняет Admin, что решает проблему с забытыми паролями и обеспечивает disaster recovery.
- **Subscription state — пока stub field**. Реальное billing — отдельная спека post-MVP. Но field уже добавляется в User для compatibility.

---

## F-5: ConfigDocument E2E Encryption — 🔴 PRODUCTION BLOCKER

### Что строим (mentor explanation)

Сейчас `ConfigDocument` (то, что admin отправляет бабушке: имена контактов, телефоны, layout'ы плиток, labels) лежит в Firestore **в plaintext**. Firebase / Google / любой с доступом к Firestore project видит **всё**: имена пожилых, телефоны их родственников, какие приложения они открывают, как зовут их соседку. Это **критическая privacy regression**.

F-5 закрывает эту дыру: `ConfigCipher` port в domain (CLAUDE.md rule 1+2), libsodium AEAD adapter — шифрует ConfigDocument **полностью** перед записью в Firestore, расшифровывает при чтении. Остальной код не знает, что шифрование вообще существует — он работает с `ConfigDocument` как раньше.

**Trade-off**: server теряет возможность field-level merge / diff. Все merge-операции переезжают на клиент. Это переписывает часть спеки 008 (optimistic concurrency остаётся, но на уровне document hash, не fields).

### Зачем именно сейчас

Решение 2026-05-28 (vision review): **production blocker**, нельзя выпустить app пока plaintext config летает в Firebase. **Не блокирует development** S-features в dev environment (admin'ская команда работает с plaintext'ом локально). Поэтому в Phase 1 Foundation, после F-4 (AuthProvider) — чтобы у нас был **стабильный** named admin identity, к которому привязываются encryption keys.

### Источники и резолюции

- User raised 2026-05-28 при обсуждении спеки 014 (Contact Sharing UX).
- Backlog entry: [TODO-SEC-CRITICAL-024](../dev/project-backlog.md).
- Closes: privacy gap не покрытый спекой 011 (она закрывает только media blobs, не config).
- Extends: спека 008 (config sync) — переписывает field-level merge на client-side.

### Scope: что входит

- `ConfigCipher` port в `core/domain/`:
  ```kotlin
  interface ConfigCipher {
      suspend fun seal(linkId: String, config: ConfigDocument): SealedConfig
      suspend fun open(linkId: String, sealed: SealedConfig): Outcome<ConfigDocument, CryptoError>
  }
  ```
- libsodium AEAD adapter (`AeadConfigCipherImpl`).
- Pair-derived encryption key (X25519 ECDH между admin pub и Managed pub → symmetric key для config).
- `SealedConfig` wire-format: `{schemaVersion, encrypted_payload, nonce, recipients[]}` — backward-compat с plain ConfigDocument schemaVersion 1 (legacy read path).
- Wire-format schemaVersion bump: ConfigDocument v1 (plain) → SealedConfig v2 (encrypted). Cross-version roundtrip + backward-compat tests.
- Multi-admin case: строго фиксируется **Wrapped Key Envelope** паттерн. Сервер хранит ровно одну зашифрованную копию конфига (зашифрованную CEK). Сам CEK шифруется KEK-ключами каждого админа и Managed устройства.
- Client-side merge/diff/search: переписать `ConfigEditor.pushPending` optimistic concurrency на document-hash level, не field-by-field. `ConfigDiff` (sealed type) переезжает полностью на клиент.
- Migration: existing pair'ов с plain config → re-encrypt. **Одноразовый server-triggered job** (как FR-019 в архивированной спеке 013).

### Scope: что НЕ входит

- ❌ Group-level encryption (priv_G, N>2 recipients) — в launcher не нужно, в мессенджер позже.
- ❌ Personal vault (admin's private storage) — отдельная спека (TODO-FUTURE-SPEC-005).
- ❌ Server-side search / index (исчезает — search на client).
- ❌ Cross-version migration UI — server side migration однократно.

### Dependencies

- F-4 AuthProvider — нужен stable admin identity для key management.
- Спека 011 — reuse `AeadCipher`, `AsymmetricCrypto` ports.
- Спека 008 — переписывается часть `ConfigEditor` + `LocalConfigStore`.

### Local Test Path

- JVM unit tests на `ConfigCipher` roundtrip (encrypt → decrypt → assert equal).
- Cross-version test: read SealedConfig schemaVersion 2 → assert correct ConfigDocument; read plain v1 fixture → assert correct ConfigDocument (legacy path).
- Integration test через Miniflare: admin pushes encrypted config → server stores opaque bytes → Managed pulls → decrypts → renders correctly.
- Cross-device test: D1 (admin) seals → D2 (Managed) opens, и наоборот.

### Effort

**Large** (~2-3 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для F-5: ConfigDocument E2E Encryption.

КОНТЕКСТ:
Сейчас ConfigDocument хранится plaintext в Firestore — privacy regression
(имена, телефоны, layout видны Firebase). F-5 закрывает дыру через
прозрачный port/adapter pattern — ConfigCipher port в domain, libsodium
AEAD adapter, остальной код не знает про шифрование.

ЦЕЛЬ:
ConfigDocument никогда не лежит plaintext на сервере. Server side comparisons
переезжают на клиент.

SCOPE ВКЛЮЧАЕТ:
- ConfigCipher port + AeadConfigCipherImpl adapter.
- SealedConfig wire-format с schemaVersion (v2 vs v1 plain legacy).
- Pair-derived encryption key (X25519 ECDH).
- Multi-admin case: Wrapped Key Envelope (1 копия конфига, N зашифрованных ключей).
- Client-side ConfigDiff / merge / search.
- Server-triggered migration job для existing pair configs.
- Backward-compat read для plain v1 ConfigDocument.

SCOPE НЕ ВКЛЮЧАЕТ:
- Group-level encryption (не в launcher).
- Personal vault.
- Server-side search.

REFERENCE DOCS:
- CLAUDE.md rules 1, 2, 5
- specs/008 — extends
- specs/011 — reuses AeadCipher, AsymmetricCrypto
- TODO-SEC-CRITICAL-024 в backlog
```

### Notes / gotchas

- **Многоадминный кейс** — закрытый design point. Фиксируется паттерн **Wrapped Key Envelope** (один зашифрованный конфиг CEK + N зашифрованных ключей KEK). Никаких N копий конфига, чтобы избежать проблем с синхронизацией.
- **Server arbitration исчезает на field-level**. `ConfigEditor.pushPending` optimistic concurrency остаётся, но на уровне document hash. Это означает, что merge при conflict делается полностью на клиенте — admin'у показывается merge UI с full diff (это уже есть в спеке 008 §MergeScreen).
- **Migration of existing data**: production не запущен → существующие test data можем просто стереть. Migration job не критичен, но желателен для smooth transition.
- **Performance**: AEAD encryption на 10 KB config = микросекунды, не bottleneck.

---

# 🎨 Часть V — Phase 2: MVP Vertical Slices (S-1 .. S-8)

> **Цель Phase 2**: каждая S-спека ships demoable end-to-end feature. После каждой S-спеки можно показать **что-то новое** пользователю.

---

## S-1: Simple Launcher First-Run + Setup Wizard

### Что строим (mentor explanation)

Это **главный visible product** для Managed-устройства — то, что бабушка видит каждый день. Спека реализует **Simple Launcher preset** через wizard module (из F-3) + capability registry (из F-2).

Бабушка устанавливает приложение → запускается **wizard**: язык (system locale) → **Google Sign-In** (обычно выполняет помогающий родственник) → text size → theme (warm-contrast light/dark) → grid preset selection (2×3 / 3×4 / 4×5) → permissions (ROLE_HOME, POST_NOTIFICATIONS) → optional pairing с admin'ом → tutorial hints. После wizard'а — **никогда не пустой экран**, всегда есть config (либо выбранный template, либо paired-from-admin).

Wizard включает **2-3 starter ConfigTemplate** (bundled JSON через `BundledConfigSource`): «6 tiles classic», «9 tiles + calendar», «12 tiles dense». Каждый template — pure data per CLAUDE.md rule 9, обезличенный, shareability-ready.

Из существующих спеек: 001/003/004 уже дают launcher core + UI skeleton + Compose Multiplatform. 010 (setup-assistant) даёт ROLE_HOME / POST_NOTIFICATIONS / call confirmation. **S-1 расширяет 010** в полный preset через wizard module.

### Зачем именно сейчас

Это **первый visible MVP demo**: ставим app на эмулятор → wizard → home screen с тайлами. Без этого нет демонстрируемого продукта.

### Источники и резолюции

- [`use-cases/03-launcher-ui-and-accessibility.md` §Setup Wizard](use-cases/03-launcher-ui-and-accessibility.md).
- **Closes D-5** (no top-level empty — wizard enforces config completion).
- **Closes D-7** (grid presets выбираются в wizard).
- **Closes D-22 partial** (Simple Launcher как первый preset валидирует framework).
- Extends спеку 010 (setup-assistant) → wizard module integration.

### Scope: что входит

**Wizard reduced 9 → 5 steps + autohints** (2026-05-28 evening — pre-S-1 adjustment based on day-1 retention metric research).

- `SimpleLauncherWizardManifest` — declaration шагов для Simple Launcher preset.
- **5 mandatory steps** в wizard (всё остальное — в Settings + autohints):
  1. Welcome + language (system locale auto-detected, кнопка «изменить» доступна).
  1.5 Google Sign-In (mandatory identity binding).
  2. ROLE_HOME permission (deep-link `Settings.ACTION_HOME_SETTINGS`).
  3. POST_NOTIFICATIONS permission (Android 13+).
  4. Theme selection (warm-light / warm-dark, default warm-light).
  5. Optional pairing (skippable — «настрою позже»).
- **Autohints (вместо wizard steps)** через `TutorialHintManager` из F-3:
  - **Text size**: autohint при первом home screen render — «текст мелкий? нажмите тут».
  - **Grid preset**: autohint при первом entry в edit mode — «можно изменить раскладку».
  - **Config template picker**: autohint при пустой раскладке — «попробуйте готовый template?».
  - **Tutorial hints**: contextual, появляются при first-touch каждой feature.
  - **Frequency cap**: каждый hint показывается max 3 раза, dismissal memory persistent.
- Bundled ConfigTemplate files (3 JSON):
  - `6tiles-classic.json` — 2×3 grid, контакты + телефон.
  - `9tiles-with-calendar.json` — 3×3 grid, контакты + calendar widget.
  - `12tiles-dense.json` — 3×4 grid, advanced.
- Home screen renderer (расширяется из 003 UI skeleton, теперь читает из config).
- Skippable step «настрою позже» — banner в Settings reminds (для skip'нутых mandatory items).
- ARCH-016 finally closed (config из `/config/current`, не mock).

### Scope: что НЕ входит

- ❌ Admin App preset / Admin wizard — это S-2.
- ❌ Contact tiles content (это S-3 — S-1 рендерит «placeholder» contact tiles).
- ❌ SOS configuration (это S-4).
- ❌ Photo upload / display (это S-5).
- ❌ Caregiver invite (это S-7).
- ❌ Dwell-to-activate (inline TODO post-MVP per D-8).

### Dependencies

Должно быть готово **до** S-1:
- F-2 (Capability Registry — для capability tiles).
- F-3 (Wizard Module — основа).
- F-1 не строго обязателен **для wizard'а** (config может быть локальным в MVP), но **рекомендуется готов** для config sync.

### Local Test Path (D-2 mandatory)

- **Fresh install на emulator** → wizard appears → пройти все шаги → home screen рендерится с выбранным template.
- **Restart device** → state persistent (wizard не повторяется).
- **Skip optional steps** → home screen с defaults → banner в Settings для skipped.
- **Permission deny** → wizard handles gracefully (re-prompt or deep-link to Settings).
- **Locale switching test**: меняем system locale → строки меняются после restart.
- **Senior-safe walkthrough** на эмуляторе через skill `android-emulator` (deferred for physical users per memory).

### Effort

**Large** (~3 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для S-1: Simple Launcher First-Run + Setup Wizard.

КОНТЕКСТ:
Это первый visible MVP demo — Simple Launcher preset для Managed-устройства. После установки app → wizard → home screen с реальным config'ом (не пустой). Закрывает боль empty-state-at-launch (D-5).

Архитектура: расширение спеки 010 (setup-assistant) через Wizard Module из F-3 + Capability Registry из F-2. Использует ConfigTemplate / ConfigSource из F-3 (CLAUDE.md rule 9 shareability-ready).

ЦЕЛЬ:
Создать SimpleLauncherWizardManifest + 3 starter ConfigTemplate + home screen renderer, который читает из config (не mock).

SCOPE ВКЛЮЧАЕТ:
- SimpleLauncherWizardManifest:
  - Welcome screen
  - Language detection (system locale)
  - Google Sign-In
  - Text size selection
  - Theme selection (warm-light / warm-dark)
  - Grid preset selection (2×3 / 3×4 / 4×5)
  - Permissions: ROLE_HOME + POST_NOTIFICATIONS (с deep-link)
  - Pairing step (skippable)
  - Config template picker
  - Tutorial hints (skippable)
- Bundled ConfigTemplates (3 JSON, pure data, обезличенные):
  - 6tiles-classic
  - 9tiles-with-calendar
  - 12tiles-dense
- Home screen renderer (из спеки 003 UI skeleton) — теперь читает из /config/current (закрывает ARCH-016).
- Skippable step "настрою позже" + Settings banner reminder.
- Setup wizard enforce'ит config completion — никогда нет empty top-level screen.

SCOPE НЕ ВКЛЮЧАЕТ:
- Admin App preset (S-2).
- Contact tiles content (S-3).
- SOS config (S-4).
- Photos (S-5).
- Caregiver (S-7).
- Dwell-to-activate (post-MVP).

DEPENDENCIES:
- F-2 done (Capability Registry).
- F-3 done (Wizard Module + Localization).

LOCAL TEST PATH (mandatory per D-2):
- Fresh install on emulator → wizard appears → complete → home screen rendered.
- Restart device → state persistent.
- Skip optional → home with defaults + Settings banner.
- Permission deny → graceful handling.
- Locale switching test.
- Senior-safe walkthrough (skill android-emulator).

CONSTITUTION GATES:
- Rule 1, 2, 5, 6, 9.

EFFORT: Large (~3 weeks).

REFERENCE DOCS:
- use-cases/03-launcher-ui-and-accessibility.md §Setup Wizard
- specs/010-setup-assistant (расширяется)
- specs/003-ui-skeleton (home screen renderer)
- CLAUDE.md rules 1, 2, 5, 6, 9
```

### Notes / gotchas

- **«Empty top-level screen» — запрещено**. Если wizard прерван и user попадает на home — re-launch wizard с того же шага.
- **Skip-with-banner pattern**: skipped mandatory step → banner «настрой это в Settings», persistent.
- **Senior-safe walkthrough — отложен до physical users** (per memory `testing_environment.md`). В MVP — только emulator smoke check.
- **Localization обязательна** — все строки в wizard через string tables, проверяется CI fitness function (F-3).

---

## S-2: Admin App Preset + Remote Pairing

### Что строим (mentor explanation)

Симметрично S-1, но для **admin-устройства**. Admin (взрослый родственник, внук/сын/дочь) устанавливает app → wizard: Google Sign-In (через F-4 AuthProvider) → создать Family Group → invite Managed via signed link → видит paired устройства в device list.

Это **второй preset** в нашей Universal Preset Architecture (D-22), который валидирует, что framework работает (не зашит под Simple Launcher).

Admin wizard сильно отличается от Simple Launcher wizard'а: больше шагов (Google Sign-In, Family Group creation), меньше UX-adaptations для elderly (admin — tech-savvy adult). Это иллюстрирует, как **разные manifest'ы** дают разные UX в одном wizard engine.

Remote pairing — admin генерирует **signed invite link** в admin app (подпись `priv_admin` + group_id + role + TTL) → шлёт через **Android share intent** (любой канал) → Managed открывает в своей app → видит preview → accept → server проверяет signature → adds Managed в group.

После pairing admin видит **device list** с health snapshot каждого Managed: battery, online status, last activity, app version, permissions check.

### Зачем именно сейчас

Без admin app **никто не может настроить** Managed устройство удалённо (companion-only модель из D-1). S-2 — первая spec, которая делает remote scenario рабочим.

### Источники и резолюции

- [`use-cases/04-remote-management.md`](use-cases/04-remote-management.md).
- [`use-cases/05-pairing-identity-trust.md` §Remote Invite Flow](use-cases/05-pairing-identity-trust.md).
- **Closes D-22** (Admin App как второй preset — валидация framework).
- **Closes D-1 implementation** (companion-only с remote setup).
- Extends спеки 007 (pairing primitive) + 009 (admin flows — was stub).

### Scope: что входит

- `app/presets/admin-app/` папка с Admin App preset.
- `AdminWizardManifest`:
  - Welcome screen для admin (different copy, более tech-friendly).
  - Google Sign-In (через F-4 GoogleSignInAuthAdapter).
  - Create Family Group (название группы, default role «Admin» для creator'а).
  - Invite Managed step (signed link generation + share intent).
  - Tutorial hints (для admin'а).
- Device list UI:
  - Карточки paired Managed устройств.
  - Health snapshot: battery, online/offline, last activity, app version, permissions OK/not.
  - Tap card → device detail view (later expanded в S-8).
- Remote pairing flow с **two-factor accept** (2nd pass 2026-05-28 evening):
  - Signed invite link generation (подпись `priv_admin` + group_id + role + ttl + nonce).
  - Share intent integration (Android `ACTION_SEND` с pre-filled text + link).
  - Managed side: open link → preview → claim → **server отправляет push admin'у** «X принял invite, подтвердить?».
  - **Admin confirmation step**: admin видит claim запрос в-app → confirm/reject → only then server adds Managed to group.
  - Защищает от leaked / forwarded invite links — любой может claim, но не присоединится без admin's explicit confirmation.
- Admin's first Family Group auto-created при first launch.

### Scope: что НЕ входит

- ❌ Multi-admin invite (это S-7 — caregiver / co-admin invite через тот же mechanism).
- ❌ Full layout editor (это S-8).
- ❌ Contact upload (это S-3).
- ❌ Account deletion UI (это S-6 — Settings entry будет в S-6).
- ❌ iOS Admin (это V-1).

### Dependencies

Должно быть готово **до** S-2:
- F-1 (Family Group + envelope encryption — group model).
- F-3 (Wizard Module).
- F-4 (Google Sign-In AuthProvider).

### Local Test Path (D-2 mandatory)

- **Two emulator setup**: admin emulator (с Google Account configured) + Managed emulator (anonymous).
- **Admin completes wizard** → Google Sign-In → creates Family Group → device list пусто.
- **Admin generates invite** → manually copy link → paste в Managed app (или share intent в local mock).
- **Managed accepts** → server adds → admin device list updates.
- **Health snapshot test**: Managed reports state → admin sees correct values.
- **Sign-out / sign-in test**: admin signs out → signs in same Google → state restored.
- **Multi-device same admin test**: admin на второй emulator с same Google Account → same Family Group, same device list.

### Effort

**Large** (~3 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для S-2: Admin App Preset + Remote Pairing.

КОНТЕКСТ:
Симметрично S-1, но для admin-устройства. Admin installs app → wizard с Google Sign-In + create Family Group + invite Managed → видит device list. Это второй preset в Universal Preset Architecture (D-22), валидирует framework.

Архитектура: use-cases/04-remote-management.md + use-cases/05-pairing-identity-trust.md §Remote Invite Flow.

ЦЕЛЬ:
Создать app/presets/admin-app/ с AdminWizardManifest + remote pairing flow + device list UI.

SCOPE ВКЛЮЧАЕТ:
- app/presets/admin-app/ папка.
- AdminWizardManifest:
  - Welcome (tech-friendly copy для adult)
  - Google Sign-In (через F-4)
  - Create Family Group (название, default admin role)
  - Invite Managed (signed link)
  - Tutorial hints
- Device list UI с карточками paired Managed устройств:
  - Battery, online/offline, last activity, app version, permissions OK
  - Tap → device detail view (placeholder, expanded в S-8)
- Remote pairing flow:
  - Signed invite link generation (priv_admin signature + group_id + role + TTL + nonce)
  - Share intent (ACTION_SEND с pre-filled link)
  - Server endpoint в Cloudflare Worker (extends F-1 arbitration)
  - Managed side: open link → preview → accept → server confirms

SCOPE НЕ ВКЛЮЧАЕТ:
- Multi-admin / caregiver invite (S-7).
- Full layout editor (S-8).
- Contact upload (S-3).
- Account deletion UI (S-6).
- iOS (V-1).

DEPENDENCIES:
- F-1 done (Family Group).
- F-3 done (Wizard Module).
- F-4 done (Google Sign-In).

LOCAL TEST PATH (mandatory per D-2):
- Two emulator: admin (Google Account) + Managed (anonymous).
- Admin wizard → Sign-In → create group → invite link.
- Manually копи link → Managed accepts → pair established.
- Health snapshot reporting test.
- Sign-out / sign-in restoration test.
- Multi-device same admin test.

CONSTITUTION GATES:
- Rule 1, 2, 5, 6.

EFFORT: Large (~3 weeks).

REFERENCE DOCS:
- use-cases/04-remote-management.md
- use-cases/05-pairing-identity-trust.md §Remote Invite Flow
- specs/007 (pairing primitive)
- specs/009 (admin flows — was stub, S-2 + S-8 replace)
```

### Notes / gotchas

- **Signed invite link — нескольких параметров**: group_id, role (Member / Caregiver), TTL, nonce (против replay). Подпись `priv_admin`. Server verifies.
- **Email-bound identity admin'а используется для billing / recovery / deletion**. Подставляется в Family Group `primary_admin_id`.
- **Health snapshot — periodic** (per 007 + 009). Managed posts every 1h or on event. Admin's UI receives via 007 push.

---

## S-3: Contact Tiles + Handoff Calling

### Что строим (mentor explanation)

Admin добавляет контакт (имя, телефон, опционально фото) в admin app → контакт **синхронизируется** через Family Group (envelope encrypted) → Managed получает → contact tile появляется на home screen → tap → confirmation screen → handoff в установленный мессенджер (WhatsApp / Telegram / Viber) или системный звонок.

Это **первая spec'а, реализующая core value proposition**: бабушка позвонила внуку одним нажатием. Все предыдущие F-spec'и и S-1/S-2 — подготовка к этому моменту.

Архитектурно используем **action arch** из спеки 005 (intent-based actions с providers). Расширяем чтобы поддерживать Telegram + Viber **через capability declarations** из F-2. **Custom call confirmation** из спеки 010 уже работает для звонков (anti-mistap).

Photo на тайле — **placeholder** в S-3 (например, initials в круге). **Real photo upload** — S-5 (envelope encryption demo).

### Зачем именно сейчас

Это **главное MVP-демо**: «бабушка нажимает на тайл внука Артёма, дозванивается через WhatsApp, возвращается обратно». Без S-3 продукт не имеет visible value.

### Источники и резолюции

- [`use-cases/06-communications.md`](use-cases/06-communications.md).
- **Closes D-Comm-1** (WhatsApp + Telegram + Viber через adapter framework).
- Extends спеку 002 (WhatsApp tile — теперь contact-based, не hardcoded).
- Extends спеку 005 (action architecture — provider adapters для Telegram / Viber).
- Re-validates спеку 002 поведение в Family Group context.

### Scope: что входит

- `Contact` entity в Family Group model.
- Admin UI: add contact (name + phone + optional photo placeholder).
- Family Group sync: contact propagates через envelope encryption (FamilyContent category).
- Managed UI: contact tile rendering (placeholder photo + name + action icon).
- Multi-messenger support:
  - WhatsApp adapter (existing из 002).
  - Telegram adapter (deep links: `tg://msg?text=...&to=phone`).
  - Viber adapter (deep links: `viber://chat?number=phone`).
  - System phone fallback (если ни один messenger не installed).
- Capability declarations в Registry:
  - `call_contact(contact_id)` — handoff в default messenger.
  - `message_contact(contact_id, text?)` — handoff в messaging messenger.
  - `video_call_contact(contact_id)` — handoff в video-capable messenger.
- Custom call confirmation (existing 010) — pre-handoff screen с timeout.
- Return continuity (existing 002) — back to launcher после messenger.
- Provider selection logic: tap → если несколько installed → admin pre-configured preference; иначе — pick available.

### Scope: что НЕ входит

- ❌ Real photo upload (это S-5, S-3 рендерит placeholder).
- ❌ Video calls fully (initial scope — audio + message; video — S-5 photos enable visual UX further).
- ❌ Voice messages.
- ❌ Group calls.
- ❌ SMS fallback (это S-4 SOS поверх + общая stuff).

### Dependencies

Должно быть готово **до** S-3:
- F-1 (Family Group + envelope).
- F-2 (Capability Registry — capability declarations).
- S-1 (Managed home screen renderer).
- S-2 (Admin UI for adding contacts).

### Local Test Path (D-2 mandatory)

- **Admin emulator**: add contact (name, phone) → contact synced (Firestore verified).
- **Managed emulator**: contact tile appears на home screen.
- **Tap tile** → confirmation screen → tap «позвонить» → intent dispatched.
- **Mock messenger receives intent** (verify via test app).
- **Multi-messenger test**: install mock WhatsApp + Telegram → tap → admin's preference applied OR picker shown.
- **Return continuity test**: from mock messenger back → launcher home (existing 002).
- **No messenger installed**: tap → fallback to system phone intent.

### Effort

**Medium** (~2 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для S-3: Contact Tiles + Handoff Calling.

КОНТЕКСТ:
Главное MVP-демо: admin добавляет контакт → Managed видит тайл → tap → handoff в мессенджер. Closes core value proposition продукта.

Архитектура: use-cases/06-communications.md. Используем action arch (спека 005) + capability registry (F-2) + Family Group sync (F-1).

ЦЕЛЬ:
Реализовать contact entity, admin upload UI, Managed tile rendering, multi-messenger handoff.

SCOPE ВКЛЮЧАЕТ:
- Contact entity в Family Group model.
- Admin UI: add contact (name + phone + photo placeholder).
- Sync через envelope encryption (FamilyContent category).
- Managed UI: contact tile rendering (placeholder photo + name + action icon).
- Multi-messenger adapters:
  - WhatsApp (existing 002)
  - Telegram (tg://msg?text=...&to=phone)
  - Viber (viber://chat?number=phone)
  - System phone fallback
- Capability declarations: call_contact, message_contact, video_call_contact.
- Custom call confirmation (existing 010) — anti-mistap.
- Return continuity (existing 002).
- Provider selection logic.

SCOPE НЕ ВКЛЮЧАЕТ:
- Real photo upload (S-5).
- Voice messages.
- Group calls.
- SMS fallback (S-4).

DEPENDENCIES:
- F-1, F-2 done.
- S-1, S-2 done.

LOCAL TEST PATH (mandatory per D-2):
- Admin adds contact → sync → Managed tile appears.
- Tap → confirmation → intent dispatched.
- Mock messenger receives intent (test app).
- Multi-messenger preference test.
- Return continuity test.
- No messenger installed → system phone fallback.

CONSTITUTION GATES:
- Rule 1, 2, 5, 6.

EFFORT: Medium (~2 weeks).

REFERENCE DOCS:
- use-cases/06-communications.md
- specs/002 (WhatsApp tile — extends)
- specs/005 (action arch — extends с Telegram + Viber)
- specs/010 (custom call confirmation)
```

### Notes / gotchas

- **Contact category — FamilyContent**. Envelope wrappers только для family roles. Caregiver НЕ видит family contacts (S-7).
- **Photo placeholder в S-3**: initials в круге + colored background (consistent per contact_id hash). Real photo — S-5.
- **Provider preference**: admin configures default messenger при contact add (или global setting). UI должен это поддерживать.

---

## S-4: SOS Capability + Wizard Step

### Что строим (mentor explanation)

SOS — **критическая safety net**. Бабушка нажимает большую красную кнопку → confirmation timer (default 5 sec, можно отменить) → параллельно: sequential calls по списку emergency contacts + SMS с GPS координатами.

Архитектурно SOS — **configurable capability** в Capability Registry (из F-2). НЕ hardcoded function. Wizard step «Set up emergency button» настраивает: recipients (subset of Family Group) + actions (call sequential + SMS+GPS) + confirmation delay + surfaces (tile / voice / hardware-button post-MVP).

После configuration SOS становится capability `trigger_emergency`, доступная через:
- **UI tile** — большой красный, по центру нижней половины (one-handed reach, см. UI-008 в use-cases/03).
- **Voice** (через App Actions BII когда implementation spec позже) — «помогите!» или «SOS».
- **MCP** (через MCP adapter когда implementation spec позже) — любой AI agent.

### Зачем именно сейчас

Безопасность — главная ценность для семьи бабушки. SOS — отличительная фича (BIG Launcher / Wiser / Necta все его имеют). Vision filter test passes (усиливает «заботу о пожилом»).

### Источники и резолюции

- [`use-cases/06-communications.md` §SOS](use-cases/06-communications.md).
- **Closes D-4** (SOS in, medical out — как configurable capability).
- **Closes D-9** (hardware power-button → inline TODO post-MVP).
- Uses F-2 (Capability Registry).

### Scope: что входит

- Capability declaration `trigger_emergency`:
  - voicePhrases: ["помогите", "SOS", "помощь"]
  - params: recipients, actions, confirmation_delay_sec
  - requiresConfirmation: true
- WizardStep `SOSConfigStep` (поверх F-3 wizard module):
  - Default recommendation: highlighted "включить".
  - Recipients selection (subset of Family Group).
  - Actions checkboxes: call sequential / SMS with GPS.
  - Confirmation delay slider (0 / 3 / 5 / 10 sec).
  - Surfaces selection (tile / voice).
- SOS tile rendering:
  - Большой красный, prominent placement (нижняя половина center).
  - Editable position в edit mode.
- Activation flow:
  - Tap tile → full-screen confirmation с countdown timer.
  - Cancel button visible до timer expiry.
  - На expiry → parallel SMS + sequential calls.
- Default actions implementation:
  - **PRIMARY mechanism (2nd pass 2026-05-28 evening, user clarification)**: **System emergency call to 112 / 911 + SMS с GPS coordinates** (если permission granted). Это **главный** safety net — работает даже когда: admin device offline, push не доехал, notification permission denied. Используем system phone API напрямую, не через любые мессенджеры.
  - **Secondary mechanism**: parallel **push admin'ам** через FCM (для awareness, не safety net). Если admin не получил — primary still works.
  - **Tertiary fallback**: sequential calls по списку Family Group recipients (через system phone) до первого ответа.
- Settings reminder banner если SOS not configured.
- **App update SOS-deferral** (2026-05-28 evening — pre-S-4 adjustment):
  - WorkManager-based deferral для Play Store auto-update в течение **30 минут после SOS triggered**.
  - Critical safety claim: обновление приложения во время активного emergency может перебить flow.
  - Investigate **Play Store In-App Updates API** для programmatic deferral.
  - Fallback если API не позволяет: warning banner в-app + skip auto-update notification.
  - **Exception**: critical security CVE patches override deferral (priority flag).

### Scope: что НЕ входит

- ❌ Hardware SOS power-button (inline TODO per D-9, post-MVP).
- ❌ Real App Actions / MCP voice integration (Capability Registry готов, реальный adapter — отдельные spec'и).
- ❌ Custom messaging вне SMS (e.g., в WhatsApp специфический message text).
- ❌ Medical info card / медкарта (out per D-4).

### Dependencies

Должно быть готово **до** S-4:
- F-2 (Capability Registry — capability declaration).
- S-1 (Wizard manifest — SOSConfigStep добавляется).
- S-2 (recipients из Family Group).
- Permissions: SEND_SMS, CALL_PHONE (request в S-1 wizard или specific permission step).

### Local Test Path (D-2 mandatory)

- **Configure SOS в wizard step** → assert capability registered с params.
- **Tap SOS tile** → confirmation screen с countdown → cancel works.
- **Tap SOS, skip cancel** → verify call intent dispatched + SMS intent dispatched.
- **GPS permission denied** → SMS без coordinates, no crash.
- **No recipients answered** (mocked) → fallback to 112 / system emergency.
- **Edit mode test**: SOS tile editable position, deletion blocked (mandatory).

### Effort

**Medium** (~2 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для S-4: SOS Capability + Wizard Step.

КОНТЕКСТ:
SOS — critical safety net. Configurable capability в Capability Registry, не hardcoded. Wizard step настраивает recipients / actions / delay / surfaces.

Архитектура: use-cases/06-communications.md §SOS.

ЦЕЛЬ:
Реализовать trigger_emergency capability + SOSConfigStep wizard step + tile rendering + activation flow.

SCOPE ВКЛЮЧАЕТ:
- Capability declaration trigger_emergency (с voicePhrases, params, confirmation).
- WizardStep SOSConfigStep (default recommend "включить"):
  - Recipients subset (Family Group)
  - Actions: call sequential / SMS with GPS
  - Confirmation delay slider (0/3/5/10 sec)
  - Surfaces selection (tile / voice)
- SOS tile rendering: red, prominent, configurable position.
- Activation: tap → confirmation countdown → cancel works → SMS + sequential calls.
- GPS coordinates в SMS если permission granted, fallback без.
- System emergency (112/911) fallback если none answered.
- Settings banner if not configured.

SCOPE НЕ ВКЛЮЧАЕТ:
- Hardware power-button (post-MVP per D-9).
- Real App Actions / MCP voice (Registry ready, adapter — отдельные spec'и).
- Custom messaging вне SMS.
- Medical info (out per D-4).

DEPENDENCIES:
- F-2 done (Capability Registry).
- S-1 done (Wizard manifest для добавления SOSConfigStep).
- S-2 done (recipients из Family Group).

LOCAL TEST PATH (mandatory per D-2):
- Configure SOS in wizard, assert capability registered.
- Tap SOS tile → confirmation countdown → cancel works.
- Skip cancel → call intent + SMS intent dispatched.
- GPS denied → SMS без coordinates, no crash.
- No recipients answered → 112 fallback.
- Edit mode: position editable, deletion blocked.

CONSTITUTION GATES:
- Rule 1, 2, 5.

EFFORT: Medium (~2 weeks).

REFERENCE DOCS:
- use-cases/06-communications.md §SOS
- specs/010 (call confirmation pattern)
- CLAUDE.md rule 10 (notification minimization — SOS push к admin'у тоже подпадает)
```

### Notes / gotchas

- **SOS push к admin'у — допустим по rule 10** (actionable + time-sensitive + user-relevant). Это **исключение**, которое явно проходит фильтр.
- **GPS permission — separate** от других permissions, обоснован «для SOS». Не запрашивать "preemptively".
- **Cancel timer — UX critical**. Если бабушка случайно нажала — должна быть возможность отменить.

---

## S-5: Contact Photos (Family Album Foundation)

### Что строим (mentor explanation)

Admin загружает фото контакта (внука Артёма) → фото шифруется envelope encryption (из F-1) → blob уплывает в Firestore / Cloud Storage в зашифрованном виде → Managed скачивает → расшифровывает своим `priv_managed` → тайл контакта показывает реальное фото внука.

Это **первая spec'а с реальным content sync через envelope encryption** — демо что F-1 работает. Также это foundation для full Family Album (post-MVP v3).

Архитектурно — `PhotoStorage` port + Firestore + Cloud Storage adapters. Reference counting для cleanup (когда контакт удалён → blob удалён через 7 дней grace).

Replaces stub спеки 012.

### Зачем именно сейчас

Photo на тайле — **emotional infrastructure** (vision §Real-Time Care Communication). Бабушка узнаёт внука по фото. Это часть «эмоциональной поддержки», не только функциональности.

### Источники и резолюции

- [`use-cases/07-data-and-privacy.md` §Shared Family Album decision (D-26)](use-cases/07-data-and-privacy.md).
- **Closes D-26** (MVP photos only, full album post-MVP v2).
- Replaces stub спеки 012 fully.
- Uses F-1 (envelope encryption).
- Uses S-3 (contact tile rendering).

### Scope: что входит

- `PhotoStorage` port в `core/domain/`.
- Firestore adapter implementations:
  - Upload encrypted blob (Cloud Storage).
  - Store metadata + envelope wrappers (Firestore).
- Admin UI: pick photo (system photo picker), attach to contact.
- Encryption flow:
  - Generate random K (per-photo symmetric key).
  - Encrypt photo content with K → encrypted blob.
  - Wrap K с pub each recipient (Family Group members).
  - Upload encrypted blob + wrappers + metadata.
- Managed download flow:
  - Detect new photo for contact (push from server + sync poll).
  - Download encrypted blob + own wrapper.
  - Decrypt K with `priv_managed`.
  - Decrypt photo with K.
  - Render on contact tile.
- Reference counting:
  - Contact deleted → blob orphaned.
  - 7-day grace period → batch cleanup.
- Settings: clear cached photos option (privacy).
- **Content recall before consumption** (2nd pass 2026-05-28 evening):
  - Producer (admin) может **отозвать** uploaded blob, пока recipients не скачали.
  - UI: long-press on uploaded content → «отозвать / удалить / re-categorize».
  - Server side: если recipient ещё не download'нул envelope wrapper → удаляем wrapper → recipient никогда не decrypt.
  - Если recipient уже download'нул → too late, content на их device.
  - **Mitigation для producer mistake** (например, family photo accidentally categorized как care content → caregiver получит wrapper, но если admin recall'ит до открытия — caregiver не увидит).
- **Storage health monitoring for Managed device** (2nd pass 2026-05-28 evening):
  - Managed reports current cache size + free storage в health snapshot (см. S-8).
  - Admin видит в Managed health: «Cache: 1.2 GB / Free: 800 MB low».
  - Admin может **clear cache** via remote action:
    - «Clear all photos» (nuclear option).
    - «Clear photos older than 1 month» (default suggestion).
    - «Clear least-recently-viewed 50%» (smart cleanup).
  - **Auto-cleanup option** в Settings: «automatically clean cache when free storage < 1 GB».
  - Cache survives basic «privacy» clear (user-triggered) и admin-triggered cleanup (settings-driven).

### Scope: что НЕ входит

- ❌ Videos / audio (это V-3 Full Family Album).
- ❌ Album UI (timeline / grid / search) — это V-3.
- ❌ Photo editing / cropping (system picker возвращает as-is).
- ❌ Captions / annotations.
- ❌ Share-to-non-family (only Family Group members получают wrappers).

### Dependencies

Должно быть готово **до** S-5:
- F-1 (envelope encryption).
- S-3 (contact tile rendering).
- Permissions: READ_MEDIA_IMAGES Android 13+ (для admin).

### Local Test Path (D-2 mandatory)

- **Admin uploads photo for contact** → blob created в Firestore с envelope.
- **Server confirms upload** (no decrypt access verified — fakeServer doesn't have keys).
- **Managed downloads blob** → расшифровывает своим priv → photo appears on tile.
- **Multi-recipient test**: 3 members в group → 3 wrappers → каждый расшифровывает свой.
- **Caregiver excluded test**: caregiver НЕ в FamilyContent category → no wrapper → cannot decrypt.
- **Delete contact** → blob reference count → cleanup after 7 days.
- **Network failure during upload** → graceful retry.

### Effort

**Medium-Large** (~2-3 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для S-5: Contact Photos (Family Album Foundation).

КОНТЕКСТ:
Admin uploads photo for contact → encrypted via envelope encryption (F-1) → Managed downloads → decrypts → tile shows real photo. Replaces stub спеки 012. Foundation для full Family Album (V-3).

Архитектура: use-cases/07-data-and-privacy.md §Shared Family Album.

ЦЕЛЬ:
Реализовать PhotoStorage port + Firestore + Cloud Storage adapters + admin upload UI + Managed download/decrypt/render flow + reference counting cleanup.

SCOPE ВКЛЮЧАЕТ:
- PhotoStorage port в core/domain/.
- Firestore + Cloud Storage adapters.
- Admin UI: system photo picker, attach to contact.
- Encryption flow:
  - Random K per photo
  - Photo encrypted with K → encrypted blob
  - K wrapped с pub each Family Group member (FamilyContent category)
  - Upload encrypted blob + wrappers + metadata
- Managed download:
  - Push notification trigger (per rule 10 — это может быть in-app notification)
  - Download blob + own wrapper
  - Decrypt K with priv_managed
  - Decrypt photo
  - Render on tile
- Reference counting:
  - Contact deleted → blob orphaned
  - 7-day grace → batch cleanup
- Settings: clear cached photos.

SCOPE НЕ ВКЛЮЧАЕТ:
- Videos / audio (V-3).
- Album UI (V-3).
- Photo editing.
- Captions.
- Share-to-non-family.

DEPENDENCIES:
- F-1 done (envelope encryption).
- S-3 done (contact tiles).

LOCAL TEST PATH (mandatory per D-2):
- Admin uploads → blob с envelope.
- Server confirms no decrypt access (fakeServer no keys).
- Managed downloads → decrypts → photo appears on tile.
- Multi-recipient test (3 members, 3 wrappers).
- Caregiver excluded test (no wrapper).
- Delete contact → cleanup after 7 days.
- Network failure retry.

CONSTITUTION GATES:
- Rule 1, 2, 5, 6, 8.

EFFORT: Medium-Large (~2-3 weeks).

REFERENCE DOCS:
- use-cases/07-data-and-privacy.md §Shared Family Album
- specs/011 (envelope encryption — uses F-1 result)
- specs/012 (stub — fully replaced by S-5)
- CLAUDE.md rule 8 (server-roadmap — blob storage on Spark vs Blaze)
```

### Notes / gotchas

- **Photo upload — может быть большим** (бабушка получает 5MB+ JPEG от внука). Chunked upload, прогресс UI.
- **Cloud Storage cost — Spark лимит 5GB**. Reference counting обязателен. Когда blob orphan'ит — cleanup.
- **Server-roadmap entry**: Cloud Storage at scale → Blaze upgrade trigger.
- **No EXIF stripping**: но если фото содержит location metadata — это уже передано как часть зашифрованного blob, recipients имеют доступ. Acceptable (фото внутри Family Group).

---

## S-6: Account Deletion Flow

### Что строим (mentor explanation)

User в Settings → Account → «Удалить мой аккаунт» → explicit consequences list + re-auth → confirmation. **30-day grace period** soft delete (login ещё работает, можно cancel). После grace — **hard delete batch job**: user identity record, membership records, public keys, audit log с deletion hash для compliance proof.

Это **mandatory по Google Play Policy + GDPR Art. 17 + 152-ФЗ**. Без этого app не пройдёт Play Store review.

Особенный случай — **singleton admin**: если удаляется единственный admin Family Group, deletion blocked до designation of successor co-admin OR explicit group dissolution.

### Зачем именно сейчас

Pre-release blocker. Должно быть готово до публикации в Play Store.

### Источники и резолюции

- [`use-cases/07-data-and-privacy.md` §Account Deletion Flow](use-cases/07-data-and-privacy.md).
- Account deletion MVP decision (2026-05-28).

### Scope: что входит

- `AccountDeletion` port в `core/domain/`.
- Firestore + Cloudflare Worker adapter implementation.
- Settings UI «Account» section:
  - «Delete my account» button.
  - Consequences screen: «вы потеряете доступ к Family Group X, Y. Family photos станут недоступны вам».
  - Optional «Export my data» (simplified JSON dump).
  - Re-auth (Google Sign-In re-prompt).
- 30-day grace period state machine:
  - `deletion_pending` state, login works, can cancel.
  - Family Group members see «X собирается уйти, осталось N дней».
- Email confirmation (initiation + final):
  - Send via adapter (FakeMailAdapter для tests, real provider in post-MVP).
- Singleton admin handover requirement:
  - Block deletion если admin is sole admin of group.
  - UI prompts «передайте admin role» или «распустите группу».
- Hard delete (batch, manual cron в MVP, automated post-Blaze):
  - User identity record delete.
  - Membership records delete.
  - Public keys delete.
  - Envelope wrapper cleanup (наш wrappers removed).
- Audit log entry с deletion hash (compliance proof).
- Subscription cancellation cascade (если admin платит — billing provider notified).

### Scope: что НЕ входит

- ❌ Sophisticated GDPR data export (simplified JSON dump в MVP, polished post-MVP).
- ❌ Automated batch job (manual cron в MVP, automated post-Blaze).
- ❌ Per-region grace periods (единый 30d в MVP).
- ❌ Account suspension / reactivation flow (deleted = permanently deleted).
- ❌ Real billing provider integration (frozen — subscription billing вне MVP).

### Dependencies

Должно быть готово **до** S-6:
- F-1 (group model — для membership cleanup).
- F-4 (AuthProvider — для re-auth и email-bound identity).

### Local Test Path (D-2 mandatory)

- **Initiate deletion in fake env** → state «deletion pending», email mock sent.
- **Mock 30 days elapse** → batch job → state «hard deleted», final email sent.
- **Cancel during grace** → state restored, all data accessible.
- **Singleton admin** → deletion blocked, prompt for successor.
- **Co-admin available** → admin role auto-suggested for handover.
- **Group dissolution flow**: choose to dissolve → all members notified, group deleted.
- **Email mocking**: verify sent on initiation + final, content includes consequences.
- **Envelope wrapper cleanup**: verify orphaned wrappers removed.

### Effort

**Medium** (~2 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для S-6: Account Deletion Flow.

КОНТЕКСТ:
Mandatory для Google Play Policy + GDPR Art. 17 + 152-ФЗ. Pre-release blocker. 30-day grace period soft delete, затем hard delete batch job.

Архитектура: use-cases/07-data-and-privacy.md §Account Deletion Flow.

ЦЕЛЬ:
Реализовать полный account deletion flow с UI, state machine, email confirmation, singleton admin handover requirement, envelope cleanup, audit log.

SCOPE ВКЛЮЧАЕТ:
- AccountDeletion port в core/domain/.
- Firestore + Cloudflare Worker adapter implementation.
- Settings UI:
  - "Account" section
  - "Delete my account" button
  - Consequences screen
  - Optional data export (simplified JSON)
  - Re-auth (Google Sign-In)
- 30-day grace state machine:
  - deletion_pending — login works, cancellable
  - Family members see "leaving in N days"
- Email confirmation (initiation + final, via FakeMailAdapter в MVP).
- Singleton admin handover requirement (block deletion until successor designated).
- Hard delete batch (manual cron в MVP):
  - User identity, memberships, public keys delete
  - Envelope wrapper cleanup
- Audit log с deletion hash (compliance proof).
- Subscription cancellation cascade (если admin платит).

SCOPE НЕ ВКЛЮЧАЕТ:
- Sophisticated GDPR export (post-MVP).
- Automated batch (post-Blaze).
- Per-region grace periods.
- Account reactivation.
- Real billing provider integration.

DEPENDENCIES:
- F-1 done (group model).
- F-4 done (AuthProvider).

LOCAL TEST PATH (mandatory per D-2):
- Initiate deletion → state pending + email mock.
- Mock 30 days → batch → hard deleted + final email.
- Cancel during grace → restored.
- Singleton admin → blocked until successor.
- Group dissolution flow.
- Envelope wrapper cleanup verified.

CONSTITUTION GATES:
- Rule 1, 2, 6, 8.

EFFORT: Medium (~2 weeks).

PRE-RELEASE CHECKLIST (separate, not spec scope):
- Privacy Policy text update (Termly/iubenda template).
- Account deletion disclosure section.
- Envelope limits disclosure ("downloaded copies stay").

REFERENCE DOCS:
- use-cases/07-data-and-privacy.md §Account Deletion Flow
- Google Play Data Safety policy
- GDPR Art. 17 (right to erasure)
- 152-ФЗ
```

### Notes / gotchas

- **Email confirmation — adapter pattern**. В MVP FakeMailAdapter (logs to console). Real provider (SendGrid / Mailgun free tier) — post-MVP.
- **Privacy Policy text update — отдельная pre-release задача**. Не часть спеки, но dependency.
- **«Downloaded copies stay» — записать в Privacy Policy явно**. Envelope encryption не позволяет dereach данные на устройствах других members.
- **Audit log — критично для compliance**. Hash подтверждает что мы реально удалили.

---

## S-7: Caregiver Remote Invite + Role Presets

### Что строим (mentor explanation)

Admin invites caregiver (профессиональную сиделку / медсестру / врача) **удалённо через signed link**: выбирает role preset (Medical Worker / Hired Caregiver / Volunteer / Clinic Stay) → app генерирует signed invite link → admin шлёт через share intent (любой channel) → caregiver открывает в нашей app → видит preview → accept → server adds caregiver в group с preset permissions + TTL.

Critical mechanism: **role-based envelope filtering**. Каждый content имеет category (`FamilyContent` / `CareContent`). При upload content producer выбирает category → server / app формирует list recipients по правилам:
- FamilyContent → wrappers только для {Admin, CoAdmin, Member, Managed}.
- CareContent → wrappers для всех включая Caregiver.

Это **enforce'ит privacy boundary crypto-уровнем**: caregiver физически не получает envelope wrapper для family album, даже если scrape'нет encrypted_content.

### Зачем именно сейчас

Caregiver integration — strong differentiator (per vision §Integration With Caregivers & Clinics). Расширяет product из «launcher для пожилых» в «space координации ухода».

### Источники и резолюции

- [`use-cases/05-pairing-identity-trust.md` §Caregiver Integration](use-cases/05-pairing-identity-trust.md).
- **Closes D-27** (Caregiver integration depth — tier-based + remote invite + TTL).
- **Implements D-15 architectural commitment** (vendor / clinic integration).
- Uses F-1 (group + envelope).
- Uses F-4 (signed operations).

### Scope: что входит

- New role `Caregiver` в Family Group (наряду с admin / co-admin / member / managed).
- Role presets bundle permissions + default TTL:
  - `MedicalWorker` — SOS, read health, write medical note. TTL 24h.
  - `HiredCaregiver` — SOS, health, contact tile, visit log. TTL indefinite.
  - `FamilyCaregiver` — as Member + write care notes. TTL indefinite.
  - `Volunteer` — SOS only. TTL 30d renewable.
  - `ClinicStay` — full care access, audit-logged. TTL = stay duration.
- Admin UI «Add caregiver»:
  - Select preset.
  - Optional customize permissions.
  - Set TTL.
  - Generate signed link.
  - Share intent.
- Caregiver-side flow:
  - Open link in our app (или install first).
  - Preview screen с invite details.
  - Accept → server verifies → membership created.
- Content category enum: `FamilyContent` / `CareContent` / `Public`.
- Role-based envelope filtering (producer picks category → recipients calculated from roles).
- TTL on Membership (server checks per-request; expired → 403, treat as kicked).
- Audit log mandatory для caregiver actions (per backlog SEC-003):
  - Logs: view sensitive data, write care note, trigger emergency.
  - Notify admin на view of sensitive data.

### Scope: что НЕ входит

- ❌ Auto-create separate Care Group (per discussion 2026-05-27 evening — MVP one Family Group + role-based filtering).
- ❌ Caregiver-specific UI preset (e.g., simplified caregiver app) — это V-x post-MVP.
- ❌ B2B clinic onboarding (бизнес-flow, post-MVP).
- ❌ Multi-tier sub-presets для одного role (e.g., MedicalWorker.Visit vs MedicalWorker.OnCall) — post-MVP.
- ❌ Caregiver-discoverable marketplace.

### Dependencies

Должно быть готово **до** S-7:
- F-1 (group + envelope + many-to-many membership).
- F-4 (signed operations, AuthProvider для caregiver identity).

### Local Test Path (D-2 mandatory)

- **Admin generates invite** с MedicalWorker preset + 24h TTL → signed link.
- **Caregiver emulator receives link** → preview → accept → membership created с preset permissions + TTL.
- **Family content upload** → wrappers don't include caregiver → verify caregiver cannot decrypt.
- **Care content upload** → wrappers include caregiver → verify caregiver decrypts.
- **Mock 24h elapse** → TTL expired → server 403 для caregiver requests.
- **Permission escalation block**: caregiver tries to invite другого caregiver → 403.
- **Audit log test**: caregiver views health → admin gets notification, log entry created.

### Effort

**Medium-Large** (~3 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для S-7: Caregiver Remote Invite + Role Presets.

КОНТЕКСТ:
Admin invites caregiver remotely через signed link. Role presets bundle permissions + TTL. Content category + role-based envelope filtering enforce'ит privacy boundary crypto-уровнем.

Архитектура: use-cases/05-pairing-identity-trust.md §Caregiver Integration.

ЦЕЛЬ:
Реализовать Caregiver role + role presets + remote invite flow + content categorization + role-based envelope filtering + audit log.

SCOPE ВКЛЮЧАЕТ:
- Role Caregiver в Family Group.
- Role presets bundle permissions + default TTL:
  - MedicalWorker (24h TTL)
  - HiredCaregiver (indefinite, manual revoke)
  - FamilyCaregiver (as Member +)
  - Volunteer (30d renewable)
  - ClinicStay (stay duration)
- Admin UI "Add caregiver":
  - Preset selector
  - Optional permissions customize
  - TTL setter
  - Signed link generation
  - Share intent
- Caregiver-side: open link → preview → accept → server adds.
- Content category enum: FamilyContent / CareContent / Public.
- Role-based envelope filtering (producer picks category → recipients auto-calculated).
- Membership.ttl_expiry field (server enforces per-request).
- Audit log mandatory для caregiver actions (SEC-003):
  - View sensitive data, write notes, trigger actions
  - Notify admin на view sensitive data

SCOPE НЕ ВКЛЮЧАЕТ:
- Auto-create separate Care Group (MVP one Family Group + filtering).
- Caregiver-specific UI preset (V-x).
- B2B clinic onboarding (post-MVP).
- Multi-tier sub-presets (post-MVP).
- Marketplace.

DEPENDENCIES:
- F-1 done (group, envelope, many-to-many membership).
- F-4 done (signed operations).

LOCAL TEST PATH (mandatory per D-2):
- Admin generates invite с MedicalWorker preset + 24h TTL.
- Caregiver receives → preview → accept → membership created.
- Family content upload → caregiver excluded (no wrapper).
- Care content upload → caregiver included.
- Mock 24h → TTL expired → 403.
- Permission escalation blocked.
- Audit log: caregiver view → admin notified.

CONSTITUTION GATES:
- Rule 1, 2, 5, 6, 8.

EFFORT: Medium-Large (~3 weeks).

REFERENCE DOCS:
- use-cases/05-pairing-identity-trust.md §Caregiver Integration
- backlog SEC-003 (audit logging)
```

### Notes / gotchas

- **TTL — на membership, не на group**. Group persists, only caregiver's slot expires.
- **Content category — обязательное поле в wire format**. Если producer не указал — fall back to FamilyContent (privacy-safe default).
- **Audit log — privacy concern сам по себе**. Что хранить, как долго — отдельная implementation decision (frozen в monetization-legal).
- **Family vetoing**: может ли co-admin отменить caregiver'a, которого пригласил основной admin? — post-MVP question.

---

## S-8: Family Group Editor + History Rollback

### Что строим (mentor explanation)

Полноценный admin UI для редактирования Family Group config: layout editor (drag-drop tiles, add/remove, reorder), group settings editor (members, roles, permissions), config history (последние 20 версий) с rollback flow.

Это **последняя MVP spec'а**, которая закрывает admin UX полностью. Replaces stub спеки 009 (admin-mode-flows — was not started).

Также включает обновление **multi-admin merge UI** (existing logic из спеки 008) под group model — теперь конкуррентные edits решаются на group-level с N admin'ами, не pair-level с 2.

### Зачем именно сейчас

Admin'у нужно **управлять конфигурацией**, не только наблюдать. S-2 даёт device list + create group; S-8 даёт reality управления — фактическую раскладку.

### Источники и резолюции

- [`use-cases/04-remote-management.md`](use-cases/04-remote-management.md).
- Extends D-22 (admin preset complete UX).
- Replaces stub спеки 009.
- Uses multi-admin merge logic из спеки 008 (теперь group-aware).

### Scope: что входит

- **Layout editor**:
  - Drag-drop tiles на grid.
  - Add tile (capability picker: contact / app / SOS / website / widget).
  - Remove tile (long-press → confirmation).
  - Reorder.
  - Real-time preview (как будет выглядеть у Managed).
- **Group settings editor**:
  - Members list.
  - Role management (promote co-admin, demote, remove).
  - Permissions per role customize.
- **Config history**:
  - Storage последние 20 versions (client-side housekeeping per CLAUDE.md rule 8 + ARCH-008).
  - View past version.
  - Diff с current.
- **Rollback flow**:
  - Select past version → preview → confirm → applied.
  - Managed receives push (per rule 10 — significant event = actionable, time-sensitive).
- **Multi-admin merge UI** (existing 008 logic, обновлено для group):
  - Conflict detection at sync.
  - Merge resolution UI с preview.
  - Audit log записывает merge decisions.
- Health snapshot detail view (per device card из S-2 — теперь expanded):
  - Battery history graph (optional, может быть post-MVP).
  - Permission status per critical permission.
  - Last sync time, last activity.
  - **Cache size + free storage** (2nd pass 2026-05-28 evening) — admin видит texno состояние Managed device.
  - **Remote cache cleanup actions** (см. S-5 §Storage health monitoring):
    - «Clear all cached photos» button.
    - «Clear photos older than 1 month».
    - «Clear least-recently-viewed 50%».
    - Auto-cleanup settings toggle.

### Scope: что НЕ входит

- ❌ Wearable device management (V-5).
- ❌ Phone health alerts customization (notification rule 10 — отдельная spec'а для customizable notifications).
- ❌ Backup / restore manual (post-MVP).
- ❌ Performance metrics admin-side (Android Vitals — admin-уровневая аналитика).

### Dependencies

Должно быть готово **до** S-8:
- F-1 (group model).
- S-2 (admin app preset, device list).
- (Useful but not strict): S-3 (contact tiles — admin может adding contacts), S-4 (SOS — admin может configure), S-5 (photos — admin может upload).

### Local Test Path (D-2 mandatory)

- **Admin edits config** (add tile) → history entry created → push к Managed → config updates.
- **Multi-admin concurrent edit**: two admin emulators, both edit → conflict detected → merge UI → resolution → both applied.
- **Rollback to previous version**: preview diff → confirm → Managed updates.
- **History limit**: 21st edit → oldest pruned.
- **Permissions per role customize test**: admin changes Caregiver default permissions → applies → caregiver sees updated access.
- **Audit log**: every config change logged with timestamp + admin + change description.

### Effort

**Large** (~3-4 weeks). Самая большая S-спека.

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для S-8: Family Group Editor + History Rollback.

КОНТЕКСТ:
Last MVP spec'а — полноценный admin UI для editing Family Group config. Layout editor + group settings + history + rollback + multi-admin merge. Replaces stub спеки 009.

Архитектура: use-cases/04-remote-management.md.

ЦЕЛЬ:
Реализовать full admin editor с drag-drop layout, role management, config history (20 versions), rollback flow, multi-admin merge UI (group-aware).

SCOPE ВКЛЮЧАЕТ:
- Layout editor:
  - Drag-drop tiles on grid
  - Add tile (capability picker)
  - Remove (long-press confirm)
  - Reorder
  - Real-time preview
- Group settings editor:
  - Members list
  - Role management (promote/demote/remove)
  - Permissions customize per role
- Config history (last 20 versions, client-side housekeeping per rule 8):
  - View past version
  - Diff с current
- Rollback flow:
  - Select past → preview → confirm → applied
  - Push к Managed (per rule 10 — actionable significant event)
- Multi-admin merge UI (existing 008 logic, group-aware):
  - Conflict detection at sync
  - Merge resolution UI
  - Audit log decisions
- Health snapshot detail view (expanded from S-2 device card):
  - Per-permission status
  - Last sync/activity
  - Battery history (optional, может быть post-MVP)

SCOPE НЕ ВКЛЮЧАЕТ:
- Wearable management (V-5).
- Phone health alerts customization (отдельная spec'а).
- Manual backup/restore (post-MVP).
- Admin-side Vitals integration.

DEPENDENCIES:
- F-1 done (group model).
- S-2 done (admin app, device list).
- Useful: S-3, S-4, S-5.

LOCAL TEST PATH (mandatory per D-2):
- Admin edits → history entry + push к Managed.
- Multi-admin concurrent → merge UI → resolution.
- Rollback flow with preview diff.
- History limit: 21st pruned.
- Permissions customize per role.
- Audit log every change.

CONSTITUTION GATES:
- Rule 1, 2, 5, 6, 8, 10.

EFFORT: Large (~3-4 weeks).

REFERENCE DOCS:
- use-cases/04-remote-management.md
- specs/008 (multi-admin merge logic — extends)
- specs/009 (admin flows — was stub, S-8 replaces)
- CLAUDE.md rules 5, 8, 10
```

### Notes / gotchas

- **Multi-admin merge — existing 008 logic**. Не переписывать, только адаптировать под group (N admin'ов вместо 2).
- **History — client-side housekeeping** (rule 8). Server не cron'ит cleanup, Managed устройство сам подрезает старые версии.
- **Rollback notification — допустим push по rule 10** (actionable + significant + user-relevant — Managed config changed).
- **Audit log — same source как S-7**. Единая audit infrastructure.

---

# 🚀 Часть VI — Phase 3: Post-MVP v2 (V-1 .. V-5)

> После MVP stable (target ~6-8 months from F-1 start). Каждая V-spec — большая отдельная вертикаль.

## V-1: iOS Admin Preset

iOS-specific implementations поверх KMP foundation. Compose Multiplatform iosMain. iOS-specific OAuth (Apple Sign-In вдобавок к Google), deep links, share intents. App Store submission flow.

**Closes**: D-14 (iOS admin in post-MVP v2).
**Effort**: Very large (~3-4 months).

## V-2: Elderly-Friendly Messenger (Jitsi-based separate app)

Отдельное приложение (или shared module), на Jitsi Meet. SSO с launcher через F-4 AuthProvider (one Google login для обоих apps).

**Scope expansion (2026-05-28 evening)**: применить **Universal Preset Architecture** (D-22) **к messenger тоже**. Default presets:
- **Elderly preset** — extremely simplified UX, large buttons, easy answer flow, family-oriented.
- **Adult preset** — full features, normal messenger UX, для обычных взрослых членов семьи.
- **(Future) Caregiver preset** — focused на care communication.

Это значит messenger **не только для пожилых** — он становится **семейным messenger'ом**, где каждый член семьи выбирает свой preset. Это усиливает strategy «семья переезжает в наш messenger» (per user 2026-05-28).

Group call invites через Family Group. Reuse F-1 envelope encryption для encrypted media.

**Closes**: D-23 implementation (MVP — handoff; post-MVP — separate Jitsi app + SSO + presets).
**Effort**: Very large (~4-6 months).

## V-3: Full Shared Family Album

Videos + audio + memories. Chunked upload для больших файлов. Album UI (timeline, search, share, captions, anniversaries). Multi-content envelope encryption.

**Closes**: D-26 full implementation.
**Effort**: Large (~3 months).

## V-4: Android TV Preset

TV-specific UI (Leanback или custom Compose for TV). Voice navigation (через Android TV system). Big tiles. Family call quick-join. Ambient family presence mode.

**Closes**: D-24 (TV in post-MVP).
**Effort**: Large (~2-3 months).

## V-5: Health Device Monitoring Start

Wearable detection (BLE pairing с smart watches). Basic health data integration (heart rate, steps, fall detection). Alert escalation flow. Privacy boundaries (caregiver tier access).

**Implements**: FUTURE-SPEC-001 start.
**Effort**: Large (~3 months).

---

# 🌅 Часть VII — Phase 4: Long-term (L-x)

Не fixed roadmap, направления для consideration:

- **L-1**: Clinic / partner B2B integration (per D-15 architectural readiness, S-7 caregiver foundation).
- **L-2**: Marketplace для config templates (per CLAUDE.md rule 9 — shareable templates curated).
- **L-3**: AI provider implementations:
  - L-3a: App Actions adapter (Layer 2).
  - L-3b: MCP server adapter (Layer 3) — Cloudflare Worker extends.
  - L-3c: Gemini Nano integration (Layer 1) — image description, smart suggestions.
- **L-4**: Self-hosted Sentry migration (per server-roadmap).
- **L-5**: Backup / disaster recovery (D-Be-4).
- **L-6**: Social recovery (re-open D-25 OWD-4 — only if "потерял так потерял" окажется bad PR).
- **L-7**: Multi-device per user (FUTURE-SPEC-009).
- **L-8**: Key rotation / forward secrecy (FUTURE-SPEC-010).
- **L-9**: Family group encryption migration к Signal-style (если outgrow envelope).
- **L-10**: Wearable monitoring full (FUTURE-SPEC-001 expanded).
- **L-11**: Security sensors integration (FUTURE-SPEC-002).
- **L-12**: Closed messengers (LINE / WeChat / KakaoTalk — FUTURE-SPEC-003).
- **L-13**: Shared admin contact book (FUTURE-SPEC-004).
- **L-14**: Family Activity Challenges (PARK-001 если решим build).

---

# 🛠️ Часть VIII — Cross-cutting work

Не отдельные спекi, но **обязательные** работы параллельно или внутри других:

## New ADRs to write

- **ADR-008** — AI affordance posture («AI-ready, not AI-built»). Создаётся в составе F-2.
- **ADR-009** — Notification minimization (CLAUDE.md rule 10). Можно сделать как stand-alone задачу до F-1.

## New skills для создания в `.claude/skills/`

- **checklist-preset-readiness** — против unification erosion (D-22). Создаётся в составе F-3.
- **checklist-ai-readiness** — AI affordance check (D-20). Создаётся в составе F-2.
- **checklist-shareability** — rule 9 enforcement (опционально). Может быть pre-F-3 standalone.
- **checklist-notification-minimization** — rule 10 enforcement. Можно как stand-alone до F-1.
- **checklist-dev-experience** (NEW 2026-05-28 evening, user proposal) — проверка, что при написании spec'и учтены: local dev tools, reproducibility средства (staging-style env, fixtures, mock backends), debugging access. Активируется через `procedure-assess-spec-complexity` для любой спеки с backend interaction или multi-device flow. **Сэкономит часы при production debugging.**

## Spec-kit updates

- **spec-template.md** — добавить обязательную **«Local Test Path»** секцию (D-2). Standalone task, желательно до F-1.
- **procedure-cross-artifact-trace** — verify Local Test Path filled.
- **procedure-assess-spec-complexity** — activate new checklists conditionally based on spec content.
- Добавить **«AI Affordance»** section в spec template (D-21). Делается с F-2.

## CLAUDE.md updates (already done)

- Rule 9 — Shareability-readiness ✅ (2026-05-27).
- Rule 10 — Notification minimization ✅ (2026-05-28).
- Refuse patterns #12, #13 ✅.

## Server-roadmap updates

- **Self-hosted Sentry** exit ramp (для non-Play distribution когда понадобится).
- **Custom domain** для Cloudflare Worker (ARCH-001).
- **Cloudflare KV** для accurate rate-limiting (ARCH-002).
- **Blaze upgrade** trigger conditions (ARCH-003).
- **Cloud Storage at scale** trigger (для S-5 photos volume).

## Cross-cutting в коде

- **Sanitize logs from PII** — fitness function проверяет, что `phoneNumber` / `email` / `contactName` не попадают в plain text log statements. Делается с F-1.
- **APK size monitoring** в CI (per-PR check).
- **Macrobenchmark в CI** для cold start (PERF-001) — делается с F-1.
- **Localization fitness function** — все строки переведены на supported languages. Делается с F-3.
- **OEM matrix testing** — **mandatory section в каждой S-spec'е** (2026-05-28 evening — pre-F-1 adjustment). Minimum: Pixel + Samsung One UI + Xiaomi MIUI. Добавлено в `spec-template.md` Local Test Path section.

## Security mitigations (бесплатные, добавлены 2026-05-28 evening)

Стратегия security — **через influencer / blogger outreach** на этапе продвижения (user choice 2026-05-28). Чтобы блогеры не нашли catastrophic bug первыми, добавляем 4 бесплатных process'ных items, которые блогеры **не могут заменить**:

### Security Mitigation 1 — OWASP MASVS checklist через spec-kit

- `checklist-security` skill (уже в проекте) активируется через `procedure-assess-spec-complexity` для любой spec'и с crypto / auth / data flow surface.
- **Покрывает**: 60-70% security gap'а, которые блогеры не находят (signature verification, side channels, replay protection, etc.).
- **Стоимость**: 0₽.
- **Делается**: с F-2 (Capability Registry — там обновляется procedure-assess-spec-complexity).

### Security Mitigation 2 — Friend crypto review для F-1

- Когда F-1 PR готов — попросить **одного knowledgeable друга** (с crypto или serious backend background) посмотреть код.
- Не full audit — просто scan на nonce reuse, plaintext logging, signature bypass.
- **Покрывает**: 70-80% gap'а специфически для crypto-heavy F-1.
- **Стоимость**: 0₽ (бутылка вина если хочется).
- **Делается**: в Phase 1 completion criteria для F-1 — informal review check before merge.

### Security Mitigation 3 — Property-based crypto tests

- Mandatory в LocalTestPath для всех F-spec с crypto:
  - **Sign → tamper → verify FAILS**.
  - **Encrypt different content with same K → ciphertext different** (catches nonce reuse).
  - **Replay protection**: same signed message twice → server rejects second.
- **Покрывает**: 80% crypto-specific gap'а.
- **Стоимость**: 0₽ (часть spec'и которая всё равно пишется).
- **Делается**: с F-1, F-4 — добавлено в их Local Test Path requirements.

### Security Mitigation 4 — Soft launch gate (5-10 друзей перед public)

- Между **MVP code-complete** и **public release** — soft launch на **5-10 близких людей** install + use 2 weeks → review.
- Catches OEM-specific issues, UX confusion, обычные bugs **до того**, как блогер первый их найдёт.
- **Покрывает**: bytwo gap'а + OEM-specific basics.
- **Стоимость**: 0₽ (зависит от network of friends).
- **Делается**: как **обязательный gate** в roadmap между Phase 2 completion и public release. **Block** на любые блогер-outreach до soft launch passed.

### Что осталось НЕ закрыто (acceptable risk)

- Heavy cryptographic side-channel attacks (timing, power analysis).
- Memory leaks с sensitive data при advanced attack (memory dump after app close).
- Sophisticated multi-step race conditions involving server arbitration.

**Mitigation для этого**: blogger outreach phase (user's primary security strategy) обнаружит, если они станут реальной проблемой post-launch. Это **acceptable risk** в MVP scope.

## Soft launch gate (release process)

**Обязательный** перед public release:

1. **MVP code-complete** (все F + S done).
2. **Soft launch с 5-10 друзьями** в течение **2 недель**.
3. **All P0/P1 bugs** найденные friends — fixed.
4. **OEM matrix smoke** — Pixel + Samsung + Xiaomi (mandatory per OEM testing rule).
5. **Privacy Policy text** — published (pre-release task).
6. **Account deletion flow** (S-6) — tested end-to-end.
7. **Property-based crypto tests** — все green.
8. **Friend crypto review** для F-1 — done.
9. **`checklist-security` review** для всех F+S — passed.
10. → **Public release** + start blogger outreach (user's primary security strategy).

## Privacy Policy

- **Termly / iubenda template** setup pre-release.
- **Account deletion section** (per S-6).
- **Envelope encryption limits disclosure** («downloaded copies stay»).
- **Telemetry opt-in disclosure** (D-12).

---

# 📐 Часть IX — Dependencies DAG

Visual representation deps:

```
Phase 0: Vision DONE
        │
        ▼
┌───────────────────────────────────────────┐
│ Phase 1: Foundation (parallelizable)      │
│                                            │
│  F-1 ──────────────┬──────────────┐       │
│  Family Group       │              │       │
│  Envelope crypto    │              │       │
│  Server arbitration │              │       │
│                     │              │       │
│  F-2 ──────────┬────┤              │       │
│  Capability    │    │              │       │
│  Registry      │    │              │       │
│                │    │              │       │
│  F-3 ──┬───────┤    │              │       │
│  Wizard │      │    │              │       │
│  Module │      │    │              │       │
│  Local. │      │    │              │       │
│         │      │    │              │       │
│  F-4 ───┼──────┼────┼──────────────┤       │
│  Auth   │      │    │              │       │
│  Google │      │    │              │       │
└─────────┼──────┼────┼──────────────┼───────┘
          │      │    │              │
          ▼      ▼    ▼              ▼
┌───────────────────────────────────────────┐
│ Phase 2: MVP Vertical Slices              │
│                                            │
│  S-1 Simple Launcher (F-2, F-3)           │
│    │                                       │
│    ▼                                       │
│  S-3 Contact Tiles (F-1, F-2, S-1, S-2)   │
│    │                                       │
│    ▼                                       │
│  S-4 SOS (F-2, S-1, S-2)                  │
│    │                                       │
│    ▼                                       │
│  S-5 Photos (F-1, S-3)                    │
│                                            │
│  S-2 Admin App (F-1, F-3, F-4)            │
│    │                                       │
│    ▼                                       │
│  S-6 Account Deletion (F-1, F-4)          │
│    │                                       │
│    ▼                                       │
│  S-7 Caregiver (F-1, F-4)                 │
│    │                                       │
│    ▼                                       │
│  S-8 Family Editor (F-1, S-2)             │
└───────────────────────────────────────────┘
                    │
                    ▼
              MVP RELEASE
                    │
                    ▼
┌───────────────────────────────────────────┐
│ Phase 3: Post-MVP v2 (parallel)           │
│  V-1 iOS · V-2 Messenger · V-3 Album      │
│  V-4 TV · V-5 Wearables                   │
└───────────────────────────────────────────┘
                    │
                    ▼
              Phase 4: Long-term (L-x)
```

**Critical path для first end-to-end demo**: F-1 → S-1 → S-3 (~3 months sequential).

**Parallelization opportunities**:
- F-1, F-2, F-3, F-4 — все могут идти параллельно (different modules, не cross-deps).
- S-1 + S-2 — параллельно после F's.
- S-4 + S-5 + S-6 + S-7 — параллельно после S-1, S-2 base.

---

# 📋 Часть X — Tracking & Exit Ramps

## How to track progress

- Этот roadmap — **source of truth для phase планирования**.
- **README.md в use-cases** — source of truth для D-questions status.
- **project-backlog.md** — operational TODO list (хранится отдельно).
- **Каждая спека** в `specs/` — implementation source of truth, с обязательной «Local Test Path» секцией.

## Exit ramps (one-way doors с regret conditions)

Recorded в decision log of [`01-vision-and-positioning.md`](use-cases/01-vision-and-positioning.md) §9. Краткий summary:

| Decision | Exit ramp |
|---|---|
| Family Group + envelope encryption (D-25) | Migrate to Signal-style group crypto в будущем. Pair-keys + envelope остаются как fallback. |
| Capability Registry pattern (D-17) | Registry полезен сам по себе для intent dispatch, даже без AI implementations. |
| Wizard module + nested templates (D-22 / D-5/7/8) | Деградация до direct setup-screens возможна, без manifest layer — потеря reuse. |
| Family monthly subscription (D-11) | Data model поддерживает individual / per-group tier additively. |
| Companion-only positioning (D-1) | Hybrid (self-serve + companion) — additive opt-in onboarding. |
| Universal Preset Architecture (D-22) | Split на independent apps возможен, без архитектурной катастрофы. |
| Android Vitals primary crash source (D-16) | CrashReporter port позволяет добавить любой adapter. |
| Google Sign-In для admin (D-Pair-1) | Anonymous fallback доступен; AuthProvider port позволяет сменить provider. |
| Server-arbitration model (D-25) | Если scale issue — migrate к signed-chain membership ledger. |
| Cloudflare Worker как backend MCP host | Migrate на own server (server-roadmap). |
| 30-day grace period для deletion (S-6) | Configurable per region post-MVP. |
| Localization initial set (10 языков) | Add/remove на основе market signals; system locale fallback to EN. |
| Notification minimization rule (rule 10) | Каждый case подлежит review; можно ослабить если critical event миссится. |
| Performance gates (1s cold start) | Per-flavor adjustable; release blocker только для main user-facing flows. |

## История изменений

- **2026-05-28** — Полная перезапись roadmap'а от **«launcher для пожилых»** к **Family Care Ecosystem**. Mentor-стиль с copy-paste-ready prompts для `/speckit.specify`. Phase 0 vision discussion с 28 D-вопросами закрыта в discussion 2026-05-27/2026-05-28. Старый roadmap (691 строка, на 2026-05-07) заменён.
- _(сюда добавляются изменения по мере работы)_

## Связь с другими документами

- **Vision и use-cases**: [`docs/product/use-cases/`](use-cases/README.md) — обязательная база.
- **Конституция**: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md).
- **Project rules**: [`CLAUDE.md`](../../CLAUDE.md) (rules 1-10 + refuse patterns 1-13).
- **Server roadmap**: [`docs/dev/server-roadmap.md`](../dev/server-roadmap.md).
- **Backlog**: [`docs/dev/project-backlog.md`](../dev/project-backlog.md).
- **ADRs**: [`docs/adr/`](../adr/).

---

**Конец roadmap'а. Следующий шаг — начать F-1 через `/speckit.specify` с prompt'ом из соответствующего раздела.**

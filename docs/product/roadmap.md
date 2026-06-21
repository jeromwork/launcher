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
Phase 1: Foundation (~5-7 weeks sequential)
   F-1 Family Group Foundation        ❌ DEPRECATED 2026-05-28
                                      (moved to ecosystem-vision.md)
   Шаг 1: F-3  Wizard Module + Localization    — wizard работает ЛОКАЛЬНО (✅ Done 2026-06-17, merged PR #19)
   Шаг 2: F-CRYPTO  core/crypto/ KMP module    — lib-family-crypto (🚧 InProgress 2026-06-17 — implementation: ports + Libsodium adapters + Android Keystore wrap + RFC KAT + instrumentation green on emulator; docs/dev/crypto-review.md published; awaits paid audit before billing per SRV-CRYPTO-003)
   Шаг 3: F-4  AuthProvider + Google Sign-In   — identity foundation (✅ Done, merged PR #21 2026-06-18)
   Шаг 4: F-5  Root Key Hierarchy + Config Encryption + Recovery 🔴 PRODUCTION BLOCKER (🟢 Implemented as F-5b envelope variant 2026-06-20 — spec 018 pivoted from symmetric self-edit to hybrid envelope per spec 011 §C-2/§C-3; RemoteStorage facade + ConfigSaver + EnvelopeBootstrap + multi-recipient cross-user delegation; 68 JVM tests green; legacy AeadConfigCipher/KeyRegistry/SealedConfig removed)
          → создаёт core/keys/ envelope foundation, multi-recipient ready, reusable across launcher + future messenger + album
   Шаг 4a: F-5c Push-trigger foundation + config-updated event (📋 PLANNED 2026-06-20, rescoped 2026-06-20 evening)
          → **Generic reusable push infrastructure** (не узкая мини-спека) + первый use case (config-updated). Foundation:
            - Cloudflare Worker `POST /push` endpoint (generic, не /trigger-config-updated) с EventTypeRegistry (whitelist event types + per-type rules: auth, rate-limit, collapse-key, priority).
            - JWT verification через `jose` library (dynamic JWKS TTL по Cache-Control), все claims проверяются (alg=RS256, iss, aud, exp, iat, sub, kid).
            - Idempotency-Key (UUID v4 per push action) через Workers KV, 10-min TTL.
            - Recipient resolution per TargetScope (OwnDevices / OwnAndGrants).
            - FCM dispatch с bounded retry (3 attempts), collapse_key per `(eventType, ownerUid, contextKey)`.
            - Rate-limit per UID per eventType (защита от abuse, не от UX — нет debounce, push = explicit user action per spec 008 model).
            - Client side: `core/push/` KMP module — generic `PushTrigger` port + `PushHandler` + `EventType` sealed + `TargetScope`.
            - Android adapter: `HttpPushTrigger`, `FcmTokenPublisher` (отдельный port от EnvelopeBootstrap — lifecycle independence).
            - PushPayload breaking change: `linkId` → nullable, `ownerUid + configName + eventType` first-class (старая spec 008 единственный consumer, переписывается параллельно).
            - First event type: `ConfigUpdated`. ConfigSaver triggers push после successful save через `PushTrigger.trigger(...)`.
          → Foundation переиспользуется в S-4 (sos-triggered), S-9 (battery-critical, device-offline), S-2 (pairing-accepted), V-2 (message-arrived, call-incoming), V-3 (album-photo-added) — каждый последующий = ~30 строк (EventTypeRegistry entry + handler + consumer wrapper), без правки foundation. Архитектура изложена в spec 019 §«Reuse pattern».
          → Extraction-ready: `core/push/` → Maven artifact + `workers/push/` → отдельный repo. Триггер extraction: начало V-2 (Messenger как отдельное приложение). Документировано в SRV-PUSH-EXTRACTION (server-roadmap.md).
          → Скоп: **5-7 дней работы** (foundation +2 дня против узкой мини-спеки, payback в S-4/S-9/V-2 которые сэкономят 3-5 дней каждая). Owner approved scope expansion 2026-06-20 evening.
   Шаг 4b: Spec 008 rewrite (collaborative editing) — отдельная спайка (📋 PLANNED 2026-06-20)
          → Текущая spec 008 устарела (anonymous pair model до decisions 2026-05-30/2026-06-15). Concepts (collaborative editing, merge UI, pending-changes warning, Room persistence) сохраняются. Storage layer переписывается на ConfigSaver + RemoteStorage от F-5b. Legacy code: DefaultConfigEditor, FirebaseConfigApplier, FirebaseTransactionScope удаляются. WorkManager async push integration. Объём: ~2-3 недели работы. Owner approved 2026-06-20.
   Шаг 4c: E2E через Firestore Emulator extension — F-5b envelope rules tests (TypeScript через @firebase/rules-unit-testing — 22/22 в firestore-tests/rules.f5b.envelope.test.ts) + Android instrumented [CloudConfigEncryptionE2ETest](../../app/src/androidTest/java/com/launcher/app/data/envelope/CloudConfigEncryptionE2ETest.kt) (🟢 Verified 2026-06-20 на Xiaomi 11T: Firebase Emulator + real `launcher-old-dev` cloud — оба пути 2/2 PASSED, SC-001 acceptance закрыт на реальном TEE Keystore + MIUI).

   F-2  Capability Registry  — отложен в Phase 4+ entirely
        │
        ▼
Phase 2: MVP Core Vertical Slices (REORDERED 2026-06-15 v3, sequential, 9 спек)
   S-1   Simple Launcher Wizard         (LOCAL — 1 default config only)
   S-3   Contact Tiles + Calling         (LOCAL — ACTION_DIAL)
   S-5   Contact Photos                  (CLOUD — первый Sign-In; первый потребитель F-5 KeyRegistry под new DEK)
   S-8   VersionedConfigViewer + Editor  (CLOUD — named configs в cloud namespace)
   S-9   Phone Health Monitoring         (CLOUD — battery / online / activity)  ← NEW
   S-4   SOS Capability                  (CLOUD — push admin'у)
   S-2   Admin App + QR Pairing          (CLOUD — QR primary; LinkInvite — Phase 4)
   S-10  Subscription Server Timer       (CLOUD — server-only entitlement) ← NEW
   S-6   Account Deletion                (CLOUD — GDPR / Play Store gate)
        │
        ▼
Phase 3: MVP Preset Depth (sequential, 10 P-спек) — ТОЖЕ MVP
   P-1   Preset Schema v2 + Wizard Engine        (schemaVersion bump 1→2 backward-compat)
   P-2   Android Deep Integration Steps          (drawer block / swipe block / hide Settings)
   P-3   Preset Authoring + Sharing              (admin создаёт / экспорт / импорт)
   P-4   Adaptive UX Presets                     (tremor-mild / tremor-severe / vision / perception)
   P-5   Config Copy Between Own Devices         (multi-device same owner)
   P-6   Account Recovery Flow + 2FA escrow      (потерял телефон → новое устройство)
   P-7   Optional Step Reminder System           (persistent reminder без надоедания)
   P-8   Provider Recipe Catalogue               (server-side recipes: deep-link templates с параметрами)  ← NEW
   P-9   Device Inventory Sync                   (зашифрованный список установленных apps senior'а для admin'а)  ← NEW
   P-10  Multi-app Cohabitation                  (chain-of-trust между launcher / messenger / album, one-click recovery)  ← research notes: docs/product/future/multi-app-cohabitation.md
        │
        ▼ ✅ PRODUCTION RELEASE — конец полного MVP (Phase 2 + Phase 3)
        │
        ▼
Phase 4: Product Extensions (post-MVP, 7 V-спек)
   V-1   iOS Admin Preset                        · V-2 Elderly-Friendly Messenger
   V-3   Full Shared Family Album                · V-4 Android TV Preset
   V-5   Wearable Health Monitoring              · V-6 Caregiver Remote Invite + LinkInvite
   V-7   Audit Log Infrastructure
   F-2   Capability Registry Foundation          (когда найдётся первый AI/MCP consumer)
        │
        ▼
Phase 5: Long-term Parking Lot (L-x, направления на годы)
   L-1 Clinic B2B · L-2 Marketplace · L-3 AI adapters · L-4 Self-hosted Sentry · ...
```

**Critical path для production release**: F-3 → F-CRYPTO → F-4 → F-5 → S-1 → S-3 → S-5 → S-8 → S-9 → S-4 → S-2 → S-10 → S-6 → P-1 → P-2 → P-3 → P-4 → P-5 → P-6 → P-7 → P-8 → P-9 → P-10 (sequential).

**Critical path для local-only public beta**: F-3 → S-1 → S-3. Можно выпустить **local-only public beta** значительно раньше production release.

**MVP total estimate**: Phase 1 + 2 + 3 ≈ **8-10 месяцев** sequential.

**Vision shift 2026-05-28**: Family Group primitive removed from launcher (was F-1). Multi-admin scenarios handled by N independent pair-edits merged via spec 008. Shared content (album, messenger) moved to separate ecosystem apps. Group primitive design preserved in [ecosystem-vision.md](future/ecosystem-vision.md) for future messenger/album specs. See research docs in `docs/research/2026-05-28-*.md`.

**Architecture shift 2026-06-15** (deferred-cloud, per [`decisions/2026-06-15-deferred-cloud/`](decisions/2026-06-15-deferred-cloud/)): каждое устройство самодостаточно. Google Sign-In — условие cloud action, не первого запуска. Local mode — бесплатен бессрочно. Cloud mode (pair, sync, push, remote) — после Sign-In, после trial = subscription. Конфиг принадлежит локальному Google-аккаунту устройства. **Каждый юзер делает Sign-In своим собственным Google-аккаунтом на своём собственном телефоне** — не «дочь Sign-In'ит бабушкин телефон через свой аккаунт», а «бабушка / помогающий взрослый Sign-In'ит бабушкин телефон под бабушкиным Google-аккаунтом (тем же, что в её Play Store)». QR-pairing — primary; signed invite link — additive **только в Phase 4** через `LinkInvitePairingChannel`.

**Phase split 2026-06-15 v3** — **MVP теперь = Phase 2 + Phase 3** (раньше = только Phase 2):
- **Phase 2 "MVP Core Vertical Slices"** — 9 спек, видимый функциональный минимум для demoable cut.
- **Phase 3 "MVP Preset Depth"** — 9 P-спек, **тоже MVP**, расширяет до полноты обещаний vision (preset architecture, adaptive UX, recovery, generic app launch с параметрами). Раньше тут была Phase 3 "Post-MVP v2" — она переехала в Phase 4.
- **Phase 4 "Product Extensions"** — V-спеки (iOS / TV / messenger / album / wearable / caregiver / audit) + F-2 (Capability Registry, ждёт consumer'а).
- **Phase 5 "Long-term Parking"** — L-x идеи.

**Phase 2 reorder v3** (2026-06-15):
- Добавлены **S-9 Phone Health Monitoring** (обязательно для vision «снижение тревоги семьи») и **S-10 Subscription Server Timer** (server-side entitlement, защита от взлома без R8).
- **S-7 Caregiver Remote Invite** — убран из Phase 2 → переехал в **Phase 4 как V-6**. Caregiver flow требует LinkInvite + audit log + role-based access — слишком много для MVP.
- Co-admin onboarding в Phase 2 — через **тот же QR**, не через invite link. Если внук тоже хочет управлять бабушкиным телефоном — он физически делает QR-pair со своего телефона.
- **Local mode = 1 default config** (не named) — упрощение. Named configs появляются **только** в cloud mode (per [decision 02](decisions/2026-06-15-deferred-cloud/02-config-ownership-per-device.md)).

> ⚠️ **Divergence note (опция A 2026-06-15)**: спека [`014-tile-editing-admin-senior-profiles`](../specs/014-tile-editing-admin-senior-profiles/spec.md) FR-003 в текущем виде описывает «до 5 named configs локально». Это **противоречит** решению v3 (local = 1 default only). Спека 014 остаётся как есть в работающем коде F-014.0, но **будет переписана в P-3** (Preset Authoring + Sharing) в Phase 3. До тех пор любой код, опирающийся на named configs в local mode, должен **рассматриваться как legacy**.

**Phase 1 reorder v3** (2026-06-19, current):
- F-3 (Wizard + Localization) первый — работает локально без identity. ✅ Done.
- F-CRYPTO второй — `core/crypto/` KMP-модуль. 🚧 InProgress.
- F-4 третий — AuthProvider + Google Sign-In, identity foundation. ✅ Done (PR #21).
- **F-5 четвёртый — redefined 2026-06-19**: root key hierarchy + ConfigCipher + recovery flow. Production blocker для cloud release. Foundation, на которой строятся все cloud-фичи Phase 2 (S-2 multi-admin, S-4 SOS, S-5 photo, S-9 health, V-2 messenger). Spec 018. 🚧 Planning.
- F-2 — Phase 4+ entirely.

**Phase 1 reorder v2** (2026-06-15, superseded):
- F-3 первый, F-CRYPTO второй, F-4 и F-5 «активируются при cloud features» (наглядное упрощение). v3 явно ставит F-5 в Phase 1 sequential, потому что F-5 теперь не «активируется», а **строит foundation** для всего Phase 2.

**Phase 1 reorder v1** (отменён, для истории):
- ~~F-4 (AuthProvider) — первый шаг~~ → отменено в v2, F-3 стал первым.

---

# 🏗️ Часть IV — Phase 1: Foundation

> **Цель Phase 1**: подготовить архитектуру для всего, что строится в Phase 2. **Refactoring** existing system без потери функциональности.
>
> **Принцип**: каждая F-спека делает migration внутри. После F-x existing функциональность 002 / 007 / 010 продолжает работать, но через новый, более общий путь.
>
> **Порядок выполнения (определён 2026-06-15)** — секции ниже идут **в порядке выполнения**:
>
> 1. **F-1** — DEPRECATED (оставлен в файле как историческая запись).
> 2. **F-4** — AuthProvider + Google Sign-In (identity foundation).
> 3. **F-CRYPTO** — выделение `core/crypto/` (`lib-family-crypto`) KMP-модуля.
> 4. **F-5** — ConfigDocument E2E Encryption (использует `core/crypto/`).
> 5. **F-3** — Wizard Module + Localization.
> 6. **F-2** перенесён в конец Phase 2 (см. Часть V).
>
> **Cross-cutting skills**, гоняющиеся на каждой S-спеке начиная с Phase 2:
> - `checklist-localization-ui` — UI готов к разным длинам строк (RU/DE ~30-40% длиннее EN), RTL (AR/HI), plural rules, mock-screenshot минимум для 3 локалей.
> - `checklist-capability-registry-readiness` — refuse если нет `// TODO(capability-registry)` для нового action; запрещает упоминание конкретных MCP/AI провайдеров в domain или S-спеках.

---

## F-1: Family Group Foundation & Core Crypto

> ⚠️ **CRITICAL ARCHITECTURE NOTE FOR AI:** THE "FAMILY GROUP" PARADOX
> **UI-уровень монолитной группы (редактор группы) отложен в лаунчере**, но логическая серверная структура и криптографический слой Envelope Encryption **ОСТАЮТСЯ ЯДРОМ СИСТЕМЫ** и реализуются в этой спецификации. Все последующие задачи (S-2, S-5, S-7, S-8) опираются на эту логику.
>
> **Как работает группа и шифрование (Envelope Encryption):**
> 1. **Общего ключа нет (`priv_G` не существует).** Группа — это только логический список `public_key` участников на сервере.
> 2. **Шифрование данных (Wrapped Key Envelope):** Контент (фото, конфиг) шифруется одноразовой "отмычкой" (Симметричный ключ `K` / CEK). Затем эта отмычка `K` шифруется публичным ключом *каждого* разрешенного участника (создаются N "конвертов" или wrappers). Сервер хранит 1 зашифрованный файл + N конвертов.
> 3. **Role-Based Envelope Filtering (Математическая защита):** Роли (например, Caregiver / Сиделка) ограничиваются не просто флагами на сервере, а криптографией. Если загружается семейное фото (`FamilyContent`), приложение просто **не генерирует** конверт с отмычкой для публичного ключа сиделки. Сиделка технически не может открыть файл.
> 4. **Библиотека `core/crypto/`:** Вся криптография выносится в отдельный KMP-модуль `core/crypto/` (`lib-family-crypto`), не привязанный к UI лаунчера. Сейчас он реализует Layer 3 (Envelope Encryption для небольших групп). В будущем (для мессенджера на 1000+ участников) он будет расширен до Layer 4 (Sender Keys / MLS), не меняя примитивов (libsodium).

---

### Что строим (mentor explanation)

Сейчас наша система знает только **pair** — пара устройств (admin ↔ Managed), которая доверяет друг другу через спеку 007. Это работает для одного admin'а и одной бабушки, но **vision требует логическую группу**: семья из N родственников, M устройств, плюс caregivers.

Мы строим логическую **Family Group** — это серверный список участников, а не монолитный UI в лаунчере. Group объединяет участников (admin, co-admin, member, managed, caregiver), их устройства, общий trust state. Каждый участник имеет **свой собственный keypair**, никто не делится своим private key. Чтобы content был доступен всем — используется **envelope encryption** (см. блок ⚠️ CRITICAL ARCHITECTURE NOTE FOR AI выше).

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

## ~~Шаг 1~~ — F-4: AuthProvider + Google Sign-In (теперь cloud-feature setup)

**Spec number: 017** (assigned 2026-06-18 — переназначен с placeholder'а multi-app cohabitation, который перенесён в Phase 3 как P-10).


> **Order shift 2026-06-15 v2**: F-4 **больше не первый шаг Phase 1**. Phase 1 шаг 1 теперь — F-3 (wizard работает локально). F-4 активируется **в момент первого cloud action** в Phase 2 (S-5 / S-8 / S-4 / S-2), а не на первом запуске app.
>
> Per [decision 2026-06-15-deferred-cloud/01](../decisions/2026-06-15-deferred-cloud/01-deferred-sign-in.md): Google Sign-In — условие cloud-mode, не первого запуска. Local-mode работает без Sign-In, бесплатно бессрочно.
>
> Сама спека F-4 (Sign-In Adapter, identity model, token refresh, session management) остаётся как описана — меняется **только момент её активации** в продукте.

> **Order shift v1 (отменён)**: F-4 = первый шаг Phase 1.

> **Priority shift (2026-05-29)**: F-4 поднят как **dependency для F-014.1** (Server backup of named configs). Без stable Google identity невозможно cross-device sync admin'ского self-config. См. specs/014-tile-editing-admin-senior-profiles/spec.md §Phase Dependencies.
>
> **Order shift (2026-06-15)**: F-4 — **первый** шаг Phase 1. Identity foundation нужна до криптографии (для key derivation) и до всех S-спек.

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

F-CRYPTO (шаг 2) тоже зависит от F-4: encryption keys привязываются к stable Google UID admin'а и Managed.

### Источники и резолюции

- [`use-cases/05-pairing-identity-trust.md` §Identity](use-cases/05-pairing-identity-trust.md) D-Pair-1.
- **Closes D-Pair-1** (полный отказ от анонимности — все устройства named) — **в MVP** (решение от 2026-05-30).
- Декабрь 2026-05-28 evening: Google OAuth для admin зафиксирован.
- Extends `project-backlog.md` AUTH-001.

### Scope: что входит

- **`AuthProvider` port в `core/domain/`** — **provider-agnostic** интерфейс. Не упоминает Google, Firebase, OAuth, email в сигнатурах. Возвращает абстрактный `AuthIdentity { stableId, displayName?, email?, providerKind }`. Google — **лишь один из возможных провайдеров** (rule 2 ACL применён к auth-провайдерам). Любой будущий провайдер (Phone, Email/Password, Apple, SAML, custom) подключается через **тот же `AuthProvider` port** добавлением нового adapter'а — **без изменения port'а** и без переписывания consumer'ов (F-5, S-2, S-6).
- **`GoogleSignInAuthAdapter`** — **первая (и единственная в MVP)** реализация `AuthProvider`:
  - Firebase OAuth integration (Google Sign-In).
  - Email-bound identity binding.
  - Token refresh / session management.
- **`FakeAuthAdapter`** — реализация `AuthProvider` без сети, для unit-тестов (rule 6 mock-first).
- **DI seam**: где-то один `AuthProvider` инжектится по build flavor / runtime config. Замена provider'а = замена одной DI-привязки.
- **Отказ от AnonymousAuthAdapter**: все устройства используют named identity через `AuthProvider` (decision 2026-05-30 D-Pair-1).
- Identity model:
  - `User { id, identity_keys, email?, display_name?, subscription_state, providerKind }`
  - `providerKind` — enum-открытый field (`GOOGLE` сейчас, расширяется при добавлении adapter'а).
  - Email — Required для всех пользователей **в MVP** (потому что единственный adapter — Google, который email возвращает). Когда добавим phone-only provider — email станет optional, identity model уже это допускает.
- Session management (token storage, refresh logic, expiry handling) — **в abstract `SessionStore` port'е**, не привязан к Firebase token shape.
- **Inline TODO**: `// TODO(auth-provider-extensions): Phone / Email-Password / Apple / SSO adapters add through this port without changing the port shape`.

### Scope: что НЕ входит

- ❌ Phone Auth, Email/Password Auth, Apple Sign-In, SSO — только **Google adapter** в MVP. **Port спроектирован так**, что эти провайдеры добавятся additively (новый adapter + новая DI-привязка), **без переписывания port'а**, F-5, S-2, S-6.
- ❌ Apple Sign-In — это V-1 iOS территория.
- ❌ Real subscription billing flow (frozen).
- ❌ Account recovery UI flow — частично в S-2 (admin onboarding), полная — S-6 (account deletion с recovery option).

### Dependencies

Должно быть готово **до** F-4:
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

## Шаг 2 — F-CRYPTO: `core/crypto/` KMP module foundation 🆕

> **Новый шаг, добавлен 2026-06-15.** Выделяет криптографию в отдельный KMP-модуль **сразу при первом использовании**, а не задним числом внутри F-5. Это применение [CLAUDE.md rule 2](../../CLAUDE.md) (ACL для каждой external dependency) и [rule 6](../../CLAUDE.md) (mock-first).

### Что строим (mentor explanation)

`core/crypto/` (он же `lib-family-crypto`) — отдельный KMP common module, в котором живёт **вся** криптография проекта. Содержит:

- libsodium binding (через KMP-обёртку или платформенные adapter'ы).
- Доменные **ports**: `AeadCipher` (симметричное AEAD-шифрование), `AsymmetricCrypto` (X25519 ECDH + signing), `KeyDerivation` (HKDF), `RandomSource`.
- Реальные **adapters** на libsodium.
- **FakeAdapter** для тестов (deterministic, без libsodium).
- Property-based crypto tests: sign→tamper→verify FAILS, nonce reuse → ciphertext differs, replay protection.

Модуль **полностью отвязан** от UI, бизнес-логики и Firebase. Любой код, которому нужна криптография (F-5 ConfigCipher, спека 011 media blobs, будущий мессенджер), импортирует из `core/crypto/` — не пишет inline libsodium вызовы.

**Задел на будущее**: сейчас модуль реализует Layer 3 (Envelope Encryption для небольших групп). Архитектурно готов к расширению до Layer 4 (Sender Keys / MLS для мессенджера) без изменения примитивов.

### Зачем именно сейчас (а не задним числом из F-5)

CLAUDE.md rule 2: **wrap every external SDK so its types never appear in any signature visible to domain**. libsodium — внешняя SDK. Если ввести его inline внутри F-5 ConfigCipher, а позже вынимать — это retroactive ACL, нарушение rule. Правильно — выделить сразу.

Дополнительно: F-5 ConfigCipher + спека 011 media crypto + будущий мессенджер используют **одни и те же примитивы**. Дублирование = bug surface (например, nonce-reuse в одном месте, корректное использование в другом). Один источник истины.

### Источники и резолюции

- [CLAUDE.md rule 2](../../CLAUDE.md) — ACL для каждой external dependency.
- [CLAUDE.md rule 6](../../CLAUDE.md) — mock-first development.
- Property-based crypto tests = Security Mitigation 3 (см. Часть VIII).
- ~~Friend crypto review = Security Mitigation 2~~ — **ОТМЕНЕНО 2026-06-17**. Solo-dev без сети криптографов; заменено на **измеримый test-driven validation set** (см. ниже).

### Validation strategy (replaces friend crypto review, 2026-06-17)

Решение 2026-06-17: жёсткое требование «2 знакомых криптографа» снято как нереалистичное для bootstrapped solo-dev. **Заменено на четырёхуровневый measurable validation set**, который индустриально сильнее single peer review:

1. **RFC Known Answer Tests (KAT) — обязательны:**
   - RFC 7748 (X25519), RFC 8032 (Ed25519), RFC 8439 (ChaCha20-Poly1305), RFC 5869 (HKDF-SHA256). Векторы прямо из RFC, копируются в `commonTest`.
2. **Google Project Wycheproof test vectors — обязательны.** Покрывают edge cases (low-order points, malleability, point-at-infinity). https://github.com/google/wycheproof.
3. **Property-based tests** (Kotest properties): ECDH symmetry, encrypt→decrypt roundtrip, sign→tamper→fail, nonce policy enforcement.
4. **Industrial reference baseline** (документ `docs/dev/crypto-review.md`): «мы используем тот же primitives stack, что Signal, WhatsApp (Signal Protocol), WireGuard, age, Threema, Bitwarden Send». Это — наш argument к любому регулятору/аудитору.

**Платный аудит** (Cure53 / 7ASecurity / Radically Open Security ~$5-12k) — записан в `docs/dev/server-roadmap.md` как milestone **перед запуском billing/payments**, не для F-CRYPTO merge'а.

### Scope: что входит

- KMP common module `core/crypto/` (gradle subproject, артефакт `lib-family-crypto`).
- **KMP targets с day 1**: `androidMain`, `jvmMain` (тесты), `iosX64`/`iosArm64`/`iosSimulatorArm64` (декларированы в `build.gradle.kts`, CI iOS не активен до первой iOS-фичи). **Решение 2026-06-17**: владелец планирует мессенджер + фото-приложение + Android TV / Google TV / EOS, поэтому iOS-readiness закладываем сразу — overhead ~1 день setup vs полный rewrite потом.
- **Library extraction policy** (CLAUDE.md rule 4): модуль живёт **внутри launcher-репо** как subproject до появления 2-го реального потребителя (мессенджер или фото-приложение). Inline TODO в `build.gradle.kts`: `// TODO(extract-when-2nd-consumer): git filter-repo в отдельный приватный репо`. **Лицензия** — приватная пока не вынесен; при вынесении — Apache 2.0 (Kerckhoffs's principle: opensource крипты даёт credibility, не уменьшает безопасность).
- Доменные ports в `core/crypto/api/`:
  - `AeadCipher { encrypt, decrypt }` — XChaCha20-Poly1305 или AES-GCM.
  - `AsymmetricCrypto { generateKeyPair, deriveSharedSecret, sign, verify }` — X25519 + Ed25519.
  - `KeyDerivation { derive(salt, info, length) }` — HKDF-SHA256.
  - `RandomSource { nextBytes(n) }` — cryptographically-secure RNG.
  - **`SecureKeyStore { store(keyId, blob), load(keyId) }` 🆕** — порт для wrap pattern (см. ниже).
  - **`KeyRotation { currentKeyId(), keyHistory(), rotateIdentityKey(reason), revoke(keyId, reason) }` 🆕 (interface-only, real-impl = stub)** — швы для будущих ротационных спек.
  - **`KeyEscrow { export(passphrase), restore(bundle, passphrase) }` 🆕 (interface-only, real-impl = stub)** — швы для 2FA admin migration спеки.
- Реальный adapter на libsodium через `ionspin/kotlin-multiplatform-libsodium` (`LibsodiumAeadCipher`, etc.) — отдельный subpackage `core/crypto/libsodium/`. Research-фаза спеки **обязана проверить** последние релизы / open issues по iOS targets; fallback на BouncyCastle (Android) + собственный cinterop (iOS) если ionspin окажется dead.
- **`SecureKeyStore` Android adapter — wrap pattern** 🆕: Curve25519 private keys сериализуются, шифруются AES-256-GCM ключом из Android Keystore (TEE), хранятся в app sandbox файле. Это паттерн **Signal / WhatsApp / Bitwarden** (TEE не поддерживает Curve25519 нативно, только wrap). iOS adapter — аналогично через iOS Keychain (`kSecAttrAccessibleAfterFirstUnlock`).
- **Key storage wire format с `schemaVersion`, `algorithm`, `createdAt`, `retiredAt?`, `replacedBy?`** 🆕 — с первого коммита (CLAUDE.md rule 5). Retired keys retained для decryption старых ciphertext'ов до явного purge.
- FakeAdapter (`FakeAeadCipher`, etc.) — deterministic в тестах, **не безопасный для прода** (`@VisibleForTesting`, исключён из release builds через build-config + Detekt-правило).
- DI wiring (Koin / Hilt) — выбор adapter'а по build variant.
- Property-based crypto tests (kotlin-test + Kotest properties):
  - sign → tamper → verify FAILS.
  - encrypt different content with same K → ciphertext differs (catches nonce reuse).
  - replay protection: same ciphertext twice → second rejected.
  - ECDH symmetry: `DH(a, B) == DH(b, A)`.
- **Cross-platform test vector reuse** 🆕: один JSON-файл векторов в `commonTest` исполняется на каждой платформе (`androidUnitTest`, `jvmTest`, будущий `iosTest`). Любое расхождение байтов между платформами = build failure. Это гарантирует, что admin на Android-телефоне зашифровал → senior на Android TV / iOS прочитал корректно.
- `docs/dev/crypto-review.md` — checklist + industrial reference baseline (вместо friend review).

### Scope: что НЕ входит

- ❌ Конкретные применения: `ConfigCipher` для F-5, envelope encryption для shared content, media blob encryption из 011 — это всё **потребители** модуля, описаны в своих спеках.
- ❌ Sender Keys / MLS / messenger protocol — задел оставлен, реализация позже.
- ❌ **Реальная key rotation logic** — порты есть, real-implementation = stub. Реальная ротация (rotate identity key, re-encrypt history, cloud escrow) — отдельная спека после F-5 / S-3 / S-6.
- ❌ **Cloud key escrow** (backup ключей в Firebase для 2FA admin migration) — порт есть, real-impl = stub. Отдельная спека (см. memory `project_2fa_admin_migration`).
- ❌ Post-quantum primitives — отдельный addendum при необходимости.
- ❌ **Активный iOS CI** — targets декларированы, реальная сборка под iOS откладывается до первой iOS-фичи. Cross-platform тесты гоняются на JVM и AndroidUnitTest (jvmTest имитирует «другую платформу»).

### Dependencies

Должно быть готово **до** F-CRYPTO:
- ~~F-4 (AuthProvider) — стабильный Google UID нужен как input для KeyDerivation (HKDF info field).~~ **СНЯТО 2026-06-17**: deferred-cloud architecture (memory `project_deferred_cloud_architecture`) делает F-4 отложенным до первого cloud action. F-CRYPTO стартует **без** F-4: `KeyDerivation` использует device-local random salt как HKDF info; когда F-4 активируется (в S-5+), Google UID подмешивается **дополнительно** через key rotation (новая identity = новый key derivation). Это совместимо с device-self-sufficiency principle.

### Local Test Path (D-2 mandatory)

- **AeadCipher roundtrip**: encrypt → decrypt → assert equal.
- **AsymmetricCrypto roundtrip**: D1 generates keypair, D2 generates keypair, ECDH между ними → одинаковый shared secret с обеих сторон.
- **Sign / verify**: corrupted message → verify FAILS.
- **Nonce reuse detection**: same key + same nonce + different plaintext → adapter raises error (или property test ловит).
- **Replay protection**: ciphertext sent twice → second rejected by application-level counter.
- **Fake vs real parity**: same input → same output structure (size, headers), хотя значения отличаются.
- **🆕 RFC KAT**: каждый primitive проходит RFC test vectors.
- **🆕 Wycheproof edge cases**: low-order points, point-at-infinity, malleability rejected.
- **🆕 SecureKeyStore wrap/unwrap roundtrip**: положить blob через Keystore-wrap → прочитать → assert equal.
- **🆕 Cross-platform vector reuse**: один и тот же KAT-JSON проходит на `androidUnitTest` И `jvmTest` (имитация будущего iOS) с идентичными байтами.
- **🆕 Key storage wire-format backward-compat read**: положить blob `schemaVersion=1`, прочитать в коде, ожидающем `schemaVersion=2` (или будущую миграцию) — должен корректно отказаться/мигрировать.

### Effort

**Medium-Large** (~2-3 weeks) — увеличено с «Medium 1-2 нед» из-за расширенного scope (iOS-readiness + SecureKeyStore wrap pattern + cross-platform vectors + rotation/escrow stubs).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для F-CRYPTO: core/crypto/ KMP module foundation.

КОНТЕКСТ:
F-5 (ConfigDocument E2E), спека 011 (media blob crypto), будущий мессенджер
владельца, фото-приложение, EOS / Android TV / Google TV — ВСЕ используют одни и
те же криптографические примитивы. Чтобы избежать дублирования и retroactive ACL
extraction, выделяем core/crypto/ KMP module СРАЗУ при первом использовании
криптографии — это применение CLAUDE.md rule 2 + rule 6.

Владелец проекта — solo-dev без сети криптографов; friend crypto review снят
как mandatory, заменён на measurable test-driven validation (RFC KAT + Wycheproof
+ property tests + industrial reference baseline). Платный аудит отложен до
запуска billing.

ЦЕЛЬ:
Создать core/crypto/ (артефакт lib-family-crypto) с domain ports, libsodium
adapter, FakeAdapter, KAT/Wycheproof/property tests, SecureKeyStore wrap pattern,
KeyRotation/KeyEscrow interface-only ports. iOS targets декларированы с day 1.
Модуль полностью отвязан от UI / business / Firebase.

SCOPE ВКЛЮЧАЕТ:
- KMP common module core/crypto/, targets: androidMain + jvmMain + iosX64/iosArm64/iosSimulatorArm64.
- Library extraction policy: внутри launcher-репо до 2-го потребителя; inline TODO на extraction.
- Domain ports:
  * AeadCipher, AsymmetricCrypto, KeyDerivation, RandomSource (criptographic primitives).
  * SecureKeyStore (хранение wrapped Curve25519 keys).
  * KeyRotation (interface-only, real-impl = stub).
  * KeyEscrow (interface-only, real-impl = stub).
- Libsodium real adapter через ionspin/kotlin-multiplatform-libsodium
  (XChaCha20-Poly1305 + X25519 + Ed25519 + HKDF-SHA256).
  Research-фаза обязана проверить актуальность ionspin lib на момент спеки;
  fallback на BouncyCastle (Android) + cinterop (iOS) если ionspin dead.
- SecureKeyStore Android adapter — wrap pattern: AES-256-GCM ключ в Keystore TEE
  обёртывает Curve25519 private key, blob лежит в app sandbox файле.
  iOS adapter — iOS Keychain (kSecAttrAccessibleAfterFirstUnlock).
- FakeAdapter (deterministic, @VisibleForTesting, исключён из release).
- Key storage wire format с schemaVersion, algorithm, createdAt, retiredAt?,
  replacedBy? (CLAUDE.md rule 5).
- DI wiring по build variant.

VALIDATION SET (replaces friend crypto review):
- RFC KAT: RFC 7748 (X25519), RFC 8032 (Ed25519), RFC 8439 (ChaCha20-Poly1305),
  RFC 5869 (HKDF-SHA256).
- Google Project Wycheproof test vectors (edge cases, low-order points, etc.).
- Property tests: sign→tamper→fail, nonce reuse, replay, ECDH symmetry.
- Industrial reference baseline (docs/dev/crypto-review.md): «тот же stack,
  что Signal, WhatsApp, WireGuard, age, Threema, Bitwarden Send».
- Cross-platform vector reuse: один JSON вектор в commonTest исполняется на
  androidUnitTest И jvmTest с идентичными байтами (имитация iOS-совместимости).

SCOPE НЕ ВКЛЮЧАЕТ:
- ConfigCipher (это F-5).
- Реальная key rotation logic (порт есть, real-impl = stub; отдельная спека после F-5/S-3/S-6).
- Cloud key escrow (порт есть, real-impl = stub; отдельная спека).
- Envelope encryption для shared content (потребители — следующие спеки).
- Sender Keys / MLS (задел, реализация позже).
- Post-quantum primitives — addendum.
- Активный iOS CI build (targets декларированы, но iOS build гоняется
  только при первой iOS-фиче).

DEPENDENCIES:
- НЕТ (F-4 deferred per deferred-cloud architecture). KeyDerivation использует
  device-local random salt; когда F-4 активируется в S-5+, Google UID
  подмешивается через rotation.

LOCAL TEST PATH (mandatory per D-2):
- AeadCipher roundtrip.
- AsymmetricCrypto ECDH symmetry.
- Sign / verify tampering detection.
- Nonce reuse detection.
- Replay protection.
- Fake vs real adapter parity.
- RFC KAT для каждого primitive.
- Wycheproof edge cases rejected.
- SecureKeyStore wrap/unwrap roundtrip.
- Cross-platform vector reuse (androidUnitTest + jvmTest идентичные байты).
- Key storage wire-format backward-compat read.

CONSTITUTION GATES:
- Rule 1 (domain isolated), Rule 2 (ACL для libsodium и Keystore), Rule 4 (MVA —
  rotation/escrow ports без real-impl), Rule 5 (wire-format с schemaVersion для
  key storage), Rule 6 (mock-first), Rule 8 (server-roadmap entries для cloud
  key escrow и server-side re-encryption).

ЯВНО ОБРАЩЕНИЕ К:
- Scenario diagrams (плотно): wrap pattern, key rotation triggers (admin меняет
  телефон / переустанавливает app / suspected compromise / senior меняет
  телефон), cross-platform vector reuse, library extraction trigger.
- Use /speckit.scenarios после /speckit.clarify ОБЯЗАТЕЛЬНО — владелец
  верифицирует через диаграммы последовательности.

EFFORT: Medium-Large (~2-3 weeks).

REFERENCE DOCS:
- CLAUDE.md rules 1, 2, 4, 5, 6, 8
- Security Mitigation 3 (property tests) в Части VIII
- memory: project_deferred_cloud_architecture, project_2fa_admin_migration,
  project_unified_app_model
- roadmap §F-CRYPTO Validation strategy (replaces friend crypto review, 2026-06-17)
```

### Notes / gotchas

- **Никакой бизнес-логики в `core/crypto/`**. Если кто-то хочет «давайте сюда добавим ConfigCipher» — refuse, ConfigCipher — потребитель, живёт в F-5 + использует ports отсюда.
- **FakeAdapter явно non-production**. Помечен `@VisibleForTesting` и build-config'ом исключается из release builds. Detekt-правило ловит попадание `Fake*Cipher` в `app/release/`.
- **libsodium binding** — выбор между `libsodium-kmp` от ionspin (рекомендация), BouncyCastle fallback, или собственным cinterop. Решается research-фазой спеки. ionspin покрывает iOS targets «out of box» при условии что library жива.
- ~~**Friend crypto review** — после готовности модуля, до merge'а F-5: показать минимум 2 знакомым с прод-криптографией.~~ **ОТМЕНЕНО 2026-06-17** — заменено на measurable validation set (RFC KAT + Wycheproof + property tests + industrial reference). Платный security audit перенесён в `docs/dev/server-roadmap.md` как milestone перед billing.
- **Library extraction**: НЕ выносим в отдельный репо сейчас (CLAUDE.md rule 4 — MVA). Inline TODO `// TODO(extract-when-2nd-consumer)` в `core/crypto/build.gradle.kts`. Триггер выноса = появление мессенджера / фото-приложения как реального потребителя. При выносе — Apache 2.0 (Kerckhoffs's principle).
- **SecureKeyStore wrap pattern** — индустриальный паттерн, не наш изобретение. Reference: Signal Android `IdentityKeyUtil`, Bitwarden mobile, Threema. Android Keystore не поддерживает Curve25519 нативно, поэтому wrap обязателен.
- **Key storage schema migration**: с первого коммита держим `schemaVersion` + backward-compat read test. Когда формат поменяется (например, добавим `keyAttestation` поле) — миграция уже возможна.

---

## Шаг 3 — F-5: Root Key Hierarchy + ConfigDocument Encryption + Recovery — 🔴 PRODUCTION BLOCKER

> **Order shift (2026-06-15)**: F-5 = **шаг 3 Phase 1**, после F-4 (identity) и F-CRYPTO (модуль). F-5 — **потребитель** `core/crypto/`, не место для inline libsodium.
>
> **Scope redefinition 2026-06-19** (per clarify session): F-5 = **root key hierarchy + первый потребитель (ConfigCipher) + recovery flow**. Полный обновлённый scope — в подразделах ниже. Multi-admin envelope, pairing-recovery, ghost device defence перенесены в [S-2 enhancement notes 2026-06-19](#enhancement-notes-2026-06-19--multi-admin-encrypted-config-sharing-из-f-5-clarify-session). Cross-app broker — в [P-10](#phase-3-mvp-preset-depth-sequential-10-p-спек--тоже-mvp).
>
> **Spec статус** (2026-06-19): spec.md / clarify / scenarios / plan / contracts complete. Next step — `/speckit.tasks`.

### Что строим (mentor explanation)

Сейчас `ConfigDocument` (раскладки плиток, имена контактов, телефоны, labels, темы) лежит в Firestore **в plaintext**. Firebase / Google / любой с доступом к Firestore project видит **всё**. Критическая privacy regression.

F-5 закрывает дыру **двумя сцепленными частями**:

1. **Crypto foundation** — новый KMP module `core/keys/` с **root key hierarchy**: один главный ключ на identity (Google UID), под который шифруются все DEKs (data encryption keys). Хранится в Android Keystore TEE. Это **foundation для всей экосистемы** — F-5 регистрирует первый DEK (`ConfigCipher`), будущие спеки (S-2 X25519 pair-keys, S-5 photo, V-2 messenger, V-3 album) регистрируют свои **под тот же root**.

2. **Recovery flow** — при потере телефона / переустановке: passphrase-encrypted root key хранится в Firestore `users/{uid}/recovery-key`. Пользователь логинится в Google, вводит passphrase (через Android Autofill → Google Password Manager «Suggest strong password»), root key восстанавливается, все DEKs автоматически становятся доступны. **Один passphrase возвращает всё.**

ConfigDocument шифруется через `ConfigCipher.seal()` → SealedConfig улетает в Firestore. Сервер видит opaque bytes. Остальной код не знает про шифрование.

### Зачем именно сейчас

Решение 2026-05-28 (vision review): **production blocker** для cloud release. Решение 2026-06-19 (clarify session): расширено с «encryption only» до «encryption + recovery + root key hierarchy», потому что (a) encryption без recovery = technical debt без user value; (b) root key hierarchy нужна сразу — иначе каждая последующая cloud-спека изобретёт свой key management.

### Источники и резолюции

- User raised 2026-05-28 при обсуждении спеки 014 (Contact Sharing UX).
- Backlog: [TODO-SEC-CRITICAL-024](../dev/project-backlog.md).
- Closes: privacy gap не покрытый спекой 011 (та закрывает только media blobs).
- Extends: спека 008 (config sync) — adapter pattern, не переписывание merge logic.
- **Clarify session 2026-06-19** — 14 решений (см. Clarifications секция в spec.md).
- **Owner decision 2026-06-19** (multi-app-cohabitation.md): экосистема family apps подписывается одним signing key → broker pattern для cross-app sharing.

### Scope: что входит (final 2026-06-19)

**Crypto foundation**:
- Новый KMP module `core/keys/` (~10 файлов в commonMain) поверх F-CRYPTO примитивов.
- Ports: `KeyRegistry`, `RootKeyManager`, `IdentityProof`, `RecoveryKeyVault`, `ConfigCipher`.
- Wire-formats: `SealedConfig`, `RecoveryVaultBlob`, `WrappedDek` — все с `schemaVersion` + `algorithm` от первого коммита.
- Identity isolation: каждый Google UID = независимый namespace в Keystore (alias `rootkey-${uid}`).
- App-layer adapters: `GoogleSignInIdentityProof` (wraps F-4), `FirestoreRecoveryKeyVault`, `NoOpRecoveryKeyVault` для Huawei.

**Encryption layer** (первый потребитель foundation):
- `ConfigCipher.seal(config) / open(sealed)` через `KeyRegistry`-managed `config-cipher-aead-v1` DEK.
- XChaCha20-Poly1305 AEAD, ConfigDocument в Firestore — только opaque bytes.

**Recovery flow**:
- При первом setup: random root key → wrapped passphrase'ом (Argon2id 64MB/3 iter/1 par) → Firestore `users/{uid}/recovery-key`.
- При recovery: Google Sign-In → fetch vault → passphrase prompt с Android Autofill `password` hint → unwrap → restore root key в Keystore → все DEKs автоматически доступны.
- Passphrase UX: «Suggest strong password» chip Google Password Manager / Bitwarden / 1Password — admin не видит пароль глазами; опциональная кнопка «копировать в clipboard» (60s auto-clear на Android 13+).

**Cross-app forward-compat** (per owner decision 2026-06-19):
- Wire-format `RecoveryVaultBlob` и `KeyRegistry` — app-agnostic, global DEK names (`config-cipher-aead-v1`, future `pair-x25519-v1`).
- Format совместим с **broker pattern** (Path A — единый signing key экосистемы) и с **independent cloud access** (Path B). Решение между путями — P-10.

### Scope: что НЕ входит (final)

- ❌ **Multi-admin envelope** (Wrapped Key Envelope, N recipients), pairing-recovery flow, ghost device defence, re-pairing — всё это **переехало в [S-2](#s-2-admin-app--qr-pairing-was-admin-app-preset--remote-pairing)** Phase 2 как enhancement notes 2026-06-19.
- ❌ Cross-app broker / AIDL infrastructure — P-10 territory.
- ❌ Смена passphrase после setup — inline TODO future-spec.
- ❌ Старые ключи при sign-in под другим Google account — accepted decision «остаются изолированно в Keystore» (per owner clarification — Sign-in под другим UID = новая независимая иерархия, старые ключи доступны при возврате на исходный UID).
- ❌ Биометрический unlock root key — future enhancement (FR-003 имеет место под `setUserAuthenticationRequired(true)` flag).
- ❌ Field-level merge / CRDT — moved to S-2 (multi-admin) territory.
- ❌ Group-level encryption (N>2), personal vault, server-side search — out of scope (как раньше).
- ❌ Libsodium binding — в F-CRYPTO, F-5 только consume через ports.

### Dependencies

- **F-4 готов**: `AuthIdentity.stableId` стабильно доступен; `AuthProvider` wrapped через `GoogleSignInIdentityProof`.
- **F-CRYPTO готов**: `AeadCipher`, `AsymmetricCrypto`, `KeyDerivation`, `SecureKeystore`, `CryptoError` доступны в `family.crypto.api`.
- **Не зависит от спеки 007** (pairing) — pairing появляется в S-2 как потребитель foundation, не наоборот.
- **Не зависит от спеки 008** в части merge logic — F-5 шифрует на уровне adapter'а pull/push, merge logic переезжает на client позже в S-2.

### Local Test Path

- JVM unit tests `core/keys/` (KeyRegistry, RootKeyManager, ConfigCipher roundtrip, backward-compat read v1 fixtures).
- Integration через **Firestore Emulator** + два fake-клиентов (старый device, новый device после recovery).
- Smoke на `pixel_5_api_34` через skill `android-emulator` для проверки реального Android Autofill + Google Password Manager UX.
- Cross-version: read SealedConfig v1 → ok; read новый — ok; коэксистенция через `algorithm` field.
- Identity isolation test: UID1 → setup → sign-out → UID2 → новая иерархия → return UID1 → старые данные доступны без recovery.

### Effort

**Medium+** (~2-3 weeks). Module `core/keys/` маленький (~10 файлов), но добавляет один layer над F-CRYPTO + 3 Compose screens (setup / entry / fallback).

### Copy-paste prompt для `/speckit.specify` (final 2026-06-19)

```
Напиши спецификацию для F-5: Root Key Hierarchy + ConfigDocument Encryption + Recovery.

КОНТЕКСТ:
ConfigDocument хранится plaintext в Firestore — privacy regression. F-5 строит
foundation для всей экосистемы: один root key на identity (Google UID) защищает
все будущие DEKs (S-2 X25519 pair-keys, S-5 photo, V-2 messenger). Recovery —
через passphrase в Firestore (Argon2id + Android Autofill).

ЦЕЛЬ:
1. ConfigDocument никогда не лежит plaintext на сервере (первый потребитель).
2. Root key hierarchy готова для добавления DEKs будущими спеками без переделки.
3. Recovery flow возвращает root key (и автоматически все DEKs) после потери устройства.

SCOPE ВКЛЮЧАЕТ:
- Новый KMP module core/keys/ (~10 файлов) поверх F-CRYPTO ports.
- Ports: KeyRegistry, RootKeyManager, IdentityProof, RecoveryKeyVault, ConfigCipher.
- Wire-formats: SealedConfig, RecoveryVaultBlob, WrappedDek (все с schemaVersion + algorithm).
- Identity isolation: per-UID namespace в Android Keystore.
- ConfigCipher через KeyRegistry (config-cipher-aead-v1 DEK).
- Recovery flow: passphrase + Argon2id + Firestore vault + Android Autofill UX.
- Cross-app forward-compat (broker pattern или independent cloud — решение в P-10).
- Huawei / non-GMS: NoOpRecoveryAdapter, local mode.

SCOPE НЕ ВКЛЮЧАЕТ:
- Multi-admin envelope (S-2 territory).
- Pairing flow (spec 007 / S-2).
- Cross-app broker (P-10).
- Смена passphrase (future-spec).
- Биометрический unlock (future enhancement).

DEPENDENCIES:
- F-4 (AuthProvider + AuthIdentity).
- F-CRYPTO (AeadCipher, AsymmetricCrypto, KeyDerivation, SecureKeystore).

REFERENCE DOCS:
- CLAUDE.md rules 1, 2, 4, 5
- spec 016 (F-CRYPTO) ports
- spec 017 (F-4) ports
- docs/product/future/multi-app-cohabitation.md (signing key decision 2026-06-19)
- docs/dev/server-roadmap.md (SRV-RECOVERY-001, SRV-CRYPTO-005, SRV-CRYPTO-006, SRV-CRYPTO-007)
```

### Notes / gotchas (final 2026-06-19)

- **Root key hierarchy** — один root per identity, защищает все DEKs. Future cloud-фичи (S-2, S-5, V-2) **обязаны** регистрировать свои ключи в `KeyRegistry`, а не изобретать свой key management.
- **Passphrase UX через Android Autofill** — `EditText` с `autofillHints="newPassword"` активирует «Suggest strong password» Google Password Manager / Bitwarden / 1Password. Admin не видит пароль глазами. Опциональная кнопка «копировать в clipboard» для тех, кто пользуется внешним менеджером.
- **Sign-out НЕ wipe'ит root key** — Keystore сохраняется при sign-out. Recovery нужен только при физически пустом Keystore (переустановка / factory reset / новое устройство).
- **Identity isolation** — Sign-in под другим Google UID создаёт новую независимую иерархию. Старые UID ключи остаются в Keystore, доступны при возврате.
- **Huawei / non-GMS** — `NoOpRecoveryAdapter` активируется (как F-4 `NoSupportedAuthProvider`). App работает в local mode. Future support через свой сервер (SRV-RECOVERY-001).
- **Wire-format `algorithm: String`** — позволяет сосуществование версий, готовность к future algorithm migration (XChaCha20 → post-quantum через SRV-CRYPTO-007).
- **Никаких libsodium / Firebase в `core/keys/commonMain`** — Detekt fitness function (CLAUDE.md rule 7). Adapter паттерн обязателен.
- **Multi-admin envelope ушёл в S-2** — F-5 single-owner. Если в F-5 PR'е появится Wrapped Key Envelope / N recipients — refuse, направь в S-2.
- **Cross-app sharing decision** — broker pattern (Path A) per owner decision 2026-06-19, требует единого signing key экосистемы. P-10 строит broker поверх F-5 foundation.

---

## Шаг 1 — F-3: Wizard Module + Localization (reordered to FIRST 2026-06-15 v2)

> **Terminology refresh 2026-06-16**: терминология F-3 уточнена в [`docs/product/glossary.md`](glossary.md). Ключевые изменения, актуальные ко всему тексту ниже:
> - «preset» в смысле Simple Launcher / Admin App → **app-family**.
> - «preset» в смысле 2×3 / 3×4 / 4×5 grid → **layout-grid** (параметр шага в wizard'е).
> - «ConfigTemplate» (упоминается ниже в Scope и оригинальном prompt'е) разносится на **две независимые bundled JSON-схемы**: `tile.set` (обезличенные стартовые плитки) и `screen.layout` (каркас экрана: grid + toolbar + табы).
> - Третья bundled-схема — `wizard.manifest` (порядок шагов для app-family).
> - Общий 6-полевой header, forward-compat readers, hard-fail на breaking schemaVersion, локализация через ключи к strings.xml.
> - Отвергнуты: Server-Driven UI, общий envelope с `kind`-discriminator, server-hosted JSON в MVP, отдельные схемы `theme` / `hint.set` / `permission.set` в F-3.
>
> Актуальный prompt для `/speckit.specify` — взять из commit'а, который ввёл glossary (или из [glossary.md §9](glossary.md#9-что-осталось-решить-в-f-3-спеке) для открытых вопросов). Старый prompt ниже сохранён для истории.

> **Order shift 2026-06-15 v2**: F-3 теперь **первый** шаг Phase 1. Wizard работает **локально**, без Google Sign-In, без cloud. Это базис всего: launcher запускается, wizard ведёт через язык / тему / размер шрифта / выбор стартового layout'а / ROLE_HOME permission — всё это local-mode.
>
> Cloud feature setup (F-4 AuthProvider) теперь активируется **в момент первого cloud action** в Phase 2 (S-5 onwards), а не в F-3.
>
> Localization обязательна с дня 1: gate'ит каждую S-спеку через [`checklist-localization-ui`](../../.claude/skills/checklist-localization-ui/SKILL.md) + переводы 10 языков должны делаться **сразу же**, а не «потом» (per явное решение владельца 2026-06-15).

> **Order shift (2026-06-15)**: F-3 = **шаг 4 Phase 1**, после F-4 → F-CRYPTO → F-5.
>
> **Localization обязательна с дня 1** — переводы можно выполнить позже, но **проверять локализуемость UI** надо на каждой спеке через `checklist-localization-ui` skill. Этот skill проверяет готовность UI к разным длинам строк (RU/DE ~30-40% длиннее EN), RTL (AR/HI), plural rules, и требует mock-screenshot для минимум 3 локалей на каждый новый экран.

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

# 🎨 Часть V — Phase 2: MVP Core Vertical Slices

> **Цель Phase 2**: каждая S-спека ships demoable end-to-end feature. После каждой S-спеки можно показать **что-то новое** пользователю.
>
> ⚠️ **Phase 2 + Phase 3 = полный MVP**. Phase 2 даёт демонстрируемый core. Phase 3 (MVP Preset Depth) добавляет полноту обещаний vision (preset architecture, adaptive UX, recovery). Production release — после Phase 3.

> ## 🔁 Порядок выполнения Phase 2 (REORDER 2026-06-15 v3, 9 спек)
>
> S-секции ниже расположены **по историческому номеру**, но **выполняются в новом порядке** — sequential, не параллельно:
>
> | Порядок | Спека | Режим | Что |
> |---------|-------|-------|-----|
> | **1** | S-1 | LOCAL | Simple Launcher Wizard (расширяет F-3). **1 default config**, не named. |
> | **2** | S-3 | LOCAL | Contact Tiles + Calling (ACTION_DIAL, без cloud) |
> | **3** | S-5 | CLOUD | Contact Photos (нужны F-4 + F-5; первый Sign-In trigger) |
> | **4** | **S-8** | CLOUD | **VersionedConfigViewer + Editor** (универсальный diff/version viewer — used by history rollback, multi-admin conflict, AND local→cloud promotion merge). **Named configs появляются здесь** в cloud namespace. |
> | **5** | **S-9** | CLOUD | **Phone Health Monitoring** (battery / online / activity / app crashes / abnormal inactivity) — vision §«снижение тревоги семьи» ← **NEW в v3** |
> | **6** | S-4 | CLOUD | SOS Capability (push admin'у) |
> | **7** | S-2 | CLOUD | Admin App + **QR Pairing** (QR primary per [decision 04](decisions/2026-06-15-deferred-cloud/04-pairing-channel-abstraction.md)). Co-admin = **тоже QR**, не LinkInvite. |
> | **8** | **S-10** | CLOUD | **Subscription Server Timer** — server-side trial timer + entitlement endpoint, без UI billing. Защита от взлома без R8 — server-only validation. ← **NEW в v3** |
> | **9** | S-6 | CLOUD | Account Deletion (GDPR / Play Store gate). Recovery flow откладывается в **P-6 (Phase 3)**. |
> | ~~10~~ | ~~S-7~~ | — | **Caregiver Remote Invite — перемещён в Phase 4 как V-6**. Требует LinkInvite + audit log + role-based access — слишком много для MVP. |
> | ~~11~~ | ~~F-2~~ | — | **Отложен в Phase 4+** (Capability Registry — пока без consumer) |
>
> **Что изменилось vs v2 (предыдущий reorder)**:
> - **Добавлен S-9** Phone Health Monitoring — обязательный по vision «снижение тревоги семьи».
> - **Добавлен S-10** Subscription Server Timer — закрывает защиту от взлома без R8 (server-side validation = достаточно в L0 per [decision 03](decisions/2026-06-15-deferred-cloud/03-billing-cloud-only.md)).
> - **S-7 Caregiver убран** → перемещён в Phase 4 как V-6.
> - **F-2 откложен в Phase 4+**, не в финале Phase 2.
> - **Local mode упрощён до 1 default config** (не named) per [decision 02](decisions/2026-06-15-deferred-cloud/02-config-ownership-per-device.md).
>
> **Architecture context** ([decisions/2026-06-15-deferred-cloud/](decisions/2026-06-15-deferred-cloud/)):
> - **LOCAL** S-спеки (S-1, S-3) работают **без Google Sign-In**. Только локальный конфиг (1 default).
> - **CLOUD** S-спеки (S-5, S-8, S-9, S-4, S-2, S-10, S-6) **требуют Sign-In в момент первого cloud action** (deferred sign-in pattern). Sign-In появляется в S-5 или S-8 — что юзер активирует раньше, не в S-1.
> - **Каждый юзер делает Sign-In своим собственным Google-аккаунтом** — на телефоне, где приложение скачано из Play Store, **тот же аккаунт**, что Play Store login. Нет «дочь Sign-In'ит бабушкин телефон через свой аккаунт» — бабушкин телефон Sign-In'ится под бабушкиным Google, который уже залогинен в её Play Store. Если бабушке физически помогает дочь — она делает Sign-In под **бабушкиным** аккаунтом (например, на новом телефоне). Это применение принципа «наименьшее количество шагов» и [decision 02](decisions/2026-06-15-deferred-cloud/02-config-ownership-per-device.md).
> - **Setup persona** = компетентный взрослый, не cognitively-limited senior. Первая настройка телефона делается человеком, способным пройти wizard и Sign-In.
> - **Каждое устройство самодостаточно**. Конфиг принадлежит локальному Google-аккаунту устройства. Pairing = grant на чтение/запись чужого конфига, **не** передача собственности.

> ## 📦 MVP-ограничение: запуск сторонних apps в Phase 2 (зафиксировано 2026-06-15)
>
> Phase 2 поддерживает запуск сторонних apps по **упрощённой модели**:
>
> - **Источник списка apps** — только `PackageManager` на устройстве admin'а в момент редактирования. То, что у admin'а **не установлено**, в редакторе **не предлагается**.
> - **Параметры запуска** — **не используются**. Тапы плиток запускают app через `Intent(ACTION_MAIN).setPackage(packageName)` — open-only, без deep-link payload. Исключение — спец-провайдеры из spec 005 (`phone`, `sms`, `browser`, `whatsapp`, `telegram`, `viber`, `youtube`), которые работают в S-3 для контактов.
> - **Fallback при отсутствии app на устройстве senior'а** — два: (a) запуск app, если установлен; (b) `market://details?id=<package>` (Play Store), если не установлен. Никакого web-fallback в Phase 2.
> - **Иконки** — `PackageManager.getApplicationIcon(packageName)` локально на senior-устройстве. Если app не установлен → дефолтная «not installed» иконка из ресурсов лаунчера (или кастомная, если admin приложил).
> - **Inventory senior'а** (что у бабушки установлено) — admin **не видит**. Настраивает «вслепую» из своего списка. Если у бабушки app нет — fallback на Play Store при тапе.
> - **Серверный каталог recipes** — **нет**. Никаких deep-link templates на сервере, никаких региональных провайдеров, никакой telemetry о тапах.
>
> Generic app launch с параметрами + inventory sync senior'а — **отложены в Phase 3** как [P-8](#p-8-provider-recipe-catalogue) и [P-9](#p-9-device-inventory-sync) (см. [decision 06](decisions/2026-06-15-deferred-cloud/06-app-launch-mvp-simplification.md)).

> ## 🛡️ Cross-cutting checklists на каждой S-спеке (REORDER 2026-06-15)
>
> На **каждой** S-спеке гоняются (через `procedure-assess-spec-complexity`):
>
> 1. **`checklist-localization-ui`** (новый) — UI готов к разным длинам строк (RU/DE ~30-40% длиннее EN), RTL (AR/HI), plural rules, mock-screenshot для минимум 3 локалей. Дополняет существующий `checklist-localization` (тот — про string tables / format / plurals; новый — про **UI/UX устойчивость**).
> 2. **`checklist-capability-registry-readiness`** (новый) — при объявлении нового action / intent / external-callable surface спека обязана содержать `// TODO(capability-registry): объявить capability declaration для <action_name>`. Skill refuse'ит спеку без TODO. Также **запрещает упоминание конкретных MCP/AI провайдеров** (Google Assistant, App Actions, Gemini, OpenAI, Claude, MCP server) внутри domain или S-спек — всё через будущий abstract `ExposureAdapter`.
>
> **Финальный шаг Phase 2: F-2 Capability Registry Foundation** — собирает все `TODO(capability-registry)` из S-1..S-8 → объявляет capabilities + `ExposureAdapter` interface + FakeAdapter. Реальных AI/MCP adapter'ов нет (отдельные implementation spec'и позже, при необходимости).

---

## S-1: Simple Launcher First-Run + Setup Wizard

> **Order 2026-06-15 v2**: Phase 2 — **шаг 1**. **LOCAL mode** — без Google Sign-In, без cloud, без identity. Wizard ведёт через язык / тему / размер шрифта / выбор preset'а / ROLE_HOME permission. Никакого Sign-In step. Cloud features включаются позже (S-5 onwards).
>
> **Setup persona**: компетентный взрослый, не cognitively-limited senior (per [vision update 2026-06-15](use-cases/01-vision-and-positioning.md)). Wizard может быть и 5, и 9 шагов — UX-сокращения «бабушка устанет» **отменены**.

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

## S-2: Admin App + QR Pairing (was: Admin App Preset + Remote Pairing)

> **Order 2026-06-15 v2**: Phase 2 — **шаг 6** (опущен с шага 2). **CLOUD feature**.
>
> **Primary pairing = QR через камеру** (per [decision 2026-06-15-deferred-cloud/04](decisions/2026-06-15-deferred-cloud/04-pairing-channel-abstraction.md) и [спека 007](../specs/007-pairing-and-firebase-channel/spec.md) User Story 1 P1). Реализация уже в коде.
>
> **Signed invite link через share intent — НЕ primary**. Это additive add'on, появляется в S-7 (Caregiver) через `PairingChannel` abstraction. **Ранее в roadmap были ошибочные утверждения**, что signed invite link — primary, а QR «отвергнут потому что требует физического присутствия» — это **неверно**, исправлено в этом banner'е.
>
> **Унифицированный APK**: и admin'ское устройство, и устройство пожилого — **один и тот же APK** (per memory [`project_unified_app_model`](../../C:/Users/user/.claude/projects/c--work-launcher/memory/project_unified_app_model.md)). Различие — в **runtime preset**, выбираемом в wizard'е. Wizard'ы (`SimpleLauncherWizardManifest`, `AdminWizardManifest` — **это не классы в коде**, это **динамические манифесты внутри preset config'а**) собираются по выбранному preset'у через `ConfigSource` adapter.

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

### Enhancement notes 2026-06-19 — multi-admin encrypted config sharing (из F-5 clarify session)

> **Контекст**: F-5 (spec 018) переопределена на **single-owner encryption + recovery**. Multi-admin envelope (когда second admin читает зашифрованный config бабушки) **переехал сюда в S-2** как часть scope «Admin App + QR Pairing».

**Дополнительные требования в S-2** (поверх существующих):

- **Wrapped Key Envelope pattern**: один ciphertext конфига бабушки + N wrapped CEK (по одному на каждого admin'а пары). При добавлении нового admin'а — wrapped CEK для него добавляется при следующем push'е любого существующего admin'а. **Сервер сам wrapped CEK добавлять не может** (у него нет CEK plaintext).
- **`PairKeyRegistry` port** (где живёт directory pubkey'ев recipients): port должен быть деклаирован в `core/crypto/api/` с минимальной сигнатурой `getRecipientPubKeys(linkId) → List<RecipientPubKey>`. Real adapter поверх Firestore `/links/{linkId}/devices/*` — здесь же.
- **KDF info-string для multi-admin**: derived encryption key содержит `linkId + recipientStableId` для domain separation между admin'ами одной пары (компрометация одного wrapped CEK не открывает чужие).
- **`documentHash`-based optimistic concurrency**: вместо field-level merge (которое невозможно при шифровании на клиенте) — сравнение `expected_hash` от plaintext ConfigDocument. **Conflict resolution = blocking alert** «отменить мои / перезаписать чужие». CRDT/OT — out of scope MVP.
- **Re-pairing flow при смене pubkey'я recipient'а** (бабушка переустановила app):
  - Сервер при обнаружении смены X25519 pubkey'я в `/links/{linkId}/devices/{deviceId}` рассылает push admin'ам с уведомлением `pairing-pending-confirmation`.
  - Admin в UI видит значок «требуется подтверждение pairing'а» у этой пары.
  - Admin может (a) пройти QR заново физически, (b) нажать **«Подтвердить удалённо»** после голосового звонка бабушке — это cryptographic подпись нового pubkey'я admin'ским privkey'ём.
  - Сервер принимает новый pubkey бабушки **только если** есть валидная `recoveryConfirmation` подпись хотя бы одного admin'а пары.
  - Сервер-side rate limit: не более 1 successful re-pairing per `linkId` в 24 часа.
- **Senior fallback во время `pending-confirmation`**: бабушка продолжает работать из app cache (per memory `project_config_cache_model`). Никакого «пустого экрана» — three-tier cache model уже решает.
- **Удалённый recipient (admin удалён из пары)**: следующий push не включает его wrapped CEK. Старые версии остаются доступны удалённому recipient'у — accepted limitation MVP. Forward unsharing (re-encryption старых версий) — future spec.

**Non-goals (явные)**:
- ❌ Защита от malicious authorized admin'а (например, родственник-недоброжелатель формально в семье) — **семейные конфликты не наша территория**, accepted risk.
- ❌ Уведомление других admin'ов когда один admin подтверждает re-pairing — accepted risk per owner decision 2026-06-19.
- ❌ Out-of-band Safety Number / fingerprint comparison на pairing-экранах — отложено в [`server-roadmap.md SRV-CRYPTO-005`](../dev/server-roadmap.md).

**Concept note 2026-06-19**: pairing и recovery — концептуально **одна операция** «authorize new device to existing identity» с разными триггерами. Сейчас разнесены (pairing = spec 007 + S-2, recovery = F-5 / spec 018). Возможно слияние в **unified `DeviceAuthorization` flow** (iMessage / Matrix pattern) в Phase 3 или Phase 4 — research note в [`docs/product/future/`](future/) если потребуется.

### Effort revision

S-2 effort расширяется с **Large (~3 weeks)** до **Large+ (~4 weeks)** из-за multi-admin envelope + re-pairing FRs. Если scope окажется слишком большим — split на **S-2a (basic QR pairing + envelope)** + **S-2b (re-pairing flow + ghost device handling)**.

---

## S-3: Contact Tiles + Handoff Calling

> **Order 2026-06-15 v2**: Phase 2 — **шаг 2**. **LOCAL mode**. Звонки через `ACTION_DIAL` intent (не требует `CALL_PHONE` permission). Контакты вводятся локально, без cloud sync.

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

> **Order 2026-06-15 v2**: Phase 2 — **шаг 5**. **CLOUD feature** — отправка SOS требует push'а на сервер.
>
> **SMS fallback** (предложение владельца 2026-06-15): primary path = FCM push + escalation phone call (`ACTION_CALL`). SMS как **третий fallback** — отложено в [`server-roadmap.md`](../dev/server-roadmap.md) (требует carrier SMS gateway, $$ per message, post-MVP).
>
> Hardware power-button SOS — explicitly OUT в MVP (vision recap).

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

> **Order 2026-06-15 v2**: Phase 2 — **шаг 3**. **CLOUD feature** — фото хранятся на Backblaze B2, требует F-4 (Sign-In) + F-5 (encryption). Это **первая cloud feature** в Phase 2; здесь юзеру предлагается Sign-In с понятным объяснением «чтобы admin мог загрузить фото контактов с другого устройства, нужен Google-аккаунт».

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

> **Order 2026-06-15 v2**: Phase 2 — **шаг 7**. **CLOUD feature**. Применяется только к cloud namespace юзера.
>
> **Семантика per [decision 2026-06-15-deferred-cloud/02](decisions/2026-06-15-deferred-cloud/02-config-ownership-per-device.md)**:
> - Удаляется namespace `/users/{adminUid}/...` (его конфиги, его `access-grants`, его pair'ы).
> - **Конфиги других пользователей не трогаются** — каждый конфиг принадлежит локальному Google-аккаунту своего устройства, не admin'у.
> - `access-grants/{adminUid}` записи у других юзеров — удаляются (cascade): admin теряет доступ к чужим конфигам.
> - Local mode копия (если юзер также работал локально на устройстве, не входя в cloud) — **не трогается**, она вне namespace.

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

## ~~S-7: Caregiver Remote Invite + Role Presets~~ (MOVED to Phase 4 as V-6)

> **🔁 2026-06-15 v3**: S-7 **полностью убран из MVP** и перемещён в Phase 4 как **V-6 Caregiver Remote Invite + LinkInvitePairingChannel**.
>
> Аргумент: caregiver flow требует одновременно:
> - `LinkInvitePairingChannel` adapter (второй после `QrPairingChannel`),
> - Audit log infrastructure (которая теперь = V-7 в Phase 4),
> - Role-based access на сервере + envelope filtering на клиенте.
>
> Это **слишком много** для MVP. Caregiver — non-family path, появляется реже, без него MVP функционален. В MVP всё pairing идёт через QR (включая co-admin: если внук тоже хочет управлять бабушкиным телефоном, он физически делает QR-pair с своего телефона).
>
> Текст спеки ниже **сохранён в roadmap для исторической ссылки** — содержание переедет в V-6 описание в Phase 4 при следующем restructure.

> **Order 2026-06-15 v2 (отменено)**: Phase 2 — шаг 8 (последний). CLOUD feature. Late — за месяц до public release.
>
> **Здесь появляется `LinkInvitePairingChannel`** — второй adapter `PairingChannel` port'а (первый — `QrPairingChannel` из S-2). Caregiver не находится физически рядом, admin генерирует signed invite link, шлёт через share intent.
>
> Audit log (Tier 1 metadata: кто, когда, что) — был запланирован в deprecated F-1. Реализуется здесь как **отдельная micro-spec**, потому что S-7 и S-8 оба требуют audit log.

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

## S-8: VersionedConfigViewer + Layout Editor (was: Family Group Editor + History Rollback)

> **Order 2026-06-15 v2**: Phase 2 — **шаг 4** (поднят с финала). **CLOUD feature**. Универсальный компонент, используется тремя use case'ами.
>
> **Переименование**: спека больше не «Layout Editor + History Rollback», а **VersionedConfigViewer + Layout Editor**. `VersionedConfigViewer` — универсальный компонент с тремя контекстами использования:
>
> 1. **History rollback**: «вернуться к предыдущей версии» (кнопки ← → между версиями, подсветка изменений).
> 2. **Multi-admin conflict resolution**: «другой admin изменил, моя или его версия?» — тот же viewer, два состояния показывает.
> 3. **Local→cloud promotion merge** (per [decision 2026-06-15-deferred-cloud/01](decisions/2026-06-15-deferred-cloud/01-deferred-sign-in.md)): юзер локально настроил → Sign-In'нулся → в облаке уже есть конфиг → viewer показывает оба, юзер выбирает.
>
> **CRDT / OT отвергнуты** (как в F-5) — это automatic field-level merge, сложно. **Visual diff с двумя кнопками — оставлен**: это другая, простая вещь, реалистична в MVP.
>
> Внутреннее устройство: `core/versioned-config/` модуль, типизированный под `ConfigDocument`. Generic `Versioned<T>` НЕ делаем — `Rule of Three` (см. [`decisions/2026-06-15-deferred-cloud/`](decisions/2026-06-15-deferred-cloud/) обсуждение): один потребитель сейчас, абстракция вводится при 3-м.
>
> Inline TODO у `core/versioned-config/` site: `// TODO(rule-of-three): если появится 2-й потребитель history — копировать модуль; на 3-м — обобщать в Versioned<T>`.

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

## S-9: Phone Health Monitoring 🆕 (added 2026-06-15 v3)

> **Order 2026-06-15 v3**: Phase 2 — **шаг 5**. **CLOUD feature**. **Обязательная для MVP** — закрывает центральное обещание vision §«снижение тревоги семьи».

### Что строим (mentor explanation)

Это **active reassurance** — admin (на своём телефоне) видит периодический health snapshot бабушкиного телефона: уровень батареи, заряжается ли, online status (есть ли интернет), last activity (когда бабушка последний раз касалась экрана), последние app crashes (косвенно — что-то идёт не так).

Не нужно ждать SOS, чтобы знать, что всё в порядке. Открыл admin app — видишь зелёную «всё ок» / жёлтый «нет связи 6 часов» / красный «SOS».

### Зачем именно сейчас

Vision §«Phone Health Monitoring» явно перечисляет: battery level, charging status, connectivity, internet availability, device offline state, app crashes, abnormal inactivity, SOS events, emergency triggers. **Без этого продукт не выполняет обещание «снижение тревоги»** — admin вынужден звонить бабушке «ты как там, жива?» вместо тихого наблюдения.

S-9 — **обязательно в MVP**, не post-MVP. Зафиксировано владельцем 2026-06-15.

### Источники и резолюции

- [`use-cases/01-vision-and-positioning.md` §Phone Health Monitoring](use-cases/01-vision-and-positioning.md).
- Vision §«снижение тревоги родственников».
- [CLAUDE.md rule 10](../../CLAUDE.md) (notification minimization) — push **только** при significant events (offline > N hours, low battery + no charger, critical permission revoked). Остальное — in-app indicator.

### Scope: что входит

- **HealthSnapshot** wire-format с `schemaVersion: 1`:
  ```kotlin
  data class HealthSnapshot(
      val schemaVersion: Int,
      val deviceId: DeviceId,
      val capturedAt: Instant,
      val batteryPct: Int,
      val isCharging: Boolean,
      val isOnline: Boolean,
      val lastUserActivity: Instant,
      val installedAppVersion: String,
      val recentCrashes: Int,   // counter за последние 24h, через Android Vitals
      val permissionsHealth: PermissionsHealthMap
  )
  ```
- Периодическая отправка snapshot'а на сервер: каждые **15 минут** (foreground) / **1 час** (background через WorkManager).
- Admin app: device list → tap → детальный экран с health metrics + history graph.
- Push admin'у **только** при significant events:
  - Battery < 15% AND not charging для 30+ минут.
  - Offline > 4 часа.
  - Critical permission revoked (ROLE_HOME, POST_NOTIFICATIONS).
  - Abnormal inactivity (no user activity 12+ часов днём).
- In-app indicator при остальных событиях.

### Scope: что НЕ входит

- ❌ Geolocation tracking (отдельно, может быть в V-5 wearable).
- ❌ Health vitals (heart rate, etc.) — V-5.
- ❌ Audio surveillance.
- ❌ Screen content snooping.

### Dependencies

- F-4 (AuthProvider — нужен Sign-In для cloud sync).
- F-5 (E2E encryption — health snapshot тоже PII).
- S-8 (VersionedConfigViewer — не нужен прямо, но shared infra).

### Local Test Path (D-2 mandatory)

- **FakeHealthCollector** генерирует synthetic snapshots для тестов.
- **Periodic upload test**: snapshot отправляется каждые 15 минут foreground.
- **Threshold trigger test**: battery → 14% → push; battery → 16% → no push.
- **Offline detection**: WorkManager retry с exponential backoff.
- **Permission revoke detection**: revoke ROLE_HOME → next snapshot fires push.

### Effort

**Medium** (~2 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для S-9: Phone Health Monitoring.

КОНТЕКСТ:
Vision §Phone Health Monitoring требует, чтобы admin (на своём телефоне) мог
видеть периодический health snapshot бабушкиного устройства: battery, online
status, last activity, recent crashes, permissions health. Без этого продукт
не выполняет обещание «снижение тревоги семьи».

ЦЕЛЬ:
Создать HealthCollector + HealthRepository + HealthSnapshot wire-format с
периодической отправкой на сервер и push при significant events (battery low,
offline > N hours, permission revoked, abnormal inactivity).

SCOPE ВКЛЮЧАЕТ:
- HealthCollector port + Android implementation (BatteryManager, ConnectivityManager,
  UsageStatsManager для last activity, Android Vitals для crash count).
- HealthSnapshot wire-format с schemaVersion (per rule 5).
- Periodic upload (15min foreground / 1h background WorkManager).
- Threshold-based push notifications (per CLAUDE.md rule 10 notification minimization).
- Admin app: device list health column + detailed health screen.

SCOPE НЕ ВКЛЮЧАЕТ:
- Geolocation (V-5 wearable).
- Health vitals (V-5).
- Audio / screen surveillance.

CONSTITUTION GATES:
- Rule 1, 2, 5, 10 (notification minimization).

EFFORT: Medium (~2 weeks).

REFERENCE DOCS:
- use-cases/01-vision-and-positioning.md §Phone Health Monitoring
- CLAUDE.md rules 1, 2, 5, 10
```

### Notes / gotchas

- **Push hygiene strict**. Каждый push должен пройти [CLAUDE.md rule 10](../../CLAUDE.md) тест: actionable + time-sensitive + user-relevant. «Battery 50%» — нет. «Battery 14% no charger 30 минут» — да.
- **Не surveillance**. Health monitoring ≠ tracking. Мы знаем, что устройство онлайн и батарея не села. Мы **не знаем**, что бабушка ела, кому звонила, где гуляла.
- **OEM-specific snags**. Xiaomi MIUI / Huawei EMUI агрессивно убивают background WorkManager. Нужны autostart hints в wizard (как для FCM push в S-4).

---

## S-10: Subscription Server Timer 🆕 (added 2026-06-15 v3)

> **Order 2026-06-15 v3**: Phase 2 — **шаг 8**. **CLOUD feature** — server-side только, **без UI billing**. Implements [`decision 03`](decisions/2026-06-15-deferred-cloud/03-billing-cloud-only.md) и [`decision 03 §Уровни усиления L0`](decisions/2026-06-15-deferred-cloud/03-billing-cloud-only.md).

### Что строим (mentor explanation)

Сервер-side таймер trial-периода и entitlement endpoint в Cloudflare Worker.

При первом Sign-In Worker создаёт запись `{userId, trialStartedAt: <now>, subscriptionState: "trial"}`. При каждом cloud-action (pair, sync, push, photo upload) клиент дёргает endpoint `/api/entitlement` → Worker проверяет:
- `(now - trialStartedAt) < trial_duration` → выдаёт entitlement JWT, action разрешён.
- `now > trial + grace_period` AND no active subscription → отказывает.

Клиент **не знает** trial duration / start date — это server-side. Защита от взлома **на уровне L0** (server-only): можно патчить APK сколько угодно, без entitlement JWT cloud features не работают.

**UI billing** (форма оплаты, выбор тарифа, Google Play Billing integration) — **не в S-10**, отдельная V-спека post-MVP. В MVP **trial бесплатный достаточно длинный** (предлагаю 6 месяцев), чтобы первые юзеры успели попользоваться без оплаты.

### Зачем именно сейчас

- Закрывает [CLAUDE.md rule 8](../../CLAUDE.md) — server-roadmap entry для subscription state.
- Защищает cloud features от взлома **без введения R8 obfuscation** в MVP (R8 = L1 уровень, активируется позже если понадобится).
- Готовит инфру для будущего billing: endpoints `/api/entitlement` + `/api/subscription/start|cancel` готовы; billing UI добавляется как additive change.

### Источники и резолюции

- [`decisions/2026-06-15-deferred-cloud/03-billing-cloud-only.md`](decisions/2026-06-15-deferred-cloud/03-billing-cloud-only.md) §Уровни усиления L0.
- [`docs/dev/server-roadmap.md`](../dev/server-roadmap.md).

### Scope: что входит

- Firestore document: `/users/{uid}/subscription/state`:
  ```
  {
    schemaVersion: 1,
    trialStartedAt: <timestamp>,
    trialDurationDays: 180,    // 6 months default, configurable per-rollout
    subscriptionState: "trial" | "active" | "expired" | "cancelled",
    expiresAt: <timestamp>?,
    lastValidatedAt: <timestamp>
  }
  ```
- Worker endpoint `POST /api/entitlement` — выдаёт short-lived JWT (≤1 час), верифицирует subscription state.
- Worker endpoint `GET /api/subscription/state` — для admin UI «когда истекает trial».
- Worker endpoints `POST /api/subscription/start|cancel` — **stub'ы** на MVP (возвращают `not_implemented`), будут заполнены отдельной V-спекой когда введём billing UI.
- Client: `EntitlementCache` — caches JWT с `expiresAt`, рефрешит при истечении или 5 минут до.
- Client: `SubscriptionStatePresenter` — показывает в Settings «осталось N дней trial» / «подписка активна» / «истекла, перейти в local mode».
- Graceful offline: при недоступности Worker'а cached JWT honored для **7 дней grace period**, потом cloud-features pause.

### Scope: что НЕ входит

- ❌ UI billing form (выбор тарифа / оплата).
- ❌ Google Play Billing integration.
- ❌ Stripe / другие провайдеры.
- ❌ Refunds / promo codes.
- ❌ Family / multi-device pricing tiers.

Всё перечисленное — отдельная V-спека Phase 4 (например, V-Billing).

### Dependencies

- F-4 (AuthProvider — нужен Google UID для namespace).
- Cloudflare Worker уже задеплоен (из спеки 007).

### Local Test Path (D-2 mandatory)

- **EntitlementCache test**: cache valid → don't fetch. Cache expired → fetch.
- **Trial expiry simulation**: set `trialStartedAt = now - 200 days`, trial_duration = 180 → expect `expired`.
- **Grace period offline**: simulate Worker unavailable → cached JWT honored 7 days → after that cloud features deny.
- **Worker integration test через Miniflare**: end-to-end trial lifecycle.
- **Tamper test**: client modifies local `subscriptionState` flag → Worker returns server-side state, client cannot override.

### Effort

**Small** (~1 week). Server-only, никаких UI экранов кроме одного Settings panel.

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для S-10: Subscription Server Timer.

КОНТЕКСТ:
Per decision 2026-06-15-deferred-cloud/03 — billing cloud-only, защита от
взлома на L0 (server-validated entitlement, никакого client-side flag).
В MVP — только server timer + entitlement endpoint, **без UI billing**.
UI billing (Google Play, оплата, тарифы) — отдельная V-спека Phase 4.

ЦЕЛЬ:
Worker endpoint /api/entitlement выпускает short-lived JWT для активных
trial/subscription. Client кеширует JWT, обновляет при истечении.
Settings показывает «осталось N дней trial». Graceful offline через 7-day
grace period с cached JWT.

SCOPE ВКЛЮЧАЕТ:
- Firestore /users/{uid}/subscription/state document.
- Worker endpoints: /api/entitlement (выдача JWT), /api/subscription/state (read-only).
- EntitlementCache (client-side, JWT-store с auto-refresh).
- SubscriptionStatePresenter (Settings UI, read-only display).
- Trial duration: 180 days default.
- Grace period offline: 7 days.

SCOPE НЕ ВКЛЮЧАЕТ:
- UI billing form / payment.
- Google Play Billing / Stripe.
- Refunds, promo codes, family pricing.

CONSTITUTION GATES:
- Rule 1, 2, 8 (server-roadmap).
- decision 2026-06-15-deferred-cloud/03 §Уровни усиления L0.

EFFORT: Small (~1 week).

REFERENCE DOCS:
- docs/product/decisions/2026-06-15-deferred-cloud/03-billing-cloud-only.md
- docs/dev/server-roadmap.md
- CLAUDE.md rules 1, 2, 8
```

### Notes / gotchas

- **No client-side license flag**. Если в PR появится `localUserState.isPremium = true` — refuse, направить через `EntitlementCache` + Worker.
- **JWT must be short-lived**. ≤1 час. Никаких «year-long valid tokens».
- **Grace period не обходится**. Если client offline 8 дней → cloud features pause, не silent allow.
- **Trial duration configurable per rollout**. 180 дней — default, но можно менять без code changes (Firestore document).

---

## ~~Финальный шаг Phase 2~~ — F-2: Capability Registry Foundation (ОТЛОЖЕН в Phase 4+)

> **Order shift (2026-06-15 v3)**: F-2 **больше не в Phase 2 и не в Phase 3 (MVP)**. Перемещён в **Phase 4 "Product Extensions"** — активируется когда появится реальный AI/MCP/voice consumer, не раньше.
>
> **v2 (отменено)**: F-2 — последний шаг Phase 2, после S-1..S-8.
>
> До F-2 точки сшивки продолжают работать через [`checklist-capability-registry-readiness`](../../.claude/skills/checklist-capability-registry-readiness/SKILL.md) skill (требует `// TODO(capability-registry)` для каждого нового action) и индекс [`docs/dev/capability-registry-pending.md`](../dev/capability-registry-pending.md).
>
> **Зачем перенесли**: Capability Registry без consumer'а — преждевременная абстракция ([CLAUDE.md rule 4](../../CLAUDE.md)). Реальные actions из S-1..S-8 накапливаются в pending index. Когда появится конкретный AI/voice integration target (Google Assistant, MCP server, Gemini Nano), F-2 будет первым шагом этой работы.
>
> **Order shift v1 (отменён)**: F-2 — **последний** шаг Phase 2, после S-1..S-8.
>
> До тех пор в каждой S-спеке проставляются `// TODO(capability-registry): объявить capability declaration для <action_name>` через `checklist-capability-registry-readiness` skill, и индекс actions пополняется в [`docs/dev/capability-registry-pending.md`](../dev/capability-registry-pending.md). F-2 собирает все TODO → объявляет capabilities + `ExposureAdapter` interface + FakeAdapter. Реальных MCP/AI adapter'ов всё ещё нет (отдельные implementation spec'и позже, при необходимости).
>
> **Зачем отложили**: Capability Registry — это инфра для будущего AI-доступа, не нужен для production demo. CLAUDE.md rule 4 (Minimum Viable Architecture) — точки сшивки готовим (TODO + checklist guard + pending-index), абстракцию вводим только когда есть конкретный consumer (то есть когда все S-спеки готовы и можно одним проходом cобрать все capabilities).

### Что строим (mentor explanation)

Сейчас наш product выражает actions через wire format спеки 005 (Action Architecture v2). Это уже частично «intent-based» — действия описываются структурой `providerId + params`, dispatched через `ProviderRegistry`. **Это хорошая основа**, но недостаточная для AI-readiness.

Мы строим **Capability Registry** — единый каталог того, что app умеет делать, с богатой метадатой: human-readable description, voice phrases, parameter schemas, confirmation requirements, authorization scope. Этот каталог — **порт в domain layer**. Domain не знает о существовании AI-агентов.

Снаружи каталога — **Exposure Adapter interface**, через который каталог можно экспонировать конкретному AI-consumer'у: Google Assistant App Actions, MCP server, iOS Shortcuts, etc. **В MVP ни один реальный adapter не реализуется** — только interface + FakeAdapter для тестов.

После F-2 каждая S-спека сверху может **объявлять capability declarations** для своих actions. Через год, когда понадобится первый реальный adapter (например, App Actions), пишется implementation spec без переписывания core.

### Зачем именно здесь (в конце Phase 2, а не в начале Phase 1)

AI становится default-интерфейсом в 2026. Если архитектура не подготовлена — через 1-2 года придётся переписывать. **AI-ready, not AI-built** — наша архитектурная поза (per D-17 / D-21).

Но **сам Registry-каталог** не нужен до того, как у нас есть actions, которые он каталогизирует. Делать его в Phase 1 — значит угадывать, какие capabilities понадобятся, а потом дополнять. Делать в конце Phase 2 — значит сразу собрать **полный** каталог из готовых, реальных actions S-1..S-8.

До F-2 точки сшивки держатся через `checklist-capability-registry-readiness` skill (refuse'ит спеки без `// TODO(capability-registry)`) и `docs/dev/capability-registry-pending.md` (индекс).

### Источники и резолюции

- [`use-cases/12-ai-integration.md`](use-cases/12-ai-integration.md) — full deep-dive в три слоя AI exposure.
- **Closes D-17** (только AI-ready architecture без provider implementations).
- **Closes D-18, D-19** как DEFERRED (privacy posture / MCP location — решаются при implementation spec).
- **Closes D-20** (создать `checklist-ai-readiness` skill).
- **Closes D-21** (AI affordance как обязательная ось roadmap-обсуждений).
- Reorder 2026-06-15 — отложить F-2 в конец Phase 2 (CLAUDE.md rule 4).

### Scope: что входит

- `core/capability/` модуль.
- `CapabilityRegistry` port (interface).
- `Capability` data class с полями: `intentName`, `humanReadableDescription`, `voicePhrases`, `params`, `idempotent`, `requiresConfirmation`, `auth`.
- `ExposureAdapter` interface (port для будущих adapters).
- `FakeExposureAdapter` для тестов.
- Capability declarations для всех actions, собранных из `docs/dev/capability-registry-pending.md` (заполненного через checklist в S-1..S-8). Минимально ожидаемые:
  - `call_contact` / `message_contact` / `video_call_contact`
  - `open_app` / `navigate_to`
  - `trigger_emergency`
  - … плюс всё, что S-спеки добавят в pending-index.
- Wire format с `schemaVersion` для capability declarations.
- Roundtrip tests + cross-device tests.

### Scope: что НЕ входит

- ❌ App Actions adapter implementation (отдельная implementation spec позже).
- ❌ MCP server adapter implementation (отдельная implementation spec позже).
- ❌ Gemini Nano integration (отдельная implementation spec позже).
- ❌ Voice command parsing (это Layer 2 OS-level work).

### Dependencies

Должно быть готово **до** F-2:
- S-1 .. S-8 — все S-спеки merged, чтобы pending-index был полон.
- `docs/dev/capability-registry-pending.md` заполнен через `checklist-capability-registry-readiness` skill на протяжении всего Phase 2.

### Local Test Path (D-2 mandatory)

- **Register fake capabilities** → invoke через FakeExposureAdapter → verify dispatch routing.
- **Capability declarations validation**: каждый action из pending-index имеет capability declaration (fitness function в test).
- **Roundtrip serialization test** wire format с schemaVersion.
- **Cross-device test**: capability serialized на одной версии → deserialized на другой → schema-compatible.

### Effort

**Medium** (~2 weeks).

### Copy-paste prompt для `/speckit.specify`

```
Напиши спецификацию для F-2: Capability Registry Foundation.

КОНТЕКСТ:
Продукт строится по принципу "AI-ready, not AI-built". В MVP не реализуем реальных AI-adapter'ов (App Actions, MCP, Gemini Nano), но строим архитектурный layer, на котором они подключатся как additive add'ы.

F-2 идёт ПОСЛЕДНИМ шагом Phase 2 — собирает все actions, накопленные через
checklist-capability-registry-readiness в S-1..S-8 (индекс — в
docs/dev/capability-registry-pending.md).

Решение зафиксировано в use-cases/12-ai-integration.md (Capability Registry +
Exposure Adapters pattern, расширение CLAUDE.md rule 2 ACL).

ЦЕЛЬ:
Создать core/capability/ module с CapabilityRegistry port + ExposureAdapter
interface + FakeAdapter + capability declarations для всех actions из
pending-index.

SCOPE ВКЛЮЧАЕТ:
- core/capability/ KMP common module.
- CapabilityRegistry port (interface): list(), describe(), invoke().
- Capability data class: intentName, description, voicePhrases, params with types, idempotent flag, requiresConfirmation, auth scope.
- ExposureAdapter interface (port под Layer 2 / Layer 3 / Layer 1 future adapters).
- FakeExposureAdapter для тестов (rule 6 mock-first).
- Capability declarations для всех actions из docs/dev/capability-registry-pending.md.
- Wire format с schemaVersion (per rule 5).

SCOPE НЕ ВКЛЮЧАЕТ:
- App Actions adapter (отдельная implementation spec).
- MCP server adapter (отдельная implementation spec).
- Gemini Nano integration (отдельная implementation spec).
- Voice parsing.

LOCAL TEST PATH (mandatory per D-2):
- Register fake capabilities, invoke через FakeExposureAdapter, verify dispatch.
- Fitness function: каждый action из pending-index имеет capability declaration.
- Roundtrip serialization test wire format.
- Cross-device schema compat test.

ADDITIONAL DELIVERABLES (cross-cutting):
- ADR-008 — AI affordance posture documented ("AI-ready, not AI-built").
- skill `checklist-ai-readiness` создан в .claude/skills/.
- skill `checklist-capability-registry-readiness` (создан 2026-06-15) — после F-2 переходит из "refuse without TODO" в "refuse without capability declaration".
- spec-template.md получает обязательную секцию "AI Affordance".

CONSTITUTION GATES:
- Rule 1, 2, 4, 5, 6.

EFFORT: Medium (~2 weeks).

REFERENCE DOCS:
- use-cases/12-ai-integration.md (full architecture)
- docs/dev/capability-registry-pending.md (pending actions index)
- specs/005 — capability declarations добавляются к existing actions
- CLAUDE.md rules 1, 2, 4, 5, 6
```

### Notes / gotchas

- **Никакой реальной AI-логики**. Этот spec — чистая инфра. Если кто-то хочет «давайте просто добавим Google Assistant сразу» — refuse, направь в отдельную implementation spec позже.
- **Capability declarations — single source of truth**. Любой будущий adapter транслирует из Registry, не из спеки 005 напрямую.
- **Voice phrases — на нескольких языках**. Capability должен поддерживать localized voicePhrases (Map<Locale, List<String>>), потому что App Actions BII matching language-specific.
- **После F-2 — обновить `checklist-capability-registry-readiness` skill**: вместо refuse'а без TODO теперь refuse без полноценного capability declaration в Registry.

---

# 🚀 Часть VI — Phase 3: MVP Preset Depth (P-1 .. P-10) — **ТОЖЕ MVP**

> **Phase 3 — это вторая половина MVP**. Phase 2 даёт демонстрируемый core, Phase 3 — полноту обещаний vision (preset architecture, adaptive UX, recovery). Production release — после Phase 3.
>
> **Сжато**: то, что Phase 2 строит на упрощённом preset'е (`schemaVersion: 1`), Phase 3 расширяет до полноценной preset architecture (`schemaVersion: 2`) — без поломки Phase 2 кода (per [decision 05](decisions/2026-06-15-deferred-cloud/05-preset-wire-format-versioning.md)).
>
> **Sequential** — как и Phase 2, не параллелим. По одному шагу за другим.

## P-1: Preset Schema v2 + Wizard Engine

Bump preset wire format с `schemaVersion: 1` → `2`. Backward-compat: Phase 3 reader умеет читать `v1` (lift to `v2` shape), Phase 2 reader на `v2` получает только `platformAgnostic` секцию через server-side downgrade.

Wizard engine эволюционирует: preset теперь декларирует **mandatory steps** + **optional steps**. Wizard runtime читает manifest из текущего preset'а, проходит mandatory steps, ставит **persistent reminder** для optional (см. P-7).

**Scope ВКЛЮЧАЕТ**: `ConfigDocumentV2` wire format + lift `v1→v2` + `WizardManifest` schema + roundtrip tests + cross-version tests.
**Scope НЕ ВКЛЮЧАЕТ**: конкретные Android intents (P-2), adaptive presets (P-4), authoring UI (P-3).
**Dependencies**: Phase 2 завершена.
**Effort**: Medium (~2 weeks).

## P-2: Android Deep Integration Steps

Реализация **reusable Android-specific wizard steps**, нужных для полноценного «безопасного пространства пожилого» (vision §Simple Launcher):
- **BlockNotificationDrawerStep** — отключить notification drawer (через `Settings.Global` или Accessibility Service).
- **DisableHorizontalSwipeStep** — заблокировать swipe между screens (через accessibility overlay).
- **HideSettingsBehind7TapStep** — настроить, чтобы Settings были доступны только через 7-tap из main flow.
- **DisableLockscreenWidgetsStep**, **RestrictAppListVisibilityStep**, etc.

Каждый step — `WizardStep` implementation, который вызывает Android system intent или Accessibility Service.

**Scope ВКЛЮЧАЕТ**: набор reusable Android Deep Integration steps + permissions wizard + Settings deep-links + state reconciliation после OS update.
**Scope НЕ ВКЛЮЧАЕТ**: iOS / TV / Wear варианты — это V-1 / V-4 / V-5 в Phase 4.
**Dependencies**: P-1 (schemaVersion 2 готов).
**Effort**: Large (~3 weeks).

## P-3: Preset Authoring + Sharing

UI для admin'а: создание / экспорт / импорт preset'ов.
- Создать новый preset на основе существующего (поверх P-5 copy mechanism).
- Сохранить под именем (5 named configs limit per cloud namespace).
- Экспорт preset'а как `.json` файл / share через Android share intent.
- Импорт `.json` файла или принять share intent от другого юзера.
- **ConfigSource adapter list**: `BundledConfigSource` (из F-3) + `ImportFromFileConfigSource` + `ShareIntentConfigSource`.

**Marketplace** (curated catalog) — **не в P-3**, отдельная Phase 5 L-2.

**Scope ВКЛЮЧАЕТ**: authoring UI + export / import flows + ConfigSource adapters (file / share intent) + переписать спеку 014 на `v2` schema (закрывает divergence note из Phase 2 intro).
**Scope НЕ ВКЛЮЧАЕТ**: marketplace, curation, ratings.
**Dependencies**: P-1, P-2.
**Effort**: Large (~2-3 weeks).

## P-4: Adaptive UX Presets

Готовые preset'ы для разных групп users с ограниченными возможностями. Каждый — JSON config с `adaptiveProfile` полем:

| Preset | adaptiveProfile | Что делает |
|--------|-----------------|------------|
| Стандартный | `default` | Обычный senior-safe (то, что есть в Phase 2 baseline) |
| Тремор лёгкий | `tremor-mild` | Tap targets ≥72dp, debounce 500ms |
| Тремор сильный | `tremor-severe` | Long-press 1 сек для активации, все swipes отключены |
| Нарушение восприятия | `perception-impaired` | Dwell-to-activate, минимум элементов на экране |
| Слабовидящий | `vision-impaired` | Экстра контраст, голосовое озвучивание плиток |

Каждый — **отдельный bundled preset**. Admin выбирает в P-3 authoring UI.

**Scope ВКЛЮЧАЕТ**: 5 bundled adaptive presets + runtime `AdaptiveTouchBehavior` (debounce / long-press / dwell) + accessibility audit.
**Scope НЕ ВКЛЮЧАЕТ**: machine learning для автоопределения тремора (post-MVP).
**Dependencies**: P-1, P-2.
**Effort**: Medium (~2 weeks).

## P-5: Config Copy Between Own Devices

Admin имеет несколько устройств в своём namespace (телефон / планшет / TV в зале / TV в кухне). Operation **copy + modify + apply**:
1. Выбрать source config (например, `tv-living-room`).
2. Clone → новый `configId`, имя `tv-living-room (copy)`.
3. Юзер правит (например, убирает 2 контакта).
4. Переименовывает в `tv-kitchen`.
5. Apply to device — push в namespace, активируется на `kitchen-tv` device.

**Cross-platform copy** (телефон → TV) — копируется только `platformAgnostic` секция, `platformSpecific.android` НЕ копируется в `platformSpecific.androidTv`. Юзер допроходит TV-specific wizard для нового config'а.

**Scope ВКЛЮЧАЕТ**: clone operation + UI + cross-platform handling + tests.
**Scope НЕ ВКЛЮЧАЕТ**: marketplace sharing (это P-3 export или Phase 5 L-2).
**Dependencies**: P-1.
**Effort**: Small (~1 week).

## P-6: Account Recovery Flow + 2FA escrow

Сейчас (Phase 2 baseline): юзер потерял телефон → Google Sign-In на новом устройстве → namespace восстанавливается → pair с другими устройствами нужно **rescan QR**.

P-6 добавляет **pair-key recovery** через **2FA escrow** в Firestore: при pairing короткий код отсылается на старое связанное устройство, юзер вводит на новом → pair восстанавливается без физической встречи.

**Scope ВКЛЮЧАЕТ**: 2FA escrow document в Firestore + recovery wizard в new device flow + tests + clearly-stated UX flow в спеке S-6.
**Scope НЕ ВКЛЮЧАЕТ**: social recovery («друг помогает восстановить», deprecated D-25 OWD-4).
**Dependencies**: F-4, S-6.
**Effort**: Medium (~2 weeks).

## P-7: Optional Step Reminder System

В preset (`schemaVersion: 2`) есть `optionalSteps`. Эти шаги юзер может пропустить, но они **должны напоминать о себе**:
- В Settings — badge на «Setup» entry: «У вас 5 не настроенных шагов».
- В Settings → Setup — список с прогрессом.
- При тапе на каждый — соответствующий wizard step запускается.
- **Frequency cap** — badge всегда виден (он не push), но **не надоедает** — не блокирует основной flow.

**Scope ВКЛЮЧАЕТ**: `OptionalStepTracker` + badge UI + список + accessibility audit.
**Scope НЕ ВКЛЮЧАЕТ**: push reminders (отрицание [CLAUDE.md rule 10](../../CLAUDE.md) — это не actionable + не time-sensitive).
**Dependencies**: P-1 (optionalSteps в schema), P-2 (есть optional Android steps).
**Effort**: Small (~1 week).

## P-8: Provider Recipe Catalogue

> **NEW 2026-06-15**. Источник: [decision 06](decisions/2026-06-15-deferred-cloud/06-app-launch-mvp-simplification.md). Снимает MVP-ограничение Phase 2: «запуск apps только без параметров».

Серверный публичный каталог **launch recipes** — структурированных описаний «как открыть конкретный app с параметрами через deep-link». Расширяет ограниченный набор провайдеров из spec 005 (8 встроенных: phone, sms, browser, whatsapp, telegram, viber, youtube, app) до **сотен / тысяч** recipes для региональных apps (Uber / Bolt / 99 / Ola / Kakao T / Yandex Taxi / etc.).

### Что строим

- **Recipe wire format** (`schemaVersion: 1` с первого commit'а):
  ```
  recipe {
    schemaVersion: 1
    recipeId: "uber"
    packageName: "com.ubercabs"
    parameterTypes: [{name, type, maxLen}, ...]
    deepLinkTemplate: "uber://?action=setPickup&pickup[address]={pickup_address}"
    webFallback: "https://m.uber.com/?pickup={pickup_address}"
    playStoreUrl: "market://details?id=com.ubercabs"
    availableRegions: ["US", "BR", "IN", ...]
    lastUpdatedAt: ISO timestamp
  }
  ```
- **Серверный endpoint** для pull каталога (`GET /recipes?region=<region>&since=<ts>`).
  - Pull **всего региона целиком** (privacy: сервер не знает, какой recipe юзер тапнул).
  - ETag / `since` для incremental updates.
  - На MVP — Cloudflare Worker + KV / R2 (per [rule 8](../../CLAUDE.md) server-roadmap).
- **Локальный кэш recipes** на устройствах admin + senior.
  - TTL — раз в сутки + при запуске admin-режима.
  - В config бабушки лежит **только** `{recipeId, parameters}`, **не копия** recipe — иначе recipe протухнет при изменении Uber'ом схемы.
- **Resolver в момент тапа**: `config.tile.recipeId` → достать recipe из кэша → подставить parameters в `deepLinkTemplate` → запустить Intent. При failure: webFallback → playStoreUrl (fallback chain spec 005).
- **Admin UI**: в редакторе плиток показать пересечение `recipe-каталог × admin-installed apps`. Опция «показать все recipes» (expert mode).
- **Curator workflow** — отдельная **серверная** sub-спека (CMS / парсинг / CI fitness function «recipe deep-link реально открывается на эмуляторе»).

### Privacy boundary

| Что | Кто знает |
|---|---|
| Регион устройства | Сервер (через query param) |
| Что бабушка тапнула | **Никто** (config зашифрован, нет telemetry) |
| Какие apps интересны юзеру | Никто (pull всего региона, не по одному recipe) |

Если позже добавится server-side фильтрация по конкретному recipe — это **дополнительный** privacy compromise, явное решение через decision-документ.

### Scope: что НЕ входит

- ❌ User-generated recipes (admin создаёт свой recipe для приватного app) — это L-фаза marketplace-like.
- ❌ Inventory sync senior'а — это P-9.
- ❌ Telemetry о тапах — никогда.
- ❌ Marketplace / community / ratings — L-2.

### Dependencies

- Phase 2 завершена (S-3 уже использует встроенные провайдеры из spec 005).
- P-1 (preset `schemaVersion: 2` — место хранения `recipeId` в tile).

### Effort

Medium (~2-3 weeks клиент + отдельная серверная спека на curator).

## P-9: Device Inventory Sync

> **NEW 2026-06-15**. Источник: [decision 06](decisions/2026-06-15-deferred-cloud/06-app-launch-mvp-simplification.md). Парный к P-8.

Senior-устройство периодически собирает список **установленных apps** локально через `PackageManager`, шифрует тем же ключом, что и config (envelope encryption из F-5), и пушит на сервер. Admin при редактировании читает этот список и **видит, что у бабушки реально установлено**.

### Что строим

- **Inventory wire format** (`schemaVersion: 1` с первого commit'а):
  ```
  inventory {
    schemaVersion: 1
    deviceId: "..."
    lastUpdatedAt: ISO timestamp
    apps: [{packageName: "com.ubercabs", label: "Uber", versionCode: 123}, ...]
  }
  ```
  Soft limit ~500 apps. Без иконок (admin рендерит через свой `PackageManager` или дефолт).
- **Сбор на senior-устройстве**:
  - Broadcast receiver на `PACKAGE_ADDED` / `PACKAGE_REMOVED` → triggers rebuild.
  - Sanity refresh раз в сутки (на случай если receiver проспал из-за low-memory kill).
  - `PackageManager.getInstalledApplications(MATCH_ALL)` локально → фильтр пользовательских apps.
- **Шифрование и push**:
  - Тем же envelope encryption ключом, что и config (F-5). Сервер видит blob, не структуру.
  - Push через тот же канал sync, что и config (spec 008), в обратную сторону (senior → cloud → admin).
- **Admin UI в редакторе плиток**:
  - Пересечение `recipe-каталог × inventory senior'а` — «вот что у бабушки есть, для которого есть recipe».
  - Warning при сохранении: «inventory обновилась, app X у senior больше нет — плитка будет вести в Play Store. Продолжить?».
  - Показать «inventory last updated: 3 hours ago» (не real-time).

### Privacy boundary

| Что | Кто знает |
|---|---|
| Список packageNames у бабушки | Admin (расшифровывает локально) |
| Тот же список — сервер | Не знает (зашифровано) |
| Те же данные — analytics / crash reports | **Никогда** не отправляются в открытом виде |

### Scope: что НЕ входит

- ❌ Apps, которые `PackageManager` не показывает из-за Android 11+ package visibility — admin видит warning «inventory may be incomplete». Не пытаемся обойти через `QUERY_ALL_PACKAGES` если Play Store запретит.
- ❌ Real-time push «бабушка только что установила X» — не нужно, fallback покрывает.
- ❌ Inventory с других устройств одного admin'а — admin видит **свой** `PackageManager` напрямую.
- ❌ Cross-platform inventory (TV / wearable) — Phase 4.

### Dependencies

- F-4 (identity), F-5 (envelope encryption) — Phase 1.
- S-8 (config sync механизм — переиспользуем для inventory).
- P-8 (recipe-каталог) — без него inventory бесполезна для редактора, можно строить параллельно.

### Effort

Small (~1-1.5 weeks).

## P-10: Multi-app Cohabitation + Chain-of-trust Recovery

> **Moved here 2026-06-18 (evening update)** from `docs/product/future/multi-app-cohabitation.md` (research notes; ранее лежали в `specs/017-multi-app-cohabitation/README.md`, переехали в `docs/product/future/` и старый каталог удалён 2026-06-18 после того, как номер 017 переназначен на F-4 AuthProvider). Решение владельца — это не Phase 1 dependency, а часть полной preset/recovery полноты, поэтому переехала в Phase 3. При создании полной спеки получит свободный порядковый номер на момент `/speckit.specify`.

### Что строим (mentor explanation)

Когда у нас будет 3 приложения одной семьи (**launcher + messenger + photo album**) на одном телефоне, владелец хочет, чтобы пользователь **один раз** подтвердил «это новое устройство — реально я», и после этого **все 3 приложения** автоматически восстановили доступ. Не три раза по очереди.

Каждое app — отдельный Android-package с отдельным sandbox'ом и своим экземпляром `:core:crypto`. Android **не разрешает** одному app читать файлы другого по умолчанию. P-10 строит **chain-of-trust**: launcher подтверждает messenger, messenger подтверждает album, и эта цепочка переживает factory reset / смену устройства.

UX-цель: **один клик восстанавливает доступ ко всем same-family app на устройстве**.

### Зачем именно сейчас (а не в Phase 4 рядом с V-2 messenger'ом)

К моменту запуска messenger'а (V-2 в Phase 4) chain-of-trust **должна быть готова** — иначе UX deteriorates обратно к «логиниться в каждое приложение отдельно». P-10 идёт **в конце Phase 3**, незадолго до Phase 4 messenger'а, так что сразу при выходе messenger MVP cohabitation уже работает. Building раньше — преждевременная абстракция (нет 2-го потребителя, нечего подтверждать).

### Источники и резолюции

- Research notes + mentor-context: [`docs/product/future/multi-app-cohabitation.md`](future/multi-app-cohabitation.md).
- Three технических варианта B / C / гибрид — см. [`docs/dev/crypto-review.md`](../dev/crypto-review.md) §A2.
- Inline TODOs: `TODO(pre-release-audit): multi-app cohabitation` в `app/src/main/java/com/launcher/app/di/F016CryptoModule.kt`, `core/crypto/src/iosMain/kotlin/family/crypto/SecureKeyStore.ios.kt`, `core/crypto/build.gradle.kts`. Все grep-discoverable: `grep -r "TODO(pre-release-audit):" core/ app/ docs/ specs/`.

### Scope: что входит

- Выбор технического варианта: **B (ContentProvider + custom permission)** / **C (Server-mediated handoff)** / **B+C гибрид**.
- Wire format для encrypted-pending-handoff (если C / гибрид) — `schemaVersion: 1` с первого commit'а (rule 5).
- Один **`ChainOfTrustVerifier`** port в `core/crypto/` (rule 2 ACL). Adapter'ы — Android (ContentProvider) и iOS (App Groups + shared Keychain).
- Standalone-install fallback: если установлен только messenger (без launcher) — messenger делает свой recovery flow независимо.
- Reverse trust: messenger может подтверждать launcher (а не только наоборот) — для случая, когда пользователь восстанавливает messenger первым.
- Trust revocation: «удалить app X из доверенного семейства» (на случай compromise / give-away).

### Scope: что НЕ входит

- ❌ Сам messenger MVP — V-2.
- ❌ Сам photo album MVP — V-3.
- ❌ Cross-platform handoff Android↔iOS — если выбран вариант C, MVP только Android↔Android. iOS↔Android отдельным шагом.
- ❌ Key rotation cascade — отдельный спек.

### Dependencies

- F-CRYPTO готов (KeyStore, KeyBlob, primitives) — ✅.
- F-4, F-5 (identity + envelope encryption) — Phase 1 / Phase 2.
- P-6 (Account Recovery + 2FA escrow) — recovery flow для **single app** уже работает к моменту P-10; P-10 расширяет его до **multi-app**.
- Хотя бы один **второй потребитель `core/crypto/`** existing или scheduled — иначе нет цепочки. К моменту P-10 messenger MVP scheduled (Phase 4 V-2), значит триггер сработал.

### Effort

**Medium** (~2-3 weeks). Самая большая неизвестная — выбор B / C / гибрид; до того, как решено, нельзя оценить точно.

### Open research questions (для spec-фазы)

См. [`docs/product/future/multi-app-cohabitation.md`](future/multi-app-cohabitation.md) §Research questions: ContentProvider permission UX на Android 15/16, iOS App Groups ограничения, wire format для handoff'а, standalone install, reverse trust, key rotation cascade, trust revocation.

---

> ✅ **PRODUCTION RELEASE** — конец полного MVP (Phase 2 + Phase 3).

---

# 🌅 Часть VII — Phase 4: Product Extensions (V-1 .. V-7 + F-2)

> Post-MVP. Каждая V-спека — большая отдельная вертикаль, расширяющая продукт в новый сегмент.
>
> **Не fixed sequence** — порядок выбирается после MVP по сигналам рынка (какой сегмент даёт самый сильный pull).

## V-1: iOS Admin Preset

iOS-specific implementations поверх KMP foundation. Compose Multiplatform iosMain. iOS-specific OAuth (Apple Sign-In вдобавок к Google), deep links, share intents. App Store submission flow.

**Закрывает**: D-14 (iOS admin в post-MVP).
**Effort**: Very large (~3-4 months).

## V-2: Elderly-Friendly Messenger (Jitsi-based separate app)

Отдельное приложение, на Jitsi Meet. SSO с launcher через F-4 AuthProvider (один Google login для обоих apps). Universal Preset Architecture применяется и к messenger тоже:
- Elderly preset — extremely simplified UX, large buttons.
- Adult preset — full features.

Group call invites. Reuse `core/crypto/` для encrypted media.

**Закрывает**: D-23 implementation (MVP — handoff; post-MVP — own messenger).
**Effort**: Very large (~4-6 months).

## V-3: Full Shared Family Album

Расширение S-5 (которая делает photos для контактов) в полноценный семейный альбом: видео + аудио + memories. Chunked upload. Album UI (timeline, search, captions, anniversaries).

**Закрывает**: D-26 full implementation.
**Effort**: Large (~3 months).

## V-4: Android TV Preset

TV-specific UI (Leanback или custom Compose for TV). Voice navigation через Android TV system. Big tiles. Family call quick-join. Ambient family presence mode. Pairing через `RemoteCodePairingChannel` (TV показывает 6-значный код, юзер вводит на телефоне).

**Закрывает**: D-24 (TV в post-MVP).
**Effort**: Large (~2-3 months).

## V-5: Wearable Health Monitoring

BLE pairing с smart watches через `BluetoothPairingChannel`. Heart rate, steps, fall detection. Alert escalation. Privacy boundaries.

**Implements**: FUTURE-SPEC-001 start.
**Effort**: Large (~3 months).

## V-6: Caregiver Remote Invite + LinkInvitePairingChannel 🆕 (moved from Phase 2)

> **Moved from S-7 in Phase 2** (2026-06-15 v3). Caregiver flow требует слишком много для MVP: `LinkInvitePairingChannel` (второй adapter `PairingChannel`), audit log infrastructure (V-7), role-based access на сервере + envelope filtering на клиенте.

Caregiver = сиделка / соц.работник, к которой admin может **дать ограниченный доступ** через signed invite link (share intent). Caregiver видит SOS, может позвонить, но **не видит** family album. TTL membership.

**Закрывает**: D-15 (caregiver/clinic integration foundation).
**Effort**: Large (~3-4 months — с учётом всей инфры).

## V-7: Audit Log Infrastructure 🆕

> **Полезная фича для прозрачности** — кто из родственников / caregiver'ов что делал в семейном пространстве. Снижает тревогу «кто-то меняет настройки, я не знаю кто». В MVP **сознательно отложено** — упрощённый «Recent Activity» можно получить поверх version history из S-8 как inline TODO в той спеке.

Полноценный audit log с Tier 1 (public metadata: actor, timestamp, action_type) + Tier 2 (private payload encrypted to actor only). Используется и в V-6 (caregiver actions), и в admin app для прозрачности.

**Effort**: Medium (~2 weeks). Зависит от V-6 (нужны caregiver actions для логирования).

## F-2: Capability Registry Foundation (moved from Phase 2 → Phase 4)

> **Moved from Phase 2 в Phase 4** (2026-06-15 v3). Capability Registry без consumer'а — преждевременная абстракция. Активируется, когда появится первый AI/MCP/voice integration target (Google Assistant App Actions, MCP server, Gemini Nano).

Существующий [`checklist-capability-registry-readiness`](../../.claude/skills/checklist-capability-registry-readiness/SKILL.md) skill + индекс [`docs/dev/capability-registry-pending.md`](../dev/capability-registry-pending.md) накапливают actions через Phase 2 + Phase 3 + начало Phase 4. F-2 собирает всё → `CapabilityRegistry` port + `ExposureAdapter` interface + FakeAdapter.

**Effort**: Medium (~2 weeks).

---

# 🛤️ Часть VII.5 — Phase 5: Long-term Parking Lot (L-x)

> Не fixed roadmap, направления для consideration. Идеи на годы вперёд. Каждая активируется, если появится сильный сигнал из рынка / pull от пользователей.

- **L-1**: Clinic / partner B2B integration (per D-15 architectural readiness, V-6 caregiver foundation).
- **L-2**: Marketplace для config templates (per CLAUDE.md rule 9 — shareable templates curated).
- **L-3**: AI provider implementations (после F-2):
  - L-3a: App Actions adapter (Layer 2).
  - L-3b: MCP server adapter (Layer 3) — Cloudflare Worker extends.
  - L-3c: Gemini Nano integration (Layer 1) — image description, smart suggestions.
- **L-4**: Self-hosted Sentry migration (per server-roadmap).
- **L-5**: Backup / disaster recovery (D-Be-4).
- **L-6**: Social recovery (re-open D-25 OWD-4 — only if «потерял так потерял» окажется bad PR).
- **L-7**: Multi-device per user beyond F-4 (FUTURE-SPEC-009).
- **L-8**: Key rotation / forward secrecy (FUTURE-SPEC-010).
- **L-9**: Family group encryption migration к Signal-style (если outgrow envelope encryption).
- **L-10**: Wearable monitoring full (FUTURE-SPEC-001 expanded).
- **L-11**: Security sensors integration (FUTURE-SPEC-002).
- **L-12**: Closed messengers (LINE / WeChat / KakaoTalk — FUTURE-SPEC-003).
- **L-13**: Shared admin contact book (FUTURE-SPEC-004).
- **L-14**: Family Activity Challenges (PARK-001 если решим build).
- **L-15**: Tamper-resistance escalation L1+L2+L3 (per [decision 03 §Уровни усиления](decisions/2026-06-15-deferred-cloud/03-billing-cloud-only.md)) — активируется если статистика покажет abuse через modified APKs.

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
- **2026-06-18** — F-CRYPTO (spec 016) merged в main (PR #20). Multi-app cohabitation placeholder (`specs/017-multi-app-cohabitation/`) перенесён из текущего скоупа в **Phase 3 как P-10** (решение владельца) — это не Phase 1 dependency, а часть полной preset/recovery полноты перед Phase 4 messenger'ом. Phase 3 теперь P-1..P-10 (было P-1..P-9). F-4 scope усилен: `AuthProvider` port объявлен **provider-agnostic** (Google — лишь один из adapter'ов; Phone / Email-Password / Apple / SSO добавляются additively без переписывания port'а — rule 2 ACL применён к auth-провайдерам).
- **2026-06-18 (вечер)** — Spec number 017 reassigned to F-4 AuthProvider + Google Sign-In. Multi-app cohabitation research notes перенесены из `specs/017-multi-app-cohabitation/README.md` в `docs/product/future/multi-app-cohabitation.md` (P-10 в Phase 3 — реальный спек напишется через ~5 месяцев). Social recovery (ADR-008) больше не привязан к номеру 017 — получит свободный номер на момент `/speckit.specify`. Inline TODO в `core/crypto/` и `app/` обновлены.
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

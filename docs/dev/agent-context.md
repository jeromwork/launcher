# Архитектура лаунчера — справочник для AI-агента

> **Аудитория**: свежий AI-агент без предыдущего контекста.
> **Цель**: понять принятые решения, их обоснование, структуру и опасные места.
> **Стек**: Kotlin + KMP + Compose (Android). Rust для крипто-primitives через UniFFI FFI. TypeScript Worker на Cloudflare → будущие Go-микросервисы.

---

## 0. Инвариантные правила архитектуры

Эти правила нарушать запрещено — при любом новом коде проверяй каждый из них.

**Rule 1 — Domain isolated from infrastructure.**
Domain-код не импортирует SDK, HTTP-клиенты, Android-системные типы (Intent, Context, URI). Каждая внешняя зависимость скрыта за *port* (interface, объявленный в domain) и реализована *adapter*'ом (отдельный модуль с SDK). Domain разговаривает только с ports.

**Rule 2 — ACL за каждым внешним SDK.**
Если завтра вендор исчезнет — поменяется только один adapter-модуль. Если придётся менять больше — wrapping неправильный.

**Rule 3 — One-way doors vs two-way doors.**
Необратимые решения (KDF-параметры, wire format, MLS-выбор, identifier-модель) принимаются медленно с явным exit ramp. Reversible решения — быстро.

**Rule 5 — Wire-format versioning.**
Всё что уходит с устройства или сохраняется между версиями app'а — wire format. Правила целиком в [`docs/architecture/wire-format.md`](../architecture/wire-format.md) (единственный источник; здесь не дублируем).

**Rule 6 — Mock-first.**
Каждый port имеет fake-adapter для тестов. Real-adapter добавляется после того, как port-shape доказал себя на fake.

**Rule 9 — Shareability-readiness.**
Любой non-identity конфиг (layout, preset, theme, wizard manifest) проектируется как portable JSON-артефакт с `schemaVersion` и `ConfigSource` adapter pattern с первого коммита — даже если sharing UI ещё не строится.

**Rule 12 — Zero-trust на каждом endpoint.**
JWT verify + rate-limit + input validation + observability + explicit failure codes. Authenticated ≠ trusted.

**Rule 13 — Zero-knowledge сервер.**
Сервер видит только зашифрованные конверты и непрозрачные UUID. Не видит контент, не управляет membership, не знает event type, не хранит ACL-граф.

---

## 1. Пресеты, Профили, Компоненты

> ⚠️ Может дублировать [`../architecture/ecs.md`](../architecture/ecs.md) (SoT для ECS/preset-модели) — отдельный dedup-проход pending. При расхождении `ecs.md` wins; вызывай skill `ecs`.

### Словарь — три уровня абстракции

**Component** — единица настраиваемого поведения. sealed hierarchy (8 типов в MVP):

| Component | Ключевые параметры |
|-----------|-------------------|
| `AppTile` | packageName, labelKey, iconKey, pinProtected |
| `FontSize` | scale |
| `Sos` | shareLocation, autoAnswer |
| `Toolbar` | items, layoutKey |
| `LauncherRole` | (нет параметров — HOME role) |
| `Theme` | paletteSeedHex, typographyScale, shapeStyle, darkMode |
| `Language` | locale ("system" = следовать ОС) |
| `StatusBarPolicy` | скрыть строку статуса (kiosk) |

**Pool** — JSON-каталог всех известных `ComponentDeclaration`'ов. Растёт только аддитивно (нельзя удалять/переименовывать существующие entries — это breaking change для старых preset'ов).

**Preset** — portable JSON-документ (`schemaVersion=2`), который *выбирает из Pool* нужные компоненты и задаёт:
- `wizardFlow` — какие шаги показывать при первом запуске, порядок, режим (Interactive / AutoApply / InitialDefault).
- `settingsMap` — что показывать в Settings.
- `activeComponents` — финальный список активированных компонентов.

Preset **не содержит PII** (rule 9). Можно делиться файлом. Три bundled preset'а:
- `simple-launcher` — primary user (крупные плитки, минимум выбора).
- `launcher` — средний.
- `workspace` — admin.

**Profile** — device-local live-state, зашифрован в DataStore (`schemaVersion=2`). Содержит реальное состояние применённых компонентов (`ComponentStatus`: Applied / Skipped / Failed), snapshot до wizard'а (для undo), историческую карту для переключения preset'ов.

### Как они связаны

```
Pool (каталог declarations, растёт аддитивно)
  └─ Preset (JSON выбирает из Pool, задаёт порядок + params)
       └─ Profile (device-local copy, редактируется пользователем)
            └─ ReconcileEngine (единый движок apply/check/rollback)
```

### Единый движок — ReconcileEngine

**Ключевое решение**: один `ReconcileEngine` работает в четырёх режимах:
- `Wizard` — первый запуск.
- `BootCheck` — каждый старт (через WorkManager).
- `Single` — применить один компонент из Settings.
- `RemotePush` — admin push → apply на managed-устройстве.

**Зачем один движок**: гарантия что wizard, settings и remote push применяют одинаковую логику. Раньше было несколько разных движков — расходились.

**Provider pattern**: каждый тип Component имеет свой `Provider` с методами `check()` + `apply()`. Registry направляет по типу + платформе + вендору. Добавить новый Component subtype = добавить Provider, не трогая engine.

### Wizard flow (pre-flight важен)

`WizardEngine.computePending(manifest)` — перед показом wizard'а проверяет **фактическое** состояние Android (реально ли нужны permissions и т.д.). Показывает только действительно нужные шаги. Без этого wizard показывал шаги которые уже выполнены.

### Опасные места — пресеты

⚠️ **Pool deletion** — нельзя удалять pool-entry на который ссылается существующий preset, иначе preset сломается при загрузке. Pool = public API.

⚠️ **Platform-specific компоненты** — `AppTile` с конкретным packageName не работает на TV или iOS (другие app-пакеты). При clone config между платформами — strip platform-specific entries, оставлять только `platformAgnostic`.

⚠️ **Preset ≠ Profile** — не путать. Profile это живое состояние устройства. Если применять preset поверх существующего profile — надо явно решить conflict resolution (текущий подход: preset побеждает, но хранится preWizardSnapshot для undo).

⚠️ **Vendor variants** — один и тот же `CheckSpec` / `ApplySpec` работает по-разному на Xiaomi (MIUI), Samsung (One UI), Huawei (EMUI). Provider Registry должен знать вендора. Тестировать только на одном вендоре — скрытый баг.

---

## 2–6. Крипто, ключи, recovery, pairing, config-sync

> **Архитектура этих зон вынесена в single source of truth** (SoT-консолидация, TASK-145). Здесь **не дублируем** — читай:
>
> | Тема | Файл |
> |---|---|
> | Умбрелла + карта зон + routing | [`../architecture/crypto.md`](../architecture/crypto.md) |
> | Примитивы (libsodium, AEAD/ECDH/KDF, validation) | [`../architecture/crypto-primitives.md`](../architecture/crypto-primitives.md) |
> | Root key, HKDF, envelope (ConfigCipher2), recovery vault, passphrase/Argon2id | [`../architecture/crypto-key-hierarchy.md`](../architecture/crypto-key-hierarchy.md) |
> | Pairing (Noise_XX/`snow`), identity↔key binding, revoke | [`../architecture/crypto-pairing.md`](../architecture/crypto-pairing.md) |
> | MLS / KeyPackage (0 кода — читать Decision-задачи TASK-124 / TASK-104) | [`../architecture/crypto.md`](../architecture/crypto.md) зона map |
> | Versioning wire-форматов | [`../architecture/wire-format.md`](../architecture/wire-format.md) |
> | Config-sync + envelope + FCM trigger (server-сторона) | [`../architecture/server.md`](../architecture/server.md) + [`../architecture/crypto-key-hierarchy.md`](../architecture/crypto-key-hierarchy.md) |
>
> Для любого вопроса «как устроена крипта» вызывай skill `crypto` — он маршрутизирует в нужный файл.

### Операционные опасные места MVP (gotchas, не архитектура — держим здесь как чек-лист)

Это известные MVP-допущения и подводные камни, каждое отслеживается своей задачей; архитектурное «как правильно» — в SoT-файлах выше.

- ⚠️ **KeyPackage exhaustion / epoch drift** — пустой pool → last-resort (без FS); устройство после долгого офлайна пропускает epoch'и (MLS требует sequential). Стратегия «вернулся после 30 дней» — не решена. Owner: TASK-124.
- ⚠️ **UniFFI version lockstep** — uniffi-rs + uniffi-bindgen + runtime одной версии; расхождение = рантайм-ошибки. CI fitness. Panic-контракт — skill [`crypto-ffi-panic-check`](../../.claude/skills/crypto-ffi-panic-check/SKILL.md).
- ⚠️ **`stableId`/`identity_id` в путях сервера** — сейчас видим серверу (нарушение rule 13, opaque identifiers). Целевое: `hash(root_public)` / opaque token. MVP-допущение. Owner: TASK-106 / TASK-108.
- ⚠️ **Нет root-key rotation в MVP** — компрометация = полный reset. Rotation = TASK-41 (Phase 5), exit ramp документирован.
- ⚠️ **Huawei без GMS** — нет Sign-In → только device-local, factory reset теряет всё. Physical-device dependent.
- ⚠️ **eventType читается сервером для роутинга** (config-updated push) — нарушение zero-knowledge (rule 13). Целевое: encrypt eventType + opaque routing token. Owner: TASK-108 / server-log.
- ⚠️ **Config-sync**: last-write-wins (потеря при concurrent edit), client-side retention 10 версий (не восстанавливается на новом устройстве), `RecipientResolver` требует атомарного re-encrypt при добавлении устройства.

---

## 7. Серверная архитектура

> ⚠️ Может дублировать [`../architecture/server.md`](../architecture/server.md) (SoT для серверной топологии/zero-trust/zero-knowledge) — отдельный dedup-проход pending. При расхождении `server.md` wins.

### Current stack

- **Cloudflare Worker** (`push-worker/src/index.ts`) — всё сейчас в одном Worker'е.
- **Firestore** — `/links/{linkId}` (owner + members), `/identity-links/{uid}` (stableId mapping).
- **Backblaze B2** (S3-compatible) — encrypted blob storage (фото, медиа), через Worker-прокси.
- **Cloudflare KV** — idempotency cache, FCM token directory.
- **Cloudflare Durable Object** — atomic counters (anti-brute-force, запланировано).

### Будущая структура — один Worker = один будущий Go-микросервис

```
workers/
├── push/             → generic push foundation
├── identity/         → JWT issuance (свои токены, не Firebase)
├── keypackage-store/ → MLS prekey pool
├── message-fanout/   → group messaging
├── device-lock/      → remote app lock
├── config-sync/      → config history server-side
└── _shared/
    └── auth-jwt/     → JWT verification module (уже извлечен)
```

Не bundling несвязанные сервисы — каждый `/workers/<name>/` = future Go-microservice. Это preserves migration seams.

### Zero-trust posture (Rule 12)

Каждый endpoint обязан иметь:
1. **JWT verify**: RS256, kid, iss, aud, exp (+60s skew), iat, sub non-empty.
2. **Rate-limit**: per-identity / per-IP + конкретные числа + декларированный storage tier (KV vs DO).
3. **Input validation**: zod schema, 1MB limit (per-endpoint override).
4. **Observability**: structured JSON logs + counters (rate-limit-hit, auth-failure, malformed).
5. **Failure codes**: 400/401/403/404/429+Retry-After/502/503.
6. **Idempotency** для state-modifying: Idempotency-Key header + KV dedup.

**Authenticated ≠ trusted.** Рогатый девайс с валидным JWT всё равно может атаковать. Сервер защищает себя независимо от JWT.

### Zero-knowledge posture (Rule 13)

**Три принципа — все обязательны:**

**1. Sealed Server Default (Tier 0)** — сервер видит: opaque nsId + opaque key + ciphertext + Ed25519 подпись. Authorization = проверка подписи против публичного ключа namespace. Без ACL-таблиц.

**2. Client Coordinates, Server Stores** — бизнес-логика на клиенте:
- Membership management → keyring blob в namespace (клиент знает кто в группе, сервер — нет).
- History rotation → клиент LIST+DELETE, сервер — только cron-TTL по time.
- Schema migration → клиент делает lazy migration при чтении.
- Push routing → зашифрованный payload, сервер forwards opaque по routing token.

**3. Opaque Identifiers** — все ID видимые серверу = UUID v4, NOT Google `sub`, NOT email. URL: `/namespaces/{nsId}/blobs/{key}`. Маппинг `userUid → nsId` хранится только на клиенте.

**Tiered model:**
- **Tier 0** — sealed storage (default для всего).
- **Tier 1** — минимальный directory (pubkey discovery, FCM token routing). Только для async patterns. Сервер видит opaque id → pubkey mapping, без relationships.
- **Tier 2** — server-required logic (anti-brute-force vault counter, subscription entitlement timer). Только когда client-side bypass возможен (Clear App Data / factory reset / root).

### Текущие нарушения zero-knowledge (MVP допущения)

- **stableId = Google UUID** в backup пути → сервер может связать Google аккаунт с recovery blob. Mitigation: HMAC pseudonym, нужна миграция.
- **eventType в push payload** читается Worker'ом → сервер знает semantics события. Mitigation: зашифровать + opaque routing.
- **Firestore ACL** (`identity-links/{uid}`) → сервер знает mapping uid↔linkId. Mitigation: убрать при переходе на own-server.

---

## 8. Карта ключевых файлов

### Architecture docs
- [`docs/architecture/crypto.md`](../architecture/crypto.md) — AI TL;DR крипты (читать первым, 60 строк)
- [`docs/architecture/server.md`](../architecture/server.md) — server endpoints, zero-knowledge posture
- [`docs/dev/server-roadmap.md`](server-roadmap.md) — exit ramps для всех MVP компромиссов
- [`docs/dev/key-hierarchy.md`](key-hierarchy.md) — Root Key Hierarchy технический справочник

### Core domain (commonMain — платформонезависимый)
- `core/src/commonMain/kotlin/com/launcher/preset/model/` — Component, Preset, Profile, Pool
- `core/src/commonMain/kotlin/com/launcher/preset/port/` — Provider, ProfileStore, PoolSource, PresetSource
- `core/src/commonMain/kotlin/com/launcher/preset/engine/` — ReconcileEngine, PresetValidator
- `core/src/commonMain/kotlin/domain/crypto/` — CryptoPort, GroupPort, KeyPackagePort, IdentityVaultPort
- `core/keys/src/commonMain/kotlin/family/keys/api/` — KeyRegistry, RemoteStorage, PublicKeyDirectory, ConfigSaver

### Android adapters (androidMain — платформозависимый)
- `core/src/androidMain/kotlin/com/launcher/adapters/crypto/` — Android Keystore, PairingCryptoCoordinator
- `app/src/main/java/com/launcher/app/preset/task120/provider/` — все Component Providers
- `app/src/main/java/com/launcher/app/preset/task120/facade/` — Android facades (ACL per rule 2)
- `app/src/main/java/com/launcher/app/preset/task120/adapter/` — DataStoreProfileStore, BundledSources
- `core/crypto/src/androidMain/kotlin/cryptokit/adapters/openmls/` — Kotlin→UniFFI→Rust

### Server
- `push-worker/src/index.ts` — все текущие endpoints
- `workers/_shared/auth-jwt/` — JWT verification (извлечён)

---

## 9. Сводка опасных мест (читать перед любыми изменениями)

| Зона | Опасность | Правило |
|------|-----------|---------|
| Pool entries | Нельзя удалять/переименовывать — сломаются preset'ы | Rule 5 |
| Platform-specific компоненты | Не копировать между платформами без strip | Rule 5 |
| UniFFI version | lockstep pin — расхождение = runtime errors | Rule 2 |
| Rust panic через FFI | abort процесса без catcher'а | — |
| KeyPackage pool | При исчерпании — last-resort key (нет FS) | — |
| MLS epoch drift | Офлайн девайс не может прыгнуть через эпохи | — |
| Argon2id params change | Schema version bump обязателен | Rule 5 |
| stableId = Google UUID | Server видит identity → нарушение rule 13 | Rule 13 |
| eventType в push | Server видит семантику событий → нарушение rule 13 | Rule 13 |
| claimToken TTL | QR истекает — нужен явный UI с таймером | — |
| Install Referrer 90d | Fallback QR path нужен для поздних установок | — |
| Config conflict resolution | Last-write-wins — при concurrent edit данные теряются | — |
| RecipientResolver | Re-encrypt при добавлении нового устройства | — |
| Anti-brute-force | Client-side счётчик обходится через Clear App Data | Rule 13 Tier 2 |
| RootKey rotation | Нет в MVP — при компрометации = полный reset | Rule 3 |

---

*Синтез: 2026-07-14. Верифицировать актуальность через [`docs/architecture/crypto.md`](../architecture/crypto.md) + [`backlog/`](../../backlog/) для статусов.*

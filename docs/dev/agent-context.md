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

## 2. Крипто-стек

### Принцип: 90% — чужой аудированный код

| Слой | Компонент | Язык | Лицензия |
|------|-----------|------|---------|
| Primitives (AEAD, ECDH, sign, KDF, CSPRNG) | libsodium via ionspin KMP 0.9.5 | Kotlin | ISC |
| Group protocol (RFC 9420 MLS) | openmls 0.8.1 | Rust | MIT |
| QR Handshake (Noise_XX) | snow crate | Rust | Apache-2.0 |
| FFI bridge | UniFFI (Mozilla) | Rust→Kotlin gen | — |
| Encrypted at-rest | SQLCipher + Android Keystore AES-256-GCM | C + Android | BSD-style |

Наш код (~10%): domain ports, UniFFI wrapper, wire formats + roundtrip tests, storage adapter, UI.

### Почему MLS (openmls) — а не Sender Keys, не libsignal, не CoreCrypto

**Sender Keys** (Signal/WhatsApp для групп): каждый участник шлёт свой ключ O(N) раз при изменении группы. При 50 людях в группе = 50 pairwise-сессий на одно обновление. Нет post-compromise security (если ключ утёк — старые данные не защищены).

**MLS TreeKEM (RFC 9420)**: binary tree ключей. Обновление при add/remove = O(log N) — только путь нового узла к корню. Forward secrecy встроена в ratchet. Post-compromise security: каждый add/remove = новый epoch, полностью новый групповой ключ.

**Отклонено навсегда** (не реоткрывать — аргументы уже проработаны):
- `libsignal` — AGPL, GPL-contamination в commercial product.
- `matrix-rust-sdk` — AGPL + тащит за собой Synapse.
- `CoreCrypto` (Wire) — GPL-3, та же проблема.
- `mls-kotlin` — hobby project, 1 contributor, нет audit, нет releases.
- `Kalium` (Element) — GPL.
- Свой MLS wire format — нет смысла, RFC 9420 совместимость = interop с Signal/Wire/Discord DAVE.
- SGX для vault counter — нет у нас инфраструктуры, опережает нужды.

**openmls exit ramp**: `mls-rs` (AWS Labs, Apache-2.0/MIT, тот же RFC 9420 wire format) — ~1-2 недели rewrite adapter. Не breaking change для wire format.

### UniFFI — почему не manual JNI

UniFFI генерирует Kotlin bindings из Rust автоматически. Ручной JNI = 3-4 недели на написание и постоянная синхронизация при изменении API. UniFFI — industry standard: Element X Android, Wire Android, Firefox mobile все используют его.

**Важно**: UniFFI + uniffi-bindgen CLI + сгенерированный runtime должны быть **одной версии**. Расхождение = ошибки в рантайме которые сложно диагностировать. Нужен CI fitness function на lockstep pin.

**Panic across FFI**: Rust panic через FFI = abort процесса на Android. UniFFI имеет panic catcher — проверять что он включён при каждом release. Без него один Rust panic = крэш app.

### SQLCipher — почему не Room + отдельный Keystore ключ

openmls требует custom `StorageProvider` интерфейс — SQLCipher реализует его нативно через openmls-storage-provider crate. Room + отдельный ключ = самописный storage provider + ручное шифрование = больше кода нашего = больше ошибок.

SQLCipher exit ramp: Room + Android Keystore AES-GCM wrap — ~2 недели написать storage provider.

### Domain ports для крипты (rule 1 — domain только через них)

- `CryptoPort` — encrypt / decrypt сообщения.
- `GroupPort` — create group / add member / remove member / process commit.
- `KeyPackagePort` — publish / claim KeyPackage batches.
- `IdentityVaultPort` — хранение и использование root key (см. раздел 3.2).

### Как работает шифрование (один flow)

```
Domain → CryptoPort.encryptMessage(groupId, plaintext)
  → OpenmlsAdapter (Kotlin): единственный кто знает про openmls
    → UniFFI JNI bridge (автосгенерированный)
      → openmls (Rust): читает group state из SQLCipher
        → MLS PrivateMessage encapsulation
        → ratchet шагает (старый ключ уничтожен = forward secrecy)
        → возвращает ciphertext
```

Сервер получает только непрозрачный конверт. Никогда не видит plaintext.

### Опасные места — крипто

⚠️ **KeyPackage exhaustion** — если все 100 KeyPackages израсходованы и new ones не загружены, fall-back на Last-Resort Key. Last-resort — переиспользуемый (нет forward secrecy для той сессии). Нужен proactive publish + мониторинг pool size.

⚠️ **MLS Commit signer** — в текущей модели только один девайс является "Bab's device" = sole MLS Commit signer. Multi-device signing усложнит модель в будущем.

⚠️ **Epoch drift** — если устройство долго офлайн, оно может пропустить N epoch'ов. MLS требует sequential epoch processing — нельзя прыгнуть через. Нужна стратегия для "устройство вернулось после 30 дней офлайн".

⚠️ **UniFFI version pin** — при обновлении Rust крипты всегда проверять что uniffi-rs + uniffi-bindgen + runtime в lockstep. CI должен ловить несоответствие.

---

## 3. Root Key Hierarchy + IdentityVault

### Root Key — архитектурный центр всей крипты

Один `RootKey` (32 байта, Ed25519) на пользователя. Хранится в Android Keystore TEE (hardware-backed, StrongBox на современных устройствах). Никогда не покидает устройство в plaintext.

Из RootKey через HKDF-SHA256 детерминистически выводятся все производные ключи:

```
RootKey
  ├─ DerivedKey("config")       → шифрование конфигов (ConfigCipher2)
  ├─ DerivedKey("contacts")     → (зарезервировано)
  ├─ DerivedKey("media")        → фото, медиа
  ├─ DerivedKey("mls-signature")→ MLS identity key (будущее)
  └─ DerivedKey("recovery-...")  → recovery blob wrap key (будущее)
```

**Identity** пользователя: `identity_id = hash(root_public)` — выводится из публичной части ключа, не из Google UID. Это позволяет менять Google-аккаунт не теряя идентичность (теоретически).

### Генерация при первой настройке

1. Google Sign-In → `stableId` (UUID v4) — namespace пользователя.
2. Пользователь придумывает passphrase (8+ символов).
3. **RootKey генерируется локально случайно**. Passphrase НЕ является seed'ом для ключа.
4. `Argon2id(passphrase, random_salt) → wrapKey`.
5. `AEAD-encrypt(RootKey, wrapKey, nonce) → ciphertext`.
6. Blob `{schemaVersion, algorithm, salt, nonce, ciphertext}` → upload в `backup/{stableId}/v1.json`.

### Восстановление на новом устройстве

1. Google Sign-In (тот же аккаунт) → тот же `stableId`.
2. Пользователь вводит passphrase.
3. Загружается blob с сервера по `stableId`.
4. `Argon2id(passphrase, blob.salt) → wrapKey → AEAD-decrypt(blob.ciphertext) → RootKey`.
5. RootKey идентичен оригинальному — все производные ключи те же — все данные расшифровываются.

### IdentityVaultPort — граница port'а (архитектурный one-way door)

**Проблема**: как domain получает доступ к производным ключам без утечки сырого материала.

**Наивное решение** (нельзя делать):
```kotlin
val derivedKey = vault.exportDerivedKey("config")
val ciphertext = cipher.encrypt(plaintext, derivedKey) // derivedKey теперь в domain
```
Domain видит raw bytes — может случайно залогировать, передать в IPC, попасть в crash report.

**Правильный pattern** ("operation-on-vault"):
```kotlin
val ciphertext = vault.performOperation("config") { derivedKey ->
    cipher.encrypt(plaintext, derivedKey)
    // derivedKey живёт только внутри lambda, уничтожается после
}
```
Domain получает только результат операции. Ключ не покидает cryptographic boundary.

Исключение ("narrow hatch"): в редких случаях когда нужен raw key export (например, для передачи ключа во внешний Rust код через FFI) — явный `exportDerivedKey(purpose, ReceiverScope)` с задокументированным justification. Не дефолт.

### Android Keystore — TEE, StrongBox, per-identity isolation

- RootKey обёрнут AES-256-GCM ключом из Android Keystore (hardware-backed).
- На устройствах с StrongBox ключ хранится в отдельном защищённом чипе.
- Per-identity namespacing: у каждого Google UID — отдельный namespace в Keystore. При logout → cascade wipe всех ключей этого пользователя.

### Опасные места — ключи

⚠️ **stableId = Google UUID** — сейчас `stableId` это Google sub/UID. Сервер видит `stableId` в пути `/backup/{stableId}/v1.json`. Это нарушает rule 13 (opaque identifiers). Правильно: `stableId = HMAC(root_key, "server-pseudonym")`, но это migration с re-upload всех blobs. Пока MVP допущение.

⚠️ **Argon2id параметры** — при изменении параметров (memory, iterations) нужен schema version bump в recovery blob. Старые blobs с другими параметрами должны читаться. Параметры хранятся в blob'е именно для этого.

⚠️ **Нет rotation в MVP** — RootKey не ротируется. При компрометации = полный reset. Rotation запланирована как отдельная фича в Phase 5+. Exit ramp документирован.

⚠️ **Huawei без GMS** — нет Google Sign-In → нет stableId → нет cloud recovery. Только device-local режим. При factory reset — всё потеряется. Документировать это пользователю.

---

## 4. Passphrase + Recovery

### Что passphrase делает и чего не делает

| Вопрос | Ответ |
|--------|-------|
| Derivation seed для RootKey? | ❌ Нет. RootKey генерируется случайно |
| Unlock локального vault? | ❌ Нет. Vault зашифрован Keystore TEE-ключом |
| Unwrap RootKey при восстановлении? | ✅ Да. Единственная функция |
| Отправляется на сервер? | ❌ Никогда |
| Хранится на устройстве? | ❌ Никогда |

### Argon2id — почему и какие параметры

Argon2id — memory-hard KDF. При подборе passphrase атакующий должен держать в памяти сотни мегабайт — GPU с тысячами ядер не помогает, каждое ядро всё равно ждёт RAM.

- Memory: ~512 МБ (баланс: дорого для атакующего, ~1-2 сек на современном телефоне).
- Salt: 16 байт случайных, хранится в blob'е — без него recovery невозможно.
- Параметры записаны в blob (`algorithm` поле) — при смене параметров читаются старые blob'ы без проблем.

### Пути восстановления

**1. Passphrase alone** — основной путь MVP:
Google Sign-In + passphrase → unwrap blob → RootKey.

**2. Passphrase + peer confirmation** — Chrome-model:
Новое устройство добавляется через MLS Add, push на существующие устройства «новое устройство добавлено — это вы?». Существующий девайс может отозвать в течение TTL. Не требует ceremony на старом устройстве — auto-add.

**3. Passphrase + 2FA escrow** — для admin recovery:
Admin восстановился на новом девайсе → получает 6-значный код с managed-устройства primary user'а. Код хранится в зашифрованном escrow (Firestore), сервер не видит plaintext. TTL 10 мин, cooldown 1 час на 3 ошибки.

**4. "Начать с нуля"** — fallback при 3 неверных попытках:
Удаляет recovery blob, очищает счётчик, возвращает на первоначальную настройку. Требует двойного подтверждения. Google-аккаунт сохраняется, устройство настраивается заново.

**5. Social recovery** — (запланировано, не реализовано):
2-of-3 доверенных лица подтверждают через универсальный attestation механизм. Активировать только если потеря passphrase + Google-аккаунта станет реальной проблемой.

### Anti-brute-force — нерешённая проблема

**Client-side** (реализовано): 3 попытки = 1 час блокировка. **Легко обойти** через Clear App Data / factory reset. Недостаточно для production.

**Server-side** (нужно, ещё не выбрано):

Три варианта:
- **Signal SVR** (Secure Value Recovery) — SGX на сервере обслуживает защищённый vault-counter. Нет у нас на Cloudflare, требует SGX-инфраструктуры.
- **OPAQUE** (RFC draft) — PAKE-протокол, сервер никогда не видит passphrase даже при проверке. Production deployments пока единицы, immature.
- **Simple HMAC counter** (наиболее вероятный выбор): `HMAC(derived_key, vaultId)` → сервер проверяет HMAC + атомарно инкрементирует счётчик. Rate-limit 3/час. Сервер не видит passphrase, но нужно доверять серверу с counter'ом.

Это Tier 2 по rule 13 — server-side state допускается только если client-side bypass **реален** (Clear App Data / factory reset). Здесь bypass реален, поэтому server-side counter оправдан.

**Это блокирует production deployment** — без server-side counter offline brute-force возможен.

### Биometric unlock

Optional UX adapter. Биометрия разблокирует только локальный vault (convenience). **Не заменяет passphrase при восстановлении** — биометрия не переносится через cloud. При recovery на новом устройстве passphrase всегда нужен.

---

## 5. Pairing

### QR Pairing — Noise_XX handshake

**Noise_XX** (snow Rust crate) — взаимная аутентификация + ephemeral X25519 ECDH. 3-message protocol.

Почему Noise_XX, а не самописный ECDH + signature:
- Доказанная безопасность протокола (formalized noise spec).
- Защита от replay, MITM, identity misbinding out-of-the-box.
- snow — audited, production-used Rust crate.
- Самописный протокол = ошибки в edge cases (timing attacks, nonce reuse, etc.).

**Flow QR pairing**:
1. Device A показывает QR: `{noiseMessage1, claimToken, sourceIdentityId, expiresAt}`.
2. Device B сканирует → Noise_XX handshake через сервер (`/v1/pairing/complete`, `/v1/pairing/finalize`).
3. Handshake завершён → симметричный `TrustEdge` на обоих.
4. A выполняет MLS Add: claim KeyPackage B → Commit + Welcome → B joinит device management group.

**TrustEdge** — sealed output pairing'а: `{edgeId, peerIdentity, peerPubKey, role: ManagedByMe | ManagerOfMe}`. Хранится в зашифрованном bucket'е на сервере.

### Iconic Challenge — SAS verification поверх Noise

Short Authentication String (SAS) — визуальное сравнение обоими пользователями для защиты от активного MITM при QR-pairing.

Реализация через иконки вместо emoji/цифр:
- `seed` (32 байта) — общий для обоих устройств, выводится из Noise handshake.
- Device A: показывает одну иконку `render(seed)`.
- Device B: показывает 3 варианта (правильный + 2 distractor'а).
- Пользователь тапает совпадающую.
- Deterministic + single-use (каждый challenge = новый seed).

Компонент reusable — используется везде где нужно визуальное подтверждение (cross-app onboarding, social recovery).

### Cross-app trust — chain via Install Referrer

**Проблема**: Launcher + Мессенджер + Фотоальбом — три отдельных APK. При recovery каждого нужен passphrase отдельно. Пользователь не помнит три passphrase.

**Решение**: Chain of trusted anchors через Google Play Install Referrer.

Launcher первый (у него passphrase). Хочет доверить мессенджеру свой recovery key.
1. Launcher публикует `sealed_box(messenger_pubkey, handoff_data)` + opaque token на сервер.
2. Открывает `market://...&referrer=base64(sealed||token)`.
3. Google Play передаёт referrer при установке (persist 90 дней).
4. Мессенджер читает InstallReferrer → расшифровывает sealed_box → redeems opaque token.
5. Iconic challenge (визуальное подтверждение).
6. Attestation выдаёт recovery key от launcher'а.
7. Мессенджер восстанавливается **без passphrase**.

Symmetric chain — мессенджер сам может стать anchor для следующего приложения.

### Link Invite для caregiver (ограниченный доступ)

Второй `PairingChannel` adapter:
- Admin создаёт signed invite-ссылку (TTL 30 дней, ограниченный scope: только SOS, без конфига).
- Caregiver открывает ссылку → устанавливает app → автоматический pairing в caregiver-режиме.
- Manual revoke = мгновенная потеря доступа.

### Роли в системе (domain vocabulary — не персонажи)

| Роль | Определение |
|------|-----------|
| **primary user** | Основной пользователь устройства (тот, кого настраивают) |
| **remote administrator** | Полный remote-доступ к настройкам primary user'а |
| **restricted caregiver** | Ограниченный доступ (SOS-only и т.п.) |
| **family group / care group** | Абстрактная группа устройств/пользователей |

Запрещено в domain code: "grandma", "бабушка", "семья", "daughter" — только в примерах use-cases.

### Опасные места — pairing

⚠️ **claimToken истечёт** — QR-коды имеют `expiresAt`. UI должен показывать таймер и давать regenerate. Иначе пользователь покажет QR, второй человек начнёт сканировать — token expired, handshake падает без понятной ошибки.

⚠️ **Server delivery pairing messages** — Noise messages идут через сервер (`/v1/pairing/complete`, `/v1/pairing/finalize`). Сервер не видит plaintext (Noise зашифрован), но видит что происходит pairing между двумя identities. Это нарушение rule 13 если identities не opaque. Mitigation: использовать опaque `claimToken` как routing key, не identity_id.

⚠️ **Install Referrer window** — 90 дней TTL Google Play. Если пользователь установил мессенджер > 90 дней после launcher'а — referrer потерян. Нужен fallback path (QR pairing между приложениями напрямую).

---

## 6. Config Sync между устройствами

### Что синхронизируется и что нет

**Синхронизируется** (non-identity, portable по rule 9):
- Tile arrangements, contact grid layout.
- Launcher theme, font size, contrast.
- Wizard-produced profile (preset applied, adaptive UX profile).
- Contact metadata (имя, телефон, emergency flags).
- Named configs (библиотека на 5 конфигов на namespace).

**НЕ синхронизируется** (identity-bound, остаётся на устройстве):
- X25519 / Ed25519 key pairs.
- Recovery blob, passphrase.
- FCM token, Google auth tokens.
- Device-specific state (installed apps inventory).

### E2E Encryption Envelope — ConfigCipher2

Hybrid encryption: Curve25519 + XChaCha20-Poly1305 AEAD.

```json
{
  "schemaVersion": 1,
  "bucketTypeId": "bucket.config",
  "ciphertext": "<XChaCha20-Poly1305 blob>",
  "recipientKeys": {
    "deviceId1": "<X25519 sealed-box(CEK)>",
    "deviceId2": "<X25519 sealed-box(CEK)>"
  },
  "signedBy": "<Ed25519 pubkey>",
  "timestamp": 1234567890
}
```

- **Content Encryption Key (CEK)** генерируется каждый раз новый.
- Каждый получатель получает CEK зашифрованным на его X25519 pubkey.
- Сервер видит только конверт — не знает сколько получателей, кто они, что внутри.
- Multi-recipient поддерживается с первого коммита (N ≥ 1 recipients).

**ConfigSource adapter pattern** (rule 9):
- `BundledConfigSource` — bundled presets.
- `ImportFromFileConfigSource` — import .json файла (будущее).
- `ShareIntentConfigSource` — receive share intent (будущее).
- `NetworkSource` — pull from cloud library (будущее).
- `MarketplaceConfigSource` — curated marketplace (далёкое будущее).

### FCM Push Trigger

Когда конфиг обновляется — owner'ы устройств получают push:

```
Client: PushTrigger.trigger("config-updated", targetScope, ownerUid, {configName: "main"})
  → Worker: JWT verify → resolve recipients → FCM 3× retry (500ms/2s/8s backoff)
    → Receiver: ConfigUpdatedHandler → ConfigSaver.loadOwn("main") → DataStore + UI refresh
```

Payload (`schemaVersion=1`): `{ownerUid, eventType: "config-updated", targetScope, payload, triggerId}`.

Collapse key: `"config-updated:{ownerUid}:main"` + 2s debounce + KV dedup (10-min TTL). Несколько быстрых обновлений = один pull.

**Важно**: eventType в payload сейчас читается сервером для роутинга. По rule 13 это нарушение — сервер видит semantics события. Правильно: зашифрованный опaque payload + routing на основе opaque token. В MVP допущение, нужна миграция.

### Offline-first — cloud не обязателен

**CloudAvailability port**: `StateFlow<CloudState>` — Unknown / Offline / Available / Disabled.

Что работает **всегда** без cloud:
- Home screen, локальные контакты, SOS-dialer, темы, wizard, локальный конфиг.

Что требует cloud:
- Pairing, multi-device config sync, контактные фото, health monitoring, subscriptions, FCM push.

Cloud trigger: первое cloud-действие (например "синхронизировать настройки") → Google Sign-In → recovery setup → действие выполняется. **FCM token НЕ регистрируется при старте app** — только при первом cloud-действии.

### Опасные места — config sync

⚠️ **Conflict resolution** — last-write-wins по умолчанию. При одновременном редактировании с двух устройств — данные могут теряться. Для MVP допустимо, для будущего нужен CRDT или explicit merge UI.

⚠️ **Config history retention** — сейчас 10 версий хранится client-side. Server не контролирует retention. Если пользователь устанавливает app на новое устройство — history не восстанавливается (только последняя версия). Это known limitation.

⚠️ **RecipientResolver** — когда добавляется новый recipient (новое устройство через pairing), нужно re-encrypt существующий config blob для нового набора recipients. Иначе новое устройство не может его прочитать. Re-encryption должна происходить атомарно.

⚠️ **eventType утечка** — push payload содержит `eventType` читаемый Worker'ом. Нарушение zero-knowledge posture. Краткосрочный workaround: encrypt eventType в payload + use opaque routing token. Долгосрочно: зашифрованный опaque payload.

---

## 7. Серверная архитектура

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

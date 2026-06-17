# Feature Specification: F-CRYPTO — `core/crypto/` KMP module foundation

**Feature Branch**: `j_f_crypto_core_module_17_06_26`
**Created**: 2026-06-17
**Status**: Draft
**Input**: User description: «Phase 1 шаг 2 — выделить криптографию проекта в отдельный переиспользуемый KMP-модуль `core/crypto/` (артефакт `lib-family-crypto`) **до** первого реального использования (F-5 ConfigCipher, спека 011 media blobs, будущий мессенджер владельца, фото-приложение, EOS / Android TV / Google TV). Решения mentor-сессии 2026-06-17 зафиксированы в `docs/product/roadmap.md` §F-CRYPTO и memory `project_f_crypto_decisions.md`.»

## Контекст и цель спека

Сейчас в проекте **нет** криптографического модуля. Первые потребители криптографии (F-5 ConfigDocument E2E, спека 011 media blobs) ещё не написаны, но **архитектурное решение** — выделить криптографию в отдельный модуль **сразу при первом использовании**, не задним числом. Это применение [CLAUDE.md rule 2](../../CLAUDE.md) (ACL для каждой внешней зависимости) и rule 6 (mock-first).

**Что строим**: `core/crypto/` — отдельный KMP common module (Gradle subproject, артефакт `lib-family-crypto`). Содержит:

- **Domain ports** (interfaces в `core/crypto/api/`) — это знают потребители: `AeadCipher`, `AsymmetricCrypto`, `KeyDerivation`, `RandomSource`, `SecureKeyStore`, `KeyRotation` (interface-only), `KeyEscrow` (interface-only).
- **Реальный adapter** на libsodium через `ionspin/kotlin-multiplatform-libsodium`.
- **SecureKeyStore Android adapter** — wrap pattern (AES-256-GCM ключ из Android Keystore TEE обёртывает Curve25519 private keys, blob в app sandbox).
- **FakeAdapter** для тестов (deterministic, `@VisibleForTesting`, исключён из release builds).
- **Validation set** (заменяет ~~friend crypto review~~): RFC KAT + Google Wycheproof + property tests + industrial reference baseline.
- **Cross-platform test vector reuse** — гарантирует, что Android-телефон и Android TV / iOS читают одинаковые байты.

**KMP targets с day 1**: `androidMain`, `jvmMain`, `iosX64`/`iosArm64`/`iosSimulatorArm64` декларированы; iOS CI build активируется при первой iOS-фиче.

**Что НЕ строим**: ConfigCipher (F-5), envelope encryption для shared content, реальная key rotation logic, cloud key escrow, Sender Keys / MLS, post-quantum primitives, активный iOS CI.

**Скрытое допущение** (явно зафиксировано): индустриальный baseline (Signal, WhatsApp, WireGuard, age, Threema, Bitwarden Send используют тот же primitives stack: XChaCha20-Poly1305 + X25519 + Ed25519 + HKDF-SHA256) — это наш аргумент к любому регулятору / аудитору, заменяющий friend crypto review.

**Стратегия валидации без аудита** (решение 2026-06-17): RFC KAT + Google Wycheproof + property tests дают **измеримое** покрытие; платный аудит (7ASecurity / Radically Open Security) перенесён в `docs/dev/server-roadmap.md` SRV-CRYPTO-003 как milestone **перед запуском billing**, не для F-CRYPTO merge'а.

**Дальнейшие потребители** (НЕ часть этого спека, но должны быть unblock'нуты):
- F-5: `ConfigCipher` поверх `AeadCipher` + `AsymmetricCrypto` + `KeyDerivation`.
- Спека 011: media blob encryption поверх `AeadCipher`.
- **Спека 017 (multi-device-recovery)** — social recovery per [ADR-008](../../docs/adr/ADR-008-social-recovery-architecture.md). Использует `KeyDerivation` (HKDF-SHA256), `AeadCipher` (XChaCha20-Poly1305), `AsymmetricCrypto` (X25519 ECDH + **sealed boxes** через `crypto_box_seal` для encryption peer_nonce для конкретного recipient'а).
- Будущий мессенджер: Sender Keys поверх примитивов.
- Фото-приложение / EOS / Android TV / Google TV: те же примитивы через published artifact `lib-family-crypto`.

## Clarifications

### 2026-06-17 — Pre-plan clarification pass (mentor mode, 8 questions)

| # | Question | Resolution |
|---|----------|------------|
| 1 | iOS targets day 1: что писать на iOS-стороне сейчас? | **(a) Заглушка-крикун** — `actual class` в `iosMain` для `SecureKeyStore` бросает `NotImplementedOnIosYetError()`. Targets компилируются, iOS-runtime сразу падает при попытке использовать. Заменяется на реальный iOS Keychain adapter когда придёт первая iOS-фича — это «дописывание», не «переписывание». Утверждён мета-принцип владельца: «откладываем что можно, при условии что отложенное добавляется как дописывание, не переписывание». |
| 2 | Тип `KeyId` в портах? | **(b) value class** `KeyId(val raw: String)` + **валидация префикса в `init`**: имя обязано начинаться с одного из registered prefix'ов (`config-`, `media-`, `messenger-`, `recovery-`, `__internal-`). Регистр префиксов — отдельный sealed class `KeyNamespace` в `core/crypto/api/`. Compile-time safety от типовых багов + runtime safety от collision'ов между features. |
| 3 | Кто генерирует nonce для AEAD? | **(a) Модуль сам.** `AeadCipher.encrypt(plaintext, key, aad)` — без параметра nonce; adapter генерирует random внутри, nonce встроен в возвращаемый ciphertext blob. **Защита by design**: caller физически не может зафакапить nonce reuse. Это и есть port+adapter pattern по rule 2 — потребители не знают про nonce внутри. |
| 4 | Streaming AEAD для больших файлов? | **(a) Только one-shot API.** `AeadCipher` даёт `encrypt(ByteArray) → ByteArray`. **Chunking — работа потребителя**, не F-CRYPTO. Например: спека 011 для фото 5-15MB шифрует целиком (one-shot), будущая видео-спека для 2-3GB файлов делает chunking сама (один media blob = N AEAD-chunk'ов). F-CRYPTO **не знает** domain-specific размеров (5MB/64KB/2GB) — это лежит у потребителей. Соответствует rule 4 (MVA — не делать абстракцию заранее). |
| 5 | Wire format для KeyBlob: JSON или CBOR? | **(a) JSON через `kotlinx.serialization.json`** — для **F-CRYPTO KeyBlob'ов** (их единицы, размер не важен, debug-ability ценнее). **Поправка**: CBOR **не безопаснее** JSON криптографически — оба формата находятся **внутри** уже-AEAD-encrypted blob'а, snaружи виден только ciphertext (формат внутри неотличим). Миграция JSON → CBOR в будущем возможна **через standard schemaVersion migration** (rule 5), но требует написания migrator (не «автоматическая бесшовная подмена»). Для media blobs (спека 011) выбор JSON vs CBOR — отдельный вопрос той спеки. |
| 6 | Device-local HKDF salt — где живёт, что при reinstall? | **(a) Salt в `SecureKeyStore`** под зарезервированным keyId `__internal-hkdf-device-salt-v1`. При app uninstall TEE-ключ уничтожается → salt теряется → все wrapped blobs становятся «навсегда нечитаемыми». **Восстановление** — через **social recovery per [ADR-008](../../docs/adr/ADR-008-social-recovery-architecture.md)** в будущей **спеке 017 (multi-device-recovery)**: multi-factor — passphrase бабушки + 2FA подтверждение от trusted peer (внука) + email auth. Это становится **value proposition** «зарегистрируйся → получишь восстановление при потере телефона». |
| 7 | Google Wycheproof — snapshot pin или dynamic? | **(a) Snapshot pin.** Subset Wycheproof JSON-векторов скачивается один раз, кладётся в `core/crypto/src/commonTest/resources/wycheproof/`, коммитится в git. Обновление — manual через отдельный PR «refresh wycheproof vectors». CI стабилен, build offline-friendly. |
| 8 | Cross-version blob compatibility (`:1.x → :2.x`)? | **(a) Стандартный semver.** В пределах major (1.x) — backward-compat read обязателен всегда. Major bump (1.x → 2.0) — migrator пишется **до** релиза, читает 1.x и записывает 2.0. Индустриальный стандарт для библиотек. На данный момент имеем версию 1.0; правило применится при первом major bump'е. |

### Side-effects от этого clarify-pass'а (вне scope текущей спеки)

- **Ренумерация спек 2026-06-17**: `spec 015 (multi-device-recovery)` в ADR-008 и backlog переименован в **`spec 017`**, потому что текущие `015 = Wizard Localization (закрыт)` и `016 = F-CRYPTO (текущий)`. Обновлено в [ADR-008](../../docs/adr/ADR-008-social-recovery-architecture.md), [project-backlog.md](../../docs/dev/project-backlog.md) §TODO-RECOVERY-001 + §TODO-AUTH-001.
- **`AsymmetricCrypto` port расширен**: добавлены операции `sealCEK(cek, recipientPub) → sealedBlob` / `unsealCEK(sealedBlob, recipientPriv) → cek` (используют libsodium `crypto_box_seal` / `crypto_box_seal_open`). Это нужно для ADR-008 — peer_nonce шифруется для конкретного recipient'а без отправки sender public key. См. обновлённый FR-007.
- **Server-roadmap дополнен**: SRV-CRYPTO-004 уточнён (recovery = social per ADR-008, **не** passphrase-only); добавлен SRV-CRYPTO-006 (server-side rate-limit на recovery attempts + peer notification на неудачные попытки) и SRV-CRYPTO-007 (где хранить `encrypted_backup` — Firestore document или Firebase Storage; **главное ограничение** — структура должна **легко переезжать на собственный сервер**).
- **Мета-правило владельца** записано в feedback memory: «откладываем что можно при условии, что отложенное добавляется как *дописывание*, не *переписывание*».

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Feature developer пишет E2E-шифрование без знания libsodium (Priority: P1)

Разработчик F-5 (ConfigDocument E2E) пишет `ConfigCipher` поверх `AeadCipher.encrypt(...)` и `AsymmetricCrypto.deriveSharedSecret(...)`. **Не импортирует libsodium**, не знает деталей XChaCha20-Poly1305 / X25519. Код F-5 живёт в `core/config/`, импортирует только `core/crypto/api/`.

**Why this priority**: без этого F-5 (production blocker) написать нельзя без нарушения CLAUDE.md rule 1+2. Это **базовый сценарий**, для которого F-CRYPTO существует.

**Independent Test**: можно протестировать в `commonTest`: создать `FakeAeadCipher` + `FakeAsymmetricCrypto`, написать тестовый `ConfigCipher` (минимальный пример), пройти roundtrip `encrypt → decrypt → equal`. Не требует libsodium binding.

**Acceptance Scenarios**:

1. **Given** F-CRYPTO модуль готов, **When** разработчик импортирует `import family.crypto.api.AeadCipher`, **Then** компилируется без транзитивных импортов libsodium / ionspin / JCA в его файле.
2. **Given** разработчик пишет тест `ConfigCipherRoundtripTest`, **When** использует `FakeAeadCipher` через DI, **Then** тест проходит без интернета, без эмулятора, на чистом JVM.
3. **Given** разработчик меняет реализацию adapter'а в `core/crypto/libsodium/`, **When** все потребители (F-5, 011) пересобираются, **Then** ни один файл потребителей **не требует** изменений — только DI wiring.

---

### User Story 2 — Cross-platform compatibility (admin Android ↔ senior Android TV ↔ будущий iOS) (Priority: P1)

Внук-admin шифрует config на Android-телефоне. Бабушка-senior на Android TV / Google TV должна расшифровать **тот же** config (одинаковые байты). Будущий мессенджер на iOS — то же самое. Это гарантируется **одинаковыми primitives** на всех target'ах и **одинаковыми test vectors** в `commonTest`.

**Why this priority**: владелец явно подтвердил, что у его семьи будут устройства разных типов (Android TV, Google TV, EOS); cross-platform несовместимость = silent corruption.

**Independent Test**: `commonTest` с RFC test vectors исполняется на `androidUnitTest` + `jvmTest` (имитация «другой платформы»). Идентичные байты на обоих — pass. Любое расхождение — build failure.

**Acceptance Scenarios**:

1. **Given** RFC 7748 X25519 test vector в `commonTest`, **When** запускается на `androidUnitTest`, **Then** результат идентичен ожидаемому из RFC.
2. **Given** тот же тест на `jvmTest`, **When** запускается, **Then** результат идентичен Android-результату (byte-for-byte).
3. **Given** config зашифрован `AeadCipher` на Android, **When** сериализован байтами и расшифрован на JVM (через тот же `AeadCipher` impl), **Then** plaintext совпадает.

---

### User Story 3 — Приватные ключи admin'а защищены от извлечения (Priority: P1)

Внук-admin хранит свой Curve25519 private key на Android-телефоне. Даже если злоумышленник получил root-доступ к sandbox-файлу — он **не может** расшифровать blob без TEE-ключа из Android Keystore. Это wrap pattern (Signal/WhatsApp/Bitwarden).

**Why this priority**: privacy-критично; решение о wrap pattern принято на mentor-сессии 2026-06-17 как обязательное.

**Independent Test**: `SecureKeyStoreInstrumentationTest` на эмуляторе — `store(keyId, blob) → load(keyId) → assert equal`. На JVM-тесте использовать FakeSecureKeyStore (HashMap in-memory, явно non-production).

**Acceptance Scenarios**:

1. **Given** Android Keystore доступен, **When** admin app генерирует X25519 keypair и вызывает `SecureKeyStore.store("admin-identity-v1", privateKey)`, **Then** в app sandbox появляется blob, **который не содержит plaintext private key** (проверка: scan blob на байты исходного ключа — не найдены).
2. **Given** blob сохранён, **When** admin app перезапускается и вызывает `SecureKeyStore.load("admin-identity-v1")`, **Then** возвращается оригинальный private key.
3. **Given** app uninstalled и переустановлен, **When** `SecureKeyStore.load(...)` вызывается, **Then** возвращается `null` (TEE-ключ удалён вместе с app data — это **ожидаемое** поведение, scenario B в mentor session).

---

### User Story 4 — F-CRYPTO maintainer заменяет libsodium binding без переписывания потребителей (Priority: P2)

Если `ionspin/kotlin-multiplatform-libsodium` deprecated, maintainer F-CRYPTO заменяет binding на BouncyCastle (Android) + cinterop libsodium (iOS) — **только в `core/crypto/libsodium/`** и DI wiring. Потребители (F-5, 011) **не меняются**.

**Why this priority**: rule 2 ACL — это **главный** архитектурный test. Без него весь F-CRYPTO бесполезен.

**Independent Test**: experimental — заменить implementation одного port'а на alternative (BouncyCastle), прогнать все тесты потребителей — должны пройти без изменений.

**Acceptance Scenarios**:

1. **Given** F-CRYPTO собран с ionspin binding, **When** maintainer удаляет ionspin и подключает BouncyCastle для `AeadCipher`, **Then** все тесты потребителей (фейк F-5 ConfigCipher) проходят без code changes.
2. **Given** замена выполнена, **When** RFC KAT тесты прогоняются на новом adapter'е, **Then** результаты идентичны старым байтам (byte-for-byte).

---

### User Story 5 — Library extraction в отдельный репозиторий через год (Priority: P3)

Когда у владельца появится 2-й реальный потребитель (мессенджер или фото-приложение), `core/crypto/` выносится в отдельный приватный git-репозиторий через `git filter-repo`. История коммитов сохраняется. Launcher продолжает работать через published Maven artifact.

**Why this priority**: P3 — это не блокер для текущего merge'а, но **должно быть возможно за день работы**, без переписывания. Inline TODO + clean module boundary гарантируют это.

**Independent Test**: проверяется через **fitness function** — Detekt-правило / Gradle-проверка, что `core/crypto/` не импортирует ничего кроме Kotlin stdlib, coroutines, ionspin libsodium. То есть: модуль может скомпилироваться **сам по себе** без launcher-кода.

**Acceptance Scenarios**:

1. **Given** `core/crypto/build.gradle.kts`, **When** Gradle dependencies проверены, **Then** список = `kotlin-stdlib`, `kotlinx-coroutines-core`, `kotlinx-serialization-json`, `ionspin libsodium-kmp`. Никаких `core/wizard/`, `core/config/`, `app/`.
2. **Given** Gradle task `:core:crypto:assemble`, **When** запускается без других модулей, **Then** успешно собирается.

---

### User Story 6 — Регулятор / аудитор спрашивает «как вы валидировали криптографию?» (Priority: P2)

Владелец показывает `docs/dev/crypto-review.md` с industrial reference baseline (Signal/WhatsApp/WireGuard/age/Threema/Bitwarden Send используют тот же stack) + список passed KAT + Wycheproof + property tests. Это **достаточно** для public beta без billing.

**Why this priority**: P2 — не блокирует тесты, но **необходимо** для answering «как вы это проверили».

**Independent Test**: документ `docs/dev/crypto-review.md` существует, содержит все 4 уровня валидации, имеет ссылки на passed CI build с зелёными KAT.

**Acceptance Scenarios**:

1. **Given** F-CRYPTO merged, **When** регулятор / аудитор / любопытный пользователь читает `docs/dev/crypto-review.md`, **Then** видит: список primitives, industrial reference, список pass'ов KAT / Wycheproof, ссылку на CI run.
2. **Given** запрос «когда будет paid audit», **When** документ открыт, **Then** содержит cross-reference на SRV-CRYPTO-003 (milestone перед billing).

---

### Edge Cases

- **libsodium binding ionspin deprecated / dead на момент имплементации** → research-фаза спеки **обязана** проверить last release / open issues; fallback на BouncyCastle (Android) + cinterop (iOS). Это **two-way door** (можем поменять позже, потому что Port + Adapter), но research должен подтвердить выбор **до** старта кодирования.
- **Android Keystore не доступен** (rooted-эмулятор без Keystore) → `SecureKeyStore` Android adapter падает с `KeystoreUnavailableException`. Fake adapter в тестах продолжает работать. **Production app должен fail-fast** с ясным сообщением «ваше устройство не поддерживает защищённое хранение ключей» — это P3 UX issue, в F-CRYPTO только эксепшен.
- **FakeAdapter случайно попал в release build** → build-config gate + Detekt-правило ловит `Fake*Cipher` в `app/release/`. Если правило не сработало — runtime assertion в `Application.onCreate()` падает с явной ошибкой «non-production adapter detected in release build».
- **Cross-platform vector расхождение** (Android vs JVM выдают разные байты) → CI failure, blocking merge.
- **Wycheproof low-order point** скормлен `AsymmetricCrypto.deriveSharedSecret(...)` → adapter возвращает error (не silent zero shared secret). Это критично — silent zero = full break.
- **Nonce policy violation** (попытка encrypt с тем же nonce+key) → adapter raises error; property test ловит это автоматически.
- **`SecureKeyStore.store(...)` после смены user lock-screen** → Android Keystore инвалидирует ключи привязанные к биометрии. Решение: для F-CRYPTO admin keys биометрия **не требуется** (`setUserAuthenticationRequired(false)`); владелец может усилить позже отдельной спекой.
- **Key storage wire format миграция** (когда добавляем `keyAttestation` поле в blob) → backward-compat read test обязан читать `schemaVersion=1` blob кодом, ожидающим `v2`.
- **App factory reset / переустановка**: TEE-ключ удаляется → wrapped blobs становятся «навсегда нечитаемыми». Это **ожидаемое** поведение; восстановление — **спека 017 (multi-device-recovery)** per [ADR-008 social recovery](../../docs/adr/ADR-008-social-recovery-architecture.md): multi-factor через passphrase + 2FA от trusted peer + email auth. F-CRYPTO даёт необходимые примитивы (`KeyDerivation`, `AeadCipher`, `AsymmetricCrypto.sealCEK`).
- **Один потребитель попытался положить ключ с неизвестным префиксом** (например, `KeyId("photo-album-v1")` без префикса `media-`) → `IllegalArgumentException` на конструировании `KeyId` (FR-010). Property test ловит это.
- **Большой файл (фото 50MB, видео 2-3GB) шифруется через `AeadCipher.encrypt(...)` целиком** → потенциально OOM на старых устройствах. **F-CRYPTO не отвечает** — потребитель (спека 011 media-blobs / будущая видео-спека) **обязан** разбить файл на chunks **до** вызова F-CRYPTO. F-CRYPTO работает с тем, что ему передали.

## Requirements *(mandatory)*

### Functional Requirements

**Module structure & build:**

- **FR-001**: Система MUST содержать Gradle subproject `core/crypto/` (артефакт name `lib-family-crypto`), КАК KMP common module.
- **FR-002**: Subproject MUST декларировать KMP targets: `androidMain`, `jvmMain`, `iosX64`, `iosArm64`, `iosSimulatorArm64` с первого коммита.
- **FR-003**: iOS CI build MUST NOT быть активирован до первой iOS-фичи (декларация ≠ сборка).
- **FR-004**: `core/crypto/build.gradle.kts` MUST содержать inline TODO `// TODO(extract-when-2nd-consumer): когда появится мессенджер / фото-приложение / EOS / Android TV — git filter-repo в отдельный приватный репо; license на extract = Apache 2.0`.
- **FR-005**: `core/crypto/` MUST НЕ импортировать никаких модулей launcher'а (`core/wizard/`, `core/config/`, `app/`); проверяется Gradle fitness function.

**Domain ports:**

- **FR-006**: `core/crypto/api/` MUST содержать port `AeadCipher` с операциями:
  - `encrypt(plaintext, key, aad) → ciphertext` — **nonce генерируется adapter'ом** (random внутри), встроен в возвращаемый ciphertext blob. Caller **не** передаёт nonce (clarification Q3).
  - `decrypt(ciphertext, key, aad) → plaintext` — nonce извлекается из ciphertext header.
  - **API — only one-shot** (`ByteArray` целиком). Streaming / chunking — **работа потребителя**, не F-CRYPTO (clarification Q4).
  - Signatures используют только Kotlin stdlib типы (`ByteArray`, etc.).
- **FR-007**: `AsymmetricCrypto` port MUST содержать:
  - `generateX25519KeyPair() → KeyPair`, `generateEd25519KeyPair() → KeyPair`.
  - `deriveSharedSecret(myX25519Priv, theirX25519Pub) → SharedSecret` (X25519 ECDH).
  - `sign(message, ed25519Priv) → Signature`, `verify(signature, message, ed25519Pub) → Bool`.
  - **`sealCEK(cek, recipientX25519Pub) → SealedBlob`** — sealed-box encryption (libsodium `crypto_box_seal`); sender ephemeral. **Зачем**: ADR-008 social recovery шифрует `peer_nonce` для конкретного trusted peer без раскрытия sender identity.
  - **`unsealCEK(sealedBlob, recipientX25519Priv) → CEK`** — sealed-box decryption (libsodium `crypto_box_seal_open`).
- **FR-008**: `KeyDerivation` port MUST содержать `derive(ikm, salt, info, length) → key` (HKDF-SHA256 семантика). `info` field MUST принимать произвольные ASCII strings (например, `"launcher-recovery-aead-v1"`, `"config-key-v1"`, `"media-key-v1"`) — потребители используют для domain separation между ключами разного назначения.
- **FR-009**: `RandomSource` port MUST содержать `nextBytes(n) → ByteArray` (cryptographically-secure).
- **FR-010**: `SecureKeyStore` port MUST содержать `store(keyId, blob)`, `load(keyId) → blob?`, `delete(keyId)`. `KeyId` — value class `KeyId(val raw: String)` с валидацией префикса в `init` (clarification Q2): `raw` MUST начинаться с одного из registered префиксов, перечисленных в sealed class `KeyNamespace`: `config-`, `media-`, `messenger-`, `recovery-`, `__internal-` (последний зарезервирован для F-CRYPTO internal — `__internal-hkdf-device-salt-v1` и подобные). Попытка создать `KeyId` с unregistered префиксом — `IllegalArgumentException` в compile-test time через property test.
- **FR-011**: `KeyRotation` port MUST быть interface-only (real-impl = stub) с операциями `currentKeyId()`, `keyHistory()`, `rotateIdentityKey(reason)`, `revoke(keyId, reason)`. Stub returns `currentKeyId` = константа, `keyHistory` = пустой список, rotate/revoke выбрасывают `NotImplementedError`.
- **FR-012**: `KeyEscrow` port MUST быть interface-only (real-impl = stub) с операциями `export(passphrase) → EscrowBundle`, `restore(bundle, passphrase)`. Stub выбрасывает `NotImplementedError`.

**Adapters:**

- **FR-013**: Реальный libsodium adapter MUST реализовать `AeadCipher` через **XChaCha20-Poly1305**, `AsymmetricCrypto` через **X25519** (ECDH) + **Ed25519** (signing), `KeyDerivation` через **HKDF-SHA256**.
- **FR-014**: Libsodium binding MUST использовать `ionspin/kotlin-multiplatform-libsodium`. Research-фаза перед началом кода MUST подтвердить актуальность library (last release, open iOS issues). Если library dead — fallback на BouncyCastle (Android) + собственный cinterop libsodium (iOS), MUST быть задокументирован в research.md.
- **FR-015**: `SecureKeyStore` Android adapter MUST реализовать wrap pattern: AES-256-GCM ключ хранится в Android Keystore (TEE), Curve25519 private key serialized и зашифрован этим AES-ключом, blob лежит в app sandbox файле (`/data/data/<pkg>/files/keys/<keyId>.blob`).
- **FR-016**: Blob wire format MUST содержать `schemaVersion`, `algorithm`, `createdAt`, `retiredAt?` (nullable), `replacedBy?` (nullable), `wrappedKey: ByteArray`. **Сериализация — JSON** через `kotlinx.serialization.json` (clarification Q5: для KeyBlob'ов их единицы, размер не важен, debug-ability ценнее; CBOR не безопаснее JSON криптографически). Миграция на CBOR в будущем возможна через `schemaVersion` bump + migrator (rule 5).
- **FR-017**: FakeAdapter версии каждого port'а MUST быть deterministic и помечены `@VisibleForTesting`. FakeAeadCipher MAY использовать identity encryption (XOR / passthrough) — НЕ должно использовать реальную крипту, чтобы случайно не выглядеть «production-grade».
- **FR-018**: Build-config gate MUST исключать FakeAdapter из release builds. Detekt-правило MUST ловить `Fake*Cipher` / `FakeAsymmetricCrypto` / `FakeSecureKeyStore` import в любом файле под `app/src/main/` или `:app:release` source set.

**Validation set (replaces friend crypto review):**

- **FR-019**: `commonTest` MUST содержать RFC test vectors как KAT (Known Answer Tests):
  - RFC 7748 (X25519) — минимум 5 векторов.
  - RFC 8032 (Ed25519) — минимум 5 векторов (test cases из sect 7).
  - RFC 8439 (ChaCha20-Poly1305) — минимум 3 вектора (включая Appendix A.2 / A.5).
  - RFC 5869 (HKDF-SHA256) — минимум 3 вектора (Test Cases 1, 2, 3 из Appendix A).
- **FR-020**: `commonTest` MUST содержать subset Google Project Wycheproof test vectors (JSON-формат из https://github.com/google/wycheproof) — минимум: low-order points для X25519, malleable signatures для Ed25519, point-at-infinity rejection.
- **FR-021**: `commonTest` MUST содержать property tests (Kotest properties):
  - ECDH symmetry: `DH(a, B) == DH(b, A)`.
  - AEAD roundtrip: `decrypt(encrypt(p, k, n), k, n) == p`.
  - Sign/verify roundtrip: `verify(sign(m, priv), m, pub) == true`.
  - Tamper detection: flip 1 random bit в ciphertext → decrypt MUST fail.
  - Tamper detection signing: flip 1 bit в signature → verify MUST fail.
  - Nonce reuse rejection: attempt to encrypt twice с тем же nonce+key → adapter MUST raise.
- **FR-022**: KAT и property tests MUST исполняться на **обоих** `androidUnitTest` и `jvmTest` source sets с идентичными ожидаемыми байтами (cross-platform vector reuse).
- **FR-023**: Документ `docs/dev/crypto-review.md` MUST существовать и содержать: список primitives, industrial reference baseline (Signal/WhatsApp/WireGuard/age/Threema/Bitwarden Send), список passed RFC KAT с указанием конкретных RFC sections, описание property tests, явный cross-reference на SRV-CRYPTO-003 (paid audit milestone).
- **FR-024**: `docs/dev/crypto-review.md` MUST содержать прямой текст: «Friend crypto review снят как mandatory (решение 2026-06-17, mentor session). Заменён на measurable validation set ниже. Paid audit (7ASecurity / Radically Open Security) — milestone перед billing, см. SRV-CRYPTO-003».

**Storage & wire-format:**

- **FR-025**: Blob format для `SecureKeyStore` MUST карьер `schemaVersion` int field с первого коммита (CLAUDE.md rule 5).
- **FR-026**: `commonTest` MUST содержать backward-compat read test: положить blob `schemaVersion=1`, прочитать тем же кодом — pass. Имитировать будущую миграцию: добавить заглушку handler для `schemaVersion=2` — `v1` blob корректно мигрирует.
- **FR-027**: Все wire-format roundtrip тесты MUST включать assertion: identical bytes on Android и JVM (cross-platform).

**Constitution gates & rules:**

- **FR-028**: Никакой код в `core/crypto/api/` MUST НЕ импортировать `com.ionspin.kotlin.crypto.*` / `android.security.keystore.*` / любые SDK типы. Только Kotlin stdlib + coroutines + own types.
- **FR-029**: Никакой код в `core/crypto/api/` MUST НЕ содержать бизнес-логику (`ConfigCipher`, envelope encryption, media blob protocol) — это потребители.
- **FR-030**: DI wiring (Koin) MUST выбирать adapter по build variant: `debug` / `test` — Fake, `release` — Libsodium. Wiring код живёт в `app/` модуле, не в `core/crypto/`.

### Key Entities

- **`AeadCipher`**: domain port, операции encrypt/decrypt с AEAD. Не знает, какой алгоритм под капотом.
- **`AsymmetricCrypto`**: domain port, X25519 + Ed25519 операции. Не знает binding.
- **`KeyDerivation`**: domain port, HKDF-SHA256 семантика.
- **`RandomSource`**: domain port, cryptographically-secure random.
- **`SecureKeyStore`**: domain port для хранения wrapped private keys. На Android — wrap через TEE.
- **`KeyRotation`** (interface-only): port для будущей реальной ротации. Содержит `KeyId`, `RetiredKey`, `RotationReason` value types.
- **`KeyEscrow`** (interface-only): port для будущего 2FA admin migration. Содержит `EscrowBundle` value type.
- **`KeyBlob`**: persisted wire-format value (`schemaVersion`, `algorithm`, `createdAt`, `retiredAt?`, `replacedBy?`, `wrappedKey`).
- **`TestVector`**: structure для RFC KAT — `input`, `expectedOutput`, `description`, `rfc-section`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: F-5 разработчик пишет `ConfigCipher` поверх `AeadCipher` + `AsymmetricCrypto` без единого импорта `com.ionspin.*` или `org.bouncycastle.*` в файле `ConfigCipher.kt`. Verification: grep по `core/config/cipher/ConfigCipher.kt` — нет таких импортов.
- **SC-002**: Все RFC KAT (X25519, Ed25519, ChaCha20-Poly1305, HKDF) проходят на CI **зелёным** на обоих source sets `androidUnitTest` + `jvmTest`. Verification: GitHub Actions / GitLab CI badge.
- **SC-003**: Все Google Wycheproof subset (low-order, malleable signatures, point-at-infinity) проходят. Verification: CI зелёный.
- **SC-004**: Property tests запускаются 1000 итераций без false positive / false negative. Verification: `./gradlew :core:crypto:test --tests *PropertyTest -PkotestPropertyIterations=1000`.
- **SC-005**: Замена implementation `AeadCipher` с ionspin на BouncyCastle (experimental dry-run) — все тесты потребителей (минимальный фейк F-5) проходят без code changes в потребительских файлах. Verification: branch experiment, не merge'нутый.
- **SC-006**: `core/crypto/build.gradle.kts` зависимости содержат только: kotlin-stdlib, kotlinx-coroutines, kotlinx-serialization, ionspin libsodium-kmp. Никаких launcher-модулей. Verification: `./gradlew :core:crypto:dependencies | grep "project:"` — пусто.
- **SC-007**: Cross-platform vector test проходит: identical bytes для encryption / signing на Android и JVM. Verification: специальный тест `CrossPlatformVectorParityTest`.
- **SC-008**: `SecureKeyStore` Android adapter сохраняет Curve25519 private key, после перезапуска приложения — `load(...)` возвращает оригинал. Verification: instrumentation test `SecureKeyStorePersistenceTest`.
- **SC-009**: Scan saved blob bytes на presence оригинального private key (raw bytes) → not found. Verification: тест `SecureKeyStoreNoPlaintextLeakTest`.
- **SC-010**: `docs/dev/crypto-review.md` создан, содержит все 4 уровня валидации + industrial baseline + cross-reference на SRV-CRYPTO-003. Verification: manual review + grep для ключевых строк.
- **SC-011**: Detekt-правило ловит `Fake*Cipher` import в `app/src/main/` — verification: namespaced unit test для Detekt rule.
- **SC-012**: F-CRYPTO merged до начала F-5 spec'ификации. Effort estimate: 2-3 weeks (Medium-Large).

## Assumptions

- **Владелец проекта (g.jeromwork) — solo-dev** без сети криптографов; friend crypto review недостижим. Это **зафиксировано** mentor session 2026-06-17 и обосновывает FR-023/024.
- **Industrial reference baseline** (Signal/WhatsApp/WireGuard/age/Threema/Bitwarden Send используют XChaCha20-Poly1305 + X25519 + Ed25519 + HKDF-SHA256) — accepted как достаточное обоснование выбора primitives. Если кто-то из этих систем перейдёт на другие primitives — переоценим.
- **`ionspin/kotlin-multiplatform-libsodium`** живой и поддерживает iOS targets на момент research-фазы. Если research показывает obsolescence — fallback план в FR-014.
- **F-4 (AuthProvider / Google Sign-In) НЕ требуется** как dependency. KeyDerivation использует device-local random salt; Google UID подмешивается позже через rotation, когда F-4 активируется в S-5+. Это совместимо с deferred-cloud architecture (memory `project_deferred_cloud_architecture`).
- **Android Keystore TEE доступен** на target-устройствах (Android 6+). Rooted devices / устройства без TEE — fail-fast с ясным сообщением, не silent degradation.
- **Текущий target SDK** = 35+ (соответствует launcher'у), Compose Multiplatform может потребоваться позже, но F-CRYPTO **не зависит** от Compose.
- **License model** при будущем extract'е — Apache 2.0 (Kerckhoffs's principle: opensource крипты не уменьшает безопасность).
- **Кросс-семейная privacy invariant**: даже maintainer F-CRYPTO **не имеет** доступа к плэйнтекст ключам пользователей. F-CRYPTO — это library code, не runtime service.
- **Никакой telemetry / analytics / network calls** в `core/crypto/`. Module работает offline.

## Local Test Path *(mandatory)*

- **Emulator / device**: pure JVM unit tests для основной массы (commonTest + jvmTest); instrumentation тесты для `SecureKeyStore` Android adapter — на `pixel_5_api_34` через skill `android-emulator`.
- **Fake adapters used**:
  - `FakeAeadCipher` (identity / passthrough — НЕ real crypto).
  - `FakeAsymmetricCrypto` (deterministic key generation для тестов).
  - `FakeKeyDerivation` (echo back).
  - `FakeRandomSource` (seeded deterministic for property tests).
  - `FakeSecureKeyStore` (in-memory HashMap).
  - `KeyRotation` / `KeyEscrow` — stub (no fake, real-impl = stub).
- **Fixtures / seed data**:
  - `core/crypto/src/commonTest/resources/rfc-test-vectors/rfc7748-x25519.json`, `rfc8032-ed25519.json`, `rfc8439-chacha20-poly1305.json`, `rfc5869-hkdf.json`.
  - `core/crypto/src/commonTest/resources/wycheproof-subset/x25519_test.json`, `ed25519_test.json` (downloaded subset from Wycheproof).
  - `core/crypto/src/commonTest/resources/cross-platform-vectors/encryption-roundtrip-v1.json` — owner-генерированные векторы для cross-platform parity.
- **Verification commands**:
  - `./gradlew :core:crypto:jvmTest` — все common + jvm тесты.
  - `./gradlew :core:crypto:testDebugUnitTest` — Android unit-tests (без эмулятора).
  - `./gradlew :core:crypto:connectedDebugAndroidTest` — instrumentation `SecureKeyStore` тесты на эмуляторе.
  - `./gradlew :core:crypto:dependencies` — verify no launcher-module dependencies (SC-006).
- **Cannot-test-locally gaps**:
  - **iOS target build** — закладывается в `build.gradle.kts`, но реальная компиляция требует macOS host. Inline `// TODO(physical-mac): iOS build verification` в `iosMain/`.
  - **OEM-specific Android Keystore quirks** (Xiaomi MIUI sometimes drops Keystore aliases on aggressive cleanup) — inline `// TODO(physical-device): Xiaomi MIUI key persistence soak test`.
  - **TEE attestation** — verification что ключ **реально** в TEE, а не в software fallback — `KeyInfo.isInsideSecureHardware`. Это runtime check, есть в FR, но проверить на эмуляторе нельзя — эмулятор не имеет real TEE. Inline `// TODO(physical-device): TEE attestation verification on real device`.

## AI Affordance *(mandatory)*

**`no AI affordance — internal capability only`.**

Reason: F-CRYPTO — это **криптографический foundation**. AI agent НЕ должен **никогда** иметь возможность дёргать crypto operations напрямую (это нарушает изоляцию — AI с capability `encrypt(...)` может exfiltrate plaintext через side channel). Domain-level capabilities (`pairWithSenior`, `sendCommandToBabushka`, etc.) — это потребители F-CRYPTO, и они описывают AI affordance в **своих** спеках (S-1..S-8), не здесь.

Конкретно: capability registry foundation (F-2, отложен в Phase 4+) НЕ будет включать `aeadEncrypt`/`x25519DeriveSharedSecret`. F-CRYPTO остаётся **infrastructure layer**.

## OEM Matrix *(mandatory if feature touches device behavior)*

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---------------|------------------|-------------------------|---------------------|
| Stock Android (Pixel) | baseline | — | emulator `pixel_5_api_34` |
| Samsung One UI | Keystore aliases preserved at parity with stock. **StrongBox** (если есть Samsung Knox) даёт сильнее TEE — F-CRYPTO use это автоматически через `setIsStrongBoxBacked(true)` fallback. | Capability detection при `SecureKeyStore.init()` — если StrongBox недоступен, fall back на обычный TEE | TODO(physical-device): Samsung Knox StrongBox attestation test |
| Xiaomi MIUI | Aggressive app cleanup может **в редких случаях** инвалидировать Keystore aliases при «очистке памяти». | Adapter MUST detect alias-missing exception и rethrow as `KeystoreInvalidatedException`; UX в потребителях показывает re-pairing flow | TODO(physical-device): MIUI cleanup soak test |
| Huawei EMUI | Без Google Mobile Services, но Keystore — standard Android. | Adapter работает нормально; **iCloud-style backup ключей не работает**, но это OK для F-CRYPTO scope. | TODO(physical-device): Huawei P-series Keystore test |
| Android TV / Google TV | Часто **нет lock screen** — биометрия / device PIN могут быть disabled. | F-CRYPTO admin keys НЕ требуют `setUserAuthenticationRequired(true)` — поэтому работает; senior config decryption работает в любом случае. | TODO(physical-device): ATV emulator `tv_4k_api_34` |

---

## Решения, зафиксированные на mentor-сессии 2026-06-17

Сводка для cross-reference (полный текст — в `docs/product/roadmap.md` §F-CRYPTO + memory `project_f_crypto_decisions.md`):

1. **Friend crypto review снят** — заменён на RFC KAT + Wycheproof + property tests + industrial reference. Платный аудит в SRV-CRYPTO-003 как milestone перед billing.
2. **Wrap pattern** для Curve25519 в Android Keystore TEE — pattern Signal/WhatsApp/Bitwarden.
3. **KeyRotation / KeyEscrow** — только interface-only ports, real-impl = stub. Реальная ротация в SRV-CRYPTO-004 / SRV-CRYPTO-005.
4. **iOS targets** декларированы day 1, CI отложен.
5. **Library extraction policy**: внутри launcher-репо до 2-го потребителя, inline TODO, Apache 2.0 при extract.
6. **F-4 dependency снят** — F-CRYPTO работает без Google UID; device-local salt; UID подмешивается через rotation в S-5+.
7. **libsodium binding**: `ionspin/kotlin-multiplatform-libsodium` (приоритет), fallback BouncyCastle + cinterop.
8. **Cross-platform test vector reuse** обязателен (один JSON в `commonTest` исполняется на Android + JVM).

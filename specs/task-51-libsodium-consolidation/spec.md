# Feature Specification: libsodium consolidation (TASK-51)

**Feature Branch**: `task-51-libsodium-consolidation`
**Created**: 2026-06-26
**Status**: Draft
**Input**: User description: "TASK-51 — libsodium consolidation: миграция crypto-стэка проекта launcher на единую KMP-foundation, устранение архитектурного crash'а через JNA eager-bind, разделение слоёв 'crypto primitives' и 'spec 011 pairing wire format'."

> **Note on spec timing**: эта спека создаётся **пост-фактум** после Phase 1 имплементации (gradle cleanup commit `1e6be2e` 2026-06-26). Обоснование: TASK-51 эволюционировал от узкого bug-репорта до архитектурного рефакторинга в процессе разбора; появились конкурирующие варианты решения (deep vs narrow adapter pattern), один из которых — one-way door (CLAUDE.md rule 3). Spec нужен ДО выбора варианта, чтобы зафиксировать grey zones, прогнать через clarify + Constitution Check. Phase 1 (gradle stripping) — операция, **общая** для обоих вариантов, и поэтому не блокирует spec authoring.

## Clarifications

### 2026-06-26 — Pre-plan clarification pass (7 questions resolved)

Все 7 grey zones закрыты после mentor-style разбора + 4 параллельных research-spike'ов индустриальных паттернов (Signal, Tink, libsignal, WhatsApp, Telegram, Matrix, Now in Android, KMP community, OWASP MSTG, NIST SP 800-57).

| # | Question | Resolution | Rationale |
|---|----------|------------|-----------|
| Q1 | Глубокая миграция (deep) vs adapter pattern (narrow) | **Deep migration** — 15 spec 011 wire-format типов переезжают в новый пакет `cryptokit.pairing.api.*`, 5 криптопортов удалены. ~25 call-sites переписываются. | Owner-mandate «делаем от и до, не возвращаться». Чище архитектурно: разделение примитивов и pairing wire-format. |
| Q2 | Backward-compat persisted Keystore ключи | **Force re-pair (Pattern 4)** — старые aliases (`spec011.encryption.own`, `spec011.signing.own`) удаляются при первом запуске после upgrade, user проходит pairing flow заново. | Pre-release фаза, нет production users. F-5b recovery flow делает re-pair painless. Pattern 4 = zero migration code, zero migration bugs (CLAUDE.md rule 4 MVA). Inline-TODO к TASK-6 Root Key Hierarchy как exit ramp на Pattern 5 (derive-from-root). |
| Q3 | Error handling: Outcome vs throws | **Везде throws** + универсальный `try/catch` наверху + auto re-throw `CancellationException` для structured concurrency. Старый `Outcome<T, CryptoError>` удаляется. | Owner-mandate простоты. Один отлов, один логгер, один UI error handler. CancellationException re-throw — критично для coroutines (без него scope не отменяется при close экрана). |
| Q4 | DI modules: один vs два | **Один module** (`cryptokit module` объединяет `F016CryptoModule` + старый `CryptoModule`). | Не плодить infrastructure. После Q1 deep migration старые порты удалены — нет смысла в двух modules. |
| Q6 | Hash для display fingerprint | **`java.security.MessageDigest.getInstance("SHA-256")`** inline в `Spec011SmokeDebugActivity`. Никакого `HashFunction` port. | CLAUDE.md rule 4 — port ради одной debug-фичи = premature abstraction. SHA-256 — industry default для fingerprints (SSH, Bitcoin, PGP V5). |
| Q7 | `AndroidKeystoreSecureKeystore` (300+ строк, lazysodium-зависимый) | **Удалить целиком**. Использовать `cryptokit.crypto.api.SecureKeyStore` (expect/actual в androidMain — уже реализует generic wrap-pattern). | Готовый аналог покрывает функционал. CLAUDE.md rule 4 — не переписывать, удалить. Сохраняет multi-platform readiness (iOS Keychain в iosMain без рефакторинга consumer'ов). |
| Namespace | `family.*` (historical) vs new | **`cryptokit`** — переименование с первого коммита имплементации. | Owner-decision: `family.*` historical artifact, не доменное слово; `cryptokit` отражает что это **обёртка над crypto-примитивами, переиспользуемая для всей экосистемы** (launcher, messenger, medical, photo). |

### Architectural decisions derived from clarifications

1. **Package structure** (single repo, `core/crypto/` module):
   ```
   cryptokit/
   ├── crypto/                      # примитивы
   │   ├── api/                     # interfaces (throws CryptoException)
   │   ├── libsodium/               # ionspin-based implementation
   │   └── exception/               # CryptoException hierarchy
   └── pairing/                     # spec 011 wire-format types
       └── api/                     # DeviceIdentity, EncryptedEnvelope, Recipient, ...
   ```

2. **Не вводятся новые abstractions**: HashFunction port — нет; Outcome<T,E> sealed hierarchy — нет; PrivateKey/SigningPrivateKey opaque types — удаляются (orphan по разведке).

3. **Inline-TODO к TASK-6** (Root Key Hierarchy parking-lot): где старые aliases удаляются, оставить комментарий:
   ```kotlin
   // TODO(post-task-6): replace nuke-and-re-pair with derive-from-root after Root Key Hierarchy lands
   ```

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Конечный пользователь может привязать админ-устройство (Priority: P1)

**Описание**: Помощник (assisting family member / IT-support) на устройстве primary user'а нажимает «Привязать админа» в Settings. Открывается экран pairing'а с QR-кодом. Помощник делает фото QR кодом со своего устройства. Pairing-link создаётся, обе стороны подключены.

**Почему P1**: главная функциональная цель TASK-51 — устранить crash'ующий блокер для TASK-8 (Admin App + QR Pairing). Без этой User Story TASK-51 не имеет смысла.

**Independent Test**: на Xiaomi 11T (arm64 устройство) запустить PairingActivity — оно открывается без `UnsatisfiedLinkError`. Сценарий E2E зависит также от TASK-8 (admin-app stub), но **stand-alone** проверка «PairingActivity не крашит» достаточна для подтверждения P1.

**Acceptance Scenarios**:

1. **Given** Xiaomi 11T (arm64, Android 11, MIUI), новая установка APK от ветки `task-51-libsodium-consolidation`, **When** пользователь открывает PairingActivity (через intent или Settings menu), **Then** экран отображается без crash'а; QR-код viewable.
2. **Given** эмулятор `pixel_5_api_34` (x86_64), новая установка APK, **When** PairingActivity launched, **Then** экран отображается без crash'а.
3. **Given** Xiaomi 11T, **When** запускается smoke-roundtrip в `Spec011SmokeDebugActivity` (encrypt + decrypt 32-byte payload), **Then** результат == исходные байты, no exceptions.

---

### User Story 2 — Разработчик имеет один источник правды для crypto API (Priority: P1)

**Описание**: Разработчик, открывающий проект впервые (or future-AI agent debugging crypto issue), видит **один** канонический crypto-пакет (`cryptokit.crypto.api.*`) с **одной** реализацией (`cryptokit.crypto.libsodium.*` через ionspin). Нет дублирования, нет «которое из двух?». Импорты однозначны.

**Почему P1**: целостность mental model — критерий «закрыли тему, не возвращаемся» (per owner-mandate 2026-06-26). Без этого следующая фича на crypto будет блокирована «что использовать?»

**Independent Test**: grep по проекту по запрещённым символам (lazysodium / JNA / SodiumAndroid / GoTeRL package) даёт 0 матчей в production-коде.

**Acceptance Scenarios**:

1. **Given** клонированный репозиторий после merge TASK-51, **When** делаю `grep -r "com.goterl"` в production sources (`app/src/main/`, `core/src/{common,android}Main/`, исключая `specs/`, `docs/`), **Then** 0 матчей.
2. **Given** свежий чекаут, **When** `grep -r "lazysodium"` в production sources, **Then** 0 матчей.
3. **Given** репозиторий, **When** `grep -r "JNA\.register\|SodiumAndroid"` в production sources, **Then** 0 матчей.

---

### User Story 3 — Будущая платформенная миграция (iOS / Android TV) не требует переписывания crypto (Priority: P2)

**Описание**: Когда TASK-26 (iOS Admin Preset) дойдёт до имплементации, разработчик не должен переписывать crypto-слой. Существующий `cryptokit.crypto.api.*` в commonMain автоматически работает на iOS через ionspin's iOS-binding. Adapter pattern в `androidMain` (Android Keystore) дублируется в `iosMain` (iOS Keychain) — но это **iOS-локальная работа**, не общая.

**Почему P2**: важно архитектурно, но не блокирует MVP-демо (iOS — отдельный spec через 6+ месяцев). Это страховочная гарантия что мы не накапливаем платформ-debt.

**Independent Test**: статический анализ — все production crypto-классы располагаются в `commonMain` (за исключением Android-specific Keystore adapter, который **expect/actual**).

**Acceptance Scenarios**:

1. **Given** репозиторий после TASK-51, **When** анализирую расположение файлов `family.crypto.*`, **Then** API + libsodium-implementation в `commonMain`, Android Keystore — только в `androidMain` через `expect/actual SecureKeyStore`.
2. **Given** репозиторий, **When** проверяю что в `commonMain` нет Android-specific imports (`android.*`, `androidx.*`) в crypto-стэке, **Then** clean.

---

### User Story 4 — APK становится меньше + cold start не вырастает (Priority: P2)

**Описание**: end-user (пожилой человек) с устройством на 16-32 GB storage получает обновление APK, которое **меньше** предыдущей версии. Cold start приложения (от tap до первого экрана) **не вырос** относительно baseline.

**Почему P2**: важно для senior-launcher УХ (target-segment чувствителен к performance), но не блокирует TASK-8.

**Independent Test**: измерение через `./gradlew :app:assembleMockBackendDebug` + `stat -c%s` для APK + `adb shell am start -W -n ... `для cold start.

**Acceptance Scenarios**:

1. **Given** baseline APK (до Phase 1, commit `d5763d6`), **When** собираю APK после TASK-51 merge, **Then** размер уменьшился на ≥3 МБ (выкинули JNA ~5 МБ AAR).
2. **Given** Xiaomi 11T, **When** замеряю cold start через `adb shell am start -W`, **Then** TotalTime ≤ baseline 1330 ms из T061 TASK-7.

---

### Edge Cases

- **What happens when**: `family.crypto.SecureKeyStore.load(keyId)` возвращает `null` (ключ не найден, например после clear-data)?
  → `PairingCryptoCoordinator` должен сгенерировать новый ключ и сохранить (idempotent ensure-keys). Никаких exceptions для caller'а.
- **What happens when**: миграция выпустила и есть **persisted ключи** под старыми aliases (`spec011.encryption.own`, `spec011.signing.own`) на устройстве с предыдущей версии APK?
  → Нужно решить через clarify: backward-compat read из старых aliases? Или forced re-pair (старые pairings invalidated)?
- **How does system handle**: ошибка JNI link на `cryptokit.crypto.libsodium.*` (если ionspin тоже зачем-то eager-bind'нет)?
  → ionspin lazy-bind by design; теоретически невозможно. Но если случится — fail-fast в `Application.onCreate` через `assertNoFakeCryptoInRelease` или новый health check.
- **What happens when**: при тестах используется FakeAeadCipher из старого пакета (`com.launcher.fake.crypto`)?
  → Должен быть удалён вместе со старой стопкой; тесты переезжают на `cryptokit.crypto.fake.*`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST полностью устранить crash `UnsatisfiedLinkError: crypto_core_ristretto255_add` на любых устройствах (arm64, armeabi-v7a, x86, x86_64) при открытии PairingActivity.

- **FR-002**: System MUST убрать `lazysodium-android` (vendor: com.goterl) и `net.java.dev.jna:jna` из всех transitive и direct зависимостей. `./gradlew :app:dependencies` не показывает их.

- **FR-003**: System MUST оставить ровно одну реализацию `libsodium.so` в выпущенном APK на каждый ABI. Костыль `packaging.jniLibs.pickFirsts` в `app/build.gradle.kts` удалён.

- **FR-004**: System MUST сохранить функциональную совместимость spec 011 pairing flow — все wire-format типы (DeviceIdentity, EncryptedEnvelope, Recipient, etc.) **читаются** и **пишутся** идентично pre-TASK-51 (byte-equal для same input, same `schemaVersion` остаётся 1).
  - **Serialization compatibility note**: при namespace rename (`family.* → cryptokit.*`, `com.launcher.api.crypto.* → cryptokit.pairing.api.*`) Kotlin-сериализатор не должен использовать FQN класса как key. Все wire-format типы обязаны иметь явные `@SerialName("DeviceIdentity")` / `@SerialName("EncryptedEnvelope")` / etc. — verify в `/speckit.plan` Phase research grep по существующему коду.

- **FR-005**: System MUST применить **force re-pair migration strategy** (Q2 resolved): при первом запуске после upgrade старые Keystore aliases (`spec011.encryption.own`, `spec011.signing.own`) удаляются; user видит pairing flow заново. Backward-compat read **не поддерживается**. Inline-TODO в коде wipe-логики: `// TODO(post-task-6): replace nuke-and-re-pair with derive-from-root after Root Key Hierarchy lands`.

- **FR-006**: System MUST использовать `cryptokit.crypto.api.*` (KMP commonMain) как **единственный** crypto-API в production-коде. Параллельный `com.launcher.api.crypto.*` **полностью удалён** (Q1 deep migration). Все ~25 call-sites переписаны на новые импорты. Spec 011 wire-format типы (15 типов: `DeviceIdentity`, `EncryptedEnvelope`, `Recipient`, `DeviceIdentityRepository`, `EncryptedMediaStorage`, `RecipientResolver`, etc.) переезжают в `cryptokit.pairing.api.*` (новый pakage в том же `core/crypto/` модуле).

- **FR-007**: System MUST провести fitness-tests (Konsist + custom) которые **запрещают** возврат старых паттернов:
  - запрет import'а `com.goterl.*` в production-коде
  - запрет import'а `net.java.dev.jna.*` в production-коде
  - запрет вызовов `JNA.register(...)`
  - запрет import'а `com.launcher.api.crypto.*` (старая стопка полностью удалена per Q1)
  - запрет import'а `family.crypto.*` (Q-namespace: namespace переименован в `cryptokit.*`)

- **FR-008**: System MUST переписать `PairingCryptoCoordinator` на новый стэк (`cryptokit.crypto.api.AsymmetricCrypto.generateX25519KeyPair / generateEd25519KeyPair` + `cryptokit.crypto.api.SecureKeyStore.store(keyId, bytes)`), убрав вызовы deprecated `SecureKeystore.generateAndStoreEncryption(alias)` / `loadEncryption(alias)`.

- **FR-009**: System MUST применить **uniform throws pattern** (Q3 resolved): все APIs (примитивы + pairing-domain operations) бросают `cryptokit.crypto.exception.CryptoException` при ошибках. Старый `Outcome<T, CryptoError>` удалён. Adapter-обёртки автоматически re-throw `CancellationException` для совместимости с coroutine structured concurrency.

- **FR-010**: System MUST **удалить** `AndroidKeystoreSecureKeystore` целиком (Q7 resolved). Использовать `cryptokit.crypto.api.SecureKeyStore` (expect/actual класс из spec 016 `:core:crypto`) — он уже реализует generic wrap-pattern для произвольных ByteArray.

- **FR-014** *(new from Q6)*: System MUST использовать `java.security.MessageDigest.getInstance("SHA-256")` inline в `Spec011SmokeDebugActivity` для display fingerprint'а pub-ключа. Старый `HashFunction` port из `com.launcher.api.crypto` удалён. Никакого нового `HashFunction` port в `cryptokit.crypto.api` не вводится.

- **FR-015** *(new from Q4)*: System MUST объединить DI bindings в **один Koin module** (`cryptokitModule` или эквивалент). Старые `CryptoModule.kt` и `F016CryptoModule.kt` сливаются в один. PairingModule остаётся отдельным (он DI module для pairing-flow, не crypto).

- **FR-016** *(new from Namespace clarification)*: System MUST переименовать существующий namespace `family.*` (наследие spec 016) в `cryptokit.*` в первом implementation commit'е. Это включает:
  - `core/crypto/src/commonMain/kotlin/family/crypto/api/` → `core/crypto/src/commonMain/kotlin/cryptokit/crypto/api/`
  - все `import family.crypto.*` → `import cryptokit.crypto.*` в потребителях (`:core:keys`, `:app`)
  - JVM-tests в `core/crypto/src/jvmTest/kotlin/family/crypto/kat/` → `cryptokit/crypto/kat/`
  - exception package `family.crypto.exception` → `cryptokit.crypto.exception`
  - stubs `family.crypto.stubs` → `cryptokit.crypto.stubs`
  - DI module name update: `f016CryptoModule` → `cryptokitModule`

- **FR-011**: System MUST оставить все юнит-тесты + Robolectric тесты зелёными после миграции. Снижение coverage недопустимо.

- **FR-012**: System MUST сохранить размер APK не больше pre-TASK-51 baseline (commit `d5763d6`) и **уменьшить** его на ≥3 МБ (выкидывание JNA AAR).

- **FR-013**: System MUST сохранить cold start приложения не хуже pre-TASK-51 baseline (1260-1330 ms на Xiaomi 11T из TASK-7 T061).

- **FR-017** *(new from dev-experience + failure-recovery checklists)*: System MUST реализовать **uniform CryptoException logging contract**. На верхнем уровне (universal try/catch в pairing-side операциях) при `catch (e: CryptoException)` эмиттится Logcat запись с:
  - **Tag**: `cryptokit`
  - **Level**: `Log.W` (warn) или `Log.E` (error) в зависимости от типа exception (см. FR-018 иерархию).
  - **Required fields**: `operation` (имя функции, например `pairingCoordinator.publishOwnIdentity`), `exceptionClass` (e.g. `KeyStoreException`), `messageHash` (SHA-256(exception.message).take(8), чтобы dedup в logs не раскрывая PII).
  - **Forbidden fields**: raw key bytes, hex >8 bytes, device IDs, user PII, Firestore document IDs. Это enforce'ится Konsist fitness-rule (FR-007 расширение).
  - **CancellationException** re-thrown БЕЗ логирования (это control-flow, не error).

- **FR-018** *(new from failure-recovery checklist CHK017)*: System MUST организовать `CryptoException` в **sealed hierarchy** с явными подклассами для observability/categorization:
  ```kotlin
  sealed class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause) {
      class AeadException(...) : CryptoException(...)         // encrypt/decrypt failures
      class KeyStoreException(...) : CryptoException(...)     // SecureKeyStore failures
      class KeyDerivationException(...) : CryptoException(...) // HKDF/Argon2 failures
      class NativeLinkException(...) : CryptoException(...)   // JNI/libsodium link failures
      class SerializationException(...) : CryptoException(...) // wire-format read/write failures
  }
  ```
  Минимум — эти 5 подклассов. Существующий `family.crypto.exception.CryptoException` уже может содержать часть иерархии — verify в `/speckit.plan` и дополнить недостающие.

### Key Entities

- **CryptoPort family** (`cryptokit.crypto.api.AeadCipher`, `AsymmetricCrypto`, `KeyDerivation`, `SecureKeyStore`, `RandomSource`): single source of truth для криптографических примитивов. KMP `commonMain`.
- **Spec 011 wire format** (`DeviceIdentity`, `DeviceId`, `EncryptedEnvelope`, `Recipient`, `EncryptedMediaStorage`): pairing-domain типы с явным `schemaVersion`. Их пакет — **точка решения через clarify** (остаются в `com.launcher.api.crypto`? Переезжают в `cryptokit.pairing.api.*`?).
- **PairingCryptoCoordinator**: orchestrator pairing-flow: генерация per-device ключей + публикация `DeviceIdentity` в Firestore. **Сильно переписывается** в любом варианте.
- **Old stack ghost**: 22 файла в `com.launcher.api.crypto/` + 5 файлов `Libsodium*.kt` + `LibsodiumProvider`, `AndroidKeystoreSecureKeystore` — либо удалены (вариант 1), либо часть удалена + часть обёрнута (вариант 2).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: PairingActivity открывается на Xiaomi 11T (arm64, `17f33878`) без `UnsatisfiedLinkError`. Verifies FR-001.
- **SC-002 [backlog]**: Spec011SmokeDebugActivity round-trip encrypt/decrypt проходит на Xiaomi 11T без exceptions. Подтверждает что crypto-стэк функционально работает (USv2 + FR-008).
- **SC-003**: `grep -r "com.goterl" app/src/main/ core/src/{common,android}Main/` = 0 матчей. Verifies FR-002 + FR-007.
- **SC-004**: `grep -r "lazysodium\|JNA\.register\|SodiumAndroid" app/src/main/ core/src/` = 0 матчей в production. Verifies FR-007.
- **SC-005**: `./gradlew :app:dependencies | grep -E "lazysodium|net.java.dev.jna"` = empty. Verifies FR-002.
- **SC-006 [backlog]**: `./gradlew :app:assembleMockBackendDebug` + `./gradlew test` = BUILD SUCCESSFUL, все unit + Robolectric тесты зелёные. Verifies FR-011.
- **SC-007**: Fitness-test (Konsist) `NoLazysodiumInProductionTest` + (если вариант 1) `NoLegacyComLauncherCryptoTest` зелёные. Verifies FR-007.
- **SC-008 [backlog]**: APK размер `app-mockBackend-debug.apk` после TASK-51 меньше baseline (commit `d5763d6`) на ≥3 МБ. Verifies FR-012.
- **SC-009 [backlog]**: Cold start `adb shell am start -W -n com.launcher.app/.HomeActivity` TotalTime ≤ 1330 ms на Xiaomi 11T. Verifies FR-013.
- **SC-010**: В `app/build.gradle.kts` отсутствует блок `packaging.jniLibs.pickFirsts` для libsodium.so. Verifies FR-003.
- **SC-011 [backlog]**: На Xiaomi 11T с persisted pairing-данными от pre-TASK-51 версии — после установки нового APK старые Keystore aliases удалены, при открытии PairingActivity user видит pairing flow с QR-кодом (force re-pair per Q2). Verifies FR-005.
- **SC-012**: Namespace `family.*` полностью отсутствует в `core/crypto/`, `core/keys/`, `app/`, `core/` после миграции. `grep -r "family\.crypto" --include="*.kt"` = 0 матчей. Verifies FR-016.
- **SC-013**: `EnvelopeConfigCipherRoundtripTest` (existing golden-vector test) проходит **байт-в-байт** после namespace rename. Verify через `./gradlew :core:keys:jvmTest --tests "*EnvelopeConfigCipherRoundtripTest"`. Verifies FR-004 (serialization compatibility).
- **SC-014**: Logcat tag `cryptokit` появляется при искусственно вызванной CryptoException (negative test). Verify через `adb logcat -s cryptokit` + ручное triggering неправильного ключа. Verifies FR-017.

## Assumptions

- **Устройство Xiaomi 11T (`17f33878`)** — единственное доступное physical-device для верификации (per memory `reference_testing_environment.md`). Samsung, Huawei OEM проверки откладываются на отдельный gate (deferred-physical-device).
- **Эмулятор `pixel_5_api_34`** — основной emulator-target (per `skill android-emulator`).
- **ionspin libsodium-kmp 0.9.5** покрывает все нужные нашему pairing-коду криптопримитивы: X25519 keypair, Ed25519 keypair, Ed25519 sign/verify, XChaCha20-Poly1305 AEAD, BLAKE2b (если нужен). Подтверждено в spec 016 (`:core:crypto` Done).
- **Ristretto255** **не нужен** нашему коду (grep по Kotlin-сорсам = 0 матчей). Crash был артефактом JNA eager-bind стороннего lazysodium, не функциональной потребности.
- **Spec 011 wire-format** (DeviceIdentity, EncryptedEnvelope) **не меняется** в этой задаче. `schemaVersion: 1` остаётся; backward compat read не требуется (только rebuild).
- **MLS-протокол** (TASK-42 parking-lot) — не часть TASK-51. Добавится как отдельная библиотека рядом с libsodium, не вместо.
- **iOS поддержка** (TASK-26) — не часть TASK-51, но архитектурно подготавливается (crypto в commonMain).

## Local Test Path *(mandatory)*

- **Emulator / device**:
  - `pixel_5_api_34` (x86_64) via skill `android-emulator` — для unit+integration smoke
  - Xiaomi 11T (arm64, `17f33878`, MIUI V125, Android 11) — для physical PairingActivity smoke + cold start measurement
- **Fake adapters used**:
  - `cryptokit.crypto.fake.FakeAeadCipher` (XOR stub, deterministic)
  - `cryptokit.crypto.fake.FakeAsymmetricCrypto` (seeded)
  - `cryptokit.crypto.fake.FakeRandomSource`
  - `cryptokit.crypto.fake.FakeSecureKeyStore` (in-memory HashMap)
  - Test fakes для spec 011 wire-format adapters (`FakeDeviceIdentityRepository`, `FakeEncryptedMediaStorage`) переезжают в `core/crypto/src/commonTest/kotlin/cryptokit/pairing/fake/` (per Q1 deep migration + clarification log Section 5). Старые `com.launcher.fake.crypto.*` удалены полностью.
- **Fixtures / seed data**:
  - `core/src/commonTest/.../EnvelopeConfigCipherRoundtripTest` (golden vectors для backward compat)
  - `app/src/main/java/com/launcher/app/debug/Spec011SmokeDebugActivity.kt` (in-app smoke flow)
- **Verification commands**:
  - `./gradlew :app:assembleMockBackendDebug` — собрать APK
  - `./gradlew test` — все unit + Robolectric
  - `./gradlew :core:testMockBackendDebugUnitTest --tests "*PairingCryptoCoordinatorTest"` — focused pairing tests
  - `./gradlew :app:assembleMockBackendDebug && adb install -r app/build/outputs/apk/mockBackend/debug/app-mockBackend-debug.apk`
  - `adb shell am start -n com.launcher.app.mock/com.launcher.app.ui.pairing.PairingActivity` — manual smoke
  - `adb shell am start -W -n com.launcher.app/.HomeActivity` — cold start measurement
- **Cannot-test-locally gaps**:
  - Samsung One UI behavior на pairing flow — TODO(physical-device): нет Samsung устройства, см. TASK-55 verification aggregator.
  - Huawei EMUI без GMS behavior — TODO(physical-device): нет Huawei устройства.
  - Real 2-device pairing handshake — зависит от TASK-8 (admin app stub); проверка только когда TASK-8 in flight.

## AI Affordance *(mandatory)*

**no AI affordance — internal capability only**.

Это инфраструктурный рефакторинг crypto-слоя. AI-агент не «использует libsodium» как domain-action; libsodium — internal capability обеспечивающая шифрование на уровне адаптеров. Никакой AI exposure не появляется и не исчезает в результате этой миграции.

Capability Registry readiness **не затрагивается**: те же abstract'ные verbs (`encryptForRecipient`, `decryptOwnConfig`, `establishPairing`) останутся неизменно exposable в будущем, независимо от того под капотом lazysodium или ionspin. Pairing-side capabilities (publishOwnIdentity, fetchPeerIdentity) тоже остаются domain ports, без vendor leakage.

## OEM Matrix *(mandatory if feature touches device behavior)*

Эта спека **не меняет** OEM-specific behavior — она устраняет crash, который **проявлялся одинаково** на всех OEM с arm64 (и эмуляторах с x86_64). После миграции expected behavior — единообразное **отсутствие crash'а** на всех OEM.

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---------------|------------------|-------------------------|---------------------|
| Stock Android (Pixel) | baseline — crash не проявляется на emulator x86_64? см. spec 011 testing | — | emulator `pixel_5_api_34` (SC-001 scenario 2) |
| Xiaomi MIUI | Crash проявляется на Xiaomi 11T (`17f33878`) arm64 + MIUI V125 + Android 11. Воспроизведено 2026-06-26 со stacktrace. | После TASK-51: ionspin lazy-bind не пытается линковать недостающие символы → crash исчезает. | Xiaomi 11T smoke (SC-001 scenario 1) |
| Samsung One UI | Crash ожидаемо проявляется (arm64 + JNA eager-bind = тот же сценарий) | После TASK-51: тот же fix. **Verification TBD** — нет Samsung устройства. | TODO(physical-device) → TASK-55 |
| Huawei EMUI | Crash ожидаемо проявляется. | После TASK-51: тот же fix. **Verification TBD** — нет Huawei устройства. | TODO(physical-device) → TASK-55 |

**Note**: эта спека **не вводит** новой OEM-divergence. Если в будущем найдётся OEM где ionspin lazy-bind ведёт себя иначе — это будет отдельный bug, не часть TASK-51 ownership.

## Clarifications resolution log

Все grey zones закрыты в **§ Clarifications** в начале документа (2026-06-26 pre-plan clarification pass). Распространены в FR-005..FR-016 и SC-001..SC-012 выше.

**Test fakes destination** (был открытый вопрос 5): 8 файлов `com.launcher.fake.crypto.*` (FakeAeadCipher, FakeAsymmetricCrypto, FakeSecureKeystore, FakeDigitalSignature, FakeHashFunction, FakeDeviceIdentityRepository, FakeEncryptedMediaStorage, FakeRecipientResolver) — **удалить целиком** (логичное следствие Q1 deep migration + Q3 throws + Q6 SHA-256 inline). Новые fakes для `cryptokit.*` создаются в `core/crypto/src/commonTest/kotlin/cryptokit/crypto/fake/` и `cryptokit/pairing/fake/`.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Спека описывает миграцию crypto-слоя проекта с библиотеки `lazysodium-android` на `ionspin libsodium-kmp`, устранение crash'а PairingActivity на arm64 (`UnsatisfiedLinkError: crypto_core_ristretto255_add`) и переименование namespace `family.*` → `cryptokit.*`. Crash происходит из-за **JNA eager-bind** в `SodiumAndroid.<init>` — не нашей бизнес-логики (наш код ristretto255 не вызывает); ionspin использует JNI lazy-bind и не падает.

**Конкретика, которую стоит запомнить:**
- **Namespace**: `cryptokit.crypto.api` (примитивы) + `cryptokit.pairing.api` (wire-format spec 011) + `cryptokit.crypto.libsodium` (impl), всё в `core/crypto/` модуле — один репо.
- **Стиль ошибок (FR-009)**: везде `throws CryptoException` + universal `try/catch` наверху + auto re-throw `CancellationException`. Старый `Outcome<T, CryptoError>` удалён.
- **Иерархия исключений (FR-018)**: sealed `CryptoException` с 5 подклассами — `AeadException`, `KeyStoreException`, `KeyDerivationException`, `NativeLinkException`, `SerializationException`.
- **Логирование (FR-017)**: Logcat tag = `cryptokit`, поля `operation/exceptionClass/messageHash(SHA-256, 8 байт)`. Запрещено: raw bytes, hex >8B, device IDs.
- **Удаляется**: 22 файла в `com.launcher.api.crypto/`, 7 файлов адаптеров (`Libsodium*.kt` + `AndroidKeystoreSecureKeystore` + `LibsodiumProvider`), 8 файлов старых fakes, `lazysodium` + `jna` из gradle.
- **Migration strategy (FR-005)**: force re-pair — старые Keystore aliases `spec011.encryption.own/signing.own` удаляются при первом запуске, user проходит pairing flow заново. Backward-compat read **не поддерживается** (pre-release, нет users).
- **Метрики (SC-008, SC-009)**: APK уменьшится на ≥3 МБ; cold start ≤ 1330 ms на Xiaomi 11T (baseline TASK-7 T061).
- **Checklists score**: meta-minimization 13/13 ✓, domain-isolation 16/16 ✓, modular-delivery 18/18 ✓, security 17/24 (7 N/A, 0 fail), dev-experience 19/22, остальные с ожидаемыми N/A для refactor scope.

**На что смотреть с осторожностью:**
- **Serialization compatibility (FR-004)**: при namespace rename все wire-format типы (`DeviceIdentity`, `EncryptedEnvelope`, `Recipient`) должны иметь explicit `@SerialName` — без этого Kotlin-сериализатор использует FQN и сломает byte-equal roundtrip. Verify в `/speckit.plan` перед миграцией.
- **CancellationException re-throw**: в universal `try/catch` обязательно re-throw, иначе coroutine cancellation сломается (silent bug, не падает при тестах, проявляется в UI hang при закрытии экрана).
- **Force re-pair как one-way door**: если в release окажется production user — потеря pairing-state. Mitigated тем что pre-release + Xiaomi 11T единственное устройство. Exit ramp — TASK-6 Root Key Hierarchy (derive-from-root pattern).
- **OEM verification**: Samsung One UI / Huawei EMUI не тестируются (нет устройств) → `TODO(physical-device)` → TASK-55 deferred-aggregator.

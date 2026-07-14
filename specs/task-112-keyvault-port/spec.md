# Feature Specification: KeyVault Port Boundary — Cross-Platform Cryptographic Contract

**Feature Branch**: `task-112-keyvault-port`
**Created**: 2026-07-14
**Status**: Draft
**Input**: [TASK-112 backlog card](../../backlog/tasks/task-112%20-%20Decision-Cross-platform-IdentityVault.md) — «Decision: KeyVault port boundary — operation-on-vault + narrow export». Decision block revised 2026-07-14 (Session 6). Owner sign-off C1 passphrase + pluggable RecoveryStrategy, C2 remove exportDerivedKey, C3 libsodium-kmp software crypto.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Настройки пользователя защищены на диске (Priority: P1)

Бабушка настраивает лаунчер: выбирает пресет, включает плитки контактов, задаёт крупный шрифт. Всё это сохраняется в конфиг-файл. Если её телефон украдут и попытаются прочитать этот файл (через ADB, USB debugger, root, forensic tool) — файл выглядит как случайные байты. Открыть его можно только через `KeyVault.aeadOpen(Purpose.CONFIG, ...)` с валидным `root_key`, который живёт в Android Keystore под pin-lock устройства.

**Why this priority**: без этого весь launcher теряет privacy — фотки контактов бабушки, её маршрут скорой помощи, привычки использования утекают при потере устройства. Это foundation'ное свойство продукта.

**Independent Test**: `./gradlew :core:keys:test --tests *ConfigCipher2Test` + integration test — записываем зашифрованный конфиг, читаем через adb pull, проверяем что файл entropy-high (не plaintext), после `keyVault.aeadOpen(...)` восстанавливается исходный JSON.

**Acceptance Scenarios**:

1. **Given** пользователь настроил пресет и записал конфиг, **When** файл извлекается напрямую с устройства (`adb pull /data/data/.../config.enc`), **Then** содержимое — случайные байты, JSON не читается.
2. **Given** зашифрованный `Ciphertext(purpose=Purpose.CONFIG)`, **When** вызывается `keyVault.aeadOpen(Purpose.RECOVERY_BLOB, ciphertext, aad)`, **Then** бросается `VaultException.WrongPurpose` (второй линия защиты через purpose_id в blob header).
3. **Given** атакующий подменил один байт в зашифрованном blob'е (флип), **When** вызывается `aeadOpen`, **Then** бросается `VaultException.TamperDetected` (AEAD MAC).

---

### User Story 2 — Recovery mechanism plug-in-play (Priority: P1)

Сегодня бабушка восстанавливает данные через свой пароль (TASK-6). Через год мы решаем добавить 12-слов recovery для «продвинутых» users → нам НЕ нужно переписывать `KeyVault` — только добавить новый `Bip39Recovery` adapter к `RecoveryStrategy` port'у. Ещё через год — 2FA через SMS. То же самое.

**Why this priority**: recovery — самая эволюционирующая часть крипто-стека (BIP39 сейчас, hardware key через 2 года, social recovery потом). Если `KeyVault` знает про пароль напрямую — каждое расширение = переписывание. Если знает только про port `RecoveryStrategy` — расширение = additive adapter.

**Independent Test**: `./gradlew :core:keys:test --tests *RecoveryStrategyTest` — Fake `TestRecoveryStrategy` (детерминистический root) успешно unlocks vault; последующий `aeadOpen` восстанавливает данные, зашифрованные при setup через `PassphraseRecovery`.

**Acceptance Scenarios**:

1. **Given** vault initialized с `PassphraseRecovery("Люба1948", salt, Argon2Params.V1)`, **When** новое устройство создаёт vault и вызывает `unlock(PassphraseRecovery("Люба1948", sameSalt, Argon2Params.V1))`, **Then** vault разблокирован, `aeadOpen` возвращает исходные данные (regression от TASK-6).
2. **Given** vault initialized через `PassphraseRecovery`, **When** вызывается `unlock(TestRecoveryStrategy(fakeRootBytes))` с несовпадающим root, **Then** `aeadOpen` бросает `VaultException.TamperDetected` (MAC не сходится).
3. **Given** будущий разработчик добавляет `Bip39Recovery(words: List<String>) : RecoveryStrategy`, **When** прогоняется существующий тест `PassphraseRecoveryTest`, **Then** он проходит без изменений — новый adapter не ломает существующие.

---

### User Story 3 — Cross-platform data portability (Priority: P2)

В будущем (TASK-26) выйдет iOS-версия admin-приложения. Родственник настроил бабушке лаунчер через Android → тот же родственник открывает admin приложение на своём iPhone → видит те же настройки бабушки, читает те же зашифрованные конфиги. Никакой ручной конвертации, никаких «сначала выгрузите на Android потом импортируйте на iOS».

**Why this priority**: cross-platform data portability — обещание нашего продукта. Если крипто-байты не совпадают между платформами — весь sync ломается.

**Independent Test**: `./gradlew :core:keys:test --tests *CrossPlatformVectorTest` — фиксированный `root_key` + фиксированный plaintext + фиксированный AAD → ожидаемый ciphertext bytes (fixture в `core/keys/src/commonTest/resources/vectors/v1.json`). Тест прогоняется на JVM (native libsodium) и на Android (native libsodium через JNI) — результат byte-equal.

**Acceptance Scenarios**:

1. **Given** vault с root_key `0x00..00` (32 нулевых байта), plaintext `"hello"`, AAD `canonicalAad("ns1", 1, 1)`, **When** вызывается `aeadSeal(Purpose.CONFIG, plaintext, aad)`, **Then** ciphertext bytes соответствуют fixture `vectors/v1.json → seal_zeros_hello_ns1`.
2. **Given** зашифрованные данные с Android (реальное устройство), **When** те же bytes подаются в `FakeKeyVault` с тем же root_key на JVM тесте, **Then** `aeadOpen` возвращает исходный plaintext.

---

### User Story 4 — Downstream features строят на едином контракте (Priority: P2)

Мессенджер (TASK-27), Фотоальбом (TASK-28) шифруют свои bucket'ы через тот же `KeyVault.aeadSeal(Purpose.CONFIG, ...)` (или новый Purpose добавляется в enum). Никакого дублирования крипто-кода в разных feature-модулях. Никаких «cipher.rs» / «encryption.kt» / «my-custom-aes-wrapper» в каждой feature.

**Why this priority**: без единого контракта каждая feature-команда изобретает свой crypto — гарантированные баги, дублированный код, невозможность аудита. С единым `KeyVault` port'ом — один audit-able entry point, обновления влияют на всех сразу.

**Independent Test**: fitness function `checklist-domain-isolation.md` — в `:core:keys` не должно быть imports Firebase / Android SDK; в feature-модулях (`:core:cloud`, `:core:push`) не должно быть прямых imports крипто-примитивов (libsodium, kotlinx-crypto), только через `KeyVault` port.

**Acceptance Scenarios**:

1. **Given** feature developer работает над TASK-27 (мессенджер), **When** они пишут код который шифрует контент сообщения, **Then** единственный доступный API — `keyVault.aeadSeal(Purpose.CONFIG, messageBytes, canonicalAad(...))`; прямой доступ к libsodium primitives невозможен (`internal` visibility).
2. **Given** review checklist `domain-isolation`, **When** прогоняется `./gradlew :core:keys:detekt`, **Then** нет import statement'ов с `com.google.*`, `android.*`, `com.launcher.core.cloud.*` в `:core:keys`.

---

### Edge Cases

- **Recovery attempt with wrong passphrase** → `PassphraseRecovery.deriveRoot()` возвращает «неправильный» root_key (математически всё равно валидный, просто другой) → последующий `aeadOpen` бросает `VaultException.TamperDetected`. UX слой в TASK-6 (`RecoveryFallbackScreen`) считает 3 неверные попытки и предлагает fallback.
- **Vault не unlock'нут (нет root_key) перед вызовом aeadSeal** → бросается `VaultException.VaultLocked`. Feature code обязан обработать это состояние (перед первым запуском или после logout).
- **Blob header format_version неизвестен** (например будущая версия открывает старую blob v3, поле format_version=3 не поддерживается v1-only реализацией) → `VaultException.UnsupportedFormatVersion(version=3)`. Migration path через версионирование blob header.
- **AAD contents не совпадают** (namespace_id изменён, попытка replay из другого namespace) → `VaultException.TamperDetected`. Защита от rollback / namespace substitution attack.
- **Android Keystore недоступен** (кастомная ROM без StrongBox, эмулятор без TEE) → `VaultException.HardwareBackedKeystoreUnavailable`. Fallback: обычная software-encrypted storage через libsodium wrap.
- **openmls в будущем захочет external signature key** → exit ramp: additive `Purpose.External(labelBytes)` sealed variant + новый метод `exportDerivedKey`. openmls `SignatureKeyPair::from_raw` verified (2026-07-14) как compatible.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `KeyVault` port MUST provide operations: `unlock(RecoveryStrategy)`, `wipe()`, `aeadSeal(Purpose, ByteArray, Aad) → Ciphertext`, `aeadOpen(Purpose, Ciphertext, Aad) → ByteArray`, `mac(Purpose, ByteArray) → MacTag`, `verifyMac(Purpose, ByteArray, MacTag) → Boolean`, `sign(ByteArray) → Signature`, `verify(PublicIdentity, ByteArray, Signature) → Boolean`, `publicIdentity() → PublicIdentity`.
- **FR-002**: `Purpose` enum MUST include exactly `CONFIG` и `RECOVERY_BLOB` для MVP. Дополнительные variants добавляются additively через sealed class migration (exit ramp).
- **FR-003**: `Ciphertext.bytes` MUST начинаться с blob header: `magic (2b = 0x4B 0x56) || format_version (1b = 0x01) || purpose_id (2b BE) || key_epoch (2b BE = 0x0000) || nonce (24b random CSPRNG) || ciphertext_payload || MAC (16b)`.
- **FR-004**: `Aad.bytes` MUST быть length-prefixed canonical layout: `namespace_id_len (2b BE) || namespace_id_bytes || schema_version (2b BE) || blob_version (2b BE)`.
- **FR-005**: `RecoveryStrategy` port MUST допускать pluggable adapters. Добавление нового adapter (BIP39, 2FA) MUST быть additive без изменения `KeyVault` port'а или существующих callers.
- **FR-006**: `PassphraseRecovery` adapter MUST реализовать `deriveRoot() = Argon2id(passphrase, salt, params)` с параметрами `Argon2Params.V1(memory=64MiB, iterations=3, parallelism=1)` — matches Bitwarden default 2023 + OWASP recommendation. Salt derivation (Session 7 Q-B, Bitwarden pattern):
  - GMS device: `salt = HKDF(googleUid.toByteArray(UTF_8), info="salt-v1", 16 bytes)` — deterministic, no separate storage needed.
  - No-GMS device: `salt = deviceRandomSalt (16 bytes CSPRNG stored device-only in Android Keystore at first setup)`.
  - Adapter accepts `IdentityHint` sealed class with variants `GoogleAccount(googleUid)` / `NoGmsDevice(deviceRandomSalt)`.
- **FR-006b**: `PassphraseRecovery` MUST include passphrase validation via known-plaintext blob (Session 7 Q-D, D1 decision): adapter internally seals `"vault-init-v1"` on first setup, verifies on subsequent unlock — wrong passphrase → `VaultException.RecoveryFailed` (not silent TamperDetected on first data access).
- **FR-006c**: `KeyVault.wipe()` MUST atomically clear `root_key` from Android Keystore AND in-memory state (Session 7 Q-C, C1 decision). All subsequent operations MUST throw `VaultException.NoRootKey` until next `unlock(...)`. Wipe MUST be idempotent (safe to call when vault already wiped).
- **FR-007**: `:core:keys` domain layer MUST NOT импортировать `com.google.*`, `android.*`, `com.launcher.core.cloud.*`, `com.launcher.core.push.*`. Enforcement: detekt fitness rule или lint rule.
- **FR-008**: `RootKey` public class MUST быть понижен до `internal class family.keys.impl.RootKey`. Public `RootKey(val bytes: ByteArray)` accessor MUST быть удалён. `KeyRegistry.derive(...)` public API MUST быть понижен до `internal helper` внутри `AndroidKeyVault` impl.
- **FR-009**: Existing call sites `ConfigCipher2` и `EnvelopeStorage` MUST быть мигрированы на `keyVault.aeadSeal(Purpose.CONFIG, plaintext, canonicalAad(...))` и `keyVault.aeadOpen(Purpose.CONFIG, ciphertext, canonicalAad(...))`. Прямое использование `DerivedKey.bytes` MUST быть удалено.
- **FR-010**: `AndroidKeyVault` adapter MUST хранить `root_key` at rest ТОЛЬКО в Android Keystore (StrongBox where available). ВСЕ операции (derive, aeadSeal, aeadOpen, mac, sign) MUST использовать libsodium-kmp software layer. Android Keystore hardware AEAD/HMAC/signing MUST НЕ использоваться.
- **FR-011**: Cross-platform test vectors MUST совпадать byte-for-byte между Android (native libsodium через JNI) и JVM tests (native libsodium). Fixture: `core/keys/src/commonTest/resources/vectors/v1.json`. Break test vectors = major format_version bump.
- **FR-012**: Sealed `VaultException` hierarchy MUST покрывать: `VaultLocked`, `WrongPurpose(expected, actual)`, `TamperDetected`, `UnsupportedFormatVersion(version)`, `NoRootKey`, `HardwareBackedKeystoreUnavailable`, `RecoveryFailed`. Все методы `KeyVault` MUST быть annotated `@Throws(VaultException::class)`.
- **FR-013**: `exportDerivedKey` метод MUST НЕ существовать в `KeyVault` port. Обоснование: TASK-100 «new device = new MLS identity» model — external MLS signature key derivation не требуется для MVP. Exit ramp через additive `Purpose.External(labelBytes)` variant при Phase-3+ (openmls `SignatureKeyPair::from_raw` verified compatible 2026-07-14).
- **FR-014**: `FakeKeyVault` implementation MUST существовать в `commonTest` для использования downstream feature tests (rule 6 mock-first). Fake MUST быть детерминистическим (fixed root seed) для test vector reproducibility.
- **FR-015**: Migration MUST быть phased (5 фаз): port + fakes → Android adapter → migrate call sites → downgrade RootKey/KeyRegistry → cleanup. Каждая фаза = отдельный commit со зелёными тестами.

### Key Entities

- **`KeyVault`** (port, `com.launcher.core.keys.api`) — крипто-контракт для всего домена. Operation-on-vault паттерн: ключи не покидают vault. Includes `wipe()` для logout cascade (Session 7 Q-C).
- **`Purpose`** (enum registry) — CONFIG + RECOVERY_BLOB, каждый с attributes (`algorithm`, `exportable`, `rotationPolicy`).
- **`Ciphertext`** (newtype) — зашифрованный blob с header (magic + format_version + purpose_id + key_epoch + nonce + payload + MAC).
- **`MacTag`** (newtype) — Blake2b-256 MAC output, purpose-tagged.
- **`Signature`** (newtype) — Ed25519 signature, identity-scoped (не purpose-scoped).
- **`PublicIdentity`** (newtype) — 32-byte Ed25519 pubkey. Safe to expose (rule 13 zero-knowledge server verifies через это).
- **`Aad`** (value class) — length-prefixed canonical associated data.
- **`RecoveryStrategy`** (port) — pluggable адаптеры для восстановления `root_key`. Includes `verifyUnlock` hook (Session 7 Q-D).
- **`PassphraseRecovery`** (adapter, MVP) — Argon2id V1 + Bitwarden-pattern salt derivation via `IdentityHint`.
- **`IdentityHint`** (sealed class) — `GoogleAccount(googleUid)` / `NoGmsDevice(deviceRandomSalt)`. Determines salt derivation path.
- **`Argon2Params`** (frozen data class) — `V1(memory=64MiB, iterations=3, parallelism=1)`. Versioned. Matches Bitwarden default 2023 + OWASP.
- **`VaultException`** (sealed hierarchy) — все ошибки KeyVault.
- **`AndroidKeyVault`** (adapter, `com.launcher.core.keys.impl` androidMain) — Android platform-specific. Keystore для `root_key`, libsodium-kmp для всего остального.
- **`FakeKeyVault`** (adapter, `com.launcher.core.keys` commonTest) — in-memory для тестов downstream features.
- **`RootKey`** (internal class, `com.launcher.core.keys.impl`) — 32 байта root material. НЕ пересекает port boundary.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: Все existing call sites `DerivedKey.bytes` в domain коде мигрированы на `KeyVault.aeadSeal/aeadOpen`. Grep по `:core:` не находит `DerivedKey.bytes` в public/domain коде (только `internal` helper внутри `AndroidKeyVault`).
- ~~**SC-002 [backlog]**: Existing user data backward compat~~ **DROPPED (Session 7)** — no TASK-6 users exist yet, backward-compat не требуется. Все данные будут создаваться через новый KeyVault code path.
- **SC-003 [backlog]**: Recovery flow через passphrase работает (Setup → Entry → Fallback screens). GMS path (Google account) через `PassphraseRecovery(GoogleAccount(uid))`, no-GMS path через `PassphraseRecovery(NoGmsDevice(deviceSalt))`. Оба regression'ятся на эмуляторе.
- **SC-004**: Cross-platform test vectors проходят byte-equal на Android (`connectedAndroidTest`) + JVM (`commonTest`) + Fake. Fixture в `vectors/v1.json`, ≥5 vector cases (seal/open/mac/sign/edge).
- **SC-005 [backlog]**: TASK-6 flow (Setup → Entry → Fallback) не сломан — существующий `RecoveryUiTest` проходит без изменений.
- **SC-006**: Fitness function `checklist-domain-isolation` проходит: `:core:keys` не имеет imports `com.google.*`, `android.*`, feature-модулей.
- **SC-007**: AAD tamper attack test — модифицированный AAD (изменённый namespace или blob_version) → `VaultException.TamperDetected`.
- **SC-008**: Purpose enforcement test — `Ciphertext(purpose=Purpose.CONFIG)`, `aeadOpen(Purpose.RECOVERY_BLOB, ct, aad)` → `VaultException.WrongPurpose`.
- **SC-009 [backlog]**: Downstream TASK-124 (openmls integration) не требует изменений `KeyVault` port. Verified через inspection of TASK-124 design — openmls storage может использовать `keyVault.aeadSeal(Purpose.CONFIG, ...)` для MLS state at rest без external signature key export.
- **SC-010**: `RootKey` public API удалён — попытка использовать `RootKey(bytes: ByteArray)` из внешнего модуля даёт compile error (visibility `internal`).
- **SC-011**: `RecoveryStrategy` extensibility validated — mock adapter `TestRecoveryStrategy` работает без изменений `KeyVault` interface (Fake test).
- **SC-012 [backlog]**: Cascade wipe работает — after `keyVault.wipe()`, попытка `aeadOpen` на ранее сохранённом ciphertext → `VaultException.NoRootKey`. Test coverage: `KeyVaultLifecycleTest.wipe_thenAccessData_throwsNoRootKey`.
- **SC-013**: Passphrase validation в `PassphraseRecovery` работает — wrong passphrase → `RecoveryFailed` (не silent тихий фейл на первом access). Test coverage: `PassphraseRecoveryValidationTest`.
- **SC-014**: Salt derivation детерминистична — тот же googleUid + info="salt-v1" → тот же 16-byte salt (cross-run, cross-device). Test coverage в `PassphraseRecoveryTest`.

## Assumptions

- TASK-6 (Root Key Hierarchy + Owner Recovery) — no production users yet (Session 7 owner confirmation), backward-compat не требуется. TASK-112 может свободно менять wire format / package naming / migration path. TASK-6 UI flow (Setup → Entry → Fallback screens) сохраняется и переиспользуется через `PassphraseRecovery` adapter.
- TASK-100 (History backup strategy) остаётся at «no history backup MVP» — новое устройство после recovery = новая MLS identity. Влияет на решение убрать `exportDerivedKey(MLS_SIGNATURE)`.
- Cross-platform target: Android now, iOS at TASK-26 (parking-lot), HarmonyOS/desktop гипотетически позже.
- Threat model: family segment (senior + relatives). Clinic/B2B (TASK-34) остаётся Phase-5 parking-lot 24+ мес.
- libsodium-kmp API поддерживает: XChaCha20-Poly1305 AEAD, Blake2b MAC, Ed25519 sign/verify, `crypto_kdf` для key derivation, Argon2id (`crypto_pwhash`) для passphrase KDF. **TODO(phase-1)**: verified API surface before phase-2 start.
- TASK-113 (Outcome<T,E> refactor) остаётся orthogonal. TASK-112 использует sealed `VaultException` per Session 2 Q3; TASK-113 aligns остальные ports separately.
- No user-visible UI changes в TASK-112. Все UI работает как раньше (TASK-6 recovery flow сохранён byte-for-byte).

## Local Test Path *(mandatory)*

- **Emulator / device**: `pixel_5_api_34` через skill `android-emulator` для `connectedAndroidTest` (cross-platform vector verification, ConfigCipher2 migration integration). JVM tests достаточны для port contract / fake adapter / VaultException / blob header parsing.
- **Fake adapters used**: `FakeKeyVault` (deterministic in-memory), `TestRecoveryStrategy` (mock RecoveryStrategy для vault init), возможно `TestKeystoreAdapter` (для isolating AndroidKeyVault от реального Android Keystore в unit тестах).
- **Fixtures / seed data**: `core/keys/src/commonTest/resources/vectors/v1.json` (cross-platform test vectors), `core/keys/src/commonTest/resources/fixtures/task-6-legacy-blob.bin` (pre-migration ciphertext для backward-compat check).
- **Verification command**:
  - `./gradlew :core:keys:test` — все JVM unit тесты (port contract, fake adapter, VaultException coverage, blob header parsing, RecoveryStrategy plug-in).
  - `./gradlew :core:keys:connectedAndroidTest` — Android integration (real libsodium через JNI, real Android Keystore, cross-platform vector match).
  - `./gradlew :app:testMockBackendDebugUnitTest` — regression от TASK-6 (setup → entry → fallback UI flow с новым KeyVault под капотом).
  - `./gradlew :core:keys:detekt` — fitness function (rule 1 domain isolation).
- **Cannot-test-locally gaps**: none. Все крипто — pure libsodium software layer, cross-platform determinism гарантирует что behavior одинаковый на любом Android device. Специфичное железо не требуется.

## AI Affordance *(mandatory)*

`KeyVault` operations exposable to AI по принципу capability-registry-readiness:

- **Exposable capabilities**: `encryptForPurpose(purposeId: String, plaintext: ByteArray, aad: ByteArray) → CiphertextBytes`, `decryptForPurpose(purposeId: String, ciphertext: ByteArray, aad: ByteArray) → PlaintextBytes`, `signAsIdentity(message: ByteArray) → SignatureBytes`, `getPublicIdentity() → PublicIdentityBytes`. Domain verbs, NOT SDK-specific calls.
- **Required affordances on data**: AI agent can request encryption/decryption ON BEHALF OF the user but MUST NOT have access to `root_key`, `RecoveryStrategy` internals, or any private key material. AI работает только через public API `KeyVault`.
- **Provider-agnostic shape**: подтверждено — `KeyVault` port живёт в `family.keys.api`, не имеет imports Gemini / OpenAI / Claude / MCP типов. Rule 1 (domain isolation) satisfied.
- **Out of scope for this spec**: no provider implementation (Capability Registry Foundation — TASK-33 Phase-4), no LLM prompt design, no telemetry. AI feature ships in future spec, not TASK-112.

## OEM Matrix *(mandatory if feature touches device behavior)*

**Not applicable** — TASK-112 = pure Kotlin domain layer + libsodium-kmp software crypto. Android Keystore используется в minimal way (только хранение `root_key` at rest через `EncryptedSharedPreferences` / `MasterKey.Builder` — standard AndroidX Security-Crypto API, тестируется на любом эмуляторе, OEM divergence не наблюдается).

Никакого background work, permissions, launcher role, notifications, foreground service, content provider exposure — TASK-112 не касается ни одного из этих surface'ов.

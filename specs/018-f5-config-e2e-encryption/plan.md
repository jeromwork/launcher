# Implementation Plan: F-5 — Root Key Hierarchy + ConfigDocument Encryption + Recovery

**Branch**: `018-f5-config-e2e-encryption` | **Date**: 2026-06-19 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/018-f5-config-e2e-encryption/spec.md`

## Summary

F-5 строит **root key hierarchy** — единый главный ключ на identity (Google UID), под который шифруются все будущие DEKs (data-encryption keys) экосистемы family apps. Первый потребитель — `ConfigCipher`, который шифрует `ConfigDocument` перед отправкой в Firestore так, чтобы Firebase / Google / forensics видели только opaque bytes. Параллельно строится recovery flow: passphrase-encrypted root key хранится в Firestore `users/{uid}/recovery-key`, защищён Argon2id + XChaCha20-Poly1305 AEAD; passphrase вводится через Android Autofill `newPassword` / `password` hints, что активирует «Suggest strong password» chip Google Password Manager / Bitwarden / 1Password.

Технический подход: новый KMP module `core/keys/` (~10 файлов) поверх F-CRYPTO примитивов (`AeadCipher`, `SecureKeystore`, `KeyDerivation`) и F-4 `AuthProvider`. Domain ports (`KeyRegistry`, `RootKeyManager`, `IdentityProof`, `RecoveryKeyVault`, `ConfigCipher`) живут в `commonMain`, MVP-адаптеры (`GoogleSignInIdentityProof`, `FirestoreRecoveryKeyVault`) — в app-layer. Никаких libsodium-вызовов или Firestore-типов в domain. Future spec'и (S-2 X25519 pair-keys, S-5 photo, V-2 messenger) регистрируют свои DEKs в том же `KeyRegistry` additively, без breaking changes.

## Technical Context

**Language/Version**: Kotlin Multiplatform 2.0+ (соответствует существующим F-CRYPTO / F-4 модулям)
**Primary Dependencies**: ionspin libsodium-kmp (через F-CRYPTO, не напрямую); F-CRYPTO ports (`AeadCipher`, `AsymmetricCrypto`, `SecureKeystore`, `CryptoError`); F-4 ports (`AuthProvider`, `AuthIdentity`); Firebase Android SDK (Firestore + Auth) — **только в app-слое** через `FirestoreRecoveryKeyVault`; AndroidX Credentials (autofill metadata)
**Storage**: Android Keystore TEE (root key + wrapped DEKs через `SecureKeystore` adapter); Firestore `users/{uid}/recovery-key` (encrypted RecoveryVaultBlob); Firestore `users/{uid}/config` (SealedConfig — touched, но владение spec 008)
**Testing**: kotlinx-coroutines-test, Turbine для Flow tests, Firestore Emulator для integration tests, FakeAdapters (`FakeIdentityProof`, `FakeRecoveryKeyVault`, `FakeSecureKeystore` уже в F-CRYPTO); JVM unit tests как основной режим; smoke на `pixel_5_api_34` через skill `android-emulator` для Autofill UX
**Target Platform**: Android API 26+ (Autofill API, Google Password Manager); iOS targets declared in KMP module но inactive (consistent с F-CRYPTO decisions 2026-06-17)
**Project Type**: KMP library module (`core/keys/`) → consumed by `app/` module; app-слой добавляет Firebase-bound adapters и Compose UI экраны recovery
**Performance Goals**:
- SC-002: roundtrip seal→open для ≤10 KB config < 50 ms на эмуляторе `pixel_5_api_34`; Argon2id passphrase derivation (interactive params) < 500 ms
- SC-003: recovery end-to-end (Sign-In → fetch vault → unwrap → restore root → decrypt config) < 3 сек при network < 500ms
- Cold start не блокируется: `RootKeyManager` lazy-init при первом use, libsodium init не на critical path (per MIUI/Xiaomi mitigation в OEM matrix)
**Constraints**:
- 256 KB max ConfigDocument plaintext (FR-029); превышение → `CryptoError.ConfigTooLarge`
- Никакого plaintext passphrase в RAM longer than necessary (FR-013); CharArray обнуляется сразу после Argon2id derivation
- Никаких libsodium-импортов вне F-CRYPTO модуля (rule 2 ACL); никаких Firebase-импортов в `core/keys/commonMain` (rule 1)
- `allowBackup=false` на app-manifest — защита от Google Drive backup leak'а локального config cache
**Scale/Scope**: 100+ DEKs в `KeyRegistry` на одну identity без degradation (SC-008); multi-identity isolation per device — каждый Google UID имеет независимый namespace в Keystore (FR-031); ~10 файлов в `core/keys/commonMain` + ~6 в app-layer + ~6 test fixtures/files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Gate 1 — Architecture (Article III)**: PASS. Новый KMP module `core/keys/` с ports в `commonMain` и Android-specific implementations в `androidMain`. Никаких Firebase / libsodium / Android-system типов в `commonMain` API. App-слой owns Firestore adapter, отделённо.

**Gate 2 — Core / System Integration (Article IV)**: PASS. F-5 не вводит новых системных взаимодействий — переиспользует F-CRYPTO `SecureKeystore` для Android Keystore TEE и F-4 `AuthProvider` для Google Sign-In. `IdentityProof.requestSignIn()` делегируется в F-4 (FR-007).

**Gate 3 — Configuration (Article VII)**: N/A. F-5 — foundation infrastructure, не user-facing configuration. Никаких presets/templates/themes не вводит. Wire-format `RecoveryVaultBlob` и `SealedConfig` имеют `schemaVersion` от первого коммита (rule 5).

**Gate 4 — Required Context Review (Article XVI)**: PASS. Прочитаны: `.specify/memory/constitution.md` Articles I-XIX, `CLAUDE.md` 13 rules, spec 016 (F-CRYPTO) ports, spec 017 (F-4) ports, memory `project_f_crypto_decisions`, `project_deferred_cloud_architecture`, `project_config_cache_model`, `project_auth_provider_architecture`, документ `docs/product/future/multi-app-cohabitation.md` (decision 2026-06-19 о single signing key).

**Gate 5 — Accessibility (Article VIII)**: PASS для F-5 scope. Два Compose-экрана (passphrase setup / recovery) — Material 3 поля с `autofillHints`, tap target ≥ 56dp (senior-safe override), TalkBack labels. Детали валидируются через `checklist-accessibility` и `checklist-elderly-friendly` на tasks-этапе. UI не показывает passphrase plaintext (FR-013a) — это +1 для elderly UX.

**Gate 6 — Battery / Performance (Article IX)**: PASS. Argon2id interactive params (64MB / 3 iter / 1 par) запускается **только** при setup/recovery (редко), не на каждый push. AEAD seal/open для ≤10 KB config < 50ms. Lazy-init `RootKeyManager` не блокирует cold start. Нет background workers, broadcasts, polling.

**Gate 7 — Testing (Article XII)**: PASS. Каждый port имеет fake-adapter (`FakeIdentityProof`, `FakeRecoveryKeyVault`). Каждый wire-format (`SealedConfig`, `RecoveryVaultBlob`, `KeyRegistry` storage) имеет roundtrip-тест + backward-compat-тест с fixture v1 (rule 7). Integration test через Firestore Emulator. JVM unit tests как primary mode.

**Gate 8 — Simplicity (Article XI, CLAUDE.md rule 4)**: PASS с обоснованием. `KeyRegistry` как map name→WrappedDek вместо одного namespace = задел на multi-DEK consumers (S-2, S-5, V-2), которые **уже известны** (decisions 2026-06-15, 2026-06-19). Удаление этой абстракции означало бы rewrite, не addition (rule 4 test 1). `IdentityProof` / `RecoveryKeyVault` ports — single MVP implementation, но decision 2026-06-19 (deferred-cloud) явно ожидает `SmsIdentityProof` / `OwnServerRecoveryKeyVault` в future-specs (scenario 6 в spec.md). MVP architecture justified.

**Article XIV (Security & Privacy)** — central tenet feature. На этапе plan фиксируем threat model в spec.md (assumptions). MASVS-аудит запускается через `checklist-security` на tasks-этапе.

<!-- TODO(security-review-cadence H-5): review Argon2id params (memory/iterations/parallelism) каждые 2 года против OWASP актуальных рекомендаций; wire-format `kdfParams` поле уже поддерживает upgrade без breaking change (FR-032). Next review due: 2028-06. -->


**Constitution Check verdict**: PASS на всех 8 gates. Никаких violations требующих Complexity Tracking justification.

## Project Structure

### Documentation (this feature)

```text
specs/018-f5-config-e2e-encryption/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── key-registry-v1.md
│   ├── recovery-vault-v1.md
│   ├── sealed-config-v1.md
│   ├── identity-proof-v1.md
│   └── firestore-security-rules.md
├── checklists/          # Already exists
└── tasks.md             # Phase 2 output (NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
core/keys/                                          # NEW KMP module — lib-family-keys
├── build.gradle.kts                                # KMP config (android + jvm + iosX64/Arm64 targets, ionspin libsodium через core/crypto)
├── src/commonMain/kotlin/com/launcher/api/keys/
│   ├── api/
│   │   ├── KeyRegistry.kt                          # port: registerDek/getDek/hasDek + KeyRegistryError sealed type
│   │   ├── RootKeyManager.kt                       # port: getOrCreate/wipe + RootKeyError sealed type
│   │   ├── IdentityProof.kt                        # port: currentIdentity/requestSignIn + IdentityError sealed type
│   │   ├── RecoveryKeyVault.kt                     # port: fetchVault/storeVault/deleteVault + VaultError sealed type
│   │   ├── ConfigCipher.kt                         # port: seal(ConfigDocument)/open(SealedConfig)
│   │   ├── SealedConfig.kt                         # wire-format data class + schemaVersion + algorithm
│   │   ├── RecoveryVaultBlob.kt                    # wire-format data class + schemaVersion + algorithm + kdfParams
│   │   ├── PassphraseKdfParams.kt                  # value class — Argon2id (memory, iterations, parallelism, salt)
│   │   ├── KeyRegistryError.kt                     # sealed: NotFound | StorageFailure | UnknownDek
│   │   ├── VaultError.kt                           # sealed: Network | Unauthorized | NotFound | Conflict
│   │   └── RecoveryError.kt                        # sealed: WrongPassphrase | MalformedVault | NoVaultPresent
│   └── impl/
│       ├── AeadConfigCipherImpl.kt                 # XChaCha20-Poly1305 seal/open через F-CRYPTO AeadCipher
│       ├── RootKeyManagerImpl.kt                   # generate / unwrap from vault / wrap into Keystore через SecureKeystore
│       ├── KeyRegistryImpl.kt                      # name → WrappedDek storage поверх SecureKeystore
│       └── Argon2idPassphraseKdf.kt                # passphrase + salt + params → 32-byte key via libsodium через KeyDerivation port
├── src/commonTest/kotlin/com/launcher/api/keys/
│   ├── KeyRegistryTest.kt
│   ├── RootKeyManagerTest.kt
│   ├── ConfigCipherRoundtripTest.kt
│   ├── SealedConfigBackwardCompatTest.kt
│   ├── RecoveryVaultRoundtripTest.kt
│   ├── RecoveryVaultBackwardCompatTest.kt
│   ├── MultiIdentityIsolationTest.kt
│   └── fakes/
│       ├── FakeIdentityProof.kt
│       └── FakeRecoveryKeyVault.kt
├── src/commonTest/resources/fixtures/
│   ├── sealed-config-v1.json
│   ├── recovery-vault-v1.json
│   └── multi-dek-keyregistry-v1.json
└── src/androidMain/kotlin/com/launcher/api/keys/impl/
    └── AndroidKeyRegistryStorage.kt                # Android-specific persistent storage (если KMP common недостаточно)

app/src/main/kotlin/com/launcher/data/identity/
├── GoogleSignInIdentityProof.kt                    # wraps F-4 AuthProvider — единственное место, где AuthProvider встречается извне F-4
└── NoOpIdentityProof.kt                            # для non-GMS / Huawei — возвращает NoSupportedProvider error

app/src/main/kotlin/com/launcher/data/recovery/
├── FirestoreRecoveryKeyVault.kt                    # Firebase Firestore — единственное место с com.google.firebase.firestore.* в F-5 коде
└── NoOpRecoveryKeyVault.kt                         # для non-GMS devices

app/src/main/kotlin/com/launcher/ui/recovery/
├── RecoveryPassphraseSetupScreen.kt                # Compose: setup passphrase, autofillHints="newPassword", «Copy to clipboard» button
├── RecoveryPassphraseEntryScreen.kt                # Compose: entry на новом устройстве, autofillHints="password"
└── RecoveryViewModel.kt                            # state machine: Idle | SettingUp | Restoring | Error(...) | Done

app/src/main/kotlin/com/launcher/di/
└── KeysModule.kt                                   # DI wiring: GoogleSignInIdentityProof vs NoOp на основе F-4 AuthAdapterSelector

firebase/firestore.rules                            # обновляется: добавляются rules для users/{uid}/recovery-key и users/{uid}/config (если ещё не закрыты)
```

**Structure Decision**: F-5 = новый KMP module `core/keys/` (domain ports + impl + KMP commonMain тесты + Android-specific storage в androidMain) + app-layer adapters (Firebase Firestore, F-4 AuthProvider wrap) + три Compose-экрана recovery UI + DI wiring + Firestore Security Rules update. Iteration структуры: ~10 файлов в `core/keys/commonMain/kotlin` (5 ports + 5 impl/wire), ~10 файлов commonTest, ~6 файлов app-layer adapters, ~3 Compose screens, ~1 DI module, ~1 Firestore rules update.

## Complexity Tracking

*Все 8 constitutional gates passed. Таблица не требуется.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *(none)*  | —          | —                                    |

---

## Краткое резюме (для не-разработчика)

**Что внутри**:

- Решено: F-5 — это **новый отдельный модуль `core/keys/`** (примерно 10 файлов на Kotlin Multiplatform) + несколько файлов в app-слое для Firebase и UI экранов.
- Architecture check (8 проверок конституции) — **все пройдены**. Никаких архитектурных компромиссов / костылей не вводится.
- Перформанс цели зафиксированы: открытие конфига < 50 мс, recovery < 3 секунд, passphrase derivation < 500 мс.
- Никаких libsodium / Firebase типов в общей бизнес-логике — всё через port'ы (CLAUDE.md правила 1-2 соблюдены).
- Кросс-устройство и кросс-identity isolation: каждый Google UID имеет независимый namespace ключей в Keystore.

**Что в следующих файлах**:

- `research.md` — 7 технических решений (R-1..R-7): параметры Argon2id, как именно wrap'ить root key в Keystore, Firestore Security Rules, Android Autofill chip behavior, ClipboardManager auto-clear, identity isolation паттерн.
- `data-model.md` — список entities: RootKey, WrappedDek, KeyRegistry, RecoveryVaultBlob, SealedConfig + связи и lifecycle.
- `quickstart.md` — пошагово как разработчику собрать и запустить F-5 локально с эмулятором Firebase.
- `contracts/` — 5 файлов: формальные контракты port'ов и wire-format'ов.

**Следующий шаг (после plan'а)**: `/speckit.tasks` — декомпозиция в задачи (`tasks.md`).

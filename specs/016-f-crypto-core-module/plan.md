# Implementation Plan: F-CRYPTO — `core/crypto/` KMP module foundation

**Branch**: `j_f_crypto_core_module_17_06_26`
**Date**: 2026-06-17
**Spec**: [specs/016-f-crypto-core-module/spec.md](spec.md)
**Status**: Draft

## Summary

F-CRYPTO выделяет всю криптографию проекта в отдельный KMP common module `core/crypto/` (артефакт `lib-family-crypto`) **до** первого реального использования (F-5 ConfigCipher, спека 011 media blobs, будущий мессенджер, фото-приложение, EOS / Android TV / iOS launcher). Это реализация [CLAUDE.md rule 2](../../CLAUDE.md) (Anti-Corruption Layer для libsodium) + rule 6 (mock-first development).

**Technical approach** (high-level): Gradle subproject с KMP targets `androidMain` / `jvmMain` / `iosX64` / `iosArm64` / `iosSimulatorArm64`. Domain ports в `commonMain/api/` (7 portов), real adapters через `ionspin/kotlin-multiplatform-libsodium` в `libsodium/`, FakeAdapter в `fake/`. Wrap pattern для Curve25519 ключей через Android Keystore TEE (AES-256-GCM wrap). Validation set: RFC KAT + Google Wycheproof + Kotest property tests + cross-platform parity. iOS adapters — stub-screamers до первой iOS-фичи.

## Technical Context

- **Language/Version**: Kotlin 2.1+, Kotlin Multiplatform plugin.
- **Primary Dependencies**:
  - `com.ionspin.kotlin:libsodium-bindings:<latest>` (research-фаза подтверждает версию).
  - `kotlinx.serialization.json` (KeyBlob serialization).
  - `kotlinx.coroutines.core` (suspend functions в портах).
  - `kotlinx.datetime` (`Instant` для `KeyBlob.createdAt` без зависимости от `java.time`).
  - Android Keystore API (через `androidMain` adapter, не в domain).
- **Storage**: file-based blobs в `/data/data/<pkg>/files/keys/<keyId>.blob` (per device). No SQL, no DataStore, no SharedPreferences for crypto material.
- **Testing**: `kotlin-test` (common), Kotest `runners-junit5` + `property` для property-based tests, Roborazzi не применим (нет UI), Robolectric не нужен (Android Keystore требует instrumentation tests на эмуляторе).
- **Target Platform**:
  - Android API 23+ (Keystore requirement).
  - iOS 14+ (deferred, declared не активен в CI).
  - JVM 17+ (для тестов).
- **Project Type**: KMP library module (Gradle subproject).
- **Performance Goals**:
  - AEAD encrypt/decrypt 1 KB blob: < 5ms на Pixel 5.
  - X25519 keypair generation: < 50ms.
  - HKDF derive: < 10ms.
  - SecureKeyStore wrap+store: < 100ms (Keystore TEE round-trip).
  - SecureKeyStore load+unwrap: < 50ms.
  - **Не блокер performance** — F-CRYPTO operations редкие (один раз при первом запуске, потом по запросу). Performance мониторим в perf-checkpoint но не оптимизируем агрессивно.
- **Constraints**:
  - Offline by design (no network calls).
  - No telemetry / analytics.
  - No PII в exception messages — только category и keyId alias.
  - Cross-platform byte-level identical ciphertext (FR-022).
- **Scale/Scope**: 7 domain ports, ~20-30 Kotlin source files, ~50 test cases (RFC KAT) + ~10 property tests + ~5 instrumentation tests, 1 wire format (`KeyBlob`).

## Project Structure

### Documentation (this feature)

```text
specs/016-f-crypto-core-module/
├── plan.md              # This file
├── research.md          # ionspin actuality + iOS strategy + Wrap pattern detail
├── data-model.md        # KeyBlob structure, value types, port signatures (Kotlin pseudo)
├── quickstart.md        # Build/test commands, fixture layout, CI setup
├── contracts/
│   └── key-blob-v1.md   # KeyBlob wire format contract, schemaVersion=1
├── spec.md
├── scenarios.md         # 11 acceptance scenarios (plain text steps)
└── checklists/          # 14 quality checklists (all PASS)
```

### Source Code (repository root)

```text
core/
└── crypto/                          # New Gradle subproject :core:crypto
    ├── build.gradle.kts             # KMP targets + ionspin dependency
    ├── src/
    │   ├── commonMain/
    │   │   └── kotlin/family/crypto/
    │   │       ├── api/             # Domain ports (interfaces only)
    │   │       │   ├── AeadCipher.kt
    │   │       │   ├── AsymmetricCrypto.kt        # incl. sealCEK/unsealCEK
    │   │       │   ├── KeyDerivation.kt
    │   │       │   ├── RandomSource.kt
    │   │       │   ├── SecureKeyStore.kt           # expect class
    │   │       │   ├── KeyRotation.kt              # interface-only stub
    │   │       │   ├── KeyEscrow.kt                # interface-only stub
    │   │       │   └── values/
    │   │       │       ├── KeyId.kt                # value class w/ prefix validation
    │   │       │       ├── KeyNamespace.kt         # sealed class enumeration
    │   │       │       ├── KeyPair.kt
    │   │       │       ├── SharedSecret.kt
    │   │       │       ├── Signature.kt
    │   │       │       ├── SealedBlob.kt
    │   │       │       ├── KeyBlob.kt              # @Serializable wire format
    │   │       │       ├── RotationReason.kt
    │   │       │       ├── RetiredKey.kt
    │   │       │       └── EscrowBundle.kt
    │   │       ├── libsodium/        # Real adapters using ionspin
    │   │       │   ├── LibsodiumAeadCipher.kt
    │   │       │   ├── LibsodiumAsymmetricCrypto.kt
    │   │       │   ├── LibsodiumKeyDerivation.kt
    │   │       │   └── LibsodiumRandomSource.kt
    │   │       ├── stubs/            # Interface-only stub impls
    │   │       │   ├── StubKeyRotation.kt
    │   │       │   └── StubKeyEscrow.kt
    │   │       └── exception/
    │   │           └── CryptoException.kt          # sealed class hierarchy
    │   ├── androidMain/
    │   │   └── kotlin/family/crypto/
    │   │       └── SecureKeyStore.kt   # actual class via Android Keystore wrap
    │   ├── iosMain/
    │   │   └── kotlin/family/crypto/
    │   │       └── SecureKeyStore.kt   # actual class = stub-screamer
    │   ├── jvmMain/                    # for JVM tests
    │   │   └── kotlin/family/crypto/
    │   │       └── SecureKeyStore.kt   # actual class = in-memory (test-only)
    │   ├── commonTest/
    │   │   ├── kotlin/family/crypto/
    │   │   │   ├── fake/               # Fake adapters
    │   │   │   │   ├── FakeAeadCipher.kt
    │   │   │   │   ├── FakeAsymmetricCrypto.kt
    │   │   │   │   ├── FakeKeyDerivation.kt
    │   │   │   │   ├── FakeRandomSource.kt
    │   │   │   │   └── FakeSecureKeyStore.kt
    │   │   │   ├── kat/                # RFC Known Answer Tests
    │   │   │   │   ├── X25519KatTest.kt         # RFC 7748
    │   │   │   │   ├── Ed25519KatTest.kt        # RFC 8032
    │   │   │   │   ├── ChaCha20Poly1305KatTest.kt  # RFC 8439
    │   │   │   │   └── HkdfKatTest.kt           # RFC 5869
    │   │   │   ├── wycheproof/         # Google Wycheproof subset
    │   │   │   │   ├── WycheproofX25519Test.kt
    │   │   │   │   └── WycheproofEd25519Test.kt
    │   │   │   ├── property/           # Kotest property tests
    │   │   │   │   ├── AeadRoundtripPropertyTest.kt
    │   │   │   │   ├── EcdhSymmetryPropertyTest.kt
    │   │   │   │   ├── SignVerifyTamperPropertyTest.kt
    │   │   │   │   ├── NonceReuseRejectionPropertyTest.kt
    │   │   │   │   └── TamperDetectionPropertyTest.kt
    │   │   │   ├── crossplatform/
    │   │   │   │   └── CrossPlatformVectorParityTest.kt
    │   │   │   └── wireformat/
    │   │   │       ├── KeyBlobRoundtripTest.kt
    │   │   │       └── KeyBlobBackwardCompatReadTest.kt
    │   │   └── resources/
    │   │       ├── rfc-test-vectors/
    │   │       │   ├── rfc7748-x25519.json
    │   │       │   ├── rfc8032-ed25519.json
    │   │       │   ├── rfc8439-chacha20-poly1305.json
    │   │       │   └── rfc5869-hkdf.json
    │   │       ├── wycheproof-subset/
    │   │       │   ├── x25519_test.json
    │   │       │   └── ed25519_test.json
    │   │       ├── cross-platform-vectors/
    │   │       │   └── encryption-roundtrip-v1.json
    │   │       └── key-blob/
    │   │           └── v1-sample.json    # backward-compat fixture
    │   └── androidInstrumentedTest/
    │       └── kotlin/family/crypto/
    │           ├── SecureKeyStorePersistenceTest.kt
    │           ├── SecureKeyStoreNoPlaintextLeakTest.kt
    │           └── SecureKeyStoreInvalidPrefixTest.kt
docs/
└── dev/
    └── crypto-review.md    # New file — industrial baseline + validation set
                            # (FR-023, FR-024) — replaces friend review
app/
├── src/main/
│   └── kotlin/.../CryptoModule.kt    # Koin DI wiring — Libsodium adapter in release
├── src/debug/
│   └── kotlin/.../DebugCryptoModule.kt    # Koin DI wiring — Fake adapter in debug
└── src/main/res/xml/
    └── data_extraction_rules.xml    # UPDATED — exclude files/keys/ from backup
```

## Architecture

### Module map

`:core:crypto` — standalone Gradle subproject, **no inbound dependencies on launcher modules**. Outbound dependencies: Kotlin stdlib, kotlinx.coroutines, kotlinx.serialization, kotlinx.datetime, ionspin libsodium-bindings.

```
                                 ┌────────────────────────┐
                                 │ Consumers              │
                                 │ - F-5 (:core:config)   │
                                 │ - spec 011 (:core:media│
                                 │ - spec 017 (recovery)  │
                                 │ - future messenger app │
                                 │ - future photo app     │
                                 └───────────┬────────────┘
                                             │ imports family.crypto.api.*
                                             ▼
                              ┌─────────────────────────────┐
                              │ :core:crypto/commonMain/api │
                              │ (port interfaces only)      │
                              │ ─ AeadCipher                │
                              │ ─ AsymmetricCrypto          │
                              │ ─ KeyDerivation             │
                              │ ─ RandomSource              │
                              │ ─ SecureKeyStore            │
                              │ ─ KeyRotation (stub)        │
                              │ ─ KeyEscrow (stub)          │
                              │ ─ values: KeyId, KeyBlob, … │
                              └────┬──────────┬─────────────┘
                                   │          │
                  bound by DI per build variant
                                   │          │
              ┌────────────────────┘          └────────────────────┐
              ▼                                                    ▼
   ┌────────────────────────┐                       ┌─────────────────────────┐
   │ libsodium/ adapters    │                       │ fake/ adapters          │
   │ (release builds)       │                       │ (debug/test builds)     │
   │                        │                       │                         │
   │ LibsodiumAeadCipher    │                       │ FakeAeadCipher          │
   │ LibsodiumAsymmetric    │                       │ FakeAsymmetricCrypto    │
   │ LibsodiumKeyDerivation │                       │ FakeKeyDerivation       │
   │ LibsodiumRandomSource  │                       │ FakeRandomSource        │
   │                        │                       │ FakeSecureKeyStore      │
   │ + Android-specific:    │                       │                         │
   │   KeystoreSecureKeyStore                       │ + JVM-specific:         │
   │ + iOS-specific:        │                       │   InMemorySecureKeyStore│
   │   IosKeychainSecure*   │                       │                         │
   │   (stub-screamer)      │                       │                         │
   └────────────────────────┘                       └─────────────────────────┘
```

### Port-adapter shape

- **Port** = `interface` (or `expect class` для `SecureKeyStore` where platform impl differs). Lives в `commonMain/api/`. Domain code (потребители) знают **только** ports.
- **Real adapter** = `class` implementing port, использует libsodium / Android Keystore / iOS Keychain. Lives в `libsodium/` (common) или `androidMain/`/`iosMain/` (platform-specific).
- **Fake adapter** = `class` implementing port, deterministic, `@VisibleForTesting`. Lives в `commonTest/fake/` (test-only — НЕ попадает в release).
- **DI wiring** = Koin module в `app/`, выбирает Fake vs Real по build variant. `app/src/main/` имеет `release` wiring (Real), `app/src/debug/` имеет `debug` wiring (может использовать Fake для UI demo's, но в продакшене никогда).

### Data flow — encrypt example

```
1. Consumer (F-5 ConfigCipher):
   val key = keyDerivation.derive(secret, salt, info = "config-v1", length = 32)
   val ciphertext = aeadCipher.encrypt(plaintext, key, aad)

2. Inside LibsodiumAeadCipher.encrypt:
   - nonce = randomSource.nextBytes(24)   // XChaCha20 nonce
   - rawCiphertext = libsodium.crypto_aead_xchacha20poly1305_ietf_encrypt(...)
   - return ConcatBytes(nonce, rawCiphertext)
   // Consumer NE VIDIT nonce — он встроен в blob

3. Consumer передаёт ciphertext (например, F-5 кладёт в Firestore document).

4. Decrypt:
   val plaintext = aeadCipher.decrypt(ciphertext, key, aad)
   // Inside: nonce = ciphertext.take(24), rest = ciphertext.drop(24)
   // libsodium.crypto_aead_xchacha20poly1305_ietf_decrypt(rest, key, nonce, aad)
   // MAC verification внутри libsodium — на mismatch → DecryptionFailedException
```

### Data flow — SecureKeyStore wrap example (Android)

```
1. Consumer (F-5 при first run):
   val keyPair = asymmetricCrypto.generateX25519KeyPair()
   secureKeyStore.store(KeyId("config-admin-identity-v1"), keyPair.privateKey)
   // KeyId.init проверяет префикс "config-" → OK

2. Inside KeystoreSecureKeyStore.store:
   - Ensure AES wrap key existence в Keystore (alias "family-crypto-wrap-key-v1"),
     create if missing — purpose ENCRYPT/DECRYPT, setIsStrongBoxBacked(true).
   - cipher = Cipher.getInstance("AES/GCM/NoPadding"), init(ENCRYPT_MODE, wrapKey).
   - wrappedBytes = cipher.doFinal(privateKey)
   - iv = cipher.iv
   - keyBlob = KeyBlob(schemaVersion=1, algorithm="X25519",
       createdAt=now, retiredAt=null, replacedBy=null,
       wrappedKey=wrappedBytes, iv=iv)
   - jsonBytes = Json.encodeToString(keyBlob).toByteArray()
   - File("/data/data/<pkg>/files/keys/${keyId.raw}.blob").writeBytes(jsonBytes)

3. При load — обратная операция. На uninstall → Keystore alias уничтожается
   → unwrap не работает → load returns null.
```

## Data model

См. отдельный документ [data-model.md](data-model.md).

## Wire formats

См. отдельный документ [contracts/key-blob-v1.md](contracts/key-blob-v1.md).

Список wire formats в F-CRYPTO:

| Format | Scope | Contract |
|---|---|---|
| `KeyBlob` (JSON) | Persisted в app sandbox, format для `SecureKeyStore` | [contracts/key-blob-v1.md](contracts/key-blob-v1.md) |
| AEAD ciphertext (raw bytes) | Внутренний — nonce + libsodium ciphertext. Consumer treats as opaque `ByteArray`. | Не отдельный contract — структура определяется libsodium XChaCha20-Poly1305 (RFC 8439 + IETF XChaCha20 draft). Document'нем в KDoc на `AeadCipher.encrypt(...)`. |

**Note**: F-5 (Config E2E) и спека 011 (media blobs) будут иметь **свои** wire formats поверх F-CRYPTO ciphertext — это **не** F-CRYPTO ответственность, document'нем в **их** spec'ах.

## Dependency impact

### New gradle dependencies

| Dependency | Why | Justified per Article XIII | Exit ramp |
|---|---|---|---|
| `com.ionspin.kotlin:libsodium-bindings:<v>` | libsodium binding для KMP, primary crypto implementation | Article XIII: yes — vendor дёшево swap'ается через port (Article XIII §A justifies single vendor with port). | Research-фаза проверяет актуальность. Fallback: BouncyCastle (Android) + cinterop (iOS). Effort 1-2 weeks. |
| `org.jetbrains.kotlinx:kotlinx-datetime:<v>` | `Instant` без `java.time` (для iOS readiness) | Article XIII: yes — стандартная KMP dep. | N/A (Kotlin org-owned). |
| `io.kotest:kotest-runner-junit5:<v>` (testImplementation) | Property-based tests | Article XIII: yes — test-only. | swap to kotlin-test if needed. |
| `io.kotest:kotest-property:<v>` (testImplementation) | Property generators | Same as above. | Same. |

### Removed dependencies

None. F-CRYPTO is purely additive.

### `core/crypto/build.gradle.kts` constraints

- **MUST NOT depend on any launcher module** — verified through fitness function (Gradle task or Detekt rule). See `tasks.md` plan.
- **MUST NOT depend on Compose** — F-CRYPTO is non-UI.
- **MUST NOT depend on Koin** — DI wiring lives в `:app`, not в `:core:crypto`.

## Test strategy

### Test pyramid

```
                  ┌──────────────────────────────────┐
                  │ Manual / Acceptance              │  (~3 manual:
                  │ - scenarios.md walk-through      │   library extract
                  │ - Real device OEM matrix         │   dry-run, OEM
                  │ - libsodium swap experiment      │   Keystore quirks)
                  └──────────────────────────────────┘
                ┌────────────────────────────────────────┐
                │ Instrumentation tests (androidInstr.)  │  (~5 tests:
                │ - SecureKeyStorePersistenceTest        │   Android Keystore
                │ - SecureKeyStoreNoPlaintextLeakTest    │   needs real
                │ - SecureKeyStoreInvalidPrefixTest      │   instrumentation)
                │ - SecureKeyStoreUninstallScenarioTest  │
                │ - TeeAttestationCheckTest              │
                └────────────────────────────────────────┘
            ┌──────────────────────────────────────────────┐
            │ Unit tests (jvmTest + androidUnitTest)       │  (~50 tests:
            │ - RFC KAT (X25519, Ed25519, ChaCha20, HKDF)  │   the bulk —
            │ - Wycheproof subset (low-order, malleable)   │   commonTest
            │ - Property tests (Kotest, 1000 iterations)   │   runs on both
            │ - KeyBlob roundtrip + backward-compat read   │   targets =
            │ - CrossPlatformVectorParityTest              │   cross-plat
            │ - Fake adapter parity vs Real adapter        │   automatic)
            │ - KeyId prefix validation                    │
            └──────────────────────────────────────────────┘
```

### Test types per CLAUDE.md rule §6 + §7

| Type | Coverage | Location |
|---|---|---|
| **Contract tests** | Each port has shape contract: signature + property invariants (e.g., AEAD: decrypt(encrypt(x))=x; ECDH: DH(a,B)=DH(b,A)). | `commonTest/property/` |
| **RFC KAT** | RFC 7748/8032/8439/5869 test vectors as Known Answer Tests | `commonTest/kat/` |
| **Wycheproof** | Google's edge-case JSON vectors (low-order, malleable, point-at-infinity) | `commonTest/wycheproof/` |
| **Property-based** | Kotest properties, 1000 iterations (configurable via `kotestPropertyIterations` Gradle property) | `commonTest/property/` |
| **Cross-platform** | One JSON vector executed on `androidUnitTest` AND `jvmTest`, identical byte assertion | `commonTest/crossplatform/` |
| **Backward-compat read** | Fixture from `schemaVersion=1` reads successfully when code is at `v2` | `commonTest/wireformat/` |
| **Fake-Real parity** | Fake adapter has same byte-shape return as Real (size, headers) where applicable | `commonTest/property/` |
| **Instrumentation** | Android Keystore wrap/unwrap roundtrip, plaintext leak scan | `androidInstrumentedTest/` |
| **Fitness functions** | Gradle task / Detekt rule: `:core:crypto:dependencies` has no `project:` references except allowed; Fake* import in `app/src/main/` fails CI | `core/crypto/build.gradle.kts` + Detekt config |

### Test commands

- `./gradlew :core:crypto:jvmTest` — main batch (RFC KAT, Wycheproof, property, wire format).
- `./gradlew :core:crypto:testDebugUnitTest` — Android source set unit tests.
- `./gradlew :core:crypto:connectedDebugAndroidTest` — Keystore instrumentation tests (требует эмулятор `pixel_5_api_34` via skill `android-emulator`).
- `./gradlew :core:crypto:dependencies` + grep — fitness function: no launcher-module deps.
- `./gradlew :core:crypto:test -PkotestPropertyIterations=1000` — property tests с 1000 iterations (CI).

## Risks

| Risk | Category | Mitigation |
|---|---|---|
| **ionspin libsodium-kmp окажется dead/deprecated** | Vendor lock-in | Research-фаза проверяет last commit / open issues; fallback на BouncyCastle (Android) + собственный cinterop (iOS) задокументирован. |
| **Android Keystore alias invalidation на Xiaomi MIUI** | OEM quirk | Adapter ловит exception, rethrow as `KeystoreInvalidatedException`. Consumer UX shows re-pairing flow. `TODO(physical-device): MIUI cleanup soak test` inline. |
| **TEE недоступен (rooted device, emulator без TEE)** | Platform | Adapter fail-fast `KeystoreUnavailableException`. Spec: "production app must show clear message". `KeyInfo.isInsideSecureHardware` runtime check. |
| **FakeAdapter в release build (developer mistake)** | Build hardening | Detekt-правило ловит `Fake*Cipher` import в `app/src/main/`. Runtime assertion в DI init. R8 rule в plan (опционально для MVP). |
| **Backup `data_extraction_rules.xml` отправляет blobs в Google Drive** | Recovery UX | `data_extraction_rules.xml` MUST exclude `files/keys/` (plan task). Иначе на новом устройстве blobs восстанавливаются но TEE wrap key другой → unreadable. |
| **Cross-platform byte mismatch** | Correctness | CrossPlatformVectorParityTest fails CI → blocking merge. Fixture в `commonTest/resources/cross-platform-vectors/`. |
| **libsodium binding отличается между Android и JVM** | Correctness | Same fixture run on both source sets — automatic detection. |
| **Property test seed non-deterministic** | Test reliability | FakeRandomSource: seeded. Kotest property: `PropTestConfig(seed=...)` для reproducibility. |
| **Wycheproof обновляется → новый вектор ломает CI** | Test stability | Snapshot pin: subset commit'нут в git. Refresh — manual PR. |
| **AEAD ciphertext envelope ответственность** | Cross-spec | F-CRYPTO declines responsibility — consumer (F-5) пишет свой envelope с schemaVersion. Document'нем в FR-006 KDoc. Open issue из wire-format checklist. |
| **iOS targets компилируются — но реальная сборка только на macOS** | Dev experience | iOS targets declared, CI skips iOS build. `TODO(physical-mac): iOS build verification` inline в `iosMain/`. |

## Required Context Review

Per Article XII §7 — все relevant files проверены и учтены:

### `docs/governance/`

- [`docs/governance/document-map.md`](../../docs/governance/document-map.md) — позиция F-CRYPTO в архитектурной иерархии.

### `docs/adr/`

- [`docs/adr/ADR-008-social-recovery-architecture.md`](../../docs/adr/ADR-008-social-recovery-architecture.md) — F-CRYPTO предоставляет ВСЕ примитивы для ADR-008 (включая `sealCEK`/`unsealCEK`). Spec 017 будет потребителем.

### `docs/product/`

- [`docs/product/roadmap.md`](../../docs/product/roadmap.md) §F-CRYPTO — текущая позиция (Phase 1 шаг 2), все скоп-решения 2026-06-17 уже отражены.
- [`docs/product/glossary.md`](../../docs/product/glossary.md) — `KeyBlob` term добавляется в plan-phase.
- [`docs/product/decisions/2026-06-15-deferred-cloud/02-config-ownership-per-device.md`](../../docs/product/decisions/2026-06-15-deferred-cloud/02-config-ownership-per-device.md) — server = source of truth для config; F-CRYPTO supports E2E around это.

### `docs/dev/`

- [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) — TODO-RECOVERY-001 + TODO-AUTH-001 ссылаются на F-CRYPTO как dependency.
- [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md) — SRV-CRYPTO-003..007 cross-references.
- [`docs/dev/crypto-review.md`](../../docs/dev/crypto-review.md) — **CREATE** в этой спеке (FR-023, FR-024). Industrial baseline document.

### `docs/compliance/`

- [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — F-CRYPTO **не добавляет** permissions, не требует update. Записать в commit message.

### `CLAUDE.md` rules — applied

- Rule 1 (domain isolated): FR-028 enforces.
- Rule 2 (ACL для libsodium): primary purpose F-CRYPTO.
- Rule 4 (MVA): `KeyRotation`/`KeyEscrow` stubs, не real impl сейчас.
- Rule 5 (wire format `schemaVersion`): FR-016, FR-025.
- Rule 6 (mock-first): FakeAdapter для каждого port.
- Rule 8 (server-roadmap entries): SRV-CRYPTO-003..007 added.

### Constitution Articles — applied

- Article V (Modularization With Restraint): `core/crypto/` justified per Article V §3 — see modular-delivery checklist.
- Article XIII (External dependencies, vendor lock-in): ionspin justified, exit ramp documented.
- Article XIV (Security & permissions): no new permissions; backup rules exclude `files/keys/`.
- Article XIX (Organic question budgets): clarify pass asked 8 organic questions, not capped.

### Memory references

- `project_f_crypto_decisions.md` — все decisions 2026-06-17.
- `project_deferred_cloud_architecture.md` — F-4 deferred, device-local salt rationale.
- `feedback_defer_only_if_no_rewrite.md` — iOS targets declared day 1 because deferral потом = переписывание.
- `feedback_organic_question_budgets.md` — Article XIX applied.

## Constitution Check

**Run date**: 2026-06-17 (Step 4 of speckit-plan).
**Verdict**: ✅ **COMPLETE** — 6 PASS, 2 N/A, 0 FAIL.

| # | Gate | Result | Justification |
|---|---|---|---|
| 1 | Architecture | ✅ PASS | `core/crypto/` Gradle subproject justified per Article V §3 (5 criteria: ownership boundary, build isolation, independent enable/disable, stable API via semver, material testability gain). 7 ports, каждый имеет реального потребителя. |
| 2 | Core/System Integration | ➖ N/A | F-CRYPTO offline by design (Assumptions); no system events, no broadcasts, no boot listener, no lifecycle owner. Android Keystore — direct API, не event-based. |
| 3 | Configuration | ✅ PASS | `KeyBlob` wire format несёт `schemaVersion: Int = 1` с первого коммита (FR-016, FR-025). Contract в [contracts/key-blob-v1.md](contracts/key-blob-v1.md) полностью описывает: shape, fields, migration policy (additive minor / migrator-before major), fixture (`v1-sample.json` frozen at 1.0.0), backward-compat test (FR-026). CLAUDE.md rule 5 full compliance. |
| 4 | Required Context Review | ✅ PASS | Linked: ADR-008 (consumer для F-CRYPTO primitives), roadmap §F-CRYPTO, glossary (KeyBlob term — to add), decisions/2026-06-15-deferred-cloud/02-config-ownership, project-backlog (TODO-RECOVERY-001 / TODO-AUTH-001), server-roadmap (SRV-CRYPTO-003..007), crypto-review.md (creates in this spec), permissions-budget (explicit "no new permissions"), CLAUDE.md rules 1+2+4+5+6+8 applied, 4 memory files. |
| 5 | Accessibility | ➖ N/A | F-CRYPTO is infrastructure module — no UI, no user-facing surfaces. AI Affordance явно: `no AI affordance — internal capability only`. Accessibility — забота consumer'ов (F-5, спека 011, S-1..S-8) в их спеках. |
| 6 | Battery/Performance | ✅ PASS | Offline by design. No background tasks. No polling. No WorkManager. Perf targets перечислены в Technical Context (AEAD < 5ms, X25519 keypair < 50ms, HKDF < 10ms, SecureKeyStore TEE round-trip < 100ms). F-CRYPTO operations редкие. Perf checkpoint planned in Phase 12. Article IX full compliance. |
| 7 | Testing | ✅ PASS | Test strategy полная: contract tests (port invariants — AEAD roundtrip, ECDH symmetry, sign/verify, tamper detection, nonce reuse rejection), RFC KAT (4 RFCs), Wycheproof subset (edge cases), property-based 1000 iterations, wire-format roundtrip + backward-compat read, cross-platform parity (jvmTest + androidUnitTest identical bytes), Fake-Real adapter parity, instrumentation tests на эмуляторе, fitness functions (Gradle deps + Detekt rule). CLAUDE.md rule 6 (mock-first): каждый port имеет Fake + Real + DI wiring. |
| 8 | Simplicity | ✅ PASS | Каждое из 7 portов имеет реального потребителя. `KeyRotation`/`KeyEscrow` interface-only — **accepted exception** (meta-minimization O-1): их storage shape (`retiredAt`/`replacedBy`) нужна с первого коммита по rule 5, иначе потом миграция всех wrapped blobs. CLAUDE.md rule 4 self-test: Test 1 (inlining `AeadCipher` → leak libsodium = нарушение rule 1+2), Test 2 (libsodium → BouncyCastle swap = 1 adapter module, 1-2 weeks, exit ramp в research.md R1). No mediators, no DSLs, no plugin systems. |

**Remediation**: none.

Plan ready for `/speckit.tasks`.

## Rollout / verification

### Phase ordering (для `/speckit.tasks` фазы)

1. **Phase 0 — Research** (output: [research.md](research.md)): ionspin actuality, BouncyCastle fallback details, iOS expect/actual approach.
2. **Phase 1 — Module scaffolding**: `core/crypto/build.gradle.kts`, KMP targets, empty ports.
3. **Phase 2 — Ports & values**: `commonMain/api/`, `KeyId`, `KeyNamespace`, `KeyBlob`.
4. **Phase 3 — Fake adapters + property tests**: `commonTest/fake/`, `commonTest/property/`.
5. **Phase 4 — Real adapters (libsodium)**: `libsodium/` package, RFC KAT + Wycheproof tests.
6. **Phase 5 — SecureKeyStore Android adapter**: `androidMain/`, instrumentation tests, wrap pattern.
7. **Phase 6 — iOS stub-screamer**: `iosMain/` actual класс с throw.
8. **Phase 7 — DI wiring + Detekt rule + R8 rule**: `:app` integration.
9. **Phase 8 — Cross-platform test vector + backward-compat fixture**: `commonTest/resources/`.
10. **Phase 9 — `docs/dev/crypto-review.md`**: industrial baseline document.
11. **Phase 10 — backup rules update**: `data_extraction_rules.xml` exclude `files/keys/`.
12. **Phase 11 — Fitness functions**: Gradle dependency check, Detekt rules.
13. **Phase 12 — Perf checkpoint** (post-implementation): performance measurements vs SC targets.
14. **Phase 13 — Acceptance verification trace**: walk through `scenarios.md` 11 сценариев, mark checkboxes.

### Verification gates

- **Gate 1** (after Phase 4): all RFC KAT green, all property tests green, Wycheproof subset green.
- **Gate 2** (after Phase 5): instrumentation tests green on `pixel_5_api_34` emulator.
- **Gate 3** (after Phase 8): cross-platform vector parity test green (jvmTest + androidUnitTest identical bytes).
- **Gate 4** (after Phase 11): fitness functions green — no launcher-module dependencies, no Fake* in release.
- **Gate 5** (post-merge): F-5 spec'ификация может начаться, может писать `ConfigCipher` поверх F-CRYPTO ports.

### Perf checkpoint targets

| Operation | Target | Measure on |
|---|---|---|
| AEAD encrypt 1 KB | < 5ms | Pixel 5 (`pixel_5_api_34` emulator) |
| AEAD decrypt 1 KB | < 5ms | same |
| X25519 keypair generate | < 50ms | same |
| X25519 ECDH derive | < 20ms | same |
| Ed25519 sign | < 30ms | same |
| Ed25519 verify | < 30ms | same |
| HKDF derive 32 bytes | < 10ms | same |
| `SecureKeyStore.store` (TEE round-trip) | < 100ms | physical device (TEE not simulated on emulator) — `TODO(physical-device)` |
| `SecureKeyStore.load` | < 50ms | same |

Targets — not blockers — measured, recorded in `perf-checkpoint.md` (Phase 12), no agressive optimization unless > 2x target.

---

<!-- novice summary added below by procedure-add-novice-summary -->

## TL;DR простым языком

Это **техническое задание для реализации** F-CRYPTO (криптографического модуля). Что внутри:

1. **Где код будет жить** — новая папка `core/crypto/` со структурой подпапок для разных платформ (Android, iOS, JVM для тестов) и разных частей (интерфейсы, реализация на libsodium, "заглушки" для тестов).

2. **Что зависит от чего** — `core/crypto/` ни от каких других модулей проекта не зависит (это специальная проверка, она автоматическая), но другие модули будут зависеть от него.

3. **Как будем тестировать** — три уровня:
   - Простые тесты на JVM (быстро, без эмулятора).
   - Тесты на Android-эмуляторе (для проверки защищённого чипа TEE).
   - Ручные проверки на реальных устройствах разных производителей (Samsung, Xiaomi, и т.п.).
   Плюс — специальные «эталонные тесты» (RFC KAT, Google Wycheproof), которые проверяют, что наша криптография работает точно так же, как стандарты требуют.

4. **Что может пойти не так** — 10 рисков перечислено с тем, как мы их избегаем (например, библиотека libsodium может стать неподдерживаемой — у нас есть план Б).

5. **Порядок работ** — 14 фаз от настройки модуля до финальной проверки 11 сценариев из `scenarios.md`.

6. **Цели по скорости** — F-CRYPTO работает быстро (миллисекунды), не критическая performance, но измеряем чтобы убедиться.

После этого документа дальше идёт `/speckit.tasks` — это разбивка плана на конкретные мелкие задачи, которые можно реально делать одну за другой.

# Implementation Plan: libsodium consolidation (TASK-51)

**Branch**: `task-51-libsodium-consolidation` | **Date**: 2026-06-26 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/task-51-libsodium-consolidation/spec.md`

## Summary

TASK-51 — архитектурная консолидация crypto-стэка проекта launcher: устранение JNA eager-bind crash'а через переход с `lazysodium-android` на `ionspin/multiplatform-crypto-libsodium-bindings`, переименование namespace `family.*` → `cryptokit.*`, удаление дублирующей старой стопки `com.launcher.api.crypto.*` (15 wire-format типов мигрируют в `cryptokit.pairing.api.*`), uniform `throws CryptoException` error handling, silent auto-migration persisted keys через AndroidKeystore TEE (никаких user-facing шагов).

**Technical approach**: рефакторинг без новых архитектурных абстракций. Большая часть проекта уже на новом стэке (`family.crypto.*` после namespace rename → `cryptokit.crypto.*`). Осталось: переписать pairing-side (`PairingCryptoCoordinator`, wire-format adapters, DI), удалить старый параллельный пакет, добавить silent migration logic, обогатить `CryptoException` иерархию для observability.

## Technical Context

**Language/Version**: Kotlin 2.0.x, JVM 17, Android API 26+ (minSdk).
**Primary Dependencies**:
- `ionspin/multiplatform-crypto-libsodium-bindings` 0.9.5 (KMP — Android/JVM/iOS targets) — **остаётся**
- `androidx.security:security-crypto` 1.1.0-alpha06 (EncryptedSharedPreferences) — **остаётся**
- ~~`lazysodium-android` 5.1.0 + JNA 5.13.0~~ — **удалены** в Phase 1 (commit `1e6be2e`)
- Koin (DI) — **остаётся**
- Kotlin coroutines, kotlinx.serialization — **остаются**

**Storage**:
- Android Keystore (TEE/StrongBox) — root AES master keys
- EncryptedSharedPreferences — wrapped X25519/Ed25519 priv bytes
- Firestore (real backend flavor) — DeviceIdentity, EncryptedEnvelope wire formats
- В TASK-51 эти storage backends **не меняются** — только rename namespace в сериализаторах.

**Testing**:
- Unit tests + Robolectric (`androidUnitTest`) — pure JVM crypto adapter tests
- Konsist (`androidUnitTest`) — fitness-functions для архитектурных инвариантов
- JVM tests в `:core:crypto` (`jvmTest`) — KAT vectors против RFC test vectors
- Manual smoke (Xiaomi 11T, `17f33878`) — PairingActivity launch + Spec011SmokeDebugActivity round-trip
- Goldens — `EnvelopeConfigCipherRoundtripTest` (existing, в `:core:keys`)

**Target Platform**:
- Primary: Android (arm64, armeabi-v7a, x86_64, x86) — minSdk 26, targetSdk 34
- Future-ready: iOS (через KMP, TASK-26), Android TV (TASK-29) — `commonMain` без Android-specific imports

**Project Type**: KMP Android library refactor.

**Performance Goals**: N/A для собственного кода (per owner-mandate 2026-06-26 — performance metrics meaningful только при выборе внешних библиотек).

**Constraints**:
- **Никаких user-facing migration шагов** — silent auto-migration через AndroidKeystore TEE (FR-005).
- **Wire-format byte-equal compatibility** — `EnvelopeConfigCipherRoundtripTest` golden vectors должны проходить после rename (FR-004).
- **Coroutine structured concurrency** — auto re-throw `CancellationException` в universal `try/catch` (FR-009).

**Scale/Scope**: refactor ~37 файлов (см. spec § "Что входит технически" из backlog description). Ничего нового не добавляется в codebase кроме silent migration logic + CryptoException hierarchy expansion.

## Architecture

### Module map (после TASK-51)

```text
:app
├── di/
│   └── cryptokitModule.kt       ← унифицированный Koin module (FR-015)
│
:core:crypto                      ← единая crypto-стопка (был F-CRYPTO)
├── commonMain/
│   ├── cryptokit/
│   │   ├── crypto/
│   │   │   ├── api/             ← порты: AeadCipher, AsymmetricCrypto,
│   │   │   │                       KeyDerivation, SecureKeyStore, RandomSource,
│   │   │   │                       PasswordHash, KeyRotation, KeyEscrow
│   │   │   ├── libsodium/       ← реализации через ionspin
│   │   │   └── exception/       ← sealed CryptoException hierarchy (5 subclasses)
│   │   └── pairing/             ← NEW package (per Q1 deep migration)
│   │       └── api/             ← DeviceIdentity, EncryptedEnvelope, Recipient,
│   │                              DeviceIdentityRepository, EncryptedMediaStorage,
│   │                              RecipientResolver, DeviceKeyPair,
│   │                              ContentEncryptionKey, и т.д. (15 типов из spec 011)
│   ├── commonTest/
│   │   └── cryptokit/
│   │       ├── crypto/fake/     ← FakeAeadCipher, FakeAsymmetricCrypto, и т.д.
│   │       └── pairing/fake/    ← FakeDeviceIdentityRepository, FakeEncryptedMediaStorage
│   ├── androidMain/
│   │   └── cryptokit/
│   │       └── crypto/libsodium/ ← actual для SecureKeyStore (Android Keystore wrap)
│   └── iosMain/                 ← future-ready (TASK-26): iOS Keychain actual
│
:core (legacy module)
├── androidMain/
│   └── adapters/crypto/
│       ├── PairingCryptoCoordinator.kt  ← REWRITTEN: cryptokit.crypto.api.* + try/catch
│       ├── PairRecipientResolver.kt     ← REWRITTEN: imports cryptokit.pairing.api.*
│       ├── BackgroundReconciler.kt      ← REWRITTEN: try/catch вместо Outcome
│       ├── SqlDelightBlobReferenceLedger.kt  ← imports updated
│       ├── ClearDataDetector.kt         ← unchanged (no crypto deps)
│       ├── StorageRetryWorker.kt        ← imports updated
│       │
│       ├── LibsodiumProvider.kt              ← DELETED
│       ├── LibsodiumAeadCipher.kt            ← DELETED
│       ├── LibsodiumAsymmetricCrypto.kt      ← DELETED
│       ├── LibsodiumDigitalSignature.kt      ← DELETED
│       ├── LibsodiumHashFunction.kt          ← DELETED
│       └── AndroidKeystoreSecureKeystore.kt  ← DELETED (replaced by cryptokit.crypto.api.SecureKeyStore)
│
└── commonMain/
    └── api/crypto/              ← DELETED ENTIRELY (22 файла) — types migrated к cryptokit.pairing.api
```

### Data flow

```text
PairingActivity (UI)
   │ Koin injection
   ▼
PairingViewModel
   │
   ▼
PairingCryptoCoordinator                          [androidMain, :core/adapters/crypto]
   │ uses:
   │  • cryptokit.crypto.api.AsymmetricCrypto (generateX25519KeyPair, sign)
   │  • cryptokit.crypto.api.SecureKeyStore (store, load — silent migration logic here)
   │  • cryptokit.pairing.api.DeviceIdentityRepository
   │
   │ throws CryptoException (uniform — no Outcome wrapping)
   ▼
[universal try/catch in PairingViewModel]
   │ - CancellationException → re-throw
   │ - CryptoException → Logcat tag "cryptokit" + UI error state
```

### Silent migration logic (FR-005)

```kotlin
// Pseudocode inside SecureKeyStore wrapper or PairingCryptoCoordinator init
suspend fun loadOrMigrate(newKeyId: KeyId, legacyAlias: String): ByteArray? {
    secureKeyStore.load(newKeyId)?.let { return it }

    val legacyBytes = legacyKeystoreReader.read(legacyAlias) ?: return null
    // Silent migration:
    secureKeyStore.store(newKeyId, legacyBytes)
    legacyKeystoreReader.delete(legacyAlias)
    // TODO(post-task-6): replace with derive-from-root after Root Key Hierarchy lands
    return legacyBytes
}
```

`legacyKeystoreReader` — минимальная утилита для чтения старых aliases через прямой Android Keystore API (не через lazysodium). После TASK-6 — удаляется.

## Data model

См. [data-model.md](./data-model.md) — описывает структуру `cryptokit.pairing.api.*` пакета (15 типов) + `cryptokit.crypto.exception.CryptoException` иерархию (5 subclasses).

## Wire formats

Все wire formats **сохраняют существующий byte-layout**. Меняется только Kotlin namespace в сериализаторах.

| Wire format | Контракт | Изменения в TASK-51 |
|---|---|---|
| `DeviceIdentity` | [contracts/device-identity.md](./contracts/device-identity.md) | namespace `cryptokit.pairing.api.DeviceIdentity` + `@SerialName("DeviceIdentity")` |
| `EncryptedEnvelope` | [contracts/encrypted-envelope.md](./contracts/encrypted-envelope.md) | namespace + `@SerialName` |
| `Ciphertext` (existing F-CRYPTO) | [contracts/ciphertext.md](./contracts/ciphertext.md) | namespace `family.* → cryptokit.*` |
| `KeyBlob` (existing F-CRYPTO) | unchanged in this task | namespace rename only |

**Critical**: каждый wire-format тип должен иметь explicit `@SerialName(...)` annotation чтобы binary-compat сохранился при namespace rename (FR-004 patch). Plan-time task: grep existing types на наличие `@SerialName`.

## Dependency impact

| Direction | Dependency | Scope | Justification |
|---|---|---|---|
| **Removed** | `lazysodium-android:5.1.0` (`com.goterl`) | `:core` androidMain | Replaced by ionspin libsodium-kmp (broader ABI support, no JNA eager-bind crash) |
| **Removed** | `jna:5.13.0` (`net.java.dev.jna`, aar + jar variants) | `:core` androidMain + test | Transitive of lazysodium — gone with it |
| **Kept** | `multiplatform-crypto-libsodium-bindings:0.9.5` | `:core:crypto` commonMain | Already used in F-CRYPTO; covers все нужные примитивы |
| **Kept** | `androidx.security:security-crypto:1.1.0-alpha06` | `:core` androidMain + `:app` | EncryptedSharedPreferences для wrapped X25519/Ed25519 priv bytes |
| **No new** | — | — | TASK-51 не вводит новые внешние зависимости |

**Article XIII compliance**: ни одной новой dependency не добавляется. Удаление 2 dependencies → меньше supply-chain surface.

## Test strategy

Per CLAUDE.md rule §6 (mock-first) + §7 (fitness functions):

### Unit tests (rewrite)
- `PairingCryptoCoordinatorTest` (existing, в `:core/androidUnitTest`) — переписать на `cryptokit.crypto.fake.*` (новые fakes из spec 016).
- `LibsodiumAdaptersTest` — **удалить** (тестировал удаляемые lazysodium adapters).
- `CryptoEnvelopeWireFormatTest` — переписать на cryptokit imports + новые fakes.

### Integration tests
- `EnvelopeConfigCipherRoundtripTest` (existing, в `:core:keys`) — **golden vector test**. Должен проходить **байт-в-байт** после namespace rename — это и есть SC-013.

### Fitness functions (Konsist) — обновить + добавить новые

| Test | Покрытие |
|---|---|
| `NoLazysodiumInProductionTest` (NEW) | grep `com.goterl.*` в production code = 0 матчей |
| `NoLegacyComLauncherCryptoTest` (NEW) | grep `com.launcher.api.crypto.*` = 0 матчей в любом коде проекта |
| `NoLegacyFamilyNamespaceTest` (NEW) | grep `family.crypto.*` / `family.pairing.*` = 0 матчей (только `cryptokit.*`) |
| `Spec011IsolationTest` (existing) | обновить ban list — добавить запреты выше |
| `Spec014IsolationTest` (existing) | аналогично |
| `NoFakeCryptoInAppTest` (existing) | hardcoded path обновить с `family.crypto.fake` на `cryptokit.crypto.fake` |
| `NoBackdoorLoggingTest` (NEW) | Konsist rule: catch (CryptoException) → log fields whitelist (operation, exceptionClass, messageHash). НЕ permit raw bytes, hex >8B, deviceIds (FR-017) |

### Manual smoke (Xiaomi 11T)
- Launch `PairingActivity` без crash → SC-001.
- Run `Spec011SmokeDebugActivity` round-trip → SC-002.
- Install APK поверх предыдущей версии → existing pairing продолжает работать без UI шагов → SC-011.
- Trigger artificial CryptoException → Logcat tag `cryptokit` появляется → SC-014.

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **Silent migration не сработает на устройстве с persisted lazysodium-state** | LOW | HIGH (user не сможет open pairing) | Phase 7 manual smoke на Xiaomi 11T (если есть pre-TASK-51 ключи). Fallback — recovery flow F-5b. |
| **`@SerialName` отсутствует на части wire-format типов → binary drift** | MEDIUM | HIGH (Firestore documents с old version unreadable) | Plan-time grep audit перед миграцией. Если absent — добавить в первом коммите Phase 4. Verify через `EnvelopeConfigCipherRoundtripTest` golden vectors. |
| **OEM divergence на не-Xiaomi устройствах (Samsung One UI, Huawei EMUI)** | MEDIUM | MEDIUM | Defer to TASK-55 verification aggregator. ionspin JNI lazy-bind работает одинаково на всех OEM по design — crash fix архитектурно applicable. |
| **CancellationException не re-throw в catch (Throwable)** | MEDIUM | MEDIUM (silent coroutine leak) | Konsist rule (FR-017 enforcement) — verify все `catch (e: Throwable)` имеют preceding `catch (e: CancellationException) { throw e }`. |
| **ionspin JNI lazy-bind теоретически может eager-bind в неизвестных условиях** | LOW | HIGH (новый crash) | Smoke test (`Spec011SmokeDebugActivity`) гарантированно вызывает все нужные функции — если eager-bind проблема была бы, она бы проявилась здесь. |
| **`AndroidKeystoreSecureKeystore` удаление сломает existing tests** | MEDIUM | LOW | После удаления — компилятор покажет все ссылки. Phase 5 — rewrite tests. |
| **Namespace rename миссит какое-то место** | MEDIUM | LOW (compile error) | Phase 6 fitness test `NoLegacyFamilyNamespaceTest` catches это автоматически. |

## Required Context Review

Перед началом имплементации читай:

- [`CLAUDE.md`](../../CLAUDE.md) — Engineering rules (особенно §1 domain isolation, §2 ACL, §4 MVA, §5 wire-format versioning, §9 shareability).
- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — Articles I-XVIII (особенно §V Modularization, §VII Configuration, §XII Documentation Discipline, §XIII Dependency, §XVI Constitution Check).
- [`specs/016-f-crypto-core-module/`](../016-f-crypto-core-module/) — F-CRYPTO spec, который определил `family.crypto.api.*` (теперь rename'имся в `cryptokit.*`).
- [`specs/011-contacts-and-e2e-encrypted-media/`](../011-contacts-and-e2e-encrypted-media/) — spec 011 wire format (DeviceIdentity, EncryptedEnvelope) — типы которые мигрируют в `cryptokit.pairing.api.*`.
- [`specs/018-f5-config-e2e-encryption/`](../018-f5-config-e2e-encryption/) — F-5 spec, который описывает `EnvelopeConfigCipherRoundtripTest` golden vectors.
- [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) — где TODOs для post-TASK-51 follow-ups (особенно TODO-TASK51-* если будут).
- Memory:
  - [`feedback_no_user_action_for_internal_migrations`](../../../C:/Users/user/.claude/projects/c--work-launcher/memory/feedback_no_user_action_for_internal_migrations.md) — Q2 silent migration rationale
  - [`feedback_apk_size_only_for_external_libs`](../../../C:/Users/user/.claude/projects/c--work-launcher/memory/feedback_apk_size_only_for_external_libs.md) — почему cold-start/APK не в acceptance criteria
  - [`feedback_research_industry_before_asking`](../../../C:/Users/user/.claude/projects/c--work-launcher/memory/feedback_research_industry_before_asking.md) — pattern исследования индустрии при выборе технологий

## Constitution Check

См. § "Constitution Check Report" ниже (генерируется через `procedure-constitution-check`).

## Rollout / verification

### Phased rollout (commits на ветке)

| Phase | Commits done so far | Scope |
|---|---|---|
| Phase 1 | `1e6be2e` (done) | Gradle stripping — lazysodium + JNA удалены |
| Phase 2 | `20013d1`, `beca982`, `e342a76`, `e67e4bf` | spec.md + clarifications + checklists + scenarios |
| Phase 3 | TODO | data-model + contracts (this plan output) |
| Phase 4 | TODO | Namespace rename `family.* → cryptokit.*` (one commit, find-replace) |
| Phase 5 | TODO | Pairing-side rewrite: `PairingCryptoCoordinator` + DI + adapters |
| Phase 6 | TODO | Old stack deletion: 22 файла com.launcher.api.crypto + 7 lazysodium files + 8 old fakes |
| Phase 7 | TODO | Tests rewrite + Konsist fitness rules update/add |
| Phase 8 | TODO | Manual smoke на Xiaomi 11T |
| Phase 9 | TODO | pre-pr-backlog-sync + PR open |

### Verification gates

После каждой фазы:
- `./gradlew :app:assembleMockBackendDebug` — compile-green
- `./gradlew test` — все unit + Robolectric тесты зелёные
- При Phase 4 после namespace rename — `EnvelopeConfigCipherRoundtripTest` (SC-013) **байт-в-байт green**

Финальные gates (после Phase 8):
- SC-001 ✅ PairingActivity opens без crash на Xiaomi 11T
- SC-002 ✅ Spec011SmokeDebugActivity round-trip
- SC-003..SC-007, SC-010..SC-014 — все grep / fitness / test gates
- 6 `[backlog]`-помеченных SC закрыты → backlog AC #16-#19 переключаются на `[x]`

## Constitution Check Report

Generated 2026-06-26 via `procedure-constitution-check`. **OVERALL: 6 PASS, 2 N/A, 0 FAIL — plan PASSES.**

| Gate | Verdict | Justification |
|---|---|---|
| **1. Architecture** | ✅ PASS | Module map explicit, no new gradle module, no new abstractions (HashFunction port explicitly rejected per R-004). Existing port-adapter shape preserved. |
| **2. Core/System Integration** | N/A | Pure refactor, no new system events / broadcasts / lifecycle callbacks. |
| **3. Configuration** | ✅ PASS | `schemaVersion: 1` зафиксирован в всех wire-format contracts. No schema change — только namespace rename. Backward-compat verified через `EnvelopeConfigCipherRoundtripTest` golden vectors (SC-013). `@SerialName(...)` audit защищает от binary drift. |
| **4. Required Context Review** | ✅ PASS | Plan §"Required Context Review" линкует CLAUDE.md, constitution.md, spec 016/011/018, project-backlog.md, 3 memory файла. `docs/compliance/permissions-and-resource-budget.md` omitted — TASK-51 не меняет permissions (justified). |
| **5. Accessibility** | N/A | Pure refactor, no new UI surfaces. `Spec011SmokeDebugActivity` — debug-only, не user-facing. |
| **6. Battery/Performance** | ✅ PASS | Performance metrics для собственного кода explicitly out-of-scope per owner-mandate (memory `feedback_apk_size_only_for_external_libs`). No new background work, no polling. ionspin lazy-bind cheaper than lazysodium eager-bind = net positive (not tracked). |
| **7. Testing** | ✅ PASS | Test strategy explicit: rewrite unit tests, golden-vector roundtrip (SC-013), fake adapters в `cryptokit.{crypto,pairing}.fake.*`, 7 Konsist fitness rules (4 NEW + 3 updated), manual smoke на Xiaomi 11T (SC-001/002/011/014). |
| **8. Simplicity** | ✅ PASS | HashFunction port НЕ вводится (R-004). Один DI module (R-005). AndroidKeystoreSecureKeystore удаляется, не переписывается (R-007). `cryptokit.pairing.api.*` — rename existing, не новая абстракция. Checklist meta-minimization 13/13 ✓ подтверждает. |

**Notable strengths**:
- R-001..R-007 каждое decision имеет alternatives + regret conditions + exit ramp (CLAUDE.md rule §3 one-way doors).
- Hardening adjacent concerns (AAD, nonce, allowBackup, zeroize) явно out-of-scope TASK-51 с reasoning — не tech-debt, а scope discipline.

**No remediation required**. Plan ready for /speckit.tasks.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** План архитектурного refactor'а — выкинуть lazysodium, оставить ionspin, переименовать namespace `family.*` → `cryptokit.*`, переписать pairing-side на новые импорты, добавить silent migration logic для persisted ключей (без user action). Нет новых features, нет новых dependencies, нет новых wire formats — только consolidation.

**Конкретика, которую стоит запомнить:**
- **9 phases на ветке**: 1-2 уже done (gradle + spec/clarify/scenarios), 3-9 TODO (data-model + namespace rename + pairing rewrite + deletion + tests + smoke + PR).
- **Удаляются файлы** (~37): 22 в `com.launcher.api.crypto/` + 7 lazysodium adapters в `:core/androidMain/.../adapters/crypto/Libsodium*.kt` + `AndroidKeystoreSecureKeystore.kt` + `LibsodiumProvider.kt` + 8 old fakes в `commonTest/com/launcher/fake/crypto/`.
- **`cryptokit.pairing.api.*`** — новый пакет в `:core:crypto/commonMain/` для 15 wire-format типов spec 011 (DeviceIdentity, EncryptedEnvelope, Recipient, и т.д.).
- **`@SerialName` audit** (Phase 4 critical) — все wire-format типы должны иметь explicit `@SerialName("...")` annotation чтобы byte-equal compat сохранился при rename.
- **Silent migration logic** — read legacy alias → re-encrypt under new name → delete legacy. Inline TODO к TASK-6 (Root Key Hierarchy) — после неё derive-from-root заменит read-old-then-re-encrypt.
- **7 new/updated Konsist fitness rules**: ban lazysodium, com.launcher.api.crypto, family.* namespace, fake crypto в production, backdoor logging (raw bytes / PII в logcat).
- **Никаких новых dependencies** — удаление 2 (lazysodium + JNA).
- **Manual smoke** на Xiaomi 11T (`17f33878`, единственное доступное real device).

**На что смотреть с осторожностью:**
- **Phase 4 namespace rename** — critical, если миссим `@SerialName` — драматично сломаем Firestore document compatibility (SC-013 fail). Plan-time grep audit обязателен ДО самого rename.
- **Silent migration на устройстве с pre-TASK-51 ключами** — Xiaomi 11T у владельца не имеет успешных pairing'ов (всегда крашился), поэтому миграционная логика **не тестируется на реальных данных**. Если в будущем production устройство будет иметь persisted lazysodium-state — это путь не verified end-to-end в TASK-51 scope.
- **CancellationException re-throw в universal `try/catch`** — easily missed bug. Konsist rule обязателен для enforcement.

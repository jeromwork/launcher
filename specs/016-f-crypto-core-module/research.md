# Phase 0 Research: F-CRYPTO

**Date**: 2026-06-17
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Этот документ — research для one-way doors из spec'и. Каждое решение содержит: альтернативы, trade-off, выбранный путь, exit ramp.

---

## R1 — libsodium binding для Kotlin Multiplatform

### Context

F-CRYPTO нужна реализация: XChaCha20-Poly1305 (AEAD), X25519 (ECDH), Ed25519 (signing), HKDF-SHA256 (KDF) + `crypto_box_seal`/`crypto_box_seal_open` (sealed-box envelope для ADR-008 social recovery). Все эти примитивы есть в libsodium (BSD-licensed, industry standard).

Вопрос: **через какой Kotlin binding** мы будем дёргать libsodium на разных платформах?

### Alternatives

| Option | Pros | Cons |
|---|---|---|
| **A. `ionspin/kotlin-multiplatform-libsodium`** | Single binding для всех KMP targets (Android/JVM/iOS/JS/Native), maintained, используется в нескольких opensource KMP проектах. Drop-in `commonMain` API. | Зависит от одного maintainer'а. Из-за этого требует периодического sanity-check. iOS support через cinterop к prebuilt static libs — может быть quirky. |
| **B. BouncyCastle (Android) + own cinterop libsodium (iOS)** | Известные battle-tested библиотеки. BouncyCastle давно на Android. | Два разных binding'а → разные code paths для Android/iOS. expect/actual для каждого port, потенциальные divergence bugs. BouncyCastle не имеет XChaCha20 нативно. |
| **C. JCA (Android) + iOS CryptoKit + own portable layer** | Use platform-native crypto (well-supported). | Платформенный crypto **не** поддерживает Curve25519 на Android Keystore нативно — пришлось бы делать software fallback. CryptoKit P-256 only. Cross-platform parity невозможна без compatibility layer. |
| **D. Pure-Kotlin software implementation** | Zero external deps. | **Никогда не делать.** Самописная криптография = automatic security disqualification. |

### Decision

**Selected: A — `ionspin/kotlin-multiplatform-libsodium`** при условии, что research-time проверка (ниже) показывает library активной.

**Why A over B**: A даёт **single source of truth** для крипто-байтов на всех платформах. CrossPlatformVectorParityTest (FR-022) — ключевое требование. С B мы получили бы два разных backend'а с потенциально разными edge cases (например, padding nuances), которые периодически бы расходились в edge cases.

**Why A over D**: см. spec.md Assumptions — мы **не** пишем свою криптографию.

### Research-time проверка (обязательно сделать до Phase 4)

Перед началом имплементации libsodium adapters:

1. Открыть https://github.com/ionspin/kotlin-multiplatform-libsodium.
2. Проверить:
   - **Last release date**: должно быть < 12 месяцев.
   - **Open critical iOS issues**: проверить tag `ios`. Если есть критические open issues > 90 дней без ответа → red flag.
   - **iOS targets support**: `iosX64`, `iosArm64`, `iosSimulatorArm64` объявлены в их `build.gradle.kts`?
   - **XChaCha20-Poly1305 + `crypto_box_seal`/`crypto_box_seal_open` доступны**: эти конкретные функции должны быть exposed в KMP API. Если только `chacha20poly1305` без XChaCha — нужен fallback.
3. Записать конкретную версию + commit SHA в `core/crypto/build.gradle.kts`.

**Если research показывает library dead**: переходим на Option B (BouncyCastle Android + cinterop iOS). Это **+1-2 weeks effort** к estimated 2-3 weeks. Записать в `tasks.md` если применяется.

### Exit ramp

Если ionspin умрёт после merge'а F-CRYPTO:

1. Создать новую папку `core/crypto/bouncycastle/` (Android) + `core/crypto/cinterop/` (iOS).
2. Реализовать те же port'ы (`AeadCipher`, `AsymmetricCrypto`, etc.) через BouncyCastle / cinterop.
3. Прогнать **все** RFC KAT и Wycheproof тесты — они должны passing на новом backend'е (byte-level совместимость).
4. Поменять DI wiring в `app/`.
5. Удалить `core/crypto/libsodium/` package.

**Estimated swap cost**: 1-2 weeks. **Потребители (F-5, спека 011, мессенджер) не меняются** — это центральное требование Article XIII.

---

## R2 — iOS expect/actual стратегия для `SecureKeyStore`

### Context

Per Q1 clarification: iOS targets declared day 1 (FR-002), но **iOS adapter не реализуется** до первой iOS-фичи.

`SecureKeyStore` — единственный port, который не может быть `commonMain` implementation (поскольку Android Keystore и iOS Keychain — разные APIs). Остальные ports (`AeadCipher`, `AsymmetricCrypto`, etc.) реализуются через libsodium **в commonMain** (ionspin даёт common API).

Вопрос: как структурно объявить `SecureKeyStore` так, чтобы iOS target компилировался?

### Alternatives

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A. `expect class` + iOS actual = stub-screamer** | `commonMain` объявляет `expect class SecureKeyStore`. `iosMain` имеет `actual class` который при любом вызове throws `NotImplementedOnIosYetError()`. | Targets компилируются. iOS code paths видны как "не готовы". При попытке использовать — громкая ошибка, не silent corruption. | Программист должен помнить, что iOS-приложение упадёт if хоть кто-то вызовет `SecureKeyStore`. Mitigation: KDoc + integration tests на jvm/Android только. |
| **B. `expect class` + iOS actual = in-memory** | iOS actual использует in-memory HashMap. | Test-friendly. | Critically insecure для real iOS app. Easy to ship by accident. Refuse. |
| **C. Не declare iOS target до первой iOS-фичи** | Откатывает FR-002. | Минимум surface. | Когда iOS придёт — нужно переписывать структуру модуля (add target, expect/actual decls). Это **переписывание**, не дописывание → нарушает мета-правило 2026-06-17. |
| **D. `SecureKeyStore` только в `androidMain`, не в `commonMain`** | iOS приложения не имеют access к `SecureKeyStore` port вообще. | Кажется проще. | Domain code на iOS не сможет dependency-inject `SecureKeyStore`. Cross-platform API surface не единый. Нарушает domain isolation. |

### Decision

**Selected: A — stub-screamer**.

Per мета-правило owner'а 2026-06-17: "откладываем что можно, при условии что отложенное добавляется как дописывание, не переписывание". Option A фиксирует контракт day 1; iOS implementation потом — это **замена одного файла** `iosMain/SecureKeyStore.kt`, остальной код не трогается. Это **дописывание**.

### Implementation sketch

```kotlin
// commonMain/api/SecureKeyStore.kt
expect class SecureKeyStore(context: KeyStoreContext) {
  suspend fun store(keyId: KeyId, secret: ByteArray)
  suspend fun load(keyId: KeyId): ByteArray?
  suspend fun delete(keyId: KeyId)
}

expect class KeyStoreContext  // platform-specific init data

// androidMain/SecureKeyStore.kt
actual class SecureKeyStore actual constructor(context: KeyStoreContext) {
  // Real implementation: Android Keystore wrap pattern
  // ... (см. R3)
}
actual class KeyStoreContext(val androidContext: Context)

// iosMain/SecureKeyStore.kt
actual class SecureKeyStore actual constructor(context: KeyStoreContext) {
  actual suspend fun store(keyId: KeyId, secret: ByteArray): Unit =
    throw NotImplementedOnIosYetError(
      "SecureKeyStore iOS adapter not implemented yet. " +
      "Tracked in spec V-1 (iOS Admin Preset)."
    )
  // ... аналогично load / delete
}
actual class KeyStoreContext  // empty data class for iOS (Keychain doesn't need pre-init)

// jvmMain/SecureKeyStore.kt (для JVM unit tests, НЕ для production)
actual class SecureKeyStore actual constructor(context: KeyStoreContext) {
  // In-memory HashMap, @VisibleForTesting equivalent
  // Build-config gate: not included in :app production
}
actual class KeyStoreContext
```

### Exit ramp

Когда придёт iOS-фича (V-1 iOS Admin Preset): заменяем `iosMain/SecureKeyStore.kt` на реальную Keychain implementation:

```kotlin
// Future iosMain/SecureKeyStore.kt
actual class SecureKeyStore actual constructor(context: KeyStoreContext) {
  // Use kSecAttrAccessibleAfterFirstUnlock
  // wrap Curve25519 priv as SecKey, store via SecItemAdd
  // ... (детали в V-1 spec)
}
```

Никакие commonMain / androidMain / consumers не меняются.

---

## R3 — Wrap pattern для Curve25519 в Android Keystore

### Context

Android Keystore TEE поддерживает RSA, EC P-256/384/521, AES, HMAC — **но НЕ Curve25519 (X25519/Ed25519)** на уровне hardware key storage. API 31+ добавил Ed25519 *attestation*, но не key storage primitive. (См. spec.md "Скрытое допущение".)

Нам нужно хранить X25519 + Ed25519 private keys так, чтобы:
- Plaintext private key **никогда** не лежит на диске.
- Извлечение требует cooperation с TEE (даже с root).

### Solution — wrap pattern

Двух-уровневое хранение:

1. **Layer 1 (TEE)**: AES-256-GCM ключ хранится в Android Keystore с alias `family-crypto-wrap-key-v1`. Параметры:
   - Purpose: `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`.
   - Algorithm: `KEY_ALGORITHM_AES`.
   - Block mode: `BLOCK_MODE_GCM`.
   - Padding: `ENCRYPTION_PADDING_NONE`.
   - `setIsStrongBoxBacked(true)` (Samsung Knox / Pixel Titan) — fallback на обычный TEE если не доступен.
   - `setUserAuthenticationRequired(false)` — нет биометрии (per spec.md Edge Cases — admin keys биометрию не требуют, чтобы не сломаться при смене PIN).
   - `setKeySize(256)`.

2. **Layer 2 (file)**: Curve25519 private key:
   - Сериализуется в `ByteArray` (32 bytes для X25519/Ed25519 raw).
   - Шифруется через Layer 1 cipher: `AES/GCM/NoPadding`.
   - Результат + IV + metadata складывается в `KeyBlob` data class.
   - Сериализуется как JSON через `kotlinx.serialization.json`.
   - Сохраняется в файл `/data/data/<pkg>/files/keys/<keyId.raw>.blob`.

### Properties

| Threat | Defense |
|---|---|
| Root + dump `/data/data/<pkg>/files/` | Видит JSON `KeyBlob` со `wrappedKey` bytes (зашифрованные). Не может расшифровать без TEE-key. |
| Root + Keystore CLI dump | TEE alias `family-crypto-wrap-key-v1` существует, но extract raw key из TEE невозможен без hardware-level attack ($$$, нужен физический доступ к чипу). |
| Подмена APK signature | Keystore проверяет APK signature при unwrap → wrap-key invalidated. Old blobs становятся unreadable. |
| App reinstall / clear data | Keystore удаляет alias → blobs unreadable. **Expected behavior** (см. scenarios.md Сценарий 5 + Сценарий 8 для recovery flow). |
| `KeyInfo.isInsideSecureHardware == false` (software fallback) | Adapter logs warning + (опционально) `KeystoreUnavailableException`. Production decision: hard-fail или warn → решить в plan-phase implementation. Защита weakened, не нулевая. |

### Alternatives — почему не P-256?

Альтернатива: перейти на **P-256 (NIST P256)** primitives на Android (так как Keystore их поддерживает нативно — ключи хранятся внутри TEE без wrap'а).

**Refused**, потому что:

1. Cross-platform compatibility — будущий мессенджер и iOS launcher лучше всего работают с Curve25519 (Signal Protocol, Wire, age, и т.п.).
2. libsodium вообще НЕ поддерживает P-256 нативно — пришлось бы тянуть BouncyCastle/JCA дополнительно.
3. Wrap pattern — **industrial standard** для Curve25519 на Android: Signal Android (`IdentityKeyUtil`), Bitwarden, Threema используют именно его. Мы не изобретаем кустарщину.

Trade-off: Wrap pattern немного **слабее** чем TEE-native key (теоретически можно атаковать "process memory while unwrapped"). Acceptable для нашего scope; для banking-grade — нужен HSM, что вне Article XIV §6 budget.

### Exit ramp

Если регулятор потребует hardware-attested keys (например, EU eIDAS):

- Переход на P-256 на Android (Keystore native) + Curve25519 на iOS (Keychain).
- Adapter `SecureKeyStore` меняется; ports не меняются.
- KeyBlob `algorithm` поле уже поддерживает миграцию (см. data-model.md).
- Migration: новые keys — P-256, старые Curve25519 keys — re-issue через social recovery (ADR-008).

---

## R4 — KeyBlob serialization format

### Context

Q5 clarification зафиксировал: **JSON для KeyBlob**. Этот research доуточняет детали.

### JSON layout

```json
{
  "schemaVersion": 1,
  "algorithm": "X25519",
  "createdAt": "2026-06-17T10:30:00Z",
  "retiredAt": null,
  "replacedBy": null,
  "wrappedKey": "base64-encoded-bytes...",
  "iv": "base64-encoded-12-bytes...",
  "wrapKeyAlias": "family-crypto-wrap-key-v1"
}
```

### Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `schemaVersion` | `Int` | Yes | Wire format version. Starts at 1. |
| `algorithm` | `String` | Yes | One of `"X25519"`, `"Ed25519"`, `"AES-256"` (future). |
| `createdAt` | `Instant` (ISO-8601 string) | Yes | When key was generated. |
| `retiredAt` | `Instant?` | No | When key was retired (rotation). Null if active. |
| `replacedBy` | `KeyId?` (String) | No | If retired, which key superseded. Null if active. |
| `wrappedKey` | `ByteArray` (base64) | Yes | AES-256-GCM ciphertext of private key. |
| `iv` | `ByteArray` (base64, 12 bytes) | Yes | GCM nonce/IV for `wrappedKey`. |
| `wrapKeyAlias` | `String` | Yes | Android Keystore alias used to wrap (for rotation). |

### Forward compat policy

- **Adding optional field**: minor version bump (1.0 → 1.1), no migrator needed. Default value на read.
- **Renaming/removing field**: major bump (1.x → 2.0), migrator written **before** release.

### Backward compat fixture

`commonTest/resources/key-blob/v1-sample.json`:

```json
{
  "schemaVersion": 1,
  "algorithm": "X25519",
  "createdAt": "2026-06-17T10:30:00Z",
  "retiredAt": null,
  "replacedBy": null,
  "wrappedKey": "ZGV0ZXJtaW5pc3RpYy10ZXN0LXdyYXBwZWQta2V5LWJ5dGVzAAAAAAAAAAA=",
  "iv": "AAAAAAAAAAAAAAAA",
  "wrapKeyAlias": "family-crypto-wrap-key-v1"
}
```

Этот файл commit'ится в момент 1.0.0 release и **никогда не меняется**. Любая future версия должна уметь его прочитать.

### Why not CBOR / Protobuf

- **CBOR**: компактнее, но debug-ability ценнее для F-CRYPTO (10 ключей на устройство, не миллион). Может быть стоит для media blobs в спеке 011 — отдельный вопрос той спеки.
- **Protobuf**: ещё компактнее, но требует .proto schema management. Overkill для 8 полей.
- **JSON**: можно открыть текстовым редактором, увидеть структуру, проверить миграцию. Win.

---

## R5 — RFC KAT и Wycheproof test vectors selection

### Context

FR-019, FR-020 требуют RFC test vectors + Wycheproof. Need to enumerate **which exact vectors** и где взять.

### RFC KAT sources

| RFC | Section | What to test | How many vectors |
|---|---|---|---|
| **RFC 7748** (X25519) | §6.1 | Compute `a*9 mod 2^255-19`, then `bob = a*alice_pub` → equals `alice = b*bob_pub` | 2 official test vectors |
| **RFC 8032** (Ed25519) | §7.1 | sign+verify, msg+key+sig tuples | Min 5 vectors (test1, test2, test3, test1024, testabc) |
| **RFC 8439** (ChaCha20-Poly1305) | Appendix A.2 + A.5 | ChaCha20 stream + Poly1305 auth + AEAD compound | A.2.1 (ChaCha20), A.3.2 (Poly1305), A.5 (AEAD) — 3 vectors total |
| **RFC 5869** (HKDF-SHA256) | Appendix A.1, A.2, A.3 | HKDF Extract + Expand | 3 vectors |

**Total**: ~13 RFC vectors. Кладём в `commonTest/resources/rfc-test-vectors/<rfc>.json` как deterministic JSON для parsing.

**Note про XChaCha20**: RFC 8439 покрывает ChaCha20 (regular), а не XChaCha20 (extended nonce). Для XChaCha20 test vectors берём из **IETF draft `draft-irtf-cfrg-xchacha-03`** Appendix A.

### Wycheproof subset

https://github.com/google/wycheproof — содержит **тысячи** test vectors. Для F-CRYPTO мы берём **subset**:

| File from Wycheproof | What it tests | Key vectors we include |
|---|---|---|
| `x25519_test.json` | X25519 edge cases | low-order points (all-zero pub, all-FF pub), point-at-infinity, twist-form points |
| `eddsa_test.json` | Ed25519 signature edge cases | malleable signatures (high-S), all-zero R, public key issues |
| `chacha20_poly1305_test.json` | AEAD AAD/nonce edge cases | empty plaintext, empty AAD, max-length cases |

**Snapshot pin**: на момент F-CRYPTO 1.0.0 release — fixate commit SHA Wycheproof repo, скачать выбранные JSON, commit в `commonTest/resources/wycheproof-subset/`.

**Refresh policy**: отдельный manual PR раз в 12 месяцев (или по trigger'у Wycheproof major update / новый CVE).

### Implementation note

RFC vectors часто содержат hex bytes — parsing через extension `String.hexToByteArray()` в test utils. Wycheproof JSON имеет nested structure (`testGroups[].tests[]`) — нужен small parser.

---

## R6 — `KeyId` prefix validation strategy

### Context

Q2 clarification: `KeyId` = `value class KeyId(val raw: String)` с валидацией префикса в `init`. `KeyNamespace` — sealed class с registered prefix'ами.

### Implementation sketch

```kotlin
@JvmInline
value class KeyId(val raw: String) {
  init {
    require(KeyNamespace.isValidPrefix(raw)) {
      "Invalid KeyId '$raw': must start with one of: ${KeyNamespace.allPrefixes()}"
    }
    require(raw.matches(Regex("^[a-z_-]+(-[a-z0-9-]+)+$"))) {
      "Invalid KeyId '$raw': must be kebab-case ASCII"
    }
  }
}

sealed class KeyNamespace(val prefix: String) {
  object Config : KeyNamespace("config-")
  object Media : KeyNamespace("media-")
  object Messenger : KeyNamespace("messenger-")
  object Recovery : KeyNamespace("recovery-")
  object Internal : KeyNamespace("__internal-")

  companion object {
    private val all = listOf(Config, Media, Messenger, Recovery, Internal)
    fun isValidPrefix(raw: String): Boolean = all.any { raw.startsWith(it.prefix) }
    fun allPrefixes(): List<String> = all.map { it.prefix }
  }
}
```

### Property tests

- Random valid prefix + suffix → `KeyId(...)` succeeds.
- Random invalid prefix → `IllegalArgumentException`.
- Edge case: empty string, prefix only, special chars.

### Future additions

When new feature module needs its own namespace — add new `object` to `KeyNamespace`, no migration of existing keys needed (prefixes are additive).

---

## R7 — Cross-platform vector parity strategy

### Context

FR-022 + Сценарий 2 — bytes должны быть идентичны на Android и JVM (и потенциально iOS).

### Approach

В `commonTest/resources/cross-platform-vectors/encryption-roundtrip-v1.json`:

```json
{
  "vectors": [
    {
      "name": "x25519_keypair_from_seed",
      "input": { "seed_hex": "0102...32_bytes" },
      "expected": {
        "private_hex": "0a0b...32_bytes",
        "public_hex": "1a1b...32_bytes"
      }
    },
    {
      "name": "aead_encrypt_known_key_nonce",
      "input": {
        "key_hex": "00...32_bytes",
        "nonce_hex": "00...24_bytes",
        "plaintext_hex": "48656c6c6f",
        "aad_hex": ""
      },
      "expected": {
        "ciphertext_hex": "<deterministic_output>"
      }
    },
    {
      "name": "ed25519_sign_known_msg",
      "input": {
        "private_hex": "...",
        "message_hex": "48656c6c6f"
      },
      "expected": {
        "signature_hex": "..."
      }
    }
  ]
}
```

Test runs on **both** `androidUnitTest` AND `jvmTest`. Same vectors, same expected bytes. If divergence — CI fail.

**Note**: AEAD encrypt в production использует random nonce. Для cross-platform test нам нужен **deterministic** path — special test-only API на adapter'е? Или special FakeAdapter с seeded RandomSource?

**Decision**: использовать **deterministic** encrypt path в test fixtures: test injects fixed nonce через test-specific constructor `LibsodiumAeadCipher(forcedNonce = ...)`. Этот constructor `internal` (compile-error если кто-то использует в production), помечен `@VisibleForTesting`.

---

## R8 — Detekt rule для FakeAdapter защиты

### Context

FR-018 + SC-011: Detekt должен ловить `Fake*Cipher` / `FakeAsymmetricCrypto` / `FakeSecureKeyStore` imports в `app/src/main/`.

### Detekt rule design

Custom rule `FakeCryptoInReleaseRule.kt` в `tools/detekt-rules/`:

```kotlin
class FakeCryptoInReleaseRule(config: Config) : Rule(config) {
  override val issue = Issue(
    id = "FakeCryptoInRelease",
    severity = Severity.CodeSmell,
    description = "Fake crypto adapters MUST NOT be imported in production source sets. Use real adapters via Koin DI.",
    debt = Debt.TWENTY_MINS
  )

  override fun visitImportDirective(importDirective: KtImportDirective) {
    val import = importDirective.importedFqName?.asString() ?: return
    val forbiddenPatterns = listOf(
      "family.crypto.fake.FakeAeadCipher",
      "family.crypto.fake.FakeAsymmetricCrypto",
      "family.crypto.fake.FakeKeyDerivation",
      "family.crypto.fake.FakeRandomSource",
      "family.crypto.fake.FakeSecureKeyStore",
    )

    if (forbiddenPatterns.any { import.startsWith(it) }) {
      val filePath = importDirective.containingFile.virtualFile?.path ?: ""
      // Allow in test source sets
      if (filePath.contains("/src/test/") || filePath.contains("/src/androidTest/") || filePath.contains("/src/commonTest/")) return
      // Allow in debug source set
      if (filePath.contains("/src/debug/")) return

      report(CodeSmell(issue, Entity.from(importDirective),
        "Fake crypto adapter imported in production code: '$import'"))
    }
  }
}
```

**Rule placement**: `tools/detekt-rules/src/main/kotlin/` (separate Gradle subproject). Rule registered in `detekt-config.yml` for `:app` module.

**Verification**: unit test for the rule itself with positive (`/src/main/` import → fail) and negative (`/src/test/` import → ok) cases.

---

## R9 — `data_extraction_rules.xml` exclude policy

### Context

Security checklist O-2 medium open: Android Auto Backup default sends `/data/data/<pkg>/files/` to user's Google Drive. F-CRYPTO blobs are wrapped by Keystore TEE key → restored backup is **unreadable** on new device (TEE key уничтожена при reset).

→ Bad UX: user думает "у меня backup есть, всё восстановится", а на самом деле — нет.

### Approach

Edit `app/src/main/res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
  <cloud-backup>
    <!-- Existing rules ... -->
    <exclude domain="file" path="keys/" />
  </cloud-backup>
  <device-transfer>
    <!-- Existing rules ... -->
    <exclude domain="file" path="keys/" />
  </device-transfer>
</data-extraction-rules>
```

Plus добавить в `AndroidManifest.xml` если ещё не указано:

```xml
<application
  android:dataExtractionRules="@xml/data_extraction_rules"
  android:fullBackupContent="@xml/backup_rules" />
```

Аналогично legacy `backup_rules.xml` (Android 11 и ниже).

### Verification

Manual: с включённой Auto Backup, дать app полететь — проверить, что `files/keys/` не попадает в archive. Unfortunately нет автоматического теста на это (требует full backup cycle через ADB).

**Note**: запустить research, есть ли способ через `adb shell bmgr backupnow` + дамп archive structure. Это `TODO(plan-phase)`.

---

## Summary

| Research item | Decision | Status |
|---|---|---|
| R1 — libsodium binding | ionspin/kotlin-multiplatform-libsodium (subject to research-time verification) | Verify before Phase 4 |
| R2 — iOS strategy | expect/actual with stub-screamer iOS actual | Done |
| R3 — Wrap pattern detail | AES-256-GCM wrap key в Android Keystore + JSON KeyBlob в app sandbox | Done |
| R4 — KeyBlob format | JSON, schemaVersion=1 day 1, fixture для backward-compat test | Done |
| R5 — RFC KAT + Wycheproof | RFC 7748/8032/8439/5869 + IETF draft XChaCha20 + Wycheproof x25519/eddsa/chacha20poly1305 subset | Implement Phase 4 |
| R6 — KeyId validation | value class with prefix validation via sealed KeyNamespace | Done |
| R7 — Cross-platform parity | Deterministic vectors + `@VisibleForTesting forcedNonce` test API | Done |
| R8 — Detekt rule | Custom rule `FakeCryptoInReleaseRule` in `tools/detekt-rules/` | Implement Phase 11 |
| R9 — Backup rules | `data_extraction_rules.xml` excludes `files/keys/` | Implement Phase 10 |

All one-way doors have exit ramps documented. Plan goes to next phase.

---

## TL;DR простым языком

Эта research-фаза отвечает на 9 «как именно» вопросов, которые spec оставил открытыми:

1. **Какой именно binding libsodium брать** — берём ionspin/kotlin-multiplatform-libsodium как primary; если research-проверка (перед началом кода) покажет, что библиотека не живёт — план Б с BouncyCastle.

2. **Что писать в iOS-папке** — заглушку, которая кричит «не реализовано». iOS-приложение упадёт явно, не «работает мусорно».

3. **Как Curve25519 ключи лежат на Android** — двух-уровневая защита: AES-ключ внутри защищённого чипа TEE → им шифруем настоящий ключ → результат в файл приложения. Без TEE расшифровать нельзя.

4. **Формат файла ключа** — JSON с `schemaVersion`, удобно отлаживать.

5. **Какие именно тестовые векторы брать** — конкретные пункты из стандартов RFC + от Google.

6. **Как защититься от опечаток в именах ключей** — обёрнутая строка с проверкой при создании.

7. **Как гарантировать одинаковые байты на Android и JVM** — один файл векторов на обе платформы.

8. **Как защитить production от FakeAdapter** — специальное Detekt-правило проверки кода + проверка во время запуска.

9. **Как настроить Android backup, чтобы он не отправлял ключи в Google Drive** — конкретное правило в `data_extraction_rules.xml`.

Все 9 решений принято, у каждого есть план Б на случай провала. Дальше идёт data-model.md (типы данных) и contracts/ (форматы данных).

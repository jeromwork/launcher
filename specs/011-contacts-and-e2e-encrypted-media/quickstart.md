# Quickstart: Spec 011 (E2E Crypto Foundation) Implementation

**Spec**: [spec.md](spec.md)
**Plan**: [plan.md](plan.md)
**Audience**: разработчик, начинающий Phase 0 — Phase 1 спека 011.
**Date**: 2026-05-21 / rev. 2 2026-05-22 (scope-split)

Этот документ — стартовый чек-лист «что добавить / сконфигурировать», прежде чем писать первую строчку crypto-кода для фундамента.

---

## §1. Add Lazysodium-android dependency

Edit `gradle/libs.versions.toml`:

```toml
[versions]
lazysodium = "5.1.0"  # verify latest stable at Maven Central before Phase 0 commit
jna = "5.13.0"

[libraries]
lazysodium-android = { module = "com.goterl:lazysodium-android", version.ref = "lazysodium" }
jna = { module = "net.java.dev.jna:jna", version.ref = "jna" }
```

Edit `core/build.gradle.kts` (Android source set only):

```kotlin
kotlin {
    sourceSets {
        androidMain {
            dependencies {
                implementation(libs.lazysodium.android)
                implementation(libs.jna) { artifact { type = "aar" } }
            }
        }
    }
}
```

**Why `androidMain` only**: Lazysodium-android ships .so files for Android. Pure JVM tests (на CI) need separate setup — see §3.

---

## §2. Configure ABI splits (release build only)

Edit `app/build.gradle.kts`:

```kotlin
android {
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false  // don't bundle all ABIs into one big APK
        }
    }
}
```

**Effect**: Google Play receives 4 APK variants (one per ABI). Each user device downloads only its own ABI's .so — ~300 KiB instead of ~1.2 MiB for crypto deps.

**Local debug builds**: ABI splits disabled (default). Universal APK includes all ABIs. APK size impact ~1.2 MiB only locally.

---

## §3. Configure CI for libsodium desktop tests

`commonTest` for `AeadCipherContractTest` / `AsymmetricCryptoContractTest` runs on JVM (no Android), but Lazysodium needs **libsodium native library** available.

**Ubuntu CI** (`.github/workflows/test.yml` or равnoval):
```yaml
- name: Install libsodium
  run: sudo apt-get update && sudo apt-get install -y libsodium23
```

**macOS CI**:
```yaml
- name: Install libsodium
  run: brew install libsodium
```

**Local developer machine** — same one-liner. **Windows local development**: WSL2 + libsodium23 via apt. Native Windows libsodium support через Lazysodium possible but documented as fragile — use WSL.

---

## §4. Add Firebase Storage dependency

Edit `gradle/libs.versions.toml`:

```toml
[libraries]
firebase-storage = { module = "com.google.firebase:firebase-storage-ktx" }
```

Edit `core/build.gradle.kts` androidMain:
```kotlin
implementation(libs.firebase.storage)
```

Verify Firebase BoM version в проекте (existing from спека 007) covers Storage — should be implicit per Firebase BoM.

---

## §5. Initialize Storage Emulator for tests

`firebase.json` extension (add Storage emulator port):

```json
{
  "emulators": {
    "firestore": { "port": 8080 },
    "auth": { "port": 9099 },
    "storage": { "port": 9199 }
  }
}
```

`storage.rules` (new file at project root or в `firebase/`):

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /links/{linkId}/private-media/{uuid} {
      // copy rules from contracts/encrypted-media-storage.md §Storage Rules
    }
    match /{document=**} { allow read, write: if false; }
  }
}
```

Start emulator:
```bash
firebase emulators:start --only firestore,auth,storage
```

---

## §6. Initialize Android Keystore wrapper

`core/src/androidMain/kotlin/com/launcher/adapters/crypto/AndroidKeystoreSecureKeystore.kt`:

```kotlin
class AndroidKeystoreSecureKeystore(
    private val context: Context,
) : SecureKeystore {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        // alias prefixes
        private const val ALIAS_X25519_AES_WRAP = "launcher_aes_wrap_x25519"
        private const val ALIAS_ED25519_NATIVE = "launcher_ed25519_native"
        private const val ALIAS_ED25519_AES_WRAP = "launcher_aes_wrap_ed25519"  // fallback for API 30
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    // X25519 (encryption) — ALWAYS AES-wrap (Android Keystore не поддерживает X25519 нативно)
    override fun generateAndStoreEncryption(alias: String): DeviceKeyPair {
        // 1. libsodium generates X25519 keypair (Pub, Priv) — выполняется in-memory.
        // 2. Sgenerate AES-256 ключ в Keystore с aliasPrefix + "_aes" (StrongBox/TEE if available).
        // 3. Шифровать X25519 priv bytes этим AES-ключом (AES-GCM).
        // 4. Persist encrypted X25519 priv bytes + nonce в EncryptedSharedPreferences под alias.
        // 5. Return DeviceKeyPair с opaque PrivateKey wrapping the alias.
        TODO("implement in Phase 3")
    }

    // Ed25519 (signing) — native Keystore с API 31+, AES-wrap fallback на API 30
    override fun generateAndStoreSigning(alias: String): DeviceSigningKeyPair {
        val useNative = Build.VERSION.SDK_INT >= 31
        if (useNative) {
            // Native path: KeyPairGenerator с algorithm "Ed25519"
            // KeyGenParameterSpec.Builder(alias, PURPOSE_SIGN | PURPOSE_VERIFY)
            //   .setAlgorithmParameterSpec(NamedParameterSpec("Ed25519"))
            //   .setIsStrongBoxBacked(true) // try-catch fallback to TEE
            //   .build()
            TODO("Phase 3 native Ed25519")
        } else {
            // AES-wrap fallback (same as X25519 path)
            TODO("Phase 3 AES-wrap fallback")
        }
    }

    override fun loadEncryption(alias: String): Result<DeviceKeyPair, CryptoError> = TODO()
    override fun loadSigning(alias: String): Result<DeviceSigningKeyPair, CryptoError> = TODO()
    override fun delete(alias: String) = TODO()
    override fun exists(alias: String): Boolean = TODO()
}
```

**Important caveats**:
- **Android Keystore does NOT natively support X25519** (на 2026-05-22). Strategy — wrap private bytes with Keystore-held AES key. AES-key lives в StrongBox/TEE if available. Documented in research.md §3.
- **Android Keystore Ed25519** — native с API 31+ (Android 12). На API 30 (наш minSdk) — fallback на AES-wrap (тот же паттерн, что для X25519). Documented in research.md §2b.

---

## §7. Update `Link.KNOWN_SUBCOLLECTIONS`

`core/src/commonMain/kotlin/com/launcher/api/link/Link.kt` — extend the list (see plan.md §Architecture):

```kotlin
companion object {
    val KNOWN_SUBCOLLECTIONS: List<String> = listOf(
        "state", "config", "capabilities", "health", "commands", "configHistory",
        // NEW spec 011:
        "devices",
        "deviceOwnership",
    )

    // Storage paths (separate list — not Firestore subcollections)
    val KNOWN_STORAGE_PATHS: List<String> = listOf(
        "private-media",  // NEW spec 011
    )
}
```

Then update `LinkRegistry.revoke()` to enumerate `KNOWN_STORAGE_PATHS` and delete corresponding objects from Firebase Storage. See data-model.md §4 (Time T6).

---

## §8. Add SQLDelight schema files

Только **одна** таблица в 011 — `BlobReferenceLedger`. `PrivateMediaCache` (для расшифрованных bytes) — это спек 012.

`core/src/commonMain/sqldelight/com/launcher/db/BlobReferenceLedger.sq`:

```sql
-- See data-model.md §3 for full schema
CREATE TABLE BlobReferenceLedger (...);
```

`core/src/commonMain/sqldelight/com/launcher/db/SystemMeta.sq` *(new в 011 rev. 2)*:

```sql
-- For clear-data sentinel (research.md §5c, CHK-FR-015)
CREATE TABLE SystemMeta (
    key TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL,
    updatedAt INTEGER NOT NULL
);

-- Keys used:
--   "clearDataAt" — epoch millis of first DB initialization after clear-data
--   (more keys may be added by future specs)
```

Bump SQLDelight `schemaVersion` in `core/build.gradle.kts`:
```kotlin
sqldelight {
    databases {
        create("ConfigSyncDatabase") {
            packageName.set("com.launcher.db")
            // existing config from spec 008; bump migration version
        }
    }
}
```

Create migration file `core/src/commonMain/sqldelight/migrations/1.sqm` (or higher number depending on current state):

```sql
CREATE TABLE BlobReferenceLedger (
    uuid TEXT NOT NULL,
    linkId TEXT NOT NULL,
    refSource TEXT NOT NULL,
    refUpdatedAt INTEGER NOT NULL,
    PRIMARY KEY (uuid, refSource)
);
CREATE INDEX idx_blob_ref_uuid ON BlobReferenceLedger(uuid);
CREATE INDEX idx_blob_ref_link ON BlobReferenceLedger(linkId);

CREATE TABLE SystemMeta (
    key TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL,
    updatedAt INTEGER NOT NULL
);
```

---

## §9. `SRV-CRYPTO-001` в server-roadmap — ✅ done

Запись `SRV-CRYPTO-001` (универсальный маршрут переезда крипто-инфраструктуры на собственный backend, не привязан к Firebase лимитам) — уже добавлена в [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md) section §CRYPTO + PRIVATE MEDIA STORAGE на 2026-05-22.

**Note**: устаревшее имя `SRV-MEDIA-001` не используется. Если встретится в коде / спеках — переименовать в `SRV-CRYPTO-001`.

---

## §10. Forward-compat — N/A в 011

Спек 011 не создаёт `private:<uuid>` URIs ни в каких `/config` (это спек 012). Forward-compat между разными версиями приложения становится проблемой только когда спек 012 начинает наполнять `Contact.photoRef`. Анализ — в quickstart.md спека 012.

В 011 only forward-compat concern — envelope `cipherSuiteId` registry (forward-readers MUST return `CipherSuiteUnsupported` на unknown values). Phase 2 wire-format test покрывает это.

---

## §11. Setup ADR-007 draft

`docs/adr/ADR-007-trust-edge-bootstrap-subtypes.md`:

```markdown
# ADR-007: TrustEdgeBootstrap Subtypes for Per-Device Asymmetric Keys

**Status:** Draft (Phase 0); Accepted on Phase 1 completion.
**Decided in:** spec 011 mentor session 2026-05-21.

## Context

[Copy from research.md §8]

## Decision

[Copy from research.md §8]

## Consequences

[Copy from research.md §8]
```

Polished after Phase 1 — initial finalization gate for Phase 2.

---

## §12. First commits, push, PR open

Per CLAUDE.md §Branching:
1. Phase 0 changes committed (deps + emulator setup + roadmap entries) — one logical commit.
2. Push to remote.
3. Open PR for spec 011 with link to spec/plan/research/data-model/contracts/quickstart.
4. CI green = Phase 0 complete.

---

<!-- novice summary -->

## TL;DR (простым языком)

**Что в этом файле.** Пошаговый чек-лист «что добавить в проект перед тем как начать писать crypto-код для фундамента». 12 шагов, около получаса работы.

**Главное (Phase 0 deliverables):**

1. **Добавить Lazysodium-android + JNA + kotlinx-serialization-cbor** в gradle (для крипто + CBOR envelope).
2. **Включить ABI splits** в release-сборке (иначе APK потяжелеет на 1.2 MB).
3. **Установить libsodium на CI machines** — apt-get / brew одной командой.
4. **Добавить Firebase Storage SDK** + сконфигурировать Storage Emulator для тестов.
5. **Скелет для Android Keystore wrapper** с **двумя стратегиями**: X25519 priv через AES-wrap (Keystore не поддерживает X25519 нативно), Ed25519 priv — native на API 31+ / AES-wrap fallback на API 30.
6. **Расширить `Link.KNOWN_SUBCOLLECTIONS`** — добавить `"devices"` (Firestore subcollection с Pub-ключами) + `"private-media"` в `KNOWN_STORAGE_PATHS` (новый список Storage paths для recursive revoke).
7. **Добавить две таблицы в SQLDelight**: `BlobReferenceLedger` (счётчик references) + `SystemMeta` (sentinel для clear-data detection). `PrivateMediaCache` НЕ создаём — это спек 012.
8. **`SRV-CRYPTO-001` уже в server-roadmap** ✅ (добавлено 2026-05-22).
9. **Создать draft ADR-007** про TrustEdgeBootstrap subtypes для per-device asymmetric keys + Pub publication через Firestore.
10. **Закоммитить + push + PR** — Phase 0 готова.

**После этого** можно начинать Phase 1: ADR-007 finalization + 8 domain types + 8 port interfaces (включая новые DigitalSignature, HashFunction, DeviceSigningKeyPair) в commonMain.

**Что НЕ в этом quickstart** (потому что не в 011 scope):
- UI код — спек 012.
- PrivateMediaResolver / PrivateMediaCache — спек 012.
- Document picker / viewer — спек 012.
- Real photo encrypt performance budgets — спек 012.

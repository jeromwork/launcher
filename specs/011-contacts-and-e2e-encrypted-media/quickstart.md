# Quickstart: Spec 011 Implementation

**Spec**: [spec.md](spec.md)
**Plan**: [plan.md](plan.md)
**Audience**: разработчик, начинающий Phase 0 — Phase 1 спека 011.

Этот документ — стартовый чек-лист «что добавить / сконфигурировать», прежде чем писать первую строчку crypto-кода.

---

## §1. Add Lazysodium-android dependency

Edit `gradle/libs.versions.toml`:

```toml
[versions]
lazysodium = "5.1.4"  # verify latest stable at Maven Central before Phase 0 commit
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
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    override fun generateAndStore(alias: String): DeviceKeyPair {
        // libsodium generates the X25519 keypair (Android Keystore alone does not support X25519 directly).
        // Strategy: generate X25519 in user-space (libsodium), then wrap PRIVATE half with an AES key
        // that lives in Android Keystore. Encrypted private bytes persist in EncryptedSharedPreferences.
        // This gives StrongBox/TEE protection to the wrapping key.
        TODO("implement in Phase 3")
    }

    override fun loadKeyPair(alias: String): DeviceKeyPair = TODO()
    override fun delete(alias: String) = TODO()
    override fun exists(alias: String): Boolean = TODO()
}
```

**Important caveat**: Android Keystore does NOT natively support X25519 (на 2026-05-21). Strategy explained — wrap private bytes with Keystore-held AES key. This is documented in research.md §3 and warrants its own Phase 3 attention.

**Alternative considered**: use Android Keystore-native Ed25519/EC algorithms instead of X25519. Rejected — would require additional libsodium primitives outside `crypto_box_seal`, more complex.

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

`core/src/commonMain/sqldelight/com/launcher/db/PrivateMediaCache.sq`:

```sql
-- See data-model.md §3 for full schema
CREATE TABLE PrivateMediaCache (...);
```

`core/src/commonMain/sqldelight/com/launcher/db/BlobReferenceLedger.sq`:

```sql
-- See data-model.md §3 for full schema
CREATE TABLE BlobReferenceLedger (...);
```

Bump SQLDelight `schemaVersion` in `core/build.gradle.kts`:
```kotlin
sqldelight {
    databases {
        create("ConfigSyncDatabase") {
            packageName.set("com.launcher.db")
            // existing config from spec 008; bump migration version when adding tables
        }
    }
}
```

Create migration file `core/src/commonMain/sqldelight/migrations/1.sqm` (or higher number depending on current state):

```sql
CREATE TABLE PrivateMediaCache (...);
CREATE INDEX idx_private_media_cache_link ON PrivateMediaCache(linkId);
CREATE INDEX idx_private_media_cache_accessed ON PrivateMediaCache(accessedAt);

CREATE TABLE BlobReferenceLedger (...);
CREATE INDEX idx_blob_ref_uuid ON BlobReferenceLedger(uuid);
CREATE INDEX idx_blob_ref_link ON BlobReferenceLedger(linkId);
```

---

## §9. Add `SRV-MEDIA-001` to server-roadmap

Edit `docs/dev/server-roadmap.md` — append in section §SECURITY + COMPLIANCE (или create new section §MEDIA STORAGE):

```markdown
**SRV-MEDIA-001: Migrate private-media Storage from Firebase to own server.**
- *Сейчас:* Firebase Storage Spark plan (5 GB limit, 1 GB/day download).
- *Проблема:* выход за лимиты при ≥250-500 пар активного использования (см. spec 011 research.md §4).
- *Сервер должен:* блоб-хранилище (S3-compatible, MinIO, или прямо на disk собственного VPS) с теми же Security guarantees (доступ только members of link). Размер квот определяется бизнес-моделью.
- *Когда поедет:* при превышении 4 GB total storage OR 800 MB/day download (мониторинг через Firebase Console).
- *Зависимости:* SRV-CONFIG-001 (как минимум один server-side компонент уже работает).
- *Note:* envelope format (CBOR) и Security model (member-of-link gating) language- and storage-agnostic — миграция blob'ов = re-upload existing files, не требует перешифровки.
```

---

## §10. Verify forward-compat для Managed на 010

Перед коммитом первого blob'a на admin device проверить:
- Managed на спеке 010 видит `private:<uuid>` → `IconStorage.resolve()` per спек 006 returns `Placeholder` (gracefully).
- Tile показывает generic placeholder.
- Никакого crash или error log на 010 Managed.

Тестовый сценарий — Phase 5/8 integration test, или manual в Phase 11 smoke.

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

**Что в этом файле.** Пошаговый чек-лист «что добавить в проект перед тем как начать писать crypto-код». 12 шагов, около получаса работы.

**Главное:**

1. **Добавить Lazysodium-android** в gradle (одна строка зависимости).
2. **Включить ABI splits** в release-сборке (иначе APK потяжелеет на 1.2 MB).
3. **Установить libsodium на CI machines** — apt-get / brew одной командой.
4. **Добавить Firebase Storage SDK** + сконфигурировать Storage Emulator для тестов.
5. **Скелет для Android Keystore wrapper** — с важным caveat'ом: Keystore не поддерживает X25519 нативно, поэтому private bytes храним под AES-обёрткой от Keystore.
6. **Расширить `Link.KNOWN_SUBCOLLECTIONS`** двумя новыми subcollection (`devices`, `deviceOwnership`) + один новый Storage path (`private-media`).
7. **Добавить две таблицы в SQLDelight** (расширяет существующую DB из спека 008).
8. **Записать `SRV-MEDIA-001` в server-roadmap** — когда мигрировать Storage с Firebase на свой сервер (порог 4 GB total / 800 MB/day download).
9. **Создать draft ADR-007** про TrustEdgeBootstrap subtypes.
10. **Закоммитить + push + PR** — Phase 0 готова.

**После этого** можно начинать Phase 1: ADR-007 finalization + domain types в commonMain.

**Что НЕ в этом quickstart**:
- UI код (Phase 9).
- Konsist fitness rules (Phase 10).
- Macrobenchmark performance tests (Phase 11).
- Smoke и docs обновление (Phase 12).

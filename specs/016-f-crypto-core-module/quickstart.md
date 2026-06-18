# Quickstart: F-CRYPTO

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Dev workflow для разработчика, который будет имплементировать или работать с F-CRYPTO.

---

## Prerequisites

- **JDK 21** (project default — confirmed in current launcher build).
- **Kotlin 2.1+** Gradle plugin.
- **Android SDK 35+** (for `:app` build), API 23+ for F-CRYPTO Android adapter.
- **Android emulator preset `pixel_5_api_34`** для instrumentation tests (создаётся через skill `android-emulator`).
- macOS — **только** для будущей iOS-фичи; F-CRYPTO MVP не требует macOS (iOS targets declared но CI skipped).

---

## Module setup

### Add to `settings.gradle.kts`

```kotlin
include(":core:crypto")
```

### `core/crypto/build.gradle.kts` outline

```kotlin
plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.kotlinSerialization)
}

kotlin {
  androidTarget()
  jvm()
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.ionspin.libsodium)  // verify version in research phase
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotest.runner.junit5)
      implementation(libs.kotest.property)
    }
    androidMain.dependencies {
      // Android Keystore API is in androidx.security
    }
  }
}

android {
  namespace = "family.crypto"
  compileSdk = 35
  defaultConfig {
    minSdk = 23  // Android Keystore requirement
  }
}

// TODO(extract-when-2nd-consumer): когда появится мессенджер / фото-приложение
// / EOS / Android TV — git filter-repo в отдельный приватный репо.
// License на extract = Apache 2.0 (Kerckhoffs's principle).
```

### Verify no launcher-module dependencies (Article XIII fitness function)

```bash
./gradlew :core:crypto:dependencies | grep "project:"
# Should be empty — no `project:core:foo`, `project:app`, etc.
```

---

## Build commands

### Build module

```bash
./gradlew :core:crypto:assemble
```

### Run all common + JVM tests (no emulator needed)

```bash
./gradlew :core:crypto:jvmTest
```

Expected duration: < 5 минут cold cycle.

### Run Android unit tests (debug variant)

```bash
./gradlew :core:crypto:testDebugUnitTest
```

### Run Android instrumentation tests (требует эмулятор)

```bash
# 1. Start emulator pixel_5_api_34 (via skill android-emulator):
#    Skill creates AVD + boots it.

# 2. Run instrumentation tests:
./gradlew :core:crypto:connectedDebugAndroidTest
```

### Run property tests с 1000 iterations (как на CI)

```bash
./gradlew :core:crypto:jvmTest -PkotestPropertyIterations=1000
```

Default локально = 100 iterations (быстро). CI = 1000.

---

## Fixture layout

```
core/crypto/src/commonTest/resources/
├── rfc-test-vectors/
│   ├── rfc7748-x25519.json       # X25519 vectors from RFC 7748 §6.1
│   ├── rfc8032-ed25519.json       # Ed25519 vectors from RFC 8032 §7.1
│   ├── rfc8439-chacha20-poly1305.json  # AEAD vectors from RFC 8439 App. A
│   ├── xchacha20-ietf-draft.json   # XChaCha20 from IETF draft App. A
│   └── rfc5869-hkdf.json           # HKDF vectors from RFC 5869 App. A
├── wycheproof-subset/
│   ├── x25519_test.json           # subset from google/wycheproof
│   ├── eddsa_test.json
│   └── chacha20_poly1305_test.json
├── cross-platform-vectors/
│   └── encryption-roundtrip-v1.json  # deterministic vectors for parity test
└── key-blob/
    ├── v1-sample.json              # backward-compat fixture (frozen at 1.0.0)
    └── v1-retired-sample.json
```

### Updating fixtures

- **RFC vectors**: manual — extract from RFC text once, never updated (RFCs immutable).
- **Wycheproof subset**: manual PR — pull from `google/wycheproof` at a pinned commit. Update раз в 12 месяцев или при критическом обновлении.
- **Cross-platform vectors**: generated once during F-CRYPTO 1.0.0 development — checked in, then frozen.
- **`key-blob/v1-sample.json`**: frozen at 1.0.0 release. **NEVER edit** — would break backward-compat test guarantees.

---

## DI wiring (`:app`)

### `app/src/main/.../CryptoModule.kt` (release)

```kotlin
val cryptoModule = module {
  single<AeadCipher> { LibsodiumAeadCipher() }
  single<AsymmetricCrypto> { LibsodiumAsymmetricCrypto() }
  single<KeyDerivation> { LibsodiumKeyDerivation() }
  single<RandomSource> { LibsodiumRandomSource() }
  single { SecureKeyStore(KeyStoreContext(androidContext())) }
  single<KeyRotation> { StubKeyRotation() }
  single<KeyEscrow> { StubKeyEscrow() }
}
```

### `app/src/debug/.../DebugCryptoModule.kt` (debug)

Same as release — Fake adapters MUST NOT be wired in `:app` runtime even in debug. Fakes are test-only.

### Application initialization assertion

```kotlin
// app/src/main/.../FamilyLauncherApplication.kt
class FamilyLauncherApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    startKoin {
      modules(cryptoModule, /* other modules */)
    }
    assertNoFakeCryptoInRelease()  // see below
  }

  private fun assertNoFakeCryptoInRelease() {
    if (BuildConfig.DEBUG) return
    val cipher = get<AeadCipher>()
    check(cipher::class.simpleName?.startsWith("Fake") != true) {
      "FATAL: Fake crypto adapter detected in release build. Check CryptoModule."
    }
  }
}
```

---

## Detekt rule

### Add to `tools/detekt-rules/` (new subproject)

```kotlin
// tools/detekt-rules/src/main/.../FakeCryptoInReleaseRule.kt
class FakeCryptoInReleaseRule : Rule(...) {
  // See research.md R8 for full implementation
}
```

### Register in `detekt-config.yml`

```yaml
family-launcher:
  FakeCryptoInRelease:
    active: true
```

### Run

```bash
./gradlew detekt
```

CI fails if Fake* imported in `/src/main/` (excluding test source sets).

---

## R8 / ProGuard rules

```proguard
# app/proguard-rules.pro
# Defense-in-depth: physically remove Fake* from release APK.
-assumenosideeffects class family.crypto.fake.** { *; }
```

(Optional for MVP — Detekt rule + runtime assertion = первичные защиты. R8 rule = третий уровень.)

---

## Backup rules

### `app/src/main/res/xml/data_extraction_rules.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
  <cloud-backup>
    <!-- existing rules ... -->
    <exclude domain="file" path="keys/" />
  </cloud-backup>
  <device-transfer>
    <!-- existing rules ... -->
    <exclude domain="file" path="keys/" />
  </device-transfer>
</data-extraction-rules>
```

### Verify in `AndroidManifest.xml`

```xml
<application
  android:dataExtractionRules="@xml/data_extraction_rules"
  android:fullBackupContent="@xml/backup_rules" />
```

---

## CI configuration

### GitHub Actions / GitLab CI

```yaml
jobs:
  crypto-jvm-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - run: ./gradlew :core:crypto:jvmTest -PkotestPropertyIterations=1000
      - run: ./gradlew :core:crypto:testDebugUnitTest

  crypto-android-instrumentation:
    runs-on: macos-latest  # macOS for hardware-accel AVD
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          target: google_apis
          arch: x86_64
          script: ./gradlew :core:crypto:connectedDebugAndroidTest

  crypto-detekt:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
      - run: ./gradlew detekt

  crypto-fitness:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
      - run: ./gradlew :core:crypto:dependencies | tee deps.txt
      - run: |
          if grep "project:" deps.txt; then
            echo "FAIL: :core:crypto has launcher-module dependencies"
            exit 1
          fi
```

**iOS CI**: skipped в MVP. When V-1 (iOS Admin Preset) spec'и appears — add iOS target build job на macos-latest.

---

## Common pitfalls

| Pitfall | Symptom | Fix |
|---|---|---|
| Forgot to add `:core:crypto` to `settings.gradle.kts` | `Could not resolve project :core:crypto` | Add include line |
| Tried to use `FakeAeadCipher` in `app/src/main/` | Detekt fail OR runtime crash on app start | Move to `app/src/debug/` или используй real adapter |
| RFC vector parse fails | JSON shape mismatch | Verify RFC vector format — нужен small parser per RFC, not generic |
| Wycheproof test fails after refresh | New edge case added by Google | Investigate: либо новая атака на наш adapter (fix urgently), либо false positive (skip with reason in PR) |
| iOS build fails on macOS | iOS targets declared but не реализованы | Expected — iOS adapter = stub-screamer; will throw runtime |
| Backward-compat test fails | `v1-sample.json` was modified by mistake | `git restore` the fixture — never modify after 1.0.0 freeze |
| Property test флакает | Non-deterministic seed | Set explicit seed in `PropTestConfig(seed=12345)` |

---

## Acceptance verification trace

After implementation, walk through [scenarios.md](scenarios.md) — 11 user/app behavior scenarios. Each has a checklist; mark `[x]` when verified (autotest or manual).

Recommended order:
1. Сценарий 3 (первый запуск + identity ключ) — простой entry point.
2. Сценарий 4 (ключи защищены от извлечения) — main security guarantee.
3. Сценарий 1+2 (cross-platform encrypt/decrypt) — core function.
4. Сценарий 9 (`docs/dev/crypto-review.md` создан) — документация.
5. Сценарий 6 (libsodium swap experiment) — manual dry-run.
6. Сценарий 7 (library extract experiment) — manual dry-run.
7. Сценарий 11 (backward-compat read) — fixture test.
8. Сценарий 5 (clear data behavior) — manual эмулятор тест.
9. Сценарии 8, 10 — пока **deferred** (real implementation в spec 017); проверить, что F-CRYPTO даёт all primitives.

---

## TL;DR простым языком

Это **инструкция для разработчика**, который будет реализовывать F-CRYPTO. По шагам:

1. **Как настроить проект** — добавить новый Gradle subproject `:core:crypto` с правильными зависимостями (Kotlin Multiplatform, libsodium binding, тесты).

2. **Какие команды запускать** — обычные тесты через `./gradlew :core:crypto:jvmTest`, на эмуляторе — `:connectedDebugAndroidTest`, тесты с 1000 итераций для CI.

3. **Где какие файлы лежат** — папка с тестовыми векторами из RFC, папка с Wycheproof'ом, папка с fixture'ми для backward-compat теста.

4. **Как подключить криптографию к приложению** — Koin DI module в `:app`, где «релизные» (libsodium) и «тестовые» (Fake) реализации разводятся.

5. **Как защитить от ошибок** — Detekt-правило, проверка во время старта, R8-правило (третий уровень защиты).

6. **Как настроить Android backup** — исключить папку с ключами из Google Drive.

7. **Какие CI задачи запускать** — отдельные jobs для JVM-тестов, instrumentation, detekt, fitness.

8. **Типичные ошибки и как их исправить** — таблица.

9. **Как проверить готовый код** — пройти 11 сценариев из `scenarios.md` и поставить галочки.

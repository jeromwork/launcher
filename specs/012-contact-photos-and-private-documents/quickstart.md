# Quickstart: Implementing spec 012

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Data model**: [data-model.md](data-model.md)
**Audience**: разработчик / AI agent, начинающий implementation после `speckit-tasks`.

---

## Prerequisites

✓ Spec 011 merged в main (PR #11, 2026-05-23).
✓ Branch `012-contact-photos-and-private-documents` создан (2026-05-26).
✓ JDK 21 установлен (требуется для AGP 8+).
✓ `NO_PROXY` для эмулятора (см. memory `project_007_operational_state.md`).

---

## 🚨 First two mandatory steps (security + accessibility, не пропускать)

Эти два пункта **обязательны до** любого другого кода, потому что они исключают critical risks:

### Step 1 — Create `data_extraction_rules.xml` (PII backup exclusion)

```bash
# Create the file
mkdir -p app/src/main/res/xml
```

Содержание `app/src/main/res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="private-media/" />
    </cloud-backup>

    <device-transfer>
        <exclude domain="file" path="private-media/" />
    </device-transfer>
</data-extraction-rules>
```

В `app/src/main/AndroidManifest.xml` добавить в `<application>`:

```xml
<application
    ...
    android:dataExtractionRules="@xml/data_extraction_rules"
    ...>
```

**Verification**: запустить `LocalMediaStoreBackupExclusionTest` (instrumented). Должен быть зелёным **до** первого `LocalMediaStore.write` где-либо в проекте.

Подробнее: [`contracts/local-media-store-layout.md`](contracts/local-media-store-layout.md) §Mandatory backup exclusion.

### Step 2 — Plan DocumentViewer zoom buttons (accessibility)

В дизайне `DocumentViewerScreen.kt` зафиксировать:

- Постоянно видимые кнопки `+` и `−` (zoom in/out), tap target ≥ 56dp каждая, контраст ≥ 4.5:1 с фоном.
- Поддержка double-tap zoom (alternative для TalkBack users).
- Pinch-to-zoom — primary gesture для full-vision users; кнопки и double-tap — accessible alternatives.

**Verification**: UI test `DocumentViewerScreenTest.zoom_buttons_visible_when_TalkBack_on` должен быть зелёным.

Подробнее: [`checklists/accessibility.md`](checklists/accessibility.md) adjacent concern.

---

## Module setup

### Create new gradle module `:adapters:media-picker`

```text
adapters/media-picker/
├── build.gradle.kts
├── src/
│   ├── commonMain/kotlin/
│   │   └── (empty — port lives in :core:api)
│   └── androidMain/kotlin/com/launcher/adapters/mediapicker/
│       ├── SystemPhotoPickerAdapter.kt           # entry point
│       ├── PickerBranch33Plus.kt                 # ACTION_PICK_IMAGES
│       ├── PickerBranch29To32.kt                 # androidx PhotoPicker compat
│       └── PickerBranch26To28.kt                 # SAF + temp file copy
```

`build.gradle.kts` skeleton:

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:api"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)  // already in project
            implementation(libs.androidx.documentfile)       // NEW ~50 KB
        }
    }
}

android {
    namespace = "com.launcher.adapters.mediapicker"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig.minSdk = libs.versions.minSdk.get().toInt()
}
```

Update `settings.gradle.kts`:

```kotlin
include(":adapters:media-picker")
```

### Where rest of code lives (no new modules)

- **Facades** `PrivateMediaUploader`, `PrivateMediaResolver` → **`:core:domain/media/`** (pure Kotlin, no platform).
- **`FileLocalMediaStore`** (Android adapter) → **`:app`** (single class, не оправдывает отдельный module — см. research.md R6).
- **Compose screens** (DocumentViewer, admin screens) → существующий `:features:settings:ui` ИЛИ новый `:features:private-media:ui` (plan-phase decision; рекомендация — новый module если ≥ 4 screens, иначе extend существующий).

---

## Dependencies to add

В `gradle/libs.versions.toml`:

```toml
[versions]
documentfile = "1.0.1"  # already at this version? проверить

[libraries]
androidx-documentfile = { module = "androidx.documentfile:documentfile", version.ref = "documentfile" }
```

**Justification**: для SAF ветки на API 26-28 — упрощает URI handling (см. plan.md Dependency Impact).

---

## KDoc directives to add (Article XI §8 compliance)

Перед началом implementation добавить **сначала** KDoc'и (это «дешёвая защита»):

В каждом из 6 крипто-портов из спека 011 (`AeadCipher`, `AsymmetricCrypto`, `EncryptedMediaStorage`, `DigitalSignature`, `HashFunction`, `SecureKeystore`) — добавить блок:

```kotlin
/**
 * ...existing docs...
 *
 * **DO NOT use directly from UI / business logic / feature modules.**
 * Use [com.launcher.api.media.PrivateMediaUploader] /
 * [com.launcher.api.media.PrivateMediaResolver] facades instead.
 *
 * See [docs/dev/private-media-architecture.md](../../../../../docs/dev/private-media-architecture.md).
 *
 * Rationale: Article XI §8 (Reuse before invention). Direct use из UI обходит
 * media pipeline conventions (encryption + reference counting + LocalMediaStore caching)
 * и приводит к утечке cryptographic concerns в UI.
 */
```

В новых facades:

```kotlin
/**
 * ...existing docs...
 *
 * Это **entry point** для media operations из UI / business logic.
 * Внутри использует крипто-порты спека 011 (AeadCipher, EncryptedMediaStorage, etc.) —
 * НЕ обходи facade'ы reach'ясь к ним напрямую.
 */
```

---

## Recommended task order (для speckit-tasks)

1. **T001-T005 (security gate)**: data_extraction_rules.xml + LocalMediaStoreBackupExclusionTest + KDoc directives на крипто-портах.
2. **T006-T015 (Layer 1 — domain types + facades)**: PrivateMediaKind, MediaPickResult, MediaPickerError, ports + facade impls + fakes.
3. **T016-T020 (Layer 1 — adapter)**: FileLocalMediaStore Android impl + integration tests.
4. **T021-T028 (Layer 2 — picker adapter)**: SystemPhotoPickerAdapter + 3 API-level branches + tests.
5. **T029-T032 (Tile extension)**: Tile.DocumentTile + roundtrip + backward-compat tests.
6. **T033-T040 (Layer 3 — admin UI)**: AdminAddDocumentScreen + AdminUploadProgress + AdminDecryptIndicator + tests.
7. **T041-T048 (Layer 3 — Managed UI)**: DocumentViewer (с TalkBack zoom buttons!) + плитка с аватаром + плитка-документ + tests.
8. **T049-T052 (integration + privacy)**: end-to-end tests, EnvelopeMetadataPrivacyTest, partialApplyReasons emit verification.
9. **T053-T055 (architecture doc + APK measurement + senior walkthrough)**: проверить docs/dev/private-media-architecture.md, измерить APK delta, manual UAT plan.

---

## Definition of Done — gates перед merge

См. [plan.md §Rollout / Verification](plan.md#rollout--verification). Кратко:

1. ✅ All 14 checklists в `checklists/` показывают 0 violations.
2. ✅ Constitution Check 8/8 PASS.
3. ✅ `EnvelopeMetadataPrivacyTest.no_label_leak_in_plaintext_metadata` зелёный.
4. ✅ `LocalMediaStoreBackupExclusionTest.private_media_dir_excluded_from_backup` зелёный.
5. ✅ `DocumentViewerScreenTest.zoom_buttons_visible_when_TalkBack_on` зелёный.
6. ✅ APK delta release ≤ 500 KB (measured via R8 build).
7. ✅ Manual UAT на Samsung medium-tier + Xiaomi medium-tier для US-1..US-4.
8. ✅ Все user-facing строки имеют ru+en переводы (ADR-004).

---

## Common pitfalls

- ❌ **Забыли `data_extraction_rules.xml`** → расшифрованные фото в Google Drive backup. Critical PII leak.
- ❌ **Pinch-to-zoom без fallback buttons** → TalkBack пользователи (бабушка со слабым зрением) не могут zoom.
- ❌ **`bytes` в `rememberSaveable`** → PII в Android system parcel, утечка через `dumpsys window`.
- ❌ **Label в `envelope.metadata` вместо ciphertext** → server видит «у бабушки есть документ Медкарта». Metadata leak.
- ❌ **`AeadCipher` import из Compose** → обход facade, нарушение Article XI §8. Manual review должен ловить (Konsist gate отложен).
- ❌ **`MediaPicker` возвращает `Uri`** вместо bytes → Anti-Corruption Layer не работает, platform types leak в domain.

---

## TL;DR (для новичка)

**Что делать сразу**:
1. **`data_extraction_rules.xml`** — exclude `private-media/` из Google Drive backup. Без этого паспорта улетят в облако.
2. **DocumentViewer zoom buttons** — pinch-to-zoom + кнопки `+` `−` обе нужны (бабушка без TalkBack использует pinch, бабушка с TalkBack — кнопки).
3. **KDoc** на крипто-порты — «не дёргать напрямую из UI, иди через фасад».

**Module structure**:
- Новый `:adapters:media-picker` — picker сам решает по API level.
- Всё остальное (facades, LocalMediaStore Android impl, UI screens) — в существующих модулях.

**Порядок tasks** (см. recommended order выше): security gate → Layer 1 → Layer 2 → Tile → Admin UI → Managed UI → integration → docs/APK.

**Don't forget**: privacy test (label не в plaintext metadata) — gate для CI.

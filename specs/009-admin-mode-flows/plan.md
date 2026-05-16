# Implementation Plan: Admin Mode Flows

**Branch**: `009-admin-mode-flows` | **Date**: 2026-05-15 | **Spec**: [spec.md](spec.md)
**Input**: 76 FR/NFR/A11Y из spec.md после `/speckit.specify` + `/speckit.clarify` (33 resolved clarifications)

---

## 1. Overview

Spec 009 даёт «кузов» admin-режима поверх «мотора» спека 8 (bidirectional config sync): полнофункциональный UI редактирования бабушкиной раскладки, мониторинг здоровья её устройства, добавление контактов через picker + VCard share intent, история конфигов с откатом, плитки «открыть приложение» с Play Store fallback. **Архитектурный принцип** «одно приложение, разные конфиги» (унаследовано из спека 8 FR-050): admin-устройство и Managed запускают **то же** приложение, редактор раскладки = тот же rendering pipeline в edit-режиме.

**Технический подход**: 5 новых ports (ConfigHistoryRepository, InstalledAppsCatalog, SystemContactPicker, VCardImporter, OpenAppDispatcher-extended) + 5 androidMain адаптеров; локальный UI слой `app/health-ui/` для phone health severity (НЕ обобщается на часы/сенсоры — те отдельные спеки); расширение existing Compose-компонентов (TileCard/FlowScreen/BottomFlowBar/HomeScreen) параметром `editMode` + drag-and-drop через `Modifier.dragAndDropSource/Target`. Wire format additive только (forward-compat `presetOverrides: null`, новая subcollection `/config/history/*`). Никаких новых gradle deps.

---

## 2. Architecture

### Module map

```
:core (KMP, commonMain)
├── api/
│   ├── config/           (existing — extend)
│   │   ├── Contact.kt              [+fromRaw factory, ValidationError sealed]
│   │   ├── ConfigSnapshot.kt       [NEW — history envelope]
│   │   ├── PresetSettings.kt       [NEW — forward-compat]
│   │   └── PhoneHealthSettings.kt  [NEW — внутри PresetSettings]
│   ├── history/          [NEW package]
│   │   └── ConfigHistoryRepository.kt [port]
│   ├── apps/             [NEW package]
│   │   └── InstalledAppsCatalog.kt    [port]
│   ├── contacts/         [NEW package]
│   │   ├── SystemContactPicker.kt     [port]
│   │   └── VCardImporter.kt           [port]
│   └── admin/            [NEW package]
│       ├── AdminEditorMode.kt         [enum: View / Edit]
│       └── EditorState.kt             [editor view-model state]
├── ui/components/        (existing — extend with editMode param)
│   ├── TileCard.kt                    [+editMode, +onLongPress, +onEditMenuClick, varying icon by SlotKind]
│   ├── BottomFlowBar.kt               [+editMode, +onAddFlow, +onDeleteFlow]
│   └── ... (HomeScreen, FlowScreen)
└── ui/health/            [NEW package — local UI types only]
    ├── PhoneHealthIndicator.kt
    ├── PhoneHealthSeverity.kt
    ├── PhoneHealthPreset.kt           [+ DEFAULT_PHONE_HEALTH_PRESET]
    └── HealthToPhoneIndicatorAdapter.kt

:core (androidMain — adapters per CLAUDE.md rule 2 ACL)
├── adapters/
│   ├── SystemContactPickerAdapter.kt  [ContactsContract → Contact.fromRaw]
│   ├── VCardImportAdapter.kt          [VCard text → Contact.fromRaw]
│   ├── OpenAppDispatcher.kt           [queryIntentActivities + Play Store fallback]
│   └── InstalledAppsCatalogAdapter.kt [PackageManager → app list]
└── firestore/
    └── FirestoreConfigHistoryAdapter.kt [/config/history/* read/write/housekeeping]

:app (Android-only — admin UI surfaces)
├── admin/
│   ├── AdminDevicesScreen.kt          [replace mock from spec 7]
│   ├── EditorScreen.kt                [hosts FlowScreen(editMode=true) + banner + buttons]
│   ├── TileEditForm.kt                [edit individual tile: kind/contact/packageName]
│   ├── HistoryScreen.kt               [list snapshots + preview + rollback]
│   └── ContactsManageScreen.kt        [privacy minimum FR-031a: list + delete]
├── contacts/
│   ├── VCardReceiveActivity.kt        [singleTask + onNewIntent — FR-027a]
│   ├── ContactPickerLauncher.kt       [ActivityResultContract.PickContact]
│   ├── ManualContactEntryForm.kt      [FR-023a — alternative]
│   └── ContactPermissionRationale.kt  [FR-023, FR-023b deep-link to Settings]
└── theme/                             (existing)
    └── severity-colors.kt             [+distinct hue light/dark — FR-046b]
```

### Port-adapter shape

Каждый port в `commonMain` — interface; каждый adapter в `androidMain` — class implementing port. Domain (`api/`) типы НЕ импортируют Android-specific (`Cursor`, `Uri`, `Intent`, `Drawable`).

```
:core/commonMain                          :core/androidMain
─────────────────────                     ────────────────────
ConfigHistoryRepository  ←implements—     FirestoreConfigHistoryAdapter
                                          (uses CollectionReference)

SystemContactPicker      ←implements—     SystemContactPickerAdapter
                                          (uses ContactsContract.CommonDataKinds.Phone)

VCardImporter            ←implements—     VCardImportAdapter
                                          (hand-written parser ~100 LOC)

OpenAppDispatcher        ←implements—     OpenAppDispatcherAndroid
                                          (uses queryIntentActivities, Intent)

InstalledAppsCatalog     ←implements—     InstalledAppsCatalogAdapter
                                          (uses PackageManager)
```

Adapter-result type: `Result<DomainType, DomainError>`, никогда не `Uri`/`Cursor`/`Intent` в return signature.

### Data flow — US-1 (admin edits layout)

```
User long-press tile in EditorScreen
    │
    ▼
FlowScreen(editMode=true) → Modifier.dragAndDropSource
    │ drag to new position
    ▼
EditorViewModel.moveTile(slotId, fromFlow, toFlow, newIndex)
    │ updates EditorState (local draft)
    ▼
PendingLocalChangesRepository (Room — reused from spec 8)
    │ continuous autosave (FR-014b) — every change
    ▼
User taps "Опубликовать"
    │
    ▼
ConfigPublishUseCase (spec 8 reused — FR-013/022 conflict-check)
    │
    ├─→ ConfigHistoryRepository.recordSnapshot(currentConfig) [NEW]
    │     │ writes /config/history/{autoId} with anti-spoof recordedFromDeviceId=auth.uid
    │     │ if snapshots ≥ 11 → delete oldest (FR-038 housekeeping)
    │
    ├─→ ConfigCurrentRepository.push(newConfig) [spec 8]
    │     │ optimistic concurrency check via clientSnapshotUpdatedAt
    │     │ if conflict → MergeUI (spec 8 FR-050)
    │
    └─→ on success → Managed reads /config/current via Firestore listener (spec 8 FR-022)
        │ applies → /state.appliedConfigUpdatedAt updates → admin UI shows "Применено"
```

### Data flow — US-4 (VCard share)

```
Admin in WhatsApp: "Share contact" → System share sheet → selects our app
    │
    ▼
Android delivers ACTION_SEND + MIME text/x-vcard to VCardReceiveActivity (singleTask)
    │ if app already open: onNewIntent (FR-027a) — no second copy in task switcher
    ▼
VCardImportAdapter.parse(extraStream) → RawVCard(displayName, phoneNumbers)
    │ rejects > 10KB, non-UTF8, missing TEL
    ▼
Contact.fromRaw(displayName, phoneNumbers[0]) → Result<Contact, ValidationError>
    │ universal domain validator (FR-C3)
    ▼
"Add to Managed: <picker>" screen (preselect if 1 Managed)
    │
    ▼
EditorScreen of selected Managed opens, prefills new tile draft
    │
    ▼
Admin taps "Опубликовать" → same flow as US-1
```

---

## 3. Data model

См. [data-model.md](data-model.md). Краткий обзор:

- **`Contact`** (existing, extended): `+ fromRaw()` factory + `ValidationError` sealed type.
- **`ConfigSnapshot`** (NEW envelope): `{ snapshotSchemaVersion: Int, config: ConfigCurrent, recordedAt: Long, recordedFromDeviceId: String }`.
- **`PresetSettings`** (NEW forward-compat): `{ phoneHealthSettings: PhoneHealthSettings? }`, всегда null в спеке 9.
- **`PhoneHealthSettings`** (NEW forward-compat): full struct for future configurable thresholds; null в спеке 9.
- **`PhoneHealthIndicator`** (UI-local): `{ id, sourceType="phone", label, value, severity, updatedAt }`.
- **`PhoneHealthPreset`** (UI-local data class): полная структура threshold'ов + 1 захардкоженный `DEFAULT_PHONE_HEALTH_PRESET`.

---

## 4. Wire formats

См. [contracts/](contracts/):

- **[contracts/config-history.md](contracts/config-history.md)** — NEW subcollection `/links/{linkId}/config/history/{autoId}`, `ConfigSnapshot` envelope с двумя независимыми schemaVersion (envelope + nested config), `snapshotSchemaVersion = 1`.
- **[contracts/config-current-additions.md](contracts/config-current-additions.md)** — additive field `presetOverrides: PresetSettings?` в существующем `/config/current` (NO `schemaVersion` bump, follows spec 8 FR-006 additive policy).
- **[contracts/vcard-incoming.md](contracts/vcard-incoming.md)** — read-only external format. Parser contract: FN + TEL only, 10KB limit, UTF-8 required, output → `Contact.fromRaw()` raw strings.

---

## 5. Dependency impact

**Article XIII (Dependency discipline) — PASS, без новых gradle deps:**

| Что | Откуда |
|---|---|
| Firestore subcollection access | существующий `firebase-firestore-ktx` (BOM from спека 7) |
| Compose drag-and-drop | существующий `androidx.compose.foundation` 1.6+ (Material BOM 2024.10) — `Modifier.dragAndDropSource/Target` |
| ContactsContract picker | стандартный Android API (no extra lib) |
| VCard parser | **hand-written ~100 LOC** в androidMain (FN + TEL only). Решение mentor-session 2026-05-15: `ezvcard` library в `:core/commonMain` нарушит rule 1 domain isolation; minimal parser в androidMain — единственный clean путь. |
| Backup exclusion | стандартный Android (`data_extraction_rules.xml` resource) |

**`AndroidManifest.xml` дополнения:**
- `<uses-permission android:name="android.permission.READ_CONTACTS" />` (admin-only — Managed manifest НЕ затрагивается).
- `<intent-filter>` на `ACTION_SEND` + `text/x-vcard` для `VCardReceiveActivity` с `launchMode="singleTask"`.
- `<queries>` block: `<intent><action MAIN/><category LAUNCHER/></intent>` + `<intent><action VIEW/><data scheme="market"/></intent>`.
- `<application android:dataExtractionRules="@xml/data_extraction_rules">` — backup exclusion.

---

## 6. Test strategy

Per CLAUDE.md §6 (mock-first) + §7 (fitness functions):

### Contract tests (every port)

| Port | Fake adapter | Real adapter | Contract test |
|---|---|---|---|
| `ConfigHistoryRepository` | `FakeConfigHistoryRepository` (in-memory list) | `FirestoreConfigHistoryAdapter` | Roundtrip `recordSnapshot → readAll → housekeep` |
| `SystemContactPicker` | `FakeSystemContactPicker` (predefined contacts) | `SystemContactPickerAdapter` | Validates URI → `Contact.fromRaw()` flow |
| `VCardImporter` | `FakeVCardImporter` (returns pre-parsed) | `VCardImportAdapter` | Real VCard samples (WhatsApp / Telegram / system Contacts exports) |
| `OpenAppDispatcher` | `FakeOpenAppDispatcher` (records launch calls) | `OpenAppDispatcherAndroid` | Verifies fallback chain: launcher → market → web |
| `InstalledAppsCatalog` | `FakeInstalledAppsCatalog` | `InstalledAppsCatalogAdapter` | Verifies includes own app filtered out, sorted by label |

### Wire format roundtrip tests (FR-047 deferred — implemented here)

1. `ConfigCurrentRoundtripTest` (с `presetOverrides = null` — спек 9 default).
2. `ConfigCurrentNonNullPresetRoundtripTest` (с `presetOverrides = PresetSettings(phoneHealthSettings = null)` — forward-compat smoke).
3. `ConfigSnapshotRoundtripTest` (envelope + nested config roundtrip).
4. `VCardAdapterContractTest` (4-5 real VCard samples → `Contact.fromRaw()` → assert match).

### Backward-compat smoke

- `ConfigCurrentSchemaV1Test` — старый `/config/current` без `presetOverrides` поля читается корректно с default null.
- `ConfigSnapshotForwardSchemaTest` — snapshot со schema `> SUPPORTED_SCHEMA_VERSION` → reader fails closed (FR-043).

### Domain-level tests

- `ContactFromRawTest` — все 5 правил валидации (name trim/control-strip, max 100, non-empty; phone normalize, regex match).
- `ContactFromRawEmojiTest` — explicit case «Маша 😍» → valid Contact (emoji preserved).
- `PhoneHealthSeverityTest` — все 4 поля (battery / lastSeen / audio / connectivity) × 3 severity transitions.

### UI tests (`:core/androidUnitTest` Compose UI test)

- `TileCardEditModeTest` — long-press triggers `onLongPress`, tap triggers `onEditMenuClick`, icon variation per SlotKind.
- `FlowScreenDragDropTest` — drag tile to new position, drag cross-flow, drag to trash.
- `HistoryScreenTest` — list renders, tap opens preview, rollback triggers `recordedFromDeviceId` constraint.
- `EditorScreenRecreationTest` — Activity recreate via rotate → draft restored from Room (FR-014a/b).
- `VCardReceiveActivityTest` — `onNewIntent` handles second VCard share без relaunch.

### Performance tests (NFR verification)

- **NFR-001**: `androidx.benchmark.macro` `FrameTimingMetric` for drag-and-drop scenario on Pixel 4a class. `frameDurationCpuMs` p99 < 16ms across 20 drag operations. Gate: if fails → switch to `pointerInput` (FR-008 two-way door).
- **NFR-002**: `androidx.benchmark` microbenchmark for `VCardImportAdapter.parse(10KB)`. p95 < 100ms on Dispatchers.Default.

### Accessibility tests (FR-A11Y verification)

- `AccessibilityScannerCITest` — Android Accessibility Scanner CLI runs against all admin screens, fails build on contrast / tap-target violations.
- Manual TalkBack walkthrough — checklist в [smoke/talkback-walkthrough.md](smoke/talkback-walkthrough.md): US-1, US-2, US-5 paths.

### Fitness functions (Konsist gates)

5 NEW Konsist rules в `core/src/commonTest/.../KonsistGate*.kt`:

1. `api/contacts/*.kt` MUST NOT import `android.*` / `androidx.*`.
2. `api/history/*.kt` MUST NOT import `com.google.firebase.*`.
3. `api/apps/*.kt` MUST NOT import `android.content.pm.*`.
4. `api/config/Contact.fromRaw` return type MUST be `Result<Contact, ValidationError>`, not `Contact` (no exceptions thrown for validation).
5. `ui/components/TileCard` MUST варьировать icon по `SlotKind` parameter (lint-style check on `imageVector` parameter presence).

### Manual smoke checks

- `smoke/elderly-walkthrough.md` — US-6 path (Managed via 7-tap + password → editor → rollback) на physical / emulator device.
- `smoke/oem-matrix.md` — VCard intent на Samsung One UI + Xiaomi MIUI + Pixel emulator; READ_CONTACTS deny+manual-entry path.

---

## 7. Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Compose `Modifier.dragAndDropSource/Target` имеет глюки на cross-flow drag (новые API) | M | H — основная UX фичи | Two-way door (FR-008 fallback на `pointerInput`); NFR-001 frame-budget gate ловит проблемы рано |
| R2 | OEM task-killer убивает app в фоне → draft теряется | H (Xiaomi/Huawei) | M | FR-014b continuous autosave в Room на каждое изменение; Recreation test проверяет explicitly |
| R3 | VCard parser falls на нестандартных VCard (Telegram custom fields, emoji в FN с RTL chars) | M | L — UX «не удалось добавить» | FR-028 whitelist policy (только FN+TEL), 10KB limit, UTF-8 strict; explicit ValidationError для unknown encoding |
| R4 | Spoofing `recordedFromDeviceId` через клиентский write | H (any malicious editor) | M (integrity history) | FR-045a Security Rule `recordedFromDeviceId == request.auth.uid` (server-enforced); migration к server-only через SRV-CONFIG-001 |
| R5 | Race condition: client write `/config/current` ok, write `/config/history` падает | L (требует crash window) | L (потеря одной версии истории) | FR-037 explicit accept rare loss; migration к atomicity через SRV-CONFIG-001 |
| R6 | `<queries>` блок недостаточен для популярных приложений → false negative «приложение не установлено» | M | M — UX «открыть Яндекс Карты» уходит в Play Store даже если установлено | FR-035a generic `<queries>` + `queryIntentActivities` (не `getPackageInfo`); plan-task: OEM test matrix включает Huawei AppGallery scenario |
| R7 | TalkBack не озвучивает severity transitions (emoji 🔴 vs vector icon Warning) | H в эпоху OUT-022 (если бы оставили emoji) | M-H | FR-022a vector icons + explicit contentDescription; manual TalkBack walkthrough в smoke |
| R8 | Privacy compliance reject в Play Store при первой попытке публикации | H (без TODO-LEGAL-001) | H — блокирует release | FR-031a/b/c минимум в спеке 9; TODO-LEGAL-001 🚨 PLAY-STORE-BLOCKER до production; полный compliance audit ДО первого Play Store upload |
| R9 | Android Auto Backup захватывает Room contacts DB без consent от 3rd party (Маша) | M | M — GDPR transfer-to-processor | FR-046a `data_extraction_rules.xml` exclusion для contacts DB |
| R10 | Schema bump в будущем ломает rollback к старым snapshot'ам | L (требует /config schemaVersion bump) | M (история теряется) | FR-043 + TODO-ARCH-015 lazy transformers + двойной schemaVersion в ConfigSnapshot (envelope + nested config) даёт independent evolution |

---

## 8. Required Context Review

Per конституция Article XII §7 — каждый relevant документ.

### Constitution & engineering rules

- [`/.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — все 16 Articles. Particular focus: Article I (Architecture), Article IV (Configuration), Article VII (Wire Format), Article VIII (Senior-Safe), Article IX (Performance), Article XI (MVA), Article XII (Context Review), Article XIII (Dependencies), Article XIV (Privacy/Security), Article XVI (Constitution Check).
- [`/CLAUDE.md`](../../CLAUDE.md) — все 8 rules. Особенно rule 1 (domain isolation), rule 2 (ACL), rule 4 (MVA), rule 5 (wire-format versioning), rule 8 (server migration tracking).

### Architectural Decision Records

- [`docs/adr/ADR-001-*.md`](../../docs/adr/) — все active ADRs (5+). Relevant для спека 9: ADR-005 (Compose Multiplatform UI stack — наш render pipeline), ADR-004 (i18n strategy — Russian-only, see also locales for `lastSeen` formatting FR-022).

### Product

- [`docs/product/roadmap.md:192-260`](../../docs/product/roadmap.md#L192) — Spec 009 секция (updated 2026-05-15 после pre-specify discovery).
- [`docs/product/senior-safe-launcher-plan.md`](../../docs/product/senior-safe-launcher-plan.md) — overall product philosophy, проверить consistency US-6 решения (Managed editor через 7-tap+пароль).

### Compliance

- [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — RUNTIME permissions list. Спек 9 добавляет `READ_CONTACTS` (admin-only). Plan-task: update этот файл.
- [`docs/compliance/store-policy-register.md`](../../docs/compliance/store-policy-register.md) — Play Store rules. Privacy + Data Safety form для PII третьих лиц — TODO-LEGAL-001 🚨 PLAY-STORE-BLOCKER.

### Dev / Operations

- [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) — TODO-ARCH-006/007/010-015, TODO-LEGAL-001, TODO-FUTURE-SPEC-001-005.
- [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md) — SRV-CONFIG-001 (history server-side write migration), SRV-CONFIG-002 (housekeeping cron), SRV-MONITOR-001 (push admin on critical).

### Existing code references (read-before-write)

- [`core/src/commonMain/kotlin/com/launcher/api/config/Contact.kt`](../../core/src/commonMain/kotlin/com/launcher/api/config/Contact.kt) — будем extend с `fromRaw()`.
- [`core/src/commonMain/kotlin/com/launcher/api/health/Health.kt`](../../core/src/commonMain/kotlin/com/launcher/api/health/Health.kt) — wire format inherited из спека 6, читать без изменений.
- [`core/src/commonMain/kotlin/com/launcher/ui/components/TileCard.kt`](../../core/src/commonMain/kotlin/com/launcher/ui/components/TileCard.kt) — будем extend; **известный bug**: icon hardcoded `Icons.Filled.Call` (FR-046 fixes).
- [`core/src/commonMain/kotlin/com/launcher/ui/screens/FlowScreen.kt`](../../core/src/commonMain/kotlin/com/launcher/ui/screens/FlowScreen.kt) — extend с editMode + drag-and-drop.
- [`firestore.rules`](../../firestore.rules) — extend subcollection rules для history (FR-045b).
- [`app/src/main/AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) — intent-filter + queries + backup ref.

### Спеки upstream

- [`specs/007-pairing-and-firebase-channel/`](../007-pairing-and-firebase-channel/) — pairing dependency, linkId/adminId/managedDeviceFirebaseUid identity.
- [`specs/008-bidirectional-config-sync/`](../008-bidirectional-config-sync/) — config sync motor (FR-013/022/050 inheritances), `PendingLocalChanges` Room reuse.
- [`specs/006-provider-capabilities-and-health/`](../006-provider-capabilities-and-health/) — Health wire format.

---

## 9. Constitution Check

**Status**: ✅ **8/8 PASS** — plan COMPLETE (run 2026-05-15 via `procedure-constitution-check`).

| Gate | Status | Justification |
|------|--------|---------------|
| 1 Architecture | ✅ PASS | Расширение existing `:core` / `:app` модулей. No new gradle modules. Ports/adapters per CLAUDE.md rule 2 с clear ownership + build isolation + testability. См. Module map в §2. |
| 2 Core/System Integration | ✅ PASS | No new BroadcastReceiver feature-modules. Все Android types (`Intent`/`Uri`/`Cursor`/`Drawable`/`PackageManager`) wrapped в `androidMain` адаптерах per rule 2. VCard `<intent-filter>` — explicit user-initiated через системный share sheet, не background event. |
| 3 Configuration | ✅ PASS | `ConfigSnapshot.snapshotSchemaVersion = 1` explicit. `presetOverrides` — additive change per spec 8 FR-006 (no bump). Migration policy для будущих breaking changes — `TODO-ARCH-015` lazy transformers + R-002 research entry. См. [contracts/](contracts/). |
| 4 Required Context Review | ✅ PASS | §8 links: constitution (все 16 Articles), CLAUDE.md (8 rules), ADR-005 (Compose Multiplatform UI), ADR-004 (i18n), `roadmap.md:192-260`, `senior-safe-launcher-plan.md`, `permissions-and-resource-budget.md`, `store-policy-register.md`, `project-backlog.md`, `server-roadmap.md`, upstream specs 007/008/006, существующий код (Contact / Health / TileCard / FlowScreen / firestore.rules / AndroidManifest). |
| 5 Accessibility | ✅ PASS | Отдельный раздел `## Accessibility requirements` в spec.md (FR-A11Y-001..007). Tap target ≥ 56 dp наследуется из Article VIII. WCAG 2.2 AA contrast (FR-A11Y-006). TalkBack walkthrough в smoke tests. FR-046b distinct hue + shape duplication для color blindness. SC-006/007 acceptance criteria. |
| 6 Battery/Performance | ✅ PASS | No background work (push админу при closed app — `TODO-ARCH-012` в backlog, не в спеке). FR-020 переписан C9 — listener-only when screen open, polling не используется. NFR-001 (0 dropped frames Pixel 4a class) + NFR-002 (VCard parse < 100 ms p95) measurable. Macrobenchmark + microbenchmark gates в Phase 4 / 5 verification. |
| 7 Testing | ✅ PASS | Все 5 портов имеют fake + real + contract test (§6). 4 wire-format roundtrip tests. Backward-compat smoke. Domain tests (`Contact.fromRaw` rules). Compose UI tests (TileCard/FlowScreen/HistoryScreen/EditorScreen recreation/VCardReceive). 5 Konsist gates fitness functions. Manual TalkBack + elderly + OEM matrix. |
| 8 Simplicity | ✅ PASS | Три conscious speculative abstractions documented в research.md с trade-offs: (1) `presetOverrides: null` — justified rule 5 wire-format additive readiness; (2) `PhoneHealthCriticalEvent` без subscriber — 3 LOC emit, готовит SRV-MONITOR-001; (3) `ConfigSnapshot` dual schemaVersion — R-002 independent evolution rationale. Все 5 NEW ports имеют ≥ 2 consumers (real + fake adapter) per rule 6. |

**Watch items** (приняты deliberately, не нарушения):
- 2 meta-minimization watch items (см. [checklists/meta-minimization.md](checklists/meta-minimization.md)).
- Race condition acceptance в FR-037 + spoofing risk mitigated via FR-045a — conscious risk acceptance с маршрутом миграции в [server-roadmap.md SRV-CONFIG-001](../../docs/dev/server-roadmap.md).

**Verdict**: plan ready for Step 5 (plan-level checklist re-runs) + Step 6 (final report). После — переход к `/speckit.tasks`.

---

## 10. Rollout / verification

### Phase 0 — Foundation (~1 week)
- New domain types: `Contact.fromRaw`, `ConfigSnapshot`, `PresetSettings`, `PhoneHealthSettings`.
- New ports + fake adapters: ConfigHistoryRepository, InstalledAppsCatalog, SystemContactPicker, VCardImporter, OpenAppDispatcher (extended).
- Konsist gates × 5.
- Wire format contracts + roundtrip tests.
- **Verification**: all contract + roundtrip tests green; Konsist gates pass.

### Phase 1 — Adapters (~1 week)
- `FirestoreConfigHistoryAdapter`, `SystemContactPickerAdapter`, `VCardImportAdapter`, `OpenAppDispatcherAndroid`, `InstalledAppsCatalogAdapter`.
- Security Rules update: subcollection + anti-spoofing.
- AndroidManifest changes: queries + intent-filter + backup ref.
- Backup exclusion file.
- **Verification**: real-adapter contract tests green; security rules firestore-tests green.

### Phase 2 — Health monitoring UI (~0.5 week)
- `PhoneHealthIndicator` + adapter + severity computation.
- `AdminDevicesScreen` replaces mock; health сводка в списке.
- Extended view in EditorScreen.
- FR-020 Firestore listener wiring (open/close on screen lifecycle).
- FR-022a vector icons + contentDescription.
- **Verification**: manual TalkBack walkthrough US-2; accessibility scanner clean.

### Phase 3 — Editor scaffold (~1.5 week)
- Расширение TileCard / FlowScreen / BottomFlowBar / HomeScreen с editMode.
- TileCard icon fix (FR-046) — variable by SlotKind.
- EditorScreen + TileEditForm.
- Continuous autosave в Room (FR-014b).
- **Verification**: manual edit + save + recreation test; SC-001 90-sec scenario.

### Phase 4 — Drag-and-drop (~1.5 week)
- `Modifier.dragAndDropSource/Target` per FR-008.
- Drop targets: cross-flow + trash zone (with WindowInsets).
- Параллельная кнопка «···» (FR-009 / FR-A11Y-004).
- **Verification**: NFR-001 macrobenchmark gate; manual drag walkthrough; manual TalkBack walkthrough alternative channel.

### Phase 5 — Contacts (~1 week)
- `SystemContactPickerAdapter` flow + permission rationale (FR-023).
- `ManualContactEntryForm` (FR-023a) + denial recovery (FR-023b deep-link).
- `VCardReceiveActivity` singleTask + onNewIntent (FR-027/027a).
- `VCardImportAdapter` parser + validation.
- `ContactsManageScreen` (FR-031a privacy minimum).
- **Verification**: NFR-002 microbenchmark; manual OEM matrix smoke; manual privacy flow walkthrough.

### Phase 6 — History + rollback (~1 week)
- `FirestoreConfigHistoryAdapter` write + housekeeping (FR-037/038).
- `HistoryScreen` (отдельный full screen FR-039) + preview + rollback flow.
- Editor symmetry (FR-042) — Managed access through Settings.
- Schema validation на read (FR-043).
- **Verification**: end-to-end manual: edit → push → push again → history → rollback → conflict via merge UI.

### Phase 7 — OpenApp tiles (~0.5 week)
- `OpenAppDispatcher` queryIntentActivities + Play Store + web fallback chain (FR-035/035a).
- `InstalledAppsCatalog` UI selector in TileEditForm.
- **Verification**: OEM matrix Pixel + Samsung + Huawei (no GMS check).

### Phase 8 — Verification & ship-readiness (~0.5 week)
- Full cross-artifact trace via `procedure-cross-artifact-trace`.
- `/speckit.analyze` audit.
- Re-run all checklists post-implementation.
- Update perf-checkpoint.md + smoke-checkpoint.md.

**Total estimate**: ~8 weeks (consistent с user signal «размер не пугает», spec 8 reference = 5-7 weeks).

### Continuous verification gates

- **Pre-commit**: 5 Konsist gates fail build on domain isolation violations.
- **Per-merge**: contract tests + roundtrip tests + unit tests.
- **Pre-release**: macrobenchmark NFR-001/002 + manual TalkBack walkthrough + OEM matrix smoke.
- **Pre-Play Store**: TODO-LEGAL-001 closed; TODO-ARCH-006 R8 done; Data Safety form filled correctly.

---

## 11. Implementation constraints (anti-bloat per meta-minimization plan-level checklist)

Эти ограничения фиксируются как **обязательные** policy для `/speckit.tasks` и реализации — предотвращают over-engineering speculative abstractions, идентифицированных в plan-level meta-minimization review.

- **C-1: No EventBus framework для `PhoneHealthCriticalEvent`.** Реализация — единственный `MutableSharedFlow<PhoneHealthCriticalEvent>` в admin DI scope, single emit site в `HealthToPhoneIndicatorAdapter`, **no subscriber в спеке 9** (FR-021 explicit). При появлении подписчика в SRV-MONITOR-001 / TODO-ARCH-012 — конкретный suspend collector, не registry.
- **C-2: No `TransformerRegistry<Version>` generic infrastructure** для `ConfigSnapshot` транзформеров. Когда настанет первый schema bump (TODO-ARCH-015) — explicit `when (fromVersion) { 1 -> v1ToV2(it); 2 -> ...}` chain в одном файле `SnapshotMigrator.kt`. Generic registry — premature до **третьего** transformer (rule 4 «3 examples»).
- **C-3: Inline-TODO exit ramp в forward-compat data classes.** `PresetSettings.kt` и `PhoneHealthSettings.kt` MUST содержать inline-TODO `// TODO(exit-ramp): фактически пустая структура спека 9; не добавлять fields без current consumer. Schema bump НЕ требуется при добавлении полей (additive policy). См. spec 9 FR-013 + meta-minimization-plan.md W3.` Соответствует memory rule `feedback_exit_ramps_as_todos`.
- **C-4: `PhoneHealthIndicator` placement — final decision.** Plan §2 указал `:core/commonMain/ui/health/`, spec.md FR-014 указал `app/health-ui/`. **Final**: `:core/commonMain/ui/health/` (consistent с уже-существующим UI layer pipeline `:core/.../ui/`, ADR-005 compliance). Spec.md обновится в tasks.md фазе (это implementation detail, не API contract).
- **C-5: `SeverityWire` enum vs `PhoneHealthSeverity` enum split.** Wire format использует `SeverityWire` (с `wireValue: String` per spec 5 SlotKind pattern), UI использует `PhoneHealthSeverity`. Adapter в `HealthToPhoneIndicatorAdapter` mapping. **Этот split — не over-engineering**: разные жизненные циклы (wire stable, UI evolves with Material), типобезопасность для adapter.

Эти constraints зафиксированы для `/speckit.tasks`, чтобы каждая ARCH task имела explicit «не делать X».

---

## Что внутри (TL;DR на русском)

Это **архитектурный план** спека 9 — переход от «что нужно сделать» (76 FR в спеке) к «как именно реализовать» (модули, порты, тесты, фазы работ).

**Главное архитектурное решение:** одно приложение, разные конфиги (унаследовано из спека 8). Admin-устройство и Managed запускают **то же** приложение, только в разных режимах — view (бабушка) vs edit (admin). Никаких отдельных рендереров; существующие компоненты раскладки расширяются параметром `editMode`.

**5 новых портов** (для подключения внешних систем — Firebase history, system contacts picker, VCard parser, installed apps, OpenApp dispatcher) + **5 адаптеров** в `androidMain` (по правилу 2 ACL — Android-specific типы не попадают в domain).

**Тесты:** контрактные на каждый порт, roundtrip × 4 wire formats, NFR-001/002 measurable performance budgets (drag-and-drop 0 dropped frames, VCard parse < 100 ms), 5 Konsist gates следят за domain isolation на build pipeline, manual TalkBack + elderly + OEM matrix smoke.

**8 фаз работ** общим объёмом ~8 недель. Constitution Check 8/8 PASS. Все 3 plan-level checklist'a (domain-isolation, wire-format, meta-minimization) — PASS, без блокеров.

**Следующий шаг:** `/speckit.tasks` — разбиение plan'a на конкретные T-задачи (T001..Txxx) с traceability на FR/NFR/A11Y. Затем `/speckit.analyze` → имплементация.

**Constraints на реализацию** (см. §11): не делать EventBus framework, не делать generic `TransformerRegistry`, inline-TODO exit ramp в forward-compat data classes, `PhoneHealthIndicator` живёт в `:core/commonMain/ui/health/` (не `app/`).

# Checklist: domain-isolation

**Spec**: `spec.md` (rev. 2026-05-15, post-clarify C1-C5)
**Run**: 2026-05-15 — pre-`/speckit.plan` discovery pass.

Enforces [`CLAUDE.md`](../../../CLAUDE.md) rules 1 (Domain isolated from infrastructure) and 2 (Anti-Corruption Layer for every external dependency) + ADR-001 Platform Parity Gate.

Follows the style of [spec 008 domain-isolation checklist](../../008-bidirectional-config-sync/checklists/domain-isolation.md).

---

## Inventory: external surfaces in spec 009

Spec 009 touches **more** external surfaces than spec 008 — it introduces real OS-platform reads (Contacts SDK, intent system) on top of the cloud surfaces already wrapped in 007/008.

| # | Surface | Vendor / Platform | Re-use или new? | Port (existing or proposed) |
|---|---|---|---|---|
| S1 | Firestore read `/config/current` (pull при open editor) | Firebase (vendor) | **re-use** 007/008 | `RemoteSyncBackend` |
| S2 | Firestore atomic transaction для publish flow (FR-014) | Firebase | **re-use** 008 | `RemoteSyncBackend.runTransaction` + `ConfigEditor.pushPending` |
| S3 | Firestore subcollection write `/config/history/{autoId}` (FR-037) | Firebase | **NEW use of existing port** | `RemoteSyncBackend.writeDoc` + **NEW** `ConfigHistoryRepository` port |
| S4 | Firestore read `/config/history/*` listing (FR-039) | Firebase | **NEW use of existing port** | `RemoteSyncBackend.listDocs` (existing) + **NEW** `ConfigHistoryRepository` port |
| S5 | Firestore client-side housekeeping delete oldest (FR-038) | Firebase | **NEW use of existing port** | `RemoteSyncBackend.deleteDoc` (existing or new) + via `ConfigHistoryRepository` |
| S6 | Firestore realtime listener для `/links/{linkId}/health` (FR-020 cadence by severity) | Firebase | **re-use** 006/007 | `HealthRepository.observe()` (existing) |
| S7 | Local persistence — admin-side per-Managed config draft (FR-014a) | Room (AndroidX) | **re-use** 008 `PendingLocalChanges` table | `LocalConfigStore` (existing 008) |
| S8 | Android **Contacts** SDK — `Intent.ACTION_PICK` + `ContactsContract.CommonDataKinds.Phone` (FR-024..026) | Android platform | **NEW** — full ACL | **NEW** port `SystemContactPicker` (commonMain) + adapter `AndroidSystemContactPicker` (androidMain) |
| S9 | Android **VCard share intent** — `ACTION_SEND` + MIME `text/x-vcard` (FR-027..031) | Android platform | **NEW** — full ACL | **NEW** port `VCardImport` (commonMain) + adapter `AndroidVCardImport` (androidMain) |
| S10 | Android **`<queries>`** + intent `ACTION_VIEW` / `LAUNCHER` для `OpenApp` (FR-034 admin side: list installed apps) | Android platform | **NEW** | **NEW** port `InstalledAppsCatalog` (commonMain) + `AndroidInstalledAppsCatalog` (androidMain) |
| S11 | Android **`LAUNCHER` intent + Play Store fallback** on Managed (FR-035 dispatcher side) | Android platform | **NEW** — Managed-side, единственный use site | **NEW** port `OpenAppDispatcher` ИЛИ inline в существующий `ActionDispatcher` (см. CHK008) |
| S12 | Runtime permission flow — `READ_CONTACTS` (FR-023) | Android platform | partial re-use rationale pattern из 006 | port `PermissionRequester` (re-use existing если есть; иначе NEW) |
| S13 | Android **package manager queries** — `getPackageInfo(packageName, 0)` для проверки наличия приложения на Managed (FR-035) | Android platform | **NEW**, same module as S10/S11 | inline в `OpenAppDispatcher` adapter |
| S14 | UUID v4 generation для новых Contact/Slot id | platform-agnostic | **re-use** 008 решение | `kotlin.uuid.Uuid` inline (no port) |
| S15 | FCM push при Critical health (FR-021) — **out of scope** для спека 9 | Firebase | not implemented in 9 | event-only — `PhoneHealthCriticalEvent` локальный, subscriber отсутствует → `SRV-MONITOR-001` |
| S16 | `kotlinx.serialization.json.JsonObject` в `Slot.args` (existing) | Kotlin stdlib-ish | existing | accepted в domain (project convention) |

---

## Vendor SDKs

- [x] **CHK001 — No vendor SDK type appears in any signature visible to the domain layer**

  Spec 009 не вводит **никаких** vendor types в `:core/commonMain`. Все Firebase surfaces (S1-S6) уже скрыты за портами 007/008. Новые external surfaces (S8-S13) — **Android платформа**, не «vendor SDK» в строгом смысле (см. CHK006), но требуют такого же ACL wrapping.

  **Watch (для plan.md)**:
  - `ConfigSnapshot` (envelope `/config/history/*`, FR-036) — **domain data class** в commonMain, не Firebase `DocumentSnapshot`. ✅ (явно в spec.md написано «Key Entities» — domain typed).
  - `PhoneHealthIndicator` — local UI type **не в domain layer** (явно зафиксировано в Key Entities). Это правильно: UI projection живёт в `app/health-ui/`, не в `:core/commonMain/api/health/`. Adapter `HealthToPhoneIndicatorAdapter` — в `app/` (UI module), не в `:core` — это «UI-projection adapter», не «vendor adapter», но boundaries соблюдены.

- [x] **CHK002 — Each external SDK has exactly one wrapper module (adapter); domain references only the port**

  Re-used adapters: `FirebaseRemoteSyncBackend`, `FirebasePushReceiver/Sender`, `RoomLocalConfigStore`, `ConnectivityManagerNetworkAvailability` — каждый одинокий.

  **NEW adapters мандатно один-в-один** (action для plan.md):
  - `AndroidSystemContactPicker` (S8) — единственное место, где `ContactsContract.CommonDataKinds.Phone.CONTENT_URI`, `cursor.getString(ContactsContract...)`, `Intent.ACTION_PICK` появляются в коде.
  - `AndroidVCardImport` (S9) — единственное место, где VCard text parser (BufferedReader, encoding detect) работает.
  - `AndroidInstalledAppsCatalog` (S10) — единственное место `PackageManager.getInstalledApplications` / `getLaunchIntentForPackage` для **admin-side списка**.
  - `AndroidOpenAppDispatcher` (S11+S13) — единственное место `PackageManager.getPackageInfo` + `LAUNCHER` intent + `market://` fallback для **Managed-side dispatch**.

- [x] **CHK003 — The "vendor disappears tomorrow" test: number of files needing change ≤ size of one adapter module**

  - **Firebase vanishes**: переписать 4 adapter'a (RemoteSyncBackend, PushReceiver/Sender, и server-side Worker). 1-2 недели per 007. Spec 009 не увеличивает coupling — наоборот, новый `ConfigHistoryRepository` port добавляет ещё один слой изоляции для history-related Firestore writes.
  - **Android Contacts API changes**: переписать `AndroidSystemContactPicker` adapter. `SystemContactPicker` port + `Contact.fromRaw()` остаются. Estimate: 1-2 дня.
  - **VCard format vanishes (заменён на JSON-LD или vCard 4.0 fully)**: переписать `AndroidVCardImport`. Port `VCardImport` остаётся (или переименовываем в `ContactSharePayloadImport` если хотим generalize). Estimate: 2-3 дня.
  - **PackageManager API breaks**: переписать `AndroidInstalledAppsCatalog` + `AndroidOpenAppDispatcher`. Ports остаются. Estimate: 1 день.
  - All within adapter-module scope. ✅

## Transport types

- [x] **CHK004 — No transport types appear in domain signatures**

  - `RemoteSyncBackend` (re-used) принимает `JsonElement` + domain types. ✅
  - **NEW**: `ConfigHistoryRepository.list(linkId): Outcome<List<ConfigSnapshot>, BackendError>` — должен принимать/возвращать **domain types only**. Никаких `QuerySnapshot`, `DocumentSnapshot`, `Task<>` Firebase types.
  - **NEW**: `SystemContactPicker.pickContact(): Outcome<RawPickerContact, PickerError>` где `RawPickerContact = (displayName: String, phoneNumbers: List<String>)` — domain projection, не `android.net.Uri` или `Cursor`. Adapter извлекает данные из cursor'a и возвращает domain shape.
  - **NEW**: `VCardImport.parse(rawBytes: ByteArray): Outcome<RawVCardContact, VCardError>` где `RawVCardContact = (displayName: String, phoneNumbers: List<String>)` — domain projection. Adapter принимает `Intent` или `ByteArray` (через bridge в app/), парсит, возвращает domain shape.

  **Watch для plan.md**: `Intent`-приём VCard происходит в `Activity`/`BroadcastReceiver` (app module), который читает `EXTRA_STREAM` или `EXTRA_TEXT`, **извлекает `ByteArray`**, и передаёт его в `VCardImport.parse()` (core port). `Intent` сам **никогда не пересекает** границу `:core/commonMain`.

- [x] **CHK005 — Wire format type is domain-owned data class with serializers in adapter**

  - `ConfigSnapshot` (NEW wire entity, `/config/history/{autoId}`, FR-036) — domain data class в `:core/commonMain/api/config/`, с **двумя независимыми schemaVersion'ами** (envelope + config внутри). Action для plan.md: `ConfigSnapshot.kt` + `ConfigSnapshotWireFormat.kt` рядом с существующими `ConfigDocument.kt` / `ConfigDocumentWireFormat.kt`.
  - `PresetSettings?` (forward-compat поле, FR-013) — domain data class (всегда null в 9). `presetOverrides: PresetSettings?` поле в `ConfigDocument` — additive, без bump `schemaVersion`. Action для plan.md: stub data class `PresetSettings(...)` с пустым body или `@Serializable data class PresetSettings(val phoneHealthSettings: PhoneHealthPresetWire? = null)` — но **только если** в спеке 9 реально потребуется. Иначе — комментарий «reserved» без actual fields.

## Platform types

- [x] **CHK006 — No `android.*`, `androidx.*`, `Intent`, `Uri`, `Context`, `Bundle`, `LifecycleOwner` appears in `commonMain`**

  - **High-risk surface для регрессий**: S8 (Contacts SDK), S9 (VCard intent), S10/S11 (PackageManager). Спек явно ссылается на `Intent.ACTION_PICK`, `ContactsContract.CommonDataKinds.Phone.CONTENT_URI` (FR-024), `ACTION_SEND` + `text/x-vcard` (FR-027), `<queries>` манифест (FR-035), `market://` URI (FR-035).

    **Все эти типы должны жить ИСКЛЮЧИТЕЛЬНО в androidMain adapter'ах**. Domain ports принимают/возвращают только `String` projections и domain-defined data classes.

  - **Action для plan.md** — явно зафиксировать port shapes:
    - `SystemContactPicker.pickContact(): Outcome<RawPickerContact, PickerError>` — никаких `Uri`, `Cursor`, `Intent` в сигнатуре. Picker activity result обрабатывается **в androidMain adapter** (ActivityResultLauncher), результат конвертируется в `RawPickerContact` перед возвращением через port.
    - `VCardImport.parse(rawBytes: ByteArray, sourceLabelHint: String? = null): Outcome<RawVCardContact, VCardError>` — `ByteArray` приемлемо (это Kotlin primitive, не Android). Никаких `Intent` / `Uri`.
    - `InstalledAppsCatalog.listLaunchableApps(): List<InstalledAppInfo>` где `InstalledAppInfo = (packageName: String, displayName: String, iconRef: AppIconRef)`. **Watch**: `iconRef` — это домен-typed projection (например, sealed class или просто `packageName` который UI разрешает в Drawable через свой собственный Android-specific resolver). `Drawable`/`Icon` Android **не** утекает в commonMain.
    - `OpenAppDispatcher.openOrInstall(packageName: String): Outcome<Unit, OpenAppError>` — return-only-domain. Adapter сам строит intent, запускает activity, обрабатывает Play Store fallback.

  - **Action для plan.md** — расширить Konsist gate (Phase 10 pattern из 007):
    - `:core/commonMain/api/config/` не имеет `import android.*`, `import androidx.*`.
    - `:core/commonMain/api/contacts/` (NEW package) не имеет того же.
    - `:core/commonMain/api/apps/` (NEW package для S10/S11) не имеет того же.

- [x] **CHK007 — Domain values carry domain-typed projection, not raw platform type**

  - **`packageName: String`** (для `Slot.args = {packageName}` per FR-034) — это **String**, не `android.content.ComponentName` или `android.content.pm.ApplicationInfo`. ✅ Existing pattern (`SlotKind.OpenApp` уже в spec 008).
  - **`phoneNumber: String`** (после adapter normalization в `Contact.fromRaw`) — String, не `PhoneNumberUtils.PhoneNumber`. ✅
  - **`displayName: String`** — String. ✅
  - **`packageIconRef: ???`** — NEW. **Decision для plan.md**: либо (а) `packageName: String` достаточно — UI сам резолвит иконку через свой adapter (рекомендуется, нулевые leaks); либо (б) introduce `AppIconRef` sealed class в domain если нужна сериализация иконки.
  - **Watch**: `Contact.photoRef` уже `String?` per existing 008 code — оставить как есть.

## Ports

- [x] **CHK008 — Every external surface used by this spec is exposed through a port**

  Re-used:
  - `RemoteSyncBackend`, `ConfigEditor`, `LocalConfigStore`, `HealthRepository`, `PushReceiver/Sender`, `NetworkAvailability`, `AppForegroundEvents`, `PermissionRequester` (если существует).

  **NEW (mandatory для plan.md)**:
  - `SystemContactPicker` (commonMain `api/contacts/`) — port для S8.
  - `VCardImport` (commonMain `api/contacts/`) — port для S9.
  - `InstalledAppsCatalog` (commonMain `api/apps/`) — port для S10 (admin-side список приложений).
  - `OpenAppDispatcher` (commonMain `api/apps/`) — port для S11+S13 (Managed-side launch + fallback).
  - `ConfigHistoryRepository` (commonMain `api/config/`) — port для S3/S4/S5 (write snapshot, list, delete oldest).

  **Decision: PermissionRequester** — если существующего port'a нет, рекомендуется новый минимальный port `PermissionRequester.request(perm: PermissionType): Outcome<Granted, Denied|PermanentlyDenied>` где `PermissionType` — sealed class в domain (`ReadContacts`, ...) **без** Android `Manifest.permission.READ_CONTACTS` строки. Adapter мапит на конкретный Android string.

  **Decision: PhoneHealthCriticalEventBus** — это **локальный event** (FR-021), не external surface. Можно держать как обычный `MutableSharedFlow<PhoneHealthCriticalEvent>` в domain или application service, **port не нужен** (нет fake/real пары, нет external dependency).

- [x] **CHK009 — Port shape is driven by domain need, not by adapter convenience**

  Каждый proposed port имеет **domain-shaped** методы:
  - `SystemContactPicker.pickContact()` — domain op «дай контакт», не `pickContactByUri(uri)`.
  - `VCardImport.parse(bytes)` — domain op «разбери vcard», не `parseVCardFromIntentExtraStream(intent)`.
  - `InstalledAppsCatalog.listLaunchableApps()` — domain op, не `queryIntentActivities(LAUNCHER_INTENT)`.
  - `OpenAppDispatcher.openOrInstall(pkg)` — domain op «открой приложение или предложи установить», не `startActivity(Intent.LAUNCHER)`.
  - `ConfigHistoryRepository.append(snapshot)`, `list(linkId)`, `pruneToRetention(limit=10)` — domain ops, не `writeFirestoreDocument(collection, doc)`.

- [x] **CHK010 — Each port has a fake adapter (commonTest)**

  Existing fakes (re-use): `FakeRemoteSyncBackend`, `FakeConfigEditor`, `FakeLocalConfigStore`, `FakeHealthRepository`, `FakeNetworkAvailability`, `FakeAppForegroundEvents`.

  **NEW fakes (mandatory per CLAUDE.md §6, action для tasks.md)**:
  - `FakeSystemContactPicker` — programmable «next pickContact() returns ...».
  - `FakeVCardImport` — programmable «next parse(bytes) returns ...» + sample VCard fixtures (WhatsApp, Telegram, Viber).
  - `FakeInstalledAppsCatalog` — in-memory `List<InstalledAppInfo>`.
  - `FakeOpenAppDispatcher` — записывает invocations для assertion.
  - `FakeConfigHistoryRepository` — in-memory list, programmable conflict / retention behavior.

- [x] **CHK011 — Each port has a real adapter (androidMain)**

  - Spec 009 — Android-only (iOS не входит в проект).
  - Real adapters in `:core/androidMain/.../adapters/` (per existing pattern):
    - `AndroidSystemContactPicker` — uses `ActivityResultLauncher` + `ContactsContract`.
    - `AndroidVCardImport` — Java/Kotlin VCard parser (без `ezvcard` library — слишком жирная зависимость; писать минимальный parser per FR-028, который читает только `FN` и `TEL[n]`).
    - `AndroidInstalledAppsCatalog` — `PackageManager.queryIntentActivities(LAUNCHER_INTENT)`.
    - `AndroidOpenAppDispatcher` — `PackageManager.getPackageInfo` + `startActivity(LAUNCHER)` + `market://` fallback + web URL fallback.
    - `FirestoreConfigHistoryRepository` — uses existing `RemoteSyncBackend` (no direct Firebase access — это **composite adapter**, важная архитектурная деталь).

- [x] **CHK012 — DI wiring picks fake/real per build per CLAUDE.md rule §6**

  - Existing `realBackendModule` / `mockBackendModule` Koin модули (007). 009 расширяет их bindings для **всех 5 NEW портов**.
  - **Action для plan.md**: явный список bindings (`single<SystemContactPicker> { AndroidSystemContactPicker(get()) }` и т.д.) в `realBackendModule`; fakes в `mockBackendModule` для дев-сборки без granted READ_CONTACTS / без реальных VCard intent'ов.

## Source-set placement

- [x] **CHK013 — Every new file clearly assigned to commonMain / androidMain / iosMain**

  Recommended placement (для plan.md — table format для tasks):

  **`:core/commonMain/`** (pure domain, no platform deps):
  - `api/config/ConfigSnapshot.kt` — wire entity envelope для history.
  - `api/config/ConfigSnapshotWireFormat.kt` — serialization.
  - `api/config/ConfigHistoryRepository.kt` — port.
  - `api/config/PresetSettings.kt` — forward-compat stub (если решим, что нужен).
  - `api/contacts/SystemContactPicker.kt` — port.
  - `api/contacts/VCardImport.kt` — port.
  - `api/contacts/RawPickerContact.kt` — domain projection.
  - `api/contacts/RawVCardContact.kt` — domain projection.
  - `api/contacts/ContactValidationError.kt` — sealed class (NameTooLong, PhoneInvalid, ...).
  - `api/contacts/Contact.kt` — **EXTEND existing** — add `companion object { fun fromRaw(...): Result<Contact> }` per `## Domain validation contract`. Pure validation logic.
  - `api/apps/InstalledAppsCatalog.kt` — port.
  - `api/apps/OpenAppDispatcher.kt` — port.
  - `api/apps/InstalledAppInfo.kt` — domain data class.
  - `api/health/PhoneHealthPreset.kt` + `DEFAULT_PHONE_HEALTH_PRESET` constant (FR-019). **Watch**: это **не часть domain core layer** — может жить в `app/health-ui/` тоже. Decision для plan.md per Key Entities note «not part of domain layer».
  - `api/health/PhoneHealthCriticalEvent.kt` — local event type.

  **`:core/androidMain/`** (Android-specific adapters):
  - `adapters/contacts/AndroidSystemContactPicker.kt`.
  - `adapters/contacts/AndroidVCardImport.kt` — includes minimal VCard parser (FR-028 strict subset).
  - `adapters/apps/AndroidInstalledAppsCatalog.kt`.
  - `adapters/apps/AndroidOpenAppDispatcher.kt` — includes Play Store fallback.
  - `adapters/config/FirestoreConfigHistoryRepository.kt` — composite adapter over RemoteSyncBackend.

  **`:core/commonTest/`**:
  - Fakes for каждого NEW port (см. CHK010).
  - Contract tests:
    - `Contact.fromRaw` validation rules (per `## Domain validation contract` table) — pure unit tests.
    - `VCardImport` parse contract — feed sample VCards from WhatsApp/Telegram/Viber, assert correct extraction.
    - `SystemContactPicker` contract — feed sample raw inputs (Cursor mock via fake), assert correct output.

  **`:app/`** (UI module, может содержать platform code где это естественно):
  - `health-ui/HealthToPhoneIndicatorAdapter.kt` — UI projection (domain Health → UI PhoneHealthIndicator).
  - `health-ui/PhoneHealthIndicator.kt` — local UI type (Key Entities note explicit).
  - VCard-receive `Activity` (handles `ACTION_SEND` intent-filter) — extracts `ByteArray` from `EXTRA_STREAM`, passes to `VCardImport.parse()`. Intent stays in app module.

- [x] **CHK014 — Default placement is commonMain; deviation has explicit reason**

  Каждый androidMain-файл имеет explicit reason (uses Android platform API: ContactsContract, PackageManager, Intent system). Каждый commonMain-файл — pure Kotlin + kotlinx primitives.

  **Exception note**: `PhoneHealthIndicator` намеренно живёт в `app/health-ui/` (не `commonMain/api/`) — это UI-layer projection, а не domain entity. Это **правильное** отступление, явно зафиксированное в spec.md Key Entities: «**Не часть domain layer** (не претендует на universality для часов / сенсоров)».

## Existing-code regressions

- [x] **CHK015 — Spec doesn't reintroduce any vendor type into a commonMain file already cleansed by prior specs**

  Spec.md ссылается на Android Contacts / VCard / PackageManager / Intent / Play Store / Firebase — но это в **тексте спека**, не в коде commonMain.

  **High-risk watch для plan.md / tasks.md**:
  - **`Contact.kt` extension** (для `fromRaw`) — НИ В КОЕМ СЛУЧАЕ не импортировать `android.provider.ContactsContract`, `android.telephony.PhoneNumberUtils`. Валидация **pure Kotlin regex** (`^\+?\d{5,20}$`) и `String` operations. Trim/normalize — в `String` API, не `PhoneNumberUtils.normalizeNumber()`.
  - **`Slot.kt` (existing)** — `args: JsonObject` остаётся. Не добавлять `packageName: String` поле в `Slot` directly — `args` уже опционально содержит kind-specific шейп.
  - **`SlotKind.OpenApp` (existing)** — уже в commonMain, без vendor deps. ✅
  - **`ConfigDocument.kt` extension** для `presetOverrides: PresetSettings?` — поле опционально, default null. Additive, не bump schemaVersion. **Watch**: `PresetSettings` data class сам не должен иметь Android deps.

- [x] **CHK016 — Spec doesn't add new expect/actual declaration where pure-Kotlin would suffice**

  Spec.md не упоминает expect/actual явно. Все NEW порты — обычные `interface` в commonMain с adapter `class` в androidMain через DI — это **предпочтительный pattern** (порты через interface, не expect/actual). ✅

  **Watch для plan.md**: если возникнет соблазн использовать expect/actual для VCard parser ради «общего» API на iOS — отвергать. Spec 009 Android-only.

---

## Summary

| Status | Count | Notes |
|---|---|---|
| ✅ Pass (spec-level) | 16 | All 16 checks pass at spec-level |
| 📋 Action items для plan.md | many | 5 new ports + 5 new androidMain adapters + 5 new fakes + Konsist gate extension |
| ⚠️ Watch items | 4 | См. список ниже |
| ❌ Fail | 0 | — |

**Verdict: PASS at spec-level.**

Спек 009 **архитектурно корректен** в отношении domain isolation. Новый раздел spec.md `## Domain validation contract` — образцовое решение проблемы C3 «per-provider vs universal»: domain `Contact.fromRaw()` + per-provider adapters. Это **прямое исполнение CLAUDE.md rule 2** (Anti-Corruption Layer) на multi-source design.

Однако спек **существенно расширяет attack surface** новых платформенных интеграций (Contacts SDK, VCard intent, PackageManager). Без чёткой фиксации portов и konsist gates в `plan.md` риск регрессий выше, чем в спеке 008 (где всё сводилось к Firebase + Room).

---

## Mandatory action items для plan.md

1. **Создать 5 NEW ports** в `:core/commonMain/`:
   - `api/contacts/SystemContactPicker.kt`
   - `api/contacts/VCardImport.kt`
   - `api/apps/InstalledAppsCatalog.kt`
   - `api/apps/OpenAppDispatcher.kt`
   - `api/config/ConfigHistoryRepository.kt`

2. **Расширить `Contact.kt`** — добавить `companion object { fun fromRaw(rawName: String, rawPhone: String): Result<Contact> }` per `## Domain validation contract` правилам. Pure Kotlin, no platform deps.

3. **Создать domain projections** в `:core/commonMain/api/contacts/`:
   - `RawPickerContact` = (displayName: String, phoneNumbers: List<String>)
   - `RawVCardContact` = (displayName: String, phoneNumbers: List<String>)
   - `ContactValidationError` (sealed class)

4. **Создать NEW wire entity** `ConfigSnapshot` в `:core/commonMain/api/config/`:
   - Два независимых schemaVersion'a (envelope + config внутри) per FR-036 / C2.
   - Roundtrip test (write → read → assert equal) per CLAUDE.md rule 5.

5. **Создать 5 NEW androidMain adapters** в `:core/androidMain/adapters/`:
   - `AndroidSystemContactPicker` (ContactsContract).
   - `AndroidVCardImport` (минимальный VCard parser, **без** ezvcard dependency, **только** FN + TEL[n], 10 KB limit per FR-028).
   - `AndroidInstalledAppsCatalog` (PackageManager.queryIntentActivities).
   - `AndroidOpenAppDispatcher` (PackageManager.getPackageInfo + LAUNCHER intent + market:// fallback + web fallback).
   - `FirestoreConfigHistoryRepository` (composite adapter over RemoteSyncBackend).

6. **Создать 5 NEW fakes** в `:core/commonTest/` per CLAUDE.md §6.

7. **Расширить Koin modules** — bindings для каждого NEW port в `realBackendModule` / `mockBackendModule`.

8. **Расширить Konsist gates** (по pattern Phase 10 спека 007):
   - `:core/commonMain/api/contacts/` не имеет `import android.*` / `import androidx.*`.
   - `:core/commonMain/api/apps/` — то же.
   - `:core/commonMain/api/config/ConfigSnapshot.kt` — то же.
   - Расширенный `Contact.kt` (после `fromRaw`) — то же. Особо: **никакого** `android.telephony.PhoneNumberUtils` или `android.provider.ContactsContract`.

9. **Создать contract tests** в `:core/commonTest/`:
   - `Contact.fromRaw` — все 5 правил валидации (см. таблицу в spec.md).
   - VCard parse — sample inputs от WhatsApp/Telegram/Viber.
   - System picker — sample raw cursor outputs.

10. **ADR-001 Platform Parity Gate**: подтвердить, что iOS не входит в 009 scope. Spec 009 — Android-only (Contacts SDK / VCard intent / PackageManager — Android API surfaces).

## Watch items (требуют решения в plan.md, не в spec.md)

- **WATCH-1 (HealthToPhoneIndicatorAdapter placement)**: spec.md явно пишет, что `PhoneHealthIndicator` — UI-layer, не domain. Подтвердить в plan.md, что adapter живёт в `:app/health-ui/`, не в `:core`. Это «UI projection adapter», не «vendor adapter».

- **WATCH-2 (PhoneHealthPreset placement)**: data class с захардкоженными threshold'ами per FR-019. Это **pure data**, может жить и в commonMain (если предполагается, что бизнес-логика severity-классификации остаётся в core), и в app/health-ui/ (если severity-классификация — UI concern). Decision: в plan.md.

- **WATCH-3 (PermissionRequester reuse vs new)**: если в проекте уже есть port для runtime permissions — re-use. Иначе создать минимальный новый port. Проверить в plan.md.

- **WATCH-4 (Drag-and-drop API через `Modifier.dragAndDropSource/Target`)**: это **Jetpack Compose API**, живёт в UI layer (`:app/`), не в `:core`. Никаких domain implications. **Но**: spec FR-008 явно отмечает «two-way door fallback на `pointerInput`» — это UI implementation detail, не domain. ✅

**No spec.md edits required.** Спек 009 architecturally sound; вся работа — в plan.md / tasks.md следить за конкретной placement каждого нового файла.

---

## TL;DR (русский)

Спек 9 проходит domain-isolation checklist на spec-уровне — все 16 пунктов PASS, 0 FAIL. Архитектурная **сильная сторона**: новый раздел `## Domain validation contract` (`Contact.fromRaw()` + per-provider adapters) — это **образцовая** реализация CLAUDE.md rule 2 (ACL), решающая проблему «как валидировать контакты из 3+ источников (system picker / VCard / future Telegram SDK) единообразно».

**Что нового по сравнению со спеком 8**: значительно больше платформенных интеграций (Contacts SDK, VCard intent, PackageManager) — 5 новых портов в `:core/commonMain/`, 5 новых androidMain adapter'ов, 5 новых fakes. Все они **должны** быть явно зафиксированы в plan.md, иначе риск регрессии (Android типы протекут в commonMain) высокий.

**Top 3 риска утечки**:
1. **`Intent`/`Uri`/`Cursor`** из Android Contacts picker — easiest path для regression. Adapter должен сразу же конвертировать в `RawPickerContact(displayName, phoneNumbers)` domain projection, **не** возвращать наружу `Cursor` или `Uri`.
2. **`PackageManager.ApplicationInfo`/`Drawable`** из InstalledAppsCatalog — `Drawable` для иконок не должен утекать; решение — `packageName: String` + UI сам разрешает иконку через свой Android-specific resolver.
3. **VCard parser library temptation** — если разработчик добавит `ezvcard` зависимость в `:core/commonMain`, это утечёт vendor SDK type в domain. **Mitigation**: minimal hand-written parser в androidMain, только FN + TEL[n], ничего больше (FR-028 уже это требует).

**Нужно ли явно зафиксировать новые порты в спеке?** Нет, spec.md уже это делает в `## Domain validation contract` + Key Entities + FR-017 (`HealthToPhoneIndicatorAdapter`) + FR-026 (`SystemContactPickerAdapter`) + FR-028 (`VCardImportAdapter`). Всё остальное (`ConfigHistoryRepository`, `InstalledAppsCatalog`, `OpenAppDispatcher`) — это **plan-level** детали (как именно построить адаптеры), которые в спеке упоминать не нужно. Поэтому **spec.md edits — не требуются**.

**Summary counts**: ✅ 16 PASS / ⚠️ 4 watch items / ❌ 0 FAIL. Готов идти в `/speckit.plan` без блокеров.

# Checklist: domain-isolation — TASK-49 plan.md

**Target**: `specs/task-49-cloud-feature-inventory-offline-first/plan.md`
**Date**: 2026-06-23
**Stage**: post-plan, pre-tasks

## Inventory

Новые external surfaces / ports в плане:
1. **`CloudAvailability`** (port в `core/cloud/commonMain`) — внутренний API проекта, не external SDK.
2. **`LocalAlternative`** (port в `core/cloud/commonMain`) — внутренний API проекта.
3. **`EmergencyNumberResolver`** (port в `core/cloud/commonMain`) — wrap для `TelephonyManager`.
4. **`CloudAvailabilityImpl`** (adapter в `core/cloud/androidMain`) — wrap для DataStore + использует `AuthProvider` (внутренний port).
5. **`EmergencyNumberResolverImpl`** (adapter в `core/cloud/androidMain`) — wrap для `TelephonyManager`.
6. **`SOSDialerAlternative`** (adapter в `core/cloud/androidMain`) — wrap для `Intent.ACTION_DIAL`.
7. **`SignInExplanationScreen`** (Composable в `app/.../onboarding/`) — Compose UI, не port.
8. **`FcmTokenRegistrationGuard`** (wrapper в `app/.../auth/`) — uses `CloudAvailability` port.

External Android/system APIs использованные:
- `androidx.datastore.preferences` — wrapped by `CloudAvailabilityImpl`.
- `android.telephony.TelephonyManager` — wrapped by `EmergencyNumberResolverImpl`.
- `android.content.Intent.ACTION_DIAL` — used by `SOSDialerAlternative`.

External vendor SDK: **none directly used** (TASK-49 не trogает Firebase, Google Play Services и т.п. напрямую — это через `AuthProvider` port из TASK-3).

---

## Results

### Vendor SDKs

- [x] **CHK001** No vendor SDK type appears in domain signatures. `core/cloud/commonMain` имеет только Kotlin stdlib + coroutines + (internal) `AuthIdentity` type из TASK-3. Никаких Firebase, GMS, Coil types.
- [x] **CHK002** Каждый external SDK через ровно один adapter. DataStore → `CloudAvailabilityImpl`. TelephonyManager → `EmergencyNumberResolverImpl`. Intent → `SOSDialerAlternative`. Firebase Auth — через `AuthProvider` port (territory TASK-3, не наша).
- [x] **CHK003** "Vendor disappears" test: если DataStore удалят, нужно поменять `CloudAvailabilityImpl` (1 файл). Если TelephonyManager — `EmergencyNumberResolverImpl` (1 файл). Если Intent API — `SOSDialerAlternative` (1 файл). ✅ Each adapter ≤ 1 file change. Documented в plan.md §Architecture (module map).

### Transport types

- [x] **CHK004** No transport types. TASK-49 не делает network calls. DataStore IO — local file, не transport.
- [x] **CHK005** N/A — нет wire format (DataStore preference — internal cache, не leaves device).

### Platform types

- [x] **CHK006** `commonMain` чист:
  - `CloudAvailability.kt` — `Boolean`, `Flow`, `suspend fun`.
  - `LocalAlternative.kt` — `ActionContext`, `ActionResult` (data classes из Kotlin stdlib).
  - `EmergencyNumberResolver.kt` — `String`, `suspend fun`.
  - `ActionContext.kt` — `String`, `Map<String, String>`.
  - `ActionResult.kt` — sealed class.
  - **No** `android.*`, `Intent`, `Uri`, `Context`, `Bundle`, `LifecycleOwner`.
- [x] **CHK007** Все platform-derived data приходит в domain как `Boolean` (cloudAvailable) или `String` (emergency number). Никакие raw platform types не утекают.

### Ports

- [x] **CHK008** Каждая external surface через port:
  - DataStore → `CloudAvailability` port (DataStore не появляется в caller code).
  - TelephonyManager → `EmergencyNumberResolver` port.
  - Intent ACTION_DIAL → `LocalAlternative` interface (consumers вызывают `executeLocally`, не строят Intent сами).
- [x] **CHK009** Port shapes domain-driven:
  - `CloudAvailability` exposes "is cloud available" question, не "read from DataStore key X". ✅
  - `LocalAlternative` exposes "execute locally" action, не "build dialer Intent". ✅
  - `EmergencyNumberResolver` exposes "get emergency number", не "query TelephonyManager API 29+ with fallback to map". ✅
- [x] **CHK010** Each port has fake adapter:
  - `FakeCloudAvailability` — в plan.md §Architecture + contract `cloud-availability-port.md`.
  - `FakeEmergencyNumberResolver` — в plan.md §Test Strategy + contract `emergency-number-resolver-port.md`.
  - `FakeLocalAlternative` — в plan.md §Test Strategy + contract `local-alternative-port.md`.
  - `FakeAuthProvider` — existing в `core/commonTest`, переиспользуем.
  Все fake adapters в `core/cloud/commonMain/fake/` (visible тестам через source set).
- [x] **CHK011** Each port has real adapter:
  - `CloudAvailabilityImpl` (androidMain).
  - `EmergencyNumberResolverImpl` (androidMain).
  - `SOSDialerAlternative` (androidMain).
  iOS adapters out of scope этой спеки (Phase 4 V-1 / TASK-26).
- [x] **CHK012** DI wiring picks fake/real per build. Plan.md §Architecture упоминает Koin `CloudModule.kt` в `app/di/`. Detail для `/speckit.tasks`.

### Source-set placement

- [x] **CHK013** Каждый новый файл явно назначен:
  - `core/cloud/commonMain/api/*.kt` — pure Kotlin domain types, no platform deps.
  - `core/cloud/commonMain/fake/*.kt` — pure Kotlin fakes.
  - `core/cloud/commonTest/contracts/*.kt` — pure Kotlin tests using fakes.
  - `core/cloud/androidMain/impl/*.kt` — Android-specific implementations (DataStore, TelephonyManager, Intent).
  - `app/.../onboarding/SignInExplanationScreen.kt` — Compose UI, Android-specific.
  Justifications inline в plan.md §Project Structure.
- [x] **CHK014** Default placement в `commonMain`. Deviation justified: всё в `androidMain` потому что использует Android API (DataStore preferences — Android only при текущей конфигурации; TelephonyManager / Intent — Android only). iOS adapters не нужны для MVP (Phase 4 V-1).

### Existing-code regressions

- [x] **CHK015** Spec не reintroduce'ит vendor types в `commonMain`. Не trogает уже-вычищенные files из spec 016/017/018/019.
- [x] **CHK016** Никаких `expect`/`actual` declarations не добавляется. Всё либо pure Kotlin в `commonMain`, либо чистый Android в `androidMain`. iOS adapters out of scope.

---

## Summary

**16/16 PASS.** Plan TASK-49 полностью соответствует domain-isolation invariants:
- Domain (`core/cloud/commonMain`) изолирован от vendor / platform / transport.
- 3 port'а, каждый с fake + real adapter + planned DI wiring.
- Source-set placement explicit для каждого нового файла.
- "Vendor disappears" test soundly: 1 файл изменений per adapter.

**No remediation needed.**

---

## Plain Russian summary

Проверили план TASK-49 на соблюдение правил «доменный код не знает про Android / Firebase / network». **Всё чисто**: 3 порта в `core/cloud/commonMain` написаны на чистом Kotlin без упоминания Android, реальные реализации лежат отдельно в `androidMain` (DataStore, TelephonyManager, Intent.ACTION_DIAL). Если Google / DataStore завтра исчезнет — поменяется один файл на каждый API. Каждый порт имеет fake-версию для тестов и реальную — для устройства. Никаких нарушений правил.

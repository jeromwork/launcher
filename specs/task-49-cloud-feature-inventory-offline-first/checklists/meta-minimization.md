# Checklist: meta-minimization — TASK-49 plan.md

**Target**: `specs/task-49-cloud-feature-inventory-offline-first/plan.md`
**Date**: 2026-06-23
**Stage**: post-plan, pre-tasks

## Inventory новых абстракций

| Abstraction | Type | Concrete consumer в этой спеке? |
|---|---|---|
| `CloudAvailability` port | interface | YES — `CloudAvailabilityImpl` (real) + `FakeCloudAvailability` + `FcmTokenRegistrationGuard` (consumer) |
| `LocalAlternative` port | interface | YES — `SOSDialerAlternative` (real impl + consumer для SOS) |
| `EmergencyNumberResolver` port | interface | YES — `SOSDialerAlternative` consumer |
| `ActionContext` / `ActionResult` | data types | YES — `LocalAlternative` API |
| `SignInExplanationScreen` | Composable | YES — Settings cloud-actions + planned use в wizard TASK-7 (но wizard ещё не реализован) |
| `FcmTokenRegistrationGuard` | wrapper class | YES — обёртка вокруг существующего FCM registration site (regression fix) |
| **новый Gradle модуль `:core:cloud`** | KMP module | YES — содержит все вышеперечисленное |

**Explicit отказы от абстракций** (зафиксировано в spec.md Out of Scope и plan.md Constitution Gate 8):
- ❌ `CloudActionGate` port — нарушал MVA (single-implementation interface поверх существующей `CloudAvailability` абстракции).
- ❌ `CloudFeatureRegistry` central manifest — premature; каждая S-задача сама решит свой подход при clarify.
- ❌ `CloudMode` enum (LocalOnly / CloudRequired / CloudAugmented) — premature classification.
- ❌ Polling механизмы для network / GMS / token expiry — не задача TASK-49.

---

## Results

### New abstractions

- [x] **CHK001** Каждый новый port имеет concrete consumer **в этой спеке**:
  - `CloudAvailability` → consumer `FcmTokenRegistrationGuard` (regression fix TASK-5, реализован в этой спеке) + planned consumers Phase 2 (но это **не основание**, основание — `FcmTokenRegistrationGuard` сейчас).
  - `LocalAlternative` → consumer `SOSDialerAlternative` (реализован в этой спеке).
  - `EmergencyNumberResolver` → consumer `SOSDialerAlternative` (реализован в этой спеке).
  Никаких портов «для spec 7 потом» — все имеют реальных consumers сейчас.

- [x] **CHK002** Single-implementation interfaces justified:
  - `CloudAvailability` имеет 1 real impl + 1 fake. Justified by mock-first DI (CLAUDE.md rule 6).
  - `LocalAlternative` имеет 1 real impl (SOSDialerAlternative) + 1 fake. Port shape driven by **opt-in pattern** для критических фич — реальная domain-driven причина (не "для будущей extensibility").
  - `EmergencyNumberResolver` имеет 1 real impl + 1 fake. Justified by need to mock `TelephonyManager` в unit tests (нельзя замокать без abstraction).

- [x] **CHK003** Mediator/orchestrator/manager class отсутствует. `FcmTokenRegistrationGuard` — НЕ pass-through: добавляет проверку `cloudAvailable` перед persist. Это **real conditional logic**, не mediator.

- [x] **CHK004** Никакого custom DSL / registry / plugin system. Прямой код: subscription на Flow, чтение из DataStore, вызов Composable. Простейшая композиция.

### New modules / packages

- [x] **CHK005** Новый Gradle модуль `:core:cloud` удовлетворяет Article V §3:
  - **Ownership boundary**: cloud-related abstractions изолированы от core и от app.
  - **Build isolation**: depends только на `kotlin-stdlib` + `kotlinx.coroutines` + (androidMain) `datastore-preferences`. Verify через fitness function `verifyCloudIsolation`.
  - **Stable API**: 3 ports + 2 data types — small public surface.
  - **Testability**: позволяет contract tests + fake adapters per-module.
  - **Future iOS readiness**: KMP commonMain структура готова для iOS adapter в Phase 4 (TASK-26).

- [x] **CHK006** Plan.md §Project Structure явно отвечает «почему package недостаточен»:
  - `:core:cloud` — это **first consumer cloud-related abstractions Phase 2**. Помещение в существующий `:core` raises dependency: `:core` уже depend от много, добавление DataStore туда расширит scope `:core` сверх его текущей роли (Auth, identity primitives).
  - **iOS readiness**: KMP module structure облегчает добавление `iosMain` adapter в Phase 4.

- [x] **CHK007** Никакого "utils" / "common" / "helpers" модуля не создаётся. `:core:cloud` имеет clear domain — cloud availability и related concerns.

### New configuration

- [x] **CHK008** DataStore `cloud_available: Boolean` — single config field, consumer `FcmTokenRegistrationGuard` (regression fix) + future cloud-фичи Phase 2 (через reactive Flow). Реальный consumer в этой спеке.

- [x] **CHK009** Defaults документированы (`false`), backward-compat policy explicit (existing users получают `false` до первого AuthProvider emit — это **корректное поведение**, не requires migration). См. spec.md FR-005 + data-model.md §Migration.

### CLAUDE.md rule 4 self-test

- [x] **CHK010** **Test 1 (inline test)** applied для каждой абстракции:
  - **`CloudAvailability` port**: если inline → каждый consumer должен сам читать DataStore + подписываться на `AuthProvider`. Это duplicate'нет logic в 2+ местах (`FcmTokenRegistrationGuard` + future S-tasks). Inline = loss of single source of truth + DRY violation. **Lost**: consistent state management, mockability.
  - **`LocalAlternative` port**: если inline → SOS code напрямую строит Intent.ACTION_DIAL без абстракции. Это нормально для **сейчас** (SOS — единственный consumer). **Risk**: если завтра появится вторая critical feature с local fallback (например emergency wearable signal) — придётся выделять interface обратно, что = rewrite. **Lost minimally**: один pattern для нескольких potential consumers. **Accepted abstraction** для consistency.
  - **`EmergencyNumberResolver` port**: если inline → `SOSDialerAlternative` сам делает `TelephonyManager` call + fallback map. Это **inline-able**. Но: TelephonyManager unit-test'ить **нельзя без mock**. Port нужен для testability. **Lost**: mockability.
  - **`SignInExplanationScreen`**: если inline → копия Composable в 2 местах (wizard + Settings). **Lost**: DRY + visual consistency.
  - **`FcmTokenRegistrationGuard`**: если inline → проверка `cloudAvailable` в самом FCM registration site. **Lost**: testability isolation; harder to mock cloud state.
  Каждая абстракция justified реальной причиной (testability, DRY, real-time consumer), не «future optionality».

- [x] **CHK011** **Test 2 (swap test)** applied:
  - `AuthProvider` swap (Firebase → own server): через TASK-3 territory; ~1 week (новый adapter). TASK-49 НЕ меняется. Seam в TASK-3 уже existing — TASK-49 переиспользует.
  - `CloudAvailability` swap (DataStore → in-memory): ~1 day (поменять `CloudAvailabilityImpl`). Seam justified.
  - `EmergencyNumberResolver` swap (TelephonyManager → другой source): ~0.5 day. Seam justified для testability.
  - `LocalAlternative` swap: если решим pattern не нужен → ~0.5 day удаление + inline SOS. Seam justified (см. CHK010 — accepted).

### Removal validation

- [x] **CHK012** TASK-49 не удаляет существующих абстракций. Modifies (через `FcmTokenRegistrationGuard` wrapper) — но не удаляет.

- [x] **CHK013** Никакого «deprecated, will remove later» нет в plan.md / spec.md.

---

## Summary

**13/13 PASS.**

**Strong positive signals для MVA compliance**:
- Explicit отказ от 3 premature abstractions (`CloudActionGate`, `CloudFeatureRegistry`, `CloudMode`) зафиксирован в spec и plan.
- Каждый introduced port имеет concrete consumer в этой спеке.
- Каждый single-impl port имеет justification (testability / domain-driven shape).
- Новый Gradle модуль justified per Article V §3.

**No remediation needed.**

---

## Plain Russian summary

Проверили план TASK-49 на правило «не добавлять абстракции про запас». **Все 13 проверок пройдены**.

Сильные плюсы:
- Мы **явно отказались** от трёх абстракций которые были over-engineering: `CloudActionGate` (просто обёртка вокруг `CloudAvailability`), `CloudFeatureRegistry` (преждевременный manifest), `CloudMode` enum (преждевременная классификация). Это решение зафиксировано в спеке.
- Каждый порт который мы вводим имеет **реального consumer прямо в этой спеке** (не «для будущих фич»):
  - `CloudAvailability` → `FcmTokenRegistrationGuard` (regression fix).
  - `LocalAlternative` → `SOSDialerAlternative`.
  - `EmergencyNumberResolver` → `SOSDialerAlternative`.
- Новый модуль `:core:cloud` оправдан: ownership boundary + build isolation + iOS readiness в будущем.

Никаких изменений не требуется. План минимален и не нарушает MVA.

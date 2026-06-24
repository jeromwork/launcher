# Checklist: wire-format — TASK-49 plan.md

**Target**: `specs/task-49-cloud-feature-inventory-offline-first/plan.md`
**Date**: 2026-06-23
**Stage**: post-plan, pre-tasks

## Inventory wire formats / persistence

Что плановых artefact'ов потенциально под scope wire-format checklist:

| Artefact | Тип | Wire format? | Reason |
|---|---|---|---|
| DataStore `cloud_available: Boolean` | Persistence | **No** | Internal cache, не leaves device, single boolean без structure, не пересекается across major versions с migration. Документировано в data-model.md + spec.md FR-005. |
| `ActionContext` / `ActionResult` | Runtime types | **No** | Runtime data classes, не serialized, не persistent. Передаются между функциями in-memory. |
| `SignInExplanationScreen` strings | i18n resources | **No** | String resources (TASK-1 infrastructure), не wire format. |
| Recovery backup blob | — | **N/A** | TASK-6 territory, на Paused, не trogается этой spec'ой. |
| FCM token | — | **N/A** | TASK-5 territory; existing format не меняется. Только timing registration меняется (regression fix FR-013). |

**Вывод**: TASK-49 не вводит wire format'ов. Большинство checklist gates становятся **N/A**, но проходим явно для документирования.

---

## Results

### Schema version

- [N/A] **CHK001** Schema version не нужен — нет wire format'а. DataStore boolean — internal preference, не leaves device, не нуждается в migration policy (single boolean всегда совместим с собой).
- [N/A] **CHK002** N/A — нет deserialization.
- [N/A] **CHK003** N/A — нет schema constant.

### Backward compatibility

- [N/A] **CHK004** N/A — нет previous schema. Default value `false` на свежем install / clear data (документировано в spec FR-005 + data-model.md). Existing users post-upgrade получают `false` до первого `AuthProvider` emit — это **корректное поведение**, не requires migration.
- [N/A] **CHK005** N/A — нет fields.
- [N/A] **CHK006** N/A — нет переименований.
- [N/A] **CHK007** N/A — нет migrations.

### Forward compatibility

- [N/A] **CHK008** N/A — нет version discrimination.
- [N/A] **CHK009** N/A — нет discriminator.

### Tests

- [N/A] **CHK010** Roundtrip test для wire format не требуется. Но: **есть persistence test** — `CloudAvailabilityContractTest` invariant INV-7 «persistence survives recreate» (записал → kill → recreate → read). Это аналог roundtrip для preference, документирован в contract `cloud-availability-port.md`.
- [N/A] **CHK011** N/A — нет previous schema version fixture.
- [N/A] **CHK012** N/A — нет wire format fixtures.

### Persistence specifics

- [x] **CHK013** **DataStore key namespaced correctly**. Plan.md §Architecture pseudocode использует `booleanPreferencesKey("cloud_available")`. Это **bare string**, не namespaced формата `<domain>.<feature>.<key>`.
  - **Minor remediation**: переименовать в `cloud.availability.cloud_available` или похожий namespaced формат. **Не блокирующий** — single key в новом DataStore file, collision risk минимальный.
  - **Решение**: документирую как **soft recommendation** для `/speckit.tasks` фазы; в implementation использовать `cloud.availability.is_available` или `core.cloud.is_available`.
- [N/A] **CHK014** SQLDelight не используется в TASK-49.
- [N/A] **CHK015** Никакой removal stored type'а нет.

### Deep-link / QR / exported config

- [N/A] **CHK016** Нет deep-links / QR / exported config в этой спеке.
- [N/A] **CHK017** N/A.

### Contract folder

- [x] **CHK018** `contracts/` folder содержит 3 файла (`cloud-availability-port.md`, `local-alternative-port.md`, `emergency-number-resolver-port.md`). Каждый описывает **port interface**, не wire format. Файлы содержат invariants + implementations + roundtrip examples. Semantic version и breaking-change policy не указаны явно — это **port contracts**, не wire formats; для них policy = «interface остаётся compatible until major refactor», документировано в plan.md §Exit Ramps and Design Reversibility. **Acceptable**.

---

## Summary

**1 PASS (CHK013 — with soft remediation), 1 PASS (CHK018), 14 N/A.**

**No blocking failures.**

**Soft remediation for CHK013**: при implementation в `/speckit.tasks` фазе — использовать namespaced DataStore key (например `core.cloud.is_available` или `cloud.availability.is_available`) вместо bare `cloud_available`. Записал как TODO для tasks.md.

---

## Plain Russian summary

Проверили план TASK-49 на правила «версионирование форматов данных». **TASK-49 не вводит никаких форматов данных** — нет JSON, нет deep-link'ов, нет QR-кодов, нет exported файлов. Единственная persistence — один булев в Android DataStore — это **внутренний кэш**, не контракт с внешним миром, не требует версионирования.

Одно мелкое замечание: ключ DataStore надо назвать более «безопасно» (например `cloud.availability.is_available` вместо просто `cloud_available`) — чтобы случайно не было коллизий, если в будущем добавится другой DataStore preference. Это не блокер, просто convention для implementation.

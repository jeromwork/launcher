# Checklist: meta-minimization

**Spec**: `spec.md` (rev. 2026-05-14, post-clarify Q1-Q10)
**Run**: 2026-05-14 — `/speckit.clarify` post-pass before `/speckit.plan`.

Anti-bloat audit per Article XI of [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md) and rule 4 of [`CLAUDE.md`](../../../CLAUDE.md).

---

## New abstractions

### Inventory of new abstractions in spec 008

| Entity | Type | Concrete consumer in 008 |
|---|---|---|
| `ConfigDocument` | Wire-format data class | FR-001..006, FR-010..014, FR-040, FR-051 |
| `StateDocument` | Wire-format data class | FR-030..034, SC-001b |
| `LocalAppliedConfig` | Room entity | FR-041, FR-044, SC-004a |
| `PendingLocalChanges` | Room entity | FR-042, FR-043, FR-046, FR-047 |
| `ConfigDiff` | Domain value | FR-014, FR-051..054, SC-007 |
| `ConfigApplier` | Domain port (interface) | FR-021, FR-023, FR-030 |
| `ConfigEditor` | Domain port (interface) | FR-040, FR-042, FR-050, FR-054 |

---

- [x] **CHK001 — Every new interface/port has at least one concrete consumer in this spec**
  - `ConfigApplier`: реальный consumer — Managed-side apply flow (FR-021 FCM trigger → apply; FR-022 four triggers → apply; FR-023 self-as-writer skip). Не «когда-нибудь», прямо в US-1 и US-3.
  - `ConfigEditor`: реальный consumer — все три типа editor'ов (admin-phone, admin-tablet, Managed-phone) для US-1/US-2/US-3/US-4. Save локально + push + merge resolution.
  - Прочие entities — это data shapes (ConfigDocument, StateDocument, LocalAppliedConfig, PendingLocalChanges, ConfigDiff), не interfaces — CHK001 к ним не применим как к interfaces, но проверка «есть consumer прямо сейчас» — все имеют FR-cross-references.

- [x] **CHK002 — If a new interface has only one implementation: justified by port-shape need**
  - `ConfigApplier`: будет два implementations — **fake** (для tests + mockBackend flavor, per `mock-first development` CLAUDE.md §6) + **real** (Firestore + Room). Per CLAUDE.md §6: каждый external port имеет fake + real. Это **обязательное** правило проекта.
  - `ConfigEditor`: то же — fake (для tests + admin-side test fixture per US-1 OUT-001) + real. Также — Managed-side editor может иметь slightly different implementation от admin-side (например, Managed читает self-write check), но это **один port, два consumers**, не два interfaces.
  - Conclusion: оба ports оправданы (mock-first development + multiple consumers).

- [x] **CHK003 — Mediator/orchestrator/manager class is justified by data transformation**
  Spec не вводит "*Manager", "*Mediator", "*Orchestrator", "*Coordinator" классов. ConfigApplier и ConfigEditor — это **adapter ports**, не оркестраторы. Хорошо.

- [x] **CHK004 — No custom DSL, registry, or plugin system unless simpler composition has been tried**
  Никаких DSL / registry / plugin. Diff-алгоритм (FR-051) — обычная функция, не DSL.

## New modules / packages

Spec 008 **не вводит новых gradle модулей**. Это критично проверить — модули в роадмапе нашего проекта чувствительны (Article V §3). Текущий план (implicit, до plan.md):
- Domain types (ConfigDocument, StateDocument, ConfigDiff) → `:core/commonMain` (existing module).
- ConfigApplier / ConfigEditor interfaces → `:core/commonMain`.
- Firebase adapter → `:core/androidMain` (existing pattern из спека 007).
- Room adapter → `:core/androidMain` (existing pattern, либо `:storage/androidMain` если выделится).
- Merge UI → `:app` (existing module).

- [x] **CHK005 — New gradle module satisfies at least one of Article V §3 criteria** — N/A (нет новых модулей).
- [x] **CHK006 — If new module is added: "Why is a package not enough?"** — N/A.
- [x] **CHK007 — No "utils" / "common" / "helpers" dumping ground module created** — N/A; spec не создаёт generic-helpers.

**Note для plan.md**: если на этапе планирования окажется, что Room-adapter естественнее выделить в `:storage/androidMain` (по аналогии с тем, как 007 не выделял `:pairing/`, а размещал в `:core/`), — это решение plan.md, не spec.md, и должно пройти Article V §3 gate (procedure-constitution-check).

## New configuration

### Inventory of new fields

| Field | Document | Consumer in 008 | Future use |
|---|---|---|---|
| `/config.schemaVersion` | ConfigDocument | FR-005 (roundtrip test), FR-006 (additive policy) | future spec extensions (additive) — additive не считается speculative |
| `/config.serverUpdatedAt` | ConfigDocument | FR-002, FR-012, FR-013, SC-007 | — |
| `/config.lastWriterDeviceId` | ConfigDocument | FR-023 (self-as-writer skip) | — |
| `/config.presetId` | ConfigDocument | FR-003, US-1 scenario 1 | — |
| `/config.flows[]` / `slots[]` | ConfigDocument | FR-003, US-6 | — |
| `/config.contacts[]` | ConfigDocument | FR-003, US-6 | — |
| `element.id` (UUID v4) | All elements | FR-004, SC-007 | — |
| `/state.appliedConfigUpdatedAt` | StateDocument | FR-031, SC-001b | — |
| `/state.flowsApplied[]` | StateDocument | FR-033 | — |
| `/state.contactsApplied[]` | StateDocument | FR-033 | — |
| `/state.partialApplyReasons[]` | StateDocument | FR-033, Edge Cases "partial apply" | — |

- [x] **CHK008 — New config field has a current FR consuming it**
  Все 11 полей имеют FR/SC consumer прямо в 008. Никаких полей вида «может пригодится в спеке 009». **Особо проверено**: поля `requiredManagedAppVersion` / `managedAppVersion` / `compatibilityError` — НЕ добавлены в 008 (OUT-006). Не предзакладываем поля «на потом».

- [x] **CHK009 — Config field defaults documented; backward-compat policy defined; migration path documented**
  - `schemaVersion` = 1 from first commit (FR-001).
  - Optional vs required разделены в FR-003 (минимум: schemaVersion, serverUpdatedAt, lastWriterDeviceId, presetId, flows, contacts).
  - Backward-compat: FR-005 (roundtrip + read v1 by future v2 reader), FR-006 (additive only, rename/remove requires schemaVersion bump).
  - Migration path: FR-045 (cleanup of legacy mock-storage at first launch) — не migration в строгом смысле, а cleanup, что явно обосновано (003 не в production).

## CLAUDE.md rule 4 self-test

- [x] **CHK010 — Test 1 applied: if abstraction were inlined, what would be lost?**

  - **ConfigApplier (port)**: Если бы inline'или — Managed-side apply flow таскал бы Firebase + Room прямо из presentation/UI слоя; нарушение CLAUDE.md §1 (domain isolation); невозможны fakes для unit tests. **Loss**: ability to test apply logic без real Firestore. Port оправдан.
  - **ConfigEditor (port)**: Если бы inline'или — каждый editor (admin-phone, admin-tablet, Managed-phone) дублировал бы save+push+merge код. Loss: code duplication × 3, divergence over time. Port оправдан.
  - **ConfigDiff (data value)**: Inlined alternative — везде передавать пару `(local, server)` и вычислять diff inline. Loss: невозможно отдельно тестировать diff algorithm (SC-007); merge UI вынужден работать с raw configs. Value type оправдан.
  - **LocalAppliedConfig / PendingLocalChanges** (Room entities): они **являются** persistence form, inline = nothing. They are the persistence; nothing to inline. Оправданы.

- [x] **CHK011 — Test 2 applied: if dependency on the other side doubled in price / was deprecated / violated privacy, how long to swap?**

  - **Firestore deprecated / banned**: ConfigApplier и ConfigEditor — это ports; real implementation в `:core/androidMain/...FirebaseConfigApplier.kt`. Чтобы swap'нуть на (например) Supabase или own-backend: переписать **только** real adapter, plus Cloudflare Worker. domain + UI + tests остаются. Estimate: 1-2 недели (consistent with спек 007 wrap-cost). Port оправдан (>1 день swap-time → seam stays per CLAUDE.md §4 Test 2).
  - **Room deprecated**: aналогично, LocalAppliedConfig / PendingLocalChanges — это Room entities. Чтобы swap'нуть на SQLDelight / SharedPreferences: переписать `LocalStorageAdapter` (`:core/androidMain/...RoomLocalStorage.kt`); domain ports остаются. Estimate: 3-5 дней. Port оправдан.
  - **FCM deprecated**: уже инфраструктура из 007 — спек 008 наследует. Spec 008 не добавляет FCM-specific seam.
  - **ConnectivityManager.NetworkCallback deprecated**: единственный consumer — Managed-side refresh trigger (T2 в FR-022). Если deprecated — заменить на WorkManager network constraints (3-4 дня). Port вокруг NetworkCallback **не** введён в spec.md — и это правильно, потому что это **single use site**. CHK010 inline test: «если inline'ить — потеряем что?» — почти ничего; одна вызов callback`'а. **Возможно требует port'а в plan.md если появится второй consumer**. Сейчас не требуется. ✅

## Removal validation

- [x] **CHK012 — If spec removes existing abstractions/modules: dangling references audited**

  Spec 008 удаляет / overrides:
  - **Mock JSON storage из спека 003** (FR-045): cleanup at first launch. Dangling references: проверено grep'ом — mock-JSON упоминается в спеке 003 (`AdminDevicesFragment`, `presets/simple-launcher.json`?). Эти файлы остаются как **demo fixtures**, но runtime больше не читает их — spec.md `Assumptions` блок отмечает это. Action для plan.md: явно проинвентаризовать места ссылок и решить — delete files (cleanup) или leave as demo-fixtures (для тестов).
  - **`/state.fcmToken` (bootstrap-only) из спека 007**: FR-032 явно говорит «расширяет 007 additive (без bump schemaVersion)» — fcmToken остаётся, additive поля добавляются. Никакого removal.
  - Никаких других removals.

- [x] **CHK013 — If spec marks code "deprecated, will remove later" — concrete removal task in tasks.md**
  Spec 008 не использует pattern «deprecated, remove later». Все OUT-блоки — это **forward references** в другие спеки, не deprecation в текущем коде. ✅

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 13 | All 13 checks |
| ⚠️ Watch | 1 | CHK012 — mock-JSON files dangling references; action item для plan.md (явная инвентаризация) |
| ❌ Fail | 0 | — |

**Verdict: PASS** — no speculative abstractions, no premature modules, no fields-for-future-use.

**Particular merit**:
- Q4 (schema mismatch) → вынесено в OUT-006 + backlog spec. Это **good anti-bloat** — мы НЕ добавили в 008 fields/logic для use case, которого пока нет.
- Q7 (rollback) → вынесено в spec 009. Same merit.
- Q10 (size limits) → не добавлено в 008. Same merit.

**Action items для plan.md / tasks.md**:
1. Явно инвентаризовать ссылки на mock-JSON storage в 003-коде (FR-045 cleanup scope) — что именно удалять, что оставлять как test/demo fixture.
2. Решение о gradle modules (Room-adapter в `:core/` или `:storage/`) — proceed in plan.md, pass through Article V §3.
3. Plan.md: явно зафиксировать decision «no port wrapping `NetworkCallback`» (single use site, inline ok), чтобы будущий reviewer не задался вопросом «почему port не введён».

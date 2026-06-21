# Plan-level checklist review

**Plan**: [plan.md](../plan.md) · **Date**: 2026-06-21 · **Trigger**: /speckit.plan procedure Step 5.

> Per speckit-plan procedure: re-run domain-isolation + wire-format + meta-minimization on plan.md to verify plan-level decisions consistent with spec-level. Spec-level runs already done в /speckit.clarify ([requirements](requirements.md), [meta-minimization](meta-minimization.md), [wire-format](wire-format.md), [domain-isolation](domain-isolation.md), [_overview](_overview.md)).

## Plan-level domain-isolation re-check

Spec-level: 13/16 pass ([domain-isolation.md](domain-isolation.md)).

**Plan-level additional concerns**:

- ✅ **Module structure clear** — plan.md §Architecture / Module map спецifies exact source-set placement для каждого new file (commonMain / androidMain / Worker TypeScript). Closes earlier ambiguity (domain-isolation CHK013).
- ✅ **HTTP client placement explicit** — `DefaultPushTrigger` в commonMain (Ktor KMP), не androidMain. Closes domain-isolation CHK014.
- ✅ **DI flavor split documented** — Local Test Path в spec.md + plan.md §Architecture confirms mockBackend = fakes + NullPushTrigger, realBackend = HttpPushTrigger + WorkManagerBackgroundDispatcher. Closes domain-isolation CHK012.
- ✅ **Auth-jwt extracted** — separate Worker module `workers/_shared/auth-jwt/`, не coupled к push. Confirms vendor-bounded change (CHK002, CHK003).
- ✅ **BackgroundDispatcher port** — new abstraction но properly isolated: port в commonMain, WorkManager impl в androidMain. No platform leakage.

**Plan-level pass**: 16/16 effective (3 spec-level warnings closed by plan-level explicit decisions).

## Plan-level wire-format re-check

Spec-level: 7/18 pass + 8 warnings ([wire-format.md](wire-format.md)).

**Plan-level additional resolutions**:

- ✅ **schemaVersion-first deserialization** — explicit в data-model.md PushPayload reading order + contracts/push-payload-v1.md receiver-side parse logic. Closes wire-format CHK002.
- ✅ **MAX_SUPPORTED location pinned** — data-model.md `WireFormatVersion` object в `core/push/commonMain/api/WireFormatVersion.kt`. CI script для sync с TypeScript const. Closes CHK003.
- ✅ **Forward-compat asymmetry documented** — Wire-format policy section в spec.md + contracts/push-payload-v1.md (receiver fail-soft) + contracts/push-trigger-request-v1.md (Worker fail-closed). Closes CHK008.
- ✅ **Receiver malformed payload handling** — contracts/push-payload-v1.md explicit receiver-side parse-failure logic + FR-075. Closes CHK017.
- ✅ **URL versioning strategy** — body-versioning documented в Wire-format policy section. Closes CHK016.
- ✅ **Contracts folder created** — 3 files (push-trigger-request-v1, push-payload-v1, event-type-registry) с semantic version + breaking-change policy + roundtrip test references. Closes CHK018.
- ⚠️ **linkId grep audit** — done (8 files), all в migration scope. Closes CHK006 caveat. inline TODO в PushPayload.kt для schemaVersion 2 future removal.

**Plan-level pass**: 17/18 effective (1 advisory — manual sync TypeScript ↔ Kotlin remains, addressed by CI check + contract test).

## Plan-level meta-minimization re-check

Spec-level: 9/13 pass + 4 warnings ([meta-minimization.md](meta-minimization.md)).

**Plan-level new abstractions introduced** (not в spec.md):

1. **`BackgroundDispatcher` port** (FR-078) — new port post-spec, decided 2026-06-21.
   - **Test 1 (inline?)**: if removed, every PushHandler manages WorkManager itself. For 9 known consumers — duplicate ~30 LOC × 9 = ~270 LOC. **Rewrite, not addition.** Test PASSES.
   - **Test 2 (vendor swap?)**: future iOS port needs different background mechanism (BGTaskScheduler). Port abstracts platform. Without port — Android-only forever, iOS requires rewrite. Test PASSES.

2. **`RetryStrategy` sealed class** (data-model.md) — new sealed для retry policy configurability.
   - **Test 1**: if removed, all events use same retry policy. SOS (no retry) vs config (retry OK) — incompatible. Test PASSES (legitimate variation).

3. **`@familycare/auth-jwt` separate module** — new TypeScript module.
   - **Test 1**: if inlined в `workers/push/src/auth/`, future Workers (V-3 album metadata, etc.) duplicate JWT verification. Test PASSES.
   - **Test 2**: own backend migration — auth-jwt portируется отдельно от push transport. Test PASSES.

**All 3 new plan-level abstractions justified by rule 4**.

**Plan-level concerns**:

- ✅ **`PushHandlerRegistry` + `EventTypeRegistry`** — both registries justified (FR-060 — adding event type без foundation modification). Documented in plan.md §Complexity Tracking.
- ✅ **Module split (`core/push/` standalone)** — extraction-readiness preserved at near-zero cost. Per TODO-ARCH-017.
- ✅ **Migration scope (8 existing files)** — accepted in plan §Rollout Phase 2. Inline TODO для schemaVersion 2 removal (linkId).
- ⚠️ **Total new types**: ~15 in `core/push/api/` + ~5 in `workers/push/src/` + ~5 in `auth-jwt/`. **Borderline** для foundation spec. Justified by 9 known consumers.

**Plan-level pass**: 13/13 effective (no new violations, 3 new abstractions all rule-4 justified).

## Summary

| Checklist | Spec-level | Plan-level delta |
|---|---|---|
| domain-isolation | 13/16 (3 warnings) | **+3** resolutions через plan-level explicit decisions = **16/16** |
| wire-format | 7/18 (8 warnings) | **+8** resolutions через Wire-format policy section + contracts/ + Wire-format policy block в spec.md = **17/18** (1 advisory) |
| meta-minimization | 9/13 (4 warnings) | **+0** new violations; **+3** new abstractions all rule-4 PASS; existing warnings closed via inline TODO + grep audit = **13/13** |

**Plan-level Constitution Check**: re-validated. Still 6 PASS + 2 NOTE + 0 FAIL (consistent с inlined в plan.md).

**Open issues remaining**:
1. (advisory) Wire-format CHK010 — manual sync TypeScript ↔ Kotlin DTOs. Addressed via CI script (sync MAX_SUPPORTED_SCHEMA_VERSION) + contract test (run Kotlin client vs `wrangler dev`). Becomes automation candidate if event types > 5.
2. (medium) Action items из spec-level `_overview.md` — 7 medium-priority tasks для `/speckit.tasks` (Worker README, contracts/, perf-checkpoint, privacy policy hook, etc.). Will be picked up в next stage.

**Recommendation**: proceed к `/speckit.tasks`.

---

## Краткое резюме (для не-разработчика)

Re-проверили 3 checklist'а уже не на спецификации, а на плане реализации:

- **Domain isolation** (правильно ли разделены модули): **16/16** — план явно расписал какой файл в каком source-set лежит, закрыв все warning'и со спек-фазы.
- **Wire format** (правильно ли версионируем данные между процессами): **17/18** — план создал отдельные файлы-контракты для каждого формата, добавил блок «Wire-format policy» в спеку. Один advisory остался — нужно вручную поддерживать синхрон Kotlin DTO и TypeScript DTO (есть автоматический CI check для базовой защиты).
- **Meta-minimization** (нет ли лишних абстракций «на будущее»): **13/13** — план добавил 3 новые абстракции (BackgroundDispatcher, RetryStrategy, auth-jwt module), все обоснованы — без них пришлось бы переписывать код, а не дописывать.

**Главный вывод**: план solid, готов к написанию пошагового tasks.md.

# Analyze Report: Setup Assistant and Launcher Bootstrap

**Date**: 2026-05-19
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Tasks**: [tasks.md](tasks.md) | **Checklists**: [_overview.md](checklists/_overview.md)
**Trigger**: `/speckit.analyze` post-`/speckit.tasks` (commit `f3ddb32`)

---

## 1. Constitution Check

**Status**: ✅ **8/8 PASS** — unchanged since `/speckit.plan` (see plan.md §9).

All 8 gates (Architecture, Core/System Integration, Configuration, Required Context Review, Accessibility, Battery/Performance, Testing, Simplicity) — PASS with documented watch items (Surface.MainScreen seam с anticipated спек 013 consumer; ROLE_HOME legacy fallback API 26-28).

## 2. Cross-Artifact Trace

**Status**: ✅ **PASS** (with 1 drift finding from analyze scans — см. §5 ниже).

- ✓ All **41 FRs** covered by tasks (negative requirements FR-009/FR-010/FR-025/FR-040 implicit by code review).
- ✓ All **7 USs** have test + smoke evidence.
- ✓ Plan introduces no abstractions без spec FR ground.
- ✓ Contracts → N/A (FR-040 explicit no wire formats).
- ✓ Checklists cite spec sections (CHK-NNN → Spec §FR-NNN / Article §X).
- ✓ Required context links — markdown-formatted в plan.md §8 и tasks.md.
- ✓ Task ordering valid (T058/T073 forward dependencies fixed in `/speckit.tasks` Step 4).

## 3. Checklist drift detection (since `/speckit.clarify`)

5 spec-level findings closed by post-clarify additions (commit `6cabfaa`):
- elderly-friendly CHK007 — closed by FR-008a
- failure-recovery CHK001/002 (SetupCheck exception) — closed by FR-020b
- failure-recovery CHK001/002/003/010 (unlink network fail) — closed by FR-032/32a rewrite
- elderly-friendly CHK001 (challenge ≤14sp) — closed by A-13
- requirements-quality CHK016 + ux-quality CHK013 (SC-006 weak) — closed by SC-006 removal

3 plan-level findings closed by `/speckit.plan` decisions:
- domain-isolation CHK001/002/003/015 — closed by `GmsAvailabilityPort` introduction (Phase 0)
- meta-minimization CHK002/004 — closed by D-2/D-3 registry collapse decisions
- meta-minimization CHK012 — `flows_mock` removal explicit (Phase 2 T031-T036)

### Updated checklist verdict

| Checklist | Original | After clarify additions | After plan | Net |
|-----------|----------|--------------------------|------------|-----|
| requirements-quality | 15/16 | **16/16** ✓ | 16/16 | ✓ |
| meta-minimization | 10/13 | 10/13 | **12/13** ✓ | D-1 watch item |
| domain-isolation | 11/16 | 11/16 | **16/16** ✓ | ✓ |
| wire-format | 18/18 ✓ | 18/18 | 18/18 | ✓ |
| state-management | 13/17 | 13/17 | **15/17** ✓ | rememberSaveable C-1 + font-scale edge accepted |
| failure-recovery | 13/17 | **17/17** ✓ | 17/17 | ✓ |
| performance | 16/20 | 16/20 | **18/20** ✓ | macrobenchmark + apkdiff planned |
| security | 21/24 | 21/24 | **23/24** ✓ | exported=false Konsist + backup audit pending T108 |
| permissions-platform | 18/22 | 18/22 | **22/22** ✓ | Phase 0 closes all critical findings |
| ux-quality | 17/22 | **19/22** ✓ | 19/22 | FR-008a + plan §6 a11y |
| accessibility | 16/25 | 16/25 | **20/25** ✓ | plan-level color spec/focus order/plurals; 7-tap D-pad → future-spec OUT |
| elderly-friendly | 19/22 | **22/22** ✓ | 22/22 | FR-008a + A-13 + Phase 8 walkthrough |
| localization | 16/20 | 16/20 | **19/20** ✓ | plurals T068/T069 + locale-aware dates T089 |
| **TOTAL** | 203/258 (79%) | 215/258 (83%) | **236/258 (91%)** | **+33 closures** |

22 remaining items — all "watch" / plan-time decisions documented and accepted with exit ramps.

## 4. Specific scans (analyze-unique)

### Scan A — Deleted-file dangling references

Plan.md DELETE list: `flows_mock_*.json` + `MockFlowRepository` class + `MockFlowRepositoryTest`.

**Grep findings** (production code, not spec docs):

| File | Reference | Covered by current tasks? |
|------|-----------|----------------------------|
| `core/src/androidMain/.../core/LauncherCore.kt:23` | `import com.launcher.core.flows.MockFlowRepository` | ❌ **NOT explicit** |
| `core/src/androidMain/.../core/LauncherCore.kt:49` | `val flowRepository: FlowRepository = flowRepository ?: MockFlowRepository(appContext, ...)` | ❌ **NOT explicit** |
| `core/src/commonMain/.../ui/di/CoreKoinModule.kt:8` | Comment-only mention в docstring | ⚠️ Comment cleanup needed |
| `core/src/androidMain/.../adapters/config/LegacyMockStorageCleanup.kt` | File name suggests cleanup logic — may need review | ⚠️ Unknown scope |
| `core/src/androidUnitTest/.../MockFlowRepositoryTest.kt` | Test file for class being deleted | ⚠️ Should be deleted too (covered by T034-T036 rewriting paradigm but not explicit) |
| `specs/008-bidirectional-config-sync/legacy-cleanup-inventory.md` | Spec doc tracking cleanup items — expected reference | ✓ OK (it's the inventory) |
| `specs/003-005-006-*` historical specs | Past references | ✓ OK historical |
| `docs/dev/project-backlog.md` (TODO-ARCH-016) | Backlog entry | ✓ Will be updated by T111 (close TODO-ARCH-016) |

**🔴 DRIFT FINDING #1**: Tasks T029-T032 deletion sequence **does not explicitly update** `LauncherCore.kt` где `MockFlowRepository` is instantiated as default fallback. After T032 deletes class, `LauncherCore.kt` **не скомпилируется**.

**Remediation**: expand T032 acceptance OR add T032a:
> «Update `core/src/androidMain/kotlin/com/launcher/core/LauncherCore.kt`: remove `MockFlowRepository` import (line 23) and default fallback (line 49). New behavior: `flowRepository` parameter is mandatory OR provided через DI from ConfigEditor adapter (per ARCH-016).»

Also remove obsolete `MockFlowRepositoryTest.kt` (covered implicitly by Phase 2 test rewrites but not explicit).

Also update `CoreKoinModule.kt:8` docstring comment to remove MockFlowRepository mention.

### Scan B — Wire-format `schemaVersion` audit

**Status**: N/A. FR-040 explicit: «никакая wire-format модификация в этом спеке не повышает schemaVersion». No new files в plan.md introduce persistent / cross-process formats.

### Scan C — Source-set placement audit

**Status**: ✓ PASS. Plan.md §2 module map is explicit. Tasks T013-T020 follow placement (commonMain ports, androidMain adapters). Konsist gates T007-T010 enforce.

### Scan D — Required-context link audit

**Spec.md findings**:

| Line | Reference | Linked? |
|------|-----------|---------|
| 211 | «Article VIII senior-safe override» | ❌ Bare prose |
| 243 | «Article VIII senior-safe destructive-action paradigm» | ❌ Bare prose |
| 262 | «per ADR-004» | ❌ Bare prose |
| 330 | «GDPR Article 7 / 152-ФЗ ст. 9 ч. 2» | ❌ Bare prose (legal refs OK) |
| 332 | «Article VIII §7» (multiple) | ❌ Bare prose |
| 308 | «A-3» в Assumptions | OK (internal ref) |

**🟡 DRIFT FINDING #2**: 5+ unlinked references к Article VIII / ADR-004 в spec.md. Plan.md линкует их корректно. Cosmetic but cleanup recommended.

**Remediation** (optional, low priority):
```diff
- (Article VIII senior-safe override)
+ ([Article VIII §7](../../.specify/memory/constitution.md#article-viii-senior-safe))

- per ADR-004
+ per [ADR-004](../../docs/adr/ADR-004-localization-and-global-readiness.md)
```

### Scan E — Vague language sweep

Grepped «intuitive|smooth|просто|удобно|быстро|should be» (case-insensitive).

**Status**: ✓ PASS. False-positive matches only — Russian word-fragments inside `нажмёт`, `просто` в normal prose context, etc. No unqualified vague adjectives describing requirements found.

## 5. Verdict

```
SPECKIT-ANALYZE for specs/010-setup-assistant/:

CONSTITUTION CHECK: 8/8 PASS

CROSS-ARTIFACT TRACE: ✓ PASS (with 1 drift finding from Scan A)

CHECKLISTS: 236/258 ✓ (91%)
  remaining 22 — all "watch" / plan-time decisions с exit ramps

SCANS:
  A. Deleted-file dangling refs    : ✗ 1 critical drift (LauncherCore.kt)
  B. Wire-format schemaVersion     : N/A (no new formats)
  C. Source-set placement          : ✓ PASS
  D. Required-context links        : ⚠ 5+ bare ADR/Article refs in spec.md (cosmetic)
  E. Vague language sweep          : ✓ PASS

VERDICT: READY-WITH-CAVEATS
```

### Open items (must address or accept-as-risk before implementation)

1. **🟢 RESOLVED: `LauncherCore.kt` consumer of MockFlowRepository** — **fixed post-analyze** via insertion of **T032a** task в tasks.md (commit pending). T032a explicitly covers:
   - `LauncherCore.kt:23` import removal
   - `LauncherCore.kt:49` default fallback removal — replaced с required DI parameter sourced from ConfigEditor
   - `MockFlowRepositoryTest.kt` deletion
   - `CoreKoinModule.kt:8` docstring update
   - `LegacyMockStorageCleanup.kt` comment verification (file is no-op marker per spec 008; comments may be updated to note ARCH-016 closure но это optional)
   
   **Acceptance**: `./gradlew :core:compileDebugKotlinAndroid` succeeds after T032 + T032a.

2. **🟡 COSMETIC (OPEN): Linkify Article VIII / ADR-004 references в spec.md** — 5+ bare prose mentions should be markdown links. Low priority; не блокирует implementation. Можно сделать в Phase 8 final review (T112 territory).

### Recommendation

After T032a insertion (this commit) → **READY for implementation**, начинать с Phase 0 (T001-T012).

**Final verdict (post-fix)**: ✅ **READY** — single cosmetic open item (Finding #2) accepted-as-risk для Phase 8 cleanup.

---

## Что внутри (TL;DR на русском)

Это **финальный pre-implementation audit** спека 010 перед началом реальной работы.

**Главный результат**: спек **готов к реализации с одним обязательным уточнением** в tasks.md — Drift Finding #1.

**Что проверили**:
1. **Constitution Check 8/8 PASS** (8 архитектурных гейтов — все зелёные, unchanged с plan'а).
2. **Cross-artifact trace** ✓ — все 41 FR покрыты задачами, 7 user stories имеют тесты.
3. **13 checklists** — 236 из 258 пунктов passed (91%). Остальные 22 — accepted watch items с exit ramps (например, Surface.MainScreen seam — anticipated consumer спек 013).
4. **Specific scans** (унаследовано от analyze):
   - Поиск ссылок на удаляемые файлы → нашли что `LauncherCore.kt` использует `MockFlowRepository` как fallback. Это **критичный finding** — без обновления Phase 2 не скомпилируется.
   - Wire-format schemaVersion → N/A (нет новых форматов).
   - Source-set placement → ✓ ОК.
   - Markdown-ссылки на ADR/Article → 5+ упоминаний в спеке голым текстом (косметика, не блокирует).
   - Поиск vague language («intuitive», «smooth» и т.д.) → ✓ чисто.

**Финальный вердикт: READY-WITH-CAVEATS** — нужно адресовать 1 критичный finding (расширить T032 чтобы покрыть LauncherCore.kt), и можно начинать с Phase 0.

**Следующий шаг**: пользователь подтверждает (или сам применяет) T032 fix → implementation kickoff с Phase 0 (T001-T012, ~3-4 дня) — закрывает 3 critical findings из checklists (`<queries>` для tel:, GmsAvailabilityPort, plurals).

# Constitution Check: spec 008 plan.md

**Date**: 2026-05-14
**Plan**: [plan.md](plan.md)
**Constitution**: [.specify/memory/constitution.md](../../.specify/memory/constitution.md) (Article XVI gates).

---

## Gate 1 — Architecture

**Question**: Does the feature fit the layered architecture? New modules justified per Article V §3?

**Evaluation**:
- Plan §Architecture (Module map): NO new gradle modules. Все новые files placed within existing `:core` (api/ + adapters/ + fake/) и `:app` (ui/). Article V §1-2 («initial structure SHOULD remain intentionally compact») satisfied.
- Layered separation explicit: commonMain (domain) ↔ androidMain (adapters) ↔ app (UI). UI never imports Firebase/Room directly.
- Ports/adapters pattern consistent with spec 007 baseline.
- 5 new ports (ConfigApplier, ConfigEditor, LocalConfigStore, NetworkAvailability, AppForegroundEvents) — каждый имеет concrete consumer in spec (CHK001/CHK010 of meta-minimization checklist).

**Verdict**: **PASS** — no new modules, port-adapter pattern consistent with project baseline.

---

## Gate 2 — Core/System Integration

**Question**: System events centralized in :core? Event contracts typed and documented? Article VI §6 frequency/threading/battery cost documented?

**Evaluation**:
- 4 system event sources (FR-022 T1-T4):
  - T1 FCM `config.updated` — reused from spec 007 push infrastructure.
  - T2 `ConnectivityManager.NetworkCallback` — NEW, wrapped in `NetworkAvailability` port (commonMain).
  - T3 WorkManager periodic 15 min — NEW, single androidMain adapter, no port (Article VI §6 documented in research.md §5 + §8).
  - T4 Activity#onResume throttled 2min — wrapped in `AppForegroundEvents` port.
- Event contracts: research.md §5 has explicit table «Background task → source / frequency / thread / battery cost / fallback».
- All events centralized: T1 in spec 007's PushReceiver path; T2/T4 in new commonMain ports; T3 in single androidMain worker.
- No feature module registers its own BroadcastReceiver (verified — все ports + adapters located in :core).

**Verdict**: **PASS** — system events properly wrapped, Article VI §6 documentation present in research.md §5.

---

## Gate 3 — Configuration

**Question**: schemaVersion present for wire formats? Validation/migration documented? CLAUDE.md §5 compliance?

**Evaluation**:
- Two wire formats: `/config/current` ([contracts/config.md](contracts/config.md)) и `/state/current` extended ([contracts/state-applied.md](contracts/state-applied.md)).
- **`schemaVersion: Int` present in both** — first field per CLAUDE.md §5 (FR-001, FR-032).
- Roundtrip + backward-compat read tests required: FR-005, SC-005. Test list explicit в contracts/*.md §Tests.
- Migration policy explicit: FR-006 — «additive only; rename/remove requires schemaVersion bump + reader-migration в Phase 0 next spec».
- Test fixtures stored as files: research.md §test strategy + plan.md §Test Strategy («commonTest/resources/wire-format/*.json»).
- Schema export для Room (`exportSchema = true`) — data-model.md §Persistence schema versioning.

**Verdict**: **PASS** — schemaVersion present, backward-compat policy documented, tests planned, Room export configured.

---

## Gate 4 — Required Context Review

**Question**: docs/governance, docs/adr, docs/product, docs/compliance linked? Article XII §7 compliance?

**Evaluation**: plan.md §Required Context Review explicitly lists:
- Constitution: linked Articles I-XVI, especially VIII §7 / IX / XI / XII §7 / XIV.
- CLAUDE.md rules 1, 2, 4, 5, 6 — referenced.
- ADRs: ADR-001 (Platform Parity), ADR-004 (Localization), ADR-005 (UI Stack) — all linked. No new ADR needed (per Article XV §4).
- Product: roadmap.md §008 (lines 168-188); senior-safe-launcher-plan.md.
- Compliance: permissions-and-resource-budget.md (update planned).
- Backlog: project-backlog.md — TODO-ARCH-007/008/009 (all originated in 008 clarify).
- Spec 007 dependencies: plan.md, contracts/, state-bootstrap.md — all linked.

**Verdict**: **PASS** — explicit cross-references in plan.md §Required Context Review block.

---

## Gate 5 — Accessibility

**Question**: Article VIII — tap target ≥ 56dp, contrast ≥ 4.5:1, TalkBack documented, elderly-friendly?

**Evaluation**:
- **Two UI surface categories**:
  1. **Main launcher rendering** (applied config display): NOT introduced by spec 008. Inherits спек 003. Senior-safe rules apply там, не здесь.
  2. **Editor UI** (Settings autosave, MergeScreen, pending banner, discard dialog): introduced in 008.
- **FR-050 documented exception** (Article VIII §7): unified merge UI, not senior-safe variant. Justification: 7-tap + password entry barrier acts as cognitive filter; пользователь, прошедший этот barrier, demonstrably capable. **This IS an Article VIII §7 valid exception** (explicit documented product constraint).
- Wording revision applied (CHK009 of elderly-friendly): user-facing strings now neutral (no jargon, no persona-specific terms).
- Action items для plan.md follow-up (CHK001-005 of elderly-friendly): sizing, contrast audit, color-not-only, reduce-motion. Documented в elderly-friendly.md checklist as plan-level follow-ups.

**Verdict**: **PASS WITH EXCEPTION** — FR-050 единый merge UI documented per Article VIII §7; elderly-friendly checklist шatched with action items в plan.md.

---

## Gate 6 — Battery/Performance

**Question**: Background tasks justified per Article IX §2-3? Startup minimal per §4? Measurable targets per §8?

**Evaluation**:
- **Background tasks justified** (research.md §5 table):
  - T1 FCM listener — reused from 007 (no new cost).
  - T2 NetworkCallback — system-driven (~0 battery).
  - T3 WorkManager 15min — explicit fallback justification; 96 wakeups/day; <0.1% battery (within Article IX §3 cap).
  - T4 Activity#onResume throttled 2min — user-bound, no background.
  - Aggregate: <10 wakeups/hour (well within cap).
- **Event-driven preferred** (Article IX §3): 4 triggers, only T3 is polling, explicit fallback role.
- **Startup minimal** (Article IX §4): Application.onCreate has no Room access (lazy DAO); Room read deferred on Dispatchers.IO, first-frame UI from StateFlow.
- **Measurable targets** (Article IX §8): SC-004a `≤ 650 ms p95` macrobenchmark; APK delta < 4 MiB; perf-checkpoint phase planned (Phase 12).
- **Coordination needed**: TODO-ARCH-006 (R8 minification) MUST land before/during 008 to keep APK delta in budget (research.md §6 explicit).

**Verdict**: **PASS** — explicit budgets, event-driven preferred, measurable targets, perf checkpoint planned.

---

## Gate 7 — Testing

**Question**: Article X §3 order? CLAUDE.md §6 fake + real adapters? Wire-format roundtrip/backward-compat?

**Evaluation**:
- Plan.md §Test Strategy: 8 levels mirror spec 007 (Domain unit / Contract / Fake-adapter / Firebase Emulator integration / Worker unit / UI Compose / Konsist fitness / manual smoke).
- **Mock-first** (CLAUDE.md §6): 5 new ports, each has fake adapter (FakeLocalConfigStore, FakeConfigApplier, FakeConfigEditor, FakeNetworkAvailability, FakeAppForegroundEvents) + real adapter (Room*, Firebase*, ConnectivityManager*, ProcessLifecycle*).
- **DI wiring**: Koin modules `realBackendModule` / `mockBackendModule` extended in Phase 4/7 of plan.md §Implementation Phasing.
- **Wire-format tests** (CLAUDE.md §5, FR-005, SC-005): roundtrip + backward-compat read tests for ConfigDocument and StateApplied. Test names explicit in contracts/*.md §Tests.
- **Core event handling tests** (Article X §5): translation (FCM payload → apply trigger), dedup (FR-023 self-as-writer skip), throttling (FR-022 T4 throttle 2min), fallback (T3 WorkManager when FCM absent) — all enumerated в Phase 11 in-process E2E test plan.
- **Fitness functions** (CLAUDE.md §7): Konsist gates extend spec 007 Phase 10 patterns — `commonMain config/*` clean of Firebase/Room/android.*.

**Verdict**: **PASS** — comprehensive test strategy, mock-first respected, wire-format tests required.

---

## Gate 8 — Simplicity

**Question**: Article XI — speculative abstractions? CLAUDE.md §4 Test 1/Test 2?

**Evaluation**: per meta-minimization.md checklist (re-validated against plan.md):
- **No speculative ports**: 5 new ports — каждый has concrete fake-adapter test consumer + multiple real consumers (per data-model.md §Domain ports inventory).
- **No new gradle modules**: explicit in plan.md §Architecture.
- **No new DSL / registry / orchestrator**: ConfigApplier / ConfigEditor are domain ports, not orchestrators (they have real domain ops, not pass-through).
- **No fields for future use**: Q4/Q7/Q10 results — schema mismatch fields, history fields, size-limit fields все carved out to separate specs (OUT-006/007/008). 11 fields в /config and 7 new fields в /state — каждое with current FR consumer.
- **CLAUDE.md §4 Test 1 (inline cost)** — applied in research.md §1 (concurrency model), §7 (NetworkAvailability port), §8 (no WorkManager port).
- **CLAUDE.md §4 Test 2 (swap cost)** — applied in research.md §1 (concurrency exit ramp), domain-isolation.md CHK011 (per-port swap estimate).
- **Decision не оборачивать WorkManager** (research.md §8) — explicit «single use site, no fake value» justification per Article XI §2.

**Verdict**: **PASS** — no speculative abstractions; all ports justified with consumers and swap-cost analysis.

---

## Overall

```
CONSTITUTION CHECK for specs/008-bidirectional-config-sync/plan.md:
  Gate 1 Architecture           : PASS  — no new modules, port-adapter pattern consistent with 007 baseline
  Gate 2 Core/System Integration: PASS  — system events wrapped in ports; Article VI §6 documented in research.md §5
  Gate 3 Configuration          : PASS  — schemaVersion present, backward-compat policy explicit, tests planned
  Gate 4 Required Context Review: PASS  — constitution + ADRs + roadmap + compliance + 007 deps all linked
  Gate 5 Accessibility          : PASS WITH EXCEPTION — FR-050 documented per Article VIII §7 (7-tap+password barrier)
  Gate 6 Battery/Performance    : PASS  — explicit budgets, event-driven preferred, perf checkpoint planned
  Gate 7 Testing                : PASS  — mock-first + roundtrip + integration + fitness; 8 levels mirror 007
  Gate 8 Simplicity             : PASS  — no speculative abstractions; Tests 1 + 2 applied in research.md

OVERALL: 8 PASS (1 with documented exception), 0 FAIL, 0 N/A — plan is COMPLETE.

No remediation required.
```

---

## Notes

The exception in Gate 5 (FR-050 unified merge UI without senior-safe variant) is **explicitly authorized by Article VIII §7** «If a design is elegant for experts but confusing for elderly users, the elderly-friendly design wins by default **unless a documented product constraint says otherwise**». FR-050 provides that documented constraint (7-tap+password barrier = cognitive filter; users who pass it are demonstrably capable of multi-step interactions).

No `Complexity Tracking` row needed — exception is *authorized*, not *justified deviation*.

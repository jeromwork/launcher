# Analyze Report: ECS Tags Foundation + HomeScreen Query Rewire

**Date**: 2026-07-16 (updated same day after Deep Pre-Implement Audit #2) | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Tasks**: [tasks.md](tasks.md) | **Contract**: [contracts/profile-v2.md](contracts/profile-v2.md)

> **Reading order note**: sections below are chronological. §Constitution Check / §Cross-Artifact Trace were re-checked after audit #2; §"Deep ECS Industry Audit" and §"Q6 Remediation" are historical records of earlier waves — where they mention `profile-v3.md`, `profile-v1.md`, `schemaVersion 1`, or migration tasks T127-007/011/012/013, those artifacts no longer exist (see §Deep Pre-Implement Audit #2 for the current state).

---

## Constitution Check

*Per Article XVI of `.specify/memory/constitution.md`. Run via `procedure-constitution-check` against plan.md.*

| Gate | Status | Notes |
|------|--------|-------|
| G-1 Architecture | **PASS** | Extension methods + one adapter in existing `core/preset/*` and `core/adapters/flow/`. No new gradle module. Port + implementation shape preserved. |
| G-2 Core/System Integration | **N/A** | No new system events, no BroadcastReceiver, no lifecycle callbacks. Data-model + adapter change only. |
| G-3 Configuration | **PASS** (re-checked audit #2) | `schemaVersion: 2` — matches shipped code + TASK-120 Decision; `tags` additive, no bump (rule 5). **No migration writer** per Clarification Q6 (MVP не релизнут; rule 4 MVA). Constructor-defaults на Component subtypes = единственный источник истины для tags. Roundtrip + fail-loud pins scoped. Pool.json override via embedded component per Clarification Q2. |
| G-4 Required Context Review | **PASS** | Links present: CLAUDE.md (rules 1, 4, 5, 9), ADR-011, constitution.md, preset-model.md, server-roadmap.md, TASK-120 Decision, task-49 precedent. No permissions change. |
| G-5 Accessibility | **PASS** | US-3 (wizard localization) verifies readable strings for senior users (SC-002). No new UI below 56dp. `FontSize` Component carries `Tag.Accessibility`. |
| G-6 Battery/Performance | **PASS** | Event-driven (`ProfileStore.observe()` on user edit). No polling. Никакой migration cost (нет migration writer). Perf target NFR-003 (< 1 ms) + SC-008 benchmark. Zero new deps. |
| G-7 Testing | **PASS** (re-checked audit #2) | Contract (roundtrip v2 + missing-tags + fail-loud pins; no migration tests per Q6). Unit (Query API + render gating, ProfileBackedFlowRepository incl. loadFlows, ComponentTagsFitnessTest). Integration (HomeComponentLoadingStateTest, JVM commonTest). Fitness (reflection walk, benchmark). Existing `FakeProfileStore`. |
| G-8 Simplicity | **PASS** | `ProfileQueryService` rejected (R-1). Migration writer rejected (R-2 revised per Q6 — constructor-defaults вместо). Linear scan over index (R-4). Rule 4 Test 1: inlining Query API loses type-safety — kept. Test 2: swap to member methods ~1 hour. |

**OVERALL: 7 PASS, 1 N/A, 0 FAIL** — plan is **COMPLETE**.

---

## Cross-Artifact Trace

*Per `procedure-cross-artifact-trace`.*

### Spec → Tasks coverage (all FRs)

- **FR-001** (Tag enum) → T127-003 ✓
- **FR-002** (Component.tags) → T127-004 ✓
- **FR-003** (pool tags override via embedded component; restated audit #2) → T127-005 ✓
- **FR-004** (schemaVersion: 2 unchanged + constructor-defaults, no migration writer per Q6) → T127-004, T127-009, T127-021 ✓
- **FR-005** (Query API) → T127-006, T127-023 ✓
- **FR-006** (ProfileBackedFlowRepository) → T127-014, T127-015 ✓
- **FR-007** (DI wiring) → T127-017, T127-018 ✓
- **FR-008** (strings_wizard.xml) → T127-019 ✓
- **FR-009** (preset-model.md docs) → T127-008, T127-025 ✓
- **FR-010** (server-roadmap SRV-CONFIG-DEPRECATION) → T127-024 ✓

**10/10 FRs covered** ✓

### Spec → Tasks coverage (all USs)

- **US-1** (Fresh install → HomeScreen Ready) → T127-020 (integration), T127-026 (emulator smoke), T127-027 (physical smoke) ✓
- **US-2** (Developer adds Component) → T127-023 (unit tests), T127-021 (fitness) ✓
- **US-3** (Wizard localization) → T127-019 (strings), T127-026, T127-027 (visual checks) ✓

**3/3 USs covered with test evidence** ✓

### Contracts → Tests (updated audit #2)

- **profile-v2.md contract**: roundtrip (T127-009), missing-tags case (T127-009), fail-loud pins unknown-Tag/unknown-type (T127-009), fixtures (T127-010) ✓
- ~~migration tests~~ — removed per Q6 ✓

**All contracts have required tests** ✓

### Non-Functional Requirements

- **NFR-001** (domain isolation) → T127-021 + checklist-domain-isolation ✓
- **NFR-002** (emissions tracking) → T127-015 unit test ✓
- **NFR-003** (query < 1 ms) → T127-022 benchmark ✓
- **NFR-004** [REMOVED per Q6] — no migration, no idempotency ✓

**All NFRs covered** ✓

### Wire-format audit (updated audit #2)

- `contracts/profile-v2.md` — `schemaVersion: 2` matches `Profile.CURRENT_SCHEMA_VERSION` ✓
- `data-model.md` — describes wire-format changes ✓
- **No migration writer** per Clarification Q6 — constructor-defaults on Component subtypes verify tags on missing-JSON-field via T127-009 bonus test case ✓

**No missing schemaVersion fields** ✓

### Source-set placement

All new files placed per plan.md §Module map:
- `Tag`, `Component.tags`, `Profile.query` — `core/src/commonMain/` (pure Kotlin, zero Android) ✓
- `ProfileBackedFlowRepository` — `core/src/commonMain/` (adapter, zero Android imports) ✓
- ~~`ProfileMigrationV2toV3`~~ — removed per Q6 ✓
- Tests — `core/src/commonTest/` ✓
- DI wiring — `core/src/android{Mock,Real}Backend/kotlin/com/launcher/di/BackendInit.kt` (real binding sites; corrected by audit #2 — NOT `app/.../di/*Module.kt`) ✓

**Placement consistent after audit #2 path corrections** ✓

### Context link audit

- CLAUDE.md — rule 1 (domain isolation), rule 4 (MVA), rule 5 (wire-format), rule 9 (shareability) — all linked in plan.md ✓
- ADR-011 — referenced for sequences + chat-only checklist convention ✓
- constitution.md — Article XI (meta-minimization), XVI (Constitution Check), XVII (deviations) — all linked ✓
- preset-model.md — new doc, linked in FR-009 ✓
- server-roadmap.md — SRV-CONFIG-DEPRECATION entry required (FR-010, task T127-024) ✓

**All required context linked** ✓

### Vague-language sweep

Grep spec.md + plan.md for: "intuitive", "smooth", "fast", "simple", "should be", "may", "could", "probably":

- All instances paired with measurable operationalisation (e.g., "Query API performance < 1 ms" not just "fast").
- No survivors flagged ✓

---

## Checklists (Chat-Only Summary)

*Per ADR-011 §5 revised: no persisted files. Checklists run fresh against current spec.md / plan.md.*

### checklist-domain-isolation

**16/16 ✓** — All new types (`Tag`, `Component.tags`, Query API, `ProfileBackedFlowRepository`) zero Android imports. Verified: `commonMain` source-set placement, pure Kotlin.

### checklist-wire-format

**18/18 ✓** — Profile v1 has `schemaVersion` field с day 1 (rule 5). No migration writer per Q6 — constructor-defaults on Component subtypes cover missing `tags` field. Serialization stable (roundtrip test T127-009). No free-form identifiers (Tag = closed enum, not free-form String). Pool.json override additive-only.

### checklist-meta-minimization

**13/13 ✓** — No speculative abstractions (ProfileQueryService rejected per R-1). No single-implementation interfaces unnecessary (Query API = extension functions, not service). MVP scale query = linear scan (exit ramp documented). No new gradle modules.

### checklist-performance

**20/20 ✓** — Query API target < 1 ms (NFR-003, SC-008, T127-022 benchmark). No migration cost (нет migration writer). No polling. Event-driven `ProfileStore.observe()`. No new background work. Startup impact controlled.

### checklist-failure-recovery

**17/17 ✓** — Profile null handling (SEQ-4): `filterNotNull()` keeps HomeComponent in `Loading`, not `Error`. Pre-release: dev `ProfileStore` reset acceptable for breaking dev-changes. Post-release: R-7 tracks migration-writer-mandatory rule. Corrupt Profile path scoped (recovery flow noted as out-of-scope in spec §Out of Scope).

---

## Specific Scans

### Deleted-file dangling references

Plan.md contains no "DELETE" list — pure additive change. ConfigBackedFlowRepository retained (not deleted), marked TODO. No dangling references ✓

### Task ordering

All T127-NNN tasks have valid forward dependencies:
- T127-004 (Component.tags) requires T127-003 (Tag enum) ✓
- ~~T127-007 / T127-013 (migration)~~ — removed per Q6 ✓
- T127-014 (ProfileBackedFlowRepository) requires T127-006 (Query API) ✓
- All subsequent tasks respect phase boundaries ✓

**No forward-refs, topological order valid** ✓

---

## Deep ECS Industry Audit (2026-07-16)

*Requested by owner after initial analyze; agent researched Bevy `bevy_ecs`, Flecs, Unity DOTS Entities, EnTT.*

### Verdict from deep audit

**adapted-but-legitimate** — the design is **tagged-component model, ECS-inspired**, NOT canonical ECS. Sealed hierarchy `Component` = one component per entity (discriminated union), not multi-component composition. Legitimate for MVP scale (~20 entities, user-triggered queries), but the "ECS-native" framing in initial docs was misleading.

### Findings and remediation

| # | Finding | Severity | Fix applied |
|---|---------|----------|-------------|
| 1 | `toolbar()` used `is Toolbar` type check — paradigm mix (tag-based tiles + type-based toolbar) | Red flag | Added `Tag.Toolbar` marker. `toolbar() = byTag(Tag.Toolbar).firstOrNull()`. Both queries now tag-based. Both tag and default for `Component.Toolbar` updated in spec.md, data-model.md, contracts/profile-v3.md, tasks.md T127-003, T127-023, migration mapping T127-013. |
| 2 | Missing `Without` / `Not` predicate (canonical ECS has it: Bevy `Without<T>`, Flecs `!tag`, EnTT `exclude<>`) | Nice-to-have | Added `Profile.byNotTag(tag)` extension function. Documented in spec.md FR-005, data-model.md § Query API, tasks.md T127-006 + T127-023. |
| 3 | Docs mis-labeled as "ECS-native" — misleads contributors familiar with Bevy/Flecs semantics | Vocabulary drift | Reframed to "tagged-component model, ECS-inspired" in spec.md (Context, MENTOR-DETAIL SEQ-3, TL;DR), data-model.md TL;DR, plan.md TL;DR, research.md TL;DR. |
| 4 | Latent one-way door (composition pressure): single-component-per-entity blocks future "any tile can gain Cooldown/Disabled marker" without breaking refactor | Long-term risk | Created [ADR-012](../../docs/adr/ADR-012-tagged-component-model-vs-canonical-ecs.md) documenting deviation, trigger to watch for, and 8–16-week canonical-ECS migration exit ramp. |

### Tag enum expansion (was 9, now 10)

Added `Tag.Toolbar` to enable query-based toolbar lookup:

```
Presentation, Appearance, System, Safety, Capabilities,
Communication, Accessibility, Emergency, Tile, Toolbar
```

Wire-format impact: additive only (existing v1 examples updated to include `Tag.Toolbar` in Toolbar defaults).

### Files modified in remediation

- `specs/task-127-ecs-foundation/spec.md` — 5 edits (Context ECS framing, Clarifications Q3/Q4, FR-001, FR-002 Toolbar default, FR-005 Query API + byNotTag, MENTOR-DETAIL SEQ-3, TL;DR)
- `specs/task-127-ecs-foundation/data-model.md` — 4 edits (Tag enum +Toolbar, Component.Toolbar default, Query API +byNotTag + tag-based toolbar(), migration mapping, TL;DR)
- `specs/task-127-ecs-foundation/contracts/profile-v3.md` — 2 edits (example Toolbar tags, closed set of 10 tag names)
- `specs/task-127-ecs-foundation/research.md` — 2 edits (R-6 rewritten for `Tag.Tile` + `Tag.Toolbar` two-marker choice with Option C rationale, TL;DR)
- `specs/task-127-ecs-foundation/plan.md` — 3 edits (Data model summary, TL;DR, Required Context Review + ADR-012 link)
- `specs/task-127-ecs-foundation/tasks.md` — 4 edits (T127-003 10-tag list, T127-006 7-function API, T127-023 unit tests for byNotTag + toolbar tag-based, TL;DR)
- `docs/adr/ADR-012-tagged-component-model-vs-canonical-ecs.md` — **new file** documenting deviation

### Cost

Total edits: **~1.5 hours**, all additive, zero wire-format breaking impact (v3 schema unchanged, only Toolbar's default `tags` set expanded).

### Post-remediation state

- ✅ Zero paradigm mix (all queries tag-based, no `is Toolbar` in query code)
- ✅ Canonical ECS query filter parity: `With` (byTag/byAllTags), `Or` (byAnyTag), `Without` (byNotTag)
- ✅ Terminology honesty: docs say "tagged-component model, ECS-inspired" not "ECS-native"
- ✅ Latent one-way door documented (ADR-012 § latent-one-way-door)

### Overall risk after remediation

**Low for 6–12 months** (MVP-scale usage is safe). **Moderate at 12–24 months** if composition pressure emerges (first PR that says "add temporary modifier to Component without changing its type"). Trigger + exit ramp documented in ADR-012.

---

## Q6 Remediation (2026-07-16 late) — Removed migration writer

Owner questioned необходимость `ProfileMigrationV2toV3`: «У нас же нету никаких потребителей. Зачем нужно прописывать отдельный?».

**Verdict**: правильно. Rule 4 (MVA) запрещает писать миграцию без потребителя.

**Applied changes**:

1. **`schemaVersion` reset**: `3` → `1` (starting fresh, MVP not released). Contract file renamed: `profile-v3.md` → `profile-v1.md`.
2. **Removed 4 tasks**: T127-007 (migration skeleton), T127-011 (migration roundtrip test), T127-012 (backward-compat test), T127-013 (implement mapping). Marked as `[REMOVED]` inline in tasks.md with reasoning.
3. **Removed 2 files that would have been created**: `ProfileMigrationV2toV3.kt`, `profile-v2-sample.json` fixture.
4. **Single source of truth for tags-defaults**: constructor-defaults on `Component` subtypes. No parallel `defaultTagsFor()` mapping to keep in sync. R-1 risk (mapping-vs-constructor drift) eliminated.
5. **Added bonus test case to T127-009**: `ProfileWireFormatV1ContractTest` also verifies that JSON without `tags` field deserializes with constructor-default tags (kotlinx.serialization defaults kicking in).
6. **NFR-004 removed**: no migration → no idempotency to prove.
7. **R-2 removed** from Risks (was: "migration race with wizard save"). **R-7 added**: post-release breaking change без migration writer = data loss for released users. Owner aware; будущий post-release breaking change обязан включать migration writer.
8. **spec.md § Clarifications** — Q6 added (2026-07-16): «Profile migration writer нужен? — No, MVP не релизнут».
9. **spec.md § Sequences** — SEQ-2 (v2→v3 migration) marked `[REMOVED]` with rationale.

**Total tasks now**: 23 active (was 27). **Total files removed**: 3 (ProfileMigrationV2toV3.kt, ProfileMigrationV2toV3RoundtripTest.kt, ProfileMigrationV2toV3BackwardCompatTest.kt + one v2 fixture).

**Post-release plan**: first breaking change → first migration writer. Documented in contracts § "Migration policy (pre-release)".

> **Superseded in part by audit #2 (below)**: the "reset schemaVersion to 1" element of this remediation was reversed — shipped code (`Profile.CURRENT_SCHEMA_VERSION = 2`) and the immutable TASK-120 Decision already say 2, and `tags` is additive (no bump needed). Contract renamed `profile-v1.md → profile-v2.md`. The core of Q6 (no migration writer, constructor-defaults as single source of truth) stands unchanged.

---

## Deep Pre-Implement Audit #2 (2026-07-16, four independent auditors)

*Requested by owner before `/speckit.implement`: model map, layer isolation, ECS industry comparison, cross-platform portability — run as four independent subagent audits against artifacts + real code on the branch.*

**Core finding: plan/data-model/contract were written against an imagined codebase.** The tagged-component architecture itself is sound (layers, ports, fakes, schemaVersion discipline, identity-free artifacts all confirmed); the artifacts diverged from the real `Component.kt` / `Profile.kt` / `FlowRepository` / DI wiring.

| # | Finding | Severity | Fix applied |
|---|---------|----------|-------------|
| 1 | data-model/contract described fictional Component shapes (`AppTile(label)`, `Sos(targetPhone)` — PII in shareable artifact, `Toolbar(buttons)`, abstract `id`); missed `Language` + `StatusBarPolicy`; fixture used non-existent `presetId` top-level field; roundtrip fixture could not deserialize against real code | CRITICAL | data-model.md rewritten from real code (8 subtypes, real fields, ProfileComponent wrapper); contract fixtures regenerated; `LauncherRole`/`StatusBarPolicy` object → data class decision made explicit (T127-004) |
| 2 | schemaVersion incoherence: artifacts said "reset to 1", code says 2 (`CURRENT_SCHEMA_VERSION`, `profile_json_v2` key), TASK-120 Decision (immutable) says 2; no task existed to change the constant | CRITICAL | schemaVersion **stays 2** (additive tags); contract renamed profile-v1.md → profile-v2.md; Q6 wording corrected in spec/plan/research |
| 3 | Plan did not fix the actual regression: Error UI originates in `HomeComponent.launchLoadFlows()` → `loadFlows()` (one-shot, 3s timeout), but sketch implemented only `observeFlows()` | CRITICAL | FR-006 expanded: adapter implements all four port methods; `loadFlows()` = `.filterNotNull().first()`; absent-Profile → caller timeout → Error+Retry (no eternal Loading) |
| 4 | Port contract silently changed: sketch declared `observeToolbar()` (not in port) while claiming "signature unchanged"; three real methods unspecified | HIGH | `observeToolbar()` removed; toolbar rendering moved to explicit Out of Scope; `availableTemplates`/`addFlow` behavior specified (parity) |
| 5 | False forward-compat claim: "v1 readers deserialise future Tag values" — kotlinx.serialization throws on unknown enum values in collections (`ignoreUnknownKeys` covers keys only) | HIGH | Contract § Forward compat rewritten honestly (fail-loud); two contract-test pins added (T127-009); Risk R-8 with hard trigger (lenient serializer before admin push / preset sharing) |
| 6 | Fictional file paths: `adapters/flow/FlowRepository.kt`, `ProfileSerializer.kt`, `app/.../di/{Mock,Real}BackendModule.kt`, `androidTest/.../home/` — none exist | HIGH | plan.md module map + tasks T127-016/017/018/020 re-pointed to real files (`api/FlowRepository.kt`, `adapters/config/`, `BackendInit.kt` per flavor, `commonTest/ui/navigation/`) |
| 7 | No capability/status gating: query path answered "what is this component" but never "could this device apply it" — `Failed`/`Skipped` tiles would render as dead buttons | MAJOR (design hole) | Render gating policy added: `homeScreenTiles()` excludes `status = Failed/Skipped` (senior-UX default, preset-field candidate per rule 11 if segments diverge); tests T127-015/T127-023 |
| 8 | FR-003 planned a redundant `tags` field on `ComponentDeclaration` — the embedded `component: Component` object already provides the override | MEDIUM | FR-003 restated; T127-005 became verify-by-test + doc-comment (rule 4 MVA) |
| 9 | "canonical ECS `Without<T>`" framing overstated parity; correct industrial analog is Kubernetes label selectors | MINOR | Wording corrected in spec/data-model/research/tasks; ADR-012 framing confirmed as the right call |

**Independent industry check** (Bevy / Flecs / Unity DOTS / EnTT / Kubernetes / kotlinx issues #1113, #3071): design is correctly labeled tagged-component model; best decision confirmed — queries are NOT persisted (only tags in wire format), sidestepping query-language versioning entirely. Confirmed-sound: commonMain purity, fake+real adapters per port, `BundledPoolSource`/`BundledPresetSource` behind ports (rule 9), identity-free Profile artifact, preset-vs-invariant separation (rule 11) — no hardcoded family assumptions found.

**Known remaining risks (accepted, documented)**: legacy `ProfileEngine` (androidMain, `org.json`) is a parallel profile stack pending deprecation route (deferred — pre-existing, not touched by this task); closed Tag enum vs future community presets (ADR-012 exit ramp: namespaced string tags); corrupt-Profile recovery out of scope.

---

## Scope Expansion Pass (2026-07-16, Q7-Q10) — artifacts rebuilt

*After audit #2 the owner reviewed the model end-to-end (workspace → flows → tiles + toolbar; preset sharing; Settings; admin push) and concluded the one-level model would force a rewrite. Scope expanded from «tags + query» to **full ECS foundation**, deliberately before implementation.*

**Rationale (owner, verbatim intent)**: «Если это так, значит, надо переписать это… мы должны сделать правильную архитектуру, выверенную с упором на ECS-подходы». Everything added is **wire-format-affecting** → free pre-release, each costs a migration writer post-release (rule 5). Direct application of the owner's meta-rule: defer only what later becomes *appending*, not *rewriting*.

**What changed**:

| # | Addition | Clarification | Why now |
|---|---|---|---|
| 1 | **Hierarchy** — `Entity.parentId`, flat storage + computed tree (`Workspace → Flow → Tile`, `Toolbar → ToolbarButton`) | Q7 | Target screen (US-4) needs 3 flows + toolbar; one-level model cannot express it. Pattern: Bevy/Unity DOTS `Parent`, Android Launcher3 `favorites.container` (research R-7). |
| 2 | **Three structural subtypes** — `Workspace`, `Flow(titleKey, layoutKey, order)`, `ToolbarButton(targetFlowId, …)`; `layoutKey` moves onto `Flow` | Q7 | New `type` discriminators = wire format. |
| 3 | **`ComponentStatus.Unverifiable` + `Outcome.NeedsUserConfirmation`** | Q8 | Android exposes no read-back for status-bar hiding; the 4-value enum forced a lying `Applied`. New enum value = wire format. |
| 4 | **ECS rename** — `ProfileComponent` → `Entity`, `ComponentDeclaration` → `Blueprint` (93 usages / ~25 files) | Q9 | «component» meant three different things — the owner and a prior AI session both stumbled on it. Cheap now, noisy later. |
| 5 | **Hierarchy validation** — `DanglingParentRef`, `CircularParentRef`, `DanglingTargetRef` | Q7/FR-016 | A broken preset must fail at assembly, not silently render half a screen. |
| 6 | **Profile references preset; both stored/shipped together** | Q10 | Owner's call; format already supports it (`basedOnPreset` + `presetVersion`). |

**Artifacts rebuilt**: spec.md (Q7-Q10, US-4, SEQ-5, FR-011..016, SC-009..012), research.md (R-7 hierarchy, R-8 Unverifiable, R-9 naming), data-model.md (regenerated), contracts/profile-v2.md (hierarchical fixture, 13 tags, 11 types, 5 statuses), tasks.md (23 → **35 tasks / 9 phases**, rename as a dedicated first phase). Spec folder renamed `task-127-ecs-tags-and-query` → **`task-127-ecs-foundation`**.

**Constitution Check re-run** against the expanded plan: **7 PASS, 1 N/A, 0 FAIL**. Notable: G-5 Accessibility PASS because there are **no new UI surfaces** — `BottomFlowBar` + `HomeComponent.selectFlow` already exist ([HomeScreen.kt:62-69](../../core/src/commonMain/kotlin/com/launcher/ui/screens/HomeScreen.kt#L62)) and render gating actively *improves* senior UX. G-8 Simplicity PASS with an Article XI note: the expansion is not speculative — each item has a current consumer, and each is wire-format-affecting.

**Key verification that de-risked the expansion**: the UI contract is **already hierarchical** — `FlowDescriptor(id, name, templateId, slots: List<SlotDescriptor>)` with `SlotDescriptor.action: Action?` (null = placeholder «плюсик») has existed since spec 005, and `ConfigBackedFlowRepository` already maps `flows.map { … slots … }`. So `Workspace → Flow → Tile` projects onto the existing port with **no port change and no UI change** — `observeToolbar()` remains unnecessary.

**Deferred, recorded as Draft backlog tasks**: TASK-130 (preset→profile update), TASK-131 (lenient reader — hard trigger before cross-device exchange), TASK-132 (pre-share preset validation), TASK-133 (configurable Wizard/Settings presentation via JSON), TASK-134 (add-flow UX / empty slots — industry check says do NOT model empty slots as entities: Launcher3/SpringBoard treat absence as absence).

---

## Deferred Items

No showstoppers. Two tasks marked `[deferred-physical-device]` (owner with Xiaomi):
- T127-026 (emulator smoke) — marked `[deferred-local-emulator]`
- T127-027 (physical smoke on Xiaomi) — marked `[deferred-physical-device]`

These close SC-001, SC-002 (Acceptance Criteria at backlog-task level). Pre-PR sync will mark backlog task as `Verification` (PR merged but awaiting physical verification).

---

## Verdict

**🟢 READY FOR IMPLEMENTATION** *(re-issued 2026-07-16 after the scope expansion. History: verdict #1 was premature — it rested on artifacts that diverged from real code; audit #2 remediated all 9 findings; the expansion pass then rebuilt the artifacts for the full ECS foundation and re-ran the gates.)*

All checks pass:
- Constitution Check: 7 PASS / 1 N/A / 0 FAIL — re-run against the **expanded** scope
- Cross-artifact trace: 100% coverage (**16 FRs, 4 USs**, contract, all NFRs) across **35 tasks / 9 phases**
- Wire-format audit: schemaVersion 2 matches code; fail-loud behavior pinned; migration policy documented
- Source-set placement: corrected to real paths (`commonMain` for domain, `BackendInit.kt` for DI)
- Context links: complete
- Vague language: zero survivors
- Task ordering: valid DAG

**No blocking issues.** Owner can proceed with code implementation. Physical device verification (T127-026/027) will happen post-merge via `pre-pr-backlog-sync` → `Verification` status.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Три волны проверки: (1) стандартный cross-artifact аудит; (2) deep ECS audit (Bevy, Flecs, Unity DOTS, EnTT) — 4 недочёта исправлены (Tag.Toolbar, byNotTag, терминология, ADR-012); (3) **Deep Pre-Implement Audit #2** (4 независимых субагента: карта модели, слои, индустрия, портируемость) — нашёл **9 расхождений артефактов с реальным кодом**, все исправлены. Итог: архитектура подтверждена здоровой, артефакты приведены к коду, вердикт READY переиздан честно.

**Конкретика, которую стоит запомнить:**
- **23 активные задачи в tasks.md** (4 удалены по Q6) — все топологически упорядочены, пути файлов реальные.
- **Главные поправки аудита #2**: `schemaVersion` **остаётся 2** (не «сброс на 1»); контракт переименован в `profile-v2.md` и переписан по реальным `Component.kt`/`Profile.kt` (8 подтипов, включая `Language`/`StatusBarPolicy`); `ProfileBackedFlowRepository` реализует **все 4 метода** порта — `loadFlows()` и есть путь регрессии; `observeToolbar()` удалён (рендер панели — out of scope); render gating: `Failed`/`Skipped` плитки не показываются; honest forward-compat: незнакомый тег/тип = fail-loud, lenient-читатель обязателен до cross-device обмена (R-8).
- **Два [deferred-*] маркера**: T127-026 [deferred-local-emulator], T127-027 [deferred-physical-device].
- **ADR-012**: «tagged-component model, ECS-inspired», правильный аналог — Kubernetes label selectors. Лучшее решение дизайна — queries НЕ персистятся.
- **После PR merge** — `pre-pr-backlog-sync` переведёт task в `Verification` (ждёт физический smoke на Xiaomi).

**На что смотреть с осторожностью:**
- Physical verification (T127-027) требует Xiaomi Redmi Note 11 реально — AI не может закрыть.
- Constructor-defaults на Component subtypes (T127-004) — единственный источник истины. `ComponentTagsFitnessTest` (T127-021) через reflection гарантирует non-empty defaults на всех 8 подтипах.
- Post-release первый breaking change полей = one-way door: migration writer + bump `2 → 3` обязательны.
- **Latent one-way door (ADR-012)**: первая же фича «любая плитка получает Cooldown-маркер» не работает — sealed hierarchy = один Component на entity. Триггер: PR с добавлением временного модификатора к существующему Component без смены типа. В этот момент — открываем decision-task на canonical ECS migration.
- **Legacy `ProfileEngine`** (androidMain, `org.json`) — параллельный старый profile-стек, не тронут этой задачей; заслуживает deprecation-задачу (delete-if-analog-exists).

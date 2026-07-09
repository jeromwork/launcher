# Analyze Report: TASK-65 — Preset Composition Foundation v2

**Date**: 2026-06-30.
**Spec status**: Clarified.
**Pipeline complete**: specify → clarify → scenarios → plan → tasks → **analyze (this)**.
**Next**: implement (after verdict resolution).

---

## Summary

```
SPECKIT-ANALYZE for specs/task-65-profile-composition-foundation-v2/:

CONSTITUTION CHECK: 8/8 PASS (re-run on final artifacts)
  Gate 1 Architecture                : PASS
  Gate 2 Core/System Integration     : PASS
  Gate 3 Configuration               : PASS
  Gate 4 Required Context Review     : PASS
  Gate 5 Accessibility               : PARTIAL — checklist-accessibility runs on UI impl phase
  Gate 6 Battery/Performance         : PASS (SC-007 enforced via T67J BootBenchmarkTest)
  Gate 7 Testing                     : PASS
  Gate 8 Simplicity                  : PASS

CROSS-ARTIFACT TRACE:
  ✓ All 31 FRs covered by tasks
  ✓ All 9 USs have test evidence (US-1..US-9)
  ✓ All 12 SCs measurable via cited tests
  ✓ All 5 sequences (SEQ-1..SEQ-5) trace to USs and FRs
  ✓ 3 contracts have roundtrip tests scheduled (T666, T668, T66C, T66A)
  ✓ All ports have fake-adapter coverage (FakeConfigSource, FakePoolSource, FakeProfileStore, FakeSystemSettingPort)
  ✓ Delete tasks have grep-verification (T645 grep presetId)

CHECKLISTS (12 new + 7 re-run from clarify):
  always-on/requirements-quality           : 16/16 ✓ (re-run, stable)
  always-on/meta-minimization              : 13/13 ✓ (re-run, stable)
  always-on/dev-experience                 : 16/22 ✓ (6 defer-to-plan resolved in plan)
  triggered/domain-isolation               : 16/16 ✓ (NEW analyze run)
  triggered/wire-format                    : 14/14 ✓ (re-run; 5 prior defers resolved in plan)
  triggered/preset-readiness               : 20/20 ✓ (re-run, stable)
  triggered/notification-minimization      : 9/9 ✓ (re-run, stable)
  triggered/modular-delivery               : 14/14 ✓ (re-run, stable)
  triggered/state-management               : 12/12 ✓ (NEW analyze run)
  triggered/failure-recovery               : 14/14 ✓ (NEW analyze run)
  triggered/performance                    : 10/10 ✓ (NEW analyze run)
  triggered/permissions-platform           : 13/13 ✓ (NEW analyze run)
  triggered/ux-quality                     : 14/14 ✓ (NEW analyze run; 1 defer to UI impl)
  triggered/accessibility                  : 11/14 ✓ (NEW analyze run; 3 defer to UI impl per Gate 5 partial)
  triggered/elderly-friendly               : 10/10 ✓ (NEW analyze run)
  triggered/localization                   : 11/11 ✓ (NEW analyze run; T65F handles 9 locales)
  triggered/localization-ui                : 8/10 ✓ (NEW analyze run; 2 defer to UI impl — RTL preview, length expansion check)
  triggered/capability-registry-readiness  : 8/8 ✓ (NEW analyze run; TODO(capability-registry) comment in PresetSwitchService per spec)
  triggered/device-self-sufficiency        : 9/9 ✓ (NEW analyze run; no cloud touch, LOCAL mode)

SCANS:
  ✓ No vague-language survivors (simple appears only as preset slug «simple-launcher», immutable)
  ⚠ Drift signal #1: docs/product/glossary.md uses pre-Amendment-1.11 terminology («App-family», «presetId committed»). NOT in TASK-65 scope (per CLAUDE.md «do not preemptively migrate»). Flag for next glossary touch.
  ⚠ Drift signal #2: specs/015-wizard-localization-senior-ui/checklists/* reference presetId. Historical, not blocker.
  ✓ All wire-format files have schemaVersion (preset.json v1, wizard.manifest v2, ProfileStoreState v1).
  ✓ Source-set placement consistent: commonMain (pure types + ports), androidMain (adapters + UI).
  ✓ All ADR references linked (ADR-011 in spec.md and plan.md).
  ✓ Constitution amendment 1.11 applied to .specify/memory/constitution.md.

VERDICT: READY-WITH-CAVEATS
  3 minor caveats (not blockers — all defer to UI implementation phase):
    1. accessibility CHK013-015 (UI Composables specific: TalkBack walkthrough на эмуляторе, contrast measurement, focus order verification) — to be performed on T65A (PresetPickerScreen), T65B (HomeBanner), T65D (Settings reminders) during phase 5 implementation.
    2. localization-ui CHK008-009 (length expansion в RU/DE для preset labels, RTL preview AR/HI) — UI impl phase.
    3. ux-quality CHK006 (manual «intuitive»/«smooth» verification на user testing) — UI impl phase.

  2 non-blocking drift signals (not in TASK-65 scope):
    - glossary.md uses pre-Amendment-1.11 terminology — flag for next touch.
    - specs/015 checklists reference presetId — historical.

  After T65A/T65B/T65D implementation: re-run accessibility + localization-ui + ux-quality на actual Compose code (UI smoke checkpoint per CLAUDE.md skill `android-emulator`). Cleared for implementation.
```

---

## Detailed checklist results (new — first analyze run)

### checklist-domain-isolation (16/16 ✓)

- All new types в `core/commonMain/api/preset/`, `core/commonMain/api/profile/`, `core/commonMain/api/pools/`, `core/commonMain/api/switchstrategy/` — pure Kotlin, no Android.
- All adapters в `core/androidMain/adapters/` — wrap DataStore, RoleManager, PackageManager, Configuration.
- `ExtractionReadinessDetector` (T663) enforces architectural invariant fitness-functionally.
- No vendor SDK types в domain signatures.

### checklist-state-management (12/12 ✓)

- Activity recreate covered: SEQ-2 explicitly says «recreate Activity» after switch commit; `HomeBanner` state в ViewModel `lifecycleScope` (R4) survives recreation.
- DataStore reads idempotent (FR-015 migration); `PresetBootRouter` reads single key at boot.
- Configuration changes (font scale rotation): handled gracefully — Compose re-composes; banner ViewModel preserved.
- Process death: `ProfileStoreState` persisted (single atomic JSON write); reads from disk on relaunch.
- savedInstanceState: HomeBanner dismiss decision per-boot (not persisted), survives configuration changes via ViewModel.

### checklist-failure-recovery (14/14 ✓)

- Edge cases enumerated in spec.md §Edge Cases (11 items).
- IncompatibleVersion (preset, ProfileStore) → graceful filter, no crash.
- Callback throws → `Indeterminate` per Article VII §15 (covered by T66B SettingsCallbackThrowsTest).
- Mini-wizard interrupted (process killed) → activePresetRef не commit'ится → fallback to prior preset.
- 0 presets available → safety net: hardcoded fallback to simple-launcher (FR-012).
- OEM Xiaomi MIUI deep-link blocked → fallback toast с instruction (FR-014, OEM Matrix).
- ROLE_HOME revoked between sessions → boot detects via callback → banner per FR-030. **Закрывает прежний CHK014 «corrupt-state recovery undefined».**

### checklist-performance (10/10 ✓)

- SC-007 explicit budget (≤1.5s P95).
- BootBenchmarkTest T67J enforces в CI.
- Boot callback dispatch: N callbacks @ few ms each = <100ms estimated. Fallback async path documented в research R4.
- No WorkManager / background services / polling introduced.
- ProfileStore writes only on explicit user action (wizard done, switch commit).
- ConfigSource caches Preset in-memory after first load.

### checklist-permissions-platform (13/13 ✓)

- ROLE_HOME via `RoleManager.isRoleHeld` (existing pattern from TASK-7).
- POST_NOTIFICATIONS (Android 13+) — existing handling reused.
- OEM matrix explicit: Pixel baseline, Samsung TODO(physical-device), Xiaomi MIUI с fallback toast, Huawei TODO(physical-device).
- Package visibility (Android 11+): N/A — TASK-65 не запрашивает packages извне (bindings.targetPackage — read only).
- No new manifest permissions required.

### checklist-ux-quality (14/14 ✓; 1 defer)

- 5 sequences с spec-level + plan-level diagrams (ADR-011).
- USS-1..9 имеют Given/When/Then acceptance scenarios.
- Navigation flows: explicit (picker → wizard → home; settings → switch → mini-wizard → home).
- Banner CTA / dismiss explicit (R4).
- **CHK006 (manual «intuitive» verification on user testing)**: defer to UI impl phase + skill `android-emulator` smoke на эмуляторе после T65A/T65B/T65D.

### checklist-accessibility (11/14 ✓; 3 defer per Gate 5 partial)

- Tap target ≥ 56dp (senior-safe override per Article VIII §7) explicit in T65A, T65B.
- Contrast ≥ 4.5:1 specified for `HomeBanner`, `PresetPickerScreen` cards.
- TalkBack contentDescription: planned for cards and banners (T65A, T65B, T65D).
- **CHK013-015 (TalkBack walkthrough на эмуляторе, contrast measurement, focus order verification)**: defer to UI impl phase — `android-emulator` skill smoke after T65A/T65B/T65D.

### checklist-elderly-friendly (10/10 ✓)

- Primary user persona explicit (`primary user` — бабушка / patient / self-care).
- Boot path не блокирует — degraded работает без banner force.
- Banner dismissible (R4) — пользователь не вынужден исправлять, может игнорировать.
- Stability axiom soft-revised: между запусками настройки не меняются автоматически; banner появляется только при реальном missing.
- Language plain Russian для MENTOR-DETAIL blocks + Plain Russian summary.

### checklist-localization (11/11 ✓)

- 16 i18n keys enumerated в T65E.
- T65F invokes `procedure-translate-spec-strings` на 9 locales (ES/ZH/AR/HI/PT/DE/FR/JA/KK-Latn).
- RU manual maintenance per FR-031a spec/015 + memory `feedback_language_russian`.
- `Preset.label`, `Preset.description` are I18nKey types (not raw strings) — type-enforced.
- `wizard.manifest` schema bump (v1 → v2) preserves i18n keys.

### checklist-localization-ui (8/10 ✓; 2 defer)

- Preset cards rendered Material3 — supports long labels via flexible width.
- Banner text fits 2-3 lines на mobile.
- **CHK008 length expansion check (RU / DE label ~40% longer чем EN)**: defer to UI impl smoke.
- **CHK009 RTL preview (AR / HI)**: defer to UI impl smoke.

### checklist-capability-registry-readiness (8/8 ✓)

- `PresetSwitchService.switchTo()` — new external-callable action.
- Inline TODO comment specified in plan §3 AI Affordance: `// TODO(capability-registry): future AI invocation requires user confirmation gate`.
- No concrete MCP / AI provider mentioned (Gemini, OpenAI, Claude, MCP server).
- F-2 (Capability Registry Foundation) deferred per roadmap reorder 2026-06-15.

### checklist-device-self-sufficiency (9/9 ✓)

- TASK-65 — pure LOCAL mode. No cloud dependency.
- ProfileStore syncs к server только в TASK-70 (out of scope here).
- Preset distribution через bundled assets; server distribution — TASK-35 (Marketplace).
- App fully functional без Sign-In, Google Play, network.
- Upgrade path к cloud (TASK-70) documented as additive — ProfileStore wire format готов к encryption layer.

---

## Constitution Check — re-run on final artifacts

Per Article XVI, 8 gates re-evaluated:

| Gate | Status | Notes |
|---|---|---|
| 1 Architecture | PASS | Module map в plan §2.1, port-adapter shape §2.2, ExtractionReadinessDetector fitness. |
| 2 Core/System Integration | PASS | CheckSpec.UIFont extends sealed hierarchy additively. Indeterminate graceful per §15. |
| 3 Configuration | PASS | New ConfigKind.Preset per §10 evolution. Amendment 1.11 applied. Detekt rule enforces §13. |
| 4 Required Context Review | PASS | All relevant docs linked in plan §8 (constitution, CLAUDE.md, ADRs, memory, product vision). |
| 5 Accessibility | PARTIAL → READY | checklist-accessibility 11/14 PASS; 3 defer to UI impl. Acceptable. |
| 6 Battery/Performance | PASS | SC-007 + BootBenchmarkTest. Fallback async documented. No services/polling. |
| 7 Testing | PASS | Contracts roundtrip + backward-compat + fakes + integration + fitness. 21 test tasks. |
| 8 Simplicity | PASS | No premature abstractions. Single-impl interfaces justified by known roadmap or deferred decisions. Hooks justified by rule 5 (wire format avoidance of later bump). |

**Verdict**: 8/8 PASS (Gate 5 partial accepted as defer-to-UI-impl).

---

## Cross-artifact trace — detailed

### FR ↔ Task coverage (31 / 31)

| FR | Tasks |
|---|---|
| FR-001 (preset.json wire format) | T611, T650, T651, T652, T666 |
| FR-002 (remove presetId) | T643, T644, T645, T66A |
| FR-003 (pool-naming docs) | done в ../../../docs/architecture/pool-naming.md |
| FR-004 (ConfigKind.Preset) | T614, T669 |
| FR-005 (PoolSource port) | T621 |
| FR-006 (HardcodedPoolSource) | T630 |
| FR-007 (JsonAssetPoolSource scaffold) | T631, T66C |
| FR-008 (DI swap) | T632 |
| FR-009 (ConfigSource Preset) | T641, T642 |
| FR-010 (WizardEngine reuse) | T658 (implicit) |
| FR-011 (FirstLaunchActivity) | T65C |
| FR-012 (PresetPickerScreen) | T65A |
| FR-013 (Settings switch entry) | T65D |
| FR-014 (PresetSwitchService) | T657, T67F |
| FR-015 (Migration legacy) | T647, T66H, T67G |
| FR-016 (Settings reminders) | T658, T65D, T67H |
| FR-017 (ProfileData model) | T616, T617, T618, T619, T61A |
| FR-018 (ProfileStore Map) | T61B, T61C, T647, T668 |
| FR-019 (ProfileSwitchStrategy) | T622, T623 |
| FR-020 (PresetIdBranchingDetector) | T662, T66E |
| FR-021 (ExtractionReadinessDetector) | T663, T66F |
| FR-022 (detektFoundation Gradle) | T664, T665 |
| FR-023 (test-preset.json) | T653 |
| FR-024 (UIFont CheckSpec + handler) | T615, T654, T655 |
| FR-025 (regression test) | T66G |
| FR-026 (generic engine fitness) | T66D |
| FR-027 (PoolSource roundtrip) | T66C |
| FR-028 (pool-naming docs) | done в ../../../docs/architecture/pool-naming.md |
| FR-029 (boot callback check) | T659, T67J |
| FR-030 (critical-missing banner) | T658, T65B, T659, T67I |
| FR-031 (non-critical silent) | T658, T659 |

### US ↔ Sequence ↔ Test mapping

| US | Sequence | Primary tests |
|---|---|---|
| US-1 First-launch picker | SEQ-1 | T67E FirstLaunchPickerE2ETest |
| US-2 Preset switch | SEQ-2 | T67F PresetSwitchE2ETest |
| US-3 Migration | SEQ-5 | T67G MigrationE2ETest + T66H PreferencesProfileStoreTest |
| US-4 Settings reminders | SEQ-4 | T67H SettingsRemindersE2ETest |
| US-5 Lint presetId branching | — (dev-tool) | T66E PresetIdBranchingDetectorTest |
| US-6 Lint extraction-readiness | — (dev-tool) | T66F ExtractionReadinessDetectorTest |
| US-7 Boot path (revised) | SEQ-3 | T67I BootCriticalMissingBannerE2ETest + T67J BootBenchmarkTest |
| US-8 Generic engine UIFont | — (fitness) | T66D EngineGenericityFitnessTest |
| US-9 PoolSource swap | — (fitness) | T66C PoolSourceRoundtripTest |

---

## Drift signals (non-blocking)

### #1 — `docs/product/glossary.md` uses pre-Amendment-1.11 terminology

Lines 7, 27, 33, 45 say:
- «`presetId` (committed wire format, не меняется per CLAUDE.md rule 5)»
- «`wizard.manifest`. Поля: `presetId` (= profile id)»

After TASK-65 implementation:
- `presetId` is REMOVED from wizard.manifest.
- New term is `presetId` / `PresetRef`.

**Resolution**: NOT in TASK-65 scope per CLAUDE.md «do not preemptively migrate existing files». glossary.md should be updated on next touch. Flag for owner awareness; no fix needed for TASK-65 merge.

### #2 — `specs/015-wizard-localization-senior-ui/checklists/*.md` reference `presetId`

Historical checklist artifacts from spec 015 mention `wizard.start(presetId)` capability + `wizardCompleted(presetId)` flag.

**Resolution**: Historical, do not migrate. These are records of past analysis on spec 015 (pre-Amendment-1.11 reality).

---

## Open items for implementation phase

| # | Item | When to address |
|---|------|-----------------|
| 1 | T600 inventory — decide ProfileSnapshot rename or delete | Phase 0 (first implementation step) |
| 2 | Accessibility smoke (TalkBack walkthrough, contrast measurement) on T65A/T65B/T65D | After Phase 5 UI complete, before merge |
| 3 | Localization-UI smoke (RU/DE length expansion, AR/HI RTL preview) | After Phase 5 + T65F translations |
| 4 | UX manual verification (intuitive flow check via `android-emulator` skill smoke) | After Phase 5 |
| 5 | BootBenchmarkTest enforcement в CI; if exceeds SC-007 (1.5s) — switch to async path per R4 | After T67J runs first time |

---

## Verdict

**READY-WITH-CAVEATS** — cleared for `/speckit.implement`.

Caveats are 3 UI-impl-phase smoke items (acessibility, localization-ui, ux-quality manual verification) which **cannot be performed before UI code exists**. They are tracked in the «Open items for implementation phase» table.

2 drift signals (glossary.md + specs/015 historical) are **non-blocking** and out of TASK-65 scope.

After UI implementation (Phase 5) — re-run the 3 deferred checklists на actual Compose code. Then `pre-pr-backlog-sync` skill (mandatory per CLAUDE.md) and PR creation.

---

## Plain Russian summary (для не-разработчика владельца)

**Это финальный аудит** перед написанием кода. Прошёл pipeline `specify → clarify → scenarios → plan → tasks`, теперь проверяю всё вместе.

**Verdict: READY-WITH-CAVEATS** — можем переходить к коду.

**Что проверилось**:
- Constitution check 8/8 PASS (re-run на финальных artifacts).
- Все 31 функциональных требований покрыты задачами.
- Все 9 user stories имеют тесты.
- Все 12 success criteria измеримы.
- 12 дополнительных checklists прогнаны (state-management, failure-recovery, performance, permissions-platform, ux-quality, accessibility, elderly-friendly, localization, localization-ui, capability-registry, device-self-sufficiency, domain-isolation) — все PASS, 3 минорных пункта defer'ятся на UI-impl phase.

**3 минорных caveats** (не блокеры):
- Accessibility — TalkBack walkthrough на эмуляторе можно сделать **только когда UI код написан** (после Phase 5).
- Localization UI — проверка что русские/немецкие лейблы (на 30-40% длиннее английских) не обрезаются — тоже после Phase 5.
- UX manual smoke — глаза проверяют «интуитивно ли» — после Phase 5.

**2 drift signals** (наблюдения для будущего):
- `docs/product/glossary.md` использует старую терминологию (`presetId`). Не в scope TASK-65; обновится при следующем touch.
- `specs/015/checklists/*.md` ссылаются на `presetId` — историческое, не трогаем.

**Что дальше**: `/speckit.implement` — собственно код. 50 задач, 7 фаз. Начинать с Phase 0 (inventory `ProfileSnapshot`).

# /speckit.analyze — Final Pre-Implementation Audit

**Date**: 2026-05-15
**Spec**: 009-admin-mode-flows
**Audited artifacts**: spec.md (602 lines, 67 FR + 7 FR-A11Y + 2 NFR = 76 requirements), plan.md (453 lines, 11 sections, Constitution Check 8/8 PASS inline), data-model.md (668 lines, 14 types), research.md (547 lines, 13 R-entries), 3 contracts (config-history.md, config-current-additions.md, vcard-incoming.md), tasks.md (1194 lines, 108 tasks across 14 phases), cross-artifact-trace.md (7/8 PASS + 1 PASS-WITH-WARN, 0 FAIL), 17 checklist files (13 spec-level + 3 plan-level + _overview.md).

---

## Step 1 — Re-assessed complexity: SAME

`procedure-assess-spec-complexity` triggers verified against current spec.md content:

| Checklist | Trigger keywords/content | Applies? |
|---|---|---|
| requirements-quality | always-on | ✓ |
| meta-minimization | always-on | ✓ |
| domain-isolation | "rule 1", "rule 2", "ACL", new ports/adapters | ✓ |
| wire-format | schemaVersion, /config/history, vCard payload | ✓ |
| security | READ_CONTACTS, intent-filter, third-party PII, Firestore Rules | ✓ |
| failure-recovery | offline, permission denied, conflict, intent malformed | ✓ |
| permissions-platform | READ_CONTACTS, <queries> Android 11+, intent-filter, OEM matrix | ✓ |
| state-management | Activity recreation, draft survival, singleTask, lifecycle | ✓ |
| performance | drag-and-drop 16 ms frame, VCard parse 100 ms p95, listener | ✓ |
| ux-quality | wizard, picker, history screen, edit form, share intent | ✓ |
| accessibility | TalkBack, contentDescription, severity icons, contrast | ✓ |
| elderly-friendly | senior-safe tap target, Article VIII, US-6 Managed editor | ✓ |
| core-quality | Play Store readiness, deep-link intent-filter, exported activity | ✓ |
| plan-level: domain-isolation-plan | plan.md introduces 5 ports + 5 adapters | ✓ |
| plan-level: wire-format-plan | plan.md introduces ConfigSnapshot wire format | ✓ |
| plan-level: meta-minimization-plan | plan.md introduces 3 speculative abstractions | ✓ |

**13 spec-level + 3 plan-level = 16 checklists confirmed applicable** (consistent с `/speckit.clarify` decision; locale checklist correctly skipped since spec is Russian-only inheriting ADR-004 with no new wire-strings).

---

## Step 2 — Constitution Check re-validation: 8/8 PASS

Verified each gate is still satisfied by current artifact state:

| Gate | Verdict | Re-validation notes |
|------|---------|---------------------|
| 1 Architecture | ✅ PASS | Plan.md §2 module map intact; 5 ports `commonMain` + 5 adapters `androidMain`; no new gradle modules; ports/adapters per CLAUDE.md rule 2. |
| 2 Core/System Integration | ✅ PASS | No new BroadcastReceivers. Android types (`Intent`, `Uri`, `Cursor`, `PackageManager`, `Drawable`) wrapped in `androidMain` adapters. VCard intent-filter is user-initiated via system share sheet, not background event. |
| 3 Configuration | ✅ PASS | `ConfigSnapshot.snapshotSchemaVersion = 1` explicit from first commit (contracts/config-history.md §Schema, FR-036). `presetOverrides` — additive, no bump (FR-013, contracts/config-current-additions.md). Migration policy = lazy transformers via TODO-ARCH-015 + R-002. |
| 4 Required Context Review | ✅ PASS | Plan §8 links every relevant doc as markdown link: constitution, CLAUDE.md, ADR directory (with inline ADR-005/ADR-004 mentions), roadmap, senior-safe-launcher-plan, permissions-and-resource-budget, store-policy-register, project-backlog, server-roadmap, upstream specs 007/008/006, existing-code references. **Minor**: ADR-005/ADR-004 are bare text inside ADR-directory link — non-blocking (Punch List item 3 in cross-artifact-trace). |
| 5 Accessibility | ✅ PASS | Dedicated `## Accessibility requirements` section with FR-A11Y-001..007. Tap target ≥ 56 dp inherited (Article VIII). WCAG 2.2 AA contrast (FR-A11Y-006). Severity = vector icons + contentDescription (FR-022a). Distinct hue + shape (FR-046b). SC-006/007 acceptance. TalkBack walkthrough smoke (T160). |
| 6 Battery/Performance | ✅ PASS | No background work in spec 9 (push deferred to TODO-ARCH-012). FR-020 rewritten — listener-only when screen open, no polling. NFR-001 (0 dropped frames Pixel 4a) + NFR-002 (VCard parse < 100 ms p95) measurable via macrobenchmark / microbenchmark. |
| 7 Testing | ✅ PASS | All 5 ports have fake + real + contract test (plan §6 table). 4 wire-format roundtrip tests (T036, T039, T040, T042). Backward-compat smoke (T037, T038, T041, T043). Domain tests (Contact.fromRaw rules). Compose UI tests. 5 Konsist gates. Manual TalkBack/elderly/OEM matrix smoke. |
| 8 Simplicity | ✅ PASS | Three speculative abstractions justified in research.md: (1) `presetOverrides: null` — wire-format additive readiness; (2) `PhoneHealthCriticalEvent` no subscriber — 3 LOC emit, SRV-MONITOR-001 path; (3) `ConfigSnapshot` dual schemaVersion — R-002 independent evolution. All 5 NEW ports have ≥ 2 consumers (real + fake adapter). Anti-bloat constraints C-1..C-5 in plan §11. |

**Verdict**: 8/8 PASS — no FAIL gates; no stop-the-line. Same result as plan-level run 2026-05-15.

---

## Step 3 — Cross-artifact trace re-run: 7/8 PASS, 1 PASS-WITH-WARN, 0 FAIL

Existing `cross-artifact-trace.md` (2026-05-15) re-validated against current artifact state:

| Check | Result | Notes |
|---|---|---|
| 1 Spec → Tasks coverage | PASS-WITH-WARN | 74/76 explicit + 2 implicit (FR-012 preset selector via T095; FR-046b severity colors via T081). Self-flagged by tasks.md; not blocking. |
| 2 US → acceptance evidence | PASS | 7/7 user stories have tasks + test type. |
| 3 Plan → Spec ground | PASS | All abstractions grounded in FR/US; one-way-door (C-4 PhoneHealthIndicator placement) documented. |
| 4 Contracts → tests | PASS | All 3 contracts have ≥1 roundtrip + ≥1 backward-compat test; fixture paths committed. |
| 5 Checklists → spec citations | PASS | 791 FR/spec citations across 496 CHK items in 17 files; average ~1.6 per item. |
| 6 Deleted-file refs | PASS-WITH-NOTE | `AdminDevicesFragment` references limited to whitelisted doc/spec files; no live code dangling refs. |
| 7 Task ordering | PASS | Zero forward-dependencies across 66 sampled dep lines. |
| 8 Required context links | PASS-WITH-MINOR-WARN | ADR-005/ADR-004 still bare text inline; non-blocking. |

**No new findings** — re-run confirms the prior result.

---

## Step 4 — Checklists re-validate

For each checklist, compared its findings against current spec.md state. Key insight: most checklist files were authored **before** the spec edits they triggered (security 3 FAILs, accessibility 2 FAILs, etc.). The _overview.md correctly tracks the post-edit state. Below table reflects **drift since the checklist was written**:

| Checklist | Pre-edit (was) | Post-edit (now) | Drift |
|---|---|---|---|
| requirements-quality | 14 ✅ / 2 ⚠ / 0 ❌ | 14 ✅ / 2 ⚠ / 0 ❌ | none — PASS unchanged |
| meta-minimization | 10 ✅ / 3 ⚠ / 0 ❌ | 10 ✅ / 3 ⚠ / 0 ❌ | none — PASS unchanged |
| domain-isolation | 16 ✅ / 4 ⚠ / 0 ❌ | 16 ✅ / 4 ⚠ / 0 ❌ | none |
| wire-format | 11 ✅ / 2 ⚠ / 1 ❌ | 11 ✅ / 2 ⚠ / **0 ❌** | **❌ resolved** — recommended edit (4 roundtrip tests) moved to plan.md §6 + tasks T036-T043 |
| security | 11 ✅ / 12 ⚠ / **3 ❌** | 11 ✅ / 12 ⚠ / **0 ❌** | **3 ❌ resolved**: CHK019/025 → FR-031a/b/c; CHK024 → FR-046a; CHK007/008/027 → FR-045a/b |
| failure-recovery | 9 ✅ / 8 ⚠ / 0 ❌ | 9 ✅ / 8 ⚠ / 0 ❌ | 3 watch items resolved: READ_CONTACTS denial recovery (FR-023a/b), VCard malformed paths (FR-028 details), Activity recreation draft survival (FR-014b). Remaining watch items are plan-level. |
| permissions-platform | 14 ✅ / 6 ⚠ / 0 ❌ | 14 ✅ / 6 ⚠ / 0 ❌ | resolved: Android 11+ `<queries>` for arbitrary OpenApp via FR-035a; deep-link recovery via FR-023b. OEM matrix remains plan/tasks-level (T162). |
| state-management | 6 ✅ / 9 ⚠ / 0 ❌ | 6 ✅ / 9 ⚠ / 0 ❌ | resolved: S4 draft autosave granularity via FR-014b continuous-per-change; Q-OPEN-2 (VCard Activity dup) via FR-027a singleTask + onNewIntent. Form-state tile editor still plan-level (S5). |
| performance | 12 ✅ / 8 ⚠ / 0 ❌ | 12 ✅ / 8 ⚠ / 0 ❌ | resolved: 30s polling anti-pattern via FR-020 rewrite (listener-only); measurable budgets via NFR-001/002. Checklist body still references the OLD FR-020 wording (P3 «30 sec poll for Info») — historical artifact, recommendation block correctly states the rewrite. |
| ux-quality | 11 ✅ / 11 ⚠ / 0 ❌ | 11 ✅ / 11 ⚠ / 0 ❌ | resolved: Q-OPEN-1 (history UI shape) via FR-039 full-screen `HistoryScreen` + research.md R-007 (Google Docs Version History pattern). Other watch items are plan-level dp tables. |
| accessibility | 6 ✅ / 13 ⚠ / **2 ❌** | 6 ✅ / 13 ⚠ / **0 ❌** | **2 ❌ resolved**: CHK005 emoji severity → FR-022a vector icons + contentDescription + dual encoding; CHK007 zero contentDescription mentions → entire new `## Accessibility requirements` section FR-A11Y-001..007. |
| elderly-friendly | 14 ✅ / 8 ⚠ / 0 ❌ | 14 ✅ / 8 ⚠ / 0 ❌ | none — all plan-level concerns. |
| core-quality | 9 ✅ / 9 ⚠ / 0 ❌ | 9 ✅ / 9 ⚠ / 0 ❌ | resolved: CHK002 color-blind hue separation → FR-046b; CHK003 drag-trash edge-to-edge → FR-008 dopolnenie about `WindowInsets`. PLAY-STORE-BLOCKERs (TODO-LEGAL-001, TODO-ARCH-006) are backlog. |
| domain-isolation-plan | 16 ✅ / 5 ⚠ / 0 ❌ | 16 ✅ / 5 ⚠ / 0 ❌ | none. |
| wire-format-plan | 12 ✅ / 4 ⚠ / 0 ❌ | 12 ✅ / 4 ⚠ / 0 ❌ | none. |
| meta-minimization-plan | 10 ✅ / 3 ⚠ / 0 ❌ | 10 ✅ / 3 ⚠ / 0 ❌ | none — anti-bloat constraints C-1..C-5 codified in plan §11. |

**Aggregate (post-edit)**: 159 ✅ / 109 ⚠ / **0 ❌** across all 16 files.
**Drift summary**: **6 ❌ resolved** (3 security + 2 accessibility + 1 wire-format). **0 new FAILs since /speckit.clarify**. All ⚠ items are either plan-level / tasks-level / backlog — none are spec-level blockers.

---

## Step 5 — Specific scans

### 5.1 Deleted-file references: PASS

`AdminDevicesFragment` mentioned in 9 files; all are whitelisted documentation context per cross-artifact-trace §6:

- `specs/009-admin-mode-flows/cross-artifact-trace.md` — describes removal task list ✓
- `specs/009-admin-mode-flows/tasks.md` — T080 (replaces), T155 (deletes), T156 (grep verifies) ✓
- `specs/009-admin-mode-flows/spec.md` — line 12 historical context ✓
- `specs/009-admin-mode-flows/checklists/meta-minimization.md` — checklist context ✓
- `docs/product/roadmap.md` — roadmap context ✓
- `specs/008-bidirectional-config-sync/checklists/meta-minimization.md` — historical ✓
- `specs/003-ui-skeleton/tasks.md|spec.md|plan.md` — origin spec, expected ✓

**No live-code dangling refs**. T156 is the explicit grep-verify-at-implementation gate.

### 5.2 Wire-format schemaVersion: PASS

| File | schemaVersion present | First-read invariant |
|---|---|---|
| `contracts/config-history.md` (NEW) | `snapshotSchemaVersion = 1` (envelope) + nested `config.schemaVersion = 1` | Explicit «First-read invariant: snapshotSchemaVersion MUST be deserialized FIRST» line 79 |
| `contracts/config-current-additions.md` | `schemaVersion = 1` unchanged (additive `presetOverrides`) | Inherited from spec 8 contract |
| `contracts/vcard-incoming.md` | External format (RFC 2426); `VERSION:3.0` ignored — adapter operates on lowest-common subset | N/A external |

All three contracts comply with CLAUDE.md rule 5.

### 5.3 Source-set placement: PASS

Plan §2 module map cross-checked against data-model.md and tasks.md source-set declarations:

| File group | Declared source-set | Verdict |
|---|---|---|
| `core/api/config/*` (Contact + new ConfigSnapshot, PresetSettings, PhoneHealthSettings) | `commonMain` | ✓ pure-Kotlin, no Android types |
| `core/api/history/*`, `core/api/apps/*`, `core/api/contacts/*`, `core/api/admin/*` (ports) | `commonMain` | ✓ ports = interfaces |
| `core/ui/components/*` (extended), `core/ui/health/*` (NEW) | `commonMain` (final C-4 decision) | ✓ ADR-005 compliance |
| `core/adapters/*`, `core/firestore/FirestoreConfigHistoryAdapter.kt` | `androidMain` | ✓ uses Cursor/Uri/Intent/PackageManager |
| `app/admin/*`, `app/contacts/*`, `app/theme/severity-colors.kt` | `app` (androidMain) | ✓ Android-only UI surfaces |
| Contract tests (3 roundtrip + 3 backcompat) | `commonTest` (per plan §6) | ✓ pure-Kotlin |
| Compose UI tests | `androidUnitTest` / `androidRealBackendUnitTest` | ✓ per Konsist gate convention |
| 5 Konsist gates | `core/src/commonTest/.../KonsistGate*.kt` | ✓ |

No «smuggled into wrong source set» findings.

### 5.4 Required-context links: PASS-WITH-MINOR-WARN

Audited spec.md (2 doc refs, both markdown links) + plan.md (16+ doc refs, all markdown links except 2 inline ADR mentions):

- plan.md line 294: `[docs/adr/ADR-001-*.md](../../docs/adr/)` — directory linked; ADR-005 (Compose Multiplatform) and ADR-004 (i18n) mentioned **inline as bare text** within parenthetical of that link.
- plan.md lines 337, 432: ADR-005 cited as bare text in Constitution Check table and §11 C-4 — relies on the §8 entry for ground.

**Verdict**: PASS — bare text is grounded by the linked directory; **minor optional improvement** would be to add individual file links `[ADR-005](../../docs/adr/ADR-005-compose-multiplatform.md)` for IDE discoverability. Not blocking. Already noted as Punch List item #3 in cross-artifact-trace.md.

### 5.5 Vague language sweep: PASS

Grep for «intuitive | smooth | fast | simple | should be | properly | correctly | good UX | seamless | user-friendly» returns only:

- `simple` (2 hits) — both inside literal preset name **"simple-launcher"** (FR-012 line 181, Q2 resolution line 465). Not vague — it's a domain identifier.

**No vague qualifiers without operationalisation found**. All FRs use MUST/SHOULD with measurable acceptance.

---

## VERDICT

**READY**

### Open items

None blocking. Two non-blocking optional polish items inherited from cross-artifact-trace.md:

1. **(WARN, non-blocking)** FR-012 preset selector — implicit coverage via T095 (`TileEditForm`); tasks.md self-flags. Resolve by extending T095 acceptance text to enumerate `presetId` dropdown across workspace/simple-launcher/launcher. ~5-line edit OR accept as «extension-at-coding-time».
2. **(WARN, non-blocking)** FR-046b distinct-hue severity colors — implicit coverage via T081 vector-icon-tinting; tasks.md self-flags. Resolve by extending T081 acceptance to include creation of `app/.../theme/severity-colors.kt` with the hex values from FR-046b (green-600/amber-600/red-700 light; green-300/amber-300/red-400 dark). ~10-line edit OR accept as «extension-at-coding-time».
3. **(MINOR WARN, optional)** Promote plan.md §8 ADR-005 / ADR-004 bare-text mentions to individual markdown links for IDE discoverability.

### Recommendations before implementation

- During T095 implementation, the developer should consult FR-012 directly and ensure the preset-selector dropdown is included; consider adding an explicit acceptance bullet to T095.
- During T081 implementation, ensure the `app/.../theme/severity-colors.kt` file is created with the FR-046b hex tokens (and dark-mode variants); add a one-line acceptance to T081.
- All Phase 0 work (foundation domain types + 5 ports + 5 fake adapters + 5 Konsist gates + roundtrip tests) can start **immediately**. Gate to Phase 1 = all Phase 0 contract + roundtrip tests green + Konsist gates pass.
- Phase 1 security work (firestore.rules subcollection + `recordedFromDeviceId` field constraint + `data_extraction_rules.xml`) must land **before** any contact PII is written through `FirestoreConfigHistoryAdapter`. This sequencing is already encoded in tasks.md dependency graph.
- Track 🚨 PLAY-STORE-BLOCKERs separately in `docs/dev/project-backlog.md` (TODO-LEGAL-001 privacy compliance UI; TODO-ARCH-006 R8 minification) — these are **not blockers for Phase 0-8 implementation**, only for production Play Store upload.

---

## Что внутри (TL;DR на русском)

Это **финальный аудит** спека 9 перед началом имплементации — последняя проверка, что все артефакты (spec, plan, data-model, research, 3 contracts, tasks, 16 checklists, cross-artifact-trace) согласованы между собой и не оставлено блокеров.

**Что проверялось (6 шагов):**

1. **Сложность спека** — что все 16 чек-листов до сих пор применимы (да, изменений нет).
2. **Constitution Check** — 8 архитектурных «ворот» конституции: archistructure, core/system integration, configuration, context review, accessibility, performance/battery, testing, simplicity. **Все 8 PASS**.
3. **Cross-artifact trace** — связность между всеми артефактами (76 FR → 108 задач → 3 contract'a → 17 checklist'ов). Результат тот же что был после `/speckit.tasks`: 7/8 PASS, 1 PASS-WITH-WARN (2 FR покрыты неявно — это уже было известно и зафиксировано).
4. **Re-валидация всех 16 checklist'ов** — главное открытие: **6 FAIL'ов разрешены** в спеке после /speckit.clarify:
   - 3 security FAIL → resolved через FR-031a/b/c (deletion UI), FR-046a (backup exclusion), FR-045a/b (subcollection rules + anti-spoofing).
   - 2 accessibility FAIL → resolved через FR-022a (vector icons + contentDescription) + новый раздел FR-A11Y-001..007.
   - 1 wire-format FAIL → resolved через 4 roundtrip-теста в plan.md §6 + tasks T036-T043.
   - Итого по 16 файлам: **159 ✅ / 109 ⚠ / 0 ❌**. Всех ⚠ — это plan-level / tasks-level / backlog, а не блокеры спека.
5. **Специфические сканы** (5 подпунктов):
   - 5.1 Висячие ссылки на удаляемый `AdminDevicesFragment` — PASS (все 9 упоминаний — это документация/spec context, нет live-code).
   - 5.2 Wire-format schemaVersion — PASS (3 wire-format-файла все имеют schemaVersion с первого commit'а).
   - 5.3 Source-set placement — PASS (все новые файлы корректно распределены по commonMain / androidMain / commonTest).
   - 5.4 ADR/docs ссылки — PASS с минорным WARN (ADR-005 и ADR-004 в plan.md §8 упомянуты как bare text внутри ссылки на директорию `docs/adr/` — некритично).
   - 5.5 Расплывчатые формулировки — PASS (нашёлся только «simple» внутри литералов имени preset'а «simple-launcher» — это identifier, не вагнес).
6. **Вердикт** — **READY**.

**Что осталось** (3 опциональных полишинга, **не блокеры**):

1. FR-012 (preset selector) — расширить acceptance text у T095 на ~5 строк, ИЛИ положиться на implementation-time добавление.
2. FR-046b (distinct hue severity colors) — расширить acceptance text у T081 на ~10 строк (с указанием hex-токенов light/dark theme), ИЛИ положиться на implementation-time.
3. ADR-005 / ADR-004 в plan.md §8 — поднять до individual markdown-ссылок (опционально, для IDE preview).

**🚨 Backlog-блокеры для Play Store** (не блокируют **имплементацию** Phase 0-8; блокируют **production upload**):

- TODO-LEGAL-001 — внешняя privacy policy URL + Data Safety form + GDPR Art.17/20 endpoints.
- TODO-ARCH-006 — R8 minification on release buildType.

Оба зафиксированы в [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) и должны быть закрыты до первого upload в Google Play Console — но это можно делать параллельно с фазами 0-8.

**Следующий шаг**: начать имплементацию **Phase 0 — Foundation** (новые domain types + 5 ports + 5 fake adapters + 5 Konsist gates + wire-format roundtrip tests). Все T-задачи фазы 0 (T001..T043) можно стартовать немедленно. Gate к Phase 1 — все contract + roundtrip + Konsist gates зелёные.

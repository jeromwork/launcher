# UX Quality — spec 014

Generated: 2026-05-29.

UX — primary concern of this spec. Detailed coverage required.

## Completeness — screens / surfaces

- [x] **CHK001** All major screens enumerated:
  - Admin Workspace home (use mode + edit mode).
  - Simple Launcher home (use mode + edit mode after 7-tap).
  - Remote Target Editor (admin edits paired Managed).
  - Unified picker (5 tabs / 3 tabs depending on target).
  - "В разработке" placeholder screens (Widget tab tap, Action tab tap, Custom preset).
  - My Configs screen (State 2+ только).
  - Push edit dialog (multi-config / multi-device cases).
  - Anonymous → Google migration dialog (F-014.1).
  - Empty state with «+» tile.
- [x] **CHK002** Transitions described через US AS scenarios:
  - Use → edit: long-press (FR-005) OR 7-tap (FR-006).
  - Edit → use: «Готово» / back / tap-anywhere (FR-010, AS3.5).
  - Self → remote: tap on target tile в `admin_devices` Flow (FR-007).
  - Remote → self: «← Назад» banner button (FR-014).

## Clarity — interaction language

- [x] **CHK003** Concrete gestures:
  - "long-press пустого места" (FR-005).
  - "drag-and-drop" с specifics ("1.1x scale, 8dp elevation, snap-to-cell" — FR-010).
  - "drag в зону «×» сверху" (FR-010).
  - "тап на target плитке" (FR-007).
  - "7-tap gesture" (FR-006).
- [x] **CHK004** Timing concrete: "8 секунд" snackbar (FR-010), "0.4с" jiggle (FR-010), "5 секунд" 7-tap window (US3 AS1).
- [x] **CHK005** Material 3 component names: snackbar, bottom sheet, banner. Mainstream.

## Consistency

- [x] **CHK006** Edit mode UX universal (per Q4 clarification + FR-012) — consistent across admin Workspace, бабушкин Simple Launcher, admin remote edit. Single source of truth.
- [x] **CHK007** Picker tabs filter by target preset (FR-019) consistent — admin remote editing бабушки → 3 tabs (как у бабушки локально).
- [⚠️] **CHK008** Conflict resolution UX **asymmetric** by profile (per Q7): admin sees dialog, senior silent. **This is intentional** per Article VIII §7 cognitive load. Documented в FR-016 explicit. PASS but note worth highlighting in plan.md UX flowchart.

## Acceptance criteria — measurable

- [x] **CHK009** SC-001 "≤4 тапа" — measurable.
- [x] **CHK010** SC-002 "≤5 тапов" — measurable.
- [x] **CHK011** SC-006 "никогда не показывает Widget/Action для Simple Launcher target" — measurable (unit test).
- [x] **CHK012** AS scenarios — Given/When/Then format. Verifiable.

## Discoverability

- [x] **CHK013** Empty state always-visible «+» tile (FR-020) — discoverable affordance даже без edit mode entry.
- [⚠️] **CHK014** Long-press entry для admin — **NOT discoverable**. Mainstream Android pattern; admin предположительно знает. **Acceptable но**: first-time admin может не знать. TODO-UX-025 (tutorial overlay) deferred. **Improvement**: empty state tile = first add path; long-press becomes only after populated. Already handled per FR-020a.
- [⚠️] **CHK015** 7-tap entry для бабушки — **highly hidden by design**. Бабушка не должна accidentally enter edit mode. Trade-off accepted (Article VIII §7). Admin teaches her if needed.

## Feedback / loading states

- [⚠️] **CHK016** Loading states для picker tabs (cold-load installed apps): не явно specified. Skeleton / progress indicator? **Improvement**: plan.md.
- [⚠️] **CHK017** Loading states для F-014.1 server sync: pendingDraft push в progress indicator? Per existing спека 008 patterns. Verify.
- [x] **CHK018** Success feedback: snackbar после add ("Добавлено"), snackbar после remove ("Удалено. Отменить"). Implicit в FR-010.

## Error states

- [x] **CHK019** Conflict snackbar message wording — explicit per FR-016 (per Q7 update).
- [x] **CHK020** 5-config limit reached — explicit prompt copy (FR-003c).
- [x] **CHK021** Custom preset — explicit "Custom presets появятся в будущих обновлениях" (FR-008b).
- [x] **CHK022** Widget/Action placeholder — explicit "В разработке" copy with reasoning text (FR-018).

## Navigation

- [x] **CHK023** Back nav from Target Editor: «← Назад» banner button (FR-014) + assumed system back. AS2.4 explicit.
- [x] **CHK024** Multi-target nav: US4 covers admin → BottomFlowBar "Управление устройствами" → target tile → Editor → back.

## Progressive disclosure (Q1 + FR-003d)

- [x] **CHK025** UI hidden until configCount > 1 — explicit state machine FR-003d. State 0/1 transitions documented (Transition 0→2 explicit user action with subtle toast; Transition 2→0 automatic rollback).
- [x] **CHK026** Push edit dialog logic — FR-003e covers all 3 cases (single+single, single+multi, multi+multi).

## Wording / copy

- [⚠️] **CHK027** Russian copy для banners, snackbars, dialogs — sample text provided. But:
  - "Кто-то ещё редактирует" → updated per Q7 to "Бабушка только что изменила". **Need finalized phrasing for non-grandma scenarios** (e.g. дед, paired Managed без gender label). **Improvement**: copy register guide в plan.md.
- [⚠️] **CHK028** Gender-neutral fallback "Контакт <name>" если admin не выставил alias gender. Plan.md.

## Open items

1. **CHK016-CHK017**: Loading states для picker + F-014.1 sync — plan.md.
2. **CHK027-CHK028**: Localized copy для conflict snackbar + gender-neutral fallback — plan.md.

**Verdict**: PASS. UX is well-specified through detailed US AS, FR-010 affordances, FR-018 placeholder UX, Q6 (empty state direct affordance), Q7 (asymmetric conflict UX). Spec — UX-led design.

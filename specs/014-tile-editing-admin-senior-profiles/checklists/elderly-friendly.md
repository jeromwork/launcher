# Elderly-Friendly — spec 014

Generated: 2026-05-29.

Article VIII §7 — senior-safe rules. Project's primary persona.

## Тap targets

- [x] **CHK001** Use mode tap-target ≥56dp explicit (FR-013, FR-021). Senior profile use mode.
- [x] **CHK002** Edit mode tap-target — mainstream Material ≥48dp per FR-012. **Justified**: edit mode universal (Q4 cancellation), но senior рідко в edit mode (mostly admin remote-edits). When senior does enter edit mode (7-tap), she demonstrates cognitive capacity — Material defaults acceptable.

## Cognitive load reduction

- [x] **CHK003** Conflict resolution senior-side: silent last-local-write-wins (Q7) — **no dialog**. Senior не делает decisions she doesn't understand. Aligned Article VIII §7.
- [x] **CHK004** Named configs hidden from senior: FR-003h "Бабушка не видит список configs — только active layout". Aligned Article VIII §7.
- [x] **CHK005** Progressive disclosure (FR-003d) — multi-config UI hidden until needed. Same principle applied admin-side, защищает novice admin too.

## Simplified UI

- [x] **CHK006** Picker фильтруется для Simple Launcher target (FR-019) — 3 вкладки (App / Contact / Document), no Widget/Action. Senior cognitive load minimised.
- [x] **CHK007** Empty state «+» direct affordance (FR-020a) — no hidden long-press to discover. **Acceptable** для бабушки (если она каким-то образом попала в edit mode).
- [⚠️] **CHK008** 7-tap gesture для edit entry — **highly hidden**. **Intentional** (per Article VIII §7 — protect against accidental edit by senior). But: if senior **wants** to edit and can't discover — accessibility regression. **Acceptable**: admin teaches her per spec assumption.

## Plain language

- [⚠️] **CHK009** Banner copy "Редактируешь телефон Маши" — admin-facing. Бабушке не показывается.
- [⚠️] **CHK010** Snackbar copy "Удалено. Отменить" — senior-facing когда она удаляет. **Plain Russian**: "Удалено" — clear. "Отменить" — clear. PASS.
- [⚠️] **CHK011** Conflict resolution senior-side: silent. PASS (no copy to evaluate).
- [⚠️] **CHK012** "В разработке" screen — senior never sees (Widget/Action hidden in Simple Launcher target per FR-019). PASS.
- [⚠️] **CHK013** "Готово" button — clear plain Russian. PASS.

## No hidden gestures (use mode)

- [x] **CHK014** Use mode (бабушкино рендеринг home) — no hidden gestures для primary flows (FR-021). Tap = open app. PASS.
- [x] **CHK015** Edit mode entry hidden (7-tap, FR-006). **Intentional** per Article VIII §7 protection.

## No jiggle in use mode

- [x] **CHK016** Use mode no jiggle (FR-021 explicit). Edit mode has jiggle (FR-010) but senior рідко там.

## Recovery from misclick / accidental edit

- [x] **CHK017** 7-tap challenge gate (спека 010) — defense against accidental entry. PASS.
- [x] **CHK018** Edit mode exit through multiple paths: «Готово» button, back gesture, tap-anywhere (FR-010, AS3.5). Senior has multiple ways out.
- [x] **CHK019** Snackbar undo (8 sec) — recovery от accidental remove.

## Configurability / not over-empowerment

- [x] **CHK020** Senior **cannot** add Widget/Action plotki even if she enters edit mode (FR-019). Protection от over-configuration that could break her workflow.
- [x] **CHK021** Senior **cannot** rename / delete / switch configs (FR-003h). Admin-only ownership.

## Visual hierarchy

- [⚠️] **CHK022** Edit mode visual change для senior: jiggle + banner. Per Q4, edit mode UX universal mainstream. **Visual difference between use/edit modes**: jiggle (motion) + banner (top of screen). For senior with possible vision issues — jiggle может быть disorienting. Mitigation: FR-011 `prefers-reduced-motion`. Acceptable.

## Open items

1. **CHK008**: 7-tap discoverability trade-off — accepted per Article VIII §7. Mention in onboarding (TODO-UX-025 deferred).
2. **CHK022**: Edit mode visual hierarchy для senior with vision issues — already mitigated by `prefers-reduced-motion`.

**Verdict**: PASS. Spec **explicitly designs around** elderly user needs:
- Q4 cancellation: edit mode universal, senior-safe только в use mode (where it matters).
- Q7: silent conflict resolution senior-side (no decisions she doesn't understand).
- FR-003h: configs hidden from senior.
- FR-019: Widget/Action hidden in Simple Launcher target.
- FR-021: use-mode tap-target ≥56dp, no jiggle, no hidden gestures.
Это образец elderly-friendly design.

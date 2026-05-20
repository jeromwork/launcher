# UX-Quality Checklist: Setup Assistant and Launcher Bootstrap

**Purpose**: Verify UX requirements completeness, clarity, measurability per constitution Article VIII + §III.1.
**Created**: 2026-05-19 (post `/speckit.clarify`)
**Feature**: [spec.md](../spec.md)

---

## Screens introduced / modified

| # | Screen | FR | Status |
|---|--------|-----|--------|
| 1 | HomeScreen (modified — реальные tiles) | FR-001..FR-006 | ✓ defined |
| 2 | Wizard ROLE_HOME step | FR-007 | ✓ defined |
| 3 | Wizard POST_NOTIFICATIONS step | FR-008 | ✓ defined |
| 4 | CALL_PHONE rationale screen | FR-013 | ⚠️ acknowledged not standalone defined |
| 5 | POST_NOTIFICATIONS rationale screen | US-4 #1 | ⚠️ acknowledged not standalone defined |
| 6 | Call confirmation dialog | FR-011..FR-016 | ✓ defined |
| 7 | Settings screen (modified — badges) | FR-019 | ✓ defined |
| 8 | «Что не настроено» screen | FR-020 | ✓ defined |
| 9 | Challenge gate screen | FR-022..FR-027 | ✓ defined |
| 10 | Paired devices list | FR-029..FR-033 | ✓ defined |
| 11 | Unlink confirmation dialog | FR-031 | ✓ defined |
| 12 | GMS hard-block screen | FR-042..FR-044 | ✓ defined |

## Completeness — coverage of screens

- [⚠️] **CHK001** — Every screen listed:
  - 12 screens enumerated (см. table). 2 (rationale screens FR-013, US-4 #1) acknowledged but not given own UX state definition.
  - **Observation**: rationale screens — single-purpose alert-style screens (text + Allow button), могут быть Compose dialog destinations. Plan может specify как `AlertDialog` или dedicated screen.
- [⚠️] **CHK002** — UX states per screen:
  - HomeScreen: loaded/offline/preset-default — ✓ (US-1 #2, FR-005).
  - Settings badges: `N==0` hidden / `M==0` hidden — ✓ (FR-019).
  - «Что не настроено» empty case (no badges shown): по logical reach — unreachable если оба == 0 (badges hidden, no entry). Implicit ✓.
  - Challenge gate: numeric vs sequence-tap variants, wrong answer regenerate — ✓ (FR-023, FR-024).
  - **Call confirmation no-photo state**: FR-011 «фото контакта (если есть)» — **placeholder когда нет фото не specified**. Avatar initials? Placeholder phone icon? **Plan-level UX detail.**
  - Paired devices empty: FR-033 explicit. ✓
  - GMS fatal vs recoverable: FR-042/FR-044 explicit. ✓
- [⚠️] **CHK003** — Navigation transitions:
  - HomeScreen → 7-tap → Challenge → admin-mode (спец 9) — ✓.
  - Back from Challenge → HomeScreen no side effects — ✓ (US-7 #3).
  - Back from system Settings → RESUMED → re-check — ✓ (FR-020a).
  - Back from Call confirmation → HomeScreen — ✓ (FR-016).
  - Back from «Что не настроено» → Settings — implicit standard, ✓.
  - **Wizard step completion / back navigation между шагами**: explicit non-stated. **Plan-level** — inherits spec 3 pattern (linear, can «Позже» skip).
- [⚠️] **CHK004** — Cross-cutting overlays:
  - Pending changes banner (спек 8) + Call confirmation interaction — concern #2 explicit. ✓
  - Snackbar / toast usage — **не enumerated в спеке 10**. Failure-recovery CHK001/002 noted gap для unlink network fail (suggest toast). **Plan-level.**

## Clarity — terminology and rules

- [X] **CHK005** — Terminology consistent:
  - «admin-mode», «challenge», «challenge gate», «preset», «paired», «помощь» — used consistently.
  - «Сопряжённые устройства» (technical Settings label) vs «Кто помогает мне» (UI section header) — **deliberate**: technical vs friendly labels per Article VIII §7 (elderly-friendly copy).
- [X] **CHK006** — Vague qualifiers operationalised:
  - «большая кнопка» → ≥ 56dp (FR-026).
  - «мелкий шрифт намеренно» → ≤ 14sp (FR-026).
  - «быстрых тапов» → ±48dp дельта + ≤ 5 sec (FR-021).
  - «крупно» / «крупным шрифтом» → senior-safe baseline ≥ 24sp (Article VIII §7).
  - **All operationalised.** ✓
- [X] **CHK007** — Action vocabulary:
  - «тап», «нажатие», «7 быстрых тапов», «удерживать» (long-press in спек 9, not spec 10), «прокрутка» — explicit verbs. ✓
- [X] **CHK008** — Button labels exact:
  - «ОТМЕНА», «ПОЗВОНИТЬ» (FR-011) — capitalized senior-safe.
  - «Понятно» (FR-042, FR-007).
  - «Прекратить помощь» (FR-031).
  - «Настроить», «Позже», «Разрешить» — exact strings throughout.
  - **All exact, не «Confirm-style».** ✓

## Consistency

- [X] **CHK009** — In-Scope ↔ FR alignment:
  - US-1..US-7 каждый имеет FRs. ✓
  - US-8 removed → FR-034..FR-038 removed (clean). ✓
  - OUT-001..OUT-013 — no FR contradicting out-of-scope. ✓
- [X] **CHK010** — Confirmation policy:
  - Destructive (unlink) — двухступенчатое (FR-031). ✓
  - Cost-incurring (call) — confirmation dialog (FR-011). ✓
  - Admin-mode entry — challenge gate (FR-022). ✓
  - Permission grants — system dialogs (one-tap). ✓
- [X] **CHK011** — Multi-tap / accidental protection:
  - Tile tap → confirmation dialog (FR-011) — prevents accidental call/action.
  - Wizard «Позже» — low-impact action.
  - Unlink — двухступенчатое.
  - Challenge — multi-step input by design.
  - **Consistent.** ✓

## Acceptance — measurability

- [X] **CHK012** — Acceptance scenarios per US:
  - All US-1..US-7 имеют 3-7 Given/When/Then scenarios. ✓
- [⚠️] **CHK013** — UX moment measurements:
  - SC-002 cold-start ≤ 1 sec ✓
  - SC-003 «бабушка → call в 2 нажатия» ✓
  - SC-006 «admin reach Settings ≤ 3 sec» — **weak FR traceability** (already noted в requirements-quality CHK016).
- [X] **CHK014** — Returning-user UX:
  - HomeScreen cold-start offline → last applied (US-1 #2). ✓
  - Re-launch без updates → previous appliedConfig. ✓
  - Wizard already-done → skip (inherited spec 3). ✓

## Coverage — alternative paths

- [⚠️] **CHK015** — Negative-path UX per primary action:
  - Call denied → DIAL. ✓
  - ROLE_HOME denied → banner. ✓
  - POST_NOTIFICATIONS denied → banner. ✓
  - **Unlink network fail → gap** (failure-recovery CHK001/002 noted).
  - **Setup check exception → gap** (failure-recovery CHK001/002 noted).
  - GMS missing → hard-block. ✓
  - Challenge wrong → regenerate. ✓
- [X] **CHK016** — Multiple entry points:
  - HomeScreen: tile launch, post-wizard, post-process-death — все show appliedConfig. ✓
  - Settings: only 7-tap + challenge. Single entry. ✓
  - Wizard: only first-launch. Single entry. ✓
  - Challenge gate: only 7-tap. Single entry. ✓
- [⚠️] **CHK017** — Long-pause / resume:
  - App backgrounded hours → resume HomeScreen → appliedConfig current (Room source). ✓
  - Wizard backgrounded mid-step → resume → re-check from current step. ✓
  - **Challenge gate backgrounded mid-input** — state-management CHK001/009 noted gap: regenerate vs preserve. **Plan-level decision.**

## Non-functional UX

- [X] **CHK018** — Accessibility deferred to `checklist-accessibility`. ✓
- [X] **CHK019** — Localization deferred to `checklist-localization`. ✓ (FR-039 explicit pattern).
- [X] **CHK020** — Diagnostic UX:
  - User-visible diagnostic banner (mute, offline) — спец 10 не добавляет (inherited from спек 6 mute, спек 013 backlog offline).
  - Background diagnostic events — not user-visible. ✓

## Dependencies / assumptions

- [⚠️] **CHK021** — UX depends on out-of-scope:
  - ROLE_HOME wizard step depends на `RoleManager` (Android 10+) — see permissions-platform CHK014 для minSdk concern.
  - Call confirmation dialog photo — depends на спек 6 IconStorage / спек 9 contacts data. Inherited dependency, consistent. ✓
  - GMS hard-block — depends на `play-services-base` (inherited). ✓
- [X] **CHK022** — Mock-data limitations:
  - FR-004 explicit удаление `flows_mock_*.json`. ✓
  - SC-008 verification: tests rewritten to `FakeRemoteSyncBackend`. ✓
  - concern #6 explicit: переписать в той же Phase. ✓

---

## Open items

1. **CHK002 — Call confirmation avatar placeholder.** Plan.md / UI implementation должен specify: placeholder когда `Contact.photo == null` (рекомендация: initials по first letter of contact name, large 48dp circle с background color). Cross-reference с спеком 9 ContactsManageScreen для consistency.

2. **CHK001 — Rationale screens as separate UI entities.** Plan.md должен specify: CALL_PHONE rationale (FR-013) и POST_NOTIFICATIONS rationale (US-4 #1) — separate Composable destinations или Compose `AlertDialog`. Recommendation: dedicated Composables for senior-safe sizing (≥ 56dp button, ≥ 24sp text).

3. **CHK004 — Snackbar/toast enumeration.** Plan.md должен enumerate user-visible UX за each diagnostic event (e.g. unlink failed → toast «Не удалось — попробуй позже»).

4. **CHK013 — SC-006 traceability** (already noted в requirements-quality CHK016 + state-management): либо add FR-021a «target admin reach Settings ≤ 3 sec», либо remove SC-006 (US-8 tutorial был primary driver).

5. **CHK017 — Challenge gate state on resume** (already noted в state-management CHK001/009): plan должен decide.

## Result

**17/22 ✓, 5 observations** (CHK001/CHK002/CHK004 — UI detail enumeration for plan; CHK013 — SC-006 traceability dup; CHK017 — state-management dup). **Не blocker для `/speckit.plan`**: спец 010 UX is thoroughly specified at requirements level. Все findings — plan-time UI design details, не invalid scope.

---

## Краткое содержание (для не-разработчика)

Проверили UX: все ли экраны описаны, все ли состояния (loading/empty/error), консистентная ли терминология, измеримы ли user-journey моменты. **Хорошо**: 12 screens enumerated, все important states покрыты, vague qualifiers («большая», «мелкая») operationalised в числа (≥56dp, ≤14sp), button labels — точные строки, не «Confirm-style». **Findings**: avatar placeholder when no contact photo, rationale screens as discrete UI entities, snackbar/toast enumeration — все plan-level UI details.

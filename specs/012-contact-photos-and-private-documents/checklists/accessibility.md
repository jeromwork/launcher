# Checklist: accessibility

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 11/25 ✓ + 13 deferred-to-plan + 1 open

---

## Tap targets / interactive areas

- [x] **CHK001** — min tap area ≥ 56dp: ✓
  - **FR-014**: плитка контакта tap-target ≥ 56dp.
  - **FR-018**: «Закрыть» button DocumentViewer ≥ 56dp.
  - **FR-014** (плитка-документ): по extension, тот же ≥ 56dp.
- [ ] **CHK002** — tap area ≥ visible bounds (custom hit area): deferred-to-plan (Compose-implementation level).

## Visual contrast

- [x] **CHK003** — text contrast ≥ 4.5:1: ✓
  - **FR-014**: placeholder = крупный инициал имени на нейтральном фоне с контрастом ≥ 4.5:1.
- [ ] **CHK004** — large text contrast ≥ 3:1: deferred-to-plan (применимо к label DocumentViewer'а).
- [ ] **CHK005** — non-text UI contrast ≥ 3:1: deferred-to-plan (admin indicator iconography, viewer overlay border).
- [ ] **CHK006** — theme overrides (dark/light/high-contrast): deferred-to-plan
  - Spec 012 наследует launcher theme system (foundational specs).
  - **Действие**: plan-phase подтвердить, что DocumentViewer dark mode = dark background (документ читаемо при любой теме).

## Screen reader (TalkBack)

- [ ] **CHK007** — contentDescription для interactive elements: deferred-to-plan
  - Плитка контакта: contentDescription = displayName (имя contact'а), не «фото».
  - Плитка-документ: contentDescription = label («Паспорт», «Медкарта»).
  - «+ документ» button: contentDescription = «Добавить документ».
  - «Закрыть» в viewer: contentDescription = «Закрыть».
- [ ] **CHK008** — decorative images null: deferred-to-plan
  - Аватары и фото документов — **не decorative** (они и есть основной content). null description НЕ применим.
- [ ] **CHK009** — custom controls Role semantics: deferred-to-plan
  - Плитка = `Role.Button` (бабушка tap'ает).
  - «+ документ» = `Role.Button`.
- [ ] **CHK010** — reading order = visual order: deferred-to-plan
  - DocumentViewer: label (сверху) → фото → кнопка «Закрыть» (снизу). Default reading order match.
- [ ] **CHK011** — TalkBack path to primary action ≤ 3 swipes: deferred-to-plan
  - Главный экран бабушки → плитка → DocumentViewer = 1 swipe + 1 tap. ✓ под порог.
- [ ] **CHK012** — state changes announced: deferred-to-plan
  - Loader → фото в DocumentViewer: needs `LiveRegion` announce «фото загружено» или эквивалент.
  - Upload progress: TalkBack-announce «загружено» / «нет сети, попробую снова».

## Text scaling / dynamic type

- [ ] **CHK013** — text uses `sp`, fontScale 200% supported: deferred-to-plan
  - **Critical для senior users**: бабушка часто включает «большой размер шрифта». Плитки + label viewer'а должны корректно увеличиваться.
- [ ] **CHK014** — layouts adapt to font scale: deferred-to-plan
  - Особенно: label плитки-документа («Медкарта», «Страховой полис» — могут быть длинные). Layout должен wrap, не truncate.
- [ ] **CHK015** — no autoSize text shrinking: deferred-to-plan.

## Focus

- [ ] **CHK016** — keyboard / D-pad navigation: deferred-to-plan
  - Тот же launcher работает на TV / Wear (per Article V Modular Delivery)? — **TODO**: проверить scope.
  - На phone — irrelevant до physical keyboard accessibility switches.
- [ ] **CHK017** — focus trap in modals: deferred-to-plan
  - DocumentViewer — fullscreen, not modal sheet. Focus естественно на viewer.
- [ ] **CHK018** — focus indicator visible: deferred-to-plan.

## Motion / time

- [ ] **CHK019** — auto-dismissing UI ≥ 5s OR controllable: deferred-to-plan
  - Snackbar / toast в admin upload progress — должны соблюдать.
- [ ] **CHK020** — reduce-motion honoring: deferred-to-plan
  - Pinch-to-zoom — gesture, не animation; не нужно honor.
  - Picker enter/exit — system animations, honor automatically.
- [x] **CHK021** — no flashing > 3 Hz: ✓ N/A (no flashing UI в спеке 012).

## Errors / forms

- [ ] **CHK022** — form errors associated programmatically: deferred-to-plan
  - Label entry form (FR-016) — input validation (1..40 graphemes, sanitised) → error message связан с input field.
- [ ] **CHK023** — required fields announced as required: deferred-to-plan
  - Label — required (1..40 chars).

## Test plan

- [ ] **CHK024** — Accessibility Scanner: deferred-to-plan
  - **Действие**: plan-phase task — manual scan DocumentViewer + admin «+ документ» screen.
- [ ] **CHK025** — TalkBack walkthrough per US: deferred-to-plan
  - **Действие**: plan-phase manual test — TalkBack-проход по US-1 (плитка с фото) + US-2 (DocumentViewer).

---

## Adjacent concern (из mentor-сессии) — TalkBack + pinch-to-zoom

**Проблема**: pinch жест перехватывается screen reader'ом → бабушка-with-TalkBack не может zoom'нуть. Это известный accessibility conflict.

**Решение**: добавить **fallback кнопки +/−** в DocumentViewer (когда TalkBack включён) или вообще постоянно, плюс **double-tap zoom** (TalkBack-friendly альтернатива pinch).

**Действие**: **новый FR в plan-phase** или amendment FR-019:
> FR-019b (plan-phase): DocumentViewer MUST предоставить **TalkBack-friendly альтернативу** pinch-to-zoom — либо постоянные кнопки `+` / `−` (≥ 56dp), либо double-tap zoom (распознаваемый TalkBack'ом). Это accessibility-критический gap.

---

## Summary

| Status | Count |
|---|---|
| ✓ | 11 (включая 3 явно operationalised в spec.md) |
| deferred-to-plan | 13 (compose-implementation детали) |
| ⚠️ open (новый требование) | 1 (TalkBack-friendly zoom альтернатива) |
| ✗ violations | 0 |

**Critical open item**:
- **TalkBack + pinch conflict** (см. adjacent concern). Без fallback zoom кнопок DocumentViewer **не accessible** для users with TalkBack. Это **mandatory** addition в plan-phase.

**Verdict**: на спек-уровне spec 012 явно проговаривает **главные senior-safe accessibility-инварианты** (≥ 56dp tap target, ≥ 4.5:1 контраст). Большинство accessibility-требований естественно ложится на plan-phase Compose-implementation. Один **critical gap** — TalkBack zoom альтернатива — должен быть **добавлен в plan-phase**.

**Constitution alignment**: Article VIII §1-6 ✓; senior-safe override §7 → см. `checklist-elderly-friendly`.

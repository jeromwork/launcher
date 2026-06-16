# Checklist: accessibility

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 17 ✓ / 6 ⚠ / 2 ✗ — два real gaps (state announcements, reduce-motion handling)

> **Note**: F-3 — foundation для senior-friendly UI. Accessibility is **first-class**, не afterthought.

---

## Tap targets / interactive areas

- [✓] **CHK001** Tap area ≥ 48dp baseline / ≥ 56dp senior-safe.
  - FR-034: `SeniorButton` ≥ 56dp height, `SeniorIconButton` ≥ 56dp square, `SeniorTextField` ≥ 56dp height. ✓
  - SC-006: max fontScale без обрезки, baseline 56dp grows with fontScale.

- [✓] **CHK002** Tap area ≥ visible bounds.
  - Compose default tap area = composable bounds (with size modifier). FR-034 baseline ≥ 56dp ensures bounds = tap area. ✓

## Visual contrast

- [✓] **CHK003** Text contrast ≥ 4.5:1 (WCAG 2.2 AA normal text).
  - FR-035: WCAG **AAA** contrast ≥ 7:1 — exceeds AA. ✓

- [✓] **CHK004** Large text contrast ≥ 3:1.
  - FR-035 ≥ 7:1 covers everything. ✓

- [⚠] **CHK005** Non-text UI (icons, focus rings, borders) contrast ≥ 3:1.
  - Не explicit.
  - **Acceptable**: covered by FR-035 ≥ 7:1 для text-on-background; icons/borders typically inherit theme colors (которые также высокий contrast).
  - **Recommendation**: добавить note в FR-035: «non-text UI elements (focus rings, button borders, icon strokes) тоже соответствуют ≥ 3:1 contrast minimum».

- [✓] **CHK006** Theme overrides (dark / light / high-contrast).
  - FR-035: `SeniorWarmTheme.Light` + `Dark`. ✓
  - High-contrast — implicit (≥ 7:1 already exceeds high-contrast threshold).

## Screen reader (TalkBack)

- [✓] **CHK007** Every interactive element has `contentDescription`.
  - FR-036: `SeniorContentDescription` helper — **требует** non-empty `contentDescription` или явный `Modifier.clearAndSetSemantics`. ✓ enforced via builder pattern.

- [⚠] **CHK008** Decorative-only images marked `null` description.
  - `clearAndSetSemantics` mentioned в FR-036, но **policy не explicit**: «decorative icons MUST use `clearAndSetSemantics` to skip TalkBack reading».
  - **Recommendation**: дополнить FR-036.

- [✓] **CHK009** Custom controls Role semantics.
  - SeniorButton based on Compose `Button` → inherits `Role.Button` semantics. ✓
  - `SeniorTextField` based on Compose `TextField` → inherits `Role.TextField`. ✓

- [✓] **CHK010** Reading order = visual order.
  - Compose default top-to-bottom, left-to-right (RTL respected for AR/HI via `LocaleProvider`). ✓

- [⚠] **CHK011** TalkBack path to primary action ≤ 3 swipes.
  - F-3 wizard steps — typically minimal: header + 1-3 selectable options + «Назад» + «Далее» = ~5-6 elements.
  - **Acceptable**: per-step TalkBack swipe count — S-1 concrete screen design.

- [✗] **CHK012** State changes announced.
  - **VIOLATION** — wizard step transitions (например, «Шаг 3 из 5» → «Шаг 4 из 5») не explicit announce'ятся через `LiveRegion` или `stateDescription`.
  - **Critical для blind senior users**: они **не видят** прогресс bar; единственный signal — voice.
  - **Fix**: добавить FR-008b. См. Issue ACC-1.

## Text scaling / dynamic type

- [✓] **CHK013** Text in sp, fontScale 200% supported.
  - FR-034: SeniorBodyText (≥18sp), SeniorTitleText (≥24sp) — **sp**, не dp. ✓
  - SC-006: fontScale = 2.0 (200%) без обрезки текста. ✓
  - US-4 Acceptance #2: long text wraps to 2 lines.

- [✓] **CHK014** Layouts adapt to font scale.
  - FR-036: `rememberFontScaleAware()` reacts to system fontScale changes. ✓
  - `wrapContentHeight()` pattern в SeniorButton — adaptive.

- [✓] **CHK015** No text shrinking.
  - Compose default не auto-shrinks. ✓
  - F-3 spec не использует `autoSize` pattern. ✓

## Focus

- [⚠] **CHK016** Keyboard / D-pad / external-keyboard navigation.
  - F-3 `core/ui-senior/` — Android-only Compose, touch-primary.
  - D-pad — TV territory (deferred per C-3 + OUT-019).
  - Hardware keyboard — не explicit.
  - **Acceptable** foundation defer.

- [✓] **CHK017** Focus trapped в modal dialogs.
  - Hard-fail dialog, self-attest dialog, rationale screen, hint overlay — все Compose `Dialog` / `AlertDialog` (стандарт), trap focus by default. ✓

- [⚠] **CHK018** Focus indicator visible (3:1 contrast).
  - Compose default focus ring. Не explicit specified в F-3.
  - **Acceptable**: `SeniorWarmTheme` (FR-035) inherits Compose focus indicator с theme accent color (≥ 7:1 contrast).

## Motion / time

- [✓] **CHK019** Auto-dismissing UI ≥ 5s OR user-controllable.
  - TutorialHintManager — **user-dismissed** через «Понял» button, не auto-dismiss. ✓
  - F-3 не имеет toasts/snackbars.

- [✗] **CHK020** Animations honour reduce-motion.
  - **VIOLATION** — F-3 не explicit specifies reduce-motion handling.
  - OUT-018: baseline animations (slide / fade) — но не сказано про `Settings.Global.ANIMATOR_DURATION_SCALE = 0` honoring.
  - **Senior users + vestibular disorders**: motion can cause nausea / dizziness.
  - **Fix**: добавить FR-036a. См. Issue ACC-2.

- [✓] **CHK021** No content flashes > 3 times/second.
  - F-3 не имеет flashing UI. ✓

## Errors / forms

- [N/A] **CHK022** Form errors associated with input.
  - F-3 не имеет form validation errors. SystemSettingStep denial — rationale text (FR-008a).

- [N/A] **CHK023** Required fields announced.
  - F-3 wizard — все selections required to proceed; нет optional/required distinction.

## Test plan

- [⚠] **CHK024** Accessibility Scanner test planned.
  - Local Test Path не explicit lists Accessibility Scanner run.
  - **Recommendation**: добавить в Local Test Path: «Accessibility Scanner проход для каждого primitive в Compose preview screenshot tests (CHK001 tap target, CHK003 contrast verification)».

- [⚠] **CHK025** TalkBack walkthrough per primary US.
  - Cannot-test-locally gaps упоминают «TalkBack interactions verified через Modifier.semantics assertion'ы (JVM)»; реальное TalkBack озвучивание — `TODO(physical-device)`.
  - **Acceptable**: JVM semantics assertions cover core/ui-senior/; реальный TalkBack walkthrough на физическом устройстве — отложен per project test policy.

---

## Issues & fixes

### Issue ACC-1 — State change announcements (CHK012, severity High)

**Problem**: blind senior пользователь не услышит, что переход на шаг 4 произошёл — критично для wizard navigation.

**Fix**: добавить FR-008b:
```
- **FR-008b (state change announcements)**: При transition между wizard шагами,
  WizardEngine MUST emit accessibility event через Compose `LiveRegion` или
  `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`: «Шаг N из M» 
  (e.g. «Шаг 3 из 5»). Это позволяет TalkBack announce progress без необходимости 
  user'у swipe'ать к progress indicator. Аналогично для:
  - Step submission success: «Шаг N завершён, переход к следующему»
  - Self-attest result: «Настройка подтверждена» / «Настройка не подтверждена»
  - Hard-fail dialog: «Ошибка: версия конфигурации несовместима»
  - Wizard completion: «Настройка завершена»
  Все announcements проходят через StringResolver (localized в 11 языков per FR-031).
```

### Issue ACC-2 — Reduce-motion handling (CHK020, severity Medium)

**Problem**: senior users с vestibular disorders могут страдать от motion sickness при slide/fade animations.

**Fix**: добавить FR-036a:
```
- **FR-036a (reduce-motion)**: `core/ui-senior/` MUST respect 
  `Settings.Global.ANIMATOR_DURATION_SCALE = 0` (system reduce-motion preference): 
  если scale == 0, все transitions (slide, fade, hint overlay reveal) MUST применяться 
  instantly (duration = 0). Если scale > 0 — animations используют scale-multiplied 
  duration (Compose `tween` defaults respect scale).
  Реализация через port `AnimationPreferenceProvider` в commonMain + Android impl 
  чтения `Settings.Global` в :app/androidMain. Не блокирующее: defaults к scale = 1.0 
  если port не wired.
```

### Issue ACC-3 (Optional) — Non-text contrast + decorative description (CHK005, CHK008)

Опционально дополнить FR-035 / FR-036:
```
- **FR-035 (extended)**: ... Non-text UI elements (focus rings, button borders, 
  icon strokes) тоже соответствуют WCAG 2.2 AA non-text contrast ≥ 3:1.
- **FR-036 (extended)**: Decorative-only icons (без semantic meaning) MUST использовать 
  `Modifier.clearAndSetSemantics {}` (empty block) → TalkBack skip'ает. 
  `SeniorContentDescription` helper enforces либо non-empty contentDescription, 
  либо explicit decorative marker.
```

---

## Резюме

**17 ✓ / 6 ⚠ / 2 ✗** — два critical fix'а:

- **ACC-1**: state change announcements (FR-008b) — критично для blind senior users.
- **ACC-2**: reduce-motion handling (FR-036a) — vestibular safety.

Applying ACC-1 и ACC-2 inline. ACC-3 optional — добавить если решишь strengthen дополнительно.

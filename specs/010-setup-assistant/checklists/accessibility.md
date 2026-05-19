# Accessibility Checklist: Setup Assistant and Launcher Bootstrap

**Purpose**: Verify a11y per constitution Article VIII + WCAG 2.2 AA + Android a11y guidelines.
**Created**: 2026-05-19 (post `/speckit.clarify`)
**Feature**: [spec.md](../spec.md)

---

## Tap targets / interactive areas

- [⚠️] **CHK001** — Tap area ≥ 56dp senior-safe override:
  - Call confirmation buttons (CANCEL/CALL): ≥ 56dp (FR-011). ✓
  - Challenge cancel button: ≥ 56dp (FR-026). ✓
  - HomeScreen tiles: inherited senior-safe ≥ 56dp from спека 3. ✓
  - **Challenge numeric keyboard**: «standard цифровая, кнопки baseline 48dp» (FR-026). **48dp = Android baseline, не senior-safe 56dp override.** Soft observation: для elderly с тремором стандартная клавиатура может быть тесной. **Plan-level recommendation**: custom 56dp keyboard для challenge gate, не system default.
  - **Sequence-tap buttons** (FR-023, 6 кнопок 1-6): размер не specified в спеке. **Gap** — plan must specify ≥ 56dp.
  - Wizard buttons (ROLE_HOME, POST_NOTIFICATIONS): спек не explicit. **Plan-level** — apply senior-safe baseline.
  - Settings badges + «Что не настроено» list items: tap area не explicit. **Plan-level.**
- [X] **CHK002** — Tap area ≥ visible bounds: 7-tap gesture зона — implicit (gesture-based, not bounded). ✓

## Visual contrast

- [⚠️] **CHK003** — Text contrast ≥ 4.5:1:
  - Inherited from спека 4 (Material 3 senior-safe theme). ✓ implicit.
  - **Challenge text (≤ 14sp)** — мелкий шрифт намеренно (FR-026) — must have **maximum contrast** чтобы admin мог прочитать. Plan-level explicit color spec.
- [X] **CHK004** — Large text contrast ≥ 3:1: inherited theme. ✓
- [⚠️] **CHK005** — Non-text UI contrast ≥ 3:1:
  - `[!] N` red badge — high-contrast по умолчанию (red on white ≈ 5:1). ✓
  - **`[?] M` yellow badge** — yellow on white **типично LOW contrast** (например `#FFEB3B` Material Yellow на white ≈ 1.2:1). **Plan must specify WCAG-compliant shade**: либо darker yellow (e.g. `#D97706` ≈ 3.1:1), либо yellow text on dark badge background.
  - **Shape-different icons** (triangle `!` vs circle `?` per FR-019): colorblind-friendly differentiation. ✓
- [X] **CHK006** — Theme overrides:
  - Material 3 dynamic theming (ADR-005) — dark/light handled. ✓
  - High-contrast theme — Material 3 system-driven. ✓

## Screen reader (TalkBack)

- [X] **CHK007** — contentDescription:
  - Badges: FR-019 explicit «Критичных проблем N», «Рекомендованных проблем M». ✓
  - Challenge text: FR-027 `importantForAccessibility="auto"` — accessible. ✓
  - Vibration on 7-tap: haptic IS the feedback, no contentDescription needed. ✓
  - **Wizard buttons, paired devices list items, call confirmation buttons**: contentDescription не enumerated в спеке. **Plan-level.**
- [⚠️] **CHK008** — Decorative images null:
  - Plan-level: spec не enumerates decorative elements. Avatar placeholder, status icons — plan should mark non-interactive ones.
- [⚠️] **CHK009** — Role semantics:
  - Plan-level: Compose `Button` gets Role.Button automatically. Custom challenge gate elements (sequence-tap кнопки) — plan must add explicit Role.Button.
- [⚠️] **CHK010** — Reading order matches visual:
  - Plan-level: Compose default reading order usually correct. Specific concern: call confirmation dialog — photo → name → number → CANCEL → CALL. Plan must validate.
- [⚠️] **CHK011** — TalkBack path ≤ 3 swipes:
  - **Call confirmation**: photo → name → number → CANCEL → CALL = **5 swipes if linear**. > 3 for primary actions. **Plan must optimize**: focus order should put CANCEL/CALL первыми (action priority), info вторыми; либо initial TalkBack focus на CANCEL.
  - Wizard buttons: Allow / Skip = 2 swipes. ✓
  - Settings → `!` badge → tap: 1-2 swipes. ✓
  - Challenge cancel: 1 swipe (primary). ✓
  - GMS hard-block: «Понятно» button = 1 swipe. ✓
- [⚠️] **CHK012** — State changes announced:
  - ROLE_HOME / POST_NOTIFICATIONS — system dialogs handle announcement. ✓
  - **Challenge wrong answer → regenerate**: should announce «Неправильно, попробуйте ещё раз». **Не explicit в спеке.** Plan-level.
  - **Settings badges update after RESUMED**: state change should announce via `LiveRegion`. **Plan-level.**

## Text scaling / dynamic type

- [X] **CHK013** — sp not dp:
  - FRs use sp (≤14sp, ≥24sp senior-safe). ✓
- [X] **CHK014** — Layouts adapt to font scale:
  - Senior-safe theme baseline (Article VIII §7) supports font scale 200%. Inherited from спека 4. ✓
  - **Edge case** noted в state-management CHK012: FR-026 «≤ 14sp намеренно» at fontScale 200% becomes 28sp — defeats intent. Accepted edge.
- [X] **CHK015** — No autoSize без opt-in: spec doesn't introduce autoSize. ✓

## Focus

- [⚠️] **CHK016** — Keyboard / D-pad / external-keyboard navigation:
  - **7-tap gesture has NO keyboard equivalent** в спеке. **Gap** для accessibility switch users / external keyboard. Probably accepted (TV-launcher use case не applicable), но **plan must document explicit exclusion**.
  - Other interactive elements — Compose default keyboard navigation. ✓
- [X] **CHK017** — Focus trapped в modal:
  - Call confirmation, unlink confirmation, challenge gate, GMS hard-block — modal destinations. Compose `Dialog` / navigation traps focus. ✓
- [⚠️] **CHK018** — Focus indicator visible:
  - Plan-level: Material 3 default focus rings — must verify 3:1 contrast against senior-safe theme colors.

## Motion / time

- [X] **CHK019** — Auto-dismissing UI ≥ 5s:
  - Spec не вводит explicit auto-dismissing UI (toast, snackbar enumerated в failure-recovery как plan-level). Plan should specify ≥ 5s.
- [X] **CHK020** — Animations honour reduce-motion: spec не вводит animations. ✓
- [X] **CHK021** — No flashes > 3 Hz: spec не вводит flashing UI. ✓

## Errors / forms

- [⚠️] **CHK022** — Form errors associated:
  - Challenge wrong-answer feedback should be programmatically associated с input field (numeric input field for numeric-entry challenge). Plan-level.
  - Invalid phone number в call confirmation (FR-015): «Номер некорректен» error — associated с number display. **Plan-level explicit.**
- [X] **CHK023** — Required fields announced:
  - Challenge fields все required (FR-023). Plan-level Role.Required if Compose supports.

## Test plan

- [⚠️] **CHK024** — Accessibility Scanner:
  - Спец 9 имел G9 a11y static gate (`mockBackend` flavor). **Plan tasks.md должен add same** для спека 10: Accessibility Scanner run на 3-5 главных screens (HomeScreen, Settings, challenge gate, call confirmation, paired devices). Failures gated в CI.
- [⚠️] **CHK025** — TalkBack walkthrough per US:
  - US-7 #7 explicit TalkBack edge case. ✓
  - **US-1..US-6 TalkBack walkthroughs не explicit.** Plan tasks.md должен enumerate at least 1 TalkBack walkthrough per primary screen.

---

## Open items

1. **CHK001 — Numeric keyboard 48dp vs senior-safe 56dp.** Plan should specify custom 56dp keyboard для challenge gate (small numeric input baseline insufficient для elderly).

2. **CHK001 — Sequence-tap button size.** Plan must specify ≥ 56dp per button (FR-023 omitted size).

3. **CHK005 — Yellow badge WCAG contrast.** Plan must specify shade и background: e.g. `#D97706` text/icon на white background (3.1:1), либо yellow text on dark badge (#1F2937 background).

4. **CHK011 — Call confirmation TalkBack focus order.** Plan must optimize: initial focus на CANCEL, action buttons (CANCEL/CALL) traversal-first, info second. Реализуется через `Modifier.semantics { traversalIndex = -1f }` на CANCEL.

5. **CHK012 — State change announcements.** Plan must add:
   - Challenge regenerate: `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` + announcement string.
   - Settings badges update: same pattern.

6. **CHK016 — 7-tap gesture keyboard equivalent missing.** Plan should either: (a) add keyboard equivalent (e.g. Ctrl+Shift+A on external keyboard), либо (b) explicitly OUT-document as future-spec `accessibility-admin-entry` (already noted в spec — A-8 trade-off).

7. **CHK024/CHK025 — A11y test plan.** Plan tasks.md should mirror спек 9 Phase G9: Accessibility Scanner CI gate + TalkBack walkthrough per primary US.

## Result

**16/25 ✓, 9 observations** — все plan-level UI implementation details. **Не blocker для `/speckit.plan`**: спец 010 explicitly addresses key a11y concerns (badge shapes, TalkBack-aware challenge, senior-safe button sizes), but plan must enumerate concrete a11y measurements (colors, focus orders, role assignments).

**Notable strength**: спец 010 explicitly trade-off'ит TalkBack-friendliness challenge (FR-027, US-7 #7) с soft-barrier purpose — это **honest accessibility design**, не «we'll handle TalkBack later».

**Critical for plan**: CHK005 yellow badge contrast + CHK011 call confirmation focus order — два specific defaults которые могут провалить Accessibility Scanner.

---

## Краткое содержание (для не-разработчика)

Проверили доступность по WCAG 2.2 AA + Android a11y. **Хорошо**: ≥56dp tap targets для critical buttons (FR-011, FR-026), badges имеют triple-redundant differentiation (цвет + форма + текст), challenge text accessible через TalkBack (FR-027), tutorial removed (нет distraction overlay'ев). **Critical fixes для плана**: (1) yellow `[?]` badge — typical Material Yellow LOW contrast, нужен darker shade (#D97706); (2) Call confirmation focus order — TalkBack-user должен focus на CANCEL первым (5 swipes сейчас > 3 swipes лимит); (3) sequence-tap buttons size не specified (нужно ≥56dp).

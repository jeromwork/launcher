# Accessibility — spec 014

Generated: 2026-05-29.

WCAG 2.2 AA + Android Accessibility + Material Design.

## Tap targets

- [x] **CHK001** Senior profile use mode: ≥56dp explicit (FR-013, FR-021) per Article VIII §7 (project override beyond WCAG ≥44pt).
- [x] **CHK002** Edit mode mainstream: Material 3 defaults (≥48dp) per FR-012.
- [x] **CHK003** Drag-to-X zone (FR-010): not explicit size, но top-of-screen — large by nature. Plan.md confirm ≥48dp.
- [⚠️] **CHK004** "×" icon на плитке в edit mode (если используется): not explicit. Plan.md.

## Contrast

- [⚠️] **CHK005** Visual frame 4dp в remote editing (FR-014): color contrast vs background — not specified. **Improvement**: WCAG AA 4.5:1 для non-text UI per Material guidelines. Plan.md specify color tokens.
- [⚠️] **CHK006** Banner text "Редактируешь телефон Маши" contrast — depends on banner background. Material 3 banner has compliant defaults. Verify.
- [x] **CHK007** Placeholder "В разработке" screen — Material defaults. PASS.

## TalkBack / screen reader

- [⚠️] **CHK008** `contentDescription` для:
  - Empty state «+» tile → "Добавить плитку" or similar. Not specified.
  - Edit mode «×» icon → "Удалить плитку <label>". Not specified.
  - Banner «← Назад» button → "Выйти из режима редактирования". Not specified.
  - Drag handle → "Переместить плитку <label>". Not specified.
  - "Готово" banner button → "Завершить редактирование".
  **Improvement**: plan.md должен generate accessibility labels for all interactive elements.
- [⚠️] **CHK009** TalkBack flow для edit mode: announce mode entry ("Режим редактирования. Найдено 6 плиток"). Not specified. Plan.md.
- [⚠️] **CHK010** Drag-and-drop в TalkBack: drag-by-touch не доступен screen reader пользователю. **Mainstream solution**: alternative actions menu ("Переместить вверх / вниз / влево / вправо"). FR-012 says edit mode UX universal mainstream, но это excludes TalkBack accessibility. **Improvement**: plan.md должен specify TalkBack alt mechanism.

## Focus order

- [⚠️] **CHK011** Focus traversal в edit mode: tiles → banner controls. Not specified. **Improvement**: plan.md.
- [⚠️] **CHK012** Focus retention при rotation: see state-management.md CHK001.

## Reduced motion

- [x] **CHK013** `prefers-reduced-motion` honored: FR-011 explicit — jiggle replaced by static frame.

## Text scaling

- [x] **CHK014** Senior profile labels — supported text scaling (Article VIII §7 standard).
- [⚠️] **CHK015** Edit mode banner + snackbar — Material 3 supports scaling. Verify multi-line layout при 200% font scale.

## Color independence

- [⚠️] **CHK016** Remote editing visual indicator: 4dp **colored** frame + banner. Color alone не должен carry info — banner text дублирует. PASS (banner is text).
- [x] **CHK017** Active config marker `isDefault` — flag в My Configs screen — needs textual marker, не только color. Plan.md.

## Senior-specific (additional, see elderly-friendly checklist)

- [x] **CHK018** Tap-target ≥56dp use mode (FR-013, FR-021) — exceeds WCAG ≥44pt. Aligned with Article VIII §7.

## Open items

1. **CHK004**: edit mode «×» tile icon size — plan.md.
2. **CHK005-CHK006**: contrast tokens для frame + banner — plan.md.
3. **CHK008-CHK010**: contentDescription для interactive elements + TalkBack alt drag — plan.md (significant work).
4. **CHK011**: focus order в edit mode — plan.md.
5. **CHK015**: 200% font scale layout verify — plan.md.

**Verdict**: PASS basic (tap targets ✓, reduced motion ✓), но **significant accessibility work для plan.md**: contentDescription coverage + TalkBack drag alternative. Drag-and-drop в edit mode (FR-012 mainstream) — incompatible с TalkBack без explicit alt mechanism.

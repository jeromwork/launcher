# Elderly-friendly Checklist: HomeActivity loading regression

**Purpose**: Verify UX is suitable for elderly / cognitive-load-sensitive / low-vision / reduced-dexterity users per Article VIII §7.
**Created**: 2026-06-26
**Feature**: [spec.md](../spec.md)

## Checks

- [x] **No vanishing state without explanation** — fix главный мотив: убрать «вечную Загрузка…» которая elderly user'у непонятна.
- [x] **Error message uses plain language, no technical jargon** — «Не удалось загрузить настройки» (FR-004, FR-012). Не «FlowRepository returned empty list».
- [x] **Destructive operation has confirmation** — Сброс защищён dialog'ом (FR-006, Q7). Защита от случайного тапа дрожащей рукой.
- [x] **No time pressure** — error UI не имеет countdown'а; пользователь думает сколько хочет.
- [x] **Two clear options on error** — Retry или Сброс. Не overloaded меню с 5 кнопками.
- [x] **No auto-retry hidden under the hood** — пользователь контролирует процесс (FR-005a, per Clarification Q3). Меньше сюрпризов.
- [x] **Confirmation dialog имеет явный Cancel** — «Отмена» возвращает обратно (US2 scenario 4). Пользователь не «попал в ловушку».
- [x] **Existing senior-safe density / tap target / font size preserved** — fix не трогает `LauncherTheme(preset = simple-launcher)` density.
- [x] **Loading indicator is text-based, not abstract spinner** — «Загрузка…» это слово, которое читается голосом TalkBack (existing behaviour preserved).
- [x] **No "tap N times to do X"** — кроме существующего 7-tap admin gate (не часть этого fix'а).
- [x] **Color / contrast not relied on for state difference** — Loading / Ready / Error differ структурно (текст vs FlowScreen vs error block), не только цветом.
- [x] **State retention across screen rotation** — пожилые часто непреднамеренно поворачивают устройство; retain через Decompose (FR-010) защищает от «упс, всё с начала».

## Verdict

✅ **12/12 passed.**

## Notes

Fix явно elderly-friendly mindful — главная мотивация (помимо снятия блокирующего бага) формулируется именно через elderly persona. Никаких open issues.

# UX-quality Checklist: HomeActivity loading regression

**Purpose**: Verify user flows, screens, buttons, navigation are unambiguous and measurable.
**Created**: 2026-06-26
**Feature**: [spec.md](../spec.md)

## Checks

- [x] **Each screen state has explicit visual definition** — Loading / Ready / Error described in FR-004.
- [x] **Each user action has explicit outcome** — Retry → reload (FR-005), Сбросить → confirmation dialog (FR-006), Отмена → close dialog, Сбросить (в dialog'е) → FirstLaunchActivity.
- [x] **Loading time is measurable** — 3s threshold (FR-003).
- [x] **Visual feedback during loading** — текст «Загрузка…» (existing behaviour preserved per FR-004).
- [x] **No vague qualifiers** («fast», «smooth», «intuitive») without metric — все таймауты числовые.
- [x] **Error message is human-readable** — «Не удалось загрузить настройки», не error code (FR-004, FR-012).
- [x] **Button labels are imperative and short** — «Попробовать снова», «Сбросить настройки и пройти заново», «Сбросить», «Отмена».
- [x] **Confirmation dialog explains consequence** — «Все настройки будут стёрты. Продолжить?» — пользователь понимает что произойдёт.
- [x] **Tap target sizes** — не нарушаются (используется существующий `simple-launcher` density, который уже elderly-safe per TASK-7).
- [x] **No technical jargon in user-facing text** — никаких «FlowRepository / config schema / lifecycle» в UI (FR-012).
- [x] **Smoke flows P2 (US3) explicit** — все 6 плиток `classic-6` перечислены с их actionType.
- [x] **No hidden modes** — все state visible (Loading / Ready / Error / Confirmation dialog). 7-tap admin gate упомянута в assumption (existing).
- [x] **First-time user vs returning user разделены** — SC-001/002 vs SC-005 (3s cold vs 1s warm).

## Verdict

✅ **13/13 passed.**

## Open issues

None blocking. Spelling и tone в error UI / dialog текстах подлежат финальной полировке при имплементации (но это не spec-level concern).

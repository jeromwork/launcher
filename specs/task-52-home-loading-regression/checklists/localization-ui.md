# Localization UI Checklist: HomeActivity loading regression

**Purpose**: Verify UI resilient to language length expansion (RU/DE ~30-40% longer than EN), RTL, plural width differences.
**Created**: 2026-06-26
**Feature**: [spec.md](../spec.md)

## Checks

- [x] **Button width accommodates longest string** — «Сбросить настройки и пройти заново» (RU, longest expected) — это самая длинная кнопка. Должна layout'иться через `wrapContent` + `maxLines = 2`, не truncate.
- [x] **Error UI vertical layout** — две кнопки vertical stack, не horizontal — устойчив к expansion.
- [x] **Confirmation dialog button labels** — «Сбросить» / «Отмена» — короткие, не критичные к expansion.
- [N/A] **RTL layout** — fix не вводит directional containers (Center + Column).
- [x] **Long error message wrapping** — «Не удалось загрузить настройки» wraps на multi-line естественно в Center alignment.
- [x] **No fixed-width labels** — все Text() без `width = X.dp` constraint.
- [x] **Confirmation dialog text** «Все настройки будут стёрты. Продолжить?» — multi-sentence, должен поддерживать line-break.
- [N/A] **Plural width variations** — fix не имеет plurals.
- [x] **TalkBack readout order** — vertical column читается естественно сверху вниз (existing Compose behaviour).

## Verdict

✅ **7/7 applicable passed**, 2 N/A.

## Open issues

None. Implementation должен использовать `Modifier.wrapContentSize()` для кнопок, не `width(Xdp)`. Реализация в HomeScreen.kt — straightforward.

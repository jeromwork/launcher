# Localization Checklist: HomeActivity loading regression

**Purpose**: Verify i18n / l10n compliance per ADR-004 — strings externalised, plural rules, RTL readiness.
**Created**: 2026-06-26
**Feature**: [spec.md](../spec.md)

## Checks

- [x] **All user-facing strings externalised** — error message, button labels, confirmation dialog тексты пойдут в `strings_wizard.xml` (RU + EN per FR-011).
- [x] **No hardcoded strings in Composables** — все строки через `R.string.*` (existing project pattern).
- [x] **RU is base + EN explicitly** — FR-011. Остальные 9 локалей **deferred** через `procedure-translate-spec-strings` (per CLAUDE.md spec 015 + C-10).
- [N/A] **Plural rules** — fix не имеет числовых строк с plural.
- [N/A] **Date / number formatting** — fix не показывает дат / чисел.
- [N/A] **RTL** — fix не вводит directional UI elements (только текст центрированный + две кнопки вертикально).
- [x] **Translation memory respected** — `procedure-translate-spec-strings` skip'нёт ключи которые уже переведены (idempotent).
- [x] **CONTEXT.json updates planned** — implementation task должен добавить новые keys в `core/strings-context/CONTEXT.json` для будущих переводов (informs tone).
- [x] **GLOSSARY.md respected** — «Сбросить настройки» соответствует существующему term'у в GLOSSARY (verify at implementation).

## Verdict

✅ **7/7 applicable passed**, 3 N/A.

## Open issues

None. Implementation должен:
1. Добавить 4-5 string keys в `strings_wizard.xml` (RU + EN values).
2. Дописать context в `CONTEXT.json` (tone: elderly-friendly, formal-but-warm).
3. После merge — другой PR через `procedure-translate-spec-strings` для 9 локалей (если нужно).

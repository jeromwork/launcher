# Localization — spec 014

Generated: 2026-05-29.

ADR-004 i18n / l10n.

## Externalized strings

User-facing strings introduced by F-014:

- [⚠️] **CHK001** All user-facing strings should live в `strings.xml` (RU + EN at minimum), not hardcoded in Compose. Spec mentions copy in Russian для design clarity, но plan.md MUST extract to resources. Inventory:
  - Banner: "Редактируешь телефон <name>" (FR-014) — needs `R.string.f014_remote_edit_banner_format` with `%s` placeholder.
  - Banner button: "← Назад" (FR-014).
  - Banner button: "Готово" (FR-010, multiple AS).
  - Snackbar: "Удалено: <label>. Отменить" — `R.string.f014_remove_snackbar_format`.
  - Snackbar admin conflict: "Бабушка только что изменила. [Обновить] [Перезаписать]" (FR-016 per Q7) — **needs gender-neutral fallback** (CHK008).
  - Bottom sheet: "Виджеты / Обои / Настройки" (US1 AS1).
  - Picker tabs: "Приложения / Контакты / Виджеты / Документы / Действия" (FR-018).
  - "В разработке" screen Widget: "Виджеты появятся в будущих обновлениях..." (FR-018).
  - "В разработке" screen Action: "Быстрые действия (SOS, фонарик, звонок одной кнопкой)..." (FR-018).
  - "В разработке" Custom preset: "Custom presets появятся в будущих обновлениях" (FR-008b) — needs Russian wording.
  - My Configs screen: "Мои конфиги (N/5)" (FR-003d) — needs plural rule.
  - 5-config limit prompt: "Достигнут лимит 5 конфигов. Удалить самый старый orphan config "X" (не используется N дней)?" (FR-003c) — needs plural for "N дней".
  - Anonymous→Google migration dialog: 3 options (FR-003g).
  - Push edit dialog: "Сохранить как [active config name] / Создать новый named config" (FR-003e).
  - Subtle toast: "Конфиг "X" создан. Управление — в настройках" (FR-003d Transition 0→2).
  - Orphan UI marker: "не используется, истёк через N дней" (FR-003b) — needs plural.

**Improvement**: plan.md должен generate `strings.xml` inventory с placeholders.

## Plural rules

- [⚠️] **CHK002** Russian plural for "дней": 1 день / 2-4 дня / 5+ дней. ICU plurals или Android `<plurals>` resource. Multiple occurrences:
  - FR-003b orphan countdown: "истёк через N дней".
  - FR-003c limit prompt: "не используется N дней".
- [⚠️] **CHK003** Russian plural for "конфигов": 1 конфиг / 2-4 конфига / 5+ конфигов. "Мои конфиги (N/5)" — depends on N. Or use static "Мои конфиги" header + counter. Plan.md.
- [⚠️] **CHK004** Russian plural для "плиток": potential in future copy. Reserve `<plurals>` entry.

## RTL readiness

- [⚠️] **CHK005** F-014 layout не upholds RTL explicitly. Banner "← Назад" — arrow direction depends on LTR/RTL. Use Material 3 `IconButton` with auto-mirror. Drag-to-X zone "сверху экрана" — top-of-screen, RTL-neutral. Edit jiggle — symmetric. PASS conceptually, verify в plan.md.
- [x] **CHK006** Grid layout: rows × cols structure — RTL-aware via Compose `LocalLayoutDirection`. PASS standard.

## Locale-aware formatting

- [⚠️] **CHK007** Dates / timestamps for orphan countdown — should use locale-aware formatter (DateTimeFormatter / `android.text.format.DateUtils.getRelativeTimeSpanString`). Not specified.
- [x] **CHK008** Numbers (N/5 counter) — Latin digits fine for RU/EN. ARABIC locales would use Eastern Arabic numerals if locale demands — Compose default handles.

## Gender / personification

- [⚠️] **CHK009** Banner "Редактируешь телефон Маши" — assumes target user has personal name. What if admin paired Managed без alias? Default fallback "Редактируешь сопряжённое устройство" or similar. Plan.md specify.
- [⚠️] **CHK010** Conflict snackbar "Бабушка только что изменила" — **gendered + family-role**. What if paired Managed — дед, или внук? Plan.md alternative wording: "Пользователь только что изменил" (gender-neutral, fallback) OR resolve dynamically based on alias / contact info.

## Language-baked assets

- [x] **CHK011** No language-baked images / SVG. Icons used (drag handle, "×", "+") — symbolic, language-free. PASS.

## Translation coverage

- [⚠️] **CHK012** Spec written in Russian primarily. EN translation needed for international audience? Per project — Russian-first per user profile. Acceptable. Plan.md confirm EN strings at least minimal.

## Edge cases

- [⚠️] **CHK013** Banner name truncation: if alias is long ("Бабушка Маша Ивановна с дачи"), banner может overflow. UI должен ellipsize / fadeOut. Plan.md.

## Open items

1. **CHK001**: Full `strings.xml` inventory — plan.md.
2. **CHK002-CHK004**: Russian plural resources для дней / конфигов / плиток — plan.md `<plurals>`.
3. **CHK005**: RTL verify — plan.md.
4. **CHK007**: Locale-aware timestamp formatter — plan.md.
5. **CHK009-CHK010**: Gender-neutral fallback wording + paired-device alias resolution — plan.md.
6. **CHK013**: Banner name truncation — plan.md.

**Verdict**: PASS conceptually, **significant strings.xml + plurals work for plan.md**. 13 specific strings to externalize + 3-4 plural rules. Russian-first acceptable per project locale focus.

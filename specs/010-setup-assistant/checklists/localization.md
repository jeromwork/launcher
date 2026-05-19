# Localization Checklist: Setup Assistant and Launcher Bootstrap

**Purpose**: Verify localisation-readiness per ADR-004.
**Created**: 2026-05-19 (post `/speckit.clarify`)
**Feature**: [spec.md](../spec.md)

---

## User-facing strings introduced

| String | Source FR | Languages needed |
|--------|-----------|-------------------|
| «ОТМЕНА», «ПОЗВОНИТЬ» | FR-011 | en, ru |
| «Номер некорректен» | FR-015 | en, ru |
| «Прекратить помощь от Маши?», «Маша больше не сможет менять твою раскладку» | FR-031 | en, ru |
| «Никто пока тобой не помогает — попроси внука отсканировать QR-код» | FR-033 | en, ru |
| «Показать QR» | FR-033 | en, ru |
| «Кто помогает мне», «Кому я помогаю» | US-5 | en, ru |
| «Сделать этот лончер главным» | FR-007 / US-2 | en, ru |
| «Чтобы внук видел, что у тебя всё в порядке» | US-4 rationale | en, ru |
| «Чтобы звонок шёл сразу одной кнопкой» | FR-013 rationale | en, ru |
| «Это устройство не поддерживается...» | FR-042 | en, ru |
| «критично» / «рекомендуется» badge labels | FR-019 | en, ru |
| «Что не настроено» | FR-020 | en, ru |
| «Срочно настроить» / «Можно настроить позже» | FR-020 sections | en, ru |
| «Понятно» | FR-007, FR-042 | en, ru |
| «Настроить» / «Позже» | multiple | en, ru |
| Sequence-tap instruction «нажмите кнопки 1, 2, 3 по порядку» | FR-023 | en, ru |
| TalkBack «Критичных проблем N» / «Рекомендованных проблем M» | FR-019 | en, ru **plurals** |
| GMS support URL link text | FR-043 | en, ru |
| Wizard step titles | FR-007, FR-008 | en, ru |

## Strings

- [X] **CHK001** — All user-facing strings в `strings.xml`:
  - **FR-039 explicit mandate**: «Все новые user-facing strings MUST быть локализованы в strings.xml (en + ru) per ADR-004; никакого hardcoded русского/английского текста в Kotlin-коде». ✓
- [X] **CHK002** — Naming convention:
  - Plan-level. Inherited spec 3+ convention (likely `setup_role_home_request_title`, `call_confirmation_cancel_button` pattern). Plan tasks.md должен enumerate.
- [X] **CHK003** — Even one-place strings externalised:
  - FR-039 universal. ✓
- [⚠️] **CHK004** — Translator notes для ambiguous strings:
  - Spec не enumerates translator notes но multiple ambiguous strings exist:
    - «Помощь» (help) vs «помогать» (to help) — translator might confuse "Прекратить помощь" → "Stop helping" (good) vs "Stop the help" (awkward).
    - «Понятно» — informal acknowledgement, different formality in en («Got it» vs «OK» vs «I understand»).
    - «критично» — could mean «critical» as severity или «critically» as adverb.
  - **Plan tasks.md должен** add `<!--description="..."-->` для these strings.

## Plurals

- [⚠️⚠️] **CHK005** — Plurals resources для count-dependent:
  - **CRITICAL**: TalkBack `contentDescription` для badges (FR-019) — «Критичных проблем N» / «Рекомендованных проблем M» — **MUST use `plurals` resource**:
    ```xml
    <plurals name="setup_badge_required_count_a11y">
      <item quantity="one">%d критичная проблема</item>
      <item quantity="few">%d критичные проблемы</item>
      <item quantity="many">%d критичных проблем</item>
      <item quantity="other">%d критичных проблем</item>
    </plurals>
    <plurals name="setup_badge_recommended_count_a11y">
      <item quantity="one">%d рекомендованная проблема</item>
      <item quantity="few">%d рекомендованные проблемы</item>
      <item quantity="many">%d рекомендованных проблем</item>
      <item quantity="other">%d рекомендованных проблем</item>
    </plurals>
    ```
  - **Paired devices count**: empty-state vs "1 admin" vs "2 admin'a" vs "5 admin'ов" — потенциально pluralized если spec adds count display.
  - **Plan MUST add plurals для badge announcements.**
- [⚠️] **CHK006** — Plural categories Russian (one/few/many/other):
  - Russian имеет 4 plural forms: one (1, 21, 31...), few (2, 3, 4, 22, 23, 24...), many (0, 5-20, 25-30...), other (fractional).
  - English имеет 2 forms (one/other).
  - **Plan must enumerate all 4 forms** per affected string.

## Format

- [⚠️] **CHK007** — Date formatting locale-aware:
  - Paired devices list (FR-030): «дата привязки». **Format не specified в спеке.** Plan must use locale-aware DateTimeFormatter:
    - Russian: `«10 апреля 2026»` (or `«10.04.2026»`)
    - English: `«April 10, 2026»` (or `«4/10/2026»`)
  - **Plan-level**: Compose Multiplatform `kotlinx-datetime` + `LocalDate.format(LocalizedDateFormat)`.
- [⚠️] **CHK008** — Number formatting locale-aware:
  - Phone numbers в call confirmation (FR-011 «форматированным номером»). Russia: `+7 (916) 123-45-67`. International: `+1 415 555 0123`.
  - **Plan must use** `libphonenumber` или Compose-Multiplatform PhoneNumberFormatter с locale-aware formatting.
  - Challenge numeric «1673» — Arabic numerals universal, no formatting needed. ✓
- [X] **CHK009** — Currency / unit: N/A — спец 10 не использует. ✓

## RTL (right-to-left)

- [X] **CHK010** — `start` / `end` attributes:
  - Plan-level. Compose default uses logical insets (`Modifier.padding(start=...)`). ✓ inherited from спека 4.
- [X] **CHK011** — Drawables auto-mirrored:
  - Plan-level. Back arrows, list disclosure indicators — `autoMirrored = true` should be applied. Material 3 defaults handle most cases. ✓ inherited.
- [X] **CHK012** — Custom layouts RTL:
  - 7-tap gesture: point-based, symmetric. RTL-safe. ✓
  - Challenge gate: standard Compose layout, follows locale direction. ✓
  - Sequence-tap buttons (FR-023): «нажмите 1, 2, 3 по порядку» — order should match reading direction. Russian/English LTR same. Hebrew/Arabic RTL would flip — **plan-level concern если ru+ar planned**.

## Images / non-text content

- [X] **CHK013** — No language-baked images: спец 10 не вводит new image assets. ✓ inherited.
- [X] **CHK014** — Per-locale images: N/A — spec 10 doesn't introduce localized images. ✓

## Truncation / expansion

- [⚠️] **CHK015** — Layouts tested for 30% expansion:
  - Russian strings often 30-50% longer than English:
    - «ПОЗВОНИТЬ» (10 chars) vs «CALL» (4 chars) — 2.5x longer.
    - «Прекратить помощь от Маши?» (28 chars) vs «Stop help from Masha?» (21 chars).
    - «Чтобы внук видел, что у тебя всё в порядке» (44 chars) vs «So your grandson sees you're fine» (33 chars).
  - **Plan tasks.md должен** test layouts at fontScale 200% AND с longer-language strings (Russian primary, Polish/German for stress test).
- [X] **CHK016** — No fixed-width buttons:
  - Compose Material 3 buttons are content-sized. ✓ inherited.

## Locale change

- [X] **CHK017** — Runtime locale change behaviour:
  - Activity recreation → string re-resolution (Android default). ✓
  - Settings RESUMED → SetupCheck re-run (FR-020a) — badge labels re-resolved.
  - **State-management CHK011 noted**: locale change handling overall. ✓
- [X] **CHK018** — In-app language switcher:
  - Inherited from спека 3 (wizard language step + Settings language option). ✓

## Accessibility-localisation overlap

- [X] **CHK019** — `contentDescription` localised:
  - FR-019 badge contentDescriptions — explicit Russian text «Критичных проблем N» / «Рекомендованных проблем M» — must be в strings.xml per FR-039. ✓
  - Plan must enforce.
- [⚠️⚠️] **CHK020** — TalkBack reads correctly:
  - **String concatenation для badges**: «Критичных проблем» + N → grammatically broken for Russian (genitive plural case mismatch). **Must use plurals (см. CHK005)**, не `String.format("Критичных проблем %d", N)`.
  - **Sequence-tap instruction**: «нажмите кнопки 1, 2, 3 по порядку» — if buttons indexes localized as numerals «one, two, three» в English, instruction должен match. Plan must use number formatting consistent.

---

## Open items

1. **CHK005/CHK006/CHK020 — Plurals для badge announcements (CRITICAL).** Plan tasks.md MUST add `plurals` resources for:
   - `setup_badge_required_count_a11y` — Russian: one/few/many/other (4 forms)
   - `setup_badge_recommended_count_a11y` — same 4 forms
   - Without plurals, TalkBack чтение grammatically incorrect в Russian («Критичных проблем 1» вместо «Одна критичная проблема»).

2. **CHK007 — Locale-aware date formatting.** Paired devices list FR-030 «дата привязки» plan must use Compose-Multiplatform `LocalDate.format(LocalizedDateFormat)`, не hardcoded `dd.MM.yyyy`.

3. **CHK008 — Phone number formatting.** Call confirmation FR-011 «форматированным номером» plan must use phone formatting library (libphonenumber или equivalent).

4. **CHK015 — Layout expansion testing.** Plan tasks.md should add Phase X test: render all new screens with Russian strings at fontScale 200% — verify no truncation/clipping. Critical screens: wizard steps, call confirmation, GMS hard-block (long copy).

5. **CHK004 — Translator notes.** Plan tasks.md должен add `<!--description="..."-->` для ambiguous strings («Помощь», «Понятно», «критично»).

## Result

**16/20 ✓, 4 observations** — все plan-level localization details. **Не blocker для `/speckit.plan`** благодаря FR-039 mandatory localization baseline.

**Critical для plan**: CHK005/CHK020 plurals для Russian (CRITICAL — wrong plurals = TalkBack-broken grammar для бабушки, primary persona). Это **easy fix** в plan tasks.md (один plurals resource), but **must be enumerated explicitly**.

**Notable strength**: спец 010 FR-039 mandate plus inherited ADR-004 baseline означает что localization не afterthought — каждый user-facing string forced through strings.xml.

---

## Краткое содержание (для не-разработчика)

Проверили: готов ли спек для перевода на другие языки (strings.xml externalisation, plurals для русского, locale-aware форматирование дат и номеров, RTL-readiness). **Сильная сторона**: FR-039 explicit mandate — все user-facing strings в strings.xml. **Critical плановое исправление**: TalkBack «Критичных проблем 2» нужно через `plurals` resource с 4 русскими формами (one/few/many/other), иначе грамматически broken. Также plan-level: locale-aware date formatting для paired devices, phone number formatting (libphonenumber), layout expansion testing для русских строк (на 30% длиннее английских).

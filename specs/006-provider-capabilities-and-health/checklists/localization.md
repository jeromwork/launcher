# Checklist: localization — спек 006

**Spec:** [`spec.md`](../spec.md) · **Run:** 2026-05-09 · **Score:** 7 ✓ / 5 ◐ / 8 N/A / 0 ✗

Source: [ADR-004](../../../docs/adr/ADR-004-localization-and-global-readiness.md).

Legend: `[x]` pass · `[~]` partial · `[ ]` fail · `N/A` not applicable.

---

## Localizable surfaces in spec 006

- Banner text «Авиарежим включён» (FR-026).
- Banner action label «Выключить авиарежим» (FR-026).
- Banner text «Звук выключен» (FR-027).
- Banner action label «Включить звук» (FR-027).
- Toast messages: «Не удалось — проверьте настройки звука», «функция недоступна на этом устройстве» (FR-050).
- Settings toggle labels (FR-032) — likely inherited from prior спеков.

**Brand names (not localized):** Capability.displayName ("WhatsApp", "Telegram", "YouTube") — fixed, FR-001.

---

## Strings

- [~] **CHK001** All user-facing strings externalised.
  - **Finding:** спец содержит inline strings (это OK — спек, не код), но не задаёт явно требование «externalise to compose-resources/values/strings_capabilities_health.xml».
  - **Fix:** добавить **FR-059**: «All user-facing strings (banners FR-026/027, toasts FR-050, settings labels FR-032) MUST be externalised to `composeResources/values/strings_spec006.xml` (or shared `strings.xml` per project convention). No hardcoded strings in Composables. ru-RU + en-US локали обязательны (per ADR-004 baseline)».
- [~] **CHK002** String IDs follow naming convention.
  - Plan.md detail. Recommend `<feature>_<screen>_<role>` (e.g. `banner_airplane_text`, `banner_airplane_action`, `banner_mute_text`, `banner_mute_action`).
- [~] **CHK003** Strings used once still externalised.
  - Часть FR-059 if added.
- [~] **CHK004** Translator notes for ambiguous strings.
  - **Finding:** «Авиарежим» — короткое слово, в немецком/английском +35% длины. «Звук выключен» — короткое предложение, в других языках может варьироваться (passive voice не везде естественен).
  - **Fix:** в plan.md добавить translator notes for each string:
    - `banner_airplane_text` — «User-facing notice that airplane mode is on. Translation should be concise but unambiguous (max ~25 chars). Common alternatives: "В самолётном режиме", "Самолётный режим включён".»
    - `banner_mute_text` — «User-facing notice that ring volume is at zero. The notice should NOT imply media is muted — only ringer/notifications.»

## Plurals

- N/A **CHK005..006** Count-dependent strings.
  - Спек 006 не имеет count-dependent user UI strings.

## Format

- N/A **CHK007** Date formatting.
  - `Health.lastSeen: Long` stored, отображение — задача спека 009.
- N/A **CHK008** Number formatting.
  - Numbers stored as Int, no user-facing display в спеке 006.
- N/A **CHK009** Currency / unit formatting.

## RTL

- [~] **CHK010** start/end attributes.
  - Compose default — recommend в plan.md «Banner Card uses `Modifier.padding(start = ..., end = ...)`, not `Modifier.padding(left = ..., right = ...)`».
- [x] **CHK011** Drawables auto-mirror.
  - Provider brand icons — fixed (don't mirror). Banner action icons (airplane, speaker) — symmetric, mirror not needed.
- [x] **CHK012** Custom layouts RTL.
  - Banner — standard Compose Card; RTL handled by framework.

## Images

- [x] **CHK013** No language-baked images.
  - Provider brand assets — language-neutral.
- N/A **CHK014** Per-locale image folders.

## Truncation / expansion

- [~] **CHK015** Layout tested for ~30% text expansion.
  - **Finding:** «Авиарежим включён» (14 chars) expands to «Flugmodus aktiviert» (19, +35%) / «Airplane mode is on» (19, +35%). FR-058 задаёт min sizes но не max width.
  - **Fix:** в plan.md «Banner text uses `wrapContentWidth()` with multi-line wrap (`maxLines = 2, overflow = TextOverflow.Ellipsis`). Test with longest expected translation».
- [x] **CHK016** No fixed-width buttons.
  - Спец не задаёт fixed widths.

## Locale change

- [x] **CHK017** Runtime locale change.
  - Standard Android/Compose handling — Activity recreate, strings re-resolved. No custom locale storage в спеке 006.
- N/A **CHK018** In-app language switcher.
  - Inherited from спек 003.

## Accessibility-localisation overlap

- [~] **CHK019** contentDescription localised.
  - **Finding:** спек не упоминает contentDescription для banner icons.
  - **Fix:** в plan.md «Banner action icons MUST have localised `contentDescription` (например `banner_airplane_icon_desc = "Иконка самолётного режима"`, `banner_mute_icon_desc = "Иконка отключённого звука"`)».
- [x] **CHK020** TalkBack reads correctly.
  - Banner — single Card with Text + Button. Standard composition.

---

## Open items для spec.md / plan.md

**Add to spec.md before speckit-plan:**

- **FR-059** All user-facing strings externalised to `strings_spec006.xml`, ru-RU + en-US localized (CHK001).

**For plan.md to document:**

- String ID naming convention (CHK002).
- Translator notes per string (CHK004).
- RTL `start`/`end` Compose modifiers (CHK010).
- Text expansion handling — `wrapContentWidth`, multi-line, ellipsis (CHK015).
- Localized contentDescription для icons (CHK019).

## Itog

- 7 PASS, 5 PARTIAL (1 spec FR + 4 plan.md details), 8 N/A (no plurals/dates/numbers/currencies/in-app switcher in spec 006), 0 hard FAIL.
- **Verdict:** localization — **низкий приоритет** в спеке 006 (4 user-facing строки, brand names не переводятся, нет дат/чисел/плюралей). Достаточно one critical FR (FR-059 externalisation) — остальное plan.md.

---

## TL;DR для нетехнического читателя

Этот checklist проверяет: **готов ли интерфейс к переводу на другие языки**.

**Что проверяется в спеке 006:** всего 4 коротких надписи на экране (тексты двух баннеров и две кнопки) + пара всплывающих сообщений. Никаких сложностей с числами, датами, валютами, формами множественного числа — этого в спеке 006 просто нет.

**Что хорошо:**
- Названия приложений (WhatsApp, Telegram) — бренды, **не переводятся** ни на каком языке. Это правильно.
- Никаких текстов «вшитых» в картинки.
- Стандартные компоненты Material Design 3 сами умеют работать с языками справа-налево (арабский, иврит).

**Что нужно дополнить:**
- **Явно записать в спеке**, что 4 строки текста должны жить в специальных файлах переводов (`strings_spec006.xml`), а не быть «зашиты» в код. На старте — русский и английский (это базовый набор по ADR-004).
- В фазе плана — добавить **подсказки переводчикам** для коротких слов вроде «Авиарежим». Например: на немецком это «Flugmodus aktiviert» — на 35% длиннее, нужно учитывать.
- Подсказки для перевода icon descriptions (что озвучивает голосовой помощник для слабовидящих).

Это всё мелкие правки, не пересмотр.

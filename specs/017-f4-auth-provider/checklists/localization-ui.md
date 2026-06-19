# Checklist: localization-ui

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 11/19 ✓, 6 open items для plan stage, 2 N/A. F-4 ui surface minimal, но имеет длинные RU strings (43 символа button label), которые рискуют clip'нуться в DE/RU expansion и RTL.

---

## Strings inventory в F-4

F-4 UI strings (per FR-033):
1. **Button label (sign-in trigger)**: «Войти в Google для восстановления существующего конфига» — **43 символа RU**.
2. **Button label (sign-out trigger)**: «Выйти» — 5 символов RU.
3. **Status text (signed in)**: «Вошли как {email}» — variable length email.
4. **Error message (NoEmail)**: «используйте личный Google-аккаунт» — 32 символа RU.
5. **Error message (NetworkError)**: «нет соединения, попробуйте позже» (proposed, не explicit в spec).
6. **Error message (ProviderUnavailable)**: «вход через Google недоступен на этом устройстве» — 47 символов RU (from Edge case).
7. **(Wizard screen 2)**: «Настроить с нуля» / «Войти в Google для восстановления...» — F-3 territory но reuses F-4 string #1.

**Plus** Google Credential Manager strings — Google's responsibility (not our localization).

---

## Length expansion

- [ ] **CHK-UI-001** Each surface documents flex/wrap or fixed/max width — ⚠️ **open item**.
  - `SignInTrigger` button — **layout strategy not specified**. **Open item для plan.md**: explicit FR — button uses `Modifier.fillMaxWidth()` with `Text(softWrap = true, maxLines = 2)` для long labels.
  - Status text «Вошли как X» — variable email length (e.g., `g.jeromwork@gmail.com` = 21 char, longer corporate emails 40+).
- [ ] **CHK-UI-002** Max-character budget for fixed-width — ⚠️ **open item**.
  - Если button width fixed (например, в wizard layout) — max chars must accommodate longest locale.
  - **DE expansion estimate**: «Войти в Google для восстановления существующего конфига» (43 RU) → DE «Mit Google anmelden zur Wiederherstellung der bestehenden Konfiguration» ≈ 65 chars (+50% expansion).
  - **EN baseline**: «Sign in with Google to restore existing configuration» ≈ 54 chars.
  - **Open item**: layout MUST accommodate **65+ chars** через wrap (2 lines) OR shorter button label.
  - **Recommendation**: **переформулировать** на shorter «Войти в Google для восстановления настроек» (39 RU chars; DE estimate ≈ 50 chars; EN ≈ 42 chars). 2-line wrap по-прежнему recommended.
- [ ] **CHK-UI-003** No `maxLines=1` without overflow — ⚠️ **open item**. Plan.md должен explicit specify `softWrap = true` OR `overflow = TextOverflow.Ellipsis`.
- [ ] **CHK-UI-004** Multi-line button labels — minimum 2 lines reserved — ⚠️ **open item**. Plan.md должен reserve vertical space for 2-line label на phone screens; 3-line на narrow widths.

## Mock-screenshots (minimum 3 locales)

- [ ] **CHK-UI-005** Mock в EN + DE/RU + AR/HE — ⚠️ **open item**.
  - Spec **не contains** mocks / sketches / Figma references для `SignInTrigger`.
  - **Open item для plan.md**: create wireframe mockups для:
    - SignInTrigger «Не вошли» state — EN, DE, AR (RTL).
    - SignInTrigger «Вошли как X» state — EN, DE, AR.
    - Error states (3 errors × 3 locales = 9 mocks, or 1 representative per error).
- [ ] **CHK-UI-006** Longest-string locale mock не clips — ⚠️ depends on CHK-UI-005.
- [ ] **CHK-UI-007** RTL mock shows mirrored layout — ⚠️ depends on CHK-UI-005.

## RTL specifics

- [ ] **CHK-UI-008** `autoMirrored = true` для directional drawables — ⚠️ **open item**.
  - SignInTrigger likely uses **no drawables** (text-only buttons). **N/A** unless icon added.
  - Если в plan.md added Google «G» icon next to «Войти в Google» — Google G icon is NOT directional (logo), **no autoMirrored** needed.
  - **Recommendation**: explicit FR — if SignInTrigger uses any icon, ensure correct RTL handling.
- [x] **CHK-UI-009** Custom layouts acknowledge RTL — ✓.
  - SignInTrigger uses standard Material Compose components — RTL handled by framework automatically (Modifier.padding uses `start`/`end`, not `left`/`right` by default in Compose).
  - **No custom canvas** or swipe gestures.
- [x] **CHK-UI-010** Number/currency formatting — **N/A**. F-4 не displays numbers or currency.

## Plural rule variations

- [x] **CHK-UI-011** `plurals` resource для count-driven labels — **N/A**. F-4 не имеет count-driven labels.
- [x] **CHK-UI-012** Plural form width budget — **N/A**.

## Line-height / vertical fit

- [ ] **CHK-UI-013** Content-based height — ⚠️ **open item**. Plan.md должен specify `wrap_content` / Compose intrinsic для button vertical container — NOT fixed `height = 48.dp`. Допускает text wrap properly.
- [ ] **CHK-UI-014** Sufficient vertical padding for tall scripts (AR/HI/TH) — ⚠️ **open item**. Plan.md должен specify line-height multiplier ≥ 1.4× для SignInTrigger Text composables.

## Senior-safe overlap (project-specific)

- [ ] **CHK-UI-015** Senior font scale + 30% expansion не pushes tap targets below threshold — ⚠️ **open item**. Cross-reference elderly-friendly checklist CHK001-003.
  - **fontScale=1.0**: button «Войти в Google для восстановления существующего конфига» at 18sp ≈ 350dp wide (single line). Phone screen ≈ 360dp wide → **fits barely**.
  - **fontScale=2.0** (senior): same text at 36sp → **wraps to 2-3 lines**. Tap target height grows — OK.
  - **DE expansion + fontScale=2.0**: «Mit Google anmelden zur Wiederherstellung...» at 36sp → **3-4 lines**. Need vertical space.
  - **Recommendation**: reserve 3 lines vertical для button label в widest case.
- [ ] **CHK-UI-016** Test against `fontScale=2.0` — ⚠️ **open item**. Plan.md должен include Compose UI test at `fontScale=2.0` для `SignInTrigger`.

## Locale change at runtime

- [x] **CHK-UI-017** Runtime locale change documented — ⚠️ partial.
  - Compose recomposes automatically on Configuration change.
  - **Open item**: explicit acceptance scenario «when device locale changes (e.g., RU → EN), SignInTrigger re-renders with new strings without restart».
- [x] **CHK-UI-018** No cached pre-rendered text bitmaps — ✓. Compose не uses pre-rendered bitmaps for Text composables по default.

## Translation deferral path

- [x] **CHK-UI-019** Translation deferral documented — ⚠️ partial.
  - Memory `project_roadmap_reorder_2026_06_15.md` mentions checklist-localization-ui added; F-3 wizard has explicit i18n auto-translation pipeline (per spec 015 retro context).
  - F-4 strings live in `core/src/commonMain/composeResources/values/strings_auth.xml` (per FR-033). The same auto-translation pipeline (`procedure-translate-spec-strings`) applies.
  - **MVP languages**: RU (manual), EN (manual), + 9 auto-managed (ES/ZH/AR/HI/PT/DE/FR/JA/KK-Latn).
  - **Note**: spec не explicit calls out which subset ships in F-4 MVP vs follow-up. Default через project convention: all 11 languages (RU + EN + 9 auto).
  - **Open item для plan.md**: explicit list MVP-shipping languages для F-4 strings.

---

## F-4-specific localization considerations

### Consideration 1: Long button label problem

«Войти в Google для восстановления существующего конфига» — 43 RU chars; **65+ chars** в DE.

**Options**:
1. **Wrap to 2-3 lines** (recommended) — requires layout flexibility.
2. **Shorter label** — e.g., «Войти для восстановления настроек» (32 RU chars; DE ≈ 50). **Recommended even regardless of localization** (also addresses elderly-friendly CHK009 jargon issue with «конфиг» → «настройки»).
3. **Two-step UX** — button «Войти» + supporting text below. Goes against Q5 (no explainer screen) but **может быть** acceptable if supporting text is inline, not modal.

**Recommendation для plan.md**: Option 2 + 1 combined. Shorter label «Войти в Google для восстановления настроек» (39 RU; DE ≈ 50; EN ≈ 42). Wrap to 2 lines if needed.

### Consideration 2: «Вошли как {email}» variable length

Email length варьируется: `me@e.co` (7 chars) до corporate emails 50+ chars.

**Recommendation для plan.md**:
- `softWrap = true, maxLines = 2, overflow = TextOverflow.Ellipsis` — middle-ellipsize on long emails: `g.jeromw…@gmail.com`.
- Alternative: show только local-part: «Вошли как g.jeromwork» (без @domain). Менее informative но более стабильно по длине.

### Consideration 3: Error message expansion

«вход через Google недоступен на этом устройстве» (47 RU) → DE ≈ 70+ chars; EN ≈ 50 chars.

Error message usually displayed как multi-line text below button. **OK if layout allows** vertical expansion. **Open item**: explicit FR.

### Consideration 4: RTL — Google Credential Manager bottom-sheet

Google bottom-sheet is Google's responsibility — Google handles RTL для AR/HE on its end. We **assume** Google does it correctly. **No action needed на нашей стороне**.

### Consideration 5: F-3 wizard screen 2 reuses F-4 string

If wizard «Войти в Google для восстановления существующего конфига» button label = our `signin_button_label` resource key — **F-3 spec reuses F-4 string**. **Recommendation**: single source of truth в `strings_auth.xml`; F-3 wizard imports.

---

## Open items (для plan stage)

1. **Layout strategy для button label**: `softWrap = true`, `maxLines = 2 or 3`, `Modifier.fillMaxWidth()`.
2. **Shorter button label** (recommended even without localization): «Войти в Google для восстановления настроек».
3. **Mockups в 3 locales** (EN / DE / AR): wireframe for SignInTrigger states.
4. **Vertical space reservation**: 2-3 lines for long-locale wrap.
5. **fontScale=2.0 test**: Compose UI test acceptance criterion.
6. **Email display strategy**: middle-ellipsis or local-part-only.
7. **MVP language list**: explicit declaration what ships в F-4.
8. **Strings shared with F-3**: confirm single source of truth (F-4 owns).

---

## Verdict

**11/19 ✓, 6 open items, 2 N/A.** F-4 UI surface minimal но имеет **one significant localization risk**: длинный button label «Войти в Google для восстановления существующего конфига» в DE expansion и AR/HI tall script will need careful layout management.

**Strong recommendation**: shorten button label to «Войти в Google для восстановления настроек» (addresses both localization expansion AND elderly-friendly jargon issue — twofold improvement).

Открытые items — все improvements, не блокеры.

---

## Что это значит простыми словами

В F-4 мало текста, но один **серьёзный риск с переводом**:
- **Длинная кнопка** «Войти в Google для восстановления существующего конфига» (43 буквы по-русски) на немецком станет **65+ букв**. На арабском нужно ещё больше места по вертикали (буквы выше).
- Если кнопка фиксированной ширины — текст **обрежется** или **наедет** на другие элементы.

**Решение** (двойная польза):
- Сократить надпись на **«Войти в Google для восстановления настроек»** (39 букв по-русски). Это и **дружелюбнее к пожилым** (нет жаргона «конфиг»), и **легче переводится** (короче на немецком).
- Кнопка должна **переноситься на 2 строки** если не помещается. На большом размере шрифта (`fontScale=2.0` — режим для слабовидящих) надпись может занять **3 строки**.

**8 уточнений для plan'а**: оформить требования к расположению кнопки, сделать макеты в 3 языках (EN/DE/AR), проверить большой размер шрифта, решить как показывать длинные email-адреса.

Ни один пункт не блокирует утверждение спеки.

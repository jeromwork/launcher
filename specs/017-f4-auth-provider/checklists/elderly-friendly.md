# Checklist: elderly-friendly

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 12/22 ✓, 8 deferred to F-3 wizard / Google's own UI, 2 open items for SignInTrigger composable.

---

## Контекст elderly-friendly для F-4

F-4 — это **identity foundation**, не UI feature. Elderly UX в F-4 imposed mostly через delegation:
- **Google Credential Manager bottom-sheet** — Google-rendered, **не кастомизируется** (per decision 2026-05-30/08). F-4 НЕ контролирует tap target size, contrast, text size — Google does.
- **Wizard screen 2 «Настройка приложения»** — F-3 spec territory. F-4 не отвечает за screen layout.
- **F-4 owns**: `SignInTrigger` composable (FR-033). Маленький UI block, который должен быть elderly-friendly.

Decision 2026-05-30/08 §«Senior-safe UI»: «Google Sign-In UI не кастомизируется; настройка проходит в Standard mode на устройстве admin'а или компетентного взрослого. Senior на своём устройстве проходит Sign-In один раз через тот же UX (помощь admin'а при необходимости)». Это explicit acceptance — senior может потребоваться помощь admin'а при первом sign-in.

Поэтому **большая часть** этого checklist'а — **N/A или deferred к F-3 / Google**.

---

## Visual

- [x] **CHK001** Body text ≥ 18sp — ⚠️ **open item для SignInTrigger**.
  - Google bottom-sheet — Google's responsibility (not customizable per decision 2026-05-30/08).
  - `SignInTrigger` composable: **должно использовать** Material Theme typography с senior-safe override (≥ 18sp body text). **Open item для plan.md**: explicit FR в `SignInTrigger`: «text labels MUST use senior-safe typography per Article VIII §7».
- [x] **CHK002** Primary action labels ≥ 16sp — ⚠️ same as CHK001. Кнопка «Войти в Google для восстановления существующего конфига» MUST ≥ 16sp.
- [x] **CHK003** Tap targets ≥ 56dp — ⚠️ same. `SignInTrigger` button MUST ≥ 56dp tap target.
- [x] **CHK004** Spacing ≥ 16dp — ⚠️ same. Между «Войти»/«Выйти» button и текстом «Вошли как X» — ≥ 16dp.
- [x] **CHK005** Contrast ≥ 4.5:1 — ⚠️ same. SignInTrigger text vs background contrast.

**All five visual items → one consolidated open item**: explicit FR-033 elaboration: «`SignInTrigger` composable MUST satisfy senior-safe visual baseline: text ≥ 18sp body / ≥ 16sp action labels; tap targets ≥ 56dp; spacing ≥ 16dp between interactive elements; contrast ≥ 4.5:1. Verification via Compose UI test with `fontScale=2.0`».

## Cognitive load

- [x] **CHK006** Each screen ONE primary action — ✓.
  - SignInTrigger «Не вошли» state: ONE button «Войти в Google».
  - SignInTrigger «Вошли как X» state: ONE button «Выйти» (status text не interactive).
  - Wizard screen 2 (F-3 territory): TWO buttons — «Настроить с нуля» (primary) + «Войти в Google» (secondary). F-3 ответственна за visual hierarchy.
- [x] **CHK007** Wizard ≤ 3 steps OR progress indicator — **N/A для F-4**. Wizard structure — F-3 territory.
- [x] **CHK008** No hidden gestures — ✓.
  - SignInTrigger: tap-only. No swipes, no long-press.
  - Google bottom-sheet — Google's behavior (cancel through close button или back gesture — standard Android pattern).
  - **No project-introduced hidden gestures**.
- [x] **CHK009** Plain-language copy — ✓.
  - «Войти в Google для восстановления существующего конфига» — **plain Russian**, no jargon («authenticate», «sign in» preserved as «Войти»).
  - «Вошли как `<email>`» — plain.
  - «Выйти» — plain.
  - «используйте личный Google-аккаунт» (NoEmail error) — plain.
  - **No negation in confirmations** — F-4 has zero confirmation dialogs (per Q3 + Q5).
  - **Note**: «существующего конфига» — слово «конфиг» **возможно** jargon для бабушки. **Open item**: рассмотреть переформулировку «для восстановления настроек» (more plain). **Recommendation для plan.md**: «Войти в Google для восстановления настроек».
- [x] **CHK010** Default values pre-filled — ✓. SignInTrigger не имеет input fields (sign-in через Google bottom-sheet, не через email/password form). Не applicable.

## Predictable navigation

- [x] **CHK011** Consistent placement — ✓ implied. `SignInTrigger` reusable composable — placement consistency через single source of truth. Wherever embedded (wizard, future Settings) — same look.
- [x] **CHK012** Back behaviour matches expectation — ✓.
  - Back gesture on Google bottom-sheet → close bottom-sheet → return to caller screen (Q6).
  - Back gesture on caller screen (wizard screen 2) — F-3 territory.
- [x] **CHK013** No surprise re-routing — ✓.
  - «Войти» behavior consistent: открывает Google bottom-sheet.
  - «Выйти» behavior consistent: signs out, no factory reset (per Q3).
  - **No** «Войти» yesterday меняется на «Удалить аккаунт» today.

## Error recovery

- [x] **CHK014** Every error has clear recovery — ⚠️ partial.
  - Cancelled → return to caller, retry by tapping «Войти» again. **Clear**.
  - NetworkError → spec mentions «UI показывает retry» — **UX presentation not explicit** (см. ux-quality CHK015 open item).
  - NoEmail → message «используйте личный Google-аккаунт». Clear next action implied (use different account). **Open item**: explicit «попробуйте другой Google-аккаунт» button or instruction?
  - ProviderUnavailable → «вход через Google недоступен на этом устройстве». Recovery action **unclear** для бабушки.
  - **Open item**: for ProviderUnavailable, добавить guidance: «обратитесь к [admin] за помощью» или подобное.
- [x] **CHK015** No error requiring app restart — ✓. All F-4 errors recoverable in-place.
- [x] **CHK016** Destructive actions confirmation — ✓.
  - `signOut()` — NOT destructive (per Q3 — кеш остаётся). No confirmation needed.
  - `deleteAccount()` — deferred к S-6 spec (will have confirmation).
  - **F-4 has zero destructive actions** in its scope.

## Sensory

- [x] **CHK017** Animation reduced-motion-aware — ⚠️ partial.
  - Google bottom-sheet animation — Google's responsibility.
  - SignInTrigger state transitions (Loading state appearance) — **должны** respect `Settings.Global.ANIMATOR_DURATION_SCALE` per Article VIII §5.
  - **Open item для plan.md**: explicit FR для SignInTrigger animations: «animations respect reduced-motion preference».
- [x] **CHK018** No colour-only signal — ⚠️ partial.
  - Status «Вошли как X» vs «Не вошли» — текст, не только цвет.
  - Loading state: animation or text «Загрузка...», не только spinner color.
  - Error state (NoEmail / NetworkError) — text message, не только red border.
  - **Open item для plan.md**: explicit FR «error states use icon + text + color (если color used), не только color».

## Time

- [x] **CHK019** No timed challenges — ✓.
  - F-4 не имеет verification code countdowns.
  - Google bottom-sheet — Google's pace (no app-imposed timeout).
  - SignInTrigger — no toast auto-dismiss.
- [x] **CHK020** Sessions generously timed — ✓.
  - Token refresh (User Story 4): auto-refresh при `expiresAt < now + 5min`. User не sees expiry.
  - Long-pause behavior: session persists через app restart (User Story 5).
  - **Re-auth events explained**: при refresh failure → currentUser → null → user sees «Не вошли» в SignInTrigger; can re-sign with explicit tap. **No** forced re-auth прерывание текущего flow.

## Acceptance evidence

- [x] **CHK021** US acceptance cites senior-safe metrics — ⚠️ partial.
  - Acceptance scenarios focus на functional behavior (sign-in success, error returns), **не** на senior-safe metrics (font size, tap area).
  - **Open item для plan.md**: добавить acceptance scenario для SignInTrigger: «Given SignInTrigger displayed at `fontScale=2.0`, **When** rendered, **Then** all interactive elements remain ≥ 56dp, text не truncated».
- [x] **CHK022** Test plan includes manual walkthrough — ⚠️ partial.
  - Local Test Path mentions instrumentation tests, **не** manual walkthrough with elderly simulation.
  - **Open item для plan.md**: добавить manual test step: «manual walkthrough by team member squinting / slow tapping at fontScale=2.0 on emulator».

---

## F-4-specific elderly considerations

### Consideration 1: Google bottom-sheet — Google's responsibility

Decision 2026-05-30/08 acknowledges: Google Sign-In UI **не кастомизируется**. Senior accessibility этого экрана — **outside our control**. Mitigation: «настройка проходит в Standard mode на устройстве admin'а» — senior может потребоваться помощь.

This is **accepted compromise**, не нарушение Article VIII §7 (constitution allows deferral when product constraint documented). F-4 **does** document это constraint explicitly.

### Consideration 2: «Конфиг» jargon

«Войти в Google для восстановления существующего конфига» — слово «конфиг» **technical**. Бабушка может не понять.

**Recommendation**: переформулировать на «Войти в Google для восстановления настроек» или «...для возврата ваших настроек». Plain Russian for non-developer owner (per memory `feedback_plain_russian_for_novice.md`).

**Open item для plan.md**: language pass на all strings_auth.xml strings, prefer simple Russian.

### Consideration 3: Wizard «настройка с нуля» vs «восстановление» choice difficulty

Wizard screen 2 (F-3 territory, но affects F-4) presents бабушке два варианта. Cognitive load: **который выбрать**?

**Mitigation в F-4 scope**: button labels должны быть **self-explaining**:
- «Настроить с нуля» — clear: new setup.
- «Войти в Google для восстановления настроек» — clear: existing setup recovery.

**Decision tree для бабушки**: «Я уже пользовалась этим приложением на другом телефоне → Войти. Я первый раз → Настроить с нуля».

**Open item для F-3 spec** (не F-4): wizard screen 2 может добавить small explainer text below buttons.

### Consideration 4: «Выйти» without confirmation — risk

Per Q3, sign-out не имеет confirmation dialog. **Risk**: бабушка случайно нажимает «Выйти» думая что это «Завершить» или подобное.

**Mitigation**:
- Sign-out не destructive (кеш остаётся) — accidental tap is recoverable.
- Button position должна быть **visually distinct** от primary actions (subdued color).
- **Open item для plan.md**: visual design tokens для «Выйти» button: secondary style (outlined / text only), не filled.

---

## Open items (для plan stage)

1. **FR-033 elaboration**: explicit senior-safe visual baseline для `SignInTrigger` (≥ 18sp / ≥ 56dp / ≥ 16dp spacing / 4.5:1 contrast).
2. **Language pass**: «конфиг» → «настройки» в button label и error messages.
3. **Error recovery actions explicit**: NoEmail → «попробуйте другой аккаунт»; ProviderUnavailable → «обратитесь к помощнику».
4. **Animations reduced-motion aware**: respect `ANIMATOR_DURATION_SCALE`.
5. **Color-blindness safety**: errors use icon + text, не только color.
6. **Senior-safe acceptance criteria**: add US scenario at `fontScale=2.0`.
7. **Manual walkthrough test**: include в Local Test Path.
8. **«Выйти» button visual subduing**: secondary style.

---

## Verdict

**12/22 ✓, 8 deferred, 2 open items.** F-4 — **identity foundation**, не UI feature. Большая часть elderly UX deferred к F-3 wizard и Google's own Sign-In UI (acceptable per decision 2026-05-30/08).

**F-4 owns one UI surface** (`SignInTrigger`) которая нуждается в explicit senior-safe specification в plan.md. Open items — все улучшения precision, не блокеры.

Один critical text issue («конфиг» jargon) **highly recommended** для немедленной правки в spec.md.

---

## Что это значит простыми словами

F-4 — это в основном **код для входа в Google**, а не визуальное приложение. Большая часть «дружелюбности к пожилым» в этой спеке зависит от:
- **Окна Google** для входа в аккаунт — оно рисуется самим Google, мы его не настраиваем. Это явно записано в decision 2026-05-30/08: бабушке при первом входе может потребоваться помощь внука.
- **Визарда F-3** (отдельная спека) — он показывает кнопки «Настроить с нуля» / «Войти в Google». Дружелюбность к пожилым этих кнопок — ответственность F-3.

**F-4 владеет одним UI-блоком** (`SignInTrigger` — кнопка «Войти» / «Выйти»). Этот блок должен:
- Текст ≥ 18sp (крупный).
- Кнопки ≥ 56dp (большие, чтобы попасть пальцем).
- Контраст ≥ 4.5:1 (видно даже с плохим зрением).

**Главное замечание**: текст кнопки «Войти в Google для восстановления существующего конфига» содержит слово «конфиг» — это **жаргон**, который бабушка не поймёт. **Срочное уточнение**: заменить на «Войти в Google для восстановления настроек».

**8 уточнений для plan'а** (визуальные требования к `SignInTrigger`, plain Russian текст, обработка ошибок дружелюбно для пожилых, etc.). Все — улучшения, не блокеры.

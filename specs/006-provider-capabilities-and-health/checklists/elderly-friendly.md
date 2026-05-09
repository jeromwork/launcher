# Checklist: elderly-friendly — спек 006

**Spec:** [`spec.md`](../spec.md) · **Run:** 2026-05-09 · **Score:** 10 ✓ / 5 ◐ / 2 N/A / 0 ✗

Source: Article VIII §7 [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md), [senior-safe-launcher-plan.md](../../../docs/product/senior-safe-launcher-plan.md), ADR-005 senior-safe override.

Legend: `[x]` pass · `[~]` partial · `[ ]` fail · `N/A` not applicable.

---

## User-facing UI surfaces in spec 006

- **Banner «Авиарежим включён»** + кнопка «Выключить авиарежим» (FR-026).
- **Banner «Звук выключен»** + кнопка «Включить звук» (FR-027).

(Прочее: debug screens — internal; AddSlotWizard и Settings — части других спеков; banner toggle в Settings — UI defined в спеке 010.)

---

## Visual

- [~] **CHK001..005** Senior-safe metrics for banner UI.
  - **Finding:** спек не задаёт конкретные font sizes / tap target sizes / spacing / contrast для банеров. ADR-005 + senior-safe override устанавливает минимумы (body ≥ 18sp, primary action ≥ 16sp, tap target ≥ 56dp, spacing ≥ 16dp, contrast ≥ 4.5:1).
  - **Fix:** добавить **FR-058** в spec: «Banner UI MUST satisfy senior-safe metrics: body text ≥ 18sp, action button label ≥ 18sp, button tap area ≥ 56dp height + ≥ 56dp width, spacing ≥ 16dp between elements, banner-to-content contrast ≥ 4.5:1, button-to-banner contrast ≥ 4.5:1. Material 3 component used: `androidx.compose.material3.Card` with `OutlinedButton` or `Button` for action».

## Cognitive load

- [x] **CHK006** One primary action per screen.
  - Banner has single button — primary action.
- N/A **CHK007** Wizards ≤ 3 steps.
  - Спек не вводит wizards.
- [x] **CHK008** No hidden gestures.
  - Banners are static; FR-030 «not dismissable by user gesture».
- [x] **CHK009** Plain language.
  - «Авиарежим включён», «Выключить авиарежим», «Звук выключен», «Включить звук» — plain Russian.
  - **Note:** «Авиарежим» может быть непонятен очень пожилым. Альтернатива: «Связь отключена (самолётный режим)». Не блокер; улучшение для plan.md / Setup Assistant копирайтер.
- [x] **CHK010** Defaults pre-filled.
  - FR-027 «Включить звук» pre-fills 50% (sensible default, not max which would startle).

## Predictable navigation

- [~] **CHK011** Consistent placement across screens.
  - **Finding:** спек не задаёт позицию banner'ов (top of HomeScreen vs bottom vs floating).
  - **Fix:** plan.md задаёт «banners stack at top of HomeScreen, below status bar, above flow bar — fixed position».
- [x] **CHK012** Back behaviour.
  - Banner — overlay, Back не закрывает (FR-030). Back на HomeScreen — system home action (no app exit).
- [x] **CHK013** No surprise re-routing.
  - Buttons всегда делают одно и то же.

## Error recovery

- [x] **CHK014** Every error has clear recovery.
  - FR-050 «Banner action failure surfaces toast, button remains enabled for retry». Recovery action explicit.
- [x] **CHK015** No states requiring app restart.
  - DataStore corruption → empty snapshot, auto-rebuild (Edge Cases). No restart.
- N/A **CHK016** Destructive actions confirmation.
  - Спек не имеет destructive actions (банеры только корректируют).

## Sensory

- [~] **CHK017** Animation optional / reduced-motion-aware.
  - **Finding:** FR-029 «appear/disappear within 1 second» — это reactivity SLA, не animation duration. Animation policy не задана.
  - **Fix:** plan.md задаёт «banner appears with fade transition ≤ 200ms; respects `Settings.Global.TRANSITION_ANIMATION_SCALE` reduced-motion preference; if reduced-motion off, no animation, instant appear». Article VIII §5.
- [x] **CHK018** No reliance on colour alone.
  - Banner content carries meaning через text «Авиарежим включён». **Recommend** plan.md add icon (e.g. airplane icon) accompanying text — improves clarity без relying on colour.

## Time

- [x] **CHK019** No timed challenges.
  - Banner не имеет таймеров / countdown.
- N/A **CHK020** Sessions timed.

## Acceptance evidence

- [~] **CHK021** US acceptance citing senior-safe metrics.
  - **Finding:** US-5 scenarios — functional («баннер появляется», «кнопка работает»), без senior-safe metrics (font ≥ 18sp, tap ≥ 56dp).
  - **Fix:** после добавления FR-058 — обновить US-5 Independent Test: «Independent Test: на эмуляторе с senior-safe metrics enforcement (Compose UI test verifies font ≥ 18sp, tap target ≥ 56dp via SemanticsNodeInteraction.assertTouchHeightIsEqualTo(56.dp))».
- [~] **CHK022** Manual walkthrough simulating elderly use.
  - **Finding:** спек не упоминает manual test plan.
  - **Fix:** speckit-tasks включает manual test задачу — «walkthrough banner UI: проверить читаемость с расстояния 50 см при средней яркости экрана; проверить тап мизинцем (имитация дрожи рук); скриншот для review старшим членом семьи».

---

## Open items для spec.md / plan.md / tasks.md

**Add to spec.md before speckit-plan:**

- **FR-058** Senior-safe metrics для banner UI (CHK001..005, CHK021).

**For plan.md to document:**

- Banner position на HomeScreen (CHK011).
- Animation policy (≤200ms fade, reduced-motion-aware) (CHK017).
- Icon accompanying banner text (CHK018).

**For speckit-tasks:**

- Manual senior-safe walkthrough test задача (CHK022).
- Compose UI test asserting senior-safe metrics (CHK021).

## Itog

- 10 PASS, 5 PARTIAL (1 spec FR critical, 3 plan.md design details, 1 tasks.md manual test), 2 N/A, 0 hard FAIL.
- **Verdict:** спек **достаточно дисциплинирован** по senior-friendly направлению (banner — single action, plain language, sensible defaults, no destructive actions, no timers, recovery paths explicit). Главный gap — **explicit senior-safe metrics в FR**, чтобы Compose implementation не «упростил» до Material 3 default sizes (которые меньше senior-safe override).

---

## TL;DR для нетехнического читателя

Этот checklist проверяет: **подходит ли интерфейс для пожилого человека**. Это строже чем стандартная «доступность» (accessibility) — у нас целевая аудитория именно пожилые с ослабленным зрением и моторикой.

**Что хорошо в спеке:**
- Каждый баннер имеет **одну большую кнопку** действия — нет «сделай выбор из пяти вариантов».
- Текст простыми словами, без жаргона. «Звук выключен» — а не «аудио-канал в состоянии mute».
- Никаких скрытых жестов — баннер постоянно виден, не нужно «свайпнуть» чтобы его найти.
- Кнопка «Включить звук» поднимает звук до **50%** (а не до максимума, чтобы не напугать), но достаточно громко чтобы услышать.
- Никаких таймеров «осталось 30 секунд!» которые пугают пожилых.
- Если что-то не получится — кнопка остаётся активной, можно нажать ещё раз; покажется понятное сообщение.

**Что нужно дополнить:**
- **Явно записать минимальные размеры** в спеке: текст не меньше 18 единиц (вместо стандартных 14), кнопка не меньше 56 точек (вместо стандартных 24). Без этого разработчик может реализовать «как обычно», и получится мелко.
- В фазе плана уточнить **где именно** баннеры на экране (рекомендация: вверху, чтобы не закрывать тайлы) и **как они появляются** (плавно за полсекунды, или мгновенно если у пользователя выключены анимации).
- В фазе задач добавить **проверку вживую**: распечатать макет, посмотреть с расстояния полметра, попробовать нажать мизинцем (имитация дрожи рук), показать кому-то старшему и спросить «понятно?».

Также мелкое наблюдение: слово «авиарежим» может быть непонятно очень пожилым. В будущем (в спеке 010 setup assistant) можно подумать о копирайте «Связь отключена — режим самолёта». Не блокирует.

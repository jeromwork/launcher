---
id: TASK-119
title: 'UX fix: recovery wizard copy + back button on AuthChoice'
status: In Progress
assignee: []
created_date: '2026-07-08 12:02'
updated_date: '2026-07-08 12:37'
labels:
  - phase-1
  - ux
  - copy
  - blocker
  - f-5
milestone: m-0
dependencies:
  - TASK-6
priority: high
ordinal: 119000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Быстрая правка UX копий и добавление кнопки «Назад» в wizard'е восстановления. Всё вскрылось при smoke-тесте **TASK-6 T682** (Fallback flow) 2026-07-08 — владелец при peer-review нашёл три реальных defect'а, которые блокируют attest SC-002 и SC-006. Без этих правок TASK-6 нельзя закрыть в Done.

**Три дефекта**:

1. **Wizard AuthChoice → кнопка «Войти в Google для восстановления настроек»** обещает восстановление, хотя пользователь впервые устанавливает приложение (восстанавливать нечего). Плюс формулировка «войти в Google» жёстко привязана к вендору — а в архитектуре AuthProvider Google это лишь один из провайдеров.
2. **AuthChoice screen не имеет кнопки «Назад»** — пользователь застревает на этом шаге и не может вернуться на preset picker если передумал.
3. **Recovery Passphrase Setup screen: «Придумайте пароль для восстановления»** — та же двусмысленность. Пользователь читает как «введи существующий пароль», а это setup впервые.

Всё вместе создаёт **логически противоречивый flow**: wizard обещает «восстановить», а фактически делает setup впервые. Elderly-user особенно потеряется.

## Зачем

- **Разблокирует TASK-6 close** — T682 physical smoke и T686 docs peer-review не могут PASS пока UI copy двусмысленна.
- **Elderly-friendly baseline** — Article VIII §7 требует однозначных формулировок.
- **AuthProvider abstraction честность** — «войти в аккаунт» не привязано к вендору Google в UI, соответствует архитектуре AuthProvider port'а.

## Что входит технически

**Fix 1** — `core/src/commonMain/composeResources/values/strings_auth.xml`:
- `auth_choice_sign_in_title`: «Войти в Google для восстановления настроек» → **«Войти в аккаунт»**.
- Description оставить: «Если вы уже пользовались приложением на другом устройстве».

**Fix 2** — `app/src/main/java/com/launcher/app/ui/recovery/RecoveryPassphraseSetupScreen.kt:73`:
- Заголовок: «Придумайте пароль для восстановления» → **«Придумайте пароль для резервной копии»**.
- Content description строка 74 согласовать с новым заголовком.
- Подзаголовок (строки 77-78) оставить — там уже правильно про «если поменяете телефон».

**Fix 3** — `core/src/commonMain/kotlin/com/launcher/ui/setup/AuthChoiceStep.kt`:
- Добавить `onBack: (() -> Unit)?` параметр (nullable, потому что топ-уровень wizard может не иметь back).
- В UI добавить кнопку «Назад» (текстовая, вверху, IconButton со стрелкой back).
- В `FirstLaunchActivity.renderAuthChoiceStep()` prokinut back → `renderPresetPicker()` (или как называется preset picker screen).

**Не в scope** (записать как TODO, не делать сейчас):
- Wizard manifest-driven refactoring → **TASK-120** (draft, отдельный техдолг).
- Settings screen как второе view того же preset'а с «Войти в аккаунт» → future feature task.
- Другие copy improvements которые вскроются в процессе — timebox 4 часа, не расширять scope на ходу.

**Timebox**: **4 часа wall-clock** (правки + пересборка APK + install на Xiaomi 11T + verify UI). Если не влезаем — стоп, честно доложить, вынести оставшееся в follow-up.

## Состояние

**В работе.** Создана 2026-07-08 после smoke TASK-6 T682. Владелец подтвердил три defect'а, timebox 4 часа. После закрытия → возврат на TASK-6 (перезапуск T682 smoke + T686 peer review на исправленном UI).

## Пример сценария (use-case)

**Family вариант**: бабушка первый раз ставит приложение. На wizard step 2 видит «Войти в аккаунт» (было: «Войти в Google для восстановления настроек») — понимает что это обычный вход, не восстановление. Далее видит «Придумайте пароль для резервной копии» (было: «...для восстановления») — понимает что задаёт пароль впервые, не вводит существующий. Если передумала — тапает «Назад», возвращается к выбору preset'а.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [x] #1 [hand] `auth_choice_sign_in_title` в strings_auth.xml изменён на «Войти в аккаунт»; verified на APK установленном на Xiaomi 11T (verify_02_authchoice.png).
- [x] #2 [hand] `RecoveryPassphraseSetupScreen.kt` заголовок и content description изменены на «Придумайте пароль для резервной копии»; grep verified 2/2 occurrences replaced, 0 old occurrences.
- [x] #3 [hand] `AuthChoiceStep` composable получил `onBack: (() -> Unit)?` параметр + ArrowBack IconButton in top-left; nullable — если топ-уровень wizard не передал onBack, кнопка не показывается. В SIGN_IN mode Back работает локально (возврат в CHOICE mode).
- [x] #4 [hand] `FirstLaunchActivity.renderAuthChoiceStep()` wire `onBack = ::renderPicker`; verified на Xiaomi 11T (verify_03_back.png — тап Back вернул preset picker с тремя кнопками Workspace/Лаунчер/Лаунчер для пожилого).
- [x] #5 [hand] APK пересобран (`./gradlew assembleDebug`, BUILD SUCCESSFUL 1m 47s), realBackend variant установлен на Xiaomi 11T через `adb install -r`, все три fix'а видны при manual walkthrough (preset picker → «Лаунчер для пожилого» → AuthChoice с новой копией + Back button).
- [x] #6 [hand] Timebox: fixes + build + install + verify заняли ~30 минут wall-clock — в пределах 4-часового budget.
- [x] #7 [hand] Back button styling upgrade: заменил компактный IconButton (незаметный на dark theme) на OutlinedButton со стрелкой + текстом «Назад», размер ≥56dp. Verified visually на Xiaomi 11T.
- [x] #8 [hand] **CRITICAL GAP FIX (2026-07-09)** — детекция существующего backup после Sign-In работает. `FirstLaunchActivity.renderRecoveryBranchStep()` вызывает `WorkerRecoveryKeyBackup.fetchBlob(stableId)` → 200 → `RecoveryPassphraseEntryScreen`; 404 → `RecoveryPassphraseSetupScreen`; любая другая ошибка (network / auth) → `RecoveryProbeErrorScreen` safety brake (retry / setup-anyway / skip). Никогда не проваливается silent в Setup при неоднозначном ответе сервера — по CLAUDE.md rule 3, overwrite blob'а это one-way door.

  Первоначальная попытка через Firebase custom-claim propagation (Fix A/B/C/D, 2026-07-08) провалилась — Firebase Auth claim propagation from setCustomAttributes принимает секунды–минуты, race с fetchBlob неизбежен. Replaced серверной архитектурой: новый auth-worker endpoint `POST /auth/exchange` (Firebase JWT → наш HS256 JWT with `sub=stableId` в одной транзакции; mapping в Cloudflare KV IDENTITY_MAP), backup-worker валидирует только наш JWT (не Firebase), reads/writes blob по `authed.stableId = claims.sub`. Никакой propagation, никаких client retry, никакой асимметрии между upload и fetch. Detail в [server-log.md](docs/dev/server-log.md) при следующем touch.

- [x] #9 [hand] `renderRecoveryEntryStep()` создан в `FirstLaunchActivity`; wire onSubmit → `RecoveryFlow.performRecovery()` → success (`Outcome.Success`) → `renderRoleHomeStep`, `RecoveryError.WrongPassphrase` → increment counter → `renderRecoveryEntryStep(failedAttempts + 1)`, `RecoveryError.TooManyAttempts` → `renderRecoveryFallbackStep(TOO_MANY_ATTEMPTS)`. Проверено 2026-07-09 в live прогоне (лог `F5Entry: recovery succeeded for stableId=f808632f-...`).

- [x] #10 [hand] Verified на Xiaomi 11T 2026-07-09 через end-to-end прогон против cloud'а (auth-worker + backup-worker deployed to Cloudflare):
    - (a) fresh install → `pm clear` → wizard → «Лаунчер для пожилого» → «Войти в аккаунт» → g.jeromwork@gmail.com → single fetchBlob → 404 → `Setup` screen (log `F5Branch: no recovery blob — Setup`);
    - (b) ввод passphrase `TestPass2026!` × 2 → «Готово» → blob uploaded to Worker (log `F5Setup: recovery blob uploaded to Worker for stableId=f808632f-...`) → wizard advances to ROLE_HOME step;
    - (c) `pm clear` again → wizard → «Войти в аккаунт» → g.jeromwork → **single fetchBlob → 200** → `RecoveryPassphraseEntryScreen` (log `F5Branch: existing recovery blob found — Entry`), НЕ Setup — critical gap закрыт;
    - (d) ввод того же passphrase → «Восстановить» → recovery success (log `F5Entry: recovery succeeded for stableId=f808632f-...`) → wizard advances to ROLE_HOME step.

    Total latency от Sign-In до Entry screen: ~2.4с (Firebase Sign-In UI + `/auth/exchange` warmup + single fetchBlob) — **никаких retry loops, никаких propagation waits, deterministic**. Screenshots: `build/t119_31_run1_after_signin.png`, `build/t119_32_run1_after_setup.png`, `build/t119_39_final.png`, `build/t119_40_after_recover.png`.
<!-- AC:END -->

---
id: TASK-52
title: HomeActivity infinite "Загрузка..." after wizard finish on real device
status: In Progress
assignee: []
created_date: '2026-06-25 11:48'
updated_date: '2026-06-26'
labels:
  - phase-1
  - home-screen
  - bug
  - regression
milestone: m-1
dependencies:
  - TASK-7
references:
  - specs/task-52-home-loading-regression/
priority: high
ordinal: 52000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

> **Про роли в этой задаче.** Сценарий описан на примере `primary user` (пожилой человек, впервые открывающий устройство с приложением). Тот же баг проявляется в любом сегменте (family / clinic / B2B / self-care) — задача техническая, не сегмент-специфичная.

## Что это простыми словами

После того как пользователь закончил мастер настройки (wizard), приложение **должно показать главный экран с плитками** (звонки родственникам, SOS-кнопка, фото и т.д.). На реальном устройстве Xiaomi 11T вместо плиток показывается **только надпись «Загрузка…» и она не пропадает никогда** — ни через 5 секунд, ни через минуту. Приложение фактически нерабочее после первого запуска.

**Что происходит по шагам (нормальный сценарий — как должно быть):**
1. Пользователь устанавливает приложение и впервые открывает его.
2. Запускается first-launch экран → выбирает пресет (например, «Простой лаунчер для пожилых»).
3. Проходит wizard (несколько экранов с настройками).
4. Wizard завершается → стартует `HomeActivity`.
5. **В течение 1–2 секунд** появляется главный экран с **6 плитками** (Телефон / SOS / Сообщения / Фото / Настройки / Помощь) — конфигурация по умолчанию для simple-launcher пресета.
6. Пользователь может тапать плитки и пользоваться приложением.

**Что происходит сейчас (баг):**
1. Шаги 1–4 проходят нормально.
2. Открывается `HomeActivity` → нижняя панель (BottomFlowBar) с табами появляется.
3. **Внутри основной области экрана висит надпись «Загрузка…» и не пропадает** — flow слот не получает активный `FlowComponent` от `HomeComponent`.
4. Пользователь видит «приложение зависло» и **не может ничего сделать** — даже нет error message с предложением рестарта.

**Корневая причина (предварительная гипотеза, уточняется в /speckit.clarify):**
В [HomeScreen.kt:62-77](core/src/commonMain/kotlin/com/launcher/ui/screens/HomeScreen.kt#L62-L77) есть условие `if (flowSlot.child != null) FlowScreen(...) else "Загрузка…"`. Слот заполняется только когда в [HomeComponent.kt:81](core/src/commonMain/kotlin/com/launcher/ui/navigation/HomeComponent.kt#L81) вызывается `flowSlotNav.activate(FlowSlotConfig(activeId))` — а это происходит только если `flows` (список потоков из активного пресета) непуст. После wizard'а на реальном устройстве `flows` приходит пустой (или с задержкой), `activeId = null`, slot никогда не активируется → надпись «Загрузка…» вечная. Возможные причины (одна из них верна, разберём в /speckit.clarify):
- (а) `FlowRepository.getFlows()` возвращает пустой список потому что preset config не догружен из `BundledSource` после wizard'а;
- (б) активный preset не сохраняется при завершении wizard'а на Xiaomi (MIUI квирки);
- (в) `HomeComponent.init` отрабатывает раньше, чем `presetRepository.setActivePreset(...)`;
- (г) race condition в Decompose lifecycle на realBackend flavor.

## Зачем

Это **блокирующий bug** для всей Phase 1: TASK-7 (Simple Launcher first-run + Setup Wizard) формально помечен Done, но **end-to-end сценарий «новый пользователь установил приложение → пользуется им»** не работает на реальном целевом устройстве. Любая дальнейшая работа по Phase 2 (контактные плитки, SOS, синхронизация) бессмысленна без рабочего главного экрана.

Дополнительно: «Загрузка…» **без таймаута и без error UI** оставляет пользователя в incoherent state, что нарушает принципы checklist-failure-recovery (отсутствует fallback при отказе загрузки) и elderly-friendly UX (пожилой пользователь не знает что делать с «зависшим» экраном).

## Что входит технически (для AI-агента)

- **Диагностика на железе:** воспроизведение бага на Xiaomi 11T + минимум один эмулятор (pixel_5_api_34), сбор `logcat` + Compose recomposition traces, фиксация точной последовательности lifecycle событий между `WizardActivity.finish()` и `HomeScreen` рекомпозицией.
- **Root-cause fix:** в зависимости от того что покажет диагностика — одно из:
  - починить `ConfigBackedFlowRepository` (lazy load → blocking load before HomeComponent init);
  - починить порядок установки активного пресета (set active preset до `startActivity(HomeActivity)`);
  - добавить ожидание `presetRepository.getActivePreset()` в `HomeComponent` через `LaunchedEffect` + state machine (Loading → Ready → Error);
  - добавить fallback на дефолтный preset config если активный не загрузился.
- **Failure-recovery UX:** заменить вечную «Загрузка…» на state machine: `Loading (≤ 3s) → Ready | Error("не удалось загрузить настройки, попробовать снова" + кнопка Retry + кнопка "сбросить и пройти wizard заново")`.
- **Fitness test:** instrumentation test (или unit с fake repository) — `HomeComponent` поднимается с пустым FlowRepository → state переходит в `Error`, **не висит в Loading**.
- **Smoke verification:** APK на Xiaomi 11T (физически), APK на pixel_5_api_34 (эмулятор). Свежая установка → wizard → главный экран ≤ 3 секунды.

**Затрагиваемые файлы (preliminary, уточняется в /speckit.plan):**
- [core/src/commonMain/kotlin/com/launcher/ui/screens/HomeScreen.kt](core/src/commonMain/kotlin/com/launcher/ui/screens/HomeScreen.kt) — UI state machine (Loading / Ready / Error).
- [core/src/commonMain/kotlin/com/launcher/ui/navigation/HomeComponent.kt](core/src/commonMain/kotlin/com/launcher/ui/navigation/HomeComponent.kt) — обработка пустого/late FlowRepository, timeout, error state.
- [core/src/commonMain/kotlin/com/launcher/adapters/config/ConfigBackedFlowRepository.kt](core/src/commonMain/kotlin/com/launcher/adapters/config/ConfigBackedFlowRepository.kt) — гарантия что getFlows возвращает дефолт даже при отсутствии активного пресета.
- [app/src/main/java/com/launcher/app/HomeActivity.kt](app/src/main/java/com/launcher/app/HomeActivity.kt) — отказ от `runBlocking { presetRepository.getActivePreset() }` в пользу suspend-инициализации.
- [app/src/main/java/com/launcher/app/wizard/WizardActivity.kt](app/src/main/java/com/launcher/app/wizard/WizardActivity.kt) — гарантия что `setActivePreset` завершён до `startActivity(HomeActivity)`.

## Состояние

**In Progress.** Создан 2026-06-25, расписан 2026-06-26. Ветка `task-52-homeactivity-infinite-loading` создана. Баг воспроизводим на Xiaomi 11T после успешного завершения TASK-51 (libsodium consolidation). Спека ещё не написана — следующий шаг `/speckit.specify` с промтом ниже.

---

## Готовый промт для /speckit.specify

> Можно скопировать целиком (4-backtick блок ниже) и вставить в `/speckit.specify`.

`````
Реализуй TASK-52: фикс блокирующего bug'а — HomeActivity показывает вечную «Загрузка…» после wizard'а на реальном устройстве.

ЧТО СТРОИМ:
После завершения wizard'а на Xiaomi 11T (Android 12, MIUI) главный экран HomeActivity показывает только надпись «Загрузка…» в центральной области (где должны быть плитки), и эта надпись не пропадает никогда. Нижняя панель табов (BottomFlowBar) при этом видна — баг локализован в области flow slot'а внутри HomeScreen. Нужно: (1) найти root cause, (2) пофиксить инициализацию flow slot'а, (3) заменить вечный спиннер на state machine Loading → Ready | Error с error UI и кнопкой Retry.

ЗАЧЕМ:
End-to-end сценарий «новый пользователь установил приложение → проходит wizard → пользуется главным экраном» не работает на целевом устройстве. TASK-7 (Simple Launcher first-run + Setup Wizard) формально помечен Done, но фактически бесполезен — главный экран нерабочий. Phase 2 (контактные плитки, SOS, синхронизация) бессмысленна без рабочего Home. Дополнительно: пожилой пользователь не знает что делать с «зависшим» экраном — нарушение elderly-friendly UX.

SCOPE ВКЛЮЧАЕТ:
1. Диагностика root cause на железе:
   - Воспроизведение на Xiaomi 11T + pixel_5_api_34 emulator со свежей установкой.
   - Сбор logcat между WizardActivity.finish() и первой рекомпозицией HomeScreen.
   - Определение какой из подозреваемых сценариев истинный:
     (а) ConfigBackedFlowRepository.getFlows() возвращает пустой список из-за late-loading config'а;
     (б) активный preset не сохранён в presetRepository к моменту запуска HomeActivity;
     (в) HomeComponent.init отрабатывает раньше чем setActivePreset завершён;
     (г) Decompose lifecycle race condition на realBackend flavor;
     (д) что-то другое (раскроется в diagnostics).
2. Root-cause fix в зависимости от диагностики — изменение в одном или нескольких из: HomeComponent, ConfigBackedFlowRepository, WizardActivity finish flow, HomeActivity onCreate.
3. Failure-recovery UX в HomeScreen.kt: вместо вечной «Загрузка…» — state machine Loading (с таймаутом ≤ 3s) → Ready | Error. В состоянии Error: текст «Не удалось загрузить настройки» + кнопка «Попробовать снова» + кнопка «Сбросить и пройти настройку заново» (вызывает onResetData).
4. Fitness test (unit с fake FlowRepository либо instrumentation): HomeComponent с пустым FlowRepository → state переходит в Error в течение таймаута, не висит в Loading навсегда.
5. Smoke verification gates (manual, deferred):
   - Xiaomi 11T physical device: свежая установка → wizard → главный экран ≤ 3s, все 6 плиток видны ([deferred-physical-device]);
   - pixel_5_api_34 emulator: то же самое ([deferred-local-emulator]);
   - повторный запуск (kill + open): главный экран ≤ 1s без перепрохождения wizard'а.

SCOPE НЕ ВКЛЮЧАЕТ:
- Изменение wizard flow logic (TASK-7 уже Done).
- Локализация error UI на 10 языков (RU + EN тексты в этом spec; остальные локали — отдельный follow-up через procedure-translate-spec-strings).
- Telemetry / crash reporting об ошибке загрузки (Sentry — TASK-37, future).
- Animations / shimmer placeholder для Loading state (cosmetics, не входит в bug-fix).
- Другие preset'ы кроме simple-launcher (workspace, launcher) — отдельный smoke если время позволит.

DEPENDENCIES:
- TASK-7 (Simple Launcher first-run + Setup Wizard) — Done, мы фиксим его регрессию.
- TASK-51 (libsodium consolidation) — Done, baseline стопки.

ACCEPTANCE CRITERIA:
- [backlog] На свежей установке APK на Xiaomi 11T (Android 12, MIUI) главный экран с 6 плитками отображается в течение 3 секунд после нажатия «Готово» в wizard'е.
- [backlog] На свежей установке APK на эмуляторе pixel_5_api_34 — то же самое поведение (≤ 3 секунды до плиток).
- [backlog] Если по какой-то причине настройки не загрузились (симулируется через DI override / fake repository), пользователь видит error UI с текстом ошибки и кнопкой Retry, а не вечную «Загрузка…».
- [backlog] Все 6 плиток simple-launcher пресета (Телефон / SOS / Сообщения / Фото / Настройки / Помощь) видимы и тапабельны после первого запуска.
- [backlog] Повторный запуск приложения (kill + open) показывает главный экран без перепрохождения wizard'а и без задержки > 1 секунды.
- Технические (не [backlog]): fitness test покрывает empty FlowRepository → Error transition; logcat trace зафиксирован в research.md; cold start time не ухудшается относительно baseline (Article IX gate).

LOCAL TEST PATH:
- Unit: `./gradlew :core:testDebugUnitTest --tests "*HomeComponent*"` (новый тест на empty repository → Error transition).
- Instrumentation (опционально): `./gradlew :app:connectedRealBackendDebugAndroidTest --tests "*HomeActivityE2ETest*"` на pixel_5_api_34.
- Manual smoke: install fresh APK → wizard → измерить время до плиток секундомером, проверить все 6 плиток тапаются.

CONSTITUTION GATES:
- Article IV §5 / §III.3 (State management): UI state survives recreation / process death — fix не должен это сломать.
- Article IX (Performance): cold start + first-frame ≤ project budget, не ухудшается относительно baseline.
- Article XI (Meta-minimization): fix без добавления абстракций — inline state machine в HomeComponent, не отдельный LoadingViewModelFactory.
- CLAUDE.md rule 1 (domain isolation): state machine в commonMain без Android типов.
- CLAUDE.md rule 4 (Minimum Viable Architecture): без новых портов / адаптеров; правка существующих.
- CLAUDE.md rule 6 (mock-first): fake FlowRepository для unit-теста error path.

EFFORT: S–M (1–3 дня): 0.5d диагностика на железе, 0.5–1d fix, 0.5d failure-recovery UX, 0.5d тест + smoke.
`````
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [auto:deferred-physical-device] На свежей установке APK на Xiaomi 11T (Android 12, MIUI) пользователь проходит wizard → главный экран с 6 плитками отображается **в течение 3 секунд** после нажатия «Готово» (T052).
- [ ] #2 [auto:deferred-local-emulator] На свежей установке APK на эмуляторе pixel_5_api_34 — то же поведение (≤ 3 секунды до плиток) (T051).
- [ ] #3 [hand] Если настройки не загрузились (симулируется через fake `FlowRepository`, возвращающий пустой результат), `HomeComponent.loadingState` переходит в `Error` в течение 3s; Compose UI рендерит блок «Не удалось загрузить настройки» + кнопки «Попробовать снова» / «Сбросить настройки и пройти заново». Покрыто unit-тестами T012, T013, T014 + UI implementation T032.
- [ ] #4 [auto:deferred-physical-device] Все 6 плиток `classic-6` tile-set'а (Phone / Messages / Camera / Gallery / Contacts / Settings) видимы и тапабельны после первого запуска на Xiaomi 11T (T042).
- [ ] #5 [auto:deferred-physical-device] Повторный запуск приложения (kill + open) на Xiaomi 11T показывает главный экран **без перепрохождения wizard'а** и без задержки > 1 секунды (T052).
- [ ] #6 [hand] Кнопка «Сбросить настройки и пройти заново» защищена confirmation dialog'ом «Все настройки будут стёрты. Продолжить?» с кнопками «Сбросить» / «Отмена» (per Clarification Q7). Покрыто T033 + unit-тест T017.
- [ ] #7 [hand] Technical reason из `Error(reason)` пишется в logcat (WARN/ERROR), но **не показывается** пользователю в UI (per Clarification Q6). Покрыто T004 (logger.warn) + T034 (UI verify).
- [ ] #8 [auto:deferred-local-emulator] Baseline cold-start time на pixel_5_api_34 не ухудшается > 200ms относительно main APK (3 прогона median diff) (T050).
- [ ] #9 [auto:checklist] checklists/requirements-quality.md: 16/16 CHK [x]
- [ ] #10 [auto:checklist] checklists/meta-minimization.md: 13/13 CHK [x]
- [ ] #11 [auto:checklist] checklists/failure-recovery.md: 17/17 CHK [x]
- [ ] #12 [auto:checklist] checklists/state-management.md: 12/12 CHK [x]
- [ ] #13 [auto:checklist] checklists/ux-quality.md: 13/13 CHK [x]
- [ ] #14 [auto:checklist] checklists/elderly-friendly.md: 12/12 CHK [x]
- [ ] #15 [auto:checklist] checklists/performance.md: 10/10 CHK [x]
- [ ] #16 [auto:checklist] checklists/dev-experience.md: 13/13 CHK [x]
- [ ] #17 [auto:checklist] checklists/device-self-sufficiency.md: 8/8 CHK [x]
- [ ] #18 [auto:checklist] checklists/localization.md: 7/7 applicable [x]
- [ ] #19 [auto:checklist] checklists/localization-ui.md: 7/7 applicable [x]
- [ ] #20 [auto:checklist] checklists/permissions-platform.md: 6/6 applicable [x]
- [ ] #21 [auto:checklist] checklists/plan-level.md: 17/17 applicable [x]
<!-- AC:END -->

## Definition of Done

Закрывается по правилу `pre-pr-backlog-sync`: все `[hand]` AC → `[x]`, generated `[auto:checklist]` AC регенерируются из `specs/task-52-*/checklists/`, generated `[auto:deferred-*]` AC регенерируются из `tasks.md`. Переход `In Progress → Verification` после merge PR, `Verification → Done` после smoke на Xiaomi 11T + эмуляторе pixel_5_api_34.

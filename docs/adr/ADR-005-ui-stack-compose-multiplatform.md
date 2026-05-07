# ADR-005 — UI Stack: Compose Multiplatform + Material 3

**Status**: Accepted
**Date**: 2026-05-07
**Supersedes (in part)**: ADR-001 §Non-decisions and §Decision-2 (now reversed: код-шеринг через KMP/CMP принимается как обязательная стратегия для UI и domain слоя).
**Reaffirms**: ADR-001 §Decision-4 (platform-specific integrations изолированы) и §Decision-5 (Platform Parity Gate в каждом plan.md).

## Context

Продукт планируется к выпуску на двух платформах:
- **Android** — primary, включая launcher-mode (HOME, ROLE_HOME, AccessibilityService и т.п.).
- **iOS** — secondary, как обычное приложение под общим product brand (без launcher-режима — iOS не позволяет заменять home screen).

Раньше (ADR-001) кодошеринг отрицался как самоцель и предлагалось ограничиться shared product model на уровне документации. Практика показывает: для маленькой команды дублирование UI и domain слоя на Kotlin (Android) и Swift (iOS) даёт:

1. **Поведенческий drift** между платформами — две реализации одной spec расходятся в edge cases в течение недель.
2. **Двойную стоимость каждой фичи** — дизайн, реализация, тесты, ревью, регрессии.
3. **Синхронизация-долг** — каждый bugfix надо повторять; каждое изменение spec проходит через два независимых implementation cycles.
4. **Тесты не переиспользуются** — Robolectric/JUnit бесполезны для Swift.

К 2026 году Compose Multiplatform (JetBrains) имеет production-ready iOS-таргет, а Kotlin Multiplatform для domain слоя стабилен с 2023 года. Material 3 — официальный UI-стандарт от Google и доступен в Compose Multiplatform.

Этот ADR фиксирует переход с «shared product model only» (ADR-001) на «shared code where it pays» через KMP/CMP, сохраняя при этом дисциплину изоляции платформенных интеграций.

## Decision

### 1. Обязательный UI-стек

Все новые user-facing экраны и миграция существующих экранов **MUST** использовать **Jetpack Compose / Compose Multiplatform** с дизайн-системой на основе **Material 3**. Альтернативные UI-стеки (Flutter, React Native, нативный SwiftUI как первичный, классический Android View System) запрещены без явного исключения, документированного по Article XVII §3.

### 2. Обязательный стек domain слоя

Domain слой — **Kotlin Multiplatform**:
- `commonMain` — pure Kotlin: модели, репозитории-интерфейсы, use-cases, валидация, state machines, error types, configuration parsing.
- `androidMain` / `iosMain` — платформенные `actual`-реализации `expect`-объявлений из `commonMain`.

Прямой доступ к платформенным API (PackageManager, UIApplication и т.п.) из `commonMain` запрещён.

### 3. Платформенная изоляция (reaffirms ADR-001 §4)

Платформенно-специфичные API живут только в платформенных source-sets:
- **Android-only:** PackageManager, LauncherApps, ROLE_HOME, AccessibilityService, AppWidgets, DPC, Firebase Android SDK, BatteryManager, ConnectivityManager.
- **iOS-only:** UIApplication, UserNotifications, Keychain, Firebase iOS SDK.

`commonMain` видит их только через KMP-интерфейсы (`expect class` / interface + DI).

### 4. Каждая фича — двух-платформенно осознанная

Каждый `spec.md` и `plan.md` для новой фичи **MUST** явно отвечать:
- Какие части идут в `commonMain` (UI, domain, тесты)?
- Какие части идут в `androidMain` / `iosMain` и почему?
- Если фича принципиально невозможна на одной из платформ (например, замена home screen на iOS) — это фиксируется как **Documented Platform Asymmetry** с обязательным fallback или альтернативным сценарием.

### 5. Shared product model — остаётся (reaffirms ADR-001 §3)

Domain semantics, configuration semantics, entitlements, localization, partner/distribution concepts продолжают быть единым продуктом. Теперь они подкрепляются shared code (KMP commonMain), а не только shared docs.

### 6. Конкретный stack

| Слой | Библиотека | Альтернатива | Запрещено для new-code |
|---|---|---|---|
| UI runtime | Compose Multiplatform | — | Android View System, Fragments-based UI, SwiftUI как первичный |
| UI library | Material 3 (`compose.material3`) | Material 3 + кастомные компоненты в `:ui/components` | Material 2 для new-code; смешение стилей |
| Иконки | `material-icons-extended` | свои векторные ассеты в `commonMain/composeResources` | Растровые иконки как замена векторных |
| Навигация | Decompose **или** Voyager (выбор фиксируется в первом plan, использующем навигацию, как amendment к этому ADR) | — | `androidx.navigation:navigation-compose` для shared-кода (Android-only) |
| Persistence (structured) | SQLDelight | — | Room (Android-only) для new-code |
| Persistence (key-value) | `multiplatform-settings` | — | DataStore-androidx напрямую в shared-коде |
| Image loading | Coil 3 | — | Coil 2, Glide (Android-only) |
| Async | Kotlin Coroutines + Flow | — | RxJava (Android-only тренд), AsyncTask |
| DI | Koin **или** manual constructor DI (выбор фиксируется в первом plan, использующем DI, как amendment к этому ADR) | — | Hilt, Dagger для shared-кода (Android-only) |
| Networking | Ktor Client | — | OkHttp/Retrofit напрямую в shared-коде (Android-only ecosystem) |
| Serialization | kotlinx.serialization | — | Gson, Moshi (Android-only ecosystem) |
| Firebase | `gitlive/firebase-kotlin-sdk` сначала; платформенные SDK через `expect`/`actual` если KMP-обёртка недостаточна | — | прямой Firebase Android SDK в shared-коде |
| UI tests | `androidx.compose.ui:ui-test-junit4` через `createComposeRule` для Android-таргета; `kotlin.test` для commonMain unit-тестов | — | Robolectric Activity-тесты для new-code |

### 7. Структура модулей (минимальная, по Article V)

```
:core    KMP module
  commonMain      domain (модели, репозитории, use-cases, валидация, событийная шина)
  androidMain     Android actual'ы для expect'ов из commonMain
  iosMain         iOS actual'ы (создаётся пустым; заполняется когда iOS-разработка стартует)
  commonTest / androidUnitTest / androidInstrumentedTest / iosTest

:app     Android entry point
  Application, AccessibilityService, манифест, ROLE_HOME-binding, MainActivity / HomeActivity
  тонкий wrapper, который вызывает Compose UI из :core (или :ui если выделен)

:iosApp  iOS entry point (создаётся, когда iOS-разработка стартует)
  Swift bootstrap, AppDelegate, вызов Compose entry function
```

Дополнительные модули вводятся только при удовлетворении Article V §5.3 (clear ownership boundary, build isolation, independent enable/disable, stable API boundary, materially better testability).

### 8. Senior-safe design overrides

`docs/dev/design-system.md` обязан фиксировать минимум:
- Минимальный размер body-текста: 18sp.
- Минимальный tap-target: 56dp.
- Минимальный контраст: 4.5:1.
- Запрет на иконки без подписи в primary-actions.
- Анимации опциональны (Article VIII §5).

Эти правила переопределяют Material 3 defaults и применяются глобально через `MaterialTheme`.

## Constitutional exceptions

Этот ADR фиксирует documented exceptions (Article XVII §3) к default-правилам:

| Article | Default rule | Exception scope | Reason | Removal condition |
|---|---|---|---|---|
| **XI §1** | Prefer platform/framework directly over wrappers | Compose Multiplatform — JetBrains-обёртка над Android Compose и iOS UIKit, а не platform-direct. | Cross-platform parity ценнее direct platform access; альтернатива (дублирование стека) выше по всем параметрам. | CMP iOS-таргет деградирует ниже production-bar; либо Google выпускает официальный Android-cross-platform stack эквивалентного качества. |
| **XIII §4** | Prefer official Android guidance | Compose Multiplatform, SQLDelight, Coil 3, Decompose/Voyager, Koin, Ktor — JetBrains/community, не Android-recommended. | Same reason: parity > Android-officiality для двух-платформенного продукта. | Same as above. |

Эти exceptions действуют **только** в scope cross-platform UI и domain слоя. Android-only функциональность (HOME, ROLE_HOME, AccessibilityService, DPC, виджеты, Android-only Firebase features) продолжает использовать Android-official APIs из `androidMain`.

## Mandatory gates for every plan.md

В дополнение к Constitution Check (Article XVI), каждый plan.md, затрагивающий код, **MUST** содержать:

### Cross-Platform Implementation Gate

Для каждого нового UI-экрана и каждой domain-операции явно указано, в каком source-set (`commonMain` / `androidMain` / `iosMain`) живёт код, и почему. Default — `commonMain`; deviation требует обоснования.

### Documented Platform Asymmetry

Если фича не переносится на iOS (или Android) — указать причину, fallback, и спрятан ли gap от пользователя или явно объяснён.

### Resource Budget Delta

CMP-стек overhead принимается один раз (см. «Required performance targets» ниже) и отдельно по каждой фиче не пересчитывается. Новые зависимости **сверх** stack этого ADR требуют явного резюме: size impact, transitive deps, KMP-совместимость.

## Required performance targets

Эти пороги обязательны для каждого plan.md, который добавляет или меняет UI/domain:

| Метрика | Target | Hard fail |
|---|---|---|
| APK debug | ≤ 18 МБ | > 22 МБ |
| APK release | ≤ 12 МБ | > 16 МБ |
| Cold start `HomeActivity` (medium-tier Android, e.g. Pixel 4a) | ≤ 600 мс | > 900 мс |
| Cold start `FirstLaunchActivity` (medium-tier Android) | ≤ 700 мс | > 1000 мс |
| Frame budget на основных скроллах (Workspace grid, Settings list) | 0 dropped frames на medium-tier | > 0 на medium-tier |
| iOS app cold start (когда применимо) | ≤ 800 мс на iPhone SE 2nd gen | > 1200 мс |
| TalkBack/VoiceOver — путь до primary action | ≤ 3 свайпа на каждом экране | > 4 |

Любая фича, которая не укладывается в Target и не имеет явного и одобренного исключения, считается performance-регрессом (Article IX §1).

## Non-decisions

Этот ADR не фиксирует:
- ~~Окончательный выбор между **Decompose** и **Voyager** для навигации.~~ **Закрыто Amendment 2026-05-07a (см. ниже): Decompose.**
- ~~Окончательный выбор между **Koin** и **manual DI**.~~ **Закрыто Amendment 2026-05-07a (см. ниже): Koin.**
- ~~Когда использовать Cupertino-look на iOS vs Material 3 на iOS.~~ **Закрыто Amendment 2026-05-07b (см. ниже): Material 3 на обеих платформах на старте.**
- Точный момент включения `iosMain` source-set и создания модуля `:iosApp` — определяется отдельным spec'ом, который инициирует iOS-разработку.
- Когда переходить от `gitlive/firebase-kotlin-sdk` к платформенным Firebase SDKs через `expect`/`actual` (зависит от того, какие Firebase features понадобятся).

Эти не-решения переходят в open questions, которые закрываются по мере появления первых конкретных plan'ов.

---

## Amendment 2026-05-07a — Navigation and DI choices

**Status**: Accepted
**Date**: 2026-05-07
**Closes**: Non-decisions §1 (Decompose vs Voyager) и §2 (Koin vs manual DI) of this ADR.

### Decision

1. **Навигация — Decompose.** Причина:
   - product root-routing нетипичный: корень держит выбор preset (Workspace / Launcher / Simple Launcher) и позже — переключение между OLD-режимом и admin-режимом (шаг 8 плана). Это естественнее ложится на компонент-модель Decompose (`RootComponent` + `StackNavigation<Config>`), чем на Composable-стек Voyager;
   - типизированный routing (sealed `Config` классы) даёт compile-time safety при добавлении новых экранов и режимов;
   - state preservation при пересоздании конфигурации работает из коробки, что важно для app-critical surfaces (Article III §3).

2. **DI — Koin.** Причина:
   - проект уже стартует с ~10–15 классов в domain/integration слое и быстро вырастет (specs 004–009 добавляют ProviderRegistry, ActionLauncher, RemoteSyncBackend, FirebaseRemoteSyncBackend, ConfigRepository, ConflictResolver, RequirementProbe, RequirementResolver, и т.д.);
   - на этом масштабе manual constructor DI накапливает boilerplate в `Application`/`RootComponent`, что мешает читать главный entry-point;
   - Koin — KMP-совместимая, минимальная по runtime-cost (~150 КБ), без code-generation и без compile-time зависимости от платформы.

### Constitutional impact

Article XI §1 (prefer platform/framework directly): Koin — обёртка-service-locator, формально это абстракция. Это уже покрыто constitutional exception этого ADR (XI §1 exception scope: cross-platform UI и domain слой). Koin лежит внутри этого scope.

### Removal condition for this amendment

- Если объём dependency graph упадёт до уровня <10 классов с тривиальными связями — Koin может быть заменён обратно на manual DI. Маловероятно по мере развития продукта.
- Если KMP-поддержка Koin деградирует (заброшен, баги на iOS) — переключение на альтернативу через отдельное amendment.

### Implications для шага 1 плана

- В `gradle/libs.versions.toml` добавляется Koin (`io.insert-koin:koin-core` для commonMain, `io.insert-koin:koin-android` для androidMain) и Decompose (`com.arkivanov.decompose:decompose` + `com.arkivanov.decompose:extensions-compose`).
- `Application` собирает Koin-модули, `RootComponent` (Decompose) получает зависимости через `KoinComponent` или конструктор-инъекцию.
- Все existing классы из текущего `core/` сохраняют constructor-injection форму — Koin их регистрирует, но не меняет API классов.

---

## Amendment 2026-05-07b — UI look on both platforms

**Status**: Accepted
**Date**: 2026-05-07
**Closes**: Non-decisions §3 (Cupertino-look vs Material 3 on iOS) of this ADR.

### Decision

**Material 3 на обеих платформах** на старте. На iOS приложение будет иметь Material-look, а не нативный Cupertino-look.

### Reasons

1. **Меньше работы** — один дизайн-система покрывает обе платформы; не нужно писать параллельный набор Cupertino-компонентов.
2. **Консистентность бренда** — пользователь, видящий приложение на Android и iPad/iPhone, узнаёт продукт по визуалу.
3. **Senior-safe override темы** (≥18sp body, ≥56dp tap-target, контраст ≥4.5:1) применяется одинаково через `MaterialTheme` — не дублируется в Cupertino-стеке.
4. **На старте достаточно «нормально выглядеть»** — соответствует Article II §6 «simple now, extensible only when evidence exists».

### Removal condition

- Если App Store ревью отвергает приложение из-за «Not native iOS look» (для consumer-store это редко, для enterprise/managed deployment — почти никогда).
- Если пользовательское исследование на iOS показывает заметный UX-регресс из-за неродного look.
- Если бренд-стратегия требует разделить визуал между платформами.

В этих случаях вводится отдельное amendment с переходом на selective Cupertino-стилизацию (compose-cupertino от alexzhirkevich) — но **не раньше реальной проблемы**.

### Implications

- В дизайн-системе (`docs/dev/design-system.md`) фиксируется один Material 3 theme; платформенных вариантов нет.
- iOS app, когда подключится, будет визуально Material 3 — это документируется в onboarding для iOS-команды (когда появится).
- Если в будущем понадобится мелкая платформенная настройка (например, нативный системный share-sheet) — это решается через `expect`/`actual` UI components, не через смену всего стека.

## Migration impact

| Документ / артефакт | Воздействие |
|---|---|
| `docs/adr/ADR-001-cross-platform-strategy.md` | Status → "Accepted; partially superseded by ADR-005 (2026-05-07)". Decision §2 («кодошеринг не цель») reversed. Decision §3 (shared product model) reaffirmed. Decision §4 (platform isolation) reaffirmed. Decision §5 (Platform Parity Gate) reaffirmed. |
| `docs/governance/document-map.md` | Добавить ADR-005 в список рекомендуемых ADR. |
| `docs/product/context-decisions-and-open-questions.md` | Упоминания ADR-001 в контексте cross-platform стратегии дополнить указанием на ADR-005. |
| `.specify/memory/constitution.md` | **Не меняется.** Article XVII §5: архитектурные решения идут в ADR, а не раздувают конституцию. |
| `specs/002-whatsapp-tile-return/*` | **Не меняется.** Закрытая фича, ссылается на ADR-001 в его исходном виде — это исторически корректно. |
| `specs/003-ui-skeleton/*` | Не меняется напрямую. UI-миграция этого скелета на Compose Multiplatform идёт отдельным новым spec'ом, ссылается на ADR-005. |
| Будущие spec'и 004+ | Обязаны ссылаться на ADR-001 + ADR-005 и проходить новые gates выше. |

## Consequences

### Плюсы

- Один источник правды для domain и UI; parity по умолчанию.
- Resource Budget overhead принимается один раз — добавление iOS не множит цену каждой фичи на 2.
- Поведенческий drift между платформами минимизирован.
- Тесты `commonMain` покрывают обе платформы.
- Команда работает в одном языке (Kotlin) для подавляющей части кодовой базы.
- Spec Kit процесс (spec → plan → tasks) остаётся единым для обеих платформ.

### Минусы (принимаются)

- APK overhead +5–15 МБ; iOS IPA +8–15 МБ.
- RAM overhead +30–80 МБ при активном Compose runtime.
- Cold start `HomeActivity` +200–500 мс относительно native View System.
- Часть Android-official ecosystem (Hilt, Room, Navigation-Compose AndroidX, Coil 2, OkHttp/Retrofit) недоступна для shared code.
- iOS-сторона CMP менее зрелая, чем Android-сторона; некоторые edge-cases могут потребовать платформенного workaround.
- Зависимость от темпа развития CMP / Material 3 у JetBrains.

### Mitigation

- Measurable performance targets выше — обязательны.
- Senior-safe override темы Material 3 — обязательная часть `docs/dev/design-system.md`.
- iOS-pilot экран (когда iOS-разработка стартует) — после первой production-ready CMP-фичи на Android, перед массовой iOS-миграцией.
- Платформенные `expect`/`actual` контракты ревьюются на границе minimum необходимого, чтобы избежать утечки platform-API в commonMain.

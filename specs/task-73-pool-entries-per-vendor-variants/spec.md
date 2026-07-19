# Feature Specification: Vendor-aware dispatch for OEM-sensitive Providers

**Feature Branch**: `task-73-pool-vendor-variants`
**Created**: 2026-07-18
**Status**: Draft
**Input**: TASK-73 — сделать так, чтобы `LauncherRole` (и другие OEM-чувствительные `Component`) корректно проверялся и применялся на Xiaomi MIUI, Huawei без GMS, Samsung One UI и других OEM, а не только на чистом Pixel.

## Контекст (что уже построено, версия 2 — после grounding-коррекции 2026-07-18)

**Важно**: первая версия этой спеки (см. git-историю) была написана на терминологии TASK-65 (`CheckSpec`/`ApplySpec`/`Pool`/`PoolEntry`/`ConfigSource`), которая была полностью заменена каноническим ECS (TASK-136, [ADR-013](../../docs/adr/ADR-013-canonical-ecs.md), смержен 2026-07-18). Эта версия написана против **реального текущего кода**, проверенного напрямую.

Актуальная модель ([`docs/architecture/ecs.md`](../../docs/architecture/ecs.md) — источник истины):

- **`Component`** (`core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt`) — закрытый `sealed interface`, 11 подтипов (`AppTile`, `FontSize`, `Sos`, `Toolbar`, `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`, `Workspace`, `Flow`, `ToolbarButton`).
- **`Provider<T : Component>`** (`core/src/commonMain/kotlin/com/launcher/preset/port/Provider.kt`) — `suspend fun check(component, profile): Outcome` / `suspend fun apply(component, profile): Outcome`. Один `Provider` на тип `Component`. Это современная замена «CheckSpec/ApplySpec».
- **`Outcome`** (`core/src/commonMain/kotlin/com/launcher/preset/model/Outcome.kt`) — `Ok | NeedsApply | Failed(FailReason) | Unsupported`. `ReconcileEngine` транслирует `Outcome` в `LifecycleState`-компонент на entity (`Pending`/`Applied`/`Skipped`/`Unverifiable`/`Failed(reason)`) — это отдельный, более поздний шаг, не результат самого `Provider`.
- **`ProviderRegistry` / `HandlerKey`** (`core/src/commonMain/kotlin/com/launcher/preset/port/ProviderRegistry.kt`, `.../model/HandlerKey.kt`) — уже существует **3-tier fallback**: `(type, platform, vendor) → (type, platform, null) → (type, null, null) → NoOpProvider`. **Вендорский tier уже спроектирован**, но сегодня не задействован: `PresetModule.kt:121` строит `DefaultProviderRegistry(handlers, runtimePlatform = "Android", runtimeVendor = null)` — `runtimeVendor` захардкожен `null`, ни один `HandlerKey` нигде не регистрируется с непустым `vendor`.
- **`enum class Vendor { Xiaomi, Samsung, Huawei, GoogleTV, GenericAndroid, iOS }`** (`core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt:62`) — **уже существует**. Не нужно изобретать `VendorProfile`.
- **`LauncherRoleProvider`** (`app/src/main/java/com/launcher/app/preset/task120/provider/LauncherRoleProvider.kt`) — единственная сегодняшняя реализация `Provider<Component.LauncherRole>`: `RoleManager.isRoleHeld`/`createRequestRoleIntent` (API ≥29), fallback на `Intent.ACTION_MAIN+CATEGORY_HOME` resolve (API 26-28). Vendor-blind — именно та проблема, которую описывает TASK-73.
- **`GmsAvailabilityPort`** (`core/src/commonMain/kotlin/com/launcher/api/setup/GmsAvailabilityPort.kt`, TASK-49) — уже есть, используется здесь для различения "есть GMS"/"нет GMS" веток отдельно от vendor-определения.
- **`PoolSource`/`PresetSource`/`HintPoolSource`** — три независимых порта, каждый со своим `Bundled*Source`-адаптером в `app/src/main/java/com/launcher/app/preset/task120/adapter/`, каждый с `// TODO(shareability)`. **`ConfigSource`/`ConfigKind` не существуют** — это не общий диспетчер, а паттерн «один порт на один артефакт», которому новый `VendorRecipeSource` следует, а не расширяет несуществующий тип.

**Ключевое архитектурное решение этой версии спеки** (см. Assumptions «Почему recipe внутри Provider»): вендорский tier `HandlerKey.vendor` в `ProviderRegistry` **не используется** для этой фичи — он потребовал бы отдельного скомпилированного `Provider`-класса на каждый vendor (= новый релиз на каждый новый override), что прямо противоречит FR-005/SC-003 («новый override для известного vendor — без пересборки APK»). Вместо этого `LauncherRoleProvider` становится vendor-aware **изнутри**, консультируясь с новым портом `VendorRecipeSource` (данные) + `VendorDetector` (текущий `Vendor` устройства). Это согласуется с `PoolAntiExplosionTest` (fitness-тест, штрафующий «catalogue explosion» вместо параметризации) и не создаёт новую систему диспетчеризации там, где одна уже есть на уровне `Provider.apply()`.

Эта спека не меняет *что* можно настраивать (набор `Component`), только *как* `Provider.check()`/`apply()` находят правильный путь на конкретном устройстве.

## Clarifications

### 2026-07-18 — Pre-plan clarification pass (типы ниже актуализированы после grounding-коррекции; сами решения не изменились)

| # | Question | Resolution |
|---|----------|------------|
| 1 | Vendor-идентификатор — закрытый набор или открытый string-backed тип? | Closed. Используется **уже существующий** `enum class Vendor` (не новый `VendorProfile`). Новое значение enum'а (не из текущих 6) — осознанный one-way door, требующий правки Kotlin-кода; «без изменения кода» гарантируется только для новых recipe-override'ов уже известных `Vendor`-значений, не для добавления нового значения как такового. |
| 2 | Суббренды (Redmi/POCO → Xiaomi) — alias-таблица или Build.MANUFACTURER as-is? | Explicit alias table внутри Android-адаптера нового порта `VendorDetector`. Redmi/POCO — самые массовые устройства среди целевой аудитории; без alias они бы молча падали в `Vendor.GenericAndroid`. |
| 3 | Приоритет: только manufacturer, или GMS-доступность — часть vendor-определения? | Manufacturer-only. `Vendor.Huawei` выставляется по `Build.MANUFACTURER` независимо от GMS; `GmsAvailabilityPort` проверяется отдельно внутри `LauncherRoleProvider.apply()`, только там, где GMS-доступность реально влияет на выбор intent. Корректно обрабатывает Huawei-устройства с GMS (модели до бана 2019 года). |
| 4 | Отсутствующий/битый `fallbackTextKey` в recipe — что показываем? | Универсальный **per-Component-type** (не per-vendor, не единая фраза на всё приложение) default-текст через `LocalizedResources.resolve(...)` (`core/src/commonMain/.../preset/port/LocalizedResources.kt` — тот же порт, что уже использует остальной `preset/`-код, не второй похожий `StringResolver` из `api/localization/`). Работает одинаково для любого OEM-чувствительного `Component` без хардкода единственной строки на всё приложение. |

### 2026-07-18 — Grounding correction (после /speckit.plan research)

`CheckSpec`/`ApplySpec`/`PoolEntry`/`ConfigSource`/`ConfigKind`/`VendorProfile` из первой версии этой спеки заменены на реальные `Component`/`Provider`/`Vendor`/`VendorDetector`/`VendorRecipeSource`. `android.permission.POST_NOTIFICATIONS` убран из минимального покрытия (FR-005) — permissions не смоделированы как `Component` в текущей ECS, для них нет `Provider`, добавлять новый `Component`-подтип — отдельная, не бюджетированная в этой спеке работа. Минимальное покрытие v1 сужено до `Component.LauncherRole`, единственного OEM-чувствительного компонента, который реально достижим в live-потоке (Wizard/Settings) сегодня.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Xiaomi MIUI: настройка HOME launcher открывает правильный экран (Priority: P1)

Пользователь на Xiaomi (MIUI) в Wizard/Settings нажимает «Сделать launcher по умолчанию». Вместо стандартного Android-диалога выбора роли (который на MIUI часто не применяется после тапа «Да») открывается путь, специфичный для MIUI: Настройки → Приложения → Приложения по умолчанию → Домашний экран.

**Why this priority**: без этого MIUI-пользователь (значимая доля целевой аудитории) застревает на шаге, который выглядит пройденным, но не сработал — silent failure, худший вид UX-бага.

**Independent Test**: на Xiaomi-эмуляторе (или Firebase Test Lab Xiaomi Redmi image) вызвать `LauncherRoleProvider.apply()` с загруженным MIUI-рецептом и проверить, что запущенный intent ведёт на MIUI-специфичный экран, а не на generic `RoleManager` dialog.

**Acceptance Scenarios**:

1. **Given** устройство определено как `Vendor.Xiaomi` и загружен recipe с MIUI-override для `LauncherRole`, **When** пользователь запускает Apply, **Then** открывается MIUI-специфичный intent (не generic `ACTION_REQUEST_ROLE`).
2. **Given** MIUI-override отсутствует в текущем recipe-каталоге, **When** пользователь запускает Apply, **Then** система падает обратно на существующий generic Android путь (без краша) — поведение, идентичное сегодняшнему `LauncherRoleProvider`.

---

### User Story 2 — Huawei без GMS: честный статус вместо краша (Priority: P1)

На устройстве без Google Play Services (Huawei EMUI/HarmonyOS без GMS) вызов `RoleManager`-based `check()` сегодня может бросить исключение или дать неопределённый результат. Вместо этого пользователь должен увидеть честный `Outcome`/`LifecycleState` («не настроено», без краша) и, если системный путь недоступен, — структурированную текстовую инструкцию, куда идти руками.

**Why this priority**: EMUI-без-GMS — устройства, где меньше всего системных гарантий; молчаливый краш здесь означает, что пользователь вообще не понимает, что делать.

**Independent Test**: на Huawei-без-GMS образе (Firebase Test Lab EMUI image, `GmsAvailabilityPort` возвращает unavailable) вызвать `check()` — убедиться, что результат `Outcome.NeedsApply`/`Outcome.Unsupported`, не исключение; вызвать `apply()` — убедиться, что при `resolveActivity() == null` возвращается `Outcome.Failed(FailReason.InternalError(fallbackTextKey))`, и что этот текст доходит до пользователя через существующий `ApplyResult.Failed`-путь Settings (TASK-69), без нового UI-механизма.

**Acceptance Scenarios**:

1. **Given** `GmsAvailabilityPort` = unavailable и `Vendor.Huawei`, **When** `LauncherRoleProvider.check()` вызывается, **Then** результат — `Outcome.NeedsApply` (не `Ok`), без exception.
2. **Given** ни vendor-override, ни generic intent не резолвятся, **When** пользователь запускает Apply, **Then** `apply()` возвращает `Outcome.Failed(FailReason.InternalError(fallbackTextKey))`, и Settings (через существующий `EngineSettingsGateway`/`ApplyResult.Failed`) показывает локализованную инструкцию «Откройте Настройки → …» пользователю немедленно — не тихий no-op.

---

### User Story 3 — Автор recipe добавляет override для уже известного vendor без пересборки APK (Priority: P2)

Инфраструктурный автор (не обязательно программист приложения) добавляет override для уже известного `Vendor` (например, новую версию MIUI-intent для `Xiaomi`) в `vendor-recipes.json` и раздаёт его через новый порт `VendorRecipeSource`. Приложению не нужен новый релиз, чтобы получить это покрытие.

**Why this priority**: OEM-матрица меняется быстрее релизного цикла приложения; без этого каждый новый vendor-баг требует полного релиза.

**Independent Test**: положить обновлённый `vendor-recipes.json` через тестовый `VendorRecipeSource`, перезапустить чтение — новый override dispatch'ится без изменений в Kotlin-коде.

**Acceptance Scenarios**:

1. **Given** новый `vendor-recipes.json` с override для уже известного `Vendor.Xiaomi`, **When** приложение перечитывает recipe catalogue, **Then** `LauncherRoleProvider` использует новый override без изменения кода.
2. **Given** recipe ссылается на `componentType`, которого не существует в текущем `Component`-наборе (или на неизвестное значение `Vendor`), **When** каталог загружается, **Then** чтение не падает — неизвестная запись игнорируется, ошибка логируется (см. Edge Cases, тот же принцип, что TASK-131 закладывает для остального ECS-чтения).

---

### User Story 4 — CI ловит OEM-регрессию до мержа (Priority: P3)

При изменении `LauncherRoleProvider` (помечен как OEM-чувствительный) PR с меткой `oem-matrix-required` прогоняет инструментальные тесты минимум на трёх устройствах (Pixel, Samsung Galaxy S24, Xiaomi Redmi) через Firebase Test Lab, прежде чем изменение попадёт в `main`.

**Why this priority**: без автоматической проверки OEM-регрессии обнаруживаются только пользователями в проде — ценно, но не блокирует MVP этой фичи (вручную прогоняемо на первом этапе).

**Independent Test**: изменить `LauncherRoleProvider`, открыть PR с меткой `oem-matrix-required`, убедиться, что Firebase Test Lab job запускается и репортит статус по каждому из трёх устройств.

**Acceptance Scenarios**:

1. **Given** PR с меткой `oem-matrix-required` меняет OEM-чувствительный `Provider`, **When** CI запускается, **Then** инструментальный тест проходит на Pixel 8 + Samsung Galaxy S24 + Xiaomi Redmi отдельно, и лог показывает per-device результат.
2. **Given** PR без метки `oem-matrix-required`, **When** CI запускается, **Then** OEM-matrix job не запускается (не блокирует обычные PR).

### Edge Cases

- Устройство от производителя, не входящего в известный `Vendor` enum (например, редкий китайский бренд) → `Vendor.GenericAndroid`, используется существующий default-путь `LauncherRoleProvider` — тот же путь, что работал до этой фичи.
- `vendor-recipes.json` отсутствует, повреждён или не проходит schema-валидацию при старте → приложение продолжает работать на существующем default-поведении `LauncherRoleProvider`, ошибка парсинга логируется, не крашит запуск (тот же принцип, что TASK-131 закладывает для остального ECS-чтения).
- `GmsAvailabilityPort` сам бросает исключение при проверке → трактуется как unavailable (fail-safe в сторону "нет GMS", не в сторону краша).
- Recipe ссылается на значение `Vendor`, которого приложение ещё не знает (recipe написан для более новой версии приложения) → неизвестное значение игнорируется при парсинге, dispatch падает на default.
- Пользователь отменяет системный диалог/intent на середине Apply-потока (Xiaomi/Huawei fallback экран) → `LifecycleState` остаётся `Pending`/не переходит в `Applied`, повторный запуск возможен, ничего не ломается — тот же инвариант, что уже проверен для generic Android-пути в TASK-69 SEQ-3.
- Firebase Test Lab недоступен/квота исчерпана в момент PR → OEM-matrix job помечается как inconclusive (не blocking silently зелёным), PR не мержится до ручной проверки — см. Local Test Path.
- Устройство сообщает суббренд без записи в alias-таблице (например, редкий Xiaomi sub-brand, добавленный OEM позже) → `Vendor.GenericAndroid`, тот же путь, что и для незнакомого производителя — расширение alias-таблицы не требует изменения recipe-формата, только код адаптера (см. Clarifications #2).
- Vendor-override ссылается на `fallbackTextKey`, отсутствующий в строковых ресурсах приложения → `FailReason.InternalError` несёт universal per-Component-type default-ключ вместо битого, `LocalizedResources.resolve(...)` никогда не получает несуществующий ключ (см. FR-003, Clarifications #4).

## Out of Scope

- Активация вендорского tier'а `HandlerKey.vendor`/`runtimeVendor` в `ProviderRegistry` — он остаётся незадействованным этой спекой (см. «Ключевое архитектурное решение» выше); может понадобиться позже для компонентов, которым нужна структурно другая реализация на vendor, не просто другой intent.
- Runtime-докачка только нужных vendor-recipe записей (сейчас каталог грузится целиком как bundled asset; выборочная докачка — отдельная задача оптимизации).
- Vendor-override для сторонних (не системных Android) SDK — только для системных surface.
- `POST_NOTIFICATIONS` и любые permission-based записи — нет соответствующего `Component`/`Provider` в текущей ECS; добавление нового `Component`-подтипа под permissions — отдельная спека.
- `StatusBarPolicy` и другие OEM-чувствительные `Component`, которые существуют в коде, но не референсятся ни одним bundled-пресетом (TASK-69 finding) — получают vendor recipes только когда станут реально достижимы через живой Preset; иначе повторяем тот же «нечего тестировать end-to-end» разрыв, что TASK-69 уже нашла.
- Полное покрытие Oppo/Vivo/OnePlus/Honor (autostart manager, battery optimization экраны) — новые значения `Vendor` для них не добавляются в этой спеке; см. OEM Matrix.
- Samsung Knox-специфичные device-policy пути — generic fallback приемлем для MVP, отдельный Knox-путь не реализуется.
- Procurement физических устройств для тестирования — решение владельца, не техническая задача этой спеки.
- Изменение таймингов/UX первого запроса permission — эта спека не трогает permission-флоу вообще (см. пункт про `POST_NOTIFICATIONS` выше).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Приложение MUST определять `Vendor` устройства через новый порт `VendorDetector` (`fun detect(): Vendor`, `core/src/commonMain/.../preset/port/`), Android-адаптер читает `Build.MANUFACTURER`/`Build.BRAND`. Адаптер MUST нормализовать известные суббренды через explicit alias-таблицу (минимум: `Redmi`, `POCO` → `Vendor.Xiaomi`), иначе устройства этих суббрендов молча получают `Vendor.GenericAndroid` вместо релевантного override'а (см. Clarifications #2). Неизвестный manufacturer → `Vendor.GenericAndroid` (переиспользует существующее enum-значение, новый «Generic»-концепт не вводится).
- **FR-002**: `GmsAvailabilityPort` MUST оставаться вне `VendorDetector` — определение `Vendor` только по производителю; GMS-доступность проверяется отдельно, внутри `LauncherRoleProvider`, только там, где реально влияет на выбор intent (Huawei-ветка). Корректно обрабатывает Huawei-устройства с GMS (модели до 2019 года) — см. Clarifications #3.
- **FR-003**: `LauncherRoleProvider.apply()` MUST пробовать fallback-цепочку в порядке: vendor-специфичный intent (из `VendorRecipeSource`, ключ — текущий `Vendor`) → существующий generic Android путь (unchanged). Если ни один intent не резолвится (`resolveActivity() == null`), `apply()` MUST вернуть `Outcome.Failed(FailReason.InternalError(messageKey = fallbackTextKey, args))` — **не** показывать UI самостоятельно (`Provider` — адаптер, не UI-слой; показ диалогов — забота вызывающей стороны). Существующий канал уже доставляет это до пользователя: `ReconcileEngine` транслирует `Outcome.Failed` в `LifecycleState.Failed(reason)`; для интерактивного пути (`RunMode.Single`, тот же, что использует TASK-69 SEQ-3) `EngineSettingsGateway.apply()` уже возвращает `ApplyResult.Failed(reason)` немедленно по тапу — эта спека только поставляет правильный `fallbackTextKey`, не строит новый UI-механизм. Если vendor-override не задаёт `fallbackTextKey`, используется универсальный per-Component-type default-ключ через `LocalizedResources` (см. Clarifications #4). Провал apply не меняет `LifecycleState` необратимо — повторный тап на настройку в Settings в любой момент запускает Apply заново (без авто-retry).
- **FR-004**: `LauncherRoleProvider.check()` MUST возвращать существующий словарь `Outcome` (`Ok`/`NeedsApply`/`Failed(FailReason)`/`Unsupported`) без исключений, при отсутствии vendor-специфичного пути проверки или ошибке платформенного вызова — переиспользует уже существующий тип, не вводит новый.
- **FR-005**: Recipe catalogue MUST загружаться как отдельный wire-format файл (`vendor-recipes.json`) через **новый** порт `VendorRecipeSource` (`core/src/commonMain/.../preset/port/VendorRecipeSource.kt`) + Android-адаптер `BundledVendorRecipeSource` (`app/.../preset/task120/adapter/`), по образцу уже существующих `PoolSource`/`PresetSource`/`HintPoolSource` (один порт — один артефакт — один `Bundled*`-адаптер с `// TODO(shareability)`), не как расширение несуществующего `ConfigSource`.
- **FR-006**: Recipe wire-format MUST нести явное поле `schemaVersion` и структуру `Map<componentTypeDiscriminator: String, Map<vendorName: String, VendorOverride>>`, где `componentTypeDiscriminator` — тот же `@SerialName`-дискриминатор, что уже используется для сериализации `Component` (например `"LauncherRole"`) — переиспользует существующую стабильную id-схему, не вводит новый `PoolEntryId`-подобный идентификатор (per CLAUDE.md rule 5). Текущая версия — `schemaVersion=1` (первый коммит формата); чтение версий ≤ текущей MUST оставаться возможным минимум один major release вперёд, начиная с момента, когда появится `schemaVersion=2`.
- **FR-007**: Recipe reader MUST игнорировать неизвестные `componentTypeDiscriminator` и неизвестные значения `Vendor` без падения чтения целиком.
- **FR-008**: Минимальное recipe-покрытие v1 MUST включать `Component.LauncherRole`, минимум 3 vendor-override (Xiaomi, Huawei, Samsung) — единственный сегодня реально достижимый в живом потоке (Wizard/Settings) OEM-чувствительный `Component` (см. Out of Scope про `StatusBarPolicy`/`POST_NOTIFICATIONS`).
- **FR-009**: CI MUST предоставлять Firebase Test Lab job, запускаемый по PR-метке `oem-matrix-required`, прогоняющий инструментальные тесты минимум на трёх устройствах (Pixel 8, Samsung Galaxy S24, Xiaomi Redmi) для изменений `LauncherRoleProvider`.
- **FR-010**: Манифест MUST декларировать `<queries>` записи (package visibility, Android 11+) для каждого OEM-специфичного пакета, на который ссылается vendor-override intent (например, `com.miui.securitycenter` для Xiaomi) — без этого `resolveActivity()` не находит компонент даже при его фактическом наличии, и fallback срабатывает ошибочно.
- **FR-011**: Изменение MUST быть отражено в [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — новые `<queries>`-записи из FR-010 добавляются в реестр permissions/queries приложения.
- **FR-012**: Каждый неуспешный dispatch (`check()` → `NeedsApply`/`Unsupported`, `apply()` дошёл до fallback-диалога) MUST логироваться структурированным событием с категорией (`vendor`, `componentType`, `outcome`), без PII, чтобы разработчик мог диагностировать OEM-регрессию по логам без физического устройства.

### Key Entities

- **`Vendor`** (существующий `enum`, не новый тип) — переиспользуется как есть; новые значения не добавляются в этой спеке.
- **`VendorDetector`** (новый порт) — возвращает текущий `Vendor` устройства; Android-адаптер — единственное место, читающее `Build.MANUFACTURER`/`Build.BRAND`, несёт alias-таблицу суббрендов.
- **`VendorRecipeSource`** (новый порт) — возвращает распарсенный recipe-каталог; `BundledVendorRecipeSource` — единственная реализация в этой спеке (bundled asset), следующая адаптеры (file import, share intent, marketplace) — additive позже, `TODO(shareability)`.
- **`VendorOverride`** (новый wire-format тип, часть recipe-каталога) — vendor-специфичный intent-таргет (action/package/className) + `fallbackTextKey`, для одного `(componentType, vendor)` пеара. `fallbackTextKey` доставляется до пользователя через существующий `FailReason.InternalError.messageKey`, не через новый UI-элемент.
- **`LauncherRoleProvider`** (существующий класс, расширяется) — единственный `Provider`, получающий vendor-awareness в этой спеке.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: На Xiaomi (MIUI) с загруженным recipe-покрытием тап «Настроить HOME launcher» открывает MIUI-специфичный экран (Настройки → Приложения → По умолчанию → Домашний экран) в 100% попыток на устройствах, покрытых каталогом — не generic ROLE-диалог, который на MIUI не применяется после тапа «Да».
- **SC-002 [backlog]**: На Huawei без GMS ни один вызов `LauncherRoleProvider.check()`/`apply()` не приводит к краху приложения (0 необработанных исключений в диагностических логах за прогон OEM-matrix) — пользователь либо видит честный `LifecycleState`, либо текстовую инструкцию.
- **SC-003 [backlog]**: Новый vendor-override для уже известного `Vendor`-значения добавляется правкой `vendor-recipes.json` и раздачей через `VendorRecipeSource` — без изменения Kotlin-кода и без нового APK-релиза.
- **SC-004**: Recipe wire-format (`schemaVersion=1`, первая версия формата) проходит roundtrip-тест (write → read → equals) уже в этой спеке; backward-compat тест (чтение предыдущей `schemaVersion`) добавляется начиная с момента, когда появится `schemaVersion=2` — раньше физически нечего тестировать (нет N-1 версии).
- **SC-005**: `LauncherRoleProvider` unit-тесты покрывают dispatch с vendor-override и без него (default fallback) для каждого значения `Vendor`, включая `GenericAndroid`.
- **SC-006**: `ComponentProviderCoverageTest` (существующий fitness-тест) остаётся зелёным без изменений — `LauncherRole` продолжает резолвиться в non-NoOp `Provider` независимо от `Vendor`.
- **SC-007**: Firebase Test Lab OEM-matrix job проходит (или явно инконклюзивен, не силентно зелёный) для трёх устройств при PR с меткой `oem-matrix-required`.

## Assumptions

- Целевые пользователи преимущественно используют устройства Xiaomi/Redmi, Huawei/Honor, Samsung — это определяет приоритет vendor-покрытия (P1 для Xiaomi/Huawei).
- `GmsAvailabilityPort` (TASK-49) уже доступен и достаточен для различения GMS/no-GMS веток; новый порт под это не создаётся.
- Firebase Test Lab доступен как CI-инфраструктура (billing/quota — операционный вопрос вне этой спеки; при недоступности OEM-matrix job помечается inconclusive, не блокирует остальной CI).
- Recipe-каталог грузится полностью при старте (bundled), без runtime-докачки — оптимизация выборочной загрузки вынесена за скоуп (см. Out of Scope).
- Vendor-override пишется только для системных Android-surface; override для сторонних SDK не в скоупе.
- **Почему recipe внутри `Provider`, а не через вендорский tier `HandlerKey.vendor`**: `HandlerKey.vendor` уже существует в `ProviderRegistry`, но требует скомпилированного `Provider`-класса на каждый vendor — новый override для уже известного vendor означал бы новый релиз, что прямо противоречит FR-005/SC-003 (единственной причине существования этой фичи). Recipe-данные внутри одного `LauncherRoleProvider` дают то же поведение (разный intent на разных vendor) без новых классов. Если бы `VendorRecipeSource`/`VendorDetector` (внешние на другом конце этого шва) подорожали или были упразднены — цена замены исчисляется днями (адаптер уже изолирован per CLAUDE.md rule 2), не неделями; шов оправдан. Вендорский tier `HandlerKey.vendor` остаётся доступным для будущих случаев, где нужна структурно другая реализация на vendor (не просто другой intent) — не активируется этой спекой.

## Local Test Path *(mandatory)*

- **Emulator / device**: `pixel_5_api_34` через skill `android-emulator` для generic-пути (existing `LauncherRoleProvider` default, dispatch без vendor-override) и для unit/instrumentation-тестов dispatch-логики с застабленным `Build.MANUFACTURER`.
- **Fake adapters used**: `FakeVendorDetector` (стаб `Vendor` напрямую), `FakeVendorRecipeSource` (стаб recipe-каталога) — оба в `core/src/commonTest/kotlin/com/launcher/test/fakes/`, по образцу существующего `FakeGmsAvailabilityPort.kt` (программируемый `var`); существующий `FakeGmsAvailabilityPort` (TASK-49) переиспользуется как есть.
- **Fixtures / seed data**: `core/src/commonTest/resources/fixtures/vendor-recipes-v1.json` — минимум по одной записи на Xiaomi/Huawei/Samsung для `LauncherRole`. Начиная со `schemaVersion=2` добавляется парный legacy-fixture для backward-compat теста — не создаётся сейчас, т.к. `schemaVersion=1` ещё не имеет предшественника.
- **Verification command**: `./gradlew :core:test --tests *VendorDetector*`, `./gradlew :core:test --tests *VendorRecipe*`, `./gradlew :app:testDebugUnitTest --tests *LauncherRoleProvider*`.
- **Cannot-test-locally gaps**: реальное поведение MIUI/EMUI/One UI settings screens и silent-deny сценарии **нельзя** воспроизвести на generic-эмуляторе (эмуляторные образы — AOSP, не несут OEM-скины) → `TODO(physical-device)` для ручной Xiaomi/Huawei/Samsung проверки (см. reference-память `reference_testing_environment`); Firebase Test Lab с реальными OEM-образами — ближайшая замена, но настройка GCP-проекта/биллинга — отдельный операционный шаг, не выполняемый в этой AI-сессии.

## AI Affordance *(mandatory)*

- **Exposable capabilities**: `Provider.check()`/`apply()` для `LauncherRole` уже существуют как доменные операции с TASK-120/126; эта спека не добавляет новых AI-вызываемых verbs, только делает существующие verbs корректными на большем числе устройств.
- **Required affordances on data**: нет новых — текущий `Vendor` устройства мог бы быть read-only полем для будущего diagnostic-агента («почему эта настройка не применяется»), но это не реализуется в этой спеке.
- **Provider-agnostic shape**: подтверждено — `Vendor`, `Component`, `Provider` остаются доменными типами; никаких Gemini/OpenAI/Claude или иных vendor SDK типов не вводится.
- **Out of scope for this spec**: diagnostic AI-агент, объясняющий пользователю OEM-специфичные проблемы — future work, не в этой спеке.

## OEM Matrix *(mandatory if feature touches device behavior)*

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---------------|------------------|--------------------------|----------------------|
| Stock Android (Pixel) | baseline — `RoleManager`/`Settings.ACTION_*` работают штатно | существующий default-путь `LauncherRoleProvider` (без vendor-override) | emulator `pixel_5_api_34` |
| Xiaomi MIUI | Свой экран поверх `RoleManager`; generic `ACTION_REQUEST_ROLE` intent часто не применяется после тапа «Да»; explicit intent на `com.miui.securitycenter` не резолвится без `<queries>` записи (Android 11+, FR-010) | MIUI-специфичный vendor-override intent + манифест `<queries>`; fallback на generic, если override отсутствует в каталоге | Firebase Test Lab Xiaomi Redmi image; `TODO(physical-device)` для финальной ручной проверки |
| Huawei EMUI/HarmonyOS (без GMS) | `RoleManager` может throw или дать неопределённый результат; нет Google-путей | `check()` трактует ошибку как `Outcome.NeedsApply`/`Unsupported`, не exception; `apply()` возвращает `Outcome.Failed(FailReason.InternalError(fallbackTextKey))`, если ни один intent не резолвится — существующий Settings `ApplyResult.Failed`-путь показывает текст немедленно | Firebase Test Lab EMUI image (если доступен) либо эмулятор без Google APIs image + `GmsAvailabilityPort=unavailable` стаб; `TODO(physical-device)` |
| Samsung One UI | Knox-специфичные пути (device policy) параллельно с обычным API | Минимальное recipe-покрытие (`LauncherRole` only); полный Knox-путь — не обязателен, generic fallback приемлем для MVP | Firebase Test Lab Samsung Galaxy S24 image |
| Oppo/Vivo/OnePlus/Honor | Свои autostart/battery-optimization экраны | Явно вне минимального scope этой спеки (см. Out of Scope); новые значения `Vendor` для них не добавляются сейчас — additive позже | не проверяется в этой спеке |

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Первая версия этой спеки (2026-07-18, до полудня) была написана на терминологии TASK-65 (`CheckSpec`/`ApplySpec`/`Pool`/`ConfigSource`), которую полностью заменил канонический ECS (TASK-136, тот же день). Эта версия переписана против реального кода: единственный OEM-чувствительный `Provider` сегодня — `LauncherRoleProvider` (`RoleManager`-обёртка, vendor-blind). Спека делает его vendor-aware через новый recipe-файл `vendor-recipes.json`, не трогая ни `Component`, ни существующий `Provider`-порт.

**Конкретика, которую стоит запомнить:**
- **`enum class Vendor { Xiaomi, Samsung, Huawei, GoogleTV, GenericAndroid, iOS }` уже существует в коде** (`Enums.kt:62`) — не изобретаем новый тип.
- **`ProviderRegistry` уже умеет 3-tier vendor fallback** (`HandlerKey(type, platform, vendor)`), но это НЕ используется этой спекой — тот механизм требует компилируемого класса на vendor (= APK-релиз на каждый override), что противоречит требованию «без пересборки». Вместо этого — recipe-данные внутри `LauncherRoleProvider`.
- Новые порты: `VendorDetector` (`Build.MANUFACTURER` → `Vendor`, alias-таблица Redmi/POCO→Xiaomi) и `VendorRecipeSource` (по образцу уже существующих `PoolSource`/`PresetSource`/`HintPoolSource` — «один порт, один Bundled-адаптер»).
- Recipe wire-format ключуется по `componentType` (тот же `@SerialName`, что у `Component` — например `"LauncherRole"`), не по выдуманному `PoolEntryId`.
- Минимальное покрытие v1 — **только `LauncherRole`**, 3 vendor-override (Xiaomi/Huawei/Samsung). `POST_NOTIFICATIONS` выпал из скоупа — permissions вообще не `Component` в текущей ECS.
- `Outcome` (`Ok`/`NeedsApply`/`Failed`/`Unsupported`) — существующий тип, используется как есть, никакого нового `NotApplied`/`Indeterminate`.
- **Fallback-текст НЕ показывается диалогом из Provider'а** — `apply()` возвращает `Outcome.Failed(FailReason.InternalError(fallbackTextKey))`, и уже существующий Settings `ApplyResult.Failed`-путь (TASK-69, `EngineSettingsGateway`) показывает текст пользователю немедленно по тапу. Никакого нового UI-механизма не строим — ранняя версия этого раздела спеки ошибочно придумывала `AlertDialog` внутри `Provider`, что нарушило бы domain/UI-изоляцию.

**На что смотреть с осторожностью:**
- Если читаешь старую версию этой спеки в git-истории (до grounding-коррекции) — она **не соответствует коду**, не бери из неё типы.
- FR-010 — explicit intent на OEM-пакеты (`com.miui.securitycenter`) не резолвится без `<queries>`-записи в манифесте (Android 11+) — легко забыть, выглядит как «vendor override не сработал».
- Alias-таблица суббрендов (Redmi/POCO → Xiaomi) — без неё самые массовые устройства целевой аудитории молча получают `GenericAndroid`.
- `StatusBarPolicy` — существующий `Component`/`Provider`, но ни один bundled-пресет на него не ссылается (TASK-69 finding) — добавлять ему vendor recipe раньше, чем он станет достижим, бессмысленно; поэтому он вне скоупа.

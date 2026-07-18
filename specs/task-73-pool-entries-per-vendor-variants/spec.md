# Feature Specification: Pool entries per-vendor variants — CheckSpec/ApplySpec dispatch

**Feature Branch**: `task-73-pool-vendor-variants`
**Created**: 2026-07-18
**Status**: Draft
**Input**: TASK-73 — расширить CheckSpec/ApplySpec vendor-aware dispatch'ом, чтобы один и тот же pool entry (`android.role.home` и т.д.) корректно проверялся и применялся на Xiaomi MIUI, Huawei без GMS, Samsung One UI и других OEM, а не только на чистом Pixel.

## Контекст (что уже построено)

TASK-65 (Profile Composition Foundation v2, Done) ввёл `Pool` — каталог настроек с парой `CheckSpec` («как проверить включено ли») / `ApplySpec` («как включить») на запись. Сегодня обе спецификации **vendor-blind**: единственная реализация каждого CheckSpec/ApplySpec написана под чистый Android API (`RoleManager`, стандартные `Settings.ACTION_*` intents) и предполагает поведение, характерное для Pixel. TASK-49 (Cloud Feature Inventory, Done) уже дал `GmsAvailabilityPort` — используется здесь для различения "есть GMS" / "нет GMS" веток без завязки на конкретный vendor.

Эта спека не меняет *что* можно настраивать (набор pool entries), только *как* check/apply находят правильный путь на конкретном устройстве.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Xiaomi MIUI: настройка HOME launcher открывает правильный экран (Priority: P1)

Пользователь на Xiaomi (MIUI) в мастере/настройках нажимает «Сделать launcher по умолчанию». Вместо стандартного Android-диалога выбора роли (который на MIUI часто не применяется после тапа «Да») открывается путь, специфичный для MIUI: Настройки → Приложения → Приложения по умолчанию → Домашний экран.

**Why this priority**: без этого MIUI-пользователь (значимая доля целевой аудитории) застревает на шаге, который выглядит пройденным, но не сработал — silent failure, худший вид UX-бага.

**Independent Test**: на Xiaomi-эмуляторе (или Firebase Test Lab Xiaomi Redmi image) вызвать `ApplySpec` для `android.role.home` с загруженным MIUI-рецептом и проверить, что запущенный intent ведёт на MIUI-специфичный экран, а не на generic `RoleManager` dialog.

**Acceptance Scenarios**:

1. **Given** устройство определено как `VendorProfile.Xiaomi` и загружен recipe с MIUI-override для `android.role.home`, **When** пользователь запускает Apply, **Then** открывается MIUI-специфичный intent (не generic `ACTION_REQUEST_ROLE`).
2. **Given** MIUI-override отсутствует в текущем recipe-каталоге (recipe ещё не покрывает этот entry), **When** пользователь запускает Apply, **Then** система падает обратно на generic Android API (без краша).

---

### User Story 2 — Huawei без GMS: честный статус вместо краша (Priority: P1)

На устройстве без Google Play Services (Huawei EMUI/HarmonyOS без GMS) вызов `RoleManager`-based CheckSpec сегодня может бросить исключение или дать неопределённый результат. Вместо этого пользователь должен увидеть понятный статус «не настроено» и, если системный путь недоступен, — структурированную текстовую инструкцию, куда идти руками.

**Why this priority**: EMUI-без-GMS — устройства, где меньше всего системных гарантий; молчаливый краш здесь означает, что пользователь вообще не понимает, что делать.

**Independent Test**: на Huawei-без-GMS образе (Firebase Test Lab EMUI image, `GmsAvailabilityPort` возвращает `unavailable`) вызвать CheckSpec для `android.role.home` — убедиться, что результат `NotApplied`/`Indeterminate`, не исключение; вызвать ApplySpec — убедиться, что при `resolveActivity == null` показывается локализованный `AlertDialog` с текстовой инструкцией.

**Acceptance Scenarios**:

1. **Given** `GmsAvailabilityPort` = unavailable и manufacturer = Huawei, **When** CheckHandler проверяет `android.role.home`, **Then** результат — типизированный `NotApplied`/`Indeterminate`, не exception.
2. **Given** ни vendor-override, ни generic intent не резолвятся, **When** пользователь запускает Apply, **Then** показывается `AlertDialog` с локализованной инструкцией «Откройте Настройки → …», а не тихий no-op.

---

### User Story 3 — Автор recipe добавляет новый vendor без пересборки APK (Priority: P2)

Инфраструктурный автор (не обязательно программист приложения) добавляет override для нового vendor (например, Honor) в `vendor-recipes.json` и раздаёт его через существующий `ConfigSource`. Приложению не нужен новый релиз, чтобы получить это покрытие.

**Why this priority**: OEM-матрица растёт быстрее релизного цикла приложения; без этого каждый новый vendor-баг требует полного релиза.

**Independent Test**: положить обновлённый `vendor-recipes.json` с новым vendor override через `BundledConfigSource`/тестовый `ConfigSource`, перезапустить чтение — новый override дispatch'ится без изменений в Kotlin-коде.

**Acceptance Scenarios**:

1. **Given** новый `vendor-recipes.json` с override для `VendorProfile.Honor`, **When** приложение перечитывает recipe catalogue, **Then** `CheckHandler`/`ApplyHandler` используют новый override для соответствующего pool entry без изменения кода.
2. **Given** recipe ссылается на `poolEntryId`, которого не существует в текущем Pool, **When** каталог загружается, **Then** чтение не падает — неизвестная запись игнорируется, ошибка логируется (см. Edge Cases, симметрично TASK-131 lenient reader).

---

### User Story 4 — CI ловит OEM-регрессию до мержа (Priority: P3)

При изменении pool entry, помеченного как OEM-чувствительный (`android.role.home`, `android.permission.POST_NOTIFICATIONS`), PR с меткой `oem-matrix-required` прогоняет инструментальные тесты минимум на трёх устройствах (Pixel, Samsung Galaxy S24, Xiaomi Redmi) через Firebase Test Lab, прежде чем изменение попадёт в `main`.

**Why this priority**: без автоматической проверки OEM-регрессии обнаруживаются только пользователями в проде — ценно, но не блокирует MVP этой фичи (вручную прогоняемо на первом этапе).

**Independent Test**: изменить check/apply для `android.role.home`, открыть PR с меткой `oem-matrix-required`, убедиться, что Firebase Test Lab job запускается и репортит статус по каждому из трёх устройств.

**Acceptance Scenarios**:

1. **Given** PR с меткой `oem-matrix-required` меняет pool entry, **When** CI запускается, **Then** инструментальный тест проходит на Pixel 8 + Samsung Galaxy S24 + Xiaomi Redmi отдельно, и лог показывает per-device результат.
2. **Given** PR без метки `oem-matrix-required`, **When** CI запускается, **Then** OEM-matrix job не запускается (не блокирует обычные PR).

### Edge Cases

- Устройство от производителя, не входящего в известный `VendorProfile` enum (например, редкий китайский бренд) → `VendorProfile.Generic`, используется default (не-vendor-specific) CheckSpec/ApplySpec — тот же путь, что работал до этой фичи.
- `vendor-recipes.json` отсутствует, повреждён или не проходит schema-валидацию при старте → приложение продолжает работать на bundled default-поведении (generic CheckSpec/ApplySpec), ошибка парсинга логируется, не крашит запуск (симметрично TASK-131 lenient reader — тот же принцип для recipe wire format).
- `GmsAvailabilityPort` сам бросает исключение при проверке → трактуется как `unavailable` (fail-safe в сторону "нет GMS", не в сторону краша).
- Recipe ссылается на `VendorProfile`, который приложение ещё не знает (recipe написан для более новой версии приложения) → неизвестный vendor-ключ игнорируется при парсинге, dispatch падает на default.
- Пользователь отменяет системный диалог/intent на середине Apply-потока (Xiaomi/Huawei fallback экран) → состояние pool entry остаётся прежним (`NotApplied`), повторный запуск возможен, ничего не ломается — тот же инвариант, что уже проверен для generic Android-пути в TASK-69 SEQ-3.
- Firebase Test Lab недоступен/квота исчерпана в момент PR → OEM-matrix job помечается как inconclusive (не blocking silently зелёным), PR не мержится до ручной проверки — см. Local Test Path.

## Out of Scope

- Runtime-докачка только нужных vendor-recipe записей (сейчас каталог грузится целиком как bundled asset; выборочная докачка — отдельная задача оптимизации).
- Vendor-override для сторонних (не системных Android) SDK — только для системных surface (roles, permissions, settings screens).
- Полное покрытие Oppo/Vivo/OnePlus/Honor (autostart manager, battery optimization экраны) — архитектура (`perVendor` map) готова принять эти overrides позже, но recipe-записи для них не создаются в этой спеке; см. OEM Matrix.
- Samsung Knox-специфичные device-policy пути — generic fallback приемлем для MVP, отдельный Knox-путь не реализуется.
- Procurement физических устройств для тестирования — решение владельца, не техническая задача этой спеки.
- Изменение таймингов/UX первого запроса permission (POST_NOTIFICATIONS rationale-экран и т.п.) — эта спека меняет только dispatch check/apply, не когда и как permission запрашивается впервые (уже определено в TASK-65/wizard-спеках).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Domain MUST определять `VendorProfile` как sealed value (`Pixel`, `Xiaomi`, `Huawei`, `Samsung`, `Oppo`, `Vivo`, `OnePlus`, `Honor`, `Generic`) без вендорских SDK-типов в сигнатуре.
- **FR-002**: Приложение MUST определять `VendorProfile` устройства через порт `VendorProfileProvider` (Android-адаптер читает `Build.MANUFACTURER`/`Build.BRAND` + `GmsAvailabilityPort`), домен зависит только от порта.
- **FR-003**: `CheckSpec` и `ApplySpec` sealed-иерархии MUST поддерживать необязательный `perVendor: Map<VendorProfile, Variant>` без изменения существующих non-vendor-aware записей (обратная совместимость с TASK-65 pool entries).
- **FR-004**: `CheckHandler` MUST резолвить vendor-override для текущего `VendorProfile`, если он есть в загруженном recipe catalogue, иначе использовать default CheckSpec той же записи.
- **FR-005**: `CheckHandler` MUST возвращать типизированный результат (`Applied`/`NotApplied`/`Indeterminate`), а не бросать исключение, при отсутствии vendor-специфичного пути или ошибке платформенного вызова.
- **FR-006**: `ApplyHandler` MUST пробовать fallback-цепочку в порядке: vendor-специфичный intent → generic Android API intent → structured localized instruction (`AlertDialog`), если ни один intent не резолвится (`resolveActivity == null`). Диалог закрывается явным подтверждением пользователя («Понятно»); закрытие не меняет статус pool entry — он остаётся `NotApplied`, повторный тап на настройку в любой момент запускает Apply заново (без авто-retry).
- **FR-007**: Recipe catalogue MUST загружаться как отдельный wire-format файл (`vendor-recipes.json`) через существующий `ConfigSource` порт (новый `ConfigKind.VendorRecipes`, тот же паттерн `BundledSource`/`TODO(shareability)`, что уже установлен для других `ConfigKind` — новый adapter не изобретается), не как часть APK-кода.
- **FR-008**: Recipe wire-format MUST нести явное поле `schemaVersion`, считываемое первым при десериализации (до разбора остального содержимого), и структуру `Map<PoolEntryId, Map<VendorId, VendorOverride>>` (per CLAUDE.md rule 5). Текущая версия — `schemaVersion=1` (первый коммит формата); чтение версий ≤ текущей MUST оставаться возможным минимум один major release вперёд, начиная с момента, когда появится `schemaVersion=2`.
- **FR-009**: Recipe reader MUST игнорировать неизвестные `poolEntryId` и неизвестные vendor-ключи без падения чтения целиком (симметрично TASK-131).
- **FR-010**: Минимальное recipe-покрытие для существующих TASK-65 pool entries MUST включать `android.role.home` (минимум 3 vendor-override: Xiaomi, Huawei, Samsung) и `android.permission.POST_NOTIFICATIONS` (минимум 3 vendor-override); `ui.font.large` явно остаётся без vendor-override (стабильный Android API, single default).
- **FR-011**: CI MUST предоставлять Firebase Test Lab job, запускаемый по PR-метке `oem-matrix-required`, прогоняющий инструментальные тесты минимум на трёх устройствах (Pixel 8, Samsung Galaxy S24, Xiaomi Redmi).
- **FR-012**: Манифест MUST декларировать `<queries>` записи (package visibility, Android 11+) для каждого OEM-специфичного пакета, на который ссылается vendor-override intent (например, `com.miui.securitycenter` для Xiaomi) — без этого `resolveActivity()` не находит компонент даже при его фактическом наличии, и fallback срабатывает ошибочно.
- **FR-013**: Изменение MUST быть отражено в [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — новые `<queries>`-записи из FR-012 добавляются в реестр permissions/queries приложения.
- **FR-014**: Каждый неуспешный dispatch (CheckSpec → `Indeterminate`, ApplyHandler дошёл до fallback-диалога) MUST логироваться структурированным событием с категорией (`vendor`, `poolEntryId`, `outcome`), без PII, чтобы разработчик мог диагностировать OEM-регрессию по логам без физического устройства.

### Key Entities

- **VendorProfile**: доменное sealed-значение, идентифицирует семейство устройства для целей dispatch (не хранит вендорские SDK-типы).
- **VendorProfileProvider**: порт, возвращающий текущий `VendorProfile` устройства; Android-адаптер — единственное место, читающее `Build.MANUFACTURER`.
- **CheckSpec / ApplySpec (variant-aware)**: существующие sealed-иерархии из TASK-65, расширенные необязательной картой `perVendor`.
- **VendorRecipe**: wire-format запись — `poolEntryId → { vendorId → { check-override?, apply-override?, fallbackTextKey } }`, с `schemaVersion`.
- **PoolEntryId**: существующий идентификатор pool entry (TASK-65), используется как ключ recipe-каталога — сохраняет переносимость preset'ов между устройствами.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: На Xiaomi (MIUI) с загруженным recipe-покрытием тап «Настроить HOME launcher» открывает MIUI-специфичный экран (Настройки → Приложения → По умолчанию → Домашний экран) в 100% попыток на устройствах, покрытых каталогом — не generic ROLE-диалог, который на MIUI не применяется после тапа «Да».
- **SC-002 [backlog]**: На Huawei без GMS ни один вызов CheckSpec/ApplySpec для покрытых recipe-каталогом pool entries не приводит к краху приложения (0 необработанных исключений в диагностических логах за прогон OEM-matrix) — пользователь либо видит статус `NotApplied`/`Indeterminate`, либо текстовую инструкцию.
- **SC-003 [backlog]**: Новый vendor-override для существующего pool entry добавляется правкой `vendor-recipes.json` и раздачей через `ConfigSource` — без изменения Kotlin-кода и без нового APK-релиза.
- **SC-004**: Recipe wire-format (`schemaVersion=1`, первая версия формата) проходит roundtrip-тест (write → read → equals) уже в этой спеке; backward-compat тест (чтение предыдущей `schemaVersion`) добавляется начиная с момента, когда появится `schemaVersion=2` — раньше физически нечего тестировать (нет N-1 версии).
- **SC-005**: `CheckHandler`/`ApplyHandler` unit-тесты покрывают dispatch с vendor-override и без него (default fallback) для каждого из 9 значений `VendorProfile`, включая `Generic`.
- **SC-006**: Firebase Test Lab OEM-matrix job проходит (или явно инконклюзивен, не силентно зелёный) для трёх устройств при PR с меткой `oem-matrix-required`.

## Assumptions

- Целевые пользователи преимущественно используют устройства Xiaomi/Redmi, Huawei/Honor, Samsung — это определяет приоритет vendor-покрытия (P1 для Xiaomi/Huawei, остальные — по мере recipe-пополнения, без кода).
- `GmsAvailabilityPort` (TASK-49) уже доступен и достаточен для различения GMS/no-GMS веток; новый порт под это не создаётся.
- Firebase Test Lab доступен как CI-инфраструктура (billing/quota — операционный вопрос вне этой спеки; при недоступности OEM-matrix job помечается inconclusive, не блокирует остальной CI).
- Recipe-каталог грузится полностью при старте (bundled), без runtime-докачки — оптимизация выборочной загрузки вынесена за скоуп (см. Out of Scope).
- Vendor-override пишется только для системных Android-surface (roles, permissions, settings screens); override для сторонних SDK не в скоупе.
- **Почему `perVendor`-карта, а не `if (vendor == X)` в коде каждого CheckHandler/ApplyHandler**: если инлайнить — каждый новый vendor-override требует правки Kotlin-кода и нового релиза (прямое противоречие FR-007/SC-003, единственной причине существования этой фичи). Если бы `GmsAvailabilityPort`/`ConfigSource` (внешние на другом конце этого шва) подорожали или были упразднены — цена замены исчисляется днями (адаптер уже изолирован per CLAUDE.md rule 2), не неделями; шов оправдан.

## Local Test Path *(mandatory)*

- **Emulator / device**: `pixel_5_api_34` через skill `android-emulator` для generic-пути (default CheckSpec/ApplySpec, dispatch без vendor-override) и для unit/instrumentation-тестов dispatch-логики с застабленным `Build.MANUFACTURER`.
- **Fake adapters used**: `FakeVendorProfileProvider` (стаб `Build.MANUFACTURER`/`GmsAvailabilityPort` на произвольный `VendorProfile`), `FakeConfigSource` для recipe-каталога, существующий `FakeGmsAvailabilityPort` (TASK-49).
- **Fixtures / seed data**: `core/src/test/resources/fixtures/vendor-recipes-v1.json` — минимум по одной записи на Xiaomi/Huawei/Samsung для `android.role.home`. Начиная со `schemaVersion=2` (следующая эволюция формата) добавляется парный fixture `vendor-recipes-v1-legacy.json` для backward-compat теста — не создаётся сейчас, т.к. `schemaVersion=1` ещё не имеет предшественника.
- **Verification command**: `./gradlew :core:test --tests *VendorDispatch*`, `./gradlew :core:test --tests *VendorRecipe*Roundtrip*`.
- **Cannot-test-locally gaps**: реальное поведение MIUI/EMUI/One UI settings screens и silent-deny сценарии **нельзя** воспроизвести на generic-эмуляторе (эмуляторные образы — AOSP, не несут OEM-скины) → `TODO(physical-device)` для ручной Xiaomi/Huawei/Samsung проверки (см. reference-память `reference_testing_environment`); Firebase Test Lab с реальными OEM-образами — ближайшая замена, но настройка GCP-проекта/биллинга — отдельный операционный шаг, не выполняемый в этой AI-сессии.

## AI Affordance *(mandatory)*

- **Exposable capabilities**: `checkPoolEntry(poolEntryId)`, `applyPoolEntry(poolEntryId)` — уже существуют как доменные операции с TASK-65; эта спека не добавляет новых AI-вызываемых verbs, только делает существующие verbs корректными на большем числе устройств.
- **Required affordances on data**: нет новых — `VendorProfile` текущего устройства мог бы быть read-only полем для будущего diagnostic-агента («почему эта настройка не применяется»), но это не реализуется в этой спеке.
- **Provider-agnostic shape**: подтверждено — `VendorProfile`, `CheckSpec`, `ApplySpec` остаются доменными sealed-значениями; никаких Gemini/OpenAI/Claude или иных vendor SDK типов не вводится.
- **Out of scope for this spec**: diagnostic AI-агент, объясняющий пользователю OEM-специфичные проблемы — future work, не в этой спеке.

## OEM Matrix *(mandatory if feature touches device behavior)*

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---------------|------------------|--------------------------|----------------------|
| Stock Android (Pixel) | baseline — `RoleManager`/`Settings.ACTION_*` работают штатно | default CheckSpec/ApplySpec (без vendor-override) | emulator `pixel_5_api_34` |
| Xiaomi MIUI | Свой экран поверх `RoleManager`; generic `ACTION_REQUEST_ROLE` intent часто не применяется после тапа «Да»; explicit intent на `com.miui.securitycenter` не резолвится без `<queries>` записи (Android 11+, FR-012) | MIUI-специфичный vendor-override intent на `android.role.home`/`android.permission.POST_NOTIFICATIONS` + манифест `<queries>`; fallback на generic, если override отсутствует в каталоге | Firebase Test Lab Xiaomi Redmi image; `TODO(physical-device)` для финальной ручной проверки |
| Huawei EMUI/HarmonyOS (без GMS) | `RoleManager` может throw или дать неопределённый результат; нет Google-путей | `CheckHandler` трактует ошибку как `Indeterminate`, не exception; `ApplyHandler` падает на localized `AlertDialog`-инструкцию, если ни один intent не резолвится | Firebase Test Lab EMUI image (если доступен) либо эмулятор без Google APIs image + `GmsAvailabilityPort=unavailable` стаб; `TODO(physical-device)` |
| Samsung One UI | Knox-специфичные пути (device policy) параллельно с обычным API | В скоуп этой спеки входит минимальное recipe-покрытие (`android.role.home`, `POST_NOTIFICATIONS`); полный Knox-путь — не обязателен, generic fallback приемлем для MVP | Firebase Test Lab Samsung Galaxy S24 image |
| Oppo/Vivo/OnePlus/Honor | Свои autostart/battery-optimization экраны | Явно вне минимального scope этой спеки (см. Out of Scope); архитектура (perVendor map) готова принять эти overrides позже без кода, только через `vendor-recipes.json` | не проверяется в этой спеке — recipe catalogue дополняется отдельно |

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** CheckSpec/ApplySpec (пара «проверить/включить» настройку из TASK-65 Pool) сегодня работает по одному пути, рассчитанному на чистый Pixel. Эта спека добавляет vendor-специфичные варианты (Xiaomi, Huawei, Samsung, Oppo/Vivo/OnePlus/Honor, Generic) поверх того же pool entry — один и тот же `android.role.home` теперь может по-разному проверяться/включаться в зависимости от `VendorProfile` устройства, а данные для этого грузятся отдельным файлом `vendor-recipes.json`, а не зашиваются в APK.

**Конкретика, которую стоит запомнить:**
- Новый домен-тип `VendorProfile` (sealed value, 9 значений: Pixel/Xiaomi/Huawei/Samsung/Oppo/Vivo/OnePlus/Honor/Generic) + порт `VendorProfileProvider`.
- `CheckSpec`/`ApplySpec` получают необязательную карту `perVendor: Map<VendorProfile, Variant>` — старые записи без неё продолжают работать как раньше.
- Fallback-цепочка ApplyHandler жёстко зафиксирована в 3 шага: vendor-intent → generic Android intent → localized `AlertDialog`-инструкция.
- Recipe wire-format — `schemaVersion=1` (первый коммит формата, backward-compat тест появится только когда родится `schemaVersion=2`, раньше тестировать нечего).
- Минимальное покрытие v1: `android.role.home` и `android.permission.POST_NOTIFICATIONS` — по 3 vendor-override каждый (Xiaomi/Huawei/Samsung); Oppo/Vivo/OnePlus/Honor — архитектурно готовы, но recipe-записей для них нет (Out of Scope).
- CI: Firebase Test Lab job на 3 устройства (Pixel 8, Samsung Galaxy S24, Xiaomi Redmi) по PR-метке `oem-matrix-required`, не блокирует обычные PR.

**На что смотреть с осторожностью:**
- FR-012 — explicit intent на OEM-пакеты (например `com.miui.securitycenter`) не резолвится без `<queries>`-записи в манифесте (Android 11+ package visibility) — легко забыть и получить silent-fail, который выглядит как «vendor override не сработал», хотя на самом деле не задекларирован `<queries>`.
- Реальное поведение MIUI/EMUI/One UI нельзя проверить на обычном AOSP-эмуляторе — только Firebase Test Lab (нужен GCP-биллинг, не настраивается в этой AI-сессии) или `TODO(physical-device)` — при чтении plan.md/tasks.md не путать «протестировано» с «протестировано только dispatch-логика на generic эмуляторе».
- `schemaVersion=1` — если следующая спека (или follow-up этой) добавит `schemaVersion=2`, обязателен backward-compat fixture `vendor-recipes-v1-legacy.json`, которого пока намеренно нет.

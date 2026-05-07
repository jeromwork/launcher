# Spec 005: Action Architecture v2

**Status**: Active | **Date**: 2026-05-07 | **Author**: project owner
**Branch**: `005-action-architecture-v2`
**Depends on**: 003 (доменные модели Flow/Slot/Action), 004 (CMP-стек, KMP `:core`)

---

## Clarifications

### 2026-05-07 — Pre-plan clarification pass (speckit-clarify, applied with suggested defaults; subject to user override)

| # | Grey zone | Resolution |
|---|-----------|------------|
| C1 | **UnknownProvider semantics**: §7.1 step 2 returns `Failure("unknown provider")`, but §5.1 keeps `ProviderId` as `value class<String>` precisely so future versions can introduce new providers. Tension: should reading a config with `providerId: "future-thing"` crash, or be silently ignored? | **Resolution:** distinguish *parse-time* from *dispatch-time*. Parse never fails on unknown `providerId` (forward-compat preserved). Dispatch on an unknown provider returns `DispatchResult.ProviderUnavailable(providerId, hint = UnavailabilityHint.UnknownInThisVersion)`. Wizards (§4.1.3) hide unknown providers from the picker. **Why:** lets newer-version configs round-trip through older clients without data loss; user-visible failure mode is identical to "app not installed". |
| C2 | **`Custom.params` value types**: §4.1.1 declares `Custom(key: String, params: Map<String, String>)` — only String values. Will future custom providers need numeric/boolean payload? | **Resolution:** `Map<String, String>` only. Numeric/boolean values get serialised by the producer (`"true"`, `"42"`). **Why:** wire-format simplicity; one less type to migrate later. **Exit ramp:** if a real future provider needs typed values, switch to `Map<String, JsonElement>` from kotlinx-serialization — that change is purely additive at the type level, only `Custom`-emitting code updates. |
| C3 | **`AndroidProviderRegistry.updates: Flow<List<ProviderState>>` cadence**: not specified — emits on every `AppIndex.snapshot` change? Throttled? | **Resolution:** emits at most once per 1 second (debounce), and only when set of *available* providers changes (not on every package install for unrelated apps). Implemented via `AppIndex.snapshot.map { it.toProviderStates() }.distinctUntilChanged().debounce(1000)`. **Why:** Article IX §3 (event-driven, not polling) + §III.3 (battery discipline). |
| C4 | **Test fixture location**: §4.1.5 specifies roundtrip + backward-compat tests but doesn't say where fixtures live. | **Resolution:** `core/src/commonTest/resources/fixtures/action-wire-format/`. One file per scenario: `whatsapp-message-v1.json`, `legacy-spec003-whatsapp-call.json`, etc. Fixtures committed to repo, not generated. **Why:** drift detection — if a fixture is intentionally updated, code review sees the diff. |
| C5 | **`migrateLegacyAction` removal guarantee**: §6.3 + §9 say this bridge "exists only until spec 006, after which **mandatorily** removed". What enforces that? | **Resolution:** add fitness function (CI grep) that fails the build if `migrateLegacyAction` symbol exists in `core/src/commonMain/` after spec 006 lands — wired up *now* as a deferred check that activates when `MIGRATE_LEGACY_ACTION_DEADLINE_SPEC` constant in `build.gradle.kts` matches the merged spec id. Spec 006 must remove the constant + the function in the same PR. **Why:** mechanical guarantee instead of human memory; matches §8 fitness-function philosophy. **Exit ramp:** if spec 006 slips, intentionally bumping the constant to "007" requires explicit PR comment justifying the slip. |

### Per-resolution impact on later sections

- **C1** → §4.1.1 `DispatchResult` adds `ProviderUnavailable(providerId, hint)` with `UnavailabilityHint` enum: `Missing`, `NotApplicable`, `UnknownInThisVersion`. §7.1 step 2 updated.
- **C2** → §5.2 wire-format example for `Custom` annotated.
- **C3** → §9 NFR row "dispatch latency ≤ 50ms" remains; new row "ProviderRegistry update emit rate ≤ 1/sec".
- **C4** → §4.1.5 amended with fixture path.
- **C5** → §8 fitness-functions list grows from 3 to 4.

---

## 0. Зачем этот spec вообще существует *(объяснение для новичка)*

Когда пользователь жмёт на «плитку» (tile) в нашем лаунчере, должно произойти **действие**: открыть WhatsApp на конкретный контакт, набрать номер, открыть YouTube, запустить любое приложение. Сейчас в коде это сделано «впопыхах»:

- Часть действий (WhatsApp) описана отдельным sealed-вариантом `ActionRequest.WhatsAppHandoff`, оставшимся от давно отдельного spec 002.
- Запуск приложения — отдельный вариант `ActionRequest.OpenApplication`.
- «Открыть телефон/SMS/YouTube/браузер» — **никак не сделано**, в моках это просто placeholder.
- Логика запуска WhatsApp (`ActionDispatcher.dispatchWhatsAppHandoff` — [`ActionDispatcher.kt:98-182`](core/src/androidMain/kotlin/com/launcher/core/actions/ActionDispatcher.kt#L98-L182)) знает про SharedPreferences, валидаторы, cycle-guards — это смешение: специфика одного провайдера протекла в общий слой.

Этот spec вводит **общую модель действия** — единый контракт, по которому домен говорит «я хочу выполнить действие у провайдера `X` с такими-то параметрами», а адаптер на Android (или потом на iOS) превращает это в реальный системный вызов. Никакая «WhatsApp» не должна быть видна в имени класса домена.

**Почему это критично для будущего:** контракт `ActionRequest` — это кросс-слойный и кросс-конфигурационный контракт. Его форма уйдёт в JSON-ассеты Flow/Slot, потом — в синхронизацию с бэкендом (spec 007), потом — в публичный QR/deep-link для шаринга шаблонов (spec 010). Если форма ошибочна сегодня, исправление потребует миграции **на всех устройствах пользователей**. Это **one-way door** (см. [CLAUDE.md правило 3](../../CLAUDE.md)).

---

## 1. Overview

Заменить ad-hoc структуру действий на единую модель `Action` с явным `provider`, `payload`, `fallback` и **версионированной wire-формой**. Удалить остатки spec 002 (whatsapp-specific код), описанные в [решении #6 (session-2026-05-05)](../../docs/product/session-2026-05-05-decisions.md). Реализовать первый набор провайдеров: `app`, `whatsapp`, `telegram`, `phone`, `sms`, `browser`, `youtube`. Фактический dispatch через Android `Intent` остаётся в `androidMain`; домен в `commonMain` про `Intent` ничего не знает.

**Критическое условие:** этот spec — фундамент для specs 006–010. Любая ошибка формы контракта здесь — миграция позднее.

---

## 2. Problem Statement

### 2.1 Что не так сейчас

| Проблема | Где видно | Последствие, если оставить |
|----------|-----------|----------------------------|
| WhatsApp-специфика в общих типах | `ActionRequest.WhatsAppHandoff` ([`ActionModels.kt:17-19`](core/src/commonMain/kotlin/com/launcher/api/ActionModels.kt#L17-L19)), `DispatchResult.WhatsApp` ([`ActionModels.kt:37-39`](core/src/commonMain/kotlin/com/launcher/api/ActionModels.kt#L37-L39)) | Каждый новый провайдер потребует своего sealed-варианта; вместо 7 чистых записей в реестре — 7 sealed-классов в домене. |
| `ActionDispatcher` знает про конкретные провайдеры | [`ActionDispatcher.kt:98-182`](core/src/androidMain/kotlin/com/launcher/core/actions/ActionDispatcher.kt#L98-L182) | Добавить YouTube → редактировать центральный класс. Нарушение open-closed; невозможен «провайдер из плагина». |
| Нет реальных провайдеров кроме WhatsApp и OpenApp | в `MockFlowRepository` mock-данные есть только для них | Невозможно пройти US-301..US-307 без mock-handoff'ов; product-acceptance testing нерелевантен. |
| Нет общей wire-формы | действия в JSON Flow-ассетах ([`flows_mock_workspace.json`](core/src/androidMain/assets/flows_mock_workspace.json)) описаны разрозненно (`type: "whatsapp_call"`, `type: "open_app"`) | Backend (spec 007) и QR-share (spec 010) повторят эту разрозненность; миграция позже = ломка всех конфигов. |
| Старый whatsapp-handoff-протокол всё ещё в коде | `CommunicationModels.kt`, `WhatsAppLaunchabilityResolver.kt`, `ReturnContextStore.kt`, `ActionCycleGuard.kt`, `RestoreOutcomeEvaluator.kt`, `whatsapp_tiles_mock.json` — все живы, см. отчёт исследования | Чем дольше остаются — тем труднее удалить (растущее количество ссылок). |

### 2.2 Что мы решили в session 2026-05-05

[Решение #6](../../docs/product/session-2026-05-05-decisions.md) (явный список файлов под удаление) принято заранее, чтобы scope этого spec'а был чёткий. Список приведён в §6.4 ниже.

---

## 3. User Stories

| ID | Story | Acceptance Criteria |
|----|-------|---------------------|
| **US-501** | Пользователь жмёт на «плитку WhatsApp-контакта» и WhatsApp открывается на чате с этим контактом. | На устройстве с установленным WhatsApp: тап → открыт чат с контактом. На устройстве без WhatsApp: открывается Play Store на странице WhatsApp (`fallback`). Никаких ошибок-крэшей. |
| **US-502** | Пользователь жмёт на «плитку звонка» и открывается набор номера. | `tel:+...` intent. Если у пользователя нет телефонного приложения (планшет) — `fallback` или явное «недоступно». |
| **US-503** | Пользователь жмёт на «плитку SMS» и открывается окно SMS на этого контакта. | `smsto:+...` intent на default-SMS-приложение. |
| **US-504** | Пользователь жмёт на «плитку YouTube» и открывается главный экран приложения (или конкретный канал/видео из payload). | `vnd.youtube://...` или `https://youtube.com/...` если приложение не установлено. |
| **US-505** | Пользователь жмёт на «плитку браузера» с url'ом — открывается браузер на этом url'е. | `https://...` через ACTION_VIEW. Учитывает default-browser пользователя. |
| **US-506** | Пользователь жмёт на «плитку любого приложения». | Запуск через `getLaunchIntentForPackage`. Если приложение удалено — `fallback` (например, открыть Play Store) или явное сообщение. |
| **US-507** | Wizard добавления слота показывает только тех провайдеров, у которых в `ProviderRegistry` есть «доступная реализация на этом устройстве». | На устройстве без SIM (планшет) `phone`/`sms` либо скрыты, либо помечены как недоступные. На устройстве без WhatsApp `whatsapp` либо скрыт, либо предлагает установку. |
| **US-508** | Действие, которое не удалось выполнить, не должно ронять приложение или зависать. | При любой ошибке dispatch — возвращается `DispatchResult.Failure(...)` с понятной причиной; приложение остаётся отзывчивым; на экране Home — toast/snackbar (детали в §7). |

---

## 4. Scope

### 4.1 In Scope

#### 4.1.1 Domain (commonMain) — новый контракт `Action`

- **Новый `Action`** (заменяет старый `ActionRequest`):
  - Поля: `schemaVersion: Int`, `providerId: ProviderId`, `payload: ActionPayload`, `fallback: Action? = null`, `sourceModuleId: String? = null`.
  - `ProviderId` — это **inline value class над `String`** (не enum). См. §5.1 — это критическое решение.
  - `ActionPayload` — sealed class с известными вариантами: `OpenApp(packageHint, storeUrlHint)`, `WhatsAppMessage(contactRef)`, `WhatsAppCall(contactRef, kind)`, `Phone(number)`, `Sms(number, body?)`, `Url(url)`, `YouTube(target)`, `OpenSettings(target)`, `Custom(key, params: Map<String, String>)`.
- **Новый `DispatchResult`**: `Ok`, `BlockedByPolicy(reason)`, `ProviderUnavailable(providerId, hint: UnavailabilityHint)`, `Failure(reason)`. **Никаких provider-specific вариантов** (`DispatchResult.WhatsApp` удаляется). `UnavailabilityHint` enum: `Missing` (приложение не установлено), `NotApplicable` (нет SIM/телефонии), `UnknownInThisVersion` (per Clarification C1 — provider в payload новее этой версии лаунчера).
- **`ProviderId` константы** для встроенных провайдеров: `ProviderId.APP`, `ProviderId.WHATSAPP`, `ProviderId.TELEGRAM`, `ProviderId.PHONE`, `ProviderId.SMS`, `ProviderId.BROWSER`, `ProviderId.YOUTUBE`, `ProviderId.SYSTEM_SETTINGS`.
- **Порт `ActionDispatcher` (interface) в commonMain**: `suspend fun dispatch(action: Action): DispatchResult`. **Сейчас** `ActionDispatcher` — конкретный класс в androidMain; превращаем в интерфейс. (Правило CLAUDE.md §1: домен видит порт, не реализацию.)
- **Порт `ProviderRegistry` (interface) в commonMain**: `fun availability(providerId: ProviderId): ProviderAvailability`, `fun snapshot(): List<ProviderState>`, `val updates: Flow<List<ProviderState>>`.
  - `ProviderAvailability`: `Available`, `Missing(installHint: InstallHint?)`, `NotApplicable(reason)`.
- **Wire-format JSON-схема `Action`** (для `flows_mock_*.json`, будущих экспортов и backend-sync). Со `schemaVersion = 1` с первого коммита. См. §5.2.
- **In-memory fake adapter** для `ActionDispatcher` (записывает вызовы, возвращает заданный `DispatchResult`). Используется в тестах UI и в dev-build для двухэмуляторного smoke-check.
- **Roundtrip-тест** wire-формы: `Action → JSON → Action`, `assertEquals`. Fixtures лежат в `core/src/commonTest/resources/fixtures/action-wire-format/` (per Clarification C4) — один JSON-файл на сценарий.
- **Backward-compat-тест**: чтение старых mock JSON'ов spec 003 через explicit-migration-функцию `migrateLegacyAction(json): Action` (см. §5.3 — это переходный мост, удаляется в spec 006).

#### 4.1.2 Android adapter (androidMain)

- **`AndroidActionDispatcher : ActionDispatcher`** — единая реализация, делегирующая работу мини-handler'ам по `providerId`:
  - `AppLaunchHandler` — `getLaunchIntentForPackage`, fallback в Play Store.
  - `WhatsAppHandler` — deep-link `https://wa.me/...` или `whatsapp://send?phone=...`, fallback в Play Store на пакет `com.whatsapp`.
  - `TelegramHandler` — `tg://resolve?...` / `https://t.me/...`, fallback в Play Store.
  - `PhoneHandler` — `Intent(ACTION_DIAL)` с `tel:` (без auto-call — это требует разрешения CALL_PHONE; см. §7.5).
  - `SmsHandler` — `Intent(ACTION_SENDTO)` с `smsto:`.
  - `BrowserHandler` — `Intent(ACTION_VIEW)` с `https:` / `http:` URI.
  - `YouTubeHandler` — `vnd.youtube:...` для видео/каналов; иначе ACTION_VIEW на `https://youtube.com/...`.
  - `SystemSettingsHandler` — текущая логика `OpenSystemSettings` мигрирует сюда.
- **`AndroidProviderRegistry : ProviderRegistry`** — наблюдатель за `AppIndex.snapshot`, выводит availability каждого провайдера из package-set:
  - `whatsapp` available ⇔ установлен `com.whatsapp` или `com.whatsapp.w4b`.
  - `telegram` available ⇔ `org.telegram.messenger` / `org.telegram.plus`.
  - `phone` available ⇔ есть `PackageManager.FEATURE_TELEPHONY` И установлено приложение, отвечающее на `tel:` intent.
  - `sms` available ⇔ есть default-SMS-приложение (`Telephony.Sms.getDefaultSmsPackage`).
  - `youtube` available ⇔ установлен `com.google.android.youtube` ИЛИ `com.google.android.youtube.tv` ИЛИ есть браузер.
  - `browser` available ⇔ есть приложение, отвечающее на `https:` ACTION_VIEW.
  - `app` всегда available.
- **`PlayStoreFallbackResolver`** — единая утилита: «дай мне deep-link установки для пакета X» (`market://details?id=X`) с web-fallback.
- **DI wiring** в `PlatformKoinModule.android.kt`: связывает interfaces commonMain → их android-реализации.

#### 4.1.3 UI / Wizards (commonMain)

- `AddSlotWizardScreen` (из spec 003) — фильтрует список провайдеров через `ProviderRegistry.availability`. Недоступные либо скрыты, либо помечены «не установлено — установить?».
- На экранах подтверждения действия (`ConfirmationOverlay`) показывать имя провайдера через локализованную строку (см. §4.1.6), не хардкод «WhatsApp».
- `FlowScreen` — заменяет вызов `dispatchAction(ActionRequest)` на `dispatchAction(Action)`. Никаких изменений в визуале; меняется только тип, передаваемый в callback.

#### 4.1.4 Mock-данные

- `flows_mock_workspace.json`, `flows_mock_simple-launcher.json`, `flows_mock_launcher.json` — переписать в новой wire-форме (`schemaVersion: 1`, `providerId`, `payload`, `fallback`).
- `whatsapp_tiles_mock.json` **удаляется**; контент его (mock-контакты) перемещается в новый `mock_contacts.json` без whatsapp-привязки. Контакт — это `{ ref, displayName }`. Привязка контакта к провайдеру делается **на уровне slot'а** через `payload.contactRef`, не на уровне контакта.

#### 4.1.5 Тесты

- **Контрактные тесты (commonTest)**:
  - Roundtrip JSON `Action` для каждого `ActionPayload`-варианта.
  - Backward-compat: чтение каждого варианта старой wire-формы spec 003.
- **Fake adapter test (commonTest)**: `FakeActionDispatcher.dispatch` записывает action и возвращает заданный результат.
- **UI tests (`:core/androidUnitTest`)**: `FlowScreen` с fake-dispatcher — тап на slot → `Action` с правильными `providerId`+`payload` записан в fake.
- **Android adapter integration tests**: для каждого handler'а — построить ожидаемый `Intent` и сравнить (`Intent.filterEquals` + extras assertion). Без реального запуска intent'а.

#### 4.1.6 Локализация

Новый файл `compose-resources/values/strings_actions.xml` (или append в основной): локализованные имена провайдеров (`provider_name_whatsapp = "WhatsApp"`, `provider_name_phone = "Звонок"` и т.д.), сообщения недоступности, fallback-prompts. Per ADR-004 — все user-facing строки локализуемы.

### 4.2 Out of Scope

- **Custom-провайдеры от пользователя/админа** — `ProviderId.Custom("...")` и `ActionPayload.Custom(...)` структурно поддерживаются, но в этом spec'е нет UI для их создания. Это перенесётся в spec 006 (`provider-capabilities-and-health`) или spec 008 (`remote-control`).
- **Capabilities-report** в Firebase — отдельный spec 006.
- **Реальный backend/sync** — spec 007.
- **Запуск с авто-вызовом телефона** (`ACTION_CALL` вместо `ACTION_DIAL`) — требует `CALL_PHONE` permission и user-prompt; явно out of scope, см. §7.5.
- **iOS-реализация Android-аналогов** — `:iosMain` остаётся stub'ом. Этот spec пишет только `commonMain` ports + `androidMain` adapters. iOS-handler'ы появятся в spec'е, инициирующем iOS-разработку.
- **Удалённое управление действиями** — spec 008.
- **Entitlement-гейтинг** провайдеров (some providers только для paid-tier) — spec, относящийся к ADR-003 (monetization).
- **Региональная экспансия провайдеров** (LINE для Японии, KakaoTalk для Кореи, WeChat для Китая, Viber, региональные банки и т.д. — потенциально сотни–тысячи) — out of scope; in scope в spec 008/009 как **remote provider registry**. Архитектура spec 005 **сознательно совместима**: `ProviderId` — value class над `String` (не enum), неизвестный provider даёт `ProviderUnavailable(UnknownInThisVersion)` без падения, метаданные провайдера (имя, иконка, store URL, deep-link шаблон) поедут с remote registry в spec 008+. В сборке APK остаётся только узкое ядро (~8–30 «глобальных»). См. R6 в [`research.md`](./research.md).
- **Шаринг шаблонов flow между пользователями** (один человек собрал flow с визуализацией, поделился, получатель импортировал и подменил контакты на свои) — out of scope; in scope в spec 010 как QR/share. Action wire format v1.0.0 **сознательно рассчитан** на этот use case: `contactRef` остаётся абстрактной ссылкой, разрешаемой через локальный contact-registry получателя (не «прибитой» к конкретному ID); URI scheme валидируется (`BrowserHandler` reject не-https/http); `Custom.params` имеет жёсткие лимиты (16/64/1024) — это снижает риск вредоносного импорта. См. R7 в [`research.md`](./research.md).

---

## 5. КРИТИЧЕСКИЕ РЕШЕНИЯ — ОДНОСТОРОННИЕ ДВЕРИ *(объяснение для новичка)*

> **Что такое «односторонняя дверь» (one-way door)?** Решение, которое после принятия будет дорого/долго отменять. Например: имена API, форма JSON, идентификаторы в БД. Каждое такое решение нужно явно обсудить, выбрать, и записать **exit ramp** — «как мы выйдем, если ошибёмся». См. [CLAUDE.md правило 3](../../CLAUDE.md).

### 5.1 ⚠️ ProviderId — `String` или `enum`?

**Решение**: `ProviderId` — `value class` поверх `String`, со **встроенными константами** для известных провайдеров.

```kotlin
@JvmInline
value class ProviderId(val value: String) {
    companion object {
        val APP = ProviderId("app")
        val WHATSAPP = ProviderId("whatsapp")
        // ...
    }
}
```

**Альтернатива (отклонённая)**: `enum class ProviderId { APP, WHATSAPP, ... }`.

**Почему отклонили enum:**
- Backend (spec 007) и QR-share (spec 010) могут получать `providerId`, которого ещё нет в текущей версии приложения. Enum-парсинг падает на unknown — приложение крэшится при получении более новой конфигурации.
- Custom-провайдеры (out of scope сейчас, in scope в spec 006) принципиально не могут жить в `enum`.
- Рекомендация ADR-001 (Platform Parity Gate): wire-format должен быть **forward-compatible**.

**Регрет-условие** (когда мы пожалеем): если случится «провайдеры — это reserved namespace, ничего custom мы не хотим, и крэш на unknown — желательное поведение». Маловероятно. Если случится — миграция от `value class` к `enum` элементарна (sealed-конверсия + явный `Unknown` вариант).

**Exit ramp**: единственное место, где `String → ProviderId` парсится — функция `ProviderId.fromWire(s: String): ProviderId` (валидирует только non-empty + не-whitespace). Замена на enum означает изменение **только** этой функции и точки матчинга в `AndroidActionDispatcher`. ≤ 1 дня работы.

### 5.2 ⚠️ Wire-format `Action` — sealed payload или открытая map?

**Решение**: sealed `ActionPayload` для известных типов **+** `ActionPayload.Custom(key: String, params: Map<String, String>)` как escape-hatch для unknown / future / custom.

```kotlin
sealed class ActionPayload {
    data class OpenApp(val packageHint: String, val storeUrlHint: String? = null) : ActionPayload()
    data class WhatsAppMessage(val contactRef: String) : ActionPayload()
    // ...
    data class Custom(val key: String, val params: Map<String, String>) : ActionPayload()
}
```

**Wire-format пример (`schemaVersion: 1`)**:
```json
{
  "schemaVersion": 1,
  "providerId": "whatsapp",
  "payload": { "kind": "whatsapp_message", "contactRef": "alice" },
  "fallback": {
    "schemaVersion": 1,
    "providerId": "app",
    "payload": { "kind": "open_app", "packageHint": "com.whatsapp", "storeUrlHint": "market://details?id=com.whatsapp" }
  }
}
```

**Alternativa (отклонённая)**: полностью открытая `Map<String, Any>` payload, без sealed-классов.
- **Почему отклонили**: домен теряет тип-безопасность. Каждый handler делал бы кастинг, ошибки выявлялись бы в runtime.

**Альтернатива (отклонённая)**: только sealed, без `Custom`.
- **Почему отклонили**: spec 006/008 требуют extensibility; добавить позже = миграция всех уже-сохранённых конфигов. Дешевле зарезервировать сейчас.

**Регрет-условие**: если `Custom` будет использоваться повсеместно вместо добавления типизированных вариантов — wire-format деградирует в untyped map. **Митигация**: code review + lint-rule (если `Custom` в новом mock-JSON ассете и нет issue/spec'а на типизацию — fail).

**Exit ramp**: `Custom` сужается, типизированные варианты добавляются через **эксклюзивный путь**: новый вариант → реальная реализация в handler'е → поэтапная миграция конфигов от `Custom` к типизированной форме. Без breaking change wire-формы.

### 5.3 ⚠️ Schema migration: что делать с уже сохранённой `ReturnContextRecord` на устройствах?

`ReturnContextStore` ([`ReturnContextStore.kt`](core/src/androidMain/kotlin/com/launcher/core/actions/ReturnContextStore.kt)) персистит JSON в SharedPreferences. На dev-устройствах (включая мои собственные emulator/device) могут лежать записи `schemaVersion = 1`. После удаления `ReturnContextStore` — что с ними?

**Решение**: одноразовая cleanup-операция при старте `LauncherCore`: «если SharedPreferences `launcher.communication.return_context` существует — удалить целиком». Без миграции; контекст возврата как фича больше не нужен (старая логика «тап на WhatsApp → запоминаем → restore on home entry» удаляется как часть spec 002 cleanup).

**Почему это OK сейчас:** нет production-пользователей. Удаление dev-state — два-три устройства, известных авторам.

**Регрет-условие**: если фича возврата на home понадобится снова в будущем (например, для multi-step flows в spec 008) — мы потеряли наработанный схемный namespace. Но: если она вернётся, она будет другой (новые поля), значит и schema-version будет другой, и старые записи всё равно бы сломались. Так что не теряем ничего.

**Exit ramp**: cleanup-функция написана с явным комментарием-якорем `// FEATURE-RETURN-CONTEXT-REMOVED-IN-SPEC-005`. Если возрождать — grep по якорю, удалять cleanup, писать новую store с новым schema-version.

### 5.4 ⚠️ Удаление `ActionCycleGuard` и `RestoreOutcomeEvaluator`

Оба класса — часть протокола «вернулся ли пользователь на правильный home после WhatsApp». Этот протокол был придуман для одной фичи spec 002 и **никогда не использовался ни в одной другой фиче**.

**Решение**: удаляем оба. Если в будущем потребуется идемпотентность action-dispatch (anti-double-tap) — это будет более общее решение, не связанное с конкретным провайдером, и оно появится при первом реальном запросе.

**Регрет-условие**: пользователь жмёт на slot два раза подряд → запускается WhatsApp дважды. **Митигация**: debounce на UI-уровне (`enabled = false` на 500мс после тапа). Это in-scope в спецификации, см. §7.6.

**Exit ramp**: общая идемпотентность — feature follow-up в spec 006/008. Не блокирует этот spec.

### 5.5 ⚠️ Один интерфейс `ActionDispatcher` или цепочка handler'ов?

**Решение**: внешний интерфейс — один (`ActionDispatcher.dispatch(Action) → DispatchResult`). Внутри — registry handler'ов по `providerId`.

```kotlin
class AndroidActionDispatcher(
    private val handlers: Map<ProviderId, ActionHandler>,
    private val providerRegistry: ProviderRegistry,
) : ActionDispatcher {
    override suspend fun dispatch(action: Action): DispatchResult { ... }
}

interface ActionHandler {
    suspend fun handle(action: Action, context: HandlerContext): DispatchResult
}
```

**Альтернатива (отклонённая)**: один большой `when` в `AndroidActionDispatcher` — текущий стиль.
- **Почему отклонили**: open-closed. Добавить YouTube → редактировать `AndroidActionDispatcher` → конфликты с другими feature-ветками; невозможен registry-time provider в spec 008.

**Регрет-условие**: handlers — overkill для 7 провайдеров. **Митигация**: handler-интерфейс настолько маленький (одна функция), что overhead абстракции минимален. Тест-покрытие per-handler легче, чем гигантский `when`.

**Exit ramp**: collapse в `when` — механическое преобразование, ≤ 1 дня. Контракт `Action` остаётся неизменным (это зашитая инкапсуляция, не публичный контракт).

---

## 6. Файлы и удаления

### 6.1 Новые файлы (commonMain)

- `core/src/commonMain/kotlin/com/launcher/api/Action.kt` — модели `Action`, `ActionPayload`, `ProviderId`, `DispatchResult`, `ProviderState`, `ProviderAvailability`, `UnavailabilityHint`.
- `core/src/commonMain/kotlin/com/launcher/api/ActionDispatcher.kt` — interface (порт).
- `core/src/commonMain/kotlin/com/launcher/api/ProviderRegistry.kt` — interface (порт).
- `core/src/commonMain/kotlin/com/launcher/api/ActionWireFormat.kt` — kotlinx-serialization сериализаторы (commonMain — сериализация чистая, без Android-зависимостей).
- `core/src/commonTest/kotlin/com/launcher/api/ActionWireFormatTest.kt` — roundtrip + backward-compat.

### 6.2 Новые файлы (androidMain)

- `core/src/androidMain/kotlin/com/launcher/core/actions/AndroidActionDispatcher.kt` — основной класс.
- `core/src/androidMain/kotlin/com/launcher/core/actions/handlers/` — 8 файлов handler'ов (`AppLaunchHandler.kt`, `WhatsAppHandler.kt`, `TelegramHandler.kt`, `PhoneHandler.kt`, `SmsHandler.kt`, `BrowserHandler.kt`, `YouTubeHandler.kt`, `SystemSettingsHandler.kt`).
- `core/src/androidMain/kotlin/com/launcher/core/providers/AndroidProviderRegistry.kt`.
- `core/src/androidMain/kotlin/com/launcher/core/actions/PlayStoreFallbackResolver.kt`.

### 6.3 Изменения в существующих файлах

- `core/src/commonMain/kotlin/com/launcher/api/ActionModels.kt` — **удаляется целиком**, его роль занимают `Action.kt` + `ActionDispatcher.kt`.
- `core/src/androidMain/kotlin/com/launcher/core/actions/ActionDispatcher.kt` — **удаляется**, заменяется на `AndroidActionDispatcher.kt`.
- `core/src/commonMain/kotlin/com/launcher/api/FlowModels.kt` — `SlotAction` мигрирует с дискриминированного sealed-class'а на `Action` (один тип, не sealed).
- `core/src/androidMain/kotlin/com/launcher/core/flows/MockFlowRepository.kt` — парсинг по новой wire-форме, через `migrateLegacyAction` для совместимости с не-обновлёнными ассетами.
- `core/src/androidMain/assets/flows_mock_*.json` — переписать в новой wire-форме (схема `schemaVersion: 1`).
- DI: `PlatformKoinModule.android.kt` — wire `AndroidActionDispatcher`, `AndroidProviderRegistry`, handlers.
- `LauncherCore.kt` — пересобрать composition root, добавить cleanup-вызов §5.3.

### 6.4 Удаления (полный список — закрытие [решения #6](../../docs/product/session-2026-05-05-decisions.md))

| Файл | Статус по разведке | Действие |
|------|--------------------|----------|
| `core/src/commonMain/kotlin/com/launcher/api/CommunicationModels.kt` | ✓ существует | **DELETE** |
| `core/src/commonMain/kotlin/com/launcher/api/ActionModels.kt` | ✓ существует | **DELETE** (заменён на `Action.kt`) |
| `core/src/androidMain/kotlin/com/launcher/core/actions/ActionDispatcher.kt` | ✓ существует | **DELETE** (заменён на `AndroidActionDispatcher.kt`) |
| `core/src/androidMain/kotlin/com/launcher/core/actions/CommunicationConfigValidator.kt` | ✓ существует | **DELETE** |
| `core/src/androidMain/kotlin/com/launcher/core/actions/WhatsAppLaunchabilityResolver.kt` | ✓ существует | **DELETE** (логика наличия пакета мигрирует в `AndroidProviderRegistry`) |
| `core/src/androidMain/kotlin/com/launcher/core/actions/ReturnContextStore.kt` | ✓ существует | **DELETE** (см. §5.3) |
| `core/src/commonMain/kotlin/com/launcher/core/actions/ActionCycleGuard.kt` | ✓ существует | **DELETE** (см. §5.4) |
| `core/src/commonMain/kotlin/com/launcher/core/actions/RestoreOutcomeEvaluator.kt` | ✓ существует | **DELETE** (см. §5.4) |
| `core/src/androidMain/assets/whatsapp_tiles_mock.json` | ✓ существует | **DELETE** (контакты переезжают в `mock_contacts.json`) |
| `core/src/commonMain/kotlin/com/launcher/core/events/CommunicationDiagnostics.kt` | ✓ существует | **DELETE** (диагностика была WhatsApp-специфичной; общие диагностики dispatch появятся в spec 006) |
| `app/src/main/java/com/launcher/app/communication/` | ✗ нет (удалён в 004) | — |
| Layouts `view_contact_tile.xml`, `view_whatsapp_confirmation.xml`, `view_whatsapp_warning.xml` | ✗ нет (удалены в 004) | — |

После исполнения spec 005 в репозитории не должно остаться ни одного файла, содержащего идентификаторы `WhatsAppHandoff`, `ReturnContext`, `ActionCycleGuard`, `CommunicationConfigValidator`, `WhatsAppLaunchabilityResolver`, `RestoreOutcomeEvaluator`. Это проверяется как **fitness function** (см. §8).

---

## 7. Поведенческие правила (что должен делать dispatcher)

### 7.1 Базовый алгоритм

```
dispatch(action: Action) =
  1. Если action.schemaVersion > SUPPORTED_SCHEMA_VERSION → Failure("unsupported schema")
  2. handler = handlers[action.providerId]
     если null → ProviderUnavailable(action.providerId, UnknownInThisVersion)  // per C1: forward-compat
                 если action.fallback != null → dispatch(action.fallback)
  3. availability = providerRegistry.availability(action.providerId)
  4. Если availability is Missing/NotApplicable:
       Если action.fallback != null → dispatch(action.fallback)  // recursion с depth=1
       Иначе → ProviderUnavailable(action.providerId, hint = availability)
  5. result = handler.handle(action)
  6. Если result is Failure И action.fallback != null → dispatch(action.fallback)
  7. return result
```

### 7.2 Глубина fallback

Fallback может ссылаться на другой `Action` с собственным fallback'ом. **Максимальная глубина — 2** (action + fallback + fallback-of-fallback). Глубже → `Failure("fallback chain too deep")`. Это защита от циклов и от подложенных конфигов с длинными цепочками.

### 7.3 Атомарность

Один `dispatch()` запускает максимум один Android-Intent. Если intent выбросил `ActivityNotFoundException` — это `Failure`, дальше срабатывает fallback. Это не «retry», это «попробовать другой провайдер».

### 7.4 Логирование

Все вызовы `dispatch` пишутся в `EventRouter` как `ProjectEvent.ActionDispatched(providerId, result, fallbackUsed: Boolean)`. Без PII (никаких номеров, контактов, URL'ов). Это нужно для будущих capability-метрик (spec 006).

### 7.5 Безопасность и разрешения

- **Никаких новых runtime permissions в этом spec'е.** `phone` использует `ACTION_DIAL` (откроет dialer, не звонит автоматически — `CALL_PHONE` не нужен).
- **`sms` использует `ACTION_SENDTO`** с `smsto:` URI — открывает default-SMS-app, не отправляет.
- **`browser` через `ACTION_VIEW`** — стандарт Android, default-browser выбирается ОС.
- **Никакой telemetry** — параметры payload'а не пишутся в логи (см. §7.4: только `providerId` и булевы метаданные).
- **Compliance:** обновление [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — секция «нет дельты по разрешениям в spec 005».

### 7.6 Защита от двойного тапа

`TileCard` композик: после тапа `enabled = false` на 500мс. Реализуется в UI-слое (commonMain), не в dispatcher'е. Это **не** идемпотентность dispatcher'а, это UX-защита.

### 7.7 Платформа-asymmetry

`ProviderRegistry` на iOS **в этом spec'е отсутствует** (`:iosMain` остаётся stub). Wizards на Android фильтруют через registry; на iOS аналогичная логика появится в spec'е инициации iOS-разработки. Это формальное **Documented Platform Asymmetry** per ADR-005 §3.117. См. §10.1.

---

## 8. Fitness functions (автоматизированные проверки)

Мы добавляем четыре проверки, которые блокируют PR при нарушении:

1. **Domain-isolation lint** (per CLAUDE.md правило 1, ADR-001 §Platform Parity):
   - в `:core/src/commonMain/` запрещены import'ы `android.*`, `androidx.*`, `*.intent.*`.
   - реализуется через Konsist-тест (kotlinDSL), запускается в CI и `./gradlew check`.
2. **Wire-format roundtrip** (per CLAUDE.md правило 5):
   - тест `ActionWireFormatTest.allPayloadVariantsRoundtrip()` проверяет каждый `ActionPayload`-вариант, включая `Custom` и `null`-fallback.
3. **Whatsapp-residue gate**:
   - grep-тест в CI: `grep -r 'WhatsAppHandoff\|ReturnContext\|ActionCycleGuard\|CommunicationConfigValidator\|WhatsAppLaunchabilityResolver\|RestoreOutcomeEvaluator' src/ docs/dev/` → должно быть 0 строк (исключая текущий spec.md и историю в `docs/governance/`). Не дать им незаметно вернуться.
4. **Legacy-bridge expiry gate** (per Clarification C5):
   - константа `MIGRATE_LEGACY_ACTION_DEADLINE_SPEC = "006"` в `build.gradle.kts`. CI fitness-test: если merged spec id ≥ deadline, символ `migrateLegacyAction` в `core/src/commonMain/` запрещён. Spec 006 должен удалить мост и снять константу в одном PR; иначе CI падает.

---

## 9. Non-Functional Requirements

| Категория | Требование |
|-----------|------------|
| **Performance — cold start** | Spec 004 baseline (`HomeActivity ≤ 600мс`, `FirstLaunchActivity ≤ 700мс`) **не деградирует**. Регистрация handler'ов в Koin — eager только для default-провайдера; остальные lazy. |
| **Performance — dispatch latency** | `dispatch(action)` от вызова до запуска `Intent` ≤ 50мс на medium-tier (Pixel 4a). Замеряется с фейковым ProviderRegistry (без I/O). |
| **Performance — registry update rate** | `AndroidProviderRegistry.updates` flow эмитит ≤ 1 раз/сек (debounce per Clarification C3); только при изменении set'а *available* providers, не на каждый package-install. |
| **APK size** | Не превышать ADR-005 hard-fail (`debug ≤ 22MB`, `release ≤ 16MB`). Этот spec **не добавляет новых внешних зависимостей** — расход 0. |
| **Battery** | Никаких новых background-задач. |
| **Локализация** | Все user-facing строки (имена провайдеров, сообщения недоступности, fallback-prompts) — в `compose-resources/values/strings_actions.xml`, готовы к переводу (per ADR-004). |
| **Cross-platform readiness** | 100% контракта (`Action`, ports, wire-format) живёт в `commonMain`. Android-handler'ы — в `androidMain`. iOS source-set по-прежнему компилируется (пусто). |
| **Accessibility** | Имена провайдеров и причины недоступности озвучиваются TalkBack. Tap-target slot'а ≥ 56dp (наследуется из spec 004 senior-safe override). |
| **Backward compatibility** | Mock JSON spec 003 читается через `migrateLegacyAction` без потерь; этот мост существует только до spec 006, после чего **mandatorily** удаляется. |

---

## 10. Платформенная асимметрия (явная фиксация per ADR-005 §3 Gate 2)

### 10.1 iOS — отсутствие реализаций

| Что | Android (этот spec) | iOS (this spec) | Когда iOS закроется |
|-----|---------------------|------------------|----------------------|
| `ActionDispatcher` | `AndroidActionDispatcher` + 8 handler'ов | stub: `iosMain` source-set пуст | spec инициации iOS (≥ 010) |
| `ProviderRegistry` | `AndroidProviderRegistry` через `AppIndex` | stub: пусто | spec инициации iOS |
| Wizards UI | Фильтр через `ProviderRegistry` | На iOS Wizards недоступны (UI ещё не запускается) | spec инициации iOS |

**Почему скрыт от пользователя**: iOS-приложение не запускается до отдельного spec'а, поэтому пользователь не видит «отсутствие действий». Соответствует ADR-005 §3 Gate 2.

### 10.2 Android-only функциональность

`getLaunchIntentForPackage`, `Intent`, `PackageManager.queryIntentActivities` — это Android-API. Они живут только в `androidMain`. На iOS их аналог (`UIApplication.canOpenURL` + `openURL`) — другая модель, и порт `ActionDispatcher` сознательно **не предъявляет понятие «intent»** — он принимает только `Action`. Это и есть причина существования порта (CLAUDE.md правило 2: ACL для каждого внешнего SDK).

---

## 11. Resource budget delta

Per ADR-005 §3 Gate 3: новых внешних зависимостей **нет**. Все handler'ы — на стандартных Android-классах (`Intent`, `Uri`, `PackageManager`). Konsist (для fitness function §8.1) уже есть как dev-dependency в spec 004.

**Delta APK size: 0 КБ.**

---

## 12. Связанные документы

- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — Articles I (изоляция доменов), II (cross-platform parity), V (wire-format versioning), XI/XIII (Compose/JetBrains exception scope per ADR-005), XV–XVII (governance).
- [`docs/adr/ADR-001-cross-platform-strategy.md`](../../docs/adr/ADR-001-cross-platform-strategy.md) — Platform Parity Gate, isolation principle. **Применяется к каждому handler'у**.
- [`docs/adr/ADR-005-ui-stack-compose-multiplatform.md`](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md) — mandatory gates (cross-platform, asymmetry, resource budget).
- [`docs/adr/ADR-004-localization-and-global-readiness.md`](../../docs/adr/ADR-004-localization-and-global-readiness.md) — все строки локализуемы.
- [`docs/product/session-2026-05-05-decisions.md`](../../docs/product/session-2026-05-05-decisions.md) — решение #6 (список удаляемых файлов).
- [`docs/product/roadmap.md`](../../docs/product/roadmap.md) — позиция 005, зависимости 006/007/008/010.
- [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — обновление: «нет дельты в spec 005».
- [`specs/003-ui-skeleton/spec.md`](../003-ui-skeleton/spec.md), [`specs/003-ui-skeleton/tasks.md`](../003-ui-skeleton/tasks.md) — US-301..US-307 как функциональный source of truth, который не должен сломаться.
- [`specs/004-ui-stack-migration/spec.md`](../004-ui-stack-migration/spec.md) — CMP/KMP базовый стек, на котором этот spec строится.

---

## 13. Definition of Done

- [ ] `Action`, `ActionPayload`, `ProviderId`, `DispatchResult`, `ProviderRegistry` — определены в `commonMain`.
- [ ] `AndroidActionDispatcher` + 8 handler'ов в `androidMain`, все с unit-тестами (`Intent.filterEquals`).
- [ ] `AndroidProviderRegistry` подключён к `AppIndex.snapshot`, реактивно обновляется.
- [ ] Все 7+ провайдеров US-501..US-506 проходят smoke на двух эмуляторах (workspace + simple-launcher), per skill `android-emulator`.
- [ ] Wizard `AddSlotWizardScreen` фильтрует провайдеров по availability (US-507).
- [ ] Все файлы из §6.4 — удалены из репозитория. Grep-проверка §8.3 — 0 совпадений.
- [ ] `flows_mock_*.json` переписаны в новой wire-форме (`schemaVersion: 1`).
- [ ] Roundtrip-тест wire-формы — зелёный.
- [ ] Backward-compat-тест чтения старого формата spec 003 — зелёный.
- [ ] Fitness-functions §8 — все три проходят в CI.
- [ ] Cold start `HomeActivity` ≤ 600мс на medium-tier (замер документирован в `perf-checkpoint.md`).
- [ ] Локализация: `provider_name_*` ключи существуют в `strings_actions.xml`, используются в UI.
- [ ] iOS source-set компилируется (без новых implementations).
- [ ] `docs/compliance/permissions-and-resource-budget.md` обновлён.
- [ ] `roadmap.md` помечает 005 как `Готов`.
- [ ] PR создан, ревью пройдено, merge в `main`.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Заменяем разрозненный `ActionRequest` (с тремя WhatsApp-вариантами от spec 002) на единую модель `Action` с общим форматом для 8 типов действий: `app`, `whatsapp`, `telegram`, `phone`, `sms`, `browser`, `youtube`, `system_settings`. На Android — один `AndroidActionDispatcher` + 8 handler-классов. Удаляются 10 файлов от spec 002. Никаких новых runtime-permissions, новых зависимостей, новых background-задач.

**Конкретика, которую стоит запомнить:**
- 8 user stories US-501..US-508 (по US на провайдер + US-507 «фильтр в визарде по доступности» + US-508 «снэкбар при ошибке»).
- Wire-format `Action` стартует с `schemaVersion: 1`. Максимальная глубина fallback = 2.
- 5 одностороних дверей (§5): `ProviderId` как value class String (не enum), sealed `ActionPayload` + `Custom` escape-hatch, удаление `ReturnContextStore` cleanup'ом, удаление `ActionCycleGuard` (UI-debounce 500мс взамен), handler-registry вместо большого `when`.
- 5 уточнений Clarifications (C1–C5) добавлены в начало спека: unknown-провайдер не падает а даёт `ProviderUnavailable(UnknownInThisVersion)`; `Custom.params` только String→String; `ProviderRegistry.updates` debounced 1с; fixtures в `commonTest/resources/fixtures/action-wire-format/`; мост `migrateLegacyAction` гарантированно умирает в spec 006 (fitness function).
- Производительность: cold start ≤ 600мс (как в spec 004), dispatch ≤ 50мс, registry ≤ 1 emit/сек.
- 4 fitness functions в CI: domain-isolation, wire-format roundtrip, whatsapp-residue grep, legacy-bridge expiry.

**На что смотреть с осторожностью:**
- §5.1 + C1 — выбор «String, не enum» для `ProviderId` определяет, как программа поведёт себя через год при получении новых типов действий, которых эта версия ещё не знает.
- §5.3 — удаляются `SharedPreferences` `launcher.communication.return_context` (одноразовый cleanup в `LauncherCore.start()`); если фича возврата на home когда-то понадобится снова — неймспейс надо брать другой.
- §6.4 — список из 10 файлов под удаление; порядок важен (см. tasks.md, Phase 6 после Phase 3).
- §4.2 Out of Scope содержит два «зарезервированных направления»: региональные провайдеры (LINE/KakaoTalk/WeChat) поедут через remote registry в spec 008+ (R6); шаринг шаблонов flow между пользователями — spec 010 (R7). Любое изменение wire format `Action` обязано не ломать эти два сценария.

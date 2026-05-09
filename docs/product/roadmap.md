# Дорожная карта проекта launcher

Источник: восстановленный транскрипт `.recovered-session-2026-05-05-clean.md`.
Последнее обновление: 2026-05-07 (renumbered after ADR-005).

---

## Сводная таблица

| # | Spec slug | Статус | Зависит от |
|---|---|---|---|
| 003 | `ui-skeleton` | Частично выполнен (5 коммитов, T072 не выполнен; UI закроется через 004) | — |
| **004** | **`ui-stack-migration`** | **Не начат — миграция UI на Compose Multiplatform + Material 3 + KMP per ADR-005; включает закрытие T072** | **003** |
| 005 | `action-architecture-v2` | **Смержен в main** (PR #5 закрыт 2026-05-09; cleanup `migrateLegacyAction` намечен на спек 006) | 004 |
| **006** | **`provider-capabilities-and-health`** | **In progress — clarify завершён 2026-05-09; spec.md → plan.md следующий шаг** | **005** |
| 007 | `pairing-and-firebase-channel` | Не начат | 006 |
| 008 | `bidirectional-config-sync` | Не начат | 007 |
| 009 | `admin-mode-flows` | Не начат | 008 |
| 010 | `setup-assistant-soft-checks` | Не начат | 006 (параллельно 007/008) |
| 011 | `contacts-and-e2e-encrypted-media` | Backlog — добавлен 2026-05-09 (ранее был «контакты» без явного e2e-шифрования) | 008 |
| 012 | `balance-check-and-low-balance-warnings` | Backlog stub — добавлен 2026-05-09 | 007, 009 |
| 013 | `offline-detection-and-emergency-reachability` | Backlog stub — добавлен 2026-05-09 (вынесено из спека 006) | 007, 009 |
| backlog | `config-portability` | Backlog stub | 008 |

> **Renumbering 2026-05-07.** ADR-005 (Compose Multiplatform + Material 3 как обязательный UI-стек) — это большой архитектурный сдвиг, заслуживающий отдельного spec'а. Он встаёт под номером 004; все ранее запланированные spec'и (action-architecture-v2 и далее) сдвинуты на +1. Существующие директории `specs/001-*`, `specs/002-*`, `specs/003-*` не переименовываются — они уже исторически зафиксированы.

---

## Детальное описание каждой spec

### Spec 003 — `ui-skeleton`

**Phase 9 (расширение):** добавлен first-launch preset picker. Три универсальных пресета — `workspace`, `launcher`, `simple-launcher`. Выбор сохраняется в DataStore, переключается через Settings, debug-обход через intent extra `--es preset <slug>`. См. `tasks.md` Phase 9.

**Статус:** частично выполнен. 5 коммитов на ветке `003-ui-skeleton`:
- `65a957e` — docs: add spec 003 ui-skeleton
- `32ee98d` — feat(core): add flow/slot domain models and mock repository
- `8be5392` — feat(app): implement ui skeleton with bottom-nav flow architecture
- `906b279` — fix(test): add missing @RunWith and remove migrated HomeActivity tests
- `64fbe9a` — chore(specs): finalize 003-ui-skeleton tasks checkpoint

**T072 (smoke-check на эмуляторе) — не выполнен.** Требует ручного прохода.

**Что делает:**
- Доменные модели в `:core/api`: `Flow`, `Slot`, `TileConfig` (sealed), `Preset`, `PresetRequirement`, `LanguagePreference`.
- `RemoteSyncBackend` интерфейс в `:core/api/sync` (без реализации).
- `HomeActivity` → NavigationHost с горизонтальным `BottomFlowBar` (вкладки из `flows_mock.json` + «+»).
- `FlowFragment` → грид слотов; тап → confirmation overlay.
- `SettingsFragment` → язык-placeholder, пресет, toggle + QR-placeholder (AlertDialog), сброс данных.
- `AddFlowWizardFragment` / `AddSlotWizardFragment` → заглушки.
- `AdminDevicesFragment` → пустое состояние + FAB «+».
- `MockFlowRepository` читает `flows_mock.json` (schemaVersion:1); все 22 теста зелёные.
- Локализация ru-RU + en-US, override через Settings.

**Что НЕ входит:**
- Реальный Firebase / network / pairing-логика.
- Реальный provider dispatch (только mock-toast).
- Persistence кроме mock JSON.
- First-launch preset picker (обсуждался, реализован ли — уточнить по коду).

**Незавершённое в 003:**
- T072 smoke-check: пройти весь first-launch → добавить flow → добавить тайл → открыть Settings → сменить пресет → увидеть `!`-индикатор → выполнить требование → индикатор исчез.

---

### Spec 004 — `ui-stack-migration` *(per ADR-005)*

**Статус:** реализован, PR open. T072 закрыт через T413 на двух эмуляторах.

**Зависит от:** 003.

**Что делает:**
- Подключает Compose Multiplatform + Material 3 + Kotlin Multiplatform per ADR-005.
- Переводит `:core` в KMP-модуль (commonMain pure Kotlin, androidMain platform impl через `expect`/`actual`, iosMain stub).
- Подключает Decompose (навигация) и Koin (DI) per Amendment 2026-05-07a.
- Создаёт `docs/dev/design-system.md` с Material 3 + senior-safe override.
- Pilot экран — `FirstLaunchScreen` на CMP; gate-decision до полной миграции.
- Перевод остальных экранов 003 (HomeScreen, FlowScreen, SettingsScreen, AddFlowWizardScreen, AddSlotWizardScreen, AdminDevicesScreen) с View System на Composable.
- Удаление XML layouts, Fragment'ов, ViewBinding-кода.
- Замена Robolectric Activity-тестов на Compose UI tests через `createComposeRule`.
- Закрытие T072 (smoke-check) на двух эмуляторах.

**Что НЕ входит:**
- iOS-bootstrap (отдельный spec, отложен per Amendment 2026-05-07b).
- Реальный action dispatch (это spec 005).
- Cupertino-look на iOS (фиксировано: Material 3 на обеих платформах per Amendment 2026-05-07b).

**Required gates per ADR-005:**
- Cross-Platform Implementation Gate: для каждого экрана и domain-операции явно указано source-set.
- Documented Platform Asymmetry: HOME-mode binding (ROLE_HOME, AccessibilityService) — Android-only.
- Resource Budget Delta: впервые принимается CMP overhead (APK +5–15 МБ) согласно ADR-005.
- Performance targets: cold start `HomeActivity` ≤600 мс, `FirstLaunchActivity` ≤700 мс на medium-tier; 0 dropped frames на основных скроллах.

---

### Spec 005 — `action-architecture-v2` *(was 004, renumbered)*

**Статус:** не начат.

**Зависит от:** 003 (доменные модели).

**Что делает:**
- Реальный `ActionRequest` с `{provider, payload, fallback}` — расширение текущего sealed class.
- `ProviderRegistry` поверх `AppIndex` — маппинг установленных приложений на провайдеры (whatsapp, telegram, youtube, phone, sms, browser, custom-url).
- Реальный dispatch через интенты: WhatsApp deep-link, YouTube intent, `tel:`, `sms:`, открытие Play Store.
- Удаление файлов 002-whatsapp (перечень в `session-2026-05-05-decisions.md`, решение #6).
- Маппинг mock-провайдеров из spec 003 на реальные интенты.
- Правила миграции: как соотносится старый `ActionRequest.WhatsAppHandoff` с новым `{provider: "whatsapp", payload: ...}`.

**Что НЕ входит:**
- Firebase / remote config.
- Capabilities-report (это в 005).
- Удалённое управление.

---

### Spec 006 — `provider-capabilities-and-health` *(was 005, renumbered)*

**Статус:** не начат.

**Зависит от:** 005.

**Pre-requisite cleanup that landed with this spec (Phase 1, 2026-05-09):** `migrateLegacyAction` бридж удалён из `core/.../api/action/ActionWireFormat.kt` вместе с grep-anchor `LEGACY-BRIDGE-EXPIRES-IN-SPEC-006`, 5 `legacy-spec003-*.json` фикстур, 5 fixture-методов в `ActionWireFormatFixtureTest.kt`, 9 legacy-методов в `ActionWireFormatTest.kt`, и константа `migrateLegacyActionDeadlineSpec` из `core/build.gradle.kts`. `LegacyMigrationExpiryTest` стал green-and-noop. См. spec 005 §8.4 / Clarification C5.

**Что делает:**
- Снапшот «какие провайдеры доступны на этом устройстве»: `{provider, displayName, icon, available, version}`.
- Health-метрики OLD: battery %, connectivity (WiFi/mobile/none), lastSeen, версия приложения.
- Локальные потребители снапшота: Setup Assistant (spec 010) и UI tile-editor (spec 003+).
- Подготовка к экспорту в `/links/{linkId}/capabilities` и `/links/{linkId}/health` (фактический экспорт — в spec 007).

**Что НЕ входит:**
- Реальная отправка в Firebase (только локальный потребитель).
- Background service — только hook на `ProcessLifecycleOwner.RESUMED`.

---

### Spec 007 — `pairing-and-firebase-channel` *(was 006, renumbered)*

**Статус:** не начат.

**Зависит от:** 006.

**Что делает:**
- Firebase project setup, подключение `google-services.json`.
- `FirebaseRemoteSyncBackend` — реализация интерфейса `RemoteSyncBackend` из spec 003.
- Pairing protocol (шаги 1-6, описаны ниже в разделе «Pairing protocol»).
- Реальный consent-screen на стороне OLD (с данными admin'а).
- Firestore security rules: admin пишет только в `/config`, OLD пишет только в `/state`, `/capabilities`, `/health`.
- FCM для push-уведомлений об изменениях конфига.
- `FakeRemoteSyncBackend` — in-memory для тестов.

**Что НЕ входит:**
- Bidirectional config sync (это в 007).
- Admin-mode UI (это в 008).

**Требует до старта:**
- Создание Firebase project (real env).
- Обновление `docs/compliance/country-legal-tax-register.md` (новые персональные данные).
- Обновление `docs/compliance/permissions-and-resource-budget.md` (новые network permissions).

---

### Spec 008 — `bidirectional-config-sync` *(was 007, renumbered)*

**Статус:** не начат.

**Зависит от:** 007.

**Что делает:**
- Применение `/config` admin→OLD (раскладка flow/слотов/контактов).
- Публикация `/state` OLD→admin (что реально применилось, source of truth для admin-UI).
- Conflict resolution: что делать, если OLD offline и получил устаревший конфиг.
- Schema versioning для `/config` и `/state` — поле `schemaVersion` с первого коммита.
- Backward-compat read: читать `/config` предыдущей версии.
- Migration path при смене схемы.
- Room database для локального хранения конфига (вместо mock JSON из 003).

**Что НЕ входит:**
- Admin-mode UI (это в 008).
- Commands (create-delete-move tiles через push) — уточнить: входит ли в 007 или в 008.

**Важный паттерн:**
Admin-UI рендерится из `/state` (что реально применилось), а не из `/config` (что admin отправил). Это защита от partial-apply (provider недоступен на OLD).

---

### Spec 009 — `admin-mode-flows` *(was 008, renumbered)*

**Статус:** не начат.

**Зависит от:** 008.

**Что делает:**
- Полноценный UI редактора у admin'а: список paired-устройств (не mock), тап → device-detail.
- Мониторинг health: battery %, connectivity, lastSeen — живые данные из Firebase.
- Редактирование раскладки OLD'а удалённо: добавление/удаление/перемещение тайлов.
- Добавление контакта из телефона admin'а (его собственный `READ_CONTACTS`) в слот OLD'а без `READ_CONTACTS` на OLD.
- `/commands/{cmdId}` — push-команды от admin'а к OLD (open Play Store, refresh capabilities, и т.д.).
- Расширяет каркас 003 на реальных данных (убирает mock из `AdminDevicesFragment`).

**Что НЕ входит:**
- Screen mirroring / remote control (нельзя без DPC).
- iOS управление.

---

### Spec 010 — `setup-assistant-soft-checks` *(was 009, renumbered)*

**Статус:** не начат.

**Зависит от:** 006. Может разрабатываться параллельно с 007/008.

**Что делает:**
- Чек-лист первичной настройки OLD-устройства (admin физически рядом).
- Soft-checks: каждый check имеет `id`, `criticality`, `check()`, `resolveIntent()`.
- Критичность: required (без этого функция не работает) / recommended (рекомендуется).
- Deep-link helpers: открыть Settings для конкретного разрешения, запросить ROLE_HOME.
- Индикатор `!` в Settings — сколько требований не выполнено.
- Переключение пресетов с diff-проверкой (что выполнено, что нет при смене пресета).
- Явный экран «что умеет этот пресет» при первом выборе.
- PIN-setup (финализация dev-stub `1234` из spec 003).

**Что НЕ входит:**
- DPC/Device Owner provisioning (полный strict-mode) — это будущий ADR.
- iOS.

---

### Spec 011 — `contacts-and-e2e-encrypted-media` *(добавлен 2026-05-09)*

**Статус:** backlog (без stub-файла).

**Зависит от:** 008.

**Что делает:**
- Чтение контактов с устройства admin'а, передача выбранных контактов в `/config` для отображения в раскладке OLD'а.
- Хранение фотографий контактов и других **приватных медиа** (фото родственников, фото бабушки, фото личных документов) с **end-to-end шифрованием**: ключ генерируется парой устройств при pairing (спек 007), шифрование происходит на устройстве до загрузки в Firebase Storage, расшифровка на устройстве-получателе. Google не имеет доступа к содержимому медиа-файлов.
- Резолюция приватных медиа через namespace `private:` в `IconStorage` (введён в спеке 006).

**Что НЕ входит:**
- Шифрование `/config` целиком — там лежат настройки, не приватные данные.
- Шифрование иконок провайдеров (`bundled:`, `custom:`) — они не приватные.

**Требования к спеку 006 (предусмотрено):**
- `IconStorage` порт умеет работать с приватными медиа через будущую реализацию `EncryptedMediaStorage`. Wire-format `iconId: String` поддерживает namespace `private:<uuid>`.

**One-way door:** выбор криптосистемы (например libsodium / Tink) и схемы обмена ключами. Exit ramp описывается в этом спеке отдельно.

**Зачем именно e2e:** фотографии бабушки и родственников — однозначно приватная информация. Стандартный режим Firebase Storage защищает «доступ только своих по правилам», но Google всё равно технически имеет доступ к файлам. e2e снимает этот вопрос — Google видит только зашифрованные байты.

---

### Spec 012 — `balance-check-and-low-balance-warnings` *(добавлен 2026-05-09)*

**Статус:** backlog (без stub-файла).

**Зависит от:** 007 (Firebase channel для уведомления родственника), 009 (admin UI для отображения статуса баланса).

**Что делает:**
- Автоматическая проверка баланса мобильного оператора на телефоне OLD через USSD-запросы (`*100#` и аналоги).
- Реестр USSD-кодов по странам/операторам (выбираемая поддержка: Россия 4 оператора + СНГ как стартовый набор).
- Парсинг ответов оператора (распознавание суммы, валюты — по шаблонам на регулярных выражениях).
- При обнаружении низкого баланса (< порога):
  - Показывает крупное предупреждение в UI лаунчера на OLD (расширение блока US-5 спека 006).
  - Уведомляет admin через `/health` или отдельный канал в Firebase.
- Толерантно к неподдерживаемым операторам — fallback на «не удалось определить, позвоните родственнику».

**Что НЕ входит:**
- Автоматическая отправка SMS родственнику без интернета (это требует отдельного разрешения SEND_SMS — слишком опасное для пожилого по умолчанию).
- iOS — невозможно (нет API для USSD).

**Почему отдельный спек, а не часть 006:**
- Требует разрешение `CALL_PHONE` (опасное, пугает пожилого) — отдельный consent flow.
- Требует поддерживаемый реестр USSD-кодов — это большая работа, и точность ~80%, не 100%.
- В спеке 006 уже есть базовая защита («нет интернета — крупное предупреждение, кнопка позвонить родственнику») — она работает на 100% устройств без USSD.

**One-way door:** ожидание пользователей «проверка баланса всегда работает» после первого успешного раза. Реестр операторов нужно поддерживать. Exit ramp: spec 012 может быть отключён через feature-flag, основная защита остаётся (предупреждение из спека 006).

---

### Spec 013 — `offline-detection-and-emergency-reachability` *(добавлен 2026-05-09)*

**Статус:** backlog (без stub-файла).

**Зависит от:** 007 (Firebase channel для уведомления родственника), 009 (admin UI).

**Контекст переноса (2026-05-09):** изначально включалось в спек 006 как «эскалация громкости при долгом офлайне» + баннер «Нет интернета». Перенесено сюда после уточнения: **отсутствие интернета — слишком грубый сигнал**. Бабушкин WiFi-роутер может перезагружаться раз в неделю на 5 минут, и это не повод поднимать громкость на максимум. Точная политика «когда бабушка реально без связи с родственником» требует отдельного продумывания.

**Что нужно решить в этом спеке:**
- **Различать причины офлайна**: пропал WiFi vs пропала мобильная сеть vs включён авиарежим vs устройство в Doze (sleep) — каждая причина требует разной реакции.
- **Грейс-периоды по причинам**: WiFi-офлайн при наличии мобильной сети не критичен (родственник дозвонится через мобильный); мобильный-офлайн при наличии WiFi не критичен (WhatsApp/Telegram работают); полный офлайн (нет ни WiFi ни мобильного) — это уже проблема.
- **Длительность считается отдельно** для WiFi-офлайна, мобильного-офлайна, полного офлайна.
- **Эскалация громкости** — реакция на **длительный полный офлайн**, не на любое отсутствие интернета.
- **Баннер «Нет связи с родственником»** — крупное предупреждение в UI лаунчера с кнопкой «Позвонить родственнику» (через системный набор номера).
- **Уведомление admin'у** через Firebase когда мы детектируем длительный полный офлайн — но только если есть моменты онлайн чтобы отправить уведомление.
- **Граница с балансом (спек 012)**: USSD-проверка баланса может быть одной из реакций «не понимаем почему мобильный офлайн» — возможно, спеки 012 и 013 нужно объединить.

**One-way door:** UX поведения «телефон бабушки сам поднимает громкость и шлёт уведомления» — пользователь привыкает за пару недель, отказываться потом не получится. Поэтому решения этого спека требуют наблюдения над несколькими реальными пользователями перед фиксацией.

**Что НЕ входит:**
- Решение «дозвониться через любой канал любой ценой» (это уже не про лаунчер).
- iOS — детектирование офлайна возможно, но реакции (управление громкостью) — нет.

**Зачем именно отдельный спек:** в спеке 006 эта тема выглядела простой («интернет пропал → реакция»), но при глубоком обдумывании оказалось что **сигнал «нет интернета» слишком зашумлён** обычными бытовыми перезагрузками WiFi. Нужна более продуманная политика, и она зависит от данных реальных пользователей.

---

### Backlog — `config-portability`

**Статус:** backlog stub (`specs/backlog/config-portability.md`).

**Зависит от:** 008.

**Что делает (когда дойдёт очередь):**
При замене OLD-устройства admin перепривязывает новый телефон к существующему `linkId`/конфигу без потери настроек.

**Варианты реализации (для будущего spec'а):**
1. Bind по `linkId` в облаке: admin выпускает новый QR для существующего `linkId`, старая привязка инвалидируется, `/config` остаётся.
2. Local export/import: на OLD — экспорт конфига в файл через `ACTION_CREATE_DOCUMENT`, на новом — импорт.
3. Cloud backup snapshot: автосохранение слот-снапшотов на дату в Firebase.

---

## Firestore schema

Детально обсуждена в сессии 2026-05-05. Является частью spec 007.

```
/links/{linkId}/
  ├── /config            ← admin пишет, OLD читает (раскладка flow/слотов/контактов)
  │       schemaVersion: Int
  │       flows: [...]
  │       updatedAt: Timestamp
  │
  ├── /state             ← OLD пишет, admin читает (source of truth для admin-UI)
  │       schemaVersion: Int
  │       appliedAt: Timestamp
  │       flows: [...]   ← что реально применилось (может отличаться от /config)
  │       presetId: String
  │
  ├── /capabilities      ← OLD пишет редко (при установке/обновлении приложений)
  │       schemaVersion: Int
  │       updatedAt: Timestamp
  │       providers: [{provider, available, displayName, iconBase64, version}]
  │
  ├── /health            ← OLD пишет периодически (hook на ProcessLifecycleOwner.RESUMED)
  │       schemaVersion: Int
  │       updatedAt: Timestamp
  │       batteryPercent: Int
  │       isCharging: Boolean
  │       connectivity: "wifi" | "mobile" | "none"
  │       lastSeen: Timestamp
  │       appVersion: String
  │
  └── /commands/{cmdId}  ← admin создаёт, OLD выполняет, пишет результат
          schemaVersion: Int
          type: String    ("open_play_store" | "refresh_capabilities" | ...)
          payload: Map
          createdAt: Timestamp
          status: "pending" | "executing" | "done" | "failed"
          result: Map     ← OLD заполняет после выполнения
```

**Правила безопасности (Firestore Security Rules):**
- Admin пишет только в `/links/{linkId}/config` и `/commands/{cmdId}`.
- OLD пишет только в `/links/{linkId}/state`, `/capabilities`, `/health`, `/commands/{cmdId}/result` (апдейт статуса).
- Оба читают весь `/links/{linkId}`.
- Сторонние не имеют доступа.

**Важное соглашение:** Admin-UI рендерится из `/state`, а не из `/config`. То, что admin отправил, может ещё не примениться (OLD offline, provider отсутствует). `/state` — зеркало того, что реально применилось.

---

## Pairing protocol

Шаги 1-6, обсуждены в сессии 2026-05-05.

```
Шаг 1. OLD — первый запуск
  • Генерирует oldDeviceId (UUID).
  • Регистрируется в Firebase Auth (anonymous token).
  • Создаёт /devices/{oldDeviceId} в Firestore.

Шаг 2. OLD — пользователь разрешает удалённое управление
  • Settings → «Разрешить удалённое управление этим телефоном» → включает toggle.
  • OLD создаёт /pairings/{token} в Firestore:
      token: random 6-char alphanumeric
      oldDeviceId: ...
      claimed: false
      expiresAt: now + 5 minutes

Шаг 3. OLD — показывает QR
  • QR содержит deep-link: launcher://pair?token=<token>
  • Пользователь держит экран перед admin'ом.

Шаг 4. Admin — сканирует QR
  • Читает token из QR.
  • Аутентифицируется в Firebase (anonymous token для MVP).
  • Делает Firestore-транзакцию «claim pairing»:
      • Проверяет /pairings/{token}: exists, claimed=false, expiresAt в будущем.
      • Атомарно: claimed=true.
      • Создаёт /links/{linkId} = {adminId, oldDeviceId, createdAt}.

Шаг 5. OLD — consent
  • Слушает /pairings/{token}.claimed.
  • При claimed=true — показывает consent-экран:
      «Admin <adminId> сможет видеть: заряд батареи, последний онлайн, установленные приложения.
       Admin <adminId> сможет менять: список тайлов, контакты, порядок flow.»
  • Кнопки: «Разрешить» / «Отклонить».
  • При «Отклонить» → удаляет /links/{linkId}, сбрасывает /pairings/{token}.
  • При «Разрешить» → пишет /links/{linkId}/state (начальный снапшот).

Шаг 6. Оба — подписка на realtime-listeners
  • OLD: слушает /links/{linkId}/config (получает конфиг от admin'а).
  • Admin: слушает /links/{linkId}/state (видит текущее состояние OLD'а).
  • OLD: периодически пишет /links/{linkId}/health (hook на ProcessLifecycleOwner.RESUMED).
```

**Условия корректной работы:**
- Оба телефона имеют интернет в момент pairing.
- OLD имеет интернет для получения конфига (offline — получит при следующем подключении).
- Admin видит OLD offline через поле `lastSeen` в `/health`.
- FCM требует Google Play Services на OLD (edge-case без GMS — отдельный backlog).

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
| 006 | `provider-capabilities-and-health` | **Готов** (Phase 1-12 завершены 2026-05-10; emulator smoke-check passed; awaiting Phase 13 macrobenchmark + Phase 14 senior-safe walkthrough на physical device) | 005 |
| 007 | `pairing-and-firebase-channel` | **Готов (code-complete 2026-05-11; Phase 1-12 завершены).** Pairing FSM + RemoteSyncBackend port + Firestore/Auth/FCM adapters + Cloudflare Worker push-relay + Security Rules + Managed-side UI + Konsist fitness gates + in-process E2E test. Operational deferrals: T069 wrangler deploy (interactive login), T074 emulator rules tests (JDK 21+), T097-T100 instrumented integration tier (JDK 21+; CI workflow scaffolded `if: false`), T105/T106/T107 perf measurements (macrobenchmark module + 2-emulator smoke), T108 SC-006 R8 follow-up (`TODO-ARCH-006` in backlog). **QR-pairing работает как reusable trust primitive** — `PairingService.claim()` возвращает `TrustEdgeBootstrap`; spec 011/multi-admin расширяют без модификации pairing core. ADR-007 запланирован при имплементации спека 011 | 006 |
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

**Статус:** **Готов** (Phase 1-12 завершены 2026-05-10; emulator smoke-check passed на Medium Phone API 36.1; awaiting Phase 13 macrobenchmark + Phase 14 senior-safe walkthrough на physical device — оба требуют user input).

**Зависит от:** 005.

**Pre-requisite cleanup that landed with this spec (Phase 1, 2026-05-09):** `migrateLegacyAction` бридж удалён из `core/.../api/action/ActionWireFormat.kt` вместе с grep-anchor `LEGACY-BRIDGE-EXPIRES-IN-SPEC-006`, 5 `legacy-spec003-*.json` фикстур, 5 fixture-методов в `ActionWireFormatFixtureTest.kt`, 9 legacy-методов в `ActionWireFormatTest.kt`, и константа `migrateLegacyActionDeadlineSpec` из `core/build.gradle.kts`. `LegacyMigrationExpiryTest` стал green-and-noop. См. spec 005 §8.4 / Clarification C5.

**Что делает:**
- Снапшот «какие провайдеры доступны на этом устройстве»: `{provider, displayName, icon, available, version}`.
- Health-метрики Managed: battery %, connectivity (WiFi/mobile/none), lastSeen, версия приложения.
- Локальные потребители снапшота: Setup Assistant (spec 010) и UI tile-editor (spec 003+).
- Подготовка к экспорту в `/links/{linkId}/capabilities` и `/links/{linkId}/health` (фактический экспорт — в spec 007).

**Что НЕ входит:**
- Реальная отправка в Firebase (только локальный потребитель).
- Background service — только hook на `ProcessLifecycleOwner.RESUMED`.

---

### Spec 007 — `pairing-and-firebase-channel` *(was 006, renumbered)*

**Статус:** **Готов (code-complete 2026-05-11).** Phase 1-12 завершены на ветке `007-pairing-and-firebase-channel`.

**Зависит от:** 006.

**Что сделано:**
- Domain ports в `:core/api/{sync,identity,pairing,link,push,qr}/` — нет ни одного Firebase import; Konsist fitness gate (`DomainIsolationTest`, `Spec007PortFakesTest`) держит это инвариантом.
- `FirebaseRemoteSyncBackend` + `FirebaseIdentityProvider` + `WorkerPushSender` + `FirestoreLinkRegistry` + `LauncherFirebaseMessagingService` — все живут только в `:core/src/androidRealBackend/` source set.
- `mockBackend` flavor с in-memory Fakes — APK строится без `google-services.json`, юнит-тесты и UI-сцены гоняются без сети.
- `PairingService` FSM (Idle → WaitingForClaim → AwaitingConsent → Claimed) — atomic claim transaction, observer на `/pairings/{token}`, dispose() для clean shutdown.
- `TrustEdgeBootstrap` interface — `claim()` возвращает sealed-like контракт; в 007 единственный subtype `Link`, в 011/multi-admin будут добавляться без модификации `PairingService` (см. memory `project_qr_pairing_trust_primitive.md`).
- Cloudflare Worker (`push-worker/`) — POST `/notify`: verify Firebase ID-token → SA OAuth → FCM HTTP v1 `messages:send`. 10 vitest cases (mocked Firebase REST).
- Firestore Security Rules (`firestore.rules`) + ~21 emulator-based rules tests (runtime блокирован JDK 8).
- Managed-side UI — QR display screen с ZXing + senior-safe consent + paired status + unbind double-confirm. EN + RU strings.
- In-process E2E test `PairingEndToEndTest` гоняет два `PairingService` инстанса через один `FakeRemoteSyncBackend`, включая admin-config-write + push-receive consumer.

**Operational deferrals** (документированы с exit ramps):
- **T069** — `wrangler deploy` requires interactive `wrangler login`; runbook в `push-worker/README.md`.
- **T074** — Firestore rules runtime tests требуют JDK 21+ (firebase-tools 14+ drops Java 8).
- **T075** — `firebase deploy --only firestore:rules` требует `firebase login`.
- **T097/T098/T099/T100** — instrumented integration tests + Worker × Emulator stack — code-ready в `specs/007-.../integration-tests-deferred.md`; CI workflow scaffolded `if: false` в `.github/workflows/integration-tests.yml`.
- **T105/T106/T107** — perf measurements (macrobenchmark / 2-emulator smoke / Worker p95) — exit ramps в `specs/007-.../perf-checkpoint.md`.
- **T108 / SC-006** — APK delta измерен 3.99 MB (target 3 MB); follow-up `TODO-ARCH-006` в `docs/dev/project-backlog.md` — включить R8 на `release` buildType (типичный drop 40-60%).

**Что НЕ входит:**
- Bidirectional config sync (это в 008).
- Admin-mode UI (это в 009).

---

### Spec 008 — `bidirectional-config-sync` *(was 007, renumbered)*

**Статус:** не начат.

**Зависит от:** 007.

**Что делает:**
- Применение `/config` admin→Managed (раскладка flow/слотов/контактов).
- Публикация `/state` Managed→admin (что реально применилось, source of truth для admin-UI).
- Conflict resolution: что делать, если Managed offline и получил устаревший конфиг.
- Schema versioning для `/config` и `/state` — поле `schemaVersion` с первого коммита.
- Backward-compat read: читать `/config` предыдущей версии.
- Migration path при смене схемы.
- Room database для локального хранения конфига (вместо mock JSON из 003).

**Что НЕ входит:**
- Admin-mode UI (это в 008).
- Commands (create-delete-move tiles через push) — уточнить: входит ли в 007 или в 008.

**Важный паттерн:**
Admin-UI рендерится из `/state` (что реально применилось), а не из `/config` (что admin отправил). Это защита от partial-apply (provider недоступен на Managed).

---

### Spec 009 — `admin-mode-flows` *(was 008, renumbered)*

**Статус:** не начат.

**Зависит от:** 008.

**Что делает:**
- Полноценный UI редактора у admin'а: список paired-устройств (не mock), тап → device-detail.
- Мониторинг health: battery %, connectivity, lastSeen — живые данные из Firebase.
- Редактирование раскладки Managed'а удалённо: добавление/удаление/перемещение тайлов.
- Добавление контакта из телефона admin'а (его собственный `READ_CONTACTS`) в слот Managed'а без `READ_CONTACTS` на Managed.
- `/commands/{cmdId}` — push-команды от admin'а к Managed (open Play Store, refresh capabilities, и т.д.).
- Расширяет каркас 003 на реальных данных (убирает mock из `AdminDevicesFragment`).

**Что НЕ входит:**
- Screen mirroring / remote control (нельзя без DPC).
- iOS управление.

---

### Spec 010 — `setup-assistant-soft-checks` *(was 009, renumbered)*

**Статус:** не начат.

**Зависит от:** 006. Может разрабатываться параллельно с 007/008.

**Что делает:**
- Чек-лист первичной настройки Managed-устройства (admin физически рядом).
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
- Чтение контактов с устройства admin'а, передача выбранных контактов в `/config` для отображения в раскладке Managed'а.
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
- Автоматическая проверка баланса мобильного оператора на телефоне Managed через USSD-запросы (`*100#` и аналоги).
- Реестр USSD-кодов по странам/операторам (выбираемая поддержка: Россия 4 оператора + СНГ как стартовый набор).
- Парсинг ответов оператора (распознавание суммы, валюты — по шаблонам на регулярных выражениях).
- При обнаружении низкого баланса (< порога):
  - Показывает крупное предупреждение в UI лаунчера на Managed (расширение блока US-5 спека 006).
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

### Backlog — `app-version-compatibility` *(добавлен 2026-05-14, вынесено из 008)*

**Статус:** backlog (без stub-файла).

**Зависит от:** 008 (wire format `/config`, `/state`).

**Контекст переноса (2026-05-14):** изначально планировалось как часть 008 (Q4 clarify). Вынесено отдельным спеком — собственная глубина: Play Store update flows, выбор конкретной версии, OEM-quirks. В 008 решено протестировать монорелизом (все editor'ы одной версии), schema mismatch by construction не возникает.

**Что делает (когда дойдёт очередь):**
- **Detection**: Managed читает `/config.schemaVersion`, сравнивает с `knownSchemaVersion` приложения; если выше — НЕ применяет, продолжает использовать last-applied-config, пишет в `/state.compatibilityError`.
- **App version field (опциональное)**: `/config.requiredManagedAppVersion` (Int versionCode) — admin может явно требовать определённую версию приложения на Managed.
- **App version reporting**: Managed публикует в `/state.managedAppVersion` свой текущий versionCode.
- **Admin-side visibility**: на главном экране admin-UI в списке Managed-телефонов — значок «требует обновление приложения» для тех, у кого `/state.compatibilityError != null`. Деталь: какая версия требуется, какая установлена.
- **Security Rules**: `requiredManagedAppVersion` пишет только `adminId`, не `managedDeviceFirebaseUid` (по смыслу — Managed не должен сам себе ставить требование версии).
- **Remote update mechanism**: запуск Play Store update intent на Managed / выбор конкретной версии / force-install — основная сложность спека.
- **UI «обновить приложение бабушки»**: actionable кнопка в admin UI с инструкциями или прямым запуском обновления.

**Что НЕ входит:**
- Сам wire-format `/config` и `/state` — он живёт в 008 (этот спек только добавляет опциональные поля additive).
- Force-install без согласия пользователя Managed (DPC/Device Owner — это отдельный ADR).

**One-way door:** UX для «admin удалённо ставит конкретную версию приложения бабушке» — пользователь привыкает, отказываться потом будет сложно. Exit ramp: фича опциональна (если `requiredManagedAppVersion` не задан — поведение как сейчас).

**Почему отдельный спек:** Play Store update API, OEM-варианты, выбор конкретной версии — самостоятельная глубина работы. Если бы остался в 008, спек растянулся бы до 8-10 недель.

---

### Backlog — `config-portability`

**Статус:** backlog stub (`specs/backlog/config-portability.md`).

**Зависит от:** 008.

**Что делает (когда дойдёт очередь):**
При замене Managed-устройства admin перепривязывает новый телефон к существующему `linkId`/конфигу без потери настроек.

**Варианты реализации (для будущего spec'а):**
1. Bind по `linkId` в облаке: admin выпускает новый QR для существующего `linkId`, старая привязка инвалидируется, `/config` остаётся.
2. Local export/import: на Managed — экспорт конфига в файл через `ACTION_CREATE_DOCUMENT`, на новом — импорт.
3. Cloud backup snapshot: автосохранение слот-снапшотов на дату в Firebase.

---

## Firestore schema

Детально обсуждена в сессии 2026-05-05. Является частью spec 007.

```
/links/{linkId}/
  ├── /config            ← admin пишет, Managed читает (раскладка flow/слотов/контактов)
  │       schemaVersion: Int
  │       flows: [...]
  │       updatedAt: Timestamp
  │
  ├── /state             ← Managed пишет, admin читает (source of truth для admin-UI)
  │       schemaVersion: Int
  │       appliedAt: Timestamp
  │       flows: [...]   ← что реально применилось (может отличаться от /config)
  │       presetId: String
  │
  ├── /capabilities      ← Managed пишет редко (при установке/обновлении приложений)
  │       schemaVersion: Int
  │       updatedAt: Timestamp
  │       providers: [{provider, available, displayName, iconBase64, version}]
  │
  ├── /health            ← Managed пишет периодически (hook на ProcessLifecycleOwner.RESUMED)
  │       schemaVersion: Int
  │       updatedAt: Timestamp
  │       batteryPercent: Int
  │       isCharging: Boolean
  │       connectivity: "wifi" | "mobile" | "none"
  │       lastSeen: Timestamp
  │       appVersion: String
  │
  └── /commands/{cmdId}  ← admin создаёт, Managed выполняет, пишет результат
          schemaVersion: Int
          type: String    ("open_play_store" | "refresh_capabilities" | ...)
          payload: Map
          createdAt: Timestamp
          status: "pending" | "executing" | "done" | "failed"
          result: Map     ← Managed заполняет после выполнения
```

**Правила безопасности (Firestore Security Rules):**
- Admin пишет только в `/links/{linkId}/config` и `/commands/{cmdId}`.
- Managed пишет только в `/links/{linkId}/state`, `/capabilities`, `/health`, `/commands/{cmdId}/result` (апдейт статуса).
- Оба читают весь `/links/{linkId}`.
- Сторонние не имеют доступа.

**Важное соглашение:** Admin-UI рендерится из `/state`, а не из `/config`. То, что admin отправил, может ещё не примениться (Managed offline, provider отсутствует). `/state` — зеркало того, что реально применилось.

---

## Pairing protocol

Шаги 1-6, обсуждены в сессии 2026-05-05.

```
Шаг 1. Managed — первый запуск
  • Генерирует managedDeviceId (UUID).
  • Регистрируется в Firebase Auth (anonymous token).
  • Создаёт /devices/{managedDeviceId} в Firestore.

Шаг 2. Managed — пользователь разрешает удалённое управление
  • Settings → «Разрешить удалённое управление этим телефоном» → включает toggle.
  • Managed создаёт /pairings/{token} в Firestore:
      token: random 6-char alphanumeric
      managedDeviceId: ...
      claimed: false
      expiresAt: now + 5 minutes

Шаг 3. Managed — показывает QR
  • QR содержит deep-link: launcher://pair?token=<token>
  • Пользователь держит экран перед admin'ом.

Шаг 4. Admin — сканирует QR
  • Читает token из QR.
  • Аутентифицируется в Firebase (anonymous token для MVP).
  • Делает Firestore-транзакцию «claim pairing»:
      • Проверяет /pairings/{token}: exists, claimed=false, expiresAt в будущем.
      • Атомарно: claimed=true.
      • Создаёт /links/{linkId} = {adminId, managedDeviceId, createdAt}.

Шаг 5. Managed — consent
  • Слушает /pairings/{token}.claimed.
  • При claimed=true — показывает consent-экран:
      «Admin <adminId> сможет видеть: заряд батареи, последний онлайн, установленные приложения.
       Admin <adminId> сможет менять: список тайлов, контакты, порядок flow.»
  • Кнопки: «Разрешить» / «Отклонить».
  • При «Отклонить» → удаляет /links/{linkId}, сбрасывает /pairings/{token}.
  • При «Разрешить» → пишет /links/{linkId}/state (начальный снапшот).

Шаг 6. Оба — подписка на realtime-listeners
  • Managed: слушает /links/{linkId}/config (получает конфиг от admin'а).
  • Admin: слушает /links/{linkId}/state (видит текущее состояние Managed'а).
  • Managed: периодически пишет /links/{linkId}/health (hook на ProcessLifecycleOwner.RESUMED).
```

**Условия корректной работы:**
- Оба телефона имеют интернет в момент pairing.
- Managed имеет интернет для получения конфига (offline — получит при следующем подключении).
- Admin видит Managed offline через поле `lastSeen` в `/health`.
- FCM требует Google Play Services на Managed (edge-case без GMS — отдельный backlog).

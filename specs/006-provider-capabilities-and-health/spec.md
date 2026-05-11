# Spec 006: Provider Capabilities and Health

**Status**: Draft (post-clarify) | **Date**: 2026-05-09 | **Author**: project owner
**Branch**: `006-provider-capabilities-and-health`
**Depends on**: 005 (Action wire-format, ProviderRegistry, ProjectEvent.ActionDispatched)

---

## Clarifications

### 2026-05-09 — Pre-plan clarification pass (speckit-clarify)

| # | Grey zone | Resolution |
|---|-----------|------------|
| C1 | **`Capability.version` form**: показывать ли пользователю версию целевого приложения (`versionName` "WhatsApp 2.24.18"), хранить только внутренний номер для feature-detection (`versionCode` Long), или оба? | **Resolution:** только `versionCode: Long?` (nullable, потому что на iOS недоступно через системный API). `versionName` НЕ хранится — пользователю не показываем. **Why:** имя версии не имеет user-value в нашем UI; внутренний номер достаточен для feature-detection в коде. **Exit ramp:** если в будущем понадобится `versionName` — добавление optional поля в wire-format не ломает обратную совместимость. |
| C2 | ~~Триггер реакции «нет связи»~~ — **DEFERRED → spec 013** (2026-05-09). Изначально решено: триггер — отсутствие интернета. После повторного обдумывания: «нет интернета» — слишком грубый сигнал (бытовая перезагрузка WiFi-роутера = ложное срабатывание). Точная политика «когда бабушка реально без связи» требует разделения причин (WiFi vs мобильный vs полный офлайн) и наблюдения над реальными пользователями. Перенесено в новый спек 013 `offline-detection-and-emergency-reachability`. |
| C3 | ~~Эскалация громкости~~ — **DEFERRED → spec 013** (2026-05-09). Связано с C2 — без надёжного триггера эскалация теряет смысл. |
| C4 | ~~Триггер таймера эскалации~~ — **DEFERRED → spec 013** (2026-05-09). Связано с C2/C3. |
| C5 | **`Capability` vs существующий `ProviderState`**: расширять старый тип или ввести новый рядом? | **Resolution:** **полная замена**: `ProviderState` (введён в спеке 005 §4.1.1) удаляется, на его место становится `Capability`. Тесты спека 005 правятся в этом же PR (это часть cleanup-задач). **Why:** один тип проще двух; рассинхронизация исключена. **Cost:** breaking change для спека 005 — 4 fitness-теста и связанный код корректируются. |
| C6 | **Хранилище snapshot'а на диске**: Proto DataStore (типобезопасный, требует .proto + codegen, плохо в KMP) или Preferences DataStore + JSON (проще, переиспользует механизм спека 005)? | **Resolution:** **Preferences DataStore + JSON**. Snapshot сериализуется через тот же `Json` instance, что в спеке 005 (`ActionWireFormat.json`), хранится одним ключом `capability_snapshot_json: String`. **Why:** (а) переиспользуем kotlinx.serialization из спека 005; (б) KMP-friendly без extra build setup; (в) `schemaVersion` живёт внутри JSON. **Trade-off:** меньше build-time гарантий типизации — компенсируется обязательным roundtrip-тестом (CLAUDE.md rule 5). |
| C7 | **Settings sync**: настройки реакций (например `raiseRingerOnLongOffline`) хранятся только локально или синхронизируются с облаком? | **Resolution:** **всегда хранятся локально**. **При наличии интернета синхронизируются с `/config` в облаке** (механизм синхронизации детально — в спеках 007/008). При отсутствии интернета работаем с локальной копией. В спеке 006 закладываем **форму данных** (отдельный DataStore-файл `settings_datastore.preferences`, сериализация через JSON), но **сам канал синхронизации** — задача 008. Конфликт-резолюция (локальное vs облачное изменение) — также задача 008. **Why:** локальное решение источник правды до подключения облака; форма данных готова. |
| C8 | **`IconRef` shape**: жёсткое поле `iconRef: String` (имя bundled-ресурса), sealed class `Bundled \| Remote`, или абстрактный port? | **Resolution:** **port `IconStorage` через DI**. В `Capability` хранится `iconId: String` с namespace-конвенцией: `bundled:<name>` (известные провайдеры в APK), `custom:<uuid>` (кастомные иконки от admin'а в облаке, появятся в спеке 007/009), `private:<uuid>` (приватные медиа с e2e-шифрованием, появятся в спеке 011). В спеке 006 единственная реализация — `BundledIconStorage`. Wire-format также содержит `iconSha256: String?` — отпечаток для будущей кэш-инвалидации. **Why:** wire-format готов к 007/009/011 без миграции; код спека 006 минимален (одна реализация); `Capability` не привязана к источнику иконки. **One-way door (wire-format)**: namespace-конвенция и поле `iconSha256` фиксируются с первого коммита. **Exit ramp:** добавление нового namespace не ломает старые читатели. |
| C9 | **Кэш облачных иконок**: вводим механизм в спеке 006 «впрок», или дожидаемся спека 007? | **Resolution:** **только спецификация в спеке 006, реализация в спеке 007**. В 006: (а) wire-format `iconSha256` поле; (б) порт `IconStorage` с одной реализацией `BundledIconStorage`; (в) явный non-goal на кэш-механику (нечего кэшировать). В 007 при добавлении `RemoteIconStorage` — кэш-логика (LRU, sha-инвалидация, pinning активных иконок) проектируется. **Why:** соблюдение CLAUDE.md правила 4 (не добавляем механику без consumer'а); wire-format «договорённость о форме» — это контракт, не код. |
| C10 | **Удалённая иконка пропала**: восстанавливать из истории на сервере или просто использовать placeholder? | **Resolution:** **placeholder**. Если родственник удалил кастомную иконку из `/config` — она удаляется и из облака (Firebase Storage), и из локального кэша. Истории/корзины удалённых иконок не ведём. **Why:** упрощение — не плодим сложность ради edge case'а; если родственник передумал, добавит заново. **Cost:** случайное удаление не восстановимо. Mitigation: подтверждение «вы уверены?» в admin UI (задача спека 009). |

### 2026-05-09 — Late deferral: offline-related reactions перенесены в спек 013

После переписывания спека пользователь обратил внимание: **«отсутствие интернета»** может быть просто отвалившимся WiFi-роутером дома. Использовать это как триггер для эскалации громкости — давать ложные срабатывания при каждой бытовой перезагрузке роутера. Точная политика «когда считать что бабушка реально без связи» требует разделения причин (WiFi vs мобильный vs полный офлайн) и более продуманной модели.

**Что перенесено в спек 013** `offline-detection-and-emergency-reachability` (см. roadmap.md):
- US-4 «эскалация громкости при долгом офлайне» целиком.
- US-5 баннер «Нет интернета» (один из трёх баннеров в US-5).
- Связанные FR (эскалация steps, scheduler, worker, toggle `raiseRingerOnLongOffline`).
- Связанные SC.

**Что осталось в спеке 006:**
- Health snapshot включает `connectivity` поле — это **просто данные**, без логики реакции.
- US-5 содержит только два баннера — «Авиарежим включён» и «Звук выключен» (баннер «Нет интернета» убран).
- IconStorage, Capability, Health, cleanup спека 005 — без изменений.

### 2026-05-09 — Cross-spec impact

- **Roadmap updates:** добавлены спеки 011 (contacts с e2e-шифрованием), 012 (USSD balance check), 013 (offline detection — вынесено из 006). См. [roadmap.md](../../docs/product/roadmap.md).
- **Spec 005 impact:** `ProviderState` удаляется (C5); 4 fitness-теста спека 005 + связанные тесты корректируются в этом же PR. Бридж `migrateLegacyAction` + 5 фикстур + grep-anchor удаляются (см. cleanup section).

---

## 0. Зачем этот spec вообще существует *(объяснение для новичка)*

После спека 005 лаунчер умеет **исполнять действия**: пользователь жмёт на тайл — открывается WhatsApp, набирается номер, открывается YouTube. Но лаунчер **не умеет рассказать о себе**: какие провайдеры реально установлены на этом устройстве, какой заряд батареи, есть ли интернет.

Без этого слоя «что я умею и в каком я состоянии» три следующих спека не могут стартовать:

- **Setup Assistant (010)** не сможет показать пожилому «WhatsApp не установлен — нажмите чтобы поставить».
- **Admin-родственник (009)** при удалённой настройке шлёт тайл «WhatsApp» вслепую, не зная установлен ли он.
- **Firebase-канал (007)** не имеет, что писать в `/links/{linkId}/capabilities` и `/links/{linkId}/health`.

Спек 006 — это **локальный снапшот** доступности провайдеров и health-метрик устройства, плюс **первый набор «заботливых» реакций** на проблемы со связью и звуком. Снапшот сначала живёт только в памяти + DataStore-проекция, его читают локальные потребители (Setup Assistant, UI tile-editor). Wire-format с `schemaVersion: 1` готовится так, чтобы спек 007 просто подключил Firestore без изменения схемы.

**Почему это критично для будущего:** wire-format `Capability` и `Health` — это **публичный контракт устройства** в адрес admin-приложения. Любая ошибка формы здесь означает миграцию **на всех устройствах пользователей** позднее. Это **one-way door** ([CLAUDE.md правило 3](../../CLAUDE.md), [правило 5](../../CLAUDE.md)). Schema versioning, exit ramps и обратная совместимость закладываются сейчас, до первой записи снапшота на устройство.

---

## 1. Overview

Ввести три блока функциональности (все с `schemaVersion: 1` с первого коммита):

- **`Capability` snapshot** per-provider: `{providerId, displayName, iconId, iconSha256?, available, versionCode?}` — what this device can do.
- **`Health` snapshot** per-device: `{batteryPercent, charging, connectivity, ringerVolumePercent, audioStreamMuted, lastSeen, appVersion}` — how this device feels. Это **только данные**, без логики реакции на состояние сети.
- **Два простых баннера в UI лаунчера** (US-5): «Авиарежим включён» и «Звук выключен». Каждый имеет крупную кнопку действия. Это **независимые от связи** реакции — детектирование происходит через локальные системные настройки, не через измерение интернета.

Снапшоты строятся локально, хранятся `StateFlow + DataStore`-проекция, читаются локальными потребителями. **Сетевой отправки в этом спеке нет** — это задача спека 007. Настройки баннеров (toggle on/off) синхронизируются с облаком при наличии интернета (форма данных закладывается сейчас, механизм синхронизации — спек 008).

**Что НЕ входит в спек 006 (вынесено в будущие спеки):**
- Реакции на отсутствие интернета (эскалация громкости, баннер «Нет интернета», уведомления родственнику) — **спек 013**, потому что «отсутствие интернета» оказался слишком зашумлённым сигналом (бытовые перезагрузки WiFi).
- Автоматическая проверка баланса оператора через USSD — **спек 012**.
- Контакты с приватными фото и e2e-шифрованием — **спек 011**.

Иконки доступны через **порт `IconStorage`** с одной реализацией `BundledIconStorage` (читает встроенные drawable из APK). Поле `iconId: String` использует namespace-конвенцию (`bundled:`, `custom:`, `private:`) — это договорённость о форме wire-format на будущее.

**Pre-requisite cleanup в этом же PR:** удалить `migrateLegacyAction` бридж + 5 `legacy-spec003-*.json` фикстур + grep-якорь `LEGACY-BRIDGE-EXPIRES-IN-SPEC-006`. Это первая задача в `tasks.md`, иначе `LegacyMigrationExpiryTest` уронит main после слития ветки 006. См. [спек 005 §8.4 / Clarification C5](../005-action-architecture-v2/spec.md).

---

## 2. Problem Statement

### 2.1 Что не так сейчас

| Проблема | Где видно | Последствие, если оставить |
|----------|-----------|----------------------------|
| `ProviderRegistry.snapshot()` возвращает `List<ProviderState>` без displayName, иконки, версии | спек 005 §4.1.1 | UI tile-editor не может показать иконку и нормальное имя; Setup Assistant не имеет источника данных. |
| Нет health-метрик вообще | — | Admin (009) не сможет понять «телефон бабушки разряжен / без сети»; реакции на долгий офлайн нереализуемы. |
| Wire-format для capabilities/health не существует | roadmap.md §Firestore schema упоминает `/capabilities` и `/health`, но без `schemaVersion`, иконочной стратегии, обратной совместимости | спек 007 будет вынужден изобретать форму на лету; первая запись = первая миграция. |
| Нет реакции на длительный offline | — | Если телефон бабушки в DND или громкость занижена — родственник теряет канал связи через звонок. |
| Нет user-visible сигналов о проблемах со связью/звуком | — | Бабушка не понимает почему «никто не звонит» — нет видимого предупреждения «у тебя авиарежим / выключен звук». |
| `migrateLegacyAction` + 5 фикстур ждут удаления | спек 005 Clarification C5, [`LegacyMigrationExpiryTest`](../../core/src/androidUnitTest/kotlin/com/launcher/test/fitness/LegacyMigrationExpiryTest.kt) | После слития ветки 006 fitness-тест уронит main, если cleanup не сделан в этом же PR. |

### 2.2 Архитектурные принципы, зафиксированные в этом спеке

| Принцип | Источник |
|---------|----------|
| Snapshot per-device, никогда не приходит с облака к устройству (write-only direction). | Clarification C7 |
| Wire-format с `schemaVersion: 1` и обратной совместимостью читателей. | CLAUDE.md правило 5 |
| Иконки через port `IconStorage`, namespace в `iconId`. | Clarification C8 |
| Реактивность event-driven, не polling. | Article IX §3, Clarification C4 |
| Постепенная эскалация громкости (+20% / час), а не сразу максимум. | Clarification C3 |
| Триггер реакций — **отсутствие интернета**, не отсутствие сотовой связи. | Clarification C2 |
| End-to-end шифрование приватных медиа — отдельный спек 011, в 006 закладываем namespace `private:` без реализации. | Roadmap update 2026-05-09 |
| USSD-проверка баланса — отдельный спек 012, в 006 базовая защита через предупреждение «нет интернета». | Roadmap update 2026-05-09 |

---

## 3. User Stories

### User Story 1 — Setup Assistant видит, что установлено, что нет (Priority: P1)

Родственник admin держит телефон пожилого в руках и впервые настраивает лаунчер. Он хочет увидеть список провайдеров: какие готовы (зелёная галочка), каких нет (красный крест + кнопка «Установить из Play Store»).

**Why this priority**: без этого спек 010 Setup Assistant не может стартовать — нет источника данных «что доступно на этом устройстве».

**Independent Test**: на эмуляторе с установленным WhatsApp но без Telegram открыть debug-экран `CapabilitySnapshotDebugActivity` → увидеть список провайдеров с корректными `available` флагами и иконками.

**Acceptance Scenarios**:

1. **Given** WhatsApp установлен, Telegram нет, **When** snapshot построен, **Then** `Capability(WHATSAPP).available = true`, `Capability(TELEGRAM).available = false`.
2. **Given** WhatsApp удалён в фоне (через `pm uninstall`), **When** лаунчер вернулся в RESUMED, **Then** snapshot обновился в течение 1 секунды.
3. **Given** WhatsApp обновлён до новой версии, **When** snapshot построен, **Then** `Capability(WHATSAPP).versionCode` равен `versionCode` целевого пакета (например 241800).

---

### User Story 2 — UI tile-editor показывает «реальные» иконки и метки (Priority: P1)

Пользователь (или admin локально) добавляет тайл через wizard. Wizard показывает только те провайдеры, которые `available=true`, с правильной иконкой и displayName.

**Why this priority**: спек 005 US-507 уже говорит «wizard показывает только доступные», но реализация ограничена булевым флагом. Этот US расширяет до иконки + читабельного имени через `IconStorage`.

**Independent Test**: открыть AddSlotWizard → wizard вызывает `CapabilityRepository.snapshot()` + `IconStorage.resolve(iconId)` → отображает grid с иконками только тех провайдеров, у которых `available=true`.

**Acceptance Scenarios**:

1. **Given** snapshot содержит WHATSAPP available + TELEGRAM unavailable, **When** wizard открыт, **Then** в picker отображается WHATSAPP с правильной иконкой через `BundledIconStorage`, TELEGRAM не отображается.
2. **Given** `Capability.iconId = "bundled:whatsapp"`, **When** UI запрашивает иконку, **Then** `IconStorage.resolve` возвращает встроенный drawable.
3. **Given** `Capability.iconId = "custom:pharmacy-uuid"` (форма данных, реализация только в спеке 007+), **When** UI запрашивает иконку в спеке 006, **Then** `BundledIconStorage` возвращает placeholder (не падает).

---

### User Story 3 — Health snapshot готов к экспорту (Priority: P1)

Health-метрики (battery, charging, connectivity, ringerVolumePercent, audioStreamMuted, lastSeen, appVersion) собираются на устройстве и доступны локальным потребителям через `HealthRepository.observe()`.

**Why this priority**: без этого спек 007 не имеет, что писать в `/health`; спек 009 admin-UI не имеет, что отображать. Также: реакции (US-5) и эскалация громкости (US-4) читают `Health` для принятия решений.

**Independent Test**: запустить debug-экран `HealthSnapshotDebugActivity` → увидеть все поля корректно для текущего состояния эмулятора.

**Acceptance Scenarios**:

1. **Given** эмулятор подключен к WiFi, заряжается, battery=80%, ringer 50%, **When** snapshot построен, **Then** `Health` соответствует.
2. **Given** эмулятор переключён в airplane mode, **When** snapshot обновился, **Then** `Health.connectivity = None`.
3. **Given** snapshot построен, **When** ничего не происходит 30 секунд, **Then** snapshot НЕ обновляется (event-driven, не polling).
4. **Given** пользователь выкрутил громкость в 0 через системные кнопки, **When** snapshot обновился, **Then** `Health.audioStreamMuted = true`, `Health.ringerVolumePercent = 0`.

---

### User Story 4 — *(deferred)*

Изначально содержал «эскалация громкости при долгом офлайне». Перенесено в спек 013 `offline-detection-and-emergency-reachability`. См. секцию Clarifications выше.

---

### User Story 5 — Крупные баннеры о проблемах связи и звука (Priority: P2)

В UI лаунчера на телефоне пожилого отображаются **постоянные крупные баннеры** (внутри лаунчера, в области его видимости — НЕ системные нотификации) о проблемах, мешающих принять звонок:

- **Баннер «Авиарежим включён»** + кнопка «Выключить авиарежим» (открывает системные настройки сети).
- **Баннер «Звук выключен»** + кнопка «Включить звук» (поднимает `STREAM_RING` до 50%).

(Баннер «Нет интернета» **перенесён в спек 013** — отсутствие интернета само по себе слишком зашумлённый сигнал.)

**Why this priority**: P2, эти баннеры — самостоятельная user-visible фича. Они работают на детектировании **локальных системных настроек** (airplane mode, ringer volume = 0), не на измерении интернета. Включение баннеров в пресеты — задача спека 010 Setup Assistant.

**Independent Test**: на эмуляторе включить airplane mode → баннер «Авиарежим включён» появляется внутри лаунчера. Выкрутить звук в 0 → баннер «Звук выключен».

**Acceptance Scenarios**:

1. **Given** airplane mode выключен, звук > 0, **When** пользователь смотрит на главный экран, **Then** ни один баннер не отображается.
2. **Given** airplane mode включён, **When** пользователь смотрит на главный экран, **Then** баннер «Авиарежим включён» отображается с большой кнопкой «Выключить авиарежим».
3. **Given** баннер «Авиарежим» виден, **When** пользователь нажимает «Выключить авиарежим», **Then** открываются системные настройки сети.
4. **Given** ringer volume = 0 (полностью замьючено), **When** пользователь смотрит на главный экран, **Then** баннер «Звук выключен» с кнопкой «Включить звук».
5. **Given** баннер «Звук» виден, **When** пользователь нажимает «Включить звук», **Then** `STREAM_RING` поднят до 50% и баннер исчезает.
6. **Given** одновременно airplane mode и звук в 0, **When** пользователь смотрит на главный экран, **Then** оба баннера видны (стек, не один поверх другого). Приоритет: «Авиарежим» сверху, потом «Звук».
7. **Given** баннер виден, **When** проблема исчезла (airplane выключили / звук подняли вручную), **Then** баннер исчезает в течение 1 секунды.

---

### Edge Cases

- **Provider package переустановлен с другой подписью** (sideload vs Play Store): versionCode меняется, available остаётся true. Snapshot обновляется через `PACKAGE_REPLACED` broadcast.
- **DataStore-проекция повреждена** (force-stop во время записи): cold-start читает поврежденный файл → fallback в empty snapshot, in-memory rebuild от первого RESUMED.
- **iOS** (когда дойдёт черёд): `Capability.versionCode` всегда null, `available` определяется через `canOpenURL` для заранее объявленных в `Info.plist` схем. Не fail — просто беднее снапшот.
- **Snapshot строится во время быстрого RESUMED** (быстрое переключение между Recents и app, RESUMED каждые 200мс): debounce 1 секунда.
- **iconId = `custom:` или `private:` в спеке 006**: `BundledIconStorage` не знает namespace, возвращает placeholder. Не падает.
- **Custom-иконка удалена родственником**: см. C10. Placeholder, никакого восстановления.
- **`audioStreamMuted` = true, но ringer volume > 0**: возможно при системном DND. `Health.audioStreamMuted` отражает effective state (звонок НЕ прозвучит), не volume value. Баннер «Звук выключен» показывается.
- **Оба баннера могут быть видны одновременно** (airplane + звук в 0): UI стекает их вертикально, авиарежим сверху. Дизайн стека — задача plan.md.

---

## 4. Requirements

### 4.1 Functional Requirements

#### Capability snapshot

- **FR-001**: System MUST вычислять `Capability` per-provider для всех `ProviderId` из спека 005 (`APP, WHATSAPP, TELEGRAM, PHONE, SMS, BROWSER, YOUTUBE, SYSTEM_SETTINGS`) с полями `{providerId, displayName, iconId, iconSha256?, available, versionCode?}`.
- **FR-002**: System MUST обновлять snapshot на `ProcessLifecycleOwner.RESUMED` И на `PACKAGE_ADDED`/`PACKAGE_REMOVED`/`PACKAGE_REPLACED` broadcast для целевых пакетов (whitelist по `ProviderId.targetPackages`).
- **FR-003**: System MUST debounce snapshot rebuild не чаще 1 раз/сек.
- **FR-004**: System MUST экспортировать snapshot через `CapabilityRepository.observe(): Flow<List<Capability>>` (port в commonMain).
- **FR-005**: System MUST персистить последний known snapshot в Preferences DataStore для cold-start (in-memory `StateFlow` инициализируется из DataStore до первого RESUMED).
- **FR-006**: Capability wire-format MUST содержать `schemaVersion: Int` поле с первого коммита, MUST иметь roundtrip-test (write → read → assertEquals) и backward-compat reader (CLAUDE.md rule 5).
- **FR-007**: System MUST полностью заменить тип `ProviderState` (введён в спеке 005 §4.1.1) на `Capability` в этом же PR. Связанные тесты спека 005 правятся как часть cleanup.

#### IconStorage port

- **FR-008**: `IconStorage` MUST быть declared as port (interface) в `commonMain` с операцией `resolve(iconId: String): IconResolution` (sealed: `Drawable(...) | Placeholder | NotFound`).
- **FR-009**: Wire-format `iconId: String` MUST использовать namespace-конвенцию: `bundled:<name>` для встроенных drawable, `custom:<uuid>` (зарезервировано для спека 007/009), `private:<uuid>` (зарезервировано для спека 011 e2e). System MUST NOT reject unknown namespaces — unknown namespace returns `Placeholder` через `BundledIconStorage`.
- **FR-010**: System MUST реализовать `BundledIconStorage` (single implementation в спеке 006), читающий drawable из APK по `bundled:<name>` mapping. Для известных провайдеров (`whatsapp`, `telegram`, `phone`, `sms`, `browser`, `youtube`, `app`, `system_settings`) brand-assets находятся в `composeResources/drawable/provider_<name>.xml` или `.png`.
- **FR-011**: Wire-format MUST содержать `iconSha256: String?` (nullable) — отпечаток для будущей кэш-инвалидации (спек 007). В спеке 006 для `bundled:` иконок поле может быть null или содержать build-time computed sha. Поле резервируется в schema независимо от текущего использования.
- **FR-012**: System MUST NOT использовать base64 в wire-format ни для иконок, ни для медиа.

#### Health snapshot

- **FR-013**: System MUST собирать `Health` снапшот с полями `{schemaVersion, batteryPercent, charging, connectivity, ringerVolumePercent, audioStreamMuted, lastSeen, appVersion}`.
- **FR-014**: `connectivity` MUST быть enum `Wifi | Mobile | None` (расширяемо в будущем, не в этой версии).
- **FR-015**: `ringerVolumePercent: Int` MUST быть в диапазоне 0-100 (нормализованное значение, не raw `STREAM_RING` units).
- **FR-016**: `audioStreamMuted: Boolean` MUST отражать effective state: true если `STREAM_RING == 0` ИЛИ system DND активен и подавляет ringer.
- **FR-017**: System MUST экспортировать snapshot через `HealthRepository.observe(): Flow<Health>` (port в commonMain).
- **FR-018**: System MUST обновлять snapshot event-driven: подписка на `ProcessLifecycleOwner.RESUMED`, `ConnectivityManager.NetworkCallback`, `AudioManager.OnAudioFocusChangeListener` + `ContentObserver` на `Settings.System.VOLUME_CHANGED`, `Settings.Global.AIRPLANE_MODE_ON` ContentObserver, `Intent.ACTION_BATTERY_CHANGED` (sticky). System MUST NOT polling.

#### *(deferred section)* Long-offline ringer escalation — moved to spec 013

FR-019..025 (эскалация громкости при долгом офлайне) перенесены в спек 013 `offline-detection-and-emergency-reachability`. См. секцию Clarifications «2026-05-09 — Late deferral».

#### Big-banner alerts (US-5)

- **FR-026**: System MUST display banner «Авиарежим включён» внутри UI лаунчера (на главном экране, в области видимости лаунчера — НЕ системные нотификации) when `Settings.Global.AIRPLANE_MODE_ON = 1`. Banner MUST contain large action button «Выключить авиарежим» that opens `Settings.ACTION_AIRPLANE_MODE_SETTINGS` (or wireless settings on devices без direct deep-link).
- **FR-027**: System MUST display banner «Звук выключен» when `Health.audioStreamMuted = true`. Banner MUST contain large action button «Включить звук» that raises `STREAM_RING` to 50% and dismisses banner.
- **FR-028**: ~~Banner «Нет интернета»~~ — **DEFERRED → spec 013**.
- **FR-029**: All banners MUST update reactively (appear/disappear within 1 second of state change).
- **FR-030**: Banners MUST NOT be dismissable by user gesture — they reflect current state, not notifications. They disappear only when state changes.
- **FR-031**: Banner stack order: «Авиарежим» > «Звук выключен». Multiple banners can be visible simultaneously.
- **FR-032**: Settings UI MUST provide toggles to enable/disable each banner type independently. Defaults per preset documented in plan.md (`simple-launcher`: all ON; `workspace`/`launcher`: all OFF).

#### Settings sync forward-compat (C7)

- **FR-033**: Banner toggles (FR-032) MUST be stored in dedicated DataStore file `settings_datastore.preferences` separate from snapshot persistence.
- **FR-034**: Settings MUST be JSON-serializable through wire-format `LauncherSettings` data class with `schemaVersion: Int`. **No actual cloud sync in spec 006** — form is reserved for спек 008.

#### Cleanup of спек 005 deferred bridge

- **FR-035**: Same PR MUST delete `migrateLegacyAction` function from [`ActionWireFormat.kt`](../../core/src/commonMain/kotlin/com/launcher/api/action/ActionWireFormat.kt), grep anchor `LEGACY-BRIDGE-EXPIRES-IN-SPEC-006`, и 5 фикстур `legacy-spec003-*.json`. Removal MUST be the first task block in `tasks.md` (T001..T0XX, точные номера определяет speckit-tasks).
- **FR-036**: Same PR MUST delete `migrateLegacyAction` call site in [`MockFlowRepository.parseAction()`](../../core/src/androidMain/kotlin/com/launcher/core/flows/MockFlowRepository.kt) — `parseAction` simplifies to direct `ActionWireFormat.decode` call (legacy detection removed).
- **FR-037**: Same PR MUST delete 5 `fixture_legacy*` test methods from [`ActionWireFormatFixtureTest.kt`](../../core/src/androidUnitTest/kotlin/com/launcher/api/action/ActionWireFormatFixtureTest.kt) and adjust `fixture_directoryListedInReadme` expectation.
- **FR-038**: Same PR MUST delete `migrateLegacyActionDeadlineSpec` constant from [`core/build.gradle.kts`](../../core/build.gradle.kts) (if defined as build-time constant).
- **FR-039**: After cleanup, [`LegacyMigrationExpiryTest`](../../core/src/androidUnitTest/kotlin/com/launcher/test/fitness/LegacyMigrationExpiryTest.kt) MUST stay green-and-noop (it auto-detects спек 006 landed via `specs/` directory scan).
- **FR-040**: All other 4 fitness tests from спек 005 (§8) MUST stay green after спек 006 lands (after `Capability` replaces `ProviderState` per FR-007).

#### Wire-format discipline (added 2026-05-09 per checklist-wire-format)

- **FR-041**: Each wire-format type (`Capability`, `Health`, `LauncherSettings`) MUST expose `SUPPORTED_SCHEMA_VERSION: Int` companion constant — single source of truth. No magic numbers scattered.
- **FR-042**: Optional fields use kotlinx.serialization `@Serializable` defaults (e.g. `val versionCode: Long? = null`); deserializer handles missing fields without exception.
- **FR-043**: Reader for `Capability`/`Health`/`LauncherSettings` MUST NOT throw on `schemaVersion > SUPPORTED_SCHEMA_VERSION`. Object is parsed best-effort; consumers may downgrade behaviour. Aligns with spec 005 Clarification C1.
- **FR-044**: Unknown enum values in `Health.connectivity` deserialize to `None` (safe fallback). Same policy applies to any future enum introduced in `Capability`/`Health`/`LauncherSettings`.
- **FR-045**: Test fixtures stored as files in `core/src/commonTest/resources/fixtures/capability-wire-format/`, `…/health-wire-format/`, `…/launcher-settings/`. One file per scenario. Pattern matches spec 005 §4.1.5 / Clarification C4.
- **FR-046**: DataStore Preferences keys MUST be namespaced as `com.launcher.<feature>.<key>` (e.g. `com.launcher.capability.snapshot_v1`, `com.launcher.health.snapshot_v1`, `com.launcher.settings.banners_v1`). The `_v1` suffix anticipates future schema bumps that may want fresh keys.

#### Domain isolation discipline (added 2026-05-09 per checklist-domain-isolation)

- **FR-047**: All Android system types (`android.*`, `androidx.*`, `Intent`, `Uri`, `Context`, `Bundle`, `LifecycleOwner`, `PackageManager`, `ConnectivityManager`, `AudioManager`, `ContentObserver`) MUST stay in `androidMain`. `commonMain` exposes pure Kotlin ports + domain types only. Per CLAUDE.md rule 1.
- **FR-048**: Each port (`CapabilityRepository`, `HealthRepository`, `IconStorage`) MUST have a fake adapter (`FakeCapabilityRepository`, `FakeHealthRepository`, `FakeIconStorage`) in `commonTest` (or shared test artifact). Used by domain-level tests and dev/debug builds. Per CLAUDE.md rule 6 (mock-first).

#### Failure recovery discipline (added 2026-05-09 per checklist-failure-recovery)

- **FR-049**: System failures (broadcast missed, callback not fired, system API throws) MUST not crash app. Snapshot uses last-known DataStore projection; missing data fields default to documented sentinel (e.g. `connectivity = None`, `batteryPercent = 0`). Periodic rebuild on next `RESUMED` recovers from missed events.
- **FR-050**: Banner action button failures (e.g. `setStreamVolume` rejected by DND, `Settings` intent unavailable on OEM) MUST surface to user via toast/snackbar with localised message (e.g. «Не удалось — проверьте настройки звука»). Action button MUST remain enabled for retry. If both primary action and OS fallback fail, surface «функция недоступна на этом устройстве», banner stays visible (state has not changed).
- **FR-051**: `LauncherSettings` deserialization failure (corrupted DataStore file) MUST fallback to preset-defaults (per FR-032), not crash. Single recovery write rebuilds the file with defaults.
- **FR-052**: Each recovery path (DataStore corruption, missing fixture drawable, banner action failure, system callback timeout) MUST emit structured log event with category (e.g. `corruption`, `missing_resource`, `system_api_failure`, `user_action_failed`) and **zero PII**. Categories enable rate measurement в спеке 007 (Firebase telemetry).

#### Platform / permissions discipline (added 2026-05-09 per checklist-permissions-platform)

- **FR-053**: AndroidManifest MUST declare `<queries>` element with explicit `<package android:name="…">` entries for each known provider per spec 005 ProviderId mapping (`com.whatsapp`, `com.whatsapp.w4b`, `org.telegram.messenger`, `org.telegram.plus`, `com.google.android.youtube`, etc. — full list determined by `ProviderId.targetPackages`). **CRITICAL** — without this, `PackageManager` queries return empty on Android 11+ and `Capability.available` is always false. MUST NOT use `QUERY_ALL_PACKAGES` (Play policy violation).
- **FR-054**: In same PR `docs/compliance/permissions-and-resource-budget.md` MUST be updated с delta для спека 006: full `<queries>` entries list, `ACCESS_NETWORK_STATE` declaration verified, zero new runtime permissions / foreground services confirmed, manifest delta documented.

#### Privacy / data classification (added 2026-05-09 per checklist-security)

- **FR-056**: `Capability`, `Health`, `LauncherSettings` are classified as **non-PII device telemetry** (no name, phone, email, contact ref, location, biometric, account identifier). Stored in app-private DataStore with default Android FBE encryption. No additional encryption required for спека 006. Privacy classification revisited в спеке 007 при cloud export для admin transparency / consent.
- **FR-057**: Android Auto Backup rules (`backup_rules.xml` / `data_extraction_rules.xml`) MUST exclude `capability_snapshot_json` and `health_snapshot_json` (per-device transient state — irrelevant on new device). MUST include `settings_datastore.preferences` (user preferences worth restoring). XML file updates documented in plan.md.

#### Senior-safe UI metrics (added 2026-05-09 per checklist-elderly-friendly)

- **FR-058**: Banner UI (FR-026, FR-027) MUST satisfy senior-safe metrics from ADR-005 + Article VIII §7 senior-safe override:
  - body text ≥ 18sp,
  - action button label ≥ 18sp,
  - button tap area ≥ 56dp height AND ≥ 56dp width,
  - spacing ≥ 16dp between interactive elements,
  - banner background to text contrast ≥ 4.5:1,
  - button to banner contrast ≥ 4.5:1,
  - text accompanied by recognisable icon (e.g. airplane icon for FR-026, speaker icon for FR-027) — no reliance on colour alone,
  - banner appears with fade transition ≤ 200ms; respects `Settings.Global.TRANSITION_ANIMATION_SCALE` for reduced-motion accessibility (instant appear if scale = 0).

  Material 3 component basis: `androidx.compose.material3.Card` with `Button` for action.

#### Localization (added 2026-05-09 per checklist-localization)

- **FR-059**: All user-facing strings (banner texts FR-026/027, toast messages FR-050, settings labels FR-032) MUST be externalised to `composeResources/values/strings_spec006.xml` (or shared `strings.xml` per project convention). No hardcoded strings in Composables. ru-RU + en-US locales obligatory per [ADR-004](../../docs/adr/ADR-004-localization-and-global-readiness.md) baseline.

#### Negative requirements (что НЕ делает спек 006)

- **NFR-N01**: System MUST NOT write Capability/Health to network. Firestore export — задача спека 007.
- **NFR-N02**: System MUST NOT bypass DND, MUST NOT request `CALL_PHONE` / `ACCESS_NOTIFICATION_POLICY` / `SYSTEM_ALERT_WINDOW` / `SEND_SMS`.
- **NFR-N03**: System MUST NOT run a Foreground Service for any feature in this spec. System MUST NOT use WorkManager in спеке 006 — отложенные задачи и эскалация перенесены в спек 013.
- **NFR-N04**: System MUST NOT sync Capability/Health from cloud to device (write-only direction, see C7).
- **NFR-N05**: System MUST NOT polling — no periodic timers, no scheduled reads, no «check every N seconds». All updates event-driven (broadcasts, callbacks, lifecycle).
- **NFR-N06**: System MUST NOT attempt USSD requests for balance check (deferred to спек 012). MUST NOT use `TelephonyManager.sendUssdRequest` or similar.
- **NFR-N07**: System MUST NOT send SMS or any other off-device communication on behalf of user (no auto-message-to-relative).
- **NFR-N08**: Кэш-механика для облачных иконок (LRU, sha-инвалидация, pinning) MUST NOT be implemented in спек 006. Wire-format reserves `iconSha256` field (FR-011) and namespace convention (FR-009), but no cache code.
- **NFR-N09**: History/recovery of deleted custom icons MUST NOT be implemented. Deletion is permanent (C10).

### 4.2 Non-Functional Requirements

- **NFR-001**: Capability snapshot rebuild ≤ 50 ms on medium-tier device (Pixel 4a class).
- **NFR-002**: Health snapshot rebuild ≤ 20 ms.
- **NFR-003**: Snapshot rebuild MUST NOT block main thread (background dispatcher).
- **NFR-004**: Cold-start contribution from spec 006 init code ≤ 20 ms (DataStore lazy read, no eager network/disk).
- **NFR-005**: Battery cost ≤ 0.1% per day on medium-tier device. (Спек 006 не имеет periodic background work; все обновления event-driven.)
- **NFR-006**: Memory: snapshot in-memory footprint ≤ 5 KB (capability + health combined). DataStore file size ≤ 10 KB.
- **NFR-007**: Banner UI MUST render within 16 ms (one frame at 60Hz) — no jank on appearance/disappearance.
- **NFR-008**: Zero new Android runtime permissions beyond `ACCESS_NETWORK_STATE` (already required for connectivity observation, will be requested at first observe). Specifically: NO `ACCESS_NOTIFICATION_POLICY`, NO `CALL_PHONE`, NO `SEND_SMS`, NO `MODIFY_AUDIO_SETTINGS` (для FR-027 «Включить звук» достаточно `AudioManager.setStreamVolume` без runtime permission).
- **NFR-009**: APK size delta ≤ 100 KB (provider brand drawable assets) per ADR-005 budget.
- **NFR-010**: No persistent network connections (no WebSocket, no socket-keep-alive).
- **NFR-011**: ~~WorkManager escalation task body execution time~~ — **deferred → spec 013**. Спек 006 не использует WorkManager.
- **NFR-012**: All BroadcastReceiver `onReceive` bodies and ContentObserver `onChange` callbacks MUST complete within 10 ms. Heavier work (snapshot rebuild, DataStore write) MUST delegate to coroutine on background dispatcher (default `Dispatchers.Default` + IO-bound parts on `Dispatchers.IO`).

### 4.3 Key Entities

- **Capability** (domain): per-provider snapshot. `{schemaVersion, providerId, displayName, iconId, iconSha256?, available, versionCode?}`.
- **Health** (domain): per-device snapshot. `{schemaVersion, batteryPercent, charging, connectivity, ringerVolumePercent, audioStreamMuted, lastSeen, appVersion}`.
- **Connectivity** (domain enum): `Wifi | Mobile | None`.
- **CapabilityRepository** (port, commonMain): `observe(): Flow<List<Capability>>`, `snapshot(): List<Capability>`.
- **HealthRepository** (port, commonMain): `observe(): Flow<Health>`, `snapshot(): Health`.
- **IconStorage** (port, commonMain): `resolve(iconId: String): IconResolution`. Sealed `IconResolution = Drawable | Placeholder | NotFound`.
- **BundledIconStorage** (Android adapter): single implementation в спеке 006. Реализует resolve для namespace `bundled:`, возвращает Placeholder для остальных.
- **AlertBannerStateProvider** (domain): observable `Flow<Set<AlertBannerType>>` derived from Health + Settings.Global. UI рендерит банеры на его основе. В спеке 006 типы баннеров: `Airplane`, `Mute`. Тип `OfflineNoInternet` зарезервирован для спека 013.
- **LauncherSettings** (domain): wire-format toggles `{schemaVersion, banners: {airplane, mute}}`. Toggle `raiseRingerOnLongOffline` и `banners.offline` переедут в спек 013 — добавятся как новые поля без миграции существующих читателей.
- **CapabilityProjection / HealthProjection** (DataStore Serializer): persisted last-known snapshots для cold-start.

---

## 5. Success Criteria

### Measurable Outcomes

- **SC-001**: На эмуляторе с/без WhatsApp `Capability(WHATSAPP).available` корректен в 100% запусков.
- **SC-002**: После `pm install/uninstall` целевого пакета snapshot обновляется ≤ 1 секунды.
- **SC-003**: `Capability`, `Health`, `LauncherSettings` wire-formats имеют roundtrip-tests и backward-compat readers для `schemaVersion=1`. CLAUDE.md rule 5 satisfied.
- **SC-004**: 4 fitness-теста спека 005 остаются зелёными после слития ветки 006 (после замены `ProviderState` → `Capability`).
- **SC-005**: `LegacyMigrationExpiryTest` после удаления бриджа становится **green-and-noop** — оба `@Test` метода проходят.
- **SC-006**: 0 новых runtime permissions кроме `ACCESS_NETWORK_STATE`.
- **SC-007**: ~~эскалация громкости~~ — **deferred → spec 013**.
- **SC-008**: Banner UI обновляется ≤ 1 секунды на изменение состояния (airplane on/off, volume to 0).
- **SC-009**: Battery measurement: при WiFi online + bluetooth off + screen off, battery delta from spec 006 background work ≤ 0.1% за 24 часа (Battery Historian trace).
- **SC-010**: Snapshot rebuild не блокирует main thread — Compose recomposition остаётся ≤ 16 ms на таск-свитчинге.
- **SC-011**: Debounce 1s verified: при быстрых RESUMED событиях (200ms интервал) snapshot rebuild calls ≤ 1 per second.
- **SC-012**: No base64 in wire-format: grep-fitness test asserts отсутствие `Base64`/`base64` в JSON outputs `Capability`/`Health`/`LauncherSettings`.
- **SC-013**: APK size delta from спек 006 ≤ 100 KB (measured `release` variant).
- **SC-014**: Cold-start delta ≤ 20 ms (macrobenchmark before/after).
- **SC-015**: Forward-compat test: each wire-format reader (`Capability`, `Health`, `LauncherSettings`) handles fixture with `schemaVersion: 999` + extra unknown fields without crash. Test asserts: parsing succeeds, known fields populate correctly, unknown fields ignored.

---

## 6. Assumptions

- Спек 005 завершён и смерджен (Action wire-format, ProviderRegistry, AppIndex доступны).
- Compose Multiplatform stack из спека 004 активен; debug-экраны строятся на CMP.
- DataStore Preferences подключён или будет подключён в этом спеке (CMP-friendly).
- WorkManager в спеке 006 **не подключается** — отложенные задачи не нужны (эскалация офлайна вынесена в спек 013).
- iOS-implementation отложена; только Android adapter в этом спеке.
- Брендовые иконки провайдеров (WhatsApp, Telegram, YouTube) — vector drawable или PNG в `composeResources/drawable/`. Лицензионные права на бренд-ассеты считаются проверенными вне этого спека.

---

## 7. Out of scope (явно)

- Реальная отправка Capability/Health в Firestore (спек 007).
- Bidirectional config sync admin ↔ OLD (спек 008).
- Admin UI для просмотра health (спек 009).
- Setup Assistant UI (спек 010 — потребитель этого snapshot'а).
- Background service / persistent notification — категорически нет.
- DPC / Device Owner provisioning.
- Periodic ping каждые N минут — нет, только event-driven + WorkManager отложенные one-shot.
- Фотографии контактов / приватные медиа — спек 011 с e2e-шифрованием.
- iOS adapter.
- **Эскалация громкости при долгом офлайне — спек 013** (вынесено 2026-05-09).
- **Баннер «Нет интернета» — спек 013** (вынесено 2026-05-09).
- **Любые реакции на отсутствие интернета — спек 013**. В спеке 006 `Health.connectivity` это **только данные**, без логики.
- USSD-проверка баланса оператора — спек 012.
- Автоматическая отправка SMS родственнику — спек 012 / спек 013 (TBD).
- Кэш-механика для облачных иконок (LRU, sha-инвалидация, pinning) — спек 007.
- История удалённых иконок / восстановление — не реализуется (C10).
- E2E шифрование для встроенных и кастомных иконок — не нужно (они не приватные); отдельная задача в спеке 011 для `private:` namespace.

---

## 8. References

- [CLAUDE.md](../../CLAUDE.md) — rules 1, 2, 3, 5, 6.
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md) — Article XVI gates обязательны для plan.md, Article IX (event-driven, battery), Article XI (anti-bloat).
- [docs/governance/document-map.md](../../docs/governance/document-map.md) — где какие документы живут.
- [docs/product/roadmap.md](../../docs/product/roadmap.md) — позиция спека 006, новые спеки 011/012, Firestore schema (для спека 007).
- [docs/compliance/permissions-and-resource-budget.md](../../docs/compliance/permissions-and-resource-budget.md) — обновить при добавлении `ACCESS_NETWORK_STATE`.
- [специка 005 spec.md](../005-action-architecture-v2/spec.md) — Clarification C5 (бридж expiry), §8 (fitness functions), §4.1.1 (`ProviderState` который заменяется).
- [`LegacyMigrationExpiryTest`](../../core/src/androidUnitTest/kotlin/com/launcher/test/fitness/LegacyMigrationExpiryTest.kt) — auto-trigger для cleanup.

---

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Спек вводит два локальных snapshot'а с `schemaVersion: 1` (`Capability` per-provider, `Health` per-device), один port `IconStorage` с реализацией `BundledIconStorage`, и два crisis-баннера в UI лаунчера («Авиарежим включён», «Звук выключен»). Сетевая отправка отсутствует (NFR-N01). В том же PR удаляется бридж `migrateLegacyAction` из спека 005 + 5 фикстур + grep-anchor `LEGACY-BRIDGE-EXPIRES-IN-SPEC-006`.

**Конкретика, которую стоит запомнить:**
- 5 user stories: US-1/2/3 (snapshots) — P1; US-4 — **deferred → spec 013**; US-5 (banners) — P2 (только 2 баннера, не 3).
- 4 wire-format типа: `Capability {providerId, displayName, iconId, iconSha256?, available, versionCode?}`, `Health {batteryPercent, charging, connectivity, ringerVolumePercent, audioStreamMuted, lastSeen, appVersion}`, `LauncherSettings {banners: {airplane, mute}}`, `iconId` namespace string (`bundled:` / `custom:` / `private:`).
- 3 порта в `commonMain`: `CapabilityRepository`, `HealthRepository`, `IconStorage` — каждый требует fake adapter в `commonTest` (FR-048).
- 59 функциональных требований (FR-001..059), 12 нефункциональных (NFR-001..012), 9 negative-NFR (NFR-N01..N09), 15 success criteria.
- Hard guarantees: NFR-005 «≤ 0.1% батареи в день», NFR-008 «zero new runtime permissions кроме `ACCESS_NETWORK_STATE`», NFR-N05 «no polling», NFR-N03 «no Foreground Service / no WorkManager в спеке 006».
- 10 Clarifications зафиксированы; C2/C3/C4 помечены **DEFERRED → spec 013** после поздней правки 2026-05-09.
- Cleanup-задачи: FR-035..040 (удаление `migrateLegacyAction` + 5 фикстур + 5 fixture-тестов + build-script константы; `LegacyMigrationExpiryTest` становится green-and-noop).
- ProviderState из спека 005 **полностью заменяется** на Capability (FR-007) — breaking change для 4 fitness-тестов спека 005, корректируются в этом же PR (FR-040).
- Roadmap получил 3 новых спека: 011 (e2e-encrypted contacts media), 012 (USSD balance check), 013 (offline detection — вынесено из 006).

**На что смотреть с осторожностью:**
- **`<queries>` declaration (FR-053) — критично**: без него на Android 11+ `Capability.available` всегда `false` для всех провайдеров. Базовая фича сломана.
- **Wire-format намеспейс `iconId` (`bundled:` / `custom:` / `private:`) и поле `iconSha256` — one-way door** (CLAUDE.md rule 5). Менять потом дорого: миграция всех бабушкиных карточек.
- **DataStore namespacing convention `com.launcher.<feature>.<key>_v1` (FR-046)** — суффикс `_v1` готовит почву для будущих schema-bump'ов с новыми ключами вместо миграции существующих.
- **Backup rules (FR-057)**: `capability_snapshot_json` и `health_snapshot_json` ДОЛЖНЫ быть исключены из Auto Backup (per-device transient), `settings_datastore.preferences` — включён. Перепутать = синхронизация старых снапшотов на новый телефон с другими установленными приложениями.
- **Реакции на отсутствие интернета НЕ В ЭТОМ СПЕКЕ** — все жалобы «не работает баннер про интернет» / «не растёт громкость» относятся к спеку 013, не к 006.

# Feature Specification: Admin Mode Flows

**Feature Branch**: `009-admin-mode-flows`
**Created**: 2026-05-15
**Status**: Draft (rev. 1 — pre-specify scope discovery 2026-05-15)
**Input**: roadmap §Spec 009 ([docs/product/roadmap.md:192](../../docs/product/roadmap.md#L192)) + pre-specify mentor session 2026-05-15 — полноценный admin-режим редактирования Managed-телефона: layout editor, phone health monitoring, contacts с телефона админа (включая VCard share-intent для WhatsApp/Telegram/Viber), история конфигов с откатом, open-app плитки c Play Store fallback.

---

## Контекст и цель спека

Спек 7 (`pairing-and-firebase-channel`) установил связь admin↔Managed через QR + Firestore. Спек 8 (`bidirectional-config-sync`) дал **мотор** редактирования — wire format `/config`, save/push/merge UI, optimistic concurrency. На момент завершения 8 в admin-UI — заглушки (`AdminDevicesFragment` со списком mock-устройств, без реального редактирования, без отображения health).

Спек 9 даёт **кузов** на этот мотор: реальный список Managed, редактор раскладки, мониторинг здоровья, добавление контактов, история с откатом. После 9 admin может **полноценно** настраивать раскладку бабушкиного телефона удалённо, без её участия.

**Ключевой архитектурный принцип** (зафиксирован в pre-specify session): «одно приложение, разные конфиги» — admin-устройство и Managed-устройство запускают **то же** приложение, но с разными конфигами; редактор раскладки = тот же рендерер плиток, только в режиме редактирования.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Admin редактирует раскладку Managed-телефона удалённо (Priority: P1)

Admin открывает приложение, видит список своих paired Managed-устройств, тапает по одному (например, «Бабушка Honor»), попадает в редактор её раскладки, делает изменения (добавляет плитку «Маша внучка», убирает ненужное, переставляет местами), нажимает «Опубликовать» — изменения улетают на сервер, бабушка через несколько секунд видит обновлённую раскладку.

**Why this priority**: основная ценность спека — без P1 admin не может реально влиять на бабушкин телефон. Все остальные user stories расширяют или дополняют этот сценарий.

**Independent Test**: pair admin↔Managed (через спек 7), admin меняет один параметр в раскладке Managed через UI спека 9, push → Managed применяет → раскладка обновлена. Тестируется end-to-end без spec 9 пунктов 2/4/5/6.

**Acceptance Scenarios**:

1. **Given** admin и Managed уже paired (спек 7), Managed online, никто параллельно не редактирует, **When** admin открывает приложение → видит список → тапает на Managed → редактор раскладки открывается с актуальным конфигом (pulled с сервера или из локального кэша Firestore SDK при отсутствии сети), **Then** admin видит точно ту же логическую раскладку, что у бабушки на её устройстве (с поправкой на размер своего экрана; см. assumption про density mismatch).
2. **Given** admin в редакторе, **When** делает long-press на плитку → перетаскивает на новую позицию → отпускает, **Then** изменение применяется к локальному draft'у, плитка визуально на новой позиции, баннер «есть несохранённые изменения» появляется.
3. **Given** admin в редакторе с локальными изменениями, **When** нажимает «Опубликовать», **Then** push в `/config/current` проходит через flow спека 8 (FR-013 conflict-check, FR-022 apply triggers), при успехе — Managed применяет, `/state.appliedConfigUpdatedAt` обновляется, admin UI показывает «применено».
4. **Given** admin редактирует, параллельно другой editor (например, бабушка через Settings или admin с планшета) пушит в тот же `/config`, **When** admin нажимает «Опубликовать» с устаревшим `clientSnapshotUpdatedAt`, **Then** показывается **Merge UI спека 8 FR-050** — единый, без senior-safe-варианта; admin разруливает конфликт.
5. **Given** admin вышел на главный экран не нажав «Опубликовать», **When** возвращается в редактор, **Then** локальный draft восстанавливается, баннер «есть несохранённые изменения» виден.

---

### User Story 2 — Admin видит здоровье каждого Managed-устройства (Priority: P1)

Admin открывает список Managed → у каждого устройства видна компактная сводка: процент батареи (с цветовым индикатором при низком), интернет/нет, выключенный звонок (важное!), как давно бабушка заходила в приложение. Если ситуация критическая (3% батарея, сутки без выхода, выключен звонок) — соответствующий индикатор подсвечен красным, admin понимает, что нужно среагировать. На экране редактора одного Managed — расширенная health-сводка с полными значениями.

**Why this priority**: без этой P1 admin узнаёт об опасной ситуации (бабушка отключила звонок и неделю не выходит) только при следующем визите. Для целевой аудитории (пожилые родственники) — критичный канал.

**Independent Test**: paired Managed (спек 7) шлёт `Health` snapshot в `/links/{linkId}/health` (спек 6/7). Admin открывает список → видит индикаторы. Симуляция: на Managed выключаем звонок → через ≤30 сек у admin индикатор «звонок выключен» становится жёлтым.

**Acceptance Scenarios**:

1. **Given** Managed online, `Health` snapshot свежий, **When** admin открывает список Managed-устройств, **Then** у каждого Managed видны 4 компактных индикатора: battery (иконка + цвет по severity), connectivity (иконка), audio (иконка с предупреждением если muted), lastSeen («2 мин назад»).
2. **Given** Managed online с `batteryPercent = 3, audioStreamMuted = true`, **When** admin тапает на этого Managed → открывается редактор раскладки, **Then** сверху редактора видна расширенная health-сводка: «🔴 Заряд: 3% (критично)», «🔴 Звонок: выключен!», «🟢 Wi-Fi», «2 мин назад».
3. **Given** admin на экране редактора Managed, **When** значение `Health.batteryPercent` меняется с 30% до 3% (через Firestore listener), **Then** индикатор перерисовывается в течение ≤ 5 сек, цвет меняется с Info на Critical, **локальный** event `PhoneHealthCriticalEvent` эмитится (без подписчика в этой версии — см. server-roadmap SRV-MONITOR-001).
4. **Given** Managed офлайн (последний snapshot был 3 часа назад с `connectivity = Wifi`), **When** admin смотрит health, **Then** показывается «3 часа назад (последняя известная)», `lastSeen` индикатор — Warning.

---

### User Story 3 — Admin добавляет контакт с собственного телефона в раскладку Managed (Priority: P2)

Admin в редакторе раскладки Managed нажимает «+ контакт» → срабатывает запрос permission `READ_CONTACTS` (с rationale-экраном «зачем нам твои контакты»), открывается стандартный Android Contacts picker, admin выбирает «Маша» → создаётся плитка «Маша» в текущей вкладке flow раскладки бабушки. Бабушка через несколько секунд видит новую плитку. На её телефоне `READ_CONTACTS` **не нужен** — имя и номер целиком в её `/config`.

**Why this priority**: без этой story раскладка бабушки остаётся mock-контактами. Это **самая частая** real-world нужда (родственники добавляют контакты). P2 потому что P1 редактор раскладки уже работает с уже-добавленными контактами.

**Independent Test**: paired Managed, admin грантит `READ_CONTACTS`, выбирает контакт через picker → contact записывается в `/config.contacts[]`, slot создаётся в текущей flow → Managed применяет → плитка с именем контакта появилась.

**Acceptance Scenarios**:

1. **Given** admin в редакторе Managed, `READ_CONTACTS` не дан, **When** admin нажимает «+ контакт», **Then** показывается rationale-экран «Зачем нужно разрешение читать контакты», после согласия — Android picker открывается.
2. **Given** picker открыт, **When** admin выбирает контакт с одним номером, **Then** создаётся объект `Contact { id = новый UUID, displayName = name из picker'a, phoneNumber = number, photoRef = null }` и добавляется в `/config.contacts[]` (если deduplicate FR не сработал); создаётся `Slot { kind = Call, args = {contactId} }` в активной flow.
3. **Given** picker открыт, **When** admin выбирает контакт с двумя номерами, **Then** показывается диалог «Какой номер использовать: мобильный +X / домашний +Y», admin выбирает, дальше — как в Scenario 2.
4. **Given** в `/config.contacts[]` уже есть контакт с `phoneNumber = +79161234567`, **When** admin добавляет контакт с тем же номером (любое имя), **Then** существующий Contact переиспользуется (новый Slot ссылается на его `id`), новый Contact не создаётся.

---

### User Story 4 — Контакт из мессенджера попадает в раскладку через системный share (Priority: P2)

Admin сидит в WhatsApp (или Telegram / Viber / системных Contacts) → находит чат с Машей → нажимает «Поделиться контактом» → в системном share sheet выбирает наше приложение → попадает на экран «Добавить контакт в раскладку: какого Managed?» → выбирает «Бабушку Honor» → переходит в её редактор с предзаполненной формой новой плитки → подтверждает → push.

**Why this priority**: без этой story admin вынужден переходить в системные контакты и искать там Машу, даже если только что разговаривал с ней в Telegram. Этот flow — естественный UX «поделиться → добавить». Покрывает WhatsApp/Telegram/Viber через стандартный VCard intent filter. Закрытые мессенджеры (LINE/WeChat/KakaoTalk) — отдельный будущий спек.

**Independent Test**: установить наше приложение, в WhatsApp share contact → выбрать наше → flow добавления в Managed раскладку отрабатывает; контакт оказывается в `/config.contacts[]` через 30 сек.

**Acceptance Scenarios**:

1. **Given** наше приложение установлено и зарегистрировало intent-filter на `ACTION_SEND` + MIME `text/x-vcard`, **When** admin в WhatsApp нажимает «Поделиться контактом Машы», **Then** наше приложение присутствует в системном share sheet с правильной иконкой.
2. **Given** admin выбирает наше приложение из share sheet, VCard содержит FN + 1 TEL, **When** наше приложение получает intent, **Then** показывается экран «Добавить контакт: Маша / +79161234567» с выпадающим списком managed-устройств (preselect — если один).
3. **Given** admin выбирает Managed «Бабушка Honor» → подтверждает → переходит в её редактор, **Then** в активной flow появляется новая плитка-черновик «Маша», admin её позиционирует и нажимает «Опубликовать».
4. **Given** admin получает VCard без поля TEL (например, контакт LINE без публичного номера), **When** наше приложение пытается распарсить, **Then** показывается «Контакт без номера телефона не может быть добавлен в текущей версии; для мессенджеров LINE / WeChat / KakaoTalk — поддержка появится позже».

---

### User Story 5 — Admin откатывает раскладку к предыдущей версии (Priority: P2)

Admin случайно удалил важную плитку или поломал раскладку. Открывает редактор бабушкиного Managed → меню «История» → видит список последних 10 версий с датой и кем сделана → тапает на нужную («2 дня назад от моего планшета») → открывается **read-only** preview этой версии → нажимает «Откатить к этой версии» → подтверждение → старая версия пушится как новая current, бабушка видит знакомую раскладку.

**Why this priority**: без отката одна ошибка admin'a → бабушка в панике с поломанным телефоном, помочь некому. Это **страховка**, которая делает редактор «безопасным для использования».

**Independent Test**: pair, admin делает 3 push'а с разным содержимым (в режиме редактирования через US-1), затем открывает «История» → видит 2 старые версии (третья — current) → тапает старейшую → preview → откатить → новый push с старым содержимым → Managed применяет.

**Acceptance Scenarios**:

1. **Given** admin сделал ≥ 2 push'а в `/config/current` через US-1, **When** admin открывает «История» в редакторе, **Then** список snapshot'ов отсортирован `recordedAt DESC`, current помечен бэйджем «текущая», старые — кликабельны.
2. **Given** admin тапает на snapshot 3 дня назад, **When** открывается preview, **Then** показывается **read-only** редактор раскладки с содержимым этого snapshot (не текущим), кнопка «Откатить к этой версии» внизу.
3. **Given** admin нажимает «Откатить» → диалог подтверждения → подтверждает, **Then** содержимое выбранного snapshot пушится в `/config/current` через стандартный flow спека 8 (с conflict-check; если кто-то параллельно правит — merge UI).
4. **Given** Managed (бабушка через 7-tap+пароль) открыла Settings → редактор своего конфига → «История» → откат, **When** откат проходит, **Then** на сервере новая current = старый snapshot, admin (на своём устройстве) при следующем pull видит изменение.

---

### User Story 6 — Managed (через Settings 7-tap+пароль) попадает в тот же редактор (Priority: P3)

Бабушка вошла через 7 тапов + пароль в Settings → опция «Редактировать раскладку» → попадает в **тот же** редактор, что у admin'а, только редактирует **свой** `/config`. Может удалять плитки, переставлять, добавлять контакты (через свой `READ_CONTACTS` если есть), откатывать историю.

**Why this priority**: симметрия со спеком 8 («Merge UI единый для всех editor'ов, без senior-safe-варианта» — FR-050). Бабушка, которая прошла 7-tap+пароль, считается editor'ом, не senior-user'ом. P3 потому что использование редко (типичный сценарий — admin делает удалённо), но архитектурно обязательно для симметрии.

**Independent Test**: на Managed-устройстве пройти 7-tap+пароль в Settings → войти в редактор → внести изменение → push. Кодовая база редактора — общая с US-1.

**Acceptance Scenarios**:

1. **Given** Managed-устройство в HOME-режиме, **When** пользователь делает 7 тапов на логотипе + вводит пароль → попадает в Settings → нажимает «Редактировать раскладку», **Then** открывается **тот же** редактор, что у admin'а в US-1, но с автоматически выбранным **своим** `/config` (не Managed list).
2. **Given** Managed-editor в редакторе, **When** делает изменение и нажимает «Опубликовать», **Then** push идёт через тот же flow спека 8, admin (на своём устройстве) видит изменение.
3. **Given** Managed-editor параллельно с admin'ом правит `/config`, **When** обе стороны делают push, **Then** второй editor получает **тот же самый** Merge UI (FR-050 спека 8), без упрощений.

---

### User Story 7 — Admin создаёт плитку «открыть приложение» с Play Store fallback (Priority: P3)

Admin в редакторе плитки выбирает «Тип: открыть приложение» → выбирает «Яндекс Карты» (вводит package name `ru.yandex.yandexnavi` или выбирает из системного списка установленных приложений на админ-телефоне) → пушит. Бабушка тапает плитку:
- если приложение установлено — оно открывается;
- если нет — открывается Google Play страница этого приложения, бабушка может установить.

**Why this priority**: упрощает «как открыть бабушке Яндекс Карты». Заменяет идею «push команда на установку» (`/commands/{cmdId}`) на **штатную плитку** через `Action open_app`. P3 потому что технически — это просто расширение редактора плитки (US-1) на kind `OpenApp`.

**Independent Test**: admin создаёт плитку с `Slot { kind = OpenApp, args = {packageName = "ru.yandex.yandexnavi"} }`, пушит → Managed применяет → бабушка тапает → если установлено — открывается; если нет — открывается Play Store страница.

**Acceptance Scenarios**:

1. **Given** admin в форме редактирования плитки, **When** выбирает «Тип: открыть приложение» → выбирает «Яндекс Карты» из списка / вводит package, **Then** создаётся `Slot { kind = OpenApp, args = {packageName} }` в текущей flow.
2. **Given** бабушка тапает плитку «Яндекс Карты», установлено на Managed, **When** dispatcher выполняет Action, **Then** запускается intent `ACTION_VIEW` / `LAUNCHER` для этого packageName, Яндекс Карты открываются.
3. **Given** бабушка тапает плитку «Яндекс Карты», не установлено на Managed, **When** dispatcher не находит приложение, **Then** открывается intent на `market://details?id=ru.yandex.yandexnavi` (Play Store страница), fallback на `https://play.google.com/store/apps/details?id=...` если Play Store недоступен.

---

### Edge Cases

- **Admin офлайн, нет локального кэша конфига выбранного Managed**: показывается «Нет соединения и нет локальной копии — попробуйте позже». Никаких действий, требующих сервера.
- **Admin офлайн, есть локальный кэш**: редактор открывается с кэшированной версией, баннер «Офлайн, изменения применятся при появлении сети». При появлении сети — pull + автоматический merge через FR-013 спека 8.
- **Push в `/config/current` падает после write history**: history содержит ненужный snapshot. **Принимаем** (race condition rare loss; см. server-roadmap SRV-CONFIG-001 для миграции).
- **Snapshot в history имеет старую `schemaVersion`, транзформер не написан**: показывается «Эта версия несовместима с текущим приложением, откат невозможен». Snapshot остаётся в списке, но кнопка отката заблокирована (см. TODO-ARCH-015).
- **Admin удалил все flow** (пустой `flows: []`): на Managed применяется пустая раскладка, бабушка видит «нет плиток». UI показывает предупреждение «вы удалили все вкладки, бабушка увидит пустой экран; продолжить?».
- **Контакт удалён из системной адресной книги admin'а после добавления в раскладку**: контакт продолжает работать (имя/номер в `/config`). Drift detection — `TODO-ARCH-013` в backlog.
- **History достигла 10 snapshot'ов, push 11**: housekeeping удаляет старейший snapshot **в той же сессии** (не batched, race condition приемлем).
- **VCard intent от вредоносного приложения с гигантским payload**: парсер VCard ограничивает размер 10 KB, отвергает большие («не похоже на контакт»).
- **Managed-устройство удалено (unpair)**: исчезает из списка admin'а. Локально-кэшированные snapshot'ы для удалённого Managed — удаляются при следующем launch admin-приложения.
- **Парные edit'ы плитки и flow** (admin перемещает плитку в flow, который другой editor параллельно удалил): merge UI спека 8 покрывает; для admin-режима — visible diff показывается через тот же FR-050.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Managed device list and entry point

- **FR-001**: App MUST в admin-режиме показывать список paired Managed-устройств с health-сводкой у каждого (4 компактных индикатора: battery, connectivity, audio-mute, lastSeen). Тап → переход в редактор одного Managed.
- **FR-002**: При открытии редактора Managed app MUST сделать pull `/config/current` из Firestore; при отсутствии сети — fallback на локальный кэш (Firestore offline persistence — поведение наследуется из SDK).
- **FR-003**: App MUST показывать сверху редактора баннер «Редактирую: <displayName Managed> / экран ~<size>" / <tiles per row>» (decorative, без точного pixel-масштаба).
- **FR-004**: Список Managed MUST исключать unpaired устройства; локальные кэши удалённых Managed — очищаться при следующем launch (см. edge case).

#### Layout editor — viewing

- **FR-005**: Редактор раскладки MUST использовать **тот же** rendering pipeline (Composable экраны `HomeScreen`, `FlowScreen`, `BottomFlowBar`, `TileCard`) что и Managed-режим, **без точного pixel-масштаба** под разрешение Managed. Inline-TODO про exit ramp на pixel-accurate render если визуально окажется проблемой.
- **FR-005a** *(добавлен после code review 2026-05-15 C5)*: Существующие компоненты MUST быть расширены параметром `editMode: Boolean` (default `false`) + соответствующими edit-callback'ами (`onLongPress`, `onEditMenuClick`, `onAddSlotClick`, `onAddFlowClick`, `onDeleteFlowClick`). В view-режиме (default) — сохраняется текущее поведение спека 5; в edit-режиме — tap на плитку открывает форму редактирования (не dispatcher).
- **FR-006**: В редакторе тап на плитку **не запускает** Action; тап = открыть форму редактирования этой плитки.
- **FR-007**: Редактор MUST показывать **текущий локальный draft** (live draft), не applied snapshot. Кнопка «Показать текущую опубликованную» (read-only preview applied) — опционально.

#### Layout editor — editing operations

- **FR-008** *(уточнён C4 + core-quality 2026-05-15)*: Long-press на плитку MUST активировать drag-and-drop через **`Modifier.dragAndDropSource` / `Modifier.dragAndDropTarget`** (Compose 1.6+ built-in API) как **primary** реализация. Drop targets: другая позиция в той же flow, плитка в другой flow (cross-flow), корзина внизу экрана (удаление). **Two-way door fallback**: если в `/speckit.plan` research выявит проблемы с cross-flow drag через built-in API — переходим на ручную реализацию через `Modifier.pointerInput`. Inline-TODO в коде на этот fallback. **Window insets**: корзина-target внизу экрана MUST уважать `WindowInsets.safeContent` / `WindowInsets.navigationBars` (Android 15 edge-to-edge requirement) — не быть перекрытой system bar gestures.
- **FR-009**: Рядом с каждой плиткой в режиме редактирования MUST быть кнопка «···» с меню «Изменить / Переместить в / Удалить». Это параллельный канал для drag-and-drop (accessibility per Article VIII; users TalkBack, на планшете без точного указания).
- **FR-010**: Кнопка «+» в flow MUST позволять добавить новую плитку (выбор kind: Call / Sms / OpenApp).
- **FR-011**: Кнопка «+» в `BottomFlowBar` MUST позволять добавить новую flow (если preset допускает множественные flow; конкретное ограничение per preset — захардкожено в коде).
- **FR-012**: App MUST позволять переключение текущего preset из выпадающего списка готовых (workspace / simple-launcher / launcher). Меняет поле `presetId` в `/config`. Редактирование преsetа как структуры — out of scope (см. `TODO-FUTURE-SPEC-005`).

#### Forward-compat для preset

- **FR-013**: Wire format `/config` MUST содержать опциональное поле `presetOverrides: PresetSettings?` (всегда `null` в спеке 9). Это **зарезервированное место** для будущей подгрузки кастомных настроек preset (`TODO-FUTURE-SPEC-005`). Additive field (без bump `schemaVersion`).

#### Save / publish flow

- **FR-014**: App MUST иметь раздельные кнопки «Сохранить» (локально, инstant) и «Опубликовать» (push в `/config/current` через flow спека 8 FR-013/022).
- **FR-014a** *(добавлен после C1)*: Локальный draft MUST храниться в **локальной базе данных Room** (переиспользуется `PendingLocalChanges` table из спека 8), не в памяти (ViewModel state) и не на сервере. Per-Managed (one draft per linkId). Survives process kill / app restart / экран закрылся. Не синхронизируется между admin-устройствами одного admin'a (только локально на устройстве где сделан draft).
- **FR-014b** *(added 2026-05-15 from state-management checklist)*: Сохранение draft в Room MUST быть **continuous autosave per change** — каждое изменение в редакторе (перемещение плитки, редактирование текста, добавление/удаление flow) **немедленно** persistится в Room без необходимости явно нажимать «Сохранить локально». Кнопка «Сохранить локально» становится визуальным indicator («✓ Сохранено локально»), не triggers сохранения. Обоснование: на агрессивных OEM task-killer'ах (Xiaomi MIUI, Huawei EMUI) приложение может быть убито через 2-3 минуты в фоне — без continuous autosave admin потеряет ввод формы редактирования плитки.
- **FR-015**: При успешном «Опубликовать» — старая `current` копируется в `/config/history/{autoId}` **тем же клиентом**, **перед** обновлением current. Атомарность не гарантирована (race condition rare loss приемлем; migration → `SRV-CONFIG-001`).
- **FR-016**: При конфликте на push — Merge UI спека 8 FR-050 (единый, без senior-safe-варианта).

#### Phone health monitoring

- **FR-017**: App MUST читать `/links/{linkId}/health` (sample/snapshot from спек 6/7) и преобразовывать в локальный UI тип `List<PhoneHealthIndicator>` через adapter `HealthToPhoneIndicatorAdapter`.
- **FR-018**: Severity (`Info` / `Warning` / `Critical`) MUST вычисляться по threshold'ам из инстанса `PhoneHealthPreset` (defaults: battery `<5%` Critical / `<20%` Warning, lastSeen `>24ч` Critical / `>1ч` Warning, audioMuted = Warning, connectivity None = Warning).
- **FR-019**: Все значения threshold'ов MUST храниться **в одной структуре** `DEFAULT_PHONE_HEALTH_PRESET` (псевдо-пресет паттерн), **не разбросанные** литералами в коде. Готовность к подгрузке из `/config.presetOverrides.phoneHealthSettings` в будущем (`TODO-ARCH-010`).
- **FR-020** *(переписан 2026-05-15 после performance checklist — отказ от polling)*: Update cadence — **Firestore realtime listener когда экран открыт, отписка при закрытии**, независимо от severity. Severity (Info/Warning/Critical) вычисляется **client-side** через `DEFAULT_PHONE_HEALTH_PRESET` на каждом snapshot from listener. Polling-механизм НЕ используется — Firestore listener уже доставляет каждое изменение `/health`, разделение на «poll 30s for Info» было искусственным и нарушало Article IX §3 (event-driven preferred over polling). Push admin для closed app — `TODO-ARCH-012` / `SRV-MONITOR-001` (отдельная подсистема).
- **FR-021**: При переходе индикатора в `Critical` MUST эмититься **локальный** event `PhoneHealthCriticalEvent`. В спеке 9 — нет подписчика. Inline-TODO маршрута к Worker'у (`TODO-ARCH-012` / `SRV-MONITOR-001`).
- **FR-022**: `lastSeen` MUST показываться человекочитаемо: «сейчас», «N мин назад», «N часов назад», «N дней назад». Если `connectivity` в последнем известном snapshot был `None` — добавлять пометку «(последняя известная)».
- **FR-022a** *(added 2026-05-15 from accessibility checklist)*: Severity indicators (Info / Warning / Critical) MUST использовать **Material vector icons** (`Icons.Filled.CheckCircle` для Info, `Icons.Filled.Warning` для Warning, `Icons.Filled.Error` для Critical) с явным `contentDescription` на каждый. **НЕ использовать emoji** (🔴🟢) — TalkBack читает emoji неконсистентно (зависит от Android version + locale). ContentDescription формат: «Заряд: 78%, в норме» / «Заряд: 15%, низкий» / «Заряд: 3 процента, критический». Цвет иконки берётся из theme tint, дублируется иконкой формы (CheckCircle/Warning/Error) — не only-color индикатор (защита от color blindness).

#### Contacts — Android Contacts picker

- **FR-023**: В форме редактирования плитки `kind = Call / Sms` MUST быть кнопка «Выбрать из контактов». Запрашивает permission `READ_CONTACTS` (с rationale-экраном «зачем», стандартный Android pattern).
- **FR-023a** *(added 2026-05-15 from failure-recovery + permissions-platform checklists)*: Если `READ_CONTACTS` denied (любой degree — одноразовый отказ или permanently denied) — UI MUST предоставить **две альтернативы**:
  1. **Кнопка «Ввести вручную»** — открывает форму ручного ввода `displayName + phoneNumber` (те же поля валидации через `Contact.fromRaw`, как при picker-flow); admin вводит данные с клавиатуры, плитка создаётся без `READ_CONTACTS` permission.
  2. **Информационный баннер**: «Вы также можете добавлять контакты, делясь ими из WhatsApp / Telegram / других мессенджеров — для этого разрешение на чтение контактов не нужно. Откройте мессенджер → выберите контакт → «Поделиться» → выберите наше приложение». См. FR-027 (VCard share intent). Баннер показывается **при первом отказе** и доступен как «(?)» tooltip далее.
- **FR-023b** *(added 2026-05-15 from failure-recovery + permissions-platform checklists)*: Если `READ_CONTACTS` permanently denied (системный диалог разрешений больше не появляется) — UI MUST показать кнопку «Открыть настройки приложения», которая через `Intent.ACTION_APPLICATION_DETAILS_SETTINGS` открывает страницу разрешений нашего приложения в системных Settings (направляет пользователя на точное место, где можно вручную разрешить). Без этой кнопки denial = тупик.
- **FR-024**: После grant — открывается системный picker (`Intent.ACTION_PICK` с `ContactsContract.CommonDataKinds.Phone.CONTENT_URI` — только контакты с номером).
- **FR-025**: Если у выбранного контакта > 1 номера — показать диалог «Какой номер использовать?».
- **FR-026** *(переписан C3)*: `SystemContactPickerAdapter` (anti-corruption layer per CLAUDE.md rule 2) MUST:
  - принять URI из picker'a → прочитать `ContactsContract.CommonDataKinds.Phone` → извлечь raw `displayName: String` и `phoneNumber: String`;
  - передать сырые значения в **доменный валидатор** `Contact.fromRaw(name, phone)` (см. `## Domain validation contract`);
  - при `ValidationError` показать пользователю «Не удалось добавить контакт: <причина>», не пытаться «починить» данные.
  Создаваемый `Contact` объект имеет `photoRef = null` (фото — `spec 011`). На плитке — **инициалы** или абстрактная иконка.

#### Contacts — VCard share intent (per-provider adapter)

- **FR-027**: Admin-приложение MUST зарегистрировать `<intent-filter>` на `ACTION_SEND` + MIME `text/x-vcard`, чтобы появляться в системном share sheet при «Поделиться контактом» из WhatsApp / Telegram / Viber / системных Contacts.
- **FR-027a** *(added 2026-05-15 from state-management checklist — resolves Q-OPEN-2)*: VCard-receiving Activity MUST использовать `android:launchMode="singleTask"` в `AndroidManifest.xml` + override `onNewIntent()` в коде Activity. Если приложение уже открыто (admin был в редакторе раскладки, переключился в WhatsApp, поделился контактом) — Android **передаёт VCard intent в существующий instance** через `onNewIntent` вместо запуска новой Activity. Это даёт **одну** копию приложения в task switcher (не две), сохраняет navigation backstack admin'а, при закрытии VCard-экрана admin возвращается в свой редактор. Стандартный Android pattern для deep-link сценариев.
- **FR-028** *(переписан C3)*: `VCardImportAdapter` (anti-corruption layer per CLAUDE.md rule 2) MUST:
  - reject payload > 10 KB как «слишком большой» (DoS защита);
  - reject не-UTF8 encoding с сообщением «Не удалось прочитать контакт»;
  - parse VCard text → extract `FN` (full name) + `TEL[n]` (phone numbers, may be multiple);
  - **игнорировать** все остальные VCard поля: `PHOTO`, `EMAIL`, `ADR`, `URL`, `BDAY`, custom fields, X-* extensions (нулевая attack surface);
  - передать сырые строки `displayName + phoneNumber` в **доменный валидатор** `Contact.fromRaw()`.
- **FR-029**: При успешном parse VCard adapter MUST показать промежуточный экран «Добавить контакт <displayName>/<phoneNumber> в раскладку: <выпадающий список Managed>». Preselect — если у админа один Managed.
- **FR-030**: При выборе Managed — переход в её редактор раскладки с **предзаполненной формой** новой плитки `Call` с этим contact. Admin выбирает flow и подтверждает.
- **FR-031** *(уточнён C3)*: Если в распарсенном VCard нет `TEL` (контакт LINE/WeChat/KakaoTalk без публичного номера, или только email) — adapter MUST отвергнуть VCard с сообщением «Контакт без номера телефона не может быть добавлен в текущей версии». См. `TODO-ARCH-014` + `TODO-FUTURE-SPEC-003`.
- **FR-032** *(перенесён в FR-028)*: -- (правила payload validation теперь часть FR-028 как единого adapter contract).

#### Contacts — deduplication (domain level, после adapter validation)

- **FR-033**: После успешной валидации Contact через `Contact.fromRaw()` (независимо от того, какой adapter его создал) — проверка дубликата по **строгому совпадению `phoneNumber`** с существующими в `/config.contacts[]`. Если совпадение есть — переиспользуем существующий `Contact.id` для нового Slot, новый Contact не создаётся.

#### Contacts — privacy compliance minimum (added 2026-05-15 from /speckit.clarify security checklist)

- **FR-033a**: В admin Settings MUST быть экран «Добавленные контакты» — список всех `Contact` из `/config.contacts[]` всех управляемых Managed (group by Managed), с возможностью удалить любой контакт (приводит к удалению Contact из `/config.contacts[]` + всех ссылающихся Slot'ов в `/config.flows[].slots[]`, через стандартный push спека 8).
- **FR-033b**: Удаление контакта через FR-033a MUST быть **немедленным** (no «soft delete» с recovery period) — соответствует GDPR ст.17 «right to erasure». При оффлайн — pending action, применяется при первом online.
- **FR-033c**: Расширенный `READ_CONTACTS` rationale-экран (FR-023) MUST явно сообщать: «Контакты, которые вы добавите, сохраняются в облаке Firebase и видны на устройстве вашего родственника. Вы можете удалить их в любой момент через Settings → Добавленные контакты».

#### Application tiles

- **FR-034**: Форма редактирования плитки `kind = OpenApp` MUST позволять указать `packageName`. Способ ввода: (а) выбор из списка приложений, установленных на админ-устройстве; (б) ручной ввод.
- **FR-035**: На Managed при tap'е плитки `kind = OpenApp` dispatcher MUST: (а) проверить наличие приложения (требует объявленных `<queries>` в манифесте per Android 11+); (б) если есть — запустить через `LAUNCHER` intent; (в) если нет — открыть `market://details?id=<packageName>` (Play Store страница) с fallback на web URL.
- **FR-035a** *(added 2026-05-15 from permissions-platform checklist)*: AndroidManifest.xml `<queries>` блок MUST содержать **generic intent declaration** позволяющий probing arbitrary packages для OpenApp плиток:
  ```xml
  <queries>
      <intent>
          <action android:name="android.intent.action.MAIN" />
          <category android:name="android.intent.category.LAUNCHER" />
      </intent>
      <intent>
          <action android:name="android.intent.action.VIEW" />
          <data android:scheme="market" />
      </intent>
  </queries>
  ```
  Без этого `<queries>` на Android 11+ `PackageManager` НЕ видит произвольные packages (только whitelist из спека 6 для WhatsApp/Telegram/YouTube). Dispatcher MUST использовать `packageManager.queryIntentActivities(Intent(ACTION_MAIN).addCategory(LAUNCHER).setPackage(packageName))` вместо `getPackageInfo()` для availability check — это работает с generic queries-блоком. Без этого FR-035 случай (б) **никогда** не срабатывает для произвольных приложений (всегда уходит в Play Store fallback).

#### Config history + rollback

- **FR-036** *(уточнён C2)*: Subcollection `/links/{linkId}/config/history/{autoId}` хранит snapshot'ы предыдущих успешных push'ей в `/config/current`. Каждый snapshot содержит:
  - `snapshotSchemaVersion: Int` — версия **envelope-схемы** этого snapshot record (отдельно от config внутри);
  - `config: ConfigCurrent` — полный конфиг **с его собственным `schemaVersion`** внутри (не дубликат, не агрегат);
  - `recordedAt: Long` — epoch millis момента push'a;
  - `recordedFromDeviceId: String` — какое устройство пушило.

  **Почему два независимых schemaVersion'a**: envelope и config эволюционируют **независимо**. Bump envelope schema (например, добавление поля `revertedFromId: String?`) — не требует transformer для config. И наоборот. При rollback используется **цепочка** транзформеров: сначала envelope vN → vCurrent, потом config vN → vCurrent (см. `TODO-ARCH-015`).
- **FR-037**: При каждом успешном push в `/config/current` клиент MUST скопировать **предыдущую current** в `/config/history/{autoId}` **перед** обновлением current. Без batch transaction (race condition rare loss — приемлем).
- **FR-038**: Retention 10: после успешного push клиент MUST прочитать all snapshots в history → если ≥ 11, удалить старейшие до остатка 10. Client-side housekeeping (migration → `SRV-CONFIG-002`).
- **FR-039** *(уточнён 2026-05-15 — resolves Q-OPEN-1)*: UI «История» — **отдельный полноэкранный экран** (`HistoryScreen`), открывающийся из меню редактора раскладки (icon «···» → «История»). Список snapshot'ов по `recordedAt DESC`. Каждый item: дата/время + `recordedFromDeviceId` + бэйдж «текущая» для current. Обоснование выбора отдельного экрана vs bottom sheet / sidebar: rollback flow multi-step (list → preview → откатить → подтверждение), не помещается в bottom sheet; sidebar не привычен на телефоне (планшетный/desktop паттерн). Согласуется с Google Docs Version History pattern.
- **FR-040**: Тап по snapshot → открывается **read-only** редактор раскладки с этим содержимым (preview). Кнопка «Откатить к этой версии» внизу.
- **FR-041**: Откат = новый push в `/config/current` с содержимым выбранного snapshot. Идёт через стандартный flow спека 8 (FR-013 conflict-check; при конфликте — Merge UI).
- **FR-042**: Откат доступен **обоим** editor'ам (admin + Managed через 7-tap+пароль) — симметрия со спеком 8 FR-050.
- **FR-043**: Schema validation при чтении snapshot: если `snapshot.schemaVersion > current code support` — отвергаем как «слишком новая версия». Если `<` — пробуем lazy transformer (`TODO-ARCH-015`); пока транзформера нет — кнопка отката заблокирована с пояснением.

#### Security Rules

- **FR-044**: Read `/links/{linkId}/config/history/*` MUST разрешать adminId AND managedDeviceFirebaseUid (как `/config/current` спека 8).
- **FR-045**: Write в `/links/{linkId}/config/history/*` MUST разрешать те же UID-ы (клиент пишет history). Inline-TODO на migration к server-only через `SRV-CONFIG-001`.
- **FR-045a** *(added 2026-05-15 from security checklist)*: Security Rule MUST enforce field-level constraint: `recordedFromDeviceId == request.auth.uid` (anti-spoofing — клиент не может выдать write за другое устройство). Без этого rule клиентский spoofing возможен (admin может писать с `recordedFromDeviceId = managedDeviceFirebaseUid`, выдавая правку за бабушкину).
- **FR-045b** *(added 2026-05-15 from security checklist)*: `firestore.rules` MUST содержать **explicit subcollection rules** для `/links/{linkId}/config/{configId}/history/{autoId}` — Firestore НЕ наследует rules от parent collection на subcollections автоматически. Без этого FR-044/045 не работают (вся история inaccessible). Acceptance: `firestore-tests/` имеет тесты на read/write для admin / Managed / unauthorized попыток.

#### Android backup exclusion

- **FR-046a** *(added 2026-05-15 from security checklist; не путать с FR-046 icon fix)*: `app/src/main/AndroidManifest.xml` MUST включать reference на `data_extraction_rules.xml`, исключающий Room database с контактами (`/data/data/<app>/databases/<contacts-db>`) из Android Auto Backup и Device-to-Device Transfer. Без этого PII третьих лиц (Маша) автоматически попадает в Google Drive админа без её consent — нарушение GDPR transfer-to-processor. Проверить: возможно `allowBackup` issue уже частично адресован в спеке 8 mandatory action — если нет, фиксить здесь.

#### Existing component bug fixes (discovered in 2026-05-15 code review)

- **FR-046** *(добавлен после C5)*: Existing `TileCard` ([core/.../components/TileCard.kt:73](../../core/src/commonMain/kotlin/com/launcher/ui/components/TileCard.kt#L73)) MUST варьировать иконку в зависимости от `SlotKind`: `Call` → `Icons.Filled.Call`, `Sms` → `Icons.Filled.Sms` (или подобная message-иконка), `OpenApp` → иконка приложения из packageManager (с fallback на `Icons.Filled.Apps`). Текущий код **захардкоживает** `Icons.Filled.Call` для всех типов — это **существующий баг**, не обнаруженный в спеках 5/8. Спек 9 фиксит как часть FR-005a edit-mode расширения.

---

## Domain validation contract

> Anti-Corruption Layer per CLAUDE.md rule 2. Универсальные правила, применяемые **ко всем** Contact'ам независимо от источника. Per-provider адаптеры (system picker, VCard, future Telegram/LINE SDKs) парсят свой формат и передают сырые данные в этот **общий** валидатор.

### `Contact.fromRaw(rawName: String, rawPhone: String): Result<Contact>`

Domain factory function в [core/src/commonMain/kotlin/com/launcher/api/config/Contact.kt](../../core/src/commonMain/kotlin/com/launcher/api/config/Contact.kt). Возвращает `Result<Contact>` (либо валидный Contact, либо ValidationError).

**Правила валидации:**

| Поле | Правило | Reason |
|---|---|---|
| `rawName` | trim + remove ASCII control chars (`\x00-\x1F`, `\x7F`), но **сохранить** Unicode letters/digits/punctuation/emoji | Бабушка может назвать контакт «Маша 😍» — это нормально. Control chars ломают Firestore writes. |
| `rawName` | max 100 символов **после** trim/cleanup | Защита от DoS (огромное имя ломает UI). Под Unicode-длину (`.length` Kotlin), не byte-length. |
| `rawName` | non-empty после trim | Пустой displayName бессмыслен — пользователь не поймёт, кому звонит. |
| `rawPhone` | strip whitespace, dashes, parentheses, dots; оставить только `[0-9+]` | Системные picker'ы возвращают «+7 (916) 123-45-67» — нормализуем. |
| `rawPhone` | matches regex `^\+?\d{5,20}$` | Минимум 5 цифр (короткие SMS-сервисы), максимум 20 (международные с extensions). Защита от инъекций. |

**Ошибки** возвращаются typed: `ValidationError.NameTooLong`, `ValidationError.PhoneInvalid`, etc. UI показывает локализованное сообщение по типу.

### Per-provider adapter contract

Каждый adapter (FR-026 system picker, FR-028 VCard, future Telegram/LINE) MUST:

1. **Парсить** свой формат (URI / VCard text / SDK callback) → извлечь raw `displayName` и `phoneNumber` как строки.
2. **Игнорировать** все поля, которые `Contact` не использует (photo, email, address, custom fields, X-* extensions). Никаких extra полей в Contact «потенциально пригодится».
3. **Передать** сырые строки в `Contact.fromRaw()`. Не «чинить» данные локально (если phone в неожиданном формате — это работа domain validator, не adapter).
4. **При `ValidationError`** — показать пользователю причину, не пытаться повторно вызвать adapter с подправленными данными.

**Тестирование:**
- Domain validator тестируется **отдельно** (unit-тесты на `Contact.fromRaw` по правилам).
- Каждый adapter тестируется **отдельно** (на парсинг своего формата, с моком validator'а).
- Контрактный roundtrip тест: для каждого adapter взять «реальный» вход (sample VCard от WhatsApp, sample URI от system Contacts) → прогнать через adapter → проверить, что результат — валидный Contact или предсказуемая ValidationError.

---

### Key Entities

- **PhoneHealthIndicator** (local UI type, app/health-ui/): `{ id, sourceType="phone", label, value: String, severity: Severity, updatedAt }` — обобщённое представление одного индикатора в UI. **Не часть domain layer** (не претендует на universality для часов / сенсоров).
- **PhoneHealthSeverity** (enum): `Info` / `Warning` / `Critical`.
- **PhoneHealthPreset** (data class): псевдо-пресет с threshold'ами и flag'ами (`battery: BatteryThresholds`, `lastSeen: LastSeenThresholds`, `audioMutedSeverity`, `connectivityNoneSeverity`, `updateCadenceInfoSec`, `pushAdminOnCritical`). Один захардкоженный экземпляр `DEFAULT_PHONE_HEALTH_PRESET`. Готов к подгрузке из `/config.presetOverrides.phoneHealthSettings` (TODO).
- **PhoneHealthCriticalEvent** (local event, app-internal): эмитится при переходе индикатора в Critical. Нет подписчика в спеке 9.
- **ConfigSnapshot** (новая subcollection wire entity, `/config/history/{autoId}`): `{ schemaVersion, config: ConfigCurrent, recordedAt, recordedFromDeviceId }`.
- **PresetSettings** (forward-compat wire entity, `/config.presetOverrides`): зарезервированная структура, всегда `null` в спеке 9.

### Domain (наследуется из спеков 5/6/8 без изменений)

- `Config`, `Flow`, `Slot`, `Contact`, `SlotKind` ([core/src/commonMain/kotlin/com/launcher/api/config/](../../core/src/commonMain/kotlin/com/launcher/api/config/)) — без изменений.
- `Health` ([core/src/commonMain/kotlin/com/launcher/api/health/Health.kt](../../core/src/commonMain/kotlin/com/launcher/api/health/Health.kt)) — без изменений; читается через `HealthRepository.observe()`.

---

## Non-Functional Requirements *(performance, added 2026-05-15 from performance checklist)*

Measurable technical characteristics, не functional success criteria. SC-001..008 measure user task duration (90 sec для редактирования и т.д.); NFR ниже measure technical responsiveness без которой functional SCs не достижимы.

- **NFR-001 (Drag-and-drop frame budget)**: На стандартном тестовом устройстве (Pixel 4a class — 60 fps) drag-and-drop плитки между flow'ами MUST НЕ пропускать кадры (0 dropped frames). Измерение через `androidx.benchmark.macro` `FrameTimingMetric` (frameDurationCpuMs p99 < 16ms). Если NFR-001 фейлится → переходим на ручную реализацию через `Modifier.pointerInput` (two-way door fallback из FR-008).
- **NFR-002 (VCard parse latency)**: Парсинг VCard payload (≤ 10 KB, FR-028 limit) MUST завершаться за **< 100 ms p95** на Pixel 4a class. Runs on `Dispatchers.Default` (не блокирует UI thread). Защита от regex backtracking — linear-time парсер для FN + TEL fields, никаких greedy quantifiers. Измерение через microbenchmark.

---

## Accessibility requirements *(added 2026-05-15 from accessibility checklist — WCAG 2.2 AA + Android Accessibility + Article VIII)*

- **FR-A11Y-001 (ContentDescription policy)**: Каждый interactive UI element (button, tile, icon button, drag handle, severity indicator) MUST иметь explicit `contentDescription` (или `semantics { contentDescription = ... }` в Compose). НЕТ исключений «декоративные иконки» для action surfaces. Decorative-only icons рядом с подписью — `contentDescription = null` явно (semantic merge с подписью).
- **FR-A11Y-002 (Merged semantics для list rows)**: В списке Managed-устройств каждая строка (имя + 4 health-индикатора + chevron) MUST использовать `Modifier.semantics(mergeDescendants = true)` так, чтобы TalkBack воспринимал строку **как одну единицу** (один swipe = одна строка). Без merge — admin с TalkBack делает 5 swipes на каждую из ~10 строк (50+ tap-on-tap action для простого скана).
- **FR-A11Y-003 (LiveRegion для критичных state-changes)**: TalkBack `LiveRegion` (mode = polite) на:
  - баннер «есть несохранённые изменения / Сохранено локально» (FR-014b autosave indicator),
  - переход severity indicator в Critical (FR-022a),
  - результат push'a («Опубликовано» / «Конфликт, открыть Merge UI»).
  Без LiveRegion — слабовидящий admin не узнает о важных изменениях статуса без manual focus traversal.
- **FR-A11Y-004 (Drag-and-drop accessibility parallel)**: Кнопка «···» (FR-009) MUST быть **полноценным** accessibility-channel для всех drag-and-drop операций (move-to-flow / delete), НЕ «fallback» статус. Acceptance: SC-007 уже требует 100% покрытие; явно зафиксировано как accessibility-обязательство, не «nice-to-have».
- **FR-A11Y-005 (Font scaling)**: Все UI-компоненты MUST корректно отображаться при **200% font scale** (Android Settings → Display → Font size). Текст не обрезается, кнопки растягиваются. Layout MUST использовать `wrapContentHeight()` и `wrapContentWidth()` для adaptive sizing.
- **FR-A11Y-006 (Contrast)**: Все text-on-background pairs MUST удовлетворять **WCAG 2.2 AA contrast** (4.5:1 для normal text, 3:1 для large text ≥ 18 pt). Severity icon на background — ≥ 3:1 для shape recognition. Audited через Android Accessibility Scanner в plan-фазе.
- **FR-A11Y-007 (Focus order)**: Logical focus order для TalkBack — сверху вниз / слева направо без unexpected jumps. Modal screens (HistoryScreen preview, форма редактирования плитки) — focus возвращается на triggering element при close.

#### Existing colour-vision fix (from core-quality CHK002)

- **FR-046b** *(added 2026-05-15)*: Severity colors (Info / Warning / Critical) MUST иметь **distinct hue** (не только distinct lightness) — для color blindness compatibility. Light theme: Info = green-600, Warning = amber-600, Critical = red-700. Dark theme: Info = green-300, Warning = amber-300, Critical = red-400. Помимо цвета — дублирование иконкой формы (FR-022a) — это **second layer** color blindness mitigation.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Admin может изменить одну плитку на бабушкином телефоне удалённо за ≤ 90 секунд (от open приложения до push'a) — путь не теряется в UI.
- **SC-002**: При снижении battery бабушки до < 5% admin видит Critical индикатор в течение ≤ 35 сек (Firestore listener + UI update) и `PhoneHealthCriticalEvent` эмитится локально.
- **SC-003**: Когда admin шарит контакт из WhatsApp/Telegram в наше приложение — flow «share → выбрать Managed → подтвердить → push» завершается за ≤ 60 секунд для admin'а, привычного к приложению.
- **SC-004**: После push неудачной раскладки admin может откатить к предыдущей версии за ≤ 60 секунд (open редактор → история → выбор → preview → откатить).
- **SC-005**: При schema bump в будущем существующие snapshot'ы в history остаются preview-доступны (через lazy transformer когда написан); 100% snapshot'ов с `schemaVersion = 1` корректно читаются.
- **SC-006**: Drag-and-drop плитки между flow'ами работает корректно в 95% сценариев (10 переmещений подряд, без падений / потери позиции).
- **SC-007**: Параллельная кнопка «···» (для accessibility) позволяет выполнить все операции, доступные через drag-and-drop (move/delete) — 100% покрытие.
- **SC-008**: На Managed без `READ_CONTACTS` permission плитка `Call` корректно набирает номер из `/config.contacts[phone]` — 100% сценариев.

---

## Out of Scope

- **OUT-001**: Screen mirroring / remote control экрана Managed (нельзя без Device Policy Controller).
- **OUT-002**: iOS-управление (платформа iOS — отдельная стратегия).
- **OUT-003**: WYSIWYG pixel-accurate render под точное разрешение Managed (см. exit ramp в FR-005; trigger upgrade: если density mismatch окажется реальной UX-проблемой).
- **OUT-004**: Multi-select Managed-устройств / параллельное редактирование нескольких Managed одновременно.
- **OUT-005**: Редактирование preset как структуры (цвет фона, шрифт, расположение тулбара, свайпы) — `TODO-FUTURE-SPEC-005: preset-editor`.
- **OUT-006**: Мониторинг smart-часов (пульс, давление) — `TODO-FUTURE-SPEC-001: wearable-monitor`.
- **OUT-007**: Мониторинг охранной сигнализации / smart-home сенсоров — `TODO-FUTURE-SPEC-002: security-sensor-monitor`.
- **OUT-008**: Поддержка контактов из LINE / WeChat / KakaoTalk (закрытые мессенджеры без публичных phone) — `TODO-FUTURE-SPEC-003: messenger-contact-integration`.
- **OUT-009**: Push уведомлений админу при Critical health (`PhoneHealthCriticalEvent` эмитится, но подписчика нет) — `TODO-ARCH-012` / `SRV-MONITOR-001`.
- **OUT-010**: UI редактирования phone health threshold'ов — `TODO-ARCH-010` (структура `PhoneHealthPreset` готова к этому).
- **OUT-011**: Несколько named phone-health presets (medical / minimum / ...) — `TODO-ARCH-011`.
- **OUT-012**: Detection of contact drift (Маша в системных контактах admin'a сменила номер) — `TODO-ARCH-013`.
- **OUT-013**: Shared admin contact book (общая адресная книга на нескольких Managed) — `TODO-FUTURE-SPEC-004`.
- **OUT-014**: Полная privacy compliance UI (rationale + consent + GDPR export + deletion endpoints) — `TODO-LEGAL-001`. **Минимум** (экран «список добавленных контактов с кнопкой удалить» — 0.5 дня работы) — **отложено в backlog** по решению pre-specify session 2026-05-15.
- **OUT-015**: Server-side integrity для config history writes / housekeeping — `SRV-CONFIG-001` / `SRV-CONFIG-002` в [server-roadmap.md](../../docs/dev/server-roadmap.md).
- **OUT-016**: App version compatibility detection (admin v2 ↔ Managed v1) — `TODO-ARCH-007` (отдельный спек).
- **OUT-017**: Технические `/commands/` от admin к Managed (refresh capabilities, force-apply, и т.д.). «Открыть приложение» — заменено на штатную плитку через `Action open_app` (US-7, FR-034/035). Остальные команды — backlog для будущего спека runtime-commands.
- **OUT-018**: Config schema transformers (`vN → vN+1`) — `TODO-ARCH-015`. На момент `schemaVersion = 1` работы 0.
- **OUT-019**: Сложные retention policies для history (по дате, по размеру) — только «последние 10».
- **OUT-020**: Diff-view между версиями в UI истории (сравнение side-by-side, highlighting added/removed) — out of scope per user decision «без оверинжениринга».

---

## Clarifications

### 2026-05-15 — Post-specify clarification pass (`/speckit.clarify` run)

После draft spec.md проведён formal `/speckit.clarify` — найдены 5 grey zones, **не покрытых** pre-specify session. Резолюции вплетены в FR ниже + новый раздел `## Domain validation contract`.

| # | Question (plain language) | Resolution |
|---|---------------------------|------------|
| C1 | Где живёт черновик правок до push'a? | **Локальная база данных** (Room) — переиспользуем `PendingLocalChanges` из спека 8. Per-Managed draft, не синхронизируется между admin-устройствами одного admin'a (sync — backlog если понадобится). Survives process kill / app restart. См. FR-014a (новая). |
| C2 | Как версионируется `ConfigSnapshot` относительно `ConfigCurrent`? | **Два независимых номера версии**: `ConfigSnapshot.schemaVersion` (envelope schema истории) + `ConfigSnapshot.config.schemaVersion` (config schema внутри). Транзформеры — два независимых, при необходимости. См. FR-036 (уточнён) + TODO-ARCH-015 (расширен). |
| C3 | Валидация Contact — per-provider или universal? | **Universal contract + per-provider adapters** (CLAUDE.md rule 2 ACL). Домен (`core/api/config/Contact.kt`) определяет правила валидации (имя ≤ 100, no control chars, phone matches regex `^\+?\d{5,20}$`). Каждый источник (system picker, VCard, future Telegram SDK) имеет свой **адаптер**, который парсит формат и передаёт сырые строки в **общий** `Contact.fromRaw()`. См. новый раздел `## Domain validation contract` + переписанные FR-026/028-032. |
| C4 | Drag-and-drop API: Compose built-in или ручной? | **`Modifier.dragAndDropSource/Target`** (Compose 1.6+) как primary. **Two-way door** — fallback на `pointerInput` если research в `/speckit.plan` выявит проблемы с cross-flow drag. См. FR-008 (уточнён). |
| C5 | A-9 «existing composables pригодны к переиспользованию через mode flag» — проверено? | **Code review проведён 2026-05-15.** Verdict: расширяемо, но **не «просто флаг»** — требует 4-8 новых параметров на компонент, варьируемой иконки по SlotKind (новая работа), drag-and-drop infrastructure. **Bug discovered:** `TileCard.kt:73` иконка захардкожена `Icons.Filled.Call`, не варьируется по SlotKind — спек 9 фиксит. См. A-9 (уточнён), FR-005a (новая), FR-046 (новая). |

### 2026-05-15 — Post-checklists resolution (batch 1+2 от Skill orchestrator)

После batch 1 (security checklist) + batch 2 (failure-recovery / permissions-platform / state-management / performance / ux-quality) добавлены 9 новых FR + 2 NFR + resolved 3 Q-OPEN.

| # | Source | Issue | Resolution |
|---|--------|-------|------------|
| C6 | failure-recovery + permissions-platform | READ_CONTACTS denied → admin в тупике | FR-023a (ручной ввод + информационный баннер про VCard share как альтернативу без permission) + FR-023b (deep-link в Settings для permanently denied) |
| C7 | permissions-platform | Android 11+ `<queries>` whitelist (только WhatsApp/Telegram/YouTube) → OpenApp для произвольных packages не работает | FR-035a — generic `<queries>` блок (LAUNCHER + market:// scheme) + использование `queryIntentActivities` вместо `getPackageInfo` |
| C8 | state-management | FR-014a говорит «Room», но не уточняет cadence сохранения | FR-014b — continuous autosave per change (не explicit «Сохранить» button); защита от OEM task-killer'ов |
| C9 | performance | 30s polling для Info severity = искусственное разделение, нарушает Article IX §3 | FR-020 переписан — Firestore realtime listener когда экран открыт **всегда**, severity вычисляется client-side. Polling не используется. |
| C10 | state-management | Q-OPEN-2 (VCard intent в новой Activity vs существующей) | FR-027a — `launchMode="singleTask"` + `onNewIntent()` (одна копия в task switcher, стандартный Android deep-link pattern) |
| C11 | ux-quality + research | Q-OPEN-1 (UX-форма «История») | FR-039 уточнён — **отдельный полноэкранный экран** `HistoryScreen` (research показал: Google Docs Version History = full screen pattern; bottom sheet не вмещает preview раскладки) |
| C12 | spec-kit discipline | Q-OPEN-3 (точные dp размеры) | DEFERRED to plan.md — implementation detail, не spec-level. В спеке только Article VIII senior-safe tap target ≥ 56 dp constraint. |
| C13 | performance | Нет measurable performance budgets (есть functional SC, но нет frame budget / parse latency) | NFR-001 (drag-and-drop 0 dropped frames на Pixel 4a) + NFR-002 (VCard parse < 100 ms p95). Новый раздел `## Non-Functional Requirements`. |

### 2026-05-15 — Post-checklists batch 3 (accessibility / elderly-friendly / core-quality)

После последнего batch'a — 2 accessibility FAILs + 2 core-quality minor edits resolved.

| # | Source | Issue | Resolution |
|---|--------|-------|------------|
| C14 | accessibility | Emoji 🔴🟢 для severity indicator — TalkBack читает неконсистентно, color blindness risk | FR-022a — Material vector icons + explicit contentDescription, дублирование цвета формой иконки |
| C15 | accessibility | 0 упоминаний contentDescription / TalkBack / semantics в спеке (CHK007) | Новый раздел `## Accessibility requirements` с FR-A11Y-001..007 (contentDescription policy, merged semantics, LiveRegion, font scaling 200%, WCAG 2.2 AA contrast, focus order) |
| C16 | core-quality | Severity colors могут совпадать в hue для color blind users | FR-046b — distinct hue light/dark theme + shape duplication (icon shape) |
| C17 | core-quality | Drag-trash target внизу может перекрываться system bars (Android 15 edge-to-edge) | FR-008 дополнен — `WindowInsets.safeContent` / `navigationBars` для drag-trash target |

### 2026-05-15 — Pre-specify mentor session (16 Q-ответов до /speckit.specify)

**Q1 (density mismatch).** Admin рендерит раскладку на своём устройстве. Что значит «полностью отображать телефон Managed»?
→ **Решение**: декоративная рамка телефона (FR-005), без точного pixel-масштаба. Подпись «экран ~Y" / N плиток в ряду». **Exit ramp**: pixel-accurate render если визуально не подойдёт (inline-TODO в коде, не в спеке 9).

**Q2 (preset scope).** Preset = тема / структурное ограничение / редактируемый набор настроек?
→ **Решение**: сейчас preset = ссылка на захардкоженный шаблон (workspace / simple-launcher / launcher). Wire format готов через FR-013 (`presetOverrides: PresetSettings?`) к будущей редактируемости. Сам preset-editor — `TODO-FUTURE-SPEC-005`.

**Q3 (drag-and-drop UX).** Какой паттерн редактирования плиток?
→ **Решение**: long-press → drag (стандартный Android UX) + параллельная кнопка «···» с меню для accessibility. FR-008 / FR-009.

**Q4 (что admin видит — applied или draft).** Live draft или applied state?
→ **Решение**: live draft (FR-007). Pull один раз при open редактора, дальше — локальные изменения, applied state не auto-refresh-ится (минимизация запутывания).

**Q5 (size of spec).** Один большой 9 или 9a/9b?
→ **Решение**: один спек 9 (размер принят пользователем).

**Q6 (universal monitor abstraction).** Делать `MonitorIndicator` обобщённую структуру для часов / сенсоров?
→ **Решение**: нет (отвергнут оверинжениринг). Phone health = локальный UI тип `PhoneHealthIndicator` в `app/health-ui/`, **не в domain layer**. Часы / сигнализация — отдельные подсистемы (`TODO-FUTURE-SPEC-001/002`).

**Q7 (severity & update cadence).** Жёстко по severity?
→ **Решение**: Info → pull 30 сек, Warning/Critical → realtime listener когда экран открыт (FR-020). Push админу при closed app — `TODO-ARCH-012`.

**Q8 (configurable thresholds).** Делать UI сейчас или потом?
→ **Решение**: сейчас захардкожено в `DEFAULT_PHONE_HEALTH_PRESET` data class (структура готова, FR-019). UI редактирования — `TODO-ARCH-010`. Все настройки **в одной структуре**, не разбросаны.

**Q9 (фото контакта).** В спеке 9?
→ **Решение**: нет (FR-026). `photoRef = null`. Полные фото — `spec 011 contacts-and-e2e-encrypted-media` (с e2e-шифрованием).

**Q10 (deduplicate contacts).** Как?
→ **Решение**: по строгому совпадению `phoneNumber` (FR-033). Имя не учитывается.

**Q11 (drift detection).** Auto-обновление номеров?
→ **Решение**: нет (FR не нужен). Игнорируем drift, админ обновляет вручную. `TODO-ARCH-013` в backlog.

**Q12 (privacy compliance).** Минимум сейчас?
→ **Решение**: нет (полностью отложено в `TODO-LEGAL-001`). Risk признан, обязательно до production-релиза.

**Q13 (messenger via VCard).** Покрывать?
→ **Решение**: да через VCard share intent (FR-027..032) для WhatsApp / Telegram / Viber / системных Contacts. Закрытые — `TODO-FUTURE-SPEC-003`.

**Q14 (history write strategy).** Cloud Function или client?
→ **Решение**: client-side, без batch transaction (FR-037). Race condition приемлем. Migration → `SRV-CONFIG-001`.

**Q15 (rollback authority).** Только admin или оба editor'a?
→ **Решение**: оба (FR-042). Симметрия со спеком 8 FR-050 — кто через 7-tap+пароль попал в редактор, тот editor.

**Q16 (schema invalidation).** Drop incompatible или transformer?
→ **Решение**: lazy transformer (FR-043). `TODO-ARCH-015` для написания транзформеров при первом schema bump. Сейчас `schemaVersion = 1` — работы 0.

---

## Assumptions

- **A-1**: Спек 7 paired admin↔Managed работает; `linkId`, `adminId`, `managedDeviceFirebaseUid` доступны через `LinkRepository`.
- **A-2**: Спек 8 sync flow (save/push/conflict-check/merge UI) работает и используется как mechanism для всех push'ей этого спека.
- **A-3**: Спек 6 `Health` snapshot пишется в `/links/{linkId}/health` (через спек 7); читается через `HealthRepository.observe()`.
- **A-4**: Firestore offline persistence (Firebase SDK default) даёт локальный кэш конфига; без доп. работы.
- **A-5**: На admin-устройстве пользователь может grant'ить `READ_CONTACTS` permission (стандартный Android runtime permission).
- **A-6**: Android-устройство admin'a поддерживает `<intent-filter>` на `text/x-vcard` (Android 5+).
- **A-7**: Android-устройство Managed имеет `<queries>` блок в манифесте для intent resolution `OpenApp`/Play Store (Android 11+).
- **A-8**: WhatsApp / Telegram / Viber выдают VCard через `ACTION_SEND` MIME `text/x-vcard` (подтверждено в pre-specify research; см. Telegram open-source `LaunchActivity.java`).
- **A-9** *(уточнён после code review 2026-05-15 C5)*: Существующие composable экраны переиспользуются как rendering pipeline, **но компоненты требуют значительного расширения**: TileCard (+`editMode`, +`onLongPress`, +`onEditMenuClick`, +варьируемая иконка по SlotKind), FlowScreen (+drag-and-drop infrastructure, +«+» кнопка для добавления плитки), BottomFlowBar (+«+» tab, +удаление flow), HomeScreen (+mode-баннер). Это **не «полный перерендер»**, но и **не «просто mode flag»** — это значительное расширение API существующих компонентов. См. FR-005a/FR-046. Drag-and-drop остаётся ~1-2 недели работы (FR-008).
- **A-10**: `schemaVersion = 1` для `/config` и `/config/history/*` стабилен на протяжении эпохи спека 9. Первый bump — отдельный спек.

---

## Implementation hints (code-level TODOs для `/speckit.plan` и `/speckit.tasks`)

> Эти inline-TODO должны быть **расставлены в коде** при имплементации. Они формируют exit ramps и server-migration маршруты. Каждый — конкретное место + комментарий.

| Где разместить TODO | Содержание TODO | Maps to |
|---|---|---|
| `EditorRendererDensity.kt` (на способе расчёта tile size) | `// TODO(spec-009 exit ramp): pixel-accurate render под разрешение Managed если density mismatch ухудшает UX. Требует поля screenWidthDp/heightDp в /state.` | FR-005, OUT-003 |
| `PhoneHealthPreset.kt` (на data class) | `// TODO(spec-followup TODO-ARCH-010): load from /config.presetOverrides.phoneHealthSettings когда поедем на configurable thresholds. Сейчас — захардкоженный single default.` | FR-019, TODO-ARCH-010 |
| `PhoneHealthPreset.kt` (на DEFAULT_ constant) | `// TODO(spec-followup TODO-ARCH-011): добавить MEDICAL_/MINIMUM_/... + selector через /config.presetOverrides.phoneHealthPresetId.` | TODO-ARCH-011 |
| `HealthToPhoneIndicatorAdapter.kt` (на конструкторе) | `// TODO(spec-followup): inject preset через DI из /config вместо инжекта DEFAULT_ напрямую.` | TODO-ARCH-010 |
| `PhoneHealthCriticalEventEmitter.kt` (где читается `preset.pushAdminOnCritical`) | `// TODO(server-roadmap SRV-MONITOR-001): subscriber на этот event + Worker call для FCM push админу.` | FR-021, OUT-009, SRV-MONITOR-001 |
| `ConfigHistoryRepository.kt` (на client-side write current → history) | `// TODO(server-roadmap SRV-CONFIG-001): этот write должен перейти на сервер ради integrity (race condition + spoofing).` | FR-037, OUT-015, SRV-CONFIG-001 |
| `ConfigHistoryRepository.kt` (на client-side housekeeping) | `// TODO(server-roadmap SRV-CONFIG-002): housekeeping должен стать server cron job. Сейчас — клиент при каждом push.` | FR-038, SRV-CONFIG-002 |
| `ConfigSnapshotReader.kt` (при чтении snapshot с старой schemaVersion) | `// TODO(spec-followup TODO-ARCH-015): apply transformer chain vN → vM. Сейчас — drop с пояснением.` | FR-043, OUT-018, TODO-ARCH-015 |
| `ContactAddedFlow.kt` (после успешного добавления контакта) | `// TODO(spec-followup TODO-LEGAL-001): privacy log — добавить в список «added contacts» доступный для удаления через admin Settings.` | FR-023..033, OUT-014, TODO-LEGAL-001 |
| `VCardParser.kt` (при отказе по отсутствию TEL) | `// TODO(spec-followup TODO-ARCH-014): когда добавим Contact без phone — пропускать таких через альтернативный flow (LINE/WeChat/KakaoTalk).` | FR-031, OUT-008 |
| `OpenAppDispatcher.kt` (на fallback в Play Store) | `// TODO(spec-followup): web fallback `https://play.google.com/store/apps/details?id=...` при отсутствии Play Store на устройстве (нужно для не-GMS Huawei).` | FR-035 |
| `EditorContactPickerScreen.kt` (где `READ_CONTACTS` request) | `// TODO(spec-followup TODO-LEGAL-001): полноценный privacy disclosure (FR-031a минимум сделан в спеке 9; полное GDPR/152-ФЗ — в backlog).` | FR-023, FR-033a, TODO-LEGAL-001 |
| `ConfigCurrentRoundtripTest.kt`, `ConfigSnapshotRoundtripTest.kt`, `VCardAdapterContractTest.kt` (новые тесты в plan.md) | (нет inline-TODO; это **mandatory plan-level requirement** от wire-format checklist — 4 roundtrip теста: `/config` с `presetOverrides = null`, `/config` с non-null, `/config/history/{autoId}` envelope, VCard adapter contract) | wire-format checklist CHK010 |
| `firestore.rules` (subcollection rules для history) | `// TODO(server-roadmap SRV-CONFIG-001): когда переедем на server-side history writes — заменить client-write rules на server-only.` | FR-045a/b, SRV-CONFIG-001 |
| `AndroidManifest.xml` + `data_extraction_rules.xml` (backup exclusion) | (нет inline-TODO; FR-046a — straightforward XML config) | FR-046a |

---

## Open Questions *(resolved during /speckit.clarify 2026-05-15)*

- ~~Q-OPEN-1~~ → **RESOLVED**: «История» — **отдельный полноэкранный экран** (`HistoryScreen`), не bottom sheet / sidebar. См. FR-039.
- ~~Q-OPEN-2~~ → **RESOLVED**: VCard-receiving Activity использует `launchMode="singleTask"` + `onNewIntent()`. См. FR-027a.
- ~~Q-OPEN-3~~ → **DEFERRED to plan.md**: Точные dp размеры (tile / preview / severity-icon) — это implementation detail, не spec-level. Spec требует «senior-safe tap target ≥ 56 dp» (Article VIII override наследуется из спека 4). Точные числа задаются в `plan.md` дизайн-таблице.

---

## Что внутри (TL;DR на русском)

Спек 9 — это **admin-режим**: то, что админ делает со своим телефоном для **дистанционной настройки бабушкиного телефона**.

**Главное:** одно приложение, разные конфиги. Админ запускает то же приложение, что бабушка, но в режиме редактирования её конфига. Никаких отдельных рендереров.

**Что admin может делать (7 user stories):**

1. **(P1)** Открыть список своих paired бабушек/дедушек, тапнуть → попасть в редактор раскладки одной конкретной → перетаскивать плитки, добавлять, удалять, переключать темы (preset) → опубликовать.
2. **(P1)** Видеть здоровье каждого устройства: батарея, интернет, отключённый звонок (важно!), как давно бабушка выходила. Критические значения подсвечиваются красным.
3. **(P2)** Добавлять контакт через системный picker Android (с разрешением READ_CONTACTS на админ-устройстве; на бабушкином — НЕ нужно).
4. **(P2)** Поделиться контактом из WhatsApp/Telegram/Viber в наше приложение → попадёт в раскладку бабушки. Закрытые мессенджеры (LINE/WeChat/KakaoTalk) — позже.
5. **(P2)** Если ошибся — открыть «Историю» → выбрать одну из последних 10 версий → откатить.
6. **(P3)** Бабушка может через Settings (7 тапов + пароль) открыть **тот же** редактор для своего конфига.
7. **(P3)** Создать плитку «Яндекс Карты» → если у бабушки установлено → запустит; нет → откроет Play Store страницу.

**Что architecturально:**
- Phone health = severity-классификация (Info / Warning / Critical), threshold'ы захардкожены в одном data class (готово к редактированию в будущем).
- Раскладка editor = тот же рендерер + edit mode. Drag-and-drop + параллельная кнопка для accessibility.
- History — client-side writes (race condition acceptable, migration на сервер запланирована).
- Schema transformers — на будущее, сейчас работы 0.
- `presetOverrides` — forward-compat поле в wire format, всегда null в этом спеке.

**Что НЕ входит** (20 пунктов OUT-001..020): screen mirroring, iOS, pixel-accurate render, mулти-Managed редактирование, preset-editor, часы, сигнализация, LINE/WeChat/KakaoTalk, push админу при Critical, configurable thresholds UI, contact drift detection, shared contact book, полный privacy compliance, server-side history writes, app version compatibility, runtime commands от admin, schema transformers, complex retention, diff-view.

**Ключевые backlog'и для будущего:**
- `TODO-ARCH-010..015` — расширения этого спека.
- `TODO-FUTURE-SPEC-001..005` — будущие спеки.
- `TODO-LEGAL-001` — privacy compliance до production.
- `SRV-CONFIG-001/002`, `SRV-MONITOR-001` — миграция на собственный сервер.

**Размер**: 46 FR, 8 SC, 20 OUT, 16 pre-resolved clarifications, 7 user stories, 12 implementation-hint TODOs.

**Следующие шаги**: `/speckit.clarify` (поиск missed grey zones) → `/speckit.plan` (архитектура + constitution-check) → `/speckit.tasks` (декомпозиция) → `/speckit.analyze` (финальная сверка) → имплементация.

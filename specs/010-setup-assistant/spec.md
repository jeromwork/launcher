# Feature Specification: Setup Assistant and Launcher Bootstrap

**Feature Branch**: `010-setup-assistant`
**Created**: 2026-05-17
**Status**: Draft (mentor pre-specify session 2026-05-17)
**Input**: roadmap §Spec 010 ([docs/product/roadmap.md:241](../../docs/product/roadmap.md#L241)) + mentor pre-specify discussion 2026-05-17. Спек 10 — это **связующий** спек: закрывает блокер ARCH-016 (раскладка из mock → из `/config/current`), узаконивает ad-hoc-механики спеков 7/9 (7-tap admin-mode gate, paired-devices visibility), и добавляет тонкий слой onboarding'a (ROLE_HOME, POST_NOTIFICATIONS, custom call confirmation, tutorial overlay, soft-checks `!` индикатор). Без новых ценностных вертикалей — это **наведение порядка**.

---

## Контекст и цель спека

После спеков 3 → 9 на устройстве пользователя установлено почти всё нужное: provider capabilities (спек 6), pairing с родственником (спек 7), bidirectional config sync (спек 8), admin-mode редактирование (спек 9). Но **между этими слоями осталось 8 зазоров**, которые ни один из них не закрывает:

1. **Главный экран до сих пор рисуется из mock**. `FlowRepository` читает `flows_mock.json` (спек 3), а **не** `/config/current` (спек 8). Admin push'ит — бабушка не видит. Это **блокер `TODO-ARCH-016`** в backlog.
2. **Без `ROLE_HOME` не лончер**. Если пользователь не дал нашему приложению быть home-launcher'ом, оно не перехватывает Home button — весь смысл потерян.
3. **Без `POST_NOTIFICATIONS` (Android 13+) не работает FCM**. Push-уведомления спека 7 не доставляются.
4. **`ACTION_DIAL` слишком много шагов для пожилого**. Тап на тайл → системный dialer → ещё одна кнопка ПОЗВОНИТЬ → звонок. Нужно: тап → наше окно подтверждения → одной кнопкой звонок.
5. **7-tap admin-mode gate (спек 9 T132) появился ad-hoc**. Описано как «7-tap + password gate», но password-механика не зафиксирована. Где хранится PIN, как set'ится, recovery, дефолт — открытые вопросы.
6. **Список paired-устройств в Settings — нет**. Спек 7 закрыл pairing, но UI «у меня есть N связей, могу разорвать каждую» не сделан.
7. **Soft-checks не централизованы**. Каждый спек сам решает, как проверять «всё ли настроено». Нет единого `!` индикатора в Settings со списком невыполненных требований.
8. **Бабушка не знает, как войти в Settings**. Лончер скрывает Settings (стандартный паттерн для elderly), но без подсказки пользователь не найдёт точку входа.

**Ключевой архитектурный принцип** (зафиксирован в mentor session): **устройство играет обе роли одновременно**. Нет role-picker'a Managed/Admin. Одно и то же устройство может одновременно быть admin'ом для одних paired-устройств и managed для других. N pairing'ов параллельно — нормальный режим.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Раскладка бабушки приходит с устройства внука (Priority: P1)

Внук в admin-режиме (спек 9) меняет раскладку бабушкиного телефона: добавляет плитку «Маша», переставляет «Аптеку». Нажимает «Опубликовать». Бабушка через ≤ 10 секунд видит обновлённую раскладку **на своём главном экране**, без перезапуска приложения. Если интернета нет — бабушка видит **последнюю применённую** раскладку (а не пустой экран и не mock'и спека 3).

**Why this priority**: без US-1 спеки 7/8/9 функционально пусты — admin редактирует в вакууме, бабушка видит mock. Это блокер всего продукта.

**Independent Test**: pair admin↔Managed (спек 7), admin меняет один параметр через UI спека 9, push → Managed home screen обновился. Сепарирующий критерий: до спека 10 — раскладка бабушки **не меняется** (mock); после — меняется.

**Acceptance Scenarios**:

1. **Given** admin и Managed paired, оба online, Managed на главном экране, admin меняет порядок плиток и push'ит, **When** push доставлен (спек 7 FCM), **Then** Managed-главный экран перерисовывается с новым порядком в течение ≤ 10 сек, без действий пользователя.
2. **Given** Managed offline (airplane mode), последняя применённая раскладка содержит 4 плитки, **When** Managed cold-start (process killed → перезапуск), **Then** главный экран показывает те же 4 плитки из локальной копии (Room-backed), без сетевого запроса, в течение ≤ 1 сек до первого кадра.
3. **Given** Managed только что был paired, никакой раскладки ещё не было push'нуто, **When** Managed заходит на главный экран, **Then** показывается раскладка выбранного preset'a (`workspace` / `simple-launcher` / `launcher` — спек 3 default), без mock-данных `flows_mock_*.json`.
4. **Given** admin в режиме редактирования раскладки бабушки, тапает на плитку (preview-tap, FR-005 спека 9), **When** срабатывает SlotToActionMapper, **Then** действие плитки разрешается (звонок Маше / открытие WhatsApp / открытие YouTube) — на admin-устройстве оно **только показывается** в preview-режиме (не запускает реальный звонок Маше).

---

### User Story 2 — Бабушка делает наш лончер главным (Priority: P1)

При первом запуске после установки бабушка проходит wizard: выбор языка (есть в спеке 3), выбор preset'a (есть в спеке 3) → **новый шаг**: «Сделать этот лончер главным». Если согласилась — системный диалог `RoleManager` (ROLE_HOME) → выбрала наш лончер → теперь Home button открывает наш экран. Если отказалась — приложение работает, но Home button открывает чужой лончер. В Settings видит `!` индикатор «Лончер не главный — настроить».

**Why this priority**: без ROLE_HOME мы не лончер, продукт теряет смысл. Это P1 для целевого use case'a.

**Independent Test**: установить APK на чистое устройство, пройти first-launch wizard, выбрать «сделать главным» → нажать Home → наш экран открывается. Альтернативно: отказаться → Settings показывает `!` → тап → системный диалог снова → теперь принять.

**Acceptance Scenarios**:

1. **Given** свежеустановленное приложение, прошёл preset-picker, **When** wizard показывает «Сделать главным», бабушка нажимает ДА, **Then** `RoleManager.createRequestRoleIntent(ROLE_HOME)` открывает системный диалог; после выбора нашего приложения — Home button открывает наш `HomeActivity`.
2. **Given** бабушка отказалась от ROLE_HOME, **When** заходит в Settings (через подсказку US-8 или 7-tap), **Then** в верхней части Settings виден баннер `!` «Лончер не главный — без этого Home button работает по-другому. Настроить?» с кнопкой «Настроить».
3. **Given** бабушка тапает «Настроить» на баннере, **When** срабатывает деep-link, **Then** снова открывается системный диалог `RoleManager` — она может выбрать наш лончер.
4. **Given** в системе уже выбран другой лончер главным, **When** бабушка ставит наш и принимает ROLE_HOME, **Then** наше приложение становится default home **без перезапуска устройства**.

---

### User Story 3 — Бабушка звонит Маше одной кнопкой (Priority: P1)

Бабушка тапает на плитку «Маша» на главном экране. Открывается **наше** окно: крупное фото Маши, имя «Маша внучка», номер «+7 916 123-45-67», две большие кнопки: **ОТМЕНА** (серая) и **ПОЗВОНИТЬ** (зелёная). Нажимает ПОЗВОНИТЬ. Звонок начинается **сразу** (без второго экрана), бабушка слышит гудки. Если приложение никогда раньше не получало `CALL_PHONE` permission — при первой попытке показывается стандартный rationale «Чтобы звонок начинался сразу одной кнопкой» → запрос → если granted → `ACTION_CALL` → звонок. Если denied — fallback на `ACTION_DIAL` (открывается системный dialer с предзаполненным номером).

**Why this priority**: для пожилой целевой аудитории «1 кнопка → звонок» — это **основной use case** лончера. Двойное подтверждение системным dialer'ом — путает.

**Independent Test**: установить, granted `CALL_PHONE`, тапнуть плитку Call → одно окно подтверждения → ПОЗВОНИТЬ → звонок сразу. Сравнительный: отозвать `CALL_PHONE` → повторить → fallback на системный dialer (но окно подтверждения всё равно открывается).

**Acceptance Scenarios**:

1. **Given** `CALL_PHONE` granted, плитка `kind = Call` с `contactId → Маша`, **When** бабушка тапает плитку, **Then** открывается полноэкранное senior-safe окно с фото (если есть), именем, номером, кнопками ОТМЕНА/ПОЗВОНИТЬ. Кнопки занимают ≥ 56dp каждая.
2. **Given** окно подтверждения открыто, **When** бабушка нажимает ПОЗВОНИТЬ, **Then** запускается `ACTION_CALL` с указанным номером, начинается звонок без других экранов.
3. **Given** окно подтверждения открыто, **When** бабушка нажимает ОТМЕНА (или системную «назад»), **Then** окно закрывается, возврат на главный экран, никаких побочных эффектов.
4. **Given** `CALL_PHONE` denied (или never asked), **When** бабушка нажимает ПОЗВОНИТЬ в окне подтверждения, **Then** запускается `ACTION_DIAL` — открывается системный dialer с предзаполненным номером, бабушка нажимает зелёную кнопку для звонка.
5. **Given** `CALL_PHONE` ранее denied permanently, **When** бабушка опять тапает плитку Call, **Then** окно подтверждения всё равно открывается, по ПОЗВОНИТЬ — fallback на `ACTION_DIAL` (без повторного запроса permission), в Settings виден `!` «Звонок одной кнопкой не работает — нужно разрешение».
6. **Given** плитка `kind = WhatsAppCall` или `WhatsAppMessage`, **When** бабушка тапает, **Then** открывается **то же** окно подтверждения (вид адаптирован: иконка WhatsApp вместо телефона); по ПОЗВОНИТЬ — deep-link `wa.me/<phone>` (не требует никаких permission).

---

### User Story 4 — Push-уведомления доставляются бабушке (Priority: P1)

При first-launch на Android 13+ бабушка получает запрос `POST_NOTIFICATIONS` (стандартный системный диалог) с rationale-экраном перед ним «Чтобы внук видел, что у тебя всё в порядке». Если granted — push'и спека 7 (admin отвязал, admin меняет конфиг, низкий заряд) доставляются как обычные системные уведомления. Если denied — `!` индикатор в Settings.

**Why this priority**: без `POST_NOTIFICATIONS` основной канал коммуникации admin→Managed (FCM, спек 7) не работает на современных устройствах. Это P1 для функционирования pairing'a.

**Independent Test**: установить на Android 13+ устройство, пройти wizard, granted permission → admin push'ит pairing-уведомление → бабушка видит push в шторке.

**Acceptance Scenarios**:

1. **Given** Android 13+ свежеустановленное приложение, **When** бабушка первый раз заходит на главный экран после wizard'a, **Then** показывается rationale-экран про push'и → нажатие «Разрешить» → системный диалог.
2. **Given** бабушка нажала «Не разрешать», **When** заходит в Settings, **Then** виден `!` баннер «Уведомления выключены — внук не сможет тебе писать. Включить?» с кнопкой → deep-link в `ACTION_APP_NOTIFICATION_SETTINGS`.
3. **Given** Android < 13, `POST_NOTIFICATIONS` не существует, **When** wizard проходит, **Then** этот шаг **пропускается**, никакого rationale не показывается; в Settings нет `!` про push'и.

---

### User Story 5 — Бабушка видит список устройств, которые ей помогают (Priority: P2)

Бабушка заходит в Settings → раздел «Сопряжённые устройства» → видит **два списка**:
- «Кто помогает мне» — список admin-устройств (паспорт: «Телефон Маши, привязано 2026-04-10»); рядом — кнопка «Прекратить помощь» (disconnect).
- «Кому я помогаю» — список managed-устройств (если у бабушки есть свой бабушка, кому она admin — обычно пусто, но архитектурно возможно).

По каждому disconnect — подтверждение «Прекратить? Маша больше не сможет менять твою раскладку».

**Why this priority**: спек 7 закрыл pairing, но без UI «вот мои связи, могу разорвать» бабушка zaложник admin'a. Это privacy / agency требование. P2 — потому что в спеке 7 уже есть базовый unbind через QR-screen, спек 10 расширяет до полного списка.

**Independent Test**: pair 2 устройства (например, телефон Маши + планшет Маши), бабушка → Settings → видит оба → disconnect одного → остался один → admin тестируется отдельно (один работает, другой нет).

**Acceptance Scenarios**:

1. **Given** бабушка paired с двумя admin-устройствами, **When** открывает Settings → «Сопряжённые устройства», **Then** видит два пункта в списке «Кто помогает мне», каждый с именем устройства и датой привязки.
2. **Given** бабушка тапает «Прекратить помощь» у одного admin'a, **When** подтверждает в диалоге, **Then** `LinkRegistry.deactivate(linkId)` вызывается (спек 7), запись `/links/{linkId}` помечается revoked, push admin'у уходит; admin теряет доступ к её `/config`.
3. **Given** у бабушки 0 paired-устройств, **When** открывает «Сопряжённые устройства», **Then** видит пустое состояние «Никто пока тобой не помогает — попроси внука отсканировать QR-код» с кнопкой «Показать QR» (запускает flow спека 7).
4. **Given** бабушка одновременно admin для какого-то устройства (редко, но возможно), **When** открывает «Сопряжённые устройства», **Then** видит **два раздела**: «Кто помогает мне» (1) и «Кому я помогаю» (1), оба с disconnect-кнопками.

---

### User Story 6 — Soft-checks `!` индикатор в Settings (Priority: P2)

В Settings рядом с заголовком — иконка `!` с числом невыполненных требований (например `!3`). Тап → экран «Что не настроено»: список с описанием каждого нерешённого вопроса и кнопкой «Настроить» / «Подробнее». Required checks: ROLE_HOME (US-2), POST_NOTIFICATIONS (US-4), CALL_PHONE (опционально, для одной-кнопки-звонка US-3), наличие хотя бы одного pairing'a (если пользователь явно не отказался). Recommended checks: network online, GMS available, battery optimization disabled.

**Why this priority**: централизованная видимость «всё ли в порядке» нужна и admin'y (увидеть проблемы своего устройства), и бабушке (понять, что не так, когда что-то не работает). P2 — useful, но не блокирует основной use case.

**Independent Test**: на чистом устройстве сразу после установки `!` показывает большое число (5-6 нерешённых). По мере прохождения wizard'a счётчик уменьшается. После полной настройки — `!` исчезает.

**Acceptance Scenarios**:

1. **Given** свежеустановленное приложение, никакие permission не granted, ROLE_HOME не выбран, **When** бабушка открывает Settings, **Then** рядом с заголовком виден `!N` где N = число невыполненных required checks (≥ 2).
2. **Given** в списке «Что не настроено» виден пункт «Лончер не главный», **When** бабушка тапает «Настроить», **Then** срабатывает deep-link на `RoleManager.createRequestRoleIntent(ROLE_HOME)`.
3. **Given** все required checks выполнены, остался один recommended (например, battery optimization включён), **When** бабушка открывает Settings, **Then** `!` **не** показывается рядом с заголовком (recommended считаются отдельно или показываются меньшим шрифтом).
4. **Given** новый check добавлен в реестр (extensibility — будущий спек может добавить свой check), **When** check возвращает `Status.Required`, **Then** он автоматически появляется в списке и в счётчике `!`, без модификации UI Settings.

---

### User Story 7 — Защита от случайного входа в admin-mode (PIN опциональный) (Priority: P2)

Бабушка случайно тапнула в скрытой области 7 раз → попала в admin-mode (US-6 спека 9). По дефолту — никакого PIN не запрашивается, она сразу в редакторе раскладки. **Чтобы это было безопаснее**, после первых **3 входов** в admin-mode (через 7-tap) показывается баннер «Хочешь установить пароль, чтобы случайные тапы не открывали этот экран?» с кнопками: «Установить пароль» / «Позже» / «Больше не показывать». PIN — локальный (хранится только на этом устройстве), не синхронизируется в облако.

**Why this priority**: без защиты бабушка может случайно поломать раскладку (в спеке 9 admin-mode полностью функционален). Но **обязательный** PIN с первого запуска отпугнул бы пожилых. Прогрессивный подход — балансирует UX и безопасность. P2.

**Independent Test**: войти 3 раза через 7-tap → на 4-м входе должен появиться баннер с предложением. Установить PIN → выйти → попробовать 7-tap → теперь после 7 тапов запрашивается PIN.

**Acceptance Scenarios**:

1. **Given** PIN не установлен, бабушка тапнула 7 раз в скрытой области, **When** срабатывает gate, **Then** admin-mode открывается **сразу** (без запроса PIN); счётчик `adminModeEntryCount` инкрементируется.
2. **Given** PIN не установлен, `adminModeEntryCount == 3` после 3-го входа, **When** бабушка входит в 4-й раз, **Then** перед открытием admin-mode показывается баннер «Установить пароль?» с тремя кнопками.
3. **Given** баннер открыт, бабушка нажала «Установить пароль», **When** проходит мастер установки PIN (ввод 4 цифры → подтверждение → сохранение в `EncryptedSharedPreferences`), **Then** PIN установлен; admin-mode открывается.
4. **Given** PIN установлен (4 цифры), бабушка тапнула 7 раз, **When** срабатывает gate, **Then** показывается экран ввода PIN с цифровой клавиатурой; правильный → admin-mode; неправильный 3 раза подряд → блокировка на 1 минуту.
5. **Given** бабушка забыла PIN, **When** в Settings открывает «Сопряжённые устройства» → «Прекратить помощь» (US-5) → единственный способ сброса, **Then** после disconnect всех pairing'ов admin-mode становится бесполезен (нет `/config` для редактирования); при следующем re-pairing PIN сбрасывается.
6. **Given** бабушка нажала «Больше не показывать», **When** входит в admin-mode 10 раз позже, **Then** баннер не показывается, PIN не запрашивается (по её собственному решению).

---

### User Story 8 — Подсказка «как войти в Settings» (Priority: P2)

Лончер для пожилых **намеренно скрывает** Settings (нет иконки на главном экране — это паттерн simple-launcher). Чтобы бабушка могла найти Settings (например, чтобы переключить язык), при первых **2 запусках приложения** показывается полупрозрачный overlay с указателем на скрытую область + крупным шрифтом «Чтобы открыть настройки, коснись здесь 7 раз». Закрывается кнопкой «Понятно». Если за 7 дней пользователь ни разу не открыл Settings — overlay показывается снова (один раз).

**Why this priority**: без подсказки бабушка не знает, как настроить даже базовые вещи (язык, который выбрал внук). P2 — useful, но не блокирует основной use case (внук может настроить за неё через admin-mode).

**Independent Test**: install → cold start → overlay показан → закрыть → cold start ещё раз → overlay показан повторно (счётчик 2) → закрыть → cold start третий раз → overlay **не** показан (счётчик исчерпан). Подождать 7 дней без открытия Settings → overlay показан снова.

**Acceptance Scenarios**:

1. **Given** свежеустановленное приложение, первый cold start главного экрана, **When** экран отрисован, **Then** через 1.5 сек появляется полупрозрачный overlay с указателем и текстом, кнопка «Понятно».
2. **Given** бабушка нажала «Понятно», **When** перезапускает приложение (2-й cold start), **Then** overlay появляется снова, счётчик показов = 2.
3. **Given** 3-й cold start, **When** экран открывается, **Then** overlay **не** появляется (счётчик исчерпан); persistent `tutorialShown = true`, `lastSettingsOpenAt = null`.
4. **Given** прошло 7 дней с момента 2-го показа, `lastSettingsOpenAt = null` (Settings ни разу не открывали), **When** очередной cold start, **Then** overlay показывается ещё раз; `tutorialReshownAt = now`.
5. **Given** бабушка открыла Settings хотя бы раз (через 7-tap), **When** проходит 7 дней без активности, **Then** overlay **не** показывается повторно — пользователь уже знает путь.
6. **Given** TalkBack включён в системе, **When** условия показа overlay срабатывают, **Then** вместо визуального overlay воспроизводится голосовое сообщение через `AccessibilityManager` («Чтобы открыть настройки, коснитесь правого верхнего угла семь раз»); visual overlay не показывается, чтобы не дублировать.

---

### Edge Cases

- **ARCH-016 + race condition**: admin push'ит изменение **прямо в момент** когда Managed cold-start'ует. Что показывается — старая Room-версия или дождаться Firestore? → Room сразу (≤ 1 сек), затем live update при получении push (≤ 10 сек). Никаких блокирующих экранов.
- **CALL_PHONE granted, но номер невалиден** (пустая строка, странные символы): `ACTION_CALL` бросит SecurityException или ничего не произойдёт. Окно подтверждения должно валидировать номер до показа кнопки ПОЗВОНИТЬ; если невалидно — кнопка disabled + сообщение «Номер некорректен».
- **ROLE_HOME отозван пользователем** (зашёл в системные Settings → выбрал другой лончер): при следующем запуске наш `!` индикатор снова поднимается.
- **Multiple Managed-устройств через 7-tap одновременно**: бабушка может быть в admin-mode редактируя своё устройство, и admin (внук) может в этот момент тоже редактировать её устройство. → конфликт-resolution спека 8 (FR-050 Merge UI) уже это покрывает.
- **PIN установлен, но `EncryptedSharedPreferences` повреждён** (rare, OEM-specific): при следующем 7-tap exception → fallback на «PIN не найден, открыть admin-mode без проверки» (за пределы спека 10) или **показать экран сброса** с кнопкой «Сбросить PIN через unbind». → Принимаем второй вариант, fail-safe.
- **Tutorial overlay vs first-launch wizard**: overlay показывается **после** того, как wizard завершён (бабушка на главном экране). До этого — wizard, без overlay.
- **POST_NOTIFICATIONS denied → user revokes from system Settings**: при следующем check (next app launch) → `!` индикатор обновляется. Не trying to re-request permission programmatically (Android 13+ блокирует).
- **Удаление `flows_mock_*.json`**: 3-5 Robolectric-тестов спека 3/4 опираются на эти файлы (см. T072 спека 3, тесты Activity'ев). Должны быть переписаны на `FakeRemoteSyncBackend` (как делает спек 7 E2E) **в той же phase**, что и ARCH-016.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Part A — ARCH-016 closure (US-1)

- **FR-001**: System MUST рендерить home tiles (`HomeScreen` → `FlowScreen` → `TileCard`) из `ConfigEditor.appliedConfig` (Room-backed observable, спек 8 FR-041), а не из `FlowRepository` (mock, спек 3).
- **FR-002**: System MUST хранить локальную копию `appliedConfig` так, чтобы cold-start главного экрана происходил без сетевого запроса в течение ≤ 1 сек до первого кадра.
- **FR-003**: System MUST переводить `Slot` (модель спека 8) в `Action` (модель спека 5) через `SlotToActionMapper`, разрешая `contactId` references против `/config.contacts[]`.
- **FR-004**: System MUST удалить `flows_mock_*.json` файлы и связанный `MockFlowRepository` после миграции; ни один code path не должен от них зависеть.
- **FR-005**: Если устройство не paired, `appliedConfig` MUST возвращать раскладку выбранного preset'а (`workspace` / `simple-launcher` / `launcher`) как initial state — функционал спека 3 preset-picker сохраняется.
- **FR-006**: При admin push в `/config` Managed MUST применить изменения в течение ≤ 10 сек (T-PUSH спека 7) без действия пользователя.

#### Part B — Onboarding wizard расширение

- **FR-007**: First-launch wizard (`FirstLaunchActivity`, спек 3) MUST содержать **новый шаг** «Сделать лончер главным» после preset-picker'a: кнопка → `RoleManager.createRequestRoleIntent(ROLE_HOME)`. Пропускаемый (кнопка «Позже»).
- **FR-008**: На Android 13+ first-launch wizard MUST содержать **новый шаг** «Разрешить уведомления»: rationale → системный запрос `POST_NOTIFICATIONS`. На Android < 13 шаг автоматически пропускается.
- **FR-009**: Wizard MUST НЕ содержать role-picker (Managed vs Admin) — устройство играет обе роли симметрично.
- **FR-010**: Wizard MUST НЕ содержать pairing-предложение — pairing доступен через Settings (спек 7 UI).

#### Part C — Call confirmation dialog (US-3)

- **FR-011**: При тапе на тайл `kind = Call` System MUST открывать **наше** полноэкранное окно подтверждения с: фото контакта (если есть), именем, форматированным номером, двумя кнопками — ОТМЕНА и ПОЗВОНИТЬ. Кнопки ≥ 56dp каждая (Article VIII senior-safe override).
- **FR-012**: По кнопке ПОЗВОНИТЬ — если `CALL_PHONE` granted, System MUST запустить `Intent(ACTION_CALL, "tel:$number")` напрямую без других экранов. Если denied — fallback на `Intent(ACTION_DIAL, "tel:$number")` (текущее поведение спека 5 T541).
- **FR-013**: При первой попытке тапа на Call-тайл (если permission never asked) System MUST показать rationale-экран «Чтобы звонок шёл сразу одной кнопкой» с кнопкой «Разрешить» → системный запрос permission. Отказ → fallback на `ACTION_DIAL` (без блокировки feature'a).
- **FR-014**: При тапе на тайл `kind = WhatsAppCall` / `WhatsAppMessage` System MUST открывать **то же** окно подтверждения; по ПОЗВОНИТЬ — deep-link `https://wa.me/<phone>` (не требует permission'ов).
- **FR-015**: Окно подтверждения MUST валидировать номер до показа кнопки ПОЗВОНИТЬ; невалидный номер → кнопка disabled + текст «Номер некорректен».
- **FR-016**: Из окна подтверждения по системной кнопке «назад» или ОТМЕНА — возврат на главный экран без побочных эффектов; pending-changes баннер спека 8 (если был) сохраняется.

#### Part D — Soft-checks engine (US-6)

- **FR-017**: System MUST содержать в `core/commonMain/api/setup/` port `SetupCheck` со следующим контрактом: `id: String`, `criticality: Criticality { Required, Recommended }`, `check(): Status { Ok, NotConfigured(reason) }`, `resolveIntent(): IntentSpec?` (decoupled от Android-типов).
- **FR-018**: System MUST содержать реестр `SetupCheckRegistry` со следующими дефолтными checks: `RoleHomeCheck` (Required, **первый в списке**), `PostNotificationsCheck` (Required, Android 13+), `CallPhoneCheck` (Recommended), `NetworkOnlineCheck` (Recommended), `GmsAvailableCheck` (Recommended), `BatteryOptimizationCheck` (Recommended).
- **FR-019**: Settings screen MUST показывать иконку `!N` рядом с заголовком, где `N` = число невыполненных **Required** checks. Если `N == 0` — иконка не показывается.
- **FR-020**: Settings → «Что не настроено» — отдельный экран со списком невыполненных checks (Required first, Recommended ниже), у каждого — описание + кнопка «Настроить» (запускает `resolveIntent()`).

#### Part E — PIN + 7-tap gate (US-7)

- **FR-021**: System MUST узаконить 7-tap admin-mode gate из спека 9 T132: 7 быстрых тапов на скрытую область (правый верхний угол главного экрана, не визуализирован) в течение ≤ 5 секунд → переход в admin-mode.
- **FR-022**: По дефолту (PIN не установлен) переход в admin-mode MUST быть **немедленным** — без запроса PIN или password. Это упрощение T132 спека 9 (password в дефолтном пресете не обязателен).
- **FR-023**: System MUST хранить локальный счётчик `adminModeEntryCount` в `EncryptedSharedPreferences`. При значениях 3, 6, 9 (первые 3 входа после reset) System MUST показывать баннер перед открытием admin-mode: «Установить пароль, чтобы случайные тапы не открывали этот экран?» с кнопками: «Установить» / «Позже» / «Больше не показывать».
- **FR-024**: Установка PIN — 4 цифры, ввод дважды (новый + подтверждение), хранится в `EncryptedSharedPreferences` (NOT в `/config` — локальный, не remote-managed).
- **FR-025**: Если PIN установлен, после 7-tap System MUST показывать экран ввода PIN с цифровой клавиатурой. 3 неверных подряд → блокировка на 1 минуту с обратным отсчётом.
- **FR-026**: PIN recovery: System MUST не предоставлять прямой механизм сброса PIN. Единственный путь — disconnect всех pairing'ов через Settings → re-pair → at re-pairing PIN сбрасывается. Это explicit one-way door (см. Assumptions).
- **FR-027**: Если `EncryptedSharedPreferences` повреждён или PIN-значение не читается, System MUST показывать **fail-safe экран сброса** с кнопкой «Сбросить PIN через unbind» (deep-link на Settings).
- **FR-028**: Когда пользователь нажал «Больше не показывать» в баннере (FR-023), System MUST установить persistent флаг и не показывать баннер впредь, пока пользователь явно не зайдёт в Settings → «Безопасность» → «Установить пароль».

#### Part F — Paired devices list (US-5)

- **FR-029**: Settings MUST содержать раздел «Сопряжённые устройства» с **двумя списками**: «Кто помогает мне» (paired-устройства, где это устройство — Managed) и «Кому я помогаю» (paired-устройства, где это устройство — Admin).
- **FR-030**: Каждый пункт списка MUST содержать: имя устройства (из `/links/{linkId}` метаданных спека 7), дату привязки, кнопку «Прекратить помощь».
- **FR-031**: По нажатию «Прекратить помощь» MUST показываться двухступенчатое подтверждение (Article VIII senior-safe destructive-action paradigm): «Прекратить помощь от Маши? Маша больше не сможет менять твою раскладку» → ДА/НЕТ.
- **FR-032**: При подтверждении System MUST вызывать `LinkRegistry.deactivate(linkId)` (спек 7); запись `/links/{linkId}` помечается revoked, push другой стороне уходит.
- **FR-033**: Если оба списка пусты, System MUST показывать empty-state «Никто пока тобой не помогает — попроси внука отсканировать QR-код» с кнопкой «Показать QR» (запускает QR-flow спека 7).

#### Part G — Tutorial overlay (US-8)

- **FR-034**: При первых 2 cold-start'ах главного экрана System MUST показывать полупрозрачный overlay (alpha ≤ 0.6) с указателем на правый верхний угол (зона 7-tap) и крупным текстом «Чтобы открыть настройки, коснись здесь 7 раз». Кнопка «Понятно» закрывает overlay.
- **FR-035**: System MUST хранить счётчик `tutorialOverlayShownCount` в `EncryptedSharedPreferences` (или regular DataStore — это не security-data). Default 0; инкрементируется при каждом показе.
- **FR-036**: Если `tutorialOverlayShownCount >= 2` И `lastSettingsOpenAt == null` (Settings ни разу не открыт) И прошло ≥ 7 дней с момента последнего показа, System MUST показать overlay ещё раз (один). Затем перестать показывать пока пользователь не откроет Settings.
- **FR-037**: При включённом TalkBack (через `AccessibilityManager.isTouchExplorationEnabled`) System MUST НЕ показывать визуальный overlay; вместо — голосовое сообщение через `AccessibilityManager.interrupt()` + `AccessibilityEvent.TYPE_ANNOUNCEMENT`: «Чтобы открыть настройки, коснитесь правого верхнего угла семь раз».
- **FR-038**: Overlay MUST показываться только **после** завершения first-launch wizard'a (пользователь на главном экране, не во время setup).

### Cross-cutting Requirements

- **FR-039**: Все новые user-facing strings MUST быть локализованы в `strings.xml` (en + ru) per ADR-004; никакого hardcoded русского/английского текста в Kotlin-коде.
- **FR-040**: Никакая wire-format модификация в этом спеке не повышает `schemaVersion`. Все добавления (если требуются) MUST быть additive nullable fields в `/config` (например, потенциальная будущая `adminPin` синхронизация — explicit out-of-scope FR-026).
- **FR-041**: Все новые ports (`SetupCheck`, `SlotToActionMapper`) MUST соответствовать CLAUDE.md rule 1 (domain isolation): living в `core/commonMain/api/`, без vendor/Android type leaks.

### Key Entities

- **SetupCheck** (port): `id: String`, `criticality: Criticality`, `check(): suspend () -> CheckStatus`, `resolveIntent(): IntentSpec?`.
- **CheckStatus** (sealed): `Ok`, `NotConfigured(reason: String)`.
- **IntentSpec** (data class): platform-agnostic descriptor для deep-link'a (например `{ category: "SETTINGS", action: "POST_NOTIFICATIONS_DETAILS" }`). Mapping → реальный `android.content.Intent` происходит в `:app/androidMain`.
- **SlotToActionMapper** (function in `core/commonMain/api/action/`): `fun Slot.toAction(contacts: List<Contact>): Action?`.
- **AdminModeGateState** (data class, locally persisted): `entryCount: Int`, `pinSet: Boolean`, `bannerDismissed: Boolean`, `lockoutUntilEpochMs: Long?`.
- **TutorialOverlayState** (data class, locally persisted): `shownCount: Int`, `lastShownAt: Instant?`, `lastSettingsOpenAt: Instant?`.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Admin push раскладки доставляется и применяется на главном экране Managed в течение ≤ 10 сек в 95% случаев (T-PUSH из спека 7).
- **SC-002**: Cold-start главного экрана Managed (с уже-applied конфигом из локальной копии) показывает первый кадр раскладки в течение ≤ 1 сек на medium-tier устройстве (Pixel 4a baseline).
- **SC-003**: Бабушка может позвонить контакту с главного экрана в **2 нажатия** (тап тайл + ПОЗВОНИТЬ) при granted `CALL_PHONE`, или в **3 нажатия** (тап + ПОЗВОНИТЬ + зелёная кнопка системного dialer'a) при denied permission.
- **SC-004**: 100% свежеустановленных приложений на чистом устройстве показывают `!N` индикатор в Settings с `N >= 2` (минимум: ROLE_HOME + POST_NOTIFICATIONS на Android 13+).
- **SC-005**: После полного прохождения first-launch wizard'a + grant всех Required permissions значение `N` снижается до 0 в 100% случаев на тестовом устройстве.
- **SC-006**: Бабушка находит вход в Settings (открывает их хотя бы раз) в течение первых 2 запусков приложения в 90% случаев (UX walkthrough на 5 пожилых пользователях).
- **SC-007**: При случайном тапе бабушкой 7 раз в скрытой области она попадает в admin-mode без неожиданности (баннер на 4-й раз даёт ей возможность установить защиту) — измеряется как «процент пользователей, которые после 3 случайных входов установили PIN или нажали "Больше не показывать"» ≥ 80%.
- **SC-008**: Удаление `flows_mock_*.json` не ломает CI: все unit/integration/Robolectric тесты спеков 3, 4, 5, 6, 7, 8, 9 продолжают зелёными после Phase 0.
- **SC-009**: APK size delta после спека 10 (Phase 0 ARCH-016 + soft-checks engine + новые экраны) ≤ +500 KB к release build спека 9.

### Out of Scope

- **OUT-001**: Role-picker (Managed vs Admin) — устройство играет обе роли симметрично через одну установку. Принцип «одно приложение, разные конфиги» из спека 9 распространяется.
- **OUT-002**: Pairing-предложение в setup-wizard — pairing доступен через Settings (UI спека 7); дублировать в wizard'е не нужно.
- **OUT-003**: Remote PIN management (admin меняет PIN на Managed через `/config`) — future-spec. PIN остаётся локальным.
- **OUT-004**: PIN-recovery без unbind — explicit невозможна (FR-026); защита от lockout-сценариев требует disconnect всех pairing'ов.
- **OUT-005**: Полная GDPR / 152-ФЗ pipeline для контактов — `TODO-LEGAL-001`. Минимум спека 9 (`ContactsManageScreen`) сохраняется.
- **OUT-006**: DPC / Device Owner provisioning — отдельный будущий ADR.
- **OUT-007**: App version compatibility detection и remote update — `TODO-ARCH-007`.
- **OUT-008**: Wearable / security-sensor мониторинг — `TODO-FUTURE-SPEC-001/002`.
- **OUT-009**: Diff-проверка при смене preset'а — была в исходном описании спека 10, но при детальном рассмотрении оказалась избыточной: preset-switcher уже есть в спеке 3, а soft-checks движок (FR-017..FR-020) автоматически перенаправит `!` индикатор после смены preset'а.
- **OUT-010**: Преcет-зависимость PIN-механики («simple-launcher без PIN, launcher с PIN») — рассматривалось в mentor session, решено: единое поведение для всех preset'ов (по дефолту без PIN, опционально с PIN через FR-023 баннер). Преcет-зависимость — `TODO-FUTURE-SPEC-005`.

---

## Assumptions

### Зависимости от других спеков

- **A-1**: Спек 8 (`bidirectional-config-sync`) уже реализовал `ConfigEditor.appliedConfig` как Room-backed observable (FR-041 спека 8). Спек 10 потребляет, не модифицирует.
- **A-2**: Спек 7 (`pairing-and-firebase-channel`) уже реализовал `LinkRegistry.deactivate(linkId)` и push-механику отзыва. Спек 10 потребляет UI'ем «Прекратить помощь».
- **A-3**: Спек 9 T132 (7-tap gate для Settings → «Редактировать раскладку») остаётся, но **password** становится опциональным. Спек 10 узаконивает gate-механику в `core/commonMain/api/gate/`.
- **A-4**: Спек 5 T541 (`PhoneHandler` → `ACTION_DIAL`) **расширяется** до conditional `ACTION_CALL` (если granted) / `ACTION_DIAL` (fallback). Изменение в `:app/androidMain`, не в спеке 5 как написано в tasks.md (это будет noted в спека 10 plan.md и в перекрёстной ссылке в спек 5).

### Архитектурные принципы

- **A-5**: Принцип «устройство играет обе роли» — каждое устройство одновременно может быть admin для одних paired-устройств и managed для других. N pairing'ов параллельно — нормальный режим, поддерживается спеком 7 архитектурно.
- **A-6**: PIN — explicit local-only (one-way door). Хранится в `EncryptedSharedPreferences` через `androidx.security.crypto`. Никогда не отправляется в Firebase / Cloudflare. Recovery — через unbind+re-pair, без email/SMS/secondary-channel.
- **A-7**: `CALL_PHONE` — explicit dangerous permission в Play Store Data Safety form. Обоснование: «one-tap calling for elderly users». Exit ramp — `ACTION_DIAL` fallback всегда работает без permission'а (FR-012).

### Технические допущения

- **A-8**: `AccessibilityManager.isTouchExplorationEnabled` — корректный сигнал для определения «TalkBack включён» в FR-037. Альтернативы (например, `Settings.Secure.ACCESSIBILITY_ENABLED`) могут быть рассмотрены при `/speckit.clarify`.
- **A-9**: Скрытая область для 7-tap — правый верхний угол (24dp × 24dp). Точная зона может быть уточнена при `/speckit.clarify`.
- **A-10**: `ConfigRefreshWorker` спека 8 продолжает работать при background-restrictions OEM-специфично (Samsung, Xiaomi, Huawei). Полная OEM-matrix — `TODO-DEVICE-002` спека 8.

---

## Cross-cutting concerns surfaced from mentor session

Эти 6 вопросов были озвучены в mentor pre-specify session 2026-05-17 как **adjacent concerns**. Зафиксированы здесь чтобы избежать повторных открытий при `/speckit.clarify`:

1. **CALL_PHONE one-way door**: explicit dangerous permission затрагивает Play Store Data Safety. Inline-TODO в `PhoneHandler` про exit ramp через `noCallPermission` build flavor — на случай если будущий маркет/правовая ситуация потребует.
2. **Call dialog vs spec 8 pending changes banner**: окно подтверждения звонка может открываться поверх pending-баннера спека 8 — не должно блокировать его. E2E smoke test должен это проверить (FR-016 implicit).
3. **PIN recovery через unbind**: единственный путь — FR-026. Это намеренно одно-нaправленная дверь для simplicity и security.
4. **ROLE_HOME ordering**: ROLE_HOME — **первый** Required check в реестре `SetupCheckRegistry` (FR-018). Без него весь смысл launcher'a пропадает; ARCH-016 без ROLE_HOME пользователем не видим.
5. **Tutorial overlay TalkBack-aware**: FR-037 предписывает голосовую подсказку вместо визуального overlay при включённом TalkBack — accessibility baseline для elderly с low vision.
6. **`flows_mock_*.json` deletion ломает тесты**: 3-5 Robolectric-тестов спеков 3/4 ссылаются на эти файлы (FR-004, SC-008). Переписать на `FakeRemoteSyncBackend` (как делает спек 7 E2E test) **в той же Phase**, что и ARCH-016 — не оставлять долгом.

---

## Затрагиваемые внешние артефакты

Эти артефакты вне `specs/010-*/` будут модифицированы спеком 10:

- **Спек 5 T541 `PhoneHandler`** — переход с `Intent(ACTION_DIAL, ...)` на conditional `Intent(ACTION_CALL, ...)` (granted) / `Intent(ACTION_DIAL, ...)` (fallback). Дописывается ссылка в [specs/005-action-architecture-v2/tasks.md](../005-action-architecture-v2/tasks.md) на спек 010 FR-012.
- **[docs/compliance/permissions-and-resource-budget.md](../../docs/compliance/permissions-and-resource-budget.md)** — добавление `CALL_PHONE` в раздел Requested permissions с обоснованием «one-tap calling for elderly users».
- **[app/src/main/AndroidManifest.xml](../../app/src/main/AndroidManifest.xml)** — `<uses-permission android:name="android.permission.CALL_PHONE" />`.
- **Спек 9 T132** — узаконивание 7-tap без обязательного password. Cross-reference в спек 9 tasks.md.
- **[docs/dev/project-backlog.md](../../docs/dev/project-backlog.md)** — закрытие `TODO-ARCH-016` (статус → DONE, ссылка на коммиты спека 10).
- **[docs/product/roadmap.md](../../docs/product/roadmap.md)** §Spec 010 — обновление описания (старое roadmap-описание устарело после mentor session).

---

## Краткое содержание простым русским языком *(для не-разработчика)*

Этот спек не добавляет «новых функций» в продукт. Он **доделывает** четыре вещи, которые висели после предыдущих спеков:

**Главное (P1):**
1. **Раскладка бабушки теперь приходит с телефона внука.** До спека 10 внук менял раскладку в редакторе, нажимал «Опубликовать», бабушка ничего не видела — на её главном экране была заглушка. После спека 10 — admin меняет, бабушка через 10 секунд видит.
2. **Наш лончер становится главным.** Без этого Home button у бабушки не открывает наш экран. При первом запуске спрашиваем «сделать главным?», и потом в настройках напоминаем, если отказалась.
3. **Уведомления.** На Android 13+ Google требует, чтобы пользователь сам разрешил приложению показывать уведомления. Без этого внук не сможет получить сигнал «у бабушки 3% батарея». Добавляем явный запрос при первом запуске.
4. **Звонок одной кнопкой.** Сейчас бабушка тапает плитку «Маша» → открывается системный экран дозвона → она ещё раз нажимает зелёную кнопку. После спека 10 — тап → наше окно «Позвонить Маше? [ОТМЕНА] [ПОЗВОНИТЬ]» → одна кнопка → звонок.

**Не главное, но полезное (P2):**
5. **Список «кто мной управляет» в настройках** с кнопкой «прекратить помощь» — для приватности.
6. **Значок `!` в настройках**, который показывает «у тебя не настроены такие-то вещи» — централизованная проверка.
7. **Защита от случайного входа в admin-режим**: после 3 случайных нажатий бабушка получает баннер «не хочешь поставить пароль?». По умолчанию — пароля нет (чтобы не пугать).
8. **Подсказка «как зайти в настройки»**: на старте показываем полупрозрачную подсказку «нажми сюда 7 раз», первые 2 запуска. Если пользователь с озвучкой экрана (TalkBack) — голосовое сообщение вместо визуальной подсказки.

**Что НЕ входит** (специально отсечено в pre-specify обсуждении): выбор «ты бабушка или внук» при первом запуске (не нужен — одно устройство может играть обе роли), предложение pair'a в визарде (есть в Settings), удалённый PIN-менеджмент, юридические тонкости по GDPR (это отдельный спек), управление через DPC.

**Зачем это делать сейчас**: без п. 1 предыдущие спеки 7-8-9 функционально пусты (внук редактирует, бабушка не видит). Без п. 2-3 продукт не работает как лончер на современном Android. Это «замазать щели в стене перед сдачей дома».

# Feature Specification: Setup Assistant and Launcher Bootstrap

**Feature Branch**: `010-setup-assistant`
**Created**: 2026-05-17
**Status**: Draft post-clarify (2026-05-19)
**Input**: roadmap §Spec 010 ([docs/product/roadmap.md:241](../../docs/product/roadmap.md#L241)) + mentor pre-specify discussion 2026-05-17 + clarify session 2026-05-19. Спек 10 — это **связующий** спек: закрывает блокер ARCH-016 (раскладка из mock → из `/config/current`), узаконивает ad-hoc-механики спеков 7/9 (7-tap admin-mode gate, paired-devices visibility), и добавляет тонкий слой onboarding'a (ROLE_HOME, POST_NOTIFICATIONS, custom call confirmation, soft-checks `!` индикатор, hard-block для GMS-less устройств). Без новых ценностных вертикалей — это **наведение порядка**.

---

## Clarifications

### 2026-05-19 — Pre-plan clarify pass

| # | Question | Resolution |
|---|----------|------------|
| 1 | Когда запускать `SetupCheck.check()`? | App cold-start (warm cache) + Settings screen `Lifecycle.RESUMED` (re-check всех с `surfaces.contains(Settings)`). Port имеет `surfaces: Set<Surface>` — готовый seam, для текущих check'ов всегда `{Settings}`. Будущий MainScreen-banner subscriber подпишется без breaking change. См. FR-017..FR-020. |
| 2 | Что после PIN lockout'a? | **PIN полностью заменён на rotating challenge** (numeric-entry + sequence-tap). Threat model — *soft barrier против случайного тыра*, не security. Нет PIN'a → нет lockout'a → нет recovery. Reset сценария «бабушка забыла» решён by design. См. FR-024..FR-027, OUT-011. |
| 3 | `!` indicator: Recommended visibility? | Два badge на главном Settings экране: `[!] N` красный (Required) + `[?] M` жёлтый (Recommended). Mitigation для accessibility: text labels «критично» / «рекомендуется», shape-different icons (не только цвет), TalkBack `contentDescription`. См. FR-019. |
| 4 | 7-tap precise zone + TalkBack? | Любая non-interactive область главного экрана, ±48dp дельта между соседними тапами, ≤ 5 сек total. Vibration escalation: light (1-3) → medium (4-6) → success (7). НЕТ visual countdown. TalkBack-flow → accessibility-admin-entry future-spec (inline TODO в plan.md). См. FR-021. |
| 5 | Tutorial overlay reshow policy? | **Tutorial полностью удалён** из спека 10. US-8 + FR-034..FR-038 убраны. Обучение (admin + Managed + contextual help + видео) → отдельный спек 014 `onboarding-and-tutorials` (см. `TODO-FUTURE-SPEC-006` в backlog, roadmap §Spec 014). |
| 6 | Settings = admin-only архитектурно? | **Нет.** Видимость Settings — функция **пресета**, не архитектуры. В `simple-launcher` (senior-safe) пресете Settings скрыт за 7-tap + challenge. В других пресетах может быть напрямую видим. Бабушка теоретически может пройти gate и отвязаться сама — GDPR Article 7 / 152-ФЗ ст. 9 ч. 2 agency сохранена. См. Assumption A-11. |
| 7 | `GmsAvailableCheck` — Required в badge или hard-block? | **Hard-block screen при first launch** на GMS-less устройствах («Это устройство не поддерживается — нет Google Play Services»). НЕ входит в `!N` badge (бабушка может его не увидеть никогда). Hard-block виден устанавливающему сразу. См. FR-042. |

---

## Контекст и цель спека

После спеков 3 → 9 на устройстве пользователя установлено почти всё нужное: provider capabilities (спек 6), pairing с родственником (спек 7), bidirectional config sync (спек 8), admin-mode редактирование (спек 9). Но **между этими слоями осталось 8 зазоров**, которые ни один из них не закрывает:

1. **Главный экран до сих пор рисуется из mock**. `FlowRepository` читает `flows_mock.json` (спек 3), а **не** `/config/current` (спек 8). Admin push'ит — бабушка не видит. Это **блокер `TODO-ARCH-016`** в backlog.
2. **Без `ROLE_HOME` не лончер**. Если пользователь не дал нашему приложению быть home-launcher'ом, оно не перехватывает Home button — весь смысл потерян.
3. **Без `POST_NOTIFICATIONS` (Android 13+) не работает FCM**. Push-уведомления спека 7 не доставляются.
4. **`ACTION_DIAL` слишком много шагов для пожилого**. Тап на тайл → системный dialer → ещё одна кнопка ПОЗВОНИТЬ → звонок. Нужно: тап → наше окно подтверждения → одной кнопкой звонок.
5. **7-tap admin-mode gate (спек 9 T132) появился ad-hoc**. Описано как «7-tap + password gate», но password-механика не была определена. Clarify 2026-05-19 решил: password заменён на rotating challenge (numeric-entry / sequence-tap) — soft barrier, не security. Подробности в FR-021..FR-027.
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

### User Story 7 — Защита от случайного входа в admin-mode через rotating challenge (Priority: P2)

Бабушка случайно тапнула в любой пустой области 7 раз → попала в **challenge gate** перед admin-mode. На экране — мелким шрифтом случайное число (например «1673») и большая кнопка ОТМЕНА. Чтобы пройти — нужно ввести это число на цифровой клавиатуре. Бабушка число не различает (мелкий шрифт) или не понимает что нужно делать → нажимает большую ОТМЕНА → возвращается на главный экран. Admin (внук) читает число, набирает на клавиатуре, проходит в admin-mode. **Тип challenge варьируется случайно** при каждой попытке — иногда числовой ввод, иногда sequence-tap «нажмите кнопки 1→2→3» — чтобы бабушка не запомнила pattern.

**Why this priority**: бабушка может случайно поломать раскладку (admin-mode спека 9 полностью функционален). Обязательный PIN с recovery / lockout / эскалацией отпугнул бы пожилых и требует memory load у admin'a. Rotating challenge решает обе проблемы: zero memory load (admin читает с экрана), defence от случайного тыра (бабушка не понимает что вводить), нет состояния (challenge генерируется случайно при каждой попытке, нечего восстанавливать). **Threat model явно — soft barrier, не security.** Реальная защита — future-spec, см. OUT-011. P2.

**Independent Test**: 7 тапов в пустой области → появляется challenge screen → если ввести правильный ответ — admin-mode; если нажать ОТМЕНА — возврат на главный. После повторного 7-tap challenge может быть **другого типа** (numeric-entry или sequence-tap случайно).

**Acceptance Scenarios**:

1. **Given** бабушка тапнула 7 раз в пустой области главного экрана (с соблюдением FR-021 constraints), **When** срабатывает gate, **Then** показывается challenge screen со случайно выбранным типом challenge (numeric-entry или sequence-tap) и большой кнопкой ОТМЕНА.
2. **Given** challenge screen открыт с numeric-entry challenge (показано число «1673»), **When** пользователь вводит «1673» на клавиатуре, **Then** challenge passes → переход в admin-mode.
3. **Given** challenge screen открыт, **When** пользователь нажимает ОТМЕНА (или системную «назад»), **Then** возврат на главный экран без побочных эффектов; счётчик ничего не инкрементирует.
4. **Given** challenge screen открыт с sequence-tap challenge («нажмите кнопки 1, 2, 3 по порядку»), **When** пользователь нажимает 1 → 2 → 3, **Then** challenge passes → admin-mode. Если нажал в неверном порядке — challenge regenerated с новым случайным выбором.
5. **Given** бабушка случайно ввела часть правильного ответа («16» из «1673»), **When** пытается дальше или нажимает ОТМЕНА, **Then** никаких lockout-механизмов нет; gate всегда доступен повторно.
6. **Given** admin прошёл challenge один раз, прошло 5 минут, повторно тапает 7 раз, **When** challenge screen открывается снова, **Then** генерируется **новый** challenge (новое число или другой тип) — нет «remember me».
7. **Given** на устройстве включён TalkBack, **When** challenge screen открывается, **Then** challenge text **зачитывается вслух** (`importantForAccessibility="auto"`). Это accepted edge — бабушка с TalkBack редкий профиль, и проход gate её не приведёт к разрушительному действию (admin-mode UI ей непонятен, нажмёт Back). Реальная accessibility-aware точка входа — accessibility-admin-entry future-spec.

---

### Edge Cases

- **ARCH-016 + race condition**: admin push'ит изменение **прямо в момент** когда Managed cold-start'ует. Что показывается — старая Room-версия или дождаться Firestore? → Room сразу (≤ 1 сек), затем live update при получении push (≤ 10 сек). Никаких блокирующих экранов.
- **CALL_PHONE granted, но номер невалиден** (пустая строка, странные символы): `ACTION_CALL` бросит SecurityException или ничего не произойдёт. Окно подтверждения должно валидировать номер до показа кнопки ПОЗВОНИТЬ; если невалидно — кнопка disabled + сообщение «Номер некорректен».
- **ROLE_HOME отозван пользователем** (зашёл в системные Settings → выбрал другой лончер): при следующем запуске наш `!` индикатор снова поднимается.
- **Multiple Managed-устройств через 7-tap одновременно**: бабушка может быть в admin-mode редактируя своё устройство, и admin (внук) может в этот момент тоже редактировать её устройство. → конфликт-resolution спека 8 (FR-050 Merge UI) уже это покрывает.
- **Challenge gate state corruption**: challenge генерируется in-memory при каждом 7-tap, **persistent state нет**. Нет сценария повреждения — это explicit упрощение по сравнению с PIN-based архитектурой.
- **POST_NOTIFICATIONS denied → user revokes from system Settings**: при следующем re-check (Settings RESUMED) → `!` индикатор обновляется. Не trying to re-request permission programmatically (Android 13+ блокирует).
- **Удаление `flows_mock_*.json`**: 3-5 Robolectric-тестов спека 3/4 опираются на эти файлы (см. T072 спека 3, тесты Activity'ев). Должны быть переписаны на `FakeRemoteSyncBackend` (как делает спек 7 E2E) **в той же phase**, что и ARCH-016.
- **Challenge bypass через accidental sequence**: для sequence-tap challenge с 6 кнопками и последовательностью из 3 — FP ≈ 1/120 ≈ 0.83%. Для numeric-entry 4 цифры — FP ≈ 0.01%. Оба ниже acceptable threshold для soft barrier. Если в реальной telemetry FP окажется выше — challenges пересматриваются (это two-way door).
- **TalkBack пользователь и challenge**: challenge text accessible через TalkBack (см. US-7 Acceptance #7). Для accessibility-aware точки входа в admin-mode — `accessibility-admin-entry` future-spec (inline TODO в plan.md).
- **Vibration отключена системно** (`Settings.System.HAPTIC_FEEDBACK_ENABLED == 0`): 7-tap gate работает без tactile feedback. Admin (внук) не получает escalating-feedback но это не блокирует gesture — счётчик визуально не показывается тоже (FR-021).
- **GMS-less устройство (Huawei после 2019, китайские OEM)**: при first-launch — hard-block screen «не поддерживается» вместо wizard'а; пользователь не может перейти к настройке. См. FR-042.

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
- **FR-008a**: First-launch wizard MUST содержать visual progress indicator (текст «Шаг N из M» + visual dots / bar) над содержимым каждого шага. На Android 13+ M = 4 (language, preset, ROLE_HOME, POST_NOTIFICATIONS), на Android < 13 M = 3 (POST_NOTIFICATIONS step skipped — M уменьшается automatically). Indicator updates after each completed / skipped step. Closes Article VIII §7 «≤ 3 шага OR progress indicator» (см. checklist `elderly-friendly.md` CHK007).
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

- **FR-017**: System MUST содержать в `core/commonMain/api/setup/` port `SetupCheck` со следующим контрактом: `id: String`, `criticality: Criticality { Required, Recommended }`, `surfaces: Set<Surface> { Settings, MainScreen }`, `check(): Status { Ok, NotConfigured(reason) }`, `resolveIntent(): IntentSpec?` (decoupled от Android-типов). Поле `surfaces` зарезервировано как готовый seam: текущие check'и спека 10 все имеют `surfaces = { Settings }`; в будущем check может опубликовать `Settings + MainScreen`, и main-screen banner subscriber подключится без изменения port'а (MVA per CLAUDE.md rule 4).
- **FR-018**: System MUST содержать реестр `SetupCheckRegistry` со следующими дефолтными checks: `RoleHomeCheck` (Required, **первый в списке**, `surfaces = { Settings }`), `PostNotificationsCheck` (Required, Android 13+, `surfaces = { Settings }`), `CallPhoneCheck` (Recommended, `surfaces = { Settings }`), `NetworkOnlineCheck` (Recommended, `surfaces = { Settings }`), `BatteryOptimizationCheck` (Recommended, `surfaces = { Settings }`). **GMS availability** — НЕ check в реестре, отдельный hard-block flow в FR-042.
- **FR-019**: Settings screen MUST показывать **два badge** рядом с заголовком: `[!] N` красный (N = число невыполненных Required checks) + `[?] M` жёлтый (M = число невыполненных Recommended). Каждый badge сопровождается: **(a)** text label рядом («критично» / «рекомендуется») для пользователей не различающих цвет; **(b)** shape-different icon (treugольник `!` для Required, круг `?` для Recommended) — distinct shape независимо от цвета; **(c)** TalkBack `contentDescription` («Критичных проблем N», «Рекомендованных проблем M»). Если N == 0 — `[!]` badge не показывается; если M == 0 — `[?]` badge не показывается; если оба == 0 — никаких badge.
- **FR-020**: Settings → «Что не настроено» — отдельный экран со списком невыполненных checks, **две секции**: «Срочно настроить» (Required first) и «Можно настроить позже» (Recommended); у каждого пункта — описание + кнопка «Настроить» (запускает `resolveIntent()`).
- **FR-020a (execution model)**: System MUST выполнять `check()` всех зарегистрированных `SetupCheck` со следующей моделью: (a) один прогон при app cold-start (warm cache, results доступны для UI); (b) re-run всех check'ов с `surfaces.contains(Settings)` при каждом `Lifecycle.RESUMED` Settings screen Activity / Composable. **НЕТ** background polling, **НЕТ** проактивных subscriptions. Покрывает главный сценарий: пользователь тапнул «Настроить» → системные Settings → дал permission → вернулся в наш Settings → `!N`/`?M` обновляется немедленно.
- **FR-020b (exception handling)**: Если `SetupCheck.check()` бросает exception (например, OEM-specific API quirk: `PowerManager.isIgnoringBatteryOptimizations()` SecurityException на Xiaomi, или GMS API недоступна), System MUST интерпретировать как `CheckStatus.NotConfigured(reason = exception.message)` и логировать diagnostic event `setupCheckException(checkId, reason)`. Settings UI MUST продолжать функционировать; единичный failing check НЕ должен crash'ить весь Settings экран. `!N` / `?M` badge учитывают такой check как NotConfigured (обычный путь).

#### Part E — 7-tap gate + rotating challenge (US-7)

**Threat model (explicit):** challenge gate — **soft barrier**, защита от случайного тыра и слабо разбирающегося пользователя (бабушка). НЕ security-механизм. Реальная защита (PIN с lockout, biometrics, device-level auth) — future-spec, см. OUT-011.

- **FR-021**: System MUST детектировать 7-tap gesture со следующими constraints: (a) 7 тапов в **non-interactive области** главного экрана (не попадающие в `Slot`/tile); (b) каждый последующий тап в пределах **±48dp дельты** от первого (one-point gesture, отсеивает «smear»); (c) общая длительность ≤ 5 секунд; (d) тапы могут быть в любой части экрана, удовлетворяющей (a) — НЕТ скрытой «sweet spot» зоны. Vibration escalation: `HapticFeedbackConstants.VIRTUAL_KEY` (light) на тапах 1-3, `HapticFeedbackConstants.LONG_PRESS` (medium) на тапах 4-6, success pattern на тапе 7. **НЕТ visual countdown** — admin ориентируется по haptic feedback.
- **FR-022**: По достижении 7-tap System MUST показать **challenge screen** со случайно выбранным challenge из реестра (FR-023). Кнопка ОТМЕНА на challenge screen — большая, senior-safe (≥ 56dp), визуально доминирует над challenge UI; кнопка возврата `Back` системы → также возврат на главный экран.
- **FR-023**: System MUST содержать `ChallengeRegistry` с **минимум 2 типами** challenges: (a) **numeric-entry** — случайно генерируется 4-значное число, показывается **мелким шрифтом** (≤ 14sp, в отличие от senior-safe baseline ≥ 24sp намеренно), цифровая клавиатура для ввода; (b) **sequence-tap** — 6 кнопок с цифрами 1-6 в random visual layout, инструкция «нажмите кнопки 1, 2, 3 по порядку» (последовательность из 3 — random subset), правильный порядок → pass. **slide-puzzle** и более сложные challenges — out-of-scope спека 10 (см. OUT-012). Тип challenge выбирается **uniform random** при каждом 7-tap (нет «memory» предыдущего выбора).
- **FR-024**: Правильный ответ на challenge → переход в admin-mode (контрол передаётся UI спека 9). Неправильный ответ — challenge **regenerated** с новым random выбором типа и параметров; **нет lockout-counter'a, нет блокировок по времени**. Пользователь может бесконечно retry или нажать ОТМЕНА.
- **FR-025**: Challenge state — **только in-memory**, generated при каждом 7-tap. **НЕТ** persistent state (`EncryptedSharedPreferences` для PIN не используется, `adminModeEntryCount` НЕ хранится). Это explicit упрощение по сравнению с PIN-based design: исключены ошибки повреждения state, нет recovery flow, нет sync с `/config`.
- **FR-026**: Visual styling challenge screen: (a) challenge text (например число «1673») — **мелкий шрифт намеренно** (≤ 14sp), чтобы elderly с возрастной макулярной дегенерацией не различил; (b) кнопка ОТМЕНА — **large senior-safe primary** (≥ 56dp, контрастный фон, левая позиция по hand-reach pattern); (c) клавиатура (для numeric-entry) — standard цифровая, кнопки baseline 48dp.
- **FR-027**: Challenge accessibility (TalkBack): challenge text marked `importantForAccessibility="auto"` — TalkBack **зачитывает** его вслух. Это accepted edge per US-7 Acceptance #7 (бабушка с TalkBack может пройти gate; admin с TalkBack тоже может). Реальный accessibility-aware admin entry — future-spec `accessibility-admin-entry` (inline TODO в plan.md).

#### Part F — Paired devices list (US-5)

- **FR-029**: Settings MUST содержать раздел «Сопряжённые устройства» с **двумя списками**: «Кто помогает мне» (paired-устройства, где это устройство — Managed) и «Кому я помогаю» (paired-устройства, где это устройство — Admin).
- **FR-030**: Каждый пункт списка MUST содержать: имя устройства (из `/links/{linkId}` метаданных спека 7), дату привязки, кнопку «Прекратить помощь».
- **FR-031**: По нажатию «Прекратить помощь» MUST показываться двухступенчатое подтверждение (Article VIII senior-safe destructive-action paradigm): «Прекратить помощь от Маши? Маша больше не сможет менять твою раскладку» → ДА/НЕТ.
- **FR-032**: При подтверждении System MUST mark link as **locally revoked немедленно** (persistent state, переживает app kill / restart). Local app сразу: (a) перестаёт слушать `/config` push'и для этого link, (b) не публикует `/state` updates, (c) UI обновляется — запись Маши исчезает из «Кто помогает мне». **Server-side cleanup queued per FR-032a** (eventually-consistent).
- **FR-032a (local-first revocation pattern)**: System MUST queue server-side `LinkRegistry.deactivate(linkId)` для каждого locally-revoked link'a. Очередь обрабатывается **при наличии интернета** (first reconnect after revoke, либо immediate если интернет был во время confirm):
  - **(a) Online path** (интернет есть в момент confirm): попытка `deactivate(linkId)` сразу. Если success → cascade delete `/config/{linkId}`, `/state/{linkId}`, push admin'у через FCM. Queue entry clears.
  - **(b) Offline path** (нет интернета): non-blocking toast «Не получилось отвязать сразу — повторим автоматически когда появится сеть». Локально Маша **уже исчезла** из списка (FR-032 local revoke happened immediately). Server-side deactivation queued.
  - **(c) Reconnect path** (queued entry, интернет вернулся): check `/links/{linkId}.revoked` flag на сервере. Если ещё active — `deactivate(linkId)` → cascade cleanup. Если уже revoked (admin тоже удалил параллельно) — queue clears no-op (idempotent).
  - **(d) Retry path**: если `deactivate()` fails по сетевой ошибке снова — exponential backoff retry на следующий reconnect cycle.
  
  **Rationale**: бабушка должна быть уверена что «нажала Прекратить → Маша больше не имеет влияния», даже без интернета. Local revoke даёт **immediate UX guarantee**; server cleanup — eventual.
- **FR-033**: Если оба списка пусты, System MUST показывать empty-state «Никто пока тобой не помогает — попроси внука отсканировать QR-код» с кнопкой «Показать QR» (запускает QR-flow спека 7).

#### Part H — GMS availability hard-block (Bonus B clarify)

- **FR-042**: При first-launch System MUST детектировать состояние Google Play Services через domain port `GmsAvailabilityPort.status()` (commonMain port wrapping `GoogleApiAvailability.isGooglePlayServicesAvailable()` per CLAUDE.md rule 1; реальная имплементация — `GmsAvailabilityAdapter` в androidMain). Если результат — `GmsStatus.MissingFatal` (non-recoverable: `SERVICE_INVALID`, `SERVICE_MISSING` без `isUserResolvableError`), System MUST показать **hard-block screen** до wizard'a: «Это устройство не поддерживается. Для работы приложения нужны Google Play Services, которых нет на этом устройстве.» Кнопка «Понятно» закрывает приложение (`finishAffinity()`). Domain code MUST NOT match on raw `ConnectionResult` integer codes — only on `GmsStatus` sealed variants (enforced by Konsist gate `Spec010IsolationTest.T007`).
- **FR-043**: Hard-block screen MUST содержать ссылку (`https://support.google.com/googleplay/answer/9037938`) для пользователей, которые хотят понять что такое GMS. Текст ссылки — крупный (senior-safe ≥ 24sp), кликабельный через `Intent.ACTION_VIEW`.
- **FR-044**: Если `GmsAvailabilityPort.status()` возвращает `GmsStatus.MissingRecoverable(resolutionAvailable = true)` (`SERVICE_VERSION_UPDATE_REQUIRED`, `SERVICE_DISABLED`, `SERVICE_UPDATING`), вместо hard-block — `GoogleApiAvailability.getErrorDialog()` (системный диалог, который ведёт пользователя на установку/включение GMS, вызывается изнутри адаптера или из `GmsHardBlockActivity` через явный bridge). Это **soft-block** — после успешного resolve приложение продолжит wizard.

### Cross-cutting Requirements

- **FR-039**: Все новые user-facing strings MUST быть локализованы в `strings.xml` (en + ru) per ADR-004; никакого hardcoded русского/английского текста в Kotlin-коде.
- **FR-040**: Никакая wire-format модификация в этом спеке не повышает `schemaVersion` в `/config`. Challenge state — in-memory only (FR-025), не часть wire-format. Если в future-spec OUT-011 (реальная security) понадобится sync чего-либо PIN-related через `/config`, это будет additive nullable field в отдельном спеке со своей миграцией.
- **FR-041**: Все новые ports (`SetupCheck`, `SlotToActionMapper`) MUST соответствовать CLAUDE.md rule 1 (domain isolation): living в `core/commonMain/api/`, без vendor/Android type leaks.

### Key Entities

- **SetupCheck** (port): `id: String`, `criticality: Criticality`, `surfaces: Set<Surface>`, `check(): suspend () -> CheckStatus`, `resolveIntent(): IntentSpec?`.
- **Criticality** (sealed): `Required`, `Recommended`.
- **Surface** (sealed): `Settings`, `MainScreen`. Спек 10: все check'и публикуют только `Settings`; `MainScreen` — готовый seam для будущих банеров (mute-style).
- **CheckStatus** (sealed): `Ok`, `NotConfigured(reason: String)`.
- **IntentSpec** (data class): platform-agnostic descriptor для deep-link'a (например `{ category: "SETTINGS", action: "POST_NOTIFICATIONS_DETAILS" }`). Mapping → реальный `android.content.Intent` происходит в `:app/androidMain`.
- **SlotToActionMapper** (function in `core/commonMain/api/action/`): `fun Slot.toAction(contacts: List<Contact>): Action?`.
- **Challenge** (sealed, in `core/commonMain/api/gate/`): `NumericEntry(answer: String)`, `SequenceTap(buttonIds: List<Int>, expectedOrder: List<Int>)`. **In-memory only**, generated при каждом 7-tap, не persisted.
- **ChallengeRegistry** (interface): `fun generate(): Challenge` — uniform random выбор типа + параметров.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Admin push раскладки доставляется и применяется на главном экране Managed в течение ≤ 10 сек в 95% случаев (T-PUSH из спека 7).
- **SC-002**: Cold-start главного экрана Managed (с уже-applied конфигом из локальной копии) показывает первый кадр раскладки в течение ≤ 1 сек на medium-tier устройстве (Pixel 4a baseline).
- **SC-003**: Бабушка может позвонить контакту с главного экрана в **2 нажатия** (тап тайл + ПОЗВОНИТЬ) при granted `CALL_PHONE`, или в **3 нажатия** (тап + ПОЗВОНИТЬ + зелёная кнопка системного dialer'a) при denied permission.
- **SC-004**: 100% свежеустановленных приложений на чистом устройстве показывают `!N` индикатор в Settings с `N >= 2` (минимум: ROLE_HOME + POST_NOTIFICATIONS на Android 13+).
- **SC-005**: После полного прохождения first-launch wizard'a + grant всех Required permissions значение `N` снижается до 0 в 100% случаев на тестовом устройстве.
- **SC-007**: Бабушка тапнувшая случайно 7 раз в пустой области экрана **НЕ попадает в admin-mode** в ≥ 99% случаев — challenge screen блокирует её (она нажимает ОТМЕНА или не понимает что нужно вводить). Метрика: false-positive rate проходов challenge при random taps на цифровой клавиатуре / sequence-tap кнопках — должна быть ≤ 1% (по unit-test'у с симулируемым random input'ом).
- **SC-008**: Удаление `flows_mock_*.json` не ломает CI: все unit/integration/Robolectric тесты спеков 3, 4, 5, 6, 7, 8, 9 продолжают зелёными после Phase 0.
- **SC-009**: APK size delta после спека 10 (Phase 0 ARCH-016 + soft-checks engine + новые экраны) ≤ +500 KB к release build спека 9.

### Out of Scope

- **OUT-001**: Role-picker (Managed vs Admin) — устройство играет обе роли симметрично через одну установку. Принцип «одно приложение, разные конфиги» из спека 9 распространяется.
- **OUT-002**: Pairing-предложение в setup-wizard — pairing доступен через Settings (UI спека 7); дублировать в wizard'е не нужно.
- **OUT-003**: Remote security configuration через admin (`/config.securityPolicy`) — future-spec OUT-011. Challenge gate в спеке 10 — local-only behavior, не синхронизируется.
- **OUT-004**: Persistent counter / lockout / recovery flow — намеренно отсутствуют (см. A-6 threat model). Если потребуются — это будет реализовано в спеке OUT-011, а не расширением спека 10.
- **OUT-005**: Полная GDPR / 152-ФЗ pipeline для контактов — `TODO-LEGAL-001`. Минимум спека 9 (`ContactsManageScreen`) сохраняется.
- **OUT-006**: DPC / Device Owner provisioning — отдельный будущий ADR.
- **OUT-007**: App version compatibility detection и remote update — `TODO-ARCH-007`.
- **OUT-008**: Wearable / security-sensor мониторинг — `TODO-FUTURE-SPEC-001/002`.
- **OUT-009**: Diff-проверка при смене preset'а — была в исходном описании спека 10, но при детальном рассмотрении оказалась избыточной: preset-switcher уже есть в спеке 3, а soft-checks движок (FR-017..FR-020) автоматически перенаправит `!` индикатор после смены preset'а.
- **OUT-010**: Преcет-зависимость challenge-механики («simple-launcher с challenge, workspace без») — рассматривалось в clarify 2026-05-19, решено: единое поведение для всех preset'ов (challenge всегда после 7-tap). Преcет-зависимость challenge (для workspace без challenge) — `TODO-FUTURE-SPEC-005` (preset-editor).
- **OUT-011**: **Реальная security защита входа в admin-mode** (запомненный PIN с lockout, biometrics через `BiometricPrompt`, device-level auth через `KeyguardManager`). Сейчас challenge gate — **soft barrier** против случайного тыра, **не security**. Future-spec будет добавлен в backlog когда продукт пойдёт за пределы доверенных семейных контекстов (например, корпоративное использование, медицинские учреждения, hospice). Threat model для будущего спека: criminal adversary, device theft, malicious household member. На текущей фазе разработки спецификации этого future-спека не требуется — фиксируем как known gap. Inline note для разработчиков: «если попросят сделать "по-настоящему защищённый PIN" — это **новый спек**, не расширение спека 10».
- **OUT-012**: **Slide-puzzle challenge** и более сложные challenge типы (drag-and-drop, draw-pattern, swipe-gesture). Спек 10 ограничивается 2 типами (numeric-entry + sequence-tap) ради implementation simplicity. Расширение реестра — `TODO-ARCH-017` в backlog (если variability окажется недостаточной по telemetry).
- **OUT-013**: **Обучающий слой** (tutorial overlays, admin onboarding wizard, in-app contextual help, video walkthroughs) — вынесено в **спек 014 `onboarding-and-tutorials`** (см. roadmap, `TODO-FUTURE-SPEC-006`).

---

## Assumptions

### Зависимости от других спеков

- **A-1**: Спек 8 (`bidirectional-config-sync`) уже реализовал `ConfigEditor.appliedConfig` как Room-backed observable (FR-041 спека 8). Спек 10 потребляет, не модифицирует.
- **A-2**: Спек 7 (`pairing-and-firebase-channel`) уже реализовал `LinkRegistry.deactivate(linkId)` и push-механику отзыва. Спек 10 потребляет UI'ем «Прекратить помощь».
- **A-3**: Спек 9 T132 (7-tap gate для Settings → «Редактировать раскладку») остаётся, но **password** заменяется на rotating challenge (см. FR-021..FR-027). Спек 10 узаконивает gate-механику в `core/commonMain/api/gate/`.
- **A-4**: Спек 5 T541 (`PhoneHandler` → `ACTION_DIAL`) **расширяется** до conditional `ACTION_CALL` (если granted) / `ACTION_DIAL` (fallback). Изменение в `:app/androidMain`, не в спеке 5 как написано в tasks.md (это будет noted в спека 10 plan.md и в перекрёстной ссылке в спек 5).

### Архитектурные принципы

- **A-5**: Принцип «устройство играет обе роли» — каждое устройство одновременно может быть admin для одних paired-устройств и managed для других. N pairing'ов параллельно — нормальный режим, поддерживается спеком 7 архитектурно.
- **A-6**: **Challenge gate — soft barrier, не security.** Threat model: защита от случайного тыра elderly/неопытного пользователя. **НЕ** защита от criminal adversary, device theft, malicious household member. Challenge state — in-memory only, нет persistent storage, нет lockout. Реальная security (PIN с lockout, biometrics, device-level auth) — future-spec, см. OUT-011.
- **A-7**: `CALL_PHONE` — explicit dangerous permission в Play Store Data Safety form. Обоснование: «one-tap calling for elderly users». Exit ramp — `ACTION_DIAL` fallback всегда работает без permission'а (FR-012).

### Технические допущения

- **A-8**: TalkBack-friendliness challenge'ей: challenge text **зачитывается** TalkBack'ом (FR-027). Это accepted trade-off — soft barrier не должен полностью блокировать accessibility-aware пользователей; accessibility-aware точка входа в admin-mode — future-spec.
- **A-9**: 7-tap зона — **любая non-interactive область** главного экрана (тапы, не попадающие в `Slot`/tile), ±48dp дельта между тапами, ≤ 5 сек. См. FR-021. Это итерация по сравнению с draft'ом (где было «правый верхний угол 24dp×24dp» — отменено в clarify 2026-05-19, мелкая фиксированная зона невозможна для elderly с тремором).
- **A-10**: `ConfigRefreshWorker` спека 8 продолжает работать при background-restrictions OEM-специфично (Samsung, Xiaomi, Huawei). Полная OEM-matrix — `TODO-DEVICE-002` спека 8.
- **A-11**: **Видимость Settings — функция пресета.** В `simple-launcher` (senior-safe) пресете Settings скрыт за 7-tap + challenge; в других пресетах (workspace, launcher) Settings может быть напрямую видимым. Архитектурно Settings всегда доступен — discoverability в senior-safe пресетах низкая намеренно. Бабушка теоретически может пройти gate и отвязаться сама → **GDPR Article 7 / 152-ФЗ ст. 9 ч. 2 agency сохранена**. Преcет-зависимость challenge (например, workspace без challenge) — out-of-scope, см. OUT-010.
- **A-12**: GMS availability — обязательное precondition для всего стека (FCM спека 7, Firestore спека 8). GMS-less устройства (Huawei после 2019, китайские OEM без AOSP-GMS) — hard-block при first launch (FR-042). Alternative paths (WorkManager polling вместо FCM, REST вместо Firestore SDK) — `TODO-ARCH-005` в backlog, не блокирующий спек 10.
- **A-13**: **Challenge text ≤ 14sp — намеренное нарушение senior-safe baseline ≥ 18sp** (Article VIII §7). Это **core mechanic** soft barrier: visual difficulty для elderly is the point — бабушка не должна различить число чтобы случайно не ввести. Documented Article VIII §7 «documented product constraint says otherwise» exception clause invoked. **НЕ повышать** этот размер в будущих правках / refactor'ах — это сломает barrier mechanic полностью (бабушка прочитает число → пройдёт challenge → попадёт в admin-mode → испугается / поломает). Если в будущем real security понадобится (см. OUT-011), отдельный спек заменит challenge на PIN с recovery — но пока это soft barrier с intentional visual cost.

---

## Cross-cutting concerns surfaced from mentor session

Эти вопросы были озвучены в mentor pre-specify session 2026-05-17 и clarify session 2026-05-19. Зафиксированы здесь чтобы избежать повторных открытий:

1. **CALL_PHONE one-way door**: explicit dangerous permission затрагивает Play Store Data Safety. Inline-TODO в `PhoneHandler` про exit ramp через `noCallPermission` build flavor — на случай если будущий маркет/правовая ситуация потребует.
2. **Call dialog vs spec 8 pending changes banner**: окно подтверждения звонка может открываться поверх pending-баннера спека 8 — не должно блокировать его. E2E smoke test должен это проверить (FR-016 implicit).
3. **Challenge gate — soft barrier explicitly** (clarify 2026-05-19): нет PIN'а, нет recovery, нет lockout. Threat model: случайный тыр elderly. Реальная security — future-spec OUT-011. Это explicit one-way door: если потом захотим строгую защиту, новый спек (не расширение текущего).
4. **ROLE_HOME ordering**: ROLE_HOME — **первый** Required check в реестре `SetupCheckRegistry` (FR-018). Без него весь смысл launcher'a пропадает; ARCH-016 без ROLE_HOME пользователем не видим.
5. **TalkBack и challenge** (clarify 2026-05-19): challenge text зачитывается TalkBack'ом — accepted edge для soft barrier. Accessibility-aware admin entry → future-spec `accessibility-admin-entry`.
6. **`flows_mock_*.json` deletion ломает тесты**: 3-5 Robolectric-тестов спеков 3/4 ссылаются на эти файлы (FR-004, SC-008). Переписать на `FakeRemoteSyncBackend` (как делает спек 7 E2E test) **в той же Phase**, что и ARCH-016 — не оставлять долгом.
7. **GMS hard-block vs soft-block distinction** (clarify 2026-05-19): non-recoverable GMS errors → `finishAffinity()` hard-block; recoverable (update/disabled) → системный диалог с resolution path. См. FR-042..FR-044.
8. **Settings visibility — preset-driven, не arch-driven** (clarify 2026-05-19): senior-safe пресет скрывает Settings, другие пресеты не скрывают. GDPR agency сохранена через теоретическую доступность gate. См. A-11.

---

## Затрагиваемые внешние артефакты

Эти артефакты вне `specs/010-*/` будут модифицированы спеком 10:

- **Спек 5 T541 `PhoneHandler`** — переход с `Intent(ACTION_DIAL, ...)` на conditional `Intent(ACTION_CALL, ...)` (granted) / `Intent(ACTION_DIAL, ...)` (fallback). Дописывается ссылка в [specs/005-action-architecture-v2/tasks.md](../005-action-architecture-v2/tasks.md) на спек 010 FR-012.
- **[docs/compliance/permissions-and-resource-budget.md](../../docs/compliance/permissions-and-resource-budget.md)** — добавление `CALL_PHONE` в раздел Requested permissions с обоснованием «one-tap calling for elderly users».
- **[app/src/main/AndroidManifest.xml](../../app/src/main/AndroidManifest.xml)** — `<uses-permission android:name="android.permission.CALL_PHONE" />`.
- **Спек 9 T132** — узаконивание 7-tap, замена password на rotating challenge. Cross-reference в спек 9 tasks.md.
- **[docs/dev/project-backlog.md](../../docs/dev/project-backlog.md)** — закрытие `TODO-ARCH-016` (статус → DONE, ссылка на коммиты спека 10); добавлен `TODO-FUTURE-SPEC-006` (onboarding-and-tutorials).
- **[docs/product/roadmap.md](../../docs/product/roadmap.md)** §Spec 010 — обновление описания (устарело после clarify 2026-05-19); добавлен §Spec 014 onboarding-and-tutorials.

---

## Краткое содержание простым русским языком *(для не-разработчика)*

Этот спек не добавляет «новых функций» в продукт. Он **доделывает** четыре вещи, которые висели после предыдущих спеков:

**Главное (P1):**
1. **Раскладка бабушки теперь приходит с телефона внука.** До спека 10 внук менял раскладку в редакторе, нажимал «Опубликовать», бабушка ничего не видела — на её главном экране была заглушка. После спека 10 — admin меняет, бабушка через 10 секунд видит.
2. **Наш лончер становится главным.** Без этого Home button у бабушки не открывает наш экран. При первом запуске спрашиваем «сделать главным?», и потом в настройках напоминаем, если отказалась.
3. **Уведомления.** На Android 13+ Google требует, чтобы пользователь сам разрешил приложению показывать уведомления. Без этого внук не сможет получить сигнал «у бабушки 3% батарея». Добавляем явный запрос при первом запуске.
4. **Звонок одной кнопкой.** Сейчас бабушка тапает плитку «Маша» → открывается системный экран дозвона → она ещё раз нажимает зелёную кнопку. После спека 10 — тап → наше окно «Позвонить Маше? [ОТМЕНА] [ПОЗВОНИТЬ]» → одна кнопка → звонок.

**Не главное, но полезное (P2):**
5. **Список «кто мной управляет» в настройках** с кнопкой «прекратить помощь» — для приватности. Бабушка теоретически может туда попасть и отвязаться сама — это требование закона (GDPR / 152-ФЗ).
6. **Два значка в настройках** — красный `!` (срочно настроить, например ROLE_HOME) и жёлтый `?` (можно подождать, например battery optimization). Рядом крупными буквами «критично» / «рекомендуется» чтобы бабушка с плохим зрением различала.
7. **Защита от случайного входа в admin-режим — challenge gate**: после 7 быстрых тапов в любой пустой области экрана появляется экран с просьбой ввести случайное число (мелким шрифтом) или нажать кнопки по порядку. **И большая кнопка ОТМЕНА**. Если бабушка случайно дотапает — не поймёт что нужно делать, нажмёт отмену. Внук — прочитает число, введёт, пройдёт. **Это не серьёзная защита, а просто "стена с малозаметной дверью"**.

**Что добавлено в clarify 2026-05-19:**
8. **Hard-block на GMS-less устройствах**: на телефонах без Google Play Services (Huawei после 2019, некоторые китайские) — при первом запуске показываем экран «не поддерживается», приложение закрывается. Без GMS push'и не работают = внук не сможет ничего слать бабушке.

**Что НЕ входит** (специально отсечено в pre-specify + clarify обсуждениях): выбор «ты бабушка или внук» при первом запуске (не нужен — одно устройство может играть обе роли), предложение pair'a в визарде (есть в Settings), удалённый PIN-менеджмент, юридические тонкости по GDPR (отдельный спек), управление через DPC, **обучение пользователей через tutorial overlay** (вынесено в спек 014 onboarding-and-tutorials), **реальная security защита** входа в admin-mode с PIN/biometrics (вынесено в future-spec OUT-011, для не-семейного контекста использования).

**Зачем это делать сейчас**: без п. 1 предыдущие спеки 7-8-9 функционально пусты (внук редактирует, бабушка не видит). Без п. 2-3 продукт не работает как лончер на современном Android. Без п. 8 на 5-10% устройств продукт молча не работает. Это «замазать щели в стене перед сдачей дома».

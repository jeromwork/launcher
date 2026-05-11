# Spec 007: Pairing and Firebase Channel

**Status**: Draft (pre-clarify) | **Date**: 2026-05-11 | **Author**: project owner
**Branch**: `007-pairing-and-firebase-channel`
**Depends on**: 006 (`Capability`, `HealthSnapshot`, `IconStorage` port, wire-formats), 003 (`RemoteSyncBackend` port — пустой интерфейс), 005 (`ActionWireFormat.json` Json instance)

---

## Clarifications

*Этот раздел будет заполнен после прохода `/speckit-clarify`. Пока — placeholder.*

---

## 0. Зачем этот spec вообще существует *(объяснение для новичка)*

После спеков 003–006 лаунчер работает **локально**: показывает раскладку из mock-конфига, исполняет действия, собирает локальный snapshot провайдеров и здоровья. Но **родственник-admin не может ничего изменить удалённо** — нет канала между его телефоном и телефоном OLD.

Этот спек строит **первый реальный канал связи** между двумя устройствами через Firebase. Состоит из трёх частей:

1. **Pairing** — admin сканирует QR-код с экрана OLD, получает права на чтение/запись определённых документов в облаке для **этого конкретного OLD-устройства**.
2. **Realtime backend** — реализация `RemoteSyncBackend` (пустой port из спека 003) поверх Firebase: read/write документов, подписка на изменения, FCM для push.
3. **Безопасность** — Firestore Security Rules ограничивают, кто что может писать (admin → `/config`, `/commands`; OLD → `/state`, `/capabilities`, `/health`).

**Что этот спек НЕ делает** (и почему):

- **Не применяет** `/config` к локальной раскладке OLD'а (это спек 008 — bidirectional sync с conflict resolution).
- **Не редактирует** конфиг с admin-UI (это спек 009 — admin mode flows).
- **Не отправляет** capabilities/health в облако автоматически — только инфраструктура для отправки (фактический экспорт триггерится в 008).

Идея — **тонкий канал**. Спек 007 = «два устройства видят документы друг друга, шифр работает, права работают». Всё остальное — следующие спеки.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Pairing через QR (Priority: P1)

Родственник (admin) и пожилой пользователь (OLD) сидят рядом. OLD включает в Settings тумблер «Разрешить удалённое управление», на экране появляется QR-код. Admin со своего телефона (тоже с этим лаунчером в admin-режиме) сканирует QR. После взаимного подтверждения два устройства связаны: admin видит идентификатор OLD'а у себя в списке привязанных, OLD видит на экране consent «Admin <id> получил доступ, чтобы отозвать — выключите тумблер».

**Why this priority**: без pairing вообще ничего удалённого не работает. Это первый и единственный путь установить доверие между устройствами.

**Independent Test**: запустить лаунчер на двух эмуляторах. На одном — войти в admin-mode и нажать «Связать новое устройство». На втором — включить тумблер удалённого управления, показать QR (физически — admin наводит первый эмулятор на второй, в эмуляторе будет ручной ввод токена). После сканирования у admin'а появляется новая запись в списке устройств; у OLD появляется consent-экран с adminId; обе стороны переходят в состояние «paired». Pairing-токен после успешного использования больше не работает.

**Acceptance Scenarios**:

1. **Given** OLD на первом запуске, **When** включает тумблер «Разрешить удалённое управление», **Then** на экране показывается QR-код с deep-link `launcher://pair?token=<6-char>` и таймером обратного отсчёта 5 минут.
2. **Given** admin сканирует валидный непросроченный токен, **When** делает claim, **Then** в Firestore создаётся `/links/{linkId}` с {adminId, oldDeviceId, createdAt}; `/pairings/{token}.claimed = true`; OLD получает realtime-уведомление об изменении.
3. **Given** OLD получил `claimed=true`, **When** показывает consent-экран с adminId и кнопками «Разрешить»/«Отклонить», **Then** при «Разрешить» — `/links/{linkId}/state` инициализируется; при «Отклонить» — `/links/{linkId}` удаляется, токен сбрасывается, тумблер выключается.
4. **Given** токен просрочен (>5 минут с создания), **When** admin пытается claim, **Then** транзакция падает с понятной ошибкой; OLD продолжает показывать QR, но c истёкшим toketn (выдаётся новый автоматически или по жесту «обновить»).
5. **Given** токен уже claimed, **When** второй admin пытается claim того же токена, **Then** транзакция падает (атомарность через Firestore transaction); первый admin остаётся единственным.

---

### User Story 2 — RemoteSyncBackend поверх Firebase (Priority: P1)

Доменные коды лаунчера сегодня видят только `RemoteSyncBackend` (пустой интерфейс из спека 003). После этого спека есть **рабочая реализация** через Firebase Firestore + FCM, и `FakeRemoteSyncBackend` для тестов. DI-граф собирает нужную реализацию в зависимости от build flavor (real / fake / mock-by-default-in-tests).

**Why this priority**: после pairing нужен реальный read/write/listen — иначе pairing бесполезен.

**Independent Test**: в unit-тесте подменить backend на `FakeRemoteSyncBackend`, выполнить базовые операции (write doc, read doc, listen for changes, push command) — все возвращают консистентные результаты. В отдельном integration-тесте (`@MediumTest`, требует Firestore emulator) — те же операции против `FirebaseRemoteSyncBackend` дают идентичный observable contract.

**Acceptance Scenarios**:

1. **Given** paired link существует, **When** OLD пишет `/links/{linkId}/state` с новым snapshot'ом, **Then** admin, подписанный на `/links/{linkId}/state`, получает обновление в течение 2 секунд (real Firestore) или мгновенно (Fake).
2. **Given** admin пишет в `/links/{linkId}/config`, **When** OLD onлайн, **Then** OLD получает change event; OLD пока не применяет конфиг (это спек 008), но в логе видит «получен config с schemaVersion=N».
3. **Given** OLD offline, **When** admin пишет `/config`, **Then** запись сохраняется в облаке; когда OLD возвращается онлайн — получает change event с актуальной версией.
4. **Given** FakeRemoteSyncBackend в тесте, **When** тест записывает doc и читает обратно, **Then** возвращается тот же snapshot, schemaVersion сохраняется, observers получают изменение.

---

### User Story 3 — Consent screen на OLD и отзыв (Priority: P2)

Когда admin успешно claim'нул токен, OLD показывает экран «Admin XYZ получит доступ к: показывать текущий заряд батареи, последний онлайн, список приложений; менять список тайлов, контакты, порядок flow». Пользователь жмёт «Разрешить» или «Отклонить». В Settings лаунчера всегда виден текущий статус «Привязан к admin XYZ с DATE — отвязать» — нажатие отзывает доступ (удаляет `/links/{linkId}`, OLD заново становится свободным).

**Why this priority**: соответствие правовому контексту (Article XIV — security & privacy в constitution.md, ADR-005/006). Без явного consent'а pairing нелегален в большинстве юрисдикций. Без revoke pairing превращается в one-way door, что нарушает CLAUDE.md правило 3.

**Independent Test**: после успешного pairing'а в US-1 — открыть Settings на OLD; убедиться что виден блок «Привязан к admin <id> с <date>»; нажать «Отвязать» с двойным подтверждением; убедиться что `/links/{linkId}` удалён в Firestore, тумблер «Разрешить удалённое управление» снова в положении «выключен», admin при следующем чтении получает permission-denied.

**Acceptance Scenarios**:

1. **Given** admin claimed токен, **When** OLD рендерит consent-экран, **Then** на экране crisp-text перечислены все категории данных, к которым admin получит доступ (битмаппа списка из исходника, не свободный текст).
2. **Given** consent-экран показан, **When** пользователь жмёт «Отклонить», **Then** `/links/{linkId}` удаляется; тумблер сбрасывается; никаких остаточных данных в облаке не остаётся.
3. **Given** успешный pairing, **When** пользователь открывает Settings → «Привязка к admin», **Then** видит adminId, дату привязки, кнопку «Отвязать».
4. **Given** пользователь жмёт «Отвязать», **When** подтверждает второй диалог, **Then** `/links/{linkId}` удаляется; future-команды от admin'а отклоняются Security Rules.

---

### User Story 4 — Push-уведомления через FCM (Priority: P2)

Когда admin меняет `/config` или создаёт `/commands/{cmdId}`, OLD получает push-уведомление через FCM. OLD просыпается (если в Doze), читает изменение, реагирует (читает конфиг, выполняет команду — реальная реакция в спеках 008/009). Без FCM OLD узнавал бы об изменениях только при следующем `RESUMED`-эвенте.

**Why this priority**: критически снижает latency между «admin изменил» и «OLD увидел» с минут до секунд. Без FCM канал работает, но эргономика страдает.

**Independent Test**: запустить OLD-эмулятор в Doze (`adb shell dumpsys deviceidle force-idle`); из admin-конца записать `/links/{linkId}/commands/{cmdId}`; убедиться, что OLD получил FCM-сообщение в течение 10 секунд (Firestore Functions/extension отправляет FCM на изменение коллекций).

**Acceptance Scenarios**:

1. **Given** OLD зарегистрирован в FCM с актуальным токеном, **When** admin записывает `/config`, **Then** OLD получает FCM-сообщение data-payload (silent push) и обновляет realtime listener.
2. **Given** FCM-токен OLD'а устарел, **When** OLD выходит онлайн, **Then** он перерегистрируется в FCM, новый токен пишется в `/links/{linkId}/state.fcmToken`.
3. **Given** на устройстве OLD **нет Google Play Services** (китайская прошивка), **When** OLD запускается, **Then** лаунчер graceful-fallback на polling каждые N минут (значение N — clarification), UX-баннер в Settings «Уведомления недоступны на этом устройстве, обновления раз в N минут».

---

### User Story 5 — FakeRemoteSyncBackend для разработки и тестов (Priority: P2)

Все доменные тесты и UI-тесты используют `FakeRemoteSyncBackend` (in-memory, deterministic). Build flavor `mockBackend` (dev) собирает приложение с Fake вместо Firebase — приложение полностью работает локально без `google-services.json`. Это позволяет: (а) запускать тесты на CI без Firebase emulator, (б) разрабатывать UI флоу спеков 008/009 до того, как Firebase project готов.

**Why this priority**: соответствие CLAUDE.md правилу 6 (mock-first development). Без Fake'а тесты медленные, флаки, требуют network.

**Independent Test**: собрать APK с `assembleMockBackendDebug`, установить, пройти весь US-1 на одном эмуляторе с самим собой как admin (in-process Fake — admin/OLD оба используют один FakeRemoteSyncBackend instance с разными identity tokens), убедиться что pairing/consent/revoke работают без обращений к сети.

**Acceptance Scenarios**:

1. **Given** unit test использует `FakeRemoteSyncBackend`, **When** тест вызывает `writeDoc(path, snapshot)`, **Then** последующий `readDoc(path)` возвращает тот же snapshot.
2. **Given** Fake в режиме `mockBackend` build, **When** оба «устройства» в одном процессе пишут друг другу, **Then** listener'ы получают изменения синхронно (без сетевых задержек).
3. **Given** test использует Fake, **When** тест явно симулирует offline через `fake.setOnline(false)`, **Then** write возвращает ошибку или queue'ит (поведение — clarification).

---

### Edge Cases

- Pairing-токен пытаются использовать **дважды** одновременно (race) — Firestore transaction обеспечивает atomic claim.
- Pairing-токен **просрочен** (>5 мин) — Cloud Function чистит `claimed=false && expiresAt < now` коллекции, либо клиент удаляет при чтении.
- OLD **уходит в Doze** во время pairing — должно быть исключение из Doze на короткое время через `setExactAndAllowWhileIdle` или просто не мешать pairing-флоу (clarification).
- Admin **второй раз** сканирует тот же QR (свой собственный, для re-pairing) — должна быть UX-ветка «вы уже связаны с этим устройством», без двойной записи.
- **OLD без GMS** — fallback на polling (см. US-4, scenario 3).
- **Network** падает прямо во время pairing transaction — admin видит ошибку, может ретраить тот же токен пока не expired.
- `google-services.json` **отсутствует** в build'е (например при сборке `mockBackend` flavor) — приложение собирается, использует Fake, не падает.
- Admin **закрыл** приложение между «scan QR» и consent-confirmation OLD'а — `/pairings/{token}.claimed=true` и `/links/{linkId}` уже созданы, но если OLD «отклонил» — все эти записи удаляются.
- Пользователь **отвязал** устройство, но admin продолжает читать кэшированный `/links/{linkId}` — Security Rules должны отказать на следующем чтении.

---

## Requirements *(mandatory)*

### Functional Requirements

**Pairing**

- **FR-001**: Лаунчер MUST генерировать `oldDeviceId` (UUIDv4) при первом запуске и хранить его в DataStore.
- **FR-002**: Лаунчер MUST регистрироваться в Firebase Auth как anonymous пользователь при первом запуске (если включён real backend).
- **FR-003**: Когда OLD-пользователь включает тумблер «Разрешить удалённое управление», лаунчер MUST создавать `/pairings/{token}` с полями `{token, oldDeviceId, claimed: false, expiresAt: now + 5min}`, где `token` — 6-символьная буквенно-цифровая последовательность (без визуально похожих символов — `0`/`O`, `1`/`I`).
- **FR-004**: Лаунчер MUST показывать `token` в виде QR-кода с deep-link `launcher://pair?token=<token>` на отдельном экране с обратным отсчётом до expiry.
- **FR-005**: Admin-режим лаунчера MUST уметь читать QR-код через камеру и парсить token из deep-link.
- **FR-006**: Admin-клиент MUST выполнять «claim pairing» как **Firestore transaction**: проверить `exists`, `claimed=false`, `expiresAt > now`; атомарно установить `claimed=true`; создать `/links/{linkId}` с `{adminId, oldDeviceId, createdAt}`.
- **FR-007**: OLD MUST слушать `/pairings/{token}` и при `claimed=true` показывать consent-экран с `adminId` и фиксированным списком категорий данных (см. US-3 acceptance 1).
- **FR-008**: При «Отклонить» на consent-экране лаунчер MUST удалить `/links/{linkId}` и сбросить `/pairings/{token}.claimed = false` (или удалить токен целиком — clarification).
- **FR-009**: При «Разрешить» лаунчер MUST записать начальный snapshot в `/links/{linkId}/state` (минимум: `schemaVersion`, `appliedAt`, `presetId`).

**RemoteSyncBackend**

- **FR-010**: Лаунчер MUST предоставлять интерфейс `RemoteSyncBackend` в домене (`:core/api/sync`) со следующими операциями: `writeDoc(path, snapshot)`, `readDoc(path)`, `observe(path): Flow<DocSnapshot>`, `runTransaction(block)`, `dispose()`.
- **FR-011**: Лаунчер MUST содержать `FirebaseRemoteSyncBackend` — adapter поверх Firebase Firestore SDK (Android-only), регистрируется через DI.
- **FR-012**: Лаунчер MUST содержать `FakeRemoteSyncBackend` — in-memory deterministic реализация, регистрируется в `mockBackend` build flavor и во всех unit-тестах.
- **FR-013**: Firebase-adapter MUST **никогда не пропускать** наружу типы из Firebase SDK (`DocumentSnapshot`, `Query`, etc.) — все домены работают только с доменными `DocSnapshot`/`DocPath` типами (anti-corruption layer per CLAUDE.md rule 2).
- **FR-014**: Все доменные функции и порты MUST принимать `RemoteSyncBackend` через DI; ни одно место в домене не обращается к Firebase напрямую.

**FCM**

- **FR-015**: Лаунчер MUST регистрироваться в FCM при наличии Google Play Services; полученный fcm-token пишется в `/links/{linkId}/state.fcmToken`.
- **FR-016**: Лаунчер MUST обрабатывать silent data-push сообщения от FCM как сигнал «перечитай config/commands».
- **FR-017**: Если Google Play Services недоступны, лаунчер MUST graceful-fallback на periodic polling с интервалом N минут (значение — [NEEDS CLARIFICATION]: 5 / 15 / 30 минут?), показывать баннер в Settings «Уведомления недоступны».

**Firestore schema & security**

- **FR-018**: Wire-format `/pairings/{token}` MUST содержать поле `schemaVersion: Int` (начинается с `1`).
- **FR-019**: Wire-format `/links/{linkId}/state` MUST содержать поле `schemaVersion: Int` (начинается с `1`). (Полная схема state — это спек 008; здесь только минимальный bootstrap snapshot.)
- **FR-020**: Firestore Security Rules MUST разрешать:
  - admin (по `request.auth.uid == link.adminId`): write в `/links/{linkId}/config` и `/links/{linkId}/commands/*`; read всего `/links/{linkId}/`.
  - OLD (по `request.auth.uid == link.oldDeviceId` или эквивалент): write в `/links/{linkId}/state`, `/capabilities`, `/health`, `/commands/{cmdId}/result`; read всего `/links/{linkId}/`.
  - сторонние uid: отказ.
- **FR-021**: Security Rules MUST разрешать создание `/pairings/{token}` только аутентифицированным anonymous пользователям (чтобы избежать спама).
- **FR-022**: Все записи MUST содержать `updatedAt: Timestamp` (server-side).

**Consent & revoke**

- **FR-023**: Лаунчер MUST показывать в Settings блок «Привязка к admin» с adminId, датой привязки, кнопкой «Отвязать».
- **FR-024**: «Отвязать» MUST требовать второго подтверждения (диалог «Вы уверены? Admin потеряет доступ»).
- **FR-025**: После «Отвязать» лаунчер MUST удалить `/links/{linkId}` (или соответствующий tombstone — clarification) и переключить тумблер «Разрешить удалённое управление» в выключенное.

**Build flavor & DI**

- **FR-026**: Проект MUST иметь два build flavor'а: `realBackend` (с `google-services.json`, Firebase, FCM) и `mockBackend` (без, использует Fake). По умолчанию debug-сборки — `mockBackend`, release-сборки — `realBackend`.
- **FR-027**: Граф Koin MUST подменять реализацию `RemoteSyncBackend` в зависимости от build flavor через source-set discovery.

### Key Entities

- **PairingToken**: 6-символьный alphanumeric (`[A-HJ-NP-Z2-9]`), TTL 5 минут, single-use, хранится в `/pairings/{token}`.
- **Link**: связь admin↔OLD. Поля: `linkId`, `adminId`, `oldDeviceId`, `createdAt`. Хранится в `/links/{linkId}`. Удаление — отзыв доступа.
- **DocPath**: доменная обёртка над path в Firestore (например `Links(linkId).State`), без знания Firebase-типов.
- **DocSnapshot**: доменная обёртка над содержимым документа, содержит `data: JsonElement`, `schemaVersion`, `updatedAt`.
- **RemoteSyncBackend**: port с операциями `writeDoc`/`readDoc`/`observe`/`runTransaction`. Две реализации: `FirebaseRemoteSyncBackend`, `FakeRemoteSyncBackend`.
- **AdminIdentity**: anonymous Firebase Auth UID admin'а (для MVP). Возможный upgrade на named Google Sign-In — отложен.
- **OldIdentity**: anonymous Firebase Auth UID OLD-устройства.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Pairing-флоу от «admin тапнул scan QR» до «OLD показал consent» завершается за **≤10 секунд** при стабильной 4G сети (real Firestore).
- **SC-002**: После consent «Разрешить» admin получает первое realtime-обновление из `/links/{linkId}/state` за **≤2 секунды**.
- **SC-003**: FCM-push от admin'а к OLD доставляется за **≤10 секунд** при стабильной сети (включая Doze).
- **SC-004**: Polling-fallback (без GMS) обнаруживает изменение конфига максимум за **<NEEDS CLARIFICATION>** минут.
- **SC-005**: `FakeRemoteSyncBackend`-based unit-тесты выполняются за **≤500 мс** на CI (entire suite спека 007).
- **SC-006**: `realBackend` debug APK размер **+≤3 MB** по сравнению с `mockBackend` debug APK (firebase-firestore + firebase-messaging dependencies).
- **SC-007**: Cold start `HomeActivity` после добавления Firebase **≤650 мс** на medium-tier эмуляторе (текущий target — 600 мс, допускается delta 50 мс на init Firebase).
- **SC-008**: 0 утечек Firebase-типов наружу adapter'а (fitness function — import-restriction lint rule, проверяется в CI).
- **SC-009**: Roundtrip test passes для каждого wire-format (`PairingToken`, `LinkBootstrap`, `Commands`): write → read → assert deep-equal.
- **SC-010**: Backward-compat test passes: читатель `schemaVersion=2` без падения читает `schemaVersion=1`-snapshot.

---

## Assumptions

- Оба устройства (admin и OLD) имеют **интернет в момент pairing**. Pairing offline не поддерживается. (Если оба устройства окажутся без сети — pairing просто не начнётся, UX-сообщение «Включите интернет для связи».)
- Anonymous Firebase Auth достаточен для MVP. Реальный sign-in (Google, Phone) — за пределами этого спека.
- google-services.json коммитится в репозиторий **только для dev Firebase project**. Production google-services.json — через CI secrets (одноразовое решение позже, не блокирует этот спек).
- Firestore Security Rules деплоятся вместе с приложением (`firebase deploy --only firestore:rules`) — этот процесс описан в research.md.
- Cloud Function / Firebase Extension «отправить FCM при изменении документа» нужен — конкретная имплементация выбирается в research.md (вариант A: 1-st party Firestore-trigger Cloud Function на JS; вариант B: Trigger Email/FCM Extension; вариант C: client-side FCM с topic per link, без Cloud Function — admin отправляет push сам).
- FCM-токен может **меняться** (Google рекомендует пере-регистрацию); OLD пере-пишет `/links/{linkId}/state.fcmToken` при каждом изменении.

---

## One-way doors (CLAUDE.md правило 3)

Следующие решения внутри этого спека — необратимы в течение дней; для каждого нужен exit ramp:

### OWD-1: Firebase как backend (vs self-hosted)

- **Что фиксируется**: Firestore + FCM + Firebase Auth + (опц.) Cloud Functions как канал между устройствами.
- **Альтернативы**: Supabase (Postgres + realtime), self-hosted (own server + MQTT/WebSocket).
- **Регрет-условия**: Google поднимает цену; региональные ограничения (Россия — Firebase официально работает, но юридически серая зона); требование Self-hostable из compliance.
- **Exit ramp**: `RemoteSyncBackend` port даёт hard ACL — заменить можно одним новым adapter'ом. Все доменные коды используют только port. Wire-format JSON-based — переносится. Cost: написать новый adapter (~1 неделя), миграция /links — отдельная задача (одноразовый импорт).

### OWD-2: Anonymous Auth → named Auth

- **Что фиксируется**: admin и OLD идентифицируются anonymous UID без email/phone.
- **Альтернативы**: Google Sign-In, Phone Auth.
- **Регрет-условия**: пользователь сменил устройство — `oldDeviceId` теряется навсегда; нужен `config-portability` (см. backlog). Admin теряет доступ к своим OLD'ам если переустановил приложение.
- **Exit ramp**: при добавлении named auth — link migration «прежний anonymous UID + новый named UID = тот же admin/OLD». Firebase Auth поддерживает `linkWithCredential`. Cost: одноразовая UI-флоу «привяжите этот аккаунт к Google» для существующих пользователей.

### OWD-3: 6-char alphanumeric token

- **Что фиксируется**: формат token, длина, alphabet.
- **Альтернативы**: numeric 6-digit (как 2FA), longer alphanumeric.
- **Регрет-условия**: collision rate стал ощутимым (>0.1% при 10k pairings одновременно).
- **Exit ramp**: parser принимает любой формат если deep-link parsed корректно — длина не запекается. Можно перейти на 8 символов без миграции (старые токены валидны до expiry).

### OWD-4: `linkId` = Firestore document ID (autogenerated)

- **Что фиксируется**: linkId генерирует Firestore.
- **Альтернативы**: UUID на клиенте; child name = `{adminId}_{oldDeviceId}` (детерминированный).
- **Регрет-условия**: дубликаты link (admin случайно создал второй); хочется человекочитаемого ID для отладки.
- **Exit ramp**: linkId — opaque string во всех доменных типах; смена генерации — изменение adapter'а, домен не трогается.

### OWD-5: `mockBackend` flavor по умолчанию для debug

- **Что фиксируется**: debug-сборка не использует Firebase.
- **Альтернативы**: debug использует dev Firebase project всегда.
- **Регрет-условия**: разработчики забывают тестировать с реальным Firebase; integration-баги ловятся только в release-тестировании.
- **Exit ramp**: build flavor — gradle config, переключение — одна команда. Можно ввести `debugReal` variant если потребуется.

---

## Cleanup notes (если применимо)

Этот спек **не удаляет** legacy-код, потому что `RemoteSyncBackend` сейчас — пустой interface из спека 003 без реализаций.

Однако спек **расширяет** интерфейс, добавляя операции, которых раньше не было. Если в спеке 003 интерфейс пуст (или содержит NoOp-методы) — заменяем целиком. Если уже есть какие-то stub-методы — расширяем без удаления (clarification).

---

## Out of scope (для этого спека)

- **Применение** `/config` к локальной раскладке OLD'а (это спек 008).
- **Conflict resolution** между admin write и OLD state (это спек 008).
- **Admin-mode UI** (это спек 009: список устройств, тап → device-detail, редактирование тайлов).
- **Commands** в смысле бизнес-логики (open Play Store, refresh capabilities) — это спек 009; в 007 только инфраструктура `/commands/{cmdId}` с пустым набором типов.
- **Contacts**, e2e шифрование медиа (это спек 011).
- **Балансы**, USSD (это спек 012).
- **Offline detection с реакциями** (это спек 013).
- **DPC / Device Owner**: не использует Android Management API; pairing — soft, через consent.
- **Production Firebase project**: dev project — да, prod — отдельное решение позже.

---

## Open questions for `/speckit-clarify`

Помечены явно через `[NEEDS CLARIFICATION]` в FR-017 и SC-004 и неявно через формулировку «(clarification)» в edge cases / FR-008 / FR-025 / cleanup notes. Кратко:

1. **Polling interval без GMS** (FR-017, SC-004): 5 / 15 / 30 минут? Trade-off батарея vs latency.
2. **При «Отклонить»** на consent — удалять `/pairings/{token}` целиком или только сбросить `claimed=false`? Влияет на reuse семантику.
3. **При «Отвязать»** в Settings — hard delete `/links/{linkId}` или soft delete (tombstone с `revokedAt`)? Влияет на audit-log в будущем.
4. **`FakeRemoteSyncBackend` offline-симуляция**: write возвращает error, queue'ит до setOnline(true), или вообще нет offline-режима? Влияет на полезность Fake'а для тестов спека 013.
5. **FCM Cloud Function vs клиентский topic-broadcast**: какой механизм отправки push — Cloud Function на изменение doc, Firebase Extension, или admin-клиент сам отправляет topic-push через FCM HTTP v1 API? Trade-off: cost, security, complexity.
6. **Doze handling во время pairing**: нужен ли whitelist (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) или достаточно того, что pairing-флоу — foreground? Permissions impact.
7. **Расширение пустого `RemoteSyncBackend` из спека 003**: заменить целиком или добавить методы? (Зависит от текущего содержимого интерфейса, проверяется в research.)
8. **`linkId` — opaque string или UUID, или детерминированный hash?** Cross-ref с OWD-4 — нужно решение, чтобы не плодить tombstones при ре-пейринге.

---

## Dependencies and prerequisites

**Готово (в main):**
- Спек 005 — `ActionWireFormat.json` (kotlinx.serialization Json instance используется и здесь).
- Спек 006 — `Capability`, `HealthSnapshot` (этот спек **не** трогает их wire-format, только готовит канал для будущего экспорта).
- Спек 003 — `RemoteSyncBackend` port (пустой / NoOp — будет расширен).
- Koin DI, KMP build (per ADR-005, спек 004).

**Требуется к запуску этого спека:**
1. **Создать Firebase project** (один dev-проект). Owner — project owner.
2. **Скачать `google-services.json`** для package `com.work.launcher` (или текущее appId).
3. **Установить Firebase CLI** локально (для деплоя rules + emulator suite для интеграционных тестов).
4. **Обновить `docs/compliance/country-legal-tax-register.md`**: новые типы персональных данных (anonymous UID, fcm-token, adminId — всё псевдонимы, но требует учёта).
5. **Обновить `docs/compliance/permissions-and-resource-budget.md`**: добавить `INTERNET`, `ACCESS_NETWORK_STATE` (уже есть в манифесте?), `POST_NOTIFICATIONS` (Android 13+ для FCM).

**Не блокирует, но желательно до старта:**
- ADR на «Anonymous Auth как MVP, migration to named Auth — будущий спек». Decision-log в research.md.

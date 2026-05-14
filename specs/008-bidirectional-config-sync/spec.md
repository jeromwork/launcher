# Feature Specification: Bidirectional Config Sync (двусторонняя синхронизация конфига)

**Feature Branch**: `008-bidirectional-config-sync`
**Created**: 2026-05-14
**Status**: Draft (rev. 2 — после Q1-Q2 clarify: collaborative editing model)
**Input**: roadmap §008 (`docs/product/roadmap.md` lines 168–188) — admin's `/config` ↔ Managed's `/state` + multi-editor collaborative-edit с optimistic concurrency и merge UI.

---

## Контекст (для не-разработчика)

После спека 007 у нас уже есть «связь» (link) между двумя телефонами:
- телефон **admin'а** (взрослый родственник, обычно внук),
- телефон **Managed** (пожилой пользователь, в режиме лаунчера).

В спеке 007 admin может только **видеть**, что связь установлена. Реальное «что показывать на главном экране» (раскладка плиток, контакты, потоки flow/slot) — пока mock из спека 003.

Спек 008 даёт **возможность редактировать раскладку** Managed-телефона с любого устройства, привязанного к этой паре admin↔Managed:
- с **телефона admin'а** (типичный случай — удалённо, по сети),
- с **планшета admin'а** (тот же admin Firebase-account, второе устройство),
- с **самого Managed-телефона** (admin часто будет «брать его в руки» и настраивать напрямую — это нормальный путь, не исключение).

Все три типа устройств — **равноправные редакторы (editors)**. Это первая фича, где данные ходят **во все стороны**, и где возможны **параллельные правки**.

**Ключевая особенность.** Между «сохранить локально» и «отправить на сервер» — два **раздельных** действия пользователя. Можно сохранить локально и не пушить неделю; в это время кто-то с другого устройства может изменить тот же `/config` — тогда при push увидим **diff (различия) + merge UI**.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Admin меняет раскладку с одного устройства (Priority: P1)

Admin редактирует на своём телефоне раскладку «бабушкиного» экрана — например, добавляет плитку «позвонить внучке». Нажимает **«save локально»** (изменение сохранено только на admin-телефоне). Затем нажимает **«push на сервер»** — изменение улетает в Firestore, по push-каналу из 007 доставляется на Managed, применяется. Admin видит в своём UI подтверждение «применено».

**Why this priority**: базовая ценность спека. Без P1 — admin не может ничего реально изменить.

**Independent Test**: на работающем спеке 007 admin меняет один параметр (например, `presetId`), save локально → push → Managed получает push → применяет → `/state` обновляется → admin UI показывает «применено».

**Acceptance Scenarios**:

1. **Given** admin и Managed связаны, Managed online, никто параллельно не редактирует, **When** admin делает «save локально» затем «push на сервер», **Then** push проходит без conflict UI, и в течение T-PUSH Managed применяет, `/state.appliedConfigUpdatedAt` обновляется.
2. **Given** конфиг применён, **When** admin читает `/state/current`, **Then** видит applied-snapshot, совпадающий с реальным экраном Managed'а (не тот `/config`, который отправил — если partial-apply, видны различия).

---

### User Story 2 — Merge при параллельных правках (Priority: P1)

Admin с **телефона** делает «save локально» (изменение плитки «Маша»), но **не пушит**. В это же время admin с **планшета** меняет другую плитку и **пушит**. Когда позже admin на телефоне нажмёт «push на сервер» → обнаруживается, что сервер ушёл вперёд → показывается **merge UI** с diff'ом: «вы поменяли плитку Маша, на сервере поменяли плитку Петя, что оставить?»

**Why this priority**: без корректного conflict-resolution параллельные правки приведут к **молчаливой потере данных** (одно изменение перетрёт другое). Это критично — `/config` управляет тем, что видит пожилой пользователь, потерять плитку «вызов экстренной помощи» недопустимо.

**Independent Test**: два устройства редактируют `/config` параллельно; первое пушит, второе пушит → второе видит merge UI, юзер выбирает, save → итоговый `/config` содержит выбранную комбинацию.

**Acceptance Scenarios**:

1. **Given** оба admin-устройства имеют snapshot `/config` с `updatedAt = T0`, **When** устройство A пушит изменение (server `updatedAt → T1`), **When** устройство B затем пытается push с `clientSnapshotUpdatedAt = T0`, **Then** push отклоняется (FR-020), и устройству B показывается merge UI с server-side изменениями A.
2. **Given** конфликт показан, **When** пользователь делает выбор в merge UI и нажимает save, **Then** клиент перечитывает server (новый `updatedAt = T1`), пушит с `clientSnapshotUpdatedAt = T1` → push проходит.
3. **Given** два устройства изменили **разные** элементы `/config` (плитки с разными id), **When** оба пушат, **Then** второй push видит конфликт по `updatedAt`, но merge UI показывает: «изменения не пересекаются, применить оба?» — auto-mergeable case.
4. **Given** оба устройства **удалили одну и ту же плитку** (один id), **When** оба пушат, **Then** второй push видит конфликт по `updatedAt`, но diff пуст (оба удалили то же самое) — авто-резолв, без UI.
5. **Given** устройство A удалило плитку, устройство B изменило её, **When** оба пушат, **Then** второй push показывает merge UI с конфликтом «удалить vs изменить», юзер решает.

---

### User Story 3 — Managed-телефон как редактор (Priority: P1)

Admin физически взял Managed-телефон в руки (или бабушка прошла через защищённый вход — 7 тапов + пароль, как в спеке 003), открыл Settings прямо на Managed, изменил плитку, сделал save + push. Изменения летят на сервер так же, как с admin-устройства. С точки зрения collaborative editing — Managed это **обычный editor**, не специальный.

**Why this priority**: основной путь первичной настройки — admin берёт Managed в руки и настраивает на месте. Без P3 спек игнорирует главный реальный use case.

**Independent Test**: на Managed-телефоне войти в Settings (7 тапов + пароль), изменить плитку, save + push → запись `/config` на сервере содержит изменение, admin (на своём устройстве) при следующем чтении видит изменение.

**Acceptance Scenarios**:

1. **Given** admin физически держит Managed-телефон, никто параллельно не редактирует, **When** в Settings меняет плитку, делает save + push, **Then** push проходит, `/config` на сервере обновлён.
2. **Given** Managed редактирует локально (save локально, не push), параллельно admin с другого устройства пушит изменение, **When** Managed нажимает push, **Then** показывается **тот же самый merge UI**, что на admin-устройстве (единая реализация, не senior-safe-вариант).
3. **Given** Managed offline, **When** admin делает save локально на Managed, **Then** save проходит, баннер «pending push» появляется; при возврате online push не делается автоматически — пользователь сам должен нажать.

---

### User Story 4 — Pending changes warning (Priority: P1)

Admin сделал «save локально», но не нажал «push на сервер». Закрыл приложение. Через день/неделю/месяц открывает снова → **на главном экране** в списке привязанных Managed-телефонов телефон, для которого есть pending, **помечен значком** «есть несинхронизированные изменения». Войдя в Settings этого телефона — баннер «у вас локальные изменения, не запушено на сервер». Pending живёт **сколь угодно долго** — никакого авто-discard, никакого авто-push. Пользователь сам решает.

**Why this priority**: без этого admin может забыть про save локально и не понять, почему изменения не на Managed. Visibility — обязательное условие, иначе данные «теряются» с точки зрения пользователя.

**Independent Test**: сделать save локально без push, закрыть приложение, открыть снова → проверить, что в списке Managed-телефонов pending-значок виден; войти в Settings → проверить, что баннер виден.

**Acceptance Scenarios**:

1. **Given** save локально сделан, push не сделан, **When** приложение убито и запущено заново, **Then** на главном экране списка Managed-телефонов pending-телефон помечен значком «pending push».
2. **Given** pending существует 30+ дней, **When** пользователь открывает приложение, **Then** изменения по-прежнему доступны, никакого авто-discard.
3. **Given** есть pending, **When** пользователь push'ит и сервер за это время изменился, **Then** показывается merge UI (FR-020 / US-2).

---

### User Story 5 — Persisted config на Managed (Room) (Priority: P2)

Managed хранит применённый конфиг локально (Room database), чтобы после рестарта/process death показать тот же экран без сетевого запроса.

**Why this priority**: критично для production-надёжности, но не блокирует P1-P4 (те могут работать с in-memory storage). Без P5 после process kill бабушка увидит «загрузка» или mock-экран — приемлемо для MVP.

**Independent Test**: применить конфиг → убить процесс → запустить заново → главный экран сразу показывает applied-config без обращения к Firestore.

**Acceptance Scenarios**:

1. **Given** конфиг применён, **When** процесс Managed убит, запущен снова, **Then** UI отрисовывается из локального хранилища с тем же `presetId` и flows.
2. **Given** local store содержит конфиг schemaVersion N, **When** приложение обновлено до reader schemaVersion N+1 (additive), **Then** старый конфиг читается без ошибок.

---

### User Story 6 — Расширение раскладки: flows / slots / contacts (Priority: P2)

Admin может настраивать не только preset, но и сами потоки (flow), их слоты (slot), контакты. Все эти элементы имеют стабильный `id` (UUID v4, client-generated) — что обеспечивает корректный diff/merge.

**Why this priority**: для MVP достаточно P1 (один параметр меняется end-to-end). Полная раскладка — расширение.

**Independent Test**: после P1 — admin меняет один flow (добавляет slot с новым UUID id), save + push → Managed применяет → `/state.flowsApplied` отражает.

**Acceptance Scenarios**:

1. **Given** P1-P4 работают, **When** admin записывает `/config` с новым slot (UUID id) в одном из flows, **Then** Managed применяет, `/state.flows` отражает применённое.
2. **Given** admin записал контакты в `/config`, **When** Managed применяет, **Then** только эти контакты показаны в раскладке (старые контакты с другими id удаляются).

---

### Edge Cases

- **Partial apply на Managed** (provider недоступен, контакт-permission отозван). `/state` отражает реально применённое + `partialApplyReasons[]`. Admin-UI рендерит из `/state`, не из `/config`.
- **Schema mismatch** (admin v2 ↔ Managed v1): **out of scope 008** — управляется отдельным спеком `app-version-compatibility` (см. OUT-006). В 008 тестирование монорелизом — все editor'ы одной версии, schema mismatch by construction не возникает. Backward-compat reads (CLAUDE.md §5) сохраняются — additive fields без bump schemaVersion.
- **Конкурентная запись из двух editor'ов** — покрыто FR-020 (optimistic concurrency на `updatedAt`).
- **UUID-коллизия** (два editor'а сгенерировали одинаковый UUID v4 — вероятность ~0): обрабатывается как обычный element-conflict в merge UI.
- **Revoke во время apply**: link удалён → запись `/config` отклоняется Security Rules.
- **Огромный список контактов** (~500 контактов): Firestore лимит документа ≤ 1 MiB. [NEEDS CLARIFICATION Q10: какой soft-limit и UI-предупреждение].
- **Roll-back** (откат к предыдущему `/config`): **out of scope 008** — встроено в спек 009 (см. OUT-007). В эпоху 008 — только ручное восстановление.
- **Сеть рвётся в момент push**: push retry с проверкой `updatedAt` (если за время retry сервер ушёл — конфликт, merge UI).
- **App killed между save локально и push**: pending state сохраняется (Room), баннер при следующем запуске (US-4).
- **Pending существует месяц, server тем временем менялся 5 раз**: при push — стандартный merge UI, без особой обработки «старого» pending.

---

## Requirements *(mandatory)*

### Functional Requirements

**Wire format & versioning**

- **FR-001**: `/links/{linkId}/config/current` MUST содержать поле `schemaVersion` (Int) с первого коммита.
- **FR-002**: `/config/current` MUST содержать `serverUpdatedAt` (server-set timestamp) — используется как `version` для optimistic concurrency.
- **FR-003**: `/config/current` MUST включать (минимум): `schemaVersion`, `serverUpdatedAt`, `lastWriterDeviceId`, `presetId`, `flows[]` (с `slots[]`), `contacts[]`.
- **FR-004**: Каждый элемент `/config` (flow, slot, contact, plate, тиле — любое, что может появиться/исчезнуть/измениться независимо) MUST иметь стабильный `id` (UUID v4, client-generated at creation time).
- **FR-005**: Спек MUST включать roundtrip-test (`write v1 → read v1 → deep-equal`) и backward-compat read-test (`reader v2 reads v1`) для `/config` и `/state` (per CLAUDE.md §5).
- **FR-006**: Field additions MUST быть additive (новые опциональные поля без bump schemaVersion). Rename/remove полей requires schemaVersion bump + reader-migration в Phase 0 следующего спека.

**Channel admin/Managed/tablet → server (push с conflict check)**

- **FR-010**: Любой editor (admin-phone / admin-tablet / Managed-phone) MUST уметь записать `/links/{linkId}/config/current` через Firestore, при наличии действительного `linkId` и Firebase Auth UID, входящего в whitelist link'а (adminId или managedDeviceFirebaseUid).
- **FR-011**: Security Rules для `/config/current` MUST разрешать write для `adminId` И для `managedDeviceFirebaseUid` (оба — equal editors). Update спека 007 Security Rules — обязательно в Phase 0.
- **FR-012**: При push на сервер клиент MUST передать `clientSnapshotUpdatedAt` (значение `serverUpdatedAt`, прочитанное клиентом перед редактированием).
- **FR-013**: Server-side check (Firestore transaction или precondition): если `clientSnapshotUpdatedAt != serverUpdatedAt` в момент write — write отклоняется (PERMISSION_DENIED или transaction abort).
- **FR-014**: При отклонении (FR-013) клиент MUST прочитать актуальный `/config`, вычислить **diff** между (а) своим local-state и (б) актуальным server-state, и показать **merge UI**.

**Channel server → Managed (apply pushed config)**

- **FR-020**: При успешной записи `/config/current` Cloudflare Worker (из 007) MUST отправить FCM-пуш с payload type `config.updated` на topic `link-{linkId}`.
- **FR-021**: Managed MUST на FCM `config.updated` прочитать `/config/current` и применить атомарно (всё или ничего на уровне локального хранилища).
- **FR-022**: Trigger для apply проверки на Managed (определён в Q1 clarify):
  - On Activity#onResume (launcher visible),
  - Throttle: не чаще раза в **2 минуты**,
  - Periodic fallback (если экран долго не включают): WorkManager polling **15 минут** (consistent с FR-018 спека 007).
- **FR-023**: Если Managed сам только что был writer (только что pushed свою версию `/config`), он MUST избежать double-apply (apply уже сделан перед push'ем) — определяется по `lastWriterDeviceId == self`.

**Channel Managed → server (state publish)**

- **FR-030**: После apply Managed MUST обновить `/links/{linkId}/state/current` с applied-snapshot.
- **FR-031**: `/state/current` MUST содержать `appliedConfigUpdatedAt` (значение `serverUpdatedAt` из `/config`, который применили) — admin-UI этим полем понимает «какая версия применена».
- **FR-032**: `/state/current` MUST расширять схему 007 additive (без bump schemaVersion).
- **FR-033**: `/state/current` MUST отражать только реально применённое (не отправленное). Partial-apply видим через `partialApplyReasons[]`.
- **FR-034**: При revoke (FR-033 спека 007) `/config/current` и `/state/current` MUST быть удалены рекурсивно — TODO `Link.kt` спека 007 расширяется новым subcollection.

**Local persistence + pending state**

- **FR-040**: На каждом editor (admin-phone / admin-tablet / Managed-phone) MUST быть **раздельные действия** «save локально» (мгновенно, всегда успешно) и «push на сервер» (с FR-013 check).
- **FR-041**: Managed MUST хранить **applied-config** локально (Room) для fast bootstrap при process restart (US-5).
- **FR-042**: Каждый editor (вкл. Managed-as-editor) MUST хранить **pending-local-changes** (Room) — локальные изменения, прошедшие «save локально» но не «push на сервер».
- **FR-043**: Pending state MUST жить **сколь угодно долго** — никакого авто-discard, никакого авто-push. Только явное действие пользователя (push, discard в UI).
- **FR-044**: При process restart editor MUST читать applied-config до показа UI (FR-041), и проверять наличие pending для предупреждения (FR-046).
- **FR-045**: Managed-приложение MUST при первом запуске после обновления до 008-кода **удалить** любые legacy mock-storage artifacts (JSON-файлы из спека 003, in-memory stub state), **не пытаясь** их конвертировать в Room. Обоснование (Q6 clarify, 2026-05-14): спек 003 не достиг production-пользователей; mock-данные не являются валидным `/config` (нет UUID id, нет server-полей, нет linkId-привязки) — попытка migration создала бы «фейковые» applied-config без пары на сервере; CLAUDE.md §4 (Minimum Viable Architecture) — не добавляем abstraction для несуществующего use case.
- **FR-046**: Если в local store есть pending для какого-то Managed-телефона, **главный экран** editor-приложения MUST показывать **визуальный маркер** «pending push» рядом с этим телефоном в списке привязанных устройств.
- **FR-047**: При входе в Settings конкретного Managed-телефона, если для него есть pending, MUST показываться **баннер** «у вас локальные изменения, не запушено на сервер» с действиями «push сейчас» / «отменить локальные изменения».

**Merge UI**

- **FR-050**: Merge UI MUST быть **единым** для всех editor'ов (admin-phone / admin-tablet / Managed-phone) — одна реализация, без senior-safe-варианта. Обоснование (зафиксировано в Q2 clarify): вход в Settings уже защищён 7-тапами + паролем, пользователь осознанно туда зашёл и способен разобрать конфликт.
- **FR-051**: Merge UI MUST показывать **по-элементно** (по id) различия между local-pending и server-current: для каждого изменённого элемента — local-value, server-value, действие пользователя (keep local / keep server / для some types — keep both).
- **FR-052**: Если diff пуст (оба editor'а сделали идентичные изменения — например, оба удалили одну и ту же плитку с одним id) — merge UI **не показывается**, push проходит автоматически.
- **FR-053**: Если diff содержит только **непересекающиеся** изменения (разные id) — merge UI показывается с пометкой «изменения не пересекаются, применить оба?», действие default = «применить оба».
- **FR-054**: После merge-выбора клиент MUST перечитать `/config` (для свежего `serverUpdatedAt`) и сделать новый push с актуальным `clientSnapshotUpdatedAt`. Если за время merge сервер снова изменился — второй раунд merge UI.

**Out of scope (для предотвращения скоупа)**

- **OUT-001**: Полный admin UI editor (флоу, drag-and-drop, design polish) — это spec 009 (`admin-mode-flows`). 008 даёт wire-format, conflict logic, merge UI, базовые controls для save/push. UI-внешность 008 — минимальная функциональная.
- **OUT-002**: Commands (create/delete/move tiles через imperative push команды) — это spec 009. 008 — declarative `/config` only. (Roadmap line 185 open question — закрыто в этом clarify: 009.)
- **OUT-003**: ~~Roll-back механизм~~ → см. OUT-007 (встроено в спек 009).
- **OUT-004**: Provider capabilities / health subcollections — extension спека 006, не зависит от 008.
- **OUT-005**: Live notifications «server изменился, посмотри» (background-listener на сервере) — НЕ нужно. Diff обнаруживается **только** при push (зафиксировано в Q2 clarify).
- **OUT-006**: **App version compatibility management** — отдельный будущий спек (см. roadmap §Backlog `app-version-compatibility`). 008 НЕ содержит: detection несовместимых версий приложения, поля `requiredManagedAppVersion`/`managedAppVersion`/`compatibilityError` в wire-format, visibility на admin UI про устаревшее приложение Managed'а, механизмы remote-update. Обоснование (Q4 clarify): в текущей фазе все editor'ы тестируются монорелизом — schema mismatch by construction не возникает; полноценная инфраструктура версионности требует отдельной проработки (Play Store update flows, выбор конкретной версии, OEM-quirks).
- **OUT-007**: **Config history + rollback** — встроено в spec 009 `admin-mode-flows` (Q7 clarify, 2026-05-14). 008 НЕ содержит: subcollection `/config/history/*`, UI просмотра истории, действие «откатить к версии». Восстановление после ошибочного push в эпоху 008 — только ручное (admin вспоминает, что было, и пишет заново). Обоснование: спек 008 уже большой (5-7 недель), history+rollback — это UI-фича admin'а, естественно живёт в 009 (полноценный admin UI editor). Retention: 10 версий (закрепляется в 009). См. roadmap §Spec 009.

### Key Entities

- **ConfigDocument** (`/links/{linkId}/config/current`): editable layout document. Поля: `schemaVersion`, `serverUpdatedAt`, `lastWriterDeviceId`, `presetId`, `flows[]`, `contacts[]`. Каждый элемент имеет UUID id.
- **StateDocument** (`/links/{linkId}/state/current`): applied-snapshot Managed'а. Поля (расширение к bootstrap 007): + `appliedConfigUpdatedAt`, `flowsApplied[]`, `contactsApplied[]`, `partialApplyReasons[]`.
- **LocalAppliedConfig** (Managed, Room): копия последнего applied-config для fast bootstrap.
- **PendingLocalChanges** (любой editor, Room): локально-сохранённые но не запушенные изменения. Содержит: snapshot `serverUpdatedAt` (на момент начала редактирования) + текущий локальный draft.
- **ConfigDiff** (in-memory, domain): результат сравнения двух ConfigDocument по id — список added/removed/modified элементов.
- **ConfigApplier** (Managed domain port): применяет ConfigDocument к UI/storage; возвращает applied-snapshot для публикации в /state.
- **ConfigEditor** (любой editor, domain port): операции save локально / push на сервер / merge resolution.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: End-to-end latency «save локально + push (no conflict)» → «Managed applied + /state updated» MUST быть < [NEEDS CLARIFICATION Q8: 5s / 10s, учитывая FCM p95 спека 007 SC-001].
- **SC-002**: При offline-Managed (≥ 30 минут) и последующем online — apply последней `/config` MUST произойти в течение [NEEDS CLARIFICATION Q8: ~10s] после восстановления связи (через FCM или RESUMED-trigger).
- **SC-003**: 100% push'ей приводят к /state update **или** к visible merge UI — нет «тихих» потерь. Метрика: 100 push'ей → 100 итоговых исходов (либо state, либо merge), 0 silent failures.
- **SC-004**: После process death Managed bootstrap-time (Activity#onCreate → first frame с applied-config) < [NEEDS CLARIFICATION Q8: 500ms / 1s].
- **SC-005**: Wire-format roundtrip и backward-compat read tests — 100% green.
- **SC-006**: ~~schema mismatch behavior~~ — вынесено в отдельный спек (OUT-006); в 008 все editor'ы тестируются монорелизом, SC-006 неактивен.
- **SC-007**: Diff/merge correctness — формальные test cases для всех edge cases из этого спека (Q2 acceptance scenarios 1-5 + section Edge Cases): 100% green.
- **SC-008**: Pending visibility — 100% editor'ов с pending для какого-либо Managed-телефона показывают маркер на главном экране (per FR-046). Test: создать pending, перезапустить app, проверить наличие маркера.

---

## Assumptions

- Spec 007 в main — pairing, FCM-push, Security Rules infra работают (verified: PR #7 merged, `46eb5de`).
- Cloudflare Worker (push-relay) расширяется новым payload type `config.updated` — additive.
- Security Rules спека 007 расширяются: write на `/config/current` для adminId И для managedDeviceFirebaseUid (FR-011).
- Все три типа editor'ов (admin-phone, admin-tablet, Managed-phone) — одно и то же приложение, разные deployment-конфигурации. Merge UI — общий код.
- Managed использует Room (AndroidX) — adapter в infrastructure-слое, domain читает через port (CLAUDE.md §1).
- UUID v4 collision probability считается практически нулевой (но обрабатывается как обычный element-conflict).
- Спек НЕ вводит новых внешних SDK сверх 007 + Room.
- Admin UI editor в 008 — функционально-минимальный (save / push кнопки, list of changes); полноценный визуальный editor — spec 009.

---

## Open Questions (для дальнейшего clarify Q3-Q10)

> Q1 и Q2 уже отвечены в этой ревизии spec.md. Остальные:

3. ~~**Форма `appliedConfigRef` в /state**~~ → **РЕШЕНО**: используется `appliedConfigUpdatedAt` (server-set timestamp). Зафиксировано в FR-031 и FR-002.
4. ~~**Schema mismatch**~~ → **РЕШЕНО**: вынесено в отдельный будущий спек `app-version-compatibility` (см. OUT-006 и roadmap §Backlog). В 008 — out of scope, тестируется монорелизом.
5. ~~**Concurrent admin writes**~~ → **РЕШЕНО**: optimistic concurrency на `serverUpdatedAt` + merge UI. См. FR-013/014/050-054.
6. ~~**Migration спека 003 → 008**~~ → **РЕШЕНО**: cleanup at first launch (вариант A). См. FR-045. Обоснование: 003 не в production, mock-данные не валидны как `/config`.
7. ~~**Roll-back**~~ → **РЕШЕНО**: встроено в спек 009 `admin-mode-flows` как config history + rollback (retention 10 версий). См. OUT-007 и roadmap §Spec 009.
8. **SC-001 / SC-002 / SC-004 значения**: какие конкретные числа?
9. ~~**T-POLL для no-GMS fallback**~~ → **РЕШЕНО**: 15 минут WorkManager + RESUMED trigger throttled 2 min. См. FR-022.
10. **Лимит размера `/config`** (edge case ~500 контактов, Firestore документ ≤ 1 MiB): какой soft-limit и UI-предупреждение?

**Остаётся обсудить:** Q8, Q10.

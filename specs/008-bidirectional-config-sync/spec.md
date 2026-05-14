# Feature Specification: Bidirectional Config Sync (двусторонняя синхронизация конфига)

**Feature Branch**: `008-bidirectional-config-sync`
**Created**: 2026-05-14
**Status**: Draft
**Input**: roadmap §008 (`docs/product/roadmap.md` lines 168–188) — applies admin's `/config` to Managed, publishes back `/state` as source of truth for admin UI.

---

## Контекст (для не-разработчика)

После спека 007 у нас уже есть «связь» (link) между двумя телефонами:
- телефон **admin'а** (взрослый родственник),
- телефон **Managed** (пожилой пользователь, в режиме лаунчера).

В спеке 007 admin может только **видеть**, что связь установлена и какой preset выбран на Managed'е. Реальное «что показывать на главном экране» (раскладка плиток, контакты, потоки flow/slot) — пока mock из спека 003.

Спек 008 даёт admin'у возможность **редактировать раскладку удалённо** и видеть, **что реально применилось** на телефоне у пожилого пользователя. Это первая фича, где данные ходят в обе стороны: admin → Managed (конфиг) и Managed → admin (отчёт о применении).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Admin меняет раскладку, Managed применяет (Priority: P1)

Admin редактирует на своём телефоне раскладку «бабушкиного» экрана — например, добавляет плитку «позвонить внучке». После сохранения изменение приходит на телефон Managed (по push'у из 007), применяется и появляется на главном экране. Admin видит подтверждение «применено» в своём UI.

**Why this priority**: это базовая ценность всей фичи дистанционного управления. Без P1 спек 008 бесполезен — admin не может ничего реально изменить.

**Independent Test**: при работающем спеке 007 (pairing + push) admin меняет один параметр конфига (например, presetId) → push доставляется на Managed (FCM, или fallback poll при отсутствии GMS — C13 из 007) → Managed применяет → `/state/current` обновляется → admin UI показывает «применено». MVP-достаточно для одного типа изменения.

**Acceptance Scenarios**:

1. **Given** admin и Managed связаны (link существует) и Managed online, **When** admin записывает новый `/config/current` с другим `presetId`, **Then** в течение T-PUSH (определяется в clarify) на Managed приходит уведомление, конфиг применяется, и `/state/current.presetId` обновляется на новое значение.
2. **Given** конфиг применён, **When** admin читает `/state/current`, **Then** admin видит applied-snapshot, совпадающий с тем, что реально на главном экране Managed'а, а не тот `/config`, который он отправил.
3. **Given** admin отправил `/config`, **When** Managed offline, **Then** в admin-UI `/state` остаётся прежним (не показывает «применено»); при возврате Managed online — Managed синхронизируется и `/state` обновляется.

---

### User Story 2 — Managed offline получает устаревший конфиг (Priority: P1)

Managed был offline какое-то время. За это время admin успел изменить конфиг **дважды** (например, сначала добавил плитку, потом убрал). Когда Managed возвращается online, он должен применить **последнюю** версию `/config`, а не воспроизводить промежуточные состояния.

**Why this priority**: без корректной conflict-resolution Managed может оказаться в несогласованном состоянии или применить устаревшие данные. Это критично для пользовательской безопасности (вдруг admin специально убрал какую-то плитку).

**Independent Test**: записать `/config` версии A, затем B, при offline Managed'е → перевести Managed в online → проверить, что применилась B и `/state` отражает B.

**Acceptance Scenarios**:

1. **Given** Managed offline, admin записал `/config` A, затем B, **When** Managed подключается, **Then** Managed читает текущий `/config` (B) и применяет именно его.
2. **Given** Managed применил конфиг, **When** admin читает `/state`, **Then** в `/state` есть поле, идентифицирующее **какую именно версию конфига** Managed применил (например, hash или versionId — точная форма в clarify).

---

### User Story 3 — Расширение раскладки: flows / slots / contacts (Priority: P2)

Admin может настраивать не только preset, но и сами **потоки (flow)** и их **слоты (slot)** — какие действия доступны бабушке. Также — какие **контакты** показывать.

**Why this priority**: для MVP достаточно P1 (один параметр меняется end-to-end). Полная раскладка — расширение, поэтому P2.

**Independent Test**: после успешного P1 — admin меняет один flow (добавляет slot) → Managed применяет → `/state` отражает новый flow → admin-UI показывает применённый flow.

**Acceptance Scenarios**:

1. **Given** P1 работает, **When** admin записывает `/config` с новым набором slots в одном из flows, **Then** Managed применяет, и `/state.flows` отражает применённые slots.
2. **Given** admin записал контакты в `/config`, **When** Managed применяет, **Then** только эти контакты показаны в раскладке (старые удаляются согласно schema).

---

### User Story 4 — Persisted config на Managed (Room) (Priority: P2)

Managed хранит применённый конфиг локально (Room database), чтобы после рестарта/process death показать тот же экран без сетевого запроса.

**Why this priority**: critical for реальной надёжности, но не блокирует P1 (тот может работать с in-memory store). После P1 это первое, что нужно для production-ready.

**Independent Test**: применить конфиг → убить процесс → запустить заново → проверить, что главный экран сразу показывает применённый конфиг без обращения к Firestore.

**Acceptance Scenarios**:

1. **Given** конфиг применён, **When** процесс Managed убит (process death), **When** запущен снова, **Then** UI отрисовывается из локального хранилища с тем же `presetId` и flows, что были до убийства.
2. **Given** локальное хранилище содержит конфиг schemaVersion N, **When** приложение обновилось до версии с reader schemaVersion N+1 (additive), **Then** старый конфиг читается без ошибок и без потери полей.

---

### Edge Cases

- **Managed применить не смог** (partial apply: один из provider'ов недоступен, контакт-permission отозван). `/state` должен отразить, что **реально применилось**, и какая часть failed — admin-UI показывает только реальное состояние.
- **Schema mismatch**: admin записал `/config` schemaVersion N+1, Managed на старой версии приложения умеет только N. Поведение в clarify (отклонить vs применить additive-поля как unknown).
- **Конкурентная запись admin'ом** в /config из двух устройств одновременно (admin переустановил приложение). Last-write-wins via `updatedAt`, либо optimistic-concurrency через precondition (clarify).
- **Revoke во время apply**: admin начал запись, в этот момент Managed выполнил revoke (FR-033 из 007). `/links/{linkId}` удалён → запись `/config` должна быть отклонена Security Rules.
- **Огромный список контактов** (например, 500 контактов в одном `/config`). Лимиты Firestore: документ ≤ 1 MiB. В clarify — лимит на размер `/config`, и поведение при превышении.
- **Roll-back**: admin отправил «плохой» конфиг (например, пустой flow без slot'ов). На Managed применилось — у бабушки пустой экран. Возможность отката (восстановить предыдущий `/config`) — в clarify, возможно out of scope.

---

## Requirements *(mandatory)*

### Functional Requirements

**Channel: admin → Managed (config push)**

- **FR-001**: Admin MUST быть способен записать `/links/{linkId}/config/current` через Firestore, при наличии действительного `linkId` и Firebase Auth UID, совпадающего с `adminId` из `/links/{linkId}` (Security Rules из 007).
- **FR-002**: `/config/current` MUST содержать поле `schemaVersion` (Int) с первого коммита.
- **FR-003**: При успешной записи `/config/current` Cloudflare Worker (из 007) MUST отправить FCM-пуш на topic `link-{linkId}` с типом payload `config.updated`.
- **FR-004**: Managed MUST при получении пуша `config.updated` прочитать `/config/current` и применить его атомарно (всё или ничего — на уровне локального хранилища, не на уровне provider'ов).
- **FR-005**: При отсутствии GMS на Managed (C13 из 007) MUST работать fallback: периодический poll `/config/current` с интервалом [NEEDS CLARIFICATION: T-POLL — взять из спека 007 или иное?].

**Channel: Managed → admin (state publish)**

- **FR-006**: После apply (успешного или partial) Managed MUST обновить `/links/{linkId}/state/current` с актуальным applied-snapshot.
- **FR-007**: `/state/current` MUST содержать поле, идентифицирующее версию `/config`, которая была применена ([NEEDS CLARIFICATION: `appliedConfigHash` / `appliedConfigUpdatedAt` / `appliedConfigVersion`]).
- **FR-008**: `/state/current` MUST расширять схему spec 007 additive (без bump'а schemaVersion с 1 до 2 — per `state-bootstrap.md` §Backward compatibility).
- **FR-009**: `/state/current` MUST отражать только то, что **реально применилось** на Managed (не то, что admin отправил). Partial-apply MUST быть видимым (admin-UI рендерит из `/state`, не из `/config`).
- **FR-010**: При revoke (FR-033 из 007) `/state/current` и `/config/current` MUST быть удалены рекурсивно — должно быть учтено в TODO `Link.kt` из 007 (список known subcollections для recursive-delete).

**Wire format & schema**

- **FR-011**: `/config/current` MUST включать (минимум): `schemaVersion`, `presetId`, `flows[]` (с `slots[]`), `contacts[]`, `updatedAt` (server-set), `updatedBy` (adminId).
- **FR-012**: Спек MUST включать roundtrip-test (`write v1 → read v1 → assert deep-equal`) для `/config` и `/state` (per CLAUDE.md правило 5 и Article VII).
- **FR-013**: Спек MUST включать backward-compat read-test (`future reader v2 reads v1`) для `/config` и `/state`.
- **FR-014**: Field additions MUST быть additive (новые опциональные поля, без bump schemaVersion). Rename/remove полей requires schemaVersion bump 1 → 2 и reader-migration в Phase 0 следующего спека.

**Persistence (Managed)**

- **FR-015**: Managed MUST хранить последний applied-config локально (Room DB), чтобы после process death рендерить главный экран без сетевого запроса.
- **FR-016**: Локальная схема Room MUST иметь migration-path от mock-JSON storage спека 003 — [NEEDS CLARIFICATION: cleanup при первом запуске после обновления или явная migration функция].
- **FR-017**: При process restart Managed MUST читать local applied-config до того, как покажет UI (no white-flash, no «applying default» при наличии локально сохранённого конфига).

**Conflict resolution**

- **FR-018**: Managed offline → admin writes /config A → admin writes /config B → Managed online: Managed MUST применить B (current state of /config), не воспроизводить A.
- **FR-019**: Если admin записал `/config` с `schemaVersion` выше, чем умеет читать Managed, поведение: [NEEDS CLARIFICATION: reject (Managed пишет в `/state` ошибку «unsupported version») / apply known fields + ignore unknown additive fields / hard-fail].
- **FR-020**: Конкурентная запись admin'ом из двух устройств: [NEEDS CLARIFICATION: last-write-wins via `updatedAt` server-timestamp / optimistic-concurrency через precondition].

**Out of scope (для предотвращения скоупа)**

- **OUT-001**: Admin UI editor для flow/slot/contacts — это spec 009 (`admin-mode-flows`). 008 даёт только wire-format и Managed-side apply; admin-side writer может быть mock/CLI/test-fixture для acceptance.
- **OUT-002**: Команды (commands subcollection — create/delete/move tiles через imperative push) — это spec 009. 008 — про **declarative `/config`**, не про commands. (Roadmap line 185 имеет open question — закроем в `/speckit.clarify`.)
- **OUT-003**: Roll-back механизм (откат к предыдущему `/config`) — [NEEDS CLARIFICATION: в scope 008 или backlog?].
- **OUT-004**: Provider capabilities / health subcollections (`/capabilities/current`, `/health/current`) — это extension спека 006, отдельный трек, не зависит от 008. Roadmap-block 008 их не упоминает.

### Key Entities

- **ConfigDocument** (`/links/{linkId}/config/current`): применяемая раскладка для Managed. Поля: `schemaVersion`, `presetId`, `flows[]`, `contacts[]`, `updatedAt`, `updatedBy`.
- **StateDocument** (`/links/{linkId}/state/current`): applied-snapshot — что Managed реально применил. Поля (расширение к bootstrap из 007): прежние bootstrap-поля + `appliedConfigRef` (hash/version), `flowsApplied[]`, `contactsApplied[]`, `partialApplyReasons[]`.
- **LocalConfigStore** (Managed, Room): локальная копия applied-config + applied-version для fast bootstrap при process restart.
- **ConfigApplier** (Managed domain port): применяет ConfigDocument к UI/storage; возвращает applied-snapshot для публикации в `/state`.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: При online-Managed end-to-end latency «admin writes /config» → «Managed applied + /state updated» MUST быть < [NEEDS CLARIFICATION: 5s / 10s? с учётом FCM p95 из spec 007 SC-001].
- **SC-002**: При offline-Managed (≥ 30 минут offline) и последующем online — apply последней версии `/config` MUST произойти в течение [NEEDS CLARIFICATION: T-RECONNECT, типично 10s] после восстановления связи.
- **SC-003**: 100% записей `/config` приводят к обновлению `/state` (или к явному `partialApplyReasons[]`) — нет «тихих» failed apply. Метрика: за тестовый прогон 100 записей → 100 state-updates, 0 потерь.
- **SC-004**: После process death Managed bootstrap-time (от Activity#onCreate до first frame с applied-config) < [NEEDS CLARIFICATION: 500 ms / 1 s?].
- **SC-005**: Wire-format roundtrip и backward-compat read tests — 100% green (per FR-012, FR-013).
- **SC-006**: При schemaVersion mismatch (FR-019) — 0 crashes на Managed; поведение детерминированное (либо reject в /state, либо ignore unknown — после clarify).

---

## Assumptions

- Spec 007 в main — pairing/link/FCM-push channel работают (verified: PR #7 merged, see git log `46eb5de`).
- Cloudflare Worker (push-relay) расширяется для обработки нового payload type `config.updated` — добавление, не breaking change.
- Firebase Security Rules из спека 007 уже покрывают `/links/{linkId}/config/**` и `/state/**` (verified: `link.md` §Security Rules requirements — Rules для config/state нужно дополнить в Phase 0 этого спека).
- Managed использует Room (стандартный AndroidX — не violates rule 1, т.к. Room — infrastructure adapter, domain читает через port).
- Admin-side writer в этом спеке может быть тестовый/CLI/fake (полноценный UI — spec 009).
- Спек НЕ вводит новых внешних SDK (Firebase, FCM уже в 007; Room — стандартная Android-библиотека).

---

## Open Questions (для `/speckit.clarify`)

1. **Roadmap line 184/185 — расхождение после ренумерации**: «Admin-mode UI (это в 008)» — должно читаться «это в 009». Подтвердить и поправить roadmap.
2. **Commands (create/delete/move tiles через push)** — в спеке 008 (declarative + imperative) или **только** в 009? Текущий draft помещает в 009 (OUT-002), нужно подтвердить.
3. **`appliedConfigRef`** — какая форма ссылки на применённый /config? Hash (стабильно при replay) / `updatedAt` (server-timestamp) / monotonic `version` field? Предпочтение: `appliedConfigUpdatedAt` (server-set, monotonic per Firestore semantics).
4. **Schema mismatch (FR-019)**: reject, ignore-unknown, или hard-fail?
5. **Concurrent admin writes (FR-020)**: last-write-wins или optimistic concurrency?
6. **Migration спека 003 → 008 storage (FR-016)**: cleanup или migration? Учитывая, что 003 пока не в production у реальных пользователей — cleanup проще.
7. **Roll-back (OUT-003)**: в scope 008 или backlog?
8. **SC-001 / SC-002 / SC-004 значения**: какие конкретные числа?
9. **T-POLL для no-GMS fallback (FR-005)**: использовать значение из спека 007 или другое?
10. **Лимит размера `/config` (edge-case «500 контактов»)**: какой лимит, и как Managed/admin должны это видеть?

# Feature Specification: Family Group Foundation — ❌ DEPRECATED 2026-05-28

> **STATUS: DEPRECATED — DO NOT IMPLEMENT**
>
> This spec was archived 2026-05-28 after product vision review.
>
> **Reason**: Family Group as a primitive is **not needed in the launcher**. Multi-admin scenarios (3 admins managing 1 grandma) are solved by N independent pair-edits merged on grandma's device through spec 008 bidirectional sync. Shared content use cases (family album, group chat) belong in **separate ecosystem apps** (Family Messenger, Family Album), not the launcher.
>
> **Where the design lives now**:
> - [docs/product/future/ecosystem-vision.md](../../docs/product/future/ecosystem-vision.md) — Family Group primitive will be implemented in future Family Messenger spec; all design from sections below (domain model, envelope encryption, server arbitration, audit log Tier 1/2) preserved for copy-paste into that future spec.
> - [docs/research/2026-05-28-contact-sharing-ux-patterns.md](../../docs/research/2026-05-28-contact-sharing-ux-patterns.md) — UX research that informed the decision.
> - [docs/research/2026-05-28-shared-editor-deep-dive.md](../../docs/research/2026-05-28-shared-editor-deep-dive.md) — Pattern research.
> - [docs/dev/project-backlog.md](../../docs/dev/project-backlog.md) — TODO-FUTURE-PRODUCT-001 (Family Messenger), TODO-FUTURE-PRODUCT-002 (Family Album).
>
> **Replacement work**:
> - **spec 014 (Contact Sharing UX Refinements)** picks up the part of this spec that's actually needed in MVP — share-target intent, dedup dialog, snackbar undo, background photo prefetch.
> - **F-5 (ConfigDocument E2E Encryption)** in `docs/product/roadmap.md` is the new Phase 1 Foundation production-blocker that replaces F-1 in the roadmap.
>
> ---

**Feature Branch**: `013-family-group-foundation`
**Created**: 2026-05-28
**Status**: ARCHIVED 2026-05-28
**Input**: User description: "F-1: Family Group Foundation — primary architectural primitive (N admins + M Managed + caregivers + roles), envelope encryption for shared content, server arbitration for membership operations. Extends spec 011 per-pairing crypto; pair from spec 007 remains as 1-to-1 channel."

---

## Контекст и цель спека

### Зачем существует этот спек

До сих пор продукт построен на **pair-модели** (1 admin ↔ 1 Managed, спек 007). Это работает для MVP, но **архитектурно не выдерживает** реальные сценарии Family Care:

- 3 взрослых детей на одну пожилую маму (3 admin'а на 1 Managed).
- Одна семья с двумя родителями и пятью внуками (M Managed × N admin'ов).
- Медработник, приходящий по средам (caregiver с TTL).
- Сосед с правом запускать SOS, но не видеть фотогалерею (role-based filtering).

Каждая из этих ситуаций сегодня требовала бы **N независимых pairings**, которые не консистентны: фото, расшаренное с одним admin'ом, не видно второму, потому что envelope wrapper'ы построены под конкретную пару.

F-1 строит **Family Group** как primary architectural primitive — записанную на сервере коллекцию `{groupId, members: [{userId, pub, role, ttl}], ...}`. Контент, расшаренный в группе, доступен **всем текущим членам** через envelope encryption (без shared private key — E2E preserved). Membership operations — атомарные, подписанные admin'ом, арбитрированные сервером.

### Что НЕ строит этот спек

- **Никакого UI** — F-3 (Wizard), S-1..S-8 (visible features) поедут сверху.
- **Никакого реального AuthProvider'a** — F-4 закроет миграцию anonymous → named. Здесь используется существующий anonymous auth из 007 + Fake'и для тестов.
- **Никакого caregiver invite flow** — S-7 закроет UX. Здесь только data model + crypto support.
- **Никакого Signal-style double-ratchet** — envelope encryption достаточен для at-rest content (см. use-cases/05 §«Что НЕ нужно делать»).
- **Никакого social recovery / orphan admin handling** — отдельная будущая спека (S-401, 011 OWD-4).
- **Никакой forward secrecy при removal** — inherent limit at-rest crypto, документируется как известный compromise.

### Архитектурная роль

F-1 закрепляет **two-primitive** crypto-модель проекта:

1. **Pair** (спек 007 + 011) — 1-to-1 канал для direct admin↔Managed communication (config sync, direct commands). Сохраняется без изменений semantic'ов.
2. **Group** (этот спек) — N-membered коллекция для shared content (family album, group config history, caregiver-scoped care notes).

Pair используется там, где есть конкретный получатель. Group — где **N получателей**, причём N меняется во времени (add/remove member, TTL expiry). Эти primitive'ы **дополняют**, не заменяют друг друга. Pair-keys и group-membership хранятся в разных таблицах сервера, используют разные crypto primitive'ы из 011.

### One-way doors и exit ramps

| Решение | One-way? | Exit ramp |
|---|---|---|
| Envelope encryption (вместо shared group key) | ✅ Yes — wire-format blob'ов получит `recipients[]` форму на N лет вперёд | Если N вырастет до сотен и envelope overhead станет проблемой — миграция на MLS / Megolm как отдельный `EnvelopeAdapter`. Старые blob'ы читаются по `schemaVersion`. |
| Server arbitration через Cloudflare Worker | ❌ Two-way | Worker → собственный сервер (см. `docs/dev/server-roadmap.md` §«Membership arbitration»). |
| Many-to-many user↔group (vs 1-group-per-Managed) | ✅ Yes — schema bump для membership | Если решим вернуться к 1-group-per-Managed — миграция additive (CARE_GROUP_ID = MANAGED_DEVICE_ID), но N>1 групп уже могут существовать. |
| Anonymous auth остаётся primary (delegated F-4) | ❌ Two-way | F-4 закроет миграцию на named auth без изменений membership-схемы (uid stable). |
| Forward secrecy при removal не строится | ❌ Two-way | Будущая спека добавит key rotation на membership change — additive поверх existing envelope. |

---

## User Scenarios & Testing *(mandatory)*

<!--
  F-1 — это infrastructure spec без user-facing UI. «Пользователи» здесь —
  downstream feature specs (F-3 Wizard, F-4 AuthProvider, S-7 Caregiver Invite,
  S-1..S-8 visible features) и существующие pair users, которые автоматически
  мигрируются на group-модель. User stories выражены в этих терминах.
-->

### User Story 1 — Downstream feature spec строит family album поверх Family Group (Priority: P1)

Автор спеки S-7 (Family Album) хочет, чтобы фото, загруженное любым admin'ом, было видно **всем текущим членам семейной группы**, причём caregiver'у с ролью `MedicalWorker` фото не выдавалось. Спек S-7 не должен изобретать crypto-слой — только дёрнуть domain port `GroupRepository.shareContent(groupId, content, category=FamilyContent)` и port `GroupRepository.readContent(groupId, contentId)`.

**Why this priority**: без F-1 каждая visible-feature спека (S-1..S-8) изобретала бы свой ad-hoc multi-recipient sharing. F-1 — фундамент, поверх которого строится **весь MVP visible scope**.

**Independent Test**: написать integration-тест, где FakeGroupRepository + FakeEnvelopeAdapter обслуживают 3-member group (1 admin + 1 Managed + 1 MedicalWorker). Producer.shareContent(category=FamilyContent) → reader1 (admin) и reader2 (Managed) могут расшифровать; reader3 (MedicalWorker) получает `403`/`NotInRecipientList`. Тот же тест с category=CareContent — все три члена расшифровывают.

**Acceptance Scenarios**:

1. **Given** group G состоит из admin A, Managed M, MedicalWorker C, **When** A вызывает `GroupRepository.shareContent(G, blob, FamilyContent)`, **Then** envelope содержит wrappers только для A и M, и `readContent` от C возвращает domain error «not in recipient list».
2. **Given** group G с 3 членами, **When** одному из них (X) истёк `ttl_expiry`, **Then** X.readContent → `MembershipExpired`, и любой новый shareContent **не включает** X в recipients даже если category совпадает с его прежней role.
3. **Given** admin удаляет caregiver C (signed membership operation), **When** server обрабатывает операцию, **Then** server membership list **атомарно** обновляется и любой контент, загруженный после операции, не имеет wrapper'а для C.

---

### User Story 2 — Существующая pair (admin↔Managed) автоматически мигрируется в Family Group N=2 (Priority: P1)

У пользователя, который уже прошёл pairing по спеку 007 (1 admin + 1 Managed), при обновлении app **автоматически создаётся** Family Group из этих двух участников. Никаких действий от пользователя не требуется. Все существующие pair-keys и pair-keyed данные **остаются работать** через 1-to-1 канал. Group представление появляется поверх — для будущего add-member, для будущего group-scoped контента.

**Why this priority**: без backward-compatible миграции спек 007 ломается и текущие test-users теряют доступ. Это не «новая фича на пустом месте» — это **рефакторинг живой системы**.

**Independent Test**: записать существующий pair как JSON-фикстуру (имитация Firestore state на текущем 007-deploy'е) → запустить migration → читать обратно как Family Group N=2 → assert: members = [{admin, pub_A, role=Admin}, {Managed, pub_M, role=Managed}], groupId детерминирован от linkId, no data loss.

**Acceptance Scenarios**:

1. **Given** существующий pair с linkId L в Firestore (schemaVersion N), **When** migration job выполняется, **Then** в Firestore появляется group {groupId=derive(L), members=[admin, Managed]} и schemaVersion поднимается до N+1.
2. **Given** мигрированная группа N=2, **When** admin или Managed читает существующий pair-keyed config (из спека 008), **Then** чтение работает без изменений — pair-канал не сломан.
3. **Given** roundtrip JSON config до и после миграции, **When** сравниваются user-visible поля (tile arrangement, action mappings), **Then** ничего не потеряно и ничего не переименовано.

---

### User Story 3 — Membership операция конфликтует между двумя admin'ами одновременно (Priority: P2)

Два admin'а (A1, A2) одновременно пытаются изменить membership: A1 хочет добавить co-admin'a A3, A2 хочет удалить Managed M из группы (например, по ошибке). Сервер должен **сериализовать** обе операции атомарно: либо обе применяются в каком-то порядке без потери, либо вторая отвергается с конкретным domain error'ом. Не должно быть ситуации «membership list получился inconsistent».

**Why this priority**: без атомарности membership operations первая же race condition приведёт к расхождению membership на разных устройствах → split-brain crypto state → невозможность расшифровать контент. P2 потому что в MVP вероятность одновременных операций невелика, но **архитектурно обязательно**.

**Independent Test**: integration-тест через Miniflare с двумя concurrent HTTP request'ами в Worker от A1 и A2. Assert: финальное состояние membership соответствует одному из двух валидных порядков; ни одна операция не applied частично; loser получает explicit error code.

**Acceptance Scenarios**:

1. **Given** group G и две concurrent signed membership operations op1 (A1: add A3) и op2 (A2: remove M), **When** обе доходят до Worker'а одновременно, **Then** сервер сериализует их (например, через Firestore transaction), и финальный membership = либо `{A1, A2, M, A3}` (op1 then op2 без M), либо `{A1, A2, A3}` minus M (op2 then op1).
2. **Given** signed operation от user'а, который **не является** admin'ом группы, **When** Worker валидирует подпись и role, **Then** операция отвергается с `NotAuthorized`, и audit log пишет attempted op.
3. **Given** signed operation с устаревшим `membership_version` (op'у создали, пока был version=5, но сервер уже на version=7), **When** Worker сверяет version, **Then** операция отвергается с `StaleVersion`, и клиент должен re-fetch + re-sign.

---

### User Story 4 — Admin приглашает caregiver'а с TTL=24h (data-model only) (Priority: P2)

Admin вызывает domain port `MembershipRepository.addMember(groupId, pub_caregiver, role=MedicalWorker, ttl=24h)`. Запись membership создаётся на сервере с `ttl_expiry = now+24h`. После истечения TTL сервер автоматически (на следующем access check'е) трактует membership как expired: новый shareContent **не включает** wrapper для caregiver'а; readContent **отказывает** даже на старый контент.

> **Scope clarification**: F-1 строит **только data model + crypto enforcement** для TTL. **Сам invite UX / signed invite link / accept flow — out of scope** (закроет S-7). Здесь caregiver добавляется в тестах напрямую через MembershipRepository.

**Why this priority**: без TTL-в-membership невозможно реализовать «1-visit doctor» сценарий, который зафиксирован в use-cases/05 §Caregiver Integration как MVP-обязательный. P2 потому что в самом F-1 нет invite UX — но crypto-уровень должен быть готов.

**Independent Test**: создать FakeGroupRepository с group из 2 (admin + Managed). Через MembershipRepository.addMember добавить caregiver с TTL=1сек. Через 2 сек попытаться shareContent(category=CareContent) → wrapper для caregiver'а **не создаётся**. readContent от caregiver'а на прежний контент → `MembershipExpired`.

**Acceptance Scenarios**:

1. **Given** group G + signed addMember(C, MedicalWorker, ttl=24h) от admin'а, **When** Worker валидирует и применяет, **Then** в membership появляется `{C, pub_C, MedicalWorker, ttl_expiry=now+24h}`.
2. **Given** group G с expired caregiver C, **When** любой member вызывает shareContent(category=CareContent), **Then** envelope wrappers содержат только non-expired members (без C).
3. **Given** group G с expired C, **When** C вызывает readContent, **Then** server отвечает `MembershipExpired`, и domain layer возвращает `ContentAccessDenied(reason=MembershipExpired)`.

---

### Edge Cases

- **Последний admin удаляет себя**: операция отвергается с `LastAdminCannotLeave`. Группа без admin'а = orphan'd state (решается отдельной social recovery спекой). Архитектурно сервер обязан **запретить** такую операцию.
- **Member removed после загрузки шифрованного контента, но до wrapper generation**: race condition. Resolution — wrapper generation использует **membership snapshot на момент shareContent**; если snapshot stale, operation retries с fresh membership.
- **Schema version mismatch при reading old envelope**: новый клиент читает envelope v1 (старая структура `recipients[]`). Должен работать через backward-compat read (rule 5). Тест обязателен.
- **Group с 0 members**: `LastAdminCannotLeave` плюс explicit invariant «group always has ≥1 admin». При попытке создать group без initial admin → domain error `GroupRequiresInitialAdmin`.
- **Signed operation с подписью от unknown pub**: pub-ключ admin'а не в текущем membership → `SignatureFromNonMember`. Не путать с `NotAuthorized` (член группы, но не admin).
- **Concurrent shareContent + removeMember**: если producer успел построить envelope с wrapper'ом для X, а в момент upload'а X уже kicked — wrapper остаётся (forward secrecy inherent limit), но **сервер пишет audit log** «content uploaded with stale recipient».
- **Migration: pair существует, но один из участников уже удалил app**: migration создаёт group с placeholder для «orphan» pub (без живого устройства). При первом healthcheck — если pub не отвечает >30 дней, member помечается `Inactive` (не удаляется автоматически — admin сам решает).
- **groupId collision** при derivation от legacy linkId: derivation function (например, BLAKE2b(linkId)) обязана быть collision-resistant; тест на 10k случайных linkId без коллизий.

## Requirements *(mandatory)*

### Functional Requirements — Domain model

- **FR-001**: System MUST expose `GroupRepository` port в `core/domain/` со следующими domain verbs: `createGroup(initialAdminPub)`, `getGroup(groupId)`, `shareContent(groupId, content, category)`, `readContent(groupId, contentId)`, `listGroups(userId)`. Все возвраты — domain types, никаких Firebase / Cloudflare / libsodium типов в signatures.
- **FR-002**: System MUST expose `MembershipRepository` port в `core/domain/` с domain verbs: `addMember(groupId, pub, role, ttl?)`, `removeMember(groupId, userId)`, `updateRole(groupId, userId, newRole)`, `listMembers(groupId)`, `getMembership(groupId, userId)`. Все операции возвращают `Result<T, MembershipError>` с явными domain error variants.
- **FR-003**: System MUST поддерживать many-to-many user↔group отношение: один `userId` может состоять в N группах одновременно; одна `groupId` может содержать M членов с разными ролями. Эта связь хранится в membership entity, не в user entity и не в group entity.
- **FR-004**: System MUST определить `Membership` entity с полями: `groupId`, `userId`, `pub` (X25519 + Ed25519 keys), `role` (enum: `Admin | CoAdmin | Member | Managed | Caregiver`), `ttl_expiry` (optional timestamp), `permissions_override` (optional map), `joined_at` (timestamp), `membership_version` (monotonic counter).
- **FR-005**: System MUST определить `Group` entity с полями: `groupId` (opaque string), `schemaVersion`, `created_at`, `created_by` (initial admin userId). **Никаких** prv-ключей в group entity (priv_G не существует — fundamental architectural invariant).
- **FR-006**: System MUST поддерживать роли Admin, CoAdmin, Member, Managed, Caregiver с поведениями: `Admin` — может add/remove/promote любого включая других admin'ов; `CoAdmin` — может add/remove не-admin'ов; `Member`/`Managed` — не может менять membership; `Caregiver` — не может менять membership и не видит контент категории FamilyContent.
- **FR-007**: System MUST enforce invariant «group always has ≥1 active (non-expired) Admin». Любая операция, нарушающая инвариант (last admin removes self, last admin TTL expires) — отвергается с `LastAdminCannotLeave`.

### Functional Requirements — Envelope encryption

- **FR-008**: System MUST expose `EnvelopeAdapter` port в `core/crypto/` с domain verbs: `seal(content, recipientPubs[]) → SealedEnvelope`, `open(envelope, ownPriv) → content`, `roundtrip` обязательно symmetric. Реализация под капотом — libsodium `crypto_box_seal` + `crypto_secretbox`. Domain не знает про libsodium.
- **FR-009**: System MUST реализовать envelope encryption: producer генерирует per-content симметричный ключ K, шифрует контент K → `encrypted_content`, wrap'ит K через `crypto_box_seal(K, pub_i)` для каждого `pub_i` из current recipient set. Никаких shared group keys (priv_G запрещён).
- **FR-010**: System MUST поддерживать N=1 случай envelope (degenerate group, e.g. self-shared) и N=many случай через одинаковый wire-format. Envelope с N=0 recipients (orphan content) **отвергается** на write с `NoRecipients`.
- **FR-011**: System MUST вычислять recipients на shareContent **автоматически** по правилам category-routing: `FamilyContent` → wrappers только для roles `{Admin, CoAdmin, Member, Managed}`; `CareContent` → wrappers для всех ролей включая `Caregiver`; `Public` — out of scope (отдельная категория, обработается future spec). Producer указывает **category**, не список recipient'ов.
- **FR-012**: System MUST поставить `FakeEnvelopeAdapter` (в-памяти, без реального libsodium) для unit-тестов. Fake обязан соблюдать тот же contract что real, включая отказ на N=0, отказ open с чужим priv, и так далее.
- **FR-013**: System MUST поставить `RealEnvelopeAdapter` (на libsodium) и dependency-injection wiring, выбирающий Fake / Real по build flavor.

### Functional Requirements — Server arbitration

- **FR-014**: System MUST расширить Cloudflare Worker (`push-worker`) новым endpoint'ом для membership operations: `POST /membership/{op_type}` где op_type ∈ {`add`, `remove`, `update_role`, `create_group`}. Аутентификация через Firebase ID-token (Bearer) как в спеке 007 C11.
- **FR-015**: System MUST требовать **signed payload** в каждом membership operation request'е: payload подписан Ed25519-приватным ключом инициатора. Worker валидирует подпись по `pub` инициатора из текущего membership snapshot.
- **FR-016**: System MUST реализовать атомарные membership updates через Firestore transaction (или эквивалент): чтение membership_version → проверка == ожидаемая → запись новой версии. Stale version → return `StaleVersion`, клиент должен retry с fresh fetch.
- **FR-017**: System MUST вернуть explicit domain error codes из Worker (как HTTP status + JSON body): `NotAuthorized` (signature OK, но role insufficient), `SignatureFromNonMember` (pub not in membership), `StaleVersion`, `LastAdminCannotLeave`, `GroupNotFound`, `MembershipExpired`. Каждый код mapping → domain `MembershipError` variant.
- **FR-018**: System MUST логировать каждую membership operation в server audit log (initiator userId, op_type, target userId, timestamp, result code). Retention/UX выходят за scope — здесь только что лог пишется.

### Functional Requirements — Migration

- **FR-019**: System MUST реализовать migration job, который для каждого существующего pair (Firestore document `/links/{linkId}`) создаёт group `{groupId=derive(linkId), members=[{admin pub, role=Admin}, {Managed pub, role=Managed}]}`. Derivation function — детерминированная (стабильный re-run, no duplicates на повторных запусках).
- **FR-020**: System MUST сохранять backward compatibility: после миграции, **все pair-keyed операции из спека 007/008/011 продолжают работать** через тот же pair-канал. Group представление — additive поверх, не замена pair.
- **FR-021**: System MUST поднять `schemaVersion` для config + crypto envelope wire-format'ов. Старый клиент (без поддержки group) должен читать новый config через backward-compat path; новый клиент должен читать старый envelope через legacy read path. Обе стороны тестируются roundtrip + cross-version test'ами.
- **FR-022**: System MUST не выполнять автоматическую миграцию при первом запуске нового клиента **в отсутствие server-side trigger'а**. Migration — server-side job, запускается одноразово (или периодически идемпотентно) на side Cloudflare Worker / отдельный admin-only endpoint. Это minimum risk approach.

### Functional Requirements — Wire format

- **FR-023**: System MUST определить wire-format для Group entity с явным `schemaVersion` полем (rule 5). Поля: см. FR-005 + явная versioning convention.
- **FR-024**: System MUST определить wire-format для Membership entity с явным `schemaVersion`. Поля: см. FR-004. **Никаких** PII (phone, email, full name) в membership — только opaque `userId` и публичные ключи.
- **FR-025**: System MUST определить wire-format для `SealedEnvelope` (расширение envelope из спека 011 на N recipients): `{schemaVersion, recipients: [{userId, wrapped_key}], encrypted_content, content_metadata}`. Backward-compat с спеком 011 envelope (N=1) — обязательна.
- **FR-026**: System MUST иметь roundtrip test для каждого wire-format'а (write → read → assert equal) и backward-compat test (read previous schemaVersion, новые поля = default).

### Functional Requirements — Test infrastructure

- **FR-027**: System MUST поставить `FakeGroupRepository` и `FakeMembershipRepository` в `core/domain/test/` для использования downstream спеками без реального Firestore. Fake обязан соблюдать тот же contract что real-adapter, включая все error codes из FR-017.
- **FR-028**: System MUST поставить integration test через Miniflare (локальная эмуляция Cloudflare Worker), покрывающий: create_group, add_member, remove_member, concurrent membership ops (race resolution), signed verification, stale version rejection.
- **FR-029**: System MUST поставить cross-device test scenario: устройство D1 публикует pub_1, устройство D2 публикует pub_2, group создаётся с обоими, D1 shareContent, D2 readContent → success.

### Key Entities

- **Group**: коллекция членов с общим shared content. `{groupId, schemaVersion, created_at, created_by}`. **Не содержит** приватных ключей. Identified by opaque server-generated ID.
- **Membership**: связь user↔group с ролью и optional TTL. `{groupId, userId, pub, role, ttl_expiry?, permissions_override?, joined_at, membership_version}`. Многие-ко-многим: один user может состоять в N группах.
- **Role**: enum определяющий поведение участника. `Admin | CoAdmin | Member | Managed | Caregiver`. Role + content category вместе определяют, попадает ли участник в recipients set при shareContent.
- **SealedEnvelope**: at-rest формат зашифрованного контента, расширение envelope из 011 на N recipients. `{schemaVersion, recipients: [{userId, wrapped_key}], encrypted_content, content_metadata}`.
- **ContentCategory**: enum, определяющий маршрутизацию envelope wrappers. `FamilyContent | CareContent`. (`Public` — out of scope для F-1.)
- **MembershipOperation**: signed payload представляющий membership change. `{op_type, target_userId, expected_membership_version, payload, signature, initiator_userId, timestamp}`. Атомарно валидируется и применяется сервером.
- **MembershipError**: domain enum возвращаемый репозиториями. `NotAuthorized | SignatureFromNonMember | StaleVersion | LastAdminCannotLeave | GroupNotFound | MembershipExpired | NoRecipients | GroupRequiresInitialAdmin | ...`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Downstream feature spec (например, S-7 Family Album) может реализовать N-recipient sharing **без единой строчки кода в crypto-слое**, только через port'ы `GroupRepository.shareContent` / `readContent`. Подтверждается smoke-фичей в виде минимального S-7 stub'а в integration-тестах F-1.
- **SC-002**: 100% существующих pair-конфигов мигрируются в group N=2 **без потери user-visible данных** (tile arrangement, action mappings, contact list). Подтверждается JSON roundtrip diff'ом до/после миграции на test corpus из ≥10 pair-fixtures.
- **SC-003**: Membership operation race condition (2+ concurrent operations) **никогда не приводит** к split-brain (membership расходится между устройствами). Подтверждается integration-тестом на Miniflare с 100 concurrent op-pairs — все 100 пар разрешаются либо в один из валидных порядков, либо явный StaleVersion error.
- **SC-004**: Envelope encryption на 1 фото 5MB для group N=4 добавляет **≤ 256 байт** overhead над encrypted_content (4 × 32 байта wrapper + metadata). Подтверждается benchmark'ом на real-adapter.
- **SC-005**: Caregiver с role `MedicalWorker` **физически не может** расшифровать контент category=FamilyContent даже при scrape'е encrypted_content и попытке brute-force. Подтверждается crypto-test'ом: scrape envelope → попытка open с caregiver priv → `DecryptionFailure`.
- **SC-006**: TTL expiry на membership приводит к отказу в новых wrappers **в течение ≤ 5 секунд** после момента expiry (server cache invalidation tolerance). Подтверждается integration-тестом с manipulated timestamp.
- **SC-007**: 0 references на vendor SDK types (Firebase, libsodium, Cloudflare) в `core/domain/` пакетах. Подтверждается fitness function (lint rule на imports) — non-zero references = build failure.
- **SC-008**: Wire-format backward-compat: новый клиент успешно читает 100% envelope'ов schemaVersion=N-1 (legacy). Подтверждается cross-version roundtrip test'ом.

## Assumptions

- Anonymous Firebase auth (как в спеке 007) остаётся primary auth механизмом для F-1. F-4 закроет миграцию на named auth — здесь Membership.userId == Firebase anonymous UID, и stability обеспечивается тем же механизмом что в 007 (persistent на устройстве, переживает app restart).
- Cloudflare Worker уже задеплоен и работает (artifact спеки 007). F-1 расширяет существующий Worker новыми endpoint'ами; новый Worker не создаётся.
- Firestore Security Rules позволяют atomic transactions для membership documents. (Подтверждено в 007 — используется для pair-creation; та же модель для group-creation.)
- libsodium доступна на всех целевых платформах (Android — через `libsodium-jni` или `lazysodium-android`, как уже используется в спеке 011). iOS сейчас out of scope MVP (см. roadmap), но adapter pattern гарантирует, что добавление iOS — additive (новый `IosRealEnvelopeAdapter`).
- Migration выполняется **однократно** на side server'а (через admin-only endpoint в Worker'е или manual deploy script). Не на side клиента. Это снижает риск partial migration state.
- Audit log на Worker'е — append-only в Firestore collection `/audit/membership_ops/`. Retention/PII-scrub policy — отдельная backlog задача (см. exit ramp в `docs/dev/server-roadmap.md`).
- TTL enforcement — pull-based (проверка на каждом access), не push-based (нет periodic job, очищающего expired memberships). Это упрощает MVP; если будет проблема (audit log замусоривается expired entries) — добавится cleanup job в backlog.
- Существующий fake-adapter framework из спеки 011 переиспользуется. F-1 добавляет `FakeGroupRepository`, `FakeMembershipRepository`, `FakeEnvelopeAdapter` в той же конвенции.

## Local Test Path *(mandatory)*

- **Emulator / device**: logic-only — JVM unit tests на JUnit5 + integration tests через Miniflare (локальная Cloudflare Worker эмуляция). Никаких Android-эмуляторов не нужно (F-1 не имеет UI).
- **Fake adapters used**: `FakeGroupRepository`, `FakeMembershipRepository`, `FakeEnvelopeAdapter`, `FakeAuthProvider` (минимальный, выдаёт стабильные anonymous UID'ы), `FakeRemoteSyncBackend` (из спеки 007 — переиспользуется для cross-device тестов без Firestore).
- **Fixtures / seed data**:
  - `core/src/test/resources/fixtures/legacy-pair-v1.json` — pre-migration pair JSON (несколько примеров: 1 admin, 2 admin'а, admin + Managed + дополнительные пары).
  - `core/src/test/resources/fixtures/group-v1.json` — post-migration ожидаемая форма.
  - `core/src/test/resources/fixtures/envelope-n=1.bin`, `envelope-n=4.bin` — sealed envelopes для backward-compat read тестов.
  - `push-worker/test/fixtures/concurrent-ops.json` — pairs of operations для race condition тестов.
- **Verification commands**:
  - `./gradlew :core:test --tests *FamilyGroupTest` — domain unit tests (FR-001 .. FR-013, FR-027).
  - `./gradlew :core:test --tests *EnvelopeRoundtripTest` — envelope roundtrip + cross-version (FR-025, FR-026).
  - `./gradlew :core:test --tests *MigrationTest` — pair → group migration (FR-019 .. FR-022, SC-002).
  - `cd push-worker && npm test` — Miniflare integration: server arbitration (FR-014 .. FR-018), race conditions (SC-003).
  - `./gradlew :core:test --tests *CrossDeviceTest` — D1 share / D2 read через Fake'и (FR-029).
- **Cannot-test-locally gaps**:
  - **Real Firestore transaction под нагрузкой** (100+ concurrent ops in production cluster) — Miniflare даёт approximation, но не emulates Firestore exactly. → `TODO(production-test)` в `push-worker/test/README.md` для pre-release phase.
  - **Cross-OEM Doze behavior** при доставке push'а о membership-change — `none`, push channel не меняется в F-1 (reuse из 007).
  - **Physical-device cross-device test** (две реальные физические трубки, не два эмулятора) — `TODO(physical-device)` для pre-release phase; в текущем MVP эмулятор-эмулятор сценарий через Fake достаточен для логической верификации.

## AI Affordance *(mandatory)*

- **Exposable capabilities** (для будущих AI-agents через Capability Registry, F-2):
  - `listFamilyGroups()` — read-only список групп, в которых состоит текущий user.
  - `listGroupMembers(groupId)` — read-only список членов с ролями (без pub-keys, без PII).
  - `suggestInviteMember(groupId, contactRef)` — domain verb, который F-3 wizard'у может предложить AI; реализация — отдельная спека.
  - `summarizeMembershipChanges(groupId, sinceTimestamp)` — для AI-powered «recent activity» summary; read-only audit log access.
- **Required affordances on data**: read-only access к `MembershipRepository.listMembers` для suggestion flows. **Никакой PII (phone, email, full name) не покидает device** — AI получает только opaque userId + role. Все mutating operations (addMember, removeMember) **требуют explicit user approval** через UI и **не могут** быть инициированы AI агентом autonomously без user confirmation.
- **Provider-agnostic shape**: все exposable capabilities — domain verbs, выраженные через `GroupRepository` / `MembershipRepository` port'ы. **Нет** Gemini / OpenAI / Claude / Anthropic / MCP типов в signatures. Подтверждается rule 1 (domain isolation) + fitness function на imports.
- **Out of scope for this spec**: реальная AI provider implementation, MCP server expose, voice triggers, LLM prompt design, telemetry sampling. Всё это ship'ится в F-2 Capability Registry Foundation и далее.

## OEM Matrix

Not applicable — F-1 — это pure-Kotlin core/domain + Cloudflare Worker server-side спек без любого Android-specific runtime поведения. UI и device-behavior touchpoints закроют downstream specs (F-3, S-1..S-8), для них OEM-матрица будет обязательна.

---

## Зависимости и cross-spec impact

### Extends

- **Спек 011** (E2E Crypto Foundation) — F-1 расширяет envelope wire-format с N=1 на N=many (FR-025), переиспользует `AeadCipher`, `AsymmetricCrypto`, `DigitalSignature` port'ы. Pair-keyed envelope (N=1) **остаётся работать** через тот же wire-format.

### Updates

- **Спек 007** (Pairing) — pair-канал остаётся как 1-to-1 primitive. Никакого breaking change в pairing-token wire-format, FCM payload, Cloudflare Worker `/notify` endpoint. F-1 добавляет **новые** `/membership/*` endpoint'ы в Worker, существующие — без изменений.
- **Спек 008** (Bidirectional Config Sync) — config-sync продолжает использовать pair-канал для direct admin↔Managed. F-1 добавляет возможность group-scoped shared config history (но не enforce'ит — это уже S-feature).
- **Спек 009** (Admin Mode Flows) — admin commands продолжают идти через pair. F-1 добавляет возможность group-scoped commands (для будущих multi-admin сценариев — но enforcement через role в S-feature).
- **`docs/dev/server-roadmap.md`** — добавляется запись «Membership arbitration на собственный сервер» (exit ramp для Cloudflare Worker dependency).
- **`docs/dev/project-backlog.md`** — добавляются follow-up задачи: audit log retention policy (SEC), TTL cleanup job (PERF), social recovery spec (UX), forward secrecy on removal (CRYPTO).

### Reference docs

- [docs/product/use-cases/01-vision-and-positioning.md](../../docs/product/use-cases/01-vision-and-positioning.md) §Family Group System.
- [docs/product/use-cases/05-pairing-identity-trust.md](../../docs/product/use-cases/05-pairing-identity-trust.md) §Family Group Model + §Caregiver Integration.
- [CLAUDE.md](../../CLAUDE.md) rules 1, 2, 5, 6, 8.
- [specs/011-contacts-and-e2e-encrypted-media/spec.md](../011-contacts-and-e2e-encrypted-media/spec.md) — extends.
- [specs/007-pairing-and-firebase-channel/spec.md](../007-pairing-and-firebase-channel/spec.md) — updates.

---

## TL;DR на русском (для не-Android разработчика)

**Что внутри**: фундаментальный рефакторинг модели доверия в продукте. Раньше работала только пара «один admin ↔ один Managed» (1-to-1). Эта спека вводит **Family Group** — N admin'ов + M Managed + M caregiver'ов с ролями и TTL. Группа — основной примитив, поверх которого будут строиться все visible-фичи MVP.

**Ключевые решения**:
1. **priv_G НЕ существует** — никакого общего приватного ключа группы. E2E сохраняется: контент шифруется envelope'ом (per-content key K, обёрнутый для каждого члена через его pub).
2. **Сервер — арбитр membership, не keeper of keys**. Cloudflare Worker валидирует подписи admin'ов, атомарно обновляет членство в группе. Контент сервер видит только зашифрованным.
3. **Pair остаётся** как 1-to-1 канал для direct admin↔Managed (config sync). Group — для shared content (фото семьи, care notes).
4. **Migration** существующих pair → group N=2 происходит автоматически на side server'а, без потери данных.
5. **TTL на membership** позволяет «1-visit doctor» сценарий без overhead создания/удаления групп.

**Что НЕ строится** в этой спеке: никакого UI, никакого нового auth provider'а, никакого invite UX для caregiver'ов, никакого Signal-style group crypto, никакого social recovery. Всё это закроют отдельные спеки.

**Inherent limit**: удалённый из группы member сохраняет доступ к контенту, который уже скачал. Это at-rest crypto limit — не лечится никаким протоколом. Документируется в Privacy Policy.

**Risk**: главный риск — race conditions на membership operations и split-brain между устройствами. Решается атомарными transaction'ами в Firestore + monotonic membership_version + Miniflare integration-тестами с 100+ concurrent op-pairs.

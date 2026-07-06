---
id: TASK-102
title: 'Decision: Device management MLS group — ownership + revoke via profile reconciliation'
status: Draft
assignee: []
created_date: '2026-07-02'
updated_date: '2026-07-06'
labels:
  - decision
  - crypto
  - security
  - phase-2
milestone: m-1
dependencies:
  - TASK-101
priority: high
ordinal: 102000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

В нашем MVP главная MLS-группа — **устройство primary user'а (бабушки) как якорь** + admin-устройства подключённые через QR pairing. Не family-мессенджер (тот в TASK-42, Phase-3+).

**Кто владеет группой**: устройство бабушки. Оно **единственный** MLS Commit signer — только оно фактически issues Add/Remove operations. Admin-устройства — обычные members, могут отправлять/получать зашифрованные payload'ы (bucket sync), но не могут напрямую менять roster.

**Как admin отзывает другого admin'а** (или себя) — не через прямую MLS operation, а через **редактирование профиля бабушки** на сервере:

1. Admin берёт **edit lock** на профиле.
2. Скачивает + расшифровывает профиль (encrypted MLS payload).
3. Убирает целевого admin'а (или себя) из списка "кто управляет телефоном".
4. Загружает обратно + освобождает lock.
5. **Бабушкин планшет at next sync** скачивает обновлённый профиль → detects roster diff → issues MLS Remove Commit → новый epoch.

**Локальная альтернатива**: на планшете бабушки есть экран "Кто управляет" — там любой admin можно отключить напрямую (тот же reconciliation path, просто через local UI, без server round-trip).

**Что происходит с removed admin'ом**: forward secrecy MLS применяется автоматически — новые payload'ы группы он расшифровать не может. Может re-join через новый QR pairing (no blacklist).

**Gap accepted**: если планшет бабушки долго offline — MLS Remove не применяется до следующей синхронизации. Removed admin сохраняет group access в этом окне. Eventually consistent, приемлемо для family threat model. Compromised admin — эскалация через TASK-103 remote lock.

## Зачем

Разрешить blocking security decision для:
- TASK-6 (root key hierarchy — includes device revoke as operation for recovery flow).
- TASK-19 (config sync — bucket sync payload шифруется для current MLS group members).
- TASK-46 (shared admin book — admin management UI).
- TASK-24 (device inventory sync — device list per identity).
- TASK-25 (multi-app cohabitation — device management scope).

Без этой модели не понятно как MLS group state coordinates с profile edits — риск split-brain (два admin'а одновременно меняют roster).

## Что входит технически (для AI-агента)

**Ownership model**:
- **Bab's device = MLS group owner** (единственный member, который issues Add/Remove Commits).
- **Admin devices** = обычные members. Получают Welcome при add, обрабатывают Commit'ы, шифруют/дешифруют AppMessages (bucket sync payloads).

**Reconciliation flow**:
- Profile содержит `authorized_devices: [{identity_id, device_id, role, added_at, ...}]` list.
- Bab's device at sync compares `authorized_devices` list vs current MLS group roster (leaves).
- Diff → issues **MLS Add** (для новых authorized) / **MLS Remove** (для отсутствующих).
- Commit fanout через DS обычным MLS путём.

**Edit lock coordination**:
- Server-side lock на profile: `editing_by: identity_id, expires_at: timestamp`.
- Lock TTL = 5 минут (admin должен сохранить или lock истечёт).
- Acquire fails если lock уже held другим admin — UI показывает "сейчас редактирует X, попробуйте позже".
- Optional force-release (только owner primary device) — TBD post-MVP.

**Application-level layers**:
- **Client-side (bab's device local UI)**: direct "disconnect" в экране "Кто управляет" — same reconciliation path через local profile edit (без server round-trip, но всё равно local edit → local reconciliation → MLS Remove).
- **Client-side (admin's remote UI)**: profile edit через сервер с lock acquisition.
- **Server-side (Cloudflare Worker)**: lock management endpoint + profile storage.
- **Reconciliation (bab's device)**: background job at sync, compares rosters, issues MLS ops.

## Состояние

**Revised 2026-07-06** per rule 11 mutability window — pre-implementation task. Original Decision (peer-admin kick, immediate hard MLS Remove) заменён на текущую модель (bab's device sole executor + profile reconciliation) после mentor-обсуждения device management scope. Downstream tasks (TASK-6, 19, 46, 24, 25) добавляют `dependencies: [TASK-102]` при следующем touch.

Не implemented — Decision block mutable per rule 11 revised (2026-07-06).

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] Decision revised per rule 11 mutability window — bab's device sole MLS executor + profile reconciliation
- [x] #2 [hand] Scope narrowed to device management group (future messenger — separate decision if needed при TASK-42)
- [x] #3 [hand] Decision block filled with new model (Choice / Rationale / Applies to / Trade-offs / Exit ramp)
- [ ] #4 [hand] Edit lock spec detailed (TTL constants, force-release policy, concurrent-edit UX) — tactical, resolves при /speckit.clarify downstream task
- [ ] #5 [hand] Downstream tasks (TASK-6, 19, 46, 24, 25) уведомлены о `dependencies: [TASK-102]` при их next touch
<!-- AC:END -->

## Discussion
<!-- SECTION:DISCUSSION:BEGIN -->

### Revision note (2026-07-06)

Original Decision (peer-admin kick via direct MLS Remove) была revised после mentor-обсуждения device management model. Основной инсайт владельца: **устройство бабушки — sole executor** для её management group, admins **не имеют direct MLS-remove authority**, только через profile edit + reconciliation.

Per rule 11 mutability window — task pre-implementation, Decision block mutable, обновлён напрямую (не создаётся decision-supersedes task).

Predecessor content deleted per «current-thinking files» philosophy.

### Decision (English)

**Choice**:

**Ownership model**:
- **Bab's device is the sole MLS Commit signer** for its device management group. Admin devices are regular members — they can encrypt/decrypt group payloads (bucket sync) but cannot directly modify group roster via MLS operations.
- Group is created **at first QR pairing** (see TASK-67). Subsequent QR pairings **join existing group**.

**Revoke via profile reconciliation**:
- Admins propose roster changes by **editing bab's profile** on server (which contains `authorized_devices: [{identity_id, device_id, role, added_at, ...}]`).
- Bab's device compares `authorized_devices` list vs actual MLS group roster at sync time; diff triggers MLS Add/Remove Commit issued **by bab's device only**.
- Same reconciliation path used for local UI on bab's device ("Кто управляет" screen edits the local profile copy, triggers local reconciliation).

**Edit lock**:
- Server-side lock on profile: `editing_by: identity_id, expires_at: timestamp`.
- TTL = 5 minutes (family default). Preset-parameterizable per TASK-16 preset schema evolution.
- Acquire fails if lock held by another identity → UI displays "editing by X, try later".
- Optional force-release by bab's device (owner override) — TBD post-MVP.

**Roster roles** (declared in profile, not enforced by MLS itself):
- `owner` (bab's device) — sole MLS executor.
- `admin` (paired admin devices) — can propose profile edits.
- Additional roles reserved for Phase-3+ (clinic head-nurse/junior-nurse etc.) — wire format extensible via schemaVersion.

**Removed admin behaviour**:
- Forward secrecy applies automatically at MLS epoch change — removed admin cannot decrypt new group payloads.
- No blacklist — removed admin may re-join via fresh QR pairing (rate-limited per TASK-104 dependencies).

**Bab's device offline gap accepted**: profile edits do not take MLS effect until bab's device syncs and reconciles. Removed admin retains group access during offline window. Eventually consistent, acceptable for family threat model. Escalation for compromised admin = TASK-103 remote app lock (separate mechanism, not MLS-based).

**Applies to**:
- TASK-6 (root key hierarchy — device revoke is a reconciliation-triggered MLS Remove).
- TASK-19 (config sync — bucket sync payload encrypted for current MLS group members).
- TASK-46 (shared admin book — admin management UI is profile editor with lock).
- TASK-24 (device inventory sync — device list per identity in profile).
- TASK-25 (multi-app cohabitation — one app scope, device management per app).
- TASK-67 (QR pairing — creates or joins the group depending on existing state).
- TASK-103 (remote app lock — orthogonal mechanism for compromised device).

**Not applies to** (future scope):
- TASK-42 (future family messenger group). Peer-to-peer revoke may be appropriate there (WhatsApp/Signal model). Separate decision if/when TASK-42 activated in Phase-3+.

**Rationale**:
- **Single source of truth**: bab's device is the anchor for its device management. Sole executor model prevents split-brain where two admins issue conflicting MLS Commits.
- **Rogue admin can't silently kick**: profile change is visible to all synced clients (encrypted for current members). Family social dynamics self-correct.
- **Structural coordination**: edit lock + reconciliation eliminates need for admin-level MLS execute permission, simplifying peer verification logic.
- **Fits device management natural authority**: it's bab's device — she (or her local UI) is ultimate authority; admins are remote configurators.

**Trade-offs**:
- **Eventual consistency**: bab offline → profile changes queue until sync. Acceptable for family threat model; escalation for compromised admin via TASK-103.
- **Edit lock UX**: rare concurrent-edit conflict for two admins editing simultaneously. Family default TTL 5 min balances safety vs stuck-lock scenarios.
- **Reconciliation is bab's device workload**: extra background compute at sync. Trivial for family-scale rosters (< 20 devices).
- **Bab's device compromise = full group control**: acceptable — this is her device, defended by TASK-103 remote lock + TASK-101 recovery flow.
- **Peer messenger scope out**: if we build family messenger later (TASK-42), we need a separate decision — this one only covers device management. Not a loss for MVP.

**Exit ramp**:
- If family messenger scope (TASK-42) requires peer-admin kick: new decision task with narrower scope. This task's decision stands for device management.
- If reconciliation-based revoke proves too slow (bab offline scenarios) in beta data: introduce server-side "eviction quorum" mechanism where N admins can trigger immediate Remove via signed proof. Additive change, no wire format break.
- If edit lock proves insufficient for concurrent-edit correctness: promote to Cloudflare Durable Object for stronger consistency, minor code change (~50 LOC TS).

<!-- SECTION:DISCUSSION:END -->

## Implementation Plan
<!-- SECTION:PLAN:BEGIN -->
_(pending — feature-tasks используют Decision block выше)_
<!-- SECTION:PLAN:END -->

---
id: TASK-32
title: Audit Log Infrastructure
status: Draft
assignee: []
created_date: '2026-06-23 05:40'
updated_date: '2026-06-23 06:33'
labels:
  - phase-4
  - v-spec
  - v-7
  - audit
  - transparency
milestone: m-3
dependencies:
  - TASK-31
priority: medium
ordinal: 32000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Журнал всех действий, которые `remote administrator` или `caregiver` сделали в семейном/групповом пространстве. Снижает тревогу `primary user`'а («кто-то меняет настройки, я не знаю кто») и обеспечивает прозрачность в B2B-сценариях (clinic, корпоративный).

**Что происходит по шагам:**
1. `Admin` изменил config бабушки.
2. Запись в audit log: «admin@example.com изменил layout, 2026-06-23 14:32».
3. Любой member space видит это событие в timeline-секции своего приложения.
4. `Primary user` (бабушка) видит: «дочка обновила настройки 5 минут назад».
5. Что именно изменилось — admin видит (Tier 2, encrypted), бабушка видит обобщённо (Tier 1, public metadata).

**Два уровня логирования:**
- **Tier 1 (public metadata):** кто, когда, тип действия. Виден всем members space.
- **Tier 2 (private payload):** что именно изменено (диff). Зашифрован для actor только (или admin'ского круга).

**Возможные use-cases:**
- Family: бабушка видит «дочка изменила настройки», admin видит «дочка убрала контакт Игоря».
- Clinic: пациент видит «доктор Иванов посмотрел snapshot», админ-доктор видит «доктор Иванов открыл pulse-historic».
- Корпоративный: пользователь видит «IT-support подключился», админ видит «IT-support открыл logs».

## Зачем

- Снижает тревогу: «кто-то что-то меняет, я не знаю».
- Compliance для B2B (clinic / GDPR): нужна возможность доказать «кто что когда».
- Поддержка V-6 (TASK-31 caregiver) — без audit log caregiver безотчётен.

## Что входит технически (для AI-агента)

- Tier 1 wire format: AuditEntry с public metadata (actor, timestamp, action_type, target_id).
- Tier 2: encrypted payload (per-actor key или admin-circle key) с детальным diff.
- Admin UI: фильтрация по actor / time / type.
- Retention: 90 дней по умолчанию (configurable per region).

**Public MLS trail visibility** (added 2026-07-07 per audit item #4 / Тема 4 adjacent concern):
- **MLS commits themselves** уже создают **public trail** — server видит sequence of MLS Add/Remove commits через `authorized_devices` reconciliation (per TASK-102). Это ортогонально audit log:
  - **MLS commit trail** = server-visible sequence of membership changes (кто добавлен / кем удалён, timestamps). Server видит `identity_id` (T0 per TASK-108) или `pseudonym` (T1 future).
  - **Audit log (this task)** = application-level structured entries (`action_type`, `target_id`) с encrypted details.
- **Не дублировать**: audit log должен reference'ить MLS commit (`mlsCommitRef: opaque_id`) для revoke actions, не копировать содержимое.
- **Consistency**: audit log entry для revoke создаётся **тем же device** что issued MLS Commit (bab's device per TASK-102), в **той же transaction**.
- **Trade-off**: server видит MLS commit metadata (T0). Migration to T1 (opaque pseudonym) снимает identity leak в MLS trail — captured в TASK-108 exit ramp.
- **Display names** для audit log UI («Мама Таня изменила config») — через encrypted directory (TASK-114), не plaintext.

## Состояние

**Planned.** Зависит от TASK-31 (V-6 — нужны caregiver actions для логирования).

---

## Готовый промт для `/speckit.specify`

```
Реализуй V-7: Audit Log Infrastructure.

ЧТО СТРОИМ:
Two-tier audit log для прозрачности actions всех members в shared space:
- Tier 1 (public metadata): AuditEntry с actor, timestamp, action_type, target_id — виден всем members.
- Tier 2 (private payload): encrypted diff (per-actor или admin-circle key) — виден только actor + admins.
Admin UI с фильтрами actor / time / type. Retention 90 days (configurable).

ЗАЧЕМ:
Снижает тревогу members («кто-то меняет, не знаю кто»). Compliance для B2B. Аккаунтабилити caregiver'ов из TASK-31 V-6.

SCOPE ВКЛЮЧАЕТ:
- AuditEntry wire format schemaVersion=1 (Tier 1 public metadata).
- AuditPayload encrypted blob (Tier 2 detailed diff).
- AuditLogger service: логирование каждого admin/caregiver action.
- Admin UI: timeline view с фильтрами, search.
- Retention scheduled cleanup (Cloudflare Worker job, 90 day default).
- Primary user UI: упрощённый view (только Tier 1 plus actor display name).

SCOPE НЕ ВКЛЮЧАЕТ:
- Real-time alerts на сomatch определённых actions (post-MVP).
- Cross-app audit (launcher + messenger + album) — TASK-25 P-10 extension.
- AI-anomaly detection (TASK-36 L-3 если AI providers готовы).

DEPENDENCIES:
- TASK-31 (V-6 Caregiver — главный consumer для accountability).
- TASK-8 (S-2 admin app — UI host).

ACCEPTANCE CRITERIA:
- Admin изменил config → primary user видит «admin@example.com обновил настройки 30 сек назад».
- Admin открыл audit log → увидел timeline всех actions с фильтрами.
- Tier 2 detailed diff виден только admin'у (caregiver не видит изменения config'а если он не actor).
- Retention: запись 91-дневной давности автоматически удалена.
- B2B-сценарий: clinic admin может export audit log за период (для compliance отчёта).
- Manual проверка: payload Tier 2 encrypted (не plaintext в Firestore).

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 для admin app + второй для managed.
- Unit-tests encryption per-actor / admin-circle.
- E2E с реальным Firestore staging + Worker retention job.

CONSTITUTION GATES:
- Rule 1 (domain isolation): AuditEntry — pure domain.
- Rule 2 (ACL): Firestore client не вытекает в domain.
- Rule 5 (wire format): AuditEntry/Payload schemaVersion=1.
- Rule 9 (privacy): Tier 1 = obfuscated metadata (без PII в action_type).

EFFORT: Medium (~2 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Tier 2: encrypted private payload
- [ ] #2 Admin UI: фильтрация по actor / time / type
- [ ] #3 Retention: 90 дней по умолчанию (configurable)
- [ ] #4 Admin изменил config → primary user видит 'admin@example.com обновил настройки 30 сек назад'
- [ ] #5 Admin открыл audit log → увидел timeline всех actions с фильтрами
- [ ] #6 Tier 2 detailed diff виден только admin'у (caregiver не видит изменения config'а если он не actor)
- [ ] #7 Retention: запись 91-дневной давности автоматически удалена
- [ ] #8 B2B-сценарий: clinic admin может export audit log за период (для compliance отчёта)
- [ ] #9 Manual проверка: payload Tier 2 encrypted (не plaintext в Firestore)
<!-- AC:END -->

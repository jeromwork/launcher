---
id: TASK-117
title: 'Decision: Universal attestation mechanism'
status: Discussion
assignee: []
created_date: '2026-07-08 06:17'
updated_date: '2026-07-08'
labels:
  - decision
  - crypto
  - attestation
  - phase-3
milestone: m-2
dependencies:
  - TASK-105
  - TASK-108
  - TASK-116
priority: high
ordinal: 117000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

**Универсальный криптографический механизм** для случаев когда «уже доверенный участник подтверждает утверждение о заявителе».

Ключевое наблюдение (владелец, mentor-сессия 2026-07-08): **множество use cases** имеют одинаковую криптографическую структуру — attestor подписывает attestation о заявителе, verifier проверяет подпись. Разница между use cases — **policy** (кто attestor, какой threshold, что подтверждается), **не mechanism**.

**Use cases механизма**:

1. **Cross-app onboarding** (TASK-115) — уже установленное family app подтверждает установку следующего family app'а.
2. **Social recovery** (будущий task, часть MVP) — семья/друзья подтверждают восстановление доступа заявителя.
3. **Admin approval flows** (будущее) — admin запрашивает подтверждение sensitive action у group participants.
4. **Peer verification** (будущее) — при добавлении нового admin/member существующие подтверждают.
5. **Multi-device attestation** (будущее) — user на одном устройстве подтверждает действие с другого своего устройства.

TASK-117 = **только mechanism**. Policy для конкретных use cases — отдельные decision-tasks (TASK-115 для cross-app, TASK-101 для recovery peer confirmation, будущий task для social recovery threshold policy, будущие tasks для admin approval flows).

**Что происходит по шагам (generic mechanism)**:

1. Requester создаёт `AttestationRequest`: `{ request_id, requester_identity_id, action_type, threshold_policy, ttl_seconds, iconic_challenge_seed }`.
2. Request публикуется на сервере (`POST /v1/attestations/request`).
3. Сервер fanouts push уведомления attestor'ам (список определяется policy для action_type).
4. Attestor'ы видят уведомление с описанием action + iconic pairing challenge (TASK-116).
5. Attestor визуально сверяет свою иконку с той что показывает requester → тапает совпадающую в UI.
6. Attestor подписывает `AttestationResponse`: `{ request_id, attestor_identity_id, signed_attestation, timestamp }`.
7. Response публикуется на сервере (`POST /v1/attestations/respond`).
8. Сервер проверяет threshold (N-of-M policy для action_type).
9. Когда threshold достигнут → requester получает `verified: true` при polling `GET /v1/attestations/status/{request_id}`.
10. Requester продолжает свой flow (recovery / установка app / approval action).

## Зачем

**Один механизм — множество use cases**. Правило 4 CLAUDE.md test 1: если убрать эту abstraction — три-пять features реализуют attestation каждый по-своему, разные wire formats, разные security invariants, разные UX patterns. Централизованный mechanism = один audit target, one bug surface, one UX consistency.

**Machinery vs policy разделение**. TASK-117 отвечает на «как криптографически attestor подписывает и как verifier проверяет». Policy («кто attestor для этого action», «какой threshold», «сколько ждать») — application-level decisions в других task'ах.

**Threshold N-of-M configurable per preset**:
- Family MVP: 1-of-1 (одна дочка достаточна для recovery, launcher достаточно для cross-app trust).
- Clinic: 2-of-3 (два врача из клинического staff'а).
- Self-managed: 1-of-1 против attestor'а на другом устройстве самого пользователя.

## Что входит технически (для AI-агента)

**Domain layer**:
- `Attestor` role — value type, `{ identity_id, attestor_type: HUMAN | APP, cross_app_attestation_key_pub }`.
- `AttestationRequest` — `{ request_id, requester_identity_id, action_type, threshold_policy, ttl_seconds, iconic_challenge_seed }`.
- `AttestationResponse` — `{ request_id, attestor_identity_id, signed_attestation, timestamp }`.
- `ThresholdPolicy` — `N-of-M` scheme value type.
- Ports: `AttestationRequester`, `AttestationResponder`, `AttestationVerifier`.

**Wire format** (per TASK-16 discipline):
- Attestation payload — `{ schemaVersion, request_id, action_type, target, iconic_challenge_seed, valid_until }`.
- Signed with `cross_app_attestation_key` — separate key from identity_key, wrapped in root_key, published in identity-link.

**Server endpoints**:
- `POST /v1/attestations/request` — requester создаёт request, сервер fanouts push attestor'ам.
- `POST /v1/attestations/respond` — attestor publishes signed attestation.
- `GET /v1/attestations/status/{request_id}` — polling для requester (threshold reached?).
- Все endpoints обязаны zero-trust baseline TASK-105.

**Consumed components**:
- **TASK-116** — iconic pairing challenge для UI визуального сравнения между requester и attestor.
- **TASK-105** — zero-trust baseline для attestation endpoints.
- **TASK-108** — metadata privacy T0 (сервер видит `identity_id ↔ attestor_identity_id` — mapping без имён, acceptable T0).

**Что НЕ в scope TASK-117** (policy — отдельные task'и):
- Кто attestor для конкретного action_type — решается consuming task'ами.
- Точный threshold N-of-M per user segment — preset decisions.
- Attestor discovery — как requester узнаёт список attestor'ов (может быть server registry, может быть in-group broadcast, может быть policy-specific).
- Anti-coercion — application-level.
- Recovery key storage / threshold decryption — отдельный concern (social recovery specific).

## Состояние

**Discussion, 2026-07-08.** Концепция сформулирована в mentor-сессии TASK-115 после наблюдения владельца что множество use cases имеют одинаковую криптографическую структуру.

**Relationship to TASK-101** (Peer confirmation on recovery, Draft):
- TASK-101 = **specific policy** для recovery flow (chrome-model auto-add + post-facto notification, multi-device как first-class).
- TASK-117 = **general attestation mechanism** обслуживающая multiple use cases.
- TASK-101 после закрытия TASK-117 Decision block должен добавить `dependencies: [TASK-117]` и specify policy которую использует.

**Social recovery как отдельный будущий task**:
Полноценная social recovery UX (кто attestor'ы, threshold policy, admin-у-group vs group-у-admin, anti-coercion) = **отдельный decision-task** в будущем. Не создаём сейчас — контекст будет полнее когда подойдём к социал recovery spec. TASK-117 предоставит mechanism, social recovery task определит policy.

**Ещё открыто**:
- Формальный Decision block не написан.
- Attestor discovery pattern (server registry vs in-group broadcast vs policy-specific) — оставляем consumer task'ам? Или фиксируем в TASK-117?
- Attestation key rotation / invalidation (взаимодействие с TASK-103 remote lock).
- UI для attestor'а — точная формулировка вопроса чтобы избежать auto-approve — mechanism-level guidance или policy?
- Metadata leak assessment на server-side (сервер видит `attestor ↔ requester` mapping без имён — T0 acceptable, T2 future).

---

## Пример сценария (use-case)

**Use case A — Cross-app onboarding (TASK-115 consumer)**:

Валентина в launcher'е тапает плитку «Установить мессенджер». Launcher (issuer) выступает как requester. После install мессенджер запрашивает attestation. Launcher — единственный attestor (threshold 1-of-1). Overlay bubble показывает иконку. Бабушка тапает в мессенджере. Мессенджер получает attestation → recovery key → restored.

**Use case B — Social recovery (будущий task consumer)**:

Валентина потеряла планшет. Ставит launcher на новом устройстве. Выбирает «Восстановить через семью». Launcher = requester. Attestor'ы = дочка Таня + сын Петя. Threshold 2-of-3 (Валентина сама + один из детей, или два ребёнка). Обе стороны видят iconic challenge. Дочка звонит: «мам, что видишь?» — «огонёк». Таня тапает 🔥. То же с Петей. Threshold достигнут → recovery key восстановлен.

**Use case C — Admin approval (будущий task consumer)**:

Admin хочет удалить второго admin'а из group (sensitive action). System требует attestation от group participants. Attestor'ы = все остальные admin'ы + primary user. Threshold 1-of-N (хотя бы один participant подтвердил). Iconic challenge visualized. Один participant подтверждает → action executed.

Все три сценария — **одна криптографическая механика**, разная policy.

<!-- SECTION:DESCRIPTION:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 — 2026-07-08 (concept)

Ключевое наблюдение владельца в контексте обсуждения TASK-115 (cross-app trust bootstrap):

> «Social recovery — это когда группа знакомых подтверждает, что это именно пользователь. Cross-app trust — это когда launcher подтверждает, что это именно бабушкин мессенджер. Криптографически это одно и то же — уже доверенный участник подписывает утверждение о заявителе.»

**Iteration 1 — «social recovery + attestor infrastructure» (superseded)**:
Первоначально TASK-117 формулировался как «social recovery + attestor infrastructure». Recovery был в фокусе, cross-app trust — вторичным.

**Iteration 2 — «universal attestation mechanism» (current, 2026-07-08)**:
Владелец уточнил (правильно): TASK-117 = **только mechanism**, отдельно от любой конкретной application (recovery, cross-app, admin approval). Причины:

> «Нужно отделить, что именно recovery, от механизма. На всякий случай — могут быть другие потребители этого подхода.»

Правильная модель:
- **TASK-117** = universal attestation crypto mechanism (agnostic к use case).
- **TASK-115** = specific policy для cross-app onboarding.
- **TASK-101** = specific policy для recovery peer confirmation.
- **Future task** = social recovery threshold policy (когда доходит до social recovery UX).
- **Future task** = admin approval policy (когда доходит до sensitive admin action flows).

Разделение mechanism ↔ policy соответствует правилу 4 CLAUDE.md (Minimum Viable Architecture) — mechanism reused, policy — feature-specific.

**Threat model preliminary** (mechanism-level, application threat models в consuming tasks):
- Google Sign-In compromised alone → attacker не имеет cross_app_attestation_key → не может выпустить valid attestation.
- Attacker с телефоном скомпрометированного attestor'а → закрывается TASK-103 remote lock.
- Coercion → application-level problem, не решается mechanism.
- Metadata leak на сервере (`identity_id ↔ attestor_identity_id` visible) → T0 acceptable per TASK-108, T2 future (sealed sender).

### Decision (English, mutable pre-implementation) 🔒

*Not yet written. Session 2 formalizes when moving toward implementation of TASK-115 или recovery UX.*

<!-- SECTION:DISCUSSION:END -->

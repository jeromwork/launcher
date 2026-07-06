---
id: TASK-106
title: 'Decision: Identity signup gate — LOCAL-first with QR pairing as cloud gate'
status: Draft
assignee: []
created_date: '2026-07-03'
updated_date: '2026-07-06'
labels:
  - decision
  - crypto
  - security
  - identity
  - phase-2
milestone: m-1
dependencies:
  - TASK-101
  - TASK-102
  - TASK-104
  - TASK-105
priority: high
ordinal: 106000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

**Проблема**: как впустить нового пользователя в систему так, чтобы плохой человек не наштамповал 1000 фейков и через них не сломал per-user защиты (TASK-104 KeyPackage rate limit, TASK-105 server baseline).

**Атака** называется Sybil (по книге "Sybil" о раздвоении личности): один атакующий притворяется многими identity, обходит per-identity ограничения.

**Наша модель** (2026-07-06):

1. **LOCAL identity создаётся автоматически при первом запуске приложения** — без сервера, без Google Sign-In, без SMS. Просто криптоключ на устройстве. Бабушка открывает планшет → работает локально с самого начала (плитки, контакты, темы). Соответствует [decision 2026-06-15-deferred-cloud](../../docs/dev/server-roadmap.md) (device works locally without Google).

2. **CLOUD identity появляется лениво** — только когда пользователь **впервые** делает что-то требующее облака. Для family MVP это = **первое QR pairing** ([TASK-67](../../backlog/tasks/task-67...)): Таня-дочка сканирует QR бабушкиного планшета → устанавливается trust edge → обе identity (бабушкина + Танина) впервые попадают на сервер вместе с их KeyPackage'ами.

3. **QR pairing = наш signup gate**. Физическое присутствие (кто-то физически сканирует QR) требуется — это неавтоматизируемый барьер против Sybil.

**Почему не Google Sign-In / SMS / invitation-code**:

- **Google Sign-In**: (a) нарушает device-self-sufficiency (бабушка не должна видеть окно "войдите" на splash screen); (b) 100 aged Google-аккаунтов на darkweb ~$100-300 — слабый Sybil-барьер; (c) leak identity к Google; (d) не работает на non-GMS (Huawei).
- **SMS phone verify**: (a) SMS toll fraud vector (атакующий выжигает наш SMS budget через premium-rate numbers, ~$1500-15000 profit per attack); (b) требует отдельную инфраструктуру (Turnstile + country blocklist + number reputation) не в scope MVP.
- **Invitation-code system**: по сути = QR pairing с другим transport'ом. QR — простой, безопасный, требует физического присутствия, natural для family.

**Sybil scenario после нашей модели**: атакующий создал 100 LOCAL identity на своих устройствах → ничего не может сделать. Без QR pairing он не в trust edge ни с кем, не в MLS group, KeyPackage'ов его никто не claim'ит, per-identity rate limits не consume'ит. LOCAL identity без cloud upgrade = inert.

Если атакующий физически проникнет к бабушке и отсканирует QR — да, он получит admin-права. Это **social/physical attack**, не automated Sybil. Family threat model это принимает; escalation через TASK-103 remote lock.

## Зачем

Разрешает blocking security decision для:
- **TASK-67** (QR pairing) — сама фича pairing = наш signup gate.
- **TASK-104** (KeyPackage rate limit) — без Sybil defense per-identity rate limits ломаются. Наша модель закрывает vector.
- **TASK-105** (server abuse defense) — signup gate = один из mandatory endpoints, теперь понятно что там реализовать.
- **TASK-27** (future messenger), **TASK-42** (group encryption) — определяют кто может быть member.

## Что входит технически (для AI-агента)

**Identity types**:
- **LOCAL identity**: keypair (Ed25519 signature + X25519 encryption) на устройстве. Хранится в SQLCipher. Существует **немедленно** после первого запуска приложения. Никакой server round-trip не требуется.
- **CLOUD identity**: тот же keypair, но **зарегистрирован на сервере** через QR pairing flow. Server знает `identity_id` (hash от public key) и держит связанные KeyPackage'ы, profile access-grants.

**Cloud upgrade flow** (первое взаимодействие с сервером):
1. Устройство генерирует QR (или сканирует чей-то) при explicit user action.
2. QR pairing протокол (см. TASK-67) устанавливает trust edge между двумя devices.
3. Оба устройства впервые публикуют identity_id + initial KeyPackage batch на сервер.
4. Сервер выдаёт JWT (Firebase Auth Anonymous provider как MVP; own OIDC provider на own-server migration).
5. Устанавливается MLS group (per TASK-102 device management model: bab's device = owner).

**Sybil defense mechanism**: **physical requirement** (кто-то физически сканирует QR). Не автоматизируется, не может быть spoof'ено удалённо. Единственный vector — physical access to target's device (social/physical attack, out of scope MVP).

**Non-GMS (Huawei) — out-of-scope MVP**. Google Sign-In не задействован → non-GMS блокер частично снят, но Firebase Auth (для JWT issuance) — GMS-only. Post-MVP: own OIDC provider работает независимо от GMS.

## Состояние

**Decided 2026-07-06** после многосессионной mentor-дискуссии + research по market leaders (Signal / WhatsApp / Wire / Element / Threema / Session, см. `docs/architecture/crypto.md` § MLS библиотека для полной таблицы альтернатив). Не implemented — Decision block mutable per rule 11 mutability window.

Ортогональные task'и:
- **MLS library choice (openmls)** — вынесено в `docs/architecture/crypto.md` § MLS библиотека, там же альтернативы (libsignal / matrix-rust-sdk / Kalium / mls-rs) и rationale. Отдельный decision task не создавался (per rule 11 relaxation for pre-implementation).
- **Device management group ownership** — [TASK-102](task-102%20-%20Decision-Revoke-policy.md) (bab's device sole executor, revoke via profile reconciliation).

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] Identity model decided: LOCAL identity at first launch (no gate), CLOUD upgrade lazy at first QR pairing
- [x] #2 [hand] Sybil defense mechanism identified: physical QR pairing requirement (unforgeable remote)
- [x] #3 [hand] Non-GMS (Huawei) explicitly out-of-scope MVP with post-MVP path via own OIDC provider
- [x] #4 [hand] Alternatives (Google Sign-In / SMS / invitation-code) explicitly rejected with reasons documented
- [x] #5 [hand] Decision block filled (Choice / Rationale / Applies to / Trade-offs / Exit ramp)
- [ ] #6 [hand] Downstream tasks (TASK-67, TASK-104, TASK-105, TASK-27, TASK-42) уведомлены о `dependencies: [TASK-106]` при next touch
<!-- AC:END -->

## Discussion
<!-- SECTION:DISCUSSION:BEGIN -->

### Revision note (2026-07-06)

Первоначальная mentor-дискуссия рассматривала Google Sign-In как bootstrap gate — отброшено после выявления конфликта с decision 2026-06-15-deferred-cloud (device-self-sufficiency) + Huawei non-GMS блокера + Google privacy leak + elderly Google account assumption слабой.

Research по market leaders (openmls / mls-rs / libsignal / matrix-rust-sdk / Kalium / CoreCrypto — см. `docs/architecture/crypto.md`) выявил что "готовое complete-solution" для нашего constraint (permissive license + light backend) не существует. Chosen path: **openmls протокольная библиотека** + собственный тонкий Delivery Service.

Owner insight про device management model (bab's device = anchor, admins pair through QR) сделало identity signup gate вопрос значительно проще: **QR pairing и есть signup gate** (не отдельный mechanism). Invitation-code из research recommendation subsumed by QR — та же семантика (physical presence-based trust), просто через QR transport.

Per rule 11 mutability window — task pre-implementation, Decision block mutable, обновлён напрямую (не создаётся decision-supersedes task).

Predecessor content (Part A / Part A v2 / Part A v2.1 addendum) deleted per «current-thinking files» philosophy.

### Decision (English)

**Choice**:

**Identity model — hybrid LOCAL + CLOUD**:
- **LOCAL identity** = keypair (Ed25519 signature + X25519 encryption), generated on device at first app launch. Stored in SQLCipher keystore. No server round-trip. No user-facing gate. Compatible with decision 2026-06-15-deferred-cloud (device works locally).
- **CLOUD identity** = same keypair, registered on server via QR pairing flow at first cloud action. Server knows `identity_id = hash(public_signature_key)`.

**Signup gate = QR pairing (TASK-67)**:
- First QR pairing between two devices triggers cloud registration for both identities.
- Physical presence requirement (someone must physically scan a QR displayed on another device) provides Sybil resistance.
- No Google Sign-In, no phone verify, no invitation-code as separate mechanism (QR pairing subsumes invitation-code semantics).

**Sybil defense properties**:
- Attacker generating N LOCAL identities on their own devices produces zero server-side effect — LOCAL identity without cloud upgrade is inert.
- Cloud upgrade requires QR pairing = physical scan on target device = unforgeable remote.
- Attacker with physical access to target's device is social/physical attack, out of scope MVP. Escalation via TASK-103 remote app lock.

**JWT issuance mechanism (MVP)**:
- Firebase Auth Anonymous provider issues JWT after successful QR pairing.
- `identity_id` claim included, matches server-side registry.
- `TODO(server-roadmap): Firebase Auth → own OIDC provider` at own-server migration.

**Non-GMS (Huawei) — MVP out-of-scope**:
- Firebase Auth requires GMS. Non-GMS devices cannot cloud-upgrade in MVP.
- App still functional locally (LOCAL identity, no cloud sync, no messenger, no remote admin).
- Post-MVP: own OIDC provider works platform-agnostic.

**Applies to**:
- **TASK-67** (QR pairing) — signup gate implementation; pairing flow triggers cloud upgrade for both identities.
- **TASK-102** (device management group) — MLS group formed at pairing; bab's device = sole executor.
- **TASK-104** (KeyPackage rate limit) — Sybil vector closed by pairing requirement; TASK-104 mechanisms (pool cap, dedup, last-resort) remain valid drain protection.
- **TASK-105** (server baseline) — signup endpoint = one of the mandatory endpoints; JWT issuance flow specified here.
- **TASK-27** (messenger), **TASK-42** (group encryption) — future features inherit LOCAL/CLOUD identity model.

**Rationale**:

- **Google Sign-In rejected**: (a) breaks device-self-sufficiency (bab shouldn't see login wall); (b) $100-300 = 100 aged Google accounts on darkweb, weak Sybil barrier; (c) privacy leak to Google; (d) non-GMS blocker.
- **SMS phone verify rejected**: SMS toll fraud vector (attacker triggers our SMS budget via premium-rate numbers, $1500-15000 profit per attack); requires additional infra (Turnstile + country blocklist + number reputation) out of MVP scope. Industry (Signal/WhatsApp) copes via SIM cost + shadow banning, but they have dedicated abuse teams we don't.
- **Invitation-code as separate mechanism rejected**: QR pairing already implements physical-presence-based trust invitation. Adding a separate invitation-code system = duplication.
- **QR pairing chosen**: physical scan requirement is naturally Sybil-resistant, natural family UX (pass planshet, scan on second device), doesn't require external identity provider.
- **LOCAL identity at first launch**: mandatory per decision 2026-06-15-deferred-cloud. Also enables offline-first UX for elderly users on unreliable networks.

**Trade-offs**:

- **Bab first-time UX**: bab's device starts with LOCAL identity only. Family setup requires at least one admin to pair (Таня scans QR). If no admin available at setup — bab still uses launcher fully locally, cloud features stay dormant until admin pairs.
- **Attacker with physical access wins**: physical QR scan = full trust delegation. Family threat model accepts this (attacker in bab's home = other more serious problems). Escalation via TASK-103 remote lock post-compromise.
- **Non-GMS users**: no cloud features in MVP. Local features fully work. Post-MVP roadmap addresses via own OIDC provider.
- **JWT lifetime + refresh**: Firebase JWT default 1 hour. Bab's device may need refresh flow if setup slow. Standard Firebase SDK handles automatically; ACL adapter exposes as `refreshAuthToken()` per rule 2.
- **No key transparency** (unlike WhatsApp AKD 2024): family threat model doesn't require public verifiable identity directory. Post-MVP consideration.

**Exit ramp**:

- **Own OIDC provider** (own-server migration): drop-in replacement for Firebase Auth as JWT issuer. Adapter isolation (rule 2) makes this transparent to domain.
- **Phone verify as clinic-preset option** (Phase-3+): add as parallel signup gate with mandatory Turnstile + country reputation checks. Additive change, no LOCAL identity model change. Server-roadmap entry: `SIGNUP-PHONE-001`.
- **Play Integrity attestation** (Phase-3+, high-security preset): server verifies device attestation on signup + JWT refresh. Additive middleware in `push-worker/`. Server-roadmap entry: `SIGNUP-ATTEST-001`.
- **Non-GMS support**: switch JWT issuance to own OIDC provider (works platform-agnostic) or add Huawei ID adapter as second identity provider. Server-roadmap entry: `NON-GMS-SIGNUP-001`.

<!-- SECTION:DISCUSSION:END -->

## Implementation Plan
<!-- SECTION:PLAN:BEGIN -->
_(pending — feature-tasks используют Decision block выше. Main downstream: TASK-67 pairing implementation.)_
<!-- SECTION:PLAN:END -->

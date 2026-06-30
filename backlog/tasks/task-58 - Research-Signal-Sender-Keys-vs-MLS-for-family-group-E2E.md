---
id: TASK-58
title: 'Research: Signal Sender Keys vs MLS for family group E2E'
status: Draft
assignee: []
created_date: '2026-06-26 13:56'
labels:
  - phase-2
  - crypto
  - research
  - server
milestone: m-1
dependencies:
  - TASK-57
priority: high
ordinal: 58000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Чтобы группа (бабушка + 4 родственника) могла обмениваться зашифрованными данными так, чтобы сервер не знал содержимого и связей, нужен **групповой E2E protocol**. Существуют два production-проверенных подхода:

1. **Signal Sender Keys** — каждый member имеет свой sender key, distributed через 1-on-1 X3DH каналы. Используется WhatsApp, Signal, миллиарды пользователей. Простой, проверенный, **но без post-compromise security** (если member скомпрометирован, потом kicked — старые сообщения он мог прочитать).

2. **MLS (Messaging Layer Security, RFC 9420)** — новый IETF стандарт 2023 года. Tree-based key derivation. **Даёт post-compromise security**. Используется Cisco Webex, Wire, Discord переезжает. Сложнее, но математически доказан.

Этот task — **только research**, выбор + foundation. Реализация — в TASK-42 (priority bumped до HIGH после TASK-57 audit-а).

## Зачем

Это **foundation для 6 других task-ов**: TASK-27 (Messenger), TASK-28 (Album), TASK-30 (Wearable), TASK-32 (Audit log), TASK-46 (Shared contacts), TASK-42 (сама group encryption). Если выбрать неправильно — придётся переписывать.

Это также **one-way door**: после первого production user-а wire format группового E2E фиксирован, миграция = поломка всех групп.

## Состояние

Draft. Зависит от TASK-57 (Article XX adoption). Блокирует TASK-42 и косвенно TASK-27/28/30/32/46.

---

## Что входит технически

Для каждого из двух кандидатов произвести deep research, не поверхностный.

### Signal Sender Keys (детальный scope research'а)

Что нужно понять и задокументировать:

- **X3DH bootstrap**: как initial sender key передаётся новому member'у. Требуется ли 1-on-1 encrypted channel через Diffie-Hellman сначала?
- **Sender key chain**: каждый sender имеет свой ratchet chain — как это работает в группе из N member'ов (N chains?).
- **Adding member**: existing member отправляет новому свой sender key через 1-on-1 channel. Кто инициирует — любой existing member или только creator группы?
- **Removing member**: ВСЕ existing members ротируют свои sender keys + рассылают новые. Если N=10 — 10 rotations × (N-1) sends = 90 операций.
- **Forward secrecy**: даёт ли (после rotation старые сообщения не readable новым sender key) или нет (старые keys остаются в local store).
- **Post-compromise security**: если member был скомпрометирован 3 месяца назад, после рототации он всё ещё может прочитать **новые** сообщения? Signal Sender Keys — **нет** post-compromise security.
- **Production deployments**: точные ссылки. WhatsApp Signal Protocol whitepaper, Signal app source code (open).

### MLS (RFC 9420) (детальный scope research'а)

- **TreeKEM**: основа MLS, binary tree key derivation. Как структура tree обновляется при add/remove.
- **Epochs**: каждый change membership = новая epoch. Все members обязаны обработать epoch transitions sequentially.
- **Post-compromise security**: **да**, через regular key rotation на каждый message или каждый epoch.
- **Forward secrecy**: **да**, через ratchet.
- **Add member**: один из existing members готовит Welcome message, новый member processes.
- **Remove member**: epoch update, tree restructure.
- **Library availability на Android Kotlin**:
  - **OpenMLS** (Rust) — production-ready, но Rust binding на Kotlin потребует JNI wrapper.
  - **AWS MLS Library** (Rust) — open-source, AWS использует.
  - **MLSpp** (C++) — Cisco Webex использует.
  - **Нативной Kotlin/JVM реализации в production** на момент 2026-06 — нужно проверить актуальное состояние.
- **Wire-format spec**: RFC 9420 + extension RFCs. Размер protocol описания — около 100+ страниц.
- **Production**: Cisco Webex (production 2022+), Wire (production 2023+), Discord (planned/rolling).

### Comparison dimensions (AC#1 detail)

10 dimensions для table:

1. **Post-compromise security** (даёт / не даёт)
2. **Forward secrecy** (даёт / не даёт)
3. **Complexity of implementation** (LOC estimate, библиотека available / write from scratch)
4. **Library availability на Android Kotlin KMP** (есть готовая / нужен JNI / write from scratch)
5. **Group size scaling** (до какого N остаётся практичным — sender keys O(N^2) на remove, MLS O(log N))
6. **Key rotation overhead** (как часто, сколько данных, кому)
7. **Server requirements** (нужен ли prekey directory? message delivery semantics? what does server need to know?)
8. **Audit history** (формальные доказательства, security audits)
9. **Production deployments** (кто реально использует, как долго, какой масштаб)
10. **Maturity / spec stability** (Signal — 10+ лет проверки; MLS — RFC 2023, может ещё меняться через extensions)

### Decision matrix (AC#2 detail)

Наш use case характеристики:

- **Group size**: 2-7 member'ов (бабушка + 4-5 родственников, иногда + caregiver).
- **Message volume**: low. Не realtime messaging — push triggers + occasional config sync.
- **Latency requirements**: seconds, не milliseconds.
- **Battery constraints**: critical (пожилые телефоны, старые батареи).
- **Threat model**: ghost device attack защита нужна (Safety Number), post-compromise security — nice-to-have но не critical (поскольку pre-compromise attacker уже видел всё).
- **Implementation team**: solo dev (владелец) + AI agents. Не команда криптографов.

Decision matrix должна закрывать вопросы:
- Если выбираем Signal Sender Keys — какие риски (отсутствие post-compromise) приемлемы?
- Если выбираем MLS — где брать library, готовы ли оплатить JNI integration cost?
- Третий вариант — упрощённая собственная схема (envelope encryption keyring без ratchet)? Это **не industry standard**, но может быть достаточно для нашего use case. Аргументы за/против.

### Foundation API sketch (AC#3 detail)

После выбора — нарисовать порты:

```kotlin
// Только пример shape — конкретика зависит от выбора

interface GroupKeyDistribution {
    suspend fun bootstrap(initialMember: PublicKey): GroupState
    suspend fun addMember(group: GroupState, newMember: PublicKey): WelcomeMessage
    suspend fun processWelcome(welcome: WelcomeMessage): GroupState
}

interface MemberAddProtocol {
    suspend fun proposeAdd(group: GroupState, newMember: PublicKey): Proposal
    suspend fun commitProposal(group: GroupState, proposal: Proposal): CommitMessage
}

interface MemberRemoveProtocol {
    suspend fun proposeRemove(group: GroupState, memberToRemove: MemberId): Proposal
    suspend fun commitProposal(group: GroupState, proposal: Proposal): CommitMessage
}

interface KeyRotationProtocol {
    suspend fun rotate(group: GroupState): GroupState   // bumps epoch
}
```

### Effort estimate honesty (AC#4 detail)

Real estimate в часах, не abstract Low/Medium/High:

- Signal Sender Keys из нуля: ~120-200 часов (X3DH + ratchet + Sender Keys + tests).
- MLS из нуля: **не считать**, отказаться сразу.
- MLS через JNI wrapper (OpenMLS): ~80-120 часов (wrapper layer + Kotlin API + tests + JNI lifecycle).
- Custom keyring envelope (no ratchet): ~40-60 часов. Simpler, less secure (no FS / no PCS).

Estimate должен учитывать: crypto review (не самим написать и забыть — нужен external eyes), property-based tests, integration tests on Android.

### Decision document structure (AC#5 detail)

`docs/dev/decisions/2026-XX-XX-group-e2e-protocol.md` должен содержать:

- Context: zero-knowledge model, group E2E required.
- Options: 3 (Signal SK, MLS, custom). Каждый с pros/cons table.
- Decision: какой и почему.
- Consequences: что меняется в client-requirements C2/C8, что в server-requirements.
- Exit ramp: если decision окажется wrong через 2 года — как мигрировать. Wire-format versioning через `protocolVersion` field.
- Regret conditions: что должно случиться, чтобы переосмыслить.

---

## Контекст из mentor-сессии 2026-06-26

Эта секция фиксирует обсуждение, чтобы не воспроизводить mentor.

Владелец спросил: «Используют ли в production такой подход — групповой ключ зашифрован под pub-key каждого member'а?»

Я ответил: «Да, такой паттерн называется **envelope encryption + key wrapping**, используется в Signal, WhatsApp, Matrix, MLS, Tresorit, Bitwarden, 1Password.»

Владелец описал свою интуицию:

> «Групповой ключ зашифрован под pub-key каждого member'а. То же самое, как мы делаем, шифруем конфиг, рядом лежат все файлы. Это зашифрованные симметричные ключи. Шифруются публичными ключами. Расшифровывается приватными ключами. Каждый может расшифровать только то, что зашифровано для себя через свой ключ.»

Это точное описание **Signal Sender Keys distribution** или **MLS Welcome message**. Интуиция совпадает с industry direction.

Я честно признал слабость своего research'а в `server-requirements.md` v2:

> «C2 Group + keyring + C8 forward unsharing — самые сложные компоненты. Я набросал «Signal Sender Keys pattern», но это не спецификация, это направление. Реальная имплементация требует изучить, как Signal реально делает sender keys distribution (X3DH bootstrap, не trivial), решить Signal-стиль vs MLS-стиль (у них разные trade-off, я этим выбором не занимался), подумать про post-compromise security (Signal sender keys не дают, MLS даёт — это влияет на дизайн).»

TASK-58 — закрытие этого долга через **deep research**, не повторение sketchy mention в mentor-сессии.

## Что НЕ делать в TASK-58

- НЕ реализовывать выбранный protocol — это будет TASK-42.
- НЕ закрывать вопросы push payload encoding — это TASK-60.
- НЕ закрывать вопросы recovery vault — это TASK-59.
- НЕ выбирать «третий вариант» (custom envelope) без явного обоснования, что неприменимо Signal SK / MLS.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Comparison table: Signal Sender Keys vs MLS (RFC 9420) по 10 dimensions (post-compromise security, complexity, library availability на Android Kotlin KMP, group size scaling, key rotation overhead, forward secrecy, server requirements, audit history, production deployments, maturity)
- [ ] #2 Decision matrix: для нашего use case (small group 2-7, low message volume, push-triggered не realtime, пожилые телефоны) — какой pattern выбираем и почему
- [ ] #3 Foundation API sketch для победившего pattern: GroupKeyDistribution, MemberAddProtocol, MemberRemoveProtocol, KeyRotationProtocol
- [ ] #4 Estimate effort реальной имплементации в часах (не Low/Medium/High abstract) для победившего варианта
- [ ] #5 Документ docs/dev/decisions/2026-XX-XX-group-e2e-protocol.md с rationale, alternatives, exit ramp
- [ ] #6 TASK-42 description обновлён ссылкой на этот research документ
<!-- AC:END -->

---
id: TASK-57
title: Zero-Knowledge Server Architecture audit + Article XX adoption
status: Draft
assignee: []
created_date: '2026-06-26 13:53'
labels:
  - phase-2
  - architecture
  - server
  - crypto
milestone: m-1
dependencies: []
priority: high
ordinal: 57000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Сейчас у нас сервер в проекте задуман как «умный»: знает, кому какие конфиги принадлежат, кто чей помощник, какие event types бывают, ведёт историю, делает refcount. Это **противоречит** принципу end-to-end шифрования — сервер не должен знать содержимое и связи, иначе он становится единой точкой утечки (DB dump, insider, hosting compromise).

Мы родили в обсуждении 2026-06-26 новую модель **zero-knowledge сервер** (V2):
- Сервер знает только opaque blob'ы и opaque IDs.
- Сервер проверяет Ed25519 подписи на write, не строит ACL graph.
- Сервер не понимает eventType, content, ownership relations.
- Membership, history rotation, forward unsharing, push routing — всё на клиенте.

Два больших документа описывают модель: [server-requirements.md](../../docs/dev/server-requirements.md) и [client-requirements-for-zero-knowledge-server.md](../../docs/dev/client-requirements-for-zero-knowledge-server.md). Эти документы — **draft** уровня «архитектурное направление», написан в одном чате без deep research'а. До того как реально кодить под эту модель, **надо**:

1. Зафиксировать направление в конституции (Article XX), чтобы каждая будущая фича шла через этот фильтр.
2. Создать checklist-skill, который автоматически проверяет любую spec'у на server-side компонент против Article XX.
3. Перепроверить сами draft-документы (V2 — это «sketch», не «engineering research»; нужно сверить каждый Tier с industry pattern в production использовании).
4. Пройтись по backlog'у и пометить task'и, которые **под угрозой rewrite'а**, если они спекаются по старой smart-server модели.
5. Поднять TASK-42 (Family group Signal-style) в HIGH — это **foundation** для 6 других task'ов group E2E.

**Что НЕ входит в этот task**: реализация zero-knowledge компонентов (это будут отдельные task'и после audit'а), глубокий research по Signal Sender Keys vs MLS (это TASK-58), детальное проектирование recovery vault (TASK-59), push payload constraints (TASK-60).

## Зачем

Без зафиксированного направления каждая новая фича будет тяготеть к «сделаем на сервере, так удобнее» → накопится smart-server долг → в production невозможно отыграть назад (wire format фиксированный, миграция = поломка пользователей). Article XX — это **тормоз** для удобных-но-неправильных решений.

Audit backlog'а — это **раннее выявление**. Сейчас 13 task'ов в Draft статусе под угрозой (🟡), 8 нужны полный концептуальный reset (🔴). Если каждый из них пойдёт `/speckit.specify` без pre-spec design review — каждый из них через 2-3 недели придёт к моменту «упс, надо переписать половину».

## Что входит технически (для AI-агента)

- **Article XX в конституции**: 3 принципа, refuse signals для каждого, ссылки на server-requirements.md.
- **checklist-zero-knowledge-server skill**: 6-10 проверок на любую spec'у с server-side компонентом (opaque IDs? signature auth не ACL graph? eventually consistent client coordination? Tier 2 logic обоснован bypass impossibility?).
- **Audit task'ов**: список 🟢 safe (можно делать as-planned), 🟡 at risk (pre-spec design review), 🔴 needs reset (концепция меняется). Список выработан в чате 2026-06-26, но требует validation против актуального backlog'а.
- **V2 research pass**: для каждого Tier endpoint найти **production reference** (не просто whitepaper), оценить, насколько проверено индустриально, отметить открытые вопросы. Для Tier 2 (T1 JWT, T2 vault counter, T3 entitlement, T4 sealed unseal) — особенно тщательно, так как это допущенные исключения из zero-knowledge.

## Состояние

Draft. Создан 2026-06-26 после mentor-сессии «zero-knowledge server architecture». Ждёт прохождения через `/speckit.specify` → `/speckit.clarify` → `/speckit.plan` → `/speckit.tasks` → `/speckit.implement`.

**Зависимости**: ни от чего не зависит, можно начинать сразу. Блокирует: pre-spec гейт для TASK-21, TASK-24, TASK-25, TASK-27, TASK-28, TASK-30, TASK-31, TASK-32, TASK-46, TASK-39, TASK-40, TASK-41, TASK-44, TASK-47, TASK-48 (см. секцию «Audit backlog'а» ниже).

**Связанные research tasks**:
- **TASK-58**: Signal Sender Keys vs MLS research для group E2E.
- **TASK-59**: Recovery vault counter — SVR-style vs OPAQUE protocol vs simpler MAC.
- **TASK-60**: Push payload encryption + FCM 4KB constraint.

---

## История обсуждения (контекст 2026-06-26)

Эта секция фиксирует mentor-сессию, в которой родилась модель V2, чтобы при возврате к task'у не пришлось заново проходить через рассуждения.

### Откуда родилось

Владелец готовился обсудить серверную часть с другим AI-агентом и попросил собрать контекст. Я сначала собрал draft `server-requirements.md` v1 с **26 функциональными требованиями** (Auth, Envelope storage, PublicKeyDirectory, DeviceId allocation, Push trigger, Blob storage, Config history, Recovery vault, и т.д.).

Владелец прочитал и заметил: **«сервер слишком много знает»**. Конкретные replies из обсуждения (важные для контекста):

> «По рассуждениям я понял, что предполагается, что сервер будет знать, кому какие конфиги принадлежат и так далее. То есть сервер должен быть достаточно умный, чтобы понимать, кому что доступно. Это принципиально неправильно, потому что мы используем e2e шифрование, поэтому сервер ничего не должен знать о внутренней кухне как работает приложение.»

> «Максимум у сервера есть только что-то вроде файловой системы, где через user ID или device ID и с проверкой JWT токена отдаёт зашифрованные данные вместе с цех ключами.»

Это **разворот architecture stance**. Принципиальный, не «давайте упростим».

### Какие альтернативы рассмотрели

1. **Smart server (V1)** — текущий dominant pattern в backlog: server понимает grants, eventType, ownership graph. **Отвергнут** как metadata leak vector.
2. **Полностью server-less (P2P)** — отвергнут как нереалистичный для пожилых телефонов (battery, sync problems).
3. **Zero-knowledge server (V2)** — sealed storage + minimal directory + server-required logic только где client-bypass невозможен. **Принят**.

### Industry patterns, на которые опирается V2

Не выдумано — обсуждено и подтверждено через research:

- **Signal Sender Keys / Sealed Sender** — групповое messaging, server не знает membership. Production: WhatsApp, Signal, миллиарды юзеров.
- **MLS (RFC 9420)** — IETF стандарт 2023, формальное доказательство security. Production: Cisco Webex, Wire, Discord переезжает.
- **Tresorit envelope wrapping** — file storage, server видит только ciphertext + opaque relationships.
- **Bitwarden** — open-source реализация envelope encryption.
- **WhatsApp E2E Encrypted Backup** — HSM-protected backup key.
- **Signal SVR (Secure Value Recovery)** — Intel SGX enclave для vault counter.
- **HashiCorp Vault** — Shamir N-of-M unsealing pattern.
- **Apple iCloud Advanced Data Protection** (2022) — Apple server переключается на zero-knowledge для opted-in users.

### Что V2 говорит (для контекста при возврате)

Три tier endpoint'ов:
- **Tier 0 — Sealed storage** (80% endpoints). Server видит: opaque namespace ID + opaque key + ciphertext + signing pubkey владельца namespace. НЕ видит: контент, owner identity, тип данных, связи. Auth = signed write Ed25519.
- **Tier 1 — Minimal directory**. Server видит: opaque ID → pubkey bytes (или FCM token bytes). НЕ видит: связи между ID. Только для async pubkey discovery (X3DH pattern).
- **Tier 2 — Server-required logic**. Допускается только если client-side bypass возможен через Clear App Data / factory reset / root. Единственные кейсы: atomic anti-brute-force counter, subscription entitlement timer, JWT issuance.

С 26 функциональных требований v1 свернулись в **7 endpoint категорий v2** (S0, S1, S2, D1, D2, T1, T2, T3 + T4 sealed unseal как операционная процедура).

### Ключевая мысль владельца про распределение работы

> «Если сервер ничего не знает, значит, приложение наше должно знать, что оно принимает и что отправляет.»

Это — **bremiya** на сложности клиента. V2 правильна только если клиент готов взять на себя: membership management, history rotation, schema migration, forward unsharing, push routing, audit log, GDPR export, algorithm migration. Это весь [client-requirements-for-zero-knowledge-server.md](../../docs/dev/client-requirements-for-zero-knowledge-server.md) с 20 client component'ами.

### Что осталось unresolved

Я честно (по запросу владельца «насколько качественно это исследование?») признал:

> «Не стоит сворачивать приложение под этот V2 как есть. Это архитектурный draft мощностью «mentor-сессия в одном чате», не engineering research. Качество — где-то между «brainstorm» и «design sketch».»

Конкретные слабости V2 (важны, чтобы их закрыть в re-validation на AC#3-4):

1. **C2 Group + keyring + C8 forward unsharing** — самые сложные компоненты. Я набросал «Signal Sender Keys pattern», но это не спецификация. Реальная имплементация требует выбор Signal-стиля vs MLS-стиля + детальный protocol design. → TASK-58.
2. **C18 SelfStateBackup** — упомянуто как critical, но не разработано. Что туда кладётся? Что при забытом passphrase? Tonkaya tema.
3. **T2 Recovery vault counter** — упомянул Signal SVR, но это требует Intel SGX, у нашего hosting'а нет. → TASK-59.
4. **D1 X3DH prekey directory** — упомянуто, не детализировано. Prekey lifecycle, replenishment, ratcheting — 30+ страниц Signal spec'и.
5. **C16 Replay protection** — «храним last 100 nonces» — handwave. Реальный pattern: sliding window sequence numbers.
6. **Authorization на blob writes** — Ed25519 signature ОК, но multi-writer группы требуют multi-signer auth. Не разобрано.
7. **C20 Conflict resolution** — optimistic locking упомянут, но multi-writer CRDT-merge для keyring blob'а — не разобрано.
8. **Push payload в FCM 4KB constraint** — упомянут, не закрыт. → TASK-60.
9. **Migration plan from current Firestore-based state** — overall **не разработан**.
10. **Cost analysis** — zero-knowledge = клиент больше работает (battery, CPU, bandwidth). Для пожилых на старых телефонах — не measured.

---

## Audit backlog'а (результат classification 2026-06-26)

Прошёл по 50 backlog task'ам, классифицировал каждый.

### 🟢 Safe — можно делать как запланировано (14 task'ов)

Не зависят от server architecture, либо локальные, либо infrastructure.

| Task | Почему safe |
|---|---|
| TASK-8 Admin App + QR Pairing | Pairing exchange opaque IDs — friendly |
| TASK-9 Contact Tiles + Handoff Calling | Локальная функциональность |
| TASK-10 SOS Capability | Будущий push trigger — клиентский, friendly (но см. TASK-60 для encoding) |
| TASK-11 Contact Photos | Encrypted blobs — friendly если правильно сделать |
| TASK-13 VersionedConfigViewer | Локальное чтение |
| TASK-16 Preset Schema v2 | Локальный wizard |
| TASK-17 Android Deep Integration | Локальная функциональность |
| TASK-29 Android TV Preset | Локальная |
| TASK-37 Self-hosted Sentry migration | Independent от data plane |
| TASK-50 CI gate | Infrastructure |
| TASK-52/53/54 (UI bugfixes) | UI |
| TASK-55 Verification aggregator | Infrastructure |
| TASK-56 namespace rename | Internal refactor |

### 🟡 At risk — нужен design review против V2 до старта (13 task'ов)

Концепция может остаться, но **дизайн endpoint'ов / wire-format'ов** меняется.

| Task | Риск |
|---|---|
| **TASK-4 Own config E2E (envelope)** ✅ verification | Code уже написан под `users/{uid}/data/{key}` paths. Не катастрофично — можно мигрировать на opaque namespace через adapter swap (`EnvelopeStorage` port уже есть). Но **wire-format paths надо менять до production**. |
| **TASK-6 Root Key Hierarchy + Owner Recovery** ⏸ paused | Recovery vault сейчас Firestore-based. V2 говорит T2 server (counter atomic). При re-start TASK-6 нужно ориентировать на zero-knowledge interface. Зависит от TASK-59. |
| **TASK-15 Subscription Server Timer** | T3 entitlement в V2 — server-validated JWT. Совпадает с zero-knowledge подходом, но дизайн сейчас не сделан. До старта надо договориться, что entitlement signing key — server's, не Google Play receipts напрямую. |
| **TASK-21 Account Recovery + 2FA escrow** | Текущая концепция — multi-admin envelope. V2 говорит: keyring-based group, не multi-admin envelope. Архитектурный pivot. Зависит от TASK-58 + TASK-59. |
| **TASK-22 Optional Step Reminder System** | Если включает push event types — надо делать client-side eventType registry. Зависит от TASK-60. |
| **TASK-24 Device Inventory Sync** | Текущий план — список устройств на сервере. V2: encrypted blob в namespace. **Полный rewrite plan'а.** |
| **TASK-25 Multi-app Cohabitation + Chain-of-trust** | Chain-of-trust требует pubkey directory. V2: anonymous lookup, не привязанный к userUid. Дизайн пересматривается. |
| **TASK-27 Elderly-Friendly Messenger (Jitsi)** | Messenger = group E2E. Самый острый случай — выбирать Signal Sender Keys vs MLS до старта. Зависит от TASK-58. |
| **TASK-28 Full Shared Family Album** | Group sharing. Forward unsharing = client-coordinated. Зависит от TASK-58. |
| **TASK-30 Wearable Health Monitoring** | Time-series ingest. V2: encrypted blobs в namespace, не серверный sensor endpoint. **Полный rewrite.** |
| **TASK-31 Caregiver Remote Invite** | Pairing channel. V2: opaque namespace + keyring. |
| **TASK-32 Audit Log Infrastructure** | V2: client-side encrypted append-only blob. **Полный rewrite концепции.** |
| **TASK-46 Shared admin contact book** | V2: shared contacts = blob внутри group namespace. Зависит от TASK-58. |

### 🔴 Needs reset — концепция была под smart-server (8 task'ов)

Сама идея фичи переформулируется.

| Task | Что меняется |
|---|---|
| **TASK-39 Social recovery** | ADR-008 предполагает server-coordinated 2FA push. V2: client-coordinated через group keyring. ADR-008 надо обновлять. |
| **TASK-40 Multi-device per user** | V2: opaque namespaces per device, sync через encrypted self-state backup. **Полный rewrite.** |
| **TASK-41 Key rotation / forward secrecy** | Forward secrecy в group = MLS tree update или Signal sender keys ratchet. **Это и есть TASK-42 на самом деле.** Возможно объединить. |
| **TASK-42 Family group encryption → Signal-style** | **Это и есть V2 core direction.** Поднять priority с LOW до HIGH. Foundation для TASK-27/28/30/32/46. AC#7 в этом task'е. |
| **TASK-44 Security sensors integration** | Sensor data → encrypted blobs. **Полный rewrite.** |
| **TASK-47 Family Activity Challenges** | Если есть push event types — клиентский registry. |
| **TASK-48 Tamper-resistance escalation L1+L2+L3** | L3 attestation для клиента ОК, но требует server-side hash compare. Под zero-knowledge — атестация **сервера**, не клиента. Концепция полностью другая. AC#8. |

---

## Article XX — draft содержания (для AC#1)

При написании Article XX в `.specify/memory/constitution.md` опираться на этот sketch:

```
## Article XX. Zero-Knowledge Server Architecture

When any feature involves a server-side component (storage, push, sync, auth,
pairing, group operations), the server MUST be designed as zero-knowledge:
it knows opaque blobs and IDs but not their meaning, ownership graph, or
business semantics.

### XX.1 Sealed Server Default

Every server endpoint defaults to **Tier 0 (sealed storage)**:
- Server sees: opaque namespace ID + opaque key + ciphertext + namespace
  owner signing pubkey.
- Server does NOT see: content, owner identity (userUid), data type
  (config/photo/message), graph relationships (who-grants-whom).
- Authorization: Ed25519 signature verification against namespace-recorded
  signing pubkey. NOT an ACL graph.

Tier elevation requires explicit justification:
- **Tier 1 (minimal directory)**: only for async pubkey discovery patterns
  (X3DH). Server sees opaque ID → pubkey mapping, no relationships.
- **Tier 2 (server-required logic)**: only when client-side bypass is
  possible through Clear App Data / factory reset / root. Currently
  justified: anti-brute-force vault counter, subscription entitlement
  timer, JWT issuance for anti-abuse.

### XX.2 Client Coordinates, Server Stores

Business logic lives on the client. The server is a sealed storage
appliance. Specifically server MUST NOT do:
- Membership management for groups (use keyring blob inside namespace).
- History rotation (client LIST + DELETE).
- Schema transformers vN→vCurrent (client lazy migration).
- Forward unsharing on member removal (client re-key + re-encrypt).
- Push routing by event type (encrypted payload, server forwards opaque).
- Audit log of user operations (client encrypted append-only blob).
- GDPR export of business meaning (client decrypts own blobs).
- Resolve recipients for delivery (client provides explicit token list).

### XX.3 Opaque Identifiers Everywhere

Every ID visible to the server is an opaque UUID NOT tied to identity.
- `userUid → namespace` mapping lives only on the client.
- No URL path like `/users/{uid}/data/{key}` — use `/namespaces/{nsId}/blobs/{key}`.
- No `linkId` as a primary concept exposing pairing graph.
- No server-side join between `identity-links` and `data` tables.

### Refuse signals

The following patterns MUST be refused with surface-and-propose-alternative:

1. Server endpoint accepting `userUid` as routing identifier.
2. Server endpoint understanding `eventType` (push or otherwise).
3. Server endpoint storing membership graph / access grants / object relations.
4. Server endpoint executing business logic over plaintext.
5. Cron job on server enforcing business rules (retention by TTL OK, business
   rule by content NOT OK).
6. ACID transaction cross-document on server for membership operation.
7. Server-side resolver «кто кому помощник» / «who-is-admin-of».
8. Tier 2 logic without explicit proof that client bypass is possible.

### Reference

The full requirements specification: `docs/dev/server-requirements.md` v2.
Client deltas required: `docs/dev/client-requirements-for-zero-knowledge-server.md`.
Industry patterns supporting this: Signal Sender Keys, MLS (RFC 9420),
Tresorit, Bitwarden, WhatsApp E2E Backup, Apple iCloud Advanced Data Protection.
```

---

## checklist-zero-knowledge-server skill — sketch (для AC#2)

При написании skill в `.claude/skills/checklist-zero-knowledge-server/SKILL.md`:

**Trigger** (через `procedure-assess-spec-complexity`): любой spec, упоминающий server, backend, cloud, sync, push, pairing, group, membership, share, sensor ingest, history, audit.

**Checklist items**:
1. Все server endpoints используют opaque IDs (не `userUid` в path)?
2. Authorization через signature verification на каждом write, а не через ACL граф?
3. Если есть group / membership concept — реализован как client-coordinated keyring blob внутри namespace?
4. Если есть push event — payload encrypted под shared key, server forwards opaque ciphertext?
5. Если есть history / retention — реализовано как client LIST+DELETE (по TTL header'у) либо cron-time-based (не business-rule-based)?
6. Если есть forward unsharing / member kick — реализовано как client re-key + re-encrypt?
7. Если есть Tier 2 endpoint (counter, timer) — задокументировано почему client-side bypass возможен (Clear App Data / factory reset / root)?
8. Wire-format payload содержит `schemaVersion`?
9. Authorization — Ed25519 signature, не Firebase Auth ACL?
10. Migration plan от текущего state Firestore-based (где applicable) сформулирован?

**Output format**: то же, что и другие checklist-* skills — CHK номера, pass/fail с rationale.

---

## Re-validation pass (для AC#3-4)

Для каждого endpoint в `server-requirements.md` v2 пройти через **3 проверки**:

1. **Production reference exists**: не просто whitepaper, а реально работающая система. Указать конкретный production system + ссылку на public technical description.
2. **Honest assessment of weakness**: что в нашем sketch'е упрощено по сравнению с production reference. Sketchy areas явно помечены.
3. **Open questions**: что осталось unresolved для этого endpoint'а. Если есть — открыть отдельный research task или зафиксировать в TASK-58/59/60.

Для каждого client component в `client-requirements-for-zero-knowledge-server.md` пройти:
1. **Coverage**: какие server endpoints этот component использует. Mapping должен покрывать все endpoints.
2. **Realistic effort**: переоценить «Low/Medium/High» в часах. Особенно C2 (group keyring), C8 (forward unsharing), C18 (self-state backup) — это weeks, не days.
3. **Industry pattern actually applicable**: pattern, на который ссылаемся, реально подходит нашему use case или это handwave.

---

## Что точно НЕ делать в рамках TASK-57

- НЕ менять wire-format существующего TASK-4 (он Verification, отдельный follow-up если решим мигрировать).
- НЕ реализовывать никаких C1-C20 client components — это отдельные task'и после audit'а.
- НЕ выбирать Signal vs MLS — это TASK-58.
- НЕ проектировать recovery vault counter — это TASK-59.
- НЕ решать push payload encoding — это TASK-60.
- НЕ удалять документы V1 (server-context-for-ai-agent.md и т.д.) — оставить как historical context.

---

## Готовый промт для `/speckit.specify`

```
ЧТО СТРОИМ
Архитектурный pivot документации: фиксируем zero-knowledge server модель как
обязательную для всех будущих фич с server-side компонентом. Не реализация,
а конституционное закрепление + аудит существующего backlog'а + перепроверка
двух draft-документов (server-requirements.md v2, client-requirements-for-zero-knowledge-server.md).

ЗАЧЕМ
Без формальной фиксации каждая новая фича дрейфует к smart-server по
inertia. После production launch'а zero-knowledge pivot невозможен (wire
format фиксированный). Сейчас 13 task'ов в Draft под угрозой rewrite'а, 8
нужны концептуальный reset. Article XX — single source of truth для
будущих /speckit.* проходов.

SCOPE ВКЛЮЧАЕТ
- Article XX в .specify/memory/constitution.md (3 принципа + refuse signals).
- checklist-zero-knowledge-server skill в .claude/skills/.
- Regenerate procedure-assess-spec-complexity → добавить trigger на server/cloud/sync.
- Перепроверка server-requirements.md v2: каждый Tier 0/1/2 endpoint с production reference.
- Перепроверка client-requirements-for-zero-knowledge-server.md: C1-C20 покрывают endpoints без дыр.
- Audit 50 backlog task'ов: классификация safe/at-risk/needs-reset.
- Description-патчи в at-risk/needs-reset task'и: пометка «pre-spec design review против Article XX обязателен».
- TASK-42 priority bump LOW → HIGH.
- TASK-48 концепция: reset или close.
- ADR-008 пометка «superseded».
- Запись в server-roadmap.md журнал изменений.

SCOPE НЕ ВКЛЮЧАЕТ
- Реализация zero-knowledge компонентов (C1-C20) — отдельные task'и после audit'а.
- Signal vs MLS deep research — TASK-58.
- Recovery vault counter research — TASK-59.
- Push 4KB constraint research — TASK-60.
- Migration plan для TASK-4 (он уже Verification, отдельный follow-up).
- Code changes в существующих модулях.

DEPENDENCIES
- Существующие документы: server-requirements.md v2, client-requirements-for-zero-knowledge-server.md, server-roadmap.md.
- CLAUDE.md §1-§8 (особенно §1 domain isolation, §5 wire-format, §8 server-roadmap).

ACCEPTANCE CRITERIA (см. секцию Acceptance Criteria выше).

LOCAL TEST PATH
- Linter / fitness rules не применимы (это документация).
- Verify: `grep "smart-server\|server.knows.eventType\|userUid.in.path" docs/` = 0 после правок.
- Verify: каждый 🟡/🔴 task в backlog имеет marker «pre-spec review required».

CONSTITUTION GATES
- Article XX становится частью конституции — версия bumps до 1.7.0.
- procedure-constitution-check добавляет gate для Article XX (Server Architecture Compliance).
- Sync Impact Report в начале constitution.md обновляется.

EFFORT
1-2 недели deep work: review существующих документов (3 дня) + Article XX
с примерами (1 день) + skill (1 день) + backlog audit с per-task descriptions
(3-4 дня) + перепроверка V2 против production references (3-4 дня).
```

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Article XX «Zero-Knowledge Server Architecture» добавлен в .specify/memory/constitution.md с тремя принципами (Sealed Server Default, Client Coordinates Server Stores, Opaque Identifiers Everywhere)
- [ ] #2 checklist-zero-knowledge-server skill создан в .claude/skills/ и зарегистрирован в procedure-assess-spec-complexity
- [ ] #3 server-requirements.md v2 перепроверен на корректность — каждый Tier 0/1/2 endpoint обоснован industry pattern reference (Signal/MLS/Tresorit/Bitwarden) с актуальными ссылками
- [ ] #4 client-requirements-for-zero-knowledge-server.md перепроверен — C1-C20 deltas покрывают все endpoints из server-requirements.md, нет дыр
- [ ] #5 audit backlog: каждый из 🟡/🔴 task-ов получил пометку в description о необходимости pre-spec design review против Article XX
- [ ] #6 ADR-008 social recovery помечен «superseded by zero-knowledge model» без удаления исторического контекста
- [ ] #7 TASK-42 Family group encryption Signal-style priority поднят с LOW до HIGH (foundation для TASK-27/28/30/32/46)
- [ ] #8 TASK-48 Tamper-resistance L1+L2+L3 концепция пересмотрена или закрыта
- [ ] #9 docs/dev/server-roadmap.md журнал содержит запись 2026-06-26 о принятии Article XX
<!-- AC:END -->

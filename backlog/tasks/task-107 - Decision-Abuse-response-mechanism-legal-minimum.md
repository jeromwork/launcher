---
id: TASK-107
title: 'Abuse response umbrella (post-MVP): arbitration, open vs closed groups, auto-detection'
status: Paused
assignee: []
created_date: '2026-07-07'
updated_date: '2026-07-07'
labels:
  - decision
  - crypto
  - legal
  - abuse
  - post-mvp
  - umbrella
milestone: m-2
dependencies:
  - TASK-105
  - TASK-108
  - TASK-111
priority: medium
ordinal: 107000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

**Проблема**: наш сервер по дизайну **не видит содержимое** (E2E encryption). Но когда пользователь пожалуется на плохой контент («это фото не должно быть в семейном альбоме», «этот человек шлёт мне угрозы»), сервер должен что-то сделать. Иначе:

1. **App Store / Google Play нас не пустят** — их правила требуют complaint mechanism + block + timely response.
2. **EU / US законы оштрафуют** — DSA (Digital Services Act), DMCA notice-and-takedown, CSAM reporting obligations.
3. **Family members не смогут защититься** внутри своего family group — некому пожаловаться на abusive caregiver'а.

**Ключевое противоречие**: сервер должен реагировать на abuse **не видя контента**. Единственный workable pattern — **user-reported**: жертва (или свидетель) явно жмёт кнопку «Report», передает конкретный blob + метаданные серверу с consent, сервер выполняет action (обычно — hard-delete + опционально freeze reported identity).

**Что сюда НЕ входит**:
- SOS (TASK-11) — жертва зовёт на помощь, не жалуется. Разный UX и разные server actions.
- Automated content scan (Apple CSAM 2021 pattern) — retracted approach с массовым backlash. Ломает E2E. Отклоняем сразу.
- Government backdoor / lawful access — не строим, никогда.

## Зачем

**НЕ blocking для MVP** (пересмотрено 2026-07-07):

Первоначально предполагалось что TASK-11 / TASK-27 / TASK-28 blocking. Владелец указал: **все три — closed-group features** (аналог iCloud Photos family sharing / WhatsApp private groups), не public UGC:
- TASK-11 Contact Photos — admin загружает фото внутри family, E2E encrypted.
- TASK-27 Messenger — voice/video calls внутри family MLS group.
- TASK-28 Family Album — media sharing внутри family.

Для closed-group access-controlled sharing app-store review НЕ требует report mechanism. Sufficient:
- **Client-side block/mute/leave** (member выходит из group локально).
- **Admin remove-from-group** (bab's device removes admin через profile reconciliation, TASK-102).
- **In-band communication** (family members знают друг друга, конфликты решают в offline life).

**Становится blocking когда** — любой из:
1. **Открытые группы** попадают в scope (public discovery, admin-approved joining strangers).
2. **Публичная feature** exposes content beyond family group boundary.
3. **Legal jurisdiction change** — если targeting EU large-platform threshold или US public-facing shipping.

До этого — post-MVP concern, closed-group MVP не блокируется.

## Что входит технически (для AI-агента)

**Layers**:

1. **Legal layer** — юрисдикции (EU DSA / US Section 230 / Apple T&C / Google Play T&C), CSAM reporting obligations (NCMEC US / INHOPE EU), DMCA agent designation. Не code — infrastructure decisions.

2. **Report ingestion layer** — где пользователь жалуется:
   - In-app UI (для family members внутри group).
   - External URL / email (для third-party reporters).

3. **Report evaluation layer** — как принимается решение action:
   - No evaluation (просто выполняем): дешёвый, но censorship attack surface.
   - Threshold-based (N reports от distinct identities → action): требует metric threshold policy.
   - Manual review (owner делает human review): не масштабируется, но fine для MVP < 1000 users.

4. **Enforcement action layer** — что делает сервер:
   - Hard-delete blob (mechanism уже в TASK-111).
   - Freeze reported identity_id — block cloud upgrade, block pairing, block KeyPackage claim (LOCAL app продолжает работать per TASK-106).
   - Warn reported user через push.
   - Forward к authorities (для CSAM — mandatory к NCMEC).

5. **UX layer** — как выглядит report button, что видит reporter, что видит reported, что видит группа.

6. **Adjacent policy** — Terms of Service, Privacy Policy, DMCA agent, data retention для reports (сколько дней храним report metadata).

**Preset differences** (family vs clinic):
- Family: minimal in-app block + support email + owner ручной review.
- Clinic: audit trail, patient advocacy contacts, HIPAA-adjacent obligations, external ombudsman integration.
- B2B: separate T&C, enterprise contract.

## Состояние

**PAUSED 2026-07-07** — post-MVP умbrella. Session 1 mentor Part A написан (7 вопросов), но владелец **явно отложил** до post-MVP по причинам:

1. Сейчас нет ресурсов возиться с UX abuse-flow, арбитражем, T&C / Privacy Policy.
2. **Это не одна задача, а целый пласт** — под этим umbrella несколько отдельных decisions (см. topic list ниже).
3. MVP scope: family launcher без public UGC surfaces (мессенджер + family album в Phase-3+). Legal minimum становится blocking только когда открываем public UGC feature.

**Что осталось в задаче как пометки для возврата** (topic list, не action items):

### Topic 1: Арбитраж (не binary decision)

Один пользователь жалуется — другие members того же group наоборот считают контент нормальным (или сами его загружали). Простого «report → delete» недостаточно:
- Threshold-based (N distinct reporters) — защищает от single-user censorship attack.
- Weight by group role — admin report weight ≠ member report weight?
- Group-level dispute mechanism — открытый audit trail внутри группы?
- Timeout for automatic action — grace period где reported user может ответить?

### Topic 2: Открытые vs закрытые группы — разные политики

**Закрытые группы** (family, clinic patient circle, private team — наш MVP focus):
- Membership по invite only (QR pairing).
- Metadata не публичны, group discoverability disabled.
- Moderation через report сомнительна — все friends, конфликт лучше решать через in-band block или out-of-band разговор.
- Server auto-delete в чужой family = privacy violation.
- **Вывод**: минимум server-side action, максимум client-side self-help (block, mute, leave).

**Открытые группы** (future feature, Phase-4+):
- Public metadata (name, description) для search / discovery.
- Anyone can request join, admin approves.
- Moderation имеет смысл — group становится semi-public space.
- Server auto-delete приемлем при threshold reports от distinct identities.
- Group admin получает additional moderation tools.
- **Вывод**: full report/action flow нужен, threshold policies, admin escalation UI.

**Architectural implication**: group-visibility field (`closed | open`) в preset, dispatches к разным abuse policies. **Wire format должен нести это** с day 1 если открытые группы вообще на roadmap (rule 5 wire-format versioning).

### Topic 3: Server-side auto-detection вредности (отдельная разработка)

- **Что это**: ML/heuristic классификация контента на сервере (NSFW image detection, hate speech text detection, CSAM hash matching).
- **Проблема для нашей E2E модели**: сервер не видит plaintext. Единственный workable pattern — **client-side scanning + server report** (Apple CSAM 2021 pattern — retracted из-за backlash).
- **Alternative pattern** (для открытых групп): контент по definition public → сервер может видеть unencrypted → traditional ML detection работает.
- **Не для family / closed groups** — там E2E строго держим.
- **Отдельная разработка**: ML infrastructure, model training / procurement, false-positive tuning, appeal flow. ~3-6 месяцев минимум.
- **Not MVP, not Phase-3, likely Phase-5+ или never**.

### Topic 4: Legal minimum (был Part A вопрос Q1-Q7 ниже)

Юрисдикции, DMCA agent, NCMEC reporting, T&C / Privacy Policy — становится blocking когда first shipping в EU / US с UGC surface. До этого — parked.

### Topic 5: CSAM specifically

Отдельный decision от general abuse — mandatory legal reporting обязательство отдельного class'а. Blocking только при family album ship (Phase-3+).

---

**Return trigger** (когда разморозить):
1. **Открытые группы** попадают в scope (public discovery, admin-approved joining strangers → moderation имеет смысл).
2. **Публичная feature** exposes content beyond family group boundary (public profile, sharable link к контенту не для family).
3. **Legal jurisdiction change** — targeting EU large-platform threshold или US public-facing shipping.

**НЕ является return trigger'ом** (пересмотрено 2026-07-07):
- TASK-11 / TASK-27 / TASK-28 shipping — все три closed-group, аналог iCloud family sharing. App-store review не требует abuse mechanism для private access-controlled sharing.

**При возврате**: НЕ снимать Paused одним action'ом. Разбить на sub-tasks по topic list (TASK-107a арбитраж, TASK-107b open-vs-closed policy, TASK-107c auto-detection research, TASK-107d legal-minimum). Тогда TASK-107 становится umbrella / meta-tracking.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] Session 1 mentor Part A written (7 questions surfaced)
- [x] #2 [hand] Owner explicitly postponed (2026-07-07): «это не одна задача, это целый пласт, разбираться когда дойдем»
- [x] #3 [hand] Topic list зафиксирован в SECTION:DESCRIPTION § Состояние (арбитраж / open-vs-closed / auto-detection / legal / CSAM)
- [x] #4 [hand] Return triggers прописаны (family album ship / messenger ship / open groups в roadmap / store review reject)
- [ ] #5 [hand] При return: split TASK-107 на sub-tasks (TASK-107a арбитраж, TASK-107b policy, TASK-107c auto-detection, TASK-107d legal)
- [x] #6 [hand] Confirmed 2026-07-07: TASK-11 / TASK-27 / TASK-28 НЕ blocking, все три — closed-group features. НЕ добавлять `dependencies: [TASK-107]`. Blocking triggers переехали в § Состояние return triggers (открытые группы / public feature / legal jurisdiction change).
<!-- AC:END -->

## Discussion
<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 (2026-07-07, mentor skill invoked)

#### A.1 Что за область

**Legal-minimum abuse response** — механизм обработки жалоб пользователей на плохой контент в системе, где сервер **принципиально не видит содержимого** (E2E). Три параллельных драйвера:

1. **App-store requirements** (Apple § 1.2, Google Play UGC): требуют method to report + block + timely response + published contact. Универсальные, применяются везде.
2. **Legal jurisdictions**: EU DSA (notice-and-takedown), US DMCA (copyright), UK Online Safety Act, Germany NetzDG, Australia OSA. Разные пороги — small platforms (< 45M EU users) получают лёгкий compliance.
3. **In-family abuse**: caregiver контролирует бабушку, remote admin блокирует legitimate contacts, family member шлёт inappropriate content. Требует UX внутри app'а, не обязательно server-mediated.

Без этого механизма **TASK-11 / TASK-27 / TASK-28 не могут ship'нуться** — Google Play review reject'нет за отсутствие report button при UGC.

#### A.2 Карта темы

Область разбивается на 6 слоёв, каждый — отдельное architectural decision:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Legal layer                                              │
│    Jurisdictions, DMCA agent, NCMEC reporting, Cloudflare   │
│    T&C, EULA / Privacy Policy templates                     │
├─────────────────────────────────────────────────────────────┤
│ 2. Report ingestion layer                                   │
│    In-app UI (in-band) + external URL/email (out-of-band)   │
│    → core: AbuseReport domain type                          │
│    → push-worker: POST /v1/abuse/report endpoint            │
├─────────────────────────────────────────────────────────────┤
│ 3. Report evaluation layer                                  │
│    No eval / threshold / manual review                      │
│    → core: AbuseReportEvaluator port                        │
├─────────────────────────────────────────────────────────────┤
│ 4. Enforcement action layer                                 │
│    Hard-delete blob (TASK-111 mech), freeze identity,       │
│    warn user, forward к authorities                         │
│    → push-worker: R2 delete + KV mark identity frozen       │
├─────────────────────────────────────────────────────────────┤
│ 5. UX layer                                                 │
│    Report button UI, reported-user warning, group-visible   │
│    audit trail                                              │
│    → app/ Compose screens                                   │
├─────────────────────────────────────────────────────────────┤
│ 6. Adjacent policy                                          │
│    ToS, Privacy Policy, DMCA agent, retention policy для    │
│    report metadata, reporter privacy                        │
└─────────────────────────────────────────────────────────────┘
```

**Куда в код** (per rule 1 + rule 2):
- `core/`: `AbuseReport` domain type + `AbuseReportSubmitter` port + `AbuseReportEvaluator` port.
- `push-worker/`: `/v1/abuse/report` endpoint (per TASK-105 baseline), R2 delete mechanic (TASK-111), KV freeze marker.
- `app/`: Compose UI + navigation.
- Legal infrastructure — вне code, но inline TODO про exit ramps.

#### A.3 Главное для новичка

Три вещи которые надо усвоить прежде чем принимать decisions:

1. **Content moderation в E2E — принципиальное противоречие.** Сервер НЕ видит контент, но должен на что-то реагировать. Единственный workable pattern: **user-reported** (жертва или свидетель явно жмёт Report → передает конкретный blob-reference + metadata серверу с consent). Сервер сам сканировать НЕ МОЖЕТ (это ломает E2E, что было доказано backlash Apple CSAM 2021). Не путать: user-reported ≠ automated scanning.

2. **«Legal minimum» — очень разный по юрисдикциям.** Apple/Google Play — universal, применяется везде где мы дистрибутируем через их сторы. EU DSA — если shipping в EU и > threshold users. US DMCA — если shipping в US и есть copyrightable content. Мы должны выбрать target market **явно** и построить минимум оттуда. «Global» — самый узкий пересечение всех, самый дорогой.

3. **Abuse ≠ SOS ≠ App-lock.** Три соседние задачи легко перепутать:
   - **SOS** ([TASK-11](task-11%20-%20Contact-Photos-Family-Album-foundation.md)) — жертва зовёт на помощь (внутри family). Не жалоба, а alarm.
   - **Abuse report** (этот task) — жертва / свидетель сообщает о нарушении правил (внутри или снаружи family).
   - **Remote app-lock** ([TASK-103](task-103%20-%20Decision-Remote-app-lock-for-stolen-device.md)) — реакция на украденное устройство, не на content.

Разные UX, разные server actions, разные legal requirements. Не мержить.

#### A.4 Ключевые термины

- **CSAM** (Child Sexual Abuse Material) — специальный класс контента, для которого legal reporting mandatory во многих юрисдикциях. *Зачем нам*: если family album позволит upload фото, теоретически CSAM может оказаться на нашем R2 — нужен reporting flow к NCMEC.
- **Notice-and-takedown** — pattern где platform получает notice → в N часов принимает action. *Зачем нам*: EU DSA + US DMCA + Apple/Google Play — общий стандарт.
- **DMCA agent** — designated contact для US copyright complaints, ~$6/год registration с US Copyright Office. *Зачем нам*: если shipping в US, обязателен; отсутствие = personal liability для владельца.
- **NCMEC** (National Center for Missing & Exploited Children) — US clearinghouse для CSAM reports. *Зачем нам*: если находим CSAM (через user report), обязаны сообщить в ≤ 24 часов.
- **INHOPE** — EU equivalent NCMEC. *Зачем нам*: если targeting EU и accept UGC media.
- **Trust & Safety team** — команда рассматривающая reports. *Зачем нам*: **у нас её нет**. Значит либо automated response, либо owner делает review ручной, либо outsource — надо выбрать.
- **Reporter privacy** — anonymous vs identified reporter. *Зачем нам*: family context — reporter и reported часто знают друг друга; retribution risk высок, значит anonymous default.
- **In-band block/mute** — client-side action без server involvement. *Зачем нам*: minimum viable — пользователь может обойтись без server-mediated abuse response если может просто заблокировать peer'а локально.
- **Threshold policy** — сколько reports от distinct identities триггерит automated action. *Зачем нам*: если делаем automated hard-delete, надо thresh против single-reporter censorship attack.
- **Reporter consent + content sharing** — когда user жмёт «Report», клиент отправляет на сервер **decrypted копию контента** (тот, о котором жалоба, не весь чат). Это единственный legitimate способ дать серверу content view без ломания E2E. *Зачем нам*: понять что «E2E vs abuse response» разрешимо через explicit consent — WhatsApp именно так и делает.
- **Censorship attack** — злоумышленник массово жалуется на legitimate users чтобы триггерить automated action. *Зачем нам*: определяет как аккуратно проектировать evaluation layer.

#### A.5 Уточняющие вопросы

Тема — legal + product + technical. Организм требует **7 вопросов** (проверял можно ли сократить — нет, каждый закрывает отдельный architectural angle).

**Q1. Target-market юрисдикции.** Куда планируем shipнуть первую версию?
- (a) EU только (DSA + GDPR compliance).
- (b) US только (DMCA + state laws, e.g. California CCPA).
- (c) Россия (свои требования, реестры блокировок).
- (d) Global (пересечение всех — Apple/Google Play policies + минимум законодательства).
- (e) Что-то ещё?

*Почему спрашиваю*: legal minimum kardinal'но отличается. EU DSA требует complaint mechanism с response time. US требует DMCA agent + позвоночник для CSAM reporting. Россия требует cooperation с Роскомнадзором. Global — самый широкий, самый дорогой. Выбор определяет: (a) какие legal contacts нужны, (b) какие языки нужны для report UI, (c) какой response SLA обещать в T&C.

**Q2. Типы abuse — что защищаем в MVP.** Расставь по приоритетам (1 = most important):
- (a) **Elderly relationship abuse** — caregiver контролирует бабушку, ломает legitimate contacts, финансово эксплуатирует. Это RELATIONSHIP abuse, не content.
- (b) **Compromised device attack** — attacker paired via QR social engineering → шлёт inappropriate content жертве.
- (c) **Inappropriate content от legitimate member** — родственник шлёт adult или disturbing content в family album.
- (d) **CSAM** — theoretical, family album принял фото которое criminal.
- (e) **Copyright infringement** — родственник загрузил copyrighted material.
- (f) **Что-то ещё** что важно?

*Почему спрашиваю*: каждый тип требует разного response mechanism:
- (a) — не content moderation вообще; UX «выход из-под контроля» + возможно external hotline интеграция.
- (b) — auth/pairing flow issue + freeze identity mechanism (уже есть через TASK-106 signup gate).
- (c) — user-report на конкретный blob + TASK-111 hard-delete.
- (d) — mandatory reporting flow + legal escalation (NCMEC / INHOPE).
- (e) — DMCA notice-and-takedown (US required).
Приоритизация решает где вкладываться. Мой guess: (a) → (c) → (b) → (d) → (e). Family launcher — не Twitter/Reddit, не platform для strangers.

**Q3. Reporter model.** Кто может пожаловаться:
- (a) **In-band только**: member внутри family group. Простой server surface, cheap.
- (b) **In-band + external URL**: кто-то видит наш app где-то ещё → жалуется через website form / email.
- (c) **+ automated content scan** — **отклоняю сразу**: ломает E2E, retracted Apple pattern, backlash был токсичный. Не рекомендую даже рассматривать.

*Почему спрашиваю*: (a) закрывает 95% cases для family app'а. (b) добавляет legal exposure external channel + возможно website + DMCA agent published contact. Разница на месяцы infrastructure work.

**Q4. Server action на report.** Что делает сервер получив report:
- (a) **Ничего automated** — просто регистрирует в audit log, owner ручной review → decision. Fine для < 1000 users, не масштабируется.
- (b) **Automated hard-delete blob** (TASK-111 mechanism) сразу от одного report. Риск: false-flagging by malicious reporters censore innocent users (censorship attack).
- (c) **Automated freeze reported identity_id** — block cloud upgrade, block pairing, block KeyPackage claim. LOCAL app продолжает работать (per TASK-106 device-self-sufficiency). Sanctions без content decision.
- (d) **Threshold-based combo**: single report → warn + audit; N reports от distinct identities → hard action.

*Почему спрашиваю*: (a) simplest MVP но не future-proof. (b) даёт censorship attack surface. (c) sanctions без content evaluation. (d) требует threshold policy + audit trail — самое robust но дороже.

**Q5. Legal infrastructure — что у нас сейчас есть.** Нужны для legal minimum:
- **DMCA agent** (US) — designated contact, публичный, ~$6/год US Copyright Office registration.
- **GDPR Data Protection Officer** (EU) — если > threshold users в EU.
- **NCMEC access** (US) — для CSAM cases.
- **Terms of Service / EULA** — есть?
- **Privacy Policy** — есть?
- **Support email** — есть публичный?

Проверь каждый пункт: **есть уже / не нужно / надо создать**.

*Почему спрашиваю*: часть abuse response — это НЕ code, это designated infrastructure. Без DMCA agent — US shipping невозможен legally. Без Privacy Policy — Google Play reject. Если ничего нет — это ~1 месяц work до store submission независимо от кода.

**Q6. Preset differences family vs clinic.** Наш abuse response:
- (a) **Family-only** hardcoded в MVP. Simplest, но rule 9 violation (shareability-readiness требует preset params для non-identity config). Refactor Phase-3+ при clinic adoption.
- (b) **Preset-параметризован с day 1** через preset fields (`abuseThresholdCount`, `reporterAnonymity`, `escalationContacts`). Дороже сейчас (~1 день доп-дизайна), дешевле Phase-3+.
- (c) **Family + clinic сразу в MVP** — над-инженерия, скорее всего не нужно если clinic не в roadmap Phase-2.

Мой guess: (b) — preset params определяем, но family-default values встраиваем hardcode-ish (реальный preset switcher — Phase-3+).

*Почему спрашиваю*: rule 9 shareability-readiness. Family default — informal, in-band; clinic — formal, mandatory external reporting, audit trail. Разные thresholds. Разные contacts. Если сейчас hardcod'ить family — clinic adoption = refactor.

**Q7. Наш exposure к CSAM specifically.** Если через нашу R2 идёт CSAM (пусть теоретически), какова responsibility?
- (a) **Low exposure**: R2 buckets private, только family members через MLS-key могут читать → мы просто hosting encrypted blobs которые для third party opaque. Достаточно user-report + hard-delete.
- (b) **Medium exposure**: наш ToS должен явно запретить CSAM + response mechanism ≤ 24h + NCMEC registration.
- (c) **High exposure**: full CSAM prevention flow (client-side hash matching against known-bad list, но это опять близко к Apple 2021 pattern).

Что мы **точно НЕ делаем**: automated content scanning (клятвенно, публично). Что делаем — открытый вопрос.

*Почему спрашиваю*: критично для определения scope. Если ответ «low» → минимальный effort. Если «medium» → NCMEC + policy documents. Если «high» → пересматриваем весь E2E paradigm (не рекомендую, но надо озвучить).

---

Останавливаюсь. Жду ответы владельца.

<!-- SECTION:DISCUSSION:END -->

## Implementation Plan
<!-- SECTION:PLAN:BEGIN -->
_(pending — заполняется в /speckit.plan после Decision block frozen)_
<!-- SECTION:PLAN:END -->

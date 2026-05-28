# 13. Risks & Critique — что может убить продукт

> **Status**: 🟡 reviewed + adjustments applied · **Created**: 2026-05-28 · **Reviewed via mentor walkthrough**: 2026-05-28 evening.
>
> **Принцип**: этот документ **не yes-man'ит**. Я ищу слабые места, конкурентов, killer-сценарии. Это **обязательное чтение** перед стартом F-1 — лучше узнать риски сейчас, чем после 6 месяцев работы.

---

## 🟢 Resolution log (2026-05-28 evening — 2 passes)

После mentor walkthrough — applied следующие adjustments к roadmap (Pass 1 + Pass 2 = honest 2nd-pass critique addressing 26 dev-specific gaps):

| Topic | Decision | Where |
|---|---|---|
| Wizard 9-step friction | **Reduced to 5 critical steps + autohints** для остального. Frequency cap + dismissal memory. | S-1 roadmap section |
| Multi-admin promotion safeguard | **N/2+1 signatures required** если есть co-admin'ы. Singleton admin — alone. Timeout 30 дней. | F-1 roadmap section |
| App update during SOS | **WorkManager-based deferral** 30 min after SOS triggered. Investigate Play Store API; fallback warning. Critical CVE override. | S-4 roadmap section |
| Audit log court risk | **Two-tier architecture**: Tier 1 public metadata (for merge UI) + Tier 2 encrypted payload (actor-only). | F-1 + S-8 roadmap sections |
| Security testing strategy | **Blogger/influencer outreach** в promotion phase (user choice) + **4 free mitigations** (OWASP MASVS, friend crypto review, property-based tests, soft launch gate). | Cross-cutting Security Mitigations section in roadmap |
| OEM matrix testing | **Mandatory section в каждой S-spec'е** (Pixel + Samsung + Xiaomi minimum). | spec-template + Cross-cutting roadmap |
| Soft launch gate | **Обязательный gate** между MVP code-complete и public release: 5-10 друзей × 2 weeks. | Release process section in roadmap |
| Messenger Adult preset | **Universal Preset extends на messenger** — Elderly + Adult + future Caregiver presets. | V-2 roadmap section |
| Hostile takeover | **Out of scope** (user: «пусть сами разбираются»). Family conflicts — не наша компетенция. | Documented в this doc, no roadmap change |
| Pen test / bug bounty | **No formal test** (user choice). Replaced 4 free mitigations выше. | Cross-cutting Security section |
| Pricing decisions | **Out of dev scope** (user: «только вопросы разработки»). | Frozen в monetization-legal doc |
| Privacy Policy text | **Out of dev scope** (legal task). | Pre-release task, not spec'а |
| Multi-admin family conflict mediation | **Out of scope** (user: «пусть сами разбираются»). | Not our product territory |
| Court-ordered communication market | **Not our positioning** (OurFamilyWizard territory). | Privacy Policy disclaimer |
| Acceptable residual risks | WhatsApp Communities competition, ROLE_HOME deprecation, first-data-breach, single-point-failure (Cloudflare+Firebase). | Acceptable per vision, monitor only |

### Pass 2 adjustments (26-item dev critique 2026-05-28 evening, second mentor walkthrough)

| Topic | Decision | Where |
|---|---|---|
| Server arbitration NOT atomic (race conditions) | Firestore Transactions для membership ops в MVP + own server via SRV-CMD-002 long-term | F-1 roadmap, server-roadmap SRV-CMD-002 |
| POST_NOTIFICATIONS deny → SOS silent fail | **SOS primary mechanism reframed**: 112/911 + GPS via system phone API (works even when push fails). Admin push — secondary awareness, not safety net. | S-4 roadmap section reframed |
| Invite link forwarded / leaked | **Two-factor accept**: claim → push admin → explicit confirmation → membership granted | S-2 + S-7 roadmap |
| Producer mistake content categorization | **Content recall before consumption**: producer может отозвать blob если recipients не download'нули. Inherent limit если уже downloaded — accept. | S-5 roadmap |
| Library SPOF (libsodium KMP) | EnvelopeAdapter port (rule 2 ACL) — fallback to BouncyCastle если libsodium breaks | F-1 roadmap |
| Compose Multiplatform iOS stability | Accept risk — rewrite to SwiftUI в V-1 если CMP не production-ready | V-1 notes (accepted as risk) |
| Firestore Security Rules complexity | Comprehensive Rules unit tests + own server via SRV-SEC-005 long-term | F-1 testing + server-roadmap SRV-SEC-005 |
| Cloudflare Worker CPU time limit | Profile в F-1 + split async if needed + own server via SRV-INFRA-003 | F-1 + server-roadmap SRV-INFRA-003 |
| R8 minification (ARCH-006 PLAY-STORE-BLOCKER) | Mandatory before MVP release | Existing backlog, escalated |
| No staging environment | Setup до S-6 release (OPS-004) + propose `checklist-dev-experience` skill | server-roadmap SRV-DEV-001 + new skill in cross-cutting |
| Storage health monitoring on Managed | Cache size в health snapshot + admin-driven cache cleanup actions (clear all / older than month / 50% LRU / auto-clean) | S-5 + S-8 roadmap |
| Key rotation research | **Research done**: Signal Double Ratchet overkill для at-rest. Matrix Megolm closer. **Recommendation**: NO automatic rotation в MVP, on-demand manual в FUTURE-SPEC-010 post-MVP. Account deletion (S-6) — de facto rotation для MVP. | server-roadmap SRV-CRYPTO-002, roadmap §Phase 4 |
| Caregiver TTL expired UX | Deferred (simple future solution, не blocker) | Future work |

**Net effect after 2 passes**: roadmap honest и comprehensive. F-1 ready to start. Key rotation deferred с documented reasoning.

---

---

## 1. Что было исследовано

Этот документ — результат структурного аудита наших решений против внешнего рынка (web research 2026-05-28). Покрывает:

- **Прямые конкуренты**: GrandPad, Jitterbug/Lively, BIG Launcher, Wiser, Necta, OurFamilyWizard, AppClose, Cozi, Life360, Microsoft Family Safety, Apple Family Sharing, Google Family Link.
- **Indirect competition**: WhatsApp Communities (3.3B MAU), Signal, Telegram — могут поглотить наш value prop.
- **Market metrics**: subscription fatigue 41%, churn benchmarks, day-1 activation impact.
- **Regulatory landscape**: EU AI Act, GDPR enforcement actions, Russian 152-ФЗ pressure.
- **Failure patterns** в senior care market.

Source links — в [§9](#9-research-sources).

---

## 2. Killer-Level Competitive Risks 🔴

### 2.1 WhatsApp Communities — direct threat

**Что они уже умеют (2026)**: «WhatsApp Communities allow organizations to create a parent level community that houses multiple related groups under one umbrella». То есть **семейные группы с nested структурой + admin tools + announcement channel + умные replies + AI-powered features**.

**3.3 миллиарда MAU**. Vs наши 0. **Network effect лютый**.

**Где они уже опередили нас**:
- ✅ Family groups с admin'ами (как наша Family Group).
- ✅ Group calls + video calls (наша S-2 + S-3 — handoff).
- ✅ Encrypted media sharing (~ наша S-5).
- ✅ AI features (smart reply, translation) — а у нас «AI-ready без AI-built».
- ✅ Cross-platform (Android + iOS + Web) — а у нас iOS post-MVP v2.

**Чего у них НЕТ (наш потенциальный moat)**:
- ❌ Launcher mode (HOME replacement) — WhatsApp не может стать launcher'ом.
- ❌ Accessibility-first UX для elderly (но они быстро добавят, если рынок покажет ценность).
- ❌ Caregiver / role-based access (но это маленький B2B сегмент, который WhatsApp не интересен).
- ❌ Wizard + preset architecture (минорная техническая разница).

**Честная оценка**: наш единственный устойчивый moat — **launcher mode + accessibility-first + caregiver integration**. Всё остальное WhatsApp может clone за квартал.

**Что делать**:
- **Не пытаться конкурировать с WhatsApp как messenger** (D-23 уже это решил — handoff, не embedded).
- **Усилить launcher + accessibility differentiator** — это reasoning сильно для S-1.
- **Caregiver-integration как differentiator** — серьёзно, не post-MVP feature. Это **специфический value WhatsApp не закроет**.

### 2.2 Signal-у проще, чем нам

«Signal offers the same end-to-end encryption as WhatsApp but collects virtually no metadata. It is run by a non-profit foundation with no advertising or data monetization. However, since it has a smaller user base, you'll probably end up trying to convince friends and family to install another messaging app.»

**Это про нас.** Мы заставляем семью **переезжать** с WhatsApp на наш продукт. Каждый член семьи должен:
1. Установить наше приложение.
2. Зарегистрироваться через Google.
3. Войти в Family Group.

Это **massive friction**. Особенно для admin'ов 50+. Particularly для пожилых, кто и так с WhatsApp еле справляется.

**Что делать**:
- **Не требовать установки нашего приложения у каждого участника**. Family Group должна работать **поверх** WhatsApp/Telegram (handoff model в S-3), не **вместо**.
- **Caregiver invite link** должен работать в любом канале без обязательной install (через web-page preview before install).
- **Признать**: мы будем **один из приложений**, не **главный**. Это меняет positioning.

### 2.3 Google Family Link — что если они pivot'нутся

Google уже имеет:
- Family Link (parent-child, но **architectural patterns такие же**).
- Family Sharing infrastructure.
- Subscription billing (Play family pack).
- Google Account ecosystem.
- Health Connect API.
- Gemini AI integration.

**Если они решат добавить elderly care** — мы проиграны. У них **уже** есть всё, что мы строим.

**Сигнал**: пока Google не показал интерес. Senior care market $34B к 2035 (~$8.7B сейчас) — это **малый рынок** для Google. Но как только превысит $50B, они посмотрят.

**Что делать**:
- **Принять как acceptable risk**. Все startups жуют этот риск.
- **Vertical integrations с клиниками / caregivers** — это «слишком B2B» для Google, они не пойдут.
- **Local market expertise (RU + EU local regulations)** — barrier для US-based Google.

### 2.4 Apple's MDM advantages

Apple's Guided Access + Supervised + MDM уже даёт большую часть наших каркасов. Если Apple добавит native senior care preset — у iOS-аудитории мы не нужны.

**Что делать**:
- iOS post-MVP v2 — **acceptable trade-off** при условии, что Apple ещё не сделал own версию.
- Monitor Apple WWDC 2027 announcements.

---

## 3. Subscription / Pricing / Economic Risks 🟡

### 3.1 Subscription fatigue

**41% consumers report subscription fatigue.** **44% cancel within 90 days**. Top-performing apps below 3% monthly churn. Average 5.3%.

Family monthly subscription (D-11) — это **ещё одна подписка** в портфеле семьи. Конкуренты:
- Netflix / streaming
- Spotify
- iCloud / Google One
- Insurance / utilities
- Caregiver service (если есть)

**Risk**: при economic downturn семья cancels наименее «critical» — а наш продукт может попасть туда.

### 3.2 Pricing не определён

GrandPad: $40/month. Lively: $49.99/month. **Это US pricing**.

В РФ pensioners' families не платят $40/month. Реалистично:
- US: $5-15/month (50%+ скидка от GrandPad).
- EU: €4-12/month.
- RU: ₽300-800/month (~$3-8).
- CN/India: ещё ниже.

**Без региональной адаптации pricing — нет global product**.

### 3.3 Free competitors

- **WhatsApp** — free.
- **AppClose** (co-parenting) — free.
- **Family Link** (Google) — free.
- **Cozi** (family organizer) — free tier sufficient.

Наш value prop должен быть **enough valuable**, чтобы пользователь заплатил. **Сейчас непонятно, что именно**.

### 3.4 What we should resolve

**До MVP-релиза нужно определить**:
1. Regional pricing (US / EU / RU / minimum).
2. Free tier — есть или нет (D-11 не уточнил).
3. Trial period (D-11 deferred — но это **критично** для day-1 activation).
4. Family pack tier — N людей за фиксированную цену (D-29 deferred).

**Текущий статус (out of dev scope)** недопустим перед релизом. Нужна mini-spec на pricing **до** S-6 (account deletion включает subscription cancellation logic).

---

## 4. Legal / Regulatory Risks 🟡

### 4.1 EU AI Act (effective 2026)

«The EU AI Act's risk classification framework for biometric and health-related artificial intelligence is reshaping development roadmaps for European market entry, with several vendors delaying EU launches pending legal clarity on high-risk system obligations.»

**Применимо к нам?**
- **Если** мы используем Gemini Nano для **image description** контактов — borderline biometric.
- **Если** SOS триггеры на **поведении** (например, бабушка не двигается → alert) — это **health-related AI**.

**High-risk classification** = compliance overhead в EU launch.

**Что делать**:
- Image description (V-5 / L-x) — карантинить EU launch.
- **NO** behavior-based health AI (vision рекомендует это explicitly — «не medical platform»).
- ADR-008 (AI affordance) должен упомянуть EU AI Act exposure.

### 4.2 Court-ordered communication territory

**OurFamilyWizard** существует для **court-ordered communication** в high-conflict divorce cases. Используется **for evidence**.

**Risk**: наша Family Group + audit log → может стать evidence в семейных судебных делах:
- Custody dispute over senior parent.
- Inheritance dispute.
- Elder abuse cases.

Audit log — **double-edged**: показывает, что caregiver видел, но **наш** субъект privacy запроса.

**Что делать**:
- **Privacy Policy explicitly** говорит: «Family Group activity logs могут быть subpoena'ed».
- **Не претендовать на court-grade evidentiary value** — мы не OurFamilyWizard.
- **Mitigate retention** — audit log по умолчанию 90 дней (не indefinitely).

### 4.3 Russian 152-ФЗ data localization

Если operate в Russia — server в Russia mandatory. Firebase в Russian region or Yandex Cloud.

**Текущая позиция**: monetization+legal frozen, RU launch — post-MVP.

**Risk**: если decide expand в Russia rapidly, придётся mid-flight migrate data residency. **Painful**.

### 4.4 Healthcare regulation if classified as "medical device"

Если регулятор посчитает наш phone health monitoring + SOS + caregiver integration **«medical device»**:
- FDA approval (US): 1-3 years.
- EU MDR: similar.
- Catastrophic for timeline.

**Vision filter** говорит «не medical platform», но **boundary fuzzy**:
- Health Device Monitoring (V-5) — wearable integration. **Это** может triggernuti medical classification.
- SOS escalation flow — на грани emergency medical response.

**Что делать**:
- **Stay on family-support side** of boundary. SOS = «notify family», не «call ambulance».
- **Wearable** (V-5) — только metric display, **не diagnostic**.
- **Legal review** перед V-5 (medical lawyer consult).

---

## 5. Use Cases мы упустили 🟡

### 5.1 Court-ordered scenarios (S-901..S-904)

| ID | Сценарий | Severity |
|---|---|---|
| S-901 | Family Group activity logs subpoena'ed for elder abuse case | High |
| S-902 | Custody dispute over senior parent — multi-admin family conflict | Medium |
| S-903 | Inheritance dispute — admin'ы fight over Managed device control | Medium |
| S-904 | GDPR data export ordered by court | Low (covered by S-6) |

**Что делать**: добавить в [02-actors-and-lifecycle.md L6 End-of-life](use-cases/02-actors-and-lifecycle.md). Не отдельная спека, но Privacy Policy section.

### 5.2 Trust abuse сценарии (S-905..S-910)

| ID | Сценарий | Severity |
|---|---|---|
| S-905 | Hired caregiver screenshots Family Album for blackmail | **Critical** |
| S-906 | Hostile co-admin promotes self → ousts original admin | High |
| S-907 | Family member shares password / device with non-family | Medium |
| S-908 | Admin's Google account hacked → attacker controls Family Group | High |
| S-909 | Managed's device given to non-family (gift, donation) | Low |
| S-910 | Family member uses Family Group to monitor (spousal stalking) | **Critical** |

**Most serious — S-905 и S-910**. Это **brand-damaging incidents** waiting to happen.

**Что делать**:
- **S-905 mitigation**: caregiver tier **по умолчанию НЕ** имеет access к Family Album. Только care content. **Это уже в S-7 design**, но **проверить, что envelope encryption truly enforce'ит**.
- **S-910 mitigation**: limit «health monitoring» visibility за рамками admin. Caregiver не должен видеть detailed activity logs — только critical alerts.
- **S-908 mitigation**: 2FA на admin Google account (recommend в S-2 wizard).
- **S-906 mitigation**: promotion операция требует **multi-admin approval** (если есть co-admin'ы). Иначе easy hostile takeover.
- **S-907 mitigation**: device-binding для admin sessions (anti-abuse from ADR-002).

### 5.3 OEM / hardware quirks (S-911..S-915)

| ID | Сценарий | Severity |
|---|---|---|
| S-911 | Samsung Smart Manager kills background → SOS push delayed 15 min | High |
| S-911 | Xiaomi MIUI aggressive kill → polling fallback не работает | High |
| S-913 | Huawei (non-GMS) — no FCM, no Play Vitals | High (post-MVP) |
| S-914 | Budget Android device $80, 2GB RAM, no Play Services | Medium |
| S-915 | App update during SOS triggered → fail | **Critical** |

**S-915 — critical**. Если SOS активирован во время app update — может не доехать. **Show-stopper** для safety net claims.

**Что делать**:
- **App update strategy**: deferral if SOS recently triggered (last 30 min).
- **OEM matrix testing** — должен быть в каждой S-spec'е, не deferred (backlog SPEC010-DEV-001).
- **Performance gates тестируются на multiple OEMs**, не только Pixel.

### 5.4 First-use friction (S-916..S-919)

| ID | Сценарий | Severity |
|---|---|---|
| S-916 | Admin drops off в wizard step 4 of 9 → setup incomplete | **High** (44% cancel within 90d) |
| S-917 | Managed gets phone before admin setup → empty wizard loop | High |
| S-918 | Wizard step requires permission → user denies → wizard blocked | High |
| S-919 | Admin Google Sign-In fails (no Google account) → blocked | Medium |

«**The single most predictive metric for day-30 retention is day-1 completion rate of a meaningful first action**, with apps that nail first-session activation retaining at 2-3x rate.»

**S-916 critical** — 9-step wizard вероятно слишком длинный.

**Что делать**:
- **Wizard MVP — 5 steps max**, остальное **в Settings post-onboarding**.
- **Skippable everywhere**, кроме truly mandatory (ROLE_HOME, POST_NOTIFICATIONS).
- **«Quick start» mode**: smart defaults, выйти на home screen за 60 seconds. Power-user mode — для тех, кто хочет настроить детально.

### 5.5 Bandwidth / data-plan scenarios (S-920..S-922)

| ID | Сценарий | Severity |
|---|---|---|
| S-920 | Managed на ограниченном тарифе — photo blob downloads eat data | Medium |
| S-921 | Cellular-only Managed устройство, нет WiFi at home | Medium |
| S-922 | Roaming с photos auto-download — billing shock | Medium |

**Что делать**:
- **Photo download policy**: WiFi-only by default, manual cellular trigger.
- **Settings explicit**: «Download photos on cellular: never / WiFi only / always».

### 5.6 Suicide watch / emergency mental health (S-923)

**S-923 — Critical, sensitive**: пожилой sends concerning messages, declining health pattern, depression signals.

**Vision filter test**: усиливает «семейную связь» — да. Но **обязанность реагировать** — это **legal liability**.

**Что делать**:
- **Лучшая практика**: не делать automated mental health detection (AI Act, liability).
- **Family notification** только на explicit user-action (push в семью — но не auto-classification).
- **Privacy Policy disclaims** — мы не replacement для professional crisis services.

---

## 6. Existential Risks 🔴

### 6.1 Single point of failure: Cloudflare + Firebase

Наш entire infrastructure — Cloudflare Worker (push relay + MCP future) + Firebase (auth + Firestore + FCM).

**Risks**:
- **Cloudflare pricing changes** (workers.dev → paid?).
- **Firebase Spark limits** — first viral month — total downtime.
- **Both at once** outage — ну, у нас нет fallback.

**Server-roadmap** addresses migration path, но **не immediate fallback**.

**Что делать**:
- **Real disaster recovery test** — что происходит, если Firestore down 1 hour? 24 hour?
- **Backup connectivity** для critical alerts (SOS): if cloud down, attempt direct SMS via existing GSM.

### 6.2 First major data breach

Senior audience **first-class privacy-sensitive**. If first incident — «фото бабушки утекли», или «admin'ы могут видеть всё что угодно» — мы **готовы**?

«Don't Kill My App» communities are unforgiving. Privacy breach in senior care = **death of trust forever**.

**Mitigation готов?**:
- ✅ E2E encryption envelope.
- ✅ Server не имеет ключей.
- ⚠️ But: client-side decrypted data может leak (memory dumps, screenshots).
- ⚠️ Audit log не protect against malicious caregiver.

**Что делать**:
- **Bug bounty program** перед launch.
- **Pen test** до publish (отдельная задача).
- **Incident response plan** documented before first user.

### 6.3 Google ROLE_HOME deprecation

Если Google решит, что launcher replacement — privacy concern, и **deprecate ROLE_HOME** для third-party — наш product **dies overnight**.

Это **happened** уже:
- Background location restrictions Android 10+.
- App standby buckets.
- Foreground service types Android 14+.

**Risk level**: medium. ROLE_HOME — legit API, Google нет причин deprecating. Но possible.

**Mitigation**: zero. Это external dependency.

### 6.4 Multi-admin model abused in family conflicts

**Scenario**: семья ссорится. Co-admin'ы взаимно kick'ают друг друга. Бабушка в центре — её device становится **поле боя**.

«**One spouse uses Family Group to monitor or harass other spouse**» — это **известный pattern** в abusive relationships.

**OurFamilyWizard** оптимизирована для court-ordered communication в high-conflict cases. **Мы — нет**.

**Что делать**:
- **Admin promotion / demotion** должна требовать **co-admin approval** (если есть N admin'ов, нужны N/2 + 1).
- **Documented disputes** не наш use case — refer к OurFamilyWizard.
- **Privacy Policy** говорит, что наш app не для court-ordered communication.

### 6.5 Managed user reluctance / uninstall

**Хорошо забываемый scenario**: бабушка **не любит** новый launcher. Хочет старое.

- Если она tech-savvy enough → может uninstall (если permission allowed)?
- Если у нас ROLE_HOME — uninstall возможен через Settings.
- Family invested, бабушка отказалась — friction.

**Что делать**:
- **Soft persuasion mode** в setup: «попробуйте 7 дней — потом легко вернуть всё назад».
- **Easy uninstall flow** documented. Не lock'аем пользователя.
- **Recovery for admin investment** — если бабушка uninstalled, admin'у не нужно reconfigure весь setup при reinstall.

---

## 7. Что у нас слабо vs research показал

### 7.1 Network effect не задействован

Family plans **увеличивают retention на 52%**. У нас family-by-default, это **на нашей стороне**. Но без **viral mechanism** (admin invites родственников) — стагнация.

**Vision missing**: **invite incentive**. Например: «invite second admin → free month». Это **growth lever**, который мы не запланировали.

### 7.2 Community features снижают churn на 23%

У нас **нет community features**:
- Forum / Q&A.
- Tips & tricks sharing.
- Templates marketplace (post-MVP только).

**Vision should add**: **community surface** post-MVP.

### 7.3 Day-1 first-action не оптимизирован

«**The single most predictive metric for day-30 retention is day-1 completion rate of a meaningful first action**.»

Что наша «meaningful first action»?
- Admin: создать Family Group + invite Managed? Это **2-step**.
- Managed: complete wizard? Это **9 steps**.

**Слишком длинно**. We're losing 44% within 90 days for sure.

**Что делать**:
- **Define meaningful first action explicitly** — например, **«первый успешный звонок внуку»**. Это нужно сделать **в первый день**.
- Wizard optimize **на это** — все остальное secondary.

### 7.4 Behavior-based messaging снижает churn на 17%

Behavioral-trigger messaging (в-app, не push) reduce churn. Например:
- «Вы давно не настраивали Family Group — есть новый родственник?»
- «Managed device offline 3 дня — всё нормально?»

**Vision missing**: behavioral retention messaging plan.

**Что делать**:
- Add **in-app behavioral nudges** в S-8 (admin app) — non-intrusive, contextual.
- Comply with rule 10 (notification minimization) — in-app, не push.

---

## 8. Что предлагается изменить в roadmap

На основе research, **некоторые decisions** должны быть пересмотрены **до старта F-1**:

### 8.1 Critical adjustments

1. **Wizard scope reduction**. S-1 9-step → 5-step minimum. Остальное в Settings. **Это меняет S-1 spec.**
2. **Multi-admin promotion requires approval** (N/2+1 если есть co-admin'ы). **Это добавляется к F-1 или S-2.**
3. **App update deferral если SOS triggered недавно**. **Добавляется к S-4 + общая update strategy.**
4. **Photo download policy default WiFi-only**. **Добавляется к S-5.**
5. **2FA recommendation в admin Sign-In flow** (S-2). **Минор.**
6. **Caregiver tier — verify envelope filtering crypto-уровне** для S-905 mitigation. **Уже в design, но pen-test обязательно.**
7. **Bug bounty + pen test** — pre-release, отдельный budget item.
8. **Incident response plan** documented before user-facing release.

### 8.2 Vision additions (parking lot)

| PARK | Item |
|---|---|
| PARK-002 | Invite incentive mechanism (growth lever) |
| PARK-003 | Community features (post-MVP — forum, tips, templates) |
| PARK-004 | Behavioral retention messaging plan |
| PARK-005 | Disaster recovery test plan (Cloudflare / Firebase outage simulation) |

### 8.3 Pricing decision must be unfrozen earlier

D-11 + D-29 deferred subscription details to monetization implementation spec. But **S-6 (account deletion) уже включает subscription cancellation**. Невозможно полноценно реализовать S-6 без pricing model.

**Action**: написать mini-spec на pricing **до старта S-6**. Или embedded в S-6.

### 8.4 Legal / regulatory homework

- **EU AI Act assessment** (Gemini Nano features) — pre-V-5 / pre-AI-implementation spec.
- **Healthcare device classification consult** — pre-V-5 wearable integration.
- **Privacy Policy text** — pre-MVP-release.
- **Russian 152-ФЗ data localization decision** — pre-RU launch.

---

## 9. Open questions для user

Вопросы, на которые нужны ответы перед началом F-1 (или в первые недели):

| # | Вопрос | Critical? |
|---|---|---|
| Q1 | Какое **conservative regional pricing**? US/EU/RU minimum tiers — нужны конкретные цифры. | Yes (blocks S-6) |
| Q2 | **Trial period или free tier** — есть? Сколько? | Yes |
| Q3 | **Wizard MVP — 5 steps или 9**? Какие 5 critical? | Yes (blocks S-1) |
| Q4 | **Bug bounty / pen test budget** — кто платит, когда? | Yes (pre-release) |
| Q5 | **Incident response plan** — кто on-call? Когда писать? | Yes (pre-release) |
| Q6 | **Multi-admin promotion approval** — N/2+1 rule? Или single admin может промоут? | Yes (blocks F-1) |
| Q7 | **Privacy Policy** — Termly/iubenda template OK? Или legal consult? | Yes (pre-release) |
| Q8 | **OEM matrix testing** — какие 3-5 devices обязательно? | Yes (blocks first release) |

---

## 10. Honest assessment

### Что у нас хорошо

- ✅ Architecture solid (Family Group + envelope + Capability Registry).
- ✅ Privacy positioning strong (E2E demonstrable).
- ✅ Vision filter (family care reinforcement) — disciplinary tool.
- ✅ Accessibility-first — это unique vs WhatsApp/Signal.
- ✅ Caregiver integration — niche moat.

### Что у нас слабо

- ⚠️ **No competitor moat от WhatsApp** beyond launcher mode + accessibility.
- ⚠️ **Pricing unclear** — недопустимо для production.
- ⚠️ **Wizard слишком длинный** для day-1 activation.
- ⚠️ **Multi-admin без safeguards** для family conflict cases.
- ⚠️ **No growth / retention strategy** beyond «family invites family».
- ⚠️ **Legal compliance** — много открытых вопросов (EU AI Act, healthcare classification, RU data residency).
- ⚠️ **Critical S-905 risk** (caregiver abuse) — design correct, но pen-test обязательно.

### Что у нас существенно risky

- 🔴 **WhatsApp Communities** — direct competition, 3.3B network effect.
- 🔴 **Google Family Link pivot** — низкая вероятность но fatal.
- 🔴 **ROLE_HOME deprecation** — нулевой контроль.
- 🔴 **First data breach** — senior trust irrecoverable.
- 🔴 **App update during SOS** — show-stopper safety claim.

### Recommendation

**Не запускать F-1 без**:
1. Решений по Q1-Q8 выше.
2. **Wizard scope reduction** (S-1 5-step).
3. **Multi-admin promotion safeguard** (F-1 update).
4. **Bug bounty + pen test plan**.

Время на это: ~1-2 недели discussion (если параллельно с другой работой). Эти решения **меньше** чем 6 месяцев Phase 1+2, но **критичнее**.

---

## 9. Research Sources

### Senior care market & failures
- [Elderly Care Apps Market 2026 — MarkWide Research](https://markwideresearch.com/elderly-care-apps-market)
- [Senior Care App Development Guide 2026 — Specode.ai](https://www.specode.ai/blog/elderly-care-app-development)
- [Caregiver App Market — Verified Market Research](https://www.verifiedmarketresearch.com/product/caregiver-app-market/)

### Subscription / retention benchmarks
- [Subscription Statistics 2026 — Marketing LTB](https://marketingltb.com/blog/statistics/subscription-statistics/)
- [Mobile App Retention Benchmarks 2026](https://growth-onomics.com/mobile-app-retention-benchmarks-by-industry-2026/)
- [App Retention Benchmarks 2026 — Enable3](https://enable3.io/blog/app-retention-benchmarks-2025)

### Competitors
- [GrandPad Review & Pricing — SeniorLiving.org](https://www.seniorliving.org/cell-phone/consumer-cellular/grandpad/)
- [Jitterbug / Lively Reviews — The Senior List](https://www.theseniorlist.com/cell-phones/jitterbug/reviews/)
- [OurFamilyWizard — Court-Approved Co-Parenting](https://www.ourfamilywizard.com/practitioners/courts)
- [AppClose — Court-Ordered Co-Parenting App](https://appclose.com/)

### Messaging dominance
- [Top WhatsApp 2026 Features — Odysense](https://odysense.com/blog/whatsapp-new-features/)
- [WhatsApp vs Signal Privacy Compared 2026 — LeapXpert](https://www.leapxpert.com/whatsapp-vs-signal-privacy-features-compared/)
- [Most Popular Messaging Apps World — MessengerBot](https://messengerbot.app/most-popular-messaging-apps-in-the-world-which-app-is-no-1-what-americans-use-imessage-vs-whatsapp-and-top-apps-by-country/)

### Regulatory
- [EU AI Act risk classification framework](https://artificialintelligenceact.eu/) — official.

---

**Конец критики.** Если что-то здесь оспорить или подтвердить — обсуждаем. Это **не diss** на vision, это **due diligence** перед инвестицией 6 месяцев работы.

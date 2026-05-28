# 10. Monetization, Distribution & Legal — деньги, каналы, compliance

> ⚠️ **🔵 FROZEN 2026-05-27** — этот документ **вынесен из dev roadmap** по решению user'а («Всё, что не касается разработки, выкини из роадмап, планов, видения продукта»).
>
> **Единственное исключение, оставшееся в scope**: **anti-tampering / защита от взлома** (M-009 в этом документе + ADR-002). Это **dev concern** и сохраняется.
>
> Все остальные секции (subscription model, family pack, distribution channels, GDPR/152-ФЗ/CCPA compliance, store policy, etc.) — **отложены**. Когда понадобится — поднимем обратно. Удалять не будем — это reference на будущее.
>
> Vision (01-vision) теперь определяет продукт как **Family Care Ecosystem**, monetization вопросы — отдельный проект.

---

> **Status**: 🔵 FROZEN (кроме anti-tampering) · **Created**: 2026-05-27 · **Frozen**: 2026-05-27
> **Зачем читать (для контекста)**: эти три домена **полностью пустые** в спеках 001-012, но **критичны для релиза**. Без monetization — нечем платить за серверы и развитие. Без distribution — никто нас не найдёт. Без legal compliance — Play Store нас заблокирует (LEGAL-001 PLAY-STORE-BLOCKER).
> **Что остаётся актуальным сейчас**: только §M-009 (Anti-abuse / anti-tampering) — это dev concern и стоит ADR-002.
> **Источник**: `user-journeys-draft.md` §7.9 + §7.10 + §7.11 + ADR-002 + ADR-003 + `docs/compliance/`.

---

## Что это за документ (просто)

Эти три темы — **бизнес и юридическая** сторона продукта. Они скучные, неприятные, но **обязательные**:

- **Monetization** — кто платит, за что, сколько, как. Если не зафиксировать рано — продукт построится так, что **невозможно** добавить подписку без переписывания.
- **Distribution** — где продаётся (Play Store / App Store / RuStore / alternative / partner). Каждый канал = свои правила.
- **Legal** — GDPR / 152-ФЗ / store policy / privacy policy / TOS. Без compliance не пропустит Play Store review.

Сейчас по всем трём — **гoлый ноль решений**. Только направление зафиксировано в ADR-002/003 (anti-abuse + subscription priority) и в context-decisions, но конкретики нет.

## Главные понятия (просто)

### Monetization

- **Entitlement** — «у этого user'а есть право на фичу X». Может быть из subscription, partner license, trial. Должен быть **store-decoupled** (ADR-003 — отдельный слой, не привязан к Play Billing).
- **Tier** — уровень подписки (free / basic / premium / family).
- **Family pack** — один платит, N пользователей пользуются. Подходит к нашему admin/Managed модели.
- **Trial period** — N дней бесплатно перед оплатой.
- **Anti-abuse** — защита от refund abuse, sharing accounts, jailbroken devices, etc. (ADR-002).

### Distribution

- **Official store** — Play Store / App Store / AppGallery / Galaxy Store / RuStore.
- **Alternative store** — Aptoide, F-Droid, GetApps.
- **Partner distribution** — vendor поставляет устройство с предустановкой. Cliniki, retirement homes.
- **Direct APK** — sideload, mostly enterprise / pre-installed scenarios.
- **White-label** — partner-branded build (наш код, чужой брендинг).

### Legal

- **GDPR** — EU regulation для personal data.
- **152-ФЗ** — РФ закон о персональных данных. Требует серверов в РФ (data residency).
- **CCPA / LGPD / PDPA** — Калифорния / Бразилия / Сингапур. Похожи на GDPR.
- **Privacy Policy** — публичный документ «что мы делаем с данными». Обязателен.
- **TOS (Terms of Service)** — условия использования. Включает «как разрешаем спор», «как ограничиваем ответственность».
- **Data Subject Rights (DSR)** — права субъекта (export, delete, correct).

## Use case инвентарь

### Monetization

| ID | Кейс | Status |
|---|---|---|
| M-001 | Free vs paid feature split | ❌ |
| M-002 | Subscription model (monthly / yearly) | ❌ |
| M-003 | Family pack (один admin → N Managed) | ❌ |
| M-004 | Trial period | ❌ |
| M-005 | Subscription expiry behavior (что отключается) | ❌ |
| M-006 | Payment provider per platform (Play Billing / App Store / Stripe) | ❌ |
| M-007 | Refund handling | ❌ |
| M-008 | Region-based pricing | ❌ |
| **M-009** | **Anti-abuse / anti-tampering: device binding, integrity checks, obfuscation, root/jailbreak detection** | ❌ (ADR-002) — **остаётся в scope per user 2026-05-27 (защита от взлома)** |
| M-010 | Subscription transfer при смене устройства admin'ом | ❌ |
| M-011 | Entitlement после потери account | ❌ |
| M-012 | B2B / partner pricing (клиники, retirement homes) | ❌ |

### Distribution

| ID | Кейс | Status |
|---|---|---|
| DIS-001 | Play Store baseline | 🟡 (есть PLAY-STORE-BLOCKER'ы) |
| DIS-002 | App Store baseline | ❌ |
| DIS-003 | Alternative stores | ❌ |
| DIS-004 | Direct APK (partner / sideload) | ❌ |
| DIS-005 | Китайский рынок (нет Play Store) | ❌ |
| DIS-006 | RuStore (РФ) | ❌ |
| DIS-007 | White-label / partner-branded build | ❌ |
| DIS-008 | Update channel per source | ❌ |
| DIS-009 | Compliance per store policy | ❌ |

### Legal

| ID | Кейс | Status |
|---|---|---|
| L-001 | GDPR — controller/processor responsibilities | ❌ |
| L-002 | 152-ФЗ — РФ persdata localization | ❌ |
| L-003 | CCPA (California) | ❌ |
| L-004 | LGPD, PDPA, и т.д. | ❌ |
| L-005 | Contacts privacy compliance | ❌ LEGAL-001 PLAY-STORE-BLOCKER |
| L-006 | Privacy policy / TOS — публикация и поддержка | ❌ |
| L-007 | Children's data (могут быть фото с детьми <13) | ❌ |
| L-008 | Store policy compliance | ❌ |
| L-009 | Tax compliance per country | ❌ |
| L-010 | Subscription regulation (auto-renewal disclosures) | ❌ |
| L-011 | End-of-life inheritance — legal | ❌ |
| L-012 | Right to be forgotten implementation | ❌ |

## Главные открытые вопросы

### D-11. Monetization timing — до или после MVP

**Контекст**: monetization можно отложить (free для всех в beta) или включить сразу (subscription с первого дня).

**Варианты**:
- **Free for MVP, paid after**: проще запустить, легче собрать ранних adopters. Минус — потом «бесплатное стало платным» = массовый отток.
- **Free trial + subscription с первого дня**: разумная середина. Минус — больше работы на старте.
- **Subscription с первого дня (no free tier)**: filter quality users. Минус — отпугнёт experimenters.
- **Forever-free MVP, paid features later**: дольше валидируем продукт. Минус — нет revenue для развития.

**Регрет**:
- Если free → paid: первый же platный update приведёт к негативу в Play Store.
- Если paid → free: «дали бесплатно тем, кто платил» = unhappy paying customers.

**Рекомендация (best-guess)**: **trial-then-paid**. 7-14 дней trial + monthly subscription. Это стандартная schema, понятная пользователям, и legal-safe (subscriptions regulation в EU требует clear disclosure).

### D-Mon-1. Family pack — отдельный tier или multiplier

**Контекст**: V-002 (family scenario) — один admin, N Managed. Если каждый Managed считается отдельной подпиской — дорого. Family pack — fixed price для семьи.

**Варианты**:
- **Per-Managed subscription**: проще. Дорого для family.
- **Family pack tier**: $X for up to N Managed devices. Лучше для retention.
- **Hybrid**: per-Managed default, family upgrade.

**Рекомендация**: family pack tier с самого начала. GrandPad доказала, что family-pricing работает.

### D-Mon-2. Anti-abuse — насколько строго

**Контекст**: ADR-002 говорит — anti-abuse early. Но конкретно — насколько строго?

**Варианты**:
- **Tight**: device binding, receipt validation server-side, integrity checks. Минус — false positives отбьют пользователей.
- **Loose**: only receipt validation. Шеринг подписок легче — но и user-friendly.
- **Tier-based**: family pack — допускаем sharing, individual — нет.

**Рекомендация**: loose в MVP, наблюдаем abuse rate. Tighten в v2, если cost от abuse значителен.

### D-Dist-1. RuStore (РФ) — в MVP или нет

**Контекст**: РФ — большой market для senior-care. Play Store работает, но политически нестабильно. RuStore — официальная российская альтернатива.

**Варианты**:
- **Play Store only**: max convenience, но риск блокировки в РФ.
- **Play Store + RuStore**: безопаснее. Двойная работа compliance + 152-ФЗ.
- **Wait and see**: посмотрим на metrics в Play Store сначала.

**Рекомендация (best-guess)**: post-MVP. После Play Store stable — добавляем RuStore. РФ-data localization — отдельный architectural change (server в РФ).

### D-Legal-1. Privacy policy / TOS — кто пишет

**Контекст**: это не код. Это юридический документ. Может писать **юрист** (≥ $500 / template), **template-сервис** (Termly / iubenda — $10/month), или **сами** (рискованно).

**Варианты**:
- **Template сервис**: Termly / iubenda — auto-updated по regulations. ~$120/year. **Рекомендуется для MVP.**
- **Юрист**: правильнее для серьёзного релиза. Дороже.
- **Шаблон с GitHub**: бесплатно. Риск — не покрывает наши специфики (contacts data, family sharing).

**Рекомендация**: template-сервис для MVP, юрист — pre-public-release или при первом legal issue.

### D-Legal-2. 152-ФЗ — обязательно или нет

**Контекст**: 152-ФЗ требует, чтобы personal data РФ-граждан хранилась **на территории РФ**. Это значит — отдельный Firestore регион в РФ или собственный сервер. Дорого.

**Варианты**:
- **Не обслуживаем РФ-граждан**: технически возможно через geo-blocking.
- **Соответствуем 152-ФЗ**: server в РФ или Yandex Cloud Firestore alternative.
- **Grey zone**: работаем без compliance, надеемся, что не заметят.

**Рекомендация**: **post-MVP**. Сначала валидируем продукт в EU + US, потом добавляем РФ с compliance.

## Что в спеках / документации уже зафиксировано

| Документ | Что фиксирует |
|---|---|
| ADR-002 | anti-abuse + licensing architectural requirement |
| ADR-003 | monetization + entitlements (store-decoupled at domain level) |
| `feature-priorities.md` | billing subsystem обязателен |
| `context-decisions-and-open-questions.md` §3, §5, §7, §8, §9 | direction (но не детали) |
| `docs/compliance/` (если populated) | живые регистры |
| backlog LEGAL-001 | contacts privacy compliance PLAY-STORE-BLOCKER |

## Связь с другими документами

- **01 Vision** — D-1 (companion vs self-serve) определяет, кто платит.
- **02 Actors** — V-002 (family) определяет family pack.
- **05 Pairing** — pairing связан с entitlement (paired через подписку?).
- **07 Data & privacy** — legal compliance строится на privacy решениях.
- **09 Backend** — Blaze upgrade связан с revenue (есть деньги — можем).
- **11 Support** — refund handling, billing support tickets.

## Источники

- ADR-002, ADR-003.
- `docs/compliance/` (распакован: store-policy-register, country-legal-tax-register, distribution-channel-register, partner-distribution-model, permissions-and-resource-budget).
- [Play Billing](https://developer.android.com/google/play/billing) — Google subscription docs.
- [Apple StoreKit](https://developer.apple.com/storekit/) — Apple subscription docs.
- [Termly](https://termly.io/) / [iubenda](https://www.iubenda.com/) — privacy policy services.
- [GDPR.eu — comprehensive guide](https://gdpr.eu/).
- [152-ФЗ overview ru](https://www.consultant.ru/document/cons_doc_LAW_61801/).
- [RuStore developer docs](https://www.rustore.ru/help/developers).

## Заметки решений

| Дата | Решение | Regret | Exit ramp |
|---|---|---|---|
| _(пусто)_ | | | |

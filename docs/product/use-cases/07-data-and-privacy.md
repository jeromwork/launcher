# 07. Data & Privacy — что хранится, где, кто видит, что в конце

> **Status**: 🟡 partially decided (D-26 RESOLVED 2026-05-27 evening, account deletion + telemetry decisions 2026-05-28) · **Created**: 2026-05-27

## Account Deletion Flow — MVP (2026-05-28)

**Mandatory** per Google Play Policy + GDPR Art. 17 + 152-ФЗ. **In MVP scope.**

### UI

Settings → Account → **«Удалить мой аккаунт»**:
1. Confirmation screen с **explicit consequences**: «вы потеряете доступ к Family Group X, Y. Family photos станут недоступны вам.»
2. Optional **data export** (упрощённый JSON dump в MVP).
3. **Re-auth** (биометрия / пароль) — anti-mistap.
4. Final «Подтвердить удаление через 30 дней».

### Three phases

**Phase 1 — Initiation**: account marked **«deletion pending»** в БД. Email confirmation отправляется.

**Phase 2 — Grace period (30 days)**:
- Login ещё работает. User может cancel в любой момент в Settings.
- Family Group members видят «X собирается уйти, осталось N дней».
- Subscription (если applicable) — cancellation flow запущен у платёжного provider'a.

**Phase 3 — Hard delete** (after grace):
- Batch job (manual cron в MVP, automated post-Blaze).
- User identity record удалён.
- Membership records во всех Family Groups удалены.
- Public keys из сервера удалены.
- Audit log с deletion hash (compliance proof).
- Final email confirmation.

### Admin handover requirement

При initiation **singleton admin'а** (admin в группе без co-admin'a):
- **Block**: deletion requires either «передайте admin role одному из членов» OR «распустите Family Group».
- **No automatic dissolution** — explicit user choice.

### Envelope encryption limits

Old blob content уже зашифрован с wrapper для удалённого user'а. На hard delete:
- ✅ **Удаляем user's wrappers** из metadata (cleanup).
- ❌ **Не можем стереть** копии, которые user уже скачал (физически на его устройстве, не доступны для wipe).

Это **inherent** для at-rest encryption. **Записать в Privacy Policy**: «account deletion не достаёт data из copies, скачанных до deletion».

### Architecture

```kotlin
// core/domain/
interface AccountDeletion {
    suspend fun initiate(userId: UserId, reason: String?)
    suspend fun cancel(userId: UserId)
    suspend fun status(userId: UserId): DeletionStatus
}
```

Adapter — Firestore + Cloudflare Worker. Email sender — adapter (SendGrid / Mailgun free tier post-MVP).

### Post-MVP

- Polished GDPR data export (structured JSON / CSV with all user-content).
- Automated batch job (Blaze Cloud Functions cron).
- Per-region grace period (EU 30d, US 7d, etc.).
- Account suspension / reactivation flow (sometimes legal in some jurisdictions).



## Shared Family Album — decision (D-26)

**Resolved**: MVP — только **photos** через спеку 012 (фото контактов + личные документы). Полноценный album с **videos + audio + family memories** — **v2**.

Architectural foundation **уже готова**:
- Envelope encryption из D-25 (Family Group) даёт **N-recipient encryption** для photo blob'ов.
- Blob storage adapter из спеки 011 хранит зашифрованные данные в Firestore / Cloud Storage.
- Reference counting + cleanup из 011 управляет жизненным циклом photo blob'ов.

Что добавится в v2:
- Video blobs (тот же envelope-механизм, но контент крупнее → нужно chunked upload + streaming decrypt).
- Audio blobs (то же).
- «Family memories» — concept (collections, timelines, captions, anniversaries) — отдельная спека.
- Album UI: timeline / grid / search / share — отдельная спека.

> **Зачем читать**: privacy — это не «доделаем потом». Это **архитектурное решение в самом начале**. Сейчас спека 011 заложила сильный фундамент (per-pairing keys, encrypted envelopes), но **lifecycle-сценарии** (export, delete, end-of-life, multi-admin boundaries) — gap.
> **Источник**: `user-journeys-draft.md` §7.5 + спека 011 + `docs/dev/project-backlog.md` LEGAL-001.

---

## Что это за документ (просто)

Каждое приложение **порождает данные**: контакты, фото, история, настройки, телеметрию. Эти данные **где-то живут**: на устройстве, в облаке, у третьих лиц (Cloudflare, Firebase, Google). И их **кто-то видит**: пользователь сам, родственники-admin, разработчики (через crash reports?), Google / Cloudflare через свои тулзы, регуляторы (по запросу).

Privacy — это **ответ** на «что куда уходит, кто это видит, как удалить, как унаследовать». Если ответа нет, продукт **не пройдёт Play Store review** (LEGAL-001 PLAY-STORE-BLOCKER в backlog) и регуляторов (GDPR / 152-ФЗ / CCPA).

Сейчас архитектурно мы сильны (encryption per pair, key reference counting, blob storage adapter). **Слабы — в lifecycle** (что происходит, когда бабушка умерла? Когда admin disappears? Когда регулятор запросил все данные?).

## Главные понятия (просто)

- **PII (Personally Identifiable Information)** — данные, по которым можно идентифицировать человека. Номера телефонов, фото, имя.
- **At-rest encryption** — данные зашифрованы, когда лежат «на диске» (в Firestore, на устройстве).
- **In-transit encryption** — зашифрованы, когда «летят» (TLS).
- **End-to-end (E2E) encryption** — даже **мы сами** (разработчики, владельцы серверов) не можем прочитать. Это самый сильный уровень. Спека 011 делает E2E для blob'ов.
- **Per-pairing keys** — каждый pair (admin↔Managed) имеет **свои** ключи. Если admin2 — это другой pair, у него другие ключи. Видит только то, что зашифровано **для него**.
- **Reference counting** — учёт, сколько ссылок на blob (фото). Когда 0 — можно удалить. Реализуется в 011.
- **Retention policy** — сколько храним. История конфигов — N версий, потом старые удаляются (client-side housekeeping per CLAUDE.md rule 8).
- **Data Subject Rights (DSR)** — права субъекта данных по GDPR: export, delete (right to be forgotten), correct, restrict processing.
- **Telemetry** — анонимная или псевдонимизированная статистика использования продукта. Сейчас нет. Crash reports — тоже telemetry.

## Use case инвентарь

| ID | Кейс | Status | Notes |
|---|---|---|---|
| D-001 | Что хранится на устройстве (encrypted blobs) | ✅ (011) | |
| D-002 | Что хранится в облаке (config / blobs / telemetry) | 🟡 (008 config + 011 blobs) | telemetry — нет |
| D-003 | GDPR data export («download my data») | ❌ PLAY-STORE-BLOCKER | LEGAL-001 |
| D-004 | GDPR delete (right to be forgotten) | ❌ | |
| D-005 | Multi-admin privacy (admin1 видит то, что внёс admin2) | ❓ D-13 | |
| D-006 | Vendor / third-party (клиники) privacy boundary | 🔮 (011 OWD-5 per-app identity) | |
| D-007 | Photo encryption per recipient | 🟡 (012 stub) | |
| D-008 | Backup / restore после reset | ❌ (S-401 + 011 OWD-4) | |
| D-009 | Cross-border data residency | ❌ | 152-ФЗ требует РФ-локализации |
| D-010 | End-of-life (S-601..S-603 — наследование, удаление) | ❌ D-3 | |
| D-011 | Photos retention без reference | 🟡 (011 reference counting) | |
| D-012 | Config history TTL | 🟡 (client-side housekeeping) | |
| D-013 | Contacts privacy compliance | ❌ LEGAL-001 PLAY-STORE-BLOCKER | |
| D-014 | Crash collection (Crashlytics?) | ❌ D-16 | |
| D-015 | Telemetry opt-in/opt-out | ❌ D-12 | |
| D-016 | «Что обо мне знает Google / Cloudflare» — privacy transparency | ❌ | |

## Главные открытые вопросы

### D-12. Privacy posture default — opt-in / opt-out / no telemetry

**Контекст**: telemetry даёт **insights** о реальном использовании (сколько пользователей, какие фичи популярны, где crashes). Без неё мы летим вслепую.

**Варианты**:
- **No telemetry at all**: максимум privacy. Минус — невозможно улучшать продукт по факту.
- **Opt-out telemetry (по умолчанию on)**: GDPR-style «вы согласились использованием». Минус — некоторые регуляторы не принимают. Senior-аудитория особенно чувствительна.
- **Opt-in telemetry (по умолчанию off)**: максимум privacy + некоторые данные. Минус — мало кто включит.
- **Anonymized telemetry on by default + opt-out**: только агрегаты, без individual identifiers.

**Рекомендация (best-guess)**: вариант 4 (anonymized + opt-out). Privacy-respecting, но достаточно данных для улучшения. **Явно сообщать** в onboarding и в Settings.

### D-3 + D-10. End-of-life — наследование данных

**Контекст**: бабушка умерла → телефон у родственников. Что им видно? Что они могут восстановить? Как удалить аккаунт от её имени?

**Варианты**:
- **No succession**: при reset = всё стёрто. Семья ничего не получит.
- **Family-data succession**: фото, контакты — доступны семье через admin-роль. Шифрованные blob'ы — расшифровываются через social recovery.
- **Legal-grade procedures**: процедура наследования по запросу с official document.

**Рекомендация (best-guess)**: family-data + legal procedures обеих для critical scenarios. Это **связано с 011 OWD-4 (social recovery)** и должно быть в roadmap'е.

### D-Data-1. Multi-admin data privacy boundaries

**Контекст**: admin1 добавил «контакт врача» бабушке. Видит ли admin2 номер врача? Или только сам факт «есть контакт»?

**Варианты**:
- **Полная прозрачность**: всё что в pair-е, видно всем admin'ам. Простая модель.
- **Per-admin sub-views**: каждый admin может пометить «private», и оно скрыто от других.
- **Tier'ы**: основной admin видит всё, co-admin видит только то, что добавил сам и общие.

**Рекомендация**: **полная прозрачность для MVP**. Семья. Если в будущем нужны privacy boundaries (например, B2B клиника + семья) — добавляем opt-in.

### D-15. Vendor / partner integration boundaries

**Контекст**: 011 OWD-5 заложила per-app identity. Это значит, что в будущем «клиника» или «security сервис» может быть подключён к бабушкиному устройству, но видеть **только своё**. Архитектурно готово. Но **в продуктовой модели** не зафиксировано.

**Варианты**:
- **Не делаем сейчас**: будущая фича.
- **Зафиксировать privacy contract**: что **может** видеть vendor, что **никогда** не видит — записать в product principles, даже если фича не делается.

**Рекомендация**: зафиксировать privacy contract. Это поможет потом не закрыть one-way door случайно.

### D-16. Crash collection vs privacy

**Контекст**: без crash reports — качество не растёт. С Crashlytics (Google) — все crashes у Google.

**Варианты**:
- **Crashlytics**: convenient, free, в Google. PII filtering обязателен.
- **Sentry self-hosted**: контролируем мы. Cost — наш сервер. Связано с server-roadmap.
- **No crash collection**: rely on manual reports. Качество страдает.
- **Hybrid**: opt-in detailed crashes (Sentry или Crashlytics с consent), local-only summary if user declines.

**Рекомендация (best-guess)**: **hybrid** с opt-in. По умолчанию — только локальная статистика (количество crashes), отправка детальных trace — только с consent.

## Что в спеках уже зафиксировано

| Спек | Что фиксирует |
|---|---|
| 011 Crypto Foundation | per-pairing keys, encryption envelope, reference counting, blob storage adapter, OWD-4 social recovery (future), OWD-5 per-app identity (future) |
| 008 Config Sync | wire format, schema versioning |
| 012 (stub) | photo encryption flow per recipient |
| `docs/dev/server-roadmap.md` | privacy / compliance section (что планируется на server) |
| backlog LEGAL-001 | contacts privacy compliance PLAY-STORE-BLOCKER |
| backlog SEC-003 | audit logging для critical ops |

## Связь с другими документами

- **01 Vision** — privacy posture влияет на позиционирование продукта.
- **04 Remote management** — D-13 multi-admin privacy.
- **05 Pairing & trust** — privacy следует из trust model.
- **09 Backend & reliability** — где данные физически живут.
- **10 Monetization+Legal** — GDPR / 152-ФЗ / CCPA — legal compliance.
- **11 Support+Dev** — D-16 crash collection.

## Источники

- Спека 011 + `docs/dev/server-roadmap.md` «CRYPTO + PRIVATE MEDIA STORAGE».
- [GDPR text](https://gdpr-info.eu/) — официальный текст.
- [152-ФЗ overview](https://www.consultant.ru/document/cons_doc_LAW_61801/) — РФ.
- [Apple Privacy Nutrition Labels](https://www.apple.com/privacy/labels/) — формат privacy declaration.
- [Google Play Data Safety section](https://support.google.com/googleplay/android-developer/answer/10787469) — требования для Play Store.

## Заметки решений

| Дата | Решение | Regret | Exit ramp |
|---|---|---|---|
| _(пусто)_ | | | |

# 05. Pairing, Identity & Trust — клей между admin и Managed

> **Status**: 🟡 partially decided (D-25 RESOLVED 2026-05-27 evening) · **Created**: 2026-05-27
> **Зачем читать**: pairing — это **самый чувствительный** механизм продукта. Если он шаткий, всё, что построено сверху (config sync, photos, calls), разваливается. Сейчас в спеках 007 + 011 заложен сильный фундамент, но **edge cases** (потеря admin'а, social recovery, multi-admin invite) ещё не закрыты.
> **Источник**: `user-journeys-draft.md` §7.2 + §7.3 + spec 007 + spec 011.

---

## Что это за документ (просто)

Когда два устройства должны доверять друг другу удалённо — это нетривиально. Нельзя просто «логинься одним аккаунтом», потому что у нас две роли (admin и Managed), и они могут быть на разных Google-аккаунтах. Нельзя просто «введи пароль» — пожилой пользователь его не запомнит.

Решение в спеке 007 — **QR-pairing**: admin показывает QR на своём экране, Managed сканирует и связывается. Это создаёт **trust edge** — «эти два устройства теперь доверяют друг другу». После этого admin может что-то менять у Managed.

Этот документ — про **жизнь этого trust edge**: как он создаётся (первичное pairing), как он восстанавливается (после reset), как он отзывается (если admin потерял телефон), как добавляются дополнительные admin'ы (co-admin), и **что происходит, если всё пошло не так**.

Identity — отдельный, но связанный вопрос: **кто такой admin** с точки зрения Firebase Auth. Сейчас anonymous, в будущем — Google Sign-In или Phone Auth (см. backlog ARCH-004).

## Главные понятия (просто)

- **Pairing** — процесс установления первичного доверия между двумя устройствами. У нас — через QR + Firestore-документ (см. 007).
- **Trust edge** — установленная связь «устройство X доверяет устройству Y для операции Z». Может быть односторонней или двусторонней.
- **Link** — Firestore-document, представляющий pair (admin↔Managed). Когда unlink — документ удаляется (hard delete per 007 FR-027).
- **Identity** — кто ты с точки зрения Firebase. Anonymous (auto-generated UID) или named (Google / Phone / Email).
- **Per-pairing keys** — крипто-ключи, специфичные для одной пары устройств (см. 011). Если pair удалена — ключи стираются (reference counting).
- **Social recovery** — восстановление доверия через других людей. Например, бабушка потеряла телефон → ставит наш app на новый → admin'ы подтверждают «да, это та же бабушка». В 011 OWD-4 как будущая часть.
- **Co-admin** — второй (третий) admin на одного Managed. Сейчас спека 008 поддерживает (multi-admin model), но **процедура invite**'а нового co-admin'a не зафиксирована.

## Use case инвентарь

### Pairing primitives

| ID | Кейс | Status |
|---|---|---|
| P-001 | Первичный pairing через QR (happy path) | ✅ (007) |
| P-002 | Re-pairing после factory reset на Managed | 🟡 (частично через 011 OWD-4) |
| P-003 | Reused token / TTL expired | ✅ (007) |
| P-004 | Добавление co-admin'а — invite link / QR на admin'а | ❌ |
| P-005 | Revoke admin (отозвать доступ от co-admin'a) | 🟡 (через unlink) |
| P-006 | Reverse direction — Managed показывает QR | ❌ S-208 |
| P-007 | Pairing на расстоянии (внук в другом городе) | ❌ S-103 |
| P-008 | Trust edge revocation (удалили co-admin'a) | ❌ FUTURE-SPEC-010 |
| P-009 | Audit «кто paired со мной» (видит Managed?) | ❌ |
| P-010 | Reuse pairing primitive для Jitsi / vendor / hardware | 🔮 (011 OWD-5) |

### Identity / auth

| ID | Кейс | Status |
|---|---|---|
| A-001 | Anonymous auth на first launch | ✅ (007) |
| A-002 | Миграция на named auth (Google / Phone / Email) | 🔮 ARCH-004 / AUTH-001 |
| A-003 | Login с нового устройства (same account) | 🟡 (008 multi-device) |
| A-004 | Account suspended / banned Firebase'ом | ❌ |
| A-005 | Admin забыл Google-пароль | ❌ (полагаемся на Google recovery) |
| A-006 | Admin потерял все свои устройства, account orphaned | ❌ S-404 |
| A-007 | Identity model для vendor / partner | 🔮 |
| A-008 | Per-app identity (один user, разные роли) | 🔮 (011 OWD-5) |

## Family Group Model (RESOLVED 2026-05-27 evening, D-25)

> Базовый архитектурный паттерн для group-based trust. Закрывает D-25.

### Принципы

- **Каждый участник** имеет свою `(priv_i, pub_i)` keypair (своя, на своём устройстве, никто не делится своим priv).
- **Group** = запись в БД на сервере: `{groupId, members: [{userId, pub, role}], ...}`. **Никакого priv_G на сервере** — это сохраняет E2E promise.
- **Roles**: admin (создатель + любой promote'нутый им), member, managed, caregiver (tier — см. D-27).
- **Membership operations** (add / remove / promote / kick) — подписаны admin'ом своим priv. Сервер арбитрирует (проверяет подпись + role), обновляет membership list.

### Crypto для shared content (envelope encryption)

Используется для всего, что **разделяется среди членов группы** (Family Album, group-shared config history, group-wide announcements):

1. Producer (любой член) генерирует случайный **симметричный ключ K** (per-content).
2. Шифрует контент ключом K → `encrypted_content`.
3. **Wrapper'ит K** для каждого текущего члена группы:
   - `wrap_for_member_i = encrypt(K, pub_i)`
4. Заливает: `encrypted_content` + `[wrap_for_member_1, wrap_for_member_2, ...]`.
5. Сервер хранит — **не может прочитать**.

При чтении любой текущий член:
1. Аутентифицируется на сервере (session token).
2. Получает `encrypted_content` + список wrapper'ов.
3. Использует **свой `priv_i`** на **своём** wrapper'е → получает K.
4. Декрипт `encrypted_content` ключом K.

### Crypto для 1-to-1 (pair-keys из спеки 011)

Используется для **direct communication** (admin↔Managed config sync, direct private messages):

- Pair-keys между двумя устройствами, как описано в спеке 011 (per-pairing keys, AEAD envelope).
- **Не дублирует** group-механизм — для 1-to-1 envelope с N=1 wrapper'ом был бы overhead.

### Removal mechanics («ключи протухли»)

Когда admin кикает участника:
1. Подписанная операция → сервер удаляет из membership.
2. Сервер аннулирует session-токены кикнутого.
3. Кикнутый не может получить **новый** контент: сервер 403'ит запросы.
4. Producers, делая wrapper'ы для нового контента, **не включают** pub кикнутого → даже если он скрапит encrypted_content, K у него нет.
5. **Что осталось у кикнутого**: всё, что он скачал до кика — на его устройстве в расшифрованном виде. Это inherent для at-rest crypto, не лечится никаким протоколом.

### Подсчёт ключей

- 1 группа из N членов → N keypair'ов на сервере (pub-only), 1 membership-запись.
- 1 единица shared content → N маленьких wrapper'ов (32 байта каждый).
- N=4: overhead 128 байт на content. Незаметно на фоне самого фото.

### Что НЕ нужно делать

- ❌ Хранить priv_G на сервере (ломает E2E).
- ❌ Signal-style double ratchet (over-engineered для at-rest content).
- ❌ N² pair-keys между всеми членами (envelope encryption эффективнее).
- ❌ Re-encrypt всех старых данных при removal (не нужно — старое осталось доступно тому, кто скачал; новое уже не wrap'ится для него).

### Что добавить в спеку 011 (refactor для Family Group)

1. Membership data model (group → members → role + pub + ttl_expiry).
2. **Many-to-many user↔group** (user может быть в N groups, group имеет N members).
3. Envelope encryption primitive (`crypto_box_seal` + symmetric content key).
4. Membership operations signed by admin's priv, verified by server.
5. Server arbitration logic (Cloudflare Worker — extends push-relay).
6. **Spec 007 pair-pairing остаётся как 1-to-1 channel**, recovery flow, базовый trust.

Запланировать как новую спеку (013 или 014 «family-group-foundation»), которая extends 011 и updates 007/008.

---

## Caregiver Integration (D-27 RESOLVED 2026-05-27 evening)

> Extends Family Group Model. Применяется к hired professionals, medical staff, clinic personnel, volunteers — всех, кто не является членом семьи но участвует в уходе.

### Roles в Family Group

| Role | Trust | Default permissions |
|---|---|---|
| `Admin` | full | всё: edit members, edit config, edit content, read all |
| `CoAdmin` | full minus member-management | edit config, edit content, read all |
| `Member` | family | read family content, write to family content, see care overview |
| `Managed` | self | own device control, read what's shared with them |
| `Caregiver` (NEW) | restricted | per role preset (см. ниже) |

### Role Presets для Caregiver

Bundle permissions + default TTL:

| Preset | Permissions | Default TTL |
|---|---|---|
| `FamilyCaregiver` | as Member + write care notes | indefinite |
| `HiredCaregiver` | SOS alerts, read health, contact tile, visit log | indefinite (manual revoke) |
| `MedicalWorker` | SOS, read health, write medical note | 24h (per-visit) |
| `ClinicStay` | full care access, audit-logged | duration of stay (manual revoke) |
| `Volunteer` | SOS only | 30 days renewable |

Admin при invite **выбирает preset**, optionally overrides specific permissions, optionally adjusts TTL.

### Remote Invite Flow

Не требует физической встречи admin'а и caregiver'a:

1. Admin opens admin app → «Add caregiver».
2. Selects role preset + TTL.
3. App генерирует **signed invite link** (содержит: groupId, presetId, ttl, expiry of link itself, подпись `priv_admin`).
4. Admin шлёт link **любым channel** (messenger, email, SMS).
5. Caregiver открывает link в нашей app (с возможностью install app first).
6. Caregiver видит preview: «Bob inviting you to Care Group X with role 'MedicalWorker', expires in 24h. Accept?»
7. Caregiver жмёт Accept → её `pub_caregiver` отправляется на сервер вместе с invite signature.
8. Server проверяет подпись (от admin'а с правом invite), добавляет caregiver в group с specified preset + TTL.

**QR-альтернатива** для in-person setup — backup, не default.

### Role-Based Envelope Filtering

Когда producer (любой member с правом upload) загружает content в group:

1. Контент имеет **category** (`FamilyContent` / `CareContent` / `Public`).
2. **Recipients вычисляются автоматически** по правилам:
   - `FamilyContent` → wrappers только для roles {Admin, CoAdmin, Member, Managed}.
   - `CareContent` → wrappers для всех roles включая Caregiver.
3. Producer **не выбирает recipients вручную** — выбирает category. Сервер / app формирует wrappers list.

Это **enforce'ит** privacy boundary crypto-уровнем: caregiver физически не получает envelope wrapper для family album, даже если scrape'нет encrypted_content.

### TTL on Membership (не на group)

Каждый `Membership` имеет optional `ttl_expiry`. Сервер при каждом access check'е:
- Если `ttl_expiry` истёк → treat membership as kicked → server 403, новые wrappers не делаются.
- Group **не expires** — только membership specific user'ов.

Это позволяет «1-time visit doctor with 24h access» без overhead создания / удаления group.

### Audit Log

**Mandatory** для caregiver actions (backlog SEC-003):
- Логируется: view sensitive data, write care note, trigger emergency, etc.
- Notify admin при view of sensitive data.
- Retention policy — отдельная спека (GDPR considerations).

### Что НЕ может caregiver

- Не может escalate свою role (только admin меняет permissions).
- Не может invite других caregiver'ов (нужен admin).
- Не видит family-private content (envelope wrappers не включают).
- Не пишет в Managed config (только admin / co-admin).

### MVP scope

- 1 default Family Group per Managed (data model supports many).
- 4-5 role presets (Medical Worker, Hired Caregiver, Family Caregiver, Volunteer, Clinic Stay).
- Remote invite via signed link.
- TTL on membership.
- Role-based envelope filtering (2 categories: Family / Care).
- Basic audit log.

### Post-MVP

- Optional additional groups (Care Group dedicated, Friends Circle, etc.) — admin manually creates.
- More role presets / sub-tiers.
- Auto-revoke TTL UI + notifications.
- Vendor (B2B) integration via separate role.
- 011 OWD-5 per-app identity для finer-grained tiers.

---

## Главные открытые вопросы

### D-Pair-1. Anonymous vs Named auth — когда мигрируем

**Контекст**: сейчас anonymous (Firebase auto-UID). Это работает для MVP, но имеет проблемы:
- При factory reset бабушки — UID теряется, нет связи с предыдущей identity.
- При смене устройства admin'ом — теряется account.
- Невозможен «login с нового устройства same account».
- Anti-abuse слабее (нет stable identity для rate-limit per-uid).

**Варианты**:
- **Оставить anonymous как default**: проще для пользователя (никаких login screens). Минус — все вышеперечисленное.
- **Force named auth (Google Sign-In)**: всё решено. Минус — пожилой не сможет залогиниться сам; admin может.
- **Hybrid**: anonymous для Managed (бабушка не логинится), named для admin (admin логинится в Google). **Most likely path.**

**Регрет**: anonymous-forever — невозможна recovery / multi-device / anti-abuse.

**Рекомендация (best-guess)**: hybrid. admin = named (Google Sign-In Firebase), Managed = anonymous + paired (trust через admin'а). Это **естественная аналогия с Family Link** Google.

### D-Pair-2. Procedure to add co-admin

**Контекст**: спека 008 поддерживает multi-admin, но **как добавить второго admin'a** — не зафиксировано.

**Варианты**:
- **QR на admin'а**: admin1 показывает QR на своём экране, admin2 сканирует. Симметрично с initial pairing.
- **Invite link с TTL**: admin1 генерирует ссылку, отправляет в любой messenger, admin2 кликает.
- **Through Managed**: admin2 показывает QR на Managed-устройстве, admin1 одобряет с своей стороны.

**Регрет**:
- QR-on-admin: требует физической встречи admin1 и admin2 — обычно их нет.
- Invite link: ссылка может попасть не туда (forwarded в чужой чат).
- Through Managed: бабушка должна что-то нажать — лишний шаг.

**Рекомендация**: **invite link с TTL 1h + одобрение admin1**. Admin2 кликает → видит «я хочу присоединиться к [бабушке]» → admin1 видит запрос в своём app → одобряет. Двухстадийный.

### D-Pair-3. Social recovery — когда внедрять (S-401, 011 OWD-4)

**Контекст**: бабушка сделала factory reset → потеряла keys → admin'ы должны её «опознать» и восстановить trust. Это сложный flow.

**Варианты**:
- **Не делаем в MVP**: при reset = factory new, всё с нуля.
- **Делаем в MVP**: критическая фича, без неё первый же случай reset → потеря всего.
- **Architectural readiness, full UX позже**: закладываем в 011 (что сделано как OWD-4), полный flow — отдельная спека.

**Регрет**: если не сделаем — реальный случай «бабушка случайно сделала reset» → потеря фото внуков → крик в support.

**Рекомендация**: architectural readiness уже сделана в 011. **Spec на full flow — нужна, до публичного релиза.**

### D-Pair-4. Multi-admin invite trust model

**Контекст**: связан с D-13 (multi-admin privacy). Когда admin2 присоединяется — должен ли он видеть **историю** изменений admin1? Или только начинает с чистого листа?

**Варианты**:
- **Полная history**: admin2 видит всё, что когда-либо делал admin1. Максимальное доверие, минимальное privacy.
- **С момента invite**: admin2 видит только то, что изменялось после его invite'а.
- **Read-only history, edit forward**: admin2 видит для context, но не может «откатывать» решения admin1 без одобрения.

**Рекомендация**: «с момента invite» по умолчанию + history по запросу через UI. Avoid information asymmetry conflicts.

## Что в спеках уже зафиксировано

| Спек | Что фиксирует |
|---|---|
| 007 Pairing | QR-pairing, TTL, anti-replay, Cloudflare Worker, FCM |
| 008 Config Sync | multi-admin merge UI |
| 010 Setup Assistant | 7-tap admin-mode gate; paired-devices visibility |
| 011 Crypto Foundation | per-pairing keys, reference counting, OWD-4 social recovery as future, OWD-5 per-app identity |
| backlog ARCH-004 | named Firebase Auth migration |
| backlog AUTH-001 | AuthProvider port + Firebase Email/Password adapter |

## Связь с другими документами

- **03 Launcher UI** — где показывать «paired status», «my admins», invite QR.
- **04 Remote management** — admin UI смотрит на trust edges из этой модели.
- **07 Data & privacy** — что видит admin, что нет — privacy boundary.
- **09 Backend & reliability** — pairing зависит от Firestore + Cloudflare Worker.

## Источники

- Спеки 007, 008, 010, 011.
- `docs/adr/ADR-007` (TODO-DOC-001 — QR-pairing as trust primitive).
- Memory: `project_qr_pairing_trust_primitive.md`.
- [Apple Wonderlust pairing patterns](https://developer.apple.com/documentation/devicediscoveryui) — reference.
- [Google Family Link sharing](https://support.google.com/families) — child-parent reference.

## Заметки решений

| Дата | Решение | Regret | Exit ramp |
|---|---|---|---|
| _(пусто)_ | | | |

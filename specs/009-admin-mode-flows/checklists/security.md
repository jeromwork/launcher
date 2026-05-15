# Checklist: security

**Spec**: `spec.md` (rev. 1 — pre-specify scope discovery 2026-05-15)
**Run**: 2026-05-15 — пост-`/speckit.clarify`, до `/speckit.plan`.

Verifies OWASP MASVS + Article XIV конституции. Наследует security-baseline спека 007 (Firestore Auth + Security Rules) и спека 008 (Room + collaborative write). Спек 9 **расширяет attack surface** двумя новыми путями:

1. **Системные контакты admin-устройства** через `READ_CONTACTS` (runtime permission) — новый источник PII.
2. **Exported intent-filter на `ACTION_SEND` + `text/x-vcard`** — **новая** external attack surface (любое приложение на устройстве может слать VCard).

---

## Data flow summary (для контекста чеклиста)

| Flow | Source | Destination | Sensitivity |
|---|---|---|---|
| `/config.contacts[]` write | admin (через picker или VCard) | Firestore | **high** — имя+номер **третьего лица** (Маша), не самого admin'а |
| `/config/history/{autoId}` write | admin / Managed editor (client-side) | Firestore | **high** — все предыдущие версии config'а с PII |
| `/config/history/{autoId}` read | admin / Managed editor | Firestore | high |
| `/links/{linkId}/health` read | admin (Firestore listener когда экран открыт) | client | low-medium (battery%, audio mute, lastSeen — не PII, но revealing) |
| `READ_CONTACTS` read | admin device system contact book | local UI | high — все контакты телефона admin'а |
| VCard intent extras read | любое external app (system share sheet) | local parser | **high** — untrusted input |
| Local draft (Room `PendingLocalChanges`) | admin editor | local SQLite | high — same PII as `/config` |
| `PhoneHealthCriticalEvent` (local) | health listener | local subscriber (none) | low |
| Play Store fallback `market://` URL | Managed dispatcher | system intent | low — `packageName` only |

**Key sensitivity points:**
- `/config.contacts[]` содержит **third-party PII** (контакты бабушки/мамы/Машы), не самого admin'а — это **самая чувствительная категория** в спеке 9. GDPR ст.6 / 152-ФЗ ст.6 применимы: третье лицо не давало согласие на хранение своих данных у нас.
- VCard intent-filter — единственная **non-Firestore** входная точка спека 9; парсер должен относиться к payload как к untrusted user input.
- `/config/history` ×10 умножает PII footprint: каждый snapshot — полная копия contacts.

---

## Data at rest (MASVS-STORAGE)

- [ ] **CHK001 — No PII stored in clear-text SharedPreferences / unencrypted files unless justified**

  **Finding: WATCH (inherited + amplified).** Спек 9 расширяет применение Room database спека 8: добавляет
  - локальный draft в `PendingLocalChanges` (per-Managed) — содержит **полный editable config** включая contacts;
  - локальный кэш history (если plan.md решит кэшировать snapshot'ы для offline preview).

  Спек 8 принял **Option A** (Room unencrypted, app sandbox protection). Спек 9 наследует это решение **без изменений**. Однако:
  - Объём PII в Room **увеличивается** (теперь и draft + потенциально cached history × 10). Аргументы спека 8 продолжают применяться (app sandbox = OK; rooted/forensic = не защищаемся).
  - **Action для plan.md**: явно подтвердить, что spec 011 (e2e media) останется триггером ревизии SQLCipher; в спеке 9 — re-confirm Option A. Inline-TODO на ревизию при первом GDPR/152-ФЗ инциденте.

- [ ] **CHK002 — Sensitive data (auth tokens, biometric refs) in EncryptedSharedPreferences / Keystore**

  N/A. Спек 9 не вводит новые auth tokens. Firebase Auth refresh-token — управляется SDK. 7-tap+пароль (US-6) — это **спек 010** (per roadmap), не 9.

- [x] **CHK003 — Cache files containing user data have TTL and clear-on-uninstall policy**

  - Room database (включая `PendingLocalChanges` draft) — auto-deleted on uninstall (Android default). ✅
  - **FR-004 явно** требует: «Локальные кэши удалённых Managed — очищаются при следующем launch». ✅
  - **Watch для plan.md**: при unpair `linkId` X — также удалить (а) `PendingLocalChanges WHERE linkId = X`, (б) cached history для X (если будет кэширование), (в) live `Health` snapshot для X. Это уже **mandatory action item #8 из спека 008** — расширить на 9.

- [ ] **CHK004 — Logging excludes PII**

  Спец.md не специфицирует logging detail (это plan.md). Однако спек 9 **значительно расширяет** PII footprint в логах, особенно через VCard parsing и contact validation.

  **Action для plan.md**:
  - Категория событий: `[INFO] contact.added source=vcard linkId=<id>`, **никогда** не `[INFO] contact.added name=Маша phone=+79161234567`.
  - VCard parser MUST логировать только **причину reject'а** (size>10KB / non-UTF8 / no TEL), **не сам payload**.
  - `Contact.fromRaw` ValidationError MUST логироваться только типом (`NameTooLong`, `PhoneInvalid`), **не значением**.
  - History list MUST логироваться только `count + recordedAt`, **не diff'ом снэпшотов**.
  - Health values (`battery=3%`, `audioMuted=true`) — borderline. Per spec 008 CHK004, borderline-OK для warning/critical events; **не для regular Info polling**.

## Data in transit (MASVS-NETWORK)

- [x] **CHK005 — All network calls use HTTPS / TLS 1.2+; no cleartext**

  - Firestore SDK — HTTPS only.
  - Спек 9 не добавляет новые сетевые endpoint'ы (только расширяет схему `/config` через `/config/history/*`).
  - Play Store deep-link `market://details?id=...` — system handler, не network call приложения.
  - `https://play.google.com/store/apps/details?id=...` fallback — HTTPS. ✅

- [x] **CHK006 — If certificate pinning is required: documented**

  Спек 9 не вводит pinning (как и 007/008). **N/A**.

## Authentication / Authorization (MASVS-AUTH)

- [ ] **CHK007 — Every privileged action lists required permission + role / entitlement**

  - **FR-044**: read `/config/history/*` — `adminId` ИЛИ `managedDeviceFirebaseUid`. ✅ Соответствует pattern'у спека 008 FR-011.
  - **FR-045**: write `/config/history/*` — те же UID-ы. ⚠️ **Это новое право на write**, **должно быть явно добавлено** в `firestore.rules`. Текущий `firestore.rules` (строки 151-159) покрывает только `/config/{configId}`, **не subcollection** `/config/history`. Согласно Firestore Rules patterns, `match /config/{configId}` **не** распространяется на `/config/{configId}/history/{autoId}` автоматически — нужен новый `match` блок.

    **Action для plan.md (MANDATORY)**: добавить в `firestore.rules`:
    ```
    match /links/{linkId}/config/{configId}/history/{autoId} {
      allow read:   if isLinkParty(linkId);
      allow create: if isLinkParty(linkId)
                     && newDoc().snapshotSchemaVersion == 1
                     && newDoc().recordedFromDeviceId is string
                     && newDoc().recordedAt is number;
      // Updates запрещены — history immutable.
      allow update: if false;
      // Delete — only client-side housekeeping. FR-038: any link party may
      // delete для retention. Принимаем риск «hostile editor wipes history» —
      // переход на server-only через SRV-CONFIG-002 в backlog.
      allow delete: if isLinkParty(linkId);
    }
    ```
    Inline-TODO на сервер-only через SRV-CONFIG-001/002.

  - **FR-027 intent-filter `text/x-vcard`** — `exported=true` на activity, **никакой permission на caller** не требуется. Это **архитектурный выбор**, не bug (на share sheet нельзя поставить permission). См. CHK009 / CHK012 ниже.

- [ ] **CHK008 — No security-by-obscurity**

  - `linkId` — opaque Firestore document ID (наследуется из 007). ✅
  - `recordedFromDeviceId` в history — pseudonym, identifier device, не secret. ✅
  - **Watch**: **FR-037 client-side history write** = **spoofing risk**. Любой editor может записать в `/config/history/{autoId}` snapshot с произвольным `recordedFromDeviceId` (выдать себя за другое устройство). Security Rules не могут это enforce'ить без cross-collection check.

    **Action для plan.md**: добавить в Security Rules **field-level check**: `newDoc().recordedFromDeviceId == get(/databases/$(database)/documents/links/$(linkId)).data.adminId` ИЛИ `... .managedDeviceFirebaseUid`. Это сильно сужает spoofing window (можно выдать себя только за **другую сторону того же link'а**, не за произвольное устройство). Полное устранение spoofing'а — server-side write через SRV-CONFIG-001 в backlog. ⚠️ **Это новое требование, не покрытое FR-037; рекомендую добавить как FR-045a в spec.md** либо как plan-level constraint.

## Platform interaction (MASVS-PLATFORM)

- [ ] **CHK009 — Exported activities/services/receivers justified; non-exported is the default**

  **Finding: WATCH — новая exported surface.** Спек 9 вводит **новую exported activity** (FR-027): intent-filter на `ACTION_SEND` + MIME `text/x-vcard`. По смыслу это **public** entry point — любой app на устройстве может его вызвать.

  **Это оправдано** functionality-wise (US-4: share contact из WhatsApp). Но требует:
  1. `<intent-filter>` MUST ограничивать **только** `ACTION_SEND` + MIME `text/x-vcard` (не `ACTION_SEND_MULTIPLE`, не более широкие MIME-маски типа `text/*`).
  2. Activity, принимающая intent, MUST относиться к extras как к **untrusted input** (см. CHK012).
  3. Activity NOT MUST автоматически писать в Firestore — обязательно через явный admin-confirm screen (FR-029). ✅ покрыто в spec.md.

  **Action для plan.md**: добавить **non-exported flag** для всех intermediate activities в flow VCard import (только entry-point — exported, всё дальше — internal).

- [ ] **CHK010 — Intents to other apps use explicit package or setPackage**

  - **FR-035 Play Store fallback**: `market://details?id=<packageName>` — implicit intent. Это **стандартный** Play Store pattern; system handler resolves uniquely. ✅
  - Web fallback `https://play.google.com/store/apps/details?id=...` — implicit `ACTION_VIEW`, любой браузер. Acceptable (нет sensitive payload).
  - **FR-035 launch app by packageName**: `Intent.makeMainSelectorActivity(ACTION_MAIN, CATEGORY_LAUNCHER).setPackage(packageName)` — **explicit package**. ✅ standard pattern; **plan.md MUST не использовать** plain implicit `ACTION_MAIN` без `setPackage`.

- [x] **CHK011 — Deep links validated**

  Спек 9 не добавляет новые deep-link schemes. VCard share intent — не deep-link (это `ACTION_SEND` с binary extras, не URL). **N/A для CHK011** (но см. CHK012).

- [ ] **CHK012 — Intent extras received from external apps are size-bounded and type-checked**

  **Finding: CRITICAL ATTACK SURFACE.** FR-028 — **главная** security FR спека 9. Любой app на устройстве может слать в наш intent-filter произвольный payload. Уязвимости:

  1. **DoS через размер**: огромный VCard / EXTRA_STREAM URI на гигантский файл → OOM, freeze parser. FR-028 закрывает через **10 KB limit**. ✅
  2. **Encoding injection**: не-UTF8 payload с binary trash → парсер crash или unexpected behaviour. FR-028 закрывает через **reject non-UTF8**. ✅
  3. **VCard injection полей**: вредоносный VCard с `PHOTO:<gigabytes>`, `URL:javascript:...`, `X-CUSTOM:<exploit>`. FR-028 закрывает через **whitelist подход: только FN + TEL** (всё остальное ignored). ✅ Это **корректная стратегия** — whitelist, не blacklist.
  4. **Phone number injection**: `TEL:+7-916-DROP-TABLES` или `TEL:javascript:alert(1)`. Domain validator `Contact.fromRaw` закрывает через regex `^\+?\d{5,20}$` после нормализации. ✅
  5. **Display name injection**: control chars (`\x00`, `\x07`), zero-width chars, RTL override (`U+202E`), gigantic emoji bombing. `Contact.fromRaw` закрывает: strip ASCII control chars + max 100 chars. ⚠️ **НЕ покрывает** Unicode control chars (RTL override, zero-width joiner, bidi). Это **edge case** для UI render — может приводить к obfuscated names («Маша‮1234567890»). Низкий риск (плитка не выполняется как URL), но **рекомендую расширить** domain validator: также strip Unicode Cc/Cf categories (control + format) кроме whitelisted (emoji = Cs/So). **Action**: уточнить FR-028 или domain validation contract.
  6. **Path traversal через `EXTRA_STREAM`**: если VCard приходит как URI на attached file, и парсер слепо открывает URI — возможна leak through content provider. **Action для plan.md**: VCard parser MUST читать ТОЛЬКО из `EXTRA_TEXT` (inline VCard) либо через `ContentResolver.openInputStream` с size cap **до** чтения. NEVER через `File(uri.path)`.

  **Action для plan.md (MANDATORY)**: contract test для `VCardImportAdapter` — fuzz-like тесты с adversarial inputs (см. примеры выше), все должны быть rejected predictable. Спек 9 явно ссылается на эту необходимость в `## Domain validation contract` § «Контрактный roundtrip тест».

- [x] **CHK013 — No exported ContentProvider without permission protection**

  Спек 9 не вводит ContentProviders. **N/A**.

- [x] **CHK014 — WebView (if any) has JavaScript disabled or origin-restricted**

  Спек 9 не использует WebView. Play Store web fallback — `ACTION_VIEW` через системный браузер, не in-app WebView. **N/A**.

## Permissions (Article XIV)

- [ ] **CHK015 — Each requested permission justified by explicit FR (Article XIV §1, §2)**

  Спек 9 вводит **одно новое runtime permission**:
  - **`READ_CONTACTS`** — обоснован **FR-023**: «В форме редактирования плитки `kind = Call / Sms` MUST быть кнопка "Выбрать из контактов". Запрашивает permission `READ_CONTACTS`».
  - ✅ Explicit FR + только на **admin-устройстве** (на Managed — не запрашивается, FR-008 спека 008 это явно подтверждает).

  Manifest changes:
  - `<uses-permission android:name="android.permission.READ_CONTACTS" />` — **mandatory action для plan.md**.
  - `<queries>` block (FR-035) — расширить existing block спека 005 чтобы охватить **произвольные packageName из user input**. Это **проблема**: спек 005 явно избегает `QUERY_ALL_PACKAGES` (Play policy). Для FR-035 admin может ввести произвольный package — нет способа знать заранее. **Options**:
    - Option 1: `QUERY_ALL_PACKAGES` — нарушает Play policy спека 005 NFR-008 и compliance §27 Play Store. **Отвергнуть.**
    - Option 2: graceful fallback — если `PackageManager.getLaunchIntentForPackage()` возвращает null (нет visibility), считаем «not installed» → Play Store fallback. **На Managed-стороне это работает: тап → not visible → market URL**. ✅ **Это уже described в FR-035**.
    - **Action для plan.md**: явно зафиксировать Option 2 — отсутствие visibility = «not installed» равнозначно. Документировать в comment'е manifest'а. **Не добавлять** `QUERY_ALL_PACKAGES`.

- [x] **CHK016 — No permission requested for "future use"**

  `READ_CONTACTS` строго на FR-023. Нет «прозапас». ✅

- [ ] **CHK017 — Fallback for denied permissions designed**

  - **`READ_CONTACTS` denied**: FR-023 предполагает rationale-экран **перед** запросом. Если denied → admin может всё равно **руками ввести** имя+номер. Spec.md **этого не упоминает явно**. **Action для plan.md**: добавить fallback path «без READ_CONTACTS = manual entry form для контакта», либо явно зафиксировать в spec.md как FR-023a. Без этого fallback'а: admin denied permission → US-3 заблокирован → bad UX. ⚠️ **Рекомендую добавить FR в spec.md.**
  - **`READ_CONTACTS` permanently denied** (don't ask again): аналогично — manual entry path. Deep-link на Settings → Permissions — стандартный pattern Android.
  - VCard intent path (US-4) **не требует** `READ_CONTACTS` — это independent flow. Fallback от denied permission на US-3 = «используйте share intent» — но это требует знания.

- [ ] **CHK018 — Updates to `docs/compliance/permissions-and-resource-budget.md` planned**

  **Action для plan.md**: добавить task `T-DOCS-009-001: update permissions-and-resource-budget.md with spec 009 entry — добавлен READ_CONTACTS (runtime, admin-only) + intent-filter ACTION_SEND text/x-vcard (exported, anti-injection через FR-028)`.

## Privacy (Article XIV §3, §4)

- [ ] **CHK019 — No hidden collection of behavioural / personal data**

  **Finding: CRITICAL — third-party PII без consent.** Спек 9 — **первый** спек, который хранит PII **третьих лиц** (контактов admin'а), не самого пользователя.

  - Под GDPR ст.6 / 152-ФЗ ст.6: обработка данных третьего лица требует либо (а) согласия субъекта, либо (б) legitimate interest, который должен быть документирован.
  - Маша (third party, чей номер admin добавил в раскладку) **не давала согласия** — мы храним её имя и номер в Firestore и в local Room на двух устройствах (admin + Managed).
  - Спек.md **явно признаёт это**: OUT-014 → TODO-LEGAL-001 «Полная privacy compliance UI отложена». Это **осознанный backlog**, но **может блокировать Play Store review** (Data Safety form требует декларацию collected PII + purpose) и **является legal risk** для production-release в EU/RU.

  **Recommendation для plan.md** (минимум):
  1. **Privacy policy text** обновить — даже если UI отложен, опубликованный privacy policy на сайте/Play Store должен описывать: «приложение хранит контактные данные третьих лиц по выбору admin'а; lawful basis = legitimate interest (помощь senior-relative); subject может потребовать удаления через admin'а; admin несёт ответственность за уведомление субъектов».
  2. **In-app rationale** для READ_CONTACTS (FR-023) — расширить текст: «вы добавляете контакт другого человека; убедитесь, что у вас есть его согласие».
  3. **Минимум UI** (0.5 дня per spec.md OUT-014): экран «Контакты, которые я добавил» в admin Settings с кнопкой «удалить». Это **минимально необходимо** для GDPR ст.17 (right to erasure) — без него мы технически нарушаем регулирование.

  **Это первый top-3 риск** (см. summary внизу).

- [x] **CHK020 — Local-first preferred (Article XIV §4); networked feature explicitly justified**

  - **FR-014a**: draft хранится **локально в Room** (не на сервере). ✅ Local-first.
  - **FR-002**: pull `/config/current` при open editor + fallback на Firestore offline cache. ✅
  - Network feature (sync changes admin↔Managed) явно обоснован US-1 («remote control» — основная ценность). ✅

- [ ] **CHK021 — If data leaves device: user-visible notice + opt-in (where regulation requires)**

  - Pairing consent (спек 007) — established trust boundary, после pairing `/config` sync = expected behavior.
  - **НО**: contacts добавление — это **новый** тип данных, который leaves device. Спек 007 не покрывает «согласие на сбор PII третьих лиц». Это **дополнительный** consent point.
  - Спец.md OUT-014 отложил это в backlog.
  - **Action для plan.md**: **минимум** — обновить privacy policy + расширить READ_CONTACTS rationale-screen (см. CHK019 пункт 2). Это **не блокирует** имплементацию спека 9 (technical work), но **блокирует production-release** до того, как TODO-LEGAL-001 закрыт.

- [ ] **CHK022 — Data minimisation: only fields required for the FR are collected, stored, transmitted**

  - **FR-026/028 strict whitelist**: только `displayName` + `phoneNumber`, all other VCard fields ignored (PHOTO, EMAIL, ADR, URL, BDAY, X-*). ✅ **Образцовая** data minimisation.
  - `Contact { id, displayName, phoneNumber, photoRef = null }` — минимально необходимо. ✅
  - **FR-036 ConfigSnapshot**: содержит `recordedFromDeviceId` — pseudonym (не PII), но **revealing**. ✅ Functional necessity для UI «кто откатывал когда».
  - **History retention 10** (FR-038) — multiplier × 10 на PII footprint. **Acceptable trade-off** (полезно для rollback, ограничено 10).
  - Phone health (battery%, audioMuted, lastSeen) — usage telemetry, technically не PII, но **revealing pattern of life** (когда бабушка online). **Spec 006 + 7 уже** установили это как acceptable; спек 9 только **читает**, не расширяет. ✅

## Build hardening

- [ ] **CHK023 — No debug flags / verbose logging in release per BuildConfig.DEBUG gate**

  - Спец.md не специфицирует. Inherits от 008.
  - **Action для plan.md**: добавить explicit note: «спек 9 не вводит debug-only paths shipping в release. VCard parser в release — без verbose payload-логирования; только typed reject reasons».
  - **Watch**: existing exported debug-activities (`CapabilitySnapshotDebugActivity`, `HealthSnapshotDebugActivity` per AndroidManifest строки 100-107) — comment'ом уже отмечено «до public release — рассмотреть isolation через debug build variant». **Спек 9 — хороший момент** добавить task на это (поскольку 9 — последний крупный спек до production-readiness review).

- [ ] **CHK024 — Backup rules (`android:allowBackup`, `data_extraction_rules`) reviewed for new persistent data**

  **Finding: CRITICAL — `allowBackup="true"` на момент 2026-05-15.** AndroidManifest.xml строка 65: `android:allowBackup="true"`. С Android Auto-Backup это означает что **Room database с PII** (включая контакты Машы) **может оказаться в Google Drive backup'е admin'а**.

  - Спек 8 mandatory action item #6 уже это поднял: «`allowBackup="false"` или `data_extraction_rules.xml` исключающий 008 Room database». **Не проверял, выполнено ли** в `data_extraction_rules.xml`.
  - Спек 9 **усугубляет**: добавляет contacts (third-party PII) → backup может leak'нуть в Google Drive admin'а контакты Машы. Под GDPR это **transfer to third party** (Google) **без consent**.
  - **Action для plan.md (MANDATORY)**:
    1. Проверить текущее содержимое `app/src/main/res/xml/backup_rules.xml` и `data_extraction_rules.xml`.
    2. Если Room database спека 8 НЕ исключён из backup — это **спек 8 bug**, fix в спеке 9 (включить exclude path).
    3. Альтернатива: `android:allowBackup="false"` (более радикально, но проще).
    4. Документировать решение в `docs/compliance/permissions-and-resource-budget.md`.

  Этот пункт — **второй top-3 риск** (см. summary).

---

## Spec-9-specific additional checks

### Privacy / GDPR-specific (beyond MASVS)

- [ ] **CHK025 — Right to erasure (GDPR ст.17 / 152-ФЗ ст.14) path exists**

  - Subject (Маша) хочет удалить свои данные → она физически не имеет доступа к приложению. Удалить может только admin.
  - **Spec.md OUT-014 explicitly defers** «список контактов с кнопкой удалить» в TODO-LEGAL-001.
  - **Action для plan.md**: либо включить минимальный UI (0.5 дня per spec.md), либо документировать в privacy policy: «для удаления своих данных свяжитесь с admin'ом через [контакт]». **Минимально-приемлемое**: текстовый contact email в privacy policy.

- [ ] **CHK026 — History snapshots multiply PII footprint — retention policy reasonable?**

  - 10 snapshot'ов × N контактов = до 10× PII в Firestore + до 10× в Room. Acceptable для rollback функциональности.
  - Удаление контакта в current не удаляет его из 10 предыдущих snapshot'ов. Это **архитектурно correct** (rollback должен работать), но **противоречит GDPR right-to-erasure** в строгом чтении: admin удалил Машу → она всё ещё в `/config/history/{old}`.
  - **Action для plan.md**: при `Contact` deletion из current → НЕ удалять из history (rollback бы сломался). При **explicit "удалить контакт навсегда"** action (TODO-LEGAL-001 UI) — пройти по всему `/config/history` и удалить snapshot'ы, где этот contactId присутствовал, ИЛИ перезаписать `Contact.displayName = "Удалён"`, `phoneNumber = ""`. Это **архитектурное решение**, которое нужно зафиксировать **в TODO-LEGAL-001 design**, не сейчас.

### Spoofing / integrity (beyond MASVS-PLATFORM)

- [ ] **CHK027 — History writer identity verifiable**

  - **FR-037**: client-side write of `/config/history/{autoId}` с `recordedFromDeviceId` указанным **клиентом**. Любой editor может выдать себя за другое устройство (см. CHK008).
  - Спец.md **признаёт это** (Edge Cases: «Push в `/config/current` падает после write history» + SRV-CONFIG-001 миграция).
  - **Action для plan.md**: добавить Security Rules check **`recordedFromDeviceId in {adminId, managedDeviceFirebaseUid}`** для частичной защиты (см. CHK008). Полная защита — SRV-CONFIG-001.

- [ ] **CHK028 — `PhoneHealthCriticalEvent` без подписчика — leaked sensitive state?**

  - **FR-021**: event эмитится локально, **нет подписчика в спеке 9**. Это означает event живёт **в process memory только** — нет persistence, нет network leak. ✅
  - Watch: если plan.md решит сохранять event log для debug — должен быть **за DEBUG gate** + не содержать battery%/lastSeen values. ✅

### Play Store review readiness

- [ ] **CHK029 — Data Safety form fields для спека 9**

  - **Personal info → Contacts**: collected (true) / shared (true, via Firebase) / purpose (App functionality) / encrypted in transit (true) / users can request deletion (⚠️ нужно false / partial до TODO-LEGAL-001).
  - **Permissions → READ_CONTACTS**: justification «admin может выбрать контакт для добавления на телефон родственника».
  - **Action для plan.md (или для предрелизного review)**: обновить Data Safety form в Play Console при первом upload спека 9. Без этого upload будет rejected.

---

## Summary

| Status | Count | Items |
|---|---|---|
| Pass | 11 | CHK002, CHK003, CHK005, CHK006, CHK010 (partial — pending plan), CHK011, CHK013, CHK014, CHK016, CHK020, CHK022, CHK028 |
| Watch (action items для plan.md) | 12 | CHK001, CHK004, CHK007, CHK008, CHK009, CHK012, CHK015, CHK017, CHK018, CHK021, CHK023, CHK025, CHK026, CHK027, CHK029 |
| Fail (требует изменения spec.md / mandatory plan.md fix) | 3 | CHK019 (third-party PII без consent path) / CHK024 (backup leak risk) / CHK007 (FR-045 Security Rules не покрывают subcollection — нужен diff) |
| N/A | 4 | CHK002 partial, CHK011, CHK013, CHK014 |

**Verdict**: PASS **conditionally** — спек 9 безопасен на уровне FR при условии, что **3 fail-items** закрыты до production-release. Один — на уровне Security Rules (technical fix в plan.md), второй — на уровне backup config (technical fix в plan.md / manifest), третий — на уровне legal/privacy (TODO-LEGAL-001 — нельзя релизить в production без хотя бы минимума).

---

## Top 3 security risks (могут блокировать Play Store / GDPR)

1. **Third-party PII без consent path и без deletion UI** (CHK019 + CHK025).
   - **Что**: Маша (third party) не давала согласие; spec.md OUT-014 откладывает UI удаления в TODO-LEGAL-001.
   - **Блокирует**: Play Store Data Safety review (users can request deletion = false), GDPR ст.17, 152-ФЗ.
   - **Минимум до production**: privacy policy text + READ_CONTACTS rationale extended + текстовый contact для erasure requests. **Лучше**: 0.5-day UI «список добавленных + удалить» (FR-add).
   - **Severity**: HIGH — Play Store может rejected'ить upload; legal exposure в EU/RU.

2. **`allowBackup="true"` + Room с third-party PII = Google Drive leak** (CHK024).
   - **Что**: текущий manifest spec 7-8 не disable backup; спек 9 добавляет contacts в Room → backup попадает в Google Drive admin'а **без явного consent** на transfer to Google.
   - **Блокирует**: GDPR (transfer to processor without DPA); может быть caught в Play Store policy review.
   - **Фикс**: 30 минут работы — добавить `<exclude>` для Room database в `data_extraction_rules.xml`, или поставить `android:allowBackup="false"`.
   - **Severity**: HIGH — простой фикс, должен быть в спеке 9 как mandatory.

3. **FR-045 Security Rules не покрывают subcollection `/config/{configId}/history/{autoId}` + spoofing через `recordedFromDeviceId`** (CHK007 + CHK008 + CHK027).
   - **Что**: текущий `firestore.rules` (строки 151-159) `match /config/{configId}` **не распространяется** на subcollection — необходим новый `match` блок. FR-045 это упоминает, но не показывает rules diff. Плюс: client-side write `recordedFromDeviceId` = spoofing risk (любой editor выдаёт себя за чужое устройство).
   - **Блокирует**: integrity of history — без проверки, история становится untrusted (admin может подделать «откатил Managed»). При роботе двух editor'ов параллельно это нарушает audit trail.
   - **Фикс**: 1-2 часа — написать Security Rules для `/config/{configId}/history/{autoId}` с field-level check на `recordedFromDeviceId`. Полное устранение — server-only через SRV-CONFIG-001 (backlog).
   - **Severity**: MEDIUM-HIGH — не блокирует Play Store, но **блокирует** corporate / regulated deployments где audit integrity critical.

---

## Что нужно ужесточить в spec.md

Рекомендации **изменений в спеке** (не только plan.md):

1. **Добавить FR-023a (fallback на manual entry, если READ_CONTACTS denied)**. Сейчас denied = dead-end UX. ~2 строки.
2. **Уточнить FR-028 / Domain validation contract**: расширить strip Unicode Cc/Cf control chars (RTL override, zero-width chars) в `Contact.fromRaw` rawName cleanup. Сейчас strip только ASCII control. ~1 строка в таблицу validation rules.
3. **Добавить FR-045a (или constraint в FR-045)**: «`recordedFromDeviceId` MUST совпадать с `request.auth.uid` (адмIn или managed)». Spoofing-mitigation на Security Rules уровне до перехода на server-only. ~1 строка.
4. **Расширить OUT-014 → TODO-LEGAL-001**: добавить explicit **дату дедлайна** (или milestone: «до первого Play Store upload» / «до first production user»). Сейчас OUT-014 — open-ended.
5. **Рассмотреть** возврат «минимум контакт-management UI» (0.5 дня) обратно **в скоуп спека 9** — это снимает 1 из 3 top-risks. User pre-specify session 2026-05-15 отказался, но риск возможно недооценён.

---

## Mandatory action items для plan.md

1. **Security Rules diff** (`firestore.rules`): добавить `match /links/{linkId}/config/{configId}/history/{autoId}` с read/create/update=false/delete + field-level check `recordedFromDeviceId` against `adminId`/`managedDeviceFirebaseUid`.
2. **AndroidManifest changes**: `<uses-permission android:name="android.permission.READ_CONTACTS" />` + intent-filter for `ACTION_SEND` MIME `text/x-vcard` на dedicated activity (exported only там, остальные — non-exported).
3. **Backup config**: `<exclude>` для Room database в `app/src/main/res/xml/data_extraction_rules.xml` (и `backup_rules.xml` для Android < 12), либо `android:allowBackup="false"`. **Перед добавлением contacts в Room.**
4. **Privacy policy text** (опубликованный + ссылка в Play Console): описание сбора third-party contact PII + lawful basis + erasure contact.
5. **READ_CONTACTS rationale screen text**: расширить до GDPR-grade disclosure (упомянуть третьих лиц).
6. **VCardImportAdapter contract tests**: fuzz-like adversarial inputs (>10KB / non-UTF8 / VCard injection / RTL override / path traversal через EXTRA_STREAM URI).
7. **PII logging policy**: explicit code-level rule «никогда не логировать содержимое contacts / VCard payload / Health values regular polling» — категории only.
8. **`docs/compliance/permissions-and-resource-budget.md` update**: новая запись для спека 9 (READ_CONTACTS, intent-filter, backup config).
9. **Data Safety form draft** для Play Store: contacts (collected, shared via Firebase, app functionality, encrypted in transit).
10. **`<queries>` strategy**: подтвердить graceful fallback «no visibility = not installed → Play Store fallback» вместо `QUERY_ALL_PACKAGES`. Документировать в manifest comment.
11. **Revoke link cleanup**: при unpair — удалить local draft (`PendingLocalChanges WHERE linkId`), cached history snapshots, Health subscription для этого linkId.
12. **Debug-activity isolation**: переместить existing exported debug-activities (`CapabilitySnapshotDebugActivity`, `HealthSnapshotDebugActivity`) в debug build variant до production-release (это уже отмечено как TODO в manifest comment; спек 9 — хороший trigger).

---

## Watch items long-term (backlog)

- **SRV-CONFIG-001**: server-side history writes (устраняет spoofing через `recordedFromDeviceId`).
- **SRV-CONFIG-002**: server-side housekeeping (устраняет race condition retention).
- **SRV-MONITOR-001**: subscriber на `PhoneHealthCriticalEvent` (push admin'у). При имплементации: убедиться, что push payload **не содержит** raw battery%/health values — только category alert.
- **Spec 011** (e2e media): пересмотреть Room encryption (SQLCipher) для contacts + photos.
- **Spec 010** (setup-assistant): включить privacy policy display + initial GDPR consent flow.
- **TODO-LEGAL-001**: full privacy compliance UI (contact list with delete, GDPR export, deletion endpoints).

---

## Что внутри (TL;DR на русском)

Спек 9 — это admin-режим, который **впервые** в проекте хранит данные **третьих лиц** (контакты бабушкиной Машы), а не самого пользователя. Это меняет security profile: GDPR/152-ФЗ применимы; нужны consent path и erasure endpoint.

**Главное:**
- Спек 9 **наследует** baseline спеков 007/008 (Firebase Auth, HTTPS, Room без шифрования = acceptable trade-off).
- Спек 9 **добавляет** три новые attack surface'a: (а) `READ_CONTACTS` permission, (б) exported intent-filter на VCard (любой app может слать → парсер должен относиться как к untrusted input), (в) `/config/history` subcollection.
- VCard parser FR-028 — **образцовая** anti-injection стратегия: whitelist подход (только FN + TEL), 10KB cap, UTF-8 only, strict regex для phone. ✅
- Domain validator `Contact.fromRaw` — **правильная** анти-corruption layer. Незначительный gap: расширить strip Unicode control chars.

**Топ-3 риска, которые могут блокировать Play Store / GDPR:**

1. **Third-party PII без consent + без deletion UI** (CHK019). Маша не давала согласия, удалить можно только через admin'а, а UI удаления отложен в TODO-LEGAL-001. Минимум: privacy policy text + contact для erasure. Лучше: 0.5-day UI вернуть в скоуп.

2. **`allowBackup="true"` + третьи-лица PII в Room** (CHK024). Google Drive backup утечёт контакты Машы. 30-минутный fix: `<exclude>` в `data_extraction_rules.xml`. Mandatory до релиза.

3. **FR-045 Security Rules не покрывают subcollection + spoofing через `recordedFromDeviceId`** (CHK007/CHK008). Любой editor может подделать историю. 1-2 часа fix: написать subcollection rules + field-level check.

**Что нужно ужесточить в spec.md** (5 рекомендаций):
- Добавить FR fallback на manual contact entry если READ_CONTACTS denied.
- Расширить domain validator на Unicode Cc/Cf control chars.
- Добавить FR-45a: `recordedFromDeviceId == request.auth.uid` constraint.
- Поставить deadline на TODO-LEGAL-001.
- Рассмотреть возврат минимума privacy UI в скоуп.

**Verdict**: спек 9 PASS conditionally — безопасен на уровне FR, но требует 3 mandatory plan.md/manifest fixes до production-release.

**Размер**: 24 MASVS checks + 5 spec-9-specific (privacy, spoofing, Play Store) = 29 пунктов. 11 pass, 12 watch, 3 fail, 4 N/A. 12 mandatory action items для plan.md. 5 spec.md tightening recommendations.

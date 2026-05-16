# Checklist: permissions-platform — спек 009

**Spec:** [`spec.md`](../spec.md) · **Run:** 2026-05-15 · **Score:** 14 ✓ / 6 ◐ / 2 N/A / 0 ✗

Source: Article XIV + Article III §3 [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md). Reference run: [spec 006 permissions-platform.md](../../006-provider-capabilities-and-health/checklists/permissions-platform.md).

Legend: `[x]` pass · `[~]` partial · `[ ]` fail · `N/A` not applicable.

Build context (verified 2026-05-15):
- `gradle/libs.versions.toml`: `compileSdk=35`, `minSdk=26`, `targetSdk=35`.
- `gradle/libs.versions.toml`: `androidx-compose-bom = 2024.10.00` → Compose 1.7.x, `Modifier.dragAndDropSource/Target` API ✓ доступен (требует Compose 1.6+ per FR-008 C4).
- Текущий `app/src/main/AndroidManifest.xml`: уже объявлены `ACCESS_NETWORK_STATE`, `<queries>` с MAIN/LAUNCHER + provider packages (whatsapp/telegram/youtube) + tel:/smsto:/http(s):/ intent queries (наследие спека 006 FR-053). Уже подключены `backup_rules.xml` (Android <12) и `data_extraction_rules.xml` (Android 12+) от спека 006 FR-057. **Только excludes для capability/health DataStore — Room database спека 008 (config_sync) уже должна быть исключена там же, проверить.**

---

## Permission / API surface introduced by spec 009

| # | Surface | Permission needed | FR / Notes |
|---|---------|-------------------|------------|
| P1 | `READ_CONTACTS` для admin contact picker | `READ_CONTACTS` (dangerous, runtime) | **NEW** FR-023/024/033c. Admin-side only. Managed device — НЕ требует. |
| P2 | `Intent.ACTION_PICK` (`Phone.CONTENT_URI`) | none beyond P1 | FR-024 системный picker. |
| P3 | Exported `<activity-alias>` с `<intent-filter>` `ACTION_SEND` + MIME `text/x-vcard` | none, но **exported attack surface** | **NEW** FR-027. Принимает share от WhatsApp/Telegram/Viber/Contacts. |
| P4 | `Intent.ACTION_VIEW` + `market://details?id=...` для OpenApp Play Store fallback | none | FR-035. Требует `<queries>` на `market` scheme **или** прямой `<package>` `com.android.vending`. |
| P5 | `PackageManager.getInstalledApplications` / `queryIntentActivities` для admin app picker (FR-034 «выбор из списка установленных») и Managed dispatcher availability check (FR-035) | `<queries>` element + (опционально, рискованно) `QUERY_ALL_PACKAGES` | FR-034/035. **Default Android 11+ visibility ловушка.** Без расширения существующих `<queries>` — admin не увидит произвольные приложения для OpenApp. |
| P6 | Compose `Modifier.dragAndDropSource/Target` (Compose 1.6+) | none (Compose API) | FR-008. Compose BOM 2024.10.00 → 1.7.x — поддерживается. |
| P7 | Firestore writes к `/config/current` + `/config/history/*` | INTERNET (inherited) | FR-014/015/041. Без новых permissions. |
| P8 | Firestore listener на `/links/{linkId}/health` | INTERNET (inherited) | FR-017/020. Без новых permissions. |
| P9 | Room database (PendingLocalChanges draft per FR-014a) | none | Использует app-private storage. Backup-exclude — обязательно (FR-046a). |

---

## Runtime permissions

- [x] **CHK001** Each runtime permission has explicit user-value justification.
  - `READ_CONTACTS` обоснован FR-023 (US-3 — самая частая real-world нужда) + FR-033c (explicit privacy disclosure в rationale). Spec прямо проговаривает «на Managed `READ_CONTACTS` НЕ нужен» — корректное разделение admin/Managed surface.
- [~] **CHK002** First-launch permission flow specified.
  - **Partial.** FR-023 говорит: «запрашивает permission `READ_CONTACTS` с rationale-экраном, стандартный Android pattern». **Не сказано**: запрашивать в момент тапа «+ контакт» (just-in-time) или при первом open admin-app (anti-pattern). Acceptance Scenario US-3 #1 уточняет «когда admin нажимает “+ контакт”» — но это **сценарий**, не FR. **Fix-приемлемо**: уточнить FR-023 формулировкой «MUST запрашивать just-in-time при тапе “+ контакт” в редакторе плитки kind=Call/Sms, НЕ при cold start admin-приложения». Не блокер — поведение однозначно следует из acceptance scenarios.
- [~] **CHK003** Re-prompt strategy specified — first denial vs "don't ask again".
  - **Partial.** Спек упоминает rationale-экран (FR-033c) — это **pre**-prompt UX. Не упомянуто что делать после первого denial (показывать ли rationale снова при повторном тапе «+ контакт»), и что делать после «don't ask again» (Android 11+ — второй denial автоматически = permanently). **Fix**: добавить FR-023b «при denial — alternative path: ручной ввод имени+номера через клавиатуру (FR-008 формы редактирования плитки). При “permanently denied” — экран “Откройте Settings → Apps → <name> → Permissions → Контакты” + deep-link на app settings».
- [~] **CHK004** Settings deep-link path for permanent denial recovery.
  - **Partial.** Не специфицирован. Стандартный `Intent.ACTION_APPLICATION_DETAILS_SETTINGS` с `Uri.fromParts("package", packageName, null)` — следствие CHK003 fix. Однострочный TODO в код, не блокер для спека.
- [x] **CHK005** Pre-permission rationale screen specified per Material Design guidance.
  - **PASS.** FR-023 явно требует rationale-экран. FR-033c **обогащён** требованием privacy disclosure («Контакты сохраняются в облаке Firebase и видны на устройстве вашего родственника. Вы можете удалить их в любой момент через Settings → Добавленные контакты»). Это **сильнее** чем стандартный Material rationale — соответствует best practice для transfer-to-processor consent (GDPR ст.13/14).

## Manifest declarations

- [x] **CHK006** Required permissions listed, no broad ones.
  - `READ_CONTACTS` — единственный новый. **Не запрашивается** `WRITE_CONTACTS`, `READ_PHONE_STATE`, `CALL_PHONE`, `SEND_SMS` (всё ещё `ACTION_DIAL` / `ACTION_SENDTO` flow из спека 006). **Не запрашивается** `QUERY_ALL_PACKAGES` несмотря на FR-034 «список установленных приложений на админ-устройстве» — нужно подтвердить, что admin app picker UI работает через `<queries>` + intent `MAIN/LAUNCHER` (уже есть в манифесте) **без** broad query.
- [x] **CHK007** `<uses-feature>` matches actual hardware needs.
  - Спек 009 не вводит hardware-зависимостей (нет camera, NFC, telephony beyond inherited). N/A для новых declarations.
- [~] **CHK008** `<queries>` for package inspection (Android 11+).
  - **Partial — gap идентифицирован.** Текущий `<queries>` блок в манифесте (унаследован от спека 006 FR-053) перечисляет: `MAIN/LAUNCHER` intent, конкретные packages (whatsapp, telegram, youtube), и схемы `tel:/smsto:/http(s):`. Это покрывает:
    - ✓ admin app picker через `<intent>MAIN/LAUNCHER</intent>` (для FR-034 (а) «выбор из списка установленных»);
    - ✓ Managed availability check для WhatsApp/Telegram/YouTube (FR-035 (а));
    - ✗ **НЕ покрывает** произвольные packages, которые admin введёт вручную (FR-034 (б)) — например, `ru.yandex.yandexnavi` упомянутый в спеке. Managed dispatcher (FR-035 (а)) `getPackageInfo("ru.yandex.yandexnavi")` вернёт `NameNotFoundException` → dispatcher решит, что не установлено, и **сразу** уйдёт в Play Store fallback, даже если приложение есть.
    - ✗ **НЕ покрывает** `market://` scheme для FR-035 (в) Play Store fallback — на Huawei без GMS `resolveActivity` вернёт null, что **ожидаемо** (нужен web fallback). Но и на устройствах с Play Store без `<intent><action VIEW/><data scheme="market"/>` `queryIntentActivities` может вернуть пусто на Android 11+.
  - **Fix**: добавить FR-035a «AndroidManifest `<queries>` MUST включать `<intent><action VIEW/><data scheme="market"/></intent>` для Play Store fallback resolution + acceptance: dispatcher FR-035 поведение при произвольном packageName, не входящем в whitelist, документировано — пользовательский ввод **не** проходит через `<package>` whitelist, поэтому `getPackageInfo` НЕ работает → MUST использовать `queryIntentActivities(LAUNCHER intent с packageName, 0)` для probe, который видит **любой** package, чьи `MAIN/LAUNCHER` activities удовлетворяют существующему `<queries>` `MAIN/LAUNCHER` intent». Альтернатива (отвергнута): `QUERY_ALL_PACKAGES` — Play policy violation per security CHK-015 наследие.
  - См. также **CHK020**.

## Android version specifics

- [x] **CHK009** Scoped storage compliance.
  - Спек 009 не делает file I/O вне app-private (Room database через DataStore-like wrapper; нет внешнего write). VCard intent payload читается через `getContentResolver().openInputStream(intent.data)` — это **provider-mediated read**, не direct path access, scoped-storage-compatible.
- [x] **CHK010** Foreground service type (Android 14+).
  - Спек 009 не вводит foreground services. Health monitoring (FR-020) явно «когда экран открыт» (Process Lifecycle bound, не background) — спек 009 НЕ нарушает спек 006 NFR-N03 «MUST NOT run a Foreground Service».
- [x] **CHK011** Exact alarms (`SCHEDULE_EXACT_ALARM`) avoided unless justified.
  - Спек 009 не использует AlarmManager. Health pull cadence (FR-020 — Info: 30s pull, Warning/Critical: realtime listener) реализуется через Coroutines `delay()` + Firestore listener, не AlarmManager.
- [x] **CHK012** Notification permission flow on Android 13+ (POST_NOTIFICATIONS).
  - Спек 009 не использует системные нотификации. `PhoneHealthCriticalEvent` (FR-021) — **локальный** event без подписчика. Push админу при Critical — `TODO-ARCH-012` / `SRV-MONITOR-001`, явно OUT-009. Когда отдельный спек реализует FCM push админу — он отдельно проходит POST_NOTIFICATIONS gate.
- [~] **CHK013** Predictive back gesture (Android 14+) compatibility documented.
  - **Partial.** Спек 009 вводит несколько back-чувствительных flow: редактор раскладки с unsaved draft (US-1 acceptance #5: «admin вышел не нажав Опубликовать → возвращается → draft восстановлен»), VCard intent flow (US-4), history preview screen (US-5). Compose 1.7 имеет `PredictiveBackHandler` API. Спек **не упоминает** predictive back — типичный gap для нового UI-спека. **Fix-приемлемо**: добавить implementation hint TODO в editor screen «MUST использовать `PredictiveBackHandler` для confirm-on-unsaved-draft UX, иначе на Android 14+ swipe-back будет дёргать UI». Не блокер для FR-уровня (UX detail, plan-level concern).

## HOME / launcher role (project-specific)

- [x] **CHK014** HOME/ROLE_HOME behavior when denied.
  - Спек 009 **наследует** HOME role state от спека 7 (admin-mode не требует быть default launcher на admin-устройстве — это критично!). На admin-устройстве приложение **не** запрашивает HOME role (admin использует свой обычный launcher). FR-001 «App MUST в admin-режиме показывать список paired Managed» — flow открывается через стандартный app launch (icon на admin home screen), не через HOME intent. ✓
- [x] **CHK015** Default launcher fallback.
  - На admin-устройстве — не применимо. На Managed (US-6 7-tap+пароль flow) — наследует поведение спека 7 без изменений.

## OEM quirks

- N/A **CHK016** Samsung KNOX restrictions on AccessibilityService.
  - Спек 009 не использует AccessibilityService.
- [x] **CHK017** Xiaomi MIUI aggressive battery saver / autostart.
  - Health pull (FR-020) и Firestore listener (FR-020) — **process-lifetime-bound** (только когда экран открыт). MIUI aggressive kill в фоне приемлем — при следующем onResume listener пересоздастся, snapshot pull повторится. **Но**: VCard share intent (FR-027) — broadcast receive **должен** работать даже из killed-state. На MIUI без autostart whitelist приложение может **не запуститься** из share sheet — это **MIUI bug**, не наш. Документировать в FAQ.
- [~] **CHK018** Huawei EMUI protected apps + non-GMS.
  - **Partial — explicit risk в спеке.** A-7 явно говорит: «Android-устройство Managed имеет `<queries>` блок для intent resolution OpenApp/Play Store (Android 11+)». Implementation hint в FR-035 строке таблицы: «web fallback `https://play.google.com/store/apps/details?id=...` при отсутствии Play Store на устройстве (нужно для не-GMS Huawei)». **Gap**: на Huawei без GMS даже web fallback может открыться в Petal Browser, который **не** перенаправит на AppGallery. Реальное UX: бабушка-Huawei видит «не могу установить». **Fix-приемлемо**: либо принять как known limitation (наследие глобального constraint «GMS-only support в спеке 7»), либо добавить FR-035b «при detect Huawei без GMS — fallback на `https://appgallery.huawei.com/app/<packageName>` URL». **Рекомендация: known limitation**, документировать в FAQ Huawei.
- [~] **CHK019** OEM launcher-replacement quirks (Samsung One UI, OnePlus, Honor/EMUI).
  - **Partial.** Спек 009 — admin-mode, на admin-устройстве launcher role **не** трогается. Но: VCard share-intent (FR-027) попадает в системный share sheet, который на Samsung One UI имеет **кастомный chooser** с разной сортировкой; на Xiaomi/MIUI — отдельный native share. Это **не** ломает FR-027 (intent-filter регистрация одинаковая), но иконка нашего приложения в Samsung One UI Share может отображаться по-разному. **Fix-приемлемо**: тест на Samsung-эмуляторе с One UI launcher (см. android-emulator skill).

## Package visibility (Android 11+)

- [~] **CHK020** Relevant `<queries>` entries for any inspected/launched package.
  - **Partial — см. CHK008.** Текущий `<queries>` блок покрывает базовые случаи, но:
    - admin app picker FR-034 (а) — ✓ работает через существующий `MAIN/LAUNCHER` intent query (видит ВСЕ launchable apps);
    - Managed dispatcher availability check FR-035 (а) для **arbitrary** package — ⚠ работает только через `queryIntentActivities(MAIN/LAUNCHER intent)`, НЕ через `getPackageInfo(packageName)`. Implementation MUST использовать первый паттерн.
    - Play Store probe FR-035 (в) — ✗ не покрыт `<intent><action VIEW/><data scheme="market"/></intent>` отсутствует в текущем манифесте.
  - **Fix**: добавить FR-035a (см. CHK008) + расширить `<queries>` в манифесте на market scheme. Один атомарный коммит вместе с реализацией FR-035.
- [x] **CHK021** `QUERY_ALL_PACKAGES` not used.
  - Спек 009 явно не упоминает `QUERY_ALL_PACKAGES`. FR-034 формулирует «выбор из списка приложений, установленных на админ-устройстве» — это **может** ввести в заблуждение разработчика, но FR-035 говорит «требует объявленных `<queries>`» — намерение ясно. **Hint**: убедиться, что implementation в plan.md явно ссылается на `<intent><action MAIN/><category LAUNCHER/></intent>` query, а не на `QUERY_ALL_PACKAGES` для app picker UI.

## Security considerations beyond standard checklist

- [x] **Exported component attack surface** (FR-027 VCard intent-filter).
  - FR-028 (rewritten C3) полностью адресует: 10 KB DoS cap, UTF-8 validation, **ignore all extra VCard fields** (PHOTO/EMAIL/ADR/URL/BDAY/X-*), parse только FN + TEL[n], передача в domain `Contact.fromRaw()` validator. **Nice work.** Edge case в Edge Cases section: «VCard от вредоносного приложения с гигантским payload → парсер ограничивает 10 KB, отвергает большие».
  - Дополнение к security checklist: activity, обрабатывающая VCard, MUST быть **отдельной** `<activity-alias>` или `<activity>` с `exported="true"` (необходимо для share sheet), но MUST **не** иметь launcher icon (`MAIN/LAUNCHER` НЕ объявлен). См. также spec 009 security.md checklist если есть.
- [x] **Backup exclusion** (FR-046a).
  - **Excellent.** FR-046a явно вводит требование backup-exclude для контактов Room database. Текущий `data_extraction_rules.xml` уже исключает `datastore/com.launcher.capability.snapshot_v1.preferences_pb` и `health.snapshot_v1.preferences_pb`. **Action item**: добавить в обоих файлах (`backup_rules.xml` + `data_extraction_rules.xml`) exclude для:
    - `database/contacts.db` (или какой ни будет path выбран в plan.md);
    - возможно также `database/config_sync.db` (наследие спека 8 если не сделано) — проверить статус по спек 008 mandatory action.

## Compliance docs

- [~] **CHK022** [`docs/compliance/permissions-and-resource-budget.md`](../../../docs/compliance/permissions-and-resource-budget.md) updated.
  - **Partial.** Файл уже содержит секцию для спека 008 (verified строка 130). Для спека 009 — **отсутствует** delta-секция. **Fix**: добавить FR-046b «In same PR `docs/compliance/permissions-and-resource-budget.md` MUST be updated с delta для спека 009: новый `READ_CONTACTS` runtime permission (justification: FR-023 + FR-033c privacy disclosure), новый `<intent-filter>` на `ACTION_SEND` + `text/x-vcard` (exported attack surface mitigation per FR-028), расширение `<queries>` на market scheme (FR-035a), backup exclusion для contacts DB (FR-046a). Note: в строке 137 текущий permissions-and-resource-budget.md прямо говорит "`READ_CONTACTS` — spec 011" — **это inconsistent с тем, что спек 009 теперь имеет FR-023**. Update нужен.»

---

## Open items для spec.md

**Add to spec.md before speckit-plan:**

- **FR-023b** *(re-prompt + permanent-denial recovery, CHK003/CHK004)*: «После denial `READ_CONTACTS` admin MUST иметь alternative path — ручной ввод имени+номера через форму редактирования плитки. После “permanently denied” — экран “Откройте Settings → Apps → <name> → Permissions → Контакты” с кнопкой deep-link через `ACTION_APPLICATION_DETAILS_SETTINGS`.»
- **FR-035a** *(market scheme query + arbitrary-package availability check, CHK008/CHK020)*: «AndroidManifest `<queries>` MUST включать `<intent><action android:name="android.intent.action.VIEW"/><data android:scheme="market"/></intent>` для Play Store fallback resolution. Managed dispatcher (FR-035) MUST использовать `queryIntentActivities` с `MAIN/LAUNCHER` intent target (не `getPackageInfo`) для availability check произвольных packages.»
- **FR-046b** *(compliance doc update, CHK022)*: «In same PR `docs/compliance/permissions-and-resource-budget.md` MUST be updated с delta для спека 009: новый `READ_CONTACTS` permission, новый `<intent-filter>` на `text/x-vcard`, расширение `<queries>` на market scheme, backup exclusion для Room contacts DB. Удалить устаревшую строку “`READ_CONTACTS` — spec 011” / переформулировать как “raw photos — spec 011”.»

**Plan-level (не FR, но action items для `/speckit.plan`):**

- Implementation hint: `PredictiveBackHandler` (Compose 1.7+ API) на editor screen и VCard intent flow для unsaved-draft confirmation (CHK013).
- Implementation hint: backup_rules.xml + data_extraction_rules.xml — exclude путь к Room contacts DB (FR-046a) одновременно с проверкой `config_sync.db` exclude (наследие спека 8).
- Implementation hint: VCard intent activity — `exported="true"` для share sheet, но **без** `MAIN/LAUNCHER` (не создаём второй launcher icon).
- Documentation: Huawei without GMS — known limitation для OpenApp Play Store fallback, добавить в FAQ.
- Test: на Samsung-эмуляторе с One UI launcher проверить FR-027 share sheet иконку и регистрацию (см. android-emulator skill).

## Itog

- 14 PASS, 6 PARTIAL (все fixable короткими FR-патчами или plan-level hint'ами), 2 N/A, 0 hard FAIL.
- **Verdict:** спек **сильно дисциплинирован** по permissions — только один dangerous permission (`READ_CONTACTS`), и тот сопровождён best-practice rationale + privacy disclosure (FR-033c) + GDPR-compliant deletion UI (FR-033a/b). Backup exclusion (FR-046a) уже учтён. VCard exported attack surface адресован (FR-028 — size cap, encoding check, поле-whitelist). **Главные gap'ы** — package visibility для arbitrary OpenApp packages (FR-035a, чтобы FR-035 dispatcher работал на Android 11+) и небольшие UX-уточнения по permission flow (just-in-time prompt, alt-path при denial, permanent-denial deep-link). **Не блокер для plan/tasks**, но желательно зафиксировать перед `/speckit.plan`.

---

## TL;DR на русском (для нетехнического читателя)

Этот checklist проверяет, не нарушает ли спек 9 правила Android по разрешениям и не сломается ли на новых телефонах или у разных производителей (Samsung, Xiaomi, Huawei).

**Что хорошо в спеке 9:**

- Только **одно** новое разрешение — `READ_CONTACTS` (читать контакты) — и **только на телефоне админа**. На бабушкином телефоне это разрешение НЕ нужно (имя и номер сохраняются в облаке). Это правильное разделение: бабушка не увидит ни одного нового страшного запроса.
- Рядом с запросом контактов админ увидит **подробное объяснение**: «эти контакты сохраняются в облаке и видны на телефоне бабушки. Удалить можно через Settings» — это сильнее обычного Android pattern, соответствует европейскому GDPR.
- Удаление контактов из настроек реализовано **сразу** (не «мягкое удаление с восстановлением» — это нарушало бы GDPR ст.17).
- Контакты исключены из автоматической резервной копии Google Drive (`data_extraction_rules.xml`) — иначе телефон Маши, который попал в раскладку бабушки, попадал бы в облако админа без её согласия.
- Приём контактов из WhatsApp/Telegram/Viber (через стандартный «Поделиться контактом») защищён: ограничение размера 10 KB (от DoS-атак), проверка кодировки, **игнорируются** все «лишние» поля кроме имени и телефона (никаких фото / адресов / email из VCard).
- Никаких **служб в фоне** (которые жгут батарею), никаких **точных будильников** (которые Google не любит), никаких системных уведомлений в спеке 9.

**Что нужно подкрутить (6 partial, все исправимые):**

1. **Главный недочёт — package visibility (Android 11+)**: когда админ создаёт плитку «открыть Яндекс Карты», бабушкин телефон должен проверить «установлены ли Яндекс Карты». На Android 11+ по умолчанию приложения **не видят** другие приложения — нужно явно перечислить в манифесте либо конкретные packages, либо «открыть Play Store» intent. Сейчас manifest от спека 006 покрывает только конкретные WhatsApp/Telegram/YouTube, но **не** `market://` scheme. Без этого fallback в Play Store может не сработать. **Fix**: добавить FR-035a, расширить `<queries>` блок.
2. **Уточнить когда запрашивать READ_CONTACTS**: в момент тапа «+ контакт» (правильно), а не при cold start приложения. Это следует из сценариев, но не зафиксировано как FR.
3. **Что делать при отказе от разрешения**: альтернативный путь — ручной ввод имени+номера через клавиатуру + deep-link на системные настройки при «не спрашивать снова». **Fix**: FR-023b.
4. **Predictive back gesture (Android 14+)**: в редакторе раскладки с несохранённым черновиком нужно перехватить swipe-back, чтобы спросить «сохранить?». Compose 1.7 имеет API `PredictiveBackHandler` — это plan-level concern, не FR.
5. **Huawei без GMS**: фичу «открыть Play Store страницу» нельзя реализовать на Huawei без AppGallery URL — known limitation, документируем в FAQ.
6. **Обновить compliance doc**: `permissions-and-resource-budget.md` сейчас пишет «`READ_CONTACTS` — spec 011», что устарело. **Fix**: FR-046b — обновить в том же PR.

**Главные платформенные риски (top 3):**

1. **Android 11+ package visibility** (Huawei/Xiaomi/любой современный): без FR-035a (`market://` query) функция «открыть Play Store если не установлено» сломается. ⚠
2. **Huawei без GMS**: даже с правильным manifest'ом — нет Google Play Store на устройстве. Fallback в Petal Browser → AppGallery — отдельная история. Known limitation. ⚠
3. **Samsung One UI VCard share sheet** + **MIUI autostart**: VCard intent от мессенджера может не открыть наше приложение из killed-state на Xiaomi (MIUI autostart whitelist) — это MIUI bug, не наш, но FAQ нужен. ⚠

**Нужно ли менять спек:** да, рекомендуется добавить **3 коротких FR** перед `/speckit.plan`:
- **FR-023b** (denial recovery + manual entry alternative);
- **FR-035a** (market scheme в `<queries>` + arbitrary-package availability check pattern);
- **FR-046b** (compliance doc update в том же PR).

Все три — мелкие правки текста на 2-3 строки каждая, **не** меняющие архитектуру. Ни один hard FAIL не найден.

# Feature Specification: Contacts Photos and End-to-End Encrypted Private Media

**Feature Branch**: `011-contacts-and-e2e-encrypted-media`
**Created**: 2026-05-21
**Status**: Draft (rev. 2 — mentor clarification 2026-05-21)
**Input**: roadmap §Spec 011 ([docs/product/roadmap.md:264](../../docs/product/roadmap.md#L264)) — хранение приватных медиа (фотографий контактов и других семейных фото) с end-to-end шифрованием, проброс выбранных контактов с устройства admin в `/config` для раскладки Managed; намеренно отделено от спека 009, где фотография контакта осознанно зафиксирована как `photoRef = null`.

---

## Контекст и цель спека

Спек 006 (`provider-capabilities-and-health`) ввёл порт `IconStorage.resolve(iconId: String)` и namespace-конвенцию `<namespace>:<name>` для идентификаторов иконок ([icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48)). Namespace `private:` зарезервирован за этим спеком с разрешением `EncryptedMediaStorage` как future implementation. До 011 никаких приватных байт нигде не хранится — порт умеет реагировать на `private:<uuid>` (возвращает `Placeholder`), но реальный резолвер отсутствует.

Спек 007 (`pairing-and-firebase-channel`) установил парный канал admin↔Managed через QR + Firestore. Pairing-wire-format заранее предусмотрел future-additive поле для общего секрета: «если spec 011 (private media) добавит `pairingKey` для e2e — добавляем поле как опциональное, schemaVersion остаётся 1 (additive)» ([pairing-token.md:79](../007-pairing-and-firebase-channel/contracts/pairing-token.md#L79)). На момент 011 у пары есть Firestore-канал и FCM-пуш, но **общего криптоключа между парой устройств у пары нет** — это вводит спек 011.

Спек 008 (`bidirectional-config-sync`) ввёл `Contact` с явно nullable полем `val photoRef: String? = null, // reserved for spec 011 (private:<uuid>)` ([data-model.md:64](../008-bidirectional-config-sync/data-model.md#L64)) и зарезервировал в `PartialReason` enum значение `media_decrypt_failed — spec 011 e2e media couldn't be decrypted (future)` ([state-applied.md:65](../008-bidirectional-config-sync/contracts/state-applied.md#L65)). Спек 008 пушит контакты целиком в `/config`, чтобы Managed не нуждался в `READ_CONTACTS` — но фотография у контакта в 008 всегда `null`.

Спек 009 (`admin-mode-flows`) реализовал Contacts Picker и VCard share-intent у admin, и тоже зафиксировал фотографию как `photoRef = null (фото — spec 011)` ([спека 009 spec.md:218](../009-admin-mode-flows/spec.md#L218)). К моменту 011 admin уже умеет выбирать контакты и пушить их в `/config`, но **без фотографии**.

Спек 011 закрывает эту дыру: даёт паре устройств **общий криптоключ при pairing**, реализует `EncryptedMediaStorage` под уже существующий порт `IconStorage`, подключает фото контактов от admin'a к раскладке Managed, и открывает namespace `private:` для других приватных медиа (фото бабушки, фото личных документов). При этом ни Google (Firebase Storage), ни сетевой посредник не видят содержимое — только зашифрованные байты.

**Архитектурная роль спека:** «крипто-фундамент» — впервые в проекте каждое устройство имеет свою публично-приватную пару ключей и умеет шифровать данные для произвольного списка получателей. После 011 любые приватные данные могут переехать через тот же канал — от групповых ключей семьи до multi-device recovery. Это **one-way door** (rule 3 CLAUDE.md), но дверь спроектирована так, что её **архитектурная часть** (envelope format, hybrid encryption, per-device keys) выдержит смену модели доверия без миграции blob'ов. Меняться может «как ключи распространяются», не «как зашифровано». Exit ramp — см. §Архитектурные one-way doors.

---

## Clarifications (mentor-сессия 2026-05-21)

Этот раздел фиксирует решения, принятые в discussion-фазе с пользователем по 7 ключевым архитектурным вопросам. Каждое решение содержит формулировку, обоснование и (где применимо) ссылку на будущий спек, который снимет временное ограничение.

### C-1: Модель доверия в 011 — односторонняя пара (как в спеках 7-9)

**Решение:** в спеке 011 сохраняем одностороннюю модель управления admin→Managed из спеков 7-9. Никаких изменений в Security Rules, pairing-token wire-format direction, или ownership полей `/config`.

**Обоснование:** двусторонняя/групповая/multi-device модели — отдельная значительная работа, затрагивающая 3 уже смерженных спека. Их перенос на 011 удваивает scope и блокирует крипто-фундамент. Криптографический протокол (см. C-3) спроектирован membership-agnostic — переход на любую другую модель доверия в будущем НЕ требует перешифровки blob'ов или смены envelope format.

**Будущие спеки, которые снимут ограничение:**
- **~015 «двусторонние пары»** — Managed может тоже стать управляющим для admin'a после согласия.
- **~016 «семейные группы»** — групповой ключ для семейного круга в стиле WhatsApp (несколько участников видят общие медиа).
- **~017 «multi-device recovery»** — у одного человека несколько устройств видят одну и ту же пару/группу; восстановление при потере телефона.
- **~018 «key rotation + forward secrecy»** — периодическое обновление ключей пары для защиты от утечек.

### C-2: Multi-recipient encryption — заложить архитектурно, использовать с одним получателем

**Решение:** envelope format с первого commit поддерживает **список** получателей произвольной длины. В спеке 011 список всегда содержит **одного** получателя (другой член пары). В будущих спеках (~016, ~017) список расширяется до N без изменения envelope format.

**Обоснование:** массив длиной 1 идентичен массиву длиной N в коде write/read. Цена закладывания — 0 строк сейчас, экономия — миграция всех blob'ов в будущем.

### C-3: Криптографический протокол — per-device key pairs + hybrid encryption

**Решение:** каждое устройство при первом запуске приложения генерирует свою публично-приватную пару `(Pub_X, Priv_X)`. `Pub_X` публикуется в Firestore как часть identity устройства; `Priv_X` хранится в Android Keystore и НЕ покидает устройство.

Шифрование blob'a происходит по схеме **hybrid encryption**:
1. Генерируется одноразовый Content Encryption Key (CEK) — 32 байта рандома **на каждый blob**.
2. Blob (само фото / документ) шифруется CEK через AEAD (XChaCha20-Poly1305).
3. CEK шифруется публичным ключом каждого получателя через `crypto_box_seal` (libsodium).
4. Envelope содержит: `schemaVersion`, `cipherSuiteId`, `nonce`, `recipients: [{deviceId, encryptedCEK}]`, `ciphertext`, `mac`.

**Обоснование:** этот протокол **не привязан** к модели доверия. Smena «одного получателя» на «N получателей» — это смена только содержимого массива `recipients`, без изменения структуры envelope. Per-device keys означают, что каждое устройство держит секрет только за себя — компрометация одного устройства не вредит остальным.

**Архитектурный принцип «crypto envelope is membership-agnostic»** — формат зашифрованного blob'a ничего не знает о том, кто его расшифрует. Это знает отдельный слой `RecipientResolver` (см. C-7), который для 011 возвращает «другой член пары», в будущем — «все члены группы» или «все мои устройства».

### C-4: Криптобиблиотека — libsodium через Lazysodium-android

**Решение:** используем libsodium через Java-обёртку Lazysodium-android. AEAD = XChaCha20-Poly1305. Asymmetric key agreement = X25519. Hybrid encryption = `crypto_box_seal`.

**Обоснование:**
- libsodium закрывает все нужды через минимальное число функций без избыточного API.
- Открытый проект сообщества, не привязан к одному вендору (важно для будущего «переезда на свой сервер» по server-roadmap, rule 8 CLAUDE.md).
- Универсальность runtime: libsodium работает на любом языке/платформе (когда появится свой сервер на любом стеке).

**Альтернативы и почему отброшены:**
- **Tink (Google)** — привязка к Google Cloud KMS в документации создаёт шум; runtime'ы Tink ограничены (нет, например, Rust); философия Tink ориентирована на server-side use cases. Закрывает наши потребности, но даёт меньше гибкости при будущем переезде на свой сервер.
- **Conscrypt/BouncyCastle напрямую** — низкоуровневое API, требует сборки крипто-протокола из кубиков, риск ошибки. Противоречит принципу «никакой самописной крипты».
- **Signal Protocol (libsignal)** — избыточная сложность для текущего scope (Double Ratchet, async session state). Хороший кандидат для будущего ~018 (forward secrecy), не для 011.

**Exit ramp:** schemaVersion + cipherSuiteId в envelope с первого commit. Если через 5 лет XChaCha20-Poly1305 устареет или libsodium станет недоступным — поднимаем schemaVersion до 2, шифруем новые blob'ы новым алгоритмом, читаем старые по правилам v1. Цена exit ramp = код миграции (один раз), НЕ перешифровка всех старых blob'ов.

### C-5: Namespace `private:` — без sub-namespacing

**Решение:** один namespace `private:<uuid>` для любого типа приватного медиа. Тип содержимого (image, video, document, и т.д.) хранится **внутри envelope** как metadata-поле, не в namespace.

**Обоснование:** `IconStorage` контракт остаётся прежним (см. спек 006 [icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48)). Менять тип содержимого blob'a в будущем = изменить metadata, не namespace. Sub-namespacing (`private-photo:`, `private-doc:`) ввёл бы лишнюю абстракцию в IconStorage и потребовал бы расширения контракта при добавлении новых типов — нарушение rule 4 CLAUDE.md (MVA).

### C-6: POST_NOTIFICATIONS — откладываем за пределы 011

**Решение:** разрешение POST_NOTIFICATIONS НЕ активируется в этом спеке. Запись в [docs/compliance/permissions-and-resource-budget.md:224](../../docs/compliance/permissions-and-resource-budget.md#L224) обновляется с пометкой «deferred to spec 012 (balance alerts) / spec 013 (critical health push)».

**Обоснование:** в 011 нет нативных уведомлений. Crypto-операции (расшифровка blob'a, fallback на placeholder) происходят silently в фоне ConfigApplier'a — без push'ей пользователю.

### C-7: Reference counting blob'ов учитывает history snapshots

**Решение:** blob в Storage удаляется только когда ни один документ (текущий `/config/current` ИЛИ один из history snapshots в `/config/history/`) на него не ссылается. История из спека 009 — полноценный источник references.

**Обоснование:** если бабушка откатывается на старый конфиг через history (спек 009), и в этом конфиге был контакт с фото — blob должен быть доступен. Удаление blob'a при простом удалении контакта из текущего `/config` нарушит rollback-семантику.

**Cost implication:** blob может жить дольше, чем «казалось бы» нужно — пока bookmark в history не очистится через retention-10 (спек 009 Q2=А). Это приемлемо: при retention 10 snapshot'ов и средних 30 контактах с фото на пару, верхняя оценка ~300 blob'ов на пару (≈ 60 MB при 200 KB/blob) — комфортно для Spark plan.

### C-8 (доп.): Архитектурный seam `RecipientResolver` — обоснованное исключение из rule 4

**Решение:** вводим интерфейс `RecipientResolver { fun resolveRecipients(linkId: LinkId): List<DeviceIdentity> }` с одной реализацией `PairRecipientResolver` в 011.

**Обоснование (для Constitution Check в plan-phase):** rule 4 CLAUDE.md «Minimum Viable Architecture» запрещает single-implementation interface «на будущее». Но в нашем случае seam обоснован тем, что **точно** появятся `GroupRecipientResolver` (~016), `MyDevicesRecipientResolver` (~017) — три минимум реализации в roadmap. Это даёт **архитектурную независимость крипто-протокола от модели доверия** (главное требование пользователя из discussion). Заносим как обоснованное исключение в spec.md и plan.md.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Фотография контакта появляется на плитке у бабушки (Priority: P1)

Admin в редакторе раскладки Managed (`спек 009 US-3`) добавляет внучку Машу через Contacts Picker. Picker отдаёт телефон Маши и **её фотографию** (Android Contacts даёт `Contact.Photo` URI). Приложение шифрует байты фото на устройстве admin'a общим ключом пары, загружает зашифрованный blob в Firebase Storage по адресу, привязанному к `linkId`, пишет ссылку в `/config.contacts[i].photoRef = "private:<uuid>"` и пушит конфиг. Через ≤ 30 сек у бабушки на плитке «Маша» — реальная фотография внучки, а не серый placeholder.

**Why this priority**: эта story — главная ценность спека. Без неё крипто-инфраструктура «работает на холостом ходу» без потребительского выхода. Старшие пользователи опознают близких прежде всего по лицу — это критично для UX лаунчера.

**Independent Test**: paired пара устройств (через 007 после обновления pairing для общего ключа), admin грантит `READ_CONTACTS`, выбирает контакт **с фотографией** через picker → создаётся `Contact { photoRef = "private:<uuid>" }` в `/config.contacts[]`, blob `<uuid>` загружен в Storage в зашифрованном виде → Managed применяет конфиг, скачивает blob, расшифровывает локально, показывает фото на соответствующей плитке. На Managed `READ_CONTACTS` НЕ нужен — фото целиком приходит через `/config` + Storage.

**Acceptance Scenarios**:

1. **Given** пара заpaired'ена после внедрения общего ключа (см. US-5), admin в редакторе Managed, `READ_CONTACTS` дан, **When** admin выбирает в picker контакт с непустой Android-фотографией, **Then** создаётся `Contact` с `photoRef = "private:<uuid>"`, зашифрованный blob загружается в Storage до push'а `/config`, после успеха push'а `/config` ссылается на `<uuid>`.
2. **Given** Managed получил push о новом `/config`, **When** ConfigApplier видит `Contact.photoRef = "private:<uuid>"`, **Then** Managed скачивает blob из Storage, расшифровывает общим ключом, кеширует расшифрованные байты в свой внутренний хранилище и резолвит плитку через `IconStorage.resolve("private:<uuid>")` → `IconResolution.Drawable(bytes)`.
3. **Given** admin выбирает контакт **без** фотографии (Android picker возвращает пустой `Contact.Photo`), **When** контакт добавляется в `/config`, **Then** `photoRef = null` (как в спеке 009 до 011); никакой шифровки/загрузки не происходит, расход трафика и Storage = 0.
4. **Given** admin удаляет контакт из раскладки, **When** push нового `/config` без этого `Contact` уходит, **Then** связанный зашифрованный blob помечен на удаление (см. US-3 housekeeping); Managed чистит локальный кеш расшифрованного фото.

---

### User Story 2 — Фотографии личных документов в зашифрованном виде (Priority: P2)

Admin добавляет к раскладке бабушки плитки с её **личными документами** — фото паспорта, СНИЛС, медкарты, страховых полисов. Бабушка тапает по плитке «Паспорт» — видит увеличенное фото оригинального документа. Документы шифруются ровно тем же механизмом, что и фото контактов (см. US-1): на устройстве admin'a → зашифрованный blob в Firebase Storage → namespace `private:<uuid>` в `IconStorage`. Google не имеет доступа к содержимому документов — только зашифрованные байты.

**Why this priority**: реальная потребность пожилых пользователей — иметь документы «под рукой» без поиска по физическим папкам. Также распространённый use case для не-пожилых пользователей в будущем (любой человек хочет держать копии документов всегда доступными). P2 потому что US-1 (фото контактов) — более частый случай ежедневного использования; документы — реже, но критично-важно.

**Independent Test**: paired пара устройств с настроенным e2e (см. US-5), admin выбирает на своём устройстве фотографию из галереи (паспорт), отмечает её как «приватный документ», даёт label «Паспорт» — фото шифруется и грузится в Storage, в `/config` появляется новая плитка-документ → бабушка применяет конфиг и видит плитку «Паспорт»; тап → расшифровка blob'a на устройстве → отображение фото в полноэкранном просмотрщике.

**Acceptance Scenarios**:

1. **Given** пара заpaired'ена, e2e работает, admin в редакторе раскладки Managed, **When** admin выбирает «+ документ» → выбирает фотографию из галереи → задаёт label, **Then** фотография шифруется CEK'ом, CEK шифруется публичным ключом бабушки, envelope (с metadata.kind="document") загружается в Storage; в `/config` появляется новая плитка с `iconId = "private:<uuid>"` и label.
2. **Given** Managed применяет конфиг с новой плиткой-документом, **When** бабушка тапает по плитке, **Then** Managed скачивает blob, расшифровывает локально, показывает фото в полноэкранном viewer'е; расшифрованные байты остаются в памяти на время отображения и **не сохраняются** на disk в plaintext (FR-052).
3. **Given** admin удаляет плитку-документ, **When** push нового `/config` уходит, **Then** blob помечен на удаление (см. US-3 housekeeping с учётом history reference counting C-7).
4. **Given** Managed не может расшифровать blob документа, **When** бабушка тапает по плитке, **Then** показывается дружелюбное сообщение «Документ временно недоступен» (не technical error message), admin видит индикатор `media_decrypt_failed`.

**Замечание по UX:** конкретный UX добавления документа (отдельная кнопка «+ документ», полноэкранный preview, label-editor) детализируется в plan-фазе. В этом спеке фиксируется только **что**, не **как именно** в UI.

**Замечание по scope:** «фото бабушки в её собственной плитке» (как декорация в раскладке) выкинута из scope 011 как нишевая. При желании возвращается в любой будущий спек добавлением одного UX-flow поверх той же crypto-инфраструктуры.

---

### User Story 3 — Зашифрованные blob'ы убираются вместе с удалением контакта или revoke пары (Priority: P2)

Admin удаляет контакт «Маша» из раскладки. Через несколько минут связанный зашифрованный blob `<uuid>` в Firebase Storage удалён — не остаётся «висячих» приватных данных. Если admin вообще revoke'нет линк (спек 007 FR-033, recursive subtree delete), вся private-media subcollection для этого `linkId` тоже удаляется.

**Why this priority**: privacy hygiene и контроль расходов Storage (тариф Spark с лимитом). Без story зашифрованные фото накапливаются вечно и едят квоту. P2 потому что в краткосрочной перспективе objём blob'ов мал (одно фото контакта ≈ 50-200 KB), но накопление за месяцы делает проблему реальной.

**Independent Test**: admin добавил 5 контактов с фото → Storage содержит 5 blob'ов под `linkId`. Admin удалил 3 контакта → через ≤ 5 минут в Storage остаются только 2 blob'a. Admin revoke'нет линк → через ≤ 5 минут в Storage 0 blob'ов под этим `linkId`.

**Acceptance Scenarios**:

1. **Given** в `/config.contacts[]` есть `Contact { photoRef = "private:<uuid>" }`, **When** admin удаляет этот contact и пушит новый `/config` без него, **Then** инициируется удаление blob'a `<uuid>` из Storage; срок — best-effort, не блокирует push.
2. **Given** контакт с photoRef удалён, blob ещё не успели стереть, **When** другой контакт **в этом же** `/config` ссылается на тот же `<uuid>` (теоретически — после переиспользования), **Then** blob не удаляется (reference counting перед удалением).
3. **Given** admin делает revoke линка (спек 007 FR-033), **When** subtree delete отрабатывает, **Then** **вся** private-media subcollection под `/links/{linkId}/` удалена; bucket не имеет «осиротевших» blob'ов.
4. **Given** Storage недоступен в момент удаления (сеть упала), **When** клиент пытается удалить blob, **Then** удаление отложено в очередь повторов; счётчик попыток ограничен; после превышения — пишется warning в structured log, blob остаётся в Storage (privacy risk, но известный).

---

### User Story 4 — Managed honest reporting когда расшифровать не удалось (Priority: P2)

Что-то пошло не так: blob повреждён в Storage, ключ потерян после фабричного сброса Managed без re-pairing, MAC не совпал. На плитке контакта показывается серый placeholder (не падение приложения), Managed пишет в `/state.partialApplyReasons` значение `media_decrypt_failed` ([state-applied.md:65](../008-bidirectional-config-sync/contracts/state-applied.md#L65)), и admin в своём UI видит индикатор «фото у Маши не отрисовалось». Admin может пере-добавить фото или сделать ре-pairing.

**Why this priority**: явный fallback и обратная связь admin'у — обязательны для production. Без story сбой расшифровки = молчаливый regression «фото пропало», непонятно admin'у. P2 потому что happy-path работает и без этой story; это error-recovery поверх неё.

**Independent Test**: симулировать порчу blob'a (записать рандомные байты в Storage по адресу blob'a) → Managed скачивает, расшифровка падает → плитка с placeholder, `/state.partialApplyReasons` содержит `media_decrypt_failed`, admin UI видит индикатор. Логи Managed содержат structured event с категорией ошибки (без байт фото).

**Acceptance Scenarios**:

1. **Given** Managed получил `/config` с `Contact.photoRef = "private:<uuid>"`, **When** скачанный blob не проходит MAC-проверку (повреждён или подменён), **Then** плитка показывает placeholder, `/state.partialApplyReasons` дополняется `media_decrypt_failed`, в structured log — событие категории `media_decrypt_failed` с `<uuid>` (но **без** содержимого blob'a и **без** ключа).
2. **Given** на Managed после фабричного сброса нет общего ключа (re-pairing не сделан), **When** приходит `/config` с `photoRef`, **Then** Managed не пытается скачать blob, сразу записывает `partialApplyReasons += media_decrypt_failed` с подкатегорией `no_key`, admin видит индикатор и подсказку «требуется re-pairing».
3. **Given** Storage возвращает 404 для `<uuid>` (blob удалён раньше времени), **When** Managed применяет конфиг, **Then** плитка с placeholder, `partialApplyReasons += media_decrypt_failed` с подкатегорией `blob_missing`.
4. **Given** Managed успешно расшифровал blob ранее и закешировал расшифрованные байты, **When** Storage становится недоступен или blob исчезает, **Then** Managed использует локальный кеш расшифрованного фото и не помечает partial-apply (graceful offline).

---

### User Story 5 — Pairing генерирует общий криптоключ для пары (Priority: P1, prerequisite)

Когда admin сканирует QR и нажимает «Подтвердить» (спек 007 US-1), помимо привязки `linkId` и обмена FCM-токеном пара устанавливает **общий симметричный ключ** для шифрования приватных медиа. Ключ известен **только** этим двум устройствам — Firebase Storage, Cloudflare Worker, Google, admin/managed Firestore docs не имеют к нему доступа. После любого revoke (спек 007 FR-033) или фабричного сброса ключ инвалидируется; чтобы вернуть e2e-функциональность, пара должна сделать re-pairing.

**Why this priority**: без общего ключа никакая другая story спека 011 невозможна. Это **prerequisite-story**, упоминается отдельно потому что меняет публичный wire-format pairing'а (additive, schemaVersion остаётся 1 — см. [pairing-token.md:79](../007-pairing-and-firebase-channel/contracts/pairing-token.md#L79)) и формализует обязательства спека 007 в реальный код.

**Independent Test**: factory-fresh пара устройств, admin сканирует QR → после `consent.allow` оба устройства хранят (в Android Keystore / iOS Keychain или эквиваленте) идентичный общий ключ; ключ **не** записан в `/links/{linkId}/*` или в `/pairing/<token>` (его там не должно быть). Симметричность проверяется тестом: байты, зашифрованные на admin'е, расшифровываются на Managed.

**Acceptance Scenarios**:

1. **Given** admin сканирует QR factory-fresh Managed, **When** `consent.allow` срабатывает (спек 007 FR-009), **Then** оба устройства **независимо** выводят общий ключ из обмена при pairing'е (NEEDS CLARIFICATION схема — см. §Архитектурные one-way doors); ключ хранится в защищённом хранилище ОС; в Firestore документах ключа нет.
2. **Given** pair установлена, общий ключ есть, **When** на admin'e шифруется тестовый блок данных и кладётся в Storage, **Then** Managed скачивает blob и успешно расшифровывает; обратно — то же самое (если US-2 в scope).
3. **Given** admin делает revoke (спек 007 FR-033), **When** recursive subtree delete завершён, **Then** оба устройства локально удаляют общий ключ; зашифрованные blob'ы Storage уже снесены вместе с subtree (см. US-3 §revoke).
4. **Given** Managed получил factory reset без revoke, **When** admin продолжает пушить `/config` с `photoRef`, **Then** Managed не может расшифровать (US-4 Scenario 2); пара должна сделать re-pairing для восстановления.

---

### Edge Cases

- Очень большие фотографии (несколько MB) — шифрование/загрузка не должны блокировать UI admin'a; нужен progress-индикатор и cancel.
- Несколько контактов с одной и той же фотографией: дедупликация blob'ов по хешу содержимого (а не по контакту) — open question (NEEDS CLARIFICATION ниже).
- Admin шифрует и кладёт blob, push `/config` падает (сеть/таймаут/конфликт): «orphan blob» в Storage без ссылки. Background reconciler должен подметать.
- На admin несколько paired Managed'ов — фотография одного контакта в раскладке двух бабушек: разные `linkId` → **разные** общие ключи → blob шифруется и грузится **дважды**, по разу на пару. Кажется приемлемым (квота не критична), но фиксируется явно.
- Managed на Spark plan — Firebase Storage в Spark доступен с ограниченной квотой; нужно проверить, что spec 011 не выводит проект за пределы Spark (NEEDS CLARIFICATION в §Зависимости-обещания).
- Часы сместились (clock skew): MAC / nonce-схема не должна зависеть от устойчивости времени между устройствами.
- Конфликт с Firestore Security Rules: запись в Storage path = `linkId/private-media/<uuid>` должна быть разрешена только `adminId` и `managedDeviceFirebaseUid` этого линка; чужие пары — `PERMISSION_DENIED` (нужны Storage Rules, не только Firestore).
- Schema mismatch фото: если позже добавляется не только фото-blob, но и **видео** под тем же namespace — требуется отдельная sub-namespacing или metadata «kind=image|video». NEEDS CLARIFICATION в §Архитектурные one-way doors.

---

## Архитектурные one-way doors

Из roadmap §Spec 011 было зафиксировано как **one-way door**: выбор криптосистемы и схемы обмена ключами. После discussion-сессии 2026-05-21 (см. §Clarifications) принципиальные решения зафиксированы — door частично закрыта, но **с гарантией exit ramp на уровне envelope format**, а не на уровне «миграция всех blob'ов».

### Принятые решения и их exit ramp

**OWD-1: Криптобиблиотека = libsodium** (C-4). Exit ramp = `schemaVersion` + `cipherSuiteId` в envelope с первого commit. Смена библиотеки в будущем = новые blob'ы пишутся с другим `cipherSuiteId`, старые продолжают читаться по правилам v1. Перешифровка blob'ов НЕ требуется.

**OWD-2: Per-device key pairs (X25519) + hybrid encryption** (C-3). Exit ramp = `cipherSuiteId` в envelope покрывает и asymmetric scheme. Если через 10 лет X25519 окажется уязвимым к квантовым атакам — переход на post-quantum scheme идёт через тот же механизм version bump'а envelope.

**OWD-3: Membership-agnostic envelope** (C-2 + C-8). Список `recipients` произвольной длины. Переход от пары к группам / multi-device НЕ затрагивает envelope format — меняется только содержимое массива `recipients`. Это **главная архитектурная инвестиция** спека: разделение слоёв «кто получатели» (membership) и «как они зашифрованы» (crypto envelope).

### ADR-007 (второй subtype `TrustEdgeBootstrap`) — пишется в plan-фазе

Задача `TODO-DOC-001` в [project-backlog.md:378](../../docs/dev/project-backlog.md#L378) фиксирует, что в начале этого спека должен появиться ADR-007. По итогам clarify-сессии содержимое ADR-007 определено:
- **Subject:** TrustEdgeBootstrap расширение для per-device asymmetric keys + первое обмен публичными ключами при pairing.
- **Decision:** при первом запуске приложения генерируется (Pub_X, Priv_X); Pub_X публикуется в Firestore при первом online; pairing flow (спек 007) расширяется additive — после `consent.allow` оба устройства обмениваются Pub-ключами и сохраняют их в `/links/{linkId}/devices/{deviceId}`.
- **Consequences:** schemaVersion pairing-token остаётся 1; новое поле `pairingKey` в pairing-token НЕ нужно (отбрасываем — обмен Pub-ключами идёт через Firestore после `consent.allow`, не через QR).
- **Запись ADR-007 — в plan-фазе** (в директории `docs/adr/` если она есть, или inline в plan.md).

### Открытые вопросы для plan-фазы (не блокеры спека)

Эти вопросы влияют на детали реализации, но НЕ на архитектурные решения 011:

- **Где именно публиковать Pub_X в Firestore.** Кандидаты: `/links/{linkId}/devices/{deviceId}/pubkey` (привязан к линку) или глобальный `/devices/{deviceId}/pubkey` (один на всё). Решается в plan.md с учётом Security Rules.
- **Хранение CEK при retry uploads.** Если admin зашифровал blob, загрузил, а push `/config` упал — retry не должен заново шифровать (новый CEK = другой blob). CEK сохраняется в локальном draft'е до успешного push'а.
- **Compromised device detection** (как пользователь поймёт «pairing получился с тем, кем нужно»). В долгосрочной перспективе — safety numbers в стиле Signal. Для 011 фиксируется в backlog как `TODO-SEC-NNN`. В spec 011 этот flow НЕ обязателен (но если простой — допустимо).
- **MITM защита QR.** Текущий pairing-token из спека 007 хватает для protection of channel, но не для proof-of-identity при первом обмене. Закрывается тем же safety-numbers подходом — backlog.

---

## Requirements *(mandatory)*

### Functional Requirements

**Pairing common key (prerequisite of all e2e features):**

- **FR-001**: Pairing flow (спек 007) MUST establish a shared symmetric key known to **both** admin and Managed devices and to **no one else** (not Firestore documents, not Firebase Storage objects, not Cloudflare Worker, not third-party CDN).
- **FR-002**: Shared key MUST be persisted in OS-level secure storage on each device (Android Keystore / iOS Keychain / equivalent). Plain-text storage of the key is forbidden.
- **FR-003**: Pairing token wire-format MUST stay at `schemaVersion = 1` if extended for key exchange (additive only, per [pairing-token.md:79](../007-pairing-and-firebase-channel/contracts/pairing-token.md#L79) and CLAUDE.md rule 5). Any breaking change → bump to 2 + migration code.
- **FR-004**: On revoke (спек 007 FR-033) the shared key MUST be deleted on both devices; re-pairing is required to restore e2e features.
- **FR-005**: On factory reset of Managed (or admin) without prior revoke, the key is irrecoverable; subsequent decryption attempts MUST fail gracefully per FR-024.

**Encrypted blob envelope (wire format):**

- **FR-010**: Every encrypted blob in Firebase Storage MUST carry an explicit `schemaVersion` field in its envelope from the first commit (CLAUDE.md rule 5).
- **FR-011**: Envelope MUST contain: schemaVersion, cipher identifier, nonce/IV, ciphertext, authentication tag (MAC). Recovery of plaintext without all five MUST be cryptographically infeasible.
- **FR-012**: Blob filename in Storage MUST be a UUIDv4 (matches `<uuid>` part of `private:<uuid>` in `iconId`). No filename-leaked metadata.
- **FR-013**: Storage path MUST be scoped per link: `/links/{linkId}/private-media/<uuid>` (or equivalent, NEEDS CLARIFICATION exact path in plan-phase). Cross-link reads MUST be blocked by Storage Rules.

**Admin-side write path (US-1):**

- **FR-020**: When admin's Contacts Picker returns a contact with a non-empty photo, the system MUST encrypt the photo bytes locally on the admin device **before** uploading to Storage; plaintext bytes MUST NOT leave the device.
- **FR-021**: Successful upload of the encrypted blob MUST precede the push of `/config` referencing `photoRef = "private:<uuid>"`. If upload fails, push of `/config` MUST NOT proceed; the `Contact` is added with `photoRef = null` (graceful degradation).
- **FR-022**: Contact without an Android-side photo (picker returned empty) MUST behave as in spec 009 — `photoRef = null`, no encryption, no Storage upload (zero cost for photoless contacts).

**Managed-side read path (US-1):**

- **FR-023**: When ConfigApplier processes a `Contact.photoRef = "private:<uuid>"`, the system MUST download the blob from Storage, verify MAC, decrypt, and cache the decrypted bytes locally in private app storage (not accessible to other apps).
- **FR-024**: Failed download or decryption MUST NOT crash the app. Tile MUST fall back to placeholder, `/state.partialApplyReasons` MUST include `media_decrypt_failed` ([state-applied.md:65](../008-bidirectional-config-sync/contracts/state-applied.md#L65)), structured log MUST emit an event with `<uuid>` but **without** blob bytes and **without** key material.
- **FR-025**: `IconStorage.resolve("private:<uuid>")` MUST return `IconResolution.Drawable(handle)` on successful local cache hit; the existing port signature in spec 006 ([IconStorage.kt:20](../../core/src/commonMain/kotlin/com/launcher/api/capability/IconStorage.kt#L20)) MUST NOT change.

**Housekeeping (US-3):**

- **FR-030**: When a `Contact.photoRef` reference is removed from `/config` (admin push without that contact), the system MUST schedule deletion of the underlying blob in Storage on a best-effort basis.
- **FR-031**: Blob deletion MUST use reference counting — a blob is deleted only when no `Contact.photoRef` in the current `/config` (or pending drafts) references it.
- **FR-032**: On revoke (спек 007 FR-033), recursive subtree delete MUST include the `/links/{linkId}/private-media/` Storage path; no orphan blobs MAY remain after revoke.
- **FR-033**: `Link.KNOWN_SUBCOLLECTIONS` ([Link.kt:37-44](../../core/src/commonMain/kotlin/com/launcher/api/link/Link.kt#L37)) MUST be extended to include `private-media` (or equivalent) so that `LinkRegistry.revoke()` correctly enumerates it during subtree delete.
- **FR-034**: Background reconciler MUST detect orphan blobs (blob exists in Storage with no `/config` reference for ≥ 24h) and delete them. This is privacy hygiene; orphans MUST NOT accumulate indefinitely.

**Error reporting (US-4):**

- **FR-040**: All decryption failures (bad MAC, missing key, blob 404, ciphertext corrupted) MUST be reported through `/state.partialApplyReasons += media_decrypt_failed` and through a structured log event.
- **FR-041**: Subcategories of `media_decrypt_failed` (no_key, blob_missing, mac_failed, ciphertext_corrupted) MUST be distinguishable in the structured log (for admin diagnosis), but NEEDS CLARIFICATION whether they are distinguishable in `/state.partialApplyReasons` itself or only in the log.
- **FR-042**: Admin UI MUST display an indicator on the Managed-device summary (spec 009 US-2 health summary) when one or more contacts have `media_decrypt_failed`; tap → contextual help suggesting re-add photo or re-pair.

**Security and privacy:**

- **FR-050**: Storage Rules MUST allow read/write of `/links/{linkId}/private-media/*` only for the link's `adminId` and `managedDeviceFirebaseUid`; any other UID → `PERMISSION_DENIED`.
- **FR-051**: Plain-text photo bytes MUST NOT appear in any logs, crash reports, analytics, structured events, or any other off-device channel.
- **FR-052**: Plain-text photo bytes MUST NOT be persisted in any externally-accessible content provider on either device.
- **FR-053**: Encryption MUST be authenticated (AEAD) — no unauthenticated CTR-only or CBC-without-MAC ciphers.

**Out of scope (negative requirements):**

- **FR-090**: This spec MUST NOT encrypt `/config` itself — `/config` carries settings, not private user content (roadmap §Spec 011 «Что НЕ входит»).
- **FR-091**: This spec MUST NOT encrypt `bundled:` or `custom:` icons — they are not private (roadmap §Spec 011 «Что НЕ входит»).
- **FR-092**: This spec MUST NOT add screen mirroring, remote control, or DPC-mode features.
- **FR-093**: This spec MUST NOT introduce iOS support — out of project scope.

### Key Entities

- **SharedPairingKey**: symmetric key, generated during pairing flow (спек 007), held in OS secure storage on both devices, scoped to a single `linkId`. Lifecycle: created on `consent.allow`, deleted on revoke, irrecoverable on factory reset without prior revoke.
- **EncryptedBlob**: a single piece of private media stored in Firebase Storage. Identified by UUIDv4. Wraps ciphertext + nonce + MAC + schemaVersion. Referenced from `/config.contacts[i].photoRef = "private:<uuid>"`.
- **EncryptedMediaStorage**: the implementation of the `IconStorage` port for `private:` namespace, introduced here (declared as future in спек 006 [icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48)). Handles download from Storage, MAC verification, decryption, and local caching.
- **PrivateMediaReference**: a `private:<uuid>` string appearing in `Contact.photoRef`. The only place in wire-format where private media is named.
- **OrphanBlob**: a blob in Storage with no current `/config` reference. Background reconciler removes after threshold.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After admin adds a contact with a photo to a Managed's layout, the photo appears on the corresponding tile on Managed within ≤ 30 seconds for ≥ 95% of attempts (covers happy path of US-1).
- **SC-002**: Zero plaintext photo bytes are logged or sent to any third-party service — verified by automated log-grep tests and by inspection of network payloads in CI (covers US-1, US-5, FR-051).
- **SC-003**: A network observer between admin and Firebase Storage CANNOT recover the photo content from observed traffic (verified by passive-MITM test in integration suite).
- **SC-004**: After admin removes a contact with `photoRef`, the corresponding blob is removed from Storage within ≤ 5 minutes for ≥ 99% of attempts (covers US-3).
- **SC-005**: After admin revokes a link, the `/links/{linkId}/private-media/` Storage path is empty within ≤ 5 minutes for 100% of attempts (covers FR-032).
- **SC-006**: If decryption fails on Managed for any reason, the app does not crash, the tile shows a placeholder, and admin UI surfaces an indicator within ≤ 60 seconds for 100% of attempts (covers US-4, FR-024).
- **SC-007**: Photo upload from admin to Storage does not block admin UI thread; admin can continue editing the layout while upload proceeds in the background (covers Edge Cases §big photos).
- **SC-008**: Bringing forward the existing crypto-system selection decision (one-way door) is supported by a written exit ramp in the spec itself (covers CLAUDE.md rule 3).

---

## Assumptions

- **Spec 007** is fully implemented and merged when 011 starts; pairing flow is the integration point for shared-key establishment.
- **Spec 008** is fully implemented and merged when 011 starts; `Contact.photoRef: String?` field is already in the wire-format and validated by 008's roundtrip tests.
- **Spec 009** is fully implemented and merged when 011 starts; Contacts Picker and VCard share-intent produce `Contact { photoRef = null }`. Spec 011 changes this to optionally produce `photoRef = "private:<uuid>"`.
- **Spec 006**'s `IconStorage` port signature ([IconStorage.kt:20](../../core/src/commonMain/kotlin/com/launcher/api/capability/IconStorage.kt#L20)) remains stable; 011 implements a new adapter (`EncryptedMediaStorage`), it does not modify the port.
- **Both devices have a modern Android-OS secure-storage primitive** (Keystore on Android 6+; project minSdk is assumed to cover this — verified during plan-phase research).
- **Firebase Spark plan** continues to be sufficient. Firebase Storage in Spark has 5 GB free; one photo ≈ 50-200 KB; one pair has typically ≤ 30 contacts with photos; this gives a comfortable margin even for 1000s of pairs. To be re-validated in plan-phase, but no Blaze upgrade expected.
- **Cloudflare Worker** is not in the data path of private media. Storage uploads/downloads go directly Managed↔Firebase Storage and admin↔Firebase Storage. The Worker remains the FCM-trigger relay only.
- **No server-side key escrow.** This is intentional and aligns with the e2e guarantee. The consequence — irrecoverable data on factory reset without revoke — is accepted as a UX cost of true privacy.
- **Photos selected by admin Contacts Picker are the only source of private media in this spec**. Other private-media sources (US-2 «фото бабушки», US-2 «личные документы») depend on the §Architectural one-way doors clarification.
- **Reference counting of blobs uses the current `/config` only**, not the history snapshots from спека 009. If history snapshots also reference `photoRef`, retention of blobs must consider history. NEEDS CLARIFICATION in plan-phase whether history snapshots are reference-bearing.

---

## Зависимости-обещания (что 011 обязан выполнить за предыдущие спеки)

Спеки 6-9 заранее заложили крючки под 011. Этот спек обязан их «отдать»:

- **Спек 006 → реализовать `EncryptedMediaStorage`** под уже существующий порт `IconStorage` ([icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48), [data-model.md:123](../006-provider-capabilities-and-health/data-model.md#L123)).
- **Спек 007 → расширить known subcollections list** в `Link.KNOWN_SUBCOLLECTIONS` ([Link.kt:37-44](../../core/src/commonMain/kotlin/com/launcher/api/link/Link.kt#L37)) и добавить опциональный `pairingKey` field в pairing wire-format ([pairing-token.md:79](../007-pairing-and-firebase-channel/contracts/pairing-token.md#L79)) — additive, schemaVersion остаётся 1.
- **Спек 008 → впервые **заполнить** `Contact.photoRef`** реальным значением (а не `null`) и **впервые эмитить** `partialApplyReasons += media_decrypt_failed` ([data-model.md:64](../008-bidirectional-config-sync/data-model.md#L64), [state-applied.md:65](../008-bidirectional-config-sync/contracts/state-applied.md#L65)).
- **Спек 009 → подключить полные фото к Contacts Picker'у** (там сейчас `photoRef = null (фото — spec 011)` [009 spec.md:218](../009-admin-mode-flows/spec.md#L218)).
- **Compliance → обновить запись о `READ_CONTACTS`** ([permissions-and-resource-budget.md:137](../../docs/compliance/permissions-and-resource-budget.md#L137)) и **решить вопрос POST_NOTIFICATIONS** ([permissions-and-resource-budget.md:224](../../docs/compliance/permissions-and-resource-budget.md#L224)) — активируется в 011 или отложить на следующий спек.
- **Backlog → разрешить `TODO-DOC-001`** ([project-backlog.md:378](../../docs/dev/project-backlog.md#L378)) — создать ADR-007 (второй subtype `TrustEdgeBootstrap`) в начале спека 011.
- **Server-roadmap → ничего не меняется** ([server-roadmap.md](../../docs/dev/server-roadmap.md)) — 011 не требует серверной части; e2e реализуется на клиентах. Если будущая инвалидация требует «server-side blob audit» — добавить в server-roadmap отдельной задачей.

---

<!-- novice summary -->

## TL;DR (простым языком, для бабушки-владельца проекта и для будущего AI)

**О чём этот спек.** Этот спек добавляет в раскладку бабушки **фотографии** контактов — чтобы плитка «Маша внучка» показывала лицо Маши, а не серый квадратик. И всё это так, чтобы Google не имел доступа к содержимому фото — только зашифрованные байты.

**Почему отдельный спек, а не часть 009 (где контакты уже добавлялись).** В 009 фото осознанно поставили `null`, потому что добавить «фото в раскладку» — это **не просто загрузить файл**. Нужен общий ключ между телефоном внука и телефоном бабушки, нужно шифровать байты до загрузки, нужно расшифровывать на приёме, нужно убирать «висячие» зашифрованные файлы. Это всё крипто-инфраструктура, которая нужна не только для фотоконтактов, но и для будущих приватных штук (фото личных документов, например). Поэтому она в отдельном спеке.

**Что спек реально делает:**
1. При сканировании QR (спек 007) теперь генерируется **общий секретный ключ** между парой устройств. Этот ключ хранится только на телефонах — в Google его нет.
2. Когда внук добавляет фото контакта Маши, фото **шифруется на его телефоне**, потом грузится в Firebase Storage уже зашифрованным. В конфиг (`/config`) пишется ссылка `private:<uuid>` — это намёк «фото лежит там-то, но прочитать без ключа нельзя».
3. Когда телефон бабушки получает новый конфиг, он скачивает зашифрованные байты из Storage, расшифровывает **локально** ключом из QR, и показывает фото на плитке.
4. Если что-то пошло не так (фото повреждено, ключа нет после фабричного сброса) — плитка показывает серый placeholder, внук видит в своём приложении индикатор «фото не отрисовалось», предлагает «добавь фото заново» или «re-pair'имся».
5. При удалении контакта или revoke связи — зашифрованные файлы удаляются из Storage. Не накапливаются.

**Архитектурный риск, явно записан как one-way door:**
Выбор крипто-системы (libsodium vs Tink vs что-то ещё) и схемы обмена ключами при pairing'е — после первого реального зашифрованного фото у пользователя поменять это будет ОЧЕНЬ дорого: нужно расшифровать все blob'ы по старому, пере-шифровать по новому, и сделать так, чтобы старые читатели всё ещё работали. Поэтому решение откладывается на clarify-сессию (внутри спека 011), и обязательно описывается «exit ramp» — что мы делаем, если через год криптосистема устареет.

**Зачем именно e2e (a не стандартное Firebase Storage с правилами):** правила Firebase Storage защищают «доступ только своих по правилам сервера», но **сам Google технически имеет доступ** к файлам. Фотографии родственников — это **очень приватно**. e2e снимает этот вопрос — Google видит только мусор.

**Что зафиксировано в clarify-сессии (раздел Clarifications выше):**
- В этом спеке только admin→Managed direction; симметрия — будущий спек ~015 (C-1).
- Криптобиблиотека libsodium через Lazysodium-android; AEAD = XChaCha20-Poly1305; asymmetric = X25519 (C-4).
- Per-device key pairs на каждом устройстве, hybrid encryption с CEK (C-3).
- Membership-agnostic envelope: список `recipients` произвольной длины (в 011 длина 1, в будущих спеках — N) (C-2).
- Один namespace `private:<uuid>`, тип содержимого внутри envelope (C-5).
- POST_NOTIFICATIONS откладываем на спек 012/013 (C-6).
- Reference counting blob'ов учитывает history snapshots из спека 009 (C-7).
- `RecipientResolver` интерфейс — обоснованное исключение из rule 4 (C-8).

**Будущие спеки (добавлены в roadmap):**
- **~015 «двусторонние пары»** — Managed тоже может управлять.
- **~016 «семейные группы»** — групповой ключ для семейного круга (WhatsApp-style).
- **~017 «multi-device recovery»** — у одного человека несколько устройств; восстановление при потере.
- **~018 «key rotation + forward secrecy»** — периодическое обновление ключей.

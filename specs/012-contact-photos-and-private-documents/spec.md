# Feature Specification: Contact Photos and Private Documents

**Feature Branch**: `012-contact-photos-and-private-documents`
**Created**: 2026-05-22 (stub) · **Rewritten**: 2026-05-26 (specify) · **Clarified**: 2026-05-26 (clarify-phase)
**Status**: Draft (clarified) — готов к `speckit-plan` после прогонки checklist'ов.
**Input**: roadmap §Spec 012 ([docs/product/roadmap.md](../../docs/product/roadmap.md)) + brief 2026-05-26 (three-layer architecture + extensibility doc + Konsist gate). Первый visible-client крипто-фундамента спека 011 + универсальный pipeline для любых будущих приватных медиа.

---

## Контекст и зачем нужно

После спеков 8–9 поле `Contact.photoRef` всегда `null`, плитки контактов у бабушки показывают серый placeholder, а «личные документы» как сценарий вообще отсутствуют. Спек 011 (PR #11 смержен) построил e2e-крипто-фундамент: 8 портов, envelope wire-format, Storage adapter (Backblaze B2 через Cloudflare Worker proxy), `BlobReferenceLedger` с reference counting, Konsist fitness gates. Фундамент инфраструктурный — без видимого продукта. Спек 012 — **первый клиент**, который этот фундамент делает видимым.

Параллельно спек 012 закладывает **универсальный pipeline** для приватных медиафайлов, чтобы будущие фичи (голосовые заметки, видео ≥ 30 сек, прикрепления в сообщениях) добавлялись как новые порты рядом, а не модификацией существующих фасадов.

**Зависит от:** спек 011 (крипто-фундамент), спек 008 (`Contact.photoRef`, `PartialReason.MediaDecryptFailed`), спек 009 (VCard share intent, ACTION_PICK для контактов, редактор раскладки).
**НЕ зависит от:** спеков 013+ (модели доверия — 012 использует одностороннюю пару из 011).

**Связанные открытые backlog items**:
- [`TODO-DESIGN-001`](../../docs/dev/project-backlog.md): продуктовая модель «приватные vs shared» документы — UX-design 012 должен покрывать. По умолчанию документы **приватные** (`recipients = [только владелец]`); shared / multi-recipient — отдельный future-flow, не в scope 012.
- [`TODO-ARCH-017`](../../docs/dev/project-backlog.md): multi-identifier contacts — заведён из этого spec'а; до его реализации spec 012 работает на singular `phoneNumber`.
- [`TODO-ARCH-018`](../../docs/dev/project-backlog.md): merge dialog в admin UI — заведён из этого spec'а; spec 012 использует implicit auto-update.
- [`TODO-ARCH-019`](../../docs/dev/project-backlog.md): local storage quota / wipe-on-revoke — заведён из этого spec'а; spec 012 не реализует.

---

## Clarifications

### 2026-05-26 — Pre-plan clarification pass

| # | Question | Resolution |
|---|----------|------------|
| Q1 | Семантика overwrite `Contact.photoRef` при повторном приёме контакта | **Matching по нормализованному singular `phoneNumber`** (через существующий `Contact.fromRaw` PHONE_STRIP_REGEX → `^\+?\d{5,20}$`). Тот же phone → **implicit auto-update** photoRef старого Contact, без диалога; decrement refCount старого blob'а. Multi-identifier (`phoneNumbers[]` + telegram/facebook) и merge dialog с дофильтрованным списком — **out of scope**, заведены как [`TODO-ARCH-017`](../../docs/dev/project-backlog.md) и [`TODO-ARCH-018`](../../docs/dev/project-backlog.md). |
| Q2 | `Tile.documentRef` — additive без bump'a `schemaVersion` или 1 → 2? | **Additive, без bump'a.** До spec 030+ программа **не в production** — backwards-compat constraint спека 011 / rule 5 о read-старых-версий не применим к контракту, который ещё не зафиксирован у пользователей. `Tile` получает новый `kind="document"` вариант + `documentRef: String?`; `metadata.kind` envelope получает значение `"document"`. Sunset этой свободы — **со spec 030**, после первой публикации. |
| Q3 | Konsist fitness gate «UI MUST NOT import crypto» — какой scope? | **Gate отложен.** Защита достигается (а) KDoc-комментариями на фасадных портах `PrivateMediaUploader/Resolver` и на крипто-портах в `core/api/` с явной директивой «not for direct UI use — go through facade»; (б) разделом «когда добавлять новый порт vs переиспользовать существующий» в `docs/dev/private-media-architecture.md`. Generalization — добавлен **новый пункт 8 в Article XI** конституции (`Reuse before invention`) + ссылка из Article XV §8. Это распространяется на **все** порты проекта, не только крипто. |
| Q4 | SAF fallback UX на API < 33 | **MediaPicker adapter возвращает унифицированные bytes** (не URI). Внутри сам выбирает реализацию по API level: 33+ → `ACTION_PICK_IMAGES`; 29-32 → `androidx.activity` PhotoPicker compat; 26-28 → SAF `ACTION_OPEN_DOCUMENT` + копирование во временный app-private файл → чтение bytes → удаление временного. **Никаких hint dialog'ов** перед picker'ом не показывается. Текущий minSdk проекта = **26** (`gradle/libs.versions.toml:6`), значит SAF-ветка обязательна. |
| Q5 | DocumentViewer / DecryptCache durability + `FLAG_SECURE` | Модель проще, чем предполагалось в draft: decrypt **только** один раз при первом download'е (во время loader'а / spinner'а). Расшифрованный файл сохраняется в app-private storage **persistent**. Повторные показы — **мгновенные** (открытие файла). `DecryptCache` (in-memory LRU) **переименован** в `LocalMediaStore` (persistent на диске). **`FLAG_SECURE` не используем**. **Revoke = stop future, не wipe local** — расшифрованные локальные файлы у бабушки остаются. Это согласуется с industry baseline (WhatsApp, Telegram). Защита от переполнения локального storage — out of scope, [`TODO-ARCH-019`](../../docs/dev/project-backlog.md). |

---

## Архитектурное обрамление — три слоя

Чтобы будущие медиа-фичи добавлялись без переписывания, спек 012 строит ТРИ ЛОГИЧЕСКИХ СЛОЯ.

### Слой 1 — Pipeline (один, media-type-agnostic, context-agnostic)

- **`PrivateMediaUploader`** — фасад поверх 011-примитивов: `bytes + kind → encrypt (AeadCipher) → upload (EncryptedMediaStorage) → запись BlobReferenceLedger → вернуть "private:<uuid>"`. Используется одинаково и для фото контакта, и для документа.
- **`PrivateMediaResolver`** — обратное направление: `"private:<uuid>" → LocalMediaStore lookup → если на диске нет: download → decrypt → save to LocalMediaStore → возврат файла / bytes → IconResolution.Bitmap`. Это **реализация** `IconStorage` namespace `"private:"`, обязательство которой зарезервировано в [спеке 006 icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48). **Decrypt происходит ровно один раз** — при первом download'е; дальше файл лежит расшифрованным в app-private storage и открывается мгновенно.
- **`LocalMediaStore`** — persistent app-private storage (`Context.filesDir/private-media/<uuid>`) для уже расшифрованных файлов. **Не LRU, не in-memory**, не очищается под memory pressure. Этим отличается от первоначального draft'а «DecryptCache» — после clarification Q5 принята модель «WhatsApp/Telegram baseline: decrypt at download, store decrypted persistently». Защита от переполнения отложена как [`TODO-ARCH-019`](../../docs/dev/project-backlog.md).
- **Honest error reporting** — `CryptoError` (на этапе download / decrypt при первом обращении) маппится в `PartialReason.MediaDecryptFailed` (впервые в проекте эмитится из реального кода) и отображается у admin'а индикатором.

### Слой 2 — MediaPicker (один порт, один adapter с внутренней API-level dispatch)

- **`MediaPicker` port** с параметрами `(kind: image|video|any, maxItems: Int, mode: gallery|folders)`. **Возвращает унифицированный `MediaPickResult(bytes: ByteArray, mimeType: String, sourceLabel: String?)`** — caller никогда не видит URI или платформенные типы.
- **`SystemPhotoPickerAdapter`** — единственный adapter, **внутри себя** выбирает реализацию по API level (Anti-Corruption Layer / CLAUDE.md rule 2):
  - **API 33+** → `ACTION_PICK_IMAGES` (нативный Photo Picker, без permission).
  - **API 29-32** → `androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()` (compat picker, без permission).
  - **API 26-28** (`minSdk` проекта) → SAF `ACTION_OPEN_DOCUMENT` (без permission) → возврат `Uri` → копирование во временный app-private файл → чтение bytes → удаление временного файла. Caller получает ровно те же bytes, как на 33+.
- **Никаких hint dialog'ов** перед запуском picker'а не показывается. Adapter диктует UX. Системный picker (или SAF на старых API) уже покрывает: сортировку по дате, переключение фото/видео, навигацию по альбомам/папкам, сбор со всех источников. Кастомизация = параметры запуска.

### Слой 3 — Attachment context (два первых клиента, каждый — отдельный UX-flow)

- **Context A «фото контакта»**: фото приходит **внутри payload контакта** при share intent из WhatsApp/Telegram/системных Contacts (поле `PHOTO` в VCard) ИЛИ при `ACTION_PICK` из системной книжки. **MediaPicker НЕ используется.** Полученные байты передаются в `PrivateMediaUploader`, результат сохраняется в `Contact.photoRef`. Viewer — маленькая аватарка на плитке.
- **Context B «фото личных документов»**: admin в редакторе раскладки спека 009 жмёт «+ документ» → MediaPicker (параметры: image-only, max 1) → выбранные байты + label → `PrivateMediaUploader` → сохранение как **новый sealed variant** `Tile(kind="document", documentRef, label)` (additive к `/config`, без bump'a `schemaVersion` — обоснование в Clarification Q2). Viewer — fullscreen `DocumentViewer` по тапу бабушки.

### Extensibility (документируется в plan-phase)

Слой 1 расширяется **добавлением новых портов рядом**, не модификацией существующих фасадов:
- Future `StreamingMediaUploader` (большие видео ≥ 30 сек, chunked resumable) — отдельный порт, использует те же 011-примитивы снизу. `PrivateMediaUploader` НЕ модифицируется.
- Future `MediaRecorder` (live-запись голосовых заметок) — отдельный порт.
- Future `RangeDecrypt` (seekable playback видео) — расширение `AeadCipher` или отдельный порт в video-спеке.

`PrivateMediaUploader / PrivateMediaResolver / DecryptCache` — фасады для **blob-shaped медиа**. Это **не god-port'ы**. Новые режимы (streaming/recording) приходят как отдельные порты рядом.

Слой 2 расширяется новыми параметрами или новыми adapter'ами под платформенные API без изменения сигнатуры порта.

Слой 3 расширяется новыми attachment-полями (future `Tile.audioRef`, `Message.attachmentRef`), которые дёргают слой 1 и 2.

**Документ**: `docs/dev/private-media-architecture.md` (создаётся в plan-phase). Отвечает на: «какой порт использовать для нового медиа-типа?», «как добавить streaming?», «куда положить новый picker adapter?», «когда оправдан новый порт vs параметр существующего» (Article XI §8 — Reuse before invention).

**Защита фасадов — без Konsist gate**: после clarification Q3 принято решение **не вводить** автоматический fitness gate в спеке 012. Защита делается двумя путями:
- **KDoc-комментарии** на фасадных портах (`PrivateMediaUploader / PrivateMediaResolver`) и на крипто-портах из 011 (`AeadCipher / AsymmetricCrypto / EncryptedMediaStorage / DigitalSignature / HashFunction / SecureKeystore`) — текст «not for direct use from UI / domain code — go through `PrivateMediaUploader` / `PrivateMediaResolver`. See [docs/dev/private-media-architecture.md]».
- **Constitution amendment**: добавлен **Article XI §8** «Reuse before invention» + связанный пункт в Article XV §8 (AI Agent Operating Rules). Это распространяется на весь проект, не только крипто — AI-агент обязан сначала проверить существующие порты, прежде чем создать новый. Реальный автоматический Konsist gate можно ввести позже, когда появится первое нарушение.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Фото контакта на плитке у бабушки (Priority: P1)

Admin получает контакт внучки Маши одним из путей спека 009 — share intent из WhatsApp / Telegram (VCard с полем `PHOTO`) или `ACTION_PICK` из системной книжки контактов. Если в payload есть фото, оно шифруется через слой 1, грузится в Storage, `Contact.photoRef = "private:<uuid>"` уходит в `/config`, пушится. Через ≤ 30 секунд у бабушки на плитке «Маша» — реальная фотография вместо серого placeholder'a.

**Why this priority**: главная видимая ценность фундамента 011 — фотография любимого человека на крупной плитке. Без этого спек 011 остаётся «невидимой инфраструктурой». Эта история одна обеспечивает MVP.

**Independent Test**: на стенде admin делает share-intent контакта с фото из WhatsApp на launcher; через ≤ 30 секунд на парном устройстве бабушки на плитке этого контакта виден ровно тот аватар, который был в payload, и плитка остаётся tap-target ≥ 56dp.

**Acceptance Scenarios**:

1. **Given** admin запустил share intent контакта с фото из WhatsApp, **When** контакт добавлен, **Then** на парном устройстве бабушки через ≤ 30 секунд на соответствующей плитке видна фотография (не placeholder), и она расшифрована честно (хэш расшифрованных байт = хэш исходного фото).
2. **Given** admin выбрал контакт через `ACTION_PICK` из системной книжки, у которого в системе нет фото, **When** контакт добавлен, **Then** плитка показывает крупный placeholder с инициалом, без crash, и в `/state.partialApplyReasons` НЕТ `media_decrypt_failed` (это «фото не было», а не «фото не расшифровалось»).
3. **Given** контакт уже существует с photoRef = "private:OLD", **When** admin прислал новый share intent с другим фото того же контакта, **Then** старый blob помечается к удалению через `BlobReferenceLedger`, новый blob грузится, `Contact.photoRef = "private:NEW"`, через ≤ 30 секунд плитка обновляется.

---

### User Story 2 — Фото личных документов бабушки (Priority: P2)

Admin в редакторе раскладки спека 009 жмёт «+ документ» → системный Photo Picker → выбирает фото из галереи (паспорт, СНИЛС, медкарта, страховой полис) → вводит подпись («Паспорт») → фото шифруется через слой 1, грузится в Storage, новая плитка с `documentRef` + label попадает в `/config`. У бабушки появляется плитка «Паспорт» с фото-превью. Тап → fullscreen DocumentViewer с pinch-to-zoom и крупной кнопкой «закрыть».

**Why this priority**: реальная боль пожилых пользователей — постоянная потребность показать документ (в МФЦ, поликлинике, банке) без помощи родственника рядом. P2 после P1, потому что один P1 уже даёт ценность; P2 добавляет новый UX-flow и новый wire-format-field.

**Independent Test**: admin добавляет плитку «Паспорт» через «+ документ»; на парном устройстве бабушки появляется плитка с миниатюрой; бабушка тапает → видит fullscreen viewer с паспортом и кнопкой «закрыть» размером ≥ 56dp.

**Acceptance Scenarios**:

1. **Given** admin открыл редактор раскладки, **When** жмёт «+ документ» → выбирает фото из галереи → вводит «Паспорт» → подтверждает, **Then** через ≤ 30 секунд у бабушки на главном экране появляется плитка «Паспорт» с миниатюрой фото и tap-target ≥ 56dp.
2. **Given** на устройстве бабушки есть плитка «Паспорт», **When** бабушка тапает по ней, **Then** открывается fullscreen viewer, фото занимает максимум экрана, кнопка «закрыть» крупная (≥ 56dp), доступна pinch-to-zoom.
3. **Given** admin создал плитку «Медкарта» с фото, **When** envelope уходит в Storage, **Then** sensitive label «Медкарта» НЕ присутствует в `metadata` map envelope (был бы видим серверу) — он зашифрован внутри ciphertext (privacy-инвариант спека 011).
4. **Given** admin удаляет плитку «Паспорт» из раскладки, **When** проходит ≤ 5 минут, **Then** связанный blob удаляется из Storage (через `BlobReferenceLedger.refCount = 0`), и `EncryptedMediaStorage.exists(linkId, uuid)` возвращает `false`.

---

### User Story 3 — Честное сообщение об ошибках расшифровки (Priority: P2)

Если `PrivateMediaResolver` падает при decrypt (`CryptoError.MacFailed`, `KeyNotFound`, `BlobMissing`, `RecipientNotFound`) — плитка показывает placeholder с именем контакта / подписью документа крупным шрифтом, работоспособность плитки сохраняется. `/state.partialApplyReasons` **впервые в проекте** эмитит `"media_decrypt_failed"`. Admin UI показывает индикатор «фото не отрисовалось у <имя Managed>» с подсказкой «пере-добавить» или «повторить pairing».

**Why this priority**: без честного reporting'а silent breakage невозможно отладить. Эта история активирует enum value, зарезервированный с спека 008, и закрывает один из обязательств спека 008 (state-applied.md:65).

**Independent Test**: подмена envelope'a в Storage (или вставка повреждённого фикстура) → плитка у бабушки не падает, остаётся работоспособной; индикатор у admin'а появляется в течение времени применения config-снапшота.

**Acceptance Scenarios**:

1. **Given** envelope в Storage повреждён (MAC сломан), **When** бабушкин device пытается отрисовать плитку, **Then** плитка показывает placeholder + имя/label крупным шрифтом, плитка остаётся tap-target ≥ 56dp, и `/state/current.partialApplyReasons` содержит `media_decrypt_failed`.
2. **Given** blob удалён из Storage (`BlobMissing`), **When** Resolver пытается его download'нуть, **Then** plate fallback на placeholder, `partialApplyReasons += media_decrypt_failed`, индикатор у admin'a содержит подсказку «пере-добавить контакт/документ».
3. **Given** ключ устройства бабушки изменился (re-pairing был неполный), **When** Resolver не находит свой `deviceId` в `recipients` (`RecipientNotFound`), **Then** placeholder + индикатор с подсказкой «повторить pairing».

---

### User Story 4 — Cleanup при удалении контакта или документа (Priority: P2)

Admin удаляет контакт «Маша» или плитку-документ из раскладки. Через ≤ 5 минут связанный blob удаляется из Storage (если `refCount = 0` по `BlobReferenceLedger`). Логика уже реализована в спеке 011 — спек 012 только активирует наполнение references реальными значениями `photoRef / documentRef`.

**Why this priority**: без cleanup'a Storage растёт без ограничения → удар по Spark/B2 квотам. Однако вся логика уже в 011; 012 только дёргает её правильно. Низкий риск, P2.

**Independent Test**: добавить контакт с фото → удалить контакт; через ≤ 5 минут `EncryptedMediaStorage.exists(linkId, uuid) == false` для blob'а этого фото.

**Acceptance Scenarios**:

1. **Given** контакт с `photoRef = "private:UUID-X"` существует и refCount = 1, **When** admin удаляет контакт и пушит config, **Then** через ≤ 5 минут blob удалён из Storage.
2. **Given** документ с `documentRef = "private:UUID-Y"` существует, **When** admin удаляет плитку-документ, **Then** blob удалён в течение того же интервала.
3. **Given** один blob используется в двух местах (теоретически — admin может сделать дубликат), **When** удалена ОДНА ссылка, **Then** refCount уменьшается на 1, blob остаётся в Storage (НЕ удаляется), пока refCount > 0.

---

### Edge Cases

- **Фото в payload контакта весит > 500 KB (Storage cap)** — admin-side прозрачно ресайзит/жмёт JPEG ≤ 80% качества до того, как байты уходят в `PrivateMediaUploader`. Если даже после двух итераций сжатия > 500 KB → ошибка admin-UI «фото слишком большое», блокирует добавление, плитка остаётся с placeholder.
- **Пользователь выбрал видео вместо фото** в Photo Picker (параметр `kind = image`, но picker всё равно отдал видео из-за UI bug на OEM) — adapter валидирует MIME, отвергает с понятным сообщением, без crash.
- **Network loss во время upload'a** — `PrivateMediaUploader` возвращает `CryptoError.StorageFailure(IOException)`, admin UI показывает «нет сети, попробую снова» с retry button. Никакого partial blob в Storage (либо весь, либо ничего).
- **Bыбытие файла из `LocalMediaStore`** (например, пользователь очистил app data) — Resolver не находит файл, делает download+decrypt заново, плитка показывает placeholder ≤ 3 секунд, потом обновляется. **Memory pressure НЕ удаляет файлы** (файлы на диске, не в памяти).
- **Бабушка тапнула DocumentViewer на впервые-смотримый документ** — viewer открывается с progress indicator (≤ 3 сек на medium-tier на первый download), затем фото появляется и остаётся доступным мгновенно при повторных открытиях. Нет «пустого экрана» без feedback.
- **`metadata.kind` envelope'а не соответствует контексту** (например, blob с `kind="document"` подсунули в Contact.photoRef) — Resolver валидирует и возвращает placeholder с `media_decrypt_failed`. Защищает от admin-bug'ов в будущих спеках.
- **Storage quota исчерпана (Backblaze B2 ≥ 5 GB на link)** — `PrivateMediaUploader` возвращает `QuotaExceededException`, UI говорит «у бабушки много фото, удалите часть».
- **Pinch-to-zoom не работает на старом OEM** — graceful: viewer всё равно показывает фото в максимальном размере fit-to-screen; жест не обязателен для базового просмотра.

---

## Requirements *(mandatory)*

### Functional Requirements — Layer 1 (Pipeline)

- **FR-001**: `PrivateMediaUploader` MUST принимать `(bytes: ByteArray, kind: PrivateMediaKind, linkId: LinkId)`, шифровать через `AeadCipher` (спек 011), грузить через `EncryptedMediaStorage`, регистрировать blob в `BlobReferenceLedger`, и возвращать `Outcome<IconRef("private:<uuid>"), CryptoError>`.
- **FR-002**: `PrivateMediaResolver` MUST реализовать `IconStorage.resolve("private:<uuid>")` per [icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48): если файл уже в `LocalMediaStore` → возврат `IconResolution.Bitmap` из файла; если нет — download → **однократный** decrypt → запись в `LocalMediaStore` → возврат `IconResolution.Bitmap`. На любую ошибку download/decrypt — `IconResolution.Placeholder` + structured log + эмит `PartialReason.MediaDecryptFailed`.
- **FR-003**: `LocalMediaStore` MUST хранить уже расшифрованные файлы в `Context.filesDir/private-media/<uuid>` **persistent** (НЕ in-memory, НЕ LRU, НЕ очищается под memory pressure). Файлы выживают process death и device reboot. Защита от переполнения и wipe-on-revoke — out of scope spec 012, отслеживается [`TODO-ARCH-019`](../../docs/dev/project-backlog.md).
- **FR-004**: **Первая** загрузка blob'a (download + decrypt + запись в LocalMediaStore) MUST завершаться за ≤ 3 секунды на medium-tier устройстве (3 Mbit/s + Snapdragon 6xx-tier CPU) для blob'a ≤ 500 KB. **Повторные** показы того же blob'a (файл уже в LocalMediaStore) MUST открываться визуально мгновенно (≤ 100 мс — обычное чтение app-private файла).
- **FR-005**: Все криптографические операции (encrypt при upload'е, decrypt при первом download'е) MUST идти через фасады `PrivateMediaUploader / PrivateMediaResolver`. **Защита фасадов делается KDoc-комментариями** на портах `AeadCipher / AsymmetricCrypto / EncryptedMediaStorage / DigitalSignature / HashFunction / SecureKeystore` в `core/api/**` с явной директивой «not for direct use — go through `PrivateMediaUploader/Resolver` facade. See [docs/dev/private-media-architecture.md]». **Konsist fitness gate отложен** (см. Clarification Q3) — добавляется отдельным backlog item'ом при появлении первого реального нарушения. Generalization защиты — Article XI §8 конституции (`Reuse before invention`).
- **FR-006**: `PrivateMediaUploader` MUST добавлять `metadata.kind` (значения: `"image"`, `"document"`) в envelope. **Sensitive label** (например, «Паспорт», «Медкарта») MUST шифроваться **внутри ciphertext**, НЕ помещаться в `metadata`. (Privacy-инвариант спека 011 — metadata выходит из envelope в plaintext.)

### Functional Requirements — Layer 2 (MediaPicker)

- **FR-007**: `MediaPicker` port MUST принимать параметры `(kind: image | video | any, maxItems: Int, mode: gallery | folders)` и возвращать `Outcome<List<MediaPickResult>, MediaPickerError>`, где `MediaPickResult = (bytes: ByteArray, mimeType: String, sourceLabel: String?)`. **Caller никогда не видит URI** или платформенные типы (`Uri`, `Intent`) — это Anti-Corruption Layer (CLAUDE.md rule 2).
- **FR-008**: `SystemPhotoPickerAdapter` MUST выбирать реализацию **внутри себя** по `Build.VERSION.SDK_INT`: **API 33+** → `ACTION_PICK_IMAGES`; **API 29-32** → `androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()`; **API 26-28** → SAF `ACTION_OPEN_DOCUMENT` + копирование выбранного контента во временный app-private файл (`Context.cacheDir`) → чтение bytes → удаление временного файла. **Никаких новых permissions** не вводится спеком 012 — все три ветки работают без `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES`. **Никаких user-facing hint dialog'ов** «у вас старый Android» не показывается ни одной из веток.
- **FR-009**: Adapter MUST валидировать MIME type выбранного файла против запрошенного `kind` (`image` → `image/*`, `video` → `video/*`) и отвергать несоответствие с `MediaPickerError.InvalidMimeType` (без crash).
- **FR-010**: Adapter MUST отдавать конкретно те байты, которые выбрал пользователь, и НЕ дёргать какие-либо метаданные EXIF, кроме orientation (для корректной ориентации фото при последующем decode).

### Functional Requirements — Layer 3 (Attachment context A: Contact Photos)

- **FR-011**: Когда admin принимает share intent VCard из WhatsApp/Telegram с полем `PHOTO`, system MUST извлечь bytes этого поля, декодировать base64, передать в `PrivateMediaUploader(kind = "image")`, и сохранить результат в `Contact.photoRef`.
- **FR-012**: Когда admin выбрал контакт через `ACTION_PICK` из системной книжки, system MUST прочитать `ContactsContract.Contacts.Photo.PHOTO` (если есть) и обработать так же. Если фото отсутствует — `photoRef` остаётся `null` (это НЕ ошибка).
- **FR-013**: При повторном приёме контакта система MUST применить **implicit auto-update photoRef** для существующего `Contact`'a, найденного по **точному совпадению нормализованного `phoneNumber`** (нормализация через существующий `Contact.fromRaw` PHONE_STRIP_REGEX, формат после нормализации `^\+?\d{5,20}$`). Семантика: (а) найден существующий Contact с тем же phone → старый photoRef получает decrement `BlobReferenceLedger.refCount`, новое фото загружается, новый `photoRef` записывается в существующий Contact (его `id` сохраняется); (б) не найден → insert нового Contact с новым `id`. **Никакого UI dialog'а «обновить или добавить»** в спеке 012 нет (см. [`TODO-ARCH-018`](../../docs/dev/project-backlog.md)). При refCount = 0 удаление blob'a идёт по обычному housekeeping cadence спека 011 (≤ 5 минут до next reconciliation cycle).
- **FR-014**: UI плитки контакта MUST показывать аватар как круг с min tap-target = **56dp** (project senior-safe override per Article VIII §7). Placeholder = крупный инициал имени на нейтральном фоне с контрастом ≥ 4.5:1.

### Functional Requirements — Layer 3 (Attachment context B: Private Documents)

- **FR-015**: Редактор раскладки (спек 009) MUST показывать кнопку «+ документ» рядом с существующими «+ контакт» / «+ приложение». Tap → запуск `MediaPicker(kind = image, maxItems = 1, mode = gallery)`.
- **FR-016**: После выбора фото admin MUST увидеть форму с полями `label: String` (validation: trim, 1..40 graphemes, sanitised) и preview thumbnail. Подтверждение → `PrivateMediaUploader(kind = "document")` + сохранение label **внутри ciphertext** (НЕ в metadata).
- **FR-017**: Новый sealed variant `Tile.kind="document"` (с полем `documentRef: String`) MUST быть добавлен в `Tile` иерархию и в `/config` wire format **аддитивно**, **без bump'a `schemaVersion`**. Это допустимо до spec 030+: программа не находится в production, обратная совместимость с уже-развёрнутыми клиентами не требуется (см. Clarification Q2). После spec 030 эта свобода истекает и любое расширение `Tile` потребует `schemaVersion bump + reader migration` по rule 5 CLAUDE.md. Существующие in-progress readers MUST читать config без crash (если попадётся `kind="document"` до обновления — эмитят `PartialReason.UnknownSlotKind` per [state-applied.md:67](../008-bidirectional-config-sync/contracts/state-applied.md#L67)).
- **FR-018**: При тапе по плитке-документу у бабушки system MUST открыть fullscreen `DocumentViewer`. Viewer показывает: фото на максимум экрана, label крупным шрифтом сверху, кнопку «Закрыть» ≥ 56dp снизу-справа. **Файл уже расшифрован и лежит в `LocalMediaStore`** — открывается мгновенно (≤ 100 мс).
- **FR-019**: `DocumentViewer` MUST поддерживать pinch-to-zoom (1.0×..4.0×) и pan. Если жест не сработал (старый OEM) — fit-to-screen остаётся работоспособным.
- **FR-020**: Progress indicator (`CircularProgressIndicator`-equivalent) MUST показываться **только при первом** download'е документа (когда файл ещё не в `LocalMediaStore`); индикатор исчезает при появлении фото или при ошибке. При повторных открытиях того же документа индикатор НЕ показывается — фото появляется сразу. **`FLAG_SECURE` НЕ устанавливается** на DocumentViewer (см. Clarification Q5 — модель «после download файл — обычный app-private, защита экрана не наша ответственность; согласуется с WhatsApp/Telegram baseline»).

### Functional Requirements — Honest reporting & Cleanup

- **FR-021**: На любую `CryptoError` при resolve'е `private:<uuid>` system MUST: (1) показать placeholder, (2) обновить `/state/current.partialApplyReasons += MediaDecryptFailed`, (3) залогировать структурированное событие категории `media_decrypt_failed` с подкатегорией (`mac_failed`, `blob_missing`, `key_not_found`, `recipient_not_found`).
- **FR-022**: Admin UI MUST показывать индикатор «фото не отрисовалось у <Managed display name>» рядом с соответствующим контактом / документом, с подсказкой действия (`re-add` для `mac_failed | blob_missing`, `re-pair` для `key_not_found | recipient_not_found`).
- **FR-023**: При удалении `Contact` или `Tile.documentRef` system MUST декрементировать `BlobReferenceLedger.refCount` ровно один раз за удаление. На refCount = 0 housekeeping спека 011 удаляет blob в течение ≤ 5 минут.

### Functional Requirements — Extensibility documentation

- **FR-024**: Plan-phase MUST создать `docs/dev/private-media-architecture.md` со следующими разделами: (1) overview трёх слоёв; (2) когда добавлять новый порт vs расширять параметры; (3) как добавить streaming-uploader / recorder / range-decrypt в будущем; (4) Konsist gate и что он защищает; (5) приватность metadata vs ciphertext.

### Key Entities

- **PrivateMediaKind**: enum `Image | Document` (расширяется новыми вариантами в будущих спеках без bump'a wire-format — value сохраняется в `metadata.kind` как строка).
- **PrivateMediaUploader**: domain port (фасад) — принимает байты + kind, возвращает `IconRef`. Реализация в adapter-модуле слоя 011.
- **PrivateMediaResolver**: domain port (фасад) — принимает `IconRef`, возвращает `IconResolution`. Реализация в adapter-модуле слоя 011.
- **LocalMediaStore**: domain port + adapter поверх `Context.filesDir/private-media/`. Persistent (не in-memory, не LRU). Файлы — расшифрованные, открываются мгновенно как обычные app-private файлы.
- **MediaPicker**: domain port. Реализация — `SystemPhotoPickerAdapter` в android adapter-модуле.
- **DocumentViewer**: presentation-слой, fullscreen Compose screen. Параметры: `documentRef`, `label`.
- **Tile.documentRef**: новое nullable поле в существующей сущности `Tile` (config wire format). Содержит `IconRef` в namespace `private:`.
- **`metadata.kind` envelope key**: новый ключ в envelope `metadata` map (свобода контента, определяется спеком 012). Значения: `"image"`, `"document"`.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: От share intent контакта с фото у admin'а до видимой фотографии на плитке у бабушки проходит ≤ **30 секунд** при стабильной сети (Wi-Fi или 4G ≥ 3 Mbit/s). Измеряется stopwatch'ом на 10 проходах подряд; p95 ≤ 30s.
- **SC-002**: От тапа admin'а «+ документ» → выбор фото → подтверждение, до видимой плитки-документа у бабушки проходит ≤ **30 секунд** при стабильной сети. p95 ≤ 30s на 10 проходах.
- **SC-003**: **Повторная** отрисовка плитки / документа (файл уже в `LocalMediaStore`) — ≤ **100 мс** (визуально мгновенно — обычное чтение app-private файла). **Первая** загрузка (download blob + decrypt + запись в LocalMediaStore) — ≤ **3 секунды** на medium-tier device (Snapdragon 6xx-class, 3 Mbit/s) для blob'a ≤ 500 KB.
- **SC-004**: При повреждении envelope'а 100% случаев плитка остаётся работоспособной (НЕ crash, tap target ≥ 56dp), и индикатор у admin'а появляется в ≤ 60 секунд (время применения config-снапшота).
- **SC-005**: При удалении контакта/документа blob удаляется из Storage в ≤ **5 минут** (housekeeping cadence спека 011) в 100% случаев на стенде. Проверяется `EncryptedMediaStorage.exists(...) == false`.
- **SC-006**: APK delta спека 012 ≤ **500 KB** относительно main до merge'а. Измеряется в release-build с R8.
- **SC-007**: 100% сценариев add/view/delete фото и документа работоспособны без READ_EXTERNAL_STORAGE / READ_MEDIA_IMAGES / READ_MEDIA_VIDEO permissions.
- **SC-008**: 0 случаев утечки sensitive label в `envelope.metadata` (проверяется static analysis тестом, который шифрует document с label «TestLabelLeak» и парсит обратно plaintext metadata — leak строки = fail).
- **SC-009**: Все 6 крипто-портов 011 (`AeadCipher / AsymmetricCrypto / EncryptedMediaStorage / DigitalSignature / HashFunction / SecureKeystore`) **и оба фасада** 012 (`PrivateMediaUploader / PrivateMediaResolver`) содержат KDoc-комментарий с явной директивой о допустимом use-site и ссылкой на `docs/dev/private-media-architecture.md`. Проверяется ручным review артефактов в `speckit-analyze`. Konsist gate отложен (см. Clarification Q3 + `TODO-ARCH-016`-в-конституции).
- **SC-010**: `/state/current.partialApplyReasons` содержит `media_decrypt_failed` в 100% случаев, когда `PrivateMediaResolver` возвращает не-Bitmap. (Закрывает обязательство спека 008.)
- **SC-011**: 100% строк UI (DocumentViewer header, indicator у admin'а, error messages, picker entry button) локализованы ru + en через `strings.xml` (ADR-004 compliance).

---

## Assumptions

- Спек 011 смержен в main к моменту старта реализации 012 (true на 2026-05-26 — PR #11 уже merged, см. `git log`).
- Спек 009 готов к моменту реализации: VCard share intent работает, `ACTION_PICK` для контактов работает, редактор раскладки имеет расширяемый набор кнопок «+ X» (PR #9 merged).
- **`minSdk = 26`** (Android 8, проверено в `gradle/libs.versions.toml:6`). Значит ветка SAF `ACTION_OPEN_DOCUMENT` для API 26-28 в `SystemPhotoPickerAdapter` обязательна; ветка `ACTION_PICK_IMAGES` работает на API 33+; ветка `androidx PhotoPicker compat` — на API 29-32.
- **Программа НЕ в production** (запуск планируется со spec 030+). До этого момента изменения wire-format'ов считаются additive **без bump'a `schemaVersion`** — обратной совместимости с уже-развёрнутыми клиентами не требуется (см. Clarification Q2). После spec 030 эта свобода истекает.
- `BlobReferenceLedger` спека 011 уже корректно реализован и протестирован (FR-021 спека 011 + соответствующие smoke).
- Backblaze B2 через Cloudflare Worker proxy (спек 011 PR #11) даёт достаточный throughput для blob'ов ≤ 500 KB на 3 Mbit/s сетях.
- Admin-side JPEG compression до ≤ 500 KB реалистична для типичных фото с современных Android-камер (обычно 2-4 MB raw, после quality 80% ≈ 200-400 KB).
- Sensitive label («Паспорт», «Медкарта») остаётся ≤ 40 graphemes; внутри ciphertext с фото это +40 байт overhead — незначительно.
- **`LocalMediaStore` на app-private storage** не упирается в ограничения на устройстве в типичном сценарии (≈ 30 MB на пару при 100 контактах + 50 документов × ~200 KB). Защита от переполнения — out of scope ([`TODO-ARCH-019`](../../docs/dev/project-backlog.md)).
- iOS — out of project scope.
- Threat model spec 012: admin = доверенный родственник, **не adversarial**. Защита от device-local adversary (физический доступ к телефону бабушки, root, adb backup) — out of scope; это согласуется с industry baseline (WhatsApp, Telegram, Signal в default режиме).

---

## Out of Scope (явные не-цели)

- **Любая криптография** — в спеке 011 (фундамент). 012 только потребляет порты через фасады.
- **`StreamingMediaUploader` / `MediaRecorder` / `RangeDecrypt`** — отдельные будущие спеки. 012 только спроектирован так, чтобы их добавление было аддишеном, не рерайтом.
- **Realtime stream encryption (SRTP/SFrame)** — НИКОГДА не через envelope 011. Отдельный future Jitsi-integration спек.
- **Drift detection** (фото в контактной книге admin'а обновилось — автосинхронизация на устройство бабушки) — backlog `TODO-ARCH-013`.
- **Multi-photo selection** — `MediaPicker.maxItems` параметр поддерживается, но в 012 везде передаётся `maxItems = 1`. Расширение в спеке, где это нужно.
- **Видео / аудио** — не в 012; отдельные спеки.
- **iOS** — out of project scope.
- **Merge dialog «обновить или добавить»** в admin UI при повторном приёме контакта — [`TODO-ARCH-018`](../../docs/dev/project-backlog.md). Spec 012 использует implicit auto-update по совпадению `phoneNumber`.
- **Multi-identifier контакты** (`phoneNumbers[]` + telegram/facebook/etc.) — [`TODO-ARCH-017`](../../docs/dev/project-backlog.md). Spec 012 работает на singular `phoneNumber` из спека 008.
- **Shared / multi-recipient документы** ([`TODO-DESIGN-001`](../../docs/dev/project-backlog.md)) — все документы в spec 012 **приватные** (`recipients = [только владелец]`). Возможность поделиться документом с третьим лицом — отдельный future-flow.
- **Local quota / wipe-on-revoke** для `LocalMediaStore` — [`TODO-ARCH-019`](../../docs/dev/project-backlog.md). Spec 012: расшифрованные файлы persistent, не удаляются автоматически, не очищаются при revoke link'а. Revoke = «stop future», не «wipe past».
- **`FLAG_SECURE`** на DocumentViewer / admin upload screen / launcher home — НЕ устанавливается. После download файл — обычный app-private файл; скриншоты, screen recording, recents preview не блокируются. Согласуется с baseline WhatsApp/Telegram.
- **Защита от device-local adversary** (физический доступ к разблокированному телефону, root, adb backup) — out of scope. Threat model spec 012 покрывает только защиту канала «сервер ↔ клиент» и «admin ↔ Managed».
- **Konsist fitness gate против прямого UI→crypto import'а** — отложен (см. Clarification Q3). Защита — KDoc-комментариями + Article XI §8 конституции.

---

## Dependencies fulfilled by this spec (обязательства, закрываемые 012)

- **Спек 006** → реализация `IconStorage` namespace `"private:"` ([icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48)).
- **Спек 008** → первое реальное наполнение `Contact.photoRef` ненулевым значением + первое реальное эмитирование `PartialReason.MediaDecryptFailed` ([data-model.md:64](../008-bidirectional-config-sync/data-model.md#L64), [state-applied.md:65](../008-bidirectional-config-sync/contracts/state-applied.md#L65)).
- **Спек 009** → подключение photo-bytes к VCard share intent + `ACTION_PICK` контактов; интеграция «+ документ» в редактор раскладки.
- **Спек 011** → первый клиент `PrivateMediaUploader/Resolver/DecryptCache` использует все 8 портов 011 через фасады; валидирует, что 011 scope не содержит unused абстракций (CLAUDE.md rule 4).

---

## Когда разрабатывается

Сейчас (post-merge 011, branch `012-contact-photos-and-private-documents` создан 2026-05-26). Ожидаемый scope при decompose'е — **30–40 task'ов**, **3–4 недели**. Большая часть времени — UI (DocumentViewer, admin upload progress, indicator) + extensibility-документация + Konsist gate.

---

<!-- novice summary -->

## TL;DR (простым языком)

**О чём этот спек.** Первый видимый продукт на крипто-фундаменте 011. Здесь появляются фото контактов на плитках у бабушки + новая возможность «личные документы» (паспорт, СНИЛС, медкарта).

**Что делается:**
1. Admin принимает контакт с фото (через WhatsApp share или системную книжку) → фото шифруется → у бабушки через ≤ 30 секунд на плитке появляется реальная фотография.
2. Admin жмёт «+ документ» в редакторе → выбирает фото паспорта из галереи → подписывает «Паспорт» → у бабушки появляется плитка «Паспорт» с миниатюрой. Тап → fullscreen viewer с большой кнопкой «закрыть».
3. Если фото повреждено / ключа нет — плитка не падает, показывает имя/подпись крупно, admin видит понятный индикатор.
4. Удалили контакт/документ → blob в облаке стирается через ≤ 5 минут.

**Архитектурный фокус.** Три слоя: (1) pipeline шифрования/расшифровки, (2) picker, (3) UX-flow'ы. Сделано так, чтобы будущие медиа-фичи (голосовые, видео, прикрепления) добавлялись **как новые порты рядом**, а не модификацией существующих фасадов. Документация `docs/dev/private-media-architecture.md` объяснит будущему agent'у/разработчику «куда что добавлять».

**Модель шифрования** (упрощённо после clarify-сессии): шифруем при upload'е на сервер, расшифровываем **один раз** при download'е, дальше расшифрованный файл лежит у бабушки в обычной app-папке. Повторный показ — мгновенный. Это та же модель, что WhatsApp/Telegram: сервер не видит контент, а на самом телефоне файл — обычный.

**Защита от ошибок в будущем.** Вместо автоматического CI-теста (Konsist gate) сейчас полагаемся на (а) явные KDoc-комментарии на крипто-портах с надписью «не дёргать напрямую — иди через фасад», и (б) **новое правило в конституции** (Article XI §8 — «Reuse before invention»), которое заставляет AI-агента перед созданием нового порта проверять существующие. Если в будущем кто-то нарушит — введём настоящий Konsist gate отдельной задачей.

**Что в production пока нет.** Программа не у пользователей — до spec 030+. Это значит: wire-format можно менять без bump'a `schemaVersion`. После spec 030 эта свобода истекает.

**Когда:** 3–4 недели от старта (после `speckit-clarify` → `speckit-plan` → `speckit-tasks` → `speckit-analyze`).

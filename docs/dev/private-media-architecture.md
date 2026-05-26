# Private Media Architecture — guide for future authors

**Owner**: spec 012 (initial) · **Status**: living document
**Audience**: future spec authors, AI agents, разработчики добавляющие новые приватные медиа-фичи (audio messages, video, file attachments, etc.).

---

## Why this document exists

Spec 012 — первый видимый клиент крипто-фундамента спека 011. Чтобы будущие медиа-фичи (голосовые заметки, видео, прикрепления в сообщениях) добавлялись **как новые порты рядом**, а не модификацией существующих фасадов, **этот документ объясняет:**

1. **Какие три слоя есть**, и за что отвечает каждый.
2. **Как добавлять новый порт vs расширять существующий** — Article XI §8 «Reuse before invention».
3. **Куда складывать новый picker adapter / streaming uploader / recorder**.
4. **Что НЕ делать**: какие пути ведут к нарушению domain isolation, privacy invariants, или к "god-port"'ам.

Эти правила **обязательны** к чтению перед написанием нового spec'а, касающегося приватных медиа.

---

## Three layers — что лежит где

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 3: Attachment context (UX flows)                       │
│   - Contact photo flow (VCard share intent, ACTION_PICK)     │
│   - Document upload flow («+ документ» в редакторе)          │
│   - Future: audio message flow, video flow, etc.             │
│   Files: :features:private-media:ui/**                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ (calls facades)
┌─────────────────────────────────────────────────────────────┐
│ Layer 2: Media picker                                        │
│   - MediaPicker port (image|video|any, maxItems, mode)       │
│   - SystemPhotoPickerAdapter (API-level dispatch внутри)     │
│   - Future: AudioRecorder port для voice messages            │
│   - Future: CameraCapturePort для in-app camera              │
│   Files: :core:api/media/, :adapters:media-picker            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ (returns bytes)
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: Pipeline (media-type-agnostic, context-agnostic)    │
│   - PrivateMediaUploader (facade) — encrypt + upload + ledger│
│   - PrivateMediaResolver (facade) — download + decrypt + cache│
│   - LocalMediaStore (port) — persistent decrypted files      │
│   - Future: StreamingMediaUploader (для больших видео)        │
│   - Future: RangeDecrypt (для seekable video playback)        │
│   Files: :core:api/media/, :core:domain/media/                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ (uses crypto/storage primitives)
┌─────────────────────────────────────────────────────────────┐
│ Spec 011 ports (DO NOT touch directly from UI/domain!)        │
│   - AeadCipher, AsymmetricCrypto, DigitalSignature, etc.      │
│   - EncryptedMediaStorage, BlobReferenceLedger                │
│   Files: :core:api/crypto/, :adapters:crypto:*, :adapters:storage:* │
└─────────────────────────────────────────────────────────────┘
```

---

## Layer 1 — Pipeline. **Когда создавать новый порт vs расширять существующий**

### Существующие порты Layer 1 (spec 012)

| Port | Покрывает |
|---|---|
| `PrivateMediaUploader.upload(bytes, kind, linkId)` | **blob-shaped** media: один payload как один blob. Image, document, single audio clip. |
| `PrivateMediaResolver` (= `IconStorage` namespace `private:`) | Lookup + download + decrypt + local cache + render. |
| `LocalMediaStore` | Persistent local storage расшифрованных файлов. |

### Rule of thumb — добавить новый kind vs новый port

| Situation | Action |
|---|---|
| Новый payload type, **тот же blob-shape** (один файл — один blob, ≤ few MB) | **Расширить `PrivateMediaKind` enum** (additive). Например: добавить `Audio` для голосовой заметки до 30 секунд. |
| Новый payload type, **большой** (≥ 30 секунд video, нужно chunked upload) | **Создать новый порт `StreamingMediaUploader`** рядом с `PrivateMediaUploader`. Не модифицировать `PrivateMediaUploader.upload`. |
| Live capture (recording в момент use'а) | **Создать `MediaRecorder` port** в Layer 2 (или Layer 1 если рекордер сам шифрует stream). Не embed recording logic в `PrivateMediaUploader`. |
| Seekable playback video (не нужно download full file before play) | **Создать `RangeDecrypt` port** или extend `AeadCipher`. Это меняет crypto primitives — отдельный spec, прежде чем приходить в Layer 1. |

### Правильная последовательность при добавлении медиа-типа

1. **Прочитать этот документ.**
2. **Сверить с Article XI §8** — есть ли существующий порт, покрывающий нужду?
3. **Если да** — extend (например, добавить enum variant). Update tests + this document table.
4. **Если нет** — define новый port в `:core:api/media/`. **Не** модифицировать `PrivateMediaUploader` подписи (`suspend fun upload`).
5. **Plan-фаза**: показать в `plan.md` Article XI §8 compliance ("surveyed existing ports: A, B, C; none fit because ..." — required per Article XV §8).

### Anti-pattern — НЕ делать так

❌ **God-port`PrivateMediaUploader.upload(bytes, kind, linkId, streamingMode, chunkSize, recording, ...)`** — постепенно превратится в parameter soup. Бороться добавлением новых портов рядом, не parameters'ов.

❌ **Reaching past facade**: `:features:audio-messages:ui` напрямую импортирует `AeadCipher` и шифрует. Это нарушает Article XI §8 (есть фасад → use it) + spec 012 KDoc directive. Если фасад не покрывает нужду — расширь фасад или создай новый рядом.

❌ **Embedding sensitive data в `metadata`**: `envelope.metadata = {"kind": "document", "label": "Паспорт"}` — utечка. Label MUST идти inside ciphertext (см. [`metadata-kind-registry.md`](../../specs/012-contact-photos-and-private-documents/contracts/metadata-kind-registry.md) privacy invariant).

---

## Layer 2 — Media picker. **Где разместить новый picker / capture adapter**

### Existing

`MediaPicker` port + `SystemPhotoPickerAdapter` (Android) с тремя ветками API-level dispatch (33+, 29-32, 26-28). Возвращает unified `MediaPickResult(bytes, mimeType, sourceLabel?)`.

### Adding new picker / capture port

| Need | Action |
|---|---|
| Choose from gallery (image/video/any) | **Используй MediaPicker** — extend `Kind` enum или добавь параметр `mode`. |
| Live photo capture in-app (camera) | **Новый порт `CameraCapturePort`** в `:core:api/media/`. Adapter — `:adapters:camera`. Не reuse-ить MediaPicker — это другой UX (UI capture flow, не gallery selection). |
| Voice recording in-app | **Новый порт `AudioRecorder`** в `:core:api/media/`. Adapter — `:adapters:audio-recorder`. |
| File picker (PDF, документы) | Можно **extend MediaPicker** с `Kind.File` — это всё-таки gallery-style выбор. Но если SAF UX становится сильно другим — выделить в `FilePicker`. |

### Anti-Corruption Layer — rule

Adapter возвращает **unified bytes / domain types**, не URI / Intent / ContentResolver. Caller никогда не видит `android.net.Uri`. Если adapter не может вернуть bytes (например, video file 500 MB) — он **читает поток** и возвращает stream-handle через future `StreamingMediaResult`. URI остаётся **внутри adapter'а**.

---

## Layer 3 — UX flows. **Когда выделять новый feature module**

### Existing (spec 012)

`:features:private-media:ui`:
- DocumentViewer screen.
- Admin add-document flow.
- Admin upload progress.
- Admin failed-decrypt indicator.
- Плитка контакта с аватаром + плитка-документ.

### Adding new UX

| Need | Action |
|---|---|
| Новая плитка-тип (например, audio plate) | **Extend `:features:private-media:ui`** — добавить `AudioTileRenderer.kt`. Не создавать `:features:private-audio:ui` (один module per layer 3 unless ≥ 5 screens). |
| Voice-recording UI flow | **Создать `:features:voice-messages:ui`** — это отдельная feature, ≥ 3 screens (record / preview / send), своя navigation graph. |
| Cross-feature media gallery (показывает все фото + все документы сразу) | **Решить в spec**: либо в `:features:private-media:ui` (если он растёт), либо новый `:features:media-gallery:ui`. Plan-phase decision. |

---

## Privacy invariants — что MUST остаться

1. **`envelope.metadata` — только non-sensitive categorical hints** (`kind`). Никаких labels, имён, ИНН, дат.
2. **`LocalMediaStore` MUST оставаться excluded из cloud-backup** (`data_extraction_rules.xml`). При добавлении новых local-stored файлов — verify exclude works (test `LocalMediaStoreBackupExclusionTest`).
3. **`FLAG_SECURE` НЕ устанавливается** на launcher screens (баланс с remote screen-share use case, как у WhatsApp/Telegram). При добавлении новых sensitive screens (например, future PIN entry) — оценить отдельно; default — no FLAG_SECURE.
4. **`recipients[]` envelope'а** — определяется через `RecipientResolver`. При добавлении group sharing (future) — НЕ embed list получателей в metadata; envelope handles это в structured way.

---

## Reuse-before-invention checklist (Article XI §8)

Перед созданием нового порта/модуля в Layer 1/2/3 ответить в plan.md:

1. **Какой port существует, покрывающий мою нужду?** (См. tables выше + `:core:api/media/`.)
2. **Если такой есть — могу ли я extend additively?** (Например, добавить enum variant, optional parameter.)
3. **Если нет existing — почему extension не fит?** (Сurrent semantics distort.)
4. **Какой adapter module я добавляю?** (Один adapter per вендор SDK / system API.)
5. **Где KDoc directive «not for direct UI use»?** (Если новый port — crypto-adjacent.)

---

## Cross-references

- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — Article XI §8 (Reuse before invention), Article XV §8 (AI Agent operating rules).
- [`CLAUDE.md`](../../CLAUDE.md) — rule 1 (domain isolation), 2 (ACL), 4 (MVA), 5 (wire-format versioning).
- [`specs/011-contacts-and-e2e-encrypted-media/`](../../specs/011-contacts-and-e2e-encrypted-media/) — crypto foundation ports + envelope contract.
- [`specs/012-contact-photos-and-private-documents/`](../../specs/012-contact-photos-and-private-documents/) — three-layer architecture эталон + first client'ы.

---

## Living-document notes

Этот файл обновляется при каждом будущем spec'е, который добавляет порт / adapter / UI flow в media stack. Обязательно update'ить:
- Table в Layer 1 (если новый Layer 1 port).
- Table в Layer 2 (если новый picker / capture).
- Diagram (ASCII art) если структура слоёв меняется.

Spec'ы, которые ожидаются и придут сюда (предварительный список): audio voice messages, video messages, file attachments (PDF), camera in-app capture, group sharing of documents.

---

## TL;DR (для новичка)

**Что**: правила «куда положить новую медиа-фичу». Чтобы будущие фичи (голосовые, видео, прикрепления) добавлялись **как новые кирпичики**, а не превращались в один распухший фасад.

**Три уровня**:
1. **Pipeline** (шифрование / расшифровка / локальное хранилище) — низкий, не трогаем UI кодом.
2. **Picker** (откуда брать файл) — один порт на один тип источника (галерея, камера, диктофон отдельно).
3. **UX flow** (как пользователь видит и взаимодействует) — feature modules с экранами.

**Главное правило**: если уже есть подходящий порт — используй его. Если не подходит — **создай новый рядом**, не дописывай существующий новыми параметрами. И **никогда** не лезь напрямую в крипто-функции из UI — иди через фасад `PrivateMediaUploader/Resolver`.

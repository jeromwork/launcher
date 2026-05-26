# Research: Contact Photos and Private Documents

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Date**: 2026-05-26

Этот документ фиксирует решения по 5 точкам, где была реальная неопределённость (закрыта в clarify-сессии Q1-Q5), плюс мелкие plan-фазные decisions. Для каждой — alternatives considered, regret conditions, exit ramps.

---

## R1. Overwrite-семантика `Contact.photoRef` (resolves Clarification Q1)

**Decision**: implicit auto-update photoRef для существующего `Contact`'a, найденного по точному совпадению нормализованного singular `phoneNumber`. Никакого UI-диалога «обновить или добавить» в спеке 012.

### Alternatives considered

| Option | Pros | Cons | Rejected because |
|---|---|---|---|
| (a) Matching по `Contact.id` (UUIDv4) | Простейший; не пробивает identity-via-id | Каждое повторное share intent → новый UUID → storage leak от orphaned blobs | Дубли контактов у бабушки = UX-катастрофа |
| (b) Matching по `(displayName, phoneNumber)` auto-merge | Нет дублей, нет leak | Пробивает identity-via-id; не handle случая «admin переименовал Машу» | Хрупкое решение |
| **(c) Implicit auto-update по нормализованному `phoneNumber`** ← chosen | Решает оба edge case'а; не пробивает identity (id всё ещё authoritative); decrement explicit | Не handle случай «два номера одного человека» | Это явный future scope, TODO-ARCH-017/018 |
| (d) Phone-as-natural-key + UI dialog | Maximum контроль admin'а | Лишний шаг в UX; 20 диалогов при batch import — раздражение | Преждевременно для текущего scale (≤ 15 контактов) |

### Regret condition

«Если в дикой природе появится **adversarial admin**, который специально шлёт контакты с одинаковыми phone'ами для overwrite атак на чужие данные → переходим к (d) с явным UI confirmation».

### Exit ramp

(c) → (d): добавить detection + UI-dialog в `AddContactReviewScreen` (планируется в TODO-ARCH-018). Existing данные не требуют миграции — UI-дополнение. Cost: 2-3 дня.

---

## R2. Wire-format strategy для `Tile.DocumentTile` (resolves Q2)

**Decision**: additive new sealed variant `Tile.DocumentTile(documentRef, label)` без bump'a `/config schemaVersion`. Свобода действует **до spec 030+** (первый public release). После — bump policy CLAUDE.md rule 5 включается.

### Alternatives considered

| Option | Pros | Cons | Rejected because |
|---|---|---|---|
| (a) `schemaVersion` 1 → 2 + reader-migration | Чистый rule 5 | **Сейчас никто не читает /config со старой версии** — это lost work; накапливаемая стоимость bump'ов | Pre-production overhead |
| **(b) Additive sealed variant, no bump** ← chosen | Минимальная стоимость; используется существующий `unknown_slot_kind` fallback из спека 008 | Если бы программа была в production — silent partial-apply для бабушек на старой версии | Не production → mitigated |
| (c) `compatibilityVersion` отдельно от `schemaVersion` | Промежуточный путь | Лишний concept; никто не читает | Premature |

### Regret condition

«Spec 030 published, начинается first public rollout → следующий wire-format change MUST идти через bump policy» — записано в Assumptions spec.md.

### Exit ramp

(b) → bump policy: со spec 030 при первом breaking change — bump `1 → 2`, написать `Vn_To_Vn1` транзформер ([TODO-ARCH-015 в backlog](../../docs/dev/project-backlog.md) уже зафиксирован).

---

## R3. Защита фасадов — Konsist gate vs KDoc + Constitution (resolves Q3)

**Decision**: Konsist fitness gate **отложен**. Защита через KDoc-комментарии на портах + **новый Article XI §8** в конституции (`Reuse before invention`).

### Alternatives considered

| Option | Pros | Cons | Rejected because |
|---|---|---|---|
| (a) Narrow Konsist gate (только `:features:*:ui`) | Реальная CI-защита | Легко обходится через `:features:*:logic` | Слабая защита, false security |
| (b) Wide Konsist gate (везде кроме adapter/facade modules) | Сильная защита | Сейчас никаких нарушений нет — gate ничего не ловит; **первое тестовое нарушение** требует exempt list debt | Premature (rule 4) |
| **(c) KDoc + Constitution amendment** ← chosen | Generalises на все порты (не только crypto); 0 code overhead | Manual review, не automated | Достаточно для текущего scale; gate вводим когда впервые нарушат |

### Regret condition

«Если в спеке 015 / 016 / 017 появится первый прямой import крипто-порта вне фасадов (обнаружен code review или `speckit-analyze`) → вводим Konsist gate **точечно** под этот случай, плюс backfill scan по `:core:*` и `:features:*`».

### Exit ramp

(c) → automated gate: добавить Konsist test class `CryptoFacadeIsolationTest` в `:core:konsist-tests` (если такой module существует) или в `:app`. Время: 1 час на правило + 1 час на CI integration.

### Что зафиксировано в коде сейчас

- Article XI §8 in `.specify/memory/constitution.md`.
- KDoc-комментарии (будут добавлены в plan-phase tasks) на каждом из 6 крипто-портов + 2 фасадах.
- Раздел в `docs/dev/private-media-architecture.md` про правильное использование.

---

## R4. MediaPicker adapter design (resolves Q4)

**Decision**: один `MediaPicker` port; один `SystemPhotoPickerAdapter` (Android) с **тремя ветками внутри**, выбираемыми по `Build.VERSION.SDK_INT`. Возвращает **унифицированные bytes** (не URI). Никаких user-facing hint dialog'ов.

### Branches (minSdk=26, проверено `gradle/libs.versions.toml:6`)

| API range | Implementation | Permission required |
|---|---|---|
| **33+** (Android 13+) | `ActivityResultContracts.PickVisualMedia()` → `ACTION_PICK_IMAGES` нативный | NONE |
| **29-32** (Android 10-12) | `ActivityResultContracts.PickVisualMedia()` → androidx compat picker (использует Google Photos если установлен) | NONE |
| **26-28** (Android 8-9) | SAF `ActivityResultContracts.OpenDocument()` → URI → copy via `ContentResolver.openInputStream` в `Context.cacheDir/picker-temp/<random>` → read bytes → delete temp file | NONE |

### Alternatives considered

| Option | Pros | Cons | Rejected because |
|---|---|---|---|
| (a) Только ACTION_PICK_IMAGES, minSdk → 33 | Простой код | -50% userbase (Android 8-12 users) | Слишком жёстко |
| (b) Старый-стиль gallery library + READ_MEDIA_IMAGES | Единый UX | Нарушает SC-007 (новые permissions) | SC-007 — hard constraint |
| **(c) Adapter с internal dispatch** ← chosen | Unified bytes interface; нет user-facing разницы; 0 permissions | Сложнее adapter implementation (3 ветки) | Сложность изолирована в одном модуле — exactly rule 2 |
| (d) Hint dialog «у вас старый Android» | Транспарентность | Раздражение, infantilising | User said: «нет hint'ов» |

### Regret condition

«Если на Samsung One UI / Xiaomi MIUI API 29-32 без Google Photos androidx compat picker даст битый UX (отдельный bug-report от UAT) → добавляем SAF ветку также для 29-32 как fallback».

### Exit ramp

Branches изолированы в `SystemPhotoPickerAdapter.kt`; добавление новой ветки = добавление нового `when` arm. Cost: 1-2 часа.

### Mandatory manual UAT (per CHK019 permissions-platform)

- Samsung medium-tier (API 29-30): подтвердить compat picker работает.
- Xiaomi medium-tier (API 30-31, MIUI 12+): тот же.
- Любое API 26-28 устройство (теоретический Android 8/9, retro-test): подтвердить SAF ветка работает.

---

## R5. Local storage model — persistent decrypted vs in-memory cache (resolves Q5)

**Decision**: `LocalMediaStore` — **persistent app-private storage**. Decrypt **один раз** при первом download'е, дальше файл лежит расшифрованным как обычный app-private. Никакого FLAG_SECURE. Revoke = stop future, не wipe past.

### Alternatives considered

| Option | Pros | Cons | Rejected because |
|---|---|---|---|
| (a) In-memory LRU DecryptCache (my original draft) | RAM-only, нет on-disk PII | **2-сек lag при каждом cache miss** (включая rotation); 5 MB cap → eviction на низкой памяти → плитка моргает placeholder'ом | UX неприемлим |
| **(b) Persistent LocalMediaStore** ← chosen | Открытие плитки — мгновенно; recreation/rotation — мгновенно; согласуется с WhatsApp/Telegram baseline | PII на disk без encryption; backup risk без `data_extraction_rules.xml` | Mitigated через mandatory backup exclude |
| (c) LocalMediaStore + double-encryption with Android Keystore | PII защищена даже от root | Премийная сложность; добавляет decryption overhead на каждом show'е | Premature optimization для текущей threat model |
| (d) Hybrid: LRU in-memory + persistent после первого decrypt | Best of both | Сложность управления двумя layers | Не нужно — persistent один даёт ту же UX выгоду |

### Regret condition

«Если threat model изменится (adversarial admin, multi-recipient документы, или появится legal requirement для at-rest encryption на устройстве) → переход к (c) — добавить encryption layer в `LocalMediaStore` через Android Keystore. Подготовлено [TODO-ARCH-019](../../docs/dev/project-backlog.md)».

### Exit ramp

(b) → (c): refactor `FileLocalMediaStore.write` для encrypting + `read` для decrypting. Existing файлы — одноразовая миграция (декриптовать → encrypt с keystore key → перезаписать). Cost: 3-5 дней + миграционная пауза.

### Mandatory safeguards (определены security checklist'ом + accessibility)

- 🚨 **`data_extraction_rules.xml`** exclude `<exclude path="private-media/"/>` для `cloud-backup` и `device-transfer` (CHK024 security mandatory).
- 🚨 **TalkBack zoom buttons** в DocumentViewer ≥ 56dp (CHK accessibility mandatory).
- KDoc comment на `LocalMediaStore` port: «Decrypted PII at rest. Caller MUST ensure data_extraction_rules excludes this directory».

---

## R6. Module decomposition (plan-phase decision, не из clarify)

**Decision**: 
- **`:adapters:media-picker`** — новый module (Anti-Corruption Layer, оправдан Article V §3).
- **`LocalMediaStore` Android adapter** — кладём **в `:app`** (минимизация фрагментации; module создаём только если потребуется отдельная testability).
- **Фасады `PrivateMediaUploader/Resolver`** — **в `:core:domain`** как pure Kotlin (без отдельного `:facades:private-media` module — meta-minimization CHK016).

### Rationale

CLAUDE.md rule 4 + Article XI §1-8 (новый Reuse before invention): module создаётся только если пакета недостаточно. `:adapters:media-picker` justified (Anti-Corruption Layer + platform-specific code). `FileLocalMediaStore` — единственный класс, может жить в `:app` или вынести когда понадобится второй consumer.

### Regret condition

«Если появится второй consumer для `LocalMediaStore` (например, future `:features:audio-messages` использует тот же local cache) → выносим в отдельный `:adapters:local-media-store` module». Cost: extraction refactor, ≤ 1 день.

---

## R7. JPEG compression strategy (plan-phase, не из clarify)

**Decision**: admin-side JPEG resize/recompress перед `PrivateMediaUploader.upload(...)`. Реализация: **Android `BitmapFactory.decodeStream` + `Bitmap.compress(JPEG, 80)`**, target ≤ 500 KB. Идёт в `Dispatchers.Default` (CPU-bound).

### Algorithm

1. Decode bytes → `Bitmap` (с `inSampleSize` для downscale до max 2048×2048).
2. Compress JPEG quality=80 → byte stream.
3. If size > 500 KB → re-compress quality=60.
4. If still > 500 KB → re-decode с `inSampleSize=2` → recompress quality=80.
5. If still > 500 KB → возвращаем ошибку «фото слишком большое» (edge case в spec).

### Alternatives considered

- WebP — лучшее сжатие, но: (а) не все Android старых версий хорошо decode'ят WebP с alpha; (б) sensitive labels у нас уже внутри ciphertext (нет нужды в специфике формата); (в) lossy WebP сравним с JPEG quality 80.
- Lossless PNG — слишком большой для фотографий.
- HEIC — лучше всего, но API доступность only 28+; не покрывает minSdk=26.

JPEG quality 80 — industry default для photos.

---

## R8. `DocumentViewer` durability — что хранится через recreation

**Decision**: `rememberSaveable(documentRef: String)` — только reference. Bytes **никогда** не уходят в `Parcel` / `savedInstanceState`. При recreation (rotation, process death) — `documentRef` восстанавливается из saveable, `LocalMediaStore.read(uuid)` возвращает файл, viewer показывает мгновенно.

### Rationale (mentor-сессия Q5)

- bytes в parcel = PII в Android system parcel → потенциальная утечка через `dumpsys window`.
- bytes в memory во время Compose state — OK, GC сожрёт после destroy.
- `documentRef` (UUID) — не PII, безопасно сохранять.

---

## R9. `partialApplyReasons` TTL (open from failure-recovery checklist CHK011)

**Decision** (plan-phase commitment, требует backport в спек 008 если ещё не сделано): `/state/current.partialApplyReasons` очищается при **следующем successful apply'е** config'а. Если последний apply имеет partial reasons — они остаются в `/state` до следующего apply'а. Если успешный apply — list заменяется пустым (либо новым набором reasons, если опять partial).

### Verification

- Если спек 008 уже это делает (поверим в code review) → ✓.
- Если нет → создать follow-up task «spec 008 housekeeping: clear partialApplyReasons on next successful apply».

---

## R10. Что НЕ исследовалось (явно отложено)

- **Streaming uploader / recorder / range-decrypt** — out of scope per spec.md.
- **iOS** — out of project scope.
- **WebP/HEIC encoding** — JPEG достаточно для текущего scope; investigation отложено до spec на видео/audio.
- **Cross-device LocalMediaStore sync** (бабушка имеет планшет + телефон, шарят файлы) — out of scope; threat model 012 — singular Managed device per pair.
- **Server-side housekeeping для orphaned blobs** — server-roadmap `SRV-CRYPTO-001`, не в client.

---

## Summary

10 research areas. 5 закрывают clarify Q1-Q5 (with alternatives + regret conditions + exit ramps). 4 plan-phase decisions (module decomposition, JPEG strategy, viewer durability, partial TTL). 1 явный «не исследовали».

**Zero open architectural questions** for `speckit-tasks` decomposition.

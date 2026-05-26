# Tasks: Contact Photos and Private Documents (spec 012)

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Data model**: [data-model.md](data-model.md)
**Generated**: 2026-05-26 via `speckit-tasks`
**Estimated scope**: ~52 tasks · 3-4 недели implementation
**Branch**: `012-contact-photos-and-private-documents`

## Legend

- `[P]` — параллелизуемая task (разные файлы, нет общих зависимостей).
- `[US-N]` — task привязана к user story N (US-1 = фото контактов, US-2 = документы, US-3 = honest reporting, US-4 = cleanup). `[ALL]` — нужна всем US.
- `requires: TNNN` — task имеет dependency на завершение предыдущей.
- 🚨 — mandatory security / accessibility / privacy task (blocker для merge'a).

---

## Phase 1: Foundation + Security Gate (T1201-T1208)

**Purpose**: Module setup, dependencies, и 🚨 mandatory backup exclusion + KDoc directives.
**⚠️ Phase 1 blocks all later phases.**

- [ ] **T1201** 🚨 `[ALL]` Создать `app/src/main/res/xml/data_extraction_rules.xml` с exclude `private-media/` для `cloud-backup` и `device-transfer`. (Trace: FR-021, R1, contracts/local-media-store-layout.md §Mandatory backup exclusion)
  - **Acceptance**: файл создан с двумя `<exclude>` entries; `AndroidManifest.xml` ссылается через `android:dataExtractionRules="@xml/data_extraction_rules"`.

- [ ] **T1202** 🚨 `[ALL]` `requires: T1201` Написать `LocalMediaStoreBackupExclusionTest` (instrumented test) verifying что XML присутствует и содержит правильные exclude paths. (Trace: R1, contracts/local-media-store-layout.md §Verification test)
  - **Acceptance**: test class в `app/src/androidTest/`, метод `private_media_dir_excluded_from_backup` зелёный.

- [ ] **T1203** `[P] [ALL]` Создать новый gradle module `:adapters:media-picker` per [quickstart.md §Module setup](quickstart.md). Включает `build.gradle.kts` skeleton + skeleton package structure + entry в `settings.gradle.kts`. (Trace: Plan §Project Structure, research.md R6)
  - **Acceptance**: `:adapters:media-picker` появляется в `./gradlew projects`; module compileable (empty placeholder class).

- [ ] **T1204** `[P] [ALL]` Добавить `androidx.documentfile:documentfile:1.0.1` dependency в `gradle/libs.versions.toml` + reference в `:adapters:media-picker/build.gradle.kts`. (Trace: Plan §Dependency Impact)
  - **Acceptance**: `./gradlew :adapters:media-picker:dependencies` показывает documentfile.

- [ ] **T1205** `[P] [ALL]` Добавить KDoc directives на 6 крипто-портов спека 011 (`AeadCipher`, `AsymmetricCrypto`, `EncryptedMediaStorage`, `DigitalSignature`, `HashFunction`, `SecureKeystore`) — текст «DO NOT use directly from UI / business logic. Use PrivateMediaUploader/Resolver facades» + ссылка на `docs/dev/private-media-architecture.md`. (Trace: FR-005, SC-009, data-model.md §9, Article XI §8)
  - **Acceptance**: каждый из 6 файлов содержит block с «DO NOT use directly» + reference link; `./gradlew :core:api:assemble` зелёный.

- [ ] **T1206** `[P] [ALL]` Создать `:features:private-media:ui` module skeleton (или подтвердить план extend `:features:settings:ui` per plan-phase final decision). (Trace: Plan §Project Structure)
  - **Acceptance**: модуль/package структура готова, dummy Composable compileable.

- [ ] **T1207** `[P] [ALL]` Добавить strings.xml keys placeholders (только keys, без переводов) в `compose-resources/values/strings_private_media.xml` для всех user-facing строк, упомянутых в FR'ах (DocumentViewer close, admin indicator, picker entry, error messages, retry button). (Trace: SC-011, ADR-004, checklists/localization.md)
  - **Acceptance**: ≥ 12 keys присутствуют с `<!--description="..."-->` translator notes; ru и en placeholders заполнены.

- [ ] **T1208** `[ALL]` `requires: T1201..T1207` **Phase 1 checkpoint**: запустить full build, verify все foundation pieces compile + T1202 test green. (Trace: gate)
  - **Acceptance**: `./gradlew assemble :app:testDebugUnitTest :app:connectedDebugAndroidTest` зелёный.

---

## Phase 2: Domain types + ports (T1209-T1216) — `[ALL]`

**Purpose**: pure-Kotlin types и port interfaces в `commonMain`.

- [ ] **T1209** `[P]` Создать `core/api/media/PrivateMediaKind.kt` enum (Image, Document) + `wireValue` + `fromWire` companion. (Trace: FR-006, data-model.md §1)
  - **Acceptance**: enum compileable; `PrivateMediaKind.fromWire("image") == Image`; unit test in commonTest.

- [ ] **T1210** `[P]` Создать `core/api/media/MediaPickResult.kt` (bytes, mimeType, sourceLabel?) + `MediaPickerError` sealed (Cancelled, InvalidMimeType, IOError, FileTooLarge). (Trace: FR-007, FR-009, data-model.md §1)
  - **Acceptance**: data class + sealed interface compileable; serialization не нужна (in-memory only).

- [ ] **T1211** `[P]` Создать `core/api/media/MediaPicker.kt` port interface с `Kind` + `Mode` enums + `SIZE_CAP_BYTES` companion. (Trace: FR-007, data-model.md §2)
  - **Acceptance**: interface compileable; `suspend fun pick(...)` signature returns `Outcome<List<MediaPickResult>, MediaPickerError>`.

- [ ] **T1212** `[P]` Создать `core/api/media/LocalMediaStore.kt` port interface + `LocalMediaFile` expect class (sizeBytes, lastAccessedAt). (Trace: FR-003, data-model.md §2)
  - **Acceptance**: interface + expect class compileable; KDoc содержит «MUST be excluded from backup» warning.

- [ ] **T1213** `[P]` Создать `core/api/media/PrivateMediaUploader.kt` port (facade) interface с `suspend fun upload(bytes, kind, linkId)`. (Trace: FR-001, FR-006, data-model.md §2)
  - **Acceptance**: interface compileable; KDoc directive «entry point — DO NOT call AeadCipher directly».

- [ ] **T1214** `[P]` Создать `core/api/media/PrivateMediaResolver.kt` port (facade) interface extending `IconStorage`. (Trace: FR-002, data-model.md §2)
  - **Acceptance**: interface compileable; KDoc упоминает IconStorage namespace `private:` + reference к спеку 006.

- [ ] **T1215** `[P]` Создать `FakeMediaPicker` в `:core:api:test-fakes/commonTest/` с queue-based result enqueueing. (Trace: CLAUDE.md §6, data-model.md §8)
  - **Acceptance**: fake compileable; unit test verifies `enqueueResult` → `pick()` returns enqueued result; `enqueueFailure` works.

- [ ] **T1216** `[P]` Создать `FakeLocalMediaStore` (in-memory ConcurrentHashMap<uuid, ByteArray>) в `:core:api:test-fakes/commonTest/`. (Trace: CLAUDE.md §6, data-model.md §8)
  - **Acceptance**: fake compileable; unit test verifies read/write/delete/exists/totalSizeBytes correctness; persists через recreation внутри теста.

---

## Phase 3: Facade implementations + Tile extension (T1217-T1224) — `[ALL]`

**Purpose**: pure-Kotlin facade impls + wire-format extension.

- [ ] **T1217** `requires: T1213` Реализовать `PrivateMediaUploaderImpl` в `:core:domain/media/` (pure Kotlin). Использует `AeadCipher`, `AsymmetricCrypto`, `RecipientResolver`, `EncryptedMediaStorage`, `BlobReferenceLedger` через DI. (Trace: FR-001, FR-006, plan.md Data flow §Upload)
  - **Acceptance**: encrypt → upload → ledger.increment → return `IconRef("private:<uuid>")` flow реализован; unit test с fakes покрывает happy path + storage failure + crypto failure.

- [ ] **T1218** `requires: T1214, T1212` Реализовать `PrivateMediaResolverImpl` в `:core:domain/media/`. Lookup LocalMediaStore → если miss → download + decrypt + write + return Bitmap. ВСЕ ошибки → Placeholder + emit `PartialReason.MediaDecryptFailed`. (Trace: FR-002, FR-021, plan.md Data flow §Show)
  - **Acceptance**: unit test покрывает: cache hit, cache miss (download + decrypt), BlobMissing, MacFailed, KeyNotFound, RecipientNotFound — все non-success cases return Placeholder; каждый эмитит правильный sub-category log event.

- [ ] **T1219** `requires: T1217, T1218` Privacy invariant — внутри `PrivateMediaUploaderImpl` при `kind = Document` шифровать `DocumentPayload(bytes, label)` (label передаётся отдельно через параметр upload). Label НЕ должен попадать в `envelope.metadata`. (Trace: FR-006, SC-008, contracts/metadata-kind-registry.md)
  - **Acceptance**: создан `EnvelopeMetadataPrivacyTest.no_label_leak_in_plaintext_metadata` — gate test зелёный; static analysis (manual review or test) verifies label не появляется в plaintext envelope bytes.

- [ ] **T1220** `requires: T1218` Wire `PrivateMediaResolverImpl` как реализация `IconStorage` для namespace `"private:"`. Регистрация в DI: `IconStorage.resolve("private:...")` диспатчится на `PrivateMediaResolver`. (Trace: FR-002, spec 006 icon-id-namespace.md:48 обязательство)
  - **Acceptance**: integration test — `IconStorage.resolve("private:<existing_uuid>")` возвращает Bitmap; `IconStorage.resolve("private:<unknown_uuid>")` возвращает Placeholder.

- [ ] **T1221** `[P]` Extend `core/api/config/Tile.kt` — добавить sealed variant `Tile.DocumentTile(id, documentRef, label)`. (Trace: FR-017, data-model.md §3, contracts/tile-document-kind.md)
  - **Acceptance**: sealed hierarchy расширена; `kotlinx.serialization` discriminator `kind="document"` правильно serialize/deserialize.

- [ ] **T1222** `requires: T1221` Написать `TileWireFormatTest` (commonTest) — roundtrip + forward-compat + mixed-kinds + validation tests per [contracts/tile-document-kind.md §Tests](contracts/tile-document-kind.md). (Trace: CHK010 wire-format, contract requirement)
  - **Acceptance**: все 7 test methods зелёные; fixtures `tile-v1-document.json`, `tile-v1-mixed-kinds.json`, `tile-v1-unknown-kind.json` присутствуют в `commonTest/resources/wire-format/`.

- [ ] **T1223** `requires: T1217` Написать `EnvelopeMetadataKindTest` (commonTest) — roundtrip image + document + forward-compat unknown kind + missing kind (legacy). (Trace: CHK010 wire-format, contracts/metadata-kind-registry.md §Tests)
  - **Acceptance**: 4+ test methods зелёные.

- [ ] **T1224** `requires: T1221, T1222` Validate label sanitisation для `DocumentTile.label` — trim, strip control chars, ≤ 40 graphemes. Если > 40 → truncate + log warning (graceful). (Trace: FR-016, contracts/tile-document-kind.md §Validation rules)
  - **Acceptance**: unit test покрывает empty (reject), 1 char (accept), 40 chars (accept), 41+ chars (truncate to 40 + warning), control chars (strip).

---

## Phase 4: Android adapters — MediaPicker + LocalMediaStore (T1225-T1233)

**Purpose**: real Android implementations с API-level dispatch.

- [ ] **T1225** `[US-2]` `requires: T1211` В `:adapters:media-picker/androidMain/` создать `SystemPhotoPickerAdapter` entry class с `Build.VERSION.SDK_INT` dispatch на 3 ветки. (Trace: FR-008, research.md R4)
  - **Acceptance**: класс compileable; switch на API level выбирает правильную ветку (verify через mock-level test).

- [ ] **T1226** `requires: T1225` Реализовать `PickerBranch33Plus.kt` — `ActivityResultContracts.PickVisualMedia()` с `ACTION_PICK_IMAGES`. Без permission. (Trace: FR-008, research.md R4)
  - **Acceptance**: на тестовом устройстве API 33+ adapter возвращает bytes; MIME validation работает (image-only rejects video).

- [ ] **T1227** `[P]` `requires: T1225` Реализовать `PickerBranch29To32.kt` — androidx PhotoPicker compat. (Trace: FR-008, research.md R4)
  - **Acceptance**: на тестовом устройстве API 30 (или эмулятор) adapter возвращает bytes; если Google Photos нет — graceful SAF fallback внутри androidx compat.

- [ ] **T1228** `[P]` `requires: T1225` Реализовать `PickerBranch26To28.kt` — SAF `ActivityResultContracts.OpenDocument()` → URI → copy via `ContentResolver.openInputStream` в `Context.cacheDir/picker-temp/<random>` → read bytes → **delete temp file**. (Trace: FR-008, research.md R4)
  - **Acceptance**: на тестовом устройстве API 28 (или эмулятор) adapter возвращает bytes; temp file удалён после read; MIME validation работает.

- [ ] **T1229** `requires: T1226, T1227, T1228` Интеграционный тест `SystemPhotoPickerAdapterIntegrationTest` — verify все три ветки возвращают unified `MediaPickResult` shape. (Trace: CHK001 domain-isolation)
  - **Acceptance**: test class с 3 conditional methods (по `Build.VERSION.SDK_INT`); все возвращают `MediaPickResult(bytes != null, mimeType != null)`.

- [ ] **T1230** `[ALL]` `requires: T1212` В `:app/androidMain/` создать `FileLocalMediaStore` — реализация `LocalMediaStore` поверх `Context.filesDir/private-media/<uuid>`. (Trace: FR-003, contracts/local-media-store-layout.md)
  - **Acceptance**: read/write/delete/exists/totalSizeBytes работают через actual filesystem; KDoc цитирует backup exclusion warning.

- [ ] **T1231** `requires: T1230` `LocalMediaFile.actual` для Android — wrapper над `java.io.File`. (Trace: data-model.md §2)
  - **Acceptance**: `sizeBytes` returns `File.length()`; `lastAccessedAt` returns `File.lastModified()` (Android не tracks atime).

- [ ] **T1232** `requires: T1230, T1231` `LocalMediaStoreIntegrationTest` per [contracts/local-media-store-layout.md §Tests](contracts/local-media-store-layout.md). Включает: write/read, persists through recreation, idempotent delete, totalSizeBytes. (Trace: CHK014 failure-recovery)
  - **Acceptance**: 6 test methods зелёные на эмуляторе.

- [ ] **T1233** `requires: T1230` DI wiring — `:app` DI module binds `LocalMediaStore` к `FileLocalMediaStore` в release variant, к `FakeLocalMediaStore` в `mockBackend` variant. То же для `MediaPicker` → `SystemPhotoPickerAdapter` vs `FakeMediaPicker`. (Trace: CHK012 domain-isolation)
  - **Acceptance**: `./gradlew :app:assembleMockBackendDebug` использует Fake'и; `:app:assembleDebug` — real adapter'ы. Verified через DI graph dump.

---

## Phase 5: Admin UI flows (T1234-T1241) — `[US-1, US-2, US-3]`

**Purpose**: admin-side screens — «+ документ», upload progress, indicator, VCard intent extension.

- [ ] **T1234** `[US-1]` `requires: T1217` Extend существующий VCard share intent receiver спека 009 — извлекать `PHOTO` field из payload, base64-decode, передавать в `PrivateMediaUploader(kind = Image)`. Сохранять результат в `Contact.photoRef`. (Trace: FR-011, FR-013)
  - **Acceptance**: integration test — given VCard payload with PHOTO base64 → upload completes → Contact.photoRef = "private:..." → blob present in fake EncryptedMediaStorage.

- [ ] **T1235** `[US-1]` `requires: T1234` Extend `ACTION_PICK` контактов flow спека 009 — читать `ContactsContract.Contacts.Photo.PHOTO` (через `ContentResolver.openAssetFileDescriptor`). Если есть — upload, если нет — `photoRef = null`. (Trace: FR-012)
  - **Acceptance**: тест с mock'ом ContactsContract — `photo != null` triggers upload; `photo == null` оставляет `photoRef = null` без ошибки.

- [ ] **T1236** `[US-1]` `requires: T1234` Implement **implicit auto-update** photoRef per FR-013 — при upload contact с phoneNumber, который уже есть в `/config.contacts[]` → найти existing Contact, decrement его старый `photoRef` blob через BlobReferenceLedger, обновить photoRef новым значением. (Trace: FR-013, research.md R1)
  - **Acceptance**: integration test US-1 sc.3 — добавить contact с phone+photo, добавить **тот же phone** с новым photo → existing Contact.id сохранён, refCount старого blob decremented, photoRef = новый "private:...".

- [ ] **T1237** `[US-2]` `requires: T1211, T1217` Создать `AdminAddDocumentScreen` Compose — кнопка «+ документ» в редакторе раскладки, при тапе запускает `MediaPicker.pick(Kind.Image, maxItems=1)`. (Trace: FR-015)
  - **Acceptance**: Compose UI test — кнопка видна с tap-target ≥ 56dp; tap → picker enqueued; cancel → screen остаётся; success → переход на label entry.

- [ ] **T1238** `[US-2]` `requires: T1237` Label entry form в `AdminAddDocumentScreen` — input field с validation (1..40 graphemes, sanitised) + thumbnail preview выбранного фото + кнопка «Подтвердить» (≥ 56dp). (Trace: FR-016)
  - **Acceptance**: Compose UI test — пустой label → submit disabled; 1 char → enabled; 41+ chars → truncate; submit → upload triggered с label.

- [ ] **T1239** `[US-2]` `requires: T1238` Wire confirmation → `PrivateMediaUploader.upload(bytes, Document, linkId)` + push `Tile.DocumentTile(documentRef, label)` в `/config`. (Trace: FR-016, FR-017)
  - **Acceptance**: integration test — confirm → blob появляется в Storage, /config обновляется с новой DocumentTile, FCM push отправлен (через existing 008 mechanism).

- [ ] **T1240** `[US-1, US-2]` `requires: T1217` `AdminUploadProgressScreen` Compose — loader spinner + retry button при `CryptoError.StorageFailure(IOException)`. Никаких countdown'ов. (Trace: FR-020, edge case 3, R6)
  - **Acceptance**: Compose UI test — loader виден во время upload; on network error → retry button visible; повторный tap retry → upload re-attempted.

- [ ] **T1241** `[US-3]` `requires: T1218` `AdminDecryptIndicatorScreen` Compose (или embed в admin layout) — показывает «фото не отрисовалось у {Managed display name}» с hint action: «пере-добавить» для `mac_failed|blob_missing`, «повторить pairing» для `key_not_found|recipient_not_found`. (Trace: FR-022)
  - **Acceptance**: Compose UI test — given `/state.partialApplyReasons` содержит `MediaDecryptFailed` → indicator visible с правильным hint; pre-`Managed display name` localised.

---

## Phase 6: Managed UI (бабушкина сторона) (T1242-T1248) — `[US-1, US-2]`

**Purpose**: плитки + DocumentViewer + accessibility.

- [ ] **T1242** `[US-1]` `requires: T1220` Обновить `ContactTileRenderer` Compose — показывать аватар через `IconStorage.resolve(photoRef)`. Placeholder = крупный инициал + контраст ≥ 4.5:1. Tap-target ≥ 56dp. (Trace: FR-014)
  - **Acceptance**: Compose UI test — с `photoRef != null` показывает image; с `photoRef == null` показывает initial placeholder; tap-target measured ≥ 56dp.

- [ ] **T1243** `[US-2]` `requires: T1220, T1221` Создать `DocumentTileRenderer` Compose — миниатюра документа (через IconStorage) + label сверху. Tap-target ≥ 56dp. (Trace: FR-014, FR-017, FR-018)
  - **Acceptance**: Compose UI test — с `documentRef` показывает thumbnail; tap → navigates to DocumentViewer; label visible; tap-target ≥ 56dp.

- [ ] **T1244** `[US-2]` `requires: T1243` Создать `DocumentViewerScreen` Compose fullscreen — label сверху (≥ 18sp), фото на максимум экрана, кнопка «Закрыть» ≥ 56dp снизу-справа. State: `documentRef` через `rememberSaveable`, bytes NEVER в `Parcel`. (Trace: FR-018, research.md R8)
  - **Acceptance**: Compose UI test — `documentRef` survive rotation (recreation возвращает viewer с тем же документом); tap «Закрыть» → navigates back; pinch жест enabled.

- [ ] **T1245** 🚨 `[US-2]` `requires: T1244` **MANDATORY accessibility**: добавить постоянно видимые кнопки `+` / `−` (≥ 56dp каждая) для zoom in/out в `DocumentViewerScreen`. Pinch-to-zoom остаётся для full-vision users; кнопки — для TalkBack users. (Trace: FR-019b, R2, accessibility checklist adjacent concern)
  - **Acceptance**: UI test `DocumentViewerScreenTest.zoom_buttons_visible_when_TalkBack_on` зелёный; кнопки visible независимо от TalkBack state; tap-target measured ≥ 56dp; double-tap zoom также работает.

- [ ] **T1246** `[US-2]` `requires: T1244` Pinch-to-zoom (1.0×..4.0×) + pan в `DocumentViewerScreen`. На старом OEM где жест не работает — fit-to-screen остаётся работоспособным. (Trace: FR-019)
  - **Acceptance**: Compose UI test — pinch event scales image; bounds limited 1.0×..4.0×; pan работает после zoom > 1.0×.

- [ ] **T1247** `[US-1, US-2]` `requires: T1242, T1243` TalkBack content descriptions — для каждой плитки: contentDescription = displayName (contact) ИЛИ label (document). Для DocumentViewer: label читается как heading. (Trace: CHK007-012 accessibility, SC-011)
  - **Acceptance**: Accessibility Scanner manual test проходит; TalkBack walkthrough — все interactive elements announced.

- [ ] **T1248** `[US-1, US-2]` `requires: T1247` Localise все user-facing строки — заполнить ru + en в `strings_private_media.xml`. Translator notes для ambiguous: «Пере-добавить», «Повторить pairing», «Закрыть». (Trace: SC-011, ADR-004, T1207)
  - **Acceptance**: все string keys (≥ 12) имеют ru + en переводы; `./gradlew :app:assembleDebug` без `MissingTranslation` warnings.

---

## Phase 7: Cleanup + Honest Reporting + US-4 (T1249-T1252)

**Purpose**: refCount tracking + partialApplyReasons emit + delete cleanup.

- [ ] **T1249** `[US-4]` `requires: T1217, T1218, T1239` Implement BlobReferenceLedger refCount tracking в флоу: создание `Contact.photoRef` / `Tile.DocumentTile.documentRef` → increment refSource; удаление contact / tile → decrement refSource. Existing 011 housekeeping reconciler удалит blob при refCount = 0 в ≤ 5 минут. (Trace: FR-013, FR-023, US-4)
  - **Acceptance**: integration test US-4 sc.1 — add contact with photo → delete contact → wait ≤ 5 min → `EncryptedMediaStorage.exists(linkId, uuid) == false`.

- [ ] **T1250** `[US-3]` `requires: T1218` Emit `PartialReason.MediaDecryptFailed` в `/state/current.partialApplyReasons` при любой `CryptoError` в resolver. **Впервые в проекте** — это закрывает обязательство спека 008. (Trace: FR-021, SC-010)
  - **Acceptance**: integration test US-3 sc.1 — повредить envelope → render плитки → проверить `/state.partialApplyReasons` через Firestore Emulator → содержит `media_decrypt_failed`; sub-category в log event соответствует.

- [ ] **T1251** `[US-3]` `requires: T1250` Verify `partialApplyReasons` cleared на следующем successful apply (per research.md R9). Если spec 008 это не реализует — открыть follow-up task. (Trace: failure-recovery checklist CHK011)
  - **Acceptance**: integration test — first apply emits reason → second apply (clean) — reason cleared. Если existing 008 code этого не делает, file follow-up issue.

- [ ] **T1252** `[US-1, US-2]` `requires: T1234, T1239` Admin-side JPEG compression перед upload'ом — `BitmapFactory.decodeStream` + `inSampleSize` for downscale до 2048×2048 + `Bitmap.compress(JPEG, 80)`. Если > 500 KB после двух итераций — error «фото слишком большое» (edge case 1). Идёт в `Dispatchers.Default`. (Trace: edge case 1, research.md R7)
  - **Acceptance**: unit test — 4 MB raw → ≤ 500 KB output; «дико большое» raw → error after 2 iterations; verified что compression идёт не в main thread.

---

## Phase 8: Integration + privacy gates + smoke (T1253-T1255)

**Purpose**: end-to-end tests + critical gate tests + manual UAT.

- [ ] **T1253** `[ALL]` `requires: T1234, T1242, T1247, T1248` End-to-end test — full US-1 flow на emulator: VCard share intent с photo → upload → /config push → FCM → Managed receives → плитка показывает фото ≤ 30s. (Trace: SC-001)
  - **Acceptance**: instrumented test пробегает на CI; stopwatch verify ≤ 30s p95 на 5 проходах.

- [ ] **T1254** `[ALL]` `requires: T1239, T1244, T1245` End-to-end test — full US-2 flow: admin «+ документ» → picker → label → upload → /config push → бабушка тапает плитку → DocumentViewer fullscreen ≤ 30s. (Trace: SC-002)
  - **Acceptance**: instrumented test зелёный; stopwatch verify ≤ 30s.

- [ ] **T1255** `[ALL]` `requires: T1219` 🚨 Privacy gate test `EnvelopeMetadataPrivacyTest.no_label_leak_in_plaintext_metadata` — шифровать document с label «TestLabelLeak_123», parse plaintext envelope metadata, assert метаdata.values НЕ содержат строку. (Trace: SC-008, FR-006, contracts/metadata-kind-registry.md)
  - **Acceptance**: test зелёный; **MUST be in CI gate** (PR blocked if red).

---

## Phase 9: Documentation + verification (T1256-T1259)

**Purpose**: docs updates + measurement + manual UAT.

- [ ] **T1256** `[ALL]` Verify `docs/dev/private-media-architecture.md` соответствует фактической реализации после Phase 1-8. Update tables в Layer 1/2/3 если что-то поменялось. (Trace: FR-024)
  - **Acceptance**: doc reviewed against actual code; tables match реальные ports/adapters/UI screens; cross-references valid.

- [ ] **T1257** `[ALL]` Update `docs/compliance/permissions-and-resource-budget.md` — add section «Storage usage» с записью о `private-media/` folder (typical ≈ 30 MB на пару, no cap, TODO-ARCH-019 reference). (Trace: CHK022 permissions-platform)
  - **Acceptance**: doc updated; entry с typical size + cap policy + reference to TODO-ARCH-019.

- [ ] **T1258** `[ALL]` APK delta measurement — release build before/after spec 012 merge, document в `perf-checkpoint.md`. Target ≤ 500 KB (SC-006). (Trace: SC-006, CHK015 performance)
  - **Acceptance**: `perf-checkpoint.md` файл в specs/012/ содержит APK size measurement (baseline = main pre-merge, after = current branch); diff ≤ 500 KB confirmed.

- [ ] **T1259** `[ALL]` Manual UAT walkthrough на Samsung medium-tier + Xiaomi medium-tier devices: US-1 happy path, US-2 happy path, US-3 (повреждённый envelope), US-4 (delete cleanup). + senior-walkthrough симуляция (squint + slow tap + voice-over). (Trace: CHK019 permissions-platform OEM quirks, CHK022 elderly-friendly manual walkthrough)
  - **Acceptance**: walkthrough log в `specs/012/uat-log.md` — все 4 US passes на обоих устройствах; senior walkthrough notes — нет accessibility regressions.

---

## Cross-artifact trace verification

См. отдельный run `procedure-cross-artifact-trace` после `speckit-tasks`:

| FR | Covered by |
|---|---|
| FR-001 (uploader) | T1213, T1217 |
| FR-002 (resolver) | T1214, T1218, T1220 |
| FR-003 (LocalMediaStore) | T1212, T1230, T1231, T1232 |
| FR-004 (perf budgets) | T1232, T1253, T1254, T1258 |
| FR-005 (facade defense) | T1205, T1213, T1214 |
| FR-006 (privacy invariant) | T1219, T1255 |
| FR-007 (MediaPicker port) | T1211, T1225 |
| FR-008 (API-level dispatch) | T1225-T1229 |
| FR-009 (MIME validation) | T1226-T1228 (within branches) |
| FR-010 (no EXIF beyond orientation) | implicit in T1226-T1228, verified manual UAT |
| FR-011 (VCard PHOTO field) | T1234 |
| FR-012 (ACTION_PICK photo) | T1235 |
| FR-013 (implicit auto-update) | T1236 |
| FR-014 (плитка контакта UI) | T1242 |
| FR-015 («+ документ» button) | T1237 |
| FR-016 (label form + sanitise) | T1238, T1224 |
| FR-017 (Tile.DocumentTile additive) | T1221, T1222 |
| FR-018 (DocumentViewer) | T1244 |
| FR-019 (pinch-to-zoom) | T1246 |
| FR-019b (TalkBack zoom buttons mandatory) | T1245 |
| FR-025 (data_extraction_rules.xml) | T1201, T1202 |
| FR-020 (progress indicator on first download) | T1240, T1244 |
| FR-021 (CryptoError → MediaDecryptFailed) | T1218, T1250 |
| FR-022 (admin indicator with hints) | T1241 |
| FR-023 (refCount on delete) | T1249 |
| FR-024 (private-media-architecture.md) | T1256 (created during plan-phase, verified here) |
| SC-001 (≤30s contact e2e) | T1253 |
| SC-002 (≤30s document e2e) | T1254 |
| SC-003 (≤100ms hit, ≤3s first) | T1232, T1253, T1254 |
| SC-004 (placeholder + indicator ≤60s) | T1241, T1250 |
| SC-005 (cleanup ≤5min) | T1249 |
| SC-006 (APK ≤500 KB) | T1258 |
| SC-007 (no new permissions) | implicit in T1225-T1228, verified `:app:assembleDebug` lint |
| SC-008 (no label leak) | T1219, T1255 |
| SC-009 (KDoc presence on crypto ports) | T1205 |
| SC-010 (partialApplyReasons emit) | T1250 |
| SC-011 (ru+en strings.xml) | T1207, T1248 |

**All 25 FR (24 original + FR-019b + FR-025) + 11 SC covered.** ✓

Post-trace amendment 2026-05-26: spec.md обновлён с FR-019b (TalkBack zoom buttons mandatory) и FR-025 (data_extraction_rules.xml backup exclusion) — закрывают Article XII §1 «smuggled architecture» risks из `procedure-cross-artifact-trace`.

---

## Phases summary

| Phase | Tasks | US | Blocker for next? |
|---|---|---|---|
| 1. Foundation + Security Gate | T1201-T1208 | ALL | YES |
| 2. Domain types + ports | T1209-T1216 | ALL | YES (impls depend) |
| 3. Facade impls + Tile extension | T1217-T1224 | ALL | YES |
| 4. Android adapters | T1225-T1233 | ALL | partial — UI can stub |
| 5. Admin UI flows | T1234-T1241 | US-1, US-2, US-3 | YES (e2e needs admin) |
| 6. Managed UI (бабушка) | T1242-T1248 | US-1, US-2 | YES (e2e needs) |
| 7. Cleanup + reporting + US-4 | T1249-T1252 | US-3, US-4, ALL | YES (US-4 done here) |
| 8. Integration + privacy gates | T1253-T1255 | ALL | YES |
| 9. Documentation + verification | T1256-T1259 | ALL | last |

**Total: 59 tasks** (T1201-T1259, спец 012 prefix).

---

## Parallel execution opportunities

После T1208 (Phase 1 checkpoint):
- Phase 2: все T1209-T1216 параллельны (разные файлы).
- Phase 3: T1221, T1222, T1223 параллельны (wire format extension не зависит от facade impl).
- Phase 4: T1226, T1227, T1228 параллельны (три отдельные branch файла).
- Phase 5: T1234 ↔ T1237-T1240 параллельны (US-1 и US-2 flows независимы).
- Phase 6: T1242, T1243 параллельны.

---

## Definition of Done — gates перед merge

Из [plan.md §Rollout / Verification](plan.md#rollout--verification):

1. ✅ All 14 checklists 0 violations (verified в `_overview.md`).
2. ✅ Constitution Check 8/8 PASS (verified в plan.md).
3. ✅ T1255 privacy gate test зелёный.
4. ✅ T1202 backup exclusion test зелёный.
5. ✅ T1245 accessibility zoom buttons test зелёный.
6. ✅ T1258 APK delta ≤ 500 KB.
7. ✅ T1259 manual UAT log clean на 2 устройствах.
8. ✅ T1248 i18n complete (ru + en).

---

## TL;DR (для новичка)

**Что**: декомпозиция работы спека 012 в **59 конкретных задач** (T1201-T1259), сгруппированных в **9 фаз**.

**Порядок**: сначала **security gate** (T1201-T1202: чтобы паспорта не утекли в Google Drive) + foundation. Потом domain типы → adapter'ы → admin UI → бабушкин UI → cleanup → docs.

**Главные критические задачи**:
- **T1201-T1202** — `data_extraction_rules.xml` + verification test. **Без этого не мержим.**
- **T1245** — кнопки zoom в DocumentViewer для бабушек со слабым зрением (TalkBack). **Без этого не мержим.**
- **T1255** — privacy gate test (нет утечки label в metadata). **Без этого не мержим.**

**Параллелизм**: после Phase 1 много задач можно делать параллельно (Phase 2 entire, picker branches, US-1 vs US-2 flows).

**Trace**: каждая task имеет ссылку на FR / US / Plan section, чтобы видеть «зачем эта таска». Покрытие 24/24 FR + 11/11 SC verified в таблице выше.

**Дальше**: `speckit-analyze` — pre-implementation audit (catches что слипло между clarify/plan/tasks).

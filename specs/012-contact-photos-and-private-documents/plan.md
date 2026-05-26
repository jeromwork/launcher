# Implementation Plan: Contact Photos and Private Documents

**Branch**: `012-contact-photos-and-private-documents` · **Date**: 2026-05-26 · **Spec**: [spec.md](spec.md)
**Input**: clarified spec.md + 14 checklists в [checklists/](checklists/) + [_overview.md](checklists/_overview.md)

## Summary

Первый видимый клиент крипто-фундамента спека 011 + универсальный pipeline для приватных медиафайлов. Реализует фото контактов на плитках бабушки через VCard share intent / system contacts picker (US-1) + новый UX-flow «+ документ» для личных документов с fullscreen DocumentViewer (US-2). Шифрование один раз при upload, расшифровка один раз при первом download; дальше расшифрованный файл лежит persistent в app-private storage и открывается мгновенно (модель WhatsApp/Telegram).

**Технический подход**: 3 слоя — domain ports (Layer 1: PrivateMediaUploader/Resolver/LocalMediaStore facades поверх 011-портов; Layer 2: MediaPicker port с внутренним API-level dispatch; Layer 3: handheld UI). Никаких новых backend dependencies, никаких новых runtime permissions, 0 новых SDK в Core.

## Technical Context

| | |
|---|---|
| **Language/Version** | Kotlin 2.0+ (Multiplatform), Compose-Multiplatform |
| **Primary Dependencies** | libsodium (через `:adapters:crypto:lazysodium`, owned by 011); Backblaze B2 + Cloudflare Worker proxy (через `:adapters:storage:b2-worker`, owned by 011); androidx.activity (PhotoPicker compat); Coil 3 (bitmap loading, уже в проекте) |
| **Storage** | `Context.filesDir/private-media/<uuid>` для расшифрованных файлов (app-private, persistent); SQLDelight `BlobReferenceLedger` (унаследован из 011); Firebase Storage для encrypted blobs (унаследован из 011) |
| **Testing** | kotlin.test (commonTest), JUnit (androidTest), MockK для fakes, Compose UI Test для DocumentViewer |
| **Target Platform** | Android (minSdk=26, targetSdk=35, проверено `gradle/libs.versions.toml:5-7`) |
| **Project Type** | mobile-app (Compose-Multiplatform single-module structure with feature modules) |
| **Performance Goals** | Cache hit ≤ 100 мс; первая загрузка blob ≤ 3 секунды (на medium-tier device + 3 Mbit/s сетях); APK delta ≤ 500 KB |
| **Constraints** | minSdk=26 (требует SAF fallback ветку в MediaPicker); 0 новых permissions (SC-007); 500 KB cap на blob size (унаследовано из 011); persistent local storage без TTL (out of scope защита от переполнения) |
| **Scale/Scope** | Типичный пользователь — ≤ 15 контактов с фото + ≤ 10 документов на пару бабушка-admin; ~ 30 MB local storage на пару; threat model: admin = доверенный родственник, не adversarial |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Запускается через `procedure-constitution-check` — результат вставится сюда после генерации плана. См. секцию **Constitution Check (re-run)** ниже.

## Project Structure

### Documentation (this feature)

```text
specs/012-contact-photos-and-private-documents/
├── spec.md                               # ✓ done (specify + clarify)
├── plan.md                               # this file
├── research.md                           # ↓ Phase 0
├── data-model.md                         # ↓ Phase 1
├── quickstart.md                         # ↓ Phase 1 (data-extraction-rules workflow)
├── contracts/
│   ├── tile-document-kind.md             # new sealed variant in /config
│   ├── metadata-kind-registry.md         # envelope metadata.kind values
│   └── local-media-store-layout.md       # Context.filesDir/private-media/ layout
├── checklists/
│   └── _overview.md + 14 checklist files # ✓ done
└── tasks.md                              # ↓ Phase 2 (speckit-tasks)
```

### Source Code (repository root)

```text
core/
├── api/
│   ├── media/
│   │   ├── PrivateMediaUploader.kt       # facade port (commonMain)
│   │   ├── PrivateMediaResolver.kt       # facade port (commonMain)
│   │   ├── LocalMediaStore.kt            # port (commonMain)
│   │   ├── MediaPicker.kt                # port + MediaPickResult + MediaPickerError (commonMain)
│   │   └── PrivateMediaKind.kt           # enum Image | Document (commonMain)
│   └── config/
│       └── Tile.kt                       # 🟡 EXTEND: add DocumentTile sealed variant
└── domain/
    └── media/                            # ⚠️ если plan-phase решит facade здесь vs в :facades:private-media
        ├── PrivateMediaUploaderImpl.kt   # pure Kotlin facade implementation
        └── PrivateMediaResolverImpl.kt   # pure Kotlin facade implementation

adapters/
├── media-picker/                         # NEW MODULE
│   └── src/androidMain/kotlin/
│       └── SystemPhotoPickerAdapter.kt   # internal API-level dispatch (33+ / 29-32 / 26-28)
├── local-media-store/                    # NEW MODULE (или в :app)
│   └── src/androidMain/kotlin/
│       └── FileLocalMediaStore.kt        # Context.filesDir/private-media/<uuid>
└── (existing: crypto:lazysodium, storage:b2-worker, storage:firestore — без изменений)

features/
└── private-media/                        # NEW (или extend :features:settings:ui)
    └── src/androidMain/kotlin/
        ├── ui/
        │   ├── DocumentViewerScreen.kt           # fullscreen viewer + TalkBack zoom
        │   ├── AdminAddDocumentScreen.kt          # «+ документ» + label form
        │   ├── AdminUploadProgressScreen.kt       # loader + retry
        │   └── AdminDecryptIndicator.kt           # admin indicator с re-add/re-pair hints
        └── tile/
            ├── ContactTileWithAvatar.kt           # обновлённая плитка контакта (FR-014)
            └── DocumentTileRenderer.kt            # плитка-документ с thumbnail (FR-014)

app/
└── src/main/res/
    └── xml/
        └── data_extraction_rules.xml     # 🚨 MANDATORY: exclude private-media/ from backup

docs/
└── dev/
    └── private-media-architecture.md     # 🚨 MANDATORY: extensibility guide (FR-024)
```

**Structure Decision**: используем существующий launcher module layout. Один новый adapter module `:adapters:media-picker` (justified by Anti-Corruption Layer для System Photo Picker — CLAUDE.md rule 2). `LocalMediaStore` adapter — может быть в отдельном `:adapters:local-media-store` ИЛИ в `:app` (plan-phase decision: предпочесть `:app` для минимизации фрагментации; module создаётся только если потребуется отдельная testability). Facades `PrivateMediaUploader/Resolver` — **в `:core:domain`** как pure Kotlin (отдельный module `:facades:private-media` не создаётся — недостаточно для отдельного module per Article V §3 и meta-minimization CHK016).

## Architecture

### Data flow — upload (admin path)

```
admin tap «+ документ» (US-2) или принимает VCard share intent (US-1)
        │
        ▼
[UI screen: AdminAddDocumentScreen или VCardImportFlow]
        │ bytes (от MediaPicker.bytes OR VCard PHOTO field base64-decoded)
        │ + label (для документа, для контакта label=null)
        │ + kind (Image | Document)
        ▼
[PrivateMediaUploader facade] ◀── pure Kotlin, в :core:domain
        │
        ├──▶ AeadCipher.encrypt(bytes, key, nonce, aad)         (port 011)
        ├──▶ AsymmetricCrypto.sealForRecipient(...)             (port 011)
        ├──▶ EncryptedMediaStorage.upload(linkId, uuid, envelope)  (port 011)
        ├──▶ BlobReferenceLedger.increment(uuid, refSource)     (port 011)
        │
        └──▶ returns Outcome<IconRef("private:<uuid>"), CryptoError>
        │
        ▼
[Caller сохраняет результат]:
   - US-1: Contact.photoRef = "private:<uuid>" → /config push
   - US-2: Tile.DocumentTile(documentRef = "private:<uuid>", label) → /config push
        │
        ▼
FCM push → Managed device → applyFromRemote → плитка появляется
```

### Data flow — show (Managed path, плитка контакта или документа)

```
Compose render плитки → IconStorage.resolve("private:<uuid>")
        │
        ▼
[PrivateMediaResolver facade] ◀── pure Kotlin, в :core:domain
        │
        ├──▶ LocalMediaStore.read(uuid) → File?
        │       │
        │       ├── FOUND (recurring show): открывает файл, returns IconResolution.Bitmap. STOP (≤100 мс).
        │       │
        │       └── NOT FOUND (first show):
        │             │
        │             ├──▶ EncryptedMediaStorage.download(linkId, uuid) → EncryptedEnvelope    (port 011)
        │             ├──▶ AeadCipher.decrypt(ciphertext, key, nonce, aad) → bytes              (port 011)
        │             ├──▶ LocalMediaStore.write(uuid, bytes) → File                            (port 012)
        │             ├──▶ returns IconResolution.Bitmap (от write'нутого файла)
        │             │
        │             └── ON ERROR (любая CryptoError):
        │                   ├──▶ structured log event «media_decrypt_failed.<subcategory>»
        │                   ├──▶ /state.partialApplyReasons += MediaDecryptFailed
        │                   └──▶ returns IconResolution.Placeholder
        │
        ▼
Compose render: либо реальный bitmap, либо placeholder
```

### Module dependencies (high-level)

```
:features:private-media:ui  ──▶  :core:api  ──▶  :core:domain (facade impls)
                                    │                  │
                                    ▼                  ▼
                              :adapters:media-picker  :adapters:crypto:lazysodium  (port impls)
                              :adapters:local-media-store?  :adapters:storage:b2-worker
                              (или в :app)            :adapters:storage:firestore
                                                       (all unchanged from 011/008)

:app  ──wires all DI bindings──▶  все adapters
:app/src/main/res/xml/data_extraction_rules.xml  ◀── exclude private-media/
```

## Data Model

См. [data-model.md](data-model.md). Кратко — новые types:

| Type | Location | Description |
|---|---|---|
| `PrivateMediaKind` | `:core:api/media/` | enum: `Image`, `Document`. Расширяемая, additive в future spec'ах. |
| `PrivateMediaUploader` | `:core:api/media/` | facade port. `suspend fun upload(bytes, kind, linkId): Outcome<IconRef, CryptoError>` |
| `PrivateMediaResolver` | `:core:api/media/` | facade port. `suspend fun resolve(ref: IconRef): IconResolution` |
| `LocalMediaStore` | `:core:api/media/` | port. `read(uuid): File?`, `write(uuid, bytes): File`, `delete(uuid)`, `exists(uuid): Boolean` |
| `MediaPicker` | `:core:api/media/` | port. `suspend fun pick(kind, maxItems, mode): Outcome<List<MediaPickResult>, MediaPickerError>` |
| `MediaPickResult` | `:core:api/media/` | `(bytes: ByteArray, mimeType: String, sourceLabel: String?)` |
| `MediaPickerError` | `:core:api/media/` | sealed: `Cancelled`, `InvalidMimeType(actual, expected)`, `IOError(cause)` |
| `Tile.DocumentTile` | `:core:api/config/` | NEW sealed variant: `(id: ElementId, documentRef: String, label: String)` |

## Wire Formats

См. файлы в [contracts/](contracts/):

| Format | Owner | Change in 012 |
|---|---|---|
| [`tile-document-kind.md`](contracts/tile-document-kind.md) | spec 008 (extended by 012) | Новый sealed variant `Tile.DocumentTile` — `kind="document"` + `documentRef` + `label`. **Additive без `schemaVersion` bump** (Q2 deviation до spec 030+). |
| [`metadata-kind-registry.md`](contracts/metadata-kind-registry.md) | spec 011 (envelope) | Первое реальное наполнение envelope `metadata.kind`. Значения: `"image"`, `"document"`. Sensitive labels — внутри ciphertext, не в metadata. |
| [`local-media-store-layout.md`](contracts/local-media-store-layout.md) | spec 012 (new) | НЕ wire format (app-private, не cross-version migration). Документируется ради exclusion из backup'а (`data_extraction_rules.xml`). |
| `/state/current.partialApplyReasons` | spec 008 (extended by 012) | Первое реальное эмитирование значения `MediaDecryptFailed` (enum value зарезервирован). Никаких schema изменений. |

## Dependency Impact

**Новые gradle dependencies**:
- `androidx.activity:activity-compose` — **уже в проекте** (для существующих ActivityResult APIs). Используем `ActivityResultContracts.PickVisualMedia()` для веток API 33+ и 29-32. **Без новой зависимости**.
- `androidx.documentfile:documentfile` — для SAF ветки API 26-28, чтения content URI в bytes. **Минимальный delta** (~50 KB).

**Переиспользуемые dependencies** (без изменений):
- libsodium (через `:adapters:crypto:lazysodium`) — от 011.
- Backblaze B2 + Cloudflare Worker — от 011 (без изменений в Worker code).
- Coil 3 — для bitmap loading в плитках и DocumentViewer.
- SQLDelight — для `BlobReferenceLedger` (от 011).

**APK delta budget**: ≤ 500 KB (SC-006). Состав:
- `androidx.documentfile` ≈ 50 KB
- `:adapters:media-picker` Kotlin code ≈ 30 KB
- `:features:private-media:ui` Compose screens ≈ 200-300 KB (DocumentViewer + admin screens + indicator)
- `:core:api/media/` ports ≈ 10 KB
- Resources (strings, drawables) ≈ 50 KB
- **Total estimate**: 350-450 KB ≤ 500 KB budget ✓

**Justification per Article XIII**: единственная новая dependency — `androidx.documentfile`, оправдана необходимостью SAF поддержки на minSdk=26 (FR-008). Альтернатива (`ContentResolver.openInputStream` напрямую без `DocumentFile`) рассмотрена в research.md — отвергнута для надёжности URI handling.

## Test Strategy

Per CLAUDE.md §6 + §7 + spec.md SC.

### Contract tests (`commonTest`)

- `TileWireFormatTest.roundtrip_document` — `Tile.DocumentTile` write → read → assertEquals.
- `EnvelopeMetadataKindTest.roundtrip_image_document` — envelope с metadata.kind ∈ {"image", "document"} → roundtrip.
- `PrivateMediaKindSerializationTest` — enum serialize as lowercase string.
- `MetadataKindRegistryTest` — известные значения validated, unknown — graceful (envelope нейтрален).

### Integration tests (`androidTest`)

- `PrivateMediaUploaderIntegrationTest.upload_decrypt_roundtrip` — bytes in → upload (fake EncryptedMediaStorage) → resolve (fake) → bytes out, deep-equal.
- `PrivateMediaResolverIntegrationTest.first_show_cache_miss` — фиксирует, что first show триггерит download + decrypt + LocalMediaStore.write.
- `PrivateMediaResolverIntegrationTest.repeat_show_local_hit` — повторный show только LocalMediaStore.read, без download.
- `LocalMediaStoreIntegrationTest.persist_through_recreation` — write → recreate Activity → read still works.
- `LocalMediaStoreIntegrationTest.private_media_excluded_from_backup` — verify `data_extraction_rules.xml` exclude.

### Fake adapters (per CLAUDE.md §6)

- `FakeMediaPicker` (in-memory queue of preconfigured results).
- `FakeLocalMediaStore` (in-memory ConcurrentHashMap<uuid, ByteArray>).
- `FakeEncryptedMediaStorage` (in-memory, унаследовано из 011 testing).
- Wired through DI variant `mockBackend` (унаследованная инфраструктура спека 011).

### UI tests (`androidTest`, Compose UI)

- `DocumentViewerScreenTest.tap_close_navigates_back`.
- `DocumentViewerScreenTest.pinch_to_zoom_works_when_TalkBack_off`.
- `DocumentViewerScreenTest.zoom_buttons_visible_when_TalkBack_on` — **mandatory** для accessibility fix.
- `AdminAddDocumentScreenTest.label_validation_1_to_40_chars`.
- `AdminAddDocumentScreenTest.upload_progress_visible_during_decrypt`.
- `AdminDecryptIndicatorTest.shows_hint_re_add_for_mac_failed`.
- `AdminDecryptIndicatorTest.shows_hint_re_pair_for_key_not_found`.

### Privacy test (mandatory for FR-006 / SC-008)

- `EnvelopeMetadataPrivacyTest.no_label_leak_in_plaintext_metadata` — шифровать document с label «TestLabelLeak» → парсить plaintext envelope metadata → assert строка отсутствует. Это **gate test** для privacy инварианта.

### Fitness functions (per CLAUDE.md §7)

**Spec 012 НЕ вводит автоматический Konsist gate** (Clarification Q3 — отложено). Вместо этого:
- KDoc-комментарии на крипто-портах (`AeadCipher / AsymmetricCrypto / EncryptedMediaStorage / DigitalSignature / HashFunction / SecureKeystore`) с явной директивой «not for direct use — go through PrivateMediaUploader/Resolver facade».
- KDoc-комментарии на facade ports (`PrivateMediaUploader / PrivateMediaResolver`) — «entry point для media operations».
- Раздел в `docs/dev/private-media-architecture.md` про правильное использование.
- Проверка KDoc presence — manual в `speckit-analyze`.

## Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | 🚨 **Google Drive backup утечёт расшифрованные фото паспортов** в Google account бабушки | `data_extraction_rules.xml` exclude `private-media/` (mandatory plan-phase action item, см. quickstart.md). Verified by integration test `LocalMediaStoreIntegrationTest.private_media_excluded_from_backup`. |
| R2 | 🚨 **TalkBack пользователь не может zoom в DocumentViewer** (pinch перехватывается screen reader'ом) | DocumentViewer всегда показывает кнопки `+` / `−` ≥ 56dp (FR-019b mandatory). UI test `DocumentViewerScreenTest.zoom_buttons_visible_when_TalkBack_on`. |
| R3 | Storage переполнение на устройстве бабушки от 100+ файлов | Out of scope для 012 (TODO-ARCH-019). Принимаем threat model «admin = доверенный». Реальный bug-report триггернёт реализацию. |
| R4 | На API 26-28 SAF fallback даёт URI, который потом не читается (OEM quirk) | `androidx.documentfile` library normalises URI handling. Manual UAT тест на Samsung medium-tier + Xiaomi medium-tier (CHK019 permissions-platform). |
| R5 | `partialApplyReasons` накапливаются и никогда не очищаются → admin indicator forever red | Plan-фиксация: «при следующем successful apply config'а — old reasons очищаются». Это **inherits** существующий contract спека 008. Если 008 это не делает — открыть follow-up task. |
| R6 | Decrypt медленнее ожидаемого на low-end Android (Snapdragon < 6xx) | Decrypt идёт в `Dispatchers.Default`, не блокирует UI. Loader показывается ≤ 3 секунды budget. Если реально слишком медленно — measure в perf-checkpoint, оптимизировать в spec 013+. |
| R7 | `documentRef` в спеке 008 reader без поддержки → `unknown_slot_kind` для бабушки на старой in-progress версии | Pre-production свобода Q2: программа не в production до spec 030+. Старых in-progress версий **не существует у пользователей** (только у разработчика). Mitigation: явное assumption. |
| R8 | Backblaze B2 throttle / quota exceeded | Унаследовано из 011 (`SRV-CRYPTO-001` в server-roadmap.md). Spec 012 не ухудшает. |

## Required Context Review

Per Article XII §7 — следующие документы governance / ADR / product / compliance проверены и MUST консультироваться при разработке:

| Document | Why relevant |
|---|---|
| [`docs/governance/document-map.md`](../../docs/governance/document-map.md) | Структура артефактов проекта |
| [`docs/product/roadmap.md`](../../docs/product/roadmap.md) §Spec 012 | Position в roadmap |
| [`docs/product/senior-safe-launcher-plan.md`](../../docs/product/senior-safe-launcher-plan.md) | Senior-safe override (Article VIII §7) — ≥ 56dp tap, ≥ 4.5:1 contrast |
| [`docs/adr/ADR-001-platform-parity-gate.md`](../../docs/adr/ADR-001-platform-parity-gate.md) | Domain isolation rules |
| [`docs/adr/ADR-004-localization-and-global-readiness.md`](../../docs/adr/ADR-004-localization-and-global-readiness.md) | strings.xml + ru/en + plurals (SC-011) |
| [`docs/adr/ADR-005-performance-targets.md`](../../docs/adr/ADR-005-performance-targets.md) | APK budget (release ≤ 12 MB), frame budget, dispatcher invariants |
| [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) | Update «Storage usage» с записью о `private-media/` (CHK022 permissions-platform) |
| [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md) `SRV-CRYPTO-001` | Унаследованный exit ramp B2 → собственный server |
| [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) TODO-DESIGN-001 / ARCH-017/018/019 | Связанные backlog items |
| [`specs/011-contacts-and-e2e-encrypted-media/`](../011-contacts-and-e2e-encrypted-media/) | Все 8 портов + envelope wire format + BlobReferenceLedger |
| [`specs/008-bidirectional-config-sync/contracts/state-applied.md`](../008-bidirectional-config-sync/contracts/state-applied.md) | `MediaDecryptFailed` enum value |
| [`specs/006-provider-capabilities-and-health/contracts/icon-id-namespace.md`](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md) | `private:` namespace обязательство |
| [`specs/009-admin-mode-flows/spec.md`](../009-admin-mode-flows/spec.md) | VCard share intent + ACTION_PICK + редактор раскладки |

## Constitution Check (re-run)

Run: 2026-05-26, via `procedure-constitution-check`. **Result: 8/8 PASS** — plan COMPLETE.

| Gate | Result | Justification |
|---|---|---|
| 1. Architecture | PASS | Один новый module (`:adapters:media-picker`) justified per Article V §3 (ACL для system Photo Picker). Facades в `:core:domain` как pure Kotlin (без отдельного module per meta-minimization). `LocalMediaStore` Android impl в `:app` (rejected premature module). См. research.md R6. |
| 2. Core/System Integration | PASS | Нет новых broadcasts/boot receivers/package events. Только ActivityResultLauncher (UI-level, lifecycle-aware). Existing housekeeping reconciler 011 переиспользуется без модификации. |
| 3. Configuration | PASS | Wire-format extension (Tile.DocumentTile + metadata.kind) — additive до spec 030+ (Q2 deviation, sunset зафиксирован). schemaVersion унаследован из 008/011. Contract files определяют validation + forward-compat behaviour. |
| 4. Required Context Review | PASS | 13 documents linked: document-map, roadmap, senior-safe-launcher-plan, ADR-001/004/005, permissions-and-resource-budget, server-roadmap, project-backlog, specs 011/008/006/009. |
| 5. Accessibility | PASS | ≥ 56dp tap target (FR-014, FR-018); ≥ 4.5:1 contrast (FR-014); **TalkBack zoom buttons** mandatory fix явно в R2 risk + tests + DoD + quickstart Step 2. |
| 6. Battery/Performance | PASS | No new background tasks; no polling; cold-start cost ≈ 5 мс (DI only). Measurable targets: SC-003 (≤100 мс hit, ≤3s first), SC-006 (APK ≤500 KB). Dispatcher invariant в test strategy. |
| 7. Testing | PASS | Contract roundtrip + forward-compat; integration fake adapter wiring; UI tests; **privacy gate test** (no label leak); **backup exclusion test**; **accessibility zoom buttons test**. Каждый port имеет fake + real adapter + DI. |
| 8. Simplicity | PASS | Все Layer 1 facades justified Test 1 (inline ablation = leak crypto в UI). `PrivateMediaKind` enum borderline, допустим. Test 2 (swap cost): bounded к одному adapter per vendor. Cost-of-swap paragraph в backend-substitution checklist. |

**Notable deviations** explicitly justified в Complexity Tracking ниже.

## Rollout / Verification

### Acceptance gates перед merge'ем

1. **All checklists** в [checklists/](checklists/) показывают 0 violations + 0 blockers. ✓ (см. _overview.md).
2. **Constitution Check** — 8/8 PASS.
3. **Privacy test** `EnvelopeMetadataPrivacyTest.no_label_leak_in_plaintext_metadata` — green.
4. **Backup test** `LocalMediaStoreIntegrationTest.private_media_excluded_from_backup` — green.
5. **Accessibility test** `DocumentViewerScreenTest.zoom_buttons_visible_when_TalkBack_on` — green.
6. **APK delta** measured release build — ≤ 500 KB.
7. **Manual senior-walkthrough** на Samsung medium-tier + Xiaomi medium-tier: US-1 happy path + US-2 happy path + US-3 (повреждённый envelope) + US-4 (delete cleanup).
8. **i18n verification** — все user-facing строки имеют ru и en переводы.

### Post-merge verification

- Smoke test на стенде с реальным admin device + Managed device: end-to-end share contact с фото → плитка появляется ≤ 30s.
- Storage observability: проверить B2 bucket size / object count через 1 неделю — не должно расти неожиданно.

## Complexity Tracking

Constitution gates — будут проверены после `procedure-constitution-check`. Текущие потенциальные deviations:

| Potential deviation | Why needed | Simpler alternative rejected because |
|---|---|---|
| Pre-production wire-format additive без `schemaVersion` bump (Q2 deviation, Article VII §3) | Программа не в production до spec 030+; backwards-compat constraint не применяется | Bump 1→2 + reader-migration сейчас = lost work (никто не читает с другой версии) |
| `FLAG_SECURE` отключён на DocumentViewer (Article XIV §3-4) | Согласуется с industry baseline WhatsApp/Telegram; threat model 012 не покрывает device-local adversary | FLAG_SECURE сделал бы запись экрана невозможной → admin не сможет remote-помочь бабушке screen-share'ом |
| `LocalMediaStore` без encryption на устройстве (Article XIV §3) | Та же threat model | Двойное шифрование adds complexity без gain в принятой модели |
| Pinch-to-zoom + кнопки `+/−` одновременно (избыточно для full-vision пользователя) | TalkBack пользователь не может pinch → нужен accessible fallback (CHK accessibility adjacent concern) | «pinch only» — accessibility violation; «buttons only» — UX downgrade для остальных |

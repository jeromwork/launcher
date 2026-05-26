# Checklist: security

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 19/24 ✓ + 2 N/A + 3 explicit-deviation-with-rationale

---

## Data at rest (MASVS-STORAGE)

- [ ] **CHK001** — No PII in clear-text SharedPreferences / unencrypted files
  - **Status**: ⚠️ explicit-deviation-with-rationale.
  - **Что в clear-text**: расшифрованные фото контактов и документов лежат в `Context.filesDir/private-media/<uuid>` **без локального шифрования** (Clarification Q5).
  - **Rationale**: модель «сервер не видит → клиент видит после download» — industry baseline (WhatsApp, Telegram, Signal default mode). Threat model 012 покрывает канал «сервер ↔ клиент» и «admin ↔ Managed», но **не** покрывает device-local adversary (физический доступ, root, adb backup).
  - **`android:allowBackup`** — должен быть `false` или `data_extraction_rules` исключает `private-media/` (см. CHK024).
  - **Sensitive document labels** («Паспорт», «Медкарта»): зашифрованы **внутри ciphertext envelope** (FR-006, SC-008), НЕ в metadata. ✓ — это **главное** privacy-инвариант 012.
- [x] **CHK002** — Sensitive data (auth tokens) — Keystore: ✓ N/A (auth tokens — owned by 011 SecureKeystore port).
- [ ] **CHK003** — Cache files TTL + clear-on-uninstall
  - **Status**: ⚠️ explicit-deviation.
  - Files в `Context.filesDir/private-media/` — **без TTL** (Clarification Q5).
  - Clear-on-uninstall — **автоматически Android** (filesDir удаляется на uninstall). ✓
  - Wipe-on-revoke — **out of scope** ([`TODO-ARCH-019`](../../docs/dev/project-backlog.md)).
- [x] **CHK004** — Logging excludes PII: ✓
  - FR-021 явно: structured log с категорией + sub-category (`media_decrypt_failed.mac_failed`, `.blob_missing`), без PII в payload.
  - blob `uuid` — random UUIDv4, не PII.

## Data in transit (MASVS-NETWORK)

- [x] **CHK005** — HTTPS / TLS 1.2+: ✓ (унаследовано из 011 — Backblaze B2 через Cloudflare Worker, оба HTTPS only).
- [x] **CHK006** — Certificate pinning: ✓ N/A (Cloudflare Worker + B2 — стандартный TLS; pinning для consumer apps usually out of scope, см. TODO-SEC-002 если есть).

## Authentication / Authorization (MASVS-AUTH)

- [x] **CHK007** — Privileged actions list permission/role:
  - Storage Rules 011: `isLinkMember()` (admin OR managedDeviceFirebaseUid). ✓
  - Spec 012 не вводит новых privileged actions.
- [x] **CHK008** — No security-by-obscurity: ✓
  - Все security boundaries — explicit (Storage Rules, envelope MAC, recipient list).

## Platform interaction (MASVS-PLATFORM)

- [x] **CHK009** — Exported components: ✓ N/A. Spec 012 не вводит exported activities/services/receivers.
- [x] **CHK010** — Intents to other apps: ✓
  - `MediaPicker.SystemPhotoPickerAdapter` — system intents (`ACTION_PICK_IMAGES` / `ACTION_OPEN_DOCUMENT`), не cross-app.
  - VCard share intent — incoming, обрабатывается в спеке 009.
- [x] **CHK011** — Deep links validated: ✓ N/A (spec 012 не вводит новых deep-link'ов).
- [x] **CHK012** — Intent extras size-bounded + type-checked: ✓
  - VCard share — стандартный Intent.EXTRA_STREAM (validated в спеке 009).
  - Picker result — стандартный ActivityResult URI; bytes читаются через ContentResolver с size check (FR-009: ≤ 500 KB cap).
- [x] **CHK013** — No exported ContentProvider: ✓ N/A.
- [x] **CHK014** — WebView: ✓ N/A.

## Permissions (Article XIV)

- [x] **CHK015** — Each permission justified by FR: ✓
  - **0 новых permissions** в spec 012 (SC-007, FR-008). Никаких justifications не требуется.
- [x] **CHK016** — No "future use" permissions: ✓.
- [x] **CHK017** — Fallback for denied permissions: ✓ N/A.
- [x] **CHK018** — `docs/compliance/permissions-and-resource-budget.md` updates: ✓ N/A (no new permissions).

## Privacy (Article XIV §3, §4)

- [x] **CHK019** — No hidden behavioural collection: ✓
  - Никаких analytics, telemetry, behavioural tracking не вводится.
- [x] **CHK020** — Local-first preferred: ✓
  - Расшифрованные файлы — **локально persistent** (Clarification Q5). Каждый последующий показ — местный.
  - Server (B2) видит только encrypted blobs.
- [x] **CHK021** — Data leaves device: notice + opt-in
  - **Status**: ✓ implicit. Admin **знает**, что отправляет blob на server (это явный «+ документ» / share intent action).
  - **Действие** (plan): убедиться, что upload progress UI явно говорит «загружаю на сервер» (а не «обрабатываю»).
- [x] **CHK022** — Data minimisation: ✓
  - Envelope metadata = minimal (`kind`, possibly `createdAt`). Sensitive label — **внутри ciphertext** (FR-006).
  - blob name = random UUID (не PII).
  - В Cloudflare Worker логах — только IP + timestamp + size (per `TODO-PRIVACY-001`).

## Build hardening

- [x] **CHK023** — No debug flags in release: ✓ inherited (R8 + BuildConfig.DEBUG gates).
- [ ] **CHK024** — Backup rules reviewed
  - **Status**: ⚠️ explicit-deviation-with-rationale.
  - **Что в backup**: `Context.filesDir/private-media/` — содержит расшифрованные PII фото/документы.
  - **Threat**: если `android:allowBackup=true` или `data_extraction_rules` не исключает эту папку → Google Drive backup утечёт PII в Google account бабушки.
  - **Решение** (plan-phase mandatory):
    1. Либо `android:allowBackup="false"` глобально (но это блокирует и полезные backup'ы launcher config'а).
    2. Либо `data_extraction_rules` v31+ исключает `<exclude path="private-media/"/>` для `cloud-backup` и `device-transfer` (предпочтительно).
  - **Действие**: plan-phase task — добавить `data_extraction_rules.xml` exclude для `private-media/` directory. Это **mandatory**, не optional.

---

## Summary

| Status | Count |
|---|---|
| ✓ | 19 |
| N/A | 2 |
| ⚠️ explicit-deviation / open | 3 (CHK001 unencrypted local, CHK003 no TTL, CHK024 backup rules) |
| ✗ violations | 0 |

**Critical action items** (must address in plan-phase):
1. **CHK024** ⚠️ **MANDATORY**: `data_extraction_rules.xml` — exclude `private-media/` from cloud-backup + device-transfer. Иначе Google Drive backup сольёт фото паспортов в Google account. Это **single point** of accidental PII leak в текущей модели.
2. **CHK001 + CHK003**: задокументировать threat model спеком 012 в `docs/dev/private-media-architecture.md`:
   - «In scope: защита канала сервер ↔ клиент через E2E crypto.»
   - «Out of scope: device-local adversary с физическим / root / adb доступом. Согласуется с baseline WhatsApp/Telegram. Будущее усиление — [`TODO-ARCH-019`](../../docs/dev/project-backlog.md).»
3. Privacy-инвариант (FR-006, SC-008) — sensitive label внутри ciphertext, не в metadata — **проверяется** static analysis тестом, который шифрует document с label «TestLabelLeak» и парсит обратно plaintext metadata; leak строки = fail.

**Verdict**: Spec 012 имеет **серьёзную и явную security story** — privacy-инвариант metadata vs ciphertext (FR-006) защищён тестом (SC-008), modеl «no FLAG_SECURE / no local encryption» **явно** обоснована через Clarification Q5 + threat model. **Единственный mandatory action** — backup rules (CHK024); остальное — informational policy.

**Constitution alignment**: Article XIV ✓ (с явными deviations через Q5), MASVS-STORAGE/NETWORK/AUTH/PLATFORM ✓.

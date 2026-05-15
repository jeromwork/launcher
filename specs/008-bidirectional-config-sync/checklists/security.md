# Checklist: security

**Spec**: `spec.md` (rev. 2026-05-14, post-clarify Q1-Q10)
**Run**: 2026-05-14 — `/speckit.clarify` post-pass before `/speckit.plan`.

Verifies OWASP MASVS + Article XIV constitution requirements.

---

## Data flow summary (для контекста чеклиста)

| Flow | Source | Destination | Sensitivity |
|---|---|---|---|
| `/config/current` write | editor (admin-phone / admin-tablet / Managed-phone) | Firestore | **medium** — contains contact names/numbers (PII) |
| `/config/current` read | Managed | Firestore | medium |
| `/state/current` write | Managed | Firestore | low-medium — applied snapshot may contain contact-id refs |
| FCM trigger payload | Cloudflare Worker | Managed FCM topic | low (trigger-only, no data) |
| Local applied-config (Room) | Managed | local SQLite | **medium** — same PII as `/config` |
| Local pending-changes (Room) | editor | local SQLite | medium |

**Key sensitivity points:**
- `/config.contacts[]` contains phone numbers — это PII (per Article XIV definition).
- `lastWriterDeviceId` идентифицирует устройство — pseudonym, не direct PII, но связывается с linkId/adminId.

---

## Data at rest (MASVS-STORAGE)

- [ ] **CHK001 — No PII stored in clear-text SharedPreferences / unencrypted files unless justified**

  **Finding: WATCH.** Spec 008 хранит `/config` (включая contacts с phone numbers) в **Room** локально (FR-041, FR-042). Room database — это SQLite файл в app's internal storage. По умолчанию **не шифрованный**.

  - Linux/Android security model: internal app storage защищён UID isolation (другие приложения не имеют доступа). Это **base layer protection**.
  - Но: rooted device, ADB backup (если включен), forensic recovery — могут получить доступ.
  - Spec 011 (`contacts-and-e2e-encrypted-media`) явно планирует **e2e шифрование** для фото/медиа. Phone numbers — категория ниже (имена и телефоны менее sensitive чем фото).

  **Decision needed для plan.md**:
  - **Option A (текущий план)**: Room без шифрования, полагаемся на app sandbox. Соответствует общей практике Android-приложений (WhatsApp, Telegram).
  - **Option B**: SQLCipher для Room с derived-key (linkId как salt). Безопаснее, но +complexity, +size, +performance hit.
  - **Option C (рекомендация)**: Option A в 008; **revisit when спек 011 ships** (e2e media + ключевая инфраструктура).

  **Action для plan.md**: явно зафиксировать решение Option A с указанием threat model (что от чего защищаемся): защита от **другого app на устройстве** = ✅ (app sandbox); защита от **rooted device / forensic** = ❌ (требует full-disk encryption у пользователя). Privacy policy должна это упомянуть.

- [ ] **CHK002 — Sensitive data (auth tokens, biometric refs) in EncryptedSharedPreferences / Keystore**

  Spec 008 не хранит auth tokens или biometric refs. Firebase Auth tokens — управляются SDK (хранятся в Firebase-managed encrypted storage). Spec 003 ввёл PIN-stub `1234` — это **dev-stub**, формально не auth token; реальный PIN придёт в spec 010 (per roadmap).

  **N/A для 008** (нет новых auth secrets).

- [x] **CHK003 — Cache files containing user data have TTL and clear-on-uninstall policy**

  - Room database в `getDatabasePath()` или `applicationContext.databases/` — **auto-deleted on uninstall** (Android default). ✅
  - Pending changes (FR-043): «живут сколь угодно долго» — no TTL. Это **intentional UX decision** (per Q clarify), но **clean-on-revoke** должен работать (FR-034 — recursive subtree delete на revoke). **Action для plan.md**: при revoke link'а также очистить local pending-changes для этого linkId.

- [x] **CHK004 — Logging excludes PII**

  - Spec.md не специфицирует logging detail. Это plan.md.
  - **Action для plan.md**: явное правило «никогда не логировать содержимое `/config.contacts[]` или `flows[].slots[].args` целиком; категоризация — `[INFO] config.applied linkId=… count=10` not `[INFO] config.applied contacts=[...]`».
  - Watch: error paths (FR-013 PERMISSION_DENIED, FR-014 conflict) могут содержать diff'ы с PII — должны быть **redacted в production logs**.

## Data in transit (MASVS-NETWORK)

- [x] **CHK005 — All network calls use HTTPS / TLS 1.2+; no cleartext**

  - Firestore SDK uses HTTPS by default; cannot be downgraded by app.
  - FCM uses HTTPS.
  - Cloudflare Worker (spec 007) on `*.workers.dev` — HTTPS-only.
  - Spec.md does not add new endpoints. Inherits 007 network discipline.

- [x] **CHK006 — If certificate pinning is required: documented**

  Spec 007 не делал certificate pinning (typical для Firebase apps — relying on Google's CA + Play Protect). 008 наследует. **N/A для 008** unless plan.md upgrades.

## Authentication / Authorization (MASVS-AUTH)

- [x] **CHK007 — Every privileged action lists required permission + role / entitlement**

  - FR-010: write `/config/current` требует Firebase Auth UID совпадающий с `adminId` ИЛИ `managedDeviceFirebaseUid` link'а.
  - FR-011: Security Rules **расширение** спека 007 — write для adminId И managedDeviceFirebaseUid. **Critical**: это новое право на write, должно быть **явно** прописано в Security Rules (Firestore).
  - FR-030: write `/state/current` — только `managedDeviceFirebaseUid` (как в спеке 007 `state-bootstrap.md`).
  - Spec.md FR explicitly map privileges to auth identities. ✅

  **Action для plan.md**: Security Rules diff (минимальный):
  ```
  match /links/{linkId}/config/current {
    allow read: if isAdmin(linkId) || isManaged(linkId);
    allow write: if isAdmin(linkId) || isManaged(linkId);
    // FR-013 optimistic concurrency check enforced client-side через transaction;
    // server-side enforcement через `request.resource.data.serverUpdatedAt == request.time` (Firestore server-set)
  }
  ```

- [x] **CHK008 — No security-by-obscurity**

  - Spec не полагается на "hidden URL" / undocumented intent.
  - `linkId` is opaque Firestore document ID (per OWD-4 спека 007) — это **identifier**, не secret. Доступ контролируется Security Rules (auth-based), не secrecy.

## Platform interaction (MASVS-PLATFORM)

- [x] **CHK009 — Exported activities/services/receivers justified; non-exported is the default**

  Spec 008 не вводит новые exported components. Re-uses 007 components (FCM receiver, etc.). **N/A для 008**. **Watch для plan.md**: если потребуется новый Service для WorkManager — не exported.

- [x] **CHK010 — Intents to other apps use explicit package or setPackage**

  Spec 008 не отправляет intents в другие apps. **N/A**.

- [x] **CHK011 — Deep links validated**

  Spec 008 не вводит новые deep links. Pairing deep-link в 007 — validated там. **N/A для 008**.

- [x] **CHK012 — Intent extras received from external apps are size-bounded and type-checked**

  N/A — 008 не принимает intents от external apps. Все входные данные приходят через Firestore (validated via Security Rules + schema).

- [x] **CHK013 — No exported ContentProvider without permission protection**

  Spec 008 не вводит ContentProviders. **N/A**.

- [x] **CHK014 — WebView (if any) has JavaScript disabled or origin-restricted**

  Spec 008 не использует WebView. **N/A**.

## Permissions (Article XIV)

- [x] **CHK015 — Each requested permission justified by explicit FR (Article XIV §1, §2)**

  Spec 008 не вводит **новые runtime permissions**. Использует:
  - `INTERNET` — inherited via 007 (Firebase).
  - `ACCESS_NETWORK_STATE` — inherited (для FCM); reused для `ConnectivityManager.NetworkCallback` (FR-022 T2). **Action для plan.md**: подтвердить, что `ACCESS_NETWORK_STATE` уже в manifest спека 007 (вероятно да — Firebase автоматически просит).
  - Никаких `READ_CONTACTS` в 008 — это spec 011 (per OUT-008 implicit, через спек 011 reference).

  **Action для plan.md**: верифицировать manifest, обновить `docs/compliance/permissions-and-resource-budget.md` с явной строкой «spec 008 не добавляет new permissions».

- [x] **CHK016 — No permission requested for "future use"**

  Verified — нет permissions «прозапас».

- [x] **CHK017 — Fallback for denied permissions designed**

  - Если пользователь revoke'нул `ACCESS_NETWORK_STATE` — `NetworkCallback` не работает; **fallback**: WorkManager 15min poll (FR-022 T3) или RESUMED trigger (T4). Тройное резервирование.
  - Если пользователь revoke'нул `INTERNET` — все push/state операции failed gracefully (per spec 007 error handling). Pending state копит локально, push retry при возврате permission.
  - Spec.md явно покрывает offline behaviour (US-3 scenario 3, US-4 scenario 2, Edge Cases «App killed mid-edit»).

- [x] **CHK018 — Updates to `docs/compliance/permissions-and-resource-budget.md` planned**

  **Action для plan.md**: добавить task `T-DOCS-001: update permissions-and-resource-budget.md with 008 entry (no new permissions, reused INTERNET + ACCESS_NETWORK_STATE)`.

## Privacy (Article XIV §3, §4)

- [ ] **CHK019 — No hidden collection of behavioural / personal data**

  **Finding: WATCH.** Spec 008 пишет в Firestore:
  - `lastWriterDeviceId` — pseudonym, identifies which editor wrote last. Это **device identifier**, не PII по западному регулированию (GDPR), но **может стать link** между несколькими paired-устройствами (admin может видеть, что бабушка тоже что-то писала).
  - `appliedConfigUpdatedAt` — timestamp, не PII, но revealing usage pattern.

  **Decision needed**:
  - `lastWriterDeviceId` логически нужен для FR-023 (avoid double-apply). Это **functional necessity**.
  - Но: admin **видит** это поле (`/state` доступен admin'у через Security Rules read). Это **может быть voyeurism risk**: admin видит «бабушка только что что-то меняла в 3 ночи» → social pressure.
  - **Recommendation для plan.md**: `lastWriterDeviceId` хранится в `/config`, **не** в `/state`. Admin читает `/state` (что применилось), не `/config` (метаданные кто писал). Это уже the case в spec.md, но проверить, что в `/state` не утекает.

  Verified spec.md: FR-003 включает `lastWriterDeviceId` в `/config`. FR-031 не включает его в `/state`. ✅ Privacy ok at spec level.

- [x] **CHK020 — Local-first preferred (Article XIV §4); networked feature explicitly justified**

  - FR-040 (separate save локально vs push на сервер) — **explicit local-first**. Save локально работает always, push optional/deferred.
  - FR-041 (applied-config persisted Room) — local cache для fast bootstrap.
  - FR-044 (cold start чтение из Room без сети) — local-first principle.
  - Network feature (FR-010..014, FR-020..023) justified by US-1 (admin remote control) и US-2 (collaborative editing).
  - **Article XIV §4 compliance**: ✅ — networked feature justified, local-first design.

- [x] **CHK021 — If data leaves device: user-visible notice + opt-in (where regulation requires)**

  Spec 007 уже established trust boundary: pairing требует **explicit consent** (FR-007 spec 007), QR-scan + confirmation. After pairing, `/config` flows are **expected behavior** — data leaving device is the **purpose** of the pairing.

  No additional opt-in needed for 008 (это continuation of trust established in 007).

  **Watch**: privacy policy / user-facing notice should mention что `/config` synchronization sends data through Firebase. **Action для plan.md** (или spec 010 setup-assistant): privacy policy text.

- [x] **CHK022 — Data minimisation: only fields required for the FR are collected, stored, transmitted**

  Fields in `/config`:
  - `schemaVersion`, `serverUpdatedAt`, `lastWriterDeviceId` — required for protocol (FR-002, FR-013, FR-023).
  - `presetId`, `flows[]`, `contacts[]` — user-visible UI data; required для FR-021 apply.
  - No unnecessary fields. ✅

  Fields in `/state`:
  - `appliedConfigUpdatedAt`, `flowsApplied[]`, `contactsApplied[]`, `partialApplyReasons[]` — minimum needed для admin visibility.
  - No usage metrics, no analytics fields. ✅

  **Watch для plan.md**: если будут добавляться analytics fields (FCM token rotation tracking, error categorization) — **explicit FR** required, не «for monitoring».

## Build hardening

- [x] **CHK023 — No debug flags / verbose logging in release**

  Spec.md не специфицирует. **Inherits** существующий `BuildConfig.DEBUG` gate из проекта. **Action для plan.md**: явный note «008 не вводит debug-only paths shipping в release».

- [x] **CHK024 — Backup rules (android:allowBackup, data_extraction_rules) reviewed**

  **Action для plan.md** (mandatory):
  - 008 добавляет **новое Room database** с PII (contacts).
  - Текущее `android:allowBackup` setting проекта неизвестно из spec.md — проверить в androidMain `AndroidManifest.xml`.
  - **Recommendation**: `allowBackup="false"` для всего приложения (стандарт для apps с PII), либо настроить `data_extraction_rules.xml` явно исключая 008 Room database из backup.
  - Обоснование: ADB-backup или Google Auto-Backup сохранит PII в Google Drive / local backup — не та threat model, на которую мы рассчитываем (app sandbox protection).

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 18 | CHK002–CHK022 (most), CHK023, CHK024 |
| ⚠️ Watch (action items) | 3 | CHK001 (Room encryption decision), CHK004 (PII redaction in logs), CHK019 (lastWriterDeviceId privacy) |
| 🟢 N/A | 6 | CHK009–CHK014 (no exported components / intents / WebView / ContentProvider в 008), CHK006 (no pinning needed) |
| ❌ Fail | 0 | — |

**Verdict: PASS at spec-level with 3 watch items.**

Spec 008 inherits 007's solid security baseline. New surfaces (Room, NetworkCallback) introduce **acceptable** threat profile if Room remains unencrypted (Option A) — to be revisited when спек 011 (e2e media) lands.

---

## Mandatory action items для plan.md

1. **Security Rules diff** (Firestore): явное расширение rules спека 007 для `/config/current` write (adminId | managedDeviceFirebaseUid) + optimistic concurrency precondition.
2. **Threat model documentation**: явное decision Option A (Room без шифрования, polagaemsya на app sandbox); revisit при спеке 011.
3. **`lastWriterDeviceId` privacy**: подтвердить что поле живёт в `/config`, не в `/state` (предотвратить voyeurism risk).
4. **PII redaction в logs**: code-level rule — никогда не логировать содержимое contacts/slots целиком, только counts/categories.
5. **`docs/compliance/permissions-and-resource-budget.md` update**: добавить запись «008 не добавляет new permissions».
6. **Backup rules review**: `android:allowBackup="false"` или `data_extraction_rules.xml` исключающий 008 Room database.
7. **Privacy policy update** (или ссылка на спек 010 / setup-assistant для user-facing notice): упомянуть что `/config` синхронизируется через Firebase.
8. **Revoke clears local pending**: при revoke link'а очистить также local Room (FR-043 «pending lives forever» НЕ применяется к revoked links).

## Watch items long-term

- Спек 011 (e2e media) — пересмотреть Room encryption decision; возможно перейти на SQLCipher.
- Privacy regulation (GDPR / 152-ФЗ): если targeting EU/RU users — privacy policy и opt-in flow в setup-assistant.

**No spec.md edits required** (security обеспечивается на plan.md уровне и в Security Rules).

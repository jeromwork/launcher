# Checklist: security — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass

## Data at rest (MASVS-STORAGE)

- [x] **CHK001** No PII в clear-text SharedPreferences / unencrypted files.
  - Spec не вводит новых SharedPreferences usage.
  - FCM token stored в Firestore `/users/{uid}/devices/{deviceId}/fcmToken` (F-5b directory). Not local clear-text.
  - Idempotency-Key — runtime UUID v4, не persistent.

- [x] **CHK002** Sensitive data в EncryptedSharedPreferences / Keystore.
  - Firebase ID-token — managed by Firebase Auth SDK (not our concern).
  - FCM token — industry standard plaintext, не Keystore-worthy (it's a routing identifier, not credential).
  - JWKS cache (Worker) — public keys, not sensitive.

- [x] **CHK003** Cache files с user data have TTL.
  - Workers KV idempotency cache: 10-min TTL ✅ (FR-010).
  - Workers KV rate-limit data: per window (60s) ✅ (FR-006).
  - Workers KV JWKS cache: dynamic TTL по `Cache-Control` ✅ (FR-003).
  - Client side: no new persistent cache (Idempotency-Key generated runtime, FCM token persisted в Firestore не local cache).

- [⚠️] **CHK004** Logging excludes PII.
  - **НЕ explicit FR в spec**. Logcat / Cloudflare Analytics не constrained.
  - **Concern**: `ownerUid` — Firebase UID = identifier. `configName` ("main", "kitchen-tv") — может раскрыть details жилища.
  - Industry standard: log eventType + hash(uid) или truncated UID (e.g. first 8 chars), не full ownerUid + payload contents.
  - **Action**: добавить FR-XXX: «Push subsystem logs MUST use truncated UID (first 8 chars) + eventType, MUST NOT include full ownerUid, payload values, FCM token, или Idempotency-Key».

## Data in transit (MASVS-NETWORK)

- [x] **CHK005** All network calls HTTPS / TLS 1.2+.
  - Worker URL: `https://*.workers.dev` (CF enforces TLS 1.2+).
  - FCM API: HTTPS by design.
  - Firebase Auth ID-token acquisition: HTTPS (F-4 territory).
  - Firestore reads (from Worker): HTTPS REST API.
  - No `cleartextTrafficPermitted`.

- [N/A] **CHK006** Certificate pinning.
  - Cloudflare Worker URL serves standard CF cert (rotates regularly). Pinning would break on rotation.
  - FCM uses Google certificates — Firebase SDK handles trust.
  - Pinning не оправдан для нашей threat model (consumer app, не banking).

## Authentication / Authorization (MASVS-AUTH)

- [x] **CHK007** Every privileged action lists required permission / role.
  - Worker per-event-type authorisation rules:
    - `config-updated`: owner ∨ grant-holder (FR-005, EventTypeRegistry entry).
    - `sos-triggered` (future): owner only.
    - `entitlement-expired` (future): server-internal only.
  - Documented в FR-040 + Reuse pattern table.
  - Client-side: no new privileged actions без Firebase Auth Sign-In (consumed via F-4).

- [x] **CHK008** No security-by-obscurity.
  - Worker URL hardcoded в client code — visible через decompilation, network monitoring. Not relied upon for security.
  - Security relies on Firebase ID-token validation (FR-002) — cryptographic.

## Platform interaction (MASVS-PLATFORM)

- [⚠️] **CHK009** Exported activities/services/receivers justified; non-exported default.
  - `LauncherFirebaseMessagingService` extends `FirebaseMessagingService`. По Android 12+ requirement — service-with-intent-filter must declare `android:exported`.
  - Firebase Messaging Service requires `android:exported="false"` (Android docs) — system delivers messages, не external apps.
  - **НЕ explicit в spec**. Standard pattern.
  - **Action**: добавить FR-XXX: «`LauncherFirebaseMessagingService` MUST declare `android:exported="false"` в AndroidManifest».

- [N/A] **CHK010** Intents к other apps with explicit package.
  - Spec не вводит intent invocations к external apps.

- [N/A] **CHK011** Deep links validated.
  - Spec не вводит deep links.

- [⚠️] **CHK012** Intent extras size-bounded.
  - FCM data-message size limit 4KB (Google enforced). Receiver gets `Map<String, String>` from FCM SDK — automatically size-bounded.
  - **Concern**: receiver parsing — должна validate field types + values безопасно. Если attacker sends crafted FCM (теоретически requires hijacking FCM channel — impractical), corrupted payload должен не crash.
  - **Already addressed** в wire-format CHK017 action: «Receiver MUST handle malformed payload с Logcat warning + silent ignore, never crash».

- [N/A] **CHK013** Exported ContentProvider.
  - Spec не вводит ContentProvider.

- [N/A] **CHK014** WebView.
  - Spec не вводит WebView.

## Permissions (Article XIV)

- [⚠️] **CHK015** Each permission justified by FR.
  - **Existing**: `INTERNET` (already in manifest), `WAKE_LOCK` (FCM may use).
  - **Possibly new**: `POST_NOTIFICATIONS` (Android 13+ runtime permission).
    - F-5c uses data-only FCM messages (FR-018: no user-visible notification).
    - Data-only messages **не требуют** `POST_NOTIFICATIONS` for receipt (Google docs).
    - **Action**: verify in implementation — если `POST_NOTIFICATIONS` not needed, don't request.
  - **НЕ explicit в spec**. Should be FR.
  - **Action**: добавить FR-XXX: «F-5c MUST NOT request `POST_NOTIFICATIONS` permission. Data-only FCM messages received without notification permission. If future event types require visible alerts (e.g., SOS), они request permission separately в своих specs».

- [x] **CHK016** No permission for «future use».

- [x] **CHK017** Fallback for denied permissions designed.
  - `POST_NOTIFICATIONS` denied → still get data-only push (data delivers без visible alert). No fallback needed.
  - Если future SOS event requires visible alert and permission denied — fallback = silent log, push effectively no-op for that user.

- [⚠️] **CHK018** Updates to `docs/compliance/permissions-and-resource-budget.md` planned.
  - **Action**: добавить task в tasks.md «update permissions-and-resource-budget.md: clarify F-5c uses data-only FCM, no POST_NOTIFICATIONS required; document WAKE_LOCK usage if FCM triggers it».

## Privacy (Article XIV §3, §4)

- [⚠️] **CHK019** No hidden collection of behavioural / personal data.
  - **FCM token** = Google-tracked identifier per app install. By publishing к Firestore directory — мы facilitate Google's tracking (если doc could be combined с other Google signals).
  - Однако: FCM token publishing is **required for receiving push notifications** — это not hidden behavioural collection, это transport mechanism.
  - **Action**: ensure privacy policy (Sign-In flow disclosure) mentions «FCM token published к Firebase to enable cross-device sync». Typically already in standard Firebase privacy notice.

- [⚠️] **CHK020** Local-first preferred; networked feature explicitly justified.
  - F-5c is **inherently cloud feature** — push notifications require server intermediary.
  - **Justification documented** (Сценарий 1, 2 — cross-device sync невозможен без push channel).
  - Per decision 2026-06-15-deferred-cloud: cloud features activate **after first Sign-In** (local mode is free forever, no push).
  - **Action**: документировать в spec.md: «F-5c активируется только в cloud mode (после Sign-In). Local mode (no Sign-In) — push channel disabled, ConfigSaver не вызывает PushTrigger».

- [⚠️] **CHK021** Data leaves device — user-visible notice + opt-in (where regulation requires).
  - **Data leaving device** через `POST /push`:
    - `ownerUid` (Firebase UID — identifier).
    - `eventType` ("config-updated" — system event class).
    - `targetScope` ("own-and-grants" — recipient strategy, not PII).
    - `payload` (event-specific fields, e.g. `configName: "main"` — minimal identifier of what changed).
    - Idempotency-Key (UUID v4 — ephemeral).
    - Firebase ID-token (authentication credential).
  - **Privacy disclosure responsibility**: Sign-In flow (S-1 wizard territory) должен disclose что cloud sync involves sending event metadata.
  - **Action**: добавить task в tasks.md или в S-1 spec: «privacy policy disclosure обновить — F-5c sends event metadata (eventType, ownerUid, payload pointers) к family device push transport».

- [x] **CHK022** Data minimization.
  - Push payload минимум: pointer fields (`ownerUid + configName`), no encrypted content (FR-011).
  - Recipient retrieves freshly via existing `ConfigSaver.loadOwn` — decrypts locally.
  - Никаких user names, contact numbers, layout details в push payload — только «пойди подтяни обновление».

## Build hardening

- [x] **CHK023** No debug flags / verbose logging enabled в release.
  - Standard `BuildConfig.DEBUG` gating applies. F-5c не вводит new debug-enabled-in-release patterns.
  - Logging FR (CHK004 action) should explicitly gate verbose logging behind `BuildConfig.DEBUG`.

- [x] **CHK024** Backup rules reviewed для new persistent data.
  - F-5c не вводит new local DataStore / SharedPreferences / SQLDelight persistence.
  - FCM token published в Firestore, не stored locally beyond Firebase SDK's internal cache (which respects `allowBackup=false` of host app per F-5b).
  - `data_extraction_rules.xml` — no new entries needed.

## Summary

- **Pass**: 11/24
- **Partial/Warning**: 7/24 (CHK004, CHK009, CHK012 [dup], CHK015, CHK018, CHK019, CHK020, CHK021)
- **Fail**: 0/24
- **N/A**: 6/24 (CHK006, CHK010, CHK011, CHK013, CHK014)

**Big picture**: Security clean на network/auth/data minimization уровне. Concerns в operational / privacy disclosure areas (типично «забываются при clarify»):
- Logging PII hygiene (truncated UID, no payload values).
- Android Manifest `exported="false"` для service.
- POST_NOTIFICATIONS permission decision documented.
- Privacy disclosure для FCM token publishing + event metadata.
- Local-mode degradation (F-5c disabled when no Sign-In).
- permissions-and-resource-budget.md update.

## Action items (priority order)

1. **Высокая** (FR в spec.md): logging hygiene — truncated UID, no payload values, BuildConfig.DEBUG gating (CHK004, CHK023).
2. **Высокая** (FR в spec.md): `LauncherFirebaseMessagingService` `android:exported="false"` (CHK009).
3. **Средняя** (FR в spec.md): no `POST_NOTIFICATIONS` request — data-only FCM messages (CHK015).
4. **Средняя** (одна правка в spec.md): local-mode behavior — F-5c disabled when no Sign-In; `NullPushTrigger` no-op в DI (CHK020, dup с modular-delivery CHK009).
5. **Низкая** (для tasks.md): update `docs/compliance/permissions-and-resource-budget.md` (CHK018).
6. **Низкая** (для S-1 spec wizard tasks): privacy disclosure update — FCM token + event metadata transmission (CHK019, CHK021).

---

## Заметка для новичка (TL;DR)

Проверено: правильно ли сделана безопасность и приватность. Не передаём ли мы пароли в открытом виде, не пишем ли в логи имена пользователей, не запрашиваем ли permissions «на всякий случай».

**Хорошо сделано** (11/24):
- Всё передаётся через HTTPS.
- Содержимое конфига **не** летит в push — только указатель «обнови данные с сервера».
- Все авторизации проверяются по Firebase JWT, не по «секретному URL» (которого нет).
- Никаких новых local-хранилищ → backup rules не меняем.

**Чего не хватает** (7 «частично»):
- Не записано как должны выглядеть логи (не должны содержать полный UID, FCM token, payload).
- Не записано `exported="false"` для FCM сервиса (Android 12+ требует явно).
- Не записано «POST_NOTIFICATIONS не нужен» (data-only FCM не требует, важно не запросить лишнего).
- Не записано как F-5c ведёт себя в local mode (без Sign-In) — должен быть выключен.
- Privacy policy disclosure (что FCM token публикуется в Firebase) — нужно сделать в S-1 wizard'е.

Это **не блокирует** /speckit.plan. Закрывается 3-4 новыми FRs в spec.md + 2 операционными tasks (update permissions doc + privacy policy hook).

# Permissions and Resource Budget

## Цель
Для каждой значимой фичи фиксировать цену этой фичи в терминах:
- permissions,
- battery,
- memory,
- storage,
- network.

## Шаблон записи
### Feature
### Requested permissions
### Why each permission is needed
### Fallback if denied
### Background/runtime impact
### Startup impact
### Memory/storage impact
### Network impact
### Monitoring/observability
### Decision

## Проектный принцип
Чем меньше разрешений и ресурсов требует продукт, тем лучше для:
- store viability,
- пользовательского доверия,
- стабильности,
- продвижения,
- поддержки.

## Обязательное правило
Ни одна значимая фича не должна попадать в реализацию без явного resource-budget review.

---

## Зарегистрированные фичи

### spec 005: action-architecture-v2

- **Feature**: provider-agnostic dispatcher (8 handlers: app launch, WhatsApp, Telegram, phone, SMS, browser, YouTube, system settings) + `AndroidProviderRegistry` + `AddSlotWizard` provider filtering.
- **Requested permissions**: ноль новых runtime permissions. Phone использует `ACTION_DIAL` (диалер открывается, звонок не запускается автоматически — `CALL_PHONE` не нужен). SMS — `ACTION_SENDTO` со схемой `smsto:`. Browser — `ACTION_VIEW` для http/https.
- **Why each permission is needed**: n/a (ничего нового).
- **Fallback if denied**: n/a.
- **Manifest deltas**: добавлены `<queries>` для известных целевых пакетов (`com.whatsapp`, `com.whatsapp.w4b`, `org.telegram.messenger`, `org.telegram.plus`, `com.google.android.youtube`) + `<intent>`-queries для схем `tel:`, `smsto:`, `https:`. **Не запрашивается `QUERY_ALL_PACKAGES`** (плохой сигнал для Play). См. `app/src/main/AndroidManifest.xml`.
- **Background/runtime impact**: ноль фоновых задач, нет broadcast receiver'ов, нет AlarmManager / WorkManager. Dispatch синхронный, p95 ≤ 50 мс (T641 micromeasurement).
- **Startup impact**: cold start budget ≤ 600 мс (HomeActivity) / ≤ 700 мс (FirstLaunch). Все 8 handler'ов конструируются eagerly в `LauncherCore.init` (handlers stateless; cost платится один раз при cold start, не per-tap). См. T640 perf-checkpoint.
- **Memory/storage impact**: APK delta ≈ 0 KB (новый код помещается в существующий `:core` модуль). Asset growth: `mock_contacts.json` (~400 байт), переписаны `flows_mock_*.json` (без роста). `whatsapp_tiles_mock.json` удалён.
- **Network impact**: ноль. Dispatcher и provider registry полностью оффлайн. Все intent'ы запускают системные приложения, не делают сетевых запросов сами.
- **Monitoring/observability**: `ProjectEvent.ActionDispatched(providerId, resultKind, fallbackUsed, timestampMs)` — 4 поля, без PII. Контракт фиксирован, изменения требуют Article XIV §3 review (см. `EventTaxonomyTest`).
- **Decision**: ✅ принято. Бюджет полностью соблюдён.

### spec 006: provider-capabilities-and-health

- **Feature**: per-provider [`Capability`](../../core/src/commonMain/kotlin/com/launcher/api/capability/Capability.kt) snapshot (displayName, iconId, available, versionCode), per-device [`Health`](../../core/src/commonMain/kotlin/com/launcher/api/health/Health.kt) snapshot (battery, charging, connectivity, ringer volume, mute, lastSeen, appVersion), 2 in-launcher banner alerts (Airplane Mode On / Sound Off), settings DataStore с user toggles. Cleanup спека 005 `migrateLegacyAction` bridge.
- **Requested permissions**: одно нормальное — `ACCESS_NETWORK_STATE` (auto-granted, нет runtime prompt). **Ноль новых dangerous/runtime permissions** (NFR-008). Явно НЕ запрашиваются: `ACCESS_NOTIFICATION_POLICY` (DND не обходится), `CALL_PHONE`, `SEND_SMS`, `MODIFY_AUDIO_SETTINGS` (для FR-027 «Включить звук» достаточно `AudioManager.setStreamVolume` без permission), `QUERY_ALL_PACKAGES` (Play policy), `POST_NOTIFICATIONS` (банеры в-приложении, не системные нотификации).
- **Why each permission is needed**: `ACCESS_NETWORK_STATE` нужен для `ConnectivityManager.NetworkCallback` чтобы детектировать Wifi/Mobile/None и обновлять `Health.connectivity` (FR-018).
- **Fallback if denied**: n/a — normal permission auto-granted, denial невозможен.
- **Manifest deltas**:
  - Добавлен `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`.
  - `<queries>` блок **не изменён** относительно спека 005 — все целевые пакеты уже там (whatsapp, telegram, youtube + alternates).
  - Добавлены `android:fullBackupContent="@xml/backup_rules"` и `android:dataExtractionRules="@xml/data_extraction_rules"` в `<application>`.
  - Новые ресурсы: [`app/src/main/res/xml/backup_rules.xml`](../../app/src/main/res/xml/backup_rules.xml) + [`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml) — exclude capability/health snapshots (per-device transient), include settings (user preferences).
- **Background/runtime impact**: **ноль фоновых задач** (NFR-N03/N05). НЕТ Foreground Service, НЕТ WorkManager (вынесено в спек 013), НЕТ AlarmManager, НЕТ polling. Все обновления event-driven через 5 listeners: `ProcessLifecycleOwner.RESUMED`, `ConnectivityManager.NetworkCallback`, `Settings.System` ContentObserver (volume), `Settings.Global.AIRPLANE_MODE_ON` ContentObserver, `ACTION_BATTERY_CHANGED` sticky broadcast. Все `onReceive`/`onChange` ≤ 10 ms (NFR-012); rebuild на `Dispatchers.Default`. Battery budget ≤ 0.1%/day (NFR-005).
- **Startup impact**: cold start contribution ≤ 20 ms (NFR-004). DataStore reads lazy — не блокируют main thread. DI wiring ≤ 5 ms, listener registration ≤ 10 ms.
- **Memory/storage impact**: 3 новых DataStore Preferences файла под `files/datastore/` — capability snapshot (~2 KB JSON), health snapshot (~1 KB), settings (~200 байт). In-memory snapshot footprint ≤ 5 KB (NFR-006). APK delta ≤ 100 KB (8 placeholder vector drawables ~12 KB each, NFR-009).
- **Network impact**: **ноль** (NFR-N01, NFR-010). Никаких network calls, никаких persistent connections. `ACCESS_NETWORK_STATE` используется только для observation, не для I/O.
- **Monitoring/observability**: [`RecoveryEventLogger`](../../core/src/androidMain/kotlin/com/launcher/core/diagnostics/RecoveryEventLogger.kt) — structured log events с категориями (`corruption`, `missing_resource`, `unknown_namespace`, `system_api_failure`, `user_action_failed`), **zero PII** (FR-052). В спеке 006 пишет через `android.util.Log.i` (tag `LauncherRecovery`); в спеке 007 заменится на Firebase telemetry без изменения call sites.
- **Privacy classification**: Capability/Health/LauncherSettings — **non-PII device telemetry** (FR-056). Нет имён, телефонов, email, contact refs, location, biometric, account identifiers. App-private DataStore + Android FBE encryption по умолчанию. В спеке 007 при cloud export — privacy review для admin transparency / consent.
- **Decision**: ✅ принято. Бюджет полностью соблюдён, никаких новых dangerous permissions, никаких background services, battery-friendly event-driven design.

### spec 007: pairing-and-firebase-channel **(finalized 2026-05-11 at code-complete)**

Cross-checked against shipped manifests:
[app/src/main/AndroidManifest.xml](../../app/src/main/AndroidManifest.xml) +
[app/src/realBackend/AndroidManifest.xml](../../app/src/realBackend/AndroidManifest.xml).

- **Feature**: Pairing FSM (QR + Firestore transaction), `RemoteSyncBackend` adapter (Firebase Firestore + Auth), Cloudflare Worker push-relay, FCM data-message receiver, Firestore Security Rules. End-to-end push consumer wired in `LauncherFirebaseMessagingService`.
- **Permissions actually declared in shipped manifests**:
  - `ACCESS_NETWORK_STATE` (normal, auto-granted) — inherited from spec 006, в `app/src/main/AndroidManifest.xml`.
  - `INTERNET` (normal, auto-granted) — **realBackend flavor only**, в `app/src/realBackend/AndroidManifest.xml`. `mockBackend` flavor работает offline — никаких сетевых походов.
  - **НЕ добавлены и НЕ требовались**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (per C7), `READ_CONTACTS` (declared в спеке 009 для Picker; крипто-фундамент спека 011 не trigger'ит; реальное наполнение фото — спек 012), `SEND_SMS`, `CALL_PHONE` (legacy numbering — отложено на future balance-alerts / offline-detection specs; 017/018 после перенумерации 2026-05-22, pre-2026-06-18 reassignment когда spec 017 перешёл на F-4 AuthProvider), `QUERY_ALL_PACKAGES` (запрещено Play policy).
- **Permissions originally planned but NOT shipped in 007** (moved):
  - `POST_NOTIFICATIONS` (runtime, Android 13+) — **не нужно**, FCM SDK 23.x в Firebase BoM 33 не требует declared `POST_NOTIFICATIONS` для silent (data-only) push (per FR-016). Будет добавлено в спек 008 если появятся notification-tray уведомления.
  - `CAMERA` (runtime, dangerous) — **deferred to spec 008**: admin QR scanner (T089) не реализован в 007 (Managed-side UI only — `QrDisplayScreen` показывает QR, не сканирует). Manual token entry — fallback на стороне admin вне scope 007.
  - `<uses-feature android:hardware.camera` — deferred вместе с CAMERA.
- **Why each shipped permission is needed**:
  - `INTERNET` — Firestore + FCM + Cloudflare Worker HTTPS.
  - `ACCESS_NETWORK_STATE` — `ConnectivityManager` (inherited), GMS availability check (FR-018 stub).
- **Fallback if denied**:
  - `INTERNET`/`ACCESS_NETWORK_STATE` — normal permissions, denial невозможен.
- **Manifest deltas vs спек 006**:
  - `app/src/main/AndroidManifest.xml`: `<activity android:name=".ui.pairing.PairingActivity" exported="true" label="@string/pairing_toggle_title"/>` — adb-launchable, без MAIN/LAUNCHER (per debug activity convention из спека 006).
  - `app/src/realBackend/AndroidManifest.xml`: `<uses-permission android:name="android.permission.INTERNET"/>` + `<service android:name="com.launcher.adapters.push.LauncherFirebaseMessagingService" exported="false">` с intent-filter `com.google.firebase.MESSAGING_EVENT`.
  - **БЕЗ** Google Services Gradle plugin (per FR-034 — план `apply` был отменён, Firebase ходит через `FirebaseApp.initializeApp(context)` напрямую с baked-in `google-services.json`). См. `core/src/androidRealBackend/kotlin/com/launcher/di/BackendInit.kt`.
- **Background/runtime impact**:
  - `FirebaseMessagingService` — managed by Android system; запускается **только** при поступлении FCM message. Не foreground service. Per-message handling ≤ 200ms typical (FR-037 — read /config + log).
  - **НЕТ WorkManager polling** в спеке 007 (per C13 stub).
  - Firestore SDK поддерживает persistent connection во foreground; auto-disconnect в background per SDK default behavior.
- **Startup impact**:
  - +50ms на lazy Firebase Auth anonymous sign-in (per FR-002 + SC-007).
  - Cold start `HomeActivity` ≤650ms (vs baseline 600ms из спека 006). Замеряется в Phase 11 T105.
  - `realBackend` flavor only; `mockBackend` без Firebase init.
- **Memory/storage impact**:
  - DataStore `com.launcher.pairing.identity_v1` ~200 байт.
  - Firestore SDK in-memory cache ~5-10 MB при активной сессии.
  - APK delta **measured 2026-05-11**: `realBackend` vs `mockBackend` = +3.99 MB SI (12.98 vs 8.99 MB unminified release). **Fails SC-006 ≤3 MB target by ~0.99 MB**; root cause R8 not enabled on `release`. Exit ramp tracked as `TODO-ARCH-006` in `docs/dev/project-backlog.md` — enabling R8 typically delivers 40-60% APK reduction and lands well under target. Включает: firebase-firestore-ktx (largest), firebase-auth-ktx, firebase-messaging-ktx. ZXing уже считается в `mockBackend` baseline (для Managed QR display). CameraX + MLKit deferred to spec 008.
- **Network impact**:
  - Firestore — bidirectional long-lived connection во foreground; ~1-5 KB/min idle (heartbeats).
  - FCM — managed by Android System (не наш cost).
  - Cloudflare Worker — POST `/notify` ≤1 KB body, response ≤1 KB. Только admin-side (managed device не дёргает Worker напрямую).
  - **No polling** (per C13).
- **Monitoring/observability**:
  - `Log.i("LauncherPairing", ...)` для pairing state transitions (FR-007, FR-008, FR-009).
  - `Log.i("LauncherPush", ...)` для FCM receive events (FR-037).
  - Firebase Console показывает FCM delivery success rate + Firestore operation count (free dashboard).
  - Cloudflare dashboard показывает Worker requests/errors/p95 latency.
  - **Zero PII в логах** — UID/fcmToken не логируются (FR-052 inherited).
- **Privacy classification** (final — see `docs/compliance/country-legal-tax-register.md` §spec 007 for the cross-checked table):
  - `managedDeviceId` (UUIDv4) — pseudonym, DataStore + Firestore `/links/{linkId}.managedDeviceId`.
  - `managedDeviceFirebaseUid` (anonymous Firebase Auth UID) — pseudonym.
  - `adminId` (anonymous Firebase Auth UID) — pseudonym.
  - `linkId` — opaque 16-char alphanumeric, pseudonym.
  - `PairingToken` — 5-min ephemeral pseudonym.
  - FCM topic `link-{linkId}` — opaque, in Google's FCM server.
  - **Никаких PII в strict sense**: нет email, phone, name, address, location, biometric.
  - `fcmToken` — **NOT persisted by 007 code today** (in-memory diagnostic only via `FcmRegistration.currentFcmToken`); will become `/links/{linkId}/state.fcmToken` in spec 008 (`LauncherFirebaseMessagingService.onNewToken` TODO).
  - Хранение в облаке: Firestore (Google), Cloudflare Worker Secrets (Cloudflare). Оба US/global multi-region; data processing agreements автоматические для бесплатного tier.
- **Decision**: ✅ принято as finalized. Permissions минимальны (только INTERNET в realBackend + ACCESS_NETWORK_STATE inherited), нет dangerous permissions для managed device, no background services, no polling. Pre-production checklist (still open): `TODO-OPS-001/002` (2FA), `TODO-OPS-003` (key rotation closed 2026-05-11 per commit `c2b8490`), `TODO-ARCH-006` (R8 minification for SC-006 fix).

### spec 008: bidirectional-config-sync **(planned 2026-05-14, in implementation)**

- **Feature**: Collaborative-editing wire format `/links/{linkId}/config/current` с optimistic concurrency, unified diff/merge UI, `/links/{linkId}/state/current` extension. Local persistence через SQLDelight (KMP-friendly). 4 refresh triggers: FCM `config.updated`, `ConnectivityManager.NetworkCallback`, WorkManager periodic 15min, Activity#onResume throttled 2min.
- **Permissions added**: **none new**. Reuses INTERNET + ACCESS_NETWORK_STATE из спека 007.
  - `ACCESS_NETWORK_STATE` теперь активно используется через `ConnectivityManager.NetworkCallback` (FR-022 T2) — не только GMS detection как в 007.
  - `INTERNET` — Firestore writes /config/current, Worker push, FCM receive (все inherited).
- **Permissions NOT added** (deferred to future specs):
  - `READ_CONTACTS` — declared в спеке 009 (Picker); real-use наполнение фото — спек 012 (uses crypto foundation спека 011).
  - `READ_PHONE_STATE`, `CALL_PHONE`, `SEND_SMS` — specs 017/018 (после перенумерации 2026-05-22).
- **Why each shipped permission is needed** (no change from 007):
  - `INTERNET` — Firestore /config writes + reads, FCM, Worker push trigger.
  - `ACCESS_NETWORK_STATE` — ConnectivityManager events для FR-022 T2 (T3 WorkManager fallback при отсутствии).
- **Fallback if denied**: normal permissions, denial невозможен.
- **Manifest deltas vs спек 007**: **none expected** (no new declared permissions). May add WorkManager init metadata в `AndroidManifest.xml` (default WorkManager initialization).
- **Background/runtime impact**:
  - **NEW**: WorkManager periodic 15 min — config refresh fallback (FR-022 T3). ≤96 wakeups/day, <0.1% battery per Article IX §3 cap.
  - **NEW**: `ConnectivityManager.NetworkCallback` — system-driven event, ~0 cost (FR-022 T2).
  - **NEW**: `ProcessLifecycleOwner` ON_RESUME callback (throttled 2 min) — user-bound, no background cost (FR-022 T4).
  - **NEW**: SQLDelight Android SQLite driver — disk I/O on autosave (debounced 300ms per FR-056); typical session 10-50 writes.
  - FCM `config.updated` — managed by Android system (inherited from 007).
- **Startup impact**:
  - **NEW**: cold start с SQLDelight read для last-applied-config — target ≤ 50 ms p95 (subset of SC-004a общий budget ≤ 650 ms).
  - Application.onCreate — lazy `SqlDriver` init (no DB open).
  - 5-second post-startup `/config/current` fetch (SC-004b).
- **Memory/storage impact**:
  - **NEW**: SQLDelight DB `config_sync.db` — 2 tables (applied_config + pending_changes), один row на linkId; estimated <100 KiB local.
  - APK delta **estimated**: SQLDelight runtime + android-driver = ~200-400 KiB on top of спек 007 baseline (3.99 MiB delta). With R8 (TODO-ARCH-006) — should stay under 4 MiB delta. Final measurement в Phase 12 T142.
  - **Mandatory**: `android:allowBackup="false"` или `data_extraction_rules.xml` исключающий `config_sync.db` (PII protection — security checklist CHK024). Action item в Phase 3 T050.
- **Network impact**:
  - Firestore writes к `/config/current` — per push action (user-initiated). ≤1 KB per write при типичной раскладке.
  - Firestore reads — на каждый из 4 триггеров (T1 FCM, T2 NetworkCallback, T3 WorkManager 15min, T4 RESUMED throttled).
  - Net new: <100 KB/day typical usage.
- **Monitoring/observability**:
  - `Log.i("ConfigSync", ...)` для apply / push / conflict events (categorical, NO PII per security CHK004).
  - `ConfigSyncError` sealed class — categorical errors enable rate measurement (failure-recovery CHK017).
  - **Zero PII в логах**: никогда не логировать `contacts[]`, `flows[].slots[].args` целиком; только counts/categories.
- **Privacy classification**:
  - `/config.contacts[]` содержит phone numbers — это PII per Article XIV. Хранение: Firestore (Google) + SQLDelight local. Защита базы: Android app sandbox (не SQLCipher в 008 — Option A per research.md §security CHK001; revisit в спеке 011 e2e-media).
  - `/config.lastWriterDeviceId` — pseudonym, идентифицирует which editor wrote last. **Только в /config**, НЕ в /state (security CHK019: prevent voyeurism).
  - `/state.appliedConfigUpdatedAt` — timestamp, не PII.
  - SQLDelight DB **deleted on uninstall** (Android default) + явно на revoke link (FR-034 + new clearLocalForLink action).
- **Decision**: 🟡 planned. To finalize when 008 phases 4 (Firebase adapters) + 7 (lifecycle triggers) реализованы. Pre-production checklist: TODO-ARCH-006 (R8) перед или в Phase 12, TODO-ARCH-007 (app-version-compatibility — отдельный спек), TODO-ARCH-008 (config history+rollback в спек 009), TODO-ARCH-009 (size soft-limits, optional safety net).

### spec 009: admin-mode-flows

- **Feature**: Admin-mode UI (editor, history+rollback, phone health monitoring, contacts management via picker + VCard share intent, OpenApp tiles). Расширяет «мотор» спека 008 (config sync) UI слоем редактирования; добавляет subcollection `/config/history/*` для rollback; добавляет VCard share intent receiver.
- **Permissions added**: **`READ_CONTACTS`** (dangerous, runtime — Android 6+). **Admin-only flavour** — Managed-устройство НЕ требует и НЕ запрашивает этого permission.
- **Why each permission is needed**:
  - `READ_CONTACTS` — нужен **только** для `ACTION_PICK` с `ContactsContract` URI (FR-024). Без permission picker возвращает empty cursor на части OEM (Samsung One UI, Xiaomi MIUI). Запрашивается **лениво** при первом нажатии «+ контакт» в editor (FR-023), с rationale-экраном объясняющим зачем.
- **Fallback if denied**:
  - FR-023a — ручной ввод имени + телефона через `ManualContactEntryForm` (всегда доступен, не требует permission).
  - FR-023b — после «Don't ask again» rationale показывает deep-link на System Settings (`ACTION_APPLICATION_DETAILS_SETTINGS`) с инструкцией.
  - FR-027 — VCard share intent (`ACTION_SEND` + `text/x-vcard`) — НЕ требует `READ_CONTACTS` (адресатор VCard'а — другое приложение, user-initiated).
- **Manifest deltas**:
  - `<uses-permission android:name="android.permission.READ_CONTACTS" />` — добавляется в admin flavour manifest (если будет flavour split) или в основной manifest с TODO-flavour-split note. В спеке 9 — основной manifest (flavour split = follow-up).
  - `<intent-filter>` на `VCardReceiveActivity` для `ACTION_SEND` + MIME `text/x-vcard`, `launchMode="singleTask"` (FR-027a).
  - `<queries>` block расширяется generic `<intent>` тегами: MAIN/LAUNCHER (для `InstalledAppsCatalog` enumeration FR-034) + VIEW/scheme=market (для Play Store fallback FR-035a). Конкретные `<package>` теги per-app НЕ нужны (generic queries — FR-035a).
  - `<application android:dataExtractionRules="@xml/data_extraction_rules">` — backup exclusion для contacts DB (FR-046a, GDPR transfer-to-processor mitigation).
- **Background/runtime impact**:
  - **Zero new background work** в спеке 9. Phone health monitoring — Firestore listener-only when admin screen is open (FR-020); закрывается на `onStop()`. Никаких WorkManager / AlarmManager / BroadcastReceiver feature-modules.
  - VCard intent receiver — explicit user-initiated через системный share sheet, не background.
  - Continuous autosave editor draft (FR-014b) — Room local writes на main app process; debounced; нет background процесса.
- **Startup impact**:
  - **Zero cold-start delta** для Managed (нет нового кода на Managed flow).
  - Admin cold-start delta — lazy DI init for `SystemContactPicker` / `VCardImporter` / `InstalledAppsCatalog` (≤ 5 ms target).
- **Memory/storage impact**:
  - APK delta estimated < 100 KiB (Compose components extension + pure-Kotlin VCard parser ~100 LOC, no new gradle deps per plan.md §5).
  - Firestore storage: `/links/{linkId}/config/history/{autoId}` — max 10 snapshots × ~5 KB = ~50 KB per link (FR-038 housekeeping cap).
  - Room contacts cache (если будет добавлен в спек 009; в спеке 9 contacts хранятся внутри `/config/current.contacts[]`, отдельной Room таблицы НЕТ).
- **Network impact**:
  - Phone health listener — inherits спек 008 budget (4 triggers).
  - History writes/reads — per admin publish (≤ 1 KB write) + history-screen open (≤ 50 KB read).
  - Net new vs спек 008: < 100 KB / admin session typical.
- **Privacy classification**:
  - Contacts через `READ_CONTACTS` — **PII третьих лиц** (Маша). Critical: Android Auto Backup MUST exclude contacts DB per FR-046a — `data_extraction_rules.xml` exclude rule. 🚨 **TODO-LEGAL-001 PLAY-STORE-BLOCKER**: Data Safety form Play Store + Privacy Policy update до первого upload.
  - `recordedFromDeviceId` в `/config/history/*` — pseudonym (auth uid), server-enforced anti-spoof via Security Rule (FR-045a).
  - Минимум privacy в спеке 9: FR-031a `ContactsManageScreen` (list + delete only), FR-031b/c — deferred TODO-LEGAL-001.
- **Decision**: 🟡 planned. Pre-Play-Store gates: TODO-LEGAL-001 closed (privacy policy + Data Safety form), TODO-ARCH-006 R8 done. Spec 9 PR ships без публикации в Play Store — internal smoke + manual OEM matrix (Samsung One UI / Xiaomi MIUI / Pixel) перед merge.

### spec 010: setup-assistant-and-launcher-bootstrap **(in implementation, branch `010-setup-assistant`)**

- **Feature**: setup wizard extension (ROLE_HOME + POST_NOTIFICATIONS steps with senior-safe progress indicator), GMS hard-block screen for ungoogled devices, call confirmation dialog (one-tap CALL_PHONE path), Settings soft-checks engine (5 SetupChecks + `!N` / `?M` badges), paired-devices section + local-first revocation, 7-tap challenge gate for admin entry. Closes `TODO-ARCH-016` (HomeScreen reads from `/config/current`, mock data deleted).
- **Permissions added (vs spec 009)**:
  - **`CALL_PHONE`** (dangerous, runtime — Android 6+) — FR-012 / FR-013. Replaces dialer two-tap flow with one-tap CALL after user confirms in the senior-safe dialog. Requested lazily at first call-tile tap with rationale «Чтобы звонок шёл сразу одной кнопкой». On deny → `PhoneHandler` falls back to `ACTION_DIAL` (no functional regression vs spec 005).
  - **`POST_NOTIFICATIONS`** (runtime, Android 13+) — FR-008. Used by future status-update notifications visible to the admin («внук видит, что у тебя всё в порядке»). Requested in the wizard with senior-safe rationale; skipped automatically on API < 33. Recommended-criticality SetupCheck, not Required — denial does not break core launcher.
- **Manifest deltas vs spec 009**:
  - `<uses-permission android:name="android.permission.CALL_PHONE" />` (T001) — main manifest, applies to both flavors. Telephony-less devices (tablets, Wear) handle gracefully via `<uses-feature ... required="false">`.
  - `<uses-feature android:name="android.hardware.telephony" android:required="false" />` (T002) — keeps the app installable on non-phone hardware (CHK-permissions-007).
  - `<queries><intent><action android:name="android.intent.action.CALL"/><data android:scheme="tel"/></intent></queries>` (T003 — CRITICAL CHK-permissions-008/020) — without this, `packageManager.queryIntentActivities(ACTION_CALL)` returns empty on Android 11+ even with `CALL_PHONE` granted, breaking FR-012 / FR-014.
  - `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` — added in Phase 3 (T044 wizard step); skipped at runtime on API < 33.
  - `<activity android:name=".setup.GmsHardBlockActivity" android:exported="false" />` and new setup/gate/call/paired Activities — all `android:exported="false"` (plan §11 C-9, enforced by [Spec010IsolationTest.T010](../../core/src/androidUnitTest/kotlin/com/launcher/test/fitness/Spec010IsolationTest.kt)).
- **Role declared (not a permission)**: `RoleManager.ROLE_HOME` — request flow on Android 10+ via `createRequestRoleIntent(ROLE_HOME)` (FR-007); on Android 8-9 (API 26-28) legacy fallback via `Intent.CATEGORY_HOME` chooser (plan §11 C-6 — inline TODO TODO-PLATFORM-001 for API ≥ 29 cleanup once minSdk bumps).
- **Why each new permission is needed / fallback if denied**:
  - `CALL_PHONE` — enables `Intent(ACTION_CALL, ...)` for one-tap dialing per FR-012. Fallback (denied / not granted yet): `Intent(ACTION_DIAL, ...)` keeps the dialer-confirmation two-tap path of spec 005 — no broken feature, only one extra tap.
  - `POST_NOTIFICATIONS` — needed for system tray notifications. **После перенумерации 2026-05-22**: deferred to spec 017 (balance-check) или 018 (offline-detection) (legacy numbering pre-2026-06-18 reassignment; spec 017 номер был переназначен на F-4 AuthProvider 2026-06-18, см. ADR-008 numbering note — balance-check / offline-detection остаются TBD future specs). Спек 011 (e2e-crypto-foundation) **не** активирует POST_NOTIFICATIONS — никаких system-tray notifications в крипто-фундаменте. Fallback (denied / API < 33): zero impact in spec 010 — Settings shows `?M` recommended badge.
- **Background/runtime impact**:
  - **`UnlinkCleanupWorker`** (FR-032a) — new one-time `WorkManager` request with `NetworkType.CONNECTED` constraint, enqueued only on user-confirmed unlink. ≤ 1 wakeup per unlink action; idempotent retry on failure via exponential backoff. **Zero polling, zero periodic scheduling.**
  - 7-tap detector — pure-UI gesture; no background.
  - Setup-check engine — runs on cold-start (`LaunchedEffect`) + on Settings screen `RESUMED`; **no background polling** (FR-020a explicit constraint).
- **Startup impact**:
  - GMS availability check on cold start — `GoogleApiAvailability.isGooglePlayServicesAvailable()` is sub-millisecond on warm devices; budgeted ≤ 5 ms p95 inside the `Dispatchers.IO` wrap (T006).
  - Wizard cold start unchanged from baseline; SC-002 target `HomeScreen` first-frame ≤ 1 sec on Pixel 4a class (verified in T107 macrobenchmark).
  - `play-services-base` adds ~80-100 KB to `androidMain` baseline (now in both flavors) — kept tiny, no transitive Firebase.
- **Memory/storage impact**:
  - **`LocalLinkRevocationStore`** (FR-032 / T081) — new DataStore Preferences file, < 1 KB per linkId. Persists across process death so revoked links stay revoked offline.
  - APK delta budget: ≤ +500 KB vs spec 009 release build (SC-009 — verified in T108).
  - `play-services-base`: ~80-100 KB classes.
- **Network impact**:
  - **Zero new network work**. `UnlinkCleanupWorker` reuses spec 7 `LinkRegistry.deactivate()` (a Firestore write — inherited budget). FR-032a path (b) offline → WorkManager queues without retry storms (CONNECTED constraint guarantees no wake-on-no-network).
  - GMS check is local (queries Google Play Store package on-device).
- **Monitoring/observability**:
  - New diagnostic event `setupCheckException(checkId, reason)` (T073) — categorical, NO PII; emitted when a `SetupCheck.check()` throws (e.g. Xiaomi MIUI battery-optimization `SecurityException`). Enables R5 OEM-quirk rate measurement without exposing user state.
  - 7-tap / challenge-gate events stay local; never logged with timestamps (CHK-security-004 — no behavioral fingerprint).
- **Privacy classification**:
  - **No new PII** introduced by spec 010. Setup-check results are categorical (`Ok` / `NotConfigured(reason)`) and never include user identifiers.
  - `LocalLinkRevocationStore` stores only `linkId` (pseudonym from spec 7 — already classified there).
  - Call-confirmation dialog renders contact name + phone formatted in-RAM only; never persisted by spec 010 code (data sourced live from `/config/current.contacts[]`).
- **Decision**: 🟡 in implementation. Pre-merge gates:
  - T108 APK delta ≤ +500 KB.
  - T107 macrobenchmark cold-start ≤ 1 sec p95.
  - T106 OEM matrix smoke (Samsung One UI / Xiaomi MIUI / Pixel) — verify FR-020b exception path on Xiaomi.
  - All 13 checklists re-run clean at `/speckit.analyze` Phase 8.

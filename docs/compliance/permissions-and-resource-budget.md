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
  - **НЕ добавлены и НЕ требовались**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (per C7), `READ_CONTACTS` (это спек 011), `SEND_SMS`, `CALL_PHONE` (это спеки 012/013), `QUERY_ALL_PACKAGES` (запрещено Play policy).
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
  - `READ_CONTACTS` — spec 011 (contacts + e2e media).
  - `READ_PHONE_STATE`, `CALL_PHONE`, `SEND_SMS` — specs 012/013.
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

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

### spec 007: pairing-and-firebase-channel

- **Feature**: Pairing protocol (QR + Firestore transaction), `RemoteSyncBackend` adapter (Firebase Firestore + Auth), Cloudflare Worker push-relay, FCM data-message receiver, Firestore Security Rules. Plus end-to-end push consumer (admin write `/config` → Worker → FCM → managed reads).
- **Requested permissions**:
  - `INTERNET` (normal, auto-granted) — Firestore + FCM + Cloudflare Worker HTTPS.
  - `ACCESS_NETWORK_STATE` (normal, auto-granted) — уже добавлено в спеке 006; ре-используется для GMS detection.
  - `POST_NOTIFICATIONS` (runtime, Android 13+) — declared в manifest для FCM SDK совместимости; **runtime не запрашиваем** (silent push, FR-016).
  - `CAMERA` (runtime, dangerous) — только для admin-mode QR scanner (FR-005). Запрашивается в runtime при первом open QrScannerScreen.
  - **НЕ добавлены**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (per C7), `READ_CONTACTS` (это спек 011), `SEND_SMS`, `CALL_PHONE` (это спеки 012/013), `QUERY_ALL_PACKAGES` (запрещено Play policy).
- **Why each permission is needed**:
  - `INTERNET` — все Firebase/Cloudflare операции.
  - `ACCESS_NETWORK_STATE` — `ConnectivityManager` для определения наличия сети (для UI «Нет подключения») + GMS availability check (FR-018 stub).
  - `POST_NOTIFICATIONS` — Android 13+ требует declared для любого FCM SDK init; silent push не запрашивает runtime grant, но manifest declared нужен.
  - `CAMERA` — admin QR scanner через CameraX + ML Kit Barcode (FR-005).
- **Fallback if denied**:
  - `CAMERA` denied → fallback на manual token entry (текстовое поле в admin mode).
  - `POST_NOTIFICATIONS` denied → silent push продолжает работать (data-only, не визуальный).
  - `INTERNET`/`ACCESS_NETWORK_STATE` — normal, denial невозможен.
- **Manifest deltas** (по сравнению со спеком 006):
  - `<uses-permission android:name="android.permission.INTERNET" />`.
  - `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` (Android 13+ guarded).
  - `<uses-permission android:name="android.permission.CAMERA" />`.
  - `<uses-feature android:name="android.hardware.camera" android:required="false" />` (admin scanner — не критично для managed-only устройств).
  - `<service>` entry для `LauncherFirebaseMessagingService` с intent-filter `com.google.firebase.MESSAGING_EVENT`.
  - Google Services Gradle plugin apply (per FR-034 — только в `realBackend` flavor source-set).
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
  - APK delta: `realBackend` vs `mockBackend` ≤ +3 MB (SC-006, T108 measurement). Включает: firebase-firestore-ktx, firebase-auth-ktx, firebase-messaging-ktx. ZXing + CameraX + MLKit Barcode уже считаются в `mockBackend` baseline (для in-process pairing).
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
- **Privacy classification** (для country-legal-tax-register update):
  - `managedDeviceId` (UUIDv4) — pseudonym, persistent per device.
  - Firebase Auth UID — pseudonym, generated by Firebase per device.
  - `fcmToken` — pseudonym, rotated by Google.
  - `adminId` — pseudonym (Firebase Auth UID admin-устройства).
  - **Никаких PII в strict sense**: нет email, phone, name, address, location, biometric.
  - Хранение в облаке: Firestore (Google), Cloudflare Worker Secrets (Cloudflare). Оба US/global multi-region; data processing agreements автоматические для бесплатного tier.
- **Decision**: ✅ принято. Permissions минимальны (только runtime CAMERA в admin-mode), нет dangerous permissions для managed device, no background services, no polling. Pre-production checklist: TODO-OPS-001/002 (2FA), TODO-OPS-003 (key rotation).

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

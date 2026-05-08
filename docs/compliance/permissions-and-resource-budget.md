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

# Дорожная карта проекта launcher

Источник: восстановленный транскрипт `.recovered-session-2026-05-05-clean.md`.
Последнее обновление: 2026-05-06.

---

## Сводная таблица

| # | Spec slug | Статус | Зависит от |
|---|---|---|---|
| 003 | `ui-skeleton` | Частично выполнен (5 коммитов, T072 не выполнен) | — |
| 004 | `action-architecture-v2` | Не начат | 003 |
| 005 | `provider-capabilities-and-health` | Не начат | 004 |
| 006 | `pairing-and-firebase-channel` | Не начат | 005 |
| 007 | `bidirectional-config-sync` | Не начат | 006 |
| 008 | `admin-mode-flows` | Не начат | 007 |
| 009 | `setup-assistant-soft-checks` | Не начат | 005 (параллельно 006/007) |
| backlog | `config-portability` | Backlog stub | 007 |

---

## Детальное описание каждой spec

### Spec 003 — `ui-skeleton`

**Phase 9 (расширение):** добавлен first-launch preset picker. Три универсальных пресета — `workspace`, `launcher`, `simple-launcher`. Выбор сохраняется в DataStore, переключается через Settings, debug-обход через intent extra `--es preset <slug>`. См. `tasks.md` Phase 9.

**Статус:** частично выполнен. 5 коммитов на ветке `003-ui-skeleton`:
- `65a957e` — docs: add spec 003 ui-skeleton
- `32ee98d` — feat(core): add flow/slot domain models and mock repository
- `8be5392` — feat(app): implement ui skeleton with bottom-nav flow architecture
- `906b279` — fix(test): add missing @RunWith and remove migrated HomeActivity tests
- `64fbe9a` — chore(specs): finalize 003-ui-skeleton tasks checkpoint

**T072 (smoke-check на эмуляторе) — не выполнен.** Требует ручного прохода.

**Что делает:**
- Доменные модели в `:core/api`: `Flow`, `Slot`, `TileConfig` (sealed), `Preset`, `PresetRequirement`, `LanguagePreference`.
- `RemoteSyncBackend` интерфейс в `:core/api/sync` (без реализации).
- `HomeActivity` → NavigationHost с горизонтальным `BottomFlowBar` (вкладки из `flows_mock.json` + «+»).
- `FlowFragment` → грид слотов; тап → confirmation overlay.
- `SettingsFragment` → язык-placeholder, пресет, toggle + QR-placeholder (AlertDialog), сброс данных.
- `AddFlowWizardFragment` / `AddSlotWizardFragment` → заглушки.
- `AdminDevicesFragment` → пустое состояние + FAB «+».
- `MockFlowRepository` читает `flows_mock.json` (schemaVersion:1); все 22 теста зелёные.
- Локализация ru-RU + en-US, override через Settings.

**Что НЕ входит:**
- Реальный Firebase / network / pairing-логика.
- Реальный provider dispatch (только mock-toast).
- Persistence кроме mock JSON.
- First-launch preset picker (обсуждался, реализован ли — уточнить по коду).

**Незавершённое в 003:**
- T072 smoke-check: пройти весь first-launch → добавить flow → добавить тайл → открыть Settings → сменить пресет → увидеть `!`-индикатор → выполнить требование → индикатор исчез.

---

### Spec 004 — `action-architecture-v2`

**Статус:** не начат.

**Зависит от:** 003 (доменные модели).

**Что делает:**
- Реальный `ActionRequest` с `{provider, payload, fallback}` — расширение текущего sealed class.
- `ProviderRegistry` поверх `AppIndex` — маппинг установленных приложений на провайдеры (whatsapp, telegram, youtube, phone, sms, browser, custom-url).
- Реальный dispatch через интенты: WhatsApp deep-link, YouTube intent, `tel:`, `sms:`, открытие Play Store.
- Удаление файлов 002-whatsapp (перечень в `session-2026-05-05-decisions.md`, решение #6).
- Маппинг mock-провайдеров из spec 003 на реальные интенты.
- Правила миграции: как соотносится старый `ActionRequest.WhatsAppHandoff` с новым `{provider: "whatsapp", payload: ...}`.

**Что НЕ входит:**
- Firebase / remote config.
- Capabilities-report (это в 005).
- Удалённое управление.

---

### Spec 005 — `provider-capabilities-and-health`

**Статус:** не начат.

**Зависит от:** 004.

**Что делает:**
- Снапшот «какие провайдеры доступны на этом устройстве»: `{provider, displayName, icon, available, version}`.
- Health-метрики OLD: battery %, connectivity (WiFi/mobile/none), lastSeen, версия приложения.
- Локальные потребители снапшота: Setup Assistant (spec 009) и UI tile-editor (spec 003+).
- Подготовка к экспорту в `/links/{linkId}/capabilities` и `/links/{linkId}/health` (фактический экспорт — в spec 006).

**Что НЕ входит:**
- Реальная отправка в Firebase (только локальный потребитель).
- Background service — только hook на `ProcessLifecycleOwner.RESUMED`.

---

### Spec 006 — `pairing-and-firebase-channel`

**Статус:** не начат.

**Зависит от:** 005.

**Что делает:**
- Firebase project setup, подключение `google-services.json`.
- `FirebaseRemoteSyncBackend` — реализация интерфейса `RemoteSyncBackend` из spec 003.
- Pairing protocol (шаги 1-6, описаны ниже в разделе «Pairing protocol»).
- Реальный consent-screen на стороне OLD (с данными admin'а).
- Firestore security rules: admin пишет только в `/config`, OLD пишет только в `/state`, `/capabilities`, `/health`.
- FCM для push-уведомлений об изменениях конфига.
- `FakeRemoteSyncBackend` — in-memory для тестов.

**Что НЕ входит:**
- Bidirectional config sync (это в 007).
- Admin-mode UI (это в 008).

**Требует до старта:**
- Создание Firebase project (real env).
- Обновление `docs/compliance/country-legal-tax-register.md` (новые персональные данные).
- Обновление `docs/compliance/permissions-and-resource-budget.md` (новые network permissions).

---

### Spec 007 — `bidirectional-config-sync`

**Статус:** не начат.

**Зависит от:** 006.

**Что делает:**
- Применение `/config` admin→OLD (раскладка flow/слотов/контактов).
- Публикация `/state` OLD→admin (что реально применилось, source of truth для admin-UI).
- Conflict resolution: что делать, если OLD offline и получил устаревший конфиг.
- Schema versioning для `/config` и `/state` — поле `schemaVersion` с первого коммита.
- Backward-compat read: читать `/config` предыдущей версии.
- Migration path при смене схемы.
- Room database для локального хранения конфига (вместо mock JSON из 003).

**Что НЕ входит:**
- Admin-mode UI (это в 008).
- Commands (create-delete-move tiles через push) — уточнить: входит ли в 007 или в 008.

**Важный паттерн:**
Admin-UI рендерится из `/state` (что реально применилось), а не из `/config` (что admin отправил). Это защита от partial-apply (provider недоступен на OLD).

---

### Spec 008 — `admin-mode-flows`

**Статус:** не начат.

**Зависит от:** 007.

**Что делает:**
- Полноценный UI редактора у admin'а: список paired-устройств (не mock), тап → device-detail.
- Мониторинг health: battery %, connectivity, lastSeen — живые данные из Firebase.
- Редактирование раскладки OLD'а удалённо: добавление/удаление/перемещение тайлов.
- Добавление контакта из телефона admin'а (его собственный `READ_CONTACTS`) в слот OLD'а без `READ_CONTACTS` на OLD.
- `/commands/{cmdId}` — push-команды от admin'а к OLD (open Play Store, refresh capabilities, и т.д.).
- Расширяет каркас 003 на реальных данных (убирает mock из `AdminDevicesFragment`).

**Что НЕ входит:**
- Screen mirroring / remote control (нельзя без DPC).
- iOS управление.

---

### Spec 009 — `setup-assistant-soft-checks`

**Статус:** не начат.

**Зависит от:** 005. Может разрабатываться параллельно с 006/007.

**Что делает:**
- Чек-лист первичной настройки OLD-устройства (admin физически рядом).
- Soft-checks: каждый check имеет `id`, `criticality`, `check()`, `resolveIntent()`.
- Критичность: required (без этого функция не работает) / recommended (рекомендуется).
- Deep-link helpers: открыть Settings для конкретного разрешения, запросить ROLE_HOME.
- Индикатор `!` в Settings — сколько требований не выполнено.
- Переключение пресетов с diff-проверкой (что выполнено, что нет при смене пресета).
- Явный экран «что умеет этот пресет» при первом выборе.
- PIN-setup (финализация dev-stub `1234` из spec 003).

**Что НЕ входит:**
- DPC/Device Owner provisioning (полный strict-mode) — это будущий ADR.
- iOS.

---

### Backlog — `config-portability`

**Статус:** backlog stub (`specs/backlog/config-portability.md`).

**Зависит от:** 007.

**Что делает (когда дойдёт очередь):**
При замене OLD-устройства admin перепривязывает новый телефон к существующему `linkId`/конфигу без потери настроек.

**Варианты реализации (для будущего spec'а):**
1. Bind по `linkId` в облаке: admin выпускает новый QR для существующего `linkId`, старая привязка инвалидируется, `/config` остаётся.
2. Local export/import: на OLD — экспорт конфига в файл через `ACTION_CREATE_DOCUMENT`, на новом — импорт.
3. Cloud backup snapshot: автосохранение слот-снапшотов на дату в Firebase.

---

## Firestore schema

Детально обсуждена в сессии 2026-05-05. Является частью spec 006.

```
/links/{linkId}/
  ├── /config            ← admin пишет, OLD читает (раскладка flow/слотов/контактов)
  │       schemaVersion: Int
  │       flows: [...]
  │       updatedAt: Timestamp
  │
  ├── /state             ← OLD пишет, admin читает (source of truth для admin-UI)
  │       schemaVersion: Int
  │       appliedAt: Timestamp
  │       flows: [...]   ← что реально применилось (может отличаться от /config)
  │       presetId: String
  │
  ├── /capabilities      ← OLD пишет редко (при установке/обновлении приложений)
  │       schemaVersion: Int
  │       updatedAt: Timestamp
  │       providers: [{provider, available, displayName, iconBase64, version}]
  │
  ├── /health            ← OLD пишет периодически (hook на ProcessLifecycleOwner.RESUMED)
  │       schemaVersion: Int
  │       updatedAt: Timestamp
  │       batteryPercent: Int
  │       isCharging: Boolean
  │       connectivity: "wifi" | "mobile" | "none"
  │       lastSeen: Timestamp
  │       appVersion: String
  │
  └── /commands/{cmdId}  ← admin создаёт, OLD выполняет, пишет результат
          schemaVersion: Int
          type: String    ("open_play_store" | "refresh_capabilities" | ...)
          payload: Map
          createdAt: Timestamp
          status: "pending" | "executing" | "done" | "failed"
          result: Map     ← OLD заполняет после выполнения
```

**Правила безопасности (Firestore Security Rules):**
- Admin пишет только в `/links/{linkId}/config` и `/commands/{cmdId}`.
- OLD пишет только в `/links/{linkId}/state`, `/capabilities`, `/health`, `/commands/{cmdId}/result` (апдейт статуса).
- Оба читают весь `/links/{linkId}`.
- Сторонние не имеют доступа.

**Важное соглашение:** Admin-UI рендерится из `/state`, а не из `/config`. То, что admin отправил, может ещё не примениться (OLD offline, provider отсутствует). `/state` — зеркало того, что реально применилось.

---

## Pairing protocol

Шаги 1-6, обсуждены в сессии 2026-05-05.

```
Шаг 1. OLD — первый запуск
  • Генерирует oldDeviceId (UUID).
  • Регистрируется в Firebase Auth (anonymous token).
  • Создаёт /devices/{oldDeviceId} в Firestore.

Шаг 2. OLD — пользователь разрешает удалённое управление
  • Settings → «Разрешить удалённое управление этим телефоном» → включает toggle.
  • OLD создаёт /pairings/{token} в Firestore:
      token: random 6-char alphanumeric
      oldDeviceId: ...
      claimed: false
      expiresAt: now + 5 minutes

Шаг 3. OLD — показывает QR
  • QR содержит deep-link: launcher://pair?token=<token>
  • Пользователь держит экран перед admin'ом.

Шаг 4. Admin — сканирует QR
  • Читает token из QR.
  • Аутентифицируется в Firebase (anonymous token для MVP).
  • Делает Firestore-транзакцию «claim pairing»:
      • Проверяет /pairings/{token}: exists, claimed=false, expiresAt в будущем.
      • Атомарно: claimed=true.
      • Создаёт /links/{linkId} = {adminId, oldDeviceId, createdAt}.

Шаг 5. OLD — consent
  • Слушает /pairings/{token}.claimed.
  • При claimed=true — показывает consent-экран:
      «Admin <adminId> сможет видеть: заряд батареи, последний онлайн, установленные приложения.
       Admin <adminId> сможет менять: список тайлов, контакты, порядок flow.»
  • Кнопки: «Разрешить» / «Отклонить».
  • При «Отклонить» → удаляет /links/{linkId}, сбрасывает /pairings/{token}.
  • При «Разрешить» → пишет /links/{linkId}/state (начальный снапшот).

Шаг 6. Оба — подписка на realtime-listeners
  • OLD: слушает /links/{linkId}/config (получает конфиг от admin'а).
  • Admin: слушает /links/{linkId}/state (видит текущее состояние OLD'а).
  • OLD: периодически пишет /links/{linkId}/health (hook на ProcessLifecycleOwner.RESUMED).
```

**Условия корректной работы:**
- Оба телефона имеют интернет в момент pairing.
- OLD имеет интернет для получения конфига (offline — получит при следующем подключении).
- Admin видит OLD offline через поле `lastSeen` в `/health`.
- FCM требует Google Play Services на OLD (edge-case без GMS — отдельный backlog).

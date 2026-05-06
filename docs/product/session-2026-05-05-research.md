# Исследования сессии 2026-05-05

Источник: восстановленный транскрипт `.recovered-session-2026-05-05-clean.md`.
Зафиксировано, чтобы не повторять в будущих сессиях.

---

## 1. Рыночный анализ аналогов: почему ниша свободна

### Продукты для elderly + caregiver-портал

| Продукт | Тип | Удалённое управление лаунчером | Open-source | Примечание |
|---|---|---|---|---|
| **GrandPad** | Планшет для пожилых + caregiver-портал | Да, но только через managed-стек провайдера | Нет | Предзаказ через провайдера, полностью закрытый стек |
| **Necta Launcher** | Android senior-launcher | Нет, только локальная конфигурация | Нет | Доминирующая модель на рынке |
| **RealMe Senior** | Android senior-launcher | Нет | Нет | Аналогично Necta |
| **Big Launcher** | Android senior-launcher | Нет | Нет | Крупные иконки, один экран |
| **Sparkle** | Caregiver app + видео/контакты | Нет удалённого управления лаунчером | Нет | Есть caregiver-app, но управление слабое |
| **Oscar Senior / Oscar Family** | Caregiver app + контакты | Нет удалённого управления лаунчером | Нет | |
| **JackPro Launcher / Phoneware** | Нишевые | Неизвестно | Нет | Без публичного API |

### Вывод
На рынке нет open-source аналога «лаунчер для OLD + удалённое управление лаунчером». Это продуктовая ниша.

Ближайшая UX-метафора — **Google Family Link / Apple Screen Time** (parent ↔ child). Паттерны, которые стоит использовать: pairing через QR/account, явное согласие на устройстве пожилого, разделение «что admin может» vs «что пожилой видит».

### Открытые части для ссылок (не копировать)
- [Headwind MDM](https://github.com/h-mdm) — Android DPC, GPL. Паттерны provisioning/QR.
- [LineageOS Trebuchet](https://github.com/LineageOS/android_packages_apps_Trebuchet) — slot/grid model для launcher. Apache 2.0.
- AOSP `Settings`, `RoleManager` API — для Setup Wizard step-checks.

---

## 2. UX-исследование для пожилых

### Источники
- Nielsen Norman Group — рекомендации по UX для seniors.
- Apple Easy Mode / Samsung Easy Mode — паттерны single-screen launcher'а.
- Big Launcher / Necta / Phone Senior — реальные продукты, их сильные и слабые стороны.
- WhatsApp / Twitter / Ozon — референсы bottom-nav (по запросу владельца проекта).

### Ключевые находки

**Big Launcher / Necta / Phone Senior:**
- Один экран, гигантские иконки (56dp+).
- Слабость: нет нескольких контекстов (рабочие/личные контакты, развлечения), только один поток.

**Nielsen-Norman Group для seniors:**
- Минимальный размер тапа: 56dp+.
- Минимальный размер шрифта: 18sp+.
- Отказ от swipe-only navigation — всегда явные кнопки.
- Всегда confirmation для destructive-действий.
- Лаконичные подписи: label обязателен под иконкой в bottom-nav (без него для пожилого слишком абстрактно).

**Apple Easy Mode / Samsung Easy Mode:**
- Один экран с очень крупными плитками, нет переключений.
- Это и есть `senior-launcher-strict` пресет в терминологии нашего проекта.

**Family Link / Apple Screen Time:**
- Источник для UX парного управления.
- Паттерн consent-flow при первом подключении.

### Решения по UX-пресетам

Bottom-nav остаётся **как опция пресета**:
- `senior-launcher`: скрыта при единственном flow → визуально совпадает с традиционным senior-launcher'ом.
- `flow-light` и admin: всегда видна.
- При 2+ flow в `senior-launcher`: icon + label, обязательна подпись.

---

## 3. Технические опции backend'а с pros/cons

| Вариант | Pros | Cons |
|---|---|---|
| **Firebase Firestore + FCM** | Быстрый MVP, free tier (Spark), бесплатный FCM, готовый realtime-listener, NAT-прозрачен | Vendor lock-in, GDPR компромиссы |
| **Supabase** | Open-source, self-host, PostgreSQL | Дороже в operations, менее зрелый SDK |
| **Custom REST + WebSocket + FCM/APNs** | Максимум контроля, максимум приватности | Максимум работы, нет готового pairing-flow |
| **WebRTC P2P** (как RustDesk, STUN+signalling) | Максимально privacy-friendly, нет central server | Pairing-UX сложнее, offline-доставка отсутствует |

**Принятое решение:** Firebase для MVP за интерфейсом абстракции `RemoteSyncBackend`.

**Как работает Firebase для dual-device:**
- Прямого device-to-device соединения нет. Firebase — ретранслятор.
- Оба телефона независимо стучатся в один Firebase-проект по HTTPS.
- Firestore: пары устройств, конфиги, capabilities, health.
- FCM: push-уведомления об изменениях конфига.
- FCM требует Google Play Services (edge-case: Huawei/китайские ROM без GMS — отдельный backlog).
- Условие: оба телефона должны иметь интернет.
- Firebase Spark (free tier) хватает на тысячи устройств для MVP.

---

## 4. Setup Wizard на Android consumer (без DPC/MDM)

### Что реально возможно без Device Owner

На Android consumer (не DPC) можно только:
- `RoleManager.createRequestRoleIntent(ROLE_HOME)` — запросить роль default launcher.
- Deep-link в `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — запросить отключение оптимизации батареи.
- `NotificationManager.areNotificationsEnabled()` — проверить уведомления.
- `PackageManager.getLaunchIntentForPackage()` — проверить наличие приложения.
- `PowerManager.isIgnoringBatteryOptimizations()` — проверить текущий статус.

### Что требует Device Owner

Полный lock-down (kiosk-режим, lock-task) возможен только через:
- **Device Owner** — одноразовая `dpm set-device-owner` через ADB или QR-provisioning.
- **Android Enterprise / MDM** — Headwind MDM, Hexnode, Intune.

### Что реально для iOS

Только через supervised + MDM (Apple Configurator + бесплатный MDM типа Mosyle Free / Jamf Now). Guided Access — частичный и неавтоматизируемый.

### Вывод

Реалистичный MVP — «Android consumer + soft-lock»:
- Default launcher role.
- Battery opt-disable.
- Notification access.
- Auto-update toggle.
- Deep-link helpers.

Без обещаний полного strict-mode. Strict (DPC, lock-task, kiosk) — backlog для будущего ADR.

---

## 5. Ограничения iOS

Без MDM/supervised:
- Guided Access — частичный, не автоматизируется удалённо.
- Нельзя установить default launcher (архитектурное ограничение iOS).
- Нельзя ограничить доступ к приложениям без MDM.

С MDM (Apple Configurator + Mosyle Free / Jamf Now):
- Можно заблокировать интерфейс.
- Можно установить managed apps.
- Требует supervised-режима устройства.

**Принятое решение:** Android-only для MVP. iOS — явный gap, зафиксировано в plan.md каждой spec. iOS — следующая итерация после Android supervision/MDM-инфраструктуры.

---

## 6. ZXing vs ML Kit Barcode vs ZXing-core — сравнение

| Библиотека | Генерация QR | Сканирование | Offline | GMS | Размер | Лицензия |
|---|---|---|---|---|---|---|
| **ZXing-android-embedded** | Да | Да (готовый Activity) | Полностью | Не нужен | ~1 MB | Apache 2.0 |
| **ZXing core only** | Да | Только низкоуровневый decode | Полностью | Не нужен | ~600 KB | Apache 2.0 |
| **ML Kit Barcode Scanning** | Нет | Да | Да (runtime-модель) | Нужен + подгрузка ~2-3 MB | Мал | Google ToS |

**Важно:** ZXing — это Java/Kotlin библиотека, компилируемая внутрь APK. Не внешний сервис. Никаких запросов никуда. Не может упасть из-за недоступности сервиса.

**Выбор:** ZXing-android-embedded для MVP. ML Kit подходит для production-качества сканирования, но избыточен на Этапе 0.

---

## 7. Evolutionary architecture references

Исследование проводилось для формирования CLAUDE.md (незыблемых engineering-правил). Источники:

| Источник | Ключевая идея | Применение в проекте |
|---|---|---|
| **Neal Ford / Building Evolutionary Architectures** | Fitness functions как автоматизированные проверки архитектурных инвариантов. «Small changes + feedback loops» как защита от дрейфа. | Lint-правила «нет `import com.google.firebase` в `:core`», dependency-graph checks. |
| **Hexagonal / Ports & Adapters (Cockburn / DDD)** | Домен изолирован от инфраструктуры через интерфейсы (ports). Реализации (adapters) сменяются. | `RemoteSyncBackend`, `ProviderRegistry` — интерфейсы в `:core/api`, реализации в `:feature-*`. |
| **Anti-Corruption Layer (Eric Evans, DDD)** | Обёртка вокруг каждого внешнего SDK. Его типы не текут в домен. | Firebase, ZXing, Play Billing — каждый за своим адаптером. |
| **One-way / two-way doors (Bezos)** | Классифицировать решение перед принятием. Reversible — действуй. Irreversible — пиши exit ramp. | Framework принятия решений в CLAUDE.md. |
| **Minimum Viable Architecture** | Добавлять абстракцию сегодня только если её отсутствие потребует rewrite (не просто addition) завтра. | Тест: «если убрать абстракцию и заинлайнить — что теряем?». Если только optionality — убираем. |

---

## 8. Семь пробелов конституции, выявленных в сессии

Выявлены при сравнении конституции с Neal Ford / Bezos / DDD / MVA-литературой. Закрыты в `CLAUDE.md`.

| # | Пробел | Что добавлено |
|---|---|---|
| 1 | Конституция не использует термин «Hexagonal/Ports & Adapters» явно. Article IV намекает, но не запрещает явно вендорным типам утечь в домен. | Явный запрет: «Domain code MUST NOT import vendor SDKs, transport types, platform system types». |
| 2 | Нет явного требования Anti-Corruption Layer — обёртка SDK была опциональной. | Правило: «Wrap every external SDK so its types never appear in any signature visible to the domain». |
| 3 | Нет one-way/two-way door decision framework. | Раздел: «Decisions: one-way doors vs two-way doors» с обязательным exit ramp для one-way. |
| 4 | Article XI (anti-abstraction) тяготеет к чистому YAGNI; не сбалансирован MVA-принципом. | Правило MVA: «Add an abstraction today only if not adding it would force a future rewrite». |
| 5 | Нет требования fitness functions — автоматических проверок инвариантов. | Правило: «When an invariant can be checked automatically, prefer that to manual review». |
| 6 | Article VII покрывает schema-versioning конфигов, но не wire-format versioning в целом. | Правило: «Anything that leaves the device or persists across app versions is a wire format and behaves like a public API». |
| 7 | Нет mock-first development как process rule (только как рекомендация). | Правило: «Build domain types and UI against in-memory or fake adapters before integrating real SDKs. Every external port has at least one fake adapter and one real adapter». |

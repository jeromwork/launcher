# 07. TV и другие form factor'ы

**Дата фиксации**: 2026-05-30
**Статус**: концептуальное понимание; реализация TV — post-MVP.

---

## Суть решения

**TV — это не «режим phone-app»**, это **другой form factor** с другой физикой устройства (нет touch, есть пульт, расстояние 3 метра). TV требует **отдельный UI source set** (`tvMain/`), но **общий domain** с phone-app.

**В MVP TV не реализуется.** Цель сейчас — **оставить архитектурные швы** в F-4 / F-014, чтобы при разработке TV не пришлось переписывать domain.

---

## Концептуальная модель TV-launcher

### Физика TV

| Свойство | Phone | TV |
|---|---|---|
| Ввод | Touch + клавиатура | Пульт (D-pad + OK) |
| Расстояние от экрана | 20-30 см | 3 метра |
| Экран | 5-7 inch, portrait | 32+ inch, landscape 16:9 |
| Текст | Можно мелкий | Большой, читаемый из 3 метров |
| Камера | Front camera | Обычно нет |
| GPS, sensors | Есть | Нет |
| Установлено apps | Сотни | Десятки (TV-apps мало) |
| Владелец | Один | Семья смотрит вместе |

### Что показывает TV-launcher

- **Home screen** — крупные плитки apps (Netflix, YouTube, Kinopoisk, START, Wink) + наши плитки (контакты для звонков, фото от семьи).
- **NO edit mode на TV.** Настройка плиток — **через phone admin app**. TV только рендерит config.
- **NO wizard на TV.** Изначальная настройка — через phone (или, если TV-only, минимальный D-pad wizard — но это отдельный flow).

### Two-stage configuration pattern (выбран)

1. **Owner на phone** настраивает плитки TV через свой admin UI (тот же экран EditorScreen, но target = TV X).
2. **TV** получает config через Firestore (или own-server), рендерит.

Это согласуется со спекой 008 (config sync через общий ConfigDocument).

### Curated TV apps list

На TV нельзя получить динамический полный список установленных apps так же легко как на phone (только TV-apps с `LEANBACK_LAUNCHER` intent filter). Стратегия:

- **Наш сервер поддерживает список популярных TV-apps**: Netflix, YouTube, Kinopoisk, START, Wink, Okko, Megogo, Premier и т.д.
- TV-app **запрашивает список** с нашего сервера (НЕ hardcoded — обновляется server-side без app update).
- Phone admin app **получает тот же список** и показывает в picker'е «выберите apps для плиток TV».

Это **post-MVP**. В F-4 — только заметка в backlog.

---

## Архитектурные швы в F-4 / F-014 (для будущего TV)

### 1. `AuthProvider` port — должен принять CrossDeviceAuthAdapter

На TV вход через Google Sign-In — это **Device Code Flow** (TV показывает QR / 6-значный код → user логинится с phone → TV получает токен). Это **отдельный adapter**, но **тот же port**.

В F-4 `AuthProvider` port пишется так, что добавление `CrossDeviceAuthAdapter` потом = новый adapter + новый `AuthMethod.GoogleCrossDevice` case, **без переписывания port'а или domain**.

### 2. `EditUiProfile` — sealed type расширяемый

Сейчас в спеке 014: `AdminProfile | SeniorProfile`. Для TV добавится `TVProfile` (D-pad focus, no edit mode, large tiles).

Sealed type позволяет добавить case + новые UI компоненты без переписывания selector'а или domain.

### 3. `ConfigDocument` — platform-agnostic

`ConfigDocument` (config из спек 008/014) **не должен содержать phone-specific assumptions**:
- ❌ Hardcoded screen dimensions в pixels.
- ❌ Touch-specific gestures в config'е.
- ✅ Логические описания: «эта плитка тут, она такого размера в относительных единицах, действие = open app X».

Phone-app рендерит эти описания одним способом, TV-app — другим.

Это уже зафиксировано в спеке 008 как принцип «render-locally from shared document» (Figma multiplayer pattern). В F-4 — добавляется заметка для TV-readiness.

---

## Distribution: один Play Store listing или два

**Открытый вопрос**, требует проверки в Play Console:
- **Можно ли** зарегистрировать **один APK** (Android App Bundle) в **двух Play Store категориях** (Phone + Android TV) с разными описаниями и условиями использования?

Если **да** — это наш путь. Один app, две witness'а discovery.

Если **нет** (требуются два отдельных проекта Play Console) — мы оставляем в одной категории (Phone). TV доступен sideload или через AppGallery когда туда выложимся.

**Это не блокирует F-4.** Распределение решается перед TV release.

---

## non-GMS TV (Huawei TV, Xiaomi China TV, etc.)

**Out of MVP.** Long-term — возможный target после own-server (потому что без Firebase Auth можно использовать любой identity provider).

Push на non-GMS TV — HMS Push Kit (Huawei) или Mi Push (Xiaomi). Это **отдельная transport layer** infrastructure. Поэтому — Phase 4+ или V-4 expansion.

---

## Multi-profile на TV (бабушка / папа / дети)

Owner упомянул: «можно ещё настроить TV app под разные профили — бабушка, папа, дети».

**Это отдельный продуктовый вопрос**, аналог «named configs» из спеки 014 (Q1.1):
- TV хранит несколько named profiles.
- Каждый profile = свой config (свои плитки).
- На TV — переключатель профилей (как Netflix profiles).

**Это post-MVP.** В roadmap — будущая TV expansion spec.

---

## Что в F-4 нужно прописать про TV

В spec 015 (F-4) добавляются заметки в Scope:
- ✅ `AuthProvider` port расширяемый (CrossDeviceAuthAdapter для TV — future).
- ✅ `User`, `AuthMethod`, `AuthError` — vendor-agnostic, работают на любом form factor'е.
- ❌ TV adapter в F-4 НЕ реализуется.
- ❌ Compose-tv UI в F-4 НЕ реализуется.
- ❌ TV-specific Wizard в F-4 НЕ реализуется.

Backlog entry: `TODO-FUTURE-SPEC-013: Android TV launcher (V-4 in roadmap)`.

---

## Связанные документы

- [03-auth-provider-port.md](03-auth-provider-port.md) — port расширяемый, CrossDevice case добавится.
- [01-unified-app-model.md](01-unified-app-model.md) — phone Standard/Senior это runtime preset, TV это **отдельный form factor** (другая story).
- [`docs/product/roadmap.md`](../../roadmap.md) §V-4 Android TV Preset — без изменений.
- [`docs/dev/project-constants.md`](../../../dev/project-constants.md) §Senior-safe (для будущего сравнения с TV).

# 10. Три оси выбора + KMP мультиплатформенность

**Дата фиксации**: 2026-05-30
**Назначение**: концептуальная карта «как у нас устроены варианты UI поверх единого ядра» — нужна, чтобы не путать разные оси выбора при принятии решений о формах продукта (Standard/Senior, TV, iOS, watch, и т.д.).

---

## Три ортогональных оси выбора

Часто все три путают в одну «делаем разные приложения». Они **независимы**, и часть путаницы из обсуждения 014↔unified app model именно от их смешения.

### Ось 1: Distribution (как доставляется до пользователя)

- **Один APK** для всего — один продукт в Play Store.
- **Несколько APK** — Phone APK, TV APK, Auto APK (раздельные продукты).
- **App Bundle + dynamic feature modules** — один APK в Play Store, модули скачиваются по требованию на разные form factor'ы.

### Ось 2: Build configuration (что компилируется)

- **Build variant / flavor** — один codebase, разные сборки (для нашего проекта flavor'ов сейчас нет).
- **Один универсальный APK** — без вариаций.

### Ось 3: Runtime mode (что app делает на устройстве в момент времени)

- **Mode выбирается при первом запуске** — wizard outcome.
- **Mode выбирается автоматически по device class** — TV → TV-UI, phone → phone-UI.
- **Mode переключается в работе** — 7-tap → standard → senior.

---

## Где наши решения по каждой оси

### Phone (MVP)

- **Distribution**: один APK, один Play Store listing.
- **Build**: один универсальный APK (без flavor).
- **Runtime mode**: **runtime preset** — Standard / Senior через wizard outcome; 7-tap = switch обратно. См. [01-unified-app-model.md](01-unified-app-model.md).

### TV (post-MVP, V-4)

- **Distribution**: один Play Store listing с phone + TV категориями (если Play Console позволит). Иначе — separate listing, но **один codebase**. См. [07-tv-and-other-form-factors.md](07-tv-and-other-form-factors.md).
- **Build**: App Bundle с dynamic feature modules — `tv-ui-module` скачивается только на TV.
- **Runtime mode**: на TV — единственный mode (никакого Standard/Senior switching, потому что TV это не про owner, а про family viewing).

### iOS (V-1 post-MVP)

- **Distribution**: отдельный App Store listing (Apple требует, нельзя обойти).
- **Build**: отдельный Apple-target в KMP, использует `iosMain` source set.
- **Runtime mode**: аналог phone (Standard / Senior).

### Wear / Auto / другие (long-term)

- **Distribution**: form-factor-specific listing'и (Play Store раздельные).
- **Build**: dynamic feature modules.
- **Runtime mode**: form-factor-specific (отдельный UI).

---

## KMP мультиплатформенность — что это и как у нас

**Kotlin Multiplatform (KMP)** — возможность писать **общий код** один раз на Kotlin, который компилируется под разные платформы (Android JVM, iOS native, JavaScript, Wear, native desktop).

В нашем проекте:

```
core/
  └─ commonMain/        ← ПИШЕТСЯ ОДИН РАЗ, компилируется под все targets
       └─ domain        ← бизнес-логика, port'ы, value types
       └─ api/edit      ← EditUiProfileSelector (например)
       └─ ...

  └─ androidMain/       ← Android-specific код (если нужен в core/)
  └─ iosMain/           ← iOS-specific код (будущее, V-1)

app/
  └─ androidMain/       ← Android UI + adapter'ы
       ├─ phoneMain/    ← Compose UI для phone (touch)
       ├─ tvMain/       ← compose-tv UI для TV (D-pad) — будущее, V-4
       └─ adapters/     ← GoogleSignInAdapter, FirestoreAdapter и т.д.

  └─ iosMain/           ← iOS UI + adapter'ы (будущее, V-1)
       └─ ...
```

**Ключевой принцип**:
- **Domain (`core/commonMain/`)** — общий для всех platforms.
- **Adapter'ы / UI** — platform-specific, в `androidMain/` / `iosMain/` / etc.
- Пишешь port в `commonMain/`, **реализуешь** его в каждом `<platform>Main/` отдельно.

---

## Что это значит для F-4

`AuthProvider` port объявляется в **`core/commonMain/auth/`** — он будет работать для Android phone, Android TV, iOS, и любого будущего target'а.

Adapter `GoogleSignInFirebaseAuthAdapter` пишется в **`app/androidMain/auth/`** — он Android-specific.

Когда дойдём до iOS (V-1), напишется `GoogleSignInFirebaseAuthAdapterIos` в **`app/iosMain/auth/`** — domain port не меняется, добавляется новая реализация.

Это и есть **унификация через адаптеры** (о которой ты сказал): «через домены выбирать, какая платформа».

---

## Почему НЕ делаем «один app для всех платформ» прямо сейчас

**Compose Multiplatform** позволяет писать **общий UI** под Android + iOS + desktop. **Но**:

- Android и iOS phone — относительно близкие платформы по UI patterns, общий UI работает.
- Android phone и Android TV — **разные UI patterns** (touch vs D-pad). Общий UI не работает.
- iOS и Android TV — ничего общего.

Поэтому **realistic strategy**:
- **Domain** — общий через KMP (всегда).
- **UI** — общий через Compose Multiplatform **там, где UX patterns похожи** (phone Android ↔ phone iOS).
- **UI** — раздельный там, где physical model отличается (TV vs phone, Wear vs phone).

**В MVP (только Android phone)** этот вопрос не возникает. Заметка для будущего.

---

## Универсальность ≠ overengineering

Стремление к универсальности (один проект, разные платформы) — **правильно**, но **не значит**:
- Писать сейчас Wear / Auto / Apple TV adapter'ы. CLAUDE.md rule 4 (MVA) — не строим абстракции под отсутствующие требования.
- Закладывать UI flexibility под все возможные platform'ы заранее. UI пишется per platform.

**Универсальность достигается** через:
- Domain isolation (CLAUDE.md rule 1) — domain не знает про platform.
- ACL (CLAUDE.md rule 2) — все vendor / platform зависимости в адаптерах.
- Port-driven дизайн — добавление платформы = новый adapter, не rewrite.

Если эти три соблюдены, добавление iOS (или TV, или Wear) — это **новый source set + новые adapter'ы**, без переписывания domain.

---

## Связанные документы

- [01-unified-app-model.md](01-unified-app-model.md) — phone runtime mode.
- [07-tv-and-other-form-factors.md](07-tv-and-other-form-factors.md) — TV form factor, отдельный UI source set.
- [03-auth-provider-port.md](03-auth-provider-port.md) — port'ы в commonMain, adapter'ы в platform-specific.
- Constitution Article V (Modularization With Restraint).
- CLAUDE.md rules 1, 2, 4.

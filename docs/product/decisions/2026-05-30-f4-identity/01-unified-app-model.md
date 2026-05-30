# 01. Unified App Model — один app, runtime preset

**Дата фиксации**: 2026-05-30
**Заменяет**: модель «отдельные preset-APK» (Simple Launcher как отдельный продукт vs Admin Workspace как отдельный продукт)

---

## Суть решения

**Существует один Android app**, который пользователь устанавливает из Play Store. После установки **первоначальная настройка** проходит **в стандартном режиме телефона** (обычный Android UI, не senior-safe). По окончании wizard'а app может быть **переключён в Senior mode** (фасад с крупными плитками, lock'ом системных элементов).

**Senior Launcher — это НЕ отдельный продукт.** Это **режим того же app'а**, активированный набором конфигурационных флагов после wizard'а.

---

## Ключевые свойства

### 1. Preset — это именованный набор runtime-конфигов, НЕ enum в коде

Преставление о «preset» как `enum FlowPreset { Workspace, SimpleLauncher }` — **упрощение для спеки 014**. Реальная модель: preset — это **именованный конфиг с десятками полей**:

```
Simple Launcher preset (пример конфига для бабушки)
├─ lock_volume_buttons: true
├─ lock_system_shade: true
├─ lock_home_screen: true
├─ tile_size: large
├─ show_widgets: false
├─ tap_target_min_dp: 56
├─ allow_uninstall: false
├─ ... десятки настроек

Workspace preset (пример для admin'а самого себя)
├─ lock_volume_buttons: false
├─ tile_size: medium
├─ show_widgets: true
├─ allow_uninstall: true
├─ ... меньше настроек, больше про визуализацию
```

**Simple Launcher и Workspace = два дефолтных preset'а**, которые мы поставляем с app'ом. Owner может их:
- **применить** (выбрать как активный),
- **отредактировать** (создать вариант на основе),
- **создать новый** с нуля.

Это согласуется с CLAUDE.md rule 9 (shareability — preset = portable artifact с `schemaVersion`).

### 2. Standard mode / Senior mode — derived state, не отдельный код

«Standard mode» и «Senior mode» — это **результат применения текущего preset'а**. Никакого `if (mode == SENIOR) { ... }` в UI коде. UI читает поля preset'а:

- `show_widgets` → решает, рендерить ли widgets.
- `tile_size` → размер плиток.
- `lock_volume_buttons` → подключить ли VolumeKeyLocker.
- и т.д.

Это даёт **continuous spectrum** между full admin UI и full senior фасадом, не два дискретных режима.

### 3. 7-tap возвращает в Standard mode (временный override)

Из любого senior preset'а 7-tap → app временно применяет Workspace preset (или explicit Standard mode override). Это даёт владельцу доступ к настройкам. После выхода из настроек — возвращается к senior preset'у.

### 4. Первоначальная настройка — в Standard mode

Wizard, Google Sign-In, добавление контактов, выбор плиток — всё в **стандартном** UI, не senior-safe. Это нормально:
- Если устанавливающий — бабушка сама, она проходит wizard самостоятельно (или с помощью).
- Если устанавливающий — внук/помощник, он проходит wizard за бабушку и потом переключает в Senior mode.
- В обоих случаях бабушка **не видит** wizard после первого запуска.

Google Sign-In UI **нельзя** кастомизировать (Google его рисует), но это не проблема: пользователь его видит **один раз** в жизни на этом устройстве, в стандартном режиме.

---

## Что это меняет в спеке 014

**Spec 014 НЕ переписывается.** Interpretation **α'**:

`EditUiProfileSelector` в спеке 014 (`core/commonMain/api/edit/EditUiProfileSelector.kt`) остаётся valid pure function. Меняется только **источник входа**:
- Раньше предполагалось: preset = build-time constant (Workspace / SimpleLauncher как enum'ы).
- Теперь: preset = runtime named config (хранится локально в DataStore per F-014.0; на сервере per F-014.1 после F-4).

Domain selector не знает, откуда пришёл preset — это и есть правильное domain isolation (CLAUDE.md rule 1).

**Минорная правка в spec 014**: добавить one-liner comment рядом с selector'ом — «preset — runtime named config, может быть user-customized, не только built-in». Не блокирует F-014.0 phase.

---

## Что это меняет в roadmap

**Roadmap раздел «Universal Preset»** (часть II §Vision Recap) — был сформулирован как «единое ядро + 2 preset'a в MVP (Simple для Managed + Admin для семьи)». Это можно прочитать как «два APK». В новой модели — **один APK, два дефолтных preset'а внутри**.

Это не меняет vision, только product packaging. Roadmap раздел переформулировать в шаге обновления.

---

## Что это НЕ меняет

- **Domain (core/)** — без изменений.
- **Spec 014 scope/tasks** — без изменений (только one-liner comment).
- **Constitution Article V (Modularization With Restraint)** — без изменений. Form-factor варианты (Phone/TV) остаются профиль + downloadable модули. Unified app model — это про **один form factor (phone) с разными preset'ами внутри**, не про form factors.

---

## Связанные документы

- [02-identity-anonymous-removal.md](02-identity-anonymous-removal.md) — без anonymous обоим устройствам нужен auth, что согласуется с unified model.
- [07-tv-and-other-form-factors.md](07-tv-and-other-form-factors.md) — для TV модель другая (отдельный form factor с другим UI source set'ом).
- [`docs/product/roadmap.md`](../../roadmap.md) — §Vision Recap «Universal Preset» — переформулировать.
- [`specs/014-tile-editing-admin-senior-profiles/spec.md`](../../../../specs/014-tile-editing-admin-senior-profiles/spec.md) — minor comment update.

---
id: TASK-68
title: workspace profile preset (JSON content)
status: Draft
assignee: []
created_date: '2026-06-28 18:30'
updated_date: '2026-07-09 10:59'
labels:
  - phase-2
  - profile-content
  - workspace
milestone: m-1
dependencies:
  - TASK-65
  - TASK-67
  - TASK-120
priority: high
ordinal: 68000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** `workspace` — это **профиль** для устройства, которое **управляет другими** (типичный сценарий: дочка с workspace профилем управляет маминым simple-launcher; или врач workspace профилем управляет пациентами; или IT-support workspace профилем управляет рабочими телефонами сотрудников). Профиль — **только данные**, ни одной строки Kotlin. Если для workspace понадобится Kotlin-код — это значит TASK-65/58/59 не доделаны, возвращаемся туда.

## Что это простыми словами

Создаём второй профиль приложения — `workspace`. Это значит: при установке APK в picker'е появляется второй вариант («Я помощник для другого устройства / устройств»). Если пользователь выбирает workspace, его телефон:
- НЕ становится HOME launcher'ом (это обычное приложение).
- На главном экране — список спаренных устройств (paired devices dashboard).
- Через wizard сначала просит войти через Google (для recovery).
- Через wizard сразу предлагает спарить первое устройство.

**Что происходит по шагам (новая установка workspace):**
1. Установил APK → first-launch picker → две карточки: «Я для пожилого / простого пользователя» (simple-launcher) и «Я помощник / администратор» (workspace).
2. Выбрал workspace → wizard:
   - Шаг 1: войти через Google (для recovery).
   - Шаг 2: спарить устройство (показывает QR-код или предлагает отсканировать).
   - Шаг 3: готово.
3. На главном экране — список paired devices. Можно нажать на любое → откроется детальный экран (config editor — TASK-13, в этой задаче не входит).

**Что происходит по шагам (использование):**
1. Открыл приложение → видит список спаренных устройств с их статусом (online / offline / last sync).
2. Нажал «Добавить устройство» → QR-код для нового pairing'а.
3. Все edges/devices/configs — переживают потерю телефона (recovery через TASK-6/TASK-66).

## Зачем

Это **первая реальная фича**, которую видит конечный пользователь после TASK-65/58/59 foundation. Без TASK-68 foundation существует, но никто не пользуется. С TASK-68 — admin-сценарий («дочка управляет маминым телефоном за 200 км») закрыт и работает.

Также это **проверка правильности foundation**: если для workspace профиля **не потребовалось** ни одной строки Kotlin — значит TASK-65/58/59 действительно generic. Если потребовалось — баг foundation, возвращаемся.

## Что входит технически (для AI-агента)

**Только JSON, ни одной строки Kotlin.** Все файлы — в `core/src/androidMain/assets/profiles/workspace/` и связанные pools (которые могут получить новые entries в этой задаче).

- **`workspace.profile.json`** — корневой документ профиля (`schemaVersion=1`):
  - `id: "profile.workspace"`.
  - `name`, `description` — i18n keys.
  - `picks`: ссылки на pool entries (tiles, wizard steps, layout, settings).
  - `requires`: array обязательных system settings (НЕ-HOME role, например).
  - `excludes` (опционально): pool entries которые НЕ должны быть активны (например, simple-launcher-специфичные).
- **`workspace.wizard.manifest.json`** — последовательность шагов:
  - Step 1: `wizard.step.google-sign-in` (через `CheckSpec.AuthState` / `ApplySpec.RequestSignIn` из TASK-65).
  - Step 2: `wizard.step.pair-device` (через TASK-67).
  - Step 3 (optional): tile customization.
- **`workspace.screen.layout.json`** — layout главного экрана: paired-devices dashboard (полный экран, список карточек).
- **`workspace.tile.set.json`** — picks плиток: `tile.pairing.list` (главная), `tile.pairing.add` (FAB), `tile.settings`.
- **`profile-picker-bundle.json`** — мета-документ для first-launch picker'а, перечисляющий доступные bundled профили + их preview cards. Включает оба: `simple-launcher` и `workspace`.
- **i18n strings** — RU/EN под workspace профиль (имя, описание, тексты wizard'а, label dashboard'а).
- **Asset packaging** — обновить gradle build чтобы оба профиля попадали в APK.
- **Integration test** — на эмуляторе: установить APK → выбрать workspace → wizard → главный экран показывает dashboard.

**Что НЕ входит:**
- Config editor UI (когда тапаешь на paired device и хочешь поправить его контакты / темы) — TASK-13 Phase 2.
- Cloud sync UI поверх paired devices (статус «online», time of last sync) — TASK-24 Phase 4.
- Любой Kotlin код. Если AI-агент чувствует «надо бы добавить Kotlin» — **STOP**, это сигнал что TASK-65/58/59 не доделаны, возвращаемся туда.

## Состояние

**Planned.** Закрывается последней в цепочке TASK-65 → TASK-66 → TASK-67 → TASK-68. Финальный deliverable, превращающий foundation в видимую фичу.

---

## Готовый промт для `/speckit.specify`

```
Реализуй S-?? (TBD): workspace profile preset.

ЧТО СТРОИМ:
Второй профиль приложения — workspace. Pure JSON content, ни одной строки Kotlin.
Бандлится при build, появляется в first-launch picker (TASK-65) как второй вариант.
Wizard: Google sign-in → pair device → готово. Main screen: paired devices dashboard.

ЗАЧЕМ:
Первая видимая фича после foundation TASK-65/58/59. Закрывает сценарий «дочка управляет
телефоном бабушки за 200 км». Также — проверка что foundation действительно generic
(если потребовался Kotlin — баг foundation).

SCOPE ВКЛЮЧАЕТ:
- workspace.profile.json (picks + requires + excludes).
- workspace.wizard.manifest.json (sign-in, pair-device, done).
- workspace.screen.layout.json (dashboard layout).
- workspace.tile.set.json (picks).
- profile-picker-bundle.json (мета для picker'а).
- i18n strings RU/EN.
- Asset packaging обновлён.
- Integration test на эмуляторе.

SCOPE НЕ ВКЛЮЧАЕТ:
- Config editor UI (тап на paired device → редактирование) — TASK-13.
- Cloud sync status UI — TASK-24.
- ЛЮБОЙ Kotlin код. Это hard gate.

DEPENDENCIES:
- TASK-65 (Profile Composition Foundation v2) — first-launch picker + sign-in pool entries.
- TASK-67 (Pairing Feature) — pairing pool entries (tile.pairing.list, wizard.step.pair-device).
- TASK-3 (AuthProvider) — Verification, через TASK-65 wizard.step.google-sign-in.

ACCEPTANCE CRITERIA:
- Установил APK с чистого листа → first-launch picker показал две карточки → выбрал workspace.
- Wizard прошёл: Google sign-in → pair device (показал QR) → done.
- Главный экран: список paired devices (пустой если ничего не спарилось, появляется element после pairing).
- Спарил с simple-launcher устройством (TASK-67 flow) → у workspace в списке появилось → у simple-launcher в settings/pairing-list появилось.
- Workspace устройство НЕ является HOME launcher'ом (можно установить рядом с другим лаунчером).
- На том же APK другой пользователь выбирает simple-launcher → работает идентично TASK-7 (regression).
- Recovery на новом устройстве workspace профиля → paired devices автоматически восстановлены через TASK-66.
- Integration test проходит на pixel_5_api_34.

LOCAL TEST PATH:
- Emulator pixel_5_api_34 — workspace install + wizard + dashboard.
- Two emulators — workspace + simple-launcher pairing flow.

CONSTITUTION GATES:
- Article VII §3 (profiles = data, not forks): pure JSON, никакого Kotlin.
- Article VII §13 (no if-profileId): пройдено fitness function из TASK-65.
- Rule 5 (wire format): profile.json schemaVersion=1.
- Rule 9 (shareability): workspace.profile.json — shareable artifact, без identity-bound полей.

EFFORT: Small (~1 week — это только JSON и тесты).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Установил APK → first-launch picker показал две карточки (simple-launcher и workspace) → выбрал workspace
- [ ] #2 Wizard прошёл: Google sign-in → pair device → done
- [ ] #3 Главный экран показывает paired devices dashboard (пустой сначала, появляется элемент после pairing)
- [ ] #4 Спарил workspace с simple-launcher устройством → у обоих в списках появилось друг друга
- [ ] #5 Workspace устройство НЕ является HOME launcher'ом (можно установить рядом с другим лаунчером)
- [ ] #6 На том же APK другой пользователь выбирает simple-launcher → работает идентично TASK-7 (regression)
- [ ] #7 Recovery на новом устройстве workspace профиля → paired devices автоматически восстановлены, не нужно re-pairing
- [ ] #8 Ни одной новой строки Kotlin в репе по сравнению с baseline (только JSON / assets / i18n / тесты)
<!-- AC:END -->

---
id: TASK-7
title: Simple Launcher first-run + Setup Wizard
status: In Progress
assignee: []
created_date: '2026-06-23 05:36'
updated_date: '2026-06-24 15:00'
labels:
  - phase-2
  - s-spec
  - s-1
  - ui
  - wizard
milestone: m-1
dependencies:
  - TASK-1
priority: high
ordinal: 1000
references:
  - specs/task-7-simple-launcher-first-run/
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** В сценариях ниже используются обобщённые роли (CLAUDE.md «Personas vs domain roles»): `primary user` (тот, кто будет пользоваться телефоном — обычно пожилой), `remote administrator` или `assisting family member` (тот, кто настраивает — родственник в гостях / помощник). Реальная модель: **wizard проходит не primary user, а помощник** (либо родственник во время визита, либо платный помощник, либо в перспективе IT-support / медсестра / HR). Те же flow работают для clinic-варианта (пациент / медсестра), B2B (сотрудник / IT-support), self-care (primary user сам себе assisting).

## Что это простыми словами

**Терминология (constitution Article VII §9–13).** Приложение — это generic shell, поведение задаётся **профилями** (`profile` = композиция bundled JSON-конфигов). Каждый профиль определяет своё первое впечатление, главный экран, ассортимент плиток, набор используемых Android-настроек.

**TASK-7 = ship `simple-launcher` профиля.** Профиль `simple-launcher` — это elderly-friendly handheld variant: большие плитки, тёплые цвета, минимум choices. Wizard этого профиля проводит assisting'а через настройку телефона за один раз.

**Что входит в `simple-launcher` профиль (как композиция JSON-конфигов):**

1. `wizard.manifest` (`simple-launcher.wizard.manifest.json`) — **сценарий первого запуска**: какие шаги и в каком порядке. Шаги ссылаются на записи в `system-settings.pool` (ROLE_HOME, POST_NOTIFICATIONS, ...) и `ui-customization.pool` (язык, тема, размер сетки, выбор tile.set'а). Per-step `canSkip` override'ит pool default где это нужно для simple-launcher (например, ROLE_HOME в pool по умолчанию `canSkip: true`, для simple-launcher это `canSkip: false`).
2. Ссылки на bundled `screen.layout` (composition экрана — grid + toolbars + tabs + будущие status bar / app shortcuts) и `tile.set` (содержимое ячеек).
3. Локализованные strings (en + ru минимум) под все используемые ключи.

**Что происходит при первом запуске** (на примере family-варианта):
1. Помощник устанавливает APK на бабушкин телефон, открывает.
2. Wizard engine читает `simple-launcher.wizard.manifest.json` через `ConfigSource` (всё уже сделано в TASK-1 / F-3).
3. Engine последовательно проходит шаги, рендеря каждый через подходящий `WizardStep` (UIChoice / SystemSetting / TutorialHint).
4. Mandatory шаги — без skip'а, optional — со skip'ом и баннером в Settings.
5. По завершении wizard'а: applied configuration → home screen рендерится из выбранных `screen.layout` + `tile.set`.
6. Опционально (optional silent step или отдельная entry в Settings): pairing с устройством admin'а по QR — но в TASK-7 минимальный stub или вообще отсутствует, реальная интеграция — другая задача.

**Что НЕ происходит в этой задаче:**
- Никакого Google Sign-In (LOCAL mode per decision 2026-06-15-deferred-cloud/01).
- Контакты-плитки — заглушки (`actionType: "phone.call"` без реального contactId). Реальные contact tiles — TASK-9 / S-3.
- Кнопка SOS — TASK-10 / S-4.
- Photo upload / display — TASK-11 / S-5.
- Caregiver remote invite — TASK-31 / V-6.

## Зачем

**Первый видимый MVP-демо**: `git clone → install → open → wizard → working home screen`. Без этого нет демонстрируемого продукта — есть только инфраструктура (engine, ports, pools). Закрывает «empty top-level screen at launch».

Это также **первый concrete profile**, валидирующий универсальную модель из constitution Article VII §9–13: проверяем, что профиль действительно ship'ается как композиция bundled JSON-конфигов без нового Gradle-модуля кода и без `if (appFamilyId == "simple-launcher")` ветвей.

## Что входит технически (для AI-агента)

**Важно**: TASK-7 — это **content authoring + integration**, не infrastructure. Engine + ports + базовые pool'ы — всё сделано в TASK-1 (F-3 Done).

**Что нужно сделать в TASK-7:**

1. **Заполнить `simple-launcher.wizard.manifest.json` явными steps** — сейчас в коде `steps: null, autoOrder: true`. Указать порядок шагов и per-step `canSkip` override'ы где нужно. Примерный порядок (уточняется в clarify): language → ROLE_HOME (override `canSkip: false`) → POST_NOTIFICATIONS → theme → grid → tileSet → screenLayout.
2. **Добавить недостающие bundled `screen.layout` JSON'ы** если нужны варианты под grid choices 2×3 / 3×3 (сейчас один `3x4-classic.json` для 3×4). Альтернатива — один параметризованный layout + grid pool option решает всё; решение в clarify.
3. **Добавить недостающие bundled `tile.set` JSON'ы** под разные сегменты / размеры. Сейчас `classic-6.json` — 6 плиток (phone, messages, camera, gallery, contacts, settings). Возможно: `classic-9`, `classic-12`, или другие наборы с placeholder тайлами.
4. **Локализованные strings** (en + ru минимум) под все ключи которые `simple-launcher` flow задействует. Большая часть уже сделана в TASK-1, проверить дыры.
5. **Home renderer integration** — после wizard exit'а applied configuration → home screen рендерит выбранные `screen.layout` + `tile.set`. Renderer для этого уже существует (исторический spec 003 + spec 010 закрыли `/config/current` rendering); TASK-7 проверяет end-to-end.
6. **Тесты**:
   - Roundtrip JSON tests для всех новых bundled документов (per CLAUDE.md rule 5).
   - Backward-compat read test (`schemaVersion: 1`).
   - Integration test «fresh install → simple-launcher manifest → wizard runs → home screen renders chosen config» (через `FakeConfigSource` + `FakeSystemSettingPort` для unit-level + emulator smoke для system-level).
   - Senior-safe walkthrough на эмуляторе через skill `android-emulator` (`[hand]` AC).
7. **Skip-with-banner end-to-end check** — если в `simple-launcher` manifest какой-то step с `canSkip: true` пропущен, banner в Settings виден. Механизм уже сделан в spec 010 (`SetupCheckRegistry`); TASK-7 проверяет интеграцию для simple-launcher специфично.

**Что НЕ входит** (delegated / already done):
- `WizardEngine`, `WizardManifest`, `ConfigSource`, `SystemSettingPort`, `BundledConfigSource` — все ports + AndroidSystemSettingAdapter + AndroidStringResolverAdapter — **TASK-1 (F-3) Done**.
- `SetupCheckRegistry` + soft-checks badges + skip-with-banner infrastructure — **исторический spec 010 (orphan по backlog'у, код в репозитории)**.
- `ConfigEditor.appliedConfig` + home renderer reading from `/config/current` — **spec 010 Done**.
- Senior UI primitives (`SeniorButton`, `SeniorBodyText`, warm themes) — **F-3 Done**.
- Theme как enum / adapter — **TASK-7 не вводит**; тема уже `ui-customization.pool` entry со `choices: [light, dark, auto]`.

## Состояние

**In Progress** (взято в работу 2026-06-24). Зависит от TASK-1 (Done). Не блокируется TASK-6 (Paused, F-5 Root Key) — TASK-7 LOCAL mode, без cloud / identity.

**Корректировка модели 2026-06-24**: исходное описание этой задачи (написанное до constitution amendment 1.7) рассматривало `simple-launcher` как hardcoded Kotlin manifest с 5 шагами + 3 bundled ConfigTemplate под 6/9/12 плиток. Это было неправильно. Правильная модель — `simple-launcher` это **профиль** = композиция bundled JSON-конфигов; engine и манифест-схема уже сделаны в TASK-1; TASK-7 = content + integration. Описание выше отражает обновлённую модель.

---

## Готовый промт для `/speckit.specify`

```
Реализуй S-1: Simple Launcher profile (first MVP-demo).

ЧТО СТРОИМ:
Ship `simple-launcher` профиль (constitution Article VII §9-13) как композицию bundled JSON-документов поверх существующего wizard engine (TASK-1 / F-3 Done). Профиль = elderly-friendly handheld variant: большие плитки, тёплые цвета, минимум choices. Wizard этого профиля проводит assisting'а (родственник в гостях / помощник / IT-support) через настройку телефона primary user'а.

LOCAL mode: без Google Sign-In, без cloud. Cloud features — другие задачи.

ЗАЧЕМ:
- Первый видимый MVP-демо: `install → wizard → working home screen`. Без этого продукта нет — только инфраструктура.
- Первая валидация модели «профиль = композиция JSON-конфигов, не код» (Article VII §13).

SCOPE ВКЛЮЧАЕТ:
- Заполнить `simple-launcher.wizard.manifest.json` явными `steps` (сейчас `autoOrder: true`, нужно конкретный порядок и per-step `canSkip` override'ы).
- Добавить недостающие bundled `screen.layout` JSON'ы под grid choices если параметризованный layout недостаточен.
- Добавить недостающие bundled `tile.set` JSON'ы с placeholder тайлами (без реальных контактов — те в TASK-9).
- Локализованные strings (en + ru) под все ключи которые simple-launcher flow задействует.
- Integration tests + senior-safe walkthrough на эмуляторе.
- End-to-end check skip-with-banner для simple-launcher specifically.

SCOPE НЕ ВКЛЮЧАЕТ:
- WizardEngine, ports, AndroidSystemSettingAdapter — done в TASK-1.
- ConfigEditor + home renderer + SetupCheckRegistry — исторический spec 010 (orphan, код в репозитории).
- Admin App profile (TASK-8 / S-2).
- Contact tiles content (TASK-9 / S-3).
- SOS configuration (TASK-10 / S-4).
- Photo upload / display (TASK-11 / S-5).
- Caregiver invite (TASK-31 / V-6).
- Adaptive UX presets (TASK-19 / P-4 — это sub-feature внутри профиля per Project-Specific Direction §5).
- Google Sign-In step (deferred per decision 2026-06-15-deferred-cloud/01).
- Реальная интеграция QR-pairing в wizard (зависит от spec 007 / TASK-? — в TASK-7 либо stub либо отсутствует).

DEPENDENCIES:
- TASK-1 (F-3 Wizard Module + Localization) — Done.

ACCEPTANCE CRITERIA (помощник / assisting family member как actor):
- Установил APK на эмулятор → wizard появился сразу (≤ 2 сек cold start до первого экрана wizard'а).
- Прошёл все mandatory шаги (язык, ROLE_HOME, POST_NOTIFICATIONS, выбор сетки и tile.set) → главный экран с плитками отрисовался (≤ 1 сек после wizard exit'а), реально содержит выбранный tile.set.
- Перезагрузил устройство → wizard не повторяется; HomeActivity открывается с применённой композицией.
- Пропустил optional шаг (например, theme при `canSkip: true` в simple-launcher manifest) → в Settings висит баннер «настрой это», открывает тот же шаг standalone.
- Отказал в ROLE_HOME → wizard вежливо просит ещё раз через системные Settings (через DetectionStrategy.Programmatic check + re-prompt).
- Изменил системную локаль Android → строки wizard'а сменились после app restart.
- Senior-safe walkthrough на эмуляторе через skill `android-emulator` — assisting проходит без подсказок (это `[hand]` AC).

LOCAL TEST PATH:
- Эмулятор `pixel_5_api_34` через skill `android-emulator` (избегаем compose UI test API 35+ blocker).
- Fresh install → wizard → home screen end-to-end.
- Restart device → state persistent test.
- Locale switching test.
- Skip optional step → banner check.
- Unit: roundtrip + backward-compat для всех новых JSON'ов.
- Integration: FakeConfigSource + FakeSystemSettingPort для simple-launcher manifest replay.

CONSTITUTION GATES (relevant per constitution amendment 1.7):
- Article VII §9–13: `simple-launcher` ship'ается как композиция bundled JSON, не как новый Gradle-модуль кода и не как `if (appFamilyId == "simple-launcher")` ветка.
- Article VII §10: новые bundled JSON'ы используют существующие ConfigKind, не вводят новый kind.
- Article VII §12: mandatory/optional/skip semantics — pool-level defaults + per-profile override через `wizard.manifest`.
- CLAUDE.md rule 1 (domain isolation): никаких новых ports / Android types в new code.
- CLAUDE.md rule 5 (wire format): `schemaVersion: 1` на всех новых JSON'ах, roundtrip + backward-compat tests.
- CLAUDE.md rule 6 (mock-first): integration tests через FakeConfigSource.
- CLAUDE.md rule 9 (shareability): новые bundled JSON'ы обезличены, без identity-bound fields.

EFFORT: Medium (~1-2 weeks). Значительно меньше чем казалось изначально — infrastructure уже в коде, TASK-7 = content + integration.
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Assisting установил APK на эмулятор → wizard первый pending step виден ≤ 2 сек после tap'а на иконку
- [ ] #2 [hand] Assisting прошёл 3 mandatory + 1 optional → HomeActivity рендерит выбранную композицию (classic-6 поверх 3x4-classic) ≤ 1 сек после wizard exit'а
- [ ] #3 [hand] ROLE_HOME уже granted через Android Settings до wizard'а → wizard не показывает ROLE_HOME step (config-check master в действии)
- [ ] #4 [hand] System locale change (Android Settings → Languages → English) после wizard'а с languageOverride: ru → app остаётся на русском после restart'а (Article III §7 stability)
- [ ] #5 [hand] Pairing с admin device в wizard'е завершился успешно → LinkRegistry.activate() записал link → home screen рендерится с paired state
- [ ] #6 [hand] Перезагрузил устройство → wizard не повторяется; HomeActivity открывается с применённой композицией
- [ ] #7 [hand] Senior-safe walkthrough на эмуляторе через skill android-emulator — assisting проходит wizard без подсказок (manual [hand] AC)
<!-- AC:END -->

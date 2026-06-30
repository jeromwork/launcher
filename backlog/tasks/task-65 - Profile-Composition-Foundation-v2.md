---
id: TASK-65
title: Profile Composition Foundation v2
status: In Progress
assignee: []
created_date: '2026-06-28 18:30'
updated_date: '2026-06-30 21:00'
labels:
  - phase-2
  - foundation
  - profiles
  - composition
  - one-way-door
milestone: m-1
dependencies:
  - TASK-7
priority: high
ordinal: 65000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Эта задача — чисто **архитектурная инфраструктура**. Никаких ролей, никаких сценариев конкретного пользователя. Подготавливает почву для будущих профилей (`workspace`, `clinic-patient`, `self-care`), но сама не приносит новой user-facing функциональности — только регрессионно сохраняет `simple-launcher` через generic composition.

## Что это простыми словами

Превращаем «один профиль захардкожен» в «любой профиль = JSON-пик из каталога настроек». После этой задачи приложение знает, как **выбирать** профиль один раз при установке, **переключать** через настройки, и **никогда не проверять** профиль при boot'е.

**Что происходит по шагам (новая установка):**
1. Пользователь устанавливает APK, открывает.
2. Видит экран выбора профиля (как в Telegram при первом запуске выбираешь язык). Сейчас один вариант — `simple-launcher`. После TASK-68 появится второй — `workspace`.
3. Выбрал → запускается визард (как сейчас в TASK-7), но шаги собираются **из профиля**: профиль говорит «мне нужны эти 3 настройки», движок берёт описание шагов из `system-settings.pool.json` и собирает их в порядке.
4. Прошёл визард → приложение работает. Профиль записан в DataStore. **Никаких проверок на boot'е больше нет.**

**Что происходит при переключении профиля:**
1. Зашёл в Settings → «Сменить профиль» → выбрал новый.
2. Движок сравнивает «что требует новый профиль» и «что сейчас настроено в системе». Делает diff.
3. Если есть несоответствия (например, новый профиль не должен быть HOME launcher'ом, а сейчас является) — запускает мини-визард только из недостающих шагов.
4. Прошёл → новый профиль активен. Старые данные (контакты, темы, pairing) — на месте.

## Зачем

Сейчас `simple-launcher` захардкожен: его id вписан в манифест визарда (`appFamilyId: "simple-launcher"`), его шаги ссылаются на pool по жёстким id. Если добавить второй профиль (`workspace` для admin-сценария) — сейчас это означает форк кода. Конституция Article VII §3 явно запрещает форки профилей: profiles are **data, not forks**. Эта задача исправляет нарушение и открывает дорогу для всех будущих профилей (workspace, clinic-patient, self-care) без переписывания кода.

## Что входит технически (для AI-агента)

- **Удалить** `appFamilyId: "simple-launcher"` из `wizard-manifests/simple-launcher.json` body — это profile-leakage в wizard format. Manifest не знает о профиле; профиль ссылается на manifest по file id.
- **`Profile` wire format** — новый JSON документ `profile.json` `schemaVersion=1`. Содержит только picks по pool entry id + `requires:` array обязательных pool entries (любого kind'а, не только Android system).
- **`ConfigSource` port** (CLAUDE.md rule 9) — абстракция источника профилей. `BundledConfigSource` (первый адаптер) грузит из `assets/profiles/*.json`. Future: `NetworkProfileSource`, `ShareIntentProfileSource`.
- **`RequirementsChecker` port** — generic диспатчер. Принимает `List<CheckSpec>` (требования профиля), для каждого entry выбирает правильный checker по `kind` (android-role, android-permission, ui-font, ui-contrast, accessibility-pref, ...), возвращает `CheckResult(missing, satisfied)`. Расширяемо additively через новые `CheckSpec` variants — engine не меняется.
- **First-launch profile picker** — Compose screen в `core/profiles/`, читает список доступных профилей через `ConfigSource`, рендерит карточки с label/description (из i18n keys в profile JSON).
- **Profile switch flow** — в Settings entry «Сменить профиль», запускает diff-engine: `RequirementsChecker.check(newProfile.requires)` → строит wizard из недостающих pool entries.
- **In-app Settings reminders** — баннеры в `SettingsActivity` для missing requirements (SEQ-4). Re-check на `onResume`. Tap → mini-wizard. Per rule 10 — in-app indicator, не push.
- **`CheckSpec` / `ApplySpec` расширение** — новые variants `AuthState` / `RequestSignIn` для будущего sign-in step; demonstration `UIFont` variant (для extension-proof теста) — используется в test-profile.json fitness.
- **Pool naming convention spec** — namespaced immutable IDs `<pool>.<domain>.<subject>` (например `tile.pairing.list`, `wizard.step.google-sign-in`). Documented в `contracts/pool-naming.md`. Per rule 5: id immutable, можно `deprecated: true`, нельзя delete или rename.
- **Lint rule (no profile-id branching)** — fitness function, ловит `if (profileId == "...")` / `when (profileId)` / `appFamilyId == ...` в business logic. Pre-commit hook. Per Article VII §13.
- **Lint rule (extraction-readiness)** — fitness function, запрещает launcher-specific imports (`com.launcher.app.*`, references на tiles/contacts/home-launcher domain types) в модулях `core/profiles/`, `core/wizard/`, `core/pools/`. Обеспечивает что foundation готов к extraction в sub-repo / shared library когда придёт второе family-приложение (messenger / photo). Exit ramp для cross-app vision.
- **Fitness test** — dummy `test-profile.json` (минимальный, с **non-Android** требованием `ui.font.large` для демонстрации generic-ности RequirementsChecker), грузится в test-time DI, проверяет что engine generic (не падает на неизвестное profile id, корректно строит wizard из его requires:).
- **Regression test** — `simple-launcher` через composition выдаёт идентичный wizard и UX как до TASK-65.

## Состояние

**Planned.** Foundation для TASK-66/59/60 и всех будущих профилей. Должна быть закрыта до start работ над workspace.

---

## Готовый промт для `/speckit.specify`

```
Реализуй F-?? (TBD): Profile Composition Foundation v2.

ЧТО СТРОИМ:
Generic profile composition runtime: профиль = JSON-пик из каталога pool entries.
First-launch profile picker + profile switch flow через wizard-diff.
Удаление profile-leakage (`appFamilyId`) из wizard manifest format.
Pool naming convention (namespaced immutable IDs, schemaVersion per pool).
Lint rule «no `profileId == ...` in business logic».

ЗАЧЕМ:
Article VII §3 конституции: profiles are data, not forks. Сейчас simple-launcher
захардкожен в манифесте. Чтобы добавить workspace / clinic-patient / self-care
профили без переписывания кода — нужна generic composition foundation.

SCOPE ВКЛЮЧАЕТ:
- Удаление `appFamilyId` из wizard-manifest.json body.
- `Profile` wire format JSON (`profile.json` schemaVersion=1).
- `ConfigSource` port + `BundledConfigSource` adapter.
- `RequirementsChecker` port — generic диспатчер по CheckSpec.kind (android-role, android-permission, ui-font, ...). Расширяемо additively.
- First-launch profile picker UI (Compose).
- Profile switch flow в Settings (RequirementsChecker.check → WizardComposer.build от missing).
- In-app Settings reminders (banner-карточки по missing requirements, re-check на onResume, tap → mini-wizard) — per rule 10 in-app indicator.
- `CheckSpec.AuthState` + `ApplySpec.RequestSignIn` variants для auth-в-wizard.
- Demo `CheckSpec.UIFont` variant для extension-proof теста (используется в test-profile.json).
- Pool naming convention spec (`contracts/pool-naming.md`).
- Lint rule «no profileId branching» + pre-commit fitness function.
- Lint rule «extraction-readiness» — запрет launcher-specific imports в core/profiles/wizard/pools.
- Regression test: simple-launcher идентичен после рефакторинга.
- Fitness test: dummy test-profile.json (с non-Android требованием) доказывает generic-ность engine.

SCOPE НЕ ВКЛЮЧАЕТ:
- Server-fetched profiles (`NetworkProfileSource`) — добавляется additively позже.
- Sharing/import profiles — TASK-35 (Marketplace) в Phase 5.
- Профильно-специфические pool entries (pairing-list — TASK-67, workspace JSON — TASK-68).
- Extraction в sub-repo / shared library — kept в monorepo per rule 4. Trigger: messenger TASK-27 / photo TASK-28.
- Push notifications для missing requirements — per rule 10 используем in-app reminders, не push.

DEPENDENCIES:
- TASK-7 (Simple Launcher Setup Wizard) — Done.

ACCEPTANCE CRITERIA (проверяет пользователь):
- Установил APK с чистого листа → увидел экран выбора профиля → выбрал simple-launcher → визард прошёл идентично TASK-7.
- В Settings нашёл «Сменить профиль» → переключил на dummy `test-profile` → визард показал только недостающие шаги → после визарда профиль активен.
- Существующий simple-launcher пользователь после установки нового APK видит свой профиль автоматически (миграция в первый запуск).
- Lint падает на попытке закоммитить код с `if (profileId == "simple-launcher")` в core/ или app/.
- Документация pool-naming.md написана простым русским.

LOCAL TEST PATH:
- Emulator pixel_5_api_34 — первый запуск + переключение профиля.
- Unit tests с dummy test-profile.json — engine generic-ness.
- Fitness test для lint rule (gradle task).

CONSTITUTION GATES:
- Article VII §3 (profiles = data, not forks) — fitness через lint rule.
- Article VII §13 (no `if (appFamilyId == "x")` branches) — fitness через lint rule.
- Rule 5 (wire format): profile.json schemaVersion=1, pool naming immutable.
- Rule 9 (shareability): ConfigSource adapter pattern с первого коммита.

EFFORT: Medium (~2 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Sequences

Sequence diagrams (SEQ-1..SEQ-5) live inline in [`specs/task-65-profile-composition-foundation-v2/spec.md`](../../specs/task-65-profile-composition-foundation-v2/spec.md) under `## Sequences` heading. Format: Mermaid spec-level + plan-level + MENTOR-DETAIL block, per [CLAUDE.md «Sequences in spec.md»](../../CLAUDE.md) + ADR-011.

- **SEQ-1**: First-launch preset picker → simple-launcher apply (US-1).
- **SEQ-2**: Preset switch through Settings — diff-wizard + Profile history preserved (US-2).
- **SEQ-3**: Boot path — main axiom «no checks» (US-7).
- **SEQ-4**: In-app Settings reminders for missing requirements (US-4).
- **SEQ-5**: Silent migration of pre-TASK-65 simple-launcher users (US-3).

Backlog ранее (pre-2026-06-30) содержал sequences inline здесь — перенесены в spec.md по ADR-011 conformance (sequences живут в spec.md, не в backlog-task).

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Установил APK с чистого листа → увидел экран выбора **preset'а** → выбрал simple-launcher → визард прошёл идентично TASK-7 (SC-001)
- [ ] #2 [hand] В Settings → 'Сменить preset' → переключил на dummy test-preset → визард показал только недостающие шаги → preset активен. Switch обратно на simple-launcher → прежние bindings восстановились из истории Profile (SC-002)
- [ ] #3 [hand] Существующий simple-launcher пользователь после установки нового APK видит свой preset автоматически — без picker'а, без re-wizard (SC-003)
- [ ] #4 [hand] Detekt `PresetIdBranchingDetector` падает на `if (presetId == "simple-launcher")`, `when (appFamilyId)` в core/ или app/ (вне whitelisted `core/presets/`) — SC-004
- [ ] #5 [hand] Документация `contracts/pool-naming.md` написана простым русским, владелец-новичок может прочитать за <10 минут (SC-006)
- [ ] #6 [hand] Boot приложения после первой настройки НЕ вызывает `WizardEngine.computePending()` — trace доказывает (SC-007)
- [ ] #7 [hand] В Settings → отозвал ROLE_HOME руками → banner-карточка 'не настроено: HOME launcher' → тап → mini-wizard с ровно одним шагом → исправил → banner исчезает (SC-008 + SC-011)
- [ ] #8 [hand] Generic engine: `CheckSpec.UIFont` variant + `test-preset.json` с non-Android требованием → engine корректно диспатчит, строит wizard step, после применения fontScale re-check возвращает missing=[] (SC-009)
- [ ] #9 [hand] Detekt `ExtractionReadinessDetector` падает на `import com.launcher.app.tiles.Tile` в `core/presets/` / `core/wizard/` / `core/pools/` (SC-005)
- [ ] #10 [hand] `PoolSource` swap: DI переключение между `HardcodedPoolSource` и `JsonAssetPoolSource` (когда последний реализован) — приложение работает идентично; roundtrip test гарантирует identical entries (SC-012)
- [ ] #11 [hand] Naming inversion применён: в коде, spec'е, backlog AC используется **Preset** для shareable top-level и **Profile** для per-device personal data. Constitution amendment подготовлен (Article VII §9), требует владелец-approval перед merge
<!-- AC:END -->

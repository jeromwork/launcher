---
id: TASK-7
title: Simple Launcher first-run + Setup Wizard
status: Done
assignee: []
created_date: '2026-06-23 05:36'
updated_date: '2026-06-26 04:30'
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
> **Версии описания.** Это описание sync'нуто 2026-06-24 после полного цикла speckit (specify → clarify → scenarios → plan → tasks → analyze). Источник правды: [`specs/task-7-simple-launcher-first-run/`](../../specs/task-7-simple-launcher-first-run/). Это описание — projection финального scope'а для backlog Kanban читателя; за полным контекстом — в spec.md / plan.md / tasks.md / analyze-report.md.
>
> **Update 2026-06-25 (post device verification).** Manifest сокращён с 4 шагов до 3 (ROLE_HOME + tileSet + POST_NOTIFICATIONS). Шаг pair-admin **вынесен** из TASK-7 в TASK-8 после constitution amendment 1.10 (запрет `StepType.Custom` / per-refId Kotlin handlers). Phase-5 (T046-T049) reverted. Все упоминания "4 шага", "Custom step", "pair-admin Custom" ниже относятся к **состоянию до 2026-06-25**; читай в [`tasks.md`](../../specs/task-7-simple-launcher-first-run/tasks.md) секцию "Device Verification Findings" и Phase-5 revert notice. Pair-admin вернётся в TASK-8 как `SystemSetting` step с `CheckSpec.PairAdminLink` + `ApplySpec.PairAdminIntent` (см. TODO-TASK7-005 в [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md)).

> **Про роли в этой задаче.** Wizard проходит **assisting** — родственник в гостях / платный помощник / IT-support в clinic'е / HR в B2B. Aspirational secondary path: primary user (пожилой, со слабыми когнитивными способностями) проходит wizard сам (CLAUDE.md «Personas vs domain roles»). Те же flow для clinic / B2B / self-care сегментов.

## Что это простыми словами

**TASK-7 = ship `simple-launcher` профиль + закрыть 3 архитектурные дыры в F-3.** Профиль `simple-launcher` (per constitution Article VII §9–13) = композиция bundled JSON-конфигов = elderly-friendly handheld variant с большими плитками, тёплыми цветами, минимум choices.

**Финальный scope wizard'а (post-2026-06-25 revert)** — **3 mandatory шага**:

1. ★ ROLE_HOME (`canSkip: false` override от pool default `true`) — без этого launcher не launcher.
2. ★ tileSet — выбор раскладки плиток (currently only one bundled: `classic-6`).
3. ★ POST_NOTIFICATIONS (`canSkip: true` override от pool default `false`, auto-skip на Android < 13).

~~4. ☆ pair-admin (Custom step, optional silent)~~ — **removed 2026-06-25** per constitution amendment 1.10 (no `StepType.Custom`). Возвращается в TASK-8 как `SystemSetting` step с `CheckSpec.PairAdminLink` + `ApplySpec.PairAdminIntent` (см. TODO-TASK7-005).

Language auto-detect (нет wizard step). Theme default warm light (нет wizard step). Все остальные настройки (CALL_PHONE, accessibility-service, battery-optimization, hide-status-bar, fontScale, grid, screenLayout) — Optional Silent (доступны в Settings, нет step в wizard, нет banner'а).

**3 архитектурные дыры в F-3, которые TASK-7 закрывает:**

1. **`WizardEngine.computePending(manifest)` pre-flight** — заменяет linear traversal в `WizardEngineImpl.run()`. Engine при старте проверяет фактическое состояние per step через `SystemSettingPort.status()` и `UserPreferencesStore.current()`; pending показываются только если действительно нужны. **Replaces snapshot-of-manifest approach** (Article VII §14 config-check master pattern).
2. **`AppCompatDelegate.setApplicationLocales()` integration** — без этого `UserPreferences.languageOverride` сохранялся в DataStore, но реально не применялся. Сейчас applied после wizard exit + на cold-start (Article III §7 stability).
3. **Pool schema v1 → v2 bump** — declarative `CheckSpec` / `ApplySpec` sealed классы с `@JsonClassDiscriminator("kind")` replacing hardcoded `when(settingId)` dispatch в `AndroidSystemSettingAdapter`. Handler registry через Koin DI. v1 backward-compat read через legacy fallback.

**Что происходит при первом запуске** (на примере family-варианта):

1. Помощник устанавливает APK на бабушкин телефон, открывает.
2. Wizard engine читает `simple-launcher.wizard.manifest.json` через `ConfigSource` (TASK-1 / F-3 Done).
3. Engine вызывает `computePending(manifest)` → для каждого step проверяет фактическое состояние → возвращает pending. Если все настройки уже применены (например, ROLE_HOME granted вручную через Android Settings ранее) — wizard не запускается.
4. Wizard показывает только pending steps. Mandatory — без skip'а или с retry на отказе; optional — со skip'ом и баннером в Settings.
5. По завершении wizard'а: `AppCompatDelegate.setApplicationLocales()` применяет languageOverride; applied configuration → home screen рендерится из `classic-6` tile.set поверх `3x4-classic` screen.layout.

**Что НЕ происходит в этой задаче:**

- Никакого Google Sign-In (LOCAL mode per decision 2026-06-15-deferred-cloud/01).
- Контакты-плитки — placeholder тайлы (реальные contact tiles — TASK-9 / S-3).
- Кнопка SOS — TASK-10 / S-4.
- Photo upload / display — TASK-11 / S-5.
- Caregiver remote invite — TASK-31 / V-6.
- Adaptive-UX presets (тремор / низкое зрение / reduced dexterity) — TASK-19 / P-4.
- MCP / AI agent capability registry implementation — TASK-33 + TASK-36 (TASK-7 architecturally ready через CheckSpec/ApplySpec sealed hierarchies, не implements).
- Cloud sync «pending setup state» для admin visibility — TASK-8+ (inline TODO в коде TASK-7).

## Зачем

**Первый видимый MVP-демо**: `install → wizard → working home screen`. Без этого продукта нет, только инфраструктура (engine, ports, pools).

Это также **первый concrete profile**, валидирующий универсальную модель из constitution Article VII §9–15: профиль ship'ается как композиция bundled JSON-конфигов без нового Gradle-модуля кода и без `if (presetId == "x")` ветвей; pool schema v2 с declarative dispatch types (`CheckSpec` / `ApplySpec`) sealed в commonMain → multi-platform ready (Article VII §15 seam preserved для будущих iOS / Android TV адаптеров).

## Что входит технически (для AI-агента)

**TASK-7 — это infrastructure (3 архитектурные правки) + content authoring + UI work.** Engine + ports + базовые pool'ы — всё сделано в TASK-1 (F-3 Done). TASK-7 делает следующие конкретные изменения (см. [plan.md](../../specs/task-7-simple-launcher-first-run/plan.md) для детальной 8-phase rollout):

**Code changes (Phase 0-3, 5-6):**

- `CheckSpec` + `ApplySpec` sealed классы в `core/commonMain/api/wizard/data/` (5 + 4 = 9 variants).
- `CheckHandler` + `ApplyHandler` ports в `core/commonMain/api/wizard/handlers/`.
- 9 Android handlers (`AndroidRoleCheckHandler`, `AndroidPermissionCheckHandler`, etc.) + Koin DI registries в `core/androidMain/`.
- `WizardEngine.computePending(manifest)` + `runWalkThrough(manifest)` methods.
- `WizardEngineImpl.run()` rewires через computePending pre-flight (заменяет линейный traversal).
- `UserPreferences.hasValueFor(refId)` helper.
- `SettingStatusCache` + `CacheInvalidatingLifecycleObserver` (TTL 30s + invalidate-on-resume).
- `AndroidSystemSettingAdapter` модификация: handler registry dispatch + cache integration + v1 fallback.
- `WizardActivity` + `LauncherApplication` модификации: `AppCompatDelegate.setApplicationLocales()` calls.
- ~~`CustomStep` + `PairAdminCustomStepHandler` (launches spec 007 `PairingActivity`).~~ — **removed 2026-06-25** per constitution amendment 1.10 (no `StepType.Custom`); returns at TASK-8 as `SystemSetting` step.
- Settings UI: `PendingChecklistScreen`, `WalkThroughButton`, `LocaleDivergenceIndicator` (Compose composables + ViewModels).
- **Added 2026-06-25**: `Spec015DiGraphTest` regression test that resolves `WizardEngine` via real Koin to catch `Map<*, *>` ambiguity (the Phase-5 cycle that crashed `WizardActivity` on real device).

**Content changes (Phase 4):**

- `simple-launcher.wizard.manifest.json`: `autoOrder: true` → explicit `steps: [...]` (3 entries post-2026-06-25 revert; originally 4 including pair-admin Custom).
- `android-pool.json`: schemaVersion 1 → 2; добавить `check` + `apply` блоки для всех 6 entries.
- String resources (en + ru) для новых ключей (walk-through, locale divergence, ROLE_HOME retry message). ~~pair-admin keys~~ removed 2026-06-25 with Phase-5 revert.

**No new external dependencies.** Existing stack: kotlinx-serialization, Koin, AndroidX AppCompat, Compose Multiplatform.

**Tests (Phase 0, 2, 6, 7):**

- Roundtrip + backward-compat tests для pool v2 (per CLAUDE.md rule 5).
- Per-handler unit tests + cache invalidation test.
- `ComputePendingTest` с FakeSystemSettingPort scenarios.
- `RunWalkThroughTest`.
- Konsist fitness functions T7-001..T7-006 в `Task7ArchitectureTest.kt`.
- 6 `[deferred-local-emulator]` tasks + 3 `[deferred-physical-device]` tasks (требуют emulator / real device от owner'а).

**Что НЕ входит** (already done / delegated):

- `WizardEngine`, `WizardManifest`, `ConfigSource`, `SystemSettingPort`, `BundledConfigSource`, ports + base AndroidSystemSettingAdapter + StringResolver — **TASK-1 (F-3) Done**.
- `SetupCheckRegistry` + soft-checks badges + `PlayStoreFallbackActivity` — **spec 010 (orphan по backlog, код в репозитории verified)**.
- `ConfigBackedFlowRepository` (renders `/config/current`) + `HomeActivity` + `RootComponent` — **spec 010 + spec 003 (код verified)**.
- Senior UI primitives (`SeniorButton`, `SeniorBodyText`, warm themes) — **F-3 Done**.
- `PairingActivity` + `ConsentScreen` + `QrDisplayScreen` + `LinkRegistry` UI — **spec 007 (код verified)**.

## Состояние

**Verification** (Phase 0–7 implementation complete + device verification on Xiaomi 11T 2026-06-25). 2 architectural fixes surfaced during device run: (a) Koin `Map<*, *>` cycle → commit 8882c71 + `Spec015DiGraphTest`. (b) `StepType.Custom` retirement + Phase-5 revert → commit f4842aa + constitution amendment 1.10. Remaining device gates blocked by external TASK-51 (libsodium) / TASK-52 (HomeActivity hang). Pair-admin re-implementation moved to TASK-8 (see TODO-TASK7-005).

**Зависимости**: TASK-1 Done. Не блокируется TASK-6 (Paused, F-5 Root Key) — TASK-7 LOCAL mode, без cloud / identity. Опирается на orphan-спеки 010 / 007 / 003 (код в репозитории verified 2026-06-24 через grep / file inspection).

**Артефакты в `specs/task-7-simple-launcher-first-run/`** (9 коммитов на ветке):

- `spec.md` — 6 US (P1×3, P2×2, P3×1), 35 FR в 8 частях, 11 SC (7 c `[backlog]`), 6 scenarios, OEM matrix.
- `plan.md` — 8-phase rollout, Constitution Check 8/8 PASS inlined.
- `research.md` — 8 architectural decisions R-001..R-008 c alternatives + exit ramps.
- `data-model.md` — new types + F-3 modifications + relationship diagram.
- `tasks.md` — 68 tasks с trace matrix.
- `analyze-report.md` — 18 checklists inline assessed + verdict READY.
- `contracts/system-settings-pool-v2.md` — wire format schemaVersion 2 spec.
- `contracts/simple-launcher-manifest.md` — content spec для bundled manifest.

**Constitution amendments произведены в процессе** TASK-7 clarify pass'а (отдельные коммиты на этой ветке):

- **1.7** — Article VII §9-13: profile composition model + 5 wire-format kinds evolution policy + wizard as view of profile + mandatory/optional/skip semantics + no per-profile code module.
- **1.8** — Article II §8 (MVP definition), Article III §7 (stability over system-level changes), Article VII §14 (configuration lifecycle independent + config-check master), Article VII §15 (multi-platform adapter seam).
- **1.9** — Article XV §14 (cite UX precedent before original proposals).

**Effort revised**: исходный estimate Large (~3 weeks) → после clarify Medium (~1-2w) → после plan / tasks Medium+ (~2-3 weeks). 68 tasks; критический путь Phase 0+1+2+4+7 = MVP-shippable; Phase 3/5/6 = incremental delivery.

**MVP-первый critical path**: T001 → T002 → T003 → T004 → T010 → T012 → T013 → T016/T017 → T030 → T040/T041 → T059.

**Корректировки модели в процессе** (для контекста):

- 2026-06-24 утро: исходное описание рассматривало `simple-launcher` как hardcoded Kotlin manifest с 5 шагами + 3 bundled ConfigTemplate (6/9/12 плиток). **Это было неправильно**, поправлено в clarify.
- 2026-06-24 день (после clarify): scope расширился — обнаружены 3 архитектурные дыры в F-3 (engine traversal, locale persistence, hardcoded dispatch), которые TASK-7 закрывает. Это изменило характер задачи с pure-content на infrastructure + content.
- 2026-06-24 вечер (после scenarios): уточнён UX pattern для post-update donastroika — checklist + banner в Settings (UX precedent: GitHub / Slack / Stripe / Notion), а не auto wizard re-run. Walk-through wizard добавлен как отдельная Settings entry (precedent: Apple Setup Assistant / Windows OOBE / TurboTax).

---

## Готовый промт для `/speckit.specify` (historical, kept for archival)

> **Эта секция устарела после полного speckit-цикла 2026-06-24.** Промт ниже был использован для запуска `/speckit.specify`; финальная спека прошла существенный clarify + scenarios refinement и не совпадает с тем, что в промте. **Не использовать как стартовую точку повторного запуска** — для этого читай `specs/task-7-simple-launcher-first-run/spec.md` напрямую. Промт оставлен для истории / debugging речи / понимания «как изначально формулировалась задача vs что вышло».

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
- Article VII §9–13: `simple-launcher` ship'ается как композиция bundled JSON, не как новый Gradle-модуль кода и не как `if (presetId == "simple-launcher")` ветка.
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
- [x] #1 [hand] Wizard первый pending step виден ≤ 2 сек после tap'а на иконку — ✅ verified Xiaomi 11T 2026-06-25, T061 cold start TotalTime=1330 ms (commit 29e7c0d)
- [→] #2 [delegated:TASK-55] 3 mandatory шага → HomeActivity рендерит композицию ≤ 1 сек — moved to TASK-55 #1 (blocked by TASK-52)
- [x] #3 [hand] ROLE_HOME уже granted через Android Settings до wizard'а → wizard не показывает ROLE_HOME step (config-check master в действии) — ✅ verified Xiaomi 11T 2026-06-25, T063 PASS via `cmd role add-role-holder` (commit 29e7c0d)
- [→] #4 [delegated:TASK-55] System locale change → app остаётся на выбранном языке после restart — moved to TASK-55 #2 (blocked by TASK-52)
- [→] #5 [delegated:TASK-55] Pairing с admin device → LinkRegistry.activate() — moved to TASK-55 #3 (scope ушёл в TASK-8 per amendment 1.10 + TASK-51)
- [→] #6 [delegated:TASK-55] Перезагрузка → wizard не повторяется, HomeActivity с конфигом — moved to TASK-55 #4 (blocked by TASK-52)
- [N/A] #7 [hand] Senior-safe walkthrough на эмуляторе через skill android-emulator — assisting проходит wizard без подсказок — ⚠️ Visual contrast tuning DEFERRED per Article II §8 (MVP polish via JSON, not code) — TASK-54 paused to post-MVP Phase 4. Wizard flow itself is functional end-to-end on Xiaomi 11T 2026-06-25; only visual polish deferred.
- [→] #8 [delegated:TASK-55] Local emulator gates (T060, T062) — moved to TASK-55 #5–#6
- [→] #9 [delegated:TASK-55] Physical device gates (T038, T058, T064, T065, T066) — moved to TASK-55 #7–#11; T061 cold-start ✅ verified 2026-06-25 (1260 ms), T063 ROLE_HOME pre-grant ✅ verified.

**Resolution 2026-06-26**: TASK-7 closed Done. Все непройденные физические гейты делегированы в TASK-55 (verification aggregator) с привязкой к блокирующим task'ам (TASK-52, TASK-51, TASK-8). Code merged PR #30 (d5763d6). Wizard E2E работает до HomeActivity start; дальнейшая верификация — после закрытия блокеров.
<!-- AC:END -->

<!-- SECTION:VERIFICATION_PENDING:BEGIN -->
## Verification pending (post-merge)

Device-verification прошла на Xiaomi 11T (arm64, MIUI V125, Android 11) 2026-06-25. Подтверждено: cold start ≤2s (AC #1), config-check master ROLE_HOME pre-grant (AC #3), APK size +17 KB (T068 ≤150 KB лимит).

Найденный при прогоне блокер — Koin DI cycle между `SystemSettingPort` и `Map<StepType, WizardStep>` — починен в коммите **8882c71** (`task-7 fix: Koin Map<*,*> cycle blocking WizardActivity on real device`) + regression test `Spec015DiGraphTest`.

### Зависит от external follow-up tasks
- **TASK-52** (HomeActivity «Загрузка…» infinite) — блокирует AC #2, #4, #6 (full wizard flow → HomeActivity render → locale persist round-trip → reboot no-repeat).
- **TASK-53** (FirstLaunchActivity preset picker EN-on-RU) — visible regression в preset picker; не AC-blocker, но видна.

### Зависит от TASK-8 (Admin App + QR Pairing)
- **AC #5** (pairing) — pair-admin step removed from manifest 2026-06-25 with `StepType.Custom` retirement (constitution amendment 1.10). Returns as `SystemSetting` step at TASK-8 time. Also gated by **TASK-51** (libsodium ristretto255 native lib arm64) — without that fix `PairingActivity` itself crashes.

### Post-MVP polish (Paused)
- **TASK-54** (senior-warm theme contrast) — paused per Article II §8: MVP polish happens via JSON configuration, not code edits. Resumed at Phase 4 (m-3) when theme-variant JSON authoring pass begins. NOT blocking TASK-7 merge.

### Что нужно для перехода Verification → Done
1. TASK-52 fix (HomeActivity render finishes) → re-run T038/T058/T061-full-flow/T062.
2. TASK-8 lands with proper `SystemSetting` `pair-admin` step + TASK-51 libsodium fix → re-run T066 (2-device pairing).
3. Получить Samsung Galaxy → T064.

TASK-7-own architectural debt resolved 2026-06-25:
- DI cycle (commit 8882c71)
- StepType.Custom retired (commits in this PR + constitution amendment 1.10)

Когда любой блокер закрыт — повторный прогон `pre-pr-backlog-sync` обновит соответствующие AC.

<!-- SECTION:VERIFICATION_PENDING:END -->

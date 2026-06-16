# Quickstart: F-3 Developer Setup

**Date**: 2026-06-16 (REVISED 2026-06-17 post pre-flight) | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Что новому разработчику нужно сделать, чтобы начать работу с F-3. Цель — < 1 страница, < 1 час setup.

> **REVISED 2026-06-17**: F-3 — **не новые модули**, а пакеты в существующем `:core` модуле. Стек (Compose Multiplatform, Koin, Decompose, Compose Resources, Konsist) **уже выбран** per ADR-005. Setup быстрее, чем initially планировалось.

---

## 1. Prerequisites

- Android Studio Hedgehog (2023.1+) или Iguana (2024.1+).
- JDK 17 (per project policy).
- Kotlin 2.0+ (auto-managed by Gradle).
- Git access to `github.com/jeromwork/launcher`.

---

## 2. Initial clone

```bash
git clone https://github.com/jeromwork/launcher.git
cd launcher
git checkout 015-wizard-localization-senior-ui
```

## 3. Build & run tests *(REVISED 2026-06-17)*

```bash
# Smoke build — verify everything compiles после pull
./gradlew :core:assemble

# JVM unit tests (no эмулятор) — covers domain logic, Konsist arch tests
./gradlew :core:testRealBackendUnitTest        # включая Konsist WizardArchitectureTest
./gradlew :core:testMockBackendUnitTest

# Instrumented tests (требует эмулятор — start через android-emulator skill)
./gradlew :app:connectedRealBackendDebugAndroidTest --tests "*WizardE2ETest"

# Screenshot tests (Roborazzi, JVM-backed Robolectric)
./gradlew :core:verifyRoborazziRealBackendDebug

# Translation completeness — embedded в Konsist (custom rule check для resources)
# или ручной script (T079 — translate-strings.sh)
```

**Note**: `:core` имеет два product flavors — `realBackend` + `mockBackend` per spec 007. F-3 wizard работает identically в обоих.

**Expected**: all green. If red — see Troubleshooting section ниже.

## 4. Translation pipeline setup (NEW in F-3)

F-3 использует Claude API для авто-перевода новых strings. Required env var:

```bash
# macOS / Linux
export ANTHROPIC_API_KEY=your-key-here

# Windows (PowerShell)
$env:ANTHROPIC_API_KEY = "your-key-here"
```

Get a key from [console.anthropic.com](https://console.anthropic.com/settings/keys). Cost minimal (~$0.01 per spec batch).

**Without API key**: skill `procedure-translate-spec-strings` will terminate с понятным сообщением; manual translations возможны но не recommended.

## 5. Common dev workflows

### Adding a new user-facing string

1. Open `core/localization/src/commonMain/resources/MR/base/strings.xml`.
2. Add `<string name="my.new.key">My new string</string>` (EN base).
3. Add to `core/localization/strings-context/CONTEXT.json`:
   ```json
   "my.new.key": {
     "value": "My new string",
     "context": "Where it appears, audience tone, etc.",
     "screenshot": null
   }
   ```
4. Add Russian translation manually (your habit per C-6) in `MR/ru/strings.xml`.
5. Commit your changes.
6. When you run `/speckit.tasks` for your spec, skill `procedure-translate-spec-strings` auto-generates переводы для 9 remaining languages (es/zh/ar/hi/pt/de/fr/ja/kk-Latn).

### Adding a new system setting to wizard

1. Edit `core/wizard/src/commonMain/resources/MR/files/system-settings/android-pool.json`:
   ```json
   {
     "id": "android.permission.RECORD_AUDIO",
     "mechanism": "StandardPermission",
     "criticality": "Optional",
     "canSkip": true,
     "androidMinApi": null,
     "detectionStrategy": "Programmatic",
     "labelKey": "system_setting.record_audio.label",
     "descriptionKey": "system_setting.record_audio.desc"
   }
   ```
2. Add the two strings to `MR/base/strings.xml` + `CONTEXT.json` (per step «Adding string»).
3. Reference в any `wizard.manifest.body.steps[]` as `{ "stepType": "SystemSetting", "refId": "android.permission.RECORD_AUDIO" }` — or rely на `autoOrder: true` (auto-included by criticality).

### Adding a new UI customization option

1. Edit `core/wizard/src/commonMain/resources/MR/files/ui-customization/ui-pool.json`:
   ```json
   {
     "id": "vibrationLevel",
     "kind": "simple-choice",
     "questionKey": "ui.vibration.question",
     "criticality": "Optional",
     "defaultValue": "medium",
     "choices": [
       { "value": "off", "labelKey": "ui.vibration.off" },
       { "value": "medium", "labelKey": "ui.vibration.medium" },
       { "value": "strong", "labelKey": "ui.vibration.strong" }
     ]
   }
   ```
2. Add 4 strings to `strings.xml` + `CONTEXT.json`.
3. Handle answer в `UIChoiceStep` consumer (write to `UserPreferencesStore` через custom field — requires schemaVersion bump).

### Running the Konsist arch lint locally

```bash
./gradlew checkLauncherAgnosticImports
```

If fail: error message includes file path + imported class + suggested fix. Just follow the suggestion.

---

## 6. Package layout reference *(REVISED 2026-06-17)*

F-3 живёт в **существующем `:core` модуле** (НЕ создаём новые модули):

```
core/src/commonMain/kotlin/com/launcher/
├── api/wizard/            — domain ports (WizardEngine, ConfigSource, SystemSettingPort, ...)
├── api/localization/      — StringResolver, LocaleProvider, RtlHelper
├── ui/senior/             — Compose Multiplatform UI primitives (SeniorButton, theme, ...)
└── ui/wizard/             — Decompose host + step Composables

core/src/androidMain/kotlin/com/launcher/
├── adapters/wizard/       — Android adapters (Persistent*Store, AndroidSystemSettingAdapter, ...)
└── di/wizard/             — Koin module

core/src/commonMain/composeResources/
├── files/wizard/          — bundled JSONs (android-pool, ui-pool, etc.)
└── values{-ru,-es,...}/   — 11 локалей string resources
```

Detailed file tree: see [plan.md §4 REVISED](plan.md#4-project-structure).

---

## 7. Pre-plan spike (current pending deliverable)

Before any implementation, run the 2-day library spike per [research.md](research.md):

- Day 1: moko-resources vs Compose Multiplatform Resources (string tables).
- Day 2: Konsist vs ArchUnit-kotlin (arch lint).

Outcomes documented in `research-day1-strings.md` + `research-day2-lint.md`. Spec.md C-8/C-15 updated with final choices.

**Status**: pending. Spike must complete before `/speckit.tasks`.

---

## 8. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `./gradlew :core:wizard:check` fails on Konsist test | Forbidden import detected | Read failure message; move class or remove import |
| `checkTranslations` fails on `de.strings` missing key | New key added without translation | Run translation skill manually, or add manually |
| `connectedDebugAndroidTest` fails to start | No эмулятор running | Use `android-emulator` skill: «start pixel_5_api_34» |
| `ANTHROPIC_API_KEY not set` | Env var missing | See §4 above |
| moko-resources class `MR` not generated | KSP not configured / stale cache | `./gradlew clean :core:localization:build` |
| Compose preview fails to render SeniorButton | Theme not wrapped | Wrap preview content in `SeniorWarmTheme.Light { ... }` |

---

## 9. Useful skills (Claude Code)

- `/speckit.specify` — write new spec.
- `/speckit.clarify` — resolve ambiguities in spec.
- `/speckit.scenarios` *(новый)* — generate plain-language sequences для verification (предлагается автоматически после clarify).
- `/speckit.plan` — generate plan.md from clarified spec.
- `/speckit.tasks` — generate tasks.md from plan.md. **Triggers `procedure-translate-spec-strings` skill в конце**.
- `/speckit.analyze` — final audit before implementation.
- `android-emulator` skill — start emulator, install APK, take screenshots.

---

## Краткое содержание простым русским языком

Этот документ — **«с чего начать новому разработчику»**, который придёт в проект F-3.

**Главное за 5 пунктов**:

1. **Что установить**: Android Studio, JDK 17, ключ от Claude API (в env переменную).
2. **Как проверить, что всё работает**: `./gradlew :core:wizard:check` — должно стать зелёным за 2 минуты.
3. **Как добавить новую строку текста**: написать на английском + русском, добавить контекст в `CONTEXT.json`, при следующем `/speckit.tasks` skill сам переведёт на остальные 9 языков.
4. **Как добавить новую системную настройку Android в wizard**: одна строка в JSON-файле + два ключа перевода. Никакого Kotlin кода.
5. **Перед реализацией**: пройти 2-дневный «spike» — проверить, что 2 выбранные библиотеки реально работают. Если нет — выбрать резервные.

Всё это спроектировано так, чтобы новый разработчик мог за 1 час начать работу — без долгих чтений документации.

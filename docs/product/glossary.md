# Glossary — конфиги, presets, profiles, wizards

**Дата**: 2026-06-16, обновлено 2026-07-09 (Amendment 1.11 body rewrite) · **Источник**: discussion 2026-06-16 + constitution amendment 1.7 (2026-06-24) + amendment 1.11 (2026-06-30, body rewritten 2026-07-09 during TASK-65 close).

> Этот документ — **единственный источник правды** для терминов конфигурационной архитектуры. Любой новый артефакт (спека, ADR, decision doc) должен использовать **эти** имена.
>
> **Обновление 2026-07-09** (Amendment 1.11 body rewrite): каноническая терминология после naming inversion:
> - **`preset`** — shareable, self-contained top-level composition. Раньше называлось «профиль» / «app-family». Wire format поле — `presetId`.
> - **`profile`** — per-device personal data (applied preset ref + bindings + settings cache + UI overrides). Раньше не имело отдельного имени.
>
> Constitution Article VII §9–13 — определение composition, эволюция wire-format kinds, mandatory/optional/skip semantics.

> **⚠️ Обновление 2026-07-10** (TASK-120 Component/Preset/Profile foundational rescope, [specs/task-120-preset-composition-foundation/spec.md](../../specs/task-120-preset-composition-foundation/spec.md)):
>
> **Step / StepType / StepHandler / `wizard.manifest.steps[]` / `stepType` — LEGACY.** Все `Step`-упоминания в этом glossary ниже описывают текущий TASK-7-era wizard код и артефакты в `core/src/commonMain/kotlin/com/launcher/api/wizard/`. TASK-120 заменяет их новой моделью:
>
> - **`Component`** (sealed hierarchy) заменяет `StepType`.
> - **`ComponentDeclaration`** заменяет `WizardStep` (Pool entry с параметрами).
> - **`Pool`** (реестр declarations) заменяет `system-settings.pool` / `ui-customization.pool` / etc — единый реестр вместо разбиения по kind.
> - **`Provider` + `ProviderRegistry`** (per-platform/vendor check/apply) заменяет `SystemSettingPort` / `UIChoiceStep` handler'ы.
> - **`Preset`** wire format schemaVersion=2 получает **три поля**: `wizardFlow` / `settingsMap` / `activeComponents` (заменяет single `steps[]` list).
> - **`Profile`** wire format schemaVersion=2 хранит `activeComponents: List<ProfileComponent>` (не `ProfileStep`).
>
> Terminology purge на активные документы (spec.md, backlog task-120/121) выполнен 2026-07-10. Этот glossary — **NOT** переписан inline, только помечен header'ом. Полный rewrite глоссария — при closing of TASK-120 (следует за реализацией и terminology sync commit'ом, аналогично TASK-65 sync 2026-07-09).

---

## 1. Зачем словарь существует

В обсуждении выяснилось, что слово «**preset**» в наших же документах ранее использовалось в **трёх разных смыслах**:

1. UX-family целого приложения (Simple Launcher, Admin App, TV) — **это остаётся `preset`** после Amendment 1.11.
2. Стартовый JSON-шаблон плиток («6 plates classic», «9 plates + calendar») — это `tile.set`.
3. Параметр шага wizard'а (`2×3` / `3×4` grid layout) — это entry в `ui-customization.pool`.

Glossary закрепляет однозначные имена. Значение №1 сохранилось за словом `preset`; №2 и №3 получили другие имена.

---

## 2. Три слоя архитектуры

| Слой | Что | Где живёт в коде | Когда строится |
|---|---|---|---|
| **A — Preset** | Тип целого приложения как **shareable композиция bundled JSON-конфигов** — собственная UX, navigation, capabilities. Per constitution Article VII §9: «`simple-launcher`, `admin-app`, `clinic-patient-app`, `self-care`, `workspace` — это presets». **Прежние имена «Профиль» / «App-family»** — deprecated; в wire format поле называется `presetId`. | `app/` (Compose, MVI presentation); JSON в `core/src/androidMain/assets/presets/` | Simple Launcher — TASK-7 / S-1; Admin App — TASK-8 / S-2; Workspace — TASK-68; TV / Caregiver — Phase 4+ |
| **A' — Profile (per-device state)** | Локальные personal данные, живущие на конкретном устройстве: какой preset активен, bindings (контакты в плитках), applied-state cache, UI overrides. Не shareable; синкается только между paired устройствами через pairing keys | `core/profile/` + `ProfileStore` (DataStore Preferences) | TASK-65 foundation; расширяется в TASK-70 (multi-profile для admin app) |
| **B — Wizard (first-run flow)** | Универсальный движок шагов настройки. **Wizard — это view preset'а**, не отдельная сущность (Article VII §11) | `core/wizard/` (KMP common) | TASK-1 / F-3 (Done) |
| **C — ConfigDocument (runtime)** | Живой документ, описывающий текущее состояние устройства (плитки, контакты, виджеты) | `core/` (домен, уже существует, спека 008) | Уже есть. Спека 014 расширила до named configs |

**Ключевое правило:** Wizard **не редактирует** ConfigDocument напрямую. Wizard **производит первый** ConfigDocument из выбранного `tile.set` + ответов пользователя. Дальнейшее редактирование — спека 014 (Tile Editing), а не wizard.

**Принцип «preset = данные, не код»** (Article VII §13, Project-Specific Direction §3): новый preset ship'ается как новые bundled JSON-документы, **не** как новый Gradle-модуль кода и **не** как `if (presetId == "x")` ветка в business-logic.

---

## 3. Семейство bundled JSON-схем (текущее поколение)

> **Обновление 2026-06-24**: после реализации TASK-1 (F-3) bundled-семейство выросло с **трёх** схем до **пяти** — добавились `system-settings.pool` и `ui-customization.pool`. Это «текущее поколение» per constitution Article VII §10; деление зон ответственности **подвижно** и может эволюционировать (через schemaVersion bump per CLAUDE.md rule 5). Специфика и код MUST обращаться к этим типам через `ConfigKind` enum, не хардкодить N-way ветки по именам.

В текущем поколении — **пять** независимых JSON-схем. Это **семейство**, не единая мега-схема (Server-Driven UI отвергнут, см. §5). Каждая со своим `schemaVersion`, своим набором полей, своим roundtrip-тестом.

| Имя схемы | Описывает | Потребитель |
|---|---|---|
| **`wizard.manifest`** | Порядок шагов first-run flow для одного preset'а. Поля: `steps[]` с `stepType` (`UIChoice` / `SystemSetting` / `TutorialHint`) и параметрами. Если `autoOrder=true` — engine собирает шаги из pool'ов автоматически по criticality. Preset identity живёт в `preset.uid` / `preset.version`, не в manifest'е (removed 2026-07-09 during Amendment 1.11 body rewrite; StepType.Custom удалён 2026-06-25 Amendment 1.10) | `WizardEngine` |
| **`screen.layout`** | Композиция главного экрана: `gridRows × gridCols`, `bottomToolbar`, `topTabs`. По мере эволюции — статус-бар, app-shortcut intents, multi-screen navigation. **Не содержит плиток** — только архитектуру | `HomeRenderer` (Compose в `app/`) |
| **`tile.set`** | Обезличенный стартовый набор плиток: `tiles[]` с `position`, `actionType` (см. capability registry), `labelKey`, `iconKey` | First-run после wizard'а; создаёт первый ConfigDocument |
| **`system-settings.pool`** | Каталог Android-platform-level settings, которые preset может включить в свой wizard: `id` (`android.role.home`, `android.permission.POST_NOTIFICATIONS`, …), `mechanism` (`StandardPermission` / `SpecialPermission` / `DeepLink` / `AccessibilityService` / `InAppOnly`), `criticality`, `canSkip`, `deepLink`, `androidMinApi`, `detectionStrategy`, `labelKey`, `descriptionKey` | `SystemSettingPort` + wizard `SystemSettingStep` |
| **`ui-customization.pool`** | Каталог UI-опций: `language`, `theme`, `fontScale`, `grid`, `screenLayout` (pick-from-bundled), `tileSet` (pick-from-bundled). Каждая запись имеет `kind` (`simple-choice` / `pick-from-bundled`), `criticality`, `defaultValue`, `choices` / `choicesFrom` | Wizard `UIChoiceStep` + `UserPreferences` |

**Эволюция кинов** (Article VII §10): набор kinds — не финальный. В будущем `screen.layout` может разбиться на `screen.shell` / `screen.grid` / `screen.intents`; могут появиться `theme.pool`, `hint.set`, etc. Любое изменение — через `schemaVersion` bump + backward-compat reads (CLAUDE.md rule 5). Удаление kind — one-way door (CLAUDE.md rule 3), требует exit-ramp документации.

**Что НЕ является отдельной схемой сейчас (но может появиться при необходимости):**

- ❌ `theme` как отдельная схема — пока запись в `ui-customization.pool` (choices: `light` / `dark` / `auto`). JSON-тема как отдельный kind появится, если возникнет требование «admin remote-меняет цвета primary user'у» или «marketplace тем».
- ❌ `hint.set` — runtime-подсказки post-wizard. Отдельный kind появится **тогда**, когда первый hint реально нужен в S-1 или позднее (rule 4 MVA).
- ❌ `permission.set` — это **шаг внутри** `wizard.manifest` (`stepType: SystemSetting` с `refId` ссылающимся на запись в `system-settings.pool`), а не отдельный документ.

---

## 4. Wire-format policy для всех схем семейства

### 4.1 Общий header

Каждый bundled JSON начинается с одинакового набора полей. Это **не envelope с `kind`-discriminator** (Kubernetes-style отвергнут — у нас не бывает смешанного `kubectl apply -f file.yaml`). Это **минимальный общий заголовок метаданных**, разные у каждой схемы по `body`.

```json
{
  "schemaVersion": 1,
  "id": "tile-set.classic-6",
  "name": "tile_set.classic_6.name",
  "description": "tile_set.classic_6.desc",
  "deviceClass": ["android-phone"],
  "body": { /* специфика kind'а */ }
}
```

| Поле | Тип | Назначение |
|---|---|---|
| `schemaVersion` | int | Версия схемы (rule 5). Bump'ится при breaking change в `body` |
| `id` | string (slug) | Стабильный глобально-уникальный идентификатор. Формат: `<kind>.<slug>` (например, `tile-set.classic-6`). **Не меняется** при правке. Версионирование — через `schemaVersion`, не через `id` |
| `name` | string (key) | **Ключ** к строке локализации (см. §6). Не литерал. UI показывает |
| `description` | string (key) | **Ключ** к строке локализации. UI показывает |
| `deviceClass` | string[] | Массив поддерживаемых классов: `"android-phone"`, `"android-tv"`, `"*"` (все). Для bundled phone-template — `["android-phone"]` |
| `body` | object | Специфика конкретного `kind`'а. Структура отличается у `wizard.manifest` / `screen.layout` / `tile.set` |

### 4.2 Поля, которые **не** добавляем в MVP

| Поле | Почему не сейчас | Когда появится |
|---|---|---|
| `author` | В MVP всё bundled от разработчика — поле без смысла | Когда появится community sharing (Phase 4+). Структурный: `{ kind: builtin \| user \| admin }` |
| `createdAt` | Никто не показывает дату; для bundled есть git-история | Когда появится community sharing |
| `minAppVersion` | Bundled живёт **внутри** APK — версии физически не разъезжаются | Когда появятся не-bundled источники (file import, network) |

Forward-compat readers (см. §4.3) сами справятся с появлением этих полей позже без break'а.

### 4.3 Compatibility policy

- **Additive changes** (новые поля, новые опциональные значения) → **forward-compatible reads**. Старая версия app читает новый JSON: знакомые поля парсит, незнакомые молча игнорирует. Это Kubernetes-стиль.
- **Breaking changes** (rename, remove, semantic change существующего поля) → **bump `schemaVersion`**. Старая версия app, столкнувшись с `schemaVersion > known`, **отказывается читать** с понятным сообщением для пользователя, не падает.

Это соответствует CLAUDE.md rule 5 (wire-format versioning) и подтверждено в discussion 2026-06-16.

---

## 5. Что отвергнуто архитектурно

Зафиксировано здесь, чтобы не пересматривать при каждом новом обсуждении.

### 5.1 Server-Driven UI (единая мега-схема, runtime-интерпретатор)

**Не делаем.** Причины:

- Нарушение CLAUDE.md rule 4 (MVA): преждевременная супер-абстракция при двух app-family.
- Lock-in в свой DSL вместо Compose; потеря IDE preview, autocomplete, type safety.
- Versioning hell: bump `schemaVersion` в одном месте требует backward-compat reads на всё.
- Известно дорого в поддержке (Airbnb / Lyft опыт).
- Performance cost на дешёвых Android-устройствах при cold start.

**Граница, которую держим:** JSON задаёт **данные** (какие плитки, какой layout, какие шаги). Compose задаёт **рендеринг**. Никаких `JsonToComposable` функций в `core/`.

### 5.2 Общий envelope с `kind`-discriminator

**Не делаем.** Кубернетесовский `apiVersion + kind` нужен потому, что один файл может содержать смесь Deployment / Service / ConfigMap. У нас документы загружаются разными путями (отдельные файлы в bundled, отдельные коллекции в Firestore позже) — путь сам говорит тип. `kind` избыточен.

### 5.3 Server-hosted templates в MVP

**Не делаем.** Причины:

- Конфликт с deferred-cloud principle ([decisions/2026-06-15-deferred-cloud/](decisions/2026-06-15-deferred-cloud/)) — каждое устройство самодостаточно, first install без сети должен работать.
- Bundled-в-APK + добавление `NetworkConfigSource` потом — это **addition**, не **rewrite** (rule 4 в чистом виде).
- JSON на public CDN = public wire-format с жёстким backward-compat commitment forever.

**TODO inline** в `BundledConfigSource.kt` фиксирует exit ramp на server-side источник, когда появится конкретный consumer (community catalog, admin-curated push). См. §7.

### 5.4 XP / reputation gates для публикации шаблонов

**Не обсуждаем в этой итерации.** XP-аргумент в discussion 2026-06-16 был приведён как пример «почему разделение схем удобно», не как product feature. Реальная XP-механика — отдельная дискуссия, не сейчас.

### 5.5 «Featured / curated / popular» selection при установке

**Сводим к bundled.** Разработчик (= автор репозитория) сам отбирает starter templates при сборке APK. Никакого ranking-алгоритма, никакой курации сервером, никакой community gallery в MVP.

---

## 6. Localization policy

Все user-facing строки в JSON — **ключи** к `strings.xml` / moko-resources, **не литералы**.

Пример:
```json
{
  "name": "tile_set.classic_6.name",         // ✅ ключ
  "description": "tile_set.classic_6.desc"   // ✅ ключ
}
```

**Не**:
```json
{
  "name": "Classic 6 plates",                // ❌ литерал — невозможно перевести
  "description": "Six basic tiles..."        // ❌ литерал
}
```

Причина: bundled template без ключей **не может быть многоязычным** — для каждого языка пришлось бы держать копию JSON. Через ключи — один JSON + переводы в общих string tables (10 supported languages: RU/EN/ES/ZH/AR/HI/PT/DE/FR/JA).

Применение ADR-004 (localization global readiness) + CLAUDE.md rule 9 (shareability через обезличенный format).

---

## 7. ConfigSource adapter pattern

Все три bundled JSON-схемы загружаются через **один** port в domain:

```
ConfigSource (port в core/wizard/, domain)
    └── BundledConfigSource (impl в app/, читает из APK assets / commonMain resources)
```

В тестах используется `FakeConfigSource` (in-memory).

**Inline-TODO в `BundledConfigSource.kt`** (фиксирует exit ramp согласно `feedback_exit_ramps_as_todos`):

```kotlin
// TODO(shareability): same JSON format will be served by future ConfigSource adapters
// without rewriting BundledConfigSource. Format must NOT change for these to plug in;
// only the source changes.
//   - FileConfigSource     — share intent / import from file picker
//   - NetworkConfigSource  — server-curated catalog (Phase 4+, см. docs/dev/server-roadmap.md)
//   - MarketplaceConfigSource — community gallery (Phase 5+)
//
// Identity-bound fields stay forbidden in shareable format (CLAUDE.md rule 9).
```

**Запись в [`docs/dev/server-roadmap.md`](../dev/server-roadmap.md)** появится при создании F-3 спеки:
- Trigger to add server-side template catalog: community sharing UI lands (Phase 4+).
- What ships: `NetworkConfigSource` adapter pulling identical JSON format from CDN.
- Bundled остаётся как offline fallback навсегда — это not replacement.

---

## 7a. Reusability discipline — extraction candidates

**Контекст** (discussion 2026-06-16): экосистема в будущем добавит ещё два приложения — Elderly-Friendly Messenger (V-2, Phase 4) и Full Family Album (V-3, Phase 4). Оба будут иметь **похожие** надстройки: first-run wizard, senior-friendly UI Kit (большие тапы, контраст), локализация на 10 языков.

**Решение:** в отдельный репозиторий **не выносим сейчас** (rule of three — extract after 2-3 реальных consumer'ов, не до). Но **проектируем** три модуля как **launcher-agnostic** с дня 1, чтобы будущий extract стоил один день, а не месяц.

### Три extraction candidate модуля

| Модуль | KMP target | Что внутри | Что НЕ должно быть внутри |
|---|---|---|---|
| **`core/wizard/`** | common | `WizardEngine`, `WizardStep` interface, generic steps (`PermissionStep`, `TextSizeStep`, `ThemeStep`, `GridSelectionStep`, `TutorialHintStep`), три JSON-схемы (`wizard.manifest`, `screen.layout`, `tile.set`), `ConfigSource` port | Launcher-specific шаги (ROLE_HOME, HomeScreen renderer), tile action handlers, launcher app navigation |
| **`core/localization/`** | common | Locale detection, string-table abstraction, fallback policy, CI fitness function для missing translations | Launcher-specific строки, app-specific resource bundles |
| **`core/ui-senior/`** | Compose Multiplatform | Senior-friendly примитивы: большие кнопки (≥56dp tap target), warm-contrast тема, font scaling utilities, accessibility wrappers | Launcher-specific Composables (HomeScreen, TileGrid), messenger-specific (ChatBubble), album-specific (PhotoGrid) |

### Launcher-specific остаётся в `app/`

| Где | Что |
|---|---|
| `app/` | Bundled JSON-файлы (`wizard.manifest` для Simple Launcher, конкретные `screen.layout` и `tile.set`), HomeScreen renderer, tile actions, ROLE_HOME shepherd, capability registry consumers |

### Fitness function (rule 7 — automatic check вместо manual review)

Lint rule в Gradle build: модули `core/*` **не могут** импортировать `app/*`. Любой такой импорт = build failure. Это и есть структурная гарантия, что extract возможен.

### Triggers для будущего extract

Extract в отдельный репозиторий **рассматривать** при появлении:

1. **Второго реального consumer'а** (messenger или album начал писать `core/wizard/` импорты). Не «спланирован», а уже импортирует.
2. **Третьего consumer'а** — это уже **обязательный** trigger (rule of three).
3. **Diverging release cadence** — launcher хочет release раз в две недели, messenger раз в полгода. Один monorepo тормозит обоих.

### Inline TODO в `core/wizard/`, `core/localization/`, `core/ui-senior/`

В корне каждого модуля (`README.md` или `package-info.kt`):

```
// EXTRACT CANDIDATE: this module is designed launcher-agnostic.
// Trigger for extract to shared library: second REAL consumer
// (ecosystem app actually importing this module, not "planned").
// Rule of three (Fowler): extract on second use, library on third.
// Until then: keep API surface narrow and launcher-agnostic.
//
// Sister extract candidates: core/wizard/, core/localization/, core/ui-senior/.
```

### Что отвергнуто

- ❌ Отдельный репозиторий сейчас — premature, ломает coordination между app'ами без compensating benefit.
- ❌ Maven/JitPack distribution до extract — добавляет CI overhead без consumer'а.
- ❌ Отдельный `docs/dev/extraction-candidates.md` — inline TODO достаточно, документ риск устареть.

---

## 8. Терминологические соответствия (legacy ↔ new)

При чтении старых документов / спек переводи термины так:

| В старых документах | Здесь и далее |
|---|---|
| «preset» (Simple Launcher / Admin App) | **preset** (значение сохранилось — это top-level shareable composition) |
| «preset» (2×3 / 3×4 / 4×5 grid) | **layout-grid** (параметр шага `GridSelectionStep` в wizard'е) |
| «preset» (starter «6 plates classic») | **`tile.set`** (одна из bundled JSON-схем) |
| «ConfigTemplate» (исторически F-3, ныне Backlog TASK-1) | разносится на **`tile.set` + `screen.layout`** |
| «ConfigDocument» (спека 008) | без изменений — это runtime-конфиг устройства |
| «named config» (спека 014) | без изменений — конкретный ConfigDocument admin'а с именем |
| «UX profile» (admin / senior, спека 014) | без изменений — режим редактирования внутри Admin App |
| «WizardManifest» (исторически F-3, ныне Backlog TASK-1) | **`wizard.manifest`** (bundled JSON-схема) |
| «TutorialHintManager» (исторически F-3, ныне Backlog TASK-1) | без изменений — runtime-компонент. Hint data в Phase 1 hardcoded; future `hint.set` schema — когда появится первый реальный hint |

---

## 9. Что осталось решить **в** F-3 спеке

Glossary фиксирует **термины и формат**, но не реализационные детали. Следующие вопросы решаются при написании spec.md F-3:

1. **Физическое размещение bundled JSON** в KMP-проекте: `core/wizard/src/commonMain/resources/` (через moko-resources) vs `app/src/main/assets/` (Android-only). Влияет на возможность тестировать на JVM.
2. **JSON Schema generation для CI**: ручные roundtrip-тесты или auto-generated JSON Schema files для проверки конкретных bundled JSON в CI.
3. **`actionType` в `tile.set`** — точное значение из capability registry (спека 005). Может требовать revisit спеки 005 для документации полного списка.
4. **Структура `wizard.manifest` `steps[]`** — формат записи step parameters (each `stepType` имеет свой parameter schema; либо typed sealed class, либо open `params: Map<String, Any>`).

---

## 10. Применение к существующим спекам

- **Спека 008 (ConfigDocument)** — без изменений, формат уже зафиксирован. Glossary только закрепляет имя «ConfigDocument» как термин, не переопределяет содержимое.
- **Спека 014 (Tile Editing)** — без изменений в коде F-014.0, но при будущем рефакторинге в P-3 (Phase 3) named-configs format должен быть совместим с этим glossary (header policy, localization keys).
- **Спека 010 (Setup Assistant)** — расширяется в S-1 через `wizard.manifest`. Текущий код 010 продолжает работать, S-1 переписывает его поверх Wizard Engine из F-3.
- **Backlog TASK-1 (бывший F-3) Acceptance Criteria** — обновится при работе с фичей: «ConfigTemplate» → «`tile.set` + `screen.layout`», ссылка на этот glossary.

---

## 11. F-CRYPTO — KeyBlob, ports, namespaces (spec 016)

`:core:crypto` (KMP-модуль `family.crypto.*`) — крипто-фундамент: 7 ports (`AeadCipher`, `AsymmetricCrypto`, `KeyDerivation`, `RandomSource`, `SecureKeyStore` через expect/actual, плюс stubbed `KeyRotation` / `KeyEscrow`).

- **KeyBlob** — wire-format файла обёрнутого ключа на диске (`<filesDir>/keys/<keyId>.blob`). JSON со `schemaVersion=1`, AES-256-GCM-wrapped private key bytes + IV + wrapKeyAlias. Полный контракт: [`contracts/key-blob-v1.md`](../../specs/016-f-crypto-core-module/contracts/key-blob-v1.md). Backward-compat fixtures **заморожены** на F-CRYPTO 1.0.0 release.
- **KeyNamespace** — 5 разрешённых префиксов идентификаторов: `config-`, `media-`, `messenger-`, `recovery-`, `__internal-`. `KeyId` — `@JvmInline value class` с compile-time валидацией.
- **Wrap pattern** — Android Keystore не хранит Curve25519 нативно; X25519/Ed25519 priv bytes оборачиваются AES-256-GCM ключом в TEE (StrongBox where available). Industrial paradigm (Signal Android, Bitwarden).
- **Validation set** — RFC KAT (7748, 8032, 8439 + XChaCha IETF draft, 5869) + property tests + Android Keystore instrumentation. См. [`docs/dev/crypto-review.md`](../dev/crypto-review.md).

---

## Резюме одной фразой для будущих агентов

> **Три bundled JSON-схемы** (`wizard.manifest`, `screen.layout`, `tile.set`), общий 6-полевой header, forward-compat readers, локализация через ключи, один `ConfigSource` port с одной `BundledConfigSource` реализацией в MVP, server-side источники — additive adapters позже без смены формата. **Три KMP-модуля** (`core/wizard/`, `core/localization/`, `core/ui-senior/`) проектируются launcher-agnostic как extraction candidates для будущей messenger / album экосистемы; lint rule запрещает `core/*` → `app/*` imports. **Слово «preset» не используется.**

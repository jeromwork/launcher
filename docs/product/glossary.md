# Glossary — конфиги, пресеты, визарды

**Дата**: 2026-06-16 · **Источник**: discussion 2026-06-16 (закрытие терминологических разночтений перед запуском F-3).

> Этот документ — **единственный источник правды** для терминов, которые в `roadmap.md` и use-cases исторически назывались по-разному (особенно «preset»). Любой новый артефакт (спека, ADR, decision doc) должен использовать **эти** имена. Если в roadmap.md есть устаревший термин — он будет правлен по мере касания, не одной волной.

---

## 1. Зачем словарь существует

В обсуждении выяснилось, что слово «**preset**» в наших же документах используется в **трёх разных смыслах**:

1. UX-family целого приложения (Simple Launcher, Admin App, TV).
2. Стартовый JSON-шаблон («6 plates classic», «9 plates + calendar»).
3. Параметр шага wizard'а (`2×3` / `3×4` grid layout).

Из-за этого обсуждение Phase 1 / F-3 каждый раз ломается о «какой именно preset мы имеем в виду». Glossary закрепляет однозначные имена. **Слово «preset» в новых документах не используется** — оно остаётся только в исторических разделах roadmap.md как обозначение того, что заменено более точным термином.

---

## 2. Три слоя архитектуры

| Слой | Что | Где живёт в коде | Когда строится |
|---|---|---|---|
| **A — App-family** | Тип целого приложения с собственным UI Kit, navigation, capabilities | `app/` (Compose, MVI presentation) | Simple Launcher — спека S-1; Admin App — спека S-2; TV / Caregiver — Phase 4+ |
| **B — Wizard (first-run flow)** | Универсальный движок шагов настройки | `core/wizard/` (KMP common) | Спека F-3 (Phase 1) |
| **C — ConfigDocument (runtime)** | Живой документ, описывающий текущее состояние устройства (плитки, контакты, виджеты) | `core/` (домен, уже существует, спека 008) | Уже есть. Спека 014 расширила до named configs |

**Ключевое правило:** Wizard **не редактирует** ConfigDocument напрямую. Wizard **производит первый** ConfigDocument из выбранного `tile.set` + ответов пользователя. Дальнейшее редактирование — спека 014 (Tile Editing), а не wizard.

---

## 3. Семейство bundled JSON-схем

В Phase 1 (F-3) появляются **три** независимые JSON-схемы. Это **семейство**, не единая мега-схема (Server-Driven UI отвергнут, см. §5). Каждая со своим `schemaVersion`, своим набором полей, своим roundtrip-тестом.

| Имя схемы | Описывает | Потребитель |
|---|---|---|
| **`wizard.manifest`** | Порядок шагов first-run flow для одной app-family. Поля: `appFamilyId`, `steps[]` с `stepType` и параметрами | `WizardEngine` |
| **`screen.layout`** | Каркас экрана: размер grid'а (`gridRows × gridCols`), bottom toolbar, переключаемые табы. **Не содержит плиток** — только структуру | `HomeRenderer` (Compose в `app/`) |
| **`tile.set`** | Обезличенный стартовый набор плиток: `tiles[]` с `position`, `actionType` (см. capability registry, спека 005), `labelKey`, `iconKey` | First-run после wizard'а; создаёт первый ConfigDocument |

**Что НЕ является отдельной схемой в Phase 1:**

- ❌ `theme` — темы code-driven через Compose `MaterialTheme`. JSON-тема появится только когда возникнет требование «admin remote-меняет цвета бабушке» (не сейчас).
- ❌ `hint.set` — runtime-подсказки post-wizard. Отдельный `kind` появится **тогда**, когда первый hint реально нужен в S-1 или позднее. Преждевременно зашивать в F-3.
- ❌ `permission.set` — это **шаг внутри** `wizard.manifest` (`PermissionStep` с параметром «какое разрешение»), а не отдельный документ.

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
| «preset» (Simple Launcher / Admin App) | **app-family** |
| «preset» (2×3 / 3×4 / 4×5 grid) | **layout-grid** (параметр шага `GridSelectionStep` в wizard'е) |
| «preset» (starter «6 plates classic») | **`tile.set`** (одна из bundled JSON-схем) |
| «ConfigTemplate» (в roadmap.md F-3) | разносится на **`tile.set` + `screen.layout`** |
| «ConfigDocument» (спека 008) | без изменений — это runtime-конфиг устройства |
| «named config» (спека 014) | без изменений — конкретный ConfigDocument admin'а с именем |
| «UX profile» (admin / senior, спека 014) | без изменений — режим редактирования внутри Admin App |
| «WizardManifest» (roadmap.md F-3) | **`wizard.manifest`** (bundled JSON-схема) |
| «TutorialHintManager» (roadmap.md F-3) | без изменений — runtime-компонент. Hint data в Phase 1 hardcoded; future `hint.set` schema — когда появится первый реальный hint |

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
- **roadmap.md F-3 prompt (строки 853-915)** — будет правлен **отдельным коммитом** перед запуском `/speckit.specify`: «ConfigTemplate» → «`tile.set` + `screen.layout`», ссылка на этот glossary.

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

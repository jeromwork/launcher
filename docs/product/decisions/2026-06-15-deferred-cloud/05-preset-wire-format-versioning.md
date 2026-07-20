# 05 — Preset Wire Format Versioning (forward-compat to Phase 3 Preset Depth)

**Status**: ACCEPTED 2026-06-15 · **исторический документ, не источник правды по версионированию**

> **Читать как запись решения от 2026-06-15, а не как действующие правила.** Целочисленные версии
> (`schemaVersion: 1`, `2`) в тексте ниже — форма, действовавшая до TASK-138. Сейчас у каждого
> формата **три** поля версии в виде точечной строки, а отказ читателя происходит по
> `minReaderVersion`, не по `schemaVersion`. Действующие правила — [`wire-format.md`](../../../architecture/wire-format.md),
> он выигрывает при любом расхождении (§12). Документ намеренно не переписан: он фиксирует, что
> и почему решили тогда.

## Принцип

> **MVP Phase 2 использует упрощённый preset (`schemaVersion: 1`). Phase 3 MVP Preset Depth расширит его до `schemaVersion: 2` без поломки Phase 2 кода.**

Преsety в [CLAUDE.md rule 9](../../../../CLAUDE.md) и [Article VII Profile-Driven](../../../../.specify/memory/constitution.md) обещают быть полноценной конфигурационной системой с wizard'ами, platform-specific шагами, adaptive UX и т.д. **Это Phase 3 работа**. В Phase 2 нужен **только** простой preset (раскладка плиток + тема + шрифт) — но он должен быть **forward-compatible** с тем, что появится в Phase 3.

## Phase 2 preset (`schemaVersion: 1`)

Минимальная структура:

```json
{
  "schemaVersion": 1,
  "name": "default",
  "tiles": [...],
  "theme": "warm-light",
  "fontScale": 1.0,
  "grid": "3x4"
}
```

**В local mode**: ровно один такой config с `name: "default"` в DataStore (per [decision 02](02-config-ownership-per-device.md)).
**В cloud mode**: до 5 таких configs в namespace юзера.

## Phase 3 preset (`schemaVersion: 2`)

Расширенная структура с явным разделением:

```json
{
  "schemaVersion": 2,
  "name": "default",
  "platformAgnostic": {
    "tiles": [...],
    "theme": "warm-light",
    "fontScale": 1.0,
    "grid": "3x4"
  },
  "platformSpecific": {
    "android": {
      "wizardSteps": [...],
      "requiredAndroidIntents": [...],
      "deepIntegrationFlags": {
        "blockNotificationDrawer": true,
        "disableHorizontalSwipe": true,
        "hideSettingsBehind7Tap": true
      }
    },
    "androidTv": { ... },
    "ios": { ... }
  },
  "adaptiveProfile": "default" | "tremor-mild" | "tremor-severe" | "vision-impaired" | "perception-impaired",
  "optionalSteps": [
    { "id": "...", "label": "...", "completed": false }
  ]
}
```

## Backward-compat rules

### Phase 3 reader на Phase 2 documents (`v1 → v2`)

- **MUST** уметь читать `v1` documents без ошибки.
- При чтении `v1`: автоматический lift в `v2` shape:
  - `tiles, theme, fontScale, grid` → `platformAgnostic.{...}`
  - `platformSpecific` → `{}` (пустой)
  - `adaptiveProfile` → `"default"`
  - `optionalSteps` → `[]`
- Никаких записей `v1` после Phase 3 деплоя — Phase 3 reader при первом write **обновляет** документ до `v2`.

### Phase 2 reader на Phase 3 documents (`v2 → v1`)

- Phase 2 reader **не обязан** уметь читать `v2`. Это нормально per [CLAUDE.md rule 5](../../../../CLAUDE.md) — `MAJOR` версия может ломать.
- **Server-side graceful degradation**: если Phase 2 client читает namespace, где есть `v2` configs, Cloudflare Worker отдаёт только `platformAgnostic` часть как `v1`, остальное игнорируется.
- Это **не data loss**: данные на сервере сохраняются как `v2`, просто Phase 2 client их не видит полностью.

### Тесты обязательные с Phase 2

В Phase 2 каждая S-спека, работающая с preset'ом, использует **только публичные accessors** через `ConfigDocument` API:
- `config.tiles` — работает в обоих версиях.
- `config.platformAgnostic.tiles` — в v1 wrapper возвращает то же самое, в v2 — прямой доступ.

Прямой доступ к JSON полям типа `config.json["tiles"]` — **запрещён**. Refuse-pattern, который ловит `checklist-wire-format`.

**Roundtrip тесты в Phase 2**:
- write v1 → read v1 → asserts equal.
- write v1 → simulate Phase 3 read (lift to v2) → read v2 → assert semantics preserved.

**Roundtrip тесты в Phase 3**:
- write v2 → read v2 → assert equal.
- read existing v1 fixture → assert lift to v2 produces expected structure.
- write v2 → Phase 2 client read → assert получает только `platformAgnostic` поля.

## Что **не** делаем в Phase 2

- ❌ Не выставляем `platformSpecific`, `adaptiveProfile`, `optionalSteps` поля даже как пустые. **Лишние поля = шум.** Они появятся только в `v2`.
- ❌ Не пишем код против будущей схемы. Если Phase 2 спека хочет нечто, что есть только в `v2` (например, adaptive profile) — это **сигнал, что фича для Phase 3**, не для Phase 2.
- ❌ Не используем feature flags вида «if preset.adaptiveProfile != null». Этого поля нет в Phase 2 wire format.

## Что **делаем** в Phase 2

- ✅ Используем `ConfigDocument` API только через публичные accessors.
- ✅ Roundtrip-тест v1 в каждой Phase 2 спеке.
- ✅ Inline TODO у каждого preset-touch site: `// TODO(P-1-schemaVersion-2): when Phase 3 P-1 bumps preset to v2, ensure this path uses platformAgnostic accessor.`

## Это применение

- [CLAUDE.md rule 5](../../../../CLAUDE.md) — wire-format versioning + backward-compat reads + migration written before breaking change ships.
- [CLAUDE.md Refuse #6](../../../../CLAUDE.md) — schema field renamed without migration → refuse.
- [Article VII §3](../../../../.specify/memory/constitution.md) — versioning, validation, backward-compatibility rules.
- [Article VII §7](../../../../.specify/memory/constitution.md) — AI-generated changes MUST NOT silently widen config schema without updating schema docs, validation, migration, examples, tests. **Phase 3 P-1 спека обязана сделать всё пять.**

## Exit ramp

Если Phase 3 P-1 окажется, что `v2` структура неправильная (например, разделение platformAgnostic/Specific окажется ложным разделением) — bump до `v3` следуя тем же правилам. Это **stays additive**, не deletion.

Если хочется удалить поле из `v2` — это **deprecation flow**:
1. Phase 3 minor: пометить поле deprecated в schema docs.
2. Следующая major (`v3`): полностью убрать. Reader `v3` всё ещё умеет читать `v2`.

**Никогда не удалять поле в той же major версии**.

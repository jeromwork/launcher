# Checklist: wire-format — спек 006

**Spec:** [`spec.md`](../spec.md) · **Run:** 2026-05-09 (post-clarify, post-deferral) · **Score:** 7 ✓ / 8 ◐ / 3 N/A / 0 ✗

Source: [CLAUDE.md rule 5](../../../CLAUDE.md), Article VII §3 [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md).

Legend: `[x]` pass · `[~]` partial · `[ ]` fail · `N/A` not applicable.

---

## Wire formats введённые этим спеком

| # | Wire format | Кто читает / пишет | Назначение |
|---|-------------|-----|-----|
| W1 | `Capability` | DataStore persist + future Firestore export (007) | Per-provider snapshot |
| W2 | `Health` | DataStore persist + future Firestore export (007) | Per-device snapshot |
| W3 | `LauncherSettings` | DataStore persist + future Firestore sync (008) | Banner toggles |
| W4 | `iconId` namespace string (`bundled:` / `custom:` / `private:`) | Capability field, IconStorage port | Routing key для иконок |
| W5 | `Action` (cleanup-only) | в этом спеке — удаление `migrateLegacyAction` | Touched, не вводится |

---

## Schema version

- [x] **CHK001** `schemaVersion: Int` с первого коммита для всех wire formats.
  - W1: FR-006. W2: FR-013. W3: FR-034. W4: namespace-разделение служит de facto «версионированием». W5: spec 005 уже имеет.
- [~] **CHK002** `schemaVersion` читается первым при десериализации.
  - **Finding:** не задокументировано в spec; стандартная практика kotlinx.serialization читает поля в порядке data class declaration.
  - **Fix:** plan.md явно указывает «`schemaVersion` declared first in data class, parser respects order».
- [~] **CHK003** SUPPORTED_SCHEMA_VERSION constant single source of truth.
  - **Finding:** spec не требует `companion object` constant как в спеке 005 (`Action.SUPPORTED_SCHEMA_VERSION`).
  - **Fix:** добавить **FR-041** в spec: «Each wire-format type MUST expose `SUPPORTED_SCHEMA_VERSION: Int` companion constant».

## Backward compatibility

- N/A **CHK004** Чтение предыдущих версий.
  - W1/W2/W3 — **первая** версия в спеке 006. Backward-compat applies starting спек 007.
- [~] **CHK005** Добавление поля + missing fields с defaults.
  - **Finding:** FR-006 говорит «backward-compat reader» implicit, но не указывает механизм («@Serializable defaults»).
  - **Fix:** добавить **FR-042**: «Optional fields use `@Serializable` defaults; deserializer handles missing fields without exception».
- [x] **CHK006** Rename/remove требует migration written before.
  - В спеке 006 ничего не renaming/removing в новых типах (первая версия). Removal `migrateLegacyAction` это удаление **читалки**, не схемы.
- [x] **CHK007** Migration code scoped.
  - Removed `migrateLegacyAction` was scoped. Никаких новых migrations.

## Forward compatibility

- [~] **CHK008** Чтение newer schemaVersion gracefully.
  - **Finding:** спек не задаёт политику для `schemaVersion > SUPPORTED`. Спек 005 C1 решал аналогично («parse never fails, surface at consume time»).
  - **Fix:** добавить **FR-043**: «Reader for `Capability`/`Health`/`LauncherSettings` MUST NOT throw on `schemaVersion > SUPPORTED_SCHEMA_VERSION`. Resulting object is parsed best-effort; consumers may downgrade behaviour. Aligns with spec 005 C1».
- [~] **CHK009** Unknown discriminator → graceful.
  - W4 (`iconId` namespace): FR-009 explicit «unknown namespace returns `Placeholder`» ✓
  - W2 (`Health.connectivity` enum `Wifi | Mobile | None`): что если в v2 появится `Vpn`? Не задокументировано.
  - **Fix:** добавить **FR-044**: «Unknown enum values in `Health.connectivity` deserialize to `None` (safe fallback)».

## Tests

- [x] **CHK010** Roundtrip test для каждого wire-format.
  - SC-003 explicit покрывает W1, W2, W3.
- N/A **CHK011** Backward-compat test (read previous schema version).
  - W1/W2/W3 — первая версия. Применимо со спека 007.
  - **Recommend forward pattern:** добавить **SC-015** «forward-compat test: reader handles `schemaVersion: 999` + unknown fields without crash» — готовит инфраструктуру backward-compat.
- [~] **CHK012** Fixtures stored as files (not literal strings).
  - **Finding:** спек 005 задал паттерн `core/src/commonTest/resources/fixtures/action-wire-format/`. Спек 006 не указывает аналогичную директорию.
  - **Fix:** добавить **FR-045**: «Fixtures stored in `core/src/commonTest/resources/fixtures/capability-wire-format/`, `…/health-wire-format/`, `…/launcher-settings/` (one file per scenario)».

## Persistence specifics

- [~] **CHK013** Keys namespaced.
  - **Finding:** спек упоминает ключи `capability_snapshot_json` без namespace. Спек ИДЁТ к 6 контекстам (snapshot, health, settings, future remote storage…) — namespace критически важен.
  - **Fix:** добавить **FR-046**: «DataStore Preferences keys MUST be namespaced as `com.launcher.<feature>.<key>` (например `com.launcher.capability.snapshot_v1`, `com.launcher.health.snapshot_v1`, `com.launcher.settings.banners_v1`)».
- N/A **CHK014** SQLDelight migrations.
  - SQLDelight не используется в спеке 006.
- [x] **CHK015** Removed stored type — one-shot cleanup + grep-anchor.
  - `migrateLegacyAction` removal explicit (FR-035..039), grep-anchor `LEGACY-BRIDGE-EXPIRES-IN-SPEC-006` documented.

## Deep-link / QR / exported config

- N/A **CHK016** URL/QR payload schemaVersion.
  - Спек 006 не вводит deep-link/QR.
- [x] **CHK017** Corrupted payload → user-facing error, no crash.
  - Edge Cases: «DataStore-проекция повреждена → fallback в empty snapshot». ✓

## Contract folder

- [~] **CHK018** `contracts/` per wire-format file.
  - **Finding:** спек 006 НЕ создаёт `contracts/` директорию (как делал спек 005).
  - **Fix:** speckit-plan создаёт `specs/006/contracts/`:
    - `capability-wire-format.md` — semantic version, fields, breaking-change policy, link to fixtures.
    - `health-wire-format.md` — то же.
    - `launcher-settings-wire-format.md` — то же.
    - `icon-id-namespace.md` — convention `<namespace>:<name>`, semantic invariants.

---

## Open items для spec.md / plan.md / contracts/

**Add to spec.md before speckit-plan:**

- **FR-041** SUPPORTED_SCHEMA_VERSION companion constant per type (CHK003).
- **FR-042** Missing-field defaults via @Serializable (CHK005).
- **FR-043** Reader policy for `schemaVersion > SUPPORTED` — parse-don't-throw (CHK008).
- **FR-044** Unknown enum values in `Health.connectivity` → `None` fallback (CHK009).
- **FR-045** Fixtures location convention (CHK012).
- **FR-046** DataStore key namespace convention `com.launcher.<feature>.<key>` (CHK013).
- **SC-015** Forward-compat test: reader handles `schemaVersion: 999` + unknown fields (CHK011).

**For speckit-plan to create:**

- `specs/006/contracts/` directory with 4 contract files (CHK018).

**For plan.md to document:**

- `schemaVersion` field declaration order (first in data class) — CHK002.

## Itog

- 7 PASS, 8 PARTIAL (5 fixable добавлением FR в spec, 3 — артефакты в plan.md / contracts/), 3 N/A, 0 hard FAIL.
- **Verdict:** wire-format дисциплина в спеке хорошая на стратегическом уровне (везде schemaVersion, везде roundtrip), но **много implicit политик**. Перед speckit-plan: добавить 6 FR + 1 SC, чтобы explicit'но зафиксировать политики чтения, namespace, fallbacks. Это **one-way door** характеристики — поправить в спеке 007 будет дороже.

---

## TL;DR для нетехнического читателя

Wire-format — это **формат данных, который покинет устройство** (синхронизируется в облако в спеке 007) или **переживёт обновление приложения** (хранится на диске бабушкиного телефона). Любая ошибка в формате сейчас = миграция всех бабушкиных телефонов потом.

**Что хорошо в спеке 006:**
- Каждая структура данных имеет «номер версии» (`schemaVersion`) с самого начала. Если в будущем формат изменится, мы знаем версию старых данных и сможем правильно их прочитать.
- Все три формата (карточка приложения / здоровье устройства / настройки) обязаны иметь тесты «записал → прочитал → проверил что осталось то же». Без этого данные могли бы тихо ломаться.
- Папка для тестовых фикстур (заранее заготовленных образцов данных) переиспользует подход спека 005.

**Что нужно дополнить перед фазой плана:**
- Явно указать, что у каждой структуры есть **константа версии** в коде (один источник правды, чтобы не было волшебных чисел разбросанных по проекту).
- Явно описать **политику для будущих версий**: если бабушкин телефон с новым лаунчером встретит данные от ещё более нового лаунчера (ситуация маловероятная, но возможная при синхронизации) — что делать? Пропустить непонятное и продолжить, или показать ошибку? Принимаем ту же политику, что в спеке 005: пытаемся прочитать сколько можем, не падаем.
- Явно описать, что если в `Health.connectivity` появится новое значение (например `Vpn` в будущем), старая бабушкина версия читает его как `None` — безопасный fallback.
- Использовать **префиксы для ключей** в локальном хранилище (`com.launcher.capability.snapshot_v1` вместо просто `capability_snapshot`), чтобы не было конфликтов с другими частями приложения.
- Создать отдельную папку `specs/006/contracts/` с подробными контрактами для каждого формата (как в спеке 005).

Это всё **уточнения**, не пересмотр. Архитектура в порядке.

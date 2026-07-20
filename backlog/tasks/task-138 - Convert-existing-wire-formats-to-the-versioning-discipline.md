---
id: TASK-138
title: Convert existing wire formats to the versioning discipline
status: In Progress
assignee: []
created_date: '2026-07-20 05:46'
updated_date: '2026-07-20 07:13'
labels:
  - wire-format
  - migration
  - phase-2
milestone: m-2
dependencies:
  - TASK-16
  - TASK-140
priority: high
ordinal: 138000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

TASK-16 записала правила версионирования форматов данных в один документ — [`docs/architecture/wire-format.md`](../../docs/architecture/wire-format.md). Но сам код этим правилам пока не следует: форматы, написанные до документа, хранят версию целым числом (`schemaVersion: 2`) и не имеют двух новых полей, из которых приложение понимает, можно ли этот документ читать и можно ли его перезаписывать.

Эта задача — привести код в соответствие с документом.

**Что происходит по шагам (для одного формата):**
1. Целое число превращается в точечную строку с тем же или большим номером: `2` → `"2.0"`. **Уменьшать нельзя никогда** (правило I3 в документе — иначе устройства, читающие этот формат, начнут ошибаться молча).
2. Добавляются два поля: `minReaderVersion` (ниже неё приложение отказывается читать) и `minWriterVersion` (ниже неё — открывает только на просмотр, без сохранения).
3. Обновляются тестовые файлы-образцы (фикстуры) и тесты round-trip (запись → чтение → сравнение).
4. Если формат лежит в облаке — правится серверное правило безопасности Firestore для его коллекции.

**Про серверное правило.** Сейчас правила Firestore сравнивают версию **как число** и запрещают её уменьшение (защита от отката на старый формат). Строка так сравниваться не может: при посимвольном сравнении `"10.0"` окажется меньше `"9.0"`. Поэтому каждый облачный формат при конвертации требует правки правила и прогона его тестов.

**Порядок работы — по одному формату, не всё разом.** Документ разрешает существующим форматам переезжать «при следующем касании». Эта задача просто доводит переезд до конца целенаправленно, но по-прежнему формат за форматом, с зелёными тестами после каждого. Один большой коммит на 170 файлов не ревьюится и не откатывается.

## Зачем

Пока код и документ расходятся, документ бесполезен: агент, открывший `Preset.kt` и увидевший `schemaVersion: Int = 2`, сделает по образцу и напишет следующий формат так же. Расхождение самовоспроизводится.

Плюс сейчас в коде уже есть несогласованность **сама с собой**, независимо от TASK-16: FCM-пейлоад пишет версию строкой `"1"`, все остальные форматы — числом. То есть «единого текущего состояния» нет, и его нужно привести к одному виду.

## Что входит технически (для AI-агента)

Объём по результатам инвентаризации (2026-07-20):

- **~30 Kotlin-классов** с полем версии: `com.launcher.api` (17 — Action, Capability, Health, LauncherSettings, ConfigDocument, ConfigSnapshot, NamedConfig, LinkBootstrap, StateApplied, PairingWireFormat, PushPayload, SessionRecord, DocSnapshot и др.), `com.launcher.preset.model` (4 — Pool, Preset, Profile, VendorRecipeCatalogue), `core:push` (2), `core:crypto` (4 — EncryptedEnvelope, DeviceIdentity, KeyBlob, EscrowBundle), `core:keys` (2 — Envelope, RecoveryKeyBackupBlob).
- **~28 числовых констант** с разъехавшимися именами (`SCHEMA_VERSION`, `CURRENT_SCHEMA_VERSION`, `SUPPORTED_SCHEMA_VERSION`, `MAX_SUPPORTED_SCHEMA_VERSION`, `SCHEMA_VERSION_V1`, `SUPPORTED_SNAPSHOT_SCHEMA_VERSION`) → свести к трём именам из документа §11.
- **~65 JSON-файлов** (12 production-ассетов в APK + ~52 тестовые фикстуры + 1 worker-фикстура).
- **~46 тестовых файлов.** Особо: `WireFormatJsonTest` и `ProfileSchemaV2RoundtripTest` проверяют версию как **незакавыченное** число в JSON-тексте — сломаются гарантированно.
- **2 TypeScript-файла** в worker'ах + типы окружения.
- **Firestore security rules** + их тесты: `rules.test.ts` (`schemaVersion_cannot_decrease_on_update`), `rules.f5.recovery.test.ts`, `rules.f5b.envelope.test.ts` — все сравнивают численно.
- **`Envelope.kt`** содержит `require(schemaVersion >= 1)` — числовое сравнение, ломается о строку.
- Заодно разобраться с двумя находками инвентаризации: `theme-catalog.json` и `hint-pool.json` несут `schemaVersion`, который **не читает ни один production-класс** (объявлен только в тестовом DTO); и два параллельных дерева bundled-пресетов на разных версиях (`app/src/main/assets/preset/bundled-presets/*` = 2 против `core/src/androidMain/assets/presets/*` = 1) — либо мёртвый код, либо рассинхрон.

Порядок: сначала форматы без облака (проще, тесты локальные), потом облачные — там к каждому прилагается правка Firestore rules.

## Состояние

**Draft.** Заблокирована TASK-16 — сначала документ и чистка дубликатов, потом конвертация по нему. Владелец решил (2026-07-20) взять эту задачу сразу после закрытия TASK-16.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Все production-классы с версией формата используют точечную строку и несут `minReaderVersion` + `minWriterVersion`; номер ни у одного формата не уменьшился относительно отгруженного
- [ ] #2 [hand] Константы версий сведены к трём именам из `wire-format.md` §11, по одной на формат, объявлены рядом с типом; литералов версий в местах вызова не осталось
- [ ] #3 [hand] Все JSON-ассеты и фикстуры обновлены; roundtrip- и backward-compat-тесты зелёные
- [ ] #4 [hand] Firestore security rules и их тесты обновлены под строковую версию; запрет отката версии сохранён и проверяется тестом
- [ ] #5 [hand] Проверка версий в `ArchitectureFitnessTest` расширена до строгого режима (все три поля, порядок, golden-фикстура) и `./gradlew fitnessCheck` зелёный на всей кодовой базе
- [ ] #6 [hand] Разобраны две находки инвентаризации: непрочитываемый `schemaVersion` в `theme-catalog.json` / `hint-pool.json`, и расхождение двух деревьев bundled-пресетов
<!-- AC:END -->

## Implementation Plan
<!-- SECTION:PLAN:BEGIN -->

Полная инвентаризация выполнена 2026-07-20 (шесть параллельных срезов по коду, ассетам, тестам, серверу, докам, бэклогу). Ниже — состояние на входе и порядок работ.

### Фундамент (сделан)

Модуль `:core:wire` — `WireVersion` (точечная строка, сравнение по частям), `WireVersionHeader` + `accessFor()` (три исхода §3), `UnknownWireVersionException` / `CorruptWireFormatException` (§4/§8). Лист графа зависимостей: `core:crypto` и `core:push` — листья со своими форматами, `core` их не видит, общего места не было.

### Что нашлось сверх исходной оценки задачи

1. **Кросс-языковой фитнес-чек T402 не существует.** Спека 019 и комментарии в `wire-format.ts` / `WireFormatVersion.kt` ссылаются на CI-скрипт, сверяющий константу в TypeScript с константой в Kotlin. Его нет: `T402` в `specs/019/tasks.md` не отмечен, реализация в git не коммитилась, в `core/push/build/` лежат только устаревшие артефакты давнего локального прогона. Реально существуют два независимых теста, каждый сверяет свою сторону с литералом `1` и другую сторону не читает. То есть расхождение Kotlin ↔ TypeScript сегодня не ловится ничем.
2. **Монотонность версии в Firestore rules — вопрос безопасности, а не совместимости.** Пять правил сравнивают версию через `>=` (`firestore.rules:139, 159, 413, 429, 470`). Строковое сравнение лексикографическое: на `"9.0"` → `"10.0"` легальное обновление будет отвергнуто, а откат `"10.0"` → `"9.0"` — разрешён. Строка 413 — это защита H-2 от downgrade-атаки на vault восстановления (FR-028a): нельзя дать атакующему с украденной сессией подменить новый vault старым уязвимым. Наивная конвертация ломает именно её.
3. **Второе дерево bundled-пресетов мёртвое.** `core/src/androidMain/assets/presets/*.preset.json` — другая форма документа (`abstractProfile`, `poolVersion`), `schemaVersion: 1`, добавлено один раз в `a1799b3` и с тех пор ни одной строкой Kotlin не читается. Живое дерево — `app/src/main/assets/preset/bundled-presets/*` (`schemaVersion: 2`), его открывает `BundledPresetSource`. Мёртвое дерево удаляем, а не конвертируем.
4. **У формата Profile две разные константы.** `Profile.CURRENT_SCHEMA_VERSION = 2` и приватная `SUPPORTED_SCHEMA = 1` в `ProfileEngine.kt:18`, которая гейтит чтение `default_profile.json`. Расхождение существует до этой задачи; конвертация обязана его снять, а не перенести.
5. **Три места пишут версию в обход типов** — `GoogleSignInAuthAdapter.kt:189, 278, 290` (ad-hoc Firestore-мапы) и `WorkerPushSender.kt:48` (ручной JSON). После смены типа компилятор их не поймает: grep'ом, не типами.
6. **FCM-пейлоад уже строка** (`"1"`), остальные форматы — числа. Асимметрия существует независимо от этой задачи.
7. **Legacy-воркер `push-worker/` жив** параллельно с `workers/push/`. Плюс вторая кросс-языковая пара: `workers/backup` ↔ `RecoveryKeyBackupBlob`.
8. **Фикстуры `config-snapshot-v1.json` нет на диске**, хотя `specs/009/tasks.md` и контракт заявляют её как закоммиченную.
9. **§11 требует roundtrip + golden-корпус на каждый формат.** Сегодня roundtrip-теста нет у: `NamedConfig`, `StateApplied`, `ConfigDocument`, `LauncherSettings`, `Link`, `LinkBootstrap`, `Health`, `Capability`, `Action`, `PushPayload` (api). Golden-фикстуры нет у большинства из них плюс у `Pool` и `Profile`. Конвертация формата = момент, когда эти тесты дописываются.
10. **Нарушения §12 в доках остались**: `docs/product/glossary.md:63-92, 122-125` по-прежнему нормативно утверждает Int и пересказывает правила эволюции; `docs/product/decisions/2026-06-15-deferred-cloud/05-preset-wire-format-versioning.md` целиком дублирует дисциплину и не имеет штампа-указателя.
11. **Размещение версии в зашифрованных форматах уже верное** (§7) — во всех пяти (`Envelope`, `EncryptedEnvelope`, `KeyBlob`, `RecoveryKeyBackupBlob`, `EscrowBundle`) версия лежит открытым полем рядом с шифротекстом, а `RecoveryBlobCodec` читает её до полного разбора. Нужна только смена типа, не переезд поля.
12. **Push сегодня fail-soft** (входящая версия выше поддерживаемой → `null`, тихо), §4 требует fail-closed с типизированной ошибкой. Разрешается так: типизированная ошибка + лог, сервис сообщений не падает — «не угадываем форму» соблюдено, поведение для пользователя не меняется.

### Принятые решения по ходу

- **Firestore rules сравнивают разобранные части, а не строку**: `int(v.split('\\.')[0])` для MAJOR, затем MINOR. Проверяется на эмуляторе; если CEL не потянет — запасной вариант отдельное числовое поле сортировки рядом с версией. Правило 413 обязано остаться защитой от отката, с тестом именно на границу `9.0` → `10.0`.
- **`theme-catalog.json` / `hint-pool.json`**: версию не просто конвертируем, а начинаем читать в `ThemeCatalog` / `BundledHintPoolSource`. Иначе задача меняет данные, не меняя поведения, и «непрочитанное поле» переезжает под новым именем.
- **Порядок**: один формат = один коммит = зелёные тесты. Сначала необлачные, потом облачные (каждый облачный тянет правку rules + прогон `firestore-tests`).

### Порядок конвертации

Волна 1 (необлачные, тесты локальные): `Action` · `Capability` · `Health` · `LauncherSettings` · `Pool` · `Preset` · `Profile` (+ снятие расхождения с `ProfileEngine`) · `VendorRecipeCatalogue` · `theme-catalog` · `hint-pool` · `NamedConfig` · `SessionRecord`.

Волна 2 (крипто, локальные тесты + фикстуры): `KeyBlob` · `EncryptedEnvelope` · `DeviceIdentity` · `Envelope` · `RecoveryKeyBackupBlob` · `EscrowBundle`.

Волна 3 (облачные, каждый с правкой `firestore.rules` и её тестами): `ConfigDocument` · `ConfigSnapshot` · `PairingWireFormat` · `LinkWireFormat` · `LinkBootstrap` · `StateApplied` · `PushPayload` (api) · `family.push` + `workers/push` + `workers/backup`.

Волна 4 (закрытие): строгий режим `ArchitectureFitnessTest` (AC #5) · реальный кросс-языковой чек вместо несуществующего T402 · зачистка §12-нарушений в доках · удаление мёртвого дерева пресетов.

**Прогон rules-тестов**: `cd firestore-tests && npm test` (поднимает эмулятор Firestore+Auth по `../firebase.json`, проект `demo-test`; нужны Node 20+, JDK 21+).

<!-- SECTION:PLAN:END -->

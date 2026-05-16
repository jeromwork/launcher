# Research: Spec 009 — Admin Mode Flows

**Date**: 2026-05-15
**Spec**: [spec.md](spec.md)
**Plan**: [plan.md](plan.md)

Эта папка фиксирует one-way doors спека 009 (admin-mode-flows). Для каждого
архитектурного решения, которое будет дорого откатить через 3-6 месяцев,
зафиксированы: что выбрали, какие альтернативы рассмотрели и почему отвергли,
при каких сигналах решение пересматриваем, и какая «выходная рампа» если
ошиблись. По CLAUDE.md §3 ни одна one-way дверь не открывается без exit ramp.

---

## R-001. Local edit draft persistence — Room (PendingLocalChanges reuse)

**Context**: admin вносит правки в свой локальный draft до того, как нажмёт
«Push на сервер». Между правками может пройти процесс-смерть, reboot,
переключение устройства. Где хранить draft?

### Decision

**Room — reuse `PendingLocalChanges` таблицы из спека 008.** Draft хранится
локально per (linkId, deviceId), переживает process death, не реплицируется
между admin-устройствами одного admin'а.

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **Room reuse (chosen)** | — |
| B | In-memory ViewModel state | Process death во время правки = потеря несохранённой работы. Senior-safe: пожилой admin может уйти и вернуться через час. Article IV §5 (state survival). |
| C | Server-side draft collection (`/drafts/{linkId}/{deviceId}`) | Каждый keystroke (debounced 300ms) бьётся в Firestore → расход read/write quota Spark plan; sync между admin-устройствами одного admin'а — сейчас НЕ требуется (Clarifications C3); over-engineering без use case. |

### Regret conditions

- Если в спеке 010+ потребуется «начал править на телефоне → продолжил на
  планшете того же admin'а» — multi-device admin draft sync.
- Если draft conflict между admin-tablet и admin-phone одного admin'а станет
  частым явлением (≥ 10% сессий правок).

### Exit ramp

Migration to server-side draft: добавить collection `/drafts/{linkId}/{deviceId}`
+ Firestore listener в `ConfigEditor`. Room остаётся как L1 cache. Wire-format
расширение, добавление поля — additive, без миграции существующих данных.
Стоимость: ~1 неделя. **TODO-FUTURE-SPEC: multi-device admin draft sync**
оставлен inline в `ConfigEditor.kt` (per memory `feedback_exit_ramps_as_todos`).

---

## R-002. ConfigSnapshot dual schemaVersion — envelope + nested config

**Context**: `ConfigSnapshot` (объект истории, FR-036-038) содержит:
конфигурационный документ + метаданные снапшота (timestamp, author, label).
Что версионировать?

### Decision

**Two independent `schemaVersion` fields**: один на envelope (snapshot
метаданные), другой на nested config (тот же `schemaVersion` что в
`/config/current` из спека 008).

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **Dual independent versions (chosen)** | — |
| B | Single shared `schemaVersion` field на envelope | Связывает два независимых эволюционных трека. Spec 010 (multi-admin) может изменить envelope (добавить authorList) не трогая nested config. Spec 011 (contacts) изменит nested config не трогая envelope. Single field форсирует версионный bump в обе стороны при любом изменении. |
| C | Versionless envelope (snapshot = just config + opaque metadata) | Нарушает CLAUDE.md §5 — envelope persistится в Firestore, переживает app upgrades, **обязан** иметь schemaVersion. |

### Regret conditions

- Если за следующие 2 года ни envelope ни nested config никогда не bump'нутся
  независимо (всегда вместе) — то двойная версионность была излишней.
- Если transformer chain (lazy migration R-012) станет настолько связан, что
  логически version-pair приходится повышать одновременно.

### Exit ramp

Collapse to single field: новый snapshot пишется с `schemaVersion = N`, читатель
интерпретирует это как и envelope, и config одновременно. Старые snapshots с
парой версий читаются через `if (hasField("envelopeSchemaVersion")) { useDual()
} else { useSingle() }`. Additive change, без потери данных. Стоимость: ~2 дня.

---

## R-003. Contact validation strategy — universal contract + per-provider adapters

**Context**: FR-046-050 описывают добавление контактов (телефон, WhatsApp,
Telegram, …). У каждого источника свои правила: phone — E.164; WhatsApp —
тоже E.164; Telegram — `@username` ИЛИ `+phone`; LINE — internal id; etc.

### Decision

**Universal contract `Contact.fromRaw(rawInput, source): Outcome<Contact,
ValidationError>` в domain + per-provider adapter implementations.** Validation
errors как sealed class в domain (`InvalidPhoneFormat`, `MissingUsername`,
`UnsupportedSource`, …); per-source правила инкапсулированы в adapter, домен
работает с уже распарсенным `Contact`.

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **Universal `Contact.fromRaw` + per-provider adapters (chosen)** | — |
| B | Per-provider validation, каждый messenger со своим типом результата (`PhoneContact`, `TelegramContact`, …) | Domain код раздувается; ContactList становится `Map<Source, List<*>>`; diff/merge UI требует per-type branching. |
| C | Generic `Map<String, String>` identifiers без типизации | Нарушает CLAUDE.md §1 (transport-shaped wire в domain); компилятор не ловит ошибки; senior-safe валидация в UI становится ad-hoc. |

### Regret conditions

- Если правила реально расходятся настолько, что parser нельзя собрать в один
  ACL-слой (например, LINE требует API call для validation вместо локального
  regex).
- Если ValidationError начинает фрагментироваться так, что общий sealed type
  теряет общий смысл (≥ 8 per-source-specific variants).

### Exit ramp

Расширить `ValidationError` sealed type per source (добавить
`ValidationError.Source.Line(...)`, `Source.WhatsApp(...)`, …). Контракт
`Contact.fromRaw` остаётся, signature не меняется — только структура error type.
Additive change. Стоимость: ~1 день на каждый новый messenger.

---

## R-004. Drag-and-drop — Compose 1.6+ built-in `Modifier.dragAndDropSource/Target`

**Context**: FR-008-009 требуют drag-and-drop для переноса плиток между flows.
Compose 1.6 (Aug 2024) ввёл первоклассный API; раньше делали через
`pointerInput` руками.

### Decision

**Compose built-in `Modifier.dragAndDropSource` + `Modifier.dragAndDropTarget`.**
Two-way door (см. exit ramp).

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **Compose built-in DnD (chosen)** | — |
| B | Custom `pointerInput { detectDragGestures(...) }` + manual hit-testing | Больше кода, больше bugs (long-press cancel, scroll conflicts с LazyColumn); мы НЕ имеем нестандартных требований к DnD. CLAUDE.md §4: «удалить абстракцию — что потерять?» — здесь fewer LOC = меньше тестов = быстрее. |
| C | Сторонняя библиотека (`reorderable`, `compose-dnd`) | Vendor lock, transitive deps, NFR-001 frame budget неизвестен; built-in доступен и documented. |

### Regret conditions

- NFR-001 (60 fps frame budget на Pixel 4a) fails на drag сессии с ≥ 30
  плитками на экране.
- Bug в Compose DnD пересекается с LazyColumn scroll так, что нельзя fix без
  upstream patch.
- Cross-flow drag (плитка из flow A в flow B через scroll-to-flow) ломается
  на Android 11/12.

### Exit ramp

Two-way door: DnD логика инкапсулирована за `Modifier`-level surface; rewrite
на `pointerInput { detectDragGestures }` не трогает domain или ViewModel
contracts. FR-008/FR-009 остаются без изменений. Стоимость: ~3-5 дней на
полную замену.

---

## R-005. History writes — client-side (без Cloud Function)

**Context**: FR-036 — каждый push в `/config/current` создаёт snapshot в
`/configHistory/{linkId}/{snapshotId}`. Кто пишет: клиент или Cloud Function?

### Decision

**Client-side write.** Тот же admin, который пушит `/config/current`, в той же
не-транзакционной паре пишет snapshot. Принимается race condition (редкая
потеря snapshot если два admin одновременно пушат) + spoofing risk
(злонамеренный admin может записать fake snapshot) с mitigation через
Firestore Security Rule FR-045a (только authorUid == request.auth.uid + поле
serverTimestamp != null).

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **Client-side (chosen)** | — |
| B | Cloud Function trigger on `/config/current` write → атомарно создаёт snapshot | Требует Blaze plan. Memory `feedback_exit_ramps_as_todos` + project_backlog: TODO-ARCH-003 (Spark→Blaze upgrade). Spec 009 на Spark plan по defaults. |
| C | Firestore transaction `runBatch { write config + write snapshot }` | Не атомарно по разным collections в Firestore Web/Mobile SDK (transactions работают только в одном document path для writes без recently-read constraint — не подходит для нашего случая с разными collections). |

### Regret conditions

- Если в pre-production observability покажет: ≥ 5% snapshots потеряны из-за
  race condition.
- Если security audit (или incident) покажет реальный spoofing/PII
  tampering — фейковая история становится атакой на elderly persona
  (запутывание).
- Если Blaze upgrade случится по independent причине — переходим на Cloud
  Function для всех server-side обязанностей разом.

### Exit ramp

**TODO-ARCH-003 (server-side migration)** в `docs/dev/project-backlog.md`:
migrate `/configHistory` writes на Cloud Function, payload = ConfigSnapshot
envelope, trigger = Firestore on-write. Domain port `HistoryRecorder` остаётся
неизменным (см. R-001 в plan.md), меняется только adapter. Стоимость:
~2-3 недели (включая Blaze setup, function deployment, IAM, тесты).

---

## R-006. History housekeeping — client per-push

**Context**: FR-038 — максимум 10 snapshots per linkId. Кто удаляет 11-й при
push'е 11-го?

### Decision

**Client per-push housekeeping.** Тот admin, который пушит, в дополнение к
write нового snapshot читает список существующих и удаляет старейший если
count > 10.

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **Client per-push (chosen)** | — |
| B | Firestore TTL policy | TTL работает по `expireAt` timestamp, не по count. Нам нужно «last 10 by count», а не «last 30 days». Comlex hybrid не оправдан. |
| C | Server-side cron (Cloud Function scheduled) | Требует Blaze. Тот же TODO-ARCH-003 как R-005. Cron lag (1h scheduled minimum on Spark если бы он был доступен) — temporary 11+ snapshots. |
| D | Не housekeeping вообще, держать unbounded | Документ size growth → list reads дороже; storage cost; UI history list захламляется. FR-038 явно требует cap. |

### Regret conditions

- Если admin реально достигает ≥ 10 versions per day и housekeeping latency
  blocks push UI (push занимает > 1.5s из-за extra read+delete).
- Если concurrent housekeeping (два admin пушат одновременно, оба пытаются
  удалить тот же old snapshot) приводит к race с delete-not-found errors > 1%.

### Exit ramp

**TODO-ARCH-003 / SRV-CONFIG-002**: переход на server-side cron при Blaze
upgrade. Domain port `HistoryHousekeeper` остаётся, adapter меняется на
no-op (server делает). Стоимость: ~1 неделя поверх Blaze setup.

---

## R-007. Phone health monitoring — local UI types (НЕ domain `MonitorIndicator`)

**Context**: FR-018-020 описывают индикаторы «здоровья телефона» (заряд, signal,
data, …). Это монитор + UI компонент. Где живёт абстракция?

### Decision

**Local `PhoneHealthIndicator` UI types в module `app/health-ui/`** (или
аналог), НЕ в domain. Domain имеет только raw signals (`BatteryLevel: Percent`,
`SignalStrength: Bars`), UI слой их группирует и рисует.

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **Local UI types (chosen)** | — |
| B | Domain `MonitorIndicator` sealed type для unified «часы + health + sensors» | Premature abstraction (CLAUDE.md §4 Test 1). Сейчас единственный consumer — health UI. «Часы» уже есть в спеке 003 как самостоятельный компонент; объединение лишь усложняет тесты без выигрыша. |
| C | Каждый indicator (battery, signal, data) как separate UI component с raw signal in props | Слишком гранулярно для UI слоя; общий стиль/layout/contrast не enforced. |

### Regret conditions

- Если за следующий год в коде появятся ≥ 2 РЕАЛЬНЫХ source types помимо
  battery/signal/data — например, wearable HR sensor, ambient light, fall
  detection — и каждый дублирует UI плотно.
- Если accessibility (TalkBack semantics) требует unified «health summary»
  announcement для всех indicators сразу.

### Exit ramp

Extract domain `MonitorIndicator` sealed class (или interface) когда появляется
≥ 2 реальных source types вне health-ui. Existing health UI рефакторится через
`when (indicator)` branches; raw signals остаются в domain. Стоимость: ~3-5
дней refactor + тесты.

---

## R-008. VCard parser — hand-written ~100 LOC

**Context**: FR-046 — admin может shared VCard из messenger app → launcher
парсит и предлагает добавить контакт. Готовая lib (ezvcard) или своё?

### Decision

**Hand-written парсер ~100 LOC в androidMain, поддерживает FN + TEL only.**

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **Hand-written ~100 LOC (chosen)** | — |
| B | `ezvcard` library (~250 KiB APK + transitive deps) | APK budget (TODO-ARCH-006, ~3-4 MiB cap). Поддерживает RFC 6350 во всей полноте — overkill: нам нужны 2 поля. CLAUDE.md §4 — Test 1: «что потеряем без библиотеки?» — спec-compliance for PHOTO/ADR/BDAY (currently not in requirements). |
| C | Android ContactsContract API на receive Intent | Не парсит raw VCard text; работает только если shared через Contacts app intent, что не покрывает messenger share workflows (WhatsApp/Telegram share VCard как plain text payload). |

### Regret conditions

- Если admin сообщества начнут shared VCards с кастомными extensions
  (X-WHATSAPP, X-TELEGRAM-ID, PHOTO с base64), и эти данные нам полезны.
- Если spec 011 (contacts) расширит набор полей до ≥ 5 (org, address, email),
  парсер вручную становится noisy.

### Exit ramp

Подключить `ezvcard` через ACL adapter (`VCardLibParser implements
VCardParser`), сменить DI binding. Existing parser остаётся как fallback для
маленьких payload. Стоимость: ~2 дня (dependency + ACL + tests + APK
re-measure).

---

## R-009. Update cadence для health monitoring — listener-only when screen open

**Context**: FR-020 переписан per Clarifications: индикаторы здоровья
обновляются через listener когда экран открыт, не через polling.

### Decision

**Firestore listener (или local OS broadcast для battery/network) активен
только когда health screen открыт.** Никакого 30s polling.

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **Listener-only when screen open (chosen)** | — |
| B | 30s polling для Info-tier + listener для Warning/Critical-tier | Искусственное разделение; tier меняется динамически (battery 25% Info → 14% Warning); сложность переключения режимов на каждое значение. |
| C | Realtime listener forever (background) | Battery cost: Firestore listener держит open WebSocket → ~2-3% батареи/день впустую если health screen не открыт. Article IX §3 budget violation. |
| D | OS broadcasts only (BatteryManager, ConnectivityManager) без Firestore | Не работает для server-derived signals (custom alerts от admin'а). Часть FR-019 — custom warnings. |

### Regret conditions

- Если battery telemetry покажет listener cost > 1% per session > 10min.
- Если admin требование появится «healh push notifications когда экран
  закрыт» — потребуется FCM, отдельная архитектура.

### Exit ramp

Two-way door: переключение на polling-only либо на FCM-driven push при закрытом
экране — оба требуют изменения только adapter в androidMain. Domain port
`PhoneHealthSource: Flow<PhoneHealth>` остаётся. Стоимость: ~3 дня.

---

## R-010. VCard intake Activity launch mode — `singleTask` + `onNewIntent`

**Context**: VCard intent (см. R-008) приходит снаружи (через `ACTION_VIEW`
text/vcard или `ACTION_SEND` MIME). Какой launch mode у receiving Activity?

### Decision

**`singleTask` + `onNewIntent` override.** Если launcher уже в foreground —
intent доставляется существующему instance; если нет — стартует новый task.

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **`singleTask` + `onNewIntent` (chosen)** | — |
| B | `standard` (default) | Каждый share создаёт второй instance Activity в task switcher; пользователь видит «launcher» дважды; senior-safe нарушение (confusion). |
| C | `singleInstance` | Activity в собственном task, не разделяет state с rest of launcher; backstack ломается; over-restrictive для use case. |
| D | `singleTop` | Создаёт новый instance если на top стоит другая Activity — не предотвращает дубли в общем случае. |

### Regret conditions

- Если в спеке 012+ добавится второй entry point (например, deeplink из
  notification) и оба должны не толкаться в одном task.
- Если onNewIntent edge cases (intent extras lost on rotation) приведут к
  потере shared VCard.

### Exit ramp

Two-way door: разделение на specialized `VCardIntakeActivity` (singleTask) +
main launcher Activity (singleTask) в same task affinity. Backstack handled
explicitly. Стоимость: ~2 дня. Manifest change + minor Activity refactor.

---

## R-011. History UI — full screen (не bottom sheet, не sidebar)

**Context**: FR-039 — admin может просмотреть историю снапшотов и rollback.
UI surface?

### Decision

**Full screen history view.** Содержит timeline (10 snapshots), preview layout
(превью flows той ревизии), action «Откатить на эту ревизию».

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **Full screen (chosen)** | — |
| B | Bottom sheet (модальный) | Preview раскладки flows не помещается на bottom sheet (требует ~ 60% экрана только под mini-preview). UX сжатый. |
| C | Sidebar / split-view | Pattern для планшета/desktop; spec 009 primary device — phone (admin-phone). Не масштабируется на 5"-6" экраны. |
| D | Inline list внутри admin mode screen | Перегружает admin mode screen; rollback action путается с edit actions. |

### Regret conditions

- Если usage analytics покажет: admin открывает history > 5 раз в день и
  full-screen transition (animation cost) становится pain point.
- Если для tablet support full-screen окажется проигрышным vs split-view.

### Exit ramp

Two-way door: переход на bottom sheet с inline preview thumbnail (mini-grid
вместо full layout). UI module change only, domain `ConfigHistoryView` port
остаётся. Стоимость: 2-3 дня (включая thumbnail rendering).

---

## R-012. Schema invalidation для старых snapshots — lazy transformers

**Context**: snapshot V5 в `/configHistory` может быть прочитан клиентом V7
(после двух schema bumps). Что делать?

### Decision

**Lazy transformers**: `SnapshotMigrator.migrate(raw, fromVersion, toVersion):
ConfigSnapshot` цепочкой `vN → vN+1 → ... → vCurrent`. Transformer на каждом
шаге additive (читает старый, дописывает defaults для новых полей). Кодируем
в `TODO-ARCH-015 (snapshot migrators)` в backlog для tracking.

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **Lazy transformers (chosen)** | — |
| B | Drop incompatible snapshots на schema bump | Теряет историю при каждом version bump. Senior-safe нарушение: «откатил месяц назад» становится недоступно через 3 апдейта. CLAUDE.md §5: backward-compat reads required for ≥ 1 major release. |
| C | Eager mass migration: при загрузке всех snapshots переписываем v(N) → vCurrent в Firestore | Storage write quota Spark plan; transient inconsistency если migration fails mid-way; complexity. |

### Regret conditions

- Если transformer chain становится длинной (≥ 4 hops vN → vN+4) и сложной,
  каждый transformer накапливает edge cases.
- Если в transformer'е появляется data loss (не additive change), что
  нарушает roundtrip test.

### Exit ramp

Drop policy для очень старых snapshots: snapshots старше 90 дней И с
schemaVersion delta > 1 major — удаляются с UI warning «эти ревизии больше
недоступны». Wire-format не меняется. Стоимость: ~3 дня (policy + tests +
UI string). Документировать в backlog как **TODO-ARCH-017**.

---

## R-013. UI components extension — single component + `editMode` boolean

**Context**: `TileCard`, `FlowScreen`, etc. сейчас рендерят launcher (read-only
view). Admin mode добавляет редактирование (drag handles, кнопки delete,
inline edit). Расширять existing components или дублировать?

### Decision

**Extension через `editMode: Boolean` parameter (или `mode: TileMode` enum)
на existing component.** Один источник правды для layout, типографики,
contrast, accessibility semantics.

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | **`editMode` parameter on existing (chosen)** | — |
| B | Дублирование: `EditableTileCard`, `EditableFlowScreen`, `EditableHomeScreen` рядом с view-only | Двойной maintenance; senior-safe contrast/spacing/tap target drift between them; double tests. |
| C | Wrapper pattern: `EditableTileCard { ViewTileCard(...) }` — internal compose | Не решает проблему: edit-specific affordances (drag handle, remove X) должны быть позиционно внутри карточки, не overlay. |

### Regret conditions

- Если внутри `TileCard` (или другого component) количество `if (editMode)`
  branches превышает ~5 — code complexity становится hard to read.
- Если accessibility-semantics для view vs edit модов начнут сильно расходиться
  (read mode = `Button`, edit mode = `Container with drag affordance`).

### Exit ramp

Refactor в three-layer: `BaseTileCard` (shared layout/style) + `ViewTileCard`
(уровень над, view-only) + `EditTileCard` (уровень над, edit affordances).
Existing call-sites обновляются через простой rename. Стоимость: ~1-2 дня на
component, ~3-5 дней на весь UI слой.

---

## Summary

- **R-001 (Draft в Room)**: одна точка хранения draft per device; sync между
  admin-устройствами — future spec.
- **R-002 (Dual schemaVersion)**: envelope и nested config — независимая
  эволюция.
- **R-003 (Universal Contact contract)**: общий `fromRaw`, per-source adapters,
  sealed error type.
- **R-004 (Compose built-in DnD)**: two-way door, fallback на `pointerInput`
  готов.
- **R-005 (Client-side history writes)**: accepted spoofing risk через
  Security Rule; TODO-ARCH-003 — server migration через Blaze.
- **R-006 (Client per-push housekeeping)**: same backlog, same exit ramp как
  R-005.
- **R-007 (Local health UI types)**: domain `MonitorIndicator` отложен до
  появления ≥ 2 реальных source types.
- **R-008 (Hand-written VCard parser)**: ~100 LOC, FN + TEL only; ezvcard в
  exit ramp.
- **R-009 (Listener-only when screen open)**: battery budget compliant; FCM
  для closed-screen — отдельный feature.
- **R-010 (`singleTask` + `onNewIntent`)**: один instance launcher'а в task
  switcher, корректный intent delivery.
- **R-011 (Full-screen history)**: preview раскладки помещается; bottom sheet
  в exit ramp.
- **R-012 (Lazy snapshot transformers)**: TODO-ARCH-015 для tracking;
  drop-policy для > 90 days в TODO-ARCH-017.
- **R-013 (`editMode` parameter)**: single source of truth для layout/a11y;
  three-layer refactor в exit ramp если ветвления вырастут.

Все 13 решений имеют документированную exit ramp; ни одна one-way door не
открывается без плана отката (CLAUDE.md §3).

---

<!-- novice summary -->

## TL;DR

Этот файл — рабочая записка инженеров: какие развилки в дизайне спека 9
дорого переделывать через полгода, поэтому мы их фиксируем сразу.

Главные решения:

1. **Черновик правок** держим на устройстве (Room из спека 8), не на сервере
   — синк между устройствами одного admin'а пока не нужен.
2. **Версия снапшота** двойная — отдельно у конверта, отдельно у вложенного
   конфига. Так спеки 010/011 смогут менять одно не трогая другое.
3. **Контакты** валидируются единым контрактом `Contact.fromRaw` с
   per-messenger адаптерами внутри. Domain работает с уже распарсенным.
4. **Drag-and-drop** — встроенный Compose 1.6 API, без сторонних библиотек.
5. **История** пишется клиентом (без Cloud Function), 10 последних версий;
   старые лежат до schema-несовместимости (тогда мигрируем на лету).
6. **Здоровье телефона** мониторим только когда экран открыт, чтобы не
   жечь батарею.
7. **VCard** парсим вручную ~100 строк (FN + TEL), без библиотеки.
8. **Admin-mode UI** — те же компоненты что и view-mode, но с флагом
   `editMode`, чтобы не дублировать стили и accessibility.

Главные риски: spoofing истории (mitigation через Firestore Security Rule),
рост сложности transformer chain снапшотов через несколько version bumps
(mitigation — drop policy для > 90-day снапшотов), и frame budget DnD на
Pixel 4a (mitigation — fallback на ручной pointerInput).

Большинство решений — two-way doors с готовым exit ramp в 1-5 дней работы.
Серьёзный one-way door один: миграция истории на server-side требует
Blaze plan (TODO-ARCH-003) и стоит ~2-3 недели.

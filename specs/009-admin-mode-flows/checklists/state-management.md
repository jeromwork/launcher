# Checklist: state-management

**Spec**: `spec.md` (rev. 1 — pre-specify scope discovery 2026-05-15, batch 1 C1–C5 clarification applied)
**Run**: 2026-05-15 — `/speckit.clarify` post-pass before `/speckit.plan`.

Verifies Article IV §5 and §III.3 of [constitution.md](../../../.specify/memory/constitution.md) — state survival across Android lifecycle events. Aligned with project rules 1, 2, 6 of [CLAUDE.md](../../../CLAUDE.md).

Reference: [spec 008 state-management.md](../../008-bidirectional-config-sync/checklists/state-management.md).

---

## State inventory in spec 009

| # | State piece | Scope | Persistence layer | Surviving events |
|---|---|---|---|---|
| S1 | Managed list (paired devices) | feature-level | Firestore SDK cache + Room (inherits спека 7) | rotation ✅, process death ✅, reboot ✅ |
| S2 | `Health` snapshot для каждого Managed (list view) | feature-level | Firestore SDK cache (read-only) | rotation ✅, process death ✅ (stale cache), reboot ✅ (stale) |
| S3 | Currently-selected Managed (which one editor is editing) | screen / navigation | nav arg `linkId` (must survive rotation) | rotation ✅ если в SavedStateHandle, process death ✅ если в Bundle |
| S4 | Local edit draft per-Managed (FR-014a) | feature-level | **Room** (`PendingLocalChanges`, inherits спека 8) | rotation ✅, process death ✅, low-memory kill ✅, reboot ✅ |
| S5 | Currently-edited tile form state (open dialog, partial input) | screen | `rememberSaveable` / SavedStateHandle | rotation ✅ если saveable, process death — see below |
| S6 | Drag-and-drop in-progress state (tile being dragged, drop preview) | UI-local | `remember` + gesture state | rotation: **interruption acceptable**, process death: ephemeral |
| S7 | Phone health Firestore listener subscription (FR-020) | screen-scoped | listener lifecycle bound to Composable / ViewModel | rotation: re-attach, process death: ephemeral re-subscribe on relaunch |
| S8 | History UI — selected snapshot for preview | screen | `rememberSaveable(snapshotId: String)` | rotation ✅, process death — re-open «История» (acceptable) |
| S9 | Merge UI state (when conflict on publish) | screen | inherits спека 8 S5 — `rememberSaveable` + ViewModel | inherits спека 8 verdict |
| S10 | VCard intent payload (incoming share from WhatsApp/Telegram) | activity / process | Intent extras (Bundle, ≤10 KB per FR-028) | rotation: SavedStateHandle re-reads intent extras, process death: re-launch from intent |
| S11 | Pending Managed-selection from VCard flow (FR-029 dropdown choice) | screen | `rememberSaveable` | rotation ✅, process death: re-do share (acceptable, rare) |
| S12 | `READ_CONTACTS` permission grant state | OS-level | system permission manager | rotation/process death/reboot ✅ (OS-managed) |
| S13 | Snapshot history list (read from `/config/history/*`) | feature-level | Firestore SDK cache | rotation ✅, process death ✅, reboot ✅ |
| S14 | `PhoneHealthCriticalEvent` emission state (FR-021) | feature-level | local event bus, no persistence (no subscriber in spec 9) | all: ephemeral by design |

---

## Lifecycle events

- [ ] **CHK001 — Behaviour after Activity recreation (rotation, language change, dark/light theme switch, density change) explicitly specified**

  **Finding: PARTIAL.**

  ✅ **Implicitly handled**:
  - S1, S2, S13 (Firestore SDK cache + Room) — survive recreation; re-reads return same data.
  - S4 (local draft in Room per FR-014a) — **explicitly** survives recreation. Edge case 5 + US-1 scenario 5 cover «admin вышел на главный экран и вернулся» — draft restored.
  - S12 (permission) — OS-managed, immune to Activity recreation.

  ⚠️ **NOT explicitly specified in spec.md**:
  - **S5 (tile-edit form state)**: пользователь открыл диалог редактирования плитки `Call` (выбран контакт, изменил label, ещё не нажал «применить к draft»). Rotation — что? Spec не уточняет. **Recommendation**: либо continuous-autosave-to-S4-Room (consistent с FR-014a philosophy), либо `rememberSaveable` для form fields. **Action для plan.md**: granularity FR-014a — обновляется ли S4 на **каждое** изменение формы (autosave per keystroke), или **только** на «применить к draft»? Spec 008 имел тот же gap (CHK001 там).
  - **S6 (drag-and-drop in-progress)**: rotation посередине drag — стандартный Compose pattern says drag interrupted, plitka returns to original position. Это **acceptable** для drag UX (Android system behavior), но spec не upominает. **Action для plan.md**: явно сказать «drag interruption на configuration change — acceptable, плитка возвращается в исходную позицию; финальный drop требует завершения жеста».
  - **S7 (Firestore listener for Warning/Critical)**: FR-020 говорит «listener когда экран открыт» и «закрытие экрана = listener закрывается», но не уточняет recreation. **Issue**: если listener bound к `viewModelScope` и rotation re-creates ViewModel — есть okno без подписки. **Action для plan.md**: явно — listener в `viewModelScope` (survives configuration change через `ViewModel.onCleared`-survival), не в Composable scope.
  - **Language change**: spec 009 имеет много hardcoded русских текстов в FR (FR-026 «Не удалось добавить контакт», FR-028, FR-031, FR-033c). Это **должно быть строковые ресурсы**, не hardcoded. Inherits localization checklist concern, но релевантно и здесь — language change повторно резолвит string resources → UI must use `stringResource()`. **Action для plan.md**: confirm — все error/rationale тексты через `stringResource()`.

- [x] **CHK002 — Behaviour after process death specified**

  ✅ **Хорошо покрыто через спек 8 inheritance**:
  - FR-014a explicitly: «локальный draft хранится в Room (`PendingLocalChanges`), survives process kill / app restart / экран закрылся».
  - US-1 scenario 5: «admin вышел на главный экран не нажав Опубликовать, возвращается в редактор → локальный draft восстанавливается».
  - Edge case 5: same — explicit «локальный draft восстанавливается».
  - S13 (history) — Firestore cache survives, persistent.
  - S2 (health snapshot) — Firestore cache survives; on relaunch shows stale «3 hours ago (last known)», FR-022 explicit.

  ⚠️ **Watch**:
  - **S7 (listener) после process death**: при relaunch — listener must re-subscribe **только если экран Health-detail открыт**. Стандартный pattern: subscribe in `onStart`, unsubscribe in `onStop`. Spec не уточняет. Inherits Android Compose lifecycle pattern. **Action для plan.md**: явно DisposableEffect или collectAsStateWithLifecycle.
  - **S10 (VCard intent)**: process killed while VCard share UI is open (FR-029 «add contact: choose Managed» screen). Intent extras — survive в Bundle через SavedStateHandle. **Action для plan.md**: VCard parse result хранится в SavedStateHandle (≤ 10 KB per FR-028, within Bundle limits ✅).

- [ ] **CHK003 — Behaviour after low-memory kill (foreground process trimmed)**

  ✅ Same as CHK002 для S1/S4/S13 — Room / Firestore cache survives.

  ⚠️ **Watch — Xiaomi/Huawei aggressive task killer**:
  - **S6 (drag mid-action)**: process killed mid-drag — drop event never delivered, draft не обновлён → плитка остаётся в исходной позиции в S4 Room. **Acceptable** (user re-attempts drag). **Action для plan.md**: confirm «drag commit к Room происходит **только при drop**, не при start» — иначе плитка может оказаться «висящей» в промежуточном состоянии.
  - **S5 (tile-edit form mid-input)**: low-memory kill пока юзер набирает label плитки. **GAP** — spec не уточняет, autosave-per-keystroke к S4 Room или нет. Если нет — user теряет ввод. Same issue как CHK001. Особенно болезненно на Xiaomi MIUI где «свернул на 2 минуты» = kill.
  - **S7 (listener) при low-memory + Managed device critical**: admin foregrounded, экран Critical-health открыт, listener активен. Low-memory kill → listener потерян, health update missed. На relaunch — `lastSeen` re-read из cache (FR-022 «(последняя известная)»), listener re-subscribes. **Window of missed updates: from kill until relaunch**. Spec не упоминает. **Acceptable** для admin-UX (admin сам перезапускает приложение увидеть свежие данные), но запросить confirmation в plan.md.
  - **Doze/AppStandby (Android 6+, intensifies on Xiaomi)**: Firestore listener в фоне может быть отбрасывался даже без kill. Per FR-020 — listener активен только когда экран открыт, так что Doze не релевантен для **этого** спека. Push-уведомление при closed app — `TODO-ARCH-012` / `SRV-MONITOR-001`, OUT-009.

- [x] **CHK004 — Behaviour after device reboot specified**

  ✅ Room (S4 draft) survives reboot. Firestore SDK cache survives reboot.

  ⚠️ **Edge case**: после reboot Managed listener возобновляется при первом open экрана админ-UI (per FR-020 «когда экран открыт»). До этого момента — `Health.lastSeen` показывает «(последняя известная)» через cache. **Acceptable** (spec уже покрывает через FR-022).

## State scope

- [ ] **CHK005 — For each piece of state: scope explicitly chosen**

  **Finding: PLAN.MD ACTION**

  Spec.md не указывает scope для каждого state piece — это уровень plan.md. Recommended:

  | State | Recommended scope | Backing |
  |---|---|---|
  | S1 Managed list | feature-level (Koin singleton) | Firestore SDK + LinkRepository |
  | S2 Health per Managed (list view) | feature-level | Firestore SDK (read-only) |
  | S3 selected linkId | screen-scoped nav arg | SavedStateHandle |
  | S4 local draft per linkId | **feature-level** (FR-014a explicit) | **Room** `PendingLocalChanges` (re-used from спека 8) |
  | S5 tile-edit form transient | screen-scoped ViewModel + autosave to S4 OR `rememberSaveable` | **decision needed** in plan.md |
  | S6 drag state | UI-local `remember` + Compose gesture | n/a (ephemeral) |
  | S7 health listener subscription | screen-scoped `viewModelScope` | Firestore SDK realtime |
  | S8 history preview selection | screen-scoped + `rememberSaveable(snapshotId: String)` | derived from S13 |
  | S9 merge UI | inherits спека 8 | inherits спека 8 |
  | S10 VCard intent payload | activity-scoped via SavedStateHandle | Intent + parsed VCard (≤10 KB) |
  | S11 VCard→Managed selection | screen-scoped + `rememberSaveable` | n/a |
  | S12 permission | OS-managed | n/a |
  | S13 history list | feature-level | Firestore SDK cache |
  | S14 critical event | feature-level event bus | n/a (no subscriber) |

  **Action для plan.md**: записать эту таблицу как нормативную.

- [x] **CHK006 — No use of process-singleton state for things that should be screen-scoped**

  ✅ Spec.md не предлагает process-singletons для transient state. S7 listener должен быть screen-scoped (per FR-020 «когда экран открыт») — **must not** быть в `applicationScope`. **Watch для plan.md**: явно не делать listener process-wide.

- [ ] **CHK007 — No use of `rememberSaveable` for non-trivial / large objects (Bundle limits)**

  ⚠️ **Watch для plan.md**:
  - **S4 (draft)**: НЕ передавать `Config` (со всеми flows/slots/contacts) через `rememberSaveable` — это потенциально 100+ KB, превышает Bundle limit (~500 KB total, но Android docs рекомендуют < 50 KB на screen). Right pattern: **только `linkId: String`** через SavedStateHandle, фактический read через Room.
  - **S10 (VCard payload)**: 10 KB enforced через FR-028 — **OK** для Bundle, но **на грани**. **Action для plan.md**: confirm VCard payload size limit ≤ 10 KB и явно ссылаться на это как Bundle-safe.
  - **S13 (history snapshots)**: список snapshot'ов до 10 elements (per FR-038). Каждый snapshot может быть до ~50 KB (если конфиг большой). 10 × 50 KB = 500 KB — **превышает Bundle**. **Right pattern**: список snapshot **IDs** в `rememberSaveable`, фактические данные через Firestore cache.
  - **S8 (selected snapshot preview)**: `rememberSaveable(snapshotId: String)` — OK. Сам snapshot content читается из Firestore cache.

## Recreation correctness

- [x] **CHK008 — No "first-only" navigation logic**

  Spec.md не описывает navigation flow detail. **Watch для plan.md**: при VCard share intent (FR-027..030) — переход в editor Managed не должен зависеть от «впервые ли launched». Стандартный pattern: deep-link через `SingleLiveEvent` или `Channel`, не state flag.

  **Особенно критично для Q-OPEN-2**: spec оставляет открытым — VCard intent открывает редактор «в новой Activity или встраивает в backstack». **Action для plan.md**: ответить на Q-OPEN-2; рекомендуется **встроить через NavHost** в существующий backstack (admin pressing back → возвращается на Managed-list, не закрывает приложение). NEW Activity launch создаст риск multi-instance, см. CHK016 ниже.

- [ ] **CHK009 — Form input survives rotation without re-querying network/disk**

  ⚠️ **GAP**. Это связано с CHK001 / S5.

  Если admin редактирует label плитки или phone number и вращает телефон — текущий ввод должен пережить. Spec.md не специфицирует. **Action для plan.md**: либо **autosave-to-Room-per-change** (consistent с FR-014a philosophy), либо `rememberSaveable` form fields.

  **Recommended**: autosave per change в `PendingLocalChanges` — same подход как спек 8 рекомендовал в его CHK001 follow-up. Бабушка вращает телефон случайно — ноль потери ввода.

- [ ] **CHK010 — In-flight async operations survive recreation OR are cancelled+restarted predictably**

  ⚠️ **GAP**. Spec не упоминает scope publish operation.

  - **Publish (FR-014/016)**: push в `/config/current` идёт через flow спека 8. Inherits спека 8 plan-action: **push в `applicationScope`** (single-instance Koin), UI subscribes через StateFlow. Action для plan.md: явно подтвердить.
  - **Health listener (FR-020)**: screen-scoped в `viewModelScope` (per CHK006 above) — survives configuration change через ViewModel survival, cancels on screen close.
  - **VCard parse (FR-028)**: synchronous parsing после intent receive — не survive issue (≤ 10 KB, < 100 ms).
  - **History fetch (FR-039)**: read from Firestore cache — synchronous. Background sync handled by SDK.

## Configuration changes

- [x] **CHK011 — Locale change handled: strings re-resolved**

  ⚠️ **Watch — много hardcoded русских строк в FR**:
  - FR-026 «Не удалось добавить контакт: <причина>»
  - FR-028 «слишком большой», «Не удалось прочитать контакт»
  - FR-031 «Контакт без номера телефона...»
  - FR-033c full disclosure text
  - FR-043 «слишком новая версия»
  - Multiple acceptance scenarios — Russian UX strings.

  Это **должны быть `stringResource()`-backed**, иначе language change не повлияет. **Action для plan.md**: confirm — все user-facing strings externalized в `strings.xml` (ru/en). Это уже project convention (per memory `Communicate in Russian` — это conversation language, не code/UI baseline).

  **Watch также**: error messages из spec 8 (`partialApplyReasons[]`) — это **enum keys**, не raw text. Same applies здесь — если `ValidationError.NameTooLong` пишется в Firestore-store куда-либо (не пишется), это будет issue. На текущий момент — only UI-side, OK.

- [x] **CHK012 — Density / font-scale change handled**

  Spec.md FR-005 + FR-005a + Q1 Mentor: «декоративная рамка, без точного pixel-масштаба под Managed». Admin рендерит на своём density, **не пытается** симулировать Managed density.

  ⚠️ **Watch**: senior-safe font-scale override (Article VIII) — на admin-устройстве. Если admin использует senior-safe text (что нелогично, но возможно — admin сам пожилой) — UI должен адаптироваться. Inherits project convention.

- [ ] **CHK013 — Window size change (split-screen, foldable) handled OR explicitly out-of-scope**

  ⚠️ **GAP**. Spec не упоминает foldables / split-screen / Samsung DeX.

  **Особенно релевантно для admin-режима** — admin может пользоваться tablet'ом (Q-OPEN-3 упоминает «на планшете без точного указания» для accessibility) или foldable. Drag-and-drop (FR-008) на foldable со split-screen — что? Кросс-flow drag через crease?

  **Action для plan.md**: либо явный OUT-021 «admin foldable/split-screen — best-effort, не guaranteed», либо специфицировать поведение. **Recommended OUT-021**: «admin device foldable/split-screen — full-screen editor recommended; split-screen опыт не оптимизирован, но functional».

## Tests

- [ ] **CHK014 — Each US that touches state has at minimum one recreation test**

  ⚠️ **GAP**. Spec.md не специфицирует recreation tests.

  **Action для tasks.md** (mandatory):
  - **US-1 scenario 5** (draft survives screen close): test process kill + re-launch + verify draft в S4 Room.
  - **US-1 scenario 2** (drag-drop + draft updated): test rotation **after** drop completed + verify плитка остаётся в новой позиции.
  - **US-2 scenario 3** (health Critical update): test listener re-attaches после configuration change.
  - **US-3 scenario 2/3** (contact picker, multi-number dialog): test rotation during dialog → choice survives.
  - **US-4 scenario 2** (VCard preselect Managed dropdown): test rotation during selection → choice survives.
  - **US-5 scenario 2** (history preview): test rotation в preview → selected snapshot persists.

- [ ] **CHK015 — At least one process-death simulation test**

  ⚠️ **GAP**. FR-014a explicitly требует «survives process kill / app restart» — implies test.

  **Action для tasks.md**:
  - Explicit `T-Test-ProcessDeath-DraftSurvival` для FR-014a (open editor → make edits → kill process → relaunch → draft restored).
  - `T-Test-ProcessDeath-VCardReentry` для FR-029 (VCard share → process kill mid-flow → relaunch from share Intent → re-parse OK).

## Edge cases

- [x] **CHK016 — Multiple instances of the same Activity (multi-window) — behaviour documented or exclusion stated**

  **Особенно критично для VCard intent** (FR-027). Если admin делится контактом из WhatsApp, потом из Telegram **не закрывая** наше приложение — система может попробовать запустить **новый** instance. Если editor уже открыт на одном Managed — что с новой VCard intent?

  ⚠️ **GAP**. Spec.md не специфицирует.

  **Action для plan.md** + **Action для Q-OPEN-2 resolution**:
  - Использовать `launchMode="singleTask"` для admin MainActivity.
  - VCard intent с MIME `text/x-vcard` приходит как `onNewIntent()` callback в существующий instance, не launches new Activity.
  - Если editor уже открыт — show диалог «Закончить текущее редактирование <Managed-A> прежде чем добавить контакт в <Managed-B>?» — либо resolve auto через current draft scope.

- [ ] **CHK017 — Feature accessed from notification while killed — entry path tested**

  ⚠️ **Watch**.

  **VCard share — это share intent, не notification**. Но семантически похоже: app может быть killed, intent приходит → app cold-starts с VCard data в Intent extras.

  **Spec покрывает в US-4 acceptance scenarios**, но не явно «process killed».

  **Action для tasks.md**:
  - **T-Test-ColdStart-VCardShare**: kill app → WhatsApp share contact → app cold-starts → VCard parse → FR-029 dropdown показывается. Verify `lastSeen` cache для Managed list available offline.

  **Notification по Critical health** — отсутствует в спеке 9 (per OUT-009 / `TODO-ARCH-012`). N/A для этого спека.

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 6 | CHK002, CHK004, CHK006, CHK008, CHK011, CHK012, CHK016 (с plan.md follow-up) |
| ⚠️ Watch / Gap | 9 | CHK001 (S5/S6/S7), CHK003 (low-memory mid-edit), CHK005 (state scope table), CHK007 (Bundle size — history list), CHK009 (form input rotation), CHK010 (publish scope), CHK013 (foldable/split), CHK014 (recreation tests), CHK015 (process death tests), CHK017 (cold-start VCard) |
| ❌ Fail | 0 | — |

**Verdict: PASS at spec-level with significant plan.md/tasks.md follow-ups.**

Spec.md correctly establishes **process-death survival** of the **most critical** state (S4 local draft) через FR-014a + edge case 5 + US-1 scenario 5. Watch items сконцентрированы на:
1. **Granularity of S4 autosave** (per-keystroke vs explicit «save-to-draft» button) — ключевой gap, влияющий на CHK001/CHK003/CHK009.
2. **S7 health listener lifecycle** — FR-020 specifies «когда экран открыт», но не recreation/process-death detail.
3. **VCard intent multi-window / cold-start** — Q-OPEN-2 не resolved, влияет на CHK008/CHK016/CHK017.
4. **Test coverage** — нет explicit recreation/process-death tests в спеке.

---

## Top 3 state-management gaps

1. **S4 draft autosave granularity не уточнён** (CHK001, CHK003, CHK009): FR-014a говорит «хранится в Room», но не уточняет — обновляется ли S4 на **каждое** изменение формы (autosave-per-keystroke) или **только** на «применить к draft» / «сохранить локально». Без autosave — пользователь теряет ввод при rotation/low-memory-kill во время редактирования label плитки или выбора контакта. **Особенно болезненно на Xiaomi/Huawei** где task-killer aggressive. **Действие**: добавить FR-014b (или уточнить FR-014a): «изменения в editing UI пишутся в `PendingLocalChanges` continuously per change (autosave), не requiring явный save button».

2. **Q-OPEN-2 не resolved — VCard intent activity strategy** (CHK008, CHK016, CHK017): spec оставляет открытым «new Activity launch vs deep-link в существующий backstack». Это влияет на: multi-instance handling (FR-027 — admin делится из WhatsApp пока editor уже открыт), process-death recovery (cold-start с VCard Intent), back-navigation UX. **Действие**: resolve Q-OPEN-2 в plan.md — рекомендация: `launchMode="singleTask"` + `onNewIntent()` handler + NavHost deep-link route, **не** new Activity.

3. **Health listener lifecycle для S7 не уточнён** (CHK001, CHK002, CHK003, CHK006): FR-020 говорит «когда экран открыт» но не уточняет — bound к `viewModelScope` или Composable lifecycle. Если bound к Composable — есть window без подписки во время Activity recreation (rotation). **Действие**: в plan.md явно — listener в `viewModelScope` через `collectAsStateWithLifecycle` (или `DisposableEffect` с `Lifecycle.Event.ON_START/ON_STOP`).

---

## Mandatory action items для plan.md

1. **S4 autosave granularity** (CHK001, CHK003, CHK009): добавить FR-014b в спек ИЛИ зафиксировать в plan.md что «continuous autosave per change» — это implementation contract. Иначе пользователь теряет ввод.
2. **State scope table** (CHK005): нормативная таблица S1..S14 → scope/backing layer.
3. **Bundle size policy** (CHK007): только `linkId`/`snapshotId` через SavedStateHandle, не full Config/snapshot objects. История — IDs only, content из Firestore cache.
4. **Health listener в `viewModelScope`** (CHK001, CHK006, CHK010): не process-singleton, не Composable scope. Re-attach on configuration change.
5. **Publish operation в `applicationScope`** (CHK010, inherits спека 8): survives screen rotation.
6. **`launchMode="singleTask"` + `onNewIntent` для VCard intent** (CHK016, Q-OPEN-2): single-instance editor, VCard через deep-link.
7. **Foldable / split-screen — OUT-021** (CHK013): explicit OUT statement, не пытаемся optimize.
8. **All user-facing strings через `stringResource()`** (CHK011): externalized errors/rationales/disclosures.

## Mandatory action items для tasks.md

9. **Recreation tests** (CHK014): `StateRestorationTester` tests для:
   - US-1 scenario 2 (drag-drop + rotation после drop).
   - US-3 scenario 3 (multi-number dialog + rotation).
   - US-4 scenario 2 (VCard Managed-selection dropdown + rotation).
   - US-5 scenario 2 (history preview + rotation).

10. **Process-death simulation tests** (CHK015):
    - `T-Test-ProcessDeath-DraftSurvival` для FR-014a (edit → kill → relaunch → draft restored).
    - `T-Test-ColdStart-VCardShare` (kill → share VCard → cold-start → FR-029 screen).

11. **Listener lifecycle test** (CHK001): rotation в Health-detail экране → verify listener re-subscribes без window > 100 ms.

## Recommended addition to spec.md (optional, suggested)

Add brief FR clarifying S4 autosave granularity:

> **FR-014b (Draft autosave granularity)**: Изменения в editing UI MUST триггериться в `PendingLocalChanges` **continuously** при каждом изменении (autosave per change), не requiring явный «сохранить локально» button. Это гарантирует ноль потери данных при rotation / process death / low-memory kill во время editing на агрессивных OEM-устройствах (Xiaomi MIUI, Huawei EMUI).

Альтернативно — explicit «save локально» button (UX consistent с спека 8 FR-040 separation). Какое поведение хотите?

Resolve Q-OPEN-2 with explicit FR:

> **FR-027a (VCard activity strategy)**: Admin MainActivity MUST use `android:launchMode="singleTask"`. VCard share intent (FR-027) MUST be received через `onNewIntent()` callback в существующий instance — НЕ запускать new Activity. Если editor уже открыт на одном Managed — переход на VCard-add-screen происходит через NavHost route push, не через Activity replacement.

---

## Что внутри (TL;DR на русском)

Этот чеклист проверяет, выживает ли UI state приложения через rotation / process death / low-memory kill в admin-режиме спека 9.

**Главное хорошее:**
- FR-014a (новое из C1) **четко** говорит: локальный edit draft хранится в Room, переживает kill процесса, перезапуск, закрытие экрана. Edge case 5 и US-1 scenario 5 явно это покрывают. Это **самая критичная** часть state в спеке — здесь все ок.

**Главные пробелы (3 топовых):**

1. **Не уточнена granularity autosave'а**: FR-014a говорит «хранится в Room», но не уточняет — обновляется ли Room **на каждый keystroke** или **только** когда юзер нажмёт «сохранить локально». Если второе — на Xiaomi/Huawei с агрессивным task killer'ом юзер потеряет ввод в форме редактирования плитки при сворачивании на 2 минуты. **Решение**: добавить FR-014b «continuous autosave per change».

2. **Q-OPEN-2 не закрыт**: VCard intent (поделиться контактом из WhatsApp в наше приложение) — это новый Activity launch или встраивание в backstack? Влияет на multi-instance behavior (что если admin делится дважды подряд), на cold-start (что если app был killed), и на back-навигацию. **Решение**: `launchMode="singleTask"` + `onNewIntent()` + NavHost deep-link.

3. **Firestore health listener lifecycle**: FR-020 говорит «listener когда экран открыт», но не уточняет — bound к ViewModel или к Composable. Если к Composable — есть микро-window без подписки при rotation. **Решение**: в `viewModelScope` + `collectAsStateWithLifecycle`.

**Остальные watch'и** — стандартные plan-level вопросы: state scope table (S1..S14), Bundle size policy (только IDs через SavedStateHandle, не full Config), publish operation в `applicationScope`, recreation tests, foldable scope explicit OUT.

**Verdict**: PASS at spec-level. Критичная state (S4 draft) покрыта правильно через FR-014a. Watch items сконцентрированы на transient UI state и tests — это plan.md/tasks.md уровень.

**Counts**: ✅ 6 · ⚠️ 9 · ❌ 0.

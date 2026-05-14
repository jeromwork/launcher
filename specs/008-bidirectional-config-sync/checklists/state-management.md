# Checklist: state-management

**Spec**: `spec.md` (rev. 2026-05-14, post-clarify Q1-Q10)
**Run**: 2026-05-14 — `/speckit.clarify` post-pass before `/speckit.plan`.

Verifies Article IV §5 and §III.3 — state survival across Android lifecycle events.

---

## State inventory in spec 008

| # | State piece | Scope | Persistence layer | Surviving events |
|---|---|---|---|---|
| S1 | applied-config (Managed) | feature-level | Room | rotation ✅, process death ✅, reboot ✅ |
| S2 | pending-local-changes (any editor) | feature-level | Room | rotation ✅, process death ✅, reboot ✅ |
| S3 | currently-edited draft (in Settings UI) | screen | should be `rememberSaveable` or backed by S2 | rotation ?, process death partially via S2 |
| S4 | snapshot `serverUpdatedAt` (для optimistic concurrency) | screen / draft | should be part of S2 PendingLocalChanges | rotation ?, process death via S2 |
| S5 | merge UI state (which fields user chose) | screen | `rememberSaveable` или ViewModel | rotation ?, process death = lost (user re-do merge) |
| S6 | push spinner state (FR-015) | UI-local | `remember` + StateFlow | rotation: ephemeral OK; process death: re-derive from S2 (есть ли pending) |
| S7 | "pending push" badge on main screen list (FR-046) | feature-level | derived from S2 | rotation/process death — re-derived |

---

## Lifecycle events

- [ ] **CHK001 — Behaviour after Activity recreation (rotation, language change, theme switch) explicitly specified**

  **Finding: PARTIAL.**

  ✅ **Implicitly handled**:
  - S1, S2 (Room-backed) — survives all Activity recreation; Room read returns same data.
  - S7 (derived from S2) — re-derived on recreation.

  ⚠️ **NOT explicitly specified in spec.md**:
  - S3 (edited draft in Settings UI): что происходит при rotation если user не нажал «save локально»?
    - **Option A**: edits lost (default Compose behavior — `remember` discarded).
    - **Option B**: edits preserved через `rememberSaveable`.
    - **Option C**: edits **auto-saved** в pending Room на каждое изменение (similar к Google Docs autosave).
    - **Recommendation для plan.md**: Option C для consistency с overall philosophy («save локально мгновенно, push отдельно»). Это меняет интерпретацию FR-040: «save локально» может быть **continuous autosave**, не явная кнопка. **Action для plan.md**: уточнить granularity «save локально».
  - S5 (merge UI partial choices): если user resolved 3 из 10 конфликтов и rotation — state lost? Это **bad UX**. **Action для plan.md**: `rememberSaveable` для merge choices.

- [x] **CHK002 — Behaviour after process death specified**

  ✅ **Хорошо покрыто**:
  - FR-041: applied-config persisted Room.
  - FR-042: pending-local-changes persisted Room.
  - FR-043: pending lives forever, no auto-discard.
  - FR-044: read Room до first frame.
  - US-4 acceptance scenario 1: explicit process-kill scenario.
  - US-5 acceptance scenario 1: process death + restart.
  - SC-004a: first frame ≤ 650 ms from Room.
  - SC-004b: fetch /config 5s after first frame.

- [x] **CHK003 — Behaviour after low-memory kill (foreground process trimmed)**

  Same as CHK002 — Room survives, in-memory state lost but reconstructed from Room.

  ⚠️ Watch: low-memory часто происходит во время editing — user может потерять S3 (current draft). Mitigation: Option C из CHK001 (continuous autosave).

- [x] **CHK004 — Behaviour after device reboot specified**

  ✅ Room database survives reboot. FR-044 reads Room on launch. No additional FR needed — reboot ≈ process death + delayed restart.

## State scope

- [ ] **CHK005 — For each piece of state: scope explicitly chosen**

  **Finding: PLAN.MD ACTION**

  Spec.md не указывает scope для каждого state piece — это уровень plan.md. Recommended:

  | State | Recommended scope |
  |---|---|
  | S1 applied-config | feature-level singleton (Koin), backed by Room |
  | S2 pending-local-changes | feature-level singleton, backed by Room |
  | S3 currently-edited draft | screen-scoped ViewModel + autosave to S2 |
  | S4 snapshot updatedAt | part of S2 (PendingLocalChanges включает snapshot) |
  | S5 merge UI choices | screen-scoped ViewModel + `rememberSaveable` для individual fields |
  | S6 push spinner | UI-local `remember` + observe global push-in-progress StateFlow |
  | S7 pending badge | derived from S2 via Flow |

- [x] **CHK006 — No use of process-singleton state for things that should be screen-scoped**

  Spec.md не предлагает process-singletons. ViewModel / DI patterns стандартные. ✅

- [x] **CHK007 — No use of `rememberSaveable` for non-trivial / large objects (Bundle limits)**

  Spec.md не специфицирует. **Watch для plan.md**:
  - `rememberSaveable(ConfigDocument)` — **BAD**, ConfigDocument может содержать 100+ контактов = большой Bundle.
  - Right pattern: `rememberSaveable(configDocumentId: ConfigId)`, фактический read через Room.

## Recreation correctness

- [x] **CHK008 — No "first-only" navigation logic**

  Spec.md не описывает navigation flow detail. **Watch для plan.md**: при открытии Settings — переходы не должны зависеть от «впервые ли это запуск».

- [ ] **CHK009 — Form input survives rotation without re-querying network/disk**

  ⚠️ **GAP.** Это связано с CHK001 / S3.

  Если admin редактирует контакт и вращает телефон — текущий ввод должен пережить. Spec.md не специфицирует. **Action для plan.md**: autosave-on-each-change pattern гарантирует это (Room write на каждое изменение → rotation re-reads Room).

- [x] **CHK010 — In-flight async operations survive recreation OR are cancelled+restarted predictably**

  Spec.md не специфицирует. **Action для plan.md**: push operation — должна survive recreation. Стандартный pattern: `viewModelScope` для UI-bound, `applicationScope` для cross-screen operations like push.

  - Push initiated в Settings → user поворачивает телефон → push должен продолжаться, не cancelled.
  - **Action для plan.md**: явное решение — push в `applicationScope` (single-instance Koin), UI subscribes через StateFlow.

## Configuration changes

- [x] **CHK011 — Locale change handled: strings re-resolved**

  Spec.md не интродуцирует hardcoded strings. **Action для plan.md / spec 008**: все UI strings через `stringResource()`, не bare strings. (Это уже project convention.)

  **Watch**: error messages из `partialApplyReasons[]` (FR-033) — это **server-stored data**. Если сервер хранит русский текст «провайдер недоступен», localization broken. **Recommendation**: `partialApplyReasons[]` должны быть **enum keys** (e.g., `"provider_unavailable"`, `"contact_permission_denied"`), translated client-side.

- [x] **CHK012 — Density / font-scale change handled**

  Spec.md не специфицирует layout. **Inherits** project's existing senior-safe text sizing (per memory `project_launcher_context`). **N/A на spec-level**, обязательно соблюсти в UI implementation.

- [x] **CHK013 — Window size change (split-screen, foldable) handled OR explicitly out-of-scope**

  Spec.md не упоминает foldables / split-screen. **Inherits** Launcher constraints — launcher обычно занимает full screen. **Action для plan.md**: подтвердить, что 008 не вводит UI, требующий разных layouts.

## Tests

- [ ] **CHK014 — Each US that touches state has at minimum one recreation test**

  ⚠️ **GAP.** Spec.md не специфицирует tests recreation.

  **Action для tasks.md** (mandatory):
  - US-4 scenario 1 (process kill + restart): test (`@MediumTest` или integration with Robolectric / instrumented).
  - US-5 scenario 1: same.
  - US-2 merge UI: `StateRestorationTester` test для merge UI rotation survival.
  - US-3 scenario 3 (Managed offline, save локально): test rotation during save.

- [ ] **CHK015 — At least one process-death simulation test**

  ⚠️ **GAP.** Spec.md SC-004a/b mentions «process death first frame ≤ 650 ms» — implies test. **Action для tasks.md**: explicit `T-Test-ProcessDeath-FirstFrame` measuring boot time с simulated process kill.

## Edge cases

- [x] **CHK016 — Multiple instances of the same Activity (multi-window) — behaviour documented or exclusion stated**

  Launcher-as-HOME doesn't support multi-window для HOME role (Android constraint). Settings activity внутри Managed — обычная activity, multi-window theoretically possible.

  **Inherits** project's existing posture: launcher is single-instance. **Action для plan.md**: документировать «multi-instance Settings not supported, configChanges или `launchMode=singleTask`».

- [ ] **CHK017 — Feature accessed from notification while killed — entry path tested**

  ⚠️ **Watch.** 008 doesn't introduce notifications. But: FCM `config.updated` payload (FR-020) — это **silent push** (data-only), не user notification. Не показывается user'у. Не triggering app launch (unless app is alive).

  **Что должно быть в plan.md**: подтвердить «FCM data-message не triggering app launch, не notification». Apply triggered только если app уже running OR через T2/T3/T4 fallback triggers when app eventually launches.

  This is the existing 007 pattern. ✅ Inherited.

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 10 | CHK002, CHK003, CHK004, CHK006, CHK008, CHK010, CHK011, CHK012, CHK013, CHK016 (most with plan.md follow-ups) |
| ⚠️ Watch / Gap | 7 | CHK001 (S3/S5 rotation), CHK005 (state scope), CHK007 (rememberSaveable size), CHK009 (form input survival), CHK014 (recreation tests), CHK015 (process death tests), CHK017 (FCM entry path) |
| ❌ Fail | 0 | — |

**Verdict: PASS at spec-level with significant plan.md/tasks.md follow-ups.**

Spec.md correctly establishes **process-death survival** through Room persistence (FR-041..044). Critical state — applied-config, pending-local-changes — survives all lifecycle events. Watch items are predominantly о **transient UI state** (current edit draft, merge UI choices) и **test coverage** — нужно покрыть в plan.md/tasks.md.

---

## Mandatory action items для plan.md

1. **Autosave granularity** (CHK001, CHK009): уточнить, является ли «save локально» (FR-040) **continuous autosave** при каждом изменении (recommended) или **explicit button**. Если continuous — добавить упоминание в spec.md как clarification.

2. **State scope table** (CHK005): для каждого state piece явный scope (UI-local / screen / feature / persistent). См. таблицу выше.

3. **`rememberSaveable` policy** (CHK007): only IDs, не full ConfigDocument objects.

4. **Push in `applicationScope`** (CHK010): push operation survives screen rotation/recreation. UI observes через StateFlow.

5. **`partialApplyReasons[]` as enum keys, not strings** (CHK011): localization-safe.

6. **`launchMode=singleTask` для Settings activity** (CHK016): documented.

## Mandatory action items для tasks.md

7. **Recreation tests** (CHK014): `StateRestorationTester` test для каждой UI с state (Settings, Merge UI).
8. **Process death simulation test** (CHK015): explicit task measuring SC-004a (first frame ≤ 650 ms after process kill).

## Recommended add to spec.md (optional)

Add brief FR clarifying autosave-vs-explicit-save:

> **FR-056 (Save granularity)**: «Save локально» (FR-040) MUST триггериться **continuously** при каждом изменении в editing UI (autosave per change), не requiring явный «save» button. Pending state в Room обновляется на каждое user-edit. Это гарантирует ноль потери данных при rotation / process death во время editing.

Альтернативно — добавить через явный «save» button если предпочитаете. **Какое поведение хотите?**

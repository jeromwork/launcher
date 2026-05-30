# State Management — spec 014

Generated: 2026-05-29.

## Lifecycle events

- [⚠️] **CHK001** Activity recreation behavior **не явно специфицирован**. Что происходит, если admin в edit mode и пользователь повернёт телефон? Edit mode active state должен survive. **Improvement**: FR должен указать `rememberSaveable` для `EditMode.active` или эквивалент в Decompose state. Plan.md уточнит.
- [⚠️] **CHK002** Process death: admin made unsaved edits → process killed → restart. Что показывается? Per ConfigEditor (спека 008), `pendingDraft` сохраняется локально (FR-002 + assumption "при появлении сети push'ится автоматически" в Edge Cases). PASS для domain layer. **UI layer** — edit mode state restoration не описана.
- [⚠️] **CHK003** Low-memory kill: similar to process death. Same gap.
- [x] **CHK004** Device reboot: F-014.0 DataStore persisted, survive reboot. F-014.1 Firestore cached — survive. OK.

## State scope

- [⚠️] **CHK005** State scope не explicit в спеке. Implicit:
  - `EditMode.active: Boolean` — screen-scoped (rememberSaveable).
  - `EditMode.target: TargetIdentity` — screen-scoped.
  - `EditMode.profile: EditUiProfile` — derived from target (no separate state).
  - `pendingDraft: ConfigDocument` — feature-scoped (ConfigEditor singleton, existing спека 008).
  - Picker selection state — UI-local (`remember`).
  Plan.md должен документировать.
- [x] **CHK006** Nothing suggests process-singleton misuse.
- [x] **CHK007** EditMode state — small (Booleans + IDs), safe для Bundle. ConfigDocument **not** placed in Bundle (uses ConfigEditor reference).

## Recreation correctness

- [x] **CHK008** Нет "first-only" navigation на entry в edit mode (long-press triggered any time).
- [x] **CHK009** Form input в picker (search field для App/Contact/Document tabs) — implied rememberSaveable per стандарт. Plan.md confirm.
- [⚠️] **CHK010** In-flight async: `ConfigEditor.pushPending` — background. На recreation он не cancels (process-scoped). При process death — pending sits в DataStore. **OK** per спека 008 design.

## Configuration changes

- [x] **CHK011** Locale change: F-014 strings (banner "Редактируешь телефон Маши", snackbar "Удалено") — должны быть в `strings.xml` resources (per checklist-localization). Locale re-resolution works автоматически.
- [x] **CHK012** Font scale: senior tap-target ≥56dp (FR-013, FR-021) compatible с font scale up to 200%. Edit mode mainstream Material guidelines.
- [⚠️] **CHK013** Window size change (split-screen, foldable): spec **не упоминает**. OEM Matrix не покрывает foldable. **Acceptable**: declare out-of-scope explicitly. Improvement: add to "Что НЕ строит этот спек".

## Tests

- [⚠️] **CHK014** Recreation tests не listed в §Local Test Path. **Improvement**: добавить `EditModeRecreationTest` в `:app:test` verification commands.
- [⚠️] **CHK015** Process-death simulation: per FR-003 (named configs lifecycle), ConfigEditor pendingDraft persistence — нужен test что после kill + restart pending всё ещё там. Existing спека 008 покрывает? **Verify в plan.md**.

## Edge cases

- [x] **CHK016** Multi-window: out-of-scope implicit (launcher = home, single window). OK.
- [⚠️] **CHK017** Killed app entry: бабушка получает FCM notification от admin'а changes? Per Q5 — sync out of scope, по сути нет triggered entry from notification. PASS.

## Open items

1. **CHK001-CHK003**: Activity recreation / process death / low-memory — UI layer state survival нужно specify в plan.md (rememberSaveable / Decompose state).
2. **CHK005**: State scope для EditMode fields — plan.md.
3. **CHK013**: Foldable/split-screen — declare out-of-scope explicit в spec.
4. **CHK014-CHK015**: Recreation tests + process-death simulation tests — plan.md / tasks.md.

**Verdict**: PASS с 4 open items для plan.md. Domain layer state ОК (через existing ConfigEditor). UI layer state требует уточнения в plan.

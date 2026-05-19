# State-Management Checklist: Setup Assistant and Launcher Bootstrap

**Purpose**: Verify state survival per constitution Article IV §5 + §III.3.
**Created**: 2026-05-19 (post `/speckit.clarify`)
**Feature**: [spec.md](../spec.md)

---

## Screens / state introduced by spec 010

| Screen / state | Source | Scope (proposed) | Survives rotation? |
|----------------|--------|------------------|---------------------|
| HomeScreen (modified — reads /config) | ARCH-016 FR-001..FR-006 | Feature (Room-backed via спек 8) | ✓ by-construction |
| FirstLaunch wizard (ROLE_HOME + POST_NOTIFICATIONS) | FR-007, FR-008 | Screen (SavedStateHandle per спек 3) | ✓ ([gap — see CHK001](#open-items)) |
| Call confirmation dialog | US-3 | UI-local (per-invocation) | ⚠️ ([gap — see CHK001](#open-items)) |
| Settings screen + `!N/?M` badges | US-6 FR-019 | Feature (SetupCheck results, recompute on RESUMED) | ✓ |
| Challenge gate screen | US-7 FR-022..FR-027 | **Not specified** | ⚠️ ([gap — see CHK001, CHK005, CHK009](#open-items)) |
| «Что не настроено» screen | FR-020 | Screen | ✓ (rendered from feature-scope cache) |
| GMS hard-block screen | FR-042..FR-044 | One-shot, kills affinity | N/A (no recreation possible) |
| Paired devices list | US-5 FR-029..FR-033 | Feature (reads /links Firestore) | ✓ |

## Lifecycle events

- [⚠️] **CHK001** — Activity recreation behaviour:
  - HomeScreen: ✓ (Room observable from спек 8 survives).
  - Wizard: ⚠️ — implicit от спека 3 SavedStateHandle pattern; **spec 010 не явно говорит** что новые шаги ROLE_HOME/POST_NOTIFICATIONS survive rotation.
  - Call confirmation dialog: ⚠️ — **rotation behaviour не specified**. Если dialog destination (navigation route), survives by-default; если remembered Composable state — теряется.
  - **Challenge gate**: ⚠️⚠️ — **scope не specified в спеке**. Если challenge regenerated при каждой рекреации → admin набрал «16», повернул, поле очистилось. Frustrating. Если challenge in ViewModel → survives. **Plan должен зафиксировать.**

- [X] **CHK002** — Process death:
  - HomeScreen cold-start: explicit FR-002 — Room observable, no network. ✓
  - Wizard mid-state: каждый step (ROLE_HOME, POST_NOTIFICATIONS) — binary (granted/not), re-check at restart. Если был на step N, после restart — RESUMED triggers re-check, переход к первому incomplete step. **Acceptable**, плюс это уже спек 3 pattern.
  - Challenge gate killed mid-attempt: in-memory state lost, regenerate at next 7-tap. **Acceptable** (FR-025 explicit in-memory only).
  - Call dialog killed mid-display: dismissed, бабушка тапнет плитку снова. ✓

- [X] **CHK003** — Low-memory kill: same as CHK002. ✓

- [X] **CHK004** — Device reboot:
  - Persistent state в спеке 10: **none** (FR-025).
  - HomeScreen reads from Room (спек 8) — survives. ✓
  - Paired list reads from Firestore (спек 7) — survives. ✓

## State scope

- [⚠️] **CHK005** — State scope explicit:
  - appliedConfig: feature-scope (спек 8 ConfigEditor singleton). ✓
  - Setup check results: feature-scope, recomputed on Settings RESUMED (FR-020a). ✓
  - **Challenge state: scope NOT specified в спеке**. Plan должен выбрать: ViewModel-scope (survives rotation) или Composable-remember (lost on rotation, accepted UX cost).
  - Wizard step: SavedStateHandle (спек 3 pattern). ✓
  - Call confirmation: UI-local (per tile-tap invocation). ✓
- [X] **CHK006** — No process-singleton for screen-scoped state apparent. ✓
- [X] **CHK007** — No rememberSaveable for large objects in spec text. (Plan-level detail.)

## Recreation correctness

- [⚠️] **CHK008** — No "first-only" navigation skipping recreation:
  - Wizard: implicit OK от спека 3 pattern. ✓
  - Challenge gate post-pass → admin-mode: должен использовать `Lifecycle.STARTED` flow или Side-effect with key, не `LaunchedEffect(Unit)`. **Plan detail.**
- [⚠️] **CHK009** — Form input survives rotation без re-query:
  - **Challenge numeric-entry**: critical case — admin набрал «16», rotated → должен сохраниться. Если challenge state lost — FP rate возрастает до 0 (admin не помнит был ли ответ), retry на каждое rotation. **Plan должен зафиксировать.**
  - Wizard inputs: spec 3 pattern. ✓
- [⚠️] **CHK010** — In-flight async (SetupCheck running on RESUMED, rotation mid-check): plan-level decision. Suggest cancel + restart on rotation (re-RESUMED триггерит anyway).

## Configuration changes

- [X] **CHK011** — Locale change:
  - FR-039 — все user-facing strings localized (en+ru per ADR-004).
  - Badge labels («критично» / «рекомендуется» — FR-019) localized. ✓
  - Challenge text: number — Arabic numerals (universal). Sequence-tap instruction — localized. ✓
- [⚠️] **CHK012** — Density / font-scale change:
  - Senior-safe ≥ 56dp baseline (Article VIII §7) выдерживает font-scale 200%.
  - **Edge**: FR-026 «challenge text — мелкий шрифт намеренно (≤ 14sp)». На системном font-scale 200% становится 28sp — visible бабушке. Defeats the «мелкий шрифт чтобы бабушка не различила» intent. **Соответствующая edge case в Edge Cases уже отсутствует — нужно добавить или принять.** Soft observation: бабушка с font-scale 200% увидит challenge — но это её собственный выбор; admin-mode UI ей всё равно непонятен, нажмёт ОТМЕНА.
- [X] **CHK013** — Window size / split-screen / foldable: **out-of-scope** per implicit (lon launchers редко поддерживают split-screen, elderly use single-screen). Plan может зафиксировать в OUT.

## Tests

- [⚠️] **CHK014** — Recreation tests: plan-level. Spec не enumerates тесты; expected pattern: per `StateRestorationTester` per screen в `tasks.md` Phase 3 (фake adapters tests).
- [⚠️] **CHK015** — Process-death simulation: plan-level. Спек 10 has minimal persistent state, но cold-start path (FR-002) требует тест: process kill → restart → first frame ≤ 1 sec из Room.

## Edge cases

- [X] **CHK016** — Multi-window: implicit out-of-scope for launcher. ✓ (могут explicitly mark в OUT plan-level).
- [⚠️] **CHK017** — Feature accessed from notification while killed: спец 7 push'и в шторке. Тап → launcher cold-start → HomeScreen (per FR-002 ≤ 1 sec). **Не enumerated в acceptance scenarios** спека 10, но coverage есть через US-1 ARCH-016 path.

---

## Open items

1. **CHK001/CHK005/CHK009 — Challenge gate state scope.** Plan.md MUST зафиксировать: (a) ViewModel-scope (survives rotation, admin's «16» сохраняется), or (b) Composable-remember (rotation regenerates challenge, accepted UX cost). **Recommendation:** (a) — admin frustration cost > implementation cost of ViewModel.

2. **CHK001 — Call confirmation dialog rotation.** Plan должен явно: либо navigation destination (auto-survives), либо `rememberSaveable { contactId }` в Composable.

3. **CHK012 — Font-scale × challenge text size.** Add edge case в spec.md или принять как «известное соотношение: бабушка с font-scale 200% видит challenge, но admin-mode UI ей всё равно непонятен». **Recommendation:** add as edge в spec.md (one-line note).

4. **CHK014/CHK015 — Test coverage**: plan.md Phase 3 (fake-adapter tests) MUST включать:
   - StateRestorationTester для challenge gate screen.
   - Cold-start benchmark (process death → first frame ≤ 1 sec) для HomeScreen.
   - Locale change re-render для Settings badges.

## Result

**13/17 ✓, 4 observations** (CHK001/CHK005/CHK009 связанные с challenge gate state scope — plan-level decision; CHK012 font-scale edge — spec.md addition; CHK014/CHK015 test coverage — plan-level enumeration). **Не blocker для `/speckit.plan`**: все findings — деталь implementation pattern, не invalid scope.

---

## Краткое содержание (для не-разработчика)

Проверили: переживает ли state поворот экрана, kill процесса, перезагрузку устройства. **Основная находка**: для challenge gate не указано — если бабушка/admin начал вводить ответ и повернул экран, ввод сохраняется или нет? План обязан выбрать (рекомендуется ViewModel scope — сохраняется). Другая edge: при системном font-scale 200% «мелкий шрифт» challenge'a становится крупным — defeats intent, но accepted edge (admin-mode UI бабушке всё равно непонятен).

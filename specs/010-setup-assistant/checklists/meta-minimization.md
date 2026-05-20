# Meta-Minimization Checklist: Setup Assistant and Launcher Bootstrap

**Purpose**: Catch speculative architecture per Article XI of `constitution.md` and CLAUDE.md rule 4.
**Created**: 2026-05-19 (post `/speckit.clarify`)
**Feature**: [spec.md](../spec.md)

Reference: [`specs/002-whatsapp-tile-return/checklists/meta-minimization.md`](../../002-whatsapp-tile-return/checklists/meta-minimization.md).

---

## New abstractions in spec 010

Inventory of new types introduced:
1. `SetupCheck` (port) — domain interface for soft-checks.
2. `SetupCheckRegistry` (interface) — enumerates registered checks.
3. `Criticality` (sealed) — `Required` / `Recommended`.
4. **`Surface` (sealed)** — `Settings` / `MainScreen`.
5. `CheckStatus` (sealed) — `Ok` / `NotConfigured(reason)`.
6. `IntentSpec` (data class) — platform-agnostic intent descriptor.
7. `SlotToActionMapper` (function) — Slot+Contacts → Action.
8. `Challenge` (sealed) — `NumericEntry` / `SequenceTap`.
9. `ChallengeRegistry` (interface) — generates random challenges.

## Checks

- [X] **CHK001** — Every new interface/port has concrete consumer in this spec, **with 1 finding**:
  - `SetupCheck`: 5 concrete implementations (RoleHomeCheck, PostNotificationsCheck, CallPhoneCheck, NetworkOnlineCheck, BatteryOptimizationCheck) per FR-018. ✓
  - `SetupCheckRegistry`: consumed by Settings UI FR-019/FR-020. ✓
  - **`Surface.MainScreen`** — **NO consumer in this spec**. All check'и в FR-018 имеют `surfaces = { Settings }`. MainScreen — explicit future seam. **Failing CHK001 by literal reading.** See CHK010 for reasoning.
  - `Challenge`: 2 concrete types (NumericEntry, SequenceTap) per FR-023. ✓
  - `ChallengeRegistry`: consumed by gate UI when 7-tap fires (FR-022). ✓
  - `SlotToActionMapper`: consumed by FR-003. ✓
  - `IntentSpec`: consumed by `SetupCheck.resolveIntent()` + Android-side mapping (FR-017). ✓
  - `CheckStatus`: consumed by `SetupCheck.check()`. ✓

- [⚠️] **CHK002** — Single-implementation interfaces justified, **with 2 findings**:
  - `SetupCheck` — multiple implementations (5 + fakes). ✓
  - `SetupCheckRegistry` — **single implementation** (default registry with 5 checks). Could be replaced by `List<SetupCheck>` injection via Koin. Registry abstraction adds enum API + ordering policy — borderline value.
  - `ChallengeRegistry` — **single implementation** (uniform random generator). Could be replaced by free function `fun generateRandomChallenge(): Challenge`. Registry abstraction unnecessary в спеке 10 (single producer, single consumer).

- [X] **CHK003** — No pass-through mediators. `SlotToActionMapper` does data transformation (contact resolution by ID); not pass-through.

- [⚠️] **CHK004** — Registry pattern challenge:
  - `SetupCheckRegistry` AS A REGISTRY: justified by ordering (Required first, FR-018) + Settings UI enumeration. But ordering can be a `Comparator` extension on `List<SetupCheck>` — simpler. **Borderline; recommend simplification в plan.**
  - `ChallengeRegistry` AS A REGISTRY: not justified — single random selector, no enumeration, no plugin pattern. **Recommend collapse в `fun generateRandomChallenge(random: Random = Random.Default): Challenge`.**

## New modules / packages

- [X] **CHK005** — No new Gradle modules. All new types в existing `core/commonMain/api/setup/`, `core/commonMain/api/gate/`, `:app/androidMain`.
- [X] **CHK006** — N/A (no new modules).
- [X] **CHK007** — No "utils" / "common" / "helpers" dumping ground. New types semantically grouped by feature (setup, gate, action).

## New configuration

- [X] **CHK008** — No new wire-format config fields. FR-040 explicitly: «никакая wire-format модификация не повышает `schemaVersion`». Challenge state — in-memory only (FR-025).
- [X] **CHK009** — N/A (no new config fields). Local-only state (`AdminModeGateState`, `TutorialOverlayState`) удалены в clarify.

## CLAUDE.md rule 4 self-test

- [⚠️] **CHK010** — **Test 1: if abstraction inlined, what lost?** — **1 finding**:
  - `SetupCheck`/`CheckStatus`/`IntentSpec`/`Criticality`: тестируемость + DI; inlining ломает FakeAdapter pattern (rule 6 mock-first). ✓ Keep.
  - `SlotToActionMapper`: inlined ⇒ logic растёт в `FlowScreen` Composable, нарушает CLAUDE.md rule 1 (UI → domain leak). ✓ Keep.
  - `Challenge`/`NumericEntry`/`SequenceTap`: sealed для exhaustive `when` в Compose UI + контракт challenge generation. ✓ Keep.
  - **`Surface.MainScreen`**: inlining (т.е. удаление поля `surfaces: Set<Surface>` из `SetupCheck`) **не теряет ничего в спеке 10** — все check'и `Settings`-only. Это **failing Test 1**: future-optionality without current consumer.
  - **Counter-argument:** добавление `surfaces` поля задним числом — **breaking signature change** для всех 5 `SetupCheck` реализаций. Если спек 013 (offline-detection-and-emergency-reachability) добавит main-screen banner check — без seam'a придётся либо forkать `SetupCheck`, либо добавлять параллельный механизм banner'ов (mute-style). Оба варианта хуже additive `Set<Surface>` field.
  - **Honest verdict per rule 4**: Test 1 fails. Per strict rule application — Surface.MainScreen вариант должен быть удалён сейчас, добавлен в спеке 013. **Plan должен принять решение** (см. Open items).

- [X] **CHK011** — **Test 2: dep doubled / deprecated / privacy** — все domain types — pure stdlib + sealed classes; нет внешних зависимостей. Vendor types (`GoogleApiAvailability`, `HapticFeedbackConstants`) — только в adapter'ах, swap < 1 day. ✓

## Removal validation

- [⚠️] **CHK012** — Spec removes:
  - **`flows_mock_*.json`** (FR-004) — referenced в Robolectric тестах спека 3/4 (concern #6 + SC-008). **Audit pending в plan.md/tasks.md**: explicit task переписать ~3-5 тестов на `FakeRemoteSyncBackend`.
  - **PIN state** в `EncryptedSharedPreferences` — никогда не был реализован (draft FRs из исходного спека 10). Нечего dangling.
  - **Tutorial overlay state** (DataStore счётчики) — никогда не был реализован. Нечего dangling.
  - **Cross-spec dangling reference audit** — отложен на `procedure-cross-artifact-trace` в `/speckit.tasks`.
- [X] **CHK013** — Spec не помечает код "deprecated, will remove later". US-8 + FR-034..FR-038 удалены полностью (не deprecated). PIN-related FRs полностью переписаны (не deprecated). ✓

---

## Open items

1. **CHK001/CHK010 — `Surface.MainScreen` enum variant без consumer.** Strict application of rule 4: remove enum, inline `surfaces` field. Soft application: keep as 1-day-cost seam для anticipated spec 013 needs.
   - **Recommendation для `/speckit.plan`:** оставить Surface seam **только если** plan может явно указать спек 013 как single consumer в roadmap; иначе collapse Surface enum to single `Settings` constant и расширить когда понадобится.

2. **CHK002/CHK004 — `SetupCheckRegistry` и `ChallengeRegistry` registry patterns.** Strict: collapse to `List<SetupCheck>` injection + `fun generateRandomChallenge()` free function. Soft: keep registries для testability isolation.
   - **Recommendation для `/speckit.plan`:** простейшая форма (`List<>` + free fun) пока единственная реализация. Registry — only when 2+ implementations needed.

3. **CHK012 — `flows_mock_*.json` removal**: explicit task in `tasks.md` для переписывания 3-5 Robolectric-тестов на `FakeRemoteSyncBackend`. Должно попасть в Phase 0 (ARCH-016 closure) per concern #6.

## Result

**10/13 ✓, 3 observations** (CHK001+CHK010 связанные про `Surface.MainScreen`; CHK002+CHK004 связанные про registry patterns; CHK012 audit pending). **Не blocker для `/speckit.plan`** — все findings — soft architectural choices, не critical issues. Plan должен явно принять решение по каждому open item.

## Plan-level re-run (2026-05-19, post /speckit.plan)

**Status**: **12/13 ✓** — 2 closed, 1 watch item documented.

Plan.md addresses:
- **CHK002+CHK004 (Registry patterns)** — **CLOSED** by plan D-2/D-3 decisions:
  - **D-2**: `SetupCheckRegistry` **collapsed** to `List<SetupCheck>` Koin injection. No registry class. Listed в plan §11 C-3 as binding constraint.
  - **D-3**: `ChallengeRegistry` **collapsed** to free function `generateRandomChallenge(random)`. No interface. Listed в plan §11 C-4.
- **CHK001+CHK010 (Surface.MainScreen)** — **WATCH ITEM** with documented mitigation (D-1 plan decision):
  - Surface enum **kept** с `Settings` + `MainScreen` variants.
  - Anticipated consumer: **спек 013 `offline-detection-and-emergency-reachability`** main-screen banner pattern (per roadmap §Spec 013).
  - Exit ramp: 5-minute rename refactor если спек 013 откажется от main-screen banner pattern.
  - Constraint plan §11 C-5: inline-TODO в `Surface.kt` documents anticipated consumer.
  - **Не strict CHK010 fail** — у нас есть named anticipated consumer in same roadmap horizon, not abstract «for future flexibility».
- **CHK012 (flows_mock removal audit)** — **CLOSED** by plan Phase 2: explicit task переписать 3-5 Robolectric tests на `FakeRemoteSyncBackend` в той же phase (SC-008 verifies CI green).

**Verdict**: D-1 watch item — accepted per rule 4 (anticipated consumer documented, exit ramp tiny). D-2/D-3/CHK012 closed.

---

## Краткое содержание (для не-разработчика)

Проверили нет ли «overengineering» — лишних абстракций «на будущее». Нашли 3 borderline места: (1) поле `Surface.MainScreen` в порту `SetupCheck` сейчас не используется (нужно только в будущем спеке 013); (2) `SetupCheckRegistry` и `ChallengeRegistry` — registry-паттерны с одной реализацией, можно упростить до `List<>` injection и free function. Плану предстоит решить — оставить seam'ы (минимальный cost) или collapse до простейшей формы. **Не блокирует переход к plan.**

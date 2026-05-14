# Legacy mock-storage inventory (spec 003 → spec 008)

**Date**: 2026-05-14
**Task**: T005
**Source**: grep `core/src/`, `app/src/`, `docs/` for spec-003-era mock-storage references.

---

## Finding: assets, not user files

Spec 003's «mock storage» is **NOT runtime-mutable storage**. It's **read-only APK assets** loaded by `MockFlowRepository`:

### Asset files (read-only, bundled with APK)

- `core/src/androidMain/assets/flows_mock_simple-launcher.json` — default preset flows.
- `core/src/androidMain/assets/flows_mock_launcher.json` — alternate preset.
- `core/src/androidMain/assets/flows_mock_workspace.json` — workspace preset.
- `core/src/androidMain/assets/mock_contacts.json` — mock contacts for testing.
- `core/src/androidMain/assets/default_profile.json` — initial profile.

### Code that reads them

- `core/src/androidMain/kotlin/com/launcher/core/flows/MockFlowRepository.kt` — reads `flows_mock_${preset.slug}.json` from `context.assets`.
- `core/src/commonMain/kotlin/com/launcher/core/preset/InMemoryPresetRepository.kt` — in-memory preset state (not persisted).

### Test references

- `core/src/androidUnitTest/kotlin/com/launcher/core/flows/MockFlowRepositoryTest.kt` — tests against assets.
- `core/src/commonTest/kotlin/.../FakeProviderRegistry.kt` — uses mock data.

### DI wiring

- `core/src/androidMain/kotlin/com/launcher/di/BackendModule.kt` — binds `MockFlowRepository` for `mockBackend` flavor.

---

## Implication for FR-045

**Original FR-045 intent**: «delete legacy mock-JSON storage at first launch» — assumed user-writable JSON files в `filesDir`. **There are no such files**.

**Revised understanding**:
- spec 003's mock storage is **immutable assets** shipped в APK.
- They will continue to exist в APK после spec 008 ships — но runtime code path bifurcates by build flavor:
  - `mockBackend` flavor: продолжает использовать `MockFlowRepository` reading assets (for dev/test без Firebase).
  - `realBackend` flavor: switches to `SqlDelightLocalConfigStore`-backed flow rendering (reads applied-config from local DB written by `ConfigApplier`).
- **No cleanup needed** for user devices — nothing to delete.

---

## Revised FR-045 semantics

The «cleanup» is **conceptual / architectural**, not file-deletion:

1. **`realBackend` apps** should NOT use `MockFlowRepository` once spec 008 ships.
2. **DI wiring** in `BackendModule.kt` should bind:
   - `realBackend` flavor → new `RemoteBackedFlowRepository` that reads from `SqlDelightLocalConfigStore`.
   - `mockBackend` flavor → keeps `MockFlowRepository` (read assets) for dev/test.
3. **Existing test code** referencing `MockFlowRepository` continues to work — it's a `mockBackend`-flavor adapter, not a runtime cleanup target.

**No runtime cleanup task needed** for T054 in its original form. Instead, T054 becomes:
- **T054 (revised)**: Wire DI in `realBackend` to bind `LocalConfigStore`-backed flow repository, leaving `mockBackend` flavor undisturbed. Document the bifurcation.

---

## Updated task semantics

- **T005 (this task)**: ✅ done — inventory above.
- **T054**: re-scope to «DI wiring switch in realBackend flavor». No file-deletion.
- **T055**: re-scope to «unit test verifies realBackend DI binding chooses RemoteBackedFlowRepository, not MockFlowRepository».
- **Action для plan.md / spec.md**: spec.md FR-045 wording should be revised to reflect «no user-files to clean», but this is documentation polish — implementation-wise the change is captured here.

---

## Docs references (no orphan refs found)

Grep results:
- No references to `flows_mock_*.json` paths в `docs/`.
- `docs/product/roadmap.md` mentions spec 003 generally, не specific files.
- No CI / build scripts depend on these JSON files by path (assets are copied automatically by AGP).

**Conclusion**: zero orphan references requiring documentation updates.

---

## Decision

**Proceed** with revised T054/T055 semantics. Spec.md FR-045 wording can be touched up later or left as-is (it's directional; implementation matches intent).

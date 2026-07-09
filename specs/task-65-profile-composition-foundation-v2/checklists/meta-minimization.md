# Checklist: meta-minimization — TASK-65 (re-run after revised model)

Applied: 2026-06-30 (2nd pass).

## New abstractions

- [x] CHK001 Every new port has concrete consumer **in this spec** — **yes**.
  - `PoolSource`: HardcodedPoolSource (live) + JsonAssetPoolSource (scaffold + roundtrip test).
  - `ProfileSwitchStrategy`: CopyOnActivateStrategy (live).
  - `PresetRef`: new data class, used by ProfileStore + PresetSwitchService — concrete consumer this spec.
- [x] CHK002 Single-impl interfaces justified — **yes**.
  - `ProfileSwitchStrategy` (single CopyOnActivateStrategy): justified by **known future strategies** (kind-match, sandbox — explicit roadmap, не «future abstraction»).
  - `PoolSource` (2 adapters): justified by known deferred decision (hardcode vs JSON).
- [x] CHK003 No mediator without transformation — **yes**.
- [x] CHK004 No custom DSL — **yes**.

## New modules / packages

- [x] CHK005 N/A — no new Gradle module.
- [x] CHK006 N/A.
- [x] CHK007 No dump module — **yes**.

## New configuration

- [x] CHK008 New config fields have current FR consumer — **yes**.
  - **NEW field `Preset.uid`**: consumed by FR-018 (ProfileStore Map key), FR-015 (migration).
  - **NEW field `Preset.version`**: consumed by FR-018 (composite key).
  - **NEW field `Preset.slug`**: consumed by FR-012 (picker display), Detekt detector (FR-020).
  - `Preset.abstractProfile`: consumed by FR-014 (CopyOnActivateStrategy).
  - **Borderline**: `defaultValue`, `hideInWizard`, `showInSettings` в Config — **no current consumer in TASK-65**, hooks for TASK-71. **Per CLAUDE.md rule 4**: hooks в wire format оправданы если иначе = rewrite. Bumping schemaVersion позже = breaking change. Keep as hooks. **Borderline pass**.
- [x] CHK009 Defaults documented — **yes**. FR-001 lists defaults.

## CLAUDE.md rule 4 self-test

- [x] CHK010 **Test 1 (inlined what would be lost)** applied — **yes**.
  - `PresetRef` inlined to plain `String "simple-launcher"` → lose collision protection between authors. **Real risk identified by owner** (workspace v1 vs workspace by SuperUser v6). **Keep**.
  - `ProfileSwitchStrategy` inlined → lose extensibility to kind-match/sandbox. **Owner explicit roadmap**. **Keep**.
  - `Settings[]` array vs single `appliedSettings: Map`: array form chosen to allow per-entry callback dispatch + order. **Keep**.
- [x] CHK011 **Test 2 (swap cost)** applied — **yes**.
  - Swap `HardcodedPoolSource` → `JsonAssetPoolSource`: ~hours через DI binding.
  - Swap `CopyOnActivateStrategy` → `SandboxStrategy`: ~half day, new adapter + DI.

## Removal validation

- [x] CHK012 Dangling references audited — **partial**. `presetId` removal from wizard.manifest → grep по codebase в plan'е.
- [x] CHK013 Removed code has removal task — **partial**. ProfileSnapshot existing → decision в plan.

---

**Total**: 13/13 ✓ (3 borderline — UX hints hooks, dangling refs audit, ProfileSnapshot — defer to plan)
**Red-only summary**: meta-minimization: 13/13 ✓ (3 borderline — UX hints hooks pass rule 4 review; dangling refs + ProfileSnapshot decision defer to plan).

# Checklist: meta-minimization — Spec 005

**Skill**: `checklist-meta-minimization` | **Status**: ⚠ PASS-WITH-CAVEATS (12/13)

## New abstractions

- [x] CHK001 Every new interface has at least one concrete consumer in this spec — `ActionDispatcher` consumed by `RootComponent.dispatchAction`; `ProviderRegistry` consumed by `AddSlotWizardScreen`.
- [⚠] CHK002 Single-implementation interfaces — `ActionHandler` has 8 implementations (justified). `PlayStoreFallbackResolver` is a single class without an interface; OK as-is. `ActionDispatcher` interface has one impl (`AndroidActionDispatcher`) — justified by **port-shape need**: required for fake adapter (CLAUDE.md §6) and for future `iOSActionDispatcher`. **Open**: confirm this in plan.md §Architecture.
- [x] CHK003 No mediator/orchestrator class that just passes data through.
- [x] CHK004 No new DSL/registry/plugin system. Handler-registry is a `Map<ProviderId, ActionHandler>`, not a runtime plugin system.

## New modules / packages

- [x] CHK005 No new gradle module introduced.
- [x] CHK006 N/A — no new module.
- [x] CHK007 No "utils"/"common" dumping ground.

## New configuration

- [x] CHK008 New config field (`Action.fallback`) consumed by FR US-501..US-506.
- [x] CHK009 `schemaVersion: 1` documented; backward-compat policy in §9.

## CLAUDE.md rule 4 self-test

- [x] CHK010 **Test 1** documented per decision in §5: each abstraction explained ("if removed: lost X").
- [x] CHK011 **Test 2** documented: each one-way door has exit ramp showing ≤ 1 day swap cost.

## Removal validation

- [x] CHK012 Deletions in §6.4 verified by research agent (10 files exist, 3 already absent).
- [x] CHK013 Removed code has explicit removal task in this spec (not "eventually") — fitness function §8.3 enforces 0 residue.

---

## Open items

- **CHK002 (PlayStoreFallbackResolver)**: in plan.md §Architecture, document why it's a class, not extension function on `Context`. Likely answer: needs DI for tests + reusable across handlers. Confirm.

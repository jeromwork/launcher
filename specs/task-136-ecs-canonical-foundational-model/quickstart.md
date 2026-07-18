# Quickstart: verifying the canonical ECS model locally (TASK-136)

How a developer confirms the new model on their machine. All `:core` JVM tests — **no device required** (pure domain refactor; see spec § OEM / Device Matrix). Mirrors the spec's Local Test Path.

---

## 1. Full model suite

```bash
./gradlew :core:test
```
Green ⇒ big-bang rewrite is coherent (SC-010): every consumer compiles against the free-bag `Entity`, no two `Entity` shapes coexist.

## 2. Targeted capability checks

```bash
# Composition — multi-tag membership + test-only TestFlag added to any entity type (SC-001)
./gradlew :core:test --tests "*EntityCompositionTest*"

# Queries over entity.tags + get<T>() (SC-002)
./gradlew :core:test --tests "*ProfileQueryTest*"

# Spawn — free bag from bundle + inline + override + parentRef; bundle not retained (SC-005)
./gradlew :core:test --tests "*ProfileFactoryTest*"

# Serialization roundtrip — entity-grouped JSON → Profile, assert equal (SC-003)
./gradlew :core:test --tests "*ProfileSerializationRoundtripTest*"

# Hierarchy + validation parity with TASK-127 (SC-005)
./gradlew :core:test --tests "*ProfileQueryHierarchyTest*" --tests "*ValidateHierarchyTest*"

# State-as-component — ReconcileEngine swaps LifecycleState, render gating hides Failed/Skipped (FR-STATE)
./gradlew :core:test --tests "*ReconcileEngineStateTest*"
```

## 3. Fitness functions

```bash
# Coverage — every Component subtype has serializer + (where applicable) Provider (SC-006)
./gradlew :core:test --tests "*ComponentCoverageFitnessTest*"

# At-most-one-per-type — guarantees get<T>() unambiguity (CL-3 / FR-015d)
./gradlew :core:test --tests "*AtMostOneComponentPerTypeFitnessTest*"

# Tag consistency — tags explicit only, no auto-derivation (CL-4 / FR-015e)
./gradlew :core:test --tests "*TagConsistencyFitnessTest*"
```

Import-guard (zero Android imports in `Entity`/`Component`/`Blueprint`/`preset/ecs/`) is a source-set placement check — a rogue `import android.*` fails compilation of the JVM test target (SC-008). Core LOC budget (`preset/ecs/` ≤ ~400) is asserted by the LOC-budget fitness test (SC-011).

## 4. Consistency (cleanup) grep gates

```bash
# SC-004 — no manual casts remain
rtk grep "component as\? Component" core app        # → no hits

# SC-007 — no stale model descriptions; ADR + superseded-by in place
rtk grep -i "tagged-component\|not canonical ECS\|discriminated union" docs/architecture/preset-model.md   # → no hits describing current model
rtk grep "Superseded by ADR-013" docs/adr/ADR-012*                # → present
rtk grep "superseded-by: TASK-136" backlog/tasks/task-120* backlog/tasks/task-127*   # → present

# SC-009 — schemaVersion present = 2, no migration machinery
rtk grep "CURRENT_SCHEMA_VERSION" core/src/commonMain/kotlin/com/launcher/preset/model/Profile.kt   # → = 2
rtk grep -i "migrat" core/src/commonMain/kotlin/com/launcher/preset                                   # → no migration writer/reader
```

## 5. What "done locally" looks like

- `./gradlew :core:test` green.
- Rewritten `pool.json` + `bundled-presets/*.json` parse (roundtrip + `ProfileFactory` tests run over them) and still load through `BundledSource`/`ConfigSource` with the `// TODO(shareability)` seam intact (rule 9).
- All grep gates in § 4 clean.
- Emulator smoke (fresh install → wizard → HomeScreen tiles) is a **deferred** gate (`[deferred-local-emulator]`), run at PR/verification time — not part of this dev loop.

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Кратко по-русски

Как проверить новую модель у себя на машине — всё через `:core` тесты, устройство не нужно (это чистый рефактор доменного кода). Одной командой `./gradlew :core:test` убеждаемся, что весь рефактор согласован. Дальше — точечные проверки: композиция (несколько тегов + тестовый компонент навешивается на любую сущность), запросы, «спавн» сущности из бандла, round-trip сериализации, иерархия, состояние-как-компонент. Плюс fitness-тесты: покрытие всех типов, «не больше одного компонента каждого типа», теги только явные. И grep-проверки чистоты: не осталось ручных приведений `as?`, доки переписаны, старым таскам проставлено «заменено TASK-136», версия формата = 2, мигратора нет. Smoke на эмуляторе — отдельный отложенный шаг на этапе PR, не в этом цикле.
<!-- NOVICE-SUMMARY:END -->

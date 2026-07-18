package com.launcher.preset.ecs

/**
 * The **World** is `Profile`'s entity bag (spec Key Entities: "Profile = World").
 * There is no separate World object — `Profile.entities` *is* the World, and the
 * spawn / compose / query primitives in this package ([entity], [get], [with],
 * [com.launcher.preset.model.Profile.family]) operate over it directly.
 *
 * This file documents that mapping and carries the swap seam (FR-012):
 *
 * // TODO(ecs-fleks-migration): World internals swappable to Fleks;
 * // cost = Kotlin 2.0→2.4 upgrade + String→Int id remap; persistence stays ours.
 *
 * What this core deliberately does NOT have (contract §"deliberately does NOT
 * have"): no per-frame system scheduler (our "systems" are `ReconcileEngine` +
 * `Provider`, invoked on events, not every frame); no generic component universe
 * (concrete to `Component`/`Tag`, rule 4); no archetype / sparse-set storage
 * (flat `List<Entity>`; a type-grouped index is an exit ramp only if scale ever
 * demands it — additive, no wire change).
 */

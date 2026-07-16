package com.launcher.preset.query

import com.launcher.preset.model.Component
import com.launcher.preset.model.Tag
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * T127-029 (NFR-003, NFR-005, SC-008) — the linear scan must stay far below the
 * 1 ms budget at MVP scale, including the hierarchy selectors.
 *
 * This exists to justify *not* building an index (research.md R-4/R-7): the
 * moment this fails, the exit ramp (a `Map<parentId, List<Entity>>` built at load)
 * becomes worth its cost.
 *
 * Deliberately generous thresholds — this guards against an accidental O(n²)
 * regression, not against normal CI jitter.
 */
class ProfileQueryBenchmarkTest {

    /** ~40 entities: workspace + 3 flows + 30 tiles + toolbar + 3 buttons. */
    private fun largeProfile() = profileOf(
        *buildList {
            add(entity("ws", Component.Workspace()))
            repeat(3) { i ->
                add(entity("flow-$i", Component.Flow(titleKey = "t$i", order = i), parentId = "ws"))
            }
            repeat(30) { i ->
                add(appTile("tile-$i", "com.app$i", parentId = "flow-${i % 3}"))
            }
            add(entity("toolbar", Component.Toolbar(layoutKey = "bottom"), parentId = "ws"))
            repeat(3) { i ->
                add(
                    entity(
                        "btn-$i",
                        Component.ToolbarButton(targetFlowId = "flow-$i", labelKey = "b$i", order = i),
                        parentId = "toolbar",
                    ),
                )
            }
        }.toTypedArray(),
    )

    private fun assertUnderBudget(name: String, block: () -> Unit) {
        val profile = largeProfile()
        check(profile.components.size >= 38)

        repeat(WARMUP) { block() }
        val elapsed = measureTime { repeat(ITERATIONS) { block() } }
        val perCall = elapsed / ITERATIONS

        assertTrue(
            perCall.inWholeMicroseconds < BUDGET_MICROS,
            "$name took ${perCall.inWholeMicroseconds}µs per call — budget is ${BUDGET_MICROS}µs " +
                "(NFR-003). A linear scan over ~40 entities should be microseconds; " +
                "if this is real, see research.md R-7 for the indexing exit ramp.",
        )
    }

    @Test
    fun homeScreenTiles_isUnderBudget() {
        val profile = largeProfile()
        assertUnderBudget("homeScreenTiles") { profile.homeScreenTiles() }
    }

    @Test
    fun tilesOf_isUnderBudget() {
        val profile = largeProfile()
        assertUnderBudget("tilesOf") { profile.tilesOf("flow-1") }
    }

    @Test
    fun flows_isUnderBudget() {
        val profile = largeProfile()
        assertUnderBudget("flows") { profile.flows() }
    }

    @Test
    fun toolbarButtons_isUnderBudget() {
        val profile = largeProfile()
        assertUnderBudget("toolbarButtons") { profile.toolbarButtons() }
    }

    @Test
    fun byTag_isUnderBudget() {
        val profile = largeProfile()
        assertUnderBudget("byTag") { profile.byTag(Tag.Tile) }
    }

    @Test
    fun fullHomeScreenProjection_isUnderBudget() {
        // What the adapter actually does on every Profile emission.
        val profile = largeProfile()
        assertUnderBudget("full projection") {
            profile.flows().forEach { profile.tilesOf(it.id) }
            profile.toolbarButtons()
        }
    }

    private companion object {
        const val WARMUP = 200
        const val ITERATIONS = 2_000
        const val BUDGET_MICROS = 1_000L // 1 ms (NFR-003)
    }
}

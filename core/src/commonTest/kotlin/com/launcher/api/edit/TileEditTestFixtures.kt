package com.launcher.api.edit

import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ElementId
import com.launcher.api.config.Flow
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.config.Slot
import com.launcher.api.config.SlotKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Test fixtures для F-014 domain tests. Provides:
 *  - canned [ConfigDocument] / [Flow] / [Slot] / [ElementId] builders с
 *    stable UUIDs (deterministic tests),
 *  - one fixed [ServerTimestamp] used across tests so equality checks don't
 *    depend on wall clock.
 *
 * Pure-Kotlin, lives в commonTest. Konsist gate T170 (when uncommented in
 * Phase 8) ignores this file via `commonTest` path filter.
 */
internal object TileEditTestFixtures {

    val FIXED_TIMESTAMP = ServerTimestamp(epochSeconds = 1_747_166_400L, nanoseconds = 0)
    const val FIXED_DEVICE_ID = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a"

    val FLOW_A_ID = ElementId("11111111-1111-4111-8111-111111111111")
    val FLOW_B_ID = ElementId("22222222-2222-4222-8222-222222222222")

    val SLOT_1_ID = ElementId("a1111111-1111-4111-8111-111111111111")
    val SLOT_2_ID = ElementId("a2222222-2222-4222-8222-222222222222")
    val SLOT_3_ID = ElementId("a3333333-3333-4333-8333-333333333333")
    val SLOT_4_ID = ElementId("a4444444-4444-4444-8444-444444444444")

    fun openAppSlot(id: ElementId, packageName: String = "com.example.app"): Slot = Slot(
        id = id,
        kind = SlotKind.OpenApp,
        args = JsonObject(mapOf("packageName" to JsonPrimitive(packageName))),
    )

    fun flow(id: ElementId, slots: List<Slot> = emptyList(), title: String = "Test Flow"): Flow =
        Flow(id = id, title = title, slots = slots)

    fun config(
        presetId: String = "workspace",
        flows: List<Flow> = emptyList(),
    ): ConfigDocument = ConfigDocument(
        serverUpdatedAt = FIXED_TIMESTAMP,
        lastWriterDeviceId = FIXED_DEVICE_ID,
        presetId = presetId,
        flows = flows,
        contacts = emptyList(),
    )

    /** Single-flow config с 3 slots — common starting point. */
    fun configWithThreeSlots(): ConfigDocument = config(
        flows = listOf(
            flow(
                id = FLOW_A_ID,
                slots = listOf(
                    openAppSlot(SLOT_1_ID, "com.example.one"),
                    openAppSlot(SLOT_2_ID, "com.example.two"),
                    openAppSlot(SLOT_3_ID, "com.example.three"),
                ),
            ),
        ),
    )
}

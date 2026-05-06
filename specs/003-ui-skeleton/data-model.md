# Data Model: UI Skeleton (Spec 003)

## FlowDescriptor

```kotlin
data class FlowDescriptor(
    val schemaVersion: Int,          // wire-format version; current = 1
    val id: String,                  // stable key, e.g. "flow_family"
    val name: String,                // display name, e.g. "Семья"
    val templateId: String,          // template key, e.g. "contacts" | "admin_devices"
    val slots: List<SlotDescriptor>,
)
```

## SlotDescriptor

```kotlin
data class SlotDescriptor(
    val id: String,                  // stable key, e.g. "slot_anna_call"
    val label: String,               // display label, e.g. "Аня — Позвонить"
    val iconRef: String,             // asset/resource ref; "" = default avatar placeholder
    val action: SlotAction,
)
```

## SlotAction (sealed)

```kotlin
sealed class SlotAction {
    data class WhatsAppCall(
        val contactRef: String,      // e.g. "contact_anna" — maps to CommunicationConfigValidator
        val actionType: CommunicationActionType, // CALL | VIDEO
    ) : SlotAction()

    data class OpenApp(
        val packageName: String,
    ) : SlotAction()

    object Placeholder : SlotAction()
}
```

## FlowTemplate

```kotlin
data class FlowTemplate(
    val id: String,                  // "contacts" | "admin_devices"
    val labelResKey: String,         // string resource key for display
    val availableInPresets: Set<String>, // e.g. setOf("senior-launcher", "flow-light")
)
```

## FlowRepository (port interface)

```kotlin
interface FlowRepository {
    suspend fun loadFlows(): List<FlowDescriptor>
    fun availableTemplates(presetId: String): List<FlowTemplate>
}
```

## flows_mock.json (wire format, schemaVersion: 1)

```json
{
  "schemaVersion": 1,
  "flows": [
    {
      "id": "flow_family",
      "name": "Семья",
      "templateId": "contacts",
      "slots": [
        {
          "id": "slot_anna_call",
          "label": "Аня — Позвонить",
          "iconRef": "",
          "action": {
            "type": "whatsapp_call",
            "contactRef": "contact_anna",
            "actionType": "CALL"
          }
        },
        {
          "id": "slot_oleg_call",
          "label": "Олег — Позвонить",
          "iconRef": "",
          "action": {
            "type": "whatsapp_call",
            "contactRef": "contact_oleg",
            "actionType": "CALL"
          }
        }
      ]
    }
  ]
}
```

## FlowPreset (Phase 9)

```kotlin
enum class FlowPreset(
    val slug: String,            // wire-format identifier, never rename without migration
    val titleResKey: String,
    val descriptionResKey: String,
    val iconResKey: String,
) {
    WORKSPACE("workspace", ...),
    LAUNCHER("launcher", ...),
    SIMPLE_LAUNCHER("simple-launcher", ...),
}
```

## PresetRepository (Phase 9)

```kotlin
interface PresetRepository {
    suspend fun getActivePreset(): FlowPreset?     // null until first-launch picker is completed
    suspend fun setActivePreset(preset: FlowPreset)
    suspend fun clear()
    fun observeActivePreset(): Flow<FlowPreset?>
}
```

Adapters:
- `InMemoryPresetRepository` (in `:core`) — for tests and Core's default before app wires DataStore
- `DataStorePresetRepository` (in `:app`) — production adapter on Preferences DataStore (key `active_preset_slug` in `launcher_preset` store)

Mock JSON files split by preset (under `core/src/main/assets/`):
- `flows_mock_workspace.json` — 2 flows (Контакты, Приложения)
- `flows_mock_launcher.json` — 1 flow (Главное)
- `flows_mock_simple-launcher.json` — 1 flow (Семья)

`MockFlowRepository.loadFlows()` resolves filename from `presetRepository.getActivePreset()?.slug ?? "simple-launcher"`.

## Contract

| Contract ID | Major Version | Published in |
|-------------|--------------|--------------|
| LAUNCHER_FLOWS | 1 | CoreContractVersions |
| LAUNCHER_PRESETS | 1 | CoreContractVersions |

Контракт публикует: `FlowDescriptor`, `SlotDescriptor`, `SlotAction`, `FlowRepository`, `FlowPreset`, `PresetRepository`.

## Миграция из 002

`CommunicationActionType` (CALL / VIDEO) остаётся в `com.launcher.api.CommunicationModels`.  
`SlotAction.WhatsAppCall.actionType: CommunicationActionType` — прямая ссылка без дублирования.

Маппинг mock-данных:
- `contact_anna` → `whatsapp_tiles_mock.json` tileId `tile_anna`, capability `["CALL", "VIDEO"]`
- `contact_oleg` → `whatsapp_tiles_mock.json` tileId `tile_oleg`, capability `["CALL"]`

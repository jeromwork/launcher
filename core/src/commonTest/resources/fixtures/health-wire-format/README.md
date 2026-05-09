# Health wire-format fixtures (spec 006)

Human-readable fixtures for the [`Health` wire format v1.0.0](../../../../../../../specs/006-provider-capabilities-and-health/contracts/health-wire-format.md).

Mirror inline test cases в [`HealthWireFormatTest.kt`](../../../kotlin/com/launcher/api/health/HealthWireFormatTest.kt).

## Files

- `wifi-online-v1.json` — typical state: WiFi online, ringer 60%, not charging, 80% battery
- `airplane-offline-v1.json` — airplane mode (или нет покрытия): connectivity=None, ringer 50%
- `muted-charging-v1.json` — ringer выключен (mute=true, percent=0), заряжается, mobile data
- `forward-compat-v999.json` — schemaVersion 999 + extra unknown fields, used by SC-015 forward-compat test

## On unknown enum values

Wire-format reader использует kotlinx-serialization напрямую. **Unknown `connectivity` values** (например будущий `"Vpn"` или `"Ethernet"`) вызвали бы `SerializationException`.

Защитa живёт в **adapter layer** (`AndroidHealthCollector` / `HealthSnapshotProjection`): при чтении из untrusted источника адаптер вызывает `Connectivity.fromWireOrNone(name)` per FR-44, который безопасно мапит unknown в `None`.

`forward-compat-v999.json` поэтому держит **известный** `"Wifi"` и тестирует только schemaVersion + unknown fields tolerance. FR-44 fallback покрывается отдельным unit-тестом `Connectivity.fromWireOrNone()`.

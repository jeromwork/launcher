# LauncherSettings wire-format fixtures (spec 006)

Human-readable fixtures for the [`LauncherSettings` wire format v1.0.0](../../../../../../../specs/006-provider-capabilities-and-health/contracts/launcher-settings-wire-format.md).

Mirror inline test cases в [`LauncherSettingsWireFormatTest.kt`](../../../kotlin/com/launcher/api/settings/LauncherSettingsWireFormatTest.kt).

## Files

- `simple-launcher-defaults-v1.json` — defaults для senior preset (`simple-launcher`): оба banner toggle ON
- `workspace-defaults-v1.json` — defaults для `workspace`/`launcher`: оба OFF
- `airplane-only-v1.json` — частичная конфигурация (например пользователь явно отключил mute банер)
- `forward-compat-v999.json` — schemaVersion 999 + reserved fields, used by SC-015 forward-compat test

## Reserved future fields

Тестовая fixture `forward-compat-v999.json` содержит **зарезервированные** поля, которые появятся в будущих спеках без bump SUPPORTED_SCHEMA_VERSION (additive optional, FR-042):

- `banners.offline` — спек 013 banner про отсутствие интернета
- `raiseRingerOnLongOffline` — спек 013 toggle для эскалации громкости
- `escalation.firstStepMinutes` / `subsequentStepMinutes` / `stepPercent` — спек 013 escalation параметры

Спек 006 reader должен **игнорировать** эти поля (через `ignoreUnknownKeys = true` в `WireFormatJson.json`) — это и проверяет fixture.

# Capability wire-format fixtures (spec 006)

Human-readable fixtures for the [`Capability` wire format v1.0.0](../../../../../../../specs/006-provider-capabilities-and-health/contracts/capability-wire-format.md).

These files mirror inline test cases в [`CapabilityWireFormatTest.kt`](../../../kotlin/com/launcher/api/capability/CapabilityWireFormatTest.kt). They exist для:
- human inspection during code review,
- copy-paste examples for new providers,
- diff visibility when wire-format changes (PR reviewer sees a fixture file change, not just an obscure test string).

## Files

- `whatsapp-available-v1.json` — installed provider with versionCode
- `telegram-unavailable-v1.json` — uninstalled provider (versionCode omitted, defaults to null)
- `custom-with-sha256-v1.json` — custom-namespace icon with sha256 (форма данных reserved для спека 007)
- `forward-compat-v999.json` — schemaVersion 999 + extra unknown fields, used by SC-015 forward-compat test

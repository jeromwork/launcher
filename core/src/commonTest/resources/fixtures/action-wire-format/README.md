# Wire-format fixtures (spec 005)

Human-readable fixtures for the [`Action` wire format v1.0.0](../../../../../../../specs/005-action-architecture-v2/contracts/action-wire-format.md).

These files **mirror** the inline string literals in
[`ActionWireFormatTest.kt`](../../../kotlin/com/launcher/api/action/ActionWireFormatTest.kt).
Tests use the inline literals (zero KMP-resources access overhead in
`commonTest`); these files exist for:

- human inspection during code review,
- copy-paste examples for new providers / new payload variants,
- diff visibility when wire-format changes (PR reviewer sees a fixture file change, not just an obscure string in test source).

When you add a new `ActionPayload` variant: add a roundtrip test inline in
`ActionWireFormatTest.kt` AND a matching fixture file here.

## Files

- `whatsapp-message-v1.json` — minimal whatsapp message
- `whatsapp-call-voice-v1.json` — whatsapp voice call
- `whatsapp-call-video-v1.json` — whatsapp video call
- `phone-v1.json` — `Intent.ACTION_DIAL` payload
- `sms-v1.json` — sms with optional body
- `url-v1.json` — browser URL
- `youtube-video-v1.json` — youtube video by id
- `open-settings-v1.json` — system settings
- `custom-v1.json` — escape-hatch payload (forward-compat for unknown providers)
- `fallback-chain-v1.json` — depth-2 fallback chain (whatsapp → app → browser)
- `legacy-spec003-whatsapp-call-voice.json` — pre-spec-005 shape, parsed via `migrateLegacyAction`
- `legacy-spec003-open-app.json` — pre-spec-005 shape

LEGACY-BRIDGE-EXPIRES-IN-SPEC-006 — `legacy-*.json` fixtures are removed when
the bridge is removed (see Clarification C5).

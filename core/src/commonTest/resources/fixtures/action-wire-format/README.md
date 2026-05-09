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

- `open-app-v1.json` — `Intent` to launch an installed package, with store fallback hint
- `whatsapp-message-v1.json` — minimal whatsapp message
- `whatsapp-call-voice-v1.json` — whatsapp voice call
- `whatsapp-call-video-v1.json` — whatsapp video call
- `phone-v1.json` — `Intent.ACTION_DIAL` payload
- `sms-v1.json` — sms with optional body
- `url-v1.json` — browser URL
- `youtube-home-v1.json` — youtube app home
- `youtube-video-v1.json` — youtube video by id
- `youtube-channel-v1.json` — youtube channel by handle
- `open-settings-v1.json` — system settings
- `custom-empty-v1.json` — escape-hatch payload, no params
- `custom-populated-v1.json` — escape-hatch payload with params map
- `fallback-chain-v1.json` — depth-2 fallback chain (whatsapp → app → browser)

The legacy `legacy-spec003-*.json` fixtures и сопровождавший их
`migrateLegacyAction` бридж были удалены при поставке спека 006 (см. spec 005
Clarification C5). All assets теперь читаются прямо через `ActionWireFormat.decode`.

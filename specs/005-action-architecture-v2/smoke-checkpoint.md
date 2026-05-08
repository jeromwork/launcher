# Spec 005 — Smoke Checkpoint

Date: 2026-05-08

## T642 — Two-emulator smoke

**Status: ✅ PASS — golden path verified on both presets.**

Two `Medium_Phone_API_36.1` emulators running side-by-side via Android Studio (CLI cold-boot hangs at QEMU thread level on this AMD host; Studio's runner works around it). Fresh APK md5-verified on both via the §5a checksum gate. Screenshots captured under `build/smoke/` (gitignored).

### Setup screenshots

| Preset | Screenshot | Notes |
|---|---|---|
| `simple-launcher` (emulator-5556) | `build/smoke/home-simple-launcher.png` | One bottom tab "Семья", two big tiles "Аня — Позвонить" / "Олег — Позвонить", + button «+ Добавить». Senior-safe layout — no app drawer, no clutter. |
| `workspace` (emulator-5554) | `build/smoke/home-workspace.png` | Two bottom tabs (Контакты / Приложения), 4-tile contact grid with `null`-action placeholders rendering correctly as empty cards (Контакт 3, Контакт 4). |

### Tap dispatch — fallback chain (US-501 + US-502)

Tap "Аня — Позвонить" tile on `simple-launcher`:

- WhatsApp not installed in the bare emulator → `AndroidProviderRegistry` reports `Missing` → dispatcher recurses into `Action.fallback`.
- Fallback Action: `providerId=phone`, `payload.kind=phone`, `number=+79991234001` (Anna's phoneE164 from `mock_contacts.json`).
- `PhoneHandler` fires `Intent.ACTION_DIAL` with `tel:+79991234001`.
- **Result**: Google's stock dialer opens on the emulator with `+7 999 123-40-01` pre-filled. Screenshot `build/smoke/tap-anna-call.png`.

This is the spec's headline integration: AddSlotWizard would show "WhatsApp недоступно" on this device, but a tile authored against the new Action wire format with a phone fallback still completes the intent (the senior never sees an error). The legacy spec 002 confirmation overlay is gone — the call flows through one provider-agnostic dispatch.

### Tap dispatch — Browser handler (US-505)

Switch to "Приложения" tab on `workspace`, tap "Браузер":

- `Action(providerId=browser, payload=url("https://duckduckgo.com"))` from `flows_mock_workspace.json`.
- `BrowserHandler` fires `Intent.ACTION_VIEW` with the https URI.
- **Result**: Chrome opens with the loading spinner for duckduckgo.com. Screenshot `build/smoke/tap-browser.png`.

Confirms scheme allow-list works (https only, security CHK-011); the production URL would be supplied by the wizard or by a remote-pushed slot in spec 007+.

### Coverage tally

| Surface | Verified | Method |
|---|---|---|
| simple-launcher home renders | ✅ | screencap |
| workspace home renders + 2 tabs | ✅ | screencap |
| WhatsApp tile → fallback to phone | ✅ | tap → screencap (dialer with +7 999 123-40-01) |
| Browser tile → Chrome opens https URL | ✅ | tap → screencap (chrome splash) |
| Snackbar on Failure | ⚠ NOT EXERCISED | would need a tile that genuinely fails (e.g. malformed URL); covered by `FlowComponentTest` unit test instead |
| TileCard 500 ms debounce | ⚠ NOT EXERCISED ON DEVICE | unit test `TileCardTest` covers it deterministically; visual repro depends on emulator timing |

### What was not run

- `Custom("smart_assistant", …)` forward-compat snackbar — no test fixture currently authors that payload at runtime; the unit-test layer covers the dispatcher branch (`AndroidActionDispatcherIntegrationTest.missingHandler_noFallback_returnsUnknownInVersion`).
- Long-tap / repeat-tap stress — not part of spec 005's acceptance.

## T643 — TalkBack walkthrough on AddSlotWizardScreen

**Status: ⚠ NOT RUN — requires a human ear**

Programmatically reaching the wizard from a fresh emulator is straightforward (tap "+ Добавить" on either preset → wizard opens), but verifying TalkBack announcement order and contentDescription quality requires hearing the screen reader. Carry-over for the next session.

**To execute:**

1. Settings → Accessibility → enable TalkBack.
2. From Home: tap "+ Добавить" → AddSlotWizard.
3. Swipe right through every provider row; confirm:
   - Available providers announce only their label.
   - Missing providers announce label + "установить" + recommended package.
   - NotApplicable providers announce label + "недоступно" + reason.
4. Confirm "Готово" reachable in ≤ 3 swipes from wizard top.
5. Document gaps inline above each step.

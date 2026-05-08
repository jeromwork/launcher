# Checklist: ux-quality — Spec 005

**Skill**: `checklist-ux-quality` | **Status**: ⚠ PASS-WITH-CAVEATS (18/22)

## Completeness — coverage of screens

- [x] CHK001 Screens of this spec listed in §4.1.3: `AddSlotWizardScreen` (modified), `ConfirmationOverlay` (modified), `FlowScreen` (modified). No new screens.
- [⚠] CHK002 UX states for `AddSlotWizardScreen` provider-list: **available**, **missing-with-install-link**, **not-applicable** — covered. **Empty list** (no providers at all — extreme edge case) and **unknown-provider-from-config** (older app reading newer config per Clarification C1) not specified.
- [x] CHK003 Navigation transitions inherit from spec 004; no new flows added.
- [x] CHK004 Overlays (`ConfirmationOverlay`, `WarningOverlay` from spec 003) — re-used; behaviour inherited.

## Clarity — terminology and rules

- [x] CHK005 `provider`, `providerId`, `payload`, `fallback`, `dispatch`, `availability` — used consistently in spec body and §5.
- [x] CHK006 No vague qualifiers; metrics in §9 NFR table.
- [x] CHK007 §7.6 explicit: "тап" not "взаимодействие".
- [⚠] CHK008 Button labels not given as exact strings — §4.1.6 only mentions key naming convention (`provider_name_whatsapp`). Strings deferred to localization checklist + plan.md.

## Consistency

- [x] CHK009 In-Scope §4.1 and US §3 align — every In-Scope item produces FR(s) covering ≥1 US.
- [x] CHK010 Confirmation policy: `ConfirmationOverlay` re-used as in spec 003 (one-tap → confirm step → dispatch).
- [x] CHK011 §7.6 specifies UI debounce 500ms — applies uniformly to all action surfaces.

## Acceptance — measurability

- [x] CHK012 Each US has acceptance criteria column (§3 table).
- [x] CHK013 NFR §9 metrics tied to UX moments (cold start, dispatch latency).
- [x] CHK014 Returning-user UX: no new state added in this spec; resume-from-background unchanged.

## Coverage — alternative paths

- [x] CHK015 Each US has negative-path: US-501..US-506 have install-fallback; US-507 has "hide unavailable"; US-508 explicit.
- [x] CHK016 Multiple entry points unchanged from 004 (Home + FirstLaunch only).
- [⚠] CHK017 Long-pause scenarios — spec 005 removes `ReturnContext` (§5.3); no new "user comes back hours later" UX. **Open**: explicit note that no such UX exists — currently implicit.

## Non-functional UX

- [x] CHK018 Accessibility checklist exists (separate file).
- [x] CHK019 Localisation checklist exists (separate file).
- [⚠] CHK020 Diagnostic UX (does user see that action was dispatched / failed?) — partially covered: `ProjectEvent.ActionDispatched` is internal logging, but user-visible feedback for `Failure` state not specified ("toast?", "snackbar?"). §7.4 says "diagnostic event"; §3 US-508 says "toast/snackbar" — not which. **Open**: pick one.

## Dependencies / assumptions

- [x] CHK021 No out-of-scope dependencies (no embedded WhatsApp UI etc.).
- [x] CHK022 Mock-data limitations: `mock_contacts.json` migration noted in §4.1.4.

---

## Open items

- **CHK002** — `AddSlotWizardScreen`: empty-list and unknown-provider-from-config states (latter per Clarification C1).
- **CHK008** — concrete button labels (or string token IDs) for `ConfirmationOverlay` actions.
- **CHK017** — explicit note that spec 005 removes long-pause-return UX (replacement of ReturnContext deletion).
- **CHK020** — choose snackbar vs toast for `DispatchResult.Failure` user feedback. Likely snackbar with action button "Try again".

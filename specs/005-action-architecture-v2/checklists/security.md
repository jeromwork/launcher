# Checklist: security — Spec 005

**Skill**: `checklist-security` | **Status**: ⚠ PASS-WITH-CAVEATS (20/24)

Aligned with [OWASP MASVS](https://mas.owasp.org/MASVS/) controls and Article XIV.

## Data at rest (MASVS-STORAGE)

- [x] CHK001 No new PII in clear-text storage — spec 005 only *removes* persistence (`ReturnContextStore` deletion).
- [x] CHK002 N/A — no auth tokens.
- [x] CHK003 N/A — no cache files added.
- [⚠] CHK004 §7.4 says "no PII (никаких номеров, контактов, URL'ов)" in `ProjectEvent.ActionDispatched`. **Open**: spec doesn't enumerate **what fields IS allowed** — risk that future contributors add fields thinking they're benign. Plan.md must produce explicit logging contract `contracts/diagnostics-events-v2.md` with allowed-field whitelist.

## Data in transit (MASVS-NETWORK)

- [x] CHK005 N/A — no network calls in this spec.
- [x] CHK006 N/A.

## Authentication / Authorization (MASVS-AUTH)

- [x] CHK007 Each handler invocation does not require any new permission or role (per §7.5).
- [x] CHK008 No security-by-obscurity.

## Platform interaction (MASVS-PLATFORM)

- [x] CHK009 Only the existing `HomeActivity` and `FirstLaunchActivity` from spec 004 are exported (HOME role); no new exported components.
- [x] CHK010 Intents to other apps use either explicit deep-link URLs (whatsapp `wa.me`, youtube `vnd.youtube:`) or `ACTION_VIEW`/`ACTION_DIAL` with sanitised URI. WhatsApp handler uses `setPackage("com.whatsapp")` after availability check.
- [⚠] CHK011 `Custom.params: Map<String, String>` (Clarification C2) is **passed through** without validation in current spec text. **Open**: plan.md must define `CustomPayloadValidator` with bounds: max keys (e.g. 16), max key length (64), max value length (1024), no nested-JSON-as-string. Otherwise unbounded payload = potential intent-extras DoS / Parcel size exhaustion.
- [x] CHK012 Handler input is `Action`, a sealed-class derived type — no Parcelable of unknown class hierarchy from external apps in this spec's scope.
- [x] CHK013 N/A — no `ContentProvider`.
- [x] CHK014 N/A — no WebView.

## Permissions (Article XIV)

- [x] CHK015 No new permissions per §7.5; `phone` uses `ACTION_DIAL` (no `CALL_PHONE` needed).
- [x] CHK016 No "for future use" permission requested.
- [x] CHK017 Each handler designed to graceful-fail when target app missing — fallback chain.
- [x] CHK018 `docs/compliance/permissions-and-resource-budget.md` update planned in §7.5.

## Privacy (Article XIV §3, §4)

- [⚠] CHK019 Local-first preferred — spec is fully local. **Open**: confirm in plan.md that `ProjectEvent.ActionDispatched` events stay in-process (`EventRouter`) and don't pipe to remote analytics in this spec.
- [x] CHK020 Local-first OK.
- [x] CHK021 Nothing leaves device in this spec.
- [⚠] CHK022 Data minimisation: handler args (`contactRef`, `number`, `url`) are passed to system intents, not logged. **Open**: explicitly document — "handler arguments never logged; only `providerId` + boolean fallback flag" — as logging-contract task.

## Build hardening

- [x] CHK023 `BuildConfig.DEBUG` gating inherited from project setup — no new debug flags.
- [x] CHK024 Backup rules unaffected — spec only deletes existing data.

---

## Open items

- **CHK004** — `contracts/diagnostics-events-v2.md` with allowed-field whitelist for `ActionDispatched` event.
- **CHK011** — `CustomPayloadValidator` design: max keys 16, max key length 64, max value length 1024, reject nested.
- **CHK019** — plan.md confirmation: local-only in this spec; no remote analytics pipe.
- **CHK022** — logging contract: handler arguments off-limits.

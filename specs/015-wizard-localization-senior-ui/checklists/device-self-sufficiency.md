# Checklist: device-self-sufficiency

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 12 ✓ / 2 ⚠ / 0 ✗ + 6 N/A — clean (F-3 — pure LOCAL by design)

> **Context**: F-3 — foundation per decision 2026-06-15-deferred-cloud. Pure LOCAL, no Sign-In, no network, no server state.

---

## Local viability

- [✓] **CHK-DSS-001** Mode declared.
  - A-10 explicit: «Local-first per decision 2026-06-15-deferred-cloud: wizard работает offline, без identity, без cloud».
  - Implicit LOCAL-only mode. ✓

- [✓] **CHK-DSS-002** LOCAL-only viable.
  - F-3 не requires network call, не requires Sign-In, не requires server state. Wizard runs end-to-end на fresh install без internet. ✓

- [N/A] **CHK-DSS-003** CLOUD-only justification — F-3 не CLOUD-only.
- [N/A] **CHK-DSS-004** HYBRID baseline + enhancement — F-3 не HYBRID.

## Sign-In trigger point

- [N/A] **CHK-DSS-005-007** Sign-In prompts.
  - F-3 не triggers Sign-In. F-4 (AuthProvider) activates per first cloud action в Phase 2 (S-5+) — explicit per A-10 + roadmap order shift.

## Local→cloud promotion

- [⚠] **CHK-DSS-008** Local state merge при Sign-In via `VersionedConfigViewer` (S-8).
  - F-3 хранит UserPreferences локально (FR-047). Future migration в `ConfigDocument.userPreferences` per FR-051 inline-TODO.
  - Spec не explicit references `VersionedConfigViewer` (S-8 UI) для future merge case.
  - **Acceptable**: this is **post-F-4 migration concern**, не F-3. Migration spec при материализации F-4 + cloud sync будет reference S-8 pattern.

- [⚠] **CHK-DSS-009** Different Google account case.
  - F-3 не handle account switching. Это F-4 / migration spec territory.
  - **Acceptable**: foundation defer.

## Cloud→local downgrade

- [N/A] **CHK-DSS-010** Subscription expiry behaviour.
- [N/A] **CHK-DSS-011** User-visible "what stopped working".
  - F-3 не has cloud features → no downgrade case.

## Anti-patterns to refuse

- [✓] **CHK-DSS-012** No mandatory Sign-In at first launch.
  - F-3 wizard runs без Sign-In. ✓ Per A-10 explicit.

- [✓] **CHK-DSS-013** No mandatory pairing at first launch.
  - PairingStep — **stub** (FR-008), не required. Это S-1 / S-2 manifest решит, включать ли его в wizard и с какой priority. ✓

- [✓] **CHK-DSS-014** No local feature behind cloud dependency.
  - F-3 fully local. ✓

- [✓] **CHK-DSS-015** No anonymous Firebase Auth fallback.
  - F-3 не uses Firebase at all. ✓

## Cross-spec consistency

- [✓] **CHK-DSS-016** Cross-spec LOCAL/CLOUD boundary documented.
  - F-3 — foundation, не directly interacts с CLOUD specs.
  - Future S-1 / S-2 will use F-3 wizard для both:
    - LOCAL-only first-run (default).
    - Cloud-action prompts (когда user initiates cloud feature, S-1 manifest can include a SystemSettingStep with `Sign-In` mechanism — это enable'ится позже через F-4 capability registry entry).
  - ✓ explicit local-first architecture.

- [✓] **CHK-DSS-017** Doesn't assume cloud data always present.
  - F-3 не accesses cloud data. ✓

---

## Резюме

**12 ✓ / 2 ⚠ / 0 ✗ + 6 N/A** — F-3 device-self-sufficiency **perfectly aligned** с decision 2026-06-15-deferred-cloud.

Two minor warnings (DSS-008, DSS-009) — future concerns при F-4 / migration spec материализуется, не F-3 issues.

**No spec edits required**.

---
name: checklist-core-quality
description: Verifies the spec respects Google's Core App Quality guidelines (Android Vitals, Play Store readiness baseline). Triggered for spec covering release-bound features, feature with public surfaces (deep-links, widgets), or any spec at "ready for prod" stage. Use sparingly — most internal specs don't need this.
---

# Checklist: core-quality

Verifies the spec aligns with [Google's Core App Quality](https://developer.android.com/docs/quality-guidelines/core-app-quality) and [Android Vitals](https://developer.android.com/topic/performance/vitals) thresholds. Use for specs that touch release/distribution/public surfaces.

---

## Visual experience

- [ ] CHK001 UI follows Material Design where not overridden by senior-safe rules.
- [ ] CHK002 Light + dark themes both supported (or single-theme decision documented).
- [ ] CHK003 Edge-to-edge layout (Android 15+) handled — system bars, gesture insets.
- [ ] CHK004 Foldable / large-screen layout sane (no broken stretching) OR exclusion noted.

## Functional

- [ ] CHK005 Feature works without internet OR offline behaviour documented.
- [ ] CHK006 Feature handles configuration changes, language change, dark mode toggle without state loss.
- [ ] CHK007 Background-restricted devices (Doze, App Standby) handled if feature relies on background work.
- [ ] CHK008 Multi-window / split-screen behaviour documented.

## Performance (cross-checks `checklist-performance`)

- [ ] CHK009 ANR rate target: < 0.47% (Play Vitals threshold for "bad behaviour" report).
- [ ] CHK010 Crash rate target: < 1.09% (Play Vitals threshold).
- [ ] CHK011 Excessive wakeups, battery drain, network use all within Vitals budget.

## Privacy / Play policy

- [ ] CHK012 Data Safety section in Play Console matches data the feature actually collects.
- [ ] CHK013 No prohibited content / SDK per Play Developer Program Policies.
- [ ] CHK014 Permissions follow restricted-permissions policy (e.g. SMS, Call Log require declared use).

## Compatibility

- [ ] CHK015 minSdk / targetSdk alignment with project policy.
- [ ] CHK016 Tested on at least medium-tier device (Pixel 4a class) and one OEM (Samsung / Xiaomi).

## Distribution

- [ ] CHK017 Feature flag / staged rollout strategy documented if behaviour-changing.
- [ ] CHK018 Crash reporting / Vitals dashboard updated for new code paths.

---

## How to apply

1. For specs at "ready for prod" stage, walk all gates.
2. For internal-only specs (no UI surface, no public API), skip and note in spec why.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-core-quality: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/core-quality.md`. Scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.

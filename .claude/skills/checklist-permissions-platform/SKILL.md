---
name: checklist-permissions-platform
description: Verifies runtime-permission flows, OEM-specific behaviour quirks (Samsung, Xiaomi MIUI, Huawei EMUI), Android version-specific requirements (package visibility on 11+, scoped storage on 10+, foreground service types on 14+), and HOME/launcher role constraints. Triggered by mentions of permission, manifest, OEM, Samsung, Xiaomi, Huawei, package visibility, Android 11+, scoped storage, HOME, launcher.
---

# Checklist: permissions-platform

Verifies the spec accounts for **Android-specific platform constraints** that frequently surprise non-Android developers. Aligned with Article XIV and Article III §3 (OEM resilience) of [`/.specify/memory/constitution.md`](/.specify/memory/constitution.md).

---

## Runtime permissions

- [ ] CHK001 Each runtime permission requested has explicit user-value justification in spec.
- [ ] CHK002 First-launch permission flow specified — when prompted, in what context, what the user sees.
- [ ] CHK003 Re-prompt strategy specified — first denial vs "don't ask again" handled separately.
- [ ] CHK004 Settings deep-link path designed for permanent denial recovery.
- [ ] CHK005 Pre-permission rationale screen specified per Material Design guidance (when appropriate).

## Manifest declarations

- [ ] CHK006 Required permissions in `AndroidManifest.xml` listed; no broad permissions ("for safety").
- [ ] CHK007 `<uses-feature>` declarations match actual hardware needs (e.g. telephony, NFC) with `required="false"` when feature is graceful-degraded.
- [ ] CHK008 `<queries>` element declared for any package the app inspects (Android 11+ package visibility) — without it, `getInstalledApplications` returns empty.

## Android version specifics

- [ ] CHK009 Scoped storage compliance for any file I/O (Android 10+).
- [ ] CHK010 Foreground service `type` declared if used (Android 14+ enforcement).
- [ ] CHK011 Exact alarms (`SCHEDULE_EXACT_ALARM`) avoided unless justified; user-grant flow on Android 12+ designed.
- [ ] CHK012 Notification permission flow on Android 13+ (POST_NOTIFICATIONS) specified.
- [ ] CHK013 Predictive back gesture (Android 14+) compatibility documented for screens that override back.

## HOME / launcher role (project-specific)

- [ ] CHK014 If feature interacts with HOME/ROLE_HOME: behaviour when role denied documented.
- [ ] CHK015 If feature requires being default launcher: fallback behaviour documented.

## OEM quirks

- [ ] CHK016 Samsung: KNOX restrictions on AccessibilityService (if used) acknowledged.
- [ ] CHK017 Xiaomi MIUI: aggressive battery saver / autostart whitelisting addressed if feature relies on background work.
- [ ] CHK018 Huawei EMUI: Push-to-protected-apps process for background work documented if applicable.
- [ ] CHK019 OEM launcher-replacement quirks (Samsung One UI, OnePlus OxygenOS) noted for HOME-related features.

## Package visibility (Android 11+)

- [ ] CHK020 If feature inspects or launches other apps: relevant `<queries>` entries declared in manifest.
- [ ] CHK021 If app needs to detect ANY installed app (broad query): `QUERY_ALL_PACKAGES` justified per Play policy.

## Compliance docs

- [ ] CHK022 [`docs/compliance/permissions-and-resource-budget.md`](docs/compliance/permissions-and-resource-budget.md) updated with delta for this spec (added/removed/changed).

---

## How to apply

1. List every permission, manifest entry, OS-API, OEM-touchpoint introduced.
2. Walk gates per item.
3. Failures → add FR or shrink scope.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-permissions-platform: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/permissions-platform.md`. Scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.

---
name: checklist-security
description: Verifies security and privacy requirements per OWASP MASVS (Mobile Application Security Verification Standard) and Article XIV of constitution.md. Catches PII leakage, intent injection, exported components without verification, broad permissions, untrusted deep-link payloads. Triggered by mentions of auth, credential, token, encryption, PII, contact, payment, intent extras, deep-link payload, exported activity, content provider.
---

# Checklist: security

Verifies the spec addresses security and privacy at the requirements level. Aligned with [OWASP MASVS](https://mas.owasp.org/MASVS/), Article XIV of [`/.specify/memory/constitution.md`](/.specify/memory/constitution.md), and rule §6 of Android Vitals.

Reference: [`specs/002-whatsapp-tile-return/checklists/security.md`](specs/002-whatsapp-tile-return/checklists/security.md).

---

## Data at rest (MASVS-STORAGE)

- [ ] CHK001 No PII (name, phone, email, contact ref, location) stored in clear-text SharedPreferences / unencrypted files unless justified.
- [ ] CHK002 Sensitive data (auth tokens, biometric refs) stored in EncryptedSharedPreferences / Keystore, not bare SharedPreferences.
- [ ] CHK003 Cache files containing user data have TTL and clear-on-uninstall policy.
- [ ] CHK004 Logging excludes PII — logs categorize, not enumerate (`[INFO] action.dispatched providerId=whatsapp`, never `... contact=+79991234567`).

## Data in transit (MASVS-NETWORK)

- [ ] CHK005 All network calls use HTTPS / TLS 1.2+; no `cleartextTrafficPermitted`.
- [ ] CHK006 If certificate pinning is required: documented in plan with rotation strategy.

## Authentication / Authorization (MASVS-AUTH)

- [ ] CHK007 Every privileged action lists required permission + role / entitlement.
- [ ] CHK008 No security-by-obscurity (relying on hidden URL or undocumented intent).

## Platform interaction (MASVS-PLATFORM)

- [ ] CHK009 Exported activities/services/receivers justified; non-exported is the default.
- [ ] CHK010 Intents to other apps use explicit package or `setPackage` when target known; implicit intents to sensitive data avoided.
- [ ] CHK011 Deep links validated: scheme, host, path, parameters all whitelisted; unknown values rejected, not "best-effort parsed".
- [ ] CHK012 Intent extras received from external apps are size-bounded and type-checked (parcelable parsing exception risk).
- [ ] CHK013 No exported `ContentProvider` without permission protection.
- [ ] CHK014 WebView (if any) has JavaScript disabled or origin-restricted; no `addJavascriptInterface` without justification.

## Permissions (Article XIV)

- [ ] CHK015 Each requested permission justified by an explicit FR (per Article XIV §1, §2).
- [ ] CHK016 No permission requested for "future use".
- [ ] CHK017 Fallback for denied permissions designed, not improvised (Article XIV §6).
- [ ] CHK018 Updates to `docs/compliance/permissions-and-resource-budget.md` planned.

## Privacy (Article XIV §3, §4)

- [ ] CHK019 No hidden collection of behavioural / personal data.
- [ ] CHK020 Local-first preferred (Article XIV §4); networked feature explicitly justified.
- [ ] CHK021 If data leaves device: user-visible notice + opt-in (where regulation requires).
- [ ] CHK022 Data minimisation: only fields required for the FR are collected, stored, transmitted.

## Build hardening

- [ ] CHK023 No debug flags / verbose logging enabled in release per `BuildConfig.DEBUG` gate.
- [ ] CHK024 Backup rules (`android:allowBackup`, `data_extraction_rules`) reviewed for new persistent data.

---

## How to apply

1. Walk every data flow (in, store, out).
2. Walk every permission and exported component.
3. Walk every intent / deep-link / external surface.
4. Failures → add explicit security FR; never "we'll handle that in implementation".

## Output

Inline into `specs/<id>/checklists/security.md`.

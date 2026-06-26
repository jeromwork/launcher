# Checklist: security — TASK-51 libsodium consolidation

Applied against [`spec.md`](../spec.md) on 2026-06-26.

Skill: [`.claude/skills/checklist-security/SKILL.md`](../../../.claude/skills/checklist-security/SKILL.md).
Reference: OWASP MASVS, Article XIV of `.specify/memory/constitution.md`, CLAUDE.md rule §6.

**Scope note**: TASK-51 — infrastructure refactor of the crypto stack (lazysodium-android + JNA → ionspin libsodium-kmp), namespace rename `family.*` → `cryptokit.*`, deletion of duplicate ports, force re-pair migration for persisted Keystore aliases. The spec does NOT introduce new network calls, new permissions, new exported components, new deep links, new content providers, new PII fields, or new wire formats. Spec 011 wire format is preserved byte-equal (`schemaVersion: 1`). Many checklist items are therefore `[N/A]` (unchanged from baseline) but are evaluated explicitly so nothing is missed.

---

## Data at rest (MASVS-STORAGE)

- [x] CHK001 No PII (name, phone, email, contact ref, location) stored in clear-text SharedPreferences / unencrypted files unless justified.
  - Spec touches only crypto keypairs (X25519/Ed25519) stored via `cryptokit.crypto.api.SecureKeyStore` (expect/actual Android Keystore adapter). No PII introduced or relocated.
- [x] CHK002 Sensitive data (auth tokens, biometric refs) stored in EncryptedSharedPreferences / Keystore, not bare SharedPreferences.
  - FR-008 + FR-010: private keys go through `SecureKeyStore` (Android Keystore wrap-pattern, generic ByteArray). Old `AndroidKeystoreSecureKeystore` deleted; replacement preserves Keystore-backed protection. No regression vs. baseline.
- [x] CHK003 Cache files containing user data have TTL and clear-on-uninstall policy.
  - No cache files introduced. Existing pairing identities follow spec 011 policy (unchanged).
- [x] CHK004 Logging excludes PII — logs categorize, not enumerate.
  - Spec body does not introduce new log lines. Throws pattern (FR-009) routes to "universal try/catch at the top + one logger" — implementation must ensure no key material / device-id values are logged. Flagged as implementation gate, not a spec gap.

## Data in transit (MASVS-NETWORK)

- [N/A] CHK005 All network calls use HTTPS / TLS 1.2+; no `cleartextTrafficPermitted`.
  - No new network calls. Firestore / FCM channels are pre-existing and unchanged.
- [N/A] CHK006 Certificate pinning rotation strategy.
  - Not applicable; no network surface introduced.

## Authentication / Authorization (MASVS-AUTH)

- [x] CHK007 Every privileged action lists required permission + role / entitlement.
  - No new privileged action introduced. Pairing flow (PairingActivity) is the pre-existing surface; TASK-51 only fixes the JNI crash, doesn't add capabilities. Force re-pair (FR-005) requires user to re-traverse existing pairing UX — no privilege elevation.
- [x] CHK008 No security-by-obscurity.
  - The migration uses ionspin libsodium-kmp (open library) with documented primitives (X25519, Ed25519, XChaCha20-Poly1305). No hidden URLs or undocumented intents.

## Platform interaction (MASVS-PLATFORM)

- [x] CHK009 Exported activities/services/receivers justified; non-exported is the default.
  - Spec does NOT add new exported components. PairingActivity exposure (if any) is inherited from spec 011 and unchanged. `Spec011SmokeDebugActivity` is a debug-only smoke screen — must be guarded by `BuildConfig.DEBUG` (see CHK023).
- [N/A] CHK010 Intents to other apps use explicit package / `setPackage`.
  - No outbound intent surface introduced.
- [N/A] CHK011 Deep links validated.
  - No deep-link surface introduced (admin pairing uses QR, not deep links, in this spec).
- [N/A] CHK012 Intent extras received from external apps are size-bounded and type-checked.
  - No new intent extra surface introduced. (QR-payload parsing belongs to TASK-8 / spec 011, not this refactor.)
- [N/A] CHK013 No exported `ContentProvider` without permission protection.
  - No content provider introduced.
- [N/A] CHK014 WebView (if any) has JavaScript disabled.
  - No WebView introduced.

## Permissions (Article XIV)

- [x] CHK015 Each requested permission justified by an explicit FR.
  - No new permissions requested (refactor only).
- [x] CHK016 No permission requested for "future use".
  - N/A — no new permission requests.
- [x] CHK017 Fallback for denied permissions designed.
  - N/A for this spec; permission model unchanged.
- [x] CHK018 Updates to `docs/compliance/permissions-and-resource-budget.md` planned.
  - No permission delta. Documentation file does not need a TASK-51 entry; baseline preserved.

## Privacy (Article XIV §3, §4)

- [x] CHK019 No hidden collection of behavioural / personal data.
  - Refactor introduces zero new collection. Crypto primitives operate on existing pairing payloads only.
- [x] CHK020 Local-first preferred; networked feature explicitly justified.
  - Refactor stays purely local to device crypto stack. No new networked feature.
- [x] CHK021 If data leaves device: user-visible notice + opt-in.
  - N/A — no new outbound data flow. (Existing spec 011 pairing publishes `DeviceIdentity` to Firestore as before; not changed by TASK-51.)
- [x] CHK022 Data minimisation: only fields required for the FR are collected, stored, transmitted.
  - Wire format unchanged (FR-004). No new fields collected, stored, or transmitted.

## Build hardening

- [x] CHK023 No debug flags / verbose logging enabled in release per `BuildConfig.DEBUG` gate.
  - `Spec011SmokeDebugActivity` is a debug-only smoke screen and MUST remain gated by `BuildConfig.DEBUG` (or equivalent debug-only manifest exclusion). Spec already names it `*DebugActivity` — implementation must keep it out of release manifest. Marked PASS pending implementation verification (covered by fitness-test SC-007 + standard release manifest review).
- [x] CHK024 Backup rules (`android:allowBackup`, `data_extraction_rules`) reviewed for new persistent data.
  - Force re-pair (FR-005) explicitly nukes old Keystore aliases (`spec011.encryption.own`, `spec011.signing.own`) on first launch after upgrade. Android Keystore entries are NOT eligible for `auto_backup` regardless — they are device-bound. No new persistent data introduced outside Keystore. No backup-rules update required.

---

## Verdicts per section

| Section | Verdict | Notes |
|---|---|---|
| Data at rest | PASS | Keys protected via Android Keystore (unchanged guarantee). No PII introduced. |
| Data in transit | N/A | No new network calls. |
| Auth/Authz | PASS | No privilege change. Open libraries; no obscurity. |
| Platform interaction | PASS | No new exported / intent / deep-link / provider / WebView surface. Debug activity must remain debug-only (CHK023 gate). |
| Permissions | PASS / N/A | No permission delta. |
| Privacy | PASS | No new data collection or egress. Wire format byte-equal. |
| Build hardening | PASS | Debug activity is debug-only by name; Android Keystore is non-backup by platform. |

**Overall: PASS** — TASK-51 is a security-neutral refactor. It removes a vendor dependency (lazysodium/JNA, which had a recurring eager-bind crash), consolidates to an open library (ionspin libsodium-kmp) with the same primitive set, and preserves the spec 011 wire format byte-equal. No new attack surface introduced. Force re-pair migration (FR-005) is the only user-visible security-relevant behaviour and is correctly framed as a one-time pre-release event (no production users yet), with an inline-TODO exit ramp to TASK-6 Root Key Hierarchy.

---

## Open items

1. **Implementation-time verification — CHK004 (logging hygiene)**: when implementing the "universal try/catch + one logger" pattern (FR-009), ensure `CryptoException` messages and stack traces do not leak key material, device IDs, or recipient identifiers into logs. Add a brief assertion test (e.g. CryptoException.message MUST NOT contain hex-encoded byte arrays > 8 bytes). Not a blocker for spec sign-off; route into tasks.md as a small follow-up under FR-009.
2. **Implementation-time verification — CHK023 (`Spec011SmokeDebugActivity` debug-only)**: verify that `Spec011SmokeDebugActivity` is declared in `src/debug/AndroidManifest.xml` (or merged with `tools:node="remove"` for release), not in `src/main/AndroidManifest.xml`. Should be covered by existing fitness-test infrastructure (SC-007). If not present, add a Konsist rule.
3. **Documentation note — CHK024**: while no backup-rules update is required, add a one-line note in TASK-51 plan.md (or commit message of the migration commit) that force-re-pair wipes Keystore aliases — so future devs auditing first-launch behaviour understand intent.

None of these block the spec; they are implementation-phase reminders.

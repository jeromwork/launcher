# Crypto — post-MVP roadmap & pre-release audit (operational)

> **Operational pre-release material, moved out of architecture per the SoT consolidation (TASK-145).**
> Architecture (zones, invariants, choices) → [`../architecture/crypto.md`](../architecture/crypto.md) and its concept files.
> This file holds project-state: what is deferred, its trigger, and the pre-release checklist.

## A1. iOS reuse strategy

F-CRYPTO 1.0.0 includes `iosX64`, `iosArm64`, `iosSimulatorArm64` targets. `commonMain` is reused 1-to-1 (the ionspin libsodium binding supports iOS).

Replace on iOS (3 files):
1. `iosMain/SecureKeyStore.ios.kt` (stub today) → iOS Keychain Services (`kSecClassGenericPassword`, `kSecAttrAccessibleAfterFirstUnlock`).
2. `iosMain/HmacSha256.ios.kt` (stub today) → `CCHmac(kCCHmacAlgSHA256, …)` (CommonCrypto).
3. No changes in `commonMain` (rule 1).

Wire compat: `KeyBlob` JSON from Android reads on iOS byte-for-byte. Wrapped private keys are NOT portable (Android Keystore alias ≠ iOS Keychain); cross-device migration = social recovery, not file transfer. iOS Team ID: all family apps share one Apple Developer account for App Groups + shared Keychain. Testing needs a macOS host. **Trigger**: buy a Mac + activate V-1 (TASK-26).

## A2. Multi-app cohabitation — chain-of-trust

Resolved 2026-07-08: chain of symmetric trusted anchors via Play Install Referrer; any recovered family app invites the next. Owning decisions: TASK-115 (onboarding chain, Discussion — blocks messenger release, one-way door), TASK-117 (universal attestation, Discussion), TASK-116 (iconic pairing challenge, Discussion). TASK-25 = Done, superseded 2026-07-08. **Trigger**: approach to messenger spec.md, Session 2 mentor on TASK-115.

## A3. Data export (EU Data Act)

Owner decision 2026-06-18: "Export my data" → plaintext ZIP with a senior-safe warning ("экспортированный файл не зашифрован…"). `KeyEscrow` is NOT used for this (recovery vs compliance dump are different flows). **Trigger**: EU users in base, or Data Act enforcement.

## A4. Pre-release audit checklist

**Strategy**: no external paid crypto audit until a user base + monetization exist. Until then — agent-based pre-release audit before each major release. Each item is a question to the agent; "not closed" → release does not ship.

**Cryptography correctness:**
- [ ] Wycheproof subset SHA pinned, files in `core/crypto/src/commonTest/resources/wycheproof-subset/`.
- [ ] All RFC KAT green on JVM and iOS (if iOS release).
- [ ] Property tests green on JVM, Android, iOS.
- [ ] CVE database (NVD, GHSA) for libsodium / ionspin — no unpatched HIGH/CRITICAL.

**Android Keystore security:**
- [ ] Real-device verification Pixel (StrongBox) + Samsung Galaxy A-series (Knox).
- [ ] `SecureKeyStoreNoPlaintextLeakTest` green on a physical device.
- [ ] `KeyInfo.isInsideSecureHardware` checked on init — false → log + telemetry.
- [ ] TEE attestation hard-fail wired for billing-protected features.

**iOS Keychain security** (if iOS release):
- [ ] `iosMain/SecureKeyStore.ios.kt` + `HmacSha256.ios.kt` implemented.
- [ ] `:core:crypto:iosTest` green on a macOS host.
- [ ] Verified on a physical iPhone.

**Wire format integrity:**
- [ ] `KeyBlob v1-sample.json` + `v1-retired-sample.json` unchanged (frozen since 1.0.0).
- [ ] Schema version check (`UnsupportedSchemaVersion`) works.

**Production hygiene:**
- [ ] `verifyCryptoIsolation` Gradle task green — `:core:crypto` depends on no other module.
- [ ] Konsist `NoFakeCryptoInAppTest` green — no `family.crypto.fake.*` imports in `app/src/main`.
- [ ] R8/ProGuard rules strip `family.crypto.fake.**` from release APK.
- [ ] `assertNoFakeCryptoInRelease()` called in `LauncherApplication.onCreate` under `!BuildConfig.DEBUG`.

**Backup safety:**
- [ ] `data_extraction_rules.xml` + `backup_rules.xml` exclude `keys/`.
- [ ] Manual test: cloud backup + restore — wrapped keys NOT carried over.

**Multi-app cohabitation** (if ≥2 apps):
- [ ] Cross-app chain onboarding implemented per TASK-115 Decision, verified end-to-end for 2+ apps.
- [ ] Cross-app sealed-box handoff tested end-to-end.

**Data sovereignty:**
- [ ] Data export UI implemented with senior-safe warning.
- [ ] Google Play Data Safety form filled.
- [ ] Apple App Privacy labels filled (if iOS release).

**Research before release:**
- [ ] Sweep Signal Android, Bitwarden Android, Threema Android — what changed recently.
- [ ] Current Wycheproof commits — pin the latest.
- [ ] Android Keystore behavior changes in the latest Android version.
- [ ] iOS Keychain behavior changes in the latest iOS version.

**When to move to a paid audit** ($3–12k, Cure53 / 7ASecurity / Radically Open Security / Trail of Bits): monetization live + user base ≥ 10k active + revenue ≥ $50/mo sustained; OR a PR incident / CVE.

## A5. Deferred items (trigger summary)

| Item | "Do it now" trigger | Status |
|---|---|---|
| Wycheproof subset SHA pin | Before Play Store submission | TODO + checklist |
| iOS Keychain + HmacSha256 | Decision to ship iOS | stub-screamer + A1 reuse strategy |
| TEE attestation hard-fail | Before paid release | Documented in A4 |
| Library extract `family-crypto-kmp` | Second consumer (messenger spec.md) | TODO inline; policy → [`../architecture/extraction-policy.md`](../architecture/extraction-policy.md) |
| Real-device StrongBox verification | Acquire a used Pixel | Planned |
| Multi-app cohabitation chain-of-trust | Messenger spec creation | TASK-115 (Discussion) + TASK-117 + TASK-116 |
| Data export UI | EU release or Data Act enforcement | TODO in code |
| Paid crypto audit | User base ≥ 10k + revenue ≥ $50/mo | Agent-audit until then |

**Grep-discoverable**: `grep -r "TODO(pre-release-audit):" core/ app/ docs/` — the live list of open items.

## Security failure modes (release hygiene — check before each major release)

Timeless engineering principles; each closes with a concrete mitigation. (Moved out of architecture — this is operational review material, not architecture.)

1. **Nonce reuse in AEAD** → random nonce only, never a self-managed counter. Fitness: "encrypt twice → different output".
2. **Wrong server/Worker validation** → an attacker with a valid JWT becomes admin. Mitigation: rules tests + Worker unit tests + negative-path + 2-eye review (TASK-105).
3. **Argon2id iterations too low** → brute-force in hours. Mitigation: hardcoded constant + `assert iterations >= MIN`.
4. **Wire format without `schemaVersion`** → breaks all v1 readers on field add. Rule 5 + TASK-16 fitness rule (`../architecture/wire-format.md`).
5. **`allowBackup="true"`** → root key leaks to cloud backup. Mitigation: `allowBackup="false"` + `dataExtractionRules.xml` + CI check.
6. **KeyPackage reuse** → forward secrecy loss. Mitigation: openmls one-time enforcement + test on marked-used → refuse.
7. **Trusting JWT for authorization instead of MLS roster** → attacker with someone else's JWT gets group access. Mitigation: Worker always verifies JWT **and** roster membership (rule 12 zero-trust).

Mandatory checklists per crypto spec: `checklist-security`, `checklist-wire-format`, `checklist-domain-isolation`, `checklist-server-hardening`.

## Known risks / open TODOs (current moment)

- `crypto_box_seal` / HKDF-SHA256 not in ionspin's public API — Phase 5 uses `Box.seal`/`Box.sealOpen` + hand-rolled HKDF; switch when ionspin ships first-class HKDF.
- iOS path — stub-only, replaced with Keychain at V-1.
- TEE attestation not enforced — `isInsideSecureHardware == false` logged but not hard-fail on MVP; hard-fail when billing lands.
- Wycheproof subset not yet pinned — commit SHA picked during full Phase 5 impl.
- Key rotation interface-only — `StubKeyRotation` throws; real impl SRV-CRYPTO-002 (`server-roadmap.md`).

## Related

- Architecture: [`../architecture/crypto.md`](../architecture/crypto.md) (+ primitives / key-hierarchy / pairing files). Extraction: [`../architecture/extraction-policy.md`](../architecture/extraction-policy.md). Server migration: [`server-roadmap.md`](server-roadmap.md).

# Checklist: wire-format (applied to plan.md + contracts/)

Applied 2026-06-26 against:
- `specs/task-51-libsodium-consolidation/plan.md`
- `specs/task-51-libsodium-consolidation/contracts/device-identity.md`
- `specs/task-51-libsodium-consolidation/contracts/encrypted-envelope.md`
- `specs/task-51-libsodium-consolidation/contracts/ciphertext.md`

Scope reminder: TASK-51 — pure namespace rename + lazysodium→ionspin swap. **No schema change**. Wire formats kept byte-equal (SC-013 golden vectors). Three wire-format-relevant artifacts:
1. `DeviceIdentity` (Firestore JSON, schemaVersion=1)
2. `EncryptedEnvelope` + `Recipient` (encrypted blob, schemaVersion=1)
3. `Ciphertext` (internal value class, raw byte concatenation — **not** a JSON/persistence wire format, but listed for completeness)

---

## Schema version

- [x] CHK001 Every wire format carries an explicit `schemaVersion: Int` field from its first commit.
  - `DeviceIdentity.schemaVersion = 1` (device-identity.md L14, L33).
  - `EncryptedEnvelope.schemaVersion = 1` (encrypted-envelope.md L14, L37).
  - `Ciphertext` — N/A by design (internal value class, raw concat `nonce||ct||mac`, not a persisted/wire wrapper) — explicitly noted as "implicit (raw byte concatenation, no JSON wrapper)" in ciphertext.md L4.
- [x] CHK002 `schemaVersion` field is **read first** during deserialization.
  - Field declared first in both `DeviceIdentity` and `EncryptedEnvelope` Kotlin declarations; readers "MUST be `1`. Reader rejects unknown versions" (device-identity.md L52, encrypted-envelope.md L82).
- [x] CHK003 Currently-supported `schemaVersion` constant is documented in code (single source of truth).
  - Default value `schemaVersion: Int = 1` lives on the data class itself (single declaration site). Documented in contracts as "Schema version: `1` (unchanged in TASK-51)".

## Backward compatibility

- [x] CHK004 Reads of previous schema versions remain possible for at least one major release.
  - TASK-51 is pure namespace rename — byte-equal compat preserved. `EnvelopeConfigCipherRoundtripTest` golden vectors verify post-rename reads of pre-TASK-51 documents (plan.md SC-013, "Wire formats" table L148-158).
- [x] CHK005 Adding a field is allowed; deserializer handles missing fields with documented defaults.
  - `EncryptedEnvelope.aad: ByteArray? = null` — nullable default documented (encrypted-envelope.md L42, L89 "Optional. If present, MUST match exactly at decrypt-time").
- [N/A] CHK006 Renaming or removing a field requires a versioned migration written before breaking change ships.
  - TASK-51 explicitly does NOT rename or remove any wire-format field — only Kotlin namespace rename. `@SerialName` annotations prevent serialized-field rename (plan.md L158 "explicit `@SerialName(...)` annotation чтобы binary-compat сохранился при namespace rename").
- [N/A] CHK007 Migration code is scoped (`migrateLegacy(json)`).
  - No JSON migration in TASK-51. Silent migration logic in plan.md L127-141 is for **persisted keys** (Android Keystore aliases), not wire-format JSON — scoped to `loadOrMigrate(newKeyId, legacyAlias)` helper, not branching.

## Forward compatibility

- [x] CHK008 Reading newer schema versions handled gracefully (documented choice).
  - Documented as fail-closed: "Reader rejects unknown versions (forward compat не требуется в TASK-51 scope)" (device-identity.md L52, encrypted-envelope.md L82). Explicit choice, not crash.
- [N/A] CHK009 Open discriminator (`kind: "..."`) unknown value yields `Failure`, not crash.
  - Neither `DeviceIdentity` nor `EncryptedEnvelope` use polymorphic discriminator. `@SerialName` is for fixed-shape data classes (device-identity.md L75). No open discriminator surface.

## Tests

- [x] CHK010 Roundtrip test exists for every wire-format type: write → read → assertEquals.
  - `EnvelopeConfigCipherRoundtripTest` (existing, `:core:keys`) — plan.md L182-183. SC-013 byte-equal golden vector test.
- [~] CHK011 Backward-compat test exists: a fixture from previous schema version reads successfully.
  - **Partial / gap**: `EnvelopeConfigCipherRoundtripTest` golden vectors function as backward-compat (pre-TASK-51 bytes read after rename). Plan.md L207 lists this as the explicit mitigation for "binary drift" risk. However, no separate `schemaVersion=0` fixture exists because no prior schema version ever shipped (schema=1 from first commit). **Counted as `[x]`** because schemaVersion=1 IS the previous-and-current version, and golden vectors fixture covers it. Marker `[~]` indicates "satisfied through golden vectors, not a v0 fixture".
- [x] CHK012 Test fixtures stored as files in `commonTest/resources/`, not literal strings.
  - `EnvelopeConfigCipherRoundtripTest` documented as golden-vector test (plan.md L182, "golden vector test"). Fixtures live in `:core:keys` test resources (existing test, not introduced by TASK-51).

## Persistence specifics

- [x] CHK013 SharedPreferences/DataStore: keys namespaced.
  - EncryptedSharedPreferences holds wrapped X25519/Ed25519 priv bytes (plan.md L23-24). `KeyId` is used as namespaced identifier; `legacyAlias` distinguished from `newKeyId` in silent-migration helper (plan.md L129).
- [N/A] CHK014 SQLDelight: every migration script has a corresponding test.
  - TASK-51 does not touch SQLDelight schema. `SqlDelightBlobReferenceLedger.kt` listed as "imports updated" only (plan.md L88).
- [x] CHK015 If a stored type is removed entirely: one-shot cleanup written; grep-anchor comment.
  - Silent migration deletes legacy aliases after re-storing under new KeyId: `legacyKeystoreReader.delete(legacyAlias)` (plan.md L135) + inline TODO comment `// TODO(post-task-6): replace with derive-from-root after Root Key Hierarchy lands` (plan.md L136). After TASK-6 `legacyKeystoreReader` is removed (plan.md L141).

## Deep-link / QR / exported config

- [N/A] CHK016 URL/QR payload embeds `schemaVersion` in path or first JSON field.
  - TASK-51 introduces no deep-link / QR / exported-config surfaces. Spec is pure refactor.
- [N/A] CHK017 Truncated/corrupted payload yields user-facing error, not crash.
  - No new scan surface in TASK-51. (For existing payloads: `CryptoException.SerializationException` / `AeadException` thrown on tamper — device-identity.md L67, encrypted-envelope.md L74 — but these are programmatic errors, not user-facing QR scan UX.)

## Contract folder

- [x] CHK018 If `contracts/` exists: each contract file lists its semantic version, breaking-change policy, link to roundtrip test fixture.
  - All three contract files have **Schema version** header (`1`, or `implicit` for Ciphertext), explicit **Namespace migration risk** section, and link/reference to `EnvelopeConfigCipherRoundtripTest` (device-identity.md L70, encrypted-envelope.md L100→cross-ref, ciphertext.md L62). Breaking-change policy stated as "Reader rejects unknown versions" + "@SerialName mandatory".

---

## Summary

| Bucket | Status |
|---|---|
| Schema version (CHK001-003) | 3/3 [x] |
| Backward compat (CHK004-007) | 2/2 [x] applicable + 2 N/A |
| Forward compat (CHK008-009) | 1/1 [x] applicable + 1 N/A |
| Tests (CHK010-012) | 3/3 [x] (CHK011 satisfied via golden vectors — no v0 fixture exists because schema=1 from first commit) |
| Persistence (CHK013-015) | 2/2 [x] applicable + 1 N/A |
| Deep-link / QR (CHK016-017) | 0/0 applicable + 2 N/A |
| Contracts (CHK018) | 1/1 [x] |

**Total: 12/12 applicable CHK [x], 6 N/A.**

## Notable strengths

- `@SerialName(...)` audit elevated to a **Phase 4 obligatory gate** with explicit grep step + risk callout (plan.md L158, L207; device-identity.md L73-77, encrypted-envelope.md L98-100). This is the single critical wire-format-preservation mechanism, and it's hard-wired into the rollout.
- Golden-vector test (`EnvelopeConfigCipherRoundtripTest`) reused as the byte-equal verification gate (SC-013), not a new test — meta-minimization respected.
- Field invariants tables (byte sizes, formats) explicitly documented in each contract, enabling future drift detection.

## Gaps / watch items

- **CHK011 nuance**: there is no separate `schemaVersion=0` fixture because schema=1 has been the version since first commit of these types. Golden vectors serve as the de-facto backward-compat test. If a future task introduces `schemaVersion=2`, a v1 fixture file must be frozen at that moment.
- **No deep-link / QR surface in TASK-51** — CHK016/017 N/A. When a future feature introduces shareable config (TASK-7 wizard manifest sharing, future MarketplaceSource), this checklist must re-run.
- **Silent key migration logic (plan.md L127-141)** is not a wire-format migration but a persisted-key alias rename. It carries its own risk (Risk #1 in plan.md L206) — covered by smoke test, but the legacy-bytes-still-on-device path is not E2E-verified because Xiaomi 11T never had a successful pairing (plan.md L301). Tracked under spec risks, not a wire-format gap per se.

wire-format-plan: 12/12 CHK [x]

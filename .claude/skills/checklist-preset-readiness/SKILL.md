---
name: checklist-preset-readiness
description: Verifies that any user-facing configuration introduced by the spec (layouts, tile sets, wizard manifests, themes, action mappings, tutorial sequences) is designed as a shareable portable artifact from day one, per CLAUDE.md rule 9. Triggered by mentions of preset, template, layout, tile arrangement, wizard manifest, theme, tutorial sequence, bundled config, ConfigSource, default profile.
---

# Checklist: preset-readiness

Verifies the spec respects **CLAUDE.md rule 9 — Shareability-readiness for non-identity configurations**.

Any user-facing configuration that does NOT depend on identity / secrets / PII / device-specific state must be designed as a portable shareable artifact from the first commit, even when no sharing UI is being built now. This makes future sharing an additive change, not a rewrite.

Reference: `CLAUDE.md` §9, refuse pattern #12.

---

## What this checklist covers

Applies to: layout templates, tile arrangements, custom action mappings, theme variants, tutorial sequences, wizard manifests, preset configurations — anything one user might reasonably want to share with another or import from a community/marketplace in the future.

Does NOT apply to: pair keys, Firebase tokens, contact PII, photo blobs, application code, system permissions.

---

## Wire format

- [ ] CHK001 The config has a wire format (JSON / Protobuf / equivalent) — not a hardcoded Kotlin `object` or `enum`.
- [ ] CHK002 The wire format carries an explicit `schemaVersion` field from this commit (CLAUDE.md rule 5).
- [ ] CHK003 A roundtrip test exists or is in the spec's task list: write → read → assert equal.
- [ ] CHK004 A backward-compat read test exists or is planned: read previous `schemaVersion` and migrate or reject cleanly.

## Anonymization

- [ ] CHK005 No UID, Firebase token, account-id, or other identity-bound value appears in the config.
- [ ] CHK006 No package-specific identifiers tied to the current device (e.g., installed package names that vary per region) are baked in without a fallback / resolver.
- [ ] CHK007 No contact phone numbers, emails, or names appear in the shareable form.
- [ ] CHK008 No blob references (photo URLs, audio URIs) point to identity-bound private storage.
- [ ] CHK009 If the config references external apps by package, there is a documented fallback when the package is absent.

## Adapter pattern

- [ ] CHK010 The config is loaded through a `ConfigSource` (or equivalent named) port — the spec declares the interface.
- [ ] CHK011 `BundledSource` is **one of** several future implementations, not the only path baked into call sites.
- [ ] CHK012 Inline TODO at the `BundledSource` site documents future adapters: `// TODO(shareability): future ConfigSource adapters — file import, share intent, marketplace`.
- [ ] CHK013 No caller imports `BundledSource` directly — callers depend on the port.

## Cross-device contract

- [ ] CHK014 A device running app v(N) accepts a config produced by v(N-1) — versioned migration is described.
- [ ] CHK015 A device running v(N-1) rejects v(N) cleanly with a user-friendly message — not a crash.
- [ ] CHK016 Locale-independent: the config does not embed user-language strings as canonical values; uses keys.

## Privacy by design

- [ ] CHK017 The data model **structurally** prevents identity from being embedded — e.g., contact references use opaque local handles, not phone numbers.
- [ ] CHK018 No telemetry / device fingerprint is recorded inside the config.

## Acceptance evidence

- [ ] CHK019 Spec includes the roundtrip and cross-version tests as explicit acceptance criteria.
- [ ] CHK020 Spec's task list contains the `ConfigSource` port and at least one fake `ConfigSource` adapter for tests.

---

## How to apply

1. List every user-facing configuration this spec introduces or modifies.
2. For each: check it against CHK001-CHK020.
3. If the answer is "this config is identity-bound" (e.g., pair keys), document why and exempt it — rule 9 explicitly excludes identity-bound state.
4. Failures → either redesign the config or document why it must be identity-bound.

## Output

Inline into `specs/<id>/checklists/preset-readiness.md`.

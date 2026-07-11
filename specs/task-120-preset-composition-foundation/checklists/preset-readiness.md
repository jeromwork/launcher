# Checklist: preset-readiness

Applied against `spec.md` per CLAUDE.md rule 9 — Shareability-readiness for non-identity configurations.

Scope of user-facing configurations introduced by this spec:
- **Preset** (`preset.json`, schemaVersion=2) — three fields `wizardFlow` / `settingsMap` / `activeComponents` (FR-003), loaded via `PresetSource` port (Key Entities), MVP impl `BundledSource`.
- **Pool** (`pool.json`, schemaVersion) — catalog of `ComponentDeclaration`, loaded via `PoolSource` port (FR-002), MVP impl `BundledPoolSource`.
- **Profile** (`profile.json`, schemaVersion) — device-local snapshot, persisted through `ProfileStore` port. NOT a shareable artifact (device-local), but wire-format rules still apply.

Preset and Pool ARE shareable; Profile is device-local runtime state (not primary target of this checklist but wire-format hygiene still applies).

---

## Wire format

- [x] CHK001 Preset / Pool / Profile are JSON wire formats via kotlinx.serialization polymorphic sealed (Assumptions, FR-016) — not hardcoded Kotlin objects.
- [x] CHK002 `schemaVersion: Int` field present on `pool.json`, `preset.json`, `profile.json` from this commit (FR-003 preset schemaVersion=2; FR-016 explicit; SC-008).
- [x] CHK003 Roundtrip test present as acceptance criterion (SC-005: `pool.json + preset.json → Profile → serialize → deserialize → Profile'` bit-identical; SC-011 property-based test includes roundtrip clause `preset → profile → serialize → deserialize → equal`; also fitness function #4 in SC-004).
- [x] CHK004 Backward-compat read test planned (SC-004 fitness #5 `backward-compat pool v1→v2`; SC-008 mentions migration writer v2→v3 required before breaking change per rule 5; FR-012 rejects newer schemaVersion cleanly with "Update app" message — fail-loud).

## Anonymization

- [x] CHK005 No UID / Firebase token / account-id embedded in Preset or Pool. AI Affordance section explicitly states: "No PII leaves device — Profile содержит только Component-параметры (шрифт 1.8, target SOS = pairing-777). Identity-bound данные (pairing keys, contact PII) — в отдельном encrypted блоке, не в Profile." SOS target uses opaque `pairing-777`-style handle rather than phone number.
- [~] CHK006 Package-specific identifiers: Preset can carry `AppTile` with `packageName` (e.g. WhatsApp/Госуслуги/VK per FR-014). Fallback documented for absent package — `check() → NeedsApply → apply() → requestInstall` intent OR `Failed("app unavailable")`. However region-specific package variance (e.g. WhatsApp regional builds, package rename) not explicitly addressed as a shareability concern. **Minor gap**: no explicit region/locale-independent resolver strategy.
- [x] CHK007 No contact phone numbers, emails, names in shareable form. SOS Component uses `pairing-777`-style opaque handle (AI Affordance), not raw phone. `settingsMap` entries reference Component ids, not personal data.
- [x] CHK008 No blob references (photo URLs, audio URIs) to identity-bound private storage. Preset fields are Component params + IDs; no photo/audio references at foundation level. Downstream tasks (TASK-19 tiles with images) inherit this discipline.
- [~] CHK009 AppTile with unavailable package: fallback documented (FR-014, edge case). Play Store intent as install path; `Failed("app unavailable")` if Play Store unavailable. **Sufficient for MVP** but note: no fallback specified when Play Store link itself points to region-restricted package (e.g. shareable preset built in RU pointing to VK, imported on device in US where VK not listed).

## Adapter pattern

- [x] CHK010 `PresetSource` port declared in Key Entities: "Preset — shareable JSON template ... доступный через `PresetSource` port (реализации: bundled, file, share, network, QR — additive)". `PoolSource` port declared in FR-002 with additive implementations enumerated.
- [x] CHK011 `BundledSource` explicitly framed as ONE OF future implementations (FR-002: "Другие источники (file, share intent, network, QR) добавляются additive"; Key Entities Preset: "реализации: bundled, file, share, network, QR — additive"). FR-023 doubles down: PoolSource additive-only.
- [ ] CHK012 **FAIL** — Spec does NOT mandate an inline `// TODO(shareability): future ConfigSource adapters — file import, share intent, marketplace` comment at the `BundledSource` / `BundledPoolSource` site. Not referenced in FR-002, FR-023, or any Assumptions clause. Recommend: add explicit acceptance criterion or note in Assumptions requiring the TODO marker at both `BundledPoolSource` and `BundledPresetSource` implementation sites, so downstream tasks (draft-1, TASK-68, TASK-19) inherit the marker discipline.
- [x] CHK013 Callers depend on port not concrete impl. Plan-level sequence SEQ-1 shows `VM → PS: loadPreset(...)` where `PS` is `PresetSource` (port), not a concrete BundledSource. `ProfileFactory.create(preset, pool)` receives already-loaded data via port. No direct import of BundledSource in caller sites implied by architecture.

## Cross-device contract

- [x] CHK014 v(N) accepts v(N-1) preset — SC-008 explicit: "Wire format `preset.json` v2 читается кодом v2 (roundtrip); при появлении preset schemaVersion=3 в будущем — migration writer v2→v3 добавлен до слива breaking change (rule 5)". Fitness #5 in SC-004 enforces backward-compat pool v1→v2.
- [x] CHK015 v(N-1) rejects v(N) cleanly — FR-012: "System MUST reject preset с `schemaVersion` выше чем поддерживает приложение (fail loud)"; edge case: "schemaVersion preset выше чем поддерживаемая приложением: отклонить preset с сообщением «Update app to load this preset»." User-friendly message, not a crash.
- [~] CHK016 Locale-independent: Component IDs are opaque strings (`"font-tile"`, `"lockdown-morning"`), which is locale-independent. However, `settingsMap` mentions "категории" like «Зрение», «Слух», «Безопасность», «Приложения» (SEQ-2 MENTOR-DETAIL). **Grey zone**: not explicit whether category is a translation-key ID or a raw Russian string. Recommend spec add clause: settingsMap category identifiers are opaque i18n keys, resolved to user-language at render time (already implicit but not stated).

## Privacy by design

- [x] CHK017 Structurally prevents identity embedding. AI Affordance: Profile holds Component parameters only; identity-bound data (pairing keys, contact PII) lives in separate encrypted block, not in Profile. SOS target = opaque `pairing-777` handle. Structure of Preset/Profile does not allocate fields for phone/email/name.
- [x] CHK018 No telemetry / device fingerprint recorded in config. Vendor detection (`Build.MANUFACTURER` → `Vendor` enum) is runtime lookup on device, not baked into preset. Preset carries no device identifier. AI Affordance section: "no telemetry" explicit for MVP.

## Acceptance evidence

- [x] CHK019 Roundtrip + cross-version tests as explicit AC. SC-005 (roundtrip bit-identical), SC-008 (v2→v3 migration writer), SC-011 (property-based roundtrip N=100), SC-004 fitness #4 (roundtrip) and #5 (backward-compat pool v1→v2) — four independent AC clauses covering the contract.
- [x] CHK020 Task list contains `PresetSource` + `PoolSource` port and fake adapter. Local Test Path enumerates `FakePoolSource`, `FakePresetSource`, `FakeProfileStore` explicitly. Port declared in FR-002 and Key Entities.

---

## Summary

- **Total**: 20 CHK
- **Pass**: 17 ([x])
- **Partial / minor gap**: 2 (CHK006, CHK009 — package-region-restricted apps in shareable preset; CHK016 — locale-key convention implicit not explicit)
- **Fail**: 1 (CHK012 — inline `TODO(shareability)` marker not mandated in spec)

## Failures / Recommendations

**CHK012 (FAIL) — mandate inline TODO(shareability) at BundledSource sites.**
Rule 9 explicitly requires: "Inline-TODO у `BundledSource` сайта: `// TODO(shareability): future ConfigSource adapters — file import, share intent, marketplace`". Add an acceptance criterion (e.g. SC-014) or Assumption clause: `BundledPoolSource.kt` and `BundledPresetSource.kt` MUST carry inline `// TODO(shareability): future ConfigSource adapters — file import, share intent, network, marketplace, QR` at class site, verifiable by a grep-based fitness function or lint check. Downstream tasks (draft-1, TASK-68, TASK-19, task-121) inherit the marker.

**CHK006 / CHK009 (PARTIAL) — region-restricted package resolution in shareable preset.**
`AppTile` with `packageName` is a plausible shareable-preset field. When a family/clinic in region A shares a preset referencing region-A-specific package (VK, Госуслуги, WeChat) to a device in region B, current spec resolves to `Failed("app unavailable")` — acceptable at MVP but blocks community-marketplace future. Recommend adding to Assumptions: "AppTile package resolution failures in shared/imported presets are user-visible errors on target device; region-alternative resolver is future work (deferred to shareability-marketplace spec)". This documents the exit ramp per rule 8.

**CHK016 (PARTIAL) — settingsMap category identifier convention.**
Spec references Russian category names («Зрение», «Слух») in SEQ-2 MENTOR-DETAIL without stating whether these are i18n keys or raw strings. Add clause to FR-003 or Assumptions: "`settingsMap` category is an opaque i18n key (e.g. `category.vision`), resolved at render time; canonical preset carries keys, not localized display strings" — enforces CHK016 structurally and prevents locale-baked presets slipping through.

---

## Notes

- Preset ARE the central shareability concept of this feature; rule 9 compliance is not incidental — it is load-bearing for all downstream tasks (TASK-68 workspace preset, TASK-19 adaptive UX, future marketplace).
- Property-based test (SC-011) generates synthetic presets but does NOT undermine shareability testability: the 3 fixed seed presets (`simple-launcher`, `launcher`, `workspace` per SC-002) remain as canonical roundtrip fixtures. Property-based test extends coverage rather than replacing fixed content — both coexist as acceptance evidence.
- Profile is intentionally NOT shareable (device-local Provider outcomes, statuses); wire-format hygiene (schemaVersion, roundtrip) still applies per rule 5 but rule 9 does not.
- The one FAIL (CHK012) is low-cost to fix — a single acceptance criterion mandating inline TODO markers. Recommend addressing before speckit-plan.

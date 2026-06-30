# Checklist: preset-readiness — TASK-65 (re-run after revised model)

Applied: 2026-06-30 (2nd pass).

## Wire format

- [x] CHK001 Wire format JSON — **yes**.
- [x] CHK002 schemaVersion present — **yes**.
- [x] CHK003 Roundtrip test — **partial** (defer to plan, see wire-format CHK010).
- [x] CHK004 Backward-compat read — **partial** (defer to plan).

## Anonymization

- [x] CHK005 No UID/Firebase/account-id в preset — **yes**. Preset structure: `{uid (preset uid, not user uid), version, slug, label, description, configs[], abstractProfile?, requiredModules, optionalModules, pickEnabled}` — все обезличенные.
- [x] CHK006 No package-specific device identifiers — **yes**. abstractProfile.bindings содержат **placeholder packages** (e.g. `com.google.android.youtube`) — package identifiers общеизвестные. Personal data (контакты Иванова) — только в Profile, не Preset (architectural separation enforced by spec).
- [x] CHK007 No contact PII — **yes**. Bindings в Preset = placeholder (без личных). Bindings в Profile (per-device) ≠ Preset.
- [x] CHK008 No blob references — **yes**.
- [x] CHK009 External app fallback — **partial**. **NEW concern from Clarification #12**: placeholder package not installed на target device. Hook в Binding model. UI fallback — TASK-68/71. **Spec упоминает**, не deferred молча.

## Adapter pattern

- [x] CHK010 ConfigSource port — **yes**.
- [x] CHK011 BundledSource one of several — **yes**.
- [x] CHK012 Inline TODO at BundledSource — **yes**.
- [x] CHK013 No callers import BundledSource directly — **yes**.

## Cross-device contract

- [x] CHK014 v(N) accepts v(N-1) — **yes**. Migration writer.
- [x] CHK015 v(N-1) rejects v(N) cleanly — **yes**. IncompatibleVersion result.
- [x] CHK016 Locale-independent — **yes**. I18nKey not raw strings.

## Privacy by design

- [x] CHK017 Data model structurally prevents identity — **yes, even stronger now**.
  - **Preset structure**: configs/abstractProfile/metadata — все обезличенные.
  - **Profile** identity-bearing — отдельная сущность, never shipped в preset, лежит в ProfileStore on-device.
  - **PresetRef.uid** — это **uid preset'а**, не user uid. Глобально уникальный, но не tied to person. Подтверждено Clarification #13.
- [x] CHK018 No telemetry в config — **yes**.

## Acceptance evidence

- [x] CHK019 Roundtrip + cross-version в AC — **partial** (defer to plan).
- [x] CHK020 ConfigSource + fake adapter — **yes**.

---

**Total**: 20/20 ✓ (3 partial — defer to plan: roundtrip with PresetRef, backward-compat fixture)
**Red-only summary**: preset-readiness: 20/20 ✓ (3 partial — roundtrip test с PresetRef, backward-compat fixture pre-TASK-65, external app fallback UI — defer to plan/follow-up).

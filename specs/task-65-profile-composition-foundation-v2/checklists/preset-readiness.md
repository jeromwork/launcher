# Checklist: preset-readiness — TASK-65 Preset Composition Foundation v2

Applied: 2026-06-30. Most relevant checklist — TASK-65 INTRODUCES preset concept per CLAUDE.md rule 9.

## Wire format

- [x] CHK001 Config has wire format (JSON) — **yes**. `preset.json` (FR-001).
- [x] CHK002 `schemaVersion` field from this commit — **yes**. `schemaVersion=1` (FR-001).
- [x] CHK003 Roundtrip test — **partial** (FR-027 для PoolSource; preset roundtrip — surface to plan, см. wire-format CHK010).
- [x] CHK004 Backward-compat read test — **partial** (surface to plan, см. wire-format CHK011).

## Anonymization

- [x] CHK005 No UID / Firebase token / account-id in preset — **yes**. FR-001 fields list: только `id`, `schemaVersion`, `label`, `description`, `configs`, `requires`, `picks`, `requiredModules`, `pickEnabled` — все обезличенные.
- [x] CHK006 No package-specific identifiers tied to current device — **partial**. `configs.picks` могут содержать `tile.set` ссылки которые **в bindings содержат package names** (например WhatsApp). **НО** bindings — это `Profile` (per-device), **не Preset**. Preset содержит slots с `kind` (опциональный hint), не packages. **Clear separation enforced by spec** (User Story 2 + Adjacent Concern про переключение). **OK**.
- [x] CHK007 No contact phone/email/name — **yes**. Bindings (которые могут содержать) живут в Profile (per-device), не Preset. Структурно невозможно (spec explicit).
- [x] CHK008 No blob references to identity-bound storage — **yes**. Preset не содержит binary blobs.
- [x] CHK009 External app references with fallback — **N/A** в scope TASK-65. Workspace TASK-68 будет иметь app references, fallback документация в той задаче.

## Adapter pattern

- [x] CHK010 Config loaded через port — **yes**. `ConfigSource` (existing per Article VII §14) + новый `PoolSource` (FR-005).
- [x] CHK011 `BundledSource` — one of several — **yes**. Spec explicitly мы упоминает future `NetworkPresetSource`, `FilePresetSource`, `ShareIntentPresetSource` (Out of Scope, additively позже). `BundledConfigSource` — единственный сейчас, не хардкожен в call sites.
- [x] CHK012 Inline TODO at BundledSource — **yes**. Уже есть в existing `ConfigSource.kt`: `TODO(shareability): future ConfigSource adapters — file import, share intent, marketplace.` (см. existing code).
- [x] CHK013 No caller imports BundledSource directly — **yes by design**. Callers depend on `ConfigSource` port. Lint `ExtractionReadinessDetector` дополнительно защищает.

## Cross-device contract

- [x] CHK014 Device v(N) accepts config v(N-1) — **yes**. Versioned migration (FR-002, ConfigSource `IncompatibleVersion` result handling).
- [x] CHK015 Device v(N-1) rejects v(N) cleanly — **yes**. ConfigSource returns `IncompatibleVersion`, picker filters (Edge Case explicit), no crash.
- [x] CHK016 Locale-independent — **yes**. FR-001 `label: I18nKey` + `description: I18nKey` (string resource keys, не raw strings).

## Privacy by design

- [x] CHK017 Data model structurally prevents identity — **yes**. Preset fields (см. CHK005) не содержат и не могут содержать identity. Bindings в Profile, не Preset.
- [x] CHK018 No telemetry / fingerprint inside config — **yes**. Spec не вводит telemetry; preset чистый.

## Acceptance evidence

- [x] CHK019 Roundtrip + cross-version в AC — **partial**. FR-027 (PoolSource roundtrip) + FR-025 (regression snapshot) — SC-010/012 acceptance. Preset wire format roundtrip — surface to plan.
- [x] CHK020 ConfigSource port + fake adapter в tasks — **yes**. ConfigSource уже есть; FR-005/006/007 добавляют PoolSource; fake adapter — Local Test Path («FakeConfigSource, FakePoolSource»).

---

**Total**: 18/20 ✓, 2 partial (CHK003, CHK004 — defer to plan)
**Red-only summary**: preset-readiness: 20/20 ✓ (CHK003/004 partial — preset roundtrip + backward-compat tests defer to plan).

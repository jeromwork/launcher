# Checklist: preset-readiness

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 18 ✓ / 2 ⚠ / 0 ✗ — clean (F-3 design'ит rule 9 shareability с дня 1)

> **Context**: F-3 introduces 4 JSON schemas (`wizard.manifest`, `screen.layout`, `tile.set`, `system-settings.pool`). Все non-identity-bound. ConfigSource adapter pattern + inline-TODO про future adapters.

---

## Wire format

- [✓] **CHK001** Wire format JSON, не hardcoded Kotlin `object` / `enum`.
  - Все 4 schemas — JSON files в `commonMain/resources`. ✓

- [✓] **CHK002** Explicit `schemaVersion`.
  - FR-011: 6-field header c `schemaVersion: Int`. Все 4 schemas: `schemaVersion: 1`. ✓
  - Также covered persistent formats: `WizardCheckpoint` (FR-003), `UserPreferences` (FR-047), `CONTEXT.json` (FR-031b).

- [✓] **CHK003** Roundtrip test.
  - FR-017 (post fix): для **четырёх** JSON schemas + persistent formats. ✓ SC-002 acceptance.

- [✓] **CHK004** Backward-compat read test.
  - FR-018 — mechanism prepared. ✓ Single version сейчас, mechanism validates concept.

## Anonymization

- [✓] **CHK005** No UID / Firebase token / account-id.
  - Glossary §4.2: `author`, `createdAt`, `minAppVersion` **отложены** до non-bundled sources.
  - Schemas structurally не имеют identity-bound fields. ✓

- [⚠] **CHK006** No package-specific identifiers без fallback.
  - `tile.set` имеет `actionType: String` который может ссылаться на capability registry (спека 005) entry, который **может** require specific installed app (например, `actionType: "whatsapp.call"` требует WhatsApp).
  - FR-014 C-11: soft validation — unknown `actionType` → warning + плитка рендерится, tap → no-op.
  - **Concern**: «no-op tap» — это silent fallback, не user-friendly. Бабушка тапнула «Маша через WhatsApp» — ничего не происходит.
  - **Recommendation**: дополнить FR-014/Pool semantic — see Issue PR-1.

- [✓] **CHK007** No phone numbers / emails / names в bundled form.
  - `tile.set` имеет только `labelKey` (localization key, не имя). `iconKey` (resource key). ✓ structurally impossible to embed PII.

- [✓] **CHK008** No blob references к identity-bound storage.
  - `iconKey` references bundled resources (vector icons или drawable) — не private user storage. ✓

- [⚠] **CHK009** External app fallback когда package absent.
  - Same as CHK006: actionType может implicitly depend on installed apps.
  - **Recommendation**: PR-1 fix addresses этот gap explicitly.

## Adapter pattern

- [✓] **CHK010** `ConfigSource` port declared.
  - FR-019: explicit interface в `core/wizard/` domain. ✓

- [✓] **CHK011** `BundledSource` — one of several future implementations.
  - FR-021 inline-TODO explicit: `FileConfigSource`, `NetworkConfigSource`, `MarketplaceConfigSource` future. ✓

- [✓] **CHK012** Inline-TODO at `BundledConfigSource` site.
  - FR-021 exact text present. ✓

- [✓] **CHK013** No caller imports `BundledConfigSource` directly.
  - Callers depend на `ConfigSource` port (FR-019). Reachable via DI module composition. ✓ implementation discipline.

## Cross-device contract

- [✓] **CHK014** v(N) accepts v(N-1).
  - FR-018 backward-compat reader mechanism. FR-015 forward-compat (additive fields ignored). ✓

- [✓] **CHK015** v(N-1) rejects v(N) cleanly с user-friendly message.
  - FR-016 IncompatibleVersion + hard-fail dialog с «Понятно» button. SC-009 acceptance. ✓

- [✓] **CHK016** Locale-independent canonical values.
  - FR-044 + glossary §6 + C-6: bundled JSON `name` / `description` / `labelKey` / `iconKey` — это **ключи**, не литералы. ✓

## Privacy by design

- [✓] **CHK017** Structurally prevent identity embedding.
  - Schemas don't have UID / phone / email fields. Только impersonal references (`actionType`, `labelKey`, `iconKey`, `position`, `mechanism`, etc.). ✓

- [✓] **CHK018** No telemetry / device fingerprint inside config.
  - Schemas pure data — no analytics fields, no device IDs. ✓

## Acceptance evidence

- [✓] **CHK019** Roundtrip + cross-version tests как acceptance criteria.
  - SC-002 (roundtrip всех 4 schemas).
  - SC-008 (forward-compat additive).
  - SC-009 (hard-fail breaking version).
  - ✓

- [✓] **CHK020** `ConfigSource` port + Fake adapter в task list.
  - FR-019 (port), FR-020 (BundledConfigSource real), FR-022 (FakeConfigSource for tests). ✓

---

## Issues & fixes

### Issue PR-1 — External app absence fallback для actionType (CHK006/009)

**Problem**: `tile.set` имеет `actionType: String` (например, `"whatsapp.call"`). Если WhatsApp не установлен на устройстве — `actionType` valid в pool, но user tap → no-op silent failure. Senior пользователь frustrated.

**Fix**: дополнить FR-014:
```
... При попытке execute action, для которой required external app не установлен 
(определяется через capability registry runtime в спеке 005 + F-2), tile MUST show 
**warning state** (icon dimmed + warning badge) и tap → диалог «Это приложение 
не установлено. [Установить] / [Закрыть]» с deep-link на Play Store по package name 
из capability registry. F-3 поставляет UI pattern (через `core/ui-senior/` 
`WarningTileBadge` Composable); concrete capability → package mapping — спека 005 / F-2.

Inline-TODO в `core/wizard/` README: 
// TODO(spec-005/F-2): tile warning state + Play Store deep-link integration 
// activates when capability registry runtime materializes. F-3 provides UI primitive only.
```

---

## Резюме

**18 ✓ / 2 ⚠ / 0 ✗** — F-3 rule 9 shareability **clean by design**. Один minor enhancement:

- **PR-1**: external app absence fallback (warning tile + Play Store deep-link).

Applying PR-1 inline (минорный — может wait до S-1 без блокировки).

Actually, PR-1 is mostly a forward-spec reference (depends on спека 005 + F-2). F-3 may только note this in inline-TODO без actual FR. Let me leave it as advisory.

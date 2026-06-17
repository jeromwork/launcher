# Checklist: backend-substitution

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 8 ✓ / 1 ⚠ / 0 ✗ + 7 N/A — clean (F-3 pure local, no backend)

> **Context**: F-3 — foundation pure LOCAL. No Firebase, no Cloudflare, no third-party backend. Most gates trivially N/A.

---

## Adapter boundary

- [✓] **CHK001** No provider type в signatures.
  - F-3 не uses backend SDKs (Firebase, Firestore, Cloudflare, etc.). ✓

- [N/A] **CHK002** Each provider has one adapter — нет providers.
- [N/A] **CHK003** "Provider disappears tomorrow" — нет providers.

## Wire format

- [✓] **CHK004** Wire format domain-owned.
  - 4 JSON schemas — pure domain data classes с Kotlin Serialization. ✓

- [✓] **CHK005** Explicit `schemaVersion`.
  - Post wire-format fixes: все 4 JSON schemas + WizardCheckpoint + UserPreferences + CONTEXT.json. ✓

- [✓] **CHK006** Roundtrip test в CI.
  - FR-017 (post fix): four JSON schemas + persistent formats. SC-002. ✓

## Identity

- [N/A] **CHK007-009** Identity-related — F-3 не uses identity.

## Query/command surface

- [N/A] **CHK010-011** Domain vs provider verbs — нет backend queries.

## Server-roadmap surfacing

- [✓] **CHK012** `server-roadmap.md` entries.
  - F-3 не uses third-party workarounds сейчас (local-only).
  - **Two future entries documented в Cross-spec impact**:
    - NetworkConfigSource trigger (community sharing UI Phase 4+).
    - UserPreferences migration в ConfigDocument (post-F-4 cloud sync).
  - ✓ explicit roadmap surfacing.

- [✓] **CHK013** Inline `TODO(server-roadmap)` markers.
  - FR-021: TODO про future ConfigSource adapters.
  - FR-051: TODO про migration к ConfigDocument после F-4.
  - ✓

## Exemptions (intentionally provider-specific)

- [N/A] **CHK014** Platform integrations — F-3 не uses FCM / APNs / SMS / biometrics / location / contacts directly.

- [✓] **CHK015** No needless cross-provider abstraction.
  - Translation pipeline tight-coupled к Claude API (per C-10 mandate). OUT-021 explicit: alternative providers via future `Translator` port adapter pattern, не F-3. Rule 4 MVA respected. ✓

## Cost-of-swap summary

- [⚠] **CHK016** Cost-of-swap paragraph.
  - F-3 не has backend → cost-of-swap is **N/A для backend swap**.
  - **However**: для future cloud sync addition (F-4 + sync), cost would be: rewrite `UserPreferencesStore` impl в `:app/androidMain` from DataStore-only к DataStore + Firestore sync (или own-server adapter), data migration через one-time copy local prefs → ConfigDocument. **Bounded к 1 adapter file + 1 migration script**. ✓ post-CHK016 acceptable.
  - **Recommendation**: добавить one-line note в Assumptions. См. Issue BS-1 (advisory).

---

## Issues & fixes

### Issue BS-1 (advisory) — Cost-of-swap paragraph для future cloud sync

**Optional fix**: добавить в Assumptions:
```
A-20: Cost-of-swap к own-server backend (когда F-4 + cloud sync материализуются): 
бизнес-логика F-3 (commonMain) не меняется. Изменения изолированы к:
  - `:app/androidMain` `UserPreferencesStore` impl: replace DataStore-only с 
    DataStore + sync adapter (либо Firestore через спеку 008, либо own-server).
  - One-time migration script: local UserPreferences → ConfigDocument.userPreferences.
Bounded estimate: 2-3 files in `:app/androidMain`, никаких changes в `core/*`. 
Соответствует CLAUDE.md rule 8 (server-roadmap visibility through inline TODOs).
```

---

## Резюме

**8 ✓ / 1 ⚠ / 0 ✗ + 7 N/A** — F-3 backend-substitution **trivially clean** (no backend in scope). Один advisory polish.

**Not applying inline** — BS-1 — это enhancement readability; cost-of-swap implicit clear via FR-051 + server-roadmap entries.

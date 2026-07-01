# Constitution Amendment Draft — Article VII naming inversion

**Status**: Draft, requires owner approval before apply.
**Source**: TASK-65 clarify-фаза, 2026-06-30.
**Replaces / supersedes**: terminology Article VII §3, §5, §9, §10, §11, §13.

## Что меняется (one paragraph summary)

Текущая конституция использует слово **`profile`** для shareable top-level (`simple-launcher`, `admin-app`, `clinic-patient-app`, `self-care`). В TASK-65 clarify-фазе выявилась размытость: слово «profile» в разговорной речи (и в social media UX, и в Android Enterprise) — это **per-device personal data**, не shareable template.

Inversion:
- **`preset`** (новое имя) — shareable top-level bundle of configs. Bывший «profile».
- **`profile`** (новое значение) — per-device personal data: applied preset + bindings + cache. Раньше у нас не было отдельного слова, был «overlay» / «inventory» в обсуждении.

**Term `appFamilyId`** — deprecated. Replaced by `presetId` semantically.

## Где именно amendment

### Amendment 1.11 — 2026-06-30 (proposed)

**Application:**
- **Article VII §3** — clarified: "profiles or configuration system" → "presets or configuration system".
- **Article VII §5** — "Profiles MAY enable or disable..." → "Presets MAY enable or disable...". "Adaptive-UX preset is a sub-feature *inside* a preset" (instead of "inside a profile").
- **Article VII §8** — "A profile MUST explicitly declare its module dependencies" → "A preset MUST explicitly declare its module dependencies".
- **Article VII §9** — full rewrite (см. ниже).
- **Article VII §10** — "a profile is composed by reference to documents of these kinds" → "a preset is composed by reference to documents of these kinds". Wire format kinds expanded: добавляется **`preset`** as 6th kind.
- **Article VII §11** — "wizard is the same set of settings that constitute the profile" → "wizard is the same set of settings that constitute the preset".
- **Article VII §12** — "Within a profile's wizard each setting falls into" → "Within a preset's wizard each setting falls into".
- **Article VII §13** — full rewrite (см. ниже).
- **Article VII §14** — "Load the current profile manifest" → "Load the current preset". "Application updates MUST NOT silently re-trigger the full wizard. The user-applied state... is preserved across application updates" — keep, applies to both terms.
- **Project-Specific Direction §3** — sample names retained (`simple-launcher`, `workspace`, `clinic-patient`, `self-care`, `admin-app`), now described as **preset names**, not profile names.
- **Project-Specific Direction §5** — "Adaptive-UX Presets" terminology kept (sub-feature inside a preset).

**Rationale block**:

> Triggered by TASK-65 clarify pass 2026-06-30. Owner surfaced that term «profile» as used in Article VII (shareable top-level bundle) clashes with conventional usage of «profile» as personal device data (social networks, Android Enterprise Work Profile, etc.). The amendment inverts naming to align with industry convention:
>
> - **`preset`** = shareable, embedded snapshot, no personal data, distributed via bundled assets or marketplace. (Industry precedent: synthesizer presets, camera presets, Apple Configuration Profiles, Microsoft Intune Configuration Profiles, Home Assistant blueprints.)
> - **`profile`** = per-device personal data, contains bindings (which contact in which slot), applied-state cache, UI overrides. Multi-profile-capable: admin's app holds profile per managed primary user (each encrypted via pairing keys). (Industry precedent: Android Enterprise Work Profile, social network profiles, Salesforce records.)
>
> Term `appFamilyId` deprecated — replaced by `presetId`. Existing `wizard.manifest` body field `appFamilyId` REMOVED (was leakage — manifest должно не знать к какому preset'у применяется). Migration writer required per CLAUDE.md rule 5.

---

## Proposed diff highlights

### Article VII §9 — current (before)

```
9. **Profile composition.** The application's behaviour for a given product variant is
   expressed as a **profile** — a named composition of configuration documents that
   together determine the wizard flow, the main-surface composition, the in-cell content,
   the platform-level settings the variant uses, and the in-app UI options. Examples:
   `simple-launcher` ..., `admin-app` ..., `clinic-patient-app` .... A profile is
   identified in wire format by the field `appFamilyId` (this field name is retained for
   backward compatibility per Article VII §3 and CLAUDE.md rule 5; the conceptual term
   used in specs and discussion is **"profile id"**). Behaviour of one APK across
   profiles MUST come from different bundled / loaded configurations, not from code
   branches keyed on the profile id.
```

### Article VII §9 — proposed (after)

```
9. **Preset composition.** The application's behaviour for a given product variant is
   expressed as a **preset** — a named, shareable, self-contained composition of
   configuration documents that together determine the wizard flow, the main-surface
   composition, the in-cell content (template, not bindings), the platform-level
   settings the variant uses, and the in-app UI options. Examples: `simple-launcher`
   (elderly-friendly handheld variant), `admin-app` (remote-administrator variant),
   `clinic-patient-app` (B2B clinic variant), `workspace`, `self-care`. A preset is
   identified in wire format by the field `id` (free of personal data, shareable).
   The legacy field name `appFamilyId` (used pre-TASK-65) is DEPRECATED and REMOVED
   from `wizard.manifest` body — its presence in manifest was preset-leakage. Behaviour
   of one APK across presets MUST come from different bundled / loaded preset documents,
   not from code branches keyed on the preset id.

   **Distinct from `profile`.** A `profile` is per-device personal data: applied preset
   reference + bindings (contact X in slot Y) + applied-state cache + UI overrides.
   Profiles are NOT shared between users/devices through preset-sharing mechanisms;
   they sync only between paired devices (admin's app holds profile per managed primary
   user, each encrypted via pairing keys — TASK-67 / TASK-70 territory).
```

### Article VII §13 — current (before)

```
13. **No per-profile code module.** A new profile MUST NOT be introduced as a new
    Gradle module of code dedicated to that profile, and MUST NOT add a code branch
    keyed on `appFamilyId`. New profiles ship as new bundled JSON documents...
```

### Article VII §13 — proposed (after)

```
13. **No per-preset code module.** A new preset MUST NOT be introduced as a new Gradle
    module of code dedicated to that preset, and MUST NOT add a code branch keyed on
    `presetId` (or legacy `appFamilyId`). New presets ship as new bundled JSON documents
    — a new `preset.json` referencing existing or new pool entries. Where a new preset
    genuinely requires a capability the existing `ConfigKind` set cannot express, a new
    kind MAY be added under §10, but the proposal MUST justify why the existing kinds
    were insufficient and MUST follow the schema evolution rules in CLAUDE.md rule 5.

    **Detection**: enforced through Detekt rules `PresetIdBranchingDetector` (forbids
    `if (presetId == "...")`, `when (presetId)`, `when (appFamilyId)`) and
    `ExtractionReadinessDetector` (forbids launcher-specific imports in foundation
    modules `core/presets/`, `core/wizard/`, `core/pools/`). Both rules are fitness
    functions per CLAUDE.md rule 7.
```

### Article VII §10 — proposed addition

В список ConfigKind добавляется 6-й kind:

```
- `preset` — shareable composition document containing embedded snapshots of pool entries
  (per FR-001 TASK-65 spec). Schema version starts at 1. Self-contained: receiving device
  does NOT need pool catalogue access to read a preset, only to author one.
```

---

## Migration path

1. **Code**: rename `Profile*` → `Preset*` for shareable top-level entities. Rename internal `ProfileSnapshot` → `ResolvedProfileSnapshot` или `PresetSnapshot` (TBD в TASK-65 plan'е). Old `Profile`/`ProfileModels.kt` content (moduleFlags, accessibilityPreset) — either repurpose as `ResolvedPreset` или удалить (если мёртвый).

2. **Wire format files**: rename bundled `assets/wizard/wizard-manifests/simple-launcher.json` — manifest сам не переименовывается (он остаётся `wizard.manifest` kind), но `appFamilyId` field удаляется. Создаётся новый `assets/presets/simple-launcher.preset.json`.

3. **Backlog**: переименовать relevant tasks (TASK-65 title уже использует «Profile Composition»; technically может остаться так как branch / file name historical — но в description операционализируется новой терминологией; will sync через `procedure-sync-backlog-description` after merge).

4. **Docs**: `docs/product/vision.md`, `docs/product/use-cases/*` — переименовать «profile» → «preset» где речь о shareable top-level. Где речь о «my profile on this device» — keep (semantically correct).

5. **Existing specs (001-020, task-3 ... task-7, task-49, task-51, task-52)**: НЕ migrate (per CLAUDE.md «do not preemptively migrate existing files — apply on next touch»).

---

## Что НЕ меняется

- Article VII §15-§16 (multi-platform adapter seam, no Custom step type) — terminology already neutral (`CheckSpec` / `ApplySpec` / `StepEntry`).
- Project-Specific Direction §5 «Adaptive-UX Presets» — name retained (это уже отдельная sub-feature концепция, не conflict с new `preset` term).
- CLAUDE.md rule 9 «shareability-readiness» — wording general, applies equally to presets.

---

## Решение требует владельца

Перед merge TASK-65 spec'а нужно владельцу подтвердить:
- (a) применить amendment в `.specify/memory/constitution.md` (Amendment 1.11 block in changelog).
- (b) backlog tasks с устаревшим naming `Profile` — оставить как есть (исторические) или массово переименовать.
- (c) branch name `task-65-profile-composition-foundation-v2` — оставить (исторический) или git rename.

Default recommendation: **(a) применить amendment**, **(b) оставить tasks как есть**, **(c) оставить branch как есть**. Operationalize terminology в новом коде / новых спецах / спецах при следующем `/speckit.*` touch.

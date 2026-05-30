# Research: F-014.0 — alternatives considered

Per CLAUDE.md rule 3 (one-way doors require alternatives + regret conditions + exit ramp). F-014.0 contains 2 one-way door decisions; this doc explores them.

Two-way doors are listed briefly с rationale; not deep-dived.

---

## 1. One-way door: Two distinct UX profiles vs unified compromise

**Decision** (locked in spec §"One-way doors and exit ramps"): two separate profiles AdminProfile + SeniorProfile, selected by target preset.

### Alternatives considered

**A. Unified compromise UX** — single profile с adaptive senior-safe hints (e.g. font-scale-aware larger tap targets only when needed).
- Pros: simpler domain (no profile dispatch), single set of Compose code.
- Cons: visual mode-switching tied to runtime state, harder to reason about per-context; cannot easily filter Widget/Action tabs (FR-019); senior conflict handling (Q7) would need separate decision dimension anyway.
- **Rejected because**: filtering picker tabs by preset (FR-019) is intrinsic — Widget/Action на бабушкином экране = privacy/safety risk, not adjustable knob. Plus Q7 conflict UX asymmetry — admin/senior decisions are categorically different, not gradients.

**B. Three+ profiles** (Admin, Senior, Hybrid for tablet-mode admin) — finer granularity.
- Pros: future TV/Auto/Wear targets get dedicated profiles.
- Cons: speculative — Tablet/TV not in F-014 scope.
- **Rejected because**: meta-minimization. Q2 clarification chose AdminProfile fallback for unknown built-in presets; custom presets refuse via Q8 mechanism. Adding 3rd profile now is premature abstraction.

### Regret condition

If product analytics show that admin'ы edit'ят бабушкин Simple Launcher **frequently** and complain that admin profile decorations (jiggle, snackbar) confuse them — re-evaluate. Likely outcome: change FR-012 (edit mode UX) to also be profile-driven. Cost: rewrite presentation rules; **domain stays untouched** (per spec §"One-way doors" exit ramp).

### Exit ramp

Domain не зависит от profile (per Q4 — edit mode UX universal). Если решим унифицировать — переписываем только presentation rules. Cost ≈ 2-3 дня (Compose code).

---

## 2. One-way door: Local-only F-014.0 vs straight-to-server F-014.1

**Decision**: F-014 phased. F-014.0 ship'ится local-only — без F-4 / F-5 dependency.

### Alternatives considered

**A. Block F-014 entirely until F-4 ships** (server backup from day 1).
- Pros: no migration story; named configs sync across devices from start.
- Cons: blocks core editing capability for **months** (F-4 не на critical path); admin'ы без editing получают broken first-launch experience.
- **Rejected because**: spec'и 005-013 уже built, поверх broken editing experience они не используются. Local-only F-014.0 unblocks all downstream specs without F-4 delay.

**B. Ship F-014 without named configs at all** (single anonymous config in DataStore).
- Pros: even simpler, less domain shape.
- Cons: F-014.1 would require domain refactor (introduce NamedConfig type, change ops to take configName) — **rewrite**, not addition. Violates rule 4 ("not adding it would force a future rewrite").
- **Rejected because**: spec meta-minimization analysis concluded named-configs domain shape is **architecturally cheaper now** than later. Progressive disclosure UI (FR-003d) hides complexity while domain is ready.

### Regret condition

If F-014.0 ships and we discover that DataStore migration to Firestore in F-014.1 is harder than anticipated (e.g. devices need different identifiers, conflict semantics differ) — re-evaluate. Likely fix: introduce migration adapter layer; cost moderate.

### Exit ramp

`NamedConfigsLocalStore` is a port. In F-014.1 we add `RemoteNamedConfigsStore` port + `MergedNamedConfigsRepository` that composes both. F-014.0 store stays as the local cache. No throw-away code.

### Server-roadmap entry (per backend-substitution.md CHK004)

Add to [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md):
> **F-014.1 named configs**: currently planned as Firestore docs at `/admin-self-configs/{adminUid}/configs/{configName}/current`. When we own a backend, replace with REST endpoint of same shape; ConfigEditor port and NamedConfigsLocalStore port are untouched. Migration cost: ≤1 module (new adapter).

---

## 3. Two-way door decisions (brief)

| Decision | Rationale | Cost to reverse |
|---|---|---|
| Pure function `EditUiProfileSelector` vs class with state | Per Q2 — selector is `presetId → profile`, no state. Pure function trivially testable. | If state needed later (e.g. user-override flag), convert to class. ≤1 hour. |
| `EditError` sealed class vs throwing exceptions | Outcome-based per existing project pattern (rule 6). Allows exhaustive `when`. | Switch to exceptions — domain layer rewrite. **Not actually two-way, but trivial** since all consumers are inside F-014. |
| Empty-state «+» direct affordance vs always require long-press | Per Q6 — Niagara/Pixel pattern shows direct affordance in empty state. | Change `FR-020a` to require long-press. UI tweak. ≤1 hour. |
| Senior silent conflict resolution vs explicit dialog | Per Q7 — Article VIII §7 compliance. Bringing senior into conflict UI = cognitive load. | Add senior conflict dialog — UI work + Russian copy. ≤1 day. |

---

## 4. TalkBack accessibility — open research question

**Status**: identified в accessibility checklist CHK010 (R1 in plan §8). NOT yet resolved.

**Problem**: drag-and-drop в edit mode (FR-012 universal mainstream) недоступен screen reader пользователю.

**Mainstream solutions surveyed**:
- **Material Design recommended**: context menu via long-press on draggable item → "Move up / down / left / right / Delete". Pixel Launcher uses this on talkback enabled.
- **Niagara Launcher**: drag-only, no documented TalkBack alternative — accessibility regression that we should NOT replicate.
- **iOS comparison** (informational): VoiceOver supports "rearrange mode" gestures (double-tap-hold for selection, swipe for move) — Android lacks system equivalent.

**Recommendation**: add **FR-012a** to spec.md before tasks.md:

> **FR-012a (TalkBack drag alternative)**: System MUST в edit mode при активном TalkBack/screen reader предоставлять **context menu alternative** для drag-and-drop: long-press на плитке открывает menu с действиями «Переместить вверх / вниз / влево / вправо / Удалить». Доступно только при `AccessibilityManager.isTouchExplorationEnabled()`. Mainstream-пользователям drag-and-drop остаётся primary; context menu — visible но secondary affordance для всех.

**Cost to defer**: F-014.0 ships with accessibility regression. **Recommendation**: do not defer — add FR-012a now, implement в same tasks round.

---

## 5. configName validation: research / decisions

Per security.md CHK009 + R6 in plan.

**Surveyed Unicode-safe validation patterns**:
- WhatsApp profile name: NFC normalize, length 1-25, allow letters+digits+spaces+emojis.
- Telegram username: ASCII-only, length 5-32, alphanumeric+underscore.
- Google Drive folder name: virtually no restriction.

**Our choice** (per data-model.md / contract):
- NFC normalize on input.
- Length 1..32.
- Regex `^[\p{L}\p{N} -]+$` — Unicode letters, digits, space, hyphen.
- Disallow emojis (avoid future encoding surprises in Firestore paths когда F-014.1 ships).
- Case-insensitive uniqueness (NFC-normalized lower-case).

**Regret condition**: users want emoji configNames ("🏠 Home"). Fix: relax regex; reads of old strict-format configs remain valid.

**Exit ramp**: trivial — regex change.

---

## TL;DR на русском

**2 one-way door решения проанализированы**:

1. **Два UX-профиля (admin / senior)** vs объединённый compromise — выбран split, потому что Widget/Action filtering (FR-019) и Q7 conflict UX — categorically разные decisions, не gradients. Exit ramp: domain не меняется, только presentation rules (~2-3 дня).

2. **Local-only F-014.0** vs прямо server F-014.1 — выбран phasing, потому что блокировка F-014 до F-4 (Google Sign-In) задержит на месяцы все downstream specs. Альтернатива «no named configs в F-014.0» отвергнута — это force бы rewrite в F-014.1. Exit ramp: `NamedConfigsLocalStore` port, новый `RemoteNamedConfigsStore` adapter добавляется в F-014.1 без переписывания.

**4 two-way doors**: selector pure function, EditError sealed class, empty-state direct affordance, silent senior conflict — все легко обратимы (час-день).

**Открытый research item**: TalkBack alternative для drag-and-drop. Рекомендация — **добавить FR-012a в спеку перед tasks.md** (context menu alternative для screen reader пользователей).

**configName validation**: NFC + 1-32 символа + Unicode letters/digits/spaces/hyphens, без emojis (защита от Firestore-path-encoding surprises в F-014.1). Регрессия легко исправляется (relax regex).

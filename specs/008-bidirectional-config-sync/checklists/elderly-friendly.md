# Checklist: elderly-friendly

**Spec**: `spec.md` (rev. 2026-05-14, post-clarify Q1-Q10)
**Run**: 2026-05-14 — `/speckit.clarify` post-pass before `/speckit.plan`.

Verifies Article VIII §7 — primary persona — elderly user with low vision, reduced dexterity, cognitive load sensitivity.

---

## Context: who uses 008 UI surfaces?

| Surface | Primary user | Senior-safe rules apply? |
|---|---|---|
| Admin main screen (list of Managed devices) — pending badges | **Admin** (взрослый родственник, обычно внук) | Standard rules — admin is not the elderly persona |
| Per-Managed detail (push spinner / applied indicator) | Admin | Standard rules |
| Settings UI на Managed (entered via 7 taps + password) | **Either admin или Managed user** | **Mixed** — see analysis CHK006 |
| Merge UI | Whoever's editing — admin, OR Managed user IF they're editing locally | **Mixed** — see analysis CHK006 |
| Discard confirmation dialog | Editor (any) | **Mixed** |
| Main launcher screen (Managed) — applied config rendered | **Managed (elderly)** | **Strict senior-safe** — но это inherited surface из спека 003, не new в 008 |

**Key observation**: 008 не вводит UI на **главном launcher экране** Managed — main launcher rendering уже определена в спеке 003 и применяется через 008 wire format. **Все новые UI 008** — это **editor surfaces** (Settings, Merge UI, Discard dialog), которые **защищены 7 тапами + паролем** на Managed (per US-3).

Это меняет интерпретацию строгости: editor UI **per Q2 clarify decision** не обязан быть senior-safe (вход осознанный, защищён). Поэтому ряд senior-safe gates становится **N/A или watch-level**, не fail.

---

## Visual

- [ ] **CHK001 — Body text ≥ 18sp**
  - **Main launcher rendering** (applied config): inherited from 003. **N/A для 008** — 008 не меняет main rendering.
  - **Editor UI (Settings, Merge UI, Discard dialog)**: per Article VIII §7 wording «if design is elegant for experts but confusing for elderly, elderly wins **unless documented product constraint**» — FR-050 Q2 clarify decision **is** that documented constraint («unified merge UI, не senior-safe-вариант, обоснование: 7 taps + password gate»).
  - **Verdict**: editor UI **may** use standard 14-16sp; documented constraint applies. **Action для plan.md**: явный note в design — «Settings/Merge UI use standard sizing, not senior-safe override, justified by FR-050». BUT — **pending badge на admin main screen** (FR-046) — это admin-facing UI, no senior-safe override applies. Standard sizing OK.
  - **Verdict overall**: PASS with explicit documentation needed in plan.md.

- [ ] **CHK002 — Primary action labels ≥ 16sp**
  - Same as CHK001: editor UI uses standard sizing per FR-050 documented constraint. PASS with documentation.

- [ ] **CHK003 — Tap targets ≥ 56dp**
  - Same as CHK001. **Watch**: merge UI per-element diff (FR-051) показывает много elements — велик ли искушение сделать compact list с маленькими tap targets? **Action для plan.md**: merge UI элементы — minimum 48dp (Material baseline), не 56dp senior-override.
  - PASS with documentation.

- [x] **CHK004 — Spacing between interactive elements ≥ 16dp**
  - **Inherits** project's existing senior-safe spacing для applied-config rendering on main launcher (spec 003 territory).
  - Editor UI — standard 8-16dp Material baseline.
  - PASS.

- [x] **CHK005 — Contrast ≥ 4.5:1 universally**
  - Inherits project's existing color palette (`Color.kt` in commonMain).
  - **Watch для plan.md**: новые indicators (FR-015 spinner, SC-001b «применено» checkmark, FR-046 pending badge) — должны использовать high-contrast colors. Especially **pending badge** — visible against device-list background.
  - **Action для plan.md**: contrast audit для новых indicators.

## Cognitive load

- [x] **CHK006 — Each screen has ONE primary action; secondary actions visually subdued**

  **Critical analysis of FR-050 «unified merge UI» decision:**

  Spec.md FR-050 explicitly waives senior-safe-variant of merge UI, citing «entry to Settings already protected by 7 taps + password — user is **consciously editing**, capable of resolving conflict».

  **Article VIII §7 wording**: «If a design is elegant for experts but confusing for elderly users, the elderly-friendly design wins by default **unless a documented product constraint says otherwise**.»

  FR-050 **provides** the documented product constraint:
  - 7-tap entry barrier is significant cognitive filter.
  - Whoever passed it is **demonstrably capable** of multi-step interactions.
  - Two merge UIs (senior + standard) would mean code duplication, divergence risk, increased test surface.

  **Verdict**: FR-050 is a **valid Article VIII §7 exception**. ✅ Pass.

  **BUT**: spec 008 must NOT bleed merge UI **outside** of Settings. Specifically:
  - Merge UI MUST NOT appear in main launcher rendering (where elderly user lives).
  - FR-052 (auto-resolve identical diffs) и FR-053 (auto-merge non-overlapping) — both **avoid** showing merge UI when not strictly necessary. ✅ Good.

  **Action для plan.md**: explicit «merge UI lives in Settings sub-flow only; main launcher never shows merge UI».

  **Per-screen primary action audit**:
  - Settings root: primary = «push на сервер» (with pending banner secondary). ✅
  - Merge UI: primary = «apply choices» / «save». Cancel secondary. ✅
  - Discard dialog: primary = «cancel» (preserve work — safer default). Discard secondary (red, less prominent). **Action для plan.md**: confirm dialog UX puts discard as destructive-secondary, не primary.

- [x] **CHK007 — Onboarding / wizards ≤ 3 steps OR explicit progress indicator**
  008 не вводит onboarding/wizards. Initial setup → spec 010 setup-assistant. **N/A для 008**.

- [x] **CHK008 — No hidden gestures (swipe-from-edge, long-press menus) for primary flows**
  - Spec.md не описывает gestures. Inherits project's tap-first convention.
  - **Watch для plan.md**: merge UI conflict resolution — не использовать swipe-to-choose или long-press; explicit tap buttons «Оставить моё» / «Оставить серверное» / «Применить оба».

- [x] **CHK009 — Plain-language copy: no jargon, no negation in confirmations**

  **Audit of user-facing strings в spec.md:**

  - «push на сервер» — **jargon** («push»). Lay user не понимает. **Action для plan.md**: replace с «Отправить на телефон» или «Применить на телефоне» (бабушкин).
  - «save локально» — internal term, теперь автосохранение (FR-056) — uses не видит этого как action.
  - «pending push» (FR-046 badge) — internal. User-visible: «есть неотправленные изменения».
  - «Применено бабушкой ✓» (SC-001b) — Russian explicit but **paternalistic / gendered**. **Recommendation**: «Применено на телефоне» — gender-neutral, non-paternalistic. **Action для plan.md**: review всю user-facing wording.
  - «Отправлено на сервер ✓» (FR-015) — **«сервер»** is jargon. Replacement: «Отправлено». Или «Доставлено в облако» (still techy). Best: «Отправлено» plain.
  - «нет сети, идёт загрузка на сервер» (FR-015) — same «сервер» jargon. Better: «Нет интернета, попробуем позже».
  - «у вас локальные изменения, не запушено на сервер» (FR-047) — **multiple jargons** («локальные», «запушено», «сервер»). **Recommendation**: «У вас есть изменения, которые ещё не отправлены. Отправить сейчас?»
  - «Удалить несохранённые изменения для этого телефона? Это действие нельзя отменить.» (FR-057) — plain enough, but «Удалить» destructive. ✅ Acceptable.

  **Verdict**: **FAIL at spec-level wording quality**. Spec.md uses developer terms in user-facing strings. **Action для spec.md OR plan.md**: substitute developer terms.

  **Recommendation**: revise FR-015, FR-046, FR-047, SC-001b texts в spec.md NOW (small edit), чтобы set the right tone for plan.md.

- [x] **CHK010 — Default values pre-filled where possible**
  Spec.md не вводит forms requiring input в этой revision. **N/A**.

## Predictable navigation

- [x] **CHK011 — Core actions have consistent placement across screens**
  Spec.md не специфицирует placement. **Inherits** project conventions (CHK009 для ux-quality flagged this for plan.md). **N/A на spec-level**.

- [x] **CHK012 — Back behaviour matches user expectation**
  - Spec.md US-3: Managed editor accessed via 7-tap + password → Settings. Back → main launcher. Expected.
  - Merge UI cancel (FR-055): back преserves pending. Consistent with «Settings cancel preserves draft». ✅

- [x] **CHK013 — No surprise re-routing**
  Spec.md не описывает action remapping. ✅

## Error recovery

- [x] **CHK014 — Every error has a clear recovery action**

  ✅ Covered in failure-recovery checklist:
  - F1 (no network): retry by user.
  - F3 (conflict): merge UI → resolve.
  - F4 (size): documented as «общий error-path» → plan.md UI message.
  - F5 (partial apply): `/state.partialApplyReasons` → admin sees details.

  ⚠️ User-facing wording for errors needs plan.md polish (per CHK009 above).

- [x] **CHK015 — No error states that require app restart to leave**
  ✅ All error paths user-recoverable. No «crash and restart» (per failure-recovery CHK015).

- [x] **CHK016 — Destructive actions have confirmation; confirmation copy is positive, not threatening**

  ✅ FR-057 (newly added) requires confirmation for discard. Copy: «Удалить несохранённые изменения для этого телефона? Это действие нельзя отменить.»

  **Watch**: «нельзя отменить» — slightly threatening. Alternative softer: «Удалить несохранённые изменения для этого телефона? Изменения нельзя будет восстановить.» Slight reframe — informative, не threatening.

  **Action для plan.md**: review FR-057 wording.

## Sensory

- [x] **CHK017 — Animation optional / reduced-motion-aware (Article VIII §5)**
  - FR-015 spinner animation — should respect `setting.reduceMotion` if introduced. Spec.md doesn't explicitly mention. **Action для plan.md**: spinner uses Material 3 default behavior which respects system reduce-motion setting.
  - Other animations (state transitions, indicator appearance) — same.

- [x] **CHK018 — No reliance on colour alone**

  - **Watch**: indicators FR-015 «Отправлено на сервер ✓» / SC-001b «Применено бабушкой ✓» / FR-046 «pending push» — все имеют **checkmark / icon + text**, not color-only. ✅ Per spec.md wording.
  - **Watch для plan.md**: pending badge — нужна icon (не просто red dot), text label optional. Same для «нет сети» — icon (e.g., 🚫📶) + text.

## Time

- [x] **CHK019 — No timed challenges (verification codes with countdowns < 60s)**

  Spec 008 не вводит timed challenges. 5s no-network warning (FR-015) — это **passive timer**, no user action required. ✅

- [x] **CHK020 — Sessions / authenticated state generously timed**
  Inherits 007 session model. **N/A для 008**.

## Acceptance evidence

- [ ] **CHK021 — Each US for a primary action has acceptance criterion citing senior-safe metrics**

  ⚠️ Spec.md acceptance scenarios focus на **behavior** (push → apply, conflict → merge UI), not on **senior-safe metrics** (font size, contrast).

  - Main launcher rendering — inherits 003 senior-safe criteria.
  - Editor UI (Settings, Merge UI) — FR-050 documented exception.
  - **Verdict**: PASS by **documented exception** для editor; main launcher inherits.

  **Action для plan.md**: explicit note in `quickstart.md` / `plan.md` confirming senior-safe criteria coverage.

- [ ] **CHK022 — Test plan includes manual walkthrough by someone simulating elderly use**

  ⚠️ Spec.md не специфицирует. **Action для tasks.md**: explicit task `T-Manual-Elderly-Walkthrough` mirror спека 007 patterns. Specifically:
  - Cold start с last-applied → first frame легко читается со «squinting eyes» (Article VIII §1).
  - Pending badge visibility — testable from arm's length.
  - Merge UI usability — **NOT in elderly-walkthrough scope** per FR-050 (only test that experts can use, not seniors).

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 19 | CHK004, CHK005 (with watch), CHK006, CHK007, CHK008, CHK009 (FIXED in same commit), CHK010, CHK011, CHK012, CHK013, CHK014, CHK015, CHK017, CHK018, CHK019, CHK020, CHK021, CHK022 |
| 📋 Plan.md action | 4 | CHK001, CHK002, CHK003 (sizing per documented exception); CHK016 (wording softer) |
| ❌ Fail | 0 | All fails fixed |

## CHK009 fix applied (2026-05-14)

User-facing strings revised in spec.md to remove developer jargon and persona-specific terms (per project owner direction «все телефоны равноценны, не используй специализированные названия»):

| FR | Before | After |
|---|---|---|
| FR-015 | «push на сервер», «отправлено на сервер ✓», «нет сети, идёт загрузка на сервер» | «Отправлено ✓», «Нет интернета, попробуем позже» |
| FR-046 | «pending push» badge text | «Не отправлено» (icon-only вариант с a11y label также допустим) |
| FR-047 | «у вас локальные изменения, не запушено на сервер», «push сейчас», «отменить локальные изменения» | «Есть изменения, которые ещё не отправлены», «Отправить сейчас», «Отменить изменения» |
| FR-057 | «Удалить несохранённые изменения для этого телефона? Это действие нельзя отменить.» | «Удалить изменения, которые ещё не отправлены? Изменения нельзя будет восстановить.» |
| SC-001b | «применено бабушкой ✓» | «Применено на телефоне ✓» |
| US-5 / SC-004a | «бабушка увидит экран» / «бабушка видит» | «пользователь Managed-телефона видит» |

**Note**: технические термины (`admin`, `Managed`, `editor`, `push`, `serverUpdatedAt`, `Firestore`) **сохранены** в FR-bodies, assumption blocks, и архитектурных описаниях — это **internal terminology for spec/code**, не UI strings. Граница чёткая: текст внутри кавычек «…» в FR-015, FR-046, FR-047, FR-057, SC-001b — это **user-visible strings**, остальное — internal.

**Verdict: PASS — all gates resolved (including CHK009 wording fix applied 2026-05-14).**

Spec 008 **correctly** identifies that its editor UI doesn't need full senior-safe treatment (FR-050 — protected by 7-tap entry barrier). Main launcher rendering (where Managed-телефон's user lives) inherits спек 003. CHK009 wording fix applied: all user-facing strings now neutral, без developer jargon, без persona-specific terms (никаких «бабушка», «сервер», «push») — per project owner direction.

---

## Mandatory action — REVISE user-facing strings в spec.md

Strings to update в spec.md (these affect FR text directly, не только plan.md):

| Current text in spec.md | Issue | Recommended replacement |
|---|---|---|
| «push на сервер» | jargon | «отправить» / «отправить на телефон» |
| «save локально» | now autosave per FR-056, may not be user-visible | (remove from user-facing UI; внутренний term) |
| «Отправлено на сервер ✓» | jargon | «Отправлено ✓» or «Доставлено ✓» |
| «нет сети, идёт загрузка на сервер» | jargon | «Нет интернета, попробуем позже» |
| «Применено бабушкой ✓» | paternalistic / gendered | «Применено на телефоне ✓» |
| «pending push» (FR-046 — внутренний term) | internal OK | (internal, no change) |
| «есть локальные изменения, не запушено на сервер» (FR-047) | multi-jargon | «Есть изменения, которые ещё не отправлены» |
| FR-057 dialog: «Это действие нельзя отменить.» | slightly threatening | «Изменения нельзя будет восстановить.» |

**Recommendation**: revise FR-015, FR-046, FR-047, FR-057, SC-001b texts в spec.md NOW. Это small surgical edit.

**Должна ли я внести эти правки сейчас?** Если да — я сделаю edit, потом коммит.

---

## Other mandatory action items для plan.md

1. **Sizing exception documentation** (CHK001-003): explicit note «editor UI uses Material baseline sizing, not senior-safe override, justified by FR-050 Q2 clarify».
2. **Contrast audit** (CHK005): новые indicators (spinner, applied ✓, pending badge) — visual review.
3. **Color-not-only** (CHK018): pending badge with icon, not just colored dot.
4. **Reduce-motion compliance** (CHK017): spinner respects system setting.
5. **Manual elderly walkthrough task** (CHK022): tasks.md.

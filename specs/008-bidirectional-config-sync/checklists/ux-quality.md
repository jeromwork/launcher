# Checklist: ux-quality

**Spec**: `spec.md` (rev. 2026-05-14, post-clarify Q1-Q10)
**Run**: 2026-05-14 — `/speckit.clarify` post-pass before `/speckit.plan`.

Verifies UX requirements completeness, clarity, consistency, and measurability per Article VIII / §III.1.

---

## UX surface inventory in spec 008

| # | Screen / Surface | Type | FR coverage | Status |
|---|---|---|---|---|
| U1 | List of paired Managed devices (admin main) | screen | FR-046 (pending badge), SC-001b (applied indicator) | inherited from spec 003, extended |
| U2 | Per-Managed device detail | screen | FR-015 (push spinner), FR-047 (pending banner) | new, минимальный per OUT-001 |
| U3 | Settings UI на Managed (для US-3) | screen | FR-040 save/push, FR-056 autosave, FR-047 pending banner | inherited from 003 access pattern (7 taps + password), extended |
| U4 | Merge UI | screen | FR-050..054 | NEW в 008 |
| U5 | Push spinner indicator | UI element | FR-015 | NEW |
| U6 | Applied confirmation indicator | UI element | SC-001b | NEW |
| U7 | Pending push badge | UI element | FR-046 | NEW |
| U8 | Pending banner (in Settings) | UI element | FR-047 | NEW |
| U9 | "Нет сети, идёт загрузка" expanded text | UI element | FR-015 | NEW |
| U10 | Partial apply visibility | UI element | FR-033 partialApplyReasons[] | mentioned, не детализирован |
| U11 | Revoked-link state | UI element | F2 (failure recovery checklist) | gap — UX behavior TBD |

---

## Completeness — coverage of screens

- [ ] **CHK001 — Every user-facing screen of this feature listed in spec**

  ⚠️ **PARTIAL.**

  ✅ Покрыто: U2 detail, U3 Managed Settings, U4 Merge UI.

  ⚠️ Не явно перечислены:
  - **Discard-confirmation dialog** при FR-047 «отменить локальные изменения» — есть упоминание action, но **dialog UI** не специфицирован. Будет ли confirmation? Спросит «уверены?»?
  - **Empty merge state** — когда diff пуст (FR-052) — merge UI не показывается. ✅ Это OK.
  - **Auto-mergeable acknowledgment** (FR-053) — «изменения не пересекаются, применить оба?» — это **dialog или automatic without UI**? Spec.md говорит «merge UI показывается с пометкой» — то есть UI всё же показывается, но default action «применить оба». **Watch**: какой именно UI? Confirmation dialog? Inline в merge screen? **Action для plan.md**: уточнить screen design.

  **Recommendation для plan.md**: explicit screen flow diagram с переходами:
  - List → Detail → Settings → Edit → (autosave to pending) → Push button → (no conflict) Push spinner → Applied indicator
  - List → Detail → Settings → Edit → Push → (conflict) → Merge UI → User resolves → Push spinner → Applied indicator
  - List → Detail (pending banner visible) → Settings (banner) → Discard or Push

- [ ] **CHK002 — Every UX state per screen specified (loading, empty, success, error, partial-data)**

  ⚠️ **GAPS:**

  Per U2 (per-Managed detail):
  - ✅ "В процессе" (FR-015 spinner).
  - ✅ "Отправлено на сервер" (FR-015 ack indicator).
  - ✅ "Применено бабушкой" (SC-001b indicator).
  - ✅ "Нет сети" expanded (FR-015).
  - ⚠️ **Error state** (link revoked / permission denied): F2 from failure-recovery — что показать? «Связь разорвана»? **Gap** — обсудить в plan.md.
  - ⚠️ **Partial apply state** (FR-033 partialApplyReasons): admin видит «применено» или «частично применено»? Иконка отличается? **Gap** — обсудить.

  Per U3 (Managed Settings):
  - ✅ Editing с continuous autosave (FR-056).
  - ✅ Pending banner (FR-047).
  - ⚠️ **Saved indicator** — пользователь видит, что autosave прошёл? Маленький checkmark? Toast? «Сохранено локально, не отправлено»? **Gap** for plan.md.

  Per U4 (Merge UI):
  - ✅ Element-by-element diff (FR-051).
  - ✅ Auto-resolve case (FR-052 — no UI shown).
  - ✅ Auto-merge case (FR-053 — shown с suggestion).
  - ⚠️ **Cancel state** (FR-055): user closes merge UI — где user видит «есть unresolved conflict»? Просто same pending banner? **Gap**.

  **Action для plan.md**: state diagrams для U2, U3, U4 с явными states.

- [ ] **CHK003 — Navigation transitions between screens specified**

  ⚠️ **GAP.** Spec.md does not specify navigation entry/exit:
  - Как user попадает в Settings на Managed? «7 тапов + пароль» mentioned в US-3, but full flow inherited from spec 003 — **assumed**.
  - Как user попадает в Settings на admin (для editing remote Managed config)? Через U1 → U2 → ?
  - Как закрыть merge UI? Back button = FR-055 cancel? Save button = FR-054 apply? Both?

  **Action для plan.md**: navigation graph (Decompose / RootComponent structure расширение).

- [ ] **CHK004 — Cross-cutting overlays (snackbar, toast, dialog, bottom sheet)**

  ⚠️ **GAPS.**

  Spec.md упоминает «баннер», «значок», «спиннер», но не описывает overlay type:
  - "Нет сети, идёт загрузка" — toast / snackbar / persistent in spinner area? **Plan.md decision**.
  - Discard pending — dialog "уверены?" или мгновенный action? **Plan.md decision**.
  - Successful push ack — silent (just spinner disappears) or toast? Spec.md FR-015 implies silent indicator change. ✅ OK.

  **Action для plan.md**: явный overlay-type table.

## Clarity — terminology and rules

- [x] **CHK005 — UX terms defined unambiguously**

  Spec.md consistently uses:
  - «editor» — устройство, имеющее право писать `/config`.
  - «Managed» — телефон пожилого пользователя.
  - «admin» — родственник-управляющий.
  - «push на сервер» — write to Firestore.
  - «save локально» — write to Room (после Q clarify + FR-056 autosave: continuous, не button).
  - «pending» — saved локально, но не push'ed.
  - «applied» — Managed применил и обновил /state.
  - «conflict» — server `updatedAt > snapshot`.

  All terms consistently used.

- [x] **CHK006 — Vague qualifiers operationalised**

  Spec.md избегает «intuitive» / «smooth» / «fast».
  - «Мгновенно» (FR-015 spinner appearance) — operationalised through SC-001 «спиннер появляется при нажатии».
  - «Visible» (FR-046 visual marker) — operationalised through «icon next to device in list».

  ✅ No unmeasurable adjectives.

- [ ] **CHK007 — Action vocabulary explicit: tap vs long-press vs swipe**

  ⚠️ **GAP**: spec.md uses «нажимает» / «click» loosely. Most actions implied tap.
  - "Push на сервер" — tap кнопка. ✅
  - "Save локально" — теперь continuous autosave (FR-056), no explicit action.
  - "Cancel merge UI" — tap back / tap cancel button? **Plan.md specify**.
  - "Discard pending" (FR-047) — tap menu? Tap option in banner? **Plan.md**.

  **Action для plan.md**: action vocabulary table.

- [ ] **CHK008 — Button labels are exact strings (or token IDs), not "Confirm-style label"**

  ⚠️ **GAP.** Spec.md does NOT specify exact strings. References:
  - "save локально" / "push на сервер" — concept names, not button labels.
  - «Применено бабушкой» / «отправлено на сервер» (FR-015 indicators) — text **shown to user**? **Yes, looks like literal strings.**
  - «нет сети, идёт загрузка» (FR-015) — literal string.
  - «есть локальные изменения, не запушено на сервер» (FR-047) — literal string.
  - «pending push» (FR-046) — внутренний term, not user-facing string.

  **Action для plan.md**: extract all user-facing strings into string-resources file (e.g., `res/values/strings_config_sync.xml`). Russian primary; localization-ready.

  **Watch**: ru wording. «Применено бабушкой» — could be "применено на этом устройстве" (more neutral, не assumes user is grandma). **Recommendation**: «применено» plain or «применено на телефоне» — more neutral. **Plan.md / spec 009 decision**.

## Consistency

- [x] **CHK009 — In-Scope and Functional Requirements align**

  ✅ Каждый US имеет FRs:
  - US-1 → FR-010..015 (push channel) + FR-020..023 (server→Managed apply).
  - US-2 → FR-050..055 (merge UI) + FR-014.
  - US-3 → FR-010 (Managed-as-writer privileges) + FR-011 (Security Rules) + FR-056 (autosave).
  - US-4 → FR-042..047 (pending), FR-056 (autosave).
  - US-5 → FR-041, FR-044 (Room bootstrap), SC-004a.
  - US-6 → FR-003, FR-004 (flows/slots/contacts schema).

  No orphan FRs.

- [ ] **CHK010 — Confirmation policy consistent**

  ⚠️ **GAP.** Spec.md не явно перечисляет actions requiring confirmation:
  - **Push** — no confirmation (just spinner). ✅ OK (low-stakes, reversible by reverting in editor).
  - **Discard pending** (FR-047) — **should** require confirmation (irreversible loss of work). **Action для plan.md**.
  - **Cancel merge UI** (FR-055) — pending preserved, low-stakes. No confirmation needed. ✅
  - **Revoke link** (inherited from 007) — already has confirmation per 007.

  **Action для plan.md / spec.md possibly**: explicit «Discard pending requires confirmation» as FR or UX-tasks note.

- [x] **CHK011 — Multi-tap / accidental-double-tap protection consistent across action surfaces**

  Spec.md не специфицирует. **Inherits** project's existing button debouncing (если есть). **Watch для plan.md**: push button particularly — accidental double-tap could trigger two pushes (idempotent via optimistic concurrency, но weird UX). **Action для plan.md**: debounce push button ~500ms.

## Acceptance — measurability

- [x] **CHK012 — Each US has explicit Given/When/Then or numbered acceptance scenario**

  ✅ Verified during requirements-quality checklist (CHK010 там): 17 scenarios across 6 US, all G/W/T.

- [x] **CHK013 — Success criteria measurable per UX moment**

  ✅ Per state-management-checklist findings: SC-001 «push tap → spinner» (immediate); SC-001b «applied → second indicator»; SC-004a «cold start → first frame ≤ 650 ms»; SC-008 «pending → visible marker (100% editors)».

- [ ] **CHK014 — Returning-user UX (second-launch, resume from background) defined or excluded**

  ⚠️ **GAP.** Spec.md covers cold-start (US-5, SC-004a/b) и process-death recovery, но не **explicit** про:
  - **Returning after long absence** (app backgrounded для дней): что user видит? Same applied-config из Room? Pending banner если есть? ✅ Implicitly yes per FR-044 / FR-046, но не **acceptance scenario**.

  **Watch для plan.md**: confirm — нет «welcome back» screen или подобного. Just normal launcher experience. ✅ Probably OK as implicit.

## Coverage — alternative paths

- [x] **CHK015 — Every primary action has its negative-path UX defined**

  ✅ Failure-recovery checklist covered this exhaustively. F1-F14 mapped.

  Watch (carried from failure-recovery): F2 revoked-link UI, F4 size exceeded UI, F12 merge cancel UI — все требуют plan.md elaboration.

- [x] **CHK016 — Multiple entry points yield consistent UX**

  Spec 008 entry points:
  - Cold start (US-5).
  - FCM push received → silent apply (no notification).
  - NetworkCallback → silent apply.
  - User opens app from launcher.

  All ведут к **same UI** (last-applied-config rendered from Room). FCM doesn't cause notification; doesn't launch app. ✅ Consistent.

- [x] **CHK017 — Long-pause scenarios (user leaves app for hours) have defined return-UX**

  ✅ Covered by FR-044 (read Room on launch) + FR-046 (pending visible) + SC-004a (fast first frame).

## Non-functional UX

- [x] **CHK018 — Accessibility deferred to `checklist-accessibility` if relevant**

  N/A в этом checklist — accessibility skipped в assessment (no a11y signals). However, **elderly-friendly checklist** runs next, который покроет related concerns.

- [x] **CHK019 — Localisation deferred to `checklist-localization` if relevant**

  Localization checklist not triggered (no i18n signals в spec.md). Spec.md тексты на русском.

  ⚠️ **Watch**: ru is hardcoded в spec.md, no English equivalents. **Action для plan.md / spec 010 setup-assistant**: confirm RU is primary supported locale, EN can be added later. Strings extracted to `strings.xml` to allow translation.

- [x] **CHK020 — Diagnostic UX (how user sees that something is being tracked)**

  Spec 008 doesn't add user-visible diagnostic UX (logs are internal). FR-015 spinner / SC-001b applied indicator — these aren't diagnostics, they're operational feedback. ✅ OK.

## Dependencies / assumptions

- [x] **CHK021 — UX doesn't depend on out-of-scope capabilities**

  ✅ 008 UX scope minimal (per OUT-001 — full editor UI is spec 009). Merge UI is the major new UI in 008; rest is indicators/banners over inherited spec 003 / 007 surfaces.

- [x] **CHK022 — Mock-data limitations noted explicitly if they affect rendering**

  ✅ FR-045 explicitly removes legacy mock-storage at first launch. Spec.md Assumptions block notes that admin UI editor is minimal in 008 (full в 009).

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 12 | CHK005, CHK006, CHK009, CHK011, CHK012, CHK013, CHK015, CHK016, CHK017, CHK018, CHK019, CHK020, CHK021, CHK022 |
| ⚠️ Watch / Plan.md action | 7 | CHK001 (discard dialog, auto-merge UI form), CHK002 (UX states: error / partial / saved-acknowledgment), CHK003 (navigation graph), CHK004 (overlay types), CHK007 (action vocabulary), CHK008 (string resources + ru wording), CHK010 (discard confirmation), CHK014 (returning-user implicit) |
| ❌ Fail | 0 | — |

**Verdict: PASS at spec-level with significant UX-design action items для plan.md.**

Spec.md captures **functional** UX behavior well (autosave, spinner, applied indicator, pending banner, merge UI). Gaps are mostly в **screen-level design** (exact overlay types, button labels, navigation transitions, confirmation dialogs) — это естественная граница spec.md / plan.md. Plan.md / design-doc должен заполнить.

---

## Mandatory action items для plan.md

1. **Screen flow diagram** (CHK001, CHK003): navigation graph List → Detail → Settings → Edit → Push → (conflict?) → Merge → Apply.

2. **UX state diagrams** (CHK002): per U2 (detail) / U3 (Settings) / U4 (Merge) — все states explicitly (idle, in-progress, success, partial, error).

3. **Overlay type table** (CHK004): toast / snackbar / dialog / inline — для каждого user feedback element.

4. **Action vocabulary** (CHK007): tap / long-press / back — explicit per action.

5. **String resources** (CHK008): extract all user-facing strings to `res/values/strings_config_sync.xml`; review «применено бабушкой» wording (recommend neutral «применено» or «применено на телефоне»).

6. **Confirmation policy** (CHK010): explicit list of actions requiring "уверены?" dialog. Recommended:
   - Discard pending changes — YES (irreversible).
   - Push на сервер — NO (reversible, low-stakes).
   - Cancel merge UI — NO (pending preserved per FR-055).

7. **Button debounce** (CHK011): push button debounce ~500ms to prevent accidental double-tap.

8. **Returning-user explicit AC** (CHK014, optional): add acceptance scenario «app backgrounded for days, user opens — sees last-applied config + pending badge if applicable». Probably implicit, but acceptance test recommended.

## Recommended add to spec.md (optional)

Spec.md could add one FR clarifying discard-pending confirmation:

> **FR-057 (Discard confirmation)**: Действие «отменить локальные изменения» (FR-047) MUST требовать confirmation dialog «Удалить несохранённые изменения для этого телефона? Это действие нельзя отменить.» — потому что pending changes могут представлять часы работы пользователя.

**Optional**, но добавит явность. **Хотите — добавлю?**

**No spec.md edits required by default; FR-057 optional based on your call.**

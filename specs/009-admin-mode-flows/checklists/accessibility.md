# Checklist: accessibility

**Spec**: `spec.md` (rev. 2026-05-15, post-batch 1+2)
**Run**: 2026-05-15 — accessibility skill invoked by `/checklist-accessibility`.
**Standards**: WCAG 2.2 AA, Android Accessibility, Material Design 3, Article VIII (`/.specify/memory/constitution.md`).

Verifies the spec defines accessibility behaviour at the requirements level. A separate `elderly-friendly` checklist covers senior-user constraints (Article VIII §7); this one is the generic a11y baseline (TalkBack, contrast, focus, keyboard, text-scaling).

---

## Context: who uses 009 UI surfaces?

| Surface | Primary user | A11y baseline |
|---|---|---|
| Managed device list (FR-001) — list + 4 health indicators per row | Admin | Standard WCAG 2.2 AA |
| Layout editor — view + edit mode (FR-005..012) | Admin (US-1) **or** Managed editor via 7-tap+password (US-6) | Standard WCAG 2.2 AA; senior-safe **only if Managed editor** |
| Per-Managed health summary (FR-017..022) | Admin | Standard |
| Contacts picker rationale + manual entry (FR-023a/b, FR-033c) | Admin | Standard, **but rationale text is long → text-scaling critical** |
| VCard share intent receiver (FR-027..031) | Admin | Standard |
| History screen (FR-039..043) | Admin or Managed editor | Standard |
| Drag-and-drop (FR-008) | Admin or Managed editor | **HIGH-RISK** — TalkBack DnD support is incomplete on Android |
| Parallel "···" menu (FR-009) | Admin or Managed editor — explicitly the **a11y channel** | Standard |
| Severity icons in health (FR-022) | Admin | **HIGH-RISK** — emoji 🔴🟢 + colour-only severity coding |

**Key observation**: spec 009 introduces **two architectural a11y measures already**:
1. **FR-009** parallel "···" menu — explicit accessibility fallback to drag-and-drop (called out in spec text).
2. **FR-023a** manual contact entry fallback when `READ_CONTACTS` denied — sidesteps both permission gating *and* picker accessibility issues.

These are PASS-level for a11y intent. What is missing is **explicit spec-level commitments** on `contentDescription`, focus order, contrast, text scaling, and TalkBack walkthrough — none of those words appear in the spec.

---

## Tap targets / interactive areas

- [x] **CHK001 — Every interactive element has tap area ≥ 48dp (Android baseline); project default ≥ 56dp per spec 003 senior-safe override**
  - Spec mentions tap-target rule **once** — Q-OPEN-3 resolution: «senior-safe tap target ≥ 56 dp (Article VIII override наследуется из спека 4)». Точные dp размеры deferred to plan.md.
  - **For admin-facing surfaces** (US-1 device list, editor, history screen, VCard receiver) — Article VIII §7 documented-constraint pattern from spec 008 applies: admin is **not** the senior persona, so Material baseline 48dp is sufficient. Senior-safe 56dp override applies on Managed-rendered surfaces (which 009 reuses from spec 003 unchanged per A-9 / FR-005).
  - **Watch для plan.md**: severity-icon hit area in device list (FR-001 «4 компактных индикатора») — компактность is at odds with 48dp. Either icons are display-only (not interactive) — then no tap-target rule — or each indicator opens a detail tooltip and needs full 48dp.
  - **Watch для plan.md**: "···" menu button per tile (FR-009) — must be ≥ 48dp despite needing to coexist with the tile inside grid spacing.
  - **Verdict**: PASS with explicit plan-level action — declare which indicators are interactive and document tap-target sizing for "···" buttons and severity rows.

- [x] **CHK002 — Tap area ≥ visible bounds when needed (custom hit area documented)**
  - N/A at spec level. Plan-level concern.

## Visual contrast

- [ ] **CHK003 — Text contrast ≥ 4.5:1 for normal text (WCAG 2.2 AA)**
  - Spec does not mention contrast at all. Inherits project palette from spec 003 (which has senior-safe contrast for main launcher).
  - **New UI surfaces in 009** (history screen, VCard receiver, contact-picker rationale, manual-entry form, device-list health row) — none have explicit contrast acceptance criterion.
  - **Action для plan.md**: contrast audit at design phase for all new screens. List screens: HistoryScreen (FR-039), VCardImportScreen (FR-029), ContactPickerRationaleScreen (FR-023a/b, FR-033c), ManualContactEntryScreen (FR-023a.1), AddedContactsSettingsScreen (FR-033a), HealthSummaryHeader (US-2 Scenario 2), DeviceListItem (FR-001).

- [ ] **CHK004 — Large text (≥ 18sp regular / ≥ 14sp bold) contrast ≥ 3:1**
  - Same as CHK003. Not specified.

- [ ] **CHK005 — Non-text UI (icons, focus rings, important borders) contrast ≥ 3:1 (WCAG 2.2 AA non-text)**
  - **Critical for severity icons (FR-022)**: красный 🔴 for Critical, желтый for Warning, зелёный 🟢 for Info — must each have ≥ 3:1 contrast against their background (white card / dark card).
  - Spec uses emoji 🔴 🟢 in acceptance criteria — emoji rendering varies per Android version, locale, OEM. **Cannot guarantee 3:1 contrast** with system-rendered emoji.
  - **Refuse-and-propose pattern (CLAUDE.md "refuse and propose if you see")**: severity coding via emoji is **wire-fragile** and **a11y-fragile**. Propose: use **vector icons** from `androidx.compose.material.icons` (`Icons.Filled.Warning`, `Icons.Filled.CheckCircle`, `Icons.Filled.Error`) with explicit `tint = SeverityColors.critical/warning/info` from theme. Theme defines colours with verified contrast. Emoji in spec text is **descriptive**, not prescriptive.
  - **Action для plan.md**: explicit FR or design note — severity indicators rendered as Material icons with theme-defined tint, NOT system emoji.

- [ ] **CHK006 — Theme overrides (dark, light, high-contrast) explicitly handled**
  - Not mentioned in spec. Inherits project theme handling.
  - **Watch**: severity colour palette must be defined for both light and dark themes (red on dark vs red on light has different contrast).
  - **Action для plan.md**: include dark-theme variant in severity colour table.

## Screen reader (TalkBack)

- [ ] **CHK007 — Every interactive element has `contentDescription` (or `Modifier.semantics { contentDescription = ... }`)**
  - **Spec contains zero mentions of `contentDescription`, `semantics`, or TalkBack.**
  - Critical surfaces requiring contentDescription:
    - Device-list health indicators (battery icon, wifi icon, audio-muted icon, lastSeen) — each must announce «Заряд: 3%, критический» not just «icon».
    - Severity icons in editor health header (FR-022 expanded view) — same.
    - Drag handles for tile drag-and-drop (FR-008) — must announce «Плитка Маша, удерживайте для перемещения».
    - "···" button per tile (FR-009) — must announce «Действия с плиткой Маша».
    - Tile in edit mode (tap → open edit form per FR-006) — must announce «Плитка Маша, тап чтобы редактировать», not run as a Call action.
    - History snapshot list items (FR-039) — must announce date + author device.
    - VCard import dropdown «выберите Managed» (FR-029) — must announce per-item.
  - **Action для plan.md**: explicit `contentDescription` table in design — every interactive element + every informative icon must have one. Without this the device list (FR-001 compact health icons) is unusable on TalkBack.

- [ ] **CHK008 — Decorative-only images marked `null` description (don't read)**
  - FR-003 «декоративная рамка телефона» — marked decorative; must have `contentDescription = null`.
  - Edge-icon decoration in `BottomFlowBar`, header art — same.
  - **Action для plan.md**: design note — decorative elements explicitly listed.

- [ ] **CHK009 — Custom controls have `Role` semantics (button, checkbox, toggleable) defined**
  - Drag-and-drop area (FR-008) is a custom control — needs `Role.DropTarget` or similar (Compose doesn't have built-in role for drop; needs `customActions` semantic instead). Crucial because TalkBack will not present drag-and-drop as draggable by default.
  - "···" menu button (FR-009) — standard `Role.Button` from `IconButton`. PASS by default.
  - Severity badge — `Role.Image` with description acting as text. OR — better — bundle severity into `stateDescription` of the parent row so TalkBack announces «Бабушка Honor, заряд 3% критический, без интернета, звонок выключен, 2 минуты назад» as one block.
  - **Action для plan.md**: each custom semantic block (DeviceListItem with 4 indicators, tile in edit mode, drag handle) needs explicit semantics declaration.

- [ ] **CHK010 — Reading order matches visual order; explicit `traversalIndex` only when default fails**
  - DeviceListItem with 4 indicators: visual order is (battery, wifi, audio, lastSeen) horizontally + (displayName) above. TalkBack default traversal — depends on Compose semantics merge. **If indicators are siblings of displayName**, TalkBack reads them separately, which is wrong UX for senior admin (4 swipes per device).
  - **Action для plan.md**: device-list-item должен использовать `Modifier.semantics(mergeDescendants = true)` + composed `contentDescription` («Бабушка Honor, заряд 3 процента критический, без интернета, звонок выключен, две минуты назад»). Это сворачивает 5 swipes в 1.

- [ ] **CHK011 — TalkBack path to primary action ≤ 3 swipes per screen (per ADR-005 senior-safe)**
  - **Device list**: primary action = «открыть редактор для Managed N». Если каждая строка merged via CHK010 — 1 swipe per device + activate. PASS если CHK010 fixed.
  - **Editor in edit mode**: primary action = «опубликовать». От первой плитки до «Опубликовать» button — depends on number of tiles. Plan-level concern; cannot satisfy ≤ 3 swipes if 12 tiles between. **Watch**: «Опубликовать» button must be positionally / semantically reachable via "next heading" or pinned bottom bar with FAB semantics.
  - **Action для plan.md**: ensure «Опубликовать» button is independently reachable (skip-to-action via heading or FAB).

- [ ] **CHK012 — State changes announced (`stateDescription`, `LiveRegion`)**
  - **Banner «есть несохранённые изменения»** (US-1 Scenario 2) — must use `LiveRegion.Polite` so TalkBack announces «есть несохранённые изменения» when it appears, without admin needing to swipe back.
  - **«Сохранено локально»** indicator (FR-014b) — `stateDescription` on the save button so its toggling between «Сохранено локально» / «Сохраняется...» / «Изменено» announces correctly.
  - **Health Critical transition** (US-2 Scenario 3) — when severity flips from Info to Critical, TalkBack should announce. `LiveRegion.Assertive` on the affected row.
  - **Push success** (US-1 Scenario 3) — toast/snackbar (CHK019) + announce «Применено на устройстве».
  - **Action для plan.md**: explicit list of LiveRegion / stateDescription bindings.

## Text scaling / dynamic type

- [ ] **CHK013 — Text uses `sp` (not `dp`); fontScale 200% supported without truncation/clipping**
  - Spec does not mention font scaling. Inherits commonMain typography from spec 003.
  - **High-risk surface**: FR-033c privacy disclosure rationale — long Russian text «Контакты, которые вы добавите, сохраняются в облаке Firebase и видны на устройстве вашего родственника. Вы можете удалить их в любой момент через Settings → Добавленные контактов». At 200% fontScale this is ~3 lines that may push the «Согласен» button off-screen if layout is fixed-height.
  - **High-risk surface**: device-list item with 4 indicators + lastSeen text — at 200% scale, «2 минуты назад» wraps, and the row height grows, possibly overlapping next item if `Modifier.height(fixed)` is used.
  - **Action для plan.md**: explicit test — every new screen tested at 200% fontScale + at 100%; no text clipping; no fixed-height containers around scalable text.

- [ ] **CHK014 — Layouts adapt to font scale — no fixed-height text containers that clip**
  - Same as CHK013. **Specific watch**: tile in editor mode — tile size is determined by grid layout, but the label inside must wrap or truncate gracefully at 200% scale (NOT clip mid-character).

- [x] **CHK015 — No text shrinking ("autoSize" to maintain layout) without user opt-in**
  - Spec does not propose autoSize. PASS by default.

## Focus

- [ ] **CHK016 — Keyboard / D-pad / external-keyboard navigation works on every screen (TV remote, accessibility switch)**
  - Not mentioned. Not a primary persona use case (admin on phone, not TV). But Switch Access users are explicitly covered by Article VIII §7's spirit.
  - **Watch**: drag-and-drop (FR-008) is **inaccessible via Switch Access by default** — same root cause as TalkBack DnD issue. FR-009 "···" menu mitigates this too.
  - **Verdict**: PASS via FR-009 mitigation. Action for plan.md — document that FR-009 explicitly serves Switch Access + TalkBack + keyboard navigation, not just TalkBack alone.

- [ ] **CHK017 — Focus trapped where appropriate (modal dialogs)**
  - Discard confirmation, rollback confirmation (US-5 Scenario 3), permission rationale (FR-023a), VCard preview screen (FR-029) — all dialog/modal-like surfaces.
  - **Action для plan.md**: confirm Compose `Dialog` is used (which traps focus by default), not bottom-sheet without focus trap.

- [ ] **CHK018 — Focus indicator visible (3:1 contrast)**
  - Not specified. Inherits Material 3 default focus indicators.
  - **Action для plan.md**: verify focus indicator contrast in light/dark themes for editor tile (custom drawn) and severity row.

## Motion / time

- [x] **CHK019 — Auto-dismissing UI (toast, snackbar) lasts ≥ 5s OR user-controllable per WCAG 2.2 timing**
  - Spec implies toast/snackbar for push success/failure but does not specify duration.
  - **Action для plan.md**: snackbars for «Опубликовано» / «Сохранено» / errors use `SnackbarDuration.Long` (10s) or explicit dismiss action.

- [x] **CHK020 — Animations honour `Settings.Global.ANIMATOR_DURATION_SCALE` (reduce-motion)**
  - Drag-and-drop animation (FR-008) — Compose `Modifier.dragAndDropSource/Target` honours system animator scale by default. PASS by inheritance.
  - **Watch**: any **custom** spring animations in editor (preset switching, flow rearrange) must respect `LocalDensity.current.fontScale` and `Settings.Global.ANIMATOR_DURATION_SCALE`.

- [x] **CHK021 — No content flashes > 3 times/second (WCAG 2.3 — seizure safety)**
  - Severity transition (FR-021 Critical event) — must NOT use flashing/blinking animation. Recommendation: single smooth colour fade ≤ 1 Hz.
  - **Action для plan.md**: if a future plan iteration adds "Critical attention-grabbing" animation, cap at 1 Hz.
  - PASS by default (no flashing UI proposed).

## Errors / forms

- [ ] **CHK022 — Form errors associated programmatically with the input (`labelFor`, semantics)**
  - **Manual contact entry form** (FR-023a.1) — `displayName` + `phoneNumber` inputs with validation per `Contact.fromRaw()`. Errors («Имя слишком длинное», «Неверный формат телефона») must be associated with the offending field via `Modifier.semantics { error(...) }` (Compose 1.5+).
  - **Action для plan.md**: explicit error-binding per field in ManualContactEntryScreen.

- [ ] **CHK023 — Required fields announced as required by TalkBack**
  - Manual contact entry — both fields are required. Must use `Modifier.semantics { contentDescription = "Имя контакта, обязательное поле" }` or Compose 1.6+ `error` slot.
  - **Action для plan.md**: include in design.

## Test plan

- [ ] **CHK024 — At least one screen tested with Android Accessibility Scanner; failures listed**
  - Spec does not propose a test plan for a11y validation.
  - **Action для plan.md**: tasks.md must include `T-AX01: Run Android Accessibility Scanner on DeviceListScreen, EditorScreen edit-mode, HistoryScreen, VCardImportScreen, ManualContactEntryScreen, AddedContactsSettingsScreen, PermissionRationaleScreen. Document and fix all severity-Critical / severity-High findings.`

- [ ] **CHK025 — At least one TalkBack walkthrough planned per primary US**
  - **Action для plan.md**: tasks.md must include TalkBack walkthroughs per P1 user story:
    - `T-AX02`: TalkBack walkthrough US-1 — admin edits a tile on bababy phone end-to-end, including drag-and-drop **via FR-009 fallback** (since DnD itself isn't TalkBack-friendly).
    - `T-AX03`: TalkBack walkthrough US-2 — admin scans device list, hears all 4 health indicators per device, opens detail.

---

## Refuse-and-propose findings (from CLAUDE.md "refuse and propose if you see")

1. **Severity coding via emoji 🔴🟢 in acceptance criteria** — emoji rendering is platform/locale-fragile and a11y-fragile (TalkBack reads emoji inconsistently; contrast not guaranteed; some Android versions render colour-flat).
   **Proposed correction**: spec text retains emoji as descriptive shorthand, but adds an FR (suggested **FR-022a**) requiring severity indicators to be rendered as **Material vector icons with theme-defined tint**, not raw emoji, with explicit `contentDescription` per severity.

2. **No spec-level a11y commitments at all** — spec mentions «accessibility per Article VIII» once (FR-009) but never lists the operative requirements (contentDescription, focus order, text-scaling support, TalkBack reachable primary actions).
   **Proposed correction**: add an «Accessibility» FR section listing minimum commitments — every interactive element has `contentDescription`; every screen tested at fontScale 200%; state changes use `LiveRegion`; drag-and-drop alternative via FR-009 is **defined as the official a11y channel** (not «just a fallback»).

3. **FR-009 wording «параллельный канал для drag-and-drop (accessibility per Article VIII; users TalkBack, на планшете без точного указания)»** is too vague.
   **Proposed correction**: tighten — «FR-009 MUST cover 100% of operations available via drag-and-drop (move within flow, move cross-flow, delete) — already captured as SC-007. Add: FR-009 menu items MUST be reachable in ≤ 3 swipes from any tile under TalkBack».

---

## Verdict summary

| Status | Count | Items |
|---|---|---|
| ✅ PASS | 5 | CHK001, CHK002 (N/A), CHK015, CHK019, CHK020, CHK021 |
| ⚠️ WATCH / Action для plan.md | 13 | CHK003, CHK004, CHK006, CHK008, CHK009, CHK010, CHK011, CHK012, CHK013, CHK014, CHK016, CHK017, CHK018, CHK022, CHK023, CHK024, CHK025 |
| ❌ FAIL / spec change needed | 2 | CHK005 (emoji severity), CHK007 (zero contentDescription mentions) |

**Hard FAILs (require spec-level change before plan.md):**

1. **CHK005 — severity icons must be Material vector icons with theme-defined tint, not emoji**. Spec change: add **FR-022a** «Severity indicators MUST be rendered as Material `Icons.Filled.*` with `tint` from `SeverityColors` theme palette, NOT system emoji; each indicator MUST have `contentDescription` сформирован как `"<label>: <value>, <severity_word>"` (e.g. «Заряд: 3 процента, критический»)».
2. **CHK007 — spec is silent on contentDescription / TalkBack**. Spec change: add **FR-A11Y-001** (новая Accessibility секция) — «App MUST provide `contentDescription` on all interactive elements and informative icons across spec 009 surfaces (device list, editor edit-mode, history screen, VCard import, contact picker rationale, manual entry, added-contacts settings). App MUST merge device-list-item semantics so TalkBack reads `<displayName>, <4 indicators composed>` as a single block. App MUST emit state-change announcements via `LiveRegion.Polite` for «есть несохранённые изменения», «Сохранено локально», «Применено», and `LiveRegion.Assertive` for Critical health transitions».

**Soft FAILs (plan.md actions, not spec change):**

- Tap-target sizing for "···" buttons, severity rows (CHK001 detail).
- Contrast audit across new screens (CHK003-006).
- TalkBack walkthrough tasks per P1 user story (CHK024-025).
- 200% fontScale layout test on FR-033c rationale + device-list rows (CHK013-014).

---

## TL;DR (на русском)

Спек 9 имеет **два хороших архитектурных решения для accessibility**, но **молчит про базовые требования**.

**Хорошее (PASS):**
- **FR-009** — параллельная кнопка «···» рядом с каждой плиткой. Это **официальный** accessibility-канал для drag-and-drop (которое в Android **плохо** работает через TalkBack/Switch Access). SC-007 требует 100% покрытие операций.
- **FR-023a** — manual contact entry as fallback when `READ_CONTACTS` denied. Сторонним эффектом обходит accessibility-проблемы системного picker'а.
- Drag-and-drop через `Modifier.dragAndDropSource` (FR-008) — Compose API honours system animation scale (CHK020 PASS).
- Никаких flashing-анимаций (CHK021 PASS).

**Два HARD FAIL — требуют изменения спека ДО `/speckit.plan`:**

1. **🔴🟢 emoji в severity-индикаторах (FR-022, US-2 acceptance criteria)** — emoji **плохо озвучивается TalkBack**, рендерится неконсистентно по версиям/локалям, не гарантирует 3:1 контраст. **Предлагается** добавить **FR-022a**: индикаторы рендерятся как Material vector иконки (`Icons.Filled.Warning` и т.д.) с theme-defined tint + `contentDescription` вроде «Заряд: 3 процента, критический».

2. **В спеке 0 упоминаний `contentDescription` / TalkBack / semantics**. **Предлагается** добавить **FR-A11Y-001**: explicit accessibility commitments — contentDescription на всех interactive elements; merged semantics для device-list-item (1 swipe TalkBack вместо 5); LiveRegion announcements для banner «есть несохранённые», «Сохранено локально», severity transitions.

**Top 3 accessibility-риска:**

1. **Severity emoji** — без замены на vector icons + contentDescription, TalkBack-пользователь не узнает, какой уровень severity. Для целевого use case (узнать, что у бабушки 3% батарея) — это блокирующий риск.
2. **Drag-and-drop** — Compose built-in DnD API не имеет TalkBack-friendly fallback **внутри себя**; FR-009 покрывает, но это надо **явно зафиксировать** как official a11y channel (не «just fallback»). SC-007 требует 100% покрытия.
3. **Device list TalkBack traversal** — без `Modifier.semantics(mergeDescendants = true)` + composed contentDescription, admin с TalkBack будет swipe'ить 5 раз per device (имя + 4 индикатора). Для 10 paired Managed это 50 swipes только чтобы пройти список.

**Нужно ли менять спек?** Да — 2 hard FAIL fix'а:
- Добавить **FR-022a** (vector icons + contentDescription для severity).
- Добавить **FR-A11Y-001** (Accessibility секция со списком минимальных обязательств: contentDescription, merged semantics, LiveRegion).

Остальные 13 ⚠️ WATCH — это **plan.md действия** (contrast audit, fontScale 200% test, Accessibility Scanner запуск, TalkBack walkthrough tasks в tasks.md). Они не требуют изменений в spec.md.

**Counts:** ✅ 6 | ⚠️ 13 | ❌ 2

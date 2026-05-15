# Checklist: elderly-friendly

**Spec**: `spec.md` (rev. 2026-05-15, post batch 1+2 правок)
**Run**: 2026-05-15 — после `/speckit.clarify` + batch 1/2 checklist resolution.

Verifies Article VIII §7 — primary persona — elderly user with low vision, reduced dexterity, cognitive load sensitivity. **Stricter than generic WCAG** — project-specific senior-safe rules per `.specify/memory/constitution.md` Article VIII §7 + `docs/product/senior-safe-launcher-plan.md`.

Reference example: [`specs/008-bidirectional-config-sync/checklists/elderly-friendly.md`](../../008-bidirectional-config-sync/checklists/elderly-friendly.md).

---

## Context: who uses 009 UI surfaces?

| Surface | Primary user | Senior-safe rules apply? |
|---|---|---|
| Admin app — Managed list (FR-001 + health индикаторы FR-017..022) | **Admin** (взрослый родственник, обычно внук) | Standard — admin is not the elderly persona |
| Admin app — Layout editor (US-1, FR-005..016) | **Admin** primary; **Managed editor** через US-6 = тот же экран | **Mixed** — shared code path |
| Admin app — Health detail сверху редактора (FR-017..022) | Admin | Standard |
| Admin app — Contact picker / VCard import (US-3/4, FR-023..033c) | Admin | Standard |
| Admin app — History screen + rollback (US-5, FR-036..043) | **Admin** primary; **Managed editor** через FR-042 = same screen | **Mixed** — see CHK006 |
| Managed device — Settings entry (7-tap + password) → Layout editor (US-6) | **Managed (elderly)** | **STRICT senior-safe** — это где бабушка может попасть в сложный flow |
| Managed device — applied launcher rendering (наследуется из спека 003/008) | Managed (elderly) | **Strict** — но не new в 009 |

**Key observation для спека 9**: спек **расширяет** US-6 (Managed editor через Settings) — бабушка может попасть в **тот же** editor, что admin, через 7-tap+пароль. Это значит:

1. **FR-005a edit-mode rendering** запускается на Managed-устройстве (низкая dexterity, тремор, low vision).
2. **FR-008 drag-and-drop** — может быть критически сложным для elderly с Parkinson's tremor.
3. **FR-039 History screen** — multi-step rollback flow (list → preview → откатить → confirm) на бабушкином телефоне.
4. **FR-017..022 health индикаторы** с цветовой severity (🔴🟢) — бабушка их **не видит** на своём экране (только admin видит), значит CHK018 here applies только к admin side. ✅

**Article VIII §7 wording**: «If a design is elegant for experts but confusing for elderly users, the elderly-friendly design wins by default **unless a documented product constraint says otherwise**».

Спек 8 FR-050 («unified merge UI, не senior-safe-вариант, justified by 7-tap+password gate») создал **прецедент** для editor surfaces. Спек 9 **наследует** этот прецедент через US-6 (FR-042 откат доступен Managed editor'у) и **расширяет** его на drag-and-drop + history rollback.

**Этот checklist проверяет — насколько прецедент 008 FR-050 distinguishes valid в новых, более сложных surfaces 009.**

---

## Visual

- [x] **CHK001 — Body text ≥ 18sp**
  - **Admin-only surfaces** (Managed list, health indicators, contact picker, VCard flow): standard 14-16sp acceptable. Admin is not the elderly persona.
  - **Editor & History на Managed (US-6 / FR-042)**: per 008 FR-050 precedent, unified UI doesn't need senior-safe override. **Documented constraint**: 7-tap+password gate filters cognitively capable users. ✅
  - **Verdict**: PASS — inherits 008 precedent + admin-facing surfaces don't need override.
  - **Action для plan.md**: explicit note в design doc — «Editor UI (shared admin + Managed via US-6) uses Material baseline sizing, justified by 7-tap+password gate per 008 FR-050 precedent extended to spec 009».

- [x] **CHK002 — Primary action labels ≥ 16sp**
  - Same as CHK001 — Material baseline acceptable on shared editor; PASS with documentation.
  - **Watch**: «+ контакт» button, «Опубликовать» button — visible primary actions, just ensure they're labelled clearly (no icon-only). Spec doesn't specify; plan.md must.

- [ ] **CHK003 — Tap targets ≥ 56dp**
  - **Q-OPEN-3 → DEFERRED to plan.md**: спека явно говорит «Article VIII senior-safe tap target ≥ 56 dp constraint наследуется», точные dp в plan.md design table.
  - **Watch**: FR-009 параллельная кнопка «···» — это **критичный accessibility primary** для тремора (см. CHK008). Должна быть ≥ 56 dp **в обоих** режимах (admin & Managed editor). Это уже **больше**, чем editor может «уступить» через 008 FR-050 precedent — FR-009 само есть mitigation для tremor.
  - **Watch**: история — items в HistoryScreen (FR-039) — каждый snapshot tap-able, ≥ 56 dp inter-item spacing required (FR-040 preview, FR-041 rollback). Tremor + small list items = mis-tap откат к **wrong** version → catastrophic для бабушки.
  - **Action для plan.md**: explicit dp table — `FR-009 «···» button ≥ 56 dp`, `FR-039 history list items ≥ 56 dp height`, `inter-item spacing ≥ 16 dp`.

- [x] **CHK004 — Spacing between interactive elements ≥ 16dp**
  - Editor — drag-and-drop targets (FR-008) need clear inter-tile spacing to prevent drag-to-wrong-target with tremor. Spec doesn't specify; **inherits** from спек 003 tile layout.
  - **Action для plan.md**: confirm tile inter-spacing ≥ 16 dp; drag drop targets visually distinct (highlight on hover/drag).

- [x] **CHK005 — Contrast ≥ 4.5:1 universally**
  - **Health severity colors** (🔴 Critical, 🟡 Warning, 🟢 OK — FR-017/018): admin-only surface, standard contrast OK.
  - **Editor selection / drag state visual feedback**: standard Material 3 — PASS by inheritance.
  - **Action для plan.md**: contrast audit для severity icons; ensure red/yellow/green choices meet WCAG AA contrast (especially red on light bg).

## Cognitive load

- [ ] **CHK006 — Each screen has ONE primary action; secondary actions visually subdued**

  **Critical analysis of US-6 + FR-042 («Managed editor через 7-tap+пароль = тот же редактор»):**

  Спек 9 расширяет 008 FR-050 precedent на **более сложные** surfaces:
  - Layout editor с drag-and-drop (FR-008).
  - History screen с rollback (FR-039..043).
  - Forms редактирования плитки (call/sms/openapp).
  - Contact picker + VCard import flows.

  **Вопрос**: 7-tap+password gate **достаточен** ли как «documented constraint» для **drag-and-drop + history rollback**?

  **Analysis:**
  - 7-tap+password filters out **accidental** entry. Confirms «conscious editing».
  - НО **conscious editing** ≠ **cognitively capable of drag-and-drop with Parkinson's tremor**.
  - Бабушка может **намеренно** войти в редактор (видеоинструкция от внука), но физически не справиться с drag.
  - **Mitigation спека 9**: FR-009 параллельная кнопка «···» — explicit accessibility channel. Это **обязательный** parallel path для tremor users. ✅
  - **Mitigation спека 9**: SC-007 — «параллельная кнопка позволяет выполнить **все** операции через drag-and-drop — 100% покрытие». ✅

  **Verdict**: extension of 008 FR-050 precedent to 009 editor **valid ONLY IF FR-009 fully covers drag-and-drop functionality**. SC-007 codifies this requirement.

  **Per-screen primary action audit**:
  - **Managed list (admin)**: primary = tap-to-edit одного Managed. Health indicators — informational, не actions. ✅
  - **Layout editor**: primary = «Опубликовать». Secondary = «Сохранить локально» (по факту просто indicator per FR-014b). «···» menu — tertiary. ✅
  - **History screen (FR-039)**: primary = tap snapshot → preview. ✅ Multi-step but linear.
  - **History preview (FR-040)**: primary = «Откатить к этой версии» (внизу). Read-only viewing — secondary. ✅
  - **Contact picker rationale (FR-023)**: primary = «Разрешить» / «Запретить». «Ввести вручную» (FR-023a) — secondary. ✅
  - **Merge UI (наследуется из 008 FR-050)**: covered by 008 precedent.

  **⚠️ Watch для plan.md**:
  - **History rollback confirmation dialog** (FR-041 «диалог подтверждения»): для бабушки через US-6 это **катастрофически важный** confirmation — откат теряет правки админа. Wording must be positive («Вернуть раскладку к версии от 3 дней назад?»), not threatening («Вы потеряете текущие изменения!»).
  - **Edge case в спеке 9**: «admin удалил все flow → бабушка видит пустой экран» — UI confirmation «вы удалили все вкладки, бабушка увидит пустой экран; продолжить?» уже есть. Это в admin UI, бабушка не видит. ✅

- [x] **CHK007 — Onboarding / wizards ≤ 3 steps OR explicit progress indicator**
  - 009 не вводит onboarding. Multi-step flows:
    - **VCard share flow** (US-4): «share → выбрать Managed → подтвердить → push» — 3 steps. ✅
    - **History rollback** (US-5): «open editor → история → выбор → preview → откатить → подтверждение» — 5 steps for admin. Для **Managed через US-6** этот flow **тоже доступен** (FR-042). 5 steps + tremor + cognitive load = **risk**.
  - **Action для plan.md**: progress indicator или explicit «step N of M» visible в History rollback flow. Especially на Managed side per US-6.

- [ ] **CHK008 — No hidden gestures (swipe-from-edge, long-press menus) for primary flows**

  **CRITICAL ANALYSIS**:
  - **FR-008**: «Long-press на плитку MUST активировать drag-and-drop» — this **IS a hidden gesture** for primary flow (editing layout).
  - **Mitigation FR-009**: «параллельная кнопка «···» с меню «Изменить / Переместить в / Удалить»» — explicit non-gestural channel.
  - **SC-007 codifies**: «параллельная кнопка позволяет выполнить все операции, доступные через drag-and-drop — 100% покрытие».

  **Verdict**: PASS **because FR-009 + SC-007 mitigate**. The gesture exists, but it's **optional** — every operation has a tap-based alternative.

  **⚠️ HOWEVER**:
  - **Tremor reality check**: Parkinson's tremor (4-6 Hz) often **triggers long-press accidentally** when user is trying to do simple tap. Бабушка через US-6 может **unintentionally** запустить drag-and-drop just by holding finger 500ms.
  - **Action для plan.md**: configure long-press threshold to be **higher** than Material default (500 ms) в edit mode — рекомендация **750-1000 ms** для US-6 Managed editor (or higher on detected tremor patterns; OS-level a11y setting if exists). Дополнительно: **escape hatch** — если accidental drag, tapping outside any drop target должно cancel drag без эффекта.

  **Watch для plan.md**: drag-and-drop UX has clear visual «I am now in drag mode» indicator (per Material 3 standard). Cancel = lift finger over non-target area.

- [x] **CHK009 — Plain-language copy: no jargon, no negation in confirmations**

  **Audit of user-facing strings в spec.md FR text:**

  Спека **в основном** уже использует plain-language (наследует CHK009 fix from спека 008). Но есть несколько потенциально проблемных мест:

  - FR-023 rationale: «Зачем нужно разрешение читать контакты» — **plain**. ✅
  - FR-023a баннер: «Вы также можете добавлять контакты, делясь ими из WhatsApp / Telegram…» — **plain**. ✅
  - FR-023b: «Открыть настройки приложения» — **plain**. ✅
  - FR-029: «Добавить контакт <displayName>/<phoneNumber> в раскладку» — **plain**. ✅
  - FR-031: «Контакт без номера телефона не может быть добавлен в текущей версии; для мессенджеров LINE / WeChat / KakaoTalk — поддержка появится позже» — **plain**, even informative. ✅
  - FR-033c: «Контакты, которые вы добавите, сохраняются в облаке Firebase и видны на устройстве вашего родственника. Вы можете удалить их в любой момент через Settings → Добавленные контакты» — **«облако Firebase»** — semi-jargon. Бабушка через US-6 может это увидеть.
    - **Recommendation**: «сохраняются в интернете и видны на устройстве вашего родственника». «Облако Firebase» — internal term.
  - FR-043: «слишком новая версия» / «откат невозможен» — **plain**. ✅
  - Edge case «бабушка увидит пустой экран»: this is **admin-side** confirmation copy. Per спек 008 CHK009 fix, **persona-specific terms** должны быть neutral.
    - **Recommendation**: «На управляемом телефоне будет пустой экран — продолжить?»
  - FR-039: «История» — plain. ✅
  - FR-041 «диалог подтверждения» — spec не специфицирует точный текст. **Action для plan.md**: wording per 008 precedent: «Вернуть раскладку к этой версии? Текущие изменения будут заменены.» (informative, не threatening). NOT «Это действие нельзя отменить!».

  **⚠️ Persona-specific terms found in spec body (FR/edge cases/SC)**:
  - «бабушка» (множественно — edge cases, FR-039 description, SC-002 wording, US-7 description) — это **spec narrative**, не UI copy. ✅ Acceptable per 008 CHK009 fix («internal terminology»). НО **plan.md UI copy** должен использовать neutral term: «управляемый телефон», «пользователь Managed-телефона».
  - **Watch для plan.md**: ensure no «бабушка» / «внук» / «дедушка» strings leak в actual UI copy.

  **Verdict**: PASS at spec-level — internal terminology acceptable. **Action для plan.md**: review FR-033c wording («облако Firebase»), FR-041 confirmation copy, edge case admin confirmations.

- [x] **CHK010 — Default values pre-filled where possible**
  - **FR-029**: «<выпадающий список Managed>. Preselect — если у админа один Managed» — ✅ smart default.
  - **FR-030**: «переход в её редактор раскладки с предзаполненной формой новой плитки» — ✅ pre-filled.
  - **FR-034 OpenApp packageName**: «(а) выбор из списка приложений, установленных на админ-устройстве; (б) ручной ввод» — picker primary, manual fallback. ✅
  - **Verdict**: PASS.

## Predictable navigation

- [x] **CHK011 — Core actions have consistent placement across screens**
  - Spec не специфицирует placement. **Inherits** project conventions.
  - **Watch для plan.md**: «Опубликовать» button — **same position** на editor / history rollback preview. «Отмена» — same. «···» menu icon — same.

- [x] **CHK012 — Back behaviour matches user expectation**
  - US-6: бабушка вошла через 7-tap+password → Settings → editor. Back → Settings → main launcher. ✅ Expected.
  - **FR-027a singleTask + onNewIntent**: VCard intent возвращается **в существующий** editor instance, preserving backstack. ✅ Per state-management checklist.
  - History screen → back → editor. ✅
  - History preview → back → history list. ✅

- [x] **CHK013 — No surprise re-routing**
  - Spec не описывает action remapping. ✅
  - **Watch**: drag-and-drop drop targets must be **visible** before user drops (highlight). Without preview, user might «discover» что drop в неожиданном target = unexpected action.

## Error recovery

- [x] **CHK014 — Every error has a clear recovery action**

  ✅ Covered through 009 FR additions:
  - **FR-023a**: READ_CONTACTS denied → «Ввести вручную» + VCard alternative banner.
  - **FR-023b**: permanently denied → «Открыть настройки приложения».
  - **FR-028**: VCard parse failed → user-friendly message.
  - **FR-031**: VCard без TEL → explanatory message + future-support note.
  - **FR-043**: schema mismatch на rollback → blocked with explanation, snapshot остаётся видимым.
  - **Edge cases**: офлайн без кэша — «попробуйте позже»; офлайн с кэшем — «применятся при появлении сети».

  **Verdict**: PASS — failure-recovery checklist coverage robust.

  **⚠️ Watch для Managed editor через US-6**:
  - Если бабушка **случайно** удалила все плитки в редакторе (FR-009 «···» меню → «Удалить» × many) и нажала «Опубликовать» — её **собственный** телефон станет пустым. Edge case в спеке покрывает admin scenario, **но US-6 — это та же бабушка её **сама** делает это**. Rollback через FR-042 — её exit. ✅ Architecturally covered.
  - **Action для plan.md**: ensure rollback discoverable to Managed editor user — maybe FR-039 entry point более prominent в Settings.

- [x] **CHK015 — No error states that require app restart to leave**
  - ✅ All error paths user-recoverable (per failure-recovery checklist).
  - Schema-incompatible snapshot — blocks rollback, не блокирует app.

- [ ] **CHK016 — Destructive actions have confirmation; confirmation copy is positive, not threatening**

  - **FR-041 rollback confirmation**: spec не специфицирует точный текст.
    - **Action для plan.md**: «Вернуть раскладку к этой версии? Текущие изменения будут заменены.» (informative). Avoid threatening tone.
  - **Edge case «admin удалил все flow»**: spec специфицирует UI «вы удалили все вкладки, бабушка увидит пустой экран; продолжить?» — это admin-side confirmation. **Watch**: this is **also** triggered if **Managed editor через US-6** deletes all flows. Wording должен быть neutral («управляемый телефон» / «пользователь этого телефона»).
  - **FR-033a contact deletion**: spec не специфицирует confirmation. **Action для plan.md**: confirmation dialog «Удалить контакт <displayName>?» — neutral, informative.
  - **«+ контакт» → системный picker**: picker уже handles cancellation natively. ✅
  - **Drag-to-trash deletion** (FR-008 drop targets include «корзина внизу экрана (удаление)»):
    - **Action для plan.md**: drag-to-trash should trigger confirmation **before** deletion (especially if tile has contact attached — losing a key contact is high-impact). Hover-over-trash visual + release = confirmation dialog.

  **Verdict**: WATCH — needs plan.md wording specification + drag-delete confirmation.

## Sensory

- [x] **CHK017 — Animation optional / reduced-motion-aware (Article VIII §5)**
  - **FR-008 drag-and-drop animation**: Material 3 default behavior. Should respect system reduce-motion setting.
  - **Severity color transitions** (FR-020 listener updates): instant, no animation. ✅
  - **History screen transitions**: standard navigation, system-controlled.
  - **Action для plan.md**: explicit «drag-and-drop respects reduce-motion (no spring-back animation, instant snap)».

- [ ] **CHK018 — No reliance on colour alone**

  **🔴🟢 Severity indicators (FR-018, FR-020):**
  - Spec wording: «🔴 Заряд: 3% (критично)», «🔴 Звонок: выключен!», «🟢 Wi-Fi» — uses **emoji + text label**. Not color-only. ✅
  - BUT: emoji rendering varies across OEMs (Samsung vs Pixel vs Huawei). Some seniors with color-blindness might miss the visual distinction.
  - **Action для plan.md**: severity uses **icon shape + color + text**:
    - 🔴 Critical → red filled circle / triangle warning icon + label «Критично»
    - 🟡 Warning → yellow filled circle / exclamation icon + label «Внимание»
    - 🟢 OK → green checkmark / circle + label «ОК» (or no badge if normal)
  - **This is admin-only surface**, so bar is lower than US-6 surfaces — but elderly admin (some 60+) might be the actual user. Senior-safe ICON + COLOR + TEXT triad still recommended.

  **Drag-and-drop visual feedback (FR-008):**
  - Drop target highlighting — must not be color-only (e.g., green border) — also bold border / icon overlay.

  **Verdict**: WATCH — current spec is good (emoji + text), but plan.md must codify icon-shape-distinct indicators.

## Time

- [x] **CHK019 — No timed challenges**
  - 009 не вводит timed challenges. ✅
  - **FR-020** realtime listener — passive, no user action required. ✅
  - **Edge case «Push history race condition»** — handled async, no user-visible timer.

- [x] **CHK020 — Sessions / authenticated state generously timed**
  - Inherits спека 7 session model. **N/A для 009**.

## Acceptance evidence

- [ ] **CHK021 — Each US for primary action has acceptance criterion citing senior-safe metrics**

  - US-1/2 admin primary — admin не elderly persona, senior-safe metrics **не required**.
  - **US-6 Managed editor** — **the elderly entry point**. Acceptance criteria FR-042 / US-6 testing scenarios don't cite senior-safe metrics explicitly.
    - **Verdict for US-6**: PASS by **documented exception** (008 FR-050 precedent extended via FR-042), CONDITIONAL on:
      - FR-009 parallel button covers ALL drag operations (SC-007 — already codified). ✅
      - History rollback confirmation wording positive (action for plan.md).
      - Drag-and-drop has accessibility a11y label per element (action for plan.md).
  - **Action для plan.md**: explicit note in `quickstart.md` / `plan.md` confirming senior-safe criteria coverage for US-6 surfaces — same pattern as спек 008.

- [ ] **CHK022 — Test plan includes manual walkthrough by someone simulating elderly use**

  - Spec не специфицирует tasks. **Action для tasks.md**: explicit task `T-Manual-Elderly-Walkthrough-US-6` covering:
    - Cold-start to 7-tap+password → editor entry — flow understandable «слепо» (без чтения мелких подписей).
    - Drag-and-drop с simulated tremor (artificial hand shake during drop) — verify FR-009 «···» path works as alternative.
    - History rollback flow (5 steps) — comprehensible without explanation.
    - Confirmation dialogs — wording feels safe, not threatening, with reading glasses + bus environment.

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 14 | CHK001, CHK002, CHK004, CHK005, CHK007, CHK008 (with mitigation), CHK009 (mostly), CHK010, CHK011, CHK012, CHK013, CHK014, CHK015, CHK017, CHK019, CHK020 |
| ⚠️ Watch / Plan.md action | 8 | CHK003 (dp table), CHK006 (rollback confirm wording, drag-delete confirm), CHK009 (FR-033c «облако Firebase», edge case wording), CHK016 (confirmation copy, drag-trash confirm), CHK018 (severity icon-shape), CHK021/022 (test plan tasks), long-press threshold (CHK008 tremor mitigation) |
| ❌ Fail | 0 | All extensions of 008 FR-050 precedent valid given existing mitigations (FR-009, SC-007, FR-042) |

---

## Top 3 senior-safe risks (особенно для US-6 Managed editor)

### Risk #1 — Drag-and-drop с тремором → accidental drop в неправильный target

**Surface**: FR-008 long-press drag-and-drop, в US-6 Managed editor.
**Why critical**: Parkinson's tremor (4-6 Hz) часто triggers long-press accidentally + makes precise drop-target selection difficult.
**Mitigation (already in spec)**: FR-009 параллельная кнопка «···», SC-007 100% coverage. ✅
**Additional ask для plan.md**:
- Long-press threshold ≥ 750 ms (vs Material default 500 ms) в edit mode.
- Drop target highlight **before** release (preview).
- Cancel = lift over non-target.
- Drag-to-trash → confirmation dialog (especially for tiles with contact).

### Risk #2 — History rollback на бабушкином телефоне → откат к **wrong** version

**Surface**: FR-039..043, US-6 Managed editor via FR-042.
**Why critical**: 5-step flow (open editor → история → выбор → preview → откатить → confirm) + small history items with timestamps + cognitive load = mis-tap risk. Откат теряет recent admin changes без recovery.
**Mitigation (already in spec)**: read-only preview before commit (FR-040), confirmation dialog (FR-041), conflict-check via 008 merge UI (FR-041).
**Additional ask для plan.md**:
- History items ≥ 56 dp height, ≥ 16 dp inter-item spacing.
- Confirmation wording positive: «Вернуть раскладку к этой версии? Текущие изменения будут заменены.» NOT «Это действие нельзя отменить!».
- Display `recordedFromDeviceId` in human-readable form («с моего планшета», «с этого телефона»).
- Step indicator («Шаг 2 из 3») в multi-step rollback flow.

### Risk #3 — Confirmation copy threatening / paternalistic в Managed-facing UI

**Surface**: All confirmation dialogs that **могут** trigger на Managed-устройстве через US-6 — edge case «удалил все flow», FR-041 rollback, FR-033a contact deletion, drag-trash confirm.
**Why critical**: спека 008 CHK009 fix установила «no «бабушка», no «нельзя отменить»» pattern. Спек 9 наследует, но spec body всё ещё содержит «бабушка» в narrative + не специфицирует точные UI strings для new confirmations.
**Mitigation**: spec body narrative «бабушка» = internal terminology (acceptable per 008 precedent).
**Action для plan.md**:
- FR-033c «облако Firebase» → «интернет / в сети».
- All new confirmations wording table — neutral («управляемый телефон» вместо «бабушка»), informative not threatening.
- Edge case admin confirmations («вы удалили все вкладки, бабушка увидит пустой экран») → «На управляемом телефоне будет пустой экран — продолжить?».

---

## Spec clarifications needed? — NO

Спек 9 **architecturally sound** для senior-safe — все potential issues addressable в plan.md (UI copy, dp sizing, threshold tuning, test plan) без правок самого spec.md.

**Ключевые architectural mitigations уже в спеке:**
- FR-009 параллельная кнопка → tremor-friendly alternative для drag (mandatory).
- SC-007 → 100% drag coverage через menu (codified).
- FR-040 read-only preview → mis-rollback prevention.
- FR-042 symmetric rollback → Managed editor имеет escape hatch.
- FR-023a/b → READ_CONTACTS denial не тупик.
- FR-029/030 → smart pre-filled defaults в VCard flow.

**Verdict: PASS — extension of 008 FR-050 precedent to 009 valid given existing mitigations. All findings actionable в plan.md / tasks.md, не требуют spec.md revision.**

---

## Mandatory action items для plan.md

1. **Sizing exception documentation** (CHK001/002): explicit note «Editor UI (shared admin + Managed через US-6) uses Material baseline sizing, justified by 7-tap+password gate per 008 FR-050 precedent extended via FR-042».
2. **DP sizing table** (CHK003): `FR-009 «···» button ≥ 56 dp`, `FR-039 history list items ≥ 56 dp height`, `inter-tile spacing ≥ 16 dp`.
3. **Long-press threshold** (CHK008): edit-mode long-press = 750-1000 ms (tremor-friendly), не Material default 500 ms.
4. **Severity icon shapes** (CHK018): shape-distinct (circle/triangle/check), не color-only.
5. **Confirmation wording table** (CHK016): all new confirmations — positive/informative tone; replace «облако Firebase» (FR-033c) → «в сети»; edge case admin copy neutral terms.
6. **Drag-to-trash confirmation** (CHK016): hover-over-trash → confirmation dialog before delete (especially tiles with contacts).
7. **Reduce-motion compliance** (CHK017): drag-and-drop respects system reduce-motion setting.
8. **History flow step indicator** (CHK007): «Шаг N из M» visible during multi-step rollback.

## Mandatory action items для tasks.md

1. **`T-Manual-Elderly-Walkthrough-US-6`**: cold-start → 7-tap+password → editor → drag (with simulated tremor) → fallback to «···» → history → rollback → confirm. Verify each step comprehensible без explanation, reading glasses + bus environment.
2. **`T-A11y-Drag-Drop-Coverage`**: verify SC-007 (100% drag operations have «···» menu equivalent).

---

## Что внутри (TL;DR на русском)

**Спек 9 проверен на elderly-friendly правила.** Verdict: **PASS** — расширение прецедента 008 FR-050 (unified editor UI за 7-tap+пароль) на новые сложные surfaces (drag-and-drop, history rollback) **валидно**, потому что спек содержит обязательные mitigations:

- FR-009 параллельная кнопка «···» — tremor-friendly альтернатива drag-and-drop.
- SC-007 — 100% drag операций доступны через menu (codified).
- FR-040 read-only preview истории — защита от mis-rollback.
- FR-042 откат доступен Managed editor'у — escape hatch для бабушки.
- FR-023a/b — отказ READ_CONTACTS не тупик (ручной ввод + VCard share как альтернатива).

**14 PASS / 8 Watch / 0 Fail.**

**Топ-3 senior-safe риска (для US-6 — когда бабушка через 7 тапов + пароль попадает в редактор)**:

1. **Drag-and-drop с тремором** → случайный drop в неправильный target. Mitigation в спеке: FR-009 «···» кнопка. Plan.md action: long-press threshold 750-1000 ms, drop target preview, drag-to-trash confirmation.
2. **History rollback на бабушкином телефоне** → откат к **wrong** версии теряет admin's recent changes. Mitigation в спеке: read-only preview, confirmation. Plan.md action: items ≥ 56 dp, neutral confirmation wording, step indicator, human-readable device labels.
3. **Confirmation copy threatening / paternalistic**. Спек 8 CHK009 fix установил «no «бабушка», no «нельзя отменить»» pattern; спек 9 наследует, но `FR-033c «облако Firebase»` + edge case admin confirmations нуждаются в neutral wording. Plan.md action: wording table.

**Уточнений спека не требуется** — все findings actionable в plan.md/tasks.md. Архитектурно спек содержит правильные mitigations.

**8 Mandatory plan.md actions + 2 Mandatory tasks.md actions** перечислены выше.

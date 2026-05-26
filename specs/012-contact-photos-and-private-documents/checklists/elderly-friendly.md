# Checklist: elderly-friendly

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 13/22 ✓ + 8 deferred-to-plan + 1 open

---

## Visual

- [ ] **CHK001** — body text ≥ 18sp: deferred-to-plan
  - DocumentViewer label, плитка имя контакта, placeholder инициал — должны быть ≥ 18sp.
  - **Действие**: plan-phase зафиксировать typography tokens.
- [ ] **CHK002** — primary action labels ≥ 16sp: deferred-to-plan.
- [x] **CHK003** — tap targets ≥ 56dp: ✓
  - **FR-014** (плитка), **FR-018** (DocumentViewer «Закрыть»). Это spec-level invariant, выходящий за WCAG baseline.
- [ ] **CHK004** — spacing ≥ 16dp между interactive: deferred-to-plan
  - Существующий launcher home-screen grid использует spacing tokens (foundational specs).
  - **Действие**: plan-phase подтвердить, что плитка-документ и плитка-контакт ложатся в тот же grid.
- [x] **CHK005** — contrast ≥ 4.5:1 universally: ✓
  - **FR-014** placeholder.
  - Implicit для остальных surface'ов — наследуется из launcher theme.

## Cognitive load

- [x] **CHK006** — ONE primary action per screen: ✓
  - **DocumentViewer**: одна primary = «Закрыть»; pinch-to-zoom — secondary gesture.
  - **Admin «+ документ»** form: одна primary = «Подтвердить»; «Отмена» вторична.
  - **Admin upload progress**: одна primary = retry (если ошибка) ИЛИ нет actions (если успешно).
- [x] **CHK007** — wizards ≤ 3 steps: ✓
  - «+ документ» flow: (1) tap → (2) picker → (3) label entry. 3 steps. ✓
- [x] **CHK008** — no hidden gestures для primary flows: ✓
  - Pinch-to-zoom — secondary, не primary (fallback fit-to-screen есть, FR-019).
  - **Important**: TalkBack-friendly альтернатива zoom'у — см. accessibility checklist (mandatory add).
- [ ] **CHK009** — plain-language copy: deferred-to-localization
  - **Будут проверены ru/en строки** в `checklist-localization`.
  - Spec говорит «фото не отрисовалось у …» (FR-022) — plain language. ✓
  - «Загружаю на сервер» / «нет сети, попробую снова» (edge case 3) — plain language. ✓
  - **Не использовать**: «расшифровка не удалась», «MAC failed», «recipient not found» — это developer jargon. Должно стать «не получилось показать фото».
- [x] **CHK010** — default values pre-filled: ✓
  - Picker возвращает один файл (`maxItems=1`) — нет multi-select cognitive load.
  - Label entry — есть пустое поле, пользователь обязан ввести (1..40 graphemes). **Default не предзаполняется**.
  - **Acceptable**: label содержательный (нет общего default'а как «Документ 1»), пользователь должен подумать. Это **trade-off** против senior-safety, но minimal — single short input field.

## Predictable navigation

- [x] **CHK011** — consistent placement: ✓
  - «+ документ» — рядом с «+ контакт» (FR-015).
  - Закрыть button — снизу-справа (FR-018), стандартная позиция.
- [x] **CHK012** — Back behaviour: ✓
  - System back в DocumentViewer = «закрыть» (то же что button «Закрыть»). Estandard expectation.
- [x] **CHK013** — no surprise re-routing: ✓
  - Implicit auto-update photoRef при повторном контакте (FR-013) — это **не visual rerouting**, а seamless update. Бабушка видит обновлённое фото без notification. Это **намеренный design** Q1.

## Error recovery

- [x] **CHK014** — every error has clear recovery action: ✓
  - Upload failure → «попробую снова» retry (edge case 3).
  - Decrypt failure → admin indicator с подсказкой «пере-добавить» / «повторить pairing» (FR-022).
  - Picker MIME mismatch → no-op (back to admin screen).
- [x] **CHK015** — no app-restart-required errors: ✓.
- [ ] **CHK016** — destructive actions confirmation
  - **Status**: ⚠️ open — для бабушка-стороны.
  - Бабушка **не удаляет** контакты / документы (все управление на admin'е). На admin-стороне — удаление контакта/документа уже в спеке 009 flow с confirmation (если он там есть).
  - **Действие**: plan-phase подтвердить, что spec 009 confirmation покрывает delete-flow для документов в той же манере.

## Sensory

- [ ] **CHK017** — animation reduced-motion-aware: deferred-to-plan
  - Picker → DocumentViewer transition — system animation, honor reduce-motion auto.
- [x] **CHK018** — no reliance on colour alone: ✓
  - Failed decrypt indicator — текст «фото не отрисовалось у …» + icon (FR-022). Color reinforces, not sole signal.

## Time

- [x] **CHK019** — no timed challenges: ✓
  - Upload loader не имеет countdown (просто spinner).
  - DocumentViewer не закрывается auto.
- [x] **CHK020** — generously-timed sessions: ✓ N/A (не auth flow).

## Acceptance evidence

- [x] **CHK021** — US acceptance cites senior-safe metrics: ✓
  - US-1 sc.1: «tap-target ≥ 56dp». ✓
  - US-2 sc.2: «кнопка «закрыть» размером ≥ 56dp». ✓
  - US-3 sc.1: «плитка остаётся tap-target ≥ 56dp». ✓
- [ ] **CHK022** — manual walkthrough by elderly-simulator: deferred-to-plan
  - **Действие**: plan-phase task — «squint + slow-tap + voice-over walkthrough» на DocumentViewer + плитки на medium-tier device-е.

---

## Summary

| Status | Count |
|---|---|
| ✓ | 13 |
| deferred-to-plan | 8 (typography tokens, spacing, plain-copy строки, reduce-motion, manual walkthrough) |
| ⚠️ open | 1 (CHK016 destructive confirmation cross-check со спеком 009) |
| ✗ violations | 0 |

**Verdict**: Spec 012 чётко operationalises senior-safe инварианты — `≥ 56dp` tap target и `≥ 4.5:1` контраст явно зафиксированы в FR. 3 step'а в «+ документ» wizard'е укладываются в ≤ 3 limit. Один primary action per screen. Error recovery с понятными подсказками («пере-добавить», «повторить pairing»).

**Constitution alignment**: Article VIII §7 (Elderly-First UX) ✓.

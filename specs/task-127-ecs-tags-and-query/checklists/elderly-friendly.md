# Checklist: elderly-friendly

Applied: 2026-07-15
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

Note: Spec introduces no new user-facing UI surface. Elderly-friendly concerns pass through to existing screens (HomeActivity per TASK-52+, WizardHostActivity per TASK-126). This spec only fixes a regression (HomeScreen shows tiles instead of Error UI) + localizes wizard strings.

## Visual

- [x] CHK001-005 N/A — no new visual surfaces. Existing tile grid + wizard visuals inherited.

## Cognitive load

- [x] CHK006 One primary action inherited from existing wizard flow.
- [x] CHK007 Wizard step count inherited from TASK-126 (not modified here).
- [x] CHK008 N/A — no new gestures.
- [x] CHK009 FR-008 mandates readable Russian strings — closes "raw `wizard_confirm` key visible" gap, direct elderly-friendly win.
- [x] CHK010 N/A — no new input fields.

## Predictable navigation

- [x] CHK011 US1 acceptance scenario 3 — Profile edit through Settings → new emit without Activity restart. Consistency preserved.
- [x] CHK012 N/A — no back-nav change.
- [x] CHK013 N/A.

## Error recovery

- [ ] CHK014 PARTIAL — cross-ref checklist-failure-recovery CHK014: corrupt Profile recovery path not explicitly specified. Recommend: on migration failure → user-facing "restart wizard" recovery action.
- [x] CHK015 Empty Profile → Ready empty screen (not stuck). filterNotNull → Loading (not stuck error).
- [x] CHK016 N/A — no destructive action.

## Sensory

- [x] CHK017 N/A — no new animation.
- [x] CHK018 N/A — no colour-only signal.

## Time

- [x] CHK019 N/A.
- [x] CHK020 N/A.

## Acceptance evidence

- [x] CHK021 SC-002 verifies wizard string readability (senior-safe by removing raw keys).
- [x] CHK022 SC-001 physical device verification on Xiaomi Redmi Note 11 — manual walkthrough included.

**Result**: 21/22 passed, 1 open (CHK014 — recovery path for corrupt Profile, cross-ref failure-recovery checklist).

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Проверили UX для elderly / cognitive-load-sensitive / low-vision users. Спека не добавляет НОВЫХ user-facing surface'ов — только чиним Home регрессию + локализуем wizard строки. 21/22 pass, почти всё N/A. Единственная прямая elderly-win — CHK009 (FR-008 mandates readable Russian strings — закрывает «raw `wizard_confirm` key visible» gap).

**Конкретика, которую стоит запомнить:**
- CHK009 win — Russian wizard strings вместо raw ключей — прямой elderly-friendly gain.
- CHK015 pass — empty Profile → Ready empty screen (не stuck), null Profile → Loading (не stuck error).
- CHK014 open — cross-ref failure-recovery CHK014: corrupt Profile recovery не специфицирован (в spec.md уже помечено как Out-of-Scope).

**На что смотреть с осторожностью:**
- Тот же corrupt Profile риск что в failure-recovery — если production feedback покажет проблему, elderly users попадут в crash без прямой recovery path («нажми чтобы перезапустить wizard»).

---
id: TASK-128
title: VERIFY-xiaomi bucket
status: Paused
assignee: []
created_date: '2026-07-13'
updated_date: '2026-07-13 06:36'
labels:
  - phase-2
  - verification
  - manual-gate
  - xiaomi
milestone: m-1
dependencies:
  - TASK-126
  - TASK-127
references:
  - verification-evidence/task-128-xiaomi-fresh-01.png
  - verification-evidence/task-128-xiaomi-fresh-02.png
  - verification-evidence/task-128-xiaomi-fresh-03.png
  - verification-evidence/task-128-xiaomi-fresh-04.png
  - verification-evidence/task-128-xiaomi-fresh-05.png
  - verification-evidence/task-128-xiaomi-fresh-06.png
  - verification-evidence/task-128-xiaomi-fresh-07.png
  - verification-evidence/task-128-xiaomi-blocker-logcat.txt
priority: high
ordinal: 128000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Bucket #1 физической verification на Xiaomi Redmi Note 11 (adb id `17f33878`, MIUI) после TASK-126 (Wizard runtime migration to Preset composition foundation) Phase 2. Два manual smoke-теста:

1. Fresh install → wizard → HomeActivity показывает preset content (не Error).
2. Изменение FontSize в Settings → Provider.apply срабатывает → close/open app → значение persist.

## Bucket items

- **Item #1** — [FAILED → TASK-127]. Fresh install + wizard (Лаунчер для пожилого → Настроить с нуля → Позже → 4 confirm) → HomeActivity показывает Error UI «Не удалось загрузить настройки». Regression TASK-52. Evidence: `task-128-xiaomi-fresh-07.png`.
- **Item #2** — [ ] BLOCKED by TASK-127. Cannot reach Settings без работающего Home; verification отложен.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [FAILED → TASK-127] #1 [hand] Fresh install + wizard on Xiaomi Redmi Note 11 → HomeActivity shows preset content (not "Не удалось загрузить настройки"). Verified 2026-07-13 on adb id `17f33878`, branch `task-126-wizard-runtime-migration` @ commit f8a4d8b: Error UI reproduced. Evidence: `verification-evidence/task-128-xiaomi-fresh-07.png` + `task-128-xiaomi-blocker-logcat.txt`.
- [ ] #2 [hand] Change FontSize in Settings → Provider.apply fires → close/open app → value persists. BLOCKED by TASK-127 (cannot reach Settings without working Home).
<!-- AC:END -->

<!-- SECTION:VERIFICATION_PENDING:BEGIN -->
## Verification Pending

Both items pending resolution of TASK-127 (HomeActivity config-load failure post-wizard, TASK-126 regression). Once TASK-127 fix lands, re-run bucket on same Xiaomi device (`17f33878`) using same repro path.

**Side finding recorded in TASK-127**: new manifest-driven wizard runtime displays raw string keys (`wizard_step_of`, `wizard_component_font_size`, `wizard_component_sos`, `wizard_confirm`) — localization not wired.
<!-- SECTION:VERIFICATION_PENDING:END -->

## Definition of Done

Both AC `[x]` after TASK-127 resolution + re-verification on Xiaomi Redmi Note 11 (adb id `17f33878`).

<!-- SECTION:PAUSE_REASON:BEGIN -->
## Pause reason (2026-07-13)

Bucket pause = следствие паузы TASK-127. Оба items заблокированы одной первопричиной (HomeActivity config-load failure). Bucket остаётся `Paused` пока TASK-127 не возобновится и не закроется.

**Rotation правило (verify-bucket-rotate skill)**: Monday-отсечка не применяется к Paused bucket'ам — только к активным Draft/In Progress. Bucket оживёт когда TASK-127 → Done + prior re-run.

<!-- SECTION:PAUSE_REASON:END -->

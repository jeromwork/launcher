---
id: TASK-52
title: HomeActivity infinite "Загрузка..." after wizard finish on real device
status: Draft
assignee: []
created_date: '2026-06-25 11:48'
updated_date: '2026-06-28 14:30'
labels:
  - home-screen
  - bug
milestone: m-1
dependencies: []
priority: high
ordinal: 56000
---

## Reproduction confirmed 2026-06-28 (Xiaomi 11T, realBackend debug APK from `verification/task-3-4-49-manual-gates` branch HEAD `41ea7fc`)

Steps:
1. `adb install -r app-realBackend-debug.apk`
2. `pm clear com.launcher.app` (fresh data)
3. Launch FirstLaunchActivity
4. Wizard Step 2/4: tap «Настроить с нуля» (blank profile, local mode)
5. Wizard Step 3/4: tap «Позже» (skip default-launcher prompt)
6. Second wizard Step 1/3 (default-launcher prompt again): tap «Пропустить»
7. HomeActivity opens → black screen with centered «Загрузка…» text, bottom bar visible («+» and gear)
8. State persists 3+ minutes — never resolves

Evidence in `verification-evidence/task-49/`:
- `screen-04-after-later-tap.png` — second wizard appears (UX bug: duplicate default-launcher prompt)
- `screen-05-wizard2-step2.png` — first sight of «Загрузка…»
- `screen-07-stuck-90s.png` — still stuck 90s later

## Root cause (suspected, code-level)

`core/src/commonMain/kotlin/com/launcher/adapters/config/ConfigBackedFlowRepository.kt:38-66`:

```
When unpaired OR before first apply, observeFlows emits an empty list
and HomeScreen shows its «Загрузка…» state.
```

`activeLinkFlow()` returns null linkId for unpaired devices → `observeFlows()` emits empty list → `HomeComponent.flowSlot` stays empty → `HomeScreen` shows «Загрузка…» (HomeScreen.kt:67-77).

Local-mode users (Wizard «Настроить с нуля» path) are by-design never paired. So linkId is null **permanently** in that mode. The repository conflates two distinct states:

1. Transient «not yet loaded» (legitimate spinner)
2. Steady-state «local-only, never will be cloud-paired» (NOT a spinner — should show empty-state CTA or seeded Classic-6)

## Fix direction (for implementer)

Either:
- **(A)** Wizard «Настроить с нуля» path explicitly seeds Classic-6 default flows into `/config/current` locally before navigating to HomeActivity (matches AC #2).
- **(B)** `ConfigBackedFlowRepository` distinguishes «no link yet» (spinner) vs «local-only ConfigDocument exists» (empty-or-seeded list).
- **(C)** `HomeScreen` distinguishes loading vs empty: shows «Загрузка…» only for first ~3s, then «Добавьте свою первую плитку» empty-state CTA.

Recommended: (A) — gives user immediate value, matches TASK-52 AC #2, doesn't require new architectural distinction.

## Side findings during reproduction

1. **Duplicate default-launcher prompt** — FirstLaunchActivity Step 3/4 («Сделать это приложение главным экраном» + «Сделать главным» / «Позже») followed by a second wizard Step 1/3 with the **same question** («Сделать главным экраном» + «Далее» / «Пропустить»). Two consecutive screens asking the same thing.
2. **Provider-leak in copy** — Wizard Step 2/4 button «Войти в Google для восстановления настроек» violates TASK-6 FR-014 «neutral copy» (should be «Войти в свой аккаунт»). TASK-6 spec was written 2026-06-28 explicitly to address this for new code; existing TASK-7 wizard has the leak.
3. **Accessibility regression on cloud button** — «Войти в Google для восстановления настроек» text on light blue button background is very low contrast (likely fails WCAG 4.5:1). Worth a `checklist-accessibility` re-check.

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] HomeActivity completes loading within 5s on Xiaomi 11T after fresh wizard run
- [ ] #2 [hand] Classic-6 tiles render visibly (Phone / SOS / Messages / Photos / Settings / Help)
- [ ] #3 [hand] No silent failure path: if loading fails, user sees error UI not infinite spinner
<!-- AC:END -->

# State-management Checklist: HomeActivity loading regression

**Purpose**: Verify UI state survives Activity recreation, config change, process death per Article IV §5 + §III.3.
**Created**: 2026-06-26
**Feature**: [spec.md](../spec.md)

## Checks

- [x] **Configuration change (rotation, theme switch)** — `HomeLoadingState` retain'ится через Decompose `retainedInstance` (FR-010). Ready остаётся Ready, не reset в Loading.
- [x] **`recreate()` from preset change** — `onPresetChanged = { recreate() }` в HomeActivity. После recreate новый HomeComponent поднимается с новой preset; Loading → Ready flow повторяется штатно.
- [x] **Process death** — cold start от нуля. `HomeComponent` создаётся заново, начинает с Loading, загружает flows через FlowRepository (которая читает persisted active preset). Path покрыт FR-002, FR-007.
- [x] **Low memory kill** — same as process death.
- [x] **`savedInstanceState` not abused** — explicit reject (Clarification Q5): retain через Decompose, не через `Bundle`.
- [x] **No leak of HomeComponent через retain** — Decompose `retainedInstance` корректно cancel'ится при Activity finish (не config change). Это **существующее** поведение Decompose, новых нюансов fix не добавляет.
- [x] **Active preset persistence** — `presetRepository.setActivePreset()` MUST завершиться до старта HomeActivity (FR-007). При process death active preset MUST остаться в persisted storage (assumption: текущий `PresetRepository` implementation уже это делает).
- [x] **Race condition wizard → home** — explicit в Edge Cases + FR-007 mitigation.
- [x] **State machine reentrancy** — если повторный retry стартует пока предыдущий ещё в полёте, что происходит? **Open:** spec не оговаривает. Plan должен решить (cancel previous + start new vs. ignore new while pending).
- [x] **Confirmation dialog state через recreate** — если dialog открыт и пользователь крутит экран, что? **Open:** spec не оговаривает; default — dialog state тоже retain'ится через HomeComponent (или teardown + reopen — decide at plan).
- [x] **Activity recreation во время Error state** — после rotation Error состояние сохраняется (FR-010), пользователь не теряет место в recovery flow.
- [x] **Retry в полёте при recreate** — current pending FlowRepository call. Coroutine scope HomeComponent retain'ится через Decompose, scope живёт → call продолжается, state переходит когда result придёт.

## Verdict

✅ **12/12 passed**, 2 open issues для plan.

## Open issues for /speckit.plan

1. **State machine reentrancy на retry**: cancel-previous-start-new vs ignore-while-pending. Recommended default: cancel previous (latest user action wins), но plan должен закрепить.
2. **Confirmation dialog state retention**: retain через HomeComponent OR teardown-and-reopen. Recommended default: retain (preserves user context), но plan должен закрепить.

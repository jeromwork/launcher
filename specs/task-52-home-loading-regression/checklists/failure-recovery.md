# Failure-recovery Checklist: HomeActivity loading regression

**Purpose**: Verify error paths, fallbacks, timeout, retry, recovery UX are explicit per CLAUDE.md «no silent failure» principle.
**Created**: 2026-06-26
**Feature**: [spec.md](../spec.md)

## Checks

- [x] **Error state is explicit** — `HomeLoadingState.Error(reason)` определён, не «implicit null = error».
- [x] **Timeout is bounded** — 3 секунды (FR-003, SC-001/002). Не infinite wait.
- [x] **User-visible error message** — «Не удалось загрузить настройки» (FR-004). Не silent log-only.
- [x] **Recovery action available** — кнопки «Попробовать снова» + «Сбросить настройки» (FR-005, FR-006).
- [x] **Retry is non-destructive** — FR-005 повторяет `FlowRepository.getFlows()` без потери existing state.
- [x] **Destructive recovery has confirmation** — «Сбросить настройки» защищено dialog'ом (FR-006, per Clarification Q7).
- [x] **No infinite retry loop hazard** — cap отсутствует, но обе кнопки всегда видны, пользователь сам выбирает escape path (per Clarification Q4).
- [x] **Recovery survives Activity recreation** — `HomeLoadingState` retain'ится через Decompose (FR-010).
- [x] **Cancel/back behaviour on confirmation dialog** — FR-006 «при Отмена dialog закрывается, error UI остаётся». Не roll back в Loading.
- [x] **Technical reason logged for debugging** — FR-012 (WARN/ERROR в logcat) — root cause future regression'ов диагностируется.
- [x] **Edge case: corrupted bundled config** — explicit в Edge Cases. FlowRepository возвращает пустой → Error path.
- [x] **Edge case: race condition wizard → home** — explicit в Edge Cases + FR-007 (детерминистический порядок).
- [x] **Edge case: process death cold start** — explicit в Edge Cases + 3s timeout покрывает.
- [x] **Edge case: weak device** — упомянут в Edge Cases + Assumptions. **Open:** для слабых устройств < Xiaomi 11T spec явно не покрывает; risk noted in Assumptions.
- [x] **No fallback "default config when active preset missing"** — намеренно: активный preset = null уводит в FirstLaunchActivity (existing behaviour), не silent override. Это **out of scope** для бага.
- [x] **No "swallow exception"** — `HomeComponent` ловит exceptions из `FlowRepository.getFlows()` и переводит в `Error(reason)`, не proceed silently.
- [x] **Error UI tappable not blocked by 7-tap gate** — assumption в spec: 7-tap admin gate работает поверх error UI (для admin escape). **Open:** verify at plan stage, может быть нюанс.

## Verdict

✅ **17/17 passed**, 2 open issues noted в spec Assumptions и checklist (не блокеры).

## Open issues for /speckit.plan to address

1. **Слабые устройства < Xiaomi 11T** — не покрываются спекой явно; risk что 3s timeout будет недостаточен. Mitigation: SC-007 (cold start regression budget 200ms) фиксирует только non-regression, не absolute. План должен решить — расширить таймаут или явно out-of-scope.
2. **7-tap admin gate поверх error UI** — assumption в spec, не verified в коде. План должен проверить и закрепить либо как FR, либо снять.

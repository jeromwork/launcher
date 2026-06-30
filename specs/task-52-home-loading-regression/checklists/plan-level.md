# Plan-level checklist: HomeActivity loading regression

**Purpose**: Re-run domain-isolation / wire-format / meta-minimization against plan.md (per speckit-plan Step 5).
**Created**: 2026-06-26
**Feature**: [plan.md](../plan.md)

## Domain isolation (CLAUDE.md rule 1, 2)

- [x] **`HomeLoadingState` в commonMain** — sealed class, pure Kotlin. Никаких Android types.
- [x] **State machine логика в `HomeComponent`** — `commonMain`, использует kotlinx.coroutines (allowed per CLAUDE.md rule 1 «coroutine primitives are allowed in domain»).
- [x] **`withTimeout` — стандартная coroutine primitive**, не external SDK.
- [x] **Никаких Decompose API в `HomeLoadingState`** — Decompose `retainedInstance` использует HomeComponent через `componentContext`, но сам `HomeLoadingState` не знает о Decompose.
- [x] **Никаких Android Compose Material типов в state машине** — `AlertDialog` для confirmation живёт в `HomeScreen` Composable (UI layer), не в state.
- [x] **`FlowRepository` / `PresetRepository` — existing ports** — fix не меняет их сигнатуры.
- [x] **Fake `FlowRepository` для unit-теста — pure-Kotlin** — inline в commonTest, без Android dependencies.

**Verdict: 7/7 ✅** — domain isolation cleanly preserved.

## Wire format (CLAUDE.md rule 5)

- [N/A] Fix не вводит новых wire formats.
- [N/A] `HomeLoadingState` не персистируется (in-memory only, retain через Decompose for recreate но не process death — после kill стартует с Loading заново).
- [N/A] Нет JSON / SharedPreferences / DataStore изменений.
- [N/A] Нет deep-link / QR / cross-device data.

**Verdict: N/A** — no wire format surface.

## Meta-minimization (Article XI, CLAUDE.md rule 4)

- [x] **Никаких новых ports / interfaces** — re-use existing.
- [x] **Никаких новых adapters** — изменения в existing.
- [x] **Никаких новых modules** — fix in existing `core` + `app`.
- [x] **`HomeLoadingState` это enum-like sealed class, не state-machine framework** — 3 варианта inline.
- [x] **Confirmation dialog inline Composable** — не extract'ится в `GenericConfirmationDialog` (один use-case = inline).
- [x] **Timeout 3s hardcoded constant** — не `LoadingTimeoutConfig`. Если будущее потребует per-device tuning — расширяется тогда.
- [x] **No `RetryPolicy` / `BackoffStrategy`** — кнопка Retry это direct call (per Clarification Q3).
- [x] **No `ErrorReasonFormatter`** — error reason — простая String, в UI не показывается (FR-012).
- [x] **Test 1 (rule 4)**: убрать state machine — потеряем error recovery. Keep.
- [x] **Test 2 (rule 4)**: если Decompose `retainedInstance` deprecate'нётся — переход на ViewModel scope занимает день; acceptable.

**Verdict: 10/10 ✅** — plan остаётся minimal viable.

## Overall

✅ **17/17 applicable passed, 4 N/A**. Plan ready for `/speckit.tasks`.

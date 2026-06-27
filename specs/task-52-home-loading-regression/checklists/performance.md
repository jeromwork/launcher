# Performance Checklist: HomeActivity loading regression

**Purpose**: Verify cold-start / frame budget / memory / ANR are bounded per Article IX.
**Created**: 2026-06-26
**Feature**: [spec.md](../spec.md)

## Checks

- [x] **Cold start time measurable** — SC-007 фиксирует regression budget 200ms vs baseline (pixel_5_api_34).
- [x] **First frame budget** — SC-001/002 (3s до Ready) — это hard ceiling, не goal. Real target ≤ 1-2s на baseline.
- [x] **No new background work** — fix не вводит WorkManager job / Service / polling loop.
- [x] **No new broadcast receivers** — fix не вводит manifest-registered receivers.
- [x] **No memory bloat** — `HomeLoadingState` это sealed class на 3 варианта; retain через Decompose не добавляет статических singleton'ов.
- [x] **No ANR risk** — `runBlocking { presetRepository.getActivePreset() }` в `HomeActivity.onCreate` — **существующий** код. Fix либо оставит его (если acceptable < 16ms на Xiaomi 11T), либо заменит на suspend init (FR-008). План должен решить.
- [x] **No frame drop introduced** — state machine transitions это `MutableStateFlow.value =`, мгновенные, не блокирующие main thread.
- [x] **Timeout 3s не блокирует main thread** — реализуется через `withTimeout` в coroutine scope.
- [x] **Battery cost** — n/a, fix не добавляет background drain.
- [x] **Logcat WARN/ERROR (FR-012) low frequency** — error log пишется только при actual failure, не на каждый frame.

## Verdict

✅ **10/10 passed**, 1 open issue для плана.

## Open issues for /speckit.plan

1. **`runBlocking` в `HomeActivity.onCreate`** (текущий код в [HomeActivity.kt:55](app/src/main/java/com/launcher/app/HomeActivity.kt#L55)) — потенциальный ANR risk на холодном старте. План должен решить: оставить (если измеренное время < 16ms) или заменить на suspend init через HomeComponent.

## Baseline cold-start measurement

SC-007 требует baseline. Implementation task должен:
1. Установить baseline APK (текущий main) на pixel_5_api_34 emulator, 3 cold start'а, median (используя `adb shell am start -W`).
2. После fix — те же 3 прогона, median.
3. Diff median должен быть ≤ 200ms (regression budget).

Это `[deferred-local-emulator]` task в tasks.md.

---
name: checklist-performance
description: Verifies performance-related requirements are present and measurable — cold start, frame budget, jank, battery cost, memory, ANR. Enforces Article IX of constitution.md. Triggered by mentions of cold start, frame, jank, scroll, animation, battery, background, WorkManager, broadcast, polling, cache.
---

# Checklist: performance

Verifies the spec defines measurable performance targets and identifies risks. Aligned with Article IX of [`/.specify/memory/constitution.md`](/.specify/memory/constitution.md), ADR-005 performance targets, and Android Vitals guidelines.

Reference: [`specs/002-whatsapp-tile-return/checklists/performance-accessibility-testing.md`](specs/002-whatsapp-tile-return/checklists/performance-accessibility-testing.md), [Android Vitals](https://developer.android.com/topic/performance/vitals).

---

## Startup

- [ ] CHK001 If feature affects cold/warm/hot start path: target time documented (per ADR-005: HomeActivity ≤ 600ms, FirstLaunchActivity ≤ 700ms; iOS ≤ 800ms).
- [ ] CHK002 No new I/O / parsing / DI eagerness on the cold-start path without explicit budget.
- [ ] CHK003 Init code added to Application.onCreate has documented cost in milliseconds.

## Runtime — frames

- [ ] CHK004 Scrolling surfaces have target frame budget: 0 dropped frames on medium-tier (Pixel 4a class) per ADR-005.
- [ ] CHK005 Animations duration documented; >300ms animations justified.
- [ ] CHK006 No synchronous network/disk on the main thread.

## ANR risk

- [ ] CHK007 Operations triggered by user input that block the main thread > 100ms identified; either moved off-thread or justified.
- [ ] CHK008 BroadcastReceiver onReceive bodies stay short (no work > 10s, ideally < 1s); long work delegated to WorkManager.

## Background work

- [ ] CHK009 Each new background task (Service, WorkManager job, Coroutine on Application scope, BroadcastReceiver) is justified per Article IX §2.
- [ ] CHK010 Polling explicitly avoided or justified; event-driven preferred per Article IX §3.
- [ ] CHK011 Each event listener documents source, frequency, threading, expected battery cost, fallback if event delayed/absent (Article VI §6).

## Memory

- [ ] CHK012 New caches have explicit size limit and invalidation rule (Article IX §6).
- [ ] CHK013 Bitmap/image loading uses configured memory budget (Coil 3 default policy, no custom 100MB caches).
- [ ] CHK014 No long-lived references holding Activity/Context (leak risk).

## APK / binary size

- [ ] CHK015 New dependencies vs ADR-005 budget (debug ≤ 18MB, release ≤ 12MB; hard-fail at 22 / 16MB) — delta documented.
- [ ] CHK016 Native libraries / large assets justified.

## Measurement

- [ ] CHK017 If perf target is set: measurement method documented (macrobenchmark, manual stopwatch, frametime logs).
- [ ] CHK018 Perf checkpoint planned in tasks.md (perf-checkpoint.md output).

## Battery (Android Vitals)

- [ ] CHK019 No new wake locks without explicit justification and timeout.
- [ ] CHK020 No new alarms / Doze-bypassing scheduling.

---

## How to apply

1. Walk every new background task / animation / startup hook.
2. For each: assign target, identify risk, decide measurement.
3. Failures → add NFR rows in spec or shrink scope.

## Output

Inline into `specs/<id>/checklists/performance.md`.

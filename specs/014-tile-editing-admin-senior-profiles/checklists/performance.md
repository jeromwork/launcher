# Performance — spec 014

Generated: 2026-05-29.

## Startup

- [x] **CHK001** F-014 не на cold-start path. EditMode triggered by user gesture (long-press / 7-tap / tap on target tile) — после launch. No startup impact.
- [x] **CHK002** No new I/O в Application.onCreate. ConfigEditor existing (спека 008) — already on startup path, не F-014 concern.
- [x] **CHK003** No new init code в Application.onCreate.

## Runtime — frames

- [⚠️] **CHK004** Scrolling surfaces: picker tabs (App / Contact / Document / Widget / Action) могут содержать длинные списки (installed apps, contacts). LazyColumn / LazyVerticalGrid implied. Frame budget не explicit. **Improvement**: plan.md specify "0 dropped frames на Pixel 4a class" target per ADR-005.
- [x] **CHK005** Animation durations:
  - Jiggle: "2°, 0.4с" — explicit, под 300ms threshold? **No** — 400ms exceeds 300ms. But jiggle is **continuous loop**, not one-shot. Justified per mainstream UX pattern (Niagara/Pixel match).
  - Drag scale: "1.1x, 8dp elevation" — implied near-instant, not animated transition. OK.
  - Snackbar: 8 sec dismiss timer — это display duration, не animation. OK.
- [x] **CHK006** No sync network/disk on main thread. ConfigEditor `pushPending` async (per спека 008).

## ANR risk

- [⚠️] **CHK007** User-initiated ops blocking main thread > 100ms:
  - `EditUiProfileSelector.selectProfile` — pure function, microsecond. PASS.
  - `addSlot/removeSlot/moveSlot` — pure data ops on ConfigDocument tree. Fast (<10ms даже for large configs). PASS.
  - Picker tab load (installed apps catalog) — может быть slow на cold (~200ms+). **Improvement**: lazy load + skeleton state, plan.md.
- [N/A] **CHK008** F-014 не вводит BroadcastReceiver.

## Background work

- [N/A] **CHK009** F-014 не вводит новых background tasks. ConfigEditor `pushPending` existing.
- [x] **CHK010** Polling avoided. Event-driven через user actions only.
- [N/A] **CHK011** No new event listeners в F-014.

## Memory

- [⚠️] **CHK012** Caches:
  - Picker installed apps cache (existing `FakeInstalledAppsCatalog` / real adapter) — governed спека 005.
  - F-014 не вводит новых caches.
  - Named configs list (≤5 configs) — bounded by FR-003c. Не нужно еxplicit cache invalidation. PASS.
- [N/A] **CHK013** No Bitmap loading в F-014 (tiles render via existing Slot rendering, не F-014 concern).
- [x] **CHK014** No long-lived Activity references — F-014 ops через Decompose ComponentContext (existing pattern).

## APK / binary size

- [x] **CHK015** SC-008: APK delta ≤300 KB. Explicit budget. PASS.
- [x] **CHK016** No native libs / large assets.

## Measurement

- [⚠️] **CHK017** Measurement method для SC-008 (APK size) — implied APK analyzer. **Improvement**: plan.md specify CI check / manual gradle task.
- [⚠️] **CHK018** Perf checkpoint в tasks.md не упомянут. **Improvement**: tasks.md должен include `perf-checkpoint.md` output.

## Battery (Android Vitals)

- [x] **CHK019** No new wake locks.
- [x] **CHK020** No new alarms.

## Specific performance risks для F-014

1. **Jiggle animation на low-end OEM** (Xiaomi MIUI, Vivo FuntouchOS): spec явно listed в Cannot-test-locally gaps. Mitigation per FR-011: `prefers-reduced-motion` → static frame fallback.
2. **Picker tab cold-load**: при первом тапе на "Приложения" tab — installed apps query. Может быть 200-500ms на med-tier. Mitigation: skeleton state + lazy load (plan.md).
3. **5 configs storage** в DataStore: ≤5 named configs × ~5KB ConfigDocument = ~25KB. Trivial.
4. **Optimistic concurrency retry storm**: если admin + senior конкурируют — Q7 silent senior-win prevents storm. PASS.

## Open items

1. **CHK004**: Frame budget для picker scrolling — plan.md.
2. **CHK007**: Picker cold-load lazy strategy — plan.md.
3. **CHK017-CHK018**: APK size measurement + perf-checkpoint в tasks.md.

**Verdict**: PASS с 3 minor open items для plan. F-014 — primarily UI ops + small in-memory state, no major perf risks. Specific risk: jiggle on low-end OEM (already mitigated via FR-011).

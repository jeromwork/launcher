# Permissions & Platform — spec 014

Generated: 2026-05-29.

## Runtime permissions

- [x] **CHK001** F-014 не вводит новых runtime permissions. PASS.
- [x] **CHK002** Existing dependencies:
  - CONTACTS (спека 011) — Contact tile type.
  - HOME role (project-wide) — preset rendering.
  - Notifications (FCM, спека 007) — push for sync.
  All governed parent specs.

## HOME launcher role

- [x] **CHK003** F-014 операции (add/move/remove tile) — UI within home screen, не trigger'ят HOME role re-prompt. PASS.
- [x] **CHK004** Long-press пустого места (FR-005) — стандартный home screen gesture. Doesn't conflict с system long-press handlers (Android allows custom launcher to claim).

## Android version-specific

- [⚠️] **CHK005** Package visibility (Android 11+): F-014 picker shows installed apps. Existing `FakeInstalledAppsCatalog` + real adapter (спека 005) handles `QUERY_ALL_PACKAGES` / `<queries>` manifest pattern. Verify F-014 не bypass'ит.
- [x] **CHK006** Scoped storage (Android 10+): F-014 не touches storage. ConfigDocument через DataStore (app's internal storage). PASS.
- [x] **CHK007** Foreground service types (Android 14+): F-014 не вводит foreground services.

## OEM matrix

OEM Matrix section в spec:

- [⚠️] **CHK008** **Samsung One UI**: standard Android drag-and-drop API. Risk: touch event delivery на S22/S23. Listed as "тестировать". **Improvement**: explicit test target на reach.
- [⚠️] **CHK009** **Xiaomi MIUI Android 11+**: custom long-press handler. Risk per spec "наш long-press не конфликтует". Mitigation: test on Mi 11 Lite 5G (mentioned). **Improvement**: how to detect conflict programmatically — plan.md.
- [x] **CHK010** **Huawei EMUI**: out of GMS scope, N/A в MVP. Explicit. PASS.
- [x] **CHK011** **Pixel stock**: baseline. PASS.
- [⚠️] **CHK012** **OPPO/Vivo ColorOS/FuntouchOS**: animation smoothness risk listed. Mitigation FR-011 (`prefers-reduced-motion`). Acceptable но "tested separately" не specifies how.

## Long-press conflict on launcher

- [⚠️] **CHK013** Long-press пустого места (FR-005) vs system long-press (e.g. MIUI invokes app drawer): нужна **explicit dispatch policy** — кто wins? Если launcher claims HOME role, наш handler первый. Verify в plan.md.

## 7-tap gesture (existing)

- [x] **CHK014** 7-tap gesture (FR-006, спека 010 FR-021) — already validated в спеке 010. F-014 переиспользует. PASS.

## Configuration / preset

- [x] **CHK015** Profile selection by preset (Workspace / Simple Launcher) — F-014 internal, не OS-level config. PASS.

## Open items

1. **CHK005**: Package visibility verify F-014 picker не bypass'ит. Plan.md.
2. **CHK008-CHK009**: Samsung One UI + Xiaomi MIUI touch event testing — physical device needed (already in Cannot-test-locally).
3. **CHK012**: OPPO/Vivo animation testing protocol — plan.md.
4. **CHK013**: Long-press dispatch policy на OEM с custom system handlers — plan.md.

**Verdict**: PASS. F-014 не вводит новых permissions, OEM risks identified в spec. 4 items для plan.md по OEM testing protocol.

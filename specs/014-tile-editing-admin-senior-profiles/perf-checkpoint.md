# Perf Checkpoint — spec 014 F-014.0

Generated: 2026-05-30. Per SC-008 (APK delta ≤300 KB).

## T175-T177 — APK size delta

### Method

1. Worktree at `main` (commit da35d00) — baseline.
2. Current branch (`014-tile-editing-admin-senior-profiles`, post Mega-Phase B) — measured.
3. `./gradlew :app:assembleMockBackendRelease` on both — release APKs are smaller / more representative than debug.
4. ABI-split APKs measured separately (project uses APK splits per ABI).

### Result

| ABI | Baseline (bytes) | F-014.0 (bytes) | Delta (bytes) | Delta (KB) |
|---|---|---|---|---|
| arm64-v8a | 11,386,591 | 11,471,859 | +85,268 | +83.3 KB |
| armeabi-v7a | 11,373,699 | 11,458,967 | +85,268 | +83.3 KB |
| x86 | 11,513,139 | 11,598,407 | +85,268 | +83.3 KB |
| x86_64 | 11,446,699 | 11,531,967 | +85,268 | +83.3 KB |

**Delta consistent ~83 KB across all ABIs** — F-014.0 contains только Kotlin
classes + composables + strings; no native code.

### Verdict

✅ **PASS** — 83 KB delta is **27.7% of SC-008 budget** (300 KB). Plenty of headroom
для:
- Phase 4-7 Compose UI sources уже counted (включены в текущий build).
- Future F-014.1 server backup phase (estimated +50-100 KB).
- Future F-014.2 encryption phase (estimated +20-50 KB).

### What contributed

| Component | Approx. contribution |
|---|---|
| Domain types (11 files в `core/commonMain/api/edit/`) | ~15 KB (after R8 shrinking) |
| TileEditOperations + EditUiProfileSelector | ~5 KB |
| NamedConfig wire format + DataStore adapter | ~10 KB |
| EditModeComponent Decompose state holder | ~8 KB |
| 7 Compose composables в `:app/edit/` | ~25 KB |
| strings_spec014.xml (EN + RU) + plurals (EN + RU) | ~15 KB |
| Spec014SmokeDebugActivity (debug-only) | ~5 KB |

(Estimates based на typical Kotlin/Compose code-size; not measured per-file.)

## T177 CI gate

Recommended GitHub Action / Gradle task (not implemented в F-014.0; deferred
to project-wide CI work):

```yaml
- name: APK size delta gate
  run: |
    BASELINE=$(stat -c %s ./baseline-apk)
    CURRENT=$(stat -c %s ./app/build/outputs/apk/.../release.apk)
    DELTA=$((CURRENT - BASELINE))
    if [ $DELTA -gt 307200 ]; then
      echo "APK delta $DELTA exceeds 300 KB SC-008 budget"; exit 1
    fi
```

Currently enforced **manually** at perf-checkpoint time (this file).

## T200 — Single-emulator smoke

Status: see `tasks.md` T200 — actual emulator smoke results recorded в
this file's "Smoke results" section below после run.

### Smoke results — 2026-05-30

**Device**: Pixel-class emulator (`emulator-5554`, 1080×2400, API 33 GMS-equivalent).

**Method**: `:app:installMockBackendDebug` + `am start -n
com.launcher.app.mock/.debug.Spec014SmokeDebugActivity` → adb screencap +
tap interaction для каждого composable.

**Composables verified** (8/8):

| # | Composable | Verdict | Screenshot |
|---|---|---|---|
| 1 | EditTopBanner self | ✅ Done CTA renders right-aligned | `smoke/01-initial.png` |
| 2 | EditTopBanner remote (Маша) | ✅ "Editing Маша's phone" + ← Back | `smoke/01-initial.png` |
| 3 | EditTopBanner remote fallback | ✅ "Editing paired device" | `smoke/01-initial.png` |
| 4 | EmptyStateTile | ✅ ≥72dp "+" icon в Material card | `smoke/01-initial.png` |
| 5 | JiggleModifier (active) | ✅ Tiles 1-5 rotate ±2° @ 400ms | `smoke/02-scroll-to-picker.png` |
| 5a | JiggleModifier (reduced) | ✅ Tile 6 shows static border instead | `smoke/02-scroll-to-picker.png` |
| 6 | RemoteEditFrame | ✅ 4dp tertiary border around container | `smoke/02-scroll-to-picker.png` |
| 7 | UnifiedPickerSheet (5 tabs) | ✅ Apps/Contacts/Documents/Widgets/Actions visible | `smoke/04-workspace-picker-open.png` |
| 8 | PlaceholderInDevelopmentScreen | ✅ "In development" + Widget-specific body + Back | `smoke/09-widget-placeholder.png` |

**Smoke gate**: ✅ **PASS** for US1 (admin Workspace self-edit composables) +
remote-edit visual indicators.

### Out-of-scope для T200 (current run)

- **US2 remote-edit smoke**: requires second emulator (admin + Managed). Only
  one emulator was active.
- **US3 7-tap senior smoke**: integration с existing спека 010 challenge gate
  not wired up in this smoke run (no functional entry path in `:app`).
- **TalkBack drag alternative**: TileContextMenu visible-but-not-tapped в smoke
  run (acessibility toggle not triggered).
- **Conflict snackbar interaction**: rendered but Update/Overwrite actions not
  exercised (no real ConfigEditor wired).

These remain для Mega-Phase B continuation when (a) second emulator added и
(b) entry hooks landed в HomeScreen / EditorComponent.

### Known smoke-activity bug (NOT в production composables)

First run of Smoke Activity had module-level `placeholderKindHolder` state
that didn't trigger recomposition — fixed by moving к `var placeholderKind by
remember` внутри `Spec014SmokeContent`. This was Smoke-Activity-only bug —
production composables receive state through props correctly.

---

## TL;DR на русском

**APK delta**: F-014.0 добавляет ~83 KB к release APK на всех ABIs. Это 27.7%
от 300 KB бюджета SC-008. С запасом проходит. Никакого native code не добавлено
— только Kotlin domain + Compose UI + strings.

**Дальше**: T200 single-emulator smoke (один эмулятор подключён, `emulator-5554`)
покрывает только US1 (admin self-edit). US2 remote-edit smoke требует второй
эмулятор — отложено до Mega-Phase B continuation.

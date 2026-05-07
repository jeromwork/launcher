# Launcher Design System

Source of truth for visual decisions in this project.
Implementation lives in `core/src/commonMain/kotlin/com/launcher/ui/theme/`.

This document is binding for every UI change. Do not introduce ad-hoc colors,
font sizes, paddings, or shapes in screens — only use the tokens defined here
through `MaterialTheme.colorScheme / typography / shapes / *Dimens`.

---

## 1. Foundation

- **Material 3** is the base, per [ADR-005](../adr/ADR-005-ui-stack-compose-multiplatform.md).
  No Material 2, no custom alternative theme system.
- **Same theme on Android and iOS.** No Cupertino-look (per ADR-005 Amendment
  2026-05-07b). One `LauncherTheme` Composable wraps all screens on both
  platforms.
- **Senior-safe overrides** (per Article VIII and `senior-safe-launcher-plan.md`)
  override Material 3 defaults globally — they are not opt-in per screen.

---

## 2. Color

### 2.1 Seed and approach

The palette is generated around one seed color: **`#3E5F8A`** (calm trust blue).

Rationale: warm enough to feel friendly, cool enough to feel reliable;
high readability against neutral backgrounds; common cultural association
with safety / institutions.

Light and dark schemes are hand-tuned around this seed (no runtime dynamic
color generation — fixed palette so behaviour is identical on Android and
iOS, and identical across OEM Android builds).

### 2.2 Light scheme

| Role | Hex | Used for |
|---|---|---|
| primary | `#3E5F8A` | Main brand color, primary buttons |
| onPrimary | `#FFFFFF` | Text/icons on primary |
| primaryContainer | `#D7E3FF` | Soft primary surfaces, selected chips |
| onPrimaryContainer | `#001A41` | Text on primaryContainer |
| secondary | `#5B5D72` | Secondary actions |
| onSecondary | `#FFFFFF` | |
| secondaryContainer | `#E0E1F9` | |
| onSecondaryContainer | `#181A2C` | |
| tertiary | `#76546E` | Tertiary accents (e.g. wizard step highlight) |
| onTertiary | `#FFFFFF` | |
| tertiaryContainer | `#FFD7F1` | |
| onTertiaryContainer | `#2D1228` | |
| error | `#BA1A1A` | Errors, destructive actions |
| onError | `#FFFFFF` | |
| errorContainer | `#FFDAD6` | Error backgrounds |
| onErrorContainer | `#410002` | |
| background | `#FDFBFF` | Screen background |
| onBackground | `#1A1B1F` | Body text |
| surface | `#FDFBFF` | Cards, sheets |
| onSurface | `#1A1B1F` | |
| surfaceVariant | `#E1E2EC` | Card outlines, subtle surfaces |
| onSurfaceVariant | `#44474F` | Secondary text |
| outline | `#74777F` | Hairlines, dividers |
| outlineVariant | `#C4C6D0` | Soft hairlines |

### 2.3 Dark scheme

Symmetric inversion of light scheme via Material 3 conventions. Exact values
in `Color.kt`.

### 2.4 Contrast

Every foreground/background pair in the schemes above MUST achieve contrast
≥ **4.5:1** (Article VIII §2). Verified via Material Theme Builder.

---

## 3. Typography

15 roles per Material 3, with the following overrides for senior-safe legibility.

| Role | Default size | Project size | Reason |
|---|---|---|---|
| displayLarge | 57sp | 57sp | unchanged |
| displayMedium | 45sp | 45sp | unchanged |
| displaySmall | 36sp | 36sp | unchanged |
| headlineLarge | 32sp | 32sp | unchanged |
| headlineMedium | 28sp | 28sp | unchanged |
| headlineSmall | 24sp | 24sp | unchanged |
| titleLarge | 22sp | 22sp | unchanged |
| titleMedium | 16sp | **18sp** | bumped (senior-safe) |
| titleSmall | 14sp | **16sp** | bumped |
| bodyLarge | 16sp | **18sp** | bumped — minimum body size per Article VIII |
| bodyMedium | 14sp | **16sp** | bumped |
| bodySmall | 12sp | **14sp** | bumped |
| labelLarge | 14sp | **16sp** | bumped — button label size |
| labelMedium | 12sp | **14sp** | bumped |
| labelSmall | 11sp | **12sp** | bumped |

Font: system default (Roboto on Android, San Francisco on iOS — Material 3
selects automatically). No custom font added in this spec; can be evaluated
later only with explicit perf/legibility justification.

Line heights: Material 3 defaults; not overridden.

---

## 4. Shape

| Role | Default | Project | Used for |
|---|---|---|---|
| extraSmall | 4dp | 4dp | small chips |
| small | 8dp | 8dp | inputs, small buttons |
| medium | 12dp | **16dp** | cards, tiles, list items |
| large | 16dp | **24dp** | bottom sheets, dialogs |
| extraLarge | 28dp | 28dp | large surfaces |

Corners are slightly softer than Material 3 default — visual cue for
"friendly" rather than "sharp / institutional".

---

## 5. Spacing

Fixed scale (8dp grid). No arbitrary paddings in screen code.

| Token | Value | Used for |
|---|---|---|
| `Spacing.xs` | 4dp | tight icon-text gap |
| `Spacing.sm` | 8dp | small gaps |
| `Spacing.md` | 16dp | default content padding |
| `Spacing.lg` | 24dp | section separation |
| `Spacing.xl` | 32dp | screen-edge padding |
| `Spacing.xxl` | 48dp | large hero gaps |

Defined in `Dimens.kt`.

---

## 6. Tap targets and accessibility minimums

Hard minimums (Article VIII):

- **Tap target**: ≥ **56dp** (Material 3 default 48dp → bumped). Applied via
  `Modifier.minimumInteractiveComponentSize()` plus explicit `Modifier.size()`
  on custom components.
- **Body text**: ≥ 18sp (see Typography table).
- **Contrast**: ≥ 4.5:1 for normal text, ≥ 3:1 for large text and graphical
  elements.
- **Icons in primary actions** MUST have a text label adjacent to or under
  them. Icon-only primary actions are forbidden.
- **Hidden gestures** (swipe-to-do-X without visible affordance) are
  forbidden in launcher-mode and senior-safe surfaces.
- **Animations are optional**, readability and orientation are mandatory
  (Article VIII §5). Never block content visibility behind an animation.

---

## 7. Elevation

| Token | Value | Used for |
|---|---|---|
| `Elevation.level0` | 0dp | flat surfaces |
| `Elevation.level1` | 1dp | resting cards |
| `Elevation.level2` | 3dp | hovered/pressed cards |
| `Elevation.level3` | 6dp | dialogs, dropdowns |
| `Elevation.level4` | 8dp | modal sheets |
| `Elevation.level5` | 12dp | (reserved) |

Defined in `Dimens.kt`.

---

## 8. Density and presets

Three presets (per spec 003): `workspace`, `launcher`, `simple-launcher`.
They map to a `LayoutDensity` value used by `LauncherTheme`:

| Preset | Density | Effect |
|---|---|---|
| `workspace` | `Standard` | Tokens above as-is |
| `launcher` | `Comfortable` | Body text +2sp, tap targets +4dp |
| `simple-launcher` | `SeniorSafe` | Body text +4sp, tap targets +8dp, larger spacing |

`LauncherTheme(preset = ..., content = { ... })` selects the density and
multiplies typography/spacing accordingly. Screens themselves do not check
the preset — they only use `MaterialTheme.typography.bodyLarge` etc., which
are already adjusted by the theme.

---

## 9. Authoring rules for screens

These rules are binding for every Composable screen and component:

1. **Never** hard-code a color hex. Use `MaterialTheme.colorScheme.*`.
2. **Never** hard-code a font size in sp. Use `MaterialTheme.typography.*`.
3. **Never** hard-code a corner radius in dp for backgrounds/cards. Use
   `MaterialTheme.shapes.*` or `Modifier.clip(MaterialTheme.shapes.medium)`.
4. **Never** invent a new spacing value. Use `Spacing.*`.
5. **Custom components live only in** `core/src/commonMain/kotlin/com/launcher/ui/components/`.
   Screens consume them; screens do not declare their own private cards or
   buttons.
6. Tap-eligible elements **must** declare `Modifier.minimumInteractiveComponentSize()`
   or an explicit `.size(>= 56.dp)`.
7. Every primary action **must** have a text label visible at all times.
8. New tokens (color role, typography role, spacing scale, shape) require an
   update to this document **before** code is added.

A screen that violates these rules fails review.

---

## 10. Open evolution

The following are intentionally not yet decided and stay closed until a
specific feature needs them:

- Custom font (currently system default).
- Iconography source (currently `material-icons-extended` + custom SVGs as
  needed; no second icon family).
- Illustration/marketing graphics — scope for later product work.
- Per-platform adjustments (Cupertino-look on iOS) — closed by ADR-005
  Amendment 2026-05-07b; will only be reopened if App Store review or user
  research forces it.

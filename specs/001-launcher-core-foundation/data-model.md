# Data Model: Launcher Core Foundation

**Feature**: `001-launcher-core-foundation`  
**Spec**: [spec.md](./spec.md)

Logical entities for Core; storage formats are implementation details unless noted.

---

## Profile

**Purpose**: Drives enabled modules, accessibility-related presets, and layout-related preferences for the active launcher variant.

| Field / aspect | Description | Rules |
|----------------|-------------|--------|
| `schemaVersion` | Positive integer for profile document | MUST be present; mismatch triggers migration or fallback per policy |
| `id` | Stable profile identifier | Unique within deployment; used for logging/diagnostics only in MVP |
| `moduleFlags` | Map module id → enabled | MUST align with ModuleRegistry; unavailable modules ignored with degraded mode |
| `accessibilityPreset` | Opaque or enumerated preset id | Optional; interpreted only by modules/Home that consume it |
| `layoutHints` | Optional hints for home composition | MUST NOT bypass fallback hierarchy |

**Relationships**: Consumed by **ProfileEngine**; references **module ids** known to **ModuleRegistry**.

**Validation**: Invalid or corrupt profile → **Safe fallback** profile (bundled default). Partial document → defaults merged per published rules.

**State**: Loaded at process start; reload triggers recomposition of registered modules (event-driven, not polling).

---

## Module descriptor

**Purpose**: Declares a feature module’s identity and contract surfaces for registration.

| Field / aspect | Description | Rules |
|----------------|-------------|--------|
| `moduleId` | Stable string id | Unique; used in profile `moduleFlags` |
| `requiredContracts` | Set of contract ids | Module MUST NOT start if required contract version unsatisfied (degraded mode) |
| `publishedSurfaces` | Capability tokens (e.g., `home.region`) | Only declared surfaces may be invoked by shell or other modules |

**Relationships**: Held by **ModuleRegistry**; may reference **Profile** for enablement.

---

## Published contract (versioned)

**Purpose**: Architectural API boundary between Core and modules.

| Field / aspect | Description | Rules |
|----------------|-------------|--------|
| `contractId` | Stable id | e.g., `launcher.events`, `launcher.appindex` |
| `majorVersion` | Int | Breaking change increments major; consumers declare compatibility range |

**Relationships**: Referenced by **Module descriptor** and **ProfileEngine** compatibility checks.

---

## Catalog entry (AppIndex)

**Purpose**: Single logical representation of a launchable or launcher-visible item.

| Field / aspect | Description | Rules |
|----------------|-------------|--------|
| `stableKey` | String (e.g., package + user handle concept) | Uniqueness per index policy |
| `displayLabel` | User-visible label | Suitable for TalkBack / large text |
| `contentDescription` | Optional a11y string | SHOULD be present for icon-only rows |
| `launchIntentPayload` | Opaque to features; resolved by Core | Features MUST NOT construct raw launch Intents bypassing ActionDispatcher unless contract says otherwise |
| `isLaunchable` | Boolean | False when policy or permission blocks launch |

**Relationships**: Produced by **AppIndex**; read-only to features via contract.

---

## Project-level event

**Purpose**: Normalized signal after **SystemEventBridge** (no raw Android types in feature API).

| Field / aspect | Description | Rules |
|----------------|-------------|--------|
| `type` | Sealed category | e.g., `PackageSetChanged`, `ProfileChanged` |
| `payload` | Typed summary | Minimal; no large lists—consumers query **AppIndex** |
| `sequenceHint` | Optional monotonic id | For dedup / ordering in UI |

**Relationships**: Emitted by **EventRouter**; subscribed by modules and shell.

---

## Action request

**Purpose**: User- or system-initiated operation routed through **ActionDispatcher**.

| Field / aspect | Description | Rules |
|----------------|-------------|--------|
| `kind` | Enum / sealed | e.g., `OpenApplication`, `OpenSystemSettings` |
| `targetRef` | Reference to catalog entry or uri | Validated before dispatch |
| `sourceModuleId` | Optional audit | For conflict resolution logging only in MVP |

---

## Safe fallback and degradation record

**Purpose**: Deterministic explanation of why a non-default path is active (for QA and tests).

| Field / aspect | Description | Rules |
|----------------|-------------|--------|
| `activeProfileId` | Effective profile | May differ from requested when overridden |
| `degradedModules` | List of module ids | Unavailable or disabled by conflict resolution |
| `reasonCodes` | Ordered list | Maps to spec precedence: safety → contract → module → permission → profile |

**Relationships**: Output of **ProfileEngine** + **ModuleRegistry** resolution; observable by Home for optional user messaging (copy deferred to UX specs).

---

## Conflict resolution order (canonical)

Per **spec.md** Assumptions (must not invert without new spec):

1. Runtime safety and shell continuity  
2. Contract compatibility  
3. Module availability  
4. Permission-constrained capability state  
5. Profile intent for optional behavior  

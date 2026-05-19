# Domain-Isolation Checklist: Setup Assistant and Launcher Bootstrap

**Purpose**: Enforce domain-from-infrastructure boundary per CLAUDE.md rules 1 & 2 + ADR-001.
**Created**: 2026-05-19 (post `/speckit.clarify`)
**Feature**: [spec.md](../spec.md)

---

## External surfaces introduced/touched by spec 010

1. **`SetupCheck` port** (FR-017, new) — `core/commonMain/api/setup/`. Domain-typed contract.
2. **`Challenge` + `ChallengeRegistry`** (FR-022/023, new) — `core/commonMain/api/gate/`. Domain-typed.
3. **`SlotToActionMapper`** (FR-003, new) — `core/commonMain/api/action/`. Pure function over domain types.
4. **`IntentSpec`** (Key Entities, new) — `core/commonMain/api/setup/`. Platform-agnostic intent descriptor.
5. **GMS availability check** (FR-042..FR-044, new) — port shape **not yet defined в спеке 10**. See Open items.
6. **`RoleManager` integration** (FR-007) — Android `RoleManager.createRequestRoleIntent(ROLE_HOME)`. Activity-side, no port.
7. **`POST_NOTIFICATIONS` request** (FR-008) — Android permission request. Activity-side, no port.
8. **`PhoneHandler` extension** (FR-011..FR-016) — existing port from спека 5 T541, extended.
9. **Haptic feedback** (FR-021) — `HapticFeedbackConstants` / Compose `LocalHapticFeedback`. UI-side.

## Vendor SDKs

- [⚠️] **CHK001** — Vendor types в domain signatures:
  - `GoogleApiAvailability` appears in FR-042 text directly: «System MUST детектировать наличие Google Play Services через `GoogleApiAvailability.isGooglePlayServicesAvailable()`». Strict reading — vendor type bleeds into spec text. **However**: вfr-042 specifies *implementation behavior* (which API to call in adapter), а не domain signature. Domain должен видеть **GmsAvailabilityPort** с domain-typed result. **Spec text должен быть переформулирован в `/speckit.plan` чтобы убрать прямую ссылку на `GoogleApiAvailability` из FR.**
- [⚠️] **CHK002** — Each external SDK has exactly one wrapper:
  - **GMS — port отсутствует** в спеке 10. Прямое использование `GoogleApiAvailability` в FR-042. Plan.md ДОЛЖЕН ввести `GmsAvailabilityPort` (или переиспользовать существующий из спека 7 если есть) в `core/commonMain/api/setup/` с domain-typed result (`sealed GmsStatus { Available, MissingRecoverable(reason), MissingFatal(reason) }`). Real adapter в `:app/androidMain` wraps `GoogleApiAvailability`.
  - `RoleManager`: activity-bound, no port wrap needed (CLAUDE.md rule 2 — wrap only when domain consumes; activity glue не is domain).
  - `HapticFeedbackConstants`: UI-side, Compose `LocalHapticFeedback` is the abstraction, no domain port.
  - `PhoneHandler`: existing port from спека 5, extended (FR-012). Adapter-bound.
- [⚠️] **CHK003** — «Vendor disappears tomorrow» test:
  - **GMS disappears**: 4-5 files — hard-block screen UI, GMS adapter (new), FCM adapter (спек 7), Firestore adapter (спек 8), DI wiring. **Currently fails** because GMS reference в FR-042 not behind port — необходимо ≤ 1 adapter module changed. **Plan.md фикс через `GmsAvailabilityPort`.**

## Transport types

- [X] **CHK004** — Нет transport types в domain. Spec 010 не вводит wire formats (FR-040).
- [X] **CHK005** — Нет wire format DTO posing as domain. `Challenge` — in-memory only (FR-025). `SetupCheck` results — domain `CheckStatus`, не transport.

## Platform types

- [X] **CHK006** — Нет `android.*`/`androidx.*`/`Intent`/`Uri`/`Context`/`Bundle`/`LifecycleOwner` в `commonMain` planned types. `IntentSpec` (data class, platform-agnostic) carries category/action как `String`, mapping в `:app/androidMain`. ✓
- [X] **CHK007** — Platform-derived data → domain projection. Intent extras → `IntentSpec` (Key Entities). ROLE_HOME, POST_NOTIFICATIONS — activity-level intent strings, не domain values.

## Ports

- [⚠️] **CHK008** — Every external surface через port:
  - SetupCheck ✓
  - ChallengeRegistry ✓ (хотя см. meta-minimization CHK002 — port shape borderline)
  - SlotToActionMapper ✓ (pure function, не нужен port)
  - **GMS availability — port отсутствует** ❌ (CHK002 above)
  - ROLE_HOME / POST_NOTIFICATIONS — activity-bound, no port (acceptable per rule 2: «activity glue не domain»)
- [X] **CHK009** — Port shape driven by domain need. `SetupCheck` — domain language (id, criticality, surfaces, check, resolveIntent), не `getFromSharedPreferences(key)`-style.
- [⚠️] **CHK010** — Fake adapters: **plan.md detail**. Spec 010 не enumerates fakes но FR-041 mandate'ит CLAUDE.md rule 6 (mock-first). Phase 4 of спека 9 этот pattern, spec 010 plan должен follow.
- [⚠️] **CHK011** — Real adapters: **plan.md detail**. Spec 010 не enumerates real adapters но implication ясна: `:app/androidMain` для всех Android-specific bindings.
- [⚠️] **CHK012** — DI wiring fake/real per build: **plan.md detail**. Pattern есть в спеке 9 (Phase A Koin modules), spec 010 plan должен follow.

## Source-set placement

- [X] **CHK013** — Source-set assignments явные в спеке: `core/commonMain/api/setup/` (SetupCheck, IntentSpec), `core/commonMain/api/gate/` (Challenge), `core/commonMain/api/action/` (SlotToActionMapper). Real adapters: `:app/androidMain` (explicit в Key Entities + Затрагиваемые внешние артефакты).
- [X] **CHK014** — Default `commonMain` соблюдён. Android-specific code только в `:app/androidMain` (activities, intent mapping, Koin adapter modules).

## Existing-code regressions

- [⚠️] **CHK015** — `GoogleApiAvailability` reference в FR-042 — спорно. Existing `core/` файлы (созданные спеком 6, 7, 8) уже cleansed от vendor types. Спек 10 в чистом виде эту cleanliness не нарушает (тип не утечёт в `commonMain`), но **спек-текст** ссылается на вендор API напрямую. **Soft regression**: переформулировать в `/speckit.plan` через port.
- [X] **CHK016** — No new `expect`/`actual` mentioned in spec. ✓ (existing `expect`/`actual` from спеков 6/7 остаются как есть).

---

## Open items (must address in `/speckit.plan`)

1. **CHK001/CHK002/CHK003/CHK015 — GMS port introduction.** Plan.md MUST введите:
   ```kotlin
   // core/commonMain/api/setup/GmsAvailabilityPort.kt
   interface GmsAvailabilityPort {
       suspend fun status(): GmsStatus
   }
   sealed interface GmsStatus {
       data object Available : GmsStatus
       data class MissingRecoverable(val reason: String, val resolutionAvailable: Boolean) : GmsStatus
       data class MissingFatal(val reason: String) : GmsStatus
   }
   ```
   Real adapter `:app/androidMain/GmsAvailabilityAdapter.kt` wraps `GoogleApiAvailability`. Spec.md FR-042 текст должен быть пересмотрен в plan-driven update — domain видит `GmsAvailabilityPort.status()`, не вендор API.

2. **CHK010/CHK011/CHK012 — Plan.md должен enumerate**: fake adapters for SetupCheck (each of 5 checks needs fake), ChallengeRegistry fake, GmsAvailabilityPort fake; real adapters; Koin DI module wiring per build flavor (mockBackend vs realBackend, как в спеке 7).

## Result

**11/16 ✓, 5 observations** (CHK001+CHK002+CHK003+CHK015 связанные с GMS port introduction → plan-level fix; CHK010+CHK011+CHK012 — fake/real adapter enumeration → plan-level detail). **Не blocker для `/speckit.plan`** — все findings адресуются на plan-уровне, не требуют пересмотра спека. **CRITICAL для plan**: GMS port introduction до Phase 1 implementation.

---

## Краткое содержание (для не-разработчика)

Проверили правило 1 CLAUDE.md: domain-код не должен видеть API внешних SDK напрямую. **Главное нарушение**: FR-042 в спеке напрямую ссылается на `GoogleApiAvailability` (Google Play Services API). Это должно быть behind port'ом (`GmsAvailabilityPort` в `core/commonMain/api/setup/`), не вендор API в domain. **План обязан добавить этот port** + adapter + DI wiring в первой же фазе. Остальные находки (fake adapters, real adapters, DI wiring enumeration) — стандартная plan-работа.

# Research: Launcher Core Foundation

**Feature**: `001-launcher-core-foundation`  
**Date**: 2026-03-28

Consolidated decisions for greenfield Android launcher Core. No `NEEDS CLARIFICATION` remains for MVP planning.

---

## 1. Toolchain and baseline

**Decision**: Kotlin **2.0.x**, Android Gradle Plugin **8.7+**, `compileSdk` / `targetSdk` **35**, `minSdk` **26**.

**Rationale**: Aligns with current Android Studio defaults and Play policy trajectory; API 26+ simplifies background/limitations narrative while still covering a wide device range. Launcher-specific package visibility and queries are handled at **runtime** with guards, not by raising `minSdk` to 30.

**Alternatives considered**: `minSdk 24` — rejected for this plan to reduce legacy edge-case matrix for first Core delivery; can be revisited via ADR if product requires broader reach.

---

## 2. UI stack

**Decision**: **View + XML only**; no Compose dependencies on the classpath for `app` or `core`.

**Rationale**: Binding requirement from `spec.md` and constitution.

**Alternatives considered**: Compose for new UI — explicitly excluded unless constitution exception.

---

## 3. Asynchronous model and state

**Decision**: **Kotlin coroutines** + **Flow** for Core internals; **Main** dispatcher for UI-facing emissions; **Default** or **IO** for `AppIndex` refresh work with structured concurrency (no GlobalScope).

**Rationale**: Standard Kotlin/Android pattern; supports UDF-style observation without introducing RxJava or extra heavy deps.

**Alternatives considered**: LiveData-only — viable but mixes poorly with multi-module Core services; coroutines are one well-supported dependency.

---

## 4. Dependency injection

**Decision**: **Manual constructor injection** + a small **Application**-scoped service locator or explicit graph object in `app` for MVP Core wiring; **no** Hilt/Dagger in the first Core Foundation milestone unless a follow-up task proves constructor chains become unmaintainable.

**Rationale**: Constitution Article XI (anti-speculative abstraction); smallest correct change for two modules.

**Alternatives considered**: Hilt — defer until module count and lifecycle complexity justify the dependency (document in ADR when introduced).

---

## 5. Profile storage (MVP)

**Decision**: **Bundled default profile** as versioned JSON under `assets` (or raw) parsed at startup; optional **DataStore** for last-known active profile id / overlay path in a later iteration. Validation and fallback per `spec.md` FR-003, FR-007, FR-008.

**Rationale**: Spec defers exact JSON schema fields; foundation delivers **pipeline** (load → validate → resolve fallback) with a **minimal** bundled schema version field and module enable flags.

**Alternatives considered**: Room-first — unnecessary before profile schema is product-stable.

---

## 6. System events (MVP scope)

**Decision**: Implement **SystemEventBridge** with **minimal** registrations:

| Signal (conceptual) | Source | Frequency | Thread | Power note | Fallback |
|---------------------|--------|-----------|--------|------------|----------|
| Package set changes | `ACTION_PACKAGE_*` / `MY_PACKAGE_REPLACED` as applicable | Low (user installs/updates) | Main receiver → handoff to IO for index rebuild | Event-driven, no polling | If delivery delayed, **AppIndex** refreshes on next cold start or `onResume` of Home |
| Timezone / locale (optional) | `ACTION_*` if catalog labels depend on it | Rare | Main → debounced | Low | Skip in absolute MVP if not needed |

**Boot_COMPLETED**: **Not registered** in MVP unless a later spec proves cold-start catalog is insufficient; document as explicit non-goal for Foundation milestone to avoid extra wakeups.

**Rationale**: Constitution Articles VI and IX; spec resource discipline.

**Alternatives considered**: Polling `PackageManager` — rejected.

---

## 7. Testing stack

**Decision**: **JUnit 4/5** + **MockK** + **Robolectric** for Core unit/integration-style tests; **Android instrumented tests** only where `PackageManager` / real context behavior is required.

**Rationale**: Keeps CI viable before physical device farm; contract tests run on JVM where possible.

**Alternatives considered**: Espresso-only — too slow for Core logic iteration.

---

## 8. Module graph for this milestone

**Decision**: Gradle modules **`app`** + **`core`** only for the **first implementable increment**; first real **`feature-*`** module ships with the first vertical feature spec (e.g., Favorites) to avoid empty modules.

**Rationale**: Constitution Article V (restraint); spec FR-019 still satisfied at **documentation** level: `feature-*` pattern, contracts, and `settings.gradle.kts` comment/template prepared in implementation tasks.

**Alternatives considered**: Add empty `feature-placeholder` — rejected as noise.

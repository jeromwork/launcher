# Client — Android app architecture (`client-android` domain)

**This is the single source of truth for the Android client's layering** — the domain/adapter/UI split, ports, DI, and the fitness rules that keep them honest. If it and any other doc disagree on client layering, this file wins — except: each domain's *content* is owned by its own arch-pack (crypto, ecs, messaging, …); this file owns only the *cross-cutting layering shape*. Change the layering → update this file in the same commit.

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: three layers, arrows point **down only** (a rule-1 fitness check):

```
UI (Jetpack Compose screens + ViewModels)
   ↓ depends ONLY on ports
Domain (core/, pure Kotlin/KMP, ZERO Android or vendor imports)
   ↑ implemented by
Adapters (app/adapters/<vendor>/, one ACL per external surface)
```

- **Domain** (`core/`) is pure Kotlin Multiplatform — no Android, no vendor SDK types (rule 1). It declares **ports** (interfaces); it is the only thing UI and adapters may both depend on.
- **Adapters** (`app/adapters/<vendor>/`) — one anti-corruption wrapper per external surface (rule 2): openmls, Firestore, FCM, QR pairing, Keystore/SQLCipher, location, etc. If a vendor vanished, only its adapter module changes.
- **UI** — Compose + ViewModels, depend on **ports only**, never on an adapter, the engine, or Android system calls directly. Feature-seam pattern: a feature exposes a purpose-shaped gateway port (e.g. `SettingsGateway`), behind which the implementation lives as an adapter.
- **DI** — manual constructor injection (no DI framework in MVP); wiring picks fake vs real adapter per build (rule 6).

**Status**: Prod (this is current-state, not a plan). **Invariants** (CA1–CA4, see §Invariants).

**Routing**: layering / ports / DI / where-does-X-live → stay here. A domain's actual model → its arch-pack (crypto.md / ecs.md / messaging.md / gallery.md / safety.md / identity.md). Versioning → [`wire-format.md`](wire-format.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **CA1 — domain is pure (rule 1).** `core/` has zero Android and zero vendor-SDK imports; no transport/DTO types as domain values. Fitness: import-guard; arrows point down only.
- **CA2 — one adapter per external surface (rule 2).** Each vendor/system SDK is wrapped in exactly one `app/adapters/<vendor>/` ACL exposing a domain-owned port. Test: if the vendor vanished, only that module changes.
- **CA3 — UI/VM depend on ports, never on adapters/engine/Android directly.** Use a purpose-shaped gateway seam per feature (rule 4). Fitness: no vendor calls, no `when(concreteType)` in UI.
- **CA4 — every port has a fake + real adapter + DI wiring (rule 6).** Mock-first: domain and UI build against fakes before real SDKs; fakes never ship in release (R8 strip + Konsist fitness).

## Build-vs-buy

- 🟢 **Jetpack Compose** (UI), **Kotlin Multiplatform** (domain) — platform-standard.
- **DI**: manual constructor injection in MVP (rule 4 — no framework until needed). Exit ramp: adopt Hilt/Koin only if wiring pain justifies it.
- iOS: `core/` is KMP-ready; iOS adapters (Keychain, CommonCrypto) are the additive path (rule 1 keeps `commonMain` unchanged).

## Related domains

- Domain content lives in the per-domain arch-packs: [`crypto.md`](crypto.md) · [`ecs.md`](ecs.md) · [`messaging.md`](messaging.md) · [`gallery.md`](gallery.md) · [`safety.md`](safety.md) · [`identity.md`](identity.md). External SDKs behind adapters: [`external-services.md`](external-services.md). Server: [`server.md`](server.md). Versioning: [`wire-format.md`](wire-format.md).
- Onboarding: [`INDEX.md`](INDEX.md).

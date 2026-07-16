---
name: checklist-domain-isolation
description: Verifies the spec maintains domain-from-infrastructure isolation per CLAUDE.md rules 1 and 2. Catches vendor SDK types in domain, transport types as wire format, raw platform types in domain values, and ports without proper ACL wrapping. Triggered when spec mentions external SDKs, commonMain/androidMain/iosMain, or ports/adapters.
---

# Checklist: domain-isolation

Enforces the **domain-from-infrastructure** boundary defined in [`CLAUDE.md`](CLAUDE.md) rules 1 (Domain isolated from infrastructure) and 2 (Anti-Corruption Layer for every external dependency). Aligned with ADR-001 (Platform Parity Gate).

---

## Vendor SDKs

- [ ] CHK001 No vendor SDK type (Firebase, Coil, WhatsApp, Google Play Services, Crashlytics, etc.) appears in any signature visible to the domain layer (`commonMain` ports, domain values, repositories).
- [ ] CHK002 Each external SDK has exactly one wrapper module (the **adapter**); domain references **only the port**.
- [ ] CHK003 The "vendor disappears tomorrow" test: number of files needing change is ≤ size of one adapter module. Documented.

## Transport types

- [ ] CHK004 No transport types (HTTP clients, retrofit annotations, raw JSON containers) appear in domain signatures.
- [ ] CHK005 If domain emits/consumes a wire format, the **wire format type** is a domain-owned data class with serializers in adapter (not a generated DTO posing as a domain model).

## Platform types

- [ ] CHK006 No `android.*`, `androidx.*`, `Intent`, `Uri`, `Context`, `Bundle`, `LifecycleOwner` appears in `commonMain`.
- [ ] CHK007 If a domain value needs platform-derived data (e.g., a package name, a content URI), it carries a domain-typed projection (`String`, `LauncherUri`), not the raw platform type.

## Ports

- [ ] CHK008 Every external surface used by this spec is exposed through a **port** (interface in `commonMain`).
- [ ] CHK009 Port shape is driven by domain need, not by adapter convenience (no method like `getFromSharedPreferences(key)`).
- [ ] CHK010 Each port has a fake adapter (in `commonTest` or shared test artifact) used by tests of higher-level code (per CLAUDE.md rule §6).
- [ ] CHK011 Each port has a real adapter (in `androidMain` and/or `iosMain`).
- [ ] CHK012 DI wiring picks fake/real per build per CLAUDE.md rule §6.

## Source-set placement

- [ ] CHK013 For every new file: clearly assigned to `commonMain` / `androidMain` / `iosMain` with one-sentence justification (per ADR-005 §3 Gate 1).
- [ ] CHK014 Default placement is `commonMain`; deviation has explicit reason (uses platform API, requires platform packaging, etc.).

## Existing-code regressions

- [ ] CHK015 Spec doesn't reintroduce any vendor type into a `commonMain` file already cleansed by prior specs.
- [ ] CHK016 Spec doesn't add new `expect`/`actual` declaration where pure-Kotlin would suffice.

---

## How to apply

1. List every new external surface or new port introduced by spec.
2. Walk the gates. Failures → restructure; usually adding an adapter or moving a type.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-domain-isolation: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/domain-isolation.md`. Scratch buffer permitted, must be deleted before returning. Notable failures also surface in `speckit-analyze` punch list. Grey items land as edits to `spec.md` / `plan.md`.

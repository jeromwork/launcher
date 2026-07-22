# Module extraction policy (family of apps)

**This file is the single source of truth for *when and how* a module gets extracted into a shared library** reused across the app family (launcher → messenger → photo album → …). If it and any other doc disagree on extraction, this file wins. It does **not** own the *content* of any module — it owns the extraction discipline only. Change the policy → update this file in the same commit.

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: **extract on the second real consumer, not before** (Fowler's rule of three). Until then, design each candidate **launcher-agnostic from day 1** so the future extract costs a day, not a month. "Real consumer" = an ecosystem app *actually importing* the module, not "planned". A `core/*` module that imports `app/*` is a build failure — that lint rule is the structural guarantee that extract stays possible.

**Extraction candidates & their unit**

| Candidate | Extraction unit when triggered | Owning doc |
|---|---|---|
| `:core:crypto` + `:core:keys` + `:crypto-ffi` | the **whole crypto core + FFI bridges as ONE versioned repo** (`family-crypto-kmp`, `git filter-repo` → private repo, published Maven artifact) — NOT per-module scatter | [`crypto-primitives.md`](crypto-primitives.md), [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md) |
| `:core:wire` (versioning) | rides with crypto or stands alone; it is the **barrier** that keeps crypto extractable (version handling was deliberately moved OUT of crypto per TASK-141) | [`wire-format.md`](wire-format.md) |
| `core/wizard/`, `core/localization/`, `core/ui-senior/` | UI/onboarding modules, launcher-agnostic; trigger = messenger or album importing them | `docs/product/glossary.md` §7a |
| `core/push/` | `com.familycare:push-client` Maven + `workers/push/` own repo | `docs/dev/server-roadmap.md` (SRV-PUSH-EXTRACTION / TODO-ARCH-018) |

**Invariants** (E1–E4, see §Invariants): E1 trigger = second REAL consumer. E2 crypto extracts as one repo, bridges included. E3 vendor/KMS adapters stay OUT of the extracted core. E4 **ECS is explicitly NOT an extraction candidate**.

**Rejected**: separate repo / Maven / JitPack now (premature, CI overhead, no consumer). See §Rejected.

**Routing**: "do we extract X / when / how" → stay here. What a module *contains* → its owning architecture doc above.

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **E1 — trigger = the second REAL consumer** (rule of three). One consumer → keep in-repo, design agnostic. Second consumer actually importing → *consider* extract. Third → mandatory. Diverging release cadence between apps is an independent trigger.
- **E2 — crypto extracts as one versioned repo, FFI bridges included** (libsignal / Wire core-crypto precedent). Do not scatter `:core:crypto` / `:core:keys` / `:crypto-ffi` across separate repos — the Rust core and its language bridges ship and version together.
- **E3 — vendor / KMS / storage adapters stay OUT of the extracted core** (Tink precedent: `tink` core is vendor-free; `tink-gcpkms` / `tink-awskms` are separate artifacts). Our extracted crypto core stays free of Firestore / Cloudflare / Android-app coupling; adapters remain in the consuming app.
- **E4 — ECS is NOT an extraction candidate.** The ECS core is deliberately concrete to our Component/Tag set — "a second consumer would be speculative" (task-136 contract, rule 4). Only the Fleks-shaped vocabulary at call-sites is preserved for swap-compatibility; there is no generic-ECS module to extract. (Contrast with crypto/versioning, which ARE designed for extraction.)

## Fitness function

Gradle lint: `core/*` modules must not import `app/*` — any such import fails the build. This is the automatic, structural guarantee that every candidate stays extractable without manual review (rule 7).

## Inline exit-ramp markers (grep-discoverable)

- `core/crypto/build.gradle.kts` — `TODO(extract-when-2nd-consumer)`: extract to `family-crypto-kmp` via `git filter-repo` on the second senior-launcher-family app.
- `core/wizard/`, `core/localization/`, `core/ui-senior/` — `// EXTRACT CANDIDATE` header (rule of three).
- Push: SRV-PUSH-EXTRACTION / TODO-ARCH-018 in `../dev/server-roadmap.md`.

## Rejected (do not re-litigate)

- ❌ Separate repo now — premature, breaks cross-app coordination with no compensating benefit.
- ❌ Maven / JitPack distribution before extract — CI overhead with no consumer.
- ❌ A standalone `extraction-candidates.md` list per domain — this single policy file + inline TODOs suffice; scattered lists drift.

## Industry grounding

- **libsignal**, **Wire core-crypto** — extracted as one Rust core repo with all language bridges co-located and versioned together (not per-module scatter). https://github.com/signalapp/libsignal , https://github.com/wireapp/core-crypto
- **Google Tink** — extracted after multiple consumers; later split KMS integrations into separate artifacts while the core stayed vendor-free. https://github.com/tink-crypto/tink-java-gcpkms
- **Rule of three** (Fowler, *Refactoring*) — extract on the second use, library on the third.

## Related

- Crypto: [`crypto.md`](crypto.md) (umbrella) → [`crypto-primitives.md`](crypto-primitives.md), [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md). Versioning barrier: [`wire-format.md`](wire-format.md). UI candidates: `docs/product/glossary.md` §7a. Server modules: [`../dev/server-roadmap.md`](../dev/server-roadmap.md).

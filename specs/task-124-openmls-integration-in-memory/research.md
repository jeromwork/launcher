# Research: openmls FFI-shape decision

Most of the MLS stack decision (openmls MIT, UniFFI dumb bridge, Wire-structure copy) is **already decided** in [crypto-mls.md](../../docs/architecture/crypto-mls.md) (THE BEACON — do NOT re-decide). This file records only the **one open design choice** this task resolved, plus the version verification, so a future session doesn't re-derive it.

## Verification (primary source: openmls-v0.8.1 clone + 2 web runs, 2026-07-23/24)

Confirmed against the actual tagged source (`openmls_repo/` @ `openmls-v0.8.1`):
- `openmls 0.8.1` exists, not yanked, latest 0.8.x. Compatible: `openmls_traits 0.5.0`, `openmls_rust_crypto 0.5.1`. MSRV well below our 1.97.
- `add_members(provider, signer, &[KeyPackage]) -> (MlsMessageOut commit, MlsMessageOut welcome, Option<GroupInfo>)` — welcome always present.
- `remove_members(provider, signer, &[LeafNodeIndex]) -> (commit, Option<welcome=None>, Option<GroupInfo>)`.
- `create_message(provider, signer, &[u8]) -> MlsMessageOut` — errors if pending proposals.
- `new_with_group_id(provider, signer, &MlsGroupCreateConfig, GroupId, CredentialWithKey)`.
- `MlsGroup::load(provider, group_id) -> Option<MlsGroup>`. `MlsGroup` itself does NOT derive Serialize (serde/tls_codec removed 0.8.x, PR #1637).

## Decision: FFI state shape — snapshot the StorageProvider, not the MlsGroup

**One-way-door-ish** (the FFI contract shape; changing it later touches every verb + TASK-125). Alternatives considered:

| Option | How | Verdict |
|--------|-----|---------|
| **A. Serialize `MlsGroup` in/out per call** | pass group bytes across FFI | ❌ **Impossible** — `MlsGroup` not serializable in 0.8.1. |
| **B. Hold a live `MlsGroup` in Rust across FFI calls** (handle/pointer, stateful FFI) | Rust keeps group objects keyed by id; Kotlin passes only id | ❌ Rejected: stateful FFI breaks the "dumb bridge" invariant (ML2), leaks lifecycle across the boundary, complicates the panic contract, and fights the future SQLCipher model (state must live in the StorageProvider, not ad-hoc Rust memory). |
| **C. Serialize the `StorageProvider` snapshot in/out per call** ✅ | Kotlin passes the whole storage snapshot; Rust deserializes → `MlsGroup::load` → op → serialize back | **CHOSEN.** Idiomatic for openmls 0.8.x (state is designed to live in the provider); keeps FFI stateless; the same trait seam is where TASK-125 swaps in SQLCipher (arch-pack ML3 + exit ramp). |

**Exit ramp**: at TASK-125 the in-memory snapshot is replaced by SQLCipher-backed `StorageProvider` behind the *same* openmls trait — the Kotlin `GroupPort` contract and the FFI verb shapes are unchanged (only the storage impl swaps). No wire-format break for callers.

**Grounding**: openmls book §Storage (state persisted via provider), Wire `core-crypto` mls-provider seam, RFC 9420. Full URLs in [crypto-mls.md](../../docs/architecture/crypto-mls.md) §Industry grounding.

## Signing key (Clarification #1)

Ephemeral `SignatureKeyPair` generated in-adapter for in-memory scope (openmls standard pattern) — TASK-124 stays unblocked (no dep on Paused TASK-112). Real key-hierarchy binding (`KeyVault.exportDerivedKey`) lands with persistence (TASK-125 + TASK-112). Acceptable because the whole in-memory group state is lost on reboot anyway.

---

## TL;DR для новичка

Почти всё про «какую крипто-библиотеку берём» уже решено в арх-паке — тут не переголосовываем. Единственный реальный выбор этой задачи: **как гонять состояние группы через мостик Rust↔Kotlin**. Оказалось, саму «группу» сохранить в байты нельзя (так устроена openmls 0.8.1) — состояние живёт в отдельном «хранилище», а группа собирается из него. Поэтому гоняем **снимок хранилища** (вариант C), а не группу. Бонус: ровно в это же место потом (TASK-125) подставится «долговременное» зашифрованное хранилище, ничего не ломая. Плюс подписной ключ пока временный — настоящий подключим позже вместе с хранилищем.

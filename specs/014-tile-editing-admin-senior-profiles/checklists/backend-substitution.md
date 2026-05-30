# Backend Substitution — spec 014

Generated: 2026-05-29.

CLAUDE.md rule 8 (server migration tracking). Project-Specific Direction §7.

## Backend touches in F-014

1. **Firestore documents**: `/admin-self-configs/{adminUid}/configs/{configName}/current` (F-014.1, after F-4).
2. **Google Sign-In** identity: `adminUid` для ownership (F-014.1, depends on F-4).
3. **Optimistic concurrency**: Firestore transactions для one-default invariant (FR-003a) + version-vector conflict detection (FR-016).
4. **No Cloud Functions / Cloud Storage / FCM in F-014 directly** (FCM governed спекой 007).

## Substitution readiness

- [x] **CHK001** Domain не depends on Firestore types. `ConfigEditor` port (спека 008) abstracts away. PASS.
- [x] **CHK002** Admin UID as `String` in domain (FR-003h) — not Firebase Auth User object. Wrap'ed by `AuthProvider` port (F-4). PASS conceptually (F-4 not yet built).
- [x] **CHK003** Optimistic concurrency — abstract concept; backend may implement via transactions (Firestore) OR via DB row version (own server) OR ETag (REST). Port не commit'ит к specific mechanism.

## Cost-of-swap analysis

If we wanted to move to own-server:
- **ConfigEditor adapter**: replace `FirestoreConfigEditor` with `RestConfigEditor`. ≤ 1 module. PASS.
- **AuthProvider adapter** (F-4 dependency): replace Google Sign-In with own auth. F-4 responsibility, not F-014.
- **Optimistic concurrency**: domain unchanged (version-vector в ConfigDocument). Server impl swap.
- **Cloudflare Worker** не used by F-014 directly (FCM путь governed спекой 007).
- **Estimated cost**: 1-2 weeks (mostly F-4 swap, F-014 adapter is trivial after).

Per CLAUDE.md rule 8 — substitution path: replace Firestore + Google Sign-In with own backend + own auth. **Same migration path как для всех existing specs** — F-014 doesn't add new substitution debt.

## docs/dev/server-roadmap.md entries

- [⚠️] **CHK004** Spec не явно adds to `docs/dev/server-roadmap.md`. **Improvement**: spec'у нужно добавить server-roadmap entry для F-014.1 phase: "Move named configs collection to own server when F-4 swap happens; trivial — already through ConfigEditor port".

## Inline TODOs

- [x] **CHK005** `TODO(server-roadmap):` comments — F-014 не has new "no-server shortcut" decision. Reuses existing Firestore via existing port. PASS.

## Atomicity / integrity

- [⚠️] **CHK006** FR-003a (single-default invariant) — "atomic Firestore transaction". This is **vendor-specific atomicity**. Own-server swap requires equivalent transaction support. **Acceptable**: most backends provide. **Note**: not a one-way door — alternative is "compensating update" pattern.
- [x] **CHK007** Conflict detection (FR-016) — version-vector in ConfigDocument. Vendor-agnostic.

## Reference-counting model (FR-003 lifecycle)

- [⚠️] **CHK008** ACTIVE → ORPHAN lifecycle (FR-003): config marked when 0 devices use it. Auto-GC deferred (TODO-FUTURE-SPEC-008, own-server prerequisite). Spec explicit about deferral. PASS per rule 8.
- [x] **CHK009** TODO-FUTURE-SPEC-008 entry maps to server-roadmap.md обязательство. Verify roadmap doc has this.

## Migration concerns (F-014.1 → own server)

- [x] **CHK010** Wire format (ConfigDocument v2 with named-config fields) is JSON-serializable. Vendor-agnostic. Server impl reads same JSON.
- [⚠️] **CHK011** Firestore Security Rules → own server endpoint authz: 1-to-1 mappable. Document explicitly in plan.md.

## Open items

1. **CHK004**: Add F-014 entries to `docs/dev/server-roadmap.md`. Plan.md or post-clarify task.
2. **CHK006**: One-default invariant atomic mechanism — explicit alternative documented for non-transactional backends.
3. **CHK008**: Verify `docs/dev/server-roadmap.md` has TODO-FUTURE-SPEC-008 entry.
4. **CHK011**: Firestore Rules → server endpoint mapping in plan.md.

**Verdict**: PASS. F-014 inherits substitution-readiness from спека 008 ConfigEditor port. Adds named-config persistence, but через same port — no new substitution debt. 4 minor doc-tracking items.

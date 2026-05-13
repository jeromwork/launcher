# firestore-tests — Spec 007 Security Rules test suite

Tests `../firestore.rules` against the Firebase Emulator using
`@firebase/rules-unit-testing` (T074).

## Prerequisites

- **Node 20+** and **npm 10+**.
- **JDK 21+** — firebase-tools 14+ requires it. If you only have JDK 8,
  the emulator will refuse to start with a clear error.
- No Cloudflare/Firebase login needed — emulator uses the local
  `demo-test` project.

## Run

```sh
cd firestore-tests
npm install
npm test
```

`npm test` wraps:

```sh
firebase emulators:exec --only firestore,auth --project demo-test "vitest run"
```

The emulator boots, runs the suite once, shuts down. Expected: ~21 tests
green covering every documented rule path (pairings create/read/claim/
delete, links create/read/immutable/delete, state/config/capabilities/
health subcollections, default-deny on /devices and unknown paths).

## What this exercises

| Scope | Tests |
|---|---|
| /pairings/{token} | managed-creates, unauth-blocked, spoofed-uid-blocked, claim flips false→true, double-claim blocked, claim-cannot-mutate-other-fields, only-managed-deletes, admin-can-read |
| /links/{linkId} | admin-creates-own, spoofed-adminId-blocked, both-parties-read, foreign-uid-blocked, immutable-post-create, only-managed-deletes |
| /links/{linkId}/state | managed-creates, admin-cannot-write, admin-reads, foreign-uid-blocked |
| /links/{linkId}/config | admin-writes, managed-cannot-write |
| /devices + default deny | both denied |

## Adding tests

Extend [rules.test.ts](./rules.test.ts) — the `testEnv.authenticatedContext(uid)`
and `testEnv.withSecurityRulesDisabled(...)` helpers cover the patterns
needed. Run `npm test` after each change.

## Deploy (T075)

```sh
firebase deploy --only firestore:rules
# requires `firebase login` first
```

The rules file is `../firestore.rules` referenced via this subproject's
`firebase.json`.

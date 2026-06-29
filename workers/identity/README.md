# workers/identity — Identity-init Worker (Spec task-6 F-5 Phase 4 Track B)

Single endpoint that mints a UUID v4 `stableId` on first sign-in, persists the
`uid ↔ stableId` binding in Firestore, and sets the Firebase custom claim so
all subsequent ID-tokens carry `claims.stableId`. This is what makes the
backup Worker (Track A) addressable by stableId rather than uid.

## Endpoint

```
POST /init-claim
Authorization: Bearer <Firebase ID-token>
Content-Type: application/json
Body: { "uid": "<firebase uid>" }

200 OK { "stableId": "<uuid v4>" }
400 MALFORMED_BODY | missing uid
401 INVALID_TOKEN
403 UID_MISMATCH (claims.uid != body.uid)
500 INTERNAL
```

**Idempotency**: by construction. First call generates a fresh UUID and
persists it; subsequent calls return the existing binding without re-setting
the claim. Caller is the Android client (`InitClaimClient.kt`, T668) and runs
once after the first successful Sign-In.

## Quick start

```bash
cd workers/identity
npm install
npm test                # 7 vitest tests, all PASS
```

## Production deploy (owner)

Owner must finish:

1. **Provision service account** with Identity Toolkit + Firestore admin
   roles. Download JSON.
2. **`wrangler secret put FIREBASE_SA_JSON`** with the full JSON document.
3. **Implement the REST-backed `FirebaseAdmin`** (TODO at the bottom of
   `src/firebase-admin.ts`). The Worker runtime cannot bundle the npm
   `firebase-admin` SDK — must use Identity Toolkit + Firestore REST APIs
   directly, with a Web Crypto–signed service-account JWT to obtain the
   OAuth access token.
4. **`wrangler deploy`**.

The unit-test surface uses `InMemoryFirebaseAdmin` so the indirection is
already correct.

## Why this is a SEPARATE Worker from backup/

Per `gemini-handoff.md DZ-5` (microservice boundary): each Worker owns one
domain concern, has its own bindings, and cross-Worker calls go through the
public HTTP surface, not through shared module state. Bundling identity +
backup would force a single deploy unit and shared rate-limit / R2 surface
that neither needs.

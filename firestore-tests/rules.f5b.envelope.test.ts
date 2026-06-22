// Spec 018 F-5b Firestore Security Rules test suite for envelope-pattern
// storage paths (Batch 4 + Batch 7 docs sync).
//
// Validates:
//   /users/{ownerUid}/data/{key}                      ← RemoteStorage envelope
//   /users/{uid}/devices/{deviceId}                   ← device document
//   /users/{uid}/devices/{deviceId}/pub-key/{docId}   ← X25519 pub key
//   /users/{ownerUid}/access-grants/{helperUid}       ← grant
//
// per contracts/envelope-v1.md + Batch 4 commit 8e327f9.
//
// Run (requires Firebase Emulator + Java 21+):
//
//   cd firestore-tests
//   npm test -- rules.f5b.envelope.test.ts

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { Bytes, doc, getDoc, setDoc, deleteDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, test } from "vitest";

const PROJECT_ID = "demo-f5b-envelope-test";
const ALICE_UID = "alice-stable-uuid-v4";
const BOB_UID = "bob-stable-uuid-v4";
const CAROL_UID = "carol-stable-uuid-v4";

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync(resolve(__dirname, "../firestore.rules"), "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  // 2026-06-22 — после правки firestore.rules `isOwner()` (теперь требует
  // identity-link join) тестовые UIDs должны иметь identity-link, иначе
  // проверка владения провалится. В тестах ALICE_UID/BOB_UID/CAROL_UID
  // используются ОДНОВРЕМЕННО как Firebase Auth UID (authenticatedContext)
  // и как stableId в paths — заводим тривиальный link "сам в себя"
  // (google_X → stableId X). В production это разные значения; для тестов
  // упрощение приемлемо, проверка корректности правила не теряется.
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const fs = ctx.firestore();
    for (const uid of [ALICE_UID, BOB_UID, CAROL_UID]) {
      await setDoc(doc(fs, `identity-links/google_${uid}`), {
        schemaVersion: 1,
        stableId: uid,
        createdAt: new Date(),
      });
    }
  });
});

// ─── Helpers ──────────────────────────────────────────────────────────

function b(size: number, fill = 0): Bytes {
  return Bytes.fromUint8Array(new Uint8Array(size).fill(fill));
}

/** Valid v1 Envelope shape (single recipient). */
function validEnvelope(
  recipientDeviceId = "alice-phone",
  overrides: Partial<Record<string, unknown>> = {}
) {
  return {
    schemaVersion: 1,
    algorithm: "envelope-xchacha20poly1305-x25519-v1",
    ciphertext: b(64, 0x11),
    nonce: b(24, 0x22),
    aad: b(32, 0x33),
    recipientKeys: { [recipientDeviceId]: b(80, 0x44) },
    logicalKey: "config/default",
    ...overrides,
  };
}

/** Valid v1 device pub-key shape (32-byte X25519). */
function validPubKey(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    schemaVersion: 1,
    pubKey: b(32, 0xab),
    algorithm: "x25519-raw-v1",
    ...overrides,
  };
}

/** Active grant from owner to helper. */
function activeGrant() {
  return {
    permissions: ["read", "write"],
    grantedAt: Date.now(),
    revokedAt: null,
  };
}

async function seedActiveGrant(ownerUid: string, helperUid: string) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(
      doc(ctx.firestore(), `users/${ownerUid}/access-grants/${helperUid}`),
      activeGrant()
    );
  });
}

// ─── /users/{ownerUid}/data/{key} — envelope storage ──────────────────

describe("envelope storage /users/{ownerUid}/data/{key}", () => {
  test("owner can create envelope in own namespace", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(
      setDoc(
        doc(alice, `users/${ALICE_UID}/data/cfg__default`),
        validEnvelope("alice-phone")
      )
    );
  });

  test("owner can read own envelope", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/data/cfg__default`),
        validEnvelope("alice-phone")
      );
    });
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(getDoc(doc(alice, `users/${ALICE_UID}/data/cfg__default`)));
  });

  test("stranger without grant denied read", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/data/cfg__default`),
        validEnvelope("alice-phone")
      );
    });
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertFails(getDoc(doc(bob, `users/${ALICE_UID}/data/cfg__default`)));
  });

  test("stranger without grant denied write", async () => {
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertFails(
      setDoc(
        doc(bob, `users/${ALICE_UID}/data/cfg__default`),
        validEnvelope("bob-phone")
      )
    );
  });

  test("helper with active grant can write to owner namespace", async () => {
    await seedActiveGrant(ALICE_UID, BOB_UID);
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertSucceeds(
      setDoc(
        doc(bob, `users/${ALICE_UID}/data/cfg__grannys-phone`),
        validEnvelope("bob-phone")
      )
    );
  });

  test("helper with active grant can read from owner namespace", async () => {
    await seedActiveGrant(ALICE_UID, BOB_UID);
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/data/cfg__grannys-phone`),
        validEnvelope("alice-phone")
      );
    });
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertSucceeds(
      getDoc(doc(bob, `users/${ALICE_UID}/data/cfg__grannys-phone`))
    );
  });

  test("revoked grant denies further access", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/access-grants/${BOB_UID}`),
        { ...activeGrant(), revokedAt: Date.now() }
      );
    });
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertFails(
      setDoc(
        doc(bob, `users/${ALICE_UID}/data/cfg__default`),
        validEnvelope("bob-phone")
      )
    );
  });

  test("anonymous user denied", async () => {
    const anon = testEnv.unauthenticatedContext().firestore();
    await assertFails(
      getDoc(doc(anon, `users/${ALICE_UID}/data/cfg__default`))
    );
  });

  test("owner update preserves monotonic schemaVersion", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/data/cfg__default`),
        validEnvelope("alice-phone", { schemaVersion: 2 })
      );
    });
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    // Try to downgrade to schemaVersion=1 → denied.
    await assertFails(
      setDoc(
        doc(alice, `users/${ALICE_UID}/data/cfg__default`),
        validEnvelope("alice-phone", { schemaVersion: 1 })
      )
    );
  });

  test("owner can delete own envelope", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/data/cfg__default`),
        validEnvelope("alice-phone")
      );
    });
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(deleteDoc(doc(alice, `users/${ALICE_UID}/data/cfg__default`)));
  });
});

// ─── /users/{uid}/devices/{deviceId}/pub-key/{docId} ───────────────────

describe("public key directory /users/{uid}/devices/{deviceId}/pub-key", () => {
  test("owner can publish own pub key", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(
      setDoc(
        doc(alice, `users/${ALICE_UID}/devices/alice-phone/pub-key/current`),
        validPubKey()
      )
    );
  });

  test("owner can read own devices", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/devices/alice-phone/pub-key/current`),
        validPubKey()
      );
    });
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(
      getDoc(doc(alice, `users/${ALICE_UID}/devices/alice-phone/pub-key/current`))
    );
  });

  test("stranger without grant denied to read pub key directory", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/devices/alice-phone/pub-key/current`),
        validPubKey()
      );
    });
    const carol = testEnv.authenticatedContext(CAROL_UID).firestore();
    await assertFails(
      getDoc(doc(carol, `users/${ALICE_UID}/devices/alice-phone/pub-key/current`))
    );
  });

  test("helper with grant can read owner's pub key directory", async () => {
    await seedActiveGrant(ALICE_UID, BOB_UID);
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/devices/alice-phone/pub-key/current`),
        validPubKey()
      );
    });
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertSucceeds(
      getDoc(doc(bob, `users/${ALICE_UID}/devices/alice-phone/pub-key/current`))
    );
  });

  test("helper with grant cannot WRITE owner's pub key", async () => {
    await seedActiveGrant(ALICE_UID, BOB_UID);
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertFails(
      setDoc(
        doc(bob, `users/${ALICE_UID}/devices/alice-phone/pub-key/current`),
        validPubKey()
      )
    );
  });

  test("owner can delete own pub key", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/devices/alice-phone/pub-key/current`),
        validPubKey()
      );
    });
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(
      deleteDoc(doc(alice, `users/${ALICE_UID}/devices/alice-phone/pub-key/current`))
    );
  });
});

// ─── /users/{ownerUid}/access-grants/{helperUid} ──────────────────────

describe("access grants /users/{ownerUid}/access-grants/{helperUid}", () => {
  test("owner can create grant for helper", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(
      setDoc(
        doc(alice, `users/${ALICE_UID}/access-grants/${BOB_UID}`),
        activeGrant()
      )
    );
  });

  test("helper can read own grant from owner namespace", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/access-grants/${BOB_UID}`),
        activeGrant()
      );
    });
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertSucceeds(
      getDoc(doc(bob, `users/${ALICE_UID}/access-grants/${BOB_UID}`))
    );
  });

  test("helper cannot read grant of OTHER helper", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/access-grants/${BOB_UID}`),
        activeGrant()
      );
    });
    const carol = testEnv.authenticatedContext(CAROL_UID).firestore();
    await assertFails(
      getDoc(doc(carol, `users/${ALICE_UID}/access-grants/${BOB_UID}`))
    );
  });

  test("helper cannot create/revoke grants on owner namespace", async () => {
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertFails(
      setDoc(
        doc(bob, `users/${ALICE_UID}/access-grants/${CAROL_UID}`),
        activeGrant()
      )
    );
  });

  test("owner can revoke grant", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/access-grants/${BOB_UID}`),
        activeGrant()
      );
    });
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(
      setDoc(
        doc(alice, `users/${ALICE_UID}/access-grants/${BOB_UID}`),
        { ...activeGrant(), revokedAt: Date.now() }
      )
    );
  });

  test("anonymous user denied to read or write any grant", async () => {
    const anon = testEnv.unauthenticatedContext().firestore();
    await assertFails(
      getDoc(doc(anon, `users/${ALICE_UID}/access-grants/${BOB_UID}`))
    );
    await assertFails(
      setDoc(
        doc(anon, `users/${ALICE_UID}/access-grants/${BOB_UID}`),
        activeGrant()
      )
    );
  });
});

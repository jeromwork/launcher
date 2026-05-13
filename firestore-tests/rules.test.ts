// Spec 007 Firestore Security Rules test suite (T074, plan.md §Risks R4).
//
// Validates every rule path in ../firestore.rules against the Firebase
// Emulator using @firebase/rules-unit-testing. Run via:
//
//   cd firestore-tests
//   npm install
//   npm test     # wraps `firebase emulators:exec ... vitest run`
//
// **Prerequisite**: Java 21+ (firebase-tools 14+ requires it).

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
  type RulesTestContext,
} from "@firebase/rules-unit-testing";
import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
  updateDoc,
} from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, test } from "vitest";

// ─── env setup ──────────────────────────────────────────────────────────

const PROJECT_ID = "demo-test";
const MANAGED_UID = "managed-uid-001";
const ADMIN_UID = "admin-uid-002";
const STRANGER_UID = "stranger-uid-009";
const LINK_ID = "link-aaa111";
const TOKEN = "A3KX9B";

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
});

function managedCtx(): RulesTestContext {
  return testEnv.authenticatedContext(MANAGED_UID);
}

function adminCtx(): RulesTestContext {
  return testEnv.authenticatedContext(ADMIN_UID);
}

function strangerCtx(): RulesTestContext {
  return testEnv.authenticatedContext(STRANGER_UID);
}

function unauthCtx(): RulesTestContext {
  return testEnv.unauthenticatedContext();
}

// `withSecurityRulesDisabled` seeds documents bypassing rules — used to
// arrange existing state before the assertion checks the rules path.
async function seedPairing(opts: { claimed: boolean }): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `pairings/${TOKEN}`), {
      schemaVersion: 1,
      pairingType: "admin-managed-link",
      managedDeviceId: "device-uuid",
      managedDeviceFirebaseUid: MANAGED_UID,
      claimed: opts.claimed,
      expiresAt: new Date(Date.now() + 5 * 60 * 1000),
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });
}

async function seedLink(): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `links/${LINK_ID}`), {
      schemaVersion: 1,
      adminId: ADMIN_UID,
      managedDeviceId: "device-uuid",
      managedDeviceFirebaseUid: MANAGED_UID,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });
}

// ─── /pairings/{token} ──────────────────────────────────────────────────

describe("/pairings/{token}", () => {

  test("managed_can_create_with_own_uid", async () => {
    const fs = managedCtx().firestore();
    await assertSucceeds(setDoc(doc(fs, `pairings/${TOKEN}`), {
      schemaVersion: 1,
      pairingType: "admin-managed-link",
      managedDeviceId: "device-uuid",
      managedDeviceFirebaseUid: MANAGED_UID,
      claimed: false,
      expiresAt: new Date(Date.now() + 5 * 60 * 1000),
    }));
  });

  test("unauthenticated_cannot_create_pairing", async () => {
    const fs = unauthCtx().firestore();
    await assertFails(setDoc(doc(fs, `pairings/${TOKEN}`), {
      schemaVersion: 1,
      managedDeviceFirebaseUid: "spoofed-uid",
      claimed: false,
      expiresAt: new Date(),
    }));
  });

  test("create_with_spoofed_managedUid_rejected", async () => {
    const fs = managedCtx().firestore();
    await assertFails(setDoc(doc(fs, `pairings/${TOKEN}`), {
      schemaVersion: 1,
      managedDeviceFirebaseUid: "different-uid",
      claimed: false,
      expiresAt: new Date(),
    }));
  });

  test("create_with_claimed_true_rejected", async () => {
    // Bypass: nobody should be able to short-circuit the claim flow.
    const fs = managedCtx().firestore();
    await assertFails(setDoc(doc(fs, `pairings/${TOKEN}`), {
      schemaVersion: 1,
      managedDeviceFirebaseUid: MANAGED_UID,
      claimed: true,
      expiresAt: new Date(),
    }));
  });

  test("admin_can_read_pairing", async () => {
    await seedPairing({ claimed: false });
    await assertSucceeds(getDoc(doc(adminCtx().firestore(), `pairings/${TOKEN}`)));
  });

  test("claim_transaction_flips_claimed_false_to_true", async () => {
    await seedPairing({ claimed: false });
    // Note: the real client flips claimed:true AS PART OF a Firestore
    // transaction that ALSO creates /links/{linkId}. The Security Rules
    // for /pairings only validate the field-level change; the link
    // creation is validated by /links rules in the same transaction.
    await assertSucceeds(updateDoc(doc(adminCtx().firestore(), `pairings/${TOKEN}`), {
      claimed: true,
    }));
  });

  test("double_claim_rejected", async () => {
    await seedPairing({ claimed: true });
    await assertFails(updateDoc(doc(adminCtx().firestore(), `pairings/${TOKEN}`), {
      claimed: true,
      // claimed already true; rules require false→true transition.
    }));
  });

  test("claim_cannot_mutate_managedDeviceId", async () => {
    await seedPairing({ claimed: false });
    await assertFails(updateDoc(doc(adminCtx().firestore(), `pairings/${TOKEN}`), {
      claimed: true,
      managedDeviceId: "attacker-swapped",
    }));
  });

  test("only_managed_can_delete_pairing", async () => {
    await seedPairing({ claimed: false });
    // Admin cannot delete (only managed owner per FR-008).
    await assertFails(deleteDoc(doc(adminCtx().firestore(), `pairings/${TOKEN}`)));
    // Managed can delete (decline / single-use cleanup).
    await assertSucceeds(deleteDoc(doc(managedCtx().firestore(), `pairings/${TOKEN}`)));
  });

});

// ─── /links/{linkId} ────────────────────────────────────────────────────

describe("/links/{linkId}", () => {

  test("admin_can_create_link_with_own_uid", async () => {
    await assertSucceeds(setDoc(doc(adminCtx().firestore(), `links/${LINK_ID}`), {
      schemaVersion: 1,
      adminId: ADMIN_UID,
      managedDeviceId: "device-uuid",
      managedDeviceFirebaseUid: MANAGED_UID,
    }));
  });

  test("create_with_spoofed_adminId_rejected", async () => {
    // Some other authenticated user cannot claim adminId for someone else.
    await assertFails(setDoc(doc(strangerCtx().firestore(), `links/${LINK_ID}`), {
      schemaVersion: 1,
      adminId: ADMIN_UID,
      managedDeviceId: "device-uuid",
      managedDeviceFirebaseUid: MANAGED_UID,
    }));
  });

  test("admin_and_managed_can_read_link", async () => {
    await seedLink();
    await assertSucceeds(getDoc(doc(adminCtx().firestore(), `links/${LINK_ID}`)));
    await assertSucceeds(getDoc(doc(managedCtx().firestore(), `links/${LINK_ID}`)));
  });

  test("foreign_uid_cannot_read_link", async () => {
    await seedLink();
    await assertFails(getDoc(doc(strangerCtx().firestore(), `links/${LINK_ID}`)));
  });

  test("link_root_doc_is_immutable", async () => {
    await seedLink();
    await assertFails(updateDoc(doc(adminCtx().firestore(), `links/${LINK_ID}`), {
      adminId: "swapped",
    }));
  });

  test("only_managed_can_delete_link", async () => {
    await seedLink();
    await assertFails(deleteDoc(doc(adminCtx().firestore(), `links/${LINK_ID}`)));
    await assertSucceeds(deleteDoc(doc(managedCtx().firestore(), `links/${LINK_ID}`)));
  });

});

// ─── subcollections ────────────────────────────────────────────────────

describe("/links/{linkId}/state", () => {

  test("managed_can_create_state_with_schemaVersion_1", async () => {
    await seedLink();
    await assertSucceeds(setDoc(doc(managedCtx().firestore(), `links/${LINK_ID}/state/current`), {
      schemaVersion: 1,
      appliedAt: serverTimestamp(),
      presetId: "simple-launcher",
      fcmToken: "fcm-tok",
    }));
  });

  test("admin_cannot_write_state", async () => {
    await seedLink();
    await assertFails(setDoc(doc(adminCtx().firestore(), `links/${LINK_ID}/state/current`), {
      schemaVersion: 1,
      appliedAt: serverTimestamp(),
      presetId: "simple-launcher",
    }));
  });

  test("admin_can_read_state", async () => {
    await seedLink();
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `links/${LINK_ID}/state/current`), {
        schemaVersion: 1, appliedAt: serverTimestamp(), presetId: "simple-launcher",
      });
    });
    await assertSucceeds(getDoc(doc(adminCtx().firestore(), `links/${LINK_ID}/state/current`)));
  });

  test("foreign_uid_cannot_read_state", async () => {
    await seedLink();
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `links/${LINK_ID}/state/current`), {
        schemaVersion: 1, appliedAt: serverTimestamp(), presetId: "simple-launcher",
      });
    });
    await assertFails(getDoc(doc(strangerCtx().firestore(), `links/${LINK_ID}/state/current`)));
  });

});

describe("/links/{linkId}/config", () => {

  test("admin_can_write_config", async () => {
    await seedLink();
    await assertSucceeds(setDoc(doc(adminCtx().firestore(), `links/${LINK_ID}/config/current`), {
      schemaVersion: 1,
      payload: {},
    }));
  });

  test("managed_cannot_write_config", async () => {
    await seedLink();
    await assertFails(setDoc(doc(managedCtx().firestore(), `links/${LINK_ID}/config/current`), {
      schemaVersion: 1,
      payload: {},
    }));
  });

});

describe("/devices and default deny", () => {

  test("devices_collection_denies_writes", async () => {
    await assertFails(setDoc(doc(managedCtx().firestore(), `devices/${MANAGED_UID}`), {
      foo: "bar",
    }));
  });

  test("random_collection_denies_writes", async () => {
    await assertFails(addDoc(collection(adminCtx().firestore(), "random-undeclared"), {
      foo: "bar",
    }));
  });

});

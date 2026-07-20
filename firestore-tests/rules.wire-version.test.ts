// TASK-138 — the versionOrder() helper in ../firestore.rules.
//
// Versions travel as dotted "MAJOR.MINOR" strings. Firestore Rules compare strings
// lexicographically, which orders "10.0" BELOW "9.0" — so every monotonic guard in the rules
// would invert once a format reaches double-digit MAJOR: a rollback would be permitted and a
// legitimate upgrade refused.
//
// versionOrder() parses the parts and compares numerically. These tests exercise it through a
// real collection rather than in isolation, because the point is that the rules engine actually
// supports split()/int() — if it did not, the guard would fail closed and nobody would notice
// until a v10 document existed.
//
// Run: cd firestore-tests && npm test

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, test } from "vitest";

const PROJECT_ID = "demo-test";
const MANAGED_UID = "managed-uid-001";
const ADMIN_UID = "admin-uid-002";
const LINK_ID = "link-version-test";

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
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `links/${LINK_ID}`), {
      schemaVersion: "1.0",
      minReaderVersion: "1.0",
      minWriterVersion: "1.0",
      adminId: ADMIN_UID,
      managedDeviceFirebaseUid: MANAGED_UID,
      managedDeviceId: "device-uuid",
    });
  });
});

/** Body matching the /state/{stateId} rule, at a given schemaVersion. */
function stateBody(schemaVersion: string) {
  return {
    schemaVersion,
    minReaderVersion: "1.0",
    minWriterVersion: "1.0",
    presetId: "simple-launcher",
    fcmToken: "token-abc",
  };
}

/** Seeds the state doc at a version, bypassing rules. */
async function seedState(schemaVersion: string): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(
      doc(ctx.firestore(), `links/${LINK_ID}/state/current`),
      stateBody(schemaVersion),
    );
  });
}

function stateRef() {
  return doc(
    testEnv.authenticatedContext(MANAGED_UID).firestore(),
    `links/${LINK_ID}/state/current`,
  );
}

describe("versionOrder() — numeric ordering of dotted versions", () => {
  test("upgrade across the single-to-double digit boundary is allowed", async () => {
    // "10.0" > "9.0" numerically, but "10.0" < "9.0" as text. A lexicographic guard would
    // refuse this legitimate upgrade.
    await seedState("9.0");
    await assertSucceeds(setDoc(stateRef(), stateBody("10.0")));
  });

  test("rollback across the same boundary is refused", async () => {
    // The security-critical direction. Lexicographically "9.0" > "10.0", so a string compare
    // would have ALLOWED this rollback.
    await seedState("10.0");
    await assertFails(setDoc(stateRef(), stateBody("9.0")));
  });

  test("ordinary upgrade is allowed", async () => {
    await seedState("1.0");
    await assertSucceeds(setDoc(stateRef(), stateBody("2.0")));
  });

  test("ordinary rollback is refused", async () => {
    await seedState("2.0");
    await assertFails(setDoc(stateRef(), stateBody("1.0")));
  });

  test("same version is allowed — a plain field update must not be blocked", async () => {
    await seedState("2.0");
    await assertSucceeds(setDoc(stateRef(), stateBody("2.0")));
  });

  test("MINOR is ordered numerically too", async () => {
    await seedState("2.9");
    await assertSucceeds(setDoc(stateRef(), stateBody("2.10")));
  });

  test("MINOR rollback is refused", async () => {
    await seedState("2.10");
    await assertFails(setDoc(stateRef(), stateBody("2.9")));
  });

  test("an unparseable version is refused rather than treated as zero", async () => {
    // int() throws on a pre-release token, the rule errors, and the write is denied. Fail closed
    // is the wanted outcome — the alternative is a document ordering below everything, which
    // could then never be rolled forward.
    await seedState("2.0");
    await assertFails(setDoc(stateRef(), stateBody("3.0-beta")));
  });
});

describe("hasValidVersionHeader() — anti-lockout ceiling", () => {
  test("a header demanding a newer reader than the rules know is refused", async () => {
    // Without this, a rogue client could write minReaderVersion "999.0" into a shared document
    // and every legitimate device — including the owner's — would refuse to read it, with no
    // client-side repair possible.
    await assertFails(
      setDoc(stateRef(), {
        ...stateBody("999.0"),
        minReaderVersion: "999.0",
        minWriterVersion: "999.0",
      }),
    );
  });

  test("a newer writer that keeps the reader requirement low is accepted", async () => {
    // schemaVersion is diagnostics (§3) — it may run ahead freely as long as nobody is locked out.
    await assertSucceeds(setDoc(stateRef(), stateBody("999.0")));
  });

  test("a header with the fields out of order is refused", async () => {
    await assertFails(
      setDoc(stateRef(), {
        ...stateBody("1.0"),
        minReaderVersion: "1.0",
        minWriterVersion: "2.0", // above schemaVersion — violates the §3 ordering invariant
      }),
    );
  });
});

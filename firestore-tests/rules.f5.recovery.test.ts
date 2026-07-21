// Spec 018 (F-5) Firestore Security Rules test suite (T125).
//
// Validates users/{uid}/recovery-key/{docId} и users/{uid}/config/{docId} rules
// per contracts/firestore-security-rules.md и FR-009, FR-028a.
//
// Запуск (требует Firebase Emulator + Java 21+):
//
//   cd firestore-tests
//   npm test

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { Bytes, doc, getDoc, setDoc, updateDoc, deleteDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, test } from "vitest";

const PROJECT_ID = "demo-f5-recovery-test";
const ALICE_UID = "alice-stable-uuid-v4";
const BOB_UID = "bob-stable-uuid-v4";

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

// ─── Helpers ──────────────────────────────────────────────────────────

function b(size: number, fill = 0): Bytes {
  return Bytes.fromUint8Array(new Uint8Array(size).fill(fill));
}

/** Valid v1 RecoveryVaultBlob with native Firestore types. */
function validVaultV1(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    // TASK-141 Part D: dotted three-field version header (was the bare integer schemaVersion: 1).
    schemaVersion: "1.0",
    minReaderVersion: "1.0",
    minWriterVersion: "1.0",
    algorithm: "argon2id-xchacha20poly1305-v1",
    kdfSalt: b(16, 0x42), // exactly 16 bytes
    kdfParams: {
      memoryKiB: 65_536,
      iterations: 3,
      parallelism: 1,
    },
    wrappedRootKey: b(48, 0xab), // 1..1024 OK
    nonce: b(24, 0x55), // exactly 24 bytes
    createdAt: 1_719_792_000_000,
    ...overrides,
  };
}

// ─── Recovery vault — owner-only access (FR-009) ────────────────────────

describe("F-5 recovery vault: owner-only access", () => {
  test("owner can create valid vault", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(
      setDoc(doc(alice, `users/${ALICE_UID}/recovery-key/main`), validVaultV1())
    );
  });

  test("owner can read own vault", async () => {
    // Seed via security-bypass context.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1()
      );
    });
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(getDoc(doc(alice, `users/${ALICE_UID}/recovery-key/main`)));
  });

  test("owner can delete own vault", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1()
      );
    });
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(deleteDoc(doc(alice, `users/${ALICE_UID}/recovery-key/main`)));
  });

  test("ANOTHER user CANNOT read Alice's vault", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1()
      );
    });
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertFails(getDoc(doc(bob, `users/${ALICE_UID}/recovery-key/main`)));
  });

  test("ANOTHER user CANNOT write Alice's vault", async () => {
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertFails(
      setDoc(doc(bob, `users/${ALICE_UID}/recovery-key/main`), validVaultV1())
    );
  });

  test("ANOTHER user CANNOT delete Alice's vault", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1()
      );
    });
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertFails(deleteDoc(doc(bob, `users/${ALICE_UID}/recovery-key/main`)));
  });

  test("anonymous (unauthenticated) cannot read vault", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1()
      );
    });
    const anon = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(anon, `users/${ALICE_UID}/recovery-key/main`)));
  });
});

// ─── Shape validation ───────────────────────────────────────────────────

describe("F-5 recovery vault: shape validation", () => {
  test("missing schemaVersion → reject", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    const invalid = { ...validVaultV1() };
    delete (invalid as any).schemaVersion;
    await assertFails(setDoc(doc(alice, `users/${ALICE_UID}/recovery-key/main`), invalid));
  });

  test("integer schemaVersion (pre-conversion form) → reject", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    // hasValidVersionHeader requires `schemaVersion is string`; the retired integer form fails it.
    await assertFails(
      setDoc(
        doc(alice, `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ schemaVersion: 1 })
      )
    );
  });

  test("minReaderVersion above the accepted ceiling → reject (anti-lockout)", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertFails(
      setDoc(
        doc(alice, `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ schemaVersion: "999.0", minReaderVersion: "999.0", minWriterVersion: "999.0" })
      )
    );
  });

  test("kdfSalt size != 16 → reject", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertFails(
      setDoc(
        doc(alice, `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ kdfSalt: b(8) }) // wrong size
      )
    );
  });

  test("nonce size != 24 → reject", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertFails(
      setDoc(
        doc(alice, `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ nonce: b(16) }) // wrong size
      )
    );
  });

  test("wrappedRootKey > 1024 bytes → reject", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertFails(
      setDoc(
        doc(alice, `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ wrappedRootKey: b(2048) })
      )
    );
  });

  test("algorithm string empty → reject", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertFails(
      setDoc(
        doc(alice, `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ algorithm: "" })
      )
    );
  });

  test("kdfParams missing memoryKiB → reject", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertFails(
      setDoc(
        doc(alice, `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ kdfParams: { iterations: 3, parallelism: 1 } })
      )
    );
  });
});

// ─── Schema downgrade protection (FR-028a, H-2) ────────────────────────

describe("F-5 recovery vault: H-2 schema downgrade protection (FR-028a)", () => {
  beforeEach(async () => {
    // Seed v2 vault.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ schemaVersion: "2.0" })
      );
    });
  });

  test("owner update with schemaVersion = current (2.0) → allow", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(
      setDoc(
        doc(alice, `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ schemaVersion: "2.0" })
      )
    );
  });

  test("owner update with schemaVersion > current (3.0) → allow (migration)", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(
      setDoc(
        doc(alice, `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ schemaVersion: "3.0" })
      )
    );
  });

  test("owner update with schemaVersion < current (1.0) → REJECT (downgrade attempt)", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertFails(
      setDoc(
        doc(alice, `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ schemaVersion: "1.0" })
      )
    );
  });

  // AC #6: the 9→10 boundary is exactly where a raw string >= inverts — "10.0" sorts BELOW "9.0"
  // lexicographically. versionOrder() compares numerically, so 10.0 must outrank 9.0 (upgrade allowed)
  // and the reverse must be blocked (downgrade rejected). Both directions asserted here on the real rule.
  test("owner update 9.0 → 10.0 → allow (versionOrder numeric, not lexicographic)", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ schemaVersion: "9.0" })
      );
    });
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(
      setDoc(
        doc(alice, `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ schemaVersion: "10.0" })
      )
    );
  });

  test("owner update 10.0 → 9.0 → REJECT (downgrade across the 9→10 boundary)", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ schemaVersion: "10.0" })
      );
    });
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertFails(
      setDoc(
        doc(alice, `users/${ALICE_UID}/recovery-key/main`),
        validVaultV1({ schemaVersion: "9.0" })
      )
    );
  });
});

// ─── Config blob — owner-only + downgrade protection ───────────────────

describe("F-5 config blob: owner-only + H-2 (FR-028a)", () => {
  test("owner can create config", async () => {
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertSucceeds(
      setDoc(doc(alice, `users/${ALICE_UID}/config/main`), {
        schemaVersion: 1,
        ciphertext: b(64),
        nonce: b(24),
        aad: b(32),
        algorithm: "xchacha20poly1305-v1",
      })
    );
  });

  test("another user CANNOT read Alice's config", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `users/${ALICE_UID}/config/main`), {
        schemaVersion: 1,
      });
    });
    const bob = testEnv.authenticatedContext(BOB_UID).firestore();
    await assertFails(getDoc(doc(bob, `users/${ALICE_UID}/config/main`)));
  });

  test("schemaVersion downgrade on config → REJECT", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `users/${ALICE_UID}/config/main`), {
        schemaVersion: 2,
      });
    });
    const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
    await assertFails(
      setDoc(doc(alice, `users/${ALICE_UID}/config/main`), { schemaVersion: 1 })
    );
  });
});

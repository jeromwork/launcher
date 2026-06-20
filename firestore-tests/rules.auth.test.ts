// Spec 017 (F-4 AuthProvider) Firestore Security Rules test suite (T801).
//
// Validates identity-links и users collection rules — спека 017 contracts
// identity-link-v1.md + firestore-security-rules.md.
//
// Запуск (требует Firebase Emulator + Java 21+):
//
//   cd firestore-tests
//   npm test    # wraps firebase emulators:exec ... vitest run

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, serverTimestamp, setDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, test } from "vitest";

// ─── env setup ──────────────────────────────────────────────────────────

const PROJECT_ID = "demo-auth-test";
// Per rule: `request.auth.uid == providerAccountId`. Поэтому MANAGED_UID
// "представляет" Google sub claim — в test environment Firebase Auth UID
// устанавливается тестом, в проде это будет реальный Google sub после
// SDK exchange.
const SUB_A = "108547295013826509471";
const SUB_B = "215394081764029518273";
const STABLE_ID_A = "550e8400-e29b-41d4-a716-446655440000";
const STABLE_ID_B = "660f9511-f30c-52e5-b827-557766551111";

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

// ─── identity-links/google_{sub} (single doc-id, google_ prefix per production code) ────────────────────────────────────────

describe("identity-links/google_{sub} (single doc-id)", () => {
  test("owner can create their own link with valid stableId", async () => {
    const ctx = testEnv.authenticatedContext(SUB_A);
    await assertSucceeds(
      setDoc(doc(ctx.firestore(), `identity-links/google_${SUB_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      }),
    );
  });

  test("owner cannot create a link with mismatched providerAccountId", async () => {
    const ctx = testEnv.authenticatedContext(SUB_A);
    await assertFails(
      setDoc(doc(ctx.firestore(), `identity-links/google_${SUB_B}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      }),
    );
  });

  test("owner cannot create a link with malformed stableId (not UUIDv4)", async () => {
    const ctx = testEnv.authenticatedContext(SUB_A);
    await assertFails(
      setDoc(doc(ctx.firestore(), `identity-links/google_${SUB_A}`), {
        schemaVersion: 1,
        stableId: "not-a-uuid",
        createdAt: serverTimestamp(),
      }),
    );
  });

  test("owner cannot create a link with wrong schemaVersion", async () => {
    const ctx = testEnv.authenticatedContext(SUB_A);
    await assertFails(
      setDoc(doc(ctx.firestore(), `identity-links/google_${SUB_A}`), {
        schemaVersion: 2,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      }),
    );
  });

  test("owner cannot create a link with extra fields", async () => {
    const ctx = testEnv.authenticatedContext(SUB_A);
    await assertFails(
      setDoc(doc(ctx.firestore(), `identity-links/google_${SUB_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
        extraField: "evil",
      }),
    );
  });

  test("stranger cannot read someone else's identity-link", async () => {
    // Seed: SUB_A's link.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `identity-links/google_${SUB_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      });
    });
    const stranger = testEnv.authenticatedContext(SUB_B);
    await assertFails(
      getDoc(doc(stranger.firestore(), `identity-links/google_${SUB_A}`)),
    );
  });

  test("owner can read their own identity-link", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `identity-links/google_${SUB_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      });
    });
    const owner = testEnv.authenticatedContext(SUB_A);
    await assertSucceeds(
      getDoc(doc(owner.firestore(), `identity-links/google_${SUB_A}`)),
    );
  });

  test("identity-link is immutable — update fails", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `identity-links/google_${SUB_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      });
    });
    const owner = testEnv.authenticatedContext(SUB_A);
    await assertFails(
      setDoc(doc(owner.firestore(), `identity-links/google_${SUB_A}`), {
        schemaVersion: 1,
        stableId: "9999",  // attempt to rotate stableId
        createdAt: serverTimestamp(),
      }),
    );
  });
});

// ─── users/{stableId} ───────────────────────────────────────────────────

describe("users/{stableId}", () => {
  test("owner with identity-link can create matching user doc", async () => {
    // Spec rules use `existsAfter` / `getAfter` — это означает что
    // identity-link и user doc должны создаться в одной batched write.
    // Поэтому используем withSecurityRulesDisabled чтобы seed identity-link,
    // потом проверяем что user create with matching stableId работает.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `identity-links/google_${SUB_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      });
    });
    const owner = testEnv.authenticatedContext(SUB_A);
    await assertSucceeds(
      setDoc(doc(owner.firestore(), `users/${STABLE_ID_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      }),
    );
  });

  test("stranger without identity-link cannot read user doc", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `identity-links/google_${SUB_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      });
      await setDoc(doc(ctx.firestore(), `users/${STABLE_ID_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      });
    });
    const stranger = testEnv.authenticatedContext(SUB_B);
    await assertFails(
      getDoc(doc(stranger.firestore(), `users/${STABLE_ID_A}`)),
    );
  });

  test("user doc create fails if identity-link missing", async () => {
    // Не зашедний identity-link doc для SUB_A → user create должен fail.
    const owner = testEnv.authenticatedContext(SUB_A);
    await assertFails(
      setDoc(doc(owner.firestore(), `users/${STABLE_ID_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      }),
    );
  });

  test("user doc create fails if stableId doesn't match identity-link", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `identity-links/google_${SUB_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      });
    });
    const owner = testEnv.authenticatedContext(SUB_A);
    await assertFails(
      setDoc(doc(owner.firestore(), `users/${STABLE_ID_B}`), {
        // Подменили stableId — не совпадает с identity-link → fail.
        schemaVersion: 1,
        stableId: STABLE_ID_B,
        createdAt: serverTimestamp(),
      }),
    );
  });

  test("user doc is immutable in F-4 — update fails", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `identity-links/google_${SUB_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      });
      await setDoc(doc(ctx.firestore(), `users/${STABLE_ID_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
      });
    });
    const owner = testEnv.authenticatedContext(SUB_A);
    await assertFails(
      setDoc(doc(owner.firestore(), `users/${STABLE_ID_A}`), {
        schemaVersion: 1,
        stableId: STABLE_ID_A,
        createdAt: serverTimestamp(),
        evilField: "tampered",
      }),
    );
  });
});

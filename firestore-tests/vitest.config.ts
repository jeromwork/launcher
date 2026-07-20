import { defineConfig } from "vitest/config";

// Tests run inside `firebase emulators:exec` — when vitest starts, the
// Firestore + Auth emulator processes are already up at
// localhost:8080 (firestore) and localhost:9099 (auth). The test util
// in rules.test.ts wires `initializeTestEnvironment` to those endpoints.

export default defineConfig({
  test: {
    include: ["**/*.test.ts"],
    environment: "node",
    testTimeout: 30_000, // emulator round-trips can be slow on cold start

    // Every suite shares ONE emulator and each calls clearFirestore() in beforeEach, so running
    // files in parallel lets one suite wipe another's seeded data mid-test. With four files this
    // happened to pass; adding a fifth surfaced it as failures scattered across unrelated suites.
    // Serial execution is the correct fix — the emulator is a single shared resource, not
    // something each worker owns a copy of.
    fileParallelism: false,
  },
});

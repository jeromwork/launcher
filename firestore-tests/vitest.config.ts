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
  },
});

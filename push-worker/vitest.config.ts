import { defineConfig } from "vitest/config";

// Spec 007 T068 — vitest runs Worker tests against Node's `fetch` (Node 20+
// has it built-in). We do NOT use `@cloudflare/vitest-pool-workers` here
// because our tests stub all upstream HTTP calls and don't need the
// workerd runtime; running on Node is faster and simpler.

export default defineConfig({
  test: {
    include: ["test/**/*.test.ts"],
    environment: "node",
    coverage: {
      provider: "v8",
      include: ["src/**/*.ts"],
    },
  },
});

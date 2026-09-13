import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const root = path.dirname(fileURLToPath(import.meta.url));

// Unit tests for the pure helpers, and components rendered to static markup (no DOM
// needed). "@/..." resolves to the project root, the same as the tsconfig paths
// entry, so tests import modules the way the app does.
export default defineConfig({
  resolve: {
    alias: { "@": root },
  },
  // the automatic JSX runtime, so a *.test.tsx needs no React import
  esbuild: { jsx: "automatic" },
  test: {
    environment: "node",
    // *.test.tsx too: this used to list *.test.ts alone, and a component test was never run
    include: ["**/*.test.ts", "**/*.test.tsx"],
    exclude: ["node_modules/**", ".next/**"],
  },
});

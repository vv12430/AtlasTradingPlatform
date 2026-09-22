import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: Object.fromEntries(
      ["portfolio", "risk", "accounting"].map((name, i) => [
        "/" + name,
        {
          target: "http://localhost:" + (8081 + i),
          rewrite: (p: string) => p.replace("/" + name, ""),
        },
      ]),
    ),
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    exclude: ["e2e/**", "node_modules/**"],
  },
});

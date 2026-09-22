import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  use: { baseURL: process.env.UI_URL || "http://localhost:5173" },
  timeout: 60000,
});

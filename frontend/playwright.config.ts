import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 30000,
  retries: 0,
  use: {
    baseURL: 'http://localhost:5173',
    headless: true,
  },
  webServer: [
    {
      command: 'cd ../backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev',
      url: 'http://localhost:8080/api/health',
      timeout: 60000,
      reuseExistingServer: true,
    },
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      timeout: 15000,
      reuseExistingServer: true,
    },
  ],
})

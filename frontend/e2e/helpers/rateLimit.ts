import { execSync } from 'node:child_process'

/**
 * Clear Redis state to reset rate-limit counters between E2E test runs.
 *
 * Auth endpoints rate-limit by IP (e.g. registration is 3/hour). When E2E
 * suites register many synthetic users in a loop, every test from #4 onwards
 * trips the limit and fails with "Too many registrations from your network."
 *
 * Calling this in test.beforeEach (file-level test.describe) restores a clean
 * baseline so each test sees an empty rate-limit counter.
 *
 * Assumes the local Docker stack is up (campus_redis container).
 */
export function resetRateLimits(): void {
  try {
    execSync('docker exec campus_redis redis-cli FLUSHDB', { stdio: 'pipe' })
  } catch (err) {
    // Don't fail the test if Redis isn't reachable — print and continue
    console.warn('[e2e] resetRateLimits skipped:', (err as Error).message)
  }
}

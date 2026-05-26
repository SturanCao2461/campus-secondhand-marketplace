import { execSync } from 'node:child_process'

/**
 * Read the freshly-issued email-verification token from Redis.
 *
 * Production flow: register → backend writes `auth:verify:{token}` → user clicks
 * the email link → frontend POSTs `/api/auth/verify-email`. There is no email
 * infrastructure in dev (ConsoleEmailService just logs), so E2E grabs the token
 * straight out of Redis.
 *
 * Assumes `resetRateLimits()` has already run in beforeEach (FLUSHDB), so the
 * only `auth:verify:*` key in Redis is the one just issued by the test's
 * register call. Throws if zero or multiple keys are found.
 *
 * Same shape as resetRateLimits (D-66) and markEmailVerified (D-76): a small
 * docker exec helper for state that lives outside the HTTP surface.
 */
export function getVerificationToken(): string {
  const raw = execSync('docker exec campus_redis redis-cli --no-raw KEYS "auth:verify:*"', {
    encoding: 'utf8',
  })
  // redis-cli formats multi-key replies as `N) "key"` — strip the array index
  // and surrounding quotes before filtering.
  const keys = raw
    .split('\n')
    .map(l =>
      l
        .trim()
        .replace(/^\d+\)\s*/, '')
        .replace(/^"|"$/g, '')
    )
    .filter(l => l.startsWith('auth:verify:'))
  if (keys.length !== 1) {
    throw new Error(
      `[e2e] expected exactly 1 auth:verify:* key in Redis, found ${keys.length}: ${keys.join(', ')}`
    )
  }
  return keys[0].slice('auth:verify:'.length)
}

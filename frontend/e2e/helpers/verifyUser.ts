import { execSync } from 'node:child_process'

/**
 * Mark an account as email-verified by going around the EmailVerificationService.
 *
 * The production flow is: register → backend issues a verification token to
 * Redis with a 24-hour TTL → an email is sent → the user clicks the link →
 * the frontend POSTs `/api/auth/verify-email` with the token → backend
 * flips `users.email_verified` to TRUE and deletes the Redis key.
 *
 * For E2E tests we want to assert the verified-only operations
 * (POST /api/listings, POST /api/conversations/{id}/messages) without
 * routing through real email infrastructure. Same shape as resetRateLimits
 * (D-66) — `docker exec` against the dev compose stack, MySQL connection
 * details are the dev-only credentials baked into infra/docker-compose.yml.
 *
 * Call this in a test right after registering a synthetic user.
 */
export function markEmailVerified(email: string): void {
  const safeEmail = email.replace(/'/g, "\\'")
  const sql = `UPDATE users SET email_verified = 1 WHERE email = '${safeEmail}'`
  try {
    execSync(
      `docker exec campus_mysql mysql -uappuser -papppassword campus_marketplace -e "${sql}"`,
      { stdio: 'pipe' }
    )
  } catch (err) {
    console.warn('[e2e] markEmailVerified skipped:', (err as Error).message)
  }
}

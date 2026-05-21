import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

/**
 * ESM-safe replacement for `__dirname` resolved relative to the e2e folder.
 *
 * Since the project's package.json sets "type": "module", `__dirname` is not
 * defined inside spec files. Use `e2eDir(import.meta.url, 'fixtures/...')`
 * to resolve fixtures from any spec.
 */
export function e2eFixture(specUrl: string, ...subPaths: string[]): string {
  const __filename = fileURLToPath(specUrl)
  const __dirnameLocal = dirname(__filename)
  return resolve(__dirnameLocal, ...subPaths)
}

import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    rules: {
      // react-hooks/set-state-in-effect (new in eslint-plugin-react-hooks 6.x) flags the
      // standard "loading + error + fetch" pattern at the top of an effect body. The pattern
      // is correct and widely used; React docs suggest TanStack Query / SWR as alternatives
      // but that is an architectural change beyond MVP scope. Keep as warn so it's visible
      // during dev without failing CI.
      'react-hooks/set-state-in-effect': 'warn',
      // exhaustive-deps occasionally flags intentional omissions (e.g. polling cleanup that
      // shouldn't restart on every state tick). Keep as warn.
      'react-hooks/exhaustive-deps': 'warn',
    },
  },
])

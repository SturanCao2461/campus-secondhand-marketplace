import { useEffect, useCallback } from 'react'
import { useBlocker } from 'react-router-dom'

export function useUnsavedChangesGuard(isDirty: boolean) {
  // Browser tab close / refresh
  useEffect(() => {
    if (!isDirty) return
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [isDirty])

  // React Router navigation
  const blocker = useBlocker(isDirty)

  useEffect(() => {
    if (blocker.state === 'blocked') {
      const leave = window.confirm('You have unsaved changes. Leave this page?')
      if (leave) blocker.proceed()
      else blocker.reset()
    }
  }, [blocker])
}

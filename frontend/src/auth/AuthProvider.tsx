import { useEffect, useState, type ReactNode } from 'react'
import { api, ApiError } from '../api/apiClient'
import { AuthContext, type AuthUser, type AuthContextValue } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = async () => {
    try {
      const me = await api.get<AuthUser>('/api/auth/me')
      setUser(me)
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) setUser(null)
      else throw e
    }
  }

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        await refresh()
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const login: AuthContextValue['login'] = async (email, password) => {
    const res = await api.post<{ user: AuthUser }>('/api/auth/login', { email, password })
    setUser(res.user)
  }

  const logout: AuthContextValue['logout'] = async () => {
    try {
      await api.post('/api/auth/logout')
    } catch {
      /* ignore */
    }
    setUser(null)
  }

  const register: AuthContextValue['register'] = async (email, password, nickname) => {
    await api.post('/api/auth/register', { email, password, nickname })
    await login(email, password)
  }

  const value: AuthContextValue = { user, loading, login, logout, register, refresh }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

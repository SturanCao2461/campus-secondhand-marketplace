import { createContext } from 'react'

export type AuthUser = {
  id: number
  email: string
  nickname: string
  emailVerified: boolean
}

export type AuthContextValue = {
  user: AuthUser | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  register: (email: string, password: string, nickname: string) => Promise<void>
  refresh: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

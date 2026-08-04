import { create } from 'zustand'

interface AuthState {
  token: string | null
  userId: string | null
  username: string | null
  login: (token: string, userId: string, username: string) => void
  logout: () => void
}

const STORAGE_KEY = 'openex_auth'

function loadInitial(): { token: string | null; userId: string | null; username: string | null } {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { token: null, userId: null, username: null }
    return JSON.parse(raw)
  } catch {
    return { token: null, userId: null, username: null }
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  ...loadInitial(),
  login: (token, userId, username) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token, userId, username }))
    set({ token, userId, username })
  },
  logout: () => {
    localStorage.removeItem(STORAGE_KEY)
    set({ token: null, userId: null, username: null })
  }
}))

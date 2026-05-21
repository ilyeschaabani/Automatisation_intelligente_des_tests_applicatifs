'use client'

import { useSyncExternalStore } from 'react'

import { getAccessToken } from '@/lib/auth-storage'

function subscribe(callback: () => void) {
  window.addEventListener('storage', callback)
  return () => window.removeEventListener('storage', callback)
}

function getSnapshot() {
  return getAccessToken()
}

function getServerSnapshot() {
  return null
}

export function useAuth() {
  const token = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)

  return {
    token,
    isAuthenticated: Boolean(token),
  }
}
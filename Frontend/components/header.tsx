'use client'

import { Search, ChevronDown, LogOut, User } from 'lucide-react'
import { Input } from '@/components/ui/input'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { ThemeToggle } from '@/components/theme-toggle'
import { NotificationBell } from '@/components/notification-bell'
import { useRouter } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'

type ProfileLike = Record<string, unknown>

function pickFirstString(obj: ProfileLike, keys: string[]): string | null {
  for (const key of keys) {
    const value = obj[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return null
}

function getProfileRoot(payload: unknown): ProfileLike | null {
  if (!payload || typeof payload !== 'object') return null
  const obj = payload as ProfileLike
  const nestedUser = obj.user
  if (nestedUser && typeof nestedUser === 'object') return nestedUser as ProfileLike
  return obj
}

function toInitials(name: string): string {
  const parts = name
    .split(/\s+/)
    .map((p) => p.trim())
    .filter(Boolean)
  const first = parts[0]?.[0] ?? 'A'
  const second = parts.length > 1 ? parts[parts.length - 1]?.[0] : parts[0]?.[1]
  return `${first}${second ?? 'B'}`.toUpperCase()
}

export function Header() {
  const router = useRouter()
  const [profile, setProfile] = useState<ProfileLike | null>(null)

  useEffect(() => {
    let cancelled = false

    const run = async () => {
      const debug = process.env.NODE_ENV !== 'production'

      const fetchProfile = async () => {
        const res = await fetch('/api/auth/profile', { credentials: 'include' })
        if (!res.ok) return { ok: false as const, status: res.status, data: null }
        const data = (await res.json().catch(() => null)) as unknown
        return { ok: true as const, status: res.status, data }
      }

      let result = await fetchProfile()
      if (!result.ok && result.status === 401) {
        if (debug) console.debug('[session] profile 401, trying refresh')
        await fetch('/api/auth/refreshToken', {
          method: 'POST',
          credentials: 'include',
        }).catch(() => null)
        result = await fetchProfile()
      }

      if (cancelled) return

      if (!result.ok) {
        if (debug) console.debug('[session] no profile', { status: result.status })
        setProfile(null)
        return
      }

      const root = getProfileRoot(result.data)
      if (debug) console.debug('[session] profile loaded', root)
      setProfile(root)
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [])

  const displayName = useMemo(() => {
    if (!profile) return 'Admin'
    return (
      pickFirstString(profile, ['fullName', 'name', 'displayName']) ||
      pickFirstString(profile, ['username', 'email']) ||
      'Admin'
    )
  }, [profile])

  const initials = useMemo(() => toInitials(displayName), [displayName])

  return (
    <header className="sticky top-0 z-20 bg-card border-b border-border">
      <div className="px-6 py-4 flex items-center justify-between gap-4">
        {/* Search */}
        <div className="hidden lg:flex flex-1 max-w-md relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground size-5" />
          <Input
            placeholder="Rechercher des tests, des campagnes…"
            className="pl-10 bg-secondary"
          />
        </div>

        {/* Right Actions */}
        <div className="flex items-center gap-4 ml-auto">
          {/* Notifications */}
          <NotificationBell userId={profile ? Number((profile as any).id ?? 0) : null} />

          <ThemeToggle />

          {/* Divider */}
          <div className="w-px h-6 bg-border hidden md:block" />

          {/* User Menu */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className="flex items-center gap-2 hover:bg-secondary px-3 py-2 rounded-lg transition-colors">
                <div className="w-8 h-8 bg-gradient-to-br from-primary to-accent rounded-full flex items-center justify-center">
                  <span className="text-xs font-bold text-primary-foreground">{initials}</span>
                </div>
                <span className="hidden sm:inline text-sm font-medium">{displayName}</span>
                <ChevronDown size={16} className="text-muted-foreground" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-48">
              <DropdownMenuItem onClick={() => router.push('/profile')}>
                <User className="mr-2 h-4 w-4" />
                Profil
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onClick={async () => {
                  await fetch('/api/auth/logout', { method: 'POST' }).catch(() => null)
                  router.push('/login')
                  router.refresh()
                }}
              >
                <LogOut className="mr-2 h-4 w-4" />
                Déconnexion
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </header>
  )
}

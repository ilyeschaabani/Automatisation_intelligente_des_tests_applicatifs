"use client"

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { GitHubIntegrationCard } from '@/components/profile/GitHubIntegrationCard'
import { useEffect, useMemo, useState } from 'react'

type ProfileLike = Record<string, unknown>

function getProfileRoot(payload: unknown): ProfileLike | null {
  if (!payload || typeof payload !== 'object') return null
  const obj = payload as ProfileLike
  const nestedUser = obj.user
  if (nestedUser && typeof nestedUser === 'object') return nestedUser as ProfileLike
  return obj
}

function pickFirstString(obj: ProfileLike, keys: string[]): string {
  for (const key of keys) {
    const value = obj[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

function toInitials(name: string): string {
  const parts = name
    .split(/\s+/)
    .map((p) => p.trim())
    .filter(Boolean)
  const first = parts[0]?.[0] ?? 'U'
  const second = parts.length > 1 ? parts[parts.length - 1]?.[0] : parts[0]?.[1]
  return `${first}${second ?? ''}`.toUpperCase()
}

type HistoryItem = {
  id?: string | number
  date?: string
  createdAt?: string
  action?: string
  type?: string
  description?: string
  ip?: string
  userAgent?: string
  [k: string]: unknown
}

async function safeJson(res: Response): Promise<unknown> {
  const text = await res.text().catch(() => '')
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return { message: text }
  }
}

export default function ProfilePage() {
  const [loading, setLoading] = useState(true)
  const [profile, setProfile] = useState<ProfileLike | null>(null)
  const [history, setHistory] = useState<HistoryItem[]>([])
  const [pageError, setPageError] = useState<string | null>(null)

  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')

  const [savingAccount, setSavingAccount] = useState(false)
  const [accountMsg, setAccountMsg] = useState<string | null>(null)

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [savingPassword, setSavingPassword] = useState(false)
  const [passwordMsg, setPasswordMsg] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const debug = process.env.NODE_ENV !== 'production'

    const load = async () => {
      setLoading(true)
      setPageError(null)

      const profileRes = await fetch('/api/auth/profile', {
        credentials: 'include',
        cache: 'no-store',
      })

      if (!profileRes.ok) {
        const data = await safeJson(profileRes)
        if (debug) console.debug('[profile] load failed', profileRes.status, data)
        if (!cancelled) {
          setProfile(null)
          setPageError(
            profileRes.status === 401
              ? 'You are not logged in.'
              : 'Unable to load your profile.',
          )
        }
        setLoading(false)
        return
      }

      const profileData = await profileRes.json().catch(() => null)
      const root = getProfileRoot(profileData)
      if (debug) console.debug('[profile] loaded', root)

      if (!cancelled) {
        setProfile(root)
        setFullName(root ? pickFirstString(root, ['fullName', 'name', 'displayName']) : '')
        setEmail(root ? pickFirstString(root, ['email']) : '')
        setUsername(root ? pickFirstString(root, ['username', 'login']) : '')
      }

      // History is optional; show empty state if backend doesn't support it.
      const historyRes = await fetch('/api/auth/history', {
        credentials: 'include',
        cache: 'no-store',
      }).catch(() => null)

      if (historyRes && historyRes.ok) {
        const historyData = await historyRes.json().catch(() => null)
        const items = Array.isArray(historyData)
          ? (historyData as HistoryItem[])
          : Array.isArray((historyData as any)?.items)
            ? ((historyData as any).items as HistoryItem[])
            : []
        if (!cancelled) setHistory(items)
        if (debug) console.debug('[profile] history loaded', items.length)
      } else if (historyRes) {
        const data = await safeJson(historyRes)
        if (debug) console.debug('[profile] history not available', historyRes.status, data)
      }

      setLoading(false)
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [])

  const displayName = useMemo(() => {
    if (!profile) return 'User'
    return (
      pickFirstString(profile, ['fullName', 'name', 'displayName']) ||
      pickFirstString(profile, ['username', 'email']) ||
      'User'
    )
  }, [profile])

  const initials = useMemo(() => toInitials(displayName), [displayName])

  const onSaveAccount = async () => {
    setSavingAccount(true)
    setAccountMsg(null)
    const debug = process.env.NODE_ENV !== 'production'

    try {
      const res = await fetch('/api/auth/updateProfile', {
        method: 'PATCH',
        credentials: 'include',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ fullName, name: fullName, email, username }),
      })

      if (!res.ok) {
        const data = await safeJson(res)
        if (debug) console.debug('[profile] update failed', res.status, data)
        setAccountMsg(
          res.status === 404
            ? 'Update endpoint not available on backend yet.'
            : 'Unable to save changes.',
        )
        return
      }

      setAccountMsg('Saved.')
      // Refresh header + page state
      const refreshed = await fetch('/api/auth/profile', { credentials: 'include', cache: 'no-store' })
      if (refreshed.ok) {
        const profileData = await refreshed.json().catch(() => null)
        const root = getProfileRoot(profileData)
        setProfile(root)
      }
    } finally {
      setSavingAccount(false)
    }
  }

  const onChangePassword = async () => {
    setSavingPassword(true)
    setPasswordMsg(null)
    const debug = process.env.NODE_ENV !== 'production'

    try {
      if (!newPassword || newPassword.length < 6) {
        setPasswordMsg('New password is too short.')
        return
      }
      if (newPassword !== confirmPassword) {
        setPasswordMsg('Passwords do not match.')
        return
      }

      const res = await fetch('/api/auth/changePassword', {
        method: 'POST',
        credentials: 'include',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ currentPassword, newPassword }),
      })

      if (!res.ok) {
        const data = await safeJson(res)
        if (debug) console.debug('[profile] change password failed', res.status, data)
        setPasswordMsg(
          res.status === 404
            ? 'Change password endpoint not available on backend yet.'
            : 'Unable to change password.',
        )
        return
      }

      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setPasswordMsg('Password updated.')
    } finally {
      setSavingPassword(false)
    }
  }

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6">
          <div className="flex items-center justify-between mb-8">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Profile</h1>
              <p className="text-muted-foreground mt-1">Manage your account</p>
            </div>
          </div>

          <div className="flex flex-col lg:flex-row gap-6">
            <div className="w-full max-w-4xl">
              <Card className="p-6">
                {loading ? (
                  <p className="text-sm text-muted-foreground">Loading…</p>
                ) : pageError ? (
                  <p className="text-sm text-destructive">{pageError}</p>
                ) : (
                  <div className="flex items-center gap-4">
                    <div className="w-12 h-12 bg-gradient-to-br from-primary to-accent rounded-full flex items-center justify-center">
                      <span className="text-sm font-bold text-primary-foreground">
                        {initials}
                      </span>
                    </div>
                    <div className="min-w-0">
                      <p className="text-lg font-semibold text-foreground truncate">
                        {displayName}
                      </p>
                      {email ? (
                        <p className="text-sm text-muted-foreground truncate">{email}</p>
                      ) : null}
                    </div>
                  </div>
                )}
              </Card>

              {/* Account */}
              <Card className="p-6 mt-6">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-lg font-bold text-foreground">Account</h2>
                    <p className="text-sm text-muted-foreground mt-1">
                      Update your basic information
                    </p>
                  </div>
                  <Button onClick={onSaveAccount} disabled={savingAccount || loading || Boolean(pageError)}>
                    {savingAccount ? 'Saving…' : 'Save changes'}
                  </Button>
                </div>

                {accountMsg ? (
                  <p className="text-sm mt-4 text-muted-foreground">{accountMsg}</p>
                ) : null}

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
                  <div className="space-y-2">
                    <Label htmlFor="fullName">Full name</Label>
                    <Input
                      id="fullName"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      placeholder="Your name"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="username">Username</Label>
                    <Input
                      id="username"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="Username"
                    />
                  </div>
                  <div className="space-y-2 md:col-span-2">
                    <Label htmlFor="email">Email</Label>
                    <Input
                      id="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="you@company.com"
                    />
                  </div>
                </div>
              </Card>

              {/* Security */}
              <Card className="p-6 mt-6">
                <div>
                  <h2 className="text-lg font-bold text-foreground">Security</h2>
                  <p className="text-sm text-muted-foreground mt-1">Change your password</p>
                </div>

                {passwordMsg ? (
                  <p className="text-sm mt-4 text-muted-foreground">{passwordMsg}</p>
                ) : null}

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
                  <div className="space-y-2 md:col-span-2">
                    <Label htmlFor="currentPassword">Current password</Label>
                    <Input
                      id="currentPassword"
                      type="password"
                      value={currentPassword}
                      onChange={(e) => setCurrentPassword(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="newPassword">New password</Label>
                    <Input
                      id="newPassword"
                      type="password"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="confirmPassword">Confirm new password</Label>
                    <Input
                      id="confirmPassword"
                      type="password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                    />
                  </div>
                </div>

                <div className="mt-6">
                  <Button onClick={onChangePassword} disabled={savingPassword || loading || Boolean(pageError)}>
                    {savingPassword ? 'Updating…' : 'Update password'}
                  </Button>
                </div>
              </Card>

              {/* History */}
              <Card className="p-6 mt-6">
                <div>
                  <h2 className="text-lg font-bold text-foreground">History</h2>
                  <p className="text-sm text-muted-foreground mt-1">Recent activity</p>
                </div>

                {history.length === 0 ? (
                  <p className="text-sm text-muted-foreground mt-4">No history available.</p>
                ) : (
                  <div className="mt-6 space-y-3">
                    {history.slice(0, 10).map((item, idx) => {
                      const when =
                        (typeof item.createdAt === 'string' && item.createdAt) ||
                        (typeof item.date === 'string' && item.date) ||
                        ''
                      const action =
                        (typeof item.action === 'string' && item.action) ||
                        (typeof item.type === 'string' && item.type) ||
                        'Activity'
                      const desc =
                        (typeof item.description === 'string' && item.description) ||
                        ''
                      return (
                        <Card key={String(item.id ?? idx)} className="p-4">
                          <div className="flex items-start justify-between gap-4">
                            <div className="min-w-0">
                              <p className="text-sm font-medium text-foreground">{action}</p>
                              {desc ? (
                                <p className="text-sm text-muted-foreground mt-1 break-words">{desc}</p>
                              ) : null}
                            </div>
                            {when ? (
                              <p className="text-xs text-muted-foreground whitespace-nowrap">{when}</p>
                            ) : null}
                          </div>
                        </Card>
                      )
                    })}
                  </div>
                )}
              </Card>
            </div>

            <div className="w-full lg:flex-1">
              <GitHubIntegrationCard />
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}

"use client"

import Link from 'next/link'
import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { GitHubIntegrationCard } from '@/components/profile/GitHubIntegrationCard'
import { useEffect, useMemo, useState } from 'react'
import {
  AtSign,
  Clock,
  Folder,
  KeyRound,
  Mail,
  UserCog,
} from 'lucide-react'
import { useUserRoles } from '@/hooks/use-roles'
import { getProjects, type Project } from '@/lib/api-client'
import { ROLE_COLORS, ROLE_DESCRIPTIONS, ROLE_LABELS, type GlobalRole } from '@/types/user-admin'

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
  const { roles } = useUserRoles()
  const [loading, setLoading] = useState(true)
  const [profile, setProfile] = useState<ProfileLike | null>(null)
  const [history, setHistory] = useState<HistoryItem[]>([])
  const [pageError, setPageError] = useState<string | null>(null)
  const [projects, setProjects] = useState<Project[]>([])
  const [projectsLoading, setProjectsLoading] = useState(true)

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
              ? 'Vous n\'êtes pas connecté.'
              : 'Impossible de charger votre profil.',
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

    const loadProjects = async () => {
      setProjectsLoading(true)
      try {
        const data = await getProjects()
        if (!cancelled) setProjects(Array.isArray(data) ? data : [])
      } catch {
        if (!cancelled) setProjects([])
      } finally {
        if (!cancelled) setProjectsLoading(false)
      }
    }

    void load()
    void loadProjects()
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

  const avatarUrl = useMemo(
    () => (profile ? pickFirstString(profile, ['imageUrl', 'avatarUrl', 'picture', 'image']) : ''),
    [profile],
  )

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
            ? 'Endpoint de mise à jour pas encore disponible côté backend.'
            : 'Impossible d\'enregistrer les modifications.',
        )
        return
      }

      setAccountMsg('Enregistré.')
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
        setPasswordMsg('Le nouveau mot de passe est trop court.')
        return
      }
      if (newPassword !== confirmPassword) {
        setPasswordMsg('Les mots de passe ne correspondent pas.')
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
            ? 'Endpoint de changement de mot de passe pas encore disponible côté backend.'
            : 'Impossible de changer le mot de passe.',
        )
        return
      }

      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setPasswordMsg('Mot de passe mis à jour.')
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
          <div className="max-w-6xl mx-auto space-y-6">
            {/* Hero banner */}
            <Card className="overflow-hidden">
              <div className="h-24 bg-gradient-to-r from-primary via-cyan-500 to-accent" />
              <div className="px-6 pb-6">
                {loading ? (
                  <div className="flex items-end gap-4">
                    <div className="-mt-10 h-20 w-20 animate-pulse rounded-full bg-muted ring-4 ring-card" />
                    <div className="mb-2 h-5 w-44 animate-pulse rounded bg-muted" />
                  </div>
                ) : pageError ? (
                  <p className="pt-6 text-sm text-destructive">{pageError}</p>
                ) : (
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                    <div className="flex items-end gap-4 min-w-0">
                      {avatarUrl ? (
                        <img
                          src={avatarUrl}
                          alt=""
                          className="-mt-10 h-20 w-20 rounded-full object-cover ring-4 ring-card shadow-sm"
                        />
                      ) : (
                        <div className="-mt-10 flex h-20 w-20 items-center justify-center rounded-full bg-gradient-to-br from-primary to-accent ring-4 ring-card shadow-sm">
                          <span className="text-xl font-bold text-primary-foreground">{initials}</span>
                        </div>
                      )}
                      <div className="min-w-0 pb-1">
                        <h1 className="truncate text-2xl font-bold text-foreground">{displayName}</h1>
                        <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
                          {email ? (
                            <span className="inline-flex items-center gap-1">
                              <Mail className="h-3.5 w-3.5" />
                              <span className="truncate">{email}</span>
                            </span>
                          ) : null}
                          {username ? (
                            <span className="inline-flex items-center gap-1">
                              <AtSign className="h-3.5 w-3.5" />
                              {username}
                            </span>
                          ) : null}
                        </div>
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-1.5 pb-1">
                      {roles.length > 0 ? (
                        roles.map((r) => (
                          <Badge key={r} variant="outline" className={ROLE_COLORS[r as GlobalRole] ?? ''}>
                            {ROLE_LABELS[r as GlobalRole] ?? r}
                          </Badge>
                        ))
                      ) : (
                        <Badge variant="outline">Aucun rôle</Badge>
                      )}
                    </div>
                  </div>
                )}
              </div>
            </Card>

            <div className="flex flex-col lg:flex-row gap-6">
              <div className="w-full space-y-6 lg:flex-1 min-w-0">
              {/* Account */}
              <Card className="p-6">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-lg font-bold text-foreground">Compte</h2>
                    <p className="text-sm text-muted-foreground mt-1">
                      Mettez à jour vos informations de base
                    </p>
                  </div>
                  <Button onClick={onSaveAccount} disabled={savingAccount || loading || Boolean(pageError)}>
                    {savingAccount ? 'Enregistrement…' : 'Enregistrer'}
                  </Button>
                </div>

                {accountMsg ? (
                  <p className="text-sm mt-4 text-muted-foreground">{accountMsg}</p>
                ) : null}

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
                  <div className="space-y-2">
                    <Label htmlFor="fullName">Nom complet</Label>
                    <Input
                      id="fullName"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      placeholder="Votre nom"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="username">Nom d'utilisateur</Label>
                    <Input
                      id="username"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="Nom d'utilisateur"
                    />
                  </div>
                  <div className="space-y-2 md:col-span-2">
                    <Label htmlFor="email">E-mail</Label>
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
              <Card className="p-6">
                <div className="flex items-center gap-2">
                  <KeyRound className="h-5 w-5 text-primary" />
                  <div>
                    <h2 className="text-lg font-bold text-foreground">Sécurité</h2>
                    <p className="text-sm text-muted-foreground mt-1">Changez votre mot de passe</p>
                  </div>
                </div>

                {passwordMsg ? (
                  <p className="text-sm mt-4 text-muted-foreground">{passwordMsg}</p>
                ) : null}

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
                  <div className="space-y-2 md:col-span-2">
                    <Label htmlFor="currentPassword">Mot de passe actuel</Label>
                    <Input
                      id="currentPassword"
                      type="password"
                      value={currentPassword}
                      onChange={(e) => setCurrentPassword(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="newPassword">Nouveau mot de passe</Label>
                    <Input
                      id="newPassword"
                      type="password"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="confirmPassword">Confirmer le nouveau mot de passe</Label>
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
                    {savingPassword ? 'Mise à jour…' : 'Modifier le mot de passe'}
                  </Button>
                </div>
              </Card>

              {/* History */}
              <Card className="p-6">
                <div className="flex items-center gap-2">
                  <Clock className="h-5 w-5 text-primary" />
                  <div>
                    <h2 className="text-lg font-bold text-foreground">Historique</h2>
                    <p className="text-sm text-muted-foreground mt-1">Activité récente</p>
                  </div>
                </div>

                {history.length === 0 ? (
                  <p className="text-sm text-muted-foreground mt-4">Aucun historique disponible.</p>
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

              <div className="w-full space-y-6 lg:w-96 lg:shrink-0">
                {/* Role & Projects */}
                <Card className="p-6">
                  <div className="flex items-center gap-2">
                    <UserCog className="h-5 w-5 text-primary" />
                    <div>
                      <h2 className="text-lg font-bold text-foreground">Rôle &amp; projets</h2>
                      <p className="text-sm text-muted-foreground">
                        Votre niveau d'accès et les projets dont vous faites partie.
                      </p>
                    </div>
                  </div>

                  {/* Roles */}
                  <div className="mt-5">
                    <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Rôle</p>
                    {roles.length > 0 ? (
                      <div className="mt-2 space-y-2">
                        {roles.map((r) => (
                          <div key={r} className="flex items-start gap-2">
                            <Badge variant="outline" className={ROLE_COLORS[r as GlobalRole] ?? ''}>
                              {ROLE_LABELS[r as GlobalRole] ?? r}
                            </Badge>
                            <span className="pt-0.5 text-xs text-muted-foreground">
                              {ROLE_DESCRIPTIONS[r] ?? ''}
                            </span>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="mt-2 text-sm text-muted-foreground">Aucun rôle attribué.</p>
                    )}
                  </div>

                  {/* Projects */}
                  <div className="mt-6">
                    <div className="flex items-center justify-between">
                      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Projets</p>
                      <Badge variant="secondary" className="rounded-full">{projects.length}</Badge>
                    </div>
                    {projectsLoading ? (
                      <p className="mt-2 text-sm text-muted-foreground">Chargement des projets…</p>
                    ) : projects.length === 0 ? (
                      <p className="mt-2 text-sm text-muted-foreground">
                        Vous ne faites partie d'aucun projet pour l'instant.
                      </p>
                    ) : (
                      <div className="mt-2 space-y-1.5">
                        {projects.map((p) => (
                          <Link
                            key={p.id}
                            href={`/projects/${p.id}`}
                            className="flex items-center gap-3 rounded-lg border border-border px-3 py-2 transition-colors hover:bg-muted/50"
                          >
                            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-secondary text-primary">
                              <Folder className="h-4 w-4" />
                            </span>
                            <div className="min-w-0 flex-1">
                              <p className="truncate text-sm font-medium text-foreground">{p.name}</p>
                              {p.repositoryUrl ? (
                                <p className="truncate text-xs text-muted-foreground">{p.repositoryUrl}</p>
                              ) : null}
                            </div>
                            <Badge variant="outline" className="shrink-0 font-mono text-[10px]">
                              {p.projectType}
                            </Badge>
                          </Link>
                        ))}
                      </div>
                    )}
                  </div>
                </Card>
              </div>
            </div>

            {/* GitHub — full width (wide repo table) */}
            <GitHubIntegrationCard />
          </div>
        </div>
      </main>
    </div>
  )
}

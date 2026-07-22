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
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  AtSign,
  Camera,
  Check,
  Clock,
  Eye,
  EyeOff,
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

function buildDisplayName(profile: ProfileLike | null): string {
  if (!profile) return 'Utilisateur'
  const nom = pickFirstString(profile, ['nom'])
  const prenom = pickFirstString(profile, ['prenom'])
  if (prenom && nom) return `${prenom} ${nom}`
  if (nom) return nom
  if (prenom) return prenom
  return pickFirstString(profile, ['email']) || 'Utilisateur'
}

function toInitials(profile: ProfileLike | null): string {
  if (!profile) return 'U'
  const nom = pickFirstString(profile, ['nom'])
  const prenom = pickFirstString(profile, ['prenom'])
  if (prenom && nom) return `${prenom[0]}${nom[0]}`.toUpperCase()
  if (nom) return nom.substring(0, 2).toUpperCase()
  if (prenom) return prenom.substring(0, 2).toUpperCase()
  const email = pickFirstString(profile, ['email'])
  if (email) return email.substring(0, 2).toUpperCase()
  return 'U'
}

type HistoryItem = {
  id?: string | number
  date?: string
  createdAt?: string
  action?: string
  type?: string
  description?: string
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

  const [nom, setNom] = useState('')
  const [prenom, setPrenom] = useState('')
  const [email, setEmail] = useState('')

  const [savingAccount, setSavingAccount] = useState(false)
  const [accountMsg, setAccountMsg] = useState<{ text: string; success: boolean } | null>(null)

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showCurrentPwd, setShowCurrentPwd] = useState(false)
  const [showNewPwd, setShowNewPwd] = useState(false)
  const [savingPassword, setSavingPassword] = useState(false)
  const [passwordMsg, setPasswordMsg] = useState<{ text: string; success: boolean } | null>(null)

  const [uploadingPhoto, setUploadingPhoto] = useState(false)
  const [imgError, setImgError] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    let cancelled = false

    const load = async () => {
      setLoading(true)
      setPageError(null)

      const profileRes = await fetch('/api/auth/profile', {
        credentials: 'include',
        cache: 'no-store',
      })

      if (!profileRes.ok) {
        await safeJson(profileRes)
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

      if (!cancelled) {
        setProfile(root)
        setNom(root ? pickFirstString(root, ['nom']) : '')
        setPrenom(root ? pickFirstString(root, ['prenom']) : '')
        setEmail(root ? pickFirstString(root, ['email']) : '')
      }

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
    return () => { cancelled = true }
  }, [])

  const displayName = useMemo(() => buildDisplayName(profile), [profile])
  const initials = useMemo(() => toInitials(profile), [profile])
  const avatarUrl = useMemo(
    () => (profile ? pickFirstString(profile, ['imageUrl', 'avatarUrl', 'picture', 'githubAvatarUrl']) : ''),
    [profile],
  )

  const onUploadPhoto = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    setUploadingPhoto(true)
    try {
      const formData = new FormData()
      formData.append('file', file)

      const res = await fetch('/api/auth/uploadPhoto', {
        method: 'POST',
        credentials: 'include',
        body: formData,
      })

      if (!res.ok) {
        const data = await safeJson(res)
        console.error('[profile] upload failed', data)
        return
      }

      const refreshed = await fetch('/api/auth/profile', { credentials: 'include', cache: 'no-store' })
      if (refreshed.ok) {
        const profileData = await refreshed.json().catch(() => null)
        setProfile(getProfileRoot(profileData))
        setImgError(false)
      }
    } finally {
      setUploadingPhoto(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const onSaveAccount = async () => {
    setSavingAccount(true)
    setAccountMsg(null)

    try {
      const res = await fetch('/api/auth/updateProfile', {
        method: 'PATCH',
        credentials: 'include',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ nom, prenom }),
      })

      if (!res.ok) {
        setAccountMsg({ text: 'Impossible d\'enregistrer les modifications.', success: false })
        return
      }

      setAccountMsg({ text: 'Profil mis à jour avec succès.', success: true })
      const refreshed = await fetch('/api/auth/profile', { credentials: 'include', cache: 'no-store' })
      if (refreshed.ok) {
        const profileData = await refreshed.json().catch(() => null)
        setProfile(getProfileRoot(profileData))
      }
    } finally {
      setSavingAccount(false)
    }
  }

  const onChangePassword = async () => {
    setSavingPassword(true)
    setPasswordMsg(null)

    try {
      if (!currentPassword) {
        setPasswordMsg({ text: 'Veuillez saisir votre mot de passe actuel.', success: false })
        return
      }
      if (!newPassword || newPassword.length < 6) {
        setPasswordMsg({ text: 'Le nouveau mot de passe doit contenir au moins 6 caractères.', success: false })
        return
      }
      if (newPassword !== confirmPassword) {
        setPasswordMsg({ text: 'Les mots de passe ne correspondent pas.', success: false })
        return
      }

      const res = await fetch('/api/auth/changePassword', {
        method: 'POST',
        credentials: 'include',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ currentPassword, newPassword }),
      })

      if (!res.ok) {
        const data = await safeJson(res) as any
        const msg = data?.message || (res.status === 403 ? 'Le mot de passe actuel est incorrect.' : 'Impossible de changer le mot de passe.')
        setPasswordMsg({ text: msg, success: false })
        return
      }

      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setPasswordMsg({ text: 'Mot de passe mis à jour avec succès.', success: true })
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
              <div className="h-28 bg-gradient-to-r from-primary via-cyan-500 to-accent" />
              <div className="px-6 pb-6">
                {loading ? (
                  <div className="flex items-end gap-4">
                    <div className="-mt-12 h-24 w-24 animate-pulse rounded-full bg-muted ring-4 ring-card" />
                    <div className="mb-2 h-5 w-44 animate-pulse rounded bg-muted" />
                  </div>
                ) : pageError ? (
                  <p className="pt-6 text-sm text-destructive">{pageError}</p>
                ) : (
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                    <div className="flex items-end gap-4 min-w-0">
                      {/* Avatar with upload overlay */}
                      <div className="relative -mt-12 group">
                        {avatarUrl && !imgError ? (
                          <img
                            src={avatarUrl}
                            alt=""
                            className="h-24 w-24 rounded-full object-cover ring-4 ring-card shadow-md"
                            onError={() => setImgError(true)}
                          />
                        ) : (
                          <div className="flex h-24 w-24 items-center justify-center rounded-full bg-gradient-to-br from-primary to-accent ring-4 ring-card shadow-md">
                            <span className="text-2xl font-bold text-primary-foreground">{initials}</span>
                          </div>
                        )}
                        <button
                          type="button"
                          onClick={() => fileInputRef.current?.click()}
                          disabled={uploadingPhoto}
                          className="absolute inset-0 flex items-center justify-center rounded-full bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
                        >
                          <Camera className="h-6 w-6 text-white" />
                        </button>
                        <input
                          ref={fileInputRef}
                          type="file"
                          accept="image/*"
                          className="hidden"
                          onChange={onUploadPhoto}
                        />
                        {uploadingPhoto && (
                          <div className="absolute inset-0 flex items-center justify-center rounded-full bg-black/60">
                            <div className="h-6 w-6 animate-spin rounded-full border-2 border-white border-t-transparent" />
                          </div>
                        )}
                      </div>
                      <div className="min-w-0 pb-1">
                        <h1 className="truncate text-2xl font-bold text-foreground">{displayName}</h1>
                        <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
                          {email && (
                            <span className="inline-flex items-center gap-1">
                              <Mail className="h-3.5 w-3.5" />
                              <span className="truncate">{email}</span>
                            </span>
                          )}
                          {profile && pickFirstString(profile, ['githubUsername']) && (
                            <span className="inline-flex items-center gap-1">
                              <AtSign className="h-3.5 w-3.5" />
                              {pickFirstString(profile, ['githubUsername'])}
                            </span>
                          )}
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
                        Mettez à jour vos informations personnelles
                      </p>
                    </div>
                    <Button onClick={onSaveAccount} disabled={savingAccount || loading || Boolean(pageError)} className="gap-2">
                      <Check size={16} />
                      {savingAccount ? 'Enregistrement…' : 'Enregistrer'}
                    </Button>
                  </div>

                  {accountMsg && (
                    <p className={`text-sm mt-4 ${accountMsg.success ? 'text-green-600 dark:text-green-400' : 'text-destructive'}`}>
                      {accountMsg.text}
                    </p>
                  )}

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
                    <div className="space-y-2">
                      <Label htmlFor="prenom">Prénom</Label>
                      <Input
                        id="prenom"
                        value={prenom}
                        onChange={(e) => setPrenom(e.target.value)}
                        placeholder="Votre prénom"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="nom">Nom</Label>
                      <Input
                        id="nom"
                        value={nom}
                        onChange={(e) => setNom(e.target.value)}
                        placeholder="Votre nom"
                      />
                    </div>
                    <div className="space-y-2 md:col-span-2">
                      <Label htmlFor="email">E-mail</Label>
                      <Input
                        id="email"
                        value={email}
                        disabled
                        className="bg-muted cursor-not-allowed"
                      />
                      <p className="text-xs text-muted-foreground">L'adresse e-mail ne peut pas être modifiée.</p>
                    </div>
                  </div>
                </Card>

                {/* Security */}
                <Card className="p-6">
                  <div className="flex items-center gap-3">
                    <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-primary/10 text-primary">
                      <KeyRound size={18} />
                    </div>
                    <div>
                      <h2 className="text-lg font-bold text-foreground">Sécurité</h2>
                      <p className="text-sm text-muted-foreground">Changez votre mot de passe</p>
                    </div>
                  </div>

                  {passwordMsg && (
                    <p className={`text-sm mt-4 ${passwordMsg.success ? 'text-green-600 dark:text-green-400' : 'text-destructive'}`}>
                      {passwordMsg.text}
                    </p>
                  )}

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
                    <div className="space-y-2 md:col-span-2">
                      <Label htmlFor="currentPassword">Mot de passe actuel</Label>
                      <div className="relative">
                        <Input
                          id="currentPassword"
                          type={showCurrentPwd ? 'text' : 'password'}
                          value={currentPassword}
                          onChange={(e) => setCurrentPassword(e.target.value)}
                          placeholder="••••••••"
                        />
                        <button
                          type="button"
                          onClick={() => setShowCurrentPwd(!showCurrentPwd)}
                          className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                        >
                          {showCurrentPwd ? <EyeOff size={16} /> : <Eye size={16} />}
                        </button>
                      </div>
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="newPassword">Nouveau mot de passe</Label>
                      <div className="relative">
                        <Input
                          id="newPassword"
                          type={showNewPwd ? 'text' : 'password'}
                          value={newPassword}
                          onChange={(e) => setNewPassword(e.target.value)}
                          placeholder="Min. 6 caractères"
                        />
                        <button
                          type="button"
                          onClick={() => setShowNewPwd(!showNewPwd)}
                          className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                        >
                          {showNewPwd ? <EyeOff size={16} /> : <Eye size={16} />}
                        </button>
                      </div>
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="confirmPassword">Confirmer le mot de passe</Label>
                      <Input
                        id="confirmPassword"
                        type="password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        placeholder="Retapez le mot de passe"
                      />
                      {newPassword && confirmPassword && newPassword !== confirmPassword && (
                        <p className="text-xs text-destructive">Les mots de passe ne correspondent pas.</p>
                      )}
                    </div>
                  </div>

                  <div className="mt-6">
                    <Button
                      onClick={onChangePassword}
                      disabled={savingPassword || loading || Boolean(pageError) || !currentPassword || !newPassword}
                      variant="outline"
                    >
                      {savingPassword ? 'Mise à jour…' : 'Modifier le mot de passe'}
                    </Button>
                  </div>
                </Card>

                {/* History */}
                {history.length > 0 && (
                  <Card className="p-6">
                    <div className="flex items-center gap-3">
                      <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-primary/10 text-primary">
                        <Clock size={18} />
                      </div>
                      <div>
                        <h2 className="text-lg font-bold text-foreground">Historique</h2>
                        <p className="text-sm text-muted-foreground">Activité récente</p>
                      </div>
                    </div>

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
                                {desc && (
                                  <p className="text-sm text-muted-foreground mt-1 break-words">{desc}</p>
                                )}
                              </div>
                              {when && (
                                <p className="text-xs text-muted-foreground whitespace-nowrap">{when}</p>
                              )}
                            </div>
                          </Card>
                        )
                      })}
                    </div>
                  </Card>
                )}
              </div>

              <div className="w-full space-y-6 lg:w-96 lg:shrink-0">
                {/* Role & Projects */}
                <Card className="p-6">
                  <div className="flex items-center gap-3">
                    <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-primary/10 text-primary">
                      <UserCog size={18} />
                    </div>
                    <div>
                      <h2 className="text-lg font-bold text-foreground">Rôle &amp; projets</h2>
                      <p className="text-sm text-muted-foreground">
                        Votre niveau d'accès et vos projets.
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
                      <p className="mt-2 text-sm text-muted-foreground">Chargement…</p>
                    ) : projects.length === 0 ? (
                      <p className="mt-2 text-sm text-muted-foreground">
                        Aucun projet pour l'instant.
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
                            <p className="truncate text-sm font-medium text-foreground min-w-0 flex-1">{p.name}</p>
                          </Link>
                        ))}
                      </div>
                    )}
                  </div>
                </Card>
              </div>
            </div>

            {/* GitHub — full width */}
            <GitHubIntegrationCard />
          </div>
        </div>
      </main>
    </div>
  )
}

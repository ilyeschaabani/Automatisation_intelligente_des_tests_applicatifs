'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'

import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

export async function apiFetch(
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  const baseUrl = process.env.NEXT_PUBLIC_API_URL
  if (!baseUrl) {
    throw new Error('NEXT_PUBLIC_API_URL is not configured')
  }

  const normalizedBase = baseUrl.replace(/\/+$/, '')
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const url = `${normalizedBase}${normalizedPath}`

  return fetch(url, {
    ...init,
    credentials: 'include',
    cache: init.cache ?? 'no-store',
    headers: {
      ...(init.headers ?? {}),
    },
  })
}

type GitHubMeResponse = {
  githubConnected: boolean
  githubId?: string
  githubUsername?: string
  githubAvatarUrl?: string
  githubTokenCreatedAt?: string
}

type RepoRow = {
  key: string
  name: string
  owner: string
  isPrivate: boolean
  url: string
  updatedAt?: string
}

type ConnectionState =
  | { kind: 'loading' }
  | { kind: 'notConnected' }
  | { kind: 'connected'; me: GitHubMeResponse }
  | { kind: 'unauthorized' }
  | { kind: 'error'; message: string }

type RepoState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'available'; repos: RepoRow[] }
  | { kind: 'unavailable' }
  | { kind: 'error'; message: string }

function safeFormatDate(value?: string): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function getInitials(username?: string): string {
  const u = (username ?? '').trim()
  if (!u) return 'GH'
  const first = u[0] ?? 'G'
  const second = u.length > 1 ? u[1] : 'H'
  return `${first}${second}`.toUpperCase()
}

function normalizeRepos(payload: unknown): RepoRow[] {
  const list: unknown[] = Array.isArray(payload)
    ? payload
    : payload && typeof payload === 'object' && Array.isArray((payload as any).repos)
      ? ((payload as any).repos as unknown[])
      : []

  return list
    .map((repo, idx) => {
      if (!repo || typeof repo !== 'object') return null
      const r = repo as any

      const name: string =
        typeof r.name === 'string'
          ? r.name
          : typeof r.full_name === 'string'
            ? String(r.full_name).split('/').slice(-1)[0]
            : ''

      const owner: string =
        typeof r.owner === 'string'
          ? r.owner
          : r.owner && typeof r.owner === 'object' && typeof r.owner.login === 'string'
            ? r.owner.login
            : typeof r.full_name === 'string'
              ? String(r.full_name).split('/')[0] ?? ''
              : ''

      const url: string =
        typeof r.html_url === 'string'
          ? r.html_url
          : typeof r.url === 'string'
            ? r.url
            : ''

      const isPrivate = Boolean(r.private)

      const updatedAt: string | undefined =
        typeof r.updated_at === 'string'
          ? r.updated_at
          : typeof r.updatedAt === 'string'
            ? r.updatedAt
            : undefined

      const key =
        typeof r.id === 'number' || typeof r.id === 'string'
          ? String(r.id)
          : `${owner}/${name || 'repo'}:${idx}`

      if (!name) return null
      return { key, name, owner, isPrivate, url, updatedAt }
    })
    .filter(Boolean) as RepoRow[]
}

export function GitHubIntegrationCard({
  className,
}: {
  className?: string
}) {
  const [connection, setConnection] = useState<ConnectionState>({ kind: 'loading' })
  const [repos, setRepos] = useState<RepoState>({ kind: 'idle' })
  const [disconnecting, setDisconnecting] = useState(false)

  const apiUrl = process.env.NEXT_PUBLIC_API_URL
  const connectUrl = useMemo(() => {
    if (!apiUrl) return ''
    return `${apiUrl.replace(/\/+$/, '')}/api/github/connect`
  }, [apiUrl])

  const fetchRepos = useCallback(async () => {
    setRepos({ kind: 'loading' })

    try {
      const res = await apiFetch('/api/github/repos')
      if (res.status === 404) {
        setRepos({ kind: 'unavailable' })
        return
      }
      if (!res.ok) {
        const text = await res.text().catch(() => '')
        setRepos({
          kind: 'error',
          message: `Unable to load repos (${res.status})${text ? `: ${text}` : ''}`,
        })
        return
      }

      const data = (await res.json().catch(() => null)) as unknown
      const normalized = normalizeRepos(data)
      setRepos({ kind: 'available', repos: normalized })
    } catch (err) {
      setRepos({ kind: 'error', message: (err as Error)?.message ?? 'Network error' })
    }
  }, [])

  const fetchMe = useCallback(async () => {
    setConnection({ kind: 'loading' })
    setRepos({ kind: 'idle' })

    try {
      const res = await apiFetch('/api/github/me')

      if (res.status === 401) {
        setConnection({ kind: 'unauthorized' })
        return
      }

      if (res.status === 404 || res.status === 400) {
        setConnection({ kind: 'notConnected' })
        return
      }

      if (!res.ok) {
        const text = await res.text().catch(() => '')
        setConnection({
          kind: 'error',
          message: `Unable to load GitHub status (${res.status})${text ? `: ${text}` : ''}`,
        })
        return
      }

      const data = (await res.json().catch(() => null)) as unknown
      if (!data || typeof data !== 'object') {
        setConnection({ kind: 'error', message: 'Unexpected response from server.' })
        return
      }

      const me = data as GitHubMeResponse

      if (!me.githubConnected) {
        setConnection({ kind: 'notConnected' })
        return
      }

      setConnection({ kind: 'connected', me })
      void fetchRepos()
    } catch (err) {
      setConnection({
        kind: 'error',
        message: (err as Error)?.message ?? 'Network error',
      })
    }
  }, [fetchRepos])

  useEffect(() => {
    void fetchMe()
  }, [fetchMe])

  const disconnect = useCallback(async () => {
    setDisconnecting(true)
    try {
      const res = await apiFetch('/api/github/disconnect', { method: 'POST' })

      if (res.status === 401) {
        setConnection({ kind: 'unauthorized' })
        setRepos({ kind: 'idle' })
        return
      }

      if (res.status === 204 || res.ok) {
        setConnection({ kind: 'notConnected' })
        setRepos({ kind: 'idle' })
        return
      }

      const text = await res.text().catch(() => '')
      setConnection({
        kind: 'error',
        message: `Unable to disconnect (${res.status})${text ? `: ${text}` : ''}`,
      })
    } catch (err) {
      setConnection({
        kind: 'error',
        message: (err as Error)?.message ?? 'Network error',
      })
    } finally {
      setDisconnecting(false)
    }
  }, [])

  const connectedMeta = connection.kind === 'connected' ? connection.me : null
  const tokenCreatedLabel = useMemo(() => {
    if (!connectedMeta?.githubTokenCreatedAt) return ''
    return safeFormatDate(connectedMeta.githubTokenCreatedAt)
  }, [connectedMeta?.githubTokenCreatedAt])

  return (
    <Card className={className}>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div>
          <CardTitle>GitHub Account</CardTitle>
          <CardDescription>
            Connect your GitHub account to enable repository access.
          </CardDescription>
        </div>

        <div className="flex items-center gap-2">
          {connection.kind === 'connected' ? (
            <Button
              variant="destructive"
              onClick={() => void disconnect()}
              disabled={disconnecting}
            >
              {disconnecting ? 'Disconnecting…' : 'Disconnect'}
            </Button>
          ) : null}
          <Button
            variant="outline"
            onClick={() => void fetchMe()}
            disabled={connection.kind === 'loading' || disconnecting}
          >
            {connection.kind === 'loading' ? 'Loading…' : 'Refresh'}
          </Button>
        </div>
      </CardHeader>

      <CardContent>
        {connection.kind === 'loading' ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : connection.kind === 'unauthorized' ? (
          <p className="text-sm text-muted-foreground">
            Please sign in to connect GitHub.
          </p>
        ) : connection.kind === 'error' ? (
          <div className="space-y-2">
            <p className="text-sm text-destructive">{connection.message}</p>
            <p className="text-sm text-muted-foreground">
              Make sure the backend is reachable and you are signed in.
            </p>
          </div>
        ) : connection.kind === 'notConnected' ? (
          <div className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Connect your GitHub account to allow access to your repositories
              (including private repos if permitted).
            </p>
            <div>
              <Button
                onClick={() => {
                  if (!connectUrl) return
                  window.location.href = connectUrl
                }}
                disabled={!connectUrl}
              >
                Connect GitHub Account
              </Button>
              {!connectUrl ? (
                <p className="text-xs text-muted-foreground mt-2">
                  Missing NEXT_PUBLIC_API_URL configuration.
                </p>
              ) : null}
            </div>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-3 min-w-0">
                <Avatar className="h-10 w-10">
                  <AvatarImage
                    src={connectedMeta?.githubAvatarUrl || undefined}
                    alt={connectedMeta?.githubUsername || 'GitHub avatar'}
                  />
                  <AvatarFallback>{getInitials(connectedMeta?.githubUsername)}</AvatarFallback>
                </Avatar>

                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <p className="text-sm font-medium text-foreground truncate">
                      {connectedMeta?.githubUsername || 'GitHub'}
                    </p>
                    <Badge variant="secondary">Connected</Badge>
                  </div>
                  {tokenCreatedLabel ? (
                    <p className="text-xs text-muted-foreground mt-1">
                      Token created: {tokenCreatedLabel}
                    </p>
                  ) : null}
                </div>
              </div>
            </div>

            <div>
              <h3 className="text-sm font-semibold text-foreground">Repositories</h3>
              <p className="text-sm text-muted-foreground mt-1">
                Available repos linked to your GitHub connection.
              </p>

              <div className="mt-4">
                {repos.kind === 'idle' || repos.kind === 'loading' ? (
                  <p className="text-sm text-muted-foreground">
                    {repos.kind === 'loading' ? 'Loading repos…' : 'Loading repos…'}
                  </p>
                ) : repos.kind === 'unavailable' ? (
                  <p className="text-sm text-muted-foreground">
                    Repo listing not available yet.
                  </p>
                ) : repos.kind === 'error' ? (
                  <p className="text-sm text-muted-foreground">{repos.message}</p>
                ) : repos.repos.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No repos found.</p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Name</TableHead>
                        <TableHead>Owner</TableHead>
                        <TableHead>Privacy</TableHead>
                        <TableHead>URL</TableHead>
                        <TableHead>Updated</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {repos.repos.map((repo) => (
                        <TableRow key={repo.key}>
                          <TableCell className="font-medium">{repo.name}</TableCell>
                          <TableCell>{repo.owner}</TableCell>
                          <TableCell>
                            <Badge variant={repo.isPrivate ? 'outline' : 'secondary'}>
                              {repo.isPrivate ? 'Private' : 'Public'}
                            </Badge>
                          </TableCell>
                          <TableCell className="max-w-[220px] truncate">
                            {repo.url ? (
                              <a
                                href={repo.url}
                                target="_blank"
                                rel="noreferrer"
                                className="text-primary hover:underline"
                              >
                                {repo.url}
                              </a>
                            ) : (
                              <span className="text-muted-foreground">—</span>
                            )}
                          </TableCell>
                          <TableCell className="whitespace-nowrap">
                            {repo.updatedAt ? safeFormatDate(repo.updatedAt) : '—'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

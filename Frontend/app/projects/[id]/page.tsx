'use client'

import Link from 'next/link'
import { useParams, useSearchParams } from 'next/navigation'
import { useEffect, useMemo, useRef, useState } from 'react'

import { CampaignCard } from '@/components/campaign-card'
import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'

import { Loader2, Copy } from 'lucide-react'

import { toast } from '@/hooks/use-toast'

import {
  getProjects,
  getProjectEndpoints,
  type EndpointDto,
  type Project,
} from '@/lib/api-client'

import { scanProject, ApiScannerError, type ScanProjectPayload } from '@/api/apiScannerClient'
import type { ApiContract, Framework } from '@/types/apiContract'

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

type RepoMetaState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'available'; owner?: string; privacy?: string; updatedAt?: string }
  | { kind: 'notConnected' }
  | { kind: 'unavailable' }
  | { kind: 'unauthorized' }
  | { kind: 'error'; message: string }

function normalizeRepoUrl(url: string): string {
  return String(url || '')
    .trim()
    .toLowerCase()
    .replace(/\.git$/, '')
    .replace(/\/+$/, '')
}

function parseOwnerFromRepoUrl(repositoryUrl: string): { owner?: string; repo?: string } {
  try {
    const url = new URL(repositoryUrl)
    const parts = url.pathname.split('/').filter(Boolean)
    const owner = parts[0]
    const repo = parts[1]?.replace(/\.git$/, '')
    return {
      owner: owner || undefined,
      repo: repo || undefined,
    }
  } catch {
    return {}
  }
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

async function githubApiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const baseUrl = process.env.NEXT_PUBLIC_API_URL
  if (!baseUrl) throw new Error('NEXT_PUBLIC_API_URL is not configured')

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

const campaignsForProjectSeed = [
  {
    name: 'Banking Mobile App - v2.5',
    type: 'Functional' as const,
    status: 'Running' as const,
    progress: 65,
    tests: 145,
    passed: 94,
    failed: 0,
    lastRun: '5 mins ago',
  },
  {
    name: 'Payment Gateway API Tests',
    type: 'API' as const,
    status: 'Completed' as const,
    progress: 100,
    tests: 89,
    passed: 87,
    failed: 2,
    lastRun: '2 hours ago',
  },
  {
    name: 'Regression Suite - Production',
    type: 'Regression' as const,
    status: 'Scheduled' as const,
    progress: 0,
    tests: 234,
    passed: 0,
    failed: 0,
    lastRun: 'Tomorrow 2:00 AM',
  },
  {
    name: 'UI Components - v3.0',
    type: 'Functional' as const,
    status: 'Failed' as const,
    progress: 85,
    tests: 98,
    passed: 84,
    failed: 14,
    lastRun: '30 mins ago',
  },
] as const

const statusStyle: Record<'Deployed' | 'Not deployed', string> = {
  Deployed: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  'Not deployed': 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

function frameworkBadgeVariant(value: Framework): 'default' | 'secondary' | 'outline' {
  if (value === 'UNKNOWN') return 'outline'
  return 'secondary'
}

function getScanPhaseLabel(elapsedSeconds: number, source: 'git' | 'local'): string {
  if (elapsedSeconds < 5) return 'Starting scan…'
  if (source === 'git') {
    if (elapsedSeconds < 40) return 'Cloning repo…'
    if (elapsedSeconds < 70) return 'Detecting framework…'
    return 'Extracting endpoints…'
  }
  if (elapsedSeconds < 20) return 'Detecting framework…'
  return 'Extracting endpoints…'
}

export default function ProjectDetailsPage({
}: {}) {
  const params = useParams<{ id?: string | string[] }>()
  const id = Array.isArray(params?.id) ? params?.id[0] : params?.id
  const searchParams = useSearchParams()
  const autoDiscoverRan = useRef(false)

  const [project, setProject] = useState<Project | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [repoMeta, setRepoMeta] = useState<RepoMetaState>({ kind: 'idle' })

  const [isScanOpen, setIsScanOpen] = useState(false)
  const [scanSource, setScanSource] = useState<'git' | 'local'>('git')
  const [scanRepoUrl, setScanRepoUrl] = useState('')
  const [scanProjectPath, setScanProjectPath] = useState('')

  const [isScanning, setIsScanning] = useState(false)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [scanResult, setScanResult] = useState<ApiContract | null>(null)
  const [scanError, setScanError] = useState<string | null>(null)
  const [scanErrorDetails, setScanErrorDetails] = useState<unknown>(null)
  const [rawOpen, setRawOpen] = useState(false)

  const [endpointBranch, setEndpointBranch] = useState('')
  const [endpointSearch, setEndpointSearch] = useState('')
  const [endpointMethod, setEndpointMethod] = useState<string>('ALL')
  const [endpointsState, setEndpointsState] = useState<
    | { kind: 'idle' }
    | { kind: 'loading' }
    | { kind: 'running'; attempt: number; maxAttempts: number }
    | { kind: 'done'; endpoints: EndpointDto[] }
    | { kind: 'error'; message: string }
  >({ kind: 'idle' })
  const pollTimeoutRef = useRef<number | null>(null)
  const pollAttemptRef = useRef(0)
  const pollInFlightRef = useRef(false)
  const [schemaDialog, setSchemaDialog] = useState<{
    open: boolean
    title: string
    schema: string | null
  }>({ open: false, title: '', schema: null })

  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    return () => {
      if (pollTimeoutRef.current) window.clearTimeout(pollTimeoutRef.current)
      pollTimeoutRef.current = null
      pollAttemptRef.current = 0
      pollInFlightRef.current = false
    }
  }, [])

  useEffect(() => {
    if (!id) {
      setProject(null)
      setIsLoading(false)
      return
    }

    let cancelled = false

    const run = async () => {
      setIsLoading(true)
      try {
        const projects = await getProjects()
        const found = projects.find((p) => String(p.id) === String(id))
        if (!cancelled) setProject(found ?? null)
      } catch (error) {
        console.error('Failed to load project details', error)
        if (!cancelled) setProject(null)
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [id])

  const projectType = useMemo(() => {
    if (!project) return '—'
    return String(project.projectType ?? '—')
  }, [project])

  const sourceType = useMemo(() => {
    if (!project) return '—'
    return String(project.sourceType ?? '—')
  }, [project])

  const repositoryUrl = useMemo(() => {
    if (!project) return ''
    return String(project.repositoryUrl ?? '')
  }, [project])

  const defaultBranchLabel = useMemo(() => {
    if (!project) return ''
    const v = String(project.defaultBranch ?? '').trim()
    return v
  }, [project])

  const filteredEndpoints = useMemo(() => {
    const list = endpointsState.kind === 'done' ? endpointsState.endpoints : []
    const search = endpointSearch.trim().toLowerCase()
    return list.filter((e) => {
      if (endpointMethod !== 'ALL' && String(e.method).toUpperCase() !== endpointMethod) {
        return false
      }
      if (search) {
        const path = String(e.path ?? '').toLowerCase()
        if (!path.includes(search)) return false
      }
      return true
    })
  }, [endpointsState, endpointMethod, endpointSearch])

  const methodOptions = useMemo(() => {
    const list = endpointsState.kind === 'done' ? endpointsState.endpoints : []
    const methods = Array.from(
      new Set(list.map((e) => String(e.method ?? '').toUpperCase()).filter(Boolean)),
    ).sort()
    return ['ALL', ...methods]
  }, [endpointsState])

  const runDiscovery = async (projectId: number, branch?: string) => {
    const data = await getProjectEndpoints(projectId, branch)
    return Array.isArray(data) ? data : []
  }

  const resolveEffectiveBranch = (explicit: string | undefined, projectDefault: string | null | undefined) => {
    const typed = String(explicit ?? '').trim()
    if (typed) return typed

    const fallback = String(projectDefault ?? '').trim()
    if (fallback) return fallback

    return 'main'
  }

  const schedulePoll = (fn: () => void, delayMs: number) => {
    if (pollTimeoutRef.current) window.clearTimeout(pollTimeoutRef.current)
    pollTimeoutRef.current = window.setTimeout(fn, delayMs)
  }

  const clearPolling = () => {
    if (pollTimeoutRef.current) window.clearTimeout(pollTimeoutRef.current)
    pollTimeoutRef.current = null
    pollAttemptRef.current = 0
    pollInFlightRef.current = false
  }

  const nextDelayMs = (attempt: number) => {
    if (attempt <= 1) return 2000
    if (attempt === 2) return 3000
    return 5000
  }

  const discoverEndpoints = async (options?: { branch?: string }) => {
    if (!project) return
    if (!project.repositoryUrl) return

    clearPolling()

    const explicitBranch = String(options?.branch ?? endpointBranch).trim() || undefined
    const effectiveBranch = resolveEffectiveBranch(explicitBranch, project.defaultBranch)
    const maxAttempts = 60

    const step = async () => {
      if (pollInFlightRef.current) {
        schedulePoll(() => void step(), nextDelayMs(pollAttemptRef.current || 1))
        return
      }

      pollInFlightRef.current = true
      pollAttemptRef.current += 1
      const attempt = pollAttemptRef.current

      try {
        const endpoints = await runDiscovery(project.id, effectiveBranch)

        if (endpoints.length > 0) {
          clearPolling()
          setEndpointsState({ kind: 'done', endpoints })
          return
        }

        if (attempt >= maxAttempts) {
          clearPolling()
          setEndpointsState({
            kind: 'error',
            message: 'Timed out waiting for discovery to finish. Please try again.',
          })
          return
        }

        setEndpointsState({ kind: 'running', attempt, maxAttempts })
        schedulePoll(() => void step(), nextDelayMs(attempt))
      } catch (err) {
        clearPolling()
        setEndpointsState({
          kind: 'error',
          message: err instanceof Error ? err.message : 'Failed to load endpoints',
        })
      } finally {
        pollInFlightRef.current = false
      }
    }

    setEndpointsState({ kind: 'loading' })
    pollAttemptRef.current = 0
    void step()
  }

  useEffect(() => {
    const shouldDiscover = searchParams?.get('discover') === '1'
    if (!shouldDiscover) return
    if (!project?.id) return
    if (!project.repositoryUrl) return
    if (autoDiscoverRan.current) return

    autoDiscoverRan.current = true

    const branch = searchParams?.get('branch')
    if (branch && branch.trim()) setEndpointBranch(branch.trim())

    void discoverEndpoints({ branch: branch && branch.trim() ? branch.trim() : undefined })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [project?.id, project?.repositoryUrl])

  useEffect(() => {
    if (!isScanOpen) return

    setScanError(null)
    setScanErrorDetails(null)
    setScanResult(null)
    setRawOpen(false)

    // Prefill sensible defaults.
    const inferredRepoUrl = repositoryUrl
    if (inferredRepoUrl) setScanRepoUrl((prev) => prev || inferredRepoUrl)

    const projectSourceType = String(project?.sourceType ?? '')
    if (projectSourceType === 'LOCAL') setScanSource('local')
    else setScanSource('git')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isScanOpen])

  useEffect(() => {
    if (!isScanning) {
      setElapsedSeconds(0)
      return
    }

    const startedAt = Date.now()
    const id = window.setInterval(() => {
      const seconds = Math.floor((Date.now() - startedAt) / 1000)
      setElapsedSeconds(seconds)
    }, 1000)

    return () => window.clearInterval(id)
  }, [isScanning])

  const connectUrl = useMemo(() => {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) return ''
    return `${apiUrl.replace(/\/+$/, '')}/api/github/connect`
  }, [])

  useEffect(() => {
    const currentProject = project
    const repoUrl = repositoryUrl
    if (!currentProject) {
      setRepoMeta({ kind: 'idle' })
      return
    }

    if (String(currentProject.sourceType ?? '') !== 'GIT') {
      setRepoMeta({ kind: 'idle' })
      return
    }

    if (!repoUrl) {
      setRepoMeta({ kind: 'idle' })
      return
    }

    let cancelled = false

    const run = async () => {
      setRepoMeta({ kind: 'loading' })

      const parsed = parseOwnerFromRepoUrl(repoUrl)

      try {
        const meRes = await githubApiFetch('/api/github/me')

        if (meRes.status === 401) {
          if (!cancelled) setRepoMeta({ kind: 'unauthorized' })
          return
        }

        if (meRes.status === 404 || meRes.status === 400) {
          if (!cancelled) setRepoMeta({ kind: 'notConnected' })
          return
        }

        if (!meRes.ok) {
          const text = await meRes.text().catch(() => '')
          if (!cancelled) {
            setRepoMeta({
              kind: 'error',
              message: `Unable to load GitHub status (${meRes.status})${text ? `: ${text}` : ''}`,
            })
          }
          return
        }

        const meData = (await meRes.json().catch(() => null)) as unknown
        if (!meData || typeof meData !== 'object') {
          if (!cancelled) setRepoMeta({ kind: 'error', message: 'Unexpected GitHub status response.' })
          return
        }

        const me = meData as GitHubMeResponse
        if (!me.githubConnected) {
          if (!cancelled) setRepoMeta({ kind: 'notConnected' })
          return
        }

        const reposRes = await githubApiFetch('/api/github/repos')

        if (reposRes.status === 401) {
          if (!cancelled) setRepoMeta({ kind: 'unauthorized' })
          return
        }

        if (reposRes.status === 404) {
          // Backend semantics: 404 means GitHub is not connected.
          if (!cancelled) setRepoMeta({ kind: 'notConnected' })
          return
        }

        if (!reposRes.ok) {
          const text = await reposRes.text().catch(() => '')
          if (!cancelled) {
            setRepoMeta({
              kind: 'error',
              message: `Unable to load repos (${reposRes.status})${text ? `: ${text}` : ''}`,
            })
          }
          return
        }

        const reposData = (await reposRes.json().catch(() => null)) as unknown
        const normalized = normalizeRepos(reposData)

        const target = normalizeRepoUrl(repoUrl)
        const match = normalized.find((r) => normalizeRepoUrl(r.url) === target) ?? null

        const owner = match?.owner || parsed.owner
        const privacy = match ? (match.isPrivate ? 'Private' : 'Public') : undefined
        const updatedAt = match?.updatedAt

        if (!cancelled) {
          setRepoMeta({
            kind: 'available',
            owner,
            privacy,
            updatedAt,
          })
        }
      } catch (error) {
        if (!cancelled) {
          setRepoMeta({
            kind: 'available',
            owner: parsed.owner,
          })
        }
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [project, repositoryUrl])

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-4xl">
          <div className="flex items-center justify-between mb-8">
            <div>
              <h1 className="text-3xl font-bold text-foreground">
                {project?.name ?? 'Project'}
              </h1>
              <p className="text-muted-foreground mt-1">ID: {id ?? '—'}</p>
            </div>

            <Button asChild variant="outline">
              <Link href="/projects">Back to projects</Link>
            </Button>
          </div>

          <Card>
            {isLoading ? (
              <CardContent className="pt-6">
                <p className="text-sm text-muted-foreground">Loading…</p>
              </CardContent>
            ) : !project ? (
              <CardContent className="pt-6">
                <p className="text-sm text-muted-foreground">Not found</p>
              </CardContent>
            ) : (
              <>
                <CardHeader>
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0 space-y-1">
                      <CardTitle className="truncate">{project.name}</CardTitle>
                      <CardDescription>
                        {project.repositoryUrl ? 'Repository linked' : 'No repository'}
                      </CardDescription>
                    </div>
                    <Badge
                      variant="outline"
                      className={statusStyle[project.deployed ? 'Deployed' : 'Not deployed']}
                    >
                      {project.deployed ? 'Deployed' : 'Not deployed'}
                    </Badge>
                  </div>

                  <div className="flex flex-wrap gap-2 pt-2">
                    <Badge variant="outline">{projectType}</Badge>
                    <Badge variant="outline">{sourceType}</Badge>
                  </div>
                </CardHeader>

                <Separator />

                <CardContent className="pt-6">
                  <Table>
                    <TableBody>
                      <TableRow>
                        <TableCell className="w-40 text-muted-foreground">Repository</TableCell>
                        <TableCell className="font-medium">
                          <div className="flex items-center justify-between gap-4">
                            <div className="min-w-0">
                              {repositoryUrl ? (
                                <Button
                                  asChild
                                  variant="link"
                                  className="h-auto p-0 whitespace-normal break-all"
                                >
                                  <a href={repositoryUrl} target="_blank" rel="noreferrer">
                                    {repositoryUrl}
                                  </a>
                                </Button>
                              ) : (
                                <span className="text-muted-foreground">—</span>
                              )}
                            </div>

                            <Button
                              type="button"
                              variant="outline"
                              onClick={() => setIsScanOpen(true)}
                              disabled={isScanning}
                            >
                              Scan API
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>

                      {repositoryUrl && sourceType === 'GIT' ? (
                        <>
                          <TableRow>
                            <TableCell className="text-muted-foreground">Owner</TableCell>
                            <TableCell className="font-medium">
                              {repoMeta.kind === 'available' && repoMeta.owner ? repoMeta.owner : '—'}
                            </TableCell>
                          </TableRow>
                          <TableRow>
                            <TableCell className="text-muted-foreground">Privacy</TableCell>
                            <TableCell className="font-medium">
                              {repoMeta.kind === 'available' && repoMeta.privacy ? repoMeta.privacy : '—'}
                            </TableCell>
                          </TableRow>
                          <TableRow>
                            <TableCell className="text-muted-foreground">Updated</TableCell>
                            <TableCell className="font-medium">
                              {repoMeta.kind === 'available' && repoMeta.updatedAt
                                ? formatDate(repoMeta.updatedAt)
                                : '—'}
                            </TableCell>
                          </TableRow>
                          {repoMeta.kind === 'notConnected' ? (
                            <TableRow>
                              <TableCell className="text-muted-foreground">GitHub</TableCell>
                              <TableCell className="font-medium">
                                <span className="text-muted-foreground">Not connected.</span>{' '}
                                {connectUrl ? (
                                  <Button asChild variant="link" className="h-auto p-0 align-baseline">
                                    <a href={connectUrl}>Connect to view repo metadata</a>
                                  </Button>
                                ) : null}
                              </TableCell>
                            </TableRow>
                          ) : null}
                        </>
                      ) : null}
                      <TableRow>
                        <TableCell className="text-muted-foreground">Project type</TableCell>
                        <TableCell className="font-medium">{projectType}</TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell className="text-muted-foreground">Source type</TableCell>
                        <TableCell className="font-medium">{sourceType}</TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell className="text-muted-foreground">Created at</TableCell>
                        <TableCell className="font-medium">
                          {project.createdAt ? formatDate(project.createdAt) : '—'}
                        </TableCell>
                      </TableRow>
                    </TableBody>
                  </Table>
                </CardContent>
              </>
            )}
          </Card>

          <Card className="mt-6">
            <CardHeader>
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0 space-y-1">
                  <CardTitle className="truncate">Endpoints</CardTitle>
                  <CardDescription>
                    Discover and display extracted API endpoints for this project.
                  </CardDescription>
                </div>

                <Button
                  type="button"
                  onClick={() => void discoverEndpoints()}
                  disabled={!project?.repositoryUrl || endpointsState.kind === 'loading' || endpointsState.kind === 'running'}
                >
                  {endpointsState.kind === 'loading'
                    ? 'Loading…'
                    : endpointsState.kind === 'running'
                      ? 'Discovery running…'
                      : endpointsState.kind === 'done'
                        ? 'Refresh endpoints'
                        : 'Discover endpoints'}
                </Button>
              </div>
            </CardHeader>

            <CardContent className="space-y-4">
              {!project?.repositoryUrl ? (
                <p className="text-sm text-muted-foreground">
                  Repository URL is missing; discovery is disabled.
                </p>
              ) : null}

              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div className="space-y-2">
                  <Label htmlFor="endpoint-branch">Branch (optional)</Label>
                  <Input
                    id="endpoint-branch"
                    value={endpointBranch}
                    onChange={(e) => setEndpointBranch(e.target.value)}
                    placeholder={defaultBranchLabel ? `Fallback: ${defaultBranchLabel}` : 'Fallback: main'}
                    disabled={endpointsState.kind === 'loading' || endpointsState.kind === 'running'}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="endpoint-search">Search by path</Label>
                  <Input
                    id="endpoint-search"
                    value={endpointSearch}
                    onChange={(e) => setEndpointSearch(e.target.value)}
                    placeholder="/api/projects"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-2">
                    <Label>Method</Label>
                    <Select value={endpointMethod} onValueChange={setEndpointMethod}>
                      <SelectTrigger>
                        <SelectValue placeholder="All" />
                      </SelectTrigger>
                      <SelectContent>
                        {methodOptions.map((m) => (
                          <SelectItem key={m} value={m}>
                            {m}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
              </div>

              {endpointsState.kind === 'error' ? (
                <p className="text-sm text-destructive">{endpointsState.message}</p>
              ) : endpointsState.kind === 'loading' ? (
                <p className="text-sm text-muted-foreground">Loading endpoints…</p>
              ) : endpointsState.kind === 'running' ? (
                <p className="text-sm text-muted-foreground">
                  Discovery running… polling every few seconds ({endpointsState.attempt}/{endpointsState.maxAttempts}).
                </p>
              ) : endpointsState.kind === 'done' && endpointsState.endpoints.length === 0 ? (
                <p className="text-sm text-muted-foreground">No endpoints found.</p>
              ) : null}

              {endpointsState.kind === 'done' && endpointsState.endpoints.length > 0 ? (
                <div className="rounded-md border border-border">
                  <div className="max-h-[420px] overflow-auto">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead className="w-28">Method</TableHead>
                          <TableHead>Path</TableHead>
                          <TableHead>Summary</TableHead>
                          <TableHead className="w-56 text-right">Schema</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {filteredEndpoints.map((e) => (
                          <TableRow key={e.id}>
                            <TableCell>
                              <Badge variant="secondary">{String(e.method).toUpperCase()}</Badge>
                            </TableCell>
                            <TableCell className="font-medium">{e.path}</TableCell>
                            <TableCell className="text-muted-foreground">{e.summary ?? '—'}</TableCell>
                            <TableCell className="text-right">
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                className="whitespace-nowrap"
                                onClick={() =>
                                  setSchemaDialog({
                                    open: true,
                                    title: `${String(e.method).toUpperCase()} ${e.path}`,
                                    schema: e.requestSchema,
                                  })
                                }
                              >
                                View schema
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                </div>
              ) : null}
            </CardContent>
          </Card>

          <Dialog
            open={schemaDialog.open}
            onOpenChange={(open) => setSchemaDialog((prev) => ({ ...prev, open }))}
          >
            <DialogContent className="max-w-2xl">
              <DialogHeader>
                <DialogTitle>Request schema</DialogTitle>
                <DialogDescription>{schemaDialog.title}</DialogDescription>
              </DialogHeader>

              {schemaDialog.schema ? (
                <pre className="max-h-[60vh] overflow-auto rounded-md border border-border bg-muted/20 p-3 text-xs whitespace-pre-wrap">
                  {schemaDialog.schema}
                </pre>
              ) : (
                <p className="text-sm text-muted-foreground">No request schema available.</p>
              )}

              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setSchemaDialog((p) => ({ ...p, open: false }))}>
                  Close
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>

          <Dialog
            open={isScanOpen}
            onOpenChange={(open) => {
              if (!open && isScanning) {
                abortRef.current?.abort()
                abortRef.current = null
                setIsScanning(false)
              }
              setIsScanOpen(open)
            }}
          >
            <DialogContent className="max-w-3xl">
              <DialogHeader>
                <DialogTitle>Scan API</DialogTitle>
                <DialogDescription>
                  Scan a Git repository or local path to extract REST endpoints.
                </DialogDescription>
              </DialogHeader>

              <div className="space-y-6">
                <div className="space-y-3">
                  <Label>Scan source</Label>
                  <RadioGroup
                    value={scanSource}
                    onValueChange={(v) => setScanSource(v as any)}
                    className="grid gap-3"
                    disabled={isScanning}
                  >
                    <div className="flex items-center space-x-2">
                      <RadioGroupItem value="git" id="scan-source-git" />
                      <Label htmlFor="scan-source-git">Git Repo URL</Label>
                    </div>
                    <div className="flex items-center space-x-2">
                      <RadioGroupItem value="local" id="scan-source-local" />
                      <Label htmlFor="scan-source-local">Local Path</Label>
                    </div>
                  </RadioGroup>
                </div>

                {scanSource === 'git' ? (
                  <div className="space-y-2">
                    <Label htmlFor="scanRepoUrl">Repo URL</Label>
                    <Input
                      id="scanRepoUrl"
                      value={scanRepoUrl}
                      onChange={(e) => setScanRepoUrl(e.target.value)}
                      placeholder="https://github.com/user/repo.git"
                      disabled={isScanning}
                    />
                  </div>
                ) : (
                  <div className="space-y-2">
                    <Label htmlFor="scanProjectPath">Project path</Label>
                    <Input
                      id="scanProjectPath"
                      value={scanProjectPath}
                      onChange={(e) => setScanProjectPath(e.target.value)}
                      placeholder="C:\\path\\to\\project"
                      disabled={isScanning}
                    />
                  </div>
                )}

                {isScanning ? (
                  <div className="rounded-lg border border-border p-4">
                    <div className="flex items-center gap-3">
                      <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-foreground">
                          {getScanPhaseLabel(elapsedSeconds, scanSource)}
                        </p>
                        <p className="text-xs text-muted-foreground">Elapsed: {elapsedSeconds}s</p>
                      </div>
                    </div>
                  </div>
                ) : null}

                {scanError ? (
                  <div className="rounded-lg border border-destructive/40 bg-destructive/10 p-4">
                    <p className="text-sm font-medium text-destructive">{scanError}</p>
                    {scanErrorDetails &&
                    typeof scanErrorDetails === 'object' &&
                    scanErrorDetails !== null &&
                    typeof (scanErrorDetails as any).connectUrl === 'string' ? (
                      <div className="mt-2">
                        <Button asChild variant="link" className="h-auto p-0">
                          <a href={String((scanErrorDetails as any).connectUrl)}>
                            Connect GitHub account
                          </a>
                        </Button>
                      </div>
                    ) : null}
                    {scanErrorDetails ? (
                      <pre className="mt-2 max-h-40 overflow-auto text-xs text-muted-foreground whitespace-pre-wrap">
                        {typeof scanErrorDetails === 'string'
                          ? scanErrorDetails
                          : JSON.stringify(scanErrorDetails, null, 2)}
                      </pre>
                    ) : null}
                  </div>
                ) : null}

                {scanResult ? (
                  <div className="space-y-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant={frameworkBadgeVariant(scanResult.metadata.framework)}>
                          {scanResult.metadata.framework}
                        </Badge>
                        <Badge variant="outline">{scanResult.endpoints.length} endpoints</Badge>
                      </div>

                      <Button
                        type="button"
                        variant="outline"
                        onClick={async () => {
                          try {
                            await navigator.clipboard.writeText(JSON.stringify(scanResult, null, 2))
                            toast({ title: 'Copied', description: 'Raw JSON copied to clipboard.' })
                          } catch {
                            toast({ title: 'Copy failed', description: 'Unable to copy JSON.', variant: 'destructive' })
                          }
                        }}
                      >
                        <Copy className="mr-2 h-4 w-4" />
                        Copy JSON
                      </Button>
                    </div>

                    {Array.isArray(scanResult.issues) && scanResult.issues.length > 0 ? (
                      <div className="rounded-lg border border-border bg-muted/30 p-4">
                        <p className="text-sm font-medium text-foreground">Issues</p>
                        <ul className="mt-2 list-disc pl-5 text-sm text-muted-foreground space-y-1">
                          {scanResult.issues.map((issue, idx) => (
                            <li key={`${idx}-${issue}`}>{issue}</li>
                          ))}
                        </ul>
                      </div>
                    ) : null}

                    <div className="rounded-lg border border-border">
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead className="w-24">Method</TableHead>
                            <TableHead>Path</TableHead>
                            <TableHead>Controller/Handler</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {scanResult.endpoints.length === 0 ? (
                            <TableRow>
                              <TableCell colSpan={3} className="text-sm text-muted-foreground">
                                No endpoints returned.
                              </TableCell>
                            </TableRow>
                          ) : (
                            scanResult.endpoints.map((ep, idx) => (
                              <TableRow key={`${ep.method}-${ep.path}-${idx}`}>
                                <TableCell className="font-medium">{ep.method}</TableCell>
                                <TableCell className="font-mono text-xs break-all">{ep.path}</TableCell>
                                <TableCell className="text-sm text-muted-foreground">
                                  {ep.controller || ep.handler ? (
                                    <span>{[ep.controller, ep.handler].filter(Boolean).join(' · ')}</span>
                                  ) : (
                                    '—'
                                  )}
                                </TableCell>
                              </TableRow>
                            ))
                          )}
                        </TableBody>
                      </Table>
                    </div>

                    <Collapsible open={rawOpen} onOpenChange={setRawOpen}>
                      <div className="flex items-center justify-between">
                        <CollapsibleTrigger asChild>
                          <Button type="button" variant="outline">
                            {rawOpen ? 'Hide Raw JSON' : 'Show Raw JSON'}
                          </Button>
                        </CollapsibleTrigger>
                      </div>
                      <CollapsibleContent>
                        <pre className="mt-3 max-h-72 overflow-auto rounded-md border border-border bg-muted p-3 text-xs whitespace-pre-wrap">
                          {JSON.stringify(scanResult, null, 2)}
                        </pre>
                      </CollapsibleContent>
                    </Collapsible>
                  </div>
                ) : null}
              </div>

              <DialogFooter>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    if (isScanning) {
                      abortRef.current?.abort()
                      abortRef.current = null
                      setIsScanning(false)
                      toast({ title: 'Scan cancelled' })
                      return
                    }
                    setIsScanOpen(false)
                  }}
                >
                  {isScanning ? 'Cancel scan' : 'Close'}
                </Button>

                <Button
                  type="button"
                  disabled={isScanning}
                  onClick={async () => {
                    setScanError(null)
                    setScanErrorDetails(null)
                    setScanResult(null)
                    setRawOpen(false)

                    if (scanSource === 'git') {
                      try {
                        const meRes = await githubApiFetch('/api/github/me')
                        if (meRes.status === 401) {
                          setScanError('You are not authenticated.')
                          return
                        }

                        if (meRes.status === 404 || meRes.status === 400) {
                          setScanError('GitHub is not connected. Connect your account to scan private repos.')
                          if (connectUrl) setScanErrorDetails({ connectUrl })
                          return
                        }

                        if (meRes.ok) {
                          const meData = (await meRes.json().catch(() => null)) as unknown
                          const me = (meData && typeof meData === 'object' ? (meData as GitHubMeResponse) : null)
                          if (!me?.githubConnected) {
                            setScanError('GitHub is not connected. Connect your account to scan private repos.')
                            if (connectUrl) setScanErrorDetails({ connectUrl })
                            return
                          }
                        }
                      } catch {
                        // If we cannot check GitHub status, proceed; server will still scan public repos.
                      }
                    }

                    const payload: ScanProjectPayload | null =
                      scanSource === 'git'
                        ? (scanRepoUrl.trim() ? { repoUrl: scanRepoUrl.trim() } : null)
                        : (scanProjectPath.trim() ? { projectPath: scanProjectPath.trim() } : null)

                    if (!payload) {
                      setScanError(scanSource === 'git' ? 'Repo URL is required.' : 'Project path is required.')
                      return
                    }

                    const controller = new AbortController()
                    abortRef.current = controller
                    setIsScanning(true)

                    try {
                      const result = await scanProject(payload, {
                        signal: controller.signal,
                      })
                      setScanResult(result)
                    } catch (error) {
                      if (error instanceof ApiScannerError) {
                        setScanError(error.message)
                        setScanErrorDetails(error.body ?? (error.status ? { status: error.status } : null))
                      } else {
                        const message = error instanceof Error ? error.message : 'Scan failed'
                        setScanError(message)
                      }
                    } finally {
                      abortRef.current = null
                      setIsScanning(false)
                    }
                  }}
                >
                  Start Scan
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>

          <div className="mt-6">
            <Card>
              <CardHeader>
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0 space-y-1">
                    <CardTitle>Campaigns</CardTitle>
                    <CardDescription>
                      Create and track test campaigns for this project
                    </CardDescription>
                  </div>

                  <div className="flex items-center gap-2">
                    <Button asChild variant="outline">
                      <Link href="/campaigns">View all</Link>
                    </Button>
                    <Button asChild disabled={!project}>
                      <Link href="/campaigns/new">New campaign</Link>
                    </Button>
                  </div>
                </div>
              </CardHeader>

              <Separator />

              <CardContent className="pt-6">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {campaignsForProjectSeed.map((campaign) => (
                    <CampaignCard key={campaign.name} {...campaign} />
                  ))}
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      </main>
    </div>
  )
}

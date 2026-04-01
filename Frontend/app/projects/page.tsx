'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Plus, Search } from 'lucide-react'

import {
  createProject,
  getProjects,
  type Project,
  type ProjectType,
  type SourceType,
} from '@/lib/api-client'

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
  defaultBranch?: string
  branches: string[]
}

type GitHubConnectionState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'notConfigured' }
  | { kind: 'unauthorized' }
  | { kind: 'notConnected' }
  | { kind: 'connected'; me: GitHubMeResponse }
  | { kind: 'error'; message: string }

type RepoListState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'available'; repos: RepoRow[] }
  | { kind: 'unavailable' }
  | { kind: 'error'; message: string }

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function extractBranchNames(raw: unknown): string[] {
  // Supports these shapes:
  // - [{ name: 'main' }, ...]  (GitHub branches API)
  // - ['main', ...]
  // - { data: [...] } (axios/octokit style)
  // - { branches: [...] } / { items: [...] }
  const candidate =
    Array.isArray(raw) ? raw
      : isRecord(raw) && Array.isArray(raw.data) ? raw.data
        : isRecord(raw) && Array.isArray(raw.branches) ? raw.branches
          : isRecord(raw) && Array.isArray(raw.items) ? raw.items
            : []

  const names = candidate
    .map((b) => {
      if (typeof b === 'string') return b
      if (isRecord(b)) {
        if (typeof b.name === 'string') return b.name
        if (typeof b.ref === 'string') return b.ref
        if (typeof b.branch === 'string') return b.branch
      }
      return null
    })
    .filter(Boolean) as string[]

  // De-dupe while preserving order.
  return Array.from(new Set(names))
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

      const defaultBranch: string | undefined =
        typeof r.default_branch === 'string'
          ? r.default_branch
          : typeof r.defaultBranch === 'string'
            ? r.defaultBranch
            : undefined

      const branches = extractBranchNames(r.branches)

      const key =
        typeof r.id === 'number' || typeof r.id === 'string'
          ? String(r.id)
          : `${owner}/${name || 'repo'}:${idx}`

      if (!name) return null
      return { key, name, owner, isPrivate, url, updatedAt, defaultBranch, branches }
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

const statusStyle: Record<'Deployed' | 'Not deployed', string> = {
  Deployed: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  'Not deployed': 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

function getProvider(project: Project): string {
  return String(project.sourceType ?? '—')
}

function getRepositoryUrl(project: Project): string {
  return String(project.repositoryUrl ?? '—')
}

function getType(project: Project): string {
  const value = String(project.projectType ?? '—')
  if (value === 'WEB') return 'WEB'
  if (value === 'MOBILE') return 'MOBILE'
  if (value === 'API') return 'API'
  if (value === 'DESKTOP') return 'DESKTOP'
  if (value === 'OTHER') return 'OTHER'
  return value
}

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [searchQuery, setSearchQuery] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [repositoryUrl, setRepositoryUrl] = useState('')
  const [projectType, setProjectType] = useState<ProjectType>('WEB')
  const [sourceType, setSourceType] = useState<SourceType>('GIT')
  const [deployed, setDeployed] = useState(false)

  const [gitHubConnection, setGitHubConnection] = useState<GitHubConnectionState>({
    kind: 'idle',
  })
  const [gitHubRepos, setGitHubRepos] = useState<RepoListState>({ kind: 'idle' })
  const [selectedRepoKey, setSelectedRepoKey] = useState('')
  const [selectedBranch, setSelectedBranch] = useState('')

  const loadProjects = async () => {
    setIsLoading(true)
    try {
      const data = await getProjects()
      setProjects(Array.isArray(data) ? data : [])
    } catch (error) {
      console.error('Failed to load projects', error)
      setProjects([])
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadProjects()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const filteredProjects = useMemo(() => {
    const query = searchQuery.trim().toLowerCase()
    if (!query) return projects

    return projects.filter((project) => {
      const projectName = String(project.name ?? '').toLowerCase()
      const repoUrl = String(project.repositoryUrl ?? '').toLowerCase()
      return projectName.includes(query) || repoUrl.includes(query)
    })
  }, [projects, searchQuery])

  const resetForm = () => {
    setFormError(null)
    setName('')
    setRepositoryUrl('')
    setProjectType('WEB')
    setSourceType('GIT')
    setDeployed(false)
    setSelectedRepoKey('')
    setSelectedBranch('')
  }

  const connectUrl = useMemo(() => {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) return ''
    return `${apiUrl.replace(/\/+$/, '')}/api/github/connect`
  }, [])

  const loadGitHubInfo = async () => {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) {
      setGitHubConnection({ kind: 'notConfigured' })
      setGitHubRepos({ kind: 'idle' })
      return
    }

    setGitHubConnection({ kind: 'loading' })
    setGitHubRepos({ kind: 'loading' })

    try {
      const meRes = await githubApiFetch('/api/github/me')

      if (meRes.status === 401) {
        setGitHubConnection({ kind: 'unauthorized' })
        setGitHubRepos({ kind: 'idle' })
        return
      }

      if (meRes.status === 404 || meRes.status === 400) {
        setGitHubConnection({ kind: 'notConnected' })
        setGitHubRepos({ kind: 'idle' })
        return
      }

      if (!meRes.ok) {
        const text = await meRes.text().catch(() => '')
        setGitHubConnection({
          kind: 'error',
          message: `Unable to load GitHub status (${meRes.status})${text ? `: ${text}` : ''}`,
        })
        setGitHubRepos({ kind: 'idle' })
        return
      }

      const meData = (await meRes.json().catch(() => null)) as unknown
      if (!meData || typeof meData !== 'object') {
        setGitHubConnection({ kind: 'error', message: 'Unexpected response from GitHub status.' })
        setGitHubRepos({ kind: 'idle' })
        return
      }

      const me = meData as GitHubMeResponse
      if (!me.githubConnected) {
        setGitHubConnection({ kind: 'notConnected' })
        setGitHubRepos({ kind: 'idle' })
        return
      }

      setGitHubConnection({ kind: 'connected', me })

      const reposRes = await githubApiFetch('/api/github/repos')

      if (reposRes.status === 401) {
        setGitHubConnection({ kind: 'unauthorized' })
        setGitHubRepos({ kind: 'idle' })
        return
      }

      if (reposRes.status === 404) {
        // Backend semantics: 404 means GitHub is not connected.
        setGitHubConnection({ kind: 'notConnected' })
        setGitHubRepos({ kind: 'idle' })
        return
      }

      if (!reposRes.ok) {
        const text = await reposRes.text().catch(() => '')
        setGitHubRepos({
          kind: 'error',
          message: `Unable to load repos (${reposRes.status})${text ? `: ${text}` : ''}`,
        })
        return
      }

      const reposData = (await reposRes.json().catch(() => null)) as unknown
      const normalized = normalizeRepos(reposData)
      setGitHubRepos({ kind: 'available', repos: normalized })
    } catch (error) {
      setGitHubConnection({
        kind: 'error',
        message: error instanceof Error ? error.message : 'Network error',
      })
      setGitHubRepos({ kind: 'idle' })
    }
  }

  useEffect(() => {
    if (!isCreateOpen) return
    if (sourceType !== 'GIT') return
    void loadGitHubInfo()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isCreateOpen, sourceType])

  useEffect(() => {
    setSelectedRepoKey('')
    setRepositoryUrl('')
    setSelectedBranch('')
    if (sourceType !== 'GIT') {
      setGitHubConnection({ kind: 'idle' })
      setGitHubRepos({ kind: 'idle' })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceType])

  const onSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setIsSubmitting(true)
    setFormError(null)

    try {
      if (sourceType === 'GIT') {
        if (gitHubConnection.kind !== 'connected') {
          setFormError('GitHub must be connected to create a GIT project.')
          return
        }
        if (!repositoryUrl.trim()) {
          setFormError('Please select a repository.')
          return
        }
      }

      await createProject({
        name,
        projectType,
        sourceType,
        repositoryUrl: repositoryUrl.trim() ? repositoryUrl.trim() : null,
        defaultBranch:
          sourceType === 'GIT' ? (selectedBranch.trim() ? selectedBranch.trim() : null) : null,
        deployed,
      })
      setIsCreateOpen(false)
      resetForm()
      await loadProjects()
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to create project'
      console.error('Create project failed', error)
      setFormError(message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-7xl">
          <div className="flex items-center justify-between mb-8">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Projects</h1>
              <p className="text-muted-foreground mt-1">
                Organize campaigns, environments, and executions by application
              </p>
            </div>

            <Sheet open={isCreateOpen} onOpenChange={setIsCreateOpen}>
              <SheetTrigger asChild>
                <Button className="gap-2" onClick={() => setIsCreateOpen(true)}>
                  <Plus size={20} />
                  New Project
                </Button>
              </SheetTrigger>
              <SheetContent side="right" className="sm:max-w-md">
                <SheetHeader>
                  <SheetTitle>Add New Project</SheetTitle>
                  <SheetDescription>
                    Create a project to group campaigns and environments
                  </SheetDescription>
                </SheetHeader>

                <form className="mt-6 space-y-6" onSubmit={onSubmit}>
                  <div className="space-y-2">
                    <Label htmlFor="projectName">Project name</Label>
                    <Input
                      id="projectName"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      placeholder="Banking Web App"
                      required
                    />
                  </div>

                  {formError ? (
                    <p className="text-sm text-destructive">{formError}</p>
                  ) : null}

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label>Project type</Label>
                      <Select value={projectType} onValueChange={(v) => setProjectType(v as any)}>
                        <SelectTrigger>
                          <SelectValue placeholder="Select type" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="WEB">WEB</SelectItem>
                          <SelectItem value="MOBILE">MOBILE</SelectItem>
                          <SelectItem value="API">API</SelectItem>
                          <SelectItem value="DESKTOP">DESKTOP</SelectItem>
                          <SelectItem value="OTHER">OTHER</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>

                    <div className="space-y-2">
                      <Label>Source type</Label>
                      <Select value={sourceType} onValueChange={(v) => setSourceType(v as any)}>
                        <SelectTrigger>
                          <SelectValue placeholder="Select source" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="GIT">GIT</SelectItem>
                          <SelectItem value="LOCAL">LOCAL</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                  </div>

                  {sourceType === 'GIT' ? (
                    <div className="space-y-2">
                      <Label>Repository</Label>
                      {gitHubConnection.kind === 'notConfigured' ? (
                        <p className="text-sm text-muted-foreground">
                          Missing NEXT_PUBLIC_API_URL configuration.
                        </p>
                      ) : gitHubConnection.kind === 'unauthorized' ? (
                        <p className="text-sm text-muted-foreground">Please sign in to connect GitHub.</p>
                      ) : gitHubConnection.kind === 'notConnected' ? (
                        <div className="space-y-2">
                          <p className="text-sm text-muted-foreground">
                            GitHub is not connected. Connect it to select a repository.
                          </p>
                          <Button asChild disabled={!connectUrl}>
                            <a href={connectUrl}>Connect GitHub Account</a>
                          </Button>
                        </div>
                      ) : gitHubConnection.kind === 'error' ? (
                        <p className="text-sm text-destructive">{gitHubConnection.message}</p>
                      ) : gitHubRepos.kind === 'idle' || gitHubRepos.kind === 'loading' ? (
                        <p className="text-sm text-muted-foreground">Loading repositories…</p>
                      ) : gitHubRepos.kind === 'unavailable' ? (
                        <p className="text-sm text-muted-foreground">Repository listing is not available.</p>
                      ) : gitHubRepos.kind === 'error' ? (
                        <p className="text-sm text-destructive">{gitHubRepos.message}</p>
                      ) : gitHubRepos.repos.length === 0 ? (
                        <p className="text-sm text-muted-foreground">No repositories found.</p>
                      ) : (
                        <Select
                          value={selectedRepoKey}
                          onValueChange={(v) => {
                            setSelectedRepoKey(v)
                            const repo = gitHubRepos.repos.find((r) => r.key === v) ?? null
                            if (repo?.url) setRepositoryUrl(repo.url)
                            if (!name.trim() && repo?.name) setName(repo.name)

                            const defaultCandidate = repo?.defaultBranch
                            const nextBranch =
                              defaultCandidate && repo?.branches?.includes(defaultCandidate)
                                ? defaultCandidate
                                : repo?.branches?.[0] ?? ''
                            setSelectedBranch(nextBranch)
                          }}
                        >
                          <SelectTrigger>
                            <SelectValue placeholder="Select a repository" />
                          </SelectTrigger>
                          <SelectContent>
                            {gitHubRepos.repos.map((repo) => (
                              <SelectItem key={repo.key} value={repo.key}>
                                {repo.owner}/{repo.name}{repo.isPrivate ? ' (private)' : ''}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      )}

                      {gitHubRepos.kind === 'available' && selectedRepoKey ? (
                        <div className="space-y-2 pt-2">
                          <Label>Branch</Label>
                          {(() => {
                            const repo = gitHubRepos.repos.find((r) => r.key === selectedRepoKey) ?? null
                            const branches = (() => {
                              if (!repo) return [] as string[]
                              if (repo.branches?.length) return repo.branches
                              if (repo.defaultBranch) return [repo.defaultBranch]
                              return [] as string[]
                            })()

                            if (!repo) {
                              return (
                                <p className="text-sm text-muted-foreground">Select a repository first.</p>
                              )
                            }

                            if (branches.length === 0) {
                              return (
                                <Select value="" disabled>
                                  <SelectTrigger>
                                    <SelectValue placeholder="No branches available" />
                                  </SelectTrigger>
                                  <SelectContent />
                                </Select>
                              )
                            }

                            return (
                              <Select value={selectedBranch} onValueChange={setSelectedBranch}>
                                <SelectTrigger>
                                  <SelectValue placeholder="Select a branch" />
                                </SelectTrigger>
                                <SelectContent>
                                  {branches.map((b) => (
                                    <SelectItem key={b} value={b}>
                                      {b}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            )
                          })()}
                        </div>
                      ) : null}
                    </div>
                  ) : (
                    <div className="space-y-2">
                      <Label htmlFor="projectRepoUrl">Local path (optional)</Label>
                      <Input
                        id="projectRepoUrl"
                        value={repositoryUrl}
                        onChange={(e) => setRepositoryUrl(e.target.value)}
                        placeholder="C:\\path\\to\\project"
                      />
                    </div>
                  )}

                  <div className="flex items-center justify-between rounded-lg border border-border p-3">
                    <div>
                      <p className="text-sm font-medium text-foreground">Deployed</p>
                      <p className="text-xs text-muted-foreground">Is the app currently deployed?</p>
                    </div>
                    <Switch checked={deployed} onCheckedChange={setDeployed} />
                  </div>
                  <SheetFooter className="pt-2">
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => {
                        setIsCreateOpen(false)
                        resetForm()
                      }}
                    >
                      Cancel
                    </Button>
                    <Button type="submit" disabled={isSubmitting}>
                      {isSubmitting ? 'Creating…' : 'Create project'}
                    </Button>
                  </SheetFooter>
                </form>
              </SheetContent>
            </Sheet>
          </div>

          <div className="flex flex-col md:flex-row gap-4 mb-8">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground size-5" />
              <Input
                placeholder="Search projects..."
                className="pl-10"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {!isLoading && filteredProjects.length === 0 ? (
              <Card className="p-6">
                <p className="text-sm text-muted-foreground">No projects found.</p>
              </Card>
            ) : null}

            {filteredProjects.map((project) => (
              <Card key={project.id} className="p-6">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="text-lg font-semibold text-foreground truncate">
                      {project.name}
                    </p>
                    <p className="text-sm text-muted-foreground mt-1 truncate">
                      {getRepositoryUrl(project)}
                    </p>
                  </div>
                  <Badge
                    variant="outline"
                    className={statusStyle[project.deployed ? 'Deployed' : 'Not deployed']}
                  >
                    {project.deployed ? 'Deployed' : 'Not deployed'}
                  </Badge>
                </div>

                <div className="grid grid-cols-2 gap-4 mt-6">
                  <div>
                    <p className="text-xs text-muted-foreground">Type</p>
                    <p className="text-sm font-semibold text-foreground mt-1">
                      {getType(project)}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Source</p>
                    <p className="text-sm font-semibold text-foreground mt-1">
                      {getProvider(project)}
                    </p>
                  </div>
                </div>

                <div className="mt-4">
                  <p className="text-xs text-muted-foreground">
                    Created {project.createdAt ? formatDate(project.createdAt) : '—'}
                  </p>
                </div>

                <div className="mt-6">
                  <Button asChild variant="outline" className="w-full">
                    <Link href={`/projects/${project.id}`}>View details</Link>
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </main>
    </div>
  )
}

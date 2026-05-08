'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { Folder, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react'

import { AuthGuard } from '@/components/auth-guard'
import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { ConfirmDialog } from '@/components/crud/ConfirmDialog'
import { FormDialog } from '@/components/crud/FormDialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
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
import { projectService } from '@/services/projects'
import type {
  CreateProjectRequest,
  Project,
  ProjectStatus,
  UpdateProjectRequest,
} from '@/types/ms-gestion'

type ProjectFormState = {
  name: string
  description: string
  gitRepoUrl: string
  gitDefaultBranch: string
  useAiMode: boolean // Use internal Maven template without Git repo
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
  defaultBranch?: string
  branches: string[]
}

type GitHubConnectionState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'notConnected' }
  | { kind: 'unauthorized' }
  | { kind: 'connected'; me: GitHubMeResponse }
  | { kind: 'error'; message: string }

type RepoListState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'available'; repos: RepoRow[] }
  | { kind: 'error'; message: string }

type BranchListState =
  | { kind: 'idle'; branches: string[] }
  | { kind: 'loading'; branches: string[] }
  | { kind: 'available'; branches: string[] }
  | { kind: 'error'; branches: string[]; message: string }

const emptyForm: ProjectFormState = {
  name: '',
  description: '',
  gitRepoUrl: '',
  gitDefaultBranch: 'main',
  useAiMode: false,
}

const statusVariant: Record<
  ProjectStatus,
  'default' | 'secondary' | 'destructive' | 'outline'
> = {
  ACTIVE: 'default',
  PAUSED: 'secondary',
  ARCHIVED: 'outline',
}

const formatDate = (value?: string) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

const apiFetch = async (path: string, init: RequestInit = {}): Promise<Response> => {
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

const extractBranchNames = (payload: unknown): string[] => {
  const list: unknown[] = Array.isArray(payload)
    ? payload
    : payload && typeof payload === 'object' && Array.isArray((payload as any).data)
      ? ((payload as any).data as unknown[])
      : payload && typeof payload === 'object' && Array.isArray((payload as any).branches)
        ? ((payload as any).branches as unknown[])
        : payload && typeof payload === 'object' && Array.isArray((payload as any).items)
          ? ((payload as any).items as unknown[])
          : []

  const names = list
    .map((branch) => {
      if (typeof branch === 'string') return branch
      if (branch && typeof branch === 'object') {
        const b = branch as Record<string, unknown>
        if (typeof b.name === 'string') return b.name
        if (typeof b.ref === 'string') return b.ref
        if (typeof b.branch === 'string') return b.branch
      }
      return null
    })
    .filter(Boolean) as string[]

  return Array.from(new Set(names))
}

const normalizeRepos = (payload: unknown): RepoRow[] => {
  const list: unknown[] = Array.isArray(payload)
    ? payload
    : payload && typeof payload === 'object' && Array.isArray((payload as any).repos)
      ? ((payload as any).repos as unknown[])
      : []

  return list
    .map((repo, idx) => {
      if (!repo || typeof repo !== 'object') return null
      const r = repo as Record<string, unknown>

      const name: string =
        typeof r.name === 'string'
          ? r.name
          : typeof r.full_name === 'string'
            ? String(r.full_name).split('/').slice(-1)[0]
            : ''

      const owner: string =
        typeof r.owner === 'string'
          ? r.owner
          : r.owner && typeof r.owner === 'object' && typeof (r.owner as any).login === 'string'
            ? String((r.owner as any).login)
            : typeof r.full_name === 'string'
              ? String(r.full_name).split('/')[0] ?? ''
              : ''

      const url: string =
        typeof r.html_url === 'string'
          ? r.html_url
          : typeof r.url === 'string'
            ? r.url
            : ''

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

      const branches = extractBranchNames((r as any).branches)

      const key =
        typeof r.id === 'number' || typeof r.id === 'string'
          ? String(r.id)
          : `${owner}/${name || 'repo'}:${idx}`

      if (!name) return null
      return {
        key,
        name,
        owner,
        isPrivate: Boolean(r.private),
        url,
        updatedAt,
        defaultBranch,
        branches,
      }
    })
    .filter(Boolean) as RepoRow[]
}

const normalizeRepoUrl = (url: string): string =>
  String(url || '')
    .trim()
    .toLowerCase()
    .replace(/\.git$/, '')
    .replace(/\/+$/, '')

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')

  const [formState, setFormState] = useState<ProjectFormState>(emptyForm)
  const [formError, setFormError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const [gitHubConnection, setGitHubConnection] = useState<GitHubConnectionState>({
    kind: 'idle',
  })
  const [gitHubRepos, setGitHubRepos] = useState<RepoListState>({ kind: 'idle' })
  const [branchState, setBranchState] = useState<BranchListState>({
    kind: 'idle',
    branches: [],
  })
  const [selectedRepoKey, setSelectedRepoKey] = useState('')
  const [selectedBranch, setSelectedBranch] = useState('')
  const [repoInitialized, setRepoInitialized] = useState(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editingProject, setEditingProject] = useState<Project | null>(null)

  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletingProject, setDeletingProject] = useState<Project | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)

  const loadProjects = async () => {
    setStatus('loading')
    setError(null)
    try {
      const data = await projectService.getAll()
      setProjects(Array.isArray(data) ? data : [])
      setStatus('ready')
    } catch (err) {
      setProjects([])
      setStatus('error')
      setError(err instanceof Error ? err.message : 'Failed to load projects')
    }
  }

  useEffect(() => {
    void loadProjects()
  }, [])

  const filteredProjects = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return projects
    return projects.filter((project) => {
      const name = String(project.name ?? '').toLowerCase()
      const repo = String(project.gitRepoUrl ?? '').toLowerCase()
      const isAi = project.aiProject || project.aiBuiltin || !project.gitRepoUrl || project.gitRepoUrl === 'ai-builtin'
      return name.includes(query) || repo.includes(query) || (isAi && 'ia'.includes(query))
    })
  }, [projects, search])

  const resetForm = () => {
    setFormState(emptyForm)
    setFormError(null)
    setSelectedRepoKey('')
    setSelectedBranch('')
    setBranchState({ kind: 'idle', branches: [] })
    setRepoInitialized(false)
  }

  const openCreate = () => {
    resetForm()
    setCreateOpen(true)
  }

  const openEdit = (project: Project) => {
    setEditingProject(project)
    setFormError(null)
    const isAiMode = project.aiProject || !project.gitRepoUrl || project.gitRepoUrl === 'ai-builtin' || project.aiBuiltin
    setFormState({
      name: project.name ?? '',
      description: project.description ?? '',
      gitRepoUrl: project.gitRepoUrl ?? '',
      gitDefaultBranch: project.gitDefaultBranch ?? 'main',
      useAiMode: isAiMode,
    })
    setSelectedBranch(project.gitDefaultBranch ?? 'main')
    setSelectedRepoKey('')
    setBranchState({ kind: 'idle', branches: [] })
    setRepoInitialized(false)
    setEditOpen(true)
  }

  const openDelete = (project: Project) => {
    setDeletingProject(project)
    setDeleteOpen(true)
  }

  const connectUrl = useMemo(() => {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) return ''
    return `${apiUrl.replace(/\/+$/, '')}/api/github/connect`
  }, [])

  const fetchRepos = async () => {
    setGitHubRepos({ kind: 'loading' })
    try {
      const res = await apiFetch('/api/github/repos')

      if (res.status === 401) {
        setGitHubConnection({ kind: 'unauthorized' })
        setGitHubRepos({ kind: 'idle' })
        return
      }

      if (res.status === 404) {
        setGitHubConnection({ kind: 'notConnected' })
        setGitHubRepos({ kind: 'idle' })
        return
      }

      if (!res.ok) {
        const text = await res.text().catch(() => '')
        setGitHubRepos({
          kind: 'error',
          message: `Unable to load repos (${res.status})${text ? `: ${text}` : ''}`,
        })
        return
      }

      const data = (await res.json().catch(() => null)) as unknown
      const normalized = normalizeRepos(data)
      setGitHubRepos({ kind: 'available', repos: normalized })
    } catch (err) {
      setGitHubRepos({
        kind: 'error',
        message: err instanceof Error ? err.message : 'Network error',
      })
    }
  }

  const fetchGitHubMe = async () => {
    setGitHubConnection({ kind: 'loading' })
    setGitHubRepos({ kind: 'idle' })

    try {
      const res = await apiFetch('/api/github/me')

      if (res.status === 401) {
        setGitHubConnection({ kind: 'unauthorized' })
        return
      }

      if (res.status === 404 || res.status === 400) {
        setGitHubConnection({ kind: 'notConnected' })
        return
      }

      if (!res.ok) {
        const text = await res.text().catch(() => '')
        setGitHubConnection({
          kind: 'error',
          message: `Unable to load GitHub status (${res.status})${text ? `: ${text}` : ''}`,
        })
        return
      }

      const data = (await res.json().catch(() => null)) as unknown
      if (!data || typeof data !== 'object') {
        setGitHubConnection({ kind: 'error', message: 'Unexpected response from server.' })
        return
      }

      const me = data as GitHubMeResponse
      if (!me.githubConnected) {
        setGitHubConnection({ kind: 'notConnected' })
        return
      }

      setGitHubConnection({ kind: 'connected', me })
      await fetchRepos()
    } catch (err) {
      setGitHubConnection({
        kind: 'error',
        message: err instanceof Error ? err.message : 'Network error',
      })
    }
  }

  const fetchBranches = async (owner: string, repo: string) => {
    setBranchState({ kind: 'loading', branches: [] })
    try {
      const res = await apiFetch(`/api/github/repos/${owner}/${repo}/branches`)
      if (!res.ok) {
        const text = await res.text().catch(() => '')
        setBranchState({
          kind: 'error',
          branches: [],
          message: `Unable to load branches (${res.status})${text ? `: ${text}` : ''}`,
        })
        return []
      }
      const data = (await res.json().catch(() => null)) as unknown
      const branches = extractBranchNames(data)
      setBranchState({ kind: 'available', branches })
      return branches
    } catch (err) {
      setBranchState({
        kind: 'error',
        branches: [],
        message: err instanceof Error ? err.message : 'Network error',
      })
      return []
    }
  }

  useEffect(() => {
    if (!createOpen && !editOpen) return
    void fetchGitHubMe()
  }, [createOpen, editOpen])

  useEffect(() => {
    if (repoInitialized) return
    if (!createOpen && !editOpen) return
    if (gitHubRepos.kind !== 'available') return

    const repos = gitHubRepos.repos
    const currentRepoUrl = formState.gitRepoUrl
    if (currentRepoUrl) {
      const normalizedUrl = normalizeRepoUrl(currentRepoUrl)
      const match = repos.find((repo) => normalizeRepoUrl(repo.url) === normalizedUrl)
      if (match) {
        setSelectedRepoKey(match.key)
        setSelectedBranch(formState.gitDefaultBranch || match.defaultBranch || '')
      }
    }

    setRepoInitialized(true)
  }, [repoInitialized, createOpen, editOpen, gitHubRepos, formState.gitRepoUrl, formState.gitDefaultBranch])

  useEffect(() => {
    if (gitHubRepos.kind !== 'available') return
    if (!selectedRepoKey) return

    const repo = gitHubRepos.repos.find((item) => item.key === selectedRepoKey)
    if (!repo) return

    setFormState((prev) => {
      if (!repo.url || prev.gitRepoUrl === repo.url) return prev
      return { ...prev, gitRepoUrl: repo.url }
    })

    const existingBranch = formState.gitDefaultBranch
    const repoBranches = repo.branches
    const defaultBranch = repo.defaultBranch || ''

    const applyBranch = (branches: string[]) => {
      if (branches.length === 0) return
      const candidate =
        (existingBranch && branches.includes(existingBranch) ? existingBranch : '') ||
        (defaultBranch && branches.includes(defaultBranch) ? defaultBranch : '') ||
        branches[0]

      if (!candidate) return
      setSelectedBranch(candidate)
      setFormState((prev) => {
        if (prev.gitDefaultBranch === candidate) return prev
        return { ...prev, gitDefaultBranch: candidate }
      })
    }

    if (repoBranches.length > 0) {
      setBranchState({ kind: 'available', branches: repoBranches })
      applyBranch(repoBranches)
      return
    }

    if (repo.owner && repo.name) {
      void (async () => {
        const branches = await fetchBranches(repo.owner, repo.name)
        applyBranch(branches)
      })()
    }
  }, [gitHubRepos, selectedRepoKey, formState.gitDefaultBranch])

  const onCreateSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setIsSubmitting(true)
    setFormError(null)

    if (!formState.useAiMode && selectedRepoKey && !formState.gitDefaultBranch.trim()) {
      setFormError('Select a default branch for the repository.')
      setIsSubmitting(false)
      return
    }

    try {
      const payload: CreateProjectRequest = {
        name: formState.name.trim(),
        description: formState.description.trim() || undefined,
        gitRepoUrl: formState.useAiMode ? 'ai-builtin' : formState.gitRepoUrl.trim() || undefined,
        gitDefaultBranch: formState.useAiMode ? 'main' : formState.gitDefaultBranch.trim() || 'main',
        aiBuiltin: formState.useAiMode,
      }
      await projectService.create(payload)
      setCreateOpen(false)
      resetForm()
      await loadProjects()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to create project')
    } finally {
      setIsSubmitting(false)
    }
  }

  const onEditSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!editingProject) return
    setIsSubmitting(true)
    setFormError(null)

    if (!formState.useAiMode && selectedRepoKey && !formState.gitDefaultBranch.trim()) {
      setFormError('Select a default branch for the repository.')
      setIsSubmitting(false)
      return
    }

    try {
      const payload: UpdateProjectRequest = {
        name: formState.name.trim(),
        description: formState.description.trim() || undefined,
        gitRepoUrl: formState.useAiMode ? 'ai-builtin' : formState.gitRepoUrl.trim() || undefined,
        gitDefaultBranch: formState.useAiMode ? 'main' : formState.gitDefaultBranch.trim() || 'main',
        aiBuiltin: formState.useAiMode,
      }
      await projectService.update(editingProject.id, payload)
      setEditOpen(false)
      setEditingProject(null)
      await loadProjects()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to update project')
    } finally {
      setIsSubmitting(false)
    }
  }

  const onDeleteConfirm = async () => {
    if (!deletingProject) return
    setIsDeleting(true)
    try {
      await projectService.archive(deletingProject.id)
      setDeleteOpen(false)
      setDeletingProject(null)
      await loadProjects()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete project')
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />
        <AuthGuard>
          <div className="p-6 max-w-7xl">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div>
                <h1 className="text-3xl font-bold text-foreground">Projects</h1>
                <p className="text-muted-foreground mt-1">
                  Manage projects, environments, and test suites for ms_gestion.
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Button variant="outline" onClick={loadProjects} className="gap-2">
                  <RefreshCw size={16} />
                  Refresh
                </Button>
                <Button className="gap-2" onClick={openCreate}>
                  <Plus size={18} />
                  New project
                </Button>
              </div>
            </div>

            <div className="mt-6 flex flex-col gap-4 sm:flex-row sm:items-center">
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search by name or repo URL"
                className="max-w-md"
              />
              {error ? (
                <p className="text-sm text-destructive">{error}</p>
              ) : null}
            </div>

            <Card className="mt-6">
              {status === 'loading' ? (
                <div className="p-6 text-sm text-muted-foreground">Loading projects...</div>
              ) : null}

              {status !== 'loading' && filteredProjects.length === 0 ? (
                <div className="p-6 text-sm text-muted-foreground flex items-center gap-2">
                  <Folder size={16} />
                  No projects found.
                </div>
              ) : null}

              {status !== 'loading' && filteredProjects.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Repository</TableHead>
                      <TableHead>Default branch</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Created</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredProjects.map((project) => (
                      <TableRow key={project.id}>
                        <TableCell className="font-medium">
                          <div className="flex items-center gap-2">
                            <Link
                              href={`/projects/${project.id}`}
                              className="text-primary hover:underline"
                            >
                              {project.name}
                            </Link>
                            {(project.aiProject || project.aiBuiltin || !project.gitRepoUrl || project.gitRepoUrl === 'ai-builtin') ? (
                              <Badge variant="secondary" className="bg-blue-100 text-blue-800">
                                IA
                              </Badge>
                            ) : null}
                          </div>
                        </TableCell>
                        <TableCell>{project.gitRepoUrl || '—'}</TableCell>
                        <TableCell>{project.gitDefaultBranch || 'main'}</TableCell>
                        <TableCell>
                          <Badge variant={statusVariant[project.status]}>
                            {project.status}
                          </Badge>
                        </TableCell>
                        <TableCell>{formatDate(project.createdAt)}</TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => openEdit(project)}
                            >
                              <Pencil size={14} />
                              Edit
                            </Button>
                            <Button
                              variant="destructive"
                              size="sm"
                              onClick={() => openDelete(project)}
                            >
                              <Trash2 size={14} />
                              Delete
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : null}
            </Card>
          </div>
        </AuthGuard>
      </main>

      <FormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        title="Create project"
        description="Define the project details for ms_gestion."
        submitLabel="Create project"
        isSubmitting={isSubmitting}
        onSubmit={onCreateSubmit}
      >
        <div className="space-y-2">
          <Label htmlFor="project-name">Name</Label>
          <Input
            id="project-name"
            value={formState.name}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, name: event.target.value }))
            }
            placeholder="Digital Banking Platform"
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="project-description">Description</Label>
          <Textarea
            id="project-description"
            value={formState.description}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, description: event.target.value }))
            }
            placeholder="Optional description"
          />
        </div>

        <div className="flex items-center gap-3">
          <Label htmlFor="project-ai-mode">Use AI-built template (no Git repo)</Label>
          <Switch
            id="project-ai-mode"
            checked={formState.useAiMode}
            onCheckedChange={(checked) =>
              setFormState((prev) => ({ ...prev, useAiMode: checked }))
            }
          />
        </div>

        {!formState.useAiMode ? (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <Label>GitHub repository</Label>
            {gitHubConnection.kind === 'notConnected' && connectUrl ? (
              <Button variant="outline" size="sm" asChild>
                <a href={connectUrl}>Connect GitHub</a>
              </Button>
            ) : null}
          </div>

          {gitHubConnection.kind === 'loading' ? (
            <p className="text-sm text-muted-foreground">Checking GitHub connection...</p>
          ) : null}

          {gitHubConnection.kind === 'unauthorized' ? (
            <p className="text-sm text-muted-foreground">Sign in to load GitHub repositories.</p>
          ) : null}

          {gitHubConnection.kind === 'error' ? (
            <p className="text-sm text-destructive">{gitHubConnection.message}</p>
          ) : null}

          {gitHubConnection.kind === 'notConnected' ? (
            <div className="space-y-2">
              <p className="text-sm text-muted-foreground">
                Connect GitHub to select a repository. You can still paste a URL manually.
              </p>
              <Input
                value={formState.gitRepoUrl}
                onChange={(event) =>
                  setFormState((prev) => ({ ...prev, gitRepoUrl: event.target.value }))
                }
                placeholder="https://github.com/org/repo"
              />
            </div>
          ) : null}

          {gitHubConnection.kind === 'connected' ? (
            <div className="space-y-2">
              {gitHubRepos.kind === 'loading' ? (
                <p className="text-sm text-muted-foreground">Loading repositories...</p>
              ) : null}

              {gitHubRepos.kind === 'error' ? (
                <p className="text-sm text-destructive">{gitHubRepos.message}</p>
              ) : null}

              {gitHubRepos.kind === 'available' ? (
                <Select
                  value={selectedRepoKey}
                  onValueChange={(value) => {
                    setSelectedRepoKey(value)
                    setSelectedBranch('')
                    setBranchState({ kind: 'idle', branches: [] })
                  }}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select a repository" />
                  </SelectTrigger>
                  <SelectContent>
                    {gitHubRepos.repos.map((repo) => (
                      <SelectItem key={repo.key} value={repo.key}>
                        {repo.owner}/{repo.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : null}

              <Input value={formState.gitRepoUrl} readOnly placeholder="Repository URL" />
            </div>
          ) : null}

          <div className="space-y-2">
            <Label>Default branch</Label>
            {branchState.kind === 'loading' ? (
              <p className="text-sm text-muted-foreground">Loading branches...</p>
            ) : null}

            {branchState.kind === 'error' ? (
              <p className="text-sm text-destructive">{branchState.message}</p>
            ) : null}

            {branchState.kind === 'available' && branchState.branches.length > 0 ? (
              <Select
                value={selectedBranch}
                onValueChange={(value) => {
                  setSelectedBranch(value)
                  setFormState((prev) => ({ ...prev, gitDefaultBranch: value }))
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select a branch" />
                </SelectTrigger>
                <SelectContent>
                  {branchState.branches.map((branch) => (
                    <SelectItem key={branch} value={branch}>
                      {branch}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <Input
                value={formState.gitDefaultBranch}
                onChange={(event) =>
                  setFormState((prev) => ({ ...prev, gitDefaultBranch: event.target.value }))
                }
                placeholder="main"
              />
            )}
          </div>
        </div>
        ) : null}
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <FormDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        title="Update project"
        description="Keep project details in sync with ms_gestion."
        submitLabel="Save changes"
        isSubmitting={isSubmitting}
        onSubmit={onEditSubmit}
      >
        <div className="space-y-2">
          <Label htmlFor="edit-project-name">Name</Label>
          <Input
            id="edit-project-name"
            value={formState.name}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, name: event.target.value }))
            }
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="edit-project-description">Description</Label>
          <Textarea
            id="edit-project-description"
            value={formState.description}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, description: event.target.value }))
            }
          />
        </div>

        <div className="flex items-center gap-3">
          <Label htmlFor="edit-project-ai-mode">Use AI-built template (no Git repo)</Label>
          <Switch
            id="edit-project-ai-mode"
            checked={formState.useAiMode}
            onCheckedChange={(checked) =>
              setFormState((prev) => ({ ...prev, useAiMode: checked }))
            }
          />
        </div>

        {!formState.useAiMode ? (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <Label>GitHub repository</Label>
            {gitHubConnection.kind === 'notConnected' && connectUrl ? (
              <Button variant="outline" size="sm" asChild>
                <a href={connectUrl}>Connect GitHub</a>
              </Button>
            ) : null}
          </div>

          {gitHubConnection.kind === 'loading' ? (
            <p className="text-sm text-muted-foreground">Checking GitHub connection...</p>
          ) : null}

          {gitHubConnection.kind === 'unauthorized' ? (
            <p className="text-sm text-muted-foreground">Sign in to load GitHub repositories.</p>
          ) : null}

          {gitHubConnection.kind === 'error' ? (
            <p className="text-sm text-destructive">{gitHubConnection.message}</p>
          ) : null}

          {gitHubConnection.kind === 'notConnected' ? (
            <div className="space-y-2">
              <p className="text-sm text-muted-foreground">
                Connect GitHub to select a repository. You can still paste a URL manually.
              </p>
              <Input
                value={formState.gitRepoUrl}
                onChange={(event) =>
                  setFormState((prev) => ({ ...prev, gitRepoUrl: event.target.value }))
                }
                placeholder="https://github.com/org/repo"
              />
            </div>
          ) : null}

          {gitHubConnection.kind === 'connected' ? (
            <div className="space-y-2">
              {gitHubRepos.kind === 'loading' ? (
                <p className="text-sm text-muted-foreground">Loading repositories...</p>
              ) : null}

              {gitHubRepos.kind === 'error' ? (
                <p className="text-sm text-destructive">{gitHubRepos.message}</p>
              ) : null}

              {gitHubRepos.kind === 'available' ? (
                <Select
                  value={selectedRepoKey}
                  onValueChange={(value) => {
                    setSelectedRepoKey(value)
                    setSelectedBranch('')
                    setBranchState({ kind: 'idle', branches: [] })
                  }}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select a repository" />
                  </SelectTrigger>
                  <SelectContent>
                    {gitHubRepos.repos.map((repo) => (
                      <SelectItem key={repo.key} value={repo.key}>
                        {repo.owner}/{repo.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : null}

              <Input value={formState.gitRepoUrl} readOnly placeholder="Repository URL" />
            </div>
          ) : null}

          <div className="space-y-2">
            <Label>Default branch</Label>
            {branchState.kind === 'loading' ? (
              <p className="text-sm text-muted-foreground">Loading branches...</p>
            ) : null}

            {branchState.kind === 'error' ? (
              <p className="text-sm text-destructive">{branchState.message}</p>
            ) : null}

            {branchState.kind === 'available' && branchState.branches.length > 0 ? (
              <Select
                value={selectedBranch}
                onValueChange={(value) => {
                  setSelectedBranch(value)
                  setFormState((prev) => ({ ...prev, gitDefaultBranch: value }))
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select a branch" />
                </SelectTrigger>
                <SelectContent>
                  {branchState.branches.map((branch) => (
                    <SelectItem key={branch} value={branch}>
                      {branch}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <Input
                value={formState.gitDefaultBranch}
                onChange={(event) =>
                  setFormState((prev) => ({ ...prev, gitDefaultBranch: event.target.value }))
                }
                placeholder="main"
              />
            )}
          </div>
        </div>
        ) : null}
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete project"
        description={
          deletingProject
            ? `Delete ${deletingProject.name}? This cannot be undone.`
            : 'Delete this project?'
        }
        confirmLabel="Delete"
        isConfirming={isDeleting}
        onConfirm={onDeleteConfirm}
      />
    </div>
  )
}
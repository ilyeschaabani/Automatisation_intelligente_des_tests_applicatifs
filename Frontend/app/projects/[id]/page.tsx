'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'
import { Database, Plus, RefreshCw, Settings, Trash2, Users } from 'lucide-react'

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
import { Textarea } from '@/components/ui/textarea'
import { apiFetch } from '@/components/profile/GitHubIntegrationCard'
import { environmentService } from '@/services/environments'
import { memberService } from '@/services/members'
import { projectService } from '@/services/projects'
import { testSuiteService } from '@/services/suites'
import { userDirectoryService } from '@/services/users'
import type { AuthUserSummary } from '@/types/auth'
import type {
  AddMemberRequest,
  CreateEnvironmentRequest,
  CreateTestSuiteRequest,
  Environment,
  Member,
  Project,
  ProjectStatus,
  TestSuite,
  UpdateEnvironmentRequest,
  UpdateTestSuiteRequest,
} from '@/types/ms-gestion'

type LoadState = 'loading' | 'ready' | 'error'

type BranchListState =
  | { kind: 'idle'; branches: string[] }
  | { kind: 'loading'; branches: string[] }
  | { kind: 'available'; branches: string[] }
  | { kind: 'error'; branches: string[]; message: string }

type EnvironmentFormState = {
  name: string
  baseUrlWeb: string
  baseUrlApi: string
  gitRepoUrl: string
  gitBranch: string
  databaseType: string
}

type SuiteFormState = {
  name: string
  description: string
  gitRepoUrl: string
  gitBranch: string
  modulePath: string
  useGitRepo: boolean
  type?: SuiteType
}

// Add suite type to form state
type SuiteType = 'WEB' | 'UNIT' | 'INTEGRATION'


type MemberFormState = {
  userId: string
}

const emptyEnvForm: EnvironmentFormState = {
  name: '',
  baseUrlWeb: '',
  baseUrlApi: '',
  gitRepoUrl: '',
  gitBranch: '',
  databaseType: '',
}

const emptySuiteForm: SuiteFormState = {
  name: '',
  description: '',
  gitRepoUrl: '',
  gitBranch: '',
  modulePath: '',
  useGitRepo: false,
  type: 'WEB',
}


const emptyMemberForm: MemberFormState = {
  userId: '',
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

const formatUserLabel = (user: AuthUserSummary) => {
  const name = [user.prenom, user.nom].filter(Boolean).join(' ').trim()
  if (name && user.email) return `${name} (${user.email})`
  return name || user.email || `User ${user.id}`
}

const normalizeRepoUrl = (url: string): string =>
  String(url || '')
    .trim()
    .toLowerCase()
    .replace(/\.git$/, '')
    .replace(/\/+$/, '')

const isRepoMandatorySuiteType = (type?: SuiteType): boolean => type === 'UNIT' || type === 'INTEGRATION'

const isWebOrApiSuiteType = (type?: SuiteType): boolean => type === 'WEB'

const shouldShowGitRepoFields = (type?: SuiteType, useGitRepo?: boolean): boolean =>
  isRepoMandatorySuiteType(type) || (isWebOrApiSuiteType(type) && Boolean(useGitRepo))

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

const normalizeVariables = (raw: string) => {
  const trimmed = raw.trim()
  if (!trimmed) return { value: undefined as string | undefined }

  try {
    let parsed: unknown = JSON.parse(trimmed)
    if (typeof parsed === 'string') {
      const inner = parsed.trim()
      if (inner.startsWith('{') || inner.startsWith('[')) {
        parsed = JSON.parse(inner)
      }
    }
    return { value: JSON.stringify(parsed) }
  } catch {
    return { error: 'Variables must be valid JSON.' }
  }
}

export default function ProjectDetailsPage() {
  const params = useParams<{ id?: string | string[] }>()
  const id = Array.isArray(params?.id) ? params?.id[0] : params?.id
  const projectId = Number(id)
  const hasProjectId = Number.isFinite(projectId)

  const [project, setProject] = useState<Project | null>(null)
  const [projectState, setProjectState] = useState<LoadState>('loading')
  const [projectError, setProjectError] = useState<string | null>(null)

  const [environments, setEnvironments] = useState<Environment[]>([])
  const [envState, setEnvState] = useState<LoadState>('loading')
  const [envError, setEnvError] = useState<string | null>(null)

  const [suites, setSuites] = useState<TestSuite[]>([])
  const [suiteState, setSuiteState] = useState<LoadState>('loading')
  const [suiteError, setSuiteError] = useState<string | null>(null)

  const [members, setMembers] = useState<Member[]>([])
  const [memberState, setMemberState] = useState<LoadState>('loading')
  const [memberError, setMemberError] = useState<string | null>(null)

  const [users, setUsers] = useState<AuthUserSummary[]>([])
  const [usersState, setUsersState] = useState<LoadState>('loading')
  const [usersError, setUsersError] = useState<string | null>(null)

  const [envForm, setEnvForm] = useState<EnvironmentFormState>(emptyEnvForm)
  const [suiteForm, setSuiteForm] = useState<SuiteFormState>(emptySuiteForm)
  const [memberForm, setMemberForm] = useState<MemberFormState>(emptyMemberForm)

  const [formError, setFormError] = useState<string | null>(null)

  // GitHub repos for select when suite type is UNIT
  const [gitRepos, setGitRepos] = useState<
    Array<{
      key: string
      label: string
      url: string
      owner: string
      name: string
      defaultBranch?: string
      branches?: string[]
    }>
  >([])
  const [gitReposState, setGitReposState] = useState<LoadState>('ready')
  const [gitReposMessage, setGitReposMessage] = useState<string | null>(null)

  const [branchState, setBranchState] = useState<BranchListState>({ kind: 'idle', branches: [] })
  const [repoBranchCache, setRepoBranchCache] = useState<Record<string, string[]>>({})

  // Dedicated branch state for environment form (separate from suite form)
  const [envBranchState, setEnvBranchState] = useState<BranchListState>({ kind: 'idle', branches: [] })

  const [envCreateOpen, setEnvCreateOpen] = useState(false)
  const [envEditOpen, setEnvEditOpen] = useState(false)
  const [envEditing, setEnvEditing] = useState<Environment | null>(null)
  const [envDeleteOpen, setEnvDeleteOpen] = useState(false)
  const [envDeleting, setEnvDeleting] = useState<Environment | null>(null)
  const [isEnvSubmitting, setIsEnvSubmitting] = useState(false)
  const [isEnvDeleting, setIsEnvDeleting] = useState(false)

  const [suiteCreateOpen, setSuiteCreateOpen] = useState(false)
  const [suiteEditOpen, setSuiteEditOpen] = useState(false)
  const [suiteEditing, setSuiteEditing] = useState<TestSuite | null>(null)
  const [suiteDeleteOpen, setSuiteDeleteOpen] = useState(false)
  const [suiteDeleting, setSuiteDeleting] = useState<TestSuite | null>(null)
  const [isSuiteSubmitting, setIsSuiteSubmitting] = useState(false)
  const [isSuiteDeleting, setIsSuiteDeleting] = useState(false)

  const [memberAddOpen, setMemberAddOpen] = useState(false)
  const [memberDeleteOpen, setMemberDeleteOpen] = useState(false)
  const [memberDeleting, setMemberDeleting] = useState<Member | null>(null)
  const [isMemberSubmitting, setIsMemberSubmitting] = useState(false)
  const [isMemberDeleting, setIsMemberDeleting] = useState(false)

  const loadProject = async () => {
    if (!hasProjectId) return
    setProjectState('loading')
    setProjectError(null)
    try {
      const data = await projectService.getById(projectId)
      setProject(data)
      setProjectState('ready')
    } catch (err) {
      setProject(null)
      setProjectState('error')
      setProjectError(err instanceof Error ? err.message : 'Failed to load project')
    }
  }

  const loadEnvironments = async () => {
    if (!hasProjectId) return
    setEnvState('loading')
    setEnvError(null)
    try {
      const data = await environmentService.getAll(projectId)
      setEnvironments(Array.isArray(data) ? data : [])
      setEnvState('ready')
    } catch (err) {
      setEnvironments([])
      setEnvState('error')
      setEnvError(err instanceof Error ? err.message : 'Failed to load environments')
    }
  }

  const loadSuites = async () => {
    if (!hasProjectId) return
    setSuiteState('loading')
    setSuiteError(null)
    try {
      const data = await testSuiteService.getAll(projectId)
      setSuites(Array.isArray(data) ? data : [])
      setSuiteState('ready')
    } catch (err) {
      setSuites([])
      setSuiteState('error')
      setSuiteError(err instanceof Error ? err.message : 'Failed to load suites')
    }
  }

  const loadMembers = async () => {
    if (!hasProjectId) return
    setMemberState('loading')
    setMemberError(null)
    try {
      const data = await memberService.getAll(projectId)
      setMembers(Array.isArray(data) ? data : [])
      setMemberState('ready')
    } catch (err) {
      setMembers([])
      setMemberState('error')
      setMemberError(err instanceof Error ? err.message : 'Failed to load members')
    }
  }

  const loadUsers = async () => {
    setUsersState('loading')
    setUsersError(null)
    try {
      const data = await userDirectoryService.getAll()
      setUsers(Array.isArray(data) ? data : [])
      setUsersState('ready')
    } catch (err) {
      setUsers([])
      setUsersState('error')
      setUsersError(err instanceof Error ? err.message : 'Failed to load users')
    }
  }

  const loadAll = async () => {
    await Promise.all([loadProject(), loadEnvironments(), loadSuites(), loadMembers(), loadUsers()])
  }

  useEffect(() => {
    if (!hasProjectId) return
    void loadAll()
  }, [hasProjectId])

  const headerSubtitle = useMemo(() => {
    if (!project) return 'Manage environments, suites, and members.'
    return project.description || 'Manage environments, suites, and members.'
  }, [project])

  const resetEnvForm = () => {
    setEnvForm(emptyEnvForm)
    setFormError(null)
  }

  const resetSuiteForm = () => {
    setSuiteForm(emptySuiteForm)
    setFormError(null)
  }

  const resetMemberForm = () => {
    setMemberForm(emptyMemberForm)
    setFormError(null)
  }

  const openEnvCreate = () => {
    resetEnvForm()
    setEnvCreateOpen(true)
  }

  const openEnvEdit = (env: Environment) => {
    setEnvEditing(env)
    setEnvForm({
      name: env.name ?? '',
      baseUrlWeb: env.baseUrlWeb ?? '',
      baseUrlApi: env.baseUrlApi ?? '',
      gitRepoUrl: (env as any).gitRepoUrl ?? '',
      gitBranch: (env as any).gitBranch ?? '',
      databaseType: (env as any).databaseType ?? '',
    })
    setEnvEditOpen(true)
  }

  const openEnvDelete = (env: Environment) => {
    setEnvDeleting(env)
    setEnvDeleteOpen(true)
  }

  const openSuiteCreate = () => {
    resetSuiteForm()
    setSuiteForm((prev) => ({ ...prev, type: 'WEB', useGitRepo: false }))
    setSuiteCreateOpen(true)
  }

  const openSuiteEdit = (suite: TestSuite) => {
    setSuiteEditing(suite)
    const type = (suite.type ?? 'WEB') as SuiteType
    const gitBranchRaw = suite.gitBranch ?? ''
    const gitBranch = (type === 'UNIT' || type === 'INTEGRATION') && !gitBranchRaw.trim() ? 'main' : gitBranchRaw
    setSuiteForm({
      name: suite.name ?? '',
      description: suite.description ?? '',
      gitRepoUrl: suite.gitRepoUrl ?? '',
      gitBranch,
      modulePath: suite.modulePath ?? '',
      useGitRepo: isRepoMandatorySuiteType(type) || Boolean((suite.gitRepoUrl ?? '').trim()),
      type,
    })
    setSuiteEditOpen(true)
  }

  const openSuiteDelete = (suite: TestSuite) => {
    setSuiteDeleting(suite)
    setSuiteDeleteOpen(true)
  }

  const openMemberAdd = () => {
    resetMemberForm()
    setMemberAddOpen(true)
    void loadUsers()
  }

  const openMemberDelete = (member: Member) => {
    setMemberDeleting(member)
    setMemberDeleteOpen(true)
  }

  const submitEnvCreate = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!hasProjectId) return
    setIsEnvSubmitting(true)
    setFormError(null)

    try {
      const payload: CreateEnvironmentRequest = {
        name: envForm.name.trim(),
        baseUrlWeb: envForm.baseUrlWeb.trim() || undefined,
        baseUrlApi: envForm.baseUrlApi.trim() || undefined,
        gitRepoUrl: envForm.gitRepoUrl.trim() || undefined,
        gitBranch: envForm.gitBranch.trim() || undefined,
        databaseType: envForm.databaseType.trim() || undefined,
      }
      await environmentService.create(projectId, payload)
      setEnvCreateOpen(false)
      resetEnvForm()
      await loadEnvironments()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to create environment')
    } finally {
      setIsEnvSubmitting(false)
    }
  }

  // Fetch GitHub repos exposed by backend
  const fetchGitHubRepos = async () => {
    setGitReposState('loading')
    setGitReposMessage(null)
    setBranchState({ kind: 'idle', branches: [] })
    try {
      const res = await apiFetch('/api/github/repos')

      if (res.status === 401) {
        setGitRepos([])
        setGitReposState('ready')
        setGitReposMessage('Unauthorized. Please login again.')
        return
      }

      if (res.status === 404) {
        // Backend semantics: 404 means GitHub is not connected.
        setGitRepos([])
        setGitReposState('ready')
        setGitReposMessage('No GitHub account connected. Connect GitHub in your profile to select a repository.')
        return
      }

      if (!res.ok) {
        const text = await res.text().catch(() => '')
        setGitRepos([])
        setGitReposState('error')
        setGitReposMessage(`Unable to load repos (${res.status})${text ? `: ${text}` : ''}`)
        return
      }

      const data = (await res.json().catch(() => null)) as unknown
      const rawList: unknown[] = Array.isArray(data)
        ? data
        : data && typeof data === 'object' && Array.isArray((data as any).repos)
          ? ((data as any).repos as unknown[])
          : []

      const normalized = rawList
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
                : typeof (r as any).clone_url === 'string'
                  ? String((r as any).clone_url)
                  : ''

          const defaultBranch: string | undefined =
            typeof (r as any).default_branch === 'string'
              ? String((r as any).default_branch)
              : typeof (r as any).defaultBranch === 'string'
                ? String((r as any).defaultBranch)
                : undefined

          const branches = extractBranchNames((r as any).branches)

          const key =
            typeof r.id === 'number' || typeof r.id === 'string'
              ? String(r.id)
              : `${owner}/${name || 'repo'}:${idx}`

          if (!name || !owner || !url) return null
          return {
            key,
            label: owner ? `${owner}/${name}` : name,
            url,
            owner,
            name,
            defaultBranch,
            branches,
          }
        })
        .filter(Boolean) as Array<{
        key: string
        label: string
        url: string
        owner: string
        name: string
        defaultBranch?: string
        branches?: string[]
      }>

      setGitRepos(normalized)
      setGitReposState('ready')
    } catch (err) {
      setGitRepos([])
      setGitReposState('error')
      setGitReposMessage(err instanceof Error ? err.message : 'Network error')
    }
  }

  useEffect(() => {
    if (!suiteCreateOpen && !suiteEditOpen) return

    const showGitRepoFields = shouldShowGitRepoFields(suiteForm.type, suiteForm.useGitRepo)
    if (showGitRepoFields) {
      void fetchGitHubRepos()
    }
  }, [suiteCreateOpen, suiteEditOpen, suiteForm.type, suiteForm.useGitRepo])

  // Fetch GitHub repos when environment dialog opens
  useEffect(() => {
    if (!envCreateOpen && !envEditOpen) return
    void fetchGitHubRepos()
    setEnvBranchState({ kind: 'idle', branches: [] })
  }, [envCreateOpen, envEditOpen])

  // Fetch branches when env form's gitRepoUrl changes
  useEffect(() => {
    if (!envCreateOpen && !envEditOpen) return
    const url = envForm.gitRepoUrl.trim()
    if (!url) {
      setEnvBranchState({ kind: 'idle', branches: [] })
      return
    }
    if (gitReposState !== 'ready' || gitRepos.length === 0) return

    const repo = gitRepos.find((r) => normalizeRepoUrl(r.url) === normalizeRepoUrl(url))
    if (!repo) return

    const localBranches = Array.isArray(repo.branches) ? repo.branches : []
    if (localBranches.length > 0) {
      setEnvBranchState({ kind: 'available', branches: localBranches })
      if (!envForm.gitBranch.trim() && repo.defaultBranch) {
        setEnvForm((prev) => ({ ...prev, gitBranch: repo.defaultBranch! }))
      }
      return
    }

    void (async () => {
      setEnvBranchState({ kind: 'loading', branches: [] })
      const cacheKey = `${repo.owner}/${repo.name}`
      if (repoBranchCache[cacheKey]?.length) {
        setEnvBranchState({ kind: 'available', branches: repoBranchCache[cacheKey] })
        return
      }
      try {
        const res = await apiFetch(`/api/github/repos/${repo.owner}/${repo.name}/branches`)
        if (!res.ok) { setEnvBranchState({ kind: 'error', branches: [] }); return }
        const data = await res.json().catch(() => null)
        const branches = extractBranchNames(data)
        setRepoBranchCache((prev) => ({ ...prev, [cacheKey]: branches }))
        setEnvBranchState({ kind: 'available', branches })
        if (!envForm.gitBranch.trim() && repo.defaultBranch) {
          setEnvForm((prev) => ({ ...prev, gitBranch: repo.defaultBranch! }))
        }
      } catch {
        setEnvBranchState({ kind: 'error', branches: [] })
      }
    })()
  }, [envForm.gitRepoUrl, envCreateOpen, envEditOpen, gitReposState, gitRepos])

  const fetchBranches = async (owner: string, repo: string) => {
    const cacheKey = `${owner}/${repo}`
    if (repoBranchCache[cacheKey]?.length) {
      setBranchState({ kind: 'available', branches: repoBranchCache[cacheKey] })
      return repoBranchCache[cacheKey]
    }

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
      setRepoBranchCache((prev) => ({ ...prev, [cacheKey]: branches }))
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
    const showGitRepoFields = shouldShowGitRepoFields(suiteForm.type, suiteForm.useGitRepo)
    if (!showGitRepoFields) return
    if (!suiteCreateOpen && !suiteEditOpen) return
    if (!suiteForm.gitRepoUrl.trim()) {
      setBranchState({ kind: 'idle', branches: [] })
      return
    }
    if (gitReposState !== 'ready' || gitRepos.length === 0) return

    const normalizedUrl = normalizeRepoUrl(suiteForm.gitRepoUrl)
    const repo = gitRepos.find((r) => normalizeRepoUrl(r.url) === normalizedUrl)
    if (!repo) return

    const applyBranch = (branches: string[]) => {
      if (branches.length === 0) return

      const existing = suiteForm.gitBranch.trim()
      const defaultBranch = repo.defaultBranch?.trim() || 'main'
      const candidate =
        (existing && branches.includes(existing) ? existing : '') ||
        (defaultBranch && branches.includes(defaultBranch) ? defaultBranch : '') ||
        branches[0]

      if (!candidate) return
      setSuiteForm((prev) => {
        if (prev.gitBranch === candidate) return prev
        return { ...prev, gitBranch: candidate }
      })
    }

    const localBranches = Array.isArray(repo.branches) ? repo.branches : []
    if (localBranches.length > 0) {
      setBranchState({ kind: 'available', branches: localBranches })
      applyBranch(localBranches)
      return
    }

    void (async () => {
      const branches = await fetchBranches(repo.owner, repo.name)
      applyBranch(branches)
    })()
  }, [suiteForm.type, suiteForm.useGitRepo, suiteForm.gitRepoUrl, suiteCreateOpen, suiteEditOpen, gitReposState, gitRepos])

  useEffect(() => {
    const showGitRepoFields = shouldShowGitRepoFields(suiteForm.type, suiteForm.useGitRepo)
    if (!showGitRepoFields) return
    if (gitReposState !== 'ready') return
    if (gitRepos.length === 0) return
    if (!suiteForm.gitRepoUrl.trim()) return

    const current = suiteForm.gitRepoUrl
    const normalizedCurrent = normalizeRepoUrl(current)
    const match = gitRepos.find((repo) => normalizeRepoUrl(repo.url) === normalizedCurrent)
    if (!match || match.url === current) return

    setSuiteForm((prev) => {
      if (prev.gitRepoUrl !== current) return prev
      return { ...prev, gitRepoUrl: match.url }
    })
  }, [suiteForm.type, suiteForm.useGitRepo, suiteForm.gitRepoUrl, gitReposState, gitRepos])

  const submitEnvEdit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!hasProjectId || !envEditing) return
    setIsEnvSubmitting(true)
    setFormError(null)

    try {
      const payload: UpdateEnvironmentRequest = {
        name: envForm.name.trim(),
        baseUrlWeb: envForm.baseUrlWeb.trim() || undefined,
        baseUrlApi: envForm.baseUrlApi.trim() || undefined,
        gitRepoUrl: envForm.gitRepoUrl.trim() || undefined,
        gitBranch: envForm.gitBranch.trim() || undefined,
        databaseType: envForm.databaseType.trim() || undefined,
      }
      await environmentService.update(projectId, envEditing.id, payload)
      setEnvEditOpen(false)
      setEnvEditing(null)
      await loadEnvironments()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to update environment')
    } finally {
      setIsEnvSubmitting(false)
    }
  }

  const confirmEnvDelete = async () => {
    if (!hasProjectId || !envDeleting) return
    setIsEnvDeleting(true)
    try {
      await environmentService.delete(projectId, envDeleting.id)
      setEnvDeleteOpen(false)
      setEnvDeleting(null)
      await loadEnvironments()
    } catch (err) {
      setEnvError(err instanceof Error ? err.message : 'Failed to delete environment')
    } finally {
      setIsEnvDeleting(false)
    }
  }

  const submitSuiteCreate = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!hasProjectId) return
    setIsSuiteSubmitting(true)
    setFormError(null)

    try {
      const showGitRepoFields = shouldShowGitRepoFields(suiteForm.type, suiteForm.useGitRepo)
      const payload: CreateTestSuiteRequest = {
        name: suiteForm.name.trim(),
        type: (suiteForm.type as any) || undefined,
        description: suiteForm.description.trim() || undefined,
        gitRepoUrl: showGitRepoFields ? suiteForm.gitRepoUrl.trim() || undefined : undefined,
        gitBranch: showGitRepoFields ? suiteForm.gitBranch.trim() || undefined : undefined,
        modulePath:
          suiteForm.type === 'UNIT' || suiteForm.type === 'INTEGRATION'
            ? suiteForm.modulePath.trim() || undefined
            : undefined,
      }
      await testSuiteService.create(projectId, payload)
      setSuiteCreateOpen(false)
      resetSuiteForm()
      await loadSuites()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to create test suite')
    } finally {
      setIsSuiteSubmitting(false)
    }
  }

  const submitSuiteEdit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!hasProjectId || !suiteEditing) return
    setIsSuiteSubmitting(true)
    setFormError(null)

    try {
      const showGitRepoFields = shouldShowGitRepoFields(suiteForm.type, suiteForm.useGitRepo)
      const payload: UpdateTestSuiteRequest = {
        name: suiteForm.name.trim(),
        type: (suiteForm.type as any) || undefined,
        description: suiteForm.description.trim() || undefined,
        gitRepoUrl: showGitRepoFields ? suiteForm.gitRepoUrl.trim() || undefined : undefined,
        gitBranch: showGitRepoFields ? suiteForm.gitBranch.trim() || undefined : undefined,
        modulePath:
          suiteForm.type === 'UNIT' || suiteForm.type === 'INTEGRATION'
            ? suiteForm.modulePath.trim() || undefined
            : undefined,
      }
      await testSuiteService.update(projectId, suiteEditing.id, payload)
      setSuiteEditOpen(false)
      setSuiteEditing(null)
      await loadSuites()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to update test suite')
    } finally {
      setIsSuiteSubmitting(false)
    }
  }

  const confirmSuiteDelete = async () => {
    if (!hasProjectId || !suiteDeleting) return
    setIsSuiteDeleting(true)
    try {
      await testSuiteService.delete(projectId, suiteDeleting.id)
      setSuiteDeleteOpen(false)
      setSuiteDeleting(null)
      await loadSuites()
    } catch (err) {
      setSuiteError(err instanceof Error ? err.message : 'Failed to delete test suite')
    } finally {
      setIsSuiteDeleting(false)
    }
  }

  const submitMemberAdd = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!hasProjectId) return
    setIsMemberSubmitting(true)
    setFormError(null)

    const userId = Number(memberForm.userId)
    if (!Number.isFinite(userId)) {
      setFormError('Select a user.')
      setIsMemberSubmitting(false)
      return
    }

    try {
      const payload: AddMemberRequest = {
        userId,
      }
      await memberService.add(projectId, payload)
      setMemberAddOpen(false)
      resetMemberForm()
      await loadMembers()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to add member')
    } finally {
      setIsMemberSubmitting(false)
    }
  }

  const confirmMemberDelete = async () => {
    if (!hasProjectId || !memberDeleting) return
    setIsMemberDeleting(true)
    try {
      await memberService.remove(projectId, memberDeleting.userId)
      setMemberDeleteOpen(false)
      setMemberDeleting(null)
      await loadMembers()
    } catch (err) {
      setMemberError(err instanceof Error ? err.message : 'Failed to remove member')
    } finally {
      setIsMemberDeleting(false)
    }
  }

  const availableUsers = useMemo(() => {
    const memberIds = new Set(members.map((member) => member.userId))
    return users.filter((user) => !memberIds.has(user.id))
  }, [members, users])

  if (!hasProjectId) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">
        Invalid project id.
      </div>
    )
  }

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />
        <AuthGuard>
          <div className="p-6 max-w-7xl space-y-8">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div>
                <h1 className="text-3xl font-bold text-foreground">
                  {project?.name || 'Project'}
                </h1>
                <p className="text-muted-foreground mt-1">{headerSubtitle}</p>
              </div>
              <div className="flex items-center gap-2">
                {project?.status ? (
                  <Badge variant={statusVariant[project.status]}>{project.status}</Badge>
                ) : null}
                <Button variant="outline" onClick={loadAll} className="gap-2">
                  <RefreshCw size={16} />
                  Refresh
                </Button>
              </div>
            </div>

            {projectState === 'loading' ? (
              <Card className="p-6 text-sm text-muted-foreground">Chargement du projet…</Card>
            ) : null}
            {projectState === 'error' ? (
              <Card className="p-6 text-sm text-destructive">{projectError}</Card>
            ) : null}

            <Card className="p-6">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Database size={18} />
                  <h2 className="text-lg font-semibold">Environnements</h2>
                </div>
                <Button size="sm" onClick={openEnvCreate} className="gap-2">
                  <Plus size={14} />
                  Ajouter un environnement
                </Button>
              </div>

              {envError ? <p className="text-sm text-destructive mt-4">{envError}</p> : null}
              {envState === 'loading' ? (
                <p className="text-sm text-muted-foreground mt-4">Chargement des environnements…</p>
              ) : null}

              {envState !== 'loading' && environments.length === 0 ? (
                <p className="text-sm text-muted-foreground mt-4">Aucun environnement pour l'instant.</p>
              ) : null}

              {envState !== 'loading' && environments.length > 0 ? (
                <div className="mt-4">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Nom</TableHead>
                        <TableHead>URL de base (Web)</TableHead>
                        <TableHead>URL de base (API)</TableHead>
                        <TableHead>Créé le</TableHead>
                        <TableHead className="text-right">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {environments.map((env) => (
                        <TableRow key={env.id}>
                          <TableCell className="font-medium">{env.name}</TableCell>
                          <TableCell>{env.baseUrlWeb || '—'}</TableCell>
                          <TableCell>{env.baseUrlApi || '—'}</TableCell>
                          <TableCell>{formatDate(env.createdAt)}</TableCell>
                          <TableCell className="text-right">
                            <div className="flex justify-end gap-2">
                              <Button size="sm" variant="outline" onClick={() => openEnvEdit(env)}>
                                <Settings size={14} />
                                Modifier
                              </Button>
                              <Button size="sm" variant="destructive" onClick={() => openEnvDelete(env)}>
                                <Trash2 size={14} />
                                Supprimer
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              ) : null}
            </Card>

            <Card className="p-6">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Settings size={18} />
                  <h2 className="text-lg font-semibold">Suites de test</h2>
                </div>
                <Button size="sm" onClick={openSuiteCreate} className="gap-2">
                  <Plus size={14} />
                  Ajouter une suite
                </Button>
              </div>

              {suiteError ? <p className="text-sm text-destructive mt-4">{suiteError}</p> : null}
              {suiteState === 'loading' ? (
                <p className="text-sm text-muted-foreground mt-4">Chargement des suites…</p>
              ) : null}

              {suiteState !== 'loading' && suites.length === 0 ? (
                <p className="text-sm text-muted-foreground mt-4">Aucune suite pour l'instant.</p>
              ) : null}

              {suiteState !== 'loading' && suites.length > 0 ? (
                <div className="mt-4">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Nom</TableHead>
                        <TableHead>Description</TableHead>
                        <TableHead>Créé le</TableHead>
                        <TableHead className="text-right">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {suites.map((suite) => (
                        <TableRow key={suite.id}>
                          <TableCell className="font-medium">{suite.name}</TableCell>
                          <TableCell>{suite.description || '—'}</TableCell>
                          <TableCell>{formatDate(suite.createdAt)}</TableCell>
                          <TableCell className="text-right">
                            <div className="flex justify-end gap-2">
                              <Button size="sm" variant="outline" asChild>
                                <Link href={`/projects/${projectId}/suites/${suite.id}`}>
                                  Voir les cas
                                </Link>
                              </Button>
                              <Button size="sm" variant="outline" onClick={() => openSuiteEdit(suite)}>
                                <Settings size={14} />
                                Modifier
                              </Button>
                              <Button size="sm" variant="destructive" onClick={() => openSuiteDelete(suite)}>
                                <Trash2 size={14} />
                                Supprimer
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              ) : null}
            </Card>

            <Card className="p-6">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Users size={18} />
                  <h2 className="text-lg font-semibold">Membres du projet</h2>
                </div>
                <Button size="sm" onClick={openMemberAdd} className="gap-2">
                  <Plus size={14} />
                  Ajouter un membre
                </Button>
              </div>

              {memberError ? <p className="text-sm text-destructive mt-4">{memberError}</p> : null}
              {memberState === 'loading' ? (
                <p className="text-sm text-muted-foreground mt-4">Chargement des membres…</p>
              ) : null}

              {memberState !== 'loading' && members.length === 0 ? (
                <p className="text-sm text-muted-foreground mt-4">Aucun membre pour l'instant.</p>
              ) : null}

              {memberState !== 'loading' && members.length > 0 ? (
                <div className="mt-4">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Membre</TableHead>
                        <TableHead className="text-right">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {members.map((member) => (
                        <TableRow key={member.userId}>
                          <TableCell className="font-medium">
                            <span className="flex items-center gap-2">
                              {(() => {
                                const u = users.find(u => u.id === member.userId)
                                if (!u) return `Utilisateur #${member.userId}`
                                return formatUserLabel(u)
                              })()}
                              {project?.createdBy === member.userId && (
                                <span className="inline-flex items-center rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-950 dark:text-amber-400">
                                  Owner
                                </span>
                              )}
                            </span>
                          </TableCell>
                          <TableCell className="text-right">
                            <Button
                              size="sm"
                              variant="destructive"
                              onClick={() => openMemberDelete(member)}
                            >
                              <Trash2 size={14} />
                              Remove
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              ) : null}
            </Card>
          </div>
        </AuthGuard>
      </main>

      <FormDialog
        open={envCreateOpen}
        onOpenChange={setEnvCreateOpen}
        title="Ajouter un environnement"
        description="Définissez les URLs et variables de cet environnement."
        submitLabel="Créer l'environnement"
        isSubmitting={isEnvSubmitting}
        onSubmit={submitEnvCreate}
      >
        <div className="space-y-2">
          <Label htmlFor="env-name">Nom</Label>
          <Input
            id="env-name"
            value={envForm.name}
            onChange={(event) => setEnvForm((prev) => ({ ...prev, name: event.target.value }))}
            placeholder="Staging"
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="env-web">Base URL (Web)</Label>
          <Input
            id="env-web"
            value={envForm.baseUrlWeb}
            onChange={(event) =>
              setEnvForm((prev) => ({ ...prev, baseUrlWeb: event.target.value }))
            }
            placeholder="https://staging.example.com"
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="env-api">Base URL (API)</Label>
          <Input
            id="env-api"
            value={envForm.baseUrlApi}
            onChange={(event) =>
              setEnvForm((prev) => ({ ...prev, baseUrlApi: event.target.value }))
            }
            placeholder="https://api.staging.example.com"
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="env-git-repo">Dépôt du code source <span className="text-muted-foreground text-xs">(pour les tests UNIT / INTEGRATION)</span></Label>
          <Select
            value={envForm.gitRepoUrl}
            onValueChange={(v) => setEnvForm((prev) => ({ ...prev, gitRepoUrl: v, gitBranch: '' }))}
            disabled={gitReposState === 'loading'}
          >
            <SelectTrigger id="env-git-repo">
              <SelectValue placeholder={
                gitReposState === 'loading' ? 'Chargement…' :
                gitRepos.length > 0 ? 'Sélectionner un dépôt' : 'Aucun dépôt GitHub connecté'
              } />
            </SelectTrigger>
            <SelectContent>
              {gitReposState === 'loading' && <SelectItem value="__loading__" disabled>Chargement…</SelectItem>}
              {gitReposState === 'ready' && gitRepos.length === 0 && <SelectItem value="__none__" disabled>Aucun dépôt GitHub connecté</SelectItem>}
              {gitRepos.map((r) => <SelectItem key={r.key} value={r.url}>{r.label}</SelectItem>)}
            </SelectContent>
          </Select>
          {gitReposMessage && <p className="text-xs text-muted-foreground">{gitReposMessage}</p>}
        </div>
        <div className="space-y-2">
          <Label htmlFor="env-git-branch">Branche</Label>
          <Select
            value={envForm.gitBranch}
            onValueChange={(v) => setEnvForm((prev) => ({ ...prev, gitBranch: v }))}
            disabled={!envForm.gitRepoUrl.trim()}
          >
            <SelectTrigger id="env-git-branch">
              <SelectValue placeholder={
                !envForm.gitRepoUrl.trim() ? 'Sélectionnez d\'abord un dépôt' :
                envBranchState.kind === 'loading' ? 'Chargement des branches…' :
                envBranchState.kind === 'available' && envBranchState.branches.length > 0 ? 'Sélectionner une branche' : 'Aucune branche'
              } />
            </SelectTrigger>
            <SelectContent>
              {envBranchState.kind === 'loading' && <SelectItem value="__loading__" disabled>Chargement…</SelectItem>}
              {envBranchState.kind === 'error' && <SelectItem value="__error__" disabled>Échec du chargement des branches</SelectItem>}
              {envBranchState.kind === 'available' && envForm.gitBranch.trim() && !envBranchState.branches.includes(envForm.gitBranch.trim()) && (
                <SelectItem value={envForm.gitBranch.trim()}>{envForm.gitBranch.trim()} (actuelle)</SelectItem>
              )}
              {envBranchState.kind === 'available' && envBranchState.branches.map((b) => (
                <SelectItem key={b} value={b}>{b}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="env-db-type">Type de base de données <span className="text-muted-foreground text-xs">(pour les tests INTEGRATION)</span></Label>
          <Select value={envForm.databaseType} onValueChange={(v) => setEnvForm((prev) => ({ ...prev, databaseType: v }))}>
            <SelectTrigger id="env-db-type">
              <SelectValue placeholder="Sélectionner un type de base de données" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="H2">H2 (in-memory)</SelectItem>
              <SelectItem value="POSTGRESQL">PostgreSQL</SelectItem>
              <SelectItem value="MYSQL">MySQL</SelectItem>
              <SelectItem value="MONGODB">MongoDB</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <FormDialog
        open={envEditOpen}
        onOpenChange={setEnvEditOpen}
        title="Modifier l'environnement"
        description="Mettez à jour la configuration de l'environnement."
        submitLabel="Enregistrer"
        isSubmitting={isEnvSubmitting}
        onSubmit={submitEnvEdit}
      >
        <div className="space-y-2">
          <Label htmlFor="env-edit-name">Nom</Label>
          <Input
            id="env-edit-name"
            value={envForm.name}
            onChange={(event) => setEnvForm((prev) => ({ ...prev, name: event.target.value }))}
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="env-edit-web">Base URL (Web)</Label>
          <Input
            id="env-edit-web"
            value={envForm.baseUrlWeb}
            onChange={(event) =>
              setEnvForm((prev) => ({ ...prev, baseUrlWeb: event.target.value }))
            }
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="env-edit-api">Base URL (API)</Label>
          <Input
            id="env-edit-api"
            value={envForm.baseUrlApi}
            onChange={(event) =>
              setEnvForm((prev) => ({ ...prev, baseUrlApi: event.target.value }))
            }
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="env-edit-git-repo">Dépôt du code source <span className="text-muted-foreground text-xs">(pour les tests UNIT / INTEGRATION)</span></Label>
          <Select
            value={envForm.gitRepoUrl}
            onValueChange={(v) => setEnvForm((prev) => ({ ...prev, gitRepoUrl: v, gitBranch: '' }))}
            disabled={gitReposState === 'loading'}
          >
            <SelectTrigger id="env-edit-git-repo">
              <SelectValue placeholder={
                gitReposState === 'loading' ? 'Chargement…' :
                gitRepos.length > 0 ? 'Sélectionner un dépôt' : 'Aucun dépôt GitHub connecté'
              } />
            </SelectTrigger>
            <SelectContent>
              {gitReposState === 'loading' && <SelectItem value="__loading__" disabled>Chargement…</SelectItem>}
              {gitReposState === 'ready' && gitRepos.length === 0 && <SelectItem value="__none__" disabled>Aucun dépôt GitHub connecté</SelectItem>}
              {gitRepos.map((r) => <SelectItem key={r.key} value={r.url}>{r.label}</SelectItem>)}
            </SelectContent>
          </Select>
          {gitReposMessage && <p className="text-xs text-muted-foreground">{gitReposMessage}</p>}
        </div>
        <div className="space-y-2">
          <Label htmlFor="env-edit-git-branch">Branche</Label>
          <Select
            value={envForm.gitBranch}
            onValueChange={(v) => setEnvForm((prev) => ({ ...prev, gitBranch: v }))}
            disabled={!envForm.gitRepoUrl.trim()}
          >
            <SelectTrigger id="env-edit-git-branch">
              <SelectValue placeholder={
                !envForm.gitRepoUrl.trim() ? 'Sélectionnez d\'abord un dépôt' :
                envBranchState.kind === 'loading' ? 'Chargement des branches…' :
                envBranchState.kind === 'available' && envBranchState.branches.length > 0 ? 'Sélectionner une branche' : 'Aucune branche'
              } />
            </SelectTrigger>
            <SelectContent>
              {envBranchState.kind === 'loading' && <SelectItem value="__loading__" disabled>Chargement…</SelectItem>}
              {envBranchState.kind === 'error' && <SelectItem value="__error__" disabled>Échec du chargement des branches</SelectItem>}
              {envBranchState.kind === 'available' && envForm.gitBranch.trim() && !envBranchState.branches.includes(envForm.gitBranch.trim()) && (
                <SelectItem value={envForm.gitBranch.trim()}>{envForm.gitBranch.trim()} (actuelle)</SelectItem>
              )}
              {envBranchState.kind === 'available' && envBranchState.branches.map((b) => (
                <SelectItem key={b} value={b}>{b}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="env-edit-db-type">Type de base de données <span className="text-muted-foreground text-xs">(pour les tests INTEGRATION)</span></Label>
          <Select value={envForm.databaseType} onValueChange={(v) => setEnvForm((prev) => ({ ...prev, databaseType: v }))}>
            <SelectTrigger id="env-edit-db-type">
              <SelectValue placeholder="Sélectionner un type de base de données" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="H2">H2 (in-memory)</SelectItem>
              <SelectItem value="POSTGRESQL">PostgreSQL</SelectItem>
              <SelectItem value="MYSQL">MySQL</SelectItem>
              <SelectItem value="MONGODB">MongoDB</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <ConfirmDialog
        open={envDeleteOpen}
        onOpenChange={setEnvDeleteOpen}
        title="Supprimer l'environnement"
        description={envDeleting ? `Supprimer « ${envDeleting.name} » ?` : 'Supprimer l\'environnement ?'}
        confirmLabel="Supprimer"
        isConfirming={isEnvDeleting}
        onConfirm={confirmEnvDelete}
      />

      <FormDialog
        open={suiteCreateOpen}
        onOpenChange={setSuiteCreateOpen}
        title="Ajouter une suite de test"
        description="Regroupez les cas de test de ce projet."
        submitLabel="Créer la suite"
        isSubmitting={isSuiteSubmitting}
        onSubmit={submitSuiteCreate}
        disableSubmit={false}
      >
        <div className="space-y-2">
          <Label htmlFor="suite-name">Nom</Label>
          <Input
            id="suite-name"
            value={suiteForm.name}
            onChange={(event) => setSuiteForm((prev) => ({ ...prev, name: event.target.value }))}
            placeholder="Suite de régression"
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="suite-description">Description</Label>
          <Textarea
            id="suite-description"
            value={suiteForm.description}
            onChange={(event) =>
              setSuiteForm((prev) => ({ ...prev, description: event.target.value }))
            }
            placeholder="Description (facultatif)"
          />
        </div>
        <div className="space-y-2">
          <Label>Type</Label>
          <Select
            value={suiteForm.type}
            onValueChange={(value) => {
              setSuiteForm((prev) => ({
                ...prev,
                type: value as SuiteType,
                ...(value === 'UNIT' || value === 'INTEGRATION'
                  ? {
                      useGitRepo: true,
                      gitRepoUrl: prev.gitRepoUrl,
                      gitBranch: prev.gitBranch.trim() ? prev.gitBranch : 'main',
                    }
                  : {
                      // Switching away from mandatory types clears git fields by default.
                      useGitRepo: isWebOrApiSuiteType(prev.type) ? prev.useGitRepo : false,
                      gitRepoUrl: isWebOrApiSuiteType(prev.type) && prev.useGitRepo ? prev.gitRepoUrl : '',
                      gitBranch: isWebOrApiSuiteType(prev.type) && prev.useGitRepo ? prev.gitBranch : '',
                    }),
                modulePath: value === 'UNIT' || value === 'INTEGRATION' ? prev.modulePath : '',
              }))
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="Sélectionner un type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="UNIT">Test unitaire</SelectItem>
              <SelectItem value="INTEGRATION">Test d'intégration</SelectItem>
              <SelectItem value="WEB">E2E</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {suiteForm.type === 'UNIT' || suiteForm.type === 'INTEGRATION' ? (
          <div className="space-y-2">
            <Label htmlFor="suite-module-path">Chemin du module (relatif)</Label>
            <Input
              id="suite-module-path"
              value={suiteForm.modulePath}
              onChange={(event) =>
                setSuiteForm((prev) => ({ ...prev, modulePath: event.target.value }))
              }
              placeholder="backend/AuthenticationMicroservice"
              required={suiteForm.type === 'UNIT' || suiteForm.type === 'INTEGRATION'}
            />
            <p className="text-xs text-muted-foreground">
              Chemin relatif depuis la racine du dépôt cloné.
            </p>
          </div>
        ) : null}

        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <FormDialog
        open={suiteEditOpen}
        onOpenChange={setSuiteEditOpen}
        title="Modifier la suite de test"
        description="Mettez à jour les métadonnées de la suite."
        submitLabel="Enregistrer"
        isSubmitting={isSuiteSubmitting}
        onSubmit={submitSuiteEdit}
        disableSubmit={false}
      >
        <div className="space-y-2">
          <Label htmlFor="suite-edit-name">Nom</Label>
          <Input
            id="suite-edit-name"
            value={suiteForm.name}
            onChange={(event) => setSuiteForm((prev) => ({ ...prev, name: event.target.value }))}
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="suite-edit-description">Description</Label>
          <Textarea
            id="suite-edit-description"
            value={suiteForm.description}
            onChange={(event) =>
              setSuiteForm((prev) => ({ ...prev, description: event.target.value }))
            }
          />
        </div>
        <div className="space-y-2">
          <Label>Type</Label>
          <Select
            value={suiteForm.type}
            onValueChange={(value) => {
              setSuiteForm((prev) => ({
                ...prev,
                type: value as SuiteType,
                ...(value === 'UNIT' || value === 'INTEGRATION'
                  ? {
                      useGitRepo: true,
                      gitRepoUrl: prev.gitRepoUrl,
                      gitBranch: prev.gitBranch.trim() ? prev.gitBranch : 'main',
                    }
                  : {
                      useGitRepo: (prev.type === 'WEB' || prev.type === 'API') ? prev.useGitRepo : false,
                      gitRepoUrl: (prev.type === 'WEB' || prev.type === 'API') && prev.useGitRepo ? prev.gitRepoUrl : '',
                      gitBranch: (prev.type === 'WEB' || prev.type === 'API') && prev.useGitRepo ? prev.gitBranch : '',
                    }),
                modulePath: value === 'UNIT' || value === 'INTEGRATION' ? prev.modulePath : '',
              }))
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="Sélectionner un type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="UNIT">Test unitaire</SelectItem>
              <SelectItem value="INTEGRATION">Test d'intégration</SelectItem>
              <SelectItem value="WEB">E2E</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {suiteForm.type === 'UNIT' || suiteForm.type === 'INTEGRATION' ? (
          <div className="space-y-2">
            <Label htmlFor="suite-edit-module-path">Chemin du module (relatif)</Label>
            <Input
              id="suite-edit-module-path"
              value={suiteForm.modulePath}
              onChange={(event) =>
                setSuiteForm((prev) => ({ ...prev, modulePath: event.target.value }))
              }
              placeholder="backend/AuthenticationMicroservice"
              required={suiteForm.type === 'UNIT' || suiteForm.type === 'INTEGRATION'}
            />
            <p className="text-xs text-muted-foreground">
              Chemin relatif depuis la racine du dépôt cloné.
            </p>
          </div>
        ) : null}

        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <ConfirmDialog
        open={suiteDeleteOpen}
        onOpenChange={setSuiteDeleteOpen}
        title="Supprimer la suite de test"
        description={suiteDeleting ? `Supprimer « ${suiteDeleting.name} » ?` : 'Supprimer la suite de test ?'}
        confirmLabel="Supprimer"
        isConfirming={isSuiteDeleting}
        onConfirm={confirmSuiteDelete}
      />

      <FormDialog
        open={memberAddOpen}
        onOpenChange={setMemberAddOpen}
        title="Ajouter un membre"
        description="Invitez un coéquipier sur le projet."
        submitLabel="Ajouter le membre"
        isSubmitting={isMemberSubmitting}
        onSubmit={submitMemberAdd}
      >
        <div className="space-y-2">
          <Label>Utilisateur</Label>
          <Select
            value={memberForm.userId}
            onValueChange={(value) => setMemberForm((prev) => ({ ...prev, userId: value }))}
          >
            <SelectTrigger>
              <SelectValue placeholder="Sélectionner un utilisateur" />
            </SelectTrigger>
            <SelectContent>
              {availableUsers.map((user) => (
                <SelectItem key={user.id} value={String(user.id)}>
                  {formatUserLabel(user)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {usersState === 'loading' ? (
            <p className="text-xs text-muted-foreground">Chargement des utilisateurs…</p>
          ) : null}
          {usersState === 'error' ? (
            <p className="text-xs text-destructive">{usersError}</p>
          ) : null}
          {usersState === 'ready' && availableUsers.length === 0 ? (
            <p className="text-xs text-muted-foreground">Tous les utilisateurs sont déjà membres.</p>
          ) : null}
        </div>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <ConfirmDialog
        open={memberDeleteOpen}
        onOpenChange={setMemberDeleteOpen}
        title="Retirer le membre"
        description={
          memberDeleting
            ? `Retirer ${(() => {
                const u = users.find(u => u.id === memberDeleting.userId)
                return u ? formatUserLabel(u) : `Utilisateur #${memberDeleting.userId}`
              })()} du projet ?`
            : 'Retirer le membre ?'
        }
        confirmLabel="Retirer"
        isConfirming={isMemberDeleting}
        onConfirm={confirmMemberDelete}
      />
    </div>
  )
}
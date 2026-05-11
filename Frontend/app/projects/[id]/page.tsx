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
  MemberRole,
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
  variables: string
}

type SuiteFormState = {
  name: string
  description: string
  gitRepoUrl: string
  gitBranch: string
  modulePath: string
  type?: SuiteType
}

// Add suite type to form state
type SuiteType = 'WEB' | 'API' | 'UNIT' | 'INTEGRATION'


type MemberFormState = {
  userId: string
  role: MemberRole
}

const emptyEnvForm: EnvironmentFormState = {
  name: '',
  baseUrlWeb: '',
  baseUrlApi: '',
  variables: '',
}

const emptySuiteForm: SuiteFormState = {
  name: '',
  description: '',
  gitRepoUrl: '',
  gitBranch: '',
  modulePath: '',
  type: 'WEB',
}


const emptyMemberForm: MemberFormState = {
  userId: '',
  role: 'TESTER',
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
    await Promise.all([loadProject(), loadEnvironments(), loadSuites(), loadMembers()])
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
      variables: env.variables ?? '',
    })
    setEnvEditOpen(true)
  }

  const openEnvDelete = (env: Environment) => {
    setEnvDeleting(env)
    setEnvDeleteOpen(true)
  }

  const openSuiteCreate = () => {
    resetSuiteForm()
    setSuiteForm((prev) => ({ ...prev, type: 'WEB' }))
    setSuiteCreateOpen(true)
  }

  const openSuiteEdit = (suite: TestSuite) => {
    setSuiteEditing(suite)
    const type = (suite.type ?? 'WEB') as SuiteType
    const gitBranchRaw = suite.gitBranch ?? ''
    const gitBranch = type === 'UNIT' && !gitBranchRaw.trim() ? 'main' : gitBranchRaw
    setSuiteForm({
      name: suite.name ?? '',
      description: suite.description ?? '',
      gitRepoUrl: suite.gitRepoUrl ?? '',
      gitBranch,
      modulePath: suite.modulePath ?? '',
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
      const { value: variables, error } = normalizeVariables(envForm.variables)
      if (error) {
        setFormError(error)
        setIsEnvSubmitting(false)
        return
      }

      const payload: CreateEnvironmentRequest = {
        name: envForm.name.trim(),
        baseUrlWeb: envForm.baseUrlWeb.trim() || undefined,
        baseUrlApi: envForm.baseUrlApi.trim() || undefined,
        variables,
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
    if ((suiteCreateOpen || suiteEditOpen) && suiteForm.type === 'UNIT') {
      void fetchGitHubRepos()
    }
  }, [suiteCreateOpen, suiteEditOpen, suiteForm.type])

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
    if (suiteForm.type !== 'UNIT') return
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
  }, [suiteForm.type, suiteForm.gitRepoUrl, suiteCreateOpen, suiteEditOpen, gitReposState, gitRepos])

  useEffect(() => {
    if (suiteForm.type !== 'UNIT') return
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
  }, [suiteForm.type, suiteForm.gitRepoUrl, gitReposState, gitRepos])

  const submitEnvEdit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!hasProjectId || !envEditing) return
    setIsEnvSubmitting(true)
    setFormError(null)

    try {
      const { value: variables, error } = normalizeVariables(envForm.variables)
      if (error) {
        setFormError(error)
        setIsEnvSubmitting(false)
        return
      }

      const payload: UpdateEnvironmentRequest = {
        name: envForm.name.trim(),
        baseUrlWeb: envForm.baseUrlWeb.trim() || undefined,
        baseUrlApi: envForm.baseUrlApi.trim() || undefined,
        variables,
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
      const payload: CreateTestSuiteRequest = {
        name: suiteForm.name.trim(),
        type: (suiteForm.type as any) || undefined,
        description: suiteForm.description.trim() || undefined,
        gitRepoUrl: suiteForm.gitRepoUrl.trim() || undefined,
        gitBranch: suiteForm.gitBranch.trim() || undefined,
        modulePath: suiteForm.modulePath.trim() || undefined,
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
      const payload: UpdateTestSuiteRequest = {
        name: suiteForm.name.trim(),
        type: (suiteForm.type as any) || undefined,
        description: suiteForm.description.trim() || undefined,
        gitRepoUrl: suiteForm.gitRepoUrl.trim() || undefined,
        gitBranch: suiteForm.gitBranch.trim() || undefined,
        modulePath: suiteForm.modulePath.trim() || undefined,
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
        role: memberForm.role,
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
              <Card className="p-6 text-sm text-muted-foreground">Loading project...</Card>
            ) : null}
            {projectState === 'error' ? (
              <Card className="p-6 text-sm text-destructive">{projectError}</Card>
            ) : null}

            <Card className="p-6">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Database size={18} />
                  <h2 className="text-lg font-semibold">Environments</h2>
                </div>
                <Button size="sm" onClick={openEnvCreate} className="gap-2">
                  <Plus size={14} />
                  Add environment
                </Button>
              </div>

              {envError ? <p className="text-sm text-destructive mt-4">{envError}</p> : null}
              {envState === 'loading' ? (
                <p className="text-sm text-muted-foreground mt-4">Loading environments...</p>
              ) : null}

              {envState !== 'loading' && environments.length === 0 ? (
                <p className="text-sm text-muted-foreground mt-4">No environments yet.</p>
              ) : null}

              {envState !== 'loading' && environments.length > 0 ? (
                <div className="mt-4">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Name</TableHead>
                        <TableHead>Base URL (Web)</TableHead>
                        <TableHead>Base URL (API)</TableHead>
                        <TableHead>Created</TableHead>
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
                                Edit
                              </Button>
                              <Button size="sm" variant="destructive" onClick={() => openEnvDelete(env)}>
                                <Trash2 size={14} />
                                Delete
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
                  <h2 className="text-lg font-semibold">Test suites</h2>
                </div>
                <Button size="sm" onClick={openSuiteCreate} className="gap-2">
                  <Plus size={14} />
                  Add suite
                </Button>
              </div>

              {suiteError ? <p className="text-sm text-destructive mt-4">{suiteError}</p> : null}
              {suiteState === 'loading' ? (
                <p className="text-sm text-muted-foreground mt-4">Loading suites...</p>
              ) : null}

              {suiteState !== 'loading' && suites.length === 0 ? (
                <p className="text-sm text-muted-foreground mt-4">No suites yet.</p>
              ) : null}

              {suiteState !== 'loading' && suites.length > 0 ? (
                <div className="mt-4">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Name</TableHead>
                        <TableHead>Description</TableHead>
                        <TableHead>Created</TableHead>
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
                                  View cases
                                </Link>
                              </Button>
                              <Button size="sm" variant="outline" onClick={() => openSuiteEdit(suite)}>
                                <Settings size={14} />
                                Edit
                              </Button>
                              <Button size="sm" variant="destructive" onClick={() => openSuiteDelete(suite)}>
                                <Trash2 size={14} />
                                Delete
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
                  <h2 className="text-lg font-semibold">Project members</h2>
                </div>
                <Button size="sm" onClick={openMemberAdd} className="gap-2">
                  <Plus size={14} />
                  Add member
                </Button>
              </div>

              {memberError ? <p className="text-sm text-destructive mt-4">{memberError}</p> : null}
              {memberState === 'loading' ? (
                <p className="text-sm text-muted-foreground mt-4">Loading members...</p>
              ) : null}

              {memberState !== 'loading' && members.length === 0 ? (
                <p className="text-sm text-muted-foreground mt-4">No members yet.</p>
              ) : null}

              {memberState !== 'loading' && members.length > 0 ? (
                <div className="mt-4">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>User ID</TableHead>
                        <TableHead>Role</TableHead>
                        <TableHead className="text-right">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {members.map((member) => (
                        <TableRow key={member.userId}>
                          <TableCell className="font-medium">{member.userId}</TableCell>
                          <TableCell>{member.role}</TableCell>
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
        title="Add environment"
        description="Define URLs and variables for this environment."
        submitLabel="Create environment"
        isSubmitting={isEnvSubmitting}
        onSubmit={submitEnvCreate}
      >
        <div className="space-y-2">
          <Label htmlFor="env-name">Name</Label>
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
          <Label htmlFor="env-variables">Variables (JSON)</Label>
          <Textarea
            id="env-variables"
            value={envForm.variables}
            onChange={(event) =>
              setEnvForm((prev) => ({ ...prev, variables: event.target.value }))
            }
            placeholder='{"BASE_URL": "https://staging.example.com"}'
          />
        </div>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <FormDialog
        open={envEditOpen}
        onOpenChange={setEnvEditOpen}
        title="Edit environment"
        description="Update environment configuration."
        submitLabel="Save changes"
        isSubmitting={isEnvSubmitting}
        onSubmit={submitEnvEdit}
      >
        <div className="space-y-2">
          <Label htmlFor="env-edit-name">Name</Label>
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
          <Label htmlFor="env-edit-variables">Variables (JSON)</Label>
          <Textarea
            id="env-edit-variables"
            value={envForm.variables}
            onChange={(event) =>
              setEnvForm((prev) => ({ ...prev, variables: event.target.value }))
            }
          />
        </div>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <ConfirmDialog
        open={envDeleteOpen}
        onOpenChange={setEnvDeleteOpen}
        title="Delete environment"
        description={envDeleting ? `Delete ${envDeleting.name}?` : 'Delete environment?'}
        confirmLabel="Delete"
        isConfirming={isEnvDeleting}
        onConfirm={confirmEnvDelete}
      />

      <FormDialog
        open={suiteCreateOpen}
        onOpenChange={setSuiteCreateOpen}
        title="Add test suite"
        description="Group test cases for this project."
        submitLabel="Create suite"
        isSubmitting={isSuiteSubmitting}
        onSubmit={submitSuiteCreate}
        disableSubmit={
          suiteForm.type === 'UNIT' &&
          (!suiteForm.gitRepoUrl.trim() || !suiteForm.gitBranch.trim() || !suiteForm.modulePath.trim())
        }
      >
        <div className="space-y-2">
          <Label htmlFor="suite-name">Name</Label>
          <Input
            id="suite-name"
            value={suiteForm.name}
            onChange={(event) => setSuiteForm((prev) => ({ ...prev, name: event.target.value }))}
            placeholder="Regression Suite"
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
            placeholder="Optional description"
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
                // clear git fields when switching away
                gitRepoUrl: value === 'UNIT' ? prev.gitRepoUrl : '',
                gitBranch: value === 'UNIT' ? (prev.gitBranch.trim() ? prev.gitBranch : 'main') : '',
                modulePath: value === 'UNIT' ? prev.modulePath : '',
              }))
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="UNIT">UNIT</SelectItem>
              <SelectItem value="INTEGRATION">INTEGRATION</SelectItem>
              <SelectItem value="WEB">WEB</SelectItem>
              <SelectItem value="API">API</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {suiteForm.type === 'UNIT' ? (
          <>
            <div className="space-y-2">
              <Label htmlFor="suite-module-path">Chemin du module (relatif)</Label>
              <Input
                id="suite-module-path"
                value={suiteForm.modulePath}
                onChange={(event) =>
                  setSuiteForm((prev) => ({ ...prev, modulePath: event.target.value }))
                }
                placeholder="backend/AuthenticationMicroservice"
                required
              />
              <p className="text-xs text-muted-foreground">
                Chemin relatif depuis la racine du dépôt cloné.
              </p>
            </div>
            <div className="space-y-2">
              <Label>Git Repository</Label>
              <Select
                value={suiteForm.gitRepoUrl}
                onValueChange={(value) =>
                  setSuiteForm((prev) => ({
                    ...prev,
                    gitRepoUrl: value,
                    gitBranch: '',
                  }))
                }
              >
                <SelectTrigger>
                  <SelectValue
                    placeholder={
                      gitReposState === 'loading'
                        ? 'Loading...'
                        : gitReposState === 'ready' && gitRepos.length > 0
                          ? 'Select repository'
                          : 'No repos'
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {gitReposState === 'loading' ? (
                    <SelectItem value="__loading__" disabled>Loading...</SelectItem>
                  ) : null}
                  {gitReposState === 'error' ? (
                    <SelectItem value="__error__" disabled>Failed to load</SelectItem>
                  ) : null}
                  {gitReposState === 'ready' && gitRepos.length === 0 ? (
                    <SelectItem value="__none__" disabled>No connected GitHub repositories</SelectItem>
                  ) : null}
                  {gitRepos.map((r) => (
                    <SelectItem key={r.key} value={r.url}>
                      {r.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {gitReposMessage ? (
                <p className="text-xs text-muted-foreground">{gitReposMessage}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="suite-git-branch">Git Branch</Label>
              <Select
                value={suiteForm.gitBranch}
                onValueChange={(value) => {
                  setSuiteForm((prev) => ({ ...prev, gitBranch: value }))
                }}
                disabled={!suiteForm.gitRepoUrl.trim()}
              >
                <SelectTrigger id="suite-git-branch">
                  <SelectValue
                    placeholder={
                      !suiteForm.gitRepoUrl.trim()
                        ? 'Select repository first'
                        : branchState.kind === 'loading'
                          ? 'Loading branches...'
                          : branchState.kind === 'available' && branchState.branches.length > 0
                            ? 'Select branch'
                            : 'No branches'
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {branchState.kind === 'loading' ? (
                    <SelectItem value="__loading__" disabled>
                      Loading...
                    </SelectItem>
                  ) : null}
                  {branchState.kind === 'error' ? (
                    <SelectItem value="__error__" disabled>
                      Failed to load branches
                    </SelectItem>
                  ) : null}
                  {branchState.kind === 'available' && branchState.branches.length === 0 ? (
                    <SelectItem value="__none__" disabled>
                      No branches
                    </SelectItem>
                  ) : null}
                  {branchState.kind === 'available' &&
                  suiteForm.gitBranch.trim() &&
                  !branchState.branches.includes(suiteForm.gitBranch.trim()) ? (
                    <SelectItem value={suiteForm.gitBranch.trim()}>
                      {suiteForm.gitBranch.trim()} (current)
                    </SelectItem>
                  ) : null}
                  {branchState.kind === 'available'
                    ? branchState.branches.map((b) => (
                        <SelectItem key={b} value={b}>
                          {b}
                        </SelectItem>
                      ))
                    : null}
                </SelectContent>
              </Select>
              {branchState.kind === 'error' ? (
                <p className="text-xs text-muted-foreground">{branchState.message}</p>
              ) : null}
            </div>
          </>
        ) : null}
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <FormDialog
        open={suiteEditOpen}
        onOpenChange={setSuiteEditOpen}
        title="Edit test suite"
        description="Update suite metadata."
        submitLabel="Save changes"
        isSubmitting={isSuiteSubmitting}
        onSubmit={submitSuiteEdit}
        disableSubmit={
          suiteForm.type === 'UNIT' &&
          (!suiteForm.gitRepoUrl.trim() || !suiteForm.gitBranch.trim() || !suiteForm.modulePath.trim())
        }
      >
        <div className="space-y-2">
          <Label htmlFor="suite-edit-name">Name</Label>
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
                gitRepoUrl: value === 'UNIT' ? prev.gitRepoUrl : '',
                gitBranch: value === 'UNIT' ? (prev.gitBranch.trim() ? prev.gitBranch : 'main') : '',
                modulePath: value === 'UNIT' ? prev.modulePath : '',
              }))
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="UNIT">UNIT</SelectItem>
              <SelectItem value="INTEGRATION">INTEGRATION</SelectItem>
              <SelectItem value="WEB">WEB</SelectItem>
              <SelectItem value="API">API</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {suiteForm.type === 'UNIT' ? (
          <>
            <div className="space-y-2">
              <Label htmlFor="suite-edit-module-path">Chemin du module (relatif)</Label>
              <Input
                id="suite-edit-module-path"
                value={suiteForm.modulePath}
                onChange={(event) =>
                  setSuiteForm((prev) => ({ ...prev, modulePath: event.target.value }))
                }
                placeholder="backend/AuthenticationMicroservice"
                required
              />
              <p className="text-xs text-muted-foreground">
                Chemin relatif depuis la racine du dépôt cloné.
              </p>
            </div>
            <div className="space-y-2">
              <Label>Git Repository</Label>
              <Select
                value={suiteForm.gitRepoUrl}
                onValueChange={(value) =>
                  setSuiteForm((prev) => ({
                    ...prev,
                    gitRepoUrl: value,
                    gitBranch: '',
                  }))
                }
              >
                <SelectTrigger>
                  <SelectValue
                    placeholder={
                      gitReposState === 'loading'
                        ? 'Loading...'
                        : gitReposState === 'ready' && gitRepos.length > 0
                          ? 'Select repository'
                          : 'No repos'
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {gitReposState === 'loading' ? (
                    <SelectItem value="__loading__" disabled>Loading...</SelectItem>
                  ) : null}
                  {gitReposState === 'error' ? (
                    <SelectItem value="__error__" disabled>Failed to load</SelectItem>
                  ) : null}
                  {gitReposState === 'ready' && gitRepos.length === 0 ? (
                    <SelectItem value="__none__" disabled>No connected GitHub repositories</SelectItem>
                  ) : null}
                  {gitRepos.map((r) => (
                    <SelectItem key={r.key} value={r.url}>
                      {r.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {gitReposMessage ? (
                <p className="text-xs text-muted-foreground">{gitReposMessage}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="suite-edit-git-branch">Git Branch</Label>
              <Select
                value={suiteForm.gitBranch}
                onValueChange={(value) => {
                  setSuiteForm((prev) => ({ ...prev, gitBranch: value }))
                }}
                disabled={!suiteForm.gitRepoUrl.trim()}
              >
                <SelectTrigger id="suite-edit-git-branch">
                  <SelectValue
                    placeholder={
                      !suiteForm.gitRepoUrl.trim()
                        ? 'Select repository first'
                        : branchState.kind === 'loading'
                          ? 'Loading branches...'
                          : branchState.kind === 'available' && branchState.branches.length > 0
                            ? 'Select branch'
                            : 'No branches'
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {branchState.kind === 'loading' ? (
                    <SelectItem value="__loading__" disabled>
                      Loading...
                    </SelectItem>
                  ) : null}
                  {branchState.kind === 'error' ? (
                    <SelectItem value="__error__" disabled>
                      Failed to load branches
                    </SelectItem>
                  ) : null}
                  {branchState.kind === 'available' && branchState.branches.length === 0 ? (
                    <SelectItem value="__none__" disabled>
                      No branches
                    </SelectItem>
                  ) : null}
                  {branchState.kind === 'available' &&
                  suiteForm.gitBranch.trim() &&
                  !branchState.branches.includes(suiteForm.gitBranch.trim()) ? (
                    <SelectItem value={suiteForm.gitBranch.trim()}>
                      {suiteForm.gitBranch.trim()} (current)
                    </SelectItem>
                  ) : null}
                  {branchState.kind === 'available'
                    ? branchState.branches.map((b) => (
                        <SelectItem key={b} value={b}>
                          {b}
                        </SelectItem>
                      ))
                    : null}
                </SelectContent>
              </Select>
              {branchState.kind === 'error' ? (
                <p className="text-xs text-muted-foreground">{branchState.message}</p>
              ) : null}
            </div>
          </>
        ) : null}
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <ConfirmDialog
        open={suiteDeleteOpen}
        onOpenChange={setSuiteDeleteOpen}
        title="Delete test suite"
        description={suiteDeleting ? `Delete ${suiteDeleting.name}?` : 'Delete test suite?'}
        confirmLabel="Delete"
        isConfirming={isSuiteDeleting}
        onConfirm={confirmSuiteDelete}
      />

      <FormDialog
        open={memberAddOpen}
        onOpenChange={setMemberAddOpen}
        title="Add member"
        description="Invite a teammate to the project."
        submitLabel="Add member"
        isSubmitting={isMemberSubmitting}
        onSubmit={submitMemberAdd}
      >
        <div className="space-y-2">
          <Label>User</Label>
          <Select
            value={memberForm.userId}
            onValueChange={(value) => setMemberForm((prev) => ({ ...prev, userId: value }))}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select user" />
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
            <p className="text-xs text-muted-foreground">Loading users...</p>
          ) : null}
          {usersState === 'error' ? (
            <p className="text-xs text-destructive">{usersError}</p>
          ) : null}
          {usersState === 'ready' && availableUsers.length === 0 ? (
            <p className="text-xs text-muted-foreground">All users are already members.</p>
          ) : null}
        </div>
        <div className="space-y-2">
          <Label>Role</Label>
          <Select
            value={memberForm.role}
            onValueChange={(value) => setMemberForm((prev) => ({ ...prev, role: value as MemberRole }))}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select role" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ADMIN">ADMIN</SelectItem>
              <SelectItem value="TESTER">TESTER</SelectItem>
              <SelectItem value="DEVOPS">DEVOPS</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <ConfirmDialog
        open={memberDeleteOpen}
        onOpenChange={setMemberDeleteOpen}
        title="Remove member"
        description={
          memberDeleting
            ? `Remove user ${memberDeleting.userId} from the project?`
            : 'Remove member?'
        }
        confirmLabel="Remove"
        isConfirming={isMemberDeleting}
        onConfirm={confirmMemberDelete}
      />
    </div>
  )
}
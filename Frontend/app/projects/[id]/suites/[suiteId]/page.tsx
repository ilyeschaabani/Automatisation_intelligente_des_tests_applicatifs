'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'
import { ChevronLeft, Plus, RefreshCw, Trash2, Wand2 } from 'lucide-react'

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
import { SourceClassWizard, type WizardResult } from '@/components/source-class-wizard'
import { ScenarioBuilder, type ScenarioResult } from '@/components/scenario-builder'
import { environmentService } from '@/services/environments'
import { testCaseService } from '@/services/testCases'
import { llmService } from '@/services/llm'
import { testSuiteService } from '@/services/suites'
import { projectService } from '@/services/projects'
import type {
  CreateTestCaseRequest,
  Environment,
  RiskLevel,
  TestCase,
  TestSuite,
  TestType,
  UpdateTestCaseRequest,
  Project,
} from '@/types/ms-gestion'

type LoadState = 'loading' | 'ready' | 'error'

type CaseMode = 'AI' | 'MANUAL' | 'REPO'

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

type CaseFormState = {
  title: string
  description: string
  mode: CaseMode
  type: TestType
  gitRepoUrl: string
  springProfile: string
  databaseType: string
  priority: string
  riskLevel: RiskLevel | ''
  scriptPath: string
  testData: string
  tags: string
  maxDurationSeconds: string
  targetClassName: string
}

const emptyCaseForm: CaseFormState = {
  title: '',
  description: '',
  mode: 'MANUAL',
  type: 'WEB',
  gitRepoUrl: '',
  springProfile: '',
  databaseType: '',
  priority: '',
  riskLevel: '',
  scriptPath: '',
  testData: '',
  tags: '',
  maxDurationSeconds: '',
  targetClassName: '',
}

// Additional UI state for AI generation
type AICaseState = {
  descriptionAI: string
  generatedCode: string
  codeValidated: boolean
}

const emptyAICase: AICaseState = {
  descriptionAI: '',
  generatedCode: '',
  codeValidated: false,
}

const formatDate = (value?: string) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

const normalizeRepoUrl = (url: string): string =>
  String(url || '')
    .trim()
    .toLowerCase()
    .replace(/\.git$/, '')
    .replace(/\/+$/, '')

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
          : typeof (r as any).full_name === 'string'
            ? String((r as any).full_name).split('/').slice(-1)[0]
            : ''

      const owner: string =
        typeof r.owner === 'string'
          ? r.owner
          : r.owner && typeof r.owner === 'object' && typeof (r.owner as any).login === 'string'
            ? String((r.owner as any).login)
            : typeof (r as any).full_name === 'string'
              ? String((r as any).full_name).split('/')[0] ?? ''
              : ''

      const url: string =
        typeof (r as any).html_url === 'string'
          ? String((r as any).html_url)
          : typeof r.url === 'string'
            ? r.url
            : ''

      const updatedAt: string | undefined =
        typeof (r as any).updated_at === 'string'
          ? String((r as any).updated_at)
          : typeof (r as any).updatedAt === 'string'
            ? String((r as any).updatedAt)
            : undefined

      const branches: string[] = Array.isArray((r as any).branches)
        ? ((r as any).branches as unknown[])
            .map((b) => {
              if (typeof b === 'string') return b
              if (b && typeof b === 'object' && typeof (b as any).name === 'string') {
                return (b as any).name
              }
              return null
            })
            .filter(Boolean) as string[]
        : []

      const key =
        typeof r.id === 'number' || typeof r.id === 'string'
          ? String(r.id)
          : `${owner}/${name || 'repo'}:${idx}`

      if (!name) return null
      return {
        key,
        name,
        owner,
        isPrivate: Boolean((r as any).private),
        url,
        updatedAt,
        branches,
      }
    })
    .filter(Boolean) as RepoRow[]
}

export default function SuiteTestCasesPage() {
  const params = useParams<{ id?: string | string[]; suiteId?: string | string[] }>()
  const projectId = Number(Array.isArray(params?.id) ? params?.id[0] : params?.id)
  const suiteId = Number(
    Array.isArray(params?.suiteId) ? params?.suiteId[0] : params?.suiteId,
  )
  const hasIds = Number.isFinite(projectId) && Number.isFinite(suiteId)

  const [suite, setSuite] = useState<TestSuite | null>(null)
  const [project, setProject] = useState<Project | null>(null)
  const [suiteState, setSuiteState] = useState<LoadState>('loading')
  const [suiteError, setSuiteError] = useState<string | null>(null)

  // Source class wizard state
  const [showWizard, setShowWizard] = useState(false)
  const [wizardSkeleton, setWizardSkeleton] = useState<string>('')
  const [projectEnvs, setProjectEnvs] = useState<Environment[]>([])

  // Scenario builder state (Step 2-4 after wizard)
  const [showScenarioBuilder, setShowScenarioBuilder] = useState(false)

  // Parse owner/repo from a GitHub URL like https://github.com/owner/repo
  const parseGitHubUrl = (url: string): { owner: string; repo: string } | null => {
    const m = url?.match(/github\.com\/([^/]+)\/([^/.]+)/)
    if (!m) return null
    return { owner: m[1], repo: m[2] }
  }

  // Find env with gitRepoUrl configured
  const sourceEnv = projectEnvs.find((e) => (e as any).gitRepoUrl)
  const sourceGitInfo = sourceEnv ? parseGitHubUrl((sourceEnv as any).gitRepoUrl ?? '') : null
  const sourceBranch = (sourceEnv as any)?.gitBranch ?? 'main'
  const isUnitOrIntegration = suite?.type === 'UNIT' || suite?.type === 'INTEGRATION'

  const [cases, setCases] = useState<TestCase[]>([])
  const [caseState, setCaseState] = useState<LoadState>('loading')
  const [caseError, setCaseError] = useState<string | null>(null)

  const [formState, setFormState] = useState<CaseFormState>(emptyCaseForm)
  const [formError, setFormError] = useState<string | null>(null)
  const [aiState, setAiState] = useState<AICaseState>(emptyAICase)
  const [generating, setGenerating] = useState(false)

  const [gitHubConnection, setGitHubConnection] = useState<GitHubConnectionState>({
    kind: 'idle',
  })
  const [gitHubRepos, setGitHubRepos] = useState<RepoListState>({ kind: 'idle' })
  const [selectedRepoKey, setSelectedRepoKey] = useState('')
  const [repoInitialized, setRepoInitialized] = useState(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editingCase, setEditingCase] = useState<TestCase | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletingCase, setDeletingCase] = useState<TestCase | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)

  const isUxSuiteType = (suite?.type as string) === 'FUNCTIONAL_WEB' || (suite?.type as string) === 'FUNCTIONAL_MOBILE'
  const forceAiForSuite = suite?.type === 'UNIT' || suite?.type === 'INTEGRATION' || isUxSuiteType
  const effectiveMode: CaseMode = forceAiForSuite ? 'AI' : formState.mode
  const showAiSection = effectiveMode === 'AI'

  const connectUrl = useMemo(() => {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) return ''
    return `${apiUrl.replace(/\/+$/, '')}/api/github/connect`
  }, [])

  const loadSuite = async () => {
    if (!hasIds) return
    setSuiteState('loading')
    setSuiteError(null)
    try {
      const data = await testSuiteService.getById(projectId, suiteId)
      setSuite(data)
      setSuiteState('ready')
    } catch (err) {
      setSuite(null)
      setSuiteState('error')
      setSuiteError(err instanceof Error ? err.message : 'Failed to load suite')
    }
  }

  const loadProject = async () => {
    if (!hasIds) return
    try {
      const p = await projectService.getById(projectId)
      setProject(p)
    } catch (e) {
      setProject(null)
    }
  }

  const loadCases = async () => {
    if (!hasIds) return
    setCaseState('loading')
    setCaseError(null)
    try {
      const data = await testCaseService.getAll(suiteId)
      setCases(Array.isArray(data) ? data : [])
      setCaseState('ready')
    } catch (err) {
      setCases([])
      setCaseState('error')
      setCaseError(err instanceof Error ? err.message : 'Failed to load test cases')
    }
  }

  const loadAll = async () => {
    await Promise.all([loadSuite(), loadCases(), loadProject()])
  }

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

  useEffect(() => {
    if (!hasIds) return
    void loadAll()
  }, [hasIds])

  // Load environments to find source gitRepoUrl for wizard
  useEffect(() => {
    if (!hasIds) return
    environmentService.getAll(projectId).then((envs) => {
      setProjectEnvs(Array.isArray(envs) ? envs : [])
    }).catch(() => setProjectEnvs([]))
  }, [hasIds, projectId])

  useEffect(() => {
    if (!createOpen && !editOpen) return
    if (showAiSection) return
    void fetchGitHubMe()
  }, [createOpen, editOpen, showAiSection])

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
      }
    }

    setRepoInitialized(true)
  }, [repoInitialized, createOpen, editOpen, gitHubRepos, formState.gitRepoUrl])

  useEffect(() => {
    if (gitHubRepos.kind !== 'available') return
    if (!selectedRepoKey) return

    const repo = gitHubRepos.repos.find((item) => item.key === selectedRepoKey)
    if (!repo) return

    setFormState((prev) => {
      if (!repo.url || prev.gitRepoUrl === repo.url) return prev
      return { ...prev, gitRepoUrl: repo.url }
    })
  }, [gitHubRepos, selectedRepoKey])

  const resetForm = () => {
    const defaultMode: CaseMode = forceAiForSuite
      ? 'AI'
      : project?.aiProject
        ? 'AI'
        : 'MANUAL'
    setFormState({
      ...emptyCaseForm,
      mode: defaultMode,
      type: suite?.type ?? emptyCaseForm.type,
    })
    setFormError(null)
    setAiState(emptyAICase)
    setSelectedRepoKey('')
    setRepoInitialized(false)
    setShowWizard(false)
    setWizardSkeleton('')
    setShowScenarioBuilder(false)
  }

  const openCreate = async () => {
    resetForm()
    // Ensure we have project info before opening so UI can show AI section when needed
    try {
      if (!project) {
        const p = await projectService.getById(projectId)
        setProject(p)
      }
    } catch (e) {
      // ignore
    }
    setAiState(emptyAICase)
    setCreateOpen(true)
  }

  const generateScript = async (scenarioResult?: ScenarioResult) => {
    setFormError(null)
    const suiteType = suite?.type
    if (!suiteType) {
      setFormError('Suite type is not loaded yet. Please retry.')
      return
    }
    if (!showAiSection) {
      setFormError('AI generation is only available in AI mode.')
      return
    }
    if (forceAiForSuite && !aiState.descriptionAI.trim() && !scenarioResult) {
      setFormError('AI description is required for UNIT/INTEGRATION/UX suites.')
      return
    }
    setGenerating(true)
    try {
      const payload = {
        type: suiteType,
        description: scenarioResult?.expectedBehavior || aiState.descriptionAI || formState.description,
        databaseType: formState.databaseType || undefined,
        suiteId: suiteId || undefined,
        targetClassName: formState.targetClassName.trim() || undefined,
        testData: formState.testData.trim() || undefined,
        skeleton: wizardSkeleton.trim() || undefined,
        // M1 structured fields
        methodName: scenarioResult?.methodName || undefined,
        scenarioType: scenarioResult?.scenarioType || undefined,
        expectedBehavior: scenarioResult?.expectedBehavior || undefined,
      }
      const resp = await llmService.generateTest(payload)
      setAiState((prev) => ({ ...prev, generatedCode: resp.generatedCode, codeValidated: false }))
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to generate script')
    } finally {
      setGenerating(false)
    }
  }

  const validateScript = () => {
    setAiState((prev) => ({ ...prev, codeValidated: true }))
  }

  const openEdit = (testCase: TestCase) => {
    const deriveMode = (): CaseMode => {
      if (forceAiForSuite) return 'AI'
      if (testCase.useAI || testCase.generated || testCase.generatedCode) return 'AI'
      if (testCase.gitRepoUrl) return 'REPO'
      return 'MANUAL'
    }

    setEditingCase(testCase)
    setFormState({
      title: testCase.title ?? '',
      description: testCase.description ?? '',
      mode: deriveMode(),
      type: suite?.type ?? testCase.type,
      gitRepoUrl: testCase.gitRepoUrl ?? '',
      springProfile: testCase.springProfile ?? '',
      databaseType: (testCase as any).databaseType ?? '',
      priority: testCase.priority != null ? String(testCase.priority) : '',
      riskLevel: testCase.riskLevel ?? '',
      scriptPath: testCase.scriptPath ?? '',
      testData: testCase.testData ?? '',
      tags: testCase.tags ?? '',
      maxDurationSeconds:
        testCase.maxDurationSeconds != null ? String(testCase.maxDurationSeconds) : '',
      targetClassName: String((testCase as any).targetClassName ?? ''),
    })
    setEditOpen(true)
    // if this case was generated, prefill AI state
    setAiState({
      descriptionAI: String((testCase as any).descriptionAI ?? testCase.description ?? ''),
      generatedCode: String((testCase as any).generatedCode ?? ''),
      codeValidated: Boolean((testCase as any).generated),
    })
    setSelectedRepoKey('')
    setRepoInitialized(false)
  }

  const openDelete = (testCase: TestCase) => {
    setDeletingCase(testCase)
    setDeleteOpen(true)
  }

  const buildPayload = (): CreateTestCaseRequest | UpdateTestCaseRequest | null => {
    const suiteType = suite?.type
    if (!suiteType) {
      setFormError('Suite type is not loaded yet. Please retry.')
      return null
    }

    const isIntegration = suiteType === 'INTEGRATION'
    const showTestData = suiteType !== 'UNIT'
    const mode = effectiveMode

    const title = formState.title.trim()
    if (!title) {
      setFormError('Title is required.')
      return null
    }

    const priorityRaw = formState.priority.trim()
    const maxDurationRaw = formState.maxDurationSeconds.trim()
    const priority = priorityRaw ? Number(priorityRaw) : undefined
    const maxDurationSeconds = maxDurationRaw ? Number(maxDurationRaw) : undefined

    if (priorityRaw && !Number.isFinite(priority)) {
      setFormError('Priority must be a number.')
      return null
    }

    if (maxDurationRaw && !Number.isFinite(maxDurationSeconds)) {
      setFormError('Max duration must be a number.')
      return null
    }

    const payload: CreateTestCaseRequest = {
      title,
      description: formState.description.trim() || undefined,
      type: suiteType,
      gitRepoUrl: formState.gitRepoUrl.trim() || undefined,
      springProfile: isIntegration ? (formState.springProfile.trim() || undefined) : undefined,
      databaseType: isIntegration ? (formState.databaseType.trim() || undefined) : undefined,
      priority,
      riskLevel: formState.riskLevel ? (formState.riskLevel as RiskLevel) : undefined,
      scriptPath: formState.scriptPath.trim() || undefined,
      testData: showTestData ? (formState.testData.trim() || undefined) : undefined,
      tags: formState.tags.trim() || undefined,
      maxDurationSeconds,
      targetClassName: formState.targetClassName.trim() || undefined,
    }

    if (!isIntegration) {
      delete (payload as any).springProfile
    }
    if (!showTestData) {
      delete (payload as any).testData
    }

    if (isIntegration) {
      const dbt = formState.databaseType.trim()
      if (!dbt) {
        setFormError('databaseType is required for INTEGRATION suites')
        return null
      }
      // ensure uppercase normalized value
      payload.databaseType = dbt.toUpperCase()
    }

    // Three cases for code handling:
    // 1. If generatedCode exists (and validated) -> send it
    // 2. If project.aiProject with description -> backend regenerates
    // 3. Otherwise -> manual mode with scriptPath
    if (mode === 'AI') {
      // AI mode: either persist edited code or request generation from description.
      if (aiState.generatedCode && aiState.generatedCode.trim()) {
        if (!aiState.codeValidated) {
          setFormError('You must validate the generated script before saving.')
          return null
        }
        payload.generatedCode = aiState.generatedCode
        payload.useAI = false
        delete (payload as any).scriptPath
      } else {
        const prompt = aiState.descriptionAI.trim()
        if (!prompt) {
          setFormError('AI description is required for AI mode.')
          return null
        }
        payload.useAI = true
        payload.descriptionAI = prompt
        delete (payload as any).scriptPath
        delete (payload as any).generatedCode
      }
      delete (payload as any).gitRepoUrl
    } else {
      const repoUrl = formState.gitRepoUrl.trim()
      const scriptPath = formState.scriptPath.trim()

      if (!repoUrl) {
        setFormError('Repository URL is required for manual/import mode.')
        return null
      }

      if (!scriptPath) {
        setFormError('Script path is required for manual/import mode.')
        return null
      }

      payload.useAI = false
      payload.gitRepoUrl = repoUrl
      payload.scriptPath = scriptPath
      delete (payload as any).generatedCode
      delete (payload as any).descriptionAI
    }

    return payload
  }

  const submitCreate = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!hasIds) return
    setIsSubmitting(true)
    setFormError(null)

    const payload = buildPayload()
    if (!payload) {
      setIsSubmitting(false)
      return
    }

    try {
      await testCaseService.create(suiteId, payload)
      setCreateOpen(false)
      resetForm()
      await loadCases()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to create test case')
    } finally {
      setIsSubmitting(false)
    }
  }

  const submitEdit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!hasIds || !editingCase) return
    setIsSubmitting(true)
    setFormError(null)

    const payload = buildPayload()
    if (!payload) {
      setIsSubmitting(false)
      return
    }

    try {
      await testCaseService.update(suiteId, editingCase.id, payload)
      setEditOpen(false)
      setEditingCase(null)
      await loadCases()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to update test case')
    } finally {
      setIsSubmitting(false)
    }
  }

  const confirmDelete = async () => {
    if (!hasIds || !deletingCase) return
    setIsDeleting(true)
    try {
      await testCaseService.delete(suiteId, deletingCase.id)
      setDeleteOpen(false)
      setDeletingCase(null)
      await loadCases()
    } catch (err) {
      setCaseError(err instanceof Error ? err.message : 'Failed to delete test case')
    } finally {
      setIsDeleting(false)
    }
  }

  const suiteTitle = useMemo(() => suite?.name || 'Test suite', [suite])

  if (!hasIds) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">
        Invalid suite id.
      </div>
    )
  }

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />
        <AuthGuard>
          <div className="p-6 max-w-7xl space-y-6">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Link href="/projects" className="hover:underline">
                Projects
              </Link>
              <ChevronLeft size={12} />
              <Link href={`/projects/${projectId}`} className="hover:underline">
                Project {projectId}
              </Link>
            </div>

            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div>
                <h1 className="text-3xl font-bold text-foreground">{suiteTitle}</h1>
                <p className="text-muted-foreground mt-1">
                  Manage test cases for this suite.
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Button variant="outline" onClick={loadAll} className="gap-2">
                  <RefreshCw size={16} />
                  Refresh
                </Button>
                <Button onClick={openCreate} className="gap-2">
                  <Plus size={16} />
                  Add test case
                </Button>
              </div>
            </div>

            {suiteState === 'loading' ? (
              <Card className="p-6 text-sm text-muted-foreground">Loading suite...</Card>
            ) : null}
            {suiteState === 'error' ? (
              <Card className="p-6 text-sm text-destructive">{suiteError}</Card>
            ) : null}

            <Card className="p-6">
              <div className="flex items-center gap-2 mb-4">
                <Wand2 size={18} />
                <h2 className="text-lg font-semibold">Test cases</h2>
              </div>

              {caseError ? <p className="text-sm text-destructive mb-4">{caseError}</p> : null}
              {caseState === 'loading' ? (
                <p className="text-sm text-muted-foreground">Loading test cases...</p>
              ) : null}

              {caseState !== 'loading' && cases.length === 0 ? (
                <p className="text-sm text-muted-foreground">No test cases yet.</p>
              ) : null}

              {caseState !== 'loading' && cases.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Title</TableHead>
                      <TableHead>Type</TableHead>
                      <TableHead>Priority</TableHead>
                      <TableHead>Risk</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Created</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {cases.map((testCase) => (
                      <TableRow key={testCase.id}>
                        <TableCell className="font-medium">{testCase.title}</TableCell>
                        <TableCell>{testCase.type}</TableCell>
                        <TableCell>{testCase.priority ?? '—'}</TableCell>
                        <TableCell>{testCase.riskLevel ?? '—'}</TableCell>
                        <TableCell>
                          <div className="flex flex-wrap gap-2">
                            <Badge variant={testCase.active ? 'default' : 'secondary'}>
                              {testCase.active ? 'Active' : 'Inactive'}
                            </Badge>
                            {testCase.flaky ? <Badge variant="outline">Flaky</Badge> : null}
                          </div>
                        </TableCell>
                        <TableCell>{formatDate(testCase.createdAt)}</TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button size="sm" variant="outline" onClick={() => openEdit(testCase)}>
                              Edit
                            </Button>
                            <Button size="sm" variant="destructive" onClick={() => openDelete(testCase)}>
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
        title="Add test case"
        description="Define the test case details."
        submitLabel="Create test case"
        isSubmitting={isSubmitting}
        disableSubmit={
          (showAiSection && Boolean(aiState.generatedCode && aiState.generatedCode.trim()) && !aiState.codeValidated) ||
          (suite?.type === 'INTEGRATION' && !formState.databaseType)
        }
        size="xl"
        onSubmit={submitCreate}
      >
        <div className="space-y-2">
          <Label htmlFor="case-title">Title</Label>
          <Input
            id="case-title"
            value={formState.title}
            onChange={(event) => setFormState((prev) => ({ ...prev, title: event.target.value }))}
            placeholder="Login flow works"
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="case-description">Description</Label>
          <Textarea
            id="case-description"
            value={formState.description}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, description: event.target.value }))
            }
            placeholder="Optional description"
          />
        </div>
        <div className="space-y-2">
          <Label>Mode</Label>
          {forceAiForSuite ? (
            <Input value="Generate with AI" readOnly />
          ) : (
            <Select
              value={formState.mode}
              onValueChange={(value) => {
                const mode = value as CaseMode
                setFormState((prev) => ({ ...prev, mode }))
                if (mode !== 'AI') setAiState(emptyAICase)
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select mode" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="AI">Generate with AI</SelectItem>
                <SelectItem value="MANUAL">Write manually</SelectItem>
                <SelectItem value="REPO">Import from repo</SelectItem>
              </SelectContent>
            </Select>
          )}
        </div>
        {showAiSection ? (
          <>
            {isUnitOrIntegration && sourceGitInfo ? (
              /* ── Wizard mode for UNIT/INTEGRATION with configured env repo ── */
              showWizard ? (
                <SourceClassWizard
                  owner={sourceGitInfo.owner}
                  repo={sourceGitInfo.repo}
                  branch={sourceBranch}
                  onCancel={() => setShowWizard(false)}
                  onComplete={(result: WizardResult) => {
                    setShowWizard(false)
                    setFormState((prev) => ({
                      ...prev,
                      targetClassName: result.targetClassName,
                      testData: result.testData,
                    }))
                    setWizardSkeleton(result.skeleton)
                    setAiState((prev) => ({ ...prev, descriptionAI: result.descriptionAI }))
                    // Show ScenarioBuilder (Steps 2-4) instead of generating immediately
                    setShowScenarioBuilder(true)
                  }}
                />
              ) : showScenarioBuilder ? (
                /* ── Step 2-4: ScenarioBuilder ── */
                <div className="rounded-lg border border-primary/20 bg-primary/5 p-4 space-y-2">
                  <div className="flex items-center gap-2 mb-2">
                    <Badge variant="secondary" className="font-mono text-xs">{formState.targetClassName}</Badge>
                    <Button
                      type="button" variant="ghost" size="sm" className="h-5 text-xs ml-auto"
                      onClick={() => { setShowScenarioBuilder(false); setShowWizard(true); setWizardSkeleton(''); setFormState(prev => ({ ...prev, targetClassName: '' })) }}
                    >
                      Changer de classe
                    </Button>
                  </div>
                  <ScenarioBuilder
                    skeleton={wizardSkeleton}
                    testDataJson={formState.testData}
                    onCancel={() => { setShowScenarioBuilder(false); setWizardSkeleton(''); setFormState(prev => ({ ...prev, targetClassName: '' })) }}
                    onComplete={(result: ScenarioResult, validatedTestData: string) => {
                      setShowScenarioBuilder(false)
                      // Update testData with the LLM-generated + human-validated data
                      setFormState(prev => ({ ...prev, testData: validatedTestData }))
                      setAiState(prev => ({ ...prev, descriptionAI: result.expectedBehavior }))
                      void generateScript(result)
                    }}
                  />
                </div>
              ) : (
                <div className="space-y-3">
                  {/* Selected class badge (if already chosen) */}
                  {formState.targetClassName && !showScenarioBuilder && (
                    <div className="flex items-center gap-2 rounded-md border border-primary/30 bg-primary/5 px-3 py-2">
                      <span className="text-xs text-muted-foreground">Target class:</span>
                      <Badge variant="secondary" className="font-mono">{formState.targetClassName}</Badge>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="ml-auto h-6 text-xs"
                        onClick={() => {
                          setShowWizard(true)
                          setWizardSkeleton('')
                          setFormState((prev) => ({ ...prev, targetClassName: '' }))
                        }}
                      >
                        Change
                      </Button>
                    </div>
                  )}

                  {/* Browse button */}
                  {!formState.targetClassName && (
                    <Button
                      type="button"
                      variant="outline"
                      className="w-full gap-2 border-dashed"
                      onClick={() => setShowWizard(true)}
                    >
                      <Wand2 size={15} />
                      Browse source class & describe test
                    </Button>
                  )}

                  {/* Manual description if class already chosen */}
                  {formState.targetClassName && (
                    <>
                      <div className="space-y-2">
                        <Label htmlFor="case-description-ai">AI description <span className="text-destructive">*</span></Label>
                        <Textarea
                          id="case-description-ai"
                          value={aiState.descriptionAI}
                          onChange={(e) => setAiState((prev) => ({ ...prev, descriptionAI: e.target.value }))}
                          placeholder="Describe what the test should do"
                          required={showAiSection}
                        />
                      </div>
                      <div className="flex gap-2">
                        <Button type="button" variant="outline" onClick={() => generateScript()} disabled={generating}>
                          {generating ? 'Generating…' : 'Generate script'}
                        </Button>
                        {aiState.generatedCode ? (
                          <Button type="button" onClick={() => setAiState((prev) => ({ ...prev, generatedCode: '', codeValidated: false }))} variant="ghost">
                            Regenerate
                          </Button>
                        ) : null}
                      </div>
                    </>
                  )}
                </div>
              )
            ) : (
              /* ── Standard mode for E2E or when no env repo configured ── */
              <>
                {isUnitOrIntegration && !sourceGitInfo && (
                  <div className="rounded-md border border-orange-200 bg-orange-50 dark:bg-orange-950/20 dark:border-orange-800 px-3 py-2">
                    <p className="text-xs text-orange-800 dark:text-orange-300">
                      No source repository configured on this project's environment. Add a Git repository in the Environment settings to enable smart class browsing.
                    </p>
                  </div>
                )}
                <div className="space-y-2">
                  <Label htmlFor="case-description-ai">AI description <span className="text-destructive">*</span></Label>
                  <Textarea
                    id="case-description-ai"
                    value={aiState.descriptionAI}
                    onChange={(e) => setAiState((prev) => ({ ...prev, descriptionAI: e.target.value }))}
                    placeholder="Describe what the test should do in natural language"
                    required={showAiSection}
                  />
                </div>
                <div className="flex gap-2">
                  <Button type="button" variant="outline" onClick={() => generateScript()} disabled={generating}>
                    {generating ? 'Generating…' : 'Generate script'}
                  </Button>
                  {aiState.generatedCode ? (
                    <Button type="button" onClick={() => setAiState((prev) => ({ ...prev, generatedCode: '', codeValidated: false }))} variant="ghost">
                      Regenerate
                    </Button>
                  ) : null}
                </div>
              </>
            )}
          </>
        ) : null}

        {aiState.generatedCode ? (
          <div className="space-y-3 rounded-lg border border-border bg-muted/40 p-4">
            <div className="flex items-center justify-between">
              <Label className="text-base font-semibold">Generated code (editable)</Label>
              <span className="text-xs text-muted-foreground">Click to edit</span>
            </div>
            <Textarea
              className="font-mono text-sm bg-background resize-none focus:ring-2 focus:ring-primary/50"
              value={aiState.generatedCode}
              onChange={(e) => setAiState((prev) => ({ ...prev, generatedCode: e.target.value, codeValidated: false }))}
              rows={20}
            />
            <div className="flex gap-2 pt-2">
              <Button type="button" onClick={validateScript} disabled={aiState.codeValidated}>
                {aiState.codeValidated ? '✓ Validated' : 'Validate script'}
              </Button>
              {aiState.codeValidated && (
                <span className="text-xs text-green-600 flex items-center gap-1">
                  ✓ Code validated and ready to save
                </span>
              )}
            </div>
          </div>
        ) : null}
        <div className="space-y-2">
          <Label>Risk level</Label>
          <Select
            value={formState.riskLevel || 'UNSET'}
            onValueChange={(value) =>
              setFormState((prev) => ({
                ...prev,
                riskLevel: value === 'UNSET' ? '' : (value as RiskLevel),
              }))
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="Select risk" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="UNSET">None</SelectItem>
              <SelectItem value="CRITICAL">CRITICAL</SelectItem>
              <SelectItem value="HIGH">HIGH</SelectItem>
              <SelectItem value="MEDIUM">MEDIUM</SelectItem>
              <SelectItem value="LOW">LOW</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="case-priority">Priority</Label>
            <Input
              id="case-priority"
              value={formState.priority}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, priority: event.target.value }))
              }
              placeholder="1"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="case-duration">Max duration (seconds)</Label>
            <Input
              id="case-duration"
              value={formState.maxDurationSeconds}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, maxDurationSeconds: event.target.value }))
              }
              placeholder="120"
            />
          </div>
        </div>
        {!showAiSection ? (
          <>
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
                      onValueChange={(value) => setSelectedRepoKey(value)}
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
            </div>

            <div className="space-y-2">
              <Label htmlFor="case-script">Script path to execute</Label>
              <Input
                id="case-script"
                value={formState.scriptPath}
                onChange={(event) =>
                  setFormState((prev) => ({ ...prev, scriptPath: event.target.value }))
                }
                placeholder="tests/login.spec.ts"
                required
              />
            </div>
          </>
        ) : null}

        {suite?.type === 'INTEGRATION' ? (
          <div className="space-y-2">
            <Label htmlFor="case-spring-profile">Spring profile</Label>
            <Input
              id="case-spring-profile"
              value={formState.springProfile}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, springProfile: event.target.value }))
              }
              placeholder="test"
            />
          </div>
        ) : null}
        {suite?.type === 'INTEGRATION' ? (
          <div className="space-y-2">
            <Label htmlFor="case-database-type">Database type</Label>
            <Select
              value={formState.databaseType || 'UNSET'}
              onValueChange={(value) => setFormState((prev) => ({ ...prev, databaseType: value }))}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select database" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="POSTGRESQL">POSTGRESQL</SelectItem>
                <SelectItem value="MYSQL">MYSQL</SelectItem>
                <SelectItem value="H2">H2</SelectItem>
                <SelectItem value="MONGODB">MONGODB</SelectItem>
              </SelectContent>
            </Select>
          </div>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor="case-tags">Tags</Label>
          <Input
            id="case-tags"
            value={formState.tags}
            onChange={(event) => setFormState((prev) => ({ ...prev, tags: event.target.value }))}
            placeholder="smoke,auth"
          />
        </div>
        {suite?.type !== 'UNIT' ? (
          <div className="space-y-2">
            <Label htmlFor="case-data">Test data (JSON)</Label>
            <Textarea
              id="case-data"
              value={formState.testData}
              onChange={(event) => setFormState((prev) => ({ ...prev, testData: event.target.value }))}
              placeholder='{"email": "demo@bank.com"}'
            />
          </div>
        ) : null}
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <FormDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        title="Edit test case"
        description="Update the test case metadata."
        submitLabel="Save changes"
        isSubmitting={isSubmitting}
        disableSubmit={
          (showAiSection && Boolean(aiState.generatedCode && aiState.generatedCode.trim()) && !aiState.codeValidated) ||
          (suite?.type === 'INTEGRATION' && !formState.databaseType)
        }
        size="xl"
        onSubmit={submitEdit}
      >
        <div className="space-y-2">
          <Label htmlFor="case-edit-title">Title</Label>
          <Input
            id="case-edit-title"
            value={formState.title}
            onChange={(event) => setFormState((prev) => ({ ...prev, title: event.target.value }))}
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="case-edit-description">Description</Label>
          <Textarea
            id="case-edit-description"
            value={formState.description}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, description: event.target.value }))
            }
          />
        </div>
        <div className="space-y-2">
          <Label>Mode</Label>
          {forceAiForSuite ? (
            <Input value="Generate with AI" readOnly />
          ) : (
            <Select
              value={formState.mode}
              onValueChange={(value) => {
                const mode = value as CaseMode
                setFormState((prev) => ({ ...prev, mode }))
                if (mode !== 'AI') setAiState(emptyAICase)
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select mode" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="AI">Generate with AI</SelectItem>
                <SelectItem value="MANUAL">Write manually</SelectItem>
                <SelectItem value="REPO">Import from repo</SelectItem>
              </SelectContent>
            </Select>
          )}
        </div>
        {showAiSection ? (
          <>
            {(suite?.type === 'UNIT' || suite?.type === 'INTEGRATION') && (
              <div className="space-y-2">
                <Label>
                  Target class <span className="text-muted-foreground text-xs">(optional — improves AI accuracy)</span>
                </Label>
                <Input
                  value={formState.targetClassName}
                  onChange={(e) => setFormState((prev) => ({ ...prev, targetClassName: e.target.value }))}
                  placeholder="e.g. UserService"
                />
                <p className="text-xs text-muted-foreground">
                  Simple class name to test. The AI will extract its method signatures from the source repo and generate a real test.
                </p>
              </div>
            )}
            <div className="space-y-2">
              <Label>AI description{forceAiForSuite ? ' *' : ''}</Label>
              <Textarea
                value={aiState.descriptionAI}
                onChange={(e) => setAiState((prev) => ({ ...prev, descriptionAI: e.target.value }))}
                placeholder="Describe what the test should do in natural language"
                required={showAiSection}
              />
            </div>
            <div className="flex gap-2 mb-2">
              <Button type="button" variant="outline" onClick={() => generateScript()} disabled={generating}>
                {generating ? 'Generating…' : 'Generate script'}
              </Button>
              {aiState.generatedCode ? (
                <Button type="button" onClick={() => setAiState((prev) => ({ ...prev, generatedCode: '', codeValidated: false }))} variant="ghost">
                  Regenerate
                </Button>
              ) : null}
            </div>
          </>
        ) : null}

        {aiState.generatedCode ? (
          <div className="space-y-3 rounded-lg border border-border bg-muted/40 p-4">
            <div className="flex items-center justify-between">
              <Label className="text-base font-semibold">Generated code (editable)</Label>
              <span className="text-xs text-muted-foreground">Click to edit</span>
            </div>
            <Textarea
              className="font-mono text-sm bg-background resize-none focus:ring-2 focus:ring-primary/50"
              value={aiState.generatedCode}
              onChange={(e) => setAiState((prev) => ({ ...prev, generatedCode: e.target.value, codeValidated: false }))}
              rows={20}
            />
            <div className="flex gap-2 pt-2">
              <Button type="button" onClick={validateScript} disabled={aiState.codeValidated}>
                {aiState.codeValidated ? '✓ Validated' : 'Validate script'}
              </Button>
              {aiState.codeValidated && (
                <span className="text-xs text-green-600 flex items-center gap-1">
                  ✓ Code validated and ready to save
                </span>
              )}
            </div>
          </div>
        ) : null}
        <div className="space-y-2">
          <Label>Risk level</Label>
          <Select
            value={formState.riskLevel || 'UNSET'}
            onValueChange={(value) =>
              setFormState((prev) => ({
                ...prev,
                riskLevel: value === 'UNSET' ? '' : (value as RiskLevel),
              }))
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="Select risk" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="UNSET">None</SelectItem>
              <SelectItem value="CRITICAL">CRITICAL</SelectItem>
              <SelectItem value="HIGH">HIGH</SelectItem>
              <SelectItem value="MEDIUM">MEDIUM</SelectItem>
              <SelectItem value="LOW">LOW</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="case-edit-priority">Priority</Label>
            <Input
              id="case-edit-priority"
              value={formState.priority}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, priority: event.target.value }))
              }
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="case-edit-duration">Max duration (seconds)</Label>
            <Input
              id="case-edit-duration"
              value={formState.maxDurationSeconds}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, maxDurationSeconds: event.target.value }))
              }
            />
          </div>
        </div>
        {!showAiSection ? (
          <>
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
                      onValueChange={(value) => setSelectedRepoKey(value)}
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
            </div>

            <div className="space-y-2">
              <Label htmlFor="case-edit-script">Script path to execute</Label>
              <Input
                id="case-edit-script"
                value={formState.scriptPath}
                onChange={(event) =>
                  setFormState((prev) => ({ ...prev, scriptPath: event.target.value }))
                }
                required
              />
            </div>
          </>
        ) : null}

        {suite?.type === 'INTEGRATION' ? (
          <div className="space-y-2">
            <Label htmlFor="case-edit-spring-profile">Spring profile</Label>
            <Input
              id="case-edit-spring-profile"
              value={formState.springProfile}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, springProfile: event.target.value }))
              }
              placeholder="test"
            />
          </div>
        ) : null}
        {suite?.type === 'INTEGRATION' ? (
          <div className="space-y-2">
            <Label htmlFor="case-edit-database-type">Database type</Label>
            <Select
              value={formState.databaseType || 'UNSET'}
              onValueChange={(value) => setFormState((prev) => ({ ...prev, databaseType: value }))}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select database" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="POSTGRESQL">POSTGRESQL</SelectItem>
                <SelectItem value="MYSQL">MYSQL</SelectItem>
                <SelectItem value="H2">H2</SelectItem>
                <SelectItem value="MONGODB">MONGODB</SelectItem>
              </SelectContent>
            </Select>
          </div>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor="case-edit-tags">Tags</Label>
          <Input
            id="case-edit-tags"
            value={formState.tags}
            onChange={(event) => setFormState((prev) => ({ ...prev, tags: event.target.value }))}
          />
        </div>
        {suite?.type !== 'UNIT' ? (
          <div className="space-y-2">
            <Label htmlFor="case-edit-data">Test data (JSON)</Label>
            <Textarea
              id="case-edit-data"
              value={formState.testData}
              onChange={(event) => setFormState((prev) => ({ ...prev, testData: event.target.value }))}
            />
          </div>
        ) : null}
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete test case"
        description={deletingCase ? `Delete ${deletingCase.title}?` : 'Delete test case?'}
        confirmLabel="Delete"
        isConfirming={isDeleting}
        onConfirm={confirmDelete}
      />
    </div>
  )
}

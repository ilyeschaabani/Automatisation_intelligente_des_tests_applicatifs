'use client'

import Link from 'next/link'
import { useParams, useSearchParams } from 'next/navigation'
import { useEffect, useMemo, useRef, useState, useCallback } from 'react'

// Discovery/discovery UI removed — synced to backend campaign model
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
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Separator } from '@/components/ui/separator'
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
  ArrowLeft,
  CheckCircle2,
  Clock,
  AlertCircle,
  Crown,
  Loader2,
  Play,
  ShieldCheck,
  Square,
  User,
  UserPlus,
  Users,
} from 'lucide-react'

import {
  createCampaignExecution,
  type CampaignExecution,
} from '@/lib/campaign-executions'

import { AssignDialog } from '@/components/security/assign-dialog'

import {
  getCampaign,
  getProjects,
  listCampaigns,
  listExecutions,
  listExecutionResults,
  startCampaignRun,
  continueCampaignRun,
  stopCampaign,
  getTestCasesForCampaign,
  assignExecutionResult,
  listReportsForCampaign,
  downloadCampaignReport,
  downloadReportById,
  generateAndStoreReport,
  type CampaignRunContinueRequest,
  type CampaignRunResponse,
  type EditableFileDto,
  type Project,
  type ExecutionStatus,
  type TestCampaignDto,
  type TestExecutionDto,
  type TestCaseWithStatusDto,
  type ExecutionResultBackendDto,
  type SurefireMethodResult,
} from '@/lib/api-client'
import { environmentService } from '@/services/environments'
import type { Environment } from '@/types/ms-gestion'

import { toast } from '@/hooks/use-toast'

type CampaignType = 'Functional' | 'API' | 'Regression'
type CampaignStatus = 'Running' | 'Completed' | 'Failed' | 'Scheduled'

type Campaign = {
  id: string
  name: string
  type: CampaignType
  status: CampaignStatus
  startedAt?: string | null
  finishedAt?: string | null
  progress: number
  tests: number
  passed: number
  failed: number
  lastRun: string
  environment: string
  environmentId?: number | null
  appVersion: string
  owner: string
  branch: string
  triggerMode: string
  tags: string[]
  projectId?: number
}

type CampaignTest = {
  id: string
  name: string
  area: string
  kind: 'UI' | 'API' | 'DB'
}

type ProjectMember = {
  userId: number
  nom: string | null
  prenom: string | null
  email: string | null
  imageUrl: string | null
}

function memberDisplayName(m: ProjectMember): string {
  const parts = [m.prenom, m.nom].filter(Boolean)
  return parts.length > 0 ? parts.join(' ') : `User #${m.userId}`
}

function memberInitials(m: ProjectMember): string {
  const first = (m.prenom ?? '')[0] ?? ''
  const last = (m.nom ?? '')[0] ?? ''
  return (first + last).toUpperCase() || 'U'
}
function mapExecutionDtoToCampaignExecution(
  execution: TestExecutionDto,
  fallbackCampaignId: string,
): CampaignExecution {
  const campaignId =
    execution.campaignId !== undefined && execution.campaignId !== null
      ? String(execution.campaignId)
      : fallbackCampaignId

  return {
    id: 'exec-' + String(execution.id),
    campaignId,
    scope: 'CAMPAIGN',
    status: execution.status as ExecutionStatus,
    createdAt: execution.executionDate,
    duration: '—',
  }
}

function mapBackendStatus(value: string | null | undefined): CampaignStatus {
  const v = String(value ?? '').trim().toLowerCase()
  if (!v) return 'Scheduled'
  if (v.includes('run') || v.includes('in_progress')) return 'Running'
  if (v.includes('complete') || v.includes('finish') || v === 'done') return 'Completed'
  if (v.includes('fail') || v.includes('error') || v.includes('ko')) return 'Failed'
  if (v.includes('schedule') || v.includes('plan') || v.includes('queue') || v.includes('draft')) return 'Scheduled'
  return 'Scheduled'
}

function mapBackendCampaign(dto: TestCampaignDto): Campaign {
  const lastRun = dto.finishedAt ?? dto.startedAt ?? dto.createdAt
  return {
    id: String(dto.id),
    name: dto.name,
    type: 'Functional',
    status: mapBackendStatus(dto.status),
    startedAt: dto.startedAt ?? null,
    finishedAt: dto.finishedAt ?? null,
    progress: 0,
    tests: 0,
    passed: 0,
    failed: 0,
    lastRun: lastRun ? formatWhen(lastRun) : '—',
    environment: dto.environmentId ? `Env #${dto.environmentId}` : '—',
    environmentId: dto.environmentId ?? null,
    appVersion: dto.appVersion ? String(dto.appVersion) : '—',
    owner: '—',
    branch: dto.gitBranch ? String(dto.gitBranch) : '—',
    triggerMode: dto.triggerMode ? String(dto.triggerMode) : '—',
    tags: [],
    projectId: dto.projectId,
  }
}

const executionSteps = [
  'Clonage du dépôt',
  'Préparation du contexte de la campagne',
  'Exécution des tests Maven',
  'Enregistrement des résultats',
] as const

const perStep = Math.floor(100 / executionSteps.length)

function slugify(value: string): string {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')
}

  

const statusStyle: Record<CampaignStatus, string> = {
  Running: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400',
  Completed: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  Failed: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400',
  Scheduled: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-950 dark:text-yellow-400',
}

const typeStyle: Record<CampaignType, string> = {
  Functional: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400',
  API: 'bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-400',
  Regression: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-950 dark:text-cyan-400',
}

const allCampaignsSeed: Omit<Campaign, 'id'>[] = [
  {
    name: 'Banking Mobile App - v2.5',
    type: 'Functional',
    status: 'Running',
    progress: 65,
    tests: 145,
    passed: 94,
    failed: 0,
    lastRun: '5 mins ago',
    environment: 'staging',
    appVersion: 'v2.5',
    owner: 'QA Platform',
    branch: 'release/v2.5',
    triggerMode: 'MANUAL',
    tags: ['mobile', 'release'],
  },
  {
    name: 'Payment Gateway API Tests',
    type: 'API',
    status: 'Completed',
    progress: 100,
    tests: 89,
    passed: 87,
    failed: 2,
    lastRun: '2 hours ago',
    environment: 'preprod',
    appVersion: 'v1.8.3',
    owner: 'API QA',
    branch: 'main',
    triggerMode: 'SCHEDULED',
    tags: ['payments', 'api'],
  },
  {
    name: 'Regression Suite - Production',
    type: 'Regression',
    status: 'Scheduled',
    progress: 0,
    tests: 234,
    passed: 0,
    failed: 0,
    lastRun: 'Tomorrow 2:00 AM',
    environment: 'production',
    appVersion: 'v3.0',
    owner: 'Release QA',
    branch: 'main',
    triggerMode: 'SCHEDULED',
    tags: ['regression', 'prod'],
  },
  {
    name: 'Core Banking Features',
    type: 'Functional',
    status: 'Completed',
    progress: 100,
    tests: 112,
    passed: 110,
    failed: 2,
    lastRun: '1 day ago',
    environment: 'preprod',
    appVersion: 'v2.1',
    owner: 'Core QA',
    branch: 'main',
    triggerMode: 'SCHEDULED',
    tags: ['core', 'functional'],
  },
  {
    name: 'Authentication Module Tests',
    type: 'API',
    status: 'Running',
    progress: 40,
    tests: 76,
    passed: 30,
    failed: 1,
    lastRun: '3 mins ago',
    environment: 'staging',
    appVersion: 'v1.4',
    owner: 'Platform QA',
    branch: 'develop',
    triggerMode: 'CI',
    tags: ['auth', 'api'],
  },
  {
    name: 'UI Components - v3.0',
    type: 'Functional',
    status: 'Failed',
    progress: 85,
    tests: 98,
    passed: 84,
    failed: 14,
    lastRun: '30 mins ago',
    environment: 'staging',
    appVersion: 'v3.0',
    owner: 'Frontend QA',
    branch: 'release/v3.0',
    triggerMode: 'CI',
    tags: ['ui', 'components'],
  },
  {
    name: 'Database Integration Tests',
    type: 'Regression',
    status: 'Completed',
    progress: 100,
    tests: 167,
    passed: 165,
    failed: 2,
    lastRun: '5 hours ago',
    environment: 'staging',
    appVersion: 'v5.0',
    owner: 'Data QA',
    branch: 'main',
    triggerMode: 'SCHEDULED',
    tags: ['db', 'integration'],
  },
  {
    name: 'Security & Compliance Checks',
    type: 'API',
    status: 'Running',
    progress: 72,
    tests: 56,
    passed: 40,
    failed: 0,
    lastRun: '2 mins ago',
    environment: 'preprod',
    appVersion: 'v2.0',
    owner: 'Security QA',
    branch: 'main',
    triggerMode: 'SCHEDULED',
    tags: ['security', 'compliance'],
  },
]

function toCampaigns(): Campaign[] {
  return allCampaignsSeed.map((c) => ({ ...c, id: slugify(c.name) }))
}

function clampPercent(value: number): number {
  if (!Number.isFinite(value)) return 0
  return Math.min(100, Math.max(0, value))
}

function formatPercent(value: number): string {
  const v = clampPercent(value)
  return `${Math.round(v)}%`
}

function computePassRate(passed: number, tests: number): number {
  if (!tests) return 0
  return (passed / tests) * 100
}

function formatWhen(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleString()
}

function seedTestsFor(campaign: Campaign): CampaignTest[] {
  if (campaign.type === 'API') {
    return [
      { id: 'API-001', name: 'Auth token refresh', area: 'Auth', kind: 'API' },
      { id: 'API-002', name: 'Payments - create transaction', area: 'Payments', kind: 'API' },
      { id: 'API-003', name: 'Payments - refund', area: 'Payments', kind: 'API' },
      { id: 'API-004', name: 'Rate limiting', area: 'Gateway', kind: 'API' },
      { id: 'API-005', name: 'Audit logs', area: 'Compliance', kind: 'API' },
      { id: 'API-006', name: 'Permissions matrix', area: 'RBAC', kind: 'API' },
    ]
  }

  if (campaign.type === 'Regression') {
    return [
      { id: 'REG-001', name: 'Smoke - critical flows', area: 'Platform', kind: 'UI' },
      { id: 'REG-002', name: 'DB migration checks', area: 'Database', kind: 'DB' },
      { id: 'REG-003', name: 'Data consistency', area: 'Database', kind: 'DB' },
      { id: 'REG-004', name: 'Login + session stability', area: 'Auth', kind: 'UI' },
      { id: 'REG-005', name: 'Reporting exports', area: 'Reports', kind: 'UI' },
      { id: 'REG-006', name: 'API contract snapshot', area: 'Gateway', kind: 'API' },
    ]
  }

  return [
    { id: 'UI-001', name: 'Login - valid credentials', area: 'Auth', kind: 'UI' },
    { id: 'UI-002', name: 'Login - invalid credentials', area: 'Auth', kind: 'UI' },
    { id: 'UI-003', name: 'Navigation - sidebar routes', area: 'Shell', kind: 'UI' },
    { id: 'UI-004', name: 'Campaigns - list rendering', area: 'Campaigns', kind: 'UI' },
    { id: 'UI-005', name: 'Profile - update details', area: 'Profile', kind: 'UI' },
    { id: 'UI-006', name: 'Settings - save preferences', area: 'Settings', kind: 'UI' },
  ]
}

export default function CampaignDetailsPage() {
  const params = useParams<{ id?: string | string[] }>()
  const searchParams = useSearchParams()
  const id = Array.isArray(params?.id) ? params?.id[0] : params?.id
  const campaignNumericId = useMemo(() => {
    if (!id || !/^\d+$/.test(id)) return null
    return Number(id)
  }, [id])

  const projectIdFromQuery = useMemo(() => {
    const raw = searchParams.get('projectId')
    if (!raw || !/^\d+$/.test(raw)) return null
    return Number(raw)
  }, [searchParams])

  const [resolvedProjectId, setResolvedProjectId] = useState<number | null>(projectIdFromQuery)

  const campaigns = useMemo(() => toCampaigns(), [])
  const seedCampaign = useMemo(() => {
    if (!id) return null
    return campaigns.find((c) => c.id === id) ?? null
  }, [campaigns, id])

  const [runSubmitting, setRunSubmitting] = useState(false)
  const [runDialogOpen, setRunDialogOpen] = useState(false)
  const [runSessionId, setRunSessionId] = useState<string | null>(null)
  const [runMissingDb, setRunMissingDb] = useState(false)
  const [runDbOptions, setRunDbOptions] = useState<string[]>(['postgres', 'mysql', 'mongodb', 'redis'])
  const [runDbValue, setRunDbValue] = useState('')
  const [runMissingEnvVars, setRunMissingEnvVars] = useState<string[]>([])
  const [runEnvValues, setRunEnvValues] = useState<Record<string, string>>({})
  const [runEditableFiles, setRunEditableFiles] = useState<EditableFileDto[]>([])
  const [runFileEdits, setRunFileEdits] = useState<Record<string, string>>({})
  const [runProgressValue, setRunProgressValue] = useState(0)
  const [runStepIndex, setRunStepIndex] = useState(0)
  const [executionUiRunning, setExecutionUiRunning] = useState(false)
  const animationRef = useRef<number | null>(null)
  const lastAnimatedStep = useRef<number | null>(null)
  const lastRetryStepRef = useRef<string | null>(null)

  const stopStepAnimation = () => {
    if (animationRef.current) {
      window.clearInterval(animationRef.current)
      animationRef.current = null
    }
  }

  const startStepAnimation = (stepIndex: number) => {
    stopStepAnimation()
    const base = stepIndex * perStep
    const cap = base + perStep - 1
    animationRef.current = window.setInterval(() => {
      setRunProgressValue((prev) => {
        if (!Number.isFinite(prev)) return base
        const next = Math.min(cap, Math.max(prev + 1, base + 1))
        return next
      })
    }, 400)
    lastAnimatedStep.current = stepIndex
  }

  const [remoteCampaign, setRemoteCampaign] = useState<Campaign | null>(null)
  const [remoteLoading, setRemoteLoading] = useState(false)
  const [remoteError, setRemoteError] = useState<string | null>(null)
  const [backendExecutions, setBackendExecutions] = useState<TestExecutionDto[]>([])
  const [latestExecution, setLatestExecution] = useState<TestExecutionDto | null>(null)

  useEffect(() => {
    setResolvedProjectId(projectIdFromQuery)
  }, [projectIdFromQuery])

  useEffect(() => {
    if (!campaignNumericId) {
      setRemoteCampaign(null)
      setRemoteError(null)
      setRemoteLoading(false)
      return
    }

    let cancelled = false

    const run = async () => {
      setRemoteLoading(true)
      setRemoteError(null)
      try {
        let projectId = resolvedProjectId
        let dto: TestCampaignDto | null = null

        if (!projectId) {
          const projects = await getProjects()
          for (const project of projects) {
            const campaigns = await listCampaigns({ projectId: project.id }).catch(() => [])
            const found = Array.isArray(campaigns)
              ? campaigns.find((campaign) => campaign.id === campaignNumericId) ?? null
              : null
            if (found) {
              projectId = project.id
              dto = found
              break
            }
          }

          if (!projectId || !dto) {
            throw new Error('Campaign not found in your projects.')
          }

          if (!cancelled) setResolvedProjectId(projectId)
        } else {
          dto = await getCampaign(projectId, campaignNumericId)
        }

        if (cancelled || !dto || !projectId) return

        const envs = await environmentService.getAll(projectId)
        const envName = Array.isArray(envs)
          ? envs.find((env: Environment) => env.id === dto.environmentId)?.name
          : null

        const mapped = mapBackendCampaign(dto)
        if (envName) mapped.environment = envName
        setRemoteCampaign(mapped)
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to load campaign'
        if (!cancelled) {
          setRemoteCampaign(null)
          setRemoteError(message)
        }
      } finally {
        if (!cancelled) setRemoteLoading(false)
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [campaignNumericId, resolvedProjectId])

  const campaign = remoteCampaign ?? seedCampaign

  const showExecutionPanel =
    executionUiRunning ||
    latestExecution?.status === 'RUNNING' ||
    latestExecution?.status === 'QUEUED' ||
    latestExecution?.status === 'FINISHED' ||
    latestExecution?.status === 'ERROR' ||
    remoteCampaign?.status === 'Running'

  const isExecutionRunning =
    executionUiRunning ||
    runSubmitting ||
    latestExecution?.status === 'RUNNING' ||
    latestExecution?.status === 'QUEUED' ||
    (remoteCampaign?.status === 'Running')

  useEffect(() => {
    if (!isExecutionRunning || !campaignNumericId) {
      return
    }

    let cancelled = false

    const stepIndexFromValue = (value: string | null | undefined) => {
      const normalized = String(value ?? '').trim().toLowerCase()
      const index = executionSteps.findIndex((step) => step.toLowerCase() === normalized)
      return index >= 0 ? index : 0
    }

    // use component-scoped start/stop animation helpers

    const pollStatus = async () => {
      try {
        const response = await fetch(`/api/campaigns/${campaignNumericId}/status`, {
          method: 'GET',
          headers: { accept: 'application/json' },
          cache: 'no-store',
        })
        
        if (response.ok) {
          const data = await response.json()
          const status = String(data.status ?? '').toUpperCase()
          if (cancelled) return

          const backendProgress = Number(data.progress ?? 0)
          const rawStep = String(data.currentStep ?? '')
          const backendStep = stepIndexFromValue(data.currentStep)

          if (rawStep.startsWith('AI_RETRY:') && lastRetryStepRef.current !== rawStep) {
            lastRetryStepRef.current = rawStep
            const parts = rawStep.split(':')
            const testCaseId = parts[1] ?? '?'
            const attempt = parts[2] ?? '?'
            toast({
              title: "Correction IA en cours",
              description: `L'IA corrige le script du test #${testCaseId} (tentative ${attempt})`,
              duration: 6000,
            })
          }

          if (status === 'PENDING') {
            stopStepAnimation()
            setRunStepIndex(0)
            setRunProgressValue(Math.max(0, backendProgress))
            return
          }

          if (status === 'RUNNING') {
            const cappedStep = Math.min(backendStep, executionSteps.length - 1)
            setRunStepIndex(cappedStep)

            // If step changed, start a fresh in-step animation that grows the bar
            if (lastAnimatedStep.current !== cappedStep) {
              // set progress to the beginning of the step
              setRunProgressValue(cappedStep * perStep)
              startStepAnimation(cappedStep)
            } else {
              // Keep animating; but if backend gives an absolute progress, respect it
              const stepBase = cappedStep * perStep
              const stepCap = stepBase + perStep
              if (Number.isFinite(backendProgress) && backendProgress > runProgressValue) {
                // Respect backend if within this step's range, otherwise clamp
                const bounded = Math.min(stepCap - 1, Math.max(runProgressValue, backendProgress))
                setRunProgressValue(bounded)
              }
            }
            return
          }

          if (status === 'FINISHED' || status === 'FINISHED_WITH_ERRORS') {
            stopStepAnimation()
            setRunStepIndex(executionSteps.length - 1)
            setRunProgressValue(100)
            setExecutionUiRunning(false)
            // mark placeholder execution as finished so UI updates quickly
            setLatestExecution((prev) => (prev ? { ...prev, status: 'FINISHED' } : prev))
            // stop runSubmitting if it was still true
            setRunSubmitting(false)
          }
        }
      } catch (error) {
        // Keep the last known state if polling fails.
        console.warn('Failed to poll campaign execution status.', error)
      }
    }

    // Initial poll immediately
    void pollStatus()

    // Poll every 2 seconds during execution
    const pollInterval = window.setInterval(pollStatus, 2000)

    return () => {
      cancelled = true
      window.clearInterval(pollInterval)
      if (animationRef.current) {
        window.clearInterval(animationRef.current)
        animationRef.current = null
      }
    }
  }, [isExecutionRunning, campaignNumericId])

  // Reset progress only when starting a new execution
  useEffect(() => {
    if (runSubmitting) {
      setRunProgressValue(0)
      setRunStepIndex(0)
    }
  }, [runSubmitting])

  // Per-test-case results (SUCCESS / FAILURE / ERROR) — the source of truth for KPIs.
  // Declared here (above the metrics memos) so they can be derived from it.
  const [executionResults, setExecutionResults] = useState<ExecutionResultBackendDto[]>([])

  const executionMetrics = useMemo(() => {
    const total = executionResults.length
    const passed = executionResults.filter((r) => r.status === 'SUCCESS').length
    const failed = executionResults.filter((r) => r.status === 'FAILURE' || r.status === 'ERROR').length
    const passRate = total ? (passed / total) * 100 : 0
    const failureRate = total ? (failed / total) * 100 : 0
    const riskScore = clampPercent(failureRate * 2)

    return { total, passed, failed, passRate, riskScore }
  }, [executionResults])

  const passRate = executionMetrics.passRate

  const riskScore = useMemo(() => {
    if (!campaign) return 0
    const failureRate = campaign.tests ? (campaign.failed / campaign.tests) * 100 : 0
    // Simple UI-only signal: higher failures => higher risk.
    return clampPercent(failureRate * 2)
  }, [campaign])

  const seedPassRate = useMemo(() => {
    if (!campaign) return 0
    return computePassRate(campaign.passed, campaign.tests)
  }, [campaign])

  const recentRunsSeed = useMemo(() => {
    if (!campaign) return []

    const base = [
      { id: 'run-1042', status: campaign.status === 'Scheduled' ? 'QUEUED' : 'RUNNING', duration: '—', when: 'Just now' },
      { id: 'run-1041', status: 'FINISHED', duration: '12m 18s', when: 'Today 10:04' },
      { id: 'run-1040', status: 'FINISHED', duration: '11m 02s', when: 'Yesterday 23:11' },
      { id: 'run-1039', status: 'ERROR', duration: '2m 41s', when: 'Yesterday 22:44' },
      { id: 'run-1038', status: 'FINISHED', duration: '13m 55s', when: 'Yesterday 20:08' },
    ] as const

    // Make “Scheduled” campaigns look consistent.
    if (campaign.status === 'Scheduled') {
      return base.map((r, idx) =>
        idx === 0 ? { ...r, status: 'QUEUED', when: campaign.lastRun } : r,
      )
    }
    return base
  }, [campaign])

  const tests = useMemo(() => {
    if (!campaign) return []

    if (remoteCampaign) return []

    return seedTestsFor(campaign)
  }, [campaign, remoteCampaign])

  const [testCasesFromApi, setTestCasesFromApi] = useState<TestCaseWithStatusDto[]>([])
  const [expandedResults, setExpandedResults] = useState<Set<number>>(new Set())
  const [reports, setReports] = useState<{
    id: number
    campaignId: number
    filename: string
    generatedAt: string
  }[]>([])
  const [reportsLoading, setReportsLoading] = useState(false)
  const [selectedReportId, setSelectedReportId] = useState<number | null>(null)
  const [autoDownloadedExecutionId, setAutoDownloadedExecutionId] = useState<number | null>(null)
  const [testCasesLoading, setTestCasesLoading] = useState(false)
  const [selectedTestIds, setSelectedTestIds] = useState<string[]>([])

  // Available test cases (from project, not yet in campaign)
  const [availableTestCases, setAvailableTestCases] = useState<TestCaseWithStatusDto[]>([])
  const [availableLoading, setAvailableLoading] = useState(false)
  const [selectedAvailableIds, setSelectedAvailableIds] = useState<string[]>([])
  const [addingTests, setAddingTests] = useState(false)
  const [removingTestId, setRemovingTestId] = useState<number | null>(null)
  const [showRunDialog, setShowRunDialog] = useState(false)
  const [storedRuns, setStoredRuns] = useState<CampaignExecution[]>([])
  const [assignTarget, setAssignTarget] = useState<number | null>(null)
  const [assignedToMe, setAssignedToMe] = useState(false)
  const [currentUserName, setCurrentUserName] = useState<string | null>(null)

  // Project context (name, owner, members) — resolved from ms_gestion.
  const [projectName, setProjectName] = useState<string | null>(null)
  const [ownerId, setOwnerId] = useState<number | null>(null)
  const [members, setMembers] = useState<ProjectMember[]>([])
  const [membersLoading, setMembersLoading] = useState(false)

  const ownerMember = useMemo(
    () => (ownerId != null ? members.find((m) => m.userId === ownerId) ?? null : null),
    [members, ownerId],
  )
  const ownerName = ownerMember ? memberDisplayName(ownerMember) : (ownerId != null ? `User #${ownerId}` : '—')
  const highlightResultId = searchParams.get('highlight')
  const highlightRef = useRef<HTMLDivElement>(null)
  
  useEffect(() => {
    fetch('/api/auth/profile', { credentials: 'include' })
      .then(r => r.ok ? r.json() : null)
      .then(data => {
        if (data) {
          const name = data.fullName || data.name || data.displayName || data.username || null
          setCurrentUserName(name)
        }
      })
      .catch(() => {})
  }, [])

  // Resolve project name + owner + members once the project id is known.
  useEffect(() => {
    if (!resolvedProjectId) {
      setProjectName(null)
      setOwnerId(null)
      setMembers([])
      return
    }

    let cancelled = false

    const loadProjectContext = async () => {
      setMembersLoading(true)
      try {
        const [projects, membersRes] = await Promise.all([
          getProjects().catch(() => [] as Project[]),
          fetch(`/api/projects/${resolvedProjectId}/members`, { credentials: 'include' })
            .then((r) => (r.ok ? r.json() : []))
            .catch(() => []),
        ])
        if (cancelled) return

        const project = Array.isArray(projects)
          ? projects.find((p) => p.id === resolvedProjectId) ?? null
          : null
        setProjectName(project?.name ?? null)
        setOwnerId((project as { createdBy?: number } | null)?.createdBy ?? null)
        setMembers(Array.isArray(membersRes) ? membersRes : [])
      } catch {
        if (!cancelled) {
          setProjectName(null)
          setOwnerId(null)
          setMembers([])
        }
      } finally {
        if (!cancelled) setMembersLoading(false)
      }
    }

    void loadProjectContext()
    return () => {
      cancelled = true
    }
  }, [resolvedProjectId])

  useEffect(() => {
    if (highlightResultId && executionResults.length > 0) {
      const found = executionResults.find(r => r.id === Number(highlightResultId))
      if (found) {
        setExpandedResults(prev => new Set(prev).add(found.id))
        setTimeout(() => highlightRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 200)
      }
    }
  }, [highlightResultId, executionResults])

  // Fetch detailed execution results (with testMethodResults, aiAnalysis, etc.)
  useEffect(() => {
    if (!campaignNumericId) { setExecutionResults([]); return }
    let cancelled = false
    const load = async () => {
      try {
        const results = await listExecutionResults({ campaignId: campaignNumericId })
        if (!cancelled) setExecutionResults(results)
      } catch { if (!cancelled) setExecutionResults([]) }
    }
    void load()
    return () => { cancelled = true }
  }, [campaignNumericId, latestExecution?.status]) // re-fetch when execution finishes

  // Fetch test cases from API when campaign is loaded
  useEffect(() => {
    if (!remoteCampaign || !campaignNumericId || !resolvedProjectId) {
      setTestCasesFromApi([])
      setSelectedTestIds([])
      return
    }

    let cancelled = false

    const loadTestCases = async () => {
      setTestCasesLoading(true)
      try {
        const testCases = await getTestCasesForCampaign(resolvedProjectId, campaignNumericId)
        if (!cancelled) {
          setTestCasesFromApi(testCases)
        }
      } catch (error) {
        console.warn('Failed to load test cases for campaign:', error)
        if (!cancelled) {
          setTestCasesFromApi([])
        }
      } finally {
        if (!cancelled) {
          setTestCasesLoading(false)
        }
      }
    }

    void loadTestCases()
    return () => {
      cancelled = true
    }
  }, [remoteCampaign, campaignNumericId, resolvedProjectId])
  

  useEffect(() => {
    if (!campaignNumericId) {
      setBackendExecutions([])
      setLatestExecution(null)
      setExecutionUiRunning(false)
      setStoredRuns([])
      setSelectedTestIds([])
      setReports([])
      setSelectedReportId(null)
      return
    }

    let cancelled = false

    const loadExecutions = async () => {
      try {
        const executions = await listExecutions({ campaignId: campaignNumericId })
        if (cancelled) return
        
        // Only update if we have results, or if we're not currently running
        // This prevents clearing the placeholder execution during an active run
        if (executions.length > 0 || !isExecutionRunning) {
          setBackendExecutions(executions)
          setLatestExecution(executions[0] ?? null)
        }
      } catch (error) {
        if (!cancelled) {
          // Only clear if we're not running - keep the placeholder during execution
          if (!isExecutionRunning) {
            setBackendExecutions([])
            setLatestExecution(null)
          }
          console.warn('Campaign execution history is unavailable, continuing without it.', error)
        }
      }
    }

    void loadExecutions()
    setStoredRuns([])
    setSelectedTestIds([])
    return () => {
      cancelled = true
    }
  }, [campaignNumericId, isExecutionRunning])

  // Load reports for this campaign if the backend supports listing them.
  useEffect(() => {
    if (!campaignNumericId) return
    let cancelled = false
    const load = async () => {
      setReportsLoading(true)
      try {
        const list = await listReportsForCampaign(campaignNumericId)
        if (!cancelled) {
          setReports(list ?? [])
          if ((list ?? []).length > 0) setSelectedReportId(list[0].id)
        }
      } catch (e) {
        if (!cancelled) setReports([])
      } finally {
        if (!cancelled) setReportsLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [campaignNumericId])

  // Auto-download report when a backend execution finishes (generates PDF on demand).
  useEffect(() => {
    if (!campaignNumericId) return
    if (!latestExecution) return
    if (latestExecution.status !== 'FINISHED') return
    // Avoid re-downloading for the same execution
    if (autoDownloadedExecutionId === latestExecution.id) return

    let cancelled = false
    const download = async () => {
      try {
        const blob = await downloadCampaignReport(campaignNumericId)
        if (cancelled) return
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `campaign-${campaignNumericId}-report.pdf`
        document.body.appendChild(a)
        a.click()
        a.remove()
        window.URL.revokeObjectURL(url)
        setAutoDownloadedExecutionId(latestExecution.id)
        toast({ title: 'Report downloaded', description: 'Campaign report downloaded automatically.' })
      } catch (e) {
        console.warn('Failed to auto-download report', e)
      }
    }

    void download()
    return () => {
      cancelled = true
    }
  }, [latestExecution, campaignNumericId, autoDownloadedExecutionId])

  useEffect(() => {
    return () => {}
  }, [])

  useEffect(() => {
    // linkedProject removed — discovery UI cleared
  }, [campaign?.projectId])

  const linkedDefaultBranchLabel = useMemo(() => '', [])

  const recentRuns = useMemo(() => {
    const backendRuns = backendExecutions.map((r) => {
      return {
        id: 'exec-' + String(r.id),
        status: r.status as ExecutionStatus,
        duration: '—',
        when: formatWhen(r.executionDate),
      }
    })

    const localRuns = storedRuns.map((r) => {
      return {
        id: r.id,
        status: r.status as ExecutionStatus,
        duration: r.duration ?? '—',
        when: formatWhen(r.createdAt),
      }
    })
    
    // For backend campaigns, only show backend runs
    // For seed campaigns, show seed data as fallback
    if (campaignNumericId) {
      return [...backendRuns, ...localRuns]
    }
    
    return [...backendRuns, ...localRuns, ...recentRunsSeed]
  }, [backendExecutions, recentRunsSeed, storedRuns, campaignNumericId])
  


  // -------------------------------------------------------------------------------------------------------------------

   const applyRunResponse = async (response: CampaignRunResponse) => {
    const status = String(response.status ?? '').trim().toLowerCase()

    if (status === 'needs_user_input' || status === 'needs_review') {
      const sessionId = String(response.sessionId ?? '').trim()
      if (!sessionId) {
        throw new Error('Backend requires user inputs but did not return sessionId')
      }

      setRunSessionId(sessionId)
      setRunMissingDb(Boolean(response.missingDb))

      const options = Array.isArray(response.dbOptions)
        ? response.dbOptions.map((v) => String(v)).filter((v) => v.length > 0)
        : []
      setRunDbOptions(options.length > 0 ? options : ['postgres', 'mysql', 'mongodb', 'redis'])

      const missingEnv = Array.isArray(response.missingEnvVars)
        ? response.missingEnvVars.map((v) => String(v)).filter((v) => v.length > 0)
        : []
      setRunMissingEnvVars(missingEnv)
      setRunEnvValues((prev) => {
        const next = { ...prev }
        for (const key of missingEnv) {
          if (!(key in next)) next[key] = ''
        }
        return next
      })

      const editable = Array.isArray(response.editableFiles)
        ? response.editableFiles
            .map((f) => ({
              path: String(f?.path ?? '').trim(),
              content: String(f?.content ?? ''),
            }))
            .filter((f) => f.path.length > 0)
        : []
      setRunEditableFiles(editable)
      setRunFileEdits(() => {
        const next: Record<string, string> = {}
        for (const file of editable) {
          next[file.path] = file.content
        }
        return next
      })

      setRunDialogOpen(true)

      if (status === 'needs_user_input') {
        toast({
          title: 'More information needed',
          description: 'Please provide DB and environment values, then review docker files to continue.',
        })
      } else {
        toast({
          title: 'Review docker artifacts',
          description: 'Review and edit docker-compose.yml / Dockerfiles, then start the run.',
        })
      }
      return
    }

    if (status !== 'started') {
      throw new Error(response.message ?? 'Unexpected run response')
    }

    // Create a placeholder execution with RUNNING status to show loading bar
    // The polling will update progress from the backend status endpoint
    const placeholderExecution: TestExecutionDto = {
      id: Math.random() * 100000,
      executionNumber: null,
      executionDate: new Date().toISOString(),
      executionType: 'INITIAL',
      status: 'RUNNING',
      campaignId: campaignNumericId || -1,
    }
    setLatestExecution(placeholderExecution)
    setBackendExecutions((prev) => [placeholderExecution, ...prev])
    setExecutionUiRunning(true)

    // Bootstrap the UI immediately; backend polling will refine the step and percentage.
    setRunStepIndex(0)
    setRunProgressValue(5)
    try {
      startStepAnimation(0)
    } catch (e) {
      // ignore in environments where window timers are restricted
    }


    setRunDialogOpen(false)
    setRunSessionId(null)
    setRunEditableFiles([])
    setRunFileEdits({})

    toast({
      title: 'Campaign started',
      description: 'Repo prepared and docker files generated.',
    })
  }

  const runCampaign = async () => {
    if (!campaign) return

    if (!campaignNumericId) {
      const created = createCampaignExecution({ campaignId: campaign.id, scope: 'CAMPAIGN' })
      setStoredRuns((prev) => [created, ...prev])
      return
    }

    if (!campaign.projectId) {
      toast({
        title: 'Cannot run campaign',
        description: 'This campaign is not linked to a project.',
        variant: 'destructive',
      })
      return
    }

    // Show run mode dialog instead of running directly
    void loadAvailableTestCases()
    setShowRunDialog(true)
  }

  // ── Load available test cases (from project, not yet in campaign) ──
  const loadAvailableTestCases = async () => {
    if (!campaignNumericId || !campaign?.projectId) return
    setAvailableLoading(true)
    try {
      const res = await fetch(
        `/api/campaigns/${campaignNumericId}/available-testcases?projectId=${campaign.projectId}`,
        { credentials: 'include' }
      )
      if (res.ok) {
        const data = await res.json()
        setAvailableTestCases(Array.isArray(data) ? data : [])
      }
    } catch { setAvailableTestCases([]) }
    finally { setAvailableLoading(false) }
  }

  // ── Add test cases to campaign ──
  const handleAddTestCases = async () => {
    if (!campaignNumericId || !campaign?.projectId || selectedAvailableIds.length === 0) return
    setAddingTests(true)
    try {
      const res = await fetch(
        `/api/campaigns/${campaignNumericId}/testcases?projectId=${campaign.projectId}`,
        {
          method: 'POST',
          credentials: 'include',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ testCaseIds: selectedAvailableIds.map(Number) }),
        }
      )
      if (res.ok) {
        toast({ title: 'Tests ajoutés', description: `${selectedAvailableIds.length} cas de test ajouté(s) à la campagne.` })
        setSelectedAvailableIds([])
        // Reload both lists
        void loadAvailableTestCases()
        if (campaign.projectId) {
          const testCases = await getTestCasesForCampaign(campaign.projectId, campaignNumericId)
          setTestCasesFromApi(Array.isArray(testCases) ? testCases : [])
        }
      } else {
        const data = await res.json().catch(() => ({}))
        toast({ title: 'Erreur', description: data.message ?? 'Impossible d\'ajouter les tests', variant: 'destructive' })
      }
    } catch (err) {
      toast({ title: 'Erreur', description: err instanceof Error ? err.message : 'Erreur réseau', variant: 'destructive' })
    } finally { setAddingTests(false) }
  }

  // ── Remove a test case from campaign ──
  const handleRemoveTestCase = async (testCaseId: number) => {
    if (!campaignNumericId || !campaign?.projectId) return
    setRemovingTestId(testCaseId)
    try {
      const res = await fetch(
        `/api/campaigns/${campaignNumericId}/testcases/${testCaseId}?projectId=${campaign.projectId}`,
        { method: 'DELETE', credentials: 'include' }
      )
      if (res.ok || res.status === 204) {
        toast({ title: 'Test retiré', description: 'Le cas de test a été retiré de la campagne.' })
        setTestCasesFromApi(prev => prev.filter(tc => tc.id !== testCaseId))
        void loadAvailableTestCases()
      } else {
        const data = await res.json().catch(() => ({}))
        toast({ title: 'Erreur', description: data.message ?? 'Impossible de retirer le test', variant: 'destructive' })
      }
    } catch (err) {
      toast({ title: 'Erreur', description: err instanceof Error ? err.message : 'Erreur réseau', variant: 'destructive' })
    } finally { setRemovingTestId(null) }
  }

  // ── Run with mode selection ──
  const runCampaignWithMode = async (mode: 'ALL' | 'SELECTED') => {
    if (!campaignNumericId || !campaign?.projectId) return
    setShowRunDialog(false)
    setExecutionUiRunning(true)
    setRunSubmitting(true)
    try {
      const payload: Record<string, unknown> = { runMode: mode }
      if (mode === 'SELECTED' && selectedTestIds.length > 0) {
        payload.testCaseIds = selectedTestIds.map(Number)
      }
      const response = await startCampaignRun(campaignNumericId, payload as any)
      await applyRunResponse(response)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to start campaign run'
      setExecutionUiRunning(false)
      toast({ title: 'Run failed', description: message, variant: 'destructive' })
    } finally { setRunSubmitting(false) }
  }

  const handleStopCampaign = async () => {
    if (!campaignNumericId || !campaign?.projectId) return

    try {
      await stopCampaign(campaign.projectId, campaignNumericId)
      toast({
        title: 'Campaign stopped',
        description: 'The campaign has been aborted successfully.',
        variant: 'default',
      })
      // Reload the campaign status after a short delay
      setTimeout(() => {
        if (!campaignNumericId || !campaign?.projectId) return
        void (async () => {
          try {
            const dto = await getCampaign(campaign.projectId!, campaignNumericId)
            const mapped = mapBackendCampaign(dto)
            setRemoteCampaign(mapped)
          } catch (error) {
            console.warn('Failed to reload campaign status', error)
          }
        })()
      }, 500)
    } catch (error) {
      toast({
        title: 'Error',
        description: error instanceof Error ? error.message : 'Failed to stop campaign',
        variant: 'destructive',
      })
    }
  }

  const submitRunInputs = async () => {
    if (!campaignNumericId || !runSessionId) return

    if (runMissingDb && String(runDbValue).trim().length === 0) {
      toast({
        title: 'Database is required',
        description: 'Select a database type to continue.',
        variant: 'destructive',
      })
      return
    }

    const unresolvedEnvVars = runMissingEnvVars.filter(
      (key) => String(runEnvValues[key] ?? '').trim().length === 0,
    )
    if (unresolvedEnvVars.length > 0) {
      toast({
        title: 'Missing environment values',
        description: 'Please fill all required variables before continuing.',
        variant: 'destructive',
      })
      return
    }

    const envValues: Record<string, string> = {}
    for (const [key, rawValue] of Object.entries(runEnvValues)) {
      const value = String(rawValue ?? '').trim()
      if (value) envValues[key] = value
    }

    const payload: CampaignRunContinueRequest = {
      sessionId: runSessionId,
    }

    if (runMissingDb) payload.db = String(runDbValue).trim()
    if (Object.keys(envValues).length > 0) payload.envValues = envValues
    if (Object.keys(runFileEdits).length > 0) payload.fileOverrides = runFileEdits

    setExecutionUiRunning(true)
    setRunSubmitting(true)
    try {
      const response = await continueCampaignRun(campaignNumericId, payload)
      await applyRunResponse(response)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to continue campaign run'
      setExecutionUiRunning(false)
      toast({ title: 'Run failed', description: message, variant: 'destructive' })
    } finally {
      setRunSubmitting(false)
    }
  }

  // -------------------------------------------------------------------------------------------------------------------

  const runSelectedTests = () => {
    if (!campaign) return
    if (selectedTestIds.length === 0) return
    const created = createCampaignExecution({
      campaignId: campaign.id,
      scope: 'SELECTED_TESTS',
      selectedTests: selectedTestIds,
    })
    setStoredRuns((prev) => [created, ...prev])
    setSelectedTestIds([])
  }

  const runButtonLabel = runSubmitting
    ? 'Exécution…'
    : latestExecution?.status === 'FINISHED' || latestExecution?.status === 'ERROR'
    ? 'Relancer la campagne'
    : 'Lancer la campagne'

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-7xl">
          <div className="mb-6 flex flex-col gap-4">
            <Breadcrumb>
              <BreadcrumbList>
                <BreadcrumbItem>
                  <BreadcrumbLink asChild>
                    <Link href="/campaigns">Campaigns</Link>
                  </BreadcrumbLink>
                </BreadcrumbItem>
                <BreadcrumbSeparator />
                <BreadcrumbItem>
                  <BreadcrumbPage>{campaign?.name ?? 'Détails de la campagne'}</BreadcrumbPage>
                </BreadcrumbItem>
              </BreadcrumbList>
            </Breadcrumb>

            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0">
                <h1 className="text-3xl font-bold text-foreground truncate">
                  {campaign?.name ?? 'Campagne introuvable'}
                </h1>
                <p className="text-muted-foreground mt-1">
                  {campaign
                    ? 'Vue d\'ensemble de la campagne, signaux qualité et dernières exécutions.'
                    : 'Cet identifiant de campagne ne correspond à aucune campagne.'}
                </p>
              </div>

              <Button asChild variant="outline" className="gap-2 shrink-0">
                <Link href="/campaigns">
                  <ArrowLeft size={18} />
                  Retour
                </Link>
              </Button>
            </div>
          </div>

          {!campaign ? (
            <Card className="p-6">
              {remoteLoading ? (
                <p className="text-sm text-muted-foreground">Loading campaign…</p>
              ) : remoteError ? (
                <p className="text-sm text-destructive">{remoteError}</p>
              ) : (
                <p className="text-sm text-muted-foreground">
                  Tip: open a campaign from the list to land here.
                </p>
              )}
            </Card>
          ) : (
            <>
              {/* Hero */}
              <Card className="mb-6 overflow-hidden border-border/70 bg-gradient-to-br from-background via-background to-secondary/20 shadow-sm">
                <div className="h-1 bg-gradient-to-r from-primary via-cyan-500 to-accent" />
                <div className="p-6 lg:p-8">
                  <div className="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
                    <div className="min-w-0 space-y-4">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="outline" className={typeStyle[campaign.type]}>
                          {campaign.type}
                        </Badge>
                        <Badge variant="outline" className={statusStyle[campaign.status]}>
                          {campaign.status === 'Running' && (
                            <span className="inline-block size-1.5 bg-current rounded-full mr-1 animate-pulse" />
                          )}
                          {campaign.status}
                        </Badge>
                        {campaign.projectId ? (
                          <Badge variant="secondary" className="rounded-full">
                            {projectName ?? `Project #${campaign.projectId}`}
                          </Badge>
                        ) : null}
                      </div>

                      <div>
                        <h1 className="text-3xl lg:text-4xl font-semibold tracking-tight text-foreground truncate">
                          {campaign.name}
                        </h1>
                        <p className="text-sm lg:text-base text-muted-foreground mt-2 max-w-3xl">
                          Vue d'ensemble de la campagne, configuration de l'environnement et activité d'exécution.
                        </p>
                      </div>

                      <div className="flex flex-wrap gap-2">
                        {campaign.tags.map((t) => (
                          <Badge key={t} variant="secondary" className="rounded-full px-3 py-1">
                            {t}
                          </Badge>
                        ))}
                        {campaign.triggerMode !== '—' ? (
                          <Badge variant="outline" className="rounded-full px-3 py-1">
                            Trigger {campaign.triggerMode}
                          </Badge>
                        ) : null}
                      </div>

                      {showExecutionPanel ? (
                        <div className="rounded-2xl border border-border/70 bg-card/80 p-4 backdrop-blur">
                          <div className="space-y-4">
                            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                              <div>
                                <p className="text-sm font-semibold text-foreground">
                                  {isExecutionRunning ? 'ms-execution exécute la campagne' : 'ms-execution a terminé la campagne'}
                                </p>
                                <p className="text-sm text-muted-foreground">
                                  {isExecutionRunning
                                    ? `${campaign.name} est en cours de clonage, d'exécution et de persistance dans le backend.`
                                    : `${campaign.name} s'est terminée avec succès et la progression finale est conservée.`}
                                </p>
                              </div>
                              <Badge variant="secondary" className="w-fit rounded-full">
                                {isExecutionRunning ? executionSteps[runStepIndex] : 'Terminé'}
                              </Badge>
                            </div>

                            <div className="space-y-2">
                              <div className="flex items-center justify-between text-xs">
                                <span className="font-medium text-muted-foreground">
                                  {isExecutionRunning ? 'Progression' : 'Terminé'}
                                </span>
                                <span className="font-semibold tabular-nums text-foreground">
                                  {formatPercent(isExecutionRunning ? runProgressValue : 100)}
                                </span>
                              </div>
                              {/* Modern gradient progress bar with animated shimmer while running */}
                              <div className="relative h-2.5 w-full overflow-hidden rounded-full bg-muted">
                                <div
                                  className="h-full rounded-full bg-gradient-to-r from-primary via-cyan-500 to-emerald-500 transition-[width] duration-500 ease-out"
                                  style={{ width: `${clampPercent(isExecutionRunning ? runProgressValue : 100)}%` }}
                                >
                                  {isExecutionRunning && (
                                    <div className="h-full w-full animate-pulse bg-white/20" />
                                  )}
                                </div>
                              </div>
                            </div>

                            <div className="grid gap-2 text-sm sm:grid-cols-2">
                              {executionSteps.map((step, index) => {
                                const done = index < runStepIndex || (!isExecutionRunning && true)
                                const current = isExecutionRunning && index === runStepIndex
                                return (
                                  <div
                                    key={step}
                                    className={
                                      'flex items-center gap-2.5 rounded-lg border px-3 py-2 transition-colors ' +
                                      (current
                                        ? 'border-primary/40 bg-primary/5 text-foreground shadow-sm'
                                        : done
                                          ? 'border-emerald-500/30 bg-emerald-500/5 text-foreground'
                                          : 'border-border bg-background/60 text-muted-foreground')
                                    }
                                  >
                                    <span className="shrink-0">
                                      {current ? (
                                        <Loader2 className="h-4 w-4 animate-spin text-primary" />
                                      ) : done ? (
                                        <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                                      ) : (
                                        <span className="flex h-4 w-4 items-center justify-center rounded-full border border-current text-[10px] font-semibold">
                                          {index + 1}
                                        </span>
                                      )}
                                    </span>
                                    <span className="truncate">{step}</span>
                                  </div>
                                )
                              })}
                            </div>
                          </div>
                        </div>
                      ) : null}
                    </div>

                    <div className="grid gap-3 sm:grid-cols-2 lg:w-[360px]">
                      <div className="rounded-2xl border border-border/70 bg-card/80 p-4 backdrop-blur">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Dernière exécution</p>
                        <p className="mt-2 text-sm font-medium text-foreground">{campaign.lastRun}</p>
                      </div>
                      <div className="rounded-2xl border border-border/70 bg-card/80 p-4 backdrop-blur">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Branche</p>
                        <p className="mt-2 text-sm font-medium text-foreground truncate">{campaign.branch}</p>
                      </div>
                      <div className="rounded-2xl border border-border/70 bg-card/80 p-4 backdrop-blur">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Environnement</p>
                        <p className="mt-2 text-sm font-medium text-foreground truncate">{campaign.environment}</p>
                      </div>
                      <div className="rounded-2xl border border-border/70 bg-card/80 p-4 backdrop-blur">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Version</p>
                        <p className="mt-2 text-sm font-medium text-foreground truncate">{campaign.appVersion}</p>
                      </div>
                      <div className="rounded-2xl border border-border/70 bg-white p-4">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Rapport</p>
                        {reportsLoading ? (
                          <p className="mt-2 text-sm text-muted-foreground">Chargement des rapports…</p>
                        ) : reports.length > 0 ? (
                          <div className="mt-2 flex flex-col gap-2">
                            <Select
                              value={String(selectedReportId ?? '')}
                              onValueChange={(v) => setSelectedReportId(v ? Number(v) : null)}
                            >
                              <SelectTrigger className="w-full">
                                <SelectValue placeholder="Select report" />
                              </SelectTrigger>
                              <SelectContent>
                                {reports.map((r) => (
                                  <SelectItem key={r.id} value={String(r.id)}>
                                    {r.filename} — {new Date(r.generatedAt).toLocaleString()}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                            <Button
                              size="sm"
                              onClick={async () => {
                                if (!campaignNumericId) return
                                try {
                                  const blob = selectedReportId
                                    ? await downloadReportById(selectedReportId)
                                    : await downloadCampaignReport(campaignNumericId)
                                  const url = window.URL.createObjectURL(blob)
                                  const a = document.createElement('a')
                                  a.href = url
                                  a.download = selectedReportId
                                    ? reports.find(r => r.id === selectedReportId)?.filename ?? `campaign-${campaignNumericId}-report.pdf`
                                    : `campaign-${campaignNumericId}-report.pdf`
                                  document.body.appendChild(a)
                                  a.click()
                                  a.remove()
                                  window.URL.revokeObjectURL(url)
                                  toast({ title: 'Report downloaded', description: 'Report downloaded.' })
                                } catch (e) {
                                  toast({ title: 'Error', description: (e as Error).message ?? String(e), variant: 'destructive' })
                                }
                              }}
                            >
                              Download
                            </Button>
                          </div>
                        ) : (
                          <div className="mt-2 flex flex-col gap-2">
                            <p className="text-sm text-muted-foreground">Aucun rapport enregistré. Vous pouvez télécharger le dernier rapport.</p>
                            <Button
                              size="sm"
                              onClick={async () => {
                                if (!campaignNumericId) return
                                try {
                                  const blob = await downloadCampaignReport(campaignNumericId)
                                  const url = window.URL.createObjectURL(blob)
                                  const a = document.createElement('a')
                                  a.href = url
                                  a.download = `campaign-${campaignNumericId}-report.pdf`
                                  document.body.appendChild(a)
                                  a.click()
                                  a.remove()
                                  window.URL.revokeObjectURL(url)
                                  toast({ title: 'Report downloaded', description: 'Report downloaded.' })
                                } catch (e) {
                                  toast({ title: 'Error', description: (e as Error).message ?? String(e), variant: 'destructive' })
                                }
                              }}
                            >
                              Download
                            </Button>
                          </div>
                        )}
                      </div>

                    </div>
                  </div>

                  <div className="mt-8 flex flex-col sm:flex-row sm:items-center gap-3">
                    <Button asChild variant="outline" size="sm" className="gap-2">
                      <Link href={`/executions?campaignId=${campaign.id}`}>
                        <Clock className="h-4 w-4" />
                        Voir les exécutions
                      </Link>
                    </Button>
                    {isExecutionRunning || campaign.status === 'Running' ? (
                      <Button
                        type="button"
                        size="sm"
                        variant="destructive"
                        className="gap-2"
                        onClick={() => void handleStopCampaign()}
                      >
                        <Square className="h-4 w-4" />
                        Arrêter la campagne
                      </Button>
                    ) : (
                      <Button
                        type="button"
                        size="sm"
                        className="gap-2"
                        onClick={() => void runCampaign()}
                        disabled={runSubmitting}
                      >
                        <Play className="h-4 w-4" />
                        {runButtonLabel}
                      </Button>
                    )}
                  </div>

                  <Dialog open={runDialogOpen} onOpenChange={setRunDialogOpen}>
          <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>Vérifier les artefacts d'exécution</DialogTitle>
              <DialogDescription>
                Vérifiez les fichiers générés, fournissez les valeurs manquantes, puis continuez l'exécution.
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4">
              {runMissingDb ? (
                <div className="space-y-2">
                  <Label>Type de base de données</Label>
                  <Select value={runDbValue} onValueChange={setRunDbValue}>
                    <SelectTrigger>
                      <SelectValue placeholder="Sélectionner une base de données" />
                    </SelectTrigger>
                    <SelectContent>
                      {runDbOptions.map((db) => (
                        <SelectItem key={db} value={db}>
                          {db}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              ) : null}

              {runMissingEnvVars.map((key) => (
                <div key={key} className="space-y-2">
                  <Label htmlFor={'run-env-' + key}>{key}</Label>
                  <Input
                    id={'run-env-' + key}
                    value={runEnvValues[key] ?? ''}
                    onChange={(e) =>
                      setRunEnvValues((prev) => ({
                        ...prev,
                        [key]: e.target.value,
                      }))
                    }
                    placeholder={'Value for ' + key}
                  />
                </div>
              ))}

              {runEditableFiles.length > 0 ? (
                <>
                  <Separator />
                  <div className="space-y-4">
                    {runEditableFiles.map((file) => (
                      <div key={file.path} className="space-y-2">
                        <Label htmlFor={'run-file-' + slugify(file.path)}>{file.path}</Label>
                        <Textarea
                          id={'run-file-' + slugify(file.path)}
                          value={runFileEdits[file.path] ?? file.content ?? ''}
                          onChange={(e) =>
                            setRunFileEdits((prev) => ({
                              ...prev,
                              [file.path]: e.target.value,
                            }))
                          }
                          className="min-h-[220px] font-mono text-xs"
                          spellCheck={false}
                        />
                      </div>
                    ))}
                  </div>
                </>
              ) : null}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setRunDialogOpen(false)}>
                Annuler
              </Button>
              <Button
                type="button"
                onClick={() => void submitRunInputs()}
                disabled={runSubmitting || (runMissingDb && String(runDbValue).trim().length === 0)}
              >
                {runSubmitting ? 'Envoi…' : 'Démarrer l\'exécution'}
              </Button>
            </DialogFooter>
          </DialogContent>
                  </Dialog>

                  
                </div>
              </Card>

              {/* Content grid */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2 space-y-6">
                  <Card>
                    <CardHeader>
                      <CardTitle>Vue d'ensemble</CardTitle>
                      <CardDescription>
                        Indicateurs clés et signaux rapides de cette campagne.
                      </CardDescription>
                    </CardHeader>
                    <CardContent>
                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                        <div className="rounded-lg border border-border bg-card p-4">
                          <div className="flex items-center justify-between">
                            <p className="text-sm text-muted-foreground">Total des tests</p>
                            <Play className="h-4 w-4 text-muted-foreground" />
                          </div>
                          <p className="mt-2 text-2xl font-bold text-foreground">{executionMetrics.total || campaign.tests}</p>
                        </div>
                        <div className="rounded-lg border border-border bg-card p-4">
                          <div className="flex items-center justify-between">
                            <p className="text-sm text-muted-foreground">Réussis</p>
                            <CheckCircle2 className="h-4 w-4 text-green-600" />
                          </div>
                          <p className="mt-2 text-2xl font-bold text-foreground">{executionMetrics.passed || campaign.passed}</p>
                        </div>
                        <div className="rounded-lg border border-border bg-card p-4">
                          <div className="flex items-center justify-between">
                            <p className="text-sm text-muted-foreground">Échoués</p>
                            <AlertCircle className="h-4 w-4 text-red-600" />
                          </div>
                          <p className="mt-2 text-2xl font-bold text-foreground">{executionMetrics.failed || campaign.failed}</p>
                        </div>
                      </div>

                      <Separator className="my-6" />

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                        <div>
                          <div className="flex items-center justify-between mb-2">
                            <span className="text-sm text-muted-foreground">Taux de réussite</span>
                            <span className="text-sm font-semibold text-foreground">{formatPercent(executionMetrics.passRate || seedPassRate)}</span>
                          </div>
                          <Progress value={executionMetrics.passRate || seedPassRate} className="h-2" />
                        </div>

                        <div>
                          <div className="flex items-center justify-between mb-2">
                            <span className="text-sm text-muted-foreground">Signal de risque</span>
                            <span className="text-sm font-semibold text-foreground">{formatPercent(executionMetrics.riskScore || riskScore)}</span>
                          </div>
                          <Progress value={executionMetrics.riskScore || riskScore} className="h-2" />
                        </div>
                      </div>
                    </CardContent>
                  </Card>

                  <Card>
                    <CardHeader>
                      <CardTitle>Exécutions récentes</CardTitle>
                      <CardDescription>Dernières exécutions backend et historique local de cette campagne.</CardDescription>
                    </CardHeader>
                    <CardContent>
                      <div className="rounded-md border border-border overflow-hidden">
                        <Table>
                          <TableHeader>
                            <TableRow>
                              <TableHead>Exécution</TableHead>
                              <TableHead>Statut</TableHead>
                              <TableHead>Durée</TableHead>
                              <TableHead className="text-right">Quand</TableHead>
                            </TableRow>
                          </TableHeader>
                          <TableBody>
                            {recentRuns.map((r) => (
                              <TableRow key={r.id}>
                                <TableCell className="font-medium">{r.id}</TableCell>
                                <TableCell>
                                  <Badge
                                    variant="outline"
                                    className={
                                      r.status === 'FINISHED'
                                        ? 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400'
                                        : r.status === 'RUNNING'
                                          ? 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400'
                                          : r.status === 'ERROR'
                                            ? 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400'
                                            : 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400'
                                    }
                                  >
                                    {r.status}
                                  </Badge>
                                </TableCell>
                                <TableCell>{r.duration}</TableCell>
                                <TableCell className="text-right text-muted-foreground">{r.when}</TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </div>
                    </CardContent>
                  </Card>

                  <Card>
                    <CardHeader>
                      <div className="flex items-center justify-between">
                        <div>
                          <CardTitle>Résultats des tests</CardTitle>
                          <CardDescription>Résultats détaillés par cas de test, avec décomposition par méthode.</CardDescription>
                        </div>
                        {executionResults.length > 0 && (
                          <Button
                            variant={assignedToMe ? 'default' : 'outline'}
                            size="sm"
                            className="text-xs gap-1.5"
                            onClick={() => setAssignedToMe(prev => !prev)}
                          >
                            <User className="h-3.5 w-3.5" />
                            Mes assignements
                          </Button>
                        )}
                      </div>
                    </CardHeader>
                    <CardContent>
                      {executionResults.length > 0 ? (
                        <div className="space-y-3">
                          {executionResults
                            .filter(r => !assignedToMe || (currentUserName && r.assignedTo === currentUserName))
                            .map((result) => {
                            const tc = testCasesFromApi.find((t) => t.id === result.testCaseId)
                            const isExpanded = expandedResults.has(result.id)
                            const statusColor =
                              result.status === 'SUCCESS'
                                ? 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400'
                                : result.status === 'FAILURE'
                                  ? 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400'
                                  : 'bg-orange-100 text-orange-800 dark:bg-orange-950 dark:text-orange-400'

                            let methods: SurefireMethodResult[] = []
                            if (result.testMethodResults) {
                              try { methods = JSON.parse(result.testMethodResults) } catch { /* ignore */ }
                            }

                            const durationLabel = result.durationMs != null
                              ? result.durationMs < 1000
                                ? `${result.durationMs}ms`
                                : `${(result.durationMs / 1000).toFixed(1)}s`
                              : '—'

                            const isHighlighted = highlightResultId && result.id === Number(highlightResultId)

                            return (
                              <div
                                key={result.id}
                                ref={isHighlighted ? highlightRef : undefined}
                                className={`rounded-lg border bg-card overflow-hidden ${isHighlighted ? 'ring-2 ring-primary border-primary' : 'border-border'}`}
                              >
                                {/* Header row */}
                                <div
                                  className="flex items-center justify-between px-4 py-3 cursor-pointer hover:bg-muted/40"
                                  onClick={() => setExpandedResults((prev) => {
                                    const next = new Set(prev)
                                    if (next.has(result.id)) next.delete(result.id)
                                    else next.add(result.id)
                                    return next
                                  })}
                                >
                                  <div className="flex items-center gap-3 min-w-0">
                                    <Badge variant="outline" className={`shrink-0 ${statusColor}`}>
                                      {result.status}
                                    </Badge>
                                    <div className="min-w-0">
                                      <p className="font-medium text-sm text-foreground truncate">
                                        {tc?.title ?? `Test #${result.testCaseId}`}
                                      </p>
                                      {result.errorMessage && (
                                        <p className="text-xs text-destructive truncate max-w-xs">
                                          {result.errorMessage}
                                        </p>
                                      )}
                                    </div>
                                  </div>
                                  <div className="flex items-center gap-3 shrink-0 text-xs text-muted-foreground">
                                    {result.assignedTo && (
                                      <span className="flex items-center gap-1 text-foreground">
                                        <UserPlus className="h-3 w-3" />{result.assignedTo}
                                      </span>
                                    )}
                                    {(result.status === 'FAILURE' || result.status === 'ERROR') && (
                                      <Button
                                        variant="ghost"
                                        size="sm"
                                        className="h-6 px-2 text-xs"
                                        onClick={(e) => { e.stopPropagation(); setAssignTarget(result.id) }}
                                      >
                                        <UserPlus className="h-3 w-3 mr-1" />Assigner
                                      </Button>
                                    )}
                                    {methods.length > 0 && (
                                      <span className="font-medium">
                                        {methods.filter(m => m.status === 'PASS').length}/{methods.length} passed
                                      </span>
                                    )}
                                    <span>{durationLabel}</span>
                                    <Badge variant="outline" className="font-mono text-xs">
                                      {tc?.type ?? result.testType ?? '—'}
                                    </Badge>
                                    <span className="text-muted-foreground">{isExpanded ? '▲' : '▼'}</span>
                                  </div>
                                </div>

                                {/* Expanded details */}
                                {isExpanded && (
                                  <div className="border-t border-border px-4 py-3 space-y-4 bg-muted/20">

                                    {/* Method-level results (Surefire) */}
                                    {methods.length > 0 && (
                                      <div>
                                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-2">
                                          Méthodes ({methods.length})
                                        </p>
                                        <div className="rounded-md border border-border overflow-hidden">
                                          <Table>
                                            <TableHeader>
                                              <TableRow>
                                                <TableHead>Méthode</TableHead>
                                                <TableHead className="w-20">Statut</TableHead>
                                                <TableHead className="w-24">Durée</TableHead>
                                                <TableHead>Message</TableHead>
                                              </TableRow>
                                            </TableHeader>
                                            <TableBody>
                                              {methods.map((m, idx) => (
                                                <TableRow key={idx}>
                                                  <TableCell className="font-mono text-xs">{m.method}</TableCell>
                                                  <TableCell>
                                                    <Badge
                                                      variant="outline"
                                                      className={
                                                        m.status === 'PASS'
                                                          ? 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400'
                                                          : m.status === 'FAIL'
                                                            ? 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400'
                                                            : 'bg-orange-100 text-orange-800'
                                                      }
                                                    >
                                                      {m.status}
                                                    </Badge>
                                                  </TableCell>
                                                  <TableCell className="text-xs text-muted-foreground">
                                                    {m.durationMs < 1000 ? `${m.durationMs}ms` : `${(m.durationMs / 1000).toFixed(1)}s`}
                                                  </TableCell>
                                                  <TableCell className="text-xs text-muted-foreground max-w-xs truncate">
                                                    {m.message ?? '—'}
                                                  </TableCell>
                                                </TableRow>
                                              ))}
                                            </TableBody>
                                          </Table>
                                        </div>
                                      </div>
                                    )}

                                    {/* AI Analysis */}
                                    {result.aiAnalysis && (
                                      <div>
                                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-2">
                                          Analyse IA
                                        </p>
                                        <div className="rounded-md bg-card border border-border p-3 text-xs text-foreground whitespace-pre-wrap max-h-48 overflow-y-auto">
                                          {result.aiAnalysis}
                                        </div>
                                      </div>
                                    )}

                                    {/* Error + Logs */}
                                    {result.errorMessage && (
                                      <div>
                                        <p className="text-xs font-semibold text-destructive uppercase tracking-wide mb-1">Erreur</p>
                                        <p className="text-xs text-destructive">{result.errorMessage}</p>
                                      </div>
                                    )}
                                  </div>
                                )}
                              </div>
                            )
                          })}
                        </div>
                      ) : testCasesLoading ? (
                        <p className="text-sm text-muted-foreground">Chargement des résultats…</p>
                      ) : testCasesFromApi.length > 0 ? (
                        /* No execution results yet — show test cases with status */
                        <div className="rounded-md border border-border overflow-hidden">
                          <Table>
                            <TableHeader>
                              <TableRow>
                                <TableHead className="w-12"></TableHead>
                                <TableHead>Cas de test</TableHead>
                                <TableHead className="w-32">Type</TableHead>
                                <TableHead className="w-24">Statut</TableHead>
                                <TableHead className="w-20"></TableHead>
                              </TableRow>
                            </TableHeader>
                            <TableBody>
                              {testCasesFromApi.map((tc) => {
                                const testIdStr = String(tc.id)
                                const checked = selectedTestIds.includes(testIdStr)
                                const isRunning = campaign?.status === 'Running'
                                return (
                                  <TableRow key={tc.id}>
                                    <TableCell>
                                      <Checkbox
                                        checked={checked}
                                        onCheckedChange={(next) => {
                                          const shouldCheck = next === true
                                          setSelectedTestIds((prev) => {
                                            const has = prev.includes(testIdStr)
                                            if (shouldCheck && !has) return [...prev, testIdStr]
                                            if (!shouldCheck && has) return prev.filter((x) => x !== testIdStr)
                                            return prev
                                          })
                                        }}
                                        aria-label={`Select ${tc.title}`}
                                      />
                                    </TableCell>
                                    <TableCell>
                                      <div className="flex flex-col">
                                        <span className="font-medium text-foreground">{tc.title}</span>
                                        <span className="text-xs text-muted-foreground">{tc.scriptPath}</span>
                                      </div>
                                    </TableCell>
                                    <TableCell>
                                      <Badge variant="outline" className="font-mono text-xs">{tc.type || 'UNKNOWN'}</Badge>
                                    </TableCell>
                                    <TableCell>
                                      <Badge variant={tc.executionStatus === 'FINISHED' ? 'default' : tc.executionStatus === 'ERROR' ? 'destructive' : 'secondary'}>
                                        {tc.executionStatus || 'Not run'}
                                      </Badge>
                                    </TableCell>
                                    <TableCell>
                                      <Button
                                        variant="ghost"
                                        size="sm"
                                        className="h-7 text-xs text-destructive hover:text-destructive"
                                        disabled={isRunning || removingTestId === tc.id}
                                        onClick={() => handleRemoveTestCase(tc.id)}
                                      >
                                        {removingTestId === tc.id ? '...' : 'Retirer'}
                                      </Button>
                                    </TableCell>
                                  </TableRow>
                                )
                              })}
                            </TableBody>
                          </Table>
                        </div>
                      ) : (
                        <p className="text-sm text-muted-foreground">Aucun résultat pour l'instant. Lancez la campagne pour voir les résultats détaillés.</p>
                      )}

                      {/* ── Add test cases section ── */}
                      {campaign && campaign.status !== 'Running' && (
                        <div className="mt-6 pt-4 border-t border-border">
                          <div className="flex items-center justify-between mb-3">
                            <h3 className="text-sm font-semibold text-foreground">Ajouter des tests</h3>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={loadAvailableTestCases}
                              disabled={availableLoading}
                            >
                              {availableLoading ? 'Chargement...' : 'Rafraîchir'}
                            </Button>
                          </div>

                          {availableTestCases.length === 0 && !availableLoading ? (
                            <p className="text-xs text-muted-foreground">
                              {availableTestCases.length === 0
                                ? 'Tous les tests du projet sont déjà dans cette campagne. Cliquez Rafraîchir pour vérifier.'
                                : 'Aucun test disponible.'}
                            </p>
                          ) : availableLoading ? (
                            <p className="text-xs text-muted-foreground">Chargement des tests disponibles...</p>
                          ) : (
                            <>
                              <div className="rounded-md border border-border overflow-hidden max-h-60 overflow-y-auto">
                                <Table>
                                  <TableHeader>
                                    <TableRow>
                                      <TableHead className="w-12"></TableHead>
                                      <TableHead>Cas de test</TableHead>
                                      <TableHead className="w-32">Type</TableHead>
                                      <TableHead className="w-24">Suite</TableHead>
                                    </TableRow>
                                  </TableHeader>
                                  <TableBody>
                                    {availableTestCases.map((tc) => {
                                      const idStr = String(tc.id)
                                      const checked = selectedAvailableIds.includes(idStr)
                                      return (
                                        <TableRow key={tc.id}>
                                          <TableCell>
                                            <Checkbox
                                              checked={checked}
                                              onCheckedChange={(next) => {
                                                setSelectedAvailableIds((prev) =>
                                                  next === true
                                                    ? [...prev, idStr]
                                                    : prev.filter((x) => x !== idStr)
                                                )
                                              }}
                                            />
                                          </TableCell>
                                          <TableCell>
                                            <span className="font-medium text-sm">{tc.title}</span>
                                          </TableCell>
                                          <TableCell>
                                            <Badge variant="outline" className="font-mono text-xs">{tc.type || '—'}</Badge>
                                          </TableCell>
                                          <TableCell className="text-xs text-muted-foreground">
                                            {(tc as any).suiteName ?? '—'}
                                          </TableCell>
                                        </TableRow>
                                      )
                                    })}
                                  </TableBody>
                                </Table>
                              </div>
                              <Button
                                className="mt-2 gap-2"
                                size="sm"
                                disabled={selectedAvailableIds.length === 0 || addingTests}
                                onClick={handleAddTestCases}
                              >
                                {addingTests ? 'Ajout en cours...' : `Ajouter ${selectedAvailableIds.length} test(s)`}
                              </Button>
                            </>
                          )}
                        </div>
                      )}
                    </CardContent>
                  </Card>

                  {/* Endpoints and discovery removed — feature deprecated */}
                </div>

                <div className="space-y-6">
                  <Card>
                    <CardHeader>
                      <CardTitle>Détails</CardTitle>
                      <CardDescription>Données de la campagne en un coup d'œil.</CardDescription>
                    </CardHeader>
                    <CardContent>
                      <div className="rounded-2xl border border-border/70 overflow-hidden bg-card/60">
                        <Table>
                          <TableBody>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Projet</TableCell>
                              <TableCell className="text-right font-medium">
                                {projectName ?? (campaign.projectId ? `Projet #${campaign.projectId}` : '—')}
                              </TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Environnement</TableCell>
                              <TableCell className="text-right font-medium">{campaign.environment}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Propriétaire</TableCell>
                              <TableCell className="text-right">
                                <span className="inline-flex items-center justify-end gap-2">
                                  {ownerMember?.imageUrl ? (
                                    <img src={ownerMember.imageUrl} alt="" className="h-5 w-5 rounded-full object-cover" />
                                  ) : ownerMember ? (
                                    <span className="flex h-5 w-5 items-center justify-center rounded-full bg-gradient-to-br from-primary to-accent text-[9px] font-bold text-primary-foreground">
                                      {memberInitials(ownerMember)}
                                    </span>
                                  ) : null}
                                  <span className="font-medium">{membersLoading ? '…' : ownerName}</span>
                                </span>
                              </TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Statut</TableCell>
                              <TableCell className="text-right">
                                <Badge variant="outline" className={statusStyle[campaign.status]}>
                                  {campaign.status}
                                </Badge>
                              </TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Branche</TableCell>
                              <TableCell className="text-right font-medium">{campaign.branch}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Version de l'app</TableCell>
                              <TableCell className="text-right font-medium">{campaign.appVersion}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Mode de déclenchement</TableCell>
                              <TableCell className="text-right font-medium">{campaign.triggerMode}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Démarré le</TableCell>
                              <TableCell className="text-right font-medium">{campaign.startedAt ? formatWhen(campaign.startedAt) : '—'}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Terminé le</TableCell>
                              <TableCell className="text-right font-medium">{campaign.finishedAt ? formatWhen(campaign.finishedAt) : '—'}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">ID campagne</TableCell>
                              <TableCell className="text-right font-mono text-xs text-muted-foreground">#{campaign.id}</TableCell>
                            </TableRow>
                          </TableBody>
                        </Table>
                      </div>
                    </CardContent>
                  </Card>

                  <Card>
                    <CardHeader>
                      <CardTitle className="flex items-center gap-2">
                        <Users className="h-4 w-4" />
                        Project members
                      </CardTitle>
                      <CardDescription>
                        Only the project owner can create campaigns. Here is the project team.
                      </CardDescription>
                    </CardHeader>
                    <CardContent>
                      {membersLoading ? (
                        <div className="flex items-center justify-center py-6">
                          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                        </div>
                      ) : members.length === 0 ? (
                        <p className="text-sm text-muted-foreground">No members found for this project.</p>
                      ) : (
                        <div className="space-y-1.5">
                          {[...members]
                            .sort((a, b) => (a.userId === ownerId ? -1 : b.userId === ownerId ? 1 : 0))
                            .map((m) => {
                              const isOwner = m.userId === ownerId
                              return (
                                <div
                                  key={m.userId}
                                  className="flex items-center gap-3 rounded-lg px-2 py-2 hover:bg-muted/50"
                                >
                                  {m.imageUrl ? (
                                    <img src={m.imageUrl} alt="" className="h-8 w-8 rounded-full object-cover" />
                                  ) : (
                                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-primary to-accent">
                                      <span className="text-xs font-bold text-primary-foreground">{memberInitials(m)}</span>
                                    </div>
                                  )}
                                  <div className="min-w-0 flex-1">
                                    <p className="truncate text-sm font-medium text-foreground">{memberDisplayName(m)}</p>
                                    {m.email && <p className="truncate text-xs text-muted-foreground">{m.email}</p>}
                                  </div>
                                  {isOwner && (
                                    <Badge variant="outline" className="gap-1 border-amber-300 bg-amber-50 text-amber-700 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-400">
                                      <Crown className="h-3 w-3" />
                                      Owner
                                    </Badge>
                                  )}
                                </div>
                              )
                            })}
                        </div>
                      )}
                    </CardContent>
                  </Card>

                  <Card>
                    <CardHeader>
                      <CardTitle>Dernier résultat d'exécution</CardTitle>
                      <CardDescription>Résultat renvoyé par le backend ms-execution.</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-3">
                      {latestExecution ? (
                        <>
                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <div className="rounded-lg border border-border bg-card p-3">
                              <p className="text-xs uppercase tracking-wide text-muted-foreground">ID exécution</p>
                              <p className="mt-1 text-sm font-medium text-foreground">#{latestExecution.id}</p>
                            </div>
                            <div className="rounded-lg border border-border bg-card p-3">
                              <p className="text-xs uppercase tracking-wide text-muted-foreground">Statut</p>
                              <p className="mt-1 text-sm font-medium text-foreground">{latestExecution.status}</p>
                            </div>
                            <div className="rounded-lg border border-border bg-card p-3">
                              <p className="text-xs uppercase tracking-wide text-muted-foreground">Type d'exécution</p>
                              <p className="mt-1 text-sm font-medium text-foreground">{latestExecution.executionType}</p>
                            </div>
                            <div className="rounded-lg border border-border bg-card p-3">
                              <p className="text-xs uppercase tracking-wide text-muted-foreground">ID campagne</p>
                              <p className="mt-1 text-sm font-medium text-foreground">{latestExecution.campaignId}</p>
                            </div>
                          </div>

                          <div className="rounded-lg border border-border bg-card p-3">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Date d'exécution</p>
                            <p className="mt-1 text-sm font-medium text-foreground">{formatWhen(latestExecution.executionDate)}</p>
                            <p className="mt-2 text-xs text-muted-foreground">
                              Numéro d'exécution : {latestExecution.executionNumber ?? '—'}
                            </p>
                          </div>
                        </>
                      ) : (
                        <p className="text-sm text-muted-foreground">Aucun résultat d'exécution renvoyé pour l'instant.</p>
                      )}
                    </CardContent>
                  </Card>

                  <Card>
                    <CardHeader>
                      <CardTitle>Santé de l'exécution</CardTitle>
                      <CardDescription>Indicateurs de surveillance simples.</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <div className="flex items-start gap-3">
                        <div className="w-10 h-10 rounded-lg bg-secondary/80 flex items-center justify-center text-primary shadow-sm">
                          <ShieldCheck className="h-5 w-5" />
                        </div>
                        <div className="flex-1">
                          <p className="text-sm font-medium text-foreground">Seuil de réussite</p>
                          <p className="text-sm text-muted-foreground">Taux de réussite cible : 95 %</p>
                        </div>
                      </div>

                      <div>
                        <div className="flex items-center justify-between mb-2">
                          <span className="text-sm text-muted-foreground">Actuel</span>
                          <span className="text-sm font-semibold text-foreground">{formatPercent(passRate)}</span>
                        </div>
                        <Progress value={passRate} className="h-2" />
                      </div>
                    </CardContent>
                  </Card>
                </div>
              </div>
            </>
          )}
        </div>

        {/* Discovery dialog and schema viewer removed with endpoints feature */}

        {/* ── Assign Dialog for test errors ── */}
        {resolvedProjectId && (
          <AssignDialog
            open={assignTarget !== null}
            onOpenChange={(open) => { if (!open) setAssignTarget(null) }}
            projectId={String(resolvedProjectId)}
            onAssign={async (member) => {
              if (assignTarget === null) return
              const ok = await assignExecutionResult(assignTarget, member)
              if (ok) {
                setExecutionResults(prev =>
                  prev.map(r => r.id === assignTarget ? { ...r, assignedTo: member.name } : r)
                )
              }
              setAssignTarget(null)
            }}
          />
        )}

        {/* ── Run Mode Dialog ── */}
        <Dialog open={showRunDialog} onOpenChange={setShowRunDialog}>
          <DialogContent className="sm:max-w-lg">
            <DialogHeader>
              <DialogTitle>Lancer la campagne</DialogTitle>
              <DialogDescription>
                Sélectionnez les tests à exécuter, ou lancez toute la campagne.
              </DialogDescription>
            </DialogHeader>

            {testCasesFromApi.length === 0 ? (
              <div className="py-8 text-center text-sm text-muted-foreground">
                Cette campagne ne contient aucun cas de test. Ajoutez-en depuis la section « Ajouter des tests ».
              </div>
            ) : (
              <div className="space-y-3 py-1">
                {/* Toolbar : compteur + tout sélectionner */}
                {(() => {
                  const allIds = testCasesFromApi.map((tc) => String(tc.id))
                  const allSelected = allIds.length > 0 && allIds.every((id) => selectedTestIds.includes(id))
                  const someSelected = selectedTestIds.length > 0 && !allSelected
                  return (
                    <div className="flex items-center justify-between rounded-lg border border-border bg-muted/30 px-3 py-2">
                      <label className="flex cursor-pointer items-center gap-2 text-sm font-medium">
                        <Checkbox
                          checked={allSelected ? true : someSelected ? 'indeterminate' : false}
                          onCheckedChange={(next) => setSelectedTestIds(next === true ? allIds : [])}
                          aria-label="Tout sélectionner"
                        />
                        {allSelected ? 'Tout désélectionner' : 'Tout sélectionner'}
                      </label>
                      <span className="text-xs text-muted-foreground">
                        <strong className="text-foreground">{selectedTestIds.length}</strong> / {testCasesFromApi.length} sélectionné(s)
                      </span>
                    </div>
                  )
                })()}

                {/* Liste cochable des tests */}
                <div className="max-h-72 space-y-1.5 overflow-y-auto pr-1">
                  {testCasesFromApi.map((tc) => {
                    const idStr = String(tc.id)
                    const checked = selectedTestIds.includes(idStr)
                    const statusLabel = tc.executionStatus === 'FINISHED'
                      ? 'Passed'
                      : tc.executionStatus === 'ERROR'
                        ? 'Failed'
                        : tc.executionStatus ?? 'Not run'
                    const statusClass = tc.executionStatus === 'FINISHED'
                      ? 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400'
                      : tc.executionStatus === 'ERROR'
                        ? 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400'
                        : 'bg-muted text-muted-foreground'
                    return (
                      <label
                        key={tc.id}
                        className={`flex cursor-pointer items-center gap-3 rounded-lg border px-3 py-2 transition-colors ${
                          checked ? 'border-primary/40 bg-primary/5' : 'border-border hover:bg-muted/40'
                        }`}
                      >
                        <Checkbox
                          checked={checked}
                          onCheckedChange={(next) =>
                            setSelectedTestIds((prev) =>
                              next === true ? [...prev, idStr] : prev.filter((x) => x !== idStr),
                            )
                          }
                          aria-label={`Sélectionner ${tc.title}`}
                        />
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2">
                            <span className="truncate text-sm font-medium text-foreground">{tc.title}</span>
                            {tc.flaky && (
                              <Badge variant="outline" className="shrink-0 border-amber-300 bg-amber-50 px-1.5 py-0 text-[10px] text-amber-700 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-400">
                                flaky
                              </Badge>
                            )}
                            {tc.active === false && (
                              <Badge variant="outline" className="shrink-0 px-1.5 py-0 text-[10px] text-muted-foreground">
                                inactif
                              </Badge>
                            )}
                          </div>
                          {tc.scriptPath && (
                            <p className="truncate text-xs text-muted-foreground">{tc.scriptPath}</p>
                          )}
                        </div>
                        <Badge variant="outline" className="shrink-0 font-mono text-[10px]">
                          {tc.type ?? '—'}
                        </Badge>
                        <Badge variant="outline" className={`shrink-0 text-[10px] ${statusClass}`}>
                          {statusLabel}
                        </Badge>
                      </label>
                    )
                  })}
                </div>
              </div>
            )}

            <DialogFooter className="flex-col gap-2 sm:flex-row sm:justify-between">
              <Button
                variant="outline"
                className="gap-2"
                onClick={() => runCampaignWithMode('ALL')}
                disabled={runSubmitting || testCasesFromApi.length === 0}
              >
                <Play size={16} />
                Tout exécuter ({testCasesFromApi.length})
              </Button>
              <Button
                className="gap-2"
                onClick={() => runCampaignWithMode('SELECTED')}
                disabled={runSubmitting || selectedTestIds.length === 0}
              >
                {runSubmitting ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
                Exécuter la sélection ({selectedTestIds.length})
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </main>
    </div>
  )
}

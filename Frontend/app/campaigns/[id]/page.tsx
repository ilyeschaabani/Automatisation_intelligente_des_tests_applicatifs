'use client'

import Link from 'next/link'
import { useParams, useSearchParams } from 'next/navigation'
import { useEffect, useMemo, useRef, useState } from 'react'

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
  Play,
  ShieldCheck,
} from 'lucide-react'

import {
  createCampaignExecution,
  type CampaignExecution,
} from '@/lib/campaign-executions'

import {
  getCampaign,
  getProjects,
  listCampaigns,
  listExecutions,
  startCampaignRun,
  continueCampaignRun,
  type CampaignRunContinueRequest,
  type CampaignRunResponse,
  type EditableFileDto,
  type Project,
  type ExecutionStatus,
  type TestCampaignDto,
  type TestExecutionDto,
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
  'Cloning repository',
  'Preparing campaign context',
  'Running Maven tests',
  'Saving execution result',
] as const

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

  const isExecutionRunning =
    runSubmitting || latestExecution?.status === 'RUNNING' || latestExecution?.status === 'QUEUED'

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
          const backendStep = stepIndexFromValue(data.currentStep)

          if (status === 'PENDING') {
            setRunStepIndex(0)
            setRunProgressValue(Math.max(0, backendProgress))
            return
          }

          if (status === 'RUNNING') {
            setRunStepIndex(Math.min(backendStep, executionSteps.length - 1))
            setRunProgressValue(Math.max(0, Math.min(99, backendProgress || 10)))
            return
          }

          if (status === 'FINISHED' || status === 'FINISHED_WITH_ERRORS') {
            setRunStepIndex(executionSteps.length - 1)
            setRunProgressValue(100)
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
    }
  }, [isExecutionRunning, campaignNumericId])

  // Reset progress only when starting a new execution
  useEffect(() => {
    if (runSubmitting) {
      setRunProgressValue(0)
      setRunStepIndex(0)
    }
  }, [runSubmitting])

  const passRate = useMemo(() => {
    if (!campaign) return 0
    return computePassRate(campaign.passed, campaign.tests)
  }, [campaign])

  const riskScore = useMemo(() => {
    if (!campaign) return 0
    const failureRate = campaign.tests ? (campaign.failed / campaign.tests) * 100 : 0
    // Simple UI-only signal: higher failures => higher risk.
    return clampPercent(failureRate * 2)
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

  const [selectedTestIds, setSelectedTestIds] = useState<string[]>([])
  const [storedRuns, setStoredRuns] = useState<CampaignExecution[]>([])
  

  useEffect(() => {
    if (!campaignNumericId) {
      setBackendExecutions([])
      setLatestExecution(null)
      setStoredRuns([])
      setSelectedTestIds([])
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
    
    // Initialize progress from response campaign data if available
    if (response.campaign) {
      const backendProgress = Number(response.campaign.progress ?? 0)
      const backendStep = response.campaign.currentStep ?? ''
      setRunProgressValue(Math.max(0, backendProgress))
      
      const stepIndex = executionSteps.findIndex((step) => 
        step.toLowerCase() === String(backendStep ?? '').trim().toLowerCase()
      )
      if (stepIndex >= 0) {
        setRunStepIndex(stepIndex)
      }
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

    setRunSubmitting(true)
    try {
      const branchValue = String(campaign?.branch ?? '').trim()
      const response = await startCampaignRun(campaignNumericId, {
        branch: branchValue || undefined,
      })
      await applyRunResponse(response)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to start campaign run'
      toast({ title: 'Run failed', description: message, variant: 'destructive' })
    } finally {
      setRunSubmitting(false)
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

    setRunSubmitting(true)
    try {
      const response = await continueCampaignRun(campaignNumericId, payload)
      await applyRunResponse(response)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to continue campaign run'
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
                  <BreadcrumbPage>{campaign?.name ?? 'Campaign details'}</BreadcrumbPage>
                </BreadcrumbItem>
              </BreadcrumbList>
            </Breadcrumb>

            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0">
                <h1 className="text-3xl font-bold text-foreground truncate">
                  {campaign?.name ?? 'Campaign not found'}
                </h1>
                <p className="text-muted-foreground mt-1">
                  {campaign
                    ? 'Campaign overview, quality signals, and latest runs.'
                    : 'This campaign id does not match any campaign in the UI seed.'}
                </p>
              </div>

              <Button asChild variant="outline" className="gap-2 shrink-0">
                <Link href="/campaigns">
                  <ArrowLeft size={18} />
                  Back
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
                            Project #{campaign.projectId}
                          </Badge>
                        ) : null}
                      </div>

                      <div>
                        <h1 className="text-3xl lg:text-4xl font-semibold tracking-tight text-foreground truncate">
                          {campaign.name}
                        </h1>
                        <p className="text-sm lg:text-base text-muted-foreground mt-2 max-w-3xl">
                          Campaign overview, environment setup, and run activity from the new backend model.
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
                    </div>

                    <div className="grid gap-3 sm:grid-cols-2 lg:w-[360px]">
                      <div className="rounded-2xl border border-border/70 bg-card/80 p-4 backdrop-blur">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Last run</p>
                        <p className="mt-2 text-sm font-medium text-foreground">{campaign.lastRun}</p>
                      </div>
                      <div className="rounded-2xl border border-border/70 bg-card/80 p-4 backdrop-blur">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Branch</p>
                        <p className="mt-2 text-sm font-medium text-foreground truncate">{campaign.branch}</p>
                      </div>
                      <div className="rounded-2xl border border-border/70 bg-card/80 p-4 backdrop-blur">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Environment</p>
                        <p className="mt-2 text-sm font-medium text-foreground truncate">{campaign.environment}</p>
                      </div>
                      <div className="rounded-2xl border border-border/70 bg-card/80 p-4 backdrop-blur">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Version</p>
                        <p className="mt-2 text-sm font-medium text-foreground truncate">{campaign.appVersion}</p>
                      </div>
                    </div>
                  </div>

                  <div className="mt-8 flex flex-col sm:flex-row sm:items-center gap-3">
                    <Button asChild variant="outline" size="sm" className="gap-2">
                      <Link href={`/executions?campaignId=${campaign.id}`}>
                        <Clock className="h-4 w-4" />
                        View executions
                      </Link>
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      className="gap-2"
                      onClick={() => void runCampaign()}
                      disabled={runSubmitting}
                    >
                      <Play className="h-4 w-4" />
                      {runSubmitting ? 'Running...' : 'Run campaign'}
                    </Button>
                  </div>

                  {isExecutionRunning ? (
                    <Card className="mt-6 border-primary/20 bg-primary/5">
                      <CardContent className="space-y-4 p-4 sm:p-5">
                        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                          <div>
                            <p className="text-sm font-semibold text-foreground">ms-execution is running the campaign</p>
                            <p className="text-sm text-muted-foreground">
                              {campaign.name} is being cloned, executed, and persisted in the backend.
                            </p>
                          </div>
                          <Badge variant="secondary" className="w-fit rounded-full">
                            {executionSteps[runStepIndex]}
                          </Badge>
                        </div>

                        <div className="space-y-2">
                          <div className="flex items-center justify-between text-xs text-muted-foreground">
                            <span>Loading progress</span>
                            <span>{formatPercent(runProgressValue)}</span>
                          </div>
                          <Progress value={runProgressValue} className="h-2.5" />
                        </div>

                        <div className="grid gap-2 text-sm text-muted-foreground sm:grid-cols-2">
                          {executionSteps.map((step, index) => (
                            <div
                              key={step}
                              className={
                                'rounded-lg border px-3 py-2 ' +
                                (index <= runStepIndex
                                  ? 'border-primary/20 bg-background text-foreground'
                                  : 'border-border bg-background/60')
                              }
                            >
                              {index + 1}. {step}
                            </div>
                          ))}
                        </div>
                      </CardContent>
                    </Card>
                  ) : null}

                  <Dialog open={runDialogOpen} onOpenChange={setRunDialogOpen}>
          <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>Review run artifacts</DialogTitle>
              <DialogDescription>
                Review the generated files, provide missing values, and continue the run.
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4">
              {runMissingDb ? (
                <div className="space-y-2">
                  <Label>Database type</Label>
                  <Select value={runDbValue} onValueChange={setRunDbValue}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select database" />
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
                Cancel
              </Button>
              <Button
                type="button"
                onClick={() => void submitRunInputs()}
                disabled={runSubmitting || (runMissingDb && String(runDbValue).trim().length === 0)}
              >
                {runSubmitting ? 'Submitting…' : 'Start run'}
              </Button>
            </DialogFooter>
          </DialogContent>
                  </Dialog>

                  <div className="mt-8 space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-muted-foreground">Execution progress</span>
                      <span className="text-sm font-semibold text-foreground">
                        {formatPercent(isExecutionRunning ? runProgressValue : campaign.progress)}
                      </span>
                    </div>
                    <Progress
                      value={clampPercent(isExecutionRunning ? runProgressValue : campaign.progress)}
                      className="h-2.5"
                    />
                  </div>
                </div>
              </Card>

              {/* Content grid */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2 space-y-6">
                  <Card>
                    <CardHeader>
                      <CardTitle>Overview</CardTitle>
                      <CardDescription>
                        Key metrics and quick signals for this campaign.
                      </CardDescription>
                    </CardHeader>
                    <CardContent>
                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                        <div className="rounded-lg border border-border bg-card p-4">
                          <div className="flex items-center justify-between">
                            <p className="text-sm text-muted-foreground">Total tests</p>
                            <Play className="h-4 w-4 text-muted-foreground" />
                          </div>
                          <p className="mt-2 text-2xl font-bold text-foreground">{campaign.tests}</p>
                        </div>
                        <div className="rounded-lg border border-border bg-card p-4">
                          <div className="flex items-center justify-between">
                            <p className="text-sm text-muted-foreground">Passed</p>
                            <CheckCircle2 className="h-4 w-4 text-green-600" />
                          </div>
                          <p className="mt-2 text-2xl font-bold text-foreground">{campaign.passed}</p>
                        </div>
                        <div className="rounded-lg border border-border bg-card p-4">
                          <div className="flex items-center justify-between">
                            <p className="text-sm text-muted-foreground">Failed</p>
                            <AlertCircle className="h-4 w-4 text-red-600" />
                          </div>
                          <p className="mt-2 text-2xl font-bold text-foreground">{campaign.failed}</p>
                        </div>
                      </div>

                      <Separator className="my-6" />

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                        <div>
                          <div className="flex items-center justify-between mb-2">
                            <span className="text-sm text-muted-foreground">Pass rate</span>
                            <span className="text-sm font-semibold text-foreground">{formatPercent(passRate)}</span>
                          </div>
                          <Progress value={passRate} className="h-2" />
                        </div>

                        <div>
                          <div className="flex items-center justify-between mb-2">
                            <span className="text-sm text-muted-foreground">Risk signal</span>
                            <span className="text-sm font-semibold text-foreground">{formatPercent(riskScore)}</span>
                          </div>
                          <Progress value={riskScore} className="h-2" />
                        </div>
                      </div>
                    </CardContent>
                  </Card>

                  <Card>
                    <CardHeader>
                      <CardTitle>Recent runs</CardTitle>
                      <CardDescription>Latest backend executions and local run history for this campaign.</CardDescription>
                    </CardHeader>
                    <CardContent>
                      <div className="rounded-md border border-border overflow-hidden">
                        <Table>
                          <TableHeader>
                            <TableRow>
                              <TableHead>Run</TableHead>
                              <TableHead>Status</TableHead>
                              <TableHead>Duration</TableHead>
                              <TableHead className="text-right">When</TableHead>
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
                      <CardTitle>Tests</CardTitle>
                      <CardDescription>Select specific tests to run from this campaign.</CardDescription>
                    </CardHeader>
                    <CardContent>
                      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-4">
                        <p className="text-sm text-muted-foreground">
                          Selected: <span className="font-medium text-foreground">{selectedTestIds.length}</span>
                        </p>
                        <Button
                          variant="outline"
                          className="gap-2"
                          onClick={runSelectedTests}
                          disabled={selectedTestIds.length === 0}
                        >
                          <Play className="h-4 w-4" />
                          Run selected tests
                        </Button>
                      </div>

                      <div className="rounded-md border border-border overflow-hidden">
                        <Table>
                          <TableHeader>
                            <TableRow>
                              <TableHead className="w-12"></TableHead>
                              <TableHead>Test</TableHead>
                              <TableHead className="w-32">Area</TableHead>
                              <TableHead className="w-24 text-right">Type</TableHead>
                            </TableRow>
                          </TableHeader>
                          <TableBody>
                            {tests.map((t) => {
                              const checked = selectedTestIds.includes(t.id)
                              return (
                                <TableRow key={t.id}>
                                  <TableCell>
                                    <Checkbox
                                      checked={checked}
                                      onCheckedChange={(next) => {
                                        const shouldCheck = next === true
                                        setSelectedTestIds((prev) => {
                                          const has = prev.includes(t.id)
                                          if (shouldCheck && !has) return [...prev, t.id]
                                          if (!shouldCheck && has) return prev.filter((x) => x !== t.id)
                                          return prev
                                        })
                                      }}
                                      aria-label={`Select ${t.id}`}
                                    />
                                  </TableCell>
                                  <TableCell>
                                    <div className="flex flex-col">
                                      <span className="font-medium text-foreground">{t.name}</span>
                                      <span className="text-xs text-muted-foreground">{t.id}</span>
                                    </div>
                                  </TableCell>
                                  <TableCell className="text-muted-foreground">{t.area}</TableCell>
                                  <TableCell className="text-right">
                                    <Badge variant="secondary">{t.kind}</Badge>
                                  </TableCell>
                                </TableRow>
                              )
                            })}
                          </TableBody>
                        </Table>
                      </div>
                    </CardContent>
                  </Card>

                  {/* Endpoints and discovery removed — feature deprecated */}
                </div>

                <div className="space-y-6">
                  <Card>
                    <CardHeader>
                      <CardTitle>Details</CardTitle>
                      <CardDescription>Backend campaign table data at a glance.</CardDescription>
                    </CardHeader>
                    <CardContent>
                      <div className="rounded-2xl border border-border/70 overflow-hidden bg-card/60">
                        <Table>
                          <TableBody>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Campaign ID</TableCell>
                              <TableCell className="text-right font-medium">{campaign.id}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Status</TableCell>
                              <TableCell className="text-right font-medium">{campaign.status}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Started at</TableCell>
                              <TableCell className="text-right font-medium">{campaign.startedAt ? formatWhen(campaign.startedAt) : '—'}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Finished at</TableCell>
                              <TableCell className="text-right font-medium">{campaign.finishedAt ? formatWhen(campaign.finishedAt) : '—'}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Environment ID</TableCell>
                              <TableCell className="text-right font-medium">{campaign.environmentId ?? '—'}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Project ID</TableCell>
                              <TableCell className="text-right font-medium">{campaign.projectId ?? '—'}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Environment</TableCell>
                              <TableCell className="text-right font-medium">{campaign.environment}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">App version</TableCell>
                              <TableCell className="text-right font-medium">{campaign.appVersion}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Trigger mode</TableCell>
                              <TableCell className="text-right font-medium">{campaign.triggerMode}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Branch</TableCell>
                              <TableCell className="text-right font-medium">{campaign.branch}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Owner</TableCell>
                              <TableCell className="text-right font-medium">{campaign.owner}</TableCell>
                            </TableRow>
                          </TableBody>
                        </Table>
                      </div>
                    </CardContent>
                  </Card>

                  <Card>
                    <CardHeader>
                      <CardTitle>Latest execution result</CardTitle>
                      <CardDescription>Result returned by the ms-execution backend.</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-3">
                      {latestExecution ? (
                        <>
                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <div className="rounded-lg border border-border bg-card p-3">
                              <p className="text-xs uppercase tracking-wide text-muted-foreground">Execution ID</p>
                              <p className="mt-1 text-sm font-medium text-foreground">#{latestExecution.id}</p>
                            </div>
                            <div className="rounded-lg border border-border bg-card p-3">
                              <p className="text-xs uppercase tracking-wide text-muted-foreground">Status</p>
                              <p className="mt-1 text-sm font-medium text-foreground">{latestExecution.status}</p>
                            </div>
                            <div className="rounded-lg border border-border bg-card p-3">
                              <p className="text-xs uppercase tracking-wide text-muted-foreground">Execution type</p>
                              <p className="mt-1 text-sm font-medium text-foreground">{latestExecution.executionType}</p>
                            </div>
                            <div className="rounded-lg border border-border bg-card p-3">
                              <p className="text-xs uppercase tracking-wide text-muted-foreground">Campaign ID</p>
                              <p className="mt-1 text-sm font-medium text-foreground">{latestExecution.campaignId}</p>
                            </div>
                          </div>

                          <div className="rounded-lg border border-border bg-card p-3">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Execution date</p>
                            <p className="mt-1 text-sm font-medium text-foreground">{formatWhen(latestExecution.executionDate)}</p>
                            <p className="mt-2 text-xs text-muted-foreground">
                              Execution number: {latestExecution.executionNumber ?? '—'}
                            </p>
                          </div>
                        </>
                      ) : (
                        <p className="text-sm text-muted-foreground">No backend execution result returned yet.</p>
                      )}
                    </CardContent>
                  </Card>

                  <Card>
                    <CardHeader>
                      <CardTitle>Execution health</CardTitle>
                      <CardDescription>Simple guardrails for visibility.</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <div className="flex items-start gap-3">
                        <div className="w-10 h-10 rounded-lg bg-secondary/80 flex items-center justify-center text-primary shadow-sm">
                          <ShieldCheck className="h-5 w-5" />
                        </div>
                        <div className="flex-1">
                          <p className="text-sm font-medium text-foreground">Success threshold</p>
                          <p className="text-sm text-muted-foreground">Target pass rate: 95%</p>
                        </div>
                      </div>

                      <div>
                        <div className="flex items-center justify-between mb-2">
                          <span className="text-sm text-muted-foreground">Current</span>
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
      </main>
    </div>
  )
}

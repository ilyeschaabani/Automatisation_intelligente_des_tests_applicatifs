'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useMemo, useRef, useState } from 'react'

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
  listCampaignExecutions,
  type CampaignExecution,
  type CampaignExecutionStatus,
} from '@/lib/campaign-executions'

import {
  getCampaign,
  getProjects,
  getCampaignEndpoints,
  getProjectEndpoints,
  listTestCases,
  type EndpointDto,
  type Project,
  type TestCampaignDto,
  type TestCaseDto,
} from '@/lib/api-client'

type CampaignType = 'Functional' | 'API' | 'Regression'
type CampaignStatus = 'Running' | 'Completed' | 'Failed' | 'Scheduled'

type Campaign = {
  id: string
  name: string
  type: CampaignType
  status: CampaignStatus
  progress: number
  tests: number
  passed: number
  failed: number
  lastRun: string
  environment: string
  owner: string
  schedule: string
  repository: string
  branch: string
  tags: string[]
  projectId?: number
}

type CampaignTest = {
  id: string
  name: string
  area: string
  kind: 'UI' | 'API' | 'DB'
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

function inferKindFromTestType(value: string | null | undefined): CampaignTest['kind'] {
  const v = String(value ?? '').toLowerCase()
  if (v.includes('api')) return 'API'
  if (v.includes('db') || v.includes('database') || v.includes('sql')) return 'DB'
  return 'UI'
}

function mapBackendCampaign(dto: TestCampaignDto): Campaign {
  const testsCount = Array.isArray(dto.testCaseIds) ? dto.testCaseIds.length : 0
  const lastRun = dto.executionEndDate ?? dto.executionStartDate ?? dto.endDate ?? dto.startDate
  return {
    id: String(dto.id),
    name: dto.name,
    type: 'Functional',
    status: mapBackendStatus(dto.status),
    progress: 0,
    tests: testsCount,
    passed: 0,
    failed: 0,
    lastRun: lastRun ? formatWhen(lastRun) : '—',
    environment: dto.environment ? String(dto.environment) : '—',
    owner: dto.createdBy ? String(dto.createdBy) : '—',
    schedule: dto.triggerType ? String(dto.triggerType) : '—',
    repository: '—',
    branch: '—',
    tags: [],
    projectId: dto.projectId,
  }
}

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
    owner: 'QA Platform',
    schedule: 'Manual / On demand',
    repository: 'banking-mobile-app',
    branch: 'release/v2.5',
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
    owner: 'API QA',
    schedule: 'Nightly 02:00',
    repository: 'payments-gateway',
    branch: 'main',
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
    owner: 'Release QA',
    schedule: 'Daily 02:00',
    repository: 'core-platform',
    branch: 'main',
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
    owner: 'Core QA',
    schedule: 'Nightly 01:30',
    repository: 'core-banking',
    branch: 'main',
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
    owner: 'Platform QA',
    schedule: 'On push',
    repository: 'auth-service',
    branch: 'develop',
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
    owner: 'Frontend QA',
    schedule: 'On PR merge',
    repository: 'design-system',
    branch: 'release/v3.0',
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
    owner: 'Data QA',
    schedule: 'Nightly 03:00',
    repository: 'data-layer',
    branch: 'main',
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
    owner: 'Security QA',
    schedule: 'Hourly',
    repository: 'security-scanners',
    branch: 'main',
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
  const id = Array.isArray(params?.id) ? params?.id[0] : params?.id
  const campaignNumericId = useMemo(() => {
    if (!id || !/^\d+$/.test(id)) return null
    return Number(id)
  }, [id])

  const campaigns = useMemo(() => toCampaigns(), [])
  const seedCampaign = useMemo(() => {
    if (!id) return null
    return campaigns.find((c) => c.id === id) ?? null
  }, [campaigns, id])

  const [remoteCampaign, setRemoteCampaign] = useState<Campaign | null>(null)
  const [remoteLoading, setRemoteLoading] = useState(false)
  const [remoteError, setRemoteError] = useState<string | null>(null)
  const [remoteTestCases, setRemoteTestCases] = useState<TestCaseDto[]>([])
  const [remoteTestCaseIds, setRemoteTestCaseIds] = useState<number[]>([])

  useEffect(() => {
    if (!id || !/^\d+$/.test(id)) {
      setRemoteCampaign(null)
      setRemoteError(null)
      setRemoteLoading(false)
      setRemoteTestCases([])
      setRemoteTestCaseIds([])
      return
    }

    let cancelled = false

    const run = async () => {
      setRemoteLoading(true)
      setRemoteError(null)
      try {
        const dto = await getCampaign(Number(id))
        if (cancelled) return
        setRemoteCampaign(mapBackendCampaign(dto))
        setRemoteTestCaseIds(Array.isArray(dto.testCaseIds) ? dto.testCaseIds : [])

        const cases = await listTestCases()
        if (cancelled) return
        setRemoteTestCases(Array.isArray(cases) ? cases : [])
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to load campaign'
        if (!cancelled) {
          setRemoteCampaign(null)
          setRemoteTestCases([])
          setRemoteTestCaseIds([])
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
  }, [id])

  const campaign = remoteCampaign ?? seedCampaign

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

    if (remoteCampaign) {
      const idSet = new Set(remoteTestCaseIds)
      const attached = remoteTestCases.filter((tc) => idSet.has(tc.id))
      return attached.map((tc) => ({
        id: String(tc.id),
        name: tc.name,
        area: String(tc.priority ?? '—'),
        kind: inferKindFromTestType(tc.testType),
      }))
    }

    return seedTestsFor(campaign)
  }, [campaign, remoteCampaign, remoteTestCaseIds, remoteTestCases])

  const [selectedTestIds, setSelectedTestIds] = useState<string[]>([])
  const [storedRuns, setStoredRuns] = useState<CampaignExecution[]>([])

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
  const [schemaDialog, setSchemaDialog] = useState<{
    open: boolean
    title: string
    schema: string | null
  }>({ open: false, title: '', schema: null })

  const pollTimeoutRef = useRef<number | null>(null)
  const pollAttemptRef = useRef(0)
  const pollInFlightRef = useRef(false)
  const [linkedProject, setLinkedProject] = useState<Project | null>(null)

  useEffect(() => {
    if (!campaign) {
      setStoredRuns([])
      setSelectedTestIds([])
      return
    }
    setStoredRuns(listCampaignExecutions(campaign.id))
    setSelectedTestIds([])
  }, [campaign])

  useEffect(() => {
    return () => {
      if (pollTimeoutRef.current) window.clearTimeout(pollTimeoutRef.current)
      pollTimeoutRef.current = null
      pollAttemptRef.current = 0
      pollInFlightRef.current = false
    }
  }, [])

  useEffect(() => {
    const projectId = campaign?.projectId
    if (!projectId) {
      setLinkedProject(null)
      return
    }

    let cancelled = false

    const run = async () => {
      try {
        const projects = await getProjects()
        if (cancelled) return
        const found = projects.find((p) => Number(p.id) === Number(projectId))
        setLinkedProject(found ?? null)
      } catch {
        if (!cancelled) setLinkedProject(null)
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [campaign?.projectId])

  const linkedDefaultBranchLabel = useMemo(() => {
    const v = String(linkedProject?.defaultBranch ?? '').trim()
    return v
  }, [linkedProject])

  const recentRuns = useMemo(() => {
    const dynamic = storedRuns.map((r) => {
      return {
        id: r.id,
        status: r.status as CampaignExecutionStatus,
        duration: r.duration ?? '—',
        when: formatWhen(r.createdAt),
      }
    })
    return [...dynamic, ...recentRunsSeed]
  }, [recentRunsSeed, storedRuns])

  const filteredEndpoints = useMemo(() => {
    const list = endpointsState.kind === 'done' ? endpointsState.endpoints : []
    const search = endpointSearch.trim().toLowerCase()
    return list.filter((e) => {
      if (endpointMethod !== 'ALL' && String(e.method).toUpperCase() !== endpointMethod) return false
      if (search) {
        const path = String(e.path ?? '').toLowerCase()
        if (!path.includes(search)) return false
      }
      return true
    })
  }, [endpointsState, endpointMethod, endpointSearch])

  const methodOptions = useMemo(() => {
    const list = endpointsState.kind === 'done' ? endpointsState.endpoints : []
    const methods = Array.from(new Set(list.map((e) => String(e.method ?? '').toUpperCase()).filter(Boolean))).sort()
    return ['ALL', ...methods]
  }, [endpointsState])

  const resolveEffectiveBranch = (explicit: string | undefined, projectDefault: string | null | undefined) => {
    const typed = String(explicit ?? '').trim()
    if (typed) return typed

    const fallback = String(projectDefault ?? '').trim()
    if (fallback) return fallback

    return 'main'
  }

  const loadExistingEndpoints = async () => {
    if (!campaignNumericId) return
    if (!campaign?.projectId) return

    try {
      setEndpointsState({ kind: 'loading' })
      const endpoints = await getCampaignEndpoints(campaignNumericId)
      const list = Array.isArray(endpoints) ? endpoints : []
      if (list.length > 0) {
        setEndpointsState({ kind: 'done', endpoints: list })
      } else {
        setEndpointsState({ kind: 'idle' })
      }
    } catch (err) {
      setEndpointsState({
        kind: 'error',
        message: err instanceof Error ? err.message : 'Failed to load endpoints',
      })
    }
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
    const projectId = campaign?.projectId
    if (!projectId) return
    if (!campaignNumericId) return

    clearPolling()

    const explicitBranch = String(options?.branch ?? endpointBranch).trim() || undefined
    // If user leaves branch empty, rely on backend fallback:
    // UI branch -> project.defaultBranch -> "main".
    const branch = explicitBranch && explicitBranch.trim() ? explicitBranch.trim() : undefined
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
        const endpoints = await getProjectEndpoints(projectId, branch)
        const list = Array.isArray(endpoints) ? endpoints : []

        if (list.length > 0) {
          clearPolling()
          setEndpointsState({ kind: 'done', endpoints: list })
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
    if (!campaignNumericId) return
    if (!campaign?.projectId) return
    if (endpointsState.kind !== 'idle') return

    void loadExistingEndpoints()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [campaignNumericId, campaign?.projectId])

  const runCampaign = () => {
    if (!campaign) return
    const created = createCampaignExecution({ campaignId: campaign.id, scope: 'CAMPAIGN' })
    setStoredRuns((prev) => [created, ...prev])
  }

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
              <Card className="mb-6 overflow-hidden">
                <div className="p-6 bg-gradient-to-r from-primary/10 via-transparent to-accent/10">
                  <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
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
                      {campaign.tags.map((t) => (
                        <Badge key={t} variant="secondary" className="rounded-full">
                          {t}
                        </Badge>
                      ))}
                    </div>

                    <div className="flex flex-col sm:flex-row sm:items-center gap-3">
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <Clock className="h-4 w-4" />
                        <span>Last run: {campaign.lastRun}</span>
                      </div>
                      {remoteCampaign ? (
                        <Button asChild variant="outline" size="sm">
                          <Link href={`/campaigns/${campaign.id}/edit`}>Edit</Link>
                        </Button>
                      ) : null}
                      <Button asChild variant="outline" size="sm">
                        <Link href={`/executions?campaignId=${campaign.id}`}>View executions</Link>
                      </Button>
                      <Button size="sm" className="gap-2" onClick={runCampaign}>
                        <Play className="h-4 w-4" />
                        Run campaign
                      </Button>
                    </div>
                  </div>

                  <div className="mt-6">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm text-muted-foreground">Execution progress</span>
                      <span className="text-sm font-semibold text-foreground">{formatPercent(campaign.progress)}</span>
                    </div>
                    <Progress value={clampPercent(campaign.progress)} className="h-3" />
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
                      <CardDescription>Latest activity for this campaign.</CardDescription>
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

                  <Card>
                    <CardHeader>
                      <div className="flex items-start justify-between gap-4">
                        <div className="min-w-0">
                          <CardTitle>Endpoints</CardTitle>
                          <CardDescription>
                            Discover and browse endpoints extracted from the campaign’s project repository.
                          </CardDescription>
                        </div>

                        <Button
                          size="sm"
                          onClick={() => void discoverEndpoints()}
                          disabled={!campaign?.projectId || endpointsState.kind === 'loading' || endpointsState.kind === 'running'}
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
                      {!campaign?.projectId ? (
                        <p className="text-sm text-muted-foreground">
                          This campaign is not linked to a project.
                        </p>
                      ) : null}

                      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                        <div className="space-y-2">
                          <Label htmlFor="campaign-endpoint-branch">Branch (optional)</Label>
                          <Input
                            id="campaign-endpoint-branch"
                            value={endpointBranch}
                            onChange={(e) => setEndpointBranch(e.target.value)}
                            placeholder={linkedDefaultBranchLabel ? `Fallback: ${linkedDefaultBranchLabel}` : 'Fallback: main'}
                            disabled={endpointsState.kind === 'loading' || endpointsState.kind === 'running'}
                          />
                        </div>

                        <div className="space-y-2">
                          <Label htmlFor="campaign-endpoint-search">Search by path</Label>
                          <Input
                            id="campaign-endpoint-search"
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
                      ) : endpointsState.kind === 'idle' && campaign?.projectId ? (
                        <p className="text-sm text-muted-foreground">
                          No endpoints loaded yet. Click “Discover endpoints” to start discovery.
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
                </div>

                <div className="space-y-6">
                  <Card>
                    <CardHeader>
                      <CardTitle>Configuration</CardTitle>
                      <CardDescription>Current campaign setup.</CardDescription>
                    </CardHeader>
                    <CardContent>
                      <div className="rounded-md border border-border overflow-hidden">
                        <Table>
                          <TableBody>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Environment</TableCell>
                              <TableCell className="text-right font-medium">{campaign.environment}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Owner</TableCell>
                              <TableCell className="text-right font-medium">{campaign.owner}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Schedule</TableCell>
                              <TableCell className="text-right font-medium">{campaign.schedule}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Repository</TableCell>
                              <TableCell className="text-right font-medium">{campaign.repository}</TableCell>
                            </TableRow>
                            <TableRow>
                              <TableCell className="text-muted-foreground">Branch</TableCell>
                              <TableCell className="text-right font-medium">{campaign.branch}</TableCell>
                            </TableRow>
                          </TableBody>
                        </Table>
                      </div>
                    </CardContent>
                  </Card>

                  <Card>
                    <CardHeader>
                      <CardTitle>Quality gate</CardTitle>
                      <CardDescription>Simple guardrails for visibility.</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <div className="flex items-start gap-3">
                        <div className="w-10 h-10 bg-secondary rounded-lg flex items-center justify-center text-primary">
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
      </main>
    </div>
  )
}

import { getAccessToken as getStoredAccessToken } from '@/lib/auth-storage'

export type ProjectType = 'WEB' | 'MOBILE' | 'API' | 'DESKTOP' | 'OTHER'
export type SourceType = 'GIT' | 'LOCAL'

export type Project = {
  id: number
  name: string
  projectType: ProjectType
  sourceType: SourceType
  repositoryUrl: string | null
  defaultBranch?: string | null
  deployed: boolean
  createdAt?: string | null
}

export type CreateProjectPayload = {
  name: string
  projectType: ProjectType
  sourceType: SourceType
  repositoryUrl?: string | null
  defaultBranch?: string | null
  deployed: boolean
}

export type SessionStatus = 'OPEN' | 'IN_PROGRESS' | 'CLOSED'

export interface TestSession {
  id: number
  startDate: string
  endDate: string | null
  environment: string | null
  status: SessionStatus
  triggerType: string | null
  project?: { id: number }
  campaign?: { id: number } | null
}

export type ExecutionType = 'INITIAL' | 'RETEST'
export type ExecutionStatus = 'QUEUED' | 'RUNNING' | 'FINISHED' | 'ERROR'

export interface TestExecutionDto {
  id: number
  executionNumber: number | null
  executionDate: string
  executionType: ExecutionType
  status: ExecutionStatus
  campaignId: number
}

export type TestExecution = TestExecutionDto

export interface TestExecutionInput {
  executionNumber?: number | null
  executionDate?: string
  executionType: ExecutionType
  status: ExecutionStatus
}

export type EndpointDto = {
  id: number
  method: string
  path: string
  summary: string | null
  source: string | null
  confidence: number
  requestSchema: string | null
}

// Ms-execution backend types
export type SurefireMethodResult = {
  method: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  durationMs: number
  message?: string
  stacktrace?: string
}

export type ExecutionResultBackendDto = {
  id: number
  campaignId: number
  testCaseId: number | null
  testType?: string | null
  status: string // SUCCESS, FAILURE, ERROR
  durationMs: number | null
  errorMessage: string | null
  logs: string | null
  aiAnalysis?: string | null
  uxAnalysis?: string | null
  screenshotUrl: string | null
  executedAt: string
  testMethodResults?: string | null // JSON array of SurefireMethodResult
  assignedTo?: string | null
}

export type UxNavigationStepDto = {
  id: number
  stepNumber: number
  stepName: string | null
  actionPerformed: string | null
  observation: string | null
  pageUrl: string | null
  pageTitle: string | null
  screenshotBase64: string | null
  /** CLICK | FILL | SCROLL | DONE | SKIP */
  actionType?: string | null
  /** CSS selector or visible text of the target element */
  selector?: string | null
  /** Value typed (only for FILL actions) */
  fillValue?: string | null
}

export type UxEvaluationDto = {
  id: number
  projectId: number | null
  platform: 'WEB' | 'MOBILE' | 'WEB_DESKTOP' | 'WEB_MOBILE' | 'MOBILE_APP'
  url?: string | null
  description?: string | null
  /** Scénario imposé par le testeur (mode ciblé) ; vide = exploration libre */
  scenario?: string | null
  apkPath?: string | null
  /** AUTO (sans validation) ou SUPERVISED (le testeur valide les verdicts douteux) */
  reviewMode?: string | null
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  testSummary?: string | null
  aiAnalysis?: string | null
  pageContent?: string | null
  logs?: string | null
  screenshotUrl?: string | null
  generatedScript?: string | null
  durationMs?: number | null
  errorMessage?: string | null
  ownerUserId?: number | null
  createdAt: string
  executedAt?: string | null
  navigationSteps?: UxNavigationStepDto[]
}

export type CampaignStatusBackendDto = {
  id: number
  projectId: number | null
  environmentId: number | null
  status: string // PENDING, RUNNING, FINISHED, FINISHED_WITH_ERRORS, ABORTED
  triggerMode: string | null // MANUAL, SCHEDULED, CI
  gitBranch: string | null
  startedAt: string | null
  finishedAt: string | null
  executionResults: ExecutionResultBackendDto[]
}

export type CampaignRunResponseDto = {
  status: string // "started", "already_running", "error"
  message?: string | null
  campaignId?: number | null
  campaign?: CampaignStatusBackendDto | null
}

// Map backend DTOs to frontend TestExecutionDto for consistency
export function mapExecutionResultToTestExecutionDto(result: ExecutionResultBackendDto, campaignId: number): TestExecutionDto {
  return {
    id: result.id,
    executionNumber: null,
    executionDate: result.executedAt,
    executionType: 'INITIAL',
    status: result.status === 'SUCCESS' ? 'FINISHED' : 'ERROR',
    campaignId: campaignId,
  }
}

// Functional evaluation endpoints
export type FunctionalEvaluationDto = UxEvaluationDto

export async function listFunctionalEvaluations(projectId?: number, platform?: 'WEB' | 'MOBILE'): Promise<FunctionalEvaluationDto[]> {
  const q = new URLSearchParams()
  if (projectId !== undefined && projectId !== null) q.set('projectId', String(projectId))
  if (platform) q.set('platform', platform)
  const suffix = q.toString() ? `?${q.toString()}` : ''
  const token = getAccessToken()
  return requestJson<FunctionalEvaluationDto[]>(`/api/functional-evaluation${suffix}`, {
    cache: 'no-store',
    headers: { Authorization: `Bearer ${token}` },
  })
}

export async function getFunctionalEvaluation(id: number): Promise<FunctionalEvaluationDto> {
  const token = getAccessToken()
  return requestJson<FunctionalEvaluationDto>(`/api/functional-evaluation/${id}`, {
    cache: 'no-store',
    headers: { Authorization: `Bearer ${token}` },
  })
}

export async function createFunctionalEvaluation(payload: Partial<FunctionalEvaluationDto>): Promise<FunctionalEvaluationDto> {
  const token = getAccessToken()
  const response = await fetch(`/api/functional-evaluation`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload),
  })
  if (!response.ok) throw new Error(await readReadableError(response))
  return (await response.json()) as FunctionalEvaluationDto
}

export async function generateUxScript(payload: { platform: 'WEB' | 'MOBILE' | 'WEB_DESKTOP' | 'WEB_MOBILE' | 'MOBILE_APP'; url: string; description: string }): Promise<string> {
  const token = getAccessToken()
  const response = await fetch(`/api/llm/generate-functional-script`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload),
  })
  if (!response.ok) throw new Error(await readReadableError(response))
  const data = await response.json()
  return data.script
}

export async function executeFunctionalEvaluation(id: number): Promise<void> {
  const token = getAccessToken()
  const response = await fetch(`/api/functional-evaluation/${encodeURIComponent(String(id))}/execute`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) throw new Error(await readReadableError(response))
}

export async function deleteFunctionalEvaluation(id: number): Promise<void> {
  const token = getAccessToken()
  const response = await fetch(`/api/functional-evaluation/${encodeURIComponent(String(id))}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) throw new Error(await readReadableError(response))
}

export async function downloadFunctionalEvaluationReport(id: number): Promise<Blob> {
  const token = getAccessToken()
  const response = await fetch(`/api/reports/ux-evaluation/${encodeURIComponent(String(id))}/pdf`, {
    method: 'GET',
    headers: { accept: 'application/pdf', Authorization: `Bearer ${token}` },
  })
  if (!response.ok) throw new Error(await readReadableError(response))
  return await response.blob()
}

export async function listUxEvaluations(projectId?: number, platform?: 'WEB' | 'MOBILE'): Promise<UxEvaluationDto[]> {
  return listFunctionalEvaluations(projectId, platform)
}

export async function getUxEvaluation(id: number): Promise<UxEvaluationDto> {
  return getFunctionalEvaluation(id)
}

export async function createUxEvaluation(payload: Partial<UxEvaluationDto>): Promise<UxEvaluationDto> {
  return createFunctionalEvaluation(payload)
}

export async function executeUxEvaluation(id: number): Promise<void> {
  return executeFunctionalEvaluation(id)
}

export async function downloadUxEvaluationReport(id: number): Promise<Blob> {
  return downloadFunctionalEvaluationReport(id)
}

/** Stop a running evaluation */
export async function stopFunctionalEvaluation(id: number): Promise<void> {
  const token = getAccessToken()
  const response = await fetch(`/api/functional-evaluation/${encodeURIComponent(String(id))}/stop`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) throw new Error('Échec de l\'arrêt')
}

/** Pause a running evaluation (HITL) */
export async function pauseFunctionalEvaluation(id: number): Promise<void> {
  const token = getAccessToken()
  const response = await fetch(`/api/functional-evaluation/${encodeURIComponent(String(id))}/pause`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) throw new Error('Échec de la pause')
}

/** Resume a paused evaluation (HITL) */
export async function resumeFunctionalEvaluation(id: number): Promise<void> {
  const token = getAccessToken()
  const response = await fetch(`/api/functional-evaluation/${encodeURIComponent(String(id))}/resume`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) throw new Error('Échec de la reprise')
}

export interface FunctionalTestResultDto {
  id: number
  evaluationId: number
  formLabel: string | null
  scenario: string | null
  targetField: string | null
  inputData: string | null
  expected: string | null
  observed: string | null
  status: 'PASS' | 'FAIL' | 'WARN' | 'NEEDS_REVIEW' | null
  severity: string | null
  confidence: number | null
  evidence: string | null
  humanValidated: boolean | null
  createdAt: string | null
}

/** Fetch the detailed functional test results of an evaluation */
export async function getFunctionalResults(id: number): Promise<FunctionalTestResultDto[]> {
  const token = getAccessToken()
  return requestJson<FunctionalTestResultDto[]>(`/api/functional-evaluation/${id}/functional-results`, {
    headers: { Authorization: `Bearer ${token}` },
  })
}

export async function uploadApk(file: File): Promise<{ path: string; filename: string }> {
  const token = getAccessToken()
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch(`http://localhost:8083/api/functional-evaluation/upload-apk`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: formData,
    credentials: 'include',
  })
  if (!response.ok) throw new Error('Échec de l\'upload APK')
  return response.json()
}

/** Poll navigation steps for a running evaluation (live view) */
export async function getEvaluationSteps(id: number): Promise<UxNavigationStepDto[]> {
  const token = getAccessToken()
  return requestJson<UxNavigationStepDto[]>(`/api/functional-evaluation/${id}/steps`, {
    cache: 'no-store',
    headers: { Authorization: `Bearer ${token}` },
  })
}

export function getAccessToken(): string {
  const token = typeof window !== 'undefined' ? getStoredAccessToken() : null
  if (!token) throw new Error('JWT access token is missing')
  return token
}

// Old types for backward compatibility with test-management
export type CampaignRunResponse = {
  status: string
  message?: string | null
  sessionId?: string | null
  execution?: TestExecutionDto | null
  campaign?: CampaignStatusBackendDto | null
  endpoints?: EndpointDto[] | null
  missingDb?: boolean | null
  missingEnvVars?: string[] | null
  dbOptions?: string[] | null
  notes?: string[] | null
  editableFiles?: EditableFileDto[] | null
}

// Campaign run request/continue request types
export type CampaignRunRequest = {
  branch?: string | null
  db?: string | null
  envValues?: Record<string, string>
  hostPortBase?: number | null
  useOllama?: boolean
  ollamaModel?: string | null
  runMode?: 'ALL' | 'SELECTED'
  testCaseIds?: number[]
}

export type CampaignRunContinueRequest = {
  sessionId: string
  db?: string | null
  envValues?: Record<string, string>
  fileOverrides?: Record<string, string>
  hostPortBase?: number | null
  useOllama?: boolean
  ollamaModel?: string | null
}

export type EditableFileDto = {
  path: string
  content: string
}
// ---------------------------------------------------------------------

export type DiscoveryStatus =
  | 'queued'
  | 'running'
  | 'needs_user_input'
  | 'done'
  | 'error'
  | (string & {})

export type DiscoveryQuestionOption = string

export type DiscoveryQuestion = {
  json_path: string
  reason: string
  expected_format: string
  example?: string | null
  how_to_find?: string | null
  options?: DiscoveryQuestionOption[] | null
}

export type DiscoveryQuestionnairePayload = {
  questionnaire: {
    questions: DiscoveryQuestion[]
  }
}

export type DiscoveryFlowDto = {
  discoveryId: string
  status: DiscoveryStatus
  jobId?: string | null
  questionnaire?: DiscoveryQuestionnairePayload | null
  endpoints?: EndpointDto[] | null
  error?: unknown
}

export type DiscoveryAnswerDto = {
  json_path: string
  value: unknown
}

export type DiscoveryCompleteRequest = {
  answers: DiscoveryAnswerDto[]
  overwrite?: boolean
  return_openapi?: boolean
}

export type GitProvider = 'GITHUB' | 'GITLAB'

export type TestType = 'FUNCTIONAL' | 'PERFORMANCE' | 'REGRESSION' | 'SECURITY' | 'API' | 'FUNCTIONAL_WEB' | 'FUNCTIONAL_MOBILE'

export interface TestCaseDto {
  id: number
  name: string
  description?: string | null
  testType?: TestType | string | null
  priority?: string | null
  tool?: string | null
  riskScore?: number | null
}

export interface TestCaseCreateRequest {
  name: string
  description?: string | null
  testType?: TestType | string | null
  priority?: string | null
  tool?: string | null
  riskScore?: number | null
}

export interface TestCampaignDto {
  id: number
  projectId: number
  environmentId: number
  name: string
  appVersion?: string | null
  gitBranch?: string | null
  triggerMode?: string | null
  status?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt?: string | null
}

export interface TestCampaignCreateRequest {
  projectId: number
  name: string
  environmentId: number
  appVersion?: string | null
  gitBranch?: string | null
  triggerMode?: string | null
  testCaseIds?: number[]
}

export interface TestCampaignUpdateRequest {
  name?: string | null
  environmentId?: number | null
  appVersion?: string | null
  gitBranch?: string | null
  triggerMode?: string | null
  testCaseIds?: number[]
}

export interface TestCaseWithStatusDto {
  id: number
  title: string
  description?: string | null
  type?: string | null
  priority?: number | null
  riskLevel?: string | null
  scriptPath?: string | null
  tags?: string | null
  maxDurationSeconds?: number | null
  active?: boolean | null
  flaky?: boolean | null
  createdAt?: string | null
  executionStatus?: ExecutionStatus | null
  executionDurationMs?: number | null
  lastExecutedAt?: string | null
  lastErrorMessage?: string | null
}

export type RepoResolveResponse = {
  owner?: string | null
  repo?: string | null
  fullName?: string | null
  name?: string | null
  description?: string | null
  htmlUrl?: string | null
  repositoryUrl?: string | null
  defaultBranch?: string | null
}

function normalizeEnum<T extends string>(value: unknown, allowed: readonly T[]): T {
  const raw = String(value ?? '').trim().toUpperCase()
  if ((allowed as readonly string[]).includes(raw)) return raw as T
  // Fall back to the raw value so backend can error with details.
  return raw as T
}

export function inferGitProviderFromUrl(repositoryUrl: string): GitProvider | null {
  try {
    const url = new URL(repositoryUrl)
    const host = url.hostname.toLowerCase()
    if (host.includes('github.com')) return 'GITHUB'
    if (host.includes('gitlab')) return 'GITLAB'
    return null
  } catch {
    return null
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

async function readReadableError(response: Response): Promise<string> {
  const responseCopy = response.clone()
  const contentType = response.headers.get('content-type') ?? ''
  const isJson = contentType.includes('application/json')

  if (isJson) {
    const data = (await response.json().catch(() => null)) as unknown
    if (typeof data === 'string' && data.trim()) {
      return `${response.status} ${response.statusText}`.trim() + `: ${data}`
    }

    if (isRecord(data)) {
      const message = data.message
      if (typeof message === 'string' && message.trim()) {
        return `${response.status} ${response.statusText}`.trim() + `: ${message}`
      }

      const error = data.error
      if (typeof error === 'string' && error.trim()) {
        return `${response.status} ${response.statusText}`.trim() + `: ${error}`
      }

      const details = data.details
      if (details !== undefined) {
        const detailsText =
          typeof details === 'string'
            ? details
            : JSON.stringify(details).slice(0, 2000)
        return `${response.status} ${response.statusText}`.trim() + `: ${detailsText}`
      }
    }

    const status = `${response.status} ${response.statusText}`.trim()
    if (data) {
      return `${status}: ${JSON.stringify(data).slice(0, 2000)}`
    }

    const rawText = await responseCopy.text().catch(() => '')
    if (rawText.trim()) {
      return `${status}: ${rawText.trim()}`
    }

    return `${status}: Unknown error`
  }

  const text = await response.text().catch(() => '')
  const status = `${response.status} ${response.statusText}`.trim()
  if (text.trim()) return `${status}: ${text.trim()}`
  return `${status}: Request failed`
}

async function requestJson<T>(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<T> {
  const headers = withAuthHeaders(init?.headers)
  if (!headers.has('accept')) headers.set('accept', 'application/json')

  const response = await fetch(input, {
    credentials: 'include',
    ...init,
    headers,
  })

  if (!response.ok) {
    const message = await readReadableError(response)
    throw new Error(message)
  }

  return (await response.json()) as T
}

async function requestVoid(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<void> {
  const headers = withAuthHeaders(init?.headers)
  if (!headers.has('accept')) headers.set('accept', 'application/json')

  const response = await fetch(input, {
    credentials: 'include',
    ...init,
    headers,
  })

  if (!response.ok) {
    const message = await readReadableError(response)
    throw new Error(message)
  }
}

export async function getProjects(): Promise<Project[]> {
  return requestJson<Project[]>('/api/projects', { cache: 'no-store' })
}

export async function createProject(payload: CreateProjectPayload): Promise<Project> {
  const normalizedPayload = {
    ...payload,
    projectType: normalizeEnum(payload.projectType, ['WEB', 'MOBILE', 'API', 'DESKTOP', 'OTHER'] as const),
    sourceType: normalizeEnum(payload.sourceType, ['GIT', 'LOCAL'] as const),
  }

  return requestJson<Project>('/api/projects', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(normalizedPayload),
  })
}

export async function getProjectEndpoints(
  projectId: number,
  branch?: string,
): Promise<EndpointDto[]> {
  const query = new URLSearchParams()
  if (branch && branch.trim()) query.set('branch', branch.trim())
  const suffix = query.toString() ? `?${query.toString()}` : ''

  return requestJson<EndpointDto[]>(
    `/api/projects/${encodeURIComponent(String(projectId))}/endpoints${suffix}`,
    { cache: 'no-store' },
  )
}

export async function startProjectDiscovery(
  projectId: number,
  params?: { branch?: string },
): Promise<DiscoveryFlowDto> {
  const query = new URLSearchParams()
  if (params?.branch && params.branch.trim()) query.set('branch', params.branch.trim())
  const suffix = query.toString() ? `?${query.toString()}` : ''

  return requestJson<DiscoveryFlowDto>(
    `/api/projects/${encodeURIComponent(String(projectId))}/discoveries${suffix}`,
    {
      method: 'POST',
    },
  )
}

export async function getLatestProjectDiscovery(
  projectId: number,
  params?: { branch?: string },
): Promise<DiscoveryFlowDto> {
  const query = new URLSearchParams()
  if (params?.branch && params.branch.trim()) query.set('branch', params.branch.trim())
  const suffix = query.toString() ? `?${query.toString()}` : ''

  return requestJson<DiscoveryFlowDto>(
    `/api/projects/${encodeURIComponent(String(projectId))}/discoveries/latest${suffix}`,
    { cache: 'no-store' },
  )
}

export async function completeProjectDiscovery(
  projectId: number,
  discoveryId: string,
  payload: DiscoveryCompleteRequest,
): Promise<DiscoveryFlowDto> {
  return requestJson<DiscoveryFlowDto>(
    `/api/projects/${encodeURIComponent(String(projectId))}/discoveries/${encodeURIComponent(String(discoveryId))}/complete`,
    {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(payload),
    },
  )
}

export async function getCampaignEndpoints(
  campaignId: number,
): Promise<EndpointDto[]> {
  return requestJson<EndpointDto[]>(
    `/api/campaigns/${encodeURIComponent(String(campaignId))}/endpoints`,
    { cache: 'no-store' },
  )
}

export async function resolveRepo(payload: {
  repositoryUrl: string
  provider?: GitProvider
}): Promise<RepoResolveResponse> {
  const normalizedPayload = {
    ...payload,
    provider: payload.provider
      ? normalizeEnum(payload.provider, ['GITHUB', 'GITLAB'] as const)
      : undefined,
  }

  return requestJson<RepoResolveResponse>('/api/repo/resolve', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(normalizedPayload),
  })
}

export async function listSessions(params?: {
  projectId?: number
  campaignId?: number
}): Promise<TestSession[]> {
  const query = new URLSearchParams()
  if (params?.projectId !== undefined) query.set('projectId', String(params.projectId))
  if (params?.campaignId !== undefined) query.set('campaignId', String(params.campaignId))
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return requestJson<TestSession[]>(`/api/sessions${suffix}`, { cache: 'no-store' })
}

export async function getSession(id: number): Promise<TestSession> {
  return requestJson<TestSession>(`/api/sessions/${encodeURIComponent(String(id))}`, {
    cache: 'no-store',
  })
}

export async function createSession(
  input: Omit<TestSession, 'id'>,
  projectId: number,
  campaignId?: number,
): Promise<TestSession> {
  const query = new URLSearchParams({ projectId: String(projectId) })
  if (campaignId !== undefined) query.set('campaignId', String(campaignId))

  return requestJson<TestSession>(`/api/sessions?${query.toString()}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      startDate: input.startDate,
      endDate: input.endDate ?? null,
      environment: input.environment ?? null,
      status: normalizeEnum(input.status, ['OPEN', 'IN_PROGRESS', 'CLOSED'] as const),
      triggerType: input.triggerType ?? null,
    }),
  })
}

export async function updateSession(
  id: number,
  input: Partial<TestSession>,
): Promise<TestSession> {
  const body: Record<string, unknown> = {}
  if ('startDate' in input) body.startDate = input.startDate
  if ('endDate' in input) body.endDate = input.endDate ?? null
  if ('environment' in input) body.environment = input.environment ?? null
  if ('status' in input) {
    body.status = normalizeEnum(input.status, ['OPEN', 'IN_PROGRESS', 'CLOSED'] as const)
  }
  if ('triggerType' in input) body.triggerType = input.triggerType ?? null

  return requestJson<TestSession>(`/api/sessions/${encodeURIComponent(String(id))}`, {
    method: 'PUT',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function deleteSession(id: number): Promise<void> {
  await requestVoid(`/api/sessions/${encodeURIComponent(String(id))}`, {
    method: 'DELETE',
  })
}

// Back-compat wrappers (older UI code).
export async function getSessions(projectId: number): Promise<TestSession[]> {
  return listSessions({ projectId })
}

export async function getExecutions(campaignId: number): Promise<TestExecution[]> {
  return listExecutions({ campaignId })
}

export async function createExecution(
  campaignId: number,
  input: TestExecutionInput,
): Promise<TestExecution> {
  return createExecutionDto(campaignId, input)
}

export async function listExecutionResults(params?: {
  campaignId?: number
}): Promise<ExecutionResultBackendDto[]> {
  const query = new URLSearchParams()
  if (params?.campaignId !== undefined) query.set('campaignId', String(params.campaignId))
  const suffix = query.toString() ? '?' + query.toString() : ''
  
  try {
    const results = await requestJson<ExecutionResultBackendDto[]>('/api/executions' + suffix, { cache: 'no-store' })
    return Array.isArray(results) ? results : []
  } catch (error) {
    console.warn('Failed to load execution results', error)
    return []
  }
}

export async function assignExecutionResult(
  id: number,
  member: { userId: number; name: string; email: string }
): Promise<boolean> {
  try {
    const res = await fetch(`/api/execution-results/${id}/assign`, {
      method: 'PATCH',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(member),
    })
    return res.ok
  } catch {
    return false
  }
}

export async function listExecutions(params?: {
  campaignId?: number
}): Promise<TestExecutionDto[]> {
  const query = new URLSearchParams()
  if (params?.campaignId !== undefined) query.set('campaignId', String(params.campaignId))
  const suffix = query.toString() ? '?' + query.toString() : ''
  
  try {
    const results = await requestJson<ExecutionResultBackendDto[]>('/api/executions' + suffix, { cache: 'no-store' })
    // Map backend execution results to TestExecutionDto format
    if (Array.isArray(results) && params?.campaignId) {
      return results.map(r => mapExecutionResultToTestExecutionDto(r, params.campaignId!))
    }
    return []
  } catch (error) {
    console.warn('Failed to load executions, continuing without execution history', error)
    return []
  }
}

export async function getExecution(id: number): Promise<TestExecutionDto> {
  return requestJson<TestExecutionDto>('/api/executions/' + encodeURIComponent(String(id)), {
    cache: 'no-store',
  })
}

export async function createExecutionDto(
  campaignId: number,
  input: TestExecutionInput,
): Promise<TestExecutionDto> {
  const body: Record<string, unknown> = {
    executionType: normalizeEnum(input.executionType, ['INITIAL', 'RETEST'] as const),
    status: normalizeEnum(input.status, ['QUEUED', 'RUNNING', 'FINISHED', 'ERROR'] as const),
  }

  if ('executionNumber' in input) body.executionNumber = input.executionNumber ?? null
  if (input.executionDate !== undefined) body.executionDate = input.executionDate

  return requestJson<TestExecutionDto>(
    '/api/executions?campaignId=' + encodeURIComponent(String(campaignId)),
    {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    },
  )
}

export async function updateExecution(
  id: number,
  input: Partial<TestExecutionInput>,
): Promise<TestExecutionDto> {
  const body: Record<string, unknown> = {}

  if ('executionNumber' in input) body.executionNumber = input.executionNumber ?? null
  if (input.executionDate !== undefined) body.executionDate = input.executionDate
  if ('executionType' in input && input.executionType !== undefined) {
    body.executionType = normalizeEnum(input.executionType, ['INITIAL', 'RETEST'] as const)
  }
  if ('status' in input && input.status !== undefined) {
    body.status = normalizeEnum(input.status, ['QUEUED', 'RUNNING', 'FINISHED', 'ERROR'] as const)
  }

  return requestJson<TestExecutionDto>(`/api/executions/${encodeURIComponent(String(id))}`, {
    method: 'PUT',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function deleteExecution(id: number): Promise<void> {
  await requestVoid(`/api/executions/${encodeURIComponent(String(id))}`, {
    method: 'DELETE',
  })
}

export async function listCampaigns(params?: { projectId?: number }): Promise<TestCampaignDto[]> {
  if (params?.projectId) {
    const query = new URLSearchParams({ projectId: String(params.projectId) })
    return requestJson<TestCampaignDto[]>(`/api/campaigns?${query.toString()}`, { cache: 'no-store' })
  } else {
    // Fetch all campaigns when projectId is not provided
    return requestJson<TestCampaignDto[]>(`/api/campaigns`, { cache: 'no-store' })
  }
}

export async function getCampaign(projectId: number, id: number): Promise<TestCampaignDto> {
  const query = new URLSearchParams({ projectId: String(projectId) })
  return requestJson<TestCampaignDto>(
    `/api/campaigns/${encodeURIComponent(String(id))}?${query.toString()}`,
    {
    cache: 'no-store',
    },
  )
}

export async function getTestCasesForCampaign(projectId: number, campaignId: number): Promise<TestCaseWithStatusDto[]> {
  const query = new URLSearchParams({ projectId: String(projectId) })
  return requestJson<TestCaseWithStatusDto[]>(
    `/api/campaigns/${encodeURIComponent(String(campaignId))}/testcases?${query.toString()}`,
    { cache: 'no-store' },
  )
}

export async function createCampaign(input: TestCampaignCreateRequest): Promise<TestCampaignDto> {
  const query = new URLSearchParams({ projectId: String(input.projectId) })
  return requestJson<TestCampaignDto>(`/api/campaigns?${query.toString()}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      name: input.name,
      environmentId: input.environmentId,
      appVersion: input.appVersion ?? null,
      gitBranch: input.gitBranch ?? null,
      triggerMode: input.triggerMode
        ? normalizeEnum(input.triggerMode, ['MANUAL', 'SCHEDULED', 'CI'] as const)
        : 'MANUAL',
      testCaseIds: Array.isArray(input.testCaseIds) ? input.testCaseIds : [],
    }),
  })
}

export async function updateCampaign(projectId: number, campaignId: number, input: Omit<TestCampaignCreateRequest, 'projectId'>): Promise<TestCampaignDto> {
  const query = new URLSearchParams({ projectId: String(projectId) })
  return requestJson<TestCampaignDto>(`/api/campaigns/${campaignId}?${query.toString()}`, {
    method: 'PUT',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      name: input.name,
      environmentId: input.environmentId,
      appVersion: input.appVersion ?? null,
      triggerMode: input.triggerMode
        ? normalizeEnum(input.triggerMode, ['MANUAL', 'SCHEDULED', 'CI'] as const)
        : 'MANUAL',
    }),
  })
}

export async function deleteCampaign(projectId: number, campaignId: number): Promise<void> {
  if (!Number.isFinite(projectId) || projectId <= 0) {
    throw new Error('Invalid projectId for campaign deletion')
  }
  if (!Number.isFinite(campaignId) || campaignId <= 0) {
    throw new Error('Invalid campaignId for campaign deletion')
  }

  const query = new URLSearchParams({ projectId: String(projectId) })
  return requestVoid(`/api/campaigns/${encodeURIComponent(String(campaignId))}?${query.toString()}`, {
    method: 'DELETE',
  })
}

export async function listTestCases(): Promise<TestCaseDto[]> {
  return requestJson<TestCaseDto[]>(`/api/cases`, { cache: 'no-store' })
}

export async function createTestCase(input: TestCaseCreateRequest): Promise<TestCaseDto> {
  return requestJson<TestCaseDto>(`/api/cases`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      name: input.name,
      description: input.description ?? null,
      testType: input.testType ?? null,
      priority: input.priority ?? null,
      tool: input.tool ?? null,
      riskScore: input.riskScore ?? null,
    }),
  })
}

// --------------------------------------------------
export async function startCampaignRun(
  campaignId: number,
  payload: CampaignRunRequest = {},
): Promise<CampaignRunResponse> {
  const response = await fetch(
    '/api/campaigns/' + encodeURIComponent(String(campaignId)) + '/Run',
    {
      method: 'POST',
      credentials: 'include',
      headers: {
        accept: 'application/json',
        'content-type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )

  const text = await response.text().catch(() => '')
  let data: CampaignRunResponseDto | null = null
  try { data = text ? JSON.parse(text) : null } catch { /* not JSON */ }

  if (!response.ok) {
    const message = data?.message || text || `Run failed (HTTP ${response.status})`
    throw new Error(message)
  }

  // Map ms-execution response to CampaignRunResponse format
  if (data) {
    const mapped: CampaignRunResponse = {
      status: data.status,
      message: data.message,
      campaign: data.campaign,
      execution: data.campaign?.executionResults?.[0]
        ? mapExecutionResultToTestExecutionDto(data.campaign.executionResults[0], campaignId)
        : undefined,
    }
    return mapped
  }

  return { status: 'started' }
}

export async function continueCampaignRun(
  campaignId: number,
  payload: CampaignRunContinueRequest,
): Promise<CampaignRunResponse> {
  const response = await fetch(
    '/api/campaigns/' + encodeURIComponent(String(campaignId)) + '/Run/continue',
    {
      method: 'POST',
      credentials: 'include',
      headers: {
        accept: 'application/json',
        'content-type': 'application/json',
      },
      body: JSON.stringify(payload),
    },
  )

  const data = (await response.json().catch(() => null)) as unknown

  if (!response.ok) {
    const message = await readReadableError(response)
    throw new Error(message)
  }

  // ms-execution doesn't support continuation flow
  return { status: 'error', message: 'Continuation not supported in ms-execution' } as CampaignRunResponse
}

// Reports API helpers
export type ReportMetadata = {
  id: number
  campaignId: number
  filename: string
  generatedAt: string
}

export async function listReportsForCampaign(campaignId: number): Promise<ReportMetadata[]> {
  try {
    return await requestJson<ReportMetadata[]>(`/api/reports/campaign/${encodeURIComponent(String(campaignId))}`, {
      cache: 'no-store',
    })
  } catch (error) {
    // If backend doesn't support listing yet, return empty list and let UI fall back to on-demand generation
    console.warn('Failed to list reports for campaign', error)
    return []
  }
}

export async function downloadCampaignReport(campaignId: number): Promise<Blob> {
  const headers = withAuthHeaders({ accept: 'application/pdf' })
  const response = await fetch(`/api/reports/campaign/${encodeURIComponent(String(campaignId))}/pdf`, {
    method: 'GET',
    credentials: 'include',
    headers,
  })

  if (!response.ok) {
    const message = await readReadableError(response)
    throw new Error(message)
  }

  return await response.blob()
}

export async function downloadReportById(reportId: number): Promise<Blob> {
  const headers = withAuthHeaders({ accept: 'application/pdf' })
  const response = await fetch(`/api/reports/${encodeURIComponent(String(reportId))}/pdf`, {
    method: 'GET',
    credentials: 'include',
    headers,
  })

  if (!response.ok) {
    const message = await readReadableError(response)
    throw new Error(message)
  }

  return await response.blob()
}

export async function generateAndStoreReport(campaignId: number): Promise<ReportMetadata> {
  const headers = withAuthHeaders({ accept: 'application/json' })
  const response = await fetch(`/api/reports/campaign/${encodeURIComponent(String(campaignId))}`, {
    method: 'POST',
    credentials: 'include',
    headers,
  })

  if (!response.ok) {
    const message = await readReadableError(response)
    throw new Error(message)
  }

  return (await response.json()) as ReportMetadata
}

export async function listAllReports(): Promise<ReportMetadata[]> {
  try {
    return await requestJson<ReportMetadata[]>(`/api/reports`, { cache: 'no-store' })
  } catch (error) {
    console.warn('Failed to list all reports', error)
    return []
  }
}

export async function stopCampaign(projectId: number, campaignId: number): Promise<{ message: string }> {
  // Call ms-execution directly (via the Next route that forwards the JWT) instead of
  // proxying through ms_gestion, which dropped the token and got 401 from ms-execution.
  void projectId
  const response = await fetch(
    '/api/campaigns/' + encodeURIComponent(String(campaignId)) + '/stop',
    {
      method: 'PUT',
      credentials: 'include',
      headers: {
        accept: 'application/json',
        'content-type': 'application/json',
      },
    },
  )

  const data = (await response.json().catch(() => null)) as { message: string } | null

  if (!response.ok) {
    const message = await readReadableError(response)
    throw new Error(message)
  }

  return data ?? { message: 'Campaign stopped' }
}

function withAuthHeaders(initHeaders?: HeadersInit): Headers {
  const headers = new Headers(initHeaders ?? {})
  const token = getAccessToken()
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  return headers
}
// --------------------------------------------------


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
  sessionId: number
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

export type GitProvider = 'GITHUB' | 'GITLAB'

export type TestType = 'FUNCTIONAL' | 'PERFORMANCE' | 'REGRESSION' | 'SECURITY' | 'API'

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

export interface TestCampaignSetTestCasesRequest {
  testCaseIds: number[]
}

export interface TestCampaignDto {
  id: number
  projectId: number
  name: string
  version?: string | null
  status?: string | null
  startDate?: string | null
  endDate?: string | null
  environment?: string | null
  triggerType?: string | null
  sessionStatus?: string | null
  executionStartDate?: string | null
  executionEndDate?: string | null
  createdBy?: string | null
  testCaseIds: number[]
}

export interface TestCampaignCreateRequest {
  projectId: number
  name: string
  version?: string | null
  status?: string | null
  startDate?: string | null
  endDate?: string | null
  environment?: string | null
  triggerType?: string | null
  sessionStatus?: string | null
  executionStartDate?: string | null
  executionEndDate?: string | null
  createdBy?: string | null
}

export interface TestCampaignUpdateRequest {
  name?: string | null
  version?: string | null
  status?: string | null
  startDate?: string | null
  endDate?: string | null
  environment?: string | null
  triggerType?: string | null
  sessionStatus?: string | null
  executionStartDate?: string | null
  executionEndDate?: string | null
  createdBy?: string | null
  // Not part of the documented DTO yet, but supported as a fallback until a
  // dedicated /testcases endpoint exists server-side.
  testCaseIds?: number[]
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
    const fallback = data ? JSON.stringify(data).slice(0, 2000) : 'Unknown error'
    return `${status}: ${fallback}`
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
  const response = await fetch(input, {
    credentials: 'include',
    ...init,
    headers: {
      accept: 'application/json',
      ...(init?.headers ?? {}),
    },
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
  const response = await fetch(input, {
    credentials: 'include',
    ...init,
    headers: {
      accept: 'application/json',
      ...(init?.headers ?? {}),
    },
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

export async function getExecutions(sessionId: number): Promise<TestExecution[]> {
  return listExecutions({ sessionId })
}

export async function createExecution(
  sessionId: number,
  input: TestExecutionInput,
): Promise<TestExecution> {
  return createExecutionDto(sessionId, input)
}

export async function listExecutions(params?: {
  sessionId?: number
}): Promise<TestExecutionDto[]> {
  const query = new URLSearchParams()
  if (params?.sessionId !== undefined) query.set('sessionId', String(params.sessionId))
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return requestJson<TestExecutionDto[]>(`/api/executions${suffix}`, { cache: 'no-store' })
}

export async function getExecution(id: number): Promise<TestExecutionDto> {
  return requestJson<TestExecutionDto>(`/api/executions/${encodeURIComponent(String(id))}`, {
    cache: 'no-store',
  })
}

export async function createExecutionDto(
  sessionId: number,
  input: TestExecutionInput,
): Promise<TestExecutionDto> {
  const body: Record<string, unknown> = {
    executionType: normalizeEnum(input.executionType, ['INITIAL', 'RETEST'] as const),
    status: normalizeEnum(input.status, ['QUEUED', 'RUNNING', 'FINISHED', 'ERROR'] as const),
  }

  if ('executionNumber' in input) body.executionNumber = input.executionNumber ?? null
  if (input.executionDate !== undefined) body.executionDate = input.executionDate

  return requestJson<TestExecutionDto>(`/api/executions?sessionId=${encodeURIComponent(String(sessionId))}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  })
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

export async function listCampaigns(params: { projectId: number }): Promise<TestCampaignDto[]> {
  const query = new URLSearchParams({ projectId: String(params.projectId) })
  return requestJson<TestCampaignDto[]>(`/api/campaigns?${query.toString()}`, { cache: 'no-store' })
}

export async function getCampaign(id: number): Promise<TestCampaignDto> {
  return requestJson<TestCampaignDto>(`/api/campaigns/${encodeURIComponent(String(id))}`, {
    cache: 'no-store',
  })
}

export async function createCampaign(input: TestCampaignCreateRequest): Promise<TestCampaignDto> {
  return requestJson<TestCampaignDto>('/api/campaigns', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      projectId: input.projectId,
      name: input.name,
      version: input.version ?? null,
      status: input.status ?? null,
      startDate: input.startDate ?? null,
      endDate: input.endDate ?? null,
      environment: input.environment ?? null,
      triggerType: input.triggerType ?? null,
      sessionStatus: input.sessionStatus ?? null,
      executionStartDate: input.executionStartDate ?? null,
      executionEndDate: input.executionEndDate ?? null,
      createdBy: input.createdBy ?? null,
    }),
  })
}

export async function updateCampaign(id: number, input: TestCampaignUpdateRequest): Promise<TestCampaignDto> {
  return requestJson<TestCampaignDto>(`/api/campaigns/${encodeURIComponent(String(id))}`, {
    method: 'PUT',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export async function attachCampaignTestCases(
  campaignId: number,
  testCaseIds: number[],
): Promise<void> {
  const response = await fetch(
    `/api/campaigns/${encodeURIComponent(String(campaignId))}/testcases`,
    {
      method: 'PUT',
      credentials: 'include',
      headers: {
        'content-type': 'application/json',
        accept: 'application/json',
      },
      body: JSON.stringify({ testCaseIds } satisfies TestCampaignSetTestCasesRequest),
    },
  )

  if (response.ok) return
  const message = await readReadableError(response)
  throw new Error(message)
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

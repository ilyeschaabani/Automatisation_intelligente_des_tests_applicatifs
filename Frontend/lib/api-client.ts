export type ProjectType = 'WEB' | 'MOBILE' | 'API' | 'DESKTOP' | 'OTHER'
export type SourceType = 'GIT' | 'URL'

export type Project = {
  id: number
  name: string
  projectType: ProjectType
  sourceType: SourceType
  repositoryUrl: string | null
  gitTokenId: string | null
  technologyStack: string | null
  deployed: boolean
  createdAt?: string | null
}

export type CreateProjectPayload = {
  name: string
  projectType: ProjectType
  sourceType: SourceType
  repositoryUrl?: string | null
  gitTokenId?: string | null
  technologyStack?: string | null
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

export type TestExecution = {
  id: number
  executionNumber: number
  executionDate: string
  executionType: ExecutionType
  status: ExecutionStatus
}

export type CreateExecutionPayload = {
  executionType: ExecutionType
}

export type GitProvider = 'GITHUB' | 'GITLAB'

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
    sourceType: normalizeEnum(payload.sourceType, ['GIT', 'URL'] as const),
  }

  return requestJson<Project>('/api/projects', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(normalizedPayload),
  })
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
  return requestJson<TestExecution[]>(`/api/executions?sessionId=${encodeURIComponent(String(sessionId))}`, {
    cache: 'no-store',
  })
}

export async function createExecution(
  sessionId: number,
  payload: CreateExecutionPayload,
): Promise<TestExecution> {
  return requestJson<TestExecution>(`/api/executions?sessionId=${encodeURIComponent(String(sessionId))}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      ...payload,
      executionType: normalizeEnum(payload.executionType, ['INITIAL', 'RETEST'] as const),
    }),
  })
}

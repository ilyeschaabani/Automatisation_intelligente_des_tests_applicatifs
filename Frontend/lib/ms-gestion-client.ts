type MsProjectStatus = 'ACTIVE' | 'PAUSED'

type MsProject = {
  id: number
  name: string
  description?: string | null
  gitRepoUrl?: string | null
  gitDefaultBranch?: string | null
  status?: MsProjectStatus | null
  createdAt?: string | null
}

export type UiProjectType = 'WEB' | 'MOBILE' | 'API' | 'DESKTOP' | 'OTHER'
export type UiSourceType = 'GIT' | 'LOCAL'

export type UiProject = {
  id: number
  name: string
  description?: string | null
  repositoryUrl: string | null
  defaultBranch?: string | null
  projectType: UiProjectType
  sourceType: UiSourceType
  deployed: boolean
  createdAt?: string | null
  status?: MsProjectStatus | null
}

export type UiProjectCreatePayload = {
  name: string
  description?: string | null
  repositoryUrl?: string | null
  defaultBranch?: string | null
  projectType: UiProjectType
  sourceType: UiSourceType
  deployed: boolean
}

const BASE_URL = process.env.NEXT_PUBLIC_MS_GESTION_URL ?? ''

async function msRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${BASE_URL}${path}`
  const res = await fetch(url, {
    credentials: 'include',
    ...init,
    headers: {
      accept: 'application/json',
      ...(init?.headers ?? {}),
    },
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(text || `Request failed: ${res.status}`)
  }
  return (await res.json()) as T
}

function toUiProject(p: MsProject): UiProject {
  const repoUrl = p.gitRepoUrl ?? null
  return {
    id: p.id,
    name: p.name,
    description: p.description ?? null,
    repositoryUrl: repoUrl,
    defaultBranch: p.gitDefaultBranch ?? null,
    projectType: 'WEB',
    sourceType: repoUrl ? 'GIT' : 'LOCAL',
    deployed: p.status === 'ACTIVE',
    createdAt: p.createdAt ?? null,
    status: p.status ?? null,
  }
}

export async function msListProjects(): Promise<UiProject[]> {
  const data = await msRequest<MsProject[]>('/api/projects')
  return Array.isArray(data) ? data.map(toUiProject) : []
}

export async function msCreateProject(input: UiProjectCreatePayload): Promise<UiProject> {
  const repoUrl = input.sourceType === 'GIT' ? input.repositoryUrl?.trim() || null : null
  const defaultBranch = input.sourceType === 'GIT' ? input.defaultBranch?.trim() || null : null

  const payload = {
    name: input.name,
    description: input.description ?? null,
    gitRepoUrl: repoUrl,
    gitDefaultBranch: defaultBranch,
  }

  const created = await msRequest<MsProject>('/api/projects', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload),
  })

  return toUiProject(created)
}

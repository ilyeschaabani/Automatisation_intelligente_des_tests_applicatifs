import { getAccessToken } from '@/lib/auth-storage'

const API_BASE = '/api/kpi'

export interface TestMetric {
  testCaseId: number | null
  testCaseLabel: string
  averageDurationMs: number | null
  averageDuration: string | null
  failureCount: number | null
}

export interface Evolution {
  previousRate: number
  currentRate: number
  trend: 'UP' | 'DOWN' | 'STABLE'
}

export interface KpiResponse {
  totalTestsRun: number
  passedTests: number
  successRate: number
  failedTests: number
  activeCampaigns: number
  totalExecutionTime: string
  flakyTests: number
  averageDurationMs: number | null
  averageDuration: string | null
  top5SlowestTests: TestMetric[]
  top5FailingTests: TestMetric[]
  evolution: Evolution
}

export interface TrendPoint {
  label: string
  passed: number
  failed: number
  skipped: number
}

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const token = getAccessToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init?.headers instanceof Headers
      ? Object.fromEntries(init.headers.entries())
      : (init?.headers as Record<string, string>) || {}),
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(url, {
    ...init,
    headers,
  })

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}: ${response.statusText}`)
  }

  return response.json()
}

export async function getProjectKpi(projectId: number): Promise<KpiResponse> {
  return requestJson<KpiResponse>(`${API_BASE}/project/${projectId}`)
}

export async function getProjectTrend(projectId: number): Promise<TrendPoint[]> {
  return requestJson<TrendPoint[]>(`${API_BASE}/trend/${projectId}`)
}

export async function checkKpiHealth(): Promise<{ status: string; service: string }> {
  return requestJson(`${API_BASE}/health`)
}

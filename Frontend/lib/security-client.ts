import type {
  SecurityScore, SastScan, SastFinding, DastScan, DastFinding,
  OwaspCategory, Vulnerability, ComplianceFramework, TrendDataPoint, ScanRatePoint,
} from '@/types/security'

export interface SecurityDashboard {
  totalVulnerabilities: number
  severityCounts: Record<string, number>
  statusCounts: Record<string, number>
  securityScore: number
  scores: SecurityScore
  owaspTop10: { categories: OwaspCategory[] }
  recentScans: BackendScan[]
  totalScans: number
  compliance: BackendCompliance[]
}

interface BackendScan {
  id: number
  scanRef: string
  scanType: string
  engine: string
  status: string
  startedAt: string
  completedAt: string | null
  duration: string | null
  linesAnalyzed: number | null
  filesAnalyzed: number | null
  coverage: number | null
  branch: string | null
  commitHash: string | null
  targetUrl: string | null
}

interface BackendVuln {
  id: number
  title: string
  severity: string
  vulnType: string
  source: string
  status: string
  cweId: string | null
  owaspCategory: string | null
  file: string | null
  line: number | null
  snippet: string | null
  endpoint: string | null
  httpMethod: string | null
  parameter: string | null
  description: string | null
  risk: string | null
  recommendation: string | null
  fixExample: string | null
  evidence: string | null
  assignedTo: string | null
  createdAt: string
  updatedAt: string
}

interface BackendCompliance {
  id: number
  framework: string
  version: string
  score: number
  passedControls: number
  failedControls: number
  totalControls: number
  evaluatedAt: string
}

export async function fetchDashboard(projectId?: number): Promise<SecurityDashboard | null> {
  try {
    const url = projectId ? `/api/security/dashboard?projectId=${projectId}` : '/api/security/dashboard'
    const res = await fetch(url, { cache: 'no-store' })
    if (!res.ok) return null
    return await res.json()
  } catch {
    return null
  }
}

export async function fetchScans(type?: string): Promise<BackendScan[]> {
  try {
    const url = type ? `/api/security/scans?type=${type}` : '/api/security/scans'
    const res = await fetch(url, { cache: 'no-store' })
    if (!res.ok) return []
    return await res.json()
  } catch {
    return []
  }
}

export async function fetchVulnerabilities(projectId?: number): Promise<BackendVuln[]> {
  try {
    const url = projectId ? `/api/security/vulnerabilities?projectId=${projectId}` : '/api/security/vulnerabilities'
    const res = await fetch(url, { cache: 'no-store' })
    if (!res.ok) return []
    return await res.json()
  } catch {
    return []
  }
}

export async function fetchCompliance(projectId?: number): Promise<BackendCompliance[]> {
  try {
    const url = projectId ? `/api/security/compliance?projectId=${projectId}` : '/api/security/compliance'
    const res = await fetch(url, { cache: 'no-store' })
    if (!res.ok) return []
    return await res.json()
  } catch {
    return []
  }
}

export async function updateVulnStatus(id: number, status: string): Promise<boolean> {
  try {
    const res = await fetch(`/api/security/vulnerabilities/${id}`, {
      method: 'PATCH',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ status }),
    })
    return res.ok
  } catch {
    return false
  }
}

export async function assignVuln(
  id: number,
  member: { userId: number; name: string; email: string }
): Promise<boolean> {
  try {
    const res = await fetch(`/api/security/vulnerabilities/${id}`, {
      method: 'PATCH',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(member),
    })
    return res.ok
  } catch {
    return false
  }
}

// ── Scan launch (ms-execution) ──────────────────

export async function launchScan(
  type: 'sast' | 'dast' | 'sca',
  projectId: number,
  environmentId: number
): Promise<{ message: string } | null> {
  try {
    const res = await fetch(
      `/api/security/scan/${type}?projectId=${projectId}&environmentId=${environmentId}`,
      { method: 'POST' }
    )
    if (!res.ok) return null
    return await res.json()
  } catch {
    return null
  }
}

export async function fetchExecutionScans(projectId: number): Promise<BackendScan[]> {
  try {
    const res = await fetch(`/api/security/execution/scans?projectId=${projectId}`, { cache: 'no-store' })
    if (!res.ok) return []
    return await res.json()
  } catch {
    return []
  }
}

export async function fetchScanVulnerabilities(scanId: number): Promise<BackendVuln[]> {
  try {
    const res = await fetch(`/api/security/execution/vulnerabilities?scanId=${scanId}`, { cache: 'no-store' })
    if (!res.ok) return []
    return await res.json()
  } catch {
    return []
  }
}

// ── Mappers: backend → frontend types ──────────────────

export function mapScanToSast(scan: BackendScan, vulns: BackendVuln[]): SastScan {
  const scanVulns = vulns.filter(v => v.vulnType === 'SAST')
  return {
    id: scan.scanRef,
    date: scan.startedAt,
    engine: scan.engine.toLowerCase().replace(/\s+/g, '_') as any,
    status: scan.status.toLowerCase() as any,
    linesAnalyzed: scan.linesAnalyzed ?? 0,
    filesAnalyzed: scan.filesAnalyzed ?? 0,
    coverage: scan.coverage ?? 0,
    duration: scan.duration ?? '',
    branch: scan.branch ?? 'main',
    commit: scan.commitHash ?? '',
    findings: scanVulns.map(v => mapToSastFinding(v)),
  }
}

function mapToSastFinding(v: BackendVuln): SastFinding {
  return {
    id: `VULN-${v.id}`,
    title: v.title,
    severity: v.severity.toLowerCase() as any,
    cweId: v.cweId ?? '',
    owaspCategory: v.owaspCategory ?? '',
    file: v.file ?? '',
    line: v.line ?? 0,
    snippet: v.snippet ?? '',
    recommendation: v.recommendation ?? '',
    fixExample: v.fixExample ?? '',
    status: v.status.toLowerCase() as any,
  }
}

export function mapScanToDast(scan: BackendScan, vulns: BackendVuln[]): DastScan {
  const scanVulns = vulns.filter(v => v.vulnType === 'DAST')
  return {
    id: scan.scanRef,
    date: scan.startedAt,
    engine: scan.engine.toLowerCase().replace(/\s+/g, '_') as any,
    targetUrl: scan.targetUrl ?? '',
    status: scan.status.toLowerCase() as any,
    duration: scan.duration ?? '',
    findings: scanVulns.map(v => ({
      id: `VULN-${v.id}`,
      title: v.title,
      severity: v.severity.toLowerCase() as any,
      endpoint: v.endpoint ?? '',
      method: v.httpMethod ?? 'GET',
      parameter: v.parameter ?? '',
      description: v.description ?? '',
      risk: v.risk ?? '',
      recommendation: v.recommendation ?? '',
      evidence: v.evidence ?? '',
      status: v.status.toLowerCase() as any,
      assignedTo: v.assignedTo ?? undefined,
    })),
  }
}

export function mapToVulnerability(v: BackendVuln): Vulnerability {
  return {
    id: `VULN-${String(v.id).padStart(3, '0')}`,
    rawId: v.id,
    title: v.title,
    severity: v.severity.toLowerCase() as any,
    type: v.vulnType as any,
    source: v.source.toLowerCase().replace(/\s+/g, '_') as any,
    status: v.status.toLowerCase() as any,
    assignedTo: v.assignedTo ?? 'Unassigned',
    createdDate: v.createdAt?.split('T')[0] ?? '',
    lastUpdate: v.updatedAt?.split('T')[0] ?? '',
    cweId: v.cweId ?? undefined,
    file: v.file ? `${v.file}${v.line ? ':' + v.line : ''}` : undefined,
    endpoint: v.endpoint ? `${v.httpMethod ?? ''} ${v.endpoint}`.trim() : undefined,
  }
}

export function mapToCompliance(c: BackendCompliance): ComplianceFramework {
  return {
    name: c.framework,
    version: c.version,
    score: c.score,
    passedControls: c.passedControls,
    failedControls: c.failedControls,
    totalControls: c.totalControls,
  }
}

export type Severity = 'critical' | 'high' | 'medium' | 'low' | 'info'

export type VulnStatus = 'open' | 'in_progress' | 'resolved' | 'closed' | 'false_positive'

export type ScanEngine = 'sonarqube' | 'semgrep' | 'codeql' | 'owasp_zap' | 'burp_suite' | 'dependency_check'

export type ScanType = 'SAST' | 'DAST' | 'SCA'

export interface SecurityScore {
  overall: number
  owasp: number
  secureCoding: number
  infrastructure: number
  dependencies: number
}

export interface SastScan {
  id: string
  date: string
  engine: ScanEngine
  status: 'running' | 'completed' | 'failed' | 'scheduled'
  linesAnalyzed: number
  filesAnalyzed: number
  coverage: number
  duration: string
  branch: string
  commit: string
  findings: SastFinding[]
}

export interface SastFinding {
  id: string
  title: string
  severity: Severity
  cweId: string
  owaspCategory: string
  file: string
  line: number
  snippet: string
  recommendation: string
  fixExample: string
  status: VulnStatus
}

export interface DastScan {
  id: string
  date: string
  engine: ScanEngine
  targetUrl: string
  status: 'running' | 'completed' | 'failed'
  duration: string
  findings: DastFinding[]
}

export interface DastFinding {
  id: string
  title: string
  severity: Severity
  endpoint: string
  method: string
  parameter: string
  description: string
  risk: string
  recommendation: string
  evidence: string
  status: VulnStatus
  assignedTo?: string
}

export interface OwaspCategory {
  id: string
  name: string
  description: string
  findingsCount: number
  riskScore: number
  status: 'pass' | 'warn' | 'fail'
}

export interface Vulnerability {
  id: string
  rawId: number
  title: string
  severity: Severity
  type: ScanType
  source: ScanEngine
  status: VulnStatus
  assignedTo: string
  createdDate: string
  lastUpdate: string
  cweId?: string
  file?: string
  endpoint?: string
}

export interface ComplianceFramework {
  name: string
  version: string
  score: number
  passedControls: number
  failedControls: number
  totalControls: number
}

export interface PipelineStage {
  name: string
  status: 'success' | 'failed' | 'running' | 'pending' | 'skipped'
  duration?: string
  findings?: number
  tool?: string
}

export interface TrendDataPoint {
  date: string
  critical: number
  high: number
  medium: number
  low: number
  total: number
}

export interface ScanRatePoint {
  date: string
  passed: number
  failed: number
  total: number
}

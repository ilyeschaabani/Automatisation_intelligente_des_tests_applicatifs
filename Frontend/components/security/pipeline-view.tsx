'use client'

import { useMemo } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  CheckCircle2, XCircle, Loader2, Clock, ArrowDown,
  Workflow, Code, Bug, Package, ShieldCheck,
} from 'lucide-react'
import type { Vulnerability } from '@/types/security'

interface Props {
  scans: any[]
  vulns: Vulnerability[]
}

interface Stage {
  name: string
  status: 'success' | 'failed' | 'running' | 'pending' | 'skipped'
  tool: string
  duration?: string
  findings?: number
}

function StageIcon({ status }: { status: Stage['status'] }) {
  switch (status) {
    case 'success': return <CheckCircle2 className="h-5 w-5 text-green-500" />
    case 'failed': return <XCircle className="h-5 w-5 text-red-500" />
    case 'running': return <Loader2 className="h-5 w-5 text-blue-500 animate-spin" />
    case 'pending': return <Clock className="h-5 w-5 text-muted-foreground" />
    case 'skipped': return <Clock className="h-5 w-5 text-muted-foreground opacity-50" />
  }
}

function stageBg(status: Stage['status']) {
  switch (status) {
    case 'success': return 'border-green-500/30 bg-green-500/5'
    case 'failed': return 'border-red-500/30 bg-red-500/5'
    case 'running': return 'border-blue-500/30 bg-blue-500/5'
    case 'pending': return 'border-border bg-muted/30'
    case 'skipped': return 'border-border bg-muted/20 opacity-50'
  }
}

function mapScanStatus(s: string): Stage['status'] {
  switch (s) {
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'failed'
    case 'RUNNING': return 'running'
    default: return 'pending'
  }
}

export function PipelineView({ scans, vulns }: Props) {
  const stages = useMemo(() => {
    const latestByType = new Map<string, any>()
    for (const scan of scans) {
      const type = scan.scanType
      const existing = latestByType.get(type)
      if (!existing || new Date(scan.startedAt) > new Date(existing.startedAt)) {
        latestByType.set(type, scan)
      }
    }

    const result: Stage[] = []

    const sast = latestByType.get('SAST')
    result.push({
      name: 'SAST Scan',
      status: sast ? mapScanStatus(sast.status) : 'skipped',
      tool: sast?.engine || 'Semgrep',
      duration: sast?.duration || undefined,
      findings: sast ? vulns.filter(v => v.type === 'SAST').length : undefined,
    })

    const sca = latestByType.get('SCA')
    result.push({
      name: 'Dependency Scan (SCA)',
      status: sca ? mapScanStatus(sca.status) : 'skipped',
      tool: sca?.engine || 'OWASP Dependency-Check',
      duration: sca?.duration || undefined,
      findings: sca ? vulns.filter(v => v.type === 'SCA').length : undefined,
    })

    const dast = latestByType.get('DAST')
    result.push({
      name: 'DAST Scan',
      status: dast ? mapScanStatus(dast.status) : 'skipped',
      tool: dast?.engine || 'OWASP ZAP',
      duration: dast?.duration || undefined,
      findings: dast ? vulns.filter(v => v.type === 'DAST').length : undefined,
    })

    const critical = vulns.filter(v => v.severity === 'critical').length
    const high = vulns.filter(v => v.severity === 'high').length
    const hasScans = sast || sca || dast
    const gatePass = hasScans && critical === 0 && high <= 2

    result.push({
      name: 'Security Gate',
      status: !hasScans ? 'pending' : gatePass ? 'success' : 'failed',
      tool: 'Policy: 0 critical, max 2 high',
    })

    return result
  }, [scans, vulns])

  const failedGate = stages.find(s => s.name === 'Security Gate')?.status === 'failed'
  const critical = vulns.filter(v => v.severity === 'critical').length
  const high = vulns.filter(v => v.severity === 'high').length

  if (scans.length === 0) {
    return (
      <Card>
        <CardContent className="py-12 text-center text-muted-foreground">
          <Workflow className="h-8 w-8 mx-auto mb-3 opacity-40" />
          <p className="text-sm">Le pipeline apparaitra apres le premier scan</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold flex items-center gap-2">
          <Workflow className="h-5 w-5 text-primary" />
          Security Pipeline
        </h2>
        <Badge variant="outline" className={`text-xs ${failedGate ? 'text-red-600 border-red-300' : 'text-green-600 border-green-300'}`}>
          Gate: {failedGate ? 'BLOCKED' : stages.find(s => s.name === 'Security Gate')?.status === 'pending' ? 'EN ATTENTE' : 'PASSED'}
        </Badge>
      </div>

      <Card>
        <CardContent className="py-6">
          <div className="flex flex-col items-center gap-1">
            {stages.map((stage, i) => (
              <div key={stage.name} className="w-full max-w-lg">
                <div className={`border rounded-lg p-4 flex items-center gap-4 ${stageBg(stage.status)}`}>
                  <StageIcon status={stage.status} />
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-sm">{stage.name}</p>
                    <div className="flex items-center gap-3 text-xs text-muted-foreground mt-0.5">
                      <span>{stage.tool}</span>
                      {stage.duration && <span>{stage.duration}</span>}
                    </div>
                  </div>
                  {stage.findings !== undefined && (
                    <Badge variant="outline" className={`text-xs ${
                      stage.findings === 0 ? 'text-green-600 border-green-300' : 'text-orange-600 border-orange-300'
                    }`}>
                      {stage.findings} findings
                    </Badge>
                  )}
                  <Badge variant="outline" className={`text-[10px] ${
                    stage.status === 'success' ? 'text-green-600 border-green-300' :
                    stage.status === 'failed' ? 'text-red-600 border-red-300' :
                    stage.status === 'running' ? 'text-blue-600 border-blue-300' :
                    'text-muted-foreground'
                  }`}>
                    {stage.status.toUpperCase()}
                  </Badge>
                </div>
                {i < stages.length - 1 && (
                  <div className="flex justify-center py-1">
                    <ArrowDown className="h-4 w-4 text-muted-foreground" />
                  </div>
                )}
              </div>
            ))}
          </div>

          {failedGate && (
            <div className="mt-6 bg-red-500/10 border border-red-500/30 rounded-lg p-4 max-w-lg mx-auto">
              <div className="flex items-start gap-3">
                <XCircle className="h-5 w-5 text-red-500 shrink-0 mt-0.5" />
                <div>
                  <p className="font-semibold text-sm text-red-600 dark:text-red-400">Security Gate Failed</p>
                  <p className="text-xs text-muted-foreground mt-1">
                    Deploiement bloque : {critical} critical + {high} high vulnerabilites detectees.
                  </p>
                  <p className="text-xs text-muted-foreground mt-1">
                    Seuil: 0 critical, max 2 high autorisees.
                  </p>
                </div>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

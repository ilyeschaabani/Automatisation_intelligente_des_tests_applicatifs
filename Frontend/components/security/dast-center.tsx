'use client'

import { useState, useCallback } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Bug, ChevronDown, ChevronUp, Globe, Clock,
  Download, UserPlus, CheckCircle2, ExternalLink, Scan,
} from 'lucide-react'
import { ScanTabHeader, type ScanRecord, ScanStatusBadge } from './scan-tab-header'
import { AssignDialog } from './assign-dialog'
import { fetchScanVulnerabilities, updateVulnStatus, assignVuln } from '@/lib/security-client'

interface VulnRecord {
  id: number
  title: string
  severity: string
  cweId: string | null
  owaspCategory: string | null
  endpoint: string | null
  httpMethod: string | null
  parameter: string | null
  description: string | null
  risk: string | null
  recommendation: string | null
  evidence: string | null
  status: string
  assignedTo: string | null
}

function sevColor(s: string) {
  switch (s) {
    case 'CRITICAL': return 'text-red-600 border-red-300 bg-red-50 dark:bg-red-950'
    case 'HIGH': return 'text-orange-600 border-orange-300 bg-orange-50 dark:bg-orange-950'
    case 'MEDIUM': return 'text-yellow-600 border-yellow-300 bg-yellow-50 dark:bg-yellow-950'
    case 'LOW': return 'text-blue-600 border-blue-300 bg-blue-50 dark:bg-blue-950'
    default: return 'text-gray-600 border-gray-300'
  }
}

function sevBorder(s: string) {
  switch (s) {
    case 'CRITICAL': return 'border-l-red-500'
    case 'HIGH': return 'border-l-orange-500'
    case 'MEDIUM': return 'border-l-yellow-500'
    case 'LOW': return 'border-l-blue-500'
    default: return 'border-l-gray-300'
  }
}

function DastFindingCard({ v, onResolve, onAssign }: {
  v: VulnRecord
  onResolve: (id: number) => void
  onAssign: (id: number) => void
}) {
  const [open, setOpen] = useState(false)
  return (
    <div className={`border rounded-lg p-4 border-l-4 transition-all hover:shadow-sm ${sevBorder(v.severity)}`}>
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap mb-1">
            <h4 className="font-semibold text-sm">{v.title}</h4>
            <Badge variant="outline" className={`text-[10px] ${sevColor(v.severity)}`}>{v.severity}</Badge>
            <Badge variant="outline" className="text-[10px]">{v.status}</Badge>
          </div>
          <div className="flex items-center gap-4 text-xs text-muted-foreground mt-1">
            {v.endpoint && (
              <span className="font-mono">{v.httpMethod ?? 'GET'} {v.endpoint}</span>
            )}
            {v.parameter && (
              <span>Param: <code className="bg-muted px-1 rounded">{v.parameter}</code></span>
            )}
            {v.assignedTo && (
              <span className="flex items-center gap-1"><UserPlus className="h-3 w-3" />{v.assignedTo}</span>
            )}
          </div>
        </div>
        <Button variant="ghost" size="sm" onClick={() => setOpen(!open)}>
          {open ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
        </Button>
      </div>
      {open && (
        <div className="mt-3 space-y-3 pt-3 border-t">
          {v.description && (
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-1">DESCRIPTION</p>
              <p className="text-xs leading-relaxed">{v.description}</p>
            </div>
          )}
          {v.risk && (
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-1">RISQUE</p>
              <p className="text-xs text-red-600 dark:text-red-400">{v.risk}</p>
            </div>
          )}
          {v.evidence && (
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-1">EVIDENCE</p>
              <pre className="text-xs bg-red-500/5 border border-red-500/20 rounded p-2 font-mono overflow-x-auto whitespace-pre-wrap">{v.evidence}</pre>
            </div>
          )}
          {v.recommendation && (
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-1">RECOMMENDATION</p>
              <div className="text-xs bg-green-500/5 border border-green-500/20 rounded p-2 leading-relaxed">{v.recommendation}</div>
            </div>
          )}
          <div className="flex gap-2">
            {v.cweId && (
              <a href={`https://cwe.mitre.org/data/definitions/${v.cweId.replace(/\D/g, '')}.html`} target="_blank" rel="noopener noreferrer">
                <Button variant="outline" size="sm" className="text-xs h-7"><ExternalLink className="h-3 w-3 mr-1" />Détails CWE</Button>
              </a>
            )}
            <Button variant="outline" size="sm" className="text-xs h-7" onClick={() => onAssign(v.id)}>
              <UserPlus className="h-3 w-3 mr-1" />Assigner
            </Button>
            <Button variant="outline" size="sm" className="text-xs h-7" disabled={v.status === 'RESOLVED'} onClick={() => onResolve(v.id)}>
              <CheckCircle2 className="h-3 w-3 mr-1" />{v.status === 'RESOLVED' ? 'Résolu ✓' : 'Marquer résolu'}
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

export function DastCenter({ projectId: externalProjectId }: { projectId?: string }) {
  const [scans, setScans] = useState<ScanRecord[]>([])
  const [vulns, setVulns] = useState<VulnRecord[]>([])
  const [assignTarget, setAssignTarget] = useState<number | null>(null)
  const [internalProjectId, setInternalProjectId] = useState<string>('')
  const effectiveProjectId = internalProjectId || externalProjectId

  const handleScansLoaded = useCallback(async (loaded: ScanRecord[]) => {
    setScans(loaded)
    const completed = loaded.find(s => s.status === 'COMPLETED')
    if (completed) {
      const v = await fetchScanVulnerabilities(completed.id)
      setVulns(v as unknown as VulnRecord[])
    } else {
      setVulns([])
    }
  }, [])

  const latest = scans.find(s => s.status === 'COMPLETED')
  const critical = vulns.filter(v => v.severity === 'CRITICAL').length
  const high = vulns.filter(v => v.severity === 'HIGH').length
  const medium = vulns.filter(v => v.severity === 'MEDIUM').length
  const low = vulns.filter(v => v.severity === 'LOW').length

  const handleResolve = useCallback(async (id: number) => {
    const ok = await updateVulnStatus(id, 'RESOLVED')
    if (ok) setVulns(prev => prev.map(x => x.id === id ? { ...x, status: 'RESOLVED' } : x))
  }, [])

  const handleAssign = useCallback((id: number) => {
    setAssignTarget(id)
  }, [])

  const handleAssignConfirm = useCallback(async (member: { userId: number; name: string; email: string }) => {
    if (assignTarget === null) return
    const ok = await assignVuln(assignTarget, member)
    if (ok) setVulns(prev => prev.map(x => x.id === assignTarget ? { ...x, assignedTo: member.name } : x))
    setAssignTarget(null)
  }, [assignTarget])

  return (
    <div className="space-y-6">
      <ScanTabHeader
        scanType="dast"
        icon={<Bug className="h-5 w-5 text-red-500" />}
        title="DAST — Dynamic Application Security Testing"
        subtitle="Analyse dynamique de l'application en cours d'execution via OWASP ZAP"
        accentColor="bg-red-600 hover:bg-red-700 text-white"
        onScansLoaded={handleScansLoaded}
        onProjectChange={setInternalProjectId}
      />

      {latest && (
        <>
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">ENGINE</p>
              <p className="font-semibold text-sm mt-1">{latest.engine}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">CIBLE</p>
              <p className="font-semibold text-sm mt-1 flex items-center gap-1 truncate">
                <Globe className="h-3 w-3 shrink-0" />{latest.targetUrl ?? '—'}
              </p>
            </Card>
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">STATUS</p>
              <ScanStatusBadge status={latest.status} />
            </Card>
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">DUREE</p>
              <p className="font-semibold text-sm mt-1 flex items-center gap-1"><Clock className="h-3 w-3" />{latest.duration}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">FINDINGS</p>
              <p className="font-semibold text-sm mt-1">{vulns.length}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">DATE</p>
              <p className="font-semibold text-sm mt-1">
                {latest.startedAt ? new Date(latest.startedAt).toLocaleDateString('fr-FR') : '—'}
              </p>
            </Card>
          </div>

          <div className="grid grid-cols-4 gap-3">
            {[
              { label: 'Critical', count: critical, cls: 'bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20' },
              { label: 'High', count: high, cls: 'bg-orange-500/10 text-orange-600 dark:text-orange-400 border-orange-500/20' },
              { label: 'Medium', count: medium, cls: 'bg-yellow-500/10 text-yellow-600 dark:text-yellow-400 border-yellow-500/20' },
              { label: 'Low', count: low, cls: 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20' },
            ].map(s => (
              <div key={s.label} className={`rounded-lg border p-3 text-center ${s.cls}`}>
                <p className="text-2xl font-bold">{s.count}</p>
                <p className="text-xs font-medium">{s.label}</p>
              </div>
            ))}
          </div>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base flex items-center gap-2">
                <Scan className="h-4 w-4" />
                Findings — {latest.scanRef}
                <Badge variant="outline" className="text-[10px] ml-2">{vulns.length} alertes</Badge>
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {vulns.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-8">Aucune alerte detectee</p>
              ) : (
                vulns.map(v => <DastFindingCard key={v.id} v={v} onResolve={handleResolve} onAssign={handleAssign} />)
              )}
            </CardContent>
          </Card>
        </>
      )}

      {scans.length > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Historique des scans DAST</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              {scans.map(s => (
                <div key={s.id} className="flex items-center justify-between py-2 px-3 rounded-lg hover:bg-muted/50 transition-colors">
                  <div className="flex items-center gap-3">
                    <ScanStatusBadge status={s.status} />
                    <span className="font-mono text-sm">{s.scanRef}</span>
                    <span className="text-sm text-muted-foreground">{s.engine}</span>
                  </div>
                  <div className="flex items-center gap-4 text-sm text-muted-foreground">
                    {s.targetUrl && <span className="flex items-center gap-1"><Globe className="h-3 w-3" />{s.targetUrl}</span>}
                    {s.duration && <span>{s.duration}</span>}
                    {s.startedAt && <span>{new Date(s.startedAt).toLocaleDateString('fr-FR')}</span>}
                    {s.status === 'COMPLETED' && (
                      <a href={`/api/security/report/${s.id}`} target="_blank" rel="noopener">
                        <Button variant="ghost" size="sm"><Download className="h-4 w-4" /></Button>
                      </a>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {scans.length === 0 && (
        <div className="text-center py-16 text-muted-foreground">
          <Bug className="h-12 w-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">Aucun scan DAST</p>
          <p className="text-sm mt-1">Selectionnez un projet et un environnement puis lancez votre premier scan</p>
        </div>
      )}

      {effectiveProjectId && (
        <AssignDialog
          open={assignTarget !== null}
          onOpenChange={(open) => { if (!open) setAssignTarget(null) }}
          projectId={effectiveProjectId}
          onAssign={handleAssignConfirm}
        />
      )}
    </div>
  )
}

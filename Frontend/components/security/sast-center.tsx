'use client'

import { useState, useCallback } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Code, ChevronDown, ChevronUp, FileCode, Scan, GitBranch,
  Clock, FileText, Download,
} from 'lucide-react'
import { ScanTabHeader, type ScanRecord, ScanStatusBadge } from './scan-tab-header'
import { fetchScanVulnerabilities } from '@/lib/security-client'

interface VulnRecord {
  id: number
  title: string
  severity: string
  cweId: string | null
  owaspCategory: string | null
  file: string | null
  line: number | null
  snippet: string | null
  description: string | null
  recommendation: string | null
  fixExample: string | null
  status: string
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

function FindingCard({ v }: { v: VulnRecord }) {
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
          <div className="flex items-center gap-4 text-xs text-muted-foreground">
            {v.cweId && <span className="font-mono">{v.cweId}</span>}
            {v.owaspCategory && <span>OWASP {v.owaspCategory}</span>}
          </div>
          {v.file && (
            <div className="mt-2 text-xs font-mono bg-muted/50 rounded px-2 py-1 flex items-center gap-2">
              <FileCode className="h-3 w-3 shrink-0" />
              <span className="truncate">{v.file}{v.line ? `:${v.line}` : ''}</span>
            </div>
          )}
        </div>
        <Button variant="ghost" size="sm" onClick={() => setOpen(!open)}>
          {open ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
        </Button>
      </div>
      {open && (
        <div className="mt-3 space-y-3 pt-3 border-t">
          {v.snippet && (
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-1">CODE VULNERABLE</p>
              <pre className="text-xs bg-red-500/5 border border-red-500/20 rounded p-2 overflow-x-auto font-mono whitespace-pre-wrap">{v.snippet}</pre>
            </div>
          )}
          {v.description && (
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-1">DESCRIPTION</p>
              <p className="text-xs leading-relaxed">{v.description}</p>
            </div>
          )}
          {v.recommendation && (
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-1">RECOMMENDATION</p>
              <div className="text-xs bg-green-500/5 border border-green-500/20 rounded p-2 leading-relaxed">{v.recommendation}</div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export function SastCenter() {
  const [scans, setScans] = useState<ScanRecord[]>([])
  const [vulns, setVulns] = useState<VulnRecord[]>([])

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

  return (
    <div className="space-y-6">
      <ScanTabHeader
        scanType="sast"
        icon={<Code className="h-5 w-5 text-violet-500" />}
        title="SAST — Static Application Security Testing"
        subtitle="Analyse statique du code source via Semgrep (2500+ regles)"
        accentColor="bg-violet-600 hover:bg-violet-700 text-white"
        onScansLoaded={handleScansLoaded}
      />

      {latest && (
        <>
          {/* Metrics */}
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-3">
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">ENGINE</p>
              <p className="font-semibold text-sm mt-1">{latest.engine}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">LIGNES ANALYSEES</p>
              <p className="font-semibold text-sm mt-1">{latest.linesAnalyzed?.toLocaleString() ?? '—'}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">FICHIERS</p>
              <p className="font-semibold text-sm mt-1">{latest.filesAnalyzed ?? '—'}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">COUVERTURE</p>
              <div className="flex items-center gap-2 mt-1">
                <div className="flex-1 h-2 rounded-full bg-secondary">
                  <div className="h-full rounded-full bg-green-500" style={{ width: `${latest.coverage ?? 0}%` }} />
                </div>
                <span className="font-semibold text-sm">{latest.coverage ?? 0}%</span>
              </div>
            </Card>
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">DUREE</p>
              <p className="font-semibold text-sm mt-1 flex items-center gap-1"><Clock className="h-3 w-3" />{latest.duration}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">BRANCHE</p>
              <p className="font-semibold text-sm mt-1 flex items-center gap-1"><GitBranch className="h-3 w-3" />{latest.branch ?? '—'}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[10px] text-muted-foreground font-medium">COMMIT</p>
              <p className="font-semibold text-sm mt-1 font-mono">{latest.commitHash ?? '—'}</p>
            </Card>
          </div>

          {/* Severity counts */}
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

          {/* Findings */}
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base flex items-center gap-2">
                <Scan className="h-4 w-4" />
                Findings — {latest.scanRef}
                <Badge variant="outline" className="text-[10px] ml-2">{vulns.length} vulnerabilites</Badge>
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {vulns.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-8">Aucune vulnerabilite detectee</p>
              ) : (
                vulns.map(v => <FindingCard key={v.id} v={v} />)
              )}
            </CardContent>
          </Card>
        </>
      )}

      {/* Scan History */}
      {scans.length > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Historique des scans SAST</CardTitle>
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
                    {s.branch && <span className="flex items-center gap-1"><GitBranch className="h-3 w-3" />{s.branch}</span>}
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
          <Code className="h-12 w-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">Aucun scan SAST</p>
          <p className="text-sm mt-1">Selectionnez un projet et un environnement puis lancez votre premier scan</p>
        </div>
      )}
    </div>
  )
}

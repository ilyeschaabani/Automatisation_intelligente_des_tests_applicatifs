'use client'

import { useEffect, useState, useCallback } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { ShieldCheck, ShieldAlert, ShieldX, RefreshCw, Loader2 } from 'lucide-react'
import { projectService } from '@/services/projects'
import { fetchExecutionScans, fetchScanVulnerabilities } from '@/lib/security-client'
import type { Project } from '@/types/ms-gestion'
import type { OwaspCategory } from '@/types/security'

const OWASP_CATALOG: { id: string; name: string; description: string }[] = [
  { id: 'A01', name: 'Broken Access Control', description: 'Restrictions on authenticated users are not properly enforced' },
  { id: 'A02', name: 'Cryptographic Failures', description: 'Failures related to cryptography leading to sensitive data exposure' },
  { id: 'A03', name: 'Injection', description: 'SQL, NoSQL, OS, LDAP injection when untrusted data is sent to an interpreter' },
  { id: 'A04', name: 'Insecure Design', description: 'Missing or ineffective control design leading to architectural weaknesses' },
  { id: 'A05', name: 'Security Misconfiguration', description: 'Missing hardening, open cloud storage, verbose error messages' },
  { id: 'A06', name: 'Vulnerable & Outdated Components', description: 'Using components with known vulnerabilities' },
  { id: 'A07', name: 'Identification & Authentication Failures', description: 'Confirmation of identity, authentication, and session management weaknesses' },
  { id: 'A08', name: 'Software & Data Integrity Failures', description: 'Code and infrastructure that does not protect against integrity violations' },
  { id: 'A09', name: 'Security Logging & Monitoring Failures', description: 'Insufficient logging, detection, monitoring, and active response' },
  { id: 'A10', name: 'Server-Side Request Forgery', description: 'SSRF when a web application fetches a remote resource without validating the URL' },
]

interface BackendVuln {
  id: number
  severity: string
  owaspCategory: string | null
}

function computeOwaspFromVulns(vulns: BackendVuln[]): OwaspCategory[] {
  return OWASP_CATALOG.map(cat => {
    // owaspCategory côté backend = "A03:2021 Injection" → on compare le préfixe "A03"
    const matched = vulns.filter(v => {
      const m = v.owaspCategory ? String(v.owaspCategory).match(/A\d{2}/i) : null
      return m ? m[0].toUpperCase() === cat.id : false
    })
    const count = matched.length

    const severityWeights: Record<string, number> = {
      CRITICAL: 25, HIGH: 15, MEDIUM: 8, LOW: 3, INFO: 1,
    }
    const totalWeight = matched.reduce((sum, v) => sum + (severityWeights[v.severity] || 5), 0)
    const riskScore = Math.max(0, Math.min(100, 100 - totalWeight))

    const status: 'pass' | 'warn' | 'fail' =
      count === 0 ? 'pass' :
      matched.some(v => v.severity === 'CRITICAL' || v.severity === 'HIGH') ? 'fail' : 'warn'

    return {
      id: cat.id,
      name: cat.name,
      description: cat.description,
      findingsCount: count,
      riskScore,
      status,
    }
  })
}

function RiskBar({ score }: { score: number }) {
  const color = score >= 85 ? 'bg-green-500' : score >= 65 ? 'bg-yellow-500' : 'bg-red-500'
  return (
    <div className="flex items-center gap-2 w-full">
      <div className="flex-1 h-2.5 rounded-full bg-secondary overflow-hidden">
        <div className={`h-full rounded-full transition-all ${color}`} style={{ width: `${score}%` }} />
      </div>
      <span className={`text-xs font-bold w-8 text-right ${
        score >= 85 ? 'text-green-600 dark:text-green-400' : score >= 65 ? 'text-yellow-600 dark:text-yellow-400' : 'text-red-600 dark:text-red-400'
      }`}>{score}</span>
    </div>
  )
}

function StatusIcon({ status }: { status: string }) {
  if (status === 'pass') return <ShieldCheck className="h-4 w-4 text-green-500" />
  if (status === 'warn') return <ShieldAlert className="h-4 w-4 text-yellow-500" />
  return <ShieldX className="h-4 w-4 text-red-500" />
}

export function OwaspTable({ categories: staticCategories }: { categories?: OwaspCategory[] }) {
  const [projects, setProjects] = useState<Project[]>([])
  const [selectedProject, setSelectedProject] = useState<string>('')
  const [categories, setCategories] = useState<OwaspCategory[]>(staticCategories ?? [])
  const [loading, setLoading] = useState(false)
  const [dataSource, setDataSource] = useState<'static' | 'live'>('static')

  useEffect(() => {
    projectService.getAll().then(setProjects).catch(() => {})
  }, [])

  useEffect(() => {
    if (staticCategories && !selectedProject) {
      setCategories(staticCategories)
      setDataSource('static')
    }
  }, [staticCategories, selectedProject])

  const loadFromScans = useCallback(async () => {
    if (!selectedProject) return
    setLoading(true)
    try {
      const scans = await fetchExecutionScans(Number(selectedProject))
      const completedScans = scans.filter((s: any) => s.status === 'COMPLETED')

      const allVulns: BackendVuln[] = []
      for (const scan of completedScans) {
        const v = await fetchScanVulnerabilities(scan.id)
        allVulns.push(...(v as unknown as BackendVuln[]))
      }

      setCategories(computeOwaspFromVulns(allVulns))
      setDataSource(allVulns.length > 0 ? 'live' : 'static')
    } catch {
      if (staticCategories) setCategories(staticCategories)
    } finally {
      setLoading(false)
    }
  }, [selectedProject, staticCategories])

  useEffect(() => {
    if (selectedProject) loadFromScans()
  }, [selectedProject, loadFromScans])

  const totalFindings = categories.reduce((s, c) => s + c.findingsCount, 0)
  const passCount = categories.filter(c => c.status === 'pass').length
  const failCount = categories.filter(c => c.status === 'fail').length

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-primary" />
            OWASP Top 10 — 2021
            <Badge variant="outline" className="text-[10px] ml-2">
              {totalFindings} findings
            </Badge>
            {dataSource === 'live' && (
              <Badge className="text-[10px] bg-emerald-100 text-emerald-700 border-emerald-200">Live</Badge>
            )}
          </CardTitle>
          <div className="flex items-center gap-2">
            <Select value={selectedProject} onValueChange={setSelectedProject}>
              <SelectTrigger className="h-8 w-[180px] text-xs">
                <SelectValue placeholder="Projet (tous)" />
              </SelectTrigger>
              <SelectContent>
                {projects.map(p => (
                  <SelectItem key={p.id} value={String(p.id)}>{p.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button variant="ghost" size="sm" onClick={loadFromScans} disabled={!selectedProject || loading}>
              {loading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RefreshCw className="h-3.5 w-3.5" />}
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="flex gap-4 mb-4 text-xs">
          <div className="flex items-center gap-1.5">
            <ShieldCheck className="h-3.5 w-3.5 text-green-500" />
            <span className="text-muted-foreground">{passCount} conformes</span>
          </div>
          <div className="flex items-center gap-1.5">
            <ShieldX className="h-3.5 w-3.5 text-red-500" />
            <span className="text-muted-foreground">{failCount} a risque</span>
          </div>
          <div className="flex items-center gap-1.5">
            <ShieldAlert className="h-3.5 w-3.5 text-yellow-500" />
            <span className="text-muted-foreground">{10 - passCount - failCount} attention</span>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-muted-foreground text-xs">
                <th className="text-left py-2 pr-3 font-medium">STATUS</th>
                <th className="text-left py-2 pr-3 font-medium">CATEGORY</th>
                <th className="text-left py-2 pr-3 font-medium hidden md:table-cell">DESCRIPTION</th>
                <th className="text-center py-2 pr-3 font-medium">FINDINGS</th>
                <th className="text-left py-2 font-medium w-40">RISK SCORE</th>
              </tr>
            </thead>
            <tbody>
              {categories.map(cat => (
                <tr key={cat.id} className="border-b last:border-0 hover:bg-muted/50 transition-colors">
                  <td className="py-3 pr-3"><StatusIcon status={cat.status} /></td>
                  <td className="py-3 pr-3">
                    <div>
                      <span className="font-mono text-xs text-muted-foreground mr-2">{cat.id}</span>
                      <span className="font-medium">{cat.name}</span>
                    </div>
                  </td>
                  <td className="py-3 pr-3 text-muted-foreground text-xs hidden md:table-cell max-w-xs truncate">{cat.description}</td>
                  <td className="py-3 pr-3 text-center">
                    <Badge variant="outline" className={
                      cat.findingsCount === 0 ? 'text-green-600 border-green-300' :
                      cat.findingsCount <= 2 ? 'text-yellow-600 border-yellow-300' :
                      'text-red-600 border-red-300'
                    }>
                      {cat.findingsCount}
                    </Badge>
                  </td>
                  <td className="py-3"><RiskBar score={cat.riskScore} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  )
}

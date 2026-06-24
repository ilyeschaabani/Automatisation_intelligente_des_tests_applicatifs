'use client'

import { useMemo } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { CheckCircle2, XCircle, ClipboardCheck } from 'lucide-react'

const OWASP_CATALOG = [
  { id: 'A01', name: 'Broken Access Control' },
  { id: 'A02', name: 'Cryptographic Failures' },
  { id: 'A03', name: 'Injection' },
  { id: 'A04', name: 'Insecure Design' },
  { id: 'A05', name: 'Security Misconfiguration' },
  { id: 'A06', name: 'Vulnerable Components' },
  { id: 'A07', name: 'Auth Failures' },
  { id: 'A08', name: 'Software Integrity' },
  { id: 'A09', name: 'Logging Failures' },
  { id: 'A10', name: 'SSRF' },
]

// Score pondéré par sévérité : 100% = zéro finding ; chaque vuln pénalise selon sa gravité.
function weightedScore(vulns: any[]): number {
  const n = (s: string) => vulns.filter(v => (v.severity || '').toUpperCase() === s).length
  return Math.max(0, 100 - (n('CRITICAL') * 15 + n('HIGH') * 12 + n('MEDIUM') * 4 + n('LOW') * 1))
}

interface ComplianceFramework {
  name: string
  version: string
  score: number
  passedControls: number
  failedControls: number
  totalControls: number
}

interface Props {
  rawVulns: any[]
  scans: any[]
}

function ScoreRing({ score, size = 80 }: { score: number; size?: number }) {
  const radius = (size - 10) / 2
  const circumference = 2 * Math.PI * radius
  const offset = circumference - (score / 100) * circumference
  const color = score >= 80 ? 'stroke-green-500' : score >= 60 ? 'stroke-yellow-500' : 'stroke-red-500'
  const textColor = score >= 80 ? 'text-green-600 dark:text-green-400' : score >= 60 ? 'text-yellow-600 dark:text-yellow-400' : 'text-red-600 dark:text-red-400'

  return (
    <div className="relative" style={{ width: size, height: size }}>
      <svg className="w-full h-full -rotate-90" viewBox={`0 0 ${size} ${size}`}>
        <circle cx={size/2} cy={size/2} r={radius} fill="none" strokeWidth="5" className="stroke-secondary" />
        <circle
          cx={size/2} cy={size/2} r={radius} fill="none" strokeWidth="5"
          strokeLinecap="round"
          className={color}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
        />
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">
        <span className={`text-lg font-bold ${textColor}`}>{score}%</span>
      </div>
    </div>
  )
}

export function ComplianceCards({ rawVulns, scans }: Props) {
  const frameworks = useMemo(() => {
    const result: ComplianceFramework[] = []

    const owaspHits = new Set<string>()
    for (const v of rawVulns) {
      // owaspCategory ressemble à "A03:2021 Injection" → on extrait le préfixe "A03"
      const m = v.owaspCategory ? String(v.owaspCategory).match(/A\d{2}/i) : null
      if (m) owaspHits.add(m[0].toUpperCase())
    }
    const owaspPassed = OWASP_CATALOG.filter(c => !owaspHits.has(c.id)).length
    const owaspFailed = 10 - owaspPassed
    result.push({
      name: 'OWASP Top 10',
      version: '2021',
      score: owaspPassed * 10,
      passedControls: owaspPassed,
      failedControls: owaspFailed,
      totalControls: 10,
    })

    const sastScans = scans.filter(s => s.scanType === 'SAST')
    const sastCompleted = sastScans.filter(s => s.status === 'COMPLETED').length
    const sastTotal = Math.max(sastScans.length, 1)
    const sastVulns = rawVulns.filter((v: any) => (v.vulnType || '').toUpperCase() === 'SAST')
    const sastScore = sastCompleted > 0 ? weightedScore(sastVulns) : 0
    result.push({
      name: 'Secure Coding',
      version: 'SAST',
      score: Math.round(sastScore),
      passedControls: sastCompleted,
      failedControls: sastScans.length - sastCompleted,
      totalControls: sastTotal,
    })

    const dastScans = scans.filter(s => s.scanType === 'DAST')
    const dastCompleted = dastScans.filter(s => s.status === 'COMPLETED').length
    const dastTotal = Math.max(dastScans.length, 1)
    const dastVulns = rawVulns.filter((v: any) => (v.vulnType || '').toUpperCase() === 'DAST')
    const dastScore = dastCompleted > 0 ? weightedScore(dastVulns) : 0
    result.push({
      name: 'Infrastructure',
      version: 'DAST',
      score: Math.round(dastScore),
      passedControls: dastCompleted,
      failedControls: dastScans.length - dastCompleted,
      totalControls: dastTotal,
    })

    const scaScans = scans.filter(s => s.scanType === 'SCA')
    const scaCompleted = scaScans.filter(s => s.status === 'COMPLETED').length
    const scaTotal = Math.max(scaScans.length, 1)
    const scaVulns = rawVulns.filter((v: any) => (v.vulnType || '').toUpperCase() === 'SCA')
    const scaScore = scaCompleted > 0 ? weightedScore(scaVulns) : 0
    result.push({
      name: 'Dependencies',
      version: 'SCA',
      score: Math.round(scaScore),
      passedControls: scaCompleted,
      failedControls: scaScans.length - scaCompleted,
      totalControls: scaTotal,
    })

    return result
  }, [rawVulns, scans])

  if (scans.length === 0) {
    return (
      <Card>
        <CardContent className="py-8 text-center text-muted-foreground">
          <ClipboardCheck className="h-6 w-6 mx-auto mb-2 opacity-40" />
          <p className="text-xs">Lancez des scans pour voir la conformite</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-bold flex items-center gap-2">
        <ClipboardCheck className="h-5 w-5 text-primary" />
        Compliance
      </h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {frameworks.map(fw => (
          <Card key={fw.name}>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">
                {fw.name}
                <span className="text-xs text-muted-foreground ml-2">{fw.version}</span>
              </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col items-center gap-4">
              <ScoreRing score={fw.score} />
              <div className="w-full space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="flex items-center gap-1 text-green-600 dark:text-green-400">
                    <CheckCircle2 className="h-3 w-3" />Passed
                  </span>
                  <span className="font-semibold">{fw.passedControls}/{fw.totalControls}</span>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="flex items-center gap-1 text-red-600 dark:text-red-400">
                    <XCircle className="h-3 w-3" />Failed
                  </span>
                  <span className="font-semibold">{fw.failedControls}/{fw.totalControls}</span>
                </div>
                <div className="h-2 rounded-full bg-secondary overflow-hidden">
                  <div
                    className={`h-full rounded-full ${fw.score >= 80 ? 'bg-green-500' : fw.score >= 60 ? 'bg-yellow-500' : 'bg-red-500'}`}
                    style={{ width: `${fw.score}%` }}
                  />
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}

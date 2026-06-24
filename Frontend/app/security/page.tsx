'use client'

import { useEffect, useState, useCallback } from 'react'
import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import {
  Shield, LayoutDashboard, Code, Bug, ListChecks,
  ListFilter, FileText, ClipboardCheck, TrendingDown, Workflow,
  Package, Loader2, FolderOpen,
} from 'lucide-react'

import { OverviewCards } from '@/components/security/overview-cards'
import { SecurityScoreGauge } from '@/components/security/security-score-gauge'
import { SastCenter } from '@/components/security/sast-center'
import { DastCenter } from '@/components/security/dast-center'
import { ScaCenter } from '@/components/security/sca-center'
import { OwaspTable } from '@/components/security/owasp-table'
import { VulnerabilityTable } from '@/components/security/vulnerability-table'
import { ComplianceCards } from '@/components/security/compliance-cards'
import { SecurityTrends } from '@/components/security/security-trends'
import { PipelineView } from '@/components/security/pipeline-view'
import { ReportSection } from '@/components/security/report-section'

import { projectService } from '@/services/projects'
import { fetchExecutionScans, fetchScanVulnerabilities, mapToVulnerability } from '@/lib/security-client'
import type { Project } from '@/types/ms-gestion'
import type { Vulnerability, SecurityScore } from '@/types/security'

export default function SecurityPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [selectedProject, setSelectedProject] = useState<string>('')
  const [loading, setLoading] = useState(false)
  const [scans, setScans] = useState<any[]>([])
  const [vulns, setVulns] = useState<Vulnerability[]>([])
  const [rawVulns, setRawVulns] = useState<any[]>([])

  useEffect(() => {
    projectService.getAll().then(setProjects).catch(() => {})
  }, [])

  const loadProjectData = useCallback(async (projectId: string) => {
    if (!projectId) return
    setLoading(true)
    try {
      const allScans = await fetchExecutionScans(Number(projectId))
      setScans(allScans)

      const completed = allScans.filter((s: any) => s.status === 'COMPLETED')
      const allVulns: any[] = []
      for (const scan of completed) {
        const v = await fetchScanVulnerabilities(scan.id)
        allVulns.push(...v)
      }
      setRawVulns(allVulns)
      setVulns(allVulns.map(mapToVulnerability))
    } catch { /* handled */ }
    finally { setLoading(false) }
  }, [])

  useEffect(() => {
    if (selectedProject) loadProjectData(selectedProject)
    else { setScans([]); setVulns([]); setRawVulns([]) }
  }, [selectedProject, loadProjectData])

  const critical = vulns.filter(v => v.severity === 'critical').length
  const high = vulns.filter(v => v.severity === 'high').length
  const medium = vulns.filter(v => v.severity === 'medium').length
  const low = vulns.filter(v => v.severity === 'low').length
  const total = vulns.length

  // Score pondéré par sévérité : 100% = zéro finding (cohérent avec les cartes Compliance).
  const computeScore = (c: number, h: number, m: number, l: number) =>
    Math.max(0, 100 - (c * 15 + h * 12 + m * 4 + l * 1))

  const sastVulns = vulns.filter(v => v.type === 'SAST')
  const dastVulns = vulns.filter(v => v.type === 'DAST')
  const scaVulns = vulns.filter(v => v.type === 'SCA')

  const subScore = (vs: Vulnerability[]) => {
    if (vs.length === 0) return 100
    return computeScore(
      vs.filter(v => v.severity === 'critical').length,
      vs.filter(v => v.severity === 'high').length,
      vs.filter(v => v.severity === 'medium').length,
      vs.filter(v => v.severity === 'low').length,
    )
  }

  const score: SecurityScore = {
    overall: computeScore(critical, high, medium, low),
    owasp: subScore(vulns),
    secureCoding: subScore(sastVulns),
    infrastructure: subScore(dastVulns),
    dependencies: subScore(scaVulns),
  }

  return (
    <div className="flex h-screen bg-background">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto">
          <div className="max-w-[1440px] mx-auto px-6 py-6 space-y-6">

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-primary/10">
                  <Shield className="h-6 w-6 text-primary" />
                </div>
                <div>
                  <h1 className="text-xl font-bold">Security Center</h1>
                  <p className="text-xs text-muted-foreground">SAST, DAST, SCA — Analyse de securite applicative</p>
                </div>
              </div>
              <div className="flex items-center gap-3">
                {loading && <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />}
                <Select value={selectedProject} onValueChange={setSelectedProject}>
                  <SelectTrigger className="h-9 w-[220px]">
                    <SelectValue placeholder="Selectionner un projet" />
                  </SelectTrigger>
                  <SelectContent>
                    {projects.map(p => (
                      <SelectItem key={p.id} value={String(p.id)}>{p.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {selectedProject && !loading && (
                  <Badge variant="outline" className="text-xs">
                    {scans.length} scans · {vulns.length} vulns
                  </Badge>
                )}
              </div>
            </div>

            <Tabs defaultValue="overview" className="space-y-6">
              <TabsList className="flex-wrap h-auto gap-1 p-1.5 bg-muted/60 border">
                <TabsTrigger value="overview" className="text-xs gap-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm">
                  <LayoutDashboard className="h-3.5 w-3.5" />Overview
                </TabsTrigger>
                <TabsTrigger value="sast" className="text-xs gap-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm">
                  <Code className="h-3.5 w-3.5" />SAST
                </TabsTrigger>
                <TabsTrigger value="dast" className="text-xs gap-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm">
                  <Bug className="h-3.5 w-3.5" />DAST
                </TabsTrigger>
                <TabsTrigger value="sca" className="text-xs gap-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm">
                  <Package className="h-3.5 w-3.5" />SCA
                </TabsTrigger>
                <TabsTrigger value="owasp" className="text-xs gap-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm">
                  <ListChecks className="h-3.5 w-3.5" />OWASP Top 10
                </TabsTrigger>
                <TabsTrigger value="vulns" className="text-xs gap-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm">
                  <ListFilter className="h-3.5 w-3.5" />Vulnerabilities
                </TabsTrigger>
                <TabsTrigger value="compliance" className="text-xs gap-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm">
                  <ClipboardCheck className="h-3.5 w-3.5" />Compliance
                </TabsTrigger>
                <TabsTrigger value="trends" className="text-xs gap-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm">
                  <TrendingDown className="h-3.5 w-3.5" />Trends
                </TabsTrigger>
                <TabsTrigger value="pipeline" className="text-xs gap-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm">
                  <Workflow className="h-3.5 w-3.5" />CI/CD Pipeline
                </TabsTrigger>
                <TabsTrigger value="reports" className="text-xs gap-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm">
                  <FileText className="h-3.5 w-3.5" />Reports
                </TabsTrigger>
              </TabsList>

              <TabsContent value="overview" className="space-y-6">
                {!selectedProject ? (
                  <EmptyProjectState />
                ) : (
                  <>
                    <OverviewCards vulns={vulns} />
                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                      <div className="lg:col-span-2">
                        <OwaspTable />
                      </div>
                      <div>
                        <SecurityScoreGauge score={score} />
                      </div>
                    </div>
                    <SecurityTrends scans={scans} rawVulns={rawVulns} />
                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                      <PipelineView scans={scans} vulns={vulns} />
                      <ComplianceCards rawVulns={rawVulns} scans={scans} />
                    </div>
                  </>
                )}
              </TabsContent>

              <TabsContent value="sast">
                <SastCenter />
              </TabsContent>

              <TabsContent value="dast">
                <DastCenter />
              </TabsContent>

              <TabsContent value="sca">
                <ScaCenter />
              </TabsContent>

              <TabsContent value="owasp">
                <OwaspTable />
              </TabsContent>

              <TabsContent value="vulns">
                {!selectedProject ? <EmptyProjectState /> : (
                  <VulnerabilityTable vulns={vulns} />
                )}
              </TabsContent>

              <TabsContent value="compliance">
                {!selectedProject ? <EmptyProjectState /> : (
                  <ComplianceCards rawVulns={rawVulns} scans={scans} />
                )}
              </TabsContent>

              <TabsContent value="trends">
                {!selectedProject ? <EmptyProjectState /> : (
                  <SecurityTrends scans={scans} rawVulns={rawVulns} />
                )}
              </TabsContent>

              <TabsContent value="pipeline">
                {!selectedProject ? <EmptyProjectState /> : (
                  <PipelineView scans={scans} vulns={vulns} />
                )}
              </TabsContent>

              <TabsContent value="reports">
                {!selectedProject ? <EmptyProjectState /> : (
                  <ReportSection scans={scans} />
                )}
              </TabsContent>
            </Tabs>
          </div>
        </main>
      </div>
    </div>
  )
}

function EmptyProjectState() {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <div className="p-4 rounded-full bg-muted mb-4">
        <FolderOpen className="h-8 w-8 text-muted-foreground" />
      </div>
      <h3 className="text-lg font-semibold mb-1">Aucun projet selectionne</h3>
      <p className="text-sm text-muted-foreground max-w-md">
        Selectionnez un projet en haut a droite pour afficher les donnees de securite.
        Les donnees proviennent des scans reels (SAST, DAST, SCA).
      </p>
    </div>
  )
}

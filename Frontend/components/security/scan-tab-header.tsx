'use client'

import { useEffect, useState, useCallback } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import {
  Play, Loader2, RefreshCw, GitBranch, Globe, CheckCircle2,
  XCircle, Clock, Download,
} from 'lucide-react'
import { projectService } from '@/services/projects'
import { environmentService } from '@/services/environments'
import { launchScan, fetchExecutionScans } from '@/lib/security-client'
import type { Project, Environment } from '@/types/ms-gestion'

export interface ScanRecord {
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

interface ScanTabHeaderProps {
  scanType: 'sast' | 'dast' | 'sca'
  icon: React.ReactNode
  title: string
  subtitle: string
  accentColor: string
  onScansLoaded?: (scans: ScanRecord[]) => void
  onVulnsLoaded?: (scanId: number) => void
}

export function ScanTabHeader({
  scanType, icon, title, subtitle, accentColor,
  onScansLoaded,
}: ScanTabHeaderProps) {
  const [projects, setProjects] = useState<Project[]>([])
  const [environments, setEnvironments] = useState<Environment[]>([])
  const [selectedProject, setSelectedProject] = useState<string>('')
  const [selectedEnv, setSelectedEnv] = useState<string>('')
  const [launching, setLaunching] = useState(false)
  const [scans, setScans] = useState<ScanRecord[]>([])
  const [polling, setPolling] = useState(false)

  useEffect(() => {
    projectService.getAll().then(setProjects).catch(() => {})
  }, [])

  useEffect(() => {
    if (!selectedProject) { setEnvironments([]); setSelectedEnv(''); return }
    environmentService.getAll(Number(selectedProject))
      .then(setEnvironments)
      .catch(() => setEnvironments([]))
  }, [selectedProject])

  const loadScans = useCallback(async () => {
    if (!selectedProject) return
    const all = await fetchExecutionScans(Number(selectedProject))
    const typed = all.filter((s: ScanRecord) => s.scanType === scanType.toUpperCase())
    setScans(typed)
    onScansLoaded?.(typed)
  }, [selectedProject, scanType, onScansLoaded])

  useEffect(() => {
    if (selectedProject) loadScans()
  }, [selectedProject, loadScans])

  useEffect(() => {
    const hasRunning = scans.some(s => s.status === 'RUNNING')
    if (!hasRunning) { setPolling(false); return }
    setPolling(true)
    const interval = setInterval(loadScans, 5000)
    return () => clearInterval(interval)
  }, [scans, loadScans])

  async function handleLaunch() {
    if (!selectedProject || !selectedEnv) return
    setLaunching(true)
    try {
      await launchScan(scanType, Number(selectedProject), Number(selectedEnv))
      setTimeout(loadScans, 1500)
    } catch { /* handled */ }
    finally { setLaunching(false) }
  }

  const selectedEnvObj = environments.find(e => String(e.id) === selectedEnv)
  const latestScan = scans[0]

  return (
    <div className="space-y-4">
      {/* Title + Controls */}
      <div className="flex items-start justify-between">
        <div>
          <h2 className="text-xl font-bold flex items-center gap-2">
            {icon}
            {title}
          </h2>
          <p className="text-sm text-muted-foreground mt-1">{subtitle}</p>
        </div>
        <div className="flex items-center gap-2">
          {polling && <RefreshCw className="h-3.5 w-3.5 animate-spin text-muted-foreground" />}
          <Button variant="ghost" size="sm" onClick={loadScans} disabled={!selectedProject}>
            <RefreshCw className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>

      {/* Project + Env + Launch */}
      <div className="flex flex-wrap items-end gap-3 p-4 rounded-lg border bg-muted/30">
        <div className="space-y-1.5 min-w-[180px]">
          <label className="text-xs font-medium text-muted-foreground">Projet</label>
          <Select value={selectedProject} onValueChange={setSelectedProject}>
            <SelectTrigger className="h-9">
              <SelectValue placeholder="Selectionner un projet" />
            </SelectTrigger>
            <SelectContent>
              {projects.map(p => (
                <SelectItem key={p.id} value={String(p.id)}>{p.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5 min-w-[200px]">
          <label className="text-xs font-medium text-muted-foreground">Environnement</label>
          <Select value={selectedEnv} onValueChange={setSelectedEnv} disabled={!selectedProject || environments.length === 0}>
            <SelectTrigger className="h-9">
              <SelectValue placeholder={!selectedProject ? 'Projet d\'abord' : 'Selectionner'} />
            </SelectTrigger>
            <SelectContent>
              {environments.map(e => (
                <SelectItem key={e.id} value={String(e.id)}>
                  {e.name}
                  {e.gitBranch && <span className="text-muted-foreground ml-1">({e.gitBranch})</span>}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {selectedEnvObj && (
          <div className="flex items-center gap-2 text-xs text-muted-foreground px-2">
            {selectedEnvObj.gitRepoUrl && (
              <span className="flex items-center gap-1">
                <GitBranch className="h-3 w-3" />
                {selectedEnvObj.gitRepoUrl.replace('https://github.com/', '')}
              </span>
            )}
            {selectedEnvObj.baseUrlApi && (
              <span className="flex items-center gap-1">
                <Globe className="h-3 w-3" />{selectedEnvObj.baseUrlApi}
              </span>
            )}
          </div>
        )}

        <Button
          onClick={handleLaunch}
          disabled={!selectedProject || !selectedEnv || launching}
          className={`h-9 ${accentColor}`}
        >
          {launching
            ? <><Loader2 className="h-4 w-4 mr-1 animate-spin" />Scan en cours...</>
            : <><Play className="h-4 w-4 mr-1" />Lancer le scan {scanType.toUpperCase()}</>
          }
        </Button>
      </div>

      {/* Latest scan status bar */}
      {latestScan && (
        <div className="flex items-center justify-between px-4 py-2.5 rounded-lg border bg-card text-sm">
          <div className="flex items-center gap-3">
            <ScanStatusIcon status={latestScan.status} />
            <span className="font-mono text-xs">{latestScan.scanRef}</span>
            <Badge variant="outline" className="text-[10px]">{latestScan.engine}</Badge>
            {latestScan.startedAt && (
              <span className="text-xs text-muted-foreground">
                {new Date(latestScan.startedAt).toLocaleString('fr-FR')}
              </span>
            )}
            {latestScan.duration && (
              <span className="text-xs text-muted-foreground flex items-center gap-1">
                <Clock className="h-3 w-3" />{latestScan.duration}
              </span>
            )}
            {latestScan.branch && (
              <span className="text-xs text-muted-foreground flex items-center gap-1">
                <GitBranch className="h-3 w-3" />{latestScan.branch}
              </span>
            )}
          </div>
          <div className="flex items-center gap-2">
            {latestScan.status === 'COMPLETED' && (
              <a href={`/api/security/report/${latestScan.id}`} target="_blank" rel="noopener">
                <Button variant="outline" size="sm" className="h-7 text-xs gap-1">
                  <Download className="h-3 w-3" />PDF
                </Button>
              </a>
            )}
            <ScanStatusBadge status={latestScan.status} />
          </div>
        </div>
      )}
    </div>
  )
}

function ScanStatusIcon({ status }: { status: string }) {
  switch (status) {
    case 'RUNNING': return <Loader2 className="h-4 w-4 animate-spin text-blue-500" />
    case 'COMPLETED': return <CheckCircle2 className="h-4 w-4 text-green-500" />
    case 'FAILED': return <XCircle className="h-4 w-4 text-red-500" />
    default: return <Clock className="h-4 w-4 text-muted-foreground" />
  }
}

function ScanStatusBadge({ status }: { status: string }) {
  const map: Record<string, { label: string; cls: string }> = {
    RUNNING: { label: 'En cours', cls: 'bg-blue-100 text-blue-700 border-blue-200' },
    COMPLETED: { label: 'Termine', cls: 'bg-green-100 text-green-700 border-green-200' },
    FAILED: { label: 'Echoue', cls: 'bg-red-100 text-red-700 border-red-200' },
  }
  const s = map[status] ?? { label: status, cls: '' }
  return <Badge variant="outline" className={`text-[10px] ${s.cls}`}>{s.label}</Badge>
}

export { ScanStatusBadge }

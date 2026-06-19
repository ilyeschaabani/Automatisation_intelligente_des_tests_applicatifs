'use client'

import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Code, Bug, Package, Play, Loader2, CheckCircle2,
  XCircle, Clock, GitBranch, Globe, FileText, Download,
  RefreshCw,
} from 'lucide-react'
import { projectService } from '@/services/projects'
import { environmentService } from '@/services/environments'
import { launchScan, fetchExecutionScans } from '@/lib/security-client'
import type { Project, Environment } from '@/types/ms-gestion'

interface ScanRecord {
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
  branch: string | null
  targetUrl: string | null
}

const SCAN_TYPES = [
  {
    key: 'sast' as const,
    label: 'SAST',
    engine: 'Semgrep',
    icon: Code,
    color: 'text-blue-600',
    bg: 'bg-blue-50',
    border: 'border-blue-200',
    description: 'Analyse statique du code source',
  },
  {
    key: 'dast' as const,
    label: 'DAST',
    engine: 'OWASP ZAP',
    icon: Bug,
    color: 'text-orange-600',
    bg: 'bg-orange-50',
    border: 'border-orange-200',
    description: 'Analyse dynamique de l\'application',
  },
  {
    key: 'sca' as const,
    label: 'SCA',
    engine: 'Dependency-Check',
    icon: Package,
    color: 'text-purple-600',
    bg: 'bg-purple-50',
    border: 'border-purple-200',
    description: 'Audit des dependances Maven',
  },
]

export function ScanLauncher() {
  const [projects, setProjects] = useState<Project[]>([])
  const [environments, setEnvironments] = useState<Environment[]>([])
  const [selectedProject, setSelectedProject] = useState<string>('')
  const [selectedEnv, setSelectedEnv] = useState<string>('')
  const [launching, setLaunching] = useState<string | null>(null)
  const [recentScans, setRecentScans] = useState<ScanRecord[]>([])
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

  useEffect(() => {
    if (!selectedProject) return
    loadScans()
  }, [selectedProject])

  useEffect(() => {
    const hasRunning = recentScans.some(s => s.status === 'RUNNING')
    if (!hasRunning) { setPolling(false); return }
    setPolling(true)
    const interval = setInterval(() => loadScans(), 5000)
    return () => clearInterval(interval)
  }, [recentScans, selectedProject])

  async function loadScans() {
    if (!selectedProject) return
    const scans = await fetchExecutionScans(Number(selectedProject))
    setRecentScans(scans)
  }

  async function handleLaunch(type: 'sast' | 'dast' | 'sca') {
    if (!selectedProject || !selectedEnv) return
    setLaunching(type)
    try {
      await launchScan(type, Number(selectedProject), Number(selectedEnv))
      setTimeout(() => loadScans(), 1000)
    } catch { /* handled by client */ }
    finally { setLaunching(null) }
  }

  const selectedEnvObj = environments.find(e => String(e.id) === selectedEnv)

  return (
    <div className="space-y-6">
      {/* Project & Environment Selection */}
      <Card>
        <CardHeader className="pb-4">
          <CardTitle className="text-base flex items-center gap-2">
            <Play className="h-4 w-4" />
            Lancer un scan de securite
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">Projet</label>
              <Select value={selectedProject} onValueChange={setSelectedProject}>
                <SelectTrigger>
                  <SelectValue placeholder="Selectionner un projet" />
                </SelectTrigger>
                <SelectContent>
                  {projects.map(p => (
                    <SelectItem key={p.id} value={String(p.id)}>
                      {p.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Environnement</label>
              <Select
                value={selectedEnv}
                onValueChange={setSelectedEnv}
                disabled={!selectedProject || environments.length === 0}
              >
                <SelectTrigger>
                  <SelectValue placeholder={
                    !selectedProject ? 'Choisir un projet d\'abord'
                    : environments.length === 0 ? 'Aucun environnement'
                    : 'Selectionner un environnement'
                  } />
                </SelectTrigger>
                <SelectContent>
                  {environments.map(e => (
                    <SelectItem key={e.id} value={String(e.id)}>
                      <span>{e.name}</span>
                      {e.gitRepoUrl && (
                        <span className="ml-2 text-xs text-muted-foreground">
                          ({e.gitBranch ?? 'main'})
                        </span>
                      )}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {selectedEnvObj && (
            <div className="flex flex-wrap gap-3 text-xs text-muted-foreground bg-muted/50 rounded-lg p-3">
              {selectedEnvObj.gitRepoUrl && (
                <span className="flex items-center gap-1">
                  <GitBranch className="h-3 w-3" />
                  {selectedEnvObj.gitRepoUrl.replace('https://github.com/', '')}
                  @ {selectedEnvObj.gitBranch ?? 'main'}
                </span>
              )}
              {selectedEnvObj.baseUrlApi && (
                <span className="flex items-center gap-1">
                  <Globe className="h-3 w-3" />
                  {selectedEnvObj.baseUrlApi}
                </span>
              )}
              {selectedEnvObj.databaseType && (
                <Badge variant="outline" className="text-[10px]">
                  {selectedEnvObj.databaseType}
                </Badge>
              )}
            </div>
          )}

          {/* Scan Type Buttons */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {SCAN_TYPES.map(scan => {
              const Icon = scan.icon
              const disabled = !selectedProject || !selectedEnv || launching !== null
              const isLaunching = launching === scan.key

              return (
                <button
                  key={scan.key}
                  onClick={() => handleLaunch(scan.key)}
                  disabled={disabled}
                  className={`
                    relative flex flex-col items-center gap-2 p-4 rounded-lg border-2 transition-all
                    ${disabled
                      ? 'border-muted bg-muted/30 opacity-50 cursor-not-allowed'
                      : `${scan.border} ${scan.bg} hover:shadow-md cursor-pointer active:scale-[0.98]`
                    }
                  `}
                >
                  {isLaunching ? (
                    <Loader2 className={`h-8 w-8 animate-spin ${scan.color}`} />
                  ) : (
                    <Icon className={`h-8 w-8 ${scan.color}`} />
                  )}
                  <div className="text-center">
                    <div className="font-semibold text-sm">{scan.label}</div>
                    <div className="text-[10px] text-muted-foreground">{scan.engine}</div>
                    <div className="text-[10px] text-muted-foreground mt-1">{scan.description}</div>
                  </div>
                  {!disabled && (
                    <div className={`absolute top-2 right-2 ${scan.color}`}>
                      <Play className="h-3.5 w-3.5" />
                    </div>
                  )}
                </button>
              )
            })}
          </div>
        </CardContent>
      </Card>

      {/* Recent Scans */}
      {recentScans.length > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-base flex items-center gap-2">
                <Clock className="h-4 w-4" />
                Scans recents
                {polling && (
                  <RefreshCw className="h-3 w-3 animate-spin text-muted-foreground" />
                )}
              </CardTitle>
              <Button variant="ghost" size="sm" onClick={loadScans}>
                <RefreshCw className="h-3.5 w-3.5" />
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              {recentScans.slice(0, 10).map(scan => (
                <div
                  key={scan.id}
                  className="flex items-center justify-between p-3 rounded-lg border bg-card hover:bg-muted/30 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <ScanStatusIcon status={scan.status} />
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs font-medium">{scan.scanRef}</span>
                        <Badge variant="outline" className="text-[10px]">
                          {scan.scanType}
                        </Badge>
                        <span className="text-xs text-muted-foreground">{scan.engine}</span>
                      </div>
                      <div className="flex items-center gap-3 text-[11px] text-muted-foreground mt-0.5">
                        {scan.startedAt && (
                          <span>{new Date(scan.startedAt).toLocaleString('fr-FR')}</span>
                        )}
                        {scan.duration && <span>{scan.duration}</span>}
                        {scan.linesAnalyzed && (
                          <span>{scan.linesAnalyzed.toLocaleString()} lignes</span>
                        )}
                        {scan.filesAnalyzed && (
                          <span>{scan.filesAnalyzed} fichiers</span>
                        )}
                        {scan.branch && (
                          <span className="flex items-center gap-0.5">
                            <GitBranch className="h-3 w-3" />{scan.branch}
                          </span>
                        )}
                        {scan.targetUrl && (
                          <span className="flex items-center gap-0.5">
                            <Globe className="h-3 w-3" />{scan.targetUrl}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    {scan.status === 'RUNNING' && (
                      <div className="w-24">
                        <Progress value={undefined} className="h-1.5" />
                      </div>
                    )}
                    {scan.status === 'COMPLETED' && (
                      <a
                        href={`/api/security/report/${scan.id}`}
                        target="_blank"
                        rel="noopener"
                      >
                        <Button variant="outline" size="sm" className="h-7 text-xs gap-1">
                          <Download className="h-3 w-3" />
                          PDF
                        </Button>
                      </a>
                    )}
                    <ScanStatusBadge status={scan.status} />
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

function ScanStatusIcon({ status }: { status: string }) {
  switch (status) {
    case 'RUNNING':
      return <Loader2 className="h-5 w-5 animate-spin text-blue-500" />
    case 'COMPLETED':
      return <CheckCircle2 className="h-5 w-5 text-green-500" />
    case 'FAILED':
      return <XCircle className="h-5 w-5 text-red-500" />
    default:
      return <Clock className="h-5 w-5 text-muted-foreground" />
  }
}

function ScanStatusBadge({ status }: { status: string }) {
  const map: Record<string, { label: string; cls: string }> = {
    RUNNING: { label: 'En cours', cls: 'bg-blue-100 text-blue-700 border-blue-200' },
    COMPLETED: { label: 'Termine', cls: 'bg-green-100 text-green-700 border-green-200' },
    FAILED: { label: 'Echoue', cls: 'bg-red-100 text-red-700 border-red-200' },
    SCHEDULED: { label: 'Planifie', cls: 'bg-gray-100 text-gray-700 border-gray-200' },
  }
  const s = map[status] ?? map.SCHEDULED
  return (
    <Badge variant="outline" className={`text-[10px] ${s.cls}`}>
      {s.label}
    </Badge>
  )
}

'use client'

import { useEffect, useState, useMemo, useRef, Suspense } from 'react'
import { useSearchParams, useRouter } from 'next/navigation'
import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  ClipboardList, Shield, Bug, AlertCircle, ExternalLink,
  Loader2, ArrowRight, FileCode, Globe, Package, CheckCircle2,
} from 'lucide-react'
import { toast } from '@/hooks/use-toast'

interface VulnAssignment {
  id: number
  kind: 'vulnerability'
  title: string
  severity: string
  status: string
  vulnType: string
  cweId: string | null
  owaspCategory: string | null
  file: string | null
  endpoint: string | null
  assignedTo: string
  createdAt: string | null
  projectId: number | null
  projectName: string | null
  scanId: number | null
}

interface TestErrorAssignment {
  id: number
  kind: 'test-error'
  testCaseId: number
  campaignId: number
  status: string
  errorMessage: string | null
  assignedTo: string
}

function sevColor(s: string) {
  switch (s) {
    case 'CRITICAL': return 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400 border-red-300'
    case 'HIGH': return 'bg-orange-100 text-orange-800 dark:bg-orange-950 dark:text-orange-400 border-orange-300'
    case 'MEDIUM': return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-950 dark:text-yellow-400 border-yellow-300'
    case 'LOW': return 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400 border-blue-300'
    default: return 'bg-gray-100 text-gray-800 border-gray-300'
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

function vulnTypeIcon(type: string) {
  switch (type) {
    case 'SAST': return <FileCode className="h-4 w-4" />
    case 'DAST': return <Globe className="h-4 w-4" />
    case 'SCA': return <Package className="h-4 w-4" />
    default: return <Shield className="h-4 w-4" />
  }
}

function isResolved(status: string) {
  const s = status.toUpperCase()
  return s === 'RESOLVED' || s === 'CLOSED' || s === 'FALSE_POSITIVE'
}

function MyAssignmentsInner() {
  const searchParams = useSearchParams()
  const router = useRouter()
  const highlightParam = searchParams.get('highlight')

  const [vulns, setVulns] = useState<VulnAssignment[]>([])
  const [testErrors, setTestErrors] = useState<TestErrorAssignment[]>([])
  const [loading, setLoading] = useState(true)
  const [userId, setUserId] = useState<number | null>(null)
  const [resolving, setResolving] = useState<string | null>(null)
  const highlightRef = useRef<HTMLDivElement>(null)

  const activeTab = useMemo(() => {
    if (highlightParam?.startsWith('result-')) return 'tests'
    return 'vulns'
  }, [highlightParam])

  const [tab, setTab] = useState(activeTab)

  useEffect(() => {
    fetch('/api/auth/profile', { credentials: 'include' })
      .then(r => r.ok ? r.json() : null)
      .then(data => {
        if (data?.id) setUserId(Number(data.id))
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!userId) return
    setLoading(true)
    fetch(`/api/assignments?userId=${userId}`)
      .then(r => r.ok ? r.json() : null)
      .then(data => {
        if (data) {
          setVulns(data.vulnerabilities ?? [])
          setTestErrors(data.executionResults ?? [])
        }
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [userId])

  useEffect(() => {
    if (highlightParam && !loading) {
      if (highlightParam.startsWith('result-')) setTab('tests')
      else if (highlightParam.startsWith('vuln-')) setTab('vulns')
      setTimeout(() => highlightRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 200)
    }
  }, [highlightParam, loading])

  async function resolveVuln(e: React.MouseEvent, vulnId: number) {
    e.stopPropagation()
    setResolving(`vuln-${vulnId}`)
    try {
      const res = await fetch(`/api/security/vulnerabilities/${vulnId}`, {
        method: 'PATCH',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ status: 'RESOLVED' }),
      })
      if (res.ok) {
        setVulns(prev => prev.map(v => v.id === vulnId ? { ...v, status: 'RESOLVED' } : v))
        toast({ title: 'Résolu', description: 'La vulnérabilité a été marquée comme résolue.' })
      } else {
        const text = await res.text().catch(() => '')
        toast({ title: 'Erreur', description: text || `Erreur ${res.status}`, variant: 'destructive' })
      }
    } catch {
      toast({ title: 'Erreur', description: 'Impossible de contacter le serveur.', variant: 'destructive' })
    }
    setResolving(null)
  }

  async function resolveTestError(e: React.MouseEvent, resultId: number) {
    e.stopPropagation()
    setResolving(`result-${resultId}`)
    try {
      const res = await fetch(`/api/execution-results/${resultId}/status`, {
        method: 'PATCH',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ status: 'RESOLVED' }),
      })
      if (res.ok) {
        setTestErrors(prev => prev.map(r => r.id === resultId ? { ...r, status: 'RESOLVED' } : r))
        toast({ title: 'Résolu', description: 'L\'erreur de test a été marquée comme résolue.' })
      } else {
        const text = await res.text().catch(() => '')
        toast({ title: 'Erreur', description: text || `Erreur ${res.status}`, variant: 'destructive' })
      }
    } catch {
      toast({ title: 'Erreur', description: 'Impossible de contacter le serveur.', variant: 'destructive' })
    }
    setResolving(null)
  }

  const totalCount = vulns.length + testErrors.length

  return (
    <div className="flex h-screen bg-background">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto">
          <div className="max-w-[1200px] mx-auto px-6 py-6 space-y-6">

            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-primary/10">
                <ClipboardList className="h-6 w-6 text-primary" />
              </div>
              <div>
                <h1 className="text-xl font-bold">Mes Assignements</h1>
                <p className="text-xs text-muted-foreground">
                  {loading ? 'Chargement...' : `${totalCount} assignement(s) au total`}
                </p>
              </div>
            </div>

            {loading ? (
              <div className="flex items-center justify-center py-20">
                <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
              </div>
            ) : totalCount === 0 ? (
              <div className="flex flex-col items-center justify-center py-20 text-center">
                <div className="p-4 rounded-full bg-muted mb-4">
                  <ClipboardList className="h-8 w-8 text-muted-foreground" />
                </div>
                <h3 className="text-lg font-semibold mb-1">Aucun assignement</h3>
                <p className="text-sm text-muted-foreground max-w-md">
                  Vous n'avez aucune vulnerabilite ou erreur de test assignee pour le moment.
                </p>
              </div>
            ) : (
              <Tabs value={tab} onValueChange={setTab} className="space-y-4">
                <TabsList className="h-auto gap-1 p-1.5 bg-muted/60 border">
                  <TabsTrigger value="vulns" className="text-xs gap-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm">
                    <Shield className="h-3.5 w-3.5" />
                    Vulnerabilites
                    {vulns.length > 0 && (
                      <Badge variant="secondary" className="ml-1 text-[10px] h-5 px-1.5">{vulns.length}</Badge>
                    )}
                  </TabsTrigger>
                  <TabsTrigger value="tests" className="text-xs gap-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm">
                    <Bug className="h-3.5 w-3.5" />
                    Erreurs de test
                    {testErrors.length > 0 && (
                      <Badge variant="secondary" className="ml-1 text-[10px] h-5 px-1.5">{testErrors.length}</Badge>
                    )}
                  </TabsTrigger>
                </TabsList>

                <TabsContent value="vulns" className="space-y-3">
                  {vulns.length === 0 ? (
                    <p className="text-sm text-muted-foreground text-center py-8">Aucune vulnerabilite assignee</p>
                  ) : (
                    vulns.map(v => {
                      const isHighlighted = highlightParam === `vuln-${v.id}`
                      const resolved = isResolved(v.status)
                      return (
                        <div
                          key={v.id}
                          ref={isHighlighted ? highlightRef : undefined}
                          className={`border rounded-lg p-4 border-l-4 transition-all hover:shadow-md cursor-pointer ${resolved ? 'border-l-green-500 opacity-75' : sevBorder(v.severity)} ${isHighlighted ? 'ring-2 ring-primary bg-primary/5' : ''}`}
                          onClick={() => router.push(`/security/details/${v.id}`)}
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2 flex-wrap mb-1">
                                {resolved
                                  ? <CheckCircle2 className="h-4 w-4 text-green-500" />
                                  : vulnTypeIcon(v.vulnType)}
                                <h4 className={`font-semibold text-sm ${resolved ? 'line-through text-muted-foreground' : ''}`}>{v.title}</h4>
                                <Badge variant="outline" className={`text-[10px] ${resolved ? 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400 border-green-300' : sevColor(v.severity)}`}>
                                  {resolved ? v.status : v.severity}
                                </Badge>
                                {!resolved && <Badge variant="outline" className="text-[10px]">{v.status}</Badge>}
                                <Badge variant="secondary" className="text-[10px]">{v.vulnType}</Badge>
                              </div>
                              <div className="flex items-center gap-4 text-xs text-muted-foreground mt-1">
                                {v.assignedTo && (
                                  <span className="flex items-center gap-1 font-medium text-foreground">
                                    Assigné à: {v.assignedTo}
                                  </span>
                                )}
                                {v.cweId && <span className="font-mono">{v.cweId}</span>}
                                {v.owaspCategory && <span>OWASP {v.owaspCategory}</span>}
                                {v.file && <span className="font-mono truncate max-w-[250px]">{v.file}</span>}
                                {v.endpoint && <span className="font-mono truncate max-w-[250px]">{v.endpoint}</span>}
                              </div>
                              <div className="flex items-center gap-3 text-xs text-muted-foreground mt-2">
                                {v.projectName && (
                                  <span className="flex items-center gap-1">
                                    <span className="font-medium text-foreground">Projet:</span> {v.projectName}
                                  </span>
                                )}
                                {v.createdAt && (
                                  <span>{new Date(v.createdAt).toLocaleDateString('fr-FR')}</span>
                                )}
                              </div>
                            </div>
                            <div className="flex items-center gap-2 shrink-0 mt-1">
                              {!resolved && (
                                <Button
                                  variant="outline"
                                  size="sm"
                                  className="h-7 text-xs gap-1 text-green-600 border-green-300 hover:bg-green-50 dark:hover:bg-green-950"
                                  disabled={resolving === `vuln-${v.id}`}
                                  onClick={(e) => resolveVuln(e, v.id)}
                                >
                                  {resolving === `vuln-${v.id}` ? <Loader2 className="h-3 w-3 animate-spin" /> : <CheckCircle2 className="h-3 w-3" />}
                                  Résoudre
                                </Button>
                              )}
                              <ArrowRight className="h-4 w-4 text-muted-foreground" />
                            </div>
                          </div>
                        </div>
                      )
                    })
                  )}
                </TabsContent>

                <TabsContent value="tests" className="space-y-3">
                  {testErrors.length === 0 ? (
                    <p className="text-sm text-muted-foreground text-center py-8">Aucune erreur de test assignee</p>
                  ) : (
                    testErrors.map(r => {
                      const isHighlighted = highlightParam === `result-${r.id}`
                      const resolved = isResolved(r.status)
                      return (
                        <div
                          key={r.id}
                          ref={isHighlighted ? highlightRef : undefined}
                          className={`border rounded-lg p-4 border-l-4 transition-all hover:shadow-md cursor-pointer ${resolved ? 'border-l-green-500 opacity-75' : 'border-l-red-500'} ${isHighlighted ? 'ring-2 ring-primary bg-primary/5' : ''}`}
                          onClick={() => router.push(`/campaigns/${r.campaignId}?highlight=${r.id}`)}
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2 flex-wrap mb-1">
                                {resolved
                                  ? <CheckCircle2 className="h-4 w-4 text-green-500" />
                                  : <AlertCircle className="h-4 w-4 text-red-500" />}
                                <h4 className={`font-semibold text-sm ${resolved ? 'line-through text-muted-foreground' : ''}`}>Test #{r.testCaseId}</h4>
                                <Badge variant="outline" className={`text-[10px] ${resolved ? 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400 border-green-300' : 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400'}`}>
                                  {r.status}
                                </Badge>
                              </div>
                              {r.errorMessage && !resolved && (
                                <p className="text-xs text-destructive mt-1 line-clamp-2">{r.errorMessage}</p>
                              )}
                              <div className="flex items-center gap-3 text-xs text-muted-foreground mt-2">
                                {r.assignedTo && (
                                  <span className="flex items-center gap-1 font-medium text-foreground">
                                    Assigné à: {r.assignedTo}
                                  </span>
                                )}
                                <span className="flex items-center gap-1">
                                  <span className="font-medium text-foreground">Campagne:</span> #{r.campaignId}
                                </span>
                              </div>
                            </div>
                            <div className="flex items-center gap-2 shrink-0 mt-1">
                              {!resolved && (
                                <Button
                                  variant="outline"
                                  size="sm"
                                  className="h-7 text-xs gap-1 text-green-600 border-green-300 hover:bg-green-50 dark:hover:bg-green-950"
                                  disabled={resolving === `result-${r.id}`}
                                  onClick={(e) => resolveTestError(e, r.id)}
                                >
                                  {resolving === `result-${r.id}` ? <Loader2 className="h-3 w-3 animate-spin" /> : <CheckCircle2 className="h-3 w-3" />}
                                  Résoudre
                                </Button>
                              )}
                              <ArrowRight className="h-4 w-4 text-muted-foreground" />
                            </div>
                          </div>
                        </div>
                      )
                    })
                  )}
                </TabsContent>
              </Tabs>
            )}
          </div>
        </main>
      </div>
    </div>
  )
}

export default function MyAssignmentsPage() {
  return (
    <Suspense>
      <MyAssignmentsInner />
    </Suspense>
  )
}

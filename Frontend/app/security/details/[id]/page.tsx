'use client'

import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import Link from 'next/link'
import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Breadcrumb, BreadcrumbItem, BreadcrumbLink, BreadcrumbList,
  BreadcrumbPage, BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import {
  ArrowLeft, Shield, FileCode, Globe, Package, AlertTriangle,
  Clock, GitBranch, ExternalLink, Loader2, Folder, Scan,
} from 'lucide-react'

interface VulnDetail {
  id: number
  title: string
  severity: string
  status: string
  vulnType: string
  source: string | null
  cweId: string | null
  owaspCategory: string | null
  file: string | null
  line: number | null
  snippet: string | null
  endpoint: string | null
  httpMethod: string | null
  parameter: string | null
  description: string | null
  risk: string | null
  recommendation: string | null
  fixExample: string | null
  evidence: string | null
  assignedTo: string | null
  assignedToUserId: number | null
  createdAt: string | null
  updatedAt: string | null
  project: {
    id: number
    name: string
    description: string | null
  } | null
  scan: {
    id: number
    scanRef: string
    scanType: string
    engine: string
    status: string
    startedAt: string | null
    completedAt: string | null
    duration: string | null
    branch: string | null
    commitHash: string | null
    coverage: number | null
  } | null
}

function sevColor(s: string) {
  switch (s) {
    case 'CRITICAL': return 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400'
    case 'HIGH': return 'bg-orange-100 text-orange-800 dark:bg-orange-950 dark:text-orange-400'
    case 'MEDIUM': return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-950 dark:text-yellow-400'
    case 'LOW': return 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400'
    default: return 'bg-gray-100 text-gray-800'
  }
}

function vulnTypeLabel(type: string) {
  switch (type) {
    case 'SAST': return { icon: <FileCode className="h-4 w-4" />, label: 'Static Analysis (SAST)' }
    case 'DAST': return { icon: <Globe className="h-4 w-4" />, label: 'Dynamic Analysis (DAST)' }
    case 'SCA': return { icon: <Package className="h-4 w-4" />, label: 'Composition Analysis (SCA)' }
    default: return { icon: <Shield className="h-4 w-4" />, label: type }
  }
}

export default function SecurityDetailPage() {
  const params = useParams<{ id: string }>()
  const router = useRouter()
  const [vuln, setVuln] = useState<VulnDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!params?.id) return
    setLoading(true)
    fetch(`/api/security/vulnerabilities/${params.id}/details`)
      .then(r => {
        if (!r.ok) throw new Error('Vulnerabilite introuvable')
        return r.json()
      })
      .then(setVuln)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [params?.id])

  const typeInfo = vuln ? vulnTypeLabel(vuln.vulnType) : null

  return (
    <div className="flex h-screen bg-background">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto">
          <div className="max-w-[1100px] mx-auto px-6 py-6 space-y-6">

            <Breadcrumb>
              <BreadcrumbList>
                <BreadcrumbItem>
                  <BreadcrumbLink asChild>
                    <Link href="/my-assignments">Mes Assignements</Link>
                  </BreadcrumbLink>
                </BreadcrumbItem>
                <BreadcrumbSeparator />
                <BreadcrumbItem>
                  <BreadcrumbPage>{vuln?.title ?? 'Detail vulnerabilite'}</BreadcrumbPage>
                </BreadcrumbItem>
              </BreadcrumbList>
            </Breadcrumb>

            <div className="flex items-start justify-between gap-4">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-primary/10">
                  <Shield className="h-6 w-6 text-primary" />
                </div>
                <div>
                  <h1 className="text-xl font-bold">Detail de la vulnerabilite</h1>
                  <p className="text-xs text-muted-foreground">Informations completes sur la vulnerabilite et le projet teste</p>
                </div>
              </div>
              <Button variant="outline" size="sm" className="gap-2" onClick={() => router.push('/my-assignments')}>
                <ArrowLeft className="h-4 w-4" />
                Retour
              </Button>
            </div>

            {loading ? (
              <div className="flex items-center justify-center py-20">
                <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
              </div>
            ) : error ? (
              <Card className="p-6">
                <p className="text-sm text-destructive">{error}</p>
              </Card>
            ) : vuln ? (
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Main content */}
                <div className="lg:col-span-2 space-y-6">

                  {/* Vuln header card */}
                  <Card>
                    <CardHeader className="pb-3">
                      <div className="flex items-center gap-2 flex-wrap">
                        {typeInfo?.icon}
                        <CardTitle className="text-lg">{vuln.title}</CardTitle>
                      </div>
                      <div className="flex items-center gap-2 flex-wrap mt-2">
                        <Badge variant="outline" className={sevColor(vuln.severity)}>{vuln.severity}</Badge>
                        <Badge variant="outline">{vuln.status}</Badge>
                        <Badge variant="secondary">{typeInfo?.label}</Badge>
                        {vuln.source && <Badge variant="outline" className="text-[10px]">{vuln.source}</Badge>}
                      </div>
                    </CardHeader>
                    <CardContent className="space-y-4">

                      {/* Identifiers */}
                      <div className="flex items-center gap-4 text-xs text-muted-foreground">
                        {vuln.cweId && (
                          <a
                            href={`https://cwe.mitre.org/data/definitions/${vuln.cweId.replace(/\D/g, '')}.html`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex items-center gap-1 hover:text-foreground transition-colors"
                          >
                            <ExternalLink className="h-3 w-3" />
                            <span className="font-mono">{vuln.cweId}</span>
                          </a>
                        )}
                        {vuln.owaspCategory && <span>OWASP {vuln.owaspCategory}</span>}
                        {vuln.assignedTo && (
                          <span className="text-foreground font-medium">Assigne a: {vuln.assignedTo}</span>
                        )}
                      </div>

                      {/* Location */}
                      {(vuln.file || vuln.endpoint) && (
                        <div className="rounded-md bg-muted/50 border p-3">
                          <p className="text-xs font-medium text-muted-foreground mb-1">LOCALISATION</p>
                          {vuln.file && (
                            <p className="text-sm font-mono">
                              {vuln.file}{vuln.line ? `:${vuln.line}` : ''}
                            </p>
                          )}
                          {vuln.endpoint && (
                            <p className="text-sm font-mono mt-1">
                              {vuln.httpMethod && <span className="font-bold mr-2">{vuln.httpMethod}</span>}
                              {vuln.endpoint}
                              {vuln.parameter && <span className="text-muted-foreground ml-2">param: {vuln.parameter}</span>}
                            </p>
                          )}
                        </div>
                      )}

                      {/* Description */}
                      {vuln.description && (
                        <div>
                          <p className="text-xs font-medium text-muted-foreground mb-1">DESCRIPTION</p>
                          <p className="text-sm leading-relaxed">{vuln.description}</p>
                        </div>
                      )}

                      {/* Risk */}
                      {vuln.risk && (
                        <div>
                          <p className="text-xs font-medium text-muted-foreground mb-1">RISQUE</p>
                          <div className="text-sm bg-red-500/5 border border-red-500/20 rounded p-3 leading-relaxed">
                            <AlertTriangle className="h-4 w-4 text-red-500 inline mr-2" />
                            {vuln.risk}
                          </div>
                        </div>
                      )}

                      {/* Evidence / Snippet */}
                      {(vuln.evidence || vuln.snippet) && (
                        <div>
                          <p className="text-xs font-medium text-muted-foreground mb-1">
                            {vuln.snippet ? 'CODE EXTRAIT' : 'EVIDENCE'}
                          </p>
                          <pre className="text-xs bg-muted border rounded p-3 font-mono overflow-x-auto whitespace-pre-wrap max-h-60 overflow-y-auto">
                            {vuln.snippet || vuln.evidence}
                          </pre>
                        </div>
                      )}

                      {/* Recommendation */}
                      {vuln.recommendation && (
                        <div>
                          <p className="text-xs font-medium text-muted-foreground mb-1">RECOMMENDATION</p>
                          <div className="text-sm bg-green-500/5 border border-green-500/20 rounded p-3 leading-relaxed">
                            {vuln.recommendation}
                          </div>
                        </div>
                      )}

                      {/* Fix example */}
                      {vuln.fixExample && (
                        <div>
                          <p className="text-xs font-medium text-muted-foreground mb-1">EXEMPLE DE CORRECTION</p>
                          <pre className="text-xs bg-green-500/5 border border-green-500/20 rounded p-3 font-mono overflow-x-auto whitespace-pre-wrap">
                            {vuln.fixExample}
                          </pre>
                        </div>
                      )}
                    </CardContent>
                  </Card>
                </div>

                {/* Sidebar info */}
                <div className="space-y-6">

                  {/* Project info */}
                  {vuln.project && (
                    <Card>
                      <CardHeader className="pb-3">
                        <CardTitle className="text-sm flex items-center gap-2">
                          <Folder className="h-4 w-4" />
                          Projet teste
                        </CardTitle>
                      </CardHeader>
                      <CardContent className="space-y-3">
                        <div>
                          <p className="text-xs text-muted-foreground">Nom du projet</p>
                          <p className="text-sm font-medium">{vuln.project.name}</p>
                        </div>
                        {vuln.project.description && (
                          <div>
                            <p className="text-xs text-muted-foreground">Description</p>
                            <p className="text-sm">{vuln.project.description}</p>
                          </div>
                        )}
                        <div>
                          <p className="text-xs text-muted-foreground">ID Projet</p>
                          <p className="text-sm font-mono">#{vuln.project.id}</p>
                        </div>
                      </CardContent>
                    </Card>
                  )}

                  {/* Scan info */}
                  {vuln.scan && (
                    <Card>
                      <CardHeader className="pb-3">
                        <CardTitle className="text-sm flex items-center gap-2">
                          <Scan className="h-4 w-4" />
                          Informations du scan
                        </CardTitle>
                      </CardHeader>
                      <CardContent className="space-y-3">
                        <div>
                          <p className="text-xs text-muted-foreground">Reference</p>
                          <p className="text-sm font-mono">{vuln.scan.scanRef}</p>
                        </div>
                        <div>
                          <p className="text-xs text-muted-foreground">Moteur</p>
                          <p className="text-sm">{vuln.scan.engine}</p>
                        </div>
                        <div>
                          <p className="text-xs text-muted-foreground">Type de scan</p>
                          <p className="text-sm">{vuln.scan.scanType}</p>
                        </div>
                        <div>
                          <p className="text-xs text-muted-foreground">Statut</p>
                          <Badge variant="outline" className="text-xs">{vuln.scan.status}</Badge>
                        </div>
                        {vuln.scan.branch && (
                          <div>
                            <p className="text-xs text-muted-foreground">Branche</p>
                            <p className="text-sm flex items-center gap-1">
                              <GitBranch className="h-3 w-3" />{vuln.scan.branch}
                            </p>
                          </div>
                        )}
                        {vuln.scan.commitHash && (
                          <div>
                            <p className="text-xs text-muted-foreground">Commit</p>
                            <p className="text-sm font-mono">{vuln.scan.commitHash.substring(0, 8)}</p>
                          </div>
                        )}
                        {vuln.scan.duration && (
                          <div>
                            <p className="text-xs text-muted-foreground">Duree</p>
                            <p className="text-sm flex items-center gap-1">
                              <Clock className="h-3 w-3" />{vuln.scan.duration}
                            </p>
                          </div>
                        )}
                        {vuln.scan.startedAt && (
                          <div>
                            <p className="text-xs text-muted-foreground">Date du scan</p>
                            <p className="text-sm">{new Date(vuln.scan.startedAt).toLocaleString('fr-FR')}</p>
                          </div>
                        )}
                        {vuln.scan.coverage != null && (
                          <div>
                            <p className="text-xs text-muted-foreground">Couverture</p>
                            <p className="text-sm">{vuln.scan.coverage}%</p>
                          </div>
                        )}
                      </CardContent>
                    </Card>
                  )}

                  {/* Dates */}
                  <Card>
                    <CardHeader className="pb-3">
                      <CardTitle className="text-sm flex items-center gap-2">
                        <Clock className="h-4 w-4" />
                        Dates
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-3">
                      {vuln.createdAt && (
                        <div>
                          <p className="text-xs text-muted-foreground">Detectee le</p>
                          <p className="text-sm">{new Date(vuln.createdAt).toLocaleString('fr-FR')}</p>
                        </div>
                      )}
                      {vuln.updatedAt && (
                        <div>
                          <p className="text-xs text-muted-foreground">Mise a jour</p>
                          <p className="text-sm">{new Date(vuln.updatedAt).toLocaleString('fr-FR')}</p>
                        </div>
                      )}
                    </CardContent>
                  </Card>
                </div>
              </div>
            ) : null}
          </div>
        </main>
      </div>
    </div>
  )
}

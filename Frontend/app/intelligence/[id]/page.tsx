'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'next/navigation'
import { ArrowLeft, Download, Loader2, RefreshCw, ShieldAlert } from 'lucide-react'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { downloadUxEvaluationReport, executeUxEvaluation, getUxEvaluation, type UxEvaluationDto } from '@/lib/api-client'

function platformFromType(value?: string | null) {
  if (!value) return 'UX'
  return value === 'WEB' ? 'Web' : value === 'MOBILE' ? 'Mobile' : value
}

function statusTone(status?: string | null) {
  switch (status) {
    case 'COMPLETED':
      return 'success' as const
    case 'FAILED':
      return 'destructive' as const
    case 'RUNNING':
      return 'outline' as const
    case 'PENDING':
    default:
      return 'secondary' as const
  }
}

function statusLabel(status?: string | null) {
  switch (status) {
    case 'COMPLETED':
      return 'Terminé'
    case 'FAILED':
      return 'Échoué'
    case 'RUNNING':
      return 'En cours'
    case 'PENDING':
      return 'En attente'
    default:
      return status ?? 'Inconnu'
  }
}

function extractTestSummary(logs?: string | null) {
  if (!logs) return 'Aucun extrait TEST_SUMMARY trouvé dans les logs.'

  const marker = logs.indexOf('TEST_SUMMARY:')
  if (marker === -1) return 'Aucun extrait TEST_SUMMARY trouvé dans les logs.'

  const afterMarker = logs.slice(marker + 'TEST_SUMMARY:'.length).trim()
  const lines = afterMarker.split(/\r?\n/)
  const selected: string[] = []

  for (const line of lines) {
    if (!line.trim()) {
      if (selected.length) break
      continue
    }

    if (/^[A-Z_]+:/.test(line) && selected.length) break
    selected.push(line.trim())
    if (selected.length >= 8) break
  }

  return selected.length ? selected.join('\n') : afterMarker.slice(0, 400)
}

function LoadingState() {
  return (
    <div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
      <Card className="border-border/60 bg-card/90">
        <CardHeader className="space-y-3">
          <Skeleton className="h-8 w-2/5" />
          <Skeleton className="h-5 w-1/3" />
        </CardHeader>
        <CardContent className="space-y-4">
          <Skeleton className="h-40 w-full rounded-2xl" />
          <Skeleton className="h-28 w-full rounded-2xl" />
        </CardContent>
      </Card>
      <div className="space-y-6">
        <Card className="border-border/60 bg-card/90">
          <CardHeader>
            <Skeleton className="h-6 w-1/2" />
          </CardHeader>
          <CardContent className="space-y-3">
            <Skeleton className="h-24 w-full rounded-2xl" />
            <Skeleton className="h-24 w-full rounded-2xl" />
          </CardContent>
        </Card>
        <Card className="border-border/60 bg-card/90">
          <CardHeader>
            <Skeleton className="h-6 w-1/2" />
          </CardHeader>
          <CardContent className="space-y-3">
            <Skeleton className="h-20 w-full rounded-2xl" />
            <Skeleton className="h-20 w-full rounded-2xl" />
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function EvaluationHeader({
  item,
  onRerun,
  isRerunning,
}: {
  item: UxEvaluationDto
  onRerun: () => Promise<void>
  isRerunning: boolean
}) {
  return (
    <div className="rounded-3xl border border-border/60 bg-card/90 p-6 shadow-sm md:p-8">
      <div className="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="success" className="uppercase tracking-wide">
              Fonctionnel IA
            </Badge>
            <Badge variant="secondary">{platformFromType(item.platform)}</Badge>
            <Badge variant={statusTone(item.status)}>{statusLabel(item.status)}</Badge>
          </div>
          <div className="space-y-2">
            <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">Évaluation #{item.id}</h1>
            <p className="text-sm text-muted-foreground md:text-base">
              Enregistrée le {new Date(item.executedAt ?? item.createdAt).toLocaleString('fr-FR')}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap gap-3">
          <Button asChild variant="outline">
            <Link href="/functional-evaluation">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Retour
            </Link>
          </Button>
          <Button onClick={() => void onRerun()} disabled={isRerunning}>
            {isRerunning ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCw className="mr-2 h-4 w-4" />}
            Relancer l’évaluation
          </Button>
        </div>
      </div>
    </div>
  )
}

export default function IntelligenceDetailPage() {
  const params = useParams<{ id: string }>()
  const [item, setItem] = useState<UxEvaluationDto | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)
  const [downloadState, setDownloadState] = useState<'idle' | 'loading'>('idle')
  const [rerunState, setRerunState] = useState<'idle' | 'loading'>('idle')
  const [retryKey, setRetryKey] = useState(0)

  const rawId = Array.isArray(params.id) ? params.id[0] : params.id
  const id = Number(rawId)

  useEffect(() => {
    if (!Number.isFinite(id) || id <= 0) {
      setState('error')
      setError('Identifiant UX invalide.')
      setItem(null)
      return
    }

    let active = true

    const load = async () => {
      setState('loading')
      setError(null)

      try {
        const data = await getUxEvaluation(id)
        if (!active) return

        setItem(data)
        setState('ready')
      } catch (err) {
        if (!active) return

        setItem(null)
        setState('error')
        setError(err instanceof Error ? err.message : 'Impossible de charger cette évaluation fonctionnelle.')
      }
    }

    void load()

    return () => {
      active = false
    }
  }, [id, retryKey])

  useEffect(() => {
    if (rerunState === 'loading' && state === 'ready') {
      setRerunState('idle')
    }
    if (rerunState === 'loading' && state === 'error') {
      setRerunState('idle')
    }
  }, [rerunState, state])

  const summary = useMemo(() => item?.testSummary ?? item?.logs ?? 'Aucun extrait TEST_SUMMARY trouvé.', [item?.testSummary, item?.logs])
  const pageContent = useMemo(() => item?.pageContent ?? 'Aucun contenu de page disponible.', [item?.pageContent])

  const handleRerun = async () => {
    if (!item || rerunState === 'loading') return

    setRerunState('loading')
    setError(null)

    try {
      await executeUxEvaluation(item.id)
      await new Promise((resolve) => setTimeout(resolve, 2000))
      setRetryKey((current) => current + 1)
    } catch (err) {
      setRerunState('idle')
      setError(err instanceof Error ? err.message : 'Impossible de relancer l’évaluation fonctionnelle.')
    }
  }

  const downloadReport = async () => {
    if (!item) return
    setDownloadState('loading')

    try {
      const blob = await downloadUxEvaluationReport(item.id)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `functional-evaluation-${item.id}-report.pdf`
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Impossible de télécharger le rapport PDF.')
    } finally {
      setDownloadState('idle')
    }
  }

  return (
    <div className="flex min-h-screen bg-[radial-gradient(circle_at_top_right,_rgba(14,165,233,0.08),_transparent_28%),linear-gradient(to_bottom_left,_rgba(16,185,129,0.08),_transparent_34%)]">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto p-6 lg:p-8">
          <div className="mx-auto flex w-full max-w-7xl flex-col gap-6">
            {state === 'loading' ? (
              <LoadingState />
            ) : state === 'error' ? (
              <Card className="border-destructive/30 bg-destructive/5">
                <CardHeader>
                  <CardTitle>Impossible de charger l’évaluation fonctionnelle</CardTitle>
                  <CardDescription>{error}</CardDescription>
                </CardHeader>
                <CardContent className="flex flex-wrap gap-3">
                  <Button onClick={() => setRetryKey((current) => current + 1)}>
                    <Loader2 className="mr-2 h-4 w-4" />
                    Réessayer
                  </Button>
                  <Button variant="outline" asChild>
                    <Link href="/functional-evaluation">Retour à la liste</Link>
                  </Button>
                </CardContent>
              </Card>
            ) : item ? (
              <>
                <EvaluationHeader item={item} onRerun={handleRerun} isRerunning={rerunState === 'loading'} />

                <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
                  <Card className="border-border/60 bg-card/90 shadow-sm">
                    <CardHeader className="flex flex-row items-start justify-between gap-4">
                      <div>
                        <CardTitle>Analyse fonctionnelle de l’IA</CardTitle>
                        <CardDescription>Retour consolidé à partir de l’exécution et des logs.</CardDescription>
                      </div>
                      <Button onClick={downloadReport} disabled={downloadState === 'loading'}>
                        {downloadState === 'loading' ? (
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        ) : (
                          <Download className="mr-2 h-4 w-4" />
                        )}
                        Télécharger le PDF
                      </Button>
                    </CardHeader>
                    <CardContent>
                      <div className="rounded-3xl border border-border/60 bg-muted/25 p-5 text-sm leading-7 text-foreground">
                        {item.aiAnalysis ?? 'Aucune analyse IA disponible pour cette évaluation.'}
                      </div>
                    </CardContent>
                  </Card>

                  <div className="space-y-6">
                    <Card className="border-border/60 bg-card/90 shadow-sm">
                      <CardHeader>
                        <CardTitle>Métriques collectées</CardTitle>
                        <CardDescription>Extrait TEST_SUMMARY issu des logs d’exécution.</CardDescription>
                      </CardHeader>
                      <CardContent>
                        <pre className="whitespace-pre-wrap rounded-3xl border border-border/60 bg-muted/30 p-4 text-sm leading-6 text-foreground">
                          {summary}
                        </pre>
                      </CardContent>
                    </Card>

                    <Card className="border-border/60 bg-card/90 shadow-sm">
                      <Accordion type="single" collapsible defaultValue="execution-logs">
                        <AccordionItem value="execution-logs" className="border-none">
                          <AccordionTrigger className="px-6 py-4 text-left hover:no-underline">
                            <div className="text-left">
                              <CardTitle>Logs d’exécution</CardTitle>
                              <CardDescription>Journal brut capturé pendant la compilation et l’exécution.</CardDescription>
                            </div>
                          </AccordionTrigger>
                          <AccordionContent className="px-6 pb-6">
                            <pre className="max-h-96 overflow-auto rounded-3xl border border-slate-800/80 bg-slate-950 p-4 font-mono text-xs leading-6 text-slate-100">
                              {item.logs?.trim() ? item.logs : 'Aucun log d’exécution disponible.'}
                            </pre>
                          </AccordionContent>
                        </AccordionItem>
                      </Accordion>
                    </Card>

                    <Card className="border-border/60 bg-card/90 shadow-sm">
                      <CardHeader>
                        <CardTitle>Détails techniques</CardTitle>
                      </CardHeader>
                      <CardContent className="space-y-3 text-sm">
                        <div className="flex items-center justify-between gap-3 rounded-2xl border border-border/60 bg-muted/25 px-4 py-3">
                          <span className="text-muted-foreground">Plateforme</span>
                          <span className="font-medium">{item.platform}</span>
                        </div>
                        <div className="flex items-center justify-between gap-3 rounded-2xl border border-border/60 bg-muted/25 px-4 py-3">
                          <span className="text-muted-foreground">Statut</span>
                          <Badge variant={statusTone(item.status)}>{statusLabel(item.status)}</Badge>
                        </div>
                        <div className="flex items-center justify-between gap-3 rounded-2xl border border-border/60 bg-muted/25 px-4 py-3">
                          <span className="text-muted-foreground">Durée</span>
                          <span className="font-medium">{item.durationMs != null ? `${item.durationMs} ms` : 'Non disponible'}</span>
                        </div>
                        {item.errorMessage ? (
                          <div className="rounded-2xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-destructive">
                            <div className="mb-1 flex items-center gap-2 font-medium">
                              <ShieldAlert className="h-4 w-4" />
                              Erreur remontée
                            </div>
                            <p className="text-sm leading-6">{item.errorMessage}</p>
                          </div>
                        ) : null}
                      </CardContent>
                    </Card>
                  </div>
                </div>

                <Card className="border-dashed border-border/70 bg-card/70">
                  <CardHeader>
                    <CardTitle>Contexte fonctionnel indépendant</CardTitle>
                    <CardDescription>
                      Cette vue ne contient que les résultats FUNCTIONAL_WEB et FUNCTIONAL_MOBILE et reste séparée des autres familles de tests.
                    </CardDescription>
                  </CardHeader>
                </Card>

              <Card className="border-border/60 bg-card/90 shadow-sm">
                <CardHeader>
                  <CardTitle>Contenu de page</CardTitle>
                  <CardDescription>Valeur extraite depuis le marqueur PAGE_CONTENT.</CardDescription>
                </CardHeader>
                <CardContent>
                  <pre className="whitespace-pre-wrap rounded-3xl border border-border/60 bg-muted/30 p-4 text-sm leading-6 text-foreground">
                    {pageContent}
                  </pre>
                </CardContent>
              </Card>
              </>
            ) : null}
          </div>
        </main>
      </div>
    </div>
  )
}
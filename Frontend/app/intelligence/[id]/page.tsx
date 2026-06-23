'use client'

import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'next/navigation'
import { ArrowLeft, ChevronDown, ChevronUp, Eye, Loader2, Play, RefreshCw, Square, ZoomIn, Radio, ListChecks } from 'lucide-react'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  getFunctionalEvaluation,
  executeFunctionalEvaluation,
  stopFunctionalEvaluation,
  pauseFunctionalEvaluation,
  resumeFunctionalEvaluation,
  getEvaluationSteps,
  type UxEvaluationDto,
  type UxNavigationStepDto,
} from '@/lib/api-client'
import { useEvaluationStream } from '@/hooks/useEvaluationStream'
import { EvaluationLiveView } from '@/components/EvaluationLiveView'

/* ─── Helpers ────────────────────────────────────────────────────────────── */

function statusTone(status?: string | null) {
  switch (status) {
    case 'COMPLETED': return 'success' as const
    case 'FAILED': return 'destructive' as const
    case 'RUNNING': return 'outline' as const
    default: return 'secondary' as const
  }
}

function statusLabel(status?: string | null) {
  switch (status) {
    case 'COMPLETED': return 'Terminé'
    case 'FAILED': return 'Échoué'
    case 'RUNNING': return 'En cours…'
    case 'PENDING': return 'En attente'
    default: return status ?? 'Inconnu'
  }
}

/** Parse the first X/10 score found in the AI analysis text */
function extractUxScore(analysis: string | null): number | null {
  if (!analysis) return null
  const match = analysis.match(/(\d+(?:[.,]\d+)?)\s*\/\s*10/)
  if (!match) return null
  const score = parseFloat(match[1].replace(',', '.'))
  return isNaN(score) || score < 0 || score > 10 ? null : score
}

function scoreColor(score: number): string {
  if (score >= 8) return 'text-green-500'
  if (score >= 6) return 'text-yellow-500'
  return 'text-red-500'
}

function scoreBg(score: number): string {
  if (score >= 8) return 'bg-green-500/10 border-green-500/30'
  if (score >= 6) return 'bg-yellow-500/10 border-yellow-500/30'
  return 'bg-red-500/10 border-red-500/30'
}

/** Display label + color for action type */
function actionBadge(actionType?: string | null) {
  switch ((actionType ?? '').toUpperCase()) {
    case 'CLICK':    return { label: '🖱 Clic',        cls: 'bg-blue-500/10 text-blue-600 border-blue-500/30' }
    case 'FILL':     return { label: '✏️ Saisie',      cls: 'bg-purple-500/10 text-purple-600 border-purple-500/30' }
    case 'SCROLL':   return { label: '↕ Défilement',   cls: 'bg-slate-500/10 text-slate-600 border-slate-500/30' }
    case 'NAVIGATE': return { label: '🧭 Navigation',  cls: 'bg-cyan-500/10 text-cyan-600 border-cyan-500/30' }
    case 'BACK':     return { label: '⬅ Retour',       cls: 'bg-amber-500/10 text-amber-600 border-amber-500/30' }
    case 'DONE':     return { label: '✅ Fin',          cls: 'bg-green-500/10 text-green-600 border-green-500/30' }
    case 'SKIP':     return { label: '⚠ Ignorée',      cls: 'bg-orange-500/10 text-orange-600 border-orange-500/30' }
    default:         return null
  }
}

/* ─── Screenshot lightbox ────────────────────────────────────────────────── */

function Lightbox({ src, onClose }: { src: string; onClose: () => void }) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 cursor-pointer"
      onClick={onClose}
    >
      <img
        src={src}
        alt="Screenshot plein écran"
        className="max-h-[90vh] max-w-[90vw] rounded-lg shadow-2xl"
      />
    </div>
  )
}

/* ─── Step card ──────────────────────────────────────────────────────────── */

function StepCard({
  step,
  isLast,
  isLive,
  onZoom,
}: {
  step: UxNavigationStepDto
  isLast: boolean
  isLive: boolean
  onZoom: (src: string) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const imgSrc = step.screenshotBase64
    ? `data:image/png;base64,${step.screenshotBase64}`
    : null
  const badge = actionBadge(step.actionType)

  return (
    <div className="flex gap-4">
      {/* Timeline line + number */}
      <div className="flex flex-col items-center">
        <div
          className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-bold
            ${isLive ? 'animate-pulse bg-primary text-primary-foreground' : 'bg-primary/90 text-primary-foreground'}`}
        >
          {step.stepNumber}
        </div>
        {!isLast && <div className="w-0.5 flex-1 bg-border" />}
      </div>

      {/* Content */}
      <div className="flex-1 pb-6">
        <div className="flex items-start justify-between gap-2">
          <div className="flex-1 min-w-0">
            <div className="flex flex-wrap items-center gap-1.5 mb-0.5">
              {badge && (
                <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-medium ${badge.cls}`}>
                  {badge.label}
                </span>
              )}
              <p className="font-semibold text-sm leading-snug">{step.observation ?? step.stepName}</p>
            </div>
            <p className="text-xs text-muted-foreground mt-0.5 space-x-1">
              {step.actionPerformed && (
                <span>{step.actionPerformed}</span>
              )}
              {step.selector && (
                <span className="font-mono bg-muted/60 px-1 py-0.5 rounded text-[10px]">
                  {step.selector}
                </span>
              )}
              {step.fillValue && (
                <span className="italic text-muted-foreground/80">
                  « {step.fillValue} »
                </span>
              )}
            </p>
            {step.pageUrl && (
              <p className="text-[10px] text-muted-foreground/60 font-mono mt-0.5 truncate max-w-xs">
                {step.pageUrl}
              </p>
            )}
          </div>
          {step.pageTitle && (
            <Badge variant="outline" className="shrink-0 text-[10px]">
              {step.pageTitle}
            </Badge>
          )}
        </div>

        {/* Screenshot thumbnail */}
        {imgSrc && (
          <div className="mt-3 group relative">
            <img
              src={imgSrc}
              alt={`Étape ${step.stepNumber}`}
              className={`rounded-lg border border-border shadow-sm transition-all duration-200 cursor-pointer
                hover:shadow-md hover:border-primary/40
                ${expanded ? 'w-full max-w-2xl' : 'w-80 max-h-48 object-cover object-top'}`}
              onClick={() => setExpanded(!expanded)}
            />
            <div className="absolute top-2 right-2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); onZoom(imgSrc) }}
                className="rounded-full bg-black/60 p-1.5 text-white hover:bg-black/80"
                title="Plein écran"
              >
                <ZoomIn size={14} />
              </button>
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); setExpanded(!expanded) }}
                className="rounded-full bg-black/60 p-1.5 text-white hover:bg-black/80"
                title={expanded ? 'Réduire' : 'Agrandir'}
              >
                {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

/* ─── Main page ──────────────────────────────────────────────────────────── */

export default function EvaluationDetailPage() {
  const params = useParams<{ id: string }>()
  const rawId = Array.isArray(params.id) ? params.id[0] : params.id
  const id = Number(rawId)

  const [item, setItem] = useState<UxEvaluationDto | null>(null)
  const [steps, setSteps] = useState<UxNavigationStepDto[]>([])
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)
  const [rerunning, setRerunning] = useState(false)
  const [lightbox, setLightbox] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<'live' | 'steps' | 'report'>('live')
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const stepsEndRef = useRef<HTMLDivElement>(null)

  // ── WebSocket stream ─────────────────────────────────────────────
  const { messages, liveScreenshot, connected, needsInput, currentBackend, sendAnswer, clearMessages } =
    useEvaluationStream({
      evaluationId: Number.isFinite(id) && id > 0 ? id : null,
      baseUrl: 'http://localhost:8083',
      onCompleted: () => { void load() },
      onFailed:    () => { void load() },
    })

  // ── Load evaluation ──────────────────────────────────────────────
  const load = useCallback(async () => {
    if (!Number.isFinite(id) || id <= 0) {
      setState('error')
      setError('Identifiant invalide.')
      return
    }
    try {
      const data = await getFunctionalEvaluation(id)
      setItem(data)
      setSteps(data.navigationSteps ?? [])
      setState('ready')
    } catch (err) {
      setItem(null)
      setState('error')
      setError(err instanceof Error ? err.message : 'Impossible de charger l\'évaluation.')
    }
  }, [id])

  useEffect(() => { void load() }, [load])

  // ── Poll steps when RUNNING (fallback — only when WebSocket is NOT connected) ──
  // When WS is active, onCompleted/onFailed already calls load() — no need to poll.
  useEffect(() => {
    if (item?.status !== 'RUNNING') {
      if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null }
      return
    }

    // WebSocket is live → use a slow 30s poll just as safety fallback
    // WebSocket is down → poll every 5s to keep the UI somewhat fresh
    const intervalMs = connected ? 30_000 : 5_000

    const poll = async () => {
      try {
        const freshEval = await getFunctionalEvaluation(id)
        setItem(freshEval)

        if (freshEval.status !== 'RUNNING') {
          // Final load to get all steps
          const freshSteps = await getEvaluationSteps(id)
          setSteps(freshSteps.length ? freshSteps : (freshEval.navigationSteps ?? []))
          setStopping(false)
          if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null }
        } else if (!connected) {
          // Only refresh steps from DB when WS is disconnected
          const freshSteps = await getEvaluationSteps(id)
          setSteps(freshSteps)
        }
      } catch { /* ignore poll errors */ }
    }

    pollRef.current = setInterval(poll, intervalMs)
    return () => { if (pollRef.current) clearInterval(pollRef.current) }
  }, [item?.status, id, connected])

  // Auto-scroll to last step when new step arrives
  useEffect(() => {
    stepsEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [steps.length])

  // ── Stop ───────────────────────────────────────────────────────
  const [stopping, setStopping] = useState(false)
  const handleStop = async () => {
    if (!item || stopping) return
    setStopping(true)
    try {
      await stopFunctionalEvaluation(item.id)
    } catch { /* best effort */ }
  }

  // ── Pause / Resume (HITL) ────────────────────────────────────────
  const [paused, setPaused] = useState(false)
  const [pausing, setPausing] = useState(false)
  const handlePauseResume = async () => {
    if (!item || pausing) return
    setPausing(true)
    try {
      if (paused) {
        await resumeFunctionalEvaluation(item.id)
        setPaused(false)
      } else {
        await pauseFunctionalEvaluation(item.id)
        setPaused(true)
      }
    } catch { /* best effort */ } finally {
      setPausing(false)
    }
  }

  // ── Re-run ──────────────────────────────────────────────────────
  const handleRerun = async () => {
    if (!item || rerunning) return
    setRerunning(true)
    clearMessages()
    setActiveTab('live') // switch to live view when starting
    try {
      await executeFunctionalEvaluation(item.id)
      await new Promise(r => setTimeout(r, 1500))
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Échec du relancement.')
    } finally {
      setRerunning(false)
    }
  }

  // Switch to live tab when status becomes RUNNING
  useEffect(() => {
    if (item?.status === 'RUNNING') setActiveTab('live')
  }, [item?.status])

  // ── Render: loading ──────────────────────────────────────────────
  if (state === 'loading') {
    return (
      <div className="flex min-h-screen bg-background">
        <Sidebar />
        <div className="flex flex-1 flex-col"><Header />
          <main className="flex-1 p-6"><div className="mx-auto max-w-7xl space-y-6">
            <Skeleton className="h-32 w-full rounded-2xl" />
            <Skeleton className="h-64 w-full rounded-2xl" />
          </div></main>
        </div>
      </div>
    )
  }

  // ── Render: error ───────────────────────────────────────────────
  if (state === 'error' || !item) {
    return (
      <div className="flex min-h-screen bg-background">
        <Sidebar />
        <div className="flex flex-1 flex-col"><Header />
          <main className="flex-1 p-6">
            <Card className="mx-auto max-w-xl border-destructive/30 bg-destructive/5">
              <CardHeader>
                <CardTitle>Erreur</CardTitle>
                <CardDescription>{error}</CardDescription>
              </CardHeader>
              <CardContent>
                <Button onClick={load}>Réessayer</Button>
              </CardContent>
            </Card>
          </main>
        </div>
      </div>
    )
  }

  // ── Render: ready ───────────────────────────────────────────────
  const isRunning = item.status === 'RUNNING'

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto p-6 lg:p-8">
          <div className="mx-auto max-w-7xl space-y-6">

            {/* ── Header ────────────────────────────────────────── */}
            <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between rounded-2xl border border-border/60 bg-card/90 p-6 shadow-sm">
              <div className="space-y-3">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant="secondary">UX Evaluation</Badge>
                  <Badge variant={statusTone(item.status)}>{statusLabel(item.status)}</Badge>
                  {currentBackend && (
                    <span className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-[11px] font-medium
                      ${currentBackend.includes('🟢') ? 'bg-green-500/10 text-green-600 border-green-500/30' :
                        currentBackend.includes('🟡') ? 'bg-yellow-500/10 text-yellow-600 border-yellow-500/30' :
                        currentBackend.includes('🔴') ? 'bg-red-500/10 text-red-600 border-red-500/30' :
                        'bg-muted text-muted-foreground border-border'}`}>
                      {currentBackend}
                    </span>
                  )}
                  {isRunning && (
                    <span className="text-xs text-muted-foreground animate-pulse">
                      {steps.length} étape{steps.length > 1 ? 's' : ''} explorée{steps.length > 1 ? 's' : ''}…
                    </span>
                  )}
                </div>
                <h1 className="text-2xl font-semibold">Évaluation #{item.id}</h1>
                <p className="text-sm text-muted-foreground">
                  {item.url} · {new Date(item.executedAt ?? item.createdAt).toLocaleString('fr-FR')}
                  {item.durationMs != null && ` · ${(item.durationMs / 1000).toFixed(1)}s`}
                </p>
              </div>
              <div className="flex gap-2">
                <Button variant="outline" asChild>
                  <Link href="/functional-evaluation"><ArrowLeft className="mr-2 h-4 w-4" />Retour</Link>
                </Button>
                {isRunning ? (
                  <>
                    <Button variant="outline" onClick={handlePauseResume} disabled={pausing}>
                      {pausing ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                      {paused ? 'Reprendre' : 'Pause'}
                    </Button>
                    <Button variant="destructive" onClick={handleStop} disabled={stopping}>
                      {stopping ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Square className="mr-2 h-4 w-4" />}
                      {stopping ? 'Arrêt en cours…' : 'Arrêter'}
                    </Button>
                  </>
                ) : (
                  <Button onClick={handleRerun} disabled={rerunning}>
                    {rerunning ? <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      : <RefreshCw className="mr-2 h-4 w-4" />}
                    Relancer
                  </Button>
                )}
              </div>
            </div>

            {/* ── Tab bar ───────────────────────────────────────── */}
            <div className="flex gap-1 rounded-xl border border-border/60 bg-muted/30 p-1 w-fit">
              <button
                onClick={() => setActiveTab('live')}
                className={`flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors
                  ${activeTab === 'live'
                    ? 'bg-background shadow-sm text-foreground'
                    : 'text-muted-foreground hover:text-foreground'}`}
              >
                <Radio className="h-3.5 w-3.5" />
                Vue en direct
                {isRunning && (
                  <span className="h-2 w-2 rounded-full bg-red-500 animate-pulse" />
                )}
              </button>
              <button
                onClick={() => setActiveTab('steps')}
                className={`flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors
                  ${activeTab === 'steps'
                    ? 'bg-background shadow-sm text-foreground'
                    : 'text-muted-foreground hover:text-foreground'}`}
              >
                <ListChecks className="h-3.5 w-3.5" />
                Parcours ({steps.length})
              </button>
              <button
                onClick={() => setActiveTab('report')}
                className={`flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors
                  ${activeTab === 'report'
                    ? 'bg-background shadow-sm text-foreground'
                    : 'text-muted-foreground hover:text-foreground'}`}
              >
                <Eye className="h-3.5 w-3.5" />
                Rapport UX
              </button>
            </div>

            {/* ── Tab: Live ─────────────────────────────────────── */}
            {activeTab === 'live' && (
              <Card className="border-border/60 bg-card/90 shadow-sm">
                <CardContent className="p-5">
                  <EvaluationLiveView
                    messages={messages}
                    liveScreenshot={liveScreenshot}
                    connected={connected}
                    needsInput={needsInput}
                    isRunning={isRunning}
                    isMobile={item?.platform === 'WEB_MOBILE' || item?.platform === 'MOBILE_APP'}
                    currentBackend={currentBackend}
                    onSendAnswer={sendAnswer}
                  />
                </CardContent>
              </Card>
            )}

            {/* ── Tab: Steps timeline ───────────────────────────── */}
            {activeTab === 'steps' && (
              <Card className="border-border/60 bg-card/90 shadow-sm">
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="flex items-center gap-2">
                        <Eye className="h-5 w-5" />
                        Parcours de navigation de l'IA
                      </CardTitle>
                      <CardDescription>
                        {steps.length} étape{steps.length !== 1 ? 's' : ''} enregistrée{steps.length !== 1 ? 's' : ''}
                        {isRunning && ' — exploration en cours…'}
                      </CardDescription>
                    </div>
                    {isRunning && <Loader2 className="h-5 w-5 animate-spin text-primary" />}
                  </div>
                </CardHeader>
                <CardContent>
                  {steps.length === 0 ? (
                    <div className="text-sm text-muted-foreground py-8 text-center">
                      {isRunning ? (
                        <div className="flex flex-col items-center gap-3">
                          <Loader2 className="h-8 w-8 animate-spin text-primary" />
                          <p>L'IA explore l'application…</p>
                        </div>
                      ) : item.status === 'PENDING' ? (
                        <div className="flex flex-col items-center gap-3">
                          <Play className="h-8 w-8 text-muted-foreground" />
                          <p>Cliquez sur "Relancer" pour démarrer l'exploration</p>
                        </div>
                      ) : (
                        'Aucune étape enregistrée.'
                      )}
                    </div>
                  ) : (
                    <div className="space-y-0">
                      {steps.map((step, i) => (
                        <StepCard
                          key={step.id ?? i}
                          step={step}
                          isLast={i === steps.length - 1}
                          isLive={isRunning && i === steps.length - 1}
                          onZoom={setLightbox}
                        />
                      ))}
                      <div ref={stepsEndRef} />
                    </div>
                  )}
                </CardContent>
              </Card>
            )}

            {/* ── Tab: Report ───────────────────────────────────── */}
            {activeTab === 'report' && (
              <div className="space-y-6">

                {/* UX Score card — only when analysis is available */}
                {(() => {
                  const score = extractUxScore(item.aiAnalysis ?? null)
                  if (score === null) return null
                  return (
                    <Card className={`border shadow-sm ${scoreBg(score)}`}>
                      <CardContent className="flex items-center justify-between p-5">
                        <div>
                          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-1">
                            Score UX global
                          </p>
                          <p className={`text-4xl font-bold ${scoreColor(score)}`}>
                            {score}<span className="text-lg font-normal text-muted-foreground">/10</span>
                          </p>
                        </div>
                        {/* Mini progress bar */}
                        <div className="w-32">
                          <div className="h-2.5 w-full rounded-full bg-muted overflow-hidden">
                            <div
                              className={`h-full rounded-full transition-all ${
                                score >= 8 ? 'bg-green-500' : score >= 6 ? 'bg-yellow-500' : 'bg-red-500'
                              }`}
                              style={{ width: `${(score / 10) * 100}%` }}
                            />
                          </div>
                          <p className="text-[10px] text-muted-foreground mt-1 text-right">
                            {score >= 8 ? '✅ Bonne UX' : score >= 6 ? '⚠ À améliorer' : '❌ UX problématique'}
                          </p>
                        </div>
                      </CardContent>
                    </Card>
                  )
                })()}

                {/* AI analysis */}
                <Card className="border-border/60 bg-card/90 shadow-sm">
                  <CardHeader>
                    <CardTitle>Rapport UX de l'IA</CardTitle>
                    <CardDescription>
                      Analyse globale générée après l'exploration complète.
                    </CardDescription>
                  </CardHeader>
                  <CardContent>
                    {item.aiAnalysis ? (
                      <div className="prose prose-sm dark:prose-invert max-w-none rounded-xl border border-border/60 bg-muted/25 p-5 text-sm leading-7 whitespace-pre-wrap">
                        {item.aiAnalysis}
                      </div>
                    ) : isRunning ? (
                      <div className="flex items-center gap-2 text-sm text-muted-foreground py-4">
                        <Loader2 className="h-4 w-4 animate-spin" />
                        Le rapport sera généré une fois l'exploration terminée…
                      </div>
                    ) : (
                      <p className="text-sm text-muted-foreground py-4">
                        Aucune analyse disponible.
                      </p>
                    )}
                  </CardContent>
                </Card>

                {/* Error message */}
                {item.errorMessage && (
                  <Card className="border-destructive/30 bg-destructive/5">
                    <CardHeader>
                      <CardTitle className="text-destructive">Erreur</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <p className="text-sm">{item.errorMessage}</p>
                    </CardContent>
                  </Card>
                )}

                {/* Technical details */}
                <Card className="border-border/60 bg-card/90 shadow-sm">
                  <CardHeader><CardTitle>Détails</CardTitle></CardHeader>
                  <CardContent className="space-y-2 text-sm">
                    <div className="flex justify-between rounded-xl border border-border/60 bg-muted/25 px-4 py-2.5">
                      <span className="text-muted-foreground">URL</span>
                      <span className="font-mono text-xs">{item.url}</span>
                    </div>
                    <div className="flex justify-between rounded-xl border border-border/60 bg-muted/25 px-4 py-2.5">
                      <span className="text-muted-foreground">Statut</span>
                      <Badge variant={statusTone(item.status)}>{statusLabel(item.status)}</Badge>
                    </div>
                    <div className="flex justify-between rounded-xl border border-border/60 bg-muted/25 px-4 py-2.5">
                      <span className="text-muted-foreground">Durée</span>
                      <span>{item.durationMs != null ? `${(item.durationMs / 1000).toFixed(1)}s` : '—'}</span>
                    </div>
                    <div className="flex justify-between rounded-xl border border-border/60 bg-muted/25 px-4 py-2.5">
                      <span className="text-muted-foreground">Étapes</span>
                      <span>{steps.length}</span>
                    </div>
                    {item.description && (
                      <div className="rounded-xl border border-border/60 bg-muted/25 px-4 py-2.5">
                        <span className="text-muted-foreground block text-xs mb-1">Description</span>
                        <span>{item.description}</span>
                      </div>
                    )}
                  </CardContent>
                </Card>

                {/* Logs (collapsible) */}
                {item.logs && (
                  <Card className="border-border/60 bg-card/90 shadow-sm">
                    <CardHeader><CardTitle>Logs de navigation</CardTitle></CardHeader>
                    <CardContent>
                      <pre className="max-h-60 overflow-auto rounded-xl border border-slate-800/80 bg-slate-950 p-4 font-mono text-xs text-slate-100 whitespace-pre-wrap">
                        {item.logs}
                      </pre>
                    </CardContent>
                  </Card>
                )}
              </div>
            )}

          </div>
        </main>
      </div>

      {/* ── Lightbox overlay ──────────────────────────────── */}
      {lightbox && <Lightbox src={lightbox} onClose={() => setLightbox(null)} />}
    </div>
  )
}

'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { ArrowRight, Filter, Loader2, RefreshCcw, Sparkles, Play } from 'lucide-react'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { listFunctionalEvaluations, executeFunctionalEvaluation, type FunctionalEvaluationDto } from '@/lib/api-client'

type PlatformFilter = 'ALL' | 'WEB' | 'MOBILE'

function platformFromType(value?: string | null) {
  if (!value) return 'UX'
  return value.toUpperCase()
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

function excerpt(value?: string | null, limit = 220) {
  const text = value?.trim() || 'Aucune analyse disponible pour cette exécution.'
  return text.length > limit ? `${text.slice(0, limit).trim()}...` : text
}

function EvaluationCard({ item, onExecute }: { item: FunctionalEvaluationDto; onExecute?: () => Promise<void> }) {
  const platform = platformFromType(item.platform)
  const analysis = excerpt(item.aiAnalysis ?? item.testSummary ?? item.pageContent ?? item.logs)

  return (
    <Card className="group h-full border-border/60 bg-card/90 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-lg">
      <CardHeader className="space-y-4">
        <div className="flex items-start justify-between gap-3">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="secondary" className="uppercase tracking-wide">
                {platform}
              </Badge>
              <Badge variant={statusTone(item.status)}>{statusLabel(item.status)}</Badge>
            </div>
            <CardTitle className="text-lg">Évaluation #{item.id}</CardTitle>
            <CardDescription>
              {item.url ?? item.description ?? 'Aucune cible'} · {new Date(item.executedAt ?? item.createdAt).toLocaleString('fr-FR')}
            </CardDescription>
          </div>
          <Button asChild variant="ghost" size="icon">
            <Link href={`/functional-evaluation/${item.id}`}>
              <ArrowRight className="h-4 w-4" />
            </Link>
          </Button>
          {item.status === 'PENDING' ? (
            <Button variant="ghost" size="icon" onClick={onExecute} title="Lancer">
              <Play className="h-4 w-4" />
            </Button>
          ) : null}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="rounded-2xl border border-border/60 bg-muted/30 p-4 text-sm leading-6 text-muted-foreground">
          <p className="text-sm font-medium text-foreground">Extrait fonctionnel</p>
          <p
            className="mt-2 whitespace-pre-wrap"
            style={{ display: '-webkit-box', WebkitBoxOrient: 'vertical', WebkitLineClamp: 4, overflow: 'hidden' }}
          >
            {analysis}
          </p>
        </div>
        <Button asChild className="w-full" variant="secondary">
          <Link href={`/functional-evaluation/${item.id}`}>Voir le détail</Link>
        </Button>
      </CardContent>
    </Card>
  )
}

function ListSkeleton() {
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      {Array.from({ length: 6 }).map((_, index) => (
        <Card key={index} className="border-border/60 bg-card/80">
          <CardHeader className="space-y-3">
            <div className="flex items-center gap-2">
              <Skeleton className="h-6 w-16 rounded-full" />
              <Skeleton className="h-6 w-20 rounded-full" />
            </div>
            <Skeleton className="h-7 w-3/5" />
            <Skeleton className="h-4 w-2/3" />
          </CardHeader>
          <CardContent className="space-y-3">
            <Skeleton className="h-24 w-full rounded-2xl" />
            <Skeleton className="h-10 w-full rounded-xl" />
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

export default function IntelligencePage() {
  const [items, setItems] = useState<FunctionalEvaluationDto[]>([])
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)
  const [platform, setPlatform] = useState<PlatformFilter>('ALL')
  const searchParams = useSearchParams()
  const created = searchParams.get('created')

  const loadUxEvaluations = async (nextPlatform: PlatformFilter) => {
    setState('loading')
    setError(null)

    try {
      const platformParam = nextPlatform === 'ALL' ? undefined : (nextPlatform as 'WEB' | 'MOBILE')
      const projectIdParam = (() => {
        const v = searchParams.get('projectId')
        if (!v) return undefined
        const n = Number(v)
        return Number.isFinite(n) ? n : undefined
      })()

      const data = await listFunctionalEvaluations(projectIdParam, platformParam)
      setItems(data ?? [])
      setState('ready')
    } catch (err) {
      setItems([])
      setState('error')
      setError(err instanceof Error ? err.message : 'Impossible de charger les évaluations fonctionnelles.')
    }
  }

  const handleExecute = async (id: number) => {
    try {
      await executeFunctionalEvaluation(id)
      // refresh list after triggering execution
      void loadUxEvaluations(platform)
    } catch (err) {
      // best-effort: show console error
      console.warn('Failed to start execution', err)
    }
  }

  useEffect(() => {
    void loadUxEvaluations(platform)
  }, [platform])

  const emptyMessage = useMemo(() => {
    if (platform === 'WEB') return 'Aucune évaluation fonctionnelle Web pour le moment.'
    if (platform === 'MOBILE') return 'Aucune évaluation fonctionnelle Mobile pour le moment.'
    return 'Aucune évaluation fonctionnelle disponible pour le moment.'
  }, [platform])

  return (
    <div className="flex min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(16,185,129,0.08),_transparent_32%),linear-gradient(to_bottom_right,_rgba(15,23,42,0.03),_transparent_30%)]">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto p-6 lg:p-8">
          <div className="mx-auto flex w-full max-w-7xl flex-col gap-6">
            <section className="rounded-3xl border border-border/60 bg-card/90 p-6 shadow-sm backdrop-blur md:p-8">
              <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
                <div className="max-w-2xl space-y-4">
                  <Badge variant="success" className="w-fit uppercase tracking-wide">
                    Fonctionnel uniquement
                  </Badge>
                  <div className="space-y-2">
                    <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">Évaluation Fonctionnelle IA</h1>
                    <p className="text-sm leading-6 text-muted-foreground md:text-base">
                      Espace dédié aux évaluations fonctionnelles Web et Mobile, séparé du reste des tests pour suivre
                      les analyses, les logs, le contenu de page et les rapports de manière indépendante.
                    </p>
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <Button variant="outline" onClick={() => void loadUxEvaluations(platform)}>
                    <RefreshCcw className="mr-2 h-4 w-4" />
                    Rafraîchir
                  </Button>
                  <Button asChild>
                    <Link href="/functional-evaluation/create">
                      <Sparkles className="mr-2 h-4 w-4" />
                      Nouvelle évaluation fonctionnelle
                    </Link>
                  </Button>
                </div>
              </div>
            </section>

            <section className="flex flex-col gap-3 rounded-3xl border border-border/60 bg-card/75 p-4 shadow-sm md:flex-row md:items-center md:justify-between">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Filter className="h-4 w-4" />
                Filtrer par plateforme
              </div>
              <div className="grid grid-cols-3 gap-2 rounded-2xl bg-muted/40 p-1 md:w-fit">
                {[
                  { value: 'ALL', label: 'Tout' },
                  { value: 'WEB', label: 'Web' },
                  { value: 'MOBILE', label: 'Mobile' },
                ].map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => setPlatform(option.value as PlatformFilter)}
                    className={cn(
                      'rounded-xl px-4 py-2 text-sm font-medium transition-all',
                      platform === option.value
                        ? 'bg-background text-foreground shadow-sm'
                        : 'text-muted-foreground hover:text-foreground',
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </section>

            {created ? (
              <section className="rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-4 text-sm text-emerald-950 dark:text-emerald-100">
                L’évaluation fonctionnelle a bien été créée. Elle apparaîtra ici dès qu’une exécution aura été enregistrée.
              </section>
            ) : null}

            {state === 'loading' ? (
              <ListSkeleton />
            ) : state === 'error' ? (
              <Card className="border-destructive/30 bg-destructive/5">
                <CardHeader>
                  <CardTitle>Impossible de charger les évaluations</CardTitle>
                  <CardDescription>{error}</CardDescription>
                </CardHeader>
                <CardContent>
                  <Button onClick={() => void loadUxEvaluations(platform)}>
                    <Loader2 className="mr-2 h-4 w-4" />
                    Réessayer
                  </Button>
                </CardContent>
              </Card>
            ) : items.length === 0 ? (
              <Card className="border-dashed border-border/70 bg-card/70">
                <CardHeader>
                  <CardTitle>Aucune évaluation fonctionnelle</CardTitle>
                  <CardDescription>{emptyMessage}</CardDescription>
                </CardHeader>
                <CardContent className="flex flex-wrap gap-3">
                  <Button asChild>
                    <Link href="/functional-evaluation/create">Créer une évaluation fonctionnelle</Link>
                  </Button>
                  <Button variant="outline" onClick={() => void loadUxEvaluations(platform)}>
                    Actualiser
                  </Button>
                </CardContent>
              </Card>
            ) : (
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                {items.map((item) => (
                  <EvaluationCard key={item.id} item={item} onExecute={() => handleExecute(item.id)} />
                ))}
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  )
}

'use client'

import { useEffect, useMemo, useState } from 'react'
import { AlertCircle, CheckCircle2, Clock, Plus, RefreshCw, Repeat2, Timer, TrendingUp } from 'lucide-react'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { StatCard } from '@/components/stat-card'
import { TestResultsChart } from '@/components/charts/test-results-chart'
import { SuccessRateTrend } from '@/components/charts/success-rate-trend'
import { CampaignCard } from '@/components/campaign-card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { getProjects, listCampaigns, listExecutionResults, type TestCampaignDto } from '@/lib/api-client'
import { getProjectKpi, getProjectTrend, type KpiResponse, type TrendPoint } from '@/lib/kpi-client'
import type { Project } from '@/types/ms-gestion'

function toSuccessRate(point: TrendPoint): number {
  const total = point.passed + point.failed + point.skipped
  if (total <= 0) return 0
  return Math.round((point.passed / total) * 1000) / 10
}

export default function DashboardPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null)
  const [kpi, setKpi] = useState<KpiResponse | null>(null)
  const [trend, setTrend] = useState<TrendPoint[]>([])
  const [campaigns, setCampaigns] = useState<TestCampaignDto[]>([])
  const [projectsLoading, setProjectsLoading] = useState(true)
  const [kpiLoading, setKpiLoading] = useState(false)
  const [campaignsLoading, setCampaignsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)

  useEffect(() => {
    void loadProjects()
  }, [])

  useEffect(() => {
    if (selectedProjectId == null) return
    void Promise.all([
      loadKpi(selectedProjectId),
      loadCampaigns(selectedProjectId),
    ])
  }, [selectedProjectId])

  const loadCampaigns = async (projectId: number) => {
    setCampaignsLoading(true)
    try {
      const campaignData = await listCampaigns({ projectId })
      
      // Fetch test counts for each campaign
      const campaignsWithCounts = await Promise.all(
        campaignData.map(async (campaign) => {
          try {
            const executions = await listExecutionResults({ campaignId: campaign.id })
            return {
              ...campaign,
              testCount: executions.length,
            }
          } catch {
            return {
              ...campaign,
              testCount: 0,
            }
          }
        }),
      )
      
      setCampaigns(campaignsWithCounts)
    } catch (err) {
      console.error('Failed to load campaigns:', err)
      setCampaigns([])
    } finally {
      setCampaignsLoading(false)
    }
  }

  const loadProjects = async () => {
    setProjectsLoading(true)
    setError(null)
    try {
      const data = await getProjects()
      setProjects(data)
      if (data.length > 0) {
        setSelectedProjectId(data[0].id)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load projects')
    } finally {
      setProjectsLoading(false)
    }
  }

  const loadKpi = async (projectId: number) => {
    setKpiLoading(true)
    setError(null)
    try {
      const [kpiData, trendData] = await Promise.all([
        getProjectKpi(projectId),
        getProjectTrend(projectId),
      ])
      setKpi(kpiData)
      setTrend(trendData)
      setLastUpdated(new Date())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load KPI data')
      setKpi(null)
      setTrend([])
    } finally {
      setKpiLoading(false)
    }
  }

  const chartData = useMemo(
    () =>
      trend.map((point) => ({
        name: point.label,
        passed: point.passed,
        failed: point.failed,
        skipped: point.skipped,
      })),
    [trend],
  )

  const trendData = useMemo(
    () =>
      trend.map((point) => ({
        week: point.label,
        successRate: toSuccessRate(point),
      })),
    [trend],
  )

  const selectedProjectName = projects.find((project) => project.id === selectedProjectId)?.name
  const successRateDelta = kpi ? kpi.evolution.currentRate - kpi.evolution.previousRate : 0
  // Only show the success-rate delta once there are at least two campaigns to compare.
  const hasComparison = trend.length >= 2

  const successTone: 'success' | 'warning' | 'danger' = !kpi
    ? 'warning'
    : kpi.successRate >= 90
      ? 'success'
      : kpi.successRate >= 70
        ? 'warning'
        : 'danger'

  const maxFailingCount = Math.max(1, ...(kpi?.top5FailingTests ?? []).map((t) => t.failureCount ?? 0))
  const maxSlowestMs = Math.max(1, ...(kpi?.top5SlowestTests ?? []).map((t) => t.averageDurationMs ?? 0))

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-7xl space-y-8">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Tableau de bord</h1>
              <p className="text-muted-foreground mt-1">
                Données KPI en direct pour le projet sélectionné.
              </p>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
              <div className="min-w-56">
                <Select
                  value={selectedProjectId?.toString() ?? ''}
                  onValueChange={(value) => setSelectedProjectId(Number(value))}
                  disabled={projectsLoading || projects.length === 0}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Sélectionner un projet" />
                  </SelectTrigger>
                  <SelectContent>
                    {projects.map((project) => (
                      <SelectItem key={project.id} value={project.id.toString()}>
                        {project.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <Button
                className="bg-primary hover:bg-primary/90 text-primary-foreground gap-2"
                onClick={() => {
                  if (selectedProjectId != null) {
                    void loadKpi(selectedProjectId)
                  }
                }}
                disabled={kpiLoading || selectedProjectId == null}
              >
                <RefreshCw size={18} className={kpiLoading ? 'animate-spin' : undefined} />
                Actualiser
              </Button>
              <Button className="bg-primary hover:bg-primary/90 text-primary-foreground gap-2">
                <Plus size={20} />
                Nouvelle campagne
              </Button>
            </div>
          </div>

          {error && (
            <Card className="border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950/40">
              <CardContent className="flex items-center gap-3 p-4 text-red-700 dark:text-red-300">
                <AlertCircle size={18} />
                <span>{error}</span>
              </CardContent>
            </Card>
          )}

          {!projectsLoading && projects.length === 0 && !error && (
            <Card>
              <CardContent className="p-6 text-muted-foreground">
                Aucun projet disponible. Les KPI sont liés à un projet — créez ou importez d'abord un projet.
              </CardContent>
            </Card>
          )}

          {selectedProjectName && (
            <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
              <span>Projet :</span>
              <Badge variant="secondary">{selectedProjectName}</Badge>
              {kpiLoading ? (
                <span>Chargement des KPI en direct…</span>
              ) : lastUpdated ? (
                <span className="text-xs">
                  Mis à jour à{' '}
                  {lastUpdated.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                </span>
              ) : null}
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
            <StatCard
              title="Tests exécutés"
              value={kpi ? kpi.totalTestsRun.toLocaleString() : '—'}
              subtitle={kpi ? `${kpi.passedTests.toLocaleString()} réussis · ${kpi.failedTests.toLocaleString()} échoués` : undefined}
              tone="primary"
              icon={<CheckCircle2 size={24} />}
              loading={kpiLoading}
            />
            <StatCard
              title="Taux de réussite"
              value={kpi ? `${kpi.successRate.toFixed(1)}%` : '—'}
              change={kpi && hasComparison ? Number(successRateDelta.toFixed(1)) : undefined}
              trend={successRateDelta >= 0 ? 'up' : 'down'}
              tone={successTone}
              icon={<TrendingUp size={24} />}
              loading={kpiLoading}
            />
            <StatCard
              title="Tests échoués"
              value={kpi ? kpi.failedTests.toLocaleString() : '—'}
              subtitle={kpi && kpi.totalTestsRun > 0 ? `${((kpi.failedTests / kpi.totalTestsRun) * 100).toFixed(1)}% des exécutions` : undefined}
              tone={kpi && kpi.failedTests > 0 ? 'danger' : 'success'}
              icon={<AlertCircle size={24} />}
              loading={kpiLoading}
            />
            <StatCard
              title="Campagnes actives"
              value={kpi ? kpi.activeCampaigns.toLocaleString() : '—'}
              subtitle={kpi && kpi.activeCampaigns > 0 ? 'en cours' : 'au repos'}
              tone={kpi && kpi.activeCampaigns > 0 ? 'warning' : 'neutral'}
              icon={<Clock size={24} />}
              loading={kpiLoading}
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <StatCard
              title="Durée moyenne par test"
              value={kpi ? (kpi.averageDuration ?? '0 ms') : '—'}
              subtitle="sur toutes les exécutions chronométrées"
              tone="neutral"
              icon={<Timer size={24} />}
              loading={kpiLoading}
            />
            <StatCard
              title="Tests instables (flaky)"
              value={kpi ? kpi.flakyTests.toLocaleString() : '—'}
              subtitle="ont nécessité au moins une reprise"
              tone={kpi && kpi.flakyTests > 0 ? 'warning' : 'success'}
              icon={<Repeat2 size={24} />}
              loading={kpiLoading}
            />
            <StatCard
              title="Durée dernière campagne"
              value={kpi ? kpi.totalExecutionTime : '—'}
              subtitle="dernière campagne terminée"
              tone="neutral"
              icon={<Clock size={24} />}
              loading={kpiLoading}
            />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <Card className="lg:col-span-2 p-6">
              <div className="flex items-start justify-between gap-4 mb-4">
                <div>
                  <h2 className="text-lg font-bold text-foreground">
                    Résultats des tests dans le temps
                  </h2>
                  <p className="text-sm text-muted-foreground">
                    Exécutions réussies, échouées et ignorées des dernières campagnes.
                  </p>
                </div>
              </div>
              {chartData.length > 0 ? (
                <TestResultsChart data={chartData} />
              ) : (
                <div className="flex h-[300px] items-center justify-center text-sm text-muted-foreground">
                  Aucune donnée de tendance pour ce projet.
                </div>
              )}
            </Card>

            <Card className="p-6">
              <div className="flex items-start justify-between gap-4 mb-4">
                <div>
                  <h2 className="text-lg font-bold text-foreground">
                    Évolution du taux de réussite
                  </h2>
                  <p className="text-sm text-muted-foreground">
                    Calculée à partir des dernières exécutions de campagnes.
                  </p>
                </div>
              </div>
              {trendData.length > 0 ? (
                <SuccessRateTrend data={trendData} />
              ) : (
                <div className="flex h-[300px] items-center justify-center text-sm text-muted-foreground">
                  Aucune donnée de tendance pour ce projet.
                </div>
              )}
            </Card>
          </div>

          <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
            <Card className="p-6">
              <CardHeader className="p-0 pb-4">
                <CardTitle>Top 5 des tests les plus lents</CardTitle>
                <CardDescription>Durée moyenne sur toutes les exécutions disponibles.</CardDescription>
              </CardHeader>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border">
                      <th className="px-3 py-2 text-left font-medium text-muted-foreground">Test</th>
                      <th className="px-3 py-2 text-left font-medium text-muted-foreground w-1/3">Relatif</th>
                      <th className="px-3 py-2 text-right font-medium text-muted-foreground">Durée moy.</th>
                    </tr>
                  </thead>
                  <tbody>
                    {kpi?.top5SlowestTests.length ? (
                      kpi.top5SlowestTests.map((test) => (
                        <tr key={test.testCaseId ?? test.testCaseLabel} className="border-b border-border/40">
                          <td className="px-3 py-3">
                            <div className="font-medium text-foreground">{test.testCaseLabel}</div>
                            <div className="text-xs text-muted-foreground">#{test.testCaseId ?? 'n/a'}</div>
                          </td>
                          <td className="px-3 py-3">
                            <div className="h-1.5 w-full rounded-full bg-muted">
                              <div
                                className="h-1.5 rounded-full bg-primary/70"
                                style={{ width: `${Math.max(6, ((test.averageDurationMs ?? 0) / maxSlowestMs) * 100)}%` }}
                              />
                            </div>
                          </td>
                          <td className="px-3 py-3 text-right font-medium text-foreground whitespace-nowrap">
                            {test.averageDuration ?? '—'}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td className="px-3 py-6 text-muted-foreground" colSpan={3}>
                          Aucune donnée sur les tests lents.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </Card>

            <Card className="p-6">
              <CardHeader className="p-0 pb-4">
                <CardTitle>Top 5 des tests en échec</CardTitle>
                <CardDescription>Tests avec la plus forte fréquence d'échec dans les dernières campagnes.</CardDescription>
              </CardHeader>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border">
                      <th className="px-3 py-2 text-left font-medium text-muted-foreground">Test</th>
                      <th className="px-3 py-2 text-right font-medium text-muted-foreground">Échecs</th>
                    </tr>
                  </thead>
                  <tbody>
                    {kpi?.top5FailingTests.length ? (
                      kpi.top5FailingTests.map((test) => (
                        <tr key={test.testCaseId ?? test.testCaseLabel} className="border-b border-border/40">
                          <td className="px-3 py-3">
                            <div className="font-medium text-foreground">{test.testCaseLabel}</div>
                            <div className="text-xs text-muted-foreground">#{test.testCaseId ?? 'n/a'}</div>
                          </td>
                          <td className="px-3 py-3">
                            <div className="flex items-center justify-end gap-2">
                              <div className="h-1.5 w-24 rounded-full bg-muted">
                                <div
                                  className="h-1.5 rounded-full bg-red-500/70"
                                  style={{ width: `${Math.max(6, ((test.failureCount ?? 0) / maxFailingCount) * 100)}%` }}
                                />
                              </div>
                              <span className="font-semibold text-red-600 dark:text-red-400 tabular-nums w-6 text-right">
                                {test.failureCount ?? 0}
                              </span>
                            </div>
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td className="px-3 py-6 text-muted-foreground" colSpan={2}>
                          Aucune donnée sur les tests en échec.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </Card>
          </div>

          <div>
            <div className="mb-4">
              <h2 className="text-lg font-bold text-foreground">Campagnes récentes</h2>
              <p className="text-sm text-muted-foreground">
                Campagnes de test du projet sélectionné.
              </p>
            </div>
            {campaignsLoading ? (
              <Card>
                <CardContent className="p-6 text-muted-foreground">
                  Chargement des campagnes…
                </CardContent>
              </Card>
            ) : campaigns.length === 0 ? (
              <Card>
                <CardContent className="p-6 text-muted-foreground">
                  Aucune campagne disponible pour ce projet.
                </CardContent>
              </Card>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {campaigns.map((campaign) => (
                  <CampaignCard
                    key={campaign.id}
                    id={campaign.id}
                    projectId={campaign.projectId}
                    name={campaign.name}
                    status={
                      (campaign.status || '').toUpperCase() === 'RUNNING' ? 'Running'
                        : (campaign.status || '').toUpperCase() === 'FINISHED' ? 'Completed'
                        : (campaign.status || '').toUpperCase() === 'FINISHED_WITH_ERRORS' ? 'Failed'
                        : 'Scheduled'
                    }
                    progress={
                      (campaign.status || '').toUpperCase() === 'RUNNING' ? 50 : 100
                    }
                    tests={(campaign as any).testCount || 0}
                    passed={0}
                    failed={0}
                    lastRun={campaign.startedAt || 'Never'}
                    onDelete={() => {
                      if (selectedProjectId) {
                        void loadCampaigns(selectedProjectId)
                      }
                    }}
                  />
                ))}
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  )
}

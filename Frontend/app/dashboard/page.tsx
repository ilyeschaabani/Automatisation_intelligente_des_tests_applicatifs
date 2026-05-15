'use client'

import { useEffect, useMemo, useState } from 'react'
import { AlertCircle, CheckCircle2, Clock, Plus, RefreshCw, TrendingUp } from 'lucide-react'

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

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-7xl space-y-8">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Dashboard</h1>
              <p className="text-muted-foreground mt-1">
                Live KPI data for your selected project.
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
                    <SelectValue placeholder="Select a project" />
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
                <RefreshCw size={18} />
                Refresh
              </Button>
              <Button className="bg-primary hover:bg-primary/90 text-primary-foreground gap-2">
                <Plus size={20} />
                New Campaign
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
                No projects available. KPI data is project-scoped, so create or import a project first.
              </CardContent>
            </Card>
          )}

          {selectedProjectName && (
            <div className="flex items-center gap-3 text-sm text-muted-foreground">
              <span>Project:</span>
              <Badge variant="secondary">{selectedProjectName}</Badge>
              {kpiLoading && <span>Loading live KPI data...</span>}
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
            <StatCard
              title="Total Tests Run"
              value={kpi ? kpi.totalTestsRun.toLocaleString() : '—'}
              icon={<CheckCircle2 size={24} />}
            />
            <StatCard
              title="Success Rate"
              value={kpi ? `${kpi.successRate.toFixed(1)}%` : '—'}
              change={kpi ? Number(successRateDelta.toFixed(1)) : undefined}
              trend={successRateDelta >= 0 ? 'up' : 'down'}
              icon={<TrendingUp size={24} />}
            />
            <StatCard
              title="Failed Tests"
              value={kpi ? kpi.failedTests.toLocaleString() : '—'}
              icon={<AlertCircle size={24} />}
            />
            <StatCard
              title="Active Campaigns"
              value={kpi ? kpi.activeCampaigns.toLocaleString() : '—'}
              icon={<Clock size={24} />}
            />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <Card className="lg:col-span-2 p-6">
              <div className="flex items-start justify-between gap-4 mb-4">
                <div>
                  <h2 className="text-lg font-bold text-foreground">
                    Test Results Over Time
                  </h2>
                  <p className="text-sm text-muted-foreground">
                    Passed, failed, and skipped executions from the latest campaigns.
                  </p>
                </div>
              </div>
              {chartData.length > 0 ? (
                <TestResultsChart data={chartData} />
              ) : (
                <div className="flex h-[300px] items-center justify-center text-sm text-muted-foreground">
                  No trend data available for this project.
                </div>
              )}
            </Card>

            <Card className="p-6">
              <div className="flex items-start justify-between gap-4 mb-4">
                <div>
                  <h2 className="text-lg font-bold text-foreground">
                    Success Rate Trend
                  </h2>
                  <p className="text-sm text-muted-foreground">
                    Derived from the latest campaign executions.
                  </p>
                </div>
              </div>
              {trendData.length > 0 ? (
                <SuccessRateTrend data={trendData} />
              ) : (
                <div className="flex h-[300px] items-center justify-center text-sm text-muted-foreground">
                  No trend data available for this project.
                </div>
              )}
            </Card>
          </div>

          <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
            <Card className="p-6">
              <CardHeader className="p-0 pb-4">
                <CardTitle>Top 5 Slowest Tests</CardTitle>
                <CardDescription>Average duration across all available executions.</CardDescription>
              </CardHeader>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border">
                      <th className="px-3 py-2 text-left font-medium text-muted-foreground">Test</th>
                      <th className="px-3 py-2 text-right font-medium text-muted-foreground">Average</th>
                      <th className="px-3 py-2 text-right font-medium text-muted-foreground">Duration</th>
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
                          <td className="px-3 py-3 text-right text-muted-foreground">
                            {test.averageDurationMs?.toFixed(0) ?? '0'} ms
                          </td>
                          <td className="px-3 py-3 text-right font-medium text-foreground">
                            {test.averageDuration ?? '—'}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td className="px-3 py-6 text-muted-foreground" colSpan={3}>
                          No slow-test data available.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </Card>

            <Card className="p-6">
              <CardHeader className="p-0 pb-4">
                <CardTitle>Top 5 Failing Tests</CardTitle>
                <CardDescription>Tests with the highest failure frequency in the latest campaigns.</CardDescription>
              </CardHeader>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border">
                      <th className="px-3 py-2 text-left font-medium text-muted-foreground">Test</th>
                      <th className="px-3 py-2 text-right font-medium text-muted-foreground">Failures</th>
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
                          <td className="px-3 py-3 text-right font-semibold text-red-600 dark:text-red-400">
                            {test.failureCount ?? 0}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td className="px-3 py-6 text-muted-foreground" colSpan={2}>
                          No failing-test data available.
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
              <h2 className="text-lg font-bold text-foreground">Recent Campaigns</h2>
              <p className="text-sm text-muted-foreground">
                Test campaigns for the selected project.
              </p>
            </div>
            {campaignsLoading ? (
              <Card>
                <CardContent className="p-6 text-muted-foreground">
                  Loading campaigns...
                </CardContent>
              </Card>
            ) : campaigns.length === 0 ? (
              <Card>
                <CardContent className="p-6 text-muted-foreground">
                  No campaigns available for this project.
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
                    type={
                      campaign.triggerMode === 'CI' ? 'Regression'
                        : campaign.triggerMode === 'SCHEDULED' ? 'Functional'
                        : 'API'
                    }
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

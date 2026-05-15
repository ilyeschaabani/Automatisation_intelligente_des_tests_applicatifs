'use client'

import { useEffect, useState } from 'react'
import { AlertCircle, TrendingDown, TrendingUp } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { getProjectKpi, type KpiResponse, type TestMetric } from '@/lib/kpi-client'

export interface KpiDashboardProps {
  projectId: number
}

export function KpiDashboard({ projectId }: KpiDashboardProps) {
  const [kpi, setKpi] = useState<KpiResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    loadKpi()
  }, [projectId])

  const loadKpi = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getProjectKpi(projectId)
      setKpi(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load KPI data')
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center p-8">
        <p className="text-muted-foreground">Loading KPI data...</p>
      </div>
    )
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    )
  }

  if (!kpi) {
    return (
      <Alert>
        <AlertCircle className="h-4 w-4" />
        <AlertDescription>No KPI data available for this project.</AlertDescription>
      </Alert>
    )
  }

  return (
    <div className="space-y-6">
      {/* KPI Cards Row */}
      <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard
          title="Success Rate"
          value={kpi.successRate.toFixed(1)}
          unit="%"
          description="Tests passed over total"
          color="bg-blue-50 dark:bg-blue-950"
        />
        <KpiCard
          title="Total Tests Run"
          value={String(kpi.totalTestsRun)}
          description="All-time executions"
          color="bg-green-50 dark:bg-green-950"
        />
        <KpiCard
          title="Failed Tests"
          value={String(kpi.failedTests)}
          description="Latest 5 campaigns"
          color="bg-red-50 dark:bg-red-950"
        />
        <KpiCard
          title="Active Campaigns"
          value={String(kpi.activeCampaigns)}
          description="Currently running"
          color="bg-purple-50 dark:bg-purple-950"
        />
      </div>

      {/* Evolution Trend */}
      <Card className="border-border/70 shadow-sm">
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Success Rate Trend</CardTitle>
              <CardDescription>Previous vs. Current Campaign</CardDescription>
            </div>
            <TrendBadge trend={kpi.evolution.trend} />
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="rounded-lg border border-border/50 bg-muted/30 p-4">
                <p className="text-xs text-muted-foreground uppercase tracking-wide">Previous Rate</p>
                <p className="mt-2 text-2xl font-semibold">{kpi.evolution.previousRate.toFixed(1)}%</p>
              </div>
              <div className="rounded-lg border border-border/50 bg-muted/30 p-4">
                <p className="text-xs text-muted-foreground uppercase tracking-wide">Current Rate</p>
                <p className="mt-2 text-2xl font-semibold">{kpi.evolution.currentRate.toFixed(1)}%</p>
              </div>
            </div>
            {kpi.evolution.trend !== 'STABLE' && (
              <p className="text-sm text-muted-foreground">
                {kpi.evolution.trend === 'UP'
                  ? '✓ Quality is improving'
                  : '✗ Quality degradation detected'}
              </p>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Execution Time */}
      <Card className="border-border/70 shadow-sm">
        <CardHeader>
          <CardTitle>Last Campaign Execution Time</CardTitle>
          <CardDescription>Total duration of the last completed campaign</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-3xl font-bold text-primary">{kpi.totalExecutionTime}</p>
        </CardContent>
      </Card>

      {/* Top Slowest Tests */}
      {kpi.top5SlowestTests.length > 0 && (
        <Card className="border-border/70 shadow-sm">
          <CardHeader>
            <CardTitle>Top 5 Slowest Tests</CardTitle>
            <CardDescription>By average execution time</CardDescription>
          </CardHeader>
          <CardContent>
            <TestMetricsTable metrics={kpi.top5SlowestTests} showDuration={true} />
          </CardContent>
        </Card>
      )}

      {/* Top Failing Tests */}
      {kpi.top5FailingTests.length > 0 && (
        <Card className="border-border/70 shadow-sm">
          <CardHeader>
            <CardTitle>Top 5 Failing Tests</CardTitle>
            <CardDescription>By failure count in latest campaigns</CardDescription>
          </CardHeader>
          <CardContent>
            <TestMetricsTable metrics={kpi.top5FailingTests} showDuration={false} />
          </CardContent>
        </Card>
      )}
    </div>
  )
}

interface KpiCardProps {
  title: string
  value: string
  unit?: string
  description: string
  color: string
}

function KpiCard({ title, value, unit, description, color }: KpiCardProps) {
  return (
    <Card className={`border-border/50 ${color}`}>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex items-baseline gap-1">
          <p className="text-2xl font-bold">{value}</p>
          {unit && <span className="text-sm text-muted-foreground">{unit}</span>}
        </div>
        <p className="mt-2 text-xs text-muted-foreground">{description}</p>
      </CardContent>
    </Card>
  )
}

interface TrendBadgeProps {
  trend: 'UP' | 'DOWN' | 'STABLE'
}

function TrendBadge({ trend }: TrendBadgeProps) {
  const variants = {
    UP: { icon: TrendingUp, label: 'Improving', color: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400' },
    DOWN: { icon: TrendingDown, label: 'Declining', color: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400' },
    STABLE: { icon: null, label: 'Stable', color: 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-400' },
  }

  const variant = variants[trend]
  const Icon = variant.icon

  return (
    <Badge className={variant.color} variant="outline">
      {Icon && <Icon className="mr-1 h-3 w-3" />}
      {variant.label}
    </Badge>
  )
}

interface TestMetricsTableProps {
  metrics: TestMetric[]
  showDuration: boolean
}

function TestMetricsTable({ metrics, showDuration }: TestMetricsTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border/50">
            <th className="px-4 py-2 text-left font-medium text-muted-foreground">Test Case</th>
            {showDuration ? (
              <>
                <th className="px-4 py-2 text-right font-medium text-muted-foreground">Avg Duration</th>
                <th className="px-4 py-2 text-right font-medium text-muted-foreground">Duration (ms)</th>
              </>
            ) : (
              <th className="px-4 py-2 text-right font-medium text-muted-foreground">Failures</th>
            )}
          </tr>
        </thead>
        <tbody>
          {metrics.map((metric, idx) => (
            <tr key={idx} className="border-b border-border/30 hover:bg-muted/30">
              <td className="px-4 py-3">
                <span className="font-mono text-xs text-muted-foreground">#{metric.testCaseId}</span>
                <p className="text-sm font-medium">{metric.testCaseLabel}</p>
              </td>
              {showDuration ? (
                <>
                  <td className="px-4 py-3 text-right">{metric.averageDuration}</td>
                  <td className="px-4 py-3 text-right text-muted-foreground">
                    {metric.averageDurationMs?.toFixed(0) || '—'}
                  </td>
                </>
              ) : (
                <td className="px-4 py-3 text-right font-semibold text-red-600 dark:text-red-400">
                  {metric.failureCount}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

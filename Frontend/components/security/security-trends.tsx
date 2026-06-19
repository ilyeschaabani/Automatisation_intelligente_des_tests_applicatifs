'use client'

import { useMemo } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { TrendingDown, PieChart as PieIcon, AreaChart as AreaIcon } from 'lucide-react'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend,
  BarChart, Bar,
} from 'recharts'

const SEVERITY_COLORS = {
  critical: '#ef4444',
  high: '#f97316',
  medium: '#eab308',
  low: '#3b82f6',
}

interface Props {
  scans: any[]
  rawVulns: any[]
}

export function SecurityTrends({ scans, rawVulns }: Props) {
  const trendData = useMemo(() => {
    const completedScans = scans
      .filter(s => s.status === 'COMPLETED' && s.completedAt)
      .sort((a, b) => new Date(a.completedAt).getTime() - new Date(b.completedAt).getTime())

    const byDate = new Map<string, { critical: number; high: number; medium: number; low: number }>()

    for (const scan of completedScans) {
      const date = new Date(scan.completedAt).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })
      if (!byDate.has(date)) byDate.set(date, { critical: 0, high: 0, medium: 0, low: 0 })
      const entry = byDate.get(date)!
      const scanVulns = rawVulns.filter((v: any) => v.scanId === scan.id || v.scan?.id === scan.id)
      for (const v of scanVulns) {
        const sev = (v.severity || '').toLowerCase()
        if (sev in entry) (entry as any)[sev]++
      }
    }

    if (byDate.size === 0) {
      const c = rawVulns.filter((v: any) => (v.severity || '').toUpperCase() === 'CRITICAL').length
      const h = rawVulns.filter((v: any) => (v.severity || '').toUpperCase() === 'HIGH').length
      const m = rawVulns.filter((v: any) => (v.severity || '').toUpperCase() === 'MEDIUM').length
      const l = rawVulns.filter((v: any) => (v.severity || '').toUpperCase() === 'LOW').length
      if (c + h + m + l > 0) {
        const today = new Date().toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })
        byDate.set(today, { critical: c, high: h, medium: m, low: l })
      }
    }

    return Array.from(byDate.entries()).map(([date, counts]) => ({
      date,
      ...counts,
      total: counts.critical + counts.high + counts.medium + counts.low,
    }))
  }, [scans, rawVulns])

  const scanRate = useMemo(() => {
    const byDate = new Map<string, { passed: number; failed: number }>()
    for (const scan of scans) {
      const dateStr = scan.completedAt || scan.startedAt
      if (!dateStr) continue
      const date = new Date(dateStr).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })
      if (!byDate.has(date)) byDate.set(date, { passed: 0, failed: 0 })
      const entry = byDate.get(date)!
      if (scan.status === 'COMPLETED') entry.passed++
      else if (scan.status === 'FAILED') entry.failed++
    }
    return Array.from(byDate.entries()).map(([date, counts]) => ({
      date, ...counts, total: counts.passed + counts.failed,
    }))
  }, [scans])

  const pieData = useMemo(() => {
    const c = rawVulns.filter((v: any) => (v.severity || '').toUpperCase() === 'CRITICAL').length
    const h = rawVulns.filter((v: any) => (v.severity || '').toUpperCase() === 'HIGH').length
    const m = rawVulns.filter((v: any) => (v.severity || '').toUpperCase() === 'MEDIUM').length
    const l = rawVulns.filter((v: any) => (v.severity || '').toUpperCase() === 'LOW').length
    return [
      { name: 'Critical', value: c, color: SEVERITY_COLORS.critical },
      { name: 'High', value: h, color: SEVERITY_COLORS.high },
      { name: 'Medium', value: m, color: SEVERITY_COLORS.medium },
      { name: 'Low', value: l, color: SEVERITY_COLORS.low },
    ].filter(d => d.value > 0)
  }, [rawVulns])

  if (scans.length === 0 && rawVulns.length === 0) {
    return (
      <Card>
        <CardContent className="py-12 text-center text-muted-foreground">
          <TrendingDown className="h-8 w-8 mx-auto mb-3 opacity-40" />
          <p className="text-sm">Aucun scan termine — les tendances apparaitront apres le premier scan</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-bold flex items-center gap-2">
        <TrendingDown className="h-5 w-5 text-primary" />
        Security Trends
      </h2>
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card className="lg:col-span-2">
          <CardHeader className="pb-2">
            <CardTitle className="text-sm flex items-center gap-2">
              <AreaIcon className="h-4 w-4" />Vulnerabilities par scan
            </CardTitle>
          </CardHeader>
          <CardContent>
            {trendData.length > 0 ? (
              <ResponsiveContainer width="100%" height={280}>
                <AreaChart data={trendData}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} className="text-muted-foreground" />
                  <YAxis tick={{ fontSize: 11 }} className="text-muted-foreground" />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: 'hsl(var(--card))',
                      borderColor: 'hsl(var(--border))',
                      borderRadius: 8,
                      fontSize: 12,
                    }}
                  />
                  <Area type="monotone" dataKey="critical" stackId="1" stroke={SEVERITY_COLORS.critical} fill={SEVERITY_COLORS.critical} fillOpacity={0.6} />
                  <Area type="monotone" dataKey="high" stackId="1" stroke={SEVERITY_COLORS.high} fill={SEVERITY_COLORS.high} fillOpacity={0.5} />
                  <Area type="monotone" dataKey="medium" stackId="1" stroke={SEVERITY_COLORS.medium} fill={SEVERITY_COLORS.medium} fillOpacity={0.4} />
                  <Area type="monotone" dataKey="low" stackId="1" stroke={SEVERITY_COLORS.low} fill={SEVERITY_COLORS.low} fillOpacity={0.3} />
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-[280px] flex items-center justify-center text-sm text-muted-foreground">
                Pas encore de donnees de tendance
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm flex items-center gap-2">
              <PieIcon className="h-4 w-4" />Repartition par severite
            </CardTitle>
          </CardHeader>
          <CardContent>
            {pieData.length > 0 ? (
              <ResponsiveContainer width="100%" height={280}>
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="45%"
                    innerRadius={50}
                    outerRadius={80}
                    paddingAngle={3}
                    dataKey="value"
                    label={({ name, value }) => `${name}: ${value}`}
                  >
                    {pieData.map(entry => (
                      <Cell key={entry.name} fill={entry.color} />
                    ))}
                  </Pie>
                  <Legend verticalAlign="bottom" height={36} iconSize={10} wrapperStyle={{ fontSize: 11 }} />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-[280px] flex items-center justify-center text-sm text-muted-foreground">
                Aucune vulnerabilite detectee
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {scanRate.length > 0 && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Taux de succes des scans</CardTitle>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={scanRate}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} className="text-muted-foreground" />
                <YAxis tick={{ fontSize: 11 }} className="text-muted-foreground" />
                <Tooltip
                  contentStyle={{
                    backgroundColor: 'hsl(var(--card))',
                    borderColor: 'hsl(var(--border))',
                    borderRadius: 8,
                    fontSize: 12,
                  }}
                />
                <Bar dataKey="passed" fill="#22c55e" radius={[4, 4, 0, 0]} name="Reussi" />
                <Bar dataKey="failed" fill="#ef4444" radius={[4, 4, 0, 0]} name="Echoue" />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

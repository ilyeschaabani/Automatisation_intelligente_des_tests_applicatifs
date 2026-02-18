'use client'

import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts'

interface TestResultsChartProps {
  data: Array<{
    name: string
    passed: number
    failed: number
    skipped: number
  }>
}

export function TestResultsChart({ data }: TestResultsChartProps) {
  return (
    <ResponsiveContainer width="100%" height={300}>
      <BarChart data={data}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
        <XAxis stroke="hsl(var(--muted-foreground))" />
        <YAxis stroke="hsl(var(--muted-foreground))" />
        <Tooltip
          contentStyle={{
            backgroundColor: 'hsl(var(--card))',
            border: '1px solid hsl(var(--border))',
          }}
          cursor={{ fill: 'hsl(var(--secondary))' }}
        />
        <Legend />
        <Bar
          dataKey="passed"
          fill="hsl(var(--chart-2))"
          name="Passed"
          radius={[8, 8, 0, 0]}
        />
        <Bar
          dataKey="failed"
          fill="hsl(var(--destructive))"
          name="Failed"
          radius={[8, 8, 0, 0]}
        />
        <Bar
          dataKey="skipped"
          fill="hsl(var(--muted))"
          name="Skipped"
          radius={[8, 8, 0, 0]}
        />
      </BarChart>
    </ResponsiveContainer>
  )
}

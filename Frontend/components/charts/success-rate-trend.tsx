'use client'

import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'

interface SuccessRateTrendProps {
  data: Array<{
    week: string
    successRate: number
  }>
}

export function SuccessRateTrend({ data }: SuccessRateTrendProps) {
  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
        <XAxis dataKey="week" stroke="hsl(var(--muted-foreground))" />
        <YAxis
          stroke="hsl(var(--muted-foreground))"
          domain={[0, 100]}
        />
        <Tooltip
          contentStyle={{
            backgroundColor: 'hsl(var(--card))',
            border: '1px solid hsl(var(--border))',
          }}
          cursor={{ stroke: 'hsl(var(--primary))' }}
        />
        <Line
          type="monotone"
          dataKey="successRate"
          stroke="hsl(var(--chart-2))"
          strokeWidth={2}
          dot={{ fill: 'hsl(var(--primary))', r: 4 }}
          activeDot={{ r: 6 }}
          name="Success Rate %"
        />
      </LineChart>
    </ResponsiveContainer>
  )
}

import { ArrowUp, ArrowDown } from 'lucide-react'
import { ReactNode } from 'react'

interface StatCardProps {
  title: string
  value: string | number
  change?: number
  icon: ReactNode
  trend?: 'up' | 'down'
}

export function StatCard({
  title,
  value,
  change,
  icon,
  trend = 'up',
}: StatCardProps) {
  const isPositive = trend === 'up'

  return (
    <div className="bg-card border border-border rounded-lg p-6">
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <p className="text-sm font-medium text-muted-foreground">{title}</p>
          <p className="text-2xl font-bold text-foreground mt-2">{value}</p>
          {change !== undefined && (
            <div className="flex items-center gap-1 mt-3">
              <div
                className={`flex items-center gap-1 text-xs font-medium px-2 py-1 rounded ${
                  isPositive
                    ? 'text-green-700 bg-green-100 dark:text-green-400 dark:bg-green-950'
                    : 'text-red-700 bg-red-100 dark:text-red-400 dark:bg-red-950'
                }`}
              >
                {isPositive ? (
                  <ArrowUp size={14} />
                ) : (
                  <ArrowDown size={14} />
                )}
                {Math.abs(change)}%
              </div>
              <span className="text-xs text-muted-foreground">vs last month</span>
            </div>
          )}
        </div>
        <div className="w-12 h-12 bg-secondary rounded-lg flex items-center justify-center text-primary">
          {icon}
        </div>
      </div>
    </div>
  )
}

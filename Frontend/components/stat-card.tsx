import { ArrowUp, ArrowDown, Minus } from 'lucide-react'
import { ReactNode } from 'react'

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'primary'

interface StatCardProps {
  title: string
  value: string | number
  change?: number
  icon: ReactNode
  trend?: 'up' | 'down'
  /** Semantic color of the icon badge / accents. */
  tone?: Tone
  /** Small caption under the value (e.g. "12 passed / 3 failed"). */
  subtitle?: string
  /** Label shown next to the delta. Defaults to "vs previous campaign". */
  hint?: string
  /** Renders a shimmer skeleton instead of the value. */
  loading?: boolean
}

const toneStyles: Record<Tone, { badge: string }> = {
  neutral: { badge: 'bg-secondary text-primary' },
  primary: { badge: 'bg-primary/10 text-primary' },
  success: { badge: 'bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-400' },
  warning: { badge: 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-400' },
  danger: { badge: 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-400' },
}

export function StatCard({
  title,
  value,
  change,
  icon,
  trend = 'up',
  tone = 'neutral',
  subtitle,
  hint = 'vs campagne précédente',
  loading = false,
}: StatCardProps) {
  const styles = toneStyles[tone]
  const hasDelta = change !== undefined && Number.isFinite(change)
  const isFlat = hasDelta && Math.abs(change as number) < 0.05
  const isPositive = trend === 'up'

  return (
    <div className="bg-card border border-border rounded-lg p-6 transition-shadow hover:shadow-sm">
      <div className="flex items-start justify-between">
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-muted-foreground">{title}</p>

          {loading ? (
            <div className="mt-2 h-8 w-24 animate-pulse rounded bg-muted" />
          ) : (
            <p className="text-2xl font-bold text-foreground mt-2 truncate">{value}</p>
          )}

          {subtitle && !loading && (
            <p className="text-xs text-muted-foreground mt-1 truncate">{subtitle}</p>
          )}

          {hasDelta && !loading && (
            <div className="flex items-center gap-1 mt-3">
              <div
                className={`flex items-center gap-1 text-xs font-medium px-2 py-1 rounded ${
                  isFlat
                    ? 'text-muted-foreground bg-muted'
                    : isPositive
                      ? 'text-green-700 bg-green-100 dark:text-green-400 dark:bg-green-950'
                      : 'text-red-700 bg-red-100 dark:text-red-400 dark:bg-red-950'
                }`}
              >
                {isFlat ? (
                  <Minus size={14} />
                ) : isPositive ? (
                  <ArrowUp size={14} />
                ) : (
                  <ArrowDown size={14} />
                )}
                {Math.abs(change as number)}%
              </div>
              <span className="text-xs text-muted-foreground">{hint}</span>
            </div>
          )}
        </div>

        <div className={`w-12 h-12 rounded-lg flex items-center justify-center shrink-0 ${styles.badge}`}>
          {icon}
        </div>
      </div>
    </div>
  )
}

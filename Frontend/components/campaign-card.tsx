import { Badge } from '@/components/ui/badge'
import { MoreVertical, Play, CheckCircle2, AlertCircle } from 'lucide-react'

interface CampaignCardProps {
  name: string
  type: 'Functional' | 'API' | 'Regression'
  status: 'Running' | 'Completed' | 'Failed' | 'Scheduled'
  progress: number
  tests: number
  passed: number
  failed: number
  lastRun: string
}

const statusConfig = {
  Running: { color: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400', icon: 'animate-spin' },
  Completed: { color: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400' },
  Failed: { color: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400' },
  Scheduled: { color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-950 dark:text-yellow-400' },
}

const typeColors = {
  Functional: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400',
  API: 'bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-400',
  Regression: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-950 dark:text-cyan-400',
}

export function CampaignCard({
  name,
  type,
  status,
  progress,
  tests,
  passed,
  failed,
  lastRun,
}: CampaignCardProps) {
  const config = statusConfig[status]
  const typeColor = typeColors[type]

  return (
    <div className="bg-card border border-border rounded-lg p-5 hover:shadow-md transition-shadow">
      <div className="flex items-start justify-between mb-4">
        <div className="flex-1">
          <h3 className="font-semibold text-foreground">{name}</h3>
          <div className="flex items-center gap-2 mt-2">
            <Badge variant="outline" className={typeColor}>
              {type}
            </Badge>
            <Badge variant="outline" className={config.color}>
              {status === 'Running' && <span className="inline-block size-1.5 bg-current rounded-full mr-1 animate-pulse" />}
              {status}
            </Badge>
          </div>
        </div>
        <button className="p-2 hover:bg-secondary rounded-lg">
          <MoreVertical size={18} className="text-muted-foreground" />
        </button>
      </div>

      {/* Progress Bar */}
      <div className="mb-4">
        <div className="flex items-center justify-between mb-2">
          <span className="text-xs text-muted-foreground">Progress</span>
          <span className="text-xs font-semibold text-foreground">{progress}%</span>
        </div>
        <div className="w-full h-2 bg-secondary rounded-full overflow-hidden">
          <div
            className="h-full bg-gradient-to-r from-primary to-accent rounded-full transition-all"
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-3 mb-4">
        <div>
          <p className="text-xs text-muted-foreground">Total</p>
          <p className="font-bold text-foreground">{tests}</p>
        </div>
        <div className="flex items-center gap-1">
          <CheckCircle2 size={16} className="text-green-600" />
          <div>
            <p className="text-xs text-muted-foreground">Passed</p>
            <p className="font-bold text-foreground">{passed}</p>
          </div>
        </div>
        <div className="flex items-center gap-1">
          <AlertCircle size={16} className="text-red-600" />
          <div>
            <p className="text-xs text-muted-foreground">Failed</p>
            <p className="font-bold text-foreground">{failed}</p>
          </div>
        </div>
      </div>

      {/* Last Run */}
      <div className="pt-4 border-t border-border">
        <p className="text-xs text-muted-foreground">Last run: {lastRun}</p>
      </div>
    </div>
  )
}

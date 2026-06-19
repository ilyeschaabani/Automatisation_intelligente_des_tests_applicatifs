export function severityColor(s: string) {
  switch (s) {
    case 'critical': return 'bg-red-500/15 text-red-700 dark:text-red-400 border-red-500/30'
    case 'high': return 'bg-orange-500/15 text-orange-700 dark:text-orange-400 border-orange-500/30'
    case 'medium': return 'bg-yellow-500/15 text-yellow-700 dark:text-yellow-400 border-yellow-500/30'
    case 'low': return 'bg-blue-500/15 text-blue-700 dark:text-blue-400 border-blue-500/30'
    case 'info': return 'bg-slate-500/15 text-slate-700 dark:text-slate-400 border-slate-500/30'
    default: return 'bg-muted text-muted-foreground'
  }
}

export function statusColor(s: string) {
  switch (s) {
    case 'open': return 'bg-red-500/15 text-red-700 dark:text-red-400'
    case 'in_progress': return 'bg-blue-500/15 text-blue-700 dark:text-blue-400'
    case 'resolved': return 'bg-green-500/15 text-green-700 dark:text-green-400'
    case 'closed': return 'bg-slate-500/15 text-slate-700 dark:text-slate-400'
    case 'false_positive': return 'bg-purple-500/15 text-purple-700 dark:text-purple-400'
    default: return 'bg-muted text-muted-foreground'
  }
}

export function statusLabel(s: string) {
  switch (s) {
    case 'open': return 'Open'
    case 'in_progress': return 'In Progress'
    case 'resolved': return 'Resolved'
    case 'closed': return 'Closed'
    case 'false_positive': return 'False Positive'
    default: return s
  }
}

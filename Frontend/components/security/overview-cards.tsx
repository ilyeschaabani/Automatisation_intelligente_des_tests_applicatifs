'use client'

import { Card, CardContent } from '@/components/ui/card'
import {
  ShieldAlert, ShieldX, AlertTriangle, AlertCircle,
  Shield, CheckCircle2,
} from 'lucide-react'
import type { Vulnerability } from '@/types/security'

interface KpiCard {
  label: string
  value: number
  icon: React.ReactNode
  color: string
  bg: string
}

export function OverviewCards({ vulns }: { vulns: Vulnerability[] }) {
  const total = vulns.length
  const critical = vulns.filter(v => v.severity === 'critical').length
  const high = vulns.filter(v => v.severity === 'high').length
  const medium = vulns.filter(v => v.severity === 'medium').length
  const low = vulns.filter(v => v.severity === 'low').length
  const resolved = vulns.filter(v => v.status === 'resolved' || v.status === 'closed').length

  const cards: KpiCard[] = [
    { label: 'Total Vulnerabilities', value: total, icon: <Shield className="h-5 w-5" />, color: 'text-foreground', bg: 'bg-primary/10' },
    { label: 'Critical', value: critical, icon: <ShieldX className="h-5 w-5" />, color: 'text-red-600 dark:text-red-400', bg: 'bg-red-500/10' },
    { label: 'High', value: high, icon: <ShieldAlert className="h-5 w-5" />, color: 'text-orange-600 dark:text-orange-400', bg: 'bg-orange-500/10' },
    { label: 'Medium', value: medium, icon: <AlertTriangle className="h-5 w-5" />, color: 'text-yellow-600 dark:text-yellow-400', bg: 'bg-yellow-500/10' },
    { label: 'Low', value: low, icon: <AlertCircle className="h-5 w-5" />, color: 'text-blue-600 dark:text-blue-400', bg: 'bg-blue-500/10' },
    { label: 'Resolved', value: resolved, icon: <CheckCircle2 className="h-5 w-5" />, color: 'text-green-600 dark:text-green-400', bg: 'bg-green-500/10' },
  ]

  return (
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
      {cards.map(c => (
        <Card key={c.label} className="relative overflow-hidden">
          <CardContent className="p-4">
            <div className="flex items-center justify-between mb-3">
              <div className={`p-2 rounded-lg ${c.bg} ${c.color}`}>{c.icon}</div>
            </div>
            <p className={`text-2xl font-bold ${c.color}`}>{c.value}</p>
            <p className="text-xs text-muted-foreground mt-1">{c.label}</p>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

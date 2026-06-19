'use client'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ShieldCheck } from 'lucide-react'
import type { SecurityScore } from '@/types/security'

function scoreGrade(score: number) {
  if (score >= 90) return { label: 'Excellent', color: 'text-green-500', ring: 'stroke-green-500' }
  if (score >= 70) return { label: 'Good', color: 'text-yellow-500', ring: 'stroke-yellow-500' }
  return { label: 'Needs Attention', color: 'text-red-500', ring: 'stroke-red-500' }
}

function SubScore({ label, value }: { label: string; value: number }) {
  const grade = scoreGrade(value)
  return (
    <div className="flex items-center justify-between">
      <span className="text-sm text-muted-foreground">{label}</span>
      <div className="flex items-center gap-2">
        <div className="w-24 h-2 rounded-full bg-secondary overflow-hidden">
          <div
            className={`h-full rounded-full transition-all ${value >= 90 ? 'bg-green-500' : value >= 70 ? 'bg-yellow-500' : 'bg-red-500'}`}
            style={{ width: `${value}%` }}
          />
        </div>
        <span className={`text-sm font-semibold w-8 text-right ${grade.color}`}>{value}</span>
      </div>
    </div>
  )
}

export function SecurityScoreGauge({ score }: { score: SecurityScore }) {
  const grade = scoreGrade(score.overall)
  const circumference = 2 * Math.PI * 54
  const offset = circumference - (score.overall / 100) * circumference

  return (
    <Card className="h-full">
      <CardHeader className="pb-2">
        <CardTitle className="text-base flex items-center gap-2">
          <ShieldCheck className="h-5 w-5 text-primary" />
          Security Health Score
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col items-center gap-6">
        <div className="relative w-36 h-36">
          <svg className="w-full h-full -rotate-90" viewBox="0 0 120 120">
            <circle cx="60" cy="60" r="54" fill="none" strokeWidth="8" className="stroke-secondary" />
            <circle
              cx="60" cy="60" r="54" fill="none" strokeWidth="8"
              strokeLinecap="round"
              className={grade.ring}
              strokeDasharray={circumference}
              strokeDashoffset={offset}
              style={{ transition: 'stroke-dashoffset 1s ease-in-out' }}
            />
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <span className={`text-3xl font-bold ${grade.color}`}>{score.overall}</span>
            <span className="text-xs text-muted-foreground">/100</span>
          </div>
        </div>
        <span className={`text-sm font-medium ${grade.color}`}>{grade.label}</span>
        <div className="w-full space-y-3">
          <SubScore label="OWASP Compliance" value={score.owasp} />
          <SubScore label="Secure Coding" value={score.secureCoding} />
          <SubScore label="Infrastructure" value={score.infrastructure} />
          <SubScore label="Dependencies" value={score.dependencies} />
        </div>
      </CardContent>
    </Card>
  )
}

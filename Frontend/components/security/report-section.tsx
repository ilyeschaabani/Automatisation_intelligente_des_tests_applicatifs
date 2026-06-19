'use client'

import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Download, FileText, Clock, CheckCircle2, XCircle } from 'lucide-react'

interface Props {
  scans: any[]
}

export function ReportSection({ scans }: Props) {
  const completedScans = scans
    .filter(s => s.status === 'COMPLETED')
    .sort((a, b) => new Date(b.completedAt || b.startedAt).getTime() - new Date(a.completedAt || a.startedAt).getTime())

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-bold flex items-center gap-2">
        <FileText className="h-5 w-5 text-primary" />
        Rapports de securite
      </h2>

      {completedScans.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center text-muted-foreground">
            <FileText className="h-8 w-8 mx-auto mb-3 opacity-40" />
            <p className="text-sm">Aucun rapport disponible — les rapports sont generes automatiquement apres chaque scan termine</p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4">
          {completedScans.map(scan => {
            const date = new Date(scan.completedAt || scan.startedAt).toLocaleString('fr-FR')
            const typeLabel = scan.scanType === 'SAST' ? 'Analyse statique (SAST)'
              : scan.scanType === 'DAST' ? 'Analyse dynamique (DAST)'
              : scan.scanType === 'SCA' ? 'Analyse des dependances (SCA)'
              : scan.scanType

            return (
              <Card key={scan.id}>
                <CardContent className="p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <h3 className="font-semibold text-sm flex items-center gap-2">
                        {typeLabel}
                        <Badge variant="outline" className="text-[10px]">{scan.scanRef}</Badge>
                      </h3>
                      <p className="text-xs text-muted-foreground mt-1">
                        Moteur: {scan.engine} · Duree: {scan.duration || 'N/A'}
                        {scan.branch && ` · Branche: ${scan.branch}`}
                        {scan.targetUrl && ` · Cible: ${scan.targetUrl}`}
                      </p>
                      <div className="flex items-center gap-2 mt-2 text-xs text-muted-foreground">
                        <Clock className="h-3 w-3" />
                        <span>{date}</span>
                        <CheckCircle2 className="h-3 w-3 text-green-500 ml-2" />
                        <span className="text-green-600">Termine</span>
                      </div>
                    </div>
                    <a
                      href={`/api/security/report/${scan.id}`}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <Button variant="outline" size="sm" className="h-8 text-xs gap-1">
                        <Download className="h-3 w-3" />
                        PDF
                      </Button>
                    </a>
                  </div>
                </CardContent>
              </Card>
            )
          })}
        </div>
      )}
    </div>
  )
}

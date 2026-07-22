'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { AlertCircle, CheckCircle2, Clock } from 'lucide-react'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { listCampaigns, listExecutionResults, type TestCampaignDto } from '@/lib/api-client'
import type { ExecutionResultBackendDto } from '@/lib/api-client'

interface CampaignResult {
  id: number
  campaignName: string
  status: string
  startedAt: string | null
  totalTests: number
  passed: number
  failed: number
  skipped: number
  totalDurationMs: number
}

function formatDuration(ms: number | null): string {
  if (!ms || ms <= 0) return '0:00'
  const totalSeconds = Math.round(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

function formatDateTime(isoString: string | null): string {
  if (!isoString) return '—'
  try {
    const date = new Date(isoString)
    return date.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }) + ' à ' + date.toLocaleTimeString('fr-FR', {
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return isoString
  }
}

const STATUS_LABELS: Record<string, string> = {
  running: 'En cours',
  finished: 'Terminé',
  finished_with_errors: 'Terminé avec erreurs',
  failed: 'Échoué',
  aborted: 'Annulé',
  pending: 'En attente',
}

function getStatusLabel(status: string): string {
  return STATUS_LABELS[status.toLowerCase()] || status
}

function getStatusColor(status: string): string {
  const lowerStatus = status.toLowerCase()
  if (lowerStatus === 'running') return 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400'
  if (lowerStatus === 'finished') return 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400'
  if (lowerStatus === 'finished_with_errors' || lowerStatus === 'failed') return 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400'
  if (lowerStatus === 'aborted') return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-950 dark:text-yellow-400'
  if (lowerStatus === 'pending') return 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400'
  return 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400'
}

export default function ResultsPage() {
  const [campaigns, setCampaigns] = useState<CampaignResult[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    void loadResults()
  }, [])

  const loadResults = async () => {
    setLoading(true)
    setError(null)
    try {
      // Fetch all campaigns
      const allCampaigns = await listCampaigns()

      // Fetch execution results for each campaign and aggregate data
      const campaignResults: CampaignResult[] = await Promise.all(
        allCampaigns.map(async (campaign) => {
          try {
            const executionResults = await listExecutionResults({ campaignId: campaign.id })

            let passed = 0
            let failed = 0
            let skipped = 0
            let totalDurationMs = 0

            if (Array.isArray(executionResults)) {
              executionResults.forEach((exec) => {
                const status = (exec.status || '').toUpperCase()
                if (status === 'SUCCESS') passed++
                else if (status === 'FAILURE') failed++
                else skipped++

                if (exec.durationMs && exec.durationMs > 0) {
                  totalDurationMs += exec.durationMs
                }
              })
            }

            return {
              id: campaign.id,
              campaignName: campaign.name,
              status: campaign.status || 'UNKNOWN',
              startedAt: campaign.startedAt || null,
              totalTests: executionResults.length,
              passed,
              failed,
              skipped,
              totalDurationMs,
            }
          } catch (err) {
            console.error(`Failed to load executions for campaign ${campaign.id}:`, err)
            return {
              id: campaign.id,
              campaignName: campaign.name,
              status: campaign.status || 'UNKNOWN',
              startedAt: campaign.startedAt || null,
              totalTests: 0,
              passed: 0,
              failed: 0,
              skipped: 0,
              totalDurationMs: 0,
            }
          }
        }),
      )

      setCampaigns(campaignResults)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Échec du chargement des résultats de tests')
    } finally {
      setLoading(false)
    }
  }

  // Calculate summary stats
  const totalPassed = campaigns.reduce((sum, c) => sum + c.passed, 0)
  const totalFailed = campaigns.reduce((sum, c) => sum + c.failed, 0)
  const totalTests = campaigns.reduce((sum, c) => sum + c.totalTests, 0)
  const successRate = totalTests > 0 ? ((totalPassed / totalTests) * 100).toFixed(1) : '0.0'

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-7xl space-y-8">
          {/* Page Header */}
          <div>
            <h1 className="text-3xl font-bold text-foreground">Résultats des tests</h1>
            <p className="text-muted-foreground mt-1">
              Consultez les résultats détaillés de toutes les exécutions de tests
            </p>
          </div>

          {error && (
            <Card className="border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950/40 p-4">
              <div className="flex items-center gap-3 text-red-700 dark:text-red-300">
                <AlertCircle size={18} />
                <span>{error}</span>
              </div>
            </Card>
          )}

          {/* Summary Stats */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <Card className="p-6">
              <div className="flex items-center gap-4">
                <div className="p-3 bg-green-100 dark:bg-green-950 rounded-lg">
                  <CheckCircle2 className="text-green-600 dark:text-green-400" size={24} />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Total réussis</p>
                  <p className="text-2xl font-bold text-foreground">{loading ? '—' : totalPassed}</p>
                </div>
              </div>
            </Card>

            <Card className="p-6">
              <div className="flex items-center gap-4">
                <div className="p-3 bg-red-100 dark:bg-red-950 rounded-lg">
                  <AlertCircle className="text-red-600 dark:text-red-400" size={24} />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Total échoués</p>
                  <p className="text-2xl font-bold text-foreground">{loading ? '—' : totalFailed}</p>
                </div>
              </div>
            </Card>

            <Card className="p-6">
              <div className="flex items-center gap-4">
                <div className="p-3 bg-blue-100 dark:bg-blue-950 rounded-lg">
                  <Clock className="text-blue-600 dark:text-blue-400" size={24} />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Taux de réussite</p>
                  <p className="text-2xl font-bold text-foreground">{loading ? '—' : `${successRate}%`}</p>
                </div>
              </div>
            </Card>
          </div>

          {/* Results Table */}
          <Card className="overflow-hidden">
            <div className="overflow-x-auto">
              {loading ? (
                <div className="p-8 text-center text-muted-foreground">
                  Chargement des résultats…
                </div>
              ) : campaigns.length === 0 ? (
                <div className="p-8 text-center text-muted-foreground">
                  Aucune campagne de test trouvée.
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow className="border-b border-border">
                      <TableHead className="text-left">Campagne</TableHead>
                      <TableHead className="text-center">Statut</TableHead>
                      <TableHead className="text-center">Tests</TableHead>
                      <TableHead className="text-center">Réussis</TableHead>
                      <TableHead className="text-center">Échoués</TableHead>
                      <TableHead className="text-center">Ignorés</TableHead>
                      <TableHead className="text-center">Durée</TableHead>
                      <TableHead className="text-center">Date</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {campaigns.map((result) => (
                      <TableRow
                        key={result.id}
                        className="border-b border-border hover:bg-secondary/50"
                      >
                        <TableCell className="font-medium text-foreground">
                          <Link href={`/campaigns/${result.id}`} className="hover:text-primary hover:underline transition-colors">
                            {result.campaignName}
                          </Link>
                        </TableCell>
                        <TableCell className="text-center">
                          <Badge variant="outline" className={getStatusColor(result.status)}>
                            {getStatusLabel(result.status)}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-center text-foreground">
                          {result.totalTests}
                        </TableCell>
                        <TableCell className="text-center text-green-600 dark:text-green-400 font-semibold">
                          {result.passed}
                        </TableCell>
                        <TableCell className="text-center text-red-600 dark:text-red-400 font-semibold">
                          {result.failed}
                        </TableCell>
                        <TableCell className="text-center text-muted-foreground">
                          {result.skipped}
                        </TableCell>
                        <TableCell className="text-center text-muted-foreground">
                          {formatDuration(result.totalDurationMs)}
                        </TableCell>
                        <TableCell className="text-center text-sm text-muted-foreground">
                          {formatDateTime(result.startedAt)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </div>
          </Card>
        </div>
      </main>
    </div>
  )
}

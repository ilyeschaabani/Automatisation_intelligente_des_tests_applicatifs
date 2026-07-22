"use client"

import { useEffect, useMemo, useState } from 'react'
import { Sidebar } from '@/components/sidebar'
import { Header } from '@/components/header'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Download } from 'lucide-react'
import { downloadCampaignReport, downloadReportById, listAllReports, listCampaigns, type TestCampaignDto } from '@/lib/api-client'
import { toast } from '@/hooks/use-toast'

export default function ReportsPage() {
  const [reports, setReports] = useState<{ id: number; campaignId: number; filename: string; generatedAt: string }[]>([])
  const [loading, setLoading] = useState(false)
  const [campaignNames, setCampaignNames] = useState<Record<number, string>>({})
  const [search, setSearch] = useState('')

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      setLoading(true)
      try {
        const [list, campaigns] = await Promise.all([listAllReports(), listCampaigns({})])
        if (cancelled) return
        setReports(list ?? [])
        const nameMap = (campaigns ?? []).reduce<Record<number, string>>((acc, campaign: TestCampaignDto) => {
          if (campaign?.id) acc[campaign.id] = String(campaign.name ?? `Campaign #${campaign.id}`)
          return acc
        }, {})
        setCampaignNames(nameMap)
      } catch (e) {
        console.warn('Failed to list reports', e)
        if (!cancelled) {
          setReports([])
          setCampaignNames({})
          toast({
            title: 'Échec du chargement des rapports',
            description: e instanceof Error ? e.message : 'Impossible de charger les rapports depuis le backend.',
            variant: 'destructive',
          })
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [])

  const filteredReports = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return reports
    return reports.filter((r) => {
      const name = (campaignNames[r.campaignId] ?? '').toLowerCase()
      const idStr = String(r.campaignId)
      return name.includes(q) || idStr.includes(q)
    })
  }, [reports, campaignNames, search])

  const downloadReport = async (r: { id: number; campaignId: number; filename?: string }) => {
    try {
      let blob: Blob
      try {
        blob = await downloadReportById(r.id)
      } catch (error) {
        console.warn('Report file missing, falling back to campaign PDF', error)
        blob = await downloadCampaignReport(r.campaignId)
      }

      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = r.filename ?? `campaign-${r.campaignId}-report.pdf`
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
      toast({ title: 'Rapport téléchargé' })
    } catch (e) {
      toast({ title: 'Erreur', description: (e as Error).message ?? String(e), variant: 'destructive' })
    }
  }

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />
      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />
        <div className="p-6 max-w-7xl">
          <h1 className="text-2xl font-bold mb-4">Rapports</h1>

          <Card>
            <CardHeader>
              <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <CardTitle>Rapports générés</CardTitle>
                  <CardDescription>Tous les rapports de campagne générés.</CardDescription>
                </div>
                <Input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Rechercher par nom ou ID campagne"
                  className="max-w-xs"
                />
              </div>
            </CardHeader>
            <CardContent>
              <div className="overflow-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Campagne</TableHead>
                      <TableHead>Nom du fichier</TableHead>
                      <TableHead className="text-right">Généré le</TableHead>
                      <TableHead className="text-right">Action</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {loading ? (
                      <TableRow>
                        <TableCell colSpan={4} className="py-4 text-center text-muted-foreground">Chargement des rapports…</TableCell>
                      </TableRow>
                    ) : filteredReports.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={4} className="py-4 text-center text-muted-foreground">
                          {search ? 'Aucun rapport correspondant à la recherche.' : 'Aucun rapport disponible.'}
                        </TableCell>
                      </TableRow>
                    ) : (
                      filteredReports.map((r) => (
                        <TableRow key={r.id}>
                          <TableCell className="font-medium">{campaignNames[r.campaignId] ?? `Campagne #${r.campaignId}`}</TableCell>
                          <TableCell>{r.filename}</TableCell>
                          <TableCell className="text-right text-muted-foreground">{new Date(r.generatedAt).toLocaleString()}</TableCell>
                          <TableCell className="text-right">
                            <Button variant="ghost" size="sm" onClick={() => void downloadReport(r)} title="Télécharger">
                              <Download size={16} />
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </div>
            </CardContent>
          </Card>
        </div>
      </main>
    </div>
  )
}

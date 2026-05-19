"use client"

import { useEffect, useState } from 'react'
import { Sidebar } from '@/components/sidebar'
import { Header } from '@/components/header'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { downloadCampaignReport, downloadReportById, listAllReports, listCampaigns, type TestCampaignDto } from '@/lib/api-client'
import { toast } from '@/hooks/use-toast'

export default function ReportsPage() {
  const [reports, setReports] = useState<{ id: number; campaignId: number; filename: string; generatedAt: string }[]>([])
  const [loading, setLoading] = useState(false)
  const [campaignNames, setCampaignNames] = useState<Record<number, string>>({})

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
            title: 'Failed to load reports',
            description: e instanceof Error ? e.message : 'Unable to load reports from the backend.',
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
      toast({ title: 'Report downloaded' })
    } catch (e) {
      toast({ title: 'Error', description: (e as Error).message ?? String(e), variant: 'destructive' })
    }
  }

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />
      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />
        <div className="p-6 max-w-7xl">
          <h1 className="text-2xl font-bold mb-4">Reports</h1>

          <Card>
            <CardHeader>
              <CardTitle>Generated reports</CardTitle>
              <CardDescription>All generated campaign reports (if available from the backend).</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="overflow-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>ID</TableHead>
                      <TableHead>Campaign</TableHead>
                      <TableHead>Filename</TableHead>
                      <TableHead className="text-right">Generated</TableHead>
                      <TableHead className="text-right">Action</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {loading ? (
                      <TableRow>
                        <TableCell colSpan={5} className="py-4 text-center text-muted-foreground">Loading reports…</TableCell>
                      </TableRow>
                    ) : reports.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={5} className="py-4 text-center text-muted-foreground">No reports available</TableCell>
                      </TableRow>
                    ) : (
                      reports.map((r) => (
                        <TableRow key={r.id}>
                          <TableCell className="font-medium">{r.id}</TableCell>
                          <TableCell>{campaignNames[r.campaignId] ?? `Campaign #${r.campaignId}`}</TableCell>
                          <TableCell>{r.filename}</TableCell>
                          <TableCell className="text-right text-muted-foreground">{new Date(r.generatedAt).toLocaleString()}</TableCell>
                          <TableCell className="text-right">
                            <Button size="sm" onClick={() => void downloadReport(r)}>Download</Button>
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
 

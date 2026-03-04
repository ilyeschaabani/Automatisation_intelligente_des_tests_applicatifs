'use client'

import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Play, Search, ArrowLeft } from 'lucide-react'

import {
  createCampaignExecution,
  deleteCampaignExecution,
  listCampaignExecutions,
  type CampaignExecution,
  type CampaignExecutionStatus,
} from '@/lib/campaign-executions'

const statusStyle: Record<CampaignExecutionStatus, string> = {
  QUEUED: 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
  RUNNING: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400',
  FINISHED: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  ERROR: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400',
}

function formatWhen(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleString()
}

function matchesQuery(exe: CampaignExecution, query: string): boolean {
  if (!query) return true
  const q = query.toLowerCase()
  const selected = Array.isArray(exe.selectedTests) ? exe.selectedTests.join(',') : ''
  return (
    exe.id.toLowerCase().includes(q) ||
    exe.status.toLowerCase().includes(q) ||
    exe.scope.toLowerCase().includes(q) ||
    selected.toLowerCase().includes(q)
  )
}

export default function ExecutionsClient() {
  const searchParams = useSearchParams()
  const campaignId = searchParams.get('campaignId')?.trim() || null

  const [executions, setExecutions] = useState<CampaignExecution[]>([])
  const [searchTerm, setSearchTerm] = useState('')

  useEffect(() => {
    if (!campaignId) {
      setExecutions([])
      return
    }
    setExecutions(listCampaignExecutions(campaignId))
  }, [campaignId])

  const filtered = useMemo(() => {
    const q = searchTerm.trim()
    if (!q) return executions
    return executions.filter((e) => matchesQuery(e, q))
  }, [executions, searchTerm])

  const runCampaign = () => {
    if (!campaignId) return
    const created = createCampaignExecution({ campaignId, scope: 'CAMPAIGN' })
    setExecutions((prev) => [created, ...prev])
  }

  const onDelete = (id: string) => {
    if (!campaignId) return
    deleteCampaignExecution(campaignId, id)
    setExecutions(listCampaignExecutions(campaignId))
  }

  return (
    <div className="flex h-screen bg-background">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto p-8">
          <div className="max-w-7xl mx-auto">
            <div className="flex flex-col gap-6 mb-8">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <h1 className="text-3xl font-bold text-foreground mb-2">Executions</h1>
                  <p className="text-muted-foreground">
                    {campaignId
                      ? (
                          <>
                            Campaign executions for <span className="font-medium text-foreground">{campaignId}</span>.
                          </>
                        )
                      : 'Open a campaign and view its executions.'}
                  </p>
                </div>

                {campaignId ? (
                  <div className="flex items-center gap-2 shrink-0">
                    <Button asChild variant="outline" size="sm" className="gap-2">
                      <Link href={`/campaigns/${campaignId}`}>
                        <ArrowLeft className="h-4 w-4" />
                        Back to campaign
                      </Link>
                    </Button>
                    <Button size="sm" className="gap-2" onClick={runCampaign}>
                      <Play className="h-4 w-4" />
                      Run campaign
                    </Button>
                  </div>
                ) : null}
              </div>

              <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                <div className="relative flex-1 md:max-w-sm">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
                  <input
                    type="text"
                    placeholder="Search executions..."
                    className="w-full pl-10 pr-4 py-2 bg-card border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                  />
                </div>
              </div>
            </div>

            {!campaignId ? (
              <Card className="p-6">
                <p className="text-sm text-muted-foreground">
                  No campaign selected. Go to campaigns and open one.
                </p>
                <div className="mt-4">
                  <Button asChild variant="outline">
                    <Link href="/campaigns">Go to campaigns</Link>
                  </Button>
                </div>
              </Card>
            ) : filtered.length === 0 ? (
              <Card className="p-6">
                <p className="text-sm text-muted-foreground">No executions found.</p>
              </Card>
            ) : (
              <Card className="p-6">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Run</TableHead>
                      <TableHead className="w-36">Scope</TableHead>
                      <TableHead className="w-32">Status</TableHead>
                      <TableHead>Created</TableHead>
                      <TableHead className="w-28 text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filtered.map((exe) => (
                      <TableRow key={exe.id}>
                        <TableCell className="font-medium">{exe.id}</TableCell>
                        <TableCell>
                          <Badge variant="secondary">{exe.scope}</Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline" className={statusStyle[exe.status]}>
                            {exe.status}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-muted-foreground">{formatWhen(exe.createdAt)}</TableCell>
                        <TableCell className="text-right">
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={() => onDelete(exe.id)}
                          >
                            Delete
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Card>
            )}
          </div>
        </main>
      </div>
    </div>
  )
}

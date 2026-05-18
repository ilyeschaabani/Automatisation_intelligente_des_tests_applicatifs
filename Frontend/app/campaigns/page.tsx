"use client"

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { CampaignCard } from '@/components/campaign-card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import Link from 'next/link'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Plus, Search } from 'lucide-react'

import { useEffect, useMemo, useState } from 'react'
import { toast } from '@/hooks/use-toast'
import { getProjects, listCampaigns, listExecutionResults, type Project, type TestCampaignDto } from '@/lib/api-client'

type CardStatus = 'Running' | 'Completed' | 'Failed' | 'Scheduled'
type CardType = 'Functional' | 'API' | 'Regression'

type CampaignStats = {
  tests: number
  passed: number
  failed: number
  lastRun: string
}

const demoCampaignCard = {
  name: 'Payment Gateway API Tests',
  type: 'API' as const,
  status: 'Running' as const,
  progress: 65,
  tests: 89,
  passed: 87,
  failed: 2,
  lastRun: '5 mins ago',
}

function mapStatus(status: unknown): CardStatus {
  const value = String(status ?? '').toUpperCase()
  if (value === 'RUNNING') return 'Running'
  if (value === 'FINISHED') return 'Completed'
  if (value === 'FINISHED_WITH_ERRORS') return 'Failed'
  if (value === 'PENDING') return 'Scheduled'
  return 'Scheduled'
}

function mapType(_campaign: TestCampaignDto): CardType {
  return 'Functional'
}

function formatLastRun(value: string | null | undefined): string {
  if (!value) return '—'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString()
}

export default function CampaignsPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [projectsLoading, setProjectsLoading] = useState(true)
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null)

  const [campaigns, setCampaigns] = useState<TestCampaignDto[]>([])
  const [campaignsLoading, setCampaignsLoading] = useState(false)
  const [search, setSearch] = useState('')
  const [campaignStats, setCampaignStats] = useState<Record<number, CampaignStats>>({})
  const [statsLoading, setStatsLoading] = useState(false)

  useEffect(() => {
    let cancelled = false

    const run = async () => {
      setProjectsLoading(true)
      try {
        const data = await getProjects()
        if (cancelled) return
        const list = Array.isArray(data) ? data : []
        setProjects(list)
        if (list.length > 0) setSelectedProjectId((prev) => prev ?? list[0].id)
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to load projects'
        if (!cancelled) {
          setProjects([])
          toast({ title: 'Failed to load projects', description: message, variant: 'destructive' })
        }
      } finally {
        if (!cancelled) setProjectsLoading(false)
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    const run = async () => {
      setCampaignsLoading(true)
      try {
        // Load all campaigns without project filter
        const data = await listCampaigns({})
        if (cancelled) return
        setCampaigns(Array.isArray(data) ? data : [])
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to load campaigns'
        if (!cancelled) {
          setCampaigns([])
          toast({ title: 'Failed to load campaigns', description: message, variant: 'destructive' })
        }
      } finally {
        if (!cancelled) setCampaignsLoading(false)
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [])  // Empty dependency array - load all campaigns on mount

  useEffect(() => {
    let cancelled = false

    const run = async () => {
      if (campaigns.length === 0) {
        setCampaignStats({})
        return
      }

      setStatsLoading(true)
      try {
        const entries = await Promise.all(
          campaigns.map(async (campaign) => {
            const results = await listExecutionResults({ campaignId: campaign.id })
            const tests = results.length
            const passed = results.filter((result) => String(result.status).toUpperCase() === 'SUCCESS').length
            const failed = results.filter((result) => String(result.status).toUpperCase() === 'FAILURE' || String(result.status).toUpperCase() === 'ERROR').length
            const lastRun = results
              .map((result) => result.executedAt)
              .sort((left, right) => new Date(right).getTime() - new Date(left).getTime())[0]

            return [campaign.id, {
              tests,
              passed,
              failed,
              lastRun: formatLastRun(lastRun ?? campaign.finishedAt ?? campaign.startedAt ?? campaign.createdAt ?? '—'),
            }] as const
          }),
        )

        if (!cancelled) {
          setCampaignStats(Object.fromEntries(entries))
        }
      } catch (error) {
        if (!cancelled) {
          console.warn('Failed to load campaign execution stats', error)
          setCampaignStats({})
        }
      } finally {
        if (!cancelled) setStatsLoading(false)
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [campaigns])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return campaigns
    return campaigns.filter((c) => String(c.name ?? '').toLowerCase().includes(q))
  }, [campaigns, search])

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-7xl">
          {/* Page Header */}
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground">Test Campaigns</h1>
            <p className="text-muted-foreground mt-1">
              Manage and monitor all your test campaigns
            </p>
          </div>

          {/* Filters */}
          <div className="flex flex-col md:flex-row gap-4 mb-8">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground size-5" />
              <Input
                placeholder="Search campaigns..."
                className="pl-10"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <Select
              value={selectedProjectId ? String(selectedProjectId) : ''}
              onValueChange={(v) => setSelectedProjectId(Number(v))}
              disabled={projectsLoading || projects.length === 0}
            >
              <SelectTrigger className="w-full md:w-64">
                <SelectValue placeholder={projectsLoading ? 'Loading projects…' : 'Select project'} />
              </SelectTrigger>
              <SelectContent>
                {projects.length === 0 ? (
                  <SelectItem value="__none" disabled>
                    No projects found
                  </SelectItem>
                ) : (
                  projects
                    .slice()
                    .sort((a, b) => String(a.name).localeCompare(String(b.name)))
                    .map((p) => (
                      <SelectItem key={p.id} value={String(p.id)}>
                        {p.name}
                      </SelectItem>
                    ))
                )}
              </SelectContent>
            </Select>
            <Button
              asChild
              className="bg-primary hover:bg-primary/90 text-primary-foreground gap-2 w-full md:w-auto"
            >
              <Link href="/campaigns/new">
                <Plus size={20} />
                New Campaign
              </Link>
            </Button>
          </div>

          {/* Campaigns Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Static demo card kept for UI testing */}
            <CampaignCard {...demoCampaignCard} />

            {campaignsLoading ? (
              <p className="text-sm text-muted-foreground">Loading campaigns…</p>
            ) : (
              filtered.map((campaign) => (
                (() => {
                  const stats = campaignStats[campaign.id]
                  return (
                <CampaignCard
                  key={campaign.id}
                  id={campaign.id}
                  name={String(campaign.name ?? 'Untitled campaign')}
                  type={mapType(campaign)}
                  status={mapStatus(campaign.status)}
                  progress={0}
                  projectId={campaign.projectId}
                  tests={stats?.tests ?? 0}
                  passed={stats?.passed ?? 0}
                  failed={stats?.failed ?? 0}
                  lastRun={stats?.lastRun ?? formatLastRun(campaign.finishedAt ?? campaign.startedAt ?? campaign.createdAt)}
                />
                  )
                })()
              ))
            )}
          </div>
        </div>
      </main>
    </div>
  )
}

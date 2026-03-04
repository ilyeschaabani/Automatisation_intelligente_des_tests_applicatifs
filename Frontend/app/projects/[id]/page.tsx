'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'

import { CampaignCard } from '@/components/campaign-card'
import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'

import {
  getProjects,
  type Project,
} from '@/lib/api-client'

const campaignsForProjectSeed = [
  {
    name: 'Banking Mobile App - v2.5',
    type: 'Functional' as const,
    status: 'Running' as const,
    progress: 65,
    tests: 145,
    passed: 94,
    failed: 0,
    lastRun: '5 mins ago',
  },
  {
    name: 'Payment Gateway API Tests',
    type: 'API' as const,
    status: 'Completed' as const,
    progress: 100,
    tests: 89,
    passed: 87,
    failed: 2,
    lastRun: '2 hours ago',
  },
  {
    name: 'Regression Suite - Production',
    type: 'Regression' as const,
    status: 'Scheduled' as const,
    progress: 0,
    tests: 234,
    passed: 0,
    failed: 0,
    lastRun: 'Tomorrow 2:00 AM',
  },
  {
    name: 'UI Components - v3.0',
    type: 'Functional' as const,
    status: 'Failed' as const,
    progress: 85,
    tests: 98,
    passed: 84,
    failed: 14,
    lastRun: '30 mins ago',
  },
] as const

const statusStyle: Record<'Deployed' | 'Not deployed', string> = {
  Deployed: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  'Not deployed': 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

export default function ProjectDetailsPage({
}: {}) {
  const params = useParams<{ id?: string | string[] }>()
  const id = Array.isArray(params?.id) ? params?.id[0] : params?.id

  const [project, setProject] = useState<Project | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (!id) {
      setProject(null)
      setIsLoading(false)
      return
    }

    let cancelled = false

    const run = async () => {
      setIsLoading(true)
      try {
        const projects = await getProjects()
        const found = projects.find((p) => String(p.id) === String(id))
        if (!cancelled) setProject(found ?? null)
      } catch (error) {
        console.error('Failed to load project details', error)
        if (!cancelled) setProject(null)
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [id])

  const projectType = useMemo(() => {
    if (!project) return '—'
    return String(project.projectType ?? '—')
  }, [project])

  const sourceType = useMemo(() => {
    if (!project) return '—'
    return String(project.sourceType ?? '—')
  }, [project])

  const repositoryUrl = useMemo(() => {
    if (!project) return ''
    return String(project.repositoryUrl ?? '')
  }, [project])

  const gitTokenId = useMemo(() => {
    if (!project) return '—'
    return project.gitTokenId ? String(project.gitTokenId) : '—'
  }, [project])

  const technologyStack = useMemo(() => {
    if (!project) return '—'
    return project.technologyStack ? String(project.technologyStack) : '—'
  }, [project])

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-4xl">
          <div className="flex items-center justify-between mb-8">
            <div>
              <h1 className="text-3xl font-bold text-foreground">
                {project?.name ?? 'Project'}
              </h1>
              <p className="text-muted-foreground mt-1">ID: {id ?? '—'}</p>
            </div>

            <Button asChild variant="outline">
              <Link href="/projects">Back to projects</Link>
            </Button>
          </div>

          <Card>
            {isLoading ? (
              <CardContent className="pt-6">
                <p className="text-sm text-muted-foreground">Loading…</p>
              </CardContent>
            ) : !project ? (
              <CardContent className="pt-6">
                <p className="text-sm text-muted-foreground">Not found</p>
              </CardContent>
            ) : (
              <>
                <CardHeader>
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0 space-y-1">
                      <CardTitle className="truncate">{project.name}</CardTitle>
                      <CardDescription>
                        {project.repositoryUrl ? 'Repository linked' : 'No repository'}
                      </CardDescription>
                    </div>
                    <Badge
                      variant="outline"
                      className={statusStyle[project.deployed ? 'Deployed' : 'Not deployed']}
                    >
                      {project.deployed ? 'Deployed' : 'Not deployed'}
                    </Badge>
                  </div>

                  <div className="flex flex-wrap gap-2 pt-2">
                    <Badge variant="outline">{projectType}</Badge>
                    <Badge variant="outline">{sourceType}</Badge>
                  </div>
                </CardHeader>

                <Separator />

                <CardContent className="pt-6">
                  <Table>
                    <TableBody>
                      <TableRow>
                        <TableCell className="w-40 text-muted-foreground">Repository</TableCell>
                        <TableCell className="font-medium">
                          {repositoryUrl ? (
                            <Button
                              asChild
                              variant="link"
                              className="h-auto p-0 whitespace-normal break-all"
                            >
                              <a href={repositoryUrl} target="_blank" rel="noreferrer">
                                {repositoryUrl}
                              </a>
                            </Button>
                          ) : (
                            '—'
                          )}
                        </TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell className="text-muted-foreground">Project type</TableCell>
                        <TableCell className="font-medium">{projectType}</TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell className="text-muted-foreground">Source type</TableCell>
                        <TableCell className="font-medium">{sourceType}</TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell className="text-muted-foreground">Git token id</TableCell>
                        <TableCell className="font-medium">{gitTokenId}</TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell className="text-muted-foreground">Technology stack</TableCell>
                        <TableCell className="font-medium">
                          {technologyStack}
                        </TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell className="text-muted-foreground">Created at</TableCell>
                        <TableCell className="font-medium">
                          {project.createdAt ? formatDate(project.createdAt) : '—'}
                        </TableCell>
                      </TableRow>
                    </TableBody>
                  </Table>
                </CardContent>
              </>
            )}
          </Card>

          <div className="mt-6">
            <Card>
              <CardHeader>
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0 space-y-1">
                    <CardTitle>Campaigns</CardTitle>
                    <CardDescription>
                      Create and track test campaigns for this project
                    </CardDescription>
                  </div>

                  <div className="flex items-center gap-2">
                    <Button asChild variant="outline">
                      <Link href="/campaigns">View all</Link>
                    </Button>
                    <Button asChild disabled={!project}>
                      <Link href="/campaigns/new">New campaign</Link>
                    </Button>
                  </div>
                </div>
              </CardHeader>

              <Separator />

              <CardContent className="pt-6">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {campaignsForProjectSeed.map((campaign) => (
                    <CampaignCard key={campaign.name} {...campaign} />
                  ))}
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      </main>
    </div>
  )
}

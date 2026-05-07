'use client'

import { useEffect, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { toast } from '@/hooks/use-toast'

import {
  createCampaign,
  getCampaign,
  getProjects,
  type Project,
  type TestCampaignDto,
  type TestCampaignCreateRequest,
} from '@/lib/api-client'
import { environmentService } from '@/services/environments'
import { testCaseService } from '@/services/testCases'
import { testSuiteService } from '@/services/suites'
import type { Environment, TestCase } from '@/types/ms-gestion'

type CampaignCaseRow = TestCase & { suiteName: string }

type CampaignFormProps = {
  mode: 'create' | 'edit'
  campaignId?: number
}

export function CampaignForm({ mode, campaignId }: CampaignFormProps) {
  const router = useRouter()

  const [projects, setProjects] = useState<Project[]>([])
  const [projectsLoading, setProjectsLoading] = useState(false)
  const [projectsError, setProjectsError] = useState<string | null>(null)

  const [campaignLoading, setCampaignLoading] = useState(mode === 'edit')
  const [campaign, setCampaign] = useState<TestCampaignDto | null>(null)

  const [projectId, setProjectId] = useState<number | null>(null)
  const [name, setName] = useState('')
  const [appVersion, setAppVersion] = useState('')
  const [gitBranch, setGitBranch] = useState('')
  const [environmentId, setEnvironmentId] = useState<number | null>(null)
  const [triggerMode, setTriggerMode] = useState('MANUAL')

  const [environments, setEnvironments] = useState<Environment[]>([])
  const [environmentsLoading, setEnvironmentsLoading] = useState(false)
  const [environmentsError, setEnvironmentsError] = useState<string | null>(null)

  const [testCases, setTestCases] = useState<CampaignCaseRow[]>([])
  const [testCasesLoading, setTestCasesLoading] = useState(false)
  const [testCasesError, setTestCasesError] = useState<string | null>(null)

  const [search, setSearch] = useState('')
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())

  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false

    const run = async () => {
      setProjectsLoading(true)
      setProjectsError(null)
      try {
        const data = await getProjects()
        if (!cancelled) setProjects(Array.isArray(data) ? data : [])
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to load projects'
        if (!cancelled) {
          setProjects([])
          setProjectsError(message)
          toast({
            title: 'Failed to load projects',
            description: message,
            variant: 'destructive',
          })
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
    if (mode === 'edit') return
    setEnvironmentId(null)
    setSelectedIds(new Set())
  }, [mode, projectId])

  useEffect(() => {
    if (mode !== 'edit') return
    if (!campaignId || !projectId) return

    let cancelled = false

    const run = async () => {
      setCampaignLoading(true)
      setSubmitError(null)
      try {
        const loaded = await getCampaign(projectId, campaignId)
        if (cancelled) return

        setCampaign(loaded)
        setName(loaded.name ?? '')
        setAppVersion(String(loaded.appVersion ?? ''))
        setGitBranch(String(loaded.gitBranch ?? ''))
        setTriggerMode(String(loaded.triggerMode ?? 'MANUAL'))
        setEnvironmentId(loaded.environmentId ?? null)
        setSelectedIds(new Set())
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to load campaign'
        if (!cancelled) {
          setCampaign(null)
          setSubmitError(message)
        }
      } finally {
        if (!cancelled) setCampaignLoading(false)
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [campaignId, mode, projectId])

  useEffect(() => {
    if (!projectId) {
      setEnvironments([])
      setTestCases([])
      return
    }

    let cancelled = false

    const run = async () => {
      setEnvironmentsLoading(true)
      setEnvironmentsError(null)
      setTestCasesLoading(true)
      setTestCasesError(null)
      try {
        const [envs, suitesData] = await Promise.all([
          environmentService.getAll(projectId),
          testSuiteService.getAll(projectId),
        ])
        if (cancelled) return

        const suitesList = Array.isArray(suitesData) ? suitesData : []
        setEnvironments(Array.isArray(envs) ? envs : [])
        if (suitesList.length === 0) {
          setTestCases([])
          return
        }

        const casesBySuite = await Promise.all(
          suitesList.map(async (suite) => {
            const cases = await testCaseService.getAll(suite.id)
            return (Array.isArray(cases) ? cases : []).map((tc) => ({
              ...tc,
              suiteName: suite.name,
            }))
          }),
        )

        if (cancelled) return
        setTestCases(casesBySuite.flat())
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to load project data'
        if (!cancelled) {
          setEnvironments([])
          setTestCases([])
          setEnvironmentsError(message)
          setTestCasesError(message)
          toast({
            title: 'Failed to load project data',
            description: message,
            variant: 'destructive',
          })
        }
      } finally {
        if (!cancelled) {
          setEnvironmentsLoading(false)
          setTestCasesLoading(false)
        }
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [projectId])

  const filteredTestCases = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return testCases

    return testCases.filter((tc) => {
      const nameValue = String(tc.title ?? '').toLowerCase()
      const typeValue = String(tc.type ?? '').toLowerCase()
      const priorityValue = String(tc.priority ?? '').toLowerCase()
      const suiteValue = String(tc.suiteName ?? '').toLowerCase()
      const riskValue = String(tc.riskLevel ?? '').toLowerCase()
      return (
        nameValue.includes(q) ||
        typeValue.includes(q) ||
        priorityValue.includes(q) ||
        suiteValue.includes(q) ||
        riskValue.includes(q) ||
        String(tc.id).includes(q)
      )
    })
  }, [search, testCases])

  const allVisibleSelected = useMemo(() => {
    if (filteredTestCases.length === 0) return false
    return filteredTestCases.every((tc) => selectedIds.has(tc.id))
  }, [filteredTestCases, selectedIds])

  const toggleOne = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const selectAll = () => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      for (const tc of filteredTestCases) next.add(tc.id)
      return next
    })
  }

  const clearAll = () => {
    setSelectedIds(new Set())
  }

  const canSubmit = Boolean(projectId && name.trim() && environmentId) && !submitting

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!projectId) return
    if (!name.trim()) return
    if (!environmentId) {
      setSubmitError('Select an environment.')
      return
    }

    setSubmitting(true)
    setSubmitError(null)

    try {
      const testCaseIds = Array.from(selectedIds)

      if (mode === 'create') {
        const createPayload: TestCampaignCreateRequest = {
          projectId,
          name: name.trim(),
          environmentId,
          appVersion: appVersion.trim() ? appVersion.trim() : null,
          gitBranch: gitBranch.trim() ? gitBranch.trim() : null,
          triggerMode: triggerMode.trim() ? triggerMode.trim() : 'MANUAL',
          testCaseIds,
        }

        const created = await createCampaign(createPayload)

        toast({
          title: 'Campaign created',
          description: `Created ${created.name}`,
        })

        router.push(`/campaigns/${created.id}`)
        router.refresh()
        return
      }

      throw new Error('Campaign updates are not supported yet.')
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to save campaign'
      setSubmitError(message)
      toast({
        title: 'Campaign save failed',
        description: message,
        variant: 'destructive',
      })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{mode === 'create' ? 'Create Campaign' : 'Edit Campaign'}</CardTitle>
        <CardDescription>
          {mode === 'create'
            ? 'Select a project, fill campaign fields, then attach test cases.'
            : 'Update campaign fields and its attached test cases.'}
        </CardDescription>
      </CardHeader>

      <Separator />

      <CardContent className="pt-6">
        {campaignLoading ? (
          <p className="text-sm text-muted-foreground">Loading campaign…</p>
        ) : submitError ? (
          <p className="text-sm text-destructive">{submitError}</p>
        ) : null}

        <form className="space-y-8" onSubmit={onSubmit}>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>Project *</Label>
              <Select
                value={projectId ? String(projectId) : ''}
                onValueChange={(v) => setProjectId(Number(v))}
                disabled={projectsLoading || mode === 'edit'}
              >
                <SelectTrigger>
                  <SelectValue placeholder={projectsLoading ? 'Loading…' : 'Select project'} />
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
              {projectsError ? (
                <p className="text-xs text-destructive">{projectsError}</p>
              ) : null}
              {mode === 'edit' && campaign ? (
                <p className="text-xs text-muted-foreground">Project is locked for editing.</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="campaignName">Name *</Label>
              <Input
                id="campaignName"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Payment Gateway API Tests"
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="campaignVersion">App version</Label>
              <Input
                id="campaignVersion"
                value={appVersion}
                onChange={(e) => setAppVersion(e.target.value)}
                placeholder="v2.5"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="campaignBranch">Git branch</Label>
              <Input
                id="campaignBranch"
                value={gitBranch}
                onChange={(e) => setGitBranch(e.target.value)}
                placeholder="main"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="environment">Environment *</Label>
              <Select
                value={environmentId ? String(environmentId) : ''}
                onValueChange={(value) => setEnvironmentId(Number(value))}
                disabled={environmentsLoading || environments.length === 0}
              >
                <SelectTrigger id="environment">
                  <SelectValue
                    placeholder={
                      environmentsLoading ? 'Loading environments…' : 'Select environment'
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {environments.length === 0 ? (
                    <SelectItem value="__none" disabled>
                      No environments found
                    </SelectItem>
                  ) : (
                    environments.map((env) => (
                      <SelectItem key={env.id} value={String(env.id)}>
                        {env.name}
                      </SelectItem>
                    ))
                  )}
                </SelectContent>
              </Select>
              {environmentsError ? (
                <p className="text-xs text-destructive">{environmentsError}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="triggerType">Trigger mode</Label>
              <Select value={triggerMode} onValueChange={setTriggerMode}>
                <SelectTrigger id="triggerType">
                  <SelectValue placeholder="Select trigger mode" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="MANUAL">MANUAL</SelectItem>
                  <SelectItem value="SCHEDULED">SCHEDULED</SelectItem>
                  <SelectItem value="CI">CI</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="space-y-4">
            <div className="flex items-center justify-between gap-3">
              <div className="min-w-0">
                <h3 className="text-sm font-semibold text-foreground">Test cases</h3>
                <p className="text-xs text-muted-foreground">
                  Select test cases for this campaign.
                </p>
              </div>

              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={selectAll}
                  disabled={testCasesLoading || filteredTestCases.length === 0}
                >
                  Select all
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={clearAll}
                  disabled={testCasesLoading || selectedIds.size === 0}
                >
                  Clear
                </Button>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <Input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search by title, suite, type, priority, risk, id…"
                disabled={testCasesLoading}
              />
              <div className="text-xs text-muted-foreground whitespace-nowrap">
                {selectedIds.size} selected
              </div>
            </div>

            {testCasesLoading ? (
              <p className="text-sm text-muted-foreground">Loading test cases…</p>
            ) : testCasesError ? (
              <p className="text-sm text-destructive">{testCasesError}</p>
            ) : filteredTestCases.length === 0 ? (
              <p className="text-sm text-muted-foreground">No test cases found.</p>
            ) : (
              <div className="rounded-lg border border-border overflow-hidden">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-10">
                        <Checkbox
                          checked={allVisibleSelected}
                          onCheckedChange={(checked) => {
                            if (checked) selectAll()
                            else clearAll()
                          }}
                          aria-label="Select all visible test cases"
                        />
                      </TableHead>
                      <TableHead>Name</TableHead>
                      <TableHead className="w-40">Suite</TableHead>
                      <TableHead className="w-32">Type</TableHead>
                      <TableHead className="w-32">Priority</TableHead>
                      <TableHead className="w-40">Risk</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredTestCases.map((tc) => (
                      <TableRow key={tc.id}>
                        <TableCell>
                          <Checkbox
                            checked={selectedIds.has(tc.id)}
                            onCheckedChange={() => toggleOne(tc.id)}
                            aria-label={`Select test case ${tc.title}`}
                          />
                        </TableCell>
                        <TableCell className="font-medium">{tc.title}</TableCell>
                        <TableCell>{tc.suiteName}</TableCell>
                        <TableCell>{tc.type ?? '—'}</TableCell>
                        <TableCell>{tc.priority ?? '—'}</TableCell>
                        <TableCell>{tc.riskLevel ?? '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </div>

          {submitError ? <p className="text-sm text-destructive">{submitError}</p> : null}

          <div className="flex items-center justify-end gap-3">
            <Button
              type="button"
              variant="outline"
              onClick={() => router.push('/campaigns')}
              disabled={submitting}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={!canSubmit}>
              {submitting
                ? mode === 'create'
                  ? 'Creating…'
                  : 'Saving…'
                : mode === 'create'
                  ? 'Create campaign'
                  : 'Save changes'}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

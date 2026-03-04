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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
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
  attachCampaignTestCases,
  createTestCase,
  createCampaign,
  getCampaign,
  getProjects,
  listTestCases,
  updateCampaign,
  type Project,
  type TestCampaignDto,
  type TestCampaignCreateRequest,
  type TestCampaignUpdateRequest,
  type TestCaseDto,
} from '@/lib/api-client'

function toIsoOrNull(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) return null
  const date = new Date(trimmed)
  if (Number.isNaN(date.getTime())) return null
  return date.toISOString()
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

function toDateTimeLocalValue(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

function isoToLocalInputValue(iso: string | null | undefined): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return toDateTimeLocalValue(date)
}

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
  const [version, setVersion] = useState('')
  const [status, setStatus] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [environment, setEnvironment] = useState('')
  const [triggerType, setTriggerType] = useState('')
  const [sessionStatus, setSessionStatus] = useState('')
  const [executionStartDate, setExecutionStartDate] = useState('')
  const [executionEndDate, setExecutionEndDate] = useState('')
  const [createdBy, setCreatedBy] = useState('')

  const [testCases, setTestCases] = useState<TestCaseDto[]>([])
  const [testCasesLoading, setTestCasesLoading] = useState(false)
  const [testCasesError, setTestCasesError] = useState<string | null>(null)

  const [isCreateTestCaseOpen, setIsCreateTestCaseOpen] = useState(false)
  const [isCreatingTestCase, setIsCreatingTestCase] = useState(false)
  const [newTestCaseName, setNewTestCaseName] = useState('')
  const [newTestCaseDescription, setNewTestCaseDescription] = useState('')
  const [newTestCaseType, setNewTestCaseType] = useState('')
  const [newTestCasePriority, setNewTestCasePriority] = useState('')
  const [newTestCaseTool, setNewTestCaseTool] = useState('')
  const [newTestCaseRiskScore, setNewTestCaseRiskScore] = useState('')

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
    if (mode !== 'edit') return
    if (!campaignId) return

    let cancelled = false

    const run = async () => {
      setCampaignLoading(true)
      setSubmitError(null)
      try {
        const loaded = await getCampaign(campaignId)
        if (cancelled) return

        setCampaign(loaded)
        setProjectId(loaded.projectId)
        setName(loaded.name ?? '')
        setVersion(String(loaded.version ?? ''))
        setStatus(String(loaded.status ?? ''))
        setStartDate(isoToLocalInputValue(loaded.startDate))
        setEndDate(isoToLocalInputValue(loaded.endDate))
        setEnvironment(String(loaded.environment ?? ''))
        setTriggerType(String(loaded.triggerType ?? ''))
        setSessionStatus(String(loaded.sessionStatus ?? ''))
        setExecutionStartDate(isoToLocalInputValue(loaded.executionStartDate))
        setExecutionEndDate(isoToLocalInputValue(loaded.executionEndDate))
        setCreatedBy(String(loaded.createdBy ?? ''))

        setSelectedIds(new Set(Array.isArray(loaded.testCaseIds) ? loaded.testCaseIds : []))
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
  }, [campaignId, mode])

  useEffect(() => {
    let cancelled = false

    const run = async () => {
      setTestCasesLoading(true)
      setTestCasesError(null)
      try {
        const data = await listTestCases()
        if (cancelled) return
        setTestCases(Array.isArray(data) ? data : [])
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to load test cases'
        if (!cancelled) {
          setTestCases([])
          setTestCasesError(message)
          toast({
            title: 'Failed to load test cases',
            description: message,
            variant: 'destructive',
          })
        }
      } finally {
        if (!cancelled) setTestCasesLoading(false)
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [])

  const resetNewTestCaseForm = () => {
    setNewTestCaseName('')
    setNewTestCaseDescription('')
    setNewTestCaseType('')
    setNewTestCasePriority('')
    setNewTestCaseTool('')
    setNewTestCaseRiskScore('')
  }

  const onCreateTestCase = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newTestCaseName.trim()) return

    setIsCreatingTestCase(true)
    try {
      const riskScoreRaw = newTestCaseRiskScore.trim()
      const riskScore = riskScoreRaw ? Number(riskScoreRaw) : null
      if (riskScoreRaw && Number.isNaN(riskScore as number)) {
        throw new Error('Risk score must be a number')
      }

      const created = await createTestCase({
        name: newTestCaseName.trim(),
        description: newTestCaseDescription.trim() ? newTestCaseDescription.trim() : null,
        testType: newTestCaseType.trim() ? newTestCaseType.trim() : null,
        priority: newTestCasePriority.trim() ? newTestCasePriority.trim() : null,
        tool: newTestCaseTool.trim() ? newTestCaseTool.trim() : null,
        riskScore,
      })

      setTestCases((prev) => {
        const next = [created, ...prev]
        const byId = new Map<number, TestCaseDto>()
        for (const tc of next) byId.set(tc.id, tc)
        return Array.from(byId.values())
      })
      setSelectedIds((prev) => {
        const next = new Set(prev)
        next.add(created.id)
        return next
      })

      toast({
        title: 'Test case created',
        description: `Added ${created.name}`,
      })

      setIsCreateTestCaseOpen(false)
      resetNewTestCaseForm()
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to create test case'
      toast({
        title: 'Test case create failed',
        description: message,
        variant: 'destructive',
      })
    } finally {
      setIsCreatingTestCase(false)
    }
  }

  const filteredTestCases = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return testCases

    return testCases.filter((tc) => {
      const nameValue = String(tc.name ?? '').toLowerCase()
      const toolValue = String(tc.tool ?? '').toLowerCase()
      const typeValue = String(tc.testType ?? '').toLowerCase()
      const priorityValue = String(tc.priority ?? '').toLowerCase()
      return (
        nameValue.includes(q) ||
        toolValue.includes(q) ||
        typeValue.includes(q) ||
        priorityValue.includes(q) ||
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

  const canSubmit = Boolean(projectId && name.trim()) && !submitting

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!projectId) return
    if (!name.trim()) return

    setSubmitting(true)
    setSubmitError(null)

    try {
      const basePayload = {
        name: name.trim(),
        version: version.trim() ? version.trim() : null,
        status: status.trim() ? status.trim() : null,
        startDate: toIsoOrNull(startDate),
        endDate: toIsoOrNull(endDate),
        environment: environment.trim() ? environment.trim() : null,
        triggerType: triggerType.trim() ? triggerType.trim() : null,
        sessionStatus: sessionStatus.trim() ? sessionStatus.trim() : null,
        executionStartDate: toIsoOrNull(executionStartDate),
        executionEndDate: toIsoOrNull(executionEndDate),
        createdBy: createdBy.trim() ? createdBy.trim() : null,
      }

      const testCaseIds = Array.from(selectedIds)

      if (mode === 'create') {
        const createPayload: TestCampaignCreateRequest = {
          projectId,
          ...basePayload,
        }

        const created = await createCampaign(createPayload)
        await attachCampaignTestCases(created.id, testCaseIds)

        toast({
          title: 'Campaign created',
          description: `Created ${created.name}`,
        })

        router.push(`/campaigns/${created.id}`)
        router.refresh()
        return
      }

      if (!campaignId) throw new Error('Missing campaign id')

      const updatePayload: TestCampaignUpdateRequest = {
        ...basePayload,
      }

      const updated = await updateCampaign(campaignId, updatePayload)
      await attachCampaignTestCases(campaignId, testCaseIds)

      toast({
        title: 'Campaign saved',
        description: `Updated ${updated.name}`,
      })

      router.push(`/campaigns/${campaignId}`)
      router.refresh()
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
              <Label htmlFor="campaignVersion">Version</Label>
              <Input
                id="campaignVersion"
                value={version}
                onChange={(e) => setVersion(e.target.value)}
                placeholder="v2.5"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="campaignStatus">Status</Label>
              <Select value={status} onValueChange={setStatus}>
                <SelectTrigger id="campaignStatus">
                  <SelectValue placeholder="Select status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="DRAFT">DRAFT</SelectItem>
                  <SelectItem value="SCHEDULED">SCHEDULED</SelectItem>
                  <SelectItem value="RUNNING">RUNNING</SelectItem>
                  <SelectItem value="COMPLETED">COMPLETED</SelectItem>
                  <SelectItem value="FAILED">FAILED</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="startDate">Start date</Label>
              <Input
                id="startDate"
                type="datetime-local"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="endDate">End date</Label>
              <Input
                id="endDate"
                type="datetime-local"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="environment">Environment</Label>
              <Select value={environment} onValueChange={setEnvironment}>
                <SelectTrigger id="environment">
                  <SelectValue placeholder="Select environment" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="QA">QA</SelectItem>
                  <SelectItem value="UAT">UAT</SelectItem>
                  <SelectItem value="STAGING">STAGING</SelectItem>
                  <SelectItem value="PREPROD">PREPROD</SelectItem>
                  <SelectItem value="PROD">PROD</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="triggerType">Trigger type</Label>
              <Select value={triggerType} onValueChange={setTriggerType}>
                <SelectTrigger id="triggerType">
                  <SelectValue placeholder="Select trigger type" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="MANUAL">MANUAL</SelectItem>
                  <SelectItem value="PIPELINE">PIPELINE</SelectItem>
                  <SelectItem value="SCHEDULED">SCHEDULED</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="sessionStatus">Session status</Label>
              <Select value={sessionStatus} onValueChange={setSessionStatus}>
                <SelectTrigger id="sessionStatus">
                  <SelectValue placeholder="Select session status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="OPEN">OPEN</SelectItem>
                  <SelectItem value="IN_PROGRESS">IN_PROGRESS</SelectItem>
                  <SelectItem value="CLOSED">CLOSED</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="executionStartDate">Execution start</Label>
              <Input
                id="executionStartDate"
                type="datetime-local"
                value={executionStartDate}
                onChange={(e) => setExecutionStartDate(e.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="executionEndDate">Execution end</Label>
              <Input
                id="executionEndDate"
                type="datetime-local"
                value={executionEndDate}
                onChange={(e) => setExecutionEndDate(e.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="createdBy">Created by</Label>
              <Input
                id="createdBy"
                value={createdBy}
                onChange={(e) => setCreatedBy(e.target.value)}
                placeholder="qa.platform"
              />
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
                <Dialog open={isCreateTestCaseOpen} onOpenChange={setIsCreateTestCaseOpen}>
                  <DialogTrigger asChild>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={testCasesLoading}
                    >
                      Add test case
                    </Button>
                  </DialogTrigger>
                  <DialogContent className="sm:max-w-md">
                    <DialogHeader>
                      <DialogTitle>New Test Case</DialogTitle>
                      <DialogDescription>
                        Create a test case.
                      </DialogDescription>
                    </DialogHeader>

                    <form className="space-y-5" onSubmit={onCreateTestCase}>
                      <div className="space-y-2">
                        <Label htmlFor="newTestCaseName">Name *</Label>
                        <Input
                          id="newTestCaseName"
                          value={newTestCaseName}
                          onChange={(e) => setNewTestCaseName(e.target.value)}
                          placeholder="Login - valid credentials"
                          required
                        />
                      </div>

                      <div className="space-y-2">
                        <Label htmlFor="newTestCaseDescription">Description</Label>
                        <Input
                          id="newTestCaseDescription"
                          value={newTestCaseDescription}
                          onChange={(e) => setNewTestCaseDescription(e.target.value)}
                          placeholder="Optional description"
                        />
                      </div>

                      <div className="space-y-2">
                        <Label>Test type</Label>
                        <Select value={newTestCaseType} onValueChange={setNewTestCaseType}>
                          <SelectTrigger>
                            <SelectValue placeholder="Select type" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="FUNCTIONAL">FUNCTIONAL</SelectItem>
                            <SelectItem value="PERFORMANCE">PERFORMANCE</SelectItem>
                            <SelectItem value="REGRESSION">REGRESSION</SelectItem>
                            <SelectItem value="SECURITY">SECURITY</SelectItem>
                            <SelectItem value="API">API</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>

                      <div className="space-y-2">
                        <Label>Priority</Label>
                        <Select value={newTestCasePriority} onValueChange={setNewTestCasePriority}>
                          <SelectTrigger>
                            <SelectValue placeholder="Select priority" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="LOW">LOW</SelectItem>
                            <SelectItem value="MEDIUM">MEDIUM</SelectItem>
                            <SelectItem value="HIGH">HIGH</SelectItem>
                            <SelectItem value="CRITICAL">CRITICAL</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>

                      <div className="space-y-2">
                        <Label htmlFor="newTestCaseTool">Tool</Label>
                        <Select
                          value={newTestCaseTool}
                          onValueChange={(v) => setNewTestCaseTool(v === '__none' ? '' : v)}
                        >
                          <SelectTrigger id="newTestCaseTool">
                            <SelectValue placeholder="Select tool" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="__none">None</SelectItem>
                            <SelectItem value="SELENIUM">SELENIUM</SelectItem>
                            <SelectItem value="PLAYWRIGHT">PLAYWRIGHT</SelectItem>
                            <SelectItem value="CYPRESS">CYPRESS</SelectItem>
                            <SelectItem value="POSTMAN">POSTMAN</SelectItem>
                            <SelectItem value="REST_ASSURED">REST_ASSURED</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>

                      <div className="space-y-2">
                        <Label htmlFor="newTestCaseRiskScore">Risk score</Label>
                        <Input
                          id="newTestCaseRiskScore"
                          inputMode="decimal"
                          value={newTestCaseRiskScore}
                          onChange={(e) => setNewTestCaseRiskScore(e.target.value)}
                          placeholder="0"
                        />
                      </div>

                      <DialogFooter className="gap-2 sm:gap-0">
                        <Button
                          type="button"
                          variant="outline"
                          onClick={() => {
                            setIsCreateTestCaseOpen(false)
                            resetNewTestCaseForm()
                          }}
                          disabled={isCreatingTestCase}
                        >
                          Cancel
                        </Button>
                        <Button type="submit" disabled={isCreatingTestCase || !newTestCaseName.trim()}>
                          {isCreatingTestCase ? 'Creating…' : 'Create test case'}
                        </Button>
                      </DialogFooter>
                    </form>
                  </DialogContent>
                </Dialog>

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
                placeholder="Search by name, tool, type, priority, id…"
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
                      <TableHead className="w-32">Type</TableHead>
                      <TableHead className="w-32">Priority</TableHead>
                      <TableHead className="w-40">Tool</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredTestCases.map((tc) => (
                      <TableRow key={tc.id}>
                        <TableCell>
                          <Checkbox
                            checked={selectedIds.has(tc.id)}
                            onCheckedChange={() => toggleOne(tc.id)}
                            aria-label={`Select test case ${tc.name}`}
                          />
                        </TableCell>
                        <TableCell className="font-medium">{tc.name}</TableCell>
                        <TableCell>{tc.testType ?? '—'}</TableCell>
                        <TableCell>{tc.priority ?? '—'}</TableCell>
                        <TableCell>{tc.tool ?? '—'}</TableCell>
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

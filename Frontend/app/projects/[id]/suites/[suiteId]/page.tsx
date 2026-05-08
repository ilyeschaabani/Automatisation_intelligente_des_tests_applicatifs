'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'
import { ChevronLeft, Plus, RefreshCw, Trash2, Wand2 } from 'lucide-react'

import { AuthGuard } from '@/components/auth-guard'
import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { ConfirmDialog } from '@/components/crud/ConfirmDialog'
import { FormDialog } from '@/components/crud/FormDialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { testCaseService } from '@/services/testCases'
import { testSuiteService } from '@/services/suites'
import type {
  CreateTestCaseRequest,
  RiskLevel,
  TestCase,
  TestSuite,
  TestType,
  UpdateTestCaseRequest,
} from '@/types/ms-gestion'

type LoadState = 'loading' | 'ready' | 'error'

type CaseFormState = {
  title: string
  description: string
  type: TestType
  priority: string
  riskLevel: RiskLevel | ''
  scriptPath: string
  testData: string
  tags: string
  maxDurationSeconds: string
}

const emptyCaseForm: CaseFormState = {
  title: '',
  description: '',
  type: 'WEB',
  priority: '',
  riskLevel: '',
  scriptPath: '',
  testData: '',
  tags: '',
  maxDurationSeconds: '',
}

const formatDate = (value?: string) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

export default function SuiteTestCasesPage() {
  const params = useParams<{ id?: string | string[]; suiteId?: string | string[] }>()
  const projectId = Number(Array.isArray(params?.id) ? params?.id[0] : params?.id)
  const suiteId = Number(
    Array.isArray(params?.suiteId) ? params?.suiteId[0] : params?.suiteId,
  )
  const hasIds = Number.isFinite(projectId) && Number.isFinite(suiteId)

  const [suite, setSuite] = useState<TestSuite | null>(null)
  const [suiteState, setSuiteState] = useState<LoadState>('loading')
  const [suiteError, setSuiteError] = useState<string | null>(null)

  const [cases, setCases] = useState<TestCase[]>([])
  const [caseState, setCaseState] = useState<LoadState>('loading')
  const [caseError, setCaseError] = useState<string | null>(null)

  const [formState, setFormState] = useState<CaseFormState>(emptyCaseForm)
  const [formError, setFormError] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editingCase, setEditingCase] = useState<TestCase | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletingCase, setDeletingCase] = useState<TestCase | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)

  const loadSuite = async () => {
    if (!hasIds) return
    setSuiteState('loading')
    setSuiteError(null)
    try {
      const data = await testSuiteService.getById(projectId, suiteId)
      setSuite(data)
      setSuiteState('ready')
    } catch (err) {
      setSuite(null)
      setSuiteState('error')
      setSuiteError(err instanceof Error ? err.message : 'Failed to load suite')
    }
  }

  const loadCases = async () => {
    if (!hasIds) return
    setCaseState('loading')
    setCaseError(null)
    try {
      const data = await testCaseService.getAll(suiteId)
      setCases(Array.isArray(data) ? data : [])
      setCaseState('ready')
    } catch (err) {
      setCases([])
      setCaseState('error')
      setCaseError(err instanceof Error ? err.message : 'Failed to load test cases')
    }
  }

  const loadAll = async () => {
    await Promise.all([loadSuite(), loadCases()])
  }

  useEffect(() => {
    if (!hasIds) return
    void loadAll()
  }, [hasIds])

  const resetForm = () => {
    setFormState(emptyCaseForm)
    setFormError(null)
  }

  const openCreate = () => {
    resetForm()
    setCreateOpen(true)
  }

  const openEdit = (testCase: TestCase) => {
    setEditingCase(testCase)
    setFormState({
      title: testCase.title ?? '',
      description: testCase.description ?? '',
      type: testCase.type,
      priority: testCase.priority != null ? String(testCase.priority) : '',
      riskLevel: testCase.riskLevel ?? '',
      scriptPath: testCase.scriptPath ?? '',
      testData: testCase.testData ?? '',
      tags: testCase.tags ?? '',
      maxDurationSeconds:
        testCase.maxDurationSeconds != null ? String(testCase.maxDurationSeconds) : '',
    })
    setEditOpen(true)
  }

  const openDelete = (testCase: TestCase) => {
    setDeletingCase(testCase)
    setDeleteOpen(true)
  }

  const buildPayload = (): CreateTestCaseRequest | UpdateTestCaseRequest | null => {
    const title = formState.title.trim()
    if (!title) {
      setFormError('Title is required.')
      return null
    }

    const priorityRaw = formState.priority.trim()
    const maxDurationRaw = formState.maxDurationSeconds.trim()
    const priority = priorityRaw ? Number(priorityRaw) : undefined
    const maxDurationSeconds = maxDurationRaw ? Number(maxDurationRaw) : undefined

    if (priorityRaw && !Number.isFinite(priority)) {
      setFormError('Priority must be a number.')
      return null
    }

    if (maxDurationRaw && !Number.isFinite(maxDurationSeconds)) {
      setFormError('Max duration must be a number.')
      return null
    }

    const payload: CreateTestCaseRequest = {
      title,
      description: formState.description.trim() || undefined,
      type: formState.type,
      priority,
      riskLevel: formState.riskLevel ? (formState.riskLevel as RiskLevel) : undefined,
      scriptPath: formState.scriptPath.trim() || undefined,
      testData: formState.testData.trim() || undefined,
      tags: formState.tags.trim() || undefined,
      maxDurationSeconds,
    }

    return payload
  }

  const submitCreate = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!hasIds) return
    setIsSubmitting(true)
    setFormError(null)

    const payload = buildPayload()
    if (!payload) {
      setIsSubmitting(false)
      return
    }

    try {
      await testCaseService.create(suiteId, payload)
      setCreateOpen(false)
      resetForm()
      await loadCases()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to create test case')
    } finally {
      setIsSubmitting(false)
    }
  }

  const submitEdit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!hasIds || !editingCase) return
    setIsSubmitting(true)
    setFormError(null)

    const payload = buildPayload()
    if (!payload) {
      setIsSubmitting(false)
      return
    }

    try {
      await testCaseService.update(suiteId, editingCase.id, payload)
      setEditOpen(false)
      setEditingCase(null)
      await loadCases()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to update test case')
    } finally {
      setIsSubmitting(false)
    }
  }

  const confirmDelete = async () => {
    if (!hasIds || !deletingCase) return
    setIsDeleting(true)
    try {
      await testCaseService.delete(suiteId, deletingCase.id)
      setDeleteOpen(false)
      setDeletingCase(null)
      await loadCases()
    } catch (err) {
      setCaseError(err instanceof Error ? err.message : 'Failed to delete test case')
    } finally {
      setIsDeleting(false)
    }
  }

  const suiteTitle = useMemo(() => suite?.name || 'Test suite', [suite])

  if (!hasIds) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">
        Invalid suite id.
      </div>
    )
  }

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />
        <AuthGuard>
          <div className="p-6 max-w-7xl space-y-6">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Link href="/projects" className="hover:underline">
                Projects
              </Link>
              <ChevronLeft size={12} />
              <Link href={`/projects/${projectId}`} className="hover:underline">
                Project {projectId}
              </Link>
            </div>

            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div>
                <h1 className="text-3xl font-bold text-foreground">{suiteTitle}</h1>
                <p className="text-muted-foreground mt-1">
                  Manage test cases for this suite.
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Button variant="outline" onClick={loadAll} className="gap-2">
                  <RefreshCw size={16} />
                  Refresh
                </Button>
                <Button onClick={openCreate} className="gap-2">
                  <Plus size={16} />
                  Add test case
                </Button>
              </div>
            </div>

            {suiteState === 'loading' ? (
              <Card className="p-6 text-sm text-muted-foreground">Loading suite...</Card>
            ) : null}
            {suiteState === 'error' ? (
              <Card className="p-6 text-sm text-destructive">{suiteError}</Card>
            ) : null}

            <Card className="p-6">
              <div className="flex items-center gap-2 mb-4">
                <Wand2 size={18} />
                <h2 className="text-lg font-semibold">Test cases</h2>
              </div>

              {caseError ? <p className="text-sm text-destructive mb-4">{caseError}</p> : null}
              {caseState === 'loading' ? (
                <p className="text-sm text-muted-foreground">Loading test cases...</p>
              ) : null}

              {caseState !== 'loading' && cases.length === 0 ? (
                <p className="text-sm text-muted-foreground">No test cases yet.</p>
              ) : null}

              {caseState !== 'loading' && cases.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Title</TableHead>
                      <TableHead>Type</TableHead>
                      <TableHead>Priority</TableHead>
                      <TableHead>Risk</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Created</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {cases.map((testCase) => (
                      <TableRow key={testCase.id}>
                        <TableCell className="font-medium">{testCase.title}</TableCell>
                        <TableCell>{testCase.type}</TableCell>
                        <TableCell>{testCase.priority ?? '—'}</TableCell>
                        <TableCell>{testCase.riskLevel ?? '—'}</TableCell>
                        <TableCell>
                          <div className="flex flex-wrap gap-2">
                            <Badge variant={testCase.active ? 'default' : 'secondary'}>
                              {testCase.active ? 'Active' : 'Inactive'}
                            </Badge>
                            {testCase.flaky ? <Badge variant="outline">Flaky</Badge> : null}
                          </div>
                        </TableCell>
                        <TableCell>{formatDate(testCase.createdAt)}</TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button size="sm" variant="outline" onClick={() => openEdit(testCase)}>
                              Edit
                            </Button>
                            <Button size="sm" variant="destructive" onClick={() => openDelete(testCase)}>
                              <Trash2 size={14} />
                              Delete
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : null}
            </Card>
          </div>
        </AuthGuard>
      </main>

      <FormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        title="Add test case"
        description="Define the test case details."
        submitLabel="Create test case"
        isSubmitting={isSubmitting}
        onSubmit={submitCreate}
      >
        <div className="space-y-2">
          <Label htmlFor="case-title">Title</Label>
          <Input
            id="case-title"
            value={formState.title}
            onChange={(event) => setFormState((prev) => ({ ...prev, title: event.target.value }))}
            placeholder="Login flow works"
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="case-description">Description</Label>
          <Textarea
            id="case-description"
            value={formState.description}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, description: event.target.value }))
            }
            placeholder="Optional description"
          />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label>Type</Label>
            <Select
              value={formState.type}
              onValueChange={(value) => setFormState((prev) => ({ ...prev, type: value as TestType }))}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="WEB">WEB</SelectItem>
                <SelectItem value="API">API</SelectItem>
                <SelectItem value="UNIT">UNIT</SelectItem>
                <SelectItem value="INTEGRATION">INTEGRATION</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>Risk level</Label>
            <Select
              value={formState.riskLevel || 'UNSET'}
              onValueChange={(value) =>
                setFormState((prev) => ({
                  ...prev,
                  riskLevel: value === 'UNSET' ? '' : (value as RiskLevel),
                }))
              }
            >
              <SelectTrigger>
                <SelectValue placeholder="Select risk" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="UNSET">None</SelectItem>
                <SelectItem value="CRITICAL">CRITICAL</SelectItem>
                <SelectItem value="HIGH">HIGH</SelectItem>
                <SelectItem value="MEDIUM">MEDIUM</SelectItem>
                <SelectItem value="LOW">LOW</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="case-priority">Priority</Label>
            <Input
              id="case-priority"
              value={formState.priority}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, priority: event.target.value }))
              }
              placeholder="1"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="case-duration">Max duration (seconds)</Label>
            <Input
              id="case-duration"
              value={formState.maxDurationSeconds}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, maxDurationSeconds: event.target.value }))
              }
              placeholder="120"
            />
          </div>
        </div>
        <div className="space-y-2">
          <Label htmlFor="case-script">Script path</Label>
          <Input
            id="case-script"
            value={formState.scriptPath}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, scriptPath: event.target.value }))
            }
            placeholder="tests/login.spec.ts"
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="case-tags">Tags</Label>
          <Input
            id="case-tags"
            value={formState.tags}
            onChange={(event) => setFormState((prev) => ({ ...prev, tags: event.target.value }))}
            placeholder="smoke,auth"
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="case-data">Test data (JSON)</Label>
          <Textarea
            id="case-data"
            value={formState.testData}
            onChange={(event) => setFormState((prev) => ({ ...prev, testData: event.target.value }))}
            placeholder='{"email": "demo@bank.com"}'
          />
        </div>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <FormDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        title="Edit test case"
        description="Update the test case metadata."
        submitLabel="Save changes"
        isSubmitting={isSubmitting}
        onSubmit={submitEdit}
      >
        <div className="space-y-2">
          <Label htmlFor="case-edit-title">Title</Label>
          <Input
            id="case-edit-title"
            value={formState.title}
            onChange={(event) => setFormState((prev) => ({ ...prev, title: event.target.value }))}
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="case-edit-description">Description</Label>
          <Textarea
            id="case-edit-description"
            value={formState.description}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, description: event.target.value }))
            }
          />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label>Type</Label>
            <Select
              value={formState.type}
              onValueChange={(value) => setFormState((prev) => ({ ...prev, type: value as TestType }))}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="WEB">WEB</SelectItem>
                <SelectItem value="API">API</SelectItem>
                <SelectItem value="UNIT">UNIT</SelectItem>
                <SelectItem value="INTEGRATION">INTEGRATION</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>Risk level</Label>
            <Select
              value={formState.riskLevel || 'UNSET'}
              onValueChange={(value) =>
                setFormState((prev) => ({
                  ...prev,
                  riskLevel: value === 'UNSET' ? '' : (value as RiskLevel),
                }))
              }
            >
              <SelectTrigger>
                <SelectValue placeholder="Select risk" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="UNSET">None</SelectItem>
                <SelectItem value="CRITICAL">CRITICAL</SelectItem>
                <SelectItem value="HIGH">HIGH</SelectItem>
                <SelectItem value="MEDIUM">MEDIUM</SelectItem>
                <SelectItem value="LOW">LOW</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="case-edit-priority">Priority</Label>
            <Input
              id="case-edit-priority"
              value={formState.priority}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, priority: event.target.value }))
              }
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="case-edit-duration">Max duration (seconds)</Label>
            <Input
              id="case-edit-duration"
              value={formState.maxDurationSeconds}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, maxDurationSeconds: event.target.value }))
              }
            />
          </div>
        </div>
        <div className="space-y-2">
          <Label htmlFor="case-edit-script">Script path</Label>
          <Input
            id="case-edit-script"
            value={formState.scriptPath}
            onChange={(event) =>
              setFormState((prev) => ({ ...prev, scriptPath: event.target.value }))
            }
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="case-edit-tags">Tags</Label>
          <Input
            id="case-edit-tags"
            value={formState.tags}
            onChange={(event) => setFormState((prev) => ({ ...prev, tags: event.target.value }))}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="case-edit-data">Test data (JSON)</Label>
          <Textarea
            id="case-edit-data"
            value={formState.testData}
            onChange={(event) => setFormState((prev) => ({ ...prev, testData: event.target.value }))}
          />
        </div>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete test case"
        description={deletingCase ? `Delete ${deletingCase.title}?` : 'Delete test case?'}
        confirmLabel="Delete"
        isConfirming={isDeleting}
        onConfirm={confirmDelete}
      />
    </div>
  )
}

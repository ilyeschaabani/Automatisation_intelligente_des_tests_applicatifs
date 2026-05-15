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
import { llmService } from '@/services/llm'
import { testSuiteService } from '@/services/suites'
import { projectService } from '@/services/projects'
import type {
  CreateTestCaseRequest,
  RiskLevel,
  TestCase,
  TestSuite,
  TestType,
  UpdateTestCaseRequest,
  Project,
} from '@/types/ms-gestion'

type LoadState = 'loading' | 'ready' | 'error'

type CaseFormState = {
  title: string
  description: string
  type: TestType
  springProfile: string
  databaseType: string
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
  springProfile: '',
  databaseType: '',
  priority: '',
  riskLevel: '',
  scriptPath: '',
  testData: '',
  tags: '',
  maxDurationSeconds: '',
}

// Additional UI state for AI generation
type AICaseState = {
  descriptionAI: string
  generatedCode: string
  codeValidated: boolean
}

const emptyAICase: AICaseState = {
  descriptionAI: '',
  generatedCode: '',
  codeValidated: false,
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
  const [project, setProject] = useState<Project | null>(null)
  const [suiteState, setSuiteState] = useState<LoadState>('loading')
  const [suiteError, setSuiteError] = useState<string | null>(null)

  const [cases, setCases] = useState<TestCase[]>([])
  const [caseState, setCaseState] = useState<LoadState>('loading')
  const [caseError, setCaseError] = useState<string | null>(null)

  const [formState, setFormState] = useState<CaseFormState>(emptyCaseForm)
  const [formError, setFormError] = useState<string | null>(null)
  const [aiState, setAiState] = useState<AICaseState>(emptyAICase)
  const [generating, setGenerating] = useState(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editingCase, setEditingCase] = useState<TestCase | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletingCase, setDeletingCase] = useState<TestCase | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)

  const forceAiForSuite = suite?.type === 'UNIT' || suite?.type === 'INTEGRATION'
  const showAiSection = Boolean(project?.aiProject) || forceAiForSuite

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

  const loadProject = async () => {
    if (!hasIds) return
    try {
      const p = await projectService.getById(projectId)
      setProject(p)
    } catch (e) {
      setProject(null)
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
    await Promise.all([loadSuite(), loadCases(), loadProject()])
  }

  useEffect(() => {
    if (!hasIds) return
    void loadAll()
  }, [hasIds])

  const resetForm = () => {
    setFormState({
      ...emptyCaseForm,
      type: suite?.type ?? emptyCaseForm.type,
    })
    setFormError(null)
    setAiState(emptyAICase)
  }

  const openCreate = async () => {
    resetForm()
    // Ensure we have project info before opening so UI can show AI section when needed
    try {
      if (!project) {
        const p = await projectService.getById(projectId)
        setProject(p)
      }
    } catch (e) {
      // ignore
    }
    setAiState(emptyAICase)
    setCreateOpen(true)
  }

  const generateScript = async () => {
    setFormError(null)
    const suiteType = suite?.type
    if (!suiteType) {
      setFormError('Suite type is not loaded yet. Please retry.')
      return
    }
    if (forceAiForSuite && !aiState.descriptionAI.trim()) {
      setFormError('AI description is required for UNIT/INTEGRATION suites.')
      return
    }
    setGenerating(true)
    try {
      const payload = { type: suiteType, description: aiState.descriptionAI || formState.description }
      const resp = await llmService.generateTest(payload)
      setAiState((prev) => ({ ...prev, generatedCode: resp.generatedCode, codeValidated: false }))
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to generate script')
    } finally {
      setGenerating(false)
    }
  }

  const validateScript = () => {
    setAiState((prev) => ({ ...prev, codeValidated: true }))
  }

  const openEdit = (testCase: TestCase) => {
    setEditingCase(testCase)
    setFormState({
      title: testCase.title ?? '',
      description: testCase.description ?? '',
      type: suite?.type ?? testCase.type,
      springProfile: testCase.springProfile ?? '',
      databaseType: (testCase as any).databaseType ?? '',
      priority: testCase.priority != null ? String(testCase.priority) : '',
      riskLevel: testCase.riskLevel ?? '',
      scriptPath: testCase.scriptPath ?? '',
      testData: testCase.testData ?? '',
      tags: testCase.tags ?? '',
      maxDurationSeconds:
        testCase.maxDurationSeconds != null ? String(testCase.maxDurationSeconds) : '',
    })
    setEditOpen(true)
    // if this case was generated, prefill AI state
    setAiState({
      // best-effort prefill: we don't persist descriptionAI server-side yet
      descriptionAI: String((testCase as any).descriptionAI ?? testCase.description ?? ''),
      generatedCode: String((testCase as any).generatedCode ?? ''),
      codeValidated: Boolean((testCase as any).generated),
    })
  }

  const openDelete = (testCase: TestCase) => {
    setDeletingCase(testCase)
    setDeleteOpen(true)
  }

  const buildPayload = (): CreateTestCaseRequest | UpdateTestCaseRequest | null => {
    const suiteType = suite?.type
    if (!suiteType) {
      setFormError('Suite type is not loaded yet. Please retry.')
      return null
    }

    const isIntegration = suiteType === 'INTEGRATION'
    const showTestData = suiteType !== 'UNIT'
    const requireScriptPathInManual = suiteType === 'WEB' || suiteType === 'API'

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
      type: suiteType,
      springProfile: isIntegration ? (formState.springProfile.trim() || undefined) : undefined,
      databaseType: isIntegration ? (formState.databaseType.trim() || undefined) : undefined,
      priority,
      riskLevel: formState.riskLevel ? (formState.riskLevel as RiskLevel) : undefined,
      scriptPath: formState.scriptPath.trim() || undefined,
      testData: showTestData ? (formState.testData.trim() || undefined) : undefined,
      tags: formState.tags.trim() || undefined,
      maxDurationSeconds,
    }

    if (!isIntegration) {
      delete (payload as any).springProfile
    }
    if (!showTestData) {
      delete (payload as any).testData
    }

    if (isIntegration) {
      const dbt = formState.databaseType.trim()
      if (!dbt) {
        setFormError('databaseType is required for INTEGRATION suites')
        return null
      }
      // ensure uppercase normalized value
      payload.databaseType = dbt.toUpperCase()
    }

    // Three cases for code handling:
    // 1. If generatedCode exists (and validated) -> send it
    // 2. If project.aiProject with description -> backend regenerates
    // 3. Otherwise -> manual mode with scriptPath
    if (forceAiForSuite) {
      // Forced AI for UNIT/INTEGRATION suites:
      // - If user has code in the editor, persist it (allows post-generation edits).
      // - Otherwise, require descriptionAI and let backend generate.
      if (aiState.generatedCode && aiState.generatedCode.trim()) {
        if (!aiState.codeValidated) {
          setFormError('You must validate the generated script before saving.')
          return null
        }
        payload.generatedCode = aiState.generatedCode
        payload.useAI = false
        delete (payload as any).scriptPath
      } else {
        const prompt = aiState.descriptionAI.trim()
        if (!prompt) {
          setFormError('AI description is required for UNIT/INTEGRATION suites.')
          return null
        }
        payload.useAI = true
        payload.descriptionAI = prompt
        delete (payload as any).scriptPath
        delete (payload as any).generatedCode
      }
    } else if (aiState.generatedCode && aiState.generatedCode.trim()) {
      // Case 1: User has generated/edited code and validated it
      if (!aiState.codeValidated) {
        setFormError('You must validate the generated script before saving.')
        return null
      }
      payload.generatedCode = aiState.generatedCode
      payload.useAI = false
      delete (payload as any).scriptPath
    } else if (project?.aiProject) {
      // Case 2: AI project: backend generates (descriptionAI preferred, fallback to description)
      const prompt = (aiState.descriptionAI || formState.description).trim()
      if (!prompt) {
        setFormError('Description is required for AI projects.')
        return null
      }
      payload.useAI = true
      payload.descriptionAI = prompt
      delete (payload as any).scriptPath
    } else {
      // Case 3: Manual mode
      payload.useAI = false
      if (requireScriptPathInManual && (!payload.scriptPath || !String(payload.scriptPath).trim())) {
        setFormError('Script path is required for WEB/API test cases in manual mode.')
        return null
      }
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
        disableSubmit={
          (Boolean(aiState.generatedCode && aiState.generatedCode.trim()) && !aiState.codeValidated) ||
          (suite?.type === 'INTEGRATION' && !formState.databaseType)
        }
        size="xl"
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
        {showAiSection ? (
          <>
            <div className="space-y-2">
              <Label htmlFor="case-description-ai">AI description <span className="text-destructive">*</span></Label>
              <Textarea
                id="case-description-ai"
                value={aiState.descriptionAI}
                onChange={(e) => setAiState((prev) => ({ ...prev, descriptionAI: e.target.value }))}
                placeholder="Describe what the test should do in natural language"
                required={forceAiForSuite || Boolean(project?.aiProject)}
              />
            </div>
            <div className="flex gap-2">
              <Button type="button" variant="outline" onClick={generateScript} disabled={generating}>
                {generating ? 'Generating…' : 'Generate script'}
              </Button>
              {aiState.generatedCode ? (
                <Button type="button" onClick={() => setAiState((prev) => ({ ...prev, generatedCode: '', codeValidated: false }))} variant="ghost">
                  Regenerate
                </Button>
              ) : null}
            </div>
          </>
        ) : null}

        {aiState.generatedCode ? (
          <div className="space-y-3 rounded-lg border border-border bg-muted/40 p-4">
            <div className="flex items-center justify-between">
              <Label className="text-base font-semibold">Generated code (editable)</Label>
              <span className="text-xs text-muted-foreground">Click to edit</span>
            </div>
            <Textarea
              className="font-mono text-sm bg-background resize-none focus:ring-2 focus:ring-primary/50"
              value={aiState.generatedCode}
              onChange={(e) => setAiState((prev) => ({ ...prev, generatedCode: e.target.value, codeValidated: false }))}
              rows={20}
            />
            <div className="flex gap-2 pt-2">
              <Button type="button" onClick={validateScript} disabled={aiState.codeValidated}>
                {aiState.codeValidated ? '✓ Validated' : 'Validate script'}
              </Button>
              {aiState.codeValidated && (
                <span className="text-xs text-green-600 flex items-center gap-1">
                  ✓ Code validated and ready to save
                </span>
              )}
            </div>
          </div>
        ) : null}
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
        {!showAiSection ? (
          <div className="space-y-2">
            <Label htmlFor="case-script">Script path</Label>
            <Input
              id="case-script"
              value={formState.scriptPath}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, scriptPath: event.target.value }))
              }
              placeholder="tests/login.spec.ts"
              required={suite?.type === 'WEB' || suite?.type === 'API'}
            />
          </div>
        ) : null}

        {suite?.type === 'INTEGRATION' ? (
          <div className="space-y-2">
            <Label htmlFor="case-spring-profile">Spring profile</Label>
            <Input
              id="case-spring-profile"
              value={formState.springProfile}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, springProfile: event.target.value }))
              }
              placeholder="test"
            />
          </div>
        ) : null}
        {suite?.type === 'INTEGRATION' ? (
          <div className="space-y-2">
            <Label htmlFor="case-database-type">Database type</Label>
            <Select
              value={formState.databaseType || 'UNSET'}
              onValueChange={(value) => setFormState((prev) => ({ ...prev, databaseType: value }))}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select database" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="POSTGRESQL">POSTGRESQL</SelectItem>
                <SelectItem value="MYSQL">MYSQL</SelectItem>
                <SelectItem value="H2">H2</SelectItem>
                <SelectItem value="MONGODB">MONGODB</SelectItem>
              </SelectContent>
            </Select>
          </div>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor="case-tags">Tags</Label>
          <Input
            id="case-tags"
            value={formState.tags}
            onChange={(event) => setFormState((prev) => ({ ...prev, tags: event.target.value }))}
            placeholder="smoke,auth"
          />
        </div>
        {suite?.type !== 'UNIT' ? (
          <div className="space-y-2">
            <Label htmlFor="case-data">Test data (JSON)</Label>
            <Textarea
              id="case-data"
              value={formState.testData}
              onChange={(event) => setFormState((prev) => ({ ...prev, testData: event.target.value }))}
              placeholder='{"email": "demo@bank.com"}'
            />
          </div>
        ) : null}
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      </FormDialog>

      <FormDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        title="Edit test case"
        description="Update the test case metadata."
        submitLabel="Save changes"
        isSubmitting={isSubmitting}
        disableSubmit={
          (Boolean(aiState.generatedCode && aiState.generatedCode.trim()) && !aiState.codeValidated) ||
          (suite?.type === 'INTEGRATION' && !formState.databaseType)
        }
        size="xl"
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
        {showAiSection ? (
          <>
            <div className="space-y-2">
              <Label>AI description{forceAiForSuite ? ' *' : ''}</Label>
              <Textarea
                value={aiState.descriptionAI}
                onChange={(e) => setAiState((prev) => ({ ...prev, descriptionAI: e.target.value }))}
                placeholder="Describe what the test should do in natural language"
                required={forceAiForSuite}
              />
            </div>
            <div className="flex gap-2 mb-2">
              <Button type="button" variant="outline" onClick={generateScript} disabled={generating}>
                {generating ? 'Generating…' : 'Generate script'}
              </Button>
              {aiState.generatedCode ? (
                <Button type="button" onClick={() => setAiState((prev) => ({ ...prev, generatedCode: '', codeValidated: false }))} variant="ghost">
                  Regenerate
                </Button>
              ) : null}
            </div>
          </>
        ) : null}

        {aiState.generatedCode ? (
          <div className="space-y-3 rounded-lg border border-border bg-muted/40 p-4">
            <div className="flex items-center justify-between">
              <Label className="text-base font-semibold">Generated code (editable)</Label>
              <span className="text-xs text-muted-foreground">Click to edit</span>
            </div>
            <Textarea
              className="font-mono text-sm bg-background resize-none focus:ring-2 focus:ring-primary/50"
              value={aiState.generatedCode}
              onChange={(e) => setAiState((prev) => ({ ...prev, generatedCode: e.target.value, codeValidated: false }))}
              rows={20}
            />
            <div className="flex gap-2 pt-2">
              <Button type="button" onClick={validateScript} disabled={aiState.codeValidated}>
                {aiState.codeValidated ? '✓ Validated' : 'Validate script'}
              </Button>
              {aiState.codeValidated && (
                <span className="text-xs text-green-600 flex items-center gap-1">
                  ✓ Code validated and ready to save
                </span>
              )}
            </div>
          </div>
        ) : null}
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
        {!showAiSection ? (
          <div className="space-y-2">
            <Label htmlFor="case-edit-script">Script path</Label>
            <Input
              id="case-edit-script"
              value={formState.scriptPath}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, scriptPath: event.target.value }))
              }
              required={suite?.type === 'WEB' || suite?.type === 'API'}
            />
          </div>
        ) : null}

        {suite?.type === 'INTEGRATION' ? (
          <div className="space-y-2">
            <Label htmlFor="case-edit-spring-profile">Spring profile</Label>
            <Input
              id="case-edit-spring-profile"
              value={formState.springProfile}
              onChange={(event) =>
                setFormState((prev) => ({ ...prev, springProfile: event.target.value }))
              }
              placeholder="test"
            />
          </div>
        ) : null}
        {suite?.type === 'INTEGRATION' ? (
          <div className="space-y-2">
            <Label htmlFor="case-edit-database-type">Database type</Label>
            <Select
              value={formState.databaseType || 'UNSET'}
              onValueChange={(value) => setFormState((prev) => ({ ...prev, databaseType: value }))}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select database" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="POSTGRESQL">POSTGRESQL</SelectItem>
                <SelectItem value="MYSQL">MYSQL</SelectItem>
                <SelectItem value="H2">H2</SelectItem>
                <SelectItem value="MONGODB">MONGODB</SelectItem>
              </SelectContent>
            </Select>
          </div>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor="case-edit-tags">Tags</Label>
          <Input
            id="case-edit-tags"
            value={formState.tags}
            onChange={(event) => setFormState((prev) => ({ ...prev, tags: event.target.value }))}
          />
        </div>
        {suite?.type !== 'UNIT' ? (
          <div className="space-y-2">
            <Label htmlFor="case-edit-data">Test data (JSON)</Label>
            <Textarea
              id="case-edit-data"
              value={formState.testData}
              onChange={(event) => setFormState((prev) => ({ ...prev, testData: event.target.value }))}
            />
          </div>
        ) : null}
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

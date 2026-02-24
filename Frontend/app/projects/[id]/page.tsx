'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'

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
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
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
  createSession,
  deleteSession,
  getSession,
  getProjects,
  listSessions,
  updateSession,
  type Project,
  type SessionStatus,
  type TestSession,
} from '@/lib/api-client'

const statusStyle: Record<'Deployed' | 'Not deployed', string> = {
  Deployed: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  'Not deployed': 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
}

const sessionStatusStyle: Record<SessionStatus, string> = {
  OPEN: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-950 dark:text-yellow-400',
  CLOSED: 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

function toDateTimeLocalValue(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

function isoToLocalInputValue(iso: string | null): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return toDateTimeLocalValue(date)
}

function localInputValueToIso(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) throw new Error('Invalid date')
  return date.toISOString()
}

export default function ProjectDetailsPage({
}: {}) {
  const params = useParams<{ id?: string | string[] }>()
  const id = Array.isArray(params?.id) ? params?.id[0] : params?.id

  const [project, setProject] = useState<Project | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const [sessions, setSessions] = useState<TestSession[]>([])
  const [sessionsLoading, setSessionsLoading] = useState(false)
  const [sessionsError, setSessionsError] = useState<string | null>(null)

  const [isCreateSessionOpen, setIsCreateSessionOpen] = useState(false)
  const [isCreatingSession, setIsCreatingSession] = useState(false)
  const [createStartDate, setCreateStartDate] = useState(() => toDateTimeLocalValue(new Date()))
  const [createEndDate, setCreateEndDate] = useState('')
  const [createEnvironment, setCreateEnvironment] = useState('')
  const [createStatus, setCreateStatus] = useState<SessionStatus>('OPEN')
  const [createTriggerType, setCreateTriggerType] = useState('')

  const [isEditSessionOpen, setIsEditSessionOpen] = useState(false)
  const [editLoading, setEditLoading] = useState(false)
  const [isUpdatingSession, setIsUpdatingSession] = useState(false)
  const [editingSession, setEditingSession] = useState<TestSession | null>(null)
  const [editStartDate, setEditStartDate] = useState('')
  const [editEndDate, setEditEndDate] = useState('')
  const [editEnvironment, setEditEnvironment] = useState('')
  const [editStatus, setEditStatus] = useState<SessionStatus>('OPEN')
  const [editTriggerType, setEditTriggerType] = useState('')

  const [isDeletingSession, setIsDeletingSession] = useState(false)
  const [deletingSession, setDeletingSession] = useState<TestSession | null>(null)

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

  const loadSessions = async (projectId: number) => {
    setSessionsLoading(true)
    setSessionsError(null)
    try {
      const data = await listSessions({ projectId })
      setSessions(Array.isArray(data) ? data : [])
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to load sessions'
      console.error('Failed to load sessions', error)
      setSessions([])
      setSessionsError(message)
    } finally {
      setSessionsLoading(false)
    }
  }

  useEffect(() => {
    if (!project?.id) return
    void loadSessions(project.id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [project?.id])

  const resetSessionForm = () => {
    setCreateStartDate(toDateTimeLocalValue(new Date()))
    setCreateEndDate('')
    setCreateEnvironment('')
    setCreateStatus('OPEN')
    setCreateTriggerType('')
    setSessionsError(null)
  }

  const onCreateSession = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!project) return

    setIsCreatingSession(true)
    setSessionsError(null)
    try {
      await createSession(
        {
          startDate: localInputValueToIso(createStartDate),
          endDate: createEndDate.trim()
            ? localInputValueToIso(createEndDate)
            : null,
          environment: createEnvironment.trim() ? createEnvironment.trim() : null,
          status: createStatus,
          triggerType: createTriggerType.trim() ? createTriggerType.trim() : null,
        },
        project.id,
      )
      setIsCreateSessionOpen(false)
      resetSessionForm()
      await loadSessions(project.id)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to create session'
      console.error('Failed to create session', error)
      setSessionsError(message)
    } finally {
      setIsCreatingSession(false)
    }
  }

  const openEditSession = async (session: TestSession) => {
    setIsEditSessionOpen(true)
    setEditingSession(null)
    setEditLoading(true)
    setSessionsError(null)

    try {
      const fresh = await getSession(session.id)
      setEditingSession(fresh)
      setEditStartDate(isoToLocalInputValue(fresh.startDate))
      setEditEndDate(isoToLocalInputValue(fresh.endDate))
      setEditEnvironment(fresh.environment ?? '')
      setEditStatus(fresh.status)
      setEditTriggerType(fresh.triggerType ?? '')
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to load session'
      console.error('Failed to load session', error)
      setSessionsError(message)
      setIsEditSessionOpen(false)
    } finally {
      setEditLoading(false)
    }
  }

  const onUpdateSession = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!project || !editingSession) return

    setIsUpdatingSession(true)
    setSessionsError(null)
    try {
      await updateSession(editingSession.id, {
        startDate: localInputValueToIso(editStartDate),
        endDate: editEndDate.trim() ? localInputValueToIso(editEndDate) : null,
        environment: editEnvironment.trim() ? editEnvironment.trim() : null,
        status: editStatus,
        triggerType: editTriggerType.trim() ? editTriggerType.trim() : null,
      })

      setIsEditSessionOpen(false)
      setEditingSession(null)
      await loadSessions(project.id)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to update session'
      console.error('Failed to update session', error)
      setSessionsError(message)
    } finally {
      setIsUpdatingSession(false)
    }
  }

  const onDeleteSession = async () => {
    if (!project || !deletingSession) return

    setIsDeletingSession(true)
    setSessionsError(null)
    try {
      await deleteSession(deletingSession.id)
      setDeletingSession(null)
      await loadSessions(project.id)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to delete session'
      console.error('Failed to delete session', error)
      setSessionsError(message)
    } finally {
      setIsDeletingSession(false)
    }
  }

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
                    <CardTitle>Sessions</CardTitle>
                    <CardDescription>
                      Create and track test sessions for this project
                    </CardDescription>
                  </div>

                  <Dialog open={isCreateSessionOpen} onOpenChange={setIsCreateSessionOpen}>
                    <DialogTrigger asChild>
                      <Button variant="outline" disabled={!project}>
                        New session
                      </Button>
                    </DialogTrigger>
                    <DialogContent className="sm:max-w-md">
                      <DialogHeader>
                        <DialogTitle>New Session</DialogTitle>
                        <DialogDescription>
                          Start a session for executions and results tracking
                        </DialogDescription>
                      </DialogHeader>

                      <form className="space-y-5" onSubmit={onCreateSession}>
                        <div className="space-y-2">
                          <Label htmlFor="sessionStart">Start date</Label>
                          <Input
                            id="sessionStart"
                            type="datetime-local"
                            value={createStartDate}
                            onChange={(e) => setCreateStartDate(e.target.value)}
                            required
                          />
                        </div>

                        <div className="space-y-2">
                          <Label htmlFor="sessionEnd">End date</Label>
                          <Input
                            id="sessionEnd"
                            type="datetime-local"
                            value={createEndDate}
                            onChange={(e) => setCreateEndDate(e.target.value)}
                            placeholder="(optional)"
                          />
                          <p className="text-xs text-muted-foreground">Leave blank for ongoing sessions.</p>
                        </div>

                        <div className="space-y-2">
                          <Label htmlFor="sessionEnv">Environment</Label>
                          <Input
                            id="sessionEnv"
                            value={createEnvironment}
                            onChange={(e) => setCreateEnvironment(e.target.value)}
                            placeholder="QA / UAT / PROD (optional)"
                          />
                        </div>

                        <div className="space-y-2">
                          <Label>Status</Label>
                          <Select value={createStatus} onValueChange={(v) => setCreateStatus(v as SessionStatus)}>
                            <SelectTrigger>
                              <SelectValue placeholder="Select status" />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="OPEN">OPEN</SelectItem>
                              <SelectItem value="IN_PROGRESS">IN_PROGRESS</SelectItem>
                              <SelectItem value="CLOSED">CLOSED</SelectItem>
                            </SelectContent>
                          </Select>
                        </div>

                        <div className="space-y-2">
                          <Label htmlFor="sessionTrigger">Trigger type</Label>
                          <Input
                            id="sessionTrigger"
                            value={createTriggerType}
                            onChange={(e) => setCreateTriggerType(e.target.value)}
                            placeholder="MANUAL / PIPELINE (optional)"
                          />
                        </div>

                        {sessionsError ? (
                          <p className="text-sm text-destructive">{sessionsError}</p>
                        ) : null}

                        <DialogFooter className="gap-2 sm:gap-0">
                          <Button
                            type="button"
                            variant="outline"
                            onClick={() => {
                              setIsCreateSessionOpen(false)
                              resetSessionForm()
                            }}
                          >
                            Cancel
                          </Button>
                          <Button type="submit" disabled={isCreatingSession}>
                            {isCreatingSession ? 'Creating…' : 'Create session'}
                          </Button>
                        </DialogFooter>
                      </form>
                    </DialogContent>
                  </Dialog>
                </div>
              </CardHeader>

              <Separator />

              <CardContent className="pt-6">
                {sessionsLoading ? (
                  <p className="text-sm text-muted-foreground">Loading sessions…</p>
                ) : sessions.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No sessions yet.</p>
                ) : (
                  <div className="space-y-3">
                    {sessions.map((session) => (
                      <div
                        key={session.id}
                        className="flex items-center justify-between gap-4 rounded-lg border border-border p-4"
                      >
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <p className="font-semibold text-foreground">#{session.id}</p>
                            <Badge
                              variant="outline"
                              className={sessionStatusStyle[session.status]}
                            >
                              {session.status}
                            </Badge>
                          </div>
                          <p className="text-sm text-muted-foreground mt-1">
                            {(session.environment ?? '—')} • {formatDate(session.startDate)}
                            {session.endDate ? ` → ${formatDate(session.endDate)}` : ''}
                            {session.triggerType ? ` • ${session.triggerType}` : ''}
                          </p>
                        </div>

                        <div className="flex items-center gap-2">
                          <Button asChild variant="outline" size="sm">
                            <Link href={`/executions?sessionId=${session.id}`}>
                              View executions
                            </Link>
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => void openEditSession(session)}
                          >
                            Edit
                          </Button>

                          <AlertDialog>
                            <AlertDialogTrigger asChild>
                              <Button
                                variant="destructive"
                                size="sm"
                                onClick={() => setDeletingSession(session)}
                              >
                                Delete
                              </Button>
                            </AlertDialogTrigger>
                            <AlertDialogContent>
                              <AlertDialogHeader>
                                <AlertDialogTitle>Delete session?</AlertDialogTitle>
                                <AlertDialogDescription>
                                  This will permanently delete session #{session.id}.
                                </AlertDialogDescription>
                              </AlertDialogHeader>
                              <AlertDialogFooter>
                                <AlertDialogCancel
                                  onClick={() => setDeletingSession(null)}
                                  disabled={isDeletingSession}
                                >
                                  Cancel
                                </AlertDialogCancel>
                                <AlertDialogAction
                                  onClick={() => void onDeleteSession()}
                                  disabled={isDeletingSession || deletingSession?.id !== session.id}
                                >
                                  {isDeletingSession && deletingSession?.id === session.id ? 'Deleting…' : 'Delete'}
                                </AlertDialogAction>
                              </AlertDialogFooter>
                            </AlertDialogContent>
                          </AlertDialog>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {sessionsError && !sessionsLoading ? (
                  <p className="text-sm text-destructive mt-4">{sessionsError}</p>
                ) : null}
              </CardContent>
            </Card>
          </div>
        </div>
      </main>

      <Dialog
        open={isEditSessionOpen}
        onOpenChange={(open) => {
          setIsEditSessionOpen(open)
          if (!open) setEditingSession(null)
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Edit Session</DialogTitle>
            <DialogDescription>
              Update session fields (status must be uppercase)
            </DialogDescription>
          </DialogHeader>

          {editLoading ? (
            <p className="text-sm text-muted-foreground">Loading session…</p>
          ) : !editingSession ? (
            <p className="text-sm text-muted-foreground">No session selected.</p>
          ) : (
            <form className="space-y-5" onSubmit={onUpdateSession}>
              <div className="space-y-2">
                <Label htmlFor="editSessionStart">Start date</Label>
                <Input
                  id="editSessionStart"
                  type="datetime-local"
                  value={editStartDate}
                  onChange={(e) => setEditStartDate(e.target.value)}
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="editSessionEnd">End date</Label>
                <Input
                  id="editSessionEnd"
                  type="datetime-local"
                  value={editEndDate}
                  onChange={(e) => setEditEndDate(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="editSessionEnv">Environment</Label>
                <Input
                  id="editSessionEnv"
                  value={editEnvironment}
                  onChange={(e) => setEditEnvironment(e.target.value)}
                  placeholder="QA / UAT / PROD (optional)"
                />
              </div>

              <div className="space-y-2">
                <Label>Status</Label>
                <Select value={editStatus} onValueChange={(v) => setEditStatus(v as SessionStatus)}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select status" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="OPEN">OPEN</SelectItem>
                    <SelectItem value="IN_PROGRESS">IN_PROGRESS</SelectItem>
                    <SelectItem value="CLOSED">CLOSED</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="editSessionTrigger">Trigger type</Label>
                <Input
                  id="editSessionTrigger"
                  value={editTriggerType}
                  onChange={(e) => setEditTriggerType(e.target.value)}
                  placeholder="MANUAL / PIPELINE (optional)"
                />
              </div>

              {sessionsError ? (
                <p className="text-sm text-destructive">{sessionsError}</p>
              ) : null}

              <DialogFooter className="gap-2 sm:gap-0">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setIsEditSessionOpen(false)
                    setEditingSession(null)
                  }}
                  disabled={isUpdatingSession}
                >
                  Cancel
                </Button>
                <Button type="submit" disabled={isUpdatingSession}>
                  {isUpdatingSession ? 'Saving…' : 'Save changes'}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}

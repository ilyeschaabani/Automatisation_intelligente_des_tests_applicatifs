'use client'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import {
  Play,
  Filter,
  Search,
  Clock,
} from 'lucide-react'
import { useSearchParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'

import {
  createExecution,
  getExecutions,
  type ExecutionStatus,
  type ExecutionType,
  type TestExecution,
} from '@/lib/api-client'

const statusStyle: Record<ExecutionStatus, string> = {
  QUEUED: 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
  RUNNING: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400',
  FINISHED: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  ERROR: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400',
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

export default function ExecutionsPage() {
  const searchParams = useSearchParams()
  const sessionIdParam = searchParams.get('sessionId')
  const sessionId = sessionIdParam ? Number(sessionIdParam) : null

  const [executions, setExecutions] = useState<TestExecution[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState('')

  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isCreating, setIsCreating] = useState(false)
  const [executionType, setExecutionType] = useState<ExecutionType>('INITIAL')

  const loadExecutions = async (sid: number) => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await getExecutions(sid)
      setExecutions(Array.isArray(data) ? data : [])
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Failed to load executions'
      console.error('Failed to load executions', e)
      setExecutions([])
      setError(message)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    if (!sessionId || Number.isNaN(sessionId)) {
      setExecutions([])
      setIsLoading(false)
      return
    }
    void loadExecutions(sessionId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionIdParam])

  const filteredExecutions = useMemo(() => {
    const query = searchTerm.trim().toLowerCase()
    if (!query) return executions
    return executions.filter((exe) => {
      return (
        String(exe.id).toLowerCase().includes(query) ||
        String(exe.executionNumber).toLowerCase().includes(query) ||
        String(exe.status).toLowerCase().includes(query) ||
        String(exe.executionType).toLowerCase().includes(query)
      )
    })
  }, [executions, searchTerm])

  const onCreateExecution = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!sessionId) return

    setIsCreating(true)
    setError(null)
    try {
      await createExecution(sessionId, { executionType })
      setIsCreateOpen(false)
      setExecutionType('INITIAL')
      await loadExecutions(sessionId)
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to create execution'
      console.error('Failed to create execution', err)
      setError(message)
    } finally {
      setIsCreating(false)
    }
  }

  return (
    <div className="flex h-screen bg-background">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto p-8">
          <div className="max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col gap-6 mb-8">
              <div>
                <h1 className="text-3xl font-bold text-foreground mb-2">
                  Test Executions
                </h1>
                <p className="text-muted-foreground">
                  Monitor and manage active and historical test execution sessions
                </p>
              </div>

              {/* Controls */}
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
                <div className="flex gap-2">
                  <Button variant="outline" size="sm">
                    <Filter className="w-4 h-4 mr-2" />
                    Filter
                  </Button>
                  <Sheet open={isCreateOpen} onOpenChange={setIsCreateOpen}>
                    <SheetTrigger asChild>
                      <Button
                        size="sm"
                        className="bg-primary hover:bg-primary/90"
                        disabled={!sessionId || isLoading}
                      >
                        <Play className="w-4 h-4 mr-2" />
                        New Execution
                      </Button>
                    </SheetTrigger>
                    <SheetContent side="right" className="sm:max-w-md">
                      <SheetHeader>
                        <SheetTitle>New Execution</SheetTitle>
                        <SheetDescription>
                          Create an execution for session{' '}
                          <span className="font-medium">{sessionId ?? '—'}</span>
                        </SheetDescription>
                      </SheetHeader>

                      <form className="mt-6 space-y-6" onSubmit={onCreateExecution}>
                        <div className="space-y-2">
                          <Label>Execution type</Label>
                          <Select value={executionType} onValueChange={(v) => setExecutionType(v as any)}>
                            <SelectTrigger>
                              <SelectValue placeholder="Select type" />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="INITIAL">INITIAL</SelectItem>
                              <SelectItem value="RETEST">RETEST</SelectItem>
                            </SelectContent>
                          </Select>
                        </div>

                        {error ? <p className="text-sm text-destructive">{error}</p> : null}

                        <SheetFooter className="pt-2">
                          <Button
                            type="button"
                            variant="outline"
                            onClick={() => setIsCreateOpen(false)}
                          >
                            Cancel
                          </Button>
                          <Button type="submit" disabled={isCreating}>
                            {isCreating ? 'Creating…' : 'Create execution'}
                          </Button>
                        </SheetFooter>
                      </form>
                    </SheetContent>
                  </Sheet>
                </div>
              </div>
            </div>

            {/* Executions List */}
            <div className="space-y-4">
              {!sessionId ? (
                <Card className="p-6">
                  <p className="text-sm text-muted-foreground">
                    Select a session first. Open a project and create a session, then use “View executions”.
                  </p>
                </Card>
              ) : null}

              {isLoading ? (
                <Card className="p-6">
                  <p className="text-sm text-muted-foreground">Loading executions…</p>
                </Card>
              ) : null}

              {error && sessionId ? (
                <Card className="p-6">
                  <p className="text-sm text-destructive">{error}</p>
                </Card>
              ) : null}

              {sessionId && !isLoading && !error && filteredExecutions.length === 0 ? (
                <Card className="p-6">
                  <p className="text-sm text-muted-foreground">No executions found.</p>
                </Card>
              ) : null}

              {filteredExecutions.map((execution) => {
                return (
                  <Card
                    key={execution.id}
                    className="p-6 transition-all hover:shadow-md"
                  >
                    <div className="grid grid-cols-1 gap-6">
                      {/* Top Row */}
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <div className="flex items-center gap-3 mb-2">
                            <h3 className="text-lg font-bold text-foreground">
                              Execution #{execution.executionNumber}
                            </h3>
                            <Badge
                              variant="outline"
                              className={statusStyle[execution.status]}
                            >
                              {execution.status}
                            </Badge>
                            <Badge variant="outline">{execution.executionType}</Badge>
                          </div>
                          <p className="text-sm text-muted-foreground">
                            Session: {sessionId}
                          </p>
                        </div>
                        <div className="text-right">
                          <p className="text-xs text-muted-foreground">
                            {formatDate(execution.executionDate)}
                          </p>
                        </div>
                      </div>
                    </div>
                  </Card>
                )
              })}
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}

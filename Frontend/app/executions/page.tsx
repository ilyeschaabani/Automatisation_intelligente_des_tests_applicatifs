'use client'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Play,
  Pause,
  Square,
  RefreshCw,
  Download,
  Filter,
  Search,
  Clock,
  CheckCircle2,
  AlertCircle,
  XCircle,
} from 'lucide-react'
import { useState } from 'react'

const executions = [
  {
    id: 'EXE-2024-001',
    campaign: 'Banking Core Tests',
    app: 'Payment System',
    status: 'running',
    progress: 75,
    tests: { total: 250, passed: 180, failed: 5, skipped: 10, running: 55 },
    startTime: '2024-01-15 10:30:00',
    estimatedTime: '15 mins',
    duration: '11 mins',
    type: 'Functional',
  },
  {
    id: 'EXE-2024-002',
    campaign: 'API Regression Suite',
    app: 'Account Management',
    status: 'completed',
    progress: 100,
    tests: { total: 180, passed: 175, failed: 3, skipped: 2, running: 0 },
    startTime: '2024-01-15 09:00:00',
    estimatedTime: '12 mins',
    duration: '12 mins 35s',
    type: 'API',
  },
  {
    id: 'EXE-2024-003',
    campaign: 'Mobile Web Tests',
    app: 'Customer Portal',
    status: 'failed',
    progress: 100,
    tests: { total: 120, passed: 95, failed: 15, skipped: 10, running: 0 },
    startTime: '2024-01-14 16:45:00',
    estimatedTime: '10 mins',
    duration: '10 mins 12s',
    type: 'Functional',
  },
  {
    id: 'EXE-2024-004',
    campaign: 'Security DAST Scan',
    app: 'API Gateway',
    status: 'completed',
    progress: 100,
    tests: { total: 95, passed: 93, failed: 0, skipped: 2, running: 0 },
    startTime: '2024-01-14 14:20:00',
    estimatedTime: '8 mins',
    duration: '8 mins 45s',
    type: 'Security',
  },
]

const statusConfig = {
  running: { bg: 'bg-blue-50', text: 'text-blue-700', badge: 'bg-blue-100' },
  completed: {
    bg: 'bg-green-50',
    text: 'text-green-700',
    badge: 'bg-green-100',
  },
  failed: { bg: 'bg-red-50', text: 'text-red-700', badge: 'bg-red-100' },
  paused: { bg: 'bg-yellow-50', text: 'text-yellow-700', badge: 'bg-yellow-100' },
}

export default function ExecutionsPage() {
  const [selectedExecution, setSelectedExecution] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState('')

  const filteredExecutions = executions.filter(
    (exe) =>
      exe.id.toLowerCase().includes(searchTerm.toLowerCase()) ||
      exe.campaign.toLowerCase().includes(searchTerm.toLowerCase()) ||
      exe.app.toLowerCase().includes(searchTerm.toLowerCase()),
  )

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
                  <Button size="sm" className="bg-primary hover:bg-primary/90">
                    <Play className="w-4 h-4 mr-2" />
                    New Execution
                  </Button>
                </div>
              </div>
            </div>

            {/* Executions List */}
            <div className="space-y-4">
              {filteredExecutions.map((execution) => {
                const config =
                  statusConfig[execution.status as keyof typeof statusConfig]
                const passRate = Math.round(
                  (execution.tests.passed / execution.tests.total) * 100,
                )

                return (
                  <Card
                    key={execution.id}
                    className={`p-6 cursor-pointer transition-all hover:shadow-md ${
                      selectedExecution === execution.id ? 'ring-2 ring-primary' : ''
                    }`}
                    onClick={() => setSelectedExecution(execution.id)}
                  >
                    <div className="grid grid-cols-1 gap-6">
                      {/* Top Row */}
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <div className="flex items-center gap-3 mb-2">
                            <h3 className="text-lg font-bold text-foreground">
                              {execution.id}
                            </h3>
                            <Badge
                              variant="secondary"
                              className={`${config.badge} text-xs font-medium uppercase`}
                            >
                              {execution.status}
                            </Badge>
                            <Badge variant="outline">{execution.type}</Badge>
                          </div>
                          <p className="text-sm text-muted-foreground mb-1">
                            {execution.campaign}
                          </p>
                          <p className="text-sm text-muted-foreground">
                            App: {execution.app}
                          </p>
                        </div>
                        <div className="text-right">
                          <p className="text-2xl font-bold text-foreground">
                            {passRate}%
                          </p>
                          <p className="text-xs text-muted-foreground">
                            Pass Rate
                          </p>
                        </div>
                      </div>

                      {/* Progress Bar */}
                      <div>
                        <div className="flex justify-between mb-2">
                          <span className="text-xs text-muted-foreground">
                            Progress
                          </span>
                          <span className="text-xs font-medium text-foreground">
                            {execution.progress}%
                          </span>
                        </div>
                        <div className="w-full bg-secondary rounded-full h-2">
                          <div
                            className={`h-2 rounded-full transition-all ${
                              execution.status === 'running'
                                ? 'bg-blue-500'
                                : execution.status === 'completed'
                                  ? 'bg-green-500'
                                  : 'bg-red-500'
                            }`}
                            style={{ width: `${execution.progress}%` }}
                          />
                        </div>
                      </div>

                      {/* Test Stats Grid */}
                      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
                        <div className="bg-secondary rounded-lg p-3">
                          <p className="text-xs text-muted-foreground mb-1">
                            Total Tests
                          </p>
                          <p className="text-lg font-bold text-foreground">
                            {execution.tests.total}
                          </p>
                        </div>
                        <div className="bg-secondary rounded-lg p-3">
                          <div className="flex items-center gap-1 mb-1">
                            <CheckCircle2 className="w-4 h-4 text-green-600" />
                            <p className="text-xs text-muted-foreground">
                              Passed
                            </p>
                          </div>
                          <p className="text-lg font-bold text-green-600">
                            {execution.tests.passed}
                          </p>
                        </div>
                        <div className="bg-secondary rounded-lg p-3">
                          <div className="flex items-center gap-1 mb-1">
                            <XCircle className="w-4 h-4 text-red-600" />
                            <p className="text-xs text-muted-foreground">
                              Failed
                            </p>
                          </div>
                          <p className="text-lg font-bold text-red-600">
                            {execution.tests.failed}
                          </p>
                        </div>
                        <div className="bg-secondary rounded-lg p-3">
                          <div className="flex items-center gap-1 mb-1">
                            <AlertCircle className="w-4 h-4 text-yellow-600" />
                            <p className="text-xs text-muted-foreground">
                              Skipped
                            </p>
                          </div>
                          <p className="text-lg font-bold text-yellow-600">
                            {execution.tests.skipped}
                          </p>
                        </div>
                        <div className="bg-secondary rounded-lg p-3">
                          <div className="flex items-center gap-1 mb-1">
                            <Clock className="w-4 h-4 text-blue-600" />
                            <p className="text-xs text-muted-foreground">
                              Duration
                            </p>
                          </div>
                          <p className="text-sm font-bold text-blue-600">
                            {execution.duration}
                          </p>
                        </div>
                      </div>

                      {/* Bottom Row - Actions and Time Info */}
                      <div className="flex items-center justify-between pt-4 border-t border-border">
                        <div className="text-xs text-muted-foreground">
                          <p>Started: {execution.startTime}</p>
                          <p>Est. Time: {execution.estimatedTime}</p>
                        </div>
                        <div className="flex gap-2">
                          {execution.status === 'running' && (
                            <>
                              <Button size="sm" variant="outline">
                                <Pause className="w-4 h-4" />
                              </Button>
                              <Button
                                size="sm"
                                variant="outline"
                                className="text-destructive"
                              >
                                <Square className="w-4 h-4" />
                              </Button>
                            </>
                          )}
                          {execution.status === 'completed' ||
                            (execution.status === 'failed' && (
                              <>
                                <Button size="sm" variant="outline">
                                  <RefreshCw className="w-4 h-4" />
                                </Button>
                                <Button size="sm" variant="outline">
                                  <Download className="w-4 h-4" />
                                </Button>
                              </>
                            ))}
                          <Button
                            size="sm"
                            className="bg-primary hover:bg-primary/90"
                          >
                            View Details
                          </Button>
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

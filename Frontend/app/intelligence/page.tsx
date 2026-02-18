'use client'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Brain,
  TrendingDown,
  AlertTriangle,
  Zap,
  RefreshCw,
  Download,
  Filter,
} from 'lucide-react'

const recurringFailures = [
  {
    id: 1,
    scenario: 'Login flow - Session timeout',
    occurrences: 23,
    lastSeen: '2 hours ago',
    rootCause: 'Session expires after 30 mins of inactivity',
    severity: 'high',
    affectedTests: 127,
  },
  {
    id: 2,
    scenario: 'Payment gateway - Timeout on high load',
    occurrences: 18,
    lastSeen: '6 hours ago',
    rootCause: 'External API response delay under 2000+ concurrent requests',
    severity: 'high',
    affectedTests: 89,
  },
  {
    id: 3,
    scenario: 'Mobile - Date picker element not found',
    occurrences: 12,
    lastSeen: '1 day ago',
    rootCause: 'DOM element rendered after async data load',
    severity: 'medium',
    affectedTests: 34,
  },
  {
    id: 4,
    scenario: 'API - Encoding issues with special characters',
    occurrences: 8,
    lastSeen: '3 days ago',
    rootCause: 'UTF-8 encoding mismatch in request body',
    severity: 'medium',
    affectedTests: 16,
  },
]

const testPrioritization = [
  {
    id: 1,
    name: 'Transaction Processing Core',
    risk: 'Critical',
    frequency: 'Always',
    riskScore: 98,
    recommendation: 'Run on every commit',
  },
  {
    id: 2,
    name: 'Authentication & Authorization',
    risk: 'High',
    frequency: 'On Auth Changes',
    riskScore: 92,
    recommendation: 'Run on identity service commits',
  },
  {
    id: 3,
    name: 'Regression - Core Features',
    risk: 'High',
    frequency: 'Weekly',
    riskScore: 85,
    recommendation: 'Run nightly',
  },
  {
    id: 4,
    name: 'Mobile UI Regression',
    risk: 'Medium',
    frequency: 'Monthly',
    riskScore: 62,
    recommendation: 'Run on major releases',
  },
  {
    id: 5,
    name: 'Performance Tests',
    risk: 'Medium',
    frequency: 'Monthly',
    riskScore: 58,
    recommendation: 'Run weekly on staging',
  },
]

export default function IntelligencePage() {
  return (
    <div className="flex h-screen bg-background">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto p-8">
          <div className="max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col gap-4 mb-8">
              <div>
                <div className="flex items-center gap-3 mb-2">
                  <Brain className="w-8 h-8 text-primary" />
                  <h1 className="text-3xl font-bold text-foreground">
                    Intelligent Analytics
                  </h1>
                </div>
                <p className="text-muted-foreground">
                  AI-powered insights for test failure detection and scenario
                  prioritization
                </p>
              </div>
            </div>

            {/* Recurring Failures Section */}
            <div className="mb-12">
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h2 className="text-2xl font-bold text-foreground mb-2 flex items-center gap-2">
                    <AlertTriangle className="w-6 h-6 text-destructive" />
                    Recurring Failures Analysis
                  </h2>
                  <p className="text-muted-foreground">
                    Detected patterns and root causes from test logs
                  </p>
                </div>
                <Button variant="outline" size="sm">
                  <RefreshCw className="w-4 h-4 mr-2" />
                  Refresh
                </Button>
              </div>

              <div className="space-y-4">
                {recurringFailures.map((failure) => (
                  <Card key={failure.id} className="p-6 hover:shadow-md transition-all">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                      {/* Left Column */}
                      <div>
                        <h3 className="font-bold text-foreground mb-4">
                          {failure.scenario}
                        </h3>
                        <div className="space-y-2">
                          <div>
                            <p className="text-xs text-muted-foreground mb-1">
                              Occurrences
                            </p>
                            <p className="text-2xl font-bold text-foreground">
                              {failure.occurrences}
                            </p>
                          </div>
                          <div>
                            <p className="text-xs text-muted-foreground mb-1">
                              Last Seen
                            </p>
                            <p className="text-sm font-medium text-foreground">
                              {failure.lastSeen}
                            </p>
                          </div>
                        </div>
                      </div>

                      {/* Middle Column */}
                      <div>
                        <p className="text-xs text-muted-foreground mb-2 font-medium">
                          ROOT CAUSE ANALYSIS
                        </p>
                        <p className="text-sm text-foreground leading-relaxed mb-4">
                          {failure.rootCause}
                        </p>
                        <Badge
                          variant="secondary"
                          className={
                            failure.severity === 'high'
                              ? 'bg-red-100 text-red-700'
                              : 'bg-yellow-100 text-yellow-700'
                          }
                        >
                          {failure.severity.toUpperCase()} SEVERITY
                        </Badge>
                      </div>

                      {/* Right Column */}
                      <div className="flex flex-col justify-between">
                        <div className="bg-secondary rounded-lg p-4 mb-4">
                          <p className="text-xs text-muted-foreground mb-1">
                            Affected Tests
                          </p>
                          <p className="text-2xl font-bold text-foreground">
                            {failure.affectedTests}
                          </p>
                        </div>
                        <Button className="w-full bg-primary hover:bg-primary/90">
                          View Related Tests
                        </Button>
                      </div>
                    </div>
                  </Card>
                ))}
              </div>
            </div>

            {/* Test Prioritization Section */}
            <div>
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h2 className="text-2xl font-bold text-foreground mb-2 flex items-center gap-2">
                    <Zap className="w-6 h-6 text-accent" />
                    Intelligent Test Prioritization
                  </h2>
                  <p className="text-muted-foreground">
                    Risk-based prioritization recommendations using AI analysis
                  </p>
                </div>
                <Button variant="outline" size="sm">
                  <Download className="w-4 h-4 mr-2" />
                  Export
                </Button>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-border">
                      <th className="text-left py-4 px-4 font-bold text-foreground">
                        Test Suite
                      </th>
                      <th className="text-left py-4 px-4 font-bold text-foreground">
                        Risk Level
                      </th>
                      <th className="text-left py-4 px-4 font-bold text-foreground">
                        Risk Score
                      </th>
                      <th className="text-left py-4 px-4 font-bold text-foreground">
                        Recommended Frequency
                      </th>
                      <th className="text-left py-4 px-4 font-bold text-foreground">
                        AI Recommendation
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {testPrioritization.map((test, idx) => (
                      <tr
                        key={test.id}
                        className={`border-b border-border ${
                          idx % 2 === 0 ? 'bg-secondary/30' : ''
                        } hover:bg-secondary transition-colors`}
                      >
                        <td className="py-4 px-4 text-foreground font-medium">
                          {test.name}
                        </td>
                        <td className="py-4 px-4">
                          <Badge
                            variant="secondary"
                            className={
                              test.risk === 'Critical'
                                ? 'bg-red-100 text-red-700'
                                : test.risk === 'High'
                                  ? 'bg-orange-100 text-orange-700'
                                  : 'bg-yellow-100 text-yellow-700'
                            }
                          >
                            {test.risk}
                          </Badge>
                        </td>
                        <td className="py-4 px-4">
                          <div className="flex items-center gap-2">
                            <div className="w-16 bg-secondary rounded-full h-2">
                              <div
                                className="h-2 rounded-full bg-gradient-to-r from-accent to-primary"
                                style={{ width: `${test.riskScore}%` }}
                              />
                            </div>
                            <span className="font-bold text-foreground">
                              {test.riskScore}
                            </span>
                          </div>
                        </td>
                        <td className="py-4 px-4 text-foreground">
                          {test.frequency}
                        </td>
                        <td className="py-4 px-4">
                          <div className="text-sm text-foreground font-medium">
                            {test.recommendation}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}

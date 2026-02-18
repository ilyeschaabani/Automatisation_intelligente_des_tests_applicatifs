'use client'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Shield,
  Zap,
  AlertTriangle,
  CheckCircle2,
  Code,
  Bug,
  Play,
  Download,
  RefreshCw,
} from 'lucide-react'

const sastScans = [
  {
    id: 1,
    date: '2024-01-15 10:30:00',
    status: 'completed',
    vulnerabilities: 3,
    critical: 0,
    high: 2,
    medium: 1,
    coverage: 92,
    scanTime: '8 mins',
  },
  {
    id: 2,
    date: '2024-01-14 10:30:00',
    status: 'completed',
    vulnerabilities: 5,
    critical: 1,
    high: 2,
    medium: 2,
    coverage: 88,
    scanTime: '7 mins 45s',
  },
  {
    id: 3,
    date: '2024-01-13 10:30:00',
    status: 'completed',
    vulnerabilities: 2,
    critical: 0,
    high: 1,
    medium: 1,
    coverage: 94,
    scanTime: '8 mins 15s',
  },
]

const dastFindings = [
  {
    id: 1,
    title: 'SQL Injection Vulnerability - User Login',
    severity: 'critical',
    endpoint: '/api/auth/login',
    parameter: 'username',
    description: 'Input validation not properly sanitized',
    status: 'open',
  },
  {
    id: 2,
    title: 'Missing HTTP Security Headers',
    severity: 'high',
    endpoint: '/transactions',
    parameter: 'response headers',
    description: 'X-Frame-Options, CSP headers missing',
    status: 'open',
  },
  {
    id: 3,
    title: 'Sensitive Data in Logs',
    severity: 'high',
    endpoint: '/api/payments',
    parameter: 'log output',
    description: 'Credit card numbers logged in plaintext',
    status: 'in_progress',
  },
  {
    id: 4,
    title: 'Weak TLS Configuration',
    severity: 'medium',
    endpoint: 'API Gateway',
    parameter: 'ssl/tls',
    description: 'TLS 1.0 still enabled, should disable',
    status: 'resolved',
  },
  {
    id: 5,
    title: 'Session Timeout Not Enforced',
    severity: 'medium',
    endpoint: '/dashboard',
    parameter: 'session',
    description: 'Session remains active after 24 hours',
    status: 'open',
  },
]

export default function SecurityPage() {
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
                  <Shield className="w-8 h-8 text-primary" />
                  <h1 className="text-3xl font-bold text-foreground">
                    Security Analysis
                  </h1>
                </div>
                <p className="text-muted-foreground">
                  SAST and DAST security scanning for vulnerabilities and compliance
                </p>
              </div>
            </div>

            {/* SAST Section */}
            <div className="mb-12">
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h2 className="text-2xl font-bold text-foreground mb-2 flex items-center gap-2">
                    <Code className="w-6 h-6 text-accent" />
                    SAST - Static Application Security Testing
                  </h2>
                  <p className="text-muted-foreground">
                    Source code analysis for vulnerabilities and code quality issues
                  </p>
                </div>
                <Button className="bg-primary hover:bg-primary/90">
                  <Zap className="w-4 h-4 mr-2" />
                  Run SAST Scan
                </Button>
              </div>

              <div className="space-y-4">
                {sastScans.map((scan) => (
                  <Card key={scan.id} className="p-6 hover:shadow-md transition-all">
                    <div className="grid grid-cols-1 md:grid-cols-5 gap-6 mb-6">
                      <div>
                        <p className="text-xs text-muted-foreground mb-1">
                          SCAN DATE
                        </p>
                        <p className="font-medium text-foreground">
                          {scan.date}
                        </p>
                      </div>
                      <div>
                        <p className="text-xs text-muted-foreground mb-1">
                          CODE COVERAGE
                        </p>
                        <div className="flex items-center gap-2">
                          <div className="flex-1 bg-secondary rounded-full h-2">
                            <div
                              className="h-2 rounded-full bg-green-500"
                              style={{ width: `${scan.coverage}%` }}
                            />
                          </div>
                          <span className="font-bold text-foreground">
                            {scan.coverage}%
                          </span>
                        </div>
                      </div>
                      <div>
                        <p className="text-xs text-muted-foreground mb-1">
                          SCAN TIME
                        </p>
                        <p className="font-medium text-foreground">
                          {scan.scanTime}
                        </p>
                      </div>
                      <div className="md:col-span-2">
                        <p className="text-xs text-muted-foreground mb-3">
                          VULNERABILITIES FOUND
                        </p>
                        <div className="flex gap-4">
                          <div>
                            <Badge
                              variant="secondary"
                              className="bg-red-100 text-red-700 mr-2"
                            >
                              {scan.critical} CRITICAL
                            </Badge>
                          </div>
                          <div>
                            <Badge
                              variant="secondary"
                              className="bg-orange-100 text-orange-700 mr-2"
                            >
                              {scan.high} HIGH
                            </Badge>
                          </div>
                          <div>
                            <Badge
                              variant="secondary"
                              className="bg-yellow-100 text-yellow-700"
                            >
                              {scan.medium} MEDIUM
                            </Badge>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="flex gap-2 pt-4 border-t border-border">
                      <Button size="sm" variant="outline">
                        View Report
                      </Button>
                      <Button size="sm" variant="outline">
                        <Download className="w-4 h-4 mr-2" />
                        Export
                      </Button>
                    </div>
                  </Card>
                ))}
              </div>
            </div>

            {/* DAST Section */}
            <div>
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h2 className="text-2xl font-bold text-foreground mb-2 flex items-center gap-2">
                    <Bug className="w-6 h-6 text-destructive" />
                    DAST - Dynamic Application Security Testing
                  </h2>
                  <p className="text-muted-foreground">
                    Runtime security testing of deployed application
                  </p>
                </div>
                <Button className="bg-primary hover:bg-primary/90">
                  <Play className="w-4 h-4 mr-2" />
                  Start DAST Scan
                </Button>
              </div>

              <div className="space-y-4">
                {dastFindings.map((finding) => (
                  <Card
                    key={finding.id}
                    className={`p-6 hover:shadow-md transition-all border-l-4 ${
                      finding.severity === 'critical'
                        ? 'border-l-red-600'
                        : finding.severity === 'high'
                          ? 'border-l-orange-600'
                          : 'border-l-yellow-600'
                    }`}
                  >
                    <div className="flex items-start justify-between mb-4">
                      <div className="flex-1">
                        <h3 className="text-lg font-bold text-foreground mb-2">
                          {finding.title}
                        </h3>
                        <div className="flex flex-wrap gap-2 mb-4">
                          <Badge
                            variant="secondary"
                            className={
                              finding.severity === 'critical'
                                ? 'bg-red-100 text-red-700'
                                : finding.severity === 'high'
                                  ? 'bg-orange-100 text-orange-700'
                                  : 'bg-yellow-100 text-yellow-700'
                            }
                          >
                            {finding.severity.toUpperCase()}
                          </Badge>
                          <Badge
                            variant="secondary"
                            className={
                              finding.status === 'resolved'
                                ? 'bg-green-100 text-green-700'
                                : finding.status === 'in_progress'
                                  ? 'bg-blue-100 text-blue-700'
                                  : 'bg-gray-100 text-gray-700'
                            }
                          >
                            {finding.status.toUpperCase()}
                          </Badge>
                        </div>
                      </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-4">
                      <div>
                        <p className="text-xs text-muted-foreground mb-1 font-medium">
                          ENDPOINT
                        </p>
                        <p className="font-mono text-sm text-foreground">
                          {finding.endpoint}
                        </p>
                      </div>
                      <div>
                        <p className="text-xs text-muted-foreground mb-1 font-medium">
                          PARAMETER
                        </p>
                        <p className="font-mono text-sm text-foreground">
                          {finding.parameter}
                        </p>
                      </div>
                    </div>

                    <div className="bg-secondary rounded-lg p-4 mb-4">
                      <p className="text-xs text-muted-foreground mb-2 font-medium">
                        DESCRIPTION
                      </p>
                      <p className="text-sm text-foreground">
                        {finding.description}
                      </p>
                    </div>

                    <div className="flex gap-2">
                      <Button size="sm" variant="outline">
                        View Details
                      </Button>
                      <Button size="sm" variant="outline">
                        Create Issue
                      </Button>
                    </div>
                  </Card>
                ))}
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}

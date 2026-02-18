import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { CheckCircle2, AlertCircle, Clock, Download } from 'lucide-react'
import { Button } from '@/components/ui/button'

const results = [
  {
    id: 1,
    campaign: 'Banking Mobile App - v2.5',
    status: 'Running',
    progress: 65,
    totalTests: 145,
    passed: 94,
    failed: 0,
    skipped: 51,
    duration: '15:30',
    startTime: '2024-02-11 14:30',
  },
  {
    id: 2,
    campaign: 'Payment Gateway API Tests',
    status: 'Completed',
    progress: 100,
    totalTests: 89,
    passed: 87,
    failed: 2,
    skipped: 0,
    duration: '08:45',
    startTime: '2024-02-11 13:00',
  },
  {
    id: 3,
    campaign: 'Core Banking Features',
    status: 'Completed',
    progress: 100,
    totalTests: 112,
    passed: 110,
    failed: 2,
    skipped: 0,
    duration: '12:15',
    startTime: '2024-02-10 18:45',
  },
  {
    id: 4,
    campaign: 'Authentication Module Tests',
    status: 'Running',
    progress: 40,
    totalTests: 76,
    passed: 30,
    failed: 1,
    skipped: 45,
    duration: '06:20',
    startTime: '2024-02-11 14:50',
  },
  {
    id: 5,
    campaign: 'UI Components - v3.0',
    status: 'Failed',
    progress: 85,
    totalTests: 98,
    passed: 84,
    failed: 14,
    skipped: 0,
    duration: '11:00',
    startTime: '2024-02-11 13:20',
  },
  {
    id: 6,
    campaign: 'Database Integration Tests',
    status: 'Completed',
    progress: 100,
    totalTests: 167,
    passed: 165,
    failed: 2,
    skipped: 0,
    duration: '18:30',
    startTime: '2024-02-10 16:00',
  },
]

const statusConfig = {
  Running: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400',
  Completed: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  Failed: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400',
}

export default function ResultsPage() {
  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-7xl">
          {/* Page Header */}
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground">Test Results</h1>
            <p className="text-muted-foreground mt-1">
              View detailed results from all test executions
            </p>
          </div>

          {/* Summary Stats */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
            <Card className="p-6">
              <div className="flex items-center gap-4">
                <div className="p-3 bg-green-100 dark:bg-green-950 rounded-lg">
                  <CheckCircle2 className="text-green-600 dark:text-green-400" />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Total Passed</p>
                  <p className="text-2xl font-bold text-foreground">570</p>
                </div>
              </div>
            </Card>

            <Card className="p-6">
              <div className="flex items-center gap-4">
                <div className="p-3 bg-red-100 dark:bg-red-950 rounded-lg">
                  <AlertCircle className="text-red-600 dark:text-red-400" />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Total Failed</p>
                  <p className="text-2xl font-bold text-foreground">21</p>
                </div>
              </div>
            </Card>

            <Card className="p-6">
              <div className="flex items-center gap-4">
                <div className="p-3 bg-blue-100 dark:bg-blue-950 rounded-lg">
                  <Clock className="text-blue-600 dark:text-blue-400" />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Success Rate</p>
                  <p className="text-2xl font-bold text-foreground">96.4%</p>
                </div>
              </div>
            </Card>
          </div>

          {/* Results Table */}
          <Card className="overflow-hidden">
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow className="border-b border-border">
                    <TableHead className="text-left">Campaign</TableHead>
                    <TableHead className="text-center">Status</TableHead>
                    <TableHead className="text-center">Tests</TableHead>
                    <TableHead className="text-center">Passed</TableHead>
                    <TableHead className="text-center">Failed</TableHead>
                    <TableHead className="text-center">Skipped</TableHead>
                    <TableHead className="text-center">Duration</TableHead>
                    <TableHead className="text-center">Time</TableHead>
                    <TableHead className="text-center">Action</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {results.map((result) => (
                    <TableRow
                      key={result.id}
                      className="border-b border-border hover:bg-secondary/50"
                    >
                      <TableCell className="font-medium text-foreground">
                        {result.campaign}
                      </TableCell>
                      <TableCell className="text-center">
                        <Badge
                          variant="outline"
                          className={statusConfig[result.status as keyof typeof statusConfig]}
                        >
                          {result.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-center text-foreground">
                        {result.totalTests}
                      </TableCell>
                      <TableCell className="text-center text-green-600 dark:text-green-400 font-semibold">
                        {result.passed}
                      </TableCell>
                      <TableCell className="text-center text-red-600 dark:text-red-400 font-semibold">
                        {result.failed}
                      </TableCell>
                      <TableCell className="text-center text-muted-foreground">
                        {result.skipped}
                      </TableCell>
                      <TableCell className="text-center text-muted-foreground">
                        {result.duration}
                      </TableCell>
                      <TableCell className="text-center text-sm text-muted-foreground">
                        {result.startTime}
                      </TableCell>
                      <TableCell className="text-center">
                        <Button variant="ghost" size="sm">
                          <Download size={16} />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </Card>
        </div>
      </main>
    </div>
  )
}

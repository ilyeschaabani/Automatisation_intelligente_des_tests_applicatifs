import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Download,
  FileText,
  Calendar,
  MoreVertical,
} from 'lucide-react'

const reports = [
  {
    id: 1,
    name: 'Monthly Test Report - January 2024',
    type: 'PDF',
    date: '2024-02-01',
    size: '2.4 MB',
    status: 'Complete',
  },
  {
    id: 2,
    name: 'Banking App v2.5 Final QA Report',
    type: 'PDF',
    date: '2024-02-10',
    size: '1.8 MB',
    status: 'Complete',
  },
  {
    id: 3,
    name: 'Payment Gateway Integration Report',
    type: 'HTML',
    date: '2024-02-11',
    size: '3.2 MB',
    status: 'Complete',
  },
  {
    id: 4,
    name: 'Security & Compliance Audit',
    type: 'PDF',
    date: '2024-02-09',
    size: '4.1 MB',
    status: 'Complete',
  },
  {
    id: 5,
    name: 'Performance Testing Analysis',
    type: 'HTML',
    date: '2024-02-08',
    size: '2.7 MB',
    status: 'Complete',
  },
  {
    id: 6,
    name: 'Regression Test Coverage Report',
    type: 'PDF',
    date: '2024-02-07',
    size: '1.5 MB',
    status: 'Complete',
  },
]

export default function ReportsPage() {
  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-7xl">
          {/* Page Header */}
          <div className="flex items-center justify-between mb-8">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Reports</h1>
              <p className="text-muted-foreground mt-1">
                Download and manage test reports
              </p>
            </div>
            <Button className="bg-primary hover:bg-primary/90 text-primary-foreground gap-2">
              <FileText size={20} />
              Generate New Report
            </Button>
          </div>

          {/* Reports Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {reports.map((report) => (
              <Card
                key={report.id}
                className="p-6 hover:shadow-md transition-shadow"
              >
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-start gap-4">
                    <div className="p-3 bg-secondary rounded-lg">
                      <FileText className="text-primary" size={24} />
                    </div>
                    <div className="flex-1">
                      <h3 className="font-semibold text-foreground leading-tight">
                        {report.name}
                      </h3>
                      <div className="flex items-center gap-2 mt-2">
                        <Badge variant="outline" className="text-xs">
                          {report.type}
                        </Badge>
                        <Badge
                          variant="outline"
                          className="bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400"
                        >
                          {report.status}
                        </Badge>
                      </div>
                    </div>
                  </div>
                  <button className="p-2 hover:bg-secondary rounded-lg">
                    <MoreVertical size={18} className="text-muted-foreground" />
                  </button>
                </div>

                <div className="border-t border-border pt-4 flex items-center justify-between text-sm">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2 text-muted-foreground">
                      <Calendar size={16} />
                      <span>{report.date}</span>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      Size: {report.size}
                    </p>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-primary hover:bg-secondary"
                  >
                    <Download size={18} />
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </main>
    </div>
  )
}

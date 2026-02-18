import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { StatCard } from '@/components/stat-card'
import { CampaignCard } from '@/components/campaign-card'
import { TestResultsChart } from '@/components/charts/test-results-chart'
import { SuccessRateTrend } from '@/components/charts/success-rate-trend'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import {
  CheckCircle2,
  AlertCircle,
  Clock,
  TrendingUp,
  Plus,
  ArrowRight,
} from 'lucide-react'

const chartData = [
  { name: 'Week 1', passed: 145, failed: 12, skipped: 5 },
  { name: 'Week 2', passed: 162, failed: 8, skipped: 3 },
  { name: 'Week 3', passed: 178, failed: 6, skipped: 4 },
  { name: 'Week 4', passed: 195, failed: 5, skipped: 2 },
]

const trendData = [
  { week: 'W1', successRate: 92 },
  { week: 'W2', successRate: 95 },
  { week: 'W3', successRate: 97 },
  { week: 'W4', successRate: 98 },
]

const campaigns = [
  {
    name: 'Banking Mobile App - v2.5',
    type: 'Functional' as const,
    status: 'Running' as const,
    progress: 65,
    tests: 145,
    passed: 94,
    failed: 0,
    lastRun: '5 mins ago',
  },
  {
    name: 'Payment Gateway API Tests',
    type: 'API' as const,
    status: 'Completed' as const,
    progress: 100,
    tests: 89,
    passed: 87,
    failed: 2,
    lastRun: '2 hours ago',
  },
  {
    name: 'Regression Suite - Production',
    type: 'Regression' as const,
    status: 'Scheduled' as const,
    progress: 0,
    tests: 234,
    passed: 0,
    failed: 0,
    lastRun: 'Tomorrow 2:00 AM',
  },
  {
    name: 'Core Banking Features',
    type: 'Functional' as const,
    status: 'Completed' as const,
    progress: 100,
    tests: 112,
    passed: 110,
    failed: 2,
    lastRun: '1 day ago',
  },
]

export default function DashboardPage() {
  return (
    <div className="flex min-h-screen bg-background">
      {/* Sidebar */}
      <Sidebar />

      {/* Main Content */}
      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-7xl">
          {/* Page Header */}
          <div className="flex items-center justify-between mb-8">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Dashboard</h1>
              <p className="text-muted-foreground mt-1">
                Welcome back! Monitor your test automation platform.
              </p>
            </div>
            <Button className="bg-primary hover:bg-primary/90 text-primary-foreground gap-2">
              <Plus size={20} />
              New Campaign
            </Button>
          </div>

          {/* Stats Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            <StatCard
              title="Total Tests Run"
              value="1,456"
              change={24}
              icon={<CheckCircle2 size={24} />}
            />
            <StatCard
              title="Success Rate"
              value="97.8%"
              change={2.3}
              icon={<TrendingUp size={24} />}
            />
            <StatCard
              title="Failed Tests"
              value="32"
              change={-15}
              trend="down"
              icon={<AlertCircle size={24} />}
            />
            <StatCard
              title="Active Campaigns"
              value="8"
              icon={<Clock size={24} />}
            />
          </div>

          {/* Charts Section */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
            {/* Test Results Chart */}
            <Card className="lg:col-span-2 p-6">
              <h2 className="text-lg font-bold text-foreground mb-4">
                Test Results Over Time
              </h2>
              <TestResultsChart data={chartData} />
            </Card>

            {/* Success Rate Trend */}
            <Card className="p-6">
              <h2 className="text-lg font-bold text-foreground mb-4">
                Success Rate Trend
              </h2>
              <SuccessRateTrend data={trendData} />
            </Card>
          </div>

          {/* Recent Campaigns */}
          <div>
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-lg font-bold text-foreground">
                Active & Recent Campaigns
              </h2>
              <Button
                variant="ghost"
                className="text-primary hover:bg-secondary gap-1"
              >
                View All
                <ArrowRight size={16} />
              </Button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {campaigns.map((campaign) => (
                <CampaignCard key={campaign.name} {...campaign} />
              ))}
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}

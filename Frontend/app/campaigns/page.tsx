import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { CampaignCard } from '@/components/campaign-card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import Link from 'next/link'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Plus, Search } from 'lucide-react'

const allCampaigns = [
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
  {
    name: 'Authentication Module Tests',
    type: 'API' as const,
    status: 'Running' as const,
    progress: 40,
    tests: 76,
    passed: 30,
    failed: 1,
    lastRun: '3 mins ago',
  },
  {
    name: 'UI Components - v3.0',
    type: 'Functional' as const,
    status: 'Failed' as const,
    progress: 85,
    tests: 98,
    passed: 84,
    failed: 14,
    lastRun: '30 mins ago',
  },
  {
    name: 'Database Integration Tests',
    type: 'Regression' as const,
    status: 'Completed' as const,
    progress: 100,
    tests: 167,
    passed: 165,
    failed: 2,
    lastRun: '5 hours ago',
  },
  {
    name: 'Security & Compliance Checks',
    type: 'API' as const,
    status: 'Running' as const,
    progress: 72,
    tests: 56,
    passed: 40,
    failed: 0,
    lastRun: '2 mins ago',
  },
]

export default function CampaignsPage() {
  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-7xl">
          {/* Page Header */}
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground">Test Campaigns</h1>
            <p className="text-muted-foreground mt-1">
              Manage and monitor all your test campaigns
            </p>
          </div>

          {/* Filters */}
          <div className="flex flex-col md:flex-row gap-4 mb-8">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground size-5" />
              <Input placeholder="Search campaigns..." className="pl-10" />
            </div>
            <Select defaultValue="all">
              <SelectTrigger className="w-full md:w-40">
                <SelectValue placeholder="Filter by status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Status</SelectItem>
                <SelectItem value="running">Running</SelectItem>
                <SelectItem value="completed">Completed</SelectItem>
                <SelectItem value="failed">Failed</SelectItem>
                <SelectItem value="scheduled">Scheduled</SelectItem>
              </SelectContent>
            </Select>
            <Select defaultValue="all-types">
              <SelectTrigger className="w-full md:w-40">
                <SelectValue placeholder="Filter by type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all-types">All Types</SelectItem>
                <SelectItem value="functional">Functional</SelectItem>
                <SelectItem value="api">API</SelectItem>
                <SelectItem value="regression">Regression</SelectItem>
              </SelectContent>
            </Select>
            <Button
              asChild
              className="bg-primary hover:bg-primary/90 text-primary-foreground gap-2 w-full md:w-auto"
            >
              <Link href="/campaigns/new">
                <Plus size={20} />
                New Campaign
              </Link>
            </Button>
          </div>

          {/* Campaigns Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {allCampaigns.map((campaign) => (
              <CampaignCard key={campaign.name} {...campaign} />
            ))}
          </div>
        </div>
      </main>
    </div>
  )
}

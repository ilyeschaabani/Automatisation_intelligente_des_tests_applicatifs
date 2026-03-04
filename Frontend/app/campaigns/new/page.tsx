'use client'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { CampaignForm } from '@/app/campaigns/_components/campaign-form'

export default function NewCampaignPage() {
  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-3xl">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground">Add New Campaign</h1>
            <p className="text-muted-foreground mt-1">
              Create a campaign to organize and run your tests
            </p>
          </div>

          <CampaignForm mode="create" />
        </div>
      </main>
    </div>
  )
}

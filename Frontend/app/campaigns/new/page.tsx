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

        <div className="p-6 flex justify-center">
          <div className="w-full max-w-3xl">
            <CampaignForm mode="create" />
          </div>
        </div>
      </main>
    </div>
  )
}

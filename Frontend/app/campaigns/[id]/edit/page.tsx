'use client'

import { useParams, useSearchParams } from 'next/navigation'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { CampaignForm } from '@/app/campaigns/_components/campaign-form'

export default function EditCampaignPage() {
  const params = useParams<{ id?: string | string[] }>()
  const searchParams = useSearchParams()
  const raw = Array.isArray(params?.id) ? params?.id[0] : params?.id
  const campaignId = raw ? Number(raw) : NaN
  const projectIdParam = searchParams.get('projectId')
  const projectId = projectIdParam ? Number(projectIdParam) : undefined

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-3xl">
          {Number.isFinite(campaignId) ? (
            <CampaignForm mode="edit" campaignId={campaignId} initialProjectId={projectId} />
          ) : (
            <p className="text-sm text-muted-foreground">ID de campagne invalide.</p>
          )}
        </div>
      </main>
    </div>
  )
}

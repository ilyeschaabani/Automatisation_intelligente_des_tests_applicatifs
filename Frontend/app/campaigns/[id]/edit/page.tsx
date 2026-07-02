'use client'

import { useParams } from 'next/navigation'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { CampaignForm } from '@/app/campaigns/_components/campaign-form'

export default function EditCampaignPage() {
  const params = useParams<{ id?: string | string[] }>()
  const raw = Array.isArray(params?.id) ? params?.id[0] : params?.id
  const campaignId = raw ? Number(raw) : NaN

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-3xl">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground">Modifier la campagne</h1>
            <p className="text-muted-foreground mt-1">
              Mettez à jour les champs et les cas de test attachés
            </p>
          </div>

          {Number.isFinite(campaignId) ? (
            <CampaignForm mode="edit" campaignId={campaignId} />
          ) : (
            <p className="text-sm text-muted-foreground">Invalid campaign id.</p>
          )}
        </div>
      </main>
    </div>
  )
}

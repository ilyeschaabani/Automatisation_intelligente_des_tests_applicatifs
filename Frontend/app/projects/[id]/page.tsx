import Link from 'next/link'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'

export default async function ProjectDetailsPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-4xl">
          <div className="flex items-center justify-between mb-8">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Project</h1>
              <p className="text-muted-foreground mt-1">ID: {id}</p>
            </div>

            <Button asChild variant="outline">
              <Link href="/projects">Back to projects</Link>
            </Button>
          </div>

          <Card className="p-6">
            <p className="text-sm text-muted-foreground">
              This is a placeholder details page. Wire it to your backend when the
              Test Management microservice exposes a Project details endpoint.
            </p>
          </Card>
        </div>
      </main>
    </div>
  )
}

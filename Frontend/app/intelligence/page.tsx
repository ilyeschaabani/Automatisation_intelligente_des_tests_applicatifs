'use client'

import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'

export default function IntelligencePage() {
  return (
    <div className="flex h-screen bg-background">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto" />
      </div>
    </div>
  )
}

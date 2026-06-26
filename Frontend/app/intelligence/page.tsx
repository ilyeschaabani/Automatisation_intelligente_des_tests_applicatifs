import { Suspense } from 'react'
import IntelligenceClient from './IntelligenceClient'

export default function IntelligencePage() {
  return (
    <Suspense>
      <IntelligenceClient />
    </Suspense>
  )
}

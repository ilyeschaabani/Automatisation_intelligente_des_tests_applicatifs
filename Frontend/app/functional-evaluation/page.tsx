import { Suspense } from 'react'
import IntelligenceClient from '../intelligence/IntelligenceClient'

export default function FunctionalEvaluationPage() {
  return (
    <Suspense>
      <IntelligenceClient />
    </Suspense>
  )
}

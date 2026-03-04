import { Suspense } from 'react'

import ExecutionsClient from './executions-client'

export default function ExecutionsPage() {
  return (
    <Suspense fallback={null}>
      <ExecutionsClient />
    </Suspense>
  )
}

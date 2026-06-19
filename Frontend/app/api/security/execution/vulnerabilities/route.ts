import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'
import { buildMsGestionHeaders } from '../../../_ms-gestion-auth'

const MS_EXECUTION = process.env.EXECUTION_SERVICE_URL ?? 'http://localhost:8083'

export async function GET(request: Request) {
  const cookieStore = await cookies()
  const { searchParams } = new URL(request.url)
  const scanId = searchParams.get('scanId')

  if (!scanId) {
    return NextResponse.json({ error: 'scanId required' }, { status: 400 })
  }

  const upstream = await fetch(
    `${MS_EXECUTION}/api/security/scans/${scanId}/vulnerabilities`,
    {
      headers: buildMsGestionHeaders(cookieStore, request),
      cache: 'no-store',
    }
  )

  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: { 'content-type': 'application/json' },
  })
}

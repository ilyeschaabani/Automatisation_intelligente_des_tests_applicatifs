import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'
import { buildMsGestionHeaders } from '../../../_ms-gestion-auth'

const MS_EXECUTION = process.env.EXECUTION_SERVICE_URL ?? 'http://localhost:8083'

export async function GET(request: Request) {
  const cookieStore = await cookies()
  const { searchParams } = new URL(request.url)
  const projectId = searchParams.get('projectId')

  const url = new URL(`${MS_EXECUTION}/api/security/scans`)
  if (projectId) url.searchParams.set('projectId', projectId)

  const upstream = await fetch(url.toString(), {
    headers: buildMsGestionHeaders(cookieStore, request),
    cache: 'no-store',
  })

  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: { 'content-type': 'application/json' },
  })
}

import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'
import { buildMsGestionHeaders } from '../../_ms-gestion-auth'

const MS_GESTION = process.env.TEST_MANAGEMENT_SERVICE_URL ?? 'http://localhost:8082'

export async function GET(request: Request) {
  const cookieStore = await cookies()
  const { searchParams } = new URL(request.url)

  const url = new URL(`${MS_GESTION}/api/security/scans`)
  const projectId = searchParams.get('projectId')
  const type = searchParams.get('type')
  if (projectId) url.searchParams.set('projectId', projectId)
  if (type) url.searchParams.set('type', type)

  const upstream = await fetch(url.toString(), {
    headers: buildMsGestionHeaders(cookieStore, request),
    cache: 'no-store',
  })

  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: { 'content-type': 'application/json' },
  })
}

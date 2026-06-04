import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'
import { buildMsGestionHeaders } from '../../_ms-gestion-auth'

const MS_GESTION_URL =
  process.env.TEST_MANAGEMENT_SERVICE_URL ??
  process.env.PROJECTS_SERVICE_URL ??
  'http://localhost:8082'

export async function POST(request: Request) {
  const cookieStore = await cookies()
  const body = await request.text()

  const upstream = await fetch(`${MS_GESTION_URL}/api/llm/generate-testdata`, {
    method: 'POST',
    headers: {
      ...buildMsGestionHeaders(cookieStore, request),
      'content-type': 'application/json',
    },
    body,
    cache: 'no-store',
  })

  const data = await upstream.json().catch(() => ({ testData: '{}' }))
  return NextResponse.json(data, { status: upstream.status })
}

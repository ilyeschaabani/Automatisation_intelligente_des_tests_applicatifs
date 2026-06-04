import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'
import { buildMsGestionHeaders } from '../../../_ms-gestion-auth'

const TEST_MANAGEMENT_SERVICE_URL =
  process.env.TEST_MANAGEMENT_SERVICE_URL ??
  process.env.PROJECTS_SERVICE_URL ??
  'http://localhost:8082'

export async function GET(
  request: Request,
  context: { params: Promise<{ id: string }> | { id: string } },
) {
  const cookieStore = await cookies()
  const url = new URL(request.url)
  const resolved = await context.params
  const id = resolved.id
  const projectId = url.searchParams.get('projectId')

  if (!projectId) {
    return NextResponse.json({ error: 'projectId is required' }, { status: 400 })
  }

  const upstream = await fetch(
    `${TEST_MANAGEMENT_SERVICE_URL}/api/projects/${encodeURIComponent(projectId)}/campaigns/${encodeURIComponent(id)}/available-testcases`,
    {
      method: 'GET',
      headers: buildMsGestionHeaders(cookieStore, request),
    },
  )

  const data = await upstream.json().catch(() => [])
  return NextResponse.json(data, { status: upstream.status })
}

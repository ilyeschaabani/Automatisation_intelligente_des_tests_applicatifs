import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'
import { buildMsGestionHeaders } from '../../../_ms-gestion-auth'

const MS_GESTION = process.env.TEST_MANAGEMENT_SERVICE_URL ?? 'http://localhost:8082'

export async function PATCH(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const cookieStore = await cookies()
  const body = await request.json()

  const action = body.status ? 'status' : body.assignedTo ? 'assign' : null
  if (!action) return NextResponse.json({ error: 'Invalid body' }, { status: 400 })

  const upstream = await fetch(`${MS_GESTION}/api/security/vulnerabilities/${id}/${action}`, {
    method: 'PATCH',
    headers: { ...buildMsGestionHeaders(cookieStore, request), 'content-type': 'application/json' },
    body: JSON.stringify(body),
  })

  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: { 'content-type': 'application/json' },
  })
}

import { cookies } from 'next/headers'
import { NextRequest, NextResponse } from 'next/server'
import { buildMsGestionHeaders } from '../../../_ms-gestion-auth'

const MS_GESTION = process.env.MS_GESTION_SERVICE_URL || 'http://localhost:8082'

export async function PATCH(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params
  const body = await req.json()

  const cookieStore = await cookies()
  const baseHeaders = buildMsGestionHeaders(cookieStore, req)

  const res = await fetch(`${MS_GESTION}/api/execution-results/${id}/assign`, {
    method: 'PATCH',
    headers: { ...baseHeaders, 'content-type': 'application/json' },
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    const text = await res.text().catch(() => '')
    return NextResponse.json(
      { message: text || 'Failed to assign' },
      { status: res.status }
    )
  }

  const data = await res.json()
  return NextResponse.json(data)
}

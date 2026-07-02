import { cookies } from 'next/headers'
import { NextRequest, NextResponse } from 'next/server'
import { buildMsGestionHeaders } from '../../../_ms-gestion-auth'

const MS_GESTION = process.env.MS_GESTION_SERVICE_URL || 'http://localhost:8082'

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params
  const cookieStore = await cookies()
  const headers = buildMsGestionHeaders(cookieStore, request)

  const res = await fetch(`${MS_GESTION}/api/projects/${id}/members`, {
    headers,
    cache: 'no-store',
  })
  const data = await res.json().catch(() => [])
  return NextResponse.json(data, { status: res.status })
}

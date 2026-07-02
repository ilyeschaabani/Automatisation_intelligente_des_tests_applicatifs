import { cookies } from 'next/headers'
import { NextRequest, NextResponse } from 'next/server'
import { buildMsGestionHeaders } from '../../../../_ms-gestion-auth'

const MS_GESTION = process.env.MS_GESTION_SERVICE_URL || 'http://localhost:8082'

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params

  const cookieStore = await cookies()
  const headers = buildMsGestionHeaders(cookieStore, req)

  const res = await fetch(`${MS_GESTION}/api/security/vulnerabilities/${id}/details`, { headers })

  if (!res.ok) {
    const text = await res.text().catch(() => '')
    return NextResponse.json(
      { message: text || 'Not found' },
      { status: res.status }
    )
  }

  const data = await res.json()
  return NextResponse.json(data)
}

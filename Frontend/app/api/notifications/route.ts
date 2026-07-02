import { cookies } from 'next/headers'
import { NextRequest, NextResponse } from 'next/server'
import { buildMsGestionHeaders } from '../_ms-gestion-auth'

const MS_GESTION = process.env.MS_GESTION_SERVICE_URL || 'http://localhost:8082'

export async function GET(request: NextRequest) {
  const userId = request.nextUrl.searchParams.get('userId')
  if (!userId) return NextResponse.json({ error: 'userId required' }, { status: 400 })

  const cookieStore = await cookies()
  const headers = buildMsGestionHeaders(cookieStore, request)

  const res = await fetch(`${MS_GESTION}/api/notifications?userId=${userId}`, { headers })
  const data = await res.json().catch(() => [])
  return NextResponse.json(data, { status: res.status })
}

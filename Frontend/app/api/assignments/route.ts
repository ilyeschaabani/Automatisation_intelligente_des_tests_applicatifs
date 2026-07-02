import { cookies } from 'next/headers'
import { NextRequest, NextResponse } from 'next/server'
import { buildMsGestionHeaders } from '../_ms-gestion-auth'

const MS_GESTION = process.env.MS_GESTION_SERVICE_URL || 'http://localhost:8082'

export async function GET(req: NextRequest) {
  const userId = req.nextUrl.searchParams.get('userId')
  if (!userId) {
    return NextResponse.json({ message: 'userId required' }, { status: 400 })
  }

  const cookieStore = await cookies()
  const headers = buildMsGestionHeaders(cookieStore, req)

  const res = await fetch(`${MS_GESTION}/api/assignments?userId=${userId}`, { headers })

  if (!res.ok) {
    return NextResponse.json({ message: 'Backend error' }, { status: res.status })
  }

  const data = await res.json()
  return NextResponse.json(data)
}

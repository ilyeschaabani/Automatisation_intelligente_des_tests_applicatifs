import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'
import { buildMsGestionHeaders } from '../../../_ms-gestion-auth'

const MS_EXECUTION = process.env.EXECUTION_SERVICE_URL ?? 'http://localhost:8083'

export async function POST(request: Request) {
  const cookieStore = await cookies()
  const { searchParams } = new URL(request.url)

  const projectId = searchParams.get('projectId')
  const environmentId = searchParams.get('environmentId')

  const url = `${MS_EXECUTION}/api/security/scan/dast?projectId=${projectId}&environmentId=${environmentId}`

  const upstream = await fetch(url, {
    method: 'POST',
    headers: buildMsGestionHeaders(cookieStore, request),
  })

  const data = await upstream.json().catch(() => ({}))
  return NextResponse.json(data, { status: upstream.status })
}

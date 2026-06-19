import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'
import { buildMsGestionHeaders } from '../../../_ms-gestion-auth'

const MS_EXECUTION = process.env.EXECUTION_SERVICE_URL ?? 'http://localhost:8083'

export async function GET(
  request: Request,
  { params }: { params: Promise<{ scanId: string }> }
) {
  const { scanId } = await params
  const cookieStore = await cookies()

  const upstream = await fetch(`${MS_EXECUTION}/api/security/report/${scanId}`, {
    headers: buildMsGestionHeaders(cookieStore, request),
  })

  if (!upstream.ok) {
    return NextResponse.json({ error: 'Report not found' }, { status: upstream.status })
  }

  const buffer = await upstream.arrayBuffer()
  return new NextResponse(buffer, {
    status: 200,
    headers: {
      'content-type': 'application/pdf',
      'content-disposition': upstream.headers.get('content-disposition') ?? 'attachment; filename=security-report.pdf',
    },
  })
}

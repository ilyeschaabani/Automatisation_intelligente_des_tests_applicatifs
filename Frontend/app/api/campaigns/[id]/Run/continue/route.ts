import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'
import { buildMsGestionHeaders } from '../../../../../_ms-gestion-auth'

const MS_EXECUTION_SERVICE_URL =
  process.env.EXECUTION_SERVICE_URL ??
  process.env.MS_EXECUTION_SERVICE_URL ??
  'http://localhost:8083'

function forwardSetCookie(upstream: Response, response: NextResponse) {
  const setCookies = (upstream.headers as any).getSetCookie?.() as string[] | undefined
  if (setCookies?.length) {
    for (const cookie of setCookies) response.headers.append('set-cookie', cookie)
    return
  }

  const maybeCookie = upstream.headers.get('set-cookie')
  if (maybeCookie) response.headers.append('set-cookie', maybeCookie)
}

export async function POST(
  request: Request,
  context: { params: Promise<{ id: string }> | { id: string } },
) {
  const cookieStore = await cookies()
  const url = new URL(request.url)
  const resolved = await context.params
  const id = resolved.id
  const body = await request.text().catch(() => '')

  // For now, ms-execution doesn't support continuation flow
  // This endpoint would be used for interactive campaign runs with user input
  // For basic execution, use POST /api/campaigns/{id}/Run instead
  return NextResponse.json(
    { error: 'ms-execution does not support continuation flow. Campaigns run to completion automatically.' },
    { status: 501 },
  )
}
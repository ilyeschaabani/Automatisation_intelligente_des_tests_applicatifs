import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'
import { buildMsGestionHeaders } from '../../../_ms-gestion-auth'

const MS_EXECUTION_SERVICE_URL =
  process.env.EXECUTION_SERVICE_URL ??
  process.env.MS_EXECUTION_SERVICE_URL ??
  'http://localhost:8083'

// Stop is forwarded DIRECTLY to ms-execution with the user's JWT (same as Run).
// Going through ms_gestion's RestTemplate dropped the token, so ms-execution
// rejected the call (401) and the campaign was never marked ABORTED.
export async function PUT(
  request: Request,
  context: { params: Promise<{ id: string }> | { id: string } },
) {
  const cookieStore = await cookies()
  const resolved = await context.params
  const id = resolved.id

  const upstream = await fetch(
    MS_EXECUTION_SERVICE_URL + '/api/execution/stop/' + encodeURIComponent(id),
    {
      method: 'PUT',
      headers: {
        ...buildMsGestionHeaders(cookieStore, request),
        'content-type': 'application/json',
      },
      cache: 'no-store',
    },
  )

  const body = await upstream.text().catch(() => '')
  return new NextResponse(body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'application/json',
      'cache-control': 'no-store',
    },
  })
}

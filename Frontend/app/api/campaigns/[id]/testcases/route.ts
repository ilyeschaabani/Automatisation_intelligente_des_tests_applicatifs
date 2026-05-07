import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'
import { buildMsGestionHeaders } from '../../../_ms-gestion-auth'

const TEST_MANAGEMENT_SERVICE_URL =
  process.env.TEST_MANAGEMENT_SERVICE_URL ??
  process.env.PROJECTS_SERVICE_URL ??
  'http://localhost:8082'

function forwardSetCookie(upstream: Response, response: NextResponse) {
  const setCookies = (upstream.headers as any).getSetCookie?.() as string[] | undefined
  if (setCookies?.length) {
    for (const cookie of setCookies) response.headers.append('set-cookie', cookie)
    return
  }

  const maybeCookie = upstream.headers.get('set-cookie')
  if (maybeCookie) response.headers.append('set-cookie', maybeCookie)
}

export async function PUT(
  request: Request,
  context: { params: Promise<{ id: string }> | { id: string } },
) {
  const cookieStore = await cookies()
  const url = new URL(request.url)
  const resolved = await context.params
  const id = resolved.id
  const body = await request.text().catch(() => '')

  const upstream = await fetch(
    `${TEST_MANAGEMENT_SERVICE_URL}/api/campaigns/${encodeURIComponent(id)}/testcases${url.search}`,
    {
      method: 'PUT',
      headers: {
        ...buildMsGestionHeaders(cookieStore, request),
        'content-type': request.headers.get('content-type') ?? 'application/json',
      },
      body,
    },
  )

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'application/json',
    },
  })

  forwardSetCookie(upstream, response)
  return response
}

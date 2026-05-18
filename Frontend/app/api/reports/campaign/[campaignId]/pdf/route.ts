import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

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

export async function GET(
  request: Request,
  context: { params: Promise<{ campaignId: string }> | { campaignId: string } },
) {
  const cookieStore = await cookies()
  const resolved = await context.params
  const campaignId = resolved.campaignId
  const url = new URL(request.url)

  const headers: Record<string, string> = {
    cookie: cookieStore.toString(),
    accept: request.headers.get('accept') ?? 'application/pdf',
  }

  const auth = request.headers.get('authorization')
  if (auth) headers.authorization = auth

  const upstream = await fetch(
    `${MS_EXECUTION_SERVICE_URL}/api/reports/campaign/${encodeURIComponent(campaignId)}/pdf${url.search}`,
    {
      method: 'GET',
      headers,
      cache: 'no-store',
    },
  )

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'application/pdf',
      'content-disposition': upstream.headers.get('content-disposition') ??
        `attachment; filename=campaign-${encodeURIComponent(campaignId)}-report.pdf`,
      'cache-control': 'no-store',
    },
  })

  forwardSetCookie(upstream, response)
  return response
}

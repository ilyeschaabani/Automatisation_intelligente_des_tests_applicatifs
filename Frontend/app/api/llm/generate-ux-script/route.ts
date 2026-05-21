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

function buildHeaders(request: Request, cookieHeader: string) {
  const headers: Record<string, string> = {
    cookie: cookieHeader,
    accept: request.headers.get('accept') ?? 'application/json',
  }

  const auth = request.headers.get('authorization')
  if (auth) headers.authorization = auth

  const contentType = request.headers.get('content-type')
  if (contentType) headers['content-type'] = contentType

  return headers
}

export async function POST(request: Request) {
  const cookieStore = await cookies()
  const url = new URL(request.url)
  const body = await request.text().catch(() => '')

  const upstream = await fetch(`${MS_EXECUTION_SERVICE_URL}/api/llm/generate-functional-script${url.search}`, {
    method: 'POST',
    headers: buildHeaders(request, cookieStore.toString()),
    body,
  })

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'application/json',
      'cache-control': 'no-store',
    },
  })

  forwardSetCookie(upstream, response)
  return response
}

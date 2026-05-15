import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const MS_EXECUTION_URL =
  process.env.MS_EXECUTION_SERVICE_URL ?? 'http://localhost:8083'

function buildHeaders(
  cookieStore: Awaited<ReturnType<typeof cookies>>,
  request: Request,
) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }

  const auth = request.headers.get('Authorization')
  if (auth) {
    headers.Authorization = auth
  }

  const cookiesValue = cookieStore.getAll()
  if (cookiesValue.length > 0) {
    headers.Cookie = cookiesValue.map((cookie) => `${cookie.name}=${cookie.value}`).join('; ')
  }

  return headers
}

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
  { params }: { params: Promise<{ path?: string[] }> },
) {
  const cookieStore = await cookies()
  const { path = [] } = await params
  const targetPath = path.length > 0 ? `/${path.join('/')}` : ''

  const upstream = await fetch(`${MS_EXECUTION_URL}/api/kpi${targetPath}`, {
    method: 'GET',
    headers: buildHeaders(cookieStore, request),
    cache: 'no-store',
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
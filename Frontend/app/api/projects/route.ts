import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

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

export async function GET(request: Request) {
  const cookieStore = await cookies()

  const upstream = await fetch(`${TEST_MANAGEMENT_SERVICE_URL}/api/projects`, {
    method: 'GET',
    headers: {
      cookie: cookieStore.toString(),
      accept: request.headers.get('accept') ?? 'application/json',
    },
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

export async function POST(request: Request) {
  const cookieStore = await cookies()
  const body = await request.text().catch(() => '')

  const upstream = await fetch(`${TEST_MANAGEMENT_SERVICE_URL}/api/projects`, {
    method: 'POST',
    headers: {
      cookie: cookieStore.toString(),
      'content-type': request.headers.get('content-type') ?? 'application/json',
      accept: request.headers.get('accept') ?? 'application/json',
    },
    body,
  })

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'application/json',
    },
  })

  forwardSetCookie(upstream, response)
  return response
}

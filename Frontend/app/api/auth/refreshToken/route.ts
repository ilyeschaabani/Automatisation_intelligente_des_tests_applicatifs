import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const AUTH_SERVICE_URL = process.env.AUTH_SERVICE_URL ?? 'http://localhost:8081'

export async function POST(request: Request) {
  const cookieStore = await cookies()
  const hasRefreshCookie = Boolean(cookieStore.get('refresh_token')?.value)

  if (!hasRefreshCookie) {
    return NextResponse.json({ error: 'NO_REFRESH_TOKEN' }, { status: 401 })
  }

  const upstream = await fetch(`${AUTH_SERVICE_URL}/api/auth/refreshToken`, {
    method: 'POST',
    headers: {
      cookie: cookieStore.toString(),
      // Keep JSON content-type only if caller included a body
      ...(request.headers.get('content-type')
        ? { 'content-type': request.headers.get('content-type')! }
        : {}),
    },
    body: await request.text().catch(() => ''),
  })

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: { 'content-type': upstream.headers.get('content-type') ?? 'application/json' },
  })

  // Forward Set-Cookie from backend so browser updates cookies for the frontend origin.
  // Next's fetch exposes getSetCookie() in Node runtime.
  const setCookies = (upstream.headers as any).getSetCookie?.() as string[] | undefined
  if (setCookies?.length) {
    for (const cookie of setCookies) response.headers.append('set-cookie', cookie)
  } else {
    const maybeCookie = upstream.headers.get('set-cookie')
    if (maybeCookie) response.headers.append('set-cookie', maybeCookie)
  }

  return response
}

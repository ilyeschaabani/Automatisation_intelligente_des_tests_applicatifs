import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const AUTH_SERVICE_URL = process.env.AUTH_SERVICE_URL ?? 'http://localhost:8081'

export async function GET() {
  const cookieStore = await cookies()

  const upstream = await fetch(`${AUTH_SERVICE_URL}/api/auth/history`, {
    headers: {
      cookie: cookieStore.toString(),
      accept: 'application/json',
    },
    cache: 'no-store',
  })

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'application/json',
    },
  })

  const setCookies = (upstream.headers as any).getSetCookie?.() as string[] | undefined
  if (setCookies?.length) {
    for (const cookie of setCookies) response.headers.append('set-cookie', cookie)
  } else {
    const maybeCookie = upstream.headers.get('set-cookie')
    if (maybeCookie) response.headers.append('set-cookie', maybeCookie)
  }

  return response
}

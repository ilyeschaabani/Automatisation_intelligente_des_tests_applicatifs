import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const AUTH_SERVICE_URL = process.env.AUTH_SERVICE_URL ?? 'http://localhost:8081'

export async function GET() {
  const cookieStore = await cookies()
  const accessToken = cookieStore.get('access_token')?.value
  const refreshToken = cookieStore.get('refresh_token')?.value

  const debug = process.env.NODE_ENV !== 'production'

  if (!accessToken && !refreshToken) {
    if (debug) console.debug('[api/auth/users] missing auth cookies')
    return NextResponse.json({ error: 'NOT_AUTHENTICATED' }, { status: 401 })
  }

  const upstream = await fetch(`${AUTH_SERVICE_URL}/api/users`, {
    headers: {
      cookie: cookieStore.toString(),
      accept: 'application/json',
    },
  })

  const text = await upstream.text()
  let data: unknown = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = { message: text }
  }

  if (!upstream.ok) {
    if (debug) console.debug('[api/auth/users] upstream failed', { status: upstream.status })
    return NextResponse.json(
      { error: 'USERS_FAILED', status: upstream.status, details: data },
      { status: upstream.status },
    )
  }

  if (debug) console.debug('[api/auth/users] ok')

  return NextResponse.json(data)
}

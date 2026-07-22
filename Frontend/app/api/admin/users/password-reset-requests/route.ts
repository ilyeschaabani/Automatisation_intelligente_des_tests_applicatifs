import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const AUTH_BASE = process.env.AUTH_SERVICE_URL || 'http://localhost:8081'

async function authHeaders() {
  const cookieStore = await cookies()
  const token = cookieStore.get('access_token')?.value
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

export async function GET() {
  const res = await fetch(`${AUTH_BASE}/api/admin/users/password-reset-requests`, {
    headers: await authHeaders(),
    cache: 'no-store',
  })
  if (!res.ok) {
    return NextResponse.json({ error: 'Failed' }, { status: res.status })
  }
  return NextResponse.json(await res.json())
}

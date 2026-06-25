import { NextRequest, NextResponse } from 'next/server'
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

export async function GET(_req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const res = await fetch(`${AUTH_BASE}/api/admin/users/${id}`, {
    headers: await authHeaders(),
    cache: 'no-store',
  })
  if (!res.ok) {
    return NextResponse.json({ error: 'User not found' }, { status: res.status })
  }
  return NextResponse.json(await res.json())
}

export async function DELETE(_req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const res = await fetch(`${AUTH_BASE}/api/admin/users/${id}`, {
    method: 'DELETE',
    headers: await authHeaders(),
  })
  if (!res.ok) {
    const data = await res.json().catch(() => ({}))
    return NextResponse.json(data, { status: res.status })
  }
  return new NextResponse(null, { status: 204 })
}

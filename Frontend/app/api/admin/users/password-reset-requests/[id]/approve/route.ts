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

export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const res = await fetch(`${AUTH_BASE}/api/admin/users/password-reset-requests/${id}/approve`, {
    method: 'POST',
    headers: await authHeaders(),
  })
  const data = await res.json().catch(() => ({}))
  return NextResponse.json(data, { status: res.status })
}

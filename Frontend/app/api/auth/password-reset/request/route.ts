import { NextResponse } from 'next/server'

const AUTH_SERVICE_URL = process.env.AUTH_SERVICE_URL ?? 'http://localhost:8081'

export async function POST(request: Request) {
  const upstream = await fetch(`${AUTH_SERVICE_URL}/api/auth/password-reset/request`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
    },
    body: await request.text(),
  })

  const data = await upstream.json().catch(() => ({}))
  return NextResponse.json(data, { status: upstream.status })
}

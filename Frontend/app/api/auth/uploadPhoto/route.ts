import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const AUTH_SERVICE_URL = process.env.AUTH_SERVICE_URL ?? 'http://localhost:8081'

export async function POST(request: Request) {
  const cookieStore = await cookies()

  const formData = await request.formData()

  const upstream = await fetch(`${AUTH_SERVICE_URL}/api/auth/uploadPhoto`, {
    method: 'POST',
    headers: {
      cookie: cookieStore.toString(),
    },
    body: formData,
  })

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'application/json',
    },
  })

  return response
}

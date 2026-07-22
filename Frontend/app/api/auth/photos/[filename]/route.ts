import { NextRequest, NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const AUTH_SERVICE_URL = process.env.AUTH_SERVICE_URL ?? 'http://localhost:8081'

export async function GET(request: NextRequest, { params }: { params: Promise<{ filename: string }> }) {
  const { filename } = await params
  const cookieStore = await cookies()

  const upstream = await fetch(`${AUTH_SERVICE_URL}/api/auth/photos/${filename}`, {
    headers: {
      cookie: cookieStore.toString(),
    },
  })

  if (!upstream.ok) {
    return NextResponse.json({ message: 'Not found' }, { status: 404 })
  }

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'image/png',
      'cache-control': 'public, max-age=3600',
    },
  })

  return response
}

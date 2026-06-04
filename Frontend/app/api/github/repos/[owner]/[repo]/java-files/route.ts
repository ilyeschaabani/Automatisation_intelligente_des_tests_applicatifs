import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const AUTH_SERVICE_URL = process.env.AUTH_SERVICE_URL ?? 'http://localhost:8081'

export async function GET(
  request: Request,
  { params }: { params: Promise<{ owner: string; repo: string }> }
) {
  const cookieStore = await cookies()
  const { owner, repo } = await params
  const url = new URL(request.url)
  const branch = url.searchParams.get('branch') ?? 'main'

  const upstream = await fetch(
    `${AUTH_SERVICE_URL}/api/github/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/java-files?branch=${encodeURIComponent(branch)}`,
    {
      method: 'GET',
      headers: {
        cookie: cookieStore.toString(),
        accept: 'application/json',
      },
      cache: 'no-store',
    }
  )

  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'application/json',
      'cache-control': 'no-store',
    },
  })
}
